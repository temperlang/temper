---
title: Builtin Functions and Constants
---

# Builtin Functions and Constants

You can use builtins in your Temper code without `import`ing anything.

Some definitions are marked *Infix*, *Prefix*, or *Postfix*.  These
are operators: they are used like mathematical symbols:

- Infix `+` appears between arguments: `:::js a + b`
- Prefix `-` appears before its sole argument: `:::js -b`
- Postfix `++` appears afters its argument: `:::js i++`

Other functions are called with parentheses: `:::js f(x)`.
See also [snippet/syntax/Call].

(The full set of operator definitions is [temper/**/Operator.kt]
which also determines when you need parentheses.  Temper's operator
precedence matches JavaScript's & TypeScript's as close as possible
so doing what you would in those languages is a good guide.)

⎀ builtins

⎀ builtin/%3F.

⎀ keywords
