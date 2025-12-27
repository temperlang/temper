# Simple local functions and closures

Depending on plan of attack for implementing a backend, it can be nice to have
simple examples of features without other features crowding in. So some things
we don't include here:

- Lists
- Generics
- Cross calls between local functions
- Reassignable var captures

Should we testing mutable but non-reassignable things here?

    let callIt(i: Int, f: fn (Int): Int): Int {
      3 * f(i)
    }

    let enclose(i: Int): Void {
      let result = callIt(2) { j: Int =>
        i + j
      };
      console.log(result.toString());
    }

    enclose(1);

```log
9
```
