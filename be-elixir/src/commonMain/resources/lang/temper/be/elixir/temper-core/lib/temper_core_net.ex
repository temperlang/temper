defmodule TemperCore.Net do
  @moduledoc """
  std/net's connected functions over OTP's :httpc. The request runs
  synchronously, then settles the promise it returns: a response completes
  it, a transport error (refused, unknown host) breaks it, which an `await`
  turns into a bubble. Needs :inets and :ssl in the Mix project's
  extra_applications; :httpc fails in :public_key without :ssl, even for
  plain http.
  """
  alias TemperCore.{Heap, Promise}

  def send_request(url, method, body, mime) do
    {:ok, _} = Application.ensure_all_started(:inets)
    p = Promise.new()

    request =
      case body do
        nil -> {String.to_charlist(url), []}
        # std/net's post() drops the mime type, so nil arrives here
        _ -> {String.to_charlist(url), [], String.to_charlist(mime || "text/plain"), body}
      end

    verb = method |> String.downcase() |> String.to_atom()

    case :httpc.request(verb, request, [], body_format: :binary) do
      {:ok, {{_version, status, _reason}, headers, response_body}} ->
        content_type =
          Enum.find_value(headers, fn {k, v} ->
            if :string.lowercase(k) == ~c"content-type", do: List.to_string(v)
          end)

        response = Heap.new(:net_response, %{status: status, content_type: content_type, body: response_body})
        Promise.complete(p, response)

      {:error, _reason} ->
        Promise.break_promise(p)
    end

    p
  end

  def status(r), do: Heap.get(r, :status)
  def content_type(r), do: Heap.get(r, :content_type)

  @doc """
  The body as a Temper String, which must be valid UTF-8: :httpc hands back
  raw bytes, and TemperCore.String raises on the first invalid sequence.
  The Content-Type charset picks the decoding. UTF-8 (or no charset) keeps
  valid bytes and replaces each invalid sequence with U+FFFD, as
  `fetch().text()` does; ISO-8859-1 maps each byte to its code point (not
  WHATWG's windows-1252 reading of that label, which differs in 0x80-0x9F);
  UTF-16 is converted. Any other charset breaks the promise rather than
  return text decoded by a guess.
  """
  def body_content(r) do
    p = Promise.new()

    case decode(Heap.get(r, :body), charset(Heap.get(r, :content_type))) do
      {:ok, text} -> Promise.complete(p, text)
      :error -> Promise.break_promise(p)
    end

    p
  end

  @doc false
  def charset(nil), do: nil

  def charset(content_type) do
    content_type
    |> String.split(";")
    |> Enum.drop(1)
    |> Enum.find_value(fn param ->
      case String.split(param, "=", parts: 2) do
        [k, v] ->
          if String.downcase(String.trim(k)) == "charset",
            do: v |> String.trim() |> String.trim("\"") |> String.downcase()

        _ ->
          nil
      end
    end)
  end

  @doc false
  def decode(body, charset) when charset in [nil, "utf-8", "utf8"], do: {:ok, String.replace_invalid(body)}

  def decode(body, charset) when charset in ["iso-8859-1", "iso8859-1", "latin1", "latin-1"],
    do: {:ok, :unicode.characters_to_binary(body, :latin1)}

  def decode(body, charset) when charset in ["utf-16be", "utf-16le"] do
    endian = if charset == "utf-16be", do: :big, else: :little

    case :unicode.characters_to_binary(body, {:utf16, endian}) do
      text when is_binary(text) -> {:ok, text}
      _ -> :error
    end
  end

  def decode(_body, _charset), do: :error
end
