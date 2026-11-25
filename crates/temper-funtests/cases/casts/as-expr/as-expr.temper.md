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

A cast that fails panics, so where a value may be of another type,
check with `is` first, which narrows it.

      x = new Gala(); // Override the value in `x` with a different value.
      console.log(if (x is Fuji) { (x as Fuji).name } else { "Cast failed!" });

We can also cast from AnyValue to either interface or class types.

      let y: AnyValue? = x;
      console.log((y as Apple).name);
      console.log((y as Gala).name);

Also try null safety.

      x = null;
      console.log(if (x is Apple) { "Tasty nothing." } else { "Was null!" });
    }

prints

```log
Cast failed!
Gala
Gala
Was null!
```

`is` checks what `as` casts, by the same rule, so it too works from
`AnyValue`, for classes and interfaces, and narrows the value it checks.
Neither checks for core's types, as `StringBuilder`, which backends may
represent by types that aren't distinct.

    let describe(v: AnyValue?): String {
      if (v is Fuji) {
        return "Fuji ${v.name}";
      }
      when (v) {
        is Apple -> v.name;
        else -> "other";
      }
    }
    do {
      console.log(describe(new Fuji()));
      console.log(describe(new Gala()));
      console.log(describe(thing2));
      console.log(describe(thing1));
      console.log(describe(null));
    }

prints

```log
Fuji Fuji
Gala
other
other
other
```

## Upcasting Builtins

This isn't very interesting, but it's allowed. And we currently seem to optimize
out explicit casts to `AnyValue`, so instead assign to typed declarations.

    export let thing0: AnyValue = false;
    export let thing1: AnyValue = 1;
    export let thing2: AnyValue = "two";
    export let thing3: AnyValue = 3.14;

And export them to be sure they aren't trimmed out.
