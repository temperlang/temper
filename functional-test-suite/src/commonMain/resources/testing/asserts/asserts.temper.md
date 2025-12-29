# Testing Functional Test

## Metadata

```text
@meta:arg runAsTest = true,
@meta:arg expectedFailures = mapOf(
@meta:arg     "a failing test" to """because it's wrong, expected helper.i == (2) not (3), expected s == (hi) not (bye)""",
@meta:arg ),
```

## Test

To be able to test temper code (and help flush out bugs in backend
implementations) we provide an API for writing unit test code in temper.

Declare one or more test cases to be executed with an assertion:

    test("a test ${name}") {
      // Call a separate function to prove that we can.
      assert(normSquared({ x: 0.0, y: 0.0 }) == 0.0) { (): String =>
        "Oh noes"
      }
    }
    // Put this after just to prove it works.
    let name = "case";

But define a non-exported function to test that's also relied on by prod code
later. And we don't typically inline class usage at the moment, so use that.

    let normSquared(vec: Vec2): Float64 {
      vec.x * vec.x + vec.y * vec.y
    }

A failing test includes the generated string in the output:

    test("a failing test") { test =>
      assert(false) { "because it's wrong" }
      let helper = new TestHelper(3, 2);
      assertMoreThings(test, helper, [["bye"]]);
    }

And defer to a helper function just to prove that it works, where the `assert`
macro expects a parameter either named `test` or typed `Test`.

    let assertMoreThings(
      t: Test, helper: TestHelper, strings: List<List<String>>
    ): Void throws Bubble {
      for (var i = 0; i < strings.length; i += 1) {
        for (var j = 0; j < strings[i].length; j += 1) {
          assert(helper.i == helper.j);

Purposely nested nonsense here to invoke nested labeling, because our Python was
having trouble with that in test helper code.

          let s = strings[i][j];
          assert(s == "hi");
        }
      }
    }

Also use a helper class used just from tests for those backends that put classes
in separate files.

    class TestHelper(
      public let i: Int,
      public let j: Int,
    ) {}

The backend handles running the tests for you.

## Exported members

Temper supports tests in the same module as exported production code, so try
this out also.

    export class Vec2(
      public x: Float64,
      public y: Float64,
    ) {
      public toString(): String {

But depend in prod code on non-exported code that we also test independently.
That's `normSquared` here.

        "(${x}, ${y}) size squared ${normSquared(this)}"
      }
    }

    test("exported") {
      let vec = { x: 1.5, y: 2.5 };
      assert(vec.toString() == "(1.5, 2.5) size squared 8.5");
    }
