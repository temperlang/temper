package lang.temper.be.tmpl

import lang.temper.type.isVoidLike
import lang.temper.value.Tree

/**
 * How to represent [`Void`][lang.temper.value.TVoid] as a return value.
 *
 * Some languages, often the more functional languages, have a type
 * with a single value used as a result for functions that are called
 * for their side effect instead of for their result.
 *
 * For example, in OCaml:
 *
 * ```ocaml
 * (* function with return type unit *)
 * let f(): unit =
 *   (* we can store the unit value `()` in a local variable *)
 *   let result: unit = () in
 *   (* and then reference it as the result *)
 *   result
 * ```
 *
 * But other languages do not allow storing a value of their *void* type in
 * local variables.
 * For example, in Java:
 *
 * ```java
 * void f() { // This line is ok
 *   void x = ...;  // This line is not OK.
 *                  // `void` may not be used for locals
 *   return x;
 * }
 * ```
 *
 * Because Java's `void` keyword specifies a
 * [*Method Result*](https://docs.oracle.com/javase/specs/jls/se19/html/jls-8.html#jls-8.4.5),
 * which is distinct from a
 * [*Type*](https://docs.oracle.com/javase/specs/jls/se19/html/jls-4.html#jls-4.1).
 */
enum class RepresentationOfVoid {
    DoNotReifyVoid,
    ReifyVoid,
}

internal fun hasVoidLikeType(t: Tree?): Boolean = t?.typeInferences?.type?.isVoidLike == true
