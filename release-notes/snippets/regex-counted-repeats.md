### Counted repeats in regex literals

Regex literals now support counted repeats, greedy or reluctant: `/a{2}/`,
`/a{2,}/`, `/a{1,3}/`, and `/a{1,3}?/`. Previously the braces were read as
literal text. This comes from an updated build of the
[temper-regex-parser library](https://github.com/temperlang/temper-regex-parser),
which is now built from source with the compiler instead of fetched from Maven
Central.
