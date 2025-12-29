package lang.temper.be.names

import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.qName
import lang.temper.name.ModuleName
import lang.temper.name.QName
import lang.temper.name.ResolvedName
import lang.temper.type.TypeShape

/**
 * Interface for visiting names and tracking how names are declared and used.
 */
interface NameVisitor {
    // Runs the visit command
    fun visit(moduleSet: TmpL.ModuleSet): NameVisitor {
        doVisit(moduleSet)
        return this
    }

    // Contextual declarations
    fun inModule(name: ModuleName, module: TmpL.Module): NameVisitor = this
    fun moduleFunctionDecl(name: ResolvedName, decl: TmpL.ModuleFunctionDeclaration): NameVisitor = this
    fun localFunctionDecl(name: ResolvedName, decl: TmpL.LocalFunctionDeclaration): NameVisitor = this
    fun testDecl(name: ResolvedName, decl: TmpL.Test): NameVisitor = this
    fun moduleFieldDecl(name: ResolvedName, decl: TmpL.ModuleLevelDeclaration): NameVisitor = this
    fun moduleFieldDecl(name: ResolvedName, decl: TmpL.PooledValueDeclaration): NameVisitor = this
    fun supportDecl(name: ResolvedName, decl: TmpL.SupportCodeDeclaration): NameVisitor = this
    fun methodDecl(name: ResolvedName, decl: TmpL.Method): NameVisitor = this
    fun propertyDecl(name: ResolvedName, dotName: String, decl: TmpL.Property): NameVisitor = this
    fun typeConnect(name: ResolvedName, decl: TmpL.TypeConnection): NameVisitor = this
    fun typeDecl(name: ResolvedName, type: TypeShape, decl: TmpL.TypeDeclaration): NameVisitor = this
    fun labelDecl(name: ResolvedName, decl: TmpL.LabeledStatement): NameVisitor = this

    // Contextualize
    fun calling(expr: TmpL.CallExpression): NameVisitor = this
    fun subject(expr: TmpL.Subject): NameVisitor = this
    fun looping(stmt: TmpL.WhileStatement): NameVisitor = this
    fun parameters(params: TmpL.Parameters): NameVisitor = this

    // Local declarations
    fun typeFormalDecl(name: ResolvedName, decl: TmpL.TypeFormal) {}
    fun formalVarDecl(name: ResolvedName, decl: TmpL.Formal) {}
    fun formalVarDecl(name: ResolvedName, decl: TmpL.RestFormal) {}
    fun formalVarDecl(name: TmpL.OriginalName?, decl: TmpL.ValueFormal) {}

    // fun formalVarDecl(name: ResolvedName, decl: TmpL.RestFormal) {}
    fun localVarDecl(name: ResolvedName, decl: TmpL.LocalDeclaration) {}
    fun localVarDeclMisc(name: ResolvedName, decl: TmpL.HandlerScope) {}

    // Imports
    fun import(localName: ResolvedName?, externalName: ResolvedName, decl: TmpL.Import) {}

    // Usage
    fun varUse(name: ResolvedName, use: TmpL.Assignment) {}
    fun varUse(name: ResolvedName, use: TmpL.AnyReference) {}
    fun varUseMisc(name: ResolvedName, use: TmpL.RestSpread) {}
    fun varUseMisc(name: ResolvedName, use: TmpL.RestParameterExpression) {}
    fun varUseMisc(name: ResolvedName, use: TmpL.RestParameterCountExpression) {}
    fun propertyUse(subject: ResolvedName?, property: TmpL.PropertyId, use: TmpL.SetProperty) {}
    fun propertyUse(subject: ResolvedName?, property: TmpL.PropertyId, use: TmpL.GetProperty) {}
    fun methodUse(subject: ResolvedName?, dotName: String, use: TmpL.MethodReference) {}
    fun constructorReference(subject: ResolvedName, ref: TmpL.ConstructorReference) {}
    fun receiverUse(name: ResolvedName, decl: TmpL.This) {}
    fun typeUse(name: ResolvedName?, type: TmpL.Type) {}
    fun supportCodeUse(wrapper: TmpL.SupportCodeWrapper) {}
    fun labelUse(name: ResolvedName, use: TmpL.BreakStatement) {}
    fun labelUse(name: ResolvedName, use: TmpL.ContinueStatement) {}
}

