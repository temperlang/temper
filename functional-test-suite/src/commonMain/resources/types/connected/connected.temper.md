# Connected types

A whole class can be connected. Each backend's connected code provides a class
with the names and members Temper would have generated, and Temper uses it in
place of generating one. No package dependencies here, so this runs everywhere.

Connected functions have their own test, `functions/connected`.

    @connected
    export class Counter {
      public add(i: Int): Void;
      public total(): Int;
    }

    let counter = new Counter();
    counter.add(2);
    counter.add(3);
    console.log("counter.total(): ${counter.total()}");

```log
counter.total(): 5
```
