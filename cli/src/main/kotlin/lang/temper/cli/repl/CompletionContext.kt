package lang.temper.cli.repl

import lang.temper.lexer.TemperToken

internal data class CompletionContext(
    val tokens: List<TemperToken>,
    val cursorTokenIndex: Int,
    val offsetIntoTokenText: Int,
) {
    init {
        require(cursorTokenIndex in tokens.indices)
        require(offsetIntoTokenText in 0..tokens[cursorTokenIndex].tokenText.length)
    }
}
