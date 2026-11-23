#![cfg(all(
    feature = "cpp",
    feature = "csharp",
    feature = "java",
    feature = "js",
    feature = "lua",
    feature = "py",
    feature = "rust"
))]
//! `temper build`'s `temper.out` layout, for the functional tests the
//! backends translate (`COVERED`, `CLASSES`, and `NULLABLES`): every file
//! each backend writes, the libraries' and the runtime's, by its path,
//! against `layout.txt`. So a change to the layout is deliberate and shows
//! in the diff; run with `UPDATE_SNAPSHOTS=1` to write the file, and
//! review the diff. Paths only, with `/` on every platform, and versions
//! in file names written as `VERSION`, so the snapshot doesn't change
//! with the build. How and why the layout differs from Kotlin Temper's
//! is in `notes/kotlin-differences.md`.

use std::collections::BTreeSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use temper_be_testkit::{CLASSES, COVERED, NULLABLES, copy_dir};

/// The backends laid out.
const BACKENDS: [&str; 7] = ["cpp", "csharp", "java", "js", "lua", "py", "rust"];

/// Every file under a directory, by its path from there with `/`.
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

/// A file's path with the build's version taken out: the Lua runtime's
/// rockspec is named for it.
fn stable(file: &str) -> String {
    match file.strip_prefix("temper-core/temper-core-") {
        Some(rest) if rest.ends_with("-1.rockspec") => {
            "temper-core/temper-core-VERSION-1.rockspec".to_string()
        }
        _ => file.to_string(),
    }
}

/// The layout's lines: `BACKEND TEST FILE` for a library's files, by the
/// functional test's path, and `BACKEND * FILE` for the runtime's, which
/// every library shares.
fn layout() -> BTreeSet<String> {
    let base = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("layout");
    let _ = fs::remove_dir_all(&base);
    let mut lines = BTreeSet::new();
    for path in COVERED.iter().chain(&CLASSES).chain(&NULLABLES) {
        let test = temper_funtests::test(path).unwrap();
        let library = test.name().to_string();
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
            "{path}: {}",
            String::from_utf8_lossy(&out.stderr)
        );
        for backend in BACKENDS {
            let dir = root.join("temper.out").join(backend);
            let mut found = Vec::new();
            files(&dir, &dir, &mut found);
            for file in found {
                let file = stable(&file);
                lines.insert(if file.starts_with("temper-core/") {
                    format!("{backend} * {file}")
                } else {
                    format!("{backend} {path} {file}")
                });
            }
        }
    }
    lines
}

#[test]
fn temper_out_layout() {
    let file = Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/layout.txt");
    let mut text = String::from(
        "# temper.out's files per backend for the functional tests in\n\
         # tests/layout.rs: BACKEND TEST FILE, or BACKEND * FILE for the\n\
         # runtime. UPDATE_SNAPSHOTS=1 cargo test -p tempest --test layout\n\
         # rewrites it.\n",
    );
    for line in layout() {
        text.push_str(&line);
        text.push('\n');
    }
    if std::env::var_os("UPDATE_SNAPSHOTS").is_some() {
        fs::write(&file, &text).unwrap();
        return;
    }
    let current = fs::read_to_string(&file)
        .unwrap_or_default()
        .replace("\r\n", "\n");
    let old: BTreeSet<&str> = current.lines().collect();
    let new: BTreeSet<&str> = text.lines().collect();
    let mut problems: Vec<String> = (old.difference(&new)).map(|l| format!("- {l}")).collect();
    problems.extend(new.difference(&old).map(|l| format!("+ {l}")));
    assert!(
        current == text,
        "tests/layout.txt differs (UPDATE_SNAPSHOTS=1 accepts the new layout):\n{}",
        problems.join("\n")
    );
}
