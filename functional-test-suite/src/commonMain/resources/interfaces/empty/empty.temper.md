Empty or marker interfaces are a corner case.

    interface Greeter {}

    class HiGreeter extends Greeter {
      public greet(): Void {
        console.log("Hi!");
      }
    }

I don't remember if the cast here was relevant to the test case, but it seems
likely, given the explicit effort do that here.

    let greeter: Greeter = new HiGreeter();
    (greeter as HiGreeter).greet();

```log
Hi!
```
