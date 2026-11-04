### Regex literals gain syntax and reject more mistakes

Regex literals now also support:

- Case-insensitive groups for ASCII letters, as in `/(?i:abc)/`
- Extended groups that ignore whitespace and `#` comments, as in `/(?x: a b )/`
- Comments, as in `/a(?#note)b/`
- The anchors `\A` and `\z`, and the escapes `\cX` and `\e`
- `\` before any ASCII punctuation or a space
- `\D`, `\S`, and `\W` inside code sets, as in `/[a\D]/`

A code set ending in a dash now keeps the member before the dash, so `/[+-]/`
matches `+` as well as `-`.

Some literals that used to compile are now errors, so existing code might need
changes:

- Capture names must be an ASCII letter followed by ASCII letters or digits, so
  `(?first_name=...)` needs a new name, such as `(?firstName=...)`.
- Capture names must be unique within a regex.
- Ranges must be in order, so `[z-a]` is rejected.
- A repeat can't directly follow another, so `a{2}{3}` is rejected.
- Repeat counts must fit in 32 bits.
