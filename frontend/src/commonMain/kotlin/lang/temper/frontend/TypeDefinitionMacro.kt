package lang.temper.frontend

import lang.temper.env.InterpMode
import lang.temper.frontend.disambiguate.typeDisambiguateMacro
import lang.temper.frontend.typestage.typeRedundantMacro
import lang.temper.frontend.typestage.typeShapeMacro
import lang.temper.frontend.typestage.typeSyntaxMacro
import lang.temper.stage.Stage
import lang.temper.type.Abstractness
import lang.temper.type2.Signature2
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult

/**
 * The macro backing the `class` and `interface` builtins that delegates stage specific work to
 * other macros including:
 *
 * - [typeDisambiguateMacro] for [Stage.DisAmbiguate]
 * - [typeSyntaxMacro] for [Stage.SyntaxMacro]
 * - [typeShapeMacro] for common work
 *
 * <!-- snippet: builtin/class -->
 * # `class`
 * Defines a concrete `class` type.  Classes may extend [snippet/builtin/interface] types but may
 * not themselves be extended.
 *
 * Class declarations consist of several parts:
 *
 * 1. A type name
 * 2. Optionally, some type formals
 * 3. Optionally, constructor parameters
 * 4. Optionally, some super types
 * 5. A block containing type members
 *
 * ```temper
 * //    ①          ②      ②
 * class MyTypeName<TYPE, FORMAL>(
 *
 *   // ③
 *   public propertyName: Int,
 *
 *   //      ④       ⑤
 * ) extends AnyValue {
 *
 *   public anotherProperty: Int = propertyName + 1;
 *
 *   public method(): Int { anotherProperty * 2 }
 * }
 *
 * // Given such a declaration, we can create values of type MyTypeName.
 * // Properties that appear in the parenthetical are both constructor parameters.
 * let value = { propertyName: 11 };
 * // But parenthetical declarations also declare a property.
 * console.log(value.propertyName.toString()); //!outputs "11"
 * // Public members (the default) declared in the body are also available.
 * console.log(value.anotherProperty.toString()); //!outputs "12"
 * console.log(value.method().toString()); //!outputs "24"
 *
 * // For unconnected classes, you can check whether a value is of that type
 * // at runtime.
 * console.log((value is MyTypeName<AnyValue, AnyValue>).toString()); //!outputs "true"
 * ```
 *
 * A minimal class declaration may omit many of those elements along with
 * the brackets and keyword (like [snippet/builtin/extends]):
 *
 * ```temper
 * class Minimal {}
 *
 * let m = new Minimal();
 *
 * console.log((m is Minimal).toString()); //!outputs "true"
 * ```
 *
 * Source: [temper/**/TypeDefinitionMacro.kt]
 *
 * Re parenthetical declarations, see also: [snippet/builtin/@noProperty]
 *
 * <!-- snippet: builtin/interface -->
 * # `interface`
 * Defines an abstract `interface` type.
 * Interface types may define abstract properties but may not define constructors or backed
 * properties.
 * Interface's properties may be overridden by backed properties in a [snippet/builtin/class]
 * sub-type.
 *
 * See also [snippet/builtin/class] for details and examples of type declaration syntax.
 *
 * Source: [temper/**/TypeDefinitionMacro.kt]
 */
internal sealed class TypeDefinitionMacro(
    private val abstractness: Abstractness,
    override val name: String,
) : BuiltinStatelessMacroValue, NamedBuiltinFun {
    override val sigs: List<Signature2>? get() = null
    override val nameIsKeyword: Boolean get() = true

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult = when (macroEnv.stage) {
        Stage.Lex, Stage.Parse, Stage.Import -> NotYet
        Stage.DisAmbiguate -> typeDisambiguateMacro(abstractness, macroEnv)
        Stage.SyntaxMacro -> typeSyntaxMacro(macroEnv)
        Stage.Define -> typeShapeMacro(macroEnv)
        Stage.Type,
        Stage.FunctionMacro,
        Stage.Export,
        Stage.Query,
        Stage.GenerateCode,
        -> typeRedundantMacro(macroEnv)
        Stage.Run -> Fail
    }
}

/** Calls to `class` define a concrete (instantiable) type. */
internal object ClassDefinitionMacro : TypeDefinitionMacro(Abstractness.Concrete, "class")

/** Calls to `interface` define an abstract (not-directly-instantiable) type. */
internal object InterfaceDefinitionMacro : TypeDefinitionMacro(Abstractness.Abstract, "interface")
