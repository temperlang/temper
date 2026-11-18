// The playground page: the editor, the output tabs, and the workers.
//
// - Typing schedules a compile (debounced); the compiler's worker gets
//   one request at a time, and only the newest pending one waits. A
//   request that runs long while a newer one waits gets its worker
//   replaced, so a runaway loop can't hold up typing.
// - Each result fills every backend's tab, the editor's diagnostics,
//   and the Run tab's interpreter half; the generated JavaScript then
//   runs in a worker of its own for the other half.
// - Diagnostics come first, as soon as the source settles, before the
//   run and the backends. The worker keeps the settled source for the
//   editor's queries: hover, go to definition (F12, or Ctrl or Cmd and
//   click; into std, shown read-only on the right), completion, find
//   references (Shift+F12), rename (F2), and signature help in a call's
//   arguments, which wait for the current text to settle.
// - The REPL tab (repl.ts) takes the page's place of the editor and
//   its tabs, with the editor's code as its input 0.

import {
  type Completion,
  type CompletionContext,
  type CompletionResult,
  autocompletion,
  closeBrackets,
  closeBracketsKeymap,
} from "@codemirror/autocomplete";
import { defaultKeymap, history, historyKeymap, indentWithTab } from "@codemirror/commands";
import {
  HighlightStyle,
  type LanguageSupport,
  StreamLanguage,
  bracketMatching,
  indentOnInput,
  syntaxHighlighting,
} from "@codemirror/language";
import { type Diagnostic as LintDiagnostic, lintGutter, setDiagnostics } from "@codemirror/lint";
import { highlightSelectionMatches, searchKeymap } from "@codemirror/search";
import { Compartment, EditorState, type Extension, StateEffect, StateField } from "@codemirror/state";
import {
  Decoration,
  type DecorationSet,
  EditorView,
  type Tooltip,
  drawSelection,
  highlightActiveLine,
  highlightActiveLineGutter,
  hoverTooltip,
  keymap,
  lineNumbers,
  showTooltip,
} from "@codemirror/view";
import { tags } from "@lezer/highlight";
import { cpp, csharp, java } from "@codemirror/legacy-modes/mode/clike";
import { cmake } from "@codemirror/legacy-modes/mode/cmake";
import { javascript, json } from "@codemirror/legacy-modes/mode/javascript";
import { lua } from "@codemirror/legacy-modes/mode/lua";
import { python } from "@codemirror/legacy-modes/mode/python";
import { rust } from "@codemirror/legacy-modes/mode/rust";
import { toml } from "@codemirror/legacy-modes/mode/toml";
import { xml } from "@codemirror/legacy-modes/mode/xml";

import type {
  BackendOutput,
  CompileResult,
  CompletionItem,
  Diagnostic,
  FromCompiler,
  FromJsRunner,
  GeneratedFile,
  HoverInfo,
  Query,
  RunOutput,
  SignatureInfo,
  TestResult,
  ToCompiler,
  ToJsRunner,
} from "./api";
import { toCompletions, toDefinition, toHover, toRename, toSignature, toSpans } from "./api";
import { examples } from "./examples";
import { createRepl } from "./repl";
import { decode, encode } from "./share";
import { temper } from "./temper-language";

const DEBOUNCE_MS = 200;
/** How long a query waits for the current text to settle. */
const QUERY_WAIT_MS = 3000;
/** How long a compile may run while a newer request waits. */
const STALE_MS = 1500;
/** How long the generated JavaScript may run. */
const JS_RUN_MS = 5000;

/** Backends' names for people, and the files to show first, in tab order. */
const BACKENDS: Record<string, { label: string; main: RegExp }> = {
  cpp: { label: "C++", main: /\/init\.cpp$/ },
  csharp: { label: "C#", main: /Global\.cs$/ },
  java: { label: "Java", main: /Global\.java$/ },
  js: { label: "JavaScript", main: /\.internal\.js$/ },
  lua: { label: "Lua", main: /\/init\.lua$/ },
  py: { label: "Python", main: /^main\/main\/main\.py$/ },
  rust: { label: "Rust", main: /\/mod\.rs$/ },
};
const DEFAULT_TAB = "py";
/** What `selected` is while a std file is shown, which isn't a tab. */
const STD_VIEW = "std";

const $ = <T extends HTMLElement>(selector: string): T => {
  const element = document.querySelector<T>(selector);
  if (!element) throw new Error(`no ${selector}`);
  return element;
};

// The theme is the header's (site/shared/header.js), for the docs too.

// Highlighting and editor looks, from CSS variables, so one theme
// serves light and dark.

