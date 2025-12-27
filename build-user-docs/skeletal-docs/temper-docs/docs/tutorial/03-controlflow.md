---
title: Control Flow
temper-extract: false
---

# Control Flow

We [previously](02-interface.md) looked at classes, functions, and interfaces.
Now we actually get into programming logic. And we'll still use the Temper REPL
for now:

```sh
temper repl
```

We can also start with a variation of our types from before. You can paste this
all at once. The REPL will see it as multiple inputs:

```temper
interface Shape {
  area(): Float64;
  perimeter(): Float64;
  toString(): String;
}

class Rectangle(
  public width: Float64,
  public height: Float64,
) extends Shape {
  public area(): Float64 { width * height }
  public perimeter(): Float64 { 2.0 * (width + height) }
  public toString(): String {
    "Rectangle of size ${width} x ${height}"
  }
}

let tau = 2.0 * Float64.pi;

class Circle(
  public radius: Float64,
) extends Shape {
  public area(): Float64 { 0.5 * tau * radius * radius }
  public perimeter(): Float64 { tau * radius }
  public toString(): String { "Circle of radius ${radius}" }
}
```


## Anonymous function blocks

Let's also get ourselves some shapes to work with:

```temper
let shapes: List<Shape> = [{ width: 1.5, height: 0.5 }, { radius: 2.0 }];
```

