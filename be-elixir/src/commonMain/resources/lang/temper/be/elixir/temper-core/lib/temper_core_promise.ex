defmodule TemperCore.Promise do
  @moduledoc """
  A PromiseBuilder and its Promise are one heap object. `:state` is
  `:pending`, `{:ok, value}` or `:broken`; `:waiters` are generators parked
  by `awakeUpon`, newest first. The first settle wins; later ones are ignored
  and wake nothing.

  Nothing here resumes a generator directly. A settle, or an `awakeUpon` on a
  promise that has already settled, puts the generator on the run queue.
  Resuming inline would run a step inside another step, and the inner `next`
  would answer `:done` (the generator is marked done while it runs), losing
  the wakeup.
  """
  alias TemperCore.{Async, Heap}

  def new, do: Heap.new(:promise, %{state: :pending, waiters: []})

  def complete(b, value), do: settle(b, {:ok, value})
  def break_promise(b), do: settle(b, :broken)

  defp settle(b, state) do
    if Heap.get(b, :state) == :pending do
      waiters = Heap.get(b, :waiters)
      Heap.put(b, :state, state)
      Heap.put(b, :waiters, [])
      waiters |> Enum.reverse() |> Enum.each(&Async.enqueue/1)
    end

    nil
  end

  @doc "`awakeUpon(p, gen)`: step `gen` once `p` settles; via the queue even if it has."
  def awake_upon(p, gen) do
    case Heap.get(p, :state) do
      :pending -> Heap.put(p, :waiters, [gen | Heap.get(p, :waiters)])
      _ -> Async.enqueue(gen)
    end

    nil
  end

  @doc "`getPromiseResultSync(p)`: the value; a broken promise bubbles."
  def result(p) do
    case Heap.get(p, :state) do
      {:ok, value} -> value
      :broken -> raise TemperCore.Bubble, "broken promise"
      :pending -> raise TemperCore.Panic, "awaited a promise that has not settled"
    end
  end
end

defmodule TemperCore.Async do
  @moduledoc """
  The run queue, a FIFO of generators in the process dictionary. `async { }`
  enqueues its generator rather than running it, as be-js's setTimeout does,
  and settling a promise enqueues whatever waited on it. Nothing runs a
  generator but `drain/0`, which a library's `__temper_main__/0` calls last; so no step ever
  runs inside another, and a long chain of awaits is a loop, not a deepening
  stack. A program awaiting a promise nothing will settle ends when the queue
  is empty.
  """
  @key {__MODULE__, :queue}

  def run(factory) when is_function(factory, 0) do
    enqueue(factory.())
    nil
  end

  def enqueue(gen) do
    Process.put(@key, :queue.in(gen, Process.get(@key, :queue.new())))
    nil
  end

  def drain do
    case :queue.out(Process.get(@key, :queue.new())) do
      {{:value, gen}, rest} ->
        Process.put(@key, rest)
        TemperCore.Generator.next(gen)
        drain()

      {:empty, _} ->
        nil
    end
  end
end
