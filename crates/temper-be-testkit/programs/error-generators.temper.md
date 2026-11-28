# Error generators

A specification (`SPECS` in the testkit): generators whose bodies may
throw, as RFC 164 proposes for now, without an error type argument.
It assumes what `declared-errors` does, and, of what's still open:

- A generator body that declares `throws E` makes a `Generator<Y>`,
  whose `next()` throws core's `BrokenGenerator`, whose `cause` is the
  error the body threw, which callers handle; a body that declares
  nothing makes a `SafeGenerator<Y>`, which can't fail, and fits where
  a `Generator<Y>` is wanted. Its `next()` throws `BrokenGenerator` too,
  as `Generator`'s does; its `nextSafe()` doesn't.
- A body passing on an inner generator's error declares `throws
  BrokenGenerator`, as any other error.
- An error ends the generator: later calls of `next()` give a
  `DoneResult`.

What every section uses:

    sealed interface ErrorGeneratorsError extends Error {}
    class TooBig(public n: Int) extends ErrorGeneratorsError {}

    let generated<T>(make: fn (): Generator<T>): Generator<T> { make() }
    let safely<T>(make: fn (): SafeGenerator<T>): SafeGenerator<T> { make() }

    let why(e: Error): String {
      let mine = when (e) {
        is TooBig -> "too big ${e.n}";
        else -> e.message;
      };
      let cause = e.cause;
      if (cause == null) { mine } else { "${mine}: ${why(cause)}" }
    }

    let take(g: Generator<Int>, n: Int): String {
      let taken = new ListBuilder<String>();
      while (taken.length < n) {
        let r = g.next() catch (e) {
          return "${taken.join(" ") { s => s }} ${why(e)}";
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

`upTo` counts until it passes its limit, and throws there, which
`next()` throws as a `BrokenGenerator`'s cause; the generator is done
after that:

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
1 2 3 broken generator: too big 4
1 broken generator: too big 2
done
```

## Errors in the body, handled there or passed on

A body handles one error itself and passes another on with `try`:

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
0 1 4 9 broken generator: too big 16
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

## An inner generator's error, passed on

A body that declares `throws BrokenGenerator` passes on what an inner
generator's `next()` throws, which is then the cause of the outer's:

    let doubled(inner: Generator<Int>): Generator<Int> {
      generated { (): GeneratorResult<Int> throws BrokenGenerator extends GeneratorFn =>
        while (true) {
          let r = try inner.next();
          if (r is ValueResult<Int>) { yield r.value * 2; } else { break; }
        }
      }
    }

    console.log(take(doubled(upTo(2)), 9));
    console.log(take(doubled(count(2)), 9));

```log
2 4 broken generator: broken generator: too big 3
2 4 done
```
