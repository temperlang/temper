// Runs the generated JavaScript, with its tests, in a worker of its own,
// one per run, so the page can stop a run that doesn't end. Each
// generated module becomes a blob URL, with its imports rewritten to
// those of the modules it names: the library's own, std's (generated
// by the same wasm build), the bundled `@temperlang/core` runtime, and
// a stand-in for Node's test runner, `node:test`, whose `it` tests
// files (`test/*.test.js`) import to register with, and run after the
// library loads.

import * as core from "@temperlang/core";
import type { FromJsRunner, GeneratedFile, TestResult, ToJsRunner } from "./api";

const post = (message: FromJsRunner): void => self.postMessage(message);

function show(value: unknown): string {
  if (typeof value === "string") return value;
  if (value instanceof Error) return value.message;
  try {
    return JSON.stringify(value) ?? String(value);
  } catch {
    return String(value);
  }
}

// What the library logs goes to the page.
const log = (...args: unknown[]): void => post({ type: "log", line: args.map(show).join(" ") });
console.log = log;
console.info = log;
console.warn = log;
console.error = log;

/** A module of source text, as a blob URL. */
function moduleUrl(text: string): string {
  return URL.createObjectURL(new Blob([text], { type: "text/javascript" }));
}

/** The bundled runtime, as a module whose exports are its exports. */
function coreUrl(): string {
  const scope = globalThis as unknown as Record<string, unknown>;
  scope.__temperCore = core;
  const names = Object.keys(core).filter((n) => /^[A-Za-z_$][\w$]*$/.test(n));
  const lines = names.map((n) => `export const ${n} = globalThis.__temperCore.${n};`);
  return moduleUrl(lines.join("\n"));
}

/** Node's test runner's `it`, registering each test (`registered`). */
const TEST_RUNNER = "export const it = (name, body) => globalThis.__temperIt(name, body);";

/** Generated modules by path, made into blob URLs as they're imported. */
class Modules {
  private readonly texts = new Map<string, string>();
  private readonly urls = new Map<string, string>();
  private readonly loading = new Set<string>();
  private readonly packages = new Map<string, Record<string, string>>();

  constructor(files: GeneratedFile[]) {
    for (const file of files) {
      this.texts.set(file.path, file.text);
      const dir = file.path.split("/").slice(0, -1).join("/");
      if (file.path.endsWith("/package.json")) {
        const parsed = JSON.parse(file.text) as unknown;
        const exports =
          typeof parsed === "object" && parsed !== null && "exports" in parsed ? parsed.exports : null;
        if (typeof exports === "object" && exports !== null) {
          const map: Record<string, string> = {};
          for (const [k, v] of Object.entries(exports)) if (typeof v === "string") map[k] = v;
          this.packages.set(dir, map);
        }
      }
    }
    this.urls.set("@temperlang/core", coreUrl());
    this.urls.set("node:test", moduleUrl(TEST_RUNNER));
  }

  /** A path for an import from the module at `from`, if it's one of ours. */
  private resolve(specifier: string, from: string): string | null {
    if (specifier.startsWith("@temperlang/core")) return "@temperlang/core";
    if (specifier === "node:test") return "node:test";
    if (specifier.startsWith(".")) {
      const parts = from.split("/").slice(0, -1);
      for (const part of specifier.split("/")) {
        if (part === "..") parts.pop();
        else if (part !== ".") parts.push(part);
      }
      return parts.join("/");
    }
    // A package of ours, as `@temperlang/std/testing` for std's.
    const match = /^@temperlang\/([^/]+)(\/.*)?$/.exec(specifier);
    if (match) {
      const dir = match[1] ?? "";
      const target = this.packages.get(dir)?.[`.${match[2] ?? ""}`];
      if (target) return `${dir}/${target.replace(/^\.\//, "")}`;
    }
    return null;
  }

  /** A module's blob URL, after those of the modules it imports. */
  url(path: string): string {
    const known = this.urls.get(path);
    if (known) return known;
    const text = this.texts.get(path);
    if (text === undefined) throw new Error(`no module ${path}`);
    if (this.loading.has(path)) throw new Error(`an import cycle through ${path}`);
    this.loading.add(path);
    const rewritten = text
      .replace(/\/\/# sourceMappingURL=.*$/m, "")
      .replace(
        /(\bfrom\s*|\bimport\s*\(\s*|^\s*import\s*)(["'])([^"'\n]+)\2/gm,
        (all: string, before: string, quote: string, specifier: string) => {
          const target = this.resolve(specifier, path);
          return target === null ? all : `${before}${quote}${this.url(target)}${quote}`;
        },
      );
    this.loading.delete(path);
    const url = moduleUrl(rewritten);
    this.urls.set(path, url);
    return url;
  }
}

/** Tests as `node:test`'s `it` (`TEST_RUNNER`) registers them. */
const registered: { name: string; body: () => unknown }[] = [];
(globalThis as unknown as Record<string, unknown>).__temperIt = (name: string, body: () => unknown) => {
  registered.push({ name, body });
};

/** Lets pending turns (`async` blocks, timers) run. */
const settle = (): Promise<void> => new Promise((done) => setTimeout(done, 30));

async function run(message: ToJsRunner): Promise<void> {
  const modules = new Modules([...message.files, ...message.std]);
  try {
    await import(modules.url("main/index.js"));
    await settle();
  } catch (error) {
    post({ type: "failure", message: show(error) });
    post({ type: "done" });
    return;
  }
  const testFiles = message.files.filter((f) => /^main\/test\/.*\.test\.js$/.test(f.path));
  try {
    for (const file of testFiles) await import(modules.url(file.path));
  } catch (error) {
    post({ type: "failure", message: `loading tests: ${show(error)}` });
  }
  const results: TestResult[] = [];
  for (const test of registered) {
    try {
      await test.body();
      results.push({ name: test.name, failure: null });
    } catch (error) {
      results.push({ name: test.name, failure: show(error) });
    }
  }
  await settle();
  post({ type: "tests", tests: results });
  post({ type: "done" });
}

self.onmessage = (event: MessageEvent<ToJsRunner>) => {
  void run(event.data);
};
