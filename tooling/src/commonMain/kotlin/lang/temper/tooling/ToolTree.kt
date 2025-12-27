package lang.temper.tooling

import lang.temper.be.Backend
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.compatRemoveLast
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.subListToEnd
import lang.temper.cst.CstComment
import lang.temper.frontend.ModuleNamingContext
import lang.temper.lexer.CommentType
import lang.temper.library.LibraryConfigurationLocationKey
import lang.temper.log.Position
import lang.temper.log.unknownPos
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ModuleName
import lang.temper.name.SourceName
import lang.temper.type.TypeActual
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.InnerTreeType
import lang.temper.value.LeafTree
import lang.temper.value.LeafTreeType
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.TFunction
import lang.temper.value.TSymbol
import lang.temper.value.Tree
import lang.temper.value.TreeType
import lang.temper.value.importedSymbol
import lang.temper.value.methodSymbol
import lang.temper.value.propertySymbol
import lang.temper.value.staticPropertySymbol
import lang.temper.value.staticTypeContained
import lang.temper.value.typeDeclSymbol
import lang.temper.value.valueContained
import lang.temper.value.varSymbol
import kotlin.math.max
import kotlin.math.min

/** Simplified immutable tree for tooling purposes, using a single data class for convenience in immutability. */
data class ToolTree(
    // Treat all as named-only args.
    /** Used so far just for the callee of [InnerTreeType.Call] nodes but might be usable for other things. */
    val focus: ToolTree? = null,
    /** Child nodes if any. */
    val kids: List<ToolTree> = emptyList(),
    /** The kind of tree node. */
    val kind: TreeKind,
    /** For official names like uniquely renamed identifiers or built-ins that retain names. */
    val name: String? = null,
    /** Source range. */
    val pos: Position,
    /** Ideally represents original text such as variable base names, though this is sometimes lost in parsing. */
    val text: String? = null,
    /** The static type of this node. */
    val type: TypeActual? = null,
    /** Used for some value leaves. */
    val value: Any? = null,
) : Structured {
    init {
        when (kind) {
            TreeKind.Decl -> check(value == null || value is DeclMeta)
            else -> Unit
        }
    }

    fun asDef(): Def? = when {
        isDef -> Def(this)
        else -> null
    }

    fun asRef(): Ref? = when {
        isRef -> Ref(this)
        else -> null
    }

    fun asSym(): Sym? = when {
        isSym -> Sym(this)
        else -> null
    }

    fun asMention(): Mention? = when {
        isDef -> Def(this)
        isRef -> Ref(this)
        isSym -> Sym(this)
        else -> null
    }

    val declMeta get() = value as? DeclMeta

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("kind") { value(kind.name) }
        key("pos") {
            arr {
                value(pos.left)
                value(pos.right)
            }
        }
        if (name != null) {
            key("name") { value(name) }
        }
        if (text != null && name != text) {
            key("text") { value(text) }
        }
        if (type != null) {
            key("type") { value(type) }
        }
        if (kids.isNotEmpty()) {
            if (focus != null && kids[0] !== focus) {
                key("focus") { value(kids.indexOf(focus)) }
            }
            key("kids") {
                arr {
                    for (kid in kids) {
                        value(kid)
                    }
                }
            }
        }
        if (value != null) {
            key("value") { value(value) }
        }
    }

    fun findContainer(offset: Int) = findContainer(offset = offset, trees = kids)

    val isClass
        get() = when (kind) {
            // The `class` parent starts out a Call but gets transformed to Decl in later stage processing here.
            TreeKind.Call, TreeKind.Decl -> focus?.name == "class"
            else -> false
        }

    val isComment get() = kind.isComment

    val isCommentHead get() = kind.isCommentHead

    val isCommentTail get() = kind.isCommentTail

    fun isComplexArgName(parent: ToolTree): Boolean {
        val index = parent.kids.indexOf(this)
        for (kid in parent.kids.subListToEnd(index + 1)) {
            when {
                kid.isSym && kid.name == "default" -> return true
                kid.isComment -> {}
                else -> return false
            }
        }
        return false
    }

    val isDef get() = kind == TreeKind.LeftName

    val isDot get() = kind == TreeKind.Call && focus?.name == "."

    // Currently expecting Esc of ("`(Leaf`" "builtins" "`Leaf)`" ".") here.
    val isEmptyBuiltin get() = kind == TreeKind.Esc && kids.getOrNull(1)?.value == "builtins"

    val isMention get() = kind.isLeaf && name != null

    val isMutant get() = text != null && pos.size != text.length

    val isRef get() = kind == TreeKind.RightName

    val isSym get() = kind == TreeKind.Value && name != null

    val isText get() = name == null && (isComment || kind == TreeKind.Value)

    val isThis get() = value == RefKind.This

    fun recurse(): Sequence<ToolTree> = sequence {
        yield(this@ToolTree)
        for (kid in kids) {
            yieldAll(kid.recurse())
        }
    }

    fun recurseWithParents(parent: ToolTree? = null): Sequence<ParentPair> = sequence {
        yield(ParentPair(parent = parent, tree = this@ToolTree))
        for (kid in kids) {
            yieldAll(kid.recurseWithParents(parent = this@ToolTree))
        }
    }

    fun toJson() = FormattingStructureSink.toJsonString(this)

    companion object {
        fun def(name: String, pos: Position, text: String) =
            ToolTree(kind = TreeKind.LeftName, name = name, pos = pos, text = text)

        fun ref(name: String, pos: Position, text: String, value: RefKind? = null) =
            ToolTree(kind = TreeKind.RightName, name = name, pos = pos, text = text, value = value)

        fun sym(name: String, pos: Position) = ToolTree(kind = TreeKind.Value, name = name, pos = pos, text = name)
    }
}

