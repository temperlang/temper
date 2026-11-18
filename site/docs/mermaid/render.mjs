// Draws Mermaid diagrams as SVG, for the docs site: mermaid's browser
// bundle in one headless Chromium page, driven by playwright-core, with
// every request blocked. site/docs/build.py runs it.
//
//   node render.mjs JOBS.json [--chromium PATH]
//
// JOBS.json is a list of {"id", "theme", "source", "out"}: the SVG's id
// (unique on its page), "light" or "dark", the diagram's Mermaid source,
// and the file to write the SVG to. Chromium is PATH, else
// $TEMPER_CHROMIUM, else Playwright's own (`npx playwright-core
// install --only-shell chromium` here). Exits with 1 if any diagram
// fails, after drawing the rest, and with 3 if there's no Chromium.

import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright-core";

// The site's palette, from site/docs/style.css, on mermaid's `base` theme,
// which derives the rest. Fonts are the site's, so labels measure close to
// how readers see them; labels are SVG text, not HTML, so a wider font
// spills over a box's edge instead of being cut off.
const FONT = 'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
const THEMES = {
  light: {
    darkMode: false,
    background: "#ffffff",
    primaryColor: "#f5f6f9",
    primaryBorderColor: "#2b3a55",
    primaryTextColor: "#1d2433",
    secondaryColor: "#fbeeee",
    tertiaryColor: "#eef3fb",
    lineColor: "#5b6475",
    textColor: "#1d2433",
    noteBkgColor: "#fff8e1",
    noteTextColor: "#1d2433",
    noteBorderColor: "#9a6700",
  },
  dark: {
    darkMode: true,
    background: "#14171f",
    primaryColor: "#1d222d",
    primaryBorderColor: "#c9d3e8",
    primaryTextColor: "#e2e6ee",
    secondaryColor: "#3a2a2e",
    tertiaryColor: "#1f2a3d",
    lineColor: "#9aa3b5",
    textColor: "#e2e6ee",
    noteBkgColor: "#3a3220",
    noteTextColor: "#e2e6ee",
    noteBorderColor: "#d4a72c",
  },
};

const args = process.argv.slice(2);
const at = args.indexOf("--chromium");
const executablePath = (at >= 0 ? args.splice(at, 2)[1] : null) || process.env.TEMPER_CHROMIUM || undefined;
if (args.length !== 1) {
  console.error("usage: node render.mjs JOBS.json [--chromium PATH]");
  process.exit(2);
}
const jobs = JSON.parse(await readFile(args[0], "utf8"));
const bundle = fileURLToPath(new URL("node_modules/mermaid/dist/mermaid.min.js", import.meta.url));

let browser;
try {
  browser = await chromium.launch({ executablePath, headless: true, args: ["--no-sandbox"] });
} catch (e) {
  console.error(`render.mjs: no Chromium to draw diagrams with (${executablePath ?? "Playwright's own"}):`);
  console.error(String(e.message).split("\n").slice(0, 3).join("\n"));
  process.exit(3);
}
let failed = 0;
try {
  const page = await browser.newPage();
  await page.route("**/*", (route) => route.abort());
  await page.setContent("<!doctype html><html><body></body></html>");
  await page.addScriptTag({ path: bundle });
  for (const job of jobs) {
    const result = await page.evaluate(
      async ({ id, source, vars, font }) => {
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: "strict",
          theme: "base",
          themeVariables: { ...vars, fontFamily: font },
          fontFamily: font,
          htmlLabels: false,
          flowchart: { htmlLabels: false },
        });
        try {
          return { svg: (await mermaid.render(id, source)).svg };
        } catch (e) {
          document.getElementById("d" + id)?.remove();
          return { error: String(e?.message ?? e) };
        }
      },
      { id: job.id, source: job.source, vars: THEMES[job.theme], font: FONT },
    );
    if (result.error) {
      failed++;
      console.error(`render.mjs: ${job.id}: ${result.error}`);
    } else {
      await writeFile(job.out, result.svg);
    }
  }
} finally {
  await browser.close();
}
process.exit(failed ? 1 : 0);
