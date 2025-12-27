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
