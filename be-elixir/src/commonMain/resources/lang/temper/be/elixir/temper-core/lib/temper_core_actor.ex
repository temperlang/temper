defmodule TemperCore.Actor do
  @moduledoc """
  An instance of an `@actor` class: a process that holds the object's fields
  and runs its methods, so any number of processes can share one live object.

  - **Identity.** An actor is `%TemperCore.Actor{class, id}`. The id is
    registered in `TemperCore.Actors.Registry` to whichever process
    currently runs the actor, so the identity survives a supervised
    restart. It is an ordinary term: send it, store it, compare it.
  - **Calls.** A method call from another process is a `GenServer.call`, and
    the method runs in the actor, the only place its fields exist. A call
    from inside the actor (`this.m()`) is a plain call. Calls stay
    synchronous, as Temper expects.
  - **Crossing.** Arguments and results are copied, as every BEAM message is.
    Copying a mutable non-actor object would break Temper's aliasing, so
    one raises a `TemperCore.Panic` that names it. Values and other actors
    go through.
  - **Errors and crashes.** A Temper bubble or panic in a method is that
    call's result: the caller raises it again, so `orelse` works across
    processes, and the actor carries on. Anything else (an Elixir error, an
    exit) is a crash. The caller gets the error, and the actor stops.
  - **Cycles.** If A calls B and B calls A while A is still waiting, A could
    never answer. Every call carries the chain of actors it passed through,
    and calling back into one of them raises a `TemperCore.Panic` instead
    of deadlocking.
  - **Lifetime.** By default an actor ends when the process that created it
    ends, for any reason: it is linked to its creator and also monitors it,
    because a link alone ignores a normal exit. Actors created inside
    `supervised/1` are started under `TemperCore.Actors` instead. They
    outlive their creator, and after a crash they are restarted by
    re-running their constructor with the same arguments: fresh state, same
    identity. `stop/1` ends either kind.
  """
  use GenServer, restart: :transient
  alias TemperCore.Heap

  defstruct [:class, :id]

  @registry TemperCore.Actors.Registry
  @self {__MODULE__, :fields}
  @self_id {__MODULE__, :id}
  @chain {__MODULE__, :chain}
  @supervised {__MODULE__, :supervised}

  # -- for Elixir code ------------------------------------------------------------

  @doc """
  Runs `fun`; the actors it creates are supervised. They outlive the
  calling process and are restarted, with fresh state, after a crash.

      account = TemperCore.Actor.supervised(fn -> Temper.Bank.Account.new("ann") end)
  """
  def supervised(fun) do
    outer = Process.get(@supervised)
    Process.put(@supervised, true)

    try do
      fun.()
    after
      if outer, do: Process.put(@supervised, outer), else: Process.delete(@supervised)
    end
  end

  @doc "Ends an actor, supervised or not."
  def stop(%__MODULE__{} = actor), do: GenServer.stop(pid!(actor), :normal)

  @doc "The process running an actor now, or nil if it has ended."
  def whereis(%__MODULE__{id: id}) do
    case Registry.lookup(@registry, id) do
      [{pid, _}] -> pid
      [] -> nil
    end
  end

  # -- generated code calls these ---------------------------------------------

  @doc "`new C(...)` for an `@actor` class: starts the process and runs the constructor in it."
  def start(class, constructor) when is_function(constructor, 0) do
    sendable!(constructor, "a constructor argument of #{inspect(class)}")
    id = make_ref()
    chain = chain_for_callee()

    if Process.get(@supervised) do
      spec = %{
        id: id,
        start: {GenServer, :start_link, [__MODULE__, {id, constructor, chain, nil}]},
        restart: :transient
      }

      case DynamicSupervisor.start_child(TemperCore.Actors, spec) do
        {:ok, _pid} -> %__MODULE__{class: class, id: id}
        {:error, {:temper_raise, kind, reason, stack}} -> :erlang.raise(kind, reason, stack)
      end
    else
      # linked only once the constructor has succeeded: a linked process that
      # stops in init would take its caller down instead of raising there
      case GenServer.start(__MODULE__, {id, constructor, chain, self()}) do
        {:ok, pid} ->
          Process.link(pid)
          %__MODULE__{class: class, id: id}

        {:error, {:temper_raise, kind, reason, stack}} ->
          :erlang.raise(kind, reason, stack)
      end
    end
  end

  @doc "The constructor's `this`: inside the actor's process, an actor whose fields live here."
  def init_self(class, fields) do
    Process.put(@self, Heap.new(class, fields))
    %__MODULE__{class: class, id: Process.get(@self_id)}
  end

  @doc "A method body: run here if this is the actor, otherwise in the actor."
  def run(%__MODULE__{class: class, id: id} = actor, body) do
    if Process.get(@self_id) == id do
      body.()
    else
      if id in chain_for_callee() do
        raise TemperCore.Panic, "actor call cycle: #{inspect(class)} is already waiting on this call"
      end

      sendable!(body, "an argument to #{inspect(class)}")

      reply = call(actor, {:run, body, chain_for_callee()}, true)

      case reply do
        {:ok, value} -> value
        {:raise, kind, reason, stack} -> :erlang.raise(kind, reason, stack)
      end
    end
  end

  @doc "The heap object holding this actor's fields, for `Heap.get`/`put` on `this`."
  def fields(%__MODULE__{class: class, id: id}) do
    if Process.get(@self_id) == id,
      do: Process.get(@self),
      else: raise(TemperCore.Panic, "a field of #{inspect(class)} read outside its actor")
  end

  # A call that exits never ran: an actor only stops after replying to the
  # call that crashed it, so a call that finds it dead or dying was not
  # handled. Supervised, a new process takes over the same identity, so try
  # once more there. The registry can name the dead process for a moment
  # after it exits, which is how a call right after a crash reaches it.
  defp call(%__MODULE__{class: class} = actor, message, retry) do
    pid = pid!(actor)

    try do
      GenServer.call(pid, message, :infinity)
    catch
      :exit, _ ->
        if retry and restarted?(actor, pid),
          do: call(actor, message, false),
          else: raise(TemperCore.Panic, "#{inspect(class)} actor has ended")
    end
  end

  defp restarted?(actor, dead, tries \\ 20) do
    case whereis(actor) do
      pid when pid != nil and pid != dead -> true
      _ when tries > 0 -> Process.sleep(5) && restarted?(actor, dead, tries - 1)
      _ -> false
    end
  end

  # A supervised actor is briefly unregistered while it restarts.
  defp pid!(%__MODULE__{class: class} = actor, tries \\ 20) do
    case whereis(actor) do
      nil when tries > 0 ->
        Process.sleep(5)
        pid!(actor, tries - 1)

      nil ->
        raise TemperCore.Panic, "#{inspect(class)} actor has ended"

      pid ->
        pid
    end
  end

  # -- the process --------------------------------------------------------------

  @impl true
  def init({id, constructor, chain, creator}) do
    if creator, do: Process.monitor(creator)
    Process.put(@self_id, id)
    Process.put(@chain, chain)
    {:ok, _} = Registry.register(@registry, id, nil)

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

    try do
      value = Heap.entry(body)
      TemperCore.Async.drain()
      sendable!(value, "a result")
      {:reply, {:ok, value}, state}
    catch
      kind, reason ->
        reply = {:raise, kind, reason, __STACKTRACE__}
        # a Temper error is this call's result; anything else is a crash
        if temper_error?(kind, reason),
          do: {:reply, reply, state},
          else: {:stop, {:crash, kind, reason}, reply, state}
    after
      Process.put(@chain, outer)
    end
  end

  @impl true
  def handle_info({:DOWN, _, :process, _creator, _reason}, state), do: {:stop, :normal, state}
  def handle_info(_other, state), do: {:noreply, state}

  defp temper_error?(:error, %TemperCore.Bubble{}), do: true
  defp temper_error?(:error, %TemperCore.Panic{}), do: true
  defp temper_error?(_kind, _reason), do: false

  # The actors a call made from here has passed through: those that called
  # this process, then this process itself if it is an actor.
  defp chain_for_callee do
    chain = Process.get(@chain, [])

    case Process.get(@self_id) do
      nil -> chain
      id -> chain ++ [id]
    end
  end

  # -- what may cross -----------------------------------------------------------

  @doc """
  Raises unless `term` can be copied to another process without breaking
  Temper's meaning: no mutable non-actor object anywhere inside it,
  including in a closure's captured values.
  """
  def sendable!(term, what) do
    case first_unsendable([term]) do
      nil ->
        :ok

      class ->
        name = if is_atom(class), do: inspect(class), else: "object"

        raise TemperCore.Panic,
              "#{what} is a mutable #{name}, which cannot be shared with another process; " <>
                "make its class @imu to pass a copy, or @actor to share it"
    end
  end

  @doc "Whether `term` could cross to another process: `sendable!/2` without the raise."
  def sendable?(term), do: first_unsendable([term]) == nil

  defp first_unsendable([]), do: nil
  defp first_unsendable([%__MODULE__{} | rest]), do: first_unsendable(rest)
  defp first_unsendable([%TemperCore.Ref{class: class} | _]), do: class
  defp first_unsendable([[] | rest]), do: first_unsendable(rest)
  defp first_unsendable([[h | t] | rest]), do: first_unsendable([h, t | rest])
  defp first_unsendable([x | rest]) when is_tuple(x), do: first_unsendable([Tuple.to_list(x) | rest])
  defp first_unsendable([x | rest]) when is_map(x), do: first_unsendable([Map.keys(x), Map.values(x) | rest])

  defp first_unsendable([x | rest]) when is_function(x) do
    {:env, env} = :erlang.fun_info(x, :env)
    first_unsendable([env | rest])
  end

  defp first_unsendable([_ | rest]), do: first_unsendable(rest)
end
