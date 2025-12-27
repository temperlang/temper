package lang.temper.format

object SpecialTokens {
    // Guillemets with the NotEmitted token type indicate indent and dedent:
    // increasing and decreasing the indentation level.
    // This is useful in languages like Python that do not always have
    // lexical indicators of bracketing.
    val indent = OutputToken("«", OutputTokenType.NotEmitted, TokenAssociation.Bracket)
    val dedent = OutputToken("»", OutputTokenType.NotEmitted, TokenAssociation.Bracket)
}
