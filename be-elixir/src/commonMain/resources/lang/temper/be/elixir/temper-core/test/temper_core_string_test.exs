defmodule TemperCoreStringTest do
  use ExUnit.Case, async: true
  alias TemperCore.String, as: S

  # the examples are from the doc comments on String in Temper's core.temper
  test "next, prev and get walk code points" do
    s = "abc"
    assert S.get(s, S.next(s, S.begin())) == ?b
    assert S.get(s, S.prev(s, S.end_of(s))) == ?c
    assert S.get("foo", S.begin()) == ?f
    refute S.has_index("", S.begin())
    assert S.has_index("foo", S.begin())
  end

  test "multi-byte code points are one step each" do
    s = "aé😀b"
    i = S.next(s, 0)
    assert S.get(s, i) == ?é
    j = S.next(s, i)
    assert S.get(s, j) == 0x1F600
    assert S.next(s, j) == byte_size(s) - 1
    assert S.prev(s, byte_size(s) - 1) == j
    assert S.count_between(s, 0, S.end_of(s)) == 4
  end

  test "countBetween, hasAtLeast and slice" do
    s = "abcdefghijklmnopqrstuvwxyz"
    i = S.next(s, S.begin())
    j = S.prev(s, S.end_of(s))
    assert S.count_between(s, i, j) == 24
    assert S.has_at_least(s, i, j, 24)
    refute S.has_at_least(s, i, j, 25)
    t = "tsubo"
    assert S.slice(t, S.next(t, 0), S.prev(t, S.end_of(t))) == "sub"
    assert S.slice(t, 3, 1) == ""
  end

  test "split by \"\" gives code points" do
    assert Enum.to_list(S.split("a😀b", "")) == ["a", "😀", "b"]
    assert Enum.to_list(S.split("a,,b", ",")) == ["a", "", "b"]
  end

  test "indexOf answers a byte offset or none" do
    assert S.index_of("hello", "l") == 2
    assert S.index_of("hello", "l", 3) == 3
    assert S.index_of("hello", "z") == -1
  end

  test "integer parsing: JSON syntax or a radix, and it must fit" do
    assert S.to_int32("-42") == -42
    assert S.to_int32(" 2 ") == 2
    assert S.to_int32("7fffffff", 16) == 2_147_483_647
    assert_raise TemperCore.Bubble, fn -> S.to_int32("2147483648") end
    assert_raise TemperCore.Bubble, fn -> S.to_int32("12x") end
    assert_raise TemperCore.Bubble, fn -> S.to_int32("+1") end
  end

  test "float parsing trims and keeps a negative zero" do
    assert S.to_float64(" 2 ") == 2.0
    assert S.to_float64("5e-1") == 0.5
    assert S.to_float64("-0") === -0.0
    assert_raise TemperCore.Bubble, fn -> S.to_float64("nope") end
  end

  test "a string builder is shared by its aliases" do
    sb = TemperCore.StringBuilder.new()
    alias_ = sb
    TemperCore.StringBuilder.append(alias_, "ab")
    TemperCore.StringBuilder.append_code_point(sb, ?c)
    assert TemperCore.StringBuilder.to_string(sb) == "abc"
  end
end
