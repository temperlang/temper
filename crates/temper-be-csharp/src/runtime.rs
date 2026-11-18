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
    ("Async.cs", include_str!("../runtime/temper-core/Async.cs")),
    ("Core.cs", include_str!("../runtime/temper-core/Core.cs")),
    (
        "Float64.cs",
        include_str!("../runtime/temper-core/Float64.cs"),
    ),
    (
        "Generator.cs",
        include_str!("../runtime/temper-core/Generator.cs"),
    ),
    (
        "Listed.cs",
        include_str!("../runtime/temper-core/Listed.cs"),
    ),
    (
        "Logging.cs",
        include_str!("../runtime/temper-core/Logging.cs"),
    ),
    (
        "Optional.cs",
        include_str!("../runtime/temper-core/Optional.cs"),
    ),
    (
        "OrderedDictionary.cs",
        include_str!("../runtime/temper-core/OrderedDictionary.cs"),
    ),
    (
        "StringUtil.cs",
        include_str!("../runtime/temper-core/StringUtil.cs"),
    ),
    (
        "TemperLang.Core.csproj",
        include_str!("../runtime/temper-core/TemperLang.Core.csproj"),
    ),
];

/// Where the runtime names its version, which `temper build` fills with
/// the build's, in SemVer's spelling.
///
/// Until a release, it isn't packable, so `dotnet pack` makes no package
/// of it.
pub const VERSION: RuntimeVersion = RuntimeVersion {
    spots: &[VersionSpot {
        path: "TemperLang.Core.csproj",
        text: "<Version>0.0.0</Version>",
        spelling: Spelling::SemVer,
        rename: None,
    }],
    unpublished: &[Unpublished {
        path: "TemperLang.Core.csproj",
        text: "    <!-- temper build drops this and the next line only for a release. -->\n    <IsPackable>false</IsPackable>\n",
    }],
};
