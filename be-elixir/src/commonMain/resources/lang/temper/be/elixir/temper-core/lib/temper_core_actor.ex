defmodule TemperCore.Actor do
  @moduledoc """
  An instance of an `@actor` class: a process that holds the object's fields
  and runs its methods, so any number of processes can share one live object.

  - **Identity.** An actor object is `%TemperCore.Actor{class, pid}`. It is an
    ordinary term, so it can be sent anywhere, stored anywhere and compared
    with `==`.
  - **Calls.** A method call from another process is a `GenServer.call`. The
    method runs in the actor, which is the only place its fields exist. A
    call from inside the actor, `this.m()`, is a plain call. Calls stay
    synchronous, as Temper expects.
  - **Crossing.** Arguments and results are copied, as every BEAM message is.
    Copying a mutable non-actor object would break Temper's aliasing, so
    sending one raises a `TemperCore.Panic` that names it. Immutable values
    (strings, numbers, lists, maps, `@imu` structs) and other actors go
    through.
  - **Errors.** A bubble or panic in the method is raised again in the
    caller, so `orelse` works across processes.
  - **Cycles.** If A calls B and B calls A while A is still waiting, A could
    never answer. Every call carries the chain of actors it passed through,
    and calling back into one of them raises a `TemperCore.Panic` instead of
    deadlocking.
  - **Lifetime.** An actor ends when the process that created it ends, for
    any reason. A link alone would not do this: a link only propagates an
    abnormal exit, and an actor whose creator returned normally kept running.
    So the actor also monitors its creator. The link stays, so a crash in
    either one takes the other down. Calling an actor that has ended raises
    `TemperCore.Panic`. To outlive a short-lived process, an actor has to be
    created by a longer-lived one.
  - **Module state.** An actor starts with a copy of its creator's Temper
    globals. Module-level mutable state is per process from then on.
  """
  use GenServer
  alias TemperCore.{Heap, Ref}

  defstruct [:class, :pid]

  @self {__MODULE__, :fields}
  @chain {__MODULE__, :chain}

  # -- generated code calls these ---------------------------------------------

  @doc "`new C(...)` for an `@actor` class: starts the process and runs the constructor in it."
  def start(class, constructor) when is_function(constructor, 0) do
    sendable!(constructor, "a constructor argument of #{inspect(class)}")

    globals =
      for {{tag, _} = k, v} <- Process.get(), tag in [TemperCore.Global, :temper_init], into: %{}, do: {k, v}

    # linked only once the constructor has succeeded: a linked process that
    # stops in init would take its caller down instead of raising there
    case GenServer.start(__MODULE__, {constructor, Heap.export(globals), chain_for_callee(), self()}) do
      {:ok, pid} ->
        Process.link(pid)
        %__MODULE__{class: class, pid: pid}

      {:error, {:temper_raise, kind, reason, stack}} ->
        :erlang.raise(kind, reason, stack)
    end
  end

  @doc "The constructor's `this`: inside the new process, an actor whose fields live here."
  def init_self(class, fields) do
    Process.put(@self, Heap.new(class, fields))
    %__MODULE__{class: class, pid: self()}
  end

  @doc "A method body: run here if this is the actor, otherwise in the actor."
  def run(%__MODULE__{pid: pid}, body) when pid == self(), do: body.()

  def run(%__MODULE__{class: class, pid: pid}, body) do
    if pid in chain_for_callee() do
      raise TemperCore.Panic, "actor call cycle: #{inspect(class)} is already waiting on this call"
    end

    sendable!(body, "an argument to #{inspect(class)}")

    reply =
      try do
        GenServer.call(pid, {:run, body, chain_for_callee()}, :infinity)
      catch
        :exit, {reason, _} when reason in [:noproc, :normal, :shutdown] ->
          raise TemperCore.Panic, "#{inspect(class)} actor has ended"
      end

    case reply do
      {:ok, value} -> Heap.import(value)
      {:raise, kind, reason, stack} -> :erlang.raise(kind, reason, stack)
    end
  end

  @doc "The heap object holding this actor's fields, for `Heap.get`/`put` on `this`."
  def fields(%__MODULE__{class: class, pid: pid}) do
    if pid == self(),
      do: Process.get(@self),
      else: raise(TemperCore.Panic, "a field of #{inspect(class)} read outside its actor")
  end

  # -- the process --------------------------------------------------------------

  @impl true
  def init({constructor, globals, chain, creator}) do
    Process.monitor(creator)
    Heap.import(globals)
    Process.put(@chain, chain)

    try do
      constructor.()
      {:ok, nil}
    catch
      kind, reason -> {:stop, {:temper_raise, kind, reason, __STACKTRACE__}}
    end
  end

  @impl true
  def handle_call({:run, body, chain}, _from, state) do
    outer = Process.get(@chain)
    Process.put(@chain, chain)

    reply =
      try do
        value = Heap.entry(body)
        TemperCore.Async.drain()
        sendable!(value, "a result")
        # results that are values cross as they are; Heap.export keeps the
        # rule in one place should that ever widen
        {:ok, Heap.export(value)}
      catch
        kind, reason -> {:raise, kind, reason, __STACKTRACE__}
      after
        Process.put(@chain, outer)
      end

    {:reply, reply, state}
  end

  @impl true
  def handle_info({:DOWN, _, :process, _creator, _reason}, state), do: {:stop, :normal, state}
  def handle_info(_other, state), do: {:noreply, state}

  # The actors a call made from here has passed through: those that called
  # this process, then this process itself if it is an actor.
  defp chain_for_callee do
    chain = Process.get(@chain, [])
    if Process.get(@self), do: chain ++ [self()], else: chain
  end

  # -- what may cross -----------------------------------------------------------

  @doc """
  Raises unless `term` can be copied to another process without breaking
  Temper's meaning: no mutable non-actor object anywhere inside it,
  including in a closure's captured values.
  """
  def sendable!(term, what), do: check([term], what)

  defp check([], _what), do: :ok
  defp check([%__MODULE__{} | rest], what), do: check(rest, what)

  defp check([%Ref{class: class} | _], what) do
    name = if is_atom(class), do: inspect(class), else: "object"

    raise TemperCore.Panic,
          "#{what} is a mutable #{name}, which cannot be shared with another process; " <>
            "make its class @imu to pass a copy, or @actor to share it"
  end

  defp check([[] | rest], what), do: check(rest, what)
  defp check([[h | t] | rest], what), do: check([h, t | rest], what)
  defp check([x | rest], what) when is_tuple(x), do: check([Tuple.to_list(x) | rest], what)
  defp check([x | rest], what) when is_map(x), do: check([Map.keys(x), Map.values(x) | rest], what)

  defp check([x | rest], what) when is_function(x) do
    {:env, env} = :erlang.fun_info(x, :env)
    check([env | rest], what)
  end

  defp check([_ | rest], what), do: check(rest, what)
end
