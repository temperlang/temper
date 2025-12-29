package lang.temper.be.java

import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation

internal object JavadocTokens {
    val open = OutputToken("/**", OutputTokenType.Comment, TokenAssociation.Bracket)
    val close = OutputToken(" */", OutputTokenType.Comment, TokenAssociation.Bracket)
}
