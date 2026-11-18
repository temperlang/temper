# /// script
# requires-python = ">=3.11"
# ///
"""The release workflow's steps (.github/workflows/release.yml), which
docs/contributing/releasing.md describes. Standard library only, so
plain `python3` runs it too.

    release.py check v0.7.0-rc.1
    release.py packages --temper PATH --version 0.7.0-rc.1 --out DIR [--only rust,js]
    release.py self-test

`check` fails unless the tag is `v` and the workspace's version
(the root Cargo.toml's), or a pre-release of it (`-alpha.K`, `-beta.K`,
or `-rc.K`, K from 1), as temper_tmpl::version spells them; otherwise it
prints `version=`, `numbers=`, and `prerelease=` lines for
$GITHUB_OUTPUT.

`packages` builds a small library that imports std with that temper,
for every backend with a registry, then packs its runtime (temper-core)
and std as each registry takes them, into DIR, flat: crates, npm
tarballs, a wheel and an sdist each, NuGet packages, one Maven Central
bundle (the repository layout, unsigned), and LuaRocks source rocks with
their rockspecs. It checks that temper names the version, and that the
runtimes refuse publishing exactly when it's a pre-release. Where that
refusal stops a tool from packing at all (NuGet's `IsPackable`, and
Cargo's for temper-std, which needs temper-core publishable), a
pre-release leaves the package out and says so. It keeps going after a
failure and fails at the end, naming each one.
"""

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TAG = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(alpha|beta|rc)\.([1-9]\d*))?\Z")
ECOSYSTEMS = ("rust", "js", "py", "csharp", "java", "lua")
# Build dependencies for the wheels and sdists, at exact versions.
PY_BUILD_CONSTRAINTS = "hatchling==1.27.0\n"
# What keeps each runtime from being published until a release
# (temper_tmpl::version::Unpublished, each backend's runtime::VERSION),
# by a word of it, in temper-core's package file.
MARKERS = {
    "rust": ("Cargo.toml", "publish = false"),
    "js": ("package.json", '"private": true'),
    "py": ("pyproject.toml", "Private :: Do Not Upload"),
    "csharp": ("TemperLang.Core.csproj", "<IsPackable>false</IsPackable>"),
    "java": ("pom.xml", "<maven.deploy.skip>true</maven.deploy.skip>"),
}
PROBE_CONFIG = """# Release probe

    export let name = "release-probe";
"""
# Uses std, so `temper build` writes std beside the runtimes.
PROBE = """let { JsonTextProducer } = import("std/json");

export let probe(): String {
  let p = new JsonTextProducer();
  p.stringValue("probe");
  p.toJsonString() orelse ""
}
"""


def workspace_version() -> str:
    manifest = tomllib.loads((ROOT / "Cargo.toml").read_text())
    return manifest["workspace"]["package"]["version"]


def check_tag(tag: str, workspace: str) -> dict[str, str]:
    """The tag's version, or a ValueError saying why it can't be a release."""
    m = TAG.match(tag)
    if not m:
        raise ValueError(
            f"{tag}: not a release tag: v and a version, as v0.7.0, or a pre-release, "
            "as v0.7.0-alpha.1, v0.7.0-beta.2, or v0.7.0-rc.1 (no dev builds, alpha.0)"
        )
    numbers = ".".join(m.group(i) for i in (1, 2, 3))
    if numbers != workspace:
        raise ValueError(
            f"{tag}: names {numbers}, but the workspace's version (the root Cargo.toml's "
            f"[workspace.package] version) is {workspace}; tag v{workspace} or a "
            "pre-release of it, or change the workspace's version first"
        )
    return {
        "version": tag[1:],
        "numbers": numbers,
        "prerelease": "true" if m.group(4) else "false",
    }


def self_test() -> None:
    good = {
        "v0.7.0": ("0.7.0", "false"),
        "v0.7.0-alpha.1": ("0.7.0-alpha.1", "true"),
        "v0.7.0-beta.12": ("0.7.0-beta.12", "true"),
        "v0.7.0-rc.1": ("0.7.0-rc.1", "true"),
    }
    for tag, (version, pre) in good.items():
        out = check_tag(tag, "0.7.0")
        assert (out["version"], out["prerelease"]) == (version, pre), (tag, out)
    bad = [
        "v0.7.1", "v0.6.0", "0.7.0", "v0.7", "v0.7.0-alpha.0", "v0.7.0-alpha.0.5",
        "v0.7.0-rc.1.5", "v0.7.0-rc1", "v0.7.0-RC.1", "v0.7.0-dev.1", "v0.7.0-rc.01",
        "v00.7.0", "v0.7.0+build", "v0.7.0-rc.1\n", "nightly",
    ]
    for tag in bad:
        try:
            check_tag(tag, "0.7.0")
        except ValueError:
            continue
        raise AssertionError(f"{tag!r} passed")
    print("release.py: tag rules ok")


