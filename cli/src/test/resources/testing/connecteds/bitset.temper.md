# Dependencies for connecteds

This mostly is here just to test package dependencies for `@connected` usage.

## Bitset

Got an AI recommendation to try RoaringBitmap as a 3rd-party dependency for
connected testing. Expose it here as *Bitset*. In some ways, this is like
Temper's *DenseBitVector*, but it's presumably more efficient for large cases.

Because we don't yet support userspace connected types, just wrap an *AnyValue*
and let backends handle that. But hide the actual class type under a sealed
interface.

    @connected
    export class Bitset {
      public add(i: Int): Void;

      public contains(i: Int): Boolean;
    }

## Tests

Just about any basic test of functionality will do here.

    test("bitset") {
      let bitset = new Bitset();
      // Check one we have.
      bitset.add(50);
      assert(bitset.contains(50));
      // Check missing then added for another value.
      assert(!bitset.contains(5050));
      bitset.add(5050);
      assert(bitset.contains(5050));
    }

TODO Test connected used by the test and a `@keepTest` helper for that.
