# Failure generators

A specification (`SPECS` in the testkit): generators whose bodies may
throw, as RFC 164 proposes for now, without a failure type argument.
It assumes what `failures` does, and, of what's still open:

- A generator body that declares `throws E` makes a `Generator<Y>`,
  whose `next()` throws `Error`, any error, which callers handle; a
  body that declares nothing makes a `SafeGenerator<Y>`, which can't
  fail, and fits where a `Generator<Y>` is wanted. Its `next()` throws
  `Error` too, as `Generator`'s does; its `nextSafe()` doesn't.
- A generator body may declare `throws Error`, so it can pass on any
  error, as an inner generator's.
- A failure ends the generator: later calls of `next()` give a
  `DoneResult`.

What every section uses:

    sealed interface FailureGeneratorsError extends Error {}
    class TooBig(public n: Int) extends FailureGeneratorsError {}

    let generated<T>(make: fn (): Generator<T>): Generator<T> { make() }
    let safely<T>(make: fn (): SafeGenerator<T>): SafeGenerator<T> { make() }

    let take(g: Generator<Int>, n: Int): String {
      let taken = new ListBuilder<String>();
      while (taken.length < n) {
        let r = g.next() catch (e) {
          let why = when (e) {
            is TooBig -> "too big ${e.n}";
            else -> e.message;
          };
          return "${taken.join(" ") { s => s }} ${why}";
        };
        if (r is ValueResult<Int>) {
          taken.add(r.value.toString());
        } else {
          taken.add("done");
          break;
        }
      }
      taken.join(" ") { s => s }
    }

## A body that throws

`upTo` counts until it passes its limit, and throws there; the
generator is done after that:

    let upTo(limit: Int): Generator<Int> {
      generated { (): GeneratorResult<Int> throws TooBig extends GeneratorFn =>
        var i = 1;
        while (true) {
          if (i > limit) { throw new TooBig(i); }
          yield i;
          i += 1;
        }
      }
    }

    console.log(take(upTo(3), 9));
    let one = upTo(1);
    console.log(take(one, 9));
    console.log(take(one, 9));

```log
1 2 3 too big 4
1 too big 2
done
```

## Failures in the body, handled there or passed on

A body handles one failure itself and passes another on with `try`:

    let checked(n: Int, limit: Int): Int throws TooBig {
      if (n > limit) { throw new TooBig(n); }
      n
    }

    let squares(limit: Int): Generator<Int> {
      generated { (): GeneratorResult<Int> throws TooBig extends GeneratorFn =>
        yield (checked(50, limit) orelse 0);
        var i = 1;
        while (true) {
          yield try checked(i * i, limit);
          i += 1;
        }
      }
    }

    console.log(take(squares(10), 9));

```log
0 1 4 9 too big 16
```

## A generator that can't fail

Where a `Generator<Int>` is wanted, a `SafeGenerator<Int>` fits:

    let count(n: Int): SafeGenerator<Int> {
      safely { (): GeneratorResult<Int> extends GeneratorFn =>
        for (var i = 1; i <= n; i += 1) { yield i; }
      }
    }

    console.log(take(count(2), 9));

```log
1 2 done
```

Its `nextSafe()` needs no handling; its `next()` does, as
`Generator`'s:

    let shown(r: GeneratorResult<Int>): String {
      if (r is ValueResult<Int>) { r.value.toString() } else { "done" }
    }

    let firstTwo(g: SafeGenerator<Int>): String {
      let a = shown(g.nextSafe());
      let b = shown(g.next()) orelse "failed";
      "${a} ${b}"
    }

    console.log(firstTwo(count(1)));

```log
1 done
```

## An inner generator's failure, passed on

A body that declares `throws Error` passes on whatever an inner
generator's `next()` throws:

    let doubled(inner: Generator<Int>): Generator<Int> {
      generated { (): GeneratorResult<Int> throws Error extends GeneratorFn =>
        while (true) {
          let r = try inner.next();
          if (r is ValueResult<Int>) { yield r.value * 2; } else { break; }
        }
      }
    }

    console.log(take(doubled(upTo(2)), 9));
    console.log(take(doubled(count(2)), 9));

```log
2 4 too big 3
2 4 done
```
