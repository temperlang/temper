Matching string start is an example of zero-length match, and we need to make
sure this doesn't cause infinite loops in replace-all logic.

    let { ... } = import("std/regex");
    let commaItemStartRegex = /(^|,)\s*/;
    export let bulletizeList(text: String): String {
      let replaced = commaItemStartRegex.replace(text) { match => "\n- " };
      replaced.slice(replaced.next(String.begin), replaced.end)
    }

This example shows matching the start of the list as well as the insides.

    console.log(bulletizeList("apples, bananas, cherries"));

```log
- apples
- bananas
- cherries
```
