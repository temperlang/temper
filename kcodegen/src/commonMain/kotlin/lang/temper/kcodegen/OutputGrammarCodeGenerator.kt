package lang.temper.kcodegen

import lang.temper.common.Log
import lang.temper.common.console
import lang.temper.kcodegen.outgrammar.GrammarProcessor
import lang.temper.lexer.Lexer
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.lexer.TokenType
import lang.temper.lexer.sourceOffsetOf
import lang.temper.log.CodeLocation
import lang.temper.log.FilePositions
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.excerpt
import lang.temper.log.toReadablePosition
import lang.temper.parser.TokenSourceAdapterBuilder
import lang.temper.parser.parse

/**
 * A code generator that scans the `:be-*` (backend) subprojects' source trees for
 * `*.out-grammar` files to autogenerate a namespace with *Node* classes corresponding to
 * each type in the grammar.
 *
 * These are **output** grammars in the sense that we can serialize them to a string
 * (with sourcemap info), but do not try to derive parsers for them.
 */
class OutputGrammarCodeGenerator(
    subProject: String,
    private val logSink: LogSink? = null,
) : KotlinCodeGenerator(subProject) {
    override val sourcePrefix: String
        get() = "$GENERATED_FILE_PREFIX(\"OutputGrammarCodeGenerator\")"

    override fun generateSources(): List<GeneratedKotlinSource> {
        val files = globScanBestEffort(this.subProject, emptyList(), "**/*.out-grammar")
        return files.map { (grammarPath, readFile) ->
            generateGrammarSource(grammarPath, readFile())
        }
    }

    internal fun generateGrammarSource(
        grammarPath: List<String>,
        sourceText: String,
    ): GeneratedKotlinSource {
        val codeLocation = object : CodeLocation {
            override val diagnostic: String =
                "$subProject/src/${grammarPath.joinToString("/")}"
        }
        val fileName = grammarPath.last()
        val baseName = fileName.substring(0, fileName.indexOf('.'))
        val filePositions = FilePositions.fromSource(codeLocation, sourceText)
        val logSink = this.logSink
            ?: object : LogSink {
                override var hasFatal = false
                    private set

                override fun log(
                    level: Log.Level,
                    template: MessageTemplateI,
                    pos: Position,
                    values: List<Any>,
                    fyi: Boolean,
                ) {
                    if (level >= Log.Fatal) { hasFatal = true }
                    if (console.logs(level)) {
                        console.textOutput.withLevel(level) {
                            excerpt(pos, sourceText, console.textOutput)
                        }
                        console.log(level) {
                            val adjustedValues = values.map {
                                when (it) {
                                    is Position ->
                                        filePositions.spanning(it)?.toReadablePosition("${it.loc}")
                                            ?.let { readablePosition ->
                                                return@map readablePosition
                                            }
                                    else -> {}
                                }
                                it
                            }
                            "${
                                filePositions.filePositionAtOffset(pos.left)
                                    .toReadablePosition(fileName)
                            }: ${template.format(adjustedValues)}"
                        }
                        console.textOutput.endLine()
                    }
                }
            }
        val lexer =
            Lexer(codeLocation, logSink, sourceText, lang = StandaloneLanguageConfig, allowWordSuffixChars = true)
        val positionToPrecedingComment = run {
            readDocComments(lexer.copy(logSink = LogSink.devNull))
        }
        val outGrammarTokenSourceAdapterBuilder = TokenSourceAdapterBuilder()
        outGrammarTokenSourceAdapterBuilder.modifyingKeywords = setOf(
            // `data OutGrammarTypeName` should be treated as a `@data OutGrammarTypeName`
            "data",
            // `override MyNodeTypeName.memberName` is also a decoration.
            "override",
        )
        val root = parse(
            lexer,
            logSink,
            tokenSourceAdapterBuilder = outGrammarTokenSourceAdapterBuilder,
        )
        val packageNameParts = grammarPath.subList(2, grammarPath.size - 1)
        val gp = GrammarProcessor(
            logSink,
            baseName,
            packageNameParts,
            filePositions,
            positionToPrecedingComment,
        )
        var hasErrors = logSink.hasFatal
        val (outputText, outputTypeName) = gp.generateGrammar(root)
            // Generate a placeholder file so that we don't `git rm` prematurely.
            ?: run {
                hasErrors = true
                """
                    |${sourcePrefix}
                    |// There are errors in ${grammarPath.joinToString("/")}
                    |
                """.trimMargin() to
                    gp.getNamespaceName()
            }
        return GeneratedKotlinSource(
            packageNameParts = packageNameParts,
            baseName = outputTypeName,
            content = outputText,
            group = grammarPath[0],
            contentHasErrors = hasErrors,
        )
    }

    companion object {
        val subProjects get() = subProjectsMatchingBestEffort(Regex("^be(?:-.*)?$"))
    }
}

// Group /** comments */ with the start position of the following non-ignorable token.
private fun readDocComments(lexer: Lexer): Map<Position, String> {
    val positionToPrecedingComment = mutableMapOf<Position, String>()
    var lastComment: String? = null
    val loc = lexer.codeLocation
    for (token in lexer) {
        when (token.tokenType) {
            TokenType.Comment -> {
                val tokenText = token.tokenText
                if (tokenText.startsWith("/**")) {
                    lastComment = tokenText
                }
            }
            TokenType.Space -> Unit
            TokenType.Error -> {
                lastComment = null
            }
            TokenType.LeftDelimiter,
            TokenType.Number,
            TokenType.Punctuation,
            TokenType.RightDelimiter,
            TokenType.QuotedString,
            TokenType.Word,
            -> {
                if (lastComment != null) {
                    val leftPos = lexer.sourceOffsetOf(token)
                    positionToPrecedingComment[Position(loc, leftPos, leftPos)] = lastComment
                    lastComment = null
                }
            }
        }
    }
    return positionToPrecedingComment.toMap()
}