/** Where `null` is the default. */
enum class RefKind {
    This,
}

val EmptyToolTree = ToolTree(kind = TreeKind.None, pos = unknownPos)

class ParentPair(val parent: ToolTree?, val tree: ToolTree)

sealed interface Mention {
    val name get() = tree.name!!
    val pos get() = tree.pos
    val text get() = tree.text!!
    val tree: ToolTree

    companion object {
        fun def(name: String, pos: Position, text: String) = Def(ToolTree.def(name = name, pos = pos, text = text))
        fun ref(name: String, pos: Position, text: String) = Ref(ToolTree.ref(name = name, pos = pos, text = text))
        fun sym(name: String, pos: Position) = Sym(ToolTree.sym(name = name, pos = pos))
    }
}

@JvmInline
value class Def(override val tree: ToolTree) : Mention

@JvmInline
value class Ref(override val tree: ToolTree) : Mention

@JvmInline
value class Sym(override val tree: ToolTree) : Mention

enum class TreeKind(val isLeaf: Boolean) {
    // Placeholder for meaningless tree.
    None(true),

    // Matching InnerTreeType.
    Block(false),
    Call(false),
    Decl(false),
    Esc(false),
    Fun(false),

    // Matching LeafTreeType.
    LeftName(true),
    RightName(true),
    Stay(true),
    Value(true),

    // Custom comment types.
    // For now, these just focus on when the edges are well defined relative to the pos range.
    CommentAll(true),
    CommentHead(true),
    CommentInner(true),
    CommentTail(true),
}

val TreeKind.isComment get() = when (this) {
    TreeKind.CommentAll, TreeKind.CommentHead, TreeKind.CommentInner, TreeKind.CommentTail -> true
    else -> false
}

val TreeKind.isCommentHead get() = when (this) {
    TreeKind.CommentAll, TreeKind.CommentHead -> true
    else -> false
}

val TreeKind.isCommentTail get() = when (this) {
    TreeKind.CommentAll, TreeKind.CommentTail -> true
    else -> false
}

fun TreeType.toKind() = when (this) {
    InnerTreeType.Block -> TreeKind.Block
    InnerTreeType.Call -> TreeKind.Call
    InnerTreeType.Decl -> TreeKind.Decl
    InnerTreeType.Esc -> TreeKind.Esc
    InnerTreeType.Fun -> TreeKind.Fun
    LeafTreeType.LeftName -> TreeKind.LeftName
    LeafTreeType.RightName -> TreeKind.RightName
    LeafTreeType.Stay -> TreeKind.Stay
    LeafTreeType.Value -> TreeKind.Value
}

