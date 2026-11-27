# tmpl: the public surface

Part of the [tmpl design](README.md).

## Level 1: The public surface is first-class

**Decided (2026-10-29):** tmpl carries enough intent about a library's
public surface that each backend can present an idiomatic API, not just
correct code.

- **Decided (2026-10-29): the model stores only what the source says**
  about the public surface; shared helpers derive the rest.
- **Body lowerings and surface choices are kept apart** (Proposed),
  since a backend may lower a body construct and still expose its
  idiomatic form (a Java state machine behind an `Iterator`, Rust
  results behind a public `Result`).
- **Public API compatibility** ([api-compat.md](../api-compat.md)) is
  checked at this layer: the surface a backend derives from tmpl is
  what its API diff compares.

Level 2, below, has the structure.

### Object literals, builders, and no named arguments

Decided earlier (language.md, "Object literals, not named arguments"):
tmpl has a constructor call with its arguments already placed, never
symbols or named arguments. Whether a public constructor gets a builder
(Java, Rust) or named-argument form (JS, Python) is a `surface` query,
answered once, so every backend agrees.

## Level 2: The public surface: intent and per-backend choices

**Stored** (Decided 2026-10-29: only what the source says), on
declarations: exported and visibility; source names and kinds; docs;
fields versus getters and setters, and which are computed; constructor
parameters and which have defaults; `@fun` interfaces by name; what
they throw; generator and `async`; `@operator`, `@auto("==")`, and `@imu`
marks;
connected marks and qnames; abstract, sealed, and type parameters with bounds.

**Derived** by `surface` helpers, each answered once per declaration
and profile:

