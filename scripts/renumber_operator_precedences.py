import click
import math
import os
import re
import sys
from util import first_index, temper_path

prec_line_pat = re.compile(r"^(\s*\w+\()(\-?[\.\d]+)(\s*,)")


@click.command(help="Renumber operator precedence in Operator.kt.")
@click.option("--verbose/", "-v/", type=bool, flag_value=True, default=False)
def main(verbose):
    path = temper_path(
        "lexer", "src", "commonMain", "kotlin", "lang", "temper", "lexer", "Operator.kt"
    )
    if not os.path.isfile(path):
        raise ValueError(f"No file at {path}")
    print(f"renumbering {path}", file=sys.stderr)

    # Split the file into chunks
    before = []  # join to one string later
    prec_lines = []  # leave as a list
    after = []  # join to one string

    with open(path, "rt") as fh:
        stop_pat = re.compile(r"^\s*\) : Structured \{")
        for line in (lines := iter(fh)):
            before.append(line)
            if stop_pat.search(line):
                break
        stop_pat = re.compile(r"^\s+;$")
        for line in lines:
            prec_lines.append(line)
            if stop_pat.match(line):
                break
        for line in lines:
            after.append(line)
    before = "".join(before)
    after = "".join(after)

    # Find unique precedences
    precedences = {}
    # Comma precedence should be 0 so statementy stuff is neg and expressiony stuff
    # positive.
    comma_precedence = None
    for prec_line in prec_lines:
        if match := prec_line_pat.match(prec_line):
            # We're not doing math on these float values, so should be ok as keys.
            precedence = float(match.group(2))
            precedences[precedence] = None
            if match.group(1) == "comma":
                comma_precedence = precedence

    unique_precedences = sorted(precedences.keys())
    min_precedence = math.floor(
        -first_index(lambda x: x == comma_precedence, unique_precedences)
        if comma_precedence is not None
        else unique_precedences[0]
    )
    if verbose:
        print(
            f"Starting {min_precedence} when renumbering {unique_precedences}",
            file=sys.stderr,
        )

    # Map unqiue precedences to distinct integers
    for i in range(len(unique_precedences)):
        precedences[unique_precedences[i]] = min_precedence + i

    if verbose:
        print(f"unique_precedences={unique_precedences}", file=sys.stderr)
        for unique_precedence in unique_precedences:
            new_precedence = precedences[unique_precedence]
            print(f"{unique_precedence} => {new_precedence}", file=sys.stderr)

    def prec_replace(match):
        return f"{match.group(1)}{precedences[float(match.group(2))]}{match.group(3)}"

    middle = "".join(
        prec_line_pat.sub(prec_replace, prec_line) for prec_line in prec_lines
    )
    if verbose:
        print(f"move {path}, {path}.bak", file=sys.stderr)
    os.rename(path, f"{path}.bak")
    with open(path, "wt") as out_fh:
        out_fh.write(before)
        out_fh.write(middle)
        out_fh.write(after)
