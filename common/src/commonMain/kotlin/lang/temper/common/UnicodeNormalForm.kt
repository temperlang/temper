package lang.temper.common

enum class UnicodeNormalForm {
    NFC,
    NFD,
    NFKC,
    NFKD,
    ;

    operator fun invoke(s: String): String = normalize(s, this)
}

internal expect fun normalize(s: String, goal: UnicodeNormalForm): String
