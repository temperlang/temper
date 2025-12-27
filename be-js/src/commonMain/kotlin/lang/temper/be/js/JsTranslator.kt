package lang.temper.be.js

import lang.temper.ast.VisitCue
import lang.temper.ast.deepCopy
import lang.temper.be.Dependencies
import lang.temper.be.tmpl.ImplicitTypeTag
import lang.temper.be.tmpl.SeparatelyCompiledSupportCode
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpL.MemberOrGarbage
import lang.temper.be.tmpl.TmpLOperator
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.dependencyCategory
import lang.temper.be.tmpl.findDeclaration
import lang.temper.be.tmpl.implicitTypeTag
import lang.temper.be.tmpl.libraryName
import lang.temper.be.tmpl.mapGeneric
import lang.temper.be.tmpl.toTmpL
import lang.temper.be.tmpl.typeOrInvalid
import lang.temper.be.tmpl.withoutBubbleOrNull
import lang.temper.common.Either
import lang.temper.common.Either.Companion.partition
import lang.temper.common.TriState
import lang.temper.common.buildListMultimap
import lang.temper.common.compatRemoveLast
import lang.temper.common.console
import lang.temper.common.ignore
import lang.temper.common.isNotEmpty
import lang.temper.common.putMultiList
import lang.temper.format.CodeFormattingTemplate
import lang.temper.format.toStringViaTokenSink
import lang.temper.lexer.Genre
import lang.temper.lexer.temperAwareBaseName
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePath.Companion.toPseudoPath
import lang.temper.log.FilePathSegment
import lang.temper.log.ParentPseudoFilePathSegment
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.SameDirPseudoFilePathSegment
import lang.temper.log.last
import lang.temper.log.plus
import lang.temper.log.spanningPosition
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ExportedName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.SourceName
import lang.temper.type.Abstractness
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.value.DependencyCategory
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
import kotlin.io.path.Path

private const val DEBUG = false
private inline fun debug(message: () -> Any?) {
    if (DEBUG) {
        val o = message()
        if (o != Unit) {
            console.log("$o")
        }
    }
}

