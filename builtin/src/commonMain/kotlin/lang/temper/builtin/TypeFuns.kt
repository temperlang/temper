package lang.temper.builtin

import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.cst.NameConstants
import lang.temper.env.InterpMode
import lang.temper.format.OutToks
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.type.Abstractness
import lang.temper.type.AndType
import lang.temper.type.FunctionType
import lang.temper.type.InvalidType
import lang.temper.type.MkType
import lang.temper.type.NominalType
import lang.temper.type.StaticType
import lang.temper.type.TypeContext
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes
import lang.temper.type.canBeNull
import lang.temper.type.excludeNull
import lang.temper.type.extractNominalTypes
import lang.temper.type.isNeverType
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.ActualValues
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.FunctionSpecies
import lang.temper.value.Helpful
import lang.temper.value.InterpreterCallback
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.ReifiedType
import lang.temper.value.Result
import lang.temper.value.TBoolean
import lang.temper.value.TFunction
import lang.temper.value.TList
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.Value
import lang.temper.value.ampBuiltinName
import lang.temper.value.asBuiltinName
import lang.temper.value.connectedSymbol
import lang.temper.value.functionContained
import lang.temper.value.isBuiltinName
import lang.temper.value.mayDowncastToSymbol
import lang.temper.value.optionalSymbol
import lang.temper.value.outTypeSymbol
import lang.temper.value.restFormalSymbol
import lang.temper.value.sealedTypeSymbol
import lang.temper.value.throwsBuiltinName
import lang.temper.value.typeFormalSymbol
import lang.temper.value.unpackPositionedOr
import lang.temper.value.wordSymbol

private val oneTypeToType = Signature2(
    returnType2 = WellKnownTypes.typeType2,
    hasThisFormal = false,
    requiredInputTypes = listOf(WellKnownTypes.typeType2),
)

private val typesToListOfTypes: List<Signature2> = listOf(
    // `A & B`: Type * Type -> List<Type>
    // `A & B & C` is the same as `A & (B & C)`: Type * List<Type> -> List<Type>
    Signature2(
        returnType2 = WellKnownTypes.typeType2,
        hasThisFormal = false,
        requiredInputTypes = listOf(WellKnownTypes.typeType2, WellKnownTypes.typeType2),
    ),
    Signature2(
        returnType2 = WellKnownTypes.typeType2,
        hasThisFormal = false,
        requiredInputTypes = listOf(
            WellKnownTypes.typeType2,
            MkType2(WellKnownTypes.listTypeDefinition).actuals(listOf(WellKnownTypes.typeType2)).get(),
        ),
    ),
)

/**
 * Convert type expression pairs to [And][lang.temper.type.AndType] types.
 *
 * <!-- snippet: type/intersection-fn : type `&` -->
 * # Intersection type bound `&`
 *
 * When the `&` operator is applied to types instead of numbers, it constructs
 * an intersection type bound.
 *
 * An intersection type bound specifies that the bounded type is a sub-type of each of its members.
 * So a value of a type that `extends I & J` declares a type can be assigned to a type `I` **and** can be assigned a declaration with type `J`.
 * See also [snippet/type/relationships].
 *
 * ```temper null
 * interface A {
 *   a(): Void {}
 * }
 * interface B {
 *   b(): Void {}
 * }
 *
 * class C extends A & B {}
 *
 * let c: C = new C();
 * let a: A = c;
 * let b: B = c;
 *
 * let f<T extends A & B>(t: T): Void {
 *   let a: A = t;
 *   let b: B = t;
 *   a.a();
 *   b.b();
 * }
 * f<C>(c);
 * ```
 */
internal object TypeIntersectionFun : BuiltinFun(ampBuiltinName, typesToListOfTypes), PureCallableValue {
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (t, u) = args.unpackPositioned(2, cb) ?: return Fail
        val tTypes = asReifiedTypeListOr(t, cb) { fail -> return@invoke fail }
        val uTypes = asReifiedTypeListOr(u, cb) { fail -> return@invoke fail }
        return Value(
            buildSet {
                tTypes.mapTo(this) { Value(it, TType) }
                uTypes.mapTo(this) { Value(it, TType) }
            }.toList(),
            TList,
        )
    }
}

