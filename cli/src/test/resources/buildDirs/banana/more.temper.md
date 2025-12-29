# Exports

    export let doThings<A, B>(a: A, b: B, things: fn (A, B): B): B {
      things(a, b)
    }

    export let twice(x: Float64): Float64 {
      console.log("Please don't inline");
      2.0 * x
    }

# Test Code

    test("twice works") { test =>
      let something = new Something(2.0);
      halveValueIn(test, something);
      assert(twice(0.5) == something.value);
    }

This includes helper things that hopefully we can avoid inlining.

    class Something(
      public var value: Float64,
    ) {}

    let halveValueIn(test: Test, something: Something): Void {
      let oldValue = something.value;
      something.value = something.value * 0.5;
      assert(something.value.abs() < oldValue.abs());
    }

And this one should be excluded since it's unused.

    let nobodyWantsMe(): Void {
      console.log(":sob:");
    }