def run(*args: str | Path, cwd: Path | None = None, env: dict[str, str] | None = None) -> str:
    print("+", " ".join(str(a) for a in args), flush=True)
    program = shutil.which(str(args[0])) or str(args[0])
    out = subprocess.run(
        [program, *map(str, args[1:])],
        cwd=cwd,
        env={**os.environ, **(env or {})},
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    print(out.stdout, end="", flush=True)
    if out.returncode != 0:
        raise RuntimeError(f"{args[0]} exited with {out.returncode}")
    return out.stdout


def rust(build: Path, out: Path, release: bool) -> list[str]:
    """Crates by `cargo package`; with cargo 1.90 or later, `--workspace`
    packs temper-std against the temper-core beside it, which a release's
    temper-core allows."""
    dir = build / "rust"
    (dir / "Cargo.toml").write_text('[workspace]\nmembers = ["temper-core", "std"]\nresolver = "2"\n')
    target = dir / "target"
    packages = ["--workspace"] if release else ["-p", "temper-core"]
    run("cargo", "package", *packages, "--no-verify", "--allow-dirty", "--target-dir", target, cwd=dir)
    for crate in sorted((target / "package").glob("*.crate")):
        shutil.copy(crate, out)
    return [] if release else ["temper-std's crate: cargo can't pack it until temper-core is publishable"]


def js(build: Path, out: Path, release: bool) -> list[str]:
    for name in ["temper-core", "std"]:
        run("npm", "pack", "--pack-destination", out.resolve(), cwd=build / "js" / name)
    return []


def py(build: Path, out: Path, release: bool) -> list[str]:
    constraints = build / "py-build-constraints.txt"
    constraints.write_text(PY_BUILD_CONSTRAINTS)
    for name in ["temper-core", "std"]:
        run(
            "uv", "build", "--sdist", "--wheel", "--no-config",
            "--build-constraints", constraints, "--out-dir", out.resolve(),
            cwd=build / "py" / name,
        )
    for junk in out.glob(".gitignore"):
        junk.unlink()
    return []


def csharp(build: Path, out: Path, release: bool) -> list[str]:
    if not release:
        return ["NuGet packages: the runtime isn't packable until a release (IsPackable)"]
    env = {"DOTNET_CLI_TELEMETRY_OPTOUT": "1", "DOTNET_NOLOGO": "1"}
    for project in ["temper-core/TemperLang.Core.csproj", "std/src/TemperLang.Std.csproj"]:
        run("dotnet", "pack", build / "csharp" / project, "-c", "Release", "-o", out.resolve(), env=env)
    return []


def java(build: Path, out: Path, release: bool) -> list[str]:
    """One Maven Central bundle: each artifact's pom, jar, sources, and
    javadoc in the repository's layout, with MD5 and SHA-1 files, as the
    Central Portal takes it, but unsigned (publishing signs it)."""
    # A local repository of its own, so this release's temper-core
    # doesn't land in ~/.m2 beside dev builds. temper-core's pom asks
    # for a JDK 8 toolchain (~/.m2/toolchains.xml, `<version>8</version>`).
    repo = build / "maven-repository"
    mvn = ["mvn", "-B", "-ntp", "-Dgpg.skip", "-Dmaven.test.skip=true", f"-Dmaven.repo.local={repo}"]
    # temper-core installed, for std's dependency on it; `install` signs
    # nothing with gpg skipped, and deploys nothing.
    run(*mvn, "install", cwd=build / "java" / "temper-core")
    run(*mvn, "package", cwd=build / "java" / "std")
    bundle = build / "maven-bundle"
    version = None
    for name in ["temper-core", "std"]:
        project = build / "java" / name
        pom = (project / "pom.xml").read_text()
        group = re.search(r"<groupId>([^<]+)</groupId>", pom).group(1)
        artifact = re.search(r"<artifactId>([^<]+)</artifactId>", pom).group(1)
        version = re.search(r"<version>([^<]+)</version>", pom).group(1)
        dest = bundle.joinpath(*group.split("."), artifact, version)
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copy(project / "pom.xml", dest / f"{artifact}-{version}.pom")
        stem = f"{artifact}-{version}"
        jars = {p.name for p in (project / "target").glob(f"{stem}*.jar")}
        for want in [f"{stem}.jar", f"{stem}-sources.jar", f"{stem}-javadoc.jar"]:
            if want not in jars:
                raise RuntimeError(f"Maven: no {want} in {project / 'target'} (has {sorted(jars)})")
            shutil.copy(project / "target" / want, dest / want)
        for file in list(dest.iterdir()):
            data = file.read_bytes()
            (dest / f"{file.name}.md5").write_text(hashlib.md5(data).hexdigest())
            (dest / f"{file.name}.sha1").write_text(hashlib.sha1(data).hexdigest())
    with zipfile.ZipFile(out / f"temper-maven-{version}.zip", "w", zipfile.ZIP_DEFLATED) as z:
        for file in sorted(bundle.rglob("*")):
            if file.is_file():
                z.write(file, file.relative_to(bundle).as_posix())
    return []


def lua(build: Path, out: Path, release: bool) -> list[str]:
    """Each rockspec, as built, and a source rock: the rockspec with its
    sources zipped beside it, by `luarocks pack`, which needs the zip's
    URL absolute (publishing would point it at a download instead)."""
    dir = build / "lua"
    for name in ["temper-core", "std"]:
        (spec,) = (dir / name).glob("*.rockspec")
        shutil.copy(spec, out)
        archive = dir / f"{name}.zip"
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
            for file in sorted((dir / name).rglob("*")):
                if file.is_file() and file.suffix != ".rockspec":
                    z.write(file, file.relative_to(dir).as_posix())
        text = spec.read_text()
        local = f"file://{archive.resolve().as_posix()}"
        if text.count(f"file://{name}.zip") != 1:
            raise RuntimeError(f"{spec.name}: no source URL file://{name}.zip")
        packing = dir / spec.name
        packing.write_text(text.replace(f"file://{name}.zip", local))
        run("luarocks", "pack", packing.name, cwd=dir)
        (rock,) = dir.glob(f"{spec.stem}.src.rock")
        shutil.move(rock, out / rock.name)
    return []


PACK = {"rust": rust, "js": js, "py": py, "csharp": csharp, "java": java, "lua": lua}


def packages(temper: Path, version: str | None, out: Path, only: list[str]) -> int:
    problems: list[str] = []
    notes: list[str] = []
    said = run(temper, "--version").split()
    built = said[1] if len(said) > 1 else ""
    if version and built != version:
        problems.append(f"temper says it's {built!r}, not {version}")
    version = version or built
    release = "-" not in version
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="temper-release-") as temp:
        temp = Path(temp)
        (temp / "probe").mkdir()
        (temp / "probe" / "config.temper.md").write_text(PROBE_CONFIG)
        (temp / "probe" / "probe.temper").write_text(PROBE)
        build = temp / "build"
        backends = [arg for e in only for arg in ("-b", e)]
        try:
            run(temper, "build", "-w", temp / "probe", "-o", build, *backends)
        except RuntimeError as e:
            print(f"release.py: temper build: {e}", file=sys.stderr)
            return 1
        for ecosystem in only:
            if ecosystem in MARKERS:
                file, marker = MARKERS[ecosystem]
                text = (build / ecosystem / "temper-core" / file).read_text()
                if release == (marker in text):
                    state = "still refuses" if release else "doesn't refuse"
                    problems.append(f"{ecosystem}: a {'release' if release else 'pre-release'}'s runtime {state} publishing ({marker} in {file})")
            try:
                notes += PACK[ecosystem](build, out, release)
            except Exception as e:
                problems.append(f"{ecosystem}: {e}")
    print(f"\nPackages of {version} in {out}:")
    for file in sorted(out.iterdir()):
        print(f"  {file.name}")
    for note in notes:
        print(f"Left out, as for any pre-release: {note}")
    for problem in problems:
        print(f"release.py: {problem}", file=sys.stderr)
    return 1 if problems else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    check = sub.add_parser("check", help="check a tag against the workspace's version")
    check.add_argument("tag")
    pack = sub.add_parser("packages", help="build and pack the runtimes and std")
    pack.add_argument("--temper", type=Path, required=True)
    pack.add_argument("--version", help="the version temper must say it is")
    pack.add_argument("--out", type=Path, required=True)
    pack.add_argument("--only", default=",".join(ECOSYSTEMS), help="comma-separated, of " + ", ".join(ECOSYSTEMS))
    sub.add_parser("self-test", help="test the tag rules")
    args = parser.parse_args()
    if args.command == "self-test":
        self_test()
        return 0
    if args.command == "check":
        try:
            values = check_tag(args.tag, workspace_version())
        except ValueError as e:
            print(f"release.py: {e}", file=sys.stderr)
            return 1
        for key, value in values.items():
            print(f"{key}={value}")
        return 0
    only = [e for e in args.only.split(",") if e]
    unknown = [e for e in only if e not in ECOSYSTEMS]
    if unknown:
        parser.error(f"--only: unknown {', '.join(unknown)}")
    return packages(args.temper.resolve(), args.version, args.out, only)


if __name__ == "__main__":
    sys.exit(main())
