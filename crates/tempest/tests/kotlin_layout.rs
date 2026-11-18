#![cfg(all(
    feature = "cpp",
    feature = "csharp",
    feature = "java",
    feature = "js",
    feature = "lua",
    feature = "py",
    feature = "rust"
))]
//! `temper build`'s `temper.out` against Kotlin Temper's, for the
//! functional tests that tempest translates (`COVERED`, `CLASSES`, and
//! `NULLABLES`): every file's path, each backend's runtime beside the
//! libraries, and the package files' names and versions, as
//! `kotlin-out.txt` records Kotlin's (`compat/kotlin_out.py` writes it
//! from Kotlin's output). What differs on
//! purpose is in [`ALLOWED`], with why.

use std::collections::BTreeSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use temper_be_testkit::{CLASSES, COVERED, NULLABLES, copy_dir};

/// The backends compared, all of Kotlin's that tempest has.
const BACKENDS: [&str; 7] = ["cpp", "csharp", "java", "js", "lua", "py", "rust"];

/// Differences on purpose: a backend, a file's end, which side has it,
/// and why.
const ALLOWED: &[(&str, &str, Side, &str)] = &[
    ("py", ".map", Side::Kotlin, "source maps: only JS's so far"),
    (
        "rust",
        ".map",
        Side::Kotlin,
        "source maps: only JS's so far",
    ),
    (
        "java",
        ".map",
        Side::Kotlin,
        "source maps: only JS's so far",
    ),
    (
        "csharp",
        ".map",
        Side::Kotlin,
        "source maps: only JS's so far",
    ),
    ("cpp", ".map", Side::Kotlin, "source maps: only JS's so far"),
    ("lua", ".map", Side::Kotlin, "source maps: only JS's so far"),
    (
        "js",
        ".js.map",
        Side::Kotlin,
        "source maps: the module's (`.internal.js.map`), not yet the re-exports'",
    ),
    (
        "lua",
        ".zip",
        Side::Kotlin,
        "no zip for LuaRocks, whose rockspec names it relative (kotlin-differences.md)",
    ),
    (
        "cpp",
        "/CMakeLists.txt",
        Side::Tempest,
        "a CMake project per library, which Kotlin doesn't write (decided 2026-10-30)",
    ),
    (
        "cpp",
        "temper-core/net.hpp",
        Side::Tempest,
        "std net's pluggable client in the runtime (patch 0019); Kotlin's C++ has no net",
    ),
    (
        "csharp",
        "/IB3_Deep.cs",
        Side::Tempest,
        "an interface nothing uses, which Kotlin leaves out and tempest keeps",
    ),
    (
        "java",
        "/B3Deep.java",
        Side::Tempest,
        "an interface nothing uses, which Kotlin leaves out and tempest keeps",
    ),
    (
        "csharp",
        "static-properties-scope/src/Simple.cs",
        Side::Tempest,
        "a class whose statics Kotlin inlines and then leaves out; tempest keeps it",
    ),
    (
        "java",
        "static_properties_scope/Simple.java",
        Side::Tempest,
        "a class whose statics Kotlin inlines and then leaves out; tempest keeps it",
    ),
    (
        "py",
        "types/types/__init__.py",
        Side::Kotlin,
        "a library named like a Python standard module gets a `_` (`types_`), so it doesn't hide it (kotlin-differences.md)",
    ),
    (
        "py",
        "types/types/exporter.py",
        Side::Kotlin,
        "the same, `types_`",
    ),
    (
        "py",
        "types/types/types.py",
        Side::Kotlin,
        "the same, `types_`",
    ),
    (
        "py",
        "types/types_/__init__.py",
        Side::Tempest,
        "the same, `types_`",
    ),
    (
        "py",
        "types/types_/exporter.py",
        Side::Tempest,
        "the same, `types_`",
    ),
    (
        "py",
        "types/types_/types_.py",
        Side::Tempest,
        "the same, `types_`",
    ),
];

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
enum Side {
    Kotlin,
    Tempest,
}

