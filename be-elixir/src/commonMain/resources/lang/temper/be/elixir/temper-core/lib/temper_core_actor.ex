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
    go through, and so do promises, which are published so the receiver
    can await them. be-elixir rejects the rest when it builds; this
    check is for Elixir code, which nothing type-checks.
  - **Errors and crashes.** A Temper bubble or panic in a method is that
    call's result: the caller raises it again, so `orelse` works across
    processes, and the actor carries on. Anything else (an Elixir error, an
    exit) is a crash. The caller gets the error, and the actor stops.
  - **Re-entry.** Every call carries the chain of actors it passed through.
    If A calls B and B calls A, A is waiting on B for that same chain, so it
    runs B's call inline while it waits, as a single-threaded backend runs
    it on its one stack. A constructor that calls back into the actor
    constructing it is served the same way.
  - **Cycles.** Two chains can still block each other: A, in a turn for one
    caller, calls B while B, in a turn for another, calls A. Each actor
    records in the registry which actor it is waiting on, and a call that
    would wait on an actor already waiting, directly or through others, on
    the caller raises a `TemperCore.Panic` ("actor call cycle") instead of
    deadlocking.
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

  @typedoc "An `@actor` object: its class, and the id its process is registered under."
  @type t :: %__MODULE__{class: module(), id: reference()}

  @registry TemperCore.Actors.Registry
  @self {__MODULE__, :fields}
  @self_id {__MODULE__, :id}
  @chain {__MODULE__, :chain}
  @waiting {__MODULE__, :waiting}
  @supervised {__MODULE__, :supervised}

  # -- for Elixir code ------------------------------------------------------------

  @doc """
  Runs `fun`; the actors it creates are supervised. They outlive the
  calling process and are restarted, with fresh state, after a crash.

      account = TemperCore.Actor.supervised(fn -> Temper.Bank.Account.new("ann") end)
  """
  @spec supervised((-> result)) :: result when result: term()
  @spec supervised((-> result), Supervisor.supervisor()) :: result when result: term()
  def supervised(fun, supervisor \\ TemperCore.Actors) do
    outer = Process.get(@supervised)
    Process.put(@supervised, supervisor)

    try do
      fun.()
    after
      if outer, do: Process.put(@supervised, outer), else: Process.delete(@supervised)
    end
  end

  @doc "Ends an actor, supervised or not."
  @spec stop(t()) :: :ok
  def stop(%__MODULE__{} = actor), do: GenServer.stop(pid!(actor), :normal)

  @doc "The process running an actor now, or nil if it has ended."
  @spec whereis(t()) :: pid() | nil
  def whereis(%__MODULE__{id: id}) do
    case Registry.lookup(@registry, id) do
      [{pid, _}] -> pid
      [] -> nil
    end
  end

  # -- generated code calls these ---------------------------------------------

  @doc "`new C(...)` for an `@actor` class: starts the process and runs the constructor in it."
  @spec start(module(), (-> term())) :: t()
  def start(class, constructor), do: %__MODULE__{class: class, id: start_id(class, constructor)}

  @doc """
  `start/2`, answering only the new actor's id. Generated code writes
  `%TemperCore.Actor{class: Temper.Lib.C, id: TemperCore.Actor.start_id(...)}`,
  so the actor's class is in its type, which Dialyzer cannot see through a
  struct `start/2` builds.
  """
  @spec start_id(module(), (-> term())) :: reference()
  def start_id(class, constructor) when is_function(constructor, 0) do
    sendable!(constructor, "a constructor argument of #{inspect(class)}")
    TemperCore.Global.publish()
    id = make_ref()
    chain = chain_for_callee()
    supervisor = Process.get(@supervised)
    init_arg = {id, constructor, chain, if(supervisor, do: nil, else: self()), TemperCore.initializing()}

    # The constructor runs in the new process's init, and may call back into
    # an actor on this chain, this one included. OTP's start blocks in a
    # receive that would never serve that call, so a helper process does the
    # start while this one waits the way `run/2` waits, serving its chain.
    starting = fn ->
      if supervisor,
        do: DynamicSupervisor.start_child(supervisor, keeper(id, init_arg)),
        else: GenServer.start(__MODULE__, init_arg)
    end

    case wait_for(id, starting) do
      {:ok, pid} ->
        # linked only once the constructor has succeeded: a linked process
        # that stops in init would take its caller down instead of raising
        # there. A supervised actor is linked to its keeper instead.
        unless supervisor, do: Process.link(pid)
        id

      {:error, {:shutdown, {:failed_to_start_child, _, {:temper_raise, kind, reason, stack}}}} ->
        :erlang.raise(kind, reason, stack)

      {:error, {:temper_raise, kind, reason, stack}} ->
        :erlang.raise(kind, reason, stack)
    end
  end

  # Runs `starting` in a helper and answers its result, serving calls from
  # this process's chain meanwhile. The helper is unlinked: if this process
  # dies, the actor it was starting sees its creator go and ends too.
  defp wait_for(id, starting) do
    me = self()
    tag = make_ref()
    helper = spawn(fn -> send(me, {tag, starting.()}) end)
    mref = Process.monitor(helper)

    case waiting_on(id, fn -> await_reply(tag, mref) end) do
      {:ok, result} -> result
      {:down, reason} -> raise TemperCore.Panic, "starting an actor failed: #{inspect(reason)}"
    end
  end

  # One supervisor per supervised actor, holding that actor's restart limit
  # (OTP's default, 3 in 5 seconds). When the actor keeps crashing, only its
  # keeper gives up; the keeper is temporary, so its parent neither restarts
  # it nor counts it. The actor is significant, so stopping it normally ends
  # the keeper too.
  defp keeper(id, init_arg) do
    actor = %{
      id: :actor,
      start: {GenServer, :start_link, [__MODULE__, init_arg]},
      restart: :transient,
      significant: true
    }

    %{
      id: id,
      start: {Supervisor, :start_link, [[actor], [strategy: :one_for_one, auto_shutdown: :any_significant]]},
      restart: :temporary,
      type: :supervisor
    }
  end

  @doc "The constructor's `this`: inside the actor's process, an actor whose fields live here."
  @spec init_self(module(), map()) :: t()
  def init_self(class, fields) do
    Heap.put_root(@self, Heap.new(class, fields))
    %__MODULE__{class: class, id: Process.get(@self_id)}
  end

  @doc "A method body: run here if this is the actor, otherwise in the actor."
  @spec run(t(), (-> result)) :: result when result: term()
  def run(%__MODULE__{class: class, id: id} = actor, body) do
    if Process.get(@self_id) == id do
      body.()
    else
      sendable!(body, "an argument to #{inspect(class)}")
      TemperCore.Global.publish()
      chain = chain_for_callee()

      # An actor already on this chain is waiting in `await_reply/2` for the
      # call that led here, so it runs this one there, inline, as a
      # single-threaded backend would. Any other actor may be busy with
      # another chain, so first make sure that chain is not waiting on this.
      reply =
        if id in chain do
          waiting_on(id, fn -> call(actor, {:reenter, body, chain}, false) end)
        else
          waiting_on(id, fn ->
            cycle!(actor)
            call(actor, {:run, body, chain}, true)
          end)
        end

      TemperCore.Promise.take_settled()

      case reply do
        {:ok, value} -> value
        {:raise, kind, reason, stack} -> :erlang.raise(kind, reason, stack)
      end
    end
  end

  @doc "The heap object holding this actor's fields, for `Heap.get`/`put` on `this`."
  @spec fields(t()) :: TemperCore.Ref.t()
  def fields(%__MODULE__{class: class, id: id}) do
    if Process.get(@self_id) == id,
      do: Process.get(@self),
      else: raise(TemperCore.Panic, "a field of #{inspect(class)} read outside its actor")
  end

  # Only a call that cannot have run is tried again, on the restarted
  # process that has the same identity:
  #
  # - `:noproc`: the process was gone before the message arrived, which is
  #   how a call right after a crash reaches the dead pid the registry still
  #   names.
  # - `{:crash, ...}`: the actor stops with this reason only after replying
  #   to the call that crashed it, and it handles one message at a time, so
  #   any other call that sees it was still waiting in the mailbox.
  #
  # Any other exit can come while the method is running (the process was
  # killed, or a link took it down). Running it again on the restarted
  # actor's fresh state would turn at-most-once into at-least-once, so the
  # caller sees the exit, as OTP's callers do.
  defp call(%__MODULE__{class: class} = actor, message, retry) do
    pid = pid!(actor)
    mref = Process.monitor(pid)
    send(pid, {:"$gen_call", {self(), mref}, message})

    case await_reply(mref, mref) do
      {:ok, reply} ->
        reply

      {:down, reason} when reason == :noproc or elem(reason, 0) == :crash ->
        if retry and restarted?(actor, pid),
          do: call(actor, message, false),
          else: raise(TemperCore.Panic, "#{inspect(class)} actor has ended")

      {:down, reason} ->
        raise TemperCore.Panic, "#{inspect(class)} actor ended during the call: #{inspect(reason)}"
    end
  end

  # GenServer.call's receive, plus one more kind of message: a call from
  # this process's own chain. That chain is blocked on this process, so it
  # runs here, inline, and the wait goes on. Calls from other chains stay
  # in the mailbox until the turn ends.
  defp await_reply(tag, mref) do
    receive do
      {^tag, reply} ->
        Process.demonitor(mref, [:flush])
        {:ok, reply}

      {:DOWN, ^mref, :process, _, reason} ->
        {:down, reason}

      {:"$gen_call", from, {:reenter, body, chain}} ->
        reply = turn(body, chain, false)
        GenServer.reply(from, reply)

        # a crash is still a crash: the outer turn ends with it too
        with {:raise, kind, reason, stack} <- reply,
             false <- temper_error?(kind, reason),
             do: :erlang.raise(kind, reason, stack)

        await_reply(tag, mref)
    end
  end

  # While `fun` runs, this actor is waiting on the actor `id`, as recorded
  # in the registry for `cycle!/1` to follow. Waits nest when a call is
  # served inline; the innermost is the one that blocks.
  defp waiting_on(id, fun) do
    case Process.get(@self_id) do
      nil ->
        fun.()

      me ->
        outer = Process.get(@waiting)
        Process.put(@waiting, id)
        Registry.update_value(@registry, me, fn _ -> id end)

        try do
          fun.()
        after
          Process.put(@waiting, outer)
          Registry.update_value(@registry, me, fn _ -> outer end)
        end
    end
  end

  # A call into an actor that is waiting, directly or through other actors,
  # on this one could never be answered. Each side records its wait before
  # it looks, so of two actors calling each other at once, at least one
  # sees the cycle.
  defp cycle!(%__MODULE__{class: class, id: callee}) do
    case Process.get(@self_id) do
      nil -> :ok
      me -> follow_waits(callee, me, MapSet.new(), class)
    end
  end

  defp follow_waits(nil, _me, _seen, _class), do: :ok

  defp follow_waits(id, me, seen, class) do
    cond do
      MapSet.member?(seen, id) ->
        :ok

      true ->
        case Registry.lookup(@registry, id) do
          [{_, ^me}] ->
            raise TemperCore.Panic,
                  "actor call cycle: #{inspect(class)} is waiting, directly or through other actors, on the caller"

          [{_, next}] ->
            follow_waits(next, me, MapSet.put(seen, id), class)

          [] ->
            :ok
        end
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
  def init({id, constructor, chain, creator, initializing}) do
    TemperCore.put_initializing(initializing)
    if creator, do: Process.monitor(creator)
    Process.put(@self_id, id)
    Process.put(@chain, chain)
    {:ok, _} = Registry.register(@registry, id, nil)

    try do
      constructor.()
      TemperCore.Global.publish()
      {:ok, nil}
    catch
      kind, reason -> {:stop, {:temper_raise, kind, reason, __STACKTRACE__}}
    end
  end

  @impl true
  def handle_call({:run, body, chain}, _from, state) do
    case turn(body, chain, true) do
      {:raise, kind, reason, _} = reply ->
        # a Temper error is this call's result; anything else is a crash
        if temper_error?(kind, reason),
          do: {:reply, reply, state},
          else: {:stop, {:crash, kind, reason}, reply, state}

      reply ->
        {:reply, reply, state}
    end
  end

  # Only a process waiting in `await_reply/2` takes these. One that arrives
  # here came from a chain this actor is no longer on.
  def handle_call({:reenter, _body, _chain}, _from, state) do
    {:reply, {:raise, :error, %TemperCore.Panic{message: "an actor was called back after its call had ended"}, []}, state}
  end

  # One call's body, then, for a turn of its own, the async steps it started.
  # A call served inline is part of the turn that is waiting, so it leaves
  # the run queue to that turn.
  defp turn(body, chain, drain) do
    outer = Process.get(@chain)
    Process.put(@chain, chain)

    try do
      value = Heap.run(body)
      if drain, do: TemperCore.Async.drain_queue()
      TemperCore.Global.publish()
      sendable!(value, "a result")
      {:ok, value}
    catch
      kind, reason -> {:raise, kind, reason, __STACKTRACE__}
    after
      Process.put(@chain, outer)
    end
  end

  # A promise made elsewhere that a generator here awaits has settled: its
  # waiters run now, as a turn of their own.
  @impl true
  def handle_info({:temper_promise, id, settled}, state) do
    TemperCore.Promise.remote_settled(id, settled)
    TemperCore.Async.drain_queue()
    TemperCore.Global.publish()
    {:noreply, state}
  end

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
  @spec sendable!(term(), String.t()) :: :ok
  def sendable!(term, what) do
    case first_unsendable([term], true) do
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
  @spec sendable?(term()) :: boolean()
  def sendable?(term), do: first_unsendable([term], false) == nil

  # With `publish`, a promise is sendable, and is published on the way, so
  # whoever receives its ref can await it (see `TemperCore.Promise`).
  defp first_unsendable([], _), do: nil
  defp first_unsendable([%__MODULE__{} | rest], p), do: first_unsendable(rest, p)

  defp first_unsendable([%TemperCore.Ref{class: :promise} = promise | rest], true) do
    TemperCore.Promise.publish(promise)
    first_unsendable(rest, true)
  end

  defp first_unsendable([%TemperCore.Ref{class: class} | _], _), do: class
  defp first_unsendable([[] | rest], p), do: first_unsendable(rest, p)
  defp first_unsendable([[h | t] | rest], p), do: first_unsendable([h, t | rest], p)
  defp first_unsendable([x | rest], p) when is_tuple(x), do: first_unsendable([Tuple.to_list(x) | rest], p)
  defp first_unsendable([x | rest], p) when is_map(x), do: first_unsendable([Map.keys(x), Map.values(x) | rest], p)

  defp first_unsendable([x | rest], p) when is_function(x) do
    {:env, env} = :erlang.fun_info(x, :env)
    first_unsendable([env | rest], p)
  end

  defp first_unsendable([_ | rest], p), do: first_unsendable(rest, p)
end
