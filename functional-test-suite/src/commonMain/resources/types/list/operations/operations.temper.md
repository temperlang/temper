# List Operations Functional Test

## List as Listed

Before getting into more interesting things, start with simple usage of a list
literal as an argument to a Listed parameter. This was motivated by an issue in
the C# backend where the C# type wasn't specifically handled in the overloads
for the Listed-related .NET types, so the application was ambiguous.

Might be uninteresting for other backends, but it's also the only case in this
test script of a generic function, so it might be interesting just for variety.

    let reportLength<T>(list: Listed<T>): Void {
      console.log("Length: ${list.length}");
    }

And ensure the `List.of` option works everywhere (which it ought to because it's
unified with `[...]` syntax in the Temper frontend):

    reportLength(List.of<AnyValue>(4, 5, 6));

```log
Length: 3
```

This test is at the front of the file because we currently have issues with the
handling of functions and types defined after bubbling function calls. See
[issue 66](https://github.com/temperlang/temper/issues/66).

## Operations on imu (immutable) lists

This tests basic list operations: filter, map, join. Most operations can also
be used on mut(able) list builders, but we'll look closer at those later. Just
focus on imu lists for now.

Let's start with a list of integers, proving out sorting as well.

    let ls: List<Int> = [5, 6, 7, 8, 9, 0, 1, 2, 3, 4].sorted { i, j =>
      i - j
    };

Then we can map them to strings and print them out.

    console.log("All  ${ (ls.join(", ") { x => x.toString()}) }");

which outputs

```log
All  0, 1, 2, 3, 4, 5, 6, 7, 8, 9
```

We can filter before calling map:

    console.log(
      "Even ${
        (ls.filter { x => (x & 1) == 0 }
          .join(", ") { x => x.toString()})
      }"
    );

We should see just the even numbers:

```log
Even 0, 2, 4, 6, 8
```

We'll try the opposite filter:

    console.log(
      "Odd  ${
        (ls.filter { x => (x & 1) != 0 }
          .join(", ") { x => x.toString()})
      }"
    );

which outputs:

```log
Odd  1, 3, 5, 7, 9
```

And we can transform them.

    console.log(
      "Neg  ${
        (ls.map {(x): Int => -x }
          .join(", ") { x => x.toString()})
      }"
    );

which outputs

```log
Neg  0, -1, -2, -3, -4, -5, -6, -7, -8, -9
```

Also prove this works from the abstract `Listed`` interface.

    let listed: Listed<Int> = ls;
    console.log(
      "Neg  ${
        (listed.map {(x): Int => -x }
          .join(", ") { x => x.toString()})
      }"
    );

```log
Neg  0, -1, -2, -3, -4, -5, -6, -7, -8, -9
```

Also check that we can get a list back. Ideally, this isn't a copy on most
backends.

    let listedList = listed.toList();
    console.log("Listed list length is ${listedList.length}");

```log
Listed list length is 10
```

Instead of using functional operators, we can also loop:

    console.log("Looping");
    let n = ls.length;
    for (var i: Int = 0; i < n; ++i) {
      console.log("  ${ls.get(i)}");
    }

We get:

```log
Looping
  0
  1
  2
  3
  4
  5
  6
  7
  8
  9
