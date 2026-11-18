# /// script
# requires-python = ">=3.11"
# ///
"""Builds the playground into site/_out/playground/.

    uv run site/playground/build.py [--deps DIR] [--skip-wasm] [--out DIR]
                                    [--smoke [--chromium PATH]]

1. Builds crates/temper-wasm with the `small-wasm` profile for wasm32 (set
   RUSTUP_TOOLCHAIN to pick the toolchain), unless `--skip-wasm`.
2. Installs the page's npm packages from package-lock.json with `npm ci`,
   into site/playground/node_modules, or with `--deps`, into that
   directory, linked from there, to keep them outside the work tree.
3. Type-checks the TypeScript with tsc, the page and the workers apart.
4. Bundles and minifies it with esbuild, with the examples as text and
   the JavaScript backend's runtime from crates/temper-be-js/runtime/,
   and writes the bundled packages' licenses beside it.
5. Copies in index.html, with the site's header and the About popover
   (site/shared/header.py, as on the docs pages, with the playground
   marked current), site/shared/header.js (the theme and the phone
   menu), and the wasm, and prints each file's size, raw and gzipped.
   main.css imports site/shared/header.css, so esbuild bundles it.
6. Busts caches: every URL the page loads (main.js, main.css, and
   header.js from index.html; the workers and the wasm from main.js)
   gets `?v=HASH`, a
   hash of the built files, so a normal reload picks up a new build. The
   bundles import nothing else, so no other URLs need it.

7. With `--smoke`, runs smoke.mjs on the output: each example in
   headless Chromium (`--chromium PATH`, else $TEMPER_CHROMIUM, else
   Playwright's), with site/docs/mermaid's playwright-core.

Serve the output with `python3 -m http.server -d site/_out/playground`.
"""

import argparse
import filecmp
import gzip
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(HERE.parent / "shared"))
import header as shared  # noqa: E402  (site/shared/header.py: the site's header and About)

WASM = ROOT / "target/wasm32-unknown-unknown/small-wasm/temper_wasm.wasm"
CORE = ROOT / "crates/temper-be-js/runtime/temper-core/index.js"
MERMAID = ROOT / "site/docs/mermaid"
ENTRIES = ["src/main.ts", "src/compiler-worker.ts", "src/repl-worker.ts", "src/js-worker.ts", "src/main.css"]


def run(*command: str | Path, cwd: Path = HERE) -> None:
    print("+", " ".join(str(c) for c in command), flush=True)
    subprocess.run([str(c) for c in command], cwd=cwd, check=True)


def install(deps: Path | None) -> Path:
    """Installs the npm packages, if they changed, giving node_modules."""
    target = deps.resolve() if deps else HERE
    target.mkdir(parents=True, exist_ok=True)
    modules = target / "node_modules"
    installed = modules / ".package-lock.json"
    if deps:
        for name in ["package.json", "package-lock.json"]:
            shutil.copyfile(HERE / name, target / name)
    if not (installed.exists() and lock_matches(target / "package-lock.json", installed)):
        run("npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund", cwd=target)
    if deps:
        link = HERE / "node_modules"
        if link.is_symlink() or not link.exists():
            if link.is_symlink():
                link.unlink()
            link.symlink_to(modules, target_is_directory=True)
    return modules


def lock_matches(lock: Path, installed: Path) -> bool:
    """Whether npm's record of what it installed matches the lockfile."""
    want = json.loads(lock.read_text())["packages"]
    have = json.loads(installed.read_text())["packages"]
    want = {k: v.get("version") for k, v in want.items() if k and not v.get("optional")}
    have = {k: v.get("version") for k, v in have.items()}
    return all(have.get(k) == v for k, v in want.items())


def commit() -> str:
    if sha := os.environ.get("GITHUB_SHA"):
        return sha
    out = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True)
    return out.stdout.strip()


def licenses(meta: dict, modules: Path, out: Path) -> None:
    """The licenses of the npm packages the bundles took code from."""
    packages: set[Path] = set()
    for source in meta["inputs"]:
        parts = Path(source).parts
        if "node_modules" in parts:
            i = len(parts) - 1 - parts[::-1].index("node_modules")
            name = parts[i + 1 : i + 3] if parts[i + 1].startswith("@") else parts[i + 1 : i + 2]
            packages.add(modules.joinpath(*name))
    notes = [
        "The playground bundles these packages, each under its own license.\n"
        "The JavaScript runtime, @temperlang/core, is The Temper Project's,\n"
        "under the repository's licenses (Apache-2.0 OR MIT).\n"
    ]
    for package in sorted(packages):
        info = json.loads((package / "package.json").read_text())
        text = "".join(p.read_text() for p in sorted(package.glob("LICEN[CS]E*")))
        notes.append(f"{'=' * 72}\n{info['name']} {info['version']} ({info.get('license', '?')})\n\n{text}")
    (out / "THIRD-PARTY-NOTICES.txt").write_text("\n".join(notes))


