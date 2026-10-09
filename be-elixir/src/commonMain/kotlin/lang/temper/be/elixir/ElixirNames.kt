package lang.temper.be.elixir

import lang.temper.name.ExportedName
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.name.Temporary

/** Words Elixir's parser reserves (the syntax reference's reserved words). */
private val elixirReserved = setOf(
    "after", "and", "catch", "do", "else", "end", "false", "fn", "in", "nil", "not", "or", "rescue", "true",
    "when",
)

/**
 * Names a local function may not take without breaking its unqualified calls.
 *
 * Defining `def length(x)` compiles; calling it as `length(x)` in the same
 * module does not ("imported Kernel.length/1 conflicts with local function",
 * journal/probes/07_names.exs). The list is Kernel's own
 * `__info__(:functions) ++ __info__(:macros)` on Elixir 1.19.5, plus the
 * special forms, rather than a guess at the dangerous ones.
 */
private val elixirKernel = setOf(
    "abs", "alias!", "apply", "binary_part", "binary_slice", "binding", "bit_size", "byte_size", "ceil", "dbg",
    "def", "defdelegate", "defexception", "defguard", "defguardp", "defimpl", "defmacro", "defmacrop",
    "defmodule", "defoverridable", "defp", "defprotocol", "defstruct", "destructure", "div", "elem", "exit",
    "floor", "function_exported?", "get_and_update_in", "get_in", "hd", "if", "in", "inspect", "is_atom",
    "is_binary", "is_bitstring", "is_boolean", "is_exception", "is_float", "is_function", "is_integer",
    "is_list", "is_map", "is_map_key", "is_nil", "is_non_struct_map", "is_number", "is_pid", "is_port",
    "is_reference", "is_struct", "is_tuple", "length", "macro_exported?", "make_ref", "map_size", "match?",
    "max", "min", "node", "not", "or", "pop_in", "put_elem", "put_in", "raise", "rem", "reraise", "round",
    "self", "send", "spawn", "spawn_link", "spawn_monitor", "struct", "struct!", "tap", "then", "throw", "tl",
    "to_char_list", "to_charlist", "to_string", "to_timeout", "trunc", "tuple_size", "unless", "update_in",
    "use", "var!",
    // Kernel.SpecialForms
    "alias", "case", "cond", "for", "import", "quote", "receive", "require", "super", "try", "unquote",
    "unquote_splicing", "with",
)

/** Elixir identifiers are ASCII letters, digits and underscore here; `?`/`!` suffixes are not generated. */
private val notIdentifierChar = Regex("[^A-Za-z0-9_]")

/**
 * Makes Elixir identifiers for Temper names: lowercase-initial, stable, and
 * collision-free within one module.
 *
 * - A name starting with an uppercase letter or a digit gets a `v_` prefix:
 *   Elixir reads `Foo` as a module alias, never a variable.
 * - A leading `_` is kept off, because `_x` means "unused" to the compiler
 *   and a bare `_` cannot be read at all: such names get a `u` prefix.
 * - Reserved words and Kernel names get a trailing `_`.
 */
internal class ElixirNames {
    private var gensymCount = 0

    /** How many [withLocals] are running: functions nest inside classes. */
    private var depth = 0

    /** Short names for one function's locals, while it is translated; see [withLocals]. */
    private var locals: Map<ResolvedName, String> = emptyMap()

    /** Plain names for the library's functions, values and tests; see [nameModuleLevel]. */
    private var moduleLevel: Map<ResolvedName, String> = emptyMap()

    fun outName(name: ResolvedName): OutName = OutName(locals[name] ?: moduleLevel[name] ?: identText(name), name)