const highlight = HighlightStyle.define([
  { tag: [tags.keyword, tags.controlKeyword, tags.definitionKeyword, tags.moduleKeyword], color: "var(--tok-keyword)" },
  { tag: [tags.string, tags.special(tags.string), tags.regexp], color: "var(--tok-string)" },
  { tag: tags.escape, color: "var(--tok-meta)" },
  { tag: [tags.comment, tags.lineComment, tags.blockComment, tags.docComment], color: "var(--tok-comment)", fontStyle: "italic" },
  { tag: [tags.number, tags.integer, tags.float], color: "var(--tok-number)" },
  { tag: [tags.typeName, tags.className, tags.namespace], color: "var(--tok-type)" },
  { tag: [tags.function(tags.variableName), tags.function(tags.propertyName)], color: "var(--tok-function)" },
  { tag: [tags.definition(tags.variableName), tags.definition(tags.propertyName)], color: "var(--tok-function)" },
  { tag: [tags.atom, tags.bool, tags.null, tags.self], color: "var(--tok-atom)" },
  { tag: [tags.meta, tags.annotation, tags.processingInstruction], color: "var(--tok-meta)" },
  { tag: [tags.tagName, tags.attributeName], color: "var(--tok-keyword)" },
  { tag: tags.invalid, color: "var(--error)" },
]);

const looks = EditorView.theme({
  "&": { height: "100%", backgroundColor: "var(--surface)", color: "var(--text)" },
  ".cm-scroller": { fontFamily: "var(--mono)", lineHeight: "1.55" },
  ".cm-content": { caretColor: "var(--accent)" },
  ".cm-cursor, .cm-dropCursor": { borderLeftColor: "var(--accent)", borderLeftWidth: "2px" },
  ".cm-gutters": { backgroundColor: "var(--surface)", color: "var(--muted)", border: "none" },
  ".cm-activeLine": { backgroundColor: "var(--active-line)" },
  ".cm-activeLineGutter": { backgroundColor: "var(--active-line)", color: "var(--text)" },
  "&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection": {
    backgroundColor: "var(--selection) !important",
  },
  ".cm-matchingBracket": { backgroundColor: "var(--match)", outline: "none" },
  ".cm-tooltip": { backgroundColor: "var(--panel)", color: "var(--text)", border: "1px solid var(--border)" },
  ".cm-panels": { backgroundColor: "var(--panel)", color: "var(--text)" },
  ".cm-tooltip-hover": { padding: "4px 8px", maxWidth: "36em" },
  ".cm-hover-type": { fontFamily: "var(--mono)", whiteSpace: "pre-wrap" },
  ".cm-hover-kind": { color: "var(--muted)" },
  ".cm-hover-doc": { marginTop: "4px", whiteSpace: "pre-wrap" },
  ".cm-tooltip-autocomplete ul li[aria-selected]": { backgroundColor: "var(--selection)", color: "var(--text)" },
  ".cm-reference": { backgroundColor: "var(--match)", outline: "1px solid var(--accent)" },
  ".cm-signature": { padding: "4px 8px", fontFamily: "var(--mono)", maxWidth: "40em", whiteSpace: "pre-wrap" },
  ".cm-signature-active": { fontWeight: "bold", textDecoration: "underline" },
  ".cm-signature-doc": { fontFamily: "var(--sans, inherit)", marginTop: "4px", color: "var(--muted)" },
});

// References, marked in the editor until the text changes or Escape.

const setReferences = StateEffect.define<{ from: number; to: number }[]>();
const referenceMark = Decoration.mark({ class: "cm-reference" });
const references = StateField.define<DecorationSet>({
  create: () => Decoration.none,
  update(marks, tr) {
    for (const effect of tr.effects) {
      if (effect.is(setReferences)) {
        const ranges = effect.value
          .filter((s) => s.to <= tr.state.doc.length && s.from < s.to)
          .sort((a, b) => a.from - b.from)
          .map((s) => referenceMark.range(s.from, s.to));
        return Decoration.set(ranges);
      }
    }
    return tr.docChanged ? Decoration.none : marks;
  },
  provide: (field) => EditorView.decorations.from(field),
});

// Signature help: a tooltip over a call's arguments while typing them.

const setSignature = StateEffect.define<Tooltip | null>();
const signature = StateField.define<Tooltip | null>({
  create: () => null,
  update(tooltip, tr) {
    for (const effect of tr.effects) if (effect.is(setSignature)) return effect.value;
    // Kept where it was until the next answer, which may be a while.
    return tooltip && tr.docChanged ? { ...tooltip, pos: tr.changes.mapPos(tooltip.pos) } : tooltip;
  },
  provide: (field) => showTooltip.from(field),
});

// The editor.

const compileSoon = debounce(requestCompile, DEBOUNCE_MS);
const shareSoon = debounce(() => void updateHash(), 1000);

