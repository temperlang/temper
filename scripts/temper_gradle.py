from util import temper_path
import os
from os.path import curdir, join
import sys


def quote_args_string(args):
    """
    This is going to JavaExec; see below for parsing rules.
    https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/JavaExec.html#setArgsString-java.lang.String-

    TODO: bypass this and pass through a JSON blob.
    """

    def quote_one(arg):
        has_space = " " in arg
        has_quote = "'" in arg
        has_dquote = '"' in arg
        if not has_space and not has_quote and not has_dquote:
            return arg
        if not has_quote:
            return f"'{arg}'"
        if not has_dquote:
            return f'"{arg}"'
        raise ValueError(f"Can't quote argument, has single and double quotes: {arg}")

    return " ".join(quote_one(arg) for arg in args)


def main():
    """
    Run temper via gradle with arguments.

    Usage: temper-gradle [ARGS]...

    Caveat: the repl won't function.
    """
    os.chdir(temper_path())
    ex = join(curdir, "gradlew")
    os.execl(
        ex,
        ex,
        "--continue",
        "cli:temper",
        "--rerun",
        f"-PcliArgs={quote_args_string(sys.argv[1:])}",
    )
