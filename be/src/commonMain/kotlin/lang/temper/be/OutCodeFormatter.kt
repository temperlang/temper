package lang.temper.be

import lang.temper.ast.OutTree
import lang.temper.common.AppendingTextOutput
import lang.temper.common.LeftOrRight
import lang.temper.format.CodeFormatter
import lang.temper.format.OutputToken
import lang.temper.format.TextOutputTokenSink
import lang.temper.format.TokenSink
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.log.LogSink
import lang.temper.log.Position
import lang.temper.name.NameOutputToken
import lang.temper.name.OutName
import lang.temper.name.SourceName

/**
 * Formats an AST to an *Appendable*, usually the content of an [lang.temper.fs.OutRegularFile],
 * while simultaneously building a [SourceMap].
 */
class OutCodeFormatter(
    /** Used to populate [SourceMap.file]. */
    private val outPath: FilePath?,
    /** May wrap the token sink to merge multiple tokens into one */
    val wrapTokenSink: (TokenSink) -> TokenSink = { it },
    /**
     * Used to map code locations in source [Position]s to source file paths and the associated
     * offset -> line+column map.
     */
    lookupCodeLocation: (CodeLocation) -> Pair<FilePath, FilePositions>?,
    /**
     * Optional log sink that may be used if it is impossible to format code properly.
     * This may be the case for languages that have hard restrictions like column limits.
     * It's better to know about errors as they arise.
     */
    private val logSink: LogSink? = null,
) {
    private val sourceMapBuilder = SourceMapBuilder(outPath, lookupCodeLocation)

    fun format(root: OutTree<*>, out: Appendable) {
        val textOutput = AppendingTextOutput(out)
        val sourceMappingTokenSink = SourceMappingTokenSink(
            TextOutputTokenSink(textOutput),
            sourceMapBuilder,
        )
        wrapTokenSink(
            root.formattingHints().makeFormattingTokenSink(
                sourceMappingTokenSink,
                singleLine = false,
                logSink = logSink,
                outPath = outPath,
            ),
        ).use { formattingTokenSink ->
            // Any long-line wrapping token sinks should precede formattingTokenSink or independently
            // check root.formattingHints.mayBreakLineBetween
            CodeFormatter(formattingTokenSink).format(root, false)
        }
    }

    fun buildSourceMap(contentFor: (CodeLocation) -> String?) = sourceMapBuilder.build(contentFor)
}

private class SourceMappingTokenSink(
    val tokenSink: TokenSink,
    val sourceMapBuilder: SourceMapBuilder,
) : TokenSink {
    override fun position(pos: Position, side: LeftOrRight) {
        sourceMapBuilder.position(pos, side)
    }

    override fun endLine() {
        tokenSink.endLine()
        sourceMapBuilder.lineEnded()
    }

    override fun emit(token: OutputToken) {
        if (token is NameOutputToken) {
            val name = token.name
            if (name is OutName) {
                val sourceNameText = when (val sourceName = name.sourceName) {
                    is SourceName -> sourceName.baseName.nameText
                    null -> null
                    else -> sourceName.rawDiagnostic
                }
                if (sourceNameText != null) {
                    sourceMapBuilder.wroteName(token.text.length, sourceNameText)
                    tokenSink.emit(token)
                    return
                }
            }
        }
        sourceMapBuilder.wroteChars(token.text.length)
        tokenSink.emit(token)
    }

    override fun finish() {
        tokenSink.finish()
    }
}
