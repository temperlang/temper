//! What tools need of a backend, in one description each backend crate
//! exports as `BACKEND`, so a tool keeps one list of backends and loops
//! over it. tmpl never lists backends; `temper-driver`'s registry does.

use crate::Program;
use crate::naming::NamingRules;
use std::path::Path;

/// A backend: its name, runtime, naming, translation, and how its
/// language runs tests.
pub struct Backend {
    /// Its name on the command line and in `temper.out`, as Kotlin's.
    pub name: &'static str,
    /// Its line comment, which marks each file printed together.
    pub comment: &'static str,
    /// Its runtime's files, as `temper.out` has them beside the
    /// libraries, by paths in its `temper-core` directory; none in wasm,
    /// which writes no runtime, so the playground's download stays small.
    pub runtime: &'static [(&'static str, &'static str)],
    /// Where its runtime's files name the runtime's version, which
    /// `temper build` fills with the build's, and what keeps the runtime
    /// from being published but in a release
    /// ([`crate::version::fill_runtime`]).
    pub runtime_version: &'static crate::version::RuntimeVersion,
    /// Its naming rules, which also name what other libraries use of a
    /// library ([`crate::naming::Interface`]).
    pub rules: &'static NamingRules,
    /// A program's files, or what isn't covered yet. Given a prefix that
    /// leads from the library's output directory to its sources, a
    /// backend that writes source maps (JS) writes them, naming the
    /// sources by their paths after it.
    pub translate: fn(&Program, Option<&str>) -> Result<Vec<OutFile>, String>,
    /// Whether `temper build` builds for it without `-b`, as Kotlin's
    /// defaults: all but C++.
    pub default: bool,
    /// How its language's own tools run built libraries' tests; `None`
    /// in wasm, which runs no tools.
    pub tools: Option<&'static Tools>,
    /// Its config class, which configs may use whichever backends a build
    /// is for ([`crate::config`]).
    pub config: &'static crate::ConfigType,
}

/// How a backend runs the tests of libraries built in
/// `temper.out/BACKEND` (`dir`, absolute) with its language's tools,
/// each test's name and its failure if it failed, or why there are no
/// results. `all` is every library built there, which tests may import.
pub struct Tools {
    /// Readies `dir` once, before any library's tests run.
    pub prepare: Option<Prepare>,
    /// Runs a library's tests.
    pub test: fn(dir: &Path, library: &Tested, all: &[Tested]) -> TestResults,
    /// Runs a library's top level, as `temper run` does: building what
    /// needs building quietly, then the program with the command's own
    /// standard output and error, so what it prints streams. Whether the
    /// program succeeded, or why it couldn't run.
    pub run: Run,
}

/// Runs a library built in `temper.out/BACKEND` (`dir`, absolute), given
/// every library built there.
pub type Run = fn(dir: &Path, library: &Tested, all: &[Tested]) -> Result<bool, String>;

/// Readies `temper.out/BACKEND` for tests, given every library built
/// there.
pub type Prepare = fn(dir: &Path, all: &[Tested]) -> Result<(), String>;

/// A library that built, with how many tests it has (none, for `run`),
/// and what its config says, for the names each backend gives it.
pub struct Tested {
    pub name: String,
    pub tests: usize,
    pub metadata: crate::Metadata,
}

/// Each test's name and its failure, if it failed; or why there are no
/// results.
pub type TestResults = Result<Vec<(String, Option<String>)>, String>;

/// A generated file: its path relative to the output directory, and its
/// text.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct OutFile {
    pub path: String,
    pub text: String,
}

/// Why a backend's files can't all be written, if two of them would be
/// one file: the same path, or paths that differ only in case, which a
/// case-insensitive file system (Windows's, macOS's) takes as one. Each
/// would silently replace the other, as a class named like a module's
/// generated class (Java's `NAMEGlobal`) would replace that class and
/// drop the module's functions; tools report it as an error instead.
pub fn clashing_paths(files: &[OutFile]) -> Option<String> {
    let mut seen = std::collections::HashMap::new();
    for f in files {
        if let Some(earlier) = seen.insert(f.path.to_lowercase(), &f.path) {
            let what = match *earlier == f.path {
                true => format!("two generated files would be {}", f.path),
                false => format!(
                    "generated files {earlier} and {} would be one file where case doesn't count",
                    f.path
                ),
            };
            return Some(format!(
                "{what}: a declaration is likely named like one the backend generates (or \
                 like another but for case); rename it"
            ));
        }
    }
    None
}
