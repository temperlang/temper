// The REPL tab: a session of chained inputs, run by crates/temper-wasm's
// `repl_*` in a worker of its own (repl-worker.ts), with the editor's
// code as input 0.
//
// - Enter asks the worker whether the cell's text is whole (`temper
//   repl`'s rule); if it is, each input it splits into runs in turn,
//   else Enter starts a new line, indented by the brackets open.
//   Shift+Enter always starts a new line; Ctrl or Cmd+Enter, and Run,
//   always run. On touch screens, Enter starts a new line. Up and Down
//   on the first and last line go through history.
// - Each input's answer shows as the CLI prints it, rendered as text,
//   never as HTML. Logged lines stream in as they're logged.
// - Each input may take `FUEL` steps; one that runs out offers "Keep
//   going", which runs the same text again, as the next input, with ten
//   times as many.
// - Stop terminates the worker and starts another, which replays the
//   earlier inputs quietly; the page keeps the list. A trap (a panic, or
//   the memory cap) restores the session the same way. So does a changed
//   editor, the next time the REPL is used.
// - The cell has the editor's completion, hover (also Ctrl or Cmd+I at
//   the cursor), and signature help, from `repl_query` over the session:
//   the cell's text is read against what earlier inputs declared, never
//   settled or run. While an input runs, the worker is busy, so the
//   cell has no help until it ends. The completion menu opens by itself
//   only while a name is typed or after `.`, with the first option
//   picked: Enter or Tab accepts it, Escape closes it. Enter runs
//   instead if the name typed is already an option, as `x` for `x`.
// - The transcript keeps the latest `MAX_BLOCKS` inputs, folds long
//   output after `FOLD_LINES` lines, with "Show all", and links each
//   `interactive#N` to that input's block, putting its text in the cell.

import {
  type Completion,
  type CompletionContext,
  type CompletionResult,
  acceptCompletion,
  autocompletion,
  closeCompletion,
  completionStatus,
  currentCompletions,
  selectedCompletion,
} from "@codemirror/autocomplete";
import { defaultKeymap, history, historyKeymap, indentWithTab, insertNewlineAndIndent } from "@codemirror/commands";
import { type HighlightStyle, type Language, bracketMatching } from "@codemirror/language";
import { EditorState, type Extension, Prec, StateEffect, StateField } from "@codemirror/state";
import { EditorView, type Tooltip, drawSelection, hoverTooltip, keymap, placeholder, showTooltip } from "@codemirror/view";
import { highlightCode } from "@lezer/highlight";

import type {
  CompletionItem,
  FromRepl,
  HoverInfo,
  ReplAnswer,
  ReplCompletion,
  ReplQuery,
  ReplReplay,
  SignatureInfo,
  ToRepl,
} from "./api";
import { toHover, toReplCompletions, toSignature } from "./api";

/** Steps per input, as for a compile (temper-frontend's `interp::FUEL`). */
export const FUEL = 50_000_000;
/** How many more steps "Keep going" gives, as a factor. */
const MORE = 10;
/** Steps for replaying an input that failed for its steps or was stopped. */
const FAIL_FAST = 1;
const HISTORY_KEY = "repl-history";
const HISTORY_SIZE = 200;
/** Inputs the transcript keeps; older ones are dropped, with a note. */
const MAX_BLOCKS = 300;
/** Lines of an input's output shown before "Show all". */
const FOLD_LINES = 40;
/** A name being typed, before the cursor. */
const WORD = /[\p{L}_][\p{L}\p{N}_]*$/u;

export interface ReplHost {
  /** The editor's code, for input 0. */
  source(): string;
  /** The compiled wasm module, once loaded. */
  module(): WebAssembly.Module | null;
  /** Temper highlighting and the editor's looks, for the cell. */
  extensions: Extension[];
  highlight: HighlightStyle;
  language: Language;
  /** The editor's tooltips and completion options, for the cell's. */
  help: {
    hover(value: HoverInfo): HTMLElement;
    signature(help: SignatureInfo): HTMLElement;
    options(items: CompletionItem[]): Completion[];
  };
}

