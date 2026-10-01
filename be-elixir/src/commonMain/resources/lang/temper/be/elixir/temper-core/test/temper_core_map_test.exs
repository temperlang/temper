defmodule TemperCoreMapTest do
  use ExUnit.Case, async: true
  alias TemperCore.Map, as: M
  alias TemperCore.Pair

  test "maps keep insertion order, past the 32 keys where Elixir's stop pretending" do
    m = M.new(for i <- 40..1//-1, do: Pair.new(i, i * i))
    assert M.keys(m) == Enum.to_list(40..1//-1)
    small = M.new([Pair.new("b", 1), Pair.new("a", 2)])
    assert M.keys(small) == ["b", "a"]
  end

  test "a repeated key keeps its first place and its last value" do
    m = M.new([Pair.new(:x, 1), Pair.new(:y, 2), Pair.new(:x, 3)])
    assert M.to_list_with(m, fn k, v -> {k, v} end) == [x: 3, y: 2]
  end

  test "get bubbles, getOr and has do not" do
    m = M.new([Pair.new(1, "one")])
    assert M.get(m, 1) == "one"
    assert_raise TemperCore.Bubble, fn -> M.get(m, 2) end
    assert M.get_or(m, 2, "none") == "none"
    refute M.has(m, 2)
  end

  test "a builder is shared, and remove returns the value or bubbles" do
    b = M.builder()
    alias_ = b
    M.set(alias_, "a", 1)
    M.set(b, "b", 2)
    M.set(b, "a", 3)
    assert M.keys(b) == ["a", "b"]
    assert M.remove(b, "a") == 3
    assert M.keys(b) == ["b"]
    assert_raise TemperCore.Bubble, fn -> M.remove(b, "a") end
    frozen = M.to_map(b)
    M.set(b, "c", 4)
    assert M.length(frozen) == 1
    assert M.length(b) == 2
  end

  test "deques are first in, first out, and panic when empty" do
    d = TemperCore.Deque.new()
    TemperCore.Deque.add(d, 1)
    TemperCore.Deque.add(d, 2)
    assert TemperCore.Deque.remove_first(d) == 1
    assert TemperCore.Deque.remove_first(d) == 2
    assert TemperCore.Deque.is_empty(d)
    assert_raise TemperCore.Panic, fn -> TemperCore.Deque.remove_first(d) end
  end

  test "a bit vector's example from core.temper" do
    v = TemperCore.DenseBitVector.new(16)
    refute TemperCore.DenseBitVector.get(v, 3)
    TemperCore.DenseBitVector.set(v, 3, true)
    assert TemperCore.DenseBitVector.get(v, 3)
    TemperCore.DenseBitVector.set(v, 3, false)
    refute TemperCore.DenseBitVector.get(v, 3)
    refute TemperCore.DenseBitVector.get(v, 1000)
  end
end
