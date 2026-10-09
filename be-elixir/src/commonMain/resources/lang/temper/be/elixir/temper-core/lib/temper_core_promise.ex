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

  **Crossing processes.** A promise is a heap object, so another process
  cannot read it. When one crosses an actor's boundary, as an argument or a
  result, `TemperCore.Actor.sendable!/2` publishes it: its state is copied
  to `TemperCore.Promises`, and every later settle is too. Its ref stays
  the same term. In a process whose heap does not have it, `awake_upon/2`
  subscribes to it there and `result/1` reads the copy that arrived, so
  `await` works the same on both sides. Only the process that made it can
  complete or break it.
  """
  alias TemperCore.{Async, Heap}

  @typedoc "A PromiseBuilder, which is also its Promise."
  @type t :: TemperCore.Ref.t()

  @hub TemperCore.Promises
  @awaiting {__MODULE__, :awaiting}

  @spec new() :: t()
  def new, do: Heap.new(:promise, %{state: :pending, waiters: [], published: false})

  @spec complete(t(), term()) :: nil
  def complete(b, value), do: settle(b, {:ok, value})
  @spec break_promise(t()) :: nil
  def break_promise(b), do: settle(b, :broken)

  defp settle(b, state) do
    unless Heap.local?(b) do
      raise TemperCore.Panic, "a promise made by another process can only be settled there"
    end

    if Heap.get(b, :state) == :pending do
      waiters = Heap.get(b, :waiters)
      Heap.put(b, :state, state)
      Heap.put(b, :waiters, [])

      if Heap.get(b, :published) do
        with {:ok, value} <- state, do: TemperCore.Actor.sendable!(value, "the value of a promise another process awaits")
        TemperCore.Global.publish()
        # a call, not a cast: the hub has sent the news to every process
        # awaiting it before this one goes on (see `take_settled/0`)
        :ok = GenServer.call(@hub, {:settle, b.id, state}, :infinity)
      end

      waiters |> Enum.reverse() |> Enum.each(&Async.enqueue/1)
    end

    nil
  end

  @doc """
  Makes a promise of this process readable by others, before its ref is
  sent. A ref this process's heap does not have was published by the
  process that made it.
  """
  @spec publish(t()) :: :ok
  def publish(p) do
    if Heap.local?(p) and not Heap.get(p, :published) do
      Heap.put(p, :published, true)
      :ok = GenServer.call(@hub, {:publish, p.id, Heap.get(p, :state)}, :infinity)
    end

    :ok
  end

  @doc "`awakeUpon(p, gen)`: step `gen` once `p` settles; via the queue even if it has."
  @spec awake_upon(t(), TemperCore.Generator.t()) :: nil
  def awake_upon(p, gen) do
    if Heap.local?(p) do
      case Heap.get(p, :state) do
        :pending -> Heap.put(p, :waiters, [gen | Heap.get(p, :waiters)])
        _ -> Async.enqueue(gen)
      end
    else
      awake_upon_remote(p, gen)
    end

    nil
  end

  @doc "`getPromiseResultSync(p)`: the value; a broken promise bubbles."
  @spec result(t()) :: term()
  def result(p) do
    state = if Heap.local?(p), do: Heap.get(p, :state), else: remote_state(p)

    case state do
      {:ok, value} -> value
      :broken -> raise TemperCore.Bubble, "broken promise"
      :ended -> raise TemperCore.Panic, "the process that made this promise ended without settling it"
      _pending -> raise TemperCore.Panic, "awaited a promise that has not settled"
    end
  end

  # -- a promise another process made -----------------------------------------
  #
  # This process keeps {state, waiters} for each one it has awaited, under
  # {TemperCore.Promise, id}, and the ids it still waits on under @awaiting.

  defp awake_upon_remote(%{id: id}, gen) do
    case Process.get({__MODULE__, id}) do
      nil ->
        case GenServer.call(@hub, {:subscribe, id, self()}, :infinity) do
          {:settled, state} ->
            Heap.put_root({__MODULE__, id}, {state, []})
            Async.enqueue(gen)

          :pending ->
            Heap.put_root({__MODULE__, id}, {:pending, [gen]})
            Process.put(@awaiting, MapSet.put(awaiting(), id))

          :unknown ->
            raise TemperCore.Panic, "a promise from another process that was never shared with this one"
        end

      {:pending, waiters} ->
        Heap.put_root({__MODULE__, id}, {:pending, [gen | waiters]})

      _settled ->
        Async.enqueue(gen)
    end
  end

  defp remote_state(%{id: id}) do
    case Process.get({__MODULE__, id}) do
      {state, _} -> state
      nil -> :pending
    end
  end

  @doc false
  # `TemperCore.Promises` sends {:temper_promise, id, state}; whoever
  # receives it passes it here, then drains the run queue.
  @spec remote_settled(reference(), term()) :: nil
  def remote_settled(id, state) do
    case Process.get({__MODULE__, id}) do
      {:pending, waiters} ->
        Heap.put_root({__MODULE__, id}, {state, []})
        Process.put(@awaiting, MapSet.delete(awaiting(), id))
        waiters |> Enum.reverse() |> Enum.each(&Async.enqueue/1)

      _ ->
        nil
    end

    nil
  end

  @doc """
  Handles the settles that have already arrived, without waiting. A call
  into an actor runs this when it returns: a promise the actor settled
  during the call was sent here before the actor replied, so its waiters
  join the run queue now, ahead of whatever the caller queues next, as
  they would on a single thread.
  """
  @spec take_settled() :: nil
  def take_settled do
    receive do
      {:temper_promise, id, state} ->
        remote_settled(id, state)
        take_settled()
    after
      0 -> nil
    end
  end

  @doc false
  @spec awaiting_remote?() :: boolean()
  def awaiting_remote?, do: MapSet.size(awaiting()) > 0

  defp awaiting, do: Process.get(@awaiting, MapSet.new())
end

defmodule TemperCore.Promises do
  @moduledoc """
  The state of every promise that has crossed between processes, and who
  waits on it. The process that made a promise publishes it before its ref
  leaves, and calls here with each settle; a process awaiting it subscribes, and
  is sent `{:temper_promise, id, state}` once it settles, or at once if it
  already has. If the maker ends with the promise still pending, its
  subscribers are sent `:ended`, and their `await` panics rather than
  waiting forever.

  A published promise is never forgotten, settled or not, since its ref
  may still be on its way to a process that has not asked yet. That is a
  leak for a long-lived actor that hands out many promises.
  """
  use GenServer

  def start_link(_), do: GenServer.start_link(__MODULE__, nil, name: __MODULE__)

  @impl true
  def init(nil), do: {:ok, %{promises: %{}, owners: %{}}}

  @impl true
  def handle_call({:publish, id, state}, {owner, _}, s) do
    owners =
      case s.owners do
        %{^owner => ids} -> %{s.owners | owner => [id | ids]}
        _ -> Process.monitor(owner) && Map.put(s.owners, owner, [id])
      end

    {:reply, :ok, %{s | promises: Map.put(s.promises, id, {state, []}), owners: owners}}
  end

  def handle_call({:subscribe, id, pid}, _from, s) do
    case s.promises do
      %{^id => {:pending, subs}} -> {:reply, :pending, put_in(s.promises[id], {:pending, [pid | subs]})}
      %{^id => {state, _}} -> {:reply, {:settled, state}, s}
      _ -> {:reply, :unknown, s}
    end
  end

  def handle_call({:settle, id, state}, _from, s), do: {:reply, :ok, settle(s, id, state)}

  @impl true
  def handle_info({:DOWN, _, :process, owner, _}, s) do
    {ids, owners} = Map.pop(s.owners, owner, [])
    {:noreply, Enum.reduce(ids, %{s | owners: owners}, &settle(&2, &1, :ended))}
  end

  defp settle(s, id, state) do
    case s.promises do
      %{^id => {:pending, subs}} ->
        Enum.each(subs, &send(&1, {:temper_promise, id, state}))
        put_in(s.promises[id], {state, []})

      _ ->
        s
    end
  end
end

defmodule TemperCore.Async do
  @moduledoc """
  The run queues, two FIFOs of generators in the process dictionary, as
  js has a task queue and a microtask queue. `async { }` puts its generator
  on the first rather than running it, as be-js's setTimeout does. Settling
  a promise, or awaiting one already settled, puts whatever waited on the
  second, as a js promise reaction is a microtask. `drain_queue/0` takes
  from the second while it has anything and only then starts the next
  block, so a block runs through its awaits of settled promises before
  the next block starts, as on js, py and the interpreter. Nothing runs a
  generator but the drain, which a library's `__temper_main__/0` calls
  last; so no step ever runs inside another, and a long chain of awaits
  is a loop, not a deepening stack. A program awaiting a promise nothing
  will settle ends when the queues are empty.
  """
  @key {__MODULE__, :queue}
  @started {__MODULE__, :started}

  @doc "`async { }`: the block's first step waits for every woken step before it."
  @spec run((-> TemperCore.Generator.t())) :: nil
  def run(factory) when is_function(factory, 0) do
    TemperCore.Heap.put_root(@started, :queue.in(factory.(), Process.get(@started, :queue.new())))
    nil
  end

  @doc "Queues a generator a promise woke."
  @spec enqueue(TemperCore.Generator.t()) :: nil
  def enqueue(gen) do
    TemperCore.Heap.put_root(@key, :queue.in(gen, Process.get(@key, :queue.new())))
    nil
  end

  @doc """
  Runs the queue until it is empty, then, while a generator here awaits a
  promise another process made, waits for it to settle and runs again.
  """
  @spec drain() :: nil
  def drain do
    drain_queue()
    TemperCore.Global.publish()

    if TemperCore.Promise.awaiting_remote?() do
      receive do
        {:temper_promise, id, state} -> TemperCore.Promise.remote_settled(id, state)
      end

      drain()
    end

    nil
  end

  @doc """
  Runs the queue until it is empty, and returns. An actor's turn ends this
  way: what its generators await from elsewhere wakes it in a later turn.
  """
  @spec drain_queue() :: nil
  def drain_queue do
    case take(@key) || take(@started) do
      nil ->
        nil

      gen ->
        TemperCore.Generator.next(gen)
        drain_queue()
    end
  end

  defp take(key) do
    case :queue.out(Process.get(key, :queue.new())) do
      {{:value, gen}, rest} ->
        Process.put(key, rest)
        gen

      {:empty, _} ->
        nil
    end
  end
end
