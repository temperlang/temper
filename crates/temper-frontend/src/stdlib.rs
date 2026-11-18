//! Temper's std library, built in, so that the binaries and the wasm build
//! import `std/…` modules with nothing shipped beside them. The sources
//! are the repository's `lib/std/`; a test checks that
//! [`FILES`] lists every file there.
//!
//! Built-in std files' paths start with [`DIR`], as
//! `-std-/json/json.temper.md`, which no library's own files are likely
//! to share. [`Sources`](crate::sources::Sources) serves them there, or
//! mounts a directory there instead, for testing against another copy.

/// The directory that built-in std modules' paths start with.
pub const DIR: &str = "-std-";

/// Each std source, by path from std's root, with its text.
pub const FILES: &[(&str, &str)] = &[
    (
        "config.temper.md",
        include_str!("../../../lib/std/config.temper.md"),
    ),
    (
        "json/json.temper.md",
        include_str!("../../../lib/std/json/json.temper.md"),
    ),
    (
        "net/net.temper.md",
        include_str!("../../../lib/std/net/net.temper.md"),
    ),
    (
        "regex/regex.temper.md",
        include_str!("../../../lib/std/regex/regex.temper.md"),
    ),
    (
        "temporal/temporal.temper.md",
        include_str!("../../../lib/std/temporal/temporal.temper.md"),
    ),
    (
        "testing/testing.temper.md",
        include_str!("../../../lib/std/testing/testing.temper.md"),
    ),
];

/// std's modules that build as a library of its own, in the order they
/// run (notes/tmpl/modules.md, "std as a library"); `temporal`'s `Date` is
/// a connected class, which backends may connect to a type of their own,
/// as `net`'s `NetResponse` is a connected interface; its `sendRequest`, a
/// connected function with no Temper body, is each backend's own.
pub const MODULES: [&str; 5] = ["testing", "regex", "json", "temporal", "net"];

/// std as a library of its own, its modules named in `modules` (as
/// [`MODULES`] names them) loaded from `sources`, with what they import,
/// ready to settle and translate as the library `std`.
pub fn library(sources: &crate::sources::Sources, modules: &[&str]) -> crate::library::Library {
    let mut library = crate::library::Library::new();
    for module in modules {
        let path = format!("{DIR}/{module}/{module}.temper.md");
        if let Ok(text) = sources.read(&path) {
            library.add_file(&path, &text, crate::project::source_kind(&path));
        }
    }
    crate::project::load_imports(sources, &mut library);
    library
}

/// std's [`MODULES`] as one tmpl program, the library `std`, at the
/// version its config gives; or what isn't covered yet. Its `@connected`
/// members keep their Temper bodies: each backend connects those its own
/// table has and translates the rest (notes/tmpl/open-questions.md, 48).
pub fn program(
    sources: &crate::sources::Sources,
) -> Result<temper_tmpl::Program, crate::to_tmpl::NotYet> {
    program_of(sources, &MODULES)
}

/// [`program`] with only some of std's modules (and what they import),
/// for a backend that translates only those so far; its declarations'
/// names are those [`program`]'s naming gives, since naming is by module
/// and class.
pub fn program_of(
    sources: &crate::sources::Sources,
    modules: &[&str],
) -> Result<temper_tmpl::Program, crate::to_tmpl::NotYet> {
    let settled = crate::settle::settle(library(sources, modules));
    let mut program = crate::to_tmpl::to_tmpl(&settled, "std")?;
    program.library.version = crate::to_tmpl::std_version();
    // Its config's metadata, for its package files, as Kotlin's.
    if let Ok(text) = sources.read(&format!("{DIR}/config.temper.md")) {
        let config = crate::project::Config::read(&text, DIR);
        let metadata = temper_tmpl::Metadata {
            exports: config.exports,
        };
        program.metadata = vec![("std".to_string(), metadata)];
    }
    Ok(program)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::Path;

    /// Every file under `std`, by path from there.
    fn files_under(dir: &Path, prefix: &str, paths: &mut Vec<String>) {
        for entry in std::fs::read_dir(dir).unwrap() {
            let entry = entry.unwrap();
            let name = entry.file_name().into_string().unwrap();
            if entry.file_type().unwrap().is_dir() {
                files_under(&entry.path(), &format!("{prefix}{name}/"), paths);
            } else {
                paths.push(format!("{prefix}{name}"));
            }
        }
    }

    #[test]
    fn lists_every_std_file() {
        let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../lib/std");
        let mut paths = Vec::new();
        files_under(&root, "", &mut paths);
        paths.sort();
        let listed: Vec<&str> = FILES.iter().map(|&(path, _)| path).collect();
        assert_eq!(paths, listed);
    }
}
