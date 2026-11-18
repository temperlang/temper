//! Temper's version: one canonical version, the workspace's
//! (`[workspace.package] version` in the root `Cargo.toml`), with a stage
//! and a count of commits from git, spelled for each ecosystem so that
//! dev builds, pre-releases, and releases sort the same way in all of
//! them (docs/internals/detail/06-backends.md, "Versions").
//!
//! | Stage | SemVer (npm, Cargo, NuGet, Maven, vcpkg, Conan) | Python | LuaRocks |
//! | --- | --- | --- | --- |
//! | dev build | `0.7.0-alpha.0.123` | `0.7.0.dev123` | `0.7.0.alpha.0.123-1` |
//! | alpha 1 | `0.7.0-alpha.1` | `0.7.0a1` | `0.7.0.alpha.1-1` |
//! | alpha 1 plus 5 | `0.7.0-alpha.1.5` | `0.7.0a2.dev5` | `0.7.0.alpha.1.5-1` |
//! | beta, rc | `-beta.1`, `-rc.1` (plus `.5`) | `b1`, `rc1` (`b2.dev5`) | `.beta.1-1`, `.rc.1-1` |
//! | release | `0.7.0` | `0.7.0` | `0.7.0-1` |
//! | dev after it | `0.7.1-alpha.0.5` | `0.7.1.dev5` | `0.7.1.alpha.0.5-1` |
//!
//! Go adds `v` to the SemVer spelling; CMake takes only the numbers.
//!
//! This file uses nothing but std, as `temper-driver`'s build script
//! includes it too, to name the build's version from git.

use std::cmp::Ordering;
use std::fmt;
use std::sync::OnceLock;

/// Where a version stands in the release sequence.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Stage {
    /// A build before the base's first pre-release: SemVer's `alpha.0`.
    Dev,
    /// `alpha.K`, `beta.K`, `rc.K`, K from 1.
    Alpha(u64),
    Beta(u64),
    Rc(u64),
    Release,
}

/// A version: the base `major.minor.patch`, its stage, and the commits
/// since that stage's tag (for [`Stage::Dev`], since the last release
/// tag). A release has none: commits after it are a dev build of the
/// next patch ([`Version::new`]).
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct Version {
    pub base: [u64; 3],
    pub stage: Stage,
    pub commits: u64,
}

/// An ecosystem's spelling of a version ([`Version::spell`]).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Spelling {
    /// SemVer 2.0, as npm, Cargo, NuGet, Maven, Gradle, vcpkg, and Conan
    /// take it, and the canonical text: `0.7.0-alpha.0.123`.
    SemVer,
    /// Go's module versions: SemVer with `v`.
    Go,
    /// PEP 440: `0.7.0.dev123`, `0.7.0a1`, `0.7.0a2.dev5`.
    Python,
    /// LuaRocks, as dependencies name it: SemVer with a dot for the
    /// hyphen, `0.7.0.alpha.0.123`.
    LuaRocks,
    /// A rockspec's own version: [`Spelling::LuaRocks`] with revision
    /// `-1`.
    Rockspec,
    /// CMake's `project(VERSION)`: the numbers alone, `0.7.0`.
    CMake,
}

impl Version {
    /// A version, where a release with commits after it is a dev build
    /// of the next patch, as dev builds after a release assume.
    pub fn new(base: [u64; 3], stage: Stage, commits: u64) -> Version {
        match stage {
            Stage::Release if commits > 0 => Version {
                base: [base[0], base[1], base[2] + 1],
                stage: Stage::Dev,
                commits,
            },
            Stage::Release => Version {
                base,
                stage,
                commits: 0,
            },
            _ => Version {
                base,
                stage,
                commits,
            },
        }
    }

    /// A dev build of `base`.
    pub fn dev(base: [u64; 3], commits: u64) -> Version {
        Version::new(base, Stage::Dev, commits)
    }

    /// `major.minor.patch`.
    pub fn numbers(&self) -> String {
        let [major, minor, patch] = self.base;
        format!("{major}.{minor}.{patch}")
    }

