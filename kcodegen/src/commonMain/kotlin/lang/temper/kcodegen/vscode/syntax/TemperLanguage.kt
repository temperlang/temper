package lang.temper.kcodegen.vscode.syntax

import kotlin.reflect.KFunction

val language = lazy {
    Language(
        title = "Temper",
        scope = "source.temper",
        patterns = listOf(::expression.ref),
        repository = listOf(
            ::bracket,
            ::classDef,
            ::comment,
            ::def,
            ::escape,
            ::expression,
            ::interpolation,
            ::memberExpression,
            ::name,
            ::number,
            ::regexContent,
            ::regexpEscape,
            ::string,
            ::typeExpression,
            ::word,
        ).associate { it.name to it() },
    )
}

private val KFunction<Rule>.ref get() = Ref(name)

private fun bracket() = choice(
    // Brackets help for matching inside of string template expressions.
    Flat(scope = "meta.brace.square.temper", match = """\[|\]"""),
    // Nesting where we care about depth for end other nested scopes.
    Nest(
        begin = """(?<=\S)<""",
        end = """>""",
        patterns = listOf(::typeExpression.ref),
    ),
    // Round here could technically be in the header of a lambda block, so we might have formal parameters.
    round(::memberExpression.ref),
    curly(::expression.ref),
)

@Suppress("MagicNumber") // Capture group numbers.
private fun classDef() = Nest(
    begin = """\b(?:(class)|(enum)|(interface))\b""",
    beginCaptures = mapOf(
        1 to Scoped("storage.type.class.temper"),
        2 to Scoped("storage.type.enum.temper"),
        3 to Scoped("storage.type.interface.temper"),
    ),
    end = """[;\n]""",
    patterns = listOf(
        choice(
            // Customize curly and round handling.
            curly(::memberExpression.ref),
            round(::memberExpression.ref),
            // Vs type expressions generally.
            ::typeExpression.ref,
        ),
    ),
)

private fun comment() = choice(
    Nest(
        scope = "comment.block.temper",
        begin = """/\*""",
        beginCaptures = commentEdgeCaptures,
        end = """\*/""",
        endCaptures = commentEdgeCaptures,
    ),
    Nest(
        scope = "comment.line.double-slash.temper",
        begin = "//",
        beginCaptures = commentEdgeCaptures,
        end = "$",
    ),
)

private fun curly(nested: Ref) = Nest(
    begin = """\{""",
    beginCaptures = curlyEdgeCaptures,
    end = """\}""",
    endCaptures = curlyEdgeCaptures,
    patterns = listOf(nested),
)

private fun def() = Nest(
    scope = "meta.definition.variable.temper",
    begin = matchWords("const", "let", "var"),
    beginCaptures = mapOf(0 to Scoped("storage.type.temper")),
    end = """[{=;\n]""",
    patterns = listOf(
        choice(
            // Override any keyword interpretation in this context.
            ::name.ref,
            Nest(begin = "<", end = ">", patterns = listOf(::typeExpression.ref)),
            ::memberExpression.ref,
        ),
    ),
)

private fun escape() = Flat(scope = "constant.character.escape.temper", match = """\\.""")

private fun expression(): Choice = Choice(
    listOf(
        ::bracket,
        ::classDef,
        ::comment,
        ::def,
        ::number,
        ::string,
        ::word,
    ).map { it.ref },
)

private fun interpolation() = Nest(
    // Enable template expressions in this grammar for client speed and smarts.
    scope = "meta.template.expression.temper",
    begin = """\${'$'}\{""",
    beginCaptures = templateBeginCaptures,
    end = """\}""",
    endCaptures = templateEndCaptures,
    contentScope = "meta.embedded.line.temper",
    patterns = listOf(::expression.ref),
)

private fun memberExpression(): Rule {
    return choice(
        Nest(
            begin = """(?<=\S):""",
            end = SIMPLE_TYPE_END,
            patterns = listOf(::typeExpression.ref),
        ),
        ::expression.ref,
    )
}

private fun name() = choice(
    Flat(scope = "entity.name.function.temper", match = """\b$MATCH_WORD\b(?=[(<])"""),
    Flat(scope = "variable.other.readwrite.temper", match = """\b$MATCH_WORD\b"""),
)

