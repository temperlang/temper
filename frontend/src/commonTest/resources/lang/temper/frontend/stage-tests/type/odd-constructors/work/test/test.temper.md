## Odd constructors

    let { HasTwoTypeParams } = import("../support/");

*HasTwoTypeParams* has two type parameters, and a property `h2tp`.

We can construct an instance via property bag syntax, where the
declaration provides the type parameters.

    let h2tpWithTypeContext: HasTwoTypeParams<Boolean, Int32> =
      { h2tp: "h2tp" };

That constructor invocation should have those explicit type
parameters.

A property bag without context should make some reasonable choice
for type actuals.

    let h2tpWithoutContext = { h2tp: "more h2tp" };
