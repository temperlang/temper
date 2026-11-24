# /// script
# requires-python = ">=3.11"
# ///
"""Compares temper-syntax's grammar step with Kotlin Temper's on mutated inputs.

Like fuzz_parse.py, but for check_ast.py's trees, with extra edits that
matter to the grammar. Inputs whose trees differ are shrunk and saved in
target/fuzz-ast/found/ as seedN-NNN.temper(.md), with the original beside it.
Inputs that crash Kotlin are kept apart, as crash-*, and so are inputs whose
parse trees already differ, as parse-* (unshrunk; see fuzz_parse.py for
those). Differences that a deliberate deviation explains (see deviations.py)
are only counted.

    uv run compat/fuzz_ast.py [--seed N] [--batches N] [--size N] [--module] [DIR ...]

With --module, both sides also rewrite call joins and convert metadata to
JSON, as check_ast.py --module does, and found inputs go in
target/fuzz-module/found/.

Needs the same setup as check_lex.py.
"""

import argparse
import random
import sys
from pathlib import Path

from check_ast import CASE_INPUTS, CASES, MODULE_CASE_INPUTS, MODULE_CASES, commands, mask_delegate_spans
from check_lex import DEFAULT_DIRS, ROOT, temper_files, write_cases
from check_parse import CASE_INPUTS as PARSE_CASE_INPUTS, CASES as PARSE_CASES, commands as parse_commands
from deviations import REASONS, classify
from fuzz_lex import MAX_SOURCE_BYTES, outputs, shrink_while
from fuzz_parse import mutate_parse

WORK = ROOT / "target/fuzz-ast"

# Pieces that matter to the grammar. Keep in sync with AST_PIECES in
# crates/temper-syntax/tests/ast.rs.
AST_PIECES = [
    "@", "@A ", "let ", "var ", "fn ", "class ", "if (x) ", "} else {", "(", ")", "{", "}", "[",
    "]", ",", ";", ";;", ";;;", ":", "=", "=>", "->", "...", "case ", "is ", "of ", "extends ",
    "supports ", "\\(", "\\{", "${", "\\u{", "/** doc */", "// c\n", "builtins.", "this", "super",
    "new ", "<", ">", "0x", "1e", "i64", '"', "'", "/x/", 'rgx"', "try ", " catch (e) ",
    " catch ", "error case ",
]

# Pieces for call joins and metadata, with --module.
MODULE_PIECES = ["} else if (y) {", "} g {", '\n;;;\n{"a": [1, ', "-", "i64", '"k": ', "null", "]", "}"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("dirs", nargs="*", type=Path, default=DEFAULT_DIRS)
    parser.add_argument("--seed", type=int, default=1)
    parser.add_argument("--batches", type=int, default=5)
    parser.add_argument("--size", type=int, default=2000, help="inputs per batch")
    parser.add_argument("--module", action="store_true", help="add ParseStage's steps, for a library")
    args = parser.parse_args()

    global WORK
    builders = commands()
    parsers = parse_commands()
    cases = write_cases(PARSE_CASES, PARSE_CASE_INPUTS, "tree") | write_cases(CASES, CASE_INPUTS, "ast")
    pieces = AST_PIECES
    if args.module:
        WORK = ROOT / "target/fuzz-module"
        for command in builders:
            command.append("--module")
        cases |= write_cases(MODULE_CASES, MODULE_CASE_INPUTS, "module")
        pieces = AST_PIECES + MODULE_PIECES
    files = sorted(cases) + temper_files(args.dirs)
    sources = [p for p in files if Path(p).stat().st_size <= MAX_SOURCE_BYTES]
    texts = [(Path(p).read_text("utf-8"), p.endswith(".md")) for p in sources]
    rng = random.Random(args.seed)
    found = WORK / "found"
    found.mkdir(parents=True, exist_ok=True)

    def kinds(inputs, label):
        """Per input: None if the trees match, else "crash" if Kotlin
        crashed, a deviation's name if one explains it, "parse" if the parse
        trees already differ, or "differ"."""
        results = []
        for (text, markdown), (kotlin, rust) in zip(inputs, outputs(builders, inputs, WORK / label)):
            if args.module:
                kotlin = kotlin and mask_delegate_spans(kotlin)
                rust = rust and mask_delegate_spans(rust)
            if kotlin == rust:
                results.append(None)
            elif kotlin and any(line.startswith("Crash") for line in kotlin):
                results.append("crash")
            else:
                results.append(classify(text, markdown, kotlin or [], rust or []) or "differ")
        suspects = [i for i, kind in enumerate(results) if kind == "differ"]
        parsed = outputs(parsers, [inputs[i] for i in suspects], WORK / "parse")
        for i, (kotlin, rust) in zip(suspects, parsed):
            if kotlin != rust:
                results[i] = "parse"
        return results

    def first(kind: str):
        def test(candidates):
            return next((c for c, k in zip(candidates, kinds(candidates, "shrink")) if k == kind), None)
        return test

    checked = 0
    count: dict[str, int] = {"differ": 0, "crash": 0, "parse": 0}
    for batch in range(args.batches):
        inputs = []
        for _ in range(args.size):
            text, markdown = rng.choice(texts)
            inputs.append((mutate_ast(text, rng, pieces), markdown))
        for (text, markdown), kind in zip(inputs, kinds(inputs, f"batch{batch}")):
            if kind is None:
                continue
            count[kind] = count.get(kind, 0) + 1
            if kind in REASONS:
                continue
            prefix = f"seed{args.seed}-" + ("" if kind == "differ" else f"{kind}-") + f"{count[kind]:03}"
            suffix = ".temper" + (".md" if markdown else "")
            (found / f"{prefix}-original{suffix}").write_text(text, "utf-8", newline="")
            if kind == "parse" or (kind == "crash" and count[kind] > 5):
                continue
            small = shrink_while(first(kind), text, markdown)
            (found / f"{prefix}{suffix}").write_text(small, "utf-8", newline="")
            print(f"{kind}: {found / (prefix + suffix)} ({len(text)} chars shrunk to {len(small)})")
        checked += len(inputs)
        print(
            f"batch {batch}: {checked} inputs checked, {count['differ']} differ, "
            f"{count['crash']} crash Kotlin, {count['parse']} parse differently",
            flush=True,
        )
    for reason in REASONS:
        if count.get(reason):
            print(f"{count[reason]} inputs may differ as expected: {REASONS[reason]} ({reason})")
    return 1 if count["differ"] else 0


def mutate_ast(text: str, rng: random.Random, pieces: list[str] = AST_PIECES) -> str:
    """Edits like fuzz_parse.mutate_parse, plus inserting grammar pieces."""
    text = mutate_parse(text, rng)
    for _ in range(rng.randint(0, 2)):
        at = rng.randint(0, len(text))
        text = text[:at] + rng.choice(pieces) + text[at:]
    return text


if __name__ == "__main__":
    sys.exit(main())
