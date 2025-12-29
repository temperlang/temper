from functools import cache
from os import fstat, linesep
from os.path import dirname, join
from shutil import which
import stat
from subprocess import run


@cache
def git_path():
    """
    Find the git CLI as a tuple to add to arguments.
    """
    return which("git")


def temper_path(*descent):
    """
    Find temper git repository root by searching up from this file's path.

    The `package-mode = false` line in pyproject.toml forces using these scripts
    as a standalone virtualenv.
    """
    return join(dirname(dirname(__file__)), *descent)


def git_cap(*args, cwd=None, chomp=False):
    """
    Invoke git with the given args and working directory and capture output.
    """
    result = run((git_path(),) + args, capture_output=True, cwd=cwd)
    out = result.stdout.decode("utf8")
    return out.rstrip("\r\n") if chomp else out


def git_pass(*args, cwd=None):
    """
    Invoke git with the given args and working directory and
    returns returncode.
    """
    return run((git_path(),) + args, cwd=cwd).returncode


def sort_uniq(*data, key=None):
    """
    Given text output from one or more commands, concatenate it, remove
    duplicates, and sort the results as though it had been piped to
    sort and uniq.
    """
    lines = set()
    for dat in data:
        lines.update(dat.split(linesep))
    lines.discard('')  # drop blanks
    return sorted(lines, key=key)


def dnc_uncensor(text):
    """
    Since we scan for "DØ NØT CØMMIT" we "censor" the text
    to avoid our own search. This uncensors it.
    """
    return text.replace("ø", "o").replace("Ø", "O")


def chmod_plusx(fd):
    """
    Given a file descriptor, e.g. `open('foo').fileno()`,
    set all executable bits where read-access is present.

    Return true if any change was made.
    """
    # Import fchmod locally because not on Windows until Python 3.13.
    # And we don't want to break people who don't need this function.
    from os import fchmod
    prior_mode = mode = fstat(fd).st_mode
    if mode & stat.S_IRUSR:
        mode |= stat.S_IXUSR
    if mode & stat.S_IRGRP:
        mode |= stat.S_IXGRP
    if mode & stat.S_IROTH:
        mode |= stat.S_IXOTH
    if mode != prior_mode:
        fchmod(fd, mode)
    return mode != prior_mode


def first_index(predicate, iterable):
    """
    Given a predicate and an iterable, find the first index of the
    element where predicate is True.
    Except that we return None instead of -1, this is a
    equivalent to perl's List::MoreUtils::first_index
    """
    for index, elem in enumerate(iterable):
        if predicate(elem):
            return index
    return None
