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

    fun outName(name: ResolvedName): OutName = OutName(identText(name), name)

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
