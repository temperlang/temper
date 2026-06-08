# Call override from subtype

We formulate a variety of calls to getters to be able to debug the typer
internally and compare the differences in representation and handling as needed.

    interface Slappy {
      public get thingums(): Int;
    }

    class Slippy extends Slappy {
      public checkOverridden(): Boolean { this.thingums == 1 }
      public checkOwn(): Boolean { this.thingumses == 2 }
      public get thingums(): Int { 3 }
      public get thingumses(): Int { 4 }
    }

    let checkFromOutside(slipster: Slippy): Boolean {
      slipster.thingums == 5
    }

Also test results of using these methods.

    let slippy = new Slippy();
    let slappy: Slappy = slippy;
    console.log(slippy.thingums.toString());
    console.log(slappy.thingums.toString());
    console.log(slippy.thingumses.toString());
    console.log(slippy.checkOverridden().toString());
    console.log(slippy.checkOwn().toString());

```log
3
3
4
false
false
```

## Override resolution order

Also ensure that overrides are the same across backends. Does `A` or `C` have
priority for `D`?

    interface A { a(): String { "A wins!" } }
    interface B extends A {}
    interface C { a(): String { "C wins!" } }
    class D extends B & C {}
    console.log(new D().a());

In Python's C3 linearization, `A` wins, but that's not expected in Temper. Maybe
it's ok if subtypes of `A` implemented in Python follow Python rules, but code
written in Temper needs consistent semantics.

```log
C wins!
```
