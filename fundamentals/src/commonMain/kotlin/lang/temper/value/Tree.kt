package lang.temper.value

import lang.temper.common.LeftOrRight
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import lang.temper.log.ConfigurationKey
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.ParsedName
import lang.temper.name.TemperName
import lang.temper.type.mentionsInvalid
import kotlin.math.max
import kotlin.math.min

/**
 * A node in a Temper abstract syntax tree; the core IR definition for the Temper front-end.
 *
 * This tree form is Lispy.
 * There are only a few kinds of tree nodes, and most syntactic element, including flow control
 * like `if`, are specified as function calls.
 *
 * Variants include:
 * Leaf nodes:
 * - [NameLeaf]s which represent references and targets of simple assignments
 * - [ValueLeaf]s which represent known values.
 * - [StayLeaf]s which allow connecting values to parts of the tree.  E.g. connecting a
 *   function value back to the tree that specifies its body.
 * Inner nodes:
 * - [BlockTree]s which group together trees that are executed together.
 *   In early stages, blocks correspond to lexical blocks, and have a [LinearFlow] meaning that
 *   executing a block executes its children in order.
 *   In later stages, blocks have [StructuredFlow]s which embed a control flow that specifies
 *   the order in which to execute children some of which may be executed zero times or multiply.
 *   In later stages, blocks do not correspond to lexical blocks and instead blocks are flattened
 *   so that they only appear as module or function body roots.
 * - [CallTree]s which represent calls to functions or macros,
 *   and applications of builtin operators.
 * - [DeclTree]s which specify entries in environment records.
 * - [EscTree]s which represent ASTs that are not yet fully backed.
 * - [FunTree]s which are producers of function values.
 */
sealed class Tree(
    /**
     * An object shared by all tree nodes that may be connected.
     * A tree, its parent (if any), and all its children must have the same document.
     */
    val document: Document,
    /** Position metadata for the tree. */
    override val pos: Position,
) : Structured, Positioned, ConfigurationKey.Holder, TypeInferencesHaver {
    abstract val treeType: TreeType

    /**
     * `null` if this tree has no parent or
     * an edge whose source is this tree's parent and whose target is this.
     */
    var incoming: TEdge? = null
        private set

    /** The count of children. */
    abstract val size: Int

    /** The i-th edge. */
    abstract fun edge(i: Int): TEdge

    /** Content stored with the node */
    abstract val content: Any?

    /** Information from type analysis passes. */
    abstract fun clearTypeInferences()

    val indices: IntRange get() = 0 until size

    /** THe i-th child which is the same as the i-th [edge]'s target. */
    abstract fun child(i: Int): Tree

    /** like [edge] but returns `null` if [i] is out of bounds instead of throwing. */
    abstract fun edgeOrNull(i: Int): TEdge?

    /** like [child] but returns `null` if [i] is out of bounds instead of throwing. */
    fun childOrNull(i: Int): Tree? = edgeOrNull(i)?.target
    val lastChild get() = child(size - 1)

    /** A list representation of the [edge]s */
    val edges: List<TEdge> get() = Edges(this)

    /** A list representation of the results of [child] */
    val children: List<Tree> get() = Children(this)

    /** A tree farm that may be used to construct trees in the same document. */
    val treeFarm get() = TreeFarm(document)

    /**
     * Maps a tree to a tree, possibly with a different name type in a different document.
     * Ths is the basis for the [copy] operator and also figures heavily in name resolution.
     */
    fun map(
        document: Document,
        mapName: (n: TemperName) -> TemperName,
        copyInferences: Boolean = false,
        makeTree: (
            descendant: Tree,
            mappedChildList: List<Tree>,
            withChildren: (childList: List<Tree>) -> Tree,
        ) -> Tree = { _, mappedChildList, withChildren ->
            withChildren(mappedChildList)
        },
    ): Tree =
        makeTree(
            this,
            indices.map { i ->
                child(i).map(document, mapName = mapName, copyInferences = copyInferences, makeTree)
            },
        ) { mappedChildList: List<Tree> ->
            mapWithChildren(
                document,
                mappedChildList = mappedChildList,
                mapName = mapName,
                copyInferences = copyInferences,
            )
        }

    /** A helper for [map] which produces a node of the same kind, but with the given children */
    internal abstract fun mapWithChildren(
        document: Document,
        mappedChildList: List<Tree>,
        copyInferences: Boolean,
        mapName: (n: TemperName) -> TemperName,
    ): Tree

    fun copy(destDocument: Document = document, copyInferences: Boolean = false) =
        map(destDocument, mapName = { it }, copyInferences = copyInferences)

    internal open fun destructureChildren(structureSink: StructureSink) {
        structureSink.arr {
            for (child in children) {
                value(child)
            }
        }
    }

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("type", Hints.n) {
            value(treeType.name)
        }
        pos.positionPropertiesTo(this, Hints.u)
        if (this@Tree !is LeafTree) {
            key("children", Hints.n) {
                destructureChildren(this@key)
            }
        }
        val content = this@Tree.content
        if (content != null) {
            key("content", Hints.n, isDefault = isContentDefault(content)) {
                destructureContent(this)
            }
        }
        val typeInferences = this@Tree.typeInferences
        key("typeInferences", Hints.nu) {
            value(typeInferences)
        }
    }

    internal open fun isContentDefault(content: Any) = false

    internal open fun destructureContent(structureSink: StructureSink) {
        structureSink.value(content)
    }

    override val configurationKey: ConfigurationKey get() = document.context.configurationKey

    companion object {
        /** For same-file use only. */
        internal fun setIncoming(tree: Tree, edge: TEdge?) {
            tree.incoming = edge
        }
    }
}

