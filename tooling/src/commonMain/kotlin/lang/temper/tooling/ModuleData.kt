package lang.temper.tooling

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import lang.temper.common.mapFirst
import lang.temper.common.subListToEnd
import lang.temper.common.toStringViaTextOutput
import lang.temper.format.TextOutputTokenSink
import lang.temper.format.WrappedTokenSink
import lang.temper.frontend.ModuleSource
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.interp.EmptyEnvironment
import lang.temper.interp.builtinOnlyEnvironment
import lang.temper.interp.importExport.Export
import lang.temper.lexer.Genre
import lang.temper.lexer.IdParts
import lang.temper.log.FilePath
import lang.temper.log.FilePosition
import lang.temper.log.FilePositions
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.LogEntry
import lang.temper.log.UnknownCodeLocation
import lang.temper.log.unknownPos
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.NameOutputToken
import lang.temper.type.FunctionType
import lang.temper.type.MethodKind
import lang.temper.type.NominalType
import lang.temper.type.TypeActual
import lang.temper.type.TypeShape
import lang.temper.value.TemperFormattingHints
import kotlin.math.min

data class LocPos(
    val loc: FilePath,
    val pos: FilePosition,
)

// TODO(tjp, tooling): ReadOnly interface version?

/** Module metadata organized for tooling. */
class ModuleData {
    // TODO(tjp, tooling): Remove defs, and get to them from decls?
    private val decls = mutableMapOf<String, ToolTree>()
    private val defs = mutableMapOf<String, Def>()

    var exports = emptyList<Export>()

    val finished get() = finishedFlow.value
    private val finishedFlow = MutableStateFlow(false)

    /** Mutable list because it's appended to during compilation. */
    val logEntries = mutableListOf<LogEntry>()

    var filePositionsMap: Map<FilePath, FilePositions> = mapOf()
        private set

    var trees: List<ToolTree> = emptyList()
        private set

    var treeMap: Map<FilePath, ToolTree> = emptyMap()
        private set

    private val typeDecls: MutableMap<String, ToolType> = mutableMapOf()

    suspend fun awaitFinish() = finishedFlow.first { it }

    fun defByName(name: String) = defs[name]

    fun findCompletions(pos: LocPos): List<Def> {
        // TODO Start with a trie for all potential imports?
        val offset = toOffset(pos) ?: return emptyList()
        val defs = mutableMapOf<String, Def>()
        val tree = treeMap[pos.loc] ?: return emptyList()
        // We currently don't need to cache this info for repeated queries while typing, since the client does that.
        // Gather tops only from other trees.
        for ((path, otherTree) in treeMap.entries) {
            if (path != pos.loc) {
                // Use offset -1 to avoid matching any defs in the file.
                gatherDefsTop(offset = -1, tree = otherTree, defs = defs)
            }
        }
        // Now also go into the matching tree but finding anything at the offset this time.
        return when (val mentionContext = gatherDefsTop(offset = offset, tree = tree, defs = defs)) {
            null -> defs.values.sortCompletions()
            is DefMentionContext, is TextMentionContext -> emptyList()
            // TODO (New)CallMentionContext named args can be completed, using constructor for `new` case.
            is CallMentionContext -> emptyList()
            is NewCallMentionContext -> emptyList()
            is MemberMentionContext, is RefMentionContext -> {
                val prefix = when (val mention = mentionContext.mention) {
                    null -> ""
                    else -> when (mentionContext.parent?.isEmptyBuiltin) {
                        true -> {
                            // This is a rare case, so be inefficiently convenient.
                            defs.clear()
                            gatherBuiltins(defs = defs)
                            ""
                        }
                        else -> {
                            val endIndex = min(offset - mention.pos.left, mention.text.length)
                            mention.text.slice(0 until endIndex)
                        }
                    }
                }
                when (mentionContext) {
                    is MemberMentionContext -> completeMembers(call = mentionContext.parent, prefix = prefix)
                    else -> defs.values.filter { it.text.startsWith(prefix) }.sortCompletions()
                }
            }
        }
    }

