# Concurrency and parallelism: a recommendation

A recommendation for the team (2026-11-05), not a decision. The goal: Temper
libraries for client and server code that host code in every backend can
use naturally. [actors.md](actors.md) holds the actor brainstorm this
builds on.

## Layers, most libraries using only the first

1. **Sans-I/O cores.** Protocol and business logic as pure state machines:
   bytes or events in, actions and bytes out (as Python's h11 and Rust's
   rustls and quinn's core). The host owns sockets, timers, and threads, so
   one Temper HTTP parser, database protocol, or router drops into tokio,
   asyncio, Node, Netty, ASP.NET, or OpenResty with none of Temper's
   concurrency machinery. Hrefine and Kelpto are already pure, so they're
   already this.
2. **Turns.** Where Temper code waits, `async`/`await` runs one turn at a
   time per isolate and switches only at `await` (Temper PR #532 makes
   every backend agree; tempest does on all seven). It's deterministic and
   needs no locks. Connected code never blocks inside a turn
   ([api-compat.md](api-compat.md), "Async in connected code"). Generated
   code calls only the runtime's entry points (launch, `awakeUpon`,
   `complete`, and the top-level turn), never its queue or a particular
   thread, so the runner stays swappable: today's one FIFO queue on one
   `temper-async` thread is the one-isolate case of one queue per isolate
   or actor, many on a thread pool.
   - **Why a language guarantee, not a backend choice:** Temper code is
     written and tested once, often on JS or the interpreter, both single
     threaded. If some backends ran blocks in parallel, code correct there
     would race elsewhere, and the interpreter's tests couldn't catch it.
     So plain async never overlaps anywhere; parallelism comes back only
     through explicit constructs with checked rules (actors, below).
   - **Order is the remaining assumption:** code may still rely on the
     order ready blocks run in (FIFO today). A test mode that shuffles
     ready steps with a replayable seed would catch that, cheaply, since
     every step goes through the runner.
3. **Capabilities for effects.** Clock, timers, randomness, network, and
   files are interfaces a library is handed, with defaults (below). Hosts
   plug in their own loop and I/O, and tests plug in fakes.
4. **Structured concurrency.** Task groups, cancellation, and deadlines,
   scoped so no async block outlives its parent. Servers need timeouts and
   cancellation; today's fire-and-forget `async {}` makes leaks easy.
5. **Actors and isolates** for parallelism and shared state, by #530's
   rules: only sendable values cross, and each backend maps actors to its
   own idea (BEAM processes, Swift actors, Rust `Send` handles, JS workers,
   Python subinterpreters or threads, Java virtual threads, C# tasks, and
   C++ threads). Opt-in: libraries that don't need it don't pay.
6. **Thin host adapters, as separate libraries:** runners (asyncio, tokio,
   Node, .NET, and Java executors) and frameworks (axum, ASP.NET, Express,
   FastAPI, and Spring), such as an axum adapter. The core
   stays dependency-free.

In practice, a client is a sans-I/O protocol plus a pluggable transport,
with a convenience async wrapper. A server handler is a function from an
immutable request to a promise of an immutable response, with an actor for
shared state, mounted through the host framework's adapter.

## Frictionless in the common case

Pluggability must cost nothing when it isn't used:

- **Defaults just work.** Each capability has a built-in default per
  backend (the real clock, the real network through the platform's usual
  API, and the default runner), so `std/net` code runs with no setup on
  every backend, as today.
- **Override once, at the edge.** A host changes a default through one
  optional setup call or configuration object per language (as Rust's
  `init(Some(config))` already does for the runner), never by threading
  parameters through every call. Libraries only take a capability
  explicitly when the caller should choose (a test clock, say).
- **Adapters are one line.** Using asyncio or tokio means installing that
  adapter's runner and I/O once; no change in library code.
- **Interop is native.** Promises are awaitable and generators are
  iterable the language's standard way on every backend, so host code
  never touches Temper's runtime types directly.

## What std's `net` on C++ and Lua taught

The prototype of pluggable I/O (2026-11-05): one client shape behind
std's `sendRequest` on C++ and Lua (patches 0018 and 0019).

