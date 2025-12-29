package lang.temper.name.identifiers

import lang.temper.common.decodeUtf16Iter
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.Test

internal class ExampleTest {

    private fun parseExamples(markdown: String): List<Example> {
        return buildList {
            for (row in parseMdTable(markdown)) {
                val name = row["Name"]!!
                for (style in IdentStyle.values()) {
                    row[style.name]?.let { cell ->
                        add(Example(name, style, cell.trim('`')))
                    }
                }
            }
        }
    }

    @Test
    fun validateMarkdownExamples() {
        loadTextResource("src", "commonTest", "resources", "identifier-examples.md")?.let(::parseExamples)
            ?.let { examples ->
                for (ex in examples) {
                    println("${ex.name}--${ex.style}: id=${ex.identifier} pp=${ex.parts}")
                    assertEquals("${ex.name}: ${ex.style}.join", ex.identifier, ex.style.join(ex.parts))
                    assertEquals("${ex.name}: ${ex.style}.split", ex.parts, ex.style.split(ex.identifier))
                }
            }
    }
}

internal data class Example(val name: String, val style: IdentStyle, val identifier: String, val parts: List<Segment>) {
    constructor(name: String, style: IdentStyle, unparsed: String) :
        this(name, style, stripExample(unparsed), splitExample(unparsed))
}

/** The expected identifier is just all decorative characters removed. */
private val stripExampleRe = Regex("[.:!]")
fun stripExample(text: String): String = text.replace(stripExampleRe, "")
private val splitExampleRe = Regex("\\.|:.")

/** We remove the !'s as those indicate that we expect parsing not to detect. Then split on others. */
private fun splitExample(text: String): List<Segment> =
    text.replace("!", "").split(splitExampleRe).mapNotNull(::guessSegment)

/** Use the heuristic that the second character in the segment is the type. */
private fun guessSegment(part: String): Segment? {
    val chars = decodeUtf16Iter(part).iterator()
    if (!chars.hasNext()) {
        return null
    }
    var char = chars.next()
    if (chars.hasNext()) {
        char = chars.next()
    }
    return char.getToken().seg(part)
}

private fun parseMdTable(text: String): List<Map<String, String>> {
    val headers = mutableMapOf<Int, String>()
    var headersDone = false
    return buildList {
        text.lines().forEach { line ->
            val cells = line.split('|')
            if (cells.any { "---" in it }) {
                headersDone = true
            } else if (cells.size > 1 && headersDone) {
                this.add(
                    buildMap {
                        cells.forEachIndexed { n, txt ->
                            if (txt.isNotBlank()) {
                                headers[n]?.let { hdr -> this[hdr] = txt.trim() }
                            }
                        }
                    },
                )
            } else if (cells.size > 1) {
                cells.forEachIndexed { n, txt ->
                    if (txt.isNotBlank()) {
                        headers[n] = txt.trim()
                    }
                }
            }
        }
    }
}

expect fun loadTextResource(first: String, vararg rest: String): String?
