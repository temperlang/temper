import click
from importlib import import_module
from inspect import getdoc
import os
from os.path import dirname, join
import toml
from textwrap import wrap
from traceback import format_exception


class ScriptInfo:
    def __init__(self, cmd_name, short_help, help, doc, problem):
        self.cmd_name = cmd_name
        # Metadata from Click
        self.short_help = short_help
        self.help = help
        # The docstring; should be empty if Click is available
        self.doc = doc
        self.problem = problem

    def name(self):
        return self.cmd_name

    def one_liner(self):
        "Obtain the description in a single line."
        if self.problem:
            prefix = f"Error: <{self.problem}>"
        else:
            prefix = ""

        def lines():
            yield from (self.short_help or "").split("\n")
            yield from (self.help or "").split("\n")
            yield from (self.doc or "").split("\n")

        for line in lines():
            if line:
                return f"{prefix}{line.strip()}"
        return prefix or "<no help>"

    def long_help(self):
        "Obtain the best long help available."
        lines = []
        if self.problem is not None:
            lines.append("While looking up information:")
            for line in format_exception(self.problem):
                lines.append("    " + line)
        if self.help:
            lines.extend(self.help.split("\n"))
        elif self.doc:
            lines.extend(self.doc.split("\n"))
        if not any(lines):
            lines = ["<no help>"]
        return lines


def inspect_script(cmd_name, invoke):
    """
    Fetch the function object and look up help details.

    Design note: we're avoiding invoking commands to be safe.
    Importing a module could inadvertently
    """
    (mod_str, func_name) = invoke.split(":")
    problem = short_help = help = doc = None
    try:
        func = getattr(import_module(mod_str), func_name)
    except Exception as exc:
        problem = exc
    else:
        try:
            # This only looks at func.__doc__
            doc = getdoc(func)
        except Exception as exc:
            # It's very unlikely this fails.
            problem = exc
        # Look for click metadata.
        short_help = getattr(func, "short_help", None) or getattr(func, "help", None)

        # This is a bit more elaborate, but
        # it avoids actually trying to run the command.
        try:
            if isinstance(func, click.core.Command):
                ctx = func.context_class(info_name=cmd_name, command=func)
                fmt = click.formatting.HelpFormatter()
                func.format_help(ctx, fmt)
                help = "".join(fmt.buffer)
        except Exception as exc:
            problem = exc

    return ScriptInfo(cmd_name, short_help, help, doc, problem)


@click.command(
    help="List information about scripts available here.",
    epilog="Commands may be specified to get help only on those commands; implies -v.",
)
@click.argument("commands", nargs=-1, type=str)
@click.option(
    "-v/",
    "--verbose/",
    type=bool,
    default=False,
    flag_value=True,
    help="list detailed information for all commands",
)
def main(commands, verbose):
    with open(join(dirname(__file__), "pyproject.toml"), "r") as fh:
        pyproject = toml.load(fh)

    scripts = pyproject["tool"]["poetry"]["scripts"]

    if commands or verbose:
        for name in commands or sorted(scripts.keys()):
            print()
            invoke = scripts.get(name)
            if invoke:
                info = inspect_script(name, scripts[name])
                print(name)
                for line in info.long_help():
                    print(f"    {line}")
            else:
                print(name)
                print("    <no command found>")
        return

    print("Commands may be run as ./scr COMMAND ARGS.")
    print()
    for cmd_name, invoke in sorted(scripts.items()):
        info = inspect_script(cmd_name, invoke)
        if cmd_name == 'list':
            lines = ['Alias to help.']
        else:
            lines = wrap(info.one_liner(), 50)
        if len(cmd_name) > 20:
            print(cmd_name)
            lead = ""
        else:
            lead = cmd_name
        for line in lines:
            print(f"{lead:22}{line}")
            lead = ""

    print()
    print("See ./scr help COMMAND for more details.")
    print()
