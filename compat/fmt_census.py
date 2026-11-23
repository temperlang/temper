# /// script
# requires-python = ">=3.11"
# ///
"""Measures how Temper code is laid out today, for `temper fmt`'s rules.

Reads every .temper and .temper.md file in this repository's lib/ and
crates/, the ```temper examples in docs/ (as compat/check_doc_examples.py
finds them), and any library checkouts given as arguments, and prints, for
each candidate rule, how the code does it now and how many lines the
dominant style would change. notes/fmt.md has the results and the rules.

    uv run compat/fmt_census.py [--samples N] [--temper BIN] [LIBRARY_DIR ...]

With --temper, it also runs that `temper fmt` over copies of the files
and reports how many lines it changes and the files it can't format.

Tokens and trees come from temper-syntax's `lex-tokens` and `parse-tree`
(built here), so the census sees code as the compiler does. In .temper.md
files, only Temper code blocks count: each file's blocks, with their common
indentation removed, are joined into one source, so blocks may continue
each other's brackets.
"""

import argparse
import bisect
import re
import subprocess
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "target/fmt-census"
SKIP_PARTS = {"build", "node_modules", "temper.out", ".git", "target"}
FENCE = re.compile(r"^(\s*)```temper\s*$\n(.*?)^\1```\s*$", re.M | re.S)
OPENERS = set("({[")
CLOSERS = set(")}]")
# Words that take `(` as part of a construct, not a call.
KEYWORDS = {"if", "while", "for", "when", "catch"}
# Infix operators whose spacing stays as written: label colons have no
# settled style; the rest have their own rules.
KEPT_INFIX = {"Angle", "LowColon", "DotDot", "ColonColon", "CallJoin"}
UNIT = 2


@dataclass
class Tok:
    kind: str
    start: int
    end: int
    text: str
    role: tuple = ("", "")  # (role, operator name) from the parse tree


@dataclass
class Source:
    group: str
    name: str
    text: str
    path: Path | None = None
    suffix: str = ".temper"
    # Indentation each virtual line had in its file (for .temper.md blocks).
    bases: list[int] = field(default_factory=list)


def temper_files(root: Path) -> list[Path]:
    found = []
    for p in sorted(root.rglob("*")):
        if p.name.endswith((".temper", ".temper.md")) and p.is_file():
            if not SKIP_PARTS.intersection(p.relative_to(root).parts):
                found.append(p)
    return found


def literate_sources(blocks_bin: str, paths: list[Path]) -> dict[Path, tuple[str, list[int]]]:
    """Each .temper.md file's Temper code blocks, dedented and joined."""
    out = subprocess.run([blocks_bin, *map(str, paths)], capture_output=True, text=True, check=True).stdout
    result = {}
    current = None
    spans: dict[Path, list[tuple[int, int]]] = defaultdict(list)
    for line in out.splitlines():
        if line.startswith("# "):
            current = Path(line[2:])
            spans[current]
            continue
        tags, bs, be, es, _ee = line.split("\t")
        words = tags.split()
        if tags == "-" or (words and (words[0] != "temper" or "inert" in words)):
            continue
        spans[current].append((int(bs), int(be), int(es)))
    for path, ranges in spans.items():
        data = path.read_bytes()
        lines, bases = [], []
        for fence, begin, end in ranges:
            body = data[begin:end].decode("utf-8")
            if begin > fence:
                # Fenced: code starts on the next line, indented like the fence.
                body = body.split("\n", 1)[1] if "\n" in body else ""
                indent = len(data[fence:begin]) - len(data[fence:begin].lstrip(b" "))
            else:
                # Indented code: four spaces (ignoring list containers).
                indent = 4
            block = body.split("\n")
            if block and block[-1].strip() == "":
                block.pop()
            for b in block:
                cut = min(indent, len(b) - len(b.lstrip(" ")))
                lines.append(b[cut:])
                bases.append(cut)
        result[path] = ("\n".join(lines) + "\n", bases)
    return result


