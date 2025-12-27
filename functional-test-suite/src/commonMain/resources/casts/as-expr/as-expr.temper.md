# Cast Functional Test

The `as` operator allows casting, but it's only for casting down from sealed
interfaces or from nullable to non-nullable. Not all backend languages support
arbitrary casting of types.

    interface Apple { public get name(): String; }
    class Fuji extends Apple { public get name(): String { "Fuji" } }
    class Gala extends Apple { public get name(): String { "Gala" } }

We can store a Fuji in a variable of type *Apple*.

    do {
      var x: Apple?;
      x = new Fuji();

And retrieve it with `.as` by specifying the type that
to cast to.  As with generic methods, the type goes in
angle brackets. Should be fine as Apple or Fuji.

      console.log((x as Apple).name);
      console.log((x as Fuji).name);

prints

```log
Fuji
Fuji
```

Casts are safe.  If a value is not of the right type,
then the cast won't succeed.

      x = new Gala(); // Override the value in `x` with a different value.
      console.log((x as Fuji).name orelse "Cast failed!");

We can also cast from AnyValue to either interface or class types.

      let y: AnyValue? = x;
      console.log((y as Apple).name orelse "Cast from AnyValue failed!");
      console.log((y as Gala).name orelse "Cast from AnyValue failed!");
      console.log((y as Fuji).name orelse "Cast from AnyValue failed!");

Also try null safety.

      x = null;
      console.log(do { x as Apple; "Tasty nothing." } orelse "Was null!");
    }

prints

```log
Cast failed!
Gala
Gala
Cast from AnyValue failed!
Was null!
```

## Upcasting Builtins

This isn't very interesting, but it's allowed. And we currently seem to optimize
out explicit casts to `AnyValue`, so instead assign to typed declarations.

    export let thing0: AnyValue = false;
    export let thing1: AnyValue = 1;
    export let thing2: AnyValue = "two";
    export let thing3: AnyValue = 3.14;

And export them to be sure they aren't trimmed out.
