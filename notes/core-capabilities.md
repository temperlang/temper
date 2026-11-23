# What libraries may do with core's types

Only what core means to enable is enabled. Core's declarations in
`lib/core/` say what that is, and the frontend reads them
(`typing::core_shape`, parsed once from the built-in core, even while
an editor changes a checkout's `lib/core/`) rather than
keeping lists of names. Each check is on the type a name evaluates to,
so an alias, as `let I = Int32;`, is checked as `Int32`
(`typing/builtin_uses.rs`). A builtin type core declared nothing for
would enable nothing; every one has a declaration, which a test checks
(`NoResult`, which had none and which nothing used, is no longer one).

- **`new`** makes a class with a public constructor. Every class core
  exports declares its constructor, `private` where only core or the
  host makes the values, so nothing is implied. An interface can't be
  made ("Cannot instantiate abstract type …"); a class without a
  public constructor gets "… is not a concrete type with a public
  constructor". So does a user's class with a private constructor,
  outside it (below).
- **Extending**: only unsealed interfaces. A class is final ("Cannot
  extend concrete type(s) …"), and a sealed interface is extended only
  in its own module ("Cannot extend sealed type … from …"), for users'
  sealed interfaces too, as in Kotlin Temper.
- **Type parameters** can't be made or extended, in core too: `new T()`
  is "Cannot instantiate type parameter T", and `extends T` "Cannot
  extend type parameter T", since `T` may stand for an interface, a
  final class, or a sealed interface (settle/classes.cases).
- **`is` and `as`**: refused where core marks a type
  `@mayDowncastTo(false)` (read from core, as the rest), or where a type
  argument would need a run-time check, as before. A `@connected` type,
  core's or a user's, may be checked for only from a sealed supertype
  that reaches it through sealed supertypes, as Kotlin's rule has it
  ("Connected types cannot be targeted …"): `r is DoneResult<T>` from
  `GeneratorResult<T>` and `i is StringIndex` from `StringIndexOption`,
  but not `x is StringIndex` from `AnyValue`, which JS translated as
  `x >= 0` for any value. `@mayDowncastTo` on a type decides for it. Refused too where
  core's docs say nothing can match: `Bubble` and `Void` aren't
  `AnyValue`s ("Unrelated types"), and `Never<T>` has no values ("can
  never succeed").
- **Type arguments**: any type but `Void`, within its parameter's
  bound, wherever written (annotations too). A map key must be a
  `MapKey` of itself.
- **Statics**: public ones, as `String.fromCodePoint` or `Float64.pi`;
  another, as `Int32.zzz` or `Empty.constructor`, is "No member zzz in
  Int32", as for a user's class.

A user's class named as a builtin type, as `class Int32`, is its own,
not the builtin.

A user's class's private constructor is its own, as private methods
are: `new C()` outside `C` (and classes inside it) is "C is not a
concrete type with a public constructor", through an alias or an object
literal too. Core's own code makes its own classes' values, as
`doneResult()` and `empty()` do, but no other file's. Core is only the
built-in core's text, or the `lib/core` of the checkout the compiler was
built from (`project::is_checkout`, by build path), never a library
that copies a checkout's layout.

`crates/temper-frontend/tests/core_capabilities.rs` checks every row
below, one case per type and capability, each named directly and
through an alias, extended by an interface and a class, and checked
with `is` and `as`. Its rows must be exactly `builtins::TYPES`, and the
names it can't use exactly core's other exported types, as parsed from
core, so a type core adds needs a row.

## The table

| Type | `new` | Extend | `is`, `as` from `AnyValue` | Map key |
| --- | --- | --- | --- | --- |
| `AnyValue` | no, interface | yes | yes | no |
| `Boolean` | no, private | no, class | no, `@mayDowncastTo(false)` | no |
| `Bubble` | no, private | no | no, not an `AnyValue` | no |
| `Console` | no, interface (`getConsole(name)`, `console`) | yes | no, connected | no |
| `DoneResult<T>` | no, private (`doneResult()`) | no | no, connected | no |
| `Empty` | no, private (`empty()`) | no | no, connected | no |
| `Equatable<T>` | no, interface | no, sealed | no, type arguments | no |
| `Float64` | no, private | no | no | no |
| `Generator<T>` | no, interface | yes | no, connected | no |
| `GeneratorFn` | no, interface | yes | yes | no |
| `GeneratorResult<T>` | no, interface | no, sealed | no, connected | no |
| `Int`, `Int32` | no, private | no | no | yes |
| `Int64` | no, private | no | no | no |
| `List<T>` | no, private (`[…]`, `List.of`) | no | no | no |
| `ListBuilder<T>` | `()` | no | no | no |
| `Listed<T>` | no, interface | no, sealed | no, type arguments | no |
| `Map<K, V>` | `(entries)` | no | no | no |
| `MapBuilder<K, V>` | `()` | no | no | no |
| `MapKey<T>` | no, interface | no, sealed | no, type arguments | no |
| `Mapped<K, V>` | no, interface | no, sealed | no, type arguments | no |
| `Never<T>` | no, interface | no, sealed | no, it has no values | no |
| `NoStringIndex` | no, private (`StringIndex.none`) | no | no, connected | no |
| `Null` | no, private (`null`) | no | yes | no |
| `Pair<K, V>` | `(key, value)` | no | no, connected | no |
| `Promise<T>` | no, private (`PromiseBuilder`) | no | no, connected | no |
| `PromiseBuilder<T>` | `()` | no | no, type arguments | no |
| `SafeGenerator<T>` | no, interface | yes | no, connected | no |
| `String` | no, private | no | no | yes |
| `StringBuilder` | `()` | no | no, connected | no |
| `StringIndex` | no, private (`String.begin`, methods) | no | no, connected | no |
| `StringIndexOption` | no, interface | no, sealed | no, connected | no |
| `Type` | no, private | no | yes | no |
| `ValueResult<T>` | `(value)` | no | no, connected | no |
| `Void` | no, private | no | no, not an `AnyValue` | no |

Core also exports `Function`, `Invalid` and `Problem`, which libraries
can't name (they aren't in `builtins::TYPES`). `Console`, `getConsole`,
`Equatable` and `Null` are named as in Kotlin Temper, whose core was the
prelude: a function may take a `Console`, a class may implement one, a
bound may be `T extends Equatable<T>` (the builtin types that are
`Equatable`s are, through `MapKey` for `Int32` and `String`), and `Null`
is null's type, which only null is.

Statics: `String.fromCodePoint`, `.fromCodePoints` and `.begin`,
`StringIndex.none`, `Float64.e` and `.pi`, and `List.of`.

The host supplies `Boolean`, `Int32`, `Int64`, `Float64`, `String`,
`List`, `ListBuilder`, `Listed`, `Map`, `MapBuilder`, `Mapped`, `Pair`,
`StringBuilder`, the string indices, `Empty`,
the generators and their results, `Promise`, `PromiseBuilder`, and
`Console`, each `@connected` in core. That refuses `is` and `as` for
them by the connected rule, unless `@mayDowncastTo` or type arguments
decide first. temper-driver's `host_types` test finds the core types
each backend translates as host or runtime types by scanning its
sources for `"core.type X"`, adds tmpl's value scalars, and checks each
is `@connected`, naming the type and backend otherwise, so a host type
can't be left unmarked, and no list is kept by hand. It checks the std
types backends name (`"std/collections.type Queue"`) the same way:
std's `Queue` and `BitVector`, once core's `Deque` and `DenseBitVector`
(2026-11-23), are `@connected` there.

## Gaps and open questions

The open questions of t856 are decided (notes/decisions.md, 2026-11-21
and 2026-11-23): `MapKey` is sealed; Kotlin's connected rule is
Temper's, for core's and users' connected types; `DoneResult`'s
constructor is private; users' private constructors are enforced per
class; `Console`, `getConsole`, `Equatable` and `Null` are nameable.

- **`Pair`, `Deque`, `DenseBitVector`** are `@connected` (2026-11-23),
  without `@mayDowncastTo`, so `is` and `as` can't target them; `Deque`
  and `DenseBitVector` are std's `Queue` and `BitVector` now, in
  `std/collections`. Before that, the interpreter,
  JS (once its `instanceof` named the runtime's classes, a fix dropped
  with the checks), Lua (once its tag check took core's tables, a fix
  kept, since `x is C` crashed on them), Python, Java, and C# told a `Pair` and a `DenseBitVector`
  from other values from `AnyValue`, but C# also checked a pair's type
  arguments (a `Pair<Int, String>` wasn't a `Pair<String, Int>`), and
  Rust and C++ can't make core's values (or, in C++, a class without
  interfaces) `AnyValue`s yet. The funtest `casts/core-classes` now
  checks a user's class among core's values, on every backend but those
  two.
- **Map key bounds** (closed by t853). A type argument is checked
  against its bound as a type, not a value (notes/typing.md, "Generic
  types").
- **`Type`** values stand for types, as macros' inputs. `x is Type` is
  allowed, as in Kotlin; whether any backend can tell a `Type` at run
  time is untested.
- **`MapKey`** is sealed until backends support users' keys; a `when`
  over a `MapKey` can't list its cases anyway (`is Int32` and `is
  String` are refused), so it's de facto not exhaustive.
- **`Generator`, `SafeGenerator`, `GeneratorFn`, `AnyValue`, `Console`**
  may be extended, as in Kotlin, but tmpl can't translate a user class
  that does yet ("not yet in tmpl: a builtin supertype").
- **Not translated yet:** an `Equatable<T>` bound, a `Null` type, and on
  JS, Lua, and Rust a `Console` passed as a value; `getConsole(name)`
  and `console.log` work on every backend.
