import * as fs from "fs";
import * as path from "path";
import * as oniguruma from "vscode-oniguruma";
import * as textmate from "vscode-textmate";

export async function buildHighlightExtension() {
  const grammar = await loadGrammar();
  return {
    extendMarkdownIt(md: any) {
      const highlight = md.options.highlight;
      md.options.highlight = (code: string, lang: string) => {
        if (lang && lang.match(/\btemper\b/i)) {
          // Wrap in div to match ts. Currently results in less line spacing.
          return `<div>${tagCode({ code, grammar })}</div>`;
        }
        return highlight(code, lang);
      };
      return md;
    },
  };
}

type CodeGrammar = {
  code: string;
  grammar: textmate.IGrammar;
};

type TokensLine = {
  line: string;
  tokens: textmate.IToken[];
};

export function escapeHtml(text: string) {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;");
}

export function tagCode(codeGrammar: CodeGrammar) {
  return tagLines(tokenizeCode(codeGrammar));
}

function tagLines(lines: Iterable<TokensLine>) {
  const parts = [] as string[];
  let count = 0;
  for (const { line, tokens } of lines) {
    if (count) {
      parts.push("\n");
    }
    for (const { startIndex, endIndex, scopes } of tokens) {
      const hljs = hljsScope(scopes);
      if (hljs !== undefined) {
        parts.push('<span class="hljs-');
        parts.push(hljs);
        parts.push('">');
      }
      parts.push(escapeHtml(line.slice(startIndex, endIndex)));
      if (hljs !== undefined) {
        parts.push("</span>");
      }
    }
    count += 1;
  }
  return parts.join("");
}

function hljsScope(scopes: string[]) {
  // Check for regexp above last.
  if (scopes.length > 2) {
    if (scopes[scopes.length - 2].match(/\bregexp\b/)) {
      return "regexp";
    }
  }
  // After that, look at last.
  const scope = scopes[scopes.length - 1];
  const parts = new Set(scope.split(/\./));
  const mapping: { [key: string]: string } = {
    constant: "literal",
    function: "title",
    numeric: "number",
    storage: "keyword",
    type: "title",
  };
  // Use order here for priority as needed.
  const scopeChecks = [
    "comment",
    "regexp",
    "string",
    "keyword",
    "storage",
    // Function and type after keyword and storage.
    "function",
    "type",
    "numeric",
    // Constant after numeric.
    "constant",
  ];
  for (const scopeCheck of scopeChecks) {
    if (parts.has(scopeCheck)) {
      return mapping[scopeCheck] ?? scopeCheck;
    }
  }
  return undefined;
}

export async function loadGrammar() {
  const root = (await findAncestorWith("package.json"))!;
  const registry = new textmate.Registry({
    onigLib: (async () => {
      const wasm = await fs.promises.readFile(
        `${root}/node_modules/vscode-oniguruma/release/onig.wasm`
      );
      // console.log(`wasm.length: ${wasm.length}`);
      await oniguruma.loadWASM(wasm);
      return {
        createOnigScanner(patterns: string[]) {
          return new oniguruma.OnigScanner(patterns);
        },
        createOnigString(s: string) {
          return new oniguruma.OnigString(s);
        },
      };
    })(),
    async loadGrammar(scope) {
      if (scope !== "source.temper") {
        console.log(`Unknown scope: ${scope}`);
        return null;
      }
      const path = `${root}/syntaxes/temper.tmLanguage.json`;
      const buffer = await fs.promises.readFile(path);
      return textmate.parseRawGrammar(buffer.toString(), path);
    },
  });
  return (await registry.loadGrammar("source.temper"))!;
}

export function* tokenizeCode({ code, grammar }: CodeGrammar) {
  const lines = code.split(/\n/);
  let ruleStack = textmate.INITIAL;
  for (const line of lines) {
    const result = grammar.tokenizeLine(line, ruleStack);
    yield { line, tokens: result.tokens };
    ruleStack = result.ruleStack;
  }
}

async function exists(name: string) {
  try {
    await fs.promises.access(name);
    return true;
  } catch {
    return false;
  }
}

async function findAncestorWith(name: string) {
  for (let dir = __dirname, parent: string; ; dir = parent) {
    parent = path.dirname(dir);
    if (!parent || parent === dir) {
      break;
    }
    if (await exists(path.join(dir, name))) {
      return dir;
    }
  }
}
