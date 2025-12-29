# Actual Error

The point here is to make an error in the backend, so we can check "error"
reports in the the junit xml report rather than just "failure" reports.

    test("missing element") {

There is no element 1 in this list.

      let ls = ["Hello, World"];
      assert(ls[1] == "World");
    }