    private fun findLookup(pos: LocPos): Lookup? {
        return when (val mentionContext = findMention(pos = pos)) {
            is MemberMentionContext -> {
                mentionContext.mention?.let { mention ->
                    makeMemberLookup(mention, mentionContext.parent)
                }
            }
            // TODO(tjp, tooling): If ref is def, find other refs instead.
            is DirectMentionContext -> when (mentionContext.mention.tree.kind) {
                TreeKind.LeftName -> when (val typeActual = mentionContext.boxType) {
                    null -> DirectLookup(mention = mentionContext.mention, name = mentionContext.mention.name)
                    else -> makeMemberLookup(mention = mentionContext.mention, typeActual = typeActual)
                }
                TreeKind.RightName -> when (mentionContext.mention.tree.value) {
                    RefKind.This -> mentionContext.mention.tree.type?.let { typeActual ->
                        val typeName = findTypeName(typeActual)
                        val mention = mentionContext.mention
                        typeDecls[typeName]?.let { ThisLookup(mention = mention, name = mention.text, type = it) }
                    }
                    else -> DirectLookup(mention = mentionContext.mention, name = mentionContext.mention.name)
                }
                // Here `else` must refer to a symbol (with LeafTreeType.Value).
                else -> null
            }
            is CallMentionContext ->
                makeNamedArgLookup(mention = mentionContext.mention, callee = mentionContext.parent)
            is NewCallMentionContext ->
                makeNewCallArgLookup(mention = mentionContext.mention, typeActual = mentionContext.boxType)
            else -> null
        }
    }

    fun findDecl(pos: LocPos): DeclInfo? {
        return when (val lookup = findLookup(pos = pos)) {
            is DirectLookup -> {
                val decl = decls[lookup.name]
                val def = defs[lookup.name] ?: return null
                // Sometimes expressions are inlined instead of types, but we can still sometimes find the def type.
                val typeActual = lookup.mention.tree.type ?: def.tree.type
                val type = typeActual?.let { makePrettyTypeName(it) }
                val kind = when (def.tree.value) {
                    // TODO(tjp, tooling): Distinguish kinds of types?
                    is TypeActual -> when (typeActual) {
                        is FunctionType -> DeclKind.Constructor
                        else -> DeclKind.Class
                    }
                    else -> when (val meta = decl?.declMeta) {
                        null -> DeclKind.Other
                        else -> when {
                            meta.isVar -> DeclKind.Var
                            decl.kids.any { it.isSym && it.name == "_complexArg_" } -> DeclKind.Parameter
                            else -> DeclKind.Let
                        }
                    }
                }
                DeclInfo(kind = kind, name = def.text, type = type)
            }
            is MemberLookup -> {
                val box = lookup.type.type?.let { makePrettyTypeName(it) }
                val def = lookup.type.members[lookup.name] ?: return null
                val declKind = decls[def.name]?.declMeta?.memberKind ?: DeclKind.Other
                val type = def.tree.type?.let { makePrettyTypeName(it) }
                DeclInfo(kind = declKind, box = box, name = def.text, type = type)
            }
            is ThisLookup -> {
                val type = lookup.mention.tree.type?.let { makePrettyTypeName(it) }
                DeclInfo(kind = DeclKind.Parameter, name = lookup.name, type = type)
            }
            else -> null
        }
    }

    fun findDef(pos: LocPos) = when (val lookup = findLookup(pos)) {
        is DirectLookup -> defs[lookup.name]
        is MemberLookup -> lookup.type.members[lookup.name]
        is ThisLookup -> defs[lookup.type.defName]
        else -> null
    }

    fun findDefPos(pos: LocPos) = findDef(pos)?.let { mentionPos(it) }

    private fun findMention(pos: LocPos): MentionContext? = toOffset(pos)?.let {
        findMention(offset = it, tree = treeMap.getValue(pos.loc))
    }

