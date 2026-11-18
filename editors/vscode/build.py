# /// script
# requires-python = ">=3.11"
# ///
"""Builds, tests, and packages the VS Code extension into out/.

    uv run editors/vscode/build.py [--deps DIR] [--temper PATH] [--binaries DIR] [--test]

1. Installs the npm packages from package-lock.json with `npm ci`, into
   editors/vscode/node_modules, or with `--deps`, into that directory,
   linked from there, to keep them outside the work tree (as the
   playground's build.py does).
2. Type-checks the TypeScript with tsc (the webview's, with the DOM, on
   its own).
3. Bundles the extension with esbuild into out/extension.js (CommonJS for
   VS Code's Node, vscode-languageclient included, `vscode` left to VS
   Code), the preview's webview script into out/preview.js, and the
   tests into out/.
4. Runs the tests of finding temper (src/server.test.ts) and of the
   preview's pure parts (src/page.test.ts) with Node's own test runner,
   the grammars' check (check_grammar.py), and the grammars run by VS
   Code's own TextMate engine over samples and the repository's literate
   files (test/grammar.mjs).
5. Packages with `vsce package`: out/temper-VERSION.vsix, the universal
   package, with no temper; and a platform package,
   out/temper-TARGET-VERSION.vsix, for each temper given, with it in
   bin/: `--temper PATH` for this machine's platform, and `--binaries
   DIR` for each `DIR/temper-RUST_TARGET/temper[.exe]` (as binaries.yml
   unpacks its builds).
6. With `--test`, runs test/suite.ts in a real VS Code (test/run.ts, with
   @vscode/test-electron) on this platform's package, unpacked. VS Code
   is downloaded once, into `.vscode-test/` here, or with `--deps`, in
   that directory. On Linux without a display, it runs under xvfb-run
   if there is one, else Electron's headless Ozone platform.

Install a .vsix with `code --install-extension out/temper-...vsix`, or in
VS Code, Extensions, "...", "Install from VSIX...", then reload the
window. Each build's VERSION is its own (package.json's major and minor,
minutes since 2026 as the patch), so it replaces any older one. See
README.md.
"""

import argparse
import json
import os
import platform
import shutil
import stat
import subprocess
import sys
import time
import zipfile
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = HERE / "out"
# The extension's files, as they go in the package under extension/.
FILES = [
    "package.json",
    "language-configuration.json",
    "markdown-configuration.json",
    "README.md",
    "out/extension.js",
    "out/preview.js",
    "media/preview.css",
    "syntaxes/temper.tmLanguage.json",
    "syntaxes/temper-markdown.tmLanguage.json",
]
# VS Code's platform targets, as `vsce package --target` names them, and
# Rust's for the temper each bundles.
TARGETS = {
    "linux-x64": "x86_64-unknown-linux-gnu",
    "linux-arm64": "aarch64-unknown-linux-gnu",
    "darwin-arm64": "aarch64-apple-darwin",
    "win32-x64": "x86_64-pc-windows-msvc",
    "win32-arm64": "aarch64-pc-windows-msvc",
}
# The VS Code the tests run in, pinned so runs match and its download is
# kept.
VSCODE_VERSION = "1.144.1"
# Relative links in README.md, as the package's details page shows them.
BASE_URL = "https://github.com/temperlang/tempest/blob/main/editors/vscode"


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


def host_target() -> str:
    """This machine's VS Code target, as `linux-x64`."""
    system = {"Linux": "linux", "Darwin": "darwin", "Windows": "win32"}[platform.system()]
    machine = platform.machine().lower()
    arch = {"x86_64": "x64", "amd64": "x64", "aarch64": "arm64", "arm64": "arm64"}[machine]
    return f"{system}-{arch}"


# Every build gets its own version, so installing a new .vsix over an
# older one always replaces it (VS Code keeps an installed version it
# already has): package.json's major and minor, and as the patch, the
# minutes since 2026, the same for every package of one build.
STAMP = (int(time.time()) - int(datetime(2026, 1, 1, tzinfo=timezone.utc).timestamp())) // 60


def build_version(base: str) -> str:
    major, minor, _ = base.split(".", 2)
    return f"{major}.{minor}.{STAMP}"


