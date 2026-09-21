package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.lexer.Operator
import lang.temper.lexer.TokenType
import lang.temper.name.BuiltinName
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.HelpSnippet
import lang.temper.value.InterpreterCallback
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.helpSnippet

/**
 * Keys match the operator specification format described with [lang.temper.value.operatorSymbol].
 */
@Suppress("KDocUnresolvedReference")
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
     * 1    + 2    == 3    &&
     * 1.0  + 2.0  == 3.0  &&
     * 1i64 + 2i64 == 3i64 &&
     * +1          == 1    &&
     * +1.0        == 1.0  &&
     * +1i64       == 1i64
     * ```
     *
     * As explained above, you cannot mix [snippet/type/Int32] and
     * [snippet/type/Float64] inputs nor either with [snippet/type/Int64]:
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
        BuiltinFuns.plusIntIntFn
            .also { helpSnippet(it, "addition operator (`+`)", "builtin/+") },
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
        BuiltinFuns.minusIntIntFn
            .also { helpSnippet(it, "subtraction operator (`-`)", "builtin/-") },
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
        BuiltinFuns.timesIntIntFn
            .also { helpSnippet(it, "multiplication operator (`*`)", "builtin/%2A") },
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
        BuiltinFuns.powFloatFloatFn
            .also { helpSnippet(it, "power operator (`**`)", "builtin/%2A%2A") },
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
        BuiltinFuns.divIntIntFn
            .also { helpSnippet(it, "division operator (`/`)", "builtin/%2F") },
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
        BuiltinFuns.modIntIntFn
            .also { helpSnippet(it, "modulus operator (`%`)", "builtin/%25") },
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
        BuiltinFuns.ampIntIntFn
            .also { helpSnippet(it, "bitwise and (`&`)", "builtin/&") },
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
        BuiltinFuns.barIntIntFn
            .also { helpSnippet(it, "bitwise or (`|`)", "builtin/|") },
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
        BuiltinFuns.bitInverseIntFn
            .also { helpSnippet(it, "bitwise inverse (`~`)", "builtin/~") },
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
        BuiltinFuns.bitXorIntIntFn
            .also { helpSnippet(it, "bitwise xor (`^`)", "builtin/^") },
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
        BuiltinFuns.shlIntIntFn
            .also { helpSnippet(it, "left shift (`<<`)", "builtin/<<") },
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
        BuiltinFuns.shrIntIntFn
            .also { helpSnippet(it, "right shift (`>>`)", "builtin/>>") },
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
        BuiltinFuns.uShrIntIntFn
            .also { helpSnippet(it, "unsigned right shift (`>>>`)", "builtin/>>>") },
        BuiltinFuns.uShrLongIntFn,
    ),

    /**
     * <!-- snippet: builtin/<=> -->
     * # `<=>`
     * `a <=> b` results in an [Int][snippet/type/Int32] based on whether *a* orders before, after, or
     * with *b*, and is a compile-time error if the two are not mutually comparable.
     *
     * - `a <=> b` is `-1` if *a* orders **before** *b*
     * - `a <=> b` is `0` if *a* orders **with** *b*
     * - `a <=> b` is `1` if *a* orders **after** *b*
     *
     * ```temper
     * (   42 <=>   123) == -1 &&  //    42 orders before   123
     * (  1.0 <=>   1.0) == 0  &&  //   1.0 orders with     1.0
     * ("foo" <=> "bar") == 1      // "foo" orders after  "bar"
     * ```
     *
     * ⎀ general-comparison/algo
     *
     * ⎀ general-comparison/caveats
     *
     * <!-- snippet: general-comparison/algo -->
     * # General comparison algorithm
     * The general comparison algorithm is designed to allow for easy structural comparison of
     * data values that work the same regardless of target language.
     *
     * The whole group of comparison operators (`<`, `<=`, `>=`, `>`) are all syntactic sugar
     * for `<=>` whose semantics are defined here.
     *
     * For example, `a < b` is really syntactic sugar for `(a <=> b) < 0`: an application
     * of trinary comparison with its [snippet/type/Int32] result compared using
     * a builtin *Int32* to *Int32* comparison.
     *
     * [snippet/type/Int32]s are compared based on their position on the number line.
     * No surprises here.
     *
     * ```temper
     * -1 < 0 && 0 < 1 && 1 < 2
     * ```
     *
     * [snippet/type/Float64]s are also compared numerically.
     *
     * ```temper
     * -1.0 < 0.0 && 0.0 < 1.0 && 1.0 < 2.0
     * ```
     *
     * But the default comparison operators are meant to support structural comparison of records
     * so see also [caveats][snippet/general-comparison/caveats] for how *Float64* ordering differs
     * from some other languages.
     *
     * [snippet/type/String]s are compared lexicographically based on their code-points.
     *
     * ```temper
     * "foo" > "bar"
     * ```
     *
     * See also [caveats][snippet/general-comparison/caveats] for *String* related ordering,
     * how this differs from some programming language that prefer UTF-16 based ordering.
     *
     * ## Custom comparison for classes
     *
     * A user defined class may be made comparable by defining an
     * [`@operator("<=>")`][snippet/builtin/@operator] method or function.
     *
     * It is the implementors responsibility to ensure the usual properties
     * of ordering apply: comparison is transitive (a < b && b < c -> a < c),
     * asymmetric (a < b -> b > a), and reflexive (a <= a && a >= a && !(a < a)).
     * (For asymmetric, only the sign3 of the `<=>` matters, so it may not be the
     *  case that `(a <=> b) == -(b <=> a)`, just that
     *  `(a <=> b).signum == -((b <=> a).signum)`.
     *
     * <!-- snippet: general-comparison/caveats -->
     * # General Comparison Caveats
     *
     * ## String Ordering Caveats
     * [snippet/type/String] ordering based on code-points means that [supplementary code-points]
     * (code-points greater than U+10000) sort higher than all [basic plane] code-points,
     *
     * ```temper
     * "\u{10000}" > "\u{FFFF}" // Hex code-point escapes
     * ```
     *
     * Developers used to lexicographic [UTF-16] might be surprised since UTF-16 ordering
     * treats each supplementary code-point as two [surrogate]s in the range \[0xD800, 0xDFFF\].
     * The first string above would be `"\uD800\uDC00"` written in JSON with each surrogate
     * separately escaped. In some languages, that might compare as less than "\u{FFFF}", but
     * Temper views all strings in terms of full code-points, or more precisely, in terms of
     * Unicode [scalar value]s, which exclude surrogate codes appearing by themselves.
     *
     * ## Float64 Ordering Caveats
     *
     * ⎀ float64-comparison-details -heading
     *
     * [basic plane]: https://unicode.org/glossary/#basic_multilingual_plane
     * [scalar value]: https://unicode.org/glossary/#unicode_scalar_value
     * [supplementary code-points]: https://unicode.org/glossary/#supplementary_code_point
     * [surrogate]: https://unicode.org/glossary/#surrogate_code_point
     * [UTF-16]: https://unicode.org/glossary/#UTF_16
     */
    "_<=>_" to listOf(
        BuiltinFuns.cmpInt32Fn.also {
            helpSnippet(it, "Comparison operator", "builtin/<=>")
        },
        BuiltinFuns.cmpInt64Fn,
        BuiltinFuns.cmpFloat64Fn,
        BuiltinFuns.cmpStringFn,
        BuiltinFuns.cmpBooleanFn,
    ),

    /**
     * <!-- snippet: builtin/< -->
     * # Operator `<`, less-than
     * `a < b` is [snippet/builtin/true] when *a* orders before *b*, and is a compile-time error
     * if the two are not mutually comparable.
     *
     * `<` is part of a family of related operators including `<`, `<=`, `>=`, `>`,
     * and `<=>`.  Especially see [`<=>`][snippet/builtin/<=>] for details on how
     * comparison works for Temper-defined and user-defined types.
     *
     * See the [snippet/general-comparison/algo] for details of how they are compiled and
     * especially the [snippet/general-comparison/caveats].
     *
     * ⎀ syntax/less-than-space-sensitivity
     *
     * <!-- snippet: syntax/less-than-space-sensitivity -->
     * # Syntactic corner case: `<` ambiguity
     *
     * Tldr: always put spaces around infix operators like `<`.
     *
     * The `<` operator means comparison, but in a type expression, it can also be a bracket.
     *
     * ```temper inert
     * console.log(c < d);  // Compare c to d
     *
     * let x:      C<D>;    // x's type is C parameterized with D
     * ```
     *
     * Other languages also have two meanings for `<`.  Temper does not want to enforce a
     * hard grammatic distinction between types and expressions, and to avoid workarounds
     * like extra turbofish syntax.
     *
     * In Temper the rule is:
     *
     * > If a `<` token is not preceded by a space or comment, then it is an angle bracket
     * > otherwise it is a comparison operator.
     *
     * (In Temper, types are upper-case by convention, but we cannot use case as in `C<D>`
     * above to disambiguate because Temper assigns no semantic significance to identifier
     * case, to better support non-European identifiers which are mostly in (unicameral)
     * writing systems.)
     *
     * For example:
     *
     * ```temper inert
     * // ┏━━━━ This space makes the difference
     * f(a < b, c > d);  // pass two booleans to f
     * f(A<B, C>);       // pass one type with two parameters to f (a macro?)
     *
     * class C<T> {}  // A class declaration with a formal type parameter
     *
     * // Type argument lists can be spread over multiple lines.
     * class C< // No space **before**, so this `<` starts C's type argument list.
     *   T
     * > {}
     *
     * class C <T>    // ERROR: trying to compare `class C` to `T` probably won't work
     *
     * class C  // ERROR: space before '<'
     * <T> {}
     * ```
     *
     * The rule to determine whether a `>` token is an angle bracket or a comparison
     * operator is purely made based on preceding tokens.
     *
     * > If there are zero preceding `<` bracket tokens without a `>` partner then it
     * > is a bracket, otherwise it is an infix operator.
     *
     * This code doesn't mean much, but the parsing rules are clear.
     *
     * ```temper inert
     * // ┏━━━┓ 3 open `<` brackets
     *   A<B<C<D>>>>
     * //       ┗┳┛┗━━━━━━━ This fourth one is an infix comparison operator
     * //        ┃
     * // Make these 3 close `>` brackets
     * ```
     *
     * To avoid confusion, just put spaces around all your infix operators.
     */
    "_<_" to listOf(
        BuiltinFuns.ltIntFn.also {
            helpSnippet(it, "Less than operator", "builtin/<")
        },
        // Others by desugaring to <=>
    ),

    /**
     * <!-- snippet: builtin/<= -->
     * # `<=`
     * `a <= b` is [snippet/builtin/true] when *a* orders with or before *b*,
     * and is a compile-time error if the two are not mutually comparable.
     *
     * `<=` is part of a family of related operators including `<`, `<=`, `>=`, `>`,
     * and `<=>`.  Especially see [`<=>`][snippet/builtin/<=>] for details on how
     * comparison works for Temper-defined and user-defined types.
     *
     * See the [snippet/general-comparison/algo] for details of how they are compiled and
     * especially the [snippet/general-comparison/caveats].
     */
    "_<=_" to listOf(
        BuiltinFuns.leIntFn.also {
            helpSnippet(it, "Less than or equals operator", "builtin/<=")
        },
        // Others by desugaring to <=>
    ),

    /**
     * <!-- snippet: builtin/> -->
     * # `>`
     * `a > b` is [snippet/builtin/true] when *a* orders after *b*, and is a compile-time
     * error if the two are not mutually comparable.
     *
     * `>` is part of a family of related operators including `<`, `<=`, `>=`, `>`,
     * and `<=>`.  Especially see [`<=>`][snippet/builtin/<=>] for details on how
     * comparison works for Temper-defined and user-defined types.
     *
     * See the [snippet/general-comparison/algo] for details of how they are compiled and
     * especially the [snippet/general-comparison/caveats].
     */
    "_>_" to listOf(
        BuiltinFuns.gtIntFn.also {
            helpSnippet(it, "Greater than operator", "builtin/>")
        },
        // Others by desugaring to <=>
    ),

    /**
     * <!-- snippet: builtin/>= -->
     * # `>=`
     * `a >= b` is [snippet/builtin/true] when *a* orders after or with *b*, and is a compile-time
     * error if the two are not mutually comparable.
     *
     * `>=` is part of a family of related operators including `<`, `<=`, `>=`, `>`,
     * and `<=>`.  Especially see [`<=>`][snippet/builtin/<=>] for details on how
     * comparison works for Temper-defined and user-defined types.
     *
     * See the [snippet/general-comparison/algo] for details of how they are compiled and
     * especially the [snippet/general-comparison/caveats].
     */
    "_>=_" to listOf(
        BuiltinFuns.geIntFn.also {
            helpSnippet(it, "Greater than or equals operator", "builtin/>=")
        },
        // Others by desugaring to <=>
    ),

    /**
     * <!-- snippet: builtin/== -->
     * # `==`
     * `a == b` is the default equivalence operation.
     *
     * For builtin types, that underlying check is based on the
     * [snippet/general-comparison/algo] even though a type does not need to
     * be ordered to be comparable.
     *
     * ```temper
     * // Int32
     * console.log("0 == 0 -> ${0 == 0}"); //!outputs "0 == 0 -> true"
     * console.log("0 == 1 -> ${0 == 1}"); //!outputs "0 == 1 -> false"
     *
     * // Int64
     * console.log("0i64 == 0i64 -> ${0i64 == 0i64}"); //!outputs "0i64 == 0i64 -> true"
     * console.log("0i64 == 1i64 -> ${0i64 == 1i64}"); //!outputs "0i64 == 1i64 -> false"
     *
     * // Float64
     * // NaN and signed zero are not corner cases for ==`.`
     * console.log("0.0 == 0.0 -> ${0.0 == 0.0}"); //!outputs "0.0 == 0.0 -> true"
     * console.log("0.0 == 1.0 -> ${0.0 == 1.0}"); //!outputs "0.0 == 1.0 -> false"
     *
     * // String
     * // Unlike `<=>`, there are no UTF-8 vs UTF-16 caveats for ==`.`
     * console.log("'a' == 'a' -> ${'a' == 'a'}"); //!outputs "'a' == 'a' -> true"
     * console.log("'a' == 'b' -> ${'a' == 'b'}"); //!outputs "'a' == 'b' -> false"
     * ```
     *
     * The related [`!=` operator][snippet/builtin/!=] is just the negation of
     * the `==` operator.
     *
     * Comparing incomparable values is a compile time error.
     *
     * ```temper FAIL
     * 0 == "0"
     * ```
     *
     * But any value may be compared using `==` to `null`.
     *
     * ## Equivalence to `null`
     *
     * Only the `null` value is equivalent to itself.
     *
     * Equivalence is *null-safe*.  I.e., if `a` and `b` could be [snippet/builtin/null]
     * because their types use [snippet/builtin/%3F], then `a == b` is equivalent to the
     * below where *isNull* is a compiler-internal predicate for testing nullity:
     *
     * ```temper inert
     * if (isNull(a)) {
     *   isNull(b)
     * } else {
     *   !isNull(b) && a == b
     * }
     * ```
     *
     * The Temper compiler simplifies such complicated checks using type information
     * so `a == null` and `null == a` are equivalent to `isNull(a)`.
     *
     * The important takeaway, for Temper semantics are that `==` tests equivalence
     * using a type-specific equivalence function or method but only *after* checking
     * whether the operands are `null`.
     *
     * ```temper
     * console.log("null == null -> ${null == null}"); //!outputs "null == null -> true"
     * console.log("null == 1234 -> ${null == 1234}"); //!outputs "null == 1234 -> false"
     * console.log("1234 == null -> ${1234 == null}"); //!outputs "1234 == null -> false"
     * ```
     *
     * ⎀ equivalence/classes-and-interfaces
     *
     * <!-- snippet: equivalence/classes-and-interfaces -->
     * ## Equality for user-defined `class`es and `interface`s
     *
     * For `class` instances, equivalence is just a call to a method or function
     * with [`@operator("==")`][snippet/builtin/@operator] metadata.
     * Since [snippet/builtin/!=] is syntactic sugar for a boolean negation of
     * `==` applied to the same arguments, you don't need to define both
     * operators, and trying to define `!=` will not help.
     *
     * All operator `==` implementations should return [snippet/type/Boolean].
     * All such implementations should be reflexive: `a == b -> b == a` and
     * `a != b -> b != a` when both `a`'s and `b`'s type's provide.implemenations.
     *
     * If an implementation is defined on a [snippet/builtin/@sealed] `interface`
     * it should work for comparing all subtypes, perhaps by checking types and
     * delegating to subtype specific implementations.
     *
     * TODO: auto-derived structural equality.
     *
     * ```temper
     * class C {
     *   @operator("==")
     *   public equals(other: C?): Boolean { true } // Not a lot of difference.
     * }
     *
     * let c = new C();
     * let d = new C();
     *
     * console.log("c == c -> ${c == c}"); //!outputs "c == c -> true"
     * console.log("c == d -> ${c == d}"); //!outputs "c == d -> true"
     * // You can't call a method on `null`, but `==` is null safe.
     * console.log("null == c -> ${null == c}"); //!outputs "null == c -> false"
     * // Even though C.equals accepts null inputs, the null check happens
     * // before the method call, so this is false.
     * console.log("c == null -> ${c == null}"); //!outputs "c == null -> false"
     * ```
     */
    "_==_" to listOf(
        // Most work done by EqMacro
        BuiltinFuns.eqIntFn.also {
            helpSnippet(it, "Equal to operator", "builtin/==")
        },
        BuiltinFuns.eqInt64Fn,
        BuiltinFuns.eqFloat64Fn,
        BuiltinFuns.eqStringFn,
        BuiltinFuns.eqBooleanFn,
    ),

    /**
     * <!-- snippet: builtin/!= -->
     * # `!=`
     * `a != b` is the [snippet/type/Boolean] inverse of [snippet/builtin/==].
     *
     * Since `a != b` is syntactic sugar for `!(a == b)`, defining an
     * [equivalence operation][snippet/equivalence/classes-and-interfaces] once
     * will also enable using `!=` with that type.
     */
    "_!=_" to listOf(
        // Most work done by NeMacro
        // Placeholder to make REPL help work
        @HelpSnippet("Not equal to operator", "builtin/!=")
        object : BuiltinFun(BuiltinName("!="), EqMacro.sig) {
            override fun invoke(
                args: ActualValues,
                cb: InterpreterCallback,
                interpMode: InterpMode,
            ): PartialResult = NotYet
        },
    ),
)

/**
 * Given a compound assignment operator, like `+=`, returns the simple operator
 * like `+`.  This is meant to allow desugaring complex operations, e.g.
 * `x += y` might desugar to `x = x + y` which combines a regular assignment and a
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