VERSION_SLOT = "@@BUILD_VERSION@@"


def bust_caches(out: Path) -> str:
    """Puts a hash of the built files into each URL that loads them."""
    digest = hashlib.sha256()
    for path in sorted(out.iterdir()):
        if path.suffix != ".map":
            digest.update(path.name.encode() + b"\0" + path.read_bytes())
    version = digest.hexdigest()[:12]
    for name in ["main.js", "index.html"]:
        path = out / name
        text = path.read_text()
        if name == "index.html":
            text = text.replace('"main.css"', f'"main.css?v={VERSION_SLOT}"')
            text = text.replace('"main.js"', f'"main.js?v={VERSION_SLOT}"')
            text = text.replace('"header.js"', f'"header.js?v={VERSION_SLOT}"')
        if VERSION_SLOT not in text:
            raise SystemExit(f"build.py: no {VERSION_SLOT} in {name}")
        path.write_text(text.replace(VERSION_SLOT, version))
    return version


def mib(n: int) -> str:
    return f"{n / 1024 / 1024:.1f} MiB"


def sizes(out: Path) -> None:
    total = total_gz = 0
    for path in sorted(out.iterdir()):
        if path.suffix == ".map":
            continue
        data = path.read_bytes()
        gz = len(gzip.compress(data, 9))
        total, total_gz = total + len(data), total_gz + gz
        print(f"{path.name:28} {len(data):>10,} {gz:>10,} gzipped")
    print(f"{'total (without maps)':28} {total:>10,} {total_gz:>10,} gzipped")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--deps", type=Path, help="install npm packages here, linked from node_modules")
    parser.add_argument("--skip-wasm", action="store_true", help="use the wasm already built")
    parser.add_argument("--out", type=Path, default=ROOT / "site/_out/playground")
    parser.add_argument("--smoke", action="store_true", help="then run smoke.mjs on it in headless Chromium")
    parser.add_argument("--chromium", help="Chromium for --smoke (default $TEMPER_CHROMIUM, else Playwright's)")
    args = parser.parse_args()

    if not args.skip_wasm:
        run("cargo", "build", "--locked", "--profile", "small-wasm", "--target", "wasm32-unknown-unknown",
            "-p", "temper-wasm", cwd=ROOT)
    modules = install(args.deps)
    bin = modules / ".bin"
    run(bin / "tsc", "-p", "tsconfig.json")
    run(bin / "tsc", "-p", "tsconfig.worker.json")

    out = args.out.resolve()
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    wasm = WASM.read_bytes()
    meta = out.parent / "playground-meta.json"
    run(bin / "esbuild", *ENTRIES, "--bundle", "--minify", "--format=esm", "--target=es2022",
        "--sourcemap", "--legal-comments=none", "--log-override:duplicate-class-member=silent", "--loader:.temper=text",
        f"--alias:@temperlang/core={CORE}",
        f"--define:BUILD_VERSION={json.dumps(VERSION_SLOT)}",
        f"--metafile={meta}", f"--outdir={out}", "--log-level=warning")
    licenses(json.loads(meta.read_text()), modules, out)
    meta.unlink()
    page = (HERE / "index.html").read_text()
    for slot in ["@@HEADER@@", "@@ABOUT@@"]:
        if slot not in page:
            raise SystemExit(f"build.py: no {slot} in index.html")
    at = "playground/index.html"
    wasm_size = f"The compiler: {mib(len(wasm))} of wasm, {mib(len(gzip.compress(wasm, 9)))} gzipped"
    extra = (
        wasm_size,
        f'<a href="{shared.relative("docs/internals/08-playground.html", at)}">How the playground works</a>',
        '<a href="THIRD-PARTY-NOTICES.txt">Bundled packages\' licenses</a>',
    )
    page = page.replace("@@HEADER@@", shared.header(at, shared.PLAYGROUND, about=True))
    page = page.replace("@@ABOUT@@", shared.about(at, commit(), extra))
    (out / "index.html").write_text(page)
    shutil.copyfile(shared.JS, out / "header.js")
    shutil.copyfile(WASM, out / "temper_wasm.wasm")
    print(f"build version {bust_caches(out)}")
    sizes(out)
    if args.smoke:
        return smoke(out, args.chromium)
    return 0


def smoke(out: Path, chromium: str | None) -> int:
    """Runs smoke.mjs on the built page, with site/docs/mermaid's
    playwright-core, installing it first if its lockfile changed."""
    installed = MERMAID / "node_modules/.package-lock.json"
    if not (installed.exists() and lock_matches(MERMAID / "package-lock.json", installed)):
        run("npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund", cwd=MERMAID)
    command: list[str | Path] = ["node", HERE / "smoke.mjs", out]
    if chromium:
        command += ["--chromium", chromium]
    print("+", " ".join(str(c) for c in command), flush=True)
    return subprocess.run([str(c) for c in command]).returncode


if __name__ == "__main__":
    sys.exit(main())
