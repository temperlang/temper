### 🆕Quoted character syntax `char'a'`

The `char` string tag can be followed by exactly one quoted code-point
and resolves to that code-point's integer value.

It interoperates with code-point related:
*String.fromCodePoint*, *String.fromCodePoints*, and
*StringBuilder.appendCodePoint*, and *String.codeAt*.

```temper
console.log("A: ${ char'A'.toString() }");
// Logs "A: 65"
```
