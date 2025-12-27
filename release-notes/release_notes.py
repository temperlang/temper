# Typically run via `poetry run -- python3`

"""Usage: %(cmd)s show [--only-unreleased]
     | %(cmd)s next [--ignore-empty-release] <release-version>
     | %(cmd)s undo                          <release-version>
     | %(cmd)s cat

`%(cmd)s show` lists release note snippet files (`%(snippets_dir)s/*.md`)
by their summary: <PATH> <TAB> <RELEASED_STATUS> <TAB> <SUMMARY>

With `--only-unreleased`, filters out released note files.

RELEASED_STATUS has the following meaning:

- asterisk (`*`) indicates the file is unreleased
- tilde (`~`) indicates the file has been released but the
  git object hash under which it was released does not match
  the current hash.
- a semver version indicates the version at which the snippet
  was released.

------------------------------------------------------------------

`%(cmd)s next` takes a release version and writes a file
at `%(releases_dir)s/<release-version>.md` containing all release note
snippet files (`%(snippets_dir)s/*.md`) not previously released.

------------------------------------------------------------------

`%(cmd)s cat` produces a release history for all versions.

It sorts and concatenates all edited release summary files
(`%(releases_dir)s/*.md`) in semver order.

The concatenated markdown goes to stdout.

------------------------------------------------------------------

`%(cmd)s undo` reverses the effect of `%(cmd)s next`.

It takes a release version and removes all release history
metadata for that version and deletes the corresponding
`%(releases_dir)s/<release-version>.md` file.

If the file was previously added to git, the user is
responsible for `git rm`.

------------------------------------------------------------------

CAVEAT: This files reads and writes `%(release_history_file)s` so
concurrent use may cause problems.

"""

import marko
import semver

import html
import json
import os
import os.path
import re
import subprocess
import sys

from marko.ext.gfm import gfm  # Github flavoured Markdown

RELEASE_HISTORY_FILENAME = ".release-history.json"
RELEASES_DIRNAME = "releases"
SNIPPETS_DIRNAME = "snippets"

root_dir = os.path.abspath(os.path.dirname(__file__))
snippets_dir = os.path.join(root_dir, SNIPPETS_DIRNAME)
releases_dir = os.path.join(root_dir, RELEASES_DIRNAME)
release_history_path = os.path.join(root_dir, RELEASE_HISTORY_FILENAME)


def get_git_hash(path):
    """
    Reads the hash for a file which helps us track whether the
    version of a file in history is the same as at the time it
    was incorporated into release notes.
    """
    return (
        subprocess.check_output(["git", "hash-object", "--", path])
        .decode("utf-8")
        .strip()
    )


class ReleaseHistoryRecord:
    """
    A record about a release in the release history file.
    """

    def __init__(self, rel_path, release_version, hash):
        self.rel_path = rel_path
        self.release_version = release_version
        self.hash = hash

    def __eq__(self, other):
        return (
            isinstance(other, ReleaseHistoryRecord)
            and self.rel_path == other.rel_path
            and self.release_version == other.release_version
            and self.hash == other.hash
        )

    def to_json(self):
        return {
            "release_version": self.release_version,
            "hash": self.hash,
        }


release_history = {}


def read_release_history():
    global release_history
    with open(release_history_path, "r") as f:
        json_value = json.loads(f.read())
        for rel_path, record in json_value.items():
            path = os.path.join(root_dir, rel_path)
            release_history[path] = ReleaseHistoryRecord(
                rel_path=rel_path,
                release_version=record.get("release_version"),
                hash=record.get("hash"),
            )


def update_release_history():
    global release_history
    json_value = {}
    for _, record in release_history.items():
        json_value[record.rel_path] = record.to_json()
    updated_json_content = json.dumps(json_value, indent=2)
    # Binary with explicit encoding avoids carriage returns on windows.
    with open(release_history_path, "wb") as f:
        f.write(updated_json_content.encode("utf-8"))
    print(f"Updated {release_history_path}.", file=sys.stderr)


read_release_history()


