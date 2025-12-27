# Local scopes

Some utility functions for later use.

    let show(x: Listed<Int>): String {
      let s = new ListBuilder<String>();
      s.add("[");
      var c = "";
      for (var i = 0; i < x.length; i++) {
        s.add(c);
        // Can't actually bubble here, but swallow anyway until we can panic.
        s.add(x[i].toString());
        c = ", ";
      }
      s.add("]");
      // this seems a bit verbose for identity :-/
      return s.join("") { v => v };
    }

    let nestBlock<T>(b: fn ():T): T { b() }
    // TODO need to handle Void as a possible <T>
    let nestBlockVoid(b: fn ():Void): Void { b() }

The easy case has a free constant.

    let constantCapture(x: Listed<Int>, a: Int): Void {
      // ensure free value isn't inlined
      let b: Int = a * 1;
      let out = x.map { (n: Int): Int  => n + b };
      console.log("constantCapture = ${show(out)}");
    }
    constantCapture([1, 2, 3], 1);

```log
constantCapture = [2, 3, 4]
```

The slightly less easy case has a free constant in a nested block.

    let constantCaptureNested(x: Listed<Int>, a: Int): Void {
      let b: Int = a * 1;
      let out = nestBlock { (): List<Int>  =>
        x.map { (n: Int): Int  => n + b }
      };
      console.log("constantCaptureNested = ${show(out)}");
    }
    constantCaptureNested([1, 2, 3], 1);

```log
constantCaptureNested = [2, 3, 4]
```

Mutable captures may need special handling.

    let mutableCapture(x: Listed<Int>): Void {
      var sum = 0;  // free variable
      let out = x.map {
        (n: Int): Int  =>
        sum += n;
        sum
      };
      console.log("mutableCapture = ${show(out)}; sum=${sum}");
    }
    mutableCapture([1, 2, 3]);

```log
mutableCapture = [1, 3, 6]; sum=6
```

Make sure we can capture mutables through nested functions.

    let mutableCaptureNested(x: Listed<Int>): Void {
      var sum = 0;  // free variable
      let out = nestBlock { () : List<Int>  =>
        x.map {
          (n: Int): Int  =>
          sum += n;
          sum
        }
      };
      console.log("mutableCaptureNested = ${show(out)}; sum=${sum}");
    }
    mutableCaptureNested([1, 2, 3]);

```log
mutableCaptureNested = [1, 3, 6]; sum=6
```

Also capture of awkwardly mutable parameters and locals.

    export let thresh(values: Listed<Int>, var value: Int): Void {
      value += 1;
      let result = values.filter { it => it > value };
      console.log("thresh length = ${result.length}");
    }
    thresh([4, 5, 6], 4);

    export let thresh2(values: Listed<Int>, value: Int): Void {
      var value = value;
      value += 1;
      let result = values.filter { it => it > value };
      console.log("thresh2 length = ${result.length}");
    }
    thresh2([4, 5, 6], 4);

```log
thresh length = 1
thresh2 length = 1
```

Self recursive functions may need hoisting or a similar mechanism.

    let selfRecursion(x: List<Int>): Void {
      let mapper(i: Int, out: ListBuilder<Int>): Void {
        if (i < x.length) {
          out.add(x[i] * 2);
          mapper(i + 1, out);
        }
      }
      let out = new ListBuilder<Int>();
      mapper(0, out);
      console.log("selfRecursion = ${show(out)}");
    }
    selfRecursion([1, 2, 3]);

```log
selfRecursion = [2, 4, 6]
```

Self recursive functions may need hoisting or a similar mechanism.

    let selfRecursionNested(x: List<Int>): Void {
      let mapper(i: Int, out: ListBuilder<Int>): Void {
        nestBlockVoid { () : Void =>
          if (i < x.length) {
            out.add(x[i] * 2);
            mapper(i + 1, out);
          }
        }
      }
      let out = new ListBuilder<Int>();
      mapper(0, out);
      console.log("selfRecursionNested = ${show(out)}");
    }
    selfRecursionNested([1, 2, 3]);

```log
selfRecursionNested = [2, 4, 6]
```

Mutually recursive local functions need hoisting or forward declaration.

    let mutualRecursion(x: List<Int>): Void {
      let walker(i: Int, out: ListBuilder<Int>): Void {
        if (i < x.length) {
          putter(i, out);
        }
      }
      let putter(i: Int, out: ListBuilder<Int>): Void {
        out.add(x[i] * 2);
        walker(i + 1, out);
      }
      let out = new ListBuilder<Int>();
      walker(0, out);
      console.log("mutualRecursion = ${show(out)}");
    }
    mutualRecursion([1, 2, 3]);

```log
mutualRecursion = [2, 4, 6]
```
