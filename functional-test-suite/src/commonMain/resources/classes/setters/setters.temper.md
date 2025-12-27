# Setters Functional Test

As part of its object model Temper has property setters

    class Foo(
      private var backing: String,
    ) {
      public get bar(): String {
        return backing;
      }
      public set bar(value: String) {
        backing = value;
      }
    }

So getting works like

    console.log(new Foo("initial").bar)

printing

```log
initial
```

And

    let foo = new Foo("initial");
    foo.bar = "next";
    console.log(foo.bar);

prints

```log
next
```

A setter can be `private` to the class

    class Bar(
      private var backing: String,
    ) {
      public get bar(): String {
        return backing;
      }
      private set bar(value: String) {
        backing = value;
      }

      public let usingSet(value: String): Void {
        // some validation/business logic could go here
        bar = value;
      }
    }

by which

    let bar = new Bar("initial");
    bar.usingSet("next");
    console.log(bar.bar);

prints

```log
next
```

## Interfaces with setters

This had an issue in Python at least. The generated code was trying to reference
a missing getter. Export to make sure the code gets included.

    export interface Whatever {
      public get something(): Int;
      public set something(blah: Int): Void;
    }

    export class ConcreteWhatever(
      public var something: Int,
    ) extends Whatever {
    }

    let what: Whatever = new ConcreteWhatever(4);
    what.something = 5;
    console.log("something set to ${what.something}");

```log
something set to 5
```
