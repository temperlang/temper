# So you want to write a Temper backend

## Notice

Backends in the official Temper repository use the same licensing as Temper
itself. If you want to make a backend with different licensing, you'll need to
store the code separately from official Temper. We provide information later on
how to support backends dynamically with the `temper` cli, without maintaining a
full fork of Temper.

## Overview

Each language that Temper translates to requires a backend.

It's the Temper compiler's job to process Temper source files and turn them into
a form that can be easily translated into code artifacts in many target
languages.

The Temper compiler does global analysis so that backends can produce faithful
translations via local analysis.

One compiler frontend supports many *backend*s, plugins that know the output
language.

This document briefly outlines the translation pipeline and then provides a
step-by-step guide to writing a backend for a previously unsupported
*target language*.

There is a partial glossary at the end.

## Pre-requisites

This document assumes a working knowledge of [*Kotlin*](https://kotlinlang.org),
the language that Temper's compiler is written in, and of *Temper* the language
that is translated.

A working knowedge of tree representations, ASTs & CSTs, of programs helps.

## Pick a backend ID

Each backend needs a unique identifier.

When the compiler is run at the command line, a backend ID is used to pick the
right backends to use.  For example, `$ temper build -b java -b py ...` will
build Java and Python translations.

A backend ID must be a valid identifier: roughly a letter followed by letters,
digits, or underscores.  By convention, backend IDs use only lower-case letters.

A backend ID may not be shared by two or more backends.  Try to avoid ambiguity
and conflicts.

Clarity and meaningfulness to the target language community should trump any
other naming suggestions, but below are some guidelines.

Good choices for a backend ID include:

- the language name if it is short, and has no non-identifier characters ("c#"
  includes a '#' so is not allowed)
- the main file extension for language source files if it's unambiguous (`ml` is
  used for OCaml files but also for other languages in the larger ML family)
