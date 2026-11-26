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
  `As` (which may bubble).
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
  optional one. `MAY_BUBBLE` is set exactly when the callee or
  operator may bubble.
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
| `As` | the value | `MAY_BUBBLE`, always | written: a proper subtype, checked when run |
| `Is` | the value | `a`: a class's or interface's type | `Boolean` |
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
  init block; `Upcast`, `Downcast`, and `As` only up or down the
  supertypes; `Is` and `Identical` only on classes and interfaces.

Not yet checked: that a class implements every abstract member it
inherits (the frontend reports it), and statics read before their
initializer.

### Spreads (As built, 2026-11-25)

A spread, `{ ...old, a: e }`, arrives as the frontend lowers it:
`value { let spread#1 = old; new C(copy(spread#1.a), e) }`. Each
argument that copies `old`'s property as it is, or made nullable, is a
`GetField` marked `SPREAD_COPY` (`copy(x.f)` in text); copies held in
temporaries, where given values come out of order, aren't marked; a
marked copy held in a temporary is allowed, and backends ignore it.
Rule **C4**: a copy reads a field from a local, and as a `New`'s
argument the field named as its parameter, of the class constructed,
one `New`'s copies all from one local of that class.

So a target with its own copy form (Rust's `..old`, C#'s `with`,
Python's `dataclasses.replace`) may pass only the other arguments, where
`analysis::copyable_classes` says the class allows it (2026-11-26): a
`data` class (`Facts::DATA`, from `@data`, whose rules the frontend
checks: its constructor only stores its parameters, all public fields,
with no accessors and no default but null; notes/typing.md, "`@data`
classes") with no `var` field, as a copy form reads the fields after
the given values run, which then can't change them. Spreads need
`@data`, so a class's own copy form is only an optimization; the meaning
stays the constructor call. A `data` mark comes after `fun` in text.

Rust resugars (2026-11-26): a spread of such a class of the module is a
struct update, `Theme(Arc::new(ThemeStruct { size: 20,
..(*spread.0).clone() }))`, whose struct alone derives `Clone`; given
values run in order, then the base, the temporary already holding
`old`. A `data` class's fields are typed as its parameters, so given
values go in as they are, a nullable one too (`note: Some(…)`). In
another module, where the struct is private, or where a given value's
type isn't its field's (a generic class's `T`, given an `Int`), it
calls the constructor.

C++ copies (2026-11-26): such a spread is `std::make_shared<C>(*spread)`
and an assignment of each given value, in order, into the local a `let`
declares, else a temporary: `std::shared_ptr<Theme> loud =
std::make_shared<Theme>(*spread); loud->size = 20;`. The struct's
implicit copy constructor copies the fields, and `make_shared` gives the
copy its own `enable_shared_from_this`. An assignment's copy is a
temporary, as a given value may read the variable assigned; where no
statement may go first, it calls `make`. Other backends still call the
constructor: their generated classes don't have the shape their copy
forms take.

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
  translates as its body, its backend code unused; and a constant `/`
  or `%` where nothing may bubble (`types/int/shifty`, `types/map`).

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
  may bubble, if the promise is broken, and `Yield` pauses, giving its
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
  done, each state's code in an `orelse` whose fallback sets the state
  of the `orelse` it's in. A branch on a literal (`while (true)`'s) goes
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
  can't fail them (not 0, and for `%` positive), which never bubble,
  as Kotlin's `DivIntIntSafe` and the like, so a function that can't
  bubble may divide by one. They belong to the language, not a library, and passes
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
  may bubble. F5: an initializer doesn't read a module `let` that's
  initialized later (through a call, it's a warning, [question 19](open-questions.md)).
- **D, declarations.** D1: every compiler-made declaration has a hint
  with at least one candidate. D2: owners form a tree rooted at
  modules. D3: every type an export mentions is exported or external.
  D4: `@imu` and `@partialImu` marks hold (subset B,
  [mutability.md](mutability.md)). D5: stored facts (may-bubble marks,
  and from subset B captures) match their recomputation.
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
  signature's `bubbles` and a node's `MAY_BUBBLE` stay booleans, and a
  failure kind would be an added, optional field beside them (a
  `Nominal` declaration, by qname); nothing here depends on `Bubble`
  being the only one.
