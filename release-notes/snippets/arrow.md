### 🚨Breaking change: Use `=>` for lambda blocks instead of `;;`

To make the syntax possibly more familiar looking, lambda blocks now use thick
arrows to separate the header from the body:

```temper inert
toListBuilderWith { (key: K, value: V): Pair<K, V> =>
  new Pair(key, value)
}
```

Previously, `;;` was used instead of `=>`.

Temper syntax still differs from JS arrow functions in order to accommodate the
trailing block syntax.

Also new, When only parameters are explicit in the lambda block header, parens
are optional, as in this example:

```temper inert
forEach { key, value =>
  builder.add(func(key, value));
}
```

Explicit parameter types also can be given without parens, so long as explicit
*return* type or other components outside the parens are absent:

```temper inert
// The type of parameter *substring* is *String*.
this.substrings.join("") { substring: String => substring }
```
