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
        "README-temper-core.md",
        include_str!("../runtime/temper-core/README-temper-core.md"),
    ),
    (
        "pyproject.toml",
        include_str!("../runtime/temper-core/pyproject.toml"),
    ),
    (
        "temper_core/__init__.py",
        include_str!("../runtime/temper-core/temper_core/__init__.py"),
    ),
    (
        "temper_core/py.typed",
        include_str!("../runtime/temper-core/temper_core/py.typed"),
    ),
    (
        "temper_core/regex.py",
        include_str!("../runtime/temper-core/temper_core/regex.py"),
    ),
    (
        "temper_core/testing.py",
        include_str!("../runtime/temper-core/temper_core/testing.py"),
    ),
];

/// Where the runtime names its version, which `temper build` fills with
/// the build's, in PEP 440's spelling.
///
/// Until a release, it has a `Private ::` classifier, which PyPI rejects.
pub const VERSION: RuntimeVersion = RuntimeVersion {
    spots: &[VersionSpot {
        path: "pyproject.toml",
        text: "version = \"0.0.0\"",
        spelling: Spelling::Python,
        rename: None,
    }],
    unpublished: &[Unpublished {
        path: "pyproject.toml",
        text: "# `temper build` drops these lines only for a release: PyPI rejects\n# any upload with a `Private ::` classifier.\nclassifiers = [\"Private :: Do Not Upload\"]\n",
    }],
};
