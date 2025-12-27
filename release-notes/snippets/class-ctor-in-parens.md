### 🚨Breaking change: backed `class` properties now appear in parenthesized constructor

Previously, any backed properties declared in a class would become
constructor parameters.  And a property with an `=` expression
corresponds to an optional constructor parameter with a default
expression.

```temper
class C {
  public let a: Int;
  public let b: Int;
  public let sum: Int = a + b;
}

let c = {
  a: 1,
  b: 2,
  sum: 4,
};

console.log(c.sum.toString());
```

This made it hard to specify properties that were not overridable by
explicit constructor parameters.

Now, a `class` declaration can include parentheses and formal
parameters there specify both constructor parameters and backed
properties.

```temper
class C(
  public let a: Int, // comma separates
  public let b: Int,
) {
  public let sum: Int = a + b;
}

let c = {
  a: 1,
  b: 2,
  // sum not allowed here
};

console.log(c.sum.toString());
```

A new `@noProperty` decorator allows specifying a constructor parameter
that does not have a backed property auto-created.

```temper
// Accepts a list or a mutable list builder but stores a list.
class SomeStrings(
  @noProperty strings: Listed<String>
) {
  public strings: List<String> = strings.toList();
}

let myListBuilder = new ListBuilder<String>();
myListBuilder.add("Hello");

let ss = { strings: myListBuilder };
console.log(ss.strings.is<List<String>>().toString()); //!outputs true
```
