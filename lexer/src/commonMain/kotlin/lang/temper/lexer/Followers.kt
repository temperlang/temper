package lang.temper.lexer

interface Followers {
    fun mayFollowAtOperandIndex(i: Int, tokenText: String, tokenType: TokenType): Boolean

    /** Iterates over the token texts for which [mayFollowAtOperandIndex] may be true. */
    val followingTexts: Iterable<String>

    object None : Followers {
        override fun mayFollowAtOperandIndex(
            i: Int,
            tokenText: String,
            tokenType: TokenType,
        ): Boolean = false

        override val followingTexts: Iterable<String> get() = emptyList()
    }

    companion object {
        fun of(vararg tokenTexts: String): Followers = FiniteFollowers(tokenTexts.toList())
    }
}

private data class FiniteFollowers(val tokenTexts: List<String>) : Followers {
    override fun mayFollowAtOperandIndex(i: Int, tokenText: String, tokenType: TokenType): Boolean {
        return tokenText == tokenTexts.getOrNull(i)
    }

    override val followingTexts: Iterable<String> get() = tokenTexts
}
