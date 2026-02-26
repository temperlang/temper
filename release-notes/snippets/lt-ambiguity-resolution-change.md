### 🚨Breaking change: Details of rules for `<` ambiguity have changed

The previous parsing rules for determining when a `<` token is an
infix comparison operator and when it is part of a `<...>` type
argument group have changed.

Previously, the rule was based on speculatively parsing ahead, a
technique that made it difficult to recreate Temper's exact parsing in
tools like TextMate grammars.

Now, it is based on whether there is a space or comment token preceding.

Previously, some expressions that looked like comparisons, would require
extra parentheses.

    f(a < b, c > d)

Now they don't, but if you need a comparison operator, always put spaces
around it.

See the builtin/< reference article for details.
