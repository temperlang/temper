# tmpl: types

Part of the [tmpl design](README.md).

## Level 1: Types

Proposed; Level 2, below, has the structure. Nominal types with arguments,
invariant type parameters (typing.md), nullable types, `Void`,
`Never<T>`, and function types that carry their `@fun` interface's name
(Decided, language.md "Function types and fun interfaces"). A function
that may fail has a result type, `T throws Bubble`, as its signature's
result only ([Results](#results)); no expression has one. Classes are
final; only interfaces are extended, and they may carry method bodies.

## Level 2: The type model

The `TypeTable` interns every type, so equal types have equal ids. Its
variants:

- `Nominal { decl, args }`: classes, interfaces (`@fun` ones too), and
  builtin types, which are external declarations from core, linked by
  qname (`Int32`, `String`, `List`, `Map`, `Generator`, `Promise`, …).
- `TypeParam(decl)`, with its bounds on the declaration.
- `Nullable(T)`, never of a nullable `T`.
- `Function { params, optional, result }`: bare function types, kept
  while tests use them; Decided to go in favor of named `@fun`
  interfaces.
- `Void`, `Never(T)` (bottom with its fallback type), and `AnyValue`
  (the top).
- `Result { pass, fail }`: a signature's result where it may fail
  ([Results](#results)).

Relationships, all answered by `analysis` helpers rather than stored
twice: supertypes from each declaration's `extends` with arguments
substituted; subtyping (nominal, invariant arguments, `Nullable` and
`Never` rules); a member's type as seen from a receiver type; and a
`@fun` interface's signature.

Invariants: no inference variables or unknown types (an `Error` node
is typed `Never` of what its place wants); explicit type arguments on
every generic call and `New`; every type an export mentions is
exported or external.

Per-backend type spelling (Java's boxing, Rust's `Option` and `Arc`,
C#'s nullable value types) is the translator's, never a pass's.

## Level 3: Types for subset A

Proposed.

```rust
pub struct TypeId(u32);           // TypeId::NONE (u32::MAX) for statements
pub struct TypeTable {
    data: Vec<TypeData>,          // interned: equal types, equal ids
    lists: Vec<TypeId>,           // TypeListId ranges, for arguments and parameters
}
pub enum TypeData {
    Scalar(Scalar),               // ids 0 to 6, the same in every program
    Function { params: TypeListId, result: TypeId, bubbles: bool },
    // Subset B: Nominal, TypeParam, Nullable, Never; subset C: Result.
}
pub enum Scalar { Void, Boolean, Int32, Int64, Float64, String, AnyValue }
```

- **Scalar builtin types are fixed entries,** not external
  declarations as Level 2 had them: every program has them, they need
  no link key, and text writes them bare (`Int32`, not `Int32#7`).
  Generic builtin types (`List`, `Map`, …) stay external declarations,
  linked by qname, since they have type parameters and members to link
  ([question 26](open-questions.md)).
- **Temper's `Int` is `Int32`,** in text and everywhere else.
- **A function declaration's type is its signature,** a `Function`
  entry: parameter types, result, and whether it may bubble. This
  stays when bare function types go away, since it's only how
  signatures are stored.
- **Subtyping in subset A is equality;** with no classes or nullable
  types, a child fits its role only by having the role's type.
- **Operators have fixed signatures,** in the crate's operator table
  ([analyses.md](analyses.md#level-3-analyses-for-subset-a)), so an
  `Op`'s type comes from its operator. Core's class for a scalar type
  (`core.type Int32`) is still an external declaration, owning its
  members ([nodes.md](nodes.md#level-3-ops-and-core-as-declarations)).

As built (2026-10-29, Proposed), with core's declarations:

- **`Nominal { decl }`,** without type arguments yet: an interned entry
  per class or interface, which is also its declaration's type. Core's
  class for a scalar has the scalar as its type instead, so a
  receiver's type is its method's owner's either way.
- **Signatures say which parameters are optional** (`Signature`'s
  `optional`, kept beside the parameter types), so a call checks
  `Omitted` against its callee's signature alone, external callees
  included, which have no parameter declarations. A parameter
  declaration's `OPTIONAL` fact must agree (T1).
- **Subtyping is still equality:** an interface's implementers come
  with subset B's classes.

As built with nullable types (2026-10-30, Proposed):

- **`Nullable(T)`,** interned once per `T` (`TypeTable::nullable`, which
  gives `T?` back for a nullable `T`, and `is_nullable`, `non_null`, and
  `nullable_of`). Subtyping stays explicit: `T` goes where `T?` is
  wanted only through `AsNullable`, and `T?` where `T` is wanted only
  through `NonNull` or `As` ([nodes.md](nodes.md)).

As built with generics (2026-10-31, Proposed):

- **`Nominal { decl, args }`:** a class's or interface's type with its
  type arguments, interned by both; a generic one's own type is it
  applied to its type parameters (`Box#4<T#5>`), and text always writes
  the arguments. `TypeParam { decl }` is a type parameter, declared
  (`DeclKind::TypeParam`) and owned by its generic function, method,
  class, or interface, which lists them in order
  (`DeclTable::type_params`); a type parameter's supertypes are its
  bounds.
- **Calls carry their type arguments** (`TypeArgs`, interned lists, in a
  call's `b` slot). A call's type, a field read's, and its arguments'
  expected types are the declaration's with the type parameters put in,
  from the type arguments and from the receiver's type seen as the
  member's owner (`analysis::bindings`, `analysis::as_super`), so the
  local rules still give every type.
- **Subtyping:** a class's or interface's type is a supertype's through
  its declared supertypes with type arguments put in (invariant
  arguments); a type parameter is what its bounds are. A receiver of a
  generic owner's member may be any subtype of the owner (`List<T>` for
  `Listed.isEmpty`), with no `Upcast`, unlike a non-generic owner's.
- **Core's generic types** (`Listed`, `List`, `ListBuilder`, `Pair`,
  `MapKey`, `Mapped`, `Map`, and `MapBuilder`) are external
  declarations in the core fixture, with their members that take no
  function types; `ListLit` makes a `List` of core's.


**Function values (As built, 2026-10-31, Proposed):**

- **A `@fun` interface is a `Nominal` type** of an interface marked
  `FUN` (text `fun interface`), whose one member is its abstract `apply`
  method: strictly nominal, as the frontend decided (language.md,
  "Function types and fun interfaces"). Its values are functions; the
  signature they have is `apply`'s with the interface's type arguments
  put in (`analysis::fun_signature`, `Builder::fun_signature`, which
  interns it; `Builder::finish` interns every `@fun` interface type's,
  so analyses and backends find each without interning).
- **A bare function type is a `Function` entry,** as a declaration's
  signature is, now also a value's type: a parameter's, a local's, a
  field's, a literal's. Core's members that take functions (`map`,
  `filter`, `join`, `sorted`, `reduce`, `reduceFrom`, `ListBuilder.sort`,
  `Mapped.forEach`) take them, as core.temper declares.
- **No implicit conversions between them:** `ToFun` takes a function
  type's value to a `@fun` interface of the same signature, `FromFun`
  the reverse; a literal or reference takes the type its place wants
  instead (`Builder::retype`). Every backend so far writes both as one
  type, so the conversions write nothing (Java adapts by a method
  reference where the interfaces differ).

**Parameters' defaults (As built, 2026-11-01, Proposed):** a parameter
of the program's own with a default is optional and typed `T?`, in its
declaration and its signature alike, since null means its default
([nodes.md](nodes.md), "Subset B's next step: parameters' defaults");
callers pass `T` through `AsNullable`. A function type keeps that too,
as a reference to such a function has it (`fn (Int32, _?: Int32?):
String`). Core's members keep their fixture's types (`toString(radix?:
Int32)`), whose connections apply their defaults.

**Core's other types (As built, 2026-11-02, Proposed):** a builtin type
that isn't one of tmpl's scalars is `Nominal` of core's declaration in
the core fixture: `StringIndex` and `NoStringIndex`, classes under the
sealed interface `StringIndexOption`; `StringBuilder`; `Empty`;
`DenseBitVector`; and `Deque<T>`, as `List<T>` is. Backends write each
as Kotlin's do: string indices as integers in most languages and
`usize`, `Option<usize>`, and `()` in Rust, a builder as the language's
own, and `Empty` as its unit ([nodes.md](nodes.md), "Subset B's next
step: core's other types"; [profiles.md](profiles.md), "As built, core's
other types").

**Types as values (As built, 2026-11-05, Proposed):** Temper's `Type`
is the scalar `Type`, and `TypeValue { of }` (text `typeValue(Void)`)
is a type as a value, as `whatever(): Type { …; Void }` in
`classes/angle-call` returns. A scalar, not core's `Nominal`, since it's
final, has no members, and has no declaration to link. `to_tmpl` writes
one only for a builtin scalar other than `AnyValue` so far; the
program's own classes, generic types, and `AnyValue` are not yet. It
isn't an `AnyValue` yet, since no backend boxes one. Each backend writes
its language's own value: `type(None)` and `int` in Python, `Void.class`
as a `Class<?>` in Java, `typeof(void)` as a `Type` in C#,
`TypeId::of::<()>()` in Rust, and `std::type_index(typeid(void))` in
C++. JavaScript and Lua have no value for a builtin type, so they write
its `typeof` or `type()` name, as `"undefined"` and `"nil"`, typed
`string` (kotlin-differences.md, "Types as values").

## Results

As built (2026-11-23, Proposed): **`Result { pass, fail }`** (text `Int32
throws Bubble`), interned once per pair (`TypeTable::result`, which gives
`pass` back for a `Never` failure), is a signature's result where it may
fail, as the frontend's result types are (typing.md, "Results").
It fails with `Scalar::Bubble`, a failure type (a class or interface
below core's `Error`), or a type parameter ([nodes.md](nodes.md),
"Declared failures").

- **Only a signature's result** (T5): no declaration, parameter, or node
  has a result type or `Bubble`.
- **Calls see the pass type:** `Signature` gives `pass` (a call node's
  type), `fail`, and `returns`, the whole result, so translators read
  `sig.pass` and `sig.bubbles()` and never spell a result type.
- **Part by part** in substitution, `mentions`, `same`, and lowering
  (`nevers`), so a failure type parameter can be an ordinary one later.
- `MAY_BUBBLE` on calls and an operator's `bubbles` stay flags; what a
  node fails with is its callee's failure type, with type arguments put
  in, or `Bubble` (`failures::node_failure`).

## Never

As built (2026-11-17, Proposed): **`Never(T)`** (text `Never<Int32>`),
interned once per `T` (`TypeTable::never`), which is neither nullable nor
a `Never`: the result of a function or method that never returns
normally, as the frontend's `Never<T>` declares it (core's docs), and the
type of each call of one. It's only a result or a value's type, never a
local's, parameter's, or field's (T5), and `Never<T>?` is `T?`
(`TypeTable::nullable`), since only null comes from it.

- **It fits where `T` does** (T3; `TypeTable::fallback`), as a call's
  value returned from a function whose result is `T`, and converts as
  `T` (`AsNullable`, `Upcast`).
- **Control doesn't go on past it** (F1, R5), as after `panic()`: a
  local assigned on every other path is assigned after an `if` whose
  branch calls one, and a function's body may end with one.
- **Overrides match exactly,** as the frontend has them: a method's
  `Never<T>` overrides only a `Never<T>`, so every target's override has
  the signature its language requires.

**Lowering** (`nevers::lower`, by the profile's `NEVER`): a target with
a bottom type keeps `Never<T>` as the result of a function or method
that overrides nothing and that nothing overrides, and that isn't read
as a value (`nevers::may_keep`), and writes it as its own: Python's
`NoReturn`, JS's `never` in JSDoc. Everywhere else, and everywhere for
the other targets, it becomes `T`, and a call that ends its block as a
statement gets a `panic()` after it, so the target's compiler sees that
control stops there (Java's definite assignment, Rust's, a function's
end). A target whose bottom type fits fewer places picks its own subset
of those (`nevers::lower_where`).