def collect(libraries: list[Path], blocks_bin: str) -> list[Source]:
    sources = []
    groups = [("core+std", ROOT / "lib"), ("tests", ROOT / "crates")]
    groups += [(lib.name, lib) for lib in libraries]
    for group, root in groups:
        files = temper_files(root)
        md = [p for p in files if p.name.endswith(".md")]
        lit = literate_sources(blocks_bin, md) if md else {}
        for p in files:
            name = f"{group}:{p.relative_to(root)}"
            if p in lit:
                text, bases = lit[p]
                sources.append(Source(group, name, text, path=p, suffix=".temper.md", bases=bases))
            else:
                sources.append(Source(group, name, p.read_text("utf-8"), path=p))
    for page in sorted((ROOT / "docs").rglob("*.md")):
        rel = page.relative_to(ROOT / "docs")
        if rel.parts[0] in ("internals", "release-notes"):
            continue
        text = page.read_text("utf-8")
        for i, m in enumerate(FENCE.finditer(text)):
            indent = m.group(1)
            body = "\n".join(line.removeprefix(indent) for line in m.group(2).splitlines())
            sources.append(Source("docs", f"docs:{rel}#{i}", body + "\n"))
    return sources


def run_bins(sources: list[Source]) -> None:
    """Lexes and parses each source, attaching tokens with parse roles."""
    WORK.mkdir(parents=True, exist_ok=True)
    paths = []
    for i, s in enumerate(sources):
        p = WORK / f"{i:05}.temper"
        p.write_text(s.text, "utf-8")
        paths.append(str(p))
    lexed = parse_dump(run_chunked(str(ROOT / "target/release/lex-tokens"), paths))
    trees = parse_dump(run_chunked(str(ROOT / "target/release/parse-tree"), paths))
    for i, s in enumerate(sources):
        roles = roles_of(trees[paths[i]])
        toks = []
        btext = s.text.encode()
        for line in lexed[paths[i]]:
            kind, start, end, _b, synth, text = line.split("\t", 5)
            if synth != "-":
                continue
            start, end = int(start), int(end)
            text = btext[start:end].decode()
            toks.append(Tok(kind, start, end, text, roles.get(start, ("", ""))))
        s.tokens = toks


def run_chunked(binary: str, paths: list[str]) -> str:
    out = []
    for i in range(0, len(paths), 200):
        out.append(subprocess.run([binary, *paths[i : i + 200]], capture_output=True, text=True).stdout)
    return "".join(out)


def parse_dump(out: str) -> dict[str, list[str]]:
    by_file: dict[str, list[str]] = {}
    current: list[str] = []
    for line in out.splitlines():
        if line.startswith("# "):
            current = by_file.setdefault(line[2:], [])
        else:
            current.append(line)
    return by_file


def roles_of(lines: list[str]) -> dict[int, tuple[str, str]]:
    """Each operator token's role in its node, by start offset: `infix`
    (between two operands), `prefix`, `postfix`, `open`, `close`, or `sep`."""
    nodes = []  # (op, children) where children are ("N", node) or ("T", start, synthetic)
    stack: list[tuple[int, list]] = []
    for line in lines:
        f = line.split("\t")
        if f[0] == "N":
            depth = int(f[1])
            node = (f[2], [])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            if stack:
                stack[-1][1][1].append(("N", node))
            nodes.append(node)
            stack.append((depth, node))
        elif f[0] == "T":
            depth = int(f[1])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            if stack:
                stack[-1][1][1].append(("T", int(f[3]), f[5] == "s", f[6] if len(f) > 6 else ""))
    roles = {}
    for op, children in nodes:
        if op == "Leaf":
            continue
        for i, c in enumerate(children):
            if c[0] != "T" or c[2]:
                continue
            before = any(x[0] == "N" for x in children[:i])
            after = any(x[0] == "N" for x in children[i + 1 :])
            text = c[3]
            if text and text[-1] in OPENERS:
                role = "open"
            elif text in CLOSERS or text == ">" and op == "Angle":
                role = "close"
            elif op in ("Comma", "Semi", "SemiSemi", "SemiSemiSemi"):
                role = "sep"
            elif before and after:
                role = "infix"
            elif after:
                role = "prefix"
            else:
                role = "postfix"
            roles[c[1]] = (role, op)
    return roles


@dataclass
class Line:
    start: int
    end: int  # before the line break
    toks: list  # tokens starting on this line
    indent: int
    first: Tok | None  # first token, if it starts the line's content
    in_string: bool  # starts inside a multi-quoted string


