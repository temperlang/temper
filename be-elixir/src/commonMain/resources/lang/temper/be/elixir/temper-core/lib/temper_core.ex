# defined first: TemperCore below matches on %TemperCore.Ref{}, and a struct
# must exist before a pattern in the same file can name it
defmodule TemperCore.Ref do
  @moduledoc """
  A mutable Temper object: its class and a key into the calling process's
  heap. Two refs are equal exactly when they are the same object.
  """
  defstruct [:class, :id]
end

defmodule TemperCore do
  @moduledoc """
  Runtime support for Elixir translated from Temper by be-elixir.

  Elixir integers have no width; Temper's `Int` is 32-bit two's complement
  and wraps, and `Int64` is the same at 64 bits. Every arithmetic result the
  translator emits for those types goes through `int32/1` or `int64/1`.
  """

  @doc "Wraps an integer to signed 32 bits: `int32(2147483647 + 1)` is `-2147483648`."
  def int32(x) when is_integer(x) do
    <<v::signed-32>> = <<x::32>>
    v
  end

  @doc "Wraps an integer to signed 64 bits."
  def int64(x) when is_integer(x) do
    <<v::signed-64>> = <<x::64>>
    v
  end

  @doc """
  Temper's `Int` division: truncates toward zero, as `div/2` does, and wraps,
  so `-2147483648 / -1` is `-2147483648`. Dividing by zero bubbles.
  """
  def int32_div(_a, 0), do: raise(TemperCore.Bubble, "division by zero")
  def int32_div(a, b), do: int32(div(a, b))

  @doc "Temper's `Int` `%`: the sign of the dividend, as `rem/2`. By zero bubbles."
  def int32_rem(_a, 0), do: raise(TemperCore.Bubble, "remainder by zero")
  def int32_rem(a, b), do: rem(a, b)

  @doc "`Int64` division: truncates, wraps at 64 bits, bubbles on zero."
  def int64_div(_a, 0), do: raise(TemperCore.Bubble, "division by zero")
  def int64_div(a, b), do: int64(div(a, b))

  @doc "`Int64` `%`."
  def int64_rem(_a, 0), do: raise(TemperCore.Bubble, "remainder by zero")
  def int64_rem(a, b), do: rem(a, b)

  @doc "Temper's `Int.toString(radix)`: lower-case digits, as JavaScript writes them."
  def int_to_string(i, radix \\ 10), do: i |> Integer.to_string(radix) |> String.downcase()

  @doc """
  Runs `body` the first time `key` is seen on this node. A library's
  `__temper_init__/0` comes through here, so its top levels run once, however
  many processes and libraries ask for it. A process that arrives while
  another is running it waits for it to finish.
  """
  def init_once(key, body) do
    cond do
      # this process is already running it: a library that imports itself
      Process.get({:temper_init, key}) -> nil
      :ets.member(:temper_globals, {:temper_init, key}) -> nil
      true -> :global.trans({{:temper_init, key}, self()}, fn -> init_locked(key, body) end)
    end
  end

  # under the lock, another process may have finished it while this one waited
  defp init_locked(key, body) do
    unless :ets.member(:temper_globals, {:temper_init, key}) do
      Process.put({:temper_init, key}, true)
      # module state belongs to the node: an actor made by a top level must
      # not end with whichever process happened to run it
      TemperCore.Actor.supervised(body)
      :ets.insert(:temper_globals, {{:temper_init, key}, true})
    end

    nil
  end

  @doc "`Int.toFloat64()`."
  def int_to_float(i), do: i * 1.0

  # Float64 holds every integer of magnitude up to 2^53 - 1 exactly; core.temper
  # bounds both Int64 <-> Float64 conversions there, 2^53 itself included.
  @max_safe 0x1F_FFFF_FFFF_FFFF

  @doc "`Int64.toFloat64()`: bubbles outside plus or minus 2^53 - 1."
  def int64_to_float(i) do
    if i >= -@max_safe and i <= @max_safe, do: i * 1.0, else: raise(TemperCore.Bubble, "#{i} has no exact Float64")
  end

  @doc "`Float64.toInt32()`: truncates, and bubbles outside Int32 or for infinity and NaN."
  def float_to_int32(f) when is_float(f) do
    i = trunc(f)
    if i >= -2_147_483_648 and i <= 2_147_483_647, do: i, else: raise(TemperCore.Bubble, "#{f} is not an Int32")
  end

  def float_to_int32(f), do: raise(TemperCore.Bubble, "#{TemperCore.Float.to_string(f)} is not an Int32")

  @doc "`Float64.toInt64()`: truncates, and bubbles outside plus or minus 2^53 - 1."
  def float_to_int64(f) when is_float(f) do
    i = trunc(f)
    if i >= -@max_safe and i <= @max_safe, do: i, else: raise(TemperCore.Bubble, "#{f} is not a safe Int64")
  end

  def float_to_int64(f), do: raise(TemperCore.Bubble, "#{TemperCore.Float.to_string(f)} is not an Int64")

  @doc "`toInt32Unsafe()` / `toInt64Unsafe()`: truncates; infinity and NaN are 0, as in JavaScript."
  def float_trunc(f) when is_float(f), do: trunc(f)
  def float_trunc(_), do: 0

  @doc "`Int64.toInt32()`: bubbles outside Int32."
  def int64_to_int32(i) do
    if i >= -2_147_483_648 and i <= 2_147_483_647, do: i, else: raise(TemperCore.Bubble, "#{i} is not an Int32")
  end

  @doc "`core.ignore(x)`: evaluates x for its effects."
  def ignore(_x), do: nil

  @doc "`Listed.get(i)`: the element, or a bubble when i is outside the list."
  def list_get(list, i) when is_integer(i) and i >= 0 do
    case Enum.fetch(list, i) do
      {:ok, v} -> v
      :error -> raise(TemperCore.Bubble, "index #{i} outside a list of #{length(list)}")
    end
  end

  def list_get(list, i), do: raise(TemperCore.Bubble, "index #{i} outside a list of #{length(list)}")

  @doc "`Listed.getOr(i, fallback)`."
  def list_get_or(list, i, fallback) when is_integer(i) and i >= 0, do: Enum.at(list, i, fallback)
  def list_get_or(_list, _i, fallback), do: fallback

  @doc """
  The module a translated object's class became: a struct's `__struct__`,
  or a heap ref's `class`. Anything else is not a translated object.
  """
  def class_of(%TemperCore.Ref{class: class}), do: class
  def class_of(%TemperCore.Actor{class: class}), do: class
  def class_of(%{__struct__: class}), do: class
  def class_of(other), do: raise(ArgumentError, "#{inspect(other)} is not a Temper object")

  @doc """
  Calls a method on whatever class the object turns out to be, which is how
  a call through an interface-typed value reaches the right implementation.
  """
  def call(obj, method, args), do: apply(class_of(obj), method, [obj | args])

  @doc "`instanceof` for a translated class or interface."
  def is_a(%TemperCore.Ref{class: class}, type), do: type in class.__temper_supertypes__()
  def is_a(%TemperCore.Actor{class: class}, type), do: type in class.__temper_supertypes__()
  def is_a(%{__struct__: class}, type), do: function_exported?(class, :__temper_supertypes__, 0) and type in class.__temper_supertypes__()
  def is_a(_other, _type), do: false

  @doc "A cast that can fail: the value when it is a `type`, otherwise a bubble."
  def cast(value, type) do
    if is_a(value, type), do: value, else: raise(TemperCore.Bubble, "#{inspect(value)} is not a #{inspect(type)}")
  end

  @doc "A cast to a builtin type, checked with its guard."
  def cast_check(value, true), do: value
  def cast_check(value, false), do: raise(TemperCore.Bubble, "#{inspect(value)} is not that type")

  @doc "Three-way comparison, as Temper's `cmp`: -1, 0 or 1."
  def cmp(a, b) when a < b, do: -1
  def cmp(a, b) when a > b, do: 1
  def cmp(_a, _b), do: 0
