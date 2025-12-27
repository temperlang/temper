package lang.temper.tooling

import lang.temper.common.C_LT
import lang.temper.common.decodeUtf16
import lang.temper.lexer.IdParts
import lang.temper.lexer.LanguageConfig
import lang.temper.lexer.Lexer
import lang.temper.lexer.TokenType
import lang.temper.lexer.reservedWords
import lang.temper.lexer.sourceRangeOf
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.LogSink

data class ToolToken(val kind: TokenKind, val mods: Set<TokenModifier>, val range: IntRange)

/**
 * Directly adopt LSP semantic token types since that's what we care about for now. We can add other token types if
 * needed, and this saves the effort of inventing our own schema just to adapt to LSP.
 *
 * https://code.visualstudio.com/api/language-extensions/semantic-highlight-guide#standard-token-types-and-modifiers
 */
enum class TokenKind(
    /** A distinct string used to represent this in editor themes. */
    val externalIdentifier: kotlin.String,
) {
    // Standard VSCode semantic token types.

    /** For identifiers that declare or reference a namespace, module, or package. */
    Namespace("namespace"),

    /** For identifiers that declare or reference a class type. */
    Class("class"),

    /** For identifiers that declare or reference an enumeration type. */
    Enum("enum"),

    /** For identifiers that declare or reference an interface type. */
    Interface("interface"),

    /** For identifiers that declare or reference a struct type. */
    Struct("struct"),

    /** For identifiers that declare or reference a type parameter. */
    TypeParameter("typeParameter"),

    /** For identifiers that declare or reference a type that is not covered above. */
    Type("type"),

    /** For identifiers that declare or reference a function or method parameters. */
    Parameter("parameter"),

    /** For identifiers that declare or reference a local or global variable. */
    Variable("variable"),

    /** For identifiers that declare or reference a member property, member field, or member variable. */
    Property("property"),

    /** For identifiers that declare an enumeration property, constant, or member. */
    EnumMember("enumMember"),

    /** For identifiers that declare an event property. */
    Event("event"),

    /** For identifiers that declare a function. */
    Function("function"),

    /** For identifiers that declare a member function or method. */
    Method("method"),

    /** For identifiers that declare a macro. */
    Macro("macro"),

    /** For identifiers that declare a label. */
    Label("label"),

    /** For tokens that represent a comment. */
    Comment("comment"),

    /** For tokens that represent a string literal. */
    String("string"),

    /** For tokens that represent a language keyword. */
    Keyword("keyword"),

    /** For tokens that represent a number literal. */
    Number("number"),

    /** For tokens that represent a regular expression literal. */
    Regexp("regexp"),

    /** For tokens that represent an operator. */
    Operator("operator"),

    // Non-standard token types.

    /** Not a VSCode standard token type. For tokens that are malformed. */
    Error("error"),

    /** Not a VSCode standard token type. For intermediate processing where wanted. Not for sending to client. */
    Space("space"),
}

enum class TokenModifier(
    /** A distinct string used to represent this in editor themes. */
    val externalIdentifier: String,
) {
    // Unless otherwise noted, all of these are from the VSCode standard list.

    /** For declarations of symbols. */
    Declaration("declaration"),

    /** For definitions of symbols. */
    Definition("definition"),

    /** For readonly variables and member fields (constants). */
    Readonly("readonly"),

    /** For class members (static members). */
    Static("static"),

    /** For symbols that should no longer be used. */
    Deprecated("deprecated"),

    /** For types and member functions that are abstract. */
    Abstract("abstract"),

    /** For functions that are marked async. */
    Async("async"),

    /** For variable references where the variable is assigned to. */
    Modification("modification"),

    /** For occurrences of symbols in documentation. */
    Documentation("documentation"),

    /** For symbols that are part of the standard library. */
    DefaultLibrary("defaultLibrary"),
}

/**
 * Build a sequence of [ToolToken] from the [Lexer]. This currently services orphan temper files, but we might not need
 * it once we process orphan files more fully.
 */
fun sequenceToolTokens(codeLocation: CodeLocation, content: CharSequence, lang: LanguageConfig) = sequence {
    // Could make a plan to report errors here, but we hope to more fully process even orphan files in the future.
    val logSink = LogSink.devNull
    val lexer = Lexer(codeLocation = codeLocation, logSink = logSink, sourceText = content, lang = lang)
    for (token in lexer) {
        if (token.synthetic) { continue }
        val range = lexer.sourceRangeOf(token)
        val currentTokenStart = range.first
        val currentTokenEnd = range.last + 1
        val kind = when (token.tokenType) {
            TokenType.Space -> continue
            // Actually produce things from here down.
            TokenType.Comment -> TokenKind.Comment
            TokenType.Number -> TokenKind.Number
            TokenType.Punctuation -> if (
                currentTokenEnd - currentTokenStart == 1 &&
                mayBeAngleBracket(content[currentTokenStart]) &&
                token.mayBracket
            ) {
                // Style `<` and `>` differently when they're used as angle brackets from
                // when they're used as comparison operators.
                TokenKind.Keyword // Meh.
            } else {
                TokenKind.Operator
            }
            TokenType.LeftDelimiter,
            TokenType.RightDelimiter,
            -> TokenKind.String
            TokenType.QuotedString -> if (content[currentTokenStart] != '/') {
                TokenKind.String
            } else {
                // TODO(mikesamuel): This isn't right.  A '/' in "/" shouldn't mark the string chunk as regexp.
                // Make sure that we have delimiters for regex literals and change this loop to keep a stack of
                // current delimiters.
                TokenKind.Regexp
            }
            TokenType.Word -> TokenKind.Variable
            TokenType.Error -> TokenKind.Error
        }
        yield(ToolToken(kind = kind, mods = emptySet(), range = range))
    }
}

