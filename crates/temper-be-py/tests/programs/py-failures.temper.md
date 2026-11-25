# Declared failures, as Python callers see them

A library's failure types are Python exceptions under the root it
declares, `PyFailuresError`, an `Exception`: `host_interop` (`tests/host/
interop.py`) catches them by their own types, the root, and a sealed
interface's, reads their fields, `str()` gives their messages, and a
failure thrown while another was handled has it as its `__cause__`;
and a panic passes `except Exception`.

    export sealed interface PyFailuresError extends Error {}

    export class NotFound(public key: String) extends PyFailuresError {}

    export sealed interface SignError extends PyFailuresError {}
    export class BadSign(public text: String) extends SignError {}
    export class EmptyText extends SignError {
      public get message(): String { "no text" }
    }

    export class LookupFailed(public name: String) extends PyFailuresError {}

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

An exhaustive `when` over a sealed type needs no `else`, and panics
where host code passes a value of a type of its own under it:

    export let signName(e: SignError): String {
      when (e) {
        is BadSign -> "bad sign";
        is EmptyText -> "empty text";
      }
    }

An `orelse` handles a bubble and a declared failure alike:

    export let mixed(key: String, n: Int): Int {
      (lookup(key) + 10 / n) orelse -1
    }

    console.log("${lookup("a")} ${sign("-")} ${setting("a")}");
    console.log("${mixed("a", 0)} ${mixed("z", 1)} ${mixed("a", 5)}");
    console.log("${explained("port")}; ${explained("a")}; ${signMessage("x")}");

```log
1 -1 1
-1 -1 3
lookup failed (name: port), from not found (key: port); 1; bad sign (text: x)
```
