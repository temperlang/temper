package lang.temper.format

/** A hint about how the token associates with tokens before and after. */
enum class TokenAssociation {
    /** No information about how the token associates. */
    Unknown,

    /** The token appears between two constructs so associates both left and right. */
    Infix,

    /** The token associates to the right. */
    Prefix,

    /** The token associates with the previous token. */
    Postfix,

    /** The token is part of a matched pair like `(` and `)`. */
    Bracket,
}
