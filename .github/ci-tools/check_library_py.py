# /// script
# requires-python = ">=3.11"
# ///
"""Type-checks a build's generated Python packages with mypy and pyright, against a ratchet of allowed errors.

For the nightly's real libraries (nightly.yml's `libraries` job), the
same checks the Python backend's own tests run on each test's package
(crates/temper-be-py/tests/functional.rs), with the same options, so a
count here is what a developer sees locally:

- `mypy --strict --python-version 3.11 --follow-imports=silent`, with
  every project in the build on MYPYPATH
- pyright in strict mode, `pythonVersion` 3.11, every project in the
  build in `extraPaths`

OUT is a backend directory as `temper build -b py` or `temper test -b
py` writes it (`temper.out/py`): a project directory per library, each
holding its package and, apart, its `tests`, and std's (`std`) and the
runtime's (`temper-core`). Each library's package is checked, not its
tests, nor std or the runtime, which the backend's own tests check;
those are only on the import path. Each checker runs once over them
all, its errors counted per library (by its directory's name).

**The ratchet.** The baseline (library-py-baseline.json, beside this
script, or `--baseline FILE`) gives each library's allowed errors per
checker, as `{"library": {"mypy": 0, "pyright": 3}}`; a library or
checker it doesn't list is allowed none. It fails (exit 1, `::error`)
when a count is over its allowance, or a checker is missing or fails
to run, so a library that's clean today stays clean. A count under its
allowance passes with a `::notice` asking for the baseline to be
lowered to it in the same change, so the allowances only shrink. With
`--strict`, the baseline is ignored and every count must be 0.

It prints each library's counts and messages, and with GitHub Actions
(GITHUB_STEP_SUMMARY, GITHUB_ACTIONS), adds a table to the job summary,
with each library's first messages in a collapsible block.

mypy and pyright are found as the backend's tests find them: named by
MYPY and PYRIGHT, or on PATH. Pinned as CI's, they're what
`uv run .github/ci-tools/install.py` puts on PATH. Locally, from a
library's work root:

    temper build -b py
    uv run ~/path/to/tempest/.github/ci-tools/check_library_py.py temper.out/py
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

BASELINE = Path(__file__).resolve().parent / "library-py-baseline.json"
# Projects on the import path but not checked: std's and the runtime's.
SEPARATE = ("std", "temper-core")
MYPY_ARGS = ("--strict", "--python-version", "3.11", "--follow-imports=silent")
MYPY_ERROR = re.compile(r"^(?P<file>[^:\s][^:]*):\d+(?::\d+)?: error: ")


@dataclass
class Result:
    """One checker's run: its errors' messages by library, or why it didn't run."""

    by_library: dict[str, list[str]] = field(default_factory=dict)
    failure: str | None = None


def tool(var: str, name: str) -> str | None:
    return os.environ.get(var) or shutil.which(name)


def projects(out: Path) -> list[Path]:
    return sorted(p for p in out.iterdir() if p.is_dir() and (p / "pyproject.toml").is_file())


def packages(project: Path) -> list[Path]:
    """A library project's packages: its directories of Python but `tests`."""
    return sorted(
        p for p in project.iterdir()
        if p.is_dir() and p.name != "tests" and not p.name.startswith((".", "__")) and any(p.rglob("*.py"))
    )


def library_of(path: str, out: Path, libraries: list[str]) -> str | None:
    """The library a reported file is in, by its path's first part under `out`."""
    p = Path(path)
    if p.is_absolute():
        try:
            p = p.relative_to(out)
        except ValueError:
            return None
    first = p.parts[0] if p.parts else None
    return first if first in libraries else None


