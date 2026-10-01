defmodule TemperCore.List do
  @moduledoc """
  Temper's `Listed`, `List` and `ListBuilder`.

  A `List` is an Elixir list. A `ListBuilder` is mutable and aliased, so it is
  a heap object whose `:items` is an Elixir list; every alias sees every add.
  A `ListBuilder` is also a `Listed`, so every read here takes either.

  Where Temper's core says "panics" (`add` out of range, `removeLast` on an
  empty builder, `set` out of bounds) this raises `TemperCore.Panic`; where it
  bubbles (`get` out of range) it raises `TemperCore.Bubble`.
  """
  alias TemperCore.{Heap, Ref}

  @class :list_builder

  def builder, do: Heap.new(@class, %{items: []})
  def builder(items) when is_list(items), do: Heap.new(@class, %{items: items})

  @doc "The elements of a List or a ListBuilder, as an Elixir list."
  def items(%Ref{} = lb), do: Heap.get(lb, :items)
  def items(list) when is_list(list), do: list

  defp store(lb, items), do: Heap.put(lb, :items, items)

  # -- Listed ----------------------------------------------------------------

  def length(x), do: Kernel.length(items(x))
  def is_empty(x), do: items(x) == []
  def get(x, i), do: TemperCore.list_get(items(x), i)
  def get_or(x, i, fallback), do: TemperCore.list_get_or(items(x), i, fallback)
  def to_list(x), do: items(x)
  def to_builder(x), do: builder(items(x))
  def map(x, f), do: Enum.map(items(x), f)
  def filter(x, f), do: Enum.filter(items(x), f)
  def join(x, separator, f), do: Enum.map_join(items(x), separator, f)
  def for_each(x, f), do: Enum.each(items(x), f) && nil

  @doc "Elements from `b` inclusive to `e` exclusive, both clamped to the list."
  def slice(x, b, e) do
    list = items(x)
    n = Kernel.length(list)
    b = b |> max(0) |> min(n)
    e = e |> max(b) |> min(n)
    Enum.slice(list, b, e - b)
  end

  @doc "A stable sort by a three-way comparison."
  def sorted(x, compare), do: Enum.sort(items(x), fn a, b -> compare.(a, b) <= 0 end)

  @doc "`reduce` starts from the first element, so an empty list bubbles."
  def reduce(x, f) do
    case items(x) do
      [] -> raise TemperCore.Bubble, "reduce of an empty list"
      [first | rest] -> Enum.reduce(rest, first, fn el, acc -> f.(acc, el) end)
    end
  end

  def reduce_from(x, initial, f), do: Enum.reduce(items(x), initial, fn el, acc -> f.(acc, el) end)

  # -- ListBuilder -----------------------------------------------------------

  def add(lb, value, at \\ nil) do
    list = items(lb)
    n = Kernel.length(list)
    at = if at == nil, do: n, else: at
    if at < 0 or at > n, do: raise(TemperCore.Panic, "add at #{at} outside 0..#{n}")
    store(lb, List.insert_at(list, at, value))
    nil
  end

  def add_all(lb, values, at \\ nil) do
    list = items(lb)
    n = Kernel.length(list)
    at = if at == nil, do: n, else: at
    if at < 0 or at > n, do: raise(TemperCore.Panic, "addAll at #{at} outside 0..#{n}")
    {front, back} = Enum.split(list, at)
    store(lb, front ++ items(values) ++ back)
    nil
  end

  def clear(lb), do: store(lb, []) && nil

  def remove_last(lb) do
    case items(lb) do
      [] ->
        raise TemperCore.Panic, "removeLast of an empty list builder"

      list ->
        {rest, [last]} = Enum.split(list, -1)
        store(lb, rest)
        last
    end
  end

  def reverse(lb), do: store(lb, Enum.reverse(items(lb))) && nil

  def set(lb, i, value) do
    list = items(lb)
    if i < 0 or i >= Kernel.length(list), do: raise(TemperCore.Panic, "set at #{i} outside the list")
    store(lb, List.replace_at(list, i, value))
    nil
  end

  def sort(lb, compare), do: store(lb, sorted(lb, compare)) && nil

  @doc "Removes `count` items at `index` (clamped), puts `new_values` there, and returns what it removed."
  def splice(lb, index \\ nil, count \\ nil, new_values \\ nil) do
    list = items(lb)
    n = Kernel.length(list)
    index = (index || 0) |> max(0) |> min(n)
    count = (count || n) |> max(0) |> min(n - index)
    {front, rest} = Enum.split(list, index)
    {removed, back} = Enum.split(rest, count)
    store(lb, front ++ items(new_values || []) ++ back)
    removed
  end
end