const editor = new EditorView({
  parent: $("#editor"),
  state: EditorState.create({
    doc: "",
    extensions: [
      lineNumbers(),
      highlightActiveLineGutter(),
      history(),
      drawSelection(),
      indentOnInput(),
      bracketMatching(),
      closeBrackets(),
      autocompletion({ override: [complete] }),
      hoverTooltip(hover, { hoverTime: 300 }),
      references,
      signature,
      keymap.of([
        { key: "F12", run: (view) => (void goToDefinition(view.state.selection.main.head), true) },
        { key: "Shift-F12", run: (view) => (void findReferences(view.state.selection.main.head), true) },
        { key: "F2", run: (view) => (void renameAt(view.state.selection.main.head), true) },
        {
          key: "Escape",
          run: (view) => {
            const shown = view.state.field(references).size > 0 || view.state.field(signature) !== null;
            if (shown) view.dispatch({ effects: [setReferences.of([]), setSignature.of(null)] });
            return shown;
          },
        },
      ]),
      EditorView.domEventHandlers({
        mousedown: (event, view) => {
          if (!(event.ctrlKey || event.metaKey) || event.button !== 0) return false;
          const at = view.posAtCoords({ x: event.clientX, y: event.clientY });
          if (at === null) return false;
          event.preventDefault();
          void goToDefinition(at);
          return true;
        },
      }),
      highlightActiveLine(),
      highlightSelectionMatches(),
      lintGutter(),
      keymap.of([...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap, indentWithTab]),
      temper,
      syntaxHighlighting(highlight),
      looks,
      EditorView.lineWrapping,
      EditorView.contentAttributes.of({ "aria-label": "Temper source" }),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          document.body.classList.add("pending");
          compileSoon();
          shareSoon();
        }
        // Signature help opens on `(` or `,`, and follows the cursor while open.
        const typed = update.transactions.some((tr) => tr.isUserEvent("input.type"));
        const inserted = typed ? update.state.sliceDoc(update.state.selection.main.head - 1, update.state.selection.main.head) : "";
        if (inserted === "(" || inserted === "," || (update.startState.field(signature) && (update.docChanged || update.selectionSet))) {
          signatureSoon();
        }
      }),
    ],
  }),
});

function source(): string {
  return editor.state.doc.toString();
}

function setSource(text: string): void {
  editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: text } });
}

// Output: one read-only viewer, its language swapped per file.

const outputLanguage = new Compartment();
const viewer = new EditorView({
  parent: $("#code"),
  state: EditorState.create({
    extensions: [
      lineNumbers(),
      EditorState.readOnly.of(true),
      EditorView.editable.of(false),
      EditorView.contentAttributes.of({ "aria-label": "Generated code", tabindex: "0" }),
      // Shows go to definition's target in a std file, unfocused.
      drawSelection(),
      outputLanguage.of([]),
      syntaxHighlighting(highlight),
      highlightSelectionMatches(),
      keymap.of(searchKeymap),
      looks,
      EditorView.lineWrapping,
    ],
  }),
});

const MODES: [RegExp, Parameters<typeof StreamLanguage.define>[0]][] = [
  [/\.py$/, python],
  [/\.rs$/, rust],
  [/\.(js|mjs)$/, javascript],
  [/\.json$/, json],
  [/\.java$/, java],
  [/\.cs$/, csharp],
  [/\.(cpp|hpp|h|cc)$/, cpp],
  [/\.(lua|rockspec)$/, lua],
  [/\.toml$/, toml],
  [/\.(xml|csproj|props)$/, xml],
  [/CMakeLists\.txt$/, cmake],
];
const languages = new Map<string, LanguageSupport | Extension>();

function languageFor(path: string): Extension {
  const found = MODES.find(([pattern]) => pattern.test(path));
  if (!found) return [];
  const key = found[0].source;
  let language = languages.get(key);
  if (!language) {
    language = StreamLanguage.define(found[1]);
    languages.set(key, language);
  }
  return language;
}

// Tabs: the backends the wasm build has, by name, then Run and Errors.

const tabs = $("#tabs");
const panes = { code: $("#code-pane"), run: $("#run-pane"), errors: $("#errors-pane") };
const filePicker = $<HTMLSelectElement>("#file");
const backendError = $("#backend-error");
let backends: string[] = [];
let selected = DEFAULT_TAB;
const chosenFile = new Map<string, string>();
let latest: CompileResult | null = null;

function label(backend: string): string {
  return BACKENDS[backend]?.label ?? backend;
}

function buildTabs(): void {
  tabs.replaceChildren();
  // Known backends in the order above (alphabetical, as people say
  // them), then any others the build has.
  const order = Object.keys(BACKENDS);
  const rank = (name: string): number => (order.includes(name) ? order.indexOf(name) : order.length);
  const names = [...backends].sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
  for (const name of [...names, "run", "errors"]) {
    const tab = document.createElement("button");
    tab.className = "tab";
    tab.role = "tab";
    tab.dataset.tab = name;
    tab.id = `tab-${name}`;
    tab.append(name === "run" ? "Run" : name === "errors" ? "Errors" : label(name));
    if (name === "run" || name === "errors") {
      const badge = document.createElement("span");
      badge.className = "badge";
      badge.hidden = true;
      tab.append(badge);
    }
    tab.addEventListener("click", () => select(name));
    tabs.append(tab);
  }
  tabs.addEventListener("keydown", (event) => {
    if (event.key !== "ArrowRight" && event.key !== "ArrowLeft") return;
    const all = [...tabs.querySelectorAll<HTMLButtonElement>(".tab")];
    const at = all.findIndex((t) => t.dataset.tab === selected);
    const next = all[(at + (event.key === "ArrowRight" ? 1 : all.length - 1)) % all.length];
    if (next?.dataset.tab) {
      select(next.dataset.tab);
      next.focus();
    }
  });
  if (!backends.includes(selected)) selected = backends[0] ?? "run";
  select(selected);
}

