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
  def class_of(%{__struct__: class}), do: class
  def class_of(other), do: raise(ArgumentError, "#{inspect(other)} is not a Temper object")

  @doc """
  Calls a method on whatever class the object turns out to be, which is how
  a call through an interface-typed value reaches the right implementation.
  """
  def call(obj, method, args), do: apply(class_of(obj), method, [obj | args])

  @doc "`instanceof` for a translated class or interface."
  def is_a(%TemperCore.Ref{class: class}, type), do: type in class.__temper_supertypes__()
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
  Module-level Temper variables.

  A Temper module's top-level `let` and `var` are read and written by the
  module's functions, and Elixir functions see no variables but their own. So
  module-level values live in the process dictionary, keyed by name. Reading
  one that was never set raises rather than answering nil.
  """

  def put(name, value) when is_atom(name) do
    Process.put({__MODULE__, name}, value)
    value
  end

  def get(name) when is_atom(name) do
    case Process.get({__MODULE__, name}, __MODULE__) do
      __MODULE__ -> raise ArgumentError, "module-level #{inspect(name)} read before it was set"
      value -> value
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

  Two costs, both by design for now: an object is never freed, and it is
  only visible to the process that made it.
  """
  alias TemperCore.Ref

  @doc "Makes an object of `class` with the given fields, and returns its ref."
  def new(class, fields) when is_atom(class) and is_map(fields) do
    ref = %Ref{class: class, id: make_ref()}
    Process.put(key(ref), fields)
    ref
  end

  @doc "Reads a field. A field the object does not have raises KeyError."
  def get(%Ref{} = ref, field), do: Map.fetch!(fields!(ref), field)

  @doc "Writes a field the object already has, and returns the value written."
  def put(%Ref{} = ref, field, value) do
    Process.put(key(ref), %{fields!(ref) | field => value})
    value
  end

  defp fields!(ref) do
    case Process.get(key(ref)) do
      nil -> raise ArgumentError, "#{inspect(ref)} is not an object in this process"
      fields -> fields
    end
  end

  defp key(%Ref{id: id}), do: {__MODULE__, id}
end
