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

Also ensure that overrides are the same across backends. Which interface version
is called by default? And for some target languages, we need to choose a winner
explicitly to avoid compiler errors.

### Joined inheritance

Here, B and C both extend A.

    interface A { a(): String { "A wins!" } }
    interface B extends A {}
    interface C extends A { a(): String { "C wins!" } }
    class D extends B & C {}
    console.log(new D().a());

```log
C wins!
```

### Split inheritance

Here, C *doesn't* extend A.

    interface ASplit { a(): String { "A wins!" } }
    interface BSplit extends ASplit {}
    interface CSplit { a(): String { "C wins!" } }
    class DSplit extends BSplit & CSplit {}
    console.log(new DSplit().a());

```log
C wins!
```
