# Declared failures, as Lua callers see them

A library's failure types are the runtime's types under the library's
own base, `LuaFailuresError`, which is under the runtime's
`temper.Failure`: `runs` has host code (`tests/host/failures.lua`)
catch them with `pcall` and tell them apart with `temper.instance_of`
by their own types, the base, a sealed interface's, and
`temper.Failure`, read their fields, `tostring` and `message` give their
messages, and `cause` the failure being handled where one was thrown;
and a panic, a string, is none of them.

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

    export let earlyCause(name: String): String {
      madeBefore(name).toString() catch (e) {
        let cause = e.cause;
        if (cause != null) { cause.message } else { "nothing" }
      }
    }

An `orelse` handles a bubble and a declared failure alike, a `catch`
whose block jumps out keeps the failure for it, and one whose body
jumps out of its loop does so:

    export let mixed(key: String, n: Int): Int {
      (lookup(key) + 10 / n) orelse -1
    }

    export let firstMissing(keys: List<String>): String {
      for (let k of keys) {
        let n = lookup(k) catch (e) { return e.key; };
        if (n < 0) { break; }
      }
      "none"
    }

    export let scan(keys: List<String>): String {
      var out = "";
      for (let k of keys) {
        do {
          if (k == "stop") { break; }
          out = "${out}${lookup(k)}";
        } catch (e) {
          out = "${out}!${e.key}";
        }
      }
      out
    }

    console.log("${lookup("a")} ${sign("-")} ${setting("a")}");
    console.log("${mixed("a", 0)} ${mixed("z", 1)} ${mixed("a", 5)}");
    console.log("${explained("port")}; ${explained("a")}; ${signMessage("x")}");
    console.log("${earlyCause("soon")}; ${firstMissing(["a", "b", "c"])} ${firstMissing(["a"])}");
    console.log(scan(["a", "x", "stop", "a"]));

```log
1 -1 1
-1 -1 3
lookup failed (name: port), from not found (key: port); 1; bad sign (text: x)
not found (key: soon); b none
1!x
```
