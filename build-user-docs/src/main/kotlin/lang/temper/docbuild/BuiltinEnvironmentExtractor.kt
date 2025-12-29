package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.asciiLowerCase
import lang.temper.common.putMultiSet
import lang.temper.common.toStringViaBuilder
import lang.temper.format.OutputTokenType
import lang.temper.frontend.implicits.builtinEnvironment
import lang.temper.interp.EmptyEnvironment
import lang.temper.lexer.Genre
import lang.temper.log.FilePath
import lang.temper.name.BuiltinName
import lang.temper.name.TemperName
import lang.temper.value.FunctionSpecies
import lang.temper.value.InterpreterCallback
import lang.temper.value.TFunction
import lang.temper.value.TType
import lang.temper.value.Value

/** We group builtins by broad classifications. */
private enum class BuiltinDocGroup(
    val headingText: String,
) {
    Constants("Constants"),
    Specials("Special functions"),
    Functions("Functions"),
    Types("Types"),
    Macros("Macros"),
}

/** Builds a snippet listing each builtin environment variable. */
internal object BuiltinEnvironmentExtractor : SnippetExtractor() {
    override fun extractSnippets(
        from: FilePath,
        content: DocSourceContent,
        mimeType: MimeType,
        onto: MutableCollection<Snippet>,
    ) {
        if (from.segments.lastOrNull()?.fullName != "BuiltinEnvironment.kt") {
            return
        }

        val env = builtinEnvironment(EmptyEnvironment, Genre.Library)
        val builtinsGrouped = mutableMapOf<BuiltinDocGroup, MutableSet<BuiltinName>>()
        val ungroupedImplicitNames = TypeShapeExtractor.ungroupedSnippets.map { BuiltinName(it.id.parts.last()) }
        val allBuiltinNames = buildList {
            addAll(env.locallyDeclared)
            addAll(ungroupedImplicitNames)
            sortBy { name: TemperName -> name.builtinKey ?: "" }
        }
        for (name in allBuiltinNames) {
            val builtinKey = name.builtinKey ?: continue
            val value = env.get(name, InterpreterCallback.NullInterpreterCallback)
            check(value is Value<*>) { "Unreadable builtin $name" }
            val valueAsFn = TFunction.unpackOrNull(value)
            val group = when {
                value.typeTag == TType -> BuiltinDocGroup.Types
                valueAsFn == null -> BuiltinDocGroup.Constants
                valueAsFn.functionSpecies == FunctionSpecies.Special -> BuiltinDocGroup.Specials
                valueAsFn.functionSpecies == FunctionSpecies.Macro -> BuiltinDocGroup.Macros
                else -> BuiltinDocGroup.Functions
            }
            builtinsGrouped.putMultiSet(group, BuiltinName(builtinKey))
        }

        val snippetContent = toStringViaBuilder { snippetContentBuffer ->
            for (group in BuiltinDocGroup.values()) {
                val namesAsTokensSorted = (builtinsGrouped[group] ?: continue)
                    .toList()
                    .sortedWith { a, b ->
                        val aType = a.toToken(inOperatorPosition = true).type
                        val bType = b.toToken(inOperatorPosition = true).type
                        if (aType == bType) {
                            // Compare, case-insensitive first, then case sensitively.
                            // This gets us dictionary order, more or less.
                            var delta = a.builtinKey.asciiLowerCase()
                                .compareTo(b.builtinKey.asciiLowerCase())
                            if (delta == 0) {
                                // `Void` sorts before `void`
                                delta = a.builtinKey.compareTo(b.builtinKey)
                            }
                            delta
                        } else {
                            // Compare all alphabetic names before all operators
                            val aOrd = if (aType.isAlphabetic) { 0 } else { 1 }
                            val bOrd = if (bType.isAlphabetic) { 0 } else { 1 }
                            aOrd.compareTo(bOrd)
                        }
                    }

                snippetContentBuffer.append("# ${group.headingText}\n")
                namesAsTokensSorted.forEach {
                    snippetContentBuffer.append(
                        "\n$INSERTION_MARKER_CHAR ${
                            SnippetId(listOf("builtin", it.builtinKey), MD_EXTENSION)
                                .shortCanonString(withExtension = false)
                        } ${SnippetInsertionAttributeKey.IsCanonical.short}\n",
                    )
                }
            }
        }

        onto.add(
            Snippet(
                id = SnippetId(listOf("builtins"), extension = MD_EXTENSION),
                shortTitle = "Builtin Functions and Constants",
                source = from,
                sourceStartOffset = 0,
                mimeType = MimeType.markdown,
                content = TextDocContent(snippetContent),
                isIntermediate = false,
                derivation = ExtractedBy(this),
            ),
        )
    }

    override fun backPortInsertion(
        inserted: Snippet,
        priorInsertion: TextDocContent?,
        readInlined: () -> TextDocContent,
    ): RResult<TextDocContent, IllegalStateException> =
        if (priorInsertion != null) {
            RSuccess(priorInsertion)
        } else {
            RFailure(IllegalStateException(BACKPORT_ERROR_MESSAGE))
        }

    override fun backPortSnippetChange(
        snippet: Snippet,
        newContent: MarkdownContent,
        into: StringBuilder,
        problemTracker: ProblemTracker,
    ): Boolean {
        problemTracker.error(BACKPORT_ERROR_MESSAGE)
        return false
    }

    override val supportsBackPorting: Boolean = false
}

private val OutputTokenType.isAlphabetic
    get() = this == OutputTokenType.Name || this == OutputTokenType.Word

private const val BACKPORT_ERROR_MESSAGE =
    "Cannot back-port changes to the builtin environment.  ${
        ""
    }Maybe edit BuiltinEnvironment.kt or move changes into nested snippets."
