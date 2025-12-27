import click
from glob import iglob
import re
from os import rename
import sys


import_pat = re.compile(r"^import\b")


def sort_imports(f):
    with open(f, "r") as fh:
        orig = fh.read()

    lines = orig.split("\n")
    if not lines[-1]:
        del lines[-1]
    sorted_lines = []
    i = 0
    while i < len(lines):
        line = lines[i]
        i += 1

        if not import_pat.match(line):
            sorted_lines.append(f"{line}\n")
            continue
        imports = set([f"{line}\n"])
        while i < len(lines):
            line = lines[i]
            if not import_pat.match(line):
                break
            i += 1
            imports.add(f"{line}\n")
        sorted_lines.extend(sorted(imports))
    all_sorted = "".join(sorted_lines)
    if orig != all_sorted:
        print(f"{f} changed", file=sys.stderr)
        rename(f, f"{f}.bak")
        with open(f, "w") as fh:
            fh.write(all_sorted)


@click.command(
    "sort-imports",
    help="Sorts runs of adjacent lines that start with the keyword `import` in ./**/*.kt",
)
def main():
    for path in iglob("**/*.kt", recursive=True):
        sort_imports(path)
