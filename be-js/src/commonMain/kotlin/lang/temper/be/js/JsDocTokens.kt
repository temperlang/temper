package lang.temper.be.js

import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation

/**
 * Special tokens recognized by [CommentGroupingTokenSink] to recognize the
 * boundaries of comments that embed TypeScript type phrases.
 */
internal object JsDocTokens {
    /** passes [CommentGroupingTokenSink.isCommentOpen] */
    val commentStart = OutputToken("/**", OutputTokenType.Comment, TokenAssociation.Bracket)

    /** passes [CommentGroupingTokenSink.isCommentClose] */
    val commentEnd = OutputToken("*/", OutputTokenType.Comment, TokenAssociation.Bracket)

    /** A curly bracket token that does not cause indenting. */
    val openCurly = OutputToken("{", OutputTokenType.Comment)

    /** A curly bracket token that does not cause indenting. */
    val closeCurly = OutputToken("}", OutputTokenType.Comment)
}
