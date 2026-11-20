# Big lists

List builders taking 200,000 values at once, more than a JS call's
arguments can hold (`push(...values)` overflows the stack past about
120,000): `addAll` at the end, at the start, and in the middle, with
a builder adding ittwice; `splice` putting them in; and `filter` keeping
a long run of them; and `String.fromCodePoints` taking as many.

    let n = 200000;
    let source = new ListBuilder<Int>();
    for (var i = 0; i < n; i += 1) { source.add(i); }
    let values = source.toList();

    let b = new ListBuilder<Int>();
    b.addAll(values);
    b.addAll(values, 0);
    b.add(-1, n);
    b.addAll(values, n);
    console.log("${b.length} ${b[0]} ${b[n - 1]} ${b[n]} ${b[2 * n]} ${b[2 * n + 1]} ${b[3 * n]}");

    let twice = new ListBuilder<Int>();
    twice.addAll(values);
    twice.addAll(twice, 1);
    console.log("${twice.length} ${twice[0]} ${twice[1]} ${twice[n]} ${twice[n + 1]}");

    let s = new ListBuilder<Int>();
    s.add(7);
    s.add(8);
    let removed = s.splice(1, 0, values);
    console.log("${s.length} ${removed.length} ${s[0]} ${s[1]} ${s[n]} ${s[n + 1]}");

    let kept = values.filter(fn (x: Int): Boolean { x != 5 });
    console.log("${kept.length} ${kept[4]} ${kept[5]} ${kept[n - 2]}");

    let codes = new ListBuilder<Int>();
    for (var i = 0; i < n; i += 1) { codes.add(97 + i % 3); }
    let text = String.fromCodePoints(codes.toList()) orelse "";
    let four = text.next(text.next(text.next(text.next(String.begin))));
    console.log("${text.countBetween(String.begin, text.end)} ${text.slice(String.begin, four)}");

```log
600001 0 199999 0 -1 0 199999
400000 0 0 199999 1
200002 0 7 0 199999 8
199999 4 6 199999
200000 abca
```
