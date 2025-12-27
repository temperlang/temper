# Diff docs sample

The *diff-utils* library lets you find differences between two lists.

```temper
let diffUtils = import("diff-utils");
let { Date } = import("std/temporal");

let myPatch = diffUtils.diffStrings(
    "Hello,\nWorld!",
    "Hello,\nCosmos!"
);
```

By default it just splits on newlines, but you can *diff* arbitrary hashable values.

```temper
let dateComparer = { (a: Date, b: Date): Int =>
    a <=> b
};

let datesPatch = diffUtils.diff(
    [ {month: 3, day: 14, year: 1592} ],
    [ {month: 3, day: 14, year: 1592},
      {month: 2, day:  7, year: 1828} ],
    dateComparer
);
```

And you can format a patch as from the command line:

```temper
console.log(diffUtils.formatUnified(myPatch));
```

That logs:

```diff=
@@ -1,2 +1,2 @@
 Hello,
-World!
+Cosmos!
```
