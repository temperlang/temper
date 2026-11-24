"""Where tempest deliberately differs from Kotlin Temper, for the compat
scripts: the reasons, the inputs known to differ, and a classifier for
fuzzed inputs. Each reason is described in notes/kotlin-differences.md.
This exists only while we compare with Kotlin.
"""

import re

REASONS = {
    "quoted-code": "code blocks in markdown block quotes are prose",
    "multi-quoted": "clearer rules for multi-quoted strings and scriptlet lines",
    "nested-hole": "`${` nests in a hole, as elsewhere in code",
    "spans": "spans cover all of a node or token's parts",
    "join-spans": "spans of what rewriting a call join makes cover what it wraps",
    "brackets": "brackets that don't balance are recovered from locally",
    "missing-operator": "operands side by side with no operator keep their statement",
    "jump-operand": "`return`, `throw`, or `yield` is the operand an operator lacks",
    "jump-comma": "a comma after a jump's operand ends the jump, an error",
    "failures": "`try` and `catch` are operators, and `error case` a declaration (RFC 164)",
}

# What our parse and grammar step outputs show for bracket recovery.
BRACKET_RECOVERY = re.compile(r"B\t|E\t\w+\t(MissingCloseBracket|UnmatchedCloseBracket|UnclosedAngle)")


# What our grammar step output shows for operands with no operator between.
MISSING_OPERATOR = re.compile(r"E\t\w+\tMissingOperator")


def missing_operator(kotlin: list[str], rust: list[str]) -> bool:
    """Whether our output recovers from operands with no operator between
    them, where Kotlin's reports an error too."""
    return any(MISSING_OPERATOR.match(line) for line in rust) and any(
        line.startswith("E\t") for line in kotlin
    )


# Kotlin's error for an operator short of operands, and our nodes for the
# jumps that can be that operand.
TOO_FEW_OPERANDS = re.compile(r"E\tTooFewOperands\t")
JUMP_NODE = re.compile(r"N\t\d+\t(Return|Throw|Yield)\b")


def jump_operand(kotlin: list[str], rust: list[str]) -> bool:
    """Whether our parse makes a `return`, `throw`, or `yield` the operand
    an operator lacks, where Kotlin reports too few operands."""
    return (
        sum(bool(TOO_FEW_OPERANDS.match(line)) for line in rust)
        < sum(bool(TOO_FEW_OPERANDS.match(line)) for line in kotlin)
        and any(JUMP_NODE.match(line) for line in rust)
    )


# Our parse and grammar step errors for a comma after a jump's operand.
JUMP_BEFORE_COMMA = re.compile(r"E\t(error\t)?JumpBeforeComma\b")


def jump_comma(rust: list[str]) -> bool:
    """Whether our output ends a jump before a comma, where Kotlin's takes
    the comma list and reports no argument in it."""
    return any(JUMP_BEFORE_COMMA.match(line) for line in rust)


def recovered(rust: list[str]) -> bool:
    """Whether our output shows bracket recovery, which Kotlin lacks."""
    return any(BRACKET_RECOVERY.match(line) for line in rust)

# Inputs known to differ, by corpus path suffix or "cases file: case name",
# with the reason, and the one check they differ in ("lex", "parse" or "ast")
# if not all.
KNOWN = {
    "stage-tests/parse/bad-unicode-scalar-values/work/test/test.temper": ("spans", "parse"),
    "edge-cases.cases: holes in strings": "multi-quoted",
    "edge-cases.cases: unclosed delimiters": "nested-hole",
    "kotlin-token-cluster-tests.cases: unclosedMQ4K": "nested-hole",
    "kotlin-token-cluster-tests.cases: junkInInterp3A3B3C3F3G3I3L": "nested-hole",
    "kotlin-token-cluster-tests.cases: mQInStmtBlock4E": "multi-quoted",
    "kotlin-parse-tests.cases: stringEscapes": "spans",
    "kotlin-token-adapter-tests.cases: stringTemplateWithEmbeddedStatementFragments": ("multi-quoted", "parse"),
    "kotlin-token-adapter-tests.cases: stringsOnStringTemplateLine": "multi-quoted",
    "multi-quoted.cases: *": "multi-quoted",
    "edge-cases.cases: return, throw, and yield as an operator's missing operand, which Kotlin reports as too few operands": "jump-operand",
    "edge-cases.cases: a comma after a return, throw, or yield's operand ends it, an error": "jump-comma",
    "edge-cases.cases: a comma after a return or throw's operand: an error, and the rest still parses": "jump-comma",
    "edge-cases.cases: return's operand runs to the end of its statement or bracket, or to a comma, as before": "jump-operand",
    "failures.cases: *": "failures",
    "kotlin-parse-tests.cases: tryWithValue": "failures",
    "kotlin-parse-tests.cases: tryWithCatchAndFinally": "failures",
    "kotlin-parse-tests.cases: colonsAroundTry": "failures",
    "kotlin-build-tree-tests.cases: minimalTryCatch": "failures",
}


