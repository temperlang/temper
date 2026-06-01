package lang.temper.be.py

import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation

// Tokens used from py.out-grammar for fine-grained formatting.

// Prefix prevents trailing space, even when using without args after.
val slashToken = OutputToken("/", OutputTokenType.Punctuation, TokenAssociation.Prefix)
val starToken = OutputToken("*", OutputTokenType.Punctuation, TokenAssociation.Prefix)
val starStarToken = OutputToken("**", OutputTokenType.Punctuation, TokenAssociation.Prefix)
val sliceColonToken = OutputToken(":", OutputTokenType.Punctuation, TokenAssociation.Infix)
