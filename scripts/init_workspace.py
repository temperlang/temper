import click
from util import temper_path, dnc_uncensor, chmod_plusx
from os.path import isdir, isfile, join

precommit_script = dnc_uncensor(
    r"""\
#!/bin/bash
set -e

# Fail if /DØ.?NØT.?CØMMIT/i matches any file.
if (git diff; git diff --cached) | egrep -v "^-" | egrep -qi "DØ.*NØT.*CØMMIT"; then
    echo 'Cannot commit when edited file contains text like "DØ NØT CØMMIT".'
    echo "\$ poetry run -C scripts show-dnc"
    echo "if you have trouble finding them"
    exit 1
fi

./gradlew check
"""
)


@click.command(help="Set up pre-commit hooks for your repository.")
@click.option("-f/-F", "--force/--no-force", type=bool, default=False,
    help="Replace existing workspace files.")
def main(force):
    workspace_root = temper_path()
    expect_path = join(
        workspace_root, "common", "src", "commonMain", "kotlin", "lang", "temper"
    )
    if not isdir(expect_path):
        raise ValueError(f"Didn't find expected Temper directory: {expect_path}")
    print(f"WORKSPACE_ROOT={workspace_root}")
    precommit_hook_path = join(workspace_root, ".git", "hooks", "pre-commit")
    if not isfile(precommit_hook_path) or force:
        print(f"Installing git pre-commit hook at {precommit_hook_path}")
        with open(precommit_hook_path, "w") as fh:
            fh.write(precommit_script)
            chmod_plusx(fh.fileno())
    else:
        print("git pre-commit hook found, doing nothing.")
