### 🚨Breaking change: Use `when` for pattern matching

The builtin `match` has changed to `when` to reduce risk of collision with local
declarations called `match`, such as for instances of regex `Match`. Another
potential collision is for methods or functions named `match`. It was already
possible to work around collisions using `builtins.match`, but `when` seems much
less likely to collide with useful names in other code.

*Old* example with `match`:

```temper inert
let description = match (2) {
  0 -> "none";
  1, 2, 3 -> "little";
  else -> "lots or negative";
};
```

*New* example with `when`:

```temper inert
let description = when (2) {
  0 -> "none";
  1, 2, 3 -> "little";
  else -> "lots or negative";
};
```

Some options considered:

- `case` - Used to lead pattern matching blocks in some languages but carries
  different expectations in C-based syntax.
- `match` - The existing name is consistent with the [TC39 proposal][tc39-match]
  and several existing languages, but it has the collision risk previously
  discussed. The TC39 proposal uses `when` in place of common switch `case`, but
  it's also just a proposal rather than an accepted feature.
- `when` - Already used in Kotlin in similar ways. Used in other languages for
  match guards, which we eventually want in Temper. The corresponding
  placeholder keyword for match guards in Temper is now `given`.
- `switch` - Already a keyword in several common languages but possibly carries
  mental semantic baggage that are incompatible with Temper usage.

Given these options, we decide `when` is the most practical name to lea pattern
matching blocks.

[tc39-match]: https://github.com/tc39/proposal-pattern-matching?tab=readme-ov-file#examples-5
