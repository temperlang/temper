# Failure parameters

A specification, not run yet: functions generic over their callbacks'
failure types, as RFC 164 proposes, which later changes build (`SPECS`
in the testkit). The log is what the interpreter should give. It
assumes what `failures` does, and, of what's still open:

- The failure type is an ordinary type parameter, written here with its
  bound, `E extends Error`; whether the bound may be left out is open.
- A callback that can't fail makes `E` `Never<Error>`, and calls with
  it need no `try`; nothing writes one there.
- A `@fun interface` takes `E` as any other type parameter.
- Two callbacks of one function share one `E`.

    class Missing(public key: String) extends Error {}

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