internal class JsTranslator(
    private val jsNames: JsNames,
    private val defaultGenre: Genre,
    private val dependenciesBuilder: Dependencies.Builder<JsBackend>? = null,
    private val jsLibraryNames: Map<DashedIdentifier, String> = emptyMap(),
    private val supportCodes: Map<SupportCode, ResolvedName> = emptyMap(),
    private val keepUnusedImports: Boolean = false,
) {
    internal fun requireExternalReference(reference: JsExternalReference): JsIdentifierName =
        activeModuleParts.externalReferenceToLocalName.getOrPut(reference) {
            jsNames.unusedName("${reference.stableKey.nameText}__%d")
        }

    internal fun requirePropertyReference(reference: JsPropertyReference): JsIdentifierName =
        activeModuleParts.propertyReferenceToLocalName.getOrPut(reference) {
            jsNames.unusedName("${reference.propertyName.text}__%d")
        }

    /** Locals needed by generated code. */
    private val prodModuleParts = ModuleParts()
    private val testModuleParts = ModuleParts()
    private var dependencyMode = DependencyCategory.Production
    private val activeModuleParts get() = when (dependencyMode) {
        DependencyCategory.Production -> prodModuleParts
        DependencyCategory.Test -> testModuleParts
    }
    private val tmpLNameToSupportCode =
        supportCodes.entries.associate { it.value to it.key }

    private val generatorDoAwaitNameStack = mutableListOf<JsIdentifierName>()

    private fun<T> withDependencyMode(dependencyCategory: DependencyCategory, action: () -> T): T {
        val oldMode = dependencyMode
        try {
            dependencyMode = dependencyCategory
            return action()
        } finally {
            dependencyMode = oldMode
        }
    }

    private var betweenBoilerplateBoundaries = false

    private val genreTranslating get() = if (betweenBoilerplateBoundaries) {
        Genre.Library
    } else {
        defaultGenre
    }

    private var libraryName: DashedIdentifier? = null

    fun translate(t: TmpL.Module): List<Translation> = jsNames.forOrigin(t.codeLocation.origin) {
        debug {
            console.log("$t")
        }
        libraryName = t.libraryName

        // Build imports before topLevels, or else local import names get mismatched.
        val ungroupedImports = mutableListOf<Js.ImportDeclaration>()
        translateImports(t.imports, ungroupedImports)
        t.topLevels.forEach topLevels@{
            val dependencyCategory = effectiveDependencyCategory(it)
            when (dependencyCategory) {
                DependencyCategory.Production -> prodModuleParts
                DependencyCategory.Test -> testModuleParts
                null -> return@topLevels
            }.topLevels.addAll(
                withDependencyMode(dependencyCategory) {
                    translateTopLevel(it)
                },
            )
        }

        val imports = run {
            val grouped = buildListMultimap {
                for (import in ungroupedImports) {
                    this.putMultiList(import.source.value, import)
                }
            }
            grouped.map { (_, importDecls) ->
                val first = importDecls.first()
                val pos = importDecls.spanningPosition(first.pos)
                Js.ImportDeclaration(
                    pos,
                    listOf(
                        importDecls.flatMap { it.specifiers }.let { specifiersList ->
                            Js.ImportSpecifiers(
                                specifiersList.spanningPosition(specifiersList.first().pos),
                                specifiersList.flatMap {
                                    (it as Js.ImportSpecifiers).specifiers.deepCopy()
                                },
                            )
                        },
                    ),
                    first.source.deepCopy(),
                )
            }
        }

        // Prune actual imports now that we know what we need.
        fun filterImports(topLevels: List<Js.TopLevel>, imports: List<Js.ImportDeclaration>) =
            when (keepUnusedImports) {
                true -> imports
                false -> findNeededImports(topLevels, imports)
            }
        val prodImports = filterImports(prodModuleParts.topLevels, imports)
        prodModuleParts.explicitImports.addAll(prodImports)

        // Copy imports to test module as needed, with relative paths adjusted.
        val hasTests = t.moduleMetadata.dependencyCategory == DependencyCategory.Test
        val testPath = testDir + t.codeLocation.outputPath
        val testImports = when (hasTests) {
            true -> filterImports(testModuleParts.topLevels, imports.deepCopy()).map { imp ->
                val result = imp.deepCopy()
                if (result.source.value.startsWith(".")) {
                    val relativeToProd = Path(result.source.value).map { "$it" }.toPseudoPath()
                    // We shouldn't have built a broken relative path, so expect non-null.
                    val source = t.codeLocation.outputPath.resolvePseudo(relativeToProd, false)!!
                    result.source.value = testPath.relativePathTo(source).joinToString("/")
                }
                result
            }
            false -> emptyList()
        }
        testModuleParts.explicitImports.addAll(testImports)

        val result = t.result
        if (result != null) {
            prodModuleParts.topLevels.add(
                Js.ExportDefaultDeclaration(
                    result.pos,
                    translateExpression(result),
                ),
            )
        }

        generateImports(t)

        fun adjustTops(topLevels: List<Js.TopLevel>): List<Js.TopLevel> = topLevels.flatMap {
            if (it is Js.BlockStatement) {
                it.takeBody()
            } else {
                listOf(it)
            }
        }

        buildList {
            if (prodModuleParts.topLevels.isNotEmpty() || !hasTests) {
                add(
                    Translation(
                        t.codeLocation.outputPath,
                        Js.Program(t.pos, adjustTops(prodModuleParts.topLevels)),
                        t,
                        DependencyCategory.Production,
                    ),
                )
            }
            if (hasTests) {
                add(
                    Translation(
                        testPath,
                        Js.Program(t.pos, adjustTops(testModuleParts.topLevels)),
                        t,
                        DependencyCategory.Test,
                    ),
                )
            }
        }
    }

    private fun effectiveDependencyCategory(t: TmpL.TopLevel): DependencyCategory? {
        return t.dependencyCategory() ?: when (genreTranslating) {
            Genre.Library -> null
            Genre.Documentation -> DependencyCategory.Production
        }
    }

    private fun generateImports(t: TmpL.Module) {
        findExternalReferencesForModule(t)
        for (moduleParts in listOf(prodModuleParts, testModuleParts)) {
            val generatedImports = moduleParts.generateImports(t.pos.leftEdge)
            val topLevels = moduleParts.topLevels
            var beginningOfEnvelope = 0
            if (beginningOfEnvelope < topLevels.size) {
                val topLevel = topLevels[beginningOfEnvelope]
                if (topLevel.isEnvelopeStart) {
                    beginningOfEnvelope += 1
                }
            }
            topLevels.addAll(
                index = beginningOfEnvelope,
                elements = moduleParts.explicitImports + generatedImports,
            )
        }
    }

    private fun findExternalReferencesForModule(t: TmpL.Module) {
        // Find references to runtime support library so that we can generate an import
        fun findExternalReferencesUsed(tree: TmpL.Tree) {
            for (i in 0 until tree.childCount) {
                val child = tree.childOrNull(i) ?: continue
                findExternalReferencesUsed(child)
            }
            if (tree is TmpL.Id) {
                val supportCode = tmpLNameToSupportCode[tree.name]
                if (supportCode is JsExternalReference) {
                    activeModuleParts.externalReferencesUsed.add(supportCode)
                }
            }
        }
        topLevels@ for (topLevel in t.topLevels) {
            val dependencyCategory = effectiveDependencyCategory(topLevel) ?: continue@topLevels
            withDependencyMode(dependencyCategory) {
                findExternalReferencesUsed(topLevel)
            }
        }
        // Build local names for references used
        for (moduleParts in listOf(prodModuleParts, testModuleParts)) {
            if (moduleParts.externalReferencesUsed.isNotEmpty()) {
                moduleParts.externalReferencesUsed.forEach { ref ->
                    if (ref !in moduleParts.externalReferenceToLocalName) {
                        val exportedName = ref.stableName
                        val declaredName = supportCodes[ref]
                        val localName = declaredName?.let { jsNames.jsName(it) }
                            ?: jsNames.unusedName("${exportedName.text.replace("%", "%%")}_%d")
                        moduleParts.externalReferenceToLocalName[ref] = localName
                    }
                }
            }
        }
    }

    private fun translateImports(
        allImports: Iterable<TmpL.Import>,
        jsImports: MutableCollection<Js.ImportDeclaration>,
    ) {
        // Group imports by location imported from
        val importSpecifierToImports = buildListMultimap {
            allImports.forEach { import ->
                val path = import.path ?: return@forEach
                putMultiList(path.libraryName to path.translatedPath, import)
            }
        }

        for (imports in importSpecifierToImports.values) {
            val firstImport = imports.first()
            val firstPath = firstImport.path!!
            val importPath =
                when (firstPath) {
                    is TmpL.CrossLibraryPath -> {
                        val relativePath = firstPath.to.relativePath()
                        jsLibraryToFilePathSegments(jsLibraryNames.getValue(firstPath.libraryName)) + when {
                            firstPath.to.sourceFile.isFile -> {
                                // If a file, strip off the suffix, which we don't include in exports.
                                val last = listOf(FilePathSegment(relativePath.last().temperAwareBaseName()))
                                relativePath.segments.subList(0, relativePath.segments.size - 1) + last
                            }
                            // ... but more accurately for dir modules, we need the actual full module path.
                            else -> relativePath.segments
                        }
                    }
                    is TmpL.SameLibraryPath -> {
                        var path = firstPath.relativePath
                        val first = path.firstOrNull()
                        if (
                            first !is ParentPseudoFilePathSegment &&
                            first !is SameDirPseudoFilePathSegment
                        ) {
                            // Make CommonJS compatible
                            path = listOf(SameDirPseudoFilePathSegment) + path
                        }
                        path
                    }
                }
                    .join(isDir = false)
            for (import in imports) {
                if (import.sig is TmpL.ImportedConnection) {
                    continue // We don't need connected type imports
                }
                val exportName = JsIdentifierName.escaped(import.externalName.outName!!.outputNameText)
                val imported = Js.Identifier(import.pos, exportName, import.externalName.name)
                val local = import.localName?.let { translateId(it) as? Js.Identifier }
                val externalName = import.externalName.name
                fun addDeclaration(localId: Js.Identifier) {
                    jsImports.add(
                        Js.ImportDeclaration(
                            import.pos,
                            listOf(
                                Js.ImportSpecifiers(
                                    import.pos,
                                    listOf(Js.ImportSpecifier(import.pos, imported, localId)),
                                ),
                            ),
                            Js.StringLiteral(firstPath.pos, importPath),
                        ),
                    )
                }

                if (local != null) {
                    jsNames.useAlias(externalName, local.name)
                    addDeclaration(local)
                } else {
                    jsNames.useAliasAsNeeded(externalName) {
                        addDeclaration(Js.Identifier(import.pos, it, null))
                    }
                }
            }
        }
    }

    internal fun translateExpression(e: TmpL.Expression): Js.Expression = when (e) {
        is TmpL.GarbageExpression -> garbageExpression(e.pos, translateDiagnostic(e))
        is TmpL.ValueReference -> {
            val value = e.value
            when (value.typeTag) {
                TBoolean -> Js.BooleanLiteral(e.pos, TBoolean.unpack(value))
                TFloat64 -> Js.NumericLiteral(e.pos, TFloat64.unpack(value)) // TODO De-inline e, pi, or others?
                TInt -> Js.NumericLiteral(e.pos, TInt.unpack(value))
                TInt64 -> Js.CallExpression(
                    e.pos,
                    JsGlobalReference(ParsedName("BigInt")).asIdentifier(e.pos),
                    listOf(Js.StringLiteral(e.pos, TInt64.unpack(value).toString())),
                )
                TString -> Js.StringLiteral(e.pos, TString.unpack(value))
                TProblem -> garbageExpression(
                    e.pos,
                    toStringViaTokenSink { e.value.renderTo(it, typeInfoIsRedundant = true) },
                )
                TFunction -> TODO(toStringViaTokenSink { e.renderTo(it) })
                TList -> TODO()
                TListBuilder -> TODO()
                TMap, TMapBuilder -> TODO()
                TVoid -> undefined(e)
                TNull -> Js.NullLiteral(e.pos)
                TStageRange -> TODO()
                TSymbol -> Js.CallExpression(
                    e.pos,
                    Js.MemberExpression(
                        e.pos,
                        Js.Identifier(e.pos, JsIdentifierName("Symbol"), null),
                        Js.StringLiteral(e.pos, "for"),
                        computed = true,
                    ),
                    listOf(Js.StringLiteral(e.pos, TSymbol.unpack(value).text)),
                )
                // TODO: If it's a nominal type we should probably link to the class/interface, but otherwise
                // link to something useful with instanceof so that default exports are usable in some way.
                is TType -> Js.StringLiteral(e.pos, "$value")
                TClosureRecord -> TODO()
                is TClass -> TODO("$value")
            }
        }
        is TmpL.AwaitExpression -> {
            val doAwait = generatorDoAwaitNameStack.lastOrNull()
            val promiseExpr = translateExpression(e.promise)
            if (doAwait != null) {
                Js.YieldExpression(
                    pos = e.pos,
                    expr = Js.CallExpression(
                        pos = e.pos,
                        callee = Js.Identifier(e.pos.leftEdge, doAwait, null),
                        arguments = listOf(promiseExpr),
                    ),
                )
            } else {
                // We shouldn't reach here in normal use, but falling back to top-level await
                // is preferable to just outputting an error when showing a translation in the
                // REPL.
                Js.UnaryExpression(
                    pos = e.pos,
                    operator = Js.Operator(e.pos.leftEdge, "await"),
                    argument = promiseExpr,
                )
            }
        }
        is TmpL.BubbleSentinel -> Js.CallExpression(
            e.pos,
            Js.Identifier(e.pos, bubbleException, null),
            emptyList(),
        )
        is TmpL.FunInterfaceExpression -> when (val callable = e.callable) {
            is TmpL.FnReference -> translateId(callable.id)
            is TmpL.FunInterfaceCallable -> translateExpression(callable.expr)
            is TmpL.GarbageCallable -> translateGarbage(callable)
            is TmpL.InlineSupportCodeWrapper,
            is TmpL.ConstructorReference,
            -> TODO("$callable")
            is TmpL.MethodReference -> TODO("Translate to JS bind call if not static, direct use if static")
        }
        is TmpL.Reference -> translateId(e.id)
        is TmpL.This -> translateId(e.id, useThisStack = true)
        is TmpL.CallExpression -> {
            when (val fn = e.fn) {
                is TmpL.ConstructorReference -> translateCallToConstructorReference(e, fn)
                is TmpL.InlineSupportCodeWrapper -> {
                    val supportCode = fn.supportCode as InlinedJs
                    // The cast above is safe because JsSupportNetwork produces no other
                    // InlineSupportCode instances
                    val params = e.parameters
                    supportCode.inlineToTree(
                        e.pos,
                        translateParametersTyped(params),
                        e.type,
                        this,
                    ) as Js.Expression
                }
                is TmpL.FnReference -> Js.CallExpression(
                    pos = e.pos,
                    callee = translateId(fn.id),
                    arguments = translateParameters(e.parameters),
                )
                is TmpL.FunInterfaceCallable -> Js.CallExpression(
                    pos = e.pos,
                    callee = translateExpression(fn.expr),
                    arguments = translateParameters(e.parameters),
                )
                is TmpL.GarbageCallable -> translateGarbage(fn)
                is TmpL.MethodReference -> {
                    val obj = when (val subject = fn.subject) {
                        is TmpL.Expression -> translateExpression(subject)
                        is TmpL.TypeName -> translateTypeName(subject, asExpr = true)
                    }
                    val methodShape = fn.method
                    val (method, _) = decomposeMemberKey(
                        fn.methodName, methodShape?.let { it.name as ResolvedName },
                        methodShape?.visibility?.toTmpL() ?: TmpL.Visibility.Public,
                    )
                    val member = Js.MemberExpression(fn.pos, obj, method)
                    Js.CallExpression(e.pos, member, translateParameters(e.parameters))
                }
            }
        }
        is TmpL.InfixOperation -> Js.InfixExpression(
            e.pos,
            left = translateExpression(e.left),
            operator = Js.Operator(
                e.op.pos,
                when (e.op.tmpLOperator) {
                    TmpLOperator.AmpAmp -> "&&"
                    TmpLOperator.BarBar -> "||"
                    TmpLOperator.PlusInt -> "+"
                    TmpLOperator.EqEqInt -> "==="
                    TmpLOperator.LeInt -> "<="
                    TmpLOperator.LtInt -> "<"
                    TmpLOperator.GeInt -> ">="
                    TmpLOperator.GtInt -> ">"
                },
            ),
            right = translateExpression(e.right),
        )
        is TmpL.PrefixOperation -> {
            Js.UnaryExpression(
                e.pos,
                operator = Js.Operator(
                    e.op.pos,
                    when (e.op.tmpLOperator) {
                        TmpLOperator.Bang -> "!"
                    },
                ),
                argument = translateExpression(e.operand),
            )
        }
        is TmpL.InstanceOfExpression -> dispatchBasedOnCheckedType(
            e.pos,
            e.checkedType,
            e.expr,
            // Boolean tests
            object : TypeTagOperation<TmpL.Expression, Js.Expression> {
                override fun allHaveTypeof(
                    pos: Position,
                    type: TmpL.Type,
                    jsTypeOf: JsTypeOf,
                    x: TmpL.Expression,
                ): Js.Expression = Js.InfixExpression(
                    pos,
                    Js.UnaryExpression(
                        x.pos,
                        Js.Operator(pos.leftEdge, "typeof"),
                        translateExpression(x),
                    ),
                    Js.Operator(pos.rightEdge, "==="),
                    Js.StringLiteral(type.pos, jsTypeOf.stringValue),
                )

                override fun singleton(
                    pos: Position,
                    type: TmpL.Type,
                    jsValue: Js.Expression,
                    x: TmpL.Expression,
                ): Js.Expression = Js.InfixExpression(
                    pos,
                    translateExpression(x),
                    Js.Operator(pos.rightEdge, "==="),
                    jsValue.deepCopy(),
                )

                override fun isInt(
                    pos: Position,
                    type: TmpL.Type,
                    x: TmpL.Expression,
                ): Js.Expression = Js.CallExpression(
                    pos,
                    Js.MemberExpression(
                        pos,
                        Js.Identifier(pos, JsIdentifierName("Number"), null),
                        Js.Identifier(pos, JsIdentifierName("isSafeInteger"), null),
                    ),
                    listOf(translateExpression(x)),
                )

                override fun instanceOf(
                    pos: Position,
                    type: TmpL.Type,
                    typeShape: TypeShape,
                    jsTypeRef: Js.Identifier,
                    x: TmpL.Expression,
                ): Js.Expression = Js.InfixExpression(
                    pos,
                    translateExpression(x),
                    Js.Operator(x.pos.rightEdge, "instanceof"),
                    jsTypeRef.deepCopy(),
                )

                override fun isArray(
                    pos: Position,
                    type: TmpL.Type,
                    x: TmpL.Expression,
                ): Js.Expression = Js.CallExpression(
                    pos,
                    Js.MemberExpression(
                        pos,
                        Js.Identifier(pos, JsIdentifierName("Array"), null),
                        Js.Identifier(pos, JsIdentifierName("isArray"), null),
                    ),
                    listOf(translateExpression(x)),
                )
            },
        )
        is TmpL.CastExpression -> dispatchBasedOnCheckedType(
            e.pos,
            e.checkedType,
            e.expr,
            object : TypeTagOperation<TmpL.Expression, Js.Expression> {
                override fun allHaveTypeof(
                    pos: Position,
                    type: TmpL.Type,
                    jsTypeOf: JsTypeOf,
                    x: TmpL.Expression,
                ) = Js.CallExpression(
                    pos,
                    Js.Identifier(type.pos, requireExternalReference(requireTypeOf), null),
                    listOf(
                        translateExpression(x),
                        Js.StringLiteral(type.pos, jsTypeOf.stringValue),
                    ),
                )

                override fun singleton(
                    pos: Position,
                    type: TmpL.Type,
                    jsValue: Js.Expression,
                    x: TmpL.Expression,
                ) = Js.CallExpression(
                    pos,
                    Js.Identifier(type.pos, requireExternalReference(requireSame), null),
                    listOf(
                        translateExpression(x),
                        jsValue,
                    ),
                )

                /**
                 * We can't really tell what's an Int32 vs a Float64.
                 * TODO Remove unsupported runtime checks from be-js logic? Any other backends also need cleaned?
                 */
                override fun isInt(
                    pos: Position,
                    type: TmpL.Type,
                    x: TmpL.Expression,
                ) = Js.BooleanLiteral(e.pos, false)

                override fun instanceOf(
                    pos: Position,
                    type: TmpL.Type,
                    typeShape: TypeShape,
                    jsTypeRef: Js.Identifier,
                    x: TmpL.Expression,
                ) = Js.CallExpression(
                    pos,
                    Js.Identifier(type.pos, requireExternalReference(requireInstanceOf), null),
                    listOf(
                        translateExpression(x),
                        jsTypeRef,
                    ),
                )

                override fun isArray(
                    pos: Position,
                    type: TmpL.Type,
                    x: TmpL.Expression,
                ) = Js.CallExpression(
                    pos,
                    Js.MemberExpression(
                        pos,
                        Js.Identifier(pos, JsIdentifierName("Array"), null),
                        Js.Identifier(pos, JsIdentifierName("isArray"), null),
                    ),
                    listOf(translateExpression(x)),
                )
            },
        )
        is TmpL.UncheckedNotNullExpression -> translateExpression(e.expression)
        is TmpL.RestParameterExpression -> TODO("$e")
        is TmpL.RestParameterCountExpression -> TODO("$e")
        is TmpL.GetProperty -> {
            val (subject, propertyId) = when (val subject = e.subject) {
                is TmpL.Expression -> translateExpression(subject) to e.property
                is TmpL.TypeName -> {
                    val propertyId = when (val def = subject.sourceDefinition) {
                        is TypeShape -> (e.property as? TmpL.ExternalPropertyId)?.let { external ->
                            def.staticProperties.firstOrNull { prop ->
                                prop.symbol.text == external.name.dotNameText
                            }?.let { prop ->
                                val propName = prop.name
                                when {
                                    propName is ResolvedName && prop.visibility != Visibility.Public ->
                                        // TODO Possible to convert this earlier in TmpL? I hit some snags on first try.
                                        TmpL.InternalPropertyId(e.property.pos, TmpL.Id(e.property.pos, propName))
                                    else -> null
                                }
                            }
                        }
                        else -> null
                    } ?: e.property
                    translateTypeName(subject, asExpr = true) to propertyId
                }
            }
            val (property, propertyComputed) = decomposeMemberKey(propertyId)
            Js.MemberExpression(
                e.pos,
                obj = subject,
                property = property,
                computed = propertyComputed,
                optional = false,
            )
        }
        is TmpL.SupportCodeWrapper -> {
            val supportCode = e.supportCode
            val supportCodeName = supportCodes[supportCode]
                ?.let {
                    jsNames.jsName(it, useThisNameStack = false)!! // !! ok; not this-name
                }
                ?: (supportCode as? JsExternalReference)?.let {
                    requireExternalReference(it)
                }

            if (supportCodeName != null) {
                Js.Identifier(e.pos, supportCodeName, null)
            } else {
                translateSupportCode(supportCode, e.pos, e.type)
                    ?: garbageExpression(
                        e.pos,
                        Js.StringLiteral(e.pos, "Failed to translate support code ${e.supportCode}"),
                    )
            }
        }
    }

    private fun <IN, OUT> dispatchBasedOnCheckedType(
        pos: Position,
        rt: TmpL.AType,
        x: IN,
        op: TypeTagOperation<IN, OUT>,
    ): OUT {
        val type = rt.ot
        return when (rt.implicitTypeTag) {
            ImplicitTypeTag.Null -> op.singleton(pos, type, Js.NullLiteral(rt.pos), x)
            ImplicitTypeTag.Void -> op.singleton(
                pos,
                type,
                Js.UnaryExpression(
                    rt.pos,
                    Js.Operator(rt.pos.leftEdge, "void"),
                    Js.NumericLiteral(rt.pos.rightEdge, 0),
                ),
                x,
            )
            ImplicitTypeTag.Boolean -> op.allHaveTypeof(pos, type, JsTypeOf.boolean, x)
            ImplicitTypeTag.Float64 -> op.isFloat64(pos, type, x)
            ImplicitTypeTag.Int -> op.isInt(pos, type, x)
            ImplicitTypeTag.String -> op.allHaveTypeof(pos, type, JsTypeOf.string, x)
            ImplicitTypeTag.Function -> op.allHaveTypeof(pos, type, JsTypeOf.function, x)
            ImplicitTypeTag.ListBuilder, ImplicitTypeTag.List ->
                // TODO(tjp, backend): Distinguish on frozen? Disable checks for List(Builder)?
                op.isArray(pos, type, x)
            else -> {
                val type = rt.ot.withoutBubbleOrNull
                val nType = type as? TmpL.NominalType
                val typeShape = nType?.typeName?.sourceDefinition as? TypeShape
                if (typeShape != null) {
                    val typeName = translateIdStrict(TmpL.Id(rt.pos, typeShape.name))
                    op.instanceOf(pos, type, typeShape, typeName, x)
                } else {
                    op.singleton(
                        pos, type,
                        garbageExpression(pos, "cannot check instanceof/typeof with $type"),
                        x,
                    )
                }
            }
        }
    }

    /**
     * [JsSupportNetwork] opts into
     * [lang.temper.be.tmpl.BubbleBranchStrategy.CatchBubble]
     * so we shouldn't get handler scope calls.
     */
    private fun translateHandlerScope(t: TmpL.HandlerScope, assignedTo: TmpL.Id?): List<Js.Statement> =
        translateGarbageStatement(
            TmpL.GarbageStatement(
                t.pos,
                diagnostic = TmpL.Diagnostic(t.pos, "$t -> $assignedTo not decompiled to throw"),
            ),
        )

    private fun translateParameters(parameters: List<TmpL.Actual>) =
        parameters.mapGeneric(::translateActual)

    private fun translateParametersTyped(parameters: List<TmpL.Actual>) =
        parameters.mapGeneric { TypedArg<Js.Tree>(translateActual(it), it.typeOrInvalid) }

    private fun translateActual(actual: TmpL.Actual): Js.Actual = when (actual) {
        is TmpL.Expression -> translateExpression(actual)
        is TmpL.RestSpread ->
            Js.SpreadElement(actual.pos, translateId(actual.parameterName))
    }

    /**
     * An expression that can be used to refer to [id].
     *
     * @param useThisStack see [JsNames.withLocalNameForThis].
     */
    private fun translateId(id: TmpL.Id, useThisStack: Boolean = false): Js.Expression =
        when (genreTranslating) {
            Genre.Library -> translateIdForLibrary(id = id, useThisStack = useThisStack)
            Genre.Documentation -> translateIdForDocumentation(id = id, useThisStack = useThisStack)
        }

    private fun translateIdForLibrary(id: TmpL.Id, useThisStack: Boolean): Js.Expression {
        val name = id.name
        val jsName = jsNames.jsName(name, useThisNameStack = useThisStack)
        return if (jsName != null) {
            Js.Identifier(
                pos = id.pos,
                name = jsName,
                sourceIdentifier = name as? ResolvedParsedName,
            )
        } else {
            check(useThisStack)
            Js.ThisExpression(id.pos)
        }
    }

    private fun translateIdForDocumentation(id: TmpL.Id, useThisStack: Boolean): Js.Expression {
        val name = id.name
        return if (useThisStack && jsNames.isThisName(name)) {
            Js.ThisExpression(id.pos)
        } else {
            val jsName = JsIdentifierName(name.prefix())
            return Js.Identifier(
                pos = id.pos,
                name = jsName,
                sourceIdentifier = name as? ResolvedParsedName,
            )
        }
    }

    private fun translateCallToConstructorReference(
        e: TmpL.CallExpression,
        fn: TmpL.ConstructorReference,
    ): Js.Expression = when (genreTranslating) {
        Genre.Library -> translateCallToConstructorReferenceForLibrary(e, fn)
        Genre.Documentation -> translateCallToConstructorReferenceForDocumentation(e, fn)
    }

    private fun translateCallToConstructorReferenceForLibrary(
        e: TmpL.CallExpression,
        fn: TmpL.ConstructorReference,
    ): Js.Expression = Js.NewExpression(
        e.pos,
        translateTypeName(fn.typeName, asExpr = true),
        translateParameters(e.parameters),
    )

    private fun translateCallToConstructorReferenceForDocumentation(
        e: TmpL.CallExpression,
        fn: TmpL.ConstructorReference,
    ) = Js.NewExpression(
        e.pos,
        translateTypeName(fn.typeName, asExpr = true),
        translateParameters(e.parameters),
    )

    private fun translateTypeDeclaration(d: TmpL.TypeDeclaration): List<Js.TopLevel> {
        val nameText = d.parsedTypeName?.symbol?.text ?: d.name.name.rawDiagnostic
        val (mainDeclIndex, topLevels) = when (d.kind) {
            TmpL.TypeDeclarationKind.Interface,
            TmpL.TypeDeclarationKind.Class,
            -> translateTypeDeclaration(d, nameText)
            // TODO(mikesamuel): translateEnumType
            TmpL.TypeDeclarationKind.Enum -> translateTypeDeclaration(d, nameText)
        }
        return if (d.name.name is ExportedName) {
            val topLevelsWithExport = topLevels.toMutableList()
            val toExport = topLevelsWithExport[mainDeclIndex] as Js.Declaration
            topLevelsWithExport[mainDeclIndex] = Js.ExportNamedDeclaration(
                pos = toExport.pos,
                doc = Js.MaybeJsDocComment(toExport.pos.leftEdge, doc = null),
                declaration = toExport,
                specifiers = emptyList(),
                source = null,
            )
            return topLevelsWithExport.toList()
        } else {
            topLevels
        }
    }

    private fun makeClassBuilder(
        fn: TmpL.FunctionLike,
        classNameId: Js.Identifier,
        classDoc: Js.JsDocComment?,
    ): Js.ClassMethod? {
        // TODO Unify any logic with JavaTranslator.classBuilder?
        // If only `this` plus up to 1 more, don't bother with builder. TODO Instead checked named/optional?
        fn.parameters.parameters.count { it.name != fn.parameters.thisName } <= 1 && return null
        // And for now, skip those with rest parameters. TODO Extract to list value?
        fn.parameters.restParameter != null && return null
        // Build the builder, starting with parameters.
        val pos = fn.pos
        val objectParamId = Js.Identifier(pos, JsIdentifierName("props"), null)
        val constructorArgs = mutableListOf<Js.Actual>()
        val typeProperties = mutableListOf<Js.ObjectProperty>()
        params@ for (param in fn.parameters.parameters) {
            param.name == fn.parameters.thisName && continue@params
            // We only have arg names, so run with those. TODO Try to correlate with properties when possible?
            val fakeDotName = TmpL.DotName(param.pos, param.name.name.displayName)
            val (key, computed) = decomposeMemberKey(fakeDotName, null, TmpL.Visibility.Public)
            constructorArgs.add(Js.MemberExpression(param.pos, objectParamId.deepCopy(), key, computed = computed))
            val objectKey = key as Js.Expression
            val type = translateType(param.type).type as Js.Expression
            val optional = param.optional
            typeProperties.add(Js.ObjectProperty(param.pos, objectKey, type, computed = computed, optional = optional))
        }
        // Build parts.
        val templates = (classDoc?.typeInfo as? Js.JsDocClassType)?.templates ?: listOf()
        val paramType = Js.JsDocTypeWrap(pos, Js.ObjectExpression(pos, typeProperties))
        val returnType = when {
            templates.isEmpty() -> classNameId.deepCopy()
            else -> Js.GenericRef(pos, classNameId.deepCopy(), templates.map { it.id.deepCopy() })
        }
        val doc = Js.JsDocFunctionType(
            pos = pos,
            templates = templates.deepCopy(),
            params = listOf(Js.JsDocTagParam(pos, paramType, objectParamId.deepCopy())),
            returnType = Js.JsDocTagReturn(pos, Js.JsDocTypeWrap(pos, returnType)),
        ).let { Js.JsDocComment(pos, it) }
        val statement = Js.ReturnStatement(pos, Js.NewExpression(pos, classNameId.deepCopy(), constructorArgs))
        // Put them together.
        return Js.ClassMethod(
            pos = pos,
            doc = Js.MaybeJsDocComment(pos, doc),
            // Use computed to get around our own limits. TODO Ensure unique key.
            computed = true,
            key = Js.StringLiteral(pos, "new"),
            params = Js.Formals(pos, listOf(Js.Param(pos, objectParamId))),
            body = Js.BlockStatement(pos, listOf(statement)),
            static = true,
            kind = Js.ClassMethodKind.Method,
        )
    }

    private fun translateClassMember(
        member: MemberOrGarbage,
        classNameId: Js.Identifier,
        classDoc: Js.JsDocComment?,
    ): List<Either<Js.ClassBodyMember, Js.Statement>> = when (member) {
        is TmpL.Garbage -> listOf()
        is TmpL.Constructor -> {
            val key = Js.Identifier(member.pos.leftEdge, constructorJsName, null)
            val f = translateFunctionDeclarationUsingName(
                name = key,
                member,
            ) as? Js.FunctionDeclaration

            if (f == null) {
                listOf()
            } else {
                buildList {
                    if (member.visibility.visibility == TmpL.Visibility.Public) {
                        makeClassBuilder(member, classNameId, classDoc)?.also { add(Either.Left(it)) }
                    }
                    Either.Left(
                        Js.ClassMethod(
                            member.pos,
                            doc = translateDocFunctionTypeMaybe(member),
                            key = key,
                            params = f.params,
                            body = f.body!!,
                            kind = Js.ClassMethodKind.Constructor,
                        ),
                    ).also { add(it) }
                }
            }
        }
        is TmpL.NormalMethod, is TmpL.GetterOrSetter, is TmpL.StaticMethod -> {
            @Suppress("USELESS_IS_CHECK")
            check(member is TmpL.DotAccessible && member is TmpL.Method)
            val (visibility, shapeForKey) =
                @Suppress("USELESS_IS_CHECK") // for is Constructor
                when (member) {
                    is TmpL.StaticMethod ->
                        member.visibility.visibility to member.memberShape
                    is TmpL.GetterOrSetter ->
                        member.visibility.visibility to member.propertyShape
                    is TmpL.NormalMethod ->
                        member.visibility.visibility to member.memberShape
                    // unreachable
                    is TmpL.Constructor -> error("$member")
                }
            val nameId = translateIdStrict(member.name)
            val (keyExpr, keyComputed) = decomposeMemberKey(
                member.dotName, shapeForKey.name as ResolvedName, visibility,
            )
            val (f, nameUsed) = jsNames.monitorUsesOfName(member.name.name) {
                translateFunctionDeclarationUsingName(
                    name = nameId,
                    member,
                )
            }
            if (f != null) {
                check(f is Js.FunctionDeclaration)
                if (nameUsed) {
                    // When translating the method, there was an internal reference
                    // to it by its Temper name.
                    // Make sure to capture it via that name.
                    TODO()
                    // Can this actually happen?  What does the `this`
                    // parameter mean in that case
                }
                buildList {
                    add(
                        Either.Left(
                            Js.ClassMethod(
                                member.pos,
                                doc = translateDocFunctionTypeMaybe(member),
                                key = keyExpr,
                                computed = keyComputed,
                                static = member is TmpL.StaticMember,
                                params = f.params,
                                body = f.body!!,
                                kind = when (member) {
                                    is TmpL.Getter -> Js.ClassMethodKind.Get
                                    is TmpL.Setter -> Js.ClassMethodKind.Set
                                    else -> Js.ClassMethodKind.Method
                                },
                            ),
                        ),
                    )
                }
            } else {
                listOf()
            }
        }
        is TmpL.InstanceProperty -> when (member.memberShape.abstractness) {
            Abstractness.Abstract -> emptyList()
            Abstractness.Concrete -> {
                val internalId = TmpL.InternalPropertyId(member.name)

                val (key, keyComputed) = decomposeMemberKey(internalId)
                listOf(
                    Either.Left(
                        Js.ClassProperty(
                            member.pos,
                            doc = translateDocTypeMaybe(member.type),
                            key = key,
                            computed = keyComputed,
                            value = null, // All initialization routed through constructor
                        ),
                    ),
                )
            }
        }
        is TmpL.StaticProperty -> {
            val internalId = TmpL.InternalPropertyId(member.name)
            val (key, keyComputed) = decomposeMemberKey(internalId)
            // TODO Is this guaranteed to be a StaticMemberShape?
            val visibility = member.memberShape.visibility.toTmpL()
            val (extKey, extKeyComputed) = decomposeMemberKey(
                member.dotName, member.name.name, visibility,
            )
            buildList {
                Either.Left(
                    Js.ClassProperty(
                        member.pos,
                        doc = translateDocTypeMaybe(member.type),
                        key = key,
                        computed = keyComputed,
                        static = true,
                        value = translateExpression(member.expression),
                    ),
                ).also { add(it) }
                if (visibility == TmpL.Visibility.Public) {
                    Either.Left(
                        Js.ClassMethod(
                            member.pos,
                            doc = translateDocReturnTypeMaybe(member.type),
                            key = extKey,
                            computed = extKeyComputed,
                            static = true,
                            kind = Js.ClassMethodKind.Get,
                            params = Js.Formals(member.pos, listOf()),
                            body = Js.BlockStatement(
                                member.pos,
                                listOf(
                                    Js.ReturnStatement(
                                        member.pos,
                                        Js.MemberExpression(
                                            member.pos,
                                            Js.ThisExpression(member.pos),
                                            key,
                                            computed = keyComputed,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ).also { add(it) }
                }
            }
        }
    }

    private fun translateTypeDeclaration(
        d: TmpL.TypeDeclaration,
        nameText: String,
    ): Pair<Int, List<Js.TopLevel>> {
        ignore(nameText) // TODO: Maybe attach as a property
        val classNameId = translateIdStrict(d.name)

        val before = mutableListOf<Js.Statement>()
        val elements = mutableListOf<Js.ClassBodyMember>()
        val after = mutableListOf<Js.Statement>()

        val classDoc = translateDocClassType(d)?.also { before.add(it) }

        val memberNames = mutableSetOf<TmpL.DotName>()

        val (elementsPart, otherPart) = d.members.flatMap { m ->
            when (m) {
                is TmpL.Getter -> memberNames.add(m.dotName)
                is TmpL.Setter -> memberNames.add(m.dotName)
                is TmpL.NormalMethod -> memberNames.add(m.dotName)
                is TmpL.StaticMethod -> memberNames.add(m.dotName)
                is TmpL.InstanceProperty -> memberNames.add(m.dotName)
                is TmpL.StaticProperty -> memberNames.add(m.dotName)
                is TmpL.Garbage, is TmpL.Constructor -> {}
            }
            translateClassMember(m, classNameId, classDoc)
        }.partition()

        elements.addAll(elementsPart)
        after.addAll(otherPart)

        val superIdentifiers = d.superTypes.mapNotNull { type ->
            val superDefinition = type.findDeclaration(
                type.typeName.sourceDefinition.name,
            )?.second

            if (superDefinition is TmpL.TypeDeclaration) {
                translateId(superDefinition.name)
            } else {
                null
            }
        }

        val typeFuncName = requireExternalReference(typeSupportCode)

        val classDecl = Js.ClassDeclaration(
            pos = d.pos,
            decorators = Js.Decorators(d.pos.leftEdge, listOf()),
            id = classNameId,
            superClass = Js.CallExpression(d.pos, Js.Identifier(d.pos, typeFuncName, null), superIdentifiers),
            body = Js.ClassBody(
                pos = d.members.spanningPosition(d.pos.rightEdge),
                elements,
            ),
        )

        val topLevels = mutableListOf<Js.TopLevel>()
        topLevels.addAll(before)

        topLevels.add(classDecl)
        val classDeclIndex = topLevels.lastIndex

        topLevels.addAll(after)

        return classDeclIndex to topLevels
    }

    private fun translateTopLevel(t: TmpL.TopLevel): List<Js.TopLevel> = when (t) {
        is TmpL.GarbageTopLevel -> listOf(Js.ThrowStatement(t.pos, translateDiagnostic(t)))
        is TmpL.TypeDeclaration -> translateTypeDeclaration(t)
        is TmpL.TypeConnection ->
            listOf(Js.CommentLine(t.pos, "Type ${t.name.name} connected to ${t.to.typeName}"))
        is TmpL.PooledValueDeclaration -> {
            val name = t.name
            val init = t.init
            listOf(
                Js.VariableDeclaration(
                    t.pos,
                    listOf(
                        Js.VariableDeclarator(
                            t.pos,
                            translateIdStrict(name),
                            translateExpression(init),
                        ),
                    ),
                    Js.DeclarationKind.Const,
                ),
            )
        }
        is TmpL.SupportCodeDeclaration -> {
            val initExpr = translateSupportCode(t.init.supportCode, t.init.pos, t.init.type)
            listOfNotNull(
                initExpr?.let {
                    Js.VariableDeclaration(
                        t.pos,
                        listOf(
                            Js.VariableDeclarator(
                                t.pos,
                                translateIdStrict(t.name),
                                it,
                            ),
                        ),
                        Js.DeclarationKind.Const,
                    )
                },
            )
        }
        is TmpL.FunctionDeclaration -> when (val translated = translateFunctionDeclaration(t)) {
            null -> listOf()
            else -> listOf(maybeExport(translated, translateDocFunctionTypeMaybe(t), t.name))
        }
        is TmpL.Test -> listOf(translateTest(t))
        is TmpL.ModuleLevelDeclaration -> {
            val name = t.name
            val assignOnce = t.assignOnce
            val init = t.init
            listOf(
                maybeExport(
                    declaration = Js.VariableDeclaration(
                        t.pos,
                        listOf(
                            Js.VariableDeclarator(
                                t.pos,
                                translateIdStrict(name),
                                init?.let { translateExpression(init) },
                            ),
                        ),
                        if (assignOnce && init != null) {
                            Js.DeclarationKind.Const
                        } else {
                            Js.DeclarationKind.Let
                        },
                    ),
                    doc = translateDocTypeMaybe(t.type),
                    originalName = name,
                ),
            )
        }
        is TmpL.ModuleInitBlock -> translateStatement(t.body)
        is TmpL.BoilerplateCodeFoldBoundary -> listOf(translateBoilerplateCodeFoldBoundary(t))
        is TmpL.EmbeddedComment -> translateEmbeddedComment(t)
    }

    private fun translateTest(t: TmpL.Test): Js.TopLevel {
        dependenciesBuilder?.addTest(libraryName, t)
        val testName = Js.StringLiteral(t.name.pos, t.rawName)
        // it("test name", function expression);
        return Js.ExpressionStatement(
            t.pos,
            Js.CallExpression(
                t.pos,
                Js.Identifier(t.pos, JsIdentifierName("it"), null),
                listOf(
                    testName,
                    Js.FunctionExpression(
                        t.pos,
                        id = null,
                        params = Js.Formals(t.pos, emptyList()),
                        body = Js.BlockStatement(
                            t.body.pos,
                            buildList {
                                var testInstanceId: Js.Identifier? = null
                                // const test = new Test();
                                // We expect the test parameter, but check in case of manual oddities.
                                t.parameters.parameters.firstOrNull()?.let testParam@{ testParam ->
                                    val nominalType = testParam.type.ot as? TmpL.NominalType
                                        ?: return@testParam
                                    testInstanceId = translateIdStrict(testParam.name)
                                    add(
                                        Js.VariableDeclaration(
                                            testParam.pos,
                                            listOf(
                                                Js.VariableDeclarator(
                                                    testParam.pos,
                                                    id = testInstanceId,
                                                    // Just manually create a new test instance.
                                                    init = Js.NewExpression(
                                                        testParam.pos,
                                                        translateTypeName(nominalType.typeName, asExpr = true),
                                                        emptyList(),
                                                    ),
                                                ),
                                            ),
                                            Js.DeclarationKind.Const,
                                        ),
                                    )
                                }
                                // try { body } finally { test.softFailToHard() }
                                add(
                                    Js.TryStatement(
                                        t.body.pos,
                                        block = Js.BlockStatement(
                                            t.body.pos,
                                            t.body.statements.flatMap { translateStatement(it) },
                                        ),
                                        handler = null,
                                        finalizer = Js.BlockStatement(
                                            t.body.pos,
                                            when (testInstanceId) {
                                                null -> emptyList()
                                                else -> listOf(
                                                    Js.ExpressionStatement(
                                                        t.body.pos,
                                                        Js.CallExpression(
                                                            t.body.pos,
                                                            Js.MemberExpression(
                                                                t.body.pos,
                                                                testInstanceId.deepCopy(),
                                                                Js.Identifier(
                                                                    t.body.pos,
                                                                    JsIdentifierName("softFailToHard"),
                                                                    null,
                                                                ),
                                                            ),
                                                            emptyList(),
                                                        ),
                                                    ),
                                                )
                                            },
                                        ),
                                    ),
                                )
                            },
                        ),
                    ),
                ),
            ),
        )
    }

    private fun maybeExport(
        declaration: Js.Declaration,
        doc: Js.MaybeJsDocComment,
        originalName: TmpL.Id,
    ): Js.TopLevel {
        val name = originalName.name
        return if (name is ExportedName && name.comesFrom(jsNames.origin)) {
            val id = when (declaration) {
                is Js.ClassDeclaration -> declaration.id
                is Js.ExceptionDeclaration -> return declaration
                is Js.FunctionDeclaration -> declaration.id
                is Js.VariableDeclaration -> {
                    check(declaration.declarations.size == 1)
                    declaration.declarations.first().id as Js.Identifier
                }
            }
            id.sourceIdentifier = name // Store so that we can link imports to exports later.
            Js.ExportNamedDeclaration(
                declaration.pos,
                doc = doc,
                declaration = declaration,
                specifiers = emptyList(),
                source = null,
            )
        } else if (doc.doc != null) {
            Js.DocumentedDeclaration(declaration.pos, doc.doc!!, declaration)
        } else {
            declaration
        }
    }

    private fun translateBoilerplateCodeFoldBoundary(t: TmpL.BoilerplateCodeFoldBoundary): Js.CommentLine {
        this.betweenBoilerplateBoundaries = when (t) {
            is TmpL.BoilerplateCodeFoldEnd -> false
            is TmpL.BoilerplateCodeFoldStart -> true
        }
        return Js.CommentLine(t.pos, "#${t.markerText}")
    }

    private fun translateEmbeddedComment(t: TmpL.EmbeddedComment): List<Js.CommentLine> =
        listOf(Js.CommentLine(t.pos, t.commentText))

    /** Translate a [TmpL.Id] which must not be a reference to an implied `this`. */
    private fun translateIdStrict(id: TmpL.Id): Js.Identifier = translateId(id) as Js.Identifier

    private fun translateFunctionDeclarationUsingName(
        name: Js.Identifier,
        d: TmpL.FunctionDeclarationOrMethod,
    ): Js.Declaration? = whileTranslatingFunction(isConstructor = d is TmpL.Constructor) {
        data class JsFnParts(
            val pos: Position,
            val id: Js.Identifier,
            val params: Js.Formals,
            val body: Js.BlockStatement,
            val mayYield: Boolean,
        )

        val generatorNameAllocated: JsIdentifierName? =
            if (d.mayYield) {
                generatorDoAwaitNameStack.add(jsNames.unusedName("await_%d"))
                generatorDoAwaitNameStack.last()
            } else {
                null
            }
        // We might need
        val adapter: JsIdentifierName? = when {
            generatorNameAllocated != null -> requireExternalReference(adaptAwaiter)
            else -> null
        }

        val (fd, maskedThis) = jsNames.withLocalNameForThis(d.parameters.thisName?.name) {
            val leftPos = d.pos.leftEdge
            JsFnParts(
                pos = d.pos,
                id = name.deepCopy(),
                params = Js.Formals(
                    d.parameters.pos,
                    buildList {
                        if (generatorNameAllocated != null) {
                            add(Js.Param(leftPos, Js.Identifier(leftPos, generatorNameAllocated, null)))
                        }
                        d.parameters.parameters.mapNotNullTo(this) { formal ->
                            // do not translate `this` params
                            (translateId(formal.name, useThisStack = true) as? Js.Identifier)?.let { identifier ->
                                Js.Param(formal.pos, identifier)
                            }
                        }
                        d.parameters.restParameter?.let { restFormal ->
                            add(
                                Js.Param(
                                    restFormal.pos,
                                    Js.RestElement(restFormal.pos, translateIdStrict(restFormal.name)),
                                ),
                            )
                        }
                    },
                ),
                body = d.body?.let { block ->
                    // Sometimes pureVirtual methods come out like `myVirtualMethod() { return_1 = null; }`,
                    // which is invalid in strict mode (return_1 is never declared).
                    // So this finds those and makes it run the block below,
                    if (block.statements.size == 1) {
                        when (val first = block.statements.firstOrNull()) {
                            is TmpL.Assignment -> when (val maybeReturn = first.left.name) {
                                is SourceName -> if (maybeReturn.baseName.nameText == "return") {
                                    return@let null
                                }
                                else -> {}
                            }
                            else -> {}
                        }
                    }

                    val body = translateBlockStatement(block, prefix = buildNullDefaults(d.parameters))

                    if (d is TmpL.Constructor) {
                        Js.BlockStatement(
                            d.pos,
                            buildList {
                                add(
                                    Js.ExpressionStatement(
                                        d.pos,
                                        Js.CallExpression(
                                            d.pos,
                                            Js.Super(d.pos),
                                            listOf(),
                                        ),
                                    ),
                                )
                                addAll(
                                    body.body.map { it.deepCopy() },
                                )
                            },
                        )
                    } else {
                        body
                    }
                } ?: return@withLocalNameForThis null,
                mayYield = d.mayYield,
            )
        }

        if (fd == null) {
            return@whileTranslatingFunction null
        }

        val body = fd.body
        if (maskedThis != null) {
            // Add a declaration to make the masked this variable available.
            // This happens when compiling something like
            //     let f(@impliedThis this__1) {
            //         let g() { return this__1; }
            //     }
            // The inner function returns to `this` from the outer.  For that to work, we need to
            val pos = body.pos.leftEdge
            val maskingDecl = Js.VariableDeclaration(
                pos,
                listOf(
                    Js.VariableDeclarator(
                        pos,
                        Js.Identifier(pos, maskedThis, null),
                        Js.ThisExpression(d.parameters.thisName!!.pos),
                    ),
                ),
                Js.DeclarationKind.Const,
            )
            body.body = listOf(maskingDecl) + body.body
        }

        if (generatorNameAllocated != null) {
            generatorDoAwaitNameStack.compatRemoveLast()
        }

        if (adapter == null) {
            Js.FunctionDeclaration(
                pos = fd.pos,
                id = fd.id,
                params = fd.params,
                body = body,
                async = false,
                generator = fd.mayYield,
            )
        } else {
            Js.VariableDeclaration(
                pos = fd.pos,
                declarations = listOf(
                    Js.VariableDeclarator(
                        pos = fd.pos,
                        id = fd.id,
                        init = Js.CallExpression(
                            pos = fd.pos,
                            callee = Js.Identifier(fd.pos.leftEdge, adapter, null),
                            arguments = listOf(
                                Js.FunctionExpression(
                                    pos = fd.pos,
                                    id = fd.id.deepCopy(),
                                    params = fd.params,
                                    body = body,
                                    async = false,
                                    generator = fd.mayYield,
                                ),
                            ),
                        ),
                    ),
                ),
                kind = Js.DeclarationKind.Const,
            )
        }
    }

    private fun buildNullDefaults(parameters: TmpL.Parameters): List<Js.Statement> = buildList {
        for (param in parameters.parameters) {
            // If we're optional and default to null, we need to convert undefined to null.
            param.optionalState == TriState.OTHER || continue
            val pos = param.pos
            val name = translateId(param.name, useThisStack = true) as Js.Identifier
            Js.IfStatement(
                pos,
                test = Js.InfixExpression(pos, name, Js.Operator(pos, "==="), undefined(param)),
                consequent = Js.ExpressionStatement(
                    pos,
                    Js.AssignmentExpression(pos, name.deepCopy(), Js.Operator(pos, "="), Js.NullLiteral(pos)),
                ),
                alternate = null,
            ).also { add(it) }
        }
    }

    private fun translateFunctionDeclaration(
        d: TmpL.FunctionDeclaration,
    ): Js.Declaration? = translateFunctionDeclarationUsingName(translateIdStrict(d.name), d)

    private fun <T> whileTranslatingFunction(isConstructor: Boolean, f: () -> T): T {
        functionDefinitionStack.add(isConstructor)
        val result = f()
        functionDefinitionStack.compatRemoveLast()
        return result
    }

    private val functionDefinitionStack = mutableListOf<Boolean>()
    private val inConstructorBody get() = functionDefinitionStack.lastOrNull() == true

    /**
     * Try to turn a dot name into an identifier, or a computed expression if that's not possible.
     */
    private fun decomposeMemberKey(propertyId: TmpL.PropertyId): Pair<Js.MemberKey, Boolean> {
        val pos = propertyId.pos
        return when (propertyId) {
            is TmpL.InternalPropertyId -> {
                val privateName: JsIdentifierName = jsNames.privateName(propertyId.name.name)
                Js.PrivateName(pos, privateName, propertyId.name.name) to false
            }
            is TmpL.ExternalPropertyId -> {
                val dotNameText = propertyId.name.dotNameText
                if (JsIdentifierGrammar.isIdentifierName(dotNameText)) {
                    Js.Identifier(pos, JsIdentifierName.escaped(dotNameText), null) to false
                } else {
                    Js.StringLiteral(pos, dotNameText) to true
                }
            }
        }
    }

    private fun decomposeMemberKey(
        dotName: TmpL.DotName,
        methodName: ResolvedName?,
        visibility: TmpL.Visibility,
    ): Pair<Js.MemberKey, Boolean> {
        val pos = dotName.pos
        val dotNameText = dotName.dotNameText
        return if (visibility == TmpL.Visibility.Private && methodName != null) {
            val privateName: JsIdentifierName = jsNames.privateName(methodName)
            Js.PrivateName(pos, privateName, methodName) to false
        } else if (JsIdentifierGrammar.isIdentifierName(dotNameText)) {
            Js.Identifier(pos, JsIdentifierName.escaped(dotNameText), null) to false
        } else {
            Js.StringLiteral(pos, dotNameText) to true
        }
    }

    private fun translateDiagnostic(garbage: TmpL.Garbage): Js.Expression =
        when (val diagnostic = garbage.diagnostic) {
            null -> Js.NullLiteral(garbage.pos)
            else -> Js.StringLiteral(diagnostic.pos, diagnostic.text)
        }

    private fun translateStatement(s: TmpL.Statement): List<Js.Statement> {
        return when (s) {
            is TmpL.GarbageStatement -> translateGarbageStatement(s)
            is TmpL.LocalDeclaration -> listOf(
                Js.VariableDeclaration(
                    s.pos,
                    listOf(
                        Js.VariableDeclarator(
                            s.pos,
                            id = translateIdStrict(s.name),
                            init = s.init?.let { translateExpression(it) },
                        ),
                    ),
                    if (s.assignOnce && s.init != null) {
                        Js.DeclarationKind.Const
                    } else {
                        Js.DeclarationKind.Let
                    },
                ),
            )
            is TmpL.LocalFunctionDeclaration -> when (val translated = translateFunctionDeclaration(s)) {
                null -> listOf()
                else -> listOf(translated)
            }
            is TmpL.BlockStatement -> listOf(translateBlockStatement(s))
            is TmpL.ExpressionStatement -> listOf(
                Js.ExpressionStatement(
                    s.pos,
                    translateExpression(s.expression),
                ),
            )
            is TmpL.BreakStatement -> listOf(
                Js.BreakStatement(
                    s.pos,
                    s.label?.let { translateIdStrict(it.id) },
                ),
            )
            is TmpL.ContinueStatement -> listOf(
                Js.ContinueStatement(
                    s.pos,
                    s.label?.let { translateIdStrict(it.id) },
                ),
            )
            is TmpL.LabeledStatement -> {
                val label = translateIdStrict(s.label.id)
                val labeled = translateStatement(s.statement).toOneStatement(s.statement)
                listOf(Js.LabeledStatement(s.pos, label, labeled))
            }
            is TmpL.Assignment -> translateAssignment(s)
            is TmpL.YieldStatement -> listOf(
                Js.ExpressionStatement(
                    s.pos,
                    Js.YieldExpression(s.pos, Js.NullLiteral(s.pos)),
                ),
            )
            is TmpL.ReturnStatement -> {
                val expression = s.expression
                val inConstructorBody = this.inConstructorBody
                listOf(
                    when {
                        // Handle the common case: return from a regular function.
                        !inConstructorBody -> Js.ReturnStatement(
                            s.pos,
                            expression?.let { translateExpression(it) },
                        )
                        // In JS constructors, the return value can override the
                        // constructed value if we're not careful.
                        // It's idiomatic to just return normally, or throw
                        // if the value can't be properly constructed.
                        expression is TmpL.BubbleSentinel -> translateThrow(TmpL.ThrowStatement(s.pos))
                        else -> Js.ReturnStatement(s.pos, null)
                    },
                )
            }
            is TmpL.ModuleInitFailed -> listOf(translateThrow(TmpL.ThrowStatement(s.pos)))
            is TmpL.IfStatement -> listOf(
                Js.IfStatement(
                    s.pos,
                    test = translateExpression(s.test),
                    consequent = translateStatement(s.consequent).toOneStatement(s.consequent),
                    alternate = s.alternate?.let { translateStatement(it).toOneStatement(it) },
                ),
            )
            is TmpL.ThrowStatement -> listOf(translateThrow(s))
            is TmpL.TryStatement -> listOf(
                Js.TryStatement(
                    s.pos,
                    block = translateStatement(s.tried).toBlock(s.tried),
                    handler = Js.CatchClause(
                        s.recover.pos,
                        exceptionDeclaration = null,
                        body = translateStatement(s.recover).toBlock(s.recover),
                    ),
                    finalizer = null,
                ),
            )
            is TmpL.WhileStatement -> listOf(
                Js.WhileStatement(
                    s.pos,
                    test = translateExpression(s.test),
                    body = translateStatement(s.body).toOneStatement(s.body),
                ),
            )
            is TmpL.SetProperty -> listOf(
                Js.ExpressionStatement(s.pos, translateSetProperty(s)),
            )
            is TmpL.HandlerScope -> translateHandlerScope(s, null)
            is TmpL.BoilerplateCodeFoldBoundary -> listOf(translateBoilerplateCodeFoldBoundary(s))
            is TmpL.EmbeddedComment -> translateEmbeddedComment(s)
            // Currently compute jumps are only used with coroutine strategy mode not
            // opted into by the support network.
            is TmpL.ComputedJumpStatement -> TODO()
        }
    }

    private fun translateGarbageStatement(s: TmpL.Garbage): List<Js.Statement> = listOf(
        Js.ThrowStatement(
            s.pos,
            when (val diagnostic = s.diagnostic) {
                null -> Js.NullLiteral(s.pos)
                else -> Js.StringLiteral(diagnostic.pos, diagnostic.text)
            },
        ),
    )

    private fun translateGarbage(g: TmpL.Garbage): Js.Expression = Js.CallExpression(
        pos = g.pos,
        callee = Js.ArrowFunctionExpression(
            pos = g.pos,
            params = Js.Formals(
                pos = g.pos.leftEdge,
                params = listOf(),
                returnType = Js.Identifier(g.pos.leftEdge, JsIdentifierName("never"), null),
            ),
            body = Js.BlockStatement(
                g.pos,
                translateGarbageStatement(g),
            ),
        ),
        listOf(),
    )

    private fun translateBlockStatement(
        b: TmpL.BlockStatement,
        prefix: List<Js.Statement> = listOf(),
    ) = Js.BlockStatement(
        b.pos,
        buildList {
            addAll(prefix)
            for (statement in b.statements) {
                addAll(translateStatement(statement))
            }
        },
    )

    private fun translateAssignment(e: TmpL.Assignment): List<Js.Statement> {
        val left = e.left
        return when (val right = e.right) {
            is TmpL.Expression -> listOf(
                Js.ExpressionStatement(
                    e.pos,
                    Js.AssignmentExpression(
                        e.pos,
                        translateIdStrict(left),
                        Js.Operator(e.pos, "="),
                        translateExpression(right),
                    ),
                ),
            )
            is TmpL.HandlerScope -> translateHandlerScope(right, assignedTo = left)
        }
    }

    private fun translateSetProperty(s: TmpL.SetProperty): Js.Expression {
        val (propertyExpr, keyComputed) = decomposeMemberKey(s.left.property)
        return Js.AssignmentExpression(
            s.pos,
            left = Js.MemberExpression(
                s.left.pos,
                obj = translateExpression(s.left.subject as TmpL.Expression), // Static properties are read-only
                property = propertyExpr,
                computed = keyComputed,
                optional = false,
            ),
            operator = Js.Operator(s.left.pos.rightEdge, "="),
            right = translateExpression(s.right),
        )
    }

    internal fun translateSupportCode(code: SupportCode, pos: Position, type: Signature2): Js.Expression? =
        when (code) {
            // Inlining is handling by inspection of SupportCodeReferences in the tree.
            is InlinedJs -> code.inlineToTree(pos, emptyList(), type.returnType2, this)
                as? Js.Expression
            // Imports are grouped at the top by a separate pre-pass above
            is SeparatelyCompiledSupportCode -> null
            is JsPropertyReference -> null
            else -> TODO("Unrecognized JS SupportCode instance: $code")
        }

    private fun translateThrow(s: TmpL.ThrowStatement) = Js.ThrowStatement(
        s.pos,
        Js.CallExpression(
            s.pos,
            Js.Identifier(s.pos, bubbleException, null),
            emptyList(),
        ),
    )

    private fun translateDocFunctionType(m: TmpL.FunctionDeclarationOrMethod): Js.JsDocComment? {
        return jsNames.withLocalNameForThis(m.parameters.thisName?.name) {
            val templates = m.typeParameters.ot.typeParameters.mapNotNull {
                translateDocTagTypeParam(it)
            }
            val params = m.parameters.parameters.mapNotNull { translateDocTagParam(it) }
            val returnType = if (m.returnType.isVoid) {
                null
            } else {
                Js.JsDocTagReturn(m.returnType.pos, translateType(m.returnType))
            }

            if (templates.isEmpty() && params.isEmpty() && returnType == null) {
                null
            } else {
                Js.JsDocComment(
                    pos = m.pos,
                    typeInfo = Js.JsDocFunctionType(
                        pos = m.pos,
                        templates = templates,
                        params = params,
                        returnType = returnType,
                    ),
                )
            }
        }.first
    }

    private fun translateDocReturnType(type: TmpL.Type): Js.JsDocComment {
        return Js.JsDocComment(
            pos = type.pos,
            typeInfo = Js.JsDocFunctionType(
                type.pos,
                templates = emptyList(),
                params = emptyList(),
                returnType = Js.JsDocTagReturn(type.pos, translateType(type)),
            ),
        )
    }

    private fun translateDocType(type: TmpL.Type): Js.JsDocComment {
        return Js.JsDocComment(type.pos, Js.JsDocTagType(type.pos, translateType(type)))
    }

    private fun translateDocAnyTypeMaybe(pos: Position, translate: () -> Js.JsDocComment?) = Js.MaybeJsDocComment(
        pos,
        translateDocAnyTypeMaybeNull(translate),
    )

    private fun translateDocAnyTypeMaybeNull(translate: () -> Js.JsDocComment?) =
        when (genreTranslating) {
            Genre.Library -> translate()
            Genre.Documentation -> null
        }

    private fun translateDocFunctionTypeMaybe(m: TmpL.FunctionDeclarationOrMethod) = translateDocAnyTypeMaybe(m.pos) {
        translateDocFunctionType(m)
    }

    private fun translateDocClassType(d: TmpL.TypeDeclaration) = translateDocAnyTypeMaybeNull {
        when {
            d.typeParameters.ot.typeParameters.isEmpty() -> null
            else -> Js.JsDocComment(d.pos, Js.JsDocClassType(d.pos, translateDocTypeParameters(d)))
        }
    }

    private fun translateDocTypeParameters(d: TmpL.TypeDeclaration) =
        d.typeParameters.ot.typeParameters.map { formal ->
            // TODO Translate upperBounds instead of always null.
            Js.JsDocTagTemplate(formal.pos, null, translateIdStrict(formal.name))
        }

    private fun translateDocReturnTypeMaybe(type: TmpL.AType) =
        translateDocReturnTypeMaybe(type.ot)

    private fun translateDocReturnTypeMaybe(type: TmpL.Type) = translateDocAnyTypeMaybe(type.pos) {
        translateDocReturnType(type)
    }

    private fun translateDocTypeMaybe(type: TmpL.AType) = translateDocTypeMaybe(type.ot)

    private fun translateDocTypeMaybe(type: TmpL.Type) = translateDocAnyTypeMaybe(type.pos) {
        translateDocType(type)
    }

    private fun translateDocTagParam(f: TmpL.Formal): Js.JsDocTagParam? {
        val id = (translateId(f.name, useThisStack = true) as? Js.Identifier) ?: return null
        return Js.JsDocTagParam(f.pos, type = translateType(f.type), id = id, optional = f.optional)
    }

    private fun translateDocTagTypeParam(f: TmpL.TypeFormal): Js.JsDocTagTemplate? {
        val id = (translateId(f.name, useThisStack = true) as? Js.Identifier) ?: return null
        return Js.JsDocTagTemplate(
            f.pos,
            type = translateTypeIntersection(
                TmpL.TypeIntersection(
                    f.upperBounds.spanningPosition(f.pos),
                    f.upperBounds.map { it.deepCopy() },
                ),
            )?.let { Js.JsDocTypeWrap(it.pos, it) },
            id = id,
        )
    }

    private fun translateType(t: TmpL.AType) = translateType(t.ot)

    private fun translateType(t: TmpL.Type): Js.JsDocTypeWrap {
        return Js.JsDocTypeWrap(t.pos, translateTypeUnwrapped(t))
    }

    private fun translateTypeUnwrapped(t: TmpL.AType): Js.Type =
        translateTypeUnwrapped(t.ot)

    private fun translateTypeUnwrapped(t: TmpL.Type): Js.Type {
        val type = when (t) {
            is TmpL.FunctionType -> translateFunctionType(t)
            is TmpL.NominalType -> translateNominalType(t)
            // TODO Support @throws separately from return type, although TS doesn't care about it, so meh?
            // TODO See: https://github.com/microsoft/TypeScript/issues/31329
            is TmpL.BubbleType, // because exceptions in JS
            is TmpL.NeverType,
            -> {
                val tmpLLiteral = (t.codeFormattingTemplate as CodeFormattingTemplate.LiteralToken)
                Js.Identifier(t.pos, JsIdentifierName("never"), BuiltinName(tmpLLiteral.token.text))
            }
            is TmpL.TopType -> {
                val tmpLLiteral = (t.codeFormattingTemplate as CodeFormattingTemplate.LiteralToken)
                Js.Identifier(t.pos, JsIdentifierName("unknown"), BuiltinName(tmpLLiteral.token.text))
            }
            is TmpL.TypeUnion -> translateTypeUnion(t)
            is TmpL.TypeIntersection -> translateTypeIntersection(t)
            else -> null
        } ?: Js.StringLiteral(t.pos, "Unsupported type: $t : ${t::class}")
        return type
    }

    private fun translateFunctionType(t: TmpL.FunctionType): Js.Type {
        // TODO Type parameters.
        return Js.ArrowFunctionExpression(
            t.pos,
            Js.Formals(
                t.valueFormals.pos,
                t.valueFormals.formals.mapIndexed { index, formal ->
                    val name = when (val name = formal.name?.symbol?.text) {
                        "this" -> null // Just from constructors for now (?), where the signature isn't really right.
                        else -> name
                    } ?: "arg$index"
                    Js.Param(
                        formal.pos,
                        Js.Identifier(formal.pos, JsIdentifierName(name), null),
                        translateTypeUnwrapped(formal.type),
                    )
                },
            ),
            // All types are also expressions.
            translateTypeUnwrapped(t.returnType) as Js.Expression,
        )
    }

    private fun translateTypeName(
        typeName: TmpL.TypeName,
        /**
         * Whether to return an expression that is useful at runtime.
         * For example, static methods are looked up on objects, so
         * `String` is suitable for `String.fromCharCode`, but the
         * lower-case `string` is used in type annotations.
         *
         * false -> Give a type description. `string`
         * true -> return an expression.     `String`
         */
        asExpr: Boolean = false,
    ): Js.SimpleRef = when (typeName) {
        is TmpL.ConnectedToTypeName -> {
            when (val name = typeName.name as JsTargetLanguageTypeName) {
                is JsExternalTypeReference ->
                    Js.Identifier(typeName.pos, requireExternalReference(name), null)
                is JsGlobalReference ->
                    name.asIdentifier(typeName.pos).globalizeThis(typeName.pos)
            }
        }
        is TmpL.TemperTypeName -> {
            val def = typeName.typeDefinition
            if (def is TypeShape && WellKnownTypes.isWellKnown(def)) {
                when (def) {
                    // TODO Cache a map of these or use connected references?
                    WellKnownTypes.float64TypeDefinition,
                    WellKnownTypes.intTypeDefinition,
                    -> if (asExpr) { "Number" } else { "number" }
                    WellKnownTypes.int64TypeDefinition -> if (asExpr) { "BigInt" } else { "bigint" }
                    WellKnownTypes.booleanTypeDefinition -> if (asExpr) { "Boolean" } else { "boolean" }
                    WellKnownTypes.stringTypeDefinition,
                    -> if (asExpr) { "String" } else { "string" }
                    WellKnownTypes.anyValueTypeDefinition -> "unknown"
                    WellKnownTypes.voidTypeDefinition -> "undefined"
                    WellKnownTypes.nullTypeDefinition -> "null"
                    WellKnownTypes.listTypeDefinition,
                    WellKnownTypes.listBuilderTypeDefinition,
                    WellKnownTypes.listedTypeDefinition,
                    -> "Array"
                    WellKnownTypes.mapTypeDefinition,
                    WellKnownTypes.mappedTypeDefinition,
                    WellKnownTypes.mapBuilderTypeDefinition,
                    -> "Map"
                    WellKnownTypes.generatorTypeDefinition,
                    WellKnownTypes.safeGeneratorTypeDefinition,
                    -> "Generator"
                    WellKnownTypes.generatorResultTypeDefinition -> "IteratorResult"
                    WellKnownTypes.valueResultTypeDefinition -> "IteratorYieldResult"
                    WellKnownTypes.doneResultTypeDefinition -> "IteratorReturnResult"
                    else -> null
                }?.let { name -> Js.Identifier(typeName.pos, JsIdentifierName(name), def.name) }
            } else {
                null
            } ?: translateIdStrict(TmpL.Id(typeName.pos, typeName.typeDefinition.name))
        }
    }

    private fun translateNominalType(t: TmpL.NominalType): Js.Type {
        when (val def = t.typeName.sourceDefinition) {
            WellKnownTypes.voidTypeDefinition -> return Js.VoidType(t.pos)
            WellKnownTypes.nullTypeDefinition -> return Js.NullLiteral(t.pos)
            WellKnownTypes.emptyTypeDefinition -> return Js.ObjectExpression(t.pos, emptyList())
            WellKnownTypes.doneResultTypeDefinition -> return Js.GenericRef(
                t.pos,
                Js.Identifier(t.pos, JsIdentifierName("IteratorReturnResult"), def.name),
                listOf(Js.Identifier(t.pos.rightEdge, JsIdentifierName("any"), null)),
            )
            else -> {}
        }

        val id: Js.SimpleRef = translateTypeName(t.typeName)
        return if (t.params.isEmpty()) {
            id
        } else {
            Js.GenericRef(t.pos, id, t.params.map { translateTypeUnwrapped(it.ot) })
        }
    }

    private fun translateTypeUnion(t: TmpL.TypeUnion): Js.Type? {
        // Filter out never types since `A | never` in TS is just `A`.
        val neverLike = { type: TmpL.Type -> type is TmpL.NeverType || type is TmpL.BubbleType }
        val types = when (t.types.any(neverLike)) {
            true -> t.types.filterNot(neverLike).let { types ->
                // Support degenerate `Bubble | Bubble` unions.
                when (types.isEmpty() && t.types.isNotEmpty()) {
                    true -> listOf(t.types.first())
                    false -> types
                }
            }
            false -> t.types
        }
        // Bail out on degenerate case.
        val count = types.size
        if (count < 1) {
            return null
        }
        // Easier to use loop vs recursion for me here, so we don't have to construct temporary TmpL.Type instances.
        var result = translateTypeUnwrapped(types.first()) as Js.Expression
        for (index in 1 until count) {
            result = Js.InfixExpression(
                t.pos,
                result,
                Js.Operator(t.pos, "|"),
                translateTypeUnwrapped(types[index]) as Js.Expression,
            )
        }
        return result
    }

    private fun translateTypeIntersection(t: TmpL.TypeIntersection): Js.Type? {
        val types = t.types
        // Below we need to get the first.
        if (types.isEmpty()) { return null }
        var result = translateTypeUnwrapped(types.first()) as Js.Expression
        for (index in 1 until types.size) {
            result = Js.InfixExpression(
                t.pos,
                result,
                Js.Operator(t.pos, "&"),
                translateTypeUnwrapped(types[index]) as Js.Expression,
            )
        }
        return result
    }
}

private val constructorJsName = JsIdentifierName("constructor")

internal fun garbageExpression(
    pos: Position,
    diagnostic: String,
): Js.Expression = garbageExpression(pos, Js.StringLiteral(pos, diagnostic))

internal fun garbageExpression(
    pos: Position,
    diagnostic: Js.Expression,
): Js.Expression = Js.CallExpression(
    pos,
    Js.ArrowFunctionExpression(
        pos,
        Js.Formals(pos, emptyList()),
        Js.BlockStatement(
            pos,
            listOf(
                Js.ThrowStatement(pos, diagnostic),
            ),
        ),
    ),
    emptyList(),
)

internal fun undefined(e: Positioned) = Js.UnaryExpression(
    e.pos,
    // `void 0 === undefined` which is the return value expected of void-ish functions
    // It's also used to request a default argument value in JS.
    Js.Operator(e.pos.leftEdge, "void"),
    Js.NumericLiteral(e.pos, 0),
)

/** Abstraction for bubble exception type, so we can track intentions better. */
internal val bubbleException = JsIdentifierName("Error")

internal val requireInstanceOf = JsUnInlinedExternalFunctionReference(
    DashedIdentifier.temperCoreLibraryIdentifier,
    JsIdentifierName("requireInstanceOf"),
)

internal val requireSame = JsUnInlinedExternalFunctionReference(
    DashedIdentifier.temperCoreLibraryIdentifier,
    JsIdentifierName("requireSame"),
)

internal val requireTypeOf = JsUnInlinedExternalFunctionReference(
    DashedIdentifier.temperCoreLibraryIdentifier,
    JsIdentifierName("requireTypeOf"),
)

private fun List<Js.Statement>.toOneStatement(p: Positioned): Js.Statement {
    return if (this.size == 1) {
        this[0]
    } else {
        Js.BlockStatement(p.pos, this)
    }
}

private fun List<Js.Statement>.toBlock(p: Positioned): Js.BlockStatement {
    if (this.size == 1) {
        val s = this[0]
        if (s is Js.BlockStatement) {
            return s
        }
    }
    return Js.BlockStatement(p.pos, this)
}

/** Name of JS support library in npm */
const val RUNTIME_SUPPORT_LIBRARY_NAME = "@temperlang/core"

val Js.TopLevel.isEnvelopeStart get() =
    this is Js.CommentLine &&
        "#${TmpL.BoilerplateCodeFoldBoundary.START_MARKER_TEXT}" == this.commentText

private fun jsLibraryToFilePathSegments(jsLibraryName: String): List<FilePathSegment> =
    // JS library names can be "/" separated like "@org/project".
    jsLibraryName.split("/").map {
        FilePathSegment(it)
    }

private val TmpL.Type.isVoid: Boolean
    get() = this is TmpL.NominalType && this.params.isEmpty() &&
        this.typeName.sourceDefinition == WellKnownTypes.voidTypeDefinition

private val TmpL.AType.isVoid: Boolean
    get() = when (val t = this.t) {
        is Either.Left -> t.item.isVoid
        is Either.Right -> t.item is TmpL.VoidType
    }

private class ModuleParts {
    val topLevels = mutableListOf<Js.TopLevel>()
    val explicitImports = mutableListOf<Js.ImportDeclaration>()
    val externalReferenceToLocalName = mutableMapOf<JsExternalReference, JsIdentifierName>()
    val externalReferencesUsed = mutableSetOf<JsExternalReference>()
    val propertyReferenceToLocalName = mutableMapOf<JsPropertyReference, JsIdentifierName>()

    fun generateImports(pos: Position): MutableList<Js.TopLevel> {
        val generatedImports = mutableListOf<Js.TopLevel>()
        val externalReferenceToLocalName = externalReferenceToLocalName
        val propertyReferenceToLocalName = propertyReferenceToLocalName
        if (propertyReferenceToLocalName.isNotEmpty()) {
            val groupedByGlobal = buildListMultimap {
                propertyReferenceToLocalName.forEach { (propRef, localName) ->
                    putMultiList(propRef.obj, propRef to localName)
                }
            }
            groupedByGlobal.forEach { (obj, refNamePairs) ->
                // const { property: localName, ... } = obj;
                generatedImports.add(
                    Js.VariableDeclaration(
                        pos,
                        declarations = listOf(
                            Js.VariableDeclarator(
                                pos = pos,
                                id = Js.ObjectPattern(
                                    pos = pos,
                                    properties = refNamePairs
                                        .sortedBy { it.first.propertyName.text }
                                        .map { (propRef, localName) ->
                                            Js.ObjectPropertyPattern(
                                                pos = pos,
                                                key = Js.Identifier(pos, propRef.propertyName, null),
                                                pattern = Js.Identifier(pos, localName, null),
                                            )
                                        },
                                ),
                                init = when (obj) {
                                    is JsGlobalReference ->
                                        obj.asIdentifier(pos).globalizeThis(pos)

                                    else -> TODO("$obj")
                                },
                            ),
                        ),
                        kind = Js.DeclarationKind.Const,
                    ),
                )
            }
        }
        if (externalReferenceToLocalName.isNotEmpty()) {
            val supportCodeToLocalNameGrouped =
                externalReferenceToLocalName.keys.groupBy { it.source }
            supportCodeToLocalNameGrouped.forEach { (sourceIdentifier, externalReferences) ->
                val imports = externalReferences.map { externalReference ->
                    val localName = externalReferenceToLocalName[externalReference]!!
                    val exportedName = externalReference.stableName
                    Js.ImportSpecifier(
                        pos,
                        imported = Js.Identifier(pos, exportedName, null),
                        local = Js.Identifier(pos, localName, null),
                    )
                }
                val sourceLocationText = when (sourceIdentifier) {
                    DashedIdentifier.temperCoreLibraryIdentifier -> RUNTIME_SUPPORT_LIBRARY_NAME
                    else -> sourceIdentifier.text
                }
                if (imports.isNotEmpty()) {
                    generatedImports.add(
                        element = Js.ImportDeclaration(
                            pos,
                            listOf(Js.ImportSpecifiers(pos, imports)),
                            Js.StringLiteral(pos, sourceLocationText),
                        ),
                    )
                }
            }
        }
        return generatedImports
    }
}

private fun findNeededImports(
    topLevels: List<Js.TopLevel>,
    imports: List<Js.ImportDeclaration>,
): List<Js.ImportDeclaration> {
    val givens = buildSet {
        for (imp in imports) {
            for (spec in imp.specifiers) {
                when (spec) {
                    is Js.ImportDefaultSpecifier -> add(spec.local.name)
                    is Js.ImportNamespaceSpecifier -> add(spec.local.name)
                    is Js.ImportSpecifiers -> addAll(spec.specifiers.map { it.local.name })
                }
            }
        }
    }

    val neededIdentifiers = buildSet {
        for (topLevel in topLevels) {
            walkDepthFirst(topLevel) { tree ->
                if (tree is Js.Identifier && tree.name in givens) {
                    add(tree.name)
                }
                VisitCue.Continue
            }
        }
    }

    return imports.mapNotNull { imp ->
        val specs = imp.specifiers.mapNotNull { spec ->
            when (spec) {
                is Js.ImportDefaultSpecifier if spec.local.name in neededIdentifiers -> spec.deepCopy()
                is Js.ImportNamespaceSpecifier if spec.local.name in neededIdentifiers -> spec.deepCopy()
                is Js.ImportSpecifiers -> {
                    val subSpecs = spec.specifiers.mapNotNull { subSpec ->
                        if (subSpec.local.name in neededIdentifiers) {
                            subSpec.deepCopy()
                        } else {
                            null
                        }
                    }
                    if (subSpecs.isEmpty()) {
                        null
                    } else {
                        Js.ImportSpecifiers(spec.pos, subSpecs)
                    }
                }

                else -> null
            }
        }
        if (specs.isEmpty()) {
            null
        } else {
            Js.ImportDeclaration(imp.pos, specs, imp.source)
        }
    }
}

private val adaptAwaiter = JsExternalValueReference(
    DashedIdentifier.temperCoreLibraryIdentifier,
    JsIdentifierName("adaptAwaiter"),
)