/// Whether a line of the record, only on one side, is allowed to be.
fn allowed(line: &str, side: Side) -> bool {
    let mut words = line.splitn(4, ' ');
    let (_, backend, kind, rest) = (
        words.next(),
        words.next().unwrap_or(""),
        words.next().unwrap_or(""),
        words.next().unwrap_or(""),
    );
    kind == "file"
        && (ALLOWED.iter()).any(|&(b, end, s, _)| b == backend && s == side && rest.ends_with(end))
}

/// Every file under a directory, by its path from there, sorted.
fn files(root: &Path, dir: &Path, out: &mut Vec<String>) {
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };
    for entry in entries {
        let path = entry.unwrap().path();
        if path.is_dir() {
            files(root, &path, out);
        } else {
            let rel = path.strip_prefix(root).unwrap();
            out.push(rel.to_string_lossy().replace('\\', "/"));
        }
    }
}

/// The text after `prefix` on the first line that starts with it, up to
/// `end`.
fn field<'a>(text: &'a str, prefix: &str, end: char) -> Option<&'a str> {
    let line = text.lines().find(|l| l.starts_with(prefix))?;
    let rest = &line[prefix.len()..];
    Some(rest.split(end).next().unwrap_or(rest))
}

/// A package file's fields, as `compat/kotlin_out.py` reads them.
fn meta(backend: &str, library: &str, dir: &Path) -> Vec<String> {
    let read = |path: PathBuf| fs::read_to_string(path).unwrap_or_default();
    let find = |sub: &str, suffix: &str| {
        let mut found: Vec<PathBuf> = fs::read_dir(dir.join(library).join(sub))
            .map(|e| e.map(|e| e.unwrap().path()).collect())
            .unwrap_or_default();
        found.sort();
        found
            .into_iter()
            .find(|p| p.to_string_lossy().ends_with(suffix))
    };
    let fields: Vec<(&str, Option<String>)> = match backend {
        "py" | "rust" => {
            let file = if backend == "py" {
                "pyproject.toml"
            } else {
                "Cargo.toml"
            };
            let text = read(dir.join(library).join(file));
            vec![
                ("name", field(&text, "name = \"", '"').map(str::to_string)),
                (
                    "version",
                    field(&text, "version = \"", '"').map(str::to_string),
                ),
            ]
        }
        "js" => {
            let text = read(dir.join(library).join("package.json"));
            vec![
                (
                    "name",
                    field(&text, "    \"name\": \"", '"').map(str::to_string),
                ),
                (
                    "version",
                    field(&text, "    \"version\": \"", '"').map(str::to_string),
                ),
            ]
        }
        "java" => {
            let text = read(dir.join(library).join("pom.xml"));
            vec![
                (
                    "group",
                    field(&text, "  <groupId>", '<').map(str::to_string),
                ),
                (
                    "name",
                    field(&text, "  <artifactId>", '<').map(str::to_string),
                ),
                (
                    "version",
                    field(&text, "  <version>", '<').map(str::to_string),
                ),
            ]
        }
        "csharp" => {
            let text = find("src", ".csproj").map(read).unwrap_or_default();
            let inner = |tag: &str| {
                let open = format!("<{tag}>");
                let at = text.find(&open)? + open.len();
                Some(text[at..].split('<').next()?.to_string())
            };
            vec![
                ("name", inner("RootNamespace")),
                ("version", inner("Version")),
            ]
        }
        "lua" => {
            let text = find("", ".rockspec").map(read).unwrap_or_default();
            let quoted = |key: &str| {
                let line = text.lines().find(|l| l.starts_with(key))?;
                let rest = line[key.len()..].trim_start_matches([' ', '=']);
                Some(rest.trim_matches(['\'', '"']).to_string())
            };
            vec![("name", quoted("package")), ("version", quoted("version"))]
        }
        _ => Vec::new(),
    };
    (fields.into_iter())
        .filter_map(|(f, v)| v.map(|v| format!("{library} {backend} meta {f} {v}")))
        .collect()
}

