# List Builder Set at a Negative Index

Setting a list builder's value at a negative index panics on every
backend, as reading one does, rather than counting from the end as
some hosts' lists do.

```text
@meta:arg expectRunFailure = true,
```

    let put(builder: ListBuilder<Int>, i: Int, value: Int): Void {
      builder.set(i, value);
    }

    let builder = new ListBuilder<Int>();
    builder.add(1);
    builder.add(2);
    put(builder, 1, 5);
    console.log(builder.join(", ") { n => n.toString() });
    console.log("before");
    put(builder, -1, 0);
    console.log("not reached");

```log
1, 5
before
```

The run ends in each backend's panic, with this message:

```panic
index -1 out of bounds for length 2
```
