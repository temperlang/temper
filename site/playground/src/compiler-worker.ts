// The compiler's worker: runs the wasm build of temper off the page's
// thread, so typing never waits on it. The page sends the compiled
// module once, then source to compile; each answer carries the request's
// id, so the page can drop stale ones. Each compile answers twice: its
// diagnostics as soon as it has settled (`analyze`), then the run and the
// backends' output (`finish`). The settled source stays in the wasm, for
// the editor's queries (hover, go to definition, completion). A trap (a
// panic, say) leaves the instance unusable, so the next request gets a
// fresh one.

import {
  type FromCompiler,
  type Query,
  type ToCompiler,
  toBackendOutput,
  toCompileResult,
  toDiagnostics,
  toStrings,
} from "./api";

/** The C-ABI exports of crates/temper-wasm, narrowed from the instance. */
interface Exports {
  memory: WebAssembly.Memory;
  alloc(len: number): number;
  analyze(ptr: number, len: number, options: number): void;
  finish(options: number, backends: number): void;
  hover(at: number): void;
  definition(at: number): void;
  completions(at: number): void;
  references(at: number): void;
  rename(at: number, ptr: number, len: number): void;
  signature_help(at: number): void;
  backends(): void;
  version(): void;
  std_library(backend: number): void;
  output_ptr(): number;
  output_len(): number;
}

const RUN = 2;
const TESTS = 4;

const post = (message: FromCompiler): void => self.postMessage(message);

let module: WebAssembly.Module | null = null;
let exports: Exports | null = null;
let backends: string[] = [];
let stdSent = false;

/**
 * What the module imports (crates/temper-os/src/host.rs): the clock, for
 * std's `temporal`, and the REPL's stream of logged lines, which the
 * compiler never uses.
 */
const HOST_IMPORTS = {
  env: {
    host_now: (): number => Date.now(),
    host_log: (): void => {},
  },
};

function instantiate(m: WebAssembly.Module): Exports {
  const instance = new WebAssembly.Instance(m, HOST_IMPORTS);
  const e = instance.exports;
  const fn = (name: string): ((...args: number[]) => number) => {
    const f = e[name];
    if (typeof f !== "function") throw new Error(`the wasm build has no ${name}`);
    return f as (...args: number[]) => number;
  };
  const memory = e.memory;
  if (!(memory instanceof WebAssembly.Memory)) throw new Error("the wasm build has no memory");
  return {
    memory,
    alloc: fn("alloc"),
    analyze: fn("analyze"),
    finish: fn("finish"),
    hover: fn("hover"),
    definition: fn("definition"),
    completions: fn("completions"),
    references: fn("references"),
    rename: fn("rename"),
    signature_help: fn("signature_help"),
    backends: fn("backends"),
    version: fn("version"),
    std_library: fn("std_library"),
    output_ptr: fn("output_ptr"),
    output_len: fn("output_len"),
  };
}

function output(e: Exports): unknown {
  const bytes = new Uint8Array(e.memory.buffer, e.output_ptr(), e.output_len());
  return JSON.parse(new TextDecoder().decode(bytes)) as unknown;
}

function current(): Exports {
  if (exports) return exports;
  if (!module) throw new Error("no wasm module yet");
  exports = instantiate(module);
  return exports;
}

function compile(id: number, source: string): void {
  const e = current();
  const bytes = new TextEncoder().encode(source);
  const start = performance.now();
  const ptr = e.alloc(bytes.length);
  new Uint8Array(e.memory.buffer, ptr, bytes.length).set(bytes);
  e.analyze(ptr, bytes.length, 0);
  const diagnostics = toDiagnostics(output(e));
  post({ type: "diagnostics", id, ms: performance.now() - start, diagnostics });
  e.finish(RUN | TESTS, (1 << backends.length) - 1);
  const ms = performance.now() - start;
  const rest = output(e);
  const result = toCompileResult(typeof rest === "object" && rest !== null ? { ...rest, diagnostics: [] } : rest);
  post({ type: "result", id, ms, result: { ...result, diagnostics } });
}

/** An editor query over the last source analyzed. */
function query(qid: number, what: Query, at: number, name: string): void {
  const e = current();
  if (what === "rename") {
    // The new name goes in the input buffer; empty only checks.
    const bytes = new TextEncoder().encode(name);
    const ptr = e.alloc(bytes.length);
    new Uint8Array(e.memory.buffer, ptr, bytes.length).set(bytes);
    e.rename(at, ptr, bytes.length);
  } else {
    e[what](at);
  }
  post({ type: "answer", qid, value: output(e) });
}

/** std as JavaScript, for running tests on the generated JavaScript. */
function sendStd(): void {
  const index = backends.indexOf("js");
  if (stdSent || index < 0) return;
  stdSent = true;
  const e = current();
  e.std_library(index);
  post({ type: "std", backend: "js", output: toBackendOutput(output(e)) });
}

self.onmessage = (event: MessageEvent<ToCompiler>) => {
  const message = event.data;
  if (message.type === "init") {
    module = message.module;
    const e = current();
    e.backends();
    backends = toStrings(output(e));
    e.version();
    const version = output(e);
    post({ type: "ready", backends, version: typeof version === "string" ? version : "" });
    return;
  }
  if (message.type === "query") {
    try {
      query(message.qid, message.what, message.at, message.name ?? "");
    } catch {
      exports = null;
      post({ type: "answer", qid: message.qid, value: null });
    }
    return;
  }
  try {
    compile(message.id, message.source);
    sendStd();
  } catch (error) {
    exports = null;
    post({ type: "failed", id: message.id, message: String(error) });
  }
};
