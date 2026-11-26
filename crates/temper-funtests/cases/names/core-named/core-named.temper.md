# Names Like Core Functional Test

`core` and `builtins` are ordinary names: a library may have a module
named `core`, and a variable too, while `import("core")` still names
Temper's core.

    let { greet, share } = import("./core");
    let core = greet("a module named core");
    console.log(core);
    console.log("shares ${share(7, 2)} ${share(1, 0)}");
    var builtins = 1;
    builtins += 1;
    console.log("builtins is ${builtins}");

Core, imported as a namespace under another name, reaches a core name
that this module declares too:

    let temper = import("core");
    class String(public n: Int) {}
    let s: temper.String = "core's String";
    let mine = new String(3);
    console.log("${s}, and mine holds ${mine.n}");

A parameter may be named `core` too:

    let twice(core: Int): Int { core * 2 }
    console.log(twice(21).toString());

Expected output:

```log
hello from a module named core
shares 3 0
builtins is 2
core's String, and mine holds 3
42
```
