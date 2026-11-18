# Reach

What only tests reach, and what nothing does, as tmpl's
`reach::partition` splits a program: what only tests reach (a function,
a class and the interface it implements, a module `let` computed from
the library's, and a module `var` the test writes) is `#[cfg(test)]`,
its init too; a library value only tests read (`checked`, whose
initializer calls a function, so it runs in the library's init) has its
getter only for tests; and what nothing reaches (a function, a class,
an interface, and a module `let`) isn't written. The crate builds
without `cfg(test)` with no dead code, and its test passes only if the
test-only values are set before it runs. The log is what the
interpreter gives.

    export let shown(n: Int): Int { n + base }
    let base = 1;

    let forTests(n: Int): Int { n * testFactor }
    let testFactor = base * 2;
    var testCount = 0;
    interface TestShape { area(): Int; }
    class TestSquare(public side: Int) extends TestShape {
      public area(): Int { side * side }
    }
    let checked = shown(2);

    let unreachedFunction(): Int { 3 }
    class UnreachedClass {}
    interface UnreachedInterface {}
    let unreachedValue = 4;

    console.log("shown ${shown(1).toString()}");

    test("test parts") {
      let shape: TestShape = new TestSquare(forTests(2));
      testCount = testCount + shape.area();
      assert(testCount == 16);
      assert(checked == 3);
    }

```log
shown 2
```
