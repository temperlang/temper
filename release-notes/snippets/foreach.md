### For each loops on lists

JS-style for each loops now work on `List` instances:

```temper
for (let i of [2, 3, 4]) {
  console.log(i.toString());
}
```

This desugars to `.forEach` method calls, with the intention of inlining such
calls at a future point.
