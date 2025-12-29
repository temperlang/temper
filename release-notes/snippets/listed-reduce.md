### Listed.reduce

Temper *Listed* types now support *reduce* methods as in the following examples:

```temper
let vals = [2, 3, 4];
let sum = vals.reduce { (sum, n);; sum + n }; // -> 9
let notSum = vals.slice(0, 0).reduce { (sum, n);; sum + n } orelse -1; // -> -1
let poorReverseJoin = vals.reduceFrom("") { (text: String, n): String;;
    "${n.toString()}${text}"
}; // -> "432"
```