sealed interface TypeInferencesHaver {
    /** Information from type analysis passes. */
    val typeInferences: TypeInferences?
}

sealed interface NoTypeInferencesTree : TypeInferencesHaver {
    override val typeInferences: Nothing? get() = null
}

sealed interface BasicTypeInferencesTree : TypeInferencesHaver {
    override var typeInferences: BasicTypeInferences?
}

sealed interface CallTypeInferencesTree : TypeInferencesHaver {
    override var typeInferences: CallTypeInferences?
}

/** A node that may have children. */
sealed class InnerTree(
    document: Document,
    pos: Position,
    children: List<Tree>,
) : Tree(document = document, pos = pos) {
    private val _outgoing: MutableList<TEdge>

    override val size get() = _outgoing.size

    override fun edge(i: Int) = _outgoing[i]
    override fun child(i: Int) = _outgoing[i].target
    override fun edgeOrNull(i: Int) = _outgoing.getOrNull(i)

    override val content: Any? get() = null

    /**
     * Called when child/edge relationships are mutated to throw out any cached information
     * classifying edges.
     */
    protected open fun invalidatePartsMetadata() {}

    init {
        val candidateOutgoing = mutableListOf<TEdge>()
        children.mapTo(candidateOutgoing) {
            TEdge(this, it)
        }

        for (i in candidateOutgoing.indices) {
            val child = candidateOutgoing[i].target
            require(child.incoming == null && child.document === document) { "$i: $child" }
        }
        this._outgoing = candidateOutgoing
        for (edge in candidateOutgoing) {
            val child = edge.target
            require(child.incoming == null) // Check twice in case of duplicates
            setIncoming(child, edge)
        }
    }

    /** Removes children between [childIndices].first and [childIndices].last */
    fun removeChildren(childIndices: IntRange) = replace(childIndices) {}

    /**
     * Replaces children between [childIndices].first and [childIndices].last with
     * the output of [makeReplacements].
     *
     * [makeReplacements] is called after disconnecting the targets of the specified edges so
     * any trees there have a null [incoming] pointer and can be added to other trees.
     */
    fun replace(childIndices: IntRange, makeReplacements: (Planting).() -> Unit) {
        require(childIndices.step == 1)
        val first = childIndices.first
        val last = childIndices.last
        require(last + 1 >= first && first in 0.._outgoing.size && last in -1.._outgoing.size)
        val replacedPos = spanningPosition(first, last)

        var invalidated = false
        for (i in first..last) {
            val edge = _outgoing[i]
            setIncoming(edge.target, null)
            invalidated = true
        }
        if (invalidated) {
            invalidatePartsMetadata()
        }
        val replacements = treeFarm.growAll(replacedPos) {
            this.makeReplacements()
        }
        invalidated = false
        for (tree in replacements) {
            require(tree.incoming == null) {
                "${ tree.toLispy() } is already part of ${ tree.incoming?.source }"
            }
        }
        val nOld = (last + 1) - first
        val nNew = replacements.size
        val delta = nNew - nOld
        when {
            delta > 0 -> { // Manufacture new edges.
                val newEdges = (nOld until nNew).map {
                    val newTree = replacements[it]
                    val newEdge = TEdge(this, newTree)
                    setIncoming(newTree, newEdge)
                    newEdge
                }
                val insertionPoint = last + 1
                if (newEdges.isNotEmpty()) {
                    _outgoing.subList(insertionPoint, insertionPoint).addAll(newEdges)
                    invalidated = true
                }
            }
            delta < 0 -> { // Remove unneeded edges.
                val unneeded = _outgoing.subList(first + nNew, last + 1)
                if (unneeded.isNotEmpty()) {
                    for (edge in unneeded) {
                        edge.unlink()
                    }
                    unneeded.clear()
                    invalidated = true
                }
            }
        }
        for (i in 0 until min(nNew, nOld)) {
            val newTarget = replacements[i]
            require(newTarget.incoming == null) // Enforce replacements' elements uniqueness

            val edge = _outgoing[first + i]
            edge.setTarget(newTarget)
            setIncoming(newTarget, edge)
            invalidated = true
        }
        if (invalidated) {
            invalidatePartsMetadata()
        }
    }

    fun insert(at: Int = size, makeReplacements: (Planting).() -> Unit) {
        replace(childIndices = at until at, makeReplacements = makeReplacements)
    }

    internal fun replaceEdgeTarget(edge: TEdge, replacement: Tree?): Tree {
        val edgeIndex = _outgoing.indexOf(edge)
        require(edgeIndex >= 0)

        val old = edge.target
        if (replacement !== old) {
            require(replacement?.incoming == null)
            setIncoming(old, null)
            if (replacement == null) {
                _outgoing.removeAt(edgeIndex)
                edge.unlink()
            } else {
                setIncoming(replacement, edge)
                edge.setTarget(replacement)
            }
            invalidatePartsMetadata()
        }
        return old
    }

    /**
     * Mutates this to insert [newChild] immediately before [child]\([childIndex]\)
     * or adds at the end when [childIndex] == [size].
     */
    fun add(childIndex: Int, newChild: Tree) {
        replace(childIndex until childIndex) { Replant(newChild) }
    }

    /** Adds at end. */
    fun add(newChild: Tree) { add(size, newChild) }
}

