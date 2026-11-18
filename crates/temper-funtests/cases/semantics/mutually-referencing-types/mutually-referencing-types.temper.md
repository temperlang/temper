# Mutually Referencing Types

Temper prevents reference cycles, but allows types to reference one another
without any explicit forward declaration mechanism like C++ prototypes.

Here, `class Foo` and `class Bar` not only reference one another, but may
each have properties of the other type.

    class Foo(
      public x: Bar?,
    ) {}

    class Bar(
      public x: Foo?,
    ) {}

Since `null` is a possible value for `x` in each we have a
construction base case.

    let emptyFoo = new Foo(null);
    let emptyBar = new Bar(null);

And we can use those to create non-empty instances.

    let nonEmptyFoo = new Foo(emptyBar);
    let nonEmptyBar = new Bar(emptyFoo);
    let stuffedBar = new Bar(nonEmptyFoo);

And we can use type tests to check content.

    console.log(
      "emptyFoo.x has Foo -> ${
        (emptyFoo.x != null).toString()
      }"
    );
    console.log(
      "nonEmptyFoo.x has Foo -> ${
        (nonEmptyFoo.x != null).toString()
      }"
    );

That produces:

```log
emptyFoo.x has Foo -> false
nonEmptyFoo.x has Foo -> true
```
