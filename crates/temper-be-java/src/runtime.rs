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
    ("pom.xml", include_str!("../runtime/temper-core/pom.xml")),
    (
        "src/main/java/temper/core/Core.java",
        include_str!("../runtime/temper-core/src/main/java/temper/core/Core.java"),
    ),
    (
        "src/main/java/temper/core/Generator.java",
        include_str!("../runtime/temper-core/src/main/java/temper/core/Generator.java"),
    ),
    (
        "src/main/java/temper/core/NonNull.java",
        include_str!("../runtime/temper-core/src/main/java/temper/core/NonNull.java"),
    ),
    (
        "src/main/java/temper/core/Nullable.java",
        include_str!("../runtime/temper-core/src/main/java/temper/core/Nullable.java"),
    ),
    (
        "src/main/java/temper/core/Stub.java",
        include_str!("../runtime/temper-core/src/main/java/temper/core/Stub.java"),
    ),
    (
        "src/main/java/temper/core/Util.java",
        include_str!("../runtime/temper-core/src/main/java/temper/core/Util.java"),
    ),
    (
        "src/main/java/temper/core/net/Core.java",
        include_str!("../runtime/temper-core/src/main/java/temper/core/net/Core.java"),
    ),
    (
        "src/main/java/temper/core/net/NetResponse.java",
        include_str!("../runtime/temper-core/src/main/java/temper/core/net/NetResponse.java"),
    ),
];

/// Where the runtime names its version, which `temper build` fills with
/// the build's, in SemVer's spelling.
///
/// Until a release, it skips publishing, as the Central Portal's plugin
/// and `maven-deploy-plugin` read it.
pub const VERSION: RuntimeVersion = RuntimeVersion {
    spots: &[VersionSpot {
        path: "pom.xml",
        text: "<version>0.0.0</version>",
        spelling: Spelling::SemVer,
        rename: None,
    }],
    unpublished: &[Unpublished {
        path: "pom.xml",
        text: "    <!-- temper build drops this and the next two lines only for a release. -->\n    <skipPublishing>true</skipPublishing>\n    <maven.deploy.skip>true</maven.deploy.skip>\n",
    }],
};
