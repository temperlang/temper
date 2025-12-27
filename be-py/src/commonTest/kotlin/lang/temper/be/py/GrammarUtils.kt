package lang.temper.be.py

import lang.temper.log.CodeLocation
import lang.temper.log.Position
import lang.temper.name.OutName
import kotlin.test.assertEquals

private class TestCodeLocation(
    lineno: Int,
    endLineno: Int,
    override val diagnostic: String = "L$lineno:$endLineno",
) : CodeLocation

/** Used to construct code locations for tests. */
fun ktPos(lineno: Int, colOffset: Int, endLineno: Int, endColOffset: Int): Position =
    Position(TestCodeLocation(lineno, endLineno), colOffset, endColOffset)

fun assertEqualCode(expected: String, ast: Py.Program) = assertEquals(trimLines(expected), trimLines(formatAst(ast)))

private val trailingWhitespace = Regex("\\s+$", RegexOption.MULTILINE)

fun trimLines(lines: String) = trailingWhitespace.replace(lines, "")

fun joinLines(vararg lines: String) = lines.joinToString("\n", postfix = "\n")

fun assertExports(expected: Set<String>, ast: Py.Program) {
    val exports = mutableSetOf<OutName>()
    ast.gatherExports(null) { name, delete -> if (!delete) exports.add(name.asSimpleName()!!) }
    assertEquals(expected, exports.map { it.outputNameText }.toSet())
}

fun assertImports(expected: Set<String>, ast: Py.Program) {
    val exclude = mutableSetOf<OutName>()
    ast.gatherExports(null) { name, delete -> if (!delete) exclude.add(name.asSimpleName()!!) }
    exclude.addAll(pyReservedWordsAndNames.map { OutName(it, null) })
    val imports = mutableSetOf<OutName>()
    ast.gatherImports(exclude) { name -> imports.add(name) }
    assertEquals(expected, imports.map { it.outputNameText }.toSet())
}