/** A node that has [content] but no children. */
sealed class LeafTree(
    document: Document,
    pos: Position,
) : Tree(document = document, pos = pos) {
    abstract override val content: Any

    override val size get() = 0
    override fun edgeOrNull(i: Int): TEdge? = null
    override fun child(i: Int): Tree {
        throw IndexOutOfBoundsException()
    }
    override fun edge(i: Int): TEdge {
        throw IndexOutOfBoundsException()
    }
}

/**
 * A node whose result is void and which must be preserved across stages as long as any
 * [ValueLeaf]'s content may reference it.
 * That it is preserved across stage, somewhere in the tree, means that objects external to the
 * tree, like `export`ed partial values, may refer to it.
 *
 * It is so named because:
 * - *StayLeaf*s stay (remain) in the tree
 * - meanings of "stay" relate to connection and supports, and *StayLeaf*s are how values derived
 *   from syntactic constructs that define how they operate.
 */
class StayLeaf(
    document: Document,
    pos: Position,
) : LeafTree(document = document, pos = pos), NoTypeInferencesTree {
    override val treeType: TreeType get() = LeafTreeType.Stay
    override val content get() = Unit
    override fun mapWithChildren(
        document: Document,
        mappedChildList: List<Tree>,
        copyInferences: Boolean,
        mapName: (n: TemperName) -> TemperName,
    ): Tree = StayLeaf(document, pos)

    override fun clearTypeInferences() = Unit

    override fun isContentDefault(content: Any): Boolean = content == Unit
}

