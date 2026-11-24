# Declared failures, as Rust callers see them

A library's failure types are Rust error types: each failure class a
struct implementing `Display` (its message) and `std::error::Error`
(its cause as `source()`), each sealed failure interface an `enum` over
its types, and the library's base its crate's `Error` enum, which
`From` takes every failure into, so `?` does. Functions that fail
return `Result<T, F>`. `runs_and_lints` has host code
(`tests/host/failures.rs`) match them by their own types, the sealed
interface's enum, and the crate's `Error`, read their fields, `Display`
give their messages and `source()` the failure being handled where one
was thrown, `?` them into the crate's `Error` and a boxed error, and
see a panic stay a panic, never an `Err`.

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

An `orelse` handles a bubble and a declared failure alike, and a
`catch` whose handler jumps:

    export let mixed(key: String, n: Int): Int {
      (lookup(key) + 10 / n) orelse -1
    }

    export let firstSign(texts: List<String>): String {
      for (let t of texts) {
        let s = sign(t) catch (e) {
          if (e is BadSign) { return "bad ${e.text}"; }
          continue;
        };
        return s.toString();
      }
      "none"
    }

    console.log("${lookup("a")} ${sign("-")} ${setting("a")}");
    console.log("${mixed("a", 0)} ${mixed("z", 1)} ${mixed("a", 5)}");
    console.log("${explained("port")}; ${explained("a")}; ${signMessage("x")}");
    console.log("${firstSign(["", "+"])} ${firstSign(["", "?"])} ${firstSign([])}");

```log
1 -1 1
-1 -1 3
lookup failed (name: port), from not found (key: port); 1; bad sign (text: x)
1 bad ? none
```
