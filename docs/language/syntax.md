# Syntax Reference

This document helps understand how Temper code is parsed.  Readers
can use `temper repl` to get feedback on how the language interprets
a piece of code.  Especially useful is the *describe* REPL command
which lets you view a snapshot of the compilation state at various
processing stages.

> **Note:** the [REPL](../tools/repl.md)'s `describe` takes the
> compiler's own steps, such as `"parsed"` and `"settled"`, not the
> stage names of Temper's earlier releases; the [playground](../tools/playground.md) shows errors and every
> language's translation as you type
> ([inconsistencies](../inconsistencies.md#i9-the-command-line)).

Temper's syntax should be familiar to users of &ldquo;C-like&rdquo;
languages: languages that use `{...}` around blocks and semicolons
(`;`) separate computational steps.  It is most similar to TypeScript;
types follow names (`name: Type`) with a colon in between.
But its syntax is distinct from JS/TS in details.

Some differences include:

- Temper uses `let` for named function declarations so that there is no
  confusion about when a named function's name is visible in the
  surrounding scope.  Temper is a macro language so this is important
  when macros can operate on declarations.
- Temper allows for interpolation into any string, so `"chars${expr}"`.
  For backwards compatibility, JavaScript could only allow interpolation
  into back-tick strings (<code>\`chars${expr}\`</code>).
- Temper has substantially different syntax for function expressions
  (`fn (x: Int): Int { x + 1 }` instead of `(x: Int): Int => x + 1`)
  and function types (`fn <T> (T): T` instead of `<T>(T) => T`).
- Temper's `import` and `export` syntax, which allows connecting modules
  together is different.


The grammar below explains the main syntactic categories.
It's meant to be advisory, to help learners discover features by following
grammatical threads.

It is not an exact grammar.  Temper has a three-stage parse: lexical
analysis, operator precedence grouping, tree building.  This grammar
is derived from the tree builder which operates on a stream of tokens
*after* an operator precedence parser has inserted synthetic
parentheses into the token stream and *after* some other token level
rewriting operations.


Since Temper is a macro language, some language features that would
have separate syntactic paths in a non-macro language are instead
implemented as macros; they parse as regular function calls, but those
functions are macros that apply at a later compilation stage.  For example,
`if` is a macro so there is no dedicated syntax for `if` statements below.

## Structure of a file

<!-- TODO(mvs): explain high-level that parsing of a file happens in the context of an embedding language like Markdown, and that `;;;` is a token -->

### Syntax for *Root*

```ebnf
Root := TopLevels end-of-file
```

The root of a Temper module is a sequence of top-levels followed by an end of file marker.

### Syntax for *TopLevels*

```ebnf
TopLevels := (TopLevelsInSemi | TopLevel)*
```

Top-levels are separated by semicolons in a module body or block.

### Syntax for *TopLevel*

```ebnf
TopLevel := TopLevelNoGarbage | Garbage
```

A top-level is roughly a whole declaration, or expression.
Temper is an expression language, so most statement-like constructs can
also nest.

### Syntax for *Garbage*

```ebnf
Garbage := Consumes problem tokens and emits an error syntax-tree node
```

### Syntax for *TopLevelNoGarbage*

```ebnf
TopLevelNoGarbage := EmbeddedComment? TopLevelNoGarbageNoComment
```

### Syntax for *TrailingSemi*

```ebnf
TrailingSemi := ";"? Nop*
```

Semicolons (`;`) are allowed at the end of a block or module body.
An expression followed by a semicolon is not implicitly the result of
the containing block or module.

Trailing semicolons are never [inserted](#automatic-semicolon-insertion).

## Statements

### Syntax for *Stmt*

```ebnf
Stmt := LabeledStmt | MatchBranch | StmtBlock | CommaExpr
```

Statements are not a large syntactic category, but include labeled
statements (like `someName: /* loop or block */`), jumps (`break`,
`continue`, `return`, etc.) which go to the end or beginning of
a containing statement and which may refer to a label.

Besides those, any expression may appear in statement position.

### Syntax for *Nop*

```ebnf
Nop := ";"
```

A semicolon used to separate statements.
Since our parser is built around an operator precedence parser, and semicolon is a
low-precedence operator, this grammar consumes semicolons but does not require them.

Not all semicolons need to appear explicitly in the program text.

#### Automatic Semicolon Insertion

Semicolons are inserted in the following places:

- After  `}` that end a line except before a close bracket or an operator token that is not prefix.
- Before `{` that starts a line except after an open bracket or an operator token that is not postfix.

Except that, semicolons are never inserted after the close curly bracket in a `${` and `}` pair.

This is more conservative than semicolon insertion in JavaScript,
but still simplifies several things.

##### All adjacent statements are separated by semicolons

There's no need to have a set of special statements like `if (...) stmt0 else stmt1` that do not
need to be followed by a semicolon.
Productions for a series of statements and declarations can simply assume that semicolons appear
before them.

##### No limited set of statement continuers

We don't need a special set of statement continuers like `else` so that we know that
the token sequence `} else {` is part of one statement.
This lets us use common cues to allow new continuers like

```temper
foo(x) {
  // Ruby-style block
} bar(y) {
  // ruby-style block
}
// ⏸️
```

which de-sugars to a single statement

```temper
foo(x, fn { ... }, bar = fn (f) { f(y, fn { ... }) });
// ⏸️
```

vs something without a continuer

```temper
foo(x) {
  // Ruby-style block
}                         // <-- Semicolon inserted here
bar(y) {
  // Ruby-style-block
}
// ⏸️
```

which de-sugars to two statements

```temper
foo(x, fn { ... });
bar(y, fn { ... });
// ⏸️
```

##### Motivation
Developers of C-like languages are used to not following `}`s that end a statement with a
semicolon.

The exception is `class` definitions in C++ which, unlike Java and more recent C-like languages
do need to be followed by semicolons.

That that trips me up everytime I go back to C++ seems evidence that requiring semicolons after
statements that end with something block-like would be a burden to developers.

##### Which brackets get a semicolon

Whether a semicolon goes in around a `{` or `}` that starts or ends a
line depends on what the token on its other side can be: a prefix,
infix, or postfix operator.

Whether to insert a semicolon.

When *Position* is "Before `{`", *Semicolon* is *Y* when we should insert a semicolon because the last token can act as only the kinds of operators described by the other column.

When *Position* is "After `{`", *Semicolon*
is *Y* when we should insert a semicolon because the next token can act as only the kinds of operators described by the other column.

| Position   | Prefix | Infix&dagger; | Postfix | Semicolon   |
|------------|--------|---------------|---------|-------------|
| Before `{` | N      | N             | N       | Y           |
| Before `{` | N      | N             | Y       | Y           |
| Before `{` | N      | Y             | N       | N           |
| Before `{` | N      | Y             | Y       | Y           |
| Before `{` | Y      | N             | N       | N           |
| Before `{` | Y      | N             | Y       | Y           |
| Before `{` | Y      | Y             | N       | N           |
| Before `{` | Y      | Y             | Y       | Y           |
| After  `}` | N      | N             | N       | Y           |
| After  `}` | N      | N             | Y       | N           |
| After  `}` | N      | Y             | N       | N           |
| After  `}` | N      | Y             | Y       | N           |
| After  `}` | Y      | N             | N       | Y           |
| After  `}` | Y      | N             | Y       | Y           |
| After  `}` | Y      | Y             | N       | Y           |
| After  `}` | Y      | Y             | Y       | Y           |

&dagger; or **Separator**

In short:

```text
before `{`: semicolon = postfix || !(prefix || infix)
after `}`:  semicolon = prefix || !(infix || postfix)
```

### Syntax for *LabeledStmt*

```ebnf
LabeledStmt := LeftLabel ":" (Call | StmtBlock)
```

Declares a label and associates it as markers for the beginning and
end of a statement, so that `break`s and `continue`s within that
statement may refer to it explicitly.

Unlike TypeScript, we do not allow labeling any statement.
This allows conveying property declarations like the `p: T` in

```temper
interface I {
  p: T;
}
// ⏸️
```

to the disAmbiguate stage with that property declaration
treated as a (Call (Name ":") (Name "p") (Name "T"))

Otherwise, we would have to treat `class` bodies as a special syntactic
category to avoid ambiguity with

```temper
do {
  p: T;
}
// ⏸️
```

or the disambiguation would need to convert `T`'s from statement
context to expression context.

### Syntax for *LeftLabel*

```ebnf
LeftLabel := UnreservedWord
```

A label that that can be jumped to as by `break` and `continue`.
This is *left* in the left-hand-side sense: it is a declaration, not a use.

### Syntax for *Jump*

```ebnf
Jump := ("break" | "continue") LabelOrHole?
```

A jump to a location within the same function body that does not skip over
any necessary variable initializations.

### Syntax for *LabelOrHole*

```ebnf
LabelOrHole := Label
```

### Syntax for *Label*

```ebnf
Label := UnreservedWord
```

A label that can be jumped to as by `break` and `continue`.

### Syntax for *AwaitReturnThrowYield*

```ebnf
AwaitReturnThrowYield := ("await" | "return" | "throw" | "yield") (Expr | Garbage | ())
```

`await`, `return`, `throw`, and `yield` are operators which affect
control flow and operate on expressions.

`return(42);` for example, looks like a function call but the parentheses
are not required:

- `return;` is an application of an operator even though there
  are no parentheses.
- `return 42;` is an application of the operator to the arguments
  `(42)` even though there are no explicit parentheses.

### Syntax for *StmtBlock*

```ebnf
StmtBlock := RawBlock
```

A `{` ... `}` delimited block of statements.

## Expressions

### Syntax for *Expr*

```ebnf
Expr := Jump | AwaitReturnThrowYield | Id | SymbolLiteral | List | New | Prefix | SpecialDot | RegularDot | StringExpr | TaggedString | Call | DecoratedExpr | "(" ((Disallowed: ()) TopLevelNoGarbageNoComment | Garbage) ")" | OfExpr | Throws | Infix | Postfix | Member | Specialize | Literal | RegExp | Obj | RawBlock | "(" Garbage
```

An expression is evaluated to produce a result and/or a side effect.

### Syntax for *BooleanLiteral*

```ebnf
BooleanLiteral := "false" | "true"
```

### Syntax for *Float64Literal*

```ebnf
Float64Literal := ((whole number: [0-9]+*("_")) "." (fraction: [0-9]++("_")) (exponent: ("E" | "e") (() | "+" | "-") [0-9]++("_"))? | (whole number: [0-9]+*("_")) (exponent: ("E" | "e") (() | "+" | "-") [0-9]++("_"))) (suffix: ("D" | "d")+)? | (whole number: [0-9]+*("_")) (suffix: ("D" | "d")+)
```

### Syntax for *Call*

```ebnf
Call := CallHead "callJoin:" CallTail | CallHead
```

The call expression includes simple function calls (`f(args)`) as well as calls with
Ruby-style block functions (`f { ... }`) and flow-control like
`if (condition) { t } else { e }` because [`if` is a macro][builtin/if].

### Syntax for *New*

```ebnf
New := "new" Type "(" Args? ")"
```

### Syntax for *StringExpr*

```ebnf
StringExpr := "\u0022" (SourceCharacter - (\\n\, \\r\, \\\, \"\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "\u0022" | "`" (SourceCharacter - (\\n\, \\r\, \\\, \`\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "`" | "\" (SourceCharacter - (\\n\, \\r\, \\\, \\\\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "\" | "\u0022\u0022\u0022" (Content line starting with `"`: "LineBreak" indentation ((Ignored margin character: "\u0022" | "~") (SourceCharacter - (\\\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* | ":" StatementFragment))* "LineBreak"
```

## Block Lambdas

### Syntax for *BlockLambda*

```ebnf
BlockLambda := (Lookbehind: !BagPreceder) ("{" BlockLambdaSignatureAndSupers "=\u003e" BlockLambdaBody "}" | "{" Formals "=\u003e" BlockLambdaBody "}" | RawBlock)
```

A block lambda is a `{...}` block that specifies a function value and which
cna appear as part of a function call as below:

    someFunction { ... }

Optionally, a signature is needed to specify argument names, and may specify
the function type wholly or partially.

It may be followed by an `extends` clause that specifies marker interfaces
that are super-types for the produced function value.

The signature is followed by the double-semicolon token (`;;`) which is
distinct from two, space-separated semicolons (`; ;`).

```temper
someFunction /* <- callee */ {
  (x): ReturnType               // <- Optional signature
  extends SomeInterfaceType     // <- super types
  ;;                            // <- double semicolon separator

  body
}
// ⏸️
```

### Syntax for *BlockLambdaSignatureAndSupers*

```ebnf
BlockLambdaSignatureAndSupers := BlockLambdaSignatureAndSupers BlockLambdaSupers | BlockLambdaSignature | Garbage
```

The signature of a block lambda explains the names of arguments
visible within the body, optionally their types, and return type.

The signature also includes other interfaces that the lambda must
implement.  For example, a function that might pause execution
could use a signature line as below:

    (x: Int): Int extends GeneratorFn =>

That describes a function that takes an integer `x` and which
also is a subtype of [*GeneratorFn*](types.md#interface-generatorfn).

The `extends` clause may be left off entirely if no super-types
are desired, or multiple super-types may be specified:
`extends First & Second`.

Unlike in a function type, when a name is specified for a
block lambda argument, it is the name of the argument,
not its type.

```temper
let f: fn (Int): Void;
//         ⇧⇧⇧ word is a type

let g(myLambda: fn (Int): Void): Void { myLambda(1); }

g { (x): Void =>
  // ⇧ word is an argument name.
  // In this case, the type is inferred from g's signature.
  doSomethingWith(x + 1);
}
// ⏸️
```

### Syntax for *BlockLambdaSignature*

```ebnf
BlockLambdaSignature := "(" Formals? ")" ":" Type | "(" ("..." | Formals | ()) ")"
```

A block lambda signature line like `(x: Int): ReturnType` or just
`(x)` to take advantage of the more aggressive type inference for block
lambdas than for declared functions.

This is often followed by `;;` as it is part of
[BlockLambdaSignatureAndSupers Syntax](#syntax-for-blocklambdasignatureandsupers).

These syntactic constructs are interpreted as if preceded by
[`fn`](builtins.md#fn) but the meaning is subtly different.

- `(x: Int)` is equivalent to `fn (x: Int)` where the return type must
  later be inferrable from the calling context and the body.
- `(x)` is equivalent to `fn (x)` where the argument and return type
  must later be inferrable.
- (x): ReturnType` is equivalent to `fn (x): RT` where only argument types
  must later be inferrable.

### Syntax for *BlockLambdaSupers*

```ebnf
BlockLambdaSupers := ("extends" | "implements") Type ("," Type)* ","?
```

### Syntax for *BlockLambdaBody*

```ebnf
BlockLambdaBody := TopLevelsInSemi | TopLevel | ()
```

### Uncategorized

#### Syntax for *Arg*

```ebnf
Arg := "@" (Expr Arg | Expr "(" Arg ")") | (() | (Disallowed: (Disallowed: "let"))) (ArgNoInit "=" Expr | ArgNoInit | "..." | LetArg) | Garbage
```

#### Syntax for *ArgNoInit*

```ebnf
ArgNoInit := Pattern ":" Type | (Pattern ":" Type | Pattern) "supports" Type | Pattern
```

#### Syntax for *Args*

```ebnf
Args := (Arg ("," Arg)*)? ","? | Arg | Garbage
```

#### Syntax for *CallArgs*

```ebnf
CallArgs := "(" (ForArgs | Args | ()) ")"
```

Arguments to a function call.

A function call's arguments may be one of:

- a parenthesized, comma separated list of arguments like `(a, b, c)`.  See Args
- a parenthesized, semicolon separated list of 2 or three arguments
  with a specific purpose.  As in `(let x = 1; x < 2; ++x)` which is what the
  `for` loop macro expects.

#### Syntax for *CallHead*

```ebnf
CallHead := CalleeAndArgs BlockLambda | CalleeAndRequiredArgs
```

The function called, its arguments, and any block lambda.
This is one part of a possibly joined call.
Syntactically, `do { body } while (condition);` is two joined calls.
The first part `do { body }` is the callee `do` applied to a block as its argument.
The word `while` joins the argument list `(condition)` to that call.

#### Syntax for *CallJoiningWords*

```ebnf
CallJoiningWords := (merged to symbol: UnreservedWord+)
```

When the call continues with something like `} else if (c) {...}` we need to include
`\else_if = fn {...}` as a final named parameter to the call that receives the block
just closed, so that the called function can delegate its result to later segments.
This joins words like `else if` into the `\else_if` symbol which follows the call join
symbol.  A late parse stage pass finds those and groups everything following the joining
words into a trailing block function so that the contents of the parentheses and brackets
can match their own signature elements based on the joining words.

#### Syntax for *CallTail*

```ebnf
CallTail := (CallJoiningWords CallArgs | CallJoiningWords) BlockLambda "callJoin:" CallTail | (CallJoiningWords CallArgs | CallJoiningWords) BlockLambda | CallJoiningWords CallArgs
```

#### Syntax for *Callee*

```ebnf
Callee := Id UnreservedWord | Callee TypeArguments | Expr
```

The callee in a function application is a tad complicated.

Our OPP grammar covers many constructs that are bespoke constructs
in many languages, so `class C extends A, B { ... }` is parsed as an
application of a block lambda (later turned into a member block) like
`class(\word, C, \super, A, \super B, fn { .... })`.

This production desugars various parts into a combination of the
callee `class`, and symbol/argument pairs.

The `\word` argument is also used in constructs like function declaration
`let f<T>(arg: Type) { ... }` where the `let` macro is what is invoked to
build a named function declaration.

This production allows a callee to have:

- an expression specifying the called macro or function,
- an accompanying word,
- type parameters like `<T, U>` (whether the type parameters are actual
  parameters or formal parameters is determined by the Disambiguate stage),

#### Syntax for *CalleeAndArgs*

```ebnf
CalleeAndArgs := CalleeAndArgs ":" Type | CalleeAndArgs ("extends" | "implements") Type ("," Type)* | CalleeAndArgs "forbids" Type ("," Type)* | CalleeAndArgs "supports" Type ("," Type)* | Callee CallArgs | Callee
```

Captures low-precedence operators that may follow a parenthesized argument list.

- `: ReturnType` desugars to `\outType`, `ReturnType`.
- `extends SuperType` and `implements SuperType* desugars to `\super`, `SuperType`.

#### Syntax for *CalleeAndRequiredArgs*

```ebnf
CalleeAndRequiredArgs := CalleeAndRequiredArgs ":" Type | Callee CallArgs
```

This is like CalleeAndArgs but is used in contexts where we're not sure yet
whether this is a call.  A call requires at least one of

- Parenthesized arguments as in  `callee()`
- Semi-ed arguments       as in  `loopMacro (initialization; condition; increment)`
- A template string       as in  `callee"foo ${bar}"`
- A trailing block        as in  `callee {}`

This production succeeds is entered where we may not have a trailing block
so must have one of the others.

#### Syntax for *CommaEl*

```ebnf
CommaEl := LetBody | Expr | Garbage
```

#### Syntax for *CommaExpr*

```ebnf
CommaExpr := CommaOp | Expr
```

#### Syntax for *CommaOp*

```ebnf
CommaOp := CommaEl "," (CommaEl ("," CommaEl)* ","?)?
```

#### Syntax for *DeclDefault*

```ebnf
DeclDefault := "=" Expr
```

#### Syntax for *DeclInit*

```ebnf
DeclInit := "=" Expr
```

#### Syntax for *DeclMulti*

```ebnf
DeclMulti := "[" (LetNested "," (LetNested ("," LetNested)* ","?)? | LetNested) "]"
```

#### Syntax for *DeclMultiNamed*

```ebnf
DeclMultiNamed := "{" (LetNested ("," LetNested)* ","? | LetNested) "}"
```

#### Syntax for *DeclMultiNested*

```ebnf
DeclMultiNested := ":" DeclMultiNamed
```

#### Syntax for *DeclName*

```ebnf
DeclName := UnreservedWord
```

#### Syntax for *DeclType*

```ebnf
DeclType := ":" Type
```

#### Syntax for *DeclTypeNested*

```ebnf
DeclTypeNested := "is" Type
```

#### Syntax for *DecoratedLet*

```ebnf
DecoratedLet := DecoratedLetBody ("," LetRest)+ | DecoratedLetBody
```

#### Syntax for *DecoratedLetBody*

```ebnf
DecoratedLetBody := "@" (Expr DecoratedLetBody | Expr "(" (Disallowed: ()) DecoratedLetBody ")") | LetBody
```

#### Syntax for *DecoratedTopLevel*

```ebnf
DecoratedTopLevel := DecoratedLet | "@" (Expr TopLevelNoGarbageNoComment | Expr "(" TopLevelNoGarbageNoComment ")")
```

Decorations transform declarations and function and type definitions at compile time.

`@SomeName` followed by an optional argument list

When a `let` declaration declares multiple names, any decoration before the `let`
applies to all the names, but declarations immediately before a declared name affect
only that name.

#### Syntax for *EmbeddedComment*

```ebnf
EmbeddedComment := ()
```

Comments are not semantically significant but nor are they filtered
out entirely.

Temper tries to preserve them when translating documentation, and
they are available to backends; for example, the Python backend turns
autodoc comments before declarations into Python doc strings.

#### Syntax for *EscapeSequence*

```ebnf
EscapeSequence := "\\0" | "\\\\" | "\\/" | "\\\u0022" | "\\\" | "\\`" | "\\{" | "\\}" | "\\\u0024" | "\\b" | "\\t" | "\\n" | "\\f" | "\\r" | "\\"
```

#### Syntax for *ForArgs*

```ebnf
ForArgs := ForInit? ";" ForCond? ";" ForIncr? | ForInit? ";;" ForIncr?
```

#### Syntax for *ForCond*

```ebnf
ForCond := CommaExpr
```

#### Syntax for *ForIncr*

```ebnf
ForIncr := CommaExpr
```

#### Syntax for *ForInit*

```ebnf
ForInit := ForInitLet | CommaExpr
```

#### Syntax for *Formal*

```ebnf
Formal := "@" (Expr Arg | Expr "(" Arg ")") | (() | (Disallowed: (Disallowed: "let"))) (ArgNoInit "=" Expr | FormalNoInit | "..." | LetArg) | Garbage
```

#### Syntax for *FormalNoInit*

```ebnf
FormalNoInit := Pattern ":" Type | (Pattern ":" Type | Pattern) "supports" Type | Pattern
```

#### Syntax for *Formals*

```ebnf
Formals := (Formal ("," Formal)*)? ","? | Formal | Garbage
```

#### Syntax for *Id*

```ebnf
Id := UnreservedWord
```

#### Syntax for *Infix*

```ebnf
Infix := Expr InfixOp Type | Expr InfixOp Expr
```

#### Syntax for *InfixOp*

```ebnf
InfixOp := (Infix operators but not `callJoin:`: InfixOperators)
```

#### Syntax for *Json*

```ebnf
Json := JsonValue | Garbage
```

#### Syntax for *JsonArray*

```ebnf
JsonArray := "[" (JsonValue ("," JsonValue)* ","? | JsonValue | ()) "]"
```

#### Syntax for *JsonBoolean*

```ebnf
JsonBoolean := "false" | "true"
```

Truth values are represented using the keywords `false` and `true`.

#### Syntax for *JsonNull*

```ebnf
JsonNull := "null"
```

#### Syntax for *JsonNumber*

```ebnf
JsonNumber := "-" Number | Number
```

#### Syntax for *JsonObject*

```ebnf
JsonObject := "{" (JsonProperty ("," JsonProperty)* ","? | JsonProperty | ()) "}"
```

#### Syntax for *JsonProperty*

```ebnf
JsonProperty := JsonString ":" JsonValue
```

#### Syntax for *JsonString*

```ebnf
JsonString := "(" "\u0022" ContentChars? "\u0022" ")"
```

#### Syntax for *JsonValue*

```ebnf
JsonValue := JsonObject | JsonArray | JsonString | JsonNumber | JsonBoolean | JsonNull
```

#### Syntax for *Let*

```ebnf
Let := LetBody ("," LetRest)+ | LetBody
```

#### Syntax for *LetArg*

```ebnf
LetArg := "let" DeclName | LetArg DeclDefault | LetArg DeclType | LetArg DeclReifies
```

#### Syntax for *LetBody*

```ebnf
LetBody := "let" DeclName | "let" DeclMulti | "let" DeclMultiNamed | LetBody DeclInit | LetBody DeclType
```

#### Syntax for *LetNested*

```ebnf
LetNested := DeclName | "..." | DeclMulti | DeclMultiNamed | LetNested DeclInit | LetNested DeclMultiNested | LetNested "as" DeclName | LetNested DeclTypeNested
```

#### Syntax for *LetRest*

```ebnf
LetRest := DeclName | DeclMulti | DeclMultiNamed | LetRest DeclInit | LetRest DeclType
```

#### Syntax for *List*

```ebnf
List := "[" (Type ";" ListContent | ListContent) "]"
```

#### Syntax for *ListContent*

```ebnf
ListContent := ListElements | ListElement | Garbage | ()
```

#### Syntax for *ListElement*

```ebnf
ListElement := Spread | Expr | Garbage
```

#### Syntax for *ListElements*

```ebnf
ListElements := (ListHole | ListElement ","?)* ","?
```

#### Syntax for *ListHole*

```ebnf
ListHole := ","
```

#### Syntax for *Literal*

```ebnf
Literal := Number
```

#### Syntax for *MatchBranch*

```ebnf
MatchBranch := "else" "-\u003e" Expr | MatchCase "-\u003e" Expr | MatchCase ("," MatchCase)* ","? "-\u003e" Expr
```

Relates a match case, e.g., a pattern, to a consequence of matching that pattern.

#### Syntax for *MatchCase*

```ebnf
MatchCase := "is" Expr | "case" BalancedTokenList | Expr
```

There are two kinds of match cases: run-time type checks that use keyword `is`, and a value to match.

#### Syntax for *Member*

```ebnf
Member := Expr "[" RawCommaOp "]"
```

#### Syntax for *NoPropClass*

```ebnf
NoPropClass := ()
```

#### Syntax for *Obj*

```ebnf
Obj := (Lookbehind: BagPreceder) "{" (Props | NoPropClass) "}"
```

#### Syntax for *Pattern*

```ebnf
Pattern := "..." Expr | Expr
```

#### Syntax for *Postfix*

```ebnf
Postfix := Expr PostfixOp
```

#### Syntax for *PostfixOp*

```ebnf
PostfixOp := (Postfix operators: ":" | "++" | "--" | "!" | "?")
```

#### Syntax for *Prefix*

```ebnf
Prefix := PrefixOp Expr
```

#### Syntax for *PrefixOp*

```ebnf
PrefixOp := (Prefix operators but not `new`, `case`: PrefixOperators)
```

#### Syntax for *Prop*

```ebnf
Prop := PropName ":" Expr | PropName
```

#### Syntax for *PropClass*

```ebnf
PropClass := "class" ":" Type
```

#### Syntax for *PropName*

```ebnf
PropName := (In {...} object syntax, any `class` property must appear first: "class") | UnreservedWord
```

#### Syntax for *Props*

```ebnf
Props := (PropClass | NoPropClass Prop) ("," Prop)* ","? | PropClass | NoPropClass Prop
```

#### Syntax for *RawBlock*

```ebnf
RawBlock := (Lookbehind: !BagPreceder) "{" TopLevels "}"
```

#### Syntax for *RawCommaOp*

```ebnf
RawCommaOp := CommaEl ("," CommaEl)* ","? | CommaEl
```

#### Syntax for *RegExp*

```ebnf
RegExp := (Disallowed: (Disallowed: "/")) RegExp
```

#### Syntax for *RegularDot*

```ebnf
RegularDot := Expr ("." | "?.") SymbolValue?
```

#### Syntax for *ReservedWord*

```ebnf
ReservedWord := "nym" | "builtins" | "this" | "super"
```

#### Syntax for *SpecialDot*

```ebnf
SpecialDot := "builtins" "." BuiltinName | "this" | (Lookahead: "super") Garbage
```

#### Syntax for *Specialize*

```ebnf
Specialize := Expr "\u003c" RawCommaOp "\u003e"
```

#### Syntax for *Spread*

```ebnf
Spread := "..." Expr
```

#### Syntax for *StringGroup*

```ebnf
StringGroup := "\u0022" (SourceCharacter - (\\n\, \\r\, \\\, \"\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "\u0022" | "`" (SourceCharacter - (\\n\, \\r\, \\\, \`\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "`" | "\" (SourceCharacter - (\\n\, \\r\, \\\, \\\\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "\" | "\u0022\u0022\u0022" (Content line starting with `"`: "LineBreak" indentation ((Ignored margin character: "\u0022" | "~") (SourceCharacter - (\\\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* | ":" StatementFragment))* "LineBreak"
```

#### Syntax for *StringGroupTagged*

```ebnf
StringGroupTagged := (Whether escape sequences are expanded is up to the tag: "\u0022" (SourceCharacter - (\\n\, \\r\, \\\, \"\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "\u0022" | "`" (SourceCharacter - (\\n\, \\r\, \\\, \`\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "`" | "\" (SourceCharacter - (\\n\, \\r\, \\\, \\\\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "\" | "\u0022\u0022\u0022" (Content line starting with `"`: "LineBreak" indentation ((Ignored margin character: "\u0022" | "~") (SourceCharacter - (\\\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* | ":" StatementFragment))* "LineBreak")
```

#### Syntax for *StringHole*

```ebnf
StringHole := "\u0024{" (CommaExpr | Garbage) "}"
```

##### Syntax for *StringPart*

```ebnf
StringPart := ContentChars+ | StringHole | UnicodeRun
```

#### String interpolation

Strings may contain embedded expressions.  When a
string contains a `${` followed by an expression,
followed by a `}`, the resulting string value
is the concatenation of the content before,
content from the expression, and the content after.

```temper
"foo ${"bar"} baz"
== "foo bar baz"
// ✅
```

Interpolated values that aren't *String* instances
have `.toString()` called on them automatically,
which is convenient for types that define *toString*,
such as *Int*.

```temper
let two = 2;
"one ${two} three"
== "one 2 three"
// ✅
```

An empty interpolation contributes no characters,
which means it may be used to embed meta-characters.

```temper
"$${}{}" == "\$\{\}"
// ✅
```

(This mostly comes in handy with tagged strings to
give fine-grained control over what the tag receives.)

Empty interpolations can also be used to wrap a long
string across multiple lines, but using a multi-quoted
string with the tilde (`~`) margin character can be
easier.

```temper
"A very long string ${
  // Breaking this string across multiple lines.
}that runs on and on"
== "A very long string that runs on and on"
// ✅
```

Empty interpolations also let you include spaces at
the end of a line in a multi-quoted string.

```temper
"""
"Line 1
~Line 2 ${}
== "Line 1\nLine 2 "
// ✅
```

In that multi-quoted string, the margin characters at the left (`"` and `~`)
control newlines.  Line feeds (LF U+A) are added at the end of lines that have
a double-quote character (`"`) in the margin, but not at the end of lines that
have a tilde character (`~`) in the margin.

See [Multi-quoted strings](types.md#multi-quoted-strings) for more details on that syntax.

#### Syntax for *StringPartRaw*

```ebnf
StringPartRaw := "raw string"+ | StringHoleRaw | UnicodeRunRaw
```

Parallels [ProductionNames.StringPart] but emits a [ValuePart] instead
of routing a string token to [lang.temper.lexer.unpackQuotedString] so
that the tag expression gets string content without escape sequences
decoded.

#### Syntax for *SymbolLiteral*

```ebnf
SymbolLiteral := "\\" SymbolValue
```

#### Syntax for *SymbolValue*

```ebnf
SymbolValue := Word
```

#### Syntax for *TopLevelNoGarbageNoComment*

```ebnf
TopLevelNoGarbageNoComment := DecoratedTopLevel | Let | Stmt
```

#### Syntax for *TopLevelsInSemi*

```ebnf
TopLevelsInSemi := (Nop* TopLevel)* TrailingSemi
```

Top levels in the context of a larger semicolon-separated run.

#### Syntax for *Type*

```ebnf
Type := Expr
```

#### Syntax for *TypeArgument*

```ebnf
TypeArgument := TypeArgumentName
```

#### Syntax for *TypeArgumentName*

```ebnf
TypeArgumentName := TypeArgumentName ("extends" | "implements") Type ("," Type)* | TypeArgumentName "forbids" Type ("," Type)* | TypeArgumentName "supports" Type ("," Type)* | "@" (Expr TypeArgumentName | Expr "(" TypeArgumentName ")") | Expr
```

#### Syntax for *TypeArguments*

```ebnf
TypeArguments := "\u003c" (TypeArgument ("," TypeArgument)* | TypeArgument) "\u003e"
```
