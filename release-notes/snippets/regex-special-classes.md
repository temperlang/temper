### 🆕Regex special classes are exported

The special regex nodes, such as `Begin` and `Word`, are singletons that don't
support `==`. Their classes are now exported from `std/regex`, so code that
walks regex data can tell them apart:

```temper
when (node) {
  is BeginSpecial -> "^";
  is WordSpecial -> "\\w";
  else -> "other";
}
```

The classes are `BeginSpecial`, `DotSpecial`, `EndSpecial`,
`WordBoundarySpecial`, `DigitSpecial`, `SpaceSpecial`, and `WordSpecial`.
