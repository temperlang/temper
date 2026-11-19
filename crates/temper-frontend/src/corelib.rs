//! Temper's core, as Kotlin Temper declares it (the repository's
//! `lib/core/`): builtin types, functions, and values, with
//! their members and doc comments. Tempest builds core in rather than
//! loading it as a library; typing reads members' signatures from it
//! (`typing::core_members`), and editors show these files for go to
//! definition and read their doc comments for hover.

/// The directory editors' paths into core start with, as
/// `-core-/core.temper`, as [`crate::stdlib::DIR`] is std's.
pub const DIR: &str = "-core-";

/// core's main file, by path from core's root, as [`FILES`] names it.
pub const MAIN: &str = "core.temper";

/// core's main file's text.
pub const TEXT: &str = include_str!("../../../lib/core/core.temper");

/// Each core source, by path from core's root, with its text.
pub const FILES: &[(&str, &str)] = &[
    (
        "config.temper.md",
        include_str!("../../../lib/core/config.temper.md"),
    ),
    (MAIN, TEXT),
];

#[cfg(test)]
mod tests {
    #[test]
    fn files_lists_every_core_file() {
        let dir = concat!(env!("CARGO_MANIFEST_DIR"), "/../../lib/core");
        let mut found: Vec<String> = std::fs::read_dir(dir)
            .unwrap()
            .map(|e| e.unwrap().file_name().to_string_lossy().into_owned())
            .collect();
        found.sort();
        let mut listed: Vec<&str> = super::FILES.iter().map(|(p, _)| *p).collect();
        listed.sort();
        assert_eq!(found, listed);
    }

    /// Core and std name builtin types as any library does, `Int`, not
    /// `builtins.Int`: core's declarations of them are the builtins.
    #[test]
    fn core_and_std_use_no_builtins_qualifiers() {
        let files = (super::FILES.iter()).chain(crate::stdlib::FILES);
        let mut found = Vec::new();
        for (path, text) in files {
            for (i, line) in text.lines().enumerate() {
                let qualified = (line.match_indices("builtins.")).any(|(at, _)| {
                    line[at + 9..].starts_with(|c: char| c.is_alphanumeric() || c == '_')
                });
                if qualified {
                    found.push(format!("{path}:{}: {}", i + 1, line.trim()));
                }
            }
        }
        assert_eq!(found, Vec::<String>::new());
    }
}
