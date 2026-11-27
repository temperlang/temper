# tmpl: formats and versioning

Part of the [tmpl design](README.md).

## Level 1: Formats

**Decided (the direction):** a binary and a human-friendly but complete
text form, each round-tripping losslessly; one schema defining both;
one canonical, complete text form; an optional viewer that elides
derivable detail; binary also serving the library cache.

- **Decided (2026-10-29): canonical text is Temper-like,** with a
  distinct header and an obviously explicit style, so it's never
  mistaken for Temper. It leaves out types that the three local rules
  give back (a literal's type, a name's type from its declaration, and
  a call's result from its callee's signature with explicit type
  arguments); the reader restores them and the validator checks them.
- **The schema is Rust code in the crate** (Proposed), a `macro_rules!`
  table that declares each node kind and its fields once, and generates
  the node types, a field walker, and the text and binary readers and
  writers. No proc macros or new crates (code-style.md).
- **Spans in a trailing section,** optional on reading: golden tests
  strip it, and text without it reads with unknown spans.
- **The viewer** elides ids, conversions, capture lists, and obvious
  types for people. It never needs to read back.
- **Binary** can come later without changing the model, written to a
  spec so another implementation (Kotlin Temper, as a bridge) could
  read it.
- **JSON, later** (Proposed 2026-10-29, the user's idea; not planned
  yet): a JSON form for third-party tooling, generated from the same
  schema as the text and binary forms, as a third writer and reader.

Level 2, below, has the structure and an example.

### Where the goals conflict, and the recommendations

The user's priorities: (1) complete and explicit for every backend, (2)
profiles and lowering, (3) text round-trip, (4) binary.

- **Explicit versus readable text (1 against 3):** derivation rules plus
  a trailing spans section keep canonical text complete yet close to
  source; the viewer covers reading.
- **Explicit versus idiomatic output (1 against design.md's naming
  rules):** order is a guarantee and purity a fact; temporaries come
  only from a profile's lowering.
- **One model across levels versus simple passes (2):** node families
  with default structural walks, so a pass touches only its kinds.
- **One starting form versus per-backend choices (1 and 2):** where a
  choice is purely a target's spelling (Java's boxed type parameters,
  Rust's `Option`), it belongs in that backend's translator, not a pass.