    /// This version in an ecosystem's spelling.
    pub fn spell(&self, spelling: Spelling) -> String {
        let numbers = self.numbers();
        let n = self.commits;
        match spelling {
            Spelling::SemVer => match self.pre_release() {
                Some(pre) => format!("{numbers}-{pre}"),
                None => numbers,
            },
            Spelling::Go => format!("v{}", self.spell(Spelling::SemVer)),
            Spelling::LuaRocks => match self.pre_release() {
                Some(pre) => format!("{numbers}.{pre}"),
                None => numbers,
            },
            Spelling::Rockspec => format!("{}-1", self.spell(Spelling::LuaRocks)),
            Spelling::CMake => numbers,
            Spelling::Python => {
                // A pre-release's later commits are a dev build of the
                // next pre-release, as setuptools-scm names them.
                let pre = |label: &str, k: u64| match n {
                    0 => format!("{numbers}{label}{k}"),
                    _ => format!("{numbers}{label}{}.dev{n}", k + 1),
                };
                match self.stage {
                    Stage::Dev => format!("{numbers}.dev{n}"),
                    Stage::Alpha(k) => pre("a", k),
                    Stage::Beta(k) => pre("b", k),
                    Stage::Rc(k) => pre("rc", k),
                    Stage::Release => numbers,
                }
            }
        }
    }

    /// SemVer's pre-release part, if any: `alpha.0.123`, `beta.1`,
    /// `rc.1.5`.
    fn pre_release(&self) -> Option<String> {
        let (label, k) = match self.stage {
            Stage::Dev => return Some(format!("alpha.0.{}", self.commits)),
            Stage::Alpha(k) => ("alpha", k),
            Stage::Beta(k) => ("beta", k),
            Stage::Rc(k) => ("rc", k),
            Stage::Release => return None,
        };
        Some(match self.commits {
            0 => format!("{label}.{k}"),
            n => format!("{label}.{k}.{n}"),
        })
    }

    /// A version in its SemVer spelling ([`Spelling::SemVer`]), as
    /// [`Version::spell`] writes it, or `None` for any other text.
    pub fn parse(text: &str) -> Option<Version> {
        let number = |part: &str| -> Option<u64> {
            let canonical = !part.is_empty()
                && part.bytes().all(|b| b.is_ascii_digit())
                && (part == "0" || !part.starts_with('0'));
            if canonical { part.parse().ok() } else { None }
        };
        let (numbers, pre) = match text.split_once('-') {
            Some((numbers, pre)) => (numbers, Some(pre)),
            None => (text, None),
        };
        let parts: Vec<&str> = numbers.split('.').collect();
        let [major, minor, patch] = parts[..] else {
            return None;
        };
        let base = [number(major)?, number(minor)?, number(patch)?];
        let Some(pre) = pre else {
            return Some(Version::new(base, Stage::Release, 0));
        };
        let parts: Vec<&str> = pre.split('.').collect();
        let (label, k, n) = match parts[..] {
            [label, k] => (label, number(k)?, None),
            [label, k, n] => (label, number(k)?, Some(number(n)?)),
            _ => return None,
        };
        let stage = match (label, k, n) {
            ("alpha", 0, Some(_)) => Stage::Dev,
            (_, 0, _) => return None,
            // A zero count is written without it, so `alpha.1.0` isn't
            // this spelling.
            (_, _, Some(0)) => return None,
            ("alpha", k, _) => Stage::Alpha(k),
            ("beta", k, _) => Stage::Beta(k),
            ("rc", k, _) => Stage::Rc(k),
            _ => return None,
        };
        Some(Version::new(base, stage, n.unwrap_or(0)))
    }