fun convertTree(tree: Tree): ToolTree {
    return when (tree) {
        is LeafTree -> convertLeaf(tree)
        else -> {
            var kids = tree.children.map { convertTree(it) }
            val focus = when (tree) {
                // Asserts calls have at least one kid, which I like to hope is always true.
                is CallTree -> {
                    val first = kids[0]
                    if (first.isThis && kids.size == 1 && tree.pos == first.pos) {
                        // Unnest `this` calls.
                        return first
                    }
                    first
                }
                else -> null
            }
            // Sort kids after focus, and extend parent range as needed.
            kids = kids.sortedBy { it.pos.left }
            val left = kids.firstOrNull()?.let { min(it.pos.left, tree.pos.left) } ?: tree.pos.left
            val right = kids.maxOfOrNull { it.pos.right }?.let { max(it, tree.pos.right) } ?: tree.pos.right
            val pos = when (left == tree.pos.left && right == tree.pos.right) {
                true -> tree.pos
                false -> Position(tree.pos.loc, left, right)
            }
            ToolTree(kind = tree.treeType.toKind(), pos = pos, focus = focus, kids = kids)
        }
    }
}

fun convertLeaf(tree: LeafTree) = when (tree) {
    is NameLeaf -> {
        // The text ideally is what is seen in source.
        val text = when (val nameNode = tree.content) {
            is BuiltinName -> nameNode.builtinKey
            is SourceName -> nameNode.baseName.nameText
            else -> when (val symbol = nameNode.toSymbol()) {
                null -> nameNode.rawDiagnostic
                else -> symbol.text
            }
        }
        // And the name ideally is something more unique as needed.
        when (tree) {
            is LeftNameLeaf -> ToolTree.def(text = text, name = tree.content.rawDiagnostic, pos = tree.pos)
            is RightNameLeaf -> ToolTree.ref(text = text, name = tree.content.rawDiagnostic, pos = tree.pos)
        }
    }
    is StayLeaf -> ToolTree(kind = tree.treeType.toKind(), pos = tree.pos)
    else -> {
        val value = tree.valueContained
        when (value?.typeTag) {
            TSymbol -> ToolTree.sym(name = TSymbol.unpack(value).text, pos = tree.pos)
            TFunction -> {
                val fn = TFunction.unpack(value)
                if (fn == BuiltinFuns.thisPlaceholder) {
                    val name = (fn as NamedBuiltinFun).name
                    ToolTree.ref(text = name, name = name, pos = tree.pos, value = RefKind.This)
                } else {
                    null
                }
            }
            else -> null
        } ?: ToolTree(kind = tree.treeType.toKind(), pos = tree.pos, value = value?.stateVector)
    }
}

/**
 * The second param for [block] is a list of parent values that you need to copy if you want to
 * keep.
 */
fun <V> treeToPosMap(tree: Tree, block: (Tree, List<V>) -> V): Map<Position, List<V>> {
    return mutableMapOf<Position, MutableList<V>>().also {
        treeToPosMap(tree, map = it, parentValues = mutableListOf(), block = block)
    }
}

private fun <V> treeToPosMap(
    tree: Tree,
    map: MutableMap<Position, MutableList<V>>,
    parentValues: MutableList<V>,
    block: (Tree, List<V>) -> V,
) {
    val value = block(tree, parentValues)
    parentValues.add(value)
    try {
        map.computeIfAbsent(tree.pos) { mutableListOf() }.add(value)
        for (kid in tree.children) {
            treeToPosMap(kid, map = map, parentValues = parentValues, block = block)
        }
    } finally {
        parentValues.compatRemoveLast()
    }
}

/** Weaves in comments as value nodes.  */
fun weaveComments(tree: ToolTree, comments: List<CstComment>) = when {
    comments.isEmpty() -> tree
    else -> weaveCommentsWith(tree = tree, comments = comments, commentIndex = intArrayOf(0), top = true)
}

