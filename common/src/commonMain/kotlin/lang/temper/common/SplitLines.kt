package lang.temper.common

private val crlfOrLfPattern = Regex("""\r\n?|\n""")

fun CharSequence.splitLinesPreservingTerminators(): List<String> {
    val matches = crlfOrLfPattern.findAll(this)
    var pos = 0
    var listBuilder: MutableList<String>? = null
    for (match in matches) {
        val lines = listBuilder ?: (mutableListOf<String>().also { listBuilder = it })
        val endExclusive = match.range.last + 1
        lines.add(substring(pos, endExclusive))
        pos = endExclusive
    }
    return if (listBuilder == null) {
        listOf(this.toString())
    } else {
        listBuilder.add(substring(pos, length))
        listBuilder.toList()
    }
}
