# Host errors

`orelse` handles only the failures Temper declares. A builtin that
doesn't declare one but whose docs say it panics, as reading past a
list's end or a string's does, panics on every backend, whatever its
host does there: never a failure that `orelse` would handle, nor a
host's own error. So does a callback a builtin runs, as a regex's
`replace` does. Each test here panics past an `orelse`, even inside a
function that may throw for other reasons, so it fails saying why;
were the panic handled, it would fail saying "caught".

    let { ... } = import("std/regex");
    sealed interface Problem extends Error {}
    class Oops extends Problem {}
    let at(i: Int): Int throws Oops {
      if (i == 99) { throw new Oops() }
      i
    }
    let built(): ListBuilder<Int> {
      let lb = new ListBuilder<Int>();
      lb.add(1);
      lb
    }

Reads, past the end, at it, and before the start:

    let read(ls: Listed<Int>, i: Int): Int throws Oops { ls[at(i)] }

    test("list read") {
      let r = read([1, 2], 5) orelse -1;
      assert(r != -1) { "caught" };
    }

    test("list read at the end") {
      let ls = [1, 2];
      let r = read(ls, ls.length) orelse -1;
      assert(r != -1) { "caught" };
    }

    test("negative index") {
      let r = read(built(), -1) orelse -1;
      assert(r != -1) { "caught" };
    }

A `set` past the end and at it, where one backend's lists grow, and an
insert past the end; an insert at the end appends:

    let write(lb: ListBuilder<Int>, i: Int): Int throws Oops {
      lb.set(at(i), 3);
      lb.length
    }
    let insert(lb: ListBuilder<Int>, i: Int): Int throws Oops {
      lb.add(2, at(i));
      lb.length
    }

    test("set") {
      let r = write(built(), 5) orelse -1;
      assert(r != -1) { "caught" };
    }

    test("set at the end") {
      let lb = built();
      let r = write(lb, lb.length) orelse -1;
      assert(r != -1) { "caught" };
    }

    test("insert") {
      let r = insert(built(), 5) orelse -1;
      assert(r != -1) { "caught" };
    }

    test("insert past the end") {
      let lb = built();
      let r = insert(lb, lb.length + 1) orelse -1;
      assert(r != -1) { "caught" };
    }

    test("insert at the end appends") {
      let lb = built();
      let n = insert(lb, lb.length) orelse -1;
      assert(n == 2 && lb[1] == 2) { "didn't append" };
    }

Removing from an empty list or deque:

    let removeLast(lb: ListBuilder<Int>, i: Int): Int throws Oops {
      at(i);
      lb.removeLast()
    }
    let removeFirst(d: Deque<Int>, i: Int): Int throws Oops {
      at(i);
      d.removeFirst()
    }

    test("remove last") {
      let r = removeLast(new ListBuilder<Int>(), 0) orelse -1;
      assert(r != -1) { "caught" };
    }

    test("remove first") {
      let r = removeFirst(new Deque<Int>(), 0) orelse -1;
      assert(r != -1) { "caught" };
    }

Core's `reduce` starts from `this[0]`, so an empty list panics as that
read does:

    let sum(ls: List<Int>, i: Int): Int throws Oops {
      at(i);
      ls.reduce(fn (a: Int, b: Int): Int { a + b })
    }

    test("reduce") {
      let r = sum([], 0) orelse -1;
      assert(r != -1) { "caught" };
    }

A string's end is past its last code point (each backend counts a
string index in its own units, but ASCII's agree):

    let codeAt(s: String, i: Int): Int throws Oops {
      at(i);
      s[s.end]
    }

    test("string read") {
      let r = codeAt("ab", 0) orelse -1;
      assert(r != -1) { "caught" };
    }

A panic in a regex's `replace` callback passes `replace` and the
`orelse` around it:

    let replaced(i: Int): String throws Oops {
      at(i);
      /b/.replace("abc") { match => ["x"][i] }
    }

    test("replace callback") {
      let r = replaced(3) orelse "";
      assert(r != "") { "caught" };
    }

Each failing test, with what its failure says, never "caught":

```failures
list read: index 5 out of bounds for length 2
list read at the end: index 2 out of bounds for length 2
negative index: index -1 out of bounds for length 1
set: index 5 out of bounds for length 1
set at the end: index 1 out of bounds for length 1
insert: index 5 out of bounds for length 1
insert past the end: index 2 out of bounds for length 1
remove last: removeLast on an empty ListBuilder
remove first: removeFirst on an empty Deque
reduce: index 0 out of bounds for length 0
string read: string index 2 out of bounds
replace callback: index 3 out of bounds for length 1
```
