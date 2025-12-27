# Type Checked Locals Functional Test

This tests how illegal assignments are handled on backends.

## Metadata

```text
@meta:arg allowedErrors = setOf(
@meta:arg     MessageTemplate.ExpectedSubType.name,
@meta:arg     MessageTemplate.IllegalAssignment.name,
@meta:arg     MessageTemplate.ExpectedFunctionType.name,
@meta:arg ),
```

## Test

First, set up some sophisticated commentary.

    let yay(): Void { console.log("yay") };
    let meh(): Void { console.log("meh") };
    let boo(): Void { console.log("boo") };

Test a static type error:

    console.log("Checkpoint 0");
    do {
      let a: Int;
      a = "1"; // Static error, but not fatal
      bubble();
    } orelse yay();

```log
Checkpoint 0
yay
```

Then with the correct type:

    console.log("Checkpoint 1");
    do {
      let a: Int = 1;
      yay();
    } orelse boo();

```log
Checkpoint 1
yay
```

Test an incorrect type in an inner scope.

    console.log("Checkpoint 2");
    do {
      var a: Int = 1;
      do {
        a = "2"; // Static error, but not fatal
        bubble();
        boo();
      } orelse meh();
      if (a == 1) {
        meh();
      } else {
        meh();
      }
    }

```log
Checkpoint 2
meh
meh
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
