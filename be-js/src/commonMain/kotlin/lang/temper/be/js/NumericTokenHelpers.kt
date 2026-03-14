package lang.temper.be.js

import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType

internal fun kotlinNumberToJsNumberToken(value: Number): OutputToken {
    val tokenText = when (value) {
        is Long -> "${value}n" // BigInt notation
        else -> "$value"
    }
    return OutputToken(tokenText, OutputTokenType.NumericValue)
}
