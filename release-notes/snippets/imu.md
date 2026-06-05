### Decorators for immutable types

The following interfaces are now built into Temper. These tag interfaces are
commitments on the part of any type that extends them:

- `@imu` - Any type decorated with `@imu` must be deeply immutable with not even
  any internal state changes allowed. Built-in `@imu` types include *String*,
  *Int32*, and *Boolean*, among others.
- `@partialImu` - Any type decorated with `@partialImu` is also immutable when
  actual type arguments are deeply immutable. Built-in *PartialImu* types
  include *List* and *Map*.

These restrictions also apply to subtypes of types with the above decorations.

Immutable types will allow for safe transfer across threads or for compile-time
evaluation, among other possible applications. Most target languages don't have
a way to explicitly state immutability, but it's possible these tags can affect
translation in some cases.

Decorations `@imu` and `@partialImu` are checked by the Temper compiler. For
example, the following declarations are legal:

```temper inert
// `@imu` tag is valid because all properties are imu.
// And `List<Int>` is imu because *Int* is imu.
@imu class SomeValues(
  public text: String,
  public numbers: List<Int>,
) {}

// `@partialImu` is valid because this type is imu if *T* is imu.
@partialImu class SummarizedItems<T>(
  public items: List<T>,
  public summary: T,
) {}

// `@imu` is valid because *T* is constrained.
@imu interface PropHolder<@imu T> {
  public prop: T;
}

// And `@imu` is still valid here because all tags follow rules.
class SummarizedItemsHolder(
  public prop: SummarizedItems<String>,
) extends PropHolder<SummarizedItems<String>> {}
```

But the following are illegal:

```temper inert
// No commitment tag here, so legal but not imu.
class NoCommitment {}

// Compiler error because *NoCommitment* isn't imu.
@imu class BadImuClass(
  public things: List<NoCommitment>,
) {}

// Compiler error because *reassignable* is `var`.
@partialImu class BadImuClassBecauseVar<T>(
  public var reassignable: List<T>,
) {}
```

This is just a sampling of the rules which enforce `@imu` and `@partialImu`
tagging.
