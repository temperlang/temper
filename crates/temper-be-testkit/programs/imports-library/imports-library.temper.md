# Imports a library

Imports another library by its name, `modules`, and one of its modules
by its path there; a library's tests import std's `testing` the same way.
And from `renamed`, exports whose names backends' namings change, each
spelled as `renamed`'s own naming spelled it.

    let { sum } = import("modules");
    let { twice, base } = import("modules/helpers");

    export let both(n: Int): Int { sum(n) + twice(n) + base }

    console.log("both ${both(2).toString()}");

Another library's class's `@operator("==")` method, which `==` calls,
with a nullable side too.

    let { Ratio, ratioOf } = import("modules/core");
    let third = new Ratio(1, 3);
    console.log("ratio ${third == new Ratio(3, 9)} ${ratioOf(2, 6) == third} ${ratioOf(1, 0) == third}");

    let { lambda, native, Checked, Builtins } = import("renamed");
    let checked = new Checked(lambda(native));
    console.log(
      "renamed ${checked.delete().toString()} ${checked.local.toString()} ${checked.end.toString()}"
    );
    let b = new Builtins(4);
    console.log(
      "builtins ${b.hash.toString()} ${b.get(1).toString()} ${b.set(3).toString()} ${b.map(5).toString()} ${b.format()} ${b.int.toString()} ${b.later().toString()}"
    );
    console.log(b.print());

A spread of another library's `@data` class, which C++ builds by its
`make`, as it does any class of another library's.

    let { Spot } = import("modules/state");
    let far = { ...new Spot(3, 4), x: 9 };
    console.log("spot far ${far.x} ${far.y}");

```log
hello, helpers from deep
hello, modules from deep
sum 17
share 3 42
own, state 1 engine 4 parts 5
calls 0
calls now 3
true true true
spot 1 5
both 29
ratio true true false
renamed 6 5 3
builtins 4 5 6 6 #4 40 41
print 7
spot far 9 4
```
