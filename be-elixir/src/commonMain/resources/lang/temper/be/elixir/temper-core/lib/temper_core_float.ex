defmodule TemperCore.Float do
  @moduledoc """
  Temper's Float64, where it differs from the BEAM's float.

  - Temper prints a float the way JavaScript does, plain until the decimal
    exponent reaches 21 or falls below -6, but always with a point:
    `1.0`, not `1`, and `1.0e+25`, not `1e+25`.
  - Temper calls `-0.0` and `0.0` unequal and orders `-0.0` first. Since
    OTP 27, `===` already tells them apart; `<` does not.
  """

  @doc "The shortest round-tripping digits, laid out as Temper prints them."
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
    mantissa <> "e" <> sign <> Integer.to_string(abs(e))
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

  def eq(a, b), do: a === b
  def ne(a, b), do: a !== b
  def lt(a, b), do: a < b or (a == 0.0 and b == 0.0 and negative_zero?(a) and not negative_zero?(b))
  def gt(a, b), do: lt(b, a)
  def le(a, b), do: not lt(b, a)
  def ge(a, b), do: not lt(a, b)

  @doc "`Float64.min`: -0.0 is the lesser zero."
  def min(a, b), do: if(lt(b, a), do: b, else: a)
  def max(a, b), do: if(lt(a, b), do: b, else: a)

  @doc "`Float64.sign()`: -1.0, 0.0 (keeping a negative zero) or 1.0."
  def sign(f) when f > 0.0, do: 1.0
  def sign(f) when f < 0.0, do: -1.0
  def sign(f), do: f

  def cmp(a, b) do
    cond do
      lt(a, b) -> -1
      lt(b, a) -> 1
      true -> 0
    end
  end
end
