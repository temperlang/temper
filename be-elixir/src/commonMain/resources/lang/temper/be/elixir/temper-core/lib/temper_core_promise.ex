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
  cannot read it. It crosses only with an actor call: as an argument, a
  constructor argument or a result, or inside the value of a promise that
  did. The sender's `share/2` lists the state of every promise in the
  message, and the receiver's `arrive/1` makes a stand-in for each: a heap
  object with the same id and `foreign: true`. The ref is the same term,
  `await` works on it the same way, and the heap collects it like any other
  object. Only the process that made a promise can settle it.

  A promise still pending when it is sent is kept by `TemperCore.Promises`,
  which sends `{:temper_promise, id, state, shared}` to every process holding
  a stand-in once it settles, and then forgets it. An actor takes that
  message in a turn of its own; `TemperCore.Async.drain/0` waits for it
  while something here awaits.
  """
  alias TemperCore.{Async, Heap, Ref}

  @typedoc "A PromiseBuilder, which is also its Promise."
  @type t :: TemperCore.Ref.t()

  @typedoc "The promises in one message: each one's state when it left."
  @type shared :: %{optional(reference()) => term()}

  @typedoc "What travels with a message: `shared`, and the lease for those still pending."
  @type envelope :: {shared(), reference() | nil}

  @hub TemperCore.Promises
  # the stand-ins with waiters that are still pending, by id: what wakes
  # them comes from outside, so they are roots for the heap
  @awaiting {__MODULE__, :awaiting}

  @spec new() :: t()
  def new, do: Heap.new(:promise, %{state: :pending, waiters: [], published: false, foreign: false})

  @spec complete(t(), term()) :: nil
  def complete(b, value), do: settle(b, {:ok, value})
  @spec break_promise(t()) :: nil
  def break_promise(b), do: settle(b, :broken)

  defp settle(b, state) do
    unless Heap.local?(b) and not Heap.get(b, :foreign) do
      raise TemperCore.Panic, "a promise made by another process can only be settled there"
    end

    if Heap.get(b, :state) == :pending do
      if Heap.get(b, :published) do
        with {:ok, value} <- state, do: TemperCore.Actor.sendable!(value, "the value of a promise another process awaits")
        announce(b, state)
      end

      resolve(b, state)
    end

    nil
  end

  # Tells TemperCore.Promises, which tells everyone holding it. Promises
  # inside the value go along, and those still pending are kept for the
  # same holders in the same call.
  defp announce(%Ref{id: id} = b, state) do
    {shared, pending} = survey(state_term(state))

    case GenServer.call(@hub, {:settle, id, state, shared, pending}, :infinity) do
      :ok ->
        :ok

      {:missing, ids} ->
        take_settles(ids)
        announce(b, state)
    end
  end

  defp state_term({:ok, value}), do: value
  defp state_term(_), do: nil

  defp resolve(%Ref{id: id} = p, state) do
    waiters = Heap.get(p, :waiters)
    Heap.put(p, :state, state)
    Heap.put(p, :waiters, [])
    if Heap.get(p, :foreign), do: Process.put(@awaiting, Map.delete(awaiting(), id))
    waiters |> Enum.reverse() |> Enum.each(&Async.enqueue/1)
  end

  @doc "`awakeUpon(p, gen)`: step `gen` once `p` settles; via the queue even if it has."
  @spec awake_upon(t(), TemperCore.Generator.t()) :: nil
  def awake_upon(p, gen) do
    case state!(p) do
      :pending ->
        Heap.put(p, :waiters, [gen | Heap.get(p, :waiters)])
        if Heap.get(p, :foreign), do: Process.put(@awaiting, Map.put(awaiting(), p.id, p))

      _ ->
        Async.enqueue(gen)
    end

    nil
  end

  @doc "`getPromiseResultSync(p)`: the value; a broken promise bubbles."
  @spec result(t()) :: term()
  def result(p) do
    case state!(p) do
      {:ok, value} -> value
      :broken -> raise TemperCore.Bubble, "broken promise"
      :ended -> raise TemperCore.Panic, "the process that made this promise ended without settling it"
      :lost -> raise TemperCore.Panic, "this promise settled before the actor holding it restarted, which cannot learn its result"
      _pending -> raise TemperCore.Panic, "awaited a promise that has not settled"
    end
  end

  defp state!(p) do
    if Heap.local?(p) do
      Heap.get(p, :state)
    else
      raise TemperCore.Panic,
            "a promise from another process that did not come with an actor call; " <>
              "a promise crosses processes only as an argument or result of an @actor method"
    end
  end

  # -- crossing ---------------------------------------------------------------

  @doc """
  Before a message holding the promises `term` reaches (a list of them
  will do) is sent to `to` (nil for an actor not started yet): the state
  of every one, and a lease from
  `TemperCore.Promises` that keeps those still pending, and their outcome,
  until the receiver's `arrive/1`, or until `to` ends or `release/1`.
  """
  @spec share(term(), pid() | nil) :: envelope()
  def share([], _to), do: {%{}, nil}

  def share(term, to) do
    case survey(term) do
      {shared, []} ->
        {shared, nil}

      {shared, pending} ->
        case GenServer.call(@hub, {:share, pending, to}, :infinity) do
          {:ok, lease} ->
            {shared, lease}

          {:missing, ids} ->
            take_settles(ids)
            share(term, to)
        end
    end
  end

  @doc """
  Where a message `share/2` sent arrives: a stand-in for each promise this
  process lacks, and, for those still pending, this process now holds
  them. Any that settled on the way is settled here too.
  """
  @spec arrive(envelope()) :: nil
  def arrive({shared, lease}) do
    receive_shared(shared)

    if lease do
      ids = for {id, :pending} <- shared, do: id

      case GenServer.call(@hub, {:arrive, lease, ids}, :infinity) do
        :ok -> :ok
        {:missing, ids} -> take_settles(ids)
      end
    end

    nil
  end

  @doc "A message that will never `arrive/1` lets go of its lease."
  @spec release(envelope()) :: :ok
  def release({_, nil}), do: :ok
  def release({_, lease}), do: GenServer.cast(@hub, {:release, lease})

  defp receive_shared(shared) do
    Enum.each(shared, fn {id, state} ->
      p = %Ref{class: :promise, id: id}

      cond do
        not Heap.local?(p) ->
          Heap.init(p, %{state: state, waiters: [], published: false, foreign: true})

        state != :pending and Heap.get(p, :foreign) and Heap.get(p, :state) == :pending ->
          resolve(p, state)

        true ->
          nil
      end
    end)
  end

  # TemperCore.Promises answers {:missing, ids} for promises it no longer
  # keeps pending, after sending this process the settle of each it still
  # has. Those are in the mailbox already, in this order. One that is not
  # was forgotten before this process could hear of it, which happens only
  # to a supervised actor restarted with the constructor arguments it was
  # first started with, after one of their promises settled.
  defp take_settles(ids) do
    Enum.each(ids, fn id ->
      receive do
        {:temper_promise, ^id, state, shared} -> remote_settled(id, state, shared)
      after
        0 ->
          p = %Ref{class: :promise, id: id}
          if Heap.local?(p) and Heap.get(p, :foreign) and Heap.get(p, :state) == :pending, do: resolve(p, :lost)
      end
    end)
  end

  # Every promise `term` reaches, with its state, and those still pending:
  # {id, :own} for one made here, which this marks published, and
  # {id, :held} for a stand-in. A settled promise's value is searched too,
  # since it crosses with it.
  defp survey(term) do
    {shared, pending} = survey([term], %{}, [])
    {shared, Enum.reverse(pending)}
  end

  defp survey([], shared, pending), do: {shared, pending}

  defp survey([%Ref{class: :promise, id: id} = p | rest], shared, pending) do
    if Map.has_key?(shared, id) do
      survey(rest, shared, pending)
    else
      state = state!(p)
      shared = Map.put(shared, id, state)

      case state do
        :pending ->
          kind =
            if Heap.get(p, :foreign) do
              :held
            else
              unless Heap.get(p, :published), do: Heap.put(p, :published, true)
              :own
            end

          survey(rest, shared, [{id, kind} | pending])

        {:ok, value} ->
          survey([value | rest], shared, pending)

        _ ->
          survey(rest, shared, pending)
      end
    end
  end

  defp survey([[] | rest], s, p), do: survey(rest, s, p)
  defp survey([[h | t] | rest], s, p), do: survey([h, t | rest], s, p)
  defp survey([x | rest], s, p) when is_tuple(x), do: survey([Tuple.to_list(x) | rest], s, p)
  defp survey([%Ref{} | rest], s, p), do: survey(rest, s, p)
  defp survey([x | rest], s, p) when is_map(x), do: survey([Map.keys(x), Map.values(x) | rest], s, p)

  defp survey([x | rest], s, p) when is_function(x) do
    {:env, env} = :erlang.fun_info(x, :env)
    survey([env | rest], s, p)
  end

  defp survey([_ | rest], s, p), do: survey(rest, s, p)

  @doc false
  # `TemperCore.Promises` sends {:temper_promise, id, state, shared};
  # whoever receives it passes it here, then drains the run queue. A
  # stand-in the heap has collected is not missed: nothing could await it.
  @spec remote_settled(reference(), term(), shared()) :: nil
  def remote_settled(id, state, shared) do
    receive_shared(shared)
    p = %Ref{class: :promise, id: id}

    if Heap.local?(p) and Heap.get(p, :foreign) and Heap.get(p, :state) == :pending do
      resolve(p, state)
    end

    nil
  end

  @doc false
  @spec awaiting_remote?() :: boolean()
  def awaiting_remote?, do: map_size(awaiting()) > 0

  defp awaiting, do: Process.get(@awaiting, %{})
end

defmodule TemperCore.Promises do
  @moduledoc """
  The promises that are pending and have been sent to another process, and
  who holds each. When one settles, every holder is sent the outcome, and
  the promise is forgotten: a settled promise's state travels with every
  later message that carries it (`TemperCore.Promise.share/2`), so nobody
  needs to ask for it again. A long-lived actor handing out promise after
  promise leaves nothing here.

  A holder is registered when the message carrying the promise arrives
  (`TemperCore.Promise.arrive/1`), not when it is sent, so that the settle
  cannot reach a process before the promise does. In between, the sender's
  lease keeps the promise, and its outcome if it settles, until the
  receiver arrives, or ends, or the lease is released.

  If the process that made a promise ends with it still pending, its
  holders are sent `:ended`, and their `await` panics rather than waiting
  forever.
  """
  use GenServer

  def start_link(_), do: GenServer.start_link(__MODULE__, nil, name: __MODULE__)

  @doc "How many promises this is keeping."
  @spec size() :: non_neg_integer()
  def size, do: GenServer.call(__MODULE__, :size)

  # promises: id => %{owner, holders: pids, leases, state, shared, nested},
  #   state :pending, or what it settled to while a lease keeps it
  # owners: pid => {monitor, ids}
  # leases: lease => {monitor of the receiver or nil, ids}
  # receivers: monitor => lease
  @impl true
  def init(nil), do: {:ok, %{promises: %{}, owners: %{}, leases: %{}, receivers: %{}}}

  @impl true
  def handle_call(:size, _from, s), do: {:reply, map_size(s.promises), s}

  def handle_call({:share, items, to}, {from, _}, s) do
    case missing(s, items, from) do
      [] ->
        lease = make_ref()
        mref = if to, do: Process.monitor(to)
        s = %{s | leases: Map.put(s.leases, lease, {mref, MapSet.new()})}
        s = if mref, do: put_in(s.receivers[mref], lease), else: s
        {:reply, {:ok, lease}, keep(s, items, from, [], [lease])}

      ids ->
        {:reply, {:missing, ids}, s}
    end
  end

  def handle_call({:settle, id, state, shared, nested}, {owner, _}, s) do
    case s.promises do
      %{^id => %{state: :pending} = p} ->
        case missing(s, nested, owner) do
          [] ->
            s = keep(s, nested, owner, MapSet.to_list(p.holders), MapSet.to_list(p.leases))
            Enum.each(p.holders, &send(&1, {:temper_promise, id, state, shared}))
            nested = Enum.map(nested, &elem(&1, 0))
            {:reply, :ok, keep_or_forget(s, id, %{p | state: state, shared: shared, nested: nested, holders: MapSet.new()})}

          ids ->
            {:reply, {:missing, ids}, s}
        end

      _ ->
        {:reply, :ok, s}
    end
  end

  def handle_call({:arrive, lease, ids}, {pid, _}, s) do
    s =
      case Map.pop(s.leases, lease) do
        {{mref, _}, leases} ->
          if mref, do: Process.demonitor(mref, [:flush])
          %{s | leases: leases, receivers: Map.delete(s.receivers, mref)}

        {nil, _} ->
          s
      end

    {s, missing} = arrive(s, lease, pid, ids, [])
    {:reply, if(missing == [], do: :ok, else: {:missing, Enum.reverse(missing)}), s}
  end

  @impl true
  def handle_cast({:release, lease}, s), do: {:noreply, release(s, lease)}

  @impl true
  def handle_info({:DOWN, mref, :process, pid, _}, s) do
    case s.receivers do
      %{^mref => lease} -> {:noreply, release(%{s | receivers: Map.delete(s.receivers, mref)}, lease)}
      _ -> {:noreply, owner_ended(s, pid)}
    end
  end

  # The pending stand-ins in `items` that are not pending here: their
  # sender holds a stand-in for a promise that has settled, and was sent
  # that settle, now if this still keeps it, or earlier.
  defp missing(s, items, from) do
    for {id, :held} <- items, reduce: [] do
      ids ->
        case s.promises do
          %{^id => %{state: :pending}} ->
            ids

          %{^id => p} ->
            send(from, {:temper_promise, id, p.state, p.shared})
            ids ++ [id]

          _ ->
            ids ++ [id]
        end
    end
  end

  # Keeps each promise in `items` for these holders and leases, publishing
  # those `from` made that are not here yet.
  defp keep(s, items, from, holders, leases) do
    Enum.reduce(items, s, fn {id, _}, s ->
      p =
        Map.get_lazy(s.promises, id, fn ->
          %{owner: from, holders: MapSet.new(), leases: MapSet.new(), state: :pending, shared: %{}, nested: []}
        end)

      s = if Map.has_key?(s.promises, id), do: s, else: %{s | owners: own(s.owners, from, id)}
      p = %{p | holders: MapSet.union(p.holders, MapSet.new(holders)), leases: MapSet.union(p.leases, MapSet.new(leases))}

      s =
        Enum.reduce(leases, s, fn lease, s ->
          case s.leases do
            %{^lease => {mref, ids}} -> put_in(s.leases[lease], {mref, MapSet.put(ids, id)})
            _ -> s
          end
        end)

      put_in(s.promises[id], p)
    end)
  end

  defp own(owners, pid, id) do
    case owners do
      %{^pid => {mref, ids}} -> %{owners | pid => {mref, MapSet.put(ids, id)}}
      _ -> Map.put(owners, pid, {Process.monitor(pid), MapSet.new([id])})
    end
  end

  # The receiver of a message takes over its lease: on a pending promise it
  # becomes a holder; one that settled meanwhile it is sent, and then the
  # promises that were inside it, the same way.
  defp arrive(s, _lease, _pid, [], missing), do: {s, missing}

  defp arrive(s, lease, pid, [id | ids], missing) do
    case s.promises do
      %{^id => %{state: :pending} = p} ->
        p = %{p | holders: MapSet.put(p.holders, pid), leases: MapSet.delete(p.leases, lease)}
        arrive(put_in(s.promises[id], p), lease, pid, ids, missing)

      %{^id => p} ->
        send(pid, {:temper_promise, id, p.state, p.shared})
        s = keep_or_forget(s, id, %{p | leases: MapSet.delete(p.leases, lease)})
        arrive(s, lease, pid, p.nested ++ ids, [id | missing])

      _ ->
        arrive(s, lease, pid, ids, [id | missing])
    end
  end

  defp release(s, lease) do
    case Map.pop(s.leases, lease) do
      {{mref, ids}, leases} ->
        if mref, do: Process.demonitor(mref, [:flush])
        s = %{s | leases: leases, receivers: Map.delete(s.receivers, mref)}

        Enum.reduce(ids, s, fn id, s ->
          case s.promises do
            %{^id => p} -> keep_or_forget(s, id, %{p | leases: MapSet.delete(p.leases, lease)})
            _ -> s
          end
        end)

      {nil, _} ->
        s
    end
  end

  defp owner_ended(s, owner) do
    {{_, ids}, owners} = Map.pop(s.owners, owner, {nil, MapSet.new()})

    Enum.reduce(ids, %{s | owners: owners}, fn id, s ->
      case s.promises do
        %{^id => %{state: :pending} = p} ->
          Enum.each(p.holders, &send(&1, {:temper_promise, id, :ended, %{}}))
          keep_or_forget(s, id, %{p | state: :ended, holders: MapSet.new()})

        _ ->
          s
      end
    end)
  end

  # A settled promise stays only while a lease still waits on it.
  defp keep_or_forget(s, id, p) do
    if p.state != :pending and MapSet.size(p.leases) == 0 do
      %{s | promises: Map.delete(s.promises, id), owners: disown(s.owners, p.owner, id)}
    else
      put_in(s.promises[id], p)
    end
  end

  defp disown(owners, pid, id) do
    case owners do
      %{^pid => {mref, ids}} ->
        ids = MapSet.delete(ids, id)

        if MapSet.size(ids) == 0 do
          Process.demonitor(mref, [:flush])
          Map.delete(owners, pid)
        else
          %{owners | pid => {mref, ids}}
        end

      _ ->
        owners
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

  @spec run((-> TemperCore.Generator.t())) :: nil
  def run(factory) when is_function(factory, 0) do
    enqueue(factory.())
    nil
  end

  @spec enqueue(TemperCore.Generator.t()) :: nil
  def enqueue(gen) do
    Process.put(@key, :queue.in(gen, Process.get(@key, :queue.new())))
    nil
  end

  @doc """
  Runs the queue until it is empty, then, while a generator here awaits a
  promise another process made, waits for it to settle and runs again.
  """
  @spec drain() :: nil
  def drain do
    drain_queue()

    if TemperCore.Promise.awaiting_remote?() do
      receive do
        {:temper_promise, id, state, shared} -> TemperCore.Promise.remote_settled(id, state, shared)
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
    case :queue.out(Process.get(@key, :queue.new())) do
      {{:value, gen}, rest} ->
        Process.put(@key, rest)
        TemperCore.Generator.next(gen)
        drain_queue()

      {:empty, _} ->
        nil
    end
  end
end
