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

## Clear

    builder.clear();  // Now it's empty.
    builder.append("Back to square zero.");

    console.log(builder.toString());

```log
Back to square zero.
```

## Question marks

Runs of question marks stay as written, even where C++ would read them as
trigraphs (`??=`, `??/`, `??>`).

    builder.clear();
    builder.append("a??=b??/c??>d???e");
    let marks = builder.toString();
    console.log(marks);
    console.log("Same: ${marks == "a??=b??/c??>d???e"}");

```log
a??=b??/c??>d???e
Same: true
```

A digit after an escaped character stays a digit, even where C++ writes
that character as an octal escape (`\001`, `\342`), which takes at most
three digits. Building the same text at run time uses no escapes.

    builder.clear();
    builder.append("??");
    builder.appendCodePoint(1) orelse console.log("No U+1.");
    builder.append("0");
    builder.appendCodePoint(0x20AC) orelse console.log("No euro.");
    builder.append("1");
    console.log("Digits after escapes: ${builder.toString() == "??\u{1}0\u{20AC}1"}");

```log
Digits after escapes: true
```


## StringBuilder property

We hit an issue with properties of type StringBuilder on be-rust, so ensure it
works.

    class StringBuilderOwner(
      public builder: StringBuilder,
    ) {}

And even the above technically tests things, but might as well prove it actually
works.

    let owner = new StringBuilderOwner(new StringBuilder());
    owner.builder.append("Hi from inside.");
    console.log(owner.builder.toString());

```log
Hi from inside.
```
