command_help = """\
Scans github issues to builds a graphviz graph for deliverables.

This looks for patterns like the below in issue bodies and comments.

\b
    Requires #123

\b
    Wants #234

\b
"Requires" specifies a strong dependency,
"Wants" specifies a nice to have.

The graph includes all open nodes with label "deliverable" and
the open nodes that it transitively depends upon.
"""

command_epilog = """\
# `--use-cache` / `--clear-cache`

Running with `--use-cache` will cache and reuse JSON without
re-requesting from Github.
This can be useful if debugging this script and you don't
need any changes to the issue list.

Running with `--clear-cache` will delete the cache file but
also regenerate it so that subsequent calls with `--use-cache`
are faster.
"""

import click  # To describe the command line tool.
import graphviz  # To build the dot graph
import json  # To deal with JSON from the Github API
import marko  # Finding paragraph text in Markdown
import os  # We use env vars to find a cache file
import re  # Mucking with Github API URLs and 'requires #123' patterns in Markdown text
import subprocess  # Invoke Github API via the `gh` command line tool
import sys  # STDERR access and argument access
import time  # Pause to avoid Github rate limiting
import typing  # For type annotations

from datetime import datetime  # Issue created/modified metadata
from marko.ext.gfm import gfm  # Github flavoured markdown parser


def _abbrev(s: str) -> str:
    """Abbreviate a string in repr implementations"""
    max_len = 40
    if len(s) <= max_len:
        return s
    return "%s..." % (s[: max_len - 3],)


def _indent(s: str, prefix: str = " " * 4) -> str:
    lines = s.replace("\r\n", "\n").split("\n")

    def _indent_one(line: str) -> str:
        if line:
            return f"{prefix}{line}"
        else:
            return line

    lines = [_indent_one(line) for line in lines]
    return "\n".join(lines)


# I'm using the github command line tool because it
# uses the current user's Github credentials which avoids distributing
# security credentials.
def _gh_api_request_as_json(request):
    return json.loads(
        subprocess.check_output(
            [
                "gh",
                "api",
                "--paginate",
                "-H",
                "Accept: application/vnd.github+json",
                "-H",
                "X-GitHub-Api-Version: 2022-11-28",
                request,
            ]
        )
    )


# The Issue and IssueComment classes allow for
# syntax like `issue.number` instead of `issue['number']`
# which is how you'd deal with the raw JSON.


class IssueComment:
    """
    A comment on an issue.  The body contains the text.
    This does not arrive in the same github API request, so
    IssueCommentStore attaches these to Issue instances.
    """

    url: str
    issue_url: str
    id: int
    node_id: str
    created_at: datetime
    updated_at: datetime
    author_association: str
    body: str

    def __init__(
        self: "IssueComment",
        id: int,
        node_id: str,
        url: str,
        issue_url: str,
        created_at: datetime,
        updated_at: datetime,
        author_association: str,
        body: str,
    ):
        self.id = id
        self.node_id = node_id
        self.url = url
        self.issue_url = issue_url
        self.created_at = created_at
        self.updated_at = updated_at
        self.author_association = author_association
        self.body = body

    @staticmethod
    def from_json(json) -> "IssueComment":
        return IssueComment(
            id=json["id"],
            node_id=json["node_id"],
            url=json["url"],
            issue_url=json["issue_url"],
            created_at=datetime.fromisoformat(json["created_at"]),
            updated_at=datetime.fromisoformat(json["updated_at"]),
            author_association=json["author_association"],
            body=json["body"] or "",
        )

    def __repr__(self):
        t = self.__class__.__name__
        return f"{t}(id={self.id!r}, node_id={self.node_id!r}, url={self.url!r}, issue_url={self.issue_url!r}, author_association={self.author_association!r}, created_at={self.created_at!r}, updated_at={self.updated_at!r}, body={_abbrev(self.body)!r})"


# Groups (ignoring $&):
# 0 - protocol
# 1 - host
# 2 - path: '<org>/<repo>/issues/<number>'
# 3 - org
# 4 - repo
# 5 - number
_api_url_pattern = re.compile(
    r"^(https://)api[.](github[.]com)/repos(/([^?#/]+)/([^?#/]+)/issues/(\d+))$"
)


def api_to_human_url(api_url):
    """
    https://api.github.com/repos/temperlang/temper/issues/52

    ->

    https://github.com/temperlang/temper/issues/52
    """
    match = _api_url_pattern.match(api_url)
    (protocol, host, path) = match.groups()
    return f"{protocol}{host}{path}"


_short_link_pattern = re.compile(r"^(?:(?:([^/#]+)/)?([^/#]+))?#(\d+)$")


