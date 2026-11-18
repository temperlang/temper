// A smoke test of the built playground in headless Chromium, driven by
// site/docs/mermaid's playwright-core (no packages of its own):
//
//   node site/playground/smoke.mjs [DIR] [--site SITE] [--chromium PATH]
//
// DIR is the build (default site/_out/playground), served by a tiny
// static server at /site/playground/, as Pages nests it; SITE, the docs
// site's build (site/docs/build.py's --out), served around it at /site/. For each
// example: pick it, wait for its compile, check every backend's tab has
// code and no error, that there are no error diagnostics, and that Run
// shows output, with the interpreter's and the JavaScript's runs
// agreeing. Then a hover shows a type, an edit survives a reload in the
// URL's fragment, and the top bar's Temper link leads to the site (../).
// Then the REPL tab: the editor's tests run as input 0, `1 + 2`, a
// function typed over several lines with Enter, a log streamed while
// its input runs, Stop with the session restored (and the numbering
// kept), an input that runs out of steps and then keeps going,
// `translate`, completion in the cell offering a name an earlier input
// declared and `runTests` (Enter accepting the first), `console.`'s
// `log` (by Tab), and Enter running a name typed in full, hover over it
// by pointer and by Ctrl+I,
// signature help, an `interactive#N` link putting that input in the
// cell, and `runTests()` after a redefinition failing the editor's test.
// Then the site's header (site/shared): its sections, with Playground
// current; About, opened and closed from the keyboard, naming the
// version; the theme toggle,
// kept across a reload; and at 390 px, the sections behind Menu, with
// the playground's controls in view and no sideways scrolling. With
// SITE, a docs page's header is the same, and a theme picked there
// carries to the playground and back.
// Throughout, no console errors, page errors, or failed requests.
// Chromium is PATH, else $TEMPER_CHROMIUM, else Playwright's own
// (`npx playwright-core install --only-shell chromium` in
// site/docs/mermaid). Exits with 1 if a check fails, 3 if there's no
// Chromium.

import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { extname, join, normalize, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(new URL("../docs/mermaid/package.json", import.meta.url));
const { chromium } = require("playwright-core");

const BACKENDS = 7;
const TIMEOUT = 60_000;

const args = process.argv.slice(2);
const at = args.indexOf("--chromium");
const executablePath = (at >= 0 ? args.splice(at, 2)[1] : null) || process.env.TEMPER_CHROMIUM || undefined;
const siteAt = args.indexOf("--site");
const site = siteAt >= 0 ? resolve(args.splice(siteAt, 2)[1]) : null;
if (args.length > 1) {
  console.error("usage: node smoke.mjs [DIR] [--site SITE] [--chromium PATH]");
  process.exit(2);
}
const dir = resolve(args[0] ?? fileURLToPath(new URL("../_out/playground", import.meta.url)));

// The build, at /site/playground/, and SITE around it at /site/;
// anything else is a 404.
const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".wasm": "application/wasm",
  ".txt": "text/plain; charset=utf-8",
  ".map": "application/json",
};
const PREFIX = "/site/playground/";
const SITE_PREFIX = "/site/";
const server = createServer(async (request, response) => {
  const path = decodeURIComponent(new URL(request.url ?? "/", "http://x").pathname);
  const [root, prefix] = path.startsWith(PREFIX) ? [dir, PREFIX] : site && path.startsWith(SITE_PREFIX) ? [site, SITE_PREFIX] : [null, ""];
  let rel = root ? normalize(path.slice(prefix.length)) : null;
  if (rel !== null && (path.endsWith("/") || rel === ".")) rel = join(rel, "index.html");
  try {
    if (rel === null || rel.startsWith("..")) throw new Error("outside");
    const body = await readFile(join(root, rel));
    response.writeHead(200, { "content-type": TYPES[extname(rel)] ?? "application/octet-stream" });
    response.end(body);
  } catch {
    response.writeHead(404).end();
  }
});
await new Promise((ok) => server.listen(0, "127.0.0.1", ok));
const origin = `http://127.0.0.1:${server.address().port}`;
const url = `${origin}${PREFIX}`;

