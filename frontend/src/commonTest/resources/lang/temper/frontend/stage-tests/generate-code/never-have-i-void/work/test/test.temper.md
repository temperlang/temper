# Never and Void

Checking the behaviour of void and never return

Simple *Void* return type.
Early in the frontend, we assume `Void` and `Result<Void, ...>` can be assigned
to return variables, but we stop doing that towards the end of the generate-code
stage, and instead just split `x = returnsVoid()` into `returnsVoid(); x = void`.

    let returnsVoid(): Void {
      console.log("returnsVoid");
    }

The `panic` call here should not be assigned to the return value
because it's `Void`, as above.

    let neverReturnsVoid(): Never<Void> {
      console.log("neverReturnsVoid");
      panic()
    }

For `Never<...>` return types, we also have to be circumspect.
Some backends need to produce code that passes compiler control-flow checks
that all functions return or `throw` along all paths.
Knowing that a call has type `Never` is useful, and pulling `Never` calls into
temporaries simplifies adding known aborting statements after them,
like `throw new IllegalStateException()` in Java, and still allows truncating
blocks for different backends where the compiler flags unreachable code following
a never-exiting call.

    let neverReturnsInt(): Int32 {
      console.log("neverReturnsInt");
      panic()
    }

    let returnsInt(): Int32 {
      console.log("returnsInt");
      1
    }

The calls to `neverReturnsInt` are broken out into temporaries so that the
translators only have to look a finite-depth deep in the AST to find any
calls that might require different strategies to adjust for control-flow
compiler checks.

    export let ints = [neverReturnsInt(), returnsInt(), neverReturnsInt(), returnsInt()];

Calls to the voidish ones can appear in terminal position.
No semicolons below so they're in terminal position.

    export let sometimesVoid(b: Boolean): Void {
      if (b) {
        returnsVoid()
      } else {
        neverReturnsVoid()
      }
    }
