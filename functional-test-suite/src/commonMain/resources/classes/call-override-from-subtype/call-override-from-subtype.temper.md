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

    interface A {
      a(): String { "A wins!" }
      get thing(): String { "A has thing!" }
    }
    interface B extends A {}
    interface C extends A {
      a(): String { "C wins!" }
      get thing(): String { "C has thing!" }
    }
    class D extends B & C {}
    console.log(new D().a());
    console.log(new D().thing);

```log
C wins!
C has thing!
```

### Split inheritance

Here, C *doesn't* extend A.

    interface ASplit { a(): String { "A wins!" } }
    interface BSplit extends ASplit {}
    interface CSplit extends ASplit { a(): String { "C wins!" } }
    class DSplit extends BSplit & CSplit {}
    console.log(new DSplit().a());

```log
C wins!
```

### Deep inheritance

Here, both sides extend A, but one side is longer than the other, so
breadth-first search isn't good enough.

    interface ADeep { a(): String { "A wins!" } }

D -> B3 -> B2 -> B1 is 3 steps, and B1 is still under A, so B1 should override
A.

    interface B1Deep extends ADeep { a(): String { "B1 wins!" } }
    interface B2Deep extends B1Deep {}
    interface B3Deep extends B2Deep {}

But D -> C -> A is only 2 steps, so A is closer by breadth-first than B1. This
would cause A to win if we use simple breadth-first, which denies the override
of B1Deep.

    interface CDeep extends ADeep {}
    class DDeep extends B2Deep & CDeep {}
    console.log(new DDeep().a());

```log
B1 wins!
```

But for kicks, let's reverse the order and see what happens.

    class EDeep extends CDeep & B2Deep {}
    console.log(new EDeep().a());

And this is different from Python's C3 linearization.

```log
A wins!
```
