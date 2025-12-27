@file:Suppress("MaxLineLength") // long url

package lang.temper.be.csharp

import lang.temper.common.IdentityEscape
import lang.temper.common.jsonEscaper
import lang.temper.common.uPlus4Escape

// learn.microsoft.com/en-us/dotnet/csharp/language-reference/language-specification/lexical-structure#63-lexical-analysis
//
// fragment New_Line_Character
//     : '\u000D'  // carriage return
//     | '\u000A'  // line feed
//     | '\u0085'  // next line
//     | '\u2028'  // line separator
//     | '\u2029'  // paragraph separator
//     ;
val cSharpEscaper = jsonEscaper.withExtraEscapes(
    mapOf(
        '\u0085' to uPlus4Escape,
        '<' to IdentityEscape,
        '>' to IdentityEscape,
        '&' to IdentityEscape,
    ),
)

internal fun stringTokenText(value: String): String {
    return cSharpEscaper.escape(value)
}