def short_summary_from_gfm(content):
    """
    Given Github flavoured Markdown content, returns the short
    description.  The short description is the first header.
    """
    doc = gfm.parse(content)
    if not doc.children:
        return None
    first_block_element = doc.children[0]
    if isinstance(first_block_element, marko.block.Heading):
        summary_only_doc = marko.block.Document()
        summary_only_doc.children[:] = first_block_element.children
        return gfm.render(summary_only_doc)
    return None


class SnippetContent:
    """
    The Github flavoured markdown comment of a release note snippet file.
    """

    def __init__(self, snippet):
        self.snippet = snippet
        with open(self.snippet.path, "r", encoding="utf-8") as content_file:
            content = content_file.read()
        # Hack initial heading to 3rd level indent.
        content = re.sub(r"^#+", "###", content)
        self.content = content
        self.summary = short_summary_from_gfm(self.content)

    def __repr__(self):
        return repr({"path": self.snippet.path, "summary": self.summary})


class Snippet:
    """
    Bundles information about a release note summary files.
    """

    def __init__(self, path):
        self.path = path
        self.hash = get_git_hash(path)
        self._content = None

    def read_content(self) -> SnippetContent:
        """
        The SnippetContent for this snippet.
        """
        if self._content is None:
            self._content = SnippetContent(self)
        return self._content

    def released_at_and_fresh(self):
        """
        None if unreleased, otherwise, a pair of:

        - the semver version at which this release note snippet
          was released, and
        - a bool indicating whether the hash at which it was
          released matches the hash in the record file.
        """
        record = release_history.get(self.path)
        if record is None:
            return (None, True)
        return (record.release_version, self.hash == record.hash)

    def released_at(self):
        return self.released_at_and_fresh()[0]

    def __repr__(self):
        return repr(
            {
                "path": self.path,
                "hash": self.hash,
            }
        )


def mark_snippet_released(snippet, version):
    """
    Adds persistent metadata to a release note snippet
    via `git notes` so that snippet.released_at() will no
    longer return None.

    No-op if snippet.released_at() is already not None.
    """

    old_record = release_history.get(snippet.path)
    new_record = ReleaseHistoryRecord(
        rel_path=os.path.relpath(snippet.path, start=root_dir),
        release_version=version,
        hash=snippet.hash,
    )
    if old_record != new_record:
        release_history[snippet.path] = new_record
        print(f"Added release history for {snippet.path}.", file=sys.stderr)


snippets = sorted(
    [
        Snippet(os.path.join(snippets_dir, fname))
        for fname in os.listdir(snippets_dir)
        if fname.endswith(".md")
    ],
    # Sort by semver version
    key=lambda snippet: snippet.released_at() or "",
)


def do_help():
    print(
        __doc__.strip()
        % {
            "cmd": os.path.splitext(os.path.basename(__file__))[0],
            "release_history_file": RELEASE_HISTORY_FILENAME,
            "releases_dir": RELEASES_DIRNAME,
            "snippets_dir": SNIPPETS_DIRNAME,
        },
        file=sys.stderr,
    )
    return True


def do_show(only_unreleased):
    """Implements the `show` command-line verb described above."""
    for snippet in snippets:
        released_at, hashes_match = snippet.released_at_and_fresh()
        if only_unreleased and released_at is not None:
            continue
        rel_path = os.path.relpath(snippet.path, start=root_dir)
        if released_at is None:
            release_marker = "*"
        else:
            release_marker = f"{released_at}"
            if not hashes_match:
                release_marker = f"{release_marker}~"
        summary = snippet.read_content().summary or "<no summary>"
        print(f"{rel_path}\t{release_marker}\t{summary}")
    return True


