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

    def slow_bump(this, watcher) do
      Actor.run(this, fn ->
        Heap.put(this, :n, Heap.get(this, :n) + 1)
        send(watcher, {:started, self()})
        Process.sleep(500)
      end)
    end
  end

  # A library whose top level makes an actor whose constructor calls one of
  # the library's own exported functions, as generated code does.
  defmodule SelfInit do
    def init, do: TemperCore.init_once(:"ActorTest.SelfInit", fn -> TemperCore.Global.put(:"ActorTest.SelfInit.m", TemperCore.ActorTest.Maker.new()) end)
    def answer, do: (init(); 42)
  end

  defmodule Maker do
    def new do
      Actor.start(__MODULE__, fn ->
        this = Actor.init_self(__MODULE__, %{got: nil})
        Heap.put(this, :got, TemperCore.ActorTest.SelfInit.answer())
        this
      end)
    end

    def got(this), do: Actor.run(this, fn -> Heap.get(this, :got) end)
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

  test "a supervised actor killed during a call does not run the call again" do
    # Retrying turned at-most-once into at-least-once: the body ran a second
    # time on the restarted actor's fresh state, and the caller saw success.
    c = Actor.supervised(fn -> Counter.new(100) end)
    test = self()
    caller = Task.async(fn -> try do: Counter.slow_bump(c, test), rescue: (e -> e) end)
    assert_receive {:started, pid}, 1000
    Process.exit(pid, :kill)
    assert %TemperCore.Panic{message: message} = Task.await(caller)
    assert message =~ "ended during the call"
    refute_receive {:started, _}, 700
    assert Counter.get_n(c) == 100
    Actor.stop(c)
  end

  test "a call that finds the actor already gone is tried once more on its restart" do
    c = Actor.supervised(fn -> Counter.new(7) end)
    pid = Actor.whereis(c)
    ref = Process.monitor(pid)
    Process.exit(pid, :kill)
    assert_receive {:DOWN, ^ref, _, _, _}
    assert Counter.get_n(c) == 7
    Actor.stop(c)
  end

  test "an actor that keeps crashing ends alone" do
    # User and library actors shared one supervisor, and its restart limit
    # counted them together: four quick crashes of one actor ended every
    # actor under it, module-level ones included, and a few more rounds took
    # down :temper_core and its ETS table.
    TemperCore.init_once(:"ActorTest.flaky", fn ->
      TemperCore.Global.put(:"ActorTest.ledger", Counter.new(0))
    end)

    ledger = TemperCore.Global.get(:"ActorTest.ledger")
    bystander = Actor.supervised(fn -> Counter.new(5) end)
    flaky = Actor.supervised(fn -> Counter.new(1) end)

    for _ <- 1..12 do
      try do
        Counter.crash(flaky)
      rescue
        _ -> nil
      end
    end

    assert_raise TemperCore.Panic, ~r/has ended/, fn -> Counter.get_n(flaky) end
    Counter.bump(ledger)
    assert Counter.get_n(ledger) == 1
    assert Counter.get_n(bystander) == 5
    assert :ets.whereis(:temper_globals) != :undefined
    assert Process.whereis(TemperCore.Supervisor)
    Actor.stop(bystander)
  end

  test "an actor made by a library's top level may call the library" do
    # init_once held its lock while the top level ran; the actor's call into
    # the library waited for that lock, and the lock holder waited for the
    # actor to start. Nothing timed out.
    first = Task.async(fn -> SelfInit.answer() end)
    assert Task.yield(first, 3000) == {:ok, 42}
    assert Maker.got(TemperCore.Global.get(:"ActorTest.SelfInit.m")) == 42
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
