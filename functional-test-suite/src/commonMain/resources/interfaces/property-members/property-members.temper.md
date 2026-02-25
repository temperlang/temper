# Property Members in Interfaces

Interfaces are allowed to define properties.
When a target language's abstract, heritable type declaration does
not allow for properties, the translator should instead produce
abstract getters and setters.

    export interface I {
      public x: String;
    }

And to complicate things, add an intermediate interface we can use.

    export interface J extends I {}

Interface types can be bounds on generic type parameters.
Since the below reads `.x`, it needs the type bound.

    let leastX<IT extends I>(a: IT, b: IT): IT {
      if (a.x < b.x) { a } else { b }
    }

An interface sub-type can be used as an explicit type parameter.

    class C(
      // And make this `var` to ensure we don't mishandle readonly inheritance.
      public var x: String,
    ) extends J {}

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

## Detour on bounds for type parameters on types

The test above already includes type parameter bounds, but we don't test bounds
on type parameters on types elsewhere, so include that here.

    class D<T extends I>(public thing: T) {}

Use both the top and intermediate interfaces here to prove we can. And store
them in intermediate variables to ensure the types are being handled correctly.

    let di = new D<I>(a as I orelse panic()); // TODO Remove cast once types delivered.
    let dj = new D<J>(a as J orelse panic()); // TODO Remove cast once types delivered.
    console.log("Generically, x is ${di.thing.x}.");
    console.log("Genericallier, x is still ${dj.thing.x}.");

```log
Generically, x is foo.
Genericallier, x is still foo.
```

## Detour on generic sealed interfaces

Also because it's in the ballpark, test generic sealed interfaces here.

    sealed interface SI<T extends I> {
      public get thing(): T;
    }

    class SSub<T extends I>(public thing: T) extends SI<T> {}

    let si: SI<C> = new SSub(a);
    console.log("Sealed thing is ${si.thing.x}.");

```log
Sealed thing is foo.
```
