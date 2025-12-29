### 🚨Breaking change: *StringBuilder* moved to core

You used to have to import *StringBuilder* before using it:

```temper
let { StringBuilder } = import("std/strings");
let builder = new StringBuilder();
```

But the above is no longer needed and is now illegal because
*StringBuilder* is now part of the implicitly available core library. So now,
that's just this:

```temper
let builder = new StringBuilder();
```
