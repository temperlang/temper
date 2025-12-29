package lang.temper.builtin

import lang.temper.common.LeftOrRight
import lang.temper.common.subListToEnd
import lang.temper.env.InterpMode
import lang.temper.log.CodeLocation
import lang.temper.log.MessageTemplate
import lang.temper.log.spanningPosition
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedParsedName
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.MkType2
import lang.temper.type2.ValueFormal2
import lang.temper.type2.ValueFormalKind
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.InnerTree
import lang.temper.value.InnerTreeType
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TBoolean
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.Value
import lang.temper.value.freeTree
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.vDocStringSymbol
import lang.temper.value.varSymbol

/**
 * Attaches comments to declarations and functions.
 */
object AttachCommentDecorator : NamedBuiltinFun {
    override val name: String = "@docComment"
    override val sigs: List<MacroSignature> = listOf(
        MacroSignature(
            returnType = null,
            requiredValueFormals = listOf(
                MacroValueFormal(
                    null,
                    TreeTypeStructureExpectation(
                        // Declarations and functions accept directly metadata.
                        // Blocks can be complex arguments.
                        setOf(InnerTreeType.Block, InnerTreeType.Decl, InnerTreeType.Fun),
                    ),
                    kind = ValueFormalKind.Required,
                ),
                ValueFormal2(
                    MkType2(WellKnownTypes.listTypeDefinition)
                        .actuals(listOf(WellKnownTypes.stringType2))
                        .get(),
                    kind = ValueFormalKind.Required,
                ),
            ),
            restValuesFormal = null,
        ),
    )

    private const val ARITY = 3

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        if (macroEnv.args.size != ARITY) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(ARITY))
        }
        val decorated = macroEnv.args.valueTree(0)
        val commentListResult = macroEnv.args.evaluate(1, interpMode)
        if (commentListResult !is Value<*>) { return commentListResult }
        val commentPos = macroEnv.args.pos(1)
        val commentList = TList.unpackOrNull(commentListResult)
            ?: return macroEnv.fail(
                MessageTemplate.ExpectedValueOfType, commentPos,
                listOf(TList, commentListResult),
            )
        val filterSemilitResult = macroEnv.args.evaluate(2, interpMode)
        if (filterSemilitResult !is Value<*>) { return filterSemilitResult }
        val filterSemilit = TBoolean.unpackOrNull(filterSemilitResult)
            ?: return macroEnv.fail(
                MessageTemplate.ExpectedValueOfType, macroEnv.args.pos(2),
                listOf(TBoolean, filterSemilitResult),
            )
        val commentStrings = commentList.map {
            TString.unpackOrNull(it) ?: return@invoke macroEnv.fail(
                MessageTemplate.ExpectedValueOfType, commentPos,
                listOf(TString, it),
            )
        }
        // We pretty aggressively try to eliminate comments early.
        val decoratedThrough = decorated.incoming?.let {
            lookThroughDecorations(it).target
        } ?: decorated

        val comments = commentStrings.map {
            RemUnpacked(commentPos, association = LeftOrRight.Right, it, isSemilit = filterSemilit)
        }

        var insertInto: InnerTree? = null
        var insertionPoint: Int = -1
        var insertAsRemCall = false
        when (decoratedThrough) {
            is DeclTree -> decoratedThrough.parts?.let { parts ->
                insertInto = decoratedThrough
                insertionPoint = decorated.size
                // Attach documentation to function instead of declaration if it's
                // declaring a function
                val init = parts.metadataSymbolMap[initSymbol]?.let {
                    lookThroughDecorations(it).target
                }
                if (init is FunTree && varSymbol !in parts.metadataSymbolMap) {
                    val initParts = init.parts
                    if (initParts != null) {
                        insertInto = init
                        insertionPoint = initParts.body.incoming!!.edgeIndex
                    }
                }
            }
            is FunTree -> decoratedThrough.parts?.let { parts ->
                insertInto = decoratedThrough
                // Insert metadata before body
                insertionPoint = parts.body.incoming!!.edgeIndex
            }
            is BlockTree -> if (isComplexArg(decoratedThrough)) {
                insertInto = decoratedThrough
                insertionPoint = 1
                insertAsRemCall = true
            }
            else -> {}
        }

        insertInto?.insert(insertionPoint) {
            if (insertAsRemCall) {
                for (comment in comments) {
                    Call(commentPos, EmbeddedCommentFn) {
                        V(Value(comment.text, TString))
                        V(commentPos.rightEdge, TBoolean.value(comment.association == LeftOrRight.Right))
                        V(commentPos.rightEdge, TBoolean.value(comment.isSemilit))
                    }
                }
            } else {
                val comment = pickCommentToAttach(comments, insertInto as? DeclTree)
                if (comment != null) {
                    val metadataValue = docPartsList(comment.text, commentPos.loc)
                    V(commentPos.leftEdge, vDocStringSymbol)
                    V(commentPos, metadataValue)
                }
            }
        }
        if (macroEnv.call != null) {
            macroEnv.replaceMacroCallWith(freeTree(decorated))
        }
        return NotYet
    }
}

