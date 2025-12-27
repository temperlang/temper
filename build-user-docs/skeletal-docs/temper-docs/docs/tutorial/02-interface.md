---
title: Interfaces
temper-extract: false
---

# Interfaces

We [previously](01-classfun.md) learned about classes and functions. That was
fun. Now let's learn about interfaces, which help us organize related classes.
As before, let's get into the Temper REPL:

```sh
temper repl
# And wait for the `$` prompt.
```

## Organizing types

Let's begin with a variation on our `Rectangle` type from before:

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

You can also get the internal description of a numbered input result after
frontend processing has finished:

```temper
$ describe(1, "frontend.generateCodeStage.after")
Describe interactive#1 @ frontend.generateCodeStage.after
  let return__1 ⦂ Void, @implicit Rectangle__0 ⦂ Type;
  Rectangle__0 = type (Rectangle__0);
  @optionalImport let nym`-repl/i0001//chunk.temper`.tau ⦂ Float64;
  getStatic(Float64, \pi);
  nym`-repl/i0001//chunk.temper`.tau = 6.283185307179586;
  return__1 = void

interactive#2: void
```

In all the above, `let interactive#1.tau ⦂ Float64;` shows that `tau`'s static type was inferred to be `Float64`. And while we're in the REPL for now, if you
enter the same code in a Temper project with the Temper extension for Visual
Studio Code, you can hover over a variable name to see the inferred type.

But oh yeah, we were going to make another class to go with `Rectangle`. Here it
is:

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

```temper
// What's the type here?
let shapes = [{ width: 1.5, height: 0.5 }, { radius: 2.0 }];
```

If we use `describe` on that, we'll see that the inferred type is
`List<Rectangle__0 | Circle__0>`. But that's misleading. Many popular
programming languages that Temper does or could target don't have support for
ad hoc union types. So we can't rely on this, and we plan to restrict outward
support in Temper for ad hoc union types in the future (see [issue#23]).


## Interfaces

To make a common type for our shapes, let's define an `interface`:

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

```temper
let shapes: List<Shape> = [{ width: 1.5, height: 0.5 }, { radius: 2.0 }];
```

Here's an example function that works on lists of shapes:

```temper
let toStrings(shapes: List<Shape>): List<String> {
  // Anonymous function block here! We'll discuss those later.
  shapes.map { (shape): String => shape.toString() }
}
```

And we can call this function on our shapes:

```temper
$ toStrings(shapes)
interactive#9: ["Shape of area 0.75","Circle of radius 2.0"]
```

Also, just to make a point, we can't actually construct instances of interfaces:

```temper
$ new Shape()
1: new Shape()
       ┗━━━┛
[interactive#3:1+4-9]@G: Cannot instantiate abstract type Shape
interactive#13: fail
```


## More on inheritance

So in Temper, classes can extend interfaces. Interfaces can also extend other
interfaces. But classes *can't* extend other classes. Let's try:

```temper
// A square is a kind of rectangle, right? Maybe?
class Square extends Rectangle {
  // Custom constructors are a thing, even if inheriting from classes isn't.
  public constructor(edgeLength: Float64) {
    // Value init would also work if the properties were in this same class.
    this.width = edgeLength;
    this.height = edgeLength;
  }
}
```

Currently, some of the errors we get here are misleading and confusing, but we
do get errors:

```temper
1: lass Square extends Rectangle {
                       ┗━━━━━━━┛
[interactive#42:1+21-30]@S: Cannot extend concrete type(s) Rectangle
5: this.width = edgeLength;
   ┗━━━━━━━━━━━━━━━━━━━━━┛
[interactive#42:5+8-31]@G: Wrong number of arguments.  Expected 3
5: this.width = edgeLength;
                ┗━━━━━━━━┛
[interactive#42:5+21-31]@G: Expected subtype of Rectangle__0, but got Float64
6: this.height = edgeLength;
   ┗━━━━━━━━━━━━━━━━━━━━━━┛
[interactive#42:6+8-32]@G: Wrong number of arguments.  Expected 3
6: this.height = edgeLength;
                 ┗━━━━━━━━┛
[interactive#42:6+22-32]@G: Expected subtype of Rectangle__0, but got Float64
interactive#42: Square__0
```

Focus on that first message "Cannot extend concrete type(s) Rectangle".
Remaining errors are from trying to init properties defined in another class. We
plan to improve these other messages in the future (see [issue#12]).

If we want a square that's also rectangular, one option might be to extract
another interface:

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

```temper
class Rectangle(
  public width: Float64,
  public height: Float64,
) extends Rectangular {}
```

And we can still use a custom constructor on `Square` if we want:

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

```temper
$ boxes
interactive#50: [{class: Square__0, width: 1.0},{class: Rectangle__0, width: 1.5, height: 0.5}]
```

Our `boxes` are `Rectangular`, so we can use that abstraction:

```temper
boxes.map { (box): String =>
  "${box.width} x ${box.height}"
}
// Result: ["1.0 x 1.0","1.5 x 0.5"]
```

And meanwhile, our list `toString` conversion function still works as before:

```temper
$ let shapes: List<Shape> = [boxes[0], boxes[1], { radius: 2.0 }];
interactive#52: void
$ toStrings(shapes)
interactive#53: ["Shape of area 1.0","Shape of area 0.75","Circle of radius 2.0"]
// Temper `List` is immutable and so is covariant in its item type.
$ toStrings(boxes)
interactive#53: ["Shape of area 1.0","Shape of area 0.75"]
```

So for **hierarchies and abstractions of behavior**, use **interfaces**.
And for **actual instances**, use **classes**.
Anyway, now that we've had fun organizing types and functions, let's
look at control flow and the kinds of logic available in Temper.


## Links
- **NEXT**: [Control Flow](03-controlflow.md)
- **PREVIOUS**: [Classes & Functions](01-classfun.md)
- Reference: [Builtins](../reference/builtins.md),
  [Types](../reference/types.md)
