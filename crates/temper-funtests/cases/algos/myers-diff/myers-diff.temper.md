# Myers Diff Functional Test

The diff library defines *diff* and *formatPatch*.

Using those we can diff lists of lines and format the resulting patch:

    console.log(
      formatPatch(
        diff(
          "Hello,\nWorld!".split("\n"),
          "Hello,\nCosmos!".split("\n"),
          fn (a: String, b: String): Boolean { a == b }
        )
      )
    );

That prints:

```log
 Hello,
-World!
+Cosmos!

```
