# /// script
# requires-python = ">=3.11"
# ///
"""Installs the checkers CI runs on generated code, and Mocha, and puts them on PATH.

Pinned by default, as PR CI runs them:

- TypeScript (tsc), pyright, and Mocha from package.json and
  package-lock.json here, by `npm ci --ignore-scripts`
- mypy at MYPY, by `uv tool install`
- LuaLS at LUALS, its download checked against LUALS_SHA256, on the
  platforms with a recorded hash; elsewhere it's left out, with a
  message, and the workflow lets the tests skip it (TEMPER_ALLOW_MISSING)

Runs on Linux, macOS, and Windows. On Windows, npm's tools are .cmd
files, which Rust's Command (the tests') doesn't find by name, so their
paths are set as TSC, PYRIGHT, and MOCHA for later steps.

With `--latest NAME` (typescript, pyright, mypy, or luals), that one
checker is the latest release instead, ahead of the pinned one on PATH,
for the nightly early-warning job. Each run prints the versions it
installed, and adds them to the job summary. Bump a pin by editing it
here (mypy, LuaLS) or package.json and `npm install --package-lock-only
--ignore-scripts` (the rest); docs/contributing/ci.md has the details.
"""

import argparse
import hashlib
import os
import re
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile
from pathlib import Path

MYPY = "2.4.0"
LUALS = "3.19.2"
# By LuaLS's name for each platform, as in its release's assets
# (lua-language-server-VERSION-PLATFORM.tar.gz, or .zip on Windows). Only
# Linux x64's is recorded; record another platform's `sha256sum` of its
# asset here to check LuaLS there too (docs/contributing/ci.md). LuaLS has
# no Windows Arm64 build.
LUALS_SHA256 = {
    "linux-x64": "93ad326b10f6c00911dd8c450fa098332dbd8f9f45d1052f5636a2d54479b947",
}
LUALS_RELEASES = "https://github.com/LuaLS/lua-language-server/releases"

HERE = Path(__file__).resolve().parent
LATEST = ("typescript", "pyright", "mypy", "luals")


def run(*args: str | Path, **kwargs) -> str:
    print("+", " ".join(str(a) for a in args), flush=True)
    # By its path, so Windows finds npm.cmd and the like by name.
    program = shutil.which(args[0]) or str(args[0])
    return subprocess.run([program, *(str(a) for a in args[1:])], check=True, text=True, stdout=subprocess.PIPE,
                          **kwargs).stdout


def first_line(*args: str | Path) -> str:
    return run(*args).strip().splitlines()[0]


def add_path(path: Path, paths: list[Path]) -> None:
    """Puts `path` ahead of the others on PATH, for this script and later steps."""
    paths.append(path)
    os.environ["PATH"] = f"{path}{os.pathsep}{os.environ['PATH']}"


def latest_luals() -> str:
    """The tag that releases/latest redirects to."""
    request = urllib.request.Request(f"{LUALS_RELEASES}/latest", method="HEAD")
    with urllib.request.urlopen(request, timeout=60) as response:
        tag = response.url.rstrip("/").rsplit("/", 1)[-1]
    if not re.fullmatch(r"\d+\.\d+\.\d+", tag):
        sys.exit(f"install.py: unexpected LuaLS release tag {tag!r}")
    return tag


def luals_platform() -> str:
    """LuaLS's name for this platform, as in its release's assets."""
    system = {"Linux": "linux", "Darwin": "darwin", "Windows": "win32"}[platform.system()]
    arch = {"x86_64": "x64", "amd64": "x64", "aarch64": "arm64", "arm64": "arm64"}[platform.machine().lower()]
    return f"{system}-{arch}"


