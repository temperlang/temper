# Error causes

A specification, run where backends have it (`SPECS` in the testkit):
a `throw` in a handler gives the error it throws the handled one as
its cause; so an error of the same type made among the thrown one's
arguments has none (in C#, each error class has an internal
constructor that takes the cause as well, which such a `throw` calls).
One with a constructor of its own, a defaulted parameter, and a
parameter named `cause` takes it the same way. C# runs it, with host
code that reads them too (`errors-interop`'s); Rust doesn't yet.

    export sealed interface ErrorCausesError extends Error {}

    export class NotFound(public key: String) extends ErrorCausesError {}

    export let lookup(key: String): Int throws NotFound {
      if (key == "a") { return 1; }
      throw new NotFound(key);
    }

    export class Wrapped(public label: String, public first: Wrapped?) extends ErrorCausesError {}

    export let wrapLookup(name: String): Int throws Wrapped {
      lookup(name) orelse throw new Wrapped("outer", new Wrapped("inner", null))
    }

    export class Retry extends ErrorCausesError {
      public reason: String;
      public tries: Int;
      public constructor(cause: String, tries: Int = 1) {
        reason = "retry ${cause}";
        this.tries = tries;
      }
    }

    export let retried(name: String, n: Int): Int throws Retry {
      lookup(name) orelse do {
        if (n > 1) { throw new Retry(name, n); }
        throw new Retry(name);
      }
    }

Fields of errors made together, read where one is caught:

    let firstCause(name: String): String {
      wrapLookup(name).toString() catch (e) {
        let first = e.first;
        let inner = if (first != null && first.cause == null) { "none" } else { "some" };
        "${e.label} ${e.cause?.message ?? "none"}, ${first?.label ?? "-"} ${inner}"
      }
    }

    let retryCause(name: String, n: Int): String {
      retried(name, n).toString() catch (e) {
        "${e.reason} ${e.tries.toString()} ${e.cause?.message ?? "none"}"
      }
    }

    console.log(firstCause("x"));
    console.log("${retryCause("y", 1)}; ${retryCause("z", 3)}");

```log
outer not found (key: x), inner none
retry y 1 not found (key: y); retry z 3 not found (key: z)
```
