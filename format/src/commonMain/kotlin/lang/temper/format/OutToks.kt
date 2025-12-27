package lang.temper.format

import lang.temper.format.TokenAssociation.Bracket
import lang.temper.format.TokenAssociation.Infix
import lang.temper.format.TokenAssociation.Postfix
import lang.temper.format.TokenAssociation.Prefix

object OutToks {
    val oneSpace = OutputToken(" ", OutputTokenType.Space)

    val leftCurly = OutputToken("{", OutputTokenType.Punctuation, Bracket)
    val rightCurly = OutputToken("}", OutputTokenType.Punctuation, Bracket)
    val leftSquare = OutputToken("[", OutputTokenType.Punctuation, Bracket)
    val rightSquare = OutputToken("]", OutputTokenType.Punctuation, Bracket)
    val leftParen = OutputToken("(", OutputTokenType.Punctuation, Bracket)
    val leftEscParen = OutputToken("\\(", OutputTokenType.Punctuation, Bracket)
    val rightParen = OutputToken(")", OutputTokenType.Punctuation, Bracket)
    val leftAngle = OutputToken("<", OutputTokenType.Punctuation, Bracket)
    val rightAngle = OutputToken(">", OutputTokenType.Punctuation, Bracket)

    val semi = OutputToken(";", OutputTokenType.Punctuation)
    val semiSemi = OutputToken(";;", OutputTokenType.Punctuation)
    val bar = OutputToken("|", OutputTokenType.Punctuation, Infix)
    val barBar = OutputToken("||", OutputTokenType.Punctuation, Infix)
    val amp = OutputToken("&", OutputTokenType.Punctuation, Infix)
    val ampAmp = OutputToken("&&", OutputTokenType.Punctuation, Infix)
    val comma = OutputToken(",", OutputTokenType.Punctuation)
    val prefixStar = OutputToken("*", OutputTokenType.Punctuation, Prefix)
    val dot = OutputToken(".", OutputTokenType.Punctuation)
    val dotDot = OutputToken("..", OutputTokenType.Punctuation)
    val ellipses = OutputToken("...", OutputTokenType.Punctuation)
    val prefixEllipses = OutputToken("...", OutputTokenType.Punctuation, Prefix)
    val wtfToken = OutputToken("???", OutputTokenType.Punctuation)
    val colon = OutputToken(":", OutputTokenType.Punctuation) // Intentionally not infix
    val infixColon = OutputToken(":", OutputTokenType.Punctuation, Infix)
    val eq = OutputToken("=", OutputTokenType.Punctuation, Infix)
    val eqEq = OutputToken("==", OutputTokenType.Punctuation, Infix)
    val at = OutputToken("@", OutputTokenType.Punctuation)
    val prefixBang = OutputToken("!", OutputTokenType.Punctuation, Prefix)
    val postfixBang = OutputToken("!", OutputTokenType.Punctuation, Postfix)
    val postfixQMark = OutputToken("?", OutputTokenType.Punctuation, Postfix)

    val asWord = OutputToken("as", OutputTokenType.Word)
    val breakWord = OutputToken("break", OutputTokenType.Word)
    val classWord = OutputToken("class", OutputTokenType.Word)
    val continueWord = OutputToken("continue", OutputTokenType.Word)
    val catchWord = OutputToken("catch", OutputTokenType.Word)
    val closRecWord = OutputToken("closRec", OutputTokenType.Word)
    val doWord = OutputToken("do", OutputTokenType.Word)
    val elseWord = OutputToken("else", OutputTokenType.Word)
    val extendsWord = OutputToken("extends", OutputTokenType.Word)
    val failWord = OutputToken("fail", OutputTokenType.Word)
    val falseWord = OutputToken("false", OutputTokenType.Word)
    val finallyWord = OutputToken("finally", OutputTokenType.Word)
    val forWord = OutputToken("for", OutputTokenType.Word)
    val fnWord = OutputToken("fn", OutputTokenType.Word)
    val getWord = OutputToken("get", OutputTokenType.Word)
    val ifWord = OutputToken("if", OutputTokenType.Word)
    val inconceivableWord = OutputToken("inconceivable", OutputTokenType.Word)
    val letWord = OutputToken("let", OutputTokenType.Word)
    val notYetWord = OutputToken("notYet", OutputTokenType.Word)
    val nullWord = OutputToken("null", OutputTokenType.Word)
    val nullTypeWord = OutputToken("Null", OutputTokenType.Word)
    val orElseWord = OutputToken("orelse", OutputTokenType.Word)
    val returnWord = OutputToken("return", OutputTokenType.Word)
    val setWord = OutputToken("set", OutputTokenType.Word)
    val staticWord = OutputToken("static", OutputTokenType.Word)
    val throwsWord = OutputToken("throws", OutputTokenType.Word)
    val trueWord = OutputToken("true", OutputTokenType.Word)
    val typeWord = OutputToken("type", OutputTokenType.Word)
    val varWord = OutputToken("var", OutputTokenType.Word)
    val voidWord = OutputToken("void", OutputTokenType.Word)
    val whenWord = OutputToken("when", OutputTokenType.Word)
    val whileWord = OutputToken("while", OutputTokenType.Word)

    val topWord = OutputToken("Top", OutputTokenType.Word)
    val neverWord = OutputToken("Never", OutputTokenType.Word)
    val bubbleWord = OutputToken("Bubble", OutputTokenType.Word)
    val invalidWord = OutputToken("Invalid", OutputTokenType.Word)

    val uninitializedInfiniBindingCommentToken =
        OutputToken("/* Uninitialized InfiniBinding */", OutputTokenType.Comment)
    val newCommentToken = OutputToken("/*new*/", OutputTokenType.Comment)

    val macroDisplayName = OutputToken("\uD835\uDD92", OutputTokenType.OtherValue)
    val functionDisplayName = OutputToken("ƒ", OutputTokenType.OtherValue)
    val timesDisplayName = OutputToken("×", OutputTokenType.Punctuation, Infix)

    val rArrow = OutputToken("->", OutputTokenType.Punctuation, Infix)
    val rWideArrow = OutputToken("=>", OutputTokenType.Punctuation, Infix)
}