- anything else that a user of the language would recognize and which is
  unambiguous ("csharp" for C#)

----

A backend is allowed to have multiple backend IDs.

For example, the Java backend has two variants:

- "java" is the main backend which supports "modern" java: JDK 17+
- "java8" suports "legacy" java: JDK 8+

Though there are two distinct backends, almost all the code and definitions are
shared between the two.  Just `java8` avoids using parts of the Java standard
library that may not be available in a Java 8 runtime.

If your backend supports multiple backend IDs, it's recommended that the default
ID be shorter and simpler than the others unless there is no meaningful default.

## Create a Kotlin source directory

Under the Temper compiler source root are a number of directories like `be-...`
which each define one backend. By convention, the `...` is a backend ID.

```sh
🐚$ ls | egrep '^be-'
be-csharp
be-java
be-js
be-lua
be-py
be-rust
```

To get started, it might be easiest to find an existing backend for a "similar"
language, copy it, and adapt it.

If one of the existing languages is closely related to your target language, you
can copy that which would help you get started.

If your language is a dynamic language, like JavaScript or Python, one of those
might serve as a template.

If your language is a statically typed, object oriented language that allows at
most one public class per source file, leaning on the existing Java backend
might simplify tasks.

### External modules

Additional repositories can be placed using Git submodules under an "external/"
directory under the top "temper/" project dir. And then additional Gradle
modules can be under these directories. For example:

- ...
- be-csharp/
- be-data/
- ...
- external/
  - my-other-project/
    - be-mylang/
      - build.gradle
      - ...
    - be-myotherlang/
      - build.gradle
      - ...
  - my-yet-other-project/
- ...
- settings.gradle

The top-level settings.gradle file automatically includes subprojects fitting
this convention to the multi-project build. Then
`supported-backends/build.gradle` generates a "plugin-list.json" build artifact
that the `temper` application loads at runtime. Alternatively, if environment
variable `$TEMPER_PLUGINS` is defined, it has priority and is expected to
contain a JSON-formatted list of strings given class names of backend factories.

## Stub out a subclass of `class Backend` for your backend

**NOTE**: The rest of this document will assume your target language is
*NewLang*, that your backend ID is "newlang" and will use names like
*NewLangSomeSuffix* for Kotlin classes you might use.

The abstract [Kotlin class *Backend*](https://github.com/temperlang/temper-prepublic/blob/e39be5d6268a9727b528d89d7094431739632170/be/src/commonMain/kotlin/lang/temper/be/Backend.kt#L4)
is what plugs into the translation toolchain.

As noted above, the easiest way to get started is to use an existing backend as
a template.

Create a Kotlin class or shamelessly copy your template and rename things to
*NewLang*:

```kotlin
/**
 * <!-- snippet: backend/newlang -->
 * # NewLang Backend
 * 
 * Translates Temper to NewLang
 * ...
 *
 * ## Pre-requisites
 * NewLang version 1.2.3
 */
public class NewLangBackend private constructor(
  libraryConfigurations: LibraryConfigurations,
  modules: List<Module>,
  buildFileCreator: AsyncSystemAccess,
  persistFileUpdater: AsyncSystemReadAccess,
  logSink: LogSink,
  dependencyResolver: DependencyResolver,
  config: Config,
) : Backend<NewLangBackend>(
  backendId = backendId,
  libraryConfigurations = libraryConfigurations,
  modules = modules,
  dependencyResolver = dependencyResolver,
  buildFileCreator = buildFileCreator,
  persistFileUpdater = persistFileUpdater,
  logSink = logSink,
  config = config,
) {
  companion object : Factory<NewLangBackend> {
    override val backendId = BackendId(uniqueId = "newlang")

    /** The default file extension for output files. */
    const val EXTENSION = ".newlang"

    // ...
  }
}
```

The [*JSBackend* class](https://github.com/temperlang/temper-prepublic/blob/e39be5d6268a9727b528d89d7094431739632170/be-js/src/commonMain/kotlin/lang/temper/be/js/JsBackend.kt)
is instructive as an example.

It starts with a big comment that:

- makes clear the target language
- notes specific translation choices
- notes supported verstions of the target language
- explains compatibility committments, in this case support for TypeScript (a
  related language) type notations

That comment ends up in the [reference documentation](https://temperlang.github.io/tld/reference/target-languages/#javascript-backend),
and yours should too once it stabilizes.

## Write an out-grammar

An \*.out-grammar file defines Kotlin classes: one for each kind of output tree
node, and details on how to "un parse" them back into source code. See
[out-grammar reference](out-grammar.md) for more details.

This lets translators focus on turning one kind of language tree into another by
simplifying a number of translation problems:

- Translators can ignore details like how to indent blocks and parenthesize
  arithmetic expressions. See precendence below.
- Trees carry position information allowing co-generation of both translated
  sources but also debug metadata that relates positions in the translation to
  positions in the Temper source.

For example, the [JavaScript out-grammar file](https://github.com/temperlang/temper-prepublic/blob/e39be5d6268a9727b528d89d7094431739632170/be-js/src/commonMain/kotlin/lang/temper/be/js/js.out-grammar#L4)
defines *ConditionalExpression* thus:

```
ConditionalExpression ::=
      test%Expression
    & "?" & consequent%Expression
    & ":" & alternate%Expression;
```

That says, to turn a *ConditionalExpression* (JavaScript's [ternary operator](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Conditional_operator))
into JavaScript source code:

1. first write out the *test* expression,
2. then write a qustion mark (`?`) token,
3. followed by the *consequent* expression,
4. a colon (`:`) token, and 
5. finally the *alternate* expression.

Later declarations in that same file control when parentheses are inserted
around sub-expressions.

Then Kotlin code can use the generated Kotlin classes to produce trees.  The
below comes from a [formatting test](https://github.com/temperlang/temper-prepublic/blob/e39be5d6268a9727b528d89d7094431739632170/be-js/src/commonTest/kotlin/lang/temper/be/js/JsTreeRenderTest.kt#L441)
that builds a conditional expression using code generated from that out-grammar
declaration and others.

```kt
expectedJson = """
    {
      js: // The left of the bracket is parenthesized, but the right is not.
      ```
      (a ? b: c)[d + e]

      ```
    }
    """,

...

Js.MemberExpression(
    pos,
    Js.ConditionalExpression(
        pos,
        makeJsIdentifier(pos, "a", null),
        makeJsIdentifier(pos, "b", null),
        makeJsIdentifier(pos, "c", null),
    ),
    Js.BinaryExpression(
        pos,
        makeJsIdentifier(pos, "d", null),
        Js.Operator(pos, "+"),
        makeJsIdentifier(pos, "e", null),
    ),
    computed = true,
    optional = false,
)
```

To update Kotlin classes when you change an out-grammar file, just run:

```sh
gradle kcodegen:u
```

When writing an out-grammar, start small.
You will only need syntactic constructs that you plan to use.

The grammar needed to generate syntax can be much smaller and simpler than that
used to parse a language.

For example, Java has multiple syntaxes for array types because for a few years
in the mid 1990's it was seen as helpful to attract C++ programmers to the
language.

```java
// Normal syntax
int[][] myTwoDimensionalArray;
// Vestigial syntax
int[] myTwoDimensionalArray[];
int myTwoDimensionalArray[][];
```

You can probably start with just enough to call your language's *print* function
with a string literal; see *getting to Hello World* below.

```
// Starting small.  A program is just one expression.
Program ::= expr%Expr;

// An expression is a function call or a string
Expr ::= CallExpression | StringLiteral;

// *"," after args%Expr means if there are multiple arguments they are separated by commas
CallExpression ::=
    callee%Expr & "(" & args%Expr*"," & ")" & ";";

StringLiteral(content%`String`);
// Calls out to Kotlin stringTokenText function to quote and escape the string content
StringLiteral.renderTo =
`tokenSink.emit(
    OutputToken(
        stringTokenText(content),
        OutputTokenType.QuotedValue,
    ),
)
`;
```

## Out grammar reference documentation

Out grammar files include a mix of definitions and declarative instructions for
formatting.

The [TmpL out-grammar](https://github.com/temperlang/temper-prepublic/blob/e39be5d6268a9727b528d89d7094431739632170/be/src/commonMain/kotlin/lang/temper/be/tmpl/tmpL.out-grammar#L4)
is a good reference.  It defines the intermediate language trees, layered Temper
(TmpL) trees, which are what most backends process to produce their target
language's output trees.

[Out grammar file format by example](https://github.com/temperlang/temper-prepublic/blob/e39be5d6268a9727b528d89d7094431739632170/kcodegen/src/commonMain/kotlin/lang/temper/kcodegen/outgrammar/README.md#output-grammar-file-format-by-example)
explains the ins and outs of the file format.

In brief, alternation definitions like the below correspond to a Kotlin
interface.

```temper inert
Expression =
    Reference
  | CallExpression
  | LiteralExpression;
```

That says there are three variants of the `sealed interface Expression` and
lists them.  Each of those mentioned types will implement
`interface Expression`.

Grammar definitions tell a type how to format itself.

```temper inert
VariableDeclaration ::=
  "let" & name%Identifier & ":" & type%Type ";";
```

That defines a class (unless it has its own sub-types) that contains an
identifier and a type and knows how to format itself.  `&` means concatenation;
`%` goes between a property name and a property type.

## Make some trees and write some tests

As mentioned above, once you've got the starts of an out grammar you can start
writing tests like *JsTreeRenderTest* that check the output

Common corner cases include:

- parenthesization of nested sub-expressions: `a + b * c` is different from
  `(a + b) * c`
- required spaces: In JavaScript, `1.toString()` is illegal, but `1 .toString()`
  is fine
- banned spaces: if your target language inserts tokens at line breaks, be
  careful where lines break

See *FormattingHints* for more details about how to control when and where
spaces and newlines are inserted to format code.

See *FormattableTree.OperatorDefinition* to see how parentheses are inserted and
how to control that from within your out-grammar file based on precedence and
associativity tables.

## Kinds of tests

When developing a backend, it makes sense to translate only programs with simple
semantics first, and then work towards the more complicated and involved.

Often, when working on getting complex semantics translating correctly, one
finds that one wants a particular kind of expression translated to specific
source code, so in the process of getting a functional test working, one might
build up a suite of tests for translation of tricky sub-expressions.

Existing backends use a variety of kinds of tests.  Not all backends need to
maintain each test suite, but below is a list of the kinds that have proven
useful.

| Kind of test | What does it test | How | Likely to break when | Problems it catches |
| ---- | ---- | ---- | ---- | ---- |
| Render Test | How output trees convert to code | Produce a tree, assert its string form | The out-grammar file changes extensively | Precedence, missing spaces between tokens, and token insertion problems like ASI |
| Backend Test | The translation of carefully chosen, small Temper programs | Comparing translator output to expected output | Translator changes the trees it produces | Translation corner cases, naming conflicts |
| Functional Test | Semantics of a suite of carefully chosen Temper programs curated by the Temper core team | Comparing standard output from compiling and running the program using the target language toolchain | The way programs are compiled and launched breaks or the target language has breaking changes | Mis-translations |
| Connection tests | That hand-written code in the target language can connect to a translated Temper library and get the right results | By writing tests in the target language that depend on translated Temper libraries | The backend changes the promises it makes regarding API translation | Backwards compatibility problems and poor stability of translated APIs |

When developing a new backend, it's best to start small, with the earlier kind
of tests, and aggressively pull lessons learned on the latter kinds into new
test cases on the earlier ones.

## Add a row to the functional-test-matrix

The [functional test matrix](https://github.com/temperlang/temper-prepublic/blob/e39be5d6268a9727b528d89d7094431739632170/functional-test-matrix.md)
is a grid with backends along the top, and tests written in Temper along the
bottom.

You're going to need to insert a row into that table, and initially indicate
that you expect all tests to fail.

The [testedBackends list](https://github.com/temperlang/temper-prepublic/blob/e39be5d6268a9727b528d89d7094431739632170/functional-test-suite/src/commonMain/kotlin/lang/temper/tests/QuickTests.kt#L116-L117)
controls the columns in that table.

Add an *onlyPasses* entry to the [functional test status expectations](https://github.com/temperlang/temper-prepublic/blob/e39be5d6268a9727b528d89d7094431739632170/functional-test-suite/src/commonMain/kotlin/lang/temper/tests/FunctionalTestStatus.kt#L8-L38).

That provides the column values.  Since getting functional tests passing is an
involved process, we don't reject builds of the compiler because some backends
don't pass.  We focus on avoiding regressions.  Mature backends have empty
failure expectations except where a language feature is still under development.

## Get the "Hello, World!" functional test running by any means necessary

Initially, your entire language's row in the matrix is red crosses: ❌.  The
algos-hello-world test simply prints "Hello, World!", so it's a great first box
to get to :heavy_check_mark:.

There is a certain amount of scaffolding to implement to get there:

### Producing a bogus translation

The first step in producing a real translator would be to first write a bogus
translator: a simple translator that, no matter what Temper trees it gets,
outputs your target language's hello world program by building an out-grammar
tree.

*NewLangBackend.tentativeTmpL()* should invoke *TmpLTranslator* to produce a
module set.

*NewLangBackend.translate()* should create a tree.  Eventually it will delegate
to a *NewLangTranslator*

*Specifics* are classes that explain how to compile and run a generated program.
For example, the *JavaBackend* produces *JavaSpecific*s which can run `maven` to
compile and run a translated program.

The *JavaFunctionalTest* uses that to run the Java backend's translation of the
AlgoHelloWorld functional test and compares its output, "Hello, World!" to the
expected output.

If *NewLangSpecifics* needs tools installed locally to run, talk to Ben and see
if we can add the required compiler and runtime dependencies to the docker
image.

Finally, create a *NewLangFunctionalTest* class under
`be-newlang/src/commonTest/kotlin/lang/temper/be/newlang/` that inherits from
*class FunctionalTestRunner* to actually make the test suite run as part of
`gradle check`.

With that done, you should have enough scaffolding to get one functional test
running correctly.

## Writing a translator

As mentioned earlier, the Temper frontend does the global analysis to produce a
*TmpL* tree form that should be translatable to many target languages via mostly
local analysis.

Existing backends have their own *Backend* subclass which delegates most of the
tree walking and tree building to a *Translator* class: *JsTranslator* or
*JavaTranslator* for example.

Typically, the first thing to do is to write a *Translator* that accepts a
*TmpL.Module* from the backend and generates a top-level production defined in
your out-grammar.

Translating *Blocks* and calls to `console.log` is a good start.

Kotlin has a [TODO(\"\$tree\")](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-t-o-d-o.html)
function that lets you stub out translation paths you're not ready to implement
yet.

## Lifecycle of a Backend

TODO: Talk about PreAnalysis and metadata, getting your ducks in a row before
translation, and cross-library dependencies. There are comments for each of the
lifecycle methods. Maybe turn those into snippets and have a grouping snippet
that embeds them each.

## Support Networks connect Temper builtins to support code

TODO: Flesh out SupportNetwork comments and embed them strategically to produce
docs on SupportNetwork and what each piece does.

Note that using code from or referencing an existing backend might simplify the
effort here and in other aspects of writing a backend.

## Iterating on functional tests

Fleshing out your *NewLangTranslator* takes time.  Make use of Kotlin `when`
clauses where possible.  The *TmpL* trees that you get as inputs make extensive
use of `sealed interface`s meaning if you need to translate an expression for
`fun translateExpression(expr: TmpL.Expression)` you can write `when (expr) {}`
and the JetBrains IDE "quick fix" command will happily add all the variants for
`expr` to the body.

If you leave everything as *TODO()* above, you can pick a functional test and
see what breaks and fill out translation paths as needed.

Here's an order of functional tests based on an ordre that worked for a recent
backend effort. However, new functional tests were added since effort, so the
new tests have been inserted into this list based on estimated fit.

1. AlgosHelloWorld
1. AlgosFibonacci, TypesIntBasics
1. AlgosHelloFromClassToTop, AlgosHelloWorldObject
1. ClassesCallOverrideFromSubtype, ClassesDirectGetter,
   ClassesInheritedGetter, InterfacesPureVirtual
1. ControlFlowIfReturn, ControlFlowLoopReenterable, ControlFlowLoops
1. ControlFlowBubble
1. CastsAsExpr
1. FunctionsSimpleLocals, SemanticsMutuallyReferencingTypes
1. SemanticsTypeCheckedLocals (typically unsupported for static typing)
1. SemanticsConstness
1. ClassesAngleCall
1. TypesListEmpty, TypesListOperations
1. CastsSpecific, ClassesObjectLiterals, ClassesPrivateMethod,
   ClassesPropertyOrder, ClassesSetters, ImportsTypes,
   InterfacesEmpty, RegressionMinimalRepro, TypesListReduce,
   TypesListSorting
1. InterfacesPropertyMembers
1. FunctionsDefaulting, FunctionsLocals, FunctionsNamedArgs,
   FunctionsRestFormal, TypesStringIsEmpty
1. FunctionsAsValues
1. TypesStringIndices, TypesStringRead
1. TypesStringBuild
1. TypesIntLimits, TypesIntShifty
1. TypesFloatBasics, TypesFloatOps
1. ImportsFunctions, ImportsValues
1. NamesNonascii
1. FunctionsConstructorCallbacks
1. AlgosMyersDiff, SemanticsBroken, TypesDenseBitVector, TypesDeque
1. TypesMap
1. ClassesStaticProperties, ClassesStaticPropertiesScope
1. ControlFlowActorRun, ControlFlowAsync
1. TestingAsserts (here down requiring `std`)
1. TypesDate
1. TypesJsonSyntaxTree
1. TypesNetresponse
1. RegexMatch, RegexZeroAdvance

Anyone following this list might recommend updates based on their experience.
Best order might also depend on backend language.

## Glossary

*backend*: a plugin into the Temper toolchain that embeds knowledge about a
specific target language.  For example, Temper's JavaScript backend is
responsible for converting Temper syntax trees into JS.

*backend test*: tests relating Temper inputs to target language translations.

connections

grammar / formatting test

functional test

output grammar

support code

*target language*: a programming language that's a target of translation, such
as performed by a Temper backend.
