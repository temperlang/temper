// The Temper extension: starts `temper lsp` and connects VS Code to it
// with vscode-languageclient, which syncs documents (incrementally, as
// the server asks), shows its diagnostics, and maps VS Code's providers
// to LSP's requests; the preview of literate Temper (preview.ts); the
// marks on its `temper inert` blocks (inert.ts); and std's and core's
// copies opened read-only (readonly.ts).

import * as vscode from "vscode";
import { LanguageClient, type LanguageClientOptions, type ServerOptions } from "vscode-languageclient/node";
import { registerInert } from "./inert";
import { type PreviewHook, registerPreview } from "./preview";
import { registerReadonly } from "./readonly";
import { type Found, notFound, notStarted, search } from "./server";

let client: LanguageClient | undefined;
let server: Found | undefined;
let lastError: string | undefined;

/** What the extension gives other extensions, and its tests. */
export interface Api {
  /** The `temper` running the server, if one was found. */
  server(): Found | undefined;
  /** The running client, once started. */
  client(): LanguageClient | undefined;
  /** What the last failure to start said, if the last start failed. */
  error(): string | undefined;
  /** For tests: the open preview of a `.temper.md`, if there is one. */
  preview(uri: vscode.Uri): PreviewHook | undefined;
  /** For tests: a document's `temper inert` blocks as last marked. */
  inert(uri: vscode.Uri): vscode.Range[] | undefined;
}

const OPEN_SETTING = "Open Setting";

/** Says why the server isn't running, in the log and where it's seen, offering the setting. */
function fail(output: vscode.LogOutputChannel, message: string): void {
  lastError = message;
  output.error(message);
  void vscode.window.showErrorMessage(message, OPEN_SETTING).then((choice) => {
    if (choice === OPEN_SETTING) void vscode.commands.executeCommand("workbench.action.openSettings", "temper.server.path");
  });
}

async function start(context: vscode.ExtensionContext, output: vscode.LogOutputChannel): Promise<void> {
  const config = vscode.workspace.getConfiguration("temper");
  lastError = undefined;
  const found = search({
    setting: config.get<string>("server.path", ""),
    extensionDir: context.extensionPath,
    platform: process.platform,
    env: process.env,
  });
  server = found.found;
  if (!server) return fail(output, notFound(found.tried, process.platform, process.arch));
  const args = ["lsp", ...config.get<string[]>("server.args", [])];
  output.appendLine(`Starting ${server.command} ${args.join(" ")} (${server.from})`);
  const folder = vscode.workspace.workspaceFolders?.[0];
  const serverOptions: ServerOptions = {
    command: server.command,
    args,
    options: folder ? { cwd: folder.uri.fsPath } : {},
  };
  const clientOptions: LanguageClientOptions = {
    // Files on disk only: a diff's old side (git:), a merge view, or a
    // file from another provider isn't part of a library on disk, so the
    // server can't analyze it and would mark the whole text as errors.
    documentSelector: [
      { scheme: "file", language: "temper" },
      { scheme: "file", language: "temper-markdown" },
    ],
    outputChannel: output,
  };
  const starting = new LanguageClient("temper", "Temper", serverOptions, clientOptions);
  client = starting;
  try {
    await starting.start();
  } catch (error) {
    // Not found after all, not executable, or exited before answering.
    client = undefined;
    void starting.dispose().catch(() => undefined);
    fail(output, notStarted(server, String(error instanceof Error ? error.message : error), process.platform, process.arch));
  }
}

async function stop(): Promise<void> {
  const running = client;
  client = undefined;
  if (running?.isRunning()) await running.stop();
}

export async function activate(context: vscode.ExtensionContext): Promise<Api> {
  const output = vscode.window.createOutputChannel("Temper", { log: true });
  const restart = async (): Promise<void> => {
    await stop();
    await start(context, output);
  };
  context.subscriptions.push(
    output,
    vscode.commands.registerCommand("temper.restartServer", restart),
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration("temper.server")) void restart();
    }),
  );
  const previews = registerPreview(context);
  const inert = registerInert(context);
  registerReadonly(context);
  await start(context, output);
  return { server: () => server, client: () => client, error: () => lastError, preview: (uri) => previews.get(uri), inert };
}

export async function deactivate(): Promise<void> {
  await stop();
}