class Stats:
    def __init__(self):
        self.c: dict[str, Counter] = defaultdict(Counter)
        self.changed: Counter = Counter()  # rule -> lines it would change
        self.samples: dict[str, list[str]] = defaultdict(list)
        self.lines = 0
        self.widths: list[int] = []

    def add(self, item, choice, n=1):
        self.c[item][choice] += n

    def sample(self, key, text, limit):
        if len(self.samples[key]) < limit:
            self.samples[key].append(text)


def lines_of(s: Source) -> list[Line]:
    if getattr(s, "lines", None) is not None:
        return s.lines
    btext = s.text.encode()
    # Byte offsets: tokens use UTF-8 offsets.
    bstarts = [0] + [m.end() for m in re.finditer(b"\n", btext)]
    if bstarts[-1] == len(btext):
        bstarts.pop()
    s.line_starts = bstarts
    lines = []
    ti = 0
    toks = [t for t in s.tokens if t.kind != "Space"]
    for li, bs in enumerate(bstarts):
        be = bstarts[li + 1] - 1 if li + 1 < len(bstarts) else len(btext)
        if be > bs and btext[be - 1 : be] == b"\n":
            be -= 1
        if be > bs and btext[be - 1 : be] == b"\r":
            be -= 1
        mine = []
        while ti < len(toks) and toks[ti].start < be + 1:
            if toks[ti].start >= bs:
                mine.append(toks[ti])
            ti += 1
        raw = btext[bs:be].decode()
        indent = len(raw) - len(raw.lstrip(" \t"))
        first = mine[0] if mine and mine[0].start == bs + len(raw[:indent].encode()) else None
        in_string = first is not None and first.kind in ("Margin", "QuotedString", "RightDelimiter")
        lines.append(Line(bs, be, mine, indent, first, in_string))
    s.lines = lines
    return lines


def analyze(s: Source, st: Stats, samples: int) -> None:
    btext = s.text.encode()
    lines = lines_of(s)
    code_toks = [t for t in s.tokens if t.kind not in ("Space", "Comment")]
    # Bracket stack per token, built as we go: entries (char, line index).
    stack: list[tuple[str, int]] = []
    predicted: dict[int, int] = {}
    prev_code: Tok | None = None
    prev_code_line = -1
    line_of_tok = {}
    for li, ln in enumerate(lines):
        for t in ln.toks:
            line_of_tok[t.start] = li
    tok_iter = iter(s.tokens)
    pending = []
    # Walk lines; within each, walk tokens to update the stack.
    decorator_line = False
    # The line that started the current statement, by bracket depth.
    stmt: dict[int, int] = {}
    for li, ln in enumerate(lines):
        raw = btext[ln.start : ln.end].decode()
        width = len(raw) + (s.bases[li] if li < len(s.bases) else 0)
        if raw.strip():
            st.lines += 1
            st.widths.append(width)
            if raw != raw.rstrip():
                st.changed["no trailing space"] += 1
                st.sample("trailing space", f"{s.name}:{li + 1}: {raw!r}", samples)
            if "\t" in raw[: ln.indent]:
                st.add("indent chars", "tab")
            elif ln.indent:
                st.add("indent chars", "spaces")
        if ln.first is not None and not ln.in_string and ln.first.kind != "Margin":
            t = ln.first
            top = stack[-1] if stack else None
            level = len(stack)
            if t.text and t.text[0] in CLOSERS and t.kind == "Punctuation":
                want = predicted.get(top[3], 0) if top else 0
                kind = "closer"
            elif continuation(prev_code, top, decorator_line, t) and level in stmt:
                # Continuation lines keep their offset from the line that
                # started the statement.
                first = stmt[level]
                offset = ln.indent - lines[first].indent
                st.add("continuation offset", offset)
                prev_kind = "starts with an operator" if t.role[0] == "infix" or t.text == "." else "after an operator" if prev_code.role[0] == "infix" else "other"
                st.add(f"continuation offset, line {prev_kind}", offset)
                st.sample(f"continuation offset {offset}", f"{s.name}:{li + 1}: {raw.strip()[:60]}", samples)
                want = max(0, predicted[first] + offset)
                kind = "continuation"
            else:
                want = predicted.get(top[3], 0) + UNIT if top else 0
                kind = "statement"
                stmt[level] = li
                if top is not None and prev_code is not None and prev_code.start == top[2]:
                    st.add("block indent step", ln.indent - lines[top[1]].indent)
            predicted[li] = want
            if want == ln.indent:
                st.add("indentation", "matches")
            else:
                st.add("indentation", f"differs ({kind})")
                st.changed["indentation"] += 1
                st.sample(f"indentation ({kind})", f"{s.name}:{li + 1}: want {want}, has {ln.indent}: {raw.strip()[:70]}", samples)
        elif ln.first is not None:
            predicted[li] = ln.indent
        # Update the stack and previous code token with this line's tokens.
        line_depth = len(stack)
        codes = [t for t in ln.toks if t.kind not in ("Comment",)]
        if ln.first is not None and ln.first.text.startswith("@") and ln.first.kind == "Punctuation":
            decorator_line = True
        elif codes:
            decorator_line = False
        for t in codes:
            if t.kind == "Punctuation" and t.text and t.text[-1] in OPENERS:
                # Contents indent from the opener's line if that line
                # started at the opener's depth, or else from the line that
                # started its statement; hugged items start a statement.
                base = li if line_depth == len(stack) else stmt.get(len(stack), li)
                stack.append((t.text[-1], li, t.start, base))
                stmt[len(stack)] = li
            elif t.kind == "Punctuation" and t.text in CLOSERS and stack:
                stmt.pop(len(stack), None)
                stack.pop()
            if t.kind not in ("Margin", "QuotedString"):
                prev_code = t
                prev_code_line = li
        if decorator_line and stack and lines[stack[-1][1]] is ln:
            decorator_line = False


