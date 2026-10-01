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

  test "Int toString writes lower-case digits in any radix" do
    assert int_to_string(-255) == "-255"
    assert int_to_string(255, 16) == "ff"
    assert int_to_string(5, 2) == "101"
  end

  test "conversions bubble instead of losing the value" do
    assert int64_to_float(2) == 2.0
    assert_raise TemperCore.Bubble, fn -> int64_to_float(9_007_199_254_740_993) end
    assert float_to_int32(-2.7) == -2
    assert_raise TemperCore.Bubble, fn -> float_to_int32(3.0e9) end
    assert_raise TemperCore.Bubble, fn -> int64_to_int32(2_147_483_648) end
  end

  test "list_get bubbles outside the list" do
    assert list_get([1, 2], 1) == 2
    assert_raise TemperCore.Bubble, fn -> list_get([1, 2], 2) end
    assert_raise TemperCore.Bubble, fn -> list_get([1, 2], -1) end
    assert list_get_or([1, 2], 5, :none) == :none
  end

  defmodule Shape do
    def __temper_supertypes__, do: [__MODULE__]
  end

  defmodule Square do
    defstruct [:side]
    def __temper_supertypes__, do: [__MODULE__, TemperCoreTest.Shape]
    def area(this), do: this.side * this.side
  end

  defmodule Counter do
    def __temper_supertypes__, do: [__MODULE__]
    def count(this), do: TemperCore.Heap.get(this, :n)
  end

  test "a method call finds the object's own class, struct or heap" do
    assert TemperCore.call(%Square{side: 3}, :area, []) == 9
    c = TemperCore.Heap.new(Counter, %{n: 4})
    assert TemperCore.call(c, :count, []) == 4
    assert_raise ArgumentError, fn -> TemperCore.call("text", :count, []) end
  end

  test "instanceof and casts follow the supertype list" do
    assert is_a(%Square{side: 1}, Shape)
    refute is_a(%Square{side: 1}, Counter)
    refute is_a("text", Shape)
    assert cast(%Square{side: 1}, Shape) == %Square{side: 1}
    assert_raise TemperCore.Bubble, fn -> cast(%Square{side: 1}, Counter) end
  end

  test "cmp is three-way" do
    assert {cmp(1, 2), cmp(2, 2), cmp(3, 2)} == {-1, 0, 1}
    assert cmp("a", "b") == -1
  end

  test "a module-level value is set before it is read, and an unset one raises" do
    TemperCore.Global.put(:answer, 42)
    assert TemperCore.Global.get(:answer) == 42
    TemperCore.Global.put(:empty, nil)
    assert TemperCore.Global.get(:empty) == nil
    assert_raise ArgumentError, fn -> TemperCore.Global.get(:never_set) end
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

defmodule TemperCore.InitOnceTest do
  use ExUnit.Case, async: true

  test "a library's top levels run once per process, however many libraries depend on it" do
    me = self()
    run = fn -> TemperCore.init_once(:"Temper.Shared", fn -> send(me, :ran) end) end
    run.()
    run.()
    assert_received :ran
    refute_received :ran
  end
end

defmodule TemperCore.TemporalTest do
  use ExUnit.Case, async: true

  defmodule FakeDate do
    def new(y, m, d), do: {y, m, d}
  end

  test "today is built by the given Date module from the UTC date" do
    %Date{year: y, month: m, day: d} = Date.utc_today()
    assert TemperCore.Temporal.today(FakeDate) == {y, m, d}
  end
end
