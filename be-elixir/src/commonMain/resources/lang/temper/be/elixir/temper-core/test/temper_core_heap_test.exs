defmodule TemperCore.HeapTest do
  use ExUnit.Case, async: true
  alias TemperCore.Heap

  test "collect frees what nothing reaches and keeps what roots, globals and closures reach" do
    kept_by_root = Heap.new(:thing, %{v: 1})
    child = Heap.new(:thing, %{v: 2})
    parent = Heap.new(:thing, %{child: child})
    in_global = Heap.new(:thing, %{v: 3})
    TemperCore.Global.put(:"Test.g", [in_global])
    in_closure = Heap.new(:thing, %{v: 4})
    f = fn -> Heap.get(in_closure, :v) end
    for _ <- 1..100, do: Heap.new(:garbage, %{v: 0})

    freed = Heap.collect([kept_by_root, parent, %{f: f}])
    assert freed == 100
    assert Heap.get(kept_by_root, :v) == 1
    assert Heap.get(Heap.get(parent, :child), :v) == 2
    assert Heap.get(in_global, :v) == 3
    assert f.() == 4
  end

  test "a cycle nothing reaches is freed" do
    a = Heap.new(:node, %{next: nil})
    b = Heap.new(:node, %{next: a})
    Heap.put(a, :next, b)
    before = Heap.size()
    assert Heap.collect([]) >= 2
    assert Heap.size() <= before - 2
  end

  test "an export crosses to another process, closures and aliasing included" do
    shared = Heap.new(:counter, %{n: 1})
    value = %{a: shared, b: shared, read: fn -> Heap.get(shared, :n) end}
    parent = self()

    spawn(fn ->
      got = Heap.import(receive do x -> x end)
      Heap.put(got.a, :n, 10)
      # both fields are one object, and the closure sees it too
      send(parent, {Heap.get(got.b, :n), got.read.()})
    end)
    |> send(Heap.export(value))

    assert_receive {10, 10}
    # the sender's object is unchanged: an export is a copy
    assert Heap.get(shared, :n) == 1
  end
end
