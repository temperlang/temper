//! Names the build's version (`temper_driver::version`), from the
//! workspace's version and git, as `TEMPER_BUILD_VERSION`, with the
//! commit as `TEMPER_BUILD_COMMIT` and whether the tree had changes as
//! `TEMPER_BUILD_MODIFIED`:
//!
//! - `TEMPER_VERSION`, where set, is the version, in its SemVer spelling
//!   (`0.7.0-beta.1`), as for a release built from a tarball;
//! - otherwise git's last `v` tag and the commits since it
//!   (`temper_tmpl::version::Version::from_git`): `0.7.0-alpha.0.123`;
//! - without git, or outside this repository's work tree (a copy of the
//!   sources), or in a shallow clone without the tag, a dev build of the
//!   workspace's version with no commits counted, `0.7.0-alpha.0.0`.
//!
//! It runs again only when HEAD, the branch it's on, or the tags move,
//! or `TEMPER_VERSION` changes, so "modified" is as of then.

use std::path::{Path, PathBuf};
use std::process::Command;

// The version rules, from their one place, which uses only std.
#[allow(dead_code)]
#[path = "../temper-tmpl/src/version.rs"]
mod version;

use version::Version;

fn git(args: &[&str]) -> Option<String> {
    Command::new("git")
        .args(args)
        // Never take the index's lock to refresh it, as `status` would.
        .env("GIT_OPTIONAL_LOCKS", "0")
        .output()
        .ok()
        .filter(|o| o.status.success())
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
}

fn main() {
    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-changed=../temper-tmpl/src/version.rs");
    println!("cargo:rerun-if-env-changed=TEMPER_VERSION");
    let workspace = version::workspace();
    let manifest = PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap());
    // Git's answers only for this repository: not for a copy of the
    // sources inside another one.
    let root = manifest.join("../..").canonicalize().ok();
    let ours = git(&["rev-parse", "--show-toplevel"])
        .and_then(|top| Path::new(&top).canonicalize().ok())
        .is_some_and(|top| Some(top) == root);
    let git = |args: &[&str]| if ours { git(args) } else { None };
    let commit = git(&["rev-parse", "--short", "HEAD"]);
    let override_version = std::env::var("TEMPER_VERSION")
        .ok()
        .filter(|v| !v.is_empty());
    let version = match override_version {
        Some(text) => match Version::parse(&text) {
            Some(v) => v,
            None => panic!(
                "TEMPER_VERSION is {text:?}, not a version as 1.2.0, 1.2.0-beta.1, or \
                 1.2.0-alpha.0.34"
            ),
        },
        None if commit.is_some() => {
            let tag = git(&["describe", "--tags", "--abbrev=0", "--match", "v[0-9]*"]);
            let commits = match &tag {
                Some(tag) => git(&["rev-list", "--count", &format!("{tag}..HEAD")]),
                // A shallow clone's count isn't since the first commit.
                None if git(&["rev-parse", "--is-shallow-repository"]).as_deref()
                    == Some("false") =>
                {
                    git(&["rev-list", "--count", "HEAD"])
                }
                None => None,
            };
            let commits = commits.and_then(|n| n.parse().ok());
            Version::from_git(workspace, tag.as_deref(), commits)
        }
        None => Version::dev(workspace, 0),
    };
    println!("cargo:rustc-env=TEMPER_BUILD_VERSION={version}");
    if let Some(commit) = &commit {
        println!("cargo:rustc-env=TEMPER_BUILD_COMMIT={commit}");
        let modified =
            git(&["status", "--porcelain", "--untracked-files=no"]).is_some_and(|s| !s.is_empty());
        println!("cargo:rustc-env=TEMPER_BUILD_MODIFIED={}", modified as u8);
    }
    // Again when HEAD moves (a commit, or another branch checked out) or
    // a tag comes or goes. A worktree has its own HEAD; refs are shared.
    let dir = git(&["rev-parse", "--git-dir"]);
    let common = git(&["rev-parse", "--git-common-dir"]);
    let branch = git(&["symbolic-ref", "-q", "HEAD"]);
    let mut watched: Vec<PathBuf> = Vec::new();
    if let Some(dir) = &dir {
        watched.push(Path::new(dir).join("HEAD"));
    }
    if let Some(common) = &common {
        let common = Path::new(common);
        watched.push(common.join("packed-refs"));
        watched.push(common.join("refs/tags"));
        if let Some(branch) = &branch {
            watched.push(common.join(branch));
        }
    }
    for path in watched {
        // A path that doesn't exist would run this every build.
        if path.exists() {
            let path = match path.is_absolute() {
                true => path,
                false => manifest.join(path),
            };
            println!("cargo:rerun-if-changed={}", path.display());
        }
    }
}
