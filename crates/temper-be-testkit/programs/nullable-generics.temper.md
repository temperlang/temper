# Nullable generics

A type parameter bound to a nullable type, as `String?` or `Int?`: a
`T?` of it is no more nullable than the binding, so a `T?` result that's
null is null wherever it's read, and a parameter of type `T` with a
default takes the default for null, as a nullable parameter's default
does. Bound to a type that isn't nullable, each works as usual, as
does a `T?` result that's null where `T` is a class. The log is what
the interpreter gives.

    export let firstOrNull<T>(ls: List<T>): T? {
      if (ls.isEmpty) { null } else { ls[0] }
    }

    export class Slot<T>(public value: T) {
      public kept(keep: Boolean): T? {
        if (keep) { value } else { null }
      }
    }

    export class Named(public name: String) {}

    let strings: List<String?> = [null, "b"];
    let noStrings: List<String?> = [];
    let ints: List<Int?> = [null, 2];
    let a = firstOrNull(strings);
    let b = firstOrNull(noStrings);
    let c = firstOrNull(ints);
    console.log("${a == null} ${b == null} ${c == null}");
    console.log(firstOrNull(["x"]) ?? "none");
    console.log("${firstOrNull(ints.slice(1, 2)) ?? -1} ${firstOrNull([3]) ?? -1}");
    let slot = new Slot<String?>(null);
    console.log("${slot.kept(true) == null} ${slot.kept(false) == null}");
    console.log(new Slot<String?>("s").kept(true) ?? "none");
    let nameds: List<Named?> = [null];
    let noNameds: List<Named> = [];
    let n = firstOrNull([new Named("n")]);
    console.log("${firstOrNull(nameds) == null} ${firstOrNull(noNameds) == null} ${n?.name ?? "none"}");
    console.log(firstOrNull(noNameds)?.name ?? "none");

```log
true true true
x
2 3
true true
s
true true n
none
```

A parameter of type `T` with a default, bound to a nullable type: null,
left out or given, takes the default.

    export let withDefault<T>(x: T, y: T = x): T { y }

    let none: String? = null;
    console.log(withDefault<String?>("a", none) ?? "null");
    console.log(withDefault<String?>("a", "b") ?? "null");
    console.log(withDefault<String?>("a") ?? "null");
    console.log("${withDefault<Int?>(1, null) ?? -1}");
    console.log("${withDefault("c")} ${withDefault(4, 5)}");

```log
a
b
a
1
c 5
```
