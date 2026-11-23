# Generic null checks

A null check on a value of a bare type parameter `T` asks whether that
value is null: true for null where `T` is bound to a nullable type, and
false for any value of a type that isn't nullable; and `??` on one
takes its fallback for null. Rust can't make the
check, as its `T` may or may not be an `Option`, and refuses it (see
RFC 684, "Flat options"), so this runs on the other backends. The log
is what the interpreter gives.

    export class Named(public name: String) {}

    export let isNull<T>(x: T): Boolean { x == null }

    console.log("${isNull<String?>(null)} ${isNull<String?>("a")} ${isNull("a")}");
    console.log("${isNull<Int?>(null)} ${isNull<Int?>(1)} ${isNull(1)}");
    console.log("${isNull<Named?>(null)} ${isNull(new Named("n"))}");

    export let orElse<T>(x: T, y: T): T { x ?? y }

    console.log(orElse<String?>(null, "y") ?? "null");
    console.log("${orElse("x", "y")} ${orElse<Int?>(null, 2) ?? -1}");

```log
true false false
true false false
true false
y
x 2
```
