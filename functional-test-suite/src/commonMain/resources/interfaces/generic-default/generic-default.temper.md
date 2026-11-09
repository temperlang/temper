# Generic interface defaults

A generic interface can have default methods and getters, which classes
inherit without overriding. Some backends call the default from the class,
so they need the interface's type arguments as the class instantiates it.

    interface Box<T> {
      public get item(): T;
      public describe(prefix: String): String {
        "${prefix}: ${show(item)}"
      }
      public get label(): String { describe("box") }
      public show(x: T): String;
    }

A non-generic class fixes the type argument.

    class TextBox extends Box<String> {
      public get item(): String { "text" }
      public show(x: String): String { "'${x}'" }
    }

A generic class passes its own type parameter along.

    class NamedBox<T>(
      private value: T,
      private render: fn (T): String,
    ) extends Box<T> {
      public get item(): T { value }
      public show(x: T): String { "named ${render(x)}" }
    }

    console.log(new TextBox().describe("string"));
    console.log(new TextBox().label);
    let named = new NamedBox<Boolean>(true, fn (b: Boolean): String { b.toString() });
    console.log(named.describe("bool"));
    let box: Box<Boolean> = named;
    console.log(box.label);

```log
string: 'text'
box: 'text'
bool: named true
box: named true
```
