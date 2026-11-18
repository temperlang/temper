# Interfaces

We [previously](01-classes-and-functions.md) learned about classes and functions. That was
fun. Now let's learn about interfaces, which help us organize related classes.
As before, let's get into the Temper REPL:

```sh
temper repl
# And wait for the `$` prompt.
```

## Organizing types

Let's begin with a variation on our `Rectangle` type from before:

<!-- repl: paste -->
```temper
class Rectangle(
  public width: Float64,
  public height: Float64,
) {
  // Use function call rather than "getter" style for both of these now.
  public area(): Float64 { width * height }
  public perimeter(): Float64 { 2.0 * (width + height) }
}
```

Good enough. Now let's define another shape type. But first let's give ourselves
a useful value:

```temper
// And yeah, simple variables get static type inference.
// But we could say `let tau: Float64 = ...` if we want.
$ let tau = 2.0 * Float64.pi;
interactive#1: void
```

You can also ask the REPL how it sees a numbered input, at one of the compiler's
steps, as the types it worked out:

```console
$ describe(1, "types")
tau: Float64
interactive#2: void
```

That shows that `tau`'s static type was inferred to be `Float64`. Other steps
are `"parsed"`, `"settled"`, `"module"`, and `"tmpl"`; `help()` lists them. And
while we're in the REPL for now, if you enter the same code in a Temper project
with the Temper extension for Visual Studio Code, you can hover over a variable
name to see the inferred type.

But oh yeah, we were going to make another class to go with `Rectangle`. Here it
is:

<!-- repl: paste -->
```temper
class Circle(
  public radius: Float64,
) {
  // Exponent/power operator isn't available yet.
  public area(): Float64 { 0.5 * tau * radius * radius }
  public perimeter(): Float64 { tau * radius }
}
```

Sweet. Now what if we want a list of different kinds of shapes?

<!-- repl: paste -->
```temper
// What's the type here?
let shapes = [{ width: 1.5, height: 0.5 }, { radius: 2.0 }];
```

