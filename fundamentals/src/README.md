# Module fundamentals

<!-- The h1 name is specially interpreted by dokka -->

Fundamental language bits: values, types, and type definitions.

There are multiple kinds of things that correspond to types, so this table
summarizes the main type-related concepts.

| Concept       | Description                                                                                                                                | Where Defined                           | Examples                                                                           |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------  | ---------------------------------------------------------------------------------- |
| Type tags     | Enables `instanceof`                                                                                                                       | [*lang/temper/value/TypeTag*][TT]       | `String`, `Int`, `Foo`                                                             |
| Type shapes   | Information about the members of a `class` or `interface` type                                                                             | [*lang/temper/typeshape/TypeShape*][TS] | `class Foo`                                                                        |
| Static types  | Can be checked to alert users of type errors at compile time                                                                               | [*lang/temper/type/Types*][ST]          | `String`, `Foo<String>`, `Foo<Int>`, `Foo<T>`, `Foo │ Bar │ null`, `Never`, `Fail` |
| Reified types | A wrapper around types that can be used to vet values assigned to variables including arguments to functions                               | [*lang/temper/value/ReifiedType*][RT]   | any type tag, also structure expectaions for macros                                |

[TT]: https://github.com/temperlang/temper/blob/main/fundamentals/src/commonMain/kotlin/lang/temper/value/TypeTag.kt
[TS]: https://github.com/temperlang/temper/blob/main/fundamentals/src/commonMain/kotlin/lang/temper/typeshape/TypeShape.kt
[ST]: https://github.com/temperlang/temper/blob/main/fundamentals/src/commonMain/kotlin/lang/temper/type/Types.kt
[RT]: https://github.com/temperlang/temper/blob/main/fundamentals/src/commonMain/kotlin/lang/temper/value/BaseReifiedType.kt
