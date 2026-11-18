# Editors

Temper has a language server, and a Visual Studio Code extension built
on it. Other editors that speak the
[Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
can use the server too.

## VS Code

The extension highlights Temper (`.temper`) and literate Temper
(`.temper.md`), shows a rendered preview of literate files, and runs
the language server for:

- errors, warnings, and hints (faded, such as an unnecessary type
  check) as you type, for the whole library around a file, unsaved
  edits included;
- hover, with a name's type or signature and its doc comment;
- go to definition, into the standard library too;
- completion of names, members, and object literals' properties;
- find references, rename, the outline, and signature help.

Blocks tagged `temper inert`, Temper shown for reading but not compiled,
are highlighted too, tinted and labeled "inert" in the editor, the
preview, and on this site; the language server leaves them alone.

Each platform's package bundles the compiler; get it from a
[release](https://github.com/temperlang/tempest/releases), beside the
compiler, as `temper-PLATFORM-VERSION.vsix`. It isn't in the VS Code
Marketplace yet. How to install it, and its settings, are in
[editors/vscode](../../editors/vscode/README.md).

![Screenshot of VS Code with Temper code highlighted, with red error squiggles under "Float" in `public radius: Float` and a hover box noting "No declaration for Float". Elsewhere "Float64" appears without error squiggles.](../images/editor.png)

## The language server

`temper lsp`, or `temper serve`, speaks the protocol over standard
input and output.
[How it works](../internals/09-language-server.md) is in the internals.

> **Note:** Temper's earlier releases start the server with `temper
> serve --port N`, and their extension, "Temper Language Support", is
> built from their repository; now it's `temper lsp` (or `temper
> serve`, without `--port`), on standard input and output
> ([inconsistencies](../inconsistencies.md#i9-the-command-line)).
