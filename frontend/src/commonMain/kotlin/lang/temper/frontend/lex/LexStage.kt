package lang.temper.frontend.lex

import lang.temper.lexer.LanguageConfig
import lang.temper.lexer.Lexer
import lang.temper.log.CodeLocation
import lang.temper.log.LogSink

class LexStage(
    private val lang: LanguageConfig,
    private val codeLocation: CodeLocation,
    private val content: CharSequence,
    private val logSink: LogSink,
) {
    fun<T> process(onCompletion: (lexer: Lexer) -> T): T {
        return onCompletion(Lexer(codeLocation, logSink, sourceText = content, lang = lang))
    }
}
