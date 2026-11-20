//! Temper's core, as Kotlin Temper declares it (the repository's
//! `lib/core/`): builtin types, functions, and values, with
//! their members and doc comments. Tempest builds core in rather than
//! loading it as a library; typing reads members' signatures from it
//! (`typing::core_members`), and editors show these files for go to
//! definition and read their doc comments for hover. Builtin values,
//! functions, and macros without a Temper body, as `true`, `panic` and
//! `if`, have bodiless `@connected` declarations, which document them
//! and, in core, declare nothing, so core's own code still names the
//! builtins (`Settle::supplied_by_frontend`).

/// The directory editors' paths into core start with, as
/// `-core-/strings.temper`, as [`crate::stdlib::DIR`] is std's.
pub const DIR: &str = "-core-";

/// core's library name, as its `config.temper.md` gives it: a library
/// by this name is core, whatever its text ([`crate::library::Library::in_core`]).
pub const NAME: &str = "temper-core";

/// Declares [`TEXT`] and [`SOURCES`] from core's Temper files, in order.
macro_rules! core_sources {
    ($first:literal $(, $path:literal)* $(,)?) => {
        /// core's Temper text: its sources' texts, in [`SOURCES`]' order,
        /// with a blank line between each and the next, as one text that
        /// typing reads core's declarations from. An offset in it is in
        /// one of the sources ([`source_at`]).
        pub const TEXT: &str = concat!(
            include_str!(concat!("../../../lib/core/", $first)),
            $("\n", include_str!(concat!("../../../lib/core/", $path)),)*
        );

        /// Each source's path from core's root and its text's length.
        const LENGTHS: &[(&str, usize)] = &[
            ($first, include_str!(concat!("../../../lib/core/", $first)).len()),
            $(($path, include_str!(concat!("../../../lib/core/", $path)).len()),)*
        ];
    };
}

// In their paths' order, as a library's files load
// (`crate::project::load_library`).
core_sources!(
    "any.temper",
    "console.temper",
    "generators.temper",
    "language.temper",
    "lists.temper",
    "maps.temper",
    "numbers.temper",
    "strings.temper",
);

/// Each core Temper source, by path from core's root, with its text, in
/// the order [`TEXT`] joins them: each is a slice of [`TEXT`], so the
/// binary holds core's text once.
pub const SOURCES: &[(&str, &str)] = &sources();

const fn sources() -> [(&'static str, &'static str); LENGTHS.len()] {
    let mut out = [("", ""); LENGTHS.len()];
    let (mut i, mut start) = (0, 0);
    while i < LENGTHS.len() {
        let (path, len) = LENGTHS[i];
        let (_, rest) = TEXT.as_bytes().split_at(start);
        let (text, _) = rest.split_at(len);
        out[i] = match std::str::from_utf8(text) {
            Ok(text) => (path, text),
            Err(_) => panic!("core's sources are split mid-character"),
        };
        // Past the text and the blank line after it.
        (i, start) = (i + 1, start + len + 1);
    }
    out
}

/// The source that byte `at` of [`TEXT`] is in, by path and text, and
/// the offset there; None past the end.
pub fn source_at(at: usize) -> Option<(&'static str, &'static str, usize)> {
    let mut start = 0;
    for &(path, text) in SOURCES {
        if at < start + text.len() {
            return Some((path, text, at - start));
        }
        start += text.len() + 1;
    }
    None
}

/// Each core source, by path from core's root, with its text: its
/// config, then its Temper [`SOURCES`].
pub const FILES: &[(&str, &str)] = &files();

const fn files() -> [(&'static str, &'static str); SOURCES.len() + 1] {
    let config = (
        "config.temper.md",
        include_str!("../../../lib/core/config.temper.md"),
    );
    let mut out = [config; SOURCES.len() + 1];
    let mut i = 0;
    while i < SOURCES.len() {
        out[i + 1] = SOURCES[i];
        i += 1;
    }
    out
}

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

    /// [`super::SOURCES`] are in their paths' order, [`super::TEXT`] is
    /// them joined, a blank line between, and each offset in it is in
    /// the source [`super::source_at`] gives.
    #[test]
    fn text_joins_the_sources() {
        assert!(super::SOURCES.windows(2).all(|w| w[0].0 < w[1].0));
        let texts: Vec<&str> = super::SOURCES.iter().map(|(_, t)| *t).collect();
        assert_eq!(super::TEXT, texts.join("\n"));
        let mut start = 0;
        for &(path, text) in super::SOURCES {
            assert_eq!(super::source_at(start), Some((path, text, 0)));
            let last = start + text.len() - 1;
            assert_eq!(super::source_at(last), Some((path, text, text.len() - 1)));
            start += text.len() + 1;
        }
        assert_eq!(super::source_at(super::TEXT.len()), None);
    }

    /// [`super::NAME`] is the name core's config gives it.
    #[test]
    fn name_is_cores_config_name() {
        let config = super::FILES[0].1;
        let line = format!("export let name = \"{}\";", super::NAME);
        assert!(config.lines().any(|l| l.trim() == line), "{config}");
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
