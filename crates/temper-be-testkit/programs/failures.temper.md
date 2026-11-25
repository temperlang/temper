# Failures

Declared failures as RFC 164 proposes them, written ahead as a
specification (`SPECS` in the testkit) before later changes built them
on every backend. The log is what the interpreter gives. It assumed:

- Each failure type is under one root, a sealed interface of the
  library's own that extends core's `Error`; a library may have several
  roots. Another name for `Error` changes only the roots' lines.
- `try` is written wherever a failure propagates, which is right
  whether `try` ends up optional or required. Nothing leaves it out, and
  nothing checks what leaving it out does.
- `throw x`, `f() catch (e) { … }`, `f() catch { … }`, `do { … } catch
  (e) { … }`, and `orelse`, with parentheses wherever precedence might
  matter.
- Nothing mixes declared failures with `Bubble`, whose part is open, nor
  reads a failure's message or cause (`host-failures` shows those), nor
  depends on how the compiler holds `T throws E`.
- A `for … of` body passes failures through, as one would expect of a
  loop.

## Throwing, and defaults

A failure is an object of a declared failure type, with fields, under
a root the library declares. `throw` ends the function with it, and
`orelse` gives a default instead:

    sealed interface FailuresError extends Error {}
    class NotFound(public key: String) extends FailuresError {}

    let lookup(key: String): Int throws NotFound {
      if (key == "a") { return 1; }
      if (key == "b") { return 2; }
      throw new NotFound(key);
    }

    let found = lookup("a") orelse -1;
    let missing = lookup("z") orelse -1;
    console.log("${found} ${missing}");

```log
1 -1
```

## Catching

`catch (e)` binds the failure, whose fields are readable; `catch` alone
doesn't. The block's value is the expression's when it fails:

    let n = lookup("q") catch (e) {
      console.log("not found: ${e.key}");
      0
    };
    let m = lookup("b") catch { -1 };
    console.log("${n} ${m}");

```log
not found: q
0 2
```

## Propagating

`try` passes a failure on to the caller, whose failure type is the
same, and stops where it fails. A handler's block may `return`:

    let sum(a: String, b: String): Int throws NotFound {
      let x = try lookup(a);
      let y = try lookup(b);
      x + y
    }

    let shown(a: String, b: String): String {
      let total = sum(a, b) catch (e) { return "no ${e.key}"; };
      total.toString()
    }

    console.log("${shown("a", "b")} ${shown("a", "x")} ${shown("y", "z")}");

```log
3 no x no y
```

## Sealed failure types

A sealed interface groups failure types, and this one is a root of its
own, beside the first. A function that throws it may
throw any of them, or pass them on from a function that throws one, and
`when` tells them apart without `else`, as the function's result or
right in a handler:

    sealed interface SignError extends Error {}
    class EmptyText extends SignError {}
    class BadSign(public text: String) extends SignError {}

    let sign(text: String): Int throws BadSign {
      when (text) {
        "+" -> 1;
        "-" -> -1;
        else -> throw new BadSign(text);
      }
    }

    let parseSign(text: String): Int throws SignError {
      if (text == "") { throw new EmptyText(); }
      try sign(text)
    }

    let describe(e: SignError): String {
      when (e) {
        is EmptyText -> "empty";
        is BadSign -> "bad sign ${e.text}";
      }
    }

    for (let t of ["+", "-", "", "x"]) {
      let s = parseSign(t) catch (e) { console.log(describe(e)); continue; };
      console.log(s.toString());
    }
    let code = parseSign("?") catch (e) { when (e) { is EmptyText -> 0; is BadSign -> 9; } };
    console.log(code.toString());

```log
1
-1
empty
bad sign x
9
```

## Failures are values

A caught failure is an ordinary object: kept in a variable or a list,
passed, and thrown again later:

    var kept = new NotFound("none");
    let v = lookup("k") catch (e) { kept = e; -1 };
    let problems = [new NotFound("x"), kept];
    console.log("${v} ${problems.join(",") { p => p.key }}");

    let rethrown(p: NotFound): Int throws NotFound { throw p; }
    let w = rethrown(kept) catch (e) { console.log("again ${e.key}"); 0 };

```log
-1 x,k
again k
```

## Converting

A failure of another type passes on only explicitly, by throwing a
failure of the function's own type from a handler:

    class ConfigError(public setting: String) extends FailuresError {}

    let setting(name: String): Int throws ConfigError {
      lookup(name) catch (e) { throw new ConfigError("${name}: missing ${e.key}"); }
    }

    let port = setting("port") catch (e) { console.log(e.setting); -1 };
    let first = setting("a") orelse -1;
    console.log("${port} ${first}");

```log
port: missing port
-1 1
```

## Blocks and loops

`do { … } catch (e) { … }` handles what any `try` in its block passes
on, as a value; a handler inside it handles its own operand first. In
a loop, a handler's block may `break` or `continue`:

    let pair(a: String, b: String): String {
      do {
        let x = try lookup(a);
        let y = lookup(b) catch { 0 };
        "${x}+${y}"
      } catch (e) {
        "outer ${e.key}"
      }
    }
    console.log("${pair("a", "z")} ${pair("z", "a")}");

    var total = 0;
    for (let k of ["a", "b", "c", "a"]) {
      total += lookup(k) catch { break; };
    }
    console.log(total.toString());

```log
1+0 outer z
3
```