    fun finish() {
        // Reset defs.
        decls.clear()
        defs.clear()
        for (tree in trees) {
            // TODO(tjp, tooling): Better recurse. Parent isn't high enough for `call @ \var (decl thing init ...)`.
            for (pair in tree.recurseWithParents()) {
                // Let the first win if multiple. TODO(tjp, tooling): Should there ever be multiple?
                pair.tree.asDef()?.let { def ->
                    defs.putIfMissing(def.name, def)
                    if (pair.parent?.kind == TreeKind.Decl) {
                        decls.putIfMissing(def.name, pair.parent)
                    }
                }
            }
        }
        // Reset type decls after we have decls tracked.
        typeDecls.clear()
        for (tree in trees) {
            for (pair in tree.recurseWithParents()) {
                if (pair.tree.isDef && pair.parent?.kind == TreeKind.Decl && pair.tree.value is TypeActual) {
                    findTypeName(pair.tree.value)?.let { typeName ->
                        // TODO(tjp, tooling): Assert previously missing in typeDecls?
                        typeDecls[typeName] = ToolType.fromTree(pair.parent)
                    }
                }
            }
        }
        // Mark.
        finishedFlow.value = true
    }

    private fun makeMemberLookup(mention: Mention, typeActual: TypeActual): MemberLookup? {
        val typeName = findTypeName(typeActual)
        return typeDecls[typeName]?.let { type ->
            MemberLookup(mention = mention, name = mention.text, type = type)
        }
    }

    private fun makeMemberLookup(mention: Mention, dotCall: ToolTree): MemberLookup? {
        return dotCall.kids.getOrNull(0)?.type?.let { typeActual ->
            makeMemberLookup(mention = mention, typeActual = typeActual)
        }
    }

    private fun makeNamedArgLookup(mention: Mention, callee: ToolTree): Lookup? {
        val calleeName = if (callee.isDot) {
            // Need the member to know the parameters.
            val memberContext = MemberMentionContext.fromDot(callee) ?: return null
            val memberLookup = makeMemberLookup(mention = memberContext.mention!!, dotCall = callee) ?: return null
            memberLookup.type.members[memberLookup.name]?.name
        } else {
            callee.name
        } ?: return null
        return makeNamedArgLookup(mention = mention, calleeName = calleeName)
    }

    private fun makeNamedArgLookup(mention: Mention, calleeName: String?): DirectLookup? {
        // Dig into the function definition for the arg name.
        val decl = decls[calleeName] ?: return null
        val argName = decl.kids.asSequence().filter { it.kind == TreeKind.Decl }.mapNotNull { arg ->
            arg.kids.find { it.kind == TreeKind.LeftName }
        }.find { it.text == mention.name }?.name ?: return null
        return DirectLookup(mention = mention, name = argName)
    }

    private fun makeNewCallArgLookup(mention: Mention, typeActual: TypeActual): Lookup? {
        val methods = ((typeActual as? NominalType)?.definition as? TypeShape)?.methods ?: emptyList()
        val constructors = methods.filter { it.methodKind == MethodKind.Constructor }
        // TODO Once we're overloading, we'll need someone to tell us which constructor to use.
        return when (constructors.size == 1) {
            true -> {
                // We use rawDiagnostic in the langserver for suffixed unique ids.
                val constructorName = constructors[0].name.rawDiagnostic
                // If there's a matching constructor param, run with it. Otherwise, this is null and we default below.
                // This also nulls on implied constructor, where we definitely want to look directly at props instead.
                makeNamedArgLookup(mention = mention, calleeName = constructorName)
            }
            else -> null
            // Default to finding a property if no matching constructor arg.
        } ?: makeMemberLookup(mention, typeActual)
    }

    /** Only works if the mention's loc is for this module. */
    fun mentionPos(mention: Mention) = (mention.pos.loc as FileRelatedCodeLocation).sourceFile.let { loc ->
        LocPos(
            // TODO(tjp, tooling): Verify that loc matches?
            loc = loc,
            pos = filePositionsMap[loc]!!.filePositionAtOffset(mention.pos.left),
        )
    }

    fun resetTrees(trees: List<ToolTree>) {
        this.trees = trees
        treeMap = trees.associateBy { (it.pos.loc as FileRelatedCodeLocation).sourceFile }
        finishedFlow.value = false
    }

    fun setSources(sources: List<ModuleSource>) {
        filePositionsMap = sources.associate { it.filePath!! to it.filePositions!! }
    }

    private fun toOffset(pos: LocPos) = this.filePositionsMap[pos.loc]?.offsetAtFilePosition(pos.pos)
}

private fun Iterable<Def>.sortCompletions() = sortedWith { a, b ->
    fun locVal(def: Def) = when (def.pos.loc) {
        // Put builtins after local scope.
        ImplicitsCodeLocation, UnknownCodeLocation -> 1
        else -> 0
    }
    when (val locDiff = locVal(a) - locVal(b)) {
        0 -> a.text.compareTo(b.text)
        else -> locDiff
    }
}