private fun mayBeAngleBracket(c: Char) = C_LT == (c.code and 2.inv())

// TODO(tjp, tooling): Copied from `GenerativeGrammar`. Should we make it more general?
val reservedKeywords = setOf(
    "new", "in", "instanceof", "is", "while", "do", "return", "throw", "yield", "else",
    "finally", "catch", "default", "out", "as", "get", "set",
    // Things not in `GenerativeGrammar`.
    "class", "export", "extends", "fn", "if", "interface", "let", "public", "var",
) + reservedWords

fun ModuleData.sequenceToolTokens(filePath: FilePath, additiveOnly: Boolean = false) = sequence {
    var end = 0
    val tree = treeMap.getValue(filePath)
    tree.recurse().filter { it.kids.isEmpty() }.forEach leaf@{ leaf ->
        // Always move forward, and some error cases cause overlapping leaves.
        leaf.pos.left < end && return@leaf
        // Moving forward.
        // Additive means it's something we can't easily see in the client grammar.
        // We can tune this later for different client capabilities, but for now, we have only one such case in vscode.
        var additive = false
        var mods = emptySet<TokenModifier>()
        val kind = when {
            leaf.pos.size == 0 -> null
            leaf.isMention -> when { // here leaf.name != null
                leaf.isMutant && leaf.name == "init" -> TokenKind.Operator // "="
                leaf.isMutant && leaf.name == "super" -> TokenKind.Keyword // "extends"
                leaf.isSym -> TokenKind.Variable
                leaf.isDef -> {
                    additive = leaf.text in reservedKeywords
                    mods = setOf(TokenModifier.Definition)
                    TokenKind.Variable
                }
                leaf.name == leaf.text -> when {
                    leaf.name in reservedKeywords -> TokenKind.Keyword
                    decodeUtf16(leaf.name!!, 0) !in IdParts.Start -> TokenKind.Operator
                    else -> {
                        // TODO(tjp, tooling): Do we plan to put common names (like String) in the client grammar?
                        // TODO(tjp, tooling): Those plans will affect what's additive here or not.
                        mods = setOf(TokenModifier.DefaultLibrary)
                        TokenKind.Variable
                    }
                }
                // TODO(tjp, tooling): Indicate types, methods, properties, const, ...
                else -> {
                    // We might have thought this was a keyword, but we'd be wrong.
                    additive = leaf.text in reservedKeywords
                    TokenKind.Variable
                }
            }
            leaf.value is String -> TokenKind.String
            leaf.value is Number -> TokenKind.Number
            else -> null
        } ?: return@leaf
        val range = leaf.pos.left until leaf.pos.right
        if (additive || !additiveOnly) {
            yield(ToolToken(kind = kind, mods = mods, range = range))
        }
        end = range.last + 1
    }
}

// TODO(tjp, tooling): Should be able to infer some of the params.
fun ModuleData.sequenceComboToolTokens(
    filePath: FilePath,
    content: CharSequence,
    lang: LanguageConfig,
    additiveOnly: Boolean = false,
) = sequence tokens@{
    if (additiveOnly) {
        yieldAll(sequenceToolTokens(filePath, additiveOnly = true))
        return@tokens
    }
    val lexTokens = sequenceToolTokens(codeLocation = filePath, content = content, lang = lang).iterator()
    var lexToken = lexTokens.nextOrNull()
    val treeTokens = sequenceToolTokens(filePath).iterator()
    var treeToken = treeTokens.nextOrNull()
    val lexerWins = setOf(TokenKind.Regexp, TokenKind.String)
    while (true) {
        val token = when {
            lexToken == null && treeToken == null -> break
            lexToken != null && treeToken != null -> when {
                lexToken.range.first < treeToken.range.first -> when {
                    lexToken.kind == TokenKind.Variable && content.slice(lexToken.range) == "let" -> {
                        // Special case where tree doesn't see let but lexer does.
                        val result = lexToken.copy(kind = TokenKind.Keyword)
                        lexToken = lexTokens.nextOrNull()
                        result
                    }
                    else -> lexToken
                }
                lexToken.range.first > treeToken.range.first -> {
                    // TODO(tjp, tooling): Is this from preludes? What to do about this either way?
                    treeToken
                }
                else -> when {
                    // They're in the same place. The tree has more processing but not always more info.
                    // Lex going farther can be unterminated comments, and regexp isn't visible in tree.
                    lexToken.range.last < treeToken.range.last || lexToken.kind in lexerWins -> lexToken
                    // But most of the time, we have more info from the tree.
                    else -> treeToken
                }
            }
            lexToken != null -> lexToken
            else -> treeToken
        }!!
        // Yield and move on past anything overlapping, including the token we just used.
        yield(token)
        while (lexToken != null && lexToken.range.first <= token.range.last) {
            lexToken = lexTokens.nextOrNull()
        }
        while (treeToken != null && treeToken.range.first <= token.range.last) {
            treeToken = treeTokens.nextOrNull()
        }
    }
}

/** Why doesn't this exist in stdlib? */
fun <T> Iterator<T>.nextOrNull() = when {
    hasNext() -> next()
    else -> null
}
