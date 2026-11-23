# Builtin Functions and Constants

You can use builtins in your Temper code without `import`ing anything.
They're core's names, which a module may declare too, as its own `class
String`; to reach core's there, import core as a [namespace](#import),
under any name: `let core = import("core");` then `core.String`.

Some definitions are marked *Infix*, *Prefix*, or *Postfix*.  These
are operators: they are used like mathematical symbols:

- Infix `+` appears between arguments: `a + b`
- Prefix `-` appears before its sole argument: `-b`
- Postfix `++` appears after its argument: `i++`

Other functions are called with parentheses: `f(x)`.
See also [Call Syntax](syntax.md#syntax-for-call).

(The full set of operator definitions is [`operator.rs`](../../crates/temper-syntax/src/operator.rs)
which also determines when you need parentheses.  Temper's operator
precedence matches JavaScript's & TypeScript's as close as possible
so doing what you would in those languages is a good guide.)

## Constants

### `console`
The global default `Console` instance. It is designed to integrate with
standard backend logging services. Frontend processing replaces references to
the global console with a call to `getConsole` for the current module.

### *false*
The value `false` of type [*Boolean*](types.md#class-boolean).

### *Infinity*
The [*Float64*](types.md#class-float64) value for &infin;.

### *NaN*
The [*Float64*](types.md#class-float64) value for
[unrepresentable "Not a Number"](https://en.wikipedia.org/wiki/NaN) values.

### *null*
The singleton value `null` which is admitted by any [Postfix `?`](#postfix-) type.

`null` may be used to represent an absent value, for example:

- An unspecified, optional input
- An output that is not usable but not due to an error, for example, when mapping a list of values
  but excluding some corresponding elements from the output list
- A temporary, not computed *yet*, intermediate value

`null` is distinguishable, in translated Temper code, from any other value.
For example, in the context of the *Int32?* type, *0* is not equal to `null`.

Backends may translate `null` and the concept of nullable types in various ways:

- Target languages that have an equivalent (`null`, `nil`, `undefined`, or `None`)
  type/value pair may use that where it does not (as in C#) complicate translation of generics.
- Target languages that prefer *Option* types may translate nullable types to those
  and translate the `null` singleton to the *None* variant.

In either case, for dynamic language users and for users of typed languages that do not have
null-safety checking, please document when and why `null` is allowed in exported interfaces.
Temper has null-safety checking, so can mark APIs that accept/produce `null` equivalent values,
but the more clarity around `null` the better.

### *true*
The value `true` of type [*Boolean*](types.md#class-boolean).

### *void*
The value `void` is the sole value in type [*Void*](types.md#class-void).

`Void` is disjoint from `AnyValue` so no `void` value is directly usable.
See also [issue#38](https://github.com/temperlang/temper/issues/38).

## Special functions

### `async { ... }`

The *async* builtin function takes a safe generator block and
runs it out of band.

TODO: example

### `await(promise)`
TODO: document me

### `cat`
Short for "con**cat**enate", combines multiple strings into one string.

<!-- known: I10 -->
```temper
""       == cat()             &&
"foo"    == cat("foo")        &&
"foobar" == cat("foo", "bar")
// ✅
```

> **Note:** Temper doesn't declare `cat` yet ([inconsistencies](../inconsistencies.md#i10-examples-that-dont-run-as-marked)).

[`+`](#-2) does not concatenate strings; it's reserved for math.

`cat` is an implementation detail.  Prefer [String interpolation](types.md#string-interpolation)
to compose strings.

```temper
let a = "foo";
let b = "bar";

"foo-bar" == "${ a }-${ b }"
// ✅
```

### doPure

`doPure { x }` is the same as `x` except that the result will be available to
macro code early.  `x` must be pure code: it can't mutate any environment bindings
or objects it does not create and may not print or cause other side effects.

### `new`
The `new` operator allows constructing new instances of types.

It makes classes with a public constructor, not interfaces. Builtin
types say so in core: `new StringBuilder()` and `new Map<String,
Int>([])` work, but strings, numbers, booleans and lists come from
literals and methods, so `new String()` and `new List<Int>()` are
errors.

### `yield()`
Called from the body of a generator function, to pause the body
and cause [`Generator.next`](types.md#generatornext) to return.

The [block lambda](syntax.md#syntax-for-blocklambda) below has an
`extends GeneratorFn`.

A generator function may use the no-argument version of `yield`
in which case its wrapper generator's [`Generator.next`](types.md#generatornext)
returns the [*Empty*](types.md#class-empty) value.

If the generator function wishes to convey a value to its scheduler,
it may use `yield(x)` where `x` is the value returned by
[`Generator.next`](types.md#generatornext).

Note: `yield` is syntactically like `return` and `throw` in that
parentheses around its argument are optional.

```temper
// Two co-operating functions each have print
// calls which interleave because the generator
// function uses yield() to return control to
// its caller temporarily.

//!outputs "S: Before first call"
//!outputs "C: In coroutine, before yield"
//!outputs "S: Between calls"
//!outputs "C: Coroutine resumed"
//!outputs "S: After calls"

// scheduleTwice manually schedules execution of the generator/coroutine.
let scheduleTwice(generatorFactory: fn (): SafeGenerator<Empty>): Void {
  let generator: SafeGenerator<Empty> = generatorFactory();
  console.log("S: Before first call");
  generator.nextSafe();
  console.log("S: Between calls");
  generator.nextSafe();
  console.log("S: After calls");
}

// The block lambda declares a coroutine constructor
// which yields between two print statements causing
// them to interleave with the statement above.
scheduleTwice { (): GeneratorResult<Empty> extends GeneratorFn =>
  console.log("C: In coroutine, before yield");
  yield; // Return control to the caller
  console.log("C: Coroutine resumed");
}
// ✅
```

Above, the generator function has type `(): Empty extends GeneratorFn`
which specifies what the block lambda does.  But generator functions
are received by their receiver function as a factory for generator instances:
For example, `scheduleTwice` receives a `(fn (): Generator<Empty, Nothing>) & GeneratorFn`.

### assignment (`=`) builtin
The assignment operator allows assigning to local variables, and properties of objects.

<!-- known: I10 I20 -->
```temper
var s = "before";
console.log(s); //!outputs "before"
s = "after"; // <-- assignment
console.log(s); //!outputs "after"
// ✅
```

> **Note:** both compilers log `after` twice here, since a module's top-level reads of a `var` wait for its assignments; inside a function, it logs as shown ([inconsistencies](../inconsistencies.md#i20-top-level-statements-run-out-of-order)).

The result of assignment is the result of the right-hand, assigned expression.

```temper
let a;
let b;

// The result of the nested `b = 42` is `42`
a = b = 42;

a == 42
// ✅
```

Assignment to a property with a setter is really a method call, under the hood.

```temper
class C {
  public set p(newValue: String) {
    console.log("Assigned ${newValue}.");
  }
}
(new C()).p = "a value" //!outputs "Assigned a value."
// ✅ "a value"
```

## Functions

### `as`
The `as` operator allows safe type-casting.

`x as Type` means:

- If `x`'s [type tag](types.md#type-tags) is [compatible](types.md#type-compatibility)
  with `Type` then the result is `x`,
- otherwise [`bubble()`](#bubble).

```temper
sealed interface I {
  let s: String;
}
class A(public s: String) extends I {}
class B(public s: String) extends I {}

for (let x: I of [new A("an A"), new B("a B")]) {
  console.log((x as A).s orelse "cast failed");
}
//!outputs "an A"
//!outputs "cast failed"
// ✅
```

Note that `as` renames rather than casts in deconstructing
assignment context such as in
`let { exportedName as localName } = import("...");`.

### `bubble()`
A function that immediately bubbles.  Typically, this will result in execution
of the right argument to the closest containing [`orelse`](#orelse).

#### Failure
Expressions either evaluate to a value or fail to produce a usable result.
See also:

- [*Bubble*](#bubble-1) which provides type system support for tracking failure.
- [`orelse`](#orelse) which allows recovering from failure.

### `char`
A string tag that requires a single code-point string and returns that
code-point as an [*Int32*](types.md#class-int32).

```temper
char'a' == 97
// ✅
```

### `getConsole`
Access or make a console for the given name. If name is unspecified, use the
idiomatic backend library or module name for logging purposes. The default of
empty string is merely a placeholder.

### `ignore`
Ignore any value.

### `is`
The `is` operator allows runtime type-checking.

`x is Type` evaluates to true when `x`'s [type tag](types.md#type-tags)
is [compatible](types.md#type-compatibility) with `Type`.

```temper
class Foo {}
class Bar {}

let isAFoo(x: AnyValue): Boolean {
  x is Foo
}

console.log(isAFoo(new Foo()).toString());  //!outputs "true"
console.log(isAFoo(new Bar()).toString());  //!outputs "false"
// ✅
```

### `panic()`
A function that immediately panics, which is unrecoverable inside Temper.
It might be recoverable in backend code.

### Type operator `throws`

The infix `throws` operator can be used on a return type to indicate that a function
can fail in one of several ways.

```ebnf
Throws Syntax
```

A call that throws may be used within [`orelse`](#orelse).

```temper
let parseZero(s: String): Int throws Bubble {
  if (s != "0") { bubble() }
  return 0
}

//!outputs  "parseZero(\"0\")            = 0"
console.log("parseZero(\"0\")            = ${ parseZero("0") }");
//!outputs  "parseZero(\"hi\") orelse -1 = -1"
console.log("parseZero(\"hi\") orelse -1 = ${ parseZero("hi") orelse -1 }");
// ✅
```

### `!`
The prefix `!` operator performs [*Boolean*](types.md#class-boolean) inverse.

`!`[*false*](#false) is [*true*](#true) and vice versa.

### Postfix `?`

A question mark (`?`) after a type indicates that the type accepts the value [*null*](#null).

```temper
var firstEven: Int = null; // ILLEGAL
let ints = [1, 2, 3, 4];
for (let i of ints) {
  if (0 == (i % 2)) {
    firstEven = i;
    break;
  }
}

console.log(firstEven?.toString() ?? "so odd");
// ⏸️
```

But with a postfix question mark, that works.

```temper
//                ⬇️
var firstEven: Int? = null; // OK
let ints = [1, 2, 3, 4];
for (let i of ints) {
  if (0 == (i % 2)) {
    firstEven = i;
    break;
  }
}

console.log(firstEven?.toString() ?? "so odd"); //!outputs "2"
// ✅
```

## Operators

### `!=`
`a != b` is the [*Boolean*](types.md#class-boolean) inverse of [`==`](#-5).

Since `a != b` is syntactic sugar for `!(a == b)`, defining an
[equivalence operation](#equality-for-user-defined-classes-and-interfaces) once
will also enable using `!=` with that type.

### Remainder `%`
Given two [*Int32*](types.md#class-int32)s it produces an *Int*,
given two [*Int64*](types.md#class-int64)s it produces an *Int64*,
and given two [*Float64*](types.md#class-float64)s it produces a *Float64*.

```temper
13   % 3   == 1    &&
13.0 % 3.0 == 1.0
// ✅
```

Modulus by Zero [bubbles](types.md#class-bubble)

```temper
(1 % 0) orelse console.log("mod by zero");
//!outputs "mod by zero"
// ✅
```

```temper
(1.0 % 0.0) orelse console.log("mod by zero");
//!outputs "mod by zero"
// ✅
```

### Operator `&`, bitwise and
The `&` operator can be applied in two ways:

- To [*Int32*](types.md#class-int32)s it acts as a [bitwise operator](#int-).
- To types it produces an [intersection type](#intersection-type-bound-)

#### *Int* `&`

Takes two [*Int32*](types.md#class-int32)s or two [*Int64*](types.md#class-int64)s and returns the
integer that has any bit set that is set in both inputs.

```temper
// Using binary number syntax
(0b0010101 &
 0b1011011) ==
 0b0010001
// ✅
```

#### Intersection type bound `&`

When the `&` operator is applied to types instead of numbers, it constructs
an intersection type bound.

An intersection type bound specifies that the bounded type is a sub-type of each of its members.
So a value of a type that `extends I & J` declares a type can be assigned to a type `I` **and** can be assigned a declaration with type `J`.
See also [snippet/type/relationships](types.md#type-relationships).

```temper
interface A {
  a(): Void {}
}
interface B {
  b(): Void {}
}

class C extends A & B {}

let c: C = new C();
let a: A = c;
let b: B = c;

let f<T extends A & B>(t: T): Void {
  let a: A = t;
  let b: B = t;
  a.a();
  b.b();
}
f<C>(c);
// ✅
```

### Multiplication `*`
Infix `*` allows multiplying numbers.

Given two [*Int32*](types.md#class-int32)s it produces an *Int*, given two [*Int64*](types.md#class-int64)s it
produces an *Int64*, and given two [*Float64*](types.md#class-float64)s it produces a *Float64*.

```temper
3   * 4   == 12   &&
3.0 * 4.0 == 12.0
// ✅
```

### Exponentiation `**`
Infix `**` allows raising one number to the power of another.

Given two [*Float64*](types.md#class-float64)s it produces a *Float64*.

```temper
3.0 **  2.0 == 9.0 &&
4.0 ** -0.5 == 0.5
// ✅
```

### `+`
The builtin `+` operator has six variants:
- *Infix* with two [*Int32*](types.md#class-int32)s: signed addition
- *Prefix* with one [*Int32*](types.md#class-int32): numeric identity
- *Infix* with two [*Int64*](types.md#class-int64)s: signed addition
- *Prefix* with one [*Int64*](types.md#class-int64): numeric identity
- *Infix* with two [*Float64*](types.md#class-float64)s: signed addition
- *Prefix* with one [*Float64*](types.md#class-float64): numeric identity

<!-- known: I10 -->
```temper
1    + 2    == 3    &&
1.0  + 2.0  == 3.0  &&
1i64 + 2i64 == 3i64 &&
+1          == 1    &&
+1.0        == 1.0  &&
+1i64       == 1i64
// ✅
```

As explained above, you cannot mix [*Int32*](types.md#class-int32) and
[*Float64*](types.md#class-float64) inputs nor either with [*Int64*](types.md#class-int64):

```temper
1 + 1.0
// ❌ Actual arguments do not match signature: (Int32, Int32) -> Int32 expected [Int32, Int32], but got [Int32, Float64]!, Expected subtype of Int32, but got Invalid!
```

`+` does not work on [*String*](types.md#class-string)s.  Use [String interpolation](types.md#string-interpolation) instead.

```temper
"foo" + "bar"
// ❌ Actual arguments do not match signature: (Int32, Int32) -> Int32 expected [Int32, Int32], but got [String, String]!
```

### `++` operator: increment
`++x` reads `x` and assigns the following value back to it.

The value assigned is `x.succ()`.
Builtin numeric types implement the `.succ()` method to return a value one greater,
so for numerics `++x` is equivalent to `x += 1`.

`x++` has the same effect as `++x`, but produces the value of `x` before
assigning its successor, instead of the value after.

<!-- known: I10 I20 -->
```temper
var x: Int = 0;
// when `x` comes after  `++`, produces value after  increment
console.log((++x).toString()); //!outputs "1"
// when `x` comes before `++`, produces value before increment
console.log((x++).toString()); //!outputs "1"
x == 2
// ✅
```

> **Note:** both compilers log `0` and `2` here, at a module's top level; inside a function, it logs as shown ([inconsistencies](../inconsistencies.md#i20-top-level-statements-run-out-of-order)).

The effects of `++x` and `x++` differ from `x = x.succ()`, in that if `x` is a complex expression,
its parts are only evaluated once.
For example, in `++listBuilder[f()]`, the function call, `f()`, which computes the index,
only happens once.

### `-`
The builtin `-` operator has six variants like [`+`](#-2).

```temper
3   - 1   == 2   &&
3.0 - 1.0 == 2.0 &&
-3        <  0   &&
-3.0      <  0.0
// ✅
```

As with `+`, you cannot mix [*Int32*](types.md#class-int32) and [*Float64*](types.md#class-float64) inputs:

```temper
1 + 1.0
// ❌ Actual arguments do not match signature: (Int32, Int32) -> Int32 expected [Int32, Int32], but got [Int32, Float64]!, Expected subtype of Int32, but got Invalid!
```

The `-` operator is left-associative:

```temper
1 - 1 - 1 == (1 - 1) - 1 &&
1 - 1 - 1 == -1
// ✅
```

Since there is a [`--` operator: decrement](#---operator-decrement) operator, `--x` is not a negation of a negation.

```temper
var x = 1;
+x == -(-x) &&  // double negation is identity
--x == 0        // but two adjacent `-` means pre-decrement
// ✅
```

### `--` operator: decrement
`--x` reads `x` and assigns the preceding value back to it.
The value assigned is `x.pred()`.
Builtin numeric types implement the `.pred()` method to return a value one less,
so for numerics `--x` is equivalent to `x -= 1`.

`x--` has the same effect as `--x`, but produces the value of `x` before
assigning its predecessor, instead of the value after.

<!-- known: I10 I20 -->
```temper
var x: Int = 0;
// when `x` comes after  `--`, produces value after  decrement
console.log((--x).toString()); //!outputs "-1"
// when `x` comes before `--`, produces value before decrement
console.log((x--).toString()); //!outputs "-1"
x == -2
// ✅
```

> **Note:** both compilers log `0` and `-2` here, at a module's top level; inside a function, it logs as shown ([inconsistencies](../inconsistencies.md#i20-top-level-statements-run-out-of-order)).

The effects of `--x` and `x--` differ from `x = x.pred()`, in that if `x` is a complex expression,
its parts are only evaluated once.
For example, in `--listBuilder[f()]`, the function call, `f()`, which computes the index,
only happens once.

### Division `/`
Infix `/` allows dividing numbers.

Given two [*Int32*](types.md#class-int32)s it produces an *Int*, given two [*Int64*](types.md#class-int64)s it
produces an *Int64*, and given two [*Float64*](types.md#class-float64)s it produces a *Float64*.

```temper
12   / 3   == 4    &&
12.0 / 3.0 == 4.0
// ✅
```

Integer division [rounds towards zero].

```temper
 7   / 2   ==  3   &&
-7   / 2   == -3   &&
 7.0 / 2.0 ==  3.5 &&
-7.0 / 2.0 == -3.5
// ✅
```

Division by zero has [*Bubble*](types.md#class-bubble).

```temper
(1 / 0) orelse console.log("div by zero");
//!outputs "div by zero"
// ✅
```

Float64 division by zero is a *Bubble* too.

```temper
console.log("${ (0.0 /  0.0).toString() orelse "Bubble" }"); //!outputs "Bubble"
console.log("${ (1.0 /  0.0).toString() orelse "Bubble" }"); //!outputs "Bubble"
console.log("${ (1.0 / -0.0).toString() orelse "Bubble" }"); //!outputs "Bubble"
// ✅
```

[IEEE-754]: https://en.wikipedia.org/wiki/IEEE_754
[rounds towards zero]: https://en.wikipedia.org/wiki/Rounding#Rounding_toward_zero

### Operator `<`, less-than
`a < b` is [*true*](#true) when *a* orders before *b*, and is a compile-time error
if the two are not mutually comparable.

`<` is part of a family of related operators including `<`, `<=`, `>=`, `>`,
and `<=>`.  Especially see [`<=>`](#-4) for details on how
comparison works for Temper-defined and user-defined types.

See the [General comparison algorithm](#general-comparison-algorithm) for details of how they are compiled and
especially the [General Comparison Caveats](#general-comparison-caveats).

#### Syntactic corner case: `<` ambiguity

Tldr: always put spaces around infix operators like `<`.

The `<` operator means comparison, but in a type expression, it can also be a bracket.

```temper
console.log(c < d);  // Compare c to d

let x:      C<D>;    // x's type is C parameterized with D
// ⏸️
```

Other languages also have two meanings for `<`.  Temper does not want to enforce a
hard grammatic distinction between types and expressions, and to avoid workarounds
like extra turbofish syntax.

In Temper the rule is:

> If a `<` token is not preceded by a space or comment, then it is an angle bracket
> otherwise it is a comparison operator.

(In Temper, types are upper-case by convention, but we cannot use case as in `C<D>`
above to disambiguate because Temper assigns no semantic significance to identifier
case, to better support non-European identifiers which are mostly in (unicameral)
writing systems.)

For example:

```temper
// ┏━━━━ This space makes the difference
f(a < b, c > d);  // pass two booleans to f
f(A<B, C>);       // pass one type with two parameters to f (a macro?)

class C<T> {}  // A class declaration with a formal type parameter

// Type argument lists can be spread over multiple lines.
class C< // No space **before**, so this `<` starts C's type argument list.
  T
> {}

class C <T>    // ERROR: trying to compare `class C` to `T` probably won't work

class C  // ERROR: space before '<'
<T> {}
// ⏸️
```

The rule to determine whether a `>` token is an angle bracket or a comparison
operator is purely made based on preceding tokens.

> If there are zero preceding `<` bracket tokens without a `>` partner then it
> is a bracket, otherwise it is an infix operator.

This code doesn't mean much, but the parsing rules are clear.

```temper
// ┏━━━┓ 3 open `<` brackets
  A<B<C<D>>>>
//       ┗┳┛┗━━━━━━━ This fourth one is an infix comparison operator
//        ┃
// Make these 3 close `>` brackets
// ⏸️
```

To avoid confusion, just put spaces around all your infix operators.

### Operator `<<`, shift left
The left shift (`<<`) operator takes an [*Int32*](types.md#class-int32) or a
[*Int64*](types.md#class-int64) to shift and an [*Int32*](types.md#class-int32) which is
the number of bits to shift by.

All but the 5 (for *Int32*) or 6 (for *Int64*) least significant bits of the right
operand are ignored.

```temper
// Using binary number syntax
(0b0000_0001_0101 << 3) ==
//        / _/ /
//       / /  /
//      / /  /
 0b0000_1010_1000
// ✅
```

### `<=`
`a <= b` is [*true*](#true) when *a* orders with or before *b*,
and is a compile-time error if the two are not mutually comparable.

`<=` is part of a family of related operators including `<`, `<=`, `>=`, `>`,
and `<=>`.  Especially see [`<=>`](#-4) for details on how
comparison works for Temper-defined and user-defined types.

See the [General comparison algorithm](#general-comparison-algorithm) for details of how they are compiled and
especially the [General Comparison Caveats](#general-comparison-caveats).

### `<=>`
`a <=> b` results in an [Int](types.md#class-int32) based on whether *a* orders before, after, or
with *b*, and is a compile-time error if the two are not mutually comparable.

- `a <=> b` is `-1` if *a* orders **before** *b*
- `a <=> b` is `0` if *a* orders **with** *b*
- `a <=> b` is `1` if *a* orders **after** *b*

```temper
(   42 <=>   123) == -1 &&  //    42 orders before   123
(  1.0 <=>   1.0) == 0  &&  //   1.0 orders with     1.0
("foo" <=> "bar") == 1      // "foo" orders after  "bar"
// ✅
```

#### General comparison algorithm
The general comparison algorithm is designed to allow for easy structural comparison of
data values that work the same regardless of target language.

The whole group of comparison operators (`<`, `<=`, `>=`, `>`) are all syntactic sugar
for `<=>` whose semantics are defined here.

For example, `a < b` is really syntactic sugar for `(a <=> b) < 0`: an application
of trinary comparison with its [*Int32*](types.md#class-int32) result compared using
a builtin *Int32* to *Int32* comparison.

[*Int32*](types.md#class-int32)s are compared based on their position on the number line.
No surprises here.

```temper
-1 < 0 && 0 < 1 && 1 < 2
// ✅
```

[*Float64*](types.md#class-float64)s are also compared numerically.

```temper
-1.0 < 0.0 && 0.0 < 1.0 && 1.0 < 2.0
// ✅
```

But the default comparison operators are meant to support structural comparison of records
so see also [caveats](#general-comparison-caveats) for how *Float64* ordering differs
from some other languages.

[*String*](types.md#class-string)s are compared lexicographically based on their code-points.

```temper
"foo" > "bar"
// ✅
```

See also [caveats](#general-comparison-caveats) for *String* related ordering,
how this differs from some programming language that prefer UTF-16 based ordering.

##### Custom comparison for classes

A user defined class may be made comparable by defining an
[`@operator("<=>")`](#operator-decorator) method or function.

It is the implementors responsibility to ensure the usual properties
of ordering apply: comparison is transitive (a < b && b < c -> a < c),
asymmetric (a < b -> b > a), and reflexive (a <= a && a >= a && !(a < a)).
(For asymmetric, only the sign3 of the `<=>` matters, so it may not be the
 case that `(a <=> b) == -(b <=> a)`, just that
 `(a <=> b).signum == -((b <=> a).signum)`.

#### General Comparison Caveats

##### String Ordering Caveats
[*String*](types.md#class-string) ordering based on code-points means that [supplementary code-points]
(code-points greater than U+10000) sort higher than all [basic plane] code-points,

```temper
"\u{10000}" > "\u{FFFF}" // Hex code-point escapes
// ✅
```

Developers used to lexicographic [UTF-16] might be surprised since UTF-16 ordering
treats each supplementary code-point as two [surrogate]s in the range \[0xD800, 0xDFFF\].
The first string above would be `"\uD800\uDC00"` written in JSON with each surrogate
separately escaped. In some languages, that might compare as less than "\u{FFFF}", but
Temper views all strings in terms of full code-points, or more precisely, in terms of
Unicode [scalar value]s, which exclude surrogate codes appearing by themselves.

##### Float64 Ordering Caveats

[*Float64*](types.md#class-float64)s are compared on a modified number line where
`-0.0` precedes `+0.0` and all `NaN` values sort above +&infin;.
This differs from the [IEEE-754 comparison predicate] which treats `NaN` as incomparable.
[`Float64.near`](types.md#float64near) checks `==` by this order first, so `NaN` is near `NaN`.

<!-- known: I10 -->
```temper
-Infinity < -1.0     &&
     -1.0 <  0.0     &&
     -0.0 < +0.0     &&  // Positive and negative zero order separately
      0.0 <  1.0     &&
      1.0 < Infinity &&
 Infinity < NaN          // NaN is ordered high
// ✅
```

[IEEE-754 comparison predicate]: https://grouper.ieee.org/groups/msc/ANSI_IEEE-Std-754-2019/background/predicates.txt

[basic plane]: https://unicode.org/glossary/#basic_multilingual_plane
[scalar value]: https://unicode.org/glossary/#unicode_scalar_value
[supplementary code-points]: https://unicode.org/glossary/#supplementary_code_point
[surrogate]: https://unicode.org/glossary/#surrogate_code_point
[UTF-16]: https://unicode.org/glossary/#UTF_16

### `==`
`a == b` is the default equivalence operation.

For builtin types, that underlying check is based on the
[General comparison algorithm](#general-comparison-algorithm) even though a type does not need to
be ordered to be comparable.

```temper
// Int32
console.log("0 == 0 -> ${0 == 0}"); //!outputs "0 == 0 -> true"
console.log("0 == 1 -> ${0 == 1}"); //!outputs "0 == 1 -> false"

// Int64
console.log("0i64 == 0i64 -> ${0i64 == 0i64}"); //!outputs "0i64 == 0i64 -> true"
console.log("0i64 == 1i64 -> ${0i64 == 1i64}"); //!outputs "0i64 == 1i64 -> false"

// Float64
// NaN and signed zero are not corner cases for ==`.`
console.log("0.0 == 0.0 -> ${0.0 == 0.0}"); //!outputs "0.0 == 0.0 -> true"
console.log("0.0 == 1.0 -> ${0.0 == 1.0}"); //!outputs "0.0 == 1.0 -> false"

// String
// Unlike `<=>`, there are no UTF-8 vs UTF-16 caveats for ==`.`
console.log("'a' == 'a' -> ${'a' == 'a'}"); //!outputs "'a' == 'a' -> true"
console.log("'a' == 'b' -> ${'a' == 'b'}"); //!outputs "'a' == 'b' -> false"
// ✅
```

The related [`!=` operator](#-1) is just the negation of
the `==` operator.

Comparing incomparable values is a compile time error.

```temper
0 == "0"
// ❌ Actual arguments do not match signature: (Int32, Int32) -> Boolean expected [Int32, Int32], but got [Int32, String]!, Expected subtype of Boolean, but got Invalid!
```

But any value may be compared using `==` to `null`.

#### Equivalence to `null`

Only the `null` value is equivalent to itself.

Equivalence is *null-safe*.  I.e., if `a` and `b` could be [*null*](#null)
because their types use [Postfix `?`](#postfix-), then `a == b` is equivalent to the
below where *isNull* is a compiler-internal predicate for testing nullity:

```temper
if (isNull(a)) {
  isNull(b)
} else {
  !isNull(b) && a == b
}
// ⏸️
```

The Temper compiler simplifies such complicated checks using type information
so `a == null` and `null == a` are equivalent to `isNull(a)`.

The important takeaway, for Temper semantics are that `==` tests equivalence
using a type-specific equivalence function or method but only *after* checking
whether the operands are `null`.

```temper
console.log("null == null -> ${null == null}"); //!outputs "null == null -> true"
console.log("null == 1234 -> ${null == 1234}"); //!outputs "null == 1234 -> false"
console.log("1234 == null -> ${1234 == null}"); //!outputs "1234 == null -> false"
// ✅
```

##### Equality for user-defined `class`es and `interface`s

For `class` instances, equivalence is just a call to a method or function
with [`@operator("==")`](#operator-decorator) metadata.
Since [`!=`](#-1) is syntactic sugar for a boolean negation of
`==` applied to the same arguments, you don't need to define both
operators, and trying to define `!=` will not help.

All operator `==` implementations should return [*Boolean*](types.md#class-boolean).
All such implementations should be reflexive: `a == b -> b == a` and
`a != b -> b != a` when both `a`'s and `b`'s type's provide.implemenations.

If an implementation is defined on a [`sealed` type modifier](#sealed-type-modifier) `interface`
it should work for comparing all subtypes, perhaps by checking types and
delegating to subtype specific implementations.

TODO: auto-derived structural equality.

```temper
class C {
  @operator("==")
  public equals(other: C?): Boolean { true } // Not a lot of difference.
}

let c = new C();
let d = new C();

console.log("c == c -> ${c == c}"); //!outputs "c == c -> true"
console.log("c == d -> ${c == d}"); //!outputs "c == d -> true"
// You can't call a method on `null`, but `==` is null safe.
console.log("null == c -> ${null == c}"); //!outputs "null == c -> false"
// Even though C.equals accepts null inputs, the null check happens
// before the method call, so this is false.
console.log("c == null -> ${c == null}"); //!outputs "c == null -> false"
// ✅
```

A class without such a method has no `==`, so comparing its values is
an error, and likewise `<` and the other comparisons without an
[`@operator("<=>")`](#operator-decorator) method.

```temper
class P(public x: Int) {}
new P(1) == new P(1)
// ❌ P does not support infix `==`; give it a method marked `@operator("==")`!
```

### `>`
`a > b` is [*true*](#true) when *a* orders after *b*, and is a compile-time
error if the two are not mutually comparable.

`>` is part of a family of related operators including `<`, `<=`, `>=`, `>`,
and `<=>`.  Especially see [`<=>`](#-4) for details on how
comparison works for Temper-defined and user-defined types.

See the [General comparison algorithm](#general-comparison-algorithm) for details of how they are compiled and
especially the [General Comparison Caveats](#general-comparison-caveats).

### `>=`
`a >= b` is [*true*](#true) when *a* orders after or with *b*, and is a compile-time
error if the two are not mutually comparable.

`>=` is part of a family of related operators including `<`, `<=`, `>=`, `>`,
and `<=>`.  Especially see [`<=>`](#-4) for details on how
comparison works for Temper-defined and user-defined types.

See the [General comparison algorithm](#general-comparison-algorithm) for details of how they are compiled and
especially the [General Comparison Caveats](#general-comparison-caveats).

### Operator `>>`, shift right
The right shift (`>>`) operator takes an [*Int32*](types.md#class-int32) or a
[*Int64*](types.md#class-int64) to shift and an [*Int32*](types.md#class-int32) which is
the number of bits to shift by.

All but the 5 (for *Int32*) or 6 (for *Int64*) least significant bits of the right
operand are ignored.

```temper
// Using binary number syntax
(0b0000_1010_1010 >> 3) ==
//       \ \_ \ \
//        \  \ \ *
//         \  \ \
 0b0000_0001_0101
// ✅
```

Unlike the [`>>>`](#operator--shift-right-zero-extending) operator, this operator is sign extending.
When shifting right by *n* bits, the *n* highest bits in the output are copied
from the most-significant bit in the input.

<!-- known: I23 -->
```temper
(0x8000_0000_0000_0000 >> 2) ==
// |\
// |/\
 0xE000_0000_0000_0000
// ✅
```

### Operator `>>>`, shift right (zero extending)
The right shift (`>>>`) operator takes an [*Int32*](types.md#class-int32) or a
[*Int64*](types.md#class-int64) to shift and an [*Int32*](types.md#class-int32) which is
the number of bits to shift by.

All but the 5 (for *Int32*) or 6 (for *Int64*) least significant bits of the right
operand are ignored.

```temper
// Using binary number syntax
(0b0000_1010_1010 >>> 3) ==
//       \ \_ \ \
//        \  \ \ *
//         \  \ \
 0b0000_0001_0101
// ✅
```

Unlike the [`>>`](#operator--shift-right) operator, this operator is zero extending.
When shifting right by *n* bits, the *n* highest bits in the output are copied
from the most-significant bit in the input.

<!-- known: I23 -->
```temper
(0x8000_0000_0000_0000 >>> 2) ==
 0x2000_0000_0000_0000
// ✅
```

### Operator `^`, bitwise-xor
The bitwise-xor (`^`) operator takes two [*Int32*](types.md#class-int32)s or
two [*Int64*](types.md#class-int64)s and returns an integer of the same size
that has each bit set when the corresponding bits in the inputs
are different.

<!-- known: I23 -->
```temper
// Using binary number syntax
(0b1111_0000_1111_0000_1111_0000_1111_0000 ^
 0b1010_1010_1010_1010_0101_0101_0101_0101) ==
 0b0101_1010_0101_1010_1010_0101_1010_0101
// ✅
```

### Operator `|`, bitwise or
The `|` operator performs bitwise union.

It takes two [*Int32*](types.md#class-int32)s or two [*Int64*](types.md#class-int64)s and returns
the integer of the same size that has any bit set that is set in either input.

```temper
// Using binary number syntax
(0b0010101 |
 0b1011011) ==
 0b1011111
// ✅
```

### Operator `~`, bitwise inverse
The `~` operator negates the bits in an integer.

Given an [*Int32*](types.md#class-int32) or [*Int64*](types.md#class-int64) it returns the integer
of the same size with the opposite bits.

<!-- known: I23 -->
```temper
// Using binary number syntax
~0b0000_0001_0010_0011_0100_0101_0110_0111 ==
 0b1111_1110_1101_1100_1011_1010_1001_1000
// ✅
```

## Types

### *Boolean*
The name `Boolean` refers to the builtin type, [*Boolean*](types.md#class-boolean).

It has only two values:

- [*false*](#false)
- [*true*](#true)

```ebnf
BooleanLiteral := "false" | "true"
```

### *Bubble*
A name that may be used to reference the special [*Bubble*](types.md#class-bubble) type.

### *Float64*
The name `Float64` refers to the builtin type, [*Float64*](types.md#class-float64).

### *Function*
The name `Function` refers to the builtin type, [*Function*](types.md#interface-function), which is the super-type
for all [Function Types](types.md#function-types).

### *Int*
The name `Int` is an alias for `Int32`, for convenience in indicating the
default int type.

### *Int32*
The name `Int32` refers to the builtin type, [*Int32*](types.md#class-int32).

### *Int64*
The name `Int64` refers to the builtin type, [*Int64*](types.md#class-int64).

### `List`
The name `List` refers to the builtin type, [*List*](types.md#class-list).

### `Listed`
The name `Listed` refers to the builtin type, [*Listed*](types.md#interface-listed).

### *Problem*
The name `Problem` refers to the builtin type, [*Problem*](types.md#class-problem),
an abstraction that allows running some tests despite failures during
compilation.

TODO: StageRange

### *String*
The name `String` refers to the builtin type, [*String*](types.md#class-string).

### *Symbol*
Symbol is the type for values that are used, during compilation,
to represent parameter names, member names, and metadata keys.

They're relevant to the Temper language, and symbol values are
not meant to translate to values in other languages.

### *Type*
Type values represent a Temper type, allowing them to be inputs
to macros.

### *Void*
The name `Void` refers to the builtin type [*Void*](types.md#class-void).

*Void* is an appropriate return type for functions that are called for their effect, not
to produce a value.

## Macros

### `assert`
A macro for convenience in test cases. Calls assert on the current test
instance.

```temper
test("something important") {
  // Provide the given message if the assertion value is false.
  assert(1 + 1 == 2) { "the sky is falling" }
}
// ⏸️
```

Without a message, `assert(c)` says `expected c` if it fails, and
`assert(a == b)` shows both values, as `expected a == (2) not (3)`,
where it can: where a side has no `toString()`, it's `expected a == b`.

### `break` statement
`break` jumps to the end of a block.

Without a label, `break` jumps to the narrowest surrounding [`for` loop](#for-loop),
[`while` loop](#while-loop), or [`do`](#do-loops-and-do-blocks) loop.

```temper
for (var i = 0; i < 10; ++i) {
  if (i == 3) { break; }
  console.log(i.toString());
}
//!outputs "0"
//!outputs "1"
//!outputs "2"
// ✅
```

`break` may also be followed by a label, in which case, it jumps to just after the
statement with that label.

```temper
label: do {
  console.log("foo"); //!outputs "foo"
  break label;
  console.log("bar"); // never executed
}
// ✅
```

A `break` that matches neither label nor loop within the same function or module body
is a compile-time error.

```temper
break;
// ❌ break/continue not within a matching block!
```

```temper
for (var i = 0; i < 10; ++i) {
  break label;
}
// ❌ break/continue not within a matching block!
```

### `class`
Defines a concrete `class` type.  Classes may extend [`interface`](#interface) types but may
not themselves be extended.

Class declarations consist of several parts:

1. A type name
2. Optionally, some type formals
3. Optionally, constructor parameters
4. Optionally, some super types
5. A block containing type members

```temper
//    ①          ②      ②
class MyTypeName<TYPE, FORMAL>(

  // ③
  public propertyName: Int,

  //      ④       ⑤
) extends AnyValue {

  public anotherProperty: Int = propertyName + 1;

  public method(): Int { anotherProperty * 2 }
}

// Given such a declaration, we can create values of type MyTypeName.
// Properties that appear in the parenthetical are both properties and
// constructor parameters that initialize their corresponding property.
let value = new MyTypeName<Boolean, Boolean>(123);
// But parenthetical declarations also declare a property.
console.log(value.propertyName.toString()); //!outputs "123"
// Public members declared in the body are also available.
console.log(value.anotherProperty.toString()); //!outputs "124"
console.log(value.method().toString()); //!outputs "248"
// ✅
```

A minimal class declaration may omit many of those elements along with
the brackets and [`extends` clause](#subtype-extends-supertype):

```temper
class Minimal {}

let m = new Minimal();

console.log((m is Minimal).toString()); //!outputs "true"
// ✅
```

Source: [*TypeDefinitionMacro.kt*](https://github.com/temperlang/temper/blob/kotlin-final/frontend/src/commonMain/kotlin/lang/temper/frontend/TypeDefinitionMacro.kt), in Temper's previous compiler.

Re parenthetical declarations, see also: [`@noProperty` decorator](#noproperty-decorator)

Be aware that [`<` space sensitivity](#syntactic-corner-case--ambiguity) requires
leaving no space between the type name and the start of the `<...>` type parameter list.

### `compilelog`

TODO: Migrate the logging APIs to `console` style objects APIs per issue #1180
Name the logger that runs during early stages `compile` so that
`compile.log(...)` aids in debugging macro calls.

### `continue` statement
A `continue` statement jumps back to the beginning of a loop.
In a [`for` loop](#for-loop) loop, it jumps back to the increment part.

A `continue` without a label jumps back to the innermost containing loop.

```temper
for (var i: Int = 0; i < 4; ++i) {
  if (i == 1) { continue; }
  console.log(i.toString());
}
//!outputs "0"
// 1 is skipped
//!outputs "2"
//!outputs "3"
// ✅
```

A `continue` with a label jumps back to the loop with that label.

```temper
outer:
for (var i: Int = 0; i < 2; ++i) {
  for (var j: Int = 0; j < 10; ++j) {
    if (j == 1) { continue outer; }
    // Reached once for each value of i, with j == 0
    console.log("(${i.toString()}, ${j.toString()})");
  }
  // Continue skips over this so it never runs
  console.log("Not reached");
}
//!outputs "(0, 0)"
//!outputs "(1, 0)"

console.log("done"); //!outputs "done"
// ✅
```

### macro `dataFile`
The `dataFile` macro declares a data file that can be bundled with the library translation.
It takes three inputs:

- The path to the data file relative to the module directory.
  For example, if the current module is `my-library//foo` and the path is `bar.json`
  then in `my-library`, the resource path `foo/bar.json` would allow retrieving the data
  file in some target-language specific way.
- The mime-type of the data.
- The data itself.

<!-- known: I10 -->
```temper
dataFile(
  "./my-data.json",
  "application/json",
  '["My", "data", "goes", "here"]',
)
// ✅ null
```

> **Note:** Temper doesn't declare `dataFile` yet, and has no `data` backend ([inconsistencies](../inconsistencies.md#i10-examples-that-dont-run-as-marked)).

If the path starts with a `/` then the rest of the path will be resolved
relative to the library's root directory.

The Data Backend writes the data files to the temper output directory.

### `do` loops and `do` blocks
`do` loops are like [`for` loop](#for-loop) and [`while` loop](#while-loop) except that the body
runs once before the condition is checked the first time.
This is the same as how these loops operate in many other languages.

`do` differs in Temper that it can be used without a condition to do something once.
This can be useful when you need a block in the middle of a larger expression.

<table markdown="1"><tr markdown="1"><th markdown="1">`do`</th><th markdown="1">`while`</th></tr>
<tr markdown="1"><td markdown="1">

```mermaid
stateDiagram-v2
  [*] --> Body
  Body --> Condition
  Condition --> Body : true
  Condition --> [*] : false
```

</td><td markdown="1">

```mermaid
stateDiagram-v2
  [*] --> Condition
  Condition --> Body : true
  Body --> Condition
  Condition --> [*] : false
```

</td></tr></table>

```temper
var i = 1;
// Prints "Done" initially, and goes back to do it again when `i` is (1, 0)
do {
  console.log("Done");
} while (i-- >= 0);

//!outputs "Done"
//!outputs "Done"
//!outputs "Done"
// ✅
```

versus

```temper
var i = 1;
// Prints "Done" when `i` is (1, 0)
while (i-- >= 0) {
  console.log("Done");
}

//!outputs "Done"
//!outputs "Done"
// ✅
```

You can see that when the condition is initially false, the body still executes once.

```temper
do {
  console.log("Done once"); //!outputs "Done once"
} while (false);
// ✅
```

`continue` jumps to just before the condition, not over the condition to the top of the loop.

```temper
do {
  console.log("Done once"); //!outputs "Done once"
  continue;                 // jumps ━━━┓
  console.log("Not done");  //          ┃
  //     ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
  //     ┃
} while (false);
// ✅
```

If you just want to do something once, you can omit the `while` clause.

```temper
console.log( //!outputs "Done once"
  do { "Done once" }
);
// ✅
```

This allows you to group expressions, some for their side effects,
followed by another to compute a result.

```temper
// These related statements are now grouped together, and
// the `do` block can be nested in a complex expression.
do {
  var i = 0;  // Scoped
  i += 4;     // For side-effect
  i * 2       // Result
}
// ✅ 8
```

When `do` is followed by a curly bracket, the content between the
brackets are treated as statements, not object properties.

#### Blocks vs. bags
Putting `do` before curly brackets, `do {⋯}` specifies a block of statements, not a bag of
key/value properties à la JSON.

```temper
// A bag of properties
{
   key₀: value₀,
   key₁: value₁
}

// A series of statements
do {
  statement₀;
  label: statement₁
}
// ⏸️
```

Temper uses a simple rule to decide whether `{⋯}` surrounds properties or statements because
there can be confusion otherwise.

```temper
{ labelOrKey: valueOrStatement } // ?

{ /* are empty brackets a group of zero properties or zero statements? */ }

// This is complicated with "punning": when a key and value have the same text.
{ x } // may be equivalent to `{ x: x }` in a property context.
// Within small or heavily punned brackets, there are fewer cues like colons,
// commas, or semicolons.
// ⏸️
```

Whether a `{` is followed by properties or statements is made based on the preceding
non-comment, non-space token.

| Preceding Token Text        | Contains   | Classification                              |
| --------------------------- | ---------- | ------------------------------------------- |
| `let`, `var`, `const`       | Properties | declaration                                 |
| `new`                       | Properties | new value context                           |
| *no preceding token*        | Properties | the start of a module                       |
| `]`, `)`, *Angle* `>`, `=>` | Statements | block or block lambda                       |
| *word*                      | Statements | block or block lambda                       |
| *anything else*             | Properties | default is bag                              |

### `enum`
TODO(issue #859): Fix translation of enum definitions before documenting.

`enum` declarations are like class declarations

```temper
enum C { A, B, C; rest }
// ⏸️
```

desugars to something like

```temper
class C(public let ordinal: Int, public name: String) {
  public static @enumMember let A = new C(0, "A");
  public static @enumMember let B = new C(1, "B");
  public static @enumMember let C = new C(2, "C");

  private constructor(ordinal: Int, name: String) {
    this.ordinal = ordinal;
    this.name = name;
  }

  rest
}
// ⏸️
```

Backends should make a best effort to translate classes with
enum members to their language's idiomatic equivalent.

TODO: Document how Temper enum types relate to types per backend.

### `fn`
The `fn` keyword is used to define function **values** and [Function Types](types.md#function-types).
Contrast this with [`let`](#let) which is used to **declare** functions

TODO: more explanation needed.

### `for` loop
`for (initializer; condition; increment) { body }` is a [simple `for` loops](#simple-for-loops).
`for (let one of many) { body }` is a [for...of loop](#forof-loop).

#### simple `for` loops
First it executes the *initializer*, once only.

Then it repeatedly executes the *condition* which must be a [*Boolean*](#boolean) expression.
The first time the condition evaluates to [*false*](#false), the loop exits.
After each time the condition evaluates to [*true*](#true), the *increment* is evaluated
and control transfers back to the body.

An unlabelled [`break` statement](#break-statement) in the body exits the loop.
An unlabelled [`continue` statement](#continue-statement) in the body jumps back to the increment and then to
the condition.

```temper
for (var i = 0; i < 3; ++i) {
  console.log(i.toString());
}
//!outputs "0"
//!outputs "1"
//!outputs "2"
// ✅
```

Unlike in some other languages, curly brackets (`{...}`) are **required** around the body.

```temper
for (var i = 0; i < 3; ++i)
  console.log(i.toString()) // Curly brackets missing
// ❌ Expected function type, but got Function!, __flowInit has not been declared!, No type for subject of .toString!, Expected subtype of String, but got Invalid!
```

So a `for` loop is roughly equivalent to

```temper
{ <-- // Block establishes scope for variables defined in initializer
  initializer;
  while (condition) {
    body;
    // <-- `continue` in body goes here
    increment;
  }
} // <-- `break` in body goes here
// ⏸️
```

except that, as noted, [`continue` statement](#continue-statement)s in body do not skip over the increment.

The initializer may declare variables that are scoped to the loop.

```temper
let s = "declared-before-loop";
for (var s = "declared-in-loop-initializer"; true;) {
  console.log(s); //!outputs "declared-in-loop-initializer"
  break;
}
// The use of `s` here refers to the top-level `s`,not the loop's `s`;
// the loop variable is only visible within the loop.
console.log(s); //!outputs "declared-before-loop"
// ✅
```

#### for...of loop

*For...of* loops allow iterating over each value in some group of values.

```temper
for (let x of ["a", "b", "c"]) {
  console.log(x);
}
//!outputs "a"
//!outputs "b"
//!outputs "c"
// ✅
```

The parts of a for...of loop are:

- The *declaration*.  `let x` in the example above.
- The *source* of elements.  The list `["a", "b", "c"]` above.
- The *body*.  `{ console.log(x) }` above.

A for...of loop is syntactic sugar for a call to the `forEach` method.
(for...of simply turns one construct into another).
The example above is equivalent to the `.forEach` call below.

```temper
["a", "b", "c"].forEach { x =>
  console.log(x);
}
//!outputs "a"
//!outputs "b"
//!outputs "c"
// ✅
```

In both cases, jumps like [`break` statement](#break-statement), [`continue` statement](#continue-statement), and
[`return`](#return) may appear in the body when the body is a lexical block
provided directly to `forEach` and the `forEach` implementation uses
[`@inlineUnrealizedGoal` decorator](#inlineunrealizedgoal-decorator) as [`List.forEach`](types.md#listforeach) does.

<!-- TODO(mikesamuel): make for..of snippets below that are inert valid. -->

```temper
for (let x of ["a", "b", "c", "d", "e"]) {
  if (x == "b") { continue }
  console.log(x);
  if (x == "d") { break }
}
//!outputs "a"
//!outputs "c"
//!outputs "d"
// ⏸️
```

Unlike some other languages, a bare name is not allowed instead of a declaration
in a for...of loop.

```temper
var x;
for (x of ["a", "b", "c") {
  console.log(x)
}
// ❌ Expected a TopLevel here!
```

### `if`
`if` allows branching on a [*Boolean*](#boolean) predicate.

```temper
if (true) {
  console.log("Runs");
}
if (false) {
  console.log("Does not run");
}
//!outputs "Runs"
// ✅
```

An `if` may include an `else` block which is run when the predicate is [*false*](#false).

```temper
if (false) {
  console.log("Does not run")
} else {
  console.log("Runs")
}
if (true) {
  console.log("Runs")
} else {
  console.log("Does not run")
}
//!outputs ["Runs", "Runs"]
// ✅
```

An `else` can change to another `if` statement.

```temper
let twoPlusTwo = 2 + 2;
if (twoPlusTwo == 3) {
  console.log("Does not run")
} else if (twoPlusTwo == 4) {
  console.log("Runs")
} else {
  console.log("Does not run")
}
//!outputs "Runs"
// ✅
```

Temper is an expression language, so `if` may be used outside an expression context.

```temper
let x = (if (true) { 42 } else { 0 });
x == 42
// ✅
```

In some syntactically similar languages, you can skip the brackets (`{`...`}`) around
`if` and `else` bodies.
You can't do that in Temper; always put `{`...`}` around the bodies.

```temper
if (true) console.log("Runs"); else console.log("Does not run");
// ❌ Expected a TopLevel here!, Expected function type, but got Function!
```

> [!NOTE]
> The reason for this is that in Temper, control-flow operators like `if` are not
> special syntactic forms.
> They're macros that take blocks as arguments and phrases like `else` and `else if`
> are named parameters to that macro which also must be passed blocks.

Unlike C, conditions must be [*Boolean*](types.md#class-boolean) or a sub-type.

```temper
if (0) { console.log("truthy") } else { console.log("falsey") }
// ❌ Expected value of type Boolean not Int32!
```

### `import`
The `import` function, in conjunction with [`export`](#export),
allows connecting multiple Temper modules together.

One module may `export` a symbol.

```temper
export let world = "Earth";
// ⏸️
```

and another may import it

```temper
let { world } = import("./earth");
console.log("Hello, ${ world }!"); //!outputs "Hello, Earth!"
// ⏸️
```

As shown above, when importing from files under the same *work root*,
use a relative file path: `"."`, `".."`, or one starting with `"./"` or
`"../"`.
This allows multiple co-compiled libraries to link to one another.

A module may also be imported whole, as a namespace: `let NAME =
import(PATH);` binds `NAME`, and `NAME.member` is the module's export
`member`, in code and in types, just as destructuring would import it:

```temper
let temporal = import("std/temporal");
let d: temporal.Date = { year: 2024, month: 2, day: 29 };
console.log(d.toString()); //!outputs "2024-02-29"
// ✅
```

A namespace exists only while compiling: code that uses one translates
exactly as code that destructures the same names. So it's an error to use
`NAME` other than before `.member` (as a value, an argument, or a type),
to name a member the module doesn't export, or to export, type, or
`var` a namespace. A namespace is declared at a module's top level, and
a local of the same name shadows it, as any name. A class is constructed
through one as `new temporal.Date(2024, 2, 29)`; only a namespace may
qualify the class after `new`.

A bare `import("./tests");` statement, with nothing destructured, only
makes that module part of the library, as for a module of tests that
nothing else imports. It brings in no names, so the module it names may
import the one naming it.

Import specifiers always use the URL file separator (`/`), regardless
of whether compilation happens on an operating-system like Linux which
uses the same separator or on Windows which uses `\\`.

To link to separately compiled libraries:

1. start with the library name
2. followed by a separator: `/`
3. followed by the path to the source file relative to the library root
   using the URL file separator

```temper
// Temper's standard library uses the name `std`.
// The temporal module includes `class Date` and other time-related
// types and functions.
let { Date } = import("std/temporal");

let d: Date = { year: 2023, month: 12, day: 13 };
console.log(d.toString()); //!outputs "2023-12-13"
// ✅
```

### `interface`
Defines an abstract `interface` type.
Interface types may define abstract properties but may not define constructors or backed
properties.
Interface's properties may be overridden by backed properties in a [`class`](#class)
subtype.

See also [`class`](#class) for details and examples of type declaration syntax.

Source: [*TypeDefinitionMacro.kt*](https://github.com/temperlang/temper/blob/kotlin-final/frontend/src/commonMain/kotlin/lang/temper/frontend/TypeDefinitionMacro.kt), in Temper's previous compiler.

#### *SubType* `extends` *SuperType*

The *extends* keyword expresses that the thing to the left is a subtype of
the thing to the right.

It's used in two different contexts.

First, when defining types.

```temper
interface SuperType {}
class SubType extends SuperType {}

// An instance of a subtype may be assigned to a variable
// whose type is the supertype.
let x: SuperType = new SubType();
// ✅
```

Second, when declaring a type variable.
The below defines an upper bound: the type variable may only bind to types
that are subtypes of the upper bound.

```temper
let f<T extends Listed<String>>(x: T): T { x }
// ✅
```

When more than one super-type is extended, use
[type intersection](#intersection-type-bound-) syntax (`&`).

```temper
interface I {}
interface J {}

class C extends I & J {}

let f<T extends I & J>(x: T): T { x }
// T extends I and T extends J
// ✅
```

### `let`
The word `let` is used to define names for both variables and functions.

#### Defining variables

`let`, [`var`](#var-decorator), and [`const`](#const-decorator) are used to
define variables.

- `let x` defines a variable within the innermost containing block with the name `x`.
  It may be assigned once.

  ```temper
  let x = "initial-value";
  x == "initial-value"
  // ✅
  ```

  but it may not be re-assigned.

  ```temper
  let x = "initial-value";
  x = "new-value";
  // ❌ x is reassigned after build-user-docs/build/snippet/scoping/examples/snippet.md+386-401 but is not declared `var` at build-user-docs/build/snippet/scoping/examples/snippet.md+378-401!
  ```

- `const x` is equivalent to `@const let x`.

  ```temper
  const x = "initial-value";
  x == "initial-value"
  // ✅
  ```

- `var x` is equivalent to `@var let x` and defines a variable that may be re-assigned after
  being initialized.

  ```temper
  var x = "initial-value";
  x = "new-value"; // Re-assigned
  x == "new-value"
  // ✅
  ```

Unlike in JavaScript, `var` is scoped the same way as `let`.

```temper
var i = "outer-var-i";
do {
  var i = "inner-var-i";
  console.log("inside  block ${i}"); //!outputs "inside  block inner-var-i"
}
console.log("outside block ${i}"); //!outputs "outside block outer-var-i"
// ✅
```

Names can be re-used.  A declared name does not mask a name declared in a containing scope that
appear **before** it, but do mask uses that **appear** after it.

```temper
let i = "outer-i";
do {
  let j = i; // The `i` in the initializer refers to the innermost earlier declaration.
  let i = "inner-i";
  console.log("Inside : j=${j}, i=${i}");
  //!outputs "Inside : j=outer-i, i=inner-i"
}
console.log("Outside: i=${i}");
//!outputs "Outside: i=outer-i"
// ✅
```

#### Defining functions
To define a function, use `let`, but with an argument list and body.

```temper
// Declare a function named `f`
let f(): String { "foo" }
// and call it
console.log(f()); //!outputs "foo"
// ✅
```

The syntax for function values is similar, but uses [`fn`](#fn) instead of `let`.
A function value may have a name, so that it may recursively call itself, but that name is
not visible outside the function expression.

<!-- known: I10 -->
```temper
// A function "declaration"
let f(): String { "function-declaration" }
// A function expression's name does not affect uses of that name outside itself
fn f(): String { "function-expression" }
console.log(f()); //!outputs "function-declaration"
// ✅
```

> **Note:** Temper doesn't support named function expressions yet ("Use of builtin fn is malformed") ([inconsistencies](../inconsistencies.md#i10-examples-that-dont-run-as-marked)).

#### Optional parameters

A parameter may have a default, as `greeting: String? = "Hello"`, which a
call that leaves it out gets. A call that passes `null` for it gets the
default too, even where its type is nullable, so such a parameter is
never `null` inside the function:

```temper
let greet(name: String, greeting: String? = "Hello"): String {
  "${greeting ?? "Hi"}, ${name}!"
}
console.log(greet("Ada")); //!outputs "Hello, Ada!"
console.log(greet("Ada", "Howdy")); //!outputs "Howdy, Ada!"
console.log(greet("Ada", null)); //!outputs "Hello, Ada!"
// ✅
```

### `orelse`
The `orelse` infix operator allows recovering from
[failure to produce a result](#bubble).

The left is executed and if it [does not produce a result](types.md#class-bubble),
then the right is executed and its result is used instead.

```temper
let f(n: Int, d: Int): Int { n / d orelse -1 }
console.log("f(3, 0)=${f(3, 0).toString()}"); //!outputs "f(3, 0)=-1"
console.log("f(6, 2)=${f(6, 2).toString()}"); //!outputs "f(6, 2)=3"
// ✅
```

When you find that you're on a track that cannot produce a usable result, use
`orelse` to switch to a track that may yet succeed or allow a client to
try something else, or recover gracefully.

### *raw*
A tag for raw strings; escape sequences are not expanded.

```temper
// Since this is raw, the \b is treated as two characters
// '\' and 'b', not an ASCII bell.
raw"foo\bar"
// ✅ "foo\\bar"
```

You can have a raw string with quotes by putting it in a
[multi-quoted string](types.md#multi-quoted-strings).

<!-- known: I25 -->
```temper
raw"""
   ""quo\ted"
// ✅ "\u0022quo\\ted\u0022"
```

### `return`

The `return` keyword ends execution of the innermost enclosing function.

```temper
let f(returnEarly: Boolean): Void {
  if (returnEarly) { return } // Exit f without performing the next statement
  console.log("Did not return early");
}
console.log("Return early");        //!outputs "Return early"
f(true);                      // outputs nothing
console.log("Do not return early"); //!outputs "Do not return early"
f(false);                     //!outputs "Did not return early"
// ✅
```

`return` may be followed by an expression to specify the result value.

```temper
let answer(): Int { return 42; }
answer() == 42
// ✅
```

#### Implied `return`s

You can leave out a `return` if the type is `Void` or the
terminal statements in a function body are not in a loop and
should be the output.

```temper
let yesOrNo(b: Boolean): String {
  if (b) {
    "yes" // Implicitly returned
  } else {
    "no"  // me too
  }
}

console.log(yesOrNo(true));  //!outputs "yes"
console.log(yesOrNo(false)); //!outputs "no"
// ✅
```

*Terminal statements* are those statements that may be executed
just before control leaves the function body.

### `rgx`
Constructs a compiled regex object from a regex literal.

```temper
// These all do the same thing.
let regex1 = rgx"[ab]c";
let regex2 = /[ab]c/;
let regex3 = new Sequence([
  new CodeSet([new CodePoints("ab")]),
  new CodePoints("c"),
]).compiled();
// ⏸️
```

### `test`
Define a test case. Any module that defines tests is considered to be a
test module rather than a production module.

```temper
test("something important") {
  // Provide the given message if the assertion value is false.
  assert(1 + 1 == 2) { "the sky is falling" }
}
// ⏸️
```

### `when`
You can check when an expression matches types or values.

```temper
interface I {}
class A(public message: String) extends I {}
class B extends I {}
class C extends I {}
class D extends I {}

let nameOfI(x: I): String {
  when (x) {
    is A -> "A ${x.message}"; // Auto cast for single `is` type.
    is B, is C -> "B or C";
    else -> do {
      let a = nameOfI(new A("at all"));
      let b = nameOfI(new B());
      "not ${a} or ${b}"
    }
  }
}

console.log(nameOfI(new A("here"))); //!outputs "A here"
console.log(nameOfI(new B())); //!outputs "B or C"
console.log(nameOfI(new D())); //!outputs "not A at all or B or C"

console.log(
  when (2) {
    0 -> "none";
    1, 2, 3 -> "little";
    else -> "lots or negative";
  }
); //!outputs "little"
// ✅
```

### `while` loop
`while (condition) { body }` repeatedly executes the *condition* until it yields
[*false*](#false), and after each [*true*](#true) result of the condition,
executes the *body*.

An unlabelled [`break` statement](#break-statement) in the body exits the loop.
An unlabelled [`continue` statement](#continue-statement) in the body jumps back to the condition.

```temper
var i = 0;
while (i < 3) {
  console.log((i++).toString());
}
//!outputs "0"
//!outputs "1"
//!outputs "2"
// ✅
```

Unlike in some other languages, curly brackets (`{...}`) are **required** around the body.

```temper
var i = 0;
while (i < 3)
  console.log((i++).toString()) // Curly brackets missing
// ❌ Expected function type, but got Function!
```

### `&&`
`a && b` performs a short-circuiting [logical-and] of its arguments as in C.
By short-circuiting, we mean that if `a` is [*false*](#false), then `b` is never evaluated,
so you may assume that `a` is [*true*](#true) when writing `b`.

| a     | b     | a && b |
| ----- | ----- | ------ |
| false | false | false  |
| false | true  | false  |
| true  | false | false  |
| true  | true  | true   |

```temper
let isTwiceIsh(numerator: Int, denominator: Int): Void {
  // Here, panic asserts that the division will never fail.
  if (denominator != 0 && numerator / denominator == 2 orelse panic()) {
    console.log("yes");
  } else {
    console.log("no");
  }
}
isTwiceIsh(4, 2); //!outputs "yes"
isTwiceIsh(3, 1); //!outputs "no"
isTwiceIsh(1, 0); //!outputs "no"
// -ish might be doing a lot of work.
// ✅
```

[logical-and]: https://en.wikipedia.org/wiki/Logical_conjunction#Truth_table

### `=>` definition

TODO: Do we need this shorthand for function value/type construction?
TODO: Does it need more testing?  Issue #1549

### Null-coalescing `??`
Infix `??` allows choosing a default value when the subject is null.

```temper
let prod(i: Int, j: Int?): Int { i * (j ?? 1) }
prod(2, 3)    == 6 &&
prod(2, null) == 2
// ✅
```

### `@`
Decorators like `@Foo` allow adapting or adjusting the behavior of definitions.

TODO: write me

#### Legacy decorators
To make Temper more readable for people familiar with other languages, some decorators don't need
an `@` character before them.

The following *modifying words* are converted to decorations when followed by an identifier or
keyword token:

- `abstract`
- [`const`](#const-decorator)
- [`export`](#export)
- `final`
- `native`
- [`private`](#private-visibility)
- [`protected`](#protected-visibility)
- [`public`](#public-visibility)
= [`sealed`](#sealed-type-modifier)
- [`static`](#static-decorator)
- [`var`](#var-decorator)
- `volatile`

Additionally, some decorators imply the word `let`:
`var` and `const`, when not followed by `let` imply `let`.

```temper
do {
  @var let i = 1;
  i += 10;
  console.log(i.toString()); //!outputs "11"
}
do {
  var i = 1;
  i += 10;
  console.log(i.toString()); //!outputs "11"
}
// ✅
```

### `@auto` decorator
The *\@auto* decorator currently applies to a class or sealed interface,
indicating that the specified operator should be automatically implemented
for this type. In the future, it might be possible to specify on an
individual method for a specific operator.

Currently, only the `==` operator is supported via `@auto("==")`, but we
expect to expand on this in the future. The currently generated method name
is *eq*. Some mechanism for supplying multiple operators is expected in the
future.

It is an error to use `@auto("==")` even on a sealed interface that has other
than class or sealed interface descendents. Inheriting `@auto("==")` from
multiple sealed ancestor interface branches is also an error. Further, if a
manual `@operator("==")` method exists for a type, no automatic
implementation is injected.

Automatic `==` implementation is done by comparing all concrete properties in
an unspecified order using `==` on each. Used on a sealed interface, it adds
a pure virtual method to the interface and a concrete method on all leaf
classes that checks both the type and its concrete properties.

Manual `@operator("==")` is available if `@auto("==")` has unwanted behavior
for some type.

### `@connected` decorator
Connect types, members, and functions to backend code. Each backend defines
specific conventions for connected backend code. Internal backend translation
logic can also key on the QName of connected entities.

A connected function may have a Temper body, its reference implementation,
and list the backends that have connected code for it. Other backends, and the
interpreter, use the body.

```temper
@connected(["cpp", "rust"])
export let foldCase(c: Int): Int {
  // ... pure Temper ...
}
// ⏸️
```

A listed backend must supply connected code, and `--no-connected` makes every
backend use bodies wherever they exist. Without a list, every backend must
supply connected code, and the body, if any, serves only the interpreter.
An empty list, as in `@connected([])`, names no backend with backend code,
so every backend uses the body.

A connected class can have a Temper implementation the same way, with
bodies for all of its methods. Either every method has a body, or none
does. Its methods can't have default arguments, since connected code
implements the same methods.

### `@const` decorator
`@const` may decorate a [`let`](#let) declaration.
It is a [legacy decorator](#legacy-decorators).

By default, names declared by [`let`](#let) may be assigned once per
entry into their containing scope.  `@const` is provided for familiarity with
readers of other languages.

Contrast `@const` with [`@var`](#var-decorator).

### `@export`
The `@export` decorator keyword makes the declaration available outside the module.
Exported declarations may be linked to via [`import`](#import).

It is a [legacy decorator](#legacy-decorators), so the `export` keyword
is shorthand for the `@export` decorator.

Exported symbols are also visible within the module following normal scoping rules.

```temper
export let x = 3;
x == 3
// ✅
```

### `@extension` decorator
The *\@extension* decorator applies to a function declaration that should
be callable as if it were an instance member of a separately defined type.

(See also [`@staticExtension` decorator](#staticextension-decorator) which allows calling
a function as if it were a *static* member of a separately defined type)

For example, the String type does not have an *isPalindrome* method,
but languages like C# and Kotlin allow you to define extension methods:
functions that you can import and call *as if* they were methods of
the extended type.

```temper
@extension("isPalindrome")
let stringIsPalindrome(s: String): Boolean {
  var i = String.begin;
  var j = s.end;
  while (i < j) {
    j = s.prev(j);
    if (s[i] != s[j]) { return false }
    i = s.next(i);
  }
  return true
}

// Equivalent calls
"step on no pets".isPalindrome() &&
stringIsPalindrome("step on no pets")
// ✅
```

The quoted string `"isPalindrome"` above specifies that the name
for the function when used via method call syntax, but an extension
function may still be called via its regular name: *stringIsPalindrome*
above.

Note that the extension function must have at least one required, positional
parameter and that parameter is the *this* argument, the subject when
called via `subject.methodName(other, args)` syntax.

Type arguments in a method-style call, `subject.methodName<T>(args)`,
are the function's type parameters that the subject's type doesn't
name, as a method's own, or all of them, in order, as for a call of the
function by its name.

Backends translate these as if the *\@extension* decorator were
ignored: an ordinary function, under its regular name, and a
method-style call as a call of it, even in target languages that have
extensions of their own, such as C#.

Core's generic helpers for lists and maps are instance extensions, as
an interface's methods, which implementations may override, can't be
generic: `ls.map(f)` calls core's `listedMap`, and `ls.map<String>(f)`
gives `map`'s own type parameter. So are `reduceFrom`,
`reduceFromIndex`, `toListWith`, and `toListBuilderWith`. For now,
these are called only method-style, given only their own type
arguments.

### `@fun` decorator
Marks an interface declaration as a [Functional interface](types.md#functional-interface).

For example:

```temper
@fun interface Predicate<T>(x: T): Boolean;
// ⏸️
```

### `@imu` decorator
Marker for types that must be deeply immutable.

### `@in` deprecated variance notation
`@in` may decorate a type formal declaration to specify covariant (input) variance.

It is going away.

### `@inlineUnrealizedGoal` decorator
`@inlineUnrealizedGoal` may decorate a function parameter declaration.

An *unrealized goal* is a jump like a [`break` statement](#break-statement), [`continue` statement](#continue-statement),
[`return`](#return), or [`bubble()`](#bubble) that crosses from a
[block lambda](syntax.md#syntax-for-blocklambda) into the containing function.

Inlining a call is the act of taking the called function's body and ensuring that names
and parameters have the same meaning as if it was called.

Inlining the call to `.forEach` below while also inlining uses of the block lambda into
`f`'s body allows connecting the `return` goals to `f`'s body.

<!-- known: I10 -->
```temper
let f(ls: List<Int>): Boolean {
  ls.forEach { x => // Here's a block lambda
    if (x == 2) {
      console.log("Found 2!");
      // This `return` should exit `f`
      // but is in a different function.
      return true; // UNREALIZED
    }
  };
  false
}
f([2]) //!outputs "Found 2!"
// ✅
```

> **Note:** Temper doesn't inline block lambdas to let them `return` from the enclosing function, so reports "Expected subtype of Void, but got Boolean" ([inconsistencies](../inconsistencies.md#i10-examples-that-dont-run-as-marked)).

After inlining, that code is equivalent to this where the `.forEach` call has been
turned into a `while` loop (as specified by `List.forEach`) and the block lambda is now in
the loop body.

```temper
let f(ls: List<Int>): Boolean {
  let n = ls.length;
  var i = 0;
  while (i < n) {
    let x = ls[i]; // parameter from block lambda
    i += 1;
    // Here's where the block lambda is situated.
    do {
      if (x == 2) {
        console.log("Found 2!");
        // This `return` wants to exit `f`
        // but is in a different function.
        return true; // UNREALIZED
      }
    }
  }
  false
}
f([2]) //!outputs "Found 2!"
// ✅
```

It does come with limitations:

- `@inlineUnrealizedGoal` applies to parameters with a function type.
- The containing method or function, hereafter the "callee", must not use any
  [`@private` visibility](#private-visibility) APIs so that uses of them can be moved.
- The callee must not be an overridable method.
- The callee must call any decorated parameter at one lexical call site.
  Inlining a function multiple times can lead to explosions in code size.
- The callee must not use any decorated parameter as an r-value;
  it may not delegate calling the block lambda.
- For a decorated parameter to be inlined, it must be a block lambda

Since inlining involves copying code, it is only appropriate when the method
is a fairly thin wrapper around a single call to the function or functions
with this decoration.

### `@json` decorator

The `@json` decorator applies to a type definition.
It auto-generates *JsonAdapter* (see *std/json*) implementations
to make it easy to encode instances of the type to and from JSON.

There are a number of strategies used to generate encoders and
decoders outlined below.

> **Note:** Temper doesn't generate adapters for generic `@json`
> types, nor for std's `Date`, yet.

#### The concrete class strategy

A class type's default encoded form is just a JSON object with
a JSON property per backed field.
*IntPoint* below encode to JSON like `{"x":1,"y":2}` because its
*x* and *y* properties' values encode to JSON numbers.

```temper
let {
  InterchangeContext,
  NullInterchangeContext,
  JsonTextProducer,
  parseJson,
} = import("std/json");

// Define a simple class with JSON interop via `@json`
@json class IntPoint(
  public x: Int,
  public y: Int,
) {}

// A builder for a JSON string.
let jsonTextProducer = new JsonTextProducer();
// Encode a point
IntPoint.jsonAdapter().encodeToJson(
  new IntPoint(1, 2),
  jsonTextProducer
);
//!outputs "{\"x\":1,\"y\":2}"
console.log(jsonTextProducer.toJsonString());

// Decode a point
let jsonSyntaxTree = parseJson("{\"x\":3,\"y\":4}");
let p = IntPoint.jsonAdapter()
    .decodeFromJson(jsonSyntaxTree, NullInterchangeContext.instance);

console.log("x is ${p.x.toString()}, y is ${p.y.toString()}");
//!outputs "x is 3, y is 4"
// ✅
```

#### The custom JSON adapter strategy

If a type, whether it's a class or interface type,
already has encoding and decoding functions, then none are
auto-generated.

```temper
let {
  InterchangeContext,
  JsonInt,
  JsonNumeric,
  JsonProducer,
  JsonSyntaxTree,
  JsonTextProducer,
} = import("std/json");

@json class IntWrapper(
  public i: Int,
) {
  public encodeToJson(p: JsonProducer): Void {
    p.int32Value(i);
  }

  public static decodeFromJson(t: JsonSyntaxTree, ic: InterchangeContext): IntWrapper throws Bubble {
    new IntWrapper((t as JsonNumeric).asInt32())
  }
}

let p = new JsonTextProducer();
// IntWrapper got a static jsonAdapter() method but not encoders and decoders.
IntWrapper.jsonAdapter().encodeToJson(new IntWrapper(123), p);

"123" == p.toJsonString()
// ✅
```

#### Sealed interface strategy

For sealed interfaces, we generate adapters that delegate to adapters for
the appropriate subtype.

```temper
let {
  JsonTextProducer,
  listJsonAdapter,
} = import("std/json");

@json sealed interface Animal {}

@json class Cat(
  public meowCount: Int,
) extends Animal {}

@json class Dog(
  public hydrantsSniffed: Int,
) extends Animal {}

let ls: List<Animal> = [new Cat(11), new Dog(111)];

let p = new JsonTextProducer();
List.jsonAdapter(Animal.jsonAdapter()).encodeToJson(ls, p);
p.toJsonString() == "[{\"meowCount\":11},{\"hydrantsSniffed\":111}]"
// ✅
```

#### Adapting JSON for generic types

If a type is generic, its *jsonAdapter* static method
might require an adapter for its type arguments.

```temper
let {
  JsonAdapter,
  JsonTextProducer,
  listJsonAdapter,
  int32JsonAdapter,
} = import("std/json");

let intListAdapter: JsonAdapter<List<Int>> =
    List.jsonAdapter(Int.jsonAdapter());

let p = new JsonTextProducer();
intListAdapter.encodeToJson([123], p);

"[123]" == p.toJsonString()
// ✅
```

#### Solving Ambiguity with extra properties

Sometimes, ambiguity is unavoidable in the JSON structure of two
related types, like two subtypes of the same sealed interface.

[`@jsonExtra` decorator](#jsonextra-decorator) allows adding extra JSON properties
with known values that do not correspond to a class constructor
parameter but which the sealed interface strategy can use.

### `@jsonExtra` decorator

Allows customizing code generation for [`@json` decorator](#json-decorator) support.

This allows avoiding ambiguity when two variants of a sealed interface
have otherwise similar JSON structure in their JSON wire formats.

```temper
let {
  InterchangeContext,
  NullInterchangeContext,
  parseJson,
} = import("std/json");

@json
sealed interface FooRequest {}

// Without any disambiguation both variants of FooRequest
// would have a wire form like
//     { "name": "..." }

// Foo requests are tagged with a version: `"v": 2.0`.
@json @jsonExtra("v", 2.0)
class FooRequestVersion2(
  public name: String,
) extends FooRequest {}

// But servers still need to support a deprecated, legacy format
// tagged with `"v": 1.0`
@json @jsonExtra("v", 1.0)
class FooRequestVersion1(
  public name: String,
) extends FooRequest {}

// Neither class has a constructor input named "v", but it's
// required in the JSON because of the @jsonExtra decorations.
let jsonSyntaxTreeV1 = parseJson(
  """
  "{"v": 1.0, "name": "a"}
);
let jsonSyntaxTreeV2 = parseJson(
  """
  "{"v": 2.0, "name": "a"}
);

FooRequest.jsonAdapter()
  .decodeFromJson(jsonSyntaxTreeV1, NullInterchangeContext.instance)
  is FooRequestVersion1
&&
FooRequest.jsonAdapter()
  .decodeFromJson(jsonSyntaxTreeV2, NullInterchangeContext.instance)
  is FooRequestVersion2
// ✅
```

### `@jsonName` decorator

Specifies the JSON property name for a Temper property name.

Without the `@jsonName` decoration below, the JSON form would be
`{"messageText":"Hello, World!"}` but as seen below, the JSON
output has dash case.

```temper
@json
class Message(
  @jsonName("message-text")
  public messageText: String
) {}

let { JsonTextProducer } = import("std/json");
let jsonOut = new JsonTextProducer();
Message.jsonAdapter()
  .encodeToJson(new Message("Hello, World!"), jsonOut);

//!outputs "{\"message-text\":\"Hello, World!\"}"
console.log(jsonOut.toJsonString());
// ✅
```

### `@keep` decorator
Mark a non-exported item for being treated as reachable so it's kept in
translations for backend connected code to access.
See also [`@keepTest` decorator](#keeptest-decorator).

### `@keepTest` decorator
Acts like [`@keep` decorator](#keep-decorator) but for test reachability.

`@mayDowncastTo(true)` on a type definition indicates that the type's
values must be distinguishable via runtime type information (RTTI) from other
types' values, as when casting down from a less specific type to a more
specific type.

It is safe to use [`is`](#is) or [`as`](#as) with distinguishable types.

`@mayDowncastTo(false)` indicates the opposite.

If a type declaration has neither, then the following rules are used to
decide whether a type is safe to use.

- If a type is a direct subtype of a [sealed](#sealed-type-modifier) type,
  and there is a path from the static type of the expression to that type
  via sealed types, then it is distinguishable.
- Otherwise, if the type is connected, it is assumed not distinguishable.
  Backends may connect multiple Temper types to the same backend type.
  For example, JavaScript and Lua backends connect both [*Int32*](types.md#class-int32)
  and [*Float64*](types.md#class-float64) to their builtin *number* type.
  Perl and PHP blur the distinction between numeric, boolean, and string types.
- Otherwise, it is assumed distinguishable.

### `@noProperty` decorator
The `@noProperty` decorator may apply to constructor inputs that appear inside
the parenthetical part of a [builtin/class] declaration to indicate that the
constructor input does not correspond to a backed property.

```temper
class C(
  // constructor arguments here
  @noProperty let x: Int,
) {
  // Non constructor properties here must be initialized.
  // Their initializers may reference any constructor input including @noProperty inputs.
  public y: Int = x + 1;
}

// This works.
let c = { x: 1 };
console.log(c.y.toString()); //!outputs "2"
// ✅
```

### `@operator` decorator
The *\@operator* decorator applies to a function or method declaration
that implements an operator.

For example, maybe you're defining an arbitrary precision integer type,
*BigInteger*, and its class includes methods that implement arithmetic.
You could add `@operator` decorators to the methods and then adding two
*BigIntegers* (and a *BigInteger* and a [*Int32*](types.md#class-int32)) would
delegate semantics to that method.

The string argument must be a valid operator text or an [Operator specifier](#operator-specifier).
If an operator text, then the operator kind is inferred from the decorated function
or methods arity (2 means infix, 1 means prefix).

```temper
/**
 * I just need an example of an operator definition.
 * This mirrors electrical-engineering notation.
 */
@operator("+") // Infix plus operator because arity is 2
let addingTwoBooleans(a: Boolean, b: Boolean): Boolean {
  a || b
}

true + false
// ⏸️
```

This decorator may be applied multiple times.

#### Operator specifier

An operator specifier specifies a kind of operator.

The text of the operator surrounded by underscores where operands are allowed.
This allows for describing these different kinds of operators, as shown below
where `+` is the sample operator.

- Infix: `_+_`, the operator punctuation (or word) appears between two operands.
- Prefix: `+_`, the operator appears before its operand, like `-` for negation.
- Postfix: `_+`, the operator appears after its operand, like `++` for post-increment
  or superscript `T` for matrix transpose.

### `@out` deprecated variance notation
`@out` may decorate a type formal declaration to specify covariant (input) variance.

It is going away.

### `@overload` decorator
Gives an overload name to a function or class member. Temper respects
overloads. Some backend languages also might, so backends are permitted
to generate multiple entities with the same overload. See each backend
for details.

```temper
class ConvertingStringBuilder {
    public content: StringBuilder = new StringBuilder();

    @overload("append")
    public appendInt32(int: Int32): Void {
        content.append(int.toString());
    }

    @overload("append")
    public appendString(string: String): Void {
        content.append(string);
    }
}

let builder = new ConvertingStringBuilder();
builder.append("Hi ");
builder.append(5);
builder.appendString("!"); // individualized calls also work
builder.content.toString() == "Hi 5!"
// ✅
```

For now, only explicit methods are allowed for overloads.
Constructors, properties, and top-level functions are unsupported.

No actual function or method with the overload name itself must be
present.

TODO Should extension methods have the same prohibition?
Also detail interaction between extension methods and overloads.

### `@partialImu` decorator
Marker for types that must be deeply immutable when their actual type
parameters are deeply immutable.

### `@private` visibility

`@private` decorates type declaration members. It indicates that
the member is an implementation detail, meant for internal use.
Private members entail no backwards compatibility commitments.

`@private` is a [legacy decorator](#legacy-decorators) so
the `@` is optional.

A private member may be used from within its class definition,
via `this.` or on another instance of the same class.

```temper
class C {
  private s: String = "Hello, World!";
  public say(): Void { console.log(this.s); }
}

new C().say() //!outputs "Hello, World!"
// ✅
```

But a private member may not be read from outside the class.

```temper
class C {
  private s: String = "Hello, World!";
}
console.log(new C().s); // ERROR
// ❌ Member s defined in C not publicly accessible!
```

See also [`public` visibility](#public-visibility).

### Protected visibility

To write: visibility of type members.

### Public visibility

To write: visibility of type members.

See also [`private` visibility](#private-visibility).

See also [`@private` visibility](#private-visibility)

### `sealed` type modifier
Marks an interface type as sealed; only types declared in the same module
(the same directory's source files) may [extend](#subtype-extends-supertype) it.

Sealed types are important because the Temper translator can assume there are
no direct subtypes that it does not know about.

For example, when [matching](#when) a sealed type value, if
there is an [`is`](#is) clause for each known subtype,
there is no need for an `else` clause.

> [!NOTE]
> **Backwards compatibility note**
>
> Adding a subtype to an existing sealed type may break code that
> uses the old version of the sealed type. [`when`](#when) expressions
> are at risk, but so is other code that assumes
> "none of those subtypes, therefore, this subtype."

Backends should translate user-defined sealed types to [sum type]s
where available, and may insert tagged union tag field where needed.

[sum type]: https://en.wikipedia.org/wiki/Tagged_union

### `@static` decorator
`@static` may decorate type members, and indicates that the member is associated
with the containing type, not with any instance of that type.

It is a [legacy decorator](#legacy-decorators), so the `static` keyword
is shorthand for the `@static` decorator.

Static members are accessed via the type name, dot, member name.

```temper
class C {
  public static let foo = "foo";
}
C.foo == "foo"
// ✅
```

Unlike in Java, static members are not accessible from an instance.

```temper
class C {
  public static let foo = "foo";
}
(new C()).foo == "foo"  // .foo is not declared on an instance of C
// ❌ Member foo defined in C incompatible with usage!
```

### `@staticExtension` decorator
The *\@staticExtension* decorator applies to a function declaration and allows
calling it as if it were a [static](#static-decorator) method of a
separately defined type.

Class and interface types may be extended with [`@static` decorator](#static-decorator)
methods, by passing a type expression as the second argument to *\@extension*.

[*Float64*](types.md#class-float64) contains no static member *tau*, but when the declaration
of *float64Tau* below is in scope, it can be accessed using *Type.member* syntax.

```temper
@staticExtension(Float64, "tau")
let float64Tau(): Float64 { Float64.pi * 2.0 }

// That function can be called with static method syntax and
// regular function call syntax.
Float64.tau() == float64Tau()
// ✅
```

Types may only be extended with static methods in this way, not properties.
Note that parentheses are required after `Float64.tau()` but not after `Float64.pi`.

As with [instance extensions](#extension-decorator), the `Type.method()`
syntax only works in scopes that include an import of the extension function or
its original definition.

### `@test` decorator
`@test` may decorate a function, indicating the function is a test.
The intention is that this not be used directly but through a `test`
macro that builds an appropriate function.

### `@var` decorator
`@var` may decorate a [`let`](#let) declaration.
It is a [legacy decorator](#legacy-decorators).

By default, names declared by [`let`](#let) may be assigned once per
entry into their containing scope, but `@var` allows re-assigning values after
initialization.

Contrast `@var` with [`@const`](#const-decorator).
See [examples](#defining-variables) of how `var` contrasts with
[`let`](#let) and [`const`](#const-decorator).

Square brackets are used to access an element of a list, map, or other container.

Abbreviated syntax `container[key]` is shorthand for `container.get[key]`.

```temper
let ls = ["zero", "one"];
console.log("[0] -> ${ls[0]}, [1] -> ${ls[1]}");         //!outputs "[0] -> zero, [1] -> one"
console.log("[0] -> ${ls.get(0)}, [1] -> ${ls.get(1)}"); //!outputs "[0] -> zero, [1] -> one"
// ✅
```

This syntax may also appear to the left of `=`, as in `container[key] = newValue`.
That's shorthand for `container.set(key, newValue)`.

```temper
let ls = ["zero", "one"].toListBuilder();
console.log("[0] -> ${ls[0]}, [1] -> ${ls[1]}");         //!outputs "[0] -> zero, [1] -> one"
ls[1] = "ONE";
console.log("[0] -> ${ls[0]}, [1] -> ${ls[1]}");         //!outputs "[0] -> zero, [1] -> ONE"
ls.set(0, "Zero");
console.log("[0] -> ${ls[0]}, [1] -> ${ls[1]}");         //!outputs "[0] -> Zero, [1] -> ONE"
// ✅
```

### `||`
`a || b` performs a short-circuiting [logical-or] of its arguments as in C.
By short-circuiting, we mean that if `a` is [*true*](#true), then `b` is never evaluated,
so you may assume that `a` is [*false*](#false) when writing `b`.

| a     | b     | a \|\| b |
| ----- | ----- | -------- |
| false | false | false    |
| false | true  | true     |
| true  | false | true     |
| true  | true  | true     |

```temper
let isTwiceIsh(numerator: Int, denominator: Int): Void {
  // Here, panic asserts that the division will never fail.
  if (denominator == 0 || numerator / denominator == 2 orelse panic()) {
    console.log("yes");
  } else {
    console.log("no");
  }
}
isTwiceIsh(4, 2); //!outputs "yes"
isTwiceIsh(0, 0); //!outputs "yes"
isTwiceIsh(3, 1); //!outputs "no"
// -ish might be doing a lot of work.
// ✅
```

[logical-or]: https://en.wikipedia.org/wiki/Logical_disjunction#Semantics

## Null-chaining `?.`
Infix `?.` considers the right-hand side only when the left side is non-null.
It otherwise evaluates to null.

```temper
let maybeLength(a: String?): Int? {
  // Because of non-null inference, `a.end` is ok here.
  // Inferred non-null on the right side works only for simple name references,
  // not for nested expressions on the left side of `?.`.
  a?.countBetween(String.begin, a.end)
}
maybeLength("ab") == 2 &&
maybeLength(null) == null
// ✅
```

## Keywords
Some words have special syntactic meanings in Temper, and so may not be used as identifiers.

```ebnf
ReservedWord := "nym" | "builtins" | "this" | "super"
```

There are fewer of these than in many other languages, as many constructs are provided via
[Builtin Functions and Constants](#constants).  The Temper language will probably evolve to disallow masking certain
builtins, effectively reserving those words from use as declaration names.

### `builtins` keyword
It's a direct reference to a name in the module's context environment record.

This bypasses any in-module declaration of the same name.

<!-- known: I10 -->
```temper
class MyConsole extends Console {
  public log(str: String): Void {
    builtins.console.log("[ ${str} ]");
  }
}
let console = new MyConsole();

builtins.console.log("builtins.console"); //!outputs "builtins.console"
console.log("console");                   //!outputs "[ console ]"
// ✅
```

> **Note:** Temper doesn't declare the `Console` type, or `builtins.console`, yet ([inconsistencies](../inconsistencies.md#i10-examples-that-dont-run-as-marked)).

`builtins` must be followed by a `.`.  Unlike `globalThis` in JavaScript, `builtins` is not
an object; it's a special syntactic form.

```temper
let a = builtins;
// ❌ Expected a TopLevel here!
```

### <code>nym\`...\`</code> quoted name
Used to escape names.

    nym`...`

parses to the name with the unescaped text between the quotes so

    nym`nym`

can be used to get the name with text "nym" through the parser.

This is meant to allow names in Temper thar directly correspond to names in other systems,
including dash-case names that are widely used in databases and the web platform.

### `super` keyword

`super.` is reserved syntax.
We have yet to figure out what our goals are w.r.t. super calls
and whether it makes sense to do a super call without specifying which interface's
implementation to use given we have no plans to allow subtyping of class types.

### `this` keyword
`this` is a way to refer to the enclosing instance.
So inside a method in a [`class`](#class) or [`interface`](#interface) definition,
you can use `this` to refer to the instance on which the method was called.

```temper
class C(public who: String) {
  public sayHello(): Void {
    console.log("Hello, ${this.who}!");
  }
}
let c = new C("World");
c.sayHello() //!outputs "Hello, World!"
// ✅
```

`this` cannot be used outside a type definition.

```temper
class C { /* Inside the type definition */ }
// Outside
this
// ❌ `this` may only appear inside a type definition!
```
