# Completed Types

In some contexts, it's legal to specify incomplete types: `Pair`
instead of `Pair<K, V>`, for example.
But for translation purposes, our internal representation completes
partial types to complete types.

In a `new` operation, the type parameters can be inferred based on
the assignment context.

    let c: C<String, Int32> = new C("", 0);

And if the declared type is a super-type, we can skip type arguments
as long as the super-type parameters determine the subtype's parameters.
(It's iffy if the subtype adds parameters unrelated to any in the
super-type, but that's not the case here.)

    let d: I<String, Int32> = new C("", 0);

And even when the argument's themselves don't narrow the parameter types,
the context can be sufficient.

    let e: C<String, Int32> = new C(null, null);

(See `../supporting-types/types.temper` for definitions of *I* and *C*).

    let { I, C } = import("../support-types");

----

When casting, we also have context from the pre-cast type.

    let f(x: I<Boolean, String>): Void {

If it's an `I<Boolean, String>` and it's a `C`, then that narrows down
which variety of `C` it can be, so `is C` and `as C` are sensible.

      if (x is C) {
        (x as C).foo()
      };
    }

TODO: Do we want to allow partial types on declarations that we
complete based on initializer expression context like the below.

```temper inert
let e: I = new C<Float64, Boolean>();
```
