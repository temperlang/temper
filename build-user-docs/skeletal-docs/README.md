# Skeletal Docs

Skeletal docs are templates for user documentation that incorporate chunks of documentation that
are co-located with source files so that developers can notice which docs probably need to change
when source code changes, and so we can derive diagrams and descriptions from code artifacts.

## Relevant Files

- `build-user-docs/`
  - `build/`
    - `snippets/`
      Contains a file for each snippet so that tools can read snippets to produce processed snippets.
      For example, a JS railroad diagram snippet can be run via a script under `scripts/` to produce an SVG snippet.
      See *ShellCommandSnippetContent*.
  - `skeletal-docs/`
    Copied to `docs/for-users/` as described under [Post processing](#post-processing).
  - `snippet-factory-scripts/`
    Directory for scripts that derive snippets.
    These may use tools that should be installed by anyone who wants to update the
    docs, but which need not be available to CI and Github actions that only
    check whether docs are up-to-date.
- `docs/`
  - `for-users/`
    Where post-processed docs live that are suitable inputs for the `mkdocs` tree.
    - `.snippet-hashes.json`
      Consistency hashes for snippets.

## Snippets

A *snippet* is a chunk of documentation extracted or derived from source files.

Most snippets are free-standing chunks of Markdown, but they can be other kinds of content,
for example images derived from diagrams.

Snippets are identified by snippet IDs.  Snippet IDs are strings
consisting of one or more `/` separated ID parts.
Each ID part must be non-empty and may not contain:

- ASCII spaces or control characters, or
- the Unix or Windows file and path separator characters: `/`, `\\`, `;`, `:`

Snippet IDs are used:

- to derive a path under `build-user-docs/build/snippets/` for the snippet content, and
- to allow linking to the snippet via Markdown links like `[snippet/foo/bar]`, and
- to allow inlining the snippet into a Markdown file via
  [snippet replacement syntax](#snippet-replacement)

## Defining snippets

`SnippetExtractors.kt` determines how snippets are found.

The easiest way to define a snippet is to just define a documentation comment
in a Kotlin or Temper source file:

    /**
     * This is a doc comment.
     *
     * <!-- snippet: foo/bar -->
     * This is markdown that is part of the snippet
     * named `foo/bar`.
     *
     * <!-- snippet: foo/baz -->
     * This is content in a different snippet.
     * Snippets do not overlap.
     *
     * ⎀ foo/bar
     *
     * That ^ odd line is a snippet insertion.
     * Snippets do not overlap, but the may include
     * one another.  The same syntax is used to include
     * snippets in Markdown files under build-user-docs/skeletal-docs.
     *
     * <!-- /snippet -->
     *
     * That last line was an explicit end to a snippet.
     * Snippets end implicitly when another snippet starts
     * or at the end of the comment.
     *
     * Either way, lines that start with KDoc annotations like
     * `@return` are not part of any snippet.
     */

### Short titles

When other code refers to a snippet, via a [markdown link](#code-and-snippet-references),
the default text for the link is the snippet short title.

If a snippet has an over-arching heading, then that title text is the short title by default.
So in the below, the short title is "Lorem ipsum".

    /**
     * <!-- snippet: lorem/ipsum -->
     * # Lorem ipsum
     * dolor sic amet
     */

But you can explicitly override the short title by putting text after a colon that is separated
by spaces from the snippet path.

    /**
     * <!-- snippet: lorem/ipsum : alternate title -->
     * # Lorem ipsum
     * dolor sic amet
     */

## Snippet replacement

The `⎀` symbol, followed by a snippet ID like `foo/bar` indicates a place where a snippet should be
inserted.  If there are multiple snippets with that path, any markdown snippet will be inserted.
If the only snippet has a mime-type that starts with `image/` then an inline image reference will
be inserted.

## Code and snippet references

Markdown link targets like `[temper/fundamentals/src/commonMain/kotlin/lang/temper/value/Value.kt]`
are rewritten to links to the corresponding source file on GitHub.

Link targets may use glob syntax, so `[temper/**/Operator.kt]`, finds the corresponding source file.
It's an error when a glob is ambiguous.

Similarly, link targets like `[snippet/foo/bar]` resolve to internal links to the place that the
snippet is inserted.  If a snippet is inserted in multiple places, then an insertion that has a
`canon` attribute it will be preferred over other insertions of the same snippet.

```md
<!-- The trailing `canon` is not considered part of the snippet ID,
   - because of the space.  Instead it marks this location as the
   - *canonical* location for this snippet.
   - Links like [snippet/foo/bar] will link here instead of to
   - other, non-canonical insertions of the same snippet. -->
⎀ foo/bar canon
```

## Insertion Attributes

After a colon following the snippet path, there can be arbitrary attributes that control how the insertion
happens.

An attribute is of the form:
- `-` *key* to indicate that the attribute value is `false`.
- `+` *key* or just *key* to indicate that the attribute value is `true`.
- *key* `=` *JsonValue* to indicate that the attribute value is the specified JsonValue.

For a full list see temper/**/SnippetAttributes.kt.

- `canon` specifies that this insertion should be linked to over others of the same snippet.
- `anchor="#..."` to specify that the insertion should have that anchor and be linked to by that anchor.
- `-heading` to specify that any uniquely important heading at the beginning should be stripped.

## Post-processing

The `skeletal-docs` file tree where markdown files have had snippets inserted and link targets rewritten
is copied over any contents under `docs/for-users/`.

`mkdocs` generates HTML from those docs so we can provide user manuals.

## Docs Review

Any changes to documentation snippets in Temper source code show up in PRs.  A unit test under `build-user-docs/`
also checks that snippets are up-to-date.  If it's not, it will explain how to use
`gradle build-user-docs:update-docs` to rebuild files under `docs/for-users/`.
(`docs/for-users/.snippet-hashes.json` contains hashes for each snippet to enable this even when the
developer's environment does not contain all the tools needed to regenerate image files).

That means that people code-reviewing changes to source files that update comments including snippets will
also include diffs for the documentation files in markdown, allowing for easy API and docs reviews.

## Updating

From the project root run

```sh
$ gradle build-user-docs:updateGeneratedDocs
```

That should regenerate the `docs/for-users/` directory.

## Viewing HTML docs

Before using `mkdocs` the first time, you need to install it and its
dependencies.
If you've got Python's `poetry` package manager, run the below
from the project root.

```sh
$ (cd build-user-docs/skeletal-docs/temper-docs/ &&
   poetry install)
```

`gradle build-user-docs:makeUserDocs` and `gradle build-user-docs:serveUserDocs`
make it easy to update the generated docs and run `mkdocs`.

Once you've got `mkdocs`, you can, from the project root run the
command below, and you should see instructions on where to connect:

```sh
$ (cd docs/for-users/temper-docs/ && mkdocs serve)
INFO    -  Building documentation...
INFO    -  Cleaning site directory
INFO    -  Documentation built in 0.37 seconds
[I 220401 18:05:50 server:335] Serving on http://127.0.0.1:8000
INFO    -  Serving on http://127.0.0.1:8000
```

`mkdocs build` will package HTML files and supporting files under
`docs/for-users/temper-docs/docs/site`.