/**
 * <!-- snippet: builtin/%3F -->
 * # Postfix `?`
 *
 * A question mark (`?`) after a type indicates that the type accepts the value [snippet/builtin/null].
 *
 * ```temper inert
 * var firstEven: Int = null; // ILLEGAL
 * let ints = [1, 2, 3, 4];
 * for (let i of ints) {
 *   if (0 == (i % 2)) {
 *     firstEven = i;
 *     break;
 *   }
 * }
 *
 * console.log(firstEven?.toString() ?? "so odd");
 * ```
 *
 * But with a postfix question mark, that works.
 *
 * ```temper
 * //                ⬇️
 * var firstEven: Int? = null; // OK
 * let ints = [1, 2, 3, 4];
 * for (let i of ints) {
 *   if (0 == (i % 2)) {
 *     firstEven = i;
 *     break;
 *   }
 * }
 *
 * console.log(firstEven?.toString() ?? "so odd"); //!outputs "2"
 * ```
 */
internal object OrNullFn : BuiltinFun("?", oneTypeToType), PureCallableValue {
    override val token get() = OutToks.postfixQMark

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val (t) = args.unpackPositioned(1, cb) ?: return Fail
        val tType = asReifiedTypeOr(t, cb) { fail -> return@invoke fail }
        // TODO: ban orNull applied to void or result types
        return Value(Types.nullableOf(tType))
    }
}

/**
 * The type angle function serves two distinct purposes:
 * - It turns type expressions like `C<A, B>` into reified type that close over a generic type which
 *   are used by the *Typer*.
 * - Applied to function expressions, it supplies explicit type arguments that can be retrieved by
 *   the *Typer*.
 *
 * In the first case, calls may fail on arity mismatch: when the count of actual type parameters
 * does not match the count of formal type parameters.
 *
 * In the second case, the result is just the function passed in as the significance of the function
 * is apparent during static typing.
 */
internal object TypeAngleFn : BuiltinFun(NameConstants.Angle, null), PureCallableValue {
    // This is a static operator which is erased before runtime, so we don't need any
    // failure branches specifically for it.
    // It's super convenient for the Typer to be able to find this around the callee, instead of
    // being separated from the callee by an `hs` call.
    override val callMayFailPerSe: Boolean = false

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult {
        val n = args.size
        if (n == 0) {
            return cb.fail(MessageTemplate.ArityMismatch, values = listOf(">= 1"))
        }
        val arg0 = args[0]
        if (arg0.typeTag == TFunction) {
            // Applying actual type parameters to a callee.
            return when (interpMode) {
                // In partial mode, don't inline the value and lose the type actuals.
                InterpMode.Partial -> NotYet
                InterpMode.Full -> arg0
            }
        }
        val reifiedType = asReifiedType(arg0)
            ?: return cb.fail(
                MessageTemplate.ExpectedValueOfType,
                values = listOf(TType, arg0),
            )

        val type = reifiedType.type2
        if (type !is DefinedNonNullType) {
            return cb.fail(MessageTemplate.ExpectedValueOfType, values = listOf("NominalType", type))
        }
        val definition = type.definition
        if (definition.formals.size != n - 1) {
            return cb.fail(MessageTemplate.ArityMismatch, values = listOf(definition.formals.size))
        }

        val bindings = mutableListOf<Type2>()
        for (i in 1 until n) {
            val actualValue = args[i]
            bindings.add(
                asReifiedTypeOr(actualValue, cb) { fail ->
                    return@invoke fail
                }.type2,
            )
        }
        val parameterized = MkType2(definition).actuals(bindings.toList()).get()
        return Value(reifiedType.copy(type2 = parameterized, hasExplicitActuals = true))
    }

    override fun mayReplaceCallWithArgs(args: ActualValues): Boolean {
        val n = args.size
        if (n == 0) {
            return false
        }
        // If we're dealing with explicit type parameters to a generic function, preserve those
        // in the AST until the last Typer pass can eliminate them.
        // But allow inlining of type expressions.
        return asReifiedType(args[0]) != null
    }
}

