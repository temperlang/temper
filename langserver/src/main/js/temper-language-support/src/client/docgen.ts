import { LanguageClient } from "vscode-languageclient/node";
import * as vscode from "vscode";

// Just a simple heuristic to try to avoid auto full document selection.
// TODO(tjp, tooling): If this problem doesn't happen for others, why not?
let openTime = 0;

export function initContentProvider(context: vscode.ExtensionContext) {
  const contentProvider = new ContentProvider();
  context.subscriptions.push(contentProvider);
  context.subscriptions.push(
    vscode.workspace.registerTextDocumentContentProvider(
      ContentProvider.scheme,
      contentProvider
    )
  );
  context.subscriptions.push(
    vscode.window.onDidChangeTextEditorSelection((event) => {
      if (
        Date.now() - openTime < 500 &&
        event.textEditor.document.uri.scheme === ContentProvider.scheme &&
        !event.textEditor.selection.isEmpty
      ) {
        openTime = 0; // Prevent other quick selections from affecting things.
        const { anchor } = event.textEditor.selection;
        event.textEditor.selection = new vscode.Selection(anchor, anchor);
      }
    })
  );
  return contentProvider;
}

// Links:
// https://github.com/microsoft/vscode/blob/4949f13d135e3e04a8bc08fafaa8cc74a42ea261/extensions/markdown-language-features/src/commands/showPreview.ts#L17
// https://github.com/microsoft/vscode/blob/4949f13d135e3e04a8bc08fafaa8cc74a42ea261/extensions/markdown-language-features/src/preview/preview.ts#L623
// Maybe can just directly use this:
// https://code.visualstudio.com/api/extension-guides/command#programmatically-executing-a-command
// Or can first load up our translation then let the user load up the preview from there.
export async function showDocGenToSide(client: LanguageClient) {
  const doc = vscode.window.activeTextEditor?.document;
  if (doc === undefined || doc.languageId !== "markdown") {
    vscode.window.showErrorMessage(
      "Select a Markdown document first for code translation."
    );
    return;
  }
  const picks = [
    { backend: "js", label: "JavaScript (js)" },
    { backend: "py", label: "Python (py)" },
  ];
  const pick = await vscode.window.showQuickPick(picks, {
    placeHolder: "Select Backend",
  });
  if (!pick) {
    return;
  }
  client.sendNotification("temper/docGenSubscribe", {
    backend: pick.backend,
    uri: doc.uri.toString(),
  });
  const genUri = docGenKeyToUri({
    backend: pick.backend,
    uri: doc.uri.toString(),
  });
  openTime = Date.now();
  vscode.window.showTextDocument(genUri, {
    preview: false,
    viewColumn: vscode.ViewColumn.Beside,
  });
}

// See: https://github.com/microsoft/vscode-extension-samples/blob/fbdb9c5bc40f64489db00148d7f3bf6e161d9a8c/contentprovider-sample/src/provider.ts#L8
export class ContentProvider implements vscode.TextDocumentContentProvider {
  static scheme = "temper";

  private client: LanguageClient | undefined = undefined;
  private documents = new DocGenStorage();
  private onDidChangeEmitter = new vscode.EventEmitter<vscode.Uri>();
  private subscriptions: vscode.Disposable;

  constructor() {
    // This doesn't necessarily fire on visible close, but it does seem to fire
    // eventually.
    this.subscriptions = vscode.workspace.onDidCloseTextDocument((doc) => {
      const key = docGenUriToKey(doc.uri);
      if (key) {
        this.client?.sendNotification("temper/docGenUnsubscribe", key);
        // At least don't waste space on content.
        this.documents.track({ key, content: "" });
      }
    });
  }

  dispose() {
    this.onDidChangeEmitter.dispose();
    this.subscriptions.dispose();
  }

  get onDidChange() {
    return this.onDidChangeEmitter.event;
  }

  provideTextDocumentContent(uri: vscode.Uri) {
    const content = this.documents.contentForEncoded(uri);
    if (content === undefined) {
      console.log(`Invalid uri: ${uri}`);
    }
    return content ?? "";
  }

  async register(client: LanguageClient | undefined) {
    if (!client || client === this.client) {
      return;
    }
    await client.onReady();
    this.client = client;
    client.onNotification("temper/docGen", (update: DocGenDoc) => {
      this.documents.track(update);
      this.onDidChangeEmitter.fire(docGenKeyToUri(update.key));
    });
  }
}

// No vscode environment usage from here down.

export class DocGenStorage {
  private documents = new Map<UriString, Map<Backend, string>>();

  contentForEncoded(genUri: vscode.Uri) {
    const key = docGenUriToKey(genUri);
    if (key === undefined) {
      return undefined;
    }
    let content = this.documents.get(key.uri)?.get(key.backend);
    if (content === undefined) {
      content = "";
      this.track({ key, content });
    }
    return content;
  }

  track(doc: DocGenDoc) {
    const uri = doc.key.uri.toString();
    let backendDocs = this.documents.get(uri);
    if (!backendDocs) {
      backendDocs = new Map<Backend, string>();
      this.documents.set(uri, backendDocs);
    }
    backendDocs.set(doc.key.backend, doc.content);
  }
}

type Backend = string;
type UriString = string;
type DocGenDoc = { key: DocGenKey; content: string };
type DocGenKey = { backend: Backend; uri: UriString };
type UriExtra = { backend: string; sub: string; uri: UriString };

export function docGenKeyToUri(key: DocGenKey) {
  const uri = vscode.Uri.parse(key.uri);
  // Rewrite the last part of the name for helping the user interpret things.
  const parts = uri.path.match(/(.*\/)?(.*)/)!;
  const path = `${parts[1]}${key.backend} ${parts[2]}`;
  // TODO(tjp, tooling): Support other virtual temper file types?
  const query = stringifySortedShallow({ ...key, sub: "doc" } as UriExtra);
  return uri.with({ path, query, scheme: ContentProvider.scheme });
}

export function docGenUriToKey(uri: vscode.Uri): DocGenKey | undefined {
  const extra = JSON.parse(uri.query) as UriExtra;
  // Provide at least minimal validation.
  // TODO(tjp, tooling): Support other virtual temper file types?
  if (extra.sub !== "doc") {
    return undefined;
  }
  return { backend: extra.backend, uri: extra.uri };
}

export function stringifySortedShallow(obj: any) {
  return JSON.stringify(obj, Object.keys(obj).sort());
}
