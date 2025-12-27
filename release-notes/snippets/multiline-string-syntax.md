### 🚨Breaking change: Multiline string syntax

Previously, Temper used Python-esque triple quoted string syntax but
with rules for stripping incidental spaces from the beginning and ends
of lines.

```temper intert
let s = """
  Two spaces stripped per line
  Including here
  Can't have """ here
  """;
```

Triple-quoted string syntax has changes so that there is no end
delimiter, and there is a margin-character on each line.

```temper
let s = """
  "<-- That quote mark is not part of this line
  "But it means that we can have """ here without problem
;  // Since this line didn't start with ", the string ended.
```

Spaces (and tabs) are still stripped from the ends of lines, so use
`${}` if you need a line to end with spaces.

This change should make it easier to tell (once you know the rule)
where a line starts, and make it easier to auto-format Temper code
including code with syntax errors.
