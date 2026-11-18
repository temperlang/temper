"""The site's header, and what its footer and the playground's About
say, shared by the docs (site/docs/build.py) and the playground
(site/playground/build.py), so the two can't drift.

The header is the logo (the Temper link, to the site's home), the
sections, the blog, and the playground, the About button (where a page has no
footer), and the theme toggle. header.css styles it, imported by both
sites' stylesheets; header.js, loaded in each page's head, applies the
theme before the page shows and wires up the toggle and the phone menu.
"""

import html
import posixpath
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
LOGO = ROOT / "docs/images/temper-logo-text-auto.svg"
CSS = HERE / "header.css"
JS = HERE / "header.js"
REPO_URL = "https://github.com/temperlang/temper"
# The header's sections: docs/ folders and their names.
SECTIONS = [
    ("guide", "Guide"),
    ("language", "Language"),
    ("std", "Std"),
    ("tools", "Tools"),
    ("backends", "Backends"),
    ("contributing", "Contributing"),
    ("internals", "Internals"),
    ("release-notes", "Release notes"),
]
PLAYGROUND = "playground"
# The blog, at blog/ (a submodule; site/docs/build.py renders it).
BLOG = "blog"

# The toggle's three icons: system (half filled), light, and dark.
ICONS = (
    '<svg class="icon icon-system" viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="6.25"/>'
    '<path d="M8 1.75a6.25 6.25 0 0 1 0 12.5z" class="fill"/></svg>'
    '<svg class="icon icon-light" viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="3"/>'
    '<path d="M8 1v1.75M8 13.25V15M1 8h1.75M13.25 8H15M3.05 3.05l1.24 1.24M11.71 11.71l1.24 1.24'
    'M3.05 12.95l1.24-1.24M11.71 4.29l1.24-1.24"/></svg>'
    '<svg class="icon icon-dark" viewBox="0 0 16 16" aria-hidden="true">'
    '<path d="M13.5 9.6A5.75 5.75 0 0 1 6.4 2.5a5.75 5.75 0 1 0 7.1 7.1z"/></svg>'
)


def relative(target: str, page: str) -> str:
    """A link from `page` to `target`, both relative to the site's root;
    index pages as their folder."""
    rel = posixpath.relpath(target, posixpath.dirname(page) or ".")
    if rel == "index.html":
        return "./"
    if rel.endswith("/index.html"):
        return rel[: -len("index.html")]
    return rel


def logo(label: str = "Temper", width: str | None = None) -> str:
    """The Temper logo, inline, so header.css colors it for the chosen
    theme: without Inkscape's metadata and ids, its classes renamed
    `logo-N`."""
    svg = LOGO.read_text()
    body = svg[svg.index("<g", svg.index("</defs>")) : svg.rindex("</svg>")]
    body = re.sub(r'\s+id="[^"]*"', "", body)
    body = re.sub(r'class="cls-(\d+)"', r'class="logo-\1"', body)
    body = re.sub(r"\s+", " ", body).replace(" />", "/>").replace("> <", "><").strip()
    view = re.search(r'viewBox="([^"]+)"', svg)
    if not view:
        raise SystemExit(f"header.py: no viewBox in {LOGO}")
    size = f' width="{html.escape(width)}"' if width else ""
    return f'<svg class="logo" viewBox="{view.group(1)}"{size} role="img" aria-label="{html.escape(label)}">{body}</svg>'


def header(page: str, current: str | None, about: bool = False) -> str:
    """The header for `page` (its path from the site's root), with
    `current` (a section, or PLAYGROUND) marked; with an About button
    for the popover that about() makes, on a page without a footer."""
    links = []
    for s, name in SECTIONS + [(BLOG, "Blog"), (PLAYGROUND, "Playground")]:
        target = f"{s}/index.html" if s in (BLOG, PLAYGROUND) else f"docs/{s}/index.html"
        mark = ""
        if s == current:
            mark = ' aria-current="page"' if s == PLAYGROUND else ' aria-current="true"'
        cls = ' class="play"' if s == PLAYGROUND else ""
        links.append(f'<a href="{relative(target, page)}"{cls}{mark}>{name}</a>')
    about_button = (
        '<button class="top-button" type="button" popovertarget="about" aria-haspopup="dialog">About</button>'
        if about
        else ""
    )
    return (
        f'<header class="top{" compact" if about else ""}">'
        f'<a class="brand" href="{relative("index.html", page)}">{logo()}</a>'
        '<button class="top-button menu-button" type="button" aria-expanded="false" aria-controls="sections">Menu</button>'
        f'<nav id="sections" class="sections" aria-label="Sections">{"".join(links)}</nav>'
        f'<div class="top-tools">{about_button}'
        f'<button class="top-button theme-toggle" type="button" data-theme="system" aria-label="Theme: system" title="Theme: system">{ICONS}</button>'
        "</div></header>"
    )


def facts(page: str, commit: str, extra: tuple[str, ...] = ()) -> list[str]:
    """What the docs' footer and the playground's About say, as HTML
    items: the licenses, the repository, the commit built, and `extra`."""
    def text(name: str) -> str:
        return relative(f"{name}.txt", page)

    built = (
        f'Built from <a href="{REPO_URL}/commit/{commit}"><code>{commit[:7]}</code></a>'
        if commit
        else "Built from a local tree"
    )
    return [
        f'Code <a href="{text("LICENSE-APACHE")}">Apache-2.0</a> OR <a href="{text("LICENSE-MIT")}">MIT</a>; '
        f'docs <a href="{text("LICENSE-CC-BY-SA-4.0")}">CC BY-SA 4.0</a> (<a href="{text("COPYRIGHT")}">details</a>)',
        f'<a href="{REPO_URL}">temperlang/temper on GitHub</a>',
        built,
        *extra,
    ]


def footer(page: str, commit: str, extra: tuple[str, ...] = ()) -> str:
    """The docs pages' footer."""
    items = " <span aria-hidden=\"true\">·</span> ".join(facts(page, commit, extra))
    return f'<footer class="foot"><p>{items}</p></footer>'


def about(page: str, commit: str, extra: tuple[str, ...] = ()) -> str:
    """The playground's About popover, opened from the header's button."""
    items = "".join(f"<li>{f}</li>" for f in facts(page, commit, extra))
    return (
        '<div id="about" class="about" popover role="dialog" aria-labelledby="about-title">'
        '<div class="about-head"><h2 id="about-title">About</h2>'
        '<button class="top-button" type="button" popovertarget="about" popovertargetaction="hide">Close</button></div>'
        f"<ul>{items}</ul></div>"
    )