- **The shape:** `send(request, respond)`; the request is plain data
  (URL, method, optional body and its type), and `respond` is called
  once, with a plain response or nothing. The runtime, not the client,
  turns that into std's `NetResponse` and settles the promise, so a
  client never touches Temper's types. One optional call at the edge
  swaps it (`temper::core::net::set_client`, `temper.set_net_client`),
  and a default needs no setup.
- **Generalizes:** a callback, not a promise, is the portable result:
  it suits threads (C++), event loops (Lua, JS), and fakes alike. The
  same shape fits a clock (`sleep(ms, wake)`) and files (`read(path,
  respond)`). The runner needs two hooks for any of them: a **hold**,
  so it waits for work in flight (Rust's `AsyncRunner::hold`, C++'s
  `async_hold`), and a way **back onto the Temper thread** (C++'s
  `async_post`, Lua's I/O sources). Host tests plug in a fake the same
  way (`tests/host/net.cpp`, `net.lua`), which also gives deterministic
  simulation its seam.
- **What C++ needed:** no standard executor or HTTP client, so the
  default is an optional dependency (libcurl, found by CMake, a define
  in every file) on a thread of its own, and a thread-safe inbox, since
  the queue is single-threaded. Missing defaults fail at run time with
  a message, never at build time.
- **What Lua needed:** no threads, so the runner itself polls I/O when
  no step is ready, and a non-blocking library has to be adapted:
  LuaSocket's `socket.http` runs in a coroutine over sockets that yield
  instead of blocking. Rocks are per Lua version, so LuaJIT may lack
  the default; and there's no HTTPS without LuaSec.
- **The cost of no host loop:** where nothing else drives the queue
  (C++ and Lua), the outermost drain waits for I/O before returning, so
  top-level code after an `async` block that sends runs after the
  response, unlike JS; no turn is blocked. A host with its own loop
  plugs in a client that answers there instead.
- **Next:** the other five backends keep their own `net` as they are;
  each would adopt the shape by wrapping it as the default behind a
  `set_net_client` (Python's `urllib` on `temper-io`, JS's `fetch`,
  Rust's `ureq` thread, Java's and C#'s `HttpClient`). A Temper-level
  `NetClient` interface in std is the team's call (new public API).

## Deterministic simulation testing

Turns plus injected effects let a whole Temper client or server run in the
interpreter (or any backend) under a fake clock and network, replaying a
failure exactly, as FoundationDB and TigerBeetle test. Server logic tested
by simulation, then shipped in seven languages, would be a strong showcase.

## Isolation when host code calls in

Temper makes libraries, so host threads may call any object at any time.
[actors.md](actors.md) sorts backends by who enforces isolation (the
compiler in Rust and Swift, the runtime on the BEAM and in JS, nobody
elsewhere). The experimental Temper PRs #533 (Python), #534 (Java), #537
(Rust), and #538 (C++), closed for now, showed one reliable approach:

- Each `@actor` instance holds a reentrant gate. Every method, getter,
  setter, and constructor body takes a turn through it, as do async steps
  started during a turn and inherited interface methods.
- Calls from the thread already in the actor's chain run inline, as in the
  interpreter.
- A contended wait walks a wait-for table, and a call that would close a
  cycle panics ("actor call cycle") instead of deadlocking. Uncontended
  calls never touch the table.
- Failures and panics release the gate on the way out.

Plain (non-actor) mutable objects stay unguarded: confined to one thread or
actor by documentation, plus native markers where a language has them and an
opt-in debug check elsewhere ([actors.md](actors.md), "A possible
structure").

## Suggested order

1. Consistent turns on every backend (#532).
2. Capability interfaces in std with built-in defaults, starting with
   clock and network.
3. Sans-I/O as the documented library pattern, with a showcase (an
   HTTP/1.1 core would pair well with Hrefine).
4. Structured concurrency.
5. Actors and isolates.
6. Adapters, as demand appears.

Steps 1 to 3 pay off even if actors never come.

## Risks

- Lua has no threads, so actors there are cooperative only.
- C++ has no standard executor, so it always needs an adapter for I/O.
- Gates on actors cost something on every call from host code.
- Sans-I/O APIs are less convenient than plain async ones, so a library
  likely wants both layers.
