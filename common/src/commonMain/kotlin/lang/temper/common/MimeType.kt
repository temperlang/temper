package lang.temper.common

data class MimeType(val major: String, val minor: String) {
    override fun toString(): String = "$major/$minor"

    companion object {
        /**
         * `application/json`.  See [isJson] for a predicate that covers other mime types with
         * JSON-formatted content.
         */
        val json = MimeType("application", "json")
        val markdown = MimeType("text", "markdown")

        /** Non-standard. */
        val kotlinSource = MimeType("text", "x-kotlin")
        val textPlain = MimeType("text", "plain")
        val svg = MimeType("image", "svg+xml")
        val luaSource = MimeType("text", "x-lua")
        val javascript = MimeType("text", "javascript")
        val javascriptApp = MimeType("application", "javascript")
        val cppSource = MimeType("text", "x-c")
        val makefileSource = MimeType("text", "x-makefile")

        fun parse(s: String): RResult<MimeType, String> {
            val parts = s.split('/')
            if (parts.size != 2) {
                return RFailure("Invalid mime type `$s`, expected major/minor")
            }
            val (major, minor) = parts
            if (major.isEmpty() || minor.isEmpty()) {
                return RFailure("Empty part in mime type `$s`")
            }
            if (major.length > MAX_RESTRICTED_NAME_LEN || minor.length > MAX_RESTRICTED_NAME_LEN) {
                return if (major.length > MAX_RESTRICTED_NAME_LEN) {
                    RFailure("Invalid mime type `$s`, major part is too long")
                } else {
                    RFailure("Invalid mime type `$s`, minor part is too long")
                }
            }
            if (!mimeTypeRestrictedName.matches(major)) {
                return RFailure("Invalid mime type `$s`, major part is not a valid restricted-name")
            } else if (!mimeTypeRestrictedName.matches(minor)) {
                return RFailure("Invalid mime type `$s`, minor part is not a valid restricted-name")
            }
            return RSuccess(MimeType(major, minor))
        }
    }
}

/**
 * From RFC 6838.
 *
 * > Type and subtype names MUST conform to the following ABNF:
 * >
 * >     type-name = restricted-name
 * >     subtype-name = restricted-name
 * >
 * >     restricted-name = restricted-name-first *126restricted-name-chars
 * >     restricted-name-first  = ALPHA / DIGIT
 * >     restricted-name-chars  = ALPHA / DIGIT / "!" / "#" /
 * >                              "$" / "&" / "-" / "^" / "_"
 * >     restricted-name-chars =/ "." ; Characters before first dot always
 * >                                  ; specify a facet name
 * >     restricted-name-chars =/ "+" ; Characters after last plus always
 * >                                  ; specify a structured syntax suffix
 * >
 * > Note that this syntax is somewhat more restrictive than what is
 * > allowed by the ABNF in Section 5.1 of [RFC2045] or Section 4.2 of
 * > [RFC4288].  Also note that while this syntax allows names of up to
 * > 127 characters, implementation limits may make such long names
 * > problematic.  For this reason, <type-name> and <subtype-name> SHOULD
 * > be limited to 64 characters.
 */
private val mimeTypeRestrictedName = Regex("""[A-Za-z0-9][A-Za-z0-9!#$&\-^_.+]*""")

private const val MAX_RESTRICTED_NAME_LEN = 64