function select(name: string): void {
  selected = name;
  for (const tab of tabs.querySelectorAll<HTMLButtonElement>(".tab")) {
    const on = tab.dataset.tab === name;
    tab.setAttribute("aria-selected", String(on));
    tab.tabIndex = on ? 0 : -1;
  }
  panes.code.hidden = name === "run" || name === "errors";
  panes.run.hidden = name !== "run";
  panes.errors.hidden = name !== "errors";
  if (!panes.code.hidden) showBackend();
}

function badge(tab: string, text: string, kind: "good" | "bad" | "plain"): void {
  const element = document.querySelector<HTMLElement>(`#tab-${tab} .badge`);
  if (!element) return;
  element.hidden = text === "";
  element.textContent = text;
  element.dataset.kind = kind;
}

/** The files worth showing: not source maps. */
function shown(output: BackendOutput | undefined): GeneratedFile[] {
  return output && "files" in output ? output.files.filter((f) => !f.path.endsWith(".map")) : [];
}

function showBackend(): void {
  const output = latest?.backends[selected];
  const files = shown(output);
  backendError.hidden = !(output && "error" in output);
  backendError.textContent = output && "error" in output ? output.error : "";
  const wanted = chosenFile.get(selected);
  const main = BACKENDS[selected]?.main;
  const file =
    files.find((f) => f.path === wanted) ?? files.find((f) => main?.test(f.path)) ?? files[0];
  filePicker.replaceChildren(
    ...files.map((f) => {
      const option = document.createElement("option");
      option.value = f.path;
      option.textContent = f.path;
      option.selected = f === file;
      return option;
    }),
  );
  filePicker.disabled = files.length < 2;
  filePicker.hidden = files.length === 0;
  const text = file?.text ?? "";
  viewer.dispatch({
    changes: { from: 0, to: viewer.state.doc.length, insert: text },
    effects: outputLanguage.reconfigure(file ? languageFor(file.path) : []),
  });
}

filePicker.addEventListener("change", () => {
  chosenFile.set(selected, filePicker.value);
  showBackend();
});

// Errors: in the editor, and listed in their tab.

function showDiagnostics(diagnostics: Diagnostic[]): void {
  const length = editor.state.doc.length;
  const inMain = diagnostics.filter((d) => d.from !== undefined && d.to !== undefined);
  const lint: LintDiagnostic[] = inMain.map((d) => {
    const from = Math.min(d.from ?? 0, length);
    return {
      from,
      to: Math.max(from, Math.min(d.to ?? from, length)),
      severity: d.level === "warn" ? "warning" : d.level,
      message: d.message,
    };
  });
  editor.dispatch(setDiagnostics(editor.state, lint));
  const list = $("#errors");
  const sorted = [...diagnostics].sort((a, b) => (a.from ?? 1e9) - (b.from ?? 1e9));
  list.replaceChildren(
    ...sorted.map((d) => {
      const item = document.createElement("li");
      item.className = `diagnostic ${d.level}`;
      const where = document.createElement("button");
      where.className = "where";
      where.textContent = d.line ? `${d.line}:${d.column ?? 1}` : d.file;
      where.disabled = d.from === undefined;
      where.addEventListener("click", () => {
        const at = Math.min(d.from ?? 0, editor.state.doc.length);
        editor.dispatch({ selection: { anchor: at }, scrollIntoView: true });
        editor.focus();
      });
      const message = document.createElement("span");
      message.textContent = `${d.level}: ${d.message}`;
      item.append(where, message);
      return item;
    }),
  );
  $("#no-errors").hidden = diagnostics.length > 0;
  const errors = diagnostics.filter((d) => d.level === "error").length;
  badge("errors", diagnostics.length ? String(diagnostics.length) : "", errors ? "bad" : "plain");
}

// Run: the interpreter's results beside the generated JavaScript's.

