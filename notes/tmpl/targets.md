# tmpl: targets

Part of the [tmpl design](README.md).

## Level 1: Targets

**Decided:** primary targets are Kotlin Temper's backends (C++, C#,
Java, JavaScript, Lua, Python, Rust). Python is tempest's first backend
and Rust right after, so the design keeps both ends in view: Python
needs few lowerings but exact capture and scope facts, and Rust needs
results, state machines, explicit conversions, nullable as `Option`,
and ownership-friendly facts.

The stress list, kept in mind but not central:

| Target | What it stresses | How the design copes |
|---|---|---|
| C99 | No GC, generics, closures, classes, or exceptions | Error codes from the explicit bubbling paths; closure conversion; erasure or monomorphizing; structs and vtables. Reference counting relies on no cycles ([acyclic.md](../acyclic.md)) and cleanup on every exit path. Possible, not cheap. |
| PHP | `use (&$x)` captures; arrays copied by value | Capture lists; lists as runtime objects. Copes. |
| Go | No exceptions or inheritance, limited generics | Results as `(T, error)`; interfaces plus delegation for interfaces' method bodies; generic methods as functions. Copes, with some passes. |
| Swift | ARC, value types, protocols, `try` at each call | Every bubbling call is marked; ARC relies on no cycles. Copes. |
| Dart, Ruby, Kotlin | Little | Copes. |
| D | GC by default, C-like, `nothrow` and `@safe` attributes | Close to C++ and C99; can speak the C ABI directly. Copes. |
| OCaml | No `return`, `break`, or `continue`; mutable variables as `ref`s; objects rarely used | Early exits as local exceptions or restructured loops; mutable locals as `ref`s; classes as records of closures, or OCaml objects. Copes, with some passes. |
| Elixir, BEAM; Haskell | Immutability, processes, no loops; purity, laziness | Rebinding and recursion are generic passes; [mutability.md](mutability.md)'s facts say what may cross processes and what is plain data. Shared mutable objects still need emulating. Room is left. |
| Actors and isolates generally | What state is per isolate, what may be sent | All mutable state is explicit ([mutability.md](mutability.md)). |
| MATLAB | 1-based indexing, value semantics | Indexing stays in runtime operations; handle classes; cells. Partly. |
| WASM | Structured control only | Labeled blocks and loops fit `br`; otherwise the C99 path, or WasmGC. Copes via the low end. |

## Level 3: First variants

**Decided (2026-10-29):** every backend's first variant is
Kotlin-compatible, so Chromarite's and Ordable's tours keep working
([profiles.md](profiles.md#level-3-lowering-for-subset-a)).

## Tiers, and C99 with the C ABI (Proposed, 2026-10-30)

- **Tier 1, carried forward fully:** Kotlin Temper's 7 (C++, C#, Java,
  JavaScript, Lua, Python, Rust).
- **Tier 2, carried forward more lightly:** Go, PHP, Swift, and C99,
  each with a small runtime of our own.
- **Tier 3, design probes at subset A only, generated but not yet
  run:** D, Dart, Elixir, Kotlin, OCaml, and Ruby, brought forward when
  the design is stable or someone wants them.

C99 is tier 2 because it should be designed together with the planned
standard Temper C ABI (testing-and-milestones.md, "Later, maybe"),
which C++ and Rust would also export (`extern "C"`) so native code can
back faster Python and Lua packages. The ABI is the contract, and the
C99 backend its plainest implementation: ownership and reference counts
across the boundary, strings with explicit encoding and length,
error as status codes and out-parameters, generics monomorphized or
erased, and explicit, thread-safe module initialization.
