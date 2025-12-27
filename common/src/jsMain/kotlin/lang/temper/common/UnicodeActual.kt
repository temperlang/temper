package lang.temper.common

private val normalizeFn = eval("String.prototype.normalize")

actual fun normalize(s: String, goal: UnicodeNormalForm): String =
    "${ normalizeFn.call(s, goal.name) }"

private val codePointAtFn = eval("String.prototype.codePointAt")

// There's a heisenbug I've not yet found.
// I think it may be related to comparing character values to
// ranges whose endpoints are surrogates.
private const val USE_NATIVE_CODEPOINT_AT = false

actual fun decodeUtf16(s: String, i: Int): Int {
    @Suppress("ConstantConditionIf")
    return if (USE_NATIVE_CODEPOINT_AT) {
        codePointAtFn.call(s, i) as Int
    } else {
        decodeUtf16(s as CharSequence, i)
    }
}

@Suppress("MagicNumber")
actual fun decodeUtf16(cs: CharSequence, i: Int): Int {
    val c = cs[i].code
    if (c in 0xD800..0xDBFF && i + 1 < cs.length) {
        val d = cs[i + 1].code
        if (d in 0xDC00..0xDFFF) {
            val y = c and 0x3ff
            val x = d and 0x3ff
            return 0x10000 + ((y shl 10) or x)
        }
    }
    return c
}

/**
 * Iterate similarly to stepping through `.codePointAt`.
 */
actual fun decodeUtf16Iter(s: String): Iterable<Int> {
    val n = s.length
    return object : Iterable<Int> {
        override fun iterator(): Iterator<Int> {
            return object : Iterator<Int> {
                var i = 0
                override fun hasNext(): Boolean = i < n

                override fun next(): Int {
                    if (i >= n) {
                        throw NoSuchElementException()
                    }
                    val c: Int = decodeUtf16(s, i)
                    i += charCount(c)
                    return c
                }
            }
        }
    }
}

actual val (Int).charCategory: CharCategory
    get() {
        if (this in 0 until MIN_SUPPLEMENTAL_CP) {
            return this.toChar().category
        }
        if (this !in MIN_SUPPLEMENTAL_CP..C_MAX_CODEPOINT) {
            return CharCategory.UNASSIGNED
        }
        val match = categoryRegex.matchEntire(this.codePointString())
            ?: return CharCategory.UNASSIGNED
        // Group indices start at 1 so subtract 1
        val matchingCategory = match.groupValues.indexOfLast { it.isNotEmpty() } - 1
        return categories.getOrNull(matchingCategory) ?: CharCategory.UNASSIGNED
    }

private val categories = CharCategory.values()

private val categoryRegex = Regex(
    // Create a regex with a capturing group over a regex class for each category.
    // like
    //     ([\p{Ll}])|([\p{Lu}])|...
    categories.joinToString("|") { category ->
        "([\\p{${category.code}}])"
    },
)
