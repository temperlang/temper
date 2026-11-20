# /// script
# requires-python = ">=3.11"
# ///
"""Checks that the docs' and libraries' Temper examples run as they say.

    uv run compat/check_doc_examples.py [--temper BIN] [--verbose] [PATH...]

Each ```temper block in the docs, and each example in core's and std's
literate Markdown, is run on its own, as a one-file library, by
`temper test -v` on the interpreter, which settles it (names, macros,
and types) and runs its top level. It's judged by the marker on its
last line, the convention Temper's docs have always used:

- `// ✅`: must run cleanly, with no warning either (hints, which `-v`
  shows, are fine). A value after it, such as `// ✅ true`, `// ✅ 8`,
  or `// ✅ "text"` (a JSON string), is what the block's last
  expression must be, as string interpolation shows it.
- `// ❌` (maybe followed by the expected errors): must report an error,
  or panic or fail as it runs. A ✅ block mustn't.
- `// ⏸️`: shown, not checked (a fragment, or needs context).
- Another language: not checked.
- No marker: built, and counted, but a failure isn't fatal, since many
  tutorial snippets build on the ones before them.

A ```temper inert block isn't code of the file around it, so in a
literate `.temper.md` file, that's how an example in prose is written;
it's checked only if it ends with a ✅ or ❌ marker. Examples in doc
comments (`/** ... */`, lines starting with `*`) are ```temper blocks,
as in the docs. Other ```temper blocks of a `.temper.md` file are its
own code, and not examples.

A `//!outputs "text"` comment (a JSON string, or a list of them) says
what the line logs;
a marked ✅ block's logs must match them, in order.

A block known to disagree has `<!-- known: I10 -->` (one or more ids)
on the line right before its fence, each id an entry heading in
docs/inconsistencies.md. It's counted as known, not fatal, and the
known blocks are listed by id; once it agrees, remove the comment and
update the entry.

Exits with 1 if a marked block disagrees with its marker, a known block
agrees, or a known comment is unattached or names a missing entry.
PATH, files or folders, defaults to docs/, without docs/internals/ and
docs/release-notes/ (which keep old syntax, as it was), and lib/.
BIN defaults to target/release/temper, then target/small/temper.
"""

import argparse
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# A ```temper fence, maybe inert, maybe in a doc comment (lines start with `*`).
FENCE = re.compile(r"^([ \t]*(?:\*[ \t]?)?)```temper([ \t]+inert)?[ \t]*$\n(.*?)^\1```[ \t]*$", re.M | re.S)
# A diagnostic line, `path:line:column: level: message`, at temper's levels.
DIAGNOSTIC = re.compile(r":\d+:\d+: (error|warn|hint): ")
# How the interpreter reports a top level that ended abruptly; `temper test`
# still exits 0 when there are no tests.
ABRUPT = re.compile(r"^  (Panicked|Failed)\b|^  Jumped out of the module", re.M)
KNOWN = re.compile(r"^[ \t]*(?:\*[ \t]?)?<!-- known:((?:\s+I\d+)+) -->\s*$", re.M)


def blocks(page: Path):
    """Each example: its line, marker, body, known ids, and expected value."""
    text = page.read_text()
    literate = page.name.endswith(".temper.md")
    for m in FENCE.finditer(text):
        prefix, inert = m.group(1), bool(m.group(2))
        if literate and not inert and "*" not in prefix:
            continue  # The file's own code.
        body = "\n".join(ln.removeprefix(prefix) if ln.startswith(prefix) else ln.removeprefix(prefix.rstrip())
                         for ln in m.group(3).splitlines())
        line = text.count("\n", 0, m.start()) + 1
        before = text[:m.start()].removesuffix("\n").rpartition("\n")[2]
        known = KNOWN.fullmatch(before)
        last = [ln.strip() for ln in body.strip().splitlines()][-1:] or [""]
        marker = "ok" if last[0].startswith("// ✅") else "error" if last[0].startswith("// ❌") \
            else "skip" if last[0].startswith("// ⏸") else "none"
        if inert and marker not in ("ok", "error"):
            continue  # Only shown.
        value = last[0].removeprefix("// ✅").strip() if marker == "ok" else ""
        yield line, marker, body, known.group(1).split() if known else [], value


def expected_value(value: str) -> str | None:
    """How interpolation shows a `// ✅ value`, or None if there's none to check."""
    if value in ("", "void"):
        return None
    try:
        decoded = json.loads(value)
    except ValueError:
        return value
    return decoded if isinstance(decoded, str) else value


def with_value(body: str) -> str:
    """body, logging its last expression's value after what it logs."""
    return f"let example__ = do {{\n{body}\n}};\nconsole.log(\"${{example__}}\");"


def logged(out: str) -> list[str]:
    """What a run logged: the lines between "Testing" and the tally."""
    lines = out.splitlines()
    start = next((i + 1 for i, ln in enumerate(lines) if ln.startswith("Testing ")), 0)
    return [ln for ln in lines[start:] if not ln.startswith("Tests passed")
            and not DIAGNOSTIC.search(ln)]


def expected_logs(body: str) -> list[str]:
    found = []
    for m in re.finditer(r'//!outputs\s+(.*)$', body, re.M):
        try:
            value, _ = json.JSONDecoder().raw_decode(m.group(1).strip())
        except ValueError:
            continue
        found += value if isinstance(value, list) else [value]
    return found


