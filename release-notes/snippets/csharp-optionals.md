### C# backend uses *Optional* type for nullable type parameters

The C# backend (`-b csharp`) had a bug where the translated output
produced compiler errors when a type parameter was used in a nullable
context.

```temper
class C<T>(
  public x: T | Null,
) {
  public f(y: T | Null): T | Null {
    let z: T | Null = null;
    if (y != null) {
      z = y;
    }
    if (x != null) {
      z = x;
    }
    z
  }
}

do {
  console.log(new C<Int>(null).f(123).toString()); //!outputs 123
}
```

That class uses a type parameter `<T>`.
Unfortunately, `T | Null` cannot be translated to C# `T?` because
the suffix `?` was added late to the language to express the idea
that a type might be null.

- When the modified type is a reference type like `object`, the underlying type can store a null value
  so it has no semantic impact outside of additional type checks.
- When the modified type is a value type like `int` it translates to `Nullable<int>`, a builtin
  value type that allows distinguishing `0` from `null`.
- When the modified type is `T?` only `where T` constraints could guide the compiler to one of those
  behaviours but our type varaibles need to allow unbounded Temper type variables to bind to both
  C# value types (as Temper `Int` does) and C# reference types (as Temper `String` does).

Without these changes, we generated C# like the following:

```c#
T? z__0 = null;
```

That fails to compile.

> Compilation error (...): Cannot convert null to type parameter 'T' because it could be a non-nullable value type. Consider using 'default(T)' instead.

Now, our C# temper-core support library contains an *Optional* type that is API compatible with
DotNext's version which hopefully we can transition to as supported versions allow.

For example, instead of `T? z__0 = null;` the translated output includes:

```c#
Optional<T> z__0 = Optional.None<T>();
```

Visible changes include:

- No changes for non-generic classes or interfaces
- When a property has a type that is a type parameter or null, like `T | Null`,
  the translated C# property will store an `Optional<T>`.
- When a method takes an input with such a type or returns such a type, the
  translated method's input or output will have type `Optional<T>`.
- When a method overrides a method that does, the override will too.

<details>
<summary>
With this change the above Temper code translates to C# like the below.
</summary>

Note the use of *Optional\<T>* instead of *T?* and the inserted calls
to wrap values as optionals and to unwrap them back into, in the case
where *T* binds to *int*, a *Nullable\<int>*.

```c#
class C<T>
{
  public Optional<T> X;

  // Auto-generated constructor
  public C(Optional<T> x)
  {
    this.X = x;
  }

  public Optional<T> F(Optional<T> y)
  {
    Optional<T> z = Optional.None<T>();
    if (y.HasValue)
    {
      z = y;
    }
    if (X.HasValue)
    {
      z = X;
    }
    return z;
  }
}

// In the generated globals class
Console.WriteLine(
  Optional.ToNullable<int>(  // Unpack output
    // Pack inputs
    new C<int>(Optional.None<int>()).F(Optional.Of<int>(123))
  )
  .ToString()
)
```

</details>
