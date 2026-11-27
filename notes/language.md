# Language decisions

Temper language semantics that tempest's design depends on, as decided
with the team or proposed, moved here from design.md so the
architecture notes stay about architecture. The language itself is
documented at [temperlang.dev](https://temperlang.dev/); typing's rules
are in [typing.md](typing.md).

## Function types and fun interfaces

Fun interfaces (`@fun interface F(params): R;`) are meant to replace bare
function types: each function type gets a name in the source, and backends
use it as they can. Backends with function types (JS, Python, Rust, C++,
Lua, likely C#) emit plain function types and lose nothing; backends that
need names (Java, and C# if we choose) emit the named interface rather
than a synthetic one. This follows a general rule: Temper makes it easy to
give things unique names, and backends that allow more flexibility use
less of it.

So tmpl should carry the interface's name on function types, and the
interpreter treats a fun interface value as the function itself (as it
does now). Kotlin Temper hasn't finished this plumbing, and bare function
types still exist there, so for now tempest accepts both and stays
compatible with the functional tests; don't wire fun interfaces as mere
sugar for `apply` methods in ways that would make this harder later.

What Kotlin Temper does, as a hint at the non-sugar meaning (2026-10-24):

- **Declaration.** `@fun interface F<T>(x: T): R;` is an abstract
  interface marked `@functionalInterface`, with one public method `apply`
  and no `this` parameter ("arguably this is the function"). Its type
  category is *Functional*, and its signature is `apply`'s, with the
  interface's type arguments substituted (`sigForFunInterfaceType`).
- **Old function types become fun interfaces.** The newer type system
  (`type2`) represents each bare `fn (A): R` as a synthesized fun
  interface `Fn<I…, O>`, one per shape (count of required and optional
  inputs, type formals, whether it can fail), with contravariant inputs,
  a covariant output and a `Function` supertype. So structurally, a
  function type is just an anonymous fun interface, which is the direction
  the language is going.
- **Calls and values.** Anything whose type is a fun interface is
  callable: the typer finds callees through `apply`'s signature. tmpl
  marks the crossing between function values and fun interface values
  (`FunInterfaceExpression`, `FunInterfaceCallable`), and backends that
  use function types just drop it.
- **Per backend:** `FunctionTypeStrategy` is `ToFunctionType` (no
  interface emitted, uses become plain function types, as in C++'s
  `std::function` from the `apply` signature) or `ToFunctionalInterface`
  (emit the interface with its `apply` method). Every real backend uses
  `ToFunctionType` today, Java included, with a TODO there to rework it to
  use `@fun` interfaces, and Java's `FunInterfaceExpression` is a `TODO()`.
  That's the unfinished plumbing.

For tempest: a fun interface is a nominal type whose meaning is a
function signature. Keep the name through tmpl, treat values as
functions, and let each backend pick a function type or the named
interface.

Decided and done 2026-10-29, before tmpl: named `@fun` interfaces are
strictly nominal, a deliberate difference from Kotlin. A `Pred` isn't a
`Check` even where their signatures match, and the error suggests the
adapter, `fn (y) { p(y) }`; bare function types stay structural, to
match the tests, until they're removed. Function literals and trailing
blocks take their interface from the type wanted, as their type, and so
do references to declared functions and methods, as Java's method
references do (`keep(isPos)` passes `isPos` as a `Pred`), so tmpl can
see where a function becomes an interface's value. Adapting between
interfaces is explicit, `let c: Check = fn (y) { p(y) };`, and backends
may later collapse such wrappers where identity isn't observed. A
function literal that initializes a `let` without a type is an error for
now. Kotlin's documented rules for declaring one are checked, so each
backend may pick a named interface or a plain function type: only
`AnyValue` as a supertype, one `apply` and no other members, and no `is`
or `as` (typing.md, "Fun interfaces").

## Object literals, not named arguments

Temper is dropping named arguments everywhere except object literals
(property bags), because named arguments don't work well in every
language. In C#, for example, callers' named arguments make parameter names
part of the API, and a name we'd have to escape can break backward
compatibility. Where names really help, the idiom is a small helper class
built with an object literal.

- **What exists:** `{ a: 1, b }` and `{ class: C, a: 1 }` construct a
  class. They call its constructor, maybe `@factory` static methods later.
  Nothing else takes arguments by name, and tempest needn't support named
  arguments otherwise. If a classic Temper test uses them beyond object
  literals, change the test there. As of 2026-10-24 none does:
  `functions/named-args` was already rewritten to use object literals.
- **No first-class symbols.** It's not settled that Temper keeps
  first-class symbols at all, so don't build on them. The parser's `\a`
  markers may stay a parse-time detail of the object-literal conversion.
  After that, the tree and tmpl should have a constructor call with its
  arguments already placed (and a property-name map only if a backend
  needs one), never symbol values.
- **Which class:** target typing first, once static typing is in
  (milestone 4), before tmpl; until then, find by names only. Where the context says what type
  is expected, the literal constructs that class: a declared variable,
  field or return type, a parameter of a known callee, or a list element
  type. Kotlin Temper doesn't do this yet; tempest should, with its own
  tests (classic Temper should follow). Without an expected type, or as a
  fallback, Kotlin's `ConvertObjectLiteralNew` rule applies: the one class
  in scope whose constructor takes every given name. If the expected type
  is an interface, it narrows that search to classes implementing it.
  The user would like to drop find-by-matching-names entirely, but that
  needs a team decision, so keep it for now behind target typing.
- **When the class is decided** (decided 2026-10-29): not until the
  literal's expected type is known, or known to be missing, so not while
  macros expand, as Kotlin decides it. Matching the given names against
  constructors waits until then too, so both answers are known together,
  and where they disagree (the expected type's class doesn't take the
  names another class would match), that's an error, not a silent pick.
  A literal with an explicit `class: C` may settle as soon as `C` is
  known. Meanwhile, the literal's values may settle wherever they don't
  need the class's types (a value that needs its property's type, such as
  a nested object literal or `[]`, waits with it). Only classes'
  constructors count for now; maybe `@factory` static methods later.
  This is a case of macros waiting for types ("Macros must be able to
  wait for types", above). Done (2026-10-29); the rules are in
  typing.md, "Object literals and target typing". Where the names fit
  only a class other than the one wanted, the error is "A is wanted
  here, but these properties fit Mark's constructor".
- **Target typing beyond object literals:** the same expected types should
  type list literals (`let xs: List<Point> = []`, and object literals as
  their elements) and function literals (parameter and return types from
  an expected function type or fun interface, as Kotlin already does for
  callees' parameters, such as `sorted { a, b => … }`), wherever the
  context gives one.
- **Backends:** languages with good named arguments or object literals use
  them (JS and Python options, maybe C#, with the escaping caveat above).
  Languages without them get a builder when a constructor has more than
  one property: Java's nested `Builder` class, and Rust's `…Builder` /
  `…Options` structs, as Kotlin's `classBuilder` and
  `processForClassBuilder` do. That's decided in shared code, once.
  Every backend needs one way to build with named properties
  (2026-11-02). Kotlin Temper has them for Python (keywords), C# (named
  arguments), JS (`Rect.new({ w: 1, h: 2 })`, a static method taking a
  props object), Java, and Rust, but not for Lua or C++. Tempest should
  match those and add (Proposed) a table in Lua (`Rect.new{ w = 1, h = 2 }`,
  the language's own idiom, if the name is free) and a builder in C++,
  since C++14 has no designated initializers.
