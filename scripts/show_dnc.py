import click
from util import git_cap, dnc_uncensor
from os import linesep
import re


@click.command(
    "show-dnc",
    help=dnc_uncensor("This command searches changes for 'dø nøt cømmit' strings."),
)
def main():
    dnc_pat = re.compile(dnc_uncensor(r"DØ.*NØT.*CØMMIT"), re.I)
    hl_col = "\033[91m"
    end_col = "\033[0m"

    data = git_cap("diff") + git_cap("diff", "--cached")
    diff_line = None
    atat_block = []
    dnc_found = False
    output = False

    def show_block():
        nonlocal dnc_found, diff_line
        if not dnc_found:
            atat_block.clear()
            return
        dnc_found = False
        if diff_line:
            print(diff_line)
            diff_line = None
        for line in atat_block:
            print(line)

    atat_pat = re.compile(r"@@ .+? @@")
    for line in data.split(linesep):
        if line.startswith("diff"):
            show_block()
            diff_line = line
            continue
        if atat_pat.search(line):
            show_block()
            atat_block.append(line)
            continue
        if line.startswith("-"):
            atat_block.append(line)
            continue

        match = dnc_pat.search(line)
        if not match:
            atat_block.append(line)
            continue
        dnc_found = output = True
        begin, end = match.span()
        atat_block.append(
            f"{line[:begin]}{hl_col}{line[begin:end]}{end_col}{line[end:]}"
        )
    show_block()
    if not output:
        print(dnc_uncensor("<No DØ NØT CØMMIT messages found.>"))
