package lang.temper.be.rust

import lang.temper.ast.anyChildDepth
import lang.temper.ast.deepCopy
import lang.temper.be.Backend
import lang.temper.be.Dependencies
import lang.temper.be.DescriptorsForDeclarations
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLOperator
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.aType
import lang.temper.be.tmpl.isNullValue
import lang.temper.be.tmpl.libraryName
import lang.temper.be.tmpl.mapParameters
import lang.temper.be.tmpl.mutableCaptures
import lang.temper.be.tmpl.orInvalid
import lang.temper.be.tmpl.referencedNames
import lang.temper.be.tmpl.splitConstructorBody
import lang.temper.be.tmpl.typeOrInvalid
import lang.temper.common.compatRemoveLast
import lang.temper.frontend.ModuleNamingContext
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.library.LibraryConfigurations
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.log.last
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModularName
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.Temporary
import lang.temper.type.Abstractness
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.canOnlyBeNull
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Descriptor
import lang.temper.type2.MkType2
import lang.temper.type2.NonNullType
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.TypeParamRef
import lang.temper.type2.ValueFormalKind
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withNullity
import lang.temper.type2.withType
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TClosureRecord
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TMap
import lang.temper.value.TMapBuilder
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.TVoid
import lang.temper.value.failSymbol
import lang.temper.value.sealedTypeSymbol

