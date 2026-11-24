# Declared failures, as JS callers see them

A library's failure types are classes under the library's own base,
`JsFailuresError`, a JS `Error`: `runs` has host code
(`tests/host/failures.mjs`) catch them with `instanceof` by their own
types, the base, a sealed interface's (an abstract class), and `Error`,
read their fields, `message`, `name`, and `stack`, and `cause`, the
failure being handled where one was thrown; and a panic, a plain
`Error`, passes a `catch` that checks the type. A failure type's member
named as one of `Error`'s own (`name`) takes a `_`, as `name_`, since
`name` is the type's name.

    export class NotFound(public key: String) extends Error {}

    export sealed interface SignError extends Error {}
    export class BadSign(public text: String) extends SignError {}
    export class EmptyText extends SignError {
      public get message(): String { "no text" }
    }

    export class LookupFailed(public name: String) extends Error {}

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

A failure made before it's thrown in a handler takes the handled one as
its cause too, unless it has one already, as one thrown again keeps its
first:

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

Temper code reads a failure's message, cause, and fields, also through
a sealed interface:

    export let explained(name: String): String {
      setting(name).toString() catch (e) {
        let cause = e.cause;
        let why = if (cause != null) { cause.message } else { "nothing" };
        "${e.message} for ${e.name}, from ${why}"
      }
    }

    export let signMessage(text: String): String {
      sign(text).toString() catch (e) { e.message }
    }

An `orelse` handles a bubble and a declared failure alike, and a panic
passes it:

    export let mixed(key: String, n: Int): Int {
      (lookup(key) + 10 / n) orelse -1
    }

    console.log("${lookup("a")} ${sign("-")} ${setting("a")}");
    console.log("${mixed("a", 0)} ${mixed("z", 1)} ${mixed("a", 5)}");
    console.log("${explained("port")}; ${explained("a")}; ${signMessage("x")}");

```log
1 -1 1
-1 -1 3
lookup failed (name: port) for port, from not found (key: port); 1; bad sign (text: x)
```
