# String Building

## Operations

We can append strings or code points.

    let builder = new StringBuilder();
    builder.append("Hi ");
    builder.appendCodePoint(char"🌏");

We can also append string ranges, with the idea that we save creating a
substring.

    let source = "Wow!! Neato!";

We could use regex for this or add simple find methods to core, but for now just
hardcode offsets.

    let bangIndex = source.step(String.begin, 3);
    let spaceIndex = source.step(bangIndex, 2);
    builder.appendBetween(source, bangIndex, spaceIndex);

Also append a calculated string rather than only string literals.

    let built = builder.toString();
    builder.append("\n");
    builder.append(built);

See what we got.

    console.log(builder.toString());

```log
Hi 🌏!!
Hi 🌏!!
```

## Bad Scalar Values

Code points need to be both valid Unicode code points and also Unicode scalar
values, meaning no surrogates.

    builder.appendCodePoint(0x110000) orelse console.log("High code blocked.");
    builder.appendCodePoint(0xD800) orelse console.log("Surrogate blocked.");

And we shouldn't have changed content.

    console.log(builder.toString());

```log
High code blocked.
Surrogate blocked.
Hi 🌏!!
Hi 🌏!!
```