private fun number() = choice(
    // TODO(tjp, tooling): Other types of numbers.
    Flat(scope = "constant.numeric.decimal.temper", match = """[[:digit:]]+\.?[[:digit:]]*|\.[[:digit:]]+"""),
)

private fun regexContent() = choice(
    ::regexpEscape.ref,
    Flat(scope = "keyword.operator.quantifier.regexp", match = """[+*?]"""),
    Flat(scope = "keyword.operator.or.regexp", match = """[|]"""),
    Flat(
        match = """\((?:\?($MATCH_WORD)=)?|\)""",
        captures = mapOf(
            0 to Scoped("punctuation.definition.group.regexp"),
            1 to Scoped("variable.other.regexp"),
        ),
    ),
    Nest(
        scope = "constant.other.character-class.set.regexp",
        begin = """(\[)(\^)?""",
        beginCaptures = mapOf(1 to regexpClassPunctuation, 2 to Scoped("keyword.operator.negation.regexp")),
        end = """\]""",
        endCaptures = mapOf(0 to regexpClassPunctuation),
        patterns = listOf(::regexpEscape.ref),
    ),
)

private fun regexpEscape() = choice(
    Flat(scope = "keyword.control.anchor.regexp", match = """\\b|\^|\${'$'}"""),
    Flat(scope = "constant.other.character-class.regexp", match = """\\."""),
)

private fun round(nested: Ref) = Nest(
    begin = """\(""",
    beginCaptures = roundEdgeCaptures,
    end = """\)""",
    endCaptures = roundEdgeCaptures,
    patterns = listOf(nested),
)

private fun string() = choice(
    // TODO Multiline rgx string?
    stringMultiline(),
    stringMultiline().copy(
        begin = """\b($MATCH_WORD)(""${'"'})""",
        beginCaptures = taggedStringBeginCaptures,
    ),
    taggedString().copy(
        scope = "string.regexp.temper",
        begin = """\b(rgx)(["])""",
        patterns = listOf(
            Flat(match = "\\\\\""),
            ::interpolation.ref,
            ::regexContent.ref,
        ),
    ),
    taggedString(),
    Nest(
        scope = "string.quoted.double.temper",
        begin = """["`]""",
        beginCaptures = stringBeginCaptures,
        end = """\0""",
        endCaptures = stringEndCaptures,
        patterns = listOf(
            ::escape.ref,
            ::interpolation.ref,
        ),
    ),
    Nest(
        scope = "string.quoted.single.temper",
        begin = "'",
        beginCaptures = stringBeginCaptures,
        end = "'",
        endCaptures = stringEndCaptures,
        patterns = listOf(::escape.ref),
    ),
    Nest(
        scope = "string.regexp.temper",
        // Try to distinguish regex from division.
        begin = """(?<=^\s*)/(?!\s)|(?<=[=:,;{(\[\n]\s*|${matchWords("return", "throw", "yield")}\s*)/""",
        beginCaptures = stringBeginCaptures,
        end = "(/)($MATCH_WORD)?",
        endCaptures = mapOf(1 to stringEndPunctuation, 2 to Scoped("keyword.other.temper")),
        patterns = listOf(::regexContent.ref),
    ),
)

private fun stringMultiline() = Nest(
    scope = "string.quoted.multi.temper",
    begin = "\"\"\"",
    beginCaptures = stringBeginCaptures,
    end = """(?!(\s|"|//|/\*|$))""",
    patterns = listOf(
        ::comment.ref,
        Nest(
            scope = "string.quoted.double.temper",
            begin = "\"",
            beginCaptures = stringBeginCaptures,
            end = "$",
            patterns = listOf(
                ::escape.ref,
                ::interpolation.ref,
            ),
        ),
    ),
)

private fun taggedString(): Nest {
    return Nest(
        scope = "string.quoted.double.temper",
        begin = """\b($MATCH_WORD)(["])""",
        beginCaptures = taggedStringBeginCaptures,
        end = """["]""",
        endCaptures = stringEndCaptures,
        patterns = listOf(
            // Backslash prevents end string only, but it's still raw.
            Flat(match = "\\\\\""),
            ::interpolation.ref,
        ),
    )
}