def package(bin_dir: Path, target: str | None, temper: Path | None) -> Path:
    """Writes a .vsix with `vsce package`, from a staging directory holding
    just what ships, and checks what it holds."""
    meta = json.loads((HERE / "package.json").read_text())
    stage = OUT / "stage" / (target or "universal")
    shutil.rmtree(stage, ignore_errors=True)
    stage.mkdir(parents=True)
    # The package needs only what VS Code reads, not the build's tools.
    shipped = {k: v for k, v in meta.items() if k not in ("devDependencies", "private")}
    meta["version"] = shipped["version"] = build_version(meta["version"])
    shipped["files"] = [f for f in FILES if f not in ("package.json", "README.md")] + ["LICENSE.txt"] + (["bin/"] if temper else [])
    for name in FILES:
        (stage / name).parent.mkdir(parents=True, exist_ok=True)
        if name == "package.json":
            (stage / name).write_text(json.dumps(shipped, indent=2) + "\n")
        else:
            shutil.copyfile(HERE / name, stage / name)
    # The repository's licenses, as one file.
    license = "\n\n".join((ROOT / n).read_text() for n in ["COPYRIGHT", "LICENSE-APACHE", "LICENSE-MIT", "NOTICE"])
    (stage / "LICENSE.txt").write_text(license)
    exe = None
    if temper:
        exe = "bin/temper.exe" if target and target.startswith("win32") else "bin/temper"
        (stage / "bin").mkdir()
        shutil.copyfile(temper, stage / exe)
        (stage / exe).chmod(0o755)
    name = f"{meta['name']}-{target}-{meta['version']}" if target else f"{meta['name']}-{meta['version']}"
    vsix = OUT / f"{name}.vsix"
    vsix.unlink(missing_ok=True)
    targets = ["--target", target] if target else []
    vsce = bin_dir / ("vsce.cmd" if os.name == "nt" else "vsce")
    run(vsce, "package", "--no-dependencies", *targets, "--baseContentUrl", BASE_URL, "--out", vsix, cwd=stage)
    with zipfile.ZipFile(vsix) as z:
        names = set(z.namelist())
        want = {"[Content_Types].xml", "extension.vsixmanifest", *(f"extension/{f}" for f in FILES if f != "README.md")}
        for contribution in meta["contributes"]["grammars"] + meta["contributes"]["languages"]:
            path = contribution.get("path") or contribution.get("configuration")
            if path:
                want.add(f"extension/{path.removeprefix('./')}")
        if exe:
            want.add(f"extension/{exe}")
        missing = want - names
        if missing:
            sys.exit(f"build.py: {vsix.name} lacks {sorted(missing)}")
        if exe and not target.startswith("win32"):
            mode = z.getinfo(f"extension/{exe}").external_attr >> 16
            if not mode & stat.S_IXUSR:
                sys.exit(f"build.py: {vsix.name}'s {exe} isn't executable")
    print(f"{vsix.relative_to(HERE)}: {vsix.stat().st_size:,} bytes, {len(names)} files")
    return vsix


def vscode_test(vsix: Path, cache: Path) -> None:
    """Runs test/suite.ts in a real VS Code with the package unpacked."""
    unpacked = OUT / "test" / "package"
    shutil.rmtree(unpacked, ignore_errors=True)
    with zipfile.ZipFile(vsix) as z:
        z.extractall(unpacked)
        # zipfile drops the mode bits, so the temper in bin/ is restored's.
        for info in z.infolist():
            mode = info.external_attr >> 16
            if mode & stat.S_IXUSR:
                (unpacked / info.filename).chmod(mode & 0o777)
    command: list[str | Path] = ["node", "out/test/run.js", unpacked / "extension", cache, OUT / "test" / "suite.js", VSCODE_VERSION]
    if sys.platform == "linux" and not os.environ.get("DISPLAY") and not os.environ.get("WAYLAND_DISPLAY"):
        xvfb = shutil.which("xvfb-run")
        if xvfb:
            command = [xvfb, "-a", *command]
        else:
            # No display, and no xvfb: Chromium's headless Ozone platform.
            command += ["--ozone-platform=headless", "--disable-gpu"]
    run(*command)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--deps", type=Path, help="install npm packages (and VS Code, for --test) here, outside the work tree")
    parser.add_argument("--temper", type=Path, help="bundle this temper in a package for this machine's platform")
    parser.add_argument("--binaries", type=Path, help="bundle each DIR/temper-RUST_TARGET/temper[.exe] in its platform's package")
    parser.add_argument("--test", action="store_true", help="run the tests in a real VS Code on this platform's package")
    args = parser.parse_args()
    bin_dir = install(args.deps) / ".bin"
    run(bin_dir / "tsc", "-p", "tsconfig.json")
    run(bin_dir / "tsc", "-p", "webview/tsconfig.json")
    shutil.rmtree(OUT, ignore_errors=True)
    OUT.mkdir()
    common = ["--bundle", "--platform=node", "--target=node18", "--log-level=warning"]
    run(bin_dir / "esbuild", "src/extension.ts", *common, "--minify", "--format=cjs", "--external:vscode", "--outfile=out/extension.js")
    run(bin_dir / "esbuild", "webview/preview.ts", "--bundle", "--platform=browser", "--target=es2022", "--log-level=warning", "--minify", "--format=iife", "--outfile=out/preview.js")
    tests = ["server", "page"]
    for name in tests:
        run(bin_dir / "esbuild", f"src/{name}.test.ts", *common, "--format=esm", f"--outfile=out/{name}.test.mjs")
    run(bin_dir / "esbuild", "test/suite.ts", *common, "--format=cjs", "--external:vscode", "--outfile=out/test/suite.js")
    run(bin_dir / "esbuild", "test/run.ts", *common, "--format=cjs", "--outfile=out/test/run.js")
    run("node", "--test", *(f"out/{name}.test.mjs" for name in tests))
    run(sys.executable, HERE / "check_grammar.py")
    run("node", HERE / "test" / "grammar.mjs")

    bundles: dict[str, Path] = {}
    if args.binaries:
        for target, rust in TARGETS.items():
            for exe in ["temper", "temper.exe"]:
                if (args.binaries / f"temper-{rust}" / exe).is_file():
                    bundles[target] = args.binaries / f"temper-{rust}" / exe
    if args.temper:
        bundles[host_target()] = args.temper
    package(bin_dir, None, None)
    built = {target: package(bin_dir, target, temper) for target, temper in bundles.items()}
    if args.test:
        host = built.get(host_target())
        if not host:
            sys.exit(f"build.py: --test needs a temper for {host_target()}: --temper PATH")
        cache = (args.deps.resolve() if args.deps else HERE) / ".vscode-test"
        vscode_test(host, cache)


if __name__ == "__main__":
    main()
