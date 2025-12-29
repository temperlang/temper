import * as child_process from "child_process";
import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";
import { terminate } from "vscode-languageclient/lib/node/processes";

export class UserError {
  constructor(message: string) {
    this.message = message;
  }
  message: string;
}

// Duplicate non-exported logic from vscode-languageserver.
// https://github.com/microsoft/vscode-languageserver-node/blob/c91c2f89e0a3d8aa8923355a65a2977b2b3d3b57/client/src/node/main.ts#L217
export async function checkProcessDied(
  childProcess: child_process.ChildProcess
) {
  if (!childProcess || childProcess.pid === undefined) {
    return;
  }
  await delayMillis(2000);
  try {
    // Does the second check on pid really help with anything?
    if (childProcess.pid !== undefined) {
      process.kill(childProcess.pid, 0);
      terminate(childProcess);
    }
  } catch (error) {
    // All is fine.
  }
}

export function delayMillis(millis: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, millis));
}

export function findExe(base: string): string | undefined {
  // Presume we have a full file name by default.
  let full = base;
  var exts = [""];
  if (process.platform.startsWith("win") && !path.extname(base)) {
    // Doesn't yet have an extension, so go with the ones we use/expect.
    // Bundled temper currently uses ".cmd" on windows, so prioritize that.
    // Technically, could be any in PATHEXT.
    exts = [".cmd", ".bat"];
  }
  for (let ext of exts) {
    let full = base + ext;
    if (fs.existsSync(full)) {
      return full;
    }
  }
  return undefined;
}

// TODO(tjp, tooling): Review and test this and its usage sometime.
function findExtensionRoot(extensionPath: string): string | undefined {
  for (let ancestor = extensionPath, dirname: string; ; ancestor = dirname) {
    dirname = path.dirname(ancestor);
    if (!dirname || dirname === ancestor) {
      break;
    }
    if (path.basename(ancestor) === "langserver") {
      return dirname;
    }
  }
}

export function findTemper(extensionPath: string): string {
  // Primary case is that the user has configured which temper they want.
  const temperPathConfig = vscode.workspace
    .getConfiguration("temper")
    .get<string>("executablePath");
  if (temperPathConfig) {
    const temperPathMaybe = findExe(temperPathConfig);
    if (temperPathMaybe === undefined) {
      throw new UserError(
        `No temper executable found for: ${temperPathConfig}`
      );
    }
    return temperPathMaybe;
  }
  // Not configured, so see if we have temper bundled.
  const bundled = findExe(path.join(extensionPath, "temper/temper"));
  if (bundled) {
    return bundled;
  }
  // Not bundled, so see if we're running from a source tree, such as in debug mode.
  // This section is primarily for devs who haven't yet configured.
  // It's not for general users, so don't spend too much code on it.
  const root = findExtensionRoot(extensionPath);
  if (root) {
    // Try built temper. They must have chosen to build it at some point.
    const temperPathBuilt = findExe(
      path.join(root, "cli/build/install/temper/bin/temper")
    );
    if (temperPathBuilt) {
      vscode.window.showInformationMessage(
        "Temper path not configured. Found deployed temper."
      );
      return temperPathBuilt;
    }
    // Try temper gradle script.
    const temperPathGradle = findExe(path.join(root, "scripts/temper-gradle"));
    if (temperPathGradle) {
      vscode.window.showInformationMessage(
        "Temper path not configured. Found temper gradle script."
      );
      return temperPathGradle;
    }
  }
  // Nothing found.
  // TODO(tjp, tooling): Use "which" package to search PATH?
  throw new UserError(
    "No temper path configured, and none found automatically."
  );
}
