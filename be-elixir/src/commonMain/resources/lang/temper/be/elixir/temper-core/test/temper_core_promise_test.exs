defmodule TemperCorePromiseTest do
  use ExUnit.Case, async: true
  alias TemperCore.{Actor, Async, Generator, Heap, Promise}

  # an @actor class holding a promise, written as the backend would write it
  defmodule Oracle do
    def new do
      Actor.start(__MODULE__, fn ->
        this = Actor.init_self(__MODULE__, %{pending: nil})
        Heap.put(this, :pending, Promise.new())
        this
      end)
    end

    def now(this), do: Actor.run(this, fn -> p = Promise.new(); Promise.complete(p, 42); p end)
    def later(this), do: Actor.run(this, fn -> Heap.get(this, :pending) end)
    def settle(this, v), do: Actor.run(this, fn -> Promise.complete(Heap.get(this, :pending), v) end)

    # an async block in the actor that awaits `p` and reports to `to`
    def wait_on(this, p, to) do
      Actor.run(this, fn ->
        Async.run(fn -> TemperCorePromiseTest.awaiter(p, fn v -> send(to, {:oracle_got, v}) end) end)
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
    refute Heap.local?(now)
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
end
