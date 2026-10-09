defmodule TemperCore.HeapTest do
  use ExUnit.Case, async: true
  require TemperCore.Heap
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

  test "entry frees what a call left behind and keeps what it returned" do
    before = Heap.size()
    kept =
      Heap.entry(fn ->
        for _ <- 1..50, do: Heap.new(:scratch, %{v: 0})
        Heap.new(:result, %{v: 1})
      end)

    assert Heap.get(kept, :v) == 1
    assert Heap.size() == before + 1
  end

  test "entry never frees what existed before the call, and keeps young objects an old one now points at" do
    old = Heap.new(:holder, %{child: nil})
    held = Heap.new(:held, %{v: 7})

    Heap.entry(fn ->
      Heap.put(old, :child, Heap.new(:child, %{v: 2}))
      Heap.new(:garbage, %{v: 0})
      nil
    end)

    assert Heap.get(held, :v) == 7
    assert Heap.get(Heap.get(old, :child), :v) == 2
  end

  test "young objects in globals and closures survive; nested entries collect only at the outermost" do
    {result, inner_size} =
      Heap.entry(fn ->
        TemperCore.Global.put(:"Test.entry", Heap.new(:in_global, %{v: 3}))
        Heap.entry(fn -> Heap.new(:inner_garbage, %{}) end)
        size_after_inner = Heap.size()
        cell = Heap.new(:cell, %{v: 4})
        {fn -> Heap.get(cell, :v) end, size_after_inner}
      end)

    assert result.() == 4
    assert Heap.get(TemperCore.Global.get(:"Test.entry"), :v) == 3
    # the inner entry did not collect: its garbage was still there
    assert inner_size >= 2
  end

  # Each outermost entry listed the whole process dictionary to find roots,
  # so a loop of calls into a library cost as much per call as the heap it
  # had kept: 80,000 calls of an exported constructor took 41 s.
  test "an entry costs what it made, not the heap that was already there" do
    kept = for i <- 1..100_000, do: Heap.new(:old, %{v: i})

    {micros, made} =
      :timer.tc(fn ->
        for i <- 1..20_000 do
          Heap.entry(fn ->
            Heap.new(:garbage, %{})
            Heap.new(:made, %{v: i})
          end)
        end
      end)

    assert length(kept) == 100_000
    assert Heap.get(List.last(made), :v) == 20_000
    assert micros < 2_000_000, "20,000 entries over a 100,000-object heap took #{div(micros, 1000)} ms"
  end

  test "a young object reached only from the run queue or a remote promise's waiters survives" do
    gen = Heap.entry(fn ->
      g = TemperCore.Generator.adapt(fn _ -> :done end)
      TemperCore.Async.enqueue(g)
      nil
    end)

    assert gen == nil
    {{:value, queued}, _} = :queue.out(Process.get({TemperCore.Async, :queue}))
    assert Heap.local?(queued)
    Process.delete({TemperCore.Async, :queue})
  end

  test "a raise still collects, and the bookkeeping is gone afterwards" do
    before = Heap.size()

    assert_raise TemperCore.Bubble, fn ->
      Heap.entry(fn ->
        Heap.new(:garbage, %{})
        raise TemperCore.Bubble, "no"
      end)
    end

    assert Heap.size() == before
    refute Enum.any?(Process.get(), fn {k, _} -> match?({TemperCore.Heap.Nursery, _}, k) end)
  end

  test "a value object is collected, exported and freed like any object" do
    kept = TemperCore.StringBuilder.new()
    TemperCore.StringBuilder.append(kept, "kept")
    for _ <- 1..10, do: TemperCore.StringBuilder.new()
    assert Heap.collect([kept]) >= 10
    assert TemperCore.StringBuilder.to_string(kept) == "kept"

    sent = Heap.export(%{sb: kept})
    me = self()
    spawn(fn ->
      %{sb: sb} = Heap.import(sent)
      TemperCore.StringBuilder.append_code_point(sb, 0x20AC)
      send(me, TemperCore.StringBuilder.to_string(sb))
    end)
    assert_receive "kept\u20ac"
    assert TemperCore.StringBuilder.to_string(kept) == "kept"

    before = Heap.size()
    young = Heap.entry(fn -> TemperCore.StringBuilder.new() && :done end)
    assert young == :done
    assert Heap.size() == before
  end

  test "a value object that is not in this process raises, and leaves nothing behind" do
    sb = TemperCore.StringBuilder.new()
    Heap.collect([])
    before = Heap.size()
    assert_raise ArgumentError, ~r/is not an object in this process/, fn -> TemperCore.StringBuilder.append(sb, "x") end
    assert_raise ArgumentError, ~r/is not an object in this process/, fn -> TemperCore.StringBuilder.clear(sb) end
    assert Heap.size() == before
  end
end
