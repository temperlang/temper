# Dependencies for connecteds

This mostly is here just to test package dependencies for `@connected` usage.

## Bitset

Got an AI recommendation to try RoaringBitmap as a 3rd-party dependency for
connected testing. Expose it here as *Bitset*. In some ways, this is like
Temper's *DenseBitVector*, but it's presumably more efficient for large cases.

Because we don't yet support userspace connected types, just wrap an *AnyValue*
and let backends handle that.

    // TODO @connected
    export class Bitset(
      public internal: AnyValue,
    ) {
      // TODO Also support connected methods.
    }

Since we don't yet support connected types or methods, make some top-level
helpers here.

    @connected
    export let newBitset(): Bitset;

    @connected
    export let bitsetAdd(bitset: Bitset, i: Int): Void;

    @connected
    export let bitsetContains(bitset: Bitset, i: Int): Boolean;

## Tests

Just about any basic test of functionality will do here.

    test("bitset") {
      let bitset = newBitset();
      // Check one we have.
      bitsetAdd(bitset, 50);
      assert(bitsetContains(bitset, 50));
      // Check missing then added for another value.
      assert(!bitsetContains(bitset, 5050));
      bitsetAdd(bitset, 5050);
      assert(bitsetContains(bitset, 5050));
    }