    /**
     * Names the library's module functions, module values and tests plainly:
     * `moveRight`, not `moveRight__489`. The frontend's numbers count every
     * name in the library, so one new declaration renumbered every name after
     * it, and each diff of committed generated code was mostly renumbering. A
     * name the library declares more than once is numbered within its own
     * group, `fn`, `fn__2`, in declaration order, so a new declaration
     * renumbers only names it shares. Exported names are taken first.
     */
    fun nameModuleLevel(declared: List<ResolvedName>) {
        val taken = declared.filterIsInstance<ExportedName>().map { identText(it) }.toMutableSet()
        val renamed = mutableMapOf<ResolvedName, String>()
        for ((text, group) in declared.distinct().filter { it !is ExportedName }.groupBy { plainText(it) }) {
            if (text == null) continue
            var k = 1
            for (name in group) {
                var candidate = if (k == 1) text else "${text}__$k"
                while (candidate in taken) candidate = "${text}__${++k}"
                renamed[name] = candidate
                taken.add(candidate)
                k++
            }
        }
        moduleLevel = renamed
    }

    /**
     * Translates one function with its locals named plainly. A local that is
     * the only one of its name in the function is `x`, not `x__13`; names
     * declared more than once, like the frontend's temporaries `t`, are
     * numbered `t1`, `t2` in declaration order, so shadowing stays distinct.
     * Only declarations inside the function are renamed: globals and module
     * functions keep one name everywhere. [named] gives some locals a text of
     * the translator's choosing in place of their Temper name, numbered like
     * any other if a local of the function already has that text.
     */
    fun <T> withLocals(
        declared: Collection<ResolvedName>,
        named: Map<ResolvedName, String> = emptyMap(),
        body: () -> T,
    ): T {
        // gensyms count from 0 in each function, for the same reason as above
        val outerGensyms = gensymCount
        if (depth++ == 0) gensymCount = 0
        val byText = declared.distinct().groupBy { named[it] ?: plainText(it) }
        val renamed = mutableMapOf<ResolvedName, String>()
        val taken = byText.keys.filterNotNull().toMutableSet()
        for ((text, group) in byText) {
            if (text == null) continue
            if (group.size == 1) {
                renamed[group.single()] = text
                continue
            }
            var k = 1
            for (name in group) {
                while ("$text$k" in taken) k++
                renamed[name] = "$text$k"
                taken.add("$text$k")
            }
        }
        val outer = locals
        locals = renamed
        try {
            return body()
        } finally {
            locals = outer
            if (--depth == 0) gensymCount = outerGensyms
        }
    }

    /** `x` for `x__13`, `caseIndex` for `caseIndex#17`; null for a name with no plain form. */
    private fun plainText(name: ResolvedName): String? {
        val base = when (name) {
            is lang.temper.name.SourceName -> name.baseName.nameText
            is Temporary -> name.nameHint
            else -> return null
        }
        // gensyms are `ex_...`, so a plain name must not look like one
        return sanitize(base).takeUnless { it.startsWith("ex_") }
    }

    /** A fresh name that cannot collide with a translated one. */
    fun gensym(hint: String): OutName = OutName("ex_${sanitize(hint)}_${gensymCount++}", null)

    private fun identText(name: ResolvedName): String = sanitize(
        when (name) {
            is ExportedName -> name.displayName
            // three underscores, as be-rust and be-blimp do, so a temporary
            // cannot collide with a user name that ends in digits
            is Temporary -> "${name.nameHint}___${name.uid}"
            else -> "$name"
        },
    )

    /** A module alias segment: letters, digits and underscores, starting with a capital. */
    fun moduleSegment(text: String): String {
        val cleaned = notIdentifierChar.replace(text, "_")
        return when {
            cleaned.isEmpty() -> "T"
            !cleaned.first().isLetter() -> "T$cleaned"
            else -> cleaned.replaceFirstChar { it.uppercaseChar() }
        }
    }

    fun sanitize(text: String): String {
        val cleaned = notIdentifierChar.replace(text, "_")
        val legal = when {
            cleaned.isEmpty() -> "u_"
            cleaned.first() == '_' -> "u$cleaned"
            cleaned.first().isUpperCase() || cleaned.first().isDigit() -> "v_$cleaned"
            else -> cleaned
        }
        return when (legal) {
            in elixirReserved, in elixirKernel -> "${legal}_"
            else -> legal
        }
    }
}