def known(name: str, check: str) -> str | None:
    """The reason an input is known to differ in a check, "lex" or "parse",
    given its path or case name."""
    for key, reason in KNOWN.items():
        if isinstance(reason, tuple):
            reason, only = reason
            if only != check:
                continue
        if key.endswith(": *") and name.startswith(key[:-1]):
            return reason
        if name == key or name.endswith("/" + key):
            return reason
    return None


def known_file(name: str) -> bool:
    """Whether a case is listed by its whole cases file."""
    return any(key.endswith(": *") and name.startswith(key[:-1]) for key in KNOWN)


def classify(text: str, markdown: bool, kotlin: list[str], rust: list[str]) -> str | None:
    """For a fuzzed input that differs, a deviation that explains it, or
    None. Works on token lines (LexTokens.java), parse tree lines
    (ParseTree.java) and grammar step lines (AstTree.java).

    A difference is excused only where it starts: the first lines where the
    two outputs disagree must overlap a region the deviation is about (a
    block quote, a multi-quoted string, or from a `$` before `{` that Kotlin
    makes an error). Differences that start elsewhere are reported, even in
    inputs with those features. Differences in positions alone are the
    "spans" deviation. Inputs whose brackets don't balance differ wherever
    we recovered, so they're all the "brackets" deviation."""
    if recovered(rust):
        return "brackets"
    if missing_operator(kotlin, rust):
        return "missing-operator"
    if jump_operand(kotlin, rust):
        return "jump-operand"
    if jump_comma(rust):
        return "jump-comma"
    if strip_spans(kotlin) == strip_spans(rust):
        return "spans"
    if strip_spans(without_comments(kotlin)) == strip_spans(without_comments(rust)):
        # Embedded comments are found between node spans, so a comment in an
        # operand that Kotlin leaves out of a separator's span shows up
        # before the separator there.
        return "spans"
    first, last = first_difference(text, kotlin, rust)
    overlaps = lambda regions: any(start <= last and first <= end for start, end in regions)
    if overlaps(failure_words(text)):
        return "failures"
    for a, b in zip(kotlin, kotlin[1:]):
        if re.match(r"(T\t\d+\t)?Error\t", a) and a.endswith("\t$") and "{" in b.split("\t")[-1]:
            span = offsets(a)
            if span is not None and last >= span[0]:
                return "nested-hole"
    if markdown and overlaps(quoted_regions(text)):
        return "quoted-code"
    if overlaps(multi_quoted_regions(text)):
        return "multi-quoted"
    return None


def failure_words(text: str) -> list[tuple[int, int]]:
    """Byte ranges of the words RFC 164's syntax adds: `try`, `catch`, and
    `error case`."""
    data = text.encode("utf-8")
    return [m.span() for m in re.finditer(rb"\b(try|catch|error\s+case)\b", data)]


def offsets(line: str) -> tuple[int, int] | None:
    """The source span an output line is about, if it has one."""
    parts = line.split("\t")
    index = {"T": 3, "N": 3, "L": 3, "V": 2, "X": 2, "C": 2, "E": -2}.get(parts[0], 1)
    try:
        return int(parts[index]), int(parts[index + 1])
    except (IndexError, ValueError):
        return None


