# Reach

What only tests reach, and what nothing does, as tmpl's
`reach::partition` splits a program, which backends run as tests. What
only tests reach: a function, a class and the interface it implements,
a module `let` computed from the library's, a module `var` the test and
a test-only function write, and a function that does. A library value
only tests read (`checked`, whose initializer calls a function, so it
runs in the library's init), and a library `var` that the library writes
and a test sets. What nothing reaches: a function, a class, an
interface, and a module `let`. Each backend keeps what only tests reach
where only its tests are, and writes nothing of what nothing reaches;
the test passes only if the test-only values are set before it runs,
and if it sees and sets the library's own. The log is what the
interpreter gives.

    export let shown(n: Int): Int {
      shownCount += 1;
      n + base
    }
    let base = 1;
    var shownCount = 0;

    let forTests(n: Int): Int { n * testFactor }
    let testFactor = base * 2;
    var testCount = 0;
    interface TestShape { area(): Int; }
    class TestSquare(public side: Int) extends TestShape {
      public area(): Int { side * side }
    }
    let addArea(shape: TestShape): Void {
      testCount = testCount + shape.area();
    }
    let checked = shown(2);

    let unreachedFunction(): Int { 3 }
    class UnreachedClass {}
    interface UnreachedInterface {}
    let unreachedValue = 4;

    console.log("shown ${shown(1).toString()}");

    test("test parts") {
      let shape: TestShape = new TestSquare(forTests(2));
      addArea(shape);
      testCount = testCount + shape.area();
      assert(testCount == 32);
      assert(checked == 3);
      assert(shownCount == 2);
      shownCount = 0;
      shown(0);
      assert(shownCount == 1);
    }

```log
shown 2
```
