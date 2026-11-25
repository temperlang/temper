# Typing and flow facts

Part of the [rewrite plan](README.md).

Typing works in two layers that cooperate inside the settle loop:

1. **Declared and inferred types** of declarations and expressions, as
   constraints solved when their inputs are known (see the settle loop in
   [design.md](design.md)).
2. **Flow facts**: what's known at each *use* of a variable, such as "not null
   here", "is a `Circle` here", or "between 0 and 255 here". These come from
   structured passes over a function body, with no control-flow graph.

Static types may change what a program compiles to, such as which class an
object literal constructs, or what a backend emits.
The interpreter and dynamic backends like Python ignore types at run time,
so where types change meaning, they act on the tree before that, and the
interpreter runs the result.

## Flow facts without a CFG

Temper's settled tree has structured control flow: blocks, `if`, loops,
labeled `break`/`continue`, `return`, and failure (`bubble` with `orelse`).
So a pass can walk a function body in evaluation order and carry an
*environment* of facts, merging at the points where control flow merges,
instead of first building a graph.

The environment maps each local to what's known about it:

- **Type:** narrowed by `is`, casts and null checks.
- **Nullability:** null, not null, or either.
- **Value range,** for numbers: a constant, an interval like `0..=255`, or
  unknown. For strings and lists, possibly emptiness or length bounds.
- **Definitely assigned**, or not.

How each construct is handled:

- **Sequence:** facts flow through statements in order.
- **`if (c) A else B`:** the condition refines facts separately for each
  branch (`a != null` makes `a` non-null in `A` and null in `B`); after the
  `if`, merge the two outgoing environments. A branch that always exits
  (`return`, `throw`, `break`) contributes nothing to the merge, which is what
  makes the early-return pattern work:

  ```temper
  if (a == null) { return x; }
  a.length   // `a` is non-null here
  ```

  Kotlin Temper rejects this today; this design handles it naturally,
  and tempest does, for null checks so far (history/milestone-4.md).
- **Loops:** iterate the body until the environment at the loop head stops
  changing. To guarantee that quickly, ranges *widen* after a couple of rounds
  (an interval that keeps growing becomes unknown in that direction).
- **`break` and `continue`:** carry their environment to the loop or label
  they target; that target merges everything that arrives.
- **`return` and failure:** carry their environment to the function exit,
  or to the nearest `orelse`, which merges whatever can fail inside it.
- **Closures:** a local that a closure might assign loses its narrowed facts
  wherever the closure might run. Locals no closure assigns keep them.

The cost is about linear in the function's size, plus a few rounds for loops.

### Why "semi-holistic"

Each function body is analyzed as a whole once its inputs have settled (the
types of the things it calls and reads), rather than fact by fact. If an
input changes later in the settle loop, which is rare, the function's facts
are recomputed. Results go into side tables keyed by node id, so the tree
itself doesn't change.

### Risks to design for

- **A small core of control constructs.** After macro expansion, everything
  must reduce to forms the flow pass knows: block, `if`, loop, `break` and
  `continue`, `return`, `bubble`/`orelse`, and function. Macros that leave
  other shapes behind would make the flow pass complicated. This is the main
  commitment of the design.
- **`orelse` merges many exits.** Every call that can bubble is an implicit
  exit, so the facts at `orelse` are the merge of the facts at each such
  call. Easy to get subtly wrong; test it well.
- **Flow facts and macros affect each other within a function.** A narrowed
  type can pick which method a call resolves to, and that can trigger a
  macro that needs types. So each function gets its own small, bounded fixed
  point: expand, type and flow, run late macros, flow again.

## Null-check and `is` narrowing: the rules

What tempest does today (`flow.rs`); the status and how it was checked
are in [milestone-4.md](history/milestone-4.md). A *fact* says a declaration
isn't null, and maybe that it's of a type `T`, from `a is T`. A use of
`a: String?` where a fact holds is a `String`; a use of `i: I` where a
fact with `T` holds is a `T` (`narrowed` in `typing.rs`), as in Kotlin
Temper, unless `i`'s declared type, made non-null, is a subtype of `T`
already, which is kept as more precise. A `T` typing doesn't know, as a
generic type, makes the use's type unknown.

**Eligible names:** locals, parameters, and top-level names declared
without `var`, plus the compiler's temporaries, such as `??`'s. These are
never assigned again, so a fact, once true, holds for the rest of the
scope: nothing kills it, loops need no fixed point, and closures can keep
it. A `var` is never narrowed (`var b = a; if (b != null) { take(b) }`
reports `take(b)`), as in Kotlin Temper.

**Conditions.** Each condition gives the facts where it's true and where
it's false:

- `a == null` and `null == a`: `a` isn't null where it's false.
  `a != null` and `null != a`: where it's true.
- `a is T`: `a` isn't null where it's true, and is a `T`, in place of
  any type an earlier fact gave it, if `T` isn't nullable. Null passes
  `a is T?`, so that gives only that `a` isn't null where it's false;
  where it's true, `a` isn't narrowed to `T?` yet. A use keeps its declared type where `T` isn't
  a subtype of it, as `I` and `J` unrelated, a check that's an error of
  its own (below, "Run-time type checks").
- `!c` swaps `c`'s two.
- `x && y` (`if (x) y else false`): `y` is checked with `x`'s true
  facts. True: `x`'s and `y`'s true facts. False: what both `x` false and
  `y` false (after `x` true) give.
- `x || y` (`if (x) true else y`): `y` is checked with `x`'s false facts.
  False: `x`'s and `y`'s false facts. True: what both `x` true and `y`
  true (after `x` false) give, so `a != null || b != null` narrows
  neither.
- `true` and `false`: the side that can't happen holds everything.
- Anything else, such as a Boolean local (`let c = a != null; if (c)`),
  gives nothing.

**Statements.** Facts flow through a block in order. `if` checks its
then-branch with the condition's true facts and its else-branch with its
false ones; after it, what holds at the end of both branches
(intersection), with a type only where both give the same one, written
alike (`is D` in each, `D` the same declaration); otherwise just not
null. A branch that always leaves, by `return`, `break`,
`continue`, or `bubble()`, counts as holding everything, so after `if (a
== null) { return; }`, `a` isn't null. `panic()` doesn't count yet. Uses
after a statement that always leaves aren't narrowed. `when` and `??`
expand to `if` and `==`, so `when (a) { null -> …; else -> take(a) }`
and `a ?? ""` narrow.

**Loops and blocks.** A loop's body starts with the facts from before
it, and a `while` condition's true facts hold in the body (it's the
loop's first `if`). After a loop, only the facts from before it hold,
even after `while (a == null) { return; }`. After a block that a `break`
leaves (`label: do { if (a == null) { break label; }; … }`), only the
facts from its start hold. `orelse`'s fallback gets the facts from
before the `orelse`; after it, what both sides end with.

