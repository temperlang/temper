### 🚨Breaking change: type parameter variance now requires `@`.

Previously, a type parameter could be declared like `<out T>`.
Now you need to declare it `<@out T>` using decoration/annotation syntax.
Similarly for contravariant syntax `<@in T>`.
Invariant remains the default.

Also, both covariance and contravariance are deprecated.  It's not
clear that explicit variance is translatable to languages that infer
variance or to language's that are very aggressive about
monomorphization.