/**
 * <!-- snippet: builtin/throws -->
 * # Type operator `throws`
 *
 * The infix `throws` operator can be used on a return type to indicate that a function
 * can fail in one of several ways.
 *
 * ![snippet/syntax/Throws.svg]
 *
 * A call that throws may be used within [snippet/builtin/orelse].
 *
 * ```temper
 * let parseZero(s: String): Int throws Bubble {
 *   if (s != "0") { bubble() }
 *   return 0
 * }
 *
 * //!outputs  "parseZero(\"0\")            = 0"
 * console.log("parseZero(\"0\")            = ${ parseZero("0") }");
 * //!outputs  "parseZero(\"hi\") orelse -1 = -1"
 * console.log("parseZero(\"hi\") orelse -1 = ${ parseZero("hi") orelse -1 }");
 * ```
 */
internal object ThrowsFn : BuiltinFun(throwsBuiltinName, null as List<Signature2>?), PureCallableValue {
    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val n = args.size
        if (n < 2) {
            return cb.fail(MessageTemplate.ArityMismatch, values = listOf(">= 2"))
        }
        val typeValues = args.unpackPositioned(n, cb) ?: return Fail
        val types = typeValues.map {
            asReifiedTypeOr(it, cb) { fail -> return@invoke fail }
                .type2
        }
        // TODO: define failure types
        // TODO: ban result types in all positions
        // TODO: ban voidish types in failure mode positions
        return Value(ReifiedType(MkType2.result(types[0], WellKnownTypes.bubbleType2).get()))
    }
}

internal abstract class AbstractFnTypeFn(name: String) : BuiltinFun(name, null) {
    override val nameIsKeyword: Boolean = true

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult {
        val typeFormals = mutableListOf<TypeFormal>()
        val valueFormals = mutableListOf<FunctionType.ValueFormal>()
        var restValuesFormal: StaticType? = null // TODO
        var returnType: StaticType? = null
        var problemIndex = -1

        var argIndexCounter = 0
        val lastArgIndex = args.lastIndex
        argLoop@
        while (argIndexCounter <= lastArgIndex) {
            val argIndex = argIndexCounter++
            val type = asReifiedType(args[argIndex])?.type
            when (args.key(argIndex)) {
                typeFormalSymbol -> {
                    val definition = (type as? NominalType)?.definition
                    if (definition is TypeFormal && type.bindings.isEmpty()) {
                        typeFormals.add(definition)
                    } else {
                        problemIndex = argIndex
                        break
                    }
                }
                null -> {
                    if (type == null) {
                        problemIndex = argIndex
                        break
                    }

                    var symbol: Symbol? = null
                    var isOptional = false
                    var isResty = false
                    // Expect metadata following type like
                    // \word \nameOfArg \optional void \restFormal void
                    while (argIndexCounter < lastArgIndex) {
                        when (args.key(argIndexCounter)) {
                            wordSymbol -> {
                                val name = TSymbol.unpackOrNull(args.result(argIndexCounter))
                                if (name == null || symbol != null) {
                                    problemIndex = argIndexCounter
                                    break@argLoop
                                }
                                symbol = name
                            }
                            optionalSymbol -> isOptional = true
                            restFormalSymbol -> isResty = true
                            else -> break
                        }
                        argIndexCounter += 1
                    }
                    if (!isResty) {
                        valueFormals.add(FunctionType.ValueFormal(symbol, type, isOptional))
                    } else if (restValuesFormal == null) {
                        restValuesFormal = type
                    } else {
                        problemIndex = argIndex
                    }
                }
                outTypeSymbol -> {
                    if (type == null || returnType != null) {
                        problemIndex = argIndex
                        break
                    }

                    returnType = type
                }
                else -> {
                    problemIndex = argIndex
                    break
                }
            }
        }

        return if (problemIndex < 0) {
            val functionType = constructType(
                typeFormals = typeFormals.toList(),
                valueFormals = valueFormals.toList(),
                restValuesFormal = restValuesFormal,
                returnType = returnType,
            )
            Value(ReifiedType(hackMapOldStyleToNew(functionType)))
        } else {
            Fail(
                LogEntry(
                    Log.Error,
                    MessageTemplate.MalformedType,
                    args.pos(problemIndex) ?: cb.pos,
                    emptyList(),
                ),
            )
        }
    }

