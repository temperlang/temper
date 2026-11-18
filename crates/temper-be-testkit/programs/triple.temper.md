# Triple

The architecture docs' running example (`docs/internals/README.md`): a function,
`*`, and a string template. Its goldens here and in each backend's
`tests/goldens/` are the output the docs show, so they can't go stale.
The log is what the interpreter gives.

    let triple(n: Int): Int { n * 3 }
    console.log("triple(14) = ${triple(14)}");

```log
triple(14) = 42
```
