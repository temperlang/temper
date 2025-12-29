package lang.temper.interp

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.DesugarPrefixOperatorMacro
import lang.temper.builtin.Types
import lang.temper.common.TriState
import lang.temper.env.ChildEnvironment
import lang.temper.env.Constness
import lang.temper.env.DeclarationBits
import lang.temper.env.DeclarationMetadata
import lang.temper.env.Environment
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.interp.importExport.ExportDecorator
import lang.temper.interp.importExport.ImportMacro
import lang.temper.lexer.Operator
import lang.temper.lexer.TokenType
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.TemperName
import lang.temper.type.WellKnownTypes
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InternalFeatureKeys
import lang.temper.value.InterpreterCallback
import lang.temper.value.MacroValue
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TNull
import lang.temper.value.TType
import lang.temper.value.Value
import lang.temper.value.extensionSymbol
import lang.temper.value.inlineUnrealizedGoalSymbol
import lang.temper.value.jsonSymbol
import lang.temper.value.mayDowncastToSymbol
import lang.temper.value.maybeVarSymbol
import lang.temper.value.noPropertySymbol
import lang.temper.value.privateSymbol
import lang.temper.value.protectedSymbol
import lang.temper.value.publicSymbol
import lang.temper.value.sealedTypeSymbol
import lang.temper.value.staticSymbol
import lang.temper.value.valueContained
import lang.temper.value.varSymbol
import lang.temper.value.visibilitySymbol
import lang.temper.value.void