def run_mypy(mypy: str, out: Path, scratch: Path, path: list[Path], checked: list[Path],
             libraries: list[str]) -> Result:
    env = dict(os.environ, MYPYPATH=os.pathsep.join(str(p) for p in path))
    args = [mypy, *MYPY_ARGS, "--cache-dir", str(scratch / "mypy_cache"),
            *(str(p.relative_to(out)) for p in checked)]
    run = subprocess.run(args, cwd=out, env=env, text=True, capture_output=True)
    result = Result({name: [] for name in libraries})
    if run.returncode not in (0, 1):
        result.failure = f"exit {run.returncode}:\n{run.stdout}{run.stderr}".strip()
        return result
    for line in run.stdout.splitlines():
        if m := MYPY_ERROR.match(line):
            library = library_of(m["file"], out, libraries)
            result.by_library.setdefault(library or "(other)", []).append(line)
    if run.returncode == 1 and not any(result.by_library.values()):
        result.failure = f"exit 1 with no errors read:\n{run.stdout}{run.stderr}".strip()
    return result


def run_pyright(pyright: str, out: Path, scratch: Path, path: list[Path], checked: list[Path],
                libraries: list[str]) -> Result:
    # pyright ignores an absolute path in `include`, so they're relative
    # to the configuration's directory, as in the backend's tests.
    config = scratch / "pyrightconfig.json"
    config.write_text(json.dumps({
        "typeCheckingMode": "strict",
        "pythonVersion": "3.11",
        "extraPaths": [p.as_posix() for p in path],
        "include": [Path(os.path.relpath(p, scratch)).as_posix() for p in checked],
    }, indent=2) + "\n")
    run = subprocess.run([pyright, "--outputjson", "-p", str(config)], cwd=scratch, text=True,
                         capture_output=True)
    result = Result({name: [] for name in libraries})
    try:
        report = json.loads(run.stdout[run.stdout.index("{"):])
    except ValueError:
        result.failure = f"exit {run.returncode}, no JSON report:\n{run.stdout}{run.stderr}".strip()
        return result
    if run.returncode not in (0, 1):
        result.failure = f"exit {run.returncode}:\n{run.stderr}".strip()
        return result
    for d in report.get("generalDiagnostics", []):
        if d.get("severity") != "error":
            continue
        file = d.get("file", "")
        library = library_of(file, out, libraries)
        start = d.get("range", {}).get("start", {})
        shown = os.path.relpath(file, out) if library else file
        rule = f" ({d['rule']})" if d.get("rule") else ""
        message = d.get("message", "").replace("\n", " ").replace("\u00a0", " ")
        line = f"{shown}:{start.get('line', 0) + 1}:{start.get('character', 0) + 1}: error: {message}{rule}"
        result.by_library.setdefault(library or "(other)", []).append(line)
    return result


