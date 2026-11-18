// Temper highlighting as a CodeMirror stream language: keywords,
// strings with `${…}` interpolation, comments, numbers, and types by
// capitalization. The IDE chunk can swap it for temper's own tokens.

import { StreamLanguage, type StreamParser, type StringStream } from "@codemirror/language";

const KEYWORDS = new Set([
  "as", "assert", "async", "await", "break", "bubble", "case", "class", "const",
  "continue", "do", "else", "export", "extends", "fn", "for", "get", "if", "import",
  "in", "interface", "is", "let", "new", "of", "orelse", "panic", "private",
  "protected", "public", "return", "sealed", "set", "static", "super", "test",
  "this", "throws", "var", "when", "while", "yield",
]);

const ATOMS = new Set(["true", "false", "null", "void"]);

/** Where the tokenizer is: code, a string, or a block comment. */
type Mode = { kind: "code"; braces: number } | { kind: "string" } | { kind: "comment" };

interface State {
  /** Innermost last: a string's `${…}` is code inside a string. */
  stack: Mode[];
}

function top(state: State): Mode {
  return state.stack[state.stack.length - 1] ?? { kind: "code", braces: 0 };
}

function stringToken(stream: StringStream, state: State): string {
  if (stream.match("${")) {
    state.stack.push({ kind: "code", braces: 0 });
    return "meta";
  }
  if (stream.eat('"')) {
    state.stack.pop();
    return "string";
  }
  if (stream.eat("\\")) {
    stream.next();
    return "escape";
  }
  while (!stream.eol() && !stream.match("${", false) && stream.peek() !== '"' && stream.peek() !== "\\") {
    stream.next();
  }
  return "string";
}

function codeToken(stream: StringStream, state: State, mode: { kind: "code"; braces: number }): string | null {
  if (stream.eatSpace()) return null;
  if (stream.match("//")) {
    stream.skipToEnd();
    return "comment";
  }
  if (stream.match("/*")) {
    state.stack.push({ kind: "comment" });
    return "comment";
  }
  if (stream.eat('"')) {
    state.stack.push({ kind: "string" });
    return "string";
  }
  if (stream.match(/^0x[0-9a-fA-F_]+/) || stream.match(/^\d[\d_]*(\.\d[\d_]*)?([eE][+-]?\d+)?/)) {
    return "number";
  }
  if (stream.match(/^@[A-Za-z_]\w*/)) return "meta";
  const word = stream.match(/^[A-Za-z_$][\w$]*/);
  if (word && typeof word !== "boolean") {
    const name = word[0];
    if (KEYWORDS.has(name)) return "keyword";
    if (ATOMS.has(name)) return "atom";
    if (/^[A-Z]/.test(name)) return "typeName";
    if (stream.match(/^\s*[({]/, false)) return "variableName.function";
    return "variableName";
  }
  const ch = stream.next() ?? "";
  if (ch === "{") mode.braces += 1;
  if (ch === "}") {
    if (mode.braces === 0 && state.stack.length > 1) {
      state.stack.pop();
      return "meta";
    }
    mode.braces = Math.max(0, mode.braces - 1);
  }
  if ("{}()[]".includes(ch)) return "bracket";
  if (/[=<>!+\-*/%&|^~?:]/.test(ch)) return "operator";
  return "punctuation";
}

const parser: StreamParser<State> = {
  name: "temper",
  startState: () => ({ stack: [{ kind: "code", braces: 0 }] }),
  copyState: (state) => ({
    stack: state.stack.map((m) => (m.kind === "code" ? { kind: "code", braces: m.braces } : m)),
  }),
  token(stream, state) {
    const mode = top(state);
    if (mode.kind === "comment") {
      if (stream.skipTo("*/")) {
        stream.match("*/");
        state.stack.pop();
      } else {
        stream.skipToEnd();
      }
      return "comment";
    }
    if (mode.kind === "string") return stringToken(stream, state);
    return codeToken(stream, state, mode);
  },
  languageData: {
    commentTokens: { line: "//", block: { open: "/*", close: "*/" } },
    closeBrackets: { brackets: ["(", "[", "{", '"'] },
  },
};

export const temper = StreamLanguage.define(parser);
