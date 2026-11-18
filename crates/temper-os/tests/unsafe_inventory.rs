//! Where the repository's unsafe Rust is, and how much: every file
//! with the `unsafe` keyword (a block, `unsafe fn`, `impl`, `trait`,
//! `extern`, or an attribute such as `#[unsafe(no_mangle)]`) must be in
//! [`INVENTORY`], with that many. Adding or removing one fails this
//! test until the inventory says so, in the same change, so it shows in
//! review. Lints do the rest: the workspace forbids unsafe code but in
//! `temper-os`, where clippy wants one unsafe operation per block, each
//! with a safety comment, and every crate must take the
//! workspace's lints but `temper-os` and `temper-wasm`.
//!
//! The scan is plain text, so a comment that reads like unsafe code
//! counts too; reword it.

use std::fs;
use std::path::{Path, PathBuf};

/// Each file with unsafe code, relative to the repository's root, how
/// many times it has the keyword, and why.
const INVENTORY: &[(&str, usize, &str)] = &[
    ("crates/temper-os/src/console.rs", 6, "Windows' console"),
    (
        "crates/temper-os/src/fs.rs",
        3,
        "geteuid, and openat's descriptor",
    ),
    (
        "crates/temper-os/src/host.rs",
        3,
        "the web page's functions the wasm build imports: a log line, the clock",
    ),
    ("crates/temper-os/src/interrupt.rs", 4, "Ctrl-C handlers"),
    (
        "crates/temper-os/src/process.rs",
        11,
        "process groups, their signal handler, and children's setup",
    ),
    (
        "crates/temper-wasm/src/lib.rs",
        18,
        "#[unsafe(no_mangle)] on the playground's exports, and nothing else",
    ),
    (
        "crates/temper-wasm/src/repl.rs",
        5,
        "#[unsafe(no_mangle)] on the REPL's exports, and nothing else",
    ),
    (
        "crates/temper-be-rust/runtime/temper-core/src/lib.rs",
        1,
        "The Rust backend's runtime, built apart from the workspace: cast's read of a value whose \
         TypeId is the target's",
    ),
];

/// Crates with lints of their own, rather than the workspace's.
const OWN_LINTS: &[&str] = &["temper-os", "temper-wasm"];

/// Directories the scan skips: build output, packages, and checkouts.
const SKIPPED: &[&str] = &["target", "node_modules", "library"];

#[test]
fn unsafe_code_is_where_the_inventory_says() {
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    let mut files = Vec::new();
    rust_files(&root, &mut files);
    let mut found = Vec::new();
    for file in &files {
        let name = file.strip_prefix(&root).unwrap();
        let name = name.to_string_lossy().replace('\\', "/");
        // This file, whose samples aren't code.
        if name == "crates/temper-os/tests/unsafe_inventory.rs" {
            continue;
        }
        let count = count_unsafe(&fs::read_to_string(file).unwrap());
        if count > 0 {
            found.push((name, count));
        }
    }
    found.sort();
    let mut expected: Vec<(String, usize)> = (INVENTORY.iter())
        .map(|(name, count, _)| (name.to_string(), *count))
        .collect();
    expected.sort();
    let show = |list: &[(String, usize)]| {
        let lines = list
            .iter()
            .map(|(name, count)| format!("  {name}: {count}"));
        lines.collect::<Vec<_>>().join("\n")
    };
    assert!(
        found == expected,
        "unsafe code has changed; update INVENTORY in {} (and give each \
         new block a `// SAFETY:` comment, in temper-os only)\nfound:\n{}\nexpected:\n{}",
        file!(),
        show(&found),
        show(&expected),
    );
}

#[test]
fn every_other_crate_forbids_unsafe_code() {
    let crates = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    for entry in fs::read_dir(&crates).unwrap() {
        let dir = entry.unwrap().path();
        let name = dir.file_name().unwrap().to_string_lossy().to_string();
        let Ok(manifest) = fs::read_to_string(dir.join("Cargo.toml")) else {
            continue;
        };
        let takes_workspace = manifest.contains("\n[lints]\nworkspace = true\n");
        assert_eq!(
            takes_workspace,
            !OWN_LINTS.contains(&name.as_str()),
            "{name}'s Cargo.toml: every crate takes the workspace's lints \
             ([lints] workspace = true) but {OWN_LINTS:?}",
        );
    }
    let workspace = fs::read_to_string(crates.join("../Cargo.toml")).unwrap();
    assert!(workspace.contains("[workspace.lints.rust]\nunsafe_code = \"forbid\"\n"));
}

/// The scan's rule, on a sample.
#[test]
fn counts_the_keyword_not_the_word() {
    let text = r#"
        unsafe { a() };  unsafe{b()}
        #[unsafe(no_mangle)]
        pub unsafe fn f() {}
        unsafe impl Send for T {}
        unsafe extern "C" {}
        let names = ["unsafe", "unsafe_code"];
        // an unsafe call
        fn not_unsafe_x() {}
    "#;
    assert_eq!(count_unsafe(text), 6);
}

/// Every `.rs` file under `dir`, but in hidden and skipped directories.
fn rust_files(dir: &Path, out: &mut Vec<PathBuf>) {
    for entry in fs::read_dir(dir).unwrap() {
        let path = entry.unwrap().path();
        let name = path.file_name().unwrap().to_string_lossy();
        if path.is_dir() {
            if !name.starts_with('.') && !SKIPPED.contains(&name.as_ref()) {
                rust_files(&path, out);
            }
        } else if name.ends_with(".rs") {
            out.push(path);
        }
    }
}

/// How many times `unsafe` is used as the keyword: the word, then a
/// brace, a parenthesis, or `fn`, `impl`, `trait`, or `extern`.
fn count_unsafe(text: &str) -> usize {
    let mut count = 0;
    let mut rest = text;
    while let Some(at) = rest.find("unsafe") {
        let word = !rest[..at].ends_with(ident);
        rest = &rest[at + "unsafe".len()..];
        let after = rest.trim_start();
        if word && (after.starts_with(['{', '(']) || keyword_follows(after)) {
            count += 1;
        }
    }
    count
}

fn keyword_follows(text: &str) -> bool {
    ["fn", "impl", "trait", "extern"]
        .iter()
        .any(|word| (text.strip_prefix(word)).is_some_and(|rest| !rest.starts_with(ident)))
}

fn ident(c: char) -> bool {
    c.is_alphanumeric() || c == '_'
}
