@file:Suppress("UNUSED_PARAMETER")

package lang.temper.name.identifiers

import lang.temper.name.identifiers.Category.Digits
import lang.temper.name.identifiers.Category.Letters
import lang.temper.name.identifiers.Tok.Caseless
import lang.temper.name.identifiers.Tok.Nil

/** Maps words to lower case, e.g. this would be used in creating a snake case identifier. */
internal fun mapLower(prior: Tok, tok: Tok, s: CharSequence): CharSequence =
    if (tok.isLetters) s.makeLower() else s

/** Maps words to upper case, e.g. this would be used in creating a constant identifier. */
internal fun mapUpper(prior: Tok, tok: Tok, s: CharSequence): CharSequence =
    if (tok.isLetters) s.makeUpper() else s

/** Maps words to camel case. Uses the prior word token to decide if a word should have a leading uppercase letter. */
internal fun mapCamel(prior: Tok, tok: Tok, s: CharSequence): CharSequence =
    if (tok.isLetters) s.makeCamel(prior.isLetters) else s

/** Maps words to pascal case, which is like camel case but is always capitalized. */
internal fun mapPascal(prior: Tok, tok: Tok, s: CharSequence): CharSequence =
    if (tok.isLetters) s.makeCamel(true) else s

/** Maps words as is. */
internal fun mapIdentity(prior: Tok, tok: Tok, s: CharSequence): CharSequence =
    s

@Suppress("FunctionOnlyReturningConstant")
internal fun delimitNull(prior: Tok, tok: Tok): String = ""

/** Generally delimit if it's in the middle. */
internal fun inMiddle(prior: Tok, tok: Tok): Boolean = prior != Nil && tok != Nil

/** Test if we have non-letter tokens at the start. */
internal fun needsLead(prior: Tok, tok: Tok): Boolean = prior == Nil && !tok.isLetters

/**
 * Rules for delimiting when the mapping forces segments to be inherently indistinct,
 * such as mapLower or mapUpper.
 *  1. the broad word tokens are the same, so LettersUpper followed by LettersLower are similar
 *  2. also delimit if digits *precede* letters
 */
internal fun shouldDelimitIndistinct(prior: Tok, tok: Tok): Boolean =
    prior.cat == tok.cat || prior.cat == Digits && tok.cat == Letters

/**
 * Rules for delimiting when the mapping forces segments to be distinct, such as mapCamel.
 *  1. two runs are the same and are not cased letters
 *  2. also delimit if digits *precede* letters
 */
internal fun shouldDelimitDistinct(prior: Tok, tok: Tok): Boolean =
    (!tok.isCasedLetters && prior == tok) || (prior.cat == Digits && tok.cat == Letters)

/**
 * Rules for delimiting when the mapping forces segments to be distinct, such as mapCamel.
 *  1. two runs are the same and are not cased letters
 *  2. also delimit if digits *precede* letters
 */
internal fun shouldDelimitCaseless(prior: Tok, tok: Tok): Boolean = tok == Caseless && prior == Caseless

internal fun delimitUnderscore(prior: Tok, tok: Tok): String =
    when {
        inMiddle(prior, tok) && shouldDelimitCaseless(prior, tok) -> "·"
        inMiddle(prior, tok) && shouldDelimitIndistinct(prior, tok) -> "_"
        needsLead(prior, tok) -> "_"
        else -> ""
    }

internal fun delimitDash(prior: Tok, tok: Tok): String =
    when {
        inMiddle(prior, tok) && shouldDelimitCaseless(prior, tok) -> "·"
        inMiddle(prior, tok) && shouldDelimitIndistinct(prior, tok) -> "-"
        needsLead(prior, tok) -> "_"
        else -> ""
    }

internal fun delimitChimeric(prior: Tok, tok: Tok): String =
    when {
        inMiddle(prior, tok) && shouldDelimitCaseless(prior, tok) -> "·"
        inMiddle(prior, tok) && shouldDelimitDistinct(prior, tok) -> "_"
        needsLead(prior, tok) -> "_"
        else -> ""
    }

internal fun delimitSpace(prior: Tok, tok: Tok): String =
    when {
        inMiddle(prior, tok) && shouldDelimitCaseless(prior, tok) -> "·"
        inMiddle(prior, tok) && shouldDelimitIndistinct(prior, tok) -> " "
        inMiddle(prior, tok) && prior.cat == Letters && tok.cat == Digits -> " "
        else -> ""
    }

internal fun CharSequence.makeLower(): String = toString().lowercase()

internal fun CharSequence.makeUpper(): String = toString().uppercase()

internal fun CharSequence.makeCamel(upper: Boolean): String = if (upper) {
    this.makeLower().replaceFirstChar { it.uppercaseChar() }
} else {
    this.makeLower()
}
