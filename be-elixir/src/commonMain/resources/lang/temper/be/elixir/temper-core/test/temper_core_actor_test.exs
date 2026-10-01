defmodule TemperCore.ActorTest do
  use ExUnit.Case, async: true
  alias TemperCore.{Actor, Heap}

  # what the backend generates for an @actor class, written by hand
  defmodule Counter do
    def __temper_supertypes__, do: [__MODULE__]

    def new(start) do
      Actor.start(__MODULE__, fn ->
        this = Actor.init_self(__MODULE__, %{n: nil})
        Heap.put(this, :n, start)
        this
      end)
    end

    def bump(this), do: Actor.run(this, fn -> Heap.put(this, :n, Heap.get(this, :n) + 1) end)
    def get_n(this), do: Actor.run(this, fn -> Heap.get(this, :n) end)
    def fail(this), do: Actor.run(this, fn -> raise TemperCore.Bubble, "no" end)
    def call_back(this, other), do: Actor.run(this, fn -> Counter.bump_through(other, this) end)
    def bump_through(this, back), do: Actor.run(this, fn -> Counter.bump(back) end)
    def take(this, value), do: Actor.run(this, fn -> value end)
    def crash(this), do: Actor.run(this, fn -> raise ArgumentError, "not a Temper error" end)
  end

  test "one object, many processes, no lost updates" do
    c = Counter.new(0)
    1..500 |> Enum.map(fn _ -> Task.async(fn -> Counter.bump(c) end) end) |> Task.await_many()
    assert Counter.get_n(c) == 500
    assert TemperCore.class_of(c) == Counter and TemperCore.is_a(c, Counter)
    assert TemperCore.call(c, :get_n, []) == 500
  end

  test "a bubble in the actor is raised in the caller" do
    assert_raise TemperCore.Bubble, fn -> Counter.fail(Counter.new(0)) end
  end

  test "calling back into a waiting actor is a panic, not a deadlock" do
    a = Counter.new(0)
    b = Counter.new(0)
    assert_raise TemperCore.Panic, ~r/call cycle/, fn -> Counter.call_back(a, b) end
  end

  test "values and actors cross; a mutable object does not" do
    c = Counter.new(0)
    assert Counter.take(c, %{list: TemperCore.Vec.new([1, 2]), other: c}).other == c
    assert_raise TemperCore.Panic, ~r/cannot be shared/, fn -> Counter.take(c, Heap.new(:box, %{v: 1})) end
    box = Heap.new(:box, %{v: 1})
    assert_raise TemperCore.Panic, ~r/cannot be shared/, fn -> Counter.take(c, fn -> box end) end
  end

  test "an actor ends with the process that made it" do
    parent = self()
    spawn(fn -> send(parent, {:made, Counter.new(0)}) end)
    c = receive do: ({:made, c} -> c)
    ref = Process.monitor(Actor.whereis(c))
    assert_receive {:DOWN, ^ref, :process, _, _}
    assert_raise TemperCore.Panic, ~r/has ended/, fn -> Counter.bump(c) end
  end

  test "a field cannot be read from outside the actor" do
    assert_raise TemperCore.Panic, ~r/outside its actor/, fn -> Heap.get(Counter.new(0), :n) end
  end

  test "a supervised actor outlives its creator" do
    parent = self()
    spawn(fn -> send(parent, {:made, Actor.supervised(fn -> Counter.new(5) end)}) end)
    c = receive do: ({:made, c} -> c)
    Process.sleep(20)
    assert Counter.get_n(c) == 5
    Actor.stop(c)
    assert_raise TemperCore.Panic, ~r/has ended/, fn -> Counter.get_n(c) end
  end

  test "a crash restarts a supervised actor: fresh state, same identity" do
    c = Actor.supervised(fn -> Counter.new(10) end)
    Counter.bump(c)
    assert Counter.get_n(c) == 11
    before = Actor.whereis(c)
    assert_raise ArgumentError, fn -> Counter.crash(c) end
    assert Counter.get_n(c) == 10
    assert Actor.whereis(c) != before
    Actor.stop(c)
  end

  test "a Temper error is the call's result, not a crash" do
    c = Actor.supervised(fn -> Counter.new(1) end)
    pid = Actor.whereis(c)
    assert_raise TemperCore.Bubble, fn -> Counter.fail(c) end
    assert Actor.whereis(c) == pid
    Actor.stop(c)
  end

  test "module values are shared by every process; a mutable object is copied per process" do
    TemperCore.Global.put(:"ActorTest.shared", 1)
    task = Task.async(fn -> TemperCore.Global.put(:"ActorTest.shared", TemperCore.Global.get(:"ActorTest.shared") + 41) end)
    Task.await(task)
    assert TemperCore.Global.get(:"ActorTest.shared") == 42

    box = Heap.new(:box, %{v: 1})
    TemperCore.Global.put(:"ActorTest.box", box)

    Task.async(fn ->
      theirs = TemperCore.Global.get(:"ActorTest.box")
      Heap.put(theirs, :v, 99)
    end)
    |> Task.await()

    assert Heap.get(TemperCore.Global.get(:"ActorTest.box"), :v) == 1
  end

  test "a library's top level runs once per node, however many processes ask" do
    me = self()
    1..20 |> Enum.map(fn _ -> Task.async(fn -> TemperCore.init_once(:"ActorTest.lib", fn -> send(me, :ran) end) end) end) |> Task.await_many()
    assert_received :ran
    refute_received :ran
  end
end
