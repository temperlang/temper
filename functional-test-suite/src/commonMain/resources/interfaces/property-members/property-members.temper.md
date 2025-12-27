# Property Members in Interfaces

Interfaces are allowed to define properties.
When a target language's abstract, heritable type declaration does
not allow for properties, the translator should instead produce
abstract getters and setters.

    export interface I {
      public x: String;
    }

Interface types can be bounds on generic type parameters.
Since the below reads `.x`, it needs the type bound.

    let leastX<IT extends I>(a: IT, b: IT): IT {
      if (a.x < b.x) { a } else { b }
    }

An interface sub-type can be used as an explicit type parameter.

    class C(
      // And make this `var` to ensure we don't mishandle readonly inheritance.
      public var x: String,
    ) extends I {}

    let a = { x: "foo" };
    let b = { x: "bar" };

    console.log("leastX of ${a.x} and ${b.x} is ${leastX<C>(a, b).x}.");

```log
leastX of foo and bar is bar.
```

Casting a C up to an I still allows reading x.

    let i: I = a;
    console.log("As an I, a.x is ${i.x}.");

```log
As an I, a.x is foo.
```
