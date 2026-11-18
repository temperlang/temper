# Writing these docs

The docs are Markdown that reads well on GitHub, and the same files,
unchanged, make the [docs site](https://temperlang.github.io/tempest/)
(see [the site](#the-site)).

## Where things go

| Folder | For |
|---|---|
| [guide/](../guide/README.md) | Learning Temper: the tour, getting started, and the tutorial |
| [language/](../language/README.md) | The language reference |
| [std/](../std/README.md) | The standard library, a page per module |
| [backends/](../backends/README.md) | What each target language gets |
| [tools/](../tools/README.md) | The command line, editors, and the playground |
| [contributing/](README.md) | Working on the compiler, and these docs |
| [internals/](../internals/README.md) | How the compiler works, stage by stage |
| [release-notes/](../release-notes/README.md) | What changed, release by release |
| [`../notes/`](../../notes/README.md) | Design notes and plans, kept apart from the docs |

Each folder's `README.md` is its index. Images go in
[images/](../images/); keep them small, and give each a real `alt`
text. Language logos and their terms are in
[images/lang-icons](../images/lang-icons/README.md).

## Conventions

Plain GitHub Flavored Markdown, plus a few conventions that the site
styles:

- **Callouts** are GitHub alerts: `> [!NOTE]`, `> [!TIP]`,
  `> [!IMPORTANT]`, `> [!WARNING]`, and `> [!CAUTION]`. (mkdocs'
  `!!! note` became these.)
- **Inconsistencies** are flagged in place with a one-line
  `> **Note:** …` that links to its entry in
  [inconsistencies.md](../inconsistencies.md). Remove both when it's
  resolved.
- **Alternatives,** such as one example in each language, go in
  `<details><summary>Python</summary> … </details>` blocks, one per
  language, with a blank line after `<summary>` so the Markdown inside
  renders. Use `<details open>` for the one to show first. (mkdocs'
  `=== "Tab"` tabs became these.)
- **Grammar** is written out as text, in `ebnf` blocks:
  `Rule := Other "token" | …`. (These were railroad diagrams, drawn from
  the same text; a renderer could draw them again.)
- **Diagrams** are [Mermaid](https://mermaid.js.org/) blocks, which
  GitHub draws, and the site draws when it's built.
- **Temper examples** are ```` ```temper ```` blocks, and each ends
  with a marker comment saying what it does, as below. Literate Temper
  files are ```` ```tempermd ````, interactive sessions ```` ```console ````,
  and Temper that's only shown, never compiled, ```` ```temper inert ````,
  which the site highlights, tints, and labels "inert".
- Links are relative, to files in this repository, and never carry line
  numbers; link a file, and name what's in it.
- Write "Temper" for the language, its compiler, and its tools, and
  `temper` for the command. "tempest" is only the CLI crate's name,
  for contributors.

### Examples that are checked

`compat/check_doc_examples.py` runs every ```` ```temper ```` block in
the docs (all but `internals/` and `release-notes/`), each as its own
one-file library, with `temper test -v`, on the interpreter. The
marker on a block's last line says what should happen:

| Marker | Means | Checked |
|---|---|---|
| `// ✅` or `// ✅ value` | It runs without errors | It runs cleanly, and logs what its `//!outputs` comments say |
| `// ❌` or `// ❌ errors` | It's an error | It reports an error |
| `// ⏸️` | A fragment, or it needs context | Not run |
| none | Usually part of a longer example | Run, but only counted |

A `//!outputs "text"` comment (a JSON string, or a list of them) after
a statement says what it logs. The values after `✅` and the errors
after `❌` aren't compared yet.

    uv run compat/check_doc_examples.py [--verbose] [PATH...]

It uses `target/release/temper`, else `target/small/temper`, or
`--temper BIN`, and runs in CI. `--verbose` also lists the unmarked
blocks that don't run alone, mostly tutorial steps that build on the
ones before.

A marked example known to disagree with the compiler gets an HTML comment,
hidden on GitHub, on the line right before its fence, naming its
[inconsistencies](../inconsistencies.md) entries, such as
`<!-- known: I10 I20 -->`. It's then counted as known, not fatal, and
the checker lists the known examples by entry. Once one agrees, the
checker fails until you remove the comment and update the entry. Today, 14 are known
([I10](../inconsistencies.md#i10-examples-that-dont-run-as-marked)).

## Where they came from

Most of the guide, reference, and release notes were Temper's mkdocs
site, built from `docs/for-users/` and `build-user-docs/` in the
[temperlang/temper](https://github.com/temperlang/temper) repository.
There, the reference pages (types, builtins, and syntax) were woven
together from doc comments in the compiler's Kotlin source and std,
with each example's marker and value filled in by running it. Here,
they're plain Markdown, imported once from that generated output and
edited by hand since. If you change a builtin, update its page here.

The old site's addresses map to these pages, for redirects:

| temperlang.dev | Here |
|---|---|
| `/` | [guide/introduction.md](../guide/introduction.md) |
| `/tour/` | [guide/tour.md](../guide/tour.md) |
| `/why/` | [guide/why.md](../guide/why.md) |
| `/tutorial/` | [guide/getting-started.md](../guide/getting-started.md) |
| `/tutorial/01-classfun/` | [guide/tutorial/01-classes-and-functions.md](../guide/tutorial/01-classes-and-functions.md) |
| `/tutorial/02-interface/` | [guide/tutorial/02-interfaces.md](../guide/tutorial/02-interfaces.md) |
| `/tutorial/03-controlflow/` | [guide/tutorial/03-control-flow.md](../guide/tutorial/03-control-flow.md) |
| `/tutorial/04-modlib/` | [guide/tutorial/04-modules-and-libraries.md](../guide/tutorial/04-modules-and-libraries.md) |
| `/tutorial/05-builtin/` | [guide/tutorial/05-builtin-types.md](../guide/tutorial/05-builtin-types.md) |
| `/tutorial/06-memory/` | [guide/tutorial/06-memory.md](../guide/tutorial/06-memory.md) |
| `/tutorial/07-regex/` | [guide/tutorial/07-regex.md](../guide/tutorial/07-regex.md) |
| `/reference/` | [language/README.md](../language/README.md) |
| `/reference/types/` | [language/types.md](../language/types.md) |
| `/reference/builtins/` | [language/builtins.md](../language/builtins.md) |
| `/reference/syntax/` | [language/syntax.md](../language/syntax.md) |
| `/reference/modules/` | [language/modules.md](../language/modules.md) |
| `/reference/target-languages/` | [backends/README.md](../backends/README.md) |
| `/reference/backend/` | [internals/kotlin/writing-a-backend.md](../internals/kotlin/writing-a-backend.md) |
| `/reference/out-grammar/` | [internals/kotlin/out-grammars.md](../internals/kotlin/out-grammars.md) |
| `/legal/playground-terms/` | [legal/playground-terms.md](../legal/playground-terms.md) |
| `/blog/` | stays in its own repository, `temperlang/temperlang-blog` |

Anchors changed too: the old pages linked generated anchors, such as
`#type-Boolean`; these link GitHub's heading anchors, such as
`#class-boolean`.

## The site

The [docs site](https://temperlang.github.io/tempest/) is README.md
as its home page and every page of docs/ at the same path, as
`.html`, with the
[playground](https://temperlang.github.io/tempest/playground/) beside
them.

[Kelpto](https://github.com/temperlang/kelpto), the lossless Markdown
and HTML library written in Temper, renders it: `temper` builds Kelpto
for Python, and site/docs' [`build.py`](../../site/docs/build.py), with
nothing but Python's standard library, parses each page as GitHub
Flavored Markdown with raw HTML and renders it with Kelpto's
`renderSafe`. Then it adds what Kelpto leaves to us: GitHub's heading
anchors and alerts, Temper highlighting with the VS Code extension's
grammar, a sidebar from each folder's `README.md`, the site's header
and footer, and links rewritten for the site (`.md` to `.html`, and `README.md` to
`index.html`). Links to the repository's files outside docs/ go to
GitHub, pinned to the commit the site is built from (HEAD, or
`$GITHUB_SHA` in Actions, or `--commit REV`), with `blob/` for files and
`tree/` for folders; headings in `.md` files and line fragments
(`#L12`, `#L10-L20`) carry over. If the working tree has uncommitted
changes, the build says so, since the links show HEAD without them. The
license files are published as `.txt`, so their links stay on the site.
What Kelpto's sanitizer drops (comments, `style`,
and mkdocs' `markdown` attributes) must be listed, with a reason, in
`site/docs/removals.txt`, or the build fails, so content can't
silently go missing from a page. The build ends by
checking that every link on the site leads to a page, a file, and a
heading that exist, and that every link to GitHub leads to a file or
folder at that commit, a heading in it, and lines within it, by git's
own objects, not the network; it fails if one doesn't. Links into the
playground are only listed when it isn't built.

The header is one source for the docs and the playground:
site/shared's [`header.py`](../../site/shared/header.py) makes it (the
logo, which links home, the sections, Playground, and the theme
toggle), with the docs' footer and the playground's About (the
licenses, the repository, and the commit built), styled by
`header.css` and run by `header.js`. The theme is the system's until
the toggle picks light or dark, kept in `localStorage` as
`temper-theme`, so the choice carries between the docs and the
playground. Narrower than 860 px, the sections fold behind a Menu
button. The playground's smoke test checks that a docs page's header
matches its own and that the theme carries.

Mermaid diagrams are drawn at build time, into inline SVG, so readers
get them at once, with no script: each twice, in the site's light and
dark colors, shown by the theme, with its source in a
"Diagram source" box below. site/docs/mermaid's
[`render.mjs`](../../site/docs/mermaid/render.mjs) runs mermaid in one
headless Chromium page, through playwright-core, with every request
blocked; both are pinned in its `package-lock.json`, and `build.py`
runs `npm ci` there when they change. Drawings are cached in
`site/_out/mermaid/`, so only new or changed diagrams are drawn. Wide
diagrams shrink to fit only so far, then scroll sideways.

To preview it, with Kelpto checked out beside this repository
(`../kelpto`, or `--kelpto DIR`) and `temper` built:

    uv run site/playground/build.py --out site/_out/site/playground   # optional
    uv run site/docs/build.py [--chromium PATH | --no-diagrams]
    python3 -m http.server -d site/_out/site

Diagrams need Node and a Chromium: `--chromium PATH`, else
`$TEMPER_CHROMIUM`, else Playwright's own, which
`npx playwright-core install --only-shell chromium` in site/docs/mermaid
installs. Without one, the build stops and says so; `--no-diagrams`
shows their source instead. The pages take about ten seconds, and
drawing every diagram, uncached, about seven more. CI
([pages.yml](../../.github/workflows/pages.yml)) does the same on each
push to `main` that changes the docs or the playground, with the Kelpto
commit in site/docs' [`kelpto-ref`](../../site/docs/kelpto-ref); move
that forward to pick up Kelpto's fixes. CI
([ci.yml](../../.github/workflows/ci.yml)) builds it too, diagrams and
link check included, on every push and pull request.

### Why Kelpto

The options were:

| | For | Against |
|---|---|---|
| **mkdocs** (Material), as before | Proven, search, navigation, and themes built in; the old site's look | A Python toolchain beside Rust; needs plugins or rewriting for GitHub alerts and `<details>`; its own Markdown dialect differs from GitHub's in places |
| **A small custom renderer** (a Rust binary, `pulldown-cmark` or `comrak`) | Fast, one toolchain; could run examples and draw grammar while rendering | We'd write and keep navigation, search, and themes; a new dependency |
| **Kelpto**, the lossless Markdown and HTML library written in Temper | Dogfooding: Temper's docs rendered by a Temper library, through `temper`, in any of seven languages; GFM conformant, with HTML sanitizing and link checks; lossless edits could keep the docs tidy (rewrite links, number the inconsistencies) | Young; navigation, search, and highlighting are ours to add; its build depends on `temper` building it |

Kelpto shows off exactly what Temper is for, and what the site adds
(heading anchors, alerts, highlighting) would help Kelpto's users too.
Still to come: search, running
`check_doc_examples.py` first, and, someday, each example's
translations, made by `temper` at build time, in place of the old tabs.

## To do

- Make the known examples zero, by fixing the compiler or the docs.
- Compare the values after `✅` and the errors after `❌`, as Temper's
  first doc generator did.
- Make tutorial steps checkable: mark fragments `⏸️`, or run a page's
  unmarked blocks together, in order, as one literate file.
- Write the reference for what the old generator left as "TODO", such as
  `@protected` and `@public`, `await`, and the Java backend's notes.
- Reference pages for the standard library generated from its doc
  comments, or kept by hand as now.
