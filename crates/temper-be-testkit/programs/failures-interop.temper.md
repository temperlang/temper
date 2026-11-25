# Failures interop

Declared failures as each backend's host code sees them. Every backend
runs this program, then host code of its own beside it (its crate's
`tests/host/`) that catches or matches the failures by their own types,
the library's root, `FailuresInteropError`, a sealed interface's, and
its language's base for errors; reads their fields, messages, and
causes (the failure being handled where one was thrown); and sees a
panic pass, as none of them. What's particular to one language is here
too, valid on every backend: a member named as one of JS `Error`'s
(`name`), and a `catch` whose handler jumps (Rust, C++, and Lua). What
not every backend has yet is in specifications beside it (`SPECS` in
the testkit): `failure-causes`, failures made among a thrown one's
arguments and a constructor's parameter named `cause`, which C#'s host
code reads too, and `failure-jumps`.

    export sealed interface FailuresInteropError extends Error {}

    export class NotFound(public key: String) extends FailuresInteropError {}

    export sealed interface SignError extends FailuresInteropError {}
    export class BadSign(public text: String) extends SignError {}
    export class EmptyText extends SignError {
      public get message(): String { "no text" }
    }

    export class LookupFailed(public name: String) extends FailuresInteropError {}

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

    export let earlyCause(name: String): String {
      madeBefore(name).toString() catch (e) {
        let cause = e.cause;
        if (cause != null) { cause.message } else { "nothing" }
      }
    }

An exhaustive `when` over a sealed type needs no `else`, and panics
where host code passes a value of a type of its own under it:

    export let signName(e: SignError): String {
      when (e) {
        is BadSign -> "bad sign";
        is EmptyText -> "empty text";
      }
    }

An `orelse` handles a bubble and a declared failure alike (a
multi-catch in Java, a `catch` with a filter in C#), and a `catch`
whose handler jumps out keeps the failure for it (a `catch` whose body
jumps out of its loop is `failure-jumps`, which Rust and C++ don't have
yet):

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

    export let firstMissing(keys: List<String>): String {
      for (let k of keys) {
        let n = lookup(k) catch (e) { return e.key; };
        if (n < 0) { break; }
      }
      "none"
    }

    console.log("${lookup("a")} ${sign("-")} ${setting("a")}");
    console.log("${mixed("a", 0)} ${mixed("z", 1)} ${mixed("a", 5)}");
    console.log("${explained("port")}; ${explained("a")}; ${signMessage("x")}");
    console.log("${earlyCause("soon")}; ${firstMissing(["a", "b", "c"])} ${firstMissing(["a"])}");
    console.log("${firstSign(["", "+"])} ${firstSign(["", "?"])} ${firstSign([])}");

```log
1 -1 1
-1 -1 3
lookup failed (name: port) for port, from not found (key: port); 1; bad sign (text: x)
not found (key: soon); b none
1 bad ? none
```
