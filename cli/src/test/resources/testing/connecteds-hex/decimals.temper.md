# Decimal addition

Binary floating point cannot say `0.1 + 0.2` is `0.3`. A decimal library can,
so connected code that answers it is using the dependency, not the host's
floats.

    @connected
    export let addDecimals(a: String, b: String): String;

    test("decimal addition") {
      assert(addDecimals("0.1", "0.2") == "0.3");
      assert(addDecimals("1.10", "2.205") == "3.305");
    }
