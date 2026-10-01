defmodule TemperCoreFloatTest do
  use ExUnit.Case, async: true
  alias TemperCore.Float, as: F

  # the expected strings are lines of Temper's types/float functional tests,
  # and ECMAScript's Number::toString with Temper's ".0" for the rest
  test "floats print the way Temper prints them" do
    assert F.to_string(0.0) == "0.0"
    assert F.to_string(-0.0) == "-0.0"
    assert F.to_string(1.0) == "1.0"
    assert F.to_string(2.0) == "2.0"
    assert F.to_string(0.5) == "0.5"
    assert F.to_string(0.1) == "0.1"
    assert F.to_string(-1.5) == "-1.5"
    assert F.to_string(123.456) == "123.456"
    assert F.to_string(1.0e25) == "1.0e+25"
    assert F.to_string(9.9999999999899e25) == "9.9999999999899e+25"
    assert F.to_string(9.999999999919205e207) == "9.999999999919205e+207"
    assert F.to_string(1.0e21) == "1.0e+21"
    assert F.to_string(1.0e20) == "100000000000000000000.0"
    assert F.to_string(1.0e-6) == "0.000001"
    assert F.to_string(1.0e-7) == "1.0e-7"
    assert F.to_string(1.5e-7) == "1.5e-7"
  end

  test "negative zero is not zero, and comes first" do
    refute F.eq(-0.0, 0.0)
    assert F.ne(-0.0, 0.0)
    assert F.lt(-0.0, 0.0)
    refute F.lt(0.0, -0.0)
    assert F.le(-0.0, 0.0)
    assert F.cmp(-0.0, 0.0) == -1
    assert F.eq(1.0, 1.0)
    assert F.lt(1.0, 2.0)
  end
end
