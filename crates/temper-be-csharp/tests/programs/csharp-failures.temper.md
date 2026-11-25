# Declared failures, as C# callers see them

A library's failure types are exceptions under the root it declares,
`CsharpFailuresException` (`CsharpFailuresError` in Temper), an
`Exception`: `runs` has host code
(`tests/host/FailuresInterop.cs`) catch them by their own types, the
root, and a sealed interface's (an abstract class), read their
properties, `Message` give their messages, and `InnerException` the
failure being handled where one was made and thrown; and a panic passes
host code that catches only failures.

    export sealed interface CsharpFailuresError extends Error {}

    export class NotFound(public key: String) extends CsharpFailuresError {}

    export sealed interface SignError extends CsharpFailuresError {}
    export class BadSign(public text: String) extends SignError {}
    export class EmptyText extends SignError {
      public get message(): String { "no text" }
    }

    export class LookupFailed(public name: String) extends CsharpFailuresError {}

    export let lookup(key: String): Int throws NotFound {
      if (key == "a") { return 1; }
      throw new NotFound(key);
    }

    export let sign(text: String): Int throws SignError {
      when (text) {
        "+" -> 1;
        "-" -> -1;
        "" -> throw new EmptyText();
        else -> throw new BadSign(text);
      }
    }

    export let setting(name: String): Int throws LookupFailed {
      lookup(name) catch { throw new LookupFailed(name); }
    }

Each failure class has an internal constructor that takes the cause
as well, which a `throw` in a handler calls; so a failure of the same
type made among the thrown one's arguments has none. One with a
constructor of its own, a defaulted parameter, and a parameter named
`cause` takes it the same way:

    export class Wrapped(public label: String, public first: Wrapped?) extends CsharpFailuresError {}

    export let wrapLookup(name: String): Int throws Wrapped {
      lookup(name) catch { throw new Wrapped("outer", new Wrapped("inner", null)); }
    }

    export class Retry extends CsharpFailuresError {
      public reason: String;
      public tries: Int;
      public constructor(cause: String, tries: Int = 1) {
        reason = "retry ${cause}";
        this.tries = tries;
      }
    }

    export let retried(name: String, n: Int): Int throws Retry {
      lookup(name) catch {
        if (n > 1) { throw new Retry(name, n); }
        throw new Retry(name);
      }
    }

A failure made before it's thrown in a handler takes the handled one as
its cause too, unless it has one already, as one thrown again keeps its
first; C# can't set an exception's `InnerException` once it's made, so
the first is Temper's `cause` alone there:

    export let madeBefore(name: String): Int throws LookupFailed {
      let failure = new LookupFailed(name);
      lookup(name) catch { throw failure; }
    }

    export let thrownAgain(name: String): Int throws LookupFailed {
      setting(name) catch (e) { lookup("q") catch { throw e; } }
    }

And a panic, which no handler catches:

    export let risky(n: Int): Int throws NotFound {
      if (n > 1) { panic() }
      lookup("a")
    }

Temper code reads a failure's message and cause, also through a sealed
interface:

    export let explained(name: String): String {
      setting(name).toString() catch (e) {
        let cause = e.cause;
        let why = if (cause != null) { cause.message } else { "nothing" };
        "${e.message}, from ${why}"
      }
    }

    export let signMessage(text: String): String {
      sign(text).toString() catch (e) { e.message }
    }

    export let earlyCause(name: String): String {
      madeBefore(name).toString() catch (e) {
        let cause = e.cause;
        if (cause != null) { cause.message } else { "nothing" }
      }
    }

An `orelse` handles a bubble and a declared failure alike, a `catch`
with a filter in C#:

    export let mixed(key: String, n: Int): Int {
      (lookup(key) + 10 / n) orelse -1
    }

    console.log("${lookup("a")} ${sign("-")} ${setting("a")}");
    console.log("${mixed("a", 0)} ${mixed("z", 1)} ${mixed("a", 5)}");
    console.log("${explained("port")}; ${explained("a")}; ${signMessage("x")}");
    console.log(earlyCause("soon"));

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
1 -1 1
-1 -1 3
lookup failed (name: port), from not found (key: port); 1; bad sign (text: x)
not found (key: soon)
outer not found (key: x), inner none
retry y 1 not found (key: y); retry z 3 not found (key: z)
```
