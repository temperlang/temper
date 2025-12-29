import * as vscode from "vscode";
import {
  LanguageClient,
  LanguageClientOptions,
  StreamInfo,
} from "vscode-languageclient/node";
import appDirs from "appdirsjs";
import * as child_process from "child_process";
import * as fs from "fs";
import * as net from "net";
import * as path from "path";
import * as rfs from "rotating-file-stream";
import { initContentProvider, showDocGenToSide } from "./docgen";
import { buildHighlightExtension } from "./highlight";
import { UserError, checkProcessDied, delayMillis, findTemper } from "./util";

let client: LanguageClient | undefined;
let serverProcess: child_process.ChildProcessWithoutNullStreams | undefined;
let debugMode = false;

function initClient(context: vscode.ExtensionContext) {
  const configuration = vscode.workspace.getConfiguration("temper");

  // First see if the language server should be active.
  // This allows opt in while we work to improve the server, and you still get
  // syntax highlighting without the server.
  if (!configuration.get<boolean>("enableServer")) {
    console.log("Temper language server disabled");
    return;
  }

  // Use the console to output diagnostic information (console.log) and errors (console.error)
  // This line of code will only be executed once when your extension is activated
  console.log("Activating Temper language support");

  const workDir = path.join(appDirs({ appName: "temper" }).cache, "vscode");
  // TODO(tjp, tooling): Do we need to customize file mode for security?
  fs.mkdirSync(workDir, { recursive: true });
  // Find and assert temper immediately.
  const temperPath = findTemper(context.extensionPath);
  console.log(`Using temper path ${temperPath}`);
  // Set debug port from config, which is string type so we can default to blank.
  const debugPortText = configuration.get<string>("languageServerDebugPort");
  const debugPort = debugPortText ? Number(debugPortText) : undefined;

  // Adapted from
  // github.com/adamvoss/vscode-languageserver-java-example/blob/9beab0dfb3a512a34dfdefa60cf72f798c337c51/client/src/extension.ts#L17
  // TODO(tjp, tooling): Use Promise<MessageInfo> instead, so we can restart behind the scenes? Simpler than streams?
  function createServer(): Promise<StreamInfo> {
    let { storageUri } = context;
    let extensionLogsDir: string =
      storageUri?.fsPath || path.join(workDir, "logs");
    if (!fs.existsSync(extensionLogsDir)) {
      fs.mkdirSync(extensionLogsDir);
    }

    return new Promise((resolve, reject) => {
      let server = net
        .createServer((socket) => {
          console.log("Creating server");

          resolve({
            reader: socket,
            writer: socket,
          });

          socket.on("end", () => console.log("Disconnected server"));
        })
        .on("error", (err) => {
          console.error("Failed to create socket for server");
          reject(err);
        });
      // Start the child java process
      // grab a random port.
      server.listen(() => {
        let serverAddress: net.AddressInfo | string | null = server.address();
        if (!serverAddress || typeof serverAddress !== "object") {
          throw new TypeError("Address for local socket should be AddressInfo");
        }

        const temperArgs = ["serve", "--port", `${serverAddress.port}`];
        const options = {
          cwd: workDir,
          // Shell seems needed at least on Windows, and we do control args.
          shell: true,
        } as child_process.SpawnOptionsWithoutStdio;
        debugMode = debugPort !== undefined;
        if (debugMode) {
          options.env = {
            // eslint-disable-next-line @typescript-eslint/naming-convention
            TEMPER_OPTS: [
              "-Xdebug",
              `-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=${debugPort}`,
            ].join(" "),
          };
        }
        try {
          serverProcess = child_process.spawn(temperPath, temperArgs, options);
        } catch (err) {
          console.error("Language server spawn failed");
          console.error(err);
          throw err;
        }

        // Send raw output to a rotating log file.
        const logName = "vscode-temper-languageserver.log";
        const logPath = path.join(extensionLogsDir, logName);
        const logStream = rfs.createStream(logName, {
          maxFiles: 10,
          path: extensionLogsDir,
          // We can start up with all of "temper/" root in under 2M or so.
          // We do log a lot. Maybe can trim sometime.
          size: "5M",
        });

        serverProcess.stdout.pipe(logStream);
        serverProcess.stderr.pipe(logStream);
        serverProcess.on("error", (err) => {
          console.error("Language server error");
          console.error(err);
        });
        serverProcess.on("exit", () => {
          console.error("Language server exit");
        });

        console.log(`Storing log in ${logPath.replace(/[ \\]/g, "\\$&")}`);
      });
    });
  }

  // Options to control the language client
  let clientOptions: LanguageClientOptions = {
    // Register the server for temper documents.
    documentSelector: [
      // Include markdown so we can track changes for like doc translation.
      // TODO(tjp, tooling): Does this interfere with default markdown logic?
      // See also: https://github.com/microsoft/vscode/blob/c6b42be3c6194243517a1eb0aada3c94ed45cb08/extensions/markdown-language-features/src/extension.ts#L61
      { scheme: "file", language: "markdown" },
      { scheme: "file", language: "temper" },
      { scheme: "file", language: "tempermd" },
    ],
    synchronize: {
      // Notify the server about file changes to '.clientrc files contain in the workspace
      fileEvents: vscode.workspace.createFileSystemWatcher("**/.clientrc"),
    },
    // TODO: to get cancellation working do I need to do something with connectionOptions?
    /*
        connectionOptions: {
            cancellationStrategy: {
                receiver: {}
                sender: {}
            }
        }
        */
  };

  // Create the language client and start the client.
  client = new LanguageClient(
    "temper-language-client",
    "Temper Language Client",
    createServer,
    clientOptions
  );
  // Start the client. This will also launch the server
  let lcDisposable = client.start();
  // Push the disposable to the context's subscriptions so that the
  // client can be deactivated on extension deactivation
  context.subscriptions.push(lcDisposable);
}