/**
 * A node whose result is that of the last of its children to execute.
 * In early stages (<= [syntax][lang.temper.stage.Stage.SyntaxMacro]), blocks correspond to lexical
 * scopes.
 * In later stages, they are reserved for module roots and function bodies as statement-ish
 * constructs are pulled below expression-ish constructs.
 */
class BlockTree(
    document: Document,
    pos: Position,
    children: List<Tree>,
    /** Controls the order in which children are executed. */
    flow: BlockFlow,
) : InnerTree(document = document, pos = pos, children = children), BasicTypeInferencesTree {
    var flow = flow
        internal set

    override val content get() = flow

    override fun isContentDefault(content: Any) = content is LinearFlow

    override val treeType get() = InnerTreeType.Block

    fun replaceFlow(newFlow: BlockFlow) {
        this.flow = newFlow
        invalidatePartsMetadata()
    }

    override var typeInferences: BasicTypeInferences? = null
    override fun clearTypeInferences() { typeInferences = null }

    override fun mapWithChildren(
        document: Document,
        mappedChildList: List<Tree>,
        copyInferences: Boolean,
        mapName: (n: TemperName) -> TemperName,
    ): BlockTree {
        val mapped = BlockTree(document, pos, mappedChildList, flow.copy())
        if (copyInferences) {
            mapped.typeInferences = typeInferences
        }
        return mapped
    }

    override fun destructureContent(structureSink: StructureSink) {
        when (this.flow) {
            is LinearFlow -> structureSink.value("LinearFlow")
            // Instead of destructuring ControlFlow and referencing the child
            // list by index, we special case child list destructuring in destructureChildren
            is StructuredFlow -> structureSink.value("StructuredFlow")
        }
    }

    override fun destructureChildren(structureSink: StructureSink) {
        when (val flow = this.flow) {
            is LinearFlow -> super.destructureChildren(structureSink)
            is StructuredFlow -> structureSink.arr {
                flow.controlFlow.stmts.forEach {
                    destructureControlFlow(it, this@BlockTree, this@arr)
                }
            }
        }
    }

    fun dereference(reference: BlockChildReference): TEdge? {
        val index = reference.index
        return if (index != null && index in 0 until size) {
            edge(index)
        } else {
            null
        }
    }

    private var _parts: BlockParts? = null
    override fun invalidatePartsMetadata() { _parts = null }

    /** A snapshot of this tree's children classified according to function. */
    val parts: BlockParts get() = when (val precomputed = _parts) {
        null -> {
            val recomputed = BlockParts(this)
            _parts = recomputed
            recomputed
        }
        else -> precomputed
    }

    companion object {
        fun wrap(tree: Tree) = BlockTree(
            document = tree.document,
            pos = tree.pos,
            children = listOf(tree),
            flow = LinearFlow,
        )

        fun maybeWrap(tree: Tree) = when (tree) {
            is BlockTree -> tree
            else -> wrap(tree)
        }
    }
}

/**
 * A node that specifies a call to a function or macro.
 */
class CallTree(
    document: Document,
    pos: Position,
    children: List<Tree>,
) : InnerTree(document = document, pos = pos, children = children), CallTypeInferencesTree {
    override val treeType get() = InnerTreeType.Call

    override var typeInferences: CallTypeInferences? = null
    override fun clearTypeInferences() { typeInferences = null }

    override fun mapWithChildren(
        document: Document,
        mappedChildList: List<Tree>,
        copyInferences: Boolean,
        mapName: (n: TemperName) -> TemperName,
    ): CallTree {
        val mapped = CallTree(document, pos, mappedChildList)
        if (copyInferences) {
            mapped.typeInferences = typeInferences
        }
        return mapped
    }
}

/**
 * A node that specifies an environment binding.
 * See [decomposeDecl] to understand how its children are arranged.
 */
