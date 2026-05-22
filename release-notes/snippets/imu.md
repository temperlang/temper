### Imu and PartialImu tag interfaces

The following interfaces are now built into Temper. These tag interfaces are
commitments on the part of any type that extends them:

- *Imu* - Any type extending *Imu* must be deeply immutable with not even any
  internal state changes allowed. Built-in *Imu* types include *String*,
  *Int32*, and *Boolean*, among others.
- *PartialImu* - Any type extending *PartialImu* is also immutable when actual
  type arguments are deeply immutable. Built-in *PartialImu* types include
  *List* and *Map*.

Immutable types will allow for safe transfer across threads or for compile-time
evaluation, among other possible applications. Most target languages don't have
a way to explicitly state immutability, but it's possible these tags can affect
translation in some cases.

*Imu* and *PartialImu* are checked by the Temper compiler. For example, the
following declarations are legal:

```temper inert
// Imu tag is valid because all properties are Imu.
// And `List<Int>` is Imu because *Int* is Imu.
class SomeValues(
  public text: String,
  public numbers: List<Int>,
) extends Imu {}

// PartialImu is valid because this type is Imu if *T* is Imu.
class SummarizedItems<T>(
  public items: List<T>,
  public summary: T,
) extends PartialImu {}

// Imu is valid because *T* is constrained.
interface PropHolder<T extends Imu> extends Imu {
  public prop: T;
}

// And Imu is still valid here because all tags follow rules.
class SummarizedItemsHolder(
  public prop: SummarizedItems<String>,
) extends PropHolder<SummarizedItems<String>> {}
```

But the following are illegal:

```temper inert
// No commitment tag here, so legal but not Imu.
class NoCommitment {}

// Compiler error because *NoCommitment* isn't Imu.
class BadImuClass(
  public things: List<NoCommitment>,
) extends Imu {}

// Compiler error because *reassignable* is `var`.
class BadImuClassBecauseVar<T>(
  public var reassignable: List<T>,
) extends PartialImu {}
```

This is just a sampling of the rules which enforce *Imu* and *PartialImu*
tagging.
