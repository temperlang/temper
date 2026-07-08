package lang.temper.cli.repl

import lang.temper.common.temperEscaper
import lang.temper.lexer.Lexer
import lang.temper.lexer.LexicalDefinitions.Companion.quotedNamePrefix
import lang.temper.lexer.Operator
import lang.temper.lexer.OperatorType
import lang.temper.log.LogSink
import lang.temper.log.bannedPathSegmentNames
import lang.temper.log.filePath
import lang.temper.log.unknownPos
import lang.temper.name.ParsedName

internal fun adjustInputForRepl(commandText: String, repl: Repl): String {
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
                val isDefined = ambientNames.value[tokenText]?.terminal == true ||
                    ParsedName(tokenText) in repl.allExportedBaseNames
                val adjustedTokenText = if (isDefined) {
                    "$quotedNamePrefix`$tokenText`"
                } else if (tokenText !in bannedPathSegmentNames) {
                    val snippetIdStr = "${filePath("builtin", tokenText)}"
                    temperEscaper.escape(snippetIdStr)
                } else {
                    null
                }
                if (adjustedTokenText != null) {
                    return "$before$adjustedTokenText$after"
                }
            }
        }
    }
    return commandText
}

// token index of ")" in a sequence like ["help", "(", something, ")", ";"].
private const val TOK_INDEX_OF_RPAREN = 3