- **Constructor form:** plain, builder (Java, Rust), named arguments
  (Python, JS options), or option struct, from the parameter count and
  defaults ([Level 1, above](#object-literals-builders-and-no-named-arguments)).
- **Property form:** native property, getter and setter methods, or a
  field (api-compat.md's per-backend rules).
- **Failure in signatures:** exceptions or `Result`, even where a body
  was lowered differently.
- **Iteration:** a generator presented as the host's iterator
  (`Iterator`, `IEnumerable`, Python generators, Rust `Iterator`).
- **Equality:** whether the `@operator("==")` method also implements
  the host's protocol (Decided 2026-10-29, [question 14](open-questions.md);
  Level 3, below), and `!=` with it where needed.
- **Mutability:** whether a Rust class wrapper needs a lock
  (api-compat.md proposal 4), from [mutability.md](mutability.md)'s analysis.

Profiles carry the per-variant choices as data in their `surface`
section (a Kotlin-compatible Python variant keeps Kotlin's shapes; an
idiomatic one may choose differently, each with its own API baseline), and naming conventions come from `NamingRules`. Passes that
change a body never change surface facts; a pass that changes how a
public declaration works records which surface form it presents (a
state-machine generator still presents an iterator). The API diff
compares what these helpers derive.

## Level 3: Operators and protocols in each language's own form

**Decided (2026-10-29):** operators and protocols map to each
language's own version. As Temper adds `@operator` support and similar
mappings (`get()` as `[]` indexing), tmpl records the source's intent,
and each backend variant picks the native hook, as data. Even
`toString` maps to a native protocol. Proposed in detail:

**Intent, stored** as one fact on a method, `protocol:
Option<Protocol>`, set by the frontend from what the source says:

| Protocol | From, today | From, later |
|---|---|---|
| `Eq` | `@operator("==")`, or a synthesized `@auto("==")` method | |
| `Compare` | `@operator("<=>")` | |
| `Show` | a public `toString(): String` method ([question 33](open-questions.md)) | |
| `Index`, `IndexSet` | | `get` and `set` marked as `[]`, once Temper has it |
| `Iterate` | | a method that gives an iterator, for `for … of` |
| `Hash` | | once Temper has hashing |

Builtin types' protocols belong to the variant's operator and
connection tables instead (core's `Int32.toString`, qname
`core.type Int32.toString()`, prints as `str(n)` in Python).

**Hooks, per variant,** in its profile's `surface` section:

```rust
pub struct Hook {
    pub define: &'static [Define], // what the class gets; empty for nothing
    pub call: CallStyle,           // how call sites print: Method, Operator, or Function
    pub keep_method: bool,         // also keep the source-named method
}
pub enum Define {
    Method(&'static str),     // Python `__eq__`, Lua `__tostring`
    Trait(&'static str),      // Rust `PartialEq`, `Display`
    Operator(&'static str),   // C++ `operator==`
    Override(&'static str),   // Java `toString`, C# `ToString`
}
```

First variants' hooks, Proposed (`Eq` as decided in [question 14](open-questions.md)):

| Protocol | C++ | C# | Java | JavaScript | Lua | Python | Rust |
|---|---|---|---|---|---|---|---|
| `Eq` | `operator==` and `!=` | method only | method only | method only | method only | `__eq__` | `PartialEq` |
| `Show` | `operator<<` | `ToString` | `toString` | `toString` | `__tostring` | `__str__` | `Display` |
| `Index` | `operator[]` | indexer | method only | method only | method only | `__getitem__` | method only |

Notes:

- **`keep_method` is true in every first variant:** adding a hook adds
  to the API, but dropping or renaming the method wouldn't be
  Kotlin-compatible. Each hook is checked against the variant's API
  baseline.
- **Call sites stay calls in tmpl;** the translator prints them by
  `call`: `p == q` where the hook defines `==`, `p.eq(q)` otherwise.
  `!=` is `Boolean.not` around the call, printed `!=` where the hook
  gives one.
- **Debug and nice forms.** `Show` is the nice form (`__str__`,
  `Display`). Debug forms (`__repr__`, `Debug`) are derived by each
  backend: Rust derives `Debug` from fields, since `assert_eq!` and
  `{:?}` need it; Python's first variant keeps the default `__repr__`,
  and an idiomatic variant may build one from fields. A source-level
  debug hook is [question 31](open-questions.md).
- **Rust's `Index`** returns a reference, which doesn't fit Temper's
  `get`, so Rust keeps the method; Java, JS, and Lua have no fitting
  hook (Lua's `__index` would hide methods).
- **New protocols** add a `Protocol` variant and table rows; no pass
  changes ([question 32](open-questions.md)).

Subset A has no classes, so its surface is exported functions and
module `let`s, named by `NamingRules`, with positional-only
parameters in Python.

**Qnames and names together** (Proposed 2026-10-29): a declaration's
qname is its identity in the API diff and across versions; its output
name comes from `NamingRules`. Exported, connected (in user code), and
`@keep` names are fixed by the rules alone, never suffixed, so host
code and users can rely on them, and the first variants give Kotlin's
([names.md](names.md#level-3-connected-declarations)). Protocols arrive with subset B (`Eq` and
`Show`).

## Parameter names where languages have named arguments (Proposed, 2026-10-30)

Temper has no named arguments, except constructors and factories, which
take object literals (property bags), with builders in languages that
lack named arguments. So a function's parameter names aren't part of its
API, and authors shouldn't have to know which languages let callers pass
arguments by name. Each backend says so in its own ecosystem's terms,
rather than making names ugly:

- **C#:** the generated NuGet package carries a Roslyn analyzer (under
  `analyzers/dotnet/cs/`), which every consuming project runs
  automatically, warning on named arguments in calls into the library,
  ideally with a fix that removes the name. Constructors and factories
  are exempt: their parameter names are property names, part of the API
  on purpose.
- **PHP:** `@no-named-arguments` in each function's PHPDoc, which PHPStan
  and Psalm recognize; again not on constructors and factories.
- **Swift:** `_` labels; **Python:** `/` (as built).
- Languages without named arguments need nothing.

To confirm when those backends are built: that the analyzer and the tag
work as expected, and what each costs consumers.

As built (2026-10-30): the C# backend keeps every parameter's name
exactly the source's (a keyword as `@class`), never renamed for looks;
the analyzer isn't built yet. It needs the NuGet package (`dotnet pack`
of the library's project, with the analyzer's assembly under
`analyzers/dotnet/cs/`), so it comes with packing. A sketch: one
`DiagnosticAnalyzer` on `InvocationExpression`s whose target method is
in the library's assembly (an assembly attribute can mark it) and isn't
a constructor or factory, reporting an `ArgumentSyntax` with a
`NameColon`; and a code fix that removes the name where the arguments
are already in order.
