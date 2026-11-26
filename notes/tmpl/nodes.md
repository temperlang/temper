# tmpl: nodes

Part of the [tmpl design](README.md).

## Level 1: Errors stay values

Unresolved code arrives as a typed `Error` node with its message, as in
design.md section 6; every backend turns it into a run-time failure, as
`bubble()`'s, which an `orelse` catches. The validator treats it as
valid in any profile, and as never completing. `to_tmpl` makes one of
each error node, value that doesn't fit its place, call of an
undeclared name or of a member that isn't there, and `let` or
assignment of one (as a statement), and drops what follows it in a
block. A type an error leaves unknown, as an undeclared type's name,
is `AnyValue`, so the declaration that has it is still written.

A type-only error (code that's ill-typed but runnable, as a value that
doesn't fit its place) is an `Error` that keeps the value as written,
of its own type, and nothing around it is dropped. The shared pass
`temper_tmpl::errors::settle` settles it by the backend's profile: with
`RUNS_TYPE_ERRORS` (dynamically typed targets), it's its value, so the
code runs as written; without, a plain `Error`, and what can't run after
it goes, as above, so statically typed targets fail there.

## Level 2: Node families

Every node has a kind, a type, a span, a range of children, and maybe
a payload (an id, a literal, an operator). Kinds are grouped into families;
each family has a default structural walk, so a pass touches only the
kinds it changes. The schema says, for each kind, its family, its
children's roles, and the profile feature it needs, if any, so the
validator's "allowed in this profile" rule is generic.

**Declarations** (each defines one `DeclId`): `Module`, `Class`,
`Interface`, `Function` (top-level or local), `Method`, `Constructor`,
`Getter`, `Setter`, `Field`, `ModuleLet` (with `var` marked), `Param`,
`TypeParam`, and `Test`. A connected declaration's body, if any, is
its pure Temper fallback ([names.md](names.md#level-3-connected-declarations)). Locals
and labels are declared by the statements below.

**Statements:**

- Binding: `Let` (local, maybe `var`, maybe without an initializer).
- Writes: `Assign` to a local, `SetField`, and `SetModuleLet`
  ([modules.md](modules.md): module `var`s have their own read and write).
  Statics are only initialized, by `InitStatic`; Temper never assigns one.
- Control: `Block`, `If`, `While`, `DoWhile`, `ForOf` (Decided
  2026-10-29: a real loop, so `yield` and `await` in its body can work
  later; backends may map it to native loops or `forEach`),
  `Labeled`, `Break` and `Continue` (naming a label's declaration),
  `Return`, and `ExprStatement`.
- `When`, as the frontend settles it: cases on values or on types.

**Expressions:**

- Literals: Boolean, `Int32`, `Int64`, `Float64`, String, `Null` (typed
  by its place), and `Void`.
- Reads: `Local`, `This`, `GetField`, `GetStatic`, `GetModuleVar`, and
  `FunctionRef` (a declared function or method as a value, maybe
  bound to a receiver).
- Calls: `CallFunction` and `CallMethod` (a `DeclId` callee; a method
  call says whether it's virtual), `CallValue` (a function value or a
  `@fun` interface's value), `New` (a class, its type arguments, and
  placed arguments; object literals arrive as this), and `Op` (an
  operator on builtin types, and typed operands). Core's and std's
  members are ordinary calls of external declarations, never `Op`s
  (Proposed 2026-10-29, [below](#level-3-ops-and-core-as-declarations)).
  Arguments for omitted optional parameters are an explicit `Omitted`,
  since defaults apply inside the callee.
- Equality and comparison, below.
- Conversions ([overview.md](overview.md#fully-explicit)): `Upcast`, `ToFun` and `FromFun`, `AsNullable`,
  `NonNull` (checked), and `FromNever`; and run-time checks `Is` and
  `As` (which panics where it fails).
- Functions: `FunctionLiteral`, with its parameters, captures, and
  body, typed by its `@fun` interface or bare function type (as built,
  2026-10-31: "Subset B's next step: function values", below).
- Control expressions: `IfValue`, `BlockValue`, `And`, `Or`, `OrElse`
  (a body and a fallback), `Bubble`, and `Panic`.
- Generators and async: `Yield` and `Await`, in functions marked
  generator or `async`.
- `Error`, valid anywhere (Level 1, above).

**Lowered families,** which never appear in starting tmpl, each
introduced by one pass:

- Exceptions: `Try` (a body and a handler) and `Throw`.
- Results: the `Result<T>` builtin type for lowered signatures; `Ok`,
  `Fail`, `Propagate` (Rust's `?`), and `ResultOr` (a single bubbling
  call's `orelse`); a general `orelse` becomes a labeled block whose
  checks break to the fallback.
- Statements from expressions: compiler-made `Let`s with role hints;
  no new kinds.
- Cells for assigned captures: `NewCell`, `GetCell`, and `SetCell`.
- Control: `Switch` on integers or strings (already where the profile
  has it), `Goto` and `Label` (Lua's `continue`, C#'s labeled break),
  and the state machine's `Dispatch` (a computed jump).
- Module init: `EnsureInit(module)` at entry points, and
  `SyncGetModuleVar` and `SyncSetModuleVar` for threads.
- Named function types: synthesized `@fun` interface declarations; no
  new kinds.

**Invariants** the validator checks:

- Every expression has a type, and every child's type fits its role
  (an argument's its parameter's, maybe through an explicit
  conversion; there are no implicit ones).
- Every reference names a declaration in scope or an external one.
  Every `Break` and `Continue` names an enclosing label. Every
  may-bubble call is marked and has a handler; only marked calls may
  bubble.
- Children are in evaluation order. A node has one parent: the tree is
  a tree, not a DAG.
- Starting tmpl has no lowered-family node; a lowered program has only
  kinds its profile allows.
- Stored facts (captures, may-bubble marks, the init list) match their
  recomputation.

### Equality

**Decided (2026-10-29):** Temper's `==` is always type-specific, so tmpl
has no generic `==` node:

- **A class's or interface's `==`** is a `CallMethod` of its method
  marked `@operator("==")`, its own or the nearest inherited one,
  virtual as any method call. A class with `@auto("==")` and no such
  method gets a synthesized `eq` declaration, marked `@auto("==")` and
  compiler-made, whose body compares each property with its own type's
  equality, as Kotlin's `AddAutoMembers` does.
- **Each builtin type's equality is its own operator:** `Boolean.eq`,
  `Int32.eq`, `Int64.eq`, `Float64.eq`, and `String.eq`.
- **A comparison with `null`** is `IsNull` (or `IsNotNull`), never a
  call.
- **Identity** is `Identical(a, b)`, only for class and interface
  types, and only where the source asks for it (Decided 2026-10-29,
  [question 12](open-questions.md)). Backends
  map it to `is` (Python), `===` (JS), `ReferenceEquals` (C#), `==` on
  references (Java), `Arc::ptr_eq` (Rust), pointer comparison (C++), and
  `rawequal` (Lua).
- **`!=`** is `Op(Boolean.not)` around the equality, which a
  translator may print as `!=`.
- **Nullable on both sides** (Decided 2026-10-29, [question 15](open-questions.md)): `EqNullable(a, b)`
  names the equality for the non-null values (an `Op` or a
  method) and means `IsNull(a) ? IsNull(b) : !IsNull(b) && eq(a!, b!)`.
  A pass expands it where the profile says the target's own `==` isn't
  null-safe.
- **Comparisons** (Proposed, [question 16](open-questions.md)): builtin types have their own
  operators (`String.lt`, `Int32.cmp`); a class's `a < b` is
  `Op(Int32.lt, CallMethod(compareTo), 0)` with the method marked
  `@operator("<=>")`.

The method keeps its own name (`eq`, `compareTo`); whether a backend
also presents it as its host's equality protocol is a surface question
([surface.md](surface.md)).

**Gaps in tempest's typing today** (typing.md, "Operators defined in
Temper"; ../history/milestone-4.md, "Operators defined by methods"), for slice 7:

- **Not every `==` is resolved.** Typing checks a class's operator
  method only for a non-null first operand of a class or interface it
  knows. A generic `T`, `AnyValue`, or function operand, or a class
  with an unknown supertype, is neither checked nor resolved (as in
  Kotlin), and the interpreter then compares by identity: the generic
  identity `==` this design removes. Each must become a resolved call or an error
  (Decided 2026-10-29, [question 13](open-questions.md)) before `to_tmpl`
  can translate it.
- **Done:** typing records the choice (`Typing::resolved`, 2026-10-29),
  the frontend generates `@auto("==")`'s `eq` as a real member, as
  Kotlin's `AddAutoMembers` does (2026-10-30; ../history/milestone-4.md,
  "`@auto("==")` members generated"), and the interpreter calls it, so
  each field's own `==` applies.
- **Identity, for now:** `to_tmpl` translates a class's or interface's
  `==` with no method to call as `Identical`, where typing notes it
  (`Typing::identity_eq`); Kotlin's typing rejects most of these
  already, and the rest are to become errors (question 13).
- **Nullable operands:** typing skips `==` with a nullable side, as
  Kotlin does; `to_tmpl` must produce `IsNull` or `EqNullable` from
  the operands' types.
- **No identity operation** exists in Temper, the interpreter, or
  typing ([question 12](open-questions.md)).

## Level 3: Each node's fields and invariants

Subset A (basics) first; everything here is Proposed unless marked
Decided. [roadmap.md](roadmap.md#level-3-after-subset-a) sketches what
subsets B, C, and D add.

### The node record

Nodes live in columns, one entry per `NodeId` (a `u32`):

```rust
pub struct Nodes {
    kind: Vec<Kind>,          // u8, from the schema
    ty: Vec<TypeId>,          // TypeId::NONE for statements and declarations
    span: Vec<Span>,          // "Source positions", below
    kids: Vec<(u32, u32)>,    // start and length in `kid_list`
    a: Vec<u32>,              // payload slots; the schema names each
    b: Vec<u32>,              //   kind's use of them
    flags: Vec<Flags>,        // u8: MAY_BUBBLE, VAR, …
    kid_list: Vec<NodeId>,
}
```

The builder appends a node after its children, so a child's id is
always lower than its parent's. Payloads are ids (`DeclId`, `StrId`,
`Op`, `TypeListId`) or literal bits; `0` in `b` means none.

As built (2026-10-29, Proposed): `var` is a fact on the declaration
only, not a node flag, so the `VAR` flags in the tables below are the
declaration's; a node's flags are `MAY_BUBBLE` and `DERIVED` (its
span's bit). Type arguments in `b` wait for subset B's generics. The
schema's table is `crates/temper-tmpl/src/schema.rs`.

### Subset A's kinds

Declarations, each defining the `DeclId` in `a`:

| Kind | Children | Payload and flags |
|---|---|---|
| `Function` | `Param`s, then the body `Block` | `a`: the function |
| `Param` | none (a default is the body's first statements: "Subset B's next step: parameters' defaults", below) | `a`: the parameter |
| `ModuleLet` | none; its initializer is an `InitLet` | `a`: the module `let`; `VAR` |
| `BodilessFunction` | `Param`s | `a`: a connected function without a body (built 2026-10-29) |

Statements (no type):

| Kind | Children | Payload and flags |
|---|---|---|
| `Block` | statements | |
| `Let` | the initializer, or none | `a`: the local; `VAR` |
| `Assign` | the value | `a`: a local |
| `SetModuleLet` | the value | `a`: a module `var` |
| `InitLet` | the value | `a`: a module `let`; only in an init block |
| `If` | condition, then `Block`, maybe else `Block` | |
| `While` | condition, body `Block` | `a`: its label |
| `DoWhile` | body `Block`, condition | `a`: its label |
| `Break`, `Continue` | none | `a`: a loop's label |
| `Return` | the value, or none in a `Void` function | |
| `ExprStatement` | an expression | |

Expressions, with where their type comes from (the local rules are
[formats.md](formats.md#level-1-formats)'s, plus [question 17](open-questions.md)'s fourth):

| Kind | Children | Payload and flags | Type |
|---|---|---|---|
| `BoolLit` | none | `a`: 0 or 1 | `Boolean` |
| `Int32Lit` | none | `a`: the bits | `Int32` |
| `Int64Lit`, `Float64Lit` | none | `a`, `b`: low and high bits | `Int64`, `Float64` |
| `StringLit` | none | `a`: a `StrId` | `String` |
| `VoidLit` | none | | `Void` |
| `Local` | none | `a`: a local or parameter | its declaration's |
| `GetModuleLet` | none | `a`: a module `let` | its declaration's |
| `CallFunction` | arguments | `a`: the function; `b`: type arguments; `MAY_BUBBLE` | its signature's result |
| `Op` | operands | `a`: the operator; `MAY_BUBBLE` | its operator's result |
| `CallMethod` | receiver, then arguments | `a`: a method or getter; `MAY_BUBBLE` | its signature's result |
| `Omitted` | none | | its parameter's |
| `And`, `Or` | left, right | | `Boolean` |
| `IfValue` | condition, then, else | | written |
| `BlockValue` | statements, then the value | | written |
| `Error` | none | `a`: the message | written |

Changes from Level 2's names: module `let`s, `var` or not, are read by
`GetModuleLet` and written by `SetModuleLet` (the declaration says
`var`, and only a `var` is written); every loop declares a label,
compiler-made (`~loop`) when the source has none, so `Break` and
`Continue` always name one; and `Error` is typed as its place wants,
not `Never` of it ([questions 28, 29, and 27](open-questions.md)).

Invariants, per kind (the validator's rule codes are below):

- **`Let`** declares a local owned by the enclosing function; its
  scope is the rest of the enclosing `Block`. Without an initializer,
  it's definitely assigned before each read; without `VAR`, at most
  once on any path.
- **`Assign`** targets a local of the same function, `VAR` or not yet
  assigned (subset A has no captures), or a parameter marked `VAR`
  (As built, 2026-10-30: R2 accepts one, and R6 rejects assigning a
  parameter that isn't `VAR`).
- **Conditions** are `Boolean`.
- **`Break` and `Continue`** name an enclosing loop of the same
  function.
- **`Return`** has a value exactly when the function's result isn't
  `Void`, and the value's type is the result. A function whose result
  isn't `Void` never reaches the end of its body.
- **Calls** have one argument per parameter, each of exactly the
  parameter's type after substituting the type arguments (subset A has
  no conversions, since it has no subtypes), or `Omitted` for an
  optional one. `MAY_BUBBLE` is set exactly when the callee may bubble
  (no operator does: integer division by zero panics).
- **Bubbling:** in subset A, a call that may bubble has no handler
  (`orelse` comes with B), so its function must be marked may-bubble.
  In an init block, it fails the module's initialization
  ([question 35](open-questions.md)).
- **`IfValue`'s branches** and **`BlockValue`'s value** have the
  node's own type.
- **`Error`** is valid anywhere, with any type (Level 1, above).

### Source positions

**Decided (2026-10-29):** source positions are carried, for source
maps and similar tools; backends emit source maps from them. Proposed
content:

```rust
pub struct Span { pub file: FileId, pub start: u32, pub end: u32 } // bytes, end exclusive
pub struct SourceFile {
    pub path: StrId,              // relative to the library's root
    pub hash: [u8; 16],           // of the text, to match it later
    pub line_starts: Vec<u32>,    // byte offsets, for lines and columns
    pub wide_lines: Vec<(u32, StrId)>, // text of lines with non-ASCII characters
}
```

- **Every node and declaration has a span.** A declaration has two:
  the whole declaration, and its name (for a source map's `names` and
  for tools such as go-to-definition).
- **Each node's span is its own or derived,** one bit per node.
  Derived marks a node that the frontend or a pass made, which points
  at the source construct that caused it.
- **Columns:** source maps count UTF-16 units, so a backend converts
  byte columns using `wide_lines`; ASCII lines need no text, and the
  sources needn't be present ([question 34](open-questions.md)).
- **Unknown spans** come only from reading text without its spans
  section; such a program is marked as having no spans, and backends
  then write no source map.

What each node points at:

1. **The frontend:** each node takes the span of the settled node it
   translates. Nodes it synthesizes are derived and take the span of
   what asked for them: an `@auto("==")` method's body takes the
   annotation's span, a constructor's field assignments their
   parameters', and a loop's compiler-made label the loop's.
2. **Passes:** a node a pass keeps or rebuilds keeps its span and bit.
   A node a pass adds is derived and takes the span of the node it
   stands for: a temporary's declaration, assignments, and reads take
   the hoisted expression's; a `Try` and its handler take the
   `orelse`'s; a result check takes its call's; cell operations take
   the variable use they replace; a state machine's dispatch and state
   take the function's; and an init guard takes the declaration it
   guards.
3. **Backends:** the output tree copies each node's span; the printer
   records a mapping on entering a node whose span differs from its
   parent's, and a declaration's name with its source name. Derived
   spans map too, so stepping through a temporary lands on the
   expression it holds; a variant may leave them out.

As built (2026-10-30), for JS: a source file's line table comes from
its text in `to_tmpl` (`Builder::set_file_text`); text read back has
none, so it maps nothing until the reader finds the sources. Wide
lines keep their text as a `String`, as `path` does. Rather than an
output tree that copies every node's span, the JS tree carries spans
on statements and functions' names only, and its printer writes into
`source_map::MappedText`, which records the byte offset where each
span's output starts; `source_map::source_map` turns those into lines
and UTF-16 columns. Mapped so far: statements (and so `else if`),
functions' JSDoc, `function`, and names (with the source name in
`names`). Maps leave out `sourcesContent`.

### Subset B's first step: classes (As built, 2026-10-30, Proposed)

Declarations, module items and their members:

| Kind | Children | Payload |
|---|---|---|
| `Class`, `Interface` | members: `Field`, `StaticField`, `Method`, `AbstractMethod`, or static `Function` or connected `BodilessFunction` | `a`: the class or interface; its supertypes, interfaces only, are on its declaration (`DeclTable::supers`) |
| `Field` | none | `a`: an instance field of a class; `VAR` on the declaration |
| `StaticField` | none; its initializer is an `InitStatic` | `a`: a static field |
| `Method` | `Param`s, then the body `Block` | `a`: a method, getter, setter, or constructor, by its declaration's kind |
| `AbstractMethod` | `Param`s | `a`: an interface's method, getter, or setter without a body, or a class's connected method (2026-11-03) |

Statements: `SetField` (`a`: a field; the object, then the value) and
`InitStatic` (`a`: a static field; the value; only in its module's init
block). Expressions:

| Kind | Children | Payload and flags | Type |
|---|---|---|---|
| `This` | none | | written: the member's class or interface |
| `GetField` | the object | `a`: a field | its declaration's |
| `GetStatic` | none | `a`: a static field | its declaration's |
| `New` | arguments | `a`: a constructor; `MAY_BUBBLE` | its signature's result, the class |
| `Upcast`, `Downcast` | the value | | written: a proper supertype, or (after an `is` check, unchecked) subtype |
| `As` | the value | `MAY_BUBBLE`, always | written: checked when run, by `Is`'s rule (C3), or the value's type without null |
| `Is` | the value, not null | `a`: a class's or interface's type | `Boolean` |
| `Identical` | left, right | | `Boolean` |

Declarations: `Field`, `StaticField`, `Constructor` (named
`constructor`, as Kotlin's, its signature's result the class), and
`Setter` (`fn (T): Void`) join `Method` and `Getter`; facts `PRIVATE`,
`PROTECTED`, `OPERATOR_EQ`, `OPERATOR_CMP`, and `AUTO` (made by
`@auto("==")`, with a source name all the same, since it's API).

- **Constructors are always explicit:** a class without its own gets
  one from its header, as Kotlin's implied constructor: its parameters
  are the header's fields, and its body sets them, then the other
  fields' initializers, in order, then (with its own) its body, as the
  interpreter runs them.
- **Members read and written:** a field by `GetField` and `SetField`, a
  getter or setter by `CallMethod`, a static field by `GetStatic` and
  `InitStatic`, a static function by `CallFunction`. A bare member name
  in a class means `this`'s. An interface's abstract property `x: T` is
  an abstract getter, and setter if `var`, which a class's field or
  accessors implement.
- **Subtyping is explicit:** a value goes where a supertype is wanted
  only through `Upcast`, a receiver too, and a local that an `is`
  check narrowed is read through `Downcast`. `as` is `As`.
- **toString:** `analysis::is_plain_to_string` decides, once, which
  method is a class's display string (Kotlin's `isPlainToString`).

Validator rules (C):

- **C1:** fields only in classes, bodiless methods only in interfaces,
  a constructor's result its class, a setter's one parameter and
  `Void` result, supertypes only interfaces, and `this` only in an
  instance member, typed as its class or interface.
- **C2:** a private member is used only in its class (on any instance
  of it, matching Kotlin's PR), a protected one only there and below.
- **C3:** a field assigned only if `var`, or on `this` in its class's
  constructor; a static field initialized only in its own module's
  init block; `Upcast` and `Downcast` only up or down the supertypes;
  `Is` and `As`, one rule: to a class or interface above or below the
  value's type (`As` also from `T?` to `T`), and a host type (core's
  but `Error`, or connected), which a backend may represent by a type
  that isn't distinct, only from a class or interface it extends, as
  `StringIndex` from `StringIndexOption`; `Identical` only on classes
  and interfaces.

Not yet checked: that a class implements every abstract member it
inherits (the frontend reports it), and statics read before their
initializer.

### Subset B's next step: nullable types, `orelse`, `bubble()`, and `panic()` (As built, 2026-10-30, Proposed)

Types gain `Nullable(T)` (`T?`, never of a nullable `T`;
[types.md](types.md)). Expressions:

| Kind | Children | Payload and flags | Type |
|---|---|---|---|
| `Null` | none | | written: the nullable type its place wants |
| `IsNull` | the value, nullable | | `Boolean`; `x != null` is `Op(Boolean.not, IsNull(x))` |
| `NonNull` | the value, `T?` | | written: `T`; unchecked, where flow facts (a null check) prove it isn't null |
| `AsNullable` | the value, `T` | | written: `T?` |
| `EqNullable` | left, right, both `T?` | `a`: `T`'s equality `Op` | `Boolean`: true if both are null, false if one is, else the `Op` |
| `OrElse` | the body, then the fallback | | written: the fallback's value if the body bubbles |
| `Bubble` | none | `MAY_BUBBLE`, always | written: as its place wants |
| `Panic` | none | | written: as its place wants; no `OrElse` catches it |

- **`??`** has no node: `to_tmpl` writes it as a `BlockValue` with a
  `let`, an `IfValue` on `IsNull`, and `NonNull`.
- **Calls in an `OrElse`'s body may bubble** wherever the `OrElse` is
  (F4). One typed `Void`, where it stands as a statement, drops its
  parts' values, whatever their types. Bodies may be `BlockValue`s, as
  `do { x as String; 1 } orelse 0`.
- **`As`** may also take `T?` to `T` (a checked null check, which
  bubbles if null), or `I?` to a class `C`, checking null first.
- **Text:** `(null): T?`, `isNull(e)`, `(nonNull(e)): T`,
  `(asNullable(e)): T?`, `eqNullable(%String.eq, a, b)`,
  `(orelse(body, fallback)): T`, `(bubble()^): T`, `(panic()): T`; types
  `Int32?` and `Node#1?`.

Validator rule N1: `Null` is typed nullable; `IsNull` and `NonNull` take
only nullable values; `NonNull` goes from `T?` to `T` and `AsNullable`
from `T` to `T?`, exactly; `EqNullable`'s `Op` is an equality on `T`,
with both sides `T?`; an `OrElse`'s body and fallback have its type
unless it's `Void`; and `Bubble` is marked may-bubble (F3), where its
function may bubble or an `OrElse` encloses it (F4).

### Subset B's next step: generics (As built, 2026-10-31, Proposed)

Types gain type arguments and type parameters ([types.md](types.md)).

| Kind | Children | Payload and flags | Type |
|---|---|---|---|
| `ListLit` | the items | | written: `List<T>` of core's, which each item has |

- **Calls with type arguments:** `CallFunction`, `CallMethod`, and `New`
  hold theirs in `b` (`f#1<Int32>(x)`, `o.m#3<String>(y)`, `new
  constructor#7<Int32>(1)`): a generic function's or method's own, or a
  constructor's class's. Their types are the callee's result with the
  type parameters put in, and `GetField` is typed by its field with its
  object's type arguments put in (`TypeRule::Field`).
- **Generic calls** always carry all their type arguments, settled by
  the frontend, so no backend infers them: what the call says or its
  arguments bind, and for a type parameter that appears in no
  parameter's type nor the result's (`CONTEXT` in `combine<CONTEXT
  extends Context, T>(x: T): T`), its bound, `AnyValue` if it has none,
  as Kotlin binds it. Backends whose target infers only from arguments
  (Rust, C#) write them where `analysis::infers_type_args` says it
  can't, all of them, as neither allows some; C++ writes them always.
- **Text:** `(list(a, b)): List#64<Int32>`.

Validator rule T4: a call has as many type arguments as its callee has
type parameters, each interned, and each within its type parameter's
bounds, the other arguments put in (a builtin type's isn't checked, as
tmpl doesn't know all its supertypes).

### Subset B's next step: function values (As built, 2026-10-31, Proposed)

Function literals (closures), references to functions, calls of function
values, and the conversions between `@fun` interfaces and function types
([types.md](types.md), "Function values").

| Kind | Children | Payload and flags | Type |
|---|---|---|---|
| `FunctionLiteral` | its parameters, then its body | `a`: its declaration, a `Lambda` | written: a `@fun` interface or function type whose signature is its declaration's |
| `FunctionRef` | | `a`: a top-level or static function, not generic | written: as `FunctionLiteral`'s |
| `CallValue` | the callee, then the arguments | `MAY_BUBBLE` if its signature may | its callee's signature's result (`TypeRule::ValueCall`) |
| `ToFun` | a function type's value | | written: a `@fun` interface of that signature |
| `FromFun` | a `@fun` interface's value | | written: its function type |

- **A literal's declaration** (`DeclKind::Lambda`, compiler-made, named
  `fn`) is owned by the function, literal, or module (in an init block)
  it's in, has its signature as its type, owns its parameters and
  locals, and lists its **captures** (`DeclTable::captures`): the
  locals and parameters of what's around it that its body, or a literal
  in it, reads or assigns, in `DeclId` order. They're stored, so text
  and passes show them, and checked by recomputation
  (`analysis::captures`, rule D5), as [question 2](open-questions.md)
  recommends.
- **Assigned captures aren't marked:** `analysis::assigned_captures`
  gives the captured locals that some `Assign` writes, which backends
  whose closures copy what they capture keep in cells (Rust, Java, C++;
  [profiles.md](profiles.md)). No lowering pass makes `NewCell`,
  `GetCell`, and `SetCell` yet; each such backend spells its cells.
- **Local functions that use each other or themselves** (2026-11-05):
  a run of `let`s of literals, none a `var`, may use each other's
  locals in their literals before their `let`s, as a `letrec`; such a
  local is in scope (R3) from its block's start, and text reads it
  ahead. `analysis::function_groups` gives the groups, which targets
  whose closures can't name themselves write as one value.
- **`return` in a literal** returns from the literal. Core's `forEach`,
  whose parameter is `@inlineUnrealizedGoal`, never gets a literal:
  `to_tmpl` inlines it as Kotlin does, as a `while` over the list's
  indices, so a `return` in its block returns from the function around
  it, and a `for … of` loop is that loop, its `break` and `continue`
  the loop's.
- **Text:** `(fn ~fn#9(x#10: Int32): Int32 [f#3] { … }): IntOp#1`, the
  captures in brackets (left out if none); `(ref(addOne#4)): IntOp#1`;
  `f#3(x)` and `twice#2(f#3)(2)` for calls of values (a postfix `(`);
  `(toFun(e)): IntOp#1` and `(fromFun(e)): fn (Int32): Int32`; and the
  function type `fn (A, _?: B): R throws Bubble`, which reads back.

Validator rules: **V1** a `@fun` interface's one member is its abstract
`apply` method; a literal's or reference's type has its declaration's
signature; a `CallValue`'s callee is a function value (its arguments
checked as a call's, T3); `ToFun` and `FromFun` convert exactly between
a `@fun` interface and its function type. **D5** a literal's captures
are what its body uses of what's around it, and R3 lets it use exactly
those; R6 a captured local it assigns is a `var`. Definite assignment
(R5) stops at a literal, whose body may run any time after it's made.

### Subset B's next step: parameters' defaults (As built, 2026-11-01, Proposed)

A parameter with a default (`end: Int = start + 10`) is optional
(`OPTIONAL`, and its signature's `optional`), and typed `T?`, since
null means its default, as in Kotlin Temper: a caller passes a `T?`, a
`T` through `AsNullable`, `null`, or `Omitted` for a trailing one left
out. The default is written out where it runs, at the top of the
function's body, as a `let` of the parameter's value coalesced with it,
in parameter order, and the body reads that local, typed `T` (or `T?`
for a nullable parameter's): Kotlin's shape, made explicit, so backends
translate it with what they already have and may print it in place.

| Kind | Children | Payload and flags | Type |
|---|---|---|---|
| `Coalesce` | left, `T?`; right, run only if left is null | | written: right's, `T` or `T?` |

- **Defaults** (`analysis::defaults`): a body's leading `let x' =
  coalesce(x, d)`, one per optional parameter, `x` read only there;
  each default sees the parameters before it with their defaults
  applied (`end: Int = start + 10`), and runs only if its parameter is
  null or left out. The local takes the parameter's name, so a target
  whose naming lets them share one (Python, Rust) may set the parameter
  in place, and block-scoped targets (JS, Lua) print the local as the
  parameter.
- **Constructors:** an implied constructor's parameters take their
  fields' defaults, and set the fields from the defaults' locals; an
  object literal that leaves a property out passes `null` for it, as
  typing places it; a trailing block placed before later optional
  parameters' arguments moves there.
- **Function types:** a reference to a function with defaults is typed
  with its optional parameters (`fn (Int32, _?: Int32?): String`), as
  its signature is; calls of such values leave trailing ones out as
  `Omitted`. Typing can't yet give a literal defaults (it needs a
  declared type, which can't say so), so no test calls such a value.
- **Core's members** keep their fixture's signatures, `radix?: Int32`,
  not nullable: their connections apply the defaults.
- **Text:** `fn range#1(start#3: Int32, end#4?: Int32?): String { let
  end#5: Int32 = (coalesce(end#4, %Int32.add(start#3, 10))): Int32; …
  }`.

Validator rules: **N1** a `Coalesce`'s left is nullable, and its type is
its right's, which is the left's type with or without null. **N2** an
optional parameter of a function with a body is nullable, and the body
starts with its default, in parameter order (`analysis::defaults`).

### Subset B's next step: core's other types (As built, 2026-11-02, Proposed)

No new kinds: core's other types and members are external declarations
in the core fixture, used by the nodes that already exist.

- **Types:** a builtin type that isn't a scalar is `Nominal` of core's
  declaration (`StringIndex`, `StringIndexOption`, `NoStringIndex`,
  `StringBuilder`, `Empty`, `DenseBitVector`, and generic `Deque<T>`),
  so `is` and `as` on them are `Is` and `As` as for any class.
- **Statics:** `String.begin`, `StringIndex.none`, `Float64.e`, and
  `Float64.pi` are core's connected static fields, read by `GetStatic`.
- **Operators by core's methods:** `<`, `==`, and the rest on string
  indices call `StringIndexOption`'s `@operator` method (`compareTo`
  compared with 0, or `eq`), its receiver upcast to the interface, as
  for a class's own.
- **Optional parameters left out before one given:** where named
  arguments skip core's optional parameter, which isn't nullable
  (`near(x, absTol = 0.1)`), its argument is `Omitted`, not null; a
  backend's connection decides what to pass.
- **`char'a'`** is its code point, an `Int32` constant, as Kotlin makes
  it; **`empty()`** calls core's connected function.
- **`for … of` a string, and `String.forEach`,** inline as core's body
  does, the step before the body, as a list's: `var i = String.begin;
  while (s.hasIndex(i)) { let c = s[i]; i = s.next(i); … }`.
- **Not yet:** a library's own bodiless `@connected` function, which
  needs its backend code (`functions/connected`), while one with a body
  translates as its body, its backend code unused.

### Switch (As built, 2026-11-06, Proposed)

`switch (x) { case 0 { … } case 1, 2 { … } default { … } }`: a `Switch`
statement, its scrutinee an `Int32` or `Int64`, then `Case`s (family
`Part`, only in a `Switch`), each its labels, literals of the
scrutinee's type, and its body; the default, a `Case` without labels,
is last, if any. One case runs, or none without a default; no
fallthrough. Rule F7 checks the scrutinee's type, that labels are
literals, all different, and the default's place. A profile has it
natively with the feature `SWITCH` (Rust `match`, C++ and Java
`switch`); `temper_tmpl::switches::to_if_chains` makes it `if (x == 1
|| x == 2) … else if …` for the others, which call it by their own
profiles (Lua, and Python, JS, and C#, which get none yet), holding a
scrutinee that isn't a local in one first. Java's `switch` takes no
`long`, so Java lowers only `Int64` ones (`to_if_chains_where`). The
coroutines' state machines use it for their dispatch.

### Coroutines (As built, 2026-11-05, Proposed)

Generators and `async`, as Kotlin's `CoroutineStrategy` has them:

- **Nodes:** a generator literal is a `FunctionLiteral` whose
  declaration has the fact `GENERATOR` (text: `fn generator ~fn#8()`).
  Its value is a factory, typed `fn (T): SafeGenerator<Y>`, and its
  body's `return`s have no value. In its own body (not a literal's in
  it, rule F6), `Await` (`(await(p)^): T`) waits for a `Promise<T>` and
  throws core's `BrokenPromise`, if the promise is broken
  (`failures::node_failure`, which finds the program's declaration of
  it), and `Yield` pauses, giving its
  value, a `Y` (`yield x;`), or for `Empty`, none (`yield;`).
  `async { … }` calls core's `async` with the factory.
- **Strategies** (`temper_tmpl::coroutines`): each backend's profile
  names one. `TranslateToGenerator` (Python, JS, Lua, C#) keeps the
  nodes for the target's generators. `TranslateToRegularFunction` (Rust,
  C++, Java) lowers each literal to a factory whose body declares the
  body's locals (a type without a zero made nullable, read through
  `nonNull`), the state, and each awaited promise, and returns
  `safeAdaptGeneratorFn(step)`. The step is a state machine: `while
  (true) { let s = caseIndex; caseIndex = -1; switch (s) { case 0 { … }
  … default { return doneResult(); } } }`, so a step that fails is
  done, each state's code that may fail in the `orelse` or `catch` it's
  in, whose fallback sets the handler's state; a `catch`'s keeps the
  failure in its local, made the factory's and nullable, which the
  handler's states read through `nonNull`, and an `await` in one, after
  code that may fail, pauses from a state of its own, so a `catch`'s
  states that may fail never return (2026-11-26). A branch on a literal (`while (true)`'s) goes
  straight to its state, states that nothing reaches are dropped, a jump
  to a state that only jumps on goes straight on, states that only end
  the generator become -1 (the default's), and the rest are numbered
  from 0 in order (2026-11-06). A
  closure that captures a loop's `let` gets a copy made where it's
  made, `{ let j2 = j; fn { j2 } }`, so each pass has its own, as in a
  function; a `let` assigned again after it's set fails instead. An `await` stores its promise, sets the next state,
  calls `awakeUpon(promise, generator)`, and returns `ValueResult`; the
  next state reads `getPromiseResultSync(promise)^`. A `yield x`
  returns `ValueResult(x)`. The frontend declares these helpers of
  core's with each generator literal. Parameters stay the factory's,
  which the step captures.
- **Not yet:** a pause in a conditional part of a value (`a && await
  p`), and a literal with optional or `var` parameters.

### A `for` loop's step on every exit (As built, 2026-11-23)

A `for` loop's step runs after the body whether the body ends or a
`continue` leaves it. to_tmpl copies it: each `continue` that must run
it is a block of a fresh translation of the step (its own locals), then
the jump, and the body's end runs it once more:

```text
loop: while (c) { …; { step; continue loop; } …; step; }
```

**Output size.** With `j` such jumps and a step of size `a`, that's
`O((j + 1)·a)` per loop, additive over nested loops, so linear in the
source for fixed `a`. It would turn exponential only if the copied
actions held exits copying their own actions (about `(j + 1)^d` at
depth `d`), which can't happen: a step holds no loops, and a jump inside
a step (`i = next(i) ?? continue`) is refused, so copies hold no copies.
javac does the same for `finally`: nested `try`s with jumps grow about
115 bytes of bytecode per level (javac 21), but a `finally` holding its
own `try`/`finally` with a jump grows about 4x per level (371, 562,
3,383, 52,236 bytes at depths 1, 2, 4, 6; "code too large" at 8).
Writing the actions once per region instead (a labeled one-shot region
left by `break`, `goto`, or an exit code dispatched after them) is
`O(j + a)` whatever the nesting. RFC 661's `finally`, which needs it
for a `finally` holding jumps, should bring that as a shared helper
(it was briefly `temper_tmpl::exits`, a copying stub, removed until
then).

### Declared failures (As built, 2026-11-25, Proposed)

RFC 164's failure types, beside `Bubble`, which stays as it was
([types.md](types.md#results); typing.md, "Declared failures"). Every
backend has them (below); a backend without them would say "not yet in
the … backend: declared failures: …" for a program that uses them
(`temper_tmpl::failures::not_yet`), and a program that uses only
`Bubble` is unchanged, node for node.

| Kind | Children | Payload and flags | Type |
|---|---|---|---|
| `Throw` | the failure, then its cause if any | `MAY_BUBBLE`, always | written: as its place wants |
| `Catch` | the body, then the fallback | `a`: its local, holding the failure | written: as `OrElse`'s |

- **Failure types** are the classes and interfaces below core's
  `Error`, an external interface with connected getters `message` and
  `cause` (`failures::root`). A signature fails with `Bubble`, a
  failure type, or a failure parameter (below); failures are ordinary
  values, `Bubble` never is.
- **Core's own failure types,** external like `Error`: the root
  `CoreError` and its classes `MissingKey` (`key`, an `AnyValue`; what
  `Mapped.get` and `MapBuilder.remove` fail with), `ParseError` (`input`
  and `reason`; what conversions fail with), and `InvalidCodePoint`
  (`codePoint`; what `String.fromCodePoint(s)` and
  `StringBuilder.appendCodePoint` fail with). Each target's runtime declares them, as its
  own exception types, and its connection tables name them; `to_tmpl`
  makes a `catch (e)` whose `e` nothing reads an `OrElse`.
- **The library's roots:** the interfaces right under `Error`
  (`failures::is_library_root`), sealed interfaces the library declares
  (decision 17; the frontend checks the rules, notes/typing.md), each
  failure type under one. Targets write each over their own exception
  type, so host code never catches a Temper type (RFC 164, "host
  presentation"); a library may have several.
- **Messages:** a failure class without a member named `message` gets a
  `message` getter giving the interpreter's default message, `not found
  (key: y)` (`interp::failures`). `cause` stays core's, which targets
  map to their own (`getCause()`, `__cause__`, `{ cause }`).
- **`Throw`'s cause** is the failure the innermost handler's fallback
  around it handles, in its own function (a `for … of` body inlined
  there counts), where that handler binds it; the thrown failure takes
  it unless it already has a cause, so a rethrow keeps its first.
- **`Catch`** is `OrElse` with a binding, its local, declared by it and
  in scope only in the fallback: `catch (e)`'s `e`, or a compiler-made
  `~failure` where a fallback only throws. `to_tmpl` makes one only
  where the fallback needs the failure and the body fails with a
  failure type; else it's an `OrElse`. Where the fallback jumps
  (`return`, `break`, `continue`) and so runs after it, the `catch`
  keeps the failure in a nullable `~caught` local the fallback reads.
- **`when` over failure types** needs nothing new: `Is` and `Downcast`.
- **Several roots in one handler:** where a handler's body may fail
  with failure types under different roots, of one library or several,
  their join is core's `Error`, which a `catch` binds (signatures can't
  fail with `Error`, so only handlers have it). Each target catches only
  failures there, by its runtime's marker, and lets panics and hosts'
  errors pass, as below (Lua's already did).
- **Naming:** Java's and C#'s rules say `Exception` for `Error` at the
  end of a failure type's name (`NamingRules::failure_suffix`), so
  `ConfigError` is `ConfigException` there, the roots too.
- **Text:** `(throw(new NotFound#1(k#2))^): Void`, `(throw(x#3,
  e#4)^): Int32`, `(catch(f#5()^, e#6: NotFound#1, 0)): Int32`, and
  `interface FailuresError#7 extends Error#8 {}`.

**In Python** (chunk 7): core's `Error` is `temper_core.Failure`, a
marker that isn't an exception, giving `__str__` from `message`; a
root is `class FailuresError(Failure, Exception)`, the failure types
classes under it (a sealed interface abstract, by `Failure`'s
`ABCMeta`). `Throw` is `raise`, `raise X(…) from cause` where it makes
the failure, else `raise with_cause(x, cause)`, which keeps a cause it
has. `OrElse` and `Catch` are `try`/`except` of exactly what the body
may fail with: `Bubble` and the join of its declared failures
(`failures::failures_under`, `join`), so panics and hosts' errors pass;
where the join is core's `Error`, `except Exception as e:` then `if not
isinstance(e, Failure): raise e` (with `Bubble` in a tuple where the body
bubbles too), which checkers narrow. `cause` is `failure_cause(e)`, `__cause__` where it's a
`Failure`; `~caught` read after a jumping fallback is asserted not
null for checkers.

**In Java** (chunk 8): unchecked exceptions. Core's `Error` is
`temper.core.Failure`, a marker interface (`getMessage()`,
`getCause()`); a root is `public abstract class FailuresException
extends RuntimeException implements Failure`, a sealed failure
interface an abstract class under it (Java 8's runtime has no
`sealed`, and a library's modules are packages of their own), each with
a `protected` constructor, and failure classes `final` under those;
all `@SuppressWarnings("serial")`. `message` is `getMessage()`; `cause`
`Core.failureCause(e)`, `getCause()` where it's a `Failure`; a member
named as another of `Throwable`'s methods is "not yet". `Throw` is
`throw`, `throw Core.withCause(x, cause)` in a handler (keeps a cause
it has), `Core.throwFailure(x)` where Java needs a value. `OrElse` and
`Catch` are `try`/`catch` of exactly `Bubble` and/or the join, a
multi-catch where both, so panics (`AssertionError`) and host
exceptions pass; where the join is core's `Error`, `catch
(RuntimeException caught)`, what every root and `Bubble` extend, then
`if (!(caught instanceof Failure)) { throw caught; }` (`Bubble ||` too
where the body bubbles), and a `catch` binding `Failure e = (Failure)
caught;`; an `Error` thrown or taken as a cause is cast back to
`RuntimeException`. A function failing with a failure type lists it in
a `throws` clause. Stack traces are kept (made where the failure is
constructed), unlike `Bubble`'s.

**In Rust** (chunk 9): values, not unwinding. A signature failing with
a failure type returns `Result<T, F>` (`Bubble`'s stays
`temper_core::Result<T>`); a failure class is a class handle with a
`temper_core::Cause` field and `impl_failure!` (`Display` its message,
`Debug`, `std::error::Error` with `source()` its cause, and
`temper_core::Failure`, which core's `Error` is, `Arc<dyn Failure>`); a
sealed failure interface, the roots among them, is an `enum` of the
types right under it, `From` each below (`impl_failure_enum!`,
`impl_failure_from!`), an exported root's `#[non_exhaustive]`, and the
crate's `Error` the one root, `pub type Error = r#mod::WherewithError;`
in `lib.rs`, where the library has one, exported, and no `Error` of its
own; `is` and `as`
see through it (`AnyValueTrait` by the variant). `Throw` is `return
Err(x)`, `.into()` where the code fails with a type above, through
`temper_core::with_cause(x, cause)` in a handler. `OrElse` and `Catch`
run the body as a `Result` of exactly what it may fail with (the join,
or `temper_core::Error` with a bubble, which failures convert into); a
join that's core's `Error` is `Arc<dyn Failure>`, which each failure
type and enum converts into by `From` (in `impl_failure!` and
`impl_failure_enum!`, so `?` does) and which is a `Failure` itself, so a
`throw` there takes it as a cause;
`Catch` is `unwrap_or_else(|e| …)`, `map_err(|e| thrown)?` for a
throwing fallback, `or_else(|e| …)?` for another failing one, and as a
statement `if let Err(e) = …`. `?` converts by `From`; `Ok(e?)` is `e`
only where the types match. A module's init takes any failure as a
bubble; `uncaught` names it a failure and lists causes. No
`catch_unwind`: a panic passes every handler. Not yet: a `catch` whose
body jumps, or binding a failure beside a bubble; failure interfaces
with members.

**In C++** (chunk 10): values, never thrown. A signature failing with a
failure type returns `core::Expected<T, F>`, `F` the failure's
`shared_ptr`: C++23's `std::expected` where `__cpp_lib_expected` says
it's there, else the runtime's C++14 shim with the same API for what it
has (`expected.hpp`, with its own tests on both); `Bubble`'s signatures
are unchanged. Core's `Error` is the runtime's `core::Failure`, a
`std::exception` (`what()` its message, made once when first asked)
with `get_message()`, pure virtual, and a cause set once
(`get_cause()`, `set_cause`, never itself or a loop); a root is
`struct FailuresError : virtual core::Failure`, sealed failure
interfaces abstract structs under it, failure classes structs under
those, their `message` getters `override`. A failing call is computed
first, `auto lookupResult = lookup(key); if
(!lookupResult.has_value()) { return core::unexpected(lookupResult.error()); }`,
then `*lookupResult` where it was; one whose value and failure types
are the function's is returned as it is; `spill` makes choices' parts
and loops' conditions holding one statements. `Throw` is `return
core::unexpected(x)`, `core::with_cause(x, cause)` in a handler; in a
module's init, an unhandled failure is `core::uncaught_failure(x)`,
which throws `core::UncaughtFailure` out of the init. `OrElse` and
`Catch` branch on the result: where the body's one failing call runs
first in it, `if (r.has_value()) { …body with *r… } else { …fallback… }`;
else the body is a lambda called at once giving `core::Expected` of the
join, `std::shared_ptr<core::Failure>` where that's core's `Error`
(each library's failure's `shared_ptr` converts into it, as its root is
a `core::Failure`). A body that also bubbles is in a `try`, its failure bubbled to
the `catch` (an exception on that path only). Panics and host
exceptions pass every handler. Not yet: a `catch` binding a failure
beside a bubble, a lambda body that jumps out, a failing call where
nothing can go before it, local function groups that fail, `orelse
panic()` naming a declared failure as the panic's cause, and failure
parameters.

**In JS** (chunk 11): classes extending JS's `Error`, natively rather
than through the runtime's `type()` mixin, so `instanceof`, `stack`,
`name`, and `message` work for hosts: a root is `class FailuresError
extends Error {}` followed by `failureBase(FailuresError)`, which gives
its instances the runtime's marker symbol (`isFailure`) and `name`, the
class's own; a sealed failure interface is a class under it, never made
itself, and failure classes extend the one failure type above them
(other supertypes, and a failure interface's abstract members, are not
yet). `message` is a getter, the class's own or the generated one,
over `Error`'s, so it's read when asked, after the fields are set; a
failure type's members named `name`, `stack`, or `cause` are escaped
(`name_`, by `NamingRules::failure_member_reserved`). `cause` is
`failureCause(e)`, the standard `cause` where it's a failure. `Throw`
is `throw`, `throw withCause(x, cause)` in a handler (keeps a cause it
has, never itself or a loop; the `{ cause }` option can't go through a
generated constructor), and where JS needs a value, `(() => { throw x;
})()`. `OrElse` and `Catch` are `try`/`catch` rethrowing all but what
the body may fail with: `if (!(isBubble(e$) || e$ instanceof NotFound))
throw e$;`, a `catch`'s binding the variable, so TypeScript narrows it
(not yet beside a bubble, which it can't); where the join is core's
`Error`, the runtime's `isFailure(e$)`, a type predicate (`e is
Error`). A `~caught` read after a
jumping fallback is cast to its failure type. A function failing with
a failure type says so with JSDoc's `@throws`.

**In C#** (chunk 12): exception classes. Core's `Error` is
`TemperLang.Core.IFailure`, a marker interface (`Message`,
`InnerException`, both `Exception`'s); a root is `public abstract
class FailuresException : Exception, IFailure`, a sealed failure
interface an abstract class under it (no `I`), failure classes
`sealed`; abstract ones' constructors are `private protected` (C# can't
seal a hierarchy across files). A failure's `message` getter is
`public override string Message`; a member named as another of
`Exception`'s is "not yet". The cause is `InnerException`, which C#
sets only by constructor, so passed as hand-written C# does: each
failure class's public constructor calls an `internal` one that takes
the cause too and has the body, `: this(key, null)` (its parameter
`cause`, numbered past the class's own names), passing it to the
base's, `private protected`, and on to `Exception`'s; the `Throw` of a
failure made in a handler calls it, `throw new LookupFailed(name, e)`,
every argument given, so a failure made among its arguments takes none.
A failure made before and thrown in a handler, or another library's
(whose internal constructor is out of reach), is `Core.WithCause(x,
e)`, keeping the cause beside it (a `ConditionalWeakTable`) for
Temper's `cause` (`Core.FailureCause`) alone, unless it has one. `Throw` is `throw`,
`Core.ThrowFailure<T>(x)` where C# needs a value. `OrElse` and `Catch`
catch exactly `Bubble` or the join, `catch (NotFound e)`, or both,
`catch (Exception e) when (e is Bubble || e is NotFound)`, so panics
and host exceptions pass; where the join is core's `Error`, `catch
(Exception caught) when (caught is IFailure)`, and a `catch` binding
`IFailure e = (IFailure)caught;`; an `Error` thrown or taken as a cause
is cast back to `Exception`. A `~caught` read takes `!`. A function
failing with a failure type says so in an XML doc comment, `///
<exception cref="NotFound">`.

**In Lua** (chunk 13): the runtime's types, as other classes are.
Core's `Error` is `temper.Failure`, a type whose instances, and those
of every type under it, have a metatable of their own: `__tostring`
gives the failure's `message`, and `cause` reads as `nil` where there's
none. A root is `temper.type("failures.FailuresError",
temper.Failure)`, failure types under it as other types are, LuaCATS
classes under `temper.Failure`; a failure type's member named `cause` is
`cause_`. `message` is the getter; `cause` is `temper.failure_cause(e)`,
the key `cause` where it's a failure. `Throw` is `error(x)`,
`error(temper.with_cause(x, cause))` in a handler (keeps a cause it
has). `OrElse` and `Catch` are `temper.catch(function() … end,
NotFound)`, catching exactly the join, by `temper.instance_of`
(`temper.Failure` where it's core's `Error`), and
`temper.Bubble` where the body may bubble too, raising anything else
again unchanged; a body that only bubbles keeps `temper.pcall`. A
`catch`'s binding is its second result, `local ok, e =
temper.catch(…)`, or where the body jumps, a local of the fallback.
`orelse panic()` names a failure as its cause by its message. A failure
nothing handles: `temper run` and the tests' runner report it as
`uncaught failure: LookupFailed: lookup failed (name: port)`, then
`caused by: …` lines (`temper.failure_report`).

**Failure parameters** (chunk 15; typing.md, "Failure parameters"): a
type parameter bound by a failure type, `E extends Error`, which the
frontend gives one a signature fails with (`failures::is_failure_param`),
counts as a failure type, so generic code may `throw` one, a `catch` bind
one, and a function or `@fun` interface fail with one: `fn
mapAll#16<T#17, R#18, E#19 extends Error#9>(xs#48: List#20<T#17>, f#49:
Mapper#3<T#17, R#18, E#19>): List#20<R#18> throws E#19`. A call gives it
as a type argument, what its callbacks may fail with, or `Never<Error>`
where it can't fail by one, which isn't marked then (the builder's flags
come from the substituted result): `mapAll#16<String, Int32,
Missing#2>(…)^`, `ns#79.join#69<Never<Error#9>>(…)`. Core's generic
methods (`map`, `filter`, `join`, …) are as core declares them. Where a
function that fails more narrowly is passed where a wider failure is
wanted (`find`, throwing `Missing`, as `Mapper<String, Int32,
FailureParamsError>`), `to_tmpl` passes a literal calling it, so every
target widens it as a `throw` would. A bubble as `E` is not yet: handlers
in generic code catch failures, which a bubble isn't.

Targets whose failures are unchecked exceptions (Python, Java, JS, C#,
Lua) erase them (`failures::erase_params`): in code, each is its bound,
so a handler in generic code is one over core's `Error`, catching by the
runtime's marker (Python's `Failure`, Java's `temper.core.Failure`, C#'s
`IFailure`, JS's `isFailure`, Lua's `temper.Failure`), never a panic or
host error, and a callback's failure passes through unchanged. Signatures
keep them, so calls outside still fail with what they give, and handlers
there catch exactly that. No host signature names one
(`failures::unchecked_type_params`, `unchecked_args`): Java's `<T, R>
List<R> mapAll(List<T> xs, Function<T, R> f)` has no `throws` clause for
`E`, as Java's checked-exception idiom (`<E extends Exception> … throws
E`) would only mislead for unchecked ones; docs (`throws`, `@throws`,
`<exception>`) name only declared failure types (`failures::declared`).
A signature naming one other than as a failure, as `e: E`, is not yet
there. Rust and C++ say not yet for any failure parameter of the
program's, or a call giving one a failure type
(`failures::params_not_yet`; chunk 16).

Validator rules: T4 checks a failure parameter's type argument against
its bound, `Never<T>` as `T`, and builtin types (`Bubble`, `String`)
fail it; T5 lets a signature fail with a failure type or a failure
parameter; F3 marks every `Throw`; F4 checks the failure's type where it
goes: anything in an `OrElse`'s body or an init block, only what the
`Catch` binds (or below it) in its body, and elsewhere only the
function's failure type or below, `Bubble` and failure types never
mixing; F8: a `Throw`'s value and cause are failure types, and a
`Catch` binds one; D6: failure types aren't generic and have one
failure type above them, and one that's `Error` itself, a root, is an
interface, never a class.

Not yet: `throw` and `catch` in generators, checking a failure type's own
`message` against the root's, and a field named `message`, which gets
no getter.

## Level 3: Ops, and core as declarations

**Decided (2026-10-29):** customization is general, by naming: only
operator lookups and connected declarations
([names.md](names.md#level-3-connected-declarations)), never a
hardcoded table of core functions such as `%console.log`.

Proposed, replacing subset A's 113-key table:

- **`Op` is only operators on builtin types,** a closed set of 65,
  as Kotlin's `BuiltinOperatorId`s are: per type, `eq`, `lt`, `le`,
  `gt`, `ge`, and `cmp` (`Boolean`, `Int32`, `Int64`, `Float64`, and
  `String`); `add`, `sub`, `mul`, `div`, `mod`, and `neg` (the numeric
  types); `bitAnd`, `bitOr`, `bitXor`, `bitNot`, `shl`, `shr`, and
  `ushr` (the integer types); `Float64.pow`; `Boolean.not`; and
  `String.cat`. As built, 69 since 2026-11-02, with `divSafe` and
  `modSafe` for the integer types: `/` and `%` by a constant that
  can't be zero (not 0, and for `%` positive), which skip the zero
  check, as Kotlin's `DivIntIntSafe` and the like. No operator fails
  (2026-11-25): integer `div` and `mod` panic for a zero divisor, so
  they aren't pure ([`OpInfo::pure`]), and `Float64`'s give IEEE 754's
  results. They belong to the language, not a library, and passes
  and analyses (purity, value ranges, hoisting) match them by enum,
  never by string. Each variant spells every one.
- **Everything else is a declaration of core or std,** referenced by
  `CallFunction` or `CallMethod` like any other: the other 48 keys
  (`Int32.toString`, `Float64.sqrt`, `String.toInt32`, `String.isEmpty`
  as a getter, …) are `@connected` members of core's classes, with
  qnames such as `core.type Int32.toString()`. Their signatures are
  core's, defaults included (`toString`'s radix), so calls pass
  `Omitted` for them.
- **`console`, as Kotlin does it:** the frontend declares a
  compiler-made module `let`, `~console`, initialized by core's
  connected `getConsole()`, and `console.log(s)` is a virtual
  `CallMethod` of `Console`'s `log` on it.
- **An explicit `a.eq(b)` on a builtin type** is its `Op`, as `==` is,
  though core declares `Int32.eq` as a connected `@operator("==")`
  method.
- **Scalar types stay fixed type entries** ([question 26](open-questions.md)):
  core's class declaration for one, such as `Int32`, is an external
  declaration owning its members, linked to the fixed entry by its
  qname.

## Level 3: The validator's rules

`validate(&Program, &Profile)` returns problems, each with a rule
code, a node or declaration, and a message, sorted by writing order,
so output is deterministic. Structure runs first; the other groups run
only on a structurally sound program, so one mistake isn't reported
many times. Golden tests list problems as `RULE @node: message`.

- **S, structure.** S1: each kind's children match the schema's
  roles and count. S2: every node has one parent. S3: statements are
  in statement places and expressions in expression places. S4: the
  profile allows each kind and flag, and starting tmpl has no
  lowered-family kind.
- **T, types.** T1: every expression has a type, and every
  declaration its declared type. T2: a node the local rules cover has
  exactly their type. T3: every child's type is its role's exactly;
  there are no implicit conversions. T4: type arguments match the
  callee's type parameters in number and fit their bounds. T5: every type is well formed
  (no `Nullable` of a nullable, `Result` only in a lowered program).
- **R, references.** R1: every id exists and isn't removed. R2: a
  reference's declaration is of the kind its node wants (`Local` a
  local or parameter, `CallFunction` a function). R3: a local is read
  only in its scope, within its function. R4: labels enclose their
  jumps. R5: a `Let` without an initializer is definitely assigned
  before each read. R6: only `VAR` declarations are assigned twice.
- **F, flow.** F1: a function whose result isn't `Void` never falls
  off its end. F2: a `Return` has a value exactly when the result
  isn't `Void`. F3: `MAY_BUBBLE` is set exactly when the callee may
  bubble. F4: a call that may bubble has a handler, or its function
  may bubble (with declared failures, of a type that fits; F8 for
  `Throw` and `Catch`). F5: an initializer doesn't read a module `let` that's
  initialized later (through a call, it's a warning, [question 19](open-questions.md)).
- **D, declarations.** D1: every compiler-made declaration has a hint
  with at least one candidate. D2: owners form a tree rooted at
  modules. D3: every type an export mentions is exported or external.
  D4: `@imu` and `@partialImu` marks hold (subset B,
  [mutability.md](mutability.md)). D5: stored facts (may-bubble marks,
  and from subset B captures) match their recomputation. D6: failure
  types and the library's base are well formed ("Declared failures").
- **Q, qnames and connected** (Proposed 2026-10-29, built;
  [names.md](names.md#level-3-qnames)). Q1: qnames are unique within a
  library. Q2: every declaration but compiler-made ones has one. Q3: a
  function or method without a body is connected or external (or,
  from subset B, abstract). Q4: a connected declaration with a backend
  list has a body, its fallback.
- **P, positions.** P1: in a program with spans, every node and
  declaration has a known span inside its file.
- **M, modules** (Proposed 2026-10-30, built; [modules.md](modules.md)).
  M1: a module refers to another of the program's modules only for its
  exported top-level declarations, of a module before it in
  `Program::modules`, and assigns or initializes only its own module
  `let`s. M2: a test's signature is `fn (Test): Void throws Bubble`, with
  std's `testing` module's `Test`. M3 (2026-11-18): a module's library
  parts, its items and init block, use nothing of its test parts, its
  test items and test init.

`Error` nodes are exempt from T2 and T3.

As built (2026-10-29, Proposed), with core's declarations:

- **S3:** `Omitted` goes only in a call's arguments; a module's items
  may be `BodilessFunction`s.
- **T1 and T5:** a class's or interface's type is its own `Nominal`
  type, or for core's class of a scalar (`core.type Int32`) the
  scalar; a getter takes no arguments; a parameter's `OPTIONAL` fact
  matches its signature; a `Nominal` type names a class or interface.
- **T3:** a `CallMethod`'s receiver has its method's owner's type, and
  `Omitted` fills only an optional parameter. Operators have none.
- **R2:** `CallMethod` names a method or getter; `CallFunction` a
  function, a static one included.
- **D2:** a used library is listed in `Program::uses` and has no owner;
  an external class or interface is owned by its library, a method or
  getter by a class or interface, and a function by a module, library,
  class, or interface.
- **Q1 and Q2** check `QNames::derive`; Q2 exempts compiler-made
  declarations and what they own, labels, modules, and used libraries,
  which Kotlin gives no qname, and reports an override that doesn't
  parse. Q3 is checked on `BodilessFunction`; external declarations
  never have bodies. Q4 also rejects a backend list on a declaration
  that isn't connected.
- **P1** exempts other libraries' declarations, whose spans, if any,
  point into their own files.
- **With modules** (2026-10-30): D2 lets a `Module` be owned by a used
  library, as another library's module, which may own external
  classes, interfaces, functions, and module `let`s; S3 checks a
  module's tests are `Test` nodes, which F, R, and T check as functions.
- **Virtual isn't stored:** a `CallMethod` is virtual exactly when its
  method's owner is an interface (`Program::is_virtual`), so there's no
  `VIRTUAL` flag to keep in step.
- **Room for failure kinds** ([question 44](open-questions.md)): a
  signature's result says what it fails with, a result type
  ([types.md](types.md#results)), and a node's `MAY_BUBBLE` stays a
  flag, its failure's type found from the node
  (`failures::node_failure`), as declared failures have it.
