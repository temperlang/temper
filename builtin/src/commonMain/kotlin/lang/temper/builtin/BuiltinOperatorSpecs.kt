package lang.temper.builtin

import lang.temper.lexer.Operator
import lang.temper.lexer.TokenType
import lang.temper.value.CallableValue

/**
 * Keys match the operator specification format described with [lang.temper.value.operatorSymbol].
 */
val builtinOperatorSpecs: Map<String, List<CallableValue>> = mapOf(
    /**
     * <!-- snippet: builtin/+ -->
     * # `+`
     * The builtin `+` operator has six variants:
     * - *Infix* with two [snippet/type/Int32]s: signed addition
     * - *Prefix* with one [snippet/type/Int32]: numeric identity
     * - *Infix* with two [snippet/type/Int64]s: signed addition
     * - *Prefix* with one [snippet/type/Int64]: numeric identity
     * - *Infix* with two [snippet/type/Float64]s: signed addition
     * - *Prefix* with one [snippet/type/Float64]: numeric identity
     *
     * ```temper
     * 1   + 2   == 3   &&
     * 1.0 + 2.0 == 3.0 &&
     * +1        == 1   &&
     * +1.0      == 1.0
     * ```
     *
     * As explained above, you cannot mix [snippet/type/Int32] and
     * [snippet/type/Float64] inputs:
     *
     * ```temper FAIL
     * 1 + 1.0
     * ```
     *
     * `+` does not work on [snippet/type/String]s.  Use [snippet/syntax/string/interpolation] instead.
     *
     * ```temper FAIL
     * "foo" + "bar"
     * ```
     */
    "+_" to listOf(
        BuiltinFuns.plusIntFn,
        BuiltinFuns.plusLongFn,
        BuiltinFuns.plusFloatFn,
    ),
    "_+_" to listOf(
        BuiltinFuns.plusIntIntFn,
        BuiltinFuns.plusLongLongFn,
        BuiltinFuns.plusFloatFloatFn,
    ),

    /**
     * <!-- snippet: builtin/- -->
     * # `-`
     * The builtin `-` operator has six variants like [snippet/builtin/+].
     *
     * ```temper
     * 3   - 1   == 2   &&
     * 3.0 - 1.0 == 2.0 &&
     * -3        <  0   &&
     * -3.0      <  0.0
     * ```
     *
     * As with `+`, you cannot mix [snippet/type/Int32] and [snippet/type/Float64] inputs:
     *
     * ```temper FAIL
     * 1 + 1.0
     * ```
     *
     * The `-` operator is left-associative:
     *
     * ```temper
     * 1 - 1 - 1 == (1 - 1) - 1 &&
     * 1 - 1 - 1 == -1
     * ```
     *
     * Since there is a [snippet/builtin/--] operator, `--x` is not a negation of a negation.
     *
     * ```temper
     * var x = 1;
     * +x == -(-x) &&  // double negation is identity
     * --x == 0        // but two adjacent `-` means pre-decrement
     * ```
     */
    "-_" to listOf(
        BuiltinFuns.minusIntFn,
        BuiltinFuns.minusLongFn,
        BuiltinFuns.minusFloatFn,
    ),
    "_-_" to listOf(
        BuiltinFuns.minusIntIntFn,
        BuiltinFuns.minusLongLongFn,
        BuiltinFuns.minusFloatFloatFn,
    ),

    /**
     * <!-- snippet: builtin/%2A : operator `*` -->
     * # Multiplication `*`
     * Infix `*` allows multiplying numbers.
     *
     * Given two [snippet/type/Int32]s it produces an *Int*, given two [snippet/type/Int64]s it
     * produces an *Int64*, and given two [snippet/type/Float64]s it produces a *Float64*.
     *
     * ```temper
     * 3   * 4   == 12   &&
     * 3.0 * 4.0 == 12.0
     * ```
     */
    "_*_" to listOf(
        BuiltinFuns.timesIntIntFn,
        BuiltinFuns.timesLongLongFn,
        BuiltinFuns.timesFloatFloatFn,
    ),

    /**
     * <!-- snippet: builtin/%2A%2A : operator `**` -->
     * # Exponentiation `**`
     * Infix `**` allows raising one number to the power of another.
     *
     * Given two [snippet/type/Float64]s it produces a *Float64*.
     *
     * ```temper
     * 3.0 **  2.0 == 9.0 &&
     * 4.0 ** -0.5 == 0.5
     * ```
     */
    "_**_" to listOf(
        BuiltinFuns.powFloatFloatFn,
    ),

    /**
     * <!-- snippet: builtin/%2F : operator `/` -->
     * # Division `/`
     * Infix `/` allows dividing numbers.
     *
     * Given two [snippet/type/Int32]s it produces an *Int*, given two [snippet/type/Int64]s it
     * produces an *Int64*, and given two [snippet/type/Float64]s it produces a *Float64*.
     *
     * ```temper
     * 12   / 3   == 4    &&
     * 12.0 / 3.0 == 4.0
     * ```
     *
     * Integer division [rounds towards zero].
     *
     * ```temper
     *  7   / 2   ==  3   &&
     * -7   / 2   == -3   &&
     *  7.0 / 2.0 ==  3.5 &&
     * -7.0 / 2.0 == -3.5
     * ```
     *
     * Division by zero has [snippet/type/Bubble].
     *
     * ```temper
     * (1 / 0) orelse console.log("div by zero");
     * //!outputs "div by zero"
     * ```
     *
     * Float64 division by zero is a *Bubble* too.
     *
     * ```temper
     * console.log("${ (0.0 /  0.0).toString() orelse "Bubble" }"); //!outputs "Bubble"
     * console.log("${ (1.0 /  0.0).toString() orelse "Bubble" }"); //!outputs "Bubble"
     * console.log("${ (1.0 / -0.0).toString() orelse "Bubble" }"); //!outputs "Bubble"
     * ```
     *
     * [IEEE-754]: https://en.wikipedia.org/wiki/IEEE_754
     * [rounds towards zero]: https://en.wikipedia.org/wiki/Rounding#Rounding_toward_zero
     */
    "_/_" to listOf(
        BuiltinFuns.divIntIntFn,
        BuiltinFuns.divLongLongFn,
        BuiltinFuns.divFloatFloatFn,
    ),

    /**
     * <!-- snippet: builtin/%25 : operator `%` -->
     * # Remainder `%`
     * Given two [snippet/type/Int32]s it produces an *Int*,
     * given two [snippet/type/Int64]s it produces an *Int64*,
     * and given two [snippet/type/Float64]s it produces a *Float64*.
     *
     * ```temper
     * 13   % 3   == 1    &&
     * 13.0 % 3.0 == 1.0
     * ```
     *
     * Modulus by Zero [bubbles][snippet/type/Bubble]
     * ```temper
     * (1 % 0) orelse console.log("mod by zero");
     * //!outputs "mod by zero"
     * ```
     * ```temper
     * (1.0 % 0.0) orelse console.log("mod by zero");
     * //!outputs "mod by zero"
     * ```
     */
    "_%_" to listOf(
        BuiltinFuns.modIntIntFn,
        BuiltinFuns.modLongLongFn,
        BuiltinFuns.modFloatFloatFn,
    ),

    /**
     * <!-- snippet: builtin/& : `&` -->
     * # Operator `&`, bitwise and
     * The `&` operator can be applied in two ways:
     *
     * - To [snippet/type/Int32]s it acts as a [bitwise operator][snippet/bitwise-and].
     * - To types it produces an [intersection type][snippet/type/intersection-fn]
     *
     * ⎀ bitwise-and
     *
     * ⎀ type/intersection-fn
     * <!-- /snippet -->
     *
     * <!-- snippet: bitwise-and -->
     * # *Int* `&`
     *
     * Takes two [snippet/type/Int32]s or two [snippet/type/Int64]s and returns the
     * integer that has any bit set that is set in both inputs.
     *
     * ```temper
     * // Using binary number syntax
     * (0b0010101 &
     *  0b1011011) ==
     *  0b0010001
     * ```
     */
    "_&_" to listOf(
        BuiltinFuns.ampIntIntFn,
        BuiltinFuns.ampLongLongFn,
        TypeIntersectionFun,
    ),

    /**
     * <!-- snippet: builtin/| : `|` -->
     * # Operator `|`, bitwise or
     * The `|` operator performs bitwise union.
     *
     * It takes two [snippet/type/Int32]s or two [snippet/type/Int64]s and returns
     * the integer of the same size that has any bit set that is set in either input.
     *
     * ```temper
     * // Using binary number syntax
     * (0b0010101 |
     *  0b1011011) ==
     *  0b1011111
     * ```
     */
    "_|_" to listOf(
        BuiltinFuns.barIntIntFn,
        BuiltinFuns.barLongLongFn,
    ),

    /**
     * <!-- snippet: builtin/~ : `~` -->
     * # Operator `~`, bitwise inverse
     * The `~` operator negates the bits in an integer.
     *
     * Given an [snippet/type/Int32] or [snippet/type/Int64] it returns the integer
     * of the same size with the opposite bits.
     *
     * ```temper
     * // Using binary number syntax
     * ~0b0000_0001_0010_0011_0100_0101_0110_0111 ==
     *  0b1111_1110_1101_1100_1011_1010_1001_1000
     * ```
     */
    "~_" to listOf(
        BuiltinFuns.bitInverseIntFn,
        BuiltinFuns.bitInverseLongFn,
    ),

    /**
     * <!-- snippet: builtin/^ : `^` -->
     * # Operator `^`, bitwise-xor
     * The bitwise-xor (`^`) operator takes two [snippet/type/Int32]s or
     * two [snippet/type/Int64]s and returns an integer of the same size
     * that has each bit set when the corresponding bits in the inputs
     * are different.
     *
     * ```temper
     * // Using binary number syntax
     * (0b1111_0000_1111_0000_1111_0000_1111_0000 ^
     *  0b1010_1010_1010_1010_0101_0101_0101_0101) ==
     *  0b0101_1010_0101_1010_1010_0101_1010_0101
     * ```
     */
    "_^_" to listOf(
        BuiltinFuns.bitXorIntIntFn,
        BuiltinFuns.bitXorLongLongFn,
    ),

    /**
     * <!-- snippet: builtin/<< : `<<` -->
     * # Operator `<<`, shift left
     * The left shift (`<<`) operator takes an [snippet/type/Int32] or a
     * [snippet/type/Int64] to shift and an [snippet/type/Int32] which is
     * the number of bits to shift by.
     *
     * All but the 5 (for *Int32*) or 6 (for *Int64*) least significant bits of the right
     * operand are ignored.
     *
     * ```temper
     * // Using binary number syntax
     * (0b0000_0001_0101 << 3) ==
     * //        / _/ /
     * //       / /  /
     * //      / /  /
     *  0b0000_1010_1000
     * ```
     */
    "_<<_" to listOf(
        BuiltinFuns.shlIntIntFn,
        BuiltinFuns.shlLongLongFn,
    ),

    /**
     * <!-- snippet: builtin/>> : `>>` -->
     * # Operator `>>`, shift right
     * The right shift (`>>`) operator takes an [snippet/type/Int32] or a
     * [snippet/type/Int64] to shift and an [snippet/type/Int32] which is
     * the number of bits to shift by.
     *
     * All but the 5 (for *Int32*) or 6 (for *Int64*) least significant bits of the right
     * operand are ignored.
     *
     * ```temper
     * // Using binary number syntax
     * (0b0000_1010_1010 >> 3) ==
     * //       \ \_ \ \
     * //        \  \ \ *
     * //         \  \ \
     *  0b0000_0001_0101
     * ```
     *
     * Unlike the [snippet/builtin/>>>] operator, this operator is sign extending.
     * When shifting right by *n* bits, the *n* highest bits in the output are copied
     * from the most-significant bit in the input.
     *
     * ```temper
     * (0x8000_0000_0000_0000 >> 2) ==
     * // |\
     * // |/\
     *  0xE000_0000_0000_0000
     * ```
     */
    "_>>_" to listOf(
        BuiltinFuns.shrIntIntFn,
        BuiltinFuns.shrLongLongFn,
    ),

    /**
     * <!-- snippet: builtin/>>> : `>>>` -->
     * # Operator `>>>`, shift right (zero extending)
     * The right shift (`>>>`) operator takes an [snippet/type/Int32] or a
     * [snippet/type/Int64] to shift and an [snippet/type/Int32] which is
     * the number of bits to shift by.
     *
     * All but the 5 (for *Int32*) or 6 (for *Int64*) least significant bits of the right
     * operand are ignored.
     *
     * ```temper
     * // Using binary number syntax
     * (0b0000_1010_1010 >>> 3) ==
     * //       \ \_ \ \
     * //        \  \ \ *
     * //         \  \ \
     *  0b0000_0001_0101
     * ```
     *
     * Unlike the [snippet/builtin/>>] operator, this operator is zero extending.
     * When shifting right by *n* bits, the *n* highest bits in the output are copied
     * from the most-significant bit in the input.
     *
     * ```temper
     * (0x8000_0000_0000_0000 >>> 2) ==
     *  0x2000_0000_0000_0000
     * ```
     */
    "_>>>_" to listOf(
        BuiltinFuns.uShrIntIntFn,
        BuiltinFuns.uShrLongIntFn,
    ),
)

/**
 * Given a compound assignment operator, like `+=`, returns the simple operator
 * like `+`.  This is meant to allow desugaring complex operations, e.g.
 * `x += y` might desugar to `x = x + y` which combines regular assignment and a
 * simple operator instead of using a compound assignment operator.
 */
fun simpleBuiltinKeyFromCompoundOperator(builtinKey: String?): String? =
    if (
        builtinKey != null &&
        builtinKey != "=" && // is an assignment operator, but is not compound
        Operator.isProbablyAssignmentOperator(builtinKey, TokenType.Punctuation)
    ) {
        builtinKey.dropLast(1)
    } else {
        null
    }