/** Info that can be formatted for display to users. */
data class DeclInfo(
    /** Box here is the class or whatever that a name is used from, sort of like a namespace. */
    val box: String? = null,
    val kind: DeclKind,
    val name: String,
    val type: String? = null,
)

enum class DeclKind {
    Class,
    Constructor,
    Let,
    Other,
    Method,
    Parameter,
    Property,
    Var,
}

data class ToolType(
    val defName: String?,
    val members: Map<String, Def>,
    val type: TypeActual?,
    // Potentially useful values that aren't currently used. Examples exist below on how to fill such things in.
    // Consider also uncommenting some if it helps for context when debugging.
    // val tree: ToolTree,
    // val typeName: String?,
    // val typeNameText: String,
) {
    companion object {
        fun fromTree(tree: ToolTree): ToolType {
            var defName: String? = null
            var type: TypeActual? = null
            // var typeNameText = ""
            val members = mutableMapOf<String, Def>()
            fun processDeclKids(declKids: List<ToolTree>) {
                // For default values, there's a nested call. And looks like comments are outside here.
                val subKids = when (declKids.firstOrNull()?.kind == TreeKind.Call) {
                    true -> declKids[0].kids
                    false -> declKids
                }
                subKids.firstOrNull { it.kind == TreeKind.LeftName }?.let { def ->
                    members[def.text!!] = def.asDef()!!
                }
            }
            kids@ for (kid in tree.kids) {
                when (kid.kind) {
                    TreeKind.Call -> {
                        // Constructor properties appear here.
                        val decl = kid.kids.find { it.kind == TreeKind.Decl } ?: continue@kids
                        decl.declMeta?.memberKind == DeclKind.Property || continue@kids
                        processDeclKids(decl.kids)
                    }
                    TreeKind.LeftName -> {
                        // typeNameText = kid.text!!
                        defName = kid.name!!
                        type = kid.value as? TypeActual
                    }
                    TreeKind.Fun -> {
                        // Non-constructor members appear here.
                        // The TypeShape already has members but not where they live in source nor their unique names.
                        val blockKids =
                            // For single members, the block gets skipped.
                            (kid.kids.find { it.kind == TreeKind.Block } ?: kid).kids.asSequence()
                        // For the single member case, the `@public` call also gets turned into a decl.
                        val subs = blockKids.filter { it.kind == TreeKind.Call || it.kind == TreeKind.Decl }
                        subs.forEach subs@{ memberCall ->
                            val declKids = (memberCall.kids.find { it.kind == TreeKind.Decl } ?: return@subs).kids
                            processDeclKids(declKids)
                        }
                    }
                    else -> {}
                }
            }
            // val typeName = type?.let { findTypeName(it) }
            return ToolType(defName = defName, members = members, type = type)
        }
    }
}

private fun findTypeName(type: TypeActual): String? = when (type) {
    is NominalType -> type.definition.name.rawDiagnostic
    else -> null
}

private fun makePrettyTypeName(type: TypeActual) = toStringViaTextOutput { textOutput ->
    WrappedTokenSink(
        TemperFormattingHints.makeFormattingTokenSink(
            TextOutputTokenSink(textOutput),
            singleLine = true,
        ),
    ) { token ->
        when (token) {
            is NameOutputToken -> token.name.toSymbol()?.let { word(it.text) }
            else -> null
        } ?: emit(token)
    }.use { type.renderTo(it) }
}

private fun completeMembers(call: ToolTree, prefix: String): List<Def> {
    if (call.kids.isEmpty()) {
        return emptyList()
    }
    return when (val type = call.kids[0].type) {
        is NominalType -> when (val typeDef = type.definition) {
            is TypeShape -> {
                typeDef.methods.asSequence().filter { it.symbol.text.startsWith(prefix) }.mapNotNull {
                    when (it.methodKind) {
                        MethodKind.Constructor -> null
                        // TODO Find actual defs for these.
                        else -> Mention.def(text = it.symbol.text, name = it.name.toString(), pos = call.pos)
                    }
                }.toList().sortedBy { it.text }
            }
            // TODO Handle other kinds of type definitions.
            else -> null
        }
        // TODO Handle other kinds of types.
        else -> null
    } ?: emptyList()
}

