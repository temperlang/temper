package lang.temper.docgen.anticorruption

import lang.temper.docgen.SimpleCodeFragment
import lang.temper.name.LanguageLabel

/**
 * Facade through which the doc-gen system will interact with the main portion of the compiler
 */
interface Compiler {
    /**
     * Attempts to compile the provided fragments to the various target languages.
     * The provided fragments should be believed to compose one logical piece of contiguous code
     *
     * This attempts to cover multiple types of structures. Some non-obvious cases
     *
     *  - A class is split across two different code blocks with prose containing an escaped identifier in between
     *  - Lines from a sample program broken up with longer comments with links to API docs for code snippets
     *  - code snippets with limited context for method dispatch e.g. `foo.bar()` where the type of foo is inferable
     *  from context but not in the code
     */
    fun <T : SimpleCodeFragment> compile(fragments: List<T>): CompilationResult<T>
}

/**
 * @param preamble Any code that goes at the beginning of a code sample to make it complete
 * @param epilogue Any code that goes at the end of a code sample to make it complete
 */
data class Envelope(val preamble: String, val epilogue: String)

/**
 * @param replacements The per fragment, per backend replacement strings for the user visible portion of the code block
 * @param envelope Everything to turn the visible pieces in replacement into a full piece of code
 */
data class ResultingCode<T : SimpleCodeFragment>(
    val envelope: Map<LanguageLabel, Envelope>,
    val replacements: Map<T, Map<LanguageLabel, String>>,
) {
    init {
        // Sanity check that all the fragments have the same backends
        val backends = replacements.values.map { it.keys }
        assert(backends.all { backends.first() == it })
    }
}

/**
 * @param returnedFragments Any fragments that were not compiled, but might be compilable in a different context.
 * This could be something like a fragment that is just an identifier and needed no translation, or it could be half
 * of a class that needs the other half to compile correctly
 * @param errors any messages the compiler provided to help explain why it couldn't compile some fragment
 *
 * **Note**: The fragments in [returnedFragments] and [resultingCode] are not required to be all
 * fragments given to the compiler
 */
data class CompilationResult<T : SimpleCodeFragment>(
    val returnedFragments: List<T>,
    val resultingCode: ResultingCode<T>,
    val errors: List<String>,
) {
    init {
        assert(resultingCode.replacements.keys.intersect(returnedFragments.toSet()).isEmpty())
    }
}
