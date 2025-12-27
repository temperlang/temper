The following test has a Temper compiler error with a wrong return type.
But many backends should still be able run this as a passing test.

This means that the test should run and pass, but we should still recognize
the compiler error.

    test("a test case") {
      assert(true) { (): Int => // return type wrong on purpose!!!
        "Oh noes"
      }
    }