If we use an earlier Temper release's `describe` on that, we'll see that the inferred type is
`List<Rectangle__0 | Circle__0>`. But that's misleading. Many popular
programming languages that Temper does or could target don't have support for
ad hoc union types. So we can't rely on this, and we plan to restrict outward
support in Temper for ad hoc union types in the future (see [issue#23](https://github.com/temperlang/temper/issues/23)).

## Interfaces

To make a common type for our shapes, let's define an `interface`:

<!-- repl: paste -->
```temper
interface Shape {
  area(): Float64;
  perimeter(): Float64;
  // We can also give default implementations in interfaces.
  toString(): String { "Shape of area ${area()}" }
}
```

Once that's done, we can redefine our earlier classes as extending this
interface:

<!-- repl: paste -->
```temper
class Rectangle(
  public width: Float64,
  public height: Float64,
) extends Shape {
  public area(): Float64 { width * height }
  public perimeter(): Float64 { 2.0 * (width + height) }
  // Skip toString so we can see the default in use.
}
```

For `Circle`, let's override `toString`:

<!-- repl: paste -->
```temper
// And repeating tau here for convenience, in case you started over.
let tau = 2.0 * Float64.pi;
class Circle(
  public radius: Float64,
) extends Shape {
  public area(): Float64 { 0.5 * tau * radius * radius }
  public perimeter(): Float64 { tau * radius }
  // Override default toString to prove we can.
  public toString(): String { "Circle of radius ${radius}" }
}
```

Now we can use `Shape` as a common type for the list:

<!-- repl: paste -->
```temper
let shapes: List<Shape> = [{ width: 1.5, height: 0.5 }, { radius: 2.0 }];
```

Here's an example function that works on lists of shapes:

<!-- repl: paste -->
```temper
let toStrings(shapes: List<Shape>): List<String> {
  // Anonymous function block here! We'll discuss those later.
  shapes.map { (shape): String => shape.toString() }
}
```

And we can call this function on our shapes:

```console
$ toStrings(shapes)
interactive#11: ["Shape of area 0.75","Circle of radius 2.0"]
```

Also, just to make a point, we can't actually construct instances of interfaces:

```console
$ new Shape()
interactive#12:1:5: error: Cannot instantiate abstract type Shape
    new Shape()
        ^^^^^
interactive#12: fail
```

## More on inheritance

So in Temper, classes can extend interfaces. Interfaces can also extend other
interfaces. But classes *can't* extend other classes. Let's try:

```console
// A square is a kind of rectangle, right? Maybe?
$ class Square extends Rectangle {
    // Custom constructors are a thing, even if inheriting from classes isn't.
    public constructor(edgeLength: Float64) {
      // Value init would also work if the properties were in this same class.
      this.width = edgeLength;
      this.height = edgeLength;
    }
  }
interactive#13:1:22: error: Cannot extend concrete type(s) Rectangle
    class Square extends Rectangle {
                         ^^^^^^^^^
interactive#13:5:5: error: Member width defined in Rectangle incompatible with usage
        this.width = edgeLength;
        ^^^^^^^^^^
interactive#13:6:5: error: Member height defined in Rectangle incompatible with usage
        this.height = edgeLength;
        ^^^^^^^^^^^
interactive#13: Square
```

Focus on that first message "Cannot extend concrete type(s) Rectangle".
Remaining errors are from trying to init properties defined in another class. We
plan to improve these other messages in the future (see [issue#12](https://github.com/temperlang/temper/issues/12)).

If we want a square that's also rectangular, one option might be to extract
another interface:

<!-- repl: paste -->
```temper
interface Rectangular extends Shape {
  // Saying just `width: Float64;` confuses Temper right now.
  get width(): Float64;
  get height(): Float64;
  // The confusion at least happens here on usage in default methods.
  area(): Float64 { width * height }
  perimeter(): Float64 { 2.0 * (width + height) }
}
```

Then we can make `Rectangle` and `Square` subtypes of `Rectangular`:

<!-- repl: paste -->
```temper
class Rectangle(
  public width: Float64,
  public height: Float64,
) extends Rectangular {}
```

And we can still use a custom constructor on `Square` if we want:

<!-- repl: paste -->
```temper
class Square extends Rectangular {
  public width: Float64;
  public constructor(edgeLength: Float64) {
    width = edgeLength;
  }
  public get height(): Float64 { width }
  // And might as well optimize perimeter calculation.
  public perimeter(): Float64 { 4.0 * width }
}
```

Now, here are some updated shape lists:

<!-- repl: paste -->
```temper
let boxes: List<Rectangular> = [
  { edgeLength: 1.0 },
  { width: 1.5, height: 0.5 },
];
```

If we look at those `boxes`, we'll see the actually stored properties in the
REPL presentation, with `width` rather than `edgeLength`. Any default textual
representation is backend-specific, however, so if you view an object like this
in Java, JS, or Python, you might see something different than this:

```console
$ boxes
interactive#18: [{class: Square, width: 1.0},{class: Rectangle, width: 1.5, height: 0.5}]
```

Our `boxes` are `Rectangular`, so we can use that abstraction:

<!-- repl: paste -->
```temper
boxes.map { (box): String =>
  "${box.width} x ${box.height}"
}
// Result: ["1.0 x 1.0","1.5 x 0.5"]
```

And meanwhile, our list `toString` conversion function still works as before:

```console
$ let shapes: List<Shape> = [boxes[0], boxes[1], { radius: 2.0 }];
interactive#20: void
$ toStrings(shapes)
interactive#21: ["Shape of area 1.0","Shape of area 0.75","Circle of radius 2.0"]
// Temper `List` is immutable and so is covariant in its item type.
$ toStrings(boxes)
interactive#22: ["Shape of area 1.0","Shape of area 0.75"]
```

So for **hierarchies and abstractions of behavior**, use **interfaces**.
And for **actual instances**, use **classes**.
Anyway, now that we've had fun organizing types and functions, let's
look at control flow and the kinds of logic available in Temper.

## Links
- **NEXT**: [Control Flow](03-control-flow.md)
- **PREVIOUS**: [Classes & Functions](01-classes-and-functions.md)
- Reference: [Builtins](../../language/builtins.md),
  [Types](../../language/types.md)
