### 🚨Breaking change: *StringSlice* removed in favor of indexed *String*s

Previously, Temper provided multiple *StringSlice*s types for string
processing tasks.

Those have been removed, and *StringIndex* allows for string processing.

*StringIndex* connects to *Int* or *size_t* types in target languages,
but the precise relationship between the integral value depends on
the target language's "native" string encoding.

```temper
let s = "Blah blah Hello, World!";
// The below logs "Hello, World!"
var idx = String.begin;  // An index at the start
while (idx < s.end) { // Still in the string
  if (s[idx] == char'H') {
    console.log(s.slice(idx, s.end));
    break;
  }

  idx = s.next(idx); // Step forward one code-point
}
```
