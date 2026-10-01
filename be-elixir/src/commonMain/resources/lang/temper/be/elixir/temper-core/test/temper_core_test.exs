defmodule TemperCoreTest do
  use ExUnit.Case, async: true
  import TemperCore

  # every expectation below is a line of Temper's own
  # functional-test-suite types/int/limits test
  @big 2_147_483_647
  @small -2_147_483_648

  test "Int wraps at 32 bits" do
    assert int32(@big + 1) == -2_147_483_648
    assert int32(@small - 1) == 2_147_483_647
    assert int32(@big * 4) == -4
    assert int32(@small * 4) == 0
    assert int32((@small + 1) * 4) == 4
    assert int32((@small + 1) * @big) == -1
    assert int32(-@small) == -2_147_483_648
  end

  test "Int division truncates toward zero, wraps, and % takes the dividend's sign" do
    assert int32_div(@small, -1) == -2_147_483_648
    assert int32_rem(@small, -1) == 0
    assert {int32_div(5, 3), int32_div(5, -3), int32_div(-5, 3), int32_div(-5, -3)} == {1, -1, -1, 1}
    assert {int32_rem(5, 3), int32_rem(5, -3), int32_rem(-5, 3), int32_rem(-5, -3)} == {2, 2, -2, -2}
  end

  test "dividing by zero bubbles" do
    assert_raise TemperCore.Bubble, fn -> int32_div(1, 0) end
    assert_raise TemperCore.Bubble, fn -> int32_rem(1, 0) end
  end

  test "Int64 wraps at 64 bits" do
    assert int64(9_223_372_036_854_775_807 + 1) == -9_223_372_036_854_775_808
  end

  test "an object on the heap is shared by every alias" do
    a = TemperCore.Heap.new(Counter, %{n: 0})
    b = a
    TemperCore.Heap.put(b, :n, TemperCore.Heap.get(b, :n) + 1)
    assert TemperCore.Heap.get(a, :n) == 1
  end

  test "two objects are equal only when they are the same object" do
    a = TemperCore.Heap.new(Counter, %{n: 0})
    b = TemperCore.Heap.new(Counter, %{n: 0})
    assert a == a
    refute a == b
    assert a.class == Counter
  end

  test "a field the object does not have is an error, not nil" do
    a = TemperCore.Heap.new(Counter, %{n: 0})
    assert_raise KeyError, fn -> TemperCore.Heap.get(a, :m) end
    assert_raise KeyError, fn -> TemperCore.Heap.put(a, :m, 1) end
  end

  test "another process cannot see this process's objects" do
    a = TemperCore.Heap.new(Counter, %{n: 0})
    task = Task.async(fn ->
      try do
        TemperCore.Heap.get(a, :n)
      rescue
        e in ArgumentError -> {:raised, e.message =~ "not an object in this process"}
      end
    end)
    assert Task.await(task) == {:raised, true}
  end
end