/** Here, [commentIndex] is mutable for efficient walking. */
private fun weaveCommentsWith(
    tree: ToolTree,
    comments: List<CstComment>,
    commentIndex: IntArray,
    top: Boolean,
): ToolTree {
    if (commentIndex[0] >= comments.size || (comments[commentIndex[0]].pos.right > tree.pos.right && !top)) {
        // The first comment is past this node, so just move on.
        return tree
    }
    val kids = mutableListOf<ToolTree>()
    fun addComment() {
        val comment = comments[commentIndex[0]]
        // Comment kind matters for interpreting the right edge.
        val kind = commentKind(comment, tree, top)
        kids.add(ToolTree(kind = kind, pos = comment.pos))
        // Increment our index for every add. Each comment is added once.
        commentIndex[0] += 1
    }
    // Add kids with whatever comments come before each.
    var focus = tree.focus
    tree.kids.forEach { kid ->
        while (comments.getOrNull(commentIndex[0])?.let { it.pos.left < kid.pos.left } == true) {
            addComment()
        }
        kids.add(weaveCommentsWith(tree = kid, comments = comments, commentIndex = commentIndex, top = false))
        if (kid == focus) {
            // Pointer identity is used for focus, which might or might not have changed.
            focus = kids.last()
        }
    }
    // Add any straggling comment within the current parent tree node.
    while (comments.getOrNull(commentIndex[0])?.let { top || it.pos.right <= tree.pos.right } == true) {
        addComment()
    }
    val pos = when {
        // Extend the bounds of the top block to include leading and trailing comments.
        top -> tree.pos.copy(
            left = min(tree.pos.left, comments.first().pos.left),
            right = max(tree.pos.right, comments.last().pos.right),
        )
        else -> tree.pos
    }
    return tree.copy(focus = focus, kids = kids.toList(), pos = pos)
}

private fun commentKind(comment: CstComment, tree: ToolTree, top: Boolean) = when (comment.type) {
    CommentType.Block -> TreeKind.CommentInner
    CommentType.Line -> TreeKind.CommentTail
    else -> when {
        top -> when {
            comment.pos.right >= tree.pos.right -> when (comment.pos.left) {
                0 -> TreeKind.CommentAll
                else -> TreeKind.CommentTail
            }
            comment.pos.left == 0 -> TreeKind.CommentHead
            else -> TreeKind.CommentInner
        }
        else -> TreeKind.CommentInner
    }
}

/** Upgrade nodes to decls and/or more authoritative names. */
fun correlateDecls(tree: ToolTree, infoMap: Map<Position, List<ToolTree>>): ToolTree {
    // First handle manual things that don't get automatically aligned.
    val info = when (tree.kind) {
        TreeKind.Call -> when {
            // Turn class call into class decl.
            // Class declaration don't wrap the whole class right now, but rather are extracted to a preceding sibling
            // node that covers only the range of the class name, so we use custom logic here to convert to a decl.
            tree.focus!!.name == "class" && tree.kids.any {
                it.isDef
                // More formally, we might should verify a matching decl, but this is slower and likely redundant.
                // TODO(tjp, tooling): Extract info from decl?
                // && infoMap[kid.pos]?.any { info -> info.kind == InnerTreeType.Decl } == true
            } -> ToolTree(kind = TreeKind.Decl, pos = tree.pos)
            else -> null
        }
        else -> null
    } ?: infoMap[tree.pos]?.let infos@{ infos ->
        // Nothing manual, so see what alignment we have.
        for (info in infos) {
            when (info.kind) {
                TreeKind.Decl -> when (tree.kind) {
                    TreeKind.Block, TreeKind.Call, TreeKind.Decl -> return@infos info
                    else -> Unit
                }
                TreeKind.LeftName, TreeKind.RightName ->
                    if ((tree.kind != TreeKind.LeftName || tree.name != info.name) && tree.text == info.text) {
                        // This applies to lots of cases.
                        // For properties, we'll get both property and constructor args atop former RightNames.
                        // Right now, property comes first. Just keep first for now. Get more formal later if needed.
                        // And just keep the whole new name leaf with whatever bonus like the name string.
                        return@correlateDecls info
                    }
                else -> Unit
            }
        }
        null
    } ?: tree
    val kids = tree.kids.map { correlateDecls(tree = it, infoMap = infoMap) }
    val focus = findNewFocus(tree = tree, newKids = kids)
    return tree.copy(kind = info.kind, focus = focus, kids = kids, value = info.value)
}

data class DeclMeta(val imported: Imported?, val isVar: Boolean, val memberKind: DeclKind)