class DeclTree(
    document: Document,
    pos: Position,
    children: List<Tree>,
) : InnerTree(document = document, pos = pos, children = children), BasicTypeInferencesTree {
    override val treeType get() = InnerTreeType.Decl

    override var typeInferences: BasicTypeInferences? = null
    override fun clearTypeInferences() { typeInferences = null }

    override fun mapWithChildren(
        document: Document,
        mappedChildList: List<Tree>,
        copyInferences: Boolean,
        mapName: (n: TemperName) -> TemperName,
    ): DeclTree {
        val mapped = DeclTree(document, pos, mappedChildList)
        if (copyInferences) {
            mapped.typeInferences = typeInferences
        }
        return mapped
    }

    private val metadataMap = LiveMetadataMap(
        this,
        skipAtFront = { i, _ -> i == 0 }, // Skip over name
        nFromEndToSkip = 0, // all after name are metadata
    )
    private var _parts: DeclPartsNameless? = null
    private var _partsComputed = false
    override fun invalidatePartsMetadata() {
        metadataMap.invalidate()
        _parts = null
        _partsComputed = false
    }

    /** A view of this tree's children classified according to function. */
    val parts: DeclParts? get() = partsIgnoringName as? DeclParts

    /**
     * Like [parts] but functions even if the declaration's name is not yet well-formed
     * which is important during early stages when we may encounter declarations that
     * need desugaring like `let { x, y } = ...;`.
     */
    val partsIgnoringName: DeclPartsNameless? get() {
        if (!_partsComputed) {
            _partsComputed = true
            _parts = decomposeDecl(this, metadataMap)
        }
        return _parts
    }
}

/**
 * A node whose children are not eagerly evaluated.
 * This is reserved syntactic space for quasi quotation.
 */
class EscTree(
    document: Document,
    pos: Position,
    children: List<Tree>,
) : InnerTree(document = document, pos = pos, children = children), NoTypeInferencesTree {
    override val treeType get() = InnerTreeType.Esc

    override val typeInferences: Nothing? get() = null
    override fun clearTypeInferences() = Unit

    override fun mapWithChildren(
        document: Document,
        mappedChildList: List<Tree>,
        copyInferences: Boolean,
        mapName: (n: TemperName) -> TemperName,
    ): EscTree = EscTree(document, pos, mappedChildList)
}

/**
 * A node that specifies a function value.
 * See [decomposeFun] to understand how its children are arranged.
 */
class FunTree(
    document: Document,
    pos: Position,
    children: List<Tree>,
) : InnerTree(document = document, pos = pos, children = children), BasicTypeInferencesTree {
    override val treeType get() = InnerTreeType.Fun

    override var typeInferences: BasicTypeInferences? = null
    override fun clearTypeInferences() { typeInferences = null }

    override fun mapWithChildren(
        document: Document,
        mappedChildList: List<Tree>,
        copyInferences: Boolean,
        mapName: (n: TemperName) -> TemperName,
    ): FunTree {
        val mapped = FunTree(document, pos, mappedChildList)
        if (copyInferences) {
            mapped.typeInferences = typeInferences
        }
        return mapped
    }

    private val metadataMap = LiveMetadataMap(
        this,
        // Skip over parameters at the front.
        skipAtFront = { _, child -> child is DeclTree },
        nFromEndToSkip = 1, // Skip the body
    )
    private var _parts: FnParts? = null
    private var partsComputed = false
    override fun invalidatePartsMetadata() {
        metadataMap.invalidate()
        _parts = null
        partsComputed = false
    }

    /** A view of this tree's children classified according to function. */
    val parts: FnParts? get() = if (partsComputed) {
        _parts
    } else {
        _parts = decomposeFun(this, metadataMap)
        partsComputed = true
        _parts
    }
}

/** A leaf corresponding to a name. */
sealed class NameLeaf(
    document: Document,
    pos: Position,
    override val content: TemperName,
) : LeafTree(document = document, pos = pos), BasicTypeInferencesTree {
    fun copyLeft(): LeftNameLeaf = LeftNameLeaf(document, pos, content)
    fun copyRight(): RightNameLeaf = RightNameLeaf(document, pos, content)

    init {
        @Suppress("LeakingThis") // sealed, so safe by analysis of subtypes.
        require(content !is ParsedName || !document.isResolved)
    }

    override var typeInferences: BasicTypeInferences? = null
    override fun clearTypeInferences() { typeInferences = null }
}

/** A name used in a left expr (assignment) context. */
class LeftNameLeaf(
    document: Document,
    pos: Position,
    content: TemperName,
) : NameLeaf(document = document, pos = pos, content = content) {
    override val treeType get() = LeafTreeType.LeftName

    override fun mapWithChildren(
        document: Document,
        mappedChildList: List<Tree>,
        copyInferences: Boolean,
        mapName: (n: TemperName) -> TemperName,
    ): LeftNameLeaf {
        val mapped = LeftNameLeaf(document, pos, mapName(content))
        if (copyInferences) {
            mapped.typeInferences = typeInferences
        }
        return mapped
    }
}