// Tooltips the cell shows until the text or cursor moves on: signature
// help, and hover asked for from the keyboard.
const setSignature = StateEffect.define<Tooltip | null>();
const signatureField = StateField.define<Tooltip | null>({
  create: () => null,
  update(tooltip, tr) {
    for (const effect of tr.effects) if (effect.is(setSignature)) return effect.value;
    // Kept where it was until the next answer, which may be a while.
    return tooltip && tr.docChanged ? { ...tooltip, pos: tr.changes.mapPos(tooltip.pos) } : tooltip;
  },
  provide: (field) => showTooltip.from(field),
});
const setInfo = StateEffect.define<Tooltip | null>();
const infoField = StateField.define<Tooltip | null>({
  create: () => null,
  update(tooltip, tr) {
    for (const effect of tr.effects) if (effect.is(setInfo)) return effect.value;
    return tr.docChanged || tr.selection ? null : tooltip;
  },
  provide: (field) => showTooltip.from(field),
});

/** An input waiting for, or having, its turn in the worker. */
interface Job {
  text: string;
  fuel: number;
  block: HTMLElement;
}

const $ = <T extends HTMLElement>(selector: string): T => {
  const element = document.querySelector<T>(selector);
  if (!element) throw new Error(`no ${selector}`);
  return element;
};

function element(tag: string, className: string, text?: string): HTMLElement {
  const e = document.createElement(tag);
  e.className = className;
  if (text !== undefined) e.textContent = text;
  return e;
}

function plural(n: number, one: string, many = `${one}s`): string {
  return `${n} ${n === 1 ? one : many}`;
}

