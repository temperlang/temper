# /// script
# requires-python = ">=3.11"
# ///
"""Formats the docs' Temper examples with `temper fmt`, or checks them.

    uv run compat/fmt_doc_examples.py [--check] [--temper BIN] [PATH...]
    uv run compat/fmt_doc_examples.py --self-test [--temper BIN]

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
right before it), or by a `// fmt: off` first line in the block, as
`temper fmt` itself keeps what follows one. A block that doesn't parse,
such as a REPL transcript, a fragment, or an example of an error, is
left as written, and listed with --verbose (check_doc_examples.py
judges whether blocks build as they say). With --check, nothing changes:
it lists the blocks that need formatting. Exits with 1 if any do (with
--check), or if formatting would change a block's meaning (a bug in
`temper fmt`), and with 2 if `temper fmt` fails otherwise: it exits with
neither 0 nor 1, writes something but its `temper fmt: FILE: why` lines,
or leaves a known messy example unformatted (a binary that does nothing).
BIN defaults to target/release/temper, then target/small/temper.

--self-test checks this script: that binaries that fail or do nothing
fail it, and, with BIN, a page of examples of each kind of block.
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
# As `temper fmt` takes it: exactly `// fmt: off`.
OFF_LINE = re.compile(r"^\s*// fmt: off\s*$")
ERROR = re.compile(r"^temper fmt: (.+?\.temper): (.+)$")
# A block that any working `temper fmt` changes, formatted with the rest.
CANARY = ("let x=1;\n", "let x = 1;\n")


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


def off_inside(body: str) -> bool:
    """Whether a block's first line with code is `// fmt: off`."""
    return OFF_LINE.match(next((ln for ln in body.splitlines() if ln.strip()), "")) is not None


def pages(paths: list[Path]) -> list[Path]:
    """The Markdown pages to look at, as check_doc_examples.py finds them."""
    found = []
    for path in paths or [ROOT / "docs"]:
        path = path.resolve()
        under = sorted(path.rglob("*.md")) if path.is_dir() else [path]
        found += [p for p in under if paths or not {"internals", "release-notes"}
                  & set(p.relative_to(ROOT / "docs").parts)]
    return found


def where(page: Path, text: str, at: int) -> str:
    """A block's page and line, for messages."""
    shown = page.relative_to(ROOT) if page.is_relative_to(ROOT) else page
    return f"{shown}:{text.count(chr(10), 0, at) + 1}"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="*", type=Path)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--temper", type=Path)
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test(args.temper)
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
            if kept(text, m.start()) or off_inside(m.group(2)):
                off += 1
            else:
                blocks.append((page, text, m, indent(m.group(2))))
    with tempfile.TemporaryDirectory() as tmp:
        files = []
        for i, (_, _, m, own) in enumerate(blocks):
            body = "".join(ln.removeprefix(own) for ln in m.group(2).splitlines(keepends=True))
            files.append(Path(tmp, f"{i:05}.temper"))
            files[-1].write_text(body, "utf-8")
        canary = Path(tmp, "canary.temper")
        canary.write_text(CANARY[0], "utf-8")
        result = subprocess.run([str(temper), "fmt", tmp], capture_output=True, text=True)
        errors = {}
        failure = None
        for line in result.stderr.splitlines():
            m = ERROR.match(line)
            if m is None:
                failure = f"unexpected output: {line}"
                break
            errors[Path(m.group(1)).name] = m.group(2)
        if result.returncode not in (0, 1):
            failure = f"exit status {result.returncode}"
        elif (result.returncode == 1) != bool(errors):
            failure = f"exit status {result.returncode}, with {len(errors)} files reported"
        elif canary.read_text("utf-8") != CANARY[1]:
            failure = f"a known messy example, {CANARY[0]!r}, came back unformatted"
        if failure is not None:
            print(f"temper fmt ({temper}) failed: {failure}")
            print(result.stderr[-2000:], end="")
            return 2
        formatted = [f.read_text("utf-8") for f in files]
    problems = []
    unparsed = 0
    changed: dict[Path, list[tuple[int, int, str]]] = {}
    for (page, text, m, own), file, new in zip(blocks, files, formatted):
        at = where(page, text, m.start())
        why = errors.get(file.name)
        if why is not None and why.startswith("doesn't parse"):
            unparsed += 1
            if args.verbose:
                print(f"{at}: {why}")
            continue
        if why is not None:
            problems.append(f"{at}: {why}")
            continue
        new = "".join(own + ln if ln.strip() else ln for ln in new.splitlines(keepends=True))
        if new != m.group(2):
            if args.check:
                print(at)
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


# A page with a block of each kind, for --self-test: one to format, one
# kept by `<!-- fmt: off -->` above a `<!-- known: -->`, one starting with
# `// fmt: off`, one in a list item (its fence indented), one that doesn't
# parse, and an inert one.
PAGE = """# Examples

```temper
let a=1;
```

<!-- fmt: off -->
<!-- known: I1 -->
```temper
let  b = 2;
```

```temper
// fmt: off
let  c = 3;
```

- In a list:

  ```temper
  if (ok) {
  go();
  }
  ```

```temper
let f(x: Int {
```

```temper inert
let  d = 4;
```
"""

# PAGE, formatted.
FORMATTED = PAGE.replace("let a=1;", "let a = 1;").replace("  go();", "    go();")


def self_test(temper: Path | None) -> int:
    """Checks the script: binaries that fail or do nothing fail it, and,
    given a real one, PAGE formats as FORMATTED."""
    failures = []
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp, "page.md")

        def run(binary: Path, *flags: str) -> subprocess.CompletedProcess:
            page.write_text(PAGE, "utf-8")
            return subprocess.run(
                [sys.executable, __file__, *flags, "--temper", str(binary), str(page)],
                capture_output=True, text=True)

        fakes = {
            "exits 2": 'print("temper: unknown command", file=sys.stderr); sys.exit(2)',
            "does nothing": "pass",
            "says something else": 'print("warning: hmm", file=sys.stderr)',
        }
        for name, body in fakes.items():
            fake = Path(tmp, name.replace(" ", "-"))
            fake.write_text(f"#!{sys.executable}\nimport sys\n{body}\n", "utf-8")
            fake.chmod(0o755)
            for flags in [(), ("--check",)]:
                result = run(fake, *flags)
                if result.returncode != 2:
                    failures.append(f"a temper that {name}, {flags}: exit {result.returncode}"
                                    f" (want 2)\n{result.stdout}")
        if temper is not None:
            result = run(temper, "--check")
            listed = result.stdout.splitlines()[:-1]
            if result.returncode != 1 or listed != [f"{page}:3", f"{page}:20"]:
                failures.append(f"--check: exit {result.returncode}\n{result.stdout}")
            if page.read_text("utf-8") != PAGE:
                failures.append("--check changed the page")
            result = run(temper)
            if result.returncode != 0 or page.read_text("utf-8") != FORMATTED:
                failures.append(f"formatting: exit {result.returncode}\n{result.stdout}"
                                f"{page.read_text('utf-8')}")
    for f in failures:
        print(f"self-test: {f}")
    print(f"self-test: {'failed' if failures else 'passed'}"
          f"{'' if temper else ' (without a temper binary, fakes only)'}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
