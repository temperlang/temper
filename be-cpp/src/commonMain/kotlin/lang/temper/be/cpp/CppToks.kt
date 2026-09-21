package lang.temper.be.cpp

import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation

data object CppToks {
    val hashDefine = OutputToken("#define", OutputTokenType.Word)
    val hashElif = OutputToken("#elif", OutputTokenType.Word)
    val hashElse = OutputToken("#else", OutputTokenType.Word)
    val hashEndif = OutputToken("#endif", OutputTokenType.Word)
    val hashIf = OutputToken("#if", OutputTokenType.Word)
    val hashInclude = OutputToken("#include", OutputTokenType.Word)
    val hashPragma = OutputToken("#pragma", OutputTokenType.Word)
    val hashUndef = OutputToken("#undef", OutputTokenType.Word)

    val leftAngle = OutToks.leftAngle
    val rightAngle = OutToks.rightAngle

    /** Postfix star attaches to the left, like `int*`. */
    val postfixStar = OutputToken("*", OutputTokenType.Punctuation, TokenAssociation.Postfix)

    /** Prefix amp attaches to the right, like `&x`. */
    val prefixAmp = OutputToken("&", OutputTokenType.Punctuation, TokenAssociation.Prefix)

    /** Postfix amp attaches to the left, like `int&`. */
    val postfixAmp = OutputToken("&", OutputTokenType.Punctuation, TokenAssociation.Postfix)

    /** Postfix colon attaches to the left, like `case 123:`. */
    val postfixColon = OutputToken(":", OutputTokenType.Punctuation, TokenAssociation.Postfix)

    /** Infix colon is in the middle, like `class Sub : Super`. */
    val infixColon = OutputToken(":", OutputTokenType.Punctuation, TokenAssociation.Infix)

    /** Colons are specially recognized as tight separators like `.`: no spaces in `a.b.c` nor in `A::B::C`. */
    val colons = OutputToken("::", OutputTokenType.Punctuation, TokenAssociation.Infix)

    /**
     * Thin arrows to separate inputs from return types are infix and surrounded by spaces
     * like `) -> RETURN_TYPE`, and unlike arrows used to dereference like `subject->method(...)`.
     */
    val returnTypeArrow = OutputToken("->", OutputTokenType.Punctuation, TokenAssociation.Infix)
}
