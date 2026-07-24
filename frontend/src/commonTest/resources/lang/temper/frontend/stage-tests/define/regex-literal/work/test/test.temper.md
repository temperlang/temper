Some tests below:

- Interpolated string value next to another interpolation. Also test a disappearing empty hole.
- Simple interpolated string value since we can't evaluate regex objects at compile time yet.

Starting off with a simple regex

    let r1 = /a.b*/;

b is not in scope here.

    let r2 = /a.${b}*/;

We don't actually support the following flag syntax at the moment.
That's one of the syntax error messages.

    let r3 = /a.b*/g;
    let b = r3;

And we have a brief interpolation representation from Grammar that's
easyish to build.  It gets changed later.

    let r4 = rgx"a.${b}*";
    let r5 = rgx"a${"."}${b}*${}?";
    let r6 = rgx"a${"."}*";
    let r7 = new Sequence([
      new CodePoints("a"),
      Dot,
      new Repeat(new CodePoints("b"), 0, null),
    ]).compiled();
    let s = "[a]";
    let r8 = rgx".${s}.";
