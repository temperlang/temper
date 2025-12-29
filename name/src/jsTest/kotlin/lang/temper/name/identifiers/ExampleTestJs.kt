package lang.temper.name.identifiers

import kotlin.js.json

object ExampleTestJs {
    private val path: dynamic by lazy { js("require('path')") }
    private val fs: dynamic by lazy { js("require('fs')") }
    private val process: dynamic by lazy { js("require('process')") }

    fun cwd(): String = process.cwd() as String
    fun readFileSync(path: String) = fs.readFileSync(path, json("encoding" to "utf8")) as String

    // Oddly, the spread operator doesn't work on dynamic calls.
    fun join(vararg parts: String): String = path.join.apply(null, parts) as String
}

val node = ExampleTestJs

actual fun loadTextResource(first: String, vararg rest: String): String? {
    println("CWD=${node.cwd()}")
    // we're normally running in temper/build/js/packages/temper-name-test
    val path = node.join("..", "..", "..", "..", "name", first, *rest)
    return node.readFileSync(path)
}
