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
    /// The directories its tools make in `temper.out/BACKEND` beside the
    /// libraries' (`target`, `node_modules`), which no library's files
    /// may be in ([`clashing_paths`]).
    pub dirs: &'static [&'static str],
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

/// Why a backend's files can't all be written: a path that isn't plain
/// ([`unsafe_part`]), or that's in `reserved`, a directory beside the
/// libraries' that the backend's tools own ([`Tools::dirs`]), or
/// `temper-core`, its runtime's; or two files that would be one: the
/// same path, or paths that differ only in case, which a
/// case-insensitive file system (Windows's, macOS's) takes as one
/// ([`folded`]). Each would silently replace the other, as a class named
/// like a module's generated class (Java's `NAMEGlobal`) would replace
/// that class and drop the module's functions; tools report it as an
/// error instead.
pub fn clashing_paths(files: &[OutFile], reserved: &[&str]) -> Option<String> {
    let mut seen = std::collections::HashMap::new();
    for f in files {
        if let Some(why) = f.path.split('/').find_map(unsafe_part) {
            return Some(format!("generated file {} {why}; rename it", f.path));
        }
        let top = f.path.split('/').next().unwrap_or_default();
        if let Some(dir) =
            (reserved.iter().chain(&["temper-core"])).find(|d| folded(d) == folded(top))
        {
            return Some(format!(
                "generated file {} would be in {dir}, which the backend's tools use; \
                 rename the library",
                f.path
            ));
        }
        if let Some(earlier) = seen.insert(folded(&f.path), &f.path) {
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

/// Why a part of a generated file's path, between `/`s, isn't a plain
/// name that stays where it is on every file system, if it isn't: empty
/// (a leading `/` or `//`), `.` or `..`, with `\\` or `:` (Windows's
/// separator and drive), ending in `.` or a space (which Windows drops),
/// or a name Windows reserves for a device, as `CON` or `Aux.cs`.
pub fn unsafe_part(part: &str) -> Option<&'static str> {
    const DEVICES: &[&str] = &["CON", "PRN", "AUX", "NUL"];
    let stem = part
        .split('.')
        .next()
        .unwrap_or_default()
        .to_ascii_uppercase();
    let numbered = |prefix| {
        (stem.strip_prefix(prefix))
            .is_some_and(|n| n.len() == 1 && n != "0" && n.chars().all(|c| c.is_ascii_digit()))
    };
    if part.is_empty() || part == "." || part == ".." {
        Some("leaves its directory")
    } else if part.contains(['\\', ':']) {
        Some("has `\\` or `:`, which Windows takes as a separator or drive")
    } else if part.ends_with(['.', ' ']) {
        Some("ends in `.` or a space, which Windows drops")
    } else if DEVICES.contains(&stem.as_str()) || numbered("COM") || numbered("LPT") {
        Some("is named like a Windows device")
    } else {
        None
    }
}

/// A path as a case-insensitive file system compares it: each character
/// by its simple (one-character) uppercase, then that's lowercase, so
/// `ẞ` and `ß`, `ǅ` and `ǆ`, and `ς` and `σ` are each one. Decomposed and
/// composed forms (`é` as `e` and an accent), which macOS takes as one,
/// aren't: that would need Unicode normalization's tables, and generated
/// names come from Temper names, which the frontend reads as written.
pub fn folded(path: &str) -> String {
    fn one(mut chars: impl Iterator<Item = char>) -> Option<char> {
        let c = chars.next()?;
        chars.next().is_none().then_some(c)
    }
    (path.chars())
        .map(|c| one(c.to_uppercase()).unwrap_or(c))
        .map(|c| one(c.to_lowercase()).unwrap_or(c))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn clash(paths: &[&str]) -> Option<String> {
        let files: Vec<OutFile> = (paths.iter())
            .map(|p| OutFile {
                path: p.to_string(),
                text: String::new(),
            })
            .collect();
        clashing_paths(&files, &["target", "node_modules"])
    }

    /// What clashes and what doesn't, each verdict written down.
    #[test]
    fn clashes() {
        let fine: &[&[&str]] = &[
            &["a/src/Point.cs", "a/src/Points.cs"],
            &["a/A.java", "b/A.java"],
            // Distinct where case doesn't count, and not devices.
            &["a/Café.cs", "a/Cafe.cs"],
            &[
                "a/Console.cs",
                "a/Auxiliary.cs",
                "a/COM10.cs",
                "a/LPT0.cs",
                "a/Nul_.cs",
            ],
            &["a/.hidden", "a/x.d.ts"],
            // Decomposed vs composed `é`: one file on macOS, but not
            // detected, by decision (see `folded`).
            &["a/Caf\u{e9}.cs", "a/Cafe\u{301}.cs"],
            &["my-target/x", "targets/x"],
        ];
        for paths in fine {
            assert_eq!(clash(paths), None, "{paths:?}");
        }
        let clashing: &[(&[&str], &str)] = &[
            (&["a/X.cs", "a/X.cs"], "two generated files would be a/X.cs"),
            (
                &["a/Point.java", "a/POINT.java"],
                "would be one file where case doesn't count",
            ),
            (
                &["a/Stra\u{df}e.cs", "a/STRA\u{1e9e}E.cs"],
                "would be one file",
            ),
            (&["a/\u{1c5}.cs", "a/\u{1c6}.cs"], "would be one file"),
            (&["a/\u{3c3}.py", "a/\u{3c2}.py"], "would be one file"),
            (
                &["a/src/../src/X.cs"],
                "a/src/../src/X.cs leaves its directory",
            ),
            (&["../x.csproj"], "leaves its directory"),
            (&["/abs/x"], "leaves its directory"),
            (&["a//x"], "leaves its directory"),
            (&["a/./x"], "leaves its directory"),
            (&["C:/x"], "Windows takes as a separator or drive"),
            (&["a\\..\\x"], "Windows takes as a separator or drive"),
            (&["a/x."], "ends in `.` or a space"),
            (&["a/x "], "ends in `.` or a space"),
            (&["a/Con.java"], "is named like a Windows device"),
            (&["a/aux.cs"], "is named like a Windows device"),
            (&["a/NUL"], "is named like a Windows device"),
            (&["a/prn.tar.gz"], "is named like a Windows device"),
            (&["a/com1.lua"], "is named like a Windows device"),
            (&["lpt9/x"], "is named like a Windows device"),
            (
                &["target/Cargo.toml"],
                "would be in target, which the backend's tools use",
            ),
            (&["Node_Modules/x.js"], "would be in node_modules"),
            (&["temper-core/x"], "would be in temper-core"),
        ];
        for (paths, why) in clashing {
            let said = clash(paths);
            assert!(
                said.as_ref().is_some_and(|s| s.contains(why)),
                "{paths:?}: {said:?}"
            );
        }
    }
}
