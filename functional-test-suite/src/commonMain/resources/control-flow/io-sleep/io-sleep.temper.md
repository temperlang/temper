# IO Sleep Functional Test

This tests the `sleep()` function from `std/io`.

    let {sleep} = import("std/io");

The test runs inside an async block since `sleep` returns a `Promise<Empty>`.

    async { (): GeneratorResult<Empty> extends GeneratorFn =>

## Sleep returns and execution continues

We verify that `sleep` completes (resolves its promise) and execution
continues after `await`. We use a short delay to avoid slowing the
test suite.

      do {
        console.log("before sleep");
        await sleep(10);
        console.log("after sleep");
      } orelse panic();

```log
before sleep
after sleep
```

## Multiple sleeps in sequence

      do {
        console.log("a");
        await sleep(10);
        console.log("b");
        await sleep(10);
        console.log("c");
      } orelse panic();

```log
a
b
c
```

## Sleep with zero milliseconds

A zero-ms sleep should resolve immediately.

      do {
        console.log("before zero");
        await sleep(0);
        console.log("after zero");
      } orelse panic();

```log
before zero
after zero
```

## Sleep interleaved with computation

      do {
        var sum = 0;
        for (var i = 0; i < 3; ++i) {
          sum = sum + i;
          await sleep(5);
        }
        console.log("sum: ${sum.toString()}");
      } orelse panic();

```log
sum: 3
```

    } // ends async {...}