def escape(text: str, *, prop: bool = False) -> str:
    """A workflow command's message (or, with `prop`, property) escaped."""
    text = text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    return text.replace(":", "%3A").replace(",", "%2C") if prop else text


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("out", type=Path, help="the build's Python directory, as temper.out/py")
    parser.add_argument("--baseline", type=Path, default=BASELINE, metavar="FILE",
                        help="allowed errors per library and checker (default: library-py-baseline.json here)")
    parser.add_argument("--strict", action="store_true", help="ignore the baseline: allow no errors")
    parser.add_argument("--max-messages", type=int, default=30, metavar="N",
                        help="messages shown per library and checker in the job summary (default 30)")
    args = parser.parse_args()
    out: Path = args.out.resolve()
    actions = os.environ.get("GITHUB_ACTIONS") == "true"
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    baseline: dict[str, dict[str, int]] = {} if args.strict else json.loads(args.baseline.read_text())

    def annotate(kind: str, title: str, message: str) -> None:
        if actions:
            print(f"::{kind} title={escape(title, prop=True)}::{escape(message)}", flush=True)

    if not out.is_dir():
        message = f"no Python build at {args.out}: `temper test -b py` didn't make one (see its output above)"
        print(f"check_library_py: {message}", file=sys.stderr)
        annotate("error", "Python type checks", message)
        return 1
    path = projects(out)
    own = [p for p in path if p.name not in SEPARATE]
    libraries = [p.name for p in own]
    checked = [pkg for p in own for pkg in packages(p)]
    if not checked:
        print(f"check_library_py: no library packages in {args.out}", file=sys.stderr)
        annotate("error", "Python type checks", f"no library packages in {args.out}")
        return 1

    results: dict[str, Result] = {}
    with tempfile.TemporaryDirectory(prefix="check_library_py-") as tmp:
        scratch = Path(tmp)
        for name, var, runner in (("mypy", "MYPY", run_mypy), ("pyright", "PYRIGHT", run_pyright)):
            found = tool(var, name)
            if found is None:
                results[name] = Result(failure=f"not found (on PATH, or as {var})")
                continue
            print(f"+ {name} on {', '.join(str(p.relative_to(out)) for p in checked)}", flush=True)
            try:
                results[name] = runner(found, out, scratch, path, checked, libraries)
            except OSError as e:
                results[name] = Result(failure=f"could not run {found}: {e}")

    failed = False
    lower: list[str] = []
    rows = []
    details = []
    other = any("(other)" in r.by_library for r in results.values())
    for library in libraries + (["(other)"] if other else []):
        cells = []
        for name, result in results.items():
            if result.failure is not None:
                cells.append("not run" if result.failure.startswith("not found") else "failed")
                continue
            errors = result.by_library.get(library, [])
            allowed = baseline.get(library, {}).get(name, 0)
            count = len(errors)
            if count > allowed:
                failed = True
                verdict = f"over its {allowed} allowed"
                cells.append(f"**{count}** (allowed {allowed}; over)")
                annotate("error", f"{name} on {library}", f"{count} errors, over the {allowed} allowed "
                         f"({args.baseline.name}); the first: {errors[0]}")
            elif count < allowed:
                verdict = f"under its {allowed} allowed: lower the baseline to {count}"
                lower.append(f"{library}'s {name}: {allowed} to {count}")
                cells.append(f"{count} (allowed {allowed}; lower it)")
                annotate("notice", f"{name} on {library}", f"{count} errors, under the {allowed} allowed: "
                         f"lower {args.baseline.name}'s {library}.{name} to {count} in this change")
            else:
                verdict = "as allowed" if count else "clean"
                cells.append(str(count) if not allowed else f"{count} (allowed {allowed})")
            print(f"{library}: {name}: {count} errors, {verdict}")
            for line in errors:
                print(f"  {line}")
            if errors:
                shown = errors[:args.max_messages]
                more = f"\n... and {count - len(shown)} more" if count > len(shown) else ""
                details.append(f"<details><summary>{library}: {name}, {count} errors ({verdict})</summary>\n\n"
                               "```text\n" + "\n".join(shown) + more + "\n```\n\n</details>\n")
        rows.append(f"| {library} | " + " | ".join(cells) + " |")
    for name, result in results.items():
        if result.failure is not None:
            failed = True
            print(f"{name}: {result.failure}", file=sys.stderr)
            details.append(f"<details><summary>{name}: {result.failure.splitlines()[0]}</summary>\n\n"
                           "```text\n" + result.failure + "\n```\n\n</details>\n")
            annotate("error", name, result.failure.splitlines()[0])

    if summary_file:
        rule = ("every count must be 0 (`--strict`)" if args.strict else
                f"no count may be over its allowance in `{args.baseline.name}` (none, if unlisted)")
        note = ("\n**Lower the baseline** in this change: " + "; ".join(lower) + ".\n" if lower else "")
        with open(summary_file, "a", encoding="utf-8") as f:
            f.write(f"### Python type checks\n\n"
                    "Errors in each library's generated package, tests aside, as the Python "
                    f"backend's tests check theirs: `mypy --strict` and pyright's strict mode; {rule}.\n\n"
                    "| Library | mypy | pyright |\n|---|---|---|\n" + "\n".join(rows) + "\n" + note + "\n"
                    + "".join(details))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