    abstract fun constructType(
        typeFormals: List<TypeFormal>,
        valueFormals: List<FunctionType.ValueFormal>,
        restValuesFormal: StaticType?,
        returnType: StaticType?,
    ): StaticType
}

/**
 * Constructs a reified function type from syntax like `fn (Int): Int` after the `fn` macro
 * has done its work pre-processing type formals and the like.
 */
internal object FnTypeFn : AbstractFnTypeFn("fn"), Helpful {
    override fun constructType(
        typeFormals: List<TypeFormal>,
        valueFormals: List<FunctionType.ValueFormal>,
        restValuesFormal: StaticType?,
        returnType: StaticType?,
    ): FunctionType = MkType.fnDetails(
        typeFormals = typeFormals,
        valueFormals = valueFormals,
        restValuesFormal = restValuesFormal,
        returnType = returnType ?: Types.void.type,
    )

    override fun briefHelp(): String = "Constructs a function type"
    override fun longHelp(): String = """
        |`$name(ArgType1, ArgType2): ReturnType` is the type for a function of two arguments
        |that are of type `ArgType1` and `ArgType2` respectively
        |and which returns `ReturnType`.
    """.trimMargin()
}

enum class RuntimeTypeOperation(val asLike: Boolean) {
    As(true),
    AssertAs(true),
    Is(false),
}

sealed class RttiCheckFunction(
    builtinName: BuiltinName,
    signature: Signature2,
) : BuiltinFun(builtinName = builtinName, signatures = listOf(signature)) {
    abstract val runtimeTypeOperation: RuntimeTypeOperation

    abstract val allowUpChecks: Boolean

    abstract fun doCheck(x: Value<*>, reifiedType: ReifiedType): PartialResult

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val (x, type) = args.unpackPositionedOr(2, cb) { return@invoke it }
        val reifiedType = asReifiedTypeOr(type, cb, args.pos(1)) { return@invoke it }
        if (cb.stage <= Stage.GenerateCode) {
            // We don't want uses of casting in the REPL to give people false confidence
            // that RTTI checking code will compile and translate.
            //
            // If we haven't yet passed the TypeChecker, which can take into account
            // the static type to allow casting from String? to String but not
            // from AnyValue to String, then suspend judgement about whether the
            // check is ok.
            val problem = problems(
                Either.Right(x),
                reifiedType.type,
                cb.pos,
            ).firstOrNull { it.level >= Log.Error }
            if (problem != null) {
                return NotYet
            }
        }
        return doCheck(x, reifiedType)
    }

    override val nameIsKeyword: Boolean = true

    override val functionSpecies: FunctionSpecies get() = FunctionSpecies.Pure
}

/**
 * <!-- snippet: builtin/as -->
 * # `as`
 * The `as` operator allows safe type-casting.
 *
 * `x as Type` means:
 *
 * - If `x`'s [type tag][snippet/type-tag] is [compatible][snippet/type-compatibility]
 *   with `Type` then the result is `x`,
 * - otherwise [snippet/builtin/bubble].
 *
 * ```temper
 * sealed interface I {
 *   let s: String;
 * }
 * class A(public s: String) extends I {}
 * class B(public s: String) extends I {}
 *
 * for (let x: I of [new A("an A"), new B("a B")]) {
 *   console.log((x as A).s orelse "cast failed");
 * }
 * //!outputs "an A"
 * //!outputs "cast failed"
 * ```
 *
 * Note that `as` renames rather than casts in deconstructing
 * assignment context such as in
 * `let { exportedName as localName } = import("...");`.
 */
internal object AsFunction : AsLikeFunction(asBuiltinName) {
    override val runtimeTypeOperation = RuntimeTypeOperation.As
}