data class Imported(val path: String, val symbol: String)

fun extractDecls(tree: Tree) = treeToPosMap<ToolTree>(tree) { node, parentValues ->
    when (node) {
        is NameLeaf -> {
            var leaf = convertLeaf(node)
            if (leaf.isDef) {
                when (val parentValue = parentValues.lastOrNull()?.value) {
                    is DeclMeta -> {
                        check(leaf.value == null)
                        // In the case of multi-assignment, we need the info down on the single var.
                        // The problem is that we can have multiple decl trees with the same pos range.
                        // This gets around that.
                        leaf = leaf.copy(value = parentValue)
                    }
                    else -> {}
                }
            }
            leaf
        }
        else -> {
            val value = when (node) {
                is DeclTree -> node.parts?.let { parts ->
                    val meta = parts.metadataSymbolMap
                    val imported = meta[importedSymbol]?.let findImported@{ value ->
                        val exportedName = value.target.child(0).content as ExportedName
                        val exporter = (exportedName.origin as? ModuleNamingContext)?.owner
                        val moduleName = exporter?.loc as? ModuleName
                            ?: return@findImported null
                        exporter.sharedLocationContext[moduleName, LibraryConfigurationLocationKey]
                            ?.let { libraryConfiguration ->
                                val exporterPath = Backend.defaultFilePathForSource(
                                    libraryConfiguration,
                                    moduleName,
                                    outputFileExtension = "",
                                )
                                Imported(path = "$exporterPath", symbol = exportedName.baseName.nameText)
                            }
                    }
                    val memberKind = when {
                        methodSymbol in meta -> DeclKind.Method
                        propertySymbol in meta -> DeclKind.Property
                        // TODO(mikesamuel): is this ok?
                        staticPropertySymbol in meta -> DeclKind.Property
                        else -> DeclKind.Other
                    }
                    DeclMeta(imported = imported, isVar = varSymbol in meta, memberKind = memberKind)
                }
                else -> null
            }
            ToolTree(kind = node.treeType.toKind(), pos = node.pos, value = value)
        }
    }
}

data class TypeInfo(
    /** The kind of tree node. */
    val treeType: TreeType,
    /** The expression type of this node. */
    val type: TypeActual?,
    /** The type being declared. */
    val typeDecl: TypeActual? = null,
)

/** Upgrade nodes with type information. */
fun correlateTypes(tree: ToolTree, infoMap: Map<Position, List<TypeInfo>>): ToolTree {
    var type: TypeActual? = null
    var value = tree.value
    when (tree.kind) {
        // Just grab specific tree types for now. After all stages, conflicting types overlay the same source range.
        // Because of inlining and macros, tree types can change, so we might need to carefully review what makes sense.
        TreeKind.Call, TreeKind.RightName -> {
            type = infoMap[tree.pos]?.let { typeInfos ->
                typeInfos.find { it.treeType.toKind() == tree.kind }?.type
            }
        }
        TreeKind.LeftName -> {
            infoMap[tree.pos]?.let type@{ typeInfos ->
                for (typeInfo in typeInfos) {
                    when (typeInfo.treeType) {
                        InnerTreeType.Decl -> if (typeInfo.typeDecl != null) {
                            // Type for a type declaration itself.
                            value = typeInfo.typeDecl
                            break
                        }
                        LeafTreeType.LeftName -> if (typeInfo.type != null) {
                            // Type of a declared variable.
                            type = typeInfo.type
                            break
                        }
                        else -> Unit
                    }
                }
            }
        }
        else -> Unit
    }
    val kids = tree.kids.map { correlateTypes(tree = it, infoMap = infoMap) }
    val focus = findNewFocus(tree = tree, newKids = kids)
    return tree.copy(kids = kids, focus = focus, type = type, value = value)
}

fun extractTypes(tree: Tree) = treeToPosMap<TypeInfo>(tree) { node, _ ->
    val typeDecl = when (node) {
        is DeclTree -> {
            node.parts?.metadataSymbolMap?.get(typeDeclSymbol)?.target?.staticTypeContained
        }
        else -> null
    }
    TypeInfo(
        treeType = node.treeType,
        type = node.typeInferences?.type,
        typeDecl = typeDecl,
    )
}
