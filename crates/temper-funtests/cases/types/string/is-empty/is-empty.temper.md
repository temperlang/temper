# String IsEmpty Functional Test

Tests the `isEmpty` property of strings.

    let emptyString = "";
    let foo = "foo";

    console.log(
        if (emptyString.isEmpty) {
          "emptyString is empty"
        } else {
          "emptyString is not empty"
        }
    );

    console.log(
        if (foo.isEmpty) {
          "foo is empty"
        } else {
          "foo is not empty"
        }
    );



```log
emptyString is empty
foo is not empty
```
