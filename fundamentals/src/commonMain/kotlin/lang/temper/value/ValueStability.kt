package lang.temper.value

/**
 * Whether an equivalent value can be distinguished by some test from within the language.
 *
 * Values with **identity** are unstable because an identity check (Python `is`) might show that
 * separately allocated values are distinct.
 *
 *     // Java
 *     String a = new String("") // Explicitly non-interned String value
 *     a == "" // Nope
 *
 * *Mutable* values are probably unstable.  Code could mutate one, compare with `!=` to see that
 * they are different.
 *
 *     let a = mutableEmptyList()
 *     let b = mutableEmptyList()
 *     a == b // Sure, whatever
 *     a += 0
 *     a == b // Nope
 */
enum class ValueStability {
    /** There might be a test that distinguishes the value from an equivalent value. */
    Unstable,

    /** There must not be a test that distinguishes the value from an equivalent value. */
    Stable,
}
