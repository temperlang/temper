# Type Checked Locals Functional Test

This tests how calls with arguments of the wrong type are handled on backends.

## Metadata

```text
@meta:arg allowedErrors = setOf(
@meta:arg     MessageTemplate.ExpectedSubType.name,
@meta:arg     MessageTemplate.ExpectedFunctionType.name,
@meta:arg ),
```

## Test

First, set up some sophisticated commentary.

    let yay(): Void { console.log("yay") };
    let meh(): Void { console.log("meh") };
    let boo(): Void { console.log("boo") };

An assignment of the wrong type, like `a = "1"` to an `Int`, is not
tested here. It is a static error, and the assignment is replaced by a
failure carrying that error, so it panics in the interpreter rather than
running as written. See issue #511 and the
`rejected-assignment-fails-at-run-time` stage test.

Assign with the correct type:

    console.log("Checkpoint 1");
    do {
      let a: Int = 1;
      yay();
    } orelse boo();

```log
Checkpoint 1
yay
```

Verify updating a value with a correct type.

    console.log("Checkpoint 3");
    do {
      var a: Int = 1;
      a = 2;
      yay()
    } orelse boo();

```log
Checkpoint 3
yay
```

Check calling with an invalid argument type.

    console.log("Checkpoint 4");
    let f(x: Int): (Int throws Bubble) { x }
    do { f(0); yay() } orelse boo();
    do { f("0"); meh() } orelse meh();

```log
Checkpoint 4
yay
meh
```

Check calling with an invalid argument type, given inference through a return statement.

    console.log("Checkpoint 5");
    let g(x: Int): (Int throws Bubble) { return x }
    do { g(0); yay() } orelse boo();
    do { g("0"); meh() } orelse meh();

```log
Checkpoint 5
yay
meh
```

Same as checkpoint 4, except return type isn't in parens.

    console.log("Checkpoint 6");
    let i(x: Int): Int throws Bubble { x }
    do { i(0); yay() } orelse boo();
    do { i("0"); meh() } orelse meh();

```log
Checkpoint 6
yay
meh
```
