# Hello World Object Functional Test

This test creates a simple object with some properties set, and displays
reading those properties and calling a simple method with an argument.

    class C(
      public x: String,
      public y: String,
    ) {
      public echo(input: String): Void {
        console.log(input);
      }
    }
    let c = new C("Hello", "World");
    console.log("${c.x}, ${c.y}!")
    c.echo("Hello World, again!");

Expected results:

```log
Hello, World!
Hello World, again!
```