    /// The version a build has, given the workspace's version and, from
    /// git, the last tag (`v` and a version, as `v0.7.0-beta.1`) and the
    /// commits since it, or since the first commit where there's no tag.
    /// Without a tag, or with a tag older than the workspace's version,
    /// it's a dev build of the workspace's version; after a release tag,
    /// of the next patch. An unknown count, as in a shallow clone without
    /// tags, is 0.
    pub fn from_git(workspace: [u64; 3], tag: Option<&str>, commits: Option<u64>) -> Version {
        let n = commits.unwrap_or(0);
        let tagged = tag
            .and_then(|t| t.strip_prefix('v'))
            .and_then(Version::parse);
        match tagged {
            Some(t) => {
                let after = match t.stage {
                    Stage::Dev => Version::dev(t.base, n),
                    stage => Version::new(t.base, stage, n),
                };
                if workspace > after.base {
                    Version::dev(workspace, n)
                } else {
                    after
                }
            }
            None => Version::dev(workspace, n),
        }
    }

    /// Where the stage sorts: dev builds below alpha 1, then alphas,
    /// betas, release candidates, and the release.
    fn rank(&self) -> (u64, u64) {
        match self.stage {
            Stage::Dev => (0, 0),
            Stage::Alpha(k) => (1, k),
            Stage::Beta(k) => (2, k),
            Stage::Rc(k) => (3, k),
            Stage::Release => (4, 0),
        }
    }
}

/// The release order, which every spelling keeps.
impl Ord for Version {
    fn cmp(&self, other: &Version) -> Ordering {
        (self.base, self.rank(), self.commits).cmp(&(other.base, other.rank(), other.commits))
    }
}

impl PartialOrd for Version {
    fn partial_cmp(&self, other: &Version) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

/// The SemVer spelling, the canonical text.
impl fmt::Display for Version {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.spell(Spelling::SemVer))
    }
}

/// A version's text, as a library's config or tmpl gives it, in an
/// ecosystem's spelling: respelled where it's in the SemVer spelling
/// [`Version::parse`] reads, and as it is otherwise.
pub fn respell(text: &str, spelling: Spelling) -> String {
    match Version::parse(text) {
        Some(v) => v.spell(spelling),
        None => text.to_string(),
    }
}

/// The workspace's version, `major.minor.patch`: the one place Temper's
/// version is written.
pub fn workspace() -> [u64; 3] {
    match Version::parse(env!("CARGO_PKG_VERSION")) {
        Some(v) => v.base,
        None => panic!("the workspace's version isn't major.minor.patch"),
    }
}

static CURRENT: OnceLock<Version> = OnceLock::new();

/// The version this process names for the runtimes, std, and core: the
/// build's, where the program set it ([`set_current`], as `temper` does
/// with `temper_driver::version::init`), or else a dev build of the
/// workspace's version with no commits counted, `0.7.0-alpha.0.0`, which
/// tests and the playground see, so their output stays the same from
/// commit to commit.
pub fn current() -> &'static Version {
    CURRENT.get_or_init(|| Version::dev(workspace(), 0))
}

/// Sets [`current`], once, before anything reads it; false if it's
/// already set.
pub fn set_current(version: Version) -> bool {
    CURRENT.set(version).is_ok()
}

/// The version goldens show in place of [`current`] where it would
/// otherwise change from release to release ([`as_sentinel`]): a dev
/// build of 0.0.0 with no commits, `0.0.0-alpha.0.0` in SemVer (and Go's
/// `v0.0.0-alpha.0.0`), `0.0.0.dev0` in Python, `0.0.0.alpha.0.0-1` in a
/// rockspec. Each is a version its file's ecosystem takes, so a golden
/// stays valid in its language, and none is a real version or the
/// runtimes' [`PLACEHOLDER`].
pub fn sentinel() -> Version {
    Version::dev([0, 0, 0], 0)
}

