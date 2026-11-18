# Host errors

`orelse` handles only what Temper declares may bubble. A builtin that
isn't declared to bubble but whose docs say it panics, as reading past
a list's end or a string's does, panics on every backend, whatever its
host does there: never a bubble that `orelse` would handle, nor a
host's own error. Each section here is a run of its own, as each fails
at its panic, which passes every `orelse`, even inside a function that
bubbles for other reasons, so the run fails right after `reading`, and
says why on stderr:

    let at(i: Int): Int throws Bubble {
      if (i == 99) { bubble() }
      i
    }
    console.log("reading");

## List read

    let read(ls: List<Int>, i: Int): Int throws Bubble { ls[at(i)] }
    let r = read([1, 2], 5) orelse -1;
    console.log("read past the end ${r}");

```log
reading
```

```stderr
index 5 out of bounds for length 2
```

## Negative index

    let built(): ListBuilder<Int> {
      let lb = new ListBuilder<Int>();
      lb.add(1);
      lb
    }
    let read(ls: Listed<Int>, i: Int): Int throws Bubble { ls[at(i)] }
    let r = read(built(), -1) orelse -1;
    console.log("read before the start ${r}");

```log
reading
```

```stderr
index -1 out of bounds for length 1
```

## Set

    let built(): ListBuilder<Int> {
      let lb = new ListBuilder<Int>();
      lb.add(1);
      lb
    }
    let write(lb: ListBuilder<Int>, i: Int): Int throws Bubble {
      lb.set(at(i), 3);
      lb.length
    }
    let r = write(built(), 5) orelse -1;
    console.log("set past the end ${r}");

```log
reading
```

```stderr
index 5 out of bounds for length 1
```

## Insert

    let built(): ListBuilder<Int> {
      let lb = new ListBuilder<Int>();
      lb.add(1);
      lb
    }
    let insert(lb: ListBuilder<Int>, i: Int): Int throws Bubble {
      lb.add(2, at(i));
      lb.length
    }
    let r = insert(built(), 5) orelse -1;
    console.log("added past the end ${r}");

```log
reading
```

```stderr
index 5 out of bounds for length 1
```

## Remove last

    let removeLast(lb: ListBuilder<Int>, i: Int): Int throws Bubble {
      at(i);
      lb.removeLast()
    }
    let r = removeLast(new ListBuilder<Int>(), 0) orelse -1;
    console.log("removed from empty ${r}");

```log
reading
```

```stderr
removeLast on an empty ListBuilder
```

## Remove first

    let removeFirst(d: Deque<Int>, i: Int): Int throws Bubble {
      at(i);
      d.removeFirst()
    }
    let r = removeFirst(new Deque<Int>(), 0) orelse -1;
    console.log("removed from empty ${r}");

```log
reading
```

```stderr
removeFirst on an empty Deque
```

## Reduce

Core's `reduce` starts from `this[0]`, so an empty list panics as that
read does:

    let sum(ls: List<Int>, i: Int): Int throws Bubble {
      at(i);
      ls.reduce(fn (a: Int, b: Int): Int { a + b })
    }
    let r = sum([], 0) orelse -1;
    console.log("reduced empty ${r}");

```log
reading
```

```stderr
index 0 out of bounds for length 0
```

## String read

A string's end is past its last code point (each backend counts a
string index in its own units, but ASCII's agree):

    let codeAt(s: String, i: Int): Int throws Bubble {
      at(i);
      s[s.end]
    }
    let r = codeAt("ab", 0) orelse -1;
    console.log("read the end ${r}");

```log
reading
```

```stderr
string index 2 out of bounds
```
