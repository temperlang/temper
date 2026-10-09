defmodule TemperCoreMapTest do
  use ExUnit.Case, async: true
  alias TemperCore.Map, as: M
  alias TemperCore.Pair

  test "maps keep insertion order, past the 32 keys where Elixir's stop pretending" do
    m = M.new(for i <- 40..1//-1, do: Pair.new(i, i * i))
    assert Enum.to_list(M.keys(m)) == Enum.to_list(40..1//-1)
    small = M.new([Pair.new("b", 1), Pair.new("a", 2)])
    assert Enum.to_list(M.keys(small)) == ["b", "a"]
  end

  test "a repeated key keeps its first place and its last value" do
    m = M.new([Pair.new(:x, 1), Pair.new(:y, 2), Pair.new(:x, 3)])
    assert Enum.to_list(M.to_list_with(m, fn k, v -> {k, v} end)) == [x: 3, y: 2]
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
    assert Enum.to_list(M.keys(b)) == ["a", "b"]
    assert M.remove(b, "a") == 3
    assert Enum.to_list(M.keys(b)) == ["b"]
    assert_raise TemperCore.Bubble, fn -> M.remove(b, "a") end
    frozen = M.to_map(b)
    M.set(b, "c", 4)
    assert M.length(frozen) == 1
    assert M.length(b) == 2
  end

  test "a key removed and added again goes last, and stale keys never show" do
    b = M.builder()
    for k <- ~w(a b c d), do: M.set(b, k, k)
    M.remove(b, "b")
    M.set(b, "b", "again")
    M.remove(b, "c")
    M.set(b, "a", "kept its place")
    assert Enum.to_list(M.keys(b)) == ["a", "d", "b"]
    assert Enum.to_list(M.values(b)) == ["kept its place", "d", "again"]
    assert M.length(b) == 3
    # enough removes to rebuild the key list, then the same answers
    for k <- ~w(a d b), do: M.remove(b, k)
    assert Enum.to_list(M.keys(b)) == []
    M.set(b, "z", 1)
    M.set(b, "a", 2)
    assert Enum.to_list(M.keys(b)) == ["z", "a"]
    assert Enum.to_list(M.keys(M.to_builder(M.to_map(b)))) == ["z", "a"]
    M.clear(b)
    M.set(b, "q", 0)
    assert Enum.to_list(M.keys(b)) == ["q"]
  end

  # `set` appended each new key to a list, copying every key before it, so
  # filling a builder was quadratic: 100,000 sets took 21 s.
  test "filling and emptying a builder is linear" do
    b = M.builder()

    {micros, _} =
      :timer.tc(fn ->
        for i <- 1..200_000, do: M.set(b, i, i)
        for i <- 1..200_000//2, do: M.remove(b, i)
      end)

    assert M.length(b) == 100_000
    assert Enum.take(Enum.to_list(M.keys(b)), 3) == [2, 4, 6]
    assert micros < 5_000_000, "200,000 sets and 100,000 removes took #{div(micros, 1000)} ms"
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
