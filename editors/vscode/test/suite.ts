// The extension's tests in a real VS Code, run by run.ts with
// @vscode/test-electron: VS Code loads this module in its extension host
// and calls `run`. The workspace is a library, with shapes.temper and
// use.temper, and the extension is the platform package as built, so
// these talk to its bundled temper.

import assert from "node:assert/strict";
import { statSync } from "node:fs";
import * as vscode from "vscode";
import type { Api } from "../src/extension";
import type { FromPreview } from "../src/messages";

const sleep = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

/** Waits up to 30 s for `f` to give something truthy. */
async function until<T>(what: string, f: () => T | undefined | false): Promise<T> {
  for (let waited = 0; waited < 30_000; waited += 50) {
    const found = f();
    if (found) return found;
    await sleep(50);
  }
  throw new Error(`timed out waiting for ${what}`);
}

const text = (h: vscode.Hover): string =>
  h.contents.map((c) => (typeof c === "string" ? c : "value" in c ? c.value : "")).join("\n");

export async function run(): Promise<void> {
  const extension = vscode.extensions.getExtension<Api>("temperlang.temper");
  assert.ok(extension, "the Temper extension is installed");
  const api = await extension.activate();
  const server = api.server();
  assert.equal(server?.from, "bundled", `uses the bundled temper, not ${JSON.stringify(server)}`);
  console.log(`temper: ${server?.command}`);

  const folder = vscode.workspace.workspaceFolders?.[0]?.uri;
  assert.ok(folder, "a workspace folder");
  const shapes = vscode.Uri.joinPath(folder, "shapes.temper");
  const uses = vscode.Uri.joinPath(folder, "use.temper");
  const document = await vscode.workspace.openTextDocument(uses);
  await vscode.window.showTextDocument(document);
  assert.equal(document.languageId, "temper");
  const tests: [string, () => Promise<void>][] = [];
  const test = (name: string, f: () => Promise<void>): void => void tests.push([name, f]);

  test("diagnostics, for unsaved text too", async () => {
    // use.temper starts with `let b: String = a;`, where `a` is an Int.
    const [d] = await until("an error", () => {
      const found = vscode.languages.getDiagnostics(uses);
      return found.length === 1 && found;
    });
    assert.equal(d?.range.start.line, 1);
    assert.equal(d?.severity, vscode.DiagnosticSeverity.Error);
    assert.equal(d?.source, "temper");
    // Fixed as typed: an incremental change, `String` to `Int`.
    const edit = new vscode.WorkspaceEdit();
    edit.replace(uses, new vscode.Range(1, 7, 1, 13), "Int");
    assert.ok(await vscode.workspace.applyEdit(edit));
    assert.equal(document.getText(), "let a = area(3);\nlet b: Int = a;\n");
    await until("no errors", () => vscode.languages.getDiagnostics(uses).length === 0);
  });

  test("only file: documents go to the server", async () => {
    // An untitled document, as a diff's old side (git:) isn't on disk
    // either: the server never sees it, so its error isn't reported. The
    // test above shows the same error in a file is, within the debounce.
    const untitled = await vscode.workspace.openTextDocument({
      language: "temper",
      content: "let s: String = 1;\n",
    });
    assert.equal(untitled.uri.scheme, "untitled");
    await sleep(1_500);
    assert.deepEqual(vscode.languages.getDiagnostics(untitled.uri), []);
  });

  test("temper inert blocks are marked, and follow edits", async () => {
    const doc = await vscode.workspace.openTextDocument({
      language: "temper-markdown",
      content: "# Notes\n\n```temper inert\nlet a = 1;\n```\n\n```temper\nlet b = 2;\n```\n",
    });
    await vscode.window.showTextDocument(doc);
    const lines = (): string => (api.inert(doc.uri) ?? []).map((r) => `${r.start.line}-${r.end.line}`).join(",");
    await until("the inert block marked", () => lines() === "2-4");
    // The compiled block made inert: both marked, after the pause.
    const edit = new vscode.WorkspaceEdit();
    edit.insert(doc.uri, new vscode.Position(6, 9), " inert");
    assert.ok(await vscode.workspace.applyEdit(edit));
    await until("both marked", () => lines() === "2-4,6-8");
  });

  test("hover", async () => {
    const found = await vscode.commands.executeCommand<vscode.Hover[]>(
      "vscode.executeHoverProvider",
      uses,
      new vscode.Position(0, 9),
    );
    const shown = found.map(text).join("\n");
    assert.match(shown, /function area\(side: Int32\): Int32/);
    assert.match(shown, /A square's area\./);
  });

  test("definition, in the other file", async () => {
    const found = await vscode.commands.executeCommand<(vscode.Location | vscode.LocationLink)[]>(
      "vscode.executeDefinitionProvider",
      uses,
      new vscode.Position(0, 9),
    );
    assert.equal(found.length, 1);
    const [first] = found;
    const [uri, range] = first && "uri" in first ? [first.uri, first.range] : [first?.targetUri, first?.targetSelectionRange ?? first?.targetRange];
    assert.equal(uri?.toString(), shapes.toString());
    assert.equal(range?.start.line, 1);
    assert.equal(range?.start.character, 11);
  });

  test("definition of a builtin, in core, read-only", async () => {
    // `Int` in `let b: Int = a;`, as the first test left it.
    const found = await vscode.commands.executeCommand<(vscode.Location | vscode.LocationLink)[]>(
      "vscode.executeDefinitionProvider",
      uses,
      new vscode.Position(1, 8),
    );
    assert.equal(found.length, 1);
    const [first] = found;
    const [uri, range] = first && "uri" in first ? [first.uri, first.range] : [first?.targetUri, first?.targetSelectionRange ?? first?.targetRange];
    assert.ok(uri && range, "a location");
    assert.match(uri.path, /\/temper-std-[0-9a-f]+\/core\/core\.temper$/);
    // Read-only on disk, and to VS Code, as the extension marks it
    // (readonly.ts).
    assert.equal((statSync(uri.fsPath).mode & 0o222), 0, "no write permission");
    const editor = await vscode.window.showTextDocument(uri, { selection: range });
    const core = editor.document;
    assert.equal(core.languageId, "temper");
    assert.match(core.lineAt(range.start.line).text, /^export let Int = Int32;/);
    assert.equal(core.getText(range), "Int");
    assert.equal(editor.selection.start.line, range.start.line);
    // A read-only editor drops edits made in it (though `edit` still
    // says true), once marked.
    const before = core.getText();
    await sleep(200);
    await editor.edit((e) => e.insert(new vscode.Position(0, 0), "x"));
    assert.equal(core.getText(), before, "an edit to core is dropped");
    assert.equal(core.isDirty, false);
    await vscode.commands.executeCommand("workbench.action.closeActiveEditor");
    await vscode.window.showTextDocument(document);
  });

  test("completion", async () => {
    const found = await vscode.commands.executeCommand<vscode.CompletionList>(
      "vscode.executeCompletionItemProvider",
      uses,
      new vscode.Position(1, 0),
    );
    const labels = found.items.map((i) => (typeof i.label === "string" ? i.label : i.label.label));
    assert.ok(labels.includes("area") && labels.includes("a"), labels.join(" "));
  });

  test("rename, across both files", async () => {
    const found = await vscode.commands.executeCommand<vscode.WorkspaceEdit>(
      "vscode.executeDocumentRenameProvider",
      uses,
      new vscode.Position(0, 9),
      "squared",
    );
    const files = found.entries().map(([uri, edits]) => {
      assert.ok(edits.every((e) => e.newText === "squared"));
      return uri.toString();
    });
    assert.deepEqual(files.sort(), [shapes.toString(), uses.toString()].sort());
  });

  const notes = vscode.Uri.joinPath(folder, "notes.temper.md");
  /** The preview's first message since `from` that `f` gives something for. */
  const heard = <T>(what: string, from: number, f: (m: FromPreview) => T | undefined | false): Promise<T> =>
    until(what, () => {
      const messages = api.preview(notes)?.messages.slice(from) ?? [];
      for (const m of messages) {
        const found = f(m);
        if (found) return found;
      }
      return undefined;
    });
  const heardCount = (): number => api.preview(notes)?.messages.length ?? 0;

  test("preview: headings, highlighted Temper, and a relative image", async () => {
    const editor = await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(notes));
    assert.equal(editor.document.languageId, "temper-markdown");
    await vscode.commands.executeCommand("temper.showPreviewToSide");
    const shown = await heard("the preview", 0, (m) => m.type === "rendered" && m);
    assert.equal(shown.headings[0], "Notes on shapes");
    assert.equal(shown.headings.length, 61);
    // `let noteName = "square";` and `let noteSide = 2;`: fenced and indented.
    assert.ok(shown.highlighted >= 6, `${shown.highlighted} highlighted tokens`);
    const images = await heard("the image", 0, (m) => m.type === "images" && m);
    assert.deepEqual(images, { type: "images", loaded: 1, failed: 0 });
  });

  test("preview: an edit updates it", async () => {
    const from = heardCount();
    const edit = new vscode.WorkspaceEdit();
    edit.insert(notes, new vscode.Position(1, 0), "\n## Added as typed\n");
    assert.ok(await vscode.workspace.applyEdit(edit));
    const version = (await vscode.workspace.openTextDocument(notes)).version;
    const shown = await heard("the update", from, (m) => m.type === "rendered" && m.version === version && m);
    assert.equal(shown.headings[1], "Added as typed");
  });

  test("preview: scrolls with the editor, and the editor with it", async () => {
    const editor = vscode.window.visibleTextEditors.find((e) => e.document.uri.toString() === notes.toString());
    assert.ok(editor, "the editor beside the preview");
    let from = heardCount();
    editor.revealRange(new vscode.Range(150, 0, 150, 0), vscode.TextEditorRevealType.AtTop);
    const scrolled = await heard("the preview to follow", from, (m) => m.type === "scrolled" && m.line >= 140 && m);
    assert.ok(scrolled.top > 1000, `scrolled to ${scrolled.top}`);
    // A reader scrolls the preview back to the top: the editor follows,
    // and its scrolling doesn't scroll the preview again.
    from = heardCount();
    await api.preview(notes)?.post({ type: "userScroll", top: 0 });
    await until("the editor to follow", () => (editor.visibleRanges[0]?.start.line ?? 99) < 3);
    await sleep(500);
    const after = api.preview(notes)?.messages.slice(from) ?? [];
    assert.ok(!after.some((m) => m.type === "scrolled"), JSON.stringify(after));
  });

  // Last, as it stops the server: a temper that isn't there says so,
  // naming what was tried and the setting, rather than failing to spawn.
  test("a missing temper says what was tried", async () => {
    const config = vscode.workspace.getConfiguration("temper");
    await config.update("server.path", "/nowhere/temper", vscode.ConfigurationTarget.Global);
    try {
      const message = await until("an error", () => api.error());
      assert.match(message, /tried temper\.server\.path, \/nowhere\/temper\)/);
      assert.match(message, /Temper: Server Path/);
      assert.equal(api.client(), undefined);
    } finally {
      await config.update("server.path", undefined, vscode.ConfigurationTarget.Global);
    }
  });

  let failed = 0;
  for (const [name, f] of tests) {
    try {
      await f();
      console.log(`ok - ${name}`);
    } catch (error) {
      failed += 1;
      console.log(`not ok - ${name}\n${String(error instanceof Error ? error.stack : error)}`);
    }
  }
  console.log(`vscode tests: ${tests.length - failed} passed, ${failed} failed`);
  if (failed) throw new Error(`${failed} of ${tests.length} VS Code tests failed`);
}
