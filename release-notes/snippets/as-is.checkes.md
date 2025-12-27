### 🚨Breaking change: Compile-time checks for RTTI operators

Previously, Temper had no checks as to whether a runtime-type
information (RTTI) check could be subject to false positives.

Consider the following:

```temper inert
let a = when (x) {
  is String  -> "String";
  is Int     -> "Int";
  is Float64 -> "Float64";
  else       -> "Other";
};

let b = when (x) {
  is Float64 -> "Float64";
  is Int     -> "Int";
  is String  -> "String";
  else       -> "Other";
};
```

`a` and `b` should be the same, per Temper semantics, because there is
no Temper value that is more than one of (*Float64*, *Int*, *String*).

But in many programming languages' runtimes, there isn't enough
information to distinguish these values reliably:

- in JavaScript, the *number* type spans floating and integer types.
- in Perl and PHP, the same scalar type is used for all three; there
  are approximate predicates for making distinctions but these are
  not reliable, especially for values that enter from external target
  language code.

In Perl, for example, given `my $x = 1;` we might get
`$a == "String" && $b == "Float64"` though `1` looks integer-esque.

Now, Temper performs long planned restrictions on type casting and
checking.
Backends only need to be able to distinguish between and unconnected
types (those translated from Temper types) and those explicitly
marked with `@mayDowncastTo(true)`.

Now, the above code leads to compile-time errors, but we preserve
some RTTI distinctions.

Temper's long-term goal is for type matches (as above) and the
`.is<Type>()` RTTI predicate and `.as<Type>()` checked cast to allow
distinguishing variants of sealed types, and to work with unconnected,
Temper defined types, and to allow distinguishing *(T | Null)* from *(T)*
for all non-null *T*.
