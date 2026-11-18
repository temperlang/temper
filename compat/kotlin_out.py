# /// script
# requires-python = ">=3.11"
# ///
"""Records Kotlin Temper's temper.out layout for the functional tests that
tempest translates, for `crates/tempest/tests/kotlin_layout.rs` to compare
tempest's with.

    uv run compat/kotlin_out.py BACKEND=DIR...

Each DIR holds a directory per test, named for its library (`fibonacci`),
where Kotlin's `temper build -b BACKEND` has run, with a `config.temper.md`
naming the library and the test's main file beside it. The record,
`crates/tempest/tests/kotlin-out.txt`, has a line per file under
`temper.out/BACKEND` (the runtime's too), and per package file's name and
version, as `kotlin_layout.rs` reads them:

    fibonacci py file fibonacci/pyproject.toml
    fibonacci py meta name fibonacci
    * py file temper-core/pyproject.toml

the runtime's files once per backend, as `*`. Logs (`-logs`) and what builds and test runs leave are left out.
"""

import os
import re
import sys

LIBRARIES = [
    "basics",
    "fibonacci",
    "hello-world",
    "if-return",
    "is-empty",
    "loop-reenterable",
    "nonascii",
    # With classes and interfaces.
    "call-override-from-subtype",
    "direct-getter",
    "empty",
    "extra-class",
    "hello-from-class-to-top",
    "hello-world-object",
    "inherited-getter",
    "private-method",
    "pure-virtual",
    "setters",
    "static-properties",
    "static-properties-scope",
    "types",
    "write-back-through-field",
    # With nullable types and `orelse` (`types/float/basics` lays out as
    # `types/int/basics` does, under the same name).
    "bubble",
    "limits",
    "mutually-referencing-types",
]

# Package files, by the path after the library's directory, and the
# fields read from each: (field, pattern), the first match's group.
PACKAGE_FILES = {
    "py": [("pyproject.toml", [("name", r'^name = "([^"]*)"'), ("version", r'^version = "([^"]*)"')])],
    "rust": [("Cargo.toml", [("name", r'^name = "([^"]*)"'), ("version", r'^version = "([^"]*)"')])],
    "js": [("package.json", [("name", r'^    "name": "([^"]*)"'), ("version", r'^    "version": "([^"]*)"')])],
    "java": [("pom.xml", [("group", r"^  <groupId>([^<]*)<"), ("name", r"^  <artifactId>([^<]*)<"), ("version", r"^  <version>([^<]*)<")])],
    "csharp": [("src/*.csproj", [("name", r"<RootNamespace>([^<]*)<"), ("version", r"<Version>([^<]*)<")])],
    "lua": [("*.rockspec", [("name", r"""^package = ['"]([^'"]*)['"]"""), ("version", r"""^version = ['"]([^'"]*)['"]""")])],
    "cpp": [],
}

# What a build or a run leaves, not what `temper build` writes.
LEFT = re.compile(
    r"(^|/)(target|build|bin|obj|node_modules|__pycache__|TestResults|\.idea)(/|$)"
    r"|\.log$|(^|/)stderr[^/]*\.txt$|(^|/)(test-results|result)\.xml$|^[^/]+/Cargo\.lock$"
)


def meta_lines(library, backend, root):
    lines = []
    for pattern, fields in PACKAGE_FILES[backend]:
        directory, _, glob = pattern.rpartition("/")
        where = os.path.join(root, library, directory)
        names = sorted(os.listdir(where)) if os.path.isdir(where) else []
        found = [n for n in names if re.fullmatch(glob.replace(".", r"\.").replace("*", ".*"), n)]
        if not found:
            continue
        text = open(os.path.join(where, found[0]), encoding="utf-8").read()
        for field, regex in fields:
            m = re.search(regex, text, re.M)
            if m:
                lines.append(f"{library} {backend} meta {field} {m.group(1)}")
    return lines


def main():
    out = []
    for arg in sys.argv[1:]:
        backend, _, base = arg.partition("=")
        for library in LIBRARIES:
            root = os.path.join(base, library, "temper.out", backend)
            if not os.path.isdir(root):
                sys.exit(f"no {root}")
            files = []
            for d, dirs, names in os.walk(root):
                for n in names:
                    rel = os.path.relpath(os.path.join(d, n), root).replace(os.sep, "/")
                    if not LEFT.search(rel) or rel == "temper-core/Cargo.lock":
                        files.append(rel)
            for f in sorted(files):
                # The runtime, the same beside every library, once.
                line = f"* {backend} file {f}" if f.startswith("temper-core/") else f"{library} {backend} file {f}"
                if line not in out:
                    out.append(line)
            out.extend(meta_lines(library, backend, root))
    here = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(here, "..", "crates", "tempest", "tests", "kotlin-out.txt")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Kotlin Temper's temper.out for the functional tests tempest translates,\n")
        f.write("# from compat/kotlin_out.py: library, backend, then a file or a\n")
        f.write("# package file's field. Recorded at Kotlin Temper 69f1a5b0, its\n")
        f.write("# last sync; once tempest replaces Kotlin Temper, there's no Kotlin\n")
        f.write("# output to re-record this from.\n")
        f.write("\n".join(out) + "\n")
    print(f"{len(out)} lines")


if __name__ == "__main__":
    main()
