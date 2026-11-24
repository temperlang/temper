# String Index at the End

Reading a string's code point at its end panics on every backend, as
reading a list out of bounds does.

```text
@meta:arg expectRunFailure = true,
```

    let codeAt(s: String, i: StringIndex): Int { s[i] }

    let text = "abc";
    console.log(codeAt(text, String.begin).toString());
    console.log("before");
    console.log(codeAt(text, text.end).toString());
    console.log("not reached");

```log
97
before
```

The run ends in each backend's panic, with this message:

```panic
index 3 out of bounds for length 3
```
