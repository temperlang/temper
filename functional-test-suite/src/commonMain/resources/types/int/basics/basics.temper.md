# Int Basics

Min and max work as expected.

Note: Unlike some other languages, the dot (`.`) in `1.min` is a method
separator, not a decimal point for a floating point literal.

    console.log("1.min(3)");
    console.log((1.min(3)).toString());

    console.log("3.min(1)");
    console.log((3.min(1)).toString());

    console.log("1.max(3)");
    console.log((1.max(3)).toString());

    console.log("3.max(1)");
    console.log((3.max(1)).toString());

```log
1.min(3)
1
3.min(1)
1
1.max(3)
3
3.max(1)
3
```

Backends that have a single number representation for both floats and ints
(JS, Lua, Perl) should also test `-0.min(0)` and `0.min(-0)` return a zero
with `-` signedness, preferably by connecting Int32::{min,max} to a standard
library function that takes such care.
But such a distinction is not visible from within Temper so it is not tested
here.
