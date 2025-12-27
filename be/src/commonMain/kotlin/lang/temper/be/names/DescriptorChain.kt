package lang.temper.be.names

import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.idKind
import lang.temper.be.tmpl.idReach
import lang.temper.name.ModuleName
import lang.temper.name.ResolvedName
import lang.temper.value.TSymbol
import lang.temper.value.noneSymbol
import lang.temper.value.reachSymbol
import lang.temper.value.testSymbol
import lang.temper.value.DependencyCategory as DepCat

/**
 * Contains the TmpL node in which a name is used or declared, and
 * information about the ancestor (parent) nodes. Most of the useful
 * methods are extension methods to gather information about names.
 */
class DescriptorChain private constructor(
    val name: ResolvedName?,
    private val moduleBack: ModuleName?,
    val node: TmpL.Tree,
    val parent: DescriptorChain?,
) {
    constructor(name: ResolvedName?, node: TmpL.Tree, parent: DescriptorChain?) :
        this(name = name, moduleBack = null, node = node, parent = parent)
    constructor(module: ModuleName, node: TmpL.Tree, parent: DescriptorChain?) :
        this(name = null, moduleBack = module, node = node, parent = parent)

    val module: ModuleName? get() = ancestors().firstNotNullOfOrNull { it.moduleBack }

    // Equality is based on the TmpL node.
    override fun equals(other: Any?): Boolean =
        other is DescriptorChain && other.node == node

    override fun hashCode(): Int = node.hashCode()

    override fun toString() = buildList {
        for (c in ancestors()) {
            val nodeName = c.node::class.simpleName
            when {
                c.name != null -> add("$nodeName(${c.name})")
                c.moduleBack != null -> add("$nodeName(${c.moduleBack})")
                else -> add(nodeName)
            }
        }
        reverse()
    }.joinToString(".")
}

/**
 * Gets the ancestry of descriptors from the name itself up to the module.
 * Null behavior: an empty sequence.
 */
fun DescriptorChain?.ancestors() = sequence {
    var node: DescriptorChain? = this@ancestors
    while (node != null) {
        yield(node)
        node = node.parent
    }
}

/**
 * Find the nearest declaration of the name in this node's ancestors.
 * Null behavior: no ancestors to search, thus return null.
 */
fun DescriptorChain?.firstDeclOf(name: ResolvedName) =
    ancestors().firstOrNull { it.node is TmpL.NameDeclaration && it.name == name }

/**
 * Find a function-like node containing this descriptor.
 * Null behavior: no ancestors to search, thus return null.
 */
fun DescriptorChain?.containingFunclike() = ancestors().firstOrNull { it.node is TmpL.FunctionLike }

/** Determine if this descriptor is for some kind of assignment. */
fun DescriptorChain.isAssign(): Boolean = node is TmpL.Assignment

/** Determine if this names a value or type. */
fun DescriptorChain.idKind(): TmpL.IdKind = when (node) {
    is TmpL.FunctionDeclaration -> node.idKind()
    is TmpL.Type, is TmpL.TypeFormal, is TmpL.TypeDeclaration -> TmpL.IdKind.Type
    is TmpL.Import -> when (node.sig) {
        is TmpL.ImportedType -> TmpL.IdKind.Type
        else -> TmpL.IdKind.Value
    }
    else -> TmpL.IdKind.Value
}

/** Determine if this name is internal or is presented to external modules. */
fun DescriptorChain.idReach(ignoreImport: Boolean = false): TmpL.IdReach = when (val n = this.node) {
    is TmpL.FunctionDeclaration -> n.idReach()
    is TmpL.Import -> if (ignoreImport) TmpL.IdReach.Internal else TmpL.IdReach.External
    else -> TmpL.IdReach.Internal
}

/** Determine if this construct is used in production or test. */
fun DescriptorChain.dependencyCategory(): DepCat? {
    for (cont in ancestors()) {
        if (cont.node is TmpL.Test) {
            return DepCat.Test
        }
        val node = cont.node
        if (node is TmpL.TopLevelDeclaration) {
            val reach = node.metadata.find { it.key.symbol == reachSymbol }?.value
            if (reach !is TmpL.ValueData) {
                return DepCat.Production
            }
            return when (val reachUnpacked = TSymbol.unpack(reach.value)) {
                noneSymbol -> null
                testSymbol -> DepCat.Test
                else -> error("unexpected reach: $reachUnpacked")
            }
        }
    }
    return DepCat.Production
}

/**
 * true iff the name pointed to by the context is a local variable declaration;
 * propagates null
 */
fun DescriptorChain.isLocal(): Boolean = this.ancestors().any { it.node is TmpL.LocalDeclaration }

/** true iff this context is pointing to a looping node */
fun DescriptorChain.isLooping() = this.node is TmpL.WhileStatement
