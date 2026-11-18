// Makes editors of std's and core's sources read-only. The language
// server writes them, without write permission, to `temper-std-HASH`
// in the system's temporary directory for go to definition, but VS Code
// ignores file permissions unless `files.readonlyFromPermissions` is
// set, and `files.readonlyInclude` globs only match inside a workspace.
// So each such editor, once active, is marked read-only for the session.

import * as vscode from "vscode";

/** Whether a document is one of the server's copies of std or core. */
export function isStdCopy(uri: vscode.Uri): boolean {
  return uri.scheme === "file" && /[\\/]temper-std-[0-9a-f]{16}[\\/]/.test(uri.fsPath);
}

export function registerReadonly(context: vscode.ExtensionContext): void {
  const mark = (editor: vscode.TextEditor | undefined): void => {
    if (editor && isStdCopy(editor.document.uri)) {
      void vscode.commands.executeCommand("workbench.action.files.setActiveEditorReadonlyInSession");
    }
  };
  context.subscriptions.push(vscode.window.onDidChangeActiveTextEditor(mark));
  mark(vscode.window.activeTextEditor);
}