/** A name used in a right expr (read) context. */
class RightNameLeaf(
    document: Document,
    pos: Position,
    content: TemperName,
) : NameLeaf(document = document, pos = pos, content = content) {
    override val treeType get() = LeafTreeType.RightName

    override fun mapWithChildren(
        document: Document,
        mappedChildList: List<Tree>,
        copyInferences: Boolean,
        mapName: (n: TemperName) -> TemperName,
    ): RightNameLeaf {
        val mapped = RightNameLeaf(document, pos, mapName(content))
        if (copyInferences) {
            mapped.typeInferences = typeInferences
        }
        return mapped
    }
}

/** A leaf that contains a value. */
class ValueLeaf(
    document: Document,
    pos: Position,
    override val content: Value<*>,
) : LeafTree(document = document, pos = pos), BasicTypeInferencesTree {
    override val treeType get() = LeafTreeType.Value

    override var typeInferences: BasicTypeInferences? = null
    override fun clearTypeInferences() { typeInferences = null }

    override fun mapWithChildren(
        document: Document,
        mappedChildList: List<Tree>,
        copyInferences: Boolean,
        mapName: (n: TemperName) -> TemperName,
    ): ValueLeaf {
        val mapped = ValueLeaf(document, pos, content)
        if (copyInferences) {
            mapped.typeInferences = typeInferences
        }
        return mapped
    }
}

fun (Tree).toLispy(
    multiline: Boolean = false,
    includeTypeInfo: Boolean = false,
    decorate: (Tree) -> String? = { null },
): String = toStringViaBuilder { sb ->
    val lispification = Lispification(sb, decorate, includeTypeInfo = includeTypeInfo)
    appendLispy(
        this,
        depth = if (multiline) 0 else null,
        lispification = lispification,
    )
}

private fun indentTo(depth: Int?, out: Appendable) {
    if (depth != null) {
        repeat(depth) { out.append("  ") }
    }
}

private data class Lispification(
    val out: Appendable,
    val decorate: (Tree) -> String?,
    val includeTypeInfo: Boolean,
)

private fun appendLispy(
    tree: Tree,
    depth: Int?,
    lispification: Lispification,
) {
    val out = lispification.out
    indentTo(depth, out)
    val type = if (lispification.includeTypeInfo) {
        tree.typeInferences?.type
    } else {
        null
    }
    // Write out preface
    out.append('(')
    when (tree) {
        is InnerTree -> {
            out.append(tree.treeType.name)
        }
        is LeftNameLeaf -> {
            out.append("L ")
            out.append(tree.content.toString())
        }
        is RightNameLeaf -> {
            out.append("R ")
            out.append(tree.content.toString())
        }
        is StayLeaf -> {
            out.append('S')
        }
        is ValueLeaf -> {
            out.append("V ")
            tree.content.stringify(out)
        }
    }

    lispification.decorate(tree)?.let { decoration ->
        out.append(' ').append(decoration)
    }

    // Optionally write out type metadata
    if (type != null) {
        out.append(": ")
        if (type.mentionsInvalid) {
            out.append("⚠️")
        }
        out.append("$type")
    }
    // Write out content
    if (tree is BlockTree && tree.flow is StructuredFlow) {
        val separator = if (depth != null) '\n' else ' '
        out.append(separator)
        appendLispyControlFlow(
            controlFlow = (tree.flow as StructuredFlow).controlFlow,
            block = tree,
            depth = depth?.let { it + 1 },
            lispification = lispification,
        )
    } else {
        when (tree) {
            is InnerTree -> {
                val separator: Char
                val childDepth: Int?
                if (
                    depth != null &&
                    (tree.indices.any { tree.child(it) is InnerTree })
                ) {
                    separator = '\n'
                    childDepth = depth + 1
                } else {
                    separator = ' '
                    childDepth = null
                }
                val childIndices = if (tree is BlockTree) {
                    blockPartialEvaluationOrder(tree)
                } else {
                    tree.indices
                }
                for (i in childIndices) {
                    out.append(separator)
                    appendLispy(
                        tree.child(i),
                        depth = childDepth,
                        lispification = lispification,
                    )
                }
            }

            is LeafTree -> Unit
        }
        out.append(')')
    }
}

