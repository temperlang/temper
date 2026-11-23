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
    /// A program's files, or what isn't covered yet, pointing back at
    /// their sources as [`SourceLinks`] allows.
    pub translate: fn(&Program, SourceLinks) -> Result<Vec<OutFile>, String>,
    /// Whether `temper build` builds for it without `-b`, as Kotlin's
    /// defaults: all but C++.
    pub default: bool,
    /// How its language's own tools run built libraries' tests; `None`
    /// in wasm, which runs no tools.
    pub tools: Option<&'static Tools>,
}

/// How a translation may point back at its sources.
#[derive(Clone, Copy, Debug, Default)]
pub struct SourceLinks<'a> {
    /// A prefix that leads from the library's output directory to its
    /// root, after which a backend names each source by its path: in
    /// source maps (JS), and, with [`dev`](Self::dev), `#line`
    /// directives (C++, C#; [`crate::line_directives`]). `None` where
    /// the sources aren't at hand, as for std.
    pub prefix: Option<&'a str>,
    /// For output that only `temper run` and `temper test` build and
    /// run, not `temper build`'s, which stays as it is, deterministic and
    /// clean to publish: links that only help while developing, such as
    /// `#line`.
    pub dev: bool,
}

impl SourceLinks<'_> {
    /// The prefix for `#line` directives, where they go in: in
    /// [`dev`](Self::dev) output, and where the prefix is a relative
    /// path, not a `file:` URL (sources on another drive), which they
    /// can't name.
    pub fn line_prefix(&self) -> Option<&str> {
        (self.prefix).filter(|prefix| self.dev && !prefix.starts_with("file:"))
    }

    /// The prefix for source maps: a relative path, and a `file:` URL
    /// (sources on another drive) only in [`dev`](Self::dev) output,
    /// since `temper build`'s may be published, and mustn't name a
    /// local directory; it gets no source maps then.
    pub fn map_prefix(&self) -> Option<&str> {
        (self.prefix).filter(|prefix| self.dev || !prefix.starts_with("file:"))
    }
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

#[cfg(test)]
mod tests {
    use super::SourceLinks;

    /// Source maps name sources on another drive (`file:` URLs) only in
    /// `temper run` and `temper test`'s output, never `temper build`'s;
    /// `#line`s never do, and only there.
    #[test]
    fn local_directories_only_in_dev() {
        let links = |prefix, dev| SourceLinks {
            prefix: Some(prefix),
            dev,
        };
        let (relative, url) = ("../src/", "file:///D:/w/src/");
        assert_eq!(links(relative, false).map_prefix(), Some(relative));
        assert_eq!(links(url, false).map_prefix(), None);
        assert_eq!(links(url, true).map_prefix(), Some(url));
        assert_eq!(links(relative, false).line_prefix(), None);
        assert_eq!(links(relative, true).line_prefix(), Some(relative));
        assert_eq!(links(url, true).line_prefix(), None);
    }
}