class RustTranslator(
    private val dependenciesBuilder: Dependencies.Builder<RustBackend>,
    val module: TmpL.Module,
    private val names: RustNames,
    private val modKids: Collection<FilePath>,
    libraryConfigurations: LibraryConfigurations = (module.parent as TmpL.ModuleSet).libraryConfigurations,
) {
    private val varTypes = dependenciesBuilder.getMetadata(
        libraryConfigurations.currentLibraryConfiguration.libraryName,
        DescriptorsForDeclarations.Key(RustBackend.Factory),
    )?.nameToDescriptor ?: mapOf()
    private var closureCount = 0
    private val decls = mutableMapOf<ResolvedName, DeclInfo>()
    private var insideMutableType = false
    private val failVars = mutableSetOf<ResolvedName>()
    private val functionContextStack = mutableListOf<FunctionContext>()
    private val loopLabels = mutableListOf<Rust.Id?>()
    private val moduleInits = mutableListOf<Rust.Statement>()
    private val moduleItems = mutableListOf<Rust.Item>()
    private val nameMaker = (module.parent as TmpL.ModuleSet).let { moduleSet ->
        ResolvedNameMaker(moduleSet.mergedNamingContext, moduleSet.genre)
    }
    private val testItems = mutableListOf<Rust.Item>()
    private val traitImports = mutableSetOf<Rust.Path>()

    fun translateModule(): Backend.TranslatedFileSpecification {
        // Preprocess tops.
        preprocessImports()
        preprocessTopLevels()
        // Process tops.
        for (topLevel in module.topLevels) {
            try {
                processTopLevel(topLevel)
            } catch (exception: NeverRefException) {
                // We don't actually expect this ever, but sloppy log just in case.
                Rust.ExprStatement(topLevel.pos, Rust.StringLiteral(topLevel.pos, exception.stackTraceToString()))
            }
        }
        val pos = module.pos
        // Build init function.
        val init = Rust.Item(
            pos,
            attrs = listOf(),
            pub = Rust.VisibilityPub(pos, Rust.VisibilityScope(pos, Rust.VisibilityScopeOption.Crate)),
            item = Rust.Function(
                pos,
                id = "init".toId(pos),
                params = listOf(),
                returnType = "()".toId(pos).wrapResult(),
                block = buildInit(),
            ),
        )
        // Build source file.
        val relPath = module.codeLocation.codeLocation.relativePath()
        val isRoot = relPath.segments.isEmpty()
        return Backend.TranslatedFileSpecification(
            path = makeSrcFilePath(relPath.withTemperAwareExtension("")),
            content = Rust.SourceFile(
                pos,
                attrs = listOf(),
                items = buildList {
                    // Declare submodules, except for root that needs to declare in lib file.
                    if (!isRoot) {
                        declareSubmods(pos, modKids)
                    }
                    // Provide needed imports.
                    // We only need temper-core traits sometimes, but just keep simple for now.
                    // And these `use` statements still work even if some other top-level is defined to shadow them.
                    // TODO Infer need. Or even call trait methods as static like `AnyValueTrait::ptr_id(...)`.
                    val temperCore = "temper_core".toKeyId(module.pos)
                    add(Rust.Use(module.pos, temperCore.extendWith("AnyValueTrait")).toItem())
                    add(Rust.Use(module.pos, temperCore.deepCopy().extendWith("AsAnyValue")).toItem())
                    add(Rust.Use(module.pos, temperCore.deepCopy().extendWith("Pair")).toItem())
                    // Import other traits that we might need for access to their methods.
                    // TODO Should we track when Pair is needed for including here?
                    // TODO How will this handling change once we have name-shortening imports generally?
                    for (trait in traitImports) {
                        add(Rust.Use(module.pos, trait).toItem())
                    }
                    // Need support code for some std things.
                    if (module.libraryName?.text == STANDARD_LIBRARY_NAME) {
                        val moduleName = module.codeLocation.codeLocation.sourceFile.last().fullName
                        if (moduleName in RustBackend.stdSupportNeeders) {
                            val supportId = "support".toId(pos)
                            add(Rust.Module(pos, id = supportId).toItem())
                            // Some support exports need visible outside the crate, so re-pub those.
                            val pub = Rust.VisibilityPub(pos)
                            add(Rust.Use(pos, supportId.deepCopy().extendWith("*")).toItem(pub = pub))
                        }
                    }
                    // Init and declare our own things.
                    add(init)
                    addAll(moduleItems)
                    // Tests.
                    if (testItems.isNotEmpty()) {
                        testItems.add(Rust.Use(pos, "super::*".toId(pos)).toItem())
                        Rust.Module(
                            pos,
                            id = "tests".toId(pos), // TODO Uniqueness?
                            block = Rust.Block(pos, statements = testItems),
                        ).toItem(
                            attrs = listOf(
                                Rust.AttrOuter(pos, Rust.Call(pos, "cfg".toId(pos), listOf("test".toId(pos)))),
                            ),
                        ).also { add(it) }
                    }
                },
            ),
            mimeType = RustBackend.mimeType,
        )
    }

    private fun buildInit(): Rust.Block {
        val pos = module.pos
        val statements = buildList {
            // Declaration.
            val decl = Rust.Item(
                pos,
                item = Rust.Static(
                    pos,
                    id = INIT_ONCE_NAME.toId(pos),
                    type = Rust.GenericType(
                        pos,
                        path = ONCE_LOCK_NAME.toId(pos),
                        args = listOf("()".toId(pos).wrapResult()),
                    ),
                    value = Rust.Call(pos, callee = "$ONCE_LOCK_NAME::new".toId(pos), args = listOf()),
                ),
            )
            // Add.
            add(decl)
        }
        // Initialization.
        val call = INIT_ONCE_NAME.toId(pos).methodCall(
            "get_or_init",
            listOf(
                Rust.Closure(
                    pos,
                    params = listOf(),
                    value = Rust.Block(pos, statements = moduleInits, result = "()".toId(pos).wrapOk()),
                ),
            ),
        ).wrapClone()
        return Rust.Block(pos, statements = statements, result = call)
    }

    private fun preprocessImports() {
        imports@ for (import in module.imports) {
            val externalName = import.externalName.name as ExportedName
            val export = (externalName.origin as? ModuleNamingContext)?.owner?.exportMatching(externalName)
            val name = import.localName?.name ?: continue@imports
            decls[name] = DeclInfo(
                // We expect decls here, so invent one.
                decl = TmpL.ModuleLevelDeclaration(
                    import.pos,
                    metadata = import.metadata,
                    name = import.localName!!,
                    // TODO Some easy way to go from Type2 to TmpL.Type?
                    type = TmpL.GarbageType(import.pos).aType,
                    init = null,
                    assignOnce = true,
                    descriptor = WellKnownTypes.invalidType2,
                ),
                importInfo = DeclImportInfo(
                    exportedName = externalName,
                    sig = import.sig,
                ),
                typeFrom = export?.typeInferences?.type?.let { hackMapOldStyleToNew(it) },
            )
        }
    }

    private fun preprocessTopLevels() {
        // First gather decls.
        decls@ for (topLevel in module.topLevels) {
            val decl = (topLevel as? TmpL.ModuleLevelDeclaration) ?: continue@decls
            decl.isConsole() && continue@decls
            val name = decl.name.name
            val topper = name is ExportedName
            decls[name] = DeclInfo(
                decl = decl,
                local = false,
                topper = topper,
                type = translateType(decl.type.ot),
                typeFrom = varTypes.getValue(name),
            )
        }
        // See which are referenced in other items. Like closure capture but top levels.
        items@ for (topLevel in module.topLevels) {
            when (topLevel) {
                is TmpL.ModuleFunctionDeclaration, is TmpL.TypeDeclaration -> {}
//                is TmpL.Test -> TODO()
                else -> continue@items
            }
            refs@ for (ref in topLevel.referencedNames()) {
                val decl = decls[ref] ?: continue@refs
                decl.importedName != null && continue@refs
                decl.topper && continue@refs
                // TODO Track which are referenced by tests only?
                decls[ref] = decl.copy(topper = true)
            }
        }
        // Declare toppers as module items.
        decls@ for (decl in decls.values) {
            decl.topper || continue@decls
            buildTopperGetter(decl).also { moduleItems.add(it) }
        }
    }

    private fun processTopLevel(topLevel: TmpL.TopLevel) {
        when (topLevel) {
            is TmpL.ModuleFunctionDeclaration -> processModuleFunctionDeclaration(topLevel)
            is TmpL.ModuleInitBlock -> processModuleInitBlock(topLevel)
            is TmpL.ModuleLevelDeclaration -> processModuleLevelDeclaration(topLevel)
            is TmpL.Test -> processTest(topLevel)
            is TmpL.TypeDeclaration -> processTypeDeclaration(topLevel)
            else -> {}
        }
    }

    private fun processForClassBuilder(
        fn: TmpL.FunctionDeclarationOrMethod,
        enclosingType: Rust.Type? = null,
        generics: List<Rust.GenericParam> = listOf(),
        returnType: Type2,
    ) {
        // TODO Share this exclusion logic with Java & Js.
        // If only `this` plus up to 1 more, don't bother with builder. TODO Instead checked named/optional?
        fn.parameters.parameters.count { it.name != fn.parameters.thisName } <= 1 && return
        // And for now, skip those with rest parameters. TODO Extract to list value?
        fn.parameters.restParameter != null && return
        // Build the builder.
        // Here we make `WhateverBuilder` for requireds and/or `WhateverBuilderOptions` structs for optionals.
        // Alternatively, could make a Java-style builder, which is common in Rust, but it takes less advantage of
        // standard static checking that we get with pub struct fields.
        val pos = fn.pos
        val pub = Rust.VisibilityPub(pos)
        // Choose ids.
        val targetId = when (enclosingType) {
            is Rust.GenericType -> enclosingType.path as Rust.Id
            else -> enclosingType as Rust.Id
        }
        // TODO Ensure unique names.
        val builderId = "${targetId.outName.outputNameText}Builder".toId(targetId.pos)
        val optionsId = "${targetId.outName.outputNameText}Options".toId(targetId.pos)
        // Figure out if we have requireds and/or optionals.
        // We need to separate these in Rust because requireds often can't Default.
        // We could get fancy about which requireds can, but that also becomes cognitive effort for users.
        val rustParams = translateParameters(
            fn.parameters,
            forTrait = true, // meaning no flex types in this case because struct fields
            skipSelf = true,
            style = NameStyle.Snake,
        )
        val nonSelfParams = fn.parameters.parameters.filter { it.name != fn.parameters.thisName }
        val paramPairs = nonSelfParams.zip(rustParams)
        val (requireds, optionals) = paramPairs.partition { (tmpl, _) ->
            !tmpl.optional
        }.let { (requireds, optionals) ->
            requireds.map { it.second } to optionals.map { it.second }
        }
        fun paramsToStructAdded(
            id: Rust.Id,
            params: List<Rust.FunctionParamOption>,
            attrs: List<Rust.AttrOuter> = listOf(),
        ): List<Rust.GenericParam> {
            // Work with Rust nodes rather than TmpL nodes because we already have generics handy in Rust form.
            // Any simple type name matching a formal has to be a reference to that formal.
            val usedTypes = params.findAllSimpleTypeNames()
            val filteredGenerics = generics.filter { generic ->
                val genericId = when (generic) {
                    is Rust.Id -> generic
                    is Rust.TypeParam -> generic.id
                    else -> error(generic)
                }.outName.outputNameText
                genericId in usedTypes
            }.deepCopy()
            Rust.Struct(
                pos = pos,
                id = id,
                generics = filteredGenerics,
                fields = params.map { param ->
                    param as Rust.FunctionParam
                    Rust.StructField(param.pos, pub = pub, id = param.pattern as Rust.Id, type = param.type)
                },
            ).also { moduleItems.add(it.toItem(attrs = attrs, pub = pub)) }
            return filteredGenerics
        }
        val self = "self".toKeyId(pos)
        fun callNew(params: List<Rust.FunctionParamOption>) = Rust.Call(
            pos,
            callee = targetId.deepCopy().extendWith("new"),
            args = params.map { self.deepCopy().member(it.toId(), notMethod = true) },
        )
        val rustReturnType = translateType(returnType, fn.returnType.pos)
        val optionalGenerics = when {
            optionals.isEmpty() -> listOf()
            else -> {
                // Manage optionals independently so they can default more easily.
                val attrs = listOf(buildDerive(pos, listOf("Clone", "Default")))
                val optionalGenerics = paramsToStructAdded(optionsId, optionals, attrs = attrs)
                if (requireds.isEmpty()) {
                    // If someone adds requireds later, then that's already breaking, so this is fine here.
                    Rust.Impl(
                        pos,
                        generics = optionalGenerics.deepCopy(),
                        trait = null,
                        type = optionsId.makeTypeRef(optionalGenerics),
                        items = buildList {
                            Rust.Function(
                                pos,
                                id = "build".toId(pos),
                                // Pass self by move on purpose in these, so we can avoid cloning.
                                // We make builder types Clone in case anyone badly wants copies on their own.
                                params = listOf(self),
                                returnType = rustReturnType,
                                block = Rust.Block(pos, result = callNew(optionals)),
                            ).also { add(it.toItem(pub = pub)) }
                        },
                    ).also { moduleItems.add(it.toItem()) }
                }
                optionalGenerics
            }
        }
        if (requireds.isNotEmpty()) {
            // Adding new requireds is a breaking change anyway, so presume this is also fixed in Rust.
            val attrs = listOf(buildDerive(pos, listOf("Clone")))
            val filteredGenerics = paramsToStructAdded(builderId, requireds, attrs = attrs)
            // Adding new optionals *isn't* breaking, but adding a new related "options" field to requireds would be.
            // So make a builder independent of optionals, and another that acknowledges them if present.
            Rust.Impl(
                pos,
                generics = filteredGenerics.deepCopy(),
                trait = null,
                type = builderId.makeTypeRef(filteredGenerics),
                items = buildList {
                    Rust.Function(
                        pos,
                        id = "build".toId(pos),
                        params = listOf(self),
                        returnType = rustReturnType,
                        block = Rust.Block(
                            pos,
                            result = when {
                                // Just call the constructor if we only have requireds.
                                optionals.isEmpty() -> callNew(requireds)
                                // Delegate to the with-optionals builder.
                                else -> self.deepCopy().methodCall(
                                    key = "build_with",
                                    args = listOf(makePath(pos, "std", "default", "Default", "default").call()),
                                )
                            },
                        ),
                    ).also { add(it.toItem(pub = pub)) }
                    if (optionals.isNotEmpty()) {
                        val optionsArg = "options".toId(pos)
                        Rust.Function(
                            pos,
                            id = "build_with".toId(pos),
                            generics = optionalGenerics.deepCopy(),
                            params = listOf(
                                self.deepCopy(),
                                Rust.FunctionParam(pos, optionsArg, optionsId.makeTypeRef(optionalGenerics)),
                            ),
                            returnType = rustReturnType,
                            block = Rust.Block(
                                pos,
                                result = Rust.Call(
                                    pos,
                                    callee = targetId.deepCopy().extendWith("new"),
                                    args = buildList {
                                        // Different subjects for each case here.
                                        for (param in requireds) {
                                            add(self.deepCopy().member(param.toId(), notMethod = true))
                                        }
                                        for (param in optionals) {
                                            add(optionsArg.deepCopy().member(param.toId(), notMethod = true))
                                        }
                                    },
                                ),
                            ),
                        ).also { add(it.toItem(pub = pub)) }
                    }
                },
            ).also { moduleItems.add(it.toItem()) }
        }
    }

    private fun processModuleFunctionDeclaration(decl: TmpL.ModuleFunctionDeclaration) {
        decls.computeIfAbsent(decl.name.name) { DeclInfo(decl, typeFrom = decl.sig) }
        moduleItems.add(translateFunctionDeclarationOrMethod(decl))
    }

    private fun processModuleInitBlock(block: TmpL.ModuleInitBlock) {
        val statements = block.body.statements
        block.metadata.any { it.key.symbol == failSymbol } && return
        processStatements(statements = statements, results = moduleInits)
    }

    private fun processModuleLevelDeclaration(decl: TmpL.ModuleLevelDeclaration) {
        // Skip console declaration for now. TODO Support logging consoles.
        decl.isConsole() && return
        moduleInits.addAll(translateModuleOrLocalDeclaration(decl))
    }

    private fun processStatements(
        statements: List<TmpL.Statement>,
        results: MutableList<Rust.Statement>,
        skipLastReturn: Boolean = false,
    ) {
        // Doesn't apply for module init block, but worrying that here isn't worth the trouble.
        val pendingLocalFunctions = mutableMapOf<ResolvedName, TmpL.LocalFunctionDeclaration>()
        var i = 0
        statements@ while (i < statements.size) {
            val statement = statements[i]
            if (statement is TmpL.LocalFunctionDeclaration) {
                pendingLocalFunctions[statement.name.name] = statement
                i += 1
                continue@statements
            }
            // TODO Can handler scope check/if-fail statements call local functions? If so, need to look there, too.
            if (statement.referencedNames().any { it in pendingLocalFunctions }) {
                val translateds = translateLocalFunctionDeclarations(pendingLocalFunctions.values)
                pendingLocalFunctions.clear()
                results.addAll(translateds)
            }
            when (statement) {
                // Combine handler scope statements that come awkwardly from frontend and tmpl.
                is TmpL.HandlerScope -> translateHandlerScope(statement, statements[i + 1])
                is TmpL.Assignment -> when (statement.right) {
                    is TmpL.HandlerScope -> translateHandlerScopeAssignment(statement, statements[i + 1])
                    else -> null
                }

                else -> null
            }?.let { bubbler ->
                results.add(bubbler)
                i += 1
            } ?: results.addAll(
                when {
                    skipLastReturn && i == statements.size - 1 && statement is TmpL.ReturnStatement ->
                        translateReturnStatement(statement, last = true)

                    else -> translateStatement(statement)
                },
            )
            i += 1
        }
    }

    private fun processTest(test: TmpL.Test) {
        functionContextStack.add(
            FunctionContext(
                captures = mapOf(),
                decl = test,
                methodizeds = setOf(),
                mutableCaptures = setOf(),
                returnType = TypeDescription.make(WellKnownTypes.voidType2),
            ),
        )
        try {
            val pos = test.pos
            val testParam = test.parameters.parameters.firstOrNull()!!
            val testObjectId = translateId(testParam.name)
            val stdId = "temper_std".toId(pos)
            // Record test param info.
            val testType = stdId.extendWith(listOf("testing", "Test"))
            val testParamInfo = DeclInfo(
                decl = testParam,
                local = true,
                type = testType,
                typeFrom = varTypes[testParam.name.name],
            )
            decls[testParam.name.name] = testParamInfo
            // Build test structure and body.
            val newTest = Rust.Call(pos, testType.extendWith("new"), listOf())
            val result = testObjectId.deepCopy().methodCall("soft_fail_to_hard")
            val id = translateId(test.name)
            dependenciesBuilder.addTest(module.libraryName, test, id.outName.outputNameText)
            val crateInit = "crate".toKeyId(pos).extendWith("init")
            val stdInit = stdId.deepCopy().extendWith("init")
            Rust.Function(
                pos,
                id = id,
                params = listOf(),
                returnType = "()".toId(pos).wrapResult(),
                block = translateBlock(
                    prefix = listOf(
                        // Init both our own crate and temper_std (in case our crate doesn't) before any tests.
                        // Init is idempotent, so multi-init is ok.
                        // We currently don't actually need to init temper_std, but might in the future.
                        Rust.ExprStatement(pos, Rust.Call(pos, crateInit, listOf("None".toId(pos)))),
                        Rust.ExprStatement(pos, Rust.Call(pos, stdInit, listOf("None".toId(pos)))),
                        Rust.LetStatement(pos, pattern = testObjectId, type = null, value = newTest),
                    ),
                    block = test.body,
                    result = result,
                ),
            ).toItem(
                attrs = listOf(Rust.AttrOuter(pos, "test".toId(pos))),
            ).also { testItems.add(it) }
        } finally {
            functionContextStack.compatRemoveLast()
        }
    }

    private fun processTypeDeclaration(decl: TmpL.TypeDeclaration) {
        when (decl.kind) {
            TmpL.TypeDeclarationKind.Class -> processTypeDeclarationClass(decl)
            TmpL.TypeDeclarationKind.Enum -> {} // TODO
            TmpL.TypeDeclarationKind.Interface -> processTypeDeclarationInterface(decl)
        }
    }

    private fun processTypeDeclarationClass(decl: TmpL.TypeDeclaration) {
        gatherStaticDecls(decl)
        val name = translateTypeOutName(decl.name.name)
        val id = name.toId(decl.name.pos)
        val pos = decl.pos
        // We need a struct to store fields.
        // TODO Uniquify the struct name if it isn't already.
        val structNameText = "${name.outputNameText}Struct"
        val structId = structNameText.toId(pos)
        val generics = buildGenerics(decl.typeParameters)
        var mutable = false
        Rust.Struct(
            pos,
            id = structId,
            generics = generics,
            fields = buildList {
                // Actual fields.
                fields@ for (member in decl.members) {
                    val prop = (member as? TmpL.InstanceProperty) ?: continue@fields
                    prop.memberShape.abstractness == Abstractness.Concrete || continue@fields
                    if (!prop.assignOnce) {
                        mutable = true
                    }
                    add(translateInstanceProperty(prop))
                }
                // Phantoms for unused generics. TODO Only for *unused*.
                addPhantoms(generics)
            },
        ).toItem().also { moduleItems.add(it) }
        // We also need a wrapper for Arc<Mutex|RwLock> so we can impl it.
        val pub = (
            chooseVisibility(decl)
                // Allow crate visibility for easier support code access.
                ?: Rust.VisibilityPub(pos, scope = Rust.VisibilityScope(pos, Rust.VisibilityScopeOption.Crate))
            )
        Rust.TupleStruct(
            pos,
            id = id,
            generics = generics.deepCopy(),
            fields = listOf(
                Rust.TupleField(
                    pos,
                    type = Rust.GenericType(
                        pos,
                        // TODO Paths.
                        path = ARC_NAME.toId(pos),
                        args = structId.deepCopy().makeTypeRef(generics).let { core ->
                            when {
                                mutable -> Rust.GenericType(pos, path = RW_LOCK_NAME.toId(pos), args = listOf(core))
                                else -> core
                            }
                        }.let { listOf(it) },
                    ),
                ),
            ),
        ).toItem(attrs = listOf(buildDerive(pos, listOf("Clone"))), pub = pub).also { moduleItems.add(it) }
        // Gather up instance methods by name so we can coordinate with supertypes as needed.
        val instanceMethods = decl.members.filterIsInstance<TmpL.InstanceMethod>().filter { member ->
            member is TmpL.NormalMethod || member is TmpL.GetterOrSetter
        }.associateBy { member ->
            when (member) {
                is TmpL.NormalMethod -> member.name.name
                // We sometimes get names like "getwhatever" rather than "get.whatever", so be explicit.
                is TmpL.GetterOrSetter -> when (member) {
                    is TmpL.Getter -> "get"
                    is TmpL.Setter -> "set"
                }.let { BuiltinName("$it.${member.dotName}") } // changed to "nym`...`" below
                else -> error("unexpected")
            }.displayName
        }
        // We also need the impl itself for public class members working on the wrapper.
        val typeRef = id.makeTypeRef(generics)
        // And we need to know if we're implementing a shallowly mut type when getting internal property values.
        insideMutableType = mutable
        try {
            Rust.Impl(
                pos,
                generics = generics.deepCopy(),
                trait = null,
                type = typeRef,
                items = decl.members.flatMap { member ->
                    translateMethodLike(member, generics = generics, type = typeRef, typePub = pub)
                },
            ).toItem().also { moduleItems.add(it) }
        } finally {
            // Because type defs are always top level, we don't need a stack of indicators.
            insideMutableType = false
        }
        // Also all trait impls, remembering for AnyValue macro use.
        val supTypes = mutableListOf<Rust.Type>()
        val selfParams = listOf(Rust.RefType(pos, type = "self".toKeyId(pos)))
        val handledSups = mutableSetOf<ModularName>()
        sups@ for ((subShape, sup) in decl.typeShape.allInterfaces()) {
            // Only handle type shapes, and only unique ones.
            val supShape = (sup.definition as? TypeShape) ?: continue@sups
            // For sealed enums, this picks an arbitrary winner. TODO Allow diamonds and/or check against them earlier.
            handledSups.add(supShape.name) || continue@sups
            // Handle this one.
            val supName = translateIdFromNameAsPath(pos, supShape.name, style = NameStyle.Camel)
            val supType = translateTypeNominalApplyAnyBindings(sup, supName)
            val supTraitType = translateTypeNominalApplyAnyBindings(sup, supName.suffixed(TRAIT_NAME_SUFFIX))
            supTypes.add(translateTypeNominalApplyAnyBindings(sup, supName)) // without trait suffix
            val callClone = "self".toKeyId(pos).wrapClone()
            val supImpl = Rust.Impl(
                pos,
                generics = generics.deepCopy(),
                trait = supTraitType,
                type = typeRef.deepCopy(),
                items = buildList {
                    // Some need as_enum.
                    if (supShape.sealedSubTypes != null) {
                        // The sub shape must be a member of the sealed sub types if the Temper was legal.
                        // TODO Qualified path, not just name.
                        val enumId = supName.suffixed(ENUM_NAME_SUFFIX)
                        val subName = translateTypeOutName(subShape.name)
                        Rust.Function(
                            pos,
                            // TODO Only if all sealed subtypes are public?
                            id = AS_ENUM_NAME.toId(pos),
                            params = selfParams.deepCopy(),
                            returnType = enumId,
                            block = Rust.Block(
                                pos,
                                result = Rust.Call(
                                    pos,
                                    callee = "$enumId::$subName".toId(pos),
                                    args = callClone.deepCopy().let { clone ->
                                        when {
                                            subShape.isInterface() ->
                                                clone.box(wanted = subShape, translator = this@RustTranslator)
                                            else -> clone
                                        }
                                    }.let { listOf(it) },
                                ),
                            ),
                        ).also { add(it.toItem()) }
                    }
                    // All need clone_boxed.
                    Rust.Function(
                        pos,
                        id = CLONE_BOXED_NAME.toId(pos),
                        params = selfParams.deepCopy(),
                        returnType = supType,
                        block = Rust.Block(
                            pos,
                            result = Rust.Call(
                                pos,
                                callee = supName.deepCopy().extendWith("new"),
                                args = listOf(callClone.deepCopy()),
                            ),
                        ),
                    ).also { add(it.toItem()) }
                    // Then call the struct impl for each overridden trait method.
                    methods@ for (supMethod in supShape.methods) {
                        val method = instanceMethods[supMethod.name.displayName] ?: continue@methods
                        // Only one expected there, but meh.
                        addAll(buildForwarder(method, returnType = supMethod.descriptor.orInvalid.returnType2))
                    }
                    // Properties also, because they only sometimes align with methods.
                    for (supProperty in supShape.properties) {
                        if (supProperty.getter == null) {
                            val getterName = BuiltinName("get.${supProperty.symbol.text}").displayName
                            instanceMethods[getterName]?.let { addAll(buildForwarder(it)) }
                        }
                        if (supProperty.setter == null && supProperty.hasSetter) {
                            val setterName = BuiltinName("set.${supProperty.symbol.text}").displayName
                            instanceMethods[setterName]?.let { addAll(buildForwarder(it)) }
                        }
                    }
                },
            )
            moduleItems.add(supImpl.toItem())
        }
        // Implement AnyValue.
        Rust.Call(
            pos,
            callee = "temper_core".toKeyId(pos).extendWith("impl_any_value_trait!"),
            args = listOf(typeRef.deepCopy(), Rust.Array(pos, supTypes.deepCopy())),
        ).also { moduleItems.add(Rust.ExprStatement(pos, it).toItem()) }
    }

    private fun processTypeDeclarationInterface(decl: TmpL.TypeDeclaration) {
        gatherStaticDecls(decl)
        val name = translateTypeOutName(decl.name.name)
        val id = name.toId(decl.name.pos)
        val enumId = when {
            // TODO Only if all sealed subtypes are public?
            decl.metadata.any { it.key.symbol == sealedTypeSymbol } -> defineSealedEnum(decl)
            else -> null
        }
        val pub = chooseVisibility(decl)
        // We need an actual trait to implement.
        // It's likely less important than wrapped dyn versions of it, so for now give it a separate name.
        // TODO Uniquify names better and/or double check this naming strategy later.
        val traitNameText = "$name$TRAIT_NAME_SUFFIX"
        val pos = decl.pos
        val traitId = traitNameText.toId(pos)
        val generics = buildGenerics(decl.typeParameters)
        val fullTypeRef = id.makeTypeRef(generics)
        val fullTraitTypeRef = traitId.makeTypeRef(generics)
        val selfParams = listOf(Rust.RefType(pos, type = "self".toKeyId(pos)))
        val bounds = buildList {
            // Ensure AnyValue and other needed bounds for all interface traits.
            for (anyBound in listOf("AsAnyValue", "AnyValueTrait")) {
                add("temper_core".toKeyId(pos).extendWith(anyBound))
            }
            for (commonBound in listOf(SEND_NAME, SYNC_NAME)) {
                add(commonBound.toId(pos))
            }
            // Also provide immediate supertype bounds, which we'll implement in translation of concrete types.
            sups@ for (sup in decl.superTypes) {
                sup.typeName.sourceDefinition == WellKnownTypes.anyValueTypeDefinition && continue@sups
                add(translateTypeAsTraitName(sup))
            }
        }
        Rust.Trait(
            pos,
            id = traitId,
            generics = generics,
            bounds = bounds,
            items = buildList {
                // Optional: fn as_enum(&self) -> WhateverEnum;
                if (enumId != null) {
                    Rust.Function(
                        pos,
                        // TODO Only if all sealed subtypes are public?
                        id = AS_ENUM_NAME.toId(pos),
                        params = selfParams.deepCopy(),
                        returnType = enumId.deepCopy(),
                        block = null,
                    ).also { add(it.toItem()) }
                }
                // fn clone_boxed(&self) -> Whatever;
                Rust.Function(
                    pos,
                    id = CLONE_BOXED_NAME.toId(pos),
                    params = selfParams.deepCopy(),
                    returnType = fullTypeRef.deepCopy(),
                    block = null,
                ).also { add(it.toItem()) }
                // User-defined methods.
                for (member in decl.members) {
                    if (member !is TmpL.StaticMember) {
                        addAll(translateMethodLike(member, forTrait = true))
                    }
                }
            },
        ).toItem(pub = pub).also { moduleItems.add(it) }
        // Make an arc dyn wrapper type. TODO Combine some with class wrapper type logic?
        Rust.TupleStruct(
            pos,
            id = id,
            generics = generics.deepCopy(),
            fields = listOf(
                Rust.TupleField(
                    pos,
                    type = Rust.GenericType(
                        pos,
                        path = ARC_NAME.toId(pos),
                        args = listOf(Rust.TraitObjectType(pos, bounds = listOf(fullTraitTypeRef.deepCopy()))),
                    ),
                ),
            ),
        ).toItem(
            attrs = listOf(buildDerive(pos, listOf("Clone"))),
            pub = chooseVisibility(decl),
        ).also { moduleItems.add(it) }
        // Impl new and static members.
        Rust.Impl(
            pos,
            generics = generics.deepCopy(),
            trait = null,
            type = fullTypeRef.deepCopy(),
            items = buildList {
                // Constructor.
                Rust.Function(
                    pos,
                    id = "new".toId(pos),
                    params = listOf(
                        Rust.FunctionParam(
                            pos,
                            pattern = "selfish".toId(pos),
                            type = Rust.ImplTraitType(
                                pos,
                                bounds = listOf(fullTraitTypeRef.deepCopy(), STATIC_LIFETIME.toId(pos)),
                            ),
                        ),
                    ),
                    returnType = fullTypeRef.deepCopy(),
                    block = Rust.Block(
                        pos,
                        result = Rust.Call(pos, callee = id.deepCopy(), args = listOf("selfish".toId(pos).wrapArc())),
                    ),
                ).also { add(it.toItem(pub = Rust.VisibilityPub(pos))) }
                // Static methods.
                for (member in decl.members) {
                    if (member is TmpL.StaticMethod) {
                        add(translateNormalishMethod(member))
                    }
                }
            },
        ).toItem().also { moduleItems.add(it) }
        // Implement AnyValue.
        val typeRef = id.makeTypeRef(generics)
        Rust.Call(
            pos,
            callee = "temper_core".toKeyId(pos).extendWith("impl_any_value_trait_for_interface!"),
            args = listOf(typeRef.deepCopy()),
        ).also { moduleItems.add(Rust.ExprStatement(pos, it).toItem()) }
        // Implement Deref for all our wrapped traits.
        Rust.Impl(
            pos,
            generics = generics.deepCopy(),
            trait = "std::ops::Deref".toId(pos),
            type = fullTypeRef.deepCopy(),
            items = listOf(
                Rust.TypeAlias(
                    pos,
                    id = "Target".toId(pos),
                    type = Rust.TraitObjectType(pos, bounds = listOf(fullTraitTypeRef.deepCopy())),
                ).toItem(),
                Rust.Function(
                    pos,
                    id = "deref".toId(pos),
                    params = selfParams.deepCopy(),
                    returnType = Rust.RefType(pos, type = "Self".toKeyId(pos).extendWith("Target")),
                    block = Rust.Block(pos, result = "self".toKeyId(pos).member("0").deref().ref()),
                ).toItem(),
            ),
        ).toItem().also { moduleItems.add(it) }
    }

    private fun buildBounds(typeParam: TmpL.TypeFormal): List<Rust.TypeParamBound> {
        val bounds = buildList {
            bounds@ for (bound in typeParam.upperBounds) {
                when (bound.typeName.sourceDefinition) {
                    // Special cases. TODO Others?
                    WellKnownTypes.anyValueTypeDefinition -> {}
                    WellKnownTypes.equatableTypeDefinition -> add(PARTIAL_EQ_NAME.toId(bound.pos))
                    WellKnownTypes.mapKeyTypeDefinition -> {
                        add(EQ_NAME.toId(bound.pos))
                        add(HASH_NAME.toId(bound.pos))
                    }
                    // Translate general cases to trait names.
                    else -> translateTypeAsTraitName(bound).also { add(it) }
                }
            }
        }
        return bounds
    }

    private fun buildBoundsCommon(pos: Position): List<Rust.TypeParamBound> =
        listOf("Clone", SEND_NAME, SYNC_NAME, STATIC_LIFETIME).map { it.toId(pos) }

    internal fun buildCastCallee(
        pos: Position,
        found: Description,
        wanted: Description,
    ): Rust.Path = when {
        found.definition() == WellKnownTypes.noStringIndexTypeDefinition &&
            wanted.definition() == WellKnownTypes.stringIndexOptionTypeDefinition
        -> "temper_core".toKeyId(pos).extendWith(listOf("string", "cast_none_as_index_option"))

        found.definition() == WellKnownTypes.stringIndexTypeDefinition &&
            wanted.definition() == WellKnownTypes.stringIndexOptionTypeDefinition -> makePath(pos, "Some")

        found.definition() == WellKnownTypes.stringIndexOptionTypeDefinition &&
            wanted.definition() == WellKnownTypes.stringIndexTypeDefinition
        -> "temper_core".toKeyId(pos).extendWith(listOf("string", "cast_as_index"))

        found.definition() == WellKnownTypes.stringIndexOptionTypeDefinition &&
            wanted.definition() == WellKnownTypes.noStringIndexTypeDefinition
        -> "temper_core".toKeyId(pos).extendWith(listOf("string", "cast_as_no_index"))

        else -> {
            val typeSegment = Rust.GenericArgs(pos, listOf(translateType(wanted.type!!, pos)))
            // TODO Some way to make fewer intermediate lists?
            "temper_core".toKeyId(pos).extendWith("cast").extendWith(listOf(typeSegment))
        }
    }

    private fun buildDerive(pos: Position, deriveArgs: List<String>) = Rust.AttrOuter(
        pos,
        expr = Rust.Call(
            pos,
            callee = "derive".toId(pos),
            args = deriveArgs.map { it.toId(pos) },
        ),
    )

    private fun buildForwarder(method: TmpL.InstanceMethod, returnType: Type2? = null): List<Rust.Item> {
        var effectiveReturnType: Type2? = null
        val block = Rust.Block(
            method.pos,
            result = "self".toKeyId(method.pos).methodCall(
                translateMethodId(method),
                method.parameters.parameters.mapNotNull { param ->
                    when (param.name) {
                        method.parameters.thisName -> null
                        else -> translateId(param.name)
                    }
                },
            ).let { result ->
                when (returnType) {
                    null -> result
                    else -> {
                        val sig = method.sig
                        val given = sig.returnType2.described()
                        val wanted = returnType.described()
                        // We want the return type of the forward method, but maybe with `?` and/or `throws` added.
                        // This supports limited covariant return types.
                        effectiveReturnType = (given as? TypeDescription)?.maybeWrapStatic(wanted)
                        result.maybeWrap(given = given, wanted = wanted, translator = this)
                    }
                }
            },
        )
        return translateMethodLike(method, block = block, forTrait = true, returnType = effectiveReturnType)
    }

    private fun buildGenerics(typeParameters: TmpL.ATypeParameters) =
        buildGenerics(typeParameters.ot)

    private fun buildGenerics(typeParameters: TmpL.TypeParameters): List<Rust.GenericParam> {
        return typeParameters.typeParameters.map { param ->
            val id = translateTypeOutName(param.name.name).toId(param.pos)
            val bounds = buildBounds(param) + buildBoundsCommon(param.pos)
            val typeParam = Rust.TypeParam(param.pos, id = id, bounds = bounds)
            decls[param.name.name] = DeclInfo(param, whereItem = typeParam)
            typeParam
        }
    }

    private fun buildMethodCapture(
        captureId: Rust.Id,
        function: TmpL.LocalFunctionDeclaration,
        functionId: Rust.Id,
        capturedId: Rust.Id = captureId,
    ): Rust.Block {
        val pos = function.pos
        return Rust.Block(
            pos,
            // Clone the capture group for each function.
            statements = Rust.LetStatement(
                pos,
                pattern = Rust.IdPattern(pos, id = captureId.deepCopy()),
                type = null,
                value = capturedId.deepCopy().wrapClone(),
            ).let { listOf(it) },
            result = Rust.Closure(
                pos,
                move = Rust.Move(pos),
                params = function.parameters.parameters.map { param ->
                    Rust.FunctionParam(
                        pos,
                        pattern = Rust.IdPattern(pos, id = translateId(param.name)),
                        // Not `isParam` here because these can't be flexible.
                        // TODO Make sure we also call them non-flexibly.
                        type = translateType(param.type),
                    )
                },
                value = captureId.deepCopy().methodCall(
                    functionId.deepCopy(),
                    function.parameters.parameters.map { translateId(it.name) },
                ),
            ).wrapArc(),
        )
    }

    private fun buildTopperGetter(decl: DeclInfo, id: Rust.Id? = null, considerMember: Boolean = false): Rust.Item {
        val pos = decl.decl.pos
        val usedId = id ?: translateId(decl.decl.name)
        // Static field.
        val access = when {
            decl.assignOnce -> {
                Rust.Static(
                    pos,
                    id = usedId,
                    type = decl.type!!.onceLock(),
                    value = Rust.Call(pos, callee = "$ONCE_LOCK_NAME::new".toId(pos), args = listOf()),
                ).also { moduleItems.add(it.toItem()) }
                usedId.methodCall("get").methodCall("unwrap").deref().maybeClone(decl.typeFrom!!)
            }

            else -> {
                Rust.Static(
                    pos,
                    id = usedId,
                    type = decl.type!!.option().wrapRwLockType(),
                    value = "None".toId(pos).wrapLock(),
                ).also { moduleItems.add(it.toItem()) }
                usedId.methodCall("read").methodCall("unwrap").maybeClone(decl.typeFrom!!).methodCall("unwrap")
            }
        }
        // Accessor method for reads.
        return Rust.Function(
            pos,
            id = translateId(decl.decl.name, style = NameStyle.Snake),
            params = listOf(),
            returnType = decl.type,
            block = Rust.Block(pos, result = access),
        ).toItem(pub = chooseVisibility(decl.decl, considerMember = considerMember))
    }

    private fun buildTopperAssign(
        decl: TmpL.MaybeAssignable,
        value: Rust.Expr?,
        id: Rust.Id? = null,
    ): List<Rust.Statement> {
        val pos = decl.pos
        return when (value) {
            null -> listOf()
            else -> {
                val usedId = id ?: translateId(decl.name)
                when {
                    decl.assignOnce -> run {
                        // Panic on fail because Temper should prevent this.
                        // But we can't unwrap to panic here unless all our objects are Debug, which they aren't.
                        val fail = Rust.Closure(pos, params = listOf("_".toId(pos)), value = "panic!".toId(pos).call())
                        usedId.methodCall("set", listOf(value)).methodCall("unwrap_or_else", listOf(fail))
                    }.let { listOf(Rust.ExprStatement(pos, it)) }

                    else -> Rust.Operation(
                        pos,
                        left = usedId.methodCall("write").methodCall("unwrap").deref(),
                        operator = Rust.Operator(pos, RustOperator.Assign),
                        right = value.wrapSome(),
                    ).let { listOf(Rust.Block(pos, statements = listOf(Rust.ExprStatement(pos, it)))) }
                }
            }
        }
    }

    private fun chooseVisibility(decl: TmpL.NameDeclaration, considerMember: Boolean = false): Rust.VisibilityPub? {
        val pos = decl.pos
        return when (decl.name.name) {
            is ExportedName -> Rust.VisibilityPub(pos)
            else -> when {
                considerMember -> (decl as? TmpL.Member)?.let { chooseVisibilityForMember(it, forTrait = false) }
                else -> null
            }
        }
    }

    private fun chooseVisibilityForMember(member: TmpL.Member, forTrait: Boolean): Rust.VisibilityPub? {
        val pos = member.pos
        return when {
            forTrait -> null
            member.visibility.visibility >= TmpL.Visibility.Public -> Rust.VisibilityPub(pos)
            else -> null
        }
    }

    private fun defineSealedEnum(decl: TmpL.TypeDeclaration): Rust.Id {
        val id = translateTypeOutName(decl.name.name).toId(decl.name.pos)
        val enumId = "$id$ENUM_NAME_SUFFIX".toId(decl.name.pos)
        val pos = decl.pos
        val pub = chooseVisibility(decl)
        // Enum type.
        Rust.Enum(
            pos,
            id = enumId,
            items = decl.typeShape.sealedSubTypes!!.map { sub ->
                val subId = translateTypeOutName(sub.name).toId(pos)
                Rust.EnumItemTuple(pos, id = subId, fields = listOf(Rust.TupleField(pos, type = subId.deepCopy())))
            },
        ).let { moduleItems.add(it.toItem(pub = pub)) }
        // Convenient return value.
        return enumId
    }

    /** Direct references to static members can need to context for us to know how to handle them. */
    private fun gatherStaticDecls(decl: TmpL.TypeDeclaration) {
        members@ for (member in decl.members) {
            (member as? TmpL.StaticMember) ?: continue@members
            decls[member.name.name] = DeclInfo(
                decl = member,
                // TODO: do we need type parameters here?
                typeFrom = MkType2(decl.typeShape).get(),
            )
        }
    }

    fun isClosure(expr: Rust.Expr): Boolean {
        return when (val decl = expr.effectiveId()?.let { decls[it.outName.sourceName] }) {
            // If we couldn't find an id then presume any call result isn't a plain function.
            // Complications here include fully qualified names that we don't currently track.
            // That's why the exception for effective paths.
            null -> expr is Rust.Call && expr.effectivePath() == null
            else -> decl.local
        }
    }

    /** If the type is equatable by pointer identity. */
    fun isIdentifiable(type: Type2): Boolean {
        // TODO Track which are actually identifiable, and keep a cache of answers.
        // TODO Or maybe offload to some trait in Rust?
        val described = type.described()
        return when (val core = described.type) {
            is DefinedNonNullType -> when (val def = core.definition) {
                WellKnownTypes.booleanTypeDefinition,
                WellKnownTypes.float64TypeDefinition,
                WellKnownTypes.intTypeDefinition,
                WellKnownTypes.nullTypeDefinition,
                WellKnownTypes.stringIndexOptionTypeDefinition,
                WellKnownTypes.stringIndexTypeDefinition,
                WellKnownTypes.stringTypeDefinition,
                -> false
                // Cheat for now and allow all non-value-equatable nominal types as identifiable.
                is TypeFormal -> !def.upperBounds.any { it.definition == WellKnownTypes.equatableTypeDefinition }
                else -> true
            }
            else -> false
        }
    }

    private fun translateActual(
        actual: TmpL.Actual,
        avoidClone: Boolean = false,
        needFull: Boolean = false,
        wantedType: Type2? = null,
    ): Rust.Expr {
        val wanted = wantedType?.described()
        // We want a nullable type that we also allow impl conversions into, so we need explicit null types.
        val nullableIntoWanted = !needFull && when {
            wanted?.nullable == true -> when (wanted.definition()) {
                WellKnownTypes.listTypeDefinition,
                WellKnownTypes.listBuilderTypeDefinition,
                WellKnownTypes.listedTypeDefinition,
                WellKnownTypes.stringTypeDefinition,
                -> true

                else -> false
            }

            else -> false
        }
        val type = (actual as TmpL.Expression).type // TODO Could crash on RestSpread
        val pos = actual.pos
        return when {
            !needFull && actual.supportCode() == Listify -> {
                // Simplify a Vec to an array that can get turned into a Vec, and make items conform.
                // And use the wanted type here for simulated covariance, as Rust doesn't do covariance here.
                // TODO Handle invariance and target-typed inference in the frontend.
                val itemWantedType = wantedType?.bindings?.getOrNull(0)
                Rust.Array(
                    pos,
                    values = (actual as TmpL.CallExpression).parameters.map { value ->
                        // Array members won't get auto wrapped. Only args to functions we control.
                        translateActual(value, needFull = true, wantedType = itemWantedType)
                    },
                )
            }

            // For string literals that are call args, we can handle raw string literals.
            // Default translation wraps them in `Arc::new("...".to_string())`.
            !needFull && actual is TmpL.ValueReference && actual.value.typeTag is TString ->
                Rust.StringLiteral(pos, TString.unpack(actual.value))

            actual.isNullValue() -> {
                when {
                    // Because of impl conversions, we also have to type our nulls for such cases. Silly Rust.
                    nullableIntoWanted -> Rust.PathSegments(
                        pos,
                        segments = listOf(
                            "None".toId(pos),
                            Rust.GenericArgs(pos, args = listOf(translateType(wanted!!.type!!, pos))),
                        ),
                    )

                    else -> if (wantedType?.canOnlyBeNull != false) {
                        // TODO Why is there sometimes no wanted type coming through here?
                        "()"
                    } else {
                        "None"
                    }.toId(pos)
                }
            }

            // This goes after the more specialized ValueReference above.
            else -> translateExpression(actual, avoidClone = avoidClone)
        }.maybeWrap(given = type, wanted = wantedType, translator = this)
    }

    private fun translateAssignment(statement: TmpL.Assignment, right: Rust.Expr? = null): List<Rust.Statement> {
        statement.left.name in failVars && return listOf()
        val decl = decls[statement.left.name]
        val mutableCapture = decl?.mutableCapture == true
        // We can't be assigning to a capture unless it's also a mutable capture, so condition on that.
        val capture = mutableCapture && functionContextStack.lastOrNull()?.let { decl.name in it.captures } == true
        val topper = decl?.topper == true
        val pos = statement.pos
        val id = translateId(statement.left)
        // As for usages, we might need to refer to capture fields for assignment.
        val ref = when {
            capture -> "self".toKeyId(pos).member(id, notMethod = true)
            else -> id
        }
        val value = right ?: run {
            val foundRight = statement.right as TmpL.Expression
            val value = translateExpression(foundRight)
            when (decl) {
                null -> value
                else -> value.maybeWrap(
                    given = foundRight.type.described(),
                    wanted = decl.typeFrom?.described(),
                    translator = this,
                )
            }
        }
        if ((decl?.typeFrom as? Type2)?.definition == WellKnownTypes.neverTypeDefinition) {
            // Skip the assignment part because never vars are experimental in Rust.
            return listOf(Rust.ExprStatement(pos, value))
        }
        if (topper) {
            return buildTopperAssign(decl = decl.decl as TmpL.ModuleLevelDeclaration, id = id, value = value)
        }
        val rustStatement = Rust.ExprStatement(
            pos,
            expr = Rust.Operation(
                pos,
                left = when {
                    mutableCapture -> ref.methodCall("write").methodCall("unwrap").deref()
                    else -> id
                },
                operator = Rust.Operator(pos, RustOperator.Assign),
                // TODO Track type to see if we need to Box::new.
                // Seems we can even reference locked vars in the value without deadlock. Must happen first.
                right = value,
            ),
        )
        return when {
            mutableCapture -> Rust.Block(pos, statements = listOf(rustStatement))
            else -> rustStatement
        }.let { listOf(it) }
    }

    private fun translateBody(
        block: TmpL.BlockStatement,
        prefix: List<Rust.Statement> = listOf(),
        statementProcessor: StatementProcessor? = null,
    ): Rust.Block? {
        return when {
            block.isPureVirtual() -> null
            else ->
                translateBlock(block, prefix = prefix, skipLastReturn = true, statementProcessor = statementProcessor)
        }
    }

    private fun translateBlock(
        block: TmpL.BlockStatement,
        prefix: List<Rust.Statement> = listOf(),
        result: Rust.Expr? = null,
        skipLastReturn: Boolean = result != null,
        statementProcessor: StatementProcessor? = null,
    ): Rust.Block {
        return Rust.Block(
            block.pos,
            statements = buildList {
                addAll(prefix)
                when (statementProcessor) {
                    null -> processStatements(block.statements, results = this, skipLastReturn = skipLastReturn)
                    else -> statementProcessor.processStatements(
                        block.statements,
                        results = this,
                        skipLastReturn = skipLastReturn,
                    )
                }
            },
            result = result,
        )
    }

    private fun translateBlock(block: TmpL.Statement): Rust.Block {
        return when (block) {
            is TmpL.BlockStatement -> translateBlock(block)
            else -> translateStatement(block).toBlock(block.pos)
        }
    }

    private fun translateBreakStatement(statement: TmpL.BreakStatement): Rust.Statement {
        return Rust.ExprStatement(
            statement.pos,
            expr = Rust.BreakExpr(statement.pos, id = translateLabel(statement.label)),
        )
    }

    private fun translateBubbleSentinel(expression: TmpL.BubbleSentinel): Rust.Expr {
        return translateBubbleSentinel(expression.pos)
    }

    private fun translateBubbleSentinel(pos: Position): Rust.Call {
        return functionContextStack.lastOrNull()?.let { functionContext ->
            when {
                functionContext.returnType.bubbly -> null
                else -> "panic!".toId(pos).call()
            }
        } ?: Rust.Call(pos, callee = "Err".toId(pos), args = listOf(makeError(pos)))
    }

    private fun translateCallAwakeUpon(call: TmpL.CallExpression): Rust.Expr {
        val pos = call.pos
        val args = call.mapParameters(optionalAsNullable = true) { arg, wantedType, _ ->
            translateActual(arg, needFull = wantedType == null, wantedType = wantedType)
        }
        // Presume good arg count since these calls are generated internally.
        val closure = Rust.Closure(
            pos,
            move = Rust.Move(pos),
            params = listOf(),
            value = Rust.Block(pos, statements = listOf(Rust.ExprStatement(pos, args[1].methodCall("next")))),
        ).wrapArc()
        return args[0].methodCall("on_ready", listOf(closure))
    }

    private fun translateCallExpression(call: TmpL.CallExpression): Rust.Expr {
        val fn = call.fn
        return when (fn) {
            is TmpL.InlineSupportCodeWrapper -> return when (val supportCode = fn.supportCode) {
                AwakeUponSupportCode -> translateCallAwakeUpon(call)
                GetPromiseResultSyncSupportCode -> translateCallGetPromiseResultSync(call)
                is RustInlineSupportCode -> translateCallExpressionForSupportCode(call, supportCode)
                else -> error("$supportCode")
            }

            else -> null
        } ?: run {
            val callee = translateCallable(fn)
            val rest = mutableListOf<Rust.Expr>()
            // Calls to trait methods and closures can't use conversion overloads.
            val limited = when (fn) {
                // TODO How to check from here if the method is static? We could avoid `needFull` in such cases.
                is TmpL.MethodReference -> fn.method?.enclosingType?.let { it.abstractness == Abstractness.Abstract }
                else -> isClosure(callee)
            } ?: false
            val allArgs = call.mapParameters(optionalAsNullable = true) { arg, wantedType, f ->
                val isResty = f?.kind == ValueFormalKind.Rest
                val needFull = isResty || limited || wantedType is TypeParamRef
                val actual = translateActual(arg, needFull = needFull, wantedType = wantedType)
                if (isResty) {
                    rest.add(actual)
                }
                actual
            }
            val args = when (fn.type.restInputsType) {
                null -> allArgs
                else -> buildList {
                    allArgs.subList(0, allArgs.size - rest.size).also { addAll(it) }
                    Rust.MacroCall(
                        call.pos,
                        path = "vec!".toId(call.pos),
                        args = Rust.Array(call.pos, values = rest),
                    ).also { add(it) }
                }
            }
            if (fn is TmpL.FunInterfaceCallable) {
                Rust.Call(call.pos, callee = callee, args = args, needsParens = true)
            } else {
                Rust.Call(call.pos, callee = callee, args = args)
            }
        }
    }

    private fun translateCallExpressionForSupportCode(
        call: TmpL.CallExpression,
        supportCode: RustInlineSupportCode,
    ): Rust.Expr {
        val wantUnstrung = supportCode is ConsoleLog || supportCode is StrCat
        var first = true
        return supportCode.inlineToTree(
            call.pos,
            arguments = call.mapParameters(
                keepsThis = true,
                optionalAsNullable = true,
            ) { arg, wantedType, _ ->
                val wasFirst = first
                first = false
                val wantedTypeIsFunctional = lazy {
                    wantedType is NonNullType && wantedType.isFunctionType
                }
                supportCode.translateArg(arg, wantedType = wantedType, translator = this)?.let { custom ->
                    return@mapParameters TypedArg(custom, arg.typeOrInvalid)
                }
                val adjustedArg = when {
                    wantUnstrung -> arg.stripToString()
                    else -> arg
                }
                val avoidClone = wasFirst && !supportCode.cloneEvenIfFirst
                // Better would be to know *which* params are generic, but if an arg is some type param T, then we can't
                // use `To...` traits on it. For now, coarse grained is good enough. We want to rely on `To...` traits
                // at least some, so we ensure it works for others, but we don't need to use it everywhere.
                val needFull = supportCode.hasGeneric

                val actualWantedType = when {
                    supportCode.avoidTypeWrapping -> null
                    // When avoiding clone, we're doing specialized handling.
                    avoidClone -> null
                    // Also special handling for function types for support codes.
                    !supportCode.wrapClosures && wantedTypeIsFunctional.value -> null
                    else -> wantedType ?: supportCode.argType(call.type)
                }

                val actual = translateActual(
                    adjustedArg,
                    avoidClone = avoidClone,
                    needFull = needFull,
                    wantedType = actualWantedType,
                )
                // The type might be a lie if we stripped toString, but rust support codes cope.
                TypedArg(actual, arg.typeOrInvalid)
            },
            returnType = call.type,
            translator = this,
        ) as Rust.Expr
    }

    private fun translateCallGetPromiseResultSync(call: TmpL.CallExpression): Rust.Expr {
        val args = call.mapParameters(optionalAsNullable = true) { arg, wantedType, _ ->
            translateActual(arg, needFull = wantedType == null, wantedType = wantedType)
        }
        // Presume good arg count since these calls are generated internally.
        return args[1].methodCall("get")
    }

    private fun translateCallable(callable: TmpL.Callable): Rust.Expr {
        return when (callable) {
            is TmpL.FnReference -> translateReference(callable, isCall = true)
            is TmpL.ConstructorReference -> translateConstructorReference(callable)
            is TmpL.FunInterfaceCallable -> translateExpression(callable.expr)
            is TmpL.MethodReference -> {
                val member = translateDotName(callable.methodName)
                when (val subject = callable.subject) {
                    // Method calls typically use refs for self, so no need to clone.
                    is TmpL.Expression -> {
                        callable.method?.enclosingType?.let traitImport@{ owner ->
                            // TODO Do we need to filter out implicits? So far, no examples of hitting that case.
                            if (owner.abstractness == Abstractness.Abstract && owner.pos.loc != module.pos.loc) {
                                // For actual interface methods, we shouldn't have name collisions.
                                // TODO Can/should we provide traits for extension methods?
                                // TODO If so, just translate those as qualified calls anyway?
                                val trait =
                                    (translateTypeDefinition(owner, subject.pos) as? Rust.Path) ?: return@traitImport
                                traitImports.add(trait.suffixed(TRAIT_NAME_SUFFIX))
                            }
                        }
                        translateExpression(subject, avoidClone = true).member(member)
                    }
                    is TmpL.TypeName -> translateTypeName(subject).extendWith(listOf(member))
                }
            }
            is TmpL.GarbageCallable -> translateGarbage(callable)
            is TmpL.InlineSupportCodeWrapper ->
                error("should go through translateCallExpressionForSupportCode")
        }
    }

    private fun translateCastExpression(cast: TmpL.CastExpression): Rust.Expr {
        // TODO Bubbly found. Maybe some map expression? We don't ever expect bubbly wanted.
        // TODO Unify any of this with `translateInstanceOfExpression`?
        val found = cast.expr.type.described()
        val wanted = cast.checkedFrontendType.described()
        val pos = cast.pos
        val callee = buildCastCallee(pos, found, wanted)
        return translateExpression(cast.expr).let { expr ->
            // TODO Upcast support which needs to be like clone which needs to change to WhateverTrait::clone_box(...)
            when {
                // No need to cast or check anything.
                found == wanted -> return@translateCastExpression expr

                // No need for type casting, but we still need to check for null.
                found.definition() == wanted.definition() -> expr

                // Chain through `and_then`.
                found.nullable && !wanted.nullable -> expr.methodCall(
                    "and_then",
                    listOf(
                        Rust.Closure(
                            pos,
                            params = listOf(Rust.FunctionParam(pos, pattern = "x".toId(pos), type = null)),
                            value = Rust.Call(pos, callee = callee, args = listOf("x".toId(pos))),
                        ),
                    ),
                )

                // Just call directly.
                else -> Rust.Call(pos, callee = callee, args = listOf(expr))
            }
        }.let { result ->
            when {
                cast.type.described().bubbly -> result.wrapOkOrElse(pos)
                else -> result.methodCall("unwrap") // such as for assertAs
            }
        }
    }

    private fun translateComputedJumpStatement(statement: TmpL.ComputedJumpStatement): Rust.Statement {
        return Rust.Match(
            statement.pos,
            expr = translateExpression(statement.caseExpr),
            arms = buildList {
                // Conditional cases.
                for (case in statement.cases) {
                    Rust.MatchArm(
                        case.pos,
                        pattern = Rust.PatternAlt(
                            case.pos,
                            patterns = case.values.map { Rust.NumberLiteral(it.pos, it.index) },
                        ),
                        expr = translateBlock(case.body),
                    ).also { add(it) }
                }
                // Else case.
                val elseCase = statement.elseCase
                Rust.MatchArm(
                    elseCase.pos,
                    pattern = "_".toId(elseCase.pos),
                    expr = translateBlock(elseCase.body),
                ).also { add(it) }
            },
        )
    }

    private fun translateConstructor(
        constructor: TmpL.Constructor,
        enclosingType: Rust.Type? = null,
        enclosingTypePub: Rust.VisibilityPub? = null,
        generics: List<Rust.GenericParam> = listOf(),
    ): List<Rust.Item> {
        val pos = constructor.pos
        val type = constructor.parameters.parameters[0].type.ot
        val constructorSig = constructor.memberShape.descriptor as Signature2
        val typeRaw = constructorSig.requiredInputTypes[0]
        val statementProcessor = object : StatementProcessor {
            override fun processStatements(
                statements: List<TmpL.Statement>,
                results: MutableList<Rust.Statement>,
                skipLastReturn: Boolean, // ignorable here
            ) {
                val (initStatements, useStatements) = statements.splitConstructorBody()
                val context = functionContextStack.last()
                val inits = buildMap {
                    for (initStatement in initStatements) {
                        initStatement.anyChildDepth(
                            within = { tree ->
                                when (tree) {
                                    is TmpL.SetBackedProperty -> {
                                        val name = (tree.left.property as TmpL.InternalPropertyId).name.name
                                        compute(name) { _, value: Int? -> value?.let { it + 1 } ?: 1 }
                                        false
                                    }
                                    else -> true
                                }
                            },
                        )
                    }
                }
                for ((name, count) in inits) {
                    // Pretty names might keep them unique?
                    val id = translateIdFromName(pos, name, style = NameStyle.Snake)
                    val pattern = when {
                        count > 1 -> Rust.IdPattern(pos, Rust.IdPatternMut(pos), id)
                        else -> id
                    }
                    Rust.LetStatement(
                        pos,
                        pattern = pattern,
                        type = null,
                        value = null,
                    ).also { results.add(it) }
                }
                context.constructorMode = ConstructorMode.Init
                this@RustTranslator.processStatements(initStatements, results = results)
                val typeId = translateTypeName((type as TmpL.NominalType).typeName) as Rust.Id
                val structId = "${typeId.outName.outputNameText}Struct".toId(pos)
                Rust.LetStatement(
                    pos,
                    // TODO Ensure unique name, and track it.
                    pattern = "selfish".toId(pos),
                    type = null,
                    value = Rust.StructExpr(
                        pos,
                        id = structId,
                        members = buildList {
                            // Explicit fields.
                            for (name in inits.keys) {
                                Rust.StructExprField(
                                    pos,
                                    id = translateIdFromName(pos, name, style = NameStyle.Snake),
                                    expr = null,
                                ).let { add(it) }
                            }
                            // Phantom fields.
                            // TODO Track phantom data better and reuse that info here.
                            for (typeParam in (type.typeName.sourceDefinition as TypeShape).typeParameters) {
                                val typeName = translateTypeOutName(typeParam.name as ResolvedName).outputNameText
                                addPhantomEntry(pos, typeName)
                            }
                        },
                    ).let { core ->
                        when {
                            insideMutableType -> core.wrapLock()
                            else -> core
                        }
                    }.wrapArc().let { Rust.Call(pos, callee = typeId, args = listOf(it)) },
                ).let { results.add(it) }
                // Finish constructor.
                context.constructorMode = ConstructorMode.Use
                this@RustTranslator.processStatements(useStatements, results = results, skipLastReturn = true)
            }
        }
        // We need to return an instance, not Void, but we still might be bubbly.
        val returnType = when {
            constructorSig.returnType2.described().bubbly ->
                MkType2(WellKnownTypes.resultTypeDefinition)
                    .actuals(listOf(typeRaw, WellKnownTypes.bubbleType2))
                    .get()
            else -> typeRaw
        }
        // As appropriate, also make a builder for named properties.
        if (
            constructor.visibility.visibility == TmpL.Visibility.Public &&
            enclosingTypePub != null &&
            enclosingTypePub.scope == null
        ) {
            processForClassBuilder(constructor, enclosingType, generics, returnType = returnType)
        }
        // Actually translate the constructor now, with all our adjustments.
        return translateFunctionDeclarationOrMethod(
            constructor,
            id = "new".toId(pos),
            pub = chooseVisibilityForMember(member = constructor, forTrait = false),
            returnType = returnType,
            skipSelf = true,
            statementProcessor = statementProcessor,
        ).let { listOf(it) }
    }

    private fun translateConstructorReference(ref: TmpL.ConstructorReference): Rust.Expr {
        return (translateTypeDefinition(ref.typeName.sourceDefinition, ref.pos) as Rust.Path).extendWith("new")
    }

    private fun translateContinueStatement(statement: TmpL.ContinueStatement): Rust.Statement {
        return Rust.ExprStatement(
            statement.pos,
            expr = Rust.ContinueExpr(statement.pos, id = translateLabel(statement.label)),
        )
    }

    private fun translateDeclNamePattern(decl: TmpL.VarLike, style: NameStyle? = null): Rust.Pattern {
        val id = translateId(decl.name, style = style)
        return when {
            decl.assignOnce -> id
            else -> Rust.IdPattern(decl.name.pos, mut = Rust.IdPatternMut(decl.pos), id = id)
        }
    }

    private fun translateDotName(name: TmpL.DotName): Rust.Id {
        return name.dotNameText.camelToSnake().toId(name.pos)
    }

    private fun translateExpressionStatement(statement: TmpL.ExpressionStatement): Rust.Statement {
        val expr = translateExpression(statement.expression)
        return Rust.ExprStatement(statement.pos, expr)
    }

    internal fun translateExpression(expression: TmpL.Expression, avoidClone: Boolean = false): Rust.Expr {
        return when (expression) {
            is TmpL.AwaitExpression -> TODO()
            is TmpL.BubbleSentinel -> translateBubbleSentinel(expression)
            is TmpL.CallExpression -> translateCallExpression(expression)
            is TmpL.CastExpression -> translateCastExpression(expression)
            is TmpL.FunInterfaceExpression -> translateCallable(expression.callable)
            is TmpL.GarbageExpression -> translateGarbage(expression)
            is TmpL.GetProperty -> translateGetProperty(expression, avoidClone = avoidClone)
            is TmpL.InstanceOfExpression -> translateInstanceOfExpression(expression)
            is TmpL.InfixOperation -> translateInfixOperation(expression)
            is TmpL.PrefixOperation -> TODO()
            is TmpL.Reference -> translateReference(expression, avoidClone = avoidClone)
            is TmpL.RestParameterCountExpression -> TODO()
            is TmpL.RestParameterExpression -> TODO()
            is TmpL.This -> translateThis(expression, avoidClone = avoidClone)
            is TmpL.UncheckedNotNullExpression -> translateExpression(expression.expression).methodCall("unwrap")
            is TmpL.ValueReference -> translateValueReference(expression)
        }
    }

    private fun translateFunctionDeclarationOrMethod(
        decl: TmpL.FunctionDeclarationOrMethod,
        block: Rust.Block? = null,
        captures: Map<ResolvedName, DeclInfo>? = null,
        id: Rust.Id? = null,
        forTrait: Boolean = false,
        pub: Rust.VisibilityPub? = null,
        methodizeds: Set<ResolvedName> = emptySet(),
        returnType: Type2? = null,
        skipSelf: Boolean = false,
        statementProcessor: StatementProcessor? = null,
    ): Rust.Item {
        // Captures above are ones used at higher scopes, but we also need to know in advance about mutable vars
        // declared at this scope, because we even need to change how they're declared.
        val mutableCaptures = decl.mutableCaptures()
        val paramInfo = ParameterInfo(decl, forTrait = forTrait, mutableCaptures = mutableCaptures, skipSelf = skipSelf)
        val effectiveReturnType = returnType ?: decl.sig.returnType2
        val describedReturnType = effectiveReturnType.described()
        val function = Rust.Function(
            decl.pos,
            id = id ?: translateId(decl.name),
            generics = paramInfo.generics,
            params = when (captures) {
                null -> paramInfo.params
                else -> buildList {
                    add(Rust.RefType(decl.pos, "self".toKeyId(decl.pos)))
                    addAll(paramInfo.params)
                }
            },
            returnType = when {
                describedReturnType.isUnit() -> null
                else -> translateType(effectiveReturnType, decl.returnType.pos)
            },
            whereItems = paramInfo.whereItems,
            // TODO Can easily prettify any final return as a result expression.
            block = block ?: decl.body?.let { body ->
                functionContextStack.add(
                    FunctionContext(
                        captures = captures ?: mapOf(),
                        decl = decl,
                        methodizeds = methodizeds,
                        mutableCaptures = mutableCaptures,
                        returnType = describedReturnType,
                    ),
                )
                try {
                    translateBody(body, prefix = paramInfo.conversions, statementProcessor = statementProcessor)
                } finally {
                    functionContextStack.compatRemoveLast()
                }
            },
        )
        return Rust.Item(
            decl.pos,
            pub = pub ?: chooseVisibility(decl),
            item = function,
        )
    }

    private fun translateFunctionType(type: Signature2, pos: Position): Rust.Type {
        // We don't need mut functions if all mutation goes through Arc<Mutex|RwLock> anyway.
        // TODO We also don't need Arc functions if they're only used for local calls.
        return Rust.TraitObjectType(
            pos,
            bounds = listOf(
                Rust.FunctionType(
                    pos,
                    params = type.allValueFormals.map { translateType(it.type, pos) },
                    returnType = translateType(type.returnType2, pos),
                ),
                SEND_NAME.toId(pos),
                SYNC_NAME.toId(pos),
            ),
        ).wrapArcType()
    }

    private fun translateFunctionType(type: TmpL.FunctionType): Rust.Type {
        // We don't need mut functions if all mutation goes through Arc<Mutex|RwLock> anyway.
        // TODO We also don't need Arc functions if they're only used for local calls.
        val pos = type.pos
        val returnType = type.returnType
        return Rust.TraitObjectType(
            pos,
            bounds = listOf(
                Rust.FunctionType(
                    pos,
                    params = type.valueFormals.formals.map { translateType(it.type) },
                    returnType = translateType(returnType),
                ),
                SEND_NAME.toId(pos),
                SYNC_NAME.toId(pos),
            ),
        ).wrapArcType()
    }

    private fun translateGarbage(garbage: TmpL.Garbage): Rust.Call {
        val pos = garbage.pos
        return "panic!".toId(pos).call(listOf(Rust.StringLiteral(pos, "Garbage")))
    }

    private fun translateGetProperty(expression: TmpL.GetProperty, avoidClone: Boolean): Rust.Expr {
        val ref = translatePropertyReference(expression, lockName = "read")
        val reallyAvoidClone = avoidClone || expression.property is TmpL.ExternalPropertyId
        return ref.maybeClone(expression.type, avoidClone = reallyAvoidClone)
    }

    private fun translateGetter(
        getter: TmpL.Getter,
        block: Rust.Block? = null,
        forTrait: Boolean = false,
        returnType: Type2? = null,
    ): Rust.Item {
        val pub = chooseVisibilityForMember(getter, forTrait = forTrait)
        val id = translateGetterId(getter)
        return translateFunctionDeclarationOrMethod(getter, block = block, id = id, pub = pub, returnType = returnType)
    }

    private fun translateGetterId(getter: TmpL.Getter) = translateDotName(getter.dotName)

    private fun translateHandlerScope(statement: TmpL.HandlerScope, check: TmpL.Statement): Rust.Statement {
        return Rust.ExprStatement(
            statement.pos,
            expr = translateHandlerScopeExpression(statement = statement, check = check),
        )
    }

    private fun translateHandlerScopeAssignment(
        statement: TmpL.Assignment,
        check: TmpL.Statement,
    ): Rust.Statement {
        val decl = decls[statement.left.name]
        val right = statement.right as TmpL.HandlerScope
        val translatedRight = translateHandlerScopeExpression(
            statement = right,
            check = check,
            assignment = statement,
            wantedType = decl?.typeFrom as? Type2,
        )
        return when ((check as? TmpL.IfStatement)?.hasElse) {
            // Use internal assignment if we have actual else content. Currently just for coroutine state machines.
            true -> Rust.ExprStatement(statement.pos, translatedRight)
            else -> translateAssignment(statement = statement, right = translatedRight).first()
        }
    }

    private fun translateHandlerScopeExpression(
        statement: TmpL.HandlerScope,
        check: TmpL.Statement,
        assignment: TmpL.Assignment? = null,
        wantedType: Type2? = null,
    ): Rust.Expr {
        val pos = statement.pos
        // TODO Handled can also be SetAbstractProperty. Do we have examples of this?
        val tmplHandled = statement.handled as TmpL.Expression
        val handled = translateExpression(tmplHandled)
        // Sometimes we generate handling for nonbubbly things, but don't allow that here.
        if (!tmplHandled.type.described().bubbly) {
            // TODO Clean front end for anything that gets here.
            return handled
        }
        // Ok case.
        // For situations we conjure so far, we don't expect name collisions.
        val ifCheck = check as TmpL.IfStatement
        val capture = "x".toId(pos)
        val okValue = capture.deepCopy().maybeWrap(tmplHandled.type, wanted = wantedType, translator = this)
        val okExpr = when {
            ifCheck.hasElse -> translateHandlerScopeOkExtra(
                alternate = ifCheck.alternate!!,
                assignment = assignment,
                okValue = okValue,
            )
            else -> okValue
        }
        // Err case.
        val errExpr = ifCheck.consequent.let { failer ->
            val trimmedFailer = when {
                // We might not always want to unwrap blocks, but in this case, we do.
                failer is TmpL.BlockStatement && failer.statements.size == 1 -> failer.statements.first()
                else -> failer
            }
            if (
                (trimmedFailer is TmpL.ReturnStatement && trimmedFailer.expression is TmpL.BubbleSentinel) ||
                trimmedFailer is TmpL.ModuleInitFailed
            ) {
                // Just propagate pretty like.
                return handled.propagate().maybeWrap(tmplHandled.type, wanted = wantedType, translator = this)
            }
            translateStatement(trimmedFailer).firstOrElse(
                // Use just a single expr if we can, which should be the common case of return or break.
                first = { (it as? Rust.ExprStatement)?.expr?.deepCopy() },
                // TODO Do we ever actually have other cases?
                default = { it.toBlock(failer.pos) },
            )
        }
        // Put it all together.
        val okPattern = Rust.TupleStructPattern(pos, "Ok".toId(pos), listOf(capture))
        return Rust.Match(
            pos,
            expr = handled,
            arms = listOf(
                Rust.MatchArm(pos, pattern = okPattern, expr = okExpr),
                Rust.MatchArm(pos, pattern = "_".toId(pos), expr = errExpr),
            ),
        )
    }

    private fun translateHandlerScopeOkExtra(
        alternate: TmpL.Statement,
        assignment: TmpL.Assignment?,
        okValue: Rust.Expr,
    ): Rust.Expr {
        val internalAssignment = assignment?.let { translateAssignment(it, okValue) }
        val extra = translateStatement(alternate).map { statement ->
            when (statement) {
                is Rust.Block -> statement.simplify()
                else -> statement
            }
        }
        return when (assignment) {
            null -> extra
            else -> buildList {
                addAll(internalAssignment!!)
                addAll(extra)
            }
        }.toBlock(alternate.pos)
    }

    private fun translateId(id: TmpL.Id, style: NameStyle? = null): Rust.Id {
        return translateIdAsPath(id, style = style) as Rust.Id
    }

    private fun translateIdAsPath(
        id: TmpL.Id,
        asRef: Boolean = false,
        style: NameStyle? = null,
        wrapAnyTopper: Boolean = false,
    ): Rust.Path {
        return translateIdFromNameAsPath(
            id.pos,
            asRef = asRef,
            name = id.name,
            style = style,
            wrapAnyTopper = wrapAnyTopper,
        )
    }

    private fun translateIdFromName(pos: Position, name: ResolvedName, style: NameStyle? = null): Rust.Id {
        return translateIdFromNameAsPath(pos, name = name, style = style) as Rust.Id
    }

    private fun translateIdFromNameAsPath(
        pos: Position,
        name: ResolvedName,
        asRef: Boolean = false,
        style: NameStyle? = null,
        wrapAnyTopper: Boolean = false,
    ): Rust.Path {
        val decl = decls[name]
        var pathify: (String) -> Rust.Path = { text -> text.toId(pos, sourceName = name) }
        val effectiveName = decl?.importedName ?: name
        var effectiveStyle = when {
            decl?.decl is TmpL.StaticMember -> {
                if (asRef) {
                    // Static references need qualified.
                    pathify = { text ->
                        Rust.PathSegments(pos, listOf("Self".toKeyId(pos), text.toId(pos, sourceName = name)))
                    }
                }
                NameStyle.Snake
            }
            style == null && decl?.topper == true -> when {
                wrapAnyTopper -> NameStyle.Snake
                else -> NameStyle.Shout
            }
            else -> style
        }
        val text = when (effectiveName) {
            is ExportedName -> {
                // Type might be null for some synthetic types.
                val type = decl?.typeFrom
                if (effectiveStyle == null && !(type == null || type == WellKnownTypes.typeType)) {
                    // If it's not a type, presume we need snake case for exports.
                    effectiveStyle = NameStyle.Snake
                }
                if (effectiveName.origin.loc != module.codeLocation.codeLocation) {
                    pathify = { text ->
                        text.toPath(
                            pos,
                            current = module.codeLocation.codeLocation,
                            names = names,
                            origin = effectiveName.origin.loc,
                        )
                    }
                }
                effectiveName.displayName
            }

            is Temporary -> "${effectiveName.nameHint}___${effectiveName.uid}" // 3 "_" on temps for better uniqueness
            else -> when (effectiveStyle) {
                null, NameStyle.Ugly -> "$effectiveName"
                else -> effectiveName.displayName
            }
        }
        return when (effectiveStyle) {
            NameStyle.Shout -> text.camelToShout()
            NameStyle.Snake -> text.camelToSnake()
            else -> text
        }.let(pathify)
    }

    private fun translateIfStatement(statement: TmpL.IfStatement): List<Rust.Statement> {
        val test = statement.test
        val alt = statement.alternate
        val altEmpty = alt == null || (alt as? TmpL.BlockStatement)?.statements?.isEmpty() == true
        return when {
            // We currently generate spurious fail var tests. Avoid those.
            // TODO Find and fix the cause of these.
            test is TmpL.Reference && test.id.name in failVars -> listOf()

            else -> Rust.IfExpr(
                statement.pos,
                test = translateExpression(test),
                consequent = translateStatement(statement.consequent).toBlock(statement.consequent.pos),
                alternate = when {
                    altEmpty -> null
                    else -> translateStatement(alt).firstOrElse { it.toBlock(alt.pos) }
                },
            ).let { listOf(it) }
        }
    }

    private fun translateInfixOperation(expr: TmpL.InfixOperation): Rust.Expr {
        return Rust.Operation(
            expr.pos,
            left = translateExpression(expr.left),
            operator = translateInfixOperator(expr.op),
            right = translateExpression(expr.right),
        )
    }

    private fun translateInfixOperator(op: TmpL.InfixOperator): Rust.Operator {
        return Rust.Operator(
            op.pos,
            operator = when (op.tmpLOperator) {
                TmpLOperator.AmpAmp -> TODO() // RustOperator.LogicalAnd
                TmpLOperator.BarBar -> TODO() // RustOperator.LogicalOr
                TmpLOperator.EqEqInt -> RustOperator.Equals
                TmpLOperator.GeInt -> RustOperator.GreaterEquals
                TmpLOperator.GtInt -> RustOperator.GreaterThan
                TmpLOperator.LeInt -> RustOperator.LessEquals
                TmpLOperator.LtInt -> RustOperator.LessThan
                TmpLOperator.PlusInt -> RustOperator.Addition
            },
        )
    }

    private fun translateInstanceOfExpression(expression: TmpL.InstanceOfExpression): Rust.Expr {
        // TODO Bubbly found. Maybe some map expression? We don't ever expect bubbly wanted.
        val found = expression.expr.type.described()
        val wanted = expression.checkedFrontendType.described()
        return translateExpression(expression.expr).let { expr ->
            val pos = expr.pos
            // Build multiple clauses if needed for nullable vs type check.
            val parts = buildList {
                val postNull = when {
                    // TODO Some/None-checking also for GeneratorResult. <- What specifically here???
                    found.nullable && !wanted.nullable -> {
                        add(expr.methodCall("is_some", args = listOf()))
                        expr.deepCopy().methodCall("unwrap")
                    }
                    else -> expr
                }
                val wantedType = wanted.type
                if (wantedType != null && found.type != wantedType) {
                    val wantedType = translateType(wantedType, pos)
                    val segments = listOf(IS_NAME.toId(pos), Rust.GenericArgs(pos, args = listOf(wantedType)))
                    add(Rust.Call(pos, callee = Rust.PathSegments(pos, segments = segments), args = listOf(postNull)))
                }
            }
            // Compose. We expect two at most for now, but compose generally.
            when {
                parts.isEmpty() -> "true".toKeyId(pos) // type check without a type?
                else -> parts.reduce<Rust.Expr, Rust.Expr> { result, part ->
                    Rust.Operation(pos, left = result, operator = Rust.Operator(pos, RustOperator.And), right = part)
                }
            }
        }
    }

    private fun translateInstanceProperty(prop: TmpL.InstanceProperty): Rust.StructField {
        return Rust.StructField(
            prop.pos,
            // Struct fields are separated from impl accessors for rust, so might as well be pretty.
            id = translateDotName(prop.dotName),
            type = translateType(prop.type),
        )
    }

    private fun translateLabel(label: TmpL.JumpLabel?): Rust.Id? {
        return when (label) {
            // No explicit jump label given, so see if we need to add one.
            null -> when (loopLabels.last()) {
                // Last is null, so it was a labeled non-loop, so find the first loop label outside that.
                null -> loopLabels.last { it != null }!!.deepCopy()
                // The nearest one was a loop, so just use it as default jump.
                else -> null
            }
            // Jump to the given explicit label.
            else -> translateId(label.id)
        }
    }

    private fun translateLabeledStatement(statement: TmpL.LabeledStatement): List<Rust.Statement> {
        // Track that we have an explicit label. If child is a loop, we'll add its label when we get to it.
        loopLabels.add(null)
        try {
            val pos = statement.pos
            // We don't currently expect multiple produced statements here, but flexible is easy enough here.
            // And it's ok to have sibling blocks with the same label in rust, so just wrap each.
            // TODO Some cases need deepCopy here, which is sad, but they seem likely to be rare cases.
            val id = translateId(statement.label.id)
            return translateStatement(statement.statement).map subs@{ sub ->
                when (sub) {
                    is Rust.LetStatement -> {
                        if (sub.value != null) {
                            // The only code that could break here is the value.
                            // TODO What about future fancy expressions in destructuring pattern matching?
                            val valueBlock = Rust.Block(pos, result = sub.value!!.deepCopy())
                            sub.value = Rust.LabeledExpr(pos, id = id.deepCopy(), expr = valueBlock)
                        }
                        return@subs sub
                    }

                    // We can label blocks and loops directly, but others need nested in a block.
                    is Rust.Block, is Rust.Loop, is Rust.WhileLoop -> sub as Rust.ExprWithBlock
                    is Rust.ExprStatement -> Rust.Block(pos, result = sub.expr.deepCopy())
                    else -> Rust.Block(pos, statements = listOf(sub))
                }.let { Rust.LabeledExpr(pos, id = id.deepCopy(), expr = it) }
            }
        } finally {
            loopLabels.removeLast()
        }
    }

    private fun translateLocalFunctionDeclarations(
        functions: Collection<TmpL.LocalFunctionDeclaration>,
    ): List<Rust.Statement> {
        val functionNames = functions.map { it.name.name }.toSet()
        for (function in functions) {
            val typeFrom = function.sig
            decls[function.name.name] = DeclInfo(
                decl = function,
                local = true,
                type = translateType(typeFrom, function.pos),
                typeFrom = typeFrom,
            )
        }
        val typeParamCaptures = mutableMapOf<ResolvedName, DeclInfo>()
        val captures = buildMap {
            for (function in functions) {
                referencedNames@ for (name in function.referencedNames()) {
                    val info = decls[name] ?: continue
                    when {
                        // Simple partition doesn't work, because we also need to filter out those that are neither.
                        info.local -> this[name] = info
                        info.whereItem != null -> typeParamCaptures[name] = info
                    }
                }
            }
        }
        val pos = functions.first().pos
        // We might need to refer to outer closure types, so keep each unique.
        // TODO Avoid existing names so we don't shadow anything. Just generate random numeric suffix?
        val closureSuffix = ++closureCount
        val captureTypeId = "ClosureGroup___$closureSuffix".toId(pos)
        val captureId = "closure_group".toId(pos)
        return buildList {
            // Struct type for captures.
            val generics = typeParamCaptures.values.map { param ->
                translateTypeOutName(param.name).toId(pos)
            }
            Rust.Item(
                pos,
                attrs = listOf(buildDerive(pos, listOf("Clone"))),
                item = Rust.Struct(
                    pos,
                    id = captureTypeId,
                    // Dupes needed by Rust, but just those type parameters that are reused in the closure.
                    generics = generics,
                    whereItems = typeParamCaptures.values.map { param ->
                        param.whereItem!!.deepCopy()
                    },
                    fields = buildList {
                        captures@ for (capture in captures.values) {
                            capture.name in functionNames && continue@captures
                            Rust.StructField(
                                pos,
                                // TODO Pre-define translated ids.
                                id = translateIdFromName(pos, capture.name),
                                type = capture.type!!.deepCopy(),
                            ).also { add(it) }
                        }
                        addPhantoms(generics)
                    },
                ),
            ).also { add(it) }
            // Impl with action function definitions.
            Rust.Impl(
                pos,
                // Again duplicate captured type params.
                generics = generics.deepCopy(),
                whereItems = typeParamCaptures.values.map { param ->
                    param.whereItem!!.deepCopy()
                },
                trait = null,
                type = captureTypeId.makeTypeRef(generics),
                items = buildList {
                    for (function in functions) {
                        translateFunctionDeclarationOrMethod(
                            function,
                            captures = captures,
                            methodizeds = functionNames,
                        ).also { add(it) }
                    }
                },
            ).also { add(it.toItem()) }
            // Struct instance with the capture values.
            val functionContext = functionContextStack.lastOrNull()
            val selfName = functionContext?.decl?.parameters?.thisName?.name
            Rust.LetStatement(
                pos,
                pattern = Rust.IdPattern(pos, id = captureId),
                type = null,
                value = Rust.StructExpr(
                    pos,
                    id = captureTypeId.deepCopy(),
                    members = buildList {
                        // Actual captures.
                        captures@ for (capture in captures.values) {
                            capture.name in functionNames && continue@captures
                            // TODO Pre-define translated ids in decl info.
                            val id = translateIdFromName(pos, capture.name)
                            val expr = when {
                                capture.mutableCapture -> when (functionContext?.captures?.containsKey(capture.name)) {
                                    true -> "self".toKeyId(pos).member(id)
                                    // Normal reference handling here would get this case wrong.
                                    else -> id
                                }.wrapClone()
                                capture.name == selfName -> "self".toKeyId(pos).wrapClone()
                                else -> {
                                    val ref = translateReference(
                                        when (val type = capture.typeFrom) {
                                            is Type2? -> TmpL.Reference(
                                                capture.decl.name,
                                                type.orInvalid,
                                            )
                                            is Signature2 -> TmpL.FnReference(capture.decl.name, type)
                                        },
                                    )
                                    when (ref) {
                                        id -> null
                                        else -> ref
                                    }
                                }
                            }
                            Rust.StructExprField(pos, id = id, expr = expr).also { add(it) }
                        }
                        // Phantoms.
                        for (generic in generics) {
                            addPhantomEntry(pos, typeName = generic.outName.outputNameText)
                        }
                    },
                ),
            ).also { add(it) }
            // Arc wrapper functions that can be easily called and passed around.
            // TODO We can use simple closures without all the wrapping if we know it's only called locally.
            for (function in functions) {
                val funPos = function.pos
                val functionId = translateId(function.name)
                Rust.LetStatement(
                    funPos,
                    pattern = Rust.IdPattern(funPos, id = functionId),
                    type = null,
                    value = buildMethodCapture(captureId = captureId, function = function, functionId = functionId),
                ).also { add(it) }
            }
        }
    }

    private fun translateMethodId(member: TmpL.MemberOrGarbage): Rust.Id {
        return when (member) {
            is TmpL.Getter -> translateGetterId(member)
            is TmpL.Setter -> translateSetterId(member)
            is TmpL.NormalMethod -> translateNormalishMethodId(member)
            else -> TODO()
        }
    }

    private fun translateMethodLike(
        member: TmpL.MemberOrGarbage,
        block: Rust.Block? = null,
        forTrait: Boolean = false,
        generics: List<Rust.GenericParam> = listOf(),
        returnType: Type2? = null,
        type: Rust.Type? = null,
        typePub: Rust.VisibilityPub? = null,
    ): List<Rust.Item> {
        return when (member) {
            is TmpL.GarbageStatement -> TODO()
            // So far only bother with returnType forwarding for instance methods. Will we need more later?
            is TmpL.Getter -> translateGetter(member, block = block, forTrait = forTrait, returnType = returnType)
            is TmpL.Setter -> translateSetter(member, block = block, forTrait = forTrait) // don't expect return type
            is TmpL.NormalMethod ->
                translateNormalishMethod(member, block = block, forTrait = forTrait, returnType = returnType)
            is TmpL.StaticMethod -> translateNormalishMethod(member, block = block).also { check(!forTrait) }
            is TmpL.Constructor -> return translateConstructor(
                member,
                enclosingType = type,
                enclosingTypePub = typePub,
                generics = generics,
            )
            is TmpL.InstanceProperty -> return listOf() // handled separately
            is TmpL.StaticProperty -> translateStaticProperty(member)
        }.let { listOf(it) }
    }

    private fun translateModuleInitFailed(statement: TmpL.ModuleInitFailed): Rust.Statement {
        return Rust.ExprStatement(
            statement.pos,
            expr = Rust.ReturnExpr(statement.pos, value = translateBubbleSentinel(statement.pos)),
        )
    }

    private fun translateModuleOrLocalDeclaration(decl: TmpL.ModuleOrLocalDeclaration): List<Rust.Statement> {
        decl.metadata.any { it.key.symbol == failSymbol } && run {
            failVars.add(decl.name.name)
            return@translateModuleOrLocalDeclaration listOf()
        }
        val isMutableCapture = functionContextStack.lastOrNull()?.let { decl.name.name in it.mutableCaptures } == true
        val rawType = translateType(decl.type)
        val type = when {
            isMutableCapture -> rawType.wrapRwLockType().wrapArcType()
            else -> rawType
        }
        // TODO Absent only for locals? If so, can optimize a bit here.
        val info = decls.getOrPut(decl.name.name) {
            DeclInfo(
                decl = decl,
                local = functionContextStack.isNotEmpty(),
                mutableCapture = isMutableCapture,
                type = type,
                typeFrom = varTypes[decl.name.name],
            )
        }
        val pos = decl.pos
        val value = decl.init?.let { init ->
            val rawValue = translateExpression(init)
                .maybeWrap(given = init.type.described(), wanted = info.typeFrom?.described(), translator = this)
            when {
                isMutableCapture -> rawValue.wrapLock().wrapArc()
                else -> rawValue
            }
        }
        return when {
            // Never vars are still experimental in Rust, so we prune them.
            decl.type.ot is TmpL.NeverType -> value?.let { listOf(Rust.ExprStatement(pos, it)) } ?: listOf()
            info.topper -> buildTopperAssign(decl = decl, value = value)
            else -> listOf(Rust.LetStatement(pos, pattern = translateDeclNamePattern(decl), type = type, value = value))
        }
    }

    private fun translateNormalishMethod(
        method: TmpL.Method,
        block: Rust.Block? = null,
        forTrait: Boolean = false,
        returnType: Type2? = null,
    ): Rust.Item {
        val id = translateNormalishMethodId(method)
        val pub = (method as? TmpL.Member)?.let { chooseVisibilityForMember(it, forTrait = forTrait) }
        return translateFunctionDeclarationOrMethod(
            method,
            block = block,
            forTrait = forTrait,
            id = id,
            pub = pub,
            returnType = returnType,
        )
    }

    private fun translateNormalishMethodId(method: TmpL.Method) =
        translateId(method.name, style = NameStyle.Snake)

    private fun translateParameters(
        parameters: TmpL.Parameters,
        forTrait: Boolean = false,
        skipSelf: Boolean = false,
        style: NameStyle? = null,
    ): List<Rust.FunctionParamOption> {
        return buildList {
            params@ for (param in parameters.parameters) {
                // Don't claim parameter here, so we get fleshed out string types.
                val paramName = param.name.name
                val paramInfo = DeclInfo(
                    decl = param,
                    local = true,
                    type = translateType(param.type),
                    typeFrom = varTypes[paramName],
                )
                decls[paramName] = paramInfo
                when (param.name) {
                    parameters.thisName -> when {
                        skipSelf -> continue@params
                        else -> Rust.RefType(param.pos, type = "self".toKeyId(parameters.thisName!!.pos))
                    }

                    else -> Rust.FunctionParam(
                        param.pos,
                        pattern = translateDeclNamePattern(param, style = style),
                        type = translateType(param.type, isFlex = !forTrait).let { type ->
                            when {
                                param.optional && paramInfo.typeFrom?.described()?.nullable == false ->
                                    type.option()
                                else -> type
                            }
                        },
                    )
                }.also { add(it) }
            }
            parameters.restParameter?.let { rest ->
                val restName = rest.name.name
                val listStaticType = varTypes[restName] as? Type2 ?: WellKnownTypes.invalidType2
                decls[restName] = DeclInfo(
                    decl = rest,
                    local = true,
                    type = translateType(listStaticType, rest.pos),
                    typeFrom = listStaticType,
                )
                Rust.FunctionParam(
                    rest.pos,
                    pattern = translateDeclNamePattern(rest),
                    type = translateType(listStaticType, rest.pos, isFlex = true),
                ).also { add(it) }
            }
        }
    }

    private fun translatePropertyId(property: TmpL.PropertyId): Rust.Id {
        return when (property) {
            is TmpL.ExternalPropertyId -> translateDotName(property.name)
            is TmpL.InternalPropertyId -> translateId(property.name, style = NameStyle.Snake)
        }
    }

    private fun translatePropertyReference(ref: TmpL.PropertyReference, lockName: String): Rust.Expr {
        val propertyId = translatePropertyId(ref.property)
        val isInternal = ref.property is TmpL.InternalPropertyId
        if (isInternal && functionContextStack.last().constructorMode == ConstructorMode.Init) {
            // Use local vars until init is finished.
            return propertyId
        }
        val subject = when (val subject = ref.subject) {
            is TmpL.Expression -> subject
            is TmpL.TypeName -> {
                // Static property access.
                val callee = translateTypeName(subject).extendWith(listOf(propertyId))
                return Rust.Call(ref.pos, callee = callee, args = listOf())
            }
        }
        val expr = translateExpression(subject, avoidClone = true).let { expr ->
            when {
                // Such as `self.0`. TODO Should `expr` always be `self` in this case?
                isInternal -> expr.member("0").let { core ->
                    when {
                        // And then `.read().unwrap()` if we need to dig through a mut layer.
                        insideMutableType -> core.methodCall(lockName, listOf()).methodCall("unwrap")
                        else -> core
                    }
                }
                else -> expr
            }
        }
        return expr.member(propertyId).let { access ->
            when {
                // TODO Clone if an internal getter and a reference type?
                isInternal -> access
                else -> Rust.Call(ref.pos, callee = access, args = listOf())
            }
        }
    }

    private fun translateReference(
        reference: TmpL.AnyReference,
        avoidClone: Boolean = false,
        isCall: Boolean = false,
    ): Rust.Expr {
        val id = translateIdAsPath(reference.id, asRef = true, wrapAnyTopper = true)
        val internalName = reference.id.name
        return translateReference(id, internalName, reference.type, avoidClone = avoidClone, isCall = isCall)
    }

    private fun translateReference(
        id: Rust.Path,
        internalName: ResolvedName,
        type: Descriptor,
        avoidClone: Boolean = false,
        isCall: Boolean = false,
    ): Rust.Expr {
        val pos = id.pos
        val functionContext = functionContextStack.lastOrNull()
        val got = when (val capture = functionContext?.captures?.get(internalName)) {
            null -> {
                val decl = decls[internalName]
                if ((decl?.typeFrom as? Type2)?.definition == WellKnownTypes.neverTypeDefinition) {
                    throw NeverRefException()
                }
                when {
                    decl?.mutableCapture == true -> id.readLocked()
                    // Check for either internal or external topper.
                    decl?.topper == true || (!isCall && fromOtherModule(decl)) -> when (decl?.importInfo?.sig) {
                        // But don't call top-level functions, just values presented as getters.
                        is TmpL.ImportedFunction -> id
                        else -> Rust.Call(pos, id, listOf())
                    }
                    else -> id
                }
            }

            else -> {
                val captureId = translateIdFromName(pos, capture.name)
                val methodized = capture.name in functionContext.methodizeds
                when {
                    // Methods need wrapped to use as our functions. TODO Use a caching helper to reduce allocs?
                    methodized && !isCall -> buildMethodCapture(
                        captureId = "selfish".toId(pos),
                        capturedId = "self".toKeyId(pos),
                        function = capture.decl as TmpL.LocalFunctionDeclaration,
                        functionId = captureId,
                    )

                    else -> {
                        val access = "self".toKeyId(pos).member(captureId, notMethod = !methodized)
                        when {
                            capture.assignOnce -> access
                            else -> access.readLocked()
                        }
                    }
                }
            }
        }
        // Simple references typically need cloned, as opposed to typical function calls
        // that are already returning clones or new values, given our conventions.
        return got.maybeClone(type, avoidClone = avoidClone || isCall)
    }

    private fun fromOtherModule(decl: DeclInfo?) =
        decl?.importedName?.let { it.origin != module.codeLocation.origin } == true

    private fun translateReturnStatement(statement: TmpL.ReturnStatement, last: Boolean = false): List<Rust.Statement> {
        val pos = statement.pos
        val context = functionContextStack.last()
        val returnType = context.returnType
        val value = when (val value = statement.expression) {
            null -> when {
                context.constructorMode == ConstructorMode.Use -> "selfish".toId(pos).let { selfish ->
                    when {
                        returnType.bubbly -> selfish.wrapOk()
                        else -> selfish
                    }
                }
                returnType.bubbly -> "()".toId(pos).wrapOk()
                last -> return listOf()
                else -> null
            }

            else -> translateExpression(value).maybeWrap(given = value.type, wanted = returnType, translator = this)
        }
        return listOf(Rust.ExprStatement(pos, expr = Rust.ReturnExpr(pos, value = value)))
    }

    private fun translateSetProperty(statement: TmpL.SetProperty): Rust.Statement {
        // Get the property type so we know if we need to wrap the value. TODO Factor out any of this?
        val subjectShape = when (val subject = statement.left.subject) {
            is TmpL.Expression -> (subject.type as? DefinedNonNullType)?.definition
            is TmpL.TypeName -> subject.sourceDefinition as? TypeShape
        }
        val propertyText = when (val property = statement.left.property) {
            is TmpL.ExternalPropertyId -> property.name.dotNameText
            is TmpL.InternalPropertyId -> property.name.name.toSymbol()?.text
        }
        val propertyType = subjectShape?.properties?.find { it.symbol.text == propertyText }?.descriptor
        // Wrap the value as needed.
        val right = translateExpression(statement.right)
        val value = right.maybeWrap(given = statement.right.type, wanted = propertyType, translator = this)
        // Set the property.
        return when (statement.left.property) {
            is TmpL.ExternalPropertyId -> {
                val ref = statement.left
                val subject = translateExpression((ref.subject as? TmpL.Expression) ?: TODO(), avoidClone = true)
                val setter = "set_${translatePropertyId(ref.property)}"
                subject.methodCall(setter, listOf(value))
            }

            is TmpL.InternalPropertyId -> Rust.Operation(
                statement.pos,
                left = translatePropertyReference(statement.left, lockName = "write"),
                operator = Rust.Operator(statement.pos, RustOperator.Assign),
                right = value,
            )
        }.let { Rust.ExprStatement(statement.pos, expr = it) }
    }

    private fun translateSetter(setter: TmpL.Setter, block: Rust.Block? = null, forTrait: Boolean = false): Rust.Item {
        val id = translateSetterId(setter)
        val pub = chooseVisibilityForMember(setter, forTrait = forTrait)
        return translateFunctionDeclarationOrMethod(setter, block = block, forTrait = forTrait, id = id, pub = pub)
    }

    private fun translateSetterId(setter: TmpL.Setter) =
        "set_${setter.dotName.dotNameText.camelToSnake()}".toId(setter.dotName.pos)

    private fun translateStatement(statement: TmpL.Statement): List<Rust.Statement> {
        try {
            return when (statement) {
                is TmpL.Assignment -> return translateAssignment(statement)
                is TmpL.BoilerplateCodeFoldEnd -> TODO()
                is TmpL.BoilerplateCodeFoldStart -> TODO()
                is TmpL.BreakStatement -> translateBreakStatement(statement)
                is TmpL.ContinueStatement -> translateContinueStatement(statement)
                is TmpL.EmbeddedComment -> TODO()
                is TmpL.ExpressionStatement -> translateExpressionStatement(statement)
                is TmpL.GarbageStatement -> TODO()
                is TmpL.HandlerScope -> error("handled elsewhere")
                is TmpL.LocalDeclaration -> return translateModuleOrLocalDeclaration(statement)
                is TmpL.LocalFunctionDeclaration -> TODO() // handled elsewhere
                is TmpL.ModuleInitFailed -> translateModuleInitFailed(statement)
                is TmpL.BlockStatement -> translateBlock(statement)
                is TmpL.ComputedJumpStatement -> translateComputedJumpStatement(statement)
                is TmpL.IfStatement -> return translateIfStatement(statement)
                is TmpL.LabeledStatement -> return translateLabeledStatement(statement)
                is TmpL.TryStatement -> TODO()
                is TmpL.WhileStatement -> translateWhileStatement(statement)
                is TmpL.ReturnStatement -> return translateReturnStatement(statement)
                is TmpL.SetProperty -> translateSetProperty(statement)
                is TmpL.ThrowStatement -> TODO()
                is TmpL.YieldStatement -> TODO()
            }.let { listOf(it) }
        } catch (_: NeverRefException) {
            // No statements referencing things that are typed never.
            // You can call a function that returns never, but not reference a never directly.
            return listOf()
        }
    }

    private fun translateStaticProperty(property: TmpL.StaticProperty): Rust.Item {
        val propertyName = property.name.name
        val decl = DeclInfo(
            decl = property,
            local = false,
            topper = true,
            type = translateType(property.type),
            typeFrom = varTypes[propertyName],
        )
        decls[propertyName] = decl // intentionally clobber any from gatherStaticDecls \_(:-)_/
        // Pick a name that includes both the type name and the property name.
        // TODO Ensure uniqueness!
        val idType = property.memberShape.enclosingType.name.displayName.camelToShout()
        val idProp = property.name.name.displayName.camelToShout()
        val id = Rust.Id(property.name.pos, OutName("${idType}__$idProp", property.name.name))
        buildTopperAssign(decl = property, id = id, value = translateExpression(property.expression)).also { init ->
            moduleInits.addAll(init)
        }
        return buildTopperGetter(decl = decl, considerMember = true, id = id)
    }

    private fun translateStringLiteral(expression: TmpL.ValueReference): Rust.Expr {
        return Rust.StringLiteral(expression.pos, TString.unpack(expression.value)).wrapArcString()
    }

    private fun translateThis(expression: TmpL.This, avoidClone: Boolean = false): Rust.Expr {
        val thisNameExpressed = when (functionContextStack.lastOrNull()?.constructorMode) {
            // TODO Explicit tracking of override self name in constructors.
            ConstructorMode.Use -> "selfish"
            else -> "self"
        }
        val thisNameInternal = functionContextStack.findLast {
            it.decl.parameters.thisName != null
        }!!.decl.parameters.thisName!!.name
        return translateReference(
            id = thisNameExpressed.toKeyId(expression.pos), // "selfish" is fine here, too
            internalName = thisNameInternal,
            type = expression.type,
            // TODO Do we ever really want to avoid clone on this???
            avoidClone = avoidClone,
        )
    }

    private fun translateType(
        descriptor: Descriptor,
        pos: Position,
    ): Rust.Type = when (descriptor) {
        is Signature2 -> translateFunctionType(descriptor, pos)
        is Type2 -> translateType(descriptor, pos)
    }

    /**
     * @param inExpr For types used in expressions rather than in ordinary type position.
     * @param isFlex For flexible impl types that can be converted, typically for params for non-virtual functions.
     */
    private fun translateType(
        type: Type2,
        pos: Position,
        inExpr: Boolean = false,
        isFlex: Boolean = false,
    ): Rust.Type {
        if (type.nullity == Nullity.OrNull) {
            return translateTypeUnion(
                pos, type.withNullity(Nullity.NonNull) as NonNullType,
                canBeNull = true, isFlex = isFlex,
            )
        }
        check(type is NonNullType)
        return withType(
            type,
            fallback = {
                translateTypeNominal(type, pos, inExpr = inExpr, isFlex = isFlex)
            },
            fn = { _, sig, _ ->
                translateFunctionType(sig, pos)
            },
            never = { _, _, _ ->
                "!".toId(pos) // except not actually supported in stable rust
            },
            result = { passType, _, _ ->
                val canBeNull = passType.nullity == Nullity.OrNull
                val passTypeNonNull = passType.withNullity(Nullity.NonNull) as NonNullType
                translateTypeUnion(pos, passTypeNonNull, canBeNull = canBeNull, canBubble = true, isFlex = isFlex)
            },
        )
    }
    private fun translateType(type: TmpL.AType, inExpr: Boolean = false, isFlex: Boolean = false) =
        translateType(type.ot, inExpr = inExpr, isFlex = isFlex)

    private fun translateType(type: TmpL.Type, inExpr: Boolean = false, isFlex: Boolean = false): Rust.Type {
        val pos = type.pos
        // Check first for connected types.
        ((type as? TmpL.NominalType)?.typeName as? TmpL.ConnectedToTypeName)?.let { typeName ->
            return@translateType translateTypeConnected(pos, typeName)
        }
        // Otherwise handle non-connected types.
        return when (type) {
            is TmpL.FunctionType -> translateFunctionType(type)
            is TmpL.TypeIntersection -> TODO()
            is TmpL.TypeUnion -> translateTypeUnion(type, isFlex = isFlex)
            is TmpL.GarbageType -> "()".toId(pos)
            is TmpL.NominalType -> translateTypeNominal(type, inExpr = inExpr, isFlex = isFlex)
            is TmpL.BubbleType -> TODO()
            is TmpL.NeverType -> "!".toId(pos) // except not actually supported in stable rust
            is TmpL.TopType -> ANY_NAME.toId(pos)
        }
    }

    private fun translateTypeAsTraitName(bound: TmpL.NominalType) = when (val translated = translateType(bound)) {
        is Rust.GenericType -> translated.also { it.path = it.path.suffixed(TRAIT_NAME_SUFFIX) }
        is Rust.Path -> translated.suffixed(TRAIT_NAME_SUFFIX)
        else -> translated
    }

    private fun translateTypeBindings(type: Type2, pos: Position) =
        type.bindings.map { binding ->
            translateType(binding, pos)
        }

    private fun translateTypeBindings(type: TmpL.NominalType) =
        type.params.map { binding -> translateType(binding) }

    private fun translateTypeConnected(pos: Position, typeName: TmpL.ConnectedToTypeName): Rust.Type {
        return when ((typeName.name) as ConnectedType) {
            ConnectedType.StringBuilder -> "String".toId(pos).wrapRwLockType().wrapArcType()
        }
    }

    internal fun translateTypeDefinition(def: TypeDefinition, pos: Position, isParam: Boolean = false): Rust.Type {
        return when (def.sourceLocation) {
            ImplicitsCodeLocation -> when (def) {
                WellKnownTypes.anyValueTypeDefinition -> OutName(ANY_NAME, def.name)
                WellKnownTypes.booleanTypeDefinition -> OutName("bool", def.name)
                WellKnownTypes.denseBitVectorTypeDefinition -> OutName(DENSE_BIT_VECTOR_NAME, def.name)
                WellKnownTypes.dequeTypeDefinition -> OutName(DEQUE_NAME, def.name)
                WellKnownTypes.intTypeDefinition -> OutName("i32", def.name)
                WellKnownTypes.int64TypeDefinition -> OutName("i64", def.name)
                WellKnownTypes.float64TypeDefinition -> OutName("f64", def.name)
                WellKnownTypes.generatorResultTypeDefinition -> OutName(OPTION_NAME, def.name)

                WellKnownTypes.listTypeDefinition -> return when {
                    isParam -> Rust.ImplTraitType(pos, bounds = listOf(TO_LIST_NAME.toId(pos)))
                    else -> OutName(LIST_NAME, def.name).toId(pos)
                }

                WellKnownTypes.listedTypeDefinition -> return when {
                    isParam -> Rust.ImplTraitType(pos, bounds = listOf(TO_LISTED_NAME.toId(pos)))
                    else -> OutName(LISTED_NAME, def.name).toId(pos)
                }

                WellKnownTypes.listBuilderTypeDefinition -> return when {
                    isParam -> Rust.ImplTraitType(pos, bounds = listOf(TO_LIST_BUILDER_NAME.toId(pos)))
                    else -> OutName(LIST_BUILDER_NAME, def.name).toId(pos)
                }

                // Maybe less expectation for clever param conversion for maps, so go simple here.
                // TODO Support some ToMap/ToMapped for lists of pairs and for HashMap, or for IntoIter of pairs?
                WellKnownTypes.mapTypeDefinition -> OutName(MAP_NAME, def.name)
                WellKnownTypes.mapKeyTypeDefinition -> OutName(MAP_KEY_NAME, def.name)
                WellKnownTypes.mappedTypeDefinition -> OutName(MAPPED_NAME, def.name)
                WellKnownTypes.mapBuilderTypeDefinition -> OutName(MAP_BUILDER_NAME, def.name)
                WellKnownTypes.promiseTypeDefinition -> OutName("temper_core::Promise", def.name)
                WellKnownTypes.promiseBuilderTypeDefinition -> OutName("temper_core::PromiseBuilder", def.name)
                WellKnownTypes.safeGeneratorTypeDefinition -> OutName(SAFE_GENERATOR_NAME, def.name)
                WellKnownTypes.generatorTypeDefinition -> OutName(GENERATOR_NAME, def.name)

                WellKnownTypes.stringIndexOptionTypeDefinition ->
                    return OutName("usize", def.name).toId(pos).option()

                WellKnownTypes.stringIndexTypeDefinition -> OutName("usize", def.name)
                WellKnownTypes.stringTypeDefinition -> return when {
                    isParam -> Rust.ImplTraitType(pos, bounds = listOf(TO_ARC_STRING_NAME.toId(pos)))
                    else -> OutName(STRING_NAME, def.name).toId(pos).wrapArcType()
                }

                WellKnownTypes.typeTypeDefinition -> OutName(TYPE_ID_NAME, def.name)

                // WellKnownTypes.valueResultTypeDefinition -> OutName("???", def.name)

                WellKnownTypes.noStringIndexTypeDefinition,
                WellKnownTypes.nullTypeDefinition,
                WellKnownTypes.emptyTypeDefinition,
                WellKnownTypes.voidTypeDefinition,
                -> OutName("()", def.name)

                else -> TODO(def.name.displayName)
            }

            else -> return translateIdFromNameAsPath(pos, def.name, style = NameStyle.Camel)
        }.toId(pos)
    }

    private fun translateTypeName(subject: TmpL.TypeName): Rust.Path {
        return translateTypeDefinition(subject.sourceDefinition, subject.pos) as Rust.Path
    }

    private fun translateTypeNominal(
        type: NonNullType,
        pos: Position,
        inExpr: Boolean = false,
        isFlex: Boolean = false,
    ): Rust.Type {
        // Check in advance if we're doing a pair, because most idiomatic is (K, V), and that's differenter generics.
        if (type.definition == WellKnownTypes.pairTypeDefinition) {
            // We don't expect to need this case for any uses of translateTypeDefinition.
            return Rust.TupleType(pos, types = translateTypeBindings(type, type.definition.pos))
        }
        // Handle the rest of the cases.
        val translated = translateTypeDefinition(type.definition, pos, isParam = isFlex)
        // TODO Instead, if inExpr, make turbofish call?
        return translateTypeNominalApplyAnyBindings(type, translated, inExpr = inExpr)
    }

    private fun translateTypeNominal(
        type: TmpL.NominalType,
        inExpr: Boolean = false,
        isFlex: Boolean = false,
    ): Rust.Type {
        // Check in advance if we're doing a pair, because most idiomatic is (K, V), and that's differenter generics.
        val definition = type.typeName.sourceDefinition
        if (definition == WellKnownTypes.pairTypeDefinition) {
            // We don't expect to need this case for any uses of translateTypeDefinition.
            return Rust.TupleType(type.pos, types = translateTypeBindings(type))
        }
        // Handle the rest of the cases.
        val translated = translateTypeDefinition(definition, type.pos, isParam = isFlex)
        // TODO Instead, if inExpr, make turbofish call?
        return translateTypeNominalApplyAnyBindings(type, translated, inExpr = inExpr)
    }

    private fun translateTypeNominalApplyAnyBindings(
        type: Type2,
        translated: Rust.Type,
        inExpr: Boolean = false,
    ) = when {
        inExpr || type.bindings.isEmpty() -> translated
        else -> {
            val core = when (translated) {
                is Rust.ImplTraitType -> {
                    check(translated.bounds.size == 1)
                    (translated.bounds.first() as? Rust.Id)?.deepCopy()
                }
                is Rust.Path -> translated
                else -> null
            } ?: TODO("$translated")
            val generic = Rust.GenericType(
                core.pos,
                path = core,
                args = translateTypeBindings(type, core.pos),
            )
            when (translated) {
                is Rust.ImplTraitType -> Rust.ImplTraitType(translated.pos, bounds = listOf(generic))
                else -> generic
            }
        }
    }

    private fun translateTypeNominalApplyAnyBindings(
        type: TmpL.NominalType,
        translated: Rust.Type,
        inExpr: Boolean = false,
    ) = when {
        inExpr || type.params.isEmpty() -> translated
        else -> {
            val core = when (translated) {
                is Rust.ImplTraitType -> {
                    check(translated.bounds.size == 1)
                    (translated.bounds.first() as? Rust.Id)?.deepCopy()
                }
                is Rust.Path -> translated
                else -> null
            } ?: TODO("$translated")
            val generic = Rust.GenericType(
                core.pos,
                path = core,
                args = translateTypeBindings(type),
            )
            when (translated) {
                is Rust.ImplTraitType -> Rust.ImplTraitType(translated.pos, bounds = listOf(generic))
                else -> generic
            }
        }
    }

    private fun translateTypeOutName(name: ResolvedName) = OutName(name.displayName, name)

    private fun translateTypeUnion(
        pos: Position,
        type: NonNullType,
        isFlex: Boolean = false,
        canBeNull: Boolean = false,
        canBubble: Boolean = false,
    ): Rust.Type {
        return translateType(type, pos = pos, isFlex = isFlex).let { type ->
            when {
                canBeNull -> type.option()
                else -> type
            }
        }.let { type ->
            when {
                canBubble -> type.wrapResult()
                else -> type
            }
        }
    }

    private fun translateTypeUnion(
        type: TmpL.TypeUnion,
        isFlex: Boolean = false,
    ): Rust.Type {
        var canBeNull = false
        var canBubble = false
        var core: TmpL.Type? = null
        for (t in type.types) {
            if (t is TmpL.BubbleType) {
                canBubble = true
            } else if ((t as? TmpL.NominalType)?.typeName?.sourceDefinition == WellKnownTypes.nullTypeDefinition) {
                canBeNull = true
            } else if (core == null) {
                core = t
            }
        }

        return translateType(core ?: TmpL.NeverType(type.pos), isFlex = isFlex).let { type ->
            when {
                canBeNull -> type.option()
                else -> type
            }
        }.let { type ->
            when {
                canBubble -> type.wrapResult()
                else -> type
            }
        }
    }

    private fun translateTypeValue(value: TmpL.ValueReference): Rust.Expr {
        val pos = value.pos
        val type = TType.unpack(value.value)
        return Rust.Call(
            pos,
            callee = Rust.PathSegments(
                pos,
                segments = listOf(
                    TYPE_ID_OF_NAME.toId(pos),
                    Rust.GenericArgs(pos = pos, args = listOf(translateType(type.type2, pos))),
                ),
            ),
            args = listOf(),
        )
    }

    private fun translateValueReference(expression: TmpL.ValueReference): Rust.Expr {
        val pos = expression.pos
        return when (expression.value.typeTag) {
            TBoolean -> "${TBoolean.unpack(expression.value)}".toKeyId(pos)
            TFloat64 -> {
                val value = TFloat64.unpack(expression.value)
                when {
                    value.isNaN() -> makePath(pos, "f64", "NAN")
                    value.isInfinite() -> when {
                        value < 0 -> makePath(pos, "f64", "NEG_INFINITY")
                        else -> makePath(pos, "f64", "INFINITY")
                    }

                    else -> Rust.NumberLiteral(pos, value)
                }
            }

            TInt -> Rust.NumberLiteral(pos, TInt.unpack(expression.value))
            TInt64 -> Rust.NumberLiteral(pos, TInt64.unpack(expression.value))
            is TString -> translateStringLiteral(expression)
            is TClass -> TODO("$expression")
            TClosureRecord -> TODO("$expression")
            TFunction -> TODO("$expression")
            TList -> TODO("$expression")
            TListBuilder -> TODO("$expression")
            TMap -> TODO("$expression")
            TMapBuilder -> TODO("$expression")
            TNull -> "None".toId(pos)
            TProblem -> TODO("$expression")
            TStageRange -> TODO("$expression")
            TSymbol -> TODO("$expression")
            TType -> translateTypeValue(expression)
            TVoid -> "()".toId(pos)
        }
    }

    private fun translateWhileStatement(loop: TmpL.WhileStatement): Rust.Statement {
        val test = loop.test
        // Always label in case we need to add labels to breaks or continues inside.
        val parentLabel = loop.parent as? TmpL.LabeledStatement
        // TODO Pass down any already translated label statement id?
        val loopLabel = parentLabel?.let { translateId(it.label.id) } ?: unusedTemporaryName(loop.pos, "loop")
        loopLabels.add(loopLabel)
        try {
            return when (test) {
                is TmpL.ValueReference if TBoolean.unpackOrNull(test.value) == true -> {
                    // Avoids warnings, but more importantly is recognized as nonterminating by rustc.
                    Rust.Loop(loop.pos, block = translateBlock(loop.body))
                }
                else -> {
                    Rust.WhileLoop(loop.pos, test = translateExpression(loop.test), block = translateBlock(loop.body))
                }
            }.let { outLoop ->
                when (parentLabel) {
                    null -> Rust.LabeledExpr(loop.pos, loopLabel, outLoop)
                    else -> outLoop
                }
            }
        } finally {
            loopLabels.removeLast()
        }
    }

    private fun MutableList<Rust.StructExprMember>.addPhantomEntry(pos: Position, typeName: String) {
        Rust.StructExprField(
            pos,
            id = "phantom_$typeName".toId(pos),
            expr = PHANTOM_DATA_NAME.toId(pos),
        ).let { add(it) }
    }

    private fun MutableList<Rust.StructField>.addPhantoms(generics: List<Rust.GenericParam>) {
        for (generic in generics) {
            val arg = generic.toArg()
            Rust.StructField(
                generic.pos,
                // TODO Unique names!
                id = "phantom_${arg.outName.outputNameText}".toId(generic.pos),
                type = Rust.GenericType(
                    generic.pos,
                    path = PHANTOM_DATA_NAME.toId(generic.pos),
                    args = listOf(arg),
                ),
            ).let { add(it) }
        }
    }

    fun unusedTemporaryName(pos: Position, nameHint: String): Rust.Id {
        return translateIdFromName(pos, nameMaker.unusedTemporaryName(nameHint))
    }

    internal inner class ParameterInfo(
        function: TmpL.FunctionDeclarationOrMethod,
        forTrait: Boolean = false,
        skipSelf: Boolean = false,
        mutableCaptures: Set<ResolvedName> = emptySet(),
    ) {
        val params = translateParameters(
            function.parameters,
            forTrait = forTrait,
            skipSelf = skipSelf,
        )

        private val typeParams: List<Rust.TypeParam> = function.typeParameters.ot.typeParameters.map { typeParam ->
            val id = translateId(typeParam.name, style = NameStyle.Camel)
            val bounds = buildBounds(typeParam)
            Rust.TypeParam(typeParam.pos, id = id, bounds = bounds)
        }
        val generics: List<Rust.GenericParam> = typeParams.map { it.id.deepCopy() }
        val whereItems: List<Rust.WhereItem> =
            typeParams.zip(function.typeParameters.ot.typeParameters).map { (typeParam, source) ->
                // All our types are expected to be cloneable, typically by reference counting, but still.
                val whereItem = typeParam.deepCopy()
                whereItem.bounds += buildBoundsCommon(source.pos)
                decls[source.name.name] = DeclInfo(decl = source, whereItem = whereItem)
                whereItem
            }

        // Convert impl trait params to wrapped arc values. And hurray for Rust letting us rebind the same name.
        val conversions = buildList {
            conversions@ for (param in params) {
                val pos = param.pos
                val type = (param as? Rust.FunctionParam)?.type ?: continue@conversions
                val paramRef = param.pattern.id().deepCopy()
                when (type) {
                    is Rust.GenericType -> when {
                        // TODO Improve type recognition!
                        (type.path as? Rust.Id)?.outName?.outputNameText == OPTION_NAME && type.args.size == 1 -> {
                            (type.args.first() as? Rust.ImplTraitType)?.implConvertName()?.let { convertName ->
                                // let whatever = whatever.map(|x| x.to_...());
                                Rust.LetStatement(
                                    pos,
                                    pattern = param.pattern.deepCopy(),
                                    type = null,
                                    value = paramRef.methodCall(
                                        "map",
                                        listOf(
                                            Rust.Closure(
                                                pos,
                                                params = listOf(
                                                    Rust.FunctionParam(pos, pattern = "x".toId(pos), type = null),
                                                ),
                                                value = "x".toId(pos).methodCall(convertName),
                                            ),
                                        ),
                                    ),
                                ).also { add(it) }
                            }
                        }

                        else -> {}
                    }

                    is Rust.ImplTraitType -> type.implConvertName()?.let { convertName ->
                        // let whatever = whatever.to_...();
                        Rust.LetStatement(
                            pos,
                            pattern = param.pattern.deepCopy(),
                            type = null,
                            value = paramRef.methodCall(convertName),
                        ).also { add(it) }
                    }

                    else -> {}
                }
                // Also see if we need a conversion for mutable capture.
                if (paramRef.outName.sourceName in mutableCaptures) {
                    val decl = decls.getValue(paramRef.outName.sourceName as ResolvedName)
                    // We may have converted from parameter type above, so build non-param type from scratch.
                    // We'll need it for capture struct fields later.
                    val rawType = translateType(decl.typeFrom!!, pos)
                    val captureType = rawType.wrapRwLockType().wrapArcType()
                    Rust.LetStatement(
                        pos,
                        pattern = paramRef.deepCopy(),
                        type = null,
                        value = paramRef.deepCopy().wrapLock().wrapArc(),
                    ).also { add(it) }
                    // Also update the decl info for anyone using this in the function.
                    decls[decl.name] = decl.copy(mutableCapture = true, type = captureType)
                }
            }
        }
    }
}

