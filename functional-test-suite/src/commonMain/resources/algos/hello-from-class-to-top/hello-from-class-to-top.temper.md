# Hello World from Class to Top Level

Test calling into a top-level function from inside a class. For some backends,
this requires extra machinery.

Here's our top-level.

    let say(message: String): Void {
      console.log(message);
    }

And here's our class.

    class Something(
      private message: String,
    ) {
      public sayMessage(): Void {

It calls the top-level.

        say(message);
      }
    }

Now call the method to call the top-level.

    let something = new Something("Hello, World!");
    something.sayMessage();

Expected:

```log
Hello, World!
```