def _parse_shortlink_to_issue_url(
    short_link: str,
    context_url: str,
) -> typing.Optional[str]:
    """
    In the context of 'https://api.github.com/repos/org/repo/issues/123',

    '#234'    -> 'https://api.github.com/repos/org/repo/issues/234'

    'FOO#234' -> 'https://api.github.com/repos/org/FOO/issues/234'

    'BAR/FOO#234' -> 'https://api.github.com/repos/BAR/FOO/issues/234'

    """

    (_, _, _, context_org, context_repo, _) = _api_url_pattern.match(
        context_url
    ).groups()
    m = _short_link_pattern.match(short_link)
    if m is None:
        return None
    (org, repo, number) = m.groups()
    if org is None:
        org = context_org
    if repo is None:
        repo = context_repo

    return f"https://api.github.com/repos/{org}/{repo}/issues/{number}"


class Issue:
    """
    A github issue including the body and comments.

    url is the Github API URL, but it is easy to convert that to an actual issue
    comment via api_to_human_url.
    """

    id: int
    number: int
    url: str
    title: str
    state: str
    labels: set[str]
    body: str
    comments: list[IssueComment]

    def __init__(
        self,
        id: int,
        number: int,
        url: str,
        title: str,
        state: str,
        labels: set[str],
        body: str,
    ):
        self.id = id
        self.number = number
        self.url = url
        self.title = title
        self.state = state
        self.labels = labels
        self.body = body
        self.comments = []

    def __repr__(self):
        t = self.__class__.__name__
        return f"{t}(id={self.id!r}, number={self.number!r}, url={self.url!r}, title={self.title!r}, state={self.state!r}, labels={self.labels!r}, body={_abbrev(self.body)!r}, comments={self.comments!r})"

    @staticmethod
    def from_json(json) -> "Issue":
        labels = set()
        for label_json in json["labels"]:
            labels.add(label_json["name"])
        return Issue(
            id=json["id"],
            number=json["number"],
            url=json["url"],
            title=json["title"],
            state=json["state"],
            labels=labels,
            body=json["body"] or "",
        )


# See note about --use-cache and --clear-cache in file docstring
cache_dir = (
    os.environ.get("TMPDIR") or os.environ.get("TMP") or os.environ.get("TEMP", "/tmp")
)
# The file used to cache JSON results so we don't repeatedly fetch
# them from Github which can take time.
cache_file = os.path.join(cache_dir, "IssueAndCommentStoreCache.json")


class IssueAndCommentStore:
    """
    Fetches issues and comments from Github and
    attaches comments to issues.
    """

    def __init__(self, use_cached_json, verbose):
        issues_json = []
        comments_json = []

        need_to_fetch_json = True
        if use_cached_json:
            try:
                with open(cache_file, "r", encoding="utf-8") as f:
                    issues_json, comments_json = json.load(f)
                need_to_fetch_json = False  # I'm good, thanks
            except:
                # Will fetch and store below
                print(f"No stored JSON content in {cache_file}", file=sys.stderr)

        if need_to_fetch_json:
            # docs.github.com/en/rest/issues/issues?apiVersion=2022-11-28#list-repository-issues
            # explains how to get issues.
            fetch_timeout = 0

            def fetch(path, dest):
                nonlocal fetch_timeout
                if fetch_timeout:
                    # Don't run into GH rate limiting
                    time.sleep(fetch_timeout)
                else:
                    fetch_timeout = 0.5  # seconds
                json = _gh_api_request_as_json(path)
                if verbose:
                    print(f"Received {len(json)} items from `{path}`", file=sys.stderr)
                dest.extend(json)

            repo = "/repos/temperlang/temper"
            fetch(f"{repo}/issues", issues_json)
            fetch(f"{repo}/issues/comments", comments_json)

            if use_cached_json:
                # Store for the next --use-cache request
                try:
                    with open(cache_file, "w", encoding="utf-8") as f:
                        json.dump([issues_json, comments_json], f)
                except:
                    print(
                        f"Could not store JSON content in {cache_file}", file=sys.stderr
                    )

        issues_by_url = dict()
        for issue_json in issues_json:
            issue = Issue.from_json(issue_json)
            assert issue.url not in issues_by_url
            issues_by_url[issue.url] = issue

        found, missing = 0, 0
        for comment_json in comments_json:
            comment = IssueComment.from_json(comment_json)
            issue = issues_by_url.get(comment.issue_url)
            if issue is not None:
                found += 1
                issue.comments.append(comment)
            else:
                missing += 1

        if verbose:
            print(f"Found {found}, missing {missing}", file=sys.stderr)

        self.issues_by_url = issues_by_url

    def issues(self):
        for url in self.issues_by_url:
            yield self.issues_by_url[url]


class NodeInfo:
    """
    Collects info about Graphviz graph nodes before
    the graphviz module is used to build a file.
    """

    issue: Issue
    dot_id: str
    issue_url: str
    description: str
    followers: dict["NodeInfo", float]
    preceders: dict["NodeInfo", float]

    def __init__(self, issue: Issue):
        issue_url = issue.url
        m = _api_url_pattern.match(issue_url)
        self.issue = issue
        self.dot_id = m.groups()[2]
        self.issue_url = issue_url
        self.description = issue.title.strip().replace("\n", " ")
        self.followers = dict()
        self.preceders = dict()


