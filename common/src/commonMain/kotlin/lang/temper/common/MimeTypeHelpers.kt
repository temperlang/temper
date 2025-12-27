package lang.temper.common

/**
 * True for `application/json` and other mime-types that carry JSON formatted content.
 */
val (MimeType).isJson get() = this == MimeType.json

/**
 * True for `text/markdown` and other mime-types that clearly carry markdown content.
 */
val (MimeType).isMarkdown get() = major == "text" && (minor == "markdown" || minor == "x-markdown")

val (MimeType).isTextual get() = when {
    major == "text" -> true
    this.isJson -> true
    this == MimeType.javascriptApp -> true
    else -> false
}