end

defmodule TemperCore.Panic do
  @moduledoc "A Temper panic: not a bubble, and nothing in Temper catches it."
  defexception message: "panic"
end

defmodule TemperCore.Global do
  @moduledoc """
  Module-level Temper variables, shared by every process on the node.

  A Temper module's top-level `let` and `var` are read and written by the
  module's functions, and Elixir functions see no variables but their own,
  so module values live outside the functions. A library's top level runs
  once per node (`TemperCore.init_once/2`), and every process sees what it
  set:

  - **A value that can be shared** (a number, string, list, map, `@imu`
    struct or actor) lives in an ETS table, so every process reads the
    same one and sees every write. A shared `var` that two processes
    read and write can still lose an update between the read and the
    write. A counter or registry shared across processes should be an
    `@actor`.
  - **A mutable object that is not an actor** cannot be shared: its ref
    only means something in the heap of the process that made it. Each
    process gets its own copy, taken from a snapshot when it first reads
    the value, and from then on its copy is its own.

  Reading a value that was never set raises, rather than answering nil.
  """
  @table :temper_globals

  def put(name, value) when is_atom(name) do
    if TemperCore.Actor.sendable?(value) do
      :ets.insert(@table, {name, {:shared, value}})
      Process.delete({__MODULE__, name})
    else
      Process.put({__MODULE__, name}, value)
      :ets.insert(@table, {name, {:snapshot, TemperCore.Heap.export(value)}})
    end

    value
  end

  def get(name) when is_atom(name) do
    case Process.get({__MODULE__, name}, __MODULE__) do
      __MODULE__ ->
        case :ets.lookup(@table, name) do
          [{_, {:shared, value}}] ->
            value

          [{_, {:snapshot, snapshot}}] ->
            value = TemperCore.Heap.import(snapshot)
            Process.put({__MODULE__, name}, value)
            value

          [] ->
            raise ArgumentError, "module-level #{inspect(name)} read before it was set"
        end

      value ->
        value
    end
  end