**Functions.** A function or class declared in a block (any `let` whose
value is a `fn`) may be called before its declaration, so its body gets only
the facts at the block's start. A function value elsewhere, as an
argument, gets the facts where it's written. So after `if (a == null) {
return; };`, `let g(): String { a }` reports `a`, but `run(fn ():
String { a })` doesn't.

**Nullable until narrowed.** Typing uses types, never values:
evaluation never changes typing. A nullable value isn't taken where a
non-null one is wanted (stored, passed, an operator's operand, a
member's object, or cast with `as`) until flow facts narrow it: a null
check, `??`, an early exit, or a call that never returns. So `take(c)`
for `let c: String? = "q"` is reported, as for a parameter `c: String?`.
Kotlin Temper folds names and calls of constants to their values before
checking them, so it accepts that, and `take(find("one"))` where `find`
gives 1 ([kotlin-differences.md](kotlin-differences.md)). A nullable
value may still be an `AnyValue`. A parameter with a default takes any
nullable value whose non-null type fits, since null selects the
default: every backend translates the parameter as nullable and puts
the default in for null.

**Narrower than Kotlin Temper**, which narrows only the branches of an
`if` whose condition is a null check or `is` check of a name, negated at
most once. Tempest accepts these, which Kotlin rejects, and the same
with `is` checks (`if (!(i is D)) { return; }; takeD(i)`):

- `&&`: `if (a != null && b != null) { take(a); take(b) }`
- double negation: `if (!(a != null)) { return; }; take(a)`
- early exits: `if (a == null || a == "") { return; }; take(a)`
- `while`: `while (a != null) { take(a); break; }`
- `bubble()`: `if (a == null) { bubble() }; take(a)`
- functions and closures after an early exit:
  `run(fn (): String { a })`

## What flow facts are used for

- **Typing:** the type of each use of a local, so null checks and `is` checks
  narrow the way people expect.
- **Errors:** null access, use before assignment, unreachable code, and casts
  that can never succeed, all with precise spans.
- **Generated code quality:** skipping runtime checks that facts prove
  unnecessary. The big one is integer overflow: Python and Lua route every
  `Int` operation through helper functions to emulate 32-bit wraparound, and
  ranges can show that most loop counters and indexes can't overflow, so they
  can use plain `+`. Also bounds checks and null checks in other languages.
- **Constant folding** where values are known.

## Type parameters are invariant

Temper plans to make all type parameters invariant, because C++ and Rust,
among others, can't express variance well. Tempest doesn't support
covariant or contravariant type parameters at all: `List<Sub>` is never a
`List<Super>`. Kotlin Temper's `@out` and `@in` annotations (about a dozen,
all in `core.temper`, as on `Generator<@out YIELD>`) parse as ordinary
decorations and are ignored, with no error. When copying `core.temper` and
the `std` library from Kotlin Temper, keep them as they are, so the files
stay easy to diff, and rely on this rule. Decided (2026-10-28): tempest
stays invariant, and Kotlin Temper should change to match
([kotlin-differences.md](kotlin-differences.md)). Until then, tempest
doesn't report what only variance accepts (below), so it gives no false
errors; once it's known what code relies on `@out` and `@in`, it can.

### To explore later: variance without runtime variance

Both ideas only accept more programs, so neither conflicts with starting
invariant.

1. **Use-site sugar for bounded generics.** Most wanted covariance is
   read-only use of a parameter. `let total(shapes: List<some Shape>)` could
   mean `let total<T extends Shape>(shapes: List<T>)`: monomorphized in Rust
   (`impl Trait`) and C++ (templates), and a wildcard or generic method in
   Java and C#. Zero runtime cost. Limits: parameters only, and not on
   overridable methods, since Rust's `dyn` traits and C++ virtual functions
   can't have generic methods. Temper already forbids generics on
   overridable methods ("Overridable methods don't allow generics"), so the
   sugar would follow the same rule. Classic Temper's Rust runtime takes the
   same approach for `map<O>` and friends: a non-generic trait, plus generic
   free functions over `&dyn ListedTrait<T>`. Core's own `Listed.map<O>`,
   `reduceFrom<O>`, `reduceFromIndex<O>` and `Mapped.toListWith<T>` break
   the rule (Kotlin exempts core). Tempest's typed core will declare them
   as extensions instead, keeping the same call syntax with static
   dispatch:
   `@extension("reduceFrom") let listedReduceFrom<T, O>(listed: Listed<T>, initial: O, accumulate: fn (O, T): O): O`,
   and `@connected` ones (`map`) as connected top-level functions. Then
   the rule holds everywhere, with no exemption. Accepted: Java and C#
   host code then sees these as free functions rather than methods,
   unlike classic Temper's output, which may make the same breaking
   change. Today the untyped
   interpreter runs them as built-in list methods (`interp.rs`), exercised
   by `types/list/reduce` and `imports/functions`.
2. **Adapters inserted at conversions,** for what's left: wrap a function
   value to accept a narrower argument (O(1), always valid); wrap an `@out`
   read-only interface in a view that upcasts elements on access, or an
   `@in` one in a view that upcasts inputs. The compiler knows each concrete
   cast, so it can emit it as a closure, which works in stable Rust; C++ can
   use converting constructors like `shared_ptr`'s. Costs: an allocation per
   conversion, dynamic dispatch on every access, and a view that isn't the
   same object as the original, so identity checks differ. Copying an
   immutable concrete list instead is correct but O(n). Only worth it if
   real code needs it.

## Generic types

What tempest does today (`types.rs`, `typing.rs`), the first step of
milestone 4's generics slice; status in [milestone-4.md](history/milestone-4.md).

- **Written types:** `List<Int>`, `Map<String, Int>` and `Box<String>`
  for a user's `class Box<T>` are generic types given arguments, shown
  in messages as written, `List<Int32>`. A class given the wrong number
  of arguments, a final builtin type given any, and `Never<T>` are
  unknown for now, as is a bare generic class, `Box`.
- **Type parameters:** `T`, in a function's or class's body, is a type
  of its own, a subtype of its bound only: `AnyValue` if it has none, or
  the type it `extends`. So a `T` is an `AnyValue` and a `T?`, but not an
  `Int32`, and no other type but `Never` is a `T`: not `Int32`, not
  null, not `T?`, and not another type parameter `U`. A bound that's
  itself a type parameter, as in `<T, U extends T>`, is unknown for now,
  so whether a `U` is a `T` goes unreported.
- **Arguments:** one generic type given arguments is another only if
  they're the same type. Since Kotlin Temper honors `@out` and `@in`
  (`List<@out T>`), where tempest's parameters are invariant ("Type
  parameters are invariant", above), a mismatch is reported only where
  the bases are the same and some pair of arguments is unrelated, neither
  a subtype of the other, which no variance accepts: `List<Int32>` isn't
  a `List<String>`, nor `List<T>` a `List<Int32>`, but `List<Dog>` as a
  `List<Animal>`, or `List<Int32?>` as a `List<Int32>`, isn't reported.
- **Builtin supertypes,** as core declares them: `List<T>` and
  `ListBuilder<T>` are `Listed<T>`s, `Map<K, V>` and `MapBuilder<K, V>`
  are `Mapped<K, V>`s, `SafeGenerator<T>` is a `Generator<T>`,
  `ValueResult<T>` and `DoneResult<T>` are `GeneratorResult<T>`s, and
  `String` and `Int32` are `MapKey`s of themselves (`types.rs`,
  `BUILTIN_SUPERTYPES`, checked against core by a unit test). So
  `List<Int32>` is compared with `Listed<String>` by its arguments, and a
  builtin generic type is no other builtin generic type: `ListBuilder<Int32>`
  isn't a `List<Int32>`, nor `Listed<Int32>` a `List<Int32>`. A generic
  type is an `AnyValue`, and isn't a final builtin type, a function, or
  null, nor, if its base is builtin, a user's class or interface; a final
  type is no generic type but the `MapKey`s above.
- **Users' supertypes:** a user's class or interface is each type it
  extends, as written, with its own type arguments put in for its type
  parameters, and theirs in turn. So for `interface Sup<T>`, `class
  Sub<T> extends Sup<T>`, `interface Sup2<T, U> extends Sup<U>`, `class
  Sub2<T> extends Sup2<T, String>`, and `class Sub4 extends Sup<String>`,
  a `Sub<Int32>` is a `Sup<Int32>`, a `Sub2<Int32>` a `Sup2<Int32,
  String>` and a `Sup<String>`, and a `Sub4` a `Sup<String>`. A value
  is compared with a generic type as that supertype (`supertype_as` in
  `types.rs`), by its arguments as above: a `Sub<String>` isn't a
  `Sup<Int32>`, nor a `Sub2<T>` a `Sup<T>`. Of several ways up to the
  same base, the first, depth-first in `extends` order, counts. A
  user's class or interface that doesn't extend another's, directly or
  not, isn't one, with or without arguments: a `Sub4` isn't a
  `Sub<String>`, nor a `Sup<String>` a `Sub4`. The same view of a
  supertype solves generic calls ("Generic calls", below) and types
  inherited members (below).
- **Calls and references:** a call of a generic function declared by
  `let`, of a generic method, or of a generic class's constructor is
  solved ("Generic calls", below). Elsewhere, as a reference to one
  (`let g = id`, `o.first` not called), its own type parameters are
  unknown.
- **Generic classes' values:** `new Box(1)` is a `Box<Int32>`, solved
  as a call, and `this` in `Box`'s body is a `Box<T>`, given its own
  type parameters. A member of a `Box<Int32>`, read (`bx.item`,
  `bx.get`), called, or assigned (`bx.count = v`), has its type with
  `Int32` for the class's `T`; one it inherits from a generic supertype
  has the arguments the value's class gives that supertype, so for
  `class StrHolder extends Holder<String>`, `sh.describe(x)`, declared
  in `Holder<T>` as `describe(x: T): String`, takes a `String`. A
  static member sees its class's type parameters as unknown, and a bare
  member name in the class sees them as written. A bare generic class, `Box`, is still unknown, as
  are core's generic classes declared in Temper, which are builtin
  types, whose members are typed from core's signatures ("Builtin
  types' members", below).
- **Narrowing:** `x is D` narrows a `T` to `D` where `D` is a subtype of
  `T`'s bound, as for a downcast of the bound.
- **Run-time checks** of generic types and type parameters' values are
  judged as "Run-time type checks", below, says: a type parameter's
  value by its bound, and a check can't introduce a type argument.

## Generic calls

What tempest does today (`typing/generic_calls.rs`), as Kotlin Temper's
solver does, for a call naming a generic function declared by `let`
(not `var`), as `id(1)` or `id<Int>(x)` for `let id<T>(x: T): T`; a
generic class's constructor, `new Box(1)` or `new Box<Int>(1)`, as a
function `<T>(T) -> Box<T>` of the constructor's parameters; and a
generic method, `o.first(xs)` for `first<T>(items: List<T>): T`, its
signature with the receiver's type arguments put in for its class's type
parameters; and a builtin generic type's generic method, as `ls.map(f)`
for core's `Listed.map<O>(transform: fn (T): O): List<O>`, likewise,
with the method inherited as the receiver's type sees it ("Builtin
types' members", below); status in [milestone-4.md](history/milestone-4.md).

- **Solving:** with as many type arguments as the function has type
  parameters, each binds to its argument, `T` to `Int32` in
  `id<Int>(x)`; with another number of them, none binds. Without type
  arguments, each argument's type is matched against its parameter's,
  part by part: `T` from `Int32`, `List<T>` from `List<Int32>`, or from
  a subtype as it sees that supertype ("Users' supertypes", above), as
  `ListBuilder<Int32>` or, for `Holder<T>`, a `StrHolder`, `class
  StrHolder extends Holder<String>` (Kotlin can't bind `T` from a
  non-generic class's supertype, a bug:
  [kotlin-differences.md](kotlin-differences.md#bugs-in-kotlin-temper)), `T?`
  from `Int32?` or `Int32`, and function types' parameters and results.
  A type parameter matched to two different types is left unknown,
  since Kotlin may find a common supertype (`pair(1, "a")` passes there),
  unless one is the other made nullable, as `Dog` and `Dog?` bind `Dog?`.
  Only fully known types bind: not null, `Never`, `Void`, or a function
  literal's type, which takes its own from the solved call ("Function
  literals and target typing", below). A nullable type binds a bare `T`,
  as in Kotlin, so `pick(d)` for a `d: Dog?` and `pick<T extends
  Animal>` is "Type formal <T extends Animal> cannot bind to Dog?",
  while it fits an `AnyValue` bound. What doesn't bind is unknown.
- **Values don't count:** a call with type arguments is solved from
  them whatever its arguments' values, so `let s: String =
  id<Int>("a")` reports the argument and the assignment. Kotlin folds a
  call of constants to its value first, and passes it
  ([kotlin-differences.md](kotlin-differences.md)).
- **From the type wanted:** where a call's value is stored in a
  declaration with a type or a field, returned, given as a function's
  result, or assigned, and its type doesn't fit the type wanted there, the type
  wanted is matched against the call's result first, part by part as
  arguments are, and binds what it can; the arguments then bind the
  rest and are checked against that ("Errors", below). So `let xs:
  List<String> = wrap(1)` is "Actual arguments do not match signature:
  <T extends AnyValue>(T) -> List<T> expected [String], but got
  [Int32]" at the argument, as is `let ys: Listed<String> = wrap(1)`
  (a `List<T>` is a `Listed<T>`). Only where the result isn't a bare
  type parameter (`T` or `T?`), which Kotlin solves from the arguments
  alone (`let c: String = id(1)` is "Cannot assign to String from
  Int32"), and where the call has no type arguments. Not for a call
  that's an argument, where Kotlin misses the error and tempest reports
  the argument as for any call ("Expected subtype of List<String>, but
  got List<Int32>"). If no argument is reported, the place's error is,
  as before.
- **The call's type** is the function's result with the bindings put
  in, so `let c: String = id(1)` is "Cannot assign to String from
  Int32".
- **Errors,** in Kotlin's words and at its positions, with type
  parameters named as written (Kotlin writes `T__0`): a binding outside
  its parameter's bound, "Type formal <T extends Animal> cannot bind to
  Int32 which does not fit upper bounds [Animal]", at the call, and
  then nothing else for the call; a bound that names the function's own
  type parameters, as `<T extends MapKey<T>>`, is checked with the
  bindings put in, and a nullable type fits `AnyValue`, as in Kotlin's
  check (`f<Int?>(null)` for `<T>`); the wrong
  number of arguments, "No callee matches inputs [Int32] among [<T
  extends AnyValue>(T, Int32) -> T]", at the call, where every
  argument's type is known; and arguments that don't fit the signature
  with the bindings put in, "Actual arguments do not match signature:
  <T extends AnyValue>(T, Int32) -> T expected [String, Int32], but got
  [Int32, String]", from the first argument that doesn't fit to the
  last, judged as other calls' arguments are (null shown as the wanted
  type made nullable). A function literal argument is checked so only
  where it declares its result type and isn't a trailing block that
  moves, as Kotlin checks it: `let b: List<Int> = ls.map(fn (x: Int):
  String { … })` is "… expected [Listed<Int32>, fn (Int32): Int32], but
  got [List<Int32>, fn (Int32): String]", at the literal. An
  instance's method's receiver is shown first in its
  signature, inputs and argument lists, as Kotlin does: "<T extends
  AnyValue>(Order, T, Int32) -> T expected [Order, Int32, Int32], but
  got [Order, Int32, String]"; for a builtin method, as the type
  declaring it sees it in the signature and expected list, and as it is
  in the others: "<O extends AnyValue>(Listed<Int32>, fn (Int32): O) ->
  List<O> expected [Listed<Int32>, fn (Int32): Int32], but got
  [List<Int32>, fn (Int32): String]". A type parameter that no argument
  binds is its bound, as Kotlin's solver binds it, so `ls.map(1)` wants
  a `fn (Int32): AnyValue`, and an expected list shows it so; but it's
  checked so only where every argument's type is known and none is
  `null` or `Void`, which Kotlin reports otherwise, and not where
  arguments bind it two ways or the call gives the wrong number of type
  arguments, nor where its bound names the function's type parameters.
  A method's explicit type arguments bind
  as a function's do, where Kotlin fails
  ([kotlin-differences.md](kotlin-differences.md#bugs-in-kotlin-temper)).
- **A function literal binds by its declared types,** where it declares
  its result type, as Kotlin does, so `ls.map { (x): String => … }` is
  a `List<String>`; one that doesn't binds by what its body gives, after
  the rest ("Function literals' results from their bodies", below). A
  type parameter that nothing binds takes what the type wanted binds
  ("Inference", below).
- **Builtin types' constructors** are solved the same way, from core's
  `constructor`, as `<K, V>(K, V) -> Pair<K, V>`, with their type
  parameters' bounds as core declares them, so `new Map<P, String>([])`
  for a class `P` is "Type formal <K extends MapKey<K>> cannot bind to P
  which does not fit upper bounds [MapKey<K>]", as in Kotlin; one core
  doesn't declare, as `T` in `Deque<T>`, is unchecked: `new Pair(1,
  "a")`, `new Map(pairs)`, `new ListBuilder<Int>()`, and through an
  alias, as std's `new TestResult(…)`. Bounds aren't checked in type
  expressions, as `let m: Map<P, String>`, as Kotlin doesn't either.
- **Not yet:** Kotlin's choice among two ways arguments bind a type
  parameter, as `ls.reduceFrom(0, addS)` for `addS(a: String, x: Int):
  String`, where Kotlin binds `O` to `String` and reports the `0`.

## Target typing: where it applies

Decided 2026-10-29:

- **Wherever an expected type could help,** a value takes it: object,
  list, and function literals at least, and also `new Box()` with
  nothing to solve its type arguments from, `[]`, `null`, `bubble()`,
  `panic()`, and other places where it fits. What's done so far is in
  the sections below.
- **The expected type never chooses between overloads,** for now, and
  there's no overloading on return type.
- **Function types will be named only:** Temper plans to keep only
  `@fun` interfaces as function types and drop bare ones (language.md,
  "Function types and fun interfaces"). Tempest keeps bare function
  types for now, to match the tests, but new code shouldn't lean on
  them, and design work should keep the named-only future in mind.

## List literals and target typing

What tempest does today (`typing/targets.rs`), the start of milestone 4's
target typing slice; status in [milestone-4.md](history/milestone-4.md). Kotlin
Temper sees a list literal as a call of `<listT extends AnyValue>(listT,
listT, …) -> List<listT>`, solved per call.

- **The type wanted** at a value's place: a declared type, for a
  declaration's, field's, or parameter's initializer or default; a
  local's type, for a value assigned to it, or a member's setter's; a
  function's declared result, or for a function literal without one, the
  result its place wants ("Function literals and target typing",
  below), for what it returns, and for its body's result, through the last expression of a block and the branches of an
  `if` (not its condition) and an `orelse`; a known callee's parameter,
  for an argument, where it's fully known, so not a generic function's or
  method's own type parameters, nor a generic class's constructor's,
  except a builtin type's that `new` gives its type arguments (`[]` in
  `new Map<Int, Int>([])` is a `List<Pair<Int32, Int32>>`, 2026-11-03),
  with a trailing block placed as Kotlin places it ("Function literals
  and target typing", below); an object literal's parameter, once its
  class is decided ("Object literals and target typing", below); and,
  for a list literal's
  element, the element type of the list its place wants. Elsewhere, as
  for an undeclared local's initializer or a builtin operator's
  operand, none.
- **A list literal's type** is a `List<E>`. Where the type wanted is a
  `List<X>` or `Listed<X>`, maybe nullable, with `X` fully known, and
  every element is known to be an `X`, `E` is `X`: so `[]` is a
  `List<Int32>` in `let xs: List<Int> = []`, and `[d]`, for a `d: Dog`,
  a `List<Animal>` in `let xs: List<Animal> = [d]`, as backends need
  where lists aren't covariant. Otherwise `E` is the elements' common
  type: their type, if they're all of one; made nullable if any is null
  or nullable (`[n, 1]`, for `n: Int?`, is a `List<Int32?>`); the type
  the others are subtypes of (`[d, a]`, for `a: Animal`, is a
  `List<Animal>`); with an element that never returns, as `panic()`,
  counting for nothing. Where there's none of these, as for `[d, c]`
  with `c: Cat`, `[1, "a"]`, an empty list, or a `Void` element, `E` is
  unknown, where Kotlin finds a common supertype, or `AnyValue`.
- **Errors,** in Kotlin's words and at its positions: where a
  declaration's or field's initializer, a value assigned, a value
  returned, or a function's result is a list literal and the type wanted
  there is a `List<X>` or `Listed<X>`, elements that can't be an `X`,
  judged as arguments are (null shown as `X?`), are "Actual arguments do
  not match signature: <listT extends AnyValue>(listT, listT) ->
  List<listT> expected [String, String], but got [Int32, String]", from
  the first to the last, and nothing else for the literal. For nested
  lists, only the outer one is checked so, as Kotlin does: `let xss:
  List<List<String>> = [["a"], [1]]` reports `[1]`, "expected
  [List<String>, List<String>], but got [List<String>, List<Int32>]".
  Elsewhere a list literal is checked as any value is, by its type:
  "Cannot assign to Int32 from List<Int32>" for `let n: Int = [1]`, and
  as an argument, "Expected subtype of List<String>, but got
  List<Int32>" for `g([1])`, which Kotlin misses, as for generic calls
  that are arguments.

## Function literals and target typing

What tempest does today (`typing/targets.rs`), the second step of
milestone 4's target typing slice; status in
[milestone-4.md](history/milestone-4.md). These are Kotlin Temper's rules
(`Typer.preTypeFunTree`, `applicationOrderForActuals`):

- **Where a trailing block goes:** where a call's last argument is a
  function literal, it goes to the callee's last parameter of a function
  type or a `@fun` interface's, maybe nullable, and the other arguments from that parameter's
  place on move up one. Only a required parameter takes it, unless the
  other arguments cover all the required ones, and none does where there
  are more arguments than parameters. So `maybe(2) { x => … }`, for
  `let maybe(f: fn (Int): Int, n: Int = 1)`, passes the block as `f` and
  `2` as `n`. Where that parameter is past the other arguments, those
  between take their defaults: `h(1) { … }`, for `let h(n: Int, s:
  String = "d", g: (fn (): Void)? = null)`, passes the block as `g`
  (Kotlin crashes on this, a bug:
  [kotlin-differences.md](kotlin-differences.md#bugs-in-kotlin-temper)).
  Any function literal last counts, in parentheses too, as in Kotlin.
  Typing places the arguments of every call it checks so: functions',
  methods', and constructors', and generic ones, with type arguments
  too (which don't count as places), but not a call with named
  arguments. The interpreter and tmpl pass them as typing placed
  them (`Typing::trailing_blocks`), null for those skipped; a call
  typing can't place, as of a callee whose type it doesn't know, passes
  them as written.
  The user prefers a stricter rule: a trailing block goes only to the
  last function-typed parameter, and only where every parameter after
  it is optional, so `takeTwo(2) { … }`, for `takeTwo(p: Pred, n:
  Int)`, would be an error rather than passing `2` as `n`. Kotlin's rule
  stays while Kotlin's tests rely on it; to reassess later
  ([testing-and-milestones.md](testing-and-milestones.md), "Risks and
  open questions"). Function types count as well as `@fun` interfaces
  for now, though Temper plans to keep only named fun interfaces.
- **The type wanted** by a function literal is its place's, as for other
  values ("List literals and target typing", above), but where it's an
  argument of a generic call, it's the parameter it's placed at, with
  what the call solves put in: from its type arguments, which settle
  it, or else its other arguments ("Generic calls", above), then, where the call has no type
  arguments, from the type its place wants, which binds first unless
  the call's result is a bare `T` or `T?`; a type parameter that none
  of these binds stays unknown. So in `ls.map { x => … }`, for `ls:
  List<Int>`, `x` is an `Int32`, and in `let ys: List<String> =
  ls.map { x => … }` the result wanted is a `String`. A nullable type
  wanted counts as its non-null type, and a `@fun` interface as the
  function type it stands for ("Fun interfaces", below). Not a
  generator's factory. A `for … of` body is typed as `forEach`'s block,
  so its variable is the element type.
- **A function literal's type:** a parameter without a declared type is
  the wanted function type's parameter in its place, or `AnyValue` past
  its last; without a declared return type, the literal gives the wanted
  type's result, and may bubble if it may. What's declared stays. Its
  parameters are typed so in its body, so `{ x => x + "a" }` where a `fn
  (Int32): Int32` is wanted is "Actual arguments do not match signature:
  (Int32, Int32) -> Int32 expected [Int32, Int32], but got [Int32,
  String]", as for any `+`.
- **A literal's result binds** a generic callee's type parameter that
  nothing else binds (other arguments, type arguments, or the type the
  call's place wants), from its body, so `let m = ls.map { x => x * 2
  }` is a `List<Int32>` (2026-11-24; "Function literals' results from
  their bodies", below). Kotlin binds it to its bound, a `List<AnyValue>`,
  as tempest did from 2026-11-06; so `let ys: List<AnyValue> = m` now
  fails, as type parameters are invariant.
- **Errors,** in Kotlin's words and at its positions: a literal without
  a declared return type whose result isn't the one wanted is "Expected
  subtype of Int32, but got String", at the result, where Kotlin's
  position is empty, at the start of the body
  ([kotlin-differences.md](kotlin-differences.md#changes-we-recommend-to-temper)).
  Its result is its body's last expression, through nested blocks, as
  one value, so an `if`'s branches are joined: `if (c) { "a" } else
  { 2 }` has no type tempest knows, where Kotlin says "but got
  AnyValue". Not where `Void` is wanted, which Kotlin reports for users'
  functions but not for builtin methods with an `@inlineUnrealizedGoal`
  parameter, as `forEach`, nor for a result that's null, `Void`, or
  never returns, which Kotlin reports with odd types ("but got
  Never<Empty>?", "but got Top"). A literal with a declared return type
  is checked as other functions are. A literal argument is checked
  against the parameter it's placed at, as other arguments are, at the
  literal: `apply { (s: String): Int => 1 }`, for `apply(f: fn (Int):
  Int)`, is "Expected subtype of fn (Int32): Int32, but got fn (String):
  Int32", and `apply { x, y => x }` "… but got fn (Int32, AnyValue):
  Int32". The other arguments of a call with a trailing block are
  checked too, each at the parameter it's placed at. A generic call's
  arguments still aren't, where one is a function literal.
- **`return` in a literal** without a declared return type gives the
  result wanted, so `[]` returned where a `List<Int32>` is wanted is
  one, and a value returned that isn't one is "Expected subtype of
  Int32, but got String", at the value, each `return` on its own: for
  `fn (x) { return "a"; }` where a `fn (Int32): Int32` is wanted, or
  `fn (x) { return x; }` where a `fn (Int32): Void` is. A bare `return`
  gives `Void`, reported at the `return`, and null is shown as the
  nullable result wanted ("but got Int32?"); a value that never
  returns, as `panic()`, fits. Kotlin reports these at an empty position
  just after the `return`, and joins the types of all a literal's
  results first, so `fn (x) { if (x > 0) { return x; } return null; }`
  is "but got Int32?" at the first `return` there, and at the `null`
  here
  ([kotlin-differences.md](kotlin-differences.md#changes-we-recommend-to-temper)).
  A `return` in a block lambda, `{ x => return 1; }`, returns from the
  lambda in tempest, as from a `fn` literal, and is checked so; in
  Kotlin it's an "unrealized goal" that would leave the function around
  the lambda, which works only where the lambda is inlined, at an
  `@inlineUnrealizedGoal` parameter, as `forEach`'s, and is "Expected
  function type, but got Function" elsewhere
  ([kotlin-differences.md](kotlin-differences.md#language-and-types)).
- **Bubbling in a literal** without a declared return type: where its
  place is a generic call's failure parameter, the literal fails as its
  body does, and so does the call ("Function literals' results from
  their bodies", below). Elsewhere, where the
  function type wanted doesn't `throw Bubble`, what may bubble in its
  body is "Cannot bubble from a function without Bubble in its return
  type", as for a function that declares its return type ("What may
  bubble", below), so `apply { x => bubble() }`, `apply { x => x / 0
  }`, and `eat { x => s.toInt32(); }`, for `eat(f: fn (Int): Void)`, are
  reported, wherever the call is, even in a function that may bubble;
  where it does, as for `applyB(f: fn (Int): Int throws Bubble)`, they
  aren't. A literal with no function type wanted isn't checked.
  Core's `forEach` takes its block at an `@inlineUnrealizedGoal`
  parameter, whose type tempest reads, but `ls.forEach { x => bubble();
  }` isn't reported yet, as `forEach` wants a `Void` result; Kotlin
  inlines it, so it's reported where the function around it doesn't
  `throw Bubble`, as in a `for … of` body.

## Function literals' results from their bodies

Decided 2026-11-24 (RFC 164 chunk 2d), for function literals passed as
arguments only, not yet for functions in general. A literal without a
declared result type, in a generic call (`typing/generic_calls.rs`),
gives the callee's type parameters what its body gives:

- **What it gives:** its result type, its body's value joined with what
  each `return` from it gives (`literal_pass`), and what it may fail
  with, as "What may bubble" finds it (`literal_failure`): `Bubble` if
  anything in it may bubble, else the join of its declared failures
  ("Declared failures", below). So `ls.map { x => 10 / x }`
  solves `O` to `Int32` and `E` to `Bubble`, and the call fails, so it
  must be handled where it's called; `ls.map { x => x + 1 }` leaves `E`
  unsolved, `Never<…>`, and can't fail.
- **Which type parameters:** those in the result of the function type
  the literal is placed at, `O` and `E` in `fn (T): O throws E`, but in
  no such literal's parameters' types (`literal_formals`). So types
  flow from the other arguments into literals' parameters and from
  literals' bodies out to the call, never from one literal's body into
  a literal's parameters.
- **When, the cycle:** a literal's body needs its parameters' types,
  which come from the call, and the call's solution needs the body.
  The call is solved in order: type arguments, which win, else the
  other arguments, then the type its place wants (as before), then each
  literal's body, one at a time, in order, for what's still open; what
  stays open takes its bound, as before. A literal's place, the type its
  parameters and body see, comes from the first steps alone
  (`wanted_literal_arg`), so typing a body never waits for itself. Its
  own type is the full solution's (`literal_type`, `solve_fully`).
  Whether a body may fail is worked out only once its expressions'
  types are known: in typing's round it waits for them, as other types
  do.
- **What's declared wins:** type arguments, a declared result, and the
  type the call's place wants. A literal whose result the place wants
  is checked against that, as before: `let ys: List<String> = ls.map {
  x => x + 1 }` is "Expected subtype of String, but got Int32". A result
  outside its type parameter's bound isn't taken, so the literal is
  checked against the bound: "Expected subtype of Listed<Int32>, but got
  String". Kotlin Temper binds such a type parameter to its bound
  ([kotlin-differences.md](kotlin-differences.md#changes-we-recommend-to-temper)).
- **When it can't decide:** a result whose type can't be named, as an
  `if` with an `Int32` and a `String`, or null, takes the bound, as
  Kotlin does. A failure parameter the literal's parameters need too, as
  `E` for `retry { again => … }` and `retry<E>(f: fn (Thunk<E>): Int
  throws E)`, is `Never<…>`, and a literal that may fail there is
  "Cannot infer E from this function literal, as its parameters' types
  need it; declare its result type, with throws if it may fail", at the
  literal.
- **Type arguments:** a call with them gives its literals everything
  from them, as a non-generic call does from its parameters (t993,
  2026-11-24): `apply<Int, String, Never<Bubble>>(n) { x => x + 1 }`
  types `x` as an `Int32` and wants a `String` result ("Expected subtype
  of String, but got Int32"). Where it gives a failure parameter as a
  `Never`, a literal whose body may fail is an error at the literal:
  `apply<Int, Int, Never<Bubble>>(n) { x => 10 / x }` is "This function
  literal may fail, but its call gives E as Never<Bubble>; give E a
  failure type, or handle the failure in the literal". Its list and
  object literal arguments take their types from them too, so
  `count<Int>([])` passes a `List<Int32>`. The wrong number of type
  arguments solves nothing, so its literals' types are unknown.
- **Not yet:** literals given to non-generic functions, or not passed,
  still take their place's type or none.

## Object literals and target typing

What tempest does today (`typing/object_literals.rs`), a step of
milestone 4's target typing slice; status in
[milestone-4.md](history/milestone-4.md). Kotlin Temper picks an object
literal's class by names alone, while macros expand
(`ConvertObjectLiteralNew`); tempest decides it by the type wanted and
the names together (language.md, "Object literals, not named arguments"):

- **When:** a literal with `class: C` is placed as soon as `C` is
  known, while macros expand. Any other waits, in typing's round, for
  the type its place wants, from the places "List literals and target
  typing" lists, above, or for knowing there's none, and only then
  matches its names against constructors. Meanwhile its values settle,
  apart from what needs its class: its type, and the types its values'
  places want, so a nested object literal, a `[]`, or a function
  literal among its values waits for its class too.
- **Which class:** the classes found are those visible from the
  literal, imported ones too, whose constructors take every name given
  (their parameters, or the header's; body fields don't count). Where
  the type wanted is a class `C`, maybe nullable or given type
  arguments, and `C`'s constructor takes every name, the literal makes a
  `C`, even if several classes were found, or `C` isn't visible by
  name. Where it's an interface `I`, the literal makes the one class
  found that implements `I`. Otherwise, where a builtin type, a type
  parameter, or nothing is wanted, it makes the one class found. Only
  classes' constructors count, for now.
- **Errors:** where a class or interface is wanted, none of the classes
  found fits it, and exactly one other class was found, "A is wanted
  here, but these properties fit Mark's constructor", at the literal,
  rather than silently making the other class; Kotlin reports "Cannot
  assign to A from Mark" there. Otherwise, as Kotlin does: "No signature
  matches" where nothing fits, and "Multiple types have matching
  constructors: A, B" where several classes do and the type wanted
  picks none of them; then, for the class decided, "No value passed for
  x" for a skipped parameter without a default.
- **What it becomes:** a plain constructor call, `new C(1, null, y)`,
  its arguments placed, null for skipped ones (so their defaults), and
  where values run in an order other than the parameters', through
  temporaries, `do { let t1 = b; let t2 = a; new C(t2, t1) }`, whose
  values' places want what the arguments' would. No `\a` markers are
  left.

## Function types

What tempest does today (`fn_subtype` in `types.rs`); status in
[milestone-4.md](history/milestone-4.md). Kotlin Temper compares function types
part by part, its parameters contravariant and its result covariant, as
interfaces keyed by how many required and optional parameters they take
and whether they may bubble. Tempest reports a function type `S` where a
different function type `T` is wanted only where that rejects it, in
Kotlin's words and at its positions, wherever a value's type is checked
("Cannot assign to fn (Int32): Boolean from fn (String): Boolean";
"Expected subtype of …" as an argument):

- `S` may bubble and `T` doesn't: a `fn (Int32): Int32 throws Bubble`
  isn't a `fn (Int32): Int32`. The reverse passes. (Recheck against a
  nightly once Kotlin Temper's move to its newer types lands, PR #500:
  its assignment check may then reject the reverse too, matching
  function types with different bubbling only by exact signature.
  Tempest keeps passing it unless the team decides otherwise.)
- They take different numbers of parameters, counting `S`'s optional
  ones: `fn (Int32, _?: Int32): Int32`, for `let opt(x: Int, y: Int =
  2): Int`, isn't a `fn (Int32): Int32`, but may be a `fn (Int32,
  Int32): Int32`, as Kotlin accepts. An optional parameter of `T`, which
  no type written out has, isn't compared.
- A parameter of `T` isn't a subtype of `S`'s in its place (`fn (Dog):
  Int32` isn't a `fn (Animal): Int32`, nor `fn (Int32): Boolean` a `fn
  (Int32?): Boolean`), or `S`'s result isn't `T`'s (`fn (Int32): Animal`
  isn't a `fn (Int32): Dog`, nor `fn (Int32): Boolean` a `fn (Int32):
  Void`), judged as anywhere else, so `fn (Int32): List<Int32>` isn't a
  `fn (Int32): List<String>`.

What only that variance accepts, as `fn (Animal): Dog` for a `fn (Dog):
Animal`, or `fn (Int32): Int32` for a `fn (Int32): Int32 throws Bubble`,
isn't reported, but isn't known to be a subtype either, as for generic
types ("Type parameters are invariant", above), until it's settled
whether function types are invariant too; adapters could keep it working
there (above, "To explore later"). As function types are interned, equal
ones are the same type. Function literals are checked as other values
are, stored, returned (`let f: fn (Int): Boolean = fn (s: String):
Boolean { … }`), or passed, but not yet as a generic call's arguments
("Function literals and target typing", below). A `@fun` interface's
type is compared with a function type as the function type it stands
for, but never with another `@fun` interface's that way ("Fun
interfaces", below).

## Fun interfaces

What tempest does today (`fun_subtype` in `types.rs`,
`typing/fun_interfaces.rs`, and `typing/targets.rs`); status in
[milestone-4.md](history/milestone-4.md). An interface marked `@fun`, as `@fun
interface Mapper<A, B>(a: A): B;`, is a named function type (language.md,
"Function types and fun interfaces"): its type stands for its `apply`'s
signature, with the type's arguments put in, so a `Mapper<Int32,
String>` for `fn (Int32): String`. The long form, `@fun interface P {
apply(x: Int): Boolean; }`, is the same as the short, `@fun interface
P(x: Int): Boolean;`: `@fun` is metadata plus the `apply` convention.

- **Nominal** (decided 2026-10-29, a deliberate difference from Kotlin,
  which compares them as function types): a value of one `@fun`
  interface is never another's, even where their `apply`s' signatures
  match. Where it would fit but for the names, the error suggests the
  adapter, "A Pred isn't a Check; to adapt it, wrap it: `fn (y) { p(y)
  }`", with the wanted `apply`'s parameter names, and the value's name
  if it's a name (else `f`), at any site a value is checked, a generic
  call's arguments included. Otherwise it's Kotlin's "Cannot assign to
  IntPred from Pred". Of one declaration, they're compared as generic
  types are. Backends may later collapse adapters where identity isn't
  observed.
- **Functions:** a function is a `@fun` interface's value, and such a
  value a function, where the function types fit, compared part by part
  ("Function types", above), as bare function types stay structural
  until they're removed: `let p: Pred = isPos`, for `@fun interface
  Pred(x: String): Boolean` and `isPos(n: Int): Boolean`, is "Cannot
  assign to Pred from fn (Int32): Boolean", and `let f: fn (Int):
  Boolean = p` "Cannot assign to fn (Int32): Boolean from Pred".
- **Target typed:** a function literal, `fn (…) { … }` or a trailing
  block, and a reference to a declared function or method (`isPos`,
  `k.isBig`, or a bare `isBig` in its class, but not a `var` or a
  parameter), takes the `@fun` interface its place wants as its type,
  where its function type fits, as Java types lambdas and method
  references: `keep(isPos)`, for `keep(p: Pred)`, passes `isPos` as a
  `Pred`. A value already typed as one interface needs the adapter to
  become another.
- **A function literal with no type wanted** is an error for now: one
  that initializes a `let` or `var` without a declared type, as `let f =
  fn (x: Int): Boolean { x > 0 };`, is "f needs a declared type, such as
  a @fun interface, since a function literal takes its type from the
  type wanted". A declared function, `let f(x: Int): Boolean { … }`,
  gives the same tree and is told apart by its source text. Only one
  stage test in Kotlin's repo has one (`syntax-macro/let-of-fn`, twice),
  none of the functional tests or libraries (2026-10-29).
- **Other classes:** a function is no other class's or interface's
  value, nor theirs a function, even a class's that extends a `@fun`
  interface: `let f: fn (String): Boolean = new C()`, for `class C
  extends Pred`, is "Cannot assign to fn (String): Boolean from C". An
  interface written `interface Plain(x: Int): Int;` without `@fun` is an
  interface with an `apply` method, and no function's type.
- **Calls:** a `@fun` interface's value is called as the function it
  stands for, so `p(1)` is "Expected subtype of String, but got Int32".
- **Function literals and trailing blocks:** where a `@fun` interface is
  wanted, a function literal takes its types from the function type it
  stands for, as from a function type ("Function literals and target
  typing", above), and a trailing block goes to a parameter of one, so
  `takeTwo(2) { x => … }`, for `takeTwo(p: Pred, n: Int)`, passes the
  block as `p`.
- **Generic calls:** a function where a generic `@fun` interface is
  wanted binds its type parameters as the function type it stands for
  would ("Generic calls", above), so `applyM(1, isPos)`, for `let
  applyM<A, B>(x: A, m: Mapper<A, B>): B`, is a `Boolean`. Kotlin fails
  there ([kotlin-differences.md](kotlin-differences.md#bugs-in-kotlin-temper)).
- **Declaration rules,** as Kotlin documents them (its `FunDecorator.kt`)
  but doesn't check, so backends may make one a named interface or a
  plain function type: "@fun interface Sup may extend only AnyValue, not
  Other"; "@fun interface Two may declare only an apply method, not
  method other" (or `property n`, `getter g`, `setter s`, `static k`);
  "@fun interface NoApply must declare an apply method"; and "@fun
  interface Js may not be @json, which adds members". A type parameter
  of `apply` is Kotlin's error for any interface's method, "Illegal type
  parameter T. Overridable methods don't allow generics".
- **No `is` or `as`:** `x is Pred` and `x as Pred` are "@fun interfaces
  cannot be targeted with is or as runtime type checks, because backends
  may make them plain function types: <[Pred]> from AnyValue", in the
  form of Kotlin's other run-time check errors; Kotlin accepts them.

## `Never<T>`: bottom with a fallback

Temper has no single bottom type. `core.temper` declares
`sealed interface Never<@out T>`, and the `T` says what a backend
without a bottom type (Java, C#, C++, …) should write in its place:
`Never<String>` becomes `String` there, and a bottom type where there is
one. These are Kotlin Temper's rules (`type2/TypeContext2.kt`,
`Reconciliation.kt`, `TypeSolver.kt`, `Type2.kt`, and
`value/NullaryNeverFn.kt`):

- **Subtyping:** `Never<T>` is a subtype of `T`, and so of `T`'s
  supertypes and `AnyValue`, but not of anything else, and `T` isn't a
  subtype of `Never<T>`. `Never<Never<T>>` normalizes to `Never<T>`.
- **Nullability moves out:** `Never<T?>` normalizes to `Never<T>?`, a
  type whose only value is `null`.
- **Joins and meets unwrap:** the join (least upper bound) of `Never<A>`
  and `B` is the join of `A` and `B`, and the meet is `Never<` the meet of
  `A` and `B` `>`. So an `if` whose one branch returns and the other is a
  `String` gives `String`.
- **Calls that never return:** `panic()` and `bubble()` have two
  signatures, `(): Never<Void>` (`bubble`'s with `throws Bubble`) and
  `<T>(): Never<T>`, so `T` is inferred from what the call's place
  wants, as in `let x: Int = panic()`. Kotlin's inserted
  `voidishPanic` (a missing `else` of a `when`) works the same way.
- **Inference prefers the plain type:** a type variable with lower
  bounds `Foo` and `Never<Foo>` gets `Foo`. One with only `Never` lower
  bounds waits for other constraints, and only then becomes `Never<…>` of
  what its bounds unwrap to. Seeing `Foo <: a` for an invariant concrete
  class `Foo` also adds `a <: Never<Foo>`.
- **Void-like:** `Never<Void>` counts as `Void`, as for `Result<Void,
  …>`, when choosing between overloads by whether a result is used.
- **Errors Kotlin means to add** (TODOs there): `Never<Never<T>>`
  written out, and `<T extends Never<T>>`.

**Tempest today** has one bottom, `TypeData::Never` (`types.rs`), a
subtype of every type, used for `return`, `panic()`, and the like and
for a bare `Never`. A written `Never<String>` is `Never` for now, as
std's `bail(): Never<Void> throws Bubble`. Both accept everything Kotlin accepts,
since Kotlin's `Never<T>` is a subtype of fewer types, but a backend
can't get its fallback type, and a `Never<A>` used as an unrelated `B`
isn't reported. Before code generation (milestone 5), or reporting that,
`Never` should carry its `T`, with the rules above, likely alongside the
generics slice in [milestone-4.md](history/milestone-4.md).

**Open: is `Never` covariant?** Kotlin honors its `@out`, so there
`Never<Sub>` is a `Never<Super>`; tempest ignores `@out` (above). Only
that one relation depends on it: `Never<Sub>` is still a `Sub`, and so a
`Super`, either way. Making `Never` the one exception looks safe, since
it has no values for variance to go wrong on, and a backend's fallback
`Sub` to `Super` is an ordinary upcast. Not yet decided; if `Never`
stays invariant, code relying on it would become an error that Kotlin
doesn't report.

## Inference

- **Locals without annotations** take the type of their initializer, as
  in Kotlin Temper, so `var a = 1; a = "s"` is an error. Widening to a
  declared supertype where later assignments need it would only accept
  more programs, so it can come later if wanted.
- **Generic calls** solve their type arguments from argument types and the
  expected result type at that call site. The solver is small: unify, collect
  bounds, pick the most specific type that satisfies them. Today it
  solves from type arguments and arguments' types ("Generic calls",
  above), and then a type parameter they leave unknown from the type
  wanted, where the call has no type arguments: `let b:
  ListBuilder<Int> = new ListBuilder()` makes a `ListBuilder<Int32>`.
- **Fields** without a declared type take their initializer's, as
  locals do, static ones too: `static srgb = new Space(…)`.
- **Type aliases:** a top-level `let` naming a type, as `let TestCase =
  Pair<TestName, TestFun>` or `let TestName = String`, is that type.
- **Function signatures** come from annotations, which Temper requires at
  module boundaries, so libraries typecheck independently. A function
  needs a return type unless its context gives it one, as a parameter
  does for a function passed to it ("Explicit return type required").
- **In the loop:** a declaration's inferred type is a fact
  (`Fact::Type`), published once its initializer's type is known;
  declared types need no waiting. Anything not worked out is unknown and
  causes no error, so typing can grow a piece at a time. Status and
  differences from Kotlin are in [milestone-4.md](history/milestone-4.md).

## Joins, and sealed types and `when`

Where a value comes from either of two branches (`if`, `orelse`, a
`when`'s cases, a list literal's elements), its type is their join
(`Types::join`), as Kotlin's least upper bound: the same type; nullable
where one is null or nullable; and for two classes or interfaces (or
builtin types, as `List` and `ListBuilder`), the one supertype of both
that's below every other, with the same type arguments for both, as
`E` for an `A` and a `B` that both extend `E` (2026-11-06). Where there's
no such one, or two, it's unknown, a gap rather than a guess.

With nothing wanted, a branch that's `[]` takes the other branch's list
type, as Kotlin's solver finds it: `when (b) { is H -> b.kids; else ->
[] }` is a `List<Inline>`.

A sealed interface's subtypes are the classes and interfaces of its
library that extend it directly. An `if` chain without a final `else`,
as `when` makes, whose `is` checks of one name cover every subtype of a
sealed interface that the name's type is, never reaches its missing
`else` (`typing/sealed.rs`): its value is the join of its branches',
wherever it's a value, a function's result too, which doesn't fall off
its end there (2026-11-23), and in tmpl the `else` is a `panic()`, even
where the chain is a statement, as Kotlin's `anyExhaustiveSealedType` and
`replaceVoidishPanics` make it. Checks of another name end the chain's
scan; other checks, of `==`, don't count. Otherwise a missing `else` is
`void`, and a value that needs one is unknown.

## Builtins' signatures

What tempest does today (`typing/signatures.rs`), as Kotlin Temper does;
status in [milestone-4.md](history/milestone-4.md). Operators have variants, in
this order (Kotlin's `BuiltinOperatorSpecs.kt`):

- `+`, `-` and `*`: `(Int32, Int32) -> Int32`, then `Int64` and
  `Float64` likewise; `+` and `-` with one operand, `(Int32) -> Int32`
  and so on.
- `/` and `%`: the same three, each returning `T throws Bubble`.
- `&`, `|` and `^`: `Int32` and `Int64`; `~`: `(Int32) -> Int32` and
  `(Int64) -> Int64`; `<<`, `>>` and `>>>`: `(Int32, Int32) -> Int32` and
  `(Int64, Int32) -> Int64`.
- `<=>`, and `<`, `<=`, `>` and `>=`, which Kotlin desugars to it:
  `Int32`, `Int64`, `Float64`, `String` and `Boolean` pairs, each
  `-> Int32`.
- `==` and `!=`: the same five pairs, each `-> Boolean`.
- One each: `!`, `(Boolean) -> Boolean`; `**`, `(Float64, Float64) ->
  Float64`; and `console.log`, `(String) -> Void`.

**Picking a variant,** as Kotlin's solver does: go through the
arguments in order, and for each, drop the variants it can't fit, last
first, until one is left. An argument fits a parameter only if its type
is the parameter's: a nullable value or `null` fits none. So `"a" + 1`
picks `Int32` (the String rules out `Float64`, then `Int64`), and `1.0 +
1` picks `Float64`. The call's type is that variant's result.

**Errors.** An operator reports "Actual arguments do not match
signature: (Int32, Int32) -> Int32 expected [Int32, Int32], but got
[Int32, String]", spanning the first argument that doesn't fit to the
last, with `null` shown as the parameter's type made nullable. One with
a single signature reports each argument that doesn't fit as a call of a
function does ("Expected subtype of Boolean, but got String");
`console.log`'s arity error says "Expected 1", where Kotlin counts
`this` and says "Expected 2".

**Checked only where every argument's type is known as Kotlin knows
it:** a final builtin type, nullable or not, `null`, or a class or
interface, nullable or not, which fits no variant (named as written,
`[C, Int32]`). Not for a function, generic or `AnyValue` operand, nor
`Never`; not for `==` or `!=` with a nullable or `Void` side, which
Kotlin expands first; not for a compiler temporary, as `x++` reads; and
not for `==`, `!=`, `<=>` or a comparison whose first operand is a
non-null value of a class with a method marked `@operator`, nor of an
interface (below). Then the result's type is unknown too.

### Operators defined in Temper

Kotlin Temper has begun marking methods that implement an operator with
`@operator("…")`, so that eventually every operator's meaning can be
specified in Temper itself rather than in the compiler. Today that's
only `==` and `<=>`: `Equatable`'s `eq` and, for example,
`StringIndexOption`'s `eq` and `compareTo` in `core.temper`, and a few
`eq` methods in std's regex. The method keeps an explicit name of its
own (`eq`, `compareTo`), following Temper's general rule that everything
has an explicit, unique name by default, which eases translation into
each language; the operator is a second way to reach it. Tempest keeps
the table above for builtin types until core declares those in Temper
too.

What tempest does today, as Kotlin Temper does (`typing/signatures.rs`,
and `class_operator` in `interp.rs`):

- **Which method:** `a == b` and `a != b` call the method marked
  `@operator("==")`, and `a <=> b`, `a < b`, `a <= b`, `a > b` and `a >=
  b` the one marked `@operator("<=>")`, of `a`'s class: its own, or the
  nearest inherited one, breadth-first, called by name, so an override
  without the mark runs. A class with `@auto("==")` and no such method
  has the `eq` Kotlin's `AddAutoMembers` makes, which the frontend
  generates as a real member (2026-10-30; `classes.rs`,
  `make_auto_eq`): `public @operator("==") eq(other: C): Boolean`,
  comparing each public instance field with its own `==` (a field's
  class's `@operator("==")` method, if it has one), `true` with none.
  An `@auto("==")` interface gets an abstract `eq(other: I)`, and each
  class below it without `==` of its own an `eq(other: I)` that checks
  `other is C` first, as Kotlin's `addAutoEqForSuperSealedIfWanted`
  does; with more than one such interface above it, Kotlin's "Auto
  method cannot support multiple supertype(s): Root1, Root2" instead.
  Not yet: generic classes' (their `other` needs type arguments), and
  Kotlin's checks that the hierarchy is sealed. Other operators
  can't be marked yet. When `b` is `null`, `==`
  and `!=` are null checks, never calls.
- **Typing:** with a non-null first operand of such a class, the call is
  checked as a call of that method, the class or interface declaring it
  as `this`: "Actual arguments do not match signature: (H, H) -> Boolean
  expected [H, H], but got [H, G]", at the second operand, including a
  nullable second operand or `null` (`[C, C?]`) for `<=>` and the
  comparisons, but not for `==` and `!=`, which Kotlin expands to null
  checks first. Otherwise the builtin variants apply, which no class's
  value fits. Where Kotlin then reports `k == k` for a class without
  the method as "… (Int32, Int32) -> Boolean expected [Int32, Int32],
  but got [K, K]" (temperlang/temper#508), tempest says what's missing,
  as temperlang/temper#515 proposes: "K does not support infix `==`;
  give it a method marked `@operator("==")`" (`@operator("<=>")` for
  `<=>` and the comparisons, naming the operator as written). A nullable
  first operand, even of a class with the method, keeps Kotlin's
  message (`[H?, H]`), as does a first operand of a builtin type.
- **Interfaces** are checked as classes are, since `is` narrows a value
  to a class with the method where wanted: `i == j` for an interface
  without the method is "I does not support infix `==`; …".
- **Generic classes** are checked as other classes are, with the
  receiver's type arguments put in: `e == f` for an `Eq<Int>` and an
  `Eq<String>` is "… (Eq<Int32>, Eq<Int32>) -> Boolean expected
  [Eq<Int32>, Eq<Int32>], but got [Eq<Int32>, Eq<String>]", and `b == b`
  for a `Box<Int>` without the method is "Box<Int32> does not support
  infix `==`; …" (2026-11-16; before, `==` compared identities there,
  even with the method, and `<` stopped translation).
- **Not checked:** a first operand of a class or interface with a
  supertype typing doesn't know.

### Builtin types' members

The final builtin types' members (`Boolean`, `Int32`, `Int64`, `Float64`
and `String`) and the generic ones' (`List`, `ListBuilder`, `Listed`,
`Map`, `MapBuilder`, `Mapped`, `Pair`, `Deque`, `Generator`,
`SafeGenerator`, `GeneratorResult`, `ValueResult`, `DoneResult`,
`Promise` and `PromiseBuilder`), and `StringBuilder`'s, `StringIndex`'s,
`StringIndexOption`'s, `NoStringIndex`'s, and `DenseBitVector`'s, have
the signatures core declares for them, in Kotlin Temper's `core.temper`
(`typing/core_members.rs`), static `let`s, as `String.begin`, and
decorated parameters, as `forEach`'s, included:
`x.m(…)` calls an instance's public method, `x.p` reads its field or
getter, and `T.m(…)` and `T.p` a static one, as
`String.fromCodePoint(c)` and `Float64.pi`. A method's parameter with a
default is optional and typed as nullable, as for classes' methods, and
its arguments, arity and bubbling are checked as a function's are;
arity doesn't count `this`. `Int.m` is `Int32.m`, as core's `Int` is
`Int32`.

- **Types in signatures:** builtin types, maybe given arguments
  (`List<String>`), nullable, or function types (`fn (T): Boolean`), and
  the type's own type parameters, which take the receiver's type
  arguments: `ls.get(i)` for `ls: List<Int>` is an `Int32`, and
  `m.keys()` for `m: Map<String, Int>` a `Listed<String>`. A method's
  own type parameters, as `O` in `map<O>(transform: fn (T): O):
  List<O>`, none of which has a bound in core, are solved per call
  ("Generic calls", below), and unknown elsewhere, as in `let g =
  ls.map`. Anything else is unknown: a static member's use of the
  type's parameters, all of them for a generic type given the wrong
  number of arguments, and a method's own type parameter with a bound.
- **Inherited members:** an instance's member that a builtin generic
  type doesn't declare is looked up in the builtin generic type it
  extends with the same arguments ("Generic types", above): `List`'s and
  `ListBuilder`'s in `Listed` (`ls.isEmpty`, `ls.slice(0, 1)`), and
  `Map`'s and `MapBuilder`'s in `Mapped` (`m.get(k)`); and
  `StringIndex`'s and `NoStringIndex`'s in `StringIndexOption`.
- **Indexing:** `a[i]` is a call of `a.get(i)`, as Kotlin Temper
  desugars it, for a builtin type's value or a class's (`m[k]` for a
  `Map` may bubble, as `Mapped.get` `throws Bubble`; `ls[i]` doesn't);
  `a[i] = v` is already `a.set(i, v)` by then.
- **Not typed:** a member core doesn't declare (`x.foo()`), a nullable
  receiver's (`xs.length` for `xs: List<Int>?`, where Kotlin says "No
  member length in AnyValue"), and other builtin types' members.
- **Other builtins:** `console.log(…)` is `Void`, `char'a'` an `Int32`,
  `raw"…"` a `String`, `empty()` an `Empty`, `ignore(x)` `Void`, and
  `doPure { … }` its block's result; and `new T(…)` of a builtin type
  with a constructor, as `new StringBuilder()`, a `T` ("Generic calls",
  above, for generic ones).

## Results

As built (2026-11-23): a function that may fail has a result type, `T
throws E` (`TypeData::Result { pass, fail }` in `types.rs`), as its
function type's result; `E` is `Bubble`, a declared failure type
("Declared failures", below), or a type parameter (a failure parameter,
below). Results
are internal (RFC 164's "results can't be held"):

- **Only a function's result:** a `TypeData::Fn`'s, a builtin type's
  method's (`CoreType::Throws`), or a builtin's (`Sig`'s `pass` and
  `fail`). No value has one, and Temper code can't name one: `T throws
  X` is `T` anywhere but a declared result (`eval_result`).
- **Readers unwrap it:** a call's type is the result's pass type
  (`Types::pass`, `Types::call_pass`), so every expression has one type.
- **Made by `Types::result`,** which drops a failure type that's a
  `Never`, so `T throws Never<Error>` will be `T`, and a function that
  can't fail has a plain result.
- **Generic machinery sees both parts:** substitution, `mentions`,
  `is_known`, and unification part by part, where a result never solves a
  type parameter but its pass type does. So a failure type parameter can
  be an ordinary type parameter, for callbacks, `Promise<T, E>`, and
  `Generator<Y, E>`.
- **One assignability rule:** what fits `T` fits `T throws E`; a result
  fits only a result whose parts fit, never `AnyValue`. Function types
  compare results so, and one that may fail isn't one that can't, even
  where its pass type isn't worked out.

It's its own variant rather than a generic type applied to a hidden base,
as generic types are values' types (`AnyValue`, supertypes, members,
joins) and results mustn't be. tmpl has the same type
([tmpl/types.md](tmpl/types.md#results)).

**Why not a hidden `Result<T, E>` class (tried 2026-11-23).** The
frontend was converted on a scratch basis: `Applied { base: RESULT, args:
[pass, fail] }` on a builtin type no source can name. It passed the
frontend tests and the Python and JS goldens, but it saved nothing:

- **Lines:** 50 added, 52 removed. Five structural arms went (`mentions`,
  `is_known`, `is_generic`, part of `substitute`, exports'
  `check_inferred`). Four special cases came in: a hidden name in `TYPES`
  and a filter for it in scope, a normalizing hook in `substitute`'s
  generic arm (`Result<T, Never>` is `T`), and an `is_result` check.
- **Generic machinery doesn't cover the rules.** The semantic rules stay
  as they are: what fits `T` fits `T throws E`, a result fits no value's
  type, unification through the pass type, and display as `T throws E`.
  Generic subtyping ignores variance here, so `@out` buys nothing, and
  the result-to-result rule had to stay too. `is_generic` started calling
  every failing signature generic.
- **Less robust.** The first test run leaked a result into `to_tmpl`'s
  generic-type arm ("a builtin generic type"), so the check has to come
  first. Each of the ~40 places outside `types.rs` that match `Applied`
  would need the same audit by hand. Today, exhaustive matching makes the
  compiler find every one.
- **tmpl would cost more.** A `Nominal` needs a declaration: a hidden
  class in core's fixture, which then shows up as an external in nearly
  every program's `uses` (tests fail with `Bubble`). Backends' imports,
  dependency walks (C++, Rust groups), and type naming would all see it
  as a class. The ~16 structural arms it would remove (types.rs, nevers,
  analysis, naming, backends' walks) are one line each. The seven
  backends' "a result type as a value's" arms would become decl checks
  that must come before their class handling.

So the own variant stays: its special cases are the semantic rules, which
either design needs.

**Generic failure parameters on both (spike 2026-11-23).** `List.map`
made generic on `E` worked the same on both, with the same code (8 settle
expectations and 3 interpreter runs, identical). Only one function's
arm differs (`fails_with`), and on the hidden class it must come before
the generic-type arm, by hand. Generic `E` didn't decide between them:
substitution, unification, and normalizing `Never` were already there
in both. Nor did inferring literals' results and failures (2026-11-24):
the same code passed the same tests on both, as it reads results only
through `Types::result`, `pass`, `fail`, and unification.

### Failure parameters

A type parameter used after `throws` is a failure parameter, `E` in
core's `Listed.map<O, E>(transform: fn (T): O throws E): List<O> throws
E` (RFC 164; `Types::fails_with`). It's solved per call as any type
parameter is, from what the arguments declare: a callback `fn (Int): Int
throws Bubble` makes it `Bubble`, so the call may fail; a function
literal without a declared result, from its body, so `ls.map { x => 10
/ x }` fails at the call ("Function literals' results from their
bodies", above). One that nothing solves is `Never<Error>`
(`Types::no_failure`), which `Types::result` drops, so the call can't
fail. Whether a call fails
comes from its solved signature (`bubbles.rs`). tmpl has no failure
parameters yet, so calls' type arguments leave them out, and backends
don't take a failing callback to `map` yet (chunk 15).

## Declared failures

In progress (RFC 164, chunks 4 to 12 of
notes/error-custom-kinds-plan.md, 2026-11-24): typed in the frontend,
run on the interpreter, and translated to tmpl
([tmpl/nodes.md](tmpl/nodes.md), "Declared failures") and to Python,
Java, Rust, C++, JS, and C#, while Lua says "not yet"; failure parameters aren't in tmpl yet, and `error case`
is still an error. The user-visible names, the root's (`FAILURE_ROOT` in
`builtins.rs`, and core's `interface Error`), the messages' wording,
and the default message and reports (`interp/failures.rs`), are kept in
one place each, as they may change.

- **Failure types** (`Types::is_failure`): a class, or sealed
  interface, that extends core's root `Error` (`types::ERROR`), directly or
  through another failure type. Each is checked
  (`check_failure_class`): no type parameters ("Failure type Box can't
  have type parameters"), an interface is sealed ("Interface Loose
  extends Error, so it must be sealed"), and at most one failure type
  among what it extends, beside the root ("Both extends two failure
  types, A1 and A2; a failure type extends one"). The root is an
  `AnyValue`, and what extends it is an `Error`; joins of two failure
  types with no closer common type are `Error`.
- **`throws E`** (`eval_result`, `check_throws`): `E` is a failure type,
  `Bubble`, a type parameter, or a `Never`. Anything else is "String
  isn't a failure type; …", and the root, "Error is the root of failure
  types; …"; both stand for what they did before, `Bubble` and the root.
- **`throw x`** is a `Fail` node with `x` (`bubble()`'s has none), of
  type `Never`; `x` must be an `Error` ("Expected subtype of Error, but
  got Int32").
- **`try x`** is `x` for now (RFC 164's option B): failures propagate
  with or without it. Requiring it is a later chunk.
- **`x catch { b }`** is `Orelse(x, b)`, and **`x catch (e) { b }`**
  binds the failure first, `Orelse(x, { let e = caught(); b })`, where
  the hidden builtin `caught()` has the type of what `x` may fail with
  (`caught_type`), the join of its failures (`join_failures`), so a
  `when (e)` over a sealed type needs no `else`. Where `x` may bubble,
  that's "A Bubble has no value to bind; catch it without (e), or with
  orelse". `catch (e) { … }` with nothing before it is malformed.
- **What a function may fail with** (`allowed_failure`) is its declared
  result's failure type, or a function literal's, as before. Each
  failing place in it ("What may bubble", below) has a failure type:
  `Bubble` for `bubble()`, `await`, `as`, `/`, and `%`; the value's
  type for `throw`; and a call's callee's result's, solved
  (`call_failure`). A place passes if its type is the function's or a
  subtype (`check_failures`). Otherwise, where the function can't fail,
  "Cannot bubble …" as before, or "Cannot throw NotFound from a
  function without NotFound in its return type"; and where it may, but
  not with that: "Cannot throw NotFound from a function that throws
  Other; catch it, and throw an Other there if need be", and for
  `Bubble`, never mixed either way, "Cannot throw NotFound from a
  function that throws Bubble; …" and "Cannot bubble from a function
  that throws NotFound; …". Conversion is only explicit, by `throw` in
  a handler.
- **Exports** fail only with exported types (Tom, 2026-11-24): an
  exported function's or public method's failure type is part of its
  result type expression, so the check against exported signatures
  naming unexported types (`typing/exports.rs`) covers it, with the same
  "Export depends publicly on non-exported symbol Hidden". Private
  functions may fail with unexported types, and an exported sealed
  failure type may have unexported classes under it.
- **Calls Kotlin might fold** count only for `Bubble`: a call that may
  fail with a declared type is always a failing place, as Kotlin has
  none.
- **Messages and causes:** core's `Error` has two getters, which a
  failure type's values have unless it declares its own
  (`core_member_found` looks there for a failure class's): `message:
  String`, by default its type's name in lowercase words, then its
  public fields, `not found (key: y)`; and `cause: Error?`, the failure
  being handled where it was first thrown, by a `throw` in a `catch`
  block or `orelse` fallback (or a `for … of` body in one, but not
  another function), or null: none, or a bubble, which reports still
  say. A failure thrown again keeps its first cause and place, so causes
  never loop. A report of one nothing handles says its type, message,
  and place, then each cause's: `Failed with LookupFailed: lookup failed
  (name: port), at a.temper:5:24`, `caused by NotFound: …`. A panic of
  `orelse panic()` names it as its cause the same way. Panics pass every
  handler, as before.
- **Not yet:** `error case`, promises and generators with `E`, a
  failure parameter's implied `extends Error` bound, and checks that a
  failure type's own `message` or `cause` fits the root's.

## What may bubble

A function whose declared return type doesn't `throw Bubble` mustn't
bubble (`typing/bubbles.rs`, Kotlin's `BubblesAreNotFree`); one that
throws a declared failure type mustn't either ("Declared failures",
above). In its body and parameters' defaults, each of these is "Cannot
bubble from a function without Bubble in its return type", at the
call:

- `bubble()`.
- `await p`, as a broken promise bubbles, at the `await`; it fails
  there when run, on every target, even for a promise that's kept.
- A call of a function value whose type `throws Bubble`: a parameter,
  local, function, or method, including a builtin type's (`s.toInt32()`).
  A call Kotlin might fold, as `g(1)` or `String.fromCodePoint(65)`, isn't
  reported, but Kotlin never folds a method of a value (`"1".toInt32()`
  is reported), nor a call with an argument that doesn't fit its
  parameter. A call with the wrong number of arguments isn't reported,
  as Kotlin can't pick its callee.
- `x as T` that checks something and is allowed (below, "Run-time
  type checks"): a downcast, as `i as D` for `D extends I` or `x as C`
  for `x: AnyValue`, or a nullable `x` (`x as Int` for `x: Int?`). Not
  one where `x` is a `T` already, nor one that's an error, nor a nullable
  `x` for a nullable target (`x as Int?`, which null passes). What `x`
  holds doesn't matter, so `n as Int` for `let n: Int? = 1` may bubble.
- `a / b` and `a % b`, unless Kotlin might fold the call, or the divisor
  is an integer that's a constant (maybe negated) other than zero, or for
  `%`, positive, or the divisor is anything Kotlin might fold (it may
  fold to such a constant). Dividing `Float64`s always may bubble.

Not looked into: the arguments of an operator that don't fit the
signature it picks, as `h(y)` in `1.0 + h(y)` or `mk(n)` in `h == mk(n)`
(the operator itself, as `x / y` with `x: Int?`, still is), unless the
operator has several variants and its first argument fits none, as in
`"a" - h(y)` or `k == mk(n)` for a class without `@operator("==")`,
where all are; an `orelse`'s body (its fallback is), functions
inside (each is checked on its own), and the branches of an `if` whose condition Kotlin might fold, except
the one a literal `true` or `false` leaves. A `for … of` body is part of
the function around it. Functions checked: those that declare a return
type, generators (as `async` blocks) too, unless it's `GeneratorResult<Y>
throws Bubble`; constructors and setters, which return
`Void`; and function literals without a return type whose place wants a
function type that doesn't `throw Bubble` ("Function literals and target
typing", above). Module-level code and `test` bodies may bubble.

## Classes' types

What tempest does today (`typing/members.rs`), as Kotlin Temper does;
status in [milestone-4.md](history/milestone-4.md).

**Values.** A class or interface without type parameters is a type:
`new C(…)` is a `C`, and so is `this` in `C`'s members. A generic
one's are generic types, as `Box<Int32>` ("Generic types", above). A class or interface named as a
builtin type (`class Int32` in Kotlin's `core.temper`) is that type.

**Subtyping** is nominal: a class or interface is a subtype of what it
extends, theirs and so on, and nothing else. An interface's values may
be of a class that also extends something else, but as in Kotlin, using
one as that needs an `is` check to narrow it first (`if (i is D) {
takeD(i) }`), so `let d: D = i` and `let j: J = i` are errors. Neither is a final builtin type's value an instance
of a class, nor null a non-nullable one's. A function is a `@fun`
interface's value where its type fits, and no other class's or
interface's ("Fun interfaces", above).

**Members.** `c.x` reads a field or getter of `c`'s class, own or
inherited, and `c.m(…)` calls a method (failing that, a field's
function, as `obj.f(x)`); `C.m(…)` calls a static method and `C.x`
reads a static field. A bare member name in its class means the same,
and a type parameter's value has its bound's members.
A field's type is declared or inferred from its initializer, a
getter's is its return type, and a
method's is its signature, with a parameter that has a default typed
as nullable, as Kotlin does. Abstract and `@connected` methods without
a body, and a `@fun` interface's `apply`, are typed the same way, from
their signatures. More than one method of a name is unknown; methods
marked `@overload("name")` answer to that name too ("Overloads",
below).

**Errors**, in Kotlin's words and at its positions, with classes named as
written rather than Kotlin's `C__0`:

- What a value can't be, as for any type: "Cannot assign to I from C"
  where stored, "Expected subtype of I, but got C" as an argument.
- A method's or constructor's arguments, as a function's; arity errors
  don't count `this` ("Expected 1" for one parameter, where Kotlin
  says "Expected 2").
- Assigning a member, `c.x = v`, `this.x = v` or a bare `x = v`, and a
  field's initializer: "Expected subtype of Int32, but got String" at
  the value, as for a setter's argument.
- Calling a method that bubbles, in a function that can't (a bare call
  reads `this`, so is never folded).
- **Ties:** a method a class inherits from more than one supertype, as
  `class T extends A & B` with both defining `a`, is "No callee matches
  inputs [T, Int32] among [(A, Int32) -> Int32, (B, Int32) -> Int32]",
  spanning the call, as Kotlin's `FilterOutMaskedMembers.kt` and solver
  find it: not if the class defines its own, nor for one another
  supertype overrides, nor for one further from the class than another
  with the same signature (then the nearer wins), nor if the arguments
  fit only one. Supertypes are listed depth-first, in `extends` order.
  A tie's type is unknown. Only reported where every signature and
  argument type is known.
- **Overrides:** each of a class's or interface's own methods, getters,
  setters and fields (as getters) that isn't static must have the same
  signature as each one of its name and kind in its supertypes, private
  ones aside: "Type B has method f with signature f(x: Int32): Void, but
  it should be f(x: String): Void to correctly override from I" (`get p`
  or `set p` for an accessor, written `p(): Int32`). Signatures are
  equal when their parameters' types, with a default making one
  nullable, their results, and whether they bubble (written `Int32
  throws Bubble`) are; parameters' names don't matter, and
  nothing is covariant, so `f(): C` doesn't override `f(): I`. It
  spans the member. As Kotlin reports one error per position, a member
  is reported once, for the first supertype it differs from, depth-first
  in `extends` order, and not at all if it narrows the visibility of
  what it overrides, reported as that. A supertype's member is seen as
  the class sees it, as Kotlin's `sigInContext` does: with the type
  arguments the class gives that supertype, through what it extends, put
  in for its type parameters, so in `class L<U> extends J<List<U>>`, `J<T>`'s
  `g(x: T): T` is `g(x: List<U>): List<U>`, which `L`'s `g` must be.
  Only compared where both signatures are fully known. Kotlin checks
  this only in a module with exports; tempest checks every module
  ([kotlin-differences.md](kotlin-differences.md)).
- **Generic methods:** an interface's own method that isn't static can't
  have type parameters, as it may be overridden: "Illegal type parameter
  T. Overridable methods don't allow generics", at an empty position at
  the start of each, as Kotlin reports while expanding the declaration
  (core is exempt in Kotlin; tempest reads core only for signatures). A
  generic method still overrides one with as many type parameters if
  their signatures match with the type parameters paired by position, so
  `h<U>(x: U): U` overrides `h<T>(x: T): T`; their bounds aren't
  compared. Kotlin compares type parameters by identity, so it also
  reports every such override as differing
  ([kotlin-differences.md](kotlin-differences.md#changes-we-recommend-to-temper)).
  One with a different number of type parameters differs.

## Overloads

Decided 2026-11-06, as Kotlin Temper does (`Typer.kt`,
`TypeContext2.overloadSpecificity`, `SimplifyDotHelper.kt`), in
`typing/overloads.rs`:

- **Marking:** `@overload("append")` on a method, as Wherewith's
  `appendInt` and `appendString`, makes it answer to `c.append(x)` as
  well as its own name. Only when `c`'s class has no member `append`
  itself, as Kotlin looks for overloads only when no member has the
  name; a static method answers to `C.append(x)` the same way.
- **Choosing:** of the marked methods, own or inherited, those whose
  parameters take the arguments (count, and each argument's type a
  subtype of its parameter's, or unknown), then the one at least as
  specific as every other, each parameter a subtype of the other's: a
  `Rect` goes to `appendRect(x: Rect)` before `appendShape(x: Shape)`,
  and `null` to `appendString(x: String?)`. The expected type plays
  no part. Where none fits or none is most specific, the call stays
  unresolved, as before; Kotlin reports "No callee matches".
- **Generic arguments** (2026-11-06), as Kotlin's `@out` on `List`
  decides them, though tempest's subtyping leaves them unknown
  ("Generic types"): a `List<String?>` doesn't fit a `List<String>`
  parameter, and of two that fit, `List<String>` is more specific than
  `List<String?>`. A class whose supertypes are all known isn't a
  builtin generic type it doesn't extend, so a `Name` doesn't fit a
  `List<Boolean>` parameter either (`types.rs`, `generic_subtype`).
- **Rewriting:** the call's key then names the method, `c.appendInt(x)`,
  as Kotlin's `SimplifyDotHelper` rewrites it, so the interpreter, tmpl,
  and every backend call it by its own name, matching Kotlin's
  generated APIs (no overloads in any backend's output). Tagged
  templates' holes, `t.append(x)`, resolve the same way.
- **Not yet:** named arguments to an overloaded name, a bare `append(x)`
  in the class, and `@overload` on top-level functions.

## Fields first

Decided by the Temper team, 2026-11-02, as Swift's two-phase
initialization: a constructor doesn't use `this` until every field is
set. Kotlin Temper doesn't check it yet ([kotlin-differences.md](kotlin-differences.md)),
and its Rust backend builds the instance at the constructor's first
use of `this`, so a field set after that doesn't compile; other
backends read the unset field. Tempest checks it after typing
(`typing/fields_first.rs`).

- **Fields** are a class's non-static backed properties: `x: T`,
  `x: T = v`, `var x: T`, and header parameters. Header fields and
  fields with initializers are set before the constructor's body, in
  declaration order, as the interpreter and Kotlin set them, so an
  initializer may read only fields declared before it. Interfaces have
  no backed properties, so an inherited property is a getter or setter.
- **Before every field is definitely set,** the constructor (with its
  parameters' defaults) and the initializers may set fields, read
  fields already set, and use anything not involving `this`. Anything
  else is an error at the use: calling a method of `this`, a getter or
  setter, using `this` itself (passing, returning, or storing it), and
  a function that captures `this`, reported at its first use of it. A
  bare member name counts as `this.name`. The temporary that `this.x +=
  1` makes for `this` isn't a use.
- **Control flow:** one pass in evaluation order carries the set of
  fields set, as flow facts do. Branches merge by intersection, and a
  constant condition (`while (true)`) has only one branch; a loop
  starts each pass with what held on entry, since nothing unsets a
  field, and is followed by what its `break`s carry; an `orelse`'s
  fallback starts with what held before its body; `bubble()`,
  `panic()`, `continue`, and `return` end a path. A local function
  captures what held where it's declared, or on entry to its block if
  something before uses it.
- **At the end,** each path that returns normally (the body's end, and
  each `return`) sets every field, since the new instance escapes
  there; otherwise "Property b not initialized in constructor", at the
  constructor. A class without a constructor reports each field with
  neither an initializer nor a header place, at the field, which is
  Kotlin's one check of this, with its message. As in Kotlin, core's
  `content: builtins.T` fields are exempt.
- **Messages** say what was done and which fields aren't set yet:
  "Calling this.sum() before b is set", "Reading this.b before b is
  set", "Calling getter this.twice before b is set", "Using this before
  a and b are set", "Capturing this in a function before a and f are
  set" (`tests/settle/fields-first.cases`).

Scanned over the functional tests, std, Chromarite, Ordable, Hrefine,
Kelpto, and every Temper file in Kotlin Temper's repo, it finds nothing
new in any library or functional test; only four fragments in Kotlin's
stage tests, which stop before typing (three fields without
initializers in classes without constructors, which Kotlin would report
too, and `class C { private me = this }`).

The Rust backend still wants each field set first by top-level
statements; fields set in branches or loops, with this rule's
guarantee, are a follow-up there.

## Definite assignment

As Kotlin Temper's `UseBeforeInit`: a local or top-level `let` or `var`
read where some path to it doesn't assign it is "x is not initialized
along branches" (`typing/definite.rs`). One pass in evaluation order,
as for fields first, carries the declarations that may be unassigned,
merging by union; a module's top levels are walked in the order they
run. An `orelse`'s fallback starts with what held where its body may
fail, so `(x = i) orelse …` assigns `x`. A function's own locals are
checked, its enclosing function's aren't (as in Kotlin), but a `for …
of` body's are, as tmpl loops over it
([kotlin-differences.md](kotlin-differences.md)). Each read reported
counts as assigning, so a path reports a local once.

## Run-time type checks

What tempest reports for `x is T` and `x as T` (`typing/rtti.rs`), as
Kotlin Temper's `TypeFuns.kt` (`problems`) does, in its words and at its
positions: the check's span, or in `when (x) { is T -> … }`, `T`'s.
Status in [milestone-4.md](history/milestone-4.md). Let `S` be `x`'s type, from
flow facts where they narrow it, never from its value ("Nullable until
narrowed", above): `s is Int` for `let s: AnyValue = "a"` judges an
`AnyValue`, where Kotlin judges the `String` it folds `s` to
([kotlin-differences.md](kotlin-differences.md)). Let `T` be the
target with any `?` dropped. Rules 1 to 5 judge the check's non-null
part, so they're as Kotlin's; rule 6 counts null, which a nullable
target passes (below, "Nullable targets"). Both must be fully known: a
builtin type, a class or interface, a generic type given arguments, or
a function type; otherwise nothing is reported. Where `S` is a type
parameter's, as `x: T`, the rules judge `x` by `T`'s bound, all that's
known of its value at run time, though messages name `S` ("from T"):
so `x is Dog` for `T extends Animal` is a downcast, allowed, where
Kotlin says the types are unrelated ([kotlin-differences.md](kotlin-differences.md#bugs-in-kotlin-temper)).
In order, the first that applies:

1. **Can never succeed:** `S`, made non-null, and `T` are both final
   and differ in their classes: a builtin type other than `AnyValue`, a
   class, which no class extends, or a generic type of one, as
   `List<Int>` or `Box<String>` for a user's `class Box<T>` (core's
   generic classes are `List`, `ListBuilder`, `Map`, `MapBuilder`,
   `Deque`, `Pair`, `Promise`, `PromiseBuilder`, `ValueResult` and
   `DoneResult`). "Runtime type check from Int32 to String can never
   succeed", as `x is String` for `x: Int?`; `Box<String>` from
   `Box<Int>` is unrelated instead (rule 4).
2. **A type parameter** `T`, other than `S` itself: from a supertype of
   its bound (`AnyValue` if none), "Type parameters cannot be targeted
   with is or as runtime type checks, because a type argument's
   run-time type can't be checked: <[T]> from AnyValue"; otherwise, as
   `i is T` for `i: I` and `T` unbounded, the unrelated error below,
   naming `T`.
3. **`x is AnyValue`** for an `S` other than `AnyValue`: nothing, as in
   Kotlin.
4. **Unrelated:** neither `S` (non-null) nor `T` is a subtype of the
   other, counting `AnyValue` as no other type's subtype and `Void` as
   not an `AnyValue`: "Unrelated types cannot be targeted with is or as
   runtime type checks: <[J]> from I?", with `S` as it is, nullable or
   not, judged through users' generic supertypes ("Generic types",
   above): `Sub4` from `Sup<T>` is unrelated, as a `Sub4` is a
   `Sup<String>`. Not where either way is unknown.
5. **A downcast** (`T` is a subtype of `S`) to a type core marks
   `@mayDowncastTo(false)`, the final builtin types and `List`,
   `ListBuilder`, `Map` and `MapBuilder` given any arguments: "Types
   marked @mayDowncastTo(false) cannot be targeted with is or as runtime
   type checks because they may not be distinct on all backends:
   <[Int32]> from AnyValue". Otherwise, a downcast to a generic type
   with an argument that isn't one of `S`'s, which erasure leaves no
   way to check, as `x is Box<Int>` for `x: AnyValue`, but not
   `Box<T>` from `Sup<T>`: "Type arguments cannot be introduced with is
   or as runtime type checks, because a type argument's run-time type
   can't be checked: <[Box<Int32>]> from AnyValue". As in Kotlin, an
   argument of a parameter marked `@out` or `@in` (a user's, kept as
   `TYPE_PARAM_VARIANT`, or core's, as `Generator<@out YIELD>`) isn't
   counted, though tempest doesn't honor variance otherwise ("Type
   parameters are invariant", above), until it's known what relies on
   it. Other downcasts are fine. Kotlin's wording ends at "checks" for
   both erasure errors; tempest says why
   ([kotlin-differences.md](kotlin-differences.md#changes-we-recommend-to-temper)).
6. **Unnecessary,** as information: every `S` passes the target as
   written, `T` or `T?`, that is `S` (non-null) is a subtype of `T`, and
   `S` isn't nullable or the target is; for `is`, or for `as` only where
   `S` is the target as written: "Unnecessary type check to I from
   expression with type D which is a subtype", naming the target as
   written (`String?` for `s is String?`). Not said where flow facts
   narrowed `x`, since they may have where Kotlin doesn't.

**Nullable targets.** `x is T?` is true for null and for a `T`, and `x
as T?` lets null through, as it reads, in the interpreter and in typing:
`s is String?` for `s: String?` is unnecessary, `x as D?` for `x: I?`
may bubble (a `D` check remains) but for `x: D?` is unnecessary, and
where `a is T?` is false, `a` isn't null. This is what Kotlin Temper's
interpreter and generated code do (its `SeparateNullBranches` splits
`x is T?` into a null check and a `T` check), but its type checker drops
the `?`, so it calls `s is String?` for `s: String` "Unnecessary type
check to String", and not `s is String?` for `s: String?`. Errors for
a check whose non-null part can never succeed (`c is D?` for `c: C?`)
are still reported, though null passes it: it's just a null check,
better written `c == null`. Backends, once tempest has them, translate
`x is T?` as `x == null || x is T` and `x as T?` as `x == null ? null
: x as T` where the target language's own check would reject null, as
Kotlin Temper's backends do.

And `x` of type `Void` is also "Expected subtype of AnyValue?, but got
Void", at `x`. Not yet: `@connected` types (core's, which tempest
doesn't type yet; Kotlin fails on user code's).

## `Void` as a value

What tempest reports, as Kotlin Temper's `TypeChecker` does: a `Void`
value, as `b()` for `b(): Void`, `void`, a `Void` parameter, a loop
(`do … while`), or an `if`, `when` or `orelse` whose branches are all
`Void`, stored or passed is "Void expressions cannot be used as values":

- a declaration's initializer, local or top-level, typed or not (`let x
  = b()`, `let q: Void = b()`), at the initializer;
- an assignment to a local (`y = b()`), at the assignment;
- an argument of any call, builtin operators', `console.log`'s and
  setters' included (`c(void)`, `b() == b()`, `"${b()}"`), at the
  argument.

Not where an error is reported at the same span already, as "Expected
subtype of Int32, but got Void" for `g(b())`, since Kotlin reports one
error per position. `return b()` and a function's result aren't values
here (a non-`Void` function's are checked as results, above), nor is a
statement. Where Kotlin's position is empty, past a temporary it
introduced (`[b()]`, `b() == b()`), tempest's is the expression's.
`b().toString()` is reported at `b()` with this message, where Kotlin
says "No member toString in Void". Not yet: values whose `Void` type
comes through generics, as `a[0]` for `a: List<Void>` (slice 5).

## Every expression typed

Slice 7's goal ([milestone-4.md](history/milestone-4.md)), since tmpl needs
each expression's type (`typing/exprs.rs`). Once checking is done,
typing works out every expression's type, so `Typing::expr` gives it,
and resolves every call ("Resolutions", below).

- **What's an expression:** a value other than a symbol, a name read as
  a value, a call, a function, and a block, `if`, or `orelse` whose
  value is used (an initializer, a value assigned or returned, an
  argument, or a condition). Not a type, a declared name, a named
  argument's symbol, a callee that names what's called (a builtin, a
  function, or `o.m`, though `o` is an expression unless it's
  `console`), a name assigned, a tag's lists of parts, nor a statement.
- **Typed for this:** a block that jumps away before its end, as `{
  return -1; }`, never completes, so `if (c) { 1 } else { return -1; }`
  is an `Int32`; a generic function's own type is its signature with its
  type parameters as written, though a reference to it sees them as
  unknown; and the builtins and inference above.
- **Measured** by `run-temper --untyped`, per file, and summed by
  `compat/count_untyped.py` over the functional tests, std, and the
  libraries given (testing-and-milestones.md).
- **Left:** generators and `async` blocks (`await`, `async { … }`), a
  list literal whose elements have no common type tempest knows (`[[],
  ["a"]]`) or with no type wanted (`[]`), a static generic method with
  type arguments (`List.of<AnyValue>(…)`), and code with errors.

## Resolutions

What each call, operator, and member read resolves to, recorded on the
node once checking is done (`Typing::resolved`, `typing/resolve.rs`),
so tmpl's translation doesn't resolve again (notes/tmpl/nodes.md,
"Equality"):

- **A declaration,** by its `Let`: a function called by name, through
  an import too, a method
  (own, inherited, or a type parameter's bound's), a field or getter
  read, a constructor, or for `new C(…)` of a class without one of its
  own, the class; and a class's or interface's `@operator` method for
  `==`, `!=`, `<=>`, and the comparisons, its own or the nearest
  inherited one.
- **The `eq` that `@auto("==")` makes** is a declaration like any
  other (2026-10-30): the frontend generates it as a real member, as
  Kotlin's `AddAutoMembers` does, so `to_tmpl` never synthesizes
  members.
- **A builtin, by its key,** named as temper-tmpl's operator table (`ops.rs`) names them
  where they overlap: `Int32.add`, `Int32.neg`, `String.eq`,
  `Float64.lt`, `Boolean.not`, `String.cat` for a template, the members
  as core names them with the type declaring them (`String.toInt32`,
  `List.get` for `ls[i]`, `Listed.isEmpty`, `String.begin`), a builtin
  type's `@operator` method (`StringIndexOption.compareTo` for `i <
  j`), constructors (`ListBuilder.constructor`), `console.log`, and
  other builtins by name (`panic`, `list`, `doPure`). `!=` records the
  equality it negates, and `<` and the like the `<=>` method they
  compare with zero.
- **A null check:** `a == null` or `a != null`, either way round.
- **A call of a function value,** as `f(1)` for a parameter `f`, or
  for a local `let f = field` that holds one: another name resolves to
  what it names only for a function (an import's), so a local holding a
  value is called as the value it holds, narrowed by a null check if one
  narrows it, not read again from where it came from.

`==` with nothing to call but identity, as for a type parameter's value,
`AnyValue`, a function, or a class or interface with no `==` typing can
find, is noted (`Typing::identity_eq`) and left unresolved: these are
the uses that would become errors (Decided, notes/tmpl/open-questions.md,
question 13). `is` and `as` aren't calls to resolve.

## Checking semantics and messages

**Types in messages** are written as Kotlin Temper writes them
(`Int32`, `String?`, `fn (Int32): Boolean`, `fn: Int32` with no
parameters, `fn (Int32, _?: Int32): Int32` with an optional one, classes
as named), except that one that may bubble is
written as Temper's syntax does, `Int32 throws Bubble`, in function
types (`fn (Int32): Int32 throws Bubble`), operators' signatures
(`(Int32, Int32) -> Int32 throws Bubble`) and overrides' signatures
(`f(): Int32 throws Bubble`) alike, where Kotlin writes its internal
`(Int32 | Bubble)` or `Result<Int32, Bubble>` (`show_result` in
`types.rs`).

The type checker's behavior and its error messages should match Kotlin
Temper's wherever practical. Sources to compare against: the frontend stage
tests, the backend tests, the REPL tests, and running Kotlin Temper itself,
including its REPL, on the same inputs. See
[testing-and-milestones.md](testing-and-milestones.md).

**Positions and wording are worth improving.** Kotlin has been more
careful with error positions in some places than others. Match its
messages and positions by default, but where one is clearly bad (an
empty position, a span past the function's end, a message that counts a
hidden argument), point at the right place and say the right thing,
and list the change in [kotlin-differences.md](kotlin-differences.md).
Where it's only arguable, keep Kotlin's and note it there for review.
