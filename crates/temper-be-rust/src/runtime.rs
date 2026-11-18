//! This crate's runtime, `runtime/temper-core`, built in, for
//! `temper.out` beside generated libraries, as Kotlin Temper copies it
//! there. A test in `temper-driver` checks that [`FILES`] lists every file of
//! it that Kotlin copies: not its
//! dotfiles, nor its own tests and tools (JS's `package-lock.json`,
//! Python's `mypyc/`, and Java's `src/test/`). [`VERSION`] says where
//! its files name its version, which `temper build` fills in (and
//! Lua's rockspec is renamed for it, as Kotlin names it); the sources say
//! `0.0.0`, so the runtime still builds and tests where it is.

use temper_tmpl::version::{RuntimeVersion, Spelling, Unpublished, VersionSpot};

/// Each file, by path from the runtime's directory, with its text.
pub const FILES: &[(&str, &str)] = &[
    (
        "Cargo.lock",
        include_str!("../runtime/temper-core/Cargo.lock"),
    ),
    (
        "Cargo.toml",
        include_str!("../runtime/temper-core/Cargo.toml"),
    ),
    (
        "src/float64.rs",
        include_str!("../runtime/temper-core/src/float64.rs"),
    ),
    (
        "src/generator.rs",
        include_str!("../runtime/temper-core/src/generator.rs"),
    ),
    (
        "src/lib.rs",
        include_str!("../runtime/temper-core/src/lib.rs"),
    ),
    (
        "src/listed.rs",
        include_str!("../runtime/temper-core/src/listed.rs"),
    ),
    (
        "src/mapped.rs",
        include_str!("../runtime/temper-core/src/mapped.rs"),
    ),
    (
        "src/promise.rs",
        include_str!("../runtime/temper-core/src/promise.rs"),
    ),
    (
        "src/string.rs",
        include_str!("../runtime/temper-core/src/string.rs"),
    ),
];

/// Where the runtime names its version, which `temper build` fills with
/// the build's, in SemVer's spelling.
///
/// Until a release, it says `publish = false`, so Cargo refuses to
/// publish it.
pub const VERSION: RuntimeVersion = RuntimeVersion {
    spots: &[
        VersionSpot {
            path: "Cargo.toml",
            text: "version = \"0.0.0\"",
            spelling: Spelling::SemVer,
            rename: None,
        },
        VersionSpot {
            path: "Cargo.lock",
            text: "name = \"temper-core\"\nversion = \"0.0.0\"",
            spelling: Spelling::SemVer,
            rename: None,
        },
    ],
    unpublished: &[Unpublished {
        path: "Cargo.toml",
        text: "# `temper build` drops this and the next line only for a release.\npublish = false\n",
    }],
};
