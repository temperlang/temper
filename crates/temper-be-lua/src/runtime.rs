//! This crate's runtime, `runtime/temper-core`, built in, for
//! `temper.out` beside generated libraries, as Kotlin Temper copies it
//! there. A test in `temper-driver` checks that [`FILES`] lists every file of
//! it that Kotlin copies: not its
//! dotfiles, nor its own tests and tools (JS's `package-lock.json`,
//! Python's `mypyc/`, and Java's `src/test/`). [`VERSION`] says where
//! its files name its version, which `temper build` fills in (and
//! Lua's rockspec is renamed for it, as Kotlin names it); the sources say
//! `0.0.0`, so the runtime still builds and tests where it is.

use temper_tmpl::version::{RuntimeVersion, Spelling, VersionSpot};

/// Each file, by path from the runtime's directory, with its text.
pub const FILES: &[(&str, &str)] = &[
    ("init.lua", include_str!("../runtime/temper-core/init.lua")),
    (
        "intnew.lua",
        include_str!("../runtime/temper-core/intnew.lua"),
    ),
    (
        "intold.lua",
        include_str!("../runtime/temper-core/intold.lua"),
    ),
    (
        "powersort/README.md",
        include_str!("../runtime/temper-core/powersort/README.md"),
    ),
    (
        "powersort/init-internal.lua",
        include_str!("../runtime/temper-core/powersort/init-internal.lua"),
    ),
    (
        "powersort/init.lua",
        include_str!("../runtime/temper-core/powersort/init.lua"),
    ),
    (
        "regex/runtime.lua",
        include_str!("../runtime/temper-core/regex/runtime.lua"),
    ),
    (
        "temper-core-dev-1.rockspec",
        include_str!("../runtime/temper-core/temper-core-dev-1.rockspec"),
    ),
];

/// Where the runtime names its version, which `temper build` fills with
/// the build's, in LuaRocks' spelling.
///
/// Nothing keeps it from being published: LuaRocks has no marker that
/// refuses an upload.
pub const VERSION: RuntimeVersion = RuntimeVersion {
    spots: &[VersionSpot {
        path: "temper-core-dev-1.rockspec",
        text: "version = \"0.0.0-1\"",
        spelling: Spelling::LuaRocks,
        rename: Some("temper-core-{}-1.rockspec"),
    }],
    unpublished: &[],
};

/// LuaUnit, in `runtime/luaunit` beside the runtime, which libraries' tests use, and
/// which Kotlin copies beside the libraries to run them, as `luaunit`.
pub const LUAUNIT: &[(&str, &str)] = &[
    ("init.lua", include_str!("../runtime/luaunit/init.lua")),
    (
        "LICENSE.txt",
        include_str!("../runtime/luaunit/LICENSE.txt"),
    ),
];
