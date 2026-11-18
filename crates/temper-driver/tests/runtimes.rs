//! Each backend's built-in runtime (`temper_be_*::runtime::FILES`), which
//! `temper build` writes beside the libraries, is its crate's
//! `runtime/temper-core`, copied as Kotlin copies it: every file but its
//! dotfiles and its own tests and tools, with the same text, where each
//! place naming its version names the placeholder `temper build` fills,
//! and which only a release lets be published.

use std::fs;
use std::path::{Path, PathBuf};
#[allow(unused_imports)] // Unless every backend is left out.
use temper_tmpl::version::{RuntimeVersion, Stage, Version, fill_runtime, workspace};

/// What Kotlin leaves out of a runtime it copies.
fn left_out(backend: &str, path: &str) -> bool {
    // Dotfiles, and what's in dot folders, as a checker's `.mypy_cache`.
    path.split('/').any(|part| part.starts_with('.'))
        || (backend, path) == ("js", "package-lock.json")
        || (backend == "py" && path.starts_with("mypyc/"))
        || (backend == "java" && path.starts_with("src/test/"))
}

fn source_files(backend: &str, dir: &Path, root: &Path, out: &mut Vec<(String, String)>) {
    for entry in fs::read_dir(dir).unwrap() {
        let path = entry.unwrap().path();
        if path.is_dir() {
            source_files(backend, &path, root, out);
            continue;
        }
        let rel = path
            .strip_prefix(root)
            .unwrap()
            .to_string_lossy()
            .replace('\\', "/");
        if !left_out(backend, &rel) {
            out.push((rel, fs::read_to_string(&path).unwrap()));
        }
    }
}

#[allow(dead_code)] // Unless every backend is left out.
fn check(backend: &str, files: &[(&str, &str)], runtime: &RuntimeVersion) {
    // Each place naming the version is there, once, as the placeholder,
    // and each marker keeping it from being published, once.
    let problems = temper_tmpl::version::check_spots(files, runtime);
    assert!(problems.is_empty(), "{backend}: {problems:?}");
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join(format!("../temper-be-{backend}/runtime/temper-core"));
    let mut expected = Vec::new();
    source_files(backend, &root, &root, &mut expected);
    expected.sort();
    let mut actual: Vec<(String, String)> = (files.iter())
        .map(|(p, t)| (p.to_string(), t.to_string()))
        .collect();
    actual.sort();
    let names = |v: &[(String, String)]| v.iter().map(|(p, _)| p.clone()).collect::<Vec<_>>();
    assert_eq!(names(&actual), names(&expected), "{backend}");
    assert!(actual == expected, "{backend}: a file's text differs");
    unpublished(backend, files, runtime);
}

/// Until a release, a runtime as `temper build` writes it can't be
/// published, where its ecosystem has a way to say so; a release drops
/// what says so, and nothing else.
#[allow(dead_code)] // Unless every backend is left out.
fn unpublished(backend: &str, files: &[(&str, &str)], runtime: &RuntimeVersion) {
    let marked = ["csharp", "java", "js", "py", "rust"];
    assert_eq!(
        !runtime.unpublished.is_empty(),
        marked.contains(&backend),
        "{backend}: unpublished markers"
    );
    let base = workspace();
    let fill = |v: Version| fill_runtime(files, runtime, &v);
    let dev = fill(Version::dev(base, 5));
    let rc = fill(Version::new(base, Stage::Rc(1), 2));
    let release = fill(Version::new(base, Stage::Release, 0));
    let text = |filled: &[(String, String)], path: &str| {
        let (_, text) = (filled.iter()).find(|(p, _)| p == path).unwrap();
        text.clone()
    };
    for marker in runtime.unpublished {
        for filled in [&dev, &rc] {
            assert!(
                text(filled, marker.path).contains(marker.text),
                "{backend}: {}",
                marker.path
            );
        }
        let released = text(&release, marker.path);
        assert!(!released.contains(marker.text), "{backend}: {released}");
        assert_eq!(
            released.lines().count() + marker.text.lines().count(),
            text(&rc, marker.path).lines().count(),
            "{backend}: {released}"
        );
    }
    // Every other file is as in a pre-release, but for the version.
    for ((path, r), (_, p)) in release.iter().zip(&rc) {
        if !runtime.unpublished.iter().any(|m| m.path == path) {
            assert_eq!(r.lines().count(), p.lines().count(), "{backend}: {path}");
        }
    }
}

#[test]
fn runtimes_are_the_crates_own() {
    #[cfg(feature = "cpp")]
    check(
        "cpp",
        temper_be_cpp::runtime::FILES,
        &temper_be_cpp::runtime::VERSION,
    );
    #[cfg(feature = "csharp")]
    check(
        "csharp",
        temper_be_csharp::runtime::FILES,
        &temper_be_csharp::runtime::VERSION,
    );
    #[cfg(feature = "java")]
    check(
        "java",
        temper_be_java::runtime::FILES,
        &temper_be_java::runtime::VERSION,
    );
    #[cfg(feature = "js")]
    check(
        "js",
        temper_be_js::runtime::FILES,
        &temper_be_js::runtime::VERSION,
    );
    #[cfg(feature = "lua")]
    check(
        "lua",
        temper_be_lua::runtime::FILES,
        &temper_be_lua::runtime::VERSION,
    );
    #[cfg(feature = "py")]
    check(
        "py",
        temper_be_py::runtime::FILES,
        &temper_be_py::runtime::VERSION,
    );
    #[cfg(feature = "rust")]
    check(
        "rust",
        temper_be_rust::runtime::FILES,
        &temper_be_rust::runtime::VERSION,
    );
}
