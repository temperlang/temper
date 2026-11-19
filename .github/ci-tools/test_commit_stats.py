# /// script
# requires-python = ">=3.11"
# dependencies = ["pyyaml==6.0.2"]
# ///
"""Tests commit_stats.py, and guards stats.yml's permissions.

commit_stats.py's request from a temporary git checkout (changed,
untracked, deleted, and renamed files, and nothing changed), and its
exit codes with a fake `gh` on PATH: 3 when the branch moved, 1 for
other errors, 0 on success. Then stats.yml, read as YAML: a read-only
token but for the `commit` job, which runs only on `main` in
temperlang/temper; no event payload or inputs expanded into a shell
script; and no step or job hands every command the token as GH_TOKEN.
Fast, with nothing sent; CI's `lint` job runs it.

    uv run .github/ci-tools/test_commit_stats.py
"""

import base64
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import commit_stats  # noqa: E402

SCRIPT = HERE / "commit_stats.py"
STATS_YML = HERE.parent / "workflows/stats.yml"
# The tests' git ignores the user's and the system's configuration.
os.environ |= {"GIT_CONFIG_GLOBAL": os.devnull, "GIT_CONFIG_NOSYSTEM": "1"}


def git(directory: Path, *args: str) -> str:
    return subprocess.run(["git", "-C", str(directory), "-c", "user.name=t", "-c", "user.email=t@example.com",
                           *args], capture_output=True, text=True, check=True).stdout.strip()


def repo(directory: Path, files: dict[str, str]) -> Path:
    """A checkout with `files` committed."""
    directory.mkdir()
    git(directory, "init", "-q")
    for name, text in files.items():
        (directory / name).write_text(text)
    git(directory, "add", "-A")
    git(directory, "commit", "-q", "-m", "start")
    return directory


def files(payload: dict) -> tuple[dict[str, str], list[str]]:
    changes = payload["variables"]["input"]["fileChanges"]
    added = {a["path"]: base64.b64decode(a["contents"]).decode() for a in changes["additions"]}
    return added, [d["path"] for d in changes["deletions"]]


def test_request(tmp: Path) -> None:
    d = repo(tmp / "changes", {"a.csv": "1\n", "gone.txt": "x\n", "old.svg": "<svg/>\n"})
    assert commit_stats.request(d, "o/r", "stats", "0" * 40, "h", "b") is None
    (d / "a.csv").write_text("1\n2\n")
    (d / "new chart.svg").write_text("<svg>new</svg>\n")
    (d / "gone.txt").unlink()
    git(d, "mv", "old.svg", "renamed.svg")  # Staged, as a rename.
    payload = commit_stats.request(d, "o/r", "stats", "f" * 40, "Headline", "Body")
    assert payload is not None
    added, deleted = files(payload)
    assert added == {"a.csv": "1\n2\n", "new chart.svg": "<svg>new</svg>\n", "renamed.svg": "<svg/>\n"}, added
    assert deleted == ["gone.txt", "old.svg"], deleted
    given = payload["variables"]["input"]
    assert given["branch"] == {"repositoryNameWithOwner": "o/r", "branchName": "stats"}, given
    assert given["message"] == {"headline": "Headline", "body": "Body"}, given
    # A dry run expects the checkout's HEAD.
    out = tmp / "request.json"
    run = subprocess.run([sys.executable, str(SCRIPT), str(d), "--branch", "stats", "--headline", "h",
                          "--repo", "o/r", "--dry-run", str(out)], capture_output=True, text=True)
    assert run.returncode == 0, run.stderr
    sent = json.loads(out.read_text())
    assert sent["variables"]["input"]["expectedHeadOid"] == git(d, "rev-parse", "HEAD"), sent
    assert files(sent) == (added, deleted)


# A fake `gh`: saves what it's sent, then answers as FAKE_GH says.
FAKE_GH = """
import json, os, sys
open(os.environ["FAKE_GH_SENT"], "w").write(sys.stdin.read())
mode = os.environ["FAKE_GH"]
if mode == "moved":
    print(json.dumps({"errors": [{"message": 'Expected branch to point to "abc" but it did not.'}]}))
    sys.exit(1)
if mode == "other":
    print("HTTP 401: Bad credentials", file=sys.stderr)
    sys.exit(1)
if mode == "errors":  # Exit 0, but errors in the answer.
    print(json.dumps({"data": None, "errors": [{"message": "something else"}]}))
    sys.exit(0)
print(json.dumps({"data": {"createCommitOnBranch": {"commit": {"oid": "1234", "url": "https://x/1234"}}}}))
"""


def test_exit_codes(tmp: Path) -> None:
    d = repo(tmp / "send", {"a.csv": "1\n"})
    (d / "a.csv").write_text("1\n2\n")
    bin_dir = tmp / "bin"
    bin_dir.mkdir()
    gh = bin_dir / "gh"
    gh.write_text(f"#!{sys.executable}\n{FAKE_GH}")
    gh.chmod(0o755)
    sent = tmp / "sent.json"
    for mode, code in (("moved", 3), ("other", 1), ("errors", 1), ("ok", 0)):
        env = os.environ | {"PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}", "FAKE_GH": mode,
                            "FAKE_GH_SENT": str(sent)}
        run = subprocess.run([sys.executable, str(SCRIPT), str(d), "--branch", "stats", "--headline", "h",
                              "--repo", "o/r"], env=env, capture_output=True, text=True)
        assert run.returncode == code, (mode, run.returncode, run.stdout, run.stderr)
        added, _ = files(json.loads(sent.read_text()))
        assert added == {"a.csv": "1\n2\n"}, (mode, added)
        sent.unlink()


def test_stats_yml() -> None:
    workflow = yaml.safe_load(STATS_YML.read_text())
    assert workflow["permissions"] == {"contents": "read"}, workflow["permissions"]
    jobs = workflow["jobs"]
    for name, job in jobs.items():
        if name == "commit":
            assert job.get("permissions") == {"contents": "write"}, job.get("permissions")
        else:
            assert "permissions" not in job, (name, job["permissions"])
        assert "GH_TOKEN" not in job.get("env", {}), name
        for step in job.get("steps", []):
            run = step.get("run", "")
            assert "${{ github.event." not in run and "inputs." not in run, (name, run)
            assert "GH_TOKEN" not in step.get("env", {}), (name, step.get("name"))
    guard = jobs["commit"]["if"]
    assert "github.repository == 'temperlang/temper'" in guard, guard
    assert "github.ref == 'refs/heads/main'" in guard, guard


def main() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        test_request(Path(tmp))
        test_exit_codes(Path(tmp))
    test_stats_yml()
    print("test_commit_stats.py: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