private fun appendLispyControlFlow(
    controlFlow: ControlFlow,
    block: BlockTree,
    depth: Int?,
    lispification: Lispification,
) {
    if (controlFlow is ControlFlow.Stmt) {
        // Allow tree lispifying to handle indentation and bracketing
        appendChildReference(controlFlow.ref, block, depth, lispification)
        return
    }

    val out = lispification.out
    val separator = if (depth == null) ' ' else '\n'
    val childDepth = depth?.let { it + 1 }
    var clauses = emptyList<ControlFlow>()
    indentTo(depth, out)
    out.append('(')
    when (controlFlow) {
        is ControlFlow.If -> {
            out.append("if ")
            appendChildReference(controlFlow.condition, block, null, lispification)
            clauses = controlFlow.clauses
        }
        is ControlFlow.Loop -> {
            when (controlFlow.checkPosition) {
                LeftOrRight.Left -> out.append("while")
                LeftOrRight.Right -> out.append("do-while")
            }
            val label = controlFlow.label
            if (label != null) {
                out.append('@')
                out.append("$label")
            }
            out.append(' ')
            appendChildReference(controlFlow.condition, block, null, lispification)
            clauses = listOf(controlFlow.body, controlFlow.increment)
        }
        is ControlFlow.Jump -> {
            out.append(
                when (controlFlow) {
                    is ControlFlow.Break -> "break"
                    is ControlFlow.Continue -> "continue"
                },
            )
            when (val target = controlFlow.target) {
                is DefaultJumpSpecifier -> {}
                is NamedJumpSpecifier -> out.append(" ${target.label}")
                is UnresolvedJumpSpecifier -> out.append(" ${target.symbol}")
            }
        }
        is ControlFlow.Labeled -> {
            out.append("labeled ${controlFlow.breakLabel}")
            val continueLabel = controlFlow.continueLabel
            if (continueLabel != null) {
                out.append("&${continueLabel}")
            }
            out.append(separator)
            appendLispyControlFlow(controlFlow.stmts, block, childDepth, lispification)
        }
        is ControlFlow.OrElse -> {
            out.append("orelse")
            clauses = controlFlow.clauses
        }
        is ControlFlow.StmtBlock -> {
            out.append("stmt-block")
            clauses = controlFlow.stmts
        }
        is ControlFlow.Stmt -> {} // handled above
    }
    for (clause in clauses) {
        out.append(separator)
        appendLispyControlFlow(clause, block, childDepth, lispification)
    }
    out.append(')')
}

private fun appendChildReference(
    ref: BlockChildReference,
    block: BlockTree,
    depth: Int?,
    lispification: Lispification,
) {
    val out = lispification.out
    val edge = block.dereference(ref)
    if (edge != null) {
        appendLispy(edge.target, depth = depth, lispification)
    } else {
        out.append("(broken-ref)")
    }
}

private class Edges(
    val tree: Tree,
) : AbstractList<TEdge>(), RandomAccess {
    override val size: Int get() = tree.size
    override fun get(index: Int): TEdge = tree.edge(index)

    override fun listIterator(index: Int): ListIterator<TEdge> =
        EdgeIterator(index)

    private inner class EdgeIterator(private var i: Int) : ListIterator<TEdge> {
        override fun hasNext(): Boolean = i < tree.size

        override fun next(): TEdge {
            if (i == tree.size) {
                throw NoSuchElementException()
            }
            val e = get(i)
            i += 1
            return e
        }

        override fun hasPrevious(): Boolean = i in 1 until size

        override fun nextIndex(): Int = i

        override fun previous(): TEdge {
            val ip = i - 1
            val e = get(ip)
            i = ip
            return e
        }

        override fun previousIndex(): Int = i - 1
    }
}

