package lang.temper.langserver

import lang.temper.common.console
import lang.temper.common.jsonEscaper
import lang.temper.lexer.TEMPER_FILE_EXTENSION
import lang.temper.tooling.TokenKind
import lang.temper.tooling.TokenModifier
import lang.temper.tooling.ToolToken
import org.eclipse.lsp4j.DocumentFilter
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensServerFull
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import java.util.concurrent.atomic.AtomicBoolean

internal const val DEBUG_SEMANTIC_TOKENS = false

private val tokenKindIds = TokenKind.values().map { it.externalIdentifier }
private val tokenModifierIds = TokenModifier.values().map { it.externalIdentifier }

// See also code.visualstudio.com/api/language-extensions/semantic-highlight-guide#semantic-token-provider

enum class TokenMode {
    Add,
    Full,
    None,
}

internal val semanticTokensProviderOptions = SemanticTokensWithRegistrationOptions(
    SemanticTokensLegend(
        tokenKindIds,
        tokenModifierIds,
    ),
    SemanticTokensServerFull(true), // We can provide deltas
    /* range */
    false, // We prefer to get a full document, possibly with edits
    listOf(
        // If this changes then also change isTemperFile in //lexer/.../LanguageConfig.kt
        DocumentFilter("temper", "file", "**/*$TEMPER_FILE_EXTENSION"),
        DocumentFilter("temper", "file", "**/*$TEMPER_FILE_EXTENSION.md"),
        DocumentFilter("temper", "file", "**/*$TEMPER_FILE_EXTENSION.html"),
        // TODO: what about scheme "untitled"
    ),
)

internal fun computeSemanticTokens(
    tokens: Sequence<ToolToken>,
    content: String,
    mayUseMultilineTokens: Boolean,
    cancelled: AtomicBoolean,
): IntArray {
    val tokenListBuilder = TokenListBuilder()

    // microsoft.github.io/language-server-protocol/specifications/specification-current/#position
    // explains that both line and character offsets are zero-indexed.
    val pos = Position()

    // Track between tokens.
    var previousEnd = 0

    // Count tokens so that we don't check an atomic bool every ignored space token.
    var steps = 0

    debugSemanticTokens { "Lexing ${jsonEscaper.escape(content)}" }
    for (token in tokens) {
        if ((steps and STEP_COUNT_MASK) == STEP_COUNT_MASK && cancelled.get()) {
            break
        }
        steps += 1
        val tokenLength = token.range.last + 1 - token.range.first
        debugSemanticTokens {
            "token ${jsonEscaper.escape(content.substring(token.range))
            } @ ${token.range.first}-${token.range.last + 1} len=$tokenLength: ${token.kind}"
        }
        debugSemanticTokens { "line=${pos.line} char=${pos.character}" }

        // Scan through any skipped content token so we can update (line, pos).
        advance(pos, content = content, range = previousEnd until token.range.first)
        // Also scan through the token for where we end up afterward.
        // We store it for now instead of updating lineNo/charInLine directly
        // so that we can use the current `pos` for the coordinates when
        // we emit a token, and use `after` to fake multi-line tokens
        // for clients that do not support them.
        val after = Position(pos.line, pos.character)
        advance(after, content = content, range = token.range)

        if (token.kind != TokenKind.Space) {
            if (mayUseMultilineTokens || after.line == pos.line) {
                tokenListBuilder.addEncodedToken(
                    lineNo = pos.line,
                    charInLine = pos.character,
                    tokenLength = tokenLength,
                    externalTokenType = token.kind,
                    externalTokenMods = token.mods,
                )
            } else {
                // The client does not support multi-line tokens so we need to split multi-line
                // tokens into multiple abstract single line tokens
                var sltStartIndex = token.range.first
                var sltStartLineNo = pos.line
                var sltStartChar = pos.character
                var sltLineNo = pos.line
                var sltChar = pos.character
                for (i in token.range) {
                    // TODO(tjp, tooling): We need to know where endlines both start and end.
                    // TODO(tjp, tooling): `FilePositions` is unclear in this and currently focuses on random access.
                    val breakHere = i == token.range.last || content.lineBreakAt(i)
                    if (breakHere) {
                        tokenListBuilder.addEncodedToken(
                            lineNo = sltStartLineNo,
                            charInLine = sltStartChar,
                            tokenLength = i + 1 - sltStartIndex,
                            externalTokenType = token.kind,
                            externalTokenMods = token.mods,
                        )
                        sltLineNo += 1
                        sltStartLineNo = sltLineNo
                        sltStartChar = 0
                        sltStartIndex = i + 1
                    } else {
                        sltChar += 1
                    }
                }
            }
        }
        pos.line = after.line
        pos.character = after.character
        previousEnd = token.range.last + 1
    }

    return tokenListBuilder.integerEncodingForTokens.dataArray
}

