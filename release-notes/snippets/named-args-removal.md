### 🚨Breaking change: Named arguments removed

Because Temper needs to translate to usable libraries in other languages
without named arguments, having named args in Temper is deceptive. For example,
this looks nice in Temper (based on Temper's builtin `Float64.near` method):

```temper
// Given this function ...
let near(
  x: Float64, y: Float64, relTol: Float64 = 1e-9, absTol: Float64 = 0.0
): builtins.Boolean { ... }

// ... here are some example calls that might be in other contexts.
// But such named args are now *illegal* in Temper.
near(x, y, absTol = 0.01)
near(x, y, relTol = 0.01)
```

But in C, JS, Java, and many other languages, named args are unavailable. This
could be especially bad for functions or constructors with many parameters. We
discussed a variety of elaborate translations to address these situations, but
the simplest thing is just to remove named args entirely.

```temper
// You now instead have to provide positional arguments.
near(x, y, null, 0.01)
near(x, y, 0.01)
```

However, we do still have JS-style object literal syntax with named properties
for constructing class instances. And we've started adding builders to target
languages to enable proper user experience. For example, the above example could
be refactored as follows:

```temper
// Given these definitions ...
class Tolerances(
    public relTol: Float64 = 1e-9,
    public absTol: Float64 = 0.0,
) {}
let near(x: Float64, y: Float64, tols: Tolerances) { ... }

// ... here are some example calls that might be in other contexts.
// Such calls are *still legal* in Temper.
near(x, y, { absTol: 0.01 })
near(x, y, { relTol: 0.01 })
```

Because Temper generates named-property builders in targets lacking builtin
naming features, the expected user experience is still available. This pattern
for semi-named-args also is already commonly used in languages such as JS. The
cost of object instantiation exists, but the usability is sufficient, even for
long lists of properties.
