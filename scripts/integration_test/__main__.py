import click
import os
import pathlib as p
import platform
import shutil as sh
import subprocess as sp
import sys
import tempfile
import typing as t
from .checks import Checks
from .download import download


class Args(t.TypedDict):
    root: str
    tag: t.Optional[str]
    temper: t.Optional[str]


def choose_root(*, root: t.Optional[str]) -> str:
    if root is not None:
        if not p.Path(root).exists():
            raise RuntimeError(f"not found: {root}")
        return root
    # Loop through parents until we recognize things.
    # More flexible than looping from the current script file.
    current = p.Path.cwd()
    result: t.Optional[p.Path] = None
    while True:
        if (current / "cli").exists() and (current / "frontend").exists():
            result = current
            break
        if current == current.root:
            break
        current = current.parent
    return str(result)


def choose_temper(*, args: Args, tmp: p.Path) -> str:
    if args["tag"]:
        # Download release asset in subspace under temp dir.
        temper_tmp = tmp / "temper"
        temper_tmp.mkdir()
        temper = str(download(out_dir=temper_tmp, tag=args["tag"]))
    else:
        temper = args["temper"] or "temper"
    return which(temper)


@click.command("integration-test", help="Runs Temper integration tests.")
@click.option("--check", type=str, multiple=True)
@click.option("--root", type=str)
@click.option("--tag", type=str, default=None)
@click.option("--temper", type=str, default=None)
@click.option("--verbose", is_flag=True, default=False)
def main(
    check: t.Optional[t.List[str]],
    root: t.Optional[str],
    tag: t.Optional[str],
    temper: str,
    verbose: bool = False,
) -> None:
    # Basic system config.
    if platform.system() == "Windows":
        sp.check_call("chcp 65001", shell=True)
    # This reconfigure might matter only on Windows, but meh.
    sys.stderr.reconfigure(line_buffering=True, encoding="utf-8")
    sys.stdout.reconfigure(line_buffering=True, encoding="utf-8")
    # Handle args.
    if tag and temper:
        raise ValueError("Can't specify both tag and temper")
    args: Args = {"root": root, "tag": tag, "temper": temper}
    # Just make a temp dir for anything we might need, even if only sometimes used.
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = p.Path(tmp)
        root = choose_root(root=root)
        temper = choose_temper(args=args, tmp=tmp_path)
        checks = Checks(root=root, temper=temper, verbose=verbose)
        okay = checks.check_all(checks=check or None)

    if not okay:
        sys.exit(1)


def which(path: t.Union[p.Path, str]) -> str:
    original = path
    path = p.Path(path)
    candidate: t.Optional[p.Path] = None
    if len(path.parts) == 1:
        # No explicit path, so expect in system path.
        which_path = sh.which(str(original))
        if which_path is not None:
            candidate = p.Path(which_path)
    else:
        if sys.platform.startswith("win"):
            # Require suffix for windows.
            if path.suffix:
                candidate = path
            else:
                for ext in os.environ["PATHEXT"].split(";"):
                    suffixed = path.with_suffix(ext)
                    if suffixed.exists():
                        candidate = suffixed
                        break
        else:
            # But no suffix needed for others.
            candidate = path
    if candidate is not None and candidate.exists():
        return str(candidate.resolve())
    raise RuntimeError(f"not found: {original}")


main()