function renderRun(target: HTMLElement, run: RunOutput | null, note = ""): void {
  target.replaceChildren();
  if (note) {
    const p = document.createElement("p");
    p.className = "note";
    p.textContent = note;
    target.append(p);
  }
  if (!run) return;
  if (run.tests.length || run.defined) {
    const list = document.createElement("ul");
    list.className = "tests";
    for (const test of run.tests) {
      const item = document.createElement("li");
      item.className = test.failure === null ? "pass" : "fail";
      const mark = document.createElement("span");
      mark.className = "mark";
      mark.textContent = test.failure === null ? "✓" : "✗";
      mark.setAttribute("aria-label", test.failure === null ? "passed" : "failed");
      const name = document.createElement("span");
      name.textContent = test.name;
      item.append(mark, name);
      if (test.failure !== null) {
        const why = document.createElement("div");
        why.className = "why";
        why.textContent = test.failure;
        item.append(why);
      }
      list.append(item);
    }
    const summary = document.createElement("p");
    summary.className = `summary ${allPassed(run) ? "pass" : "fail"}`;
    summary.textContent = run.summary || summarize(run.tests, run.defined);
    target.append(summary, list);
  }
  if (run.output.length || run.failure) {
    const heading = document.createElement("h3");
    heading.textContent = "Output";
    const log = document.createElement("pre");
    log.className = "log";
    log.textContent = run.output.join("\n");
    target.append(heading, log);
  }
  if (run.failure) {
    const failure = document.createElement("pre");
    failure.className = "failure";
    failure.textContent = run.failure;
    target.append(failure);
  }
}

function allPassed(run: RunOutput): boolean {
  return run.tests.every((t) => t.failure === null) && run.tests.length === run.defined && !run.failure;
}

/** `temper test`'s summary, for the JavaScript run's tests. */
function summarize(tests: TestResult[], defined: number): string {
  const passed = tests.filter((t) => t.failure === null).length;
  const notRun = Math.max(0, defined - tests.length);
  return `Tests passed: ${passed} of ${defined}${notRun ? ` (${notRun} not run)` : ""}`;
}

let interpreterRun: RunOutput | null = null;
let jsRun: RunOutput | null = null;
let jsDone = false;

function compareRuns(): void {
  const same = $("#same");
  if (!interpreterRun || !jsRun || !jsDone) {
    same.hidden = true;
    return;
  }
  const key = (r: RunOutput): string =>
    JSON.stringify([r.output, r.tests.map((t) => [t.name, t.failure === null])]);
  same.hidden = false;
  const agree = key(interpreterRun) === key(jsRun);
  same.textContent = agree ? "Both runs agree" : "The runs differ";
  same.dataset.kind = agree ? "good" : "bad";
}

function showInterpreter(run: RunOutput | null): void {
  interpreterRun = run;
  renderRun($("#interp"), run);
  if (run && (run.tests.length || run.defined)) {
    const passed = run.tests.filter((t) => t.failure === null).length;
    badge("run", `${passed}/${run.defined}`, allPassed(run) ? "good" : "bad");
  } else {
    badge("run", run?.failure ? "!" : "", "bad");
  }
}

// The generated JavaScript's run.

let jsStd: GeneratedFile[] | null = null;
let jsStdError = "";
let jsWorker: Worker | null = null;
let jsTimer = 0;
let pendingJs: GeneratedFile[] | null = null;

function runJs(output: BackendOutput | undefined): void {
  jsWorker?.terminate();
  jsWorker = null;
  clearTimeout(jsTimer);
  jsDone = false;
  compareRuns();
  if (!output || "error" in output) {
    jsRun = null;
    renderRun($("#js"), null, output ? "No JavaScript to run: it didn't generate." : "");
    return;
  }
  if (!jsStd) {
    pendingJs = output.files;
    renderRun($("#js"), null, jsStdError || "Loading std…");
    return;
  }
  const run: RunOutput = { output: [], failure: null, tests: [], defined: interpreterRun?.defined ?? 0, summary: "" };
  jsRun = run;
  renderRun($("#js"), null, "Running…");
  const worker = new Worker(`js-worker.js?v=${BUILD_VERSION}`, { type: "module", name: "javascript" });
  jsWorker = worker;
  const finish = (): void => {
    jsDone = true;
    worker.terminate();
    if (jsWorker === worker) jsWorker = null;
    clearTimeout(jsTimer);
    renderRun($("#js"), run);
    compareRuns();
  };
  worker.onmessage = (event: MessageEvent<FromJsRunner>) => {
    if (jsRun !== run) return;
    const message = event.data;
    if (message.type === "log") run.output.push(message.line);
    else if (message.type === "failure") run.failure = message.message;
    else if (message.type === "tests") run.tests = message.tests;
    else finish();
    // No summary until the tests have run.
    if (message.type !== "done") renderRun($("#js"), { ...run, defined: run.tests.length ? run.defined : 0 });
  };
  worker.onerror = (event) => {
    run.failure = event.message || "the JavaScript run failed";
    finish();
  };
  jsTimer = window.setTimeout(() => {
    run.failure = `Stopped after ${JS_RUN_MS / 1000} s`;
    finish();
  }, JS_RUN_MS);
  const message: ToJsRunner = { files: output.files, std: jsStd };
  worker.postMessage(message);
}

// The compiler's worker.

let wasmModule: WebAssembly.Module | null = null;
let worker: Worker | null = null;
let busy: { id: number; since: number; source: string } | null = null;
let waiting: { id: number; source: string } | null = null;
let nextId = 1;
let shownId = 0;
let staleTimer = 0;

