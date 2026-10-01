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
end

defmodule TemperCore.Bubble do
  @moduledoc "A Temper bubble that nothing caught."
  defexception message: "bubble"
end

defmodule TemperCore.Ref do
  @moduledoc """
  A mutable Temper object: its class and a key into the calling process's
  heap. Two refs are equal exactly when they are the same object.
  """
  defstruct [:class, :id]
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
