package lang.temper.frontend.parse

import lang.temper.ast.flatten
import lang.temper.astbuild.StoredCommentTokens
import lang.temper.astbuild.buildTree
import lang.temper.common.Log
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonDouble
import lang.temper.common.json.JsonLong
import lang.temper.common.json.JsonNull
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonProperty
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.cst.ConcreteSyntaxTree
import lang.temper.cst.CstComment
import lang.temper.cst.CstInner
import lang.temper.cst.CstLeaf
import lang.temper.frontend.CstSnapshotKey
import lang.temper.lexer.Operator
import lang.temper.lexer.TokenSource
import lang.temper.log.ConfigurationKey
import lang.temper.log.Debug
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.snapshot
import lang.temper.log.spanningPosition
import lang.temper.parser.parse
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DocumentContext
import lang.temper.value.RightNameLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TFloat64
import lang.temper.value.TInt
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf

internal class ParseStage(
    private val tokenSource: TokenSource,
    private val logSink: LogSink,
    private val documentContext: DocumentContext,
) {
    fun<T> process(
        onCompletion: (
            root: CstInner,
            ast: BlockTree,
            appendix: JsonValue?,
            comments: List<CstComment>,
        ) -> T,
    ): T {
        val configKey: ConfigurationKey = documentContext.configurationKey
        return Debug.Frontend.ParseStage(configKey).group("Parse Stage") {
            val comments = mutableListOf<CstComment>()
            val root = parse(tokenSource, logSink, comments)
            Debug.Frontend.ParseStage.Before.snapshot(configKey, CstSnapshotKey, root)

            val (codeRoot, appendixRoot) =
                splitCodeFromMetadata(root, logSink)

            val cstParts = flatten(codeRoot)
            val storedCommentTokens = StoredCommentTokens(comments)
            val ast = BlockTree.maybeWrap(
                buildTree(
                    cstParts = cstParts.toList(),
                    storedCommentTokens = storedCommentTokens,
                    logSink = logSink,
                    documentContext = documentContext,
                ),
            )
            val appendix = appendixRoot?.let { toJsonValue(it, documentContext, logSink) }
            rewriteCallJoins(ast)
            onCompletion(root, ast, appendix, comments.toList())
            // Snapshotters with full module access (per their configuration) can use the above
            // onCompletion results.
            // Debug.Frontend.ParseStage.After is done in Module once for all sources
        }
    }
}

fun splitCodeFromMetadata(
    root: CstInner,
    logSink: LogSink,
): Pair<ConcreteSyntaxTree, ConcreteSyntaxTree?> {
    // See segmentTemperFile
    if (root.operator != Operator.SemiSemiSemi) {
        return root to null
    }

    fun isSuperToken(cst: ConcreteSyntaxTree) =
        cst is CstLeaf && cst.tokenText == Operator.SemiSemiSemi.text

    // `;;;` is a separator, so we could have
    //    ;;; metadata
    // or
    //    body ;;; metadata
    // or
    //    body ;;;
    var i = 0
    var code: ConcreteSyntaxTree? = null
    var metadata: ConcreteSyntaxTree? = null
    if (i < root.childCount) {
        val child0 = root.child(0)
        if (!isSuperToken(child0)) {
            code = child0
            i += 1
        }
    }
    if (i < root.childCount && isSuperToken(root.child(i))) {
        i += 1
        if (i < root.childCount) {
            val possibleMetadataRoot = root.child(i)
            if (!isSuperToken(possibleMetadataRoot)) {
                metadata = possibleMetadataRoot
                i += 1
            }
        }
    }
    if (i != root.childCount) {
        logSink.log(
            level = Log.Error,
            template = MessageTemplate.ExtraneousModuleContent,
            pos = (i until root.childCount).map { root.child(i).pos }
                .spanningPosition(root.child(i).pos),
            values = emptyList(),
        )
    }

    return Pair(
        code ?: CstInner(root.pos.leftEdge, Operator.Root, emptyList()),
        metadata,
    )
}

private fun toJsonValue(
    cst: ConcreteSyntaxTree,
    documentContext: DocumentContext,
    logSink: LogSink,
): JsonValue {
    val cstParts = flatten(cst)
    val ast = buildTree(
        cstParts = cstParts.toList(),
        storedCommentTokens = StoredCommentTokens.empty,
        logSink = logSink,
        documentContext = documentContext,
        startProduction = "Json",
    )
    fun treeToJson(tree: Tree): JsonValue {
        // The cases below are careful to return when they find something well-formed so that
        // we can share a log and return null strategy below.
        when (tree) {
            is ValueLeaf -> {
                val content = tree.content
                when (content.typeTag) {
                    TBoolean -> return JsonBoolean(TBoolean.unpack(content))
                    TNull -> return JsonNull
                    TInt -> return JsonLong(TInt.unpack(content).toLong())
                    TFloat64 -> return JsonDouble(TFloat64.unpack(content))
                    TString -> return JsonString(TString.unpack(content))
                    else -> Unit
                }
            }
            is CallTree -> when ((tree.childOrNull(0) as? RightNameLeaf)?.content?.builtinKey) {
                "{}" -> {
                    if ((tree.size and 1) == 1) { // After callee, need pairs
                        val props = mutableListOf<JsonProperty>()
                        for (i in 1 until tree.size step 2) {
                            val keyTree = tree.child(i)
                            val valueTree = tree.child(i + 1)
                            val key = TString.unpackOrNull((keyTree as? ValueLeaf)?.content)
                            if (key == null) {
                                logSink.log(
                                    level = Log.Error,
                                    template = MessageTemplate.InvalidMetadata,
                                    pos = listOf(keyTree, valueTree).spanningPosition(tree.pos),
                                    values = emptyList(),
                                )
                            } else {
                                props.add(JsonProperty(key, treeToJson(valueTree), emptySet()))
                            }
                        }
                        return JsonObject(props.toList())
                    }
                }
                "[]" -> return JsonArray(
                    (1 until tree.size).map { treeToJson(tree.child(it)) },
                )
                "-" -> {
                    if (tree.size == 2) {
                        val content = (tree.child(1) as? ValueLeaf)?.content
                        if (content != null) {
                            when (content.typeTag) {
                                // TODO: Handle Long.MIN_VALUE in grammar
                                TInt -> return JsonLong(-TInt.unpack(content).toLong())
                                TFloat64 -> return JsonDouble(-TFloat64.unpack(content))
                                else -> Unit
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
        logSink.log(
            level = Log.Error,
            template = MessageTemplate.InvalidMetadata,
            pos = tree.pos,
            values = emptyList(),
        )
        return JsonNull
    }
    return treeToJson(ast)
}