function startWorker(module: WebAssembly.Module): void {
  worker?.terminate();
  busy = null;
  dropQueries();
  const w = new Worker(`compiler-worker.js?v=${BUILD_VERSION}`, { type: "module", name: "compiler" });
  worker = w;
  w.onmessage = (event: MessageEvent<FromCompiler>) => onCompiler(event.data);
  w.onerror = (event) => status(`The compiler stopped: ${event.message}`, true);
  const init: ToCompiler = { type: "init", module };
  w.postMessage(init);
}

function onCompiler(message: FromCompiler): void {
  switch (message.type) {
    case "ready":
      showVersion(message.version);
      if (backends.join() !== message.backends.join()) {
        backends = message.backends;
        buildTabs();
      }
      sendNext();
      return;
    case "std":
      if ("files" in message.output) jsStd = message.output.files;
      else jsStdError = `std didn't generate: ${message.output.error}`;
      if (pendingJs) {
        const files = pendingJs;
        pendingJs = null;
        runJs({ files });
      }
      return;
    case "diagnostics":
      if (busy?.id === message.id && message.id > shownId) {
        onSettled(busy.source);
        showDiagnostics(message.diagnostics);
        status(`Checked in ${message.ms < 10 ? message.ms.toFixed(1) : Math.round(message.ms)} ms…`, false);
      }
      return;
    case "answer": {
      const resolve = queries.get(message.qid);
      queries.delete(message.qid);
      resolve?.(message.value);
      return;
    }
    case "result":
      busy = null;
      if (message.id > shownId) {
        shownId = message.id;
        show(message.result, message.ms);
      }
      sendNext();
      return;
    case "failed":
      busy = null;
      // The worker drops its instance, and with it what it settled.
      dropQueries();
      if (message.id > shownId) {
        shownId = message.id;
        status(`The compiler failed: ${message.message}`, true);
      }
      sendNext();
      return;
  }
}

function requestCompile(): void {
  waiting = { id: nextId++, source: source() };
  sendNext();
}

function sendNext(): void {
  if (!worker || !waiting) return;
  if (busy) {
    // Replace a worker that's still on an older request after a while.
    clearTimeout(staleTimer);
    const left = STALE_MS - (performance.now() - busy.since);
    staleTimer = window.setTimeout(() => {
      if (busy && waiting && wasmModule) {
        status("Restarting the compiler after a slow run…", false);
        startWorker(wasmModule);
      }
    }, Math.max(0, left));
    return;
  }
  const request = waiting;
  waiting = null;
  busy = { id: request.id, since: performance.now(), source: request.source };
  const message: ToCompiler = { type: "compile", id: request.id, source: request.source };
  worker.postMessage(message);
}

function show(result: CompileResult, ms: number): void {
  latest = result;
  if (!waiting) document.body.classList.remove("pending");
  // A std file shown stays until a tab is picked.
  if (!panes.code.hidden && selected !== STD_VIEW) showBackend();
  showDiagnostics(result.diagnostics);
  showInterpreter(result.run);
  runJs(result.backends.js);
  status(`Compiled in ${ms < 10 ? ms.toFixed(1) : Math.round(ms)} ms`, false);
}

/** The compiler's version, as `temper --version` says it, in About. */
function showVersion(version: string): void {
  const list = document.querySelector("#about ul");
  if (!version || !list) return;
  let item = document.getElementById("about-version");
  if (!item) {
    item = document.createElement("li");
    item.id = "about-version";
    list.prepend(item);
  }
  item.textContent = `Temper ${version}`;
}

function status(text: string, bad: boolean): void {
  const element = $("#compile-status");
  element.textContent = text;
  element.dataset.kind = bad ? "bad" : "plain";
}

// The editor's queries: hover, go to definition, and completion, over
// the source the worker last settled.

let analyzed: string | null = null;
let nextQuery = 1;
const queries = new Map<number, (value: unknown) => void>();
const settledWaiters: (() => void)[] = [];

/** Waits, a while at most, until the worker has settled the current text. */
async function settledNow(): Promise<boolean> {
  if (analyzed === source()) return true;
  compileSoon();
  await new Promise<void>((resolve) => {
    settledWaiters.push(resolve);
    setTimeout(resolve, QUERY_WAIT_MS);
  });
  return analyzed === source();
}

function onSettled(text: string): void {
  analyzed = text;
  for (const resolve of settledWaiters.splice(0)) resolve();
}

async function ask(what: Query, at: number, name?: string): Promise<unknown> {
  if (!worker || !(await settledNow()) || !worker) return null;
  const qid = nextQuery++;
  const message: ToCompiler = { type: "query", qid, what, at, name };
  const answer = new Promise<unknown>((resolve) => queries.set(qid, resolve));
  worker.postMessage(message);
  return answer;
}

/** Drops the queries a replaced worker won't answer. */
function dropQueries(): void {
  for (const resolve of queries.values()) resolve(null);
  queries.clear();
  analyzed = null;
}