private fun typeExpression(): Rule = choice(
    // Give some keywords priority over type names
    Flat(scope = "storage.modifier.temper", match = """\s*\b(?:extends|in|out)\b"""),
    // Prioritize most words as types then support any expression.
    // For some reason, I have to include the optional preceding space here or else multi-space starts cause weird
    // effects when I also exclude space from the ending chars for types after colon.
    // Makes no sense, but rolling with it for now.
    Flat(scope = "entity.name.type.temper", match = """\s*\b$MATCH_WORD\b"""),
    round(::typeExpression.ref), // Perhaps as `f: fn (T): U` or some such.
    Nest(begin = "<", end = ">", patterns = listOf(::typeExpression.ref)),
    ::expression.ref,
)

private fun word() = choice(
    // Nym also above declaration name matches.
    Flat(
        match = """(nym)(`(?:[^`]|\\.)+`)""",
        captures = mapOf(1 to Scoped("storage.type.temper"), 2 to Scoped("variable.other.readwrite.temper")),
    ),
    Nest(
        begin = """(?<=(?:\):\s+)|(?:\bis\b\s*))""",
        end = SIMPLE_TYPE_END,
        patterns = listOf(::typeExpression.ref),
    ),
    keywords("as"),
    keywords("conditional", listOf("else", "if")),
    keywords("export"),
    keywords("flow", listOf("await", "break", "continue", "return")),
    keywords("import"),
    keywords("is"),
    keywords("loop", listOf("do", "for", "while")),
    keywords("switch", listOf("given", "when")),
    keywords("trycatch", listOf("orelse")),
    boolean("false"),
    boolean("true"),
    Flat(scope = "constant.language.null.temper", match = matchWords("null")),
    Flat(scope = "keyword.operator.expression.void.temper", match = matchWords("void")),
    Flat(scope = "keyword.operator.new", match = matchWords("new")),
    Flat(
        scope = "storage.modifier.temper",
        match = matchWords("async", "extends", "private", "public", "sealed", "static"),
    ),
    // Leave as simple keywords too for when they match in other contexts.
    Flat(scope = "storage.type.function.temper", match = matchWords("fn")),
    Flat(scope = "storage.type.property.temper", match = matchWords("get", "set")),
    Flat(scope = "variable.language.this.temper", match = matchWords("this")),
    // Function below keyword priority for nondeclarations.
    ::name.ref,
)

private const val ROUND_EDGE_SCOPE = "meta.brace.round.temper"

private val commentEdgeCaptures = mapOf(0 to Scoped("punctuation.definition.comment.temper"))
private val roundEdgeCaptures = mapOf(0 to Scoped(ROUND_EDGE_SCOPE))
private val curlyEdgeCaptures = mapOf(0 to Scoped("punctuation.define.block.temper"))
private val regexpClassPunctuation = Scoped("punctuation.definition.character-class.regexp")
private val stringBeginCaptures = mapOf(0 to Scoped("punctuation.definition.string.begin.temper"))
private val stringEndPunctuation = Scoped("punctuation.definition.string.end.temper")
private val stringEndCaptures = mapOf(0 to stringEndPunctuation)
private val taggedStringBeginCaptures = mapOf(
    1 to Scoped("entity.name.function.temper"),
    2 to Scoped("punctuation.definition.string.begin.temper"),
)
private val templateBeginCaptures = mapOf(0 to Scoped("punctuation.definition.template-expression.begin.temper"))
private val templateEndCaptures = mapOf(0 to Scoped("punctuation.definition.template-expression.end.temper"))

private fun keywords(base: String, options: List<String>? = null) = Flat(
    scope = keywordScope(base),
    match = matchWords(options ?: listOf(base)),
)

private fun boolean(base: String) = Flat(scope = "constant.language.boolean.$base.temper", match = matchWords(base))
private fun choice(vararg rules: Rule) = Choice(rules.toList())
private fun keywordScope(base: String) = "keyword.control.$base.temper"
private fun matchWords(vararg options: String) = matchWords(options.toList())
private fun matchWords(options: List<String>) = """\b(?:${options.joinToString("|")})\b"""
private const val MATCH_WORD = "[_[:alpha:]][_[:alnum:]]*"
private const val SIMPLE_TYPE_END = """(?=[>),;{}\n=:])""" // Continuing on space here lets | and & types work.
