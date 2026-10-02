defmodule TemperCoreNetTest do
  use ExUnit.Case, async: true
  alias TemperCore.{Net, Promise}

  # a one-shot HTTP server answering with the given content type and body
  defp serve_bytes(content_type, body) do
    {:ok, listen} = :gen_tcp.listen(0, [:binary, active: false, reuseaddr: true, ip: {127, 0, 0, 1}])
    {:ok, port} = :inet.port(listen)

    spawn_link(fn ->
      {:ok, s} = :gen_tcp.accept(listen)
      {:ok, _request} = :gen_tcp.recv(s, 0)

      :gen_tcp.send(
        s,
        "HTTP/1.1 200 OK\r\nContent-Type: #{content_type}\r\n" <>
          "Content-Length: #{byte_size(body)}\r\nConnection: close\r\n\r\n" <> body
      )

      :gen_tcp.close(s)
    end)

    port
  end

  defp body_of(content_type, body) do
    port = serve_bytes(content_type, body)
    response = Promise.result(Net.send_request("http://127.0.0.1:#{port}/", "GET", nil, nil))
    Net.body_content(response)
  end

  test "a latin-1 body is decoded by its charset" do
    text = Promise.result(body_of("text/plain; charset=iso-8859-1", <<"caf", 0xE9>>))
    assert text == "café"
    assert TemperCore.String.count_between(text, 0, byte_size(text)) == 4
  end

  test "invalid UTF-8 becomes U+FFFD, as fetch().text() does" do
    assert Promise.result(body_of("text/plain", <<"caf", 0xE9>>)) == "caf\uFFFD"
    assert Promise.result(body_of("text/plain; charset=\"UTF-8\"", <<"a", 0xE2, 0x82, "b">>)) == "a\uFFFDb"
  end

  test "UTF-16 is converted" do
    assert Promise.result(body_of("text/plain; charset=utf-16be", <<0, ?h, 0, ?i>>)) == "hi"
  end

  test "a charset it cannot decode breaks the promise" do
    p = body_of("text/plain; charset=shift_jis", <<0x82, 0xA0>>)
    assert_raise TemperCore.Bubble, fn -> Promise.result(p) end
  end

  # a one-shot HTTP server answering with the request's method, as the
  # functional tests' TestWebServer does
  defp serve_once do
    {:ok, listen} = :gen_tcp.listen(0, [:binary, active: false, reuseaddr: true, ip: {127, 0, 0, 1}])
    {:ok, port} = :inet.port(listen)

    spawn_link(fn ->
      {:ok, s} = :gen_tcp.accept(listen)
      {:ok, request} = :gen_tcp.recv(s, 0)
      [method | _] = String.split(request, " ")
      body = ~s({"method": "#{method}"})

      :gen_tcp.send(
        s,
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" <>
          "Content-Length: #{byte_size(body)}\r\nConnection: close\r\n\r\n" <> body
      )

      :gen_tcp.close(s)
    end)

    port
  end

  test "a response completes the promise" do
    port = serve_once()
    response = Promise.result(Net.send_request("http://127.0.0.1:#{port}/", "POST", "[]", nil))
    assert Net.status(response) == 200
    assert Net.content_type(response) == "application/json"
    assert Promise.result(Net.body_content(response)) == ~s({"method": "POST"})
  end

  test "a refused connection breaks the promise" do
    p = Net.send_request("http://127.0.0.1:1/", "GET", nil, nil)
    assert_raise TemperCore.Bubble, fn -> Promise.result(p) end
  end
end
