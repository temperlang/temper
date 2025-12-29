package lang.temper.name

import lang.temper.lexer.Genre

/** Allows type safe creation of names by macros. */
sealed class NameMaker(
    val namingContext: NamingContext,
    /** The genre for the document.  We fail hard on temporary creation in [Genre.Documentation] */
    val genre: Genre,
) {
    /** Converts a parsed name as allowed. */
    abstract fun parsedName(name: ParsedName): ParsedName?
    // Factories for names.

    /**
     * Best effort to create a parsed name.
     * May return null after the [syntax stage][lang.temper.stage.Stage.SyntaxMacro].
     */
    fun parsedName(nameText: String): ParsedName? = parsedName(ParsedName(nameText))
    fun builtinName(builtinKey: String) = BuiltinName(builtinKey)
    fun unusedSourceName(baseName: ParsedName) = SourceName(namingContext, baseName)
    fun unusedTemporaryName(nameHint: String): Temporary {
        if (genre == Genre.Documentation) {
            throw UnsupportedOperationException("Temporary($nameHint) in genre ${genre.name}")
        }
        return Temporary(namingContext, nameHint)
    }
}

class ParsedNameMaker(
    namingContext: NamingContext,
    genre: Genre,
) : NameMaker(namingContext, genre) {
    override fun parsedName(name: ParsedName) = name
}

class ResolvedNameMaker(
    namingContext: NamingContext,
    genre: Genre,
) : NameMaker(namingContext, genre) {
    override fun parsedName(name: ParsedName) = null
}

fun NameMaker.unusedAnalogueFor(name: InternalModularName) = when (name) {
    is SourceName -> unusedSourceName(name.baseName)
    is Temporary -> unusedTemporaryName(name.nameHint)
}
