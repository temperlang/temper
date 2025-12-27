import assert from "assert";
import { execSync } from "child_process";
import { existsSync, mkdirSync, cpSync } from "fs";
import { basename, join } from "path";

// Set true to debug
const debug = false;
if (debug) {
  // Dump the contents of node_modules which helps figure
  // out what is where.
  // TODO Replace with pure js option?
  // execSync("find node_modules", { stdio: "inherit" });
}

// Copy JS and CSS files installed via npm,
// along with any images or fonts used by that CSS,
// into the docs/ext directory.

// This should be run in the same directory as package.json
// and after `npm install`.

assert(existsSync("package.json"));
assert(existsSync("node_modules"));

// Make sure we have a destination directory
mkdirSync("temper-docs/docs/ext/", { recursive: true });

const copyTo = (to, froms) => {
  for (const from of froms) {
    if (debug) {
      console.log(`copy from ${from} to ${to}`);
    }
    cpSync(from, join(to, basename(from)), { recursive: true });
  }
};

// Needed by slick carousel
copyTo("temper-docs/docs/ext/", [
  "node_modules/jquery/dist/jquery.min.js",
  ...["slick.css", "slick-theme.css", "slick.min.js", "ajax-loader.gif"].map(
    (name) => `node_modules/slick-carousel/slick/${name}`
  ),
]);
copyTo("temper-docs/docs/ext/", ["node_modules/slick-carousel/slick/fonts/"]);

// Needed by SVG railroad diagrams
copyTo("temper-docs/docs/ext/", [
  "node_modules/@prantlf/railroad-diagrams/railroad-diagrams.css",
]);
