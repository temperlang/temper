# tmpl: declarations, names, and linking

Part of the [tmpl design](README.md).

## Level 2: Declarations, names, and linking

The `DeclTable` holds every declared thing, `DeclId` dense from 0. Each
entry:

- **Kind:** module, class, interface, function, method, constructor,
  getter, setter, field, module `let`, local, parameter, type parameter,
  label, or test.
- **Name:** the source name, or for compiler-made declarations a role
  hint (`result`, `index`, `done`, or "copy of" another declaration).
  With kind and visibility, this is the `NameRequest` (design.md section
  8).
- **Owner:** the enclosing declaration; owners form a tree rooted at
  modules.
- **Facts read once** (design.md, "Declaration facts in one place"):
  visibility, exported, static, abstract, `var`, connected (and how
  it resolves, [below](#level-3-connected-declarations)), `@keep`,
  `@fun`, `@operator` marks, `@auto("==")`, optional (for
  parameters with defaults), generator, `async`, may bubble, `@imu`
  and `@partialImu` (on types and type parameters), and compiler-made.
- **Type,** span, and docs.

**External declarations** stand for what this library uses from
another, core included: same entry, no body, linked by its qname
([below](#level-3-qnames); Proposed 2026-10-29, replacing a separate
link key). Temper has no overloading, so a qname is unique; the
declaration's kind and type are kept too, so a reader checks them
against the dependency's exports and reports a mismatch rather than
mis-linking. Within a library, modules share one
table and refer to each other's declarations by `DeclId`; the import
list says which ([modules.md](modules.md)).

**Pretty names everywhere, inside functions too** (Decided 2026-10-29;
design.md section 8: no numbers except to resolve a real conflict).
Users have complained even about suffixed locals, so tmpl makes this
achievable:

- **Every local, parameter, label, and temporary is a declaration with
  a name:** its source name, or for one a pass introduces, a hint
  saying what it holds. The validator rejects a compiler-made
  declaration without a hint, so no pass can fall back to `tmp`.
- **Hints come with candidates,** in preference order, from shared
  helpers in `naming::hints`, so passes name what they add the same
  way: the parameter a value feeds (`x` for `new Point(x = …)`), the
  property or local it copies (`items`), the call it holds without
  `get` or `to` (`parsed`, `int32`), its type (`point`), or a role
  (`result`, `index`, `done`, `state`).
- **Scopes and shadowing are explicit:** every declaration's owner is
  its enclosing block, function, class, or module, and every reference
  is a `DeclId`, so two source locals named `x` are told apart without
  renaming them in tmpl.
- **The naming pass renames only on a real conflict,** as the target
  sees it: `NamingRules` describe its scoping as data (Python's and
  JS `var`'s function scope, Python's class bodies not enclosing
  methods, C#'s ban on shadowing an outer local, Rust's free
  shadowing). Order: exported names first, never suffixed (a clash
  between exports is an error reported once, not renamed), then source
  names in source order, then compiler-made names. On a conflict it
  tries the hint's next candidate, then, in function-scoped targets,
  sharing the name with a local whose lifetime doesn't overlap and
  isn't captured (liveness, [analyses.md](analyses.md)), and only then the shortest suffix
  ([question 23](open-questions.md)).

The naming pass returns a `NameMap` from `DeclId` to string; tmpl
itself stores no output names.

**Public names before private ones** (the user's rule, 2026-11-03):
public names matter more than private ones. Where a clash must be
resolved, a private or protected member, or a local, yields, never
public API; and where the target allows two things one name, nothing is
renamed. As built (`naming::name_members`):

- A class's public members are named first, then its private and
  protected ones, so a suffix lands on the private side whatever the
  source order.
- `NamingRules` says where the target keeps names apart rather than
  listing cases: `members_prefixed` (Python, whose private members and
  fields' storage take a `_`) and `fields_apart` (Rust, whose struct
  fields and methods don't clash), so std's private `_messages` is
  `messages` beside the method `messages()`, as Kotlin's.
- Names a backend makes beyond declarations (Rust's accessor functions
  for private `var` fields) are the backend's to choose, avoiding the
  class's names (`Translator::accessor`: `passing2()` beside the getter
  `passing()`), so they never push a declaration's name aside.

At a module's top level, exported names already come first (the order
above).

## Level 3: Declarations

Proposed. The `DeclTable` holds one record per `DeclId`, in columns
like the nodes:

```rust
pub struct Decl {
    pub kind: DeclKind,          // u8; `Removed` for a pass's tombstone
    pub name: StrId,             // the source name, or the first hint candidate
    pub alts: StrListId,         // further candidates; compiler-made only
    pub owner: DeclId,           // its function, class, or module; NONE for a module
    pub ty: TypeId,              // a function's is its signature
    pub facts: Facts,            // bit set: Level 2's facts read once
    pub visibility: Visibility,
    pub qname: StrId,            // an override of the derived qname, or NONE
    pub connected: bool,         // marked @connected (or core's equivalent)
    pub span: Span,
    pub name_span: Span,
}
```

A local's owner is its function, not its block: the block scope comes
from the tree (the `Let`'s enclosing `Block`), so it isn't stored
twice. A loop's label is a declaration of kind label, owned by its
function.

## Level 3: Qnames

**Decided (2026-10-29):** tmpl has a place for qualified names
(qnames). Kotlin Temper derives one for each declaration, and its
backends map core's and std's qnames to custom translation. tmpl's may
follow Kotlin's format, so backend tables and compatibility carry
over.

Proposed:

- **Kotlin's format, exactly** (its `QName.kt`): the library's name,
  the module's directory relative to the library root, then one part
  per enclosing declaration, each kinded by punctuation: `x` (a top
  level or property), `x=` (a local), `f()` (a function or method),
  `(x)` (a parameter), `<T>` (a type parameter), `type C`,
  `constructor()`, `get x()`, and `set x()`. So
  `my-lib/foo/bar.type C.f().(x)`. `#n` follows a qname that would
  otherwise be ambiguous in its module (two locals `x` in one
  function), in declaration order; `\` escapes meta-characters. Core's
  library is written `core` and std's `std`, as in Kotlin:
  `core.type Int32.toString()`, `std/temporal.type Date`.
- **Why exactly Kotlin's:** backend tables port verbatim (Kotlin's
  Python table has about 160 entries keyed by qname); a library's
  qnames identify its declarations across versions, for api-compat and
  `temper.keep`; and Kotlin's per-library qname metadata stays
  comparable, for a bridge.
- **Qnames key `temper.keep`** (the user's direction, 2026-10-29;
  little built yet in either compiler): records of decisions per
  backend variant, such as each declaration's chosen output name (as
  Kotlin's `name-selection.json` starts to), are keyed by qname, so a
  later build reuses earlier choices and backward-incompatible changes
  (a renamed or removed export, a changed signature or name choice) are
  found automatically. So the naming pass reads earlier choices from
  `temper.keep` before choosing, and writes its choices back; and
  api-compat's diffs match declarations by qname.
- **Every declaration has one, except compiler-made ones**
  (temporaries and compiler-made labels), as in Kotlin. A declaration
  the compiler makes for the surface, such as an `@auto("==")` `eq` or
  a default constructor, gets the qname Kotlin gives it.
- **Derived, with overrides.** `qname(&Program, DeclId)` builds it from
  the library's name, the module's path, the owner chain, the source
  names, and the declarations' kinds. A local's owner is its function,
  so a local in a nested block is `f().y=`, as in Kotlin. Stored is
  only an override, `NONE` almost always: the part after the module's
  path, where derivation would differ (a disambiguation index, or
  Kotlin's kind differing from tmpl's, as `f()` for a module `let`
  holding a function literal). The frontend computes qnames by Kotlin's
  rules (`AttachQNameMetadata.kt`) and stores those that differ.
- **Qnames are link keys:** an external declaration links to its
  dependency's export by qname.
- **Validator:** qnames are unique within a library, and every
  declaration but compiler-made ones has one (rules Q1 and Q2,
  [nodes.md](nodes.md#level-3-the-validators-rules)).

### As built (2026-10-29)

Proposed, settled while building `crates/temper-tmpl/src/qname.rs`:

- **The record:** `Decl` has `qname` (the override, `StrId::NONE`
  almost always); `connected`, `optional`, and `@keep` are facts
  (`CONNECTED`, `OPTIONAL`, `KEEP`), not fields; a connected
  declaration's backend list and an external function's parameter
  names are string lists beside the record, as hint candidates are.
  `visibility` waits for subset B.
- **Derivation, by kind:** a class or interface is `type C`; a function
  (top-level, static, or local), a method, or a bodiless function is
  `f()`; a getter `get x()`; a parameter `(x)`; a local `x=`, in a
  function or an init block; a module `let` `x`. A module's path is
  `Module::path`; a used library's qnames start with its name,
  `temper-core` written `core` and `temper-std` `std`
  (`qname_library`).
- **No qname** for compiler-made declarations and anything they own,
  labels (Kotlin's labels aren't declarations), modules, and used
  libraries (only roots of others' qnames). Q2 exempts exactly these.
- **`#n`:** derived qnames that coincide in a module all get an index,
  in `DeclId` order. An override is used as written, index and all,
  and isn't counted with derived ones; its declaration's children
  derive from the override's parts, without its index.
- **Overrides** are written as the part after the module's path, and
  only on the program's own declarations (items, parameters, and
  `let`s); externals' follow from the `uses` section's nesting.

**As built with `to_tmpl` (2026-10-30):** the frontend allocates
`DeclId`s in source order (module items, then each function's
parameters and locals as its body is translated), so derived `#n`
indexes follow it, and it stores no qname overrides yet; nothing checks
its qnames against Kotlin's for the same tests yet. Compiler-made
declarations so far are `~console` (hint `console`) and each loop's
`~loop` label. A library built by `run-temper` is named after its first
file (`fibonacci` for `fibonacci.temper.md`), version `0.0.0`, its one
module named as the library, at its root.

**Known gaps,** for "somewhat right" first (the user's direction):

- **Order of `#n`:** Kotlin numbers in its tree walk's order; tmpl in
  `DeclId` order. They agree when the frontend allocates ids in source
  order; where not, the frontend stores an override.
- **Kotlin's kinds that tmpl's don't give:** a module `let` holding a
  function literal (`f()`), and later anything Kotlin kinds by
  metadata; the frontend stores overrides for them.
- **Std's module paths:** a `uses` section has no modules yet, so std's
  externals derive `std.type Regex`, not Kotlin's `std/regex.type
  Regex`. Needed once `to_tmpl` uses std.
- **Subset B's parts:** setters, constructors, type parameters (`<T>`),
  properties, a method's implicit `(this)` input, and declarations
  nested in functions (`f().type Helper`) aren't derived yet; parsing
  and printing handle them all.
- **Names with `(` or `)`** can't be written, as in Kotlin, which has
  no escape for them; and a library's name isn't checked as Kotlin's
  `DashedIdentifier` is.
- **Kotlin's comment** says an exported name always gets `#0`; its code
  doesn't, and tmpl follows the code.

## Level 3: Connected declarations

**Decided (2026-10-29):**

- **One concept from outside:** core's, std's, and user code's
  `@connected` declarations are all "connected". Inside, each is
  resolved either by its translated name or, for core and std, by
  qname lookup to custom translation or lowering.
- **Customization is general, by naming:** a backend customizes only
  operators and connected declarations, never a hardcoded table of
  core functions.
- **User space resolves by name.** Kotlin Temper expects the user's
  host code to define the name the translation would otherwise give
  the declaration; it uses no qname or key. Only core's and std's
  connected declarations are mapped by qname.
- **Std may move to user space's mechanism** someday; core likely
  keeps specialized tree rewriting.

Proposed:

- **A fact on the declaration,** `connected: bool`, from `@connected`
  (core.temper marks its members the same way). Decided (2026-10-29):
  how it resolves isn't stored; it's recognized from the declaration's
  library. Core's and std's go through the variant's connection table
  by qname ("by qname" below), and everything else by name ("by name"
  below). One shared helper, `resolves_by_qname(decl)`, makes that
  call, so moving std over later changes only the helper and std's host
  code.
- **A connected declaration may have a body,** its pure Temper
  fallback (Kotlin: a translation that returns nothing uses the Temper
  implementation). Without one, it needs a translation on every
  backend.
- **By qname (core and std):** the variant's connection table, sorted by qname with
  Kotlin's strings as keys, maps a declaration to data or a hook:

  ```rust
  pub enum Connection {
      Template(&'static str),  // inline, with argument slots: "len({0})"
      Runtime(&'static str),   // a function in the variant's runtime library
      Type(&'static str),      // a connected type's host type
      Translate(fn(&mut Translator, &Call) -> Out), // custom output
      Lower(fn(&mut Rewriter, NodeId) -> NodeId),   // custom tmpl
  }
  ```

  `Lower` hooks run in one pass, first in the fixed order
  ([profiles.md](profiles.md)), so what they produce is lowered further
  like any tmpl; shared ones live in `temper-tmpl` for any variant to
  name. A declaration with no entry uses its body; with neither, it's
  an error naming the qname. Tests check each table: every key names a
  connected declaration of core or std, and every bodiless one has an
  entry.
- **By name (user code):** the naming pass names the declaration as any other,
  and the translator calls host code by that name, in the module's
  host file (Kotlin's convention: `__connected__.py`, `_connected.rs`,
  and so on), after applying defaults, since defaults apply in the
  callee. With a body, the body is the pure Temper fallback, as in the
  connected-fallback design proposed for Kotlin Temper: the declaration
  may list the backends that have host code
  (`@connected(["cpp", "rust"])`); listed backends call host code, and
  unlisted ones, and the interpreter, use the body. With no list, every
  backend needs host code, as today (Decided 2026-10-29, [question
  41](open-questions.md)). In user code, top-level functions and, since
  Kotlin's #642 (connected fallback types), classes with a complete
  Temper implementation, which a backend that takes host code makes a
  name for the host's class. As built (2026-11-02): the build gives the
  program its host files and `--no-connected` (`Program.host`), and
  `temper_tmpl::host::uses_host` makes Kotlin's per-backend choice. Host code depends on those names, so connected and `@keep`
  declarations claim theirs first, with exports, and are never
  suffixed (the naming pass, below). In the first variants, these
  names are Kotlin's, checked against the functional tests' host files.
- **Std's, per backend (2026-11-03):** std translates as a library of
  its own, its `@connected` members marked connected and keeping their
  Temper bodies; nothing upstream says which backends connect them.
  Each backend's own table (`connected::STD`, Kotlin's support network's
  std keys; Lua's connects every std key, as Kotlin's does) decides, by
  the member's key (`qname::std_key`, as `std/testing.type Test.bail()`),
  through `qname::by_connection(p, decl, connects_std)`; a member it
  doesn't connect uses its body. Backends are pluggable
  ([open-questions.md](open-questions.md), 48).
- **Operators aren't connected:** each variant spells every `Op`
  ([nodes.md](nodes.md#level-3-ops-and-core-as-declarations)) in a
  table indexed by `Op`, complete by construction.

## Level 3: The naming pass and each backend's `NamingRules`

Proposed, apart from Level 2's Decided points. `naming::assign(&Program,
&NamingRules) -> NameMap` gives every local declaration a string; the
`NameMap` is a `Vec<StrId>` by `DeclId`. External declarations take
their own library's names, and connected declarations resolved by
qname their variant's table's ([above](#level-3-connected-declarations)).

### The pass

1. **Scopes as the target sees them.** Walk each module and put every
   declaration in the scope its target gives it: module, class body,
   function, or block. Python and JS `var` put a block's locals in the
   function's scope; Python's class bodies don't enclose methods.
2. **Claim in order:** first each module's exported, connected
   (user code's), `@keep`, and other module-level names, then, scope by scope from the outside in, source names in
   source order (parameters first), then compiler-made names in
   `DeclId` order.
3. **Candidates:** the source name, or a compiler-made declaration's
   hint candidates; each put in the target's case for its kind and
   visibility, then escaped if reserved.
4. **Conflicts.** A candidate conflicts with a declaration that holds
   the same name when they're in the same scope as the target sees it;
   or the other is in an enclosing scope and is referenced inside this
   one's scope, so shadowing would change what that reference means;
   or the target forbids shadowing (C#). It also conflicts with
   reserved words and with names the translator prints in that scope
   (runtime helpers, and names from the variant's operator and
   connection tables). In Python, a
   local's scope is its whole function, since Python decides locality
   per function: a temporary can't be named for a module function
   that the function calls anywhere.
5. **First free candidate wins.** If none is free, a local in a
   function-scoped target may **share** the name of a local of the same
   function whose live range doesn't overlap its own, if neither is
   captured. Otherwise the first candidate takes the **shortest
   suffix** that's free: `x2`, `x3`, ….
6. **Exports are never suffixed:** two exports with one output name are
   one error, reported once. The same holds for user code's connected
   and `@keep` declarations, since host code calls them by name
   ([above](#level-3-connected-declarations)).

The result depends only on the program and the rules, so names are
stable across builds. `NameMap::suffixed()` lists the declarations
that got a suffix, for the golden count
([testing.md](testing.md#level-3-golden-tests)).

### `NamingRules`

```rust
pub struct NamingRules {
    pub reserved: &'static [&'static str], // keywords, and names the translator prints
    pub case: fn(DeclKind, Visibility) -> Case, // Keep, Camel, Snake, Pascal, ScreamingSnake
    pub private_prefix: &'static str,      // "_" for Python's internal top levels
    pub escape: Escape,                    // Suffix("_"), or Prefix("r#") in Rust
    pub suffix_separator: &'static str,    // "" gives `x2`
    pub identifier: fn(char, bool) -> bool, // allowed characters, first or not
    pub block_scoped_locals: bool,         // false in Python
    pub class_body_encloses_methods: bool, // false in Python
    pub may_shadow_outer_local: bool,      // false in C#
    pub may_rebind_in_scope: bool,         // Rust's `let` shadowing
    pub one_namespace: bool,               // types and values share names (Python, JS)
}
```

A character a target doesn't allow becomes `_` plus its hex code, and
that name is then treated as any other candidate.

**Python's first variant,** Kotlin-compatible (Decided 2026-10-29),
uses the conventions api-compat.md records: snake_case values,
functions, methods, and properties; types keep their case; reserved
words get a trailing `_` (`from_`); and internal top levels get a
leading `_`. Its reserved list is Python's keywords plus the builtins
and runtime names its translator prints. Kotlin compatibility covers
the public surface only: parameters are positional-only, so their
names, like locals', are free to be pretty; but a constructor's may be
passed by name, and those keep builtins' names, where Kotlin's take a
`_` (2026-11-17, "Parameters and locals" below).

**Example,** Python:

```temper
let f(a: Int): Int {
  if (a > 0) { let x = a * 2; console.log(x.toString()); }
  else { let x = 0 - a; console.log(x.toString()); }
  let x = a + 1;
  { let x = 3; console.log(x.toString()); }
  x
}
```

The first two `x`s share `x`: one scope in Python, but lifetimes that
don't overlap. The third `x` shares it too. The fourth is alive while
the third is, so it's `x2`, the only suffix, and the one the golden
count would show.

### As built (2026-10-30)

Proposed, settled while building `crates/temper-tmpl/src/naming.rs` for
the Python backend's first variant:

- **Function-scoped targets only, so far.** A function's parameters and
  locals share one scope, as Python's do; two may share a name when
  neither is alive while the other is. A live range runs from the
  definition (after its initializer, so `x = x + 1` can reuse an outer
  `x`'s name) to the last use, stretched to the end of any loop that
  uses it without defining it, since the next iteration may read it.
  Liveness is computed inside the pass, not yet a shared analysis.
  Python's checkers give a name one declared type in a function, so
  there two may share only if they have the same type, with or without
  null (a parameter's default's local, `T`, and the parameter, `T?`,
  but not a parameter `T` and a later local `T?`, since the parameter's
  annotation is the name's type, 2026-11-17), and a local declared
  inside another's initializer may not share its name, since a walrus
  assigns it before the other's annotation
  (`NamingRules::same_type_shares`, 2026-11-02). Python's translator
  then annotates a name bound more than once only where first bound,
  with its widest type (`Bindings` in temper-be-py). Python also hoists a
  block value's statements before the statement holding it, so what
  the value reads stays live until that statement, past every block
  value in it: `x++ + y++` gets `t` and `t2`, not one `t` read twice
  (`NamingRules::hoists_block_values`, 2026-11-13).
- **Module level:** the items, then the init block's locals, which live
  in the module's namespace in Python (so `_x`, private, not Kotlin's
  public `x_1`), claimed exported, connected, and `@keep` first, then
  source names, then compiler-made ones (`~console` as `_console`),
  each in `DeclId` order. Two of those first ones that clash are
  reported in `NameMap::clashes`, which the backend treats as an error.
- **A local can't take a module-level name its function refers to,**
  since Python decides locality per function (`analysis::module_refs`).
- **`NamingRules` holds only what's honored:** reserved lists, case by
  kind and exportedness (`Keep`, `Snake`, Kotlin's camel-to-snake
  conversion, `naming::snake_case`, or, with the C# backend, `Pascal`,
  Kotlin's camel-to-Pascal, `naming::pascal_case`, which shares its word
  breaks and keeps Kotlin's `A98_Rgb`), the private prefix, the escape
  suffix, and allowed characters; and, with the Rust backend, keywords
  escaped by a prefix instead (`raw_keywords` and `raw_prefix`: Rust's
  `r#type`), while the four keywords Rust can't make raw (`self`,
  `Self`, `super`, and `crate`) are reserved and take the suffix.
  Rust's first variant uses the function-scoped pass as Python's does:
  locals that share a name never overlap in life, so Rust's block
  scoping and shadowing keep each reference's meaning. Block scoping, class bodies, and
  shadowing rules come with the targets that need them. A suffix is
  the shortest number that's free (`x2`), after `_` if the name ends
  in a digit.
- **Block-scoped targets** (JS, 2026-10-30, `NamingRules::block_scoped`):
  a function's parameters and locals still take names in one pass, but
  two may share a name only when declared in blocks that don't nest (a
  parameter's block is the whole function), whatever their lifetimes:
  JS's `let` can't declare a name twice in one block, and an inner
  declaration shadows an outer one from the start of its block, so a
  read of the outer one earlier in the block would fail. Labels are
  named by the JS translator, from their source names (`outer` for a
  compiler-made one), and only where a jump needs one.
  Java's first variant (2026-10-30) uses the same rules, since Java
  forbids a local that shadows another local, and names its labels the
  same way.
  C#'s first variant (2026-10-30) uses them too, for the same reason;
  its keywords are verbatim identifiers (`raw_prefix` `@`), so a
  parameter keeps the source's name, which callers may pass arguments
  by. A parameter is still numbered if its function refers to a
  module-level declaration of the same C# name, which Temper's scoping
  makes rare; qualifying that declaration by its class instead would
  keep even those. C# has no labeled loops, so its labels are `goto`
  targets, `NAMEEnd` after a loop and `NAMENext` at the end of its
  body, from the same source names.
  C++'s first variant (2026-10-30) uses them too, so `-Wshadow` finds
  no local shadowing another; its labels are C#'s. Its reserved lists
  are Kotlin's keywords, the names the translator prints unqualified
  (`std`, `temper`, `core`, `int32_t`, `int64_t`, and
  `global_init_init`), and macros the runtime's headers define
  (`M_PI`, `INT32_MIN`, `assert`, …), each taking `_`. The module's
  helpers (`moduleMutex`, `load`, `store`) and its temporaries take
  names nothing in the module or function has (`naming::hints`, then a
  number).
  Lua's first variant (2026-10-30) uses them too: Lua would allow a
  `local` to shadow another, or to repeat a name in one block, but every
  `local` takes one of a function's 200 slots, and LuaLS flags shadowing,
  so locals in sibling blocks share names and nested ones never do. Its
  names are ASCII letters, digits, and `_`, the only characters Lua
  reads in a name, so others are `_` and their hex code (`όνομα` is
  `_3cc_3bd_3bf_3bc_3b1`, where Kotlin's, after NFKD, is
  `x3bfx301x3bdx3bfx3bcx3b1__2`). Its reserved lists are Lua 5.4's
  keywords (`goto` too, which Kotlin's list leaves out), `_ENV` and
  `_G`, Lua's standard globals, 5.1's and LuaJIT's too (`string`,
  `type`, `error`, `unpack`, `bit`; 2026-11-17, since a module's host
  code is inlined in it and may reach any of them), and what else the
  translator prints (`temper`, `exports`, and `with_tostring`), each
  taking `_`; an export keeps its name as its module's table's key
  (`NameMap::public`, below). Its
  labels are `goto` targets, as C#'s: `continue` ending the body of a
  loop that a `continue` of its own needs, and `continue_NAME` and
  `break_NAME` for jumps from inner loops, from the same source names.
- **Members** (2026-11-12, `NamingRules::member_reserved`): a class's
  or interface's members are escaped against their own lists, not the
  whole `reserved` set, since a member's name is looked up on its object
  or class, where the names the translator prints aren't. So a builtin's
  name stays public API, as Kotlin's (JS `get` and `set`, Python `map`,
  `hash`, and `format`). Each backend keeps its keywords, plus what a
  member would really hide or clash with: Python's names printed in a
  class body (`property`, `staticmethod`, and the builtins its
  annotations name, as `int` and `str`, which a method defined earlier
  in the body would hide), JS's `constructor`, Rust's `new`, `clone`,
  and `clone_boxed`, and, unchanged, Java's (a field obscures a type of
  its name) and C++'s (a member hides a name in its member functions).
  Lua's and C#'s are their keywords alone.
