### 🆕*std/strings*'s *StringBuilder* allows composing strings

The *StringBuilder* type connects to the standard library type
of the same name in Java and C#, and connects to efficient
left-to-right string composition idioms in dynamic language
backends.

```temper
let { StringBuilder } = import("std/strings");

let sb = new StringBuilder();
sb.append("Hello, ");
sb.append("World");
sb.appendCodePoint(char'!');

// Logs "Hello, World!"
console.log(sb.toString());
```
