# List Covariance Functional Test

`List` is covariant, so a `List<Square>` can be used where a `List<Shape>`
is wanted. Backends whose list types are invariant have to convert at each
place that can happen.

    export interface Shape { area(): Int; }
    export class Square(public side: Int) extends Shape {
      public area(): Int { side * side }
    }

    export let total(shapes: List<Shape>): Int {
      var sum = 0;
      for (var i = 0; i < shapes.length; ++i) { sum += shapes[i].area(); }
      sum
    }
    export let totalOrMinusOne(shapes: List<Shape>?): Int {
      if (shapes == null) { -1 } else { total(shapes) }
    }
    export let totalNested(lists: List<List<Shape>>): Int {
      var sum = 0;
      for (var i = 0; i < lists.length; ++i) { sum += total(lists[i]); }
      sum
    }

    let squares: List<Square> = [new Square(1), new Square(2)];
    console.log("argument ${total(squares)}");

    export let literalReturned(n: Int): List<Shape> { [new Square(n), new Square(n + 1)] }
    console.log("returned literal ${total(literalReturned(2))}");

    export let passedThrough(sq: List<Square>): List<Shape> { sq }
    console.log("returned variable ${total(passedThrough(squares))}");

    let topLevel: List<Shape> = [new Square(3)];
    console.log("top-level let ${total(topLevel)}");

    let topLevelFromVariable: List<Shape> = squares;
    console.log("top-level let from variable ${total(topLevelFromVariable)}");

    export let reassigned(): Int {
      var ls: List<Shape> = [];
      ls = squares;
      total(ls)
    }
    console.log("local assignment ${reassigned()}");

    console.log("nullable parameter ${totalOrMinusOne(squares)}");

    export let maybeSquares(b: Boolean): List<Square>? { if (b) { squares } else { null } }
    console.log("nullable to nullable ${totalOrMinusOne(maybeSquares(true))} ${totalOrMinusOne(maybeSquares(false))}");

    let nested: List<List<Square>> = [squares, squares];
    console.log("nested ${totalNested(nested)}");

    export class Holder(public var shapes: List<Shape>) {
      public set(sq: List<Square>): Void { shapes = sq; }
    }
    let holder = new Holder(squares);
    console.log("constructor argument ${total(holder.shapes)}");
    holder.set([new Square(4)]);
    console.log("property assignment ${total(holder.shapes)}");

The element type may also widen to `AnyValue`, which some backends box.

    export let countAll(values: List<AnyValue>): Int { values.length }
    let ints: List<Int> = [1, 2, 3];
    console.log("any value ${countAll(ints)}");

Expected output:

```log
argument 5
returned literal 13
returned variable 5
top-level let 9
top-level let from variable 5
local assignment 5
nullable parameter 5
nullable to nullable 5 -1
nested 10
constructor argument 5
property assignment 16
any value 3
```