# Matches issue body/comment conventions like
#
#    requires #123
#    requires temperlang/temper#123
#
#    wants #234
#
# The first two express that the current issue depends on issue #123 with the second
# making the repo explicit.
#
# The last expresses that the current issue would benefit from issue #123 being
# resolved but it is not necessary; a weaker form of dependency.
_dep_instruction_pattern = re.compile(
    r"(?:^|\s)(?i:(requires?)|(wants?))(?:[ \t]+|(?=#))(\S+)"
)


def build_dep_graph(
    store: IssueAndCommentStore,
) -> typing.Tuple[graphviz.Digraph, bool]:
    """
    Collects info by scanning issues.
    """

    had_problem = False

    issue_url_to_node_info: dict[str, NodeInfo] = dict()
    included: set[NodeInfo] = set()  # List of nodes that must be in the graph

    for issue in store.issues():
        node_info = NodeInfo(issue)
        issue_url_to_node_info[issue.url] = node_info
        if "deliverable" in issue.labels and issue.state == "open":
            included.add(node_info)

    def edge(source: NodeInfo, target: NodeInfo, weight: float):
        old = source.followers.get(target)
        if old is None or old < weight:
            source.followers[target] = weight
            target.preceders[source] = weight

    for issue_url in issue_url_to_node_info:
        node_info = issue_url_to_node_info[issue_url]

        issue = node_info.issue
        markdown_chunks = [issue.body] + [c.body for c in issue.comments]

        # Walk markdown and find dependency info.
        def scan_for_deps(md: marko.element.Element, in_para=False):
            if isinstance(md, marko.block.Paragraph):
                in_para = True
            if isinstance(md, marko.inline.RawText):
                if in_para:
                    for match in _dep_instruction_pattern.finditer(md.children):
                        is_require, is_want, source = match.groups()
                        if "#" in source:  # Is shortlink syntax
                            weight = 1.0
                            if is_want:
                                weight = 0.25
                            source_url = _parse_shortlink_to_issue_url(
                                source, issue_url
                            )
                            if source_url is None:
                                err_url = api_to_human_url(issue_url)
                                err_context = md.children
                                print(
                                    f"Malformed short link `{short_link}` from {err_url}!"
                                    + f"\n\n----\n{_indent(err_context, 2)}\n----",
                                    file=sys.stderr,
                                )
                                had_problem = True
                            else:
                                source_node_info = issue_url_to_node_info[source_url]
                                edge(source_node_info, node_info, weight)
            else:
                for child in md.children:
                    if isinstance(child, marko.element.Element):
                        scan_for_deps(child, in_para=in_para)

        for markdown_chunk in markdown_chunks:
            md_doc = gfm.parse(markdown_chunk)
            scan_for_deps(md_doc)

    # Now that we've got the edges, expand out the included set.
    unexploded = [node_info for node_info in included]
    while unexploded:
        node_info = unexploded.pop()
        for preceder in node_info.preceders:
            if preceder not in included:
                included.add(preceder)
                if preceder.issue.state == "open":
                    unexploded.append(preceder)

    dot = graphviz.Digraph(comment="Deliverable planning")
    for node_info in included:
        dot.node(node_info.dot_id, label=graphviz.escape(node_info.description))
    for node_info in included:
        for follower in node_info.followers:
            weight = node_info.followers[follower]
            if weight < 0.5:
                dot.attr("edge", style="dotted")
            else:
                dot.attr("edge", style="solid")
            dot.edge(node_info.dot_id, follower.dot_id)
    return (dot, had_problem)


# Request comments and attach them to the issues
@click.command(help=command_help, epilog=command_epilog)
@click.option("--out", help="path to write the graph to")
@click.option(
    "--use-cache/",
    type=bool,
    default=False,
    help="use cached JSON to avoid fetching from Github again, saving if absent",
)
@click.option(
    "--clear-cache/",
    type=bool,
    default=False,
    help="clears the cache used by --use-cache",
)
@click.option("--view/", type=bool, default=False, help="pop up graph in a window")
@click.option(
    "-v/", "--verbose/", type=bool, flag_value=True, default=False, help="more logging to STDERR"
)
def main(out, use_cache, clear_cache, view, verbose):
    use_cached_json = use_cache or clear_cache
    if args.clear_cache:
        try:
            os.unlink(cache_file)
        except FileNotFoundError:
            pass
    store = IssueAndCommentStore(use_cached_json=use_cached_json, verbose=verbose)
    dot, had_problem = build_dep_graph(store)
    dot.render(out, view=view)
    if had_problem:
        sys.exit(-1)