end

defmodule TemperCore.Bubble do
  @moduledoc "A Temper bubble that nothing caught."
  defexception message: "bubble"
end


defmodule TemperCore.Heap do
  @moduledoc """
  Where mutable Temper objects keep their fields.

  The BEAM has no mutable heap objects, and Temper objects are aliased: a
  write through one variable is seen through every other that holds the
  same object. A struct copied on write would give each holder its own copy.
  So an object with mutable fields is a `TemperCore.Ref`, and its fields live
  in the process dictionary under that ref.

  Two consequences, and what to do about each:

  - **Nothing frees an object on its own.** A heap dies with its process,
    so a process per request or per job needs nothing more. A process that
    lives on, like a GenServer holding Temper objects, calls `collect/1`
    between calls into Temper code, passing what it still holds.
  - **An object is only visible in the process that made it.** To hand
    objects to another process, `export/1` them, send the result, and
    `import/1` it there. That copies them, the way a BEAM message copies
    everything.
  """
  alias TemperCore.Ref

  # the nursery's bookkeeping, keyed apart from objects ({TemperCore.Heap, id})
  # so that size/0 and collect/1 never mistake it for one
  @depth {TemperCore.Heap.Nursery, :depth}
  @nursery {TemperCore.Heap.Nursery, :young}
  @remembered {TemperCore.Heap.Nursery, :remembered}

  @doc "Makes an object of `class` with the given fields, and returns its ref."
  def new(class, fields) when is_atom(class) and is_map(fields) do
    ref = %Ref{class: class, id: make_ref()}
    Process.put(key(ref), fields)

    case Process.get(@nursery) do
      nil -> :ok
      young -> Process.put(@nursery, MapSet.put(young, ref.id))
    end

    ref
  end

  @doc "Reads a field. A field the object does not have raises KeyError."
  def get(%TemperCore.Actor{} = actor, field), do: get(TemperCore.Actor.fields(actor), field)
  def get(%Ref{} = ref, field), do: Map.fetch!(fields!(ref), field)

  @doc "Writes a field the object already has, and returns the value written."
  def put(%TemperCore.Actor{} = actor, field, value), do: put(TemperCore.Actor.fields(actor), field, value)

  def put(%Ref{id: id} = ref, field, value) do
    Process.put(key(ref), %{fields!(ref) | field => value})

    # the write barrier: an older object written during a call may now point
    # at a young one, so its fields are roots for the minor collection
    case Process.get(@nursery) do
      nil -> :ok
      young -> if not MapSet.member?(young, id), do: Process.put(@remembered, MapSet.put(Process.get(@remembered), id))
    end

    value
  end

  defp fields!(ref) do
    case Process.get(key(ref)) do
      nil -> raise ArgumentError, "#{inspect(ref)} is not an object in this process"
      fields -> fields
    end
  end

  defp key(%Ref{id: id}), do: {__MODULE__, id}

  @doc """
  Runs a call into a library, and frees what it left behind.

  An exported function's body runs through this. Calls nest (Temper code
  calling exported functions, its own library's or another's); only the
  outermost one collects. Objects made during that call are young. When it
  returns or raises, the young objects nothing reaches are freed. "Reaches"
  means from the result, the rest of the process dictionary (Temper's
  globals, the async queue), or an older object written during the call.
  Older objects are never touched, so whatever the caller still holds from
  earlier calls stays alive. Those, and objects from code that never went
  through an entry, are left to `collect/1`, or to the process exiting.
  """
  def entry(fun) do
    case Process.get(@depth, 0) do
      0 ->
        Process.put(@depth, 1)
        Process.put(@nursery, MapSet.new())
        Process.put(@remembered, MapSet.new())

        try do
          result = fun.()
          minor([result])
          result
        catch
          kind, reason ->
            minor([])
            :erlang.raise(kind, reason, __STACKTRACE__)
        after
          Process.delete(@depth)
          Process.delete(@nursery)
          Process.delete(@remembered)
        end

      depth ->
        Process.put(@depth, depth + 1)

        try do
          fun.()
        after
          Process.put(@depth, depth)
        end
    end
  end

  # Frees the young objects that nothing reaches. Marking stops at older
  # objects: they are alive, and any of their fields that could reach a young
  # object were remembered by `put`.
  defp minor(extra) do
    young = Process.get(@nursery)

    if MapSet.size(young) == 0 do
      0
    else
      remembered = Enum.map(Process.get(@remembered), &Process.get({__MODULE__, &1}))

      others =
        for {k, v} <- Process.get(), not object?({k, v}), k not in [@depth, @nursery, @remembered], do: v

      live = mark_young([extra, remembered | others], young, MapSet.new())
      dead = MapSet.difference(young, live)
      Enum.each(dead, &Process.delete({__MODULE__, &1}))
      MapSet.size(dead)
    end
  end

  defp mark_young([], _young, live), do: live

  defp mark_young([%Ref{id: id} | rest], young, live) do
    if MapSet.member?(young, id) and not MapSet.member?(live, id) do
      mark_young([Process.get({__MODULE__, id}) | rest], young, MapSet.put(live, id))
    else
      mark_young(rest, young, live)
    end
  end

  defp mark_young([[] | rest], young, live), do: mark_young(rest, young, live)
  defp mark_young([[h | t] | rest], young, live), do: mark_young([h, t | rest], young, live)
  defp mark_young([x | rest], young, live) when is_tuple(x), do: mark_young([Tuple.to_list(x) | rest], young, live)

  defp mark_young([x | rest], young, live) when is_map(x),
    do: mark_young([Map.keys(x), Map.values(x) | rest], young, live)

  defp mark_young([x | rest], young, live) when is_function(x) do
    {:env, env} = :erlang.fun_info(x, :env)
    mark_young([env | rest], young, live)
  end

  defp mark_young([_ | rest], young, live), do: mark_young(rest, young, live)

  @doc "How many objects this process's heap holds."
  def size, do: Enum.count(Process.get(), &object?/1)

  @doc """
  Frees every object nothing can reach, and returns how many it freed.

  An object is reachable from `roots`, from everything else in the process
  dictionary (Temper's globals, the async queue, anything the host keeps
  there), and from any object, list, tuple, map or closure those reach.
  Closures are traced through `:erlang.fun_info(f, :env)`.

  The stack is not visible from here, so this is only safe between calls
  into Temper code: a local in a Temper function that is still running is
  not a root. Pass whatever the caller still holds, such as a GenServer's
  state.
  """
  def collect(roots \\ []) do
    {objects, others} = Enum.split_with(Process.get(), &object?/1)
    live = mark([roots | Enum.map(others, &elem(&1, 1))], MapSet.new())

    Enum.count(objects, fn {{__MODULE__, id} = k, _} ->
      if MapSet.member?(live, id), do: false, else: Process.delete(k) && true
    end)
  end

  @doc """
  `term`, with a copy of every object it reaches, ready to send to another
  process. `import/1` there gives the term back, with its objects living in
  that process.

  The copies keep their ids, which are unique across processes and
  nodes. That is why a ref inside an exported closure is still good after
  import, with no need to rewrite it. Importing the same object again
  replaces that process's copy with the newer snapshot. Writes made after
  the export are not shared: like any message, this is a copy.
  """
  def export(term) do
    ids = mark([term], MapSet.new())
    {:temper_export, term, Map.new(ids, fn id -> {id, Process.get({__MODULE__, id})} end)}
  end

  @doc "Puts an `export/1`ed term's objects into this process's heap and returns the term."
  def import({:temper_export, term, objects}) do
    Enum.each(objects, fn {id, fields} -> Process.put({__MODULE__, id}, fields) end)
    term
  end

  defp object?({{__MODULE__, _}, _}), do: true
  defp object?(_), do: false

  # Ids of the objects reachable from a stack of terms, walked without
  # recursion, so a long list or a deep object graph cannot overflow.
  defp mark([], live), do: live

  defp mark([%Ref{id: id} | rest], live) do
    if MapSet.member?(live, id) do
      mark(rest, live)
    else
      mark([Process.get({__MODULE__, id}) | rest], MapSet.put(live, id))
    end
  end

  defp mark([[] | rest], live), do: mark(rest, live)
  defp mark([[h | t] | rest], live), do: mark([h, t | rest], live)
  defp mark([x | rest], live) when is_tuple(x), do: mark([Tuple.to_list(x) | rest], live)
  defp mark([x | rest], live) when is_map(x), do: mark([Map.keys(x), Map.values(x) | rest], live)

  defp mark([x | rest], live) when is_function(x) do
    {:env, env} = :erlang.fun_info(x, :env)
    mark([env | rest], live)
  end

  defp mark([_ | rest], live), do: mark(rest, live)
end

defmodule TemperCore.Temporal do
  @moduledoc "What std/temporal needs from the host: the clock."

  @doc "`Date.today()`: today's UTC date, made by std's own `Date` constructor."
  def today(date_module) do
    %Date{year: y, month: m, day: d} = Date.utc_today()
    date_module.new(y, m, d)
  end
end