function tryInitClient(context: vscode.ExtensionContext) {
  try {
    initClient(context);
  } catch (error) {
    if (error instanceof UserError) {
      console.log(error.message);
      vscode.window.showInformationMessage(error.message);
    } else {
      console.error("Failed to start client", error);
    }
  }
}

export async function activate(context: vscode.ExtensionContext) {
  tryInitClient(context);
  const contentProvider = initContentProvider(context);
  contentProvider.register(client);

  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration((event) => {
      if (event.affectsConfiguration("temper.executablePath")) {
        const path = vscode.workspace
          .getConfiguration("temper")
          .get<string>("executablePath");
        if (path) {
          // TODO(tjp, tooling): Should we try to start the language client if not already running?
          console.log("Temper executable path updated:", path);
        }
      }
    })
  );

  // Register commands defined in the package.json file.
  // See also the dispose example from MS here: https://github.com/microsoft/vscode/blob/6ea335e334b90a9aca753d558dec1d14741eff1e/extensions/markdown-language-features/src/commandManager.ts#L17
  const docGenCommand = vscode.commands.registerCommand(
    "temper.showDocGenToSide",
    () => client && showDocGenToSide(client)
  );
  const restartCommand = vscode.commands.registerCommand(
    "temper.restartLanguageServer",
    async () => {
      if (client) {
        // See: setStatusBarMessage(text: string, hideWhenDone: Thenable<any>): Disposable
        // const message = await vscode.window.showInformationMessage('Now I need to restart the language server.');
        // Apparently can stop and start repeatedly: https://github.com/microsoft/vscode-languageserver-node/blob/0b15882b7cd9bee17776da77d16650cb817e150f/client-node-tests/src/integration.test.ts#L1470
        // See also discussion of restart: https://github.com/microsoft/vscode/issues/76405#issuecomment-951018232
        // And the presumably related restart method: https://github.com/microsoft/vscode-languageserver-node/blame/b00f418ad641277f2d7e5277f47fdea2552f0537/client/src/node/main.ts#L189
        await stopClient();
        await client.start();
      } else {
        // We apparently failed to launch earlier, perhaps due to missing or bad temper config. Try again.
        tryInitClient(context);
        contentProvider.register(client);
      }
    }
  );
  context.subscriptions.push(docGenCommand, restartCommand);

  const previewExtension = await buildHighlightExtension();
  console.log("Finished activation");
  return previewExtension;
}

export function deactivate(): Thenable<void> | undefined {
  console.log("Deactivating Temper language support");
  if (!client) {
    return undefined;
  }
  return client.stop();
}

async function stopClient() {
  await client!.stop();
  // Clean things that can cause trouble if left dangling.
  if (serverProcess) {
    // Helps keep things clean. Maybe can also help with debug port?
    await checkProcessDied(serverProcess);
    serverProcess = undefined;
  }
  if (debugMode) {
    // Hope to free up debug port.
    // Also in vscode-language server for processes managed there.
    await delayMillis(1000);
  }
}