val vAttachCommentDecorator = Value(AttachCommentDecorator)

fun docPartsList(
    commentText: String,
    loc: CodeLocation,
): Value<List<Value<*>>> {
    val firstParagraphEnd = firstParagraphEndRegex.find(commentText)?.range?.first
        ?: commentText.length
    val briefHelp = commentText.substring(0, firstParagraphEnd)
    return Value(
        listOf(
            Value(briefHelp, TString),
            Value(commentText, TString),
            Value(loc.diagnostic, TString),
        ),
        TList,
    )
}

private val firstParagraphEndRegex = Regex(
    """(?:\r\n?|\n)[ \t]*(?:\r\n?|\n)""",
)

/**
 * Pick a comment for a declaration.
 *
 * We apply the following rules:
 * 1. If there is a non-semilit paragraph, use that as the comment
 * 2. If all are semilit paragraph, do the following:
 *   i. Look for a semilit paragraph that starts with the declaration's parsed name
 *      case-insensitively.
 *   ii. If found, concatenate the contents of that paragraph and all following separated
 *      by 2 blank lines and use that as the comment to associate.
 *
 *      This means that markdown like the below leads to token text of
 *      "f is a cool function.\n\nAlso documented."
 *
 *          Here's some Markdown.
 *
 *          We haven't mentioned f yet.
 *
 *          f is a cool function.
 *
 *          Also documented.
 *
 *              let f(): Int { 42 }
 */
fun pickCommentToAttach(rems: List<RemUnpacked>, decl: DeclTree?): RemUnpacked? {
    val nonSemilit = rems.firstOrNull { !it.isSemilit } // Rule 1
    if (nonSemilit != null) { return nonSemilit }

    val parts = decl?.parts
    val name = when (val name = parts?.name?.content) {
        is ParsedName -> name
        is ResolvedParsedName -> name.baseName
        else -> null
    }

    val indexOfDocumenting = rems.indexOfFirst {
        name != null && remStartsWithName(it, name)
    }
    return if (indexOfDocumenting >= 0) {
        combineRems(rems.subListToEnd(indexOfDocumenting))
    } else {
        null
    }
}

private fun remStartsWithName(rem: RemUnpacked, name: ParsedName): Boolean {
    val tokenText = rem.text
    val firstSpace = tokenText.indexOfFirst { it.isWhitespace() }
    if (firstSpace <= 0) { return false }
    val firstWord = tokenText.substring(0, firstSpace)
        .trim { it in IGNORE_AROUND_NAME } // "`x`" -> "x"
        // Many languages require upper-case at the start of a paragraph or for nouns.
        .lowercase()
    val wanted = name.nameText.lowercase()
    return firstWord == wanted
}

private fun combineRems(rems: List<RemUnpacked>): RemUnpacked {
    require(rems.isNotEmpty())
    val first = rems.first()
    if (rems.size == 1) {
        return first
    }
    return first.copy(
        pos = rems.spanningPosition(first.pos),
        text = buildString {
            rems.forEachIndexed { index, rem ->
                if (index != 0) {
                    append("\n\n")
                }
                append(rem.text)
            }
        },
    )
}

/**
 * Markdown characters to ignore around a declared name.
 * These are often used for Markdown formatting,
 * for example, to set something apart as a foreign term or bold it.
 */
private const val IGNORE_AROUND_NAME = "*`"