def continuation(prev: Tok | None, top, decorator_line: bool, first: Tok) -> bool:
    if prev is None:
        return False
    if first.text in (".", "?."):
        return True
    if decorator_line:
        return False
    if top is not None and prev.start == top[2]:
        return False
    ends = (",",) if top is not None and top[0] in "([" else (";", "{", "}", ",", "=>")
    if prev.text in ends:
        return False
    return True


def spacing(s: Source, st: Stats, samples: int) -> None:
    lines_of(s)
    btext = s.text.encode()
    toks = [t for t in s.tokens if t.kind != "Space"]
    changed_lines: dict[str, set] = defaultdict(set)
    in_hole = 0
    for a, b in zip(toks, toks[1:]):
        gap = btext[a.end : b.start].decode()
        if "\n" in gap or a.kind in ("QuotedString", "Margin", "LeftDelimiter") or b.kind in ("QuotedString", "Margin", "RightDelimiter"):
            continue
        sp = "none" if gap == "" else "one" if gap == " " else "more"
        line = bisect.bisect_right(s.line_starts, a.start) - 1

        def rule(item, want, got=sp, key=None):
            st.add(item, got)
            if got != want:
                changed_lines[key or item].add(line)
                st.sample(item, f"{s.name}:{line + 1}: {a.text!r} {b.text!r} gap {gap!r}", samples)

        if b.kind == "Comment":
            if a.kind != "Comment":
                rule("before trailing comment", "one" if sp != "more" else "more", key="before trailing comment")
            continue
        if a.kind == "Comment":
            continue
        ra, rb = a.role, b.role
        if b.text == "<":
            # Space before `<` decides whether it may be an angle bracket.
            st.add("before <", sp)
        elif rb[0] == "infix" and rb[1] in KEPT_INFIX - {"Angle"} or ra[0] == "infix" and ra[1] in KEPT_INFIX - {"Angle"}:
            st.add(f"around {rb[1] if rb[0] == 'infix' else ra[1]} (kept)", sp)
        elif rb[0] == "sep" and b.text == ",":
            rule("before ,", "none")
        elif ra[0] == "sep" and a.text == ",":
            if b.text in CLOSERS:
                rule("after , before closer", "none")
            else:
                rule("after ,", "one")
        elif rb[0] == "sep" and b.text == ";":
            rule("before ;", "none")
        elif ra[0] == "sep" and a.text == ";":
            rule("after ; (same line)", "one")
        elif rb[0] == "infix" and rb[1] in ("Dot", "ChainNull"):
            rule("around . and ?.", "none")
        elif ra[0] == "infix" and ra[1] in ("Dot", "ChainNull"):
            rule("around . and ?.", "none")
        elif rb[0] == "infix" and rb[1] == "HighColon":
            rule("before : (type)", "none")
        elif ra[0] == "infix" and ra[1] == "HighColon":
            rule("after : (type)", "one")
        elif rb[0] == "infix" and b.kind == "Punctuation" and rb[1] not in ("Angle",):
            rule(f"around infix {rb[1]}", "one", key="around infix operators")
            rule("around infix operators (all)", "one", key="around infix operators")
        elif ra[0] == "infix" and a.kind == "Punctuation" and ra[1] not in ("Angle",):
            rule(f"around infix {ra[1]}", "one", key="around infix operators")
            rule("around infix operators (all)", "one", key="around infix operators")
        elif ra[0] == "sep" and a.text == "=>" or rb[0] == "sep" and b.text == "=>":
            rule("around =>", "one", key="around infix operators")
        elif rb[0] == "open" and b.text == "(" and rb[1] == "Paren":
            if a.text in KEYWORDS:
                rule("keyword (", "one")
            elif a.text == "fn":
                rule("fn (", "one")
            elif a.kind == "Word":
                rule("call name(", "none")
        elif rb[0] == "open" and b.text == "{" and rb[1] in ("Curly", "CurlyGroup"):
            if not (a.kind == "Punctuation" and a.text[-1:] in OPENERS):
                rule("before {", "one")
        elif ra[0] == "open" and a.text == "{" and ra[1] in ("Curly", "CurlyGroup"):
            rule("inside { } (same line)", "one" if b.text != "}" else "none")
        elif rb[0] == "close" and b.text == "}" and rb[1] in ("Curly", "CurlyGroup"):
            rule("inside { } (same line)", "one")
        elif ra[0] == "open" and a.text in "([" or rb[0] == "close" and b.text in ")]":
            rule("inside ( ) and [ ]", "none")
        elif rb[0] == "open" and rb[1] == "Angle" or ra[0] == "open" and ra[1] == "Angle" or rb[0] == "close" and rb[1] == "Angle":
            rule("inside < >", "none")
        elif ra[0] == "prefix" and a.kind == "Punctuation" and ra[1] != "At":
            rule("after prefix operator", "none")
        elif ra[0] == "prefix" and ra[1] == "At":
            rule("after @", "none")
        elif sp == "more":
            rule("other gaps of 2+ spaces", "one", key="collapse spaces")
        else:
            st.add("other gaps", sp)
    for c in (t for t in toks if t.kind == "Comment" and t.text.startswith("//")):
        body = c.text[2:]
        if body and body[0] not in " /!":
            st.add("// then space", "no")
        elif body:
            st.add("// then space", "yes")
    for k, v in changed_lines.items():
        st.changed[f"spacing: {k}"] += len(v)


