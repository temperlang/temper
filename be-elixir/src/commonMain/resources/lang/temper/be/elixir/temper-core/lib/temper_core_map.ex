defmodule TemperCore.Pair do
  @moduledoc "Temper's `Pair`: a struct, so `pair.key` reads like any other object's property."
  defstruct [:key, :value]

  @type t(key, value) :: %__MODULE__{key: key, value: value}
  def new(key, value), do: %__MODULE__{key: key, value: value}
  def get_key(pair), do: pair.key
  def get_value(pair), do: pair.value
  def __temper_supertypes__, do: [__MODULE__]
end

defmodule TemperCore.Map do
  @moduledoc """
  Temper's `Map`, `MapBuilder` and `Mapped`.

  Temper maps keep insertion order, and Elixir maps do not: a map of up to 32
  keys iterates in key order, a larger one in hash order. So a Temper map is
  its keys, in the order they arrived, beside an Elixir map. A `Map` is an
  immutable struct of those two; a `MapBuilder` is a heap object holding
  them, so aliases share writes. Every read takes either.
  """
  alias TemperCore.{Heap, Ref, Pair}

  defstruct keys: [], map: %{}

  @typedoc "A Temper `Map`: its keys in the order they arrived, beside an Elixir map."
  @type t(key, value) :: %__MODULE__{keys: [key], map: %{optional(key) => value}}

  @class :map_builder

  # -- making them -------------------------------------------------------------

  @doc "`new Map(entries)`: later entries win, but a key keeps its first position."
  def new(entries) do
    {keys, map} =
      entries
      |> TemperCore.List.items()
      |> Enum.reduce({[], %{}}, fn %Pair{key: k, value: v}, {keys, map} ->
        if Map.has_key?(map, k), do: {keys, Map.put(map, k, v)}, else: {[k | keys], Map.put(map, k, v)}
      end)

    %__MODULE__{keys: Enum.reverse(keys), map: map}
  end

  def builder, do: Heap.new(@class, %{keys: [], map: %{}})

  defp parts(%__MODULE__{keys: keys, map: map}), do: {keys, map}
  defp parts(%Ref{} = mb), do: {Heap.get(mb, :keys), Heap.get(mb, :map)}

  defp store(mb, keys, map) do
    Heap.put(mb, :keys, keys)
    Heap.put(mb, :map, map)
    nil
  end

  # -- Mapped ------------------------------------------------------------------

  def length(x), do: x |> parts() |> elem(1) |> map_size()

  @doc "The value for `key`, or a bubble when there is none."
  def get(x, key) do
    case Map.fetch(elem(parts(x), 1), key) do
      {:ok, v} -> v
      :error -> raise TemperCore.Bubble, "no key #{inspect(key)}"
    end
  end

  def get_or(x, key, fallback), do: Map.get(elem(parts(x), 1), key, fallback)
  def has(x, key), do: Map.has_key?(elem(parts(x), 1), key)
  # keys, values and entries come back as Temper Lists (TemperCore.Vec)
  def keys(x), do: TemperCore.Vec.new(elem(parts(x), 0))

  def values(x) do
    {keys, map} = parts(x)
    TemperCore.Vec.new(Enum.map(keys, &Map.fetch!(map, &1)))
  end

  def to_list_with(x, f), do: TemperCore.Vec.new(entries_with(x, f))
  def to_list(x), do: to_list_with(x, &Pair.new/2)
  def to_list_builder(x), do: TemperCore.List.builder(entries_with(x, &Pair.new/2))
  def to_list_builder_with(x, f), do: TemperCore.List.builder(entries_with(x, f))

  defp entries_with(x, f) do
    {keys, map} = parts(x)
    Enum.map(keys, fn k -> f.(k, Map.fetch!(map, k)) end)
  end

  def for_each(x, f), do: to_list_with(x, f) && nil

  def to_map(%__MODULE__{} = m), do: m

  def to_map(x) do
    {keys, map} = parts(x)
    %__MODULE__{keys: keys, map: map}
  end

  @doc "A builder is always a fresh copy, so the two can change independently."
  def to_builder(x) do
    {keys, map} = parts(x)
    Heap.new(@class, %{keys: keys, map: map})
  end

  # -- MapBuilder --------------------------------------------------------------

  def set(mb, key, value) do
    {keys, map} = parts(mb)
    keys = if Map.has_key?(map, key), do: keys, else: keys ++ [key]
    store(mb, keys, Map.put(map, key, value))
  end

  @doc "Removes `key` and returns its value; bubbles when there is no such key."
  def remove(mb, key) do
    {keys, map} = parts(mb)

    case Map.fetch(map, key) do
      {:ok, v} ->
        store(mb, List.delete(keys, key), Map.delete(map, key))
        v

      :error ->
        raise TemperCore.Bubble, "no key #{inspect(key)} to remove"
    end
  end

  def clear(mb), do: store(mb, [], %{})
end

defmodule TemperCore.Deque do
  @moduledoc "Temper's `Deque`: an Erlang `:queue` on the heap. `removeFirst` on empty panics."
  alias TemperCore.Heap

  def new, do: Heap.new(:deque, %{q: :queue.new()})
  def add(d, x), do: Heap.put(d, :q, :queue.in(x, Heap.get(d, :q))) && nil
  def is_empty(d), do: :queue.is_empty(Heap.get(d, :q))

  def remove_first(d) do
    case :queue.out(Heap.get(d, :q)) do
      {{:value, x}, rest} ->
        Heap.put(d, :q, rest)
        x

      {:empty, _} ->
        raise TemperCore.Panic, "removeFirst of an empty deque"
    end
  end
end

defmodule TemperCore.DenseBitVector do
  @moduledoc """
  Temper's `DenseBitVector`: the set bits, in a map on the heap. Reading a bit
  past the end answers false, so a map of the set ones is the whole state.
  """
  alias TemperCore.Heap

  def new(_capacity), do: Heap.new(:dense_bit_vector, %{bits: %{}})
  def get(v, i), do: Map.get(Heap.get(v, :bits), i, false)

  def set(v, i, true), do: Heap.put(v, :bits, Map.put(Heap.get(v, :bits), i, true)) && nil
  def set(v, i, false), do: Heap.put(v, :bits, Map.delete(Heap.get(v, :bits), i)) && nil
end
