defmodule TemperCore.Regex do
  @moduledoc """
  What std/regex needs from the host: compiling the pattern std formats,
  and running it.

  The engine is `:re` (PCRE) with `:unicode` and without `:ucp`: the
  subject is UTF-8, so `.` is a code point, while `\\d`, `\\w` and `\\s`
  stay ASCII, as be-py's `re.ASCII` keeps them. Elixir's `"u"` flag would
  turn on both.

  `:re` reports byte offsets, which is what a `StringIndex` is here, so a
  group's `begin` and `end` need no conversion.

  A compiled regex is `{mp, names}`: the names of its captures in the order
  they appear in the pattern, which is the order std's `Match.groups`
  iterates in. `:re.inspect(mp, :namelist)` sorts them alphabetically.
  """

  @capture_name ~r/(?<!\\)\(\?<([A-Za-z_][A-Za-z0-9_]*)>/

  def compile(formatted) do
    mp =
      case :re.compile(formatted, [:unicode]) do
        {:ok, mp} -> mp
        {:error, reason} -> raise TemperCore.Panic, "regex #{inspect(formatted)} does not compile: #{inspect(reason)}"
      end

    names = Regex.scan(@capture_name, formatted, capture: :all_but_first) |> List.flatten()
    {:namelist, sorted} = :re.inspect(mp, :namelist)

    if Enum.sort(names) != Enum.sort(sorted) do
      raise TemperCore.Panic, "regex #{inspect(formatted)}: captures #{inspect(names)} but :re has #{inspect(sorted)}"
    end

    {mp, names}
  end

  def found({mp, _}, text), do: :re.run(text, mp, capture: :none) == :match

  @doc "`find`: the first match at or after `begin`, or a bubble."
  def find({mp, names}, text, begin, match_module, group_module) do
    case :re.run(text, mp, [{:offset, begin}, {:capture, [0 | names], :index}]) do
      {:match, spans} -> to_match(text, names, spans, match_module, group_module)
      :nomatch -> raise TemperCore.Bubble, "no match"
    end
  end

  @doc "`replace`: every match replaced by what `format` makes of it."
  @spec replace(term(), String.t(), (term() -> String.t()), module(), module()) :: String.t()
  def replace({mp, names}, text, format, match_module, group_module) when is_binary(text) do
    case :re.run(text, mp, [:global, {:capture, [0 | names], :index}]) do
      :nomatch ->
        text

      {:match, all} ->
        {pieces, last} =
          Enum.reduce(all, {[], 0}, fn [{at, len} | _] = spans, {pieces, from} ->
            replacement = format.(to_match(text, names, spans, match_module, group_module))
            {[replacement, binary_part(text, from, at - from) | pieces], at + len}
          end)

        IO.iodata_to_binary(Enum.reverse([binary_part(text, last, byte_size(text) - last) | pieces]))
    end
  end

  @doc "`split`: the pieces between matches, with captured groups between them, as be-py's `re.split` gives."
  def split({mp, _}, text), do: TemperCore.Vec.new(:re.split(text, mp, return: :binary))

  @doc "`pushCodeTo`: PCRE's numeric escape for a code point."
  def code_escape(code), do: "\\x{" <> Integer.to_string(code, 16) <> "}"

  defp to_match(text, names, [{at, len} | named], match_module, group_module) do
    full = group_module.new("full", binary_part(text, at, len), at, at + len)

    groups =
      names
      |> Enum.zip(named)
      # an unmatched group is {-1, 0}, and absent from the map, as in be-py
      |> Enum.reject(fn {_, {b, _}} -> b < 0 end)
      |> Enum.map(fn {name, {b, l}} ->
        TemperCore.Pair.new(name, group_module.new(name, binary_part(text, b, l), b, b + l))
      end)

    match_module.new(full, TemperCore.Map.new(groups))
  end
end
