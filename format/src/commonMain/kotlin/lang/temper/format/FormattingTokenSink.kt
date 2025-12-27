package lang.temper.format

/** A TokenSink that adds tokens for indents and line breaks before delegating to [out]. */
interface FormattingTokenSink : TokenSink {
    val out: TokenSink
    val formattingHints: FormattingHints
}