/** Inserted by the compiler for known safe cases. */
internal object AssertAsFunction : AsLikeFunction(BuiltinName("assertAs"), callMayFailPerSe = false) {
    override val runtimeTypeOperation = RuntimeTypeOperation.AssertAs

    override fun doCheck(x: Value<*>, reifiedType: ReifiedType): PartialResult {
        // Let it throw on fail. TODO Throw explicit `Panic()`?
        return super.doCheck(x, reifiedType) as Value<*>
    }
}

internal sealed class AsLikeFunction(
    builtinName: BuiltinName,
    final override val callMayFailPerSe: Boolean = true,
) : RttiCheckFunction(
    builtinName = builtinName,
    signature = run {
        // fn <T>(x: AnyValue?, tReified: Type): T throws Bubble
        val (defT, typeT) = makeTypeFormal(builtinName.builtinKey, "T")
        val returnType = if (callMayFailPerSe) {
            MkType2.result(typeT, WellKnownTypes.bubbleType2).get()
        } else {
            typeT
        }
        Signature2(
            returnType2 = returnType,
            hasThisFormal = false,
            requiredInputTypes = listOf(
                WellKnownTypes.anyValueOrNullType2,
                WellKnownTypes.typeType2,
            ),
            typeFormals = listOf(defT),
        )
    },
) {
    /** Upcasting can adjust type inference, which is ok. */
    override val allowUpChecks get() = true

    override fun doCheck(x: Value<*>, reifiedType: ReifiedType): PartialResult =
        if (reifiedType.valuePredicate(x)) {
            x
        } else {
            Fail
        }
}

/**
 * <!-- snippet: builtin/is -->
 * # `is`
 * The `is` operator allows type-checking.
 *
 * `x is Type` evaluates to true when `x`'s [type tag][snippet/type-tag]
 * is [compatible][snippet/type-compatibility] with `Type`.
 *
 * ```temper
 * class Foo {}
 * class Bar {}
 *
 * let isAFoo(x: AnyValue): Boolean {
 *   x is Foo
 * }
 *
 * console.log(isAFoo(new Foo()).toString());  //!outputs "true"
 * console.log(isAFoo(new Bar()).toString());  //!outputs "false"
 * ```
 */
internal object IsFunction : RttiCheckFunction(
    builtinName = isBuiltinName,
    signature = Signature2( // fn (x: AnyValue, t: Type): Boolean
        returnType2 = WellKnownTypes.booleanType2,
        hasThisFormal = false,
        requiredInputTypes = listOf(
            WellKnownTypes.anyValueOrNullType2,
            WellKnownTypes.typeType2,
        ),
    ),
) {
    override val callMayFailPerSe get() = false
    override val runtimeTypeOperation = RuntimeTypeOperation.Is

    /** No need to check if something is known subtype. */
    override val allowUpChecks get() = false

    override fun doCheck(x: Value<*>, reifiedType: ReifiedType): PartialResult =
        TBoolean.value(reifiedType.valuePredicate(x))
}

fun asReifiedType(result: PartialResult): ReifiedType? = TType.unpackOrNull(result as? Value<*>)
fun asReifiedTypeList(result: PartialResult): List<ReifiedType>? {
    val ls = TList.unpackOrNull(result as? Value<*>) ?: return null
    if (ls.any { it.typeTag != TType }) { return null }
    return ls.map { TType.unpack(it) }
}

inline fun asReifiedTypeOr(
    result: PartialResult,
    cb: InterpreterCallback,
    posForValue: Position? = null,
    or: (Fail) -> Nothing,
): ReifiedType {
    return asReifiedType(result)
        ?: or(
            cb.fail(
                template = MessageTemplate.ExpectedValueOfType,
                pos = posForValue ?: cb.pos,
                values = listOf("Type", result),
            ),
        )
}

inline fun asReifiedTypeListOr(
    result: PartialResult,
    cb: InterpreterCallback,
    posForValue: Position? = null,
    or: (Fail) -> Nothing,
): List<ReifiedType> {
    return asReifiedType(result)?.let { listOf(it) }
        ?: asReifiedTypeList(result)
        ?: or(
            cb.fail(
                template = MessageTemplate.ExpectedValueOfType,
                pos = posForValue ?: cb.pos,
                values = listOf("Types", result),
            ),
        )
}

