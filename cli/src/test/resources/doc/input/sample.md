If we had a definition of a diff function
we could execute a diff to return a list of differences
and log something if there is a difference.

```
let diff(a: String, b: String): List<String> {
  [] // placeholder
}

let differences = diff("stringA", "stringB");
if (differences.isEmpty) {
    console.log("they're the same")
}
```

We can use a different name for console to see what happens.

```
let something = console;
something.log("more");
```
