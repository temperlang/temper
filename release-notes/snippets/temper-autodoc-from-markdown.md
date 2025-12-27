### Autodoc comments extracted from Markdown

Previously, Temper in Markdown allowed describing Temper declarations
but that information was not stored as documentation-string metadata.

    This is markdown prose.  Explaining why this code is the way it is.

    *fib* computes the n-th fibonacci number.

        export let fib(

    n should be >= 1.

          n: Int
        ): Int {
          ...
        }

There, markdown paragraphs are interleaved with indented, unfenced
code sections.

There are two declarations there, `fib`, and its parameter, `n`.

Now, when parsing in markdown mode, Temper stores enough information
about Markdown paragraphs so that it can find that the second paragraph
starts with "fib" and lexically precedes the definition of `fib`
and similarly for the third paragraph and `n`.

Both of those declarations get metadata, available to backends, with
the paragraphs following the one that starts with their name.

The specific rules are:

- If a declaration has no associated `/** ... */` comment preceding
  it inside the fenced code, then Temper looks for paragraphs of markdown.
- It only considers paragraphs that precede the declaration and which
  are not lexically separated by another declaration.
- The first eligible paragraph is the first considered that starts
  with the declared name, case insensitively,
  and after stripping markdown metacharacters: backtick (\`) for code
  formatting and asterisk (\*) for bold/italic styling.
- The first eligible and following paragraphs are joined together
  to make the doc strings with blank lines separating paragraphs.

Because of the name matching rule, the first paragraph is not included
in *fib*'s documentation.
