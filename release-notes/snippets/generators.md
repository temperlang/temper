### *Generator*s allow for complex iteration and asynchrony and `yield` is the new `pause`

Coroutines, functions that may yield control back to their caller, are
useful for:

- handling asynchrony as the coroutine may yield control until more is
  available to work on, and

- implementing *forEach* over complex data structures as the coroutine
  may yield one result and suspend execution until another is needed.
  When the loop that sechedules the coroutine might want to *break*
  after only looking at a few results, it's more efficient to avoid
  the unnecessary work of finding unneeded results eagerly and making
  a list value.

In both cases, a *scheduler* is responsible for *resuming* execution
of a function that *yielded* control.

Temper is using the terms *generator* and *yield* which have familiar
meanings to users of programming languages like C#, JavaScript, and
Python.  Instead of using terminology like *coroutine* which many
programmers associate with a specific approach to parallelism.

Now, `yield` is a control-flow operator, like `return` that yields
control back to the caller.  Like `return`, it may be followed by
an expression which specifies a value to provide to the caller.
Unlike `return`, `yield` does not tear down the function execution.
Instead, it suspends execution until a *scheduler* resumes it.

*interface Generator&lt;YIELDED_TYPE&gt;* represents a suspended
computation.  It's *.next* method allows starting/resuming the
computation.

`yield` may only be used in a block lambda that *extends GeneratorFn*.

The function called with the block lambda receives a factory for
*Generator*s.  It can call that factory with the arguments listed in
the block lambda's signature, to get an instance of *interface
Generator* which, when *generator.next* is called, resumes after the
last `yield` instruction.

```temper
// A scheduler that creates a generator and then steps it.
let thrice(makeGenerator: fn (): SafeGenerator<Null>): Void {
  let generator = makeGenerator();
  console.log("Before One");
  generator.next();
  console.log("After One");
  generator.next();
  console.log("After Two");
  generator.next();
  console.log("After Three");
}

thrice { (): GeneratorResult<Null> extends GeneratorFn;;
  console.log("One");
  yield; // Yield control back to the scheduler.
  console.log("Two");
  yield;
  console.log("Three");
}
```

That outputs the below:

    Before One
    One
    After One
    Two
    After Two
    Three
    After Three
