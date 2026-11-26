# Numbers

Booleans and numbers: `Boolean`, the integer types `Int32`, aliased as
`Int`, and `Int64`, and the floating point `Float64`. std's
`std/collections` has `BitVector`, a growable vector of bits.

## Boolean

*Boolean* is a truth value that may be used in `if` and loop conditions.

⎀ syntax/BooleanLiteral -heading

    @connected
    @imu
    @mayDowncastTo(false) // Confusable with numbers and scalars on many backends
    export class Boolean extends Equatable<Boolean> {
      private constructor();

      /**
       * For [snippet/builtin/false], the string `"false"`.
       * For [snippet/builtin/true], the string `"true"`.
       */
      @connected
      public toString(): String {
        if (this) { "true" } else { "false" }
      }

      @connected
      @operator("==")
      public eq(other: Boolean): Boolean;
    }

    export let Int = Int32;

## Int32

*Int32* is the default, general-use signed integer value type in Temper.
Because it is so frequently used, the alias *Int* is encouraged in source
code.

⎀ syntax/int32/examples

    @connected
    @mayDowncastTo(false) // Confusable with scalars on many dynlang backends
    export class Int32 extends MapKey<Int32> {
      private constructor();

      @connected
      public toFloat64(): Float64;

      @connected
      public toInt64(): Int64;

      /** Supports radix 2 through 36. Doesn't prefix `+` for positive. */
      @connected
      public toString(radix: Int = 10): String;

      @connected
      public min(other: Int): Int {
        if (this <= other) { this } else { other }
      }

      @connected
      public max(other: Int): Int {
        if (this >= other) { this } else { other }
      }

      /** `this + 1`, the successor value used by [snippet/builtin/++]. */
      @connected
      public succ(): Int32 { this + 1 }

      /** `this - 1`, the predecessor value used by [snippet/builtin/--]. */
      @connected
      public pred(): Int32 { this - 1 }

      /** `-1` if `this < 0`, `0` if `this == 0`, or otherwise `1`. */
      @connected
      public get signum(): Int32 {
        (this >> 31) | (-this >>> 31)
      }

      @connected
      @operator("==")
      public eq(other: Int32): Boolean;
    }

## Int64

*Int64* is a double wide, signed integer type.

Possible *Int64* values range from -2<sup>63</sup> to (2<sup>63</sup> - 1).

⎀ syntax/int64/examples

    @connected
    @imu
    @mayDowncastTo(false) // Confusable with scalars on some dynlang backends.
    export class Int64 extends Equatable<Int64> {
      // TODO: Whether Int64 should be a MapKey, as Int32 and String are.
      private constructor();

      /** Bubbles if the result isn't within the exponent error of the original. */
      @connected
      public toFloat64(): Float64 throws Bubble;

      /** If outside bounds, returns a backend-dependent value. Use fuzz testing. */
      @connected
      public toFloat64Unsafe(): Float64;

      // TODO: Whether this should panic instead is open (temperlang/temper#164).
      /** Bubbles if the result isn't within bounds. */
      @connected
      public toInt32(): Int32 throws Bubble;

      // TODO: Define one out-of-range behavior for all lossy conversions,
      // such as wrapping (temperlang/temper#164).
      /** If outside bounds, returns a backend-dependent value. Use fuzz testing. */
      @connected
      public toInt32Unsafe(): Int32;

      /** Supports radix 2 through 36. Doesn't prefix `+` for positive. */
      @connected
      public toString(radix: Int = 10): String;

      @connected
      public min(other: Int64): Int64 {
        if (this <= other) { this } else { other }
      }

      @connected
      public max(other: Int64): Int64 {
        if (this >= other) { this } else { other }
      }

      /** `this + 1i64`, the successor value used by [snippet/builtin/++]. */
      @connected
      public succ(): Int64 { this + 1i64 }

      /** `this - 1i64`, the predecessor value used by [snippet/builtin/--]. */
      @connected
      public pred(): Int64 { this - 1i64 }

      @connected
      @operator("==")
      public eq(other: Int64): Boolean;
    }

## Float64

*Float64* is an [IEEE-754 64-bit (aka double-precision) floating point
number][FP64].

⎀ syntax/Float64Literal

⎀ syntax/float64/examples

[FP64]: https://en.wikipedia.org/wiki/Double-precision_floating-point_format

    @connected
    @imu
    @mayDowncastTo(false) // Confusable with scalars on many dynlang backends
    export class Float64 extends Equatable<Float64> {
      private constructor();

      /**
       * Truncates toward zero. Bubbles if the value isn't within 1 of Int32 limits.
       */
      @connected
      public toInt32(): Int throws Bubble;

      /** If outside bounds, returns a backend-dependent value. Use fuzz testing. */
      @connected
      public toInt32Unsafe(): Int;

      /**
       * Truncates toward zero. Bubbles if the value isn't within plus or minus
       * 0x1f_ffff_ffff_ffff, inclusive.
       */
      @connected
      public toInt64(): Int64 throws Bubble;

      /** If outside bounds, returns a backend-dependent value. Use fuzz testing. */
      @connected
      public toInt64Unsafe(): Int64;

      @connected
      public toString(): String;

      // Constants.

      @connected
      public static e = 2.718281828459045;

      @connected
      public static pi = 3.141592653589793;

      // Math ops.

      @connected
      public abs(): Float64;

      @connected
      public acos(): Float64;

      @connected
      public asin(): Float64;

      @connected
      public atan(): Float64;

      /** The y coordinate is this object. */
      @connected
      public atan2(x: Float64): Float64;

      @connected
      public ceil(): Float64;

      @connected
      public cos(): Float64;

      @connected
      public cosh(): Float64;

      @connected
      public exp(): Float64;

      @connected
      public expm1(): Float64;

      @connected
      public floor(): Float64;

      @connected
      public log(): Float64;

      @connected
      public log10(): Float64;

      @connected
      public log1p(): Float64;

      /** Result is NaN if either is NaN. */
      @connected
      public max(other: Float64): Float64;

      /** Result is NaN if either is NaN. */
      @connected
      public min(other: Float64): Float64;

      /**
       * Like Python's *math.isclose*, except NaN is near NaN, since Temper's
       * `==` treats NaN as equal to itself.
       */
      @connected
      public near(
        other: Float64,
        relTol: Float64 = 1e-9,
        absTol: Float64 = 0.0,
      ): Boolean {
        let margin = (this.abs().max(other.abs()) * relTol).max(absTol);
        // An infinity is near only itself. Temper's `<=` orders NaN above all
        // else, but a NaN margin admits nothing.
        this == other || this.abs() != Infinity && other.abs() != Infinity &&
          margin != NaN && (this - other).abs() <= margin
      }

      @connected
      public round(): Float64;

      @connected
      public sign(): Float64;

      @connected
      public sin(): Float64;

      @connected
      public sinh(): Float64;

      @connected
      public sqrt(): Float64;

      @connected
      public tan(): Float64;

      @connected
      public tanh(): Float64;

      /** `this + 1.0`, the successor value used by [snippet/builtin/++]. */
      @connected
      public succ(): Float64 { this + 1.0 }

      /** `this - 1.0`, the predecessor value used by [snippet/builtin/--]. */
      @connected
      public pred(): Float64 { this - 1.0 }

      @connected
      @operator("==")
      public eq(other: Float64): Boolean;
    }
