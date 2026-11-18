# Static Properties in Scopes

Define a single simple class:

    public class Simple {
      public static value = "foo";
      public static other = "bar";
    }

And define some functions reading static properties:

    let readSimpleValue(): String {
      return Simple.value;
    }

    let readSimpleOther(): String {
      return Simple.other;
    }

Test the functions:

    console.log("Simple.value is ${readSimpleValue()}");
    console.log("Simple.other is ${readSimpleOther()}");

Output demonstrates how these work:

```log
Simple.value is foo
Simple.other is bar
```