abstract class ContextNameVisitor<Self : NameVisitor>(
    val context: DescriptorChain?,
) : NameVisitor {
    protected abstract fun withContext(path: DescriptorChain): Self
    protected val module: ModuleName get() = context?.module!!

    @Suppress("UNCHECKED_CAST")
    override fun visit(moduleSet: TmpL.ModuleSet): Self =
        super.visit(moduleSet) as Self

    override fun inModule(name: ModuleName, module: TmpL.Module): Self =
        withContext(DescriptorChain(module = name, node = module, parent = null))

    override fun moduleFunctionDecl(name: ResolvedName, decl: TmpL.ModuleFunctionDeclaration) =
        withContext(DescriptorChain(name, decl, context))

    override fun localFunctionDecl(name: ResolvedName, decl: TmpL.LocalFunctionDeclaration) =
        withContext(DescriptorChain(name, decl, context))

    override fun testDecl(name: ResolvedName, decl: TmpL.Test) =
        withContext(DescriptorChain(name, decl, context))

    override fun moduleFieldDecl(name: ResolvedName, decl: TmpL.ModuleLevelDeclaration) =
        withContext(DescriptorChain(name, decl, context))

    override fun moduleFieldDecl(name: ResolvedName, decl: TmpL.PooledValueDeclaration) =
        withContext(DescriptorChain(name, decl, context))

    override fun supportDecl(name: ResolvedName, decl: TmpL.SupportCodeDeclaration) =
        withContext(DescriptorChain(name, decl, context))

    override fun methodDecl(name: ResolvedName, decl: TmpL.Method) =
        withContext(DescriptorChain(name, decl, context))

    override fun propertyDecl(name: ResolvedName, dotName: String, decl: TmpL.Property) =
        withContext(DescriptorChain(name, decl, context))

    override fun typeConnect(name: ResolvedName, decl: TmpL.TypeConnection) =
        withContext(DescriptorChain(name, decl, context))

    override fun typeDecl(name: ResolvedName, type: TypeShape, decl: TmpL.TypeDeclaration) =
        withContext(DescriptorChain(name, decl, context))

    override fun labelDecl(name: ResolvedName, decl: TmpL.LabeledStatement) =
        withContext(DescriptorChain(name, decl, context))

    override fun calling(expr: TmpL.CallExpression) =
        withContext(DescriptorChain(null, expr, context))

    override fun looping(stmt: TmpL.WhileStatement) =
        withContext(DescriptorChain(null, stmt, context))
}

