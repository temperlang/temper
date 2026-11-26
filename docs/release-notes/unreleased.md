# Unreleased

- [🚨Breaking change: Rest parameter syntax removed.](#breaking-change-rest-parameter-syntax-removed)
- [Auto operator implementation](#auto-operator-implementation)
- [Rust resumes every block awaiting a promise](#rust-resumes-every-block-awaiting-a-promise)
- [Decorators for immutable types](#decorators-for-immutable-types)
- [🚨Breaking change: type parameter variance now requires `@`.](#breaking-change-type-parameter-variance-now-requires-)
- [🚨Breaking change: Multiline string syntax (yes, again)](#breaking-change-multiline-string-syntax-yes-again)
- [🆕 Bitwise operators](#-bitwise-operators)
- [🚨Breaking change: Details of rules for `<` ambiguity have changed](#breaking-change-details-of-rules-for--ambiguity-have-changed)
- [🚨Breaking change: Python parameters keep builtins' names](#breaking-change-python-parameters-keep-builtins-names)
- [🚨Breaking change: `Deque` and `DenseBitVector` are std's `Queue` and `BitVector`](#breaking-change-deque-and-densebitvector-are-stds-queue-and-bitvector)
- [Lists and strings out of bounds panic everywhere](#lists-and-strings-out-of-bounds-panic-everywhere)
- [Explicit `null` for a parameter with a default gives the default](#explicit-null-for-a-parameter-with-a-default-gives-the-default)
- [🚨Breaking change: private constructors, connected types, and member names](#breaking-change-private-constructors-connected-types-and-member-names)
- [`Console`, `getConsole`, and `Equatable` are nameable again](#console-getconsole-and-equatable-are-nameable-again)
- [🚨Breaking change: no `Null` type](#breaking-change-no-null-type)
- [🆕 Module namespaces](#-module-namespaces)
- [🚨Breaking change: `builtins.X` removed](#breaking-change-builtinsx-removed)
- [🚨Breaking change: `Void` is never a value; type arguments fit their bounds](#breaking-change-void-is-never-a-value-type-arguments-fit-their-bounds)
- [Rust crates depend on `temper-std` only when they use it](#rust-crates-depend-on-temper-std-only-when-they-use-it)
- [`++ls[i]` gives the new value](#lsi-gives-the-new-value)
- [Unassigned locals in `for … of` bodies are errors](#unassigned-locals-in-for--of-bodies-are-errors)
- [`@json` adapters](#json-adapters)
- [A class has only one constructor](#a-class-has-only-one-constructor)
- [Extension methods](#extension-methods)
- [Generated packages need the matching runtimes](#generated-packages-need-the-matching-runtimes)
- [Fixes for code Temper 0.6.0 accepted](#fixes-for-code-temper-060-accepted)
- [Classes' `==`](#classes-)
- [Lua: `Int64` on LuaJIT and Lua 5.1](#lua-int64-on-luajit-and-lua-51)
- [C++: `Float64.toString` as on the JVM](#c-float64tostring-as-on-the-jvm)
- [`temper` and its `TEMPER_*` settings](#temper-and-its-temper_-settings)
- [Fixes on Windows](#fixes-on-windows)

## 🚨Breaking change: `Deque` and `DenseBitVector` are std's `Queue` and `BitVector`

Core's `Deque` and `DenseBitVector` moved to a new std module,
`std/collections`, as `Queue` (it's first-in, first-out) and
`BitVector`. Code that used them adds one import:

```temper inert
let { Queue, BitVector } = import("std/collections");
```

Without it, `Deque` or `Queue` is an error that names that import. Both
are `@connected` to the same host types as before (`java.util.Deque`
and `java.util.BitSet` in Java, `collections.deque` in Python, and so
on); the runtimes' own types are renamed to match (`temper_core.BitVector`
in Python, `temper_core::Queue` in Rust, `temper.Queue` in Lua). Every
backend now agrees on the edges, as std's Temper body has them: `get` at
a negative index is `false`, `set` at one panics (setting `false` too),
`removeFirst` on an empty queue panics, as reading a list out of bounds
does, with the same message everywhere, and a negative capacity is none,
where each backend differed somewhere before (some threw a host
exception, some bubbled); and JavaScript's `set` far past the end no
longer drops the bit. See [`std/collections`](../std/collections.md).

## Lists and strings out of bounds panic everywhere

Reading or setting a list out of bounds (`ls[i]`, `set`, `add` at an
index), `removeLast` on an empty builder, `reduce` on an empty list, and
reading a string at or past its end panic on every backend, as core's
docs say, with the same message, "index 3 out of bounds for length 3".
Before, most backends raised a host exception or bubbled, which
`orelse` could catch, and JavaScript and Lua let `set` at a negative
index pass silently.

## Explicit `null` for a parameter with a default gives the default

As it always has, passing `null` for a parameter with a default, as
`f(null)` for `let f(x: Int? = 0)`, gives the default, `0`, even where
the parameter's type is nullable. It's now documented, under
[optional parameters](../language/builtins.md#optional-parameters).

## 🚨Breaking change: private constructors, connected types, and member names

- A class's private constructor is its own, as private methods are:
  `new C()` outside `C` is an error ("C is not a concrete type with a
  public constructor"), through an alias or object literal too. Make
  such values in a static method of the class instead.
- `DoneResult`'s constructor is private; `doneResult()` makes one.
- `MapKey` is sealed: only core's `Int32` and `String` are map keys.
- `is` and `as` may target a `@connected` type, core's or yours, only
  from a sealed supertype that tells its subtypes apart, as `r is
  DoneResult<T>` for `r: GeneratorResult<T>`. `x is StringIndex` or `x is
  StringBuilder` for `x: AnyValue` is an error, as in Kotlin Temper; JS
  translated it wrongly.
- `Pair`, `Listed`, `Mapped`, `SafeGenerator`, and `Console` (and std's
  `Queue` and `BitVector`, above) are `@connected`, as the host supplies
  them, so `x is Pair<K, V>` from `AnyValue` is an error too.
- A class or interface may not have a static and an instance member
  of the same name.
- A local alias of a type, as `let D = C;` in a function, is the type:
  checked as it is, and left out of generated code.

## `Console`, `getConsole`, and `Equatable` are nameable again

As in Temper 0.6.0, code may name `Console` (and implement it),
`getConsole(name)`, and `Equatable<T>` (as a type or bound, as `T
extends Equatable<T>`; it's sealed).

## 🚨Breaking change: no `Null` type

Core no longer declares `Null`. `null` has no type of its own: it takes
the nullable type its place wants, so it still works wherever a `T?` is
expected. A declaration whose only value is `null` now needs a type:

```temper inert
let a: String? = null; // not `let a = null;`
```

Code that named `Null` can use the nullable type it meant instead, and
may declare its own `Null`.

Null alone also has no members and is no condition: `null.isEmpty` and
`if (null)` are errors.

## 🆕 Module namespaces

`let NAME = import(PATH);` imports a whole module as a namespace:
`NAME.member` is its export `member`, in code and in types, and
translates just as a destructuring import of it would.

```temper inert
let rx = import("std/regex");
let pattern: rx.Regex = new rx.Regex(rx.oneOrMore(new rx.CodePoints("ab")));
```

In `new ns.C(x)`, the dots belong to the class, as `new (ns.C)(x)`.
Only a namespace may qualify the class there, and `new` without
arguments, as `new C` or `new { k: v }`, is an error.

## 🚨Breaking change: `builtins.X` removed

`builtins` is an ordinary name now, so a library's own `builtins` or
`core` works on every backend. Where a module declares a name core has,
as `class String`, it reaches core's through a namespace:
`let core = import("core");` then `core.String`.

## 🚨Breaking change: `Void` is never a value; type arguments fit their bounds

`Void` is only what a function returns, as `f(): Void` or `fn (): Void`.
A parameter, local, or field of type `Void`, a type argument like
`List<Void>`, `Void?`, and a `Void` result where a value is wanted, as
in `let f(): AnyValue { g() }` for `g(): Void`, are errors.

A type argument must fit its parameter's bound wherever it's written,
annotations included, so `let m: Map<Boolean, Int>` is an error: a map
key must be a `MapKey` of itself. `AnyValue`, `MapKey<String>`, and
`Never<Int>` aren't map keys.

## 🚨Breaking change: Rest parameter syntax removed.

Previously, Temper allowed variadic functions, functions
that can accept more arguments than the number of declared
formals, when the extra arguments could fit into a
homogeneous list that is bound to a "rest" parameter.

```temper inert
export let sum(
  ...nums: Int32  // ERROR: syntax not supported
): Int32 {
  var total = 0;
  for (let num of nums) {
    total += num;
  }
  total
}
```

These were hard to translate. Dynamic languages tend to
handle them fine, but TmpL had two special forms:
an expression for the count of rest arguments, and
an expression for the n-th rest argument.
Most backends didn't bother translating them, so
it was unclear that translation was generally possible.

But internally, the way rest parameters were represented
in function signatures complicated a lot of things.

It was also unclear how to support connected, variadic
functions.

Temper may reintroduce rest parameters in the future,
in a way that only puts a burden on backends that want
to allow rest-calling conventions, and in a way that
does not have extra moving parts for the vast majority
of function signatures that are non-resty.
(Perhaps by establishing the convention that if the last
parameter has a type like `RestList<T>` then extra
parameters can be auto grouped into a list, so rest
parameter calling is a compiler fiction for list passing.)

Some builtins are still variadic: the list constructor
and string concatenation.  But variadicity is not supported
in user code.

## Auto operator implementation

For the upcoming `@operator` feature, there's also a way to implement `==`
automatically:

```temper
@auto("==")
export class Point(
  public x: Int,
  public y: Int,
) {}
```

This automatically creates an `@operator("==")` implementation named `eq` that
checks `==` for all backed properties. This also works automatically for whole
hierachies of sealed types, which also checks the type in those cases.

See documentation for more details.

## Rust resumes every block awaiting a promise

On the Rust backend, a promise used to remember only the last `async` block
that awaited it, so when two blocks awaited the same promise, only one of them
resumed. Now every waiting block resumes, in the order it started waiting.
A `breakPromise` after `complete` no longer replaces the first result.

## Decorators for immutable types

The following decorators are now built into Temper. These tags are commitments
on the part of any type that extends them:

- `@imu` - Any type decorated with `@imu` must be deeply immutable with not even
  any internal state changes allowed. Built-in `@imu` types include *String*,
  *Int32*, and *Boolean*, among others.
- `@partialImu` - Any type decorated with `@partialImu` is also immutable when
  actual type arguments are deeply immutable. Built-in *PartialImu* types
  include *List* and *Map*.

These restrictions also apply to subtypes of types with the above decorations.

Immutable types will allow for safe transfer across threads or for compile-time
evaluation, among other possible applications. Most target languages don't have
a way to explicitly state immutability, but it's possible these tags can affect
translation in some cases.

Decorations `@imu` and `@partialImu` are checked by the Temper compiler. For
example, the following declarations are legal:

```temper inert
// `@imu` tag is valid because all properties are imu.
// And `List<Int>` is imu because *Int* is imu.
@imu class SomeValues(
  public text: String,
  public numbers: List<Int>,
) {}

// `@partialImu` is valid because this type is imu if *T* is imu.
@partialImu class SummarizedItems<T>(
  public items: List<T>,
  public summary: T,
) {}

// `@imu` is valid because *T* is constrained.
@imu interface PropHolder<@imu T> {
  public prop: T;
}

// And `@imu` is still valid here because all tags follow rules.
class SummarizedItemsHolder(
  public prop: SummarizedItems<String>,
) extends PropHolder<SummarizedItems<String>> {}
```

But the following are illegal:

```temper inert
// No commitment tag here, so legal but not imu.
class NoCommitment {}

// Compiler error because *NoCommitment* isn't imu.
@imu class BadImuClass(
  public things: List<NoCommitment>,
) {}

// Compiler error because *reassignable* is `var`.
@partialImu class BadImuClassBecauseVar<T>(
  public var reassignable: List<T>,
) {}
```

This is just a sampling of the rules which enforce `@imu` and `@partialImu`
tagging.

## 🚨Breaking change: type parameter variance now requires `@`.

Previously, a type parameter could be declared like `<out T>`.
Now you need to declare it `<@out T>` using decoration/annotation syntax.
Similarly for contravariant syntax `<@in T>`.
Invariant remains the default.

Also, both covariance and contravariance are deprecated.  It's not
clear that explicit variance is translatable to languages that infer
variance or to language's that are very aggressive about
monomorphization.

## 🚨Breaking change: Multiline string syntax (yes, again)

Early on, Temper used Python-esque triple quoted string syntax but
with rules for stripping incidental spaces from the beginning and ends
of lines.

```temper inert
let s = """
  Two spaces stripped per line
  Including here
  Can't have """ here
  """;
```

A subsequent 🚨breaking change introduced multi-quoted string syntax
that uses margin characters and to allow embedding statement fragments
using `{: ... :}`.

With margin characters, there's no need for an end delimiter.

```temper inert
let a = """
  "I am the Count who loves to ${action}!
  "{: for (let number of numbers) { :}
      " ${number}! Ha HA ha.
  "{: } :}
  ;
```

Unfortunately, that scheme was visually noisy: `{: } :}` is 7
characters for a payload of one single-character token.
And the rules around stripping newlines were confusing.

The new, final, multi-quoted string expression syntax now leans more
heavily on margin characters.

There are now three margin characters:

- The double quote (`"`) and tilde (`~`) margin characters precede
  literal character content, escape sequences, and interpolations.
  See below for why there are two.
- The colon margin character precedes lines of statement fragment
  tokens.

```temper
let numbers = ["Zero", "One", "Two", "Three"];
// Starting at zero because the Count is a vampire, not a monster.
let action = "count";

let a = """
  ~I am the Count who loves to ${action}!
  : for (let number of numbers) {
      ~ ${number}! Ha HA ha.
  : }
  ;
```

Spaces (and tabs) are still stripped from the ends of lines, so use
`${}` if you need a line to end with spaces.

But the `~` and `"` margin characters give fine-grained control over
newline stripping via one simple rules: `"` lines have them, `~` lines
don't.

### Migration Guide

To migrate old style complex string expressions to new style, keep in
mind two things.

1. The old style dropped any newline at the end of the last line.
   If you want the last line to drop its newline *now*, use the
   `~` margin character instead of `"`.
2. `{: ... :}` statement fragments need to be on their own lines,
   without the delimiters, and with a `:` margin character instead
   of `"`.
   if you have lines that mix statement fragments and character data,
   separate the parts onto separate lines with the right margin
   character, and use `~` instead of `"` for character data lines
   that were followed by other stuff before a newline.

## 🆕 Bitwise operators

Previously, Temper had `&` and `|` for operating on *Int32* and
*Int64* values but was missing some operators from the usual suite.

Temper now has these operators:

- Prefix `~` performs bitwise complement on an integer.
- Infix `^` performs bitwise exclusive-or on two same-width integers.
- Infix `<<` can take an integer of either width on the left and an
  *Int32* on the right, and shifts left.
- Infix `>>` does a sign-extending right shift, and take the same
  kinds of arguments as the left shift.
- Infix `>>>` does a non sign-extending right shift.

These are documented under the builtin operators reference.

All the shift operators ignore any bit the five (for 32b shifts) or
six (for 64b shifts) least significant bits which emulates common
hardware practice.

None of these operands panic on underflow or overflow.

Negative integers have defined semantics for shifting, as per their
2's complement representation, and shifting a positive value's bit
left into the most significant bit produces a negative integer again
per 2's complement.

## 🚨Breaking change: Details of rules for `<` ambiguity have changed

The previous parsing rules for determining when a `<` token is an
infix comparison operator and when it is part of a `<...>` type
argument group have changed.

Previously, the rule was based on speculatively parsing ahead, a
technique that made it difficult to recreate Temper's exact parsing in
tools like TextMate grammars.

Now, it is based on whether there is a space or comment token preceding.

Previously, some expressions that looked like comparisons, would require
extra parentheses.

    f(a < b, c > d)

Now they don't, but if you need a comparison operator, always put spaces
around it.

See the builtin/< reference article for details.

## 🚨Breaking change: Python parameters keep builtins' names

Generated Python keeps a parameter's name when a Python builtin has it,
where Temper 0.6.0 added a trailing `_`. A constructor's parameters,
which Python code may pass by name, change with it: for

```temper inert
export class Option(public help: String, public max: Int) {}
```

Python callers now write `Option(help="…", max=9)`, not
`Option(help_="…", max_=9)`. Callers using the old names, such as
`help_=`, must drop the `_`. This includes std's: `temper_std.regex`'s
`CodeRange` and `Repeat` take `min=` and `max=`. Keywords still take
the `_` (`from_`), and so do module-level names (`max_`).

Inside a function that names a parameter or local like a builtin it
also uses, generated code reaches the builtin through Python's
`builtins` module (`_builtins.len(items)`); elsewhere builtins stay as
they are.

## Rust crates depend on `temper-std` only when they use it

A generated Rust crate no longer depends on `temper-std` when its
library doesn't use std, where it always did before. A project with a
committed `Cargo.lock` that builds such a crate sees `temper-std` drop
out of it once.

## `++ls[i]` gives the new value

`++ls[i]` and `--ls[i]` on a `ListBuilder` or `MapBuilder` now give the
element's new value, as `++x` does, where they gave `Void` before, so
they can be used as values. `ls[i]++` still gives the old value, and
`ls[i] += 1` still gives `Void`, as `ls[i] = v` does.

## Unassigned locals in `for … of` bodies are errors

A `for … of` loop's body that reads a local of the function around it,
where some path to the loop doesn't assign it, is now "x is not
initialized along branches", as it is anywhere else in the function.
It wasn't reported before, and failed when run.

```temper inert
let firsts(xs: List<Int>): Int {
  let first;
  for (let x of xs) {
    console.log(first.toString()); // ERROR: not initialized
  }
  first = 0;
  first
}
```

## `@json` adapters

`@json` on a class or sealed interface now gives it a static
`jsonAdapter()`, and, unless it writes them itself, `encodeToJson` and
`decodeFromJson`, as the [decorator's docs](../language/builtins.md#json-decorator)
describe, with `@jsonName` and `@jsonExtra`; before, it gave "No member
jsonAdapter". A module with `@json` needn't import `std/json` for them.
Not yet for generic types, nor std's `Date`.

## A class has only one constructor

Several constructors were never portable (the JS output, for
one, was invalid), and now each one after the first is an error. For
other ways to make instances, use a static method, or an object literal
with the one constructor.

```temper inert
class Thing {
  public n: Int;
  public constructor(hi: Int) { n = hi; }
  public constructor(lo: Int) { n = lo + 1; } // ERROR: only one constructor
}
```

## Extension methods

A function marked `@extension("name")` can now be called as a method
wherever it's visible, declared or imported: `"step on no pets".isPalindrome()`
calls `stringIsPalindrome("step on no pets")`, on every backend; before,
such a call couldn't be translated. A method of the same name that
takes the arguments still wins. Two extensions that fit a call equally
well, declared side by side, are now an error, where Temper 0.6.0
picked the first one.

## Generated packages need the matching runtimes

A generated package depends on exactly the compiler's version of its
language's runtime, `temper-core`, and of `std`, as
`0.7.0-alpha.0.123` (`0.7.0.dev123` for Python). Temper 0.6.0's output
named the 0.6.0 runtimes on the package registries; the new
runtimes gained APIs its output uses, and aren't on the registries
yet, so use them from `temper.out/BACKEND/`, where `temper build`
writes them, or from a release's packages
([target languages](../backends/README.md)).

## Fixes for code Temper 0.6.0 accepted

- `new C(…)`, where `C`'s constructor `throws Bubble`, in a function
  that can't bubble is reported as "Cannot bubble from a function
  without Bubble in its return type", as Temper 0.6.0 reports it,
  instead of stopping translation.
- `x ?? d`, where `x` is a nullable member named bare in its class, is
  a non-null value, as `this.x ?? d` is; it was an error.
- A function literal or function reference where another library's
  `@fun` interface is wanted translates for every backend.
- In a library with errors, a value whose type an error left unknown,
  as a parameter of an undeclared type, fails where it runs when it's
  in a string template or has a method called, instead of keeping the
  module from being translated.

## Classes' `==`

- `assert(a == b)` translates for every backend when a side has no
  `toString()`, as a class's value without one, or a list. If it fails,
  its message is `expected a == b`, without the values, where Temper
  0.6.0 reported "No member toString" at the `assert`.
- In Rust, `other == this` in a class with an `@operator("==")` method
  compiles; it was `other == self`, comparing a value with a reference.
- `==` and `!=` on a generic class's values, as a `Box<Int>`'s, call its
  `@operator("==")` method on every backend, as the interpreter did,
  where the backends compared identities.
- `==`, `!=`, `<`, and the other comparisons, on a class's or
  interface's value that has no method marked `@operator` for them, say
  so: "P does not support infix `==`; give it a method marked
  `@operator("==")`", instead of a mismatch with `Int32`'s signature.

## Lua: `Int64` on LuaJIT and Lua 5.1

`Int64` works on LuaJIT and Lua 5.1, as in earlier releases, and more
of it does there: bitwise operators and shifts, `toFloat64()`, and
`toInt32Unsafe()` of a negative value. Integer division by zero fails
there, as on Lua 5.3 and later, instead of giving NaN or an infinity;
`Int32`'s `%` gives `0`, not `-0`; and `String.countBetween` and
`hasAtLeast` count only their range. On every Lua, `Int64.toInt32()`
works, and base 36 writes `x`. Each `Int64` constant is made once, as
its module loads, so a loop's `Int64` literals don't call the runtime
each time round.

## C++: `Float64.toString` as on the JVM

C++ writes a `Float64` as the interpreter and Java do: plain from 1e-3 up
to 1e7 (`-1500.0`, not `-1.5e+03`), and otherwise with one digit before
the point and an `e+N` or `e-N` exponent (`1.0e+7`), in the fewest
digits that read back as the same number.

## `temper` and its `TEMPER_*` settings

The command line tool is `temper`, as in earlier releases, and each
release's downloads are `temper-VERSION-TARGET.tar.gz` (`.zip` on
Windows). The environment variables it reads are named `TEMPER_*`:
`TEMPER_TIMEOUT` and `TEMPER_MEMORY_LIMIT` set the limits on what
`temper test` and `temper run` start
([the command line](../tools/cli.md#what-tools-start)). Development
builds before this used other names for both, so scripts written for
them need the new ones.

## Fixes on Windows

- `temper run -b java` and `temper test -b java` give javac its sources
  in an argument file, so a large library, with std and the runtime, no
  longer runs past Windows' command line limit.
- `temper run -b py` and `temper test -b py` run Python in UTF-8 mode
  (`PYTHONUTF8=1`), so what a program logs isn't lost to the system's
  code page.
- `temper build`'s JavaScript source maps name sources on another drive
  than `temper.out` by `file:` URL.