fun advance(pos: Position, content: String, range: IntRange) {
    for (i in range) {
        if (content.lineBreakAt(i)) {
            // TODO(tjp, tooling): Does this increment twice for \r\n?
            pos.line += 1
            pos.character = 0
        } else {
            pos.character += 1
        }
    }
}

internal class TokenListBuilder {
    val integerEncodingForTokens = IntegerEncodingForTokens()
    private var lastTokenLineNo = 0
    private var lastTokenCharInLine = 0

    fun addEncodedToken(
        lineNo: Int,
        charInLine: Int,
        tokenLength: Int,
        externalTokenType: TokenKind,
        externalTokenMods: Set<TokenModifier> = emptySet(),
    ) {
        val deltaLine = lineNo - lastTokenLineNo
        val deltaStartChar = if (deltaLine == 0) {
            charInLine - lastTokenCharInLine
        } else {
            charInLine
        }

        debugSemanticTokens {
            ". deltaLine=$deltaLine deltaChar=$deltaStartChar length=$tokenLength type=${externalTokenType
            }, mods=$externalTokenMods"
        }
        integerEncodingForTokens.add(
            deltaLine = deltaLine,
            deltaStartChar = deltaStartChar,
            length = tokenLength,
            tokenType = externalTokenType,
            tokenModifiers = externalTokenMods,
        )

        lastTokenLineNo = lineNo
        lastTokenCharInLine = charInLine
    }
}

/**
 * Performs the transformation defined under "Integer Encoding for Tokens" in
 * https://microsoft.github.io/language-server-protocol/specification
 */
internal class IntegerEncodingForTokens {
    private var uintData = IntArray(INITIAL_INT_VECTOR_SIZE)
    private var used = 0

    fun add(
        deltaLine: Int,
        deltaStartChar: Int,
        length: Int,
        tokenType: TokenKind,
        tokenModifiers: Set<TokenModifier>,
    ) {
        val endAfterAdding = used + PACKED_INT_FIELD_COUNT
        if (endAfterAdding > uintData.size) {
            uintData = uintData.copyOf(newSize = Integer.max(uintData.size * 2, endAfterAdding))
        }
        uintData[used + PACKED_INT_LINE_OFFSET] = deltaLine
        uintData[used + PACKED_INT_CHAR_OFFSET] = deltaStartChar
        uintData[used + PACKED_INT_SIZE_OFFSET] = length
        uintData[used + PACKED_INT_TYPE_OFFSET] = tokenType.ordinal

        var modifierBits = 0
        tokenModifiers.forEach { modifierBits = modifierBits or (1 shl it.ordinal) }
        uintData[used + PACKED_INT_MODS_OFFSET] = modifierBits

        used += PACKED_INT_FIELD_COUNT
    }

    val dataArray get() = uintData.copyOf(newSize = used)
}

// Derived from spec linked above
private const val PACKED_INT_LINE_OFFSET = 0
private const val PACKED_INT_CHAR_OFFSET = 1
private const val PACKED_INT_SIZE_OFFSET = 2
private const val PACKED_INT_TYPE_OFFSET = 3
private const val PACKED_INT_MODS_OFFSET = 4
internal const val PACKED_INT_FIELD_COUNT = 5

/** Don't sample the cancelled bit too often in a tight loop. */
private const val STEP_COUNT_MASK = 0x3F

private const val INITIAL_INT_VECTOR_SIZE = 1024

internal inline fun debugSemanticTokens(msg: () -> String) {
    if (DEBUG_SEMANTIC_TOKENS) {
        console.info(msg())
    }
}
