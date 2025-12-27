# Release notes management

## Contents

In this directory:

- `snippets/` contains `.md` files that each describe a user visible change
- `releases/` contains files that start with a [semver] tag and end in `.md`
- There's a management tool that manages these files with a [video explainer][explainer]:
  - `release_notes.py show` lists snippet files and the release versions they're
    associated with.
  - `release_notes.py next` takes a [semver] tag, concatenates `snippets/*.md`
    that haven't previously been included in any file in `releases/` and
    generates a new file containing them that may then be edited into shape.
  - `release_notes.py cat` reads each of `releases/*.md`, sorts them by
    semver versions, and concatenates them into a history of all releases
    suitable for historical documenation.

## For developers

When pushing a PR that makes changes relevant to users,
add a `snippets/*.md` file to this directory.
If your branch name is `my-cool-feature`, `snippets/my-cool-feature.md`
is a fine name for a file.

Not all PRs warrant a release note snippets.
If there are multiple, different changes in a PR, feel free to have
multiple snippet files.

## Release snippet conventions

As can be seen in [snippets/first.md](./snippet/first.md), release
note snippets include a short description in the triple-hash header,
followed by descriptive text.

```markdown
### Started managing release notes

Versions of the Temper tool-chain from now on will have release notes
explaining user-visible changes.  Happy versioning!
```

A release note should use `<h3>`,`<h4>`, etc.  `<h1>` is reserved for
the embedding documentation and `<h2>` for a version number.

The header should be the kind of thing that makes sense in a bullet list
item which links to the full descriptive text.

```markdown
# Release notes

## Version 0.1.2

- [Started managing release notes](#started-managing-release-notes)
- ...
- ...
- ...

### Started managing release notes

Versions of the Temper tool-chain from now on will have release notes
explaining user-visible changes.  Happy versioning!

...
```

[semver]: https://semver.org/
[explainer]: https://youtu.be/hyiKIeZRxjw
