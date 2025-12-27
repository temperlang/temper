package lang.temper.interp

import lang.temper.value.Value
import lang.temper.value.functionalInterfaceSymbol
import lang.temper.value.void

/**
 * Marks an interface declaration as a functional decorator.
 *
 * <!-- snippet: builtin/@fun -->
 * # `@fun` decorator
 * Marks an interface declaration as a [snippet/functional-interface].
 *
 * For example:
 *
 * ```temper inert
 * @fun interface Predicate<T>(x: T): Boolean;
 * ```
 *
 * <!-- snippet: functional-interface -->
 * # Functional interface
 *
 * Functional interfaces are like [SAM types](https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.8)
 * in Java; they let named types correspond to function types.
 *
 * ??? warning
 *
 *     In-progress. The example below does not yet work.
 *     TODO: implement functional interface support and remove SAM-type guessing in be-java.
 *     Then remove the inert marker.
 *
 * ```temper inert
 * @fun interface Predicate<T>(x: T): Boolean;
 * // Applying a predicate to a value of its type parameter, returns a boolean.
 *
 * let applyAndNegate<T>(x: T, predicate: Predicate<T>): Boolean {
 *   !predicate(x)    // predicate is applied as a function
 * }
 *
 * let s = "abcd";
 * //!outputs: "Is 'abcd' empty? The answer is NOT: true."
 * console.log("Is '${s}' empty? The answer is NOT: ${
 *     applyAndNegate(s) { x => x.empty }
 *     //                ^^^^^^^^^^^^^^^^ That block is-a Predicate<String>
 * }.");
 * ```
 *
 * The [builtin/@fun] marks the interface above as functional, and allows the
 * abbreviated method-like syntax.
 *
 * The parts of an abbreviated functional interface declaration are shown below.
 *
 * ```temper inert
 * // other decorators
 * @fun
 * interface InterfaceName<
 *   // optional type arguments for interface
 * >(
 *   // optional arguments for public .apply method
 * ): ReturnType;
 * ```
 *
 * Functional interfaces, aka `@fun` interfaces, have the following restrictions:
 *
 * - Their only allowed supertype is [type/AnyValue].
 * - They may declare only one method, named *apply*.
 * - The interface may declare type parameters but the method may not.
 * - They may not declare properties, getters, setters, or statics.
 *   This includes members implicitly added by macros like `@json`.
 * - A functional interface may not be used with runtime type operators:
 *   [builtin/is] or [builtin/as].
 *
 * !!! note
 *
 *     These restrictions allow translating functional interface types to
 *     [nominal types](https://en.wikipedia.org/wiki/Nominal_type_system)
 *     for target languages that only provide those.
 *
 *     Meanwhile, the lack of a type hierarchy and the restrictions on runtime type
 *     operations allow translating them to
 *     [structural function types](https://en.wikipedia.org/wiki/Structural_type_system),
 *     e.g. arrow types, for target languages that benefit from those.
 */
val funDecorator = MetadataDecorator(
    functionalInterfaceSymbol,
    name = "@fun",
) {
    void
}

val vFunDecorator = Value(funDecorator)
