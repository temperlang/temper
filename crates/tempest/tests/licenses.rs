//! `temper licenses` prints `src/licenses.txt`: the third-party crates
//! built into temper, with their licenses. This test makes that text from
//! Cargo's metadata (`cargo metadata`, offline where it has to be) and
//! fails if the file differs, so it can't fall behind `Cargo.lock`; run
//! it with `UPDATE_SNAPSHOTS=1` to write the file, and review the diff.
//!
//! The crates are those `temper`'s default features reach through normal
//! dependencies, on any platform, but not proc macros or build
//! dependencies, which run while building and aren't in the binary. For
//! each: its version, license, and repository. A crate that may be used
//! under MIT or Apache-2.0, whose texts `temper license` prints, gets
//! the copyright lines of its license files; any other gets its license
//! files in full.

use serde_json::Value;
use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use std::process::Command;

#[test]
fn licenses_are_current() {
    let workspace = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../..");
    let file = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("src/licenses.txt");
    let text = licenses(&workspace);
    if std::env::var_os("UPDATE_SNAPSHOTS").is_some() {
        std::fs::write(&file, &text).unwrap();
        return;
    }
    let current = std::fs::read_to_string(&file).unwrap();
    assert!(
        current == text,
        "src/licenses.txt is out of date; run with UPDATE_SNAPSHOTS=1 and review the diff"
    );
}

/// Cargo's metadata for the workspace, online if it can be, else offline.
fn metadata(workspace: &Path) -> Value {
    let cargo = std::env::var("CARGO").unwrap_or_else(|_| "cargo".into());
    let run = |offline: bool| {
        let mut command = Command::new(&cargo);
        command
            .args(["metadata", "--format-version", "1", "--locked"])
            .current_dir(workspace);
        if offline {
            command.arg("--offline");
        }
        command.output().ok().filter(|o| o.status.success())
    };
    let output = run(true)
        .or_else(|| run(false))
        .expect("cargo metadata failed");
    serde_json::from_slice(&output.stdout).unwrap()
}

/// The text `temper licenses` prints.
fn licenses(workspace: &Path) -> String {
    let meta = metadata(workspace);
    let packages: BTreeMap<&str, &Value> = (meta["packages"].as_array().unwrap().iter())
        .map(|p| (p["id"].as_str().unwrap(), p))
        .collect();
    let nodes: BTreeMap<&str, &Value> = (meta["resolve"]["nodes"].as_array().unwrap().iter())
        .map(|n| (n["id"].as_str().unwrap(), n))
        .collect();
    let root = (packages.iter())
        .find(|(_, p)| p["name"] == "tempest")
        .map(|(id, _)| *id)
        .unwrap();
    // Each crate reached, by id, and whether only on some platforms.
    let mut reached: BTreeMap<&str, bool> = BTreeMap::new();
    let mut stack = vec![(root, false)];
    while let Some((id, some_platforms)) = stack.pop() {
        let package = packages[id];
        let proc_macro = (package["targets"].as_array().unwrap().iter())
            .any(|t| (t["kind"].as_array().unwrap().iter()).any(|k| k == "proc-macro"));
        if proc_macro {
            continue;
        }
        match reached.get(id) {
            Some(&seen) if !seen || some_platforms => continue,
            _ => {}
        }
        reached.insert(id, some_platforms);
        for dep in nodes[id]["deps"].as_array().unwrap() {
            let kinds: Vec<&Value> = (dep["dep_kinds"].as_array().unwrap().iter())
                .filter(|k| k["kind"].is_null())
                .collect();
            if kinds.is_empty() {
                continue;
            }
            let only_some = some_platforms || kinds.iter().all(|k| !k["target"].is_null());
            stack.push((dep["pkg"].as_str().unwrap(), only_some));
        }
    }
    let mut crates: Vec<(&str, &str, &Value, bool)> = (reached.iter())
        .map(|(id, some)| (*id, packages[id], *some))
        // The workspace's own crates are tempest.
        .filter(|(_, p, _)| !p["source"].is_null())
        .map(|(id, p, some)| (p["name"].as_str().unwrap(), id, p, some))
        .map(|(name, _, p, some)| (name, p["version"].as_str().unwrap(), p, some))
        .collect();
    crates.sort_by(|a, b| (a.0, a.1).cmp(&(b.0, b.1)));
    let mut out = String::from(
        "Third-party crates built into temper, made from Cargo's metadata\n\
         (crates/tempest/tests/licenses.rs; do not edit). Each may be used under\n\
         the license named. Where that offers MIT or Apache-2.0, whose texts\n\
         `temper license` prints, its copyright lines follow; otherwise, its\n\
         license files in full. \"Some platforms only\" marks crates that only\n\
         some platforms' builds have.\n",
    );
    for (name, version, package, some) in crates {
        out.push('\n');
        out.push_str(&heading(&format!("{name} {version}")));
        let license = package["license"].as_str().unwrap_or("see its files");
        out.push_str(&format!("License: {license}\n"));
        if let Some(repository) = package["repository"].as_str() {
            out.push_str(&format!("Repository: {repository}\n"));
        }
        if some {
            out.push_str("Some platforms only\n");
        }
        let dir = Path::new(package["manifest_path"].as_str().unwrap())
            .parent()
            .unwrap();
        let files = license_files(dir);
        if offers_mit_or_apache(license) {
            let mut lines: Vec<String> = Vec::new();
            for (_, text) in &files {
                for line in text.lines().map(str::trim) {
                    let copyright = line.starts_with("Copyright") || line.starts_with('©');
                    let template = line.contains('[') || line.contains("notice");
                    if copyright && !template && !lines.iter().any(|l| l == line) {
                        lines.push(line.to_string());
                    }
                }
            }
            for line in lines {
                out.push_str(&format!("{line}\n"));
            }
        } else {
            for (file, text) in &files {
                out.push_str(&format!("\n{file}:\n\n{}\n", text.trim_end()));
            }
        }
    }
    out
}

/// Whether a license expression lets the crate be used under MIT or
/// Apache-2.0 alone (`MIT OR Apache-2.0`, or the old `MIT/Apache-2.0`).
fn offers_mit_or_apache(license: &str) -> bool {
    (license.split(" OR ").flat_map(|l| l.split('/')))
        .map(|l| l.trim().trim_matches(['(', ')']))
        .any(|l| l == "MIT" || l == "Apache-2.0")
}

/// A crate's license files (LICENSE*, COPYING*, NOTICE*, COPYRIGHT*),
/// by name, sorted, with their texts.
fn license_files(dir: &Path) -> Vec<(String, String)> {
    let mut files: Vec<(String, String)> = std::fs::read_dir(dir)
        .map(|entries| {
            entries
                .flatten()
                .filter(|e| e.path().is_file())
                .filter_map(|e| {
                    let name = e.file_name().to_string_lossy().into_owned();
                    let upper = name.to_uppercase();
                    let legal = ["LICEN", "COPYING", "NOTICE", "COPYRIGHT"]
                        .iter()
                        .any(|p| upper.starts_with(p));
                    let text = std::fs::read(e.path()).ok()?;
                    legal.then(|| (name, String::from_utf8_lossy(&text).into_owned()))
                })
                .collect()
        })
        .unwrap_or_default();
    files.sort();
    files
}

/// A name in a box of dashes, as Kotlin's license files head each part.
fn heading(name: &str) -> String {
    let text = format!("--- {name} ---");
    let line = "-".repeat(text.chars().count());
    format!("{line}\n{text}\n{line}\n")
}