```

and we can construct lists from strings:

    let reportSplit(string: String, sep: String): Void {
      let strings = string.split(sep);
      let len = strings.length.toString();
      console.log("Split ${(strings.join(", ") { x => x })} - ${len}");
    }

Also try splitting on empty string or repeated separators to check consistent
behavior across backends. Also try some edge cases that aren't always the same
in default behavior across backends. C# and Python, for example, don't split on
empty separator by default in the same way as JS.

    reportSplit("a ; b ; c", " ; ");
    reportSplit("def", "");
    reportSplit(" g  h i", " ");
    reportSplit(" ", " ");
    reportSplit("", " ");
    reportSplit("", "");
    reportSplit("🌍", "");

which outputs

```log
Split a, b, c - 3
Split d, e, f - 3
Split , g, , h, i - 5
Split ,  - 2
Split  - 1
Split  - 0
Split 🌍 - 1
```

## Operations on mut(able) list builders

We can reverse a list builder in-place using `.reverse`:

    let triangleOfFooBar = [
      [ ],
      [ "bar" ],
      [ "bar", "baz" ],
      [ "bar", "baz", "boo" ],
      [ "bar", "baz", "boo", "far" ],
      [ "bar", "baz", "boo", "far", "faz" ],

Start with at least one unsorted.

      [ "far", "faz", "foo", "bar", "baz", "boo" ],
    ].map { (list): ListBuilder<String> =>
      let builder = list.toListBuilder();

And verify in-place sorting.

      builder.sort { a, b => a <=> b };
      builder
    }.toListBuilder();

    for (var j = 0; j < triangleOfFooBar.length; ++j) {
      let row = triangleOfFooBar[j];
      console.log("Fwd  [${
        (row.join(" ") { x => x})
      }]");
      // Reversing the inner-list in place affects the next loop.
      row.reverse();
    }

    triangleOfFooBar.reverse();

    for (var j = 0; j < triangleOfFooBar.length; ++j) {
      let row = triangleOfFooBar[j];
      console.log("Back [${
        (row.join(" ") { x => x})
      }]");
    }

Which outputs the triangle forwards, then backwards with a few additions:

```log
Fwd  []
Fwd  [bar]
Fwd  [bar baz]
Fwd  [bar baz boo]
Fwd  [bar baz boo far]
Fwd  [bar baz boo far faz]
Fwd  [bar baz boo far faz foo]
Back [foo faz far boo baz bar]
Back [faz far boo baz bar]
Back [far boo baz bar]
Back [boo baz bar]
Back [baz bar]
Back [bar]
Back []
```

Exercise additional list builder features, creating a new one and appending
either a single item or a full `List` at once.

    let messageBuilder = new ListBuilder<String>();
    messageBuilder.add("hello");

Include indexed assignment.

    messageBuilder[0] = "hi";
    messageBuilder.addAll(["there", "y'all"]);
    messageBuilder.add("say", 0);
    // Would panic: messageBuilder.add("to", -1);

Check also that `toListBuilder` on a list builder makes a copy and that we also
can append one list builder's contents to another, since both `List` and
`ListBuilder` are `Listed`.

    let copyThat = messageBuilder.toListBuilder();

Modify the original after the copy.

    messageBuilder.addAll(["more", "than"], 1);
    copyThat.addAll(messageBuilder);

Verify our contents, both the original and the doubled one.

    console.log(messageBuilder.join(" ") { x => x});

Also verify that `sorted` doesn't modify the list builder and also that the
compare function doesn't just have to be default order.

    console.log(copyThat.sorted { a, b => b <=> a }.join(" ") { x => x });
    console.log(copyThat.join(" ") { x => x});

```log
say more than hi there y'all
y'all y'all there there than say say more hi hi
say hi there y'all say more than hi there y'all
```

Finally, just check that we track the type correctly. Had a bug in Lua with
this:

    let typed: ListBuilder<String> = messageBuilder;
    ignore(typed);

Also exercise methods to remove values.

    let oldLast = copyThat.removeLast();
    console.log("Removed ${oldLast} so ${copyThat.join(" ") { x => x }}");

And removing from empty would panic, so don't.

    // console.log("Removed ${
    //   new ListBuilder<String>().removeLast() orelse "none"
    // }");

Take some out, and put others in. Try different variations of missing args.

    let nextLast = copyThat.splice(copyThat.length - 1, 1);
    let oldWords = copyThat.splice(4, 3, nextLast);
    ignore(copyThat.splice(null, 0, oldWords));
    ignore(copyThat.splice(copyThat.length, null, ["and", "the", "end"]));
    console.log(copyThat.join(" ") { x => x });
    ignore(copyThat.splice(null, null, ["gone"]));
    console.log(copyThat.join(" ") { x => x });

```log
Removed y'all so say hi there y'all say more than hi there
say more than say hi there y'all there hi and the end
gone
```

    messageBuilder.clear();
    messageBuilder.add("Bye")
    console.log(messageBuilder.join(" ") { x => x });

```log
Bye
```

## Additional list operations

Slice allows selecting a sub-list, non-destructively.

    for (var i = -2; i <= 10; ++i) {
      console.log("Slice(${i}, ${i+2}): [${
        (ls.slice(i, i + 2).join(", ") { x => x.toString()})
      }]");
    }

That shows a window sliding over the list:

```log
Slice(-2, 0): []
Slice(-1, 1): [0]
Slice(0, 2): [0, 1]
Slice(1, 3): [1, 2]
Slice(2, 4): [2, 3]
Slice(3, 5): [3, 4]
Slice(4, 6): [4, 5]
Slice(5, 7): [5, 6]
Slice(6, 8): [6, 7]
Slice(7, 9): [7, 8]
Slice(8, 10): [8, 9]
Slice(9, 11): [9]
Slice(10, 12): []
```

We can also check outside list bounds with a fallback value.

    console.log("Outside fallback: ${ls.getOr(ls.length, 123)}");

```log
Outside fallback: 123
```

Out of bounds otherwise would panic, so don't do that.

    // console.log("Bubble fallback: ${ls[ls.length] orelse 456}");

## Operations on List<Boolean>

This shows lists of booleans being operated on

    do {
      let booleanToString(b: Boolean): String {
        return b.toString();
      }
      var ls = [true, false];
      var joined = ls.join(", ", booleanToString);
      var inverted = ls.map(fn(b: Boolean): Boolean {
        return !b;
      });
      var joinedAndInverted = inverted.join(", ", fn(b: Boolean): String {
        if (b) {
          return "not false";
        } else {
          return "not true";
        }
      });
      console.log("list = [${joined}]");
      console.log("list (each inverted) = [${joinedAndInverted}]");
      var onlyTrue = ls.filter(fn(b: Boolean): Boolean {
        return b;
      });
      var onlyFalse = ls.filter(fn(b: Boolean): Boolean {
        return !b;
      });
      console.log("list (== true only) = [${onlyTrue.join(", ", booleanToString)}]");
      console.log("list (== false only) = [${onlyFalse.join(", ", booleanToString)}]");
    }

```log
list = [true, false]
list (each inverted) = [not true, not false]
list (== true only) = [true]
list (== false only) = [false]
```

## Listed callback args

C\# had a problem with these, trying to make overloads in local variables.

    let something(callback: fn (Listed<String>): Void): Void {
      callback(["Called", "with", "listed."]);
    }
    let runSomething(): Void {
      something { words => console.log(words.join(" ") { word => word }) };
    }
    runSomething();

```log
Called with listed.
```
