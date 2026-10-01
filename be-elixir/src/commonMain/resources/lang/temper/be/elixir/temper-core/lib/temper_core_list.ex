defmodule TemperCore.Vec do
  @moduledoc """
  Temper's immutable `List`: a tuple in a struct, so `xs[i]` is `elem/2` and
  `xs.length` is `tuple_size/1`, both constant time. As an Elixir list, an
  indexed loop over 16,000 items took 282 ms, because indexing and length
  both walk the list.

  It is `Enumerable`, so Elixir code can use `Enum` on a list a Temper
  library returns. Every Temper list operation also accepts a plain Elixir
  list as input.
  """
  defstruct t: {}

  @typedoc "A Temper `List` of `elem`. The tuple's elements are `elem`; a type cannot say so."
  @type t(_elem) :: %__MODULE__{t: tuple()}

  @doc "A Temper List of these elements."
  @spec new([elem]) :: t(elem) when elem: term()
  def new(items) when is_list(items), do: %__MODULE__{t: List.to_tuple(items)}
end

defimpl Enumerable, for: TemperCore.Vec do
  def count(%{t: t}), do: {:ok, tuple_size(t)}
  def member?(_vec, _value), do: {:error, __MODULE__}

  def slice(%{t: t}) do
    {:ok, tuple_size(t),
     fn start, len, step -> for i <- start..(start + (len - 1) * step)//step, do: elem(t, i) end}
  end

  def reduce(%{t: t}, acc, fun), do: Enumerable.List.reduce(Tuple.to_list(t), acc, fun)
end

defimpl Inspect, for: TemperCore.Vec do
  def inspect(%{t: t}, opts),
    do:
      Inspect.Algebra.concat([
        "#TemperCore.Vec<",
        Inspect.Algebra.to_doc(Tuple.to_list(t), opts),
        ">"
      ])
end

defmodule TemperCore.List do
  @moduledoc """
  Temper's `Listed`, `List` and `ListBuilder`.

  A `List` is a `TemperCore.Vec`, or a plain Elixir list from Elixir code.
  A `ListBuilder` is mutable and aliased, so it is
  a heap object; every alias sees every add. It holds an Erlang `:array`,
  not a list: appending to a list copies it, which made building one item
  at a time quadratic (16,000 adds took 1.2 s). With an `:array`, `add`,
  `get`, `set` and `removeLast` cost `O(log n)` and `length` is constant.
  A `ListBuilder` is also a `Listed`, so every read here takes either.

  Where Temper's core says "panics" (`add` out of range, `removeLast` on an
  empty builder, `set` out of bounds) this raises `TemperCore.Panic`; where it
  bubbles (`get` out of range) it raises `TemperCore.Bubble`.
  """
  alias TemperCore.{Heap, Ref, Vec}

  @class :list_builder

  @typedoc "A Temper `ListBuilder`: a heap object holding an Erlang `:array`."
  @type builder :: Ref.t()

  @typedoc "A Temper `List` as an argument: a Vec, or a plain list from Elixir code."
  @type list_in(elem) :: Vec.t(elem) | [elem]

  @typedoc "A Temper `Listed`: a List or a ListBuilder. A builder's elements are untyped."
  @type listed(elem) :: list_in(elem) | builder()

  @spec builder() :: builder()
  def builder, do: Heap.new(@class, %{arr: :array.new(default: nil)})

  @spec builder([term()]) :: builder()
  def builder(items) when is_list(items),
    do: Heap.new(@class, %{arr: :array.from_list(items, nil)})

  @doc "The elements of a List or a ListBuilder, as an Elixir list."
  @spec items(listed(elem)) :: [elem] when elem: term()
  def items(%Ref{} = lb), do: :array.to_list(arr(lb))
  def items(%Vec{t: t}), do: Tuple.to_list(t)
  def items(list) when is_list(list), do: list

  @doc "`is List`: a Vec, or a plain list from Elixir code."
  @spec list?(term()) :: boolean()
  def list?(x), do: is_list(x) or is_struct(x, Vec)

  @doc "`is Listed`: a List or a ListBuilder."
  @spec listed?(term()) :: boolean()
  def listed?(x), do: list?(x) or match?(%Ref{class: @class}, x)

  defp arr(lb), do: Heap.get(lb, :arr)
  defp put_arr(lb, a), do: Heap.put(lb, :arr, a)
  # returns nil, as the builder methods that end in it do; `&& nil` on
  # Heap.put's untyped result left Dialyzer seeing a possible `false`
  defp store(lb, items) do
    put_arr(lb, :array.from_list(items, nil))
    nil
  end

  # -- Listed ----------------------------------------------------------------

  @spec length(listed(term())) :: non_neg_integer()
  def length(%Vec{t: t}), do: tuple_size(t)
  def length(%Ref{} = lb), do: :array.size(arr(lb))
  def length(list), do: Kernel.length(list)
  @spec is_empty(listed(term())) :: boolean()
  def is_empty(x), do: __MODULE__.length(x) == 0

  @spec get(listed(elem), integer()) :: elem when elem: term()
  def get(%Ref{} = lb, i) do
    a = arr(lb)
    n = :array.size(a)

    if is_integer(i) and i >= 0 and i < n,
      do: :array.get(i, a),
      else: raise(TemperCore.Bubble, "index #{i} outside a list of #{n}")
  end

  def get(%Vec{t: t}, i) do
    if is_integer(i) and i >= 0 and i < tuple_size(t),
      do: elem(t, i),
      else: raise(TemperCore.Bubble, "index #{i} outside a list of #{tuple_size(t)}")
  end

  def get(list, i), do: TemperCore.list_get(list, i)

  @spec get_or(listed(elem), integer(), fallback) :: elem | fallback
        when elem: term(), fallback: term()
  def get_or(%Ref{} = lb, i, fallback) do
    a = arr(lb)
    if is_integer(i) and i >= 0 and i < :array.size(a), do: :array.get(i, a), else: fallback
  end

  def get_or(%Vec{t: t}, i, fallback) do
    if is_integer(i) and i >= 0 and i < tuple_size(t), do: elem(t, i), else: fallback
  end

  def get_or(list, i, fallback), do: TemperCore.list_get_or(list, i, fallback)
  @spec to_list(listed(elem)) :: Vec.t(elem) when elem: term()
  def to_list(%Vec{} = v), do: v
  def to_list(x), do: Vec.new(items(x))
  @spec to_builder(listed(term())) :: builder()
  def to_builder(x), do: builder(items(x))
  @spec map(listed(elem), (elem -> result)) :: Vec.t(result) when elem: term(), result: term()
  def map(x, f), do: Vec.new(Enum.map(items(x), f))
  @spec filter(listed(elem), (elem -> boolean())) :: Vec.t(elem) when elem: term()
  def filter(x, f), do: Vec.new(Enum.filter(items(x), f))
  @spec join(listed(elem), String.t(), (elem -> String.t())) :: String.t() when elem: term()
  def join(x, separator, f), do: Enum.map_join(items(x), separator, f)
  @spec for_each(listed(elem), (elem -> term())) :: nil when elem: term()
  def for_each(x, f) do
    Enum.each(items(x), f)
    nil
  end

  @doc "Elements from `b` inclusive to `e` exclusive, both clamped to the list."
  @spec slice(listed(elem), integer(), integer()) :: Vec.t(elem) when elem: term()
  def slice(x, b, e) do
    list = items(x)
    n = Kernel.length(list)
    b = b |> max(0) |> min(n)
    e = e |> max(b) |> min(n)
    Vec.new(Enum.slice(list, b, e - b))
  end

  @doc "A stable sort by a three-way comparison."
  @spec sorted(listed(elem), (elem, elem -> integer())) :: Vec.t(elem) when elem: term()
  def sorted(x, compare), do: Vec.new(sort_items(x, compare))
  defp sort_items(x, compare), do: Enum.sort(items(x), fn a, b -> compare.(a, b) <= 0 end)

  @doc "`reduce` starts from the first element, so an empty list bubbles."
  @spec reduce(listed(elem), (elem, elem -> elem)) :: elem when elem: term()
  def reduce(x, f) do
    case items(x) do
      [] -> raise TemperCore.Bubble, "reduce of an empty list"
      [first | rest] -> Enum.reduce(rest, first, fn el, acc -> f.(acc, el) end)
    end
  end

  @spec reduce_from(listed(elem), acc, (acc, elem -> acc)) :: acc when elem: term(), acc: term()
  def reduce_from(x, initial, f),
    do: Enum.reduce(items(x), initial, fn el, acc -> f.(acc, el) end)

  # -- ListBuilder -----------------------------------------------------------

  @spec add(builder(), term()) :: nil
  @spec add(builder(), term(), integer() | nil) :: nil
  def add(lb, value, at \\ nil) do
    a = arr(lb)
    n = :array.size(a)
    at = if at == nil, do: n, else: at
    if at < 0 or at > n, do: raise(TemperCore.Panic, "add at #{at} outside 0..#{n}")

    # the common case, appending, does not touch what is there
    if at == n,
      do: put_arr(lb, :array.set(n, value, a)),
      else: store(lb, List.insert_at(:array.to_list(a), at, value))

    nil
  end

  @spec add_all(builder(), listed(term())) :: nil
  @spec add_all(builder(), listed(term()), integer() | nil) :: nil
  def add_all(lb, values, at \\ nil) do
    a = arr(lb)
    n = :array.size(a)
    at = if at == nil, do: n, else: at
    if at < 0 or at > n, do: raise(TemperCore.Panic, "addAll at #{at} outside 0..#{n}")

    if at == n do
      put_arr(
        lb,
        Enum.reduce(items(values), a, fn v, acc -> :array.set(:array.size(acc), v, acc) end)
      )
    else
      {front, back} = Enum.split(:array.to_list(a), at)
      store(lb, front ++ items(values) ++ back)
    end

    nil
  end

  @spec clear(builder()) :: nil
  def clear(lb), do: store(lb, [])

  @spec remove_last(builder()) :: term()
  def remove_last(lb) do
    a = arr(lb)

    case :array.size(a) do
      0 ->
        raise TemperCore.Panic, "removeLast of an empty list builder"

      n ->
        last = :array.get(n - 1, a)
        put_arr(lb, :array.resize(n - 1, a))
        last
    end
  end

  @spec reverse(builder()) :: nil
  def reverse(lb), do: store(lb, Enum.reverse(items(lb)))

  @spec set(builder(), integer(), term()) :: nil
  def set(lb, i, value) do
    a = arr(lb)
    if i < 0 or i >= :array.size(a), do: raise(TemperCore.Panic, "set at #{i} outside the list")
    put_arr(lb, :array.set(i, value, a))
    nil
  end

  @spec sort(builder(), (term(), term() -> integer())) :: nil
  def sort(lb, compare), do: store(lb, sort_items(lb, compare))

  @doc "Removes `count` items at `index` (clamped), puts `new_values` there, and returns what it removed."
  @spec splice(builder()) :: Vec.t(term())
  @spec splice(builder(), integer() | nil) :: Vec.t(term())
  @spec splice(builder(), integer() | nil, integer() | nil) :: Vec.t(term())
  @spec splice(builder(), integer() | nil, integer() | nil, listed(term()) | nil) :: Vec.t(term())
  def splice(lb, index \\ nil, count \\ nil, new_values \\ nil) do
    list = items(lb)
    n = Kernel.length(list)
    index = (index || 0) |> max(0) |> min(n)
    count = (count || n) |> max(0) |> min(n - index)
    {front, rest} = Enum.split(list, index)
    {removed, back} = Enum.split(rest, count)
    store(lb, front ++ items(new_values || []) ++ back)
    Vec.new(removed)
  end
end
