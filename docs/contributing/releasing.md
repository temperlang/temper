# Releasing

A release is a `v` tag. Pushing one runs
[release.yml](../../.github/workflows/release.yml), which makes a
**public GitHub release automatically**, so push a `v` tag only on
purpose: even a release candidate's release is public. Nothing goes to
a package registry yet: the release carries the packages as each
registry would take them, for review and for installing by hand.

## Who tags, and what

A maintainer with push access tags a commit on `main` that CI has
passed, signed ([signing commits](signing-commits.md)):

    git tag -s v0.7.0-rc.1 -m "Temper 0.7.0-rc.1"
    git push origin v0.7.0-rc.1

The tag must name the workspace's version, the root `Cargo.toml`'s
`[workspace.package] version`, or a pre-release of it: `v0.7.0-alpha.1`,
`v0.7.0-beta.1`, `v0.7.0-rc.1`, then `v0.7.0`
([Versions](../internals/detail/06-backends.md#versions-one-version-spelled-for-each-ecosystem)).
Anything else (`v0.7.1` while the workspace says 0.7.0, `v0.7.0-rc1`,
`v0.7.0-dev.1`) fails the first job, and nothing else runs; `uv run
.github/ci-tools/release.py check TAG` says so locally first. To
release another version, change the workspace's version in a commit
first. A tag on a commit that isn't on `main` only warns, so a release
candidate can try the workflow from a branch.

## Release candidates first

Tag `-rc.1` before every release, and review what it makes. Alphas and
betas are optional. Pre-releases are marked as such on GitHub, and
their runtimes still refuse publishing (`publish = false`, `"private":
true`, and so on), so their packages can be looked at and installed by
hand but not published. Two packages can't be packed at all until a
release, and the run says so: NuGet's (`IsPackable` false), and
temper-std's crate, which needs a publishable temper-core. Fix what
the candidate shows in commits to `main`, then tag `-rc.2`, and so on;
tag the release on the last candidate's commit, or a later one if
something changed.

## What the release has

- `temper-VERSION-TARGET.tar.gz` (`.zip` on Windows): the compiler,
  with std built in, for Linux x64 and Arm64, macOS Arm64, and Windows
  x64 and Arm64, from [binaries.yml](../../.github/workflows/binaries.yml);
- `temper-wasm-VERSION.tar.gz`, and the VS Code extension's `.vsix`
  files (with their own version);
- the runtimes (temper-core) and std, built by the Linux x64 `temper`,
  as each registry takes them: `.crate`s, npm `.tgz`s, wheels and
  sdists, NuGet `.nupkg`s, `temper-maven-VERSION.zip` (Maven Central's
  bundle layout, unsigned), and LuaRocks rockspecs and `.src.rock`s;
- `SHA256SUMS`, for `sha256sum -c SHA256SUMS`.

## What to review

- `temper --version` from a downloaded binary says the tag's version
  and commit, without "modified", on each platform you can run.
- Each package names the version in its ecosystem's spelling
  (`0.7.0-rc.1`, `0.7.0rc1`, `0.7.0.rc.1-1`), and std depends on
  exactly that temper-core.
- For a release, no package refuses publishing; for a pre-release,
  every runtime still does (the run checks both).
- Contents: unpack a few (`tar tzf`, `unzip -l`) for stray or missing
  files, licenses, and READMEs; install one per language you know into
  a scratch project.
- The run's warnings, and the playground's About after Pages deploys.

## A bad tag

While nothing is on a registry, a bad tag is cheap to undo:

1. Delete the release (`gh release delete vX --yes`), and the tag, on
   GitHub and locally: `git push origin :refs/tags/vX`, `git tag -d vX`.
2. Fix the cause in a commit, and tag again: the same name for a tag
   nobody should have used yet, or, once anyone may have downloaded
   it, the next candidate (`-rc.2`), as versions are best never reused.

A failed run makes no release: fix the cause and re-run the failed
jobs if it was outside the repository (a flaky download), or delete
and retag as above. A run that failed only at `release` because the
release exists needs that release deleted first.

## Publishing, later

The commented `publish` job at the end of release.yml lists, for each
registry, the command, the secret it needs, and its dry run. When
it's wired up, it runs for releases only, in a protected environment a
maintainer approves, before `release`, so binaries appear only once
the runtimes they name can be installed: temper-core first in each
registry, then std. Registries never take a version twice, so from
then on a bad release is fixed by the next patch release, not a retag;
yanking (crates.io, PyPI), deprecating (npm), or unlisting (NuGet)
only hides one.