- **Binary later versus a stable cache (4):** versioning starts on day
  one, in the text header too ([below](#versioning-and-compatibility)).

### Versioning and compatibility

Proposed:

- **Two versions in every file's header:** the format's (how it's
  encoded) and the model's (what nodes and fields mean). Text and binary
  share the model version.
- **At first, exact match only:** a cache is valid only for the compiler
  that wrote it ([library-cache.md](../library-cache.md)), so the model can
  change freely until tempest's backends settle.
- **Later, for published caches:** new node kinds and fields only with
  defaults; meaning changes bump the major version. Recorded in
  `temper.keep` alongside other backward-compatibility facts.
- **Only the starting tmpl is ever stored.** Lowered forms are internal
  to a build, so passes can change without a version bump.

## Level 2: Format structure

**Text** (Decided 2026-10-29: Temper-like, explicit, with a distinct
header). Its shape:

```text
file    := header uses* module* spans?
header  := "tmpl" FORMAT-VERSION "model" MODEL-VERSION "library" NAME VERSION
uses    := "uses" LIBRARY VERSION "{" external-decl* "}"
module  := "module" NAME#ID "{" import* decl* "init" "{" stmt* "}" test* "}"
```

Its style, so it's never mistaken for Temper:

- Every declaration is written `name#id`; compiler-made ones are
  `~role#id`. References are `name#id` too, the name checked by the
  reader ([question 18](open-questions.md)).
- Declarations always carry their types; expressions carry them only
  where the local rules don't give them back, as `(e): T`.
- Control nodes use Temper's syntax (`if`, `while`, `for`, `&&`,
  `orelse`, `return`); every operator is a call written `%Type.op(…)`,
  so `%Int32.add(i#4, 1)`, never `i + 1`. Core's and std's members are
  ordinary calls of the external declarations their `uses` section
  lists, with qnames (Proposed 2026-10-29: `%` is only for operators,
  [nodes.md](nodes.md#level-3-ops-and-core-as-declarations)).
- A call that may fail is marked `^`.
- Bodies are explicit: constructors assign their fields, and a
  function's last value is a `return`.
- Spans follow in a trailing `spans { … }` section, keyed by each
  node's position in writing order.

**The viewer** writes the same shape with options: hide ids where a
name is unique, hide conversions and capture lists, show `==` and `+`
for their builtins and operator methods, and show spans as
`file:line:column`. A CLI command (`tempest tmpl FILE --profile py
--view`) prints starting or lowered tmpl.

**Binary:** a header (magic `TMPL`, format and model versions, the
library's identity and its dependencies' versions), then a section
table, so a reader can skip docs or spans, and sections for strings,
types, declarations, external links, nodes stored column-wise (kinds,
types, child ranges, payloads), docs, and spans delta-encoded. Integers
are LEB128 varints and every reference an index. Equal programs give
equal bytes, so caches can compare by hash.

### Worked example

Temper source:

```temper
export class Point(public x: Int, public y: Int) {
  @operator("==")
  public eq(other: Point): Boolean { x == other.x && y == other.y }
}

export let firstIndex(points: List<Point>, text: String): Int {
  let wanted: Point = { x: text.toInt32() orelse 0, y: 0 };
  var i = 0;
  for (let p of points) {
    if (p == wanted) { return i; }
    i += 1;
  }
  -1
}
```

Starting tmpl (canonical text, spans left out):

```text
tmpl 1 model 1 library example 0.1.0
uses `temper-core` 0.1.0 {
  class List#90<T#91>;
  class ParseError#94;
  class String#92 { connected method toInt32#93(radix?: Int32): Int32 throws ParseError#94; }
}

module example#1 {
  export class Point#2 {
    public field x#3: Int32;
    public field y#4: Int32;

    public constructor new#5(x#6: Int32, y#7: Int32) {
      this.x#3 = x#6;
      this.y#4 = y#7;
    }

    @operator("==") public method eq#8(other#9: Point#2): Boolean {
      return %Int32.eq(this.x#3, other#9.x#3) && %Int32.eq(this.y#4, other#9.y#4);
    }
  }

  export fn firstIndex#10(points#11: List#90<Point#2>, text#12: String#92): Int32 {
    let wanted#13: Point#2 = new Point#2((text#12.toInt32#93(omitted)^ orelse 0): Int32, 0);
    var i#14: Int32 = 0;
    for (let p#15: Point#2 of points#11) {
      if (p#15.eq#8(wanted#13)) {
        return i#14;
      }
      i#14 = %Int32.add(i#14, 1);
    }
    return -1;
  }

  init {}
}
```

The object literal is a `New` with placed arguments; `p == wanted` is a
call of `eq#8`; the field reads inside `eq#8` use `Int32`'s own
equality; and `orelse`'s type is written, since no local rule gives it.

**Lowered for Python** (exceptions; no statements inside expressions;
everything else native). Only `firstIndex`'s first statement changes:

```text
    var ~x#16: Int32;
    try {
      ~x#16 = text#12.toInt32#93(omitted)^;
    } catch {
      ~x#16 = 0;
    }
    let wanted#13: Point#2 = new Point#2(~x#16, 0);
```

The temporary's hint comes from the constructor parameter it feeds,
`x`, with `parsed` (from `toInt32`) as its next candidate. Python's
locals are then `points`, `text`, `x`, `wanted`, `i`, and `p`, with no
numbers. Had `firstIndex` already a local `x` alive here, the temporary
would be `parsed`, not `x2`. Evaluation order needs no other
temporary, since `0` is pure. With [question 14](open-questions.md) decided,
`eq#8` presents as `__eq__` too, and the translator may print `p ==
wanted`; `%Int32.add` prints as `i + 1` only where value ranges rule
out overflow.

**Lowered for Rust** (results; everything else in this example native):

```text
    let wanted#13: Point#2 = new Point#2(result_or(text#12.toInt32#93(omitted), 0), 0);
```

`toInt32#93` now has the lowered signature, returning
`Result<Int32>`, so the call needs no `^`; `ResultOr` replaces the
`orelse` over one call, and the translator may print `.unwrap_or(0)`.
`firstIndex` can't fail, so its signature is unchanged. No
temporary is added, so Rust's locals are the source's: `points`,
`text`, `wanted`, `i`, and `p`. The module
has no state, so first-use init adds no guard. `Point`'s `x` and `y`
stay fields in bodies; the surface presents them as getters, and the
properties-to-methods pass rewrites only reads from outside `Point`.

## Level 3: The text format, for subset A

Proposed throughout, apart from what Level 2 decided; detailed enough
to write `text::write`, `text::read`, and `text::view`. Subset B adds
classes, annotations, and conversions (`uses` came early, with core's
declarations, below)
([roadmap.md](roadmap.md#level-3-after-subset-a)).

### Tokens

```text
IDENT    := [A-Za-z_][A-Za-z0-9_]*
name     := IDENT | "`" (any character but "`", "\" and newline | "\`" | "\\")* "`"
decl     := name "#" INT                     // a source declaration, where it's defined
          | "~" name ("|" name)* "#" INT     // compiler-made: hint candidates in order
ref      := name "#" INT | "~" name "#" INT  // a use; the name must be the declaration's
OP       := "%" IDENT "." IDENT              // an operator: %Int32.add
INT32    := "-"? DIGITS                      // in range
INT64    := "-"? DIGITS "i64"                // as in Temper
FLOAT64  := "-"? DIGITS "." DIGITS (("e" | "E") "-"? DIGITS)? | "nan" | "inf" | "-inf"
STRING   := '"' (character | "\" ('"' | "\" | "n" | "r" | "t" | "u{" HEX+ "}"))* '"'
VERSION  := DIGITS "." DIGITS "." DIGITS ("-" [A-Za-z0-9.-]+)?
```

- Since every reference carries `#`, bare words are keywords (`fn`,
  `let`, `true`, `nan`, `Int32`, …), and no user name can be mistaken
  for one.
- The writer writes floats in the shortest form that reads back to the
  same bits, always with `.` or an exponent; strings escape `"`, `\`,
  and control characters, and nothing else.
- `//` comments are skipped, for hand-written tests; the writer never
  writes them. `///` lines before a declaration are its doc comment,
  kept in the `DocTable`.
- Whitespace separates tokens and is otherwise ignored.

### Grammar

```text
file        := header module* spans?
header      := "tmpl" INT "model" INT "library" name VERSION ("profile" name)?
module      := "module" decl "{" item* "init" block "}"
item        := function | module-let
function    := "export"? "fn" decl "(" (param ("," param)*)? ")" ":" type throws? block
param       := decl ":" type
module-let  := "export"? ("let" | "var") decl ":" type ";"
throws      := "throws" type               // a failure type or failure parameter
type        := "Void" | "Boolean" | "Int32" | "Int64" | "Float64" | "String" | "AnyValue"

block       := "{" stmt* "}"
stmt        := ("let" | "var") decl ":" type ("=" expr)? ";"   // Let
             | ref "=" expr ";"              // Assign, or SetModuleLet for a module var
             | "init" ref "=" expr ";"       // InitLet, only in an init block
             | "if" "(" expr ")" block ("else" (block | if-stmt))?
             | decl ":" "while" "(" expr ")" block
             | decl ":" "do" block "while" "(" expr ")" ";"
             | ("break" | "continue") ref ";"
             | "return" expr? ";"
             | expr ";"                      // ExprStatement

expr        := and ("||" and)*
and         := primary ("&&" primary)*
primary     := literal | ref | call | builtin | error
             | "(" expr ")" (":" type)?      // grouping, or a written type
             | "(" "if" "(" expr ")" expr "else" expr ")" ":" type   // IfValue
             | "(" "value" "{" stmt* expr "}" ")" ":" type           // BlockValue
call        := ref "(" args? ")" "^"?        // CallFunction; "^" when it may fail
builtin     := OP "(" args? ")" "^"?         // Op
args        := expr ("," expr)*
error       := "(" "error" "(" STRING ")" ")" ":" type
literal     := "true" | "false" | INT32 | INT64 | FLOAT64 | STRING | "void"
```

- **An `else` whose block holds only an `If`** is written `else if`,
  and read back as that block.
- **Written types:** the writer writes `(e): T` exactly where the
  local rules don't give the type back. The reader accepts a redundant
  one and checks it, which helps hand-written tests. Under [question
  25](open-questions.md), `IfValue` and `BlockValue` would drop theirs.
- **Reading:** the reader builds through the `Builder`, so it checks
  as it goes; a node's kind follows from its form and, for `ref = …`,
  from the declaration's kind. Declarations may be used before they're
  defined in writing order (a call of a later function), so the reader
  first collects every `decl`, then reads bodies.
- **Ids:** a declaration keeps its `DeclId` from the builder to the
  last pass; a pass appends the declarations it adds and leaves a
  removed one as a tombstone, so lowered text differs from starting
  text only where it changed ([question 24](open-questions.md)). The reader requires each
  id to be defined once and treats unused ids as tombstones.
- **Layout:** two-space indents, one statement per line, `{` on the
  same line, a blank line between items, and no line wrapping, so
  output is deterministic.

### As built (2026-10-29)

Proposed, settled while building `text::write` and `text::read`:

- **Additions to the grammar:** a bare block is a statement (`{ … }`,
  for nested scopes); the library's and profile's names are `name`s,
  so `temper-core` is written in backquotes; and the spans section's
  entries may leave out the span (`@4 ~;`, a derived node with no span
  yet; `#3 name 0:4-9;`), so writing loses nothing.
- **`BlockValue` is written on one line,** as in the example below,
  statements and all, so an expression stays on its line.
- **Tokens:** `-` begins a negative number only where it doesn't
  follow a letter or digit, so `0:10-20` reads as a span; a word that
  begins with a digit (a version, a hash) is one token; NaN is always
  the one `nan` reads as, so `Float64` literals compare by bits.
- **What the reader checks itself:** syntax; versions (`tmpl 1 model
  1` only); each id defined once, and locals and labels defined before
  they're used (functions, parameters, and module `let`s are read in a
  first pass, so a body may use any of them); a reference's name and
  `~` matching its declaration; only functions called; written types
  matching the local rules; and `^` on exactly the calls that may
  fail. Everything else, such as argument types, scopes, and
  definite assignment, is the validator's, so it can be tested on text.

### Qnames, connected marks, and core's declarations

Proposed (2026-10-29), and built
([nodes.md](nodes.md#level-3-ops-and-core-as-declarations),
[names.md](names.md#level-3-qnames)). Additions to the grammar:

```text
file        := header uses* module* spans?
uses        := "uses" name VERSION "{" external* "}"
external    := "connected"? ("class" | "interface") decl "{" member* "}"
             | "connected"? "fn" decl ext-sig ";"
member      := "connected"? ("method" | "get" | "static") decl ext-sig ";"
ext-sig     := "(" (name "?"? ":" type ("," name "?"? ":" type)*)? ")" ":" type throws?
module      := "module" decl ("in" STRING)? "{" item* "init" block "}"
item        := ("qname" STRING)? "connected"? (function | module-let)
function    := "export"? "fn" decl "(" (param ("," param)*)? ")" ":" type throws? (block | ";")
type        := … | ref                      // a non-generic class or interface
primary     := … | primary "." ref "(" args? ")" "^"?   // CallMethod
             | "omitted"                    // Omitted
```

- **External declarations' parameters** have names but no ids, since
  nothing refers to them; the names feed naming hints. `?` marks an
  optional one.
- **A module's path,** `in "foo/bar"`, is left out for the library's
  root, as most tests are.
- **Qnames** aren't written unless one differs from its derivation:
  then `qname "…"` (the part after the module's path, as
  `"f().x=#1"`) precedes the declaring form, an item, a parameter, or
  a `let`. External
  declarations' qnames follow from the `uses` section's nesting;
  `temper-core`'s are written `core.…`, as Kotlin writes them.
- **`connected`** marks a declaration; whether it resolves by name or
  by qname follows from its library (core and std by qname), by one
  helper the frontend and the reader share, so moving std later
  changes only that helper.
- **`CallMethod` is virtual** exactly when the method's owner is an
  interface; text doesn't write it.

As built (2026-10-29), the grammar above with these changes:

```text
uses        := "uses" decl VERSION "{" external* "}"   // the library has an id
external    := doc* "keep"? "keepTest"? connected? ("class" | "interface") decl "{" member* "}"
             | doc* "keep"? "keepTest"? connected? "fn" decl ext-sig ";"
member      := doc* "keep"? "keepTest"? connected? ("method" | "get" | "static") decl ext-sig ";"
connected   := "connected" ("(" STRING ("," STRING)* ")")?   // backends with host code
item        := doc* ("qname" STRING)? "keep"? "keepTest"? connected? "export"? (function | module-let)
param       := ("qname" STRING)? decl "?"? ":" type
stmt        := … | "qname" STRING ("let" | "var") …       // a local's override
```

- **A used library is a declaration** (kind `Library`), so its
  external declarations' owner is an id like any other; text writes
  its id as a module's, ``uses `temper-core`#12 0.1.0``, so ids survive
  a round trip.
- **Marks come in one order:** `qname "…"`, `keep`, `keepTest` (2026-11-17:
  `@keepTest`, kept for tests' host code), `connected` (with its
  backends, as `connected("cpp", "rust")`), then `export`. A
  connected function without a body ends in `;`.
- **The program's own parameters** take `?` when optional, as
  externals' do, and the signature records it too.
- **Members are written in `DeclId` order,** externals before modules;
  the reader first scans every used class and interface, so a
  signature may name one written later.
- **A receiver** is parenthesized when it's `&&`, `||`, or a `Float64`
  literal, whose `.` would read as part of the number:
  `(1.5).sqrt#6()`. A getter is called with parentheses too,
  `text#13.isEmpty#10()`.
- **`omitted`** is read only as an argument, taking its type from the
  callee's parameter in that place, so text never writes its type.

As built (2026-10-30), with `to_tmpl`:

- **`var` parameters:** a parameter may be marked `var`, written before
  its name, `fn fib#1(var i#3: Int32): Int32`, so it may be assigned
  (`param := ("qname" STRING)? "var"? decl "?"? ":" type`); Temper
  allows `let f(var i: Int)`, as fibonacci uses.
- **The frontend's spans:** each source file is written by its name
  alone, its path within a one-module library, with a 128-bit FNV-1a
  hash of its text; the module's declaration spans its first file.

As built (2026-10-30), with modules and tests:

- **Another library's modules** go in its `uses` section, each named by
  its path there, with what's used of it, functions, `let`s and
  `var`s, classes, and interfaces:

  ```text
  uses std#5 0.6.0 {
    module testing#6 {
      class Test#7 {}
    }
  }
  uses other#1 1.2.0 {
    module `sub/inner`#2 {
      fn shout#3(s: String): String;
      var count#4: Int32;
    }
  }
  ```

  (`external := … | "module" decl "{" external* "}" | ("let" | "var")
  decl ":" type ";"`, a module only in a library, a `let` only in a
  module). The scan before the first pass defines these modules, with
  the classes in them, so signatures can name them early.
- **Tests** follow a module's init block, each `test`, its name as a
  string, and then a function as a module item's is written:

  ```text
    init {}

    test "twice" ~test#14(~test#15: Test#7): Void throws Error#18 {
      addTwice#12(1);
    }
  ```

  Writing order, which numbers nodes, takes each module's items, its
  init block, and then its tests.
- **Test parts** (2026-11-18): a partitioned module's test items and
  test init ([modules.md](modules.md)) come between its init block and
  its tests, in a section of their own, written only where there are
  some (`for-tests := "for" "tests" "{" item* ("init" block)? "}"`):

  ```text
    init {
      init libValue#2 = 1;
    }

    for tests {
      fn forTests#3(): Int32 {
        return testValue#4;
      }

      let testValue#4: Int32;

      init {
        init testValue#4 = 2;
      }
    }
  ```

  Writing order takes them after the init block, before the tests.

As built (2026-10-30), with classes and interfaces (subset B):

```text
item        := … | doc* marks ("class" | "interface") decl supers? "{" class-member* "}"
supers      := "extends" type ("," type)*
class-member:= doc* marks ("static"? ("let" | "var") decl ":" type ";"     // Field, StaticField
             | "static" "fn" decl params ":" type throws? block            // a static Function
             | "abstract"? ("method" | "get" | "set" | "constructor")
               decl params ":" type throws? (block | ";"))                 // Method, AbstractMethod
marks       := ("qname" STRING)? "keep"? "keepTest"? connected? "export"? "private"? "protected"?
               "auto"? ("operator" "(" "\"==\"" ")")? ("operator" "(" "\"<=>\"" ")")?
stmt        := … | postfix "." ref "=" expr ";"   // SetField
             | "init" ref "=" expr ";"           // InitStatic, for a static field
primary     := … | "this" | "(" "this" ")" ":" type | "new" ref "(" args? ")" "^"?
             | postfix "." ref                   // GetField; a static field is a bare ref
             | "(" ("upcast" | "downcast") "(" expr ")" ")" ":" type
             | "(" "as" "(" expr ")" "^"? ")" ":" type
             | "is" "(" expr "," type ")" | "identical" "(" expr "," expr ")"
```

- **`this`** is bare in a method, getter, setter, or constructor,
  whose class gives its type: `this.w#11 = w#55;`. Anywhere else, or
  typed otherwise (problems for C1), it's `(this): T`.
- **A member's kind** follows from its word and its declaration's:
  `method`, `get`, `set`, and `constructor` make a `Method`, or with
  `abstract`, an `AbstractMethod`; `.f#2` reads a field, and
  `.m#3(…)` calls a method, getter, or setter. Constructors are called
  only with `new`. Used libraries' classes take the same members, with
  `ext-sig`s, and a static function there stays `static name#id(…)`.
- **The scan** defines the program's classes and interfaces too, so a
  signature or `extends` may name one written later.
- **A receiver whose type is written** is parenthesized, so the type
  doesn't seem to run into the member: `((upcast(t#25)): Named#2).name#3()`.

As built (2026-11-01), with parameters' defaults (subset B):

```text
primary     := … | "(" "coalesce" "(" expr "," expr ")" ")" ":" type   // Coalesce
```

- **An optional parameter of the program's own** is written as before,
  `end#4?: Int32?`, typed nullable, and its default is the body's first
  `let`s: `let end#5: Int32 = (coalesce(end#4, %Int32.add(start#3,
  10))): Int32;` ([nodes.md](nodes.md), "Subset B's next step:
  parameters' defaults").

### The spans section

```text
spans   := "spans" "{" file-entry* span-entry* "}"
file-entry := "file" INT STRING HEX ";"       // id, path, hash
span-entry := "#" INT span ("name" span)? ";" // a declaration, and its name
            | "@" INT span "~"? ";"           // a node by its writing order; "~" if derived
span    := INT ":" INT "-" INT                // file, start byte, end byte
```

Nodes count in the order the writer starts them (preorder),
declarations included. A line table and wide lines aren't written:
the reader is to recompute them from the source files when present,
and otherwise reads spans without columns. As built (2026-10-30), it
always reads them without, so a program read from text maps nothing.
Golden tests strip the section.

### The view

`text::view(&Program, &ViewOptions)` writes the same shape for people,
and is never read back:

- ids only where a name repeats in its module (`x#5`), and hint
  candidates after the first left out; `~` stays, so temporaries
  stand out;
- operators as Temper writes them, with Temper's precedence
  (`%Int32.add(a, b)` as `a + b`, `%Boolean.not(x)` as `!x`), and
  calls of core's and std's members as Temper writes them
  (`n.toString()`), with `Omitted` arguments left out;
- types only on declarations; `IfValue` and `BlockValue` with braces,
  as in Temper; loop labels only where a jump names an outer loop;
- `^` kept, since bubbling matters to a reader;
- from subset B, conversions and capture lists left out, and an
  operator method's call shown as its operator;
- spans left out, or with `spans: true` shown as trailing
  `// file:line:column` comments.

Since the view depends only on the program, stable names give stable
views; golden tests keep both ([testing.md](testing.md)).

### Example

The Level 2 example predates subset A's details; with them, its
`String#92` is plain `String` ([question 26](open-questions.md)). Temper source:

```temper
var calls = 0;

export let clamp(n: Int, limit: Int): Int {
  calls += 1;
  let low = if (n < 0) { console.log("negative"); 0 } else { n };
  if (low > limit) { limit } else { low }
}

console.log(clamp(5, 3).toString());
```

Starting tmpl, canonical. The frontend pushes a function's last value
into the branches of a trailing `if`, so no `IfValue` is needed there:

```text
tmpl 1 model 1 library example 0.1.0

uses `temper-core`#12 0.1.0 {
  class Int32#7 {
    connected method toString#8(radix?: Int32): String;
  }
  interface Console#9 {
    connected method log#10(message: String): Void;
  }
  connected fn getConsole#11(name?: String): Console#9;
}

module example#0 {
  let ~console#6: Console#9;

  var calls#1: Int32;

  export fn clamp#2(n#3: Int32, limit#4: Int32): Int32 {
    calls#1 = %Int32.add(calls#1, 1);
    let low#5: Int32 = (if (%Int32.lt(n#3, 0)) (value { ~console#6.log#10("negative"); 0 }): Int32 else n#3): Int32;
    if (%Int32.gt(low#5, limit#4)) {
      return limit#4;
    } else {
      return low#5;
    }
  }

  init {
    init ~console#6 = getConsole#11(omitted);
    init calls#1 = 0;
    ~console#6.log#10(clamp#2(5, 3).toString#8(omitted));
  }
}
```

`console.log` and `toString` are calls of core's declarations, and
`console` is a compiler-made module `let`, as Kotlin's frontend makes
it (built 2026-10-29; this is the first golden case in
`crates/temper-tmpl/tests/text/basics.cases`). The qnames follow from the owners and names, so none is written:
`example.clamp()`, `example.clamp().(n)`, `example.clamp().low=`, and
`core.type Int32.toString()`.

The same as a view:

```text
module example {
  let ~console: Console;
  var calls: Int32;

  export fn clamp(n: Int32, limit: Int32): Int32 {
    calls = calls + 1;
    let low: Int32 = if (n < 0) { ~console.log("negative"); 0 } else { n };
    if (low > limit) {
      return limit;
    } else {
      return low;
    }
  }

  init {
    ~console = getConsole();
    calls = 0;
    ~console.log(clamp(5, 3).toString());
  }
}
```

Lowered for Python's first variant (header `profile py`), only `low`
changes: the hoisting pass assigns the `Let` itself, so no temporary
is needed ([profiles.md](profiles.md#level-3-lowering-for-subset-a)):

```text
    let low#5: Int32;
    if (%Int32.lt(n#3, 0)) {
      ~console#6.log#10("negative");
      low#5 = 0;
    } else {
      low#5 = n#3;
    }
```

Python's names are the source's (`calls`, `clamp`, `n`, `limit`, and
`low`), plus the hint `console`; the translator adds `global calls` to `clamp`, since the
module-state analysis shows that it writes `calls`.
