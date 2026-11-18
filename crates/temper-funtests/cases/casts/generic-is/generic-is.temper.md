# Checks against generic classes

Inside a generic function, `is` and `as` can check against a generic class
whose type arguments come from the function's own type parameters. Some
backends need those arguments written out, since a generic class's bare
name isn't a type there.

    interface Effect<T> {}
    class Append<T>(public text: String) extends Effect<T> {}
    class Mark<T>(public value: T) extends Effect<T> {}

    let texts<T>(effects: List<Effect<T>>): String {
      let out = new StringBuilder();
      for (let e of effects) {
        if (e is Append<T>) {
          out.append(e.text);
        } else {
          out.append("|");
        }
      }
      out.toString()
    }

    let firstText<T>(effect: Effect<T>): String {
      do { (effect as Append<T>).text } orelse "none"
    }

    let effects: List<Effect<Int>> = [
      new Append<Int>("a"), new Mark<Int>(1), new Append<Int>("b"),
    ];
    console.log(texts(effects));
    console.log(firstText(effects[0]));
    console.log(firstText(effects[1]));

```log
a|b
a
none
```