export function createRepl(host: ReplHost): { show(): void } {
  const log = $("#repl-transcript");
  const runButton = $<HTMLButtonElement>("#repl-run");
  const stopButton = $<HTMLButtonElement>("#repl-stop");
  const restartButton = $<HTMLButtonElement>("#repl-restart");
  const statusLine = $("#repl-status");
  const touch = window.matchMedia("(pointer: coarse)").matches;

  let worker: Worker | null = null;
  let nextId = 1;
  /** The editor's code the session started with, or null for none yet. */
  let sessionSource: string | null = null;
  /** The editor's code whose input 0 the transcript last showed. */
  let shownSource: string | null = null;
  /** Inputs after input 0, in order, for replay. */
  let taken: ReplReplay[] = [];
  /** The number the next input gets. */
  let nextNumber = 0;
  /** Waiting for the session to start (or restart). */
  let starting = 0;
  let running: (Job & { id: number }) | null = null;
  const queue: Job[] = [];
  const completions = new Map<number, (c: ReplCompletion | null) => void>();
  const queries = new Map<number, (value: unknown) => void>();
  /** Lines streamed for the running input, before its answer. */
  let streamed: HTMLElement | null = null;

  // History: per viewer, in localStorage when it's there.
  let past: string[] = [];
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(HISTORY_KEY) ?? "[]");
    if (Array.isArray(stored)) past = stored.filter((s): s is string => typeof s === "string");
  } catch {
    // No storage: history lasts the visit.
  }
  let at = past.length;
  let draft = "";

  function remember(text: string): void {
    if (past[past.length - 1] !== text) past.push(text);
    past = past.slice(-HISTORY_SIZE);
    at = past.length;
    draft = "";
    try {
      localStorage.setItem(HISTORY_KEY, JSON.stringify(past));
    } catch {
      // Private windows may refuse.
    }
  }

  function walk(view: EditorView, step: -1 | 1): boolean {
    const head = view.state.selection.main.head;
    const line = view.state.doc.lineAt(head).number;
    if (step < 0 ? line !== 1 : line !== view.state.doc.lines) return false;
    const to = at + step;
    if (to < 0 || to > past.length) return true;
    if (at === past.length) draft = view.state.doc.toString();
    at = to;
    const text = at === past.length ? draft : (past[at] ?? "");
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: text },
      selection: { anchor: step < 0 ? Math.min(text.indexOf("\n") >>> 0, text.length) : text.length },
    });
    return true;
  }

  // The input cell.

  const cell = new EditorView({
    parent: $("#repl-cell"),
    state: EditorState.create({
      extensions: [
        history(),
        drawSelection(),
        bracketMatching(),
        // Before the menu's own keys, which would accept a name already typed.
        Prec.highest(
          keymap.of([
            { key: "Enter", run: (view) => accept(view, true) },
            { key: "Tab", run: (view) => accept(view, false) },
          ]),
        ),
        autocompletion({ override: [completeCell] }),
        hoverTooltip(hoverCell, { hoverTime: 300 }),
        signatureField,
        infoField,
        keymap.of([
          { key: "Enter", run: (view) => (touch ? insertNewlineAndIndent(view) : (void enter(view, false), true)) },
          { key: "Shift-Enter", run: insertNewlineAndIndent },
          { key: "Mod-Enter", run: (view) => (void enter(view, true), true) },
          { key: "ArrowUp", run: (view) => walk(view, -1) },
          { key: "ArrowDown", run: (view) => walk(view, 1) },
          { key: "Escape", run: escape },
          { key: "Mod-i", run: (view) => (void infoAt(view), true) },
        ]),
        keymap.of([...defaultKeymap, ...historyKeymap, indentWithTab]),
        ...host.extensions,
        placeholder(touch ? "Temper, then Run" : "Temper, then Enter"),
        EditorView.lineWrapping,
        EditorView.contentAttributes.of({ "aria-label": "REPL input", "aria-multiline": "true" }),
        EditorView.updateListener.of((update) => {
          // Signature help opens on `(` or `,`, and follows the cursor while open.
          const head = update.state.selection.main.head;
          const typed = update.transactions.some((tr) => tr.isUserEvent("input.type"));
          const inserted = typed ? update.state.sliceDoc(head - 1, head) : "";
          const open = update.startState.field(signatureField) !== null;
          if (inserted === "(" || inserted === "," || (open && (update.docChanged || update.selectionSet))) {
            void updateSignature(update.view);
          }
        }),
      ],
    }),
  });

  /**
   * Enter or Tab with the completion menu open: accepts the option
   * picked. Enter closes the menu instead, and runs, if the name typed
   * is already an option. False with no menu, for the keys' other uses.
   */
  function accept(view: EditorView, enterKey: boolean): boolean {
    const state = view.state;
    if (completionStatus(state) !== "active" || !selectedCompletion(state)) return false;
    const head = state.selection.main.head;
    const typed = WORD.exec(state.sliceDoc(Math.max(0, head - 200), head))?.[0];
    if (enterKey && typed && currentCompletions(state).some((c) => c.label === typed)) {
      closeCompletion(view);
      return false;
    }
    // Too soon after the menu opened, it refuses; the key still isn't Enter's.
    acceptCompletion(view);
    return true;
  }

  /** Enter, Ctrl+Enter, or Run: runs the cell's inputs if it's whole. */
  async function enter(view: EditorView, force: boolean): Promise<void> {
    const text = view.state.doc.toString();
    if (!text.trim()) return;
    if (!host.module()) {
      status("Loading the compiler…");
      return;
    }
    const answer = await completion(text);
    // No answer: the session restarted meanwhile, as for Stop.
    if (!answer || view.state.doc.toString() !== text) return;
    let inputs: string[];
    if (answer.complete) inputs = answer.inputs;
    else if (force) inputs = [text];
    else {
      const head = view.state.selection.main.head;
      if (head === view.state.doc.length) {
        const indent = "  ".repeat(answer.brackets);
        view.dispatch({ changes: { from: head, insert: `\n${indent}` }, selection: { anchor: head + 1 + indent.length }, scrollIntoView: true });
      } else {
        insertNewlineAndIndent(view);
      }
      return;
    }
    remember(text);
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: "" } });
    for (const input of inputs) submit(input, FUEL);
  }

  function completion(text: string): Promise<ReplCompletion | null> {
    if (!ensureSession()) return Promise.resolve(null);
    const id = nextId++;
    const message: ToRepl = { type: "complete", id, text };
    const answer = new Promise<ReplCompletion | null>((resolve) => completions.set(id, resolve));
    worker?.postMessage(message);
    return answer;
  }

  // The cell's help, asked of the session; nothing while it's busy.

  function ask(what: ReplQuery, text: string, at: number): Promise<unknown> {
    if (!worker || running || starting) return Promise.resolve(null);
    const id = nextId++;
    const answer = new Promise<unknown>((resolve) => queries.set(id, resolve));
    worker.postMessage({ type: "query", id, what, text, at } satisfies ToRepl);
    return answer;
  }

  async function completeCell(context: CompletionContext): Promise<CompletionResult | null> {
    const word = context.matchBefore(WORD);
    const afterDot = context.matchBefore(/\.[\p{L}\p{N}_]*$/u);
    // A REPL function's string argument, as `describe(1, "`.
    const argument = context.matchBefore(/\(\s*(\d+\s*,\s*)?"[^"\\$]*$/u);
    if (!word && !afterDot && !argument && !context.explicit) return null;
    const value = await ask("completions", context.state.doc.toString(), context.pos);
    const answer = toReplCompletions(value);
    if (context.aborted || !answer || answer.items.length === 0) return null;
    return { from: answer.from, options: host.help.options(answer.items), validFor: /^[\p{L}\p{N}_]*$/u };
  }

  async function hoverCell(view: EditorView, at: number, side: -1 | 1): Promise<Tooltip | null> {
    // Between characters, the one the pointer's over.
    const value = toHover(await ask("hover", view.state.doc.toString(), side < 0 && at > 0 ? at - 1 : at));
    if (!value?.type) return null;
    return { pos: value.from, end: value.to, above: true, create: () => ({ dom: host.help.hover(value) }) };
  }

  /** Ctrl or Cmd+I: hover's tooltip for the name at the cursor. */
  async function infoAt(view: EditorView): Promise<void> {
    const doc = view.state.doc.toString();
    const head = view.state.selection.main.head;
    const value = toHover(await ask("hover", doc, head > 0 && !/[\p{L}\p{N}_]/u.test(doc[head] ?? "") ? head - 1 : head));
    if (view.state.doc.toString() !== doc || view.state.selection.main.head !== head) return;
    const tooltip: Tooltip | null = value?.type
      ? {
          pos: value.from,
          end: value.to,
          above: true,
          create: () => {
            // Styled as the pointer's hover is.
            const dom = document.createElement("div");
            dom.className = "cm-tooltip-hover";
            dom.append(host.help.hover(value));
            return { dom };
          },
        }
      : null;
    if (!tooltip) status("Nothing to show here");
    view.dispatch({ effects: setInfo.of(tooltip) });
  }

  async function updateSignature(view: EditorView): Promise<void> {
    const doc = view.state.doc.toString();
    const at = view.state.selection.main.head;
    const help = toSignature(await ask("signature", doc, at));
    if (view.state.doc.toString() !== doc || view.state.selection.main.head !== at) return;
    const tooltip: Tooltip | null = help && { pos: at, above: true, create: () => ({ dom: host.help.signature(help) }) };
    view.dispatch({ effects: setSignature.of(tooltip) });
  }

  /** Escape: stops a run; else closes the cell's tooltips. */
  function escape(view: EditorView): boolean {
    if (running || starting) {
      stop();
      return true;
    }
    const shown = view.state.field(signatureField) !== null || view.state.field(infoField) !== null;
    if (shown) view.dispatch({ effects: [setSignature.of(null), setInfo.of(null)] });
    return shown;
  }

  runButton.addEventListener("click", () => {
    void enter(cell, true);
    cell.focus();
  });
  stopButton.addEventListener("click", () => {
    stop();
    cell.focus();
  });
  restartButton.addEventListener("click", () => {
    taken = [];
    queue.splice(0);
    log.replaceChildren();
    restart([], "");
    cell.focus();
  });

  // The transcript.

  function scrollDown(): void {
    log.scrollTop = log.scrollHeight;
  }

  function note(text: string, kind = "note"): void {
    log.append(element("p", `repl-banner ${kind}`, text));
    scrollDown();
  }

  /** The echoed input, highlighted, after the CLI's prompt. */
  function echo(text: string): HTMLElement {
    const pre = element("pre", "repl-echo");
    pre.append(element("span", "repl-prompt", "$ "));
    const code = element("code", "");
    const tree = host.language.parser.parse(text);
    highlightCode(
      text,
      tree,
      host.highlight,
      (piece, classes) => code.append(classes ? element("span", classes, piece) : document.createTextNode(piece)),
      () => code.append("\n"),
    );
    pre.append(code);
    return pre;
  }

  function block(text: string): HTMLElement {
    const div = element("div", "repl-block");
    div.dataset.text = text;
    div.append(echo(text));
    log.append(div);
    trim();
    scrollDown();
    return div;
  }

  /** Past `MAX_BLOCKS` inputs, drops the oldest, saying how many. */
  let dropped = 0;
  function trim(): void {
    const blocks = log.querySelectorAll(":scope > .repl-block");
    const over = blocks.length - MAX_BLOCKS;
    if (over <= 0) return;
    for (const b of Array.from(blocks).slice(0, over)) b.remove();
    dropped += over;
    let first = log.querySelector<HTMLElement>(":scope > .repl-dropped");
    if (!first) {
      first = element("p", "repl-banner repl-dropped");
      log.prepend(first);
    }
    first.textContent = `${plural(dropped, "earlier input")} dropped from the transcript, to keep the page quick; the session still has them.`;
  }

  /** The latest block for input `n`, if the transcript has it. */
  function blockOf(n: number): HTMLElement | null {
    const all = log.querySelectorAll<HTMLElement>(`.repl-block[data-input="${n}"]`);
    return all[all.length - 1] ?? null;
  }

  /** `text`, with each `interactive#N` that has a block, other than `own`'s, a link to it. */
  function linked(target: HTMLElement, text: string, own: number | null): void {
    const pattern = /interactive#(\d+)/g;
    let at = 0;
    for (const match of text.matchAll(pattern)) {
      const n = Number(match[1]);
      const to = n === own ? null : blockOf(n);
      if (!to) continue;
      target.append(text.slice(at, match.index));
      const link = element("button", "repl-ref", match[0]) as HTMLButtonElement;
      link.type = "button";
      link.title = n === 0 ? "Show input 0" : `Show input ${n}, and put it in the cell`;
      link.addEventListener("click", () => goTo(n));
      target.append(link);
      at = match.index + match[0].length;
    }
    target.append(text.slice(at));
  }

  /** Scrolls to input `n`'s block, marks it, and puts its text in the cell (not input 0's, the editor's). */
  function goTo(n: number): void {
    const to = blockOf(n);
    if (!to) return;
    to.scrollIntoView({ block: "nearest" });
    to.classList.remove("repl-marked");
    void to.offsetWidth;
    to.classList.add("repl-marked");
    const text = to.dataset.text;
    if (n !== 0 && text !== undefined) {
      cell.dispatch({ changes: { from: 0, to: cell.state.doc.length, insert: text }, selection: { anchor: text.length } });
    }
    cell.focus();
  }

  function line(target: HTMLElement, kind: string, text: string, level?: string, own: number | null = null): HTMLElement {
    const pre = element("pre", `repl-event repl-${kind}${level ? ` ${level}` : ""}`);
    linked(pre, text, own);
    target.append(pre);
    return pre;
  }

  /** A "Show all" button that shows `hidden`, or a long event's whole `text`. */
  function showAll(label: string, reveal: () => void): HTMLButtonElement {
    const button = element("button", "repl-more", label) as HTMLButtonElement;
    button.type = "button";
    button.addEventListener("click", () => {
      reveal();
      button.remove();
    });
    return button;
  }

  /**
   * The events, folded after `FOLD_LINES` lines: a long event shows its
   * first lines, and events past the limit wait behind "Show all"; the
   * result line always shows.
   */
  function events(div: HTMLElement, answer: ReplAnswer): void {
    let shown = 0;
    let rest: HTMLElement | null = null;
    let restLines = 0;
    for (const event of answer.events) {
      const lines = event.text.split("\n");
      if (event.kind === "result") {
        line(div, event.kind, event.text, event.level, answer.input);
        continue;
      }
      if (shown >= FOLD_LINES) {
        rest ??= element("div", "repl-folded");
        line(rest, event.kind, event.text, event.level, answer.input);
        restLines += lines.length;
        continue;
      }
      if (shown + lines.length <= FOLD_LINES) {
        line(div, event.kind, event.text, event.level, answer.input);
        shown += lines.length;
        continue;
      }
      const keep = FOLD_LINES - shown;
      const pre = line(div, event.kind, lines.slice(0, keep).join("\n"), event.level, answer.input);
      const more = lines.length - keep;
      div.append(
        showAll(`Show all (${plural(more, "more line")})`, () => {
          pre.replaceChildren();
          linked(pre, event.text, answer.input);
        }),
      );
      shown = FOLD_LINES;
    }
    if (rest) {
      const folded = rest;
      folded.hidden = true;
      const result = div.querySelector(":scope > .repl-result");
      const button = showAll(`Show all (${plural(restLines, "more line")})`, () => {
        folded.hidden = false;
      });
      div.insertBefore(folded, result);
      div.insertBefore(button, folded);
    }
  }

  function render(job: Job, answer: ReplAnswer): void {
    const { block: div } = job;
    streamed?.remove();
    streamed = null;
    div.dataset.input = String(answer.input);
    events(div, answer);
    if (answer.out_of_steps) {
      const more = job.fuel * MORE;
      const button = element("button", "repl-more", `Keep going (${steps(more)} steps)`) as HTMLButtonElement;
      button.type = "button";
      button.addEventListener("click", () => {
        button.disabled = true;
        submit(job.text, more);
      });
      div.append(button);
    }
    scrollDown();
  }

  function steps(n: number): string {
    return n >= 1e9 ? `${n / 1e9} billion` : `${n / 1e6} million`;
  }

  // The worker, one request in it at a time, besides Enter's questions.

  /** Starts a session if there's none, or the editor changed. */
  function ensureSession(): boolean {
    if (!host.module()) {
      status("Loading the compiler…");
      return false;
    }
    if (!worker) restart(taken, "");
    else if (sessionSource !== host.source() && !running && !starting) {
      const n = taken.length;
      restart(taken, n ? `The editor changed: a new session, with ${plural(n, "input")} run again.` : "The editor changed: a new session.");
    }
    return true;
  }

  /** A new worker and session, from the editor's code, then `replay`. */
  function restart(replay: ReplReplay[], why: string): void {
    const module = host.module();
    if (!module) return;
    worker?.terminate();
    for (const resolve of completions.values()) resolve(null);
    completions.clear();
    for (const resolve of queries.values()) resolve(null);
    queries.clear();
    running = null;
    streamed?.remove();
    streamed = null;
    taken = [...replay];
    const w = new Worker(`repl-worker.js?v=${BUILD_VERSION}`, { type: "module", name: "repl" });
    worker = w;
    w.onmessage = (event: MessageEvent<FromRepl>) => {
      if (worker === w) onMessage(event.data);
    };
    w.onerror = (event) => {
      if (worker !== w) return;
      note(`The REPL stopped: ${event.message}`, "failure");
      worker = null;
      starting = 0;
      busy(false);
    };
    w.postMessage({ type: "init", module } satisfies ToRepl);
    const source = host.source();
    sessionSource = source;
    starting = nextId++;
    if (why) note(why);
    status(taken.length ? `Running ${plural(taken.length, "earlier input")} again…` : "Starting…");
    busy(true);
    const message: ToRepl = { type: "start", id: starting, source, fuel: FUEL, replay: taken };
    w.postMessage(message);
  }

  function submit(text: string, fuel: number): void {
    if (!ensureSession()) return;
    queue.push({ text, fuel, block: block(text) });
    pump();
  }

  function pump(): void {
    if (running || starting || !worker) return;
    const job = queue.shift();
    if (!job) {
      busy(false);
      return;
    }
    const id = nextId++;
    running = { ...job, id };
    busy(true);
    status("Running…");
    worker.postMessage({ type: "input", id, text: job.text, fuel: job.fuel } satisfies ToRepl);
  }

  function onMessage(message: FromRepl): void {
    switch (message.type) {
      case "log":
        if (running?.id !== message.id) return;
        if (!streamed) {
          streamed = element("div", "repl-streamed");
          running.block.append(streamed);
        }
        line(streamed, "log", message.line);
        scrollDown();
        return;
      case "query": {
        const resolve = queries.get(message.id);
        queries.delete(message.id);
        resolve?.(message.value);
        return;
      }
      case "complete": {
        const resolve = completions.get(message.id);
        completions.delete(message.id);
        resolve?.(message.completion);
        return;
      }
      case "started": {
        if (message.id !== starting) return;
        starting = 0;
        nextNumber = message.answer ? message.answer.input + 1 + message.replayed : message.replayed;
        const answer = message.answer;
        const quiet = (kind: string) => kind === "log" || kind === "result" || kind === "truncated";
        const telling = answer && (answer.value === null || answer.events.some((e) => !quiet(e.kind)));
        if (answer && telling && sessionSource !== shownSource) {
          // Input 0, the editor's code, failed, or has more to say than
          // its logs, as its tests' summary: show it, once for the code.
          // What it declared without errors still works.
          shownSource = sessionSource;
          const div = element("div", "repl-block");
          div.append(element("p", "repl-banner note", "The editor's code, input 0:"));
          log.append(div);
          render({ text: sessionSource ?? "", fuel: FUEL, block: div }, answer);
        }
        status("");
        pump();
        return;
      }
      case "answer":
        if (running?.id !== message.id) return;
        render(running, message.answer);
        nextNumber = message.answer.input + 1;
        taken.push({ text: running.text, fuel: message.answer.out_of_steps ? FAIL_FAST : running.fuel });
        running = null;
        status("");
        pump();
        return;
      case "trap":
        onTrap(message.message, message.replaying, message.id);
        return;
    }
  }

  /** The instance trapped: restore the session without what trapped. */
  function onTrap(why: string, replaying: number | null, id: number): void {
    const brief = why.replace(/^RuntimeError: /, "");
    if (replaying !== null && id === starting) {
      // An earlier input trapped again as it replayed: leave it out.
      const dropped = taken[replaying];
      const rest = taken.filter((_, i) => i !== replaying);
      note(`Input ${JSON.stringify(dropped?.text.slice(0, 40) ?? "")} crashed again as it ran again (${brief}), so it's left out; later inputs' numbers move down one.`, "failure");
      restart(rest, "");
      return;
    }
    if (running?.id === id) {
      const job = running;
      line(job.block, "failure", `interactive#${nextNumber}: crashed (${brief})`);
      restart([...taken, { text: job.text, fuel: FAIL_FAST }], "Restoring the session…");
      pump();
      return;
    }
    // Input 0, or Enter's question.
    note(`The REPL crashed (${brief}); a new session.`, "failure");
    restart(id === starting ? [] : taken, "");
  }

  /** Stops the running input, restoring the session without it. */
  function stop(): void {
    if (!worker) return;
    const dropped = queue.splice(0);
    for (const job of dropped) line(job.block, "note", "Not run: stopped before its turn.");
    if (running) {
      const job = running;
      streamed = null;
      line(job.block, "interrupted", `interactive#${nextNumber}: stopped`);
      restart([...taken, { text: job.text, fuel: FAIL_FAST }], "");
    } else if (starting) {
      // Stopped while replaying: start over, with none.
      restart([], `Stopped while restoring; a new session without the earlier ${plural(taken.length, "input")}.`);
    }
    pump();
  }

  function busy(on: boolean): void {
    stopButton.disabled = !on;
    log.setAttribute("aria-busy", String(on));
  }

  function status(text: string): void {
    statusLine.textContent = text;
  }

  let introduced = false;
  return {
    show(): void {
      if (!introduced) {
        introduced = true;
        note(
          `The editor's code is input 0. Enter runs an input once it's whole${touch ? "; on a touch screen, Run does" : ""}. ` +
            "Try help(), or translate(1, \"py\") after an input.",
        );
      }
      ensureSession();
      cell.focus();
    },
  };
}
