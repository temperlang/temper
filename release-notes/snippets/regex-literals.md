### Regex literals

Temper now supports regex literals using either of the following syntax forms:

- `/a\s*b/`
- `rgx"a\s*b/"`

This is implemented using the [temper-regex-parser library][regex-parser], which
is itself written in Temper, then compiled to Java and publish to
[Maven Central][regex-parser-maven]. There is no plan to fully self-host the
Temper compiler in Temper, but this still is an example of partial self-hosting
and demonstrates the promise of Temper for writing and sharing libraries.

Regex literals require import of `"std/regex"` (possibly renamed in the future)
and produce regexes in compiled form. To simplify future usage, we've renamed
`CompiledRegex` to `Regex` and what was `Regex` to `RegexNode`.

[regex-parser]: https://github.com/temperlang/temper-regex-parser
[regex-parser-maven]: https://central.sonatype.com/artifact/dev.temperlang/temper-regex-parser