def blank_lines(s: Source, st: Stats) -> None:
    lines = lines_of(s)
    btext = s.text.encode()
    blank = [not btext[ln.start : ln.end].strip() for ln in lines]
    # A blank line must not be inside a string or comment.
    inside = set()
    for t in s.tokens:
        if t.kind in ("Comment", "QuotedString", "Margin") and b"\n" in btext[t.start : t.end]:
            li = bisect.bisect_right(s.line_starts, t.start)
            while li < len(lines) and lines[li].start < t.end:
                inside.add(li)
                li += 1
    run = 0
    for li, b in enumerate(blank):
        if b and li not in inside:
            run += 1
            if run >= 2:
                st.changed["blank lines: collapse runs"] += 1
        else:
            run = 0
    lead = 0
    for b in blank:
        if not b:
            break
        lead += 1
    st.changed["blank lines: none at start"] += lead
    # Around block edges and between statements.
    code_lines = [i for i, b in enumerate(blank) if not b and lines[i].first is not None and i not in inside]
    for prev, cur in zip(code_lines, code_lines[1:]):
        gap = cur - prev - 1
        has_blank = any(blank[j] for j in range(prev + 1, cur))
        last = [t for t in lines[prev].toks if t.kind != "Comment"]
        first = lines[cur].first
        if last and last[-1].text == "{" and last[-1].kind == "Punctuation":
            st.add("blank line after {", "yes" if has_blank else "no")
            if has_blank:
                st.changed["blank lines: none after {"] += gap
        elif first.text in CLOSERS and first.kind == "Punctuation":
            st.add("blank line before }", "yes" if has_blank else "no")
            if has_blank:
                st.changed["blank lines: none before }"] += gap
        elif last and last[-1].text == "}" and lines[prev].first is not None and lines[prev].first.text == "}":
            st.add("blank line after a multi-line statement", "yes" if has_blank else "no")
    # Trailing newline.
    if not s.text.endswith("\n"):
        st.changed["final newline"] += 1


