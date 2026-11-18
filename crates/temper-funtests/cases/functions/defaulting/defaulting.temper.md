# Defaulting Test

Tests side effects of default parameter values. First set up function `f` with
a side effect.

    var fCalls = 0;
    let f(): Int {
      fCalls += 1;
      42
    }

Then use a call to `f` as the default parameter value of a function `g`.

    let g(x: Int = f()): Int {
      x
    }
    console.log("fCalls=${ fCalls }");
    console.log("g(   )=${ g(   ) }");
    console.log("fCalls=${ fCalls }");

```log
fCalls=0
g(   )=42
fCalls=1
```

The above call to `g` uses the default argument, so `fCalls` increments. But
now call `g` with an explicit value, so `f` *doesn't* get called, and `fCalls`
remains unchanged.

    console.log("g(123)=${ g(123) }");
    console.log("fCalls=${ fCalls }");

```log
g(123)=123
fCalls=1
```

Actual computed null value also conjurs the default arg.

    let noInline(): Int? { console.log("noInline"); null }
    console.log("g(null)=${g(noInline())}");
    console.log("fCalls=${fCalls}");

```log
noInline
g(null)=42
fCalls=2
```

## Methods

Default args for methods on classes also should work.

    class Thing {
      public say(a: String = "", b: String = ""): Void {

Log from here so it doesn't get inlined in the frontend.

        console.log("saying ${a}${b}");
      }

Also make sure we cover static methods with defaults.

      public static sayStatic(a: String, b: String = ""): Void {
        console.log("static ${a}${b}");
      }
    }

Check both trailing and leading positions.

    new Thing().say("hi");
    new Thing().say(null, "there");
    new Thing().say("by", "e");
    Thing.sayStatic("yall");

```log
saying hi
saying there
saying bye
static yall
```

## Null Coalescing

Not about optional parameters here, but vaguely related. Here we verify that we
don't double-evaluate coalesce subjects.

    var inked = 0;
    let maybeInc(): Int? {
      if (inked == 10) {
        null
      } else {
        inked += 1;
        inked
      }
    }

When we run `maybeInc()` here, we should see that it's not null, and we should
use the already returned value without running `maybeInc()` a second time.

    console.log("maybeInc() -> ${maybeInc() ?? -1}");
    console.log("inked: ${inked}");

```log
maybeInc() -> 1
inked: 1
```

Also try out null chaining while we're at it.

    let maybeLength(a: String?): Int? {
      inked += 1;
      // Because of non-null inference, `a.end` is ok here.
      a?.countBetween(String.begin, a.end)
    }

And use null and non-null in further chaining to ensure we don't double-call.

    let chainMore(a: String?): String {
      (maybeLength(a)?.max(0) ?? -1).toString()
    }

    console.log("lengthish \"ab\": ${chainMore("ab")}")
    console.log("lengthish null: ${chainMore(null)}")
    console.log("inked: ${inked}");

```log
lengthish "ab": 2
lengthish null: -1
inked: 3
```

Also check deeper chaining with simple access as well as method calls.

    class StringHolder(public string: String) {}
    let maybeEmpty(a: StringHolder?): String {
      // Keep incrementing for side effects, whether we check it or not.
      inked += 1;
      a?.string?.isEmpty?.toString() ?? "null"
    }

    console.log("maybeEmpty \"\": ${maybeEmpty(new StringHolder(""))}");
    console.log("maybeEmpty null: ${maybeEmpty(null)}");
    console.log("inked: ${inked}");

```log
maybeEmpty "": true
maybeEmpty null: null
inked: 5
```
