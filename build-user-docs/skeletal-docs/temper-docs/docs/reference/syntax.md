---
title: Syntax Reference
---

# Syntax Reference

This document helps understand how Temper code is parsed.  Readers
can use `temper repl` to get feedback on how the language interprets
a piece of code.  Especially useful is the *describe* REPL command
which lets you view a snapshot of the compilation state at various
processing stages.

Temper's syntax should be familiar to users of &ldquo;C-like&rdquo;
languages: languages that use `{...}` around blocks and semicolons
(`;`) separate computational steps.  It is most similar to TypeScript;
types follow names (`name: Type`) with a colon in between.
But its syntax is distinct from JS/TS in details.

Some diffences include:

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

<!-- TODO(mvs): what are we missing from this list?  Regex syntax -->

The grammar below explains the main syntactic categories.
It's meant to be advisory, to help learners discover features by following
grammatical threads.

It is not an exact grammar.  Temper has a three-stage parse: lexical
analysis, operator precedence grouping, tree building.  This grammar
is derived from the tree builder which operates on a stream of tokens
*after* an operator precedence parser has inserted synthetic
parentheses into the token stream and *after* some other token level
rewriting operations.

<!-- TODO(mvs): when I've got internet connectivity, link terms like lexical analysis and friends -->

Since Temper is a macro language, some language features that would
have separate syntactic paths in a non-macro language are instead
implemented as macros; they parse as regular function calls, but those
functions are macros that apply at a leter compilation-stage.  For example,
`if` is a macro so there is no dedicated syntax for `if` statements below.


## Structure of a file

<!-- TODO(mvs): explain high-level that parsing of a file happens in the context of an embedding language like Markdown, and that `;;;` is a token -->

⎀ syntax/Root

⎀ syntax/TopLevels

⎀ syntax/TopLevel

⎀ syntax/Garbage

⎀ syntax/TopLevelNoGarbage

⎀ syntax/TrailingSemi

## Statements

⎀ syntax/Stmt

⎀ syntax/Nop

⎀ syntax/LabeledStmt

⎀ syntax/LeftLabel

⎀ syntax/Jump

⎀ syntax/LabelOrHole

⎀ syntax/Label

⎀ syntax/AwaitReturnThrowYield

⎀ syntax/StmtBlock

## Expressions

⎀ syntax/Expr

⎀ syntax/BooleanLiteral

⎀ syntax/Float64Literal

⎀ syntax/Call

⎀ syntax/New

⎀ syntax/StringLiteral

## Block Lambdas

⎀ syntax/BlockLambda

⎀ syntax/BlockLambdaSignatureAndSupers

⎀ syntax/BlockLambdaSignature

⎀ syntax/BlockLambdaSupers

⎀ syntax/BlockLambdaBody

### Uncategorized

⎀ syntax/Arg

⎀ syntax/ArgNoInit

⎀ syntax/Args

⎀ syntax/CallArgs

⎀ syntax/CallHead

⎀ syntax/CallJoiningWords

⎀ syntax/CallTail

⎀ syntax/Callee

⎀ syntax/CalleeAndArgs

⎀ syntax/CalleeAndRequiredArgs

⎀ syntax/CommaEl

⎀ syntax/CommaExpr

⎀ syntax/CommaOp

⎀ syntax/DeclDefault

⎀ syntax/DeclInit

⎀ syntax/DeclMulti

⎀ syntax/DeclMultiNamed

⎀ syntax/DeclMultiNested

⎀ syntax/DeclName

⎀ syntax/DeclType

⎀ syntax/DeclTypeNested

⎀ syntax/DecoratedLet

⎀ syntax/DecoratedLetBody

⎀ syntax/DecoratedTopLevel

⎀ syntax/EmbeddedComment

⎀ syntax/EscapeSequence

⎀ syntax/ForArgs

⎀ syntax/ForCond

⎀ syntax/ForIncr

⎀ syntax/ForInit

⎀ syntax/Formal

⎀ syntax/FormalNoInit

⎀ syntax/Formals

⎀ syntax/Id

⎀ syntax/Infix

⎀ syntax/InfixOp

⎀ syntax/Json

⎀ syntax/JsonArray

⎀ syntax/JsonBoolean

⎀ syntax/JsonNull

⎀ syntax/JsonNumber

⎀ syntax/JsonObject

⎀ syntax/JsonProperty

⎀ syntax/JsonString

⎀ syntax/JsonValue

⎀ syntax/Let

⎀ syntax/LetArg

⎀ syntax/LetBody

⎀ syntax/LetNested

⎀ syntax/LetRest

⎀ syntax/List

⎀ syntax/ListContent

⎀ syntax/ListElement

⎀ syntax/ListElements

⎀ syntax/ListHole

⎀ syntax/Literal

⎀ syntax/MatchBranch

⎀ syntax/MatchCase

⎀ syntax/Member

⎀ syntax/NoPropClass

⎀ syntax/Obj

⎀ syntax/Pattern

⎀ syntax/Postfix

⎀ syntax/PostfixOp

⎀ syntax/Prefix

⎀ syntax/PrefixOp

⎀ syntax/Prop

⎀ syntax/PropClass

⎀ syntax/PropName

⎀ syntax/Props

⎀ syntax/QuasiAst

⎀ syntax/QuasiHole

⎀ syntax/QuasiInner

⎀ syntax/QuasiLeaf

⎀ syntax/QuasiTree

⎀ syntax/Quasis

⎀ syntax/RawBlock

⎀ syntax/RawCommaOp

⎀ syntax/RegExp

⎀ syntax/RegularDot

⎀ syntax/ReservedWord

⎀ syntax/SpecialDot

⎀ syntax/Specialize

⎀ syntax/Spread

⎀ syntax/StringGroup

⎀ syntax/StringGroupTagged

⎀ syntax/StringHole

⎀ syntax/StringPart

⎀ syntax/StringPartRaw

⎀ syntax/SymbolLiteral

⎀ syntax/SymbolValue

⎀ syntax/TopLevelNoGarbageNoComment

⎀ syntax/TopLevelsInSemi

⎀ syntax/Type

⎀ syntax/TypeArgument

⎀ syntax/TypeArgumentName

⎀ syntax/TypeArguments
