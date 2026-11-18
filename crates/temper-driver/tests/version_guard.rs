//! Temper's version is written in one place, the root `Cargo.toml`'s
//! `[workspace.package] version`, and flows from there
//! (`temper_tmpl::version`): no crate sets its own, and no code, runtime,
//! or std source names it, nor 0.6.0, the version tempest's runtimes and
//! std named before. Comment lines in Rust may give examples; tests'
//! goldens hold what output names and aren't looked at. And no crate of
//! the workspace can be published by accident: the workspace says
//! `publish = false`, and every crate takes it.

use std::fs;
use std::path::{Path, PathBuf};

fn root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../..")
}

/// Where the version's rules and their tests are, which spell example
/// versions.
const ALLOWED: [&str; 2] = [
    "crates/temper-tmpl/src/version.rs",
    "crates/temper-tmpl/tests/version_order.rs",
];

fn files(dir: &Path, out: &mut Vec<PathBuf>) {
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().to_string();
        if name.starts_with('.') || ["target", "node_modules", "goldens"].contains(&name.as_str()) {
            continue;
        }
        if path.is_dir() {
            files(&path, out);
        } else {
            out.push(path);
        }
    }
}

/// Whether `text` names `version` as a whole version, not as part of a
/// longer one (`10.7.0`, `0.7.01`).
fn names(text: &str, version: &str) -> bool {
    text.match_indices(version).any(|(at, _)| {
        let before = text[..at].chars().next_back();
        let after = text[at + version.len()..].chars().next();
        !before.is_some_and(|c| c.is_ascii_digit() || c == '.')
            && !after.is_some_and(|c| c.is_ascii_digit())
    })
}

#[test]
fn the_version_is_written_once() {
    let root = root();
    let [major, minor, patch] = temper_tmpl::version::workspace();
    let needles = [format!("{major}.{minor}.{patch}"), "0.6.0".to_string()];
    let mut paths = Vec::new();
    files(&root.join("lib"), &mut paths);
    for entry in fs::read_dir(root.join("crates")).unwrap().flatten() {
        let krate = entry.path();
        for dir in ["src", "runtime"] {
            files(&krate.join(dir), &mut paths);
        }
        for file in ["Cargo.toml", "build.rs"] {
            if krate.join(file).is_file() {
                paths.push(krate.join(file));
            }
        }
        // Each crate's version is the workspace's, and like it, the
        // crate isn't published.
        let manifest = fs::read_to_string(krate.join("Cargo.toml")).unwrap();
        for line in ["version.workspace = true", "publish.workspace = true"] {
            assert!(
                manifest.contains(&format!("\n{line}\n")),
                "{}: not {line}",
                krate.display()
            );
        }
    }
    let mut found = Vec::new();
    for path in paths {
        let rel = path
            .strip_prefix(&root)
            .unwrap()
            .to_string_lossy()
            .replace('\\', "/");
        if ALLOWED.contains(&rel.as_str()) {
            continue;
        }
        let Ok(text) = fs::read_to_string(&path) else {
            continue;
        };
        let rust = rel.ends_with(".rs");
        for (i, line) in text.lines().enumerate() {
            if rust && line.trim_start().starts_with("//") {
                continue;
            }
            for needle in &needles {
                if names(line, needle) {
                    found.push(format!("{rel}:{}: {}", i + 1, line.trim()));
                }
            }
        }
    }
    assert!(
        found.is_empty(),
        "versions written outside the root Cargo.toml (use temper_tmpl::version, or a \
         runtime's VersionSpot):\n{}",
        found.join("\n")
    );
}

/// The workspace's `[workspace.package]` says `publish = false`, which
/// each crate takes (`the_version_is_written_once`): releases ship
/// `temper` and the runtimes, never these crates.
#[test]
fn the_workspace_is_not_published() {
    let manifest = fs::read_to_string(root().join("Cargo.toml")).unwrap();
    let package = manifest
        .split("\n[workspace.package]\n")
        .nth(1)
        .and_then(|rest| rest.split("\n[").next())
        .expect("a [workspace.package] table");
    assert!(
        package.lines().any(|l| l.trim() == "publish = false"),
        "[workspace.package] doesn't say publish = false:\n{package}"
    );
}

/// Python's runtime keeps a pyproject.toml for mypyc's compiled wheels,
/// which `temper build` neither ships nor fills (`temper-be-py`'s
/// `runtime::FILES`), so it always refuses PyPI, as the runtimes do
/// until a release.
#[test]
fn the_mypyc_build_is_not_published() {
    let path = root().join("crates/temper-be-py/runtime/temper-core/mypyc/pyproject.toml");
    let text = fs::read_to_string(&path).unwrap();
    assert!(
        text.lines()
            .any(|l| l.trim() == "classifiers = [\"Private :: Do Not Upload\"]"),
        "{}: no `Private :: Do Not Upload` classifier",
        path.display()
    );
}

#[test]
fn names_whole_versions() {
    assert!(names("version = \"0.7.0\"", "0.7.0"));
    assert!(names("0.7.0-alpha.0.5", "0.7.0"));
    assert!(!names("10.7.0", "0.7.0"));
    assert!(!names("0.7.01", "0.7.0"));
    assert!(!names("1.0.7.0", "0.7.0"));
}
