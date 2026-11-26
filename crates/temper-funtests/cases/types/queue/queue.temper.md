# Queue Operations Functional Test

*Queues* are nice, for breadth-first-processing. They're in std.

    let { Queue } = import("std/collections");

First, we need a *Queue*

    let queue = new Queue<String>();

Let's test the initial state:

    let checkEmpty(): Void {
      console.log(if (queue.isEmpty) { "Empty" } else { "Not empty" });
    }
    checkEmpty();

```log
Empty
```

In this state, removing from the front would fail, so demonstrate proper
checking for cases that are known only dynamically.

    if (!queue.isEmpty) {
      console.log(queue.removeFirst());
    }

Let's intermix some adds-at-end and removes-from-front.

    queue.add("foo");
    queue.add("bar");
    console.log(queue.removeFirst()); // -> foo
    queue.add("baz");
    console.log(queue.removeFirst()); // -> bar
    queue.add("boo");

We see

```log
foo
bar
```

which leaves `["baz", "boo"]` on the *Queue*.
And it is now not empty.

    checkEmpty();

```log
Not empty
```

Let's loop to flush the rest, and check emptiness.

    while (!queue.isEmpty) {
      console.log(queue.removeFirst());
    }

    checkEmpty();

```log
baz
boo
Empty
```

Many adds and removes, so that a queue that drops what it removed (as
std's Temper body does) gets to, keep their order. Items may be null.

    let numbers = new Queue<Int?>();
    var sum = 0;
    for (var i = 0; i < 100; i += 1) {
      numbers.add(i);
      numbers.add(null);
      if (i % 3 == 0) {
        sum += (numbers.removeFirst() ?? -1000);
        sum += (numbers.removeFirst() ?? 1);
      }
    }
    var order = true;
    var expected = 34;
    while (!numbers.isEmpty) {
      let n = numbers.removeFirst();
      if (n != null) {
        if (n != expected) { order = false; }
        expected += 1;
      }
    }
    console.log("sum ${sum}, in order ${order}, through ${expected}");

```log
sum 595, in order true, through 100
```