/// `text` with each spelling of `version` ([`Spelling::SemVer`],
/// [`Spelling::LuaRocks`], and [`Spelling::Python`], so Go's and the
/// rockspec's too) as the same spelling of [`sentinel`], wherever it's a
/// whole version, not part of a longer one (`10.7.0-alpha.0.0`,
/// `0.7.0-alpha.0.01`), as the tests' goldens of functional tests and
/// shared programs show [`current`], so a release changes only std's and
/// core's goldens and the version's own tests. Only those exact texts:
/// any other version stays, and differs from the golden. A release's
/// spellings are the bare numbers, as is CMake's spelling of any
/// version, which other text could name, so for a release nothing is
/// replaced, nor ever CMake's numbers (tests' [`current`] is never a
/// release, and the goldens naming it in CMake's spelling are std's).
pub fn as_sentinel(text: &str, version: &Version) -> String {
    if version.stage == Stage::Release {
        return text.to_string();
    }
    let sentinel = sentinel();
    let mut out = text.to_string();
    for spelling in [Spelling::SemVer, Spelling::LuaRocks, Spelling::Python] {
        let spelled = version.spell(spelling);
        let fixed = sentinel.spell(spelling);
        let mut replaced = String::with_capacity(out.len());
        let mut rest = out.as_str();
        while let Some(at) = rest.find(&spelled) {
            let end = at + spelled.len();
            let before = rest[..at].chars().next_back();
            let after = &rest[end..];
            // Not a longer version: no digit or dot before, and no digit
            // after, alone or after a dot.
            let digit = |s: &str| s.starts_with(|c: char| c.is_ascii_digit());
            let longer = digit(after) || after.strip_prefix('.').is_some_and(digit);
            let whole = !before.is_some_and(|c| c.is_ascii_digit() || c == '.') && !longer;
            replaced.push_str(&rest[..at]);
            replaced.push_str(if whole { &fixed } else { &spelled });
            rest = after;
        }
        replaced.push_str(rest);
        out = replaced;
    }
    out
}

/// [`as_sentinel`] for [`current`].
pub fn current_as_sentinel(text: &str) -> String {
    as_sentinel(text, current())
}

/// A place in a runtime's file that names the runtime's version: the
/// exact text there, with the version as [`PLACEHOLDER`], which
/// [`fill_runtime`] spells for the current version. The sources keep the
/// placeholder, a version every ecosystem takes, so a runtime still
/// builds and tests where it is.
#[derive(Clone, Copy, Debug)]
pub struct VersionSpot {
    /// The file, by path in the runtime.
    pub path: &'static str,
    /// The text there, as `version = "0.0.0"`.
    pub text: &'static str,
    pub spelling: Spelling,
    /// The file's name in `temper.out`, with `{}` for the version, where
    /// it names it, as a rockspec's does.
    pub rename: Option<&'static str>,
}

/// The version runtimes' sources name, for [`VersionSpot`]s to fill.
pub const PLACEHOLDER: &str = "0.0.0";

/// Text in a runtime's file that keeps its package from being published,
/// as Cargo's `publish = false` or npm's `"private": true`, with its line
/// end: the sources name [`PLACEHOLDER`], which no one should publish,
/// and [`fill_runtime`] removes it only for a release, so a dev build's
/// or pre-release's runtime can't be published by accident either.
#[derive(Clone, Copy, Debug)]
pub struct Unpublished {
    /// The file, by path in the runtime.
    pub path: &'static str,
    /// The text there, removed for a release.
    pub text: &'static str,
}

/// Where a runtime names its version ([`VersionSpot`]s) and what keeps
/// it from being published ([`Unpublished`]), which `temper build`
/// handles as it writes the runtime ([`fill_runtime`]).
#[derive(Clone, Copy, Debug)]
pub struct RuntimeVersion {
    pub spots: &'static [VersionSpot],
    /// None where the ecosystem has no such marker: LuaRocks', and C++'s,
    /// which has no package file of its own.
    pub unpublished: &'static [Unpublished],
}