def do_next(release_version, ignore_empty_release):
    """Implements the `next` command-line verb described above."""
    out_file = os.path.join(releases_dir, f"{release_version}.md")
    if os.path.exists(out_file):
        print(f"Cannot write release. File {out_file} already exists!", file=sys.stderr)
        return False
    if release_version is None:
        print("Please provide a semver release version!", file=sys.stderr)
        return False

    semver.Version.parse(release_version)  # Raised ValueError if malformed.

    snippets_to_release = [s for s in snippets if s.released_at() is None]
    if not snippets_to_release and not ignore_empty_release:
        print(
            "No snippets to release.  Use `--ignore-empty-release` to continue!",
            file=sys.stderr,
        )
        return False

    htmlid_snippet_pairs = []

    print(f"Writing {len(snippets_to_release)} to {out_file}.", file=sys.stderr)

    ok = True
    with open(out_file, "w", encoding="utf-8") as out:
        out.write(f"## Version {release_version}\n\n")
        for snippet in snippets_to_release:
            htmlid = f"{release_version}--note-{len(htmlid_snippet_pairs)}"
            htmlid_snippet_pairs.append((htmlid, snippet))
            summary = snippet.read_content().summary
            if summary is None:
                print(f"Missing summary for {snippet.path}!", file=sys.stderr)
                summary = os.path.relpath(snippet.path, start=snippets_dir)
                ok = False
            summary_indented = "\n  ".join(summary.split("\n"))
            bullet_point = f"- [{summary_indented}](#{htmlid})\n"
            out.write(bullet_point)
        for htmlid, snippet in htmlid_snippet_pairs:
            htmlid_esc = html.escape(htmlid)
            out.write(f'\n\n<a id="{htmlid_esc}" name="{htmlid_esc}"></a>\n\n')
            out.write(snippet.read_content().content)

    for snippet in snippets_to_release:
        mark_snippet_released(snippet, release_version)

    update_release_history()

    print("")
    print("The generated file is just a draft.  You should edit it.")
    print(f"    $EDITOR {out_file}")

    return ok


def do_cat():
    """Implements the `cat` command-line verb described above."""

    # (semver, path)
    version_release_file_pairs = sorted(
        [
            (
                semver.Version.parse(base_name[:-3]),  # Stripping off .md
                os.path.join(releases_dir, base_name),
            )
            for base_name in os.listdir(releases_dir)
            if base_name.endswith(".md")
        ],
    )

    print("# Releases\n")
    for version, path in version_release_file_pairs:
        content = open(path, "r").read()
        sys.stdout.write(content)

    return True


def do_undo(version):
    """Implements the `undo` command-line verb described above."""
    target_path = os.path.join(releases_dir, f"{version}.md")

    if not os.path.isfile(target_path):
        print(
            os.path.exists(target_path)
            and f"Cannot delete `{target_path}`. Not a regular file!"
            or f"Cannot undo. `{target_path}` does not exist!",
            file=sys.stderr,
        )
        return False

    entries_to_remove = [
        key
        for (key, record) in release_history.items()
        if record.release_version == version
    ]

    os.unlink(target_path)
    print(f"Deleted {target_path}.", file=sys.stderr)

    for key in entries_to_remove:
        del release_history[key]
    print(f"Removed {len(entries_to_remove)} from release history.", file=sys.stderr)

    update_release_history()
    return True


if __name__ == "__main__":

    verb = None
    only_unreleased = False
    ignore_empty_release = False
    release_version = None

    for arg in sys.argv[1:]:
        flag = re.sub(r"([^-_])_", r"\1-", arg)  # `--foo_bar` -> `--foo-bar`
        if verb is None:
            verb = arg
        elif flag == "--only-unreleased":
            only_unreleased = True
        elif flag == "--ignore-empty-release":
            ignore_empty_release = True
        elif release_version is None and not arg.startswith("-"):
            release_version = arg
        else:
            print(f"Unused arg `{arg}`.", file=sys.stderr)

    actions = {
        "help": do_help,
        "--help": do_help,
        "show": lambda: do_show(
            only_unreleased=only_unreleased,
        ),
        "next": lambda: do_next(
            release_version, ignore_empty_release=ignore_empty_release
        ),
        "cat": do_cat,
        "undo": lambda: do_undo(
            release_version,
        ),
    }

    action = actions.get(verb)

    ret_code = 0
    if action is None:
        action = do_help
        ret_code = 1

    if not action() and not ret_code:
        ret_code = 1

    if ret_code:
        sys.exit(ret_code)