def first_difference(text: str, kotlin: list[str], rust: list[str]) -> tuple[int, int]:
    """The source span where the outputs start to disagree: that of the
    first line with a position on each side, from the first that differs."""
    i = 0
    while i < len(kotlin) and i < len(rust) and kotlin[i] == rust[i]:
        i += 1
    end = len(text.encode("utf-8"))
    found = []
    for lines in (kotlin, rust):
        for line in lines[i:]:
            span = offsets(line)
            if span is not None:
                found.append(span)
                break
    if not found:
        return end, end
    return min(s for s, _ in found), max(e for _, e in found)


def byte_offsets(text: str) -> list[int]:
    """UTF-8 offsets of each line's start, then the end."""
    starts, at = [], 0
    for line in text.splitlines(keepends=True):
        starts.append(at)
        at += len(line.encode("utf-8"))
    return starts + [at]


def quoted_regions(text: str) -> list[tuple[int, int]]:
    """Byte ranges of markdown block quotes: runs of lines starting `>`."""
    lines = text.splitlines(keepends=True)
    starts = byte_offsets(text)
    regions = []
    for i, line in enumerate(lines):
        if re.match(r" {0,3}>", line):
            if regions and regions[-1][1] == starts[i]:
                regions[-1] = (regions[-1][0], starts[i + 1])
            else:
                regions.append((starts[i], starts[i + 1]))
    return regions


def multi_quoted_regions(text: str) -> list[tuple[int, int]]:
    """Byte ranges of multi-quoted strings: from each `\"\"\"` through the
    following lines that start with a margin character, `\"`, `~` or `:`,
    after space or a markdown quote, or are blank or comments, and on to the next
    character that isn't space, where the string's end may first show. Our
    rules for scriptlet lines (`:`) change what blocks are open after the
    string, so a string with them reaches to the end."""
    lines = text.splitlines(keepends=True)
    starts = byte_offsets(text)
    regions = []
    for i, line in enumerate(lines):
        col = line.find('\"\"\"')
        if col < 0:
            continue
        start = starts[i] + len(line[:col].encode("utf-8"))
        j, scriptlets, last = i + 1, False, i + 1
        while j < len(lines):
            margin = re.match(r"[ \t>]*([\"~:])", lines[j])
            if margin:
                scriptlets |= margin.group(1) == ":"
                last = j + 1
            elif lines[j].strip() and not lines[j].lstrip().startswith("//"):
                break
            j += 1
        end = starts[-1] if scriptlets else starts[last]
        if not scriptlets:
            rest = text.encode("utf-8")[end:]
            end += len(rest) - len(rest.lstrip())
        regions.append((start, end))
    return regions


def without_comments(lines: list[str]) -> list[str]:
    """Grammar step tree lines without embedded comment calls (to `REM`),
    and a root block left with one statement replaced by that statement."""
    out: list[str] = []
    skip_below = None
    for i, line in enumerate(lines):
        parts = line.split("\t")
        depth = int(parts[1]) if parts[0] in "NLVX" and len(parts) > 1 and parts[1].isdigit() else None
        if skip_below is not None:
            if depth is not None and depth > skip_below:
                continue
            skip_below = None
        following = lines[i + 1].split("\t") if i + 1 < len(lines) else []
        if parts[0] == "N" and parts[2] == "Call" and following[-1:] == ["REM"] and following[0] == "V":
            skip_below = depth
            continue
        out.append(line)
    roots = [line for line in out if line.split("\t")[:2] in (["N", "1"], ["L", "1"], ["V", "1"], ["X", "1"])]
    if out and out[0].startswith("N\t0\tBlock") and len(roots) == 1:
        out = [shift(line) for line in out[1:]]
    return out


def shift(line: str) -> str:
    """A tree line one level up."""
    parts = line.split("\t")
    if parts[0] in "NLVX" and len(parts) > 1 and parts[1].isdigit():
        parts[1] = str(int(parts[1]) - 1)
    return "\t".join(parts)


def strip_spans(lines: list[str]) -> list[str]:
    """Output lines without positions: parse tree lines without token and
    error positions, and grammar step lines without any."""
    out = []
    for line in lines:
        parts = line.split("\t")
        if parts[0] in ("T", "L") or (parts[0] == "N" and len(parts) == 5):
            parts[3:5] = []
        elif parts[0] in ("V", "X"):
            parts[2:4] = []
        elif parts[0] == "E":
            parts[-2:] = []
        out.append("\t".join(parts))
    return out
