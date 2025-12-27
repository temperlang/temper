package lang.temper.common

const val DEFAULT_ABBREVIATE_LEN = 40

fun abbreviate(s: String, maxlen: Int = DEFAULT_ABBREVIATE_LEN): String =
    abbreviate(s as CharSequence, maxlen = maxlen) as String

fun abbreviate(cs: CharSequence, maxlen: Int = DEFAULT_ABBREVIATE_LEN): CharSequence {
    val n = cs.length
    if (n <= maxlen) {
        return cs
    }
    val sb = StringBuilder(maxlen)
    var leftOfElided = maxlen / 2
    if (leftOfElided > 0 && cs[leftOfElided - 1] in '\uD800'..'\uDBFF') {
        leftOfElided -= 1
    }
    var rightOfElided = n - leftOfElided - 1
    if (rightOfElided < n && cs[rightOfElided] in '\uDC00'..'\uDFFF') {
        rightOfElided += 1
    }
    sb.append(cs, 0, leftOfElided)
    sb.append(ABBREVIATED_REGION_PLACEHOLDER)
    sb.append(cs, rightOfElided, n)
    return sb.toString()
}

private const val ABBREVIATED_REGION_PLACEHOLDER = "\u22EF" // Middle horizontal ellipses
