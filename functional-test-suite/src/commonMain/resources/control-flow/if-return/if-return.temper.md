# If-Return Functional Test

Try three variants of functions that take a boolean and
return "t" given true or "f" otherwise.
These three variants mix and match explicit and implicit results.

    // has a `return` statement along every exiting branch
    let explicit(b: Boolean): String {
      if (b) {
        return "true"
      }
      return "false"
    };

    // has a `return` statement along one exiting branch
    // but an implicit result along another
    let halfAndHalf(b: Boolean): String {
      if (b) {
        return "true"
      }
      "false" // implicit assignment along only one path
    };

    // has implicit results along both exiting branches
    let implicit(b: Boolean): String {
      if (b) {
        "true"
      } else {
        "false"
      }
    };

    var t = true;
    t = t;
    var f = !t;

    console.log("explicit    true  -> ${ explicit(t) }");
    console.log("explicit    false -> ${ explicit(f) }");
    console.log("halfAndHalf true  -> ${ halfAndHalf(t) }");
    console.log("halfAndHalf false -> ${ halfAndHalf(f) }");
    console.log("implicit    true  -> ${ implicit(t) }");
    console.log("implicit    false -> ${ implicit(f) }")

Expected results:

```log
explicit    true  -> true
explicit    false -> false
halfAndHalf true  -> true
halfAndHalf false -> false
implicit    true  -> true
implicit    false -> false
```