let browser;
try {
  browser = await chromium.launch({ executablePath, headless: true, args: ["--no-sandbox"] });
} catch (e) {
  console.error(`smoke.mjs: no Chromium (${executablePath ?? "Playwright's own"}):`);
  console.error(String(e.message).split("\n").slice(0, 3).join("\n"));
  server.close();
  process.exit(3);
}

const failures = [];
const started = performance.now();
let stepStarted = started;
function step(name, problems = []) {
  const ms = performance.now() - stepStarted;
  stepStarted = performance.now();
  failures.push(...problems.map((p) => `${name}: ${p}`));
  console.log(`${problems.length ? "FAIL" : "ok  "} ${name} (${(ms / 1000).toFixed(1)} s)${problems.map((p) => `\n     ${p}`).join("")}`);
}

try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  page.setDefaultTimeout(TIMEOUT);
  const noise = [];
  page.on("console", (m) => m.type() === "error" && noise.push(`console: ${m.text()}`));
  page.on("pageerror", (e) => noise.push(`page error: ${e.message}`));
  page.on("requestfailed", (r) => noise.push(`request failed: ${r.url()} ${r.failure()?.errorText ?? ""}`));
  page.on("response", (r) => r.status() >= 400 && noise.push(`HTTP ${r.status()}: ${r.url()}`));
  const quiet = () => noise.splice(0);

  /** Waits for the compile that `action` starts to finish. */
  async function compiled(action) {
    await page.evaluate(() => {
      const status = document.querySelector("#compile-status");
      if (status) status.textContent = "";
    });
    await action();
    await page.waitForFunction(() => /^Compiled in /.test(document.querySelector("#compile-status")?.textContent ?? ""));
  }

  await compiled(() => page.goto(url));
  step("loads and compiles", quiet());

  const examples = await page.$$eval("#example option", (os) => os.map((o) => [o.value, o.textContent]));
  for (const [value, title] of examples) {
    const problems = [];
    if (value !== (await page.$eval("#example", (e) => e.value))) {
      await compiled(() => page.selectOption("#example", value));
    }
    const tabs = await page.$$eval(".tab", (ts) => ts.map((t) => t.dataset.tab));
    const backends = tabs.filter((t) => t !== "run" && t !== "errors");
    if (backends.length !== BACKENDS) problems.push(`${backends.length} backend tabs, not ${BACKENDS}`);
    for (const backend of backends) {
      await page.click(`#tab-${backend}`);
      const error = await page.$eval("#backend-error", (e) => (e.hidden ? "" : e.textContent));
      const code = await page.$eval("#code .cm-content", (e) => e.textContent ?? "");
      if (error) problems.push(`${backend}: ${error}`);
      else if (!code.trim()) problems.push(`${backend}: no code`);
    }
    const errors = await page.$$eval("#errors .diagnostic.error", (ds) => ds.map((d) => d.textContent));
    problems.push(...errors.map((e) => `error: ${e}`));
    if (await page.$(".cm-lintRange-error")) problems.push("an error underlined in the editor");
    await page.click("#tab-run");
    await page.waitForSelector("#same:not([hidden])");
    const interp = await page.$eval("#interp", (e) => e.textContent ?? "");
    const js = await page.$eval("#js", (e) => e.textContent ?? "");
    const same = await page.$eval("#same", (e) => `${e.dataset.kind}: ${e.textContent}`);
    if (!interp.trim()) problems.push("no interpreter output");
    if (!js.trim()) problems.push("no JavaScript output");
    if (!same.startsWith("good:")) problems.push(same);
    if (/✗|Tests passed: (\d+) of (?!\1\b)/.test(interp + js)) problems.push("a test failed");
    step(`example ${title}`, [...problems, ...quiet()]);
  }

  // Hover: on the first example, over `greet` where it's called.
  await compiled(() => page.selectOption("#example", examples[0][0]));
  const box = await page.evaluate(() => {
    for (const line of document.querySelectorAll("#editor .cm-line")) {
      if (!line.textContent?.startsWith("console.log(greet")) continue;
      const walker = document.createTreeWalker(line, NodeFilter.SHOW_TEXT);
      for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        const i = node.textContent?.indexOf("greet") ?? -1;
        if (i < 0) continue;
        const range = document.createRange();
        range.setStart(node, i + 1);
        range.setEnd(node, i + 3);
        const r = range.getBoundingClientRect();
        return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
      }
    }
    return null;
  });
  if (box) {
    await page.mouse.move(box.x, box.y);
    const type = await page
      .waitForSelector(".cm-tooltip-hover .cm-hover-type", { timeout: 10_000 })
      .then((e) => e.textContent())
      .catch(() => "");
    step("hover shows a type", type.includes("String") ? quiet() : [`hover showed ${JSON.stringify(type)}`, ...quiet()]);
  } else {
    step("hover shows a type", ["no `greet` call to hover over"]);
  }

  // Permalink: an edit goes into the fragment and survives a reload.
  const mark = "// smoke test was here";
  await compiled(async () => {
    await page.click("#editor .cm-content");
    await page.keyboard.press("Control+End");
    await page.keyboard.type(`\n${mark}`);
  });
  await page.waitForFunction(() => location.hash.length > 1);
  await compiled(() => page.reload());
  const text = await page.$eval("#editor .cm-content", (e) => e.textContent ?? "");
  step("permalink survives a reload", text.includes(mark) ? quiet() : ["the edit was lost", ...quiet()]);

  // The REPL tab: the editor's code is input 0; each input's answer
  // ends in a result line, as `temper repl` prints it.
  await page.click("#view-repl");
  const idle = () => page.waitForFunction(() => {
    const t = document.querySelector("#repl-transcript");
    return t?.getAttribute("aria-busy") === "false" && !document.querySelector("#repl-status")?.textContent;
  });
  await idle();
  /** Types `lines` into the cell, Enter after each, and waits for an answer. */
  async function run(...lines) {
    const before = await page.$$eval("#repl-transcript .repl-result", (rs) => rs.length);
    await page.click("#repl-cell .cm-content");
    for (const line of lines) {
      await page.keyboard.type(line);
      await page.keyboard.press("Enter");
    }
    await page.waitForFunction((n) => document.querySelectorAll("#repl-transcript .repl-result").length > n, before);
    await idle();
    return page.$eval("#repl-transcript .repl-block:last-child", (b) => b.textContent ?? "");
  }
  const lastResult = () => page.$eval("#repl-transcript .repl-block:last-child .repl-result", (r) => r.textContent ?? "");
  const replStep = async (name, check) => {
    try {
      step(name, [...(await check()), ...quiet()]);
    } catch (e) {
      step(name, [String(e.message ?? e).split("\n")[0], ...quiet()]);
    }
  };
  const expect = (what, text, pattern) => (pattern.test(text) ? [] : [`${what}: ${JSON.stringify(text.slice(-300))}`]);

  await replStep("REPL: the editor's tests run as input 0", async () => {
    // The first example, in the editor, has a test.
    const input0 = await page.$eval("#repl-transcript", (t) => t.textContent ?? "");
    const errors = await page.$$eval("#repl-transcript .repl-failure, #repl-transcript .repl-diagnostic.error", (es) => es.length);
    return [
      ...expect("input 0", input0, /Tests passed: (\d+) of \1(?!\d)[^]*interactive#0: void/),
      ...(errors ? [`${errors} failures or errors`] : []),
    ];
  });
  await replStep("REPL: 1 + 2", async () => {
    await run("1 + 2");
    return expect("result", await lastResult(), /^interactive#\d+: 3$/);
  });
  let sqInput = 0;
  await replStep("REPL: a function over several lines", async () => {
    // Enter inside the braces starts a new line; the closing one runs it.
    await run("let sq(n: Int): Int {", "n * n", "}");
    const declared = await page.$eval("#repl-transcript .repl-block:last-child", (b) => Number(b.dataset.input));
    sqInput = declared;
    await run("sq(7)");
    return expect("result", await lastResult(), /: 49$/);
  });
  await replStep("REPL: logs stream while the input runs", async () => {
    await page.click("#repl-cell .cm-content");
    await page.keyboard.type('console.log("early"); var n = 0; while (n < 3000000) { n += 1 }; console.log("late"); n');
    await page.keyboard.press("Enter");
    // "early" shows while the loop still runs.
    await page.waitForSelector("#repl-transcript .repl-streamed .repl-log");
    const busy = await page.$eval("#repl-transcript", (t) => t.getAttribute("aria-busy"));
    await page.waitForFunction(() => !document.querySelector("#repl-transcript .repl-streamed"));
    await idle();
    const logs = await page.$$eval("#repl-transcript .repl-block:last-child .repl-log", (ls) => ls.map((l) => l.textContent));
    return [
      ...(busy === "true" ? [] : ["the log showed only after the input finished"]),
      ...(logs.join() === "early,late" ? [] : [`logs ${JSON.stringify(logs)}`]),
      ...expect("result", await lastResult(), /: 3000000$/),
    ];
  });
  await replStep("REPL: Stop restores the session", async () => {
    await page.click("#repl-cell .cm-content");
    // An empty loop runs out of its 50 million steps in well under a
    // second; Stop it as it keeps going with more.
    await page.keyboard.type("while (true) {}");
    await page.keyboard.press("Enter");
    await page.waitForSelector("#repl-transcript .repl-block:last-child .repl-more");
    await idle();
    await page.click("#repl-transcript .repl-block:last-child .repl-more");
    await page.waitForFunction(() => document.querySelector("#repl-transcript")?.getAttribute("aria-busy") === "true");
    await page.waitForTimeout(300);
    await page.click("#repl-stop");
    const stopped = await page.$eval("#repl-transcript .repl-block:last-child", (b) => b.textContent ?? "");
    await idle();
    const stoppedAt = Number(/interactive#(\d+): stopped/.exec(stopped)?.[1] ?? -1);
    await run("sq(3)");
    const after = await page.$eval("#repl-transcript .repl-block:last-child", (b) => Number(b.dataset.input));
    return [
      ...expect("stopped", stopped, /interactive#\d+: stopped/),
      ...expect("after", await lastResult(), /: 9$/),
      ...(after === stoppedAt + 1 ? [] : [`the next input is #${after}, not #${stoppedAt + 1}`]),
    ];
  });
  await replStep("REPL: out of steps, then keep going", async () => {
    await run("var m = 0; while (m < 15000000) { m += 1 }; m");
    const first = await lastResult();
    const more = await page.$("#repl-transcript .repl-block:last-child .repl-more");
    if (!more) return [`no "Keep going" after ${JSON.stringify(first)}`];
    await more.click();
    await page.waitForFunction(() => document.querySelector("#repl-transcript .repl-block:last-child .repl-result"));
    await idle();
    return [...expect("first run", first, /: fail$/), ...expect("kept going", await lastResult(), /: 15000000$/)];
  });
  await replStep("REPL: translate", async () => {
    const text = await run(`translate(${sqInput}, "py")`);
    return [...expect("translation", text, /def _?sq\b/), ...expect("result", await lastResult(), /: void$/)];
  });

  /** Empties the cell, with any tooltip closed. */
  async function clearCell() {
    await page.click("#repl-cell .cm-content");
    await page.keyboard.press("Escape");
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("Backspace");
  }
  await replStep("REPL: completion in the cell", async () => {
    await clearCell();
    await page.keyboard.type("s");
    await page.waitForSelector("#repl-cell .cm-tooltip-autocomplete li");
    const names = await page.$$eval("#repl-cell .cm-tooltip-autocomplete li .cm-completionLabel", (ls) => ls.map((l) => l.textContent));
    await clearCell();
    await page.keyboard.type("runT");
    await page.waitForSelector("#repl-cell .cm-tooltip-autocomplete li");
    const own = await page.$$eval("#repl-cell .cm-tooltip-autocomplete li .cm-completionLabel", (ls) => ls.map((l) => l.textContent));
    // The first is picked: Enter accepts it.
    await page.keyboard.press("Enter");
    const accepted = await page.$eval("#repl-cell .cm-content", (c) => c.textContent ?? "");
    await clearCell();
    // So does Tab, after `.`, where `console` has `log`.
    await page.keyboard.type("console.");
    await page.waitForSelector("#repl-cell .cm-tooltip-autocomplete li");
    const members = await page.$$eval("#repl-cell .cm-tooltip-autocomplete li .cm-completionLabel", (ls) => ls.map((l) => l.textContent));
    await page.keyboard.press("Tab");
    const tabbed = await page.$eval("#repl-cell .cm-content", (c) => c.textContent ?? "");
    await clearCell();
    // A name typed in full, with the menu open, runs on Enter.
    const before = await page.$$eval("#repl-transcript .repl-result", (rs) => rs.length);
    await page.keyboard.type("sq");
    await page.waitForSelector("#repl-cell .cm-tooltip-autocomplete li");
    await page.keyboard.press("Enter");
    await page.waitForFunction((n) => document.querySelectorAll("#repl-transcript .repl-result").length > n, before);
    await idle();
    const ran = await lastResult();
    return [
      ...(names.includes("sq") ? [] : [`no sq in ${JSON.stringify(names.slice(0, 12))}`]),
      ...(own.includes("runTests") ? [] : [`no runTests in ${JSON.stringify(own)}`]),
      ...(accepted === "runTests" ? [] : [`accepted ${JSON.stringify(accepted)}`]),
      ...(members.includes("log") ? [] : [`no log after console. in ${JSON.stringify(members)}`]),
      ...(tabbed === "console.log" ? [] : [`Tab accepted ${JSON.stringify(tabbed)}`]),
      ...expect("sq, run", ran, /^interactive#\d+: /),
    ];
  });
  await replStep("REPL: hover and signature help in the cell", async () => {
    await clearCell();
    await page.keyboard.type("sq(2)");
    await page.keyboard.press("Escape");
    // The pointer over `sq`.
    const at = await page.$eval("#repl-cell .cm-line", (line) => {
      const walker = document.createTreeWalker(line, NodeFilter.SHOW_TEXT);
      const text = walker.nextNode();
      const range = document.createRange();
      range.setStart(text, 0);
      range.setEnd(text, 1);
      const r = range.getBoundingClientRect();
      return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
    });
    await page.mouse.move(at.x, at.y);
    await page.waitForSelector("#repl-cell .cm-tooltip-hover");
    const pointer = await page.$eval("#repl-cell .cm-tooltip-hover", (t) => t.textContent ?? "");
    await page.mouse.move(0, 0);
    await page.waitForSelector("#repl-cell .cm-tooltip-hover", { state: "detached" });
    // Ctrl+I at the cursor, in the name.
    await page.keyboard.press("Home");
    await page.keyboard.press("ControlOrMeta+i");
    await page.waitForSelector("#repl-cell .cm-tooltip-hover .cm-hover");
    const keyed = await page.$eval("#repl-cell .cm-tooltip-hover .cm-hover", (t) => t.textContent ?? "");
    await page.keyboard.press("Escape");
    await clearCell();
    await page.keyboard.type("sq(");
    await page.waitForSelector("#repl-cell .cm-signature");
    const signature = await page.$eval("#repl-cell .cm-signature", (t) => t.textContent ?? "");
    await clearCell();
    return [
      ...expect("hover", pointer, /sq\(n: Int32\): Int32/),
      ...expect("Ctrl+I", keyed, /sq\(n: Int32\): Int32/),
      ...expect("signature", signature, /^sq\(n: Int32\): Int32$/),
    ];
  });
  await replStep("REPL: an interactive#N link puts that input in the cell", async () => {
    await run(`describe(${sqInput}, "module")`);
    const link = await page.$(`#repl-transcript .repl-block:last-child .repl-ref`);
    if (!link) return ["no link"];
    await link.click();
    const cell = await page.$eval("#repl-cell .cm-content", (c) => c.textContent ?? "");
    await clearCell();
    return expect("cell", cell, /^let sq\(n: Int\): Int \{/);
  });
  await replStep("REPL: runTests after a redefinition", async () => {
    await clearCell();
    await run('let greet(name: String): String { "Hi, ${name}" }');
    const text = await run("runTests()");
    return [
      ...expect("tests", text, /Test failed: greets by name[^]*Tests passed: 0 of 1/),
      ...expect("result", await lastResult(), /: void$/),
    ];
  });

  // The Temper link: the header's logo, to the site, one level up.
  const link = await page.$eval("header.top a.brand", (a) => ({
    href: a.getAttribute("href"),
    resolved: a.href,
    logo: a.querySelector("svg.logo")?.getAttribute("aria-label") ?? null,
  }));
  const linkProblems = [];
  if (link.href !== "../" || link.resolved !== `${origin}/site/`) linkProblems.push(`href ${link.href} (${link.resolved})`);
  if (link.logo !== "Temper") linkProblems.push("no Temper logo");
  step("Temper link", linkProblems);

  // The site's header, shared with the docs pages (site/shared).
  const SECTIONS = ["Guide", "Language", "Std", "Tools", "Backends", "Contributing", "Internals", "Release notes", "Playground"];
  const KEY = "temper-theme";
  const headerProblems = [];
  const nav = await page.$$eval("header.top nav a", (as) => as.map((a) => [a.textContent, a.getAttribute("aria-current")]));
  if (nav.map(([t]) => t).join() !== SECTIONS.join()) headerProblems.push(`sections ${JSON.stringify(nav.map(([t]) => t))}`);
  const current = nav.filter(([, c]) => c).map(([t, c]) => `${t}=${c}`);
  if (current.join() !== "Playground=page") headerProblems.push(`current ${JSON.stringify(current)}`);
  if (await page.$("header.top .brand + .menu-button + nav#sections + .top-tools > .theme-toggle") === null) headerProblems.push("not logo, Menu, sections, then tools");
  if (await page.$("footer")) headerProblems.push("a footer");
  step("shared header", [...headerProblems, ...quiet()]);

  // About: from the keyboard, Enter opens it and Escape closes it.
  const aboutProblems = [];
  await page.focus("header.top button[popovertarget=about]");
  await page.keyboard.press("Enter");
  const about = await page.$eval("#about", (a) => (a.matches(":popover-open") ? a.textContent ?? "" : null));
  if (about === null) aboutProblems.push("Enter didn't open it");
  else for (const want of ["Apache-2.0 OR MIT", "on GitHub", "Built from", "MiB of wasm"]) if (!about.includes(want)) aboutProblems.push(`no ${JSON.stringify(want)}`);
  if (about !== null && !/Temper \d+\.\d+\.\d+/.test(about)) aboutProblems.push("no version");
  await page.keyboard.press("Escape");
  if (await page.$eval("#about", (a) => a.matches(":popover-open"))) aboutProblems.push("Escape didn't close it");
  step("About", [...aboutProblems, ...quiet()]);

  // The theme: the system's at first; the toggle cycles light, dark,
  // and system, kept under KEY across a reload.
  const themeOf = () => page.evaluate((key) => [document.documentElement.dataset.theme ?? "system", localStorage.getItem(key)], KEY);
  const themeProblems = [];
  const startTheme = await themeOf();
  if (startTheme.join() !== "system,") themeProblems.push(`started as ${JSON.stringify(startTheme)}`);
  await page.click("header.top .theme-toggle");
  const light = await themeOf();
  if (light.join() !== "light,light") themeProblems.push(`after a click ${JSON.stringify(light)}`);
  await page.click("header.top .theme-toggle");
  await compiled(() => page.reload());
  const kept = await themeOf();
  const label = await page.$eval("header.top .theme-toggle", (b) => b.getAttribute("aria-label"));
  if (kept.join() !== "dark,dark" || label !== "Theme: dark") themeProblems.push(`after a reload ${JSON.stringify([...kept, label])}`);
  const bg = await page.$eval("header.top", (h) => getComputedStyle(h).backgroundColor);
  if (bg !== "rgb(20, 23, 31)") themeProblems.push(`the header's background in dark is ${bg}`);
  await page.click("header.top .theme-toggle");
  const back = await themeOf();
  if (back.join() !== "system,") themeProblems.push(`back to the system's: ${JSON.stringify(back)}`);
  step("theme toggle", [...themeProblems, ...quiet()]);

  // A phone: the sections behind Menu, the controls still in view.
  await page.setViewportSize({ width: 390, height: 844 });
  const phoneProblems = [];
  const visible = (selector) => page.$eval(selector, (e) => {
    const r = e.getBoundingClientRect();
    return r.width > 0 && r.height > 0 && r.left >= 0 && r.right <= innerWidth && getComputedStyle(e).visibility !== "hidden";
  });
  if (await visible("nav#sections")) phoneProblems.push("the sections show before Menu");
  await page.click("header.top .menu-button");
  if (!(await visible("nav#sections a.play"))) phoneProblems.push("Menu didn't show the sections");
  await page.keyboard.press("Escape");
  if (await visible("nav#sections")) phoneProblems.push("Escape didn't close the menu");
  for (const control of ["#view-editor", "#view-repl", "#example", "#share", "header.top .theme-toggle", "header.top button[popovertarget=about]"]) {
    if (!(await visible(control))) phoneProblems.push(`${control} out of view`);
  }
  const wide = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  if (wide > 0) phoneProblems.push(`${wide} px of sideways scrolling`);
  step("phone header", [...phoneProblems, ...quiet()]);
  await page.setViewportSize({ width: 1280, height: 800 });

  // With the docs site: the same header there, and the theme carries.
  if (site) {
    const siteProblems = [];
    /** The header, its links resolved, without what differs by page. */
    const shared = () => page.$eval("header.top", (h) => {
      const copy = h.cloneNode(true);
      copy.classList.remove("compact");
      for (const a of copy.querySelectorAll("a[href]")) a.setAttribute("href", new URL(a.getAttribute("href"), location.href).href);
      for (const e of copy.querySelectorAll("[aria-current]")) e.removeAttribute("aria-current");
      copy.querySelector("[popovertarget=about]")?.remove();
      return copy.outerHTML;
    });
    const playHeader = await shared();
    await page.goto(`${origin}${SITE_PREFIX}docs/guide/`);
    const docsHeader = await shared();
    if (docsHeader !== playHeader) siteProblems.push("the docs' header differs from the playground's");
    const docsCurrent = await page.$$eval("header.top nav a[aria-current]", (as) => as.map((a) => a.textContent));
    if (docsCurrent.join() !== "Guide") siteProblems.push(`the docs' current section ${JSON.stringify(docsCurrent)}`);
    if (!(await page.$("footer.foot"))) siteProblems.push("no footer on the docs page");
    await page.click("header.top .theme-toggle");
    await page.click("header.top .theme-toggle");
    const docsTheme = await themeOf();
    await compiled(() => page.click("header.top nav a.play"));
    const playTheme = await themeOf();
    if (docsTheme.join() !== "dark,dark" || playTheme.join() !== "dark,dark") {
      siteProblems.push(`dark on the docs (${docsTheme}) isn't on the playground (${playTheme})`);
    }
    await page.click("header.top .theme-toggle");
    await page.click("header.top .brand");
    await page.waitForLoadState();
    const homeTheme = await themeOf();
    if (homeTheme.join() !== "system,") siteProblems.push(`the system's on the playground isn't on the docs' home (${homeTheme})`);
    step("docs site: same header, theme carries", [...siteProblems, ...quiet()]);
  } else {
    console.log("skip docs site: same header, theme carries (no --site)");
  }
} catch (e) {
  step("unexpected", [String(e.message ?? e).split("\n")[0]]);
} finally {
  await browser.close();
  server.close();
}

const seconds = ((performance.now() - started) / 1000).toFixed(1);
if (failures.length) {
  console.error(`smoke.mjs: ${failures.length} problems, in ${seconds} s`);
  process.exit(1);
}
console.log(`smoke.mjs: all passed in ${seconds} s`);
