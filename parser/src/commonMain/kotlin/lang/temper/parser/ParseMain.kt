@file:JvmName("ParseMain")

package lang.temper.parser

import lang.temper.common.console
import lang.temper.common.structure.toMinimalJson
import lang.temper.format.logTokens
import lang.temper.lexer.Lexer
import lang.temper.log.LogSink
import lang.temper.log.filePath
import kotlin.jvm.JvmName

fun main(args: Array<String>) {
    val (input) = args
    val loc = filePath("foo.temper")
    val logSink = LogSink.devNull
    val lexer = Lexer(loc, logSink, input)
    val root = parse(lexer, logSink)
    console.group("Regular") {
        console.logTokens(root, singleLine = false)
        console.textOutput.endLine()
    }
    console.textOutput.endLine()
    console.group("JSON") {
        console.log(root.toMinimalJson().toJsonString(indent = true))
    }
}
