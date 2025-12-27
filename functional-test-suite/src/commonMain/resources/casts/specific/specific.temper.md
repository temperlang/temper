# Cast Functional Test

The `as` operator allows casting.

We can make an interface with subclasses to verify.

    interface AlphaInterface { }
    class BravoClass extends AlphaInterface { }
    class CharlieClass extends AlphaInterface { }

Generics aren't reified today, so we can't cast to a generic type, but we can
make a function to cast to a specific type. Although in the style of generic
methods, the type goes in angle brackets.

Casts are safe. If a value is not of the right type, then the cast won't
succeed.

    let checkB(instance: AlphaInterface): Void {
      console.log(do { instance as BravoClass; "yep" } orelse "nope");
    }

Now we can try out some variations.

    checkB(new BravoClass());
    checkB(new CharlieClass());

prints

```log
yep
nope
```

Also verify that list items are checked correctly.

Currently, the list is typed as being `List<B | C>`, though this ought to
become `List<A>` by default in the future. For now, be explicit.

    let items: List<AlphaInterface> = [new BravoClass(), new CharlieClass()];
    checkB(items[0]);
    checkB(items[1]);

prints

```log
yep
nope
```

## Type checking

We can also checking if an object is an instance of some type.

    let checkIfB(instance: AlphaInterface): Void {
      console.log(if (instance is BravoClass) { "yep" } else { "nope" });
    }
    checkIfB(items[0]);
    checkIfB(items[1]);

```log
yep
nope
```
