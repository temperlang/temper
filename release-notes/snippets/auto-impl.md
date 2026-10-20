### Auto operator implementation

For the upcoming `@operator` feature, there's also a way to implement `==`
automatically:

```temper
@auto("==")
export class Point(
  public x: Int,
  public y: Int,
) {}
```

This automatically creates an `@operator("==")` implementation named `eq` that
checks `==` for all backed properties. This also works automatically for whole
hierachies of sealed types, which also checks the type in those cases.

See documentation for more details.
