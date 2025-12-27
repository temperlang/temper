package lang.temper.common

fun String.splitAfterTo(pattern: Regex, out: MutableList<String>) {
    var lastSplit = 0
    for (matchResult in pattern.findAll(this)) {
        val end = matchResult.range.last + 1
        out.add(this.substring(lastSplit, end))
        lastSplit = end
    }
    out.add(this.substring(lastSplit))
}

fun String.splitAfter(pattern: Regex): List<String> {
    val string = this
    return buildList {
        string.splitAfterTo(pattern, this)
    }
}
