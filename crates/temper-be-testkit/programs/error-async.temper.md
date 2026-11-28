# Error async

A specification, not run yet: promises that carry a declared error,
as RFC 164 proposes, which later changes build (`SPECS` in the
testkit). The log is what the interpreter should give. It assumes what
`declared-errors` does, and, of what's still open:

- `Promise<T, E>` and `PromiseBuilder<T, E>` name an error type, and
  `breakPromise(failure)` breaks a promise with an error object of it.
- `await p` throws `p`'s `E`, which `try await` passes on and handlers
  handle; a promise whose `E` is `Never<Error>` can't break, so its
  `await` needs neither.
- A one-type-argument `Promise<T>` is `Promise<T, BrokenPromise>`:
  its `await` throws core's `BrokenPromise`, as every `await` does
  today. Nothing here uses one, nor lets an error leave an `async`
  block, which is open. Generators are left for later.

One block waits on promises, in order, and another settles them all,
breaking two with errors:

    sealed interface ErrorAsyncError extends Error {}
    class Timeout(public after: Int) extends ErrorAsyncError {}

    let first = new PromiseBuilder<Int, Timeout>();
    let second = new PromiseBuilder<Int, Timeout>();
    let sure = new PromiseBuilder<Int, Never<Error>>();
    let left = new PromiseBuilder<Int, Timeout>();
    let right = new PromiseBuilder<Int, Timeout>();

    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      let n = await first.promise catch (e) { console.log("timed out after ${e.after}"); -1 };
      let m = await second.promise orelse -2;
      let s = await sure.promise;
      console.log("got ${n} ${m} ${s}");
      let total = do {
        let a = try await left.promise;
        let b = try await right.promise;
        a + b
      } catch (e) {
        console.log("gave up after ${e.after}");
        0
      };
      console.log("total ${total}");
    }

    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      first.breakPromise(new Timeout(5));
      second.complete(7);
      sure.complete(9);
      left.complete(1);
      right.breakPromise(new Timeout(3));
    }

```log
timed out after 5
got -1 7 9
gave up after 3
total 0
```