private object Builtins {
    val nameKeyToValue: Map<String, Value<*>>
    init {
        val m = mutableMapOf(
            "++" to Value(DesugarPrefixOperatorMacro("++", BuiltinFuns.plusFn)),
            "--" to Value(DesugarPrefixOperatorMacro("--", BuiltinFuns.minusFn)),
            "+" to BuiltinFuns.vPlusFn,
            "-" to BuiltinFuns.vMinusFn,
            "*" to Value(BuiltinFuns.timesFn),
            "**" to Value(BuiltinFuns.powFn),
            "/" to Value(BuiltinFuns.divFn),
            "%" to Value(BuiltinFuns.modFn),

            "<" to Value(BuiltinFuns.lessThanFn),
            ">" to Value(BuiltinFuns.greaterThanFn),
            "<=" to Value(BuiltinFuns.lessEqualsFn),
            ">=" to Value(BuiltinFuns.greaterEqualsFn),
            "==" to Value(BuiltinFuns.equalsFn),
            "!=" to Value(BuiltinFuns.notEqualsFn),
            "<=>" to BuiltinFuns.vCmp,

            "=" to BuiltinFuns.vSetLocalFn,

            "&" to BuiltinFuns.vAmpFn,
            "|" to BuiltinFuns.vBarFn,
            "!" to BuiltinFuns.vNotFn,
            "?" to BuiltinFuns.vOrNullFn,
            "throws" to BuiltinFuns.vThrowsFn,

            "[]" to BuiltinFuns.vSquareBracketFn,

            keyPair(BuiltinFuns.vStrCatMacro), // replaces itself with call to vStrCatFn
            keyPair(BuiltinFuns.vCharTagFn),

            /**
             * <!-- snippet: builtin/raw -->
             * # *raw*
             * A tag for raw strings; escape sequences are not expanded.
             *
             * ```temper "foo\\bar"
             * // Since this is raw, the \b is treated as two characters
             * // '\' and 'b', not an ASCII bell.
             * raw"foo\bar"
             * ```
             *
             * You can have a raw string with quotes by putting it in a
             * [multi-quoted string][snippet/syntax/multi-quoted-strings].
             *
             * ```temper "\"quo\\ted\""
             * raw"""
             *    ""quo\ted"
             * ```
             */
            keyPair(BuiltinFuns.vStrRawMacro),

            "false" to TBoolean.valueFalse,
            "true" to TBoolean.valueTrue,
            "null" to TNull.value,
            "void" to void,
            /**
             * <!-- snippet: builtin/Infinity -->
             * # *Infinity*
             * The [snippet/type/Float64] value for &infin;.
             */
            "Infinity" to Value(Double.POSITIVE_INFINITY, TFloat64),
            /**
             * <!-- snippet: builtin/NaN -->
             * # *NaN*
             * The [snippet/type/Float64] value for
             * [unrepresentable "Not a Number"](https://en.wikipedia.org/wiki/NaN) values.
             */
            "NaN" to Value(Double.NaN, TFloat64),

            keyPair(BuiltinFuns.vBubble),
            keyPair(BuiltinFuns.vPanic),
            keyPair(BuiltinFuns.vAssertMacro),
            keyPair(BuiltinFuns.vAsFn),
            keyPair(BuiltinFuns.vIsFn),
            keyPair(BuiltinFuns.vAsync),
            keyPair(BuiltinFuns.vAwait),
            keyPair(BuiltinFuns.vEnumMacro),
            keyPair(BuiltinFuns.vCoalesceMacro),
            keyPair(BuiltinFuns.vWhenMacro),
            keyPair(BuiltinFuns.vRegexLiteralMacro),
            keyPair(BuiltinFuns.vTestMacro),
            keyPair(BuiltinFuns.vYield),

            keyPair(vExtendsFn),

            "@" to vApplyDecoratorsMacro,
            /**
             * <!-- snippet: builtin/@protected -->
             * TODO: write me.  Visibility of type members
             */
            keyPair(
                MetadataDecorator(visibilitySymbol, "@protected") { Value(protectedSymbol) },
            ),
            /**
             * <!-- snippet: builtin/@private -->
             * # `@private` visibility
             *
             * `@private` decorates type declaration members. It indicates that
             * the member is an implementation detail, meant for internal use.
             * Private members entail no backwards compatibility commitments.
             *
             * `@private` is a [legacy decorator][snippet/legacy-decorator] so
             * the `@` is optional.
             *
             * A private member may be used from within its class definition
             * via `this.`.
             *
             * ```temper
             * class C {
             *   private s: String = "Hello, World!";
             *   public say(): Void { console.log(this.s); }
             * }
             *
             * new C().say() //!outputs "Hello, World!"
             * ```
             *
             * But a private member may not be read from outside the class.
             *
             * ```temper FAIL
             * class C {
             *   private s: String = "Hello, World!";
             * }
             * console.log(new C().s); // ERROR
             * ```
             *
             * See also [snippet/builtin/@public]
             */
            keyPair(
                MetadataDecorator(visibilitySymbol, "@private") { Value(privateSymbol) },
            ),
            /**
             * <!-- snippet: builtin/@public -->
             * TODO: write me.  Visibility of type members
             *
             * See also [snippet/builtin/@private]
             */
            keyPair(
                MetadataDecorator(visibilitySymbol, "@public") { Value(publicSymbol) },
            ),
            /**
             * <!-- snippet: builtin/@const -->
             * # `@const` decorator
             * `@const` may decorate a [snippet/builtin/let] declaration.
             * It is a [legacy decorator][snippet/legacy-decorator].
             *
             * By default, names declared by [snippet/builtin/let] may be assigned once per
             * entry into their containing scope.  `@const` is provided for familiarity with
             * readers of other languages.
             *
             * Contrast `@const` with [`@var`][snippet/builtin/@var].
             */
            keyPair(
                MetadataRemover(name = "@const") { k, _ ->
                    if (k == varSymbol || k == maybeVarSymbol) {
                        TriState.TRUE
                    } else {
                        TriState.FALSE
                    }
                },
            ),
            /**
             * <!-- snippet: builtin/@sealed : `sealed` type modifier -->
             * # `sealed` type modifier
             * Marks an interface type as sealed; only types declared in the same source file
             * may [extend][snippet/builtin/extends] it.
             *
             * Sealed types are important because the Temper translator can assume there are
             * no direct subtypes that it does not know about.
             *
             * For example, when [matching][snippet/builtin/when] a sealed type value, if
             * there is an [`is`][snippet/builtin/is] clause for each known subtype,
             * there is no need for an `else` clause.
             *
             * !!! note "Backwards compatibility note"
             *     If you add a sub-type to an existing sealed type, this may break code that
             *     uses the old version of the sealed type with a [snippet/builtin/when] or
             *     which otherwise embeds an "is none of those subtypes, therefore is this subtype"
             *     assumption.
             *
             * Backends should translate user-defined sealed types to [sum type]s
             * where available, and may insert tagged union tag field where needed.
             *
             * [sum type]: https://en.wikipedia.org/wiki/Tagged_union
             * [tagged union]: https://en.wikipedia.org/wiki/Tagged_union
             */
            keyPair(MetadataDecorator(sealedTypeSymbol, "@sealed") { void }),
            /**
             * <!-- snippet: builtin/@static -->
             * # `@static` decorator
             * `@static` may decorate a type members, and indicates that the member is associated
             * with the containing type, not with any instance of that type.
             *
             * It is a [legacy decorator][snippet/legacy-decorator], so the `static` keyword
             * is shorthand for the `@static` decorator.
             *
             * Static members are accessed via the type name, dot, member name.
             *
             * ```temper
             * class C {
             *   public static let foo = "foo";
             * }
             * C.foo == "foo"
             * ```
             *
             * Unlike in Java, static members are not accessible from an instance.
             *
             * ```temper FAIL
             * class C {
             *   public static let foo = "foo";
             * }
             * (new C()).foo == "foo"  // .foo is not declared on an instance of C
             * ```
             */
            keyPair(MetadataDecorator(staticSymbol) { void }),
            /**
             * <!-- snippet: builtin/@noProperty -->
             * # `@noProperty` decorator
             * The `@noProperty` decorator may apply to constructor inputs that appear inside
             * the parenthetical part of a [builtin/class] declaration to indicate that the
             * constructor input does not correspond to a backed property.
             *
             * ```temper
             * class C(
             *   // constructor arguments here
             *   @noProperty let x: Int,
             * ) {
             *   // Non constructor properties here must be initialized.
             *   // Their initializers may reference any constructor input including @noProperty inputs.
             *   public y: Int = x + 1;
             * }
             *
             * // This works.
             * let c = { x: 1 };
             * console.log(c.y.toString()); //!outputs "2"
             * ```
             */
            keyPair(MetadataDecorator(noPropertySymbol) { void }),
            /**
             * <!-- snippet: builtin/@var -->
             * # `@var` decorator
             * `@var` may decorate a [snippet/builtin/let] declaration.
             * It is a [legacy decorator][snippet/legacy-decorator].
             *
             * By default, names declared by [snippet/builtin/let] may be assigned once per
             * entry into their containing scope, but `@var` allows re-assigning values after
             * initialization.
             *
             * Contrast `@var` with [`@const`][snippet/builtin/@const].
             * See [examples][snippet/scoping/examples] of how `var` contrasts with
             * [snippet/builtin/let] and [`const`][snippet/builtin/@const].
             */
            keyPair(MetadataDecorator(varSymbol, findDecoratorInsertions = ::noDupeInsertions) { void }),
            "@export" to Value(ExportDecorator),
            "@test" to vTestDecorator,

            /**
             * <!-- snippet: builtin/@mayDowncastTo -->
             * `@mayDowncastTo(true)` on a type definition indicates that the type's
             * values must be distinguishable via runtime type information (RTTI) from other
             * types' values, as when casting down from a less specific type to a more
             * specific type.
             *
             * It is safe to use [snippet/builtin/is] or [snippet/builtin/as] with distinguishable types.
             *
             * `@mayDowncastTo(false)` indicates the opposite.
             *
             * If a type declaration has neither, then the following rules are used to
             * decide whether a type is safe to use.
             *
             * - If a type is a direct subtype of a [sealed][snippet/builtin/@sealed] type,
             *   and there is a path from the static type of the expression to that type
             *   via sealed types, then it is distinguishable.
             * - Otherwise, if the type is connected, it is assumed not distinguishable.
             *   Backends may connect multiple Temper types to the same backend type.
             *   For example, JavaScript and Lua backends connect both [snippet/type/Int32]
             *   and [snippet/type/Float64] to their builtin *number* type.
             *   Perl and PHP blur the distinction between numeric, boolean, and string types.
             * - Otherwise, it is assumed distinguishable.
             */
            keyPair(
                MetadataDecorator(mayDowncastToSymbol, argumentTypes = listOf(Types.boolean)) { args ->
                    args.valueTree(1).valueContained ?: NotYet
                },
            ),

            /**
             * <!-- snippet: builtin/@inlineUnrealizedGoal -->
             * # `@inlineUnrealizedGoal` decorator
             * `@inlineUnrealizedGoal` may decorate a function parameter declaration.
             *
             * An *unrealized goal* is a jump like a [snippet/builtin/break], [snippet/builtin/continue],
             * or [snippet/builtin/return] that crosses from a [block lambda][snippet/syntax/BlockLambda.svg]
             * into the containing function.
             *
             * ```temper
             * let f(ls: List<Int>): Boolean {
             *   ls.forEach { x => // Here's a block lambda
             *     if (x == 2) {
             *       console.log("Found 2!");
             *       // This `return` wants to exit `f`
             *       // but is in a different function.
             *       return true; // UNREALIZED
             *     }
             *   };
             *   false
             * }
             * f([2]) //!outputs "Found 2!"
             * ```
             *
             * Inlining a call is the act of taking the called function's body and ensuring that names
             * and parameters have the same meaning as if it was called.
             *
             * Inlining the call to `.forEach` above while also inlining uses of the block lambda into
             * `f`'s body allows connecting unrealized goals to `f`'s body.
             *
             * It does come with limitations:
             *
             * - `@inlineUnrealizedGoal` applies to parameters with function type.
             * - The containing method or function, hereafter the "callee", must not use any
             *   [snippet/builtin/@private] APIs so that uses of them can be moved.
             * - The callee must not be an overridable method.
             * - The callee must call any decorated parameter at one lexical call site.
             *   Inlining a function multiple time can lead to explosions in code size.
             * - The callee must not use any decorated parameter as an r-value;
             *   it may not delegate calling the block lambda.
             * - For a decorated parameter to be inlined, it must be a block lambda
             */
            keyPair(MetadataDecorator(inlineUnrealizedGoalSymbol) { void }),

            // TODO: enable the inert code blocks below when extension functions have
            // been fully integrated into the interpreter and existing backends.
            /**
             * <!-- snippet: builtin/@extension -->
             * # `@extension` decorator
             * The *\@extension* decorator applies to a function declaration that should
             * be callable as if it were an instance member of a separately defined type.
             *
             * (See also [snippet/builtin/@staticExtension] which allows calling
             * a function as if it were a *static* member of a separately defined type)
             *
             * For example, the String type does not have an *isPalindrome* method,
             * but languages like C# and Kotlin allow you to define extension methods:
             * functions that you can import and call *as if* they were methods of
             * the extended type.
             *
             * ```temper
             * @extension("isPalindrome")
             * let stringIsPalindrome(s: String): Boolean {
             *   var i = String.begin;
             *   var j = s.end;
             *   while (i < j) {
             *     j = s.prev(j);
             *     if (s[i] != s[j]) { return false }
             *     i = s.next(i);
             *   }
             *   return true
             * }
             *
             * // Equivalent calls
             * "step on no pets".isPalindrome() &&
             * stringIsPalindrome("step on no pets")
             * ```
             *
             * The quoted string `"isPalindrome"` above specifies that the name
             * for the function when used via method call syntax, but an extension
             * function may still be called via its regular name: *stringIsPalindrome*
             * above.
             *
             * Note that the extension function must have at least one required, positional
             * parameter and that parameter is the *this* argument, the subject when
             * called via `subject.methodName(other, args)` syntax.
             *
             * When translated to target languages that allow for extensions,
             * these functions are presented as extensions using the member name.
             *
             * In other target languages, they translate as if the *\@extension*
             * decorator were ignored.
             */
            keyPair(
                MetadataDecorator(extensionSymbol, argumentTypes = listOf(Types.string)) { args ->
                    args.valueTree(1).valueContained ?: NotYet
                },
            ),

            /**
             * <!-- snippet: builtin/@staticExtension -->
             * # `@staticExtension` decorator
             * The *\@staticExtension* decorator applies to a function declaration and allows
             * calling it as if it were a [static][snippet/builtin/@static] method of a
             * separately defined type.
             *
             * Class and interface types may be extended with [snippet/builtin/@static]
             * methods, by passing a type expression as the second argument to *\@extension*.
             *
             * [snippet/type/Float64] contains no static member *tau*, but when the declaration
             * of *float64Tau* below is in scope, it can be accessed using *Type.member* syntax.
             *
             * ```temper
             * @staticExtension(Float64, "tau")
             * let float64Tau(): Float64 { Float64.pi * 2.0 }
             *
             * // That function can be called with static method syntax and
             * // regular function call syntax.
             * Float64.tau() == float64Tau()
             * ```
             *
             * Types may only be extended with static methods in this way, not properties.
             * Note that parentheses are required after `Float64.tau()` but not after `Float64.pi`.
             *
             * As with [instance extensions][snippet/builtin/@extension], the `Type.method()`
             * syntax only works in scopes that include an import of the extension function or
             * its original definition.
             */
            keyPair(StaticExtensionDecorator),

            /**
             * <!-- snippet: builtin/@json -->
             * # `@json` decorator
             *
             * The `@json` decorator applies to a type definition.
             * It auto-generates *JsonAdapter* (see *std/json*) implementations
             * to make it easy to encode instances of the type to and from JSON.
             *
             * There are a number of strategies used to generate encoders and
             * decoders outlined below.
             *
             * ## The concrete class strategy
             *
             * A class type's default encoded form is just a JSON object with
             * a JSON property per backed field.
             * *IntPoint* below encode to JSON like `{"x":1,"y":2}` because its
             * *x* and *y* properties' values encode to JSON numbers.
             *
             * ```temper
             * let {
             *   InterchangeContext,
             *   NullInterchangeContext,
             *   JsonTextProducer,
             *   parseJson,
             * } = import("std/json");
             *
             * // Define a simple class with JSON interop via `@json`
             * @json class IntPoint(
             *   public x: Int,
             *   public y: Int,
             * ) {}
             *
             * // A builder for a JSON string.
             * let jsonTextProducer = new JsonTextProducer();
             * // Encode a point
             * IntPoint.jsonAdapter().encodeToJson(
             *   new IntPoint(1, 2),
             *   jsonTextProducer
             * );
             * //!outputs "{\"x\":1,\"y\":2}"
             * console.log(jsonTextProducer.toJsonString());
             *
             * // Decode a point
             * let jsonSyntaxTree = parseJson("{\"x\":3,\"y\":4}");
             * let p = IntPoint.jsonAdapter()
             *     .decodeFromJson(jsonSyntaxTree, NullInterchangeContext.instance);
             *
             * console.log("x is ${p.x.toString()}, y is ${p.y.toString()}");
             * //!outputs "x is 3, y is 4"
             * ```
             *
             * ## The custom JSON adapter strategy
             *
             * If a type, whether it's a class or interface type,
             * already has encoding and decoding functions then none are
             * auto-generated.
             *
             * ```temper
             * let {
             *   InterchangeContext,
             *   JsonInt,
             *   JsonNumeric,
             *   JsonProducer,
             *   JsonSyntaxTree,
             *   JsonTextProducer,
             * } = import("std/json");
             *
             * @json class IntWrapper(
             *   public i: Int,
             * ) {
             *   public encodeToJson(p: JsonProducer): Void {
             *     p.int32Value(i);
             *   }
             *
             *   public static decodeFromJson(t: JsonSyntaxTree, ic: InterchangeContext): IntWrapper throws Bubble {
             *     new IntWrapper((t as JsonNumeric).asInt32())
             *   }
             * }
             *
             * let p = new JsonTextProducer();
             * // IntWrapper got a static jsonAdapter() method but not encoders and decoders.
             * IntWrapper.jsonAdapter().encodeToJson(new IntWrapper(123), p);
             *
             * "123" == p.toJsonString()
             * ```
             *
             * ## Sealed interface strategy
             *
             * For sealed interfaces, we generate adapters that delegate to adapters for
             * the appropriate sub-type.
             *
             * ```temper
             * let {
             *   JsonTextProducer,
             *   listJsonAdapter,
             * } = import("std/json");
             *
             * @json sealed interface Animal {}
             *
             * @json class Cat(
             *   public meowCount: Int,
             * ) extends Animal {}
             *
             * @json class Dog(
             *   public hydrantsSniffed: Int,
             * ) extends Animal {}
             *
             * let ls: List<Animal> = [new Cat(11), new Dog(111)];
             *
             * let p = new JsonTextProducer();
             * List.jsonAdapter(Animal.jsonAdapter()).encodeToJson(ls, p);
             * p.toJsonString() == "[{\"meowCount\":11},{\"hydrantsSniffed\":111}]"
             * ```
             *
             * ## Adapting JSON for generic types
             *
             * If a type is generic, its *jsonAdapter* static method
             * might require an adapter for its type arguments.
             *
             * ```temper
             * let {
             *   JsonAdapter,
             *   JsonTextProducer,
             *   listJsonAdapter,
             *   int32JsonAdapter,
             * } = import("std/json");
             *
             * let intListAdapter: JsonAdapter<List<Int>> =
             *     List.jsonAdapter(Int.jsonAdapter());
             *
             * let p = new JsonTextProducer();
             * intListAdapter.encodeToJson([123], p);
             *
             * "[123]" == p.toJsonString()
             * ```
             *
             * ## Solving Ambiguity with extra properties
             *
             * Sometimes, ambiguity is unavoidable in the JSON structure of two
             * related types, like two subtypes of the same sealed interface.
             *
             * [snippet/builtin/@jsonExtra] allows adding extra JSON properties
             * with known values that do not correspond to a class constructor
             * parameter but which the sealed interface strategy can use.
             */
            keyPair(MetadataDecorator(jsonSymbol) { _ -> void }),

            /**
             * <!-- snippet: builtin/@jsonExtra -->
             * # `@jsonExtra` decorator
             *
             * Allows customizing code generation for [snippet/builtin/@json] support.
             *
             * This allows avoiding ambiguity when two variants of a sealed interface
             * have otherwise similar JSON structure in their JSON wire formats.
             *
             * ```temper
             * let {
             *   InterchangeContext,
             *   NullInterchangeContext,
             *   parseJson,
             * } = import("std/json");
             *
             * @json
             * sealed interface FooRequest {}
             *
             * // Without any disambiguation both variants of FooRequest
             * // would have a wire form like
             * //     { "name": "..." }
             *
             * // Foo requests are tagged with a version: `"v": 2.0`.
             * @json @jsonExtra("v", 2.0)
             * class FooRequestVersion2(
             *   public name: String,
             * ) extends FooRequest {}
             *
             * // But servers still need to support a deprecated, legacy format
             * // tagged with `"v": 1.0`
             * @json @jsonExtra("v", 1.0)
             * class FooRequestVersion1(
             *   public name: String,
             * ) extends FooRequest {}
             *
             * // Neither class has a constructor input named "v", but it's
             * // required in the JSON because of the @jsonExtra decorations.
             * let jsonSyntaxTreeV1 = parseJson(
             *   """
             *   "{"v": 1.0, "name": "a"}
             * );
             * let jsonSyntaxTreeV2 = parseJson(
             *   """
             *   "{"v": 2.0, "name": "a"}
             * );
             *
             * FooRequest.jsonAdapter()
             *   .decodeFromJson(jsonSyntaxTreeV1, NullInterchangeContext.instance)
             *   is FooRequestVersion1
             * &&
             * FooRequest.jsonAdapter()
             *   .decodeFromJson(jsonSyntaxTreeV2, NullInterchangeContext.instance)
             *   is FooRequestVersion2
             * ```
             */
            keyPair(jsonExtraDecorator),

            /**
             * <!-- snippet: builtin/@jsonName -->
             * # `@jsonName` decorator
             *
             * Specifies the JSON property name for a Temper property name.
             *
             * Without the `@jsonName` decoration below, the JSON form would be
             * `{"messageText":"Hello, World!"}` but as seen below, the JSON
             * output has dash case.
             *
             * ```temper
             * @json
             * class Message(
             *   @jsonName("message-text")
             *   public messageText: String
             * ) {}
             *
             * let { JsonTextProducer } = import("std/json");
             * let jsonOut = new JsonTextProducer();
             * Message.jsonAdapter()
             *   .encodeToJson(new Message("Hello, World!"), jsonOut);
             *
             * //!outputs "{\"message-text\":\"Hello, World!\"}"
             * console.log(jsonOut.toJsonString());
             * ```
             */
            keyPair(jsonNameDecorator),
            keyPair(vFunDecorator),

            "new" to Value(New),
            keyPair(Value(ImportMacro)),

            keyPair(Value(ReturnDesugarMacro)),

            // Turn flow control into blocks with complex control flow.
            "while" to Value(LoopTransform),
            "for" to Value(LoopTransform),
            // These are short-circuiting so technically control flow.
            "&&" to BuiltinFuns.vDesugarLogicalAndFn,
            "||" to BuiltinFuns.vDesugarLogicalOrFn,

            keyPair(Value(CompileLog)),
        )
        // Builtin types, including one alias for Int.
        Types.typesAsValues.forEach {
            m[TType.unpack(it).builtinTypeName!!.builtinKey] = it
        }
        m[TInt.alias.builtinKey] = Types.vInt
        // Forward details of how objects work to the compiler to avoid dependency cycles.
        InternalFeatureKeys.entries.forEach {
            if (it.featureKey !in m && it.isBuiltin) {
                m[it.featureKey] = forwardingFeatureMacro(it)
            }
        }
        // Turn flow control into blocks with complex control flow.
        listOf(
            BreakTransform,
            ContinueTransform,
            DoTransform,
            OrElseTransform,
            IfTransform,
        ).forEach {
            m[it.name] = Value(it)
        }
        nameKeyToValue = m.toMap()
    }
}

