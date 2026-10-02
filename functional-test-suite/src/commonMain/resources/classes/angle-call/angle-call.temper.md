# Angle call test

We had a problem with implied constructors creating forms that left angle
bracket call type references at the top level, which then could break in
backends.

The follow construct tests a form that was causing this problem.

There's no need to execute any code here. The error happened from these
definitions alone.

    @imu class Blah<@imu T> { } // include @imu for bonus testing

    class Hi(
      public let thing: Blah<String>,
    ) {
      // Adding this explicit constructor caused the problem to go away, so this
      // was here for uncommenting and comparison.
      // public constructor(thing: Blah<String>) { this.thing = thing }
    }

## Verifying the fix doesn't break other things

The initial wrong fix caused problems with types/constructors being returned
from functions. This same problem could likely be manifested in other ways, but
here's a simple variation on the form that was causing the problem.

    let whatever(n: Int): Type {
      if (n == 0) {
        console.log("0");
      }
      // Note that this purposely says `Void` rather than `void`.
      Void
    }

This would cause the error. We don't really care about the outcome.

    whatever(1);

## More bonus testing: Generic methods

We already require classes and generics to some extent in this test. So take
advantage of that to also test generic methods.

    class Classy(
      public index: Int,
    ) {
      // Static method.
      public static first<T>(things: List<T>): T {
        // Separate declaration that maybe exercises local declarations.
        let thing = things[0];
        thing
      }

      // Instance method.
      public indexed<T>(things: List<T>): T {
        let thing = things[index];
        thing
      }
    }

    let someThings = ["a", "b"];
    let first = Classy.first(someThings);
    let second = new Classy(1).indexed(someThings);
    console.log("Some things: ${first}, ${second}");

```log
Some things: a, b
```