/// A runtime's files with each [`VersionSpot`] filled with `version`,
/// and, where `version` is a release (no pre-release part), each
/// [`Unpublished`] removed, as `temper build` writes them into
/// `temper.out`. A spot or marker that isn't in its file, exactly once,
/// is a bug; each backend's tests check theirs ([`check_spots`]).
pub fn fill_runtime(
    files: &[(&str, &str)],
    runtime: &RuntimeVersion,
    version: &Version,
) -> Vec<(String, String)> {
    let release = version.stage == Stage::Release;
    (files.iter())
        .map(|&(path, text)| {
            let mut name = path.to_string();
            let mut text = text.to_string();
            for spot in runtime.spots.iter().filter(|s| s.path == path) {
                let spelled = version.spell(spot.spelling);
                text = text.replacen(spot.text, &spot.text.replace(PLACEHOLDER, &spelled), 1);
                if let Some(rename) = spot.rename {
                    name = rename.replace("{}", &spelled);
                }
            }
            if release {
                for marker in runtime.unpublished.iter().filter(|m| m.path == path) {
                    text = text.replacen(marker.text, "", 1);
                }
            }
            (name, text)
        })
        .collect()
}

/// Problems with a runtime's spots and markers: one not in its file
/// exactly once, a spot not naming [`PLACEHOLDER`], or a marker not
/// ending its line.
pub fn check_spots(files: &[(&str, &str)], runtime: &RuntimeVersion) -> Vec<String> {
    let mut problems = Vec::new();
    let mut once = |path: &str, text: &str| match files.iter().find(|(p, _)| *p == path) {
        Some((_, file)) if file.matches(text).count() == 1 => {}
        Some(_) => problems.push(format!("{path}: `{text}` isn't there once")),
        None => problems.push(format!("{path}: no such file")),
    };
    for spot in runtime.spots {
        once(spot.path, spot.text);
    }
    for marker in runtime.unpublished {
        once(marker.path, marker.text);
    }
    for spot in runtime.spots {
        if !spot.text.contains(PLACEHOLDER) {
            problems.push(format!("{}: `{}` has no placeholder", spot.path, spot.text));
        }
    }
    for marker in runtime.unpublished {
        if !marker.text.ends_with('\n') {
            problems.push(format!(
                "{}: `{}` doesn't end its line",
                marker.path, marker.text
            ));
        }
    }
    problems
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The test sequence, in release order: dev builds, alpha 1, its
    /// later commits, alpha 2, beta 1 and after, rc 1 and after, the
    /// release, and a dev build after it.
    pub(crate) fn sequence() -> Vec<Version> {
        let b = [0, 7, 0];
        vec![
            Version::dev(b, 5),
            Version::dev(b, 123),
            Version::new(b, Stage::Alpha(1), 0),
            Version::new(b, Stage::Alpha(1), 5),
            Version::new(b, Stage::Alpha(2), 0),
            Version::new(b, Stage::Beta(1), 0),
            Version::new(b, Stage::Beta(1), 5),
            Version::new(b, Stage::Rc(1), 0),
            Version::new(b, Stage::Rc(1), 5),
            Version::new(b, Stage::Release, 0),
            Version::new(b, Stage::Release, 5),
        ]
    }

    fn spelled(spelling: Spelling) -> Vec<String> {
        sequence().iter().map(|v| v.spell(spelling)).collect()
    }

    #[test]
    fn sequence_is_in_release_order() {
        let s = sequence();
        assert!(s.windows(2).all(|w| w[0] < w[1]), "{s:?}");
        assert_eq!(s[10], Version::dev([0, 7, 1], 5));
    }

    #[test]
    fn semver() {
        assert_eq!(
            spelled(Spelling::SemVer),
            [
                "0.7.0-alpha.0.5",
                "0.7.0-alpha.0.123",
                "0.7.0-alpha.1",
                "0.7.0-alpha.1.5",
                "0.7.0-alpha.2",
                "0.7.0-beta.1",
                "0.7.0-beta.1.5",
                "0.7.0-rc.1",
                "0.7.0-rc.1.5",
                "0.7.0",
                "0.7.1-alpha.0.5",
            ]
        );
        assert_eq!(sequence()[1].spell(Spelling::Go), "v0.7.0-alpha.0.123");
    }

    #[test]
    fn python() {
        assert_eq!(
            spelled(Spelling::Python),
            [
                "0.7.0.dev5",
                "0.7.0.dev123",
                "0.7.0a1",
                "0.7.0a2.dev5",
                "0.7.0a2",
                "0.7.0b1",
                "0.7.0b2.dev5",
                "0.7.0rc1",
                "0.7.0rc2.dev5",
                "0.7.0",
                "0.7.1.dev5",
            ]
        );
    }

    #[test]
    fn luarocks() {
        assert_eq!(
            spelled(Spelling::Rockspec),
            [
                "0.7.0.alpha.0.5-1",
                "0.7.0.alpha.0.123-1",
                "0.7.0.alpha.1-1",
                "0.7.0.alpha.1.5-1",
                "0.7.0.alpha.2-1",
                "0.7.0.beta.1-1",
                "0.7.0.beta.1.5-1",
                "0.7.0.rc.1-1",
                "0.7.0.rc.1.5-1",
                "0.7.0-1",
                "0.7.1.alpha.0.5-1",
            ]
        );
        assert_eq!(sequence()[3].spell(Spelling::LuaRocks), "0.7.0.alpha.1.5");
        assert!(spelled(Spelling::CMake)[..10].iter().all(|v| v == "0.7.0"));
    }

    #[test]
    fn semver_reads_back() {
        for v in sequence() {
            assert_eq!(Version::parse(&v.to_string()), Some(v.clone()), "{v}");
        }
        for text in [
            "0.7",
            "0.7.0.1",
            "07.0.0",
            "0.7.0-dev.5",
            "0.7.0-alpha",
            "0.7.0-alpha.0",
            "0.7.0-beta.0",
            "0.7.0-alpha.1.0",
            "0.7.0-alpha.01",
            "0.7.0+abc",
            "v0.7.0",
        ] {
            assert_eq!(Version::parse(text), None, "{text}");
        }
        assert_eq!(respell("0.7.0-beta.1", Spelling::Python), "0.7.0b1");
        assert_eq!(respell("1.2.0", Spelling::Rockspec), "1.2.0-1");
        assert_eq!(respell("1.0.0-pre", Spelling::Python), "1.0.0-pre");
    }

    #[test]
    fn from_git() {
        let w = [0, 7, 0];
        let v = |tag, n| Version::from_git(w, tag, n).to_string();
        assert_eq!(v(None, Some(213)), "0.7.0-alpha.0.213");
        assert_eq!(v(None, None), "0.7.0-alpha.0.0");
        assert_eq!(v(Some("v0.7.0-alpha.1"), Some(0)), "0.7.0-alpha.1");
        assert_eq!(v(Some("v0.7.0-alpha.1"), Some(5)), "0.7.0-alpha.1.5");
        assert_eq!(v(Some("v0.7.0-rc.2"), Some(3)), "0.7.0-rc.2.3");
        assert_eq!(v(Some("v0.7.0"), Some(0)), "0.7.0");
        assert_eq!(v(Some("v0.7.0"), Some(5)), "0.7.1-alpha.0.5");
        // The workspace moved past the tag: a dev build of its version.
        assert_eq!(v(Some("v0.6.0"), Some(5)), "0.7.0-alpha.0.5");
        assert_eq!(
            Version::from_git([0, 8, 0], Some("v0.7.0"), Some(5)).to_string(),
            "0.8.0-alpha.0.5"
        );
        // A tag ahead of the workspace's version wins.
        assert_eq!(v(Some("v0.7.1-beta.1"), Some(0)), "0.7.1-beta.1");
        // A tag in no form read here is no tag.
        assert_eq!(v(Some("v0.0.7-dev"), Some(9)), "0.7.0-alpha.0.9");
    }

    #[test]
    fn sentinel_replaces_only_the_whole_version() {
        let v = Version::dev([0, 7, 0], 0);
        let text = "uses core 0.7.0-alpha.0.0 {\n\
                    version = \"0.7.0.dev0\"\n\
                    'core == 0.7.0.alpha.0.0', version = \"0.7.0.alpha.0.0-1\"\n\
                    go v0.7.0-alpha.0.0\n\
                    10.7.0-alpha.0.0 0.7.0-alpha.0.01 0.7.0-alpha.0.0.5 0.7.0.dev01\n\
                    0.7.0-alpha.0.5 0.7.0 0.7.0.dev5 0.7.0-alpha.1 0.7.1-alpha.0.0\n";
        assert_eq!(
            as_sentinel(text, &v),
            "uses core 0.0.0-alpha.0.0 {\n\
             version = \"0.0.0.dev0\"\n\
             'core == 0.0.0.alpha.0.0', version = \"0.0.0.alpha.0.0-1\"\n\
             go v0.0.0-alpha.0.0\n\
             10.7.0-alpha.0.0 0.7.0-alpha.0.01 0.7.0-alpha.0.0.5 0.7.0.dev01\n\
             0.7.0-alpha.0.5 0.7.0 0.7.0.dev5 0.7.0-alpha.1 0.7.1-alpha.0.0\n"
        );
        let release = Version::new([0, 7, 0], Stage::Release, 0);
        assert_eq!(as_sentinel("version 0.7.0", &release), "version 0.7.0");
        // Every spelling of the sentinel parses as that spelling's version
        // and is neither the placeholder nor a release.
        assert_eq!(Version::parse(&sentinel().to_string()), Some(sentinel()));
        assert_ne!(sentinel().to_string(), PLACEHOLDER);
    }

    #[test]
    fn spots_fill() {
        let files = [
            (
                "p.toml",
                "[project]\nversion = \"0.0.0\"\nx = \"10.0.0\"\nprivate = true\n",
            ),
            ("r-dev-1.rockspec", "version = \"0.0.0-1\"\n"),
        ];
        let runtime = RuntimeVersion {
            spots: &[
                VersionSpot {
                    path: "p.toml",
                    text: "version = \"0.0.0\"",
                    spelling: Spelling::Python,
                    rename: None,
                },
                VersionSpot {
                    path: "r-dev-1.rockspec",
                    text: "version = \"0.0.0-1\"",
                    spelling: Spelling::LuaRocks,
                    rename: Some("r-{}-1.rockspec"),
                },
            ],
            unpublished: &[Unpublished {
                path: "p.toml",
                text: "private = true\n",
            }],
        };
        assert!(check_spots(&files, &runtime).is_empty());
        // A dev build keeps the marker.
        let filled = fill_runtime(&files, &runtime, &Version::dev([0, 7, 0], 3));
        assert_eq!(
            filled,
            [
                (
                    "p.toml".to_string(),
                    "[project]\nversion = \"0.7.0.dev3\"\nx = \"10.0.0\"\nprivate = true\n"
                        .to_string()
                ),
                (
                    "r-0.7.0.alpha.0.3-1.rockspec".to_string(),
                    "version = \"0.7.0.alpha.0.3-1\"\n".to_string()
                ),
            ]
        );
        // So does a pre-release; a release drops it.
        let rc = fill_runtime(&files, &runtime, &Version::new([0, 7, 0], Stage::Rc(1), 0));
        assert!(rc[0].1.contains("private = true\n"), "{rc:?}");
        let release = fill_runtime(
            &files,
            &runtime,
            &Version::new([0, 7, 0], Stage::Release, 0),
        );
        assert_eq!(
            release[0].1,
            "[project]\nversion = \"0.7.0\"\nx = \"10.0.0\"\n"
        );
        // Markers are checked as spots are.
        let broken = RuntimeVersion {
            spots: &[],
            unpublished: &[
                Unpublished {
                    path: "p.toml",
                    text: "publish = false\n",
                },
                Unpublished {
                    path: "p.toml",
                    text: "private = true",
                },
            ],
        };
        assert_eq!(check_spots(&files, &broken).len(), 2);
    }
}
