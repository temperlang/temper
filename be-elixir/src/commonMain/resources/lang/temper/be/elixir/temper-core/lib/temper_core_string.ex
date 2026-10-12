defmodule TemperCore.String do
  @moduledoc """
  Temper's `String`, over Elixir's UTF-8 binaries.

  A Temper `StringIndex` is a byte offset into the UTF-8, which is what an
  Elixir binary already is, so an index is an integer and `String.begin` is
  0. `next` and `prev` step over whole code points. `StringIndex.none` is -1.

  Counting is in code points, never graphemes: `String.length/1` counts
  graphemes, and Temper's `countBetween` does not.
  """

  @spec begin() :: 0
  def begin, do: 0
  @spec none() :: -1
  def none, do: -1
  @spec end_of(String.t()) :: non_neg_integer()
  def end_of(s), do: byte_size(s)
  @spec is_empty(String.t()) :: boolean()
  def is_empty(s), do: s == ""
  @spec has_index(String.t(), integer()) :: boolean()
  def has_index(s, i), do: i >= 0 and i < byte_size(s)

  @doc "The code point at byte offset `i`; panics if there is none."
  @spec get(String.t(), integer()) :: char()
  def get(s, i) do
    case s do
      <<_::binary-size(i), cp::utf8, _::binary>> when i >= 0 -> cp
      _ -> raise TemperCore.Panic, "no code point at #{i} in a string of #{byte_size(s)} bytes"
    end
  end

  @doc "The index after the code point at `i`, or `end` if there is none."
  @spec next(String.t(), integer()) :: non_neg_integer()
  def next(s, i) when i >= 0 do
    # one match, no byte_size or :binary.at: this runs once per code point
    case s do
      <<_::binary-size(i), b, _::binary>> when b < 0x80 -> i + 1
      <<_::binary-size(i), b, _::binary>> when b >= 0xF0 -> i + 4
      <<_::binary-size(i), b, _::binary>> when b >= 0xE0 -> i + 3
      <<_::binary-size(i), _, _::binary>> -> i + 2
      _ -> byte_size(s)
    end
  end

  def next(_s, _i), do: 0

  @doc "The index of the code point before `i`, or `begin` if there is none."
  @spec prev(String.t(), integer()) :: non_neg_integer()
  def prev(s, i) do
    i = min(i, byte_size(s))
    if i <= 0, do: 0, else: back(s, i - 1)
  end

  # step back over UTF-8 continuation bytes (10xxxxxx)
  defp back(_s, 0), do: 0
  defp back(s, i), do: if(Bitwise.band(:binary.at(s, i), 0xC0) == 0x80, do: back(s, i - 1), else: i)


  @spec step(String.t(), integer(), integer()) :: integer()
  def step(s, i, by) when by >= 0, do: Enum.reduce(1..by//1, i, fn _, acc -> next(s, acc) end)
  def step(s, i, by), do: Enum.reduce(1..(-by)//1, i, fn _, acc -> prev(s, acc) end)

  @doc "Code points between two indices; zero when `b` is at or past `e`."
  @spec count_between(String.t(), integer(), integer()) :: non_neg_integer()
  def count_between(s, b, e) when b >= e or b < 0, do: if(b < 0 and b < e, do: count_between(s, 0, e), else: 0)
  def count_between(s, b, e), do: length(String.to_charlist(slice(s, b, e)))

  @spec has_at_least(String.t(), integer(), integer(), integer()) :: boolean()
  def has_at_least(s, b, e, n), do: count_between(s, b, e) >= n

  @doc "The code points between two indices; empty when `b` is past `e`."
  @spec slice(String.t(), integer(), integer()) :: String.t()
  def slice(s, b, e) do
    n = byte_size(s)
    b = b |> max(0) |> min(n)
    e = e |> max(b) |> min(n)
    binary_part(s, b, e - b)
  end

  @doc "Splitting by \"\" gives each code point as its own string."
  @spec split(String.t(), String.t()) :: TemperCore.Vec.t(String.t())
  def split(s, ""), do: TemperCore.Vec.new(String.codepoints(s))
  def split(s, separator), do: TemperCore.Vec.new(String.split(s, separator))

  @spec for_each(String.t(), (char() -> term())) :: nil
  def for_each(s, f), do: s |> String.to_charlist() |> Enum.each(f) && nil

  @doc "The byte offset of `target` at or after `start`, or `StringIndex.none`."
  @spec index_of(String.t(), String.t()) :: integer()
  @spec index_of(String.t(), String.t(), integer()) :: integer()
  def index_of(s, target, start \\ 0) do
    start = start |> max(0) |> min(byte_size(s))
    # :binary.match rejects an empty pattern; js and py find it at the start
    if target == "", do: start, else: match_at(s, target, start)
  end

  defp match_at(s, target, start) do
    case :binary.match(s, target, scope: {start, byte_size(s) - start}) do
      {at, _} -> at
      :nomatch -> -1
    end
  end

  @spec from_code_point(integer()) :: String.t()
  def from_code_point(cp) when is_integer(cp) and cp >= 0 and cp <= 0x10FFFF and (cp < 0xD800 or cp > 0xDFFF),
    do: <<cp::utf8>>

  def from_code_point(cp), do: raise(TemperCore.Bubble, "#{cp} is not a Unicode scalar value")

  @spec from_code_points(TemperCore.Vec.t(integer()) | TemperCore.Ref.t() | [integer()]) :: String.t()
  def from_code_points(cps), do: cps |> TemperCore.List.items() |> Enum.map_join(&from_code_point/1)

  @doc "Integer JSON syntax, or digits in any radix from 2 to 36; anything else bubbles."
  @spec to_int32(String.t()) :: integer()
  @spec to_int32(String.t(), integer() | nil) :: integer()
  def to_int32(s, radix \\ 10), do: to_int(s, radix, -2_147_483_648, 2_147_483_647)
  @spec to_int64(String.t()) :: integer()
  @spec to_int64(String.t(), integer() | nil) :: integer()
  def to_int64(s, radix \\ 10), do: to_int(s, radix, -9_223_372_036_854_775_808, 9_223_372_036_854_775_807)

  # Temper's own TypesStringRead test parses " 2 " as 2, so whitespace is
  # trimmed first, as it is for floats
  defp to_int(s, radix, low, high) do
    s = String.trim(s)
    radix = radix || 10
    with true <- radix >= 2 and radix <= 36,
         {v, ""} <- Integer.parse(s, radix),
         true <- not String.starts_with?(s, "+"),
         true <- v >= low and v <= high do
      v
    else
      _ -> raise TemperCore.Bubble, "#{inspect(s)} is not an integer in radix #{radix} that fits"
    end
  end

  @doc "JSON number syntax plus `NaN` and the infinities, after trimming whitespace."
  @spec to_float64(String.t()) :: TemperCore.Float.t()
  def to_float64(s) do
    # a case, not `parse(s) || raise(...)`: Dialyzer cannot see that `||`
    # leaves no nil, and the spec says there is none
    case TemperCore.Float.parse(s) do
      nil -> raise TemperCore.Bubble, "#{inspect(s)} is not a number"
      f -> f
    end
  end
end

defmodule TemperCore.StringBuilder do
  @moduledoc """
  Temper's `StringBuilder`: a heap object whose value is the string so far.

  It is a value object (`Heap.new_value/2`), not a map of fields: a string
  holds no objects, so an append needs no write barrier. That makes an
  append two process-dictionary operations, which matters because Temper
  code builds strings one code point at a time.
  """
  alias TemperCore.Heap

  @spec new() :: TemperCore.Ref.t()
  def new, do: Heap.new_value(:string_builder, "")
  @spec append(TemperCore.Ref.t(), String.t()) :: nil
  def append(sb, text), do: Heap.put_value(sb, Heap.get_value(sb) <> text)
  @spec append_code_point(TemperCore.Ref.t(), integer()) :: nil
  def append_code_point(sb, cp), do: Heap.put_value(sb, <<Heap.get_value(sb)::binary, code_point(cp)::binary>>)
  @spec append_between(TemperCore.Ref.t(), String.t(), integer(), integer()) :: nil
  def append_between(sb, text, b, e), do: Heap.put_value(sb, Heap.get_value(sb) <> TemperCore.String.slice(text, b, e))
  @spec clear(TemperCore.Ref.t()) :: nil
  def clear(sb), do: Heap.put_value(sb, "")
  @spec to_string(TemperCore.Ref.t()) :: String.t()
  def to_string(sb), do: Heap.get_value(sb)
  @spec end_of(TemperCore.Ref.t()) :: non_neg_integer()
  def end_of(sb), do: byte_size(Heap.get_value(sb))

  defp code_point(cp) when cp < 0x80 and cp >= 0, do: <<cp>>
  defp code_point(cp), do: TemperCore.String.from_code_point(cp)
end