private enum class ConstructorMode {
    Init,
    Use,
}

private data class DeclInfo(
    val decl: TmpL.NameDeclaration,
    val importInfo: DeclImportInfo? = null,
    val local: Boolean = false,
    val mutableCapture: Boolean = false,
    /** Actual module level value. */
    val topper: Boolean = false,
    val type: Rust.Type? = null,
    val typeFrom: Descriptor? = null,
    val whereItem: Rust.WhereItem? = null,
) {
    val assignOnce get() = (decl as? TmpL.VarLike)?.assignOnce ?: true
    val importedName get() = importInfo?.exportedName
    val name get() = decl.name.name
}

private data class DeclImportInfo(
    val exportedName: ExportedName,
    val sig: TmpL.ImportSignature?,
)

private class FunctionContext(
    val captures: Map<ResolvedName, DeclInfo>,
    val decl: TmpL.FunctionLike,
    val methodizeds: Set<ResolvedName>,
    val mutableCaptures: Set<ResolvedName>,
    val returnType: Description,
    var constructorMode: ConstructorMode? = null,
)

private class NeverRefException : Exception()

private interface StatementProcessor {
    fun processStatements(
        statements: List<TmpL.Statement>,
        results: MutableList<Rust.Statement>,
        skipLastReturn: Boolean = false,
    )
}

