package lang.temper.lexer

/**
 * Strips non-comment context from slash-slash style line comments.
 *
 * This recognizes several comment styles
 *
 * ```
 * // A line comment
 * //// A wide line comment
 *
 * //////////////////////////////////////
 * The above is sometime used as a content-less horizontal rule.
 * ```
 */
fun lineCommentContent(text: String): String = unwrapCommentContent(
    text,
    firstLineDelimiter = slashSlashFirstLineDelimiter,
    lastLineDelimiter = slashSlashLastLineDelimiter,
    allowedCommonLinePrefix = slashSlashAllowedCommonLinePrefix,
    allowedCommonLineSuffix = slashSlashAllowedCommonLineSuffix,
)

// IF EDITS ARE NEEDED TO THESE, PLEASE ALSO SEE THE CORRESPONDING PATTERNS
// IN `blockCommentContent.kt`.
private val slashSlashFirstLineDelimiter = Regex("""^//+ ?""")
private val slashSlashLastLineDelimiter = Regex("""/+$""")
private val slashSlashAllowedCommonLinePrefix = Regex("""^[ \t]*(?:/+ ?)?""")
private val slashSlashAllowedCommonLineSuffix = Regex("""/*$""")
