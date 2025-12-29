# Deque Operations Functional Test

*Deques* are nice, for breadth-first-processing.

First, we need a *Deque*

```temper
let deque = new Deque<String>();
```

Let's test the initial state:

```temper
let checkEmpty() {
    print((deque.isEmpty) ? "Empty" : "Not empty");
}
checkEmpty();
```
```stdout
Empty
```

But attempting to remove from an empty deque causes a panic, so don't try that:

```temper
// print(deque.removeFirst());
```

Let's intermix some adds-at-end and removes-from-front.

```temper
deque.add("foo");
deque.add("bar");
print(deque.removeFirst()); // -> foo
deque.add("baz");
print(deque.removeFirst()); // -> bar
deque.add("boo");
```

We see
```stdout
foo
bar
```

which leaves `["baz", "boo"]` on the *Deque*.
And it is now not empty.

```temper
checkEmpty();
```
```stdout
Not empty
```

Let's loop to flush the rest, and check emptiness.

```temper
while (!deque.isEmpty) {
    print(deque.removeFirst());
}

checkEmpty();
```
```stdout
baz
boo
Empty
----------------
Void
```