def braces(s: Source, st: Stats) -> None:
    toks = [t for t in s.tokens if t.kind not in ("Space",)]
    lines = lines_of(s)
    firsts = {ln.first.start for ln in lines if ln.first is not None}
    btext = s.text.encode()
    for a, b in zip(toks, toks[1:]):
        if b.text == "{" and b.role[0] == "open" and b.role[1] == "Curly":
            st.add("{ on the same line as what it follows", "no" if b.start in firsts else "yes")
        if a.text == "}" and b.kind == "Word" and b.text in ("else", "catch", "finally"):
            st.add("} else on one line", "no" if b.start in firsts else "yes")


def calls(s: Source, st: Stats, samples: int) -> None:
    toks = [t for t in s.tokens if t.kind not in ("Space", "Comment")]
    btext = s.text.encode()
    stack = []
    for i, t in enumerate(toks):
        if t.kind == "Punctuation" and t.text and t.text[-1] in OPENERS:
            stack.append(i)
        elif t.kind == "Punctuation" and t.text in CLOSERS and stack:
            o = stack.pop()
            opener, closer = toks[o], t
            if b"\n" not in btext[opener.end : closer.start]:
                continue
            kind = {"(": "( )", "[": "[ ]", "{": "{ }"}.get(opener.text[-1], "")
            depth = 0
            commas = 0
            for x in toks[o + 1 : i]:
                if x.kind == "Punctuation" and x.text and x.text[-1] in OPENERS:
                    depth += 1
                elif x.kind == "Punctuation" and x.text in CLOSERS:
                    depth -= 1
                elif depth == 0 and x.text == ",":
                    commas += 1
            if kind == "{ }" and commas == 0:
                continue
            after_open_eol = b"\n" in btext[opener.end : toks[o + 1].start] if o + 1 < i else True
            before_close_bol = b"\n" in btext[toks[i - 1].end : closer.start]
            if after_open_eol and before_close_bol:
                shape = "tall: opener ends a line, closer starts one"
                last = toks[i - 1]
                st.add(f"trailing comma in multi-line {kind}", "yes" if last.text == "," else "no")
            elif not after_open_eol and not before_close_bol:
                nxt = toks[o + 1]
                shape = "hugged: items start on the opener's line, closer ends the last"
            elif after_open_eol:
                shape = "closer ends the last item's line"
            else:
                shape = "items start on the opener's line, closer on its own line"
            if kind != "{ }":
                st.add(f"multi-line {kind} lists", shape)
                st.sample(f"shape: {shape}", f"{s.name}:{bisect.bisect_right(s.line_starts, opener.start)}", samples)


def trailing_blocks(s: Source, st: Stats) -> None:
    toks = [t for t in s.tokens if t.kind not in ("Space", "Comment")]
    for a, b in zip(toks, toks[1:]):
        if b.text == "fn" and a.text in ("(", ","):
            st.add("block arguments", "fn literal inside ( )")
        if a.text == ")" and b.text == "{" and b.role == ("open", "Curly"):
            pass
    # Calls with a trailing block, from the tree roles: `name(...) {` where
    # the name isn't a keyword, or `name {`.
    for i in range(1, len(toks)):
        t = toks[i]
        if t.text == "{" and t.role == ("open", "Curly"):
            j = i - 1
            if toks[j].text == ")":
                depth = 0
                while j >= 0:
                    if toks[j].text == ")":
                        depth += 1
                    elif toks[j].text == "(":
                        depth -= 1
                        if depth == 0:
                            break
                    j -= 1
                callee = toks[j - 1].text if j > 0 else ""
                if j > 0 and toks[j - 1].kind == "Word" and callee not in KEYWORDS and callee != "fn" and toks[j - 2].text not in ("let", "class", "interface", ".") and not (j > 1 and toks[j - 2].kind == "Word"):
                    st.add("block arguments", "trailing block after ( )")


