# Modules

A library of eight modules: this one, `helpers`, and `helpers/deep`,
which `helpers` imports too, and which run in that order, each after
those it imports; `core`, whose C# namespace would hide the runtime's
class `Core`; `client`, which imports this one, as a library's tests
may, so this one can't run it first; and `machine`, which uses
`state`'s classes, a function of `parts/regex`, and std's regex
module. C# namespaces would hide those classes there (`State` is a
namespace too, and `Parts` one above `parts/regex`'s), and both regex
modules' classes of functions are `RegexGlobal`. This module's own
`Engine` shares its name with `state`'s, which C++ keeps apart.

Imports name functions, a `let`, and a `var`, which an import copies
when it runs, as the interpreter and Kotlin's backends read it, so
later writes don't show here; an alias; a module's own declaration
with an imported one's name; and an instance extension, which
`n.doubled()` calls.

    let { twice as double, base, bump, calls, intDoubled } = import("./helpers");
    let { greet } = import("./helpers/deep");
    let { share } = import("./core");
    let { describe } = import("./machine");

    class Engine(public name: String) {}

This module's `base` is its own, and not exported; the helpers' is
imported as `helperBase`.

    let { base as helperBase } = import("./helpers");
    let base = 1;

    export let sum(n: Int): Int {
      double(n) + helperBase + base
    }

    console.log(greet("modules"));
    console.log("sum ${sum(3).toString()}");
    console.log("share ${share(7, 2).toString()} ${21.doubled()}");
    console.log("${new Engine("own").name}, ${describe()}");
    bump();
    bump();
    console.log("calls ${calls.toString()}");

```log
hello, helpers from deep
hello, modules from deep
sum 17
share 3 42
own, state 1 engine 4 parts 5
calls 0
```

    console.log("calls now ${bump().toString()}");

```log
calls now 3
```

`core`'s `Ratio` has an `@operator("==")` method, which `==` calls
here too, with a nullable side or not.

    let { Ratio, ratioOf } = import("./core");
    let half = new Ratio(1, 2);
    console.log("${half == new Ratio(2, 4)} ${ratioOf(3, 6) == half} ${half != ratioOf(1, 0)}");

```log
true true true
```