#[test]
fn temper_out_is_kotlins() {
    let record = Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/kotlin-out.txt");
    let kotlin: BTreeSet<String> = (fs::read_to_string(record).unwrap().lines())
        .filter(|l| !l.starts_with('#') && !l.is_empty())
        .map(str::to_string)
        .collect();
    let base = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("kotlin-layout");
    let _ = fs::remove_dir_all(&base);
    let mut tempest = BTreeSet::new();
    for path in COVERED.iter().chain(&CLASSES).chain(&NULLABLES) {
        let test = temper_funtests::test(path).unwrap();
        let library = test.name().to_string();
        // By path, as two tests may share a name (`types/int/basics` and
        // `types/float/basics`), and lay out the same.
        let root = base.join(path.replace('/', "-"));
        fs::create_dir_all(&root).unwrap();
        fs::write(
            root.join("config.temper.md"),
            format!("# {library}\n\n    export let name = \"{library}\";\n"),
        )
        .unwrap();
        for source in test.sources() {
            let name = Path::new(&source.path).file_name().unwrap();
            fs::write(root.join(name), &source.text).unwrap();
        }
        // Modules in subdirectories, as `imports/types`' `exporter`.
        for entry in fs::read_dir(&test.dir).unwrap().flatten() {
            if entry.path().is_dir() && entry.file_name() != "temper.out" {
                copy_dir(
                    &entry.path(),
                    &root.join(entry.file_name()),
                    &["temper.out"],
                );
            }
        }
        let mut command = Command::new(env!("CARGO_BIN_EXE_temper"));
        command.arg("build").arg("-w").arg(&root);
        for backend in BACKENDS {
            command.args(["-b", backend]);
        }
        let out = command.output().unwrap();
        assert!(
            out.status.success(),
            "{library}: {}",
            String::from_utf8_lossy(&out.stderr)
        );
        for backend in BACKENDS {
            let dir = root.join("temper.out").join(backend);
            let mut found = Vec::new();
            files(&dir, &dir, &mut found);
            for file in found {
                // The rockspec is named for the runtime's version, which
                // is tempest's own (the build's), where Kotlin's is 0.6.0.
                let file = match file.strip_prefix("temper-core/temper-core-") {
                    Some(rest) if rest.ends_with("-1.rockspec") => {
                        "temper-core/temper-core-0.6.0-1.rockspec".to_string()
                    }
                    _ => file,
                };
                tempest.insert(if file.starts_with("temper-core/") {
                    format!("* {backend} file {file}")
                } else {
                    format!("{library} {backend} file {file}")
                });
            }
            tempest.extend(meta(backend, &library, &dir));
        }
    }
    let mut problems = Vec::new();
    for line in kotlin.difference(&tempest) {
        if !allowed(line, Side::Kotlin) {
            problems.push(format!("only Kotlin's: {line}"));
        }
    }
    for line in tempest.difference(&kotlin) {
        if !allowed(line, Side::Tempest) {
            problems.push(format!("only tempest's: {line}"));
        }
    }
    assert!(problems.is_empty(), "{}", problems.join("\n"));
}

/// Every allowance is used, so none outlives its reason.
#[test]
fn allowances_are_used() {
    let record = Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/kotlin-out.txt");
    let text = fs::read_to_string(record).unwrap();
    for &(backend, end, side, why) in ALLOWED {
        if side == Side::Kotlin {
            let used = text.lines().any(|l| {
                l.split(' ').nth(1) == Some(backend) && l.contains(" file ") && l.ends_with(end)
            });
            assert!(used, "{backend} {end}: {why}");
        }
    }
}
