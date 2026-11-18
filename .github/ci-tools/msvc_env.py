# /// script
# requires-python = ">=3.11"
# ///
"""Sets up MSVC for later steps of a Windows job, as a Developer Command Prompt would.

Finds the newest Visual Studio with the C++ tools for this machine's
architecture (vswhere), runs its vcvarsall.bat, and passes on what that
changed: new PATH entries through GITHUB_PATH, other variables through
GITHUB_ENV. Then CMake's generator is Ninja (Visual Studio's own, on
the PATH vcvarsall sets), which, unlike Visual Studio's generators,
puts each program where `temper test -b cpp` and `run -b cpp` look for
it (build/NAME), and its C and C++ compilers are cl. No third-party
action, so nothing more to pin.

    uv run .github/ci-tools/msvc_env.py
"""

import os
import platform
import subprocess
import sys
from pathlib import Path

VSWHERE = Path(os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)")) / "Microsoft Visual Studio/Installer/vswhere.exe"


def main() -> None:
    if platform.system() != "Windows":
        sys.exit("msvc_env.py: Windows only")
    arm = platform.machine().lower() in ("arm64", "aarch64")
    arch, component = ("arm64", "ARM64") if arm else ("x64", "x86.x64")
    found = subprocess.run(
        [VSWHERE, "-latest", "-products", "*", "-requires", f"Microsoft.VisualStudio.Component.VC.Tools.{component}",
         "-property", "installationPath"],
        check=True, text=True, capture_output=True,
    ).stdout.strip().splitlines()
    if not found:
        sys.exit(f"msvc_env.py: no Visual Studio with C++ tools for {arch}")
    vcvars = Path(found[0]) / "VC/Auxiliary/Build/vcvarsall.bat"
    print(f"+ {vcvars} {arch}", flush=True)
    out = subprocess.run(f'"{vcvars}" {arch} >nul && set', shell=True, check=True, text=True,
                         capture_output=True).stdout
    after = dict(line.split("=", 1) for line in out.splitlines() if "=" in line)
    before = {k.upper(): v for k, v in os.environ.items()}
    old_path = {p.lower() for p in os.environ.get("PATH", "").split(os.pathsep)}
    new_path: list[str] = []
    env: dict[str, str] = {}
    for key, value in after.items():
        if key.upper() == "PATH":
            new_path = [p for p in value.split(os.pathsep) if p and p.lower() not in old_path]
        elif before.get(key.upper()) != value:
            env[key] = value
    env |= {"CMAKE_GENERATOR": "Ninja", "CC": "cl", "CXX": "cl"}
    # With VCINSTALLDIR set, rustc takes `link.exe` from PATH, where Git
    # Bash puts its own coreutils `link` first (/usr/bin), so name
    # MSVC's linker outright for cargo's later builds.
    linker = next((Path(p) / "link.exe" for p in new_path if (Path(p) / "link.exe").is_file()), None)
    if linker is None:
        sys.exit("msvc_env.py: vcvarsall's PATH has no link.exe")
    target = "AARCH64" if arm else "X86_64"
    env[f"CARGO_TARGET_{target}_PC_WINDOWS_MSVC_LINKER"] = str(linker)
    with open(os.environ["GITHUB_PATH"], "a") as f:
        # Each line goes ahead of the earlier ones, so the first ends up first.
        f.writelines(f"{p}\n" for p in reversed(new_path))
    with open(os.environ["GITHUB_ENV"], "a") as f:
        f.writelines(f"{k}={v}\n" for k, v in env.items())
    print(f"MSVC for {arch}: {len(new_path)} PATH entries, {len(env)} variables")


if __name__ == "__main__":
    main()