private fun findMention(
    offset: Int,
    tree: ToolTree,
    parent: ToolTree? = null,
    boxType: TypeActual? = null,
    callee: ToolTree? = null,
    newType: TypeActual? = null,
): MentionContext? = when {
    tree.isMention -> when {
        parent?.isDot == true && parent.kids[0] !== tree ->
            // Not the first kid, so must be the member name. Inline comments may affect actual index.
            MemberMentionContext(mention = tree.asMention(), parent = parent)
        tree.isDef -> {
            val defBoxType = when (parent?.declMeta?.memberKind) {
                DeclKind.Method, DeclKind.Property -> boxType
                else -> null
            }
            DefMentionContext(mention = tree.asDef()!!, parent = parent, boxType = defBoxType)
        }
        tree.isRef -> when ((callee != null || newType != null) && tree.isComplexArgName(parent = parent!!)) {
            true -> when (callee != null) {
                true -> CallMentionContext(mention = tree.asMention()!!, parent = callee)
                false -> NewCallMentionContext(mention = tree.asMention()!!, boxType = newType!!)
            }
            false -> RefMentionContext(mention = tree.asMention()!!, parent = parent)
        }
        tree.isSym -> when (newType != null) {
            true -> NewCallMentionContext(mention = tree.asMention()!!, boxType = newType)
            false -> null
        }
        // Don't handle symbols outside of member context for now.
        else -> null
    }
    else -> {
        val boxTypeNow = when {
            tree.isClass -> tree.kids.mapFirst { it.value as? TypeActual }
            else -> null
        } ?: boxType
        val newTypeNow = when {
            // In early stages, named args are nested in a block immediately under the new call.
            // This is similar to callee named args above, but its later handling is different.
            newType != null && parent!!.kids[0].name == "new" && tree.kind == TreeKind.Block -> newType
            // But for property bags, we don't ever see that form.
            tree.kind == TreeKind.Call && tree.kids[0].name == "new" -> tree.type
            else -> null
        }
        // Only track explicit callees when not dealing with new calls.
        val calleeNow = when (newTypeNow == null && parent?.kind == TreeKind.Call && tree.kind == TreeKind.Block) {
            // Provide for named arg grandkids, where we only expect named down this extra level.
            true -> parent.focus
            false -> null
        }
        tree.findContainer(offset)?.let {
            findMention(
                offset = offset,
                parent = tree,
                tree = it,
                boxType = boxTypeNow,
                callee = calleeNow,
                newType = newTypeNow,
            )
        }
    }
}

/** Where found, return the new kid at the same index as the previous focus. */
fun findNewFocus(tree: ToolTree, newKids: List<ToolTree>) = when (tree.focus) {
    null -> null
    // The focus is usually at index 0 or 1, so this should be constant time.
    else -> newKids.getOrNull(tree.kids.indexOfFirst { it === tree.focus })
}

/**
 * Further constrained and prepared variation of [MentionContext].
 * TODO(tjp, tooling): Can we replace [MentionContext] with this?
 */
sealed class Lookup
class DirectLookup(val mention: Mention, val name: String) : Lookup()
class MemberLookup(val mention: Mention, val name: String, val type: ToolType) : Lookup()
class ThisLookup(val mention: Mention, val name: String, val type: ToolType) : Lookup()

sealed class MentionContext(
    open val mention: Mention?,
    open val parent: ToolTree?,
    open val boxType: TypeActual? = null,
)

/** Call to the function indicated by [parent], which actually isn't the parent for this case. */
class CallMentionContext(
    mention: Mention,
    parent: ToolTree,
) : MentionContext(mention = mention, parent = parent) {
    override val mention: Mention get() = super.mention!!
    override val parent: ToolTree get() = super.parent!!
}

/** Created only with an actual mention and in reference to something in enclosing scope. */
sealed class DirectMentionContext(
    mention: Mention,
    parent: ToolTree?,
    /** Helps track context of being inside a class definition. */
    boxType: TypeActual? = null,
) : MentionContext(mention = mention, parent = parent, boxType = boxType) {
    override val mention: Mention get() = super.mention!!
}

