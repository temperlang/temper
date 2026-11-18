# `std/net`: HTTP requests

```temper inert
let { NetRequest, NetResponse } = import("std/net");
```

A small, best-effort HTTP client. `NetRequest` builds a request, and
only `send()` starts it, returning a promise of a `NetResponse`:

```temper inert
let { NetRequest } = import("std/net");

async { (): GeneratorResult<Empty> extends GeneratorFn =>
  let request = new NetRequest("https://example.com/api");
  request.post("""{"q": 1}""", "application/json");
  let response = await request.send() orelse panic();
  console.log("Status ${response.status.toString()}");
  let body = await response.bodyContent orelse panic();
  console.log(body ?? "no text");
}
```

| Member | What it does |
|---|---|
| `new NetRequest(url)` | A `GET` request for `url` |
| `post(content, mimeType)` | Makes it a `POST` with that textual body |
| `send()` | Sends it: a `Promise<NetResponse>`, broken if the backend can't |
| `NetResponse.status` | The HTTP status code |
| `NetResponse.contentType` | The body's content type, if any |
| `NetResponse.bodyContent` | A promise of the body's text, if it's textual |

Each backend connects these to its language's HTTP support; C++ needs
libcurl, and Lua needs LuaSocket.

Source: [net.temper.md](../../lib/std/net/net.temper.md).
