# Defaults

Parameters' defaults: left out and given, null for the default, a
default that reads an earlier parameter, a default that runs only when
it's needed, a nullable parameter with a default, defaults on methods,
static methods, and constructors (a class's header and its own
constructor), and calls of another module's function and class. The log
is what the interpreter gives.

    let { wrap, Pad } = import("./helpers");

    var calls = 0;
    let next(): Int {
      calls += 1;
      calls
    }

    let range(start: Int, end: Int = start + 10, step: Int = next()): String {
      "${start}..${end} by ${step}"
    }

    console.log(range(1));
    console.log(range(1, 5));
    console.log(range(1, 5, 3));
    console.log(range(2, null, 7));
    console.log("calls ${calls}");

```log
1..11 by 1
1..5 by 2
1..5 by 3
2..12 by 7
calls 2
```

A nullable parameter may default to null, or to a value, which null
also gives.

    let tag(text: String, mark: String? = null): String {
      if (mark == null) { text } else { "${mark}${text}" }
    }
    let named(name: String? = "nobody"): String {
      name ?? "null"
    }

    console.log(tag("plain"));
    console.log(tag("marked", "#"));
    console.log(named());
    console.log(named(null));
    console.log(named("Ada"));

```log
plain
#marked
nobody
nobody
Ada
```

Methods, static methods, and constructors: a class's header, its own
constructor, and an object literal that leaves a property out.

    class Greeter(public greeting: String = "Hello", public punct: String = "!") {
      public greet(name: String = "world", times: Int = 1): String {
        var out = "";
        for (var i = 0; i < times; i += 1) {
          out = "${out}${greeting}, ${name}${punct}";
        }
        out
      }
      public static shout(text: String, mark: String = "!"): String {
        "${text}${mark}${mark}"
      }
    }

    class Counter {
      public var count: Int;
      public constructor(start: Int = 10) {
        count = start;
      }
      public add(by: Int = 1): Int {
        count += by;
        count
      }
    }

    console.log(new Greeter().greet());
    console.log(new Greeter("Hi").greet("you", 2));
    console.log({ punct: "?" }.greet(null, 2));
    console.log(Greeter.shout("hey"));
    let counter = new Counter();
    counter.add();
    console.log("count ${counter.add(5)} ${new Counter(1).add()}");

```log
Hello, world!
Hi, you!Hi, you!
Hello, world?Hello, world?
hey!!
count 16 2
```

Another module's function and class, with their defaults.

    console.log(wrap("a"));
    console.log(wrap("b", "<"));
    console.log(wrap("c", "<", ">"));
    let pad = new Pad();
    console.log(pad.apply("x"));
    console.log(new Pad(0, 3).apply("y", "-"));

```log
[a[
<b<
<c>
.x..
y---
```
