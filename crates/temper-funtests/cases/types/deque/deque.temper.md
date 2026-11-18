# Deque Operations Functional Test

*Deques* are nice, for breadth-first-processing.

First, we need a *Deque*

    let deque = new Deque<String>();

Let's test the initial state:

    let checkEmpty(): Void {
      console.log(if (deque.isEmpty) { "Empty" } else { "Not empty" });
    }
    checkEmpty();

```log
Empty
```

In this state, removing from the front would fail, so demonstrate proper
checking for cases that are known only dynamically.

    if (!deque.isEmpty) {
      console.log(deque.removeFirst());
    }

Let's intermix some adds-at-end and removes-from-front.

    deque.add("foo");
    deque.add("bar");
    console.log(deque.removeFirst()); // -> foo
    deque.add("baz");
    console.log(deque.removeFirst()); // -> bar
    deque.add("boo");

We see

```log
foo
bar
```

which leaves `["baz", "boo"]` on the *Deque*.
And it is now not empty.

    checkEmpty();

```log
Not empty
```

Let's loop to flush the rest, and check emptiness.

    while (!deque.isEmpty) {
      console.log(deque.removeFirst());
    }

    checkEmpty();

```log
baz
boo
Empty
```
