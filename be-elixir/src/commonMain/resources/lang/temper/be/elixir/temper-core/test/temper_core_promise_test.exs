defmodule TemperCorePromiseTest do
  use ExUnit.Case, async: true
  alias TemperCore.{Actor, Async, Generator, Heap, Promise}

  # an @actor class holding a promise, written as the backend would write it
  defmodule Oracle do
    def new do
      Actor.start(__MODULE__, fn ->
        this = Actor.init_self(__MODULE__, %{pending: nil, outer: nil})
        Heap.put(this, :pending, Promise.new())
        this
      end)
    end

    def now(this), do: Actor.run(this, fn -> p = Promise.new(); Promise.complete(p, 42); p end)
    def later(this), do: Actor.run(this, fn -> Heap.get(this, :pending) end)
    def fresh(this), do: Actor.run(this, fn -> Heap.put(this, :pending, Promise.new()) end)

    # a promise of a promise: the outer one settles to `:pending`'s promise
    def nest(this) do
      Actor.run(this, fn ->
        outer = Promise.new()
        Heap.put(this, :outer, outer)
        outer
      end)
    end

    def unnest(this), do: Actor.run(this, fn -> Promise.complete(Heap.get(this, :outer), Heap.get(this, :pending)) end)
    def settle(this, v), do: Actor.run(this, fn -> Promise.complete(Heap.get(this, :pending), v) end)

    # an async block in the actor that awaits `p` and reports to `to`
    def wait_on(this, p, to) do
      Actor.run(this, fn ->
        Async.run(fn -> TemperCorePromiseTest.awaiter(p, fn v -> send(to, {:oracle_got, v}) end) end)
      end)
    end
  end

  # an @actor whose constructor awaits `p` and reports to `to`
  defmodule Listener do
    def new(p, to) do
      Actor.start(__MODULE__, fn ->
        this = Actor.init_self(__MODULE__, %{})
        Async.run(fn -> TemperCorePromiseTest.awaiter(p, fn v -> send(to, {:listener_got, v}) end) end)
        this
      end)
    end

    def ping(this), do: Actor.run(this, fn -> nil end)
  end

  # an @actor whose `relay` answers a promise of what `p` settles to, plus one
  defmodule Relay do
    def new, do: Actor.start(__MODULE__, fn -> Actor.init_self(__MODULE__, %{}) end)

    def relay(this, p) do
      Actor.run(this, fn ->
        out = Promise.new()
        Async.run(fn -> TemperCorePromiseTest.awaiter(p, &Promise.complete(out, &1 + 1)) end)
        out
      end)
    end
  end

  def awaiter(p, then) do
    k = Heap.new(:cell, %{v: 0})

    Generator.adapt(fn g ->
      case Heap.get(k, :v) do
        0 ->
          Heap.put(k, :v, 1)
          Promise.awake_upon(p, g)
          {:value, :empty}

        _ ->
          then.(try do: Promise.result(p), rescue: (e -> e))
          :done
      end
    end)
  end

  # a generator whose step k runs steps[k]
  defp gen(steps) do
    k = Heap.new(:cell, %{v: 0})

    Generator.adapt(fn me ->
      i = Heap.get(k, :v)
      Heap.put(k, :v, i + 1)
      Enum.at(steps, i).(me)
    end)
  end

  test "the first settle wins, a broken promise bubbles, a pending one panics" do
    p = Promise.new()
    Promise.complete(p, "Blue")
    Promise.complete(p, "No, yell...")
    assert Promise.result(p) == "Blue"
    q = Promise.new()
    Promise.break_promise(q)
    Promise.complete(q, "too late")
    assert_raise TemperCore.Bubble, fn -> Promise.result(q) end
    assert_raise TemperCore.Panic, fn -> Promise.result(Promise.new()) end
  end

  test "async only enqueues; drain runs the queue" do
    me = self()
    b = Promise.new()

    Async.run(fn ->
      gen([
        fn g ->
          Promise.awake_upon(b, g)
          {:value, :empty}
        end,
        fn _ ->
          send(me, {:after, Promise.result(b)})
          :done
        end
      ])
    end)

    Async.run(fn ->
      gen([
        fn _ ->
          send(me, :before)
          Promise.complete(b, :empty)
          :done
        end
      ])
    end)

    refute_received :before
    assert Async.drain() == nil
    assert_received :before
    assert_received {:after, :empty}
  end

  test "waiters wake in the order they parked" do
    me = self()
    p = Promise.new()

    for name <- [:first, :second, :third] do
      Async.run(fn ->
        gen([
          fn g ->
            Promise.awake_upon(p, g)
            {:value, :empty}
          end,
          fn _ ->
            send(me, {name, Promise.result(p)})
            :done
          end
        ])
      end)
    end

    Async.run(fn -> gen([fn _ -> Promise.complete(p, 1); :done end]) end)
    Async.drain()
    assert {:messages, [{:first, 1}, {:second, 1}, {:third, 1}]} = Process.info(self(), :messages)
  end

  # One FIFO for both made a block that awaits settled promises take turns
  # with every other block: "a0 b0 a1 b1 a2 a3", where js, py and the
  # interpreter all print "a0 a1 a2 a3 b0 b1".
  test "a block runs through its awaits of settled promises before the next block starts" do
    me = self()
    p = Promise.new()
    Promise.complete(p, :empty)
    await_then = fn label -> fn g -> send(me, {:step, label}); Promise.awake_upon(p, g); {:value, :empty} end end
    last = fn label -> fn _ -> send(me, {:step, label}) && :done end end
    Async.run(fn -> gen([await_then.("a0"), await_then.("a1"), await_then.("a2"), last.("a3")]) end)
    Async.run(fn -> gen([await_then.("b0"), last.("b1")]) end)
    Async.drain()
    steps = for _ <- 1..6, do: (assert_receive {:step, s}; s)
    assert steps == ~w(a0 a1 a2 a3 b0 b1)
  end

  test "awaiting a settled promise goes through the queue, not the stack" do
    p = Promise.new()
    Promise.complete(p, 1)
    n = Heap.new(:cell, %{v: 0})

    Async.run(fn ->
      Generator.adapt(fn g ->
        if Heap.get(n, :v) < 100_000 do
          Heap.put(n, :v, Heap.get(n, :v) + 1)
          Promise.awake_upon(p, g)
          # not resumed inline: the step has not returned yet
          {:value, :empty}
        else
          :done
        end
      end)
    end)

    Async.drain()
    assert Heap.get(n, :v) == 100_000
  end

  test "a promise an actor returns can be awaited by its caller, settled or not" do
    me = self()
    o = Oracle.new()
    now = Oracle.now(o)
    later = Oracle.later(o)
    # a stand-in for the actor's promise, which only the actor can settle
    assert Heap.get(now, :foreign)
    Async.run(fn -> awaiter(now, &send(me, {:now, &1})) end)
    Async.run(fn -> awaiter(later, &send(me, {:later, &1})) end)
    Async.drain_queue()
    assert_received {:now, 42}
    refute_received {:later, _}
    Oracle.settle(o, 7)
    # drain/0 waits for the settle to arrive from TemperCore.Promises
    Async.drain()
    assert_received {:later, 7}
  end

  # The settle went to TemperCore.Promises as a cast and reached the
  # awaiting process whenever it did, so the waiter woke only once the run
  # queue was empty: "sent, after one await, after two awaits, got hi",
  # where js says "sent, got hi, after one await, after two awaits".
  test "a waiter on an actor's promise wakes in the turn of the call that settled it" do
    me = self()
    o = Oracle.new()
    later = Oracle.later(o)
    Async.run(fn -> awaiter(later, &send(me, {:step, "got #{&1}"})) end)

    done = Promise.new()
    Promise.complete(done, :empty)

    # the program's sender: settle through the actor, then await a promise
    # that has already settled, which queues it again at once
    Async.run(fn ->
      gen([
        fn me_gen ->
          Oracle.settle(o, "hi")
          send(me, {:step, "sent"})
          Promise.awake_upon(done, me_gen)
          {:value, :empty}
        end,
        fn _ ->
          send(me, {:step, "after one await"})
          :done
        end
      ])
    end)

    Async.drain()
    steps = for _ <- 1..3, do: (assert_receive {:step, s}; s)
    assert steps == ["sent", "got hi", "after one await"]
  end

  test "a promise passed into an actor wakes the actor's async block when it settles" do
    o = Oracle.new()
    mine = Promise.new()
    Oracle.wait_on(o, mine, self())
    refute_received {:oracle_got, _}
    Promise.complete(mine, "hello")
    assert_receive {:oracle_got, "hello"}
  end

  test "only the process that made a promise settles it, and its end breaks the wait" do
    o = Oracle.new()
    later = Oracle.later(o)
    assert_raise TemperCore.Panic, ~r/can only be settled there/, fn -> Promise.complete(later, 1) end
    me = self()
    Async.run(fn -> awaiter(later, &send(me, {:later, &1})) end)
    Async.drain_queue()
    Actor.stop(o)
    Async.drain()
    assert_received {:later, %TemperCore.Panic{message: "the process that made this promise ended" <> _}}
  end

  defp hub_ids, do: :sys.get_state(TemperCore.Promises).promises |> Map.keys() |> MapSet.new()

  # A long-lived actor handing out promise after promise, some settled before
  # they leave it and some after, every one awaited here.
  test "TemperCore.Promises forgets a promise once everyone holding it has its result" do
    me = self()
    o = Oracle.new()

    ids =
      for i <- 1..500, reduce: [] do
        ids ->
          now = Oracle.now(o)
          later = Oracle.fresh(o)
          Async.run(fn -> awaiter(now, &send(me, {:now, &1})) end)
          Async.run(fn -> awaiter(later, &send(me, {:later, &1})) end)
          Async.drain_queue()
          Oracle.settle(o, i)
          Async.drain()
          assert_received {:now, 42}
          assert_received {:later, ^i}
          [now.id, later.id | ids]
      end

    # tests run concurrently, so look for these ids, not at the table's size
    assert MapSet.disjoint?(hub_ids(), MapSet.new(ids))
    # and nothing of them is left here either, once nothing reaches them
    Heap.collect()
    assert Heap.size() == 0
    refute Enum.any?(Process.get_keys(), &match?({TemperCore.Promise, id} when is_reference(id), &1))
  end

  test "a promise awaited long after it settled, and after the registry forgot it, has its value" do
    me = self()
    o = Oracle.new()
    later = Oracle.fresh(o)
    Oracle.settle(o, "kept")
    # many more come and go first
    for _ <- 1..50, do: (Oracle.fresh(o); Oracle.settle(o, 0))
    refute Map.has_key?(:sys.get_state(TemperCore.Promises).promises, later.id)
    Async.run(fn -> awaiter(later, &send(me, {:later, &1})) end)
    Async.drain()
    assert_received {:later, "kept"}
  end

  # The call is made from another process, since it blocks until the
  # suspended actor answers; that process holds the promise as `a` sent it.
  test "a promise that settles while the call carrying it waits in the actor's mailbox" do
    me = self()
    a = Oracle.new()
    b = Oracle.new()
    pid = Actor.whereis(b)
    :ok = :sys.suspend(pid)

    spawn(fn ->
      later = Oracle.fresh(a)
      send(me, {:later, later})
      Oracle.wait_on(b, later, me)
    end)

    assert_receive {:later, later}
    wait_until(fn -> Process.info(pid, :message_queue_len) == {:message_queue_len, 1} end)
    Oracle.settle(a, "in flight")
    :ok = :sys.resume(pid)
    assert_receive {:oracle_got, "in flight"}
    refute Map.has_key?(:sys.get_state(TemperCore.Promises).promises, later.id)
  end

  test "a promise from one actor passed on to another wakes the second when the first settles it" do
    me = self()
    a = Oracle.new()
    b = Oracle.new()
    later = Oracle.fresh(a)
    Oracle.wait_on(b, later, me)
    Oracle.settle(a, "passed on")
    assert_receive {:oracle_got, "passed on"}
  end

  test "a promise settled to another promise brings it along" do
    me = self()
    o = Oracle.new()
    inner = Oracle.fresh(o)
    outer = Oracle.nest(o)
    Async.run(fn -> awaiter(outer, &send(me, {:outer, &1})) end)
    Async.drain_queue()
    Oracle.unnest(o)
    Async.drain()
    assert_received {:outer, got}
    assert got == inner
    Async.run(fn -> awaiter(got, &send(me, {:inner, &1})) end)
    Async.drain_queue()
    Oracle.settle(o, "inside")
    Async.drain()
    assert_received {:inner, "inside"}
  end

  test "a constructor argument that is a pending promise wakes the new actor" do
    mine = Promise.new()
    _listener = Listener.new(mine, self())
    Promise.complete(mine, "built")
    assert_receive {:listener_got, "built"}
    refute Map.has_key?(:sys.get_state(TemperCore.Promises).promises, mine.id)
  end

  # The registry forgot the promise once it had told the first process, so
  # the restarted one, given the same constructor arguments, cannot learn it.
  test "a supervised actor restarted after its constructor's promise settled cannot await it, and says so" do
    mine = Promise.new()
    listener = Actor.supervised(fn -> Listener.new(mine, self()) end)
    Promise.complete(mine, "first")
    assert_receive {:listener_got, "first"}
    old = Actor.whereis(listener)
    Process.exit(old, :kill)
    wait_until(fn -> Actor.whereis(listener) not in [nil, old] end)
    Listener.ping(listener)
    assert_receive {:listener_got, %TemperCore.Panic{message: "this promise settled before the actor holding it restarted" <> _}}
  end

  test "a promise ref that did not come with an actor call cannot be awaited" do
    me = self()
    spawn(fn -> send(me, {:ref, Promise.new()}) end)
    assert_receive {:ref, stray}
    assert_raise TemperCore.Panic, ~r/did not come with an actor call/, fn -> Promise.result(stray) end
    assert_raise TemperCore.Panic, ~r/did not come with an actor call/, fn -> Promise.awake_upon(stray, nil) end
  end

  defp wait_until(ready) do
    unless ready.() do
      Process.sleep(1)
      wait_until(ready)
    end
  end

  # What a library's __temper_main__ does last. Each relay wakes in a turn
  # of its own, for a settle the one before it sent, so nothing has
  # happened yet when the first promise is completed here.
  test "wait_idle returns only once the actors' work after the last await is done" do
    first = Promise.new()
    last = Enum.reduce(1..5, first, fn _, p -> Relay.relay(Relay.new(), p) end)
    Oracle.wait_on(Oracle.new(), last, self())
    Promise.complete(first, 0)
    Actor.wait_idle()
    # received already, not awaited: wait_idle has waited
    assert_received {:oracle_got, 5}
  end

  # js runs a constructor's async block once the top level is done. An
  # actor's ran only at the end of its next call's turn, and never for an
  # actor nobody called again: `wait_idle` had nothing to wait for.
  test "an actor constructor's async steps run as part of creating it" do
    me = self()

    Actor.start(__MODULE__, fn ->
      this = Actor.init_self(__MODULE__, %{})
      Async.run(fn -> gen([fn _ -> send(me, :constructor_async_ran) && :done end]) end)
      this
    end)

    assert_received :constructor_async_ran
  end
end
