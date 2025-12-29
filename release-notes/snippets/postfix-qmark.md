### Abbreviated nullable type syntax: *Type?*

A question mark, `?`, after a type now marks the type nullable.

```temper
// The input type is just String.
let acceptsStringOnly(s: String): Void {
  console.log("Got string: ${s}.");
}

acceptsStringOnly("a string");   //!outputs "Got string: a string."
// Can't pass the special null value to a non-nullable parameter
//// acceptsStringOnly(null);
// The string "null" is not the special value null.
acceptsStringOnly("null");       //!outputs "Got string: null."
// The empty string is not null either.
acceptsStringOnly("");           //!outputs "Got string: ."

// The input type has a postifx `?`.
let acceptsStringOrNull(s: String?): Void {
  console.log("Got string or null: ${s}.");
}
acceptsStringOrNull("a string"); //!outputs "Got string or null: a string."
acceptsStringOrNull(null);       //!outputs "Got string or null: null."
```
