package lang.temper.astbuild

import lang.temper.ast.CstPart
import lang.temper.log.LogSink
import lang.temper.value.DocumentContext
import lang.temper.value.Tree

/** Builds an abstract syntax tree from a parse tree. */
fun buildTree(
    /** A flattened tree as from [flatten] */
    cstParts: List<CstPart>,
    /** Comment tokens by position ranges. */
    storedCommentTokens: StoredCommentTokens,
    /** Receives error messages when [cstParts] are un-parsable. */
    logSink: LogSink,
    /**
     * The context for the document produced.
     * It's naming context is used to generate temporary names that do not overlap with source
     * tokens.
     */
    documentContext: DocumentContext,
    /** The name of the production to start at */
    startProduction: String = "Root",
): Tree {
    val (tree, position) = grammar.apply(
        startProduction = startProduction,
        input = cstParts,
        storedCommentTokens = storedCommentTokens,
        logSink = logSink,
        mustConsumeAllParts = true,
        documentContext = documentContext,
    )
    // Check whole input consumed.
    // For root, this is a non-issue since it has Eof at the end.
    // For other TreeTypes it may be an issue.
    require(position == cstParts.size) {
        "From $startProduction, unused parts ${
            if (position in cstParts.indices) {
                cstParts.subList(position, cstParts.size).toString()
            } else {
                "???"
            }
        }, position=$position"
    }
    return tree
}
