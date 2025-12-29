# Static Properties

Define a single simple class:

    class Simple(
      public name: String,
    ) {

Include a static member instance of the class itself, because that takes more
effort on some backends.

      public static simon = new Simple("foo");

Now use the self-typed static property for other static properties, to ensure
initialization order.

TODO Plain `simon.name` works in the interpreter but not in backends.

      public static value = Simple.simon.name;
      public static other = 22;
    }

Test accessing static properties:

    console.log("Simple.simon.name is ${Simple.simon.name}");
    console.log("Simple.value is ${Simple.value}");
    console.log("Simple.other is ${Simple.other}");

Output demonstrates how these work:

```log
Simple.simon.name is foo
Simple.value is foo
Simple.other is 22
```

## Static Methods

Static methods can be thought of as properties that always point to the same
function value, but they should translate to named functions on most backends.

Classes can have static methods.

    class C {
      public static sayHi(): Void { console.log(C.compose("C", "'hi.'")); }

And the above calls the following private static method. I'm not sure how to
prove this call isn't inlined in the frontend, but manual inspection at time of
writing shows a runtime static method call.

      private static compose(subject: String, message: String): String {
        "${subject} says ${message}"
      }
    }

Interfaces also can have static methods.

    interface I {
      public static sayBye(): Void { console.log("I says 'bye.'"); }
    }

They can be called via the type name.

    C.sayHi();
    I.sayBye();

Output demonstrates how these work:

```log
C says 'hi.'
I says 'bye.'
```