def run_fmt(temper: str, sources: list[Source]) -> None:
    """Formats copies of the files with `temper fmt` and counts changes."""
    import difflib
    import shutil

    work = WORK / "fmt"
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)
    copies = []
    for i, s in enumerate(sources):
        copy = work / f"{i:05}{s.suffix}"
        if s.path is not None:
            shutil.copyfile(s.path, copy)
        else:
            copy.write_text(s.text, "utf-8")
        copies.append(copy)
    result = subprocess.run([temper, "fmt", str(work)], capture_output=True, text=True)
    changed: Counter = Counter()
    lines: Counter = Counter()
    files: Counter = Counter()
    for s, copy in zip(sources, copies):
        before = (s.path.read_text("utf-8") if s.path else s.text).splitlines()
        after = copy.read_text("utf-8").splitlines()
        lines[s.group] += len(before)
        diff = [d for d in difflib.ndiff(before, after) if d.startswith("- ")]
        changed[s.group] += len(diff)
        files[s.group] += bool(diff)
    print("temper fmt: lines changed (of all lines, prose included), files changed:")
    for g in lines:
        print(f"  {g}: {changed[g]} of {lines[g]} ({pct(changed[g], lines[g])}), {files[g]} files")
    total = sum(changed.values())
    print(f"  all: {total} of {sum(lines.values())} ({pct(total, sum(lines.values()))})")
    for line in result.stderr.splitlines():
        index = int(Path(line.split(": ")[1]).name[:5]) if ": " in line and "/fmt/" in line else None
        print(f"  {sources[index].name if index is not None else ''}: {line.split(': ', 2)[-1]}")


def pct(n, total):
    return f"{100 * n / total:.1f}%" if total else "-"


def report(stats: dict[str, Stats]) -> None:
    total = Stats()
    for st in stats.values():
        for k, c in st.c.items():
            total.c[k].update(c)
        total.changed.update(st.changed)
        total.lines += st.lines
        total.widths += st.widths
        for k, v in st.samples.items():
            total.samples[k] += v
    print(f"code lines: {total.lines}; by group: " + ", ".join(f"{g} {st.lines}" for g, st in stats.items()))
    w = sorted(total.widths)
    if w:
        q = lambda p: w[min(len(w) - 1, int(p * len(w)))]
        print(f"line width: p50 {q(0.5)}, p90 {q(0.9)}, p99 {q(0.99)}, max {w[-1]}; "
              + ", ".join(f">{n}: {sum(x > n for x in w)}" for n in (80, 100, 120)))
        for g, st in stats.items():
            ws = st.widths
            print(f"  {g}: " + ", ".join(f">{n}: {sum(x > n for x in ws)}" for n in (80, 100, 120)) + f", max {max(ws, default=0)}")
    print()
    for item in sorted(total.c):
        c = total.c[item]
        n = sum(c.values())
        print(f"{item} ({n}): " + ", ".join(f"{k} {pct(v, n)}" for k, v in c.most_common(6)))
    print()
    print("lines each rule would change:")
    for rule, n in sorted(total.changed.items()):
        print(f"  {rule}: {n} ({pct(n, total.lines)})")
    if total.samples:
        print()
        for k, v in sorted(total.samples.items()):
            print(f"samples, {k}:")
            for x in v[: SAMPLES]:
                print(f"  {x}")


SAMPLES = 0


def main() -> None:
    global SAMPLES
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("libraries", nargs="*", type=Path)
    parser.add_argument("--samples", type=int, default=0, help="show N examples per item")
    parser.add_argument("--temper", help="a temper binary to measure `temper fmt` with")
    args = parser.parse_args()
    SAMPLES = args.samples
    subprocess.run(["cargo", "build", "-q", "--release", "-p", "temper-syntax", "--bins"], cwd=ROOT, check=True)
    sources = collect(args.libraries, str(ROOT / "target/release/literate-blocks"))
    run_bins(sources)
    stats: dict[str, Stats] = {}
    for s in sources:
        st = stats.setdefault(s.group, Stats())
        analyze(s, st, args.samples)
        spacing(s, st, args.samples)
        blank_lines(s, st)
        braces(s, st)
        calls(s, st, args.samples)
        trailing_blocks(s, st)
    report(stats)
    if args.temper:
        print()
        run_fmt(args.temper, sources)


if __name__ == "__main__":
    main()
