// The preview's page and the pure steps around it: highlighting the
// Temper in the Markdown engine's HTML, the page with its CSP, and what
// a clicked link means. Tested by page.test.ts.

import { type Grammar, escapeHtml, highlight } from "./highlight";

const unescapes: Record<string, string> = { "&lt;": "<", "&gt;": ">", "&quot;": '"', "&#39;": "'", "&amp;": "&" };
const unescapeHtml = (html: string): string => html.replace(/&(?:lt|gt|quot|#39|amp);/g, (e) => unescapes[e]!);

/** What an inert block's label says, in the editor and the preview. */
export const INERT_TITLE = "Not compiled as part of this file";

/** A fenced code block tagged `temper inert`, which the literate split
 * doesn't compile: its opening and closing fence lines, from 0 (the
 * closing one is the last line if it's never closed). */
export interface InertBlock {
  open: number;
  close: number;
}

/** The `temper inert` blocks in a `.temper.md` source, as the literate
 * split finds fences (crates/temper-syntax/src/literate.rs): a fence of
 * three or more backticks or tildes, its info's first word `temper` and
 * another `inert`, closed by a fence of the same character at least as
 * long. Fences in other fences don't count. */
export function inertBlocks(source: string): InertBlock[] {
  const lines = source.split(/\r?\n/);
  const found: InertBlock[] = [];
  let open: { fence: string; line: number; inert: boolean } | undefined;
  lines.forEach((line, i) => {
    if (open) {
      const m = /^ {0,3}(`{3,}|~{3,})[ \t]*$/.exec(line);
      if (m && m[1]![0] === open.fence[0] && m[1]!.length >= open.fence.length) {
        if (open.inert) found.push({ open: open.line, close: i });
        open = undefined;
      }
      return;
    }
    const m = /^ {0,3}(`{3,}|~{3,})(.*)$/.exec(line);
    if (!m || (m[1]![0] === "`" && m[2]!.includes("`"))) return;
    const tags = m[2]!.trim().split(/\s+/);
    open = { fence: m[1]!, line: i, inert: tags[0] === "temper" && tags.includes("inert") };
  });
  if (open?.inert) found.push({ open: open.line, close: lines.length - 1 });
  return found;
}

/** Highlights the Temper code blocks in the Markdown engine's HTML:
 * indented blocks, and fenced ones untagged or tagged `temper`, as the
 * literate split reads them, `temper inert` too, being Temper. A block
 * whose fence is on one of `inertLines` (its `data-line`) is marked
 * `data-inert` and labeled "inert" in its corner. Other languages keep
 * the engine's own highlighting. */
export function highlightCodeBlocks(html: string, grammar: Grammar, inertLines: ReadonlySet<number> = new Set()): string {
  return html.replace(/(<pre\b[^>]*>)(\s*)(<code\b([^>]*)>)([\s\S]*?)(<\/code>\s*<\/pre>)/g, (all, pre: string, space: string, code: string, attrs: string, body: string, close: string) => {
    const lang = /\bclass="[^"]*\blanguage-([^\s"]+)/.exec(attrs)?.[1];
    if (lang !== undefined && lang !== "temper") return all;
    // Already marked up (a plugin's highlighting) isn't plain code.
    if (body.includes("<")) return all;
    const line = Number(/\bdata-line="(\d+)"/.exec(attrs)?.[1] ?? NaN);
    const inert = inertLines.has(line);
    const marks = inert ? 'data-temper="" data-inert=""' : 'data-temper=""';
    const label = inert ? `<span class="inert-label" title="${INERT_TITLE}">inert</span>` : "";
    return `${pre}${label}${space}${code.replace(/<code\b/, `<code ${marks}`)}${highlight(grammar, unescapeHtml(body))}${close}`;
  });
}

/** What a link in the preview goes to. */
export type Target =
  | { kind: "fragment"; id: string }
  | { kind: "external"; uri: string }
  /** A path, from the document's folder, its workspace folder (a
   * leading `/`), or the file system's root (a `file:` URI). */
  | { kind: "file"; path: string; fragment: string; from: "document" | "workspace" | "root" };

/** What `href`, as written in the document, means: a heading in the
 * preview, a web or mail link, or a file relative to the document (or
 * with a leading `/`, to its workspace folder). Other schemes, which
 * could run commands, aren't followed. */
export function linkTarget(href: string): Target | undefined {
  const text = href.trim();
  if (!text) return undefined;
  if (text.startsWith("#")) return { kind: "fragment", id: decode(text.slice(1)) };
  const scheme = /^([a-z][a-z0-9+.-]*):/i.exec(text)?.[1]?.toLowerCase();
  if (scheme === "http" || scheme === "https" || scheme === "mailto") return { kind: "external", uri: text };
  if (scheme === "file") {
    const [path = "", fragment = ""] = text.replace(/^file:\/\//i, "").split("#", 2);
    return { kind: "file", path: decode(path), fragment: decode(fragment), from: "root" };
  }
  // A Windows drive (`C:\…`) is a path, not a scheme.
  if (scheme && scheme.length > 1) return undefined;
  const [path = "", fragment = ""] = text.split("#", 2);
  return { kind: "file", path: decode(path.split("?")[0] ?? ""), fragment: decode(fragment), from: path.startsWith("/") ? "workspace" : "document" };
}

function decode(text: string): string {
  try {
    return decodeURIComponent(text);
  } catch {
    return text;
  }
}

/** The source line a fragment like `L12` or `L12-L14` names, from 0. */
export function fragmentLine(fragment: string): number | undefined {
  const m = /^L(\d+)/i.exec(fragment);
  return m ? Math.max(0, Number(m[1]) - 1) : undefined;
}

export interface PageParts {
  /** The webview's source, for the CSP (`webview.cspSource`). */
  cspSource: string;
  nonce: string;
  /** The document's folder, as a webview URI ending in `/`, that
   * relative images resolve against. */
  base: string;
  style: string;
  script: string;
  title: string;
  /** The rendered body, and the document version it's from. */
  body: string;
  version: number;
}

/** The preview's page: a strict CSP (scripts only with the nonce,
 * nothing remote, inline styles but no inline scripts), the body, and
 * the script that syncs and links. */
export function page(p: PageParts): string {
  const csp = [
    "default-src 'none'",
    `img-src ${p.cspSource} data:`,
    `media-src ${p.cspSource}`,
    // Inline styles too: the engine aligns table columns with them.
    `style-src ${p.cspSource} 'unsafe-inline'`,
    `font-src ${p.cspSource}`,
    `script-src 'nonce-${p.nonce}'`,
  ].join("; ");
  const attr = (s: string): string => escapeHtml(s);
  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="${attr(csp)}">
<meta name="viewport" content="width=device-width, initial-scale=1">
<base href="${attr(p.base)}">
<link rel="stylesheet" href="${attr(p.style)}">
<title>${escapeHtml(p.title)}</title>
</head>
<body>
<main id="content" class="markdown-body" data-version="${p.version}">${p.body}</main>
<script nonce="${attr(p.nonce)}" src="${attr(p.script)}"></script>
</body>
</html>
`;
}

/** The engine's HTML, highlighted, `source`'s inert blocks marked, with
 * an end mark at the document's last line so the sync reaches the
 * bottom. */
export function body(rendered: string, grammar: Grammar | undefined, lineCount: number, source = ""): string {
  const inert = new Set(inertBlocks(source).map((b) => b.open));
  const html = grammar ? highlightCodeBlocks(rendered, grammar, inert) : rendered;
  return `${html}<div class="code-line" data-line="${lineCount}"></div>`;
}
