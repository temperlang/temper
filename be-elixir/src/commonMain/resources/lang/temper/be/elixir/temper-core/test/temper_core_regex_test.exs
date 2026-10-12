defmodule TemperCore.RegexTest do
  use ExUnit.Case, async: true
  alias TemperCore.Regex, as: R

  defmodule G do
    def new(name, value, b, e), do: {name, value, b, e}
  end

  defmodule M do
    def new(full, groups), do: {full, groups}
  end

  test "captures come back in pattern order, not :re's alphabetical one" do
    {_, names} = R.compile("(?<zeta>a)(?<alpha>b)")
    assert names == ["zeta", "alpha"]
  end

  test "find reports byte offsets, which are string indexes here" do
    c = R.compile("a+(?<intro>b+)c")
    {full, groups} = R.find(c, "🌍aaabbc!", 0, M, G)
    assert full == {"full", "aaabbc", 4, 10}
    assert TemperCore.Map.get(groups, "intro") == {"intro", "bb", 7, 9}
  end

  test "find from an offset, and a bubble when nothing matches" do
    c = R.compile("[a-z]+")
    assert {{"full", "xyz", 9, 12}, _} = R.find(c, "---abc---xyz---", 6, M, G)
    assert_raise TemperCore.Bubble, fn -> R.find(c, "---", 0, M, G) end
  end

  test "\\d and \\w stay ASCII while . takes a whole code point" do
    refute R.found(R.compile("\\d"), "٣")
    assert R.find(R.compile("^."), "🌍x", 0, M, G) |> elem(0) == {"full", "🌍", 0, 4}
  end

  test "replace steps past empty matches" do
    c = R.compile("(^|,)\\s*")
    assert R.replace(c, "a, b", fn _ -> "|" end, M, G) == "|a|b"
  end

  test "a code point escape PCRE reads back" do
    assert R.found(R.compile(R.code_escape(0x7F)), <<0x7F>>)
  end
end