async function hover(_view: EditorView, at: number, side: -1 | 1) {
  // Between characters, the one the pointer's over.
  const value = toHover(await ask("hover", side < 0 && at > 0 ? at - 1 : at));
  if (!value || !value.type) return null;
  return { pos: value.from, end: value.to, above: true, create: () => ({ dom: hoverDom(value) }) };
}

/** A hover's tooltip: the kind, the name and type or signature, and docs. */
function hoverDom(value: HoverInfo): HTMLElement {
  const dom = document.createElement("div");
  dom.className = "cm-hover";
  const type = document.createElement("div");
  type.className = "cm-hover-type";
  if (value.kind) {
    const kind = document.createElement("span");
    kind.className = "cm-hover-kind";
    kind.textContent = `${value.kind} `;
    type.append(kind);
  }
  // A signature or type starts with its name, `twice(n: Int32)` or
  // `List<T>`; `Int32`, `Int`'s, doesn't.
  const rest = value.name !== null && value.type?.startsWith(value.name) ? value.type.slice(value.name.length) : null;
  const isSignature = rest !== null && (rest === "" || rest.startsWith("(") || rest.startsWith("<"));
  type.append(value.name && !isSignature ? `${value.name}: ${value.type}` : (value.type ?? ""));
  dom.append(type);
  if (value.doc) {
    const doc = document.createElement("div");
    doc.className = "cm-hover-doc";
    doc.textContent = value.doc;
    dom.append(doc);
  }
  return dom;
}

async function goToDefinition(at: number): Promise<void> {
  const target = toDefinition(await ask("definition", at));
  if (!target) return;
  if ("file" in target) {
    showStd(target.file, target.text, target);
    return;
  }
  if (target.to > editor.state.doc.length) return;
  editor.dispatch({ selection: { anchor: target.from, head: target.to }, scrollIntoView: true });
  editor.focus();
}

/** Shows a std or core file read-only in the output viewer, at a declaration. */
function showStd(file: string, text: string, span: { from: number; to: number }): void {
  selected = STD_VIEW;
  for (const tab of tabs.querySelectorAll<HTMLButtonElement>(".tab")) tab.setAttribute("aria-selected", "false");
  panes.code.hidden = false;
  panes.run.hidden = true;
  panes.errors.hidden = true;
  backendError.hidden = true;
  const option = document.createElement("option");
  option.textContent = `${file} (read-only)`;
  filePicker.replaceChildren(option);
  filePicker.disabled = true;
  filePicker.hidden = false;
  viewer.dispatch({
    changes: { from: 0, to: viewer.state.doc.length, insert: text },
    effects: outputLanguage.reconfigure(temper),
  });
  viewer.dispatch({
    selection: { anchor: span.from, head: span.to },
    effects: EditorView.scrollIntoView(span.from, { y: "center" }),
  });
}

async function findReferences(at: number): Promise<void> {
  const spans = toSpans(await ask("references", at));
  editor.dispatch({ effects: setReferences.of(spans) });
  status(spans.length === 1 ? "1 reference" : `${spans.length} references`, false);
}

async function renameAt(at: number): Promise<void> {
  const check = toRename(await ask("rename", at, ""));
  if (!check) return;
  if ("error" in check) {
    status(check.error, true);
    return;
  }
  if (!("name" in check)) return;
  const name = window.prompt(`Rename ${check.name} to`, check.name);
  if (!name || name === check.name) return;
  const answer = toRename(await ask("rename", at, name));
  if (!answer) return;
  if ("error" in answer) {
    status(answer.error, true);
    return;
  }
  if (!("edits" in answer)) return;
  editor.dispatch({ changes: answer.edits.map((e) => ({ from: e.from, to: e.to, insert: e.text })), userEvent: "rename" });
  status(`Renamed ${answer.edits.length} ${answer.edits.length === 1 ? "place" : "places"}`, false);
}

const signatureSoon = debounce(() => void updateSignature(), 50);

async function updateSignature(): Promise<void> {
  const at = editor.state.selection.main.head;
  const help = toSignature(await ask("signature_help", at));
  if (editor.state.selection.main.head !== at) return;
  const tooltip: Tooltip | null = help && { pos: at, above: true, create: () => ({ dom: signatureDom(help) }) };
  editor.dispatch({ effects: setSignature.of(tooltip) });
}

/** Signature help's tooltip: the label, its active parameter bold, and docs. */
function signatureDom(help: SignatureInfo): HTMLElement {
  const dom = document.createElement("div");
  dom.className = "cm-signature";
  const line = document.createElement("div");
  const active = help.params[help.active];
  const start = active === undefined ? -1 : help.label.indexOf(active, help.label.indexOf("("));
  if (start >= 0 && active !== undefined) {
    const bold = document.createElement("span");
    bold.className = "cm-signature-active";
    bold.textContent = active;
    line.append(help.label.slice(0, start), bold, help.label.slice(start + active.length));
  } else {
    line.textContent = help.label;
  }
  dom.append(line);
  if (help.doc) {
    const doc = document.createElement("div");
    doc.className = "cm-signature-doc";
    doc.textContent = help.doc;
    dom.append(doc);
  }
  return dom;
}