And as we showed before, we can convert them all to strings using the [`map`
method of `List`][snippet/type/Listed/method/map] (and see [issue#21] for some
on return type inference):

```temper
// In this case for now, we don't yet infer block return type `String`.
$ shapes.map { (shape): String => shape.toString() }
interactive#5: ["Rectangle of size 1.5 x 0.5","Circle of radius 2.0"]
```

That `{ (shape): String => ... }` syntax differs from JS arrow functions to let
it work well as a trailing block. It's also just shorthand for an explicit
anonymous function:

```temper
// We can also spell out parameter type `fn (shape: Shape): ...` if we want.
$ shapes.map(fn (shape): String { shape.toString() })
interactive#6: ["Rectangle of size 1.5 x 0.5","Circle of radius 2.0"]
```

And top-level `let` function definitions are also shorthand for `fn` assignment:

```temper
// These two forms are roughly equivalent.
let areaPerPerimeter(shape: Shape): Float64 {
  shape.area() / shape.perimeter()
}
// This version just doesn't carry the name metadata on the function object.
let areaPerPerimeter = fn (shape: Shape): Float64 {
  shape.area() / shape.perimeter()
}
```

You can also call back to function parameters in your own functions:

```temper
let mapAreas(
  shapes: List<Shape>,
  transform: fn (Float64): Float64,
): List<Float64> {
  // Note the call to parameter `transform` in here.
  shapes.map { (shape): Float64 => transform(shape.area()) }
}
```

We can use that function as follows, where we also get return type inference
here from context and can omit the parens around `(area) =>` because of that:

```temper
$ mapAreas(shapes) { area => 2.0 * area }
interactive#14: [1.5,25.132741228718345]
```

Or in other words, `fn` is very versatile in Temper, even though the `fn`
keyword is often unseen outside of function types.

In fact, anonymous implied `fn` blocks are so pervasive in Temper that the
internal handling of most common control structures is through such function
blocks. Internal control macros such as `if` or `while` transform them into
standard flow control for backend language translation. And in the future, we
plan to support user-defined macros that can also transform function blocks in
custom ways (see [issue#8]). But for now, they already can easily be used as
function callbacks, such as seen in the `mapAreas` example above.


## Loops

Well, we've had a lot of fun with functional code above. Let's try out some
imperative looping instead, beginning with C-style `for` loops:

```temper
// Type `Int` can be inferred here, but be explicit so we see the type.
for (var i: Int = 0; i < shapes.length; i += 1) {
  // Don't use `console.log` for stdout, just for exploration or logging.
  console.log(shapes[i].toString());
}
```

Here's the output, given our earlier list of shapes:

```
Rectangle of size 1.5 x 0.5
Circle of radius 2.0
interactive#15: void
```

First, yes Temper has C-style `for` loops at present, and they might stay. We're
currently working out a good iterator strategy and expect to support some "for
each" imperative looping capability in the future (see [issue#20]). Second, as
mentioned above, `console.log` isn't intended for library code but for exploring
behavior and for logging. Third, yes, Temper does have ints.

Like `Float64`, the `Int` type in Temper also has set number of bits, 32
specifically. The official name for this type is `Int32`, but alias `Int` is
also available because this type is so commonly used. There's also an `Int64`
type for when you need more bits. Both types are signed and wrap on overflow.
You can specify an `Int64` literal with suffix `i64`, such as `1i64`. You can't
mix numeric types in arithmetic.

But back to imperative loops, in addition to `for`, Temper also has `while`
loops. For now, let's just convert the loop above to `while`:

```temper
// This `do` block eases putting multiple statements in one REPL input.
do {
  // Remember, `var` lets us reassign variables.
  var i = 0;
  while (i < shapes.length) {
    console.log(shapes[i].toString());
    i += 1; // `++i` and `i++` also exist
  }
}
```

While standalone `do` blocks might be new, there's no real surprise with `while`
loop behavior. Temper also has `do ... while` loops. But to be more surprising
maybe, let's rewrite the above using explicit `fn` function blocks as described
earlier:

```temper
do(fn () {
  var i = 0;
  // Without parameters, `()` after `fn` is optional.
  while(i < shapes.length, fn {
    console.log(shapes[i].toString());
    i += 1;
  });
});
```

Using implied trailing function blocks or explicit `fn` acts the same. Going
into Temper's built-in `while` macro, one is just syntactic sugar for the other.
Both translate to a `while` loop in Java, JS, or Python. In most cases, trailing
blocks are clearer to read and write.

To look closer at Temper's handling of various syntax forms, try using the
`describe` REPL function mentioned earlier but with
`"frontend.disAmbiguateStage.after"` to see an earlier stage of processing. Or
try the `translate` function, or enter `help()` for more info.


## Branches

Temper has if-else chains (which also are syntactic sugar for macro calls with
`fn` blocks):

```temper
shapes.map { (shape): String =>
  let area = shape.area();
  // Yes, if-else also is an expression with a value.
  if (area < 1.0) {
    "small"
  } else if (area < 10.0) {
    "medium"
  } else {
    "large"
  }
}
// Result: ["small","large"]
```

In place of `switch`, Temper has `when` blocks:

```temper
let lengthSuffix(length: Int): String {
  when (length) {
    1 -> "";
    0, 2 -> "s";
    // Numbers less than 0 or greater than 2 exist?
    else -> "s???";
  }
}
"I have ${shapes.length} shape${lengthSuffix(shapes.length)}"
// Result: "I have 2 shapes"
```

You can also match types by using `is` (and see [issue#25] on exhaustiveness
checking):

```temper
let squaredExtent(shape: Shape): Float64 {
  when (shape) {
    // On the right side of `->`, the `shape` variable is auto downcast.
    is Circle -> shape.radius * shape.radius;
    // Use a `do` block for multiple statements in the result expression.
    is Rectangle -> do {
      let { width, height } = shape;
      (width * width + height * height) / 4.0
    }
    // Because another type could be added later.
    else -> NaN;
  }
}
shapes.map { (shape): Float64 => squaredExtent(shape) }
// Result: [0.625,4.0]
```

You can include both `is` type checks with plain value checks in a `when`
block. And yes, we do intend to support full pattern matching in the future.
Just not done yet (see [issue#3]).

**WARNING:** On the topic of type casts, not all backend languages have full
type tag information in all cases. For example, `Float64` and `Int` are
mostly indistinguishable on JS. Such cases are marked `@mayDowncastTo(false)` in
their definitions.


## Handling cases that bubble

There's one other important form of control flow in Temper that we should
discuss, and that's Temper's variation on error handling. Temper needs
to support languages whose standard idioms use either return values or exception
handling. To support this, it has a simple `Bubble` type. When unhandled, it
manifests in the REPL with a simple `fail` message:

```temper
$ shapes[0]
interactive#30: {class: Rectangle__0, width: 1.5, height: 0.5}
$ shapes[1]
interactive#31: {class: Circle__0, radius: 2.0}
$ shapes[2]
interactive#32: fail
```

That last one failed because there are only 2 shapes in our array. And using bad
indices causes a panic that can't be recovered in Temper. Backend languages
might be able to recover, depending on the idiomatic representation of panic in
each.

However, Temper does have a way to handle failures based on declared `Bubble`,
which typically applies to failable operations that can't perhaps be easily or
efficiently checked in advance:

```temper
// The value after `orelse` is used when the lefthand side is `Bubble`.
$ shapes[0] as Circle orelse ({ radius: 1.0 })
interactive#33: {class: Circle__0, radius: 1.0}
```

We can use this in our own functions as well. For example, we adjust our length
suffix function from earlier:

```temper
// Note the `throws Bubble` on the type here.
let lengthSuffix(length: Int): String throws Bubble {
  when (length) {
    1 -> "";
    0, 2 -> "s";
    // Just weird out because we're expecting lengths here.
    else -> bubble();
  }
}
[0, 1, 2, -1].map { (n): String => lengthSuffix(n) }
// Result: fail
```

In this case, the whole map call fails because of a single `Bubble` inside of
it. Let's handle that case:

```temper
$ [0, 1, 2, -1].map { (n): String => lengthSuffix(n) orelse "s???" }
interactive#36: ["s","","s","s???"]
```

Some details on `Bubble` propagation still need to be worked out on backends,
and some of the handling can be improved, but the basic mechanism is in place.

Also, we currently have no capabilities to handle resources other than memory,
so we don't yet have a way to manage automatic closing of resources (see
[issue#24]). As for memory management itself, we'll discuss that later. It gets
interesting across backend languages, and Temper needs to handle that.

But for now, let's move out of the REPL and into source files and libraries.


## Links
- **NEXT**: [Modules & Libraries](04-modlib.md)
- **PREVIOUS**: [Interfaces](02-interface.md)
- Reference: [Builtins](../reference/builtins.md),
  [Types](../reference/types.md)
