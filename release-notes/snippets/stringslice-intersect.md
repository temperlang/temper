### 🆕StringSlice.excludeAfter

(This release note is obsoleted by indexed strings)

The new *StringSlice* method, *excludeAfter* allows taking the
prefix of a slice that ends at another.

*StringSlice* is meant to allow efficient left-to-right processing
of strings, when we can't assume the native string representation
supports O(1) random-access for a particular code-unit size.

*excludeAfter* is used below to choose a prefix of the remaining,
unprocessed content up to the point where we need to do some
special processing.

```temper
do {
  let carriageReturn = 0xA; // ASCII \n

  // Prefixes each line of its input with `> `.
  let markdownQuote(str: String, out: ListBuilder<String>): Void {
    var remainder = str.codePoints; // The unprocessed portion
    var emitted = remainder;        // The portion processed onto out
    while (!remainder.isEmpty) {
      let next = remainder.advance(1);
      if (remainder.read() == carriageReturn) {
        out.add("> ");
        out.add(emitted.excludeAfter(next).toString());
        emitted = next;
      }
      remainder = next;
    }
    if (!emitted.isEmpty) {
      out.add("> ");
      out.add(emitted.toString());
    }
  }

  // Create an output list to accumulate chunks.
  let stringBuilder = new ListBuilder<String>();
  markdownQuote("foo\nbar\nbaz", stringBuilder);
  //!outputs "> foo\n> bar\n> baz"
  console.log(stringBuilder.join("") { (chunk: String): String;; chunk });
}
```
