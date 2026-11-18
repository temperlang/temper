# Temper for VS Code

Highlighting for Temper (`.temper`) and literate Temper (`.temper.md`),
and Temper's language server, `temper lsp`, for:

- errors, warnings, and hints (such as an unnecessary type check) as
  you type, for the whole library around a file (its
  `config.temper.md` and every file in it), unsaved edits included;
- hover: what a name is, its type or signature, and its doc comment
  (builtins' too, from core);
- go to definition, into std's sources and core's builtins too
  (`String`, `ignore`, a `ListBuilder`'s `add`), opened read-only;
- completion of names in scope, members after `.` (builtin types' too),
  and an object literal's property names;
- find references and rename, across the library's files;
- the outline (document symbols), and signature help in calls.

Blocks tagged `temper inert`, Temper shown but not compiled, are
highlighted too, with their lines tinted and an "inert" label after the
fence (colors `temper.inertBlockBackground` and
`temper.inertLabelForeground`), and the same tint and label in the
preview. The language server doesn't analyze them.

And a rendered preview of literate Temper, as VS Code's own Markdown
preview does for `.md`: "Temper: Open Markdown Preview" (Ctrl+Shift+V,
or Cmd+Shift+V on macOS) in place of the editor, "Temper: Open Markdown
Preview to the Side" (Ctrl+K V) or the editor title's preview button
beside it. It updates as you type, highlights Temper code blocks as the
editor does, and scrolls with the editor both ways. It renders with VS Code's
built-in Markdown extension, so needs that enabled. Relative images and
links work; links to the web open in the browser; nothing remote loads
in the preview itself, and scripts in the document don't run.

## Install

Each platform's package bundles `temper`: `temper-linux-x64-VERSION.vsix`,
and likewise `linux-arm64`, `darwin-arm64`, `win32-x64`, and
`win32-arm64`. The universal `temper-VERSION.vsix` has none, and
uses `temper` on `PATH`. CI's Binaries workflow builds them all, and
each release, and the `nightly` prerelease, on
[GitHub](https://github.com/temperlang/tempest/releases) carries them.
The extension isn't in the VS Code Marketplace yet.

Install one with `code --install-extension temper-linux-x64-VERSION.vsix`,
or in VS Code, the Extensions view's `...` menu, "Install from VSIX...",
then reload the window ("Developer: Reload Window") so the new one runs.
Each build has its own VERSION, package.json's major and minor with the
minutes since 2026 as the patch, so a new package always replaces an
older one.

The server is **Temper: Server Path** (`temper.server.path`), a path
or a name on `PATH`, if set; else the bundled `temper`, then `temper`
on `PATH`. If there's none, or it doesn't start, an error says what was
tried and how to fix it, with a button to open the setting. **Temper: Server Args**
(`temper.server.args`) adds arguments, as `["--std", "/path/to/std"]`.
"Temper: Restart Language Server" restarts it; its log is in the Output
panel's "Temper" channel.

## Build

With Node 22 or later, and uv:

```sh
cargo build --profile small -p tempest
uv run editors/vscode/build.py --temper target/small/temper --test
```

To build with a fresh release `temper` and install the result in one
step (`code --install-extension`, from WSL into the connected VS Code
too; without `code`, it says how to install by hand), then run
"Developer: Reload Window":

```sh
uv run editors/vscode/install.py [--no-build] [--profile small]
```

`build.py` writes `editors/vscode/out/temper-VERSION.vsix` and
`out/temper-linux-x64-VERSION.vsix` (on Linux x64), and with `--test`,
runs the tests in a real VS Code. `--binaries DIR` bundles each
`DIR/temper-RUST_TARGET/temper[.exe]` in its platform's package.

## How it's built

- `syntaxes/`: the TextMate grammars, checked by `check_grammar.py`,
  whose small tokenizer scopes samples, and by `test/grammar.mjs`, which
  runs them with VS Code's own engine (vscode-textmate, with Oniguruma as
  wasm from vscode-oniguruma) over samples and every literate file in the
  repository: `node editors/vscode/test/grammar.mjs [FILE...]`.
- `src/extension.ts`: starts `temper lsp` with `vscode-languageclient`,
  which syncs documents incrementally and maps VS Code's features to
  LSP's requests. `src/server.ts` finds `temper`, tested by
  `src/server.test.ts` under Node's test runner.
- `src/preview.ts`: the preview, a webview of what VS Code's Markdown
  engine renders (`markdown.api.render`); `src/page.ts` highlights its
  Temper blocks (`src/highlight.ts`, a small TextMate tokenizer on
  `syntaxes/temper.tmLanguage.json`) and builds the page and its CSP;
  `webview/preview.ts` is its script, scrolling by the engine's
  `data-line` marks (`src/sync.ts`); `media/preview.css` its look.
- `src/inert.ts`: the editor's marks on `temper inert` blocks, found by
  `src/page.ts`'s `inertBlocks`, as the preview finds them.
  `src/page.test.ts` tests the pure parts under Node's test runner.
- `src/readonly.ts`: marks editors of the server's std and core copies
  (`temper-std-HASH` in the temporary directory) read-only, since VS
  Code ignores the files' permissions by default.
- `test/suite.ts`: diagnostics (and an incremental edit), none for an
  untitled document, inert blocks' marks following an edit, hover,
  definition (also of a builtin, into core's read-only copy),
  completion, rename, and the preview (rendered, updated,
  and scrolled both ways), in a real VS Code on a small library, run by `test/run.ts` with `@vscode/test-electron` against
  the platform package as built, with its `temper`. On Linux without a
  display, it runs under `xvfb-run`, or else Electron's headless mode.
- `build.py`: type checks, bundles with esbuild, tests, and packages
  with `vsce package`, from a staging directory with just what ships.

The architecture is in
[docs/internals/09-language-server.md](../../docs/internals/09-language-server.md).