fun RttiCheckFunction.problems(
    checked: Either<StaticType, Value<*>>,
    targetType: StaticType,
    pos: Position,
): List<LogEntry> {
    val exprType = checked.leftOrNull
    val exprCanBeNull = exprType?.let { canBeNull(it) }
    val targetCanBeNull = canBeNull(targetType)
    val exprTypeNotNull = exprType?.let { excludeNull(it) }
    val targetTypeNotNull = excludeNull(targetType)

    // check whether the cast can succeed at all.
    val (exprTypeNominal, targetTypeNominal) =
        if (
            exprCanBeNull == false && targetTypeNotNull.isNeverType ||
            !targetCanBeNull && exprTypeNotNull?.isNeverType == true
        ) {
            Pair(exprType as? NominalType, targetType as? NominalType)
        } else {
            Pair(exprTypeNotNull as? NominalType, targetTypeNotNull as? NominalType)
        }
    if (exprTypeNominal != null && targetTypeNominal != null) {
        val exprDef = exprTypeNominal.definition as? TypeShape
        val targetDef = targetTypeNominal.definition as? TypeShape
        if (
            exprDef?.abstractness == Abstractness.Concrete &&
            targetDef?.abstractness == Abstractness.Concrete &&
            exprDef != targetDef
        ) {
            // Disjoint, non-extensible class types
            return listOf(
                LogEntry(
                    Log.Error,
                    MessageTemplate.ImpossibleRttiCheck,
                    pos,
                    listOf(exprTypeNominal, targetTypeNominal),
                ),
            )
        }
    }

    val typeContext = TypeContext()
    val isUp = exprTypeNotNull?.let { typeContext.isSubType(it, targetTypeNotNull) } ?: false
    val isDown = !isUp && exprTypeNotNull?.let { typeContext.isSubType(targetTypeNotNull, it) } ?: false
    if (exprTypeNotNull != null && !(isUp || isDown)) {
        return LogEntry(
            Log.Error,
            MessageTemplate.IllegalRttiCheckUpDown,
            pos,
            listOf(listOf(targetType), exprType),
        ).let { listOf(it) }
    }

    val problems = mutableListOf<LogEntry>()
    val needsNullCheck = targetCanBeNull != exprCanBeNull
    val needsSubtypeCheck = exprTypeNotNull == null || isDown
    if (needsSubtypeCheck) {
        // Disallow casting to formal types
        val formalTypes = mutableListOf<NominalType>()
        // Disallow casting to non-sealed, connected types
        val connectedTypes = mutableListOf<NominalType>()
        // Disallow casting to types marked as not cast targets
        val disallowedDowncasts = mutableListOf<NominalType>()
        // Disallow introducing new type arguments.
        val introducedActualsTypes = mutableListOf<NominalType>()

        val sealedExprTypeNominals = lazy {
            requiredDefinitions(exprTypeNotNull ?: InvalidType)
                .filter { it.isSealed }.toSet()
        }

        for (nt in extractNominalTypes(targetType)) {
            when (val definition = nt.definition) {
                is TypeFormal -> formalTypes.add(nt)
                is TypeShape -> {
                    val mayDowncastTo = TBoolean.unpackOrNull(
                        definition.metadata[mayDowncastToSymbol]?.lastOrNull(),
                    )
                    if (mayDowncastTo != null) {
                        if (!mayDowncastTo) {
                            disallowedDowncasts.add(nt)
                        }
                        continue
                    }

                    if (connectedSymbol in definition.metadata) {
                        // Probably not reliably castable because there might be multiple
                        // Temper type definitions connecting to the same target-language type.
                        var distinguishableBySealed = false

                        // It's still ok to cast from the static type to the target type if there
                        // exists a path from the static type to the target type via a chain
                        // of sealed interfaces since each sealed interface knows how to
                        // differentiate their sub-types.
                        //
                        //     @connected("I") sealed interface I {}
                        //     @connected("J") sealed interface J {}
                        //     @connected("K") sealed interface K extends I {}
                        //
                        //     @connected("L") interface L extends J {}
                        //
                        //     @connected("C") class C extends I {}
                        //     @connected("D") class D extends J {}
                        //     @connected("E") class E extends L {}
                        //     @connected("F") class E extends K {}
                        //
                        // In the above, the following casts are distinguishable:
                        //     I to C
                        //     J to D
                        //     I to F, K to F
                        // But this cast is not because L is not sealed:
                        //     I to E
                        //     J to E
                        if (exprType == null) {
                            // If we do not know the expression's static type, for example
                            // because we're interpreting pre-typed code, then assume this
                            // passes if the target type is a sealed type.
                            distinguishableBySealed = true
                        } else {
                            // If walking from the sub-type to the super following only sealed
                            // super-type edges leads to one of the sealed expr nominals, then we're ok.
                            val sealedSupers = sealedExprTypeNominals.value
                            if (sealedSupers.isNotEmpty()) {
                                fun connectedBySealedChain(ts: TypeShape): Boolean =
                                    ts in sealedSupers ||
                                        ts.superTypes.any {
                                            val superTypeShape = it.definition as? TypeShape
                                            superTypeShape != null && superTypeShape.isSealed &&
                                                connectedBySealedChain(superTypeShape)
                                        }
                                distinguishableBySealed = connectedBySealedChain(definition)
                            }
                        }

                        if (!distinguishableBySealed) {
                            connectedTypes.add(nt)
                        }
                    }

                    // If it's a known subtype then all matching type args are actually woven through.
                    val exprActuals = exprTypeNominal?.bindings?.toSet() ?: setOf()
                    // We expect only invariants in the future, so for easier transition, check only invariants.
                    for ((formal, actual) in nt.definition.formals.zip(nt.bindings)) {
                        if (formal.variance == Variance.Invariant) {
                            if (actual !in exprActuals) {
                                // At least one target actual isn't in the expr type, and we can't conjure those.
                                introducedActualsTypes.add(nt)
                            }
                        }
                    }
                }
            }
        }
        val typeMessagePairs = listOf(
            MessageTemplate.IllegalRttiCheckActuals to introducedActualsTypes,
            MessageTemplate.IllegalRttiCheckFormals to formalTypes,
            MessageTemplate.IllegalRttiCheckMayDowncast to disallowedDowncasts,
            MessageTemplate.IllegalRttiCheckConnected to connectedTypes,
        )
        for ((messageTemplate, problemTypes) in typeMessagePairs) {
            if (problemTypes.isNotEmpty()) {
                val checkedDiagnostic: Any = when (checked) {
                    is Either.Left -> checked.item
                    is Either.Right -> checked.item
                }
                problems.add(
                    LogEntry(
                        Log.Error,
                        messageTemplate,
                        pos,
                        listOf(problemTypes, checkedDiagnostic),
                    ),
                )
            }
        }
    } else if (!needsNullCheck && (!allowUpChecks || targetType == exprType)) {
        // Nothing at all to check.
        problems.add(
            LogEntry(
                Log.Info,
                MessageTemplate.UnnecessaryRttiCheck,
                pos,
                listOf(targetType, exprType),
            ),
        )
    }
    return problems.toList()
}

private val TypeDefinition?.isSealed get() =
    this is TypeShape && sealedTypeSymbol in this.metadata

private fun requiredDefinitions(t: StaticType): List<TypeDefinition> = buildList {
    fun unpackDefinitions(st: StaticType) {
        when (st) {
            is NominalType -> add(st.definition)
            is AndType -> st.members.forEach { unpackDefinitions(it) }
            else -> {}
        }
    }
    unpackDefinitions(t)
}

private const val RTTI_CALL_TREE_SIZE = 3
fun isRttiCall(t: CallTree) =
    t.size == RTTI_CALL_TREE_SIZE &&
        t.childOrNull(0)?.functionContained is RttiCheckFunction
