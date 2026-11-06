First make an interface with a pure virtual method.

    interface Greeter {
      public greet(): Void;
    }

Then prove we can override and call it.

    class HiGreeter extends Greeter {
      public greet(): Void {
        console.log("Hi, virtually!");
      }
    }

    let greeter: Greeter = new HiGreeter();
    greeter.greet();

```log
Hi, virtually!
```

## Generic interfaces

An override of a generic interface's method needs the interface's
signature, whatever the type argument, and even when it reassigns a
parameter.

    interface Wrapper<T> {
      public wrap(item: T): T;
    }

    class Box(public label: String) {}

    class BoxWrapper extends Wrapper<Box> {
      public wrap(var item: Box): Box {
        item = new Box("[${item.label}]");
        item
      }
    }

    class IntWrapper extends Wrapper<Int> {
      public wrap(item: Int): Int { item + 1 }
    }

    let boxWrapper: Wrapper<Box> = new BoxWrapper();
    let intWrapper: Wrapper<Int> = new IntWrapper();
    console.log(boxWrapper.wrap(new Box("box")).label);
    console.log(intWrapper.wrap(41).toString());

```log
[box]
42
```
