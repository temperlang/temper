### Null-aware handling

Temper now has limited flow typing for null-checked branches. For example, this
is now legal:

```temper
let maybeReportNegative(a: Int | Null): Void {
  // So far, support is limited to a simple check against a single name.
  if (a != null) {
    // We know *a* isn't null here, so we can use it as type Int.
    console.log((-a).toString());
  } else {
    console.log("not an int");
  }
}
```

Further, similar to operators in JS and other languages, Temper now has
operators for explicitly managing null values. Here are examples:

```temper
let maybeLength(a: String | Null): Int | Null {
  // Because of non-null inference on simple names, `a.end` is ok here.
  a?.countBetween(String.begin, a.end)
}

let chainMore(a: String | Null): String {
  (maybeLength(a)?.max(0) ?? -1).toString()
}
```

Specifically, these operators have been added:

- `?.` null chaining, where the right-hand side is evaluated only when the
  left side is non-null. If the left is null, the resulting value is also null.
- `??` null coalescing, where the right-hand side is the alternate value
  provided when the left side is null. Some languages use the token `?:` rather
  than `??` for similar semantics.
