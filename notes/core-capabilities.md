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
  constructor".
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
  argument would need a run-time check, as before. Refused too where
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

`crates/temper-frontend/tests/core_capabilities.rs` checks every row
below, one case per type and capability, each named directly and
through an alias, extended by an interface and a class, and checked
with `is` and `as`. Its rows must be exactly `builtins::TYPES`, and the
names it can't use exactly core's other exported types, as parsed from
core, so a type core adds needs a row.

## The table

"Gap" marks what isn't meant to work but isn't refused yet.

| Type | `new` | Extend | `is`, `as` from `AnyValue` | Map key |
| --- | --- | --- | --- | --- |
| `AnyValue` | no, interface | yes | yes | no |
| `Boolean` | no, private | no, class | no, `@mayDowncastTo(false)` | no |
| `Bubble` | no, private | no | no, not an `AnyValue` | no |
| `DenseBitVector` | `(capacity)` | no | yes | no |
| `Deque<T>` | `()` | no | no, type arguments | no |
| `DoneResult<T>` | `()` | no | gap (connected) | no |
| `Empty` | no, private (`empty()`) | no | gap (connected) | no |
| `Float64` | no, private | no | no | no |
| `Generator<T>` | no, interface | yes | gap (connected) | no |
| `GeneratorFn` | no, interface | yes | yes | no |
| `GeneratorResult<T>` | no, interface | no, sealed | gap (connected) | no |
| `Int`, `Int32` | no, private | no | no | yes |
| `Int64` | no, private | no | no | no |
| `List<T>` | no, private (`[…]`, `List.of`) | no | no | no |
| `ListBuilder<T>` | `()` | no | no | no |
| `Listed<T>` | no, interface | no, sealed | no, type arguments | no |
| `Map<K, V>` | `(entries)` | no | no | no |
| `MapBuilder<K, V>` | `()` | no | no | no |
| `MapKey<T>` | no, interface | yes (open question) | no, type arguments | no |
| `Mapped<K, V>` | no, interface | no, sealed | no, type arguments | no |
| `Never<T>` | no, interface | no, sealed | no, it has no values | no |
| `NoStringIndex` | no, private (`StringIndex.none`) | no | gap (connected) | no |
| `Pair<K, V>` | `(key, value)` | no | yes | no |
| `Promise<T>` | no, private (`PromiseBuilder`) | no | gap (connected) | no |
| `PromiseBuilder<T>` | `()` | no | no, type arguments | no |
| `SafeGenerator<T>` | no, interface | yes | yes | no |
| `String` | no, private | no | no | yes |
| `StringBuilder` | `()` | no | gap (connected) | no |
| `StringIndex` | no, private (`String.begin`, methods) | no | gap (connected) | no |
| `StringIndexOption` | no, interface | no, sealed | gap (connected) | no |
| `Type` | no, private | no | yes | no |
| `ValueResult<T>` | `(value)` | no | gap (connected) | no |
| `Void` | no, private | no | no, not an `AnyValue` | no |

Core also exports `Console`, `Equatable`, `Function`, `Invalid`, `Null`
and `Problem`, which libraries can't name at all yet (they aren't in
`builtins::TYPES`); `Equatable` and `Invalid` are sealed, and `Null`'s
constructor is private, so naming them would enable nothing more.

Statics: `String.fromCodePoint`, `.fromCodePoints` and `.begin`,
`StringIndex.none`, `Float64.e` and `.pi`, and `List.of`.

The host supplies `Boolean`, `Int32`, `Int64`, `Float64`, `String`,
`List`, `ListBuilder`, `Map` and `MapBuilder`, which are `@connected` as
`StringBuilder` and `Promise` were. That changes neither typing nor
output: user code names builtins, not core's classes, and the connected
`is` rule below isn't Temper's yet.

## Gaps and open questions

- **Connected types and `is`.** Kotlin Temper refuses `is` and `as`
  targeting a `@connected` type unless reached through sealed
  supertypes ("Connected types cannot be targeted …"), so `x is
  StringBuilder` for `x: AnyValue` is refused there, and `r is
  DoneResult<T>` for `r: GeneratorResult<T>` allowed. Temper doesn't
  check this yet (notes/kotlin-differences.md, "A user's `@connected`
  type").
- **Map key bounds** (closed by t853). A type argument is checked
  against its bound as a type, not a value: `AnyValue` fits only
  `AnyValue`, though a value of it may hold any other, and a parameter
  without `@out` or `@in` compares its arguments invariantly, so
  `MapKey<String>` isn't a `MapKey<MapKey<String>>` and `Never<Int>`
  isn't a `MapKey<Never<Int>>`. Annotations, as `let m: Map<Boolean,
  Int>`, are checked as calls are (notes/typing.md, "Generic types").
- **`Type`** values stand for types, as macros' inputs. It's a class
  with a private constructor, so it can't be made or extended, and it
  isn't a `MapKey`; `x is Type` is allowed, as in Kotlin, which has no
  rule against it. Whether any backend can tell a `Type` at run time is
  untested.
- **`MapKey`** is `/*sealed*/` in Kotlin, "expected to be
  user-implementable in the future". Users may extend it today, though
  no backend can use such a key. Recommended: make it `sealed` until
  backends support user keys. That would refuse `interface I extends
  MapKey<I>` and `class C extends MapKey<C>` outside core ("Cannot
  extend sealed type MapKey …"), make a `when` over a `MapKey` value
  exhaustive with `Int32` and `String` cases (and any other core class
  that extends it), and leave map keys as they are, since only core's
  types are `MapKey`s then.
- **`Generator`, `SafeGenerator`, `GeneratorFn`, `AnyValue`** may be
  extended, as in Kotlin, but tmpl can't translate a user class that
  does yet ("not yet in tmpl: a builtin supertype"). Recommended: keep
  `Generator` and `SafeGenerator` open; consider refusing
  `GeneratorFn`, a marker for function types.
- **`DoneResult`** keeps the public constructor Kotlin gives it; its
  `public constructor();` only writes that down. `doneResult()` is the
  documented way. Recommended: private, as `Promise`'s.
- **`Pair`, `Deque`, `DenseBitVector`** are host-supplied on most
  backends but not `@connected`: connecting them would make `is`
  refuse them under Kotlin's rule.
- **Private constructors of users' classes** aren't enforced, in Kotlin
  or Temper: `new C()` works outside `C`. Core's rule above covers only
  builtins. Recommended: enforce them, as private methods are, after
  moving core's `emptySingleton` into `Empty`.
