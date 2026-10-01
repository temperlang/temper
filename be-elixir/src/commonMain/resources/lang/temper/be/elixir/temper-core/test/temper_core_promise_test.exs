defmodule TemperCorePromiseTest do
  use ExUnit.Case, async: true
  alias TemperCore.{Async, Generator, Heap, Promise}

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
end