def build(temper: Path, body: str) -> tuple[bool, str]:
    with tempfile.TemporaryDirectory() as tmp:
        (Path(tmp) / "config.temper.md").write_text('# Example\n\n    export let name = "example";\n')
        (Path(tmp) / "example.temper").write_text(body + "\n")
        r = subprocess.run([str(temper), "test", "-v", tmp], capture_output=True, text=True, timeout=60)
        out = (r.stdout + r.stderr).strip()
        return r.returncode == 0 and " error" not in out and not ABRUPT.search(out), out


def tmp_prefix(out: str) -> str:
    m = re.search(r"(/\S*/)example\.temper", out)
    return m.group(1) if m else "\0"


def rel(path: Path) -> Path:
    """path from the repository's root, where it's in the repository."""
    return path.relative_to(ROOT) if path.is_relative_to(ROOT) else path


def short(wheres: list[str]) -> str:
    """docs/a.md:1, docs/a.md:9, docs/b.md:2 as a.md:1,9 b.md:2."""
    by_page: dict[str, list[str]] = {}
    for w in wheres:
        page, _, line = w.rpartition(":")
        by_page.setdefault(page.rpartition("/")[2], []).append(line)
    return " ".join(f"{page}:{','.join(lines)}" for page, lines in by_page.items())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="*", type=Path)
    parser.add_argument("--temper", type=Path)
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()
    temper = args.temper or next(
        (p for p in (ROOT / "target/release/temper", ROOT / "target/small/temper") if p.exists()), None)
    if temper is None:
        print("No temper binary; build one (cargo build --release -p tempest) or pass --temper.")
        return 2
    pages = []
    for path in args.paths or [ROOT / "docs", ROOT / "lib"]:
        path = path.resolve()
        found = sorted(path.rglob("*.md")) if path.is_dir() else [path]
        pages += [p for p in found if args.paths or not {"internals", "release-notes"} & set(rel(p).parts[:2])]
    entries = set(re.findall(r"^### (I\d+):", (ROOT / "docs/inconsistencies.md").read_text(), re.M))
    counts: dict[str, int] = {}
    wrong = []
    known_at: dict[str, list[str]] = {}
    for page in pages:
        found = list(blocks(page))
        if len(re.findall(r"^[ \t]*(?:\*[ \t]?)?<!--\s*known\b", page.read_text(), re.M)) != sum(1 for *_, ids, _ in found if ids):
            wrong.append(f"{rel(page)}: a `<!-- known: ... -->` is malformed, or isn't on the line right before a ```temper fence")
        for line, marker, body, ids, value in found:
            where = f"{rel(page)}:{line}"
            for i in ids:
                if i not in entries:
                    wrong.append(f"{where}: known {i} isn't an entry in docs/inconsistencies.md")
            if ids and marker == "skip":
                wrong.append(f"{where}: known {' '.join(ids)}, but the block isn't checked")
            if marker == "skip":
                counts["skipped"] = counts.get("skipped", 0) + 1
                continue
            want_value = expected_value(value)
            ok, out = build(temper, body if want_value is None else with_value(body))
            want_ok = marker != "error"
            agrees = ok == want_ok
            want_logs = expected_logs(body) + ([want_value] if want_value is not None else [])
            if agrees and ok and marker == "ok" and want_logs and logged(out) != want_logs:
                agrees = False
                got = logged(out)
                out = f"logs {got!r}, not {want_logs!r}" if want_value is None or got[:-1] != want_logs[:-1] \
                    else f"logs {got[-1]!r} as its value, not {want_value!r}"
            warned = [ln for ln in out.splitlines() if ": warn: " in ln and DIAGNOSTIC.search(ln)]
            if agrees and marker == "ok" and warned:
                agrees = False
                out = f"warns: {warned[0].replace(tmp_prefix(out), '')}"
            if ids:
                tag = " ".join(ids)
                if agrees:
                    wrong.append(f"{where}: now as marked; fixed? remove `<!-- known: {tag} -->` and update docs/inconsistencies.md")
                else:
                    counts["known: not as marked"] = counts.get("known: not as marked", 0) + 1
                    for i in ids:
                        known_at.setdefault(i, []).append(where)
                    if args.verbose:
                        print(f"{where}: known {tag}")
                    continue
            key = f"{marker}: {'as marked' if agrees else 'not as marked'}"
            counts[key] = counts.get(key, 0) + 1
            if not agrees:
                errs = [ln.strip() for ln in out.splitlines() if "error" in ln.lower() or ABRUPT.match(ln)]
                first = out if out.startswith(("logs ", "warns: ")) else \
                    errs[0].replace(tmp_prefix(out), "") if errs else "runs cleanly"
                if marker != "none":
                    wrong.append(f"{where}: marked {marker}, but {first}")
                elif args.verbose:
                    print(f"{where}: unmarked, {first}")
    for w in wrong:
        print(w)
    for key in sorted(counts):
        print(f"{counts[key]:5} {key}")
    for i in sorted(known_at, key=lambda i: int(i[1:])):
        print(f"{len(known_at[i]):5} known {i}: {short(known_at[i])}")
    print(f"{len(wrong)} problem{'s' * (len(wrong) != 1)}" if wrong else "Every marked example builds as marked, or as known.")
    return 1 if wrong else 0


if __name__ == "__main__":
    sys.exit(main())