class DefMentionContext(mention: Mention, parent: ToolTree?, boxType: TypeActual? = null) :
    DirectMentionContext(mention = mention, parent = parent, boxType = boxType)

class RefMentionContext(mention: Mention, parent: ToolTree?) : DirectMentionContext(mention = mention, parent = parent)

/**
 * Mentions, e.g., a class member, such as in `expr.member`. But [mention] could be null if not inside the `member`
 * symbol or if it's missing entirely (which would be an error, but we have to support that here).
 */
class MemberMentionContext(
    mention: Mention? = null,
    parent: ToolTree,
) : MentionContext(mention = mention, parent = parent) {
    override val parent: ToolTree get() = super.parent!!

    companion object {
        /** Returns non-null only if the mention also exists. */
        fun fromDot(parent: ToolTree): MemberMentionContext? {
            parent.isDot || return null
            val startIndex = parent.kids.indexOf(parent.focus) + 1
            val mention = parent.kids.subListToEnd(startIndex).find { it.isMention }?.asMention() ?: return null
            return MemberMentionContext(mention = mention, parent = parent)
        }
    }
}

/** Constructor call for the given type. */
class NewCallMentionContext(
    mention: Mention,
    parent: ToolTree? = null,
    boxType: TypeActual,
) : MentionContext(mention = mention, parent = parent, boxType = boxType) {
    override val mention: Mention get() = super.mention!!
    override val boxType: TypeActual get() = super.boxType!!
}

/** Indicates being inside textual content such as strings or comments where mentions don't usually apply. */
object TextMentionContext : MentionContext(mention = null, parent = null)

private fun gatherDefsTop(offset: Int, tree: ToolTree, defs: MutableMap<String, Def>): MentionContext? {
    gatherBuiltins(defs = defs)
    // Let locals overwrite builtins.
    return gatherDefs(offset = offset, tree = tree, defs = defs, atTop = true)
}

/** Finds the mention at the offset, if any, and fills up defs along the way. */
private fun gatherDefs(
    offset: Int,
    tree: ToolTree,
    defs: MutableMap<String, Def>,
    atTop: Boolean,
    parent: ToolTree? = null,
): MentionContext? {
    return when {
        tree.isDef -> DefMentionContext(mention = tree.asDef()!!, parent = parent)
        tree.isMention -> {
            val isDot = parent?.isDot == true
            when {
                tree.name == "." && isDot -> MemberMentionContext(parent = parent)
                // This goes after above on purpose because "." is a ref.
                tree.isRef -> RefMentionContext(mention = tree.asRef()!!, parent = parent)
                tree.isSym && isDot -> MemberMentionContext(mention = tree.asSym()!!, parent = parent)
                else -> null
            }
        }
        tree.isText -> TextMentionContext
        else -> {
            var previous: ToolTree? = null
            var result: MentionContext? = null
            for (kid in tree.kids) {
                val offsetPastLeft = kid.pos.left <= offset
                when {
                    // Look if not yet past our offset.
                    atTop || offsetPastLeft -> {
                        when {
                            // Descend if contained.
                            offsetPastLeft && offset <= kid.pos.right -> {
                                if (kid.isText) {
                                    when {
                                        // But don't descend from edges of comments and strings.
                                        offset == kid.pos.left && !kid.isCommentHead -> continue
                                        offset == kid.pos.right && !kid.isCommentTail -> continue
                                        else -> Unit
                                    }
                                }
                                val kidAtTop = when {
                                    atTop -> when (kid.kind) {
                                        TreeKind.Block, TreeKind.Esc, TreeKind.Fun -> false
                                        else -> true
                                    }
                                    else -> false
                                }
                                result = gatherDefs(
                                    offset = offset,
                                    tree = kid,
                                    defs = defs,
                                    atTop = kidAtTop,
                                    parent = tree,
                                )
                            }
                            // Gather defs if not.
                            else -> if (!kid.isComment) {
                                if (offsetPastLeft) {
                                    previous = kid
                                }
                                gatherExposedDefs(tree = kid, defs = defs, offset = offset)
                            }
                        }
                    }
                    // Done if past offset.
                    else -> break
                }
            }
            when {
                result != null -> result
                // Remember dot even if not touching.
                tree.isDot && previous?.name == "." -> MemberMentionContext(parent = tree)
                else -> result
            }
        }
    }
}

