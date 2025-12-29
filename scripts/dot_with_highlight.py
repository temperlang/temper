import click
import re

gv_node_re = re.compile(r'^\s+(?:"((?:[^"\\]|\\.)*)"|\w+)\s*(?:;|\[)')
gv_color_re = re.compile(
    r'\b(?:color|bgcolor|fillcolor)\s*=\s*(?:\w+|"(?:[^"\\]|\\.)*")', re.I
)
gv_attr_end_re = re.compile(r"(\]\s*)?;\s*$")


def add_highlight(match):
    lead = " " if match.group(1) else " ["
    colors = 'color="#b89a0f" fillcolor="#fceea7"'
    close = "" if match.group(1) else "]"
    return f"{lead}{colors}{close}{match.group()}"


@click.command(
    help="Emits a dot file but with nodes whose names match /pattern/ highlighted.",
    epilog="""
        PATTERN is a regular expression pattern of nodes to highlight.
        DOT_FILE is a source dot file.
    """,
)
@click.argument("pattern")
@click.argument("dot_file", type=click.Path(exists=True))
def main(pattern, dot_file):
    node_name_re = re.compile(pattern)
    out_lines = []

    with open(dot_file, "r") as fh:
        for line_no, line in enumerate(fh):
            match = graphviz_node.match(line)
            if not match:
                continue
            node_name = f"{match.group(1)}{match.group(2)}"
            # A node defining line is one that has something that looks like
            # a node name followed by a semicolon or an attribute bracket.
            if not node_name_re.search(node_name):
                continue
            line = gv_color_re.sub(" ", line)

            line, n = gv_attr_end_re.subn(add_highlight, line)
            if not n:
                raise ValueError(f"{line_no + 1}: Could not highlight: {line}")
            lines.append(line)

    print("\n".join(lines))
