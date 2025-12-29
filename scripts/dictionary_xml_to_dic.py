import click
from glob import iglob
import re
from util import temper_path

pat = re.compile(r"\s*<w>(\w+)</w>\s*$")


@click.command(
    help="""
        Convert .idea/dictionaries/*.xml to hunspell format.

        Searches .idea/dictionaries/*.xml at input, and converts them to
        hunspell format.
        """,
    epilog="""
        The default glob may be overridden by specifying paths as arguments.

        Running this script allows using IntelliJ's "add to dictionary" affordance and still
        keeping `project/dictionary.doc` authoritative.
        See the ../README.md for info about the project dictionary and how
        to configure IntelliJ to use it.
        """,
)
@click.argument("paths", type=click.Path(exists=True), nargs=-1)
def main(paths):
    matches = set()
    for path in paths or iglob(temper_path(".idea", "dictionaries", "*.xml")):
        with open(path, "r") as fh:
            for line in fh:
                m = pat.match(line)
                if m:
                    matches.add(m.groups(1))
    for word in sorted(matches):
        print(word)
