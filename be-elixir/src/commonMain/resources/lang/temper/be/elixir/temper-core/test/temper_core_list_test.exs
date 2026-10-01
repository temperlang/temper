defmodule TemperCoreListTest do
  use ExUnit.Case, async: true
  alias TemperCore.List, as: L

  test "a builder is shared by its aliases, and is also a Listed" do
    a = L.builder()
    b = a
    L.add(b, 1)
    L.add(b, 2)
    assert L.to_list(a) == [1, 2]
    assert L.length(a) == 2
    assert L.get(a, 1) == 2
    assert L.map(a, &(&1 * 10)) == [10, 20]
  end

  test "add at a position, and panics outside 0..length" do
    lb = L.builder([1, 3])
    L.add(lb, 2, 1)
    assert L.items(lb) == [1, 2, 3]
    assert_raise TemperCore.Panic, fn -> L.add(lb, 9, 4) end
    assert_raise TemperCore.Panic, fn -> L.add(lb, 9, -1) end
  end

  test "removeLast returns the last element, and panics when empty" do
    lb = L.builder([1, 2])
    assert L.remove_last(lb) == 2
    assert L.remove_last(lb) == 1
    assert_raise TemperCore.Panic, fn -> L.remove_last(lb) end
  end

  test "get bubbles out of range; set panics" do
    assert_raise TemperCore.Bubble, fn -> L.get([1], 1) end
    assert_raise TemperCore.Panic, fn -> L.set(L.builder([1]), 1, 0) end
  end

  test "splice clamps, replaces, and returns what it removed" do
    lb = L.builder([0, 1, 2, 3, 4])
    assert L.splice(lb, 1, 2, [9]) == [1, 2]
    assert L.items(lb) == [0, 9, 3, 4]
    assert L.splice(lb, 10, 1) == []
    assert L.splice(lb) == [0, 9, 3, 4]
    assert L.items(lb) == []
  end

  test "sorted is stable and three-way" do
    pairs = [{1, :a}, {0, :b}, {1, :c}, {0, :d}]
    assert L.sorted(pairs, fn {x, _}, {y, _} -> TemperCore.cmp(x, y) end) == [{0, :b}, {0, :d}, {1, :a}, {1, :c}]
  end

  test "reduce bubbles on empty; reduceFrom does not" do
    assert L.reduce([1, 2, 3], &(&1 + &2)) == 6
    assert_raise TemperCore.Bubble, fn -> L.reduce([], &(&1 + &2)) end
    assert L.reduce_from([], 5, &(&1 + &2)) == 5
    assert L.reduce_from(["a", "b"], "", &(&1 <> &2)) == "ab"
  end

  test "slice clamps both ends" do
    assert L.slice([0, 1, 2, 3], 1, 3) == [1, 2]
    assert L.slice([0, 1, 2, 3], -5, 99) == [0, 1, 2, 3]
    assert L.slice([0, 1, 2, 3], 3, 1) == []
  end

  test "join stringifies with the given function" do
    assert L.join([1, 2, 3], ", ", &Integer.to_string/1) == "1, 2, 3"
  end
end