// TODO Make actual id paths and such.
internal const val ANY_NAME = "temper_core::AnyValue"
internal const val ARC_NAME = "std::sync::Arc"
internal const val ARC_NEW_NAME = "$ARC_NAME::new"
internal const val AS_ENUM_NAME = "as_enum"
internal const val CLONE_BOXED_NAME = "clone_boxed"
internal const val DENSE_BIT_VECTOR_NAME = "temper_core::DenseBitVector"
internal const val DEQUE_NAME = "temper_core::Deque"
internal const val ENUM_NAME_SUFFIX = "Enum"
internal const val EQ_NAME = "std::cmp::Eq"
internal const val ERROR_NAME = "temper_core::Error"
internal const val ERROR_NEW_NAME = "$ERROR_NAME::new"
internal const val GENERATOR_NAME = "temper_core::Generator"
internal const val HASH_NAME = "std::hash::Hash"
internal const val INIT_ONCE_NAME = "INIT_ONCE"
internal const val IS_NAME = "temper_core::is"
internal const val LIST_NAME = "temper_core::List"
internal const val LIST_BUILDER_NAME = "temper_core::ListBuilder"
internal const val LISTED_NAME = "temper_core::Listed"
internal const val LISTED_TRAIT_NAME = "temper_core::ListedTrait"
internal const val MAP_NAME = "temper_core::Map"
internal const val MAP_BUILDER_NAME = "temper_core::MapBuilder"
internal const val MAP_KEY_NAME = "temper_core::MapKey"
internal const val MAPPED_NAME = "temper_core::Mapped"
internal const val ONCE_LOCK_NAME = "std::sync::OnceLock"
internal const val OPTION_NAME = "Option"
internal const val PARTIAL_EQ_NAME = "std::cmp::PartialEq"
internal const val PHANTOM_DATA_NAME = "std::marker::PhantomData"
internal const val RESULT_NAME = "temper_core::Result"
internal const val RW_LOCK_NAME = "std::sync::RwLock"
internal const val SAFE_GENERATOR_NAME = "temper_core::SafeGenerator"
internal const val SEND_NAME = "std::marker::Send"
internal const val STATIC_LIFETIME = "'static"
internal const val STRING_NAME = "String"
internal const val SYNC_NAME = "std::marker::Sync"
internal const val TO_ARC_STRING_NAME = "temper_core::ToArcString"
internal const val TO_LIST_NAME = "temper_core::ToList"
internal const val TO_LIST_BUILDER_NAME = "temper_core::ToListBuilder"
internal const val TO_LISTED_NAME = "temper_core::ToListed"
internal const val TO_LISTED_TO_LISTED_NAME = "temper_core::ToListed::to_listed"
internal const val TRAIT_NAME_SUFFIX = "Trait"
internal const val TYPE_ID_NAME = "std::any::TypeId"
internal const val TYPE_ID_OF_NAME = "std::any::TypeId::of"
