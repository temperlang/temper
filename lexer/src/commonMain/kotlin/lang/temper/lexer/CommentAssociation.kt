package lang.temper.lexer

import lang.temper.common.LeftOrRight

/**
 * Whether a comment associates with the lexically-following declaration or
 * the lexically-preceding declaration or none at all.
 *
 *     /**
 *      * This kind of comment always associates with the next declaration.
 *      */
 *
 *     /* This associates with none. */
 *
 *     // This associates with none either.
 *
 *     let f(
 *       x: Int,  /// This should (TODO) associate with the lexically-preceding
 *                /// `x` because it is not the first comment in the line.
 *     )
 *
 *     /// Runs of `///` comments on adjacent lines should (TODO) associate
 *     /// with the lexically-following declaration if the first line has no
 *     /// non-whitespace tokens on it.
 *     let foo;
 *
 * In Markdown,
 *
 *     Here is a markdown paragraph
 *
 *     ↓ This paragraph and following, because of the arrow starting the
 *     paragraph, should (TODO) associate with the lexically-following
 *     declaration.
 *     And the arrow is stripped from the front of the paragraph.
 *
 *         let iHaveADocumentationComment() {}
 *
 *         let meToo() {}
 *
 *     ↑ This paragraph and following, similarly should (TODO) associate with
 *     the lexically-preceding declaration, `meToo`.
 *
 *  @return [LeftOrRight.Left] if the token is a comment token that associates
 *  with the **lexically-preceding** declaration.
 *  [LeftOrRight.Right] if the token is a comment token that associates
 *  with the **lexically-following** declaration.
 *  `null` otherwise.
 */
fun commentAssociation(commentText: String, commentType: CommentType): LeftOrRight? =
    when (commentType) {
        CommentType.Block -> {
            if (commentText.startsWith("/**")) {
                LeftOrRight.Right
            } else {
                null
            }
        }
        CommentType.SemilitParagraph -> LeftOrRight.Right
        CommentType.Line -> null
        CommentType.Semilit -> null
    }

// TODO: Make sure that runs of `//` tokens on adjacent lines are grouped together,
// probably in the TokenAdapter.

// TODO: Make sure we are not ambiguous about Markdown like the below, potentially
// confusing CommentType.Semilit for CommentType.Line based on prefix cues.
//
//     # Some Markdown
//     Here's a paragraph.  It's followed by some code.
//         someCode()
//     // This is not a code comment, but it might be textually indistinguishable
//         // if not for a different token type.

// TODO: In Markdown mode, have either the Lexer or TokenAdapter split
// CommentType.semilit comments based on the arrow indicators above, so we can
// associate runs of paragraphs separately.
// Maybe convert ASCII like `&uarr;` and `&darr;` too.
//
//     ↑ This paragraph associates with the declaration above.
//
//     This paragraph associates above because the last one did.
//
//     ↓ This paragraph associates with the declaration below.
