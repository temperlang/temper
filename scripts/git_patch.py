import click
from tempfile import NamedTemporaryFile, mkdtemp
from os import environ, linesep, makedirs
from os.path import dirname, exists, isdir, join
import re
import string
from shutil import which
from subprocess import run
from textwrap import dedent
from traceback import print_exc
from util import git_cap, git_pass, sort_uniq

# Use this to die with a short error.
Die = click.ClickException

@click.command(
    help="""
        Interactively select files to patch into a clone of the repository.

        Opens up an editor to allow you to pick files, then clones a new
        repository and patches those files over into the specified branch, so
        that you can create a PR with just some of the changes from the
        original client.
        """,
)
@click.option("-v/", "--verbose/", flag_value=True, default=False)
@click.option("--branch", type=str, help="branch name", default="")
def main(verbose, branch):
    git_branch_name = branch
    git_client_root = git_cap("rev-parse", "--show-toplevel", chomp=True)
    if not git_client_root or not isdir(git_client_root):
        raise Die("Cannot find git client root")
    git_origin = git_cap("config", "--get", "remote.origin.url", chomp=True)
    if not git_origin:
        raise Die("Cannot find origin to clone")
    if not git_branch_name:
        git_branch_name = git_cap("rev-parse", "--abbrev-ref", "HEAD", chomp=True)
        if not git_branch_name:
            raise Die("Could not determine branch name")
    new_client_parent = dirname(git_client_root)
    if not new_client_parent or not isdir(new_client_parent):
        raise Die("Could not determine where to create client")

    # We need to show stuff to the user.
    # https://git-scm.com/book/en/v2/Git-Internals-Environment-Variables
    for prog in (
        environ.get("GIT_EDITOR"),
        environ.get("EDITOR"),
        "emacs",
        "vim",
        "vi",
        "notepad.exe",
    ):
        if not prog:
            continue
        prog_path = which(prog)
        if prog_path:
            editor = prog_path
            break
    else:
        raise Die("Couldn't find an editor")

    if verbose:
        print(
            dedent(
                f"""\
                GIT_CLIENT_ROOT={git_client_root}
                GIT_ORIGIN={git_origin}
                GIT_BRANCH_NAME={git_branch_name}
                NEW_CLIENT_PARENT={new_client_parent}
                EDITOR={editor}
                """
            )
        )
    # Now we know about the current setup, get a list of files and
    # present them to the user.
    list_files = sort_uniq(
        git_cap("diff", "--name-only", cwd=git_client_root),
        git_cap("diff", "--cached", "--name-only", cwd=git_client_root),
    )
    # Generate a temporary text file with the list of files and open it in
    # the user's editor.
    with NamedTemporaryFile(prefix="files-to-patch", mode="w") as edited_file:
        print(
            dedent(
                """\
                Remove any files below that you do not wish to patch
                Then exit the editor to begin patching.
                Only lines starting with `+ ` will be treated as files to patch.
                """
            ),
            file=edited_file,
        )
        for file in list_files:
            print(f'+ {file}', file=edited_file)
        # Close the file object to avoid deletion.
        edited_file.file.close()

        # Pop up the editor.
        if run([editor, edited_file.name]).returncode != 0:
            raise Die("Editor did not exit normally")

        re_line_filter = re.compile(r"^\+ (.*)")
        # Read the temporary file back in to get the modified file list.
        with open(edited_file.name, "r") as fh:
            filtered_file_list = []
            for line in fh:
                if match := re_line_filter.match(line):
                    filtered_file_list.append(match.groups(1))

    if not filtered_file_list:
        raise Die("No files to patch")
    if verbose:
        print(f"Patching {len(filtered_file_list)} files")

    destination_client = mkdtemp(prefix="patch")
    if verbose:
        print("Using client directory $destinationClient")
    # Set up the client
    print(f"Cloning {git_origin} into {destination_client}")
    if git_pass("clone", git_origin, destination_client) != 0:
        raise Die("Failed to git clone")
    if git_pass("checkout", git_branch_name, cwd=destination_client) != 0:
        raise Die(f"Failed to switch to branch {git_branch_name}")
    problems = 0

    for file_to_patch in filtered_file_list:
        if verbose:
            print(f"Patching {file_to_patch}")
        if exists(file_to_patch):
            dest_file = join(destination_client, file_to_patch)
            makedirs(dirname(dest_file), exist_ok=True)
            try:
                copyfile(file_to_patch, dest_file)
            except EnvironmentError:
                problems += 1
                print(f"Failed to patch {file_to_patch} into {dest_file}")
                print_exc()
        else:
            if git_pass("rm", file_to_patch) != 0:
                problems += 1
                print(f"Failed to git rm {file_to_patch}")
    if problems:
        print(f"There were {problems} problems")

    print(
        dedent(
            f"""\
            Patched results are in {destination_client}

            Here's something that you can copy/paste to run pre-commit tests:

            cd {destination_client} &&
                ./scr init-workspace &&
                bash .git/hooks/pre-commit

            Don't forget to `git checkout -b ...` before pushing.
            """
        )
    )
