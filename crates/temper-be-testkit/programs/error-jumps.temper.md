# Error jumps

A specification, run where backends have it (`SPECS` in the testkit):
a `catch` whose body jumps out of its loop, which Rust and C++ say
they don't have yet. Lua runs it.

    export sealed interface ErrorJumpsError extends Error {}
    export class NotFound(public key: String) extends ErrorJumpsError {}

    export let lookup(key: String): Int throws NotFound {
      if (key == "a") { return 1; }
      throw new NotFound(key);
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

    console.log(scan(["a", "x", "stop", "a"]));

```log
1!x
```