const COMPLETION_TYPES: Record<string, string> = {
  function: "function",
  method: "method",
  class: "class",
  interface: "interface",
  parameter: "variable",
  let: "variable",
  var: "variable",
  field: "property",
  getter: "property",
  setter: "property",
  "type parameter": "type",
  builtin: "keyword",
  // A REPL function's string argument, as `describe(1, "tmpl`.
  text: "text",
};

async function complete(context: CompletionContext): Promise<CompletionResult | null> {
  const word = context.matchBefore(/[\p{L}_][\p{L}\p{N}_]*/u);
  const afterDot = context.matchBefore(/\.[\p{L}\p{N}_]*$/u);
  if (!word && !afterDot && !context.explicit) return null;
  const from = afterDot ? afterDot.from + 1 : (word?.from ?? context.pos);
  const items = toCompletions(await ask("completions", context.pos));
  if (context.aborted) return null;
  return { from, options: completionOptions(items), validFor: /^[\p{L}\p{N}_]*$/u };
}

/** Completions as CodeMirror's options, in the order given. */
function completionOptions(items: CompletionItem[]): Completion[] {
  return items.map((c, i) => ({
    label: c.name,
    type: COMPLETION_TYPES[c.kind] ?? "variable",
    // A signature without the name the label already shows.
    detail: c.type?.startsWith(`${c.name}(`) ? c.type.slice(c.name.length) : (c.type ?? (c.kind === "text" ? undefined : c.kind)),
    boost: -i / items.length,
  }));
}

// Examples and links.

const picker = $<HTMLSelectElement>("#example");
picker.replaceChildren(
  ...examples.map((example, i) => {
    const option = document.createElement("option");
    option.value = String(i);
    option.textContent = example.title;
    return option;
  }),
);
picker.addEventListener("change", () => {
  const example = examples[Number(picker.value)];
  if (example) setSource(example.source);
});

async function updateHash(): Promise<void> {
  const text = source();
  const isExample = examples.some((e) => e.source === text);
  const hash = isExample ? "" : `#${await encode(text)}`;
  window.history.replaceState(null, "", `${location.pathname}${location.search}${hash}`);
}

$<HTMLButtonElement>("#share").addEventListener("click", async () => {
  window.history.replaceState(null, "", `${location.pathname}${location.search}#${await encode(source())}`);
  const button = $<HTMLButtonElement>("#share");
  try {
    await navigator.clipboard.writeText(location.href);
    button.textContent = "Link copied";
  } catch {
    button.textContent = "Link in address bar";
  }
  setTimeout(() => (button.textContent = "Share"), 2000);
});

function debounce(f: () => void, ms: number): () => void {
  let timer = 0;
  return () => {
    clearTimeout(timer);
    timer = window.setTimeout(f, ms);
  };
}

// Views: the editor and its tabs, or the REPL, which starts when first
// shown.

const repl = createRepl({
  source,
  module: () => wasmModule,
  extensions: [temper, syntaxHighlighting(highlight), looks],
  highlight,
  language: temper,
  help: { hover: hoverDom, signature: signatureDom, options: completionOptions },
});
const views = { editor: $<HTMLButtonElement>("#view-editor"), repl: $<HTMLButtonElement>("#view-repl") };

function showView(name: keyof typeof views): void {
  for (const [key, tab] of Object.entries(views)) {
    const on = key === name;
    tab.setAttribute("aria-selected", String(on));
    tab.tabIndex = on ? 0 : -1;
  }
  $("#source").hidden = name !== "editor";
  $("#output").hidden = name !== "editor";
  $("#repl").hidden = name !== "repl";
  document.body.classList.toggle("repl-view", name === "repl");
  if (name === "repl") repl.show();
  else editor.focus();
}

views.editor.addEventListener("click", () => showView("editor"));
views.repl.addEventListener("click", () => showView("repl"));
views.editor.parentElement?.addEventListener("keydown", (event) => {
  if (event.key !== "ArrowRight" && event.key !== "ArrowLeft") return;
  const next = views.editor.getAttribute("aria-selected") === "true" ? "repl" : "editor";
  showView(next);
  views[next].focus();
});

// Start: the source from the link or the first example, then the wasm.

async function start(): Promise<void> {
  const linked = await decode(location.hash);
  const first = examples[0];
  setSource(linked ?? first?.source ?? "");
  if (linked !== null) picker.value = String(examples.findIndex((e) => e.source === linked));
  status("Loading the compiler…", false);
  try {
    const response = fetch(`temper_wasm.wasm?v=${BUILD_VERSION}`);
    wasmModule = await WebAssembly.compileStreaming(response).catch(async () =>
      WebAssembly.compile(await (await fetch(`temper_wasm.wasm?v=${BUILD_VERSION}`)).arrayBuffer()),
    );
  } catch (error) {
    status(`Couldn't load the compiler: ${String(error)}`, true);
    return;
  }
  startWorker(wasmModule);
  requestCompile();
}

void start();
