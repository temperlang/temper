# /// script
# requires-python = ">=3.11"
# ///
"""Builds the VS Code extension with a fresh temper and installs it.

    uv run editors/vscode/install.py [--no-build] [--profile release|small] [--deps DIR]

1. Builds temper (`cargo build --locked --release -p tempest`, or with
   `--profile small`, the size-optimized build that releases use), unless
   `--no-build`, which reuses the binary already built.
2. Builds this platform's package with that temper bundled (build.py
   `--temper`), into out/temper-TARGET-VERSION.vsix; `--deps DIR` passes
   on where its npm packages go.
3. Installs it with `code --install-extension PATH --force`. From a WSL
   terminal, `code` installs into the WSL-connected VS Code; in a dev
   container without `code` on PATH, the VS Code server's own
   `code-server` under ~/.vscode-server is used if there is one. With
   neither, it prints the package's path and how to install it by hand.

Then run "Developer: Reload Window" in VS Code to load the new version.
"""

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
from build import OUT, host_target  # noqa: E402


def run(*command: str | Path, cwd: Path = ROOT) -> None:
    print("+", " ".join(str(c) for c in command), flush=True)
    subprocess.run([str(c) for c in command], cwd=cwd, check=True)


def installer() -> list[str] | None:
    """A command that installs a .vsix: `code`, else the newest VS Code
    server's `code-server` (dev containers, remote hosts)."""
    code = shutil.which("code")
    if code:
        return [code]
    servers = [
        *Path.home().glob(".vscode-server/bin/*/bin/code-server"),
        *Path.home().glob(".vscode-server/cli/servers/*/server/bin/code-server"),
    ]
    servers = [s for s in servers if s.is_file()]
    if servers:
        return [str(max(servers, key=lambda s: s.stat().st_mtime))]
    return None


def by_hand(vsix: Path) -> None:
    print(f"\nThe package is {vsix}")
    print('In VS Code: Extensions, "...", "Install from VSIX...", pick it, then run')
    print('"Developer: Reload Window".')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--no-build", action="store_true", help="reuse the temper already built")
    parser.add_argument("--profile", choices=["release", "small"], default="release", help="temper's cargo profile")
    parser.add_argument("--deps", type=Path, help="where build.py installs the npm packages")
    args = parser.parse_args()
    temper = ROOT / "target" / args.profile / ("temper.exe" if sys.platform == "win32" else "temper")
    if not args.no_build:
        run("cargo", "build", "--locked", "--profile", args.profile, "-p", "tempest")
    if not temper.is_file():
        sys.exit(f"install.py: no {temper}; build it, or drop --no-build")
    build = [sys.executable, HERE / "build.py", "--temper", temper]
    if args.deps:
        build += ["--deps", args.deps.resolve()]
    run(*build)
    packages = sorted(OUT.glob(f"temper-{host_target()}-*.vsix"), key=lambda p: p.stat().st_mtime)
    if not packages:
        sys.exit(f"install.py: build.py wrote no package for {host_target()} in {OUT}")
    vsix = packages[-1]
    command = installer()
    if command is None:
        print("\nNo `code` on PATH, nor a VS Code server's code-server, to install it with.")
        by_hand(vsix)
        return
    try:
        run(*command, "--install-extension", vsix, "--force")
    except subprocess.CalledProcessError:
        print(f"\n{Path(command[0]).name} couldn't install it.")
        by_hand(vsix)
        sys.exit(1)
    print('\nInstalled. In VS Code, run "Developer: Reload Window" to load it.')


if __name__ == "__main__":
    main()
