package lang.temper.cli.repl

import lang.temper.lexer.Lexer
import lang.temper.lexer.LexicalDefinitions.Companion.quotedNamePrefix
import lang.temper.lexer.Operator
import lang.temper.lexer.OperatorType
import lang.temper.log.LogSink
import lang.temper.log.unknownPos

internal fun adjustInputForRepl(commandText: String): String {
    if (commandText.trimStart().startsWith(ReplHelpFn.NAME)) {
        // Lex up to 5 tokens to see if we match ["help", "(", something, ")", ";"]
        // with or without the semicolon at the end.
        // If something can't parse as a name by itself, fix it up so that the help
        // function generally works.
        val lexer = Lexer(unknownPos.loc, LogSink.devNull, commandText)
        var lexedAll = true
        val tokens = buildList {
            while (lexer.hasNext()) {
                val token = lexer.next()
                if (token.tokenType.ignorable) { continue }
                if (this.size > TOK_INDEX_OF_RPAREN + 1) {
                    lexedAll = false
                    break
                }
                add(token)
            }
        }
        if (
            lexedAll && tokens.size > TOK_INDEX_OF_RPAREN &&
            tokens[0].tokenText == ReplHelpFn.NAME &&
            tokens[1].tokenText == "(" &&
            tokens[TOK_INDEX_OF_RPAREN].tokenText == ")"
        ) {
            val argToken = tokens[2]
            val (_, tokenText, tokenType) = argToken
            if (
                OperatorType.entries.any {
                    Operator.matching(tokenText, tokenType, it).isNotEmpty()
                }
            ) {
                val before = commandText.substring(0, argToken.pos.left)
                val after = commandText.substring(argToken.pos.right)
                return "$before$quotedNamePrefix`$tokenText`$after"
            }
        }
    }
    return commandText
}

// token index of ")" in a sequence like ["help", "(", something, ")", ";"].
private const val TOK_INDEX_OF_RPAREN = 3
