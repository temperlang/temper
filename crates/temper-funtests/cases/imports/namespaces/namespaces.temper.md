# Import Namespaces Functional Test

A module imported whole, `let NAME = import(PATH)`, is a namespace: each
`NAME.member` is that module's export, just as a destructuring import
names it, and translates the same way.

    let shapes = import("./shapes");

Its values and functions:

    console.log(shapes.unit.toString());
    console.log(shapes.twice(21).toString());

Its types, in annotations and type arguments, its classes'
constructors, and their static members:

    let box: shapes.Box<Int> = new (shapes.Box<Int>)(3);
    let squares: List<shapes.Shape> = [
      new (shapes.Square)(4),
      new (shapes.Square)(shapes.unit),
      shapes.Square.sized(5),
    ];
    for (let s of squares) {
      console.log("area ${s.area()}");
    }

A class here may extend an interface there, and `is` checks its classes:

    class Circle(public r: Int) extends shapes.Shape {
      public area(): Int { 3 * r * r }
    }
    let mixed: List<shapes.Shape> = [new Circle(2), new (shapes.Square)(2)];
    for (let s of mixed) {
      when (s) {
        is shapes.Square -> console.log("a square of ${s.area()}");
        else -> console.log("something else of ${s.area()}");
      }
    }

A local of the same name shadows the namespace, as any name:

    let bump(shapes: Int): Int { shapes + 1 }
    console.log(bump(box.value).toString());

Expected output:

```log
1
42
area 16
area 1
area 25
something else of 12
a square of 4
4
```
