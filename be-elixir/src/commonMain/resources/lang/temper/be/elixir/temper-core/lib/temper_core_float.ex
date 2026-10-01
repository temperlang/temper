defmodule TemperCore.Float do
  @moduledoc """
  Temper's Float64, where it differs from the BEAM's float.

  - Temper prints a float the way JavaScript does, plain until the decimal
    exponent reaches 21 or falls below -6, but always with a point:
    `1.0`, not `1`, and `1.0e+25`, not `1e+25`.
  - Temper calls `-0.0` and `0.0` unequal and orders `-0.0` first. Since
    OTP 27, `===` already tells them apart; `<` does not.
  - The BEAM's floats have no infinity or NaN: overflow, `1.0 / 0.0` and
    `:math.sqrt(-1.0)` raise `ArithmeticError`, and those bit patterns do
    not even match out of a binary. A Temper Float64 is therefore a BEAM
    float or one of the atoms `:infinity`, `:neg_infinity` and `:nan`, and
    every float operation goes through this module.
  - Temper orders floats totally: `-Infinity < ... < -0.0 < 0.0 < ... <
    Infinity < NaN`, and `NaN == NaN`.
  """

  import Kernel, except: [abs: 1, max: 2, min: 2, rem: 2, round: 1]

  defguardp special(x) when x in [:infinity, :neg_infinity, :nan]

  @doc "A Temper Float64: a BEAM float, or `:infinity`, `:neg_infinity` or `:nan`."
  defguard float64?(x) when is_float(x) or x in [:infinity, :neg_infinity, :nan]

  # An integer where a Float64 belongs is the caller's mistake. The clauses
  # for the special values would otherwise read it as one: add(1, 2.0) was
  # 2.0 and mul(3, 2.0) was :infinity.
  defp not_float64!(fun, args) do
    bad = Enum.find(args, &(not float64?(&1)))

    raise ArgumentError,
          "TemperCore.Float.#{fun} takes Float64 values (a float, :infinity, :neg_infinity or :nan), got: #{inspect(bad)}"
  end

  @doc "Whether the sign bit is set: true for -0.0 and -Infinity, false for NaN."
  def neg?(f) when not float64?(f), do: not_float64!(:neg?, [f])
  def neg?(:neg_infinity), do: true
  def neg?(f) when is_float(f), do: f < 0.0 or f === -0.0
  def neg?(_), do: false

  defp inf(true), do: :neg_infinity
  defp inf(false), do: :infinity
  defp zero(true), do: -0.0
  defp zero(false), do: 0.0

  def add(a, b) when is_float(a) and is_float(b) do
    a + b
  rescue
    ArithmeticError -> inf(a < 0.0)
  end

  def add(a, b) when not (float64?(a) and float64?(b)), do: not_float64!(:add, [a, b])
  def add(:nan, _), do: :nan
  def add(_, :nan), do: :nan
  def add(:infinity, :neg_infinity), do: :nan
  def add(:neg_infinity, :infinity), do: :nan
  def add(a, _) when special(a), do: a
  def add(_, b), do: b

  def sub(a, b) when is_float(a) and is_float(b) do
    a - b
  rescue
    ArithmeticError -> inf(a < 0.0)
  end

  def sub(a, b) when not (float64?(a) and float64?(b)), do: not_float64!(:sub, [a, b])
  def sub(a, b), do: add(a, neg(b))

  def neg(f) when not float64?(f), do: not_float64!(:neg, [f])
  def neg(f) when is_float(f), do: -f
  def neg(:infinity), do: :neg_infinity
  def neg(:neg_infinity), do: :infinity
  def neg(:nan), do: :nan

  def mul(a, b) when is_float(a) and is_float(b) do
    a * b
  rescue
    ArithmeticError -> inf(neg?(a) != neg?(b))
  end

  def mul(a, b) when not (float64?(a) and float64?(b)), do: not_float64!(:mul, [a, b])
  def mul(:nan, _), do: :nan
  def mul(_, :nan), do: :nan
  def mul(a, b) when a == 0.0 or b == 0.0, do: :nan
  def mul(a, b), do: inf(neg?(a) != neg?(b))

  def divide(a, b) when is_float(a) and is_float(b) do
    cond do
      b != 0.0 -> a / b
      a == 0.0 -> :nan
      true -> inf(neg?(a) != neg?(b))
    end
  rescue
    ArithmeticError -> inf(neg?(a) != neg?(b))
  end

  def divide(a, b) when not (float64?(a) and float64?(b)), do: not_float64!(:divide, [a, b])
  def divide(:nan, _), do: :nan
  def divide(_, :nan), do: :nan
  def divide(a, b) when special(a) and special(b), do: :nan
  def divide(a, b) when special(a), do: inf(neg?(a) != neg?(b))
  def divide(a, b), do: zero(neg?(a) != neg?(b))

  @doc "`%`: the remainder keeps the dividend's sign, as C's fmod does."
  def rem(a, b) when is_float(a) and is_float(b),
    do: if(b == 0.0, do: :nan, else: :math.fmod(a, b))

  def rem(a, b) when not (float64?(a) and float64?(b)), do: not_float64!(:rem, [a, b])
  def rem(a, b) when special(a) or b == :nan, do: :nan
  def rem(a, _), do: a

  @doc "`**`, with C's pow rules for the cases the BEAM raises on."
  def pow(a, b) when is_float(a) and is_float(b) do
    :math.pow(a, b)
  rescue
    ArithmeticError ->
      cond do
        a < 0.0 and b != Float.floor(b) -> :nan
        a == 0.0 -> inf(neg?(a) and odd?(b))
        true -> inf(a < 0.0 and odd?(b))
      end
  end

  def pow(a, b) when not (float64?(a) and float64?(b)), do: not_float64!(:pow, [a, b])
  def pow(_, b) when b == 0.0, do: 1.0
  def pow(a, _) when a == 1.0, do: 1.0
  def pow(:nan, _), do: :nan
  def pow(_, :nan), do: :nan

  def pow(a, :infinity), do: pow_inf(abs(a))
  def pow(a, :neg_infinity), do: pow_inf(divide(1.0, abs(a)))
  def pow(:infinity, b), do: if(b > 0.0, do: :infinity, else: 0.0)
  def pow(:neg_infinity, b) when b > 0.0, do: inf(odd?(b))
  def pow(:neg_infinity, b), do: zero(odd?(b))

  defp pow_inf(m) when m == 1.0, do: 1.0
  defp pow_inf(m), do: if(gt(m, 1.0), do: :infinity, else: 0.0)

  defp odd?(b) when is_float(b),
    do: b == Float.floor(b) and abs(b) < 9.007199254740992e15 and Kernel.rem(trunc(b), 2) != 0

  defp odd?(_), do: false

  @doc "The shortest round-tripping digits, laid out as Temper prints them."
  def to_string(:infinity), do: "Infinity"
  def to_string(:neg_infinity), do: "-Infinity"
  def to_string(:nan), do: "NaN"

  def to_string(f) when is_float(f) do
    if negative_zero?(f) or f < 0.0 do
      "-" <> positive(abs(f))
    else
      positive(f)
    end
  end

  defp positive(f) when f == 0.0, do: "0.0"

  defp positive(f) do
    {digits, n} = digits_and_exponent(f)
    k = String.length(digits)

    cond do
      k <= n and n <= 21 -> digits <> String.duplicate("0", n - k) <> ".0"
      0 < n and n <= 21 -> String.slice(digits, 0, n) <> "." <> String.slice(digits, n, k - n)
      -6 < n and n <= 0 -> "0." <> String.duplicate("0", -n) <> digits
      true -> exponent_form(digits, n - 1)
    end
  end

  defp exponent_form(digits, e) do
    {first, rest} = String.split_at(digits, 1)
    mantissa = if rest == "", do: first <> ".0", else: first <> "." <> rest
    sign = if e < 0, do: "-", else: "+"
    mantissa <> "e" <> sign <> Integer.to_string(Kernel.abs(e))
  end

  # value = 0.DIGITS x 10^n, as ECMAScript's Number::toString counts it
  defp digits_and_exponent(f) do
    short = :erlang.float_to_binary(f, [:short])

    {mantissa, exp} =
      case String.split(short, "e") do
        [m, e] -> {m, String.to_integer(e)}
        [m] -> {m, 0}
      end

    {whole, frac} =
      case String.split(mantissa, ".") do
        [w, fr] -> {w, fr}
        [w] -> {w, ""}
      end

    all = whole <> frac
    trimmed_left = String.trim_leading(all, "0")
    leading = String.length(all) - String.length(trimmed_left)
    digits = String.trim_trailing(trimmed_left, "0")
    {digits, String.length(whole) + exp - leading}
  end

  def negative_zero?(f), do: f === -0.0

  @doc "`is Float64`: a BEAM float or one of the three special atoms."
  def float?(x), do: is_float(x) or x in [:infinity, :neg_infinity, :nan]

  # where a value sits on Temper's number line, before comparing floats
  defp rank(:neg_infinity), do: 0
  defp rank(f) when is_float(f), do: 1
  defp rank(:infinity), do: 2
  defp rank(:nan), do: 3

  def eq(a, b) when not (float64?(a) and float64?(b)), do: not_float64!(:eq, [a, b])
  def eq(a, b), do: a === b
  def ne(a, b) when not (float64?(a) and float64?(b)), do: not_float64!(:ne, [a, b])
  def ne(a, b), do: a !== b

  def lt(a, b) when is_float(a) and is_float(b),
    do: a < b or (a == 0.0 and b == 0.0 and negative_zero?(a) and not negative_zero?(b))

  def lt(a, b) when not (float64?(a) and float64?(b)), do: not_float64!(:lt, [a, b])
  def lt(a, b), do: rank(a) < rank(b)
  def gt(a, b), do: lt(b, a)
  def le(a, b), do: not lt(b, a)
  def ge(a, b), do: not lt(a, b)

  @doc "`Float64.min`: NaN if either is, and -0.0 is the lesser zero."
  def min(a, b) when not (float64?(a) and float64?(b)), do: not_float64!(:min, [a, b])
  def min(a, b) when a == :nan or b == :nan, do: :nan
  def min(a, b), do: if(lt(b, a), do: b, else: a)
  def max(a, b) when not (float64?(a) and float64?(b)), do: not_float64!(:max, [a, b])
  def max(a, b) when a == :nan or b == :nan, do: :nan
  def max(a, b), do: if(lt(a, b), do: b, else: a)

  @doc "`Float64.sign()`: -1.0, 0.0 (keeping a negative zero), 1.0 or NaN."
  def sign(f) when not float64?(f), do: not_float64!(:sign, [f])
  def sign(:nan), do: :nan
  def sign(f) when f == :infinity or (is_float(f) and f > 0.0), do: 1.0
  def sign(f) when f == :neg_infinity or (is_float(f) and f < 0.0), do: -1.0
  def sign(f), do: f

  def abs(f) when not float64?(f), do: not_float64!(:abs, [f])
  def abs(f) when is_float(f), do: Kernel.abs(f)
  def abs(:neg_infinity), do: :infinity
  def abs(f), do: f

  def cmp(a, b) do
    cond do
      lt(a, b) -> -1
      lt(b, a) -> 1
      true -> 0
    end
  end

  @doc """
  `Float64.near()`: Python's `math.isclose`, as core.temper's comment says
  and the JS and Python backends do. Reading core.temper's body under
  Temper's own order instead, where `NaN <= NaN`, would call NaN near
  everything.
  """
  def near(a, b, rel_tol \\ nil, abs_tol \\ nil)
  def near(a, b, _, _) when not (float64?(a) and float64?(b)), do: not_float64!(:near, [a, b])
  def near(a, b, _, _) when a == :nan or b == :nan, do: false
  def near(a, b, _, _) when a == b, do: true
  def near(a, b, _, _) when not (is_float(a) and is_float(b)), do: false

  def near(a, b, rel_tol, abs_tol) do
    margin =
      Kernel.max(Kernel.max(Kernel.abs(a), Kernel.abs(b)) * (rel_tol || 1.0e-9), abs_tol || 0.0)

    Kernel.abs(a - b) <= margin
  rescue
    # a - b overflows only when they are far apart
    ArithmeticError -> false
  end

  @doc "`Float64.round()`: halves toward +Infinity, as JavaScript's Math.round does."
  def round(f) when is_float(f) do
    down = Float.floor(f)
    r = if f - down >= 0.5, do: down + 1.0, else: down
    if r == 0.0 and neg?(f), do: -0.0, else: r
  end

  def round(f) when not float64?(f), do: not_float64!(:round, [f])
  def round(f), do: f

  @doc """
  A `Float64` method backed by `:math`. The BEAM raises where IEEE 754
  answers infinity or NaN, so a raise is mapped to that answer, and the
  special values are answered here without reaching `:math`.
  """
  def math(fun, x) when is_float(x) do
    apply(__MODULE__, :finite, [fun, x])
  rescue
    ArithmeticError -> domain(fun, x)
  end

  def math(fun, x) when not float64?(x), do: not_float64!(fun, [x])
  def math(_, :nan), do: :nan
  def math(fun, x), do: at_infinity(fun, x)

  @doc false
  def finite(:expm1, x),
    do: if(Kernel.abs(x) < 1.0e-5, do: x + x * x / 2 + x * x * x / 6, else: :math.exp(x) - 1.0)

  def finite(:log1p, x),
    do: if(Kernel.abs(x) < 1.0e-4, do: x - x * x / 2 + x * x * x / 3, else: log1p_big(x))

  def finite(fun, x), do: apply(:math, fun, [x])

  defp log1p_big(x), do: :math.log(1.0 + x)

  # finite arguments the BEAM raises on
  defp domain(fun, x) when fun in [:log, :log10, :log2],
    do: if(x == 0.0, do: :neg_infinity, else: :nan)

  defp domain(:log1p, x), do: if(x == -1.0, do: :neg_infinity, else: :nan)
  defp domain(fun, x) when fun in [:exp, :expm1, :sinh], do: inf(x < 0.0)
  defp domain(:cosh, _), do: :infinity
  defp domain(fun, _) when fun in [:sqrt, :acos, :asin], do: :nan

  defp at_infinity(fun, :infinity)
       when fun in [:sqrt, :exp, :expm1, :log, :log10, :log2, :log1p, :sinh, :cosh],
       do: :infinity

  defp at_infinity(fun, :neg_infinity) when fun in [:sqrt, :log, :log10, :log2, :log1p], do: :nan
  defp at_infinity(:exp, :neg_infinity), do: 0.0
  defp at_infinity(:expm1, :neg_infinity), do: -1.0
  defp at_infinity(:sinh, :neg_infinity), do: :neg_infinity
  defp at_infinity(:cosh, :neg_infinity), do: :infinity
  defp at_infinity(fun, _) when fun in [:sin, :cos, :tan, :asin, :acos], do: :nan
  defp at_infinity(:atan, x), do: if(x == :infinity, do: :math.pi() / 2, else: -:math.pi() / 2)
  defp at_infinity(:tanh, x), do: if(x == :infinity, do: 1.0, else: -1.0)
  defp at_infinity(fun, x) when fun in [:ceil, :floor], do: x

  @doc "`Float64.atan2()`: `y.atan2(x)`."
  def atan2(y, x) when is_float(y) and is_float(x), do: :math.atan2(y, x)
  def atan2(y, x) when not (float64?(y) and float64?(x)), do: not_float64!(:atan2, [y, x])
  def atan2(y, x) when y == :nan or x == :nan, do: :nan

  def atan2(y, x) when y in [:infinity, :neg_infinity] do
    q =
      case x do
        :infinity -> :math.pi() / 4
        :neg_infinity -> 3 * :math.pi() / 4
        _ -> :math.pi() / 2
      end

    if y == :neg_infinity, do: -q, else: q
  end

  def atan2(y, :infinity), do: zero(neg?(y))
  def atan2(y, :neg_infinity), do: if(neg?(y), do: -:math.pi(), else: :math.pi())

  @doc """
  `String.toFloat64()`: JSON's number syntax, plus `NaN`, `Infinity` and
  `-Infinity`, after trimming. A number too big for a float is infinite;
  `Float.parse` calls that an error.
  """
  def parse(s) do
    t = String.trim(s)

    cond do
      t == "NaN" ->
        :nan

      t == "Infinity" ->
        :infinity

      t == "-Infinity" ->
        :neg_infinity

      not Regex.match?(~r/\A-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?\z/, t) ->
        nil

      true ->
        case Float.parse(t) do
          {f, ""} -> if String.starts_with?(t, "-") and f == 0.0, do: -0.0, else: f
          :error -> inf(String.starts_with?(t, "-"))
        end
    end
  end
end
