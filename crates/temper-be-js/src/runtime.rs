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
    ("async.js", include_str!("../runtime/temper-core/async.js")),
    (
        "bitvector.js",
        include_str!("../runtime/temper-core/bitvector.js"),
    ),
    (
        "check-type.js",
        include_str!("../runtime/temper-core/check-type.js"),
    ),
    ("core.js", include_str!("../runtime/temper-core/core.js")),
    ("date.js", include_str!("../runtime/temper-core/date.js")),
    ("deque.js", include_str!("../runtime/temper-core/deque.js")),
    ("float.js", include_str!("../runtime/temper-core/float.js")),
    ("index.js", include_str!("../runtime/temper-core/index.js")),
    ("int.js", include_str!("../runtime/temper-core/int.js")),
    (
        "interface.js",
        include_str!("../runtime/temper-core/interface.js"),
    ),
    (
        "listed.js",
        include_str!("../runtime/temper-core/listed.js"),
    ),
    (
        "mapped.js",
        include_str!("../runtime/temper-core/mapped.js"),
    ),
    ("net.js", include_str!("../runtime/temper-core/net.js")),
    (
        "package.json",
        include_str!("../runtime/temper-core/package.json"),
    ),
    ("pair.js", include_str!("../runtime/temper-core/pair.js")),
    ("regex.js", include_str!("../runtime/temper-core/regex.js")),
    (
        "string.js",
        include_str!("../runtime/temper-core/string.js"),
    ),
    (
        "tsconfig.json",
        include_str!("../runtime/temper-core/tsconfig.json"),
    ),
];

/// Where the runtime names its version, which `temper build` fills with
/// the build's, in SemVer's spelling.
///
/// Until a release, it's private, which npm refuses to publish.
pub const VERSION: RuntimeVersion = RuntimeVersion {
    spots: &[VersionSpot {
        path: "package.json",
        text: "\"version\": \"0.0.0\"",
        spelling: Spelling::SemVer,
        rename: None,
    }],
    unpublished: &[Unpublished {
        path: "package.json",
        text: "  \"private\": true,\n",
    }],
};
