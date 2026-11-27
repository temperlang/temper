# Failure parameters

A specification, which the interpreter runs and the backends don't yet:
functions generic over their callbacks' failure types, as RFC 164
proposes (`SPECS` in the testkit). It assumes what `failures` does,
and, of what's still open:

- The failure type is an ordinary type parameter, written here with its
  bound, `E extends Error`, which may be left out where `E` follows
  `throws`.
- A callback that can't fail makes `E` `Never<Error>`, and calls with
  it need no `try`; nothing writes one there.
- A `@fun interface` takes `E` as any other type parameter.
- Two callbacks of one function share one `E`, the join of what they
  fail with.

What every section uses:

    sealed interface FailureParamsError extends Error {}
    class Missing(public key: String) extends FailureParamsError {}

    let find(key: String): Int throws Missing {
      if (key == "one") { return 1; }
      if (key == "two") { return 2; }
      throw new Missing(key);
    }

    @fun interface Mapper<T, R, E extends Error>(x: T): R throws E;

    let mapAll<T, R, E extends Error>(xs: List<T>, f: Mapper<T, R, E>): List<R> throws E {
      let out = new ListBuilder<R>();
      for (let x of xs) { out.add(try f(x)); }
      out.toList()
    }

## Callbacks that can't fail

`E` is `Never<Error>`, so the call needs no `try` or handler, and
neither does a call of a `Mapper` declared that way:

    let doubled = mapAll([1, 2, 3], fn (n: Int): Int { n * 2 });
    let safe: Mapper<Int, Int, Never<Error>> = fn (n: Int): Int { n + 1 };
    console.log("${doubled.join(",") { n => n.toString() }} ${safe(1)}");

```log
2,4,6 2
```

## Callbacks that can

`E` is the callback's failure type, which the call passes on or
handles, from a function literal or a function's name:

    let found(keys: List<String>): List<Int> throws Missing {
      try mapAll(keys, fn (k: String): Int throws Missing { try find(k) })
    }

    let shown(keys: List<String>): String {
      let ns = found(keys) catch (e) { return "missing ${e.key}"; };
      ns.join(",") { n => n.toString() }
    }

    let named = mapAll(["two", "one"], find) catch { [] };
    console.log("${shown(["one", "two"])} ${shown(["one", "six", "ten"])} ${named.length}");

```log
1,2 missing six 2
```

## Where a failing callback is wanted

A function that can't fail fits where one that may is wanted:

    let applied(f: Mapper<Int, Int, Missing>, n: Int): Int throws Missing { try f(n) }
    let six = applied(fn (n: Int): Int { n * 3 }, 2) orelse -1;
    console.log(six.toString());

```log
6
```

## Handlers in generic code

Generic code handles `E` without knowing what it is:

    let failures<T, R, E extends Error>(xs: List<T>, f: Mapper<T, R, E>): Int {
      var failed = 0;
      for (let x of xs) {
        do { try f(x); } catch { failed += 1; }
      }
      failed
    }

    let some = failures(["one", "six", "two", "ten"], find);
    let none = failures([1, 2], fn (n: Int): Int { n });
    console.log("${some} ${none}");

```log
2 0
```

A handler there may bind the failure, keep it, and throw it again:

    let sumAll<T, E extends Error>(xs: List<T>, f: Mapper<T, Int, E>): Int throws E {
      var total = 0;
      var first: E? = null;
      for (let x of xs) {
        do { total += try f(x); } catch (e) { if (first == null) { first = e; } }
      }
      let failed = first;
      if (failed != null) { throw failed; }
      total
    }

    let summed = sumAll(["one", "two"], find) orelse -1;
    let unsummed = sumAll(["six", "one", "ten"], find) catch (e) { console.log("first missing ${e.key}"); -1 };
    console.log("${summed} ${unsummed} ${sumAll([1, 2], safe)}");

```log
first missing six
3 -1 5
```

## Two callbacks

Two callbacks share `E`: one that can't fail fits beside one that may,
and `E` is the one that may's:

    let both<E extends Error>(a: Mapper<String, Int, E>, b: Mapper<String, Int, E>, key: String): Int throws E {
      (try a(key)) + (try b(key))
    }

    let twelve = both(find, fn (k: String): Int { 10 }, "two") orelse -1;
    let lost = both(find, find, "six") catch (e) { console.log("lost ${e.key}"); -1 };
    console.log("${twelve} ${lost}");

```log
lost six
12 -1
```

## Two callbacks that fail differently

Their failures' join is `E`, the sealed type both are under:

    class Unknown(public word: String) extends FailureParamsError {}

    let word(key: String): Int throws Unknown {
      if (key == "ten") { return 10; }
      throw new Unknown(key);
    }

    let either(key: String): String {
      let n = both(find, word, key) catch (e) {
        return when (e) {
          is Missing -> "no ${e.key}";
          is Unknown -> "unknown ${e.word}";
        };
      };
      n.toString()
    }
    console.log("${either("one")} ${either("ten")}");

```log
unknown one no ten
```

## Core's functions that take callbacks

Core's `Listed` and `Mapped` functions that take callbacks fail as their
callbacks do, and can't where those can't:

    let parts(keys: List<String>): String throws Missing {
      let found = keys.filter { k => find(k) > 1 };
      let total = keys.reduceFrom(0) { n, k => n + find(k) };
      "${found.join(",") { k => k }} ${total} ${keys.join("+") { k => find(k).toString() }}"
    }
    console.log(parts(["one", "two"]) orelse "?");
    console.log(parts(["one", "six"]) catch (e) { "missing ${e.key}" });
    console.log([3, 1, 2].sorted { a, b => a - b }.join(",") { n => n.toString() });

```log
two 3 1+2
missing six
1,2,3
```

## Function values passed on

A `@fun` interface's value goes where core's functions take a callback,
and generic code passes its own callbacks on, to its functions and to
core's:

    let mapTwice<T, E extends Error>(xs: List<T>, f: Mapper<T, T, E>): List<T> throws E {
      let once = try mapAll(xs, f);
      try once.map(f)
    }

    let upToTwo(n: Int): Int throws Missing {
      if (n > 2) { throw new Missing(n.toString()); }
      n + 1
    }

    let bumped = [1, 2].map(safe);
    let twice = mapTwice([1, 2], safe);
    let failed = mapTwice([1, 2], upToTwo) catch (e) { console.log("missing ${e.key}"); [] };
    console.log("${bumped.join(",") { n => n.toString() }} ${twice.join(",") { n => n.toString() }} ${failed.length}");

```log
missing 3
2,3 3,4 0
```

## std's regex replace

std's `Regex.replace` fails as its callback does: the first failure
passes out, with no result, and the callback isn't called again. One
whose callback can't fail, a literal or a function by name, needs no
handler:

    let { Match } = import("std/regex");

    let keyed(text: String): String throws Missing {
      /[a-z]+/.replace(text) { m =>
        console.log("seen ${m.full.value}");
        find(m.full.value).toString()
      }
    }

    let allKeyed = keyed("one+two") orelse "?";
    let noneKeyed = keyed("one+six+ten") catch (e) { "missing ${e.key}" };
    let bracketed(m: Match): String { "<${m.full.value}>" }
    let plain = /o/.replace("two") { m => "0" };
    console.log("${allKeyed} ${noneKeyed} ${plain} ${/w/.replace("two", bracketed)}");

```log
seen one
seen two
seen one
seen six
1+2 missing six tw0 t<w>o
```
