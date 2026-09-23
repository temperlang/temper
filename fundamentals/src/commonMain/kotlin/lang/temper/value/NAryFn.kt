package lang.temper.value

import lang.temper.name.BuiltinName
import lang.temper.type2.Signature2
import lang.temper.type2.Type2

/**
 * N-ary functions are functions that can accept any number
 * of inputs that match the [extraInputType] requirement.
 *
 * The typer assumes that any NAry function is going to
 * appear directly in the AST as a value because these
 * functions are desugared to by macros:
 *
 * - [ListifyFn] is the desugaring of `[a, b, c]` list
 *   constructor expressions, and the `List.of` static.
 * - [StrCatFn] is the desugaring of string expressions with
 *   interpolations like `"foo ${ bar } baz"`.
 * - [ErrorFn] is a placeholder for a compiler error.
 *   It has log messages, type Problem, as children, but
 *   coming from the parser may have token text, and it
 *   may preserve stay children.
 *
 * In the future Temper might support general rest parameter
 * syntax by defining a `RestList<T> extends List<T>`
 * pseudo-type and turning calls to those into calls that
 * use [ListifyFn] to bundle together inputs that form
 * part of the rest parameter list, but having a well-typed
 * listify function is necessary
 *
 */
abstract class NAryFn(
    val builtinName: BuiltinName,
    final override val sigs: List<Signature2>,
    final override val builtinOperatorId: BuiltinOperatorId?,
    val extraInputType: Type2,
) : NamedBuiltinFun, MacroValue {
    final override val name = builtinName.builtinKey
}
