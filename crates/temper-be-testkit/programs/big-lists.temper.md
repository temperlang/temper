# Big lists

List builders taking 200,000 values at once, more than a JS call's
arguments can hold (`push(...values)` overflows the stack past about
120,000): `addAll` at the end, at the start, and in the middle, with
a builder adding itself; `splice` putting them in, at an index clamped
to the builder's bounds (a negative one is the start), and putting a
builder's own values into it; `filter` keeping a long run of them; and
`String.fromCodePoints` taking as many. Shared, as Lua has a limit on
values unpacked at once too.

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

    let c = new ListBuilder<Int>();
    c.add(7);
    c.add(8);
    c.splice(-1, 0, values);
    c.splice(10 * n, 0, values);
    console.log("${c.length} ${c[0]} ${c[n - 1]} ${c[n]} ${c[n + 1]} ${c[n + 2]} ${c[2 * n + 1]}");

    let own = new ListBuilder<Int>();
    own.addAll(values);
    let gone = own.splice(0, 1, own);
    console.log("${own.length} ${gone.length} ${own[0]} ${own[n - 1]} ${own[n]} ${own[2 * n - 2]}");

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
400002 0 199999 7 8 0 199999
399999 1 0 199999 1 199999
199999 4 6 199999
200000 abca
```
