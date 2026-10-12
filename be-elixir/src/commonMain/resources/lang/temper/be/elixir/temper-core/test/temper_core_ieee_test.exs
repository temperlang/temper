defmodule TemperCore.IeeeTest do
  use ExUnit.Case, async: true
  alias TemperCore.Float, as: F

  @big 1.7976931348623157e308

  test "overflow gives infinities, not ArithmeticError" do
    assert F.mul(@big, 2.0) == :infinity
    assert F.mul(@big, -2.0) == :neg_infinity
    assert F.add(@big, @big) == :infinity
    assert F.divide(@big, 0.5) == :infinity
    assert F.divide(-@big, 1.0e-10) == :neg_infinity
    assert F.divide(1.0, :infinity) === 0.0
    assert F.divide(-1.0, :infinity) === -0.0
    assert F.divide(:infinity, -2.0) == :neg_infinity
  end

  # builtins.md: "Float64 division by zero is a Bubble too", as in the
  # interpreter, py and rust. Not IEEE's Infinity or NaN.
  test "/ and % by 0.0 or -0.0 bubble, whatever the dividend" do
    for a <- [1.0, -1.0, 0.0, -0.0, :infinity, :neg_infinity, :nan], b <- [0.0, -0.0] do
      assert_raise TemperCore.Bubble, fn -> F.divide(a, b) end
      assert_raise TemperCore.Bubble, fn -> F.rem(a, b) end
    end
  end

  test "NaN and infinities propagate" do
    assert F.add(:infinity, :neg_infinity) == :nan
    assert F.mul(:infinity, 0.0) == :nan
    assert F.sub(:infinity, 1.0) == :infinity
    assert F.neg(:infinity) == :neg_infinity
    assert F.rem(:infinity, 2.0) == :nan
    assert F.rem(1.5, :infinity) == 1.5
    assert F.rem(-7.5, 2.0) == -1.5
    assert F.pow(0.0, -1.0) == :infinity
    assert F.pow(0.0, :neg_infinity) == :infinity
    assert F.pow(0.5, :neg_infinity) == :infinity
    assert F.pow(-2.0, :neg_infinity) === 0.0
    assert F.pow(-1.0, :neg_infinity) == 1.0
    assert F.pow(-8.0, 1 / 3) == :nan
    assert F.pow(:nan, 0.0) == 1.0
  end

  test "Temper's total order: -0.0 before 0.0, NaN above Infinity, NaN == NaN" do
    assert F.lt(:neg_infinity, -1.0)
    assert F.lt(-0.0, 0.0)
    assert F.lt(1.0, :infinity)
    assert F.lt(:infinity, :nan)
    assert F.eq(:nan, :nan)
    assert F.cmp(:nan, :infinity) == 1
  end

  test "min and max are NaN if either side is" do
    assert F.max(:nan, 2.0) == :nan
    assert F.min(1.0, :nan) == :nan
    assert F.max(1.0, 2.0) == 2.0
  end

  test "math answers IEEE where :math raises" do
    assert F.math(:sqrt, -1.0) == :nan
    assert F.math(:log, 0.0) == :neg_infinity
    assert F.math(:exp, 1000.0) == :infinity
    assert F.math(:exp, :neg_infinity) == 0.0
    assert F.math(:atan, :infinity) == :math.pi() / 2
    assert F.math(:tanh, :neg_infinity) == -1.0
    assert F.near(F.math(:expm1, 1.0e-10), 1.0e-10)
    assert F.near(F.math(:log1p, 1.0e-10), 1.0e-10)
  end

  test "near is math.isclose" do
    refute F.near(1.0, 1.1)
    assert F.near(1.0, 1.25, nil, 0.25)
    assert F.near(10.0, 10.1, 0.011)
    # what the JS and Python backends print for these
    refute F.near(:nan, :nan)
    refute F.near(:nan, 1.0)
    assert F.near(:infinity, :infinity)
    refute F.near(:infinity, 1.0e308)
    assert F.near(0.0, -0.0)
  end

  test "round takes halves up, keeping a negative zero" do
    assert F.round(2.5) == 3.0
    assert F.round(-2.5) == -2.0
    assert F.round(-0.4) === -0.0
  end

  test "printing and parsing the special values" do
    assert F.to_string(:neg_infinity) == "-Infinity"
    assert F.parse("NaN") == :nan
    assert F.parse(" -Infinity ") == :neg_infinity
    assert F.parse("1e999") == :infinity
    assert F.parse("-inf") == nil
    assert F.parse("2.") == nil
    assert F.parse("-0") === -0.0
  end

  test "conversions bubble on infinity and NaN, and toInt64 stops at 2^53 - 1" do
    assert_raise TemperCore.Bubble, fn -> TemperCore.float_to_int32(:infinity) end
    assert_raise TemperCore.Bubble, fn -> TemperCore.float_to_int64(:nan) end
    assert_raise TemperCore.Bubble, fn -> TemperCore.float_to_int64(9.007199254740992e15) end
    assert TemperCore.float_to_int64(9.007199254740991e15) == 9_007_199_254_740_991
    assert_raise TemperCore.Bubble, fn -> TemperCore.int64_to_float(0x20_0000_0000_0000) end
    assert TemperCore.float_trunc(:nan) == 0
  end
end