internal class BuiltinEnvironment(
    parent: Environment,
) : ChildEnvironment(parent) {
    override val isLongLived: Boolean get() = true

    private fun hasLocalKey(name: TemperName): Boolean {
        val builtinKey = name.builtinKey
        return if (builtinKey in Builtins.nameKeyToValue) {
            true
        } else {
            val simpleBuiltinKey = simpleBuiltinKeyFromCompoundOperator(builtinKey)
            simpleBuiltinKey != null && simpleBuiltinKey in Builtins.nameKeyToValue
        }
    }

    override fun localDeclarationMetadata(name: TemperName): DeclarationMetadata? =
        if (hasLocalKey(name)) {
            BuiltinMetadata
        } else {
            null
        }

    private object BuiltinMetadata : DeclarationMetadata {
        override val constness = Constness.Const
        override val referentSource = ReferentSource.SingleSourceAssigned
        override val completeness = ReferentBitSet.complete
        override val declarationSite: Position? = null
        override val reifiedType: Value<*>? = null
    }

    override fun get(name: TemperName, cb: InterpreterCallback): PartialResult {
        val builtinKey = name.builtinKey
        if (builtinKey != null) {
            val builtin = Builtins.nameKeyToValue[builtinKey]
            if (builtin != null) { return builtin }
            val simpleBuiltinKey = simpleBuiltinKeyFromCompoundOperator(builtinKey)
            if (simpleBuiltinKey != null) {
                val simpleBuiltin = Builtins.nameKeyToValue[simpleBuiltinKey]
                val simpleFn = TFunction.unpackOrNull(simpleBuiltin)
                if (simpleFn is CallableValue) {
                    return Value(DesugarCompoundAssignmentMacro(builtinKey, simpleFn))
                }
            }
        }
        return super.get(name, cb)
    }

    override fun declare(
        name: TemperName,
        declarationBits: DeclarationBits,
        cb: InterpreterCallback,
    ): Fail = rejectDeclareOnImmutableEnv(name, cb)

    override operator fun set(name: TemperName, newValue: Value<*>, cb: InterpreterCallback): PartialResult {
        val builtinKey = name.builtinKey
        return if (builtinKey != null && builtinKey in Builtins.nameKeyToValue) {
            rejectSetOfImmutableBinding(name, cb)
        } else {
            return super.set(name, newValue, cb)
        }
    }

    // This does not include all possible compound operators but this is advertised as best effort,
    // and I'm lazy.
    override val locallyDeclared: Iterable<TemperName> get() = Builtins.nameKeyToValue.keys.map {
        BuiltinName(it)
    }
}

private fun simpleBuiltinKeyFromCompoundOperator(builtinKey: String?): String? =
    if (
        builtinKey != null &&
        Operator.isProbablyAssignmentOperator(builtinKey, TokenType.Punctuation) &&
        builtinKey != "="
    ) {
        builtinKey.substring(0, builtinKey.length - 1)
    } else {
        null
    }

private fun <T : MacroValue> keyPair(v: Value<T>): Pair<String, Value<T>> =
    (TFunction.unpack(v) as NamedBuiltinFun).name to v

private fun keyPair(md: NamedBuiltinFun): Pair<String, Value<MacroValue>> =
    md.name to Value(md)

/** The value of type [WellKnownTypes.emptyType] */
val emptyValue = Value(
    InstancePropertyRecord(mutableMapOf()),
    TClass(WellKnownTypes.emptyTypeDefinition),
)
