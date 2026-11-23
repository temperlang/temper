# Callback changes

What runtimes do when a builder is added to or spliced into itself, when
a callback changes the builder or map it walks, and when a sort's
comparison changes the builder it sorts. Each must stay memory safe and
never deadlock: no runtime holds a lock, an iterator, or a range over
its own storage while a callback runs.

```text
@meta:arg expectRunFailure = true,
```

    let show(ls: Listed<Int>): String {
      ls.join(",") { (n: Int): String => n.toString() }
    }

`addAll` and `splice` given the builder itself add a copy of its values
as they were before the call, as Python's `b[i:j] = b` does:

    let b = [1, 2].toListBuilder();
    b.addAll(b);
    let c = [1, 2].toListBuilder();
    c.addAll(c, 1);
    let d = [1, 2, 3].toListBuilder();
    let removed = d.splice(1, 1, d);
    console.log("self ${show(b)} ${show(c)} ${show(d)} ${show(removed)}");

A `filter` that keeps everything returns a new list, leaving the builder
as it was (JS once froze the builder itself):

    let e = [1, 2].toListBuilder();
    let kept = e.filter { (n: Int): Boolean => n > 0 };
    e.add(3);
    console.log("filter ${show(kept)} ${show(e)}");

A comparison may read the builder it sorts (Rust once deadlocked), and
sees it as it was, since every backend sorts a copy:

    let f = [3, 1, 2].toListBuilder();
    var peeks = 0;
    f.sort { (a: Int, z: Int): Int => peeks += f.length; a - z };
    console.log("peeking ${show(f)}");

Callbacks that change what they walk. Which values they see is open
([#671](https://github.com/temperlang/temper/issues/671)), and backends
differ (the interpreter walks a snapshot; Python and Java walk the live
builder; C#, Java, and Python's maps throw their own exceptions, which
`orelse` catches there, as -1), so each backend's values are pinned
below, to notice when one changes. The items are strings made at run
time, so that reading a removed one is a use after free that
AddressSanitizer reports:

    let words(): ListBuilder<String> {
      let w = new ListBuilder<String>();
      for (var i = 0; i < 3; i += 1) { w.add("w${i}"); }
      w
    }
    let reduceFromGrowing(): Int throws Bubble {
      let g = [1, 2].toListBuilder();
      g.reduceFrom(0) { (sum: Int, n: Int): Int =>
        if (g.length < 4) { g.add(9); }
        sum * 10 + n
      }
    }
    let mapShrinking(): Int throws Bubble {
      let w = words();
      w.map { (s: String): String =>
        if (w.length > 1) { w.removeLast(); }
        s
      }.length
    }
    let reduceShrinking(): Int throws Bubble {
      let w = words();
      let all = w.reduce { (all: String, s: String): String =>
        if (w.length > 1) { w.removeLast(); }
        all
      };
      if (all.isEmpty) { 0 } else { 1 }
    }
    let filterGrowing(): Int throws Bubble {
      let g = [1, 2].toListBuilder();
      g.filter { (n: Int): Boolean =>
        if (g.length < 4) { g.add(9); }
        true
      }.length
    }
    let joinGrowing(): Int throws Bubble {
      let g = [1, 2].toListBuilder();
      let joined = g.join(",") { (n: Int): String =>
        if (g.length < 4) { g.add(9); }
        n.toString()
      };
      if (joined.isEmpty) { 0 } else { 1 }
    }
    let letters(): MapBuilder<String, Int> {
      let m = new MapBuilder<String, Int>();
      m.set("a${1}", 1);
      m.set("b${2}", 2);
      m.set("c${3}", 3);
      m
    }
    let forEachRemoving(): Int throws Bubble {
      let m = letters();
      var sum = 0;
      m.forEach { (k: String, v: Int): Void =>
        if (m.has("c3")) { m.remove("c3") orelse void; }
        sum += v;
      };
      sum
    }
    let forEachAdding(): Int throws Bubble {
      let m = letters();
      var count = 0;
      m.forEach { (k: String, v: Int): Void =>
        count += 1;
        if (m.length < 30) { m.set("k${m.length}", 1); }
      };
      count
    }
    let toListWithRemoving(): Int throws Bubble {
      let m = letters();
      m.toListWith { (k: String, v: Int): Int =>
        if (m.has("c3")) { m.remove("c3") orelse void; }
        v
      }.length
    }

    let walked = [
      reduceFromGrowing() orelse -1,
      mapShrinking() orelse -1,
      reduceShrinking() orelse -1,
      filterGrowing() orelse -1,
      joinGrowing() orelse -1,
      forEachRemoving() orelse -1,
      forEachAdding() orelse -1,
      toListWithRemoving() orelse -1,
    ];
    console.log("walks ${show(walked)}");

A comparison that changes the builder it sorts but not its length, as
by setting an item, or removing one and adding one, doesn't change
what's sorted, which is a copy: the sorted copy replaces the change, on
every backend (Python, Java, and Lua once sorted in place, reading or
keeping the change, or failing):

    let t = [3, 1, 2].toListBuilder();
    t.sort { (a: Int, z: Int): Int => t[0] = 99; a - z };
    let u = [3, 1, 2].toListBuilder();
    u.sort { (a: Int, z: Int): Int =>
      let last = u.removeLast() orelse 0;
      u.add(last + 10);
      a - z
    };
    console.log("same length ${show(t)} ${show(u)}");

A comparison that changes the length of the builder it sorts panics, on
every backend, as Java's `ConcurrentModificationException` and Python's
"list modified during sort" do, rather than dropping what it added or
reading freed memory. A panic ends the run, so this goes last:

    let s = [3, 1, 2].toListBuilder();
    s.sort { (a: Int, z: Int): Int =>
      if (s.length < 40) { s.add(0); }
      a - z
    };
    console.log("sorted ${s.length}");

The run must end in that panic, as each backend's panic ends one, with
this message (testkit's `check_case`), not a host exception or a crash:

```panic
ListBuilder changed length during sort
```

```log
self 1,2,1,2 1,1,2,2 1,1,2,3,3 2
filter 1,2 1,2,3
peeking 1,2,3
```

```log interp cpp
walks 12,3,1,2,1,6,3,3
```

```log csharp
walks -1,-1,-1,-1,-1,-1,-1,-1
```

```log java
walks 1299,-1,1,-1,-1,-1,-1,-1
```

```log js
walks 12,3,1,4,1,3,30,2
```

```log lua
walks 12,2,1,2,1,3,30,2
```

```log py
walks 1299,2,1,4,1,-1,-1,-1
```

```log rust
walks 12,3,1,2,1,3,3,2
```

```log
same length 1,2,3 1,2,3
```