private class Children(
    val tree: Tree,
) : AbstractList<Tree>(), RandomAccess {
    override val size: Int get() = tree.size
    override fun get(index: Int): Tree = tree.child(index)

    override fun listIterator(index: Int): ListIterator<Tree> =
        ChildIterator(index)

    private inner class ChildIterator(private var i: Int) : ListIterator<Tree> {
        override fun hasNext(): Boolean = i < tree.size

        override fun next(): Tree {
            if (i == tree.size) {
                throw NoSuchElementException()
            }
            val e = get(i)
            i += 1
            return e
        }

        override fun hasPrevious(): Boolean = i in 1 until size + 1

        override fun nextIndex(): Int = i

        override fun previous(): Tree {
            val ip = i - 1
            val e = get(ip)
            i = ip
            return e
        }

        override fun previousIndex(): Int = i - 1
    }
}

private fun destructureControlFlow(
    controlFlow: ControlFlow,
    block: BlockTree,
    structureSink: StructureSink,
) {
    val condition = when (controlFlow) {
        is ControlFlow.Conditional -> controlFlow.condition
        else -> null
    }
    val label: String? = when (controlFlow) {
        is JumpDestination -> controlFlow.breakLabel?.toString()
        is ControlFlow.Jump -> when (val target = controlFlow.target) {
            is DefaultJumpSpecifier -> null
            is NamedJumpSpecifier -> "${target.label}"
            is UnresolvedJumpSpecifier -> "${target.symbol}"
        }
        else -> null
    }
    val kind = when (controlFlow) {
        is ControlFlow.If -> "if"
        is ControlFlow.Loop -> when (controlFlow.checkPosition) {
            LeftOrRight.Left -> "while"
            LeftOrRight.Right -> "do-while"
        }
        is ControlFlow.Break -> "break"
        is ControlFlow.Continue -> "continue"
        is ControlFlow.Labeled -> "labeled"
        is ControlFlow.OrElse -> "orelse"
        is ControlFlow.Stmt -> {
            val edge = block.dereference(controlFlow.ref)
            if (edge != null) {
                edge.target.destructure(structureSink)
                return
            }
            "broken-stmt"
        }
        is ControlFlow.StmtBlock -> "stmt-block"
    }
    val clauses = controlFlow.clauses.toList()
    structureSink.obj {
        key("kind", Hints.n) { value(kind) }
        key("label", if (label == null) Hints.nu else Hints.n) {
            value(label)
        }
        if (condition != null) {
            key("condition", Hints.n) {
                val edge = block.dereference(condition)
                if (edge != null) {
                    value(edge.target)
                }
            }
        }
        key("clauses", Hints.n) {
            arr {
                clauses.forEach {
                    destructureControlFlow(it, block, this@arr)
                }
            }
        }
    }
}

fun (Tree).spanningPosition(startIndex: Int, endIndex: Int): Position =
    if (startIndex >= endIndex) {
        when {
            startIndex < this.size -> this.child(startIndex).pos.leftEdge
            startIndex != 0 -> this.pos.rightEdge
            else -> this.pos.leftEdge
        }
    } else {
        val loc = child(startIndex).pos.loc
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        for (i in startIndex until endIndex) {
            val p = child(i).pos
            if (p.loc == loc) {
                left = min(p.left, left)
                right = max(p.right, right)
            }
        }
        Position(loc, left, right)
    }

/**
 * The number of children in a [CallTree] representing a binary operation
 * which have one callee and two operands.
 */
const val BINARY_OP_CALL_ARG_COUNT = 3
const val UNARY_OP_CALL_ARG_COUNT = 2

// These help avoid copying trees to preserve the 1-incoming edge invariant.
// Copying trees blows away Edge breadcrumbs.

/**
 * If the tree has a parent, replace it's edge's target with a placeholder so that tree can be
 * reused when constructing a replacement.
 *
 * @return [tree]
 */
fun <TREE : Tree> freeTree(tree: TREE): TREE =
    when (val edge = tree.incoming) {
        null -> tree
        else -> {
            freeTarget(edge)
            tree
        }
    }

/**
 * Replace the edge's target with a placeholder so that its target can be reused when constructing
 * a replacement.
 *
 * @return [edge]'s target prior to this call.
 */
fun freeTarget(edge: TEdge): Tree {
    val target = edge.target
    val nop = BlockTree(target.document, target.pos, emptyList(), LinearFlow)
    edge.replace(nop)
    return target
}
