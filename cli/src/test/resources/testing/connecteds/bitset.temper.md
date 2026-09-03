# Dependencies for connecteds

This mostly is here just to test package dependencies for `@connected` usage.

## Bitset

Got an AI recommendation to try RoaringBitmap as a 3rd-party dependency for
connected testing. Expose it here as *Bitset*. In some ways, this is like
Temper's *DenseBitVector*, but it's presumably more efficient for large cases.

Because we don't yet support userspace connected types, just wrap an *AnyValue*
and let backends handle that. But hide the actual class type under a sealed
interface.

    // TODO @connected on a class instead of needing to wrap things.
    export sealed interface Bitset {
      public add(i: Int): Void;

      public contains(i: Int): Boolean;
    }

And keep the actual type hidden to avoid outside construction with a bad wrapped
value. And we have to reference this type in exported module members to prevent
Temper from pruning it today.

    // TODO @connected
    class BitsetWrapper(
      private wrapped: AnyValue,
    ) extends Bitset {
      // TODO Also support connected instance and static methods.

      public add(i: Int): Void {
        bitsetAdd(wrapped, i);
      }

      public contains(i: Int): Boolean {
        bitsetContains(wrapped, i)
      }
    }

Since we don't yet support connected types or methods, make some top-level
helpers here. The constructor needs to be exported. And make sure to reference
the unexported subtype here.

    export let newBitset(): Bitset {
      new BitsetWrapper(newBitsetConnected())
    }

But our connected methods can be internal to the module.

    @connected
    let newBitsetConnected(): AnyValue;

    @connected
    let bitsetAdd(bitset: AnyValue, i: Int): Void;

    @connected
    let bitsetContains(bitset: AnyValue, i: Int): Boolean;

## Tests

Just about any basic test of functionality will do here.

    test("bitset") {
      let bitset = newBitset();
      // Check one we have.
      bitset.add(50);
      assert(bitset.contains(50));
      // Check missing then added for another value.
      assert(!bitset.contains(5050));
      bitset.add(5050);
      assert(bitset.contains(5050));
    }
