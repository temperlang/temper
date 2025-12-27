### Default arguments requested by null

To simplify optional arguments, Temper now indicates excluded arguments by
passing in a null value. This is carried through to all backends. Optional
parameters are always nullable, and if a null argument is supplied, the default
value is provided internally. For example, for this function:

```temper
let intName(i: Int, name: String = i.toString()): Pair<Int, String> {
  new Pair(i, name)
}
```

Here are examples from the repl:

```temper
$ intName(1, "one")
interactive#10: {class: Pair__32, key: 1, value: "one"}
$ intName(1)
interactive#11: {class: Pair__32, key: 1, value: "1"}
$ intName(1, null)
interactive#12: {class: Pair__32, key: 1, value: "1"}
```

And here's an example translation to Java, including a generated overload:

```java
public static Entry<Integer, String> intName(int i__1, @Nullable String name__4) {
    String t_19;
    String name__2;
    if (name__4 == null) {
        t_19 = Integer.toString(i__1);
        name__2 = t_19;
    } else {
        name__2 = name__4;
    }
    return new SimpleImmutableEntry<>(i__1, name__2);
}
public static Entry<Integer, String> intName(int i__1) {
    return intName(i__1, null);
}
```

Or to Python:

```py
def int_name(i: 'int', name: 'Union[str, None]' = None) -> 'Pair[int, str]':
  _name: 'Union[str, None]' = name
  t: 'str'
  name: 'str'
  if _name is None:
    t = int_to_string(i)
    name = t
  else:
    name = _name
  return pair(i, name)
```

Prior to this change, unset arguments had specialized semantics within the
interpreter and inconsistent handling across backends.
