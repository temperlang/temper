defmodule TemperCoreGeneratorTest do
  use ExUnit.Case, async: true
  alias TemperCore.{Generator, Heap}

  # a generator whose step k runs steps[k]
  def gen(steps) do
    k = Heap.new(:cell, %{v: 0})

    Generator.adapt(fn me ->
      i = Heap.get(k, :v)
      Heap.put(k, :v, i + 1)
      Enum.at(steps, i).(me)
    end)
  end

  test "next runs one step per call, and stays done once done" do
    g = gen([fn _ -> {:value, 1} end, fn _ -> :done end])
    assert Generator.next(g) == {:value, 1}
    refute Generator.done(g)
    assert Generator.next(g) == :done
    assert Generator.done(g)
    assert Generator.next(g) == :done
  end

  test "a next inside the step answers done rather than re-entering it" do
    g = gen([fn me -> {:value, Generator.next(me)} end])
    assert Generator.next(g) == {:value, :done}
  end

  test "close makes a generator done" do
    g = gen([fn _ -> {:value, 1} end])
    Generator.close(g)
    assert Generator.next(g) == :done
  end

  test "a ValueResult is read and recognised by its tag" do
    g = gen([fn _ -> {:value, 7} end, fn _ -> :done end])
    r = Generator.next(g)
    assert Generator.value_result?(r)
    assert Generator.value(r) == 7
    d = Generator.next(g)
    refute Generator.value_result?(d)
    assert d == :done
    # a tuple of another shape is not a ValueResult
    refute Generator.value_result?({:other, 7})
  end

  test "a step that returns anything but a GeneratorResult panics instead of looking not done" do
    g = gen([fn _ -> nil end])
    assert_raise TemperCore.Panic, ~r/not a GeneratorResult/, fn -> Generator.next(g) end
  end
end
