package lang.temper.be.java

import lang.temper.be.Backend
import lang.temper.be.names.DescriptorChain
import lang.temper.be.names.LookupNameVisitor
import lang.temper.be.names.NameLookup
import lang.temper.be.names.NameSelection
import lang.temper.be.names.ancestors
import lang.temper.be.names.containingFunclike
import lang.temper.be.names.dependencyCategory
import lang.temper.be.names.firstDeclOf
import lang.temper.be.names.isAssign
import lang.temper.be.names.isLocal
import lang.temper.be.names.isLooping
import lang.temper.be.tmpl.TmpL
import lang.temper.log.Position
import lang.temper.name.ExportedName
import lang.temper.name.ModuleName
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.name.identifiers.IdentStyle
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.isBooleanLike
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.withType
import lang.temper.value.DependencyCategory.Production
import lang.temper.value.TString
import lang.temper.value.connectedSymbol
import lang.temper.be.java.Java as J

class JavaNames private constructor(
    val javaLang: JavaLang,
    val currentModuleInfo: ModuleInfo?,
    private val libraries: JavaLibraryConfigs,
    private val sams: MutableMap<Signature2, Sam>,
    private val samNames: MutableSet<String>,
    private val moduleMap: MutableMap<ModuleName, JavaNames>,
    private var nameLookup: NameLookup,
) {
    constructor(
        javaLang: JavaLang,
        libraries: JavaLibraryConfigs,
    ) : this(
        javaLang = javaLang,
        currentModuleInfo = null,
        libraries = libraries,
        sams = mutableMapOf(),
        samNames = mutableSetOf(),
        moduleMap = mutableMapOf(),
        nameLookup = NameLookup.empty,
    )

    private val supportNetwork = JavaSupportNetwork.supportFor(javaLang)

    val samTypes: List<Pair<Signature2, Sam>>
        get() =
            sams.entries
                .map { (ft, sam) -> ft to sam }
                .sortedBy { it.second.klassName }

    private val currentModule: ModuleName get() = currentModuleInfo!!.module

    private fun modInfoFor(originalName: ResolvedName): ModuleInfo =
        when (originalName) {
            is ExportedName -> forModule(originalName.origin.loc as ModuleName)
            else -> this
        }.currentModuleInfo!!

    private fun qualifiedClassName(name: ResolvedName, ctx: DescriptorChain?): QualifiedName =
        modInfoFor(name).qualifiedClassName(ctx?.dependencyCategory() ?: Production)
    private fun qualifiedFullName(name: ResolvedName, ctx: DescriptorChain?): QualifiedName =
        qualifiedClassName(name, ctx).qualify(distinctOutName(name))
    private fun typeNamePair(name: ResolvedName, ctx: DescriptorChain?): Pair<QualifiedName, OutName> =
        qualifiedClassName(name, ctx) to distinctOutName(name)

    /** Register a simple outname (sans numbers) for a given resolved name. */
    private fun simpleOutName(name: ResolvedName): OutName =
        OutName(name.simpleSafeText(), name)

    /** Register a distinct outname (preserve numbers) for a given resolved name. */
    private fun distinctOutName(name: ResolvedName): OutName =
        OutName(name.distinctSafeText(), name)

    fun forModule(module: ModuleName): JavaNames = moduleMap.getOrPut(module) {
        JavaNames(
            javaLang = javaLang,
            currentModuleInfo = libraries.moduleInfo(module),
            libraries = libraries,
            sams = sams,
            samNames = samNames,
            moduleMap = moduleMap,
            nameLookup = nameLookup,
        )
    }

    /** Construct a usage visitor to analyze the entire module set. */
    fun scanNames(moduleSet: TmpL.ModuleSet) {
        val visitor = LookupNameVisitor()
        visitor.visit(moduleSet)
        val lookup = visitor.toLookup()
        nameLookup = lookup
        moduleMap.values.forEach {
            it.nameLookup = lookup
        }
    }

    /** Find all known names for [Backend.saveKeepFiles] */
    fun allNames(): List<NameSelection> =
        nameLookup.qNameMappings()
            .entries
            .filter { (_, qName) -> qName != null }
            .map { (resolvedPair, qName) ->
                NameSelection(
                    qualifiedName = qName!!,
                    selectedName = resolvedPair.second.simpleSafeText(),
                )
            }

    fun findDeclNode(name: TmpL.Id) = nameLookup.lookupDeclDescriptor(currentModule, name.name)?.node

    private fun resolveImport(localName: TmpL.Id) = resolveImportedName(localName.name)

    private fun resolveImportedName(localName: ResolvedName) =
        nameLookup.lookupExternalName(currentModule, localName) ?: localName

    fun specialIdent(pos: Position, ident: String) =
        OutName(ident, null).toIdentifier(pos)

    fun moduleField(name: TmpL.Id): Pair<QualifiedName, OutName> =
        typeNamePair(resolveImport(name), nameLookup.lookupDeclDescriptor(currentModule, name.name))

    fun moduleFunction(name: TmpL.Id): Pair<QualifiedName, OutName> =
        typeNamePair(resolveImport(name), nameLookup.lookupDeclDescriptor(currentModule, name.name))

    private fun fieldName(name: TmpL.Id) = OutName(name.name.simpleSafeText(), name.name)

    /** Translate an internal field name. */
    fun field(name: TmpL.Id): J.Identifier = fieldName(name).toIdentifier(name.pos)

    fun field(prop: TmpL.PropertyId): J.Identifier = when (prop) {
        is TmpL.ExternalPropertyId -> dotName(prop.name).toIdentifier(prop.pos)
        is TmpL.InternalPropertyId -> field(prop.name)
    }

    /** Translate a static field name in a class declaration. */
    fun staticField(name: TmpL.DotName): J.Identifier =
        dotName(name).toIdentifier(name.pos)

    /** Translate a static field name in a static field lookup. */
    fun staticField(prop: TmpL.PropertyId): OutName = when (prop) {
        is TmpL.ExternalPropertyId -> dotName(prop.name)
        is TmpL.InternalPropertyId -> distinctOutName(prop.name.name)
    }

    /** Get the full name to the support code for a static method call. */
    fun supportName(decl: TmpL.SupportCodeDeclaration): QualifiedName =
        when (val support = decl.init.supportCode) {
            is JavaSeparateStatic -> support.qualifiedName
            else -> error("Expected JavaSeparateCode: $support")
        }

    /** Translate an exportable dot name, e.g. names used in methods and properties. */
    private fun dotName(name: TmpL.DotName): OutName =
        OutName(name.dotNameText.safeIdentifier(), null)

    /** Used in a method declaration. */
    fun method(name: TmpL.DotName): OutName = dotName(name)

    /** Used in a method invocation. */
    fun method(name: TmpL.Id): J.Identifier =
        simpleOutName(name.name).toIdentifier(name.pos)

    fun privateHelperMethod(name: TmpL.Id): J.Identifier =
        distinctOutName(name.name).toIdentifier(name.pos)

    /**
     * If registered as a module function, returns the qualified class name and the method name.
     * This could matter for a direct reference to a function from another module.
     */
    fun moduleFunctionIds(ref: TmpL.Id): Pair<J.QualIdentifier, J.Identifier>? {
        val ctx = nameLookup.lookupDeclDescriptor(currentModule, ref.name) ?: return null
        if (ctx.isLocal() || ctx.node !is TmpL.FunctionLike) {
            return null
        }
        val resolvedName = resolveImport(ref)
        val classId = qualifiedClassName(resolvedName, ctx).toQualIdent(ref.pos)
        val methodId = distinctOutName(resolvedName).toIdentifier(ref.pos)
        return classId to methodId
    }

    private val localNameMap: MutableMap<ResolvedName, LocalName> = mutableMapOf()

    /**
     * Given a name and its declaration descriptor, determine if it is:
     * - captured by a containing scope, including use in init of another captured local
     * - assigned more than once and thus mutable
     */
    private fun isLocalMutablyCaptured(name: ResolvedName, decl: DescriptorChain): Boolean {
        var assigns = when (val declNode = decl.node as TmpL.VarLike) {
            is TmpL.Formal, is TmpL.RestFormal -> 1
            is TmpL.ModuleOrLocalDeclaration -> when (declNode.init) {
                null -> 0
                else -> 1
            }
        }
        val declSite = decl.containingFunclike() ?: return false
        var captures = 0
        for (use in nameLookup.lookupUseDescriptors(currentModule, name)) {
            if (use.containingFunclike() != declSite) {
                captures++
            }
            if (use.isAssign()) {
                assigns++
                for (con in use.ancestors()) {
                    if (con == declSite) break
                    if (con.isLooping()) {
                        assigns++ // assignment in a loop is multiple assignment
                        break
                    }
                }
            } else {
                // If initialized *into* a var, that init could later be lifted into a scope constructor.
                use.node.ancestor { it is TmpL.LocalDeclaration }?.let { receiving ->
                    val receiver = (receiving as TmpL.LocalDeclaration).name
                    val recDesc = nameLookup.lookupDeclDescriptor(currentModule, receiver.name)
                    if (recDesc != null) {
                        if (isLocalMutablyCaptured(receiver.name, recDesc)) {
                            captures++
                        }
                    }
                }
            }
        }
        return assigns > 1 && captures > 0
    }

    private fun isFunctionMutuallyRecursive(name: ResolvedName, ctx: DescriptorChain): Boolean {
        // Check if this is called anywhere from outside this declaration site.
        val declSite = ctx.parent.containingFunclike() ?: return false
        return nameLookup.lookupUseDescriptors(currentModule, name).any { use ->
            use.containingFunclike() != declSite
        }
    }

    private fun fallbackName(name: ResolvedName): LocalName {
        val realName = resolveImportedName(name)
        val outName = distinctOutName(realName)
        if (realName is ExportedName) {
            val modName = realName.origin.loc as? ModuleName
            if (modName != null) {
                val modInfo = forModule(modName).currentModuleInfo
                if (modInfo != null) {
                    return ModuleLevelName(modInfo.qualifiedClassName(Production).qualify(outName))
                }
            }
        }
        return RegularVarName(distinctOutName(name), isMutablyCaptured = false)
    }

    fun lookupLocalNameObj(name: TmpL.Id): LocalName? = lookupLocalNameObj(name.name)

    private fun lookupLocalNameObj(name: ResolvedName): LocalName? {
        val resolvedName = resolveImportedName(name)
        var result = localNameMap[resolvedName]
        if (result != null) return result
        val descr = nameLookup.lookupDeclDescriptor(currentModule, name).firstDeclOf(name)

        result = when (descr?.node) {
            null -> null
            is TmpL.Test, is TmpL.ModuleFunctionDeclaration, is TmpL.SupportCodeDeclaration,
            is TmpL.ModuleLevelDeclaration, is TmpL.PooledValueDeclaration,
            ->
                ModuleLevelName(qualifiedFullName(name, descr))
            is TmpL.Formal, is TmpL.RestFormal, is TmpL.LocalDeclaration -> RegularVarName(
                distinctOutName(name),
                isMutablyCaptured =
                isLocalMutablyCaptured(name, descr),
            )
            is TmpL.LocalFunctionDeclaration ->
                SimpleFuncName(
                    distinctOutName(name),
                    isRecursiveFunc = isFunctionMutuallyRecursive(
                        name,
                        descr,
                    ),
                )
            else -> null
        }
        if (result != null) {
            localNameMap[resolvedName] = result
        }
        return result
    }

    fun lookupRegularLocalNameObj(name: TmpL.Id): RegularVarName {
        val realName = resolveImportedName(name.name)
        return (lookupLocalNameObj(name) as? RegularVarName)
            ?: RegularVarName(distinctOutName(realName), isMutablyCaptured = false)
    }

    fun lookupLocalOrExternalNameObj(name: TmpL.Id): LocalName {
        val realName = resolveImportedName(name.name)
        val found = lookupLocalNameObj(name) ?: lookupLocalNameObj(realName) ?: fallbackName(realName)
        return when (found) {
            is CapturedMutableVarName -> when {
                isInScope(name, found.scopeName) -> ThisCapturedMutableVarName(found.outName)
                else -> found
            }
            else -> found
        }
    }

    private var scopeNumber = 1

    /**
     * Constructs a scope name for local names that need to be lifted into a class.
     * @return a pair of the scope as a local variable itself, and its type name
     */
    fun newScopeDecl(): Pair<OutName, OutName> {
        val suffix = "${scopeNumber++}"
        return OutName("$LOCAL_VAR_PREFIX$suffix", null) to
            OutName("$LOCAL_CLASS_PREFIX$suffix", null)
    }

    fun isInScope(node: TmpL.Tree, scopeName: OutName): Boolean {
        var n: TmpL.Tree? = node
        while (n != null) {
            if (n is TmpL.FunctionLike) {
                val ln = lookupLocalNameObj(n.name.name)
                if (ln != null && ln.scopeName == scopeName &&
                    ln.lift == NameLift.RecursiveFunction
                ) {
                    return true
                }
            }
            n = n.parent as? TmpL.Tree
        }
        return false
    }

    /** Lifts a local variable or function into a scope. */
    fun liftLocal(ref: TmpL.Id, toScope: OutName, lift: NameLift): LocalName {
        val name = ref.name
        val resolvedName = resolveImportedName(name)
        val oldName = lookupLocalNameObj(name)
        val newName = oldName?.liftName(lift, toScope)
        if (newName != null) {
            localNameMap[resolvedName] = newName
            return newName
        }
        return fallbackName(resolvedName)
    }

    /** For e.g. [TmpL.LabeledStatement] to [J.LabeledStatement]. */
    fun label(label: TmpL.JumpLabel): J.Identifier =
        distinctOutName(label.id.name).toIdentifier(label.pos)

    /** For e.g. [TmpL.Formal] to [J.FormalParameter] */
    fun formal(name: TmpL.Id): J.Identifier =
        lookupRegularLocalNameObj(name).asIdentifier(name.pos)

    /** Temporary formal name for rest arguments */
    fun restFormal(name: TmpL.Id): J.Identifier =
        lookupRegularLocalNameObj(name).asRestFormal(name.pos)

    /** For e.g. [TypeFormal] to [J.FormalParameter] */
    fun typeFormal(name: ResolvedName): OutName =
        distinctOutName(name)

    /** Look up the qualified name based on prescans or standard Java names. */
    fun classTypeName(typeDef: TypeDefinition): QualifiedName {
        val standardName = typeDefsToJava[typeDef.word]
        if (standardName != null) return standardName
        if (typeDef is TypeFormal) {
            return typeFormal(typeDef.name).toQualName()
        }
        val connectedKey = TString.unpackOrNull(
            typeDef.metadata[connectedSymbol]?.firstOrNull(),
        )
        if (connectedKey != null) {
            val jt = supportNetwork.translatedConnectedTypeToJavaType(connectedKey, emptyList())
            if (jt is ReferenceType) {
                return jt.name
            }
        }
        return modInfoFor(typeDef.name).packageName.qualify(simpleOutName(typeDef.name))
    }

    fun classTypeName(type: Type2): QualifiedName = withType(
        type,
        fn = { _, sig, _ ->
            samType(sig).klassName
        },
        fallback = { t ->
            classTypeName(t.definition)
        },
    )

    fun classTypeName(typeName: TmpL.TypeName): QualifiedName = when (typeName) {
        is TmpL.ConnectedToTypeName ->
            (typeName.name as JavaType).asReferenceType().name
        is TmpL.TemperTypeName -> classTypeName(typeName.typeDefinition)
    }

    /** Name a declared type. */
    fun typeDeclName(typeName: TmpL.Id): J.Identifier =
        simpleOutName(typeName.name).toIdentifier(typeName.pos)

    private fun syntheticSamName(type: Signature2): String {
        val base = suggestSamName(type).safeIdentifier()
        if (samNames.add(base)) {
            return base
        }
        var hit = 1
        while (hit < ONE_HUNDRED) {
            val name = "${base}${hit++}"
            if (samNames.add(name)) {
                return name
            }
        }
        error("Many name collisions with $base")
    }

    fun samType(funcType: Signature2): Sam =
        sams.getOrPut(funcType) {
            Sam.standard(funcType)
                ?: Sam.synthetic(syntheticSamName(funcType), funcType, pkg = currentModuleInfo!!.samPkg)
        }

    private var ignoredCounter = 1
    fun ignoredIdentifier(pos: Position): J.Identifier {
        return J.Identifier(pos, "${IGNORED_PREFIX}${ignoredCounter++}")
    }

    /**
     * Java standard getter name from a temper property name.
     *
     * See 8.3.1, 8.3.2, 8.8 in [JavaBeans Spec](https://www.oracle.com/java/technologies/javase/javabeans-spec.html)
     * but to summarize:
     *   - the word after get/set must be the same
     *   - decapitalization will change `FooBar` to `fooBar` but leave `BARFoo` as `BARFoo`
     */
    fun getterName(name: TmpL.DotName, returnType: Type2): J.Identifier {
        val dotName = name.dotNameText
        val capName =
            if (dotName.startsWith("is") || dotName.startsWith("get")) {
                dotName // TODO #1385 to figure out a better way to handle this
            } else if (returnType.isBooleanLike) {
                "is" + IdentStyle.Camel.convertTo(IdentStyle.Pascal, dotName)
            } else {
                "get" + IdentStyle.Camel.convertTo(IdentStyle.Pascal, dotName)
            }
        return J.Identifier(name.pos, capName)
    }

    /**
     * Java standard getter name from a temper property name.
     *
     * See 8.3.1, 8.3.2, 8.8 in [JavaBeans Spec](https://www.oracle.com/java/technologies/javase/javabeans-spec.html)
     * but to summarize:
     *   - the word after get/set must be the same
     *   - decapitalization will change `FooBar` to `fooBar` but leave `BARFoo` as `BARFoo`
     */
    fun getterName(name: TmpL.PropertyId, returnType: Type2): J.Identifier = when (name) {
        is TmpL.ExternalPropertyId -> getterName(name.name, returnType)
        is TmpL.InternalPropertyId -> error("invoking getter on internal property")
    }

    /**
     * Java standard setter name from a temper property name.
     *
     * See 8.3.1, 8.3.2, 8.8 in [JavaBeans Spec](https://www.oracle.com/java/technologies/javase/javabeans-spec.html)
     * but to summarize:
     *   - the word after get/set must be the same
     *   - decapitalization will change `FooBar` to `fooBar` but leave `BARFoo` as `BARFoo`
     */
    fun setterName(name: TmpL.DotName): J.Identifier {
        val capName = IdentStyle.Camel.convertTo(IdentStyle.Pascal, name.dotNameText)
        return J.Identifier(name.pos, "set$capName")
    }
}

private const val ONE_HUNDRED = 100
