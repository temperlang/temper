# /// script
# requires-python = ">=3.11"
# ///
"""Commits a checkout's changed files to a branch through GitHub's API, signed by GitHub.

For the nightly stats workflow (.github/workflows/stats.yml): DIR is a
checkout of the `stats` branch with new rows appended and the charts
redrawn. Its changes (`git status`: added and modified files, and
deleted ones) go to BRANCH as one commit through GraphQL's
`createCommitOnBranch`, sent by `gh api graphql` with the workflow's
token (GH_TOKEN). GitHub makes the commit itself, so it's signed by
GitHub and shows as Verified, authored by the token's owner
(github-actions[bot]); no key or `git push` is needed. The commit's
parent must be `expectedHeadOid`, DIR's HEAD (or `--head OID`): if the
branch moved since DIR was checked out, nothing is committed and this
exits with 3, so the workflow can check out the new head, add its rows
again, and retry. With nothing changed, it commits nothing.

`--dry-run FILE` writes the request to FILE (`-` for stdout) instead of
sending it, so the payload can be checked locally; an empty FILE is a
usage error. Outside a git
checkout (a local dry run on a plain directory), every file in DIR is
an addition.

    uv run .github/ci-tools/commit_stats.py DIR --branch BRANCH --headline TEXT
        [--body TEXT] [--repo OWNER/NAME] [--head OID] [--dry-run FILE]

`--repo` defaults to GITHUB_REPOSITORY.
"""

import argparse
import base64
import json
import os
import subprocess
import sys
from pathlib import Path

MUTATION = """mutation ($input: CreateCommitOnBranchInput!) {
  createCommitOnBranch(input: $input) { commit { oid url } }
}"""
# What GitHub says when expectedHeadOid isn't the branch's head.
MOVED = "Expected branch to point to"


def git(directory: Path, *args: str) -> str:
    return subprocess.run(["git", "-C", str(directory), *args], capture_output=True, text=True, check=True).stdout


def checkout(directory: Path) -> bool:
    """Whether the directory is the top of a git checkout of its own."""
    try:
        return Path(git(directory, "rev-parse", "--show-toplevel").strip()).resolve() == directory.resolve()
    except subprocess.CalledProcessError:
        return False


def changes(directory: Path) -> tuple[list[str], list[str]]:
    """The files to add or update, and those to delete, as paths in DIR."""
    if not checkout(directory):
        files = sorted(p.relative_to(directory).as_posix() for p in directory.rglob("*") if p.is_file())
        return files, []
    added, deleted = [], []
    # Without renames, which would put the old path in an entry of its own:
    # a rename is the new path added and the old one deleted.
    status = git(directory, "status", "--porcelain=v1", "-z", "--untracked-files=all", "--no-renames")
    for entry in filter(None, status.split("\0")):
        code, path = entry[:2], entry[3:]
        (deleted if "D" in code else added).append(path)
    return sorted(added), sorted(deleted)


def request(directory: Path, repo: str, branch: str, head: str, headline: str, body: str) -> dict | None:
    added, deleted = changes(directory)
    if not added and not deleted:
        return None
    return {
        "query": MUTATION,
        "variables": {"input": {
            "branch": {"repositoryNameWithOwner": repo, "branchName": branch},
            "message": {"headline": headline, "body": body},
            "expectedHeadOid": head,
            "fileChanges": {
                "additions": [{"path": p, "contents": base64.b64encode((directory / p).read_bytes()).decode()}
                              for p in added],
                "deletions": [{"path": p} for p in deleted],
            },
        }},
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("dir", type=Path, help="a checkout of the branch, with the changes to commit")
    parser.add_argument("--branch", required=True)
    parser.add_argument("--headline", required=True, help="the commit message's first line")
    parser.add_argument("--body", default="", help="the rest of the commit message")
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"), help="OWNER/NAME")
    parser.add_argument("--head", help="the commit the branch must be at (default DIR's HEAD)")
    parser.add_argument("--dry-run", metavar="FILE", help="write the request here (- for stdout), not sending it")
    args = parser.parse_args()
    if not args.repo:
        parser.error("no --repo, and GITHUB_REPOSITORY isn't set")
    # An empty FILE is a mistake, not a request to send it for real.
    if args.dry_run == "":
        parser.error("--dry-run needs a FILE, or - for stdout")
    head = args.head or (git(args.dir, "rev-parse", "HEAD").strip() if checkout(args.dir) else "0" * 40)
    payload = request(args.dir, args.repo, args.branch, head, args.headline, args.body)
    if payload is None:
        print(f"commit_stats.py: nothing changed in {args.dir}; nothing committed")
        return 0
    files = payload["variables"]["input"]["fileChanges"]
    summary = f"{len(files['additions'])} files added or updated, {len(files['deletions'])} deleted"
    if args.dry_run is not None:
        text = json.dumps(payload, indent=2) + "\n"
        if args.dry_run == "-":
            sys.stdout.write(text)
        else:
            Path(args.dry_run).write_text(text)
        print(f"commit_stats.py: dry run, {summary}, on {args.branch} at {head}", file=sys.stderr)
        return 0
    sent = subprocess.run(["gh", "api", "graphql", "--input", "-"], input=json.dumps(payload),
                          capture_output=True, text=True)
    output = sent.stdout + sent.stderr
    try:
        result = json.loads(sent.stdout)
    except json.JSONDecodeError:
        result = {}
    if sent.returncode != 0 or result.get("errors") or not result.get("data"):
        print(output, file=sys.stderr)
        if MOVED in output:
            print(f"commit_stats.py: {args.branch} moved since {head}; nothing committed", file=sys.stderr)
            return 3
        return 1
    commit = result["data"]["createCommitOnBranch"]["commit"]
    print(f"commit_stats.py: committed {commit['oid']} to {args.branch} ({summary}): {commit['url']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
