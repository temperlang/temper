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
    }
}