def install_luals(version: str, sha256: str | None, dest: Path) -> Path:
    suffix = "zip" if platform.system() == "Windows" else "tar.gz"
    name = f"lua-language-server-{version}-{luals_platform()}.{suffix}"
    url = f"{LUALS_RELEASES}/download/{version}/{name}"
    print("+ download", url, flush=True)
    with urllib.request.urlopen(url, timeout=300) as response:
        data = response.read()
    digest = hashlib.sha256(data).hexdigest()
    if sha256 is None:
        print(f"LuaLS {version}: sha256 {digest} (latest; not checked)")
    elif digest != sha256:
        sys.exit(f"install.py: {name} has sha256 {digest}, not the recorded {sha256}")
    dest.mkdir(parents=True, exist_ok=True)
    archive = dest / name
    archive.write_bytes(data)
    if suffix == "zip":
        with zipfile.ZipFile(archive) as z:
            z.extractall(dest)
    else:
        with tarfile.open(archive) as tar:
            tar.extractall(dest, filter="data")
    archive.unlink()
    return dest / "bin"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--latest", choices=LATEST, help="this checker at its latest release")
    parser.add_argument("--dir", type=Path, help="where to install the latest checker and LuaLS (default $RUNNER_TEMP)")
    args = parser.parse_args()
    base = (args.dir or Path(os.environ.get("RUNNER_TEMP") or tempfile.mkdtemp())).resolve() / "ci-tools"
    paths: list[Path] = []

    # TypeScript, pyright, and Mocha, from the lockfile; no install scripts.
    run("npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund", cwd=HERE)
    add_path(HERE / "node_modules/.bin", paths)
    if args.latest in ("typescript", "pyright"):
        latest = base / "npm-latest"
        latest.mkdir(parents=True, exist_ok=True)
        run("npm", "install", "--prefix", latest, "--ignore-scripts", "--no-audit", "--no-fund",
            "--no-save", f"{args.latest}@latest")
        add_path(latest / "node_modules/.bin", paths)

    # mypy, as a uv tool.
    mypy = "mypy" if args.latest == "mypy" else f"mypy=={MYPY}"
    run("uv", "tool", "install", "--force", mypy)
    add_path(Path(run("uv", "tool", "dir", "--bin").strip()), paths)

    # LuaLS, checked against its recorded hash unless it's the latest,
    # where one is recorded.
    luals = True
    if args.latest == "luals":
        add_path(install_luals(latest_luals(), None, base / "luals-latest"), paths)
    elif sha256 := LUALS_SHA256.get(luals_platform()):
        add_path(install_luals(LUALS, sha256, base / "luals"), paths)
    else:
        luals = False
        print(f"LuaLS: left out, no recorded SHA-256 for {luals_platform()} (LUALS_SHA256)", flush=True)

    versions = {
        "TypeScript": first_line("tsc", "--version"),
        "pyright": first_line("pyright", "--version"),
        "mypy": first_line("mypy", "--version"),
        "LuaLS": first_line("lua-language-server", "--version") if luals else "left out on this platform",
        "Mocha": first_line("mocha", "--version"),
    }
    lines = [f"{k}: {v}" + (" (latest)" if args.latest and args.latest in k.lower() else "") for k, v in versions.items()]
    print("\n".join(lines))
    if github_path := os.environ.get("GITHUB_PATH"):
        # Each line is put ahead of the earlier ones, so the latest checker wins.
        with open(github_path, "a") as f:
            f.writelines(f"{p}\n" for p in paths)
    if (github_env := os.environ.get("GITHUB_ENV")) and platform.system() == "Windows":
        with open(github_env, "a") as f:
            for var, name in (("TSC", "tsc"), ("PYRIGHT", "pyright"), ("MOCHA", "mocha")):
                f.write(f"{var}={shutil.which(name)}\n")
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(summary, "a") as f:
            f.write("### Checkers\n\n" + "".join(f"- {line}\n" for line in lines))
    if output := os.environ.get("GITHUB_OUTPUT"):
        with open(output, "a") as f:
            f.write(f"versions={'; '.join(lines)}\n")


if __name__ == "__main__":
    main()
