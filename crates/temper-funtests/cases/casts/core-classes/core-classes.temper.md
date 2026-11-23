# Checks Among Core's Values

`is` and `as` may check for a class from `AnyValue`, and each backend
tells its values from core's, whatever the host makes those. (Core's
own types the host supplies are `@connected`, so they can't be checked
for from `AnyValue`.)

    class Thing {}

    let describe(x: AnyValue): String {
      if (x is Thing) {
        return "thing";
      }
      do {
        x as Thing;
        "thing, as"
      } orelse "other"
    }

    let bits = new DenseBitVector(8);
    let ints: List<Int> = [1, 2];
    console.log(describe(new Thing()));
    console.log(describe(new Pair<String, Int>("a", 1)));
    console.log(describe(bits));
    console.log(describe("s"));
    console.log(describe(1));
    console.log(describe(ints));
    console.log(describe(new Map<String, Int>([new Pair<String, Int>("m", 2)])));
    console.log(describe(new ListBuilder<Int>()));
    console.log(describe(new Deque<Int>()));

prints

```log
thing
other
other
other
other
other
other
other
other
```

A local alias of a type is the type.

    let isThing(x: AnyValue): Boolean {
      let T = Thing;
      x is T
    }
    console.log(isThing(new Thing()).toString());
    console.log(isThing(bits).toString());

```log
true
false
```
