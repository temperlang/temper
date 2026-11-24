# /// script
# requires-python = ">=3.11"
# ///
"""Formats the docs' Temper examples with `temper fmt`, or checks them.

    uv run compat/fmt_doc_examples.py [--check] [--temper BIN] [PATH...]

`temper fmt` formats .temper and .temper.md files; the docs' examples are
```temper fences in plain Markdown, so this formats them: each block that
compat/check_doc_examples.py checks (in docs/, without docs/internals/ and
docs/release-notes/, by default) is formatted on its own, as a .temper
file, and put back as it was indented (by its fence, and as a whole, as
an example of a .temper.md file's code may be). ```temper inert blocks,
such as docs/tools/fmt.md's before-and-after examples, aren't touched.

A block laid out by hand on purpose, as a table aligned in columns, or
an example of where spaces matter, is kept as written by a
`<!-- fmt: off -->` line among the HTML comments right before its fence
(above a `<!-- known: ... -->` or `<!-- repl: ... -->`, which must be
right before it). A block that doesn't parse, such as a REPL
transcript, a fragment, or an example of an error, is left as written, and listed with --verbose
(check_doc_examples.py judges whether blocks build as they say). With
--check, nothing changes: it lists the blocks that need formatting. Exits
with 1 if any do (with --check), or if formatting would change a block's
meaning (a bug in `temper fmt`). BIN defaults to target/release/temper,
then target/small/temper.
"""

import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from check_doc_examples import FENCE, ROOT

COMMENT = re.compile(r"^\s*<!--.*-->\s*$")
OFF = re.compile(r"^\s*<!--\s*fmt:\s*off\s*-->\s*$")


def indent(body: str) -> str:
    """The indentation a block's code lines all start with."""
    lines = [ln for ln in body.splitlines() if ln.strip()]
    return min((ln[:len(ln) - len(ln.lstrip())] for ln in lines), key=len, default="")


def kept(text: str, fence: int) -> bool:
    """Whether a `<!-- fmt: off -->` keeps the block whose fence starts at
    `fence` as written."""
    for line in reversed(text[:fence].splitlines()):
        if not COMMENT.match(line):
            return False
        if OFF.match(line):
            return True
    return False


def pages(paths: list[Path]) -> list[Path]:
    """The Markdown pages to look at, as check_doc_examples.py finds them."""
    found = []
    for path in paths or [ROOT / "docs"]:
        path = path.resolve()
        under = sorted(path.rglob("*.md")) if path.is_dir() else [path]
        found += [p for p in under if paths or not {"internals", "release-notes"}
                  & set(p.relative_to(ROOT / "docs").parts)]
    return found


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="*", type=Path)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--temper", type=Path)
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()
    temper = args.temper or next(
        (p for p in (ROOT / "target/release/temper", ROOT / "target/small/temper") if p.exists()), None)
    if temper is None:
        print("No temper binary; build one (cargo build --release -p tempest) or pass --temper.")
        return 2
    # Every block, as (page, its text, the match, its code's indentation).
    blocks = []
    off = 0
    for page in pages(args.paths):
        text = page.read_text("utf-8")
        for m in FENCE.finditer(text):
            if kept(text, m.start()):
                off += 1
            else:
                blocks.append((page, text, m, indent(m.group(2))))
    with tempfile.TemporaryDirectory() as tmp:
        files = []
        for i, (_, _, m, own) in enumerate(blocks):
            body = "".join(ln.removeprefix(own) for ln in m.group(2).splitlines(keepends=True))
            files.append(Path(tmp, f"{i:05}.temper"))
            files[-1].write_text(body, "utf-8")
        result = subprocess.run([str(temper), "fmt", tmp], capture_output=True, text=True)
        errors = {}
        for line in result.stderr.splitlines():
            name, _, why = line.removeprefix("temper fmt: ").partition(": ")
            errors[Path(name).name] = why
        formatted = [f.read_text("utf-8") for f in files]
    problems = []
    unparsed = 0
    changed: dict[Path, list[tuple[int, int, str]]] = {}
    for (page, text, m, own), file, new in zip(blocks, files, formatted):
        where = f"{page.relative_to(ROOT)}:{text.count(chr(10), 0, m.start()) + 1}"
        why = errors.get(file.name)
        if why is not None and why.startswith("doesn't parse"):
            unparsed += 1
            if args.verbose:
                print(f"{where}: {why}")
            continue
        if why is not None:
            problems.append(f"{where}: {why}")
            continue
        new = "".join(own + ln if ln.strip() else ln for ln in new.splitlines(keepends=True))
        if new != m.group(2):
            if args.check:
                print(where)
            changed.setdefault(page, []).append((m.start(2), m.end(2), new))
    if not args.check:
        for page, edits in changed.items():
            text = page.read_text("utf-8")
            for start, end, new in reversed(edits):
                text = text[:start] + new + text[end:]
            page.write_text(text, "utf-8")
    for p in problems:
        print(p)
    count = sum(len(e) for e in changed.values())
    done = "need formatting" if args.check else "formatted"
    print(f"{count} of {len(blocks)} blocks {done}, in {len(changed)} pages;"
          f" {unparsed} don't parse, left as written; {off} kept by fmt: off")
    return 1 if problems or (args.check and changed) else 0


if __name__ == "__main__":
    sys.exit(main())