- **Parameters and locals** (2026-11-17, `NamingRules::local_reserved`):
  where a backend reaches what a function's own names would hide some
  other way, its parameters and source-named locals are escaped against
  that list instead of `reserved`; compiler-made locals still avoid all
  of `reserved`, being free to take another name, and module-level
  names and members keep theirs. Python's is its keywords (not soft
  keywords), dunder names, and `self`, so a constructor's parameters,
  which Python callers may pass by name, keep builtins' names (`help`,
  `min`, `type`) whatever the body does, and so do locals (`sum`,
  `next`), which keeps a parameter's default in place (`if max is None:
  max = 10`). Its translator marks every builtin it prints, in code and
  in annotations, and where a function or one around it binds the name,
  prints it through the `builtins` module, imported once under a name
  nothing else in the module has (`_builtins.len(items)`); elsewhere
  builtins stay bare. Its imports already make way for any
  declaration's name, so a parameter named for a runtime helper keeps
  its name too. Other backends set none.
- **Exports' public names** (2026-11-17, `NameMap::public`): an
  export's name as its module's table or namespace keys it, escaped
  only by `member_reserved`, as a member's is, where its own name
  escapes further; dependents read it from the `Interface` beside the
  name they bind. Lua's module tables use it (`return { string =
  string_ }`), since a field hides nothing; where the binding is the
  key (Python's module attributes) it goes unused. Two exports whose
  public names collide clash.
- **Not named:** labels (Python prints none), and core's declarations
  (the connection table's names).
- **With modules** (2026-10-30): other libraries' declarations (std's
  and the work root's, but core's) are named as their own library named
  them, so a reference spells the name the other library gave it: since
  2026-11-03, from its `naming::Interface` (open question 46), the names
  its naming gave its module-level declarations and their members, by
  qname without the index, which the build gives each program for its
  backend (`Program::interfaces`: `tempest build` names libraries in
  dependency order, and the testkit's `program_for` its shared
  libraries). A declaration of a library with no interface is an error
  (`NameMap::missing`), but std's, named as its naming would name its
  exports until std builds as a library of its own; each module claims, before its own declarations, the names of
  what it imports (`analysis::module_imports` and `test_imports`), so
  none of its own takes one, and an export that would is a clash; and a
  function's locals avoid the names of the other libraries'
  declarations it refers to, as they do its module's. Tests' functions,
  parameters, and locals are named as functions' are. Those names
  come from the other library's own naming, never derived again
  ([open-questions.md](open-questions.md), 46).
- **Not yet:** `temper.keep` (earlier choices read back), hints'
  parameter and field candidates (`naming::hints` has the callee's or
  read declaration's name, the type's, and `value`), and the
  suffix-count golden.
