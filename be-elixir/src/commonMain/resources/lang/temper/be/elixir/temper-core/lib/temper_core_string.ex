defmodule TemperCore.String do
  @moduledoc """
  Temper's `String`, over Elixir's UTF-8 binaries.

  A Temper `StringIndex` is a byte offset into the UTF-8, which is what an
  Elixir binary already is, so an index is an integer and `String.begin` is
  0. `next` and `prev` step over whole code points. `StringIndex.none` is -1.

  Counting is in code points, never graphemes: `String.length/1` counts
  graphemes, and Temper's `countBetween` does not.
  """

  def begin, do: 0
  def none, do: -1
  def end_of(s), do: byte_size(s)
  def is_empty(s), do: s == ""
  def has_index(s, i), do: i >= 0 and i < byte_size(s)

  @doc "The code point at byte offset `i`; panics if there is none."
  def get(s, i) do
    case s do
      <<_::binary-size(i), cp::utf8, _::binary>> when i >= 0 -> cp
      _ -> raise TemperCore.Panic, "no code point at #{i} in a string of #{byte_size(s)} bytes"
    end
  end

  @doc "The index after the code point at `i`, or `end` if there is none."
  def next(s, i) do
    n = byte_size(s)
    cond do
      i >= n -> n
      i < 0 -> 0
      true -> i + width(:binary.at(s, i))
    end
  end

  @doc "The index of the code point before `i`, or `begin` if there is none."
  def prev(s, i) do
    i = min(i, byte_size(s))
    if i <= 0, do: 0, else: back(s, i - 1)
  end

  # step back over UTF-8 continuation bytes (10xxxxxx)
  defp back(_s, 0), do: 0
  defp back(s, i), do: if(Bitwise.band(:binary.at(s, i), 0xC0) == 0x80, do: back(s, i - 1), else: i)

  defp width(b) when b < 0x80, do: 1
  defp width(b) when b >= 0xF0, do: 4
  defp width(b) when b >= 0xE0, do: 3
  defp width(_b), do: 2

  def step(s, i, by) when by >= 0, do: Enum.reduce(1..by//1, i, fn _, acc -> next(s, acc) end)
  def step(s, i, by), do: Enum.reduce(1..(-by)//1, i, fn _, acc -> prev(s, acc) end)

  @doc "Code points between two indices; zero when `b` is at or past `e`."
  def count_between(s, b, e) when b >= e or b < 0, do: if(b < 0 and b < e, do: count_between(s, 0, e), else: 0)
  def count_between(s, b, e), do: length(String.to_charlist(slice(s, b, e)))

  def has_at_least(s, b, e, n), do: count_between(s, b, e) >= n

  @doc "The code points between two indices; empty when `b` is past `e`."
  def slice(s, b, e) do
    n = byte_size(s)
    b = b |> max(0) |> min(n)
    e = e |> max(b) |> min(n)
    binary_part(s, b, e - b)
  end

  @doc "Splitting by \"\" gives each code point as its own string."
  def split(s, ""), do: String.codepoints(s)
  def split(s, separator), do: String.split(s, separator)

  def for_each(s, f), do: s |> String.to_charlist() |> Enum.each(f) && nil

  @doc "The byte offset of `target` at or after `start`, or `StringIndex.none`."
  def index_of(s, target, start \\ 0) do
    start = start |> max(0) |> min(byte_size(s))
    case :binary.match(s, target, scope: {start, byte_size(s) - start}) do
      {at, _} -> at
      :nomatch -> -1
    end
  end

  def from_code_point(cp) when is_integer(cp) and cp >= 0 and cp <= 0x10FFFF and (cp < 0xD800 or cp > 0xDFFF),
    do: <<cp::utf8>>

  def from_code_point(cp), do: raise(TemperCore.Bubble, "#{cp} is not a Unicode scalar value")

  def from_code_points(cps), do: cps |> TemperCore.List.items() |> Enum.map_join(&from_code_point/1)

  @doc "Integer JSON syntax, or digits in any radix from 2 to 36; anything else bubbles."
  def to_int32(s, radix \\ 10), do: to_int(s, radix, -2_147_483_648, 2_147_483_647)
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
  def to_float64(s) do
    TemperCore.Float.parse(s) || raise(TemperCore.Bubble, "#{inspect(s)} is not a number")
  end
end

defmodule TemperCore.StringBuilder do
  @moduledoc "Temper's `StringBuilder`: a heap object holding the string so far."
  alias TemperCore.Heap

  def new, do: Heap.new(:string_builder, %{s: ""})
  def append(sb, text), do: put(sb, get(sb) <> text)
  def append_code_point(sb, cp), do: put(sb, get(sb) <> TemperCore.String.from_code_point(cp))
  def append_between(sb, text, b, e), do: put(sb, get(sb) <> TemperCore.String.slice(text, b, e))
  def clear(sb), do: put(sb, "")
  def to_string(sb), do: get(sb)
  def end_of(sb), do: byte_size(get(sb))

  defp get(sb), do: Heap.get(sb, :s)
  defp put(sb, s), do: Heap.put(sb, :s, s) && nil
end