open class LookupNameVisitor private constructor(
    path: DescriptorChain? = null,
    private val allModules: MutableSet<ModuleName>,
    private val nameMap: MutableMap<Pair<ModuleName, ResolvedName>, Info>,
    private val imports: MutableMap<Pair<ModuleName, ResolvedName>, ResolvedName>,
    private val supportCodeMap: MutableMap<ResolvedName, Info>,
) : ContextNameVisitor<LookupNameVisitor>(path) {
    constructor() :
        this(null, mutableSetOf(), mutableMapOf(), mutableMapOf(), mutableMapOf())

    /** Save the visitor data to a lookup object */
    open fun toLookup() =
        NameLookup(allModules.toSet(), nameMap.toMap(), supportCodeMap.toMap(), imports.toMap())
    open fun nameData(consume: (ModuleName, ResolvedName, DescriptorChain?, QName?, List<DescriptorChain>) -> Unit) =
        nameMap.forEach { (namePair, info) ->
            consume(namePair.first, namePair.second, info.decl, info.qName, info.use)
        }
    open fun importData(consumeExtInt: (ModuleName, ResolvedName, ResolvedName) -> Unit) =
        imports.forEach { (externalPair, local) ->
            consumeExtInt(externalPair.first, externalPair.second, local)
        }
    open fun supportCodeData(consume: (ResolvedName, DescriptorChain?, QName?, List<DescriptorChain>) -> Unit) {
        supportCodeMap.forEach { (name, info) ->
            consume(name, info.decl, info.qName, info.use)
        }
    }
    override fun withContext(path: DescriptorChain): LookupNameVisitor =
        LookupNameVisitor(
            path = path,
            allModules = allModules,
            nameMap = nameMap,
            imports = imports,
            supportCodeMap = supportCodeMap,
        )
    private fun mergeInfo(
        name: ResolvedName,
        decl: DescriptorChain? = null,
        qName: QName? = null,
        uses: List<DescriptorChain> = emptyList(),
    ) {
        val moduleToName = (decl?.module ?: this.module) to name
        nameMap.compute(moduleToName) { _, info ->
            info.updateMutating(decl, qName, uses)
        }
    }

    /** Declare the name with context for its node. */
    private fun declareWithContext(
        name: ResolvedName,
        node: TmpL.Tree,
        decl: Iterable<TmpL.DeclarationMetadata> = listOf(),
    ): LookupNameVisitor {
        mergeInfo(name, DescriptorChain(name, node, context), decl.qName)
        return this
    }

    /** Declare the name with the current context. */
    private fun declare(
        name: ResolvedName,
        decl: Iterable<TmpL.DeclarationMetadata> = listOf(),
    ): LookupNameVisitor {
        mergeInfo(name, context, decl.qName)
        return this
    }

    private fun declareSupport(
        name: ResolvedName,
        decl: Iterable<TmpL.DeclarationMetadata> = listOf(),
    ): LookupNameVisitor {
        supportCodeMap.compute(name) { _, info ->
            info.updateMutating(context, decl.qName, emptyList())
        }
        return this
    }

    private fun use(name: ResolvedName?, node: TmpL.Tree) {
        if (name != null) {
            mergeInfo(name, uses = listOf(DescriptorChain(name, node, context)))
        }
    }

    override fun inModule(name: ModuleName, module: TmpL.Module): LookupNameVisitor {
        allModules.add(name)
        return super.inModule(name, module)
    }
    override fun moduleFunctionDecl(name: ResolvedName, decl: TmpL.ModuleFunctionDeclaration) =
        super.moduleFunctionDecl(name, decl).declare(name, decl.metadata)
    override fun localFunctionDecl(name: ResolvedName, decl: TmpL.LocalFunctionDeclaration) =
        super.localFunctionDecl(name, decl).declare(name, decl.metadata)
    override fun testDecl(name: ResolvedName, decl: TmpL.Test) =
        super.testDecl(name, decl).declare(name, decl.metadata)
    override fun moduleFieldDecl(name: ResolvedName, decl: TmpL.ModuleLevelDeclaration) =
        super.moduleFieldDecl(name, decl).declare(name, decl.metadata)
    override fun moduleFieldDecl(name: ResolvedName, decl: TmpL.PooledValueDeclaration) =
        super.moduleFieldDecl(name, decl).declare(name, decl.metadata)
    override fun supportDecl(name: ResolvedName, decl: TmpL.SupportCodeDeclaration) =
        super.supportDecl(name, decl).declareSupport(name, decl.metadata)
    override fun methodDecl(name: ResolvedName, decl: TmpL.Method) =
        super.methodDecl(name, decl).declare(name, decl.metadata)
    override fun propertyDecl(name: ResolvedName, dotName: String, decl: TmpL.Property) =
        super.propertyDecl(name, dotName, decl).declare(name, decl.metadata)
    override fun typeConnect(name: ResolvedName, decl: TmpL.TypeConnection) =
        super.typeConnect(name, decl).declare(name, decl.metadata)
    override fun typeDecl(name: ResolvedName, type: TypeShape, decl: TmpL.TypeDeclaration) =
        super.typeDecl(name, type, decl).declare(name, decl.metadata)
    override fun labelDecl(name: ResolvedName, decl: TmpL.LabeledStatement) =
        super.labelDecl(name, decl).declare(name)
    override fun typeFormalDecl(name: ResolvedName, decl: TmpL.TypeFormal) {
        super.typeFormalDecl(name, decl)
        declareWithContext(name, decl)
    }
    override fun formalVarDecl(name: ResolvedName, decl: TmpL.Formal) {
        super.formalVarDecl(name, decl)
        declareWithContext(name, decl)
    }
    override fun formalVarDecl(name: ResolvedName, decl: TmpL.RestFormal) {
        super.formalVarDecl(name, decl)
        declareWithContext(name, decl)
    }
    override fun localVarDecl(name: ResolvedName, decl: TmpL.LocalDeclaration) {
        super.localVarDecl(name, decl)
        declareWithContext(name, decl, decl.metadata)
    }
    override fun localVarDeclMisc(name: ResolvedName, decl: TmpL.HandlerScope) {
        super.localVarDeclMisc(name, decl)
        declareWithContext(name, decl)
    }

    override fun import(localName: ResolvedName?, externalName: ResolvedName, decl: TmpL.Import) {
        super.import(localName, externalName, decl)
        if (localName == null) {
            declareWithContext(externalName, decl)
        } else {
            val moduleName = this.context?.module
            if (moduleName != null) {
                imports[moduleName to externalName] = localName
            }
            declareWithContext(localName, decl)
        }
    }

    override fun varUse(name: ResolvedName, use: TmpL.Assignment) {
        super.varUse(name, use)
        use(name, use)
    }
    override fun varUse(name: ResolvedName, use: TmpL.AnyReference) {
        super.varUse(name, use)
        use(name, use)
    }
    override fun varUseMisc(name: ResolvedName, use: TmpL.RestSpread) {
        super.varUseMisc(name, use)
        use(name, use)
    }
    override fun varUseMisc(name: ResolvedName, use: TmpL.RestParameterExpression) {
        super.varUseMisc(name, use)
        use(name, use)
    }
    override fun varUseMisc(name: ResolvedName, use: TmpL.RestParameterCountExpression) {
        super.varUseMisc(name, use)
        use(name, use)
    }
    override fun propertyUse(subject: ResolvedName?, property: TmpL.PropertyId, use: TmpL.SetProperty) {
        super.propertyUse(subject, property, use)
        use(subject, use)
    }
    override fun propertyUse(subject: ResolvedName?, property: TmpL.PropertyId, use: TmpL.GetProperty) {
        super.propertyUse(subject, property, use)
        use(subject, use)
    }
    override fun methodUse(subject: ResolvedName?, dotName: String, use: TmpL.MethodReference) {
        super.methodUse(subject, dotName, use)
        use(subject, use)
    }
    override fun constructorReference(subject: ResolvedName, ref: TmpL.ConstructorReference) {
        super.constructorReference(subject, ref)
        use(subject, ref)
    }
    override fun receiverUse(name: ResolvedName, decl: TmpL.This) {
        super.receiverUse(name, decl)
        use(name, decl)
    }
    override fun typeUse(name: ResolvedName?, type: TmpL.Type) {
        super.typeUse(name, type)
        use(name, type)
    }
    override fun supportCodeUse(wrapper: TmpL.SupportCodeWrapper) {
        super.supportCodeUse(wrapper)
        val name = wrapper.bestCodeName()
        use(name, wrapper)
    }
    override fun labelUse(name: ResolvedName, use: TmpL.BreakStatement) {
        super.labelUse(name, use)
        use(name, use)
    }
    override fun labelUse(name: ResolvedName, use: TmpL.ContinueStatement) {
        super.labelUse(name, use)
        use(name, use)
    }
}