private val builtinDefs = lazy {
    val env = builtinOnlyEnvironment(EmptyEnvironment, Genre.Library)
    // Core builtins.
    val core = env.locallyDeclared.asSequence().mapNotNull builtinNames@{ temperName ->
        val name = temperName.builtinKey ?: return@builtinNames null
        val pos = env.declarationSite(temperName) ?: unknownPos
        name to Mention.def(name = name, pos = pos, text = name)
    }
    // Implicits, letting these overwrite core where applicable.
    val implicits = ImplicitsModule.module.exports?.asSequence()?.map { export ->
        val name = export.name.baseName.nameText
        name to Mention.def(name = name, pos = export.position, text = name)
    } ?: emptySequence()
    (core + implicits).toMap()
}

internal val builtinWordDefs = lazy {
    builtinDefs.value.entries.asSequence().filter { isWordLike(it.key) }.map { it.key to it.value }.toMap()
}

private fun gatherBuiltins(defs: MutableMap<String, Def>) {
    defs += builtinWordDefs.value
}

/** Gather defs from this scope, overwriting earlier defs with the same base name. */
private fun gatherExposedDefs(tree: ToolTree, defs: MutableMap<String, Def>, offset: Int) {
    fun maybePut(def: Def) {
        when {
            def.pos.left <= offset -> defs[def.text] = def
            else -> defs.putIfAbsent(def.text, def)
        }
    }
    when (tree.kind) {
        TreeKind.Decl -> {
            val context = when {
                // Treat declaring to a call as destructuring.
                tree.kids.isNotEmpty() && tree.kids.first().kind == TreeKind.Call -> tree.kids.first()
                else -> tree
            }
            for (declKid in context.kids) {
                declKid.asDef()?.let { maybePut(it) }
            }
        }
        TreeKind.LeftName -> maybePut(tree.asDef()!!)
        else -> Unit
    }
}

fun favoredContainer(a: ToolTree, b: ToolTree) = when {
    // Skip zero length.
    a.pos.right == a.pos.left -> b
    b.pos.right == b.pos.left -> a
    // Prefer against comments.
    a.isComment -> b
    b.isComment -> a
    // Beyond that, prefer more interesting things, and the thing we're starting (b) rather than ending (a).
    // Mention over not.
    !a.isMention -> b
    !b.isMention -> a
    // Def over other mentions.
    b.isDef -> b
    a.isDef -> a
    // Avoid mutants.
    // Things change from say ":" to "type", but could also say "something.type" for a symbol with name "type".
    a.isMutant -> b
    b.isMutant -> a
    // Words over non-words. Both must already be mentions per above.
    !isWordLike(a.text!!) -> b
    !isWordLike(b.text!!) -> a
    // Refs over symbols.
    b.isRef -> b
    a.isRef -> a
    // Default to the upcoming one.
    else -> b
}

/** Shallow search for container of offset, if any. */
internal fun findContainer(offset: Int, trees: List<ToolTree>): ToolTree? {
    // TODO Once we have large temper programs, see if binary search really helps at all.
    // TODO We already have a tree. Issues would be if an blocks have many kids, such as top level.
    val index = trees.binarySearch { it.pos.left - offset }
    return when {
        index == 0 -> trees[index]
        index > 0 -> {
            // We landed right at the start of one. See if we're also at an end.
            val previous = trees[index - 1]
            when (previous.pos.right) {
                offset -> favoredContainer(previous, trees[index])
                else -> trees[index]
            }
        }
        else -> when (val insertion = -index - 1) {
            0 -> null
            else -> {
                val before = trees[insertion - 1]
                when {
                    // Check upper bound only, since insertion point implies lower bound.
                    offset <= before.pos.right -> before
                    else -> null
                }
            }
        }
    }
}

fun isWordLike(text: String) = text.isNotEmpty() && text[0].code in IdParts.Start

/** Alternative to jvm-only putIfAbsent. */
fun <K, V> (MutableMap<K, V>).putIfMissing(key: K, value: V): V? {
    val previous = this[key]
    if (previous == null) {
        this[key] = value
    }
    return previous
}
