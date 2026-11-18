//! What tools share over the backends, as a library: the registry of
//! backends this build has ([`Registry`]); one way from a settled
//! library to each backend's files ([`checked`], [`with_std`],
//! [`translate`]), which `temper build` ([`build`]) and the
//! playground's wasm (`temper-wasm`) both take; and, natively, running
//! built libraries' tests ([`test_run`]) and top levels ([`run`]) with
//! each language's own tools, as each backend says
//! ([`temper_tmpl::Tools`]); `temper init`'s skeleton ([`init`]); and
//! the build's version, from git ([`version`]).
//!
//! Each built-in backend is a cargo feature (`cpp`, `csharp`, `java`,
//! `js`, `lua`, `py`, and `rust`), all on by default.

pub mod build;
pub mod init;
pub mod repl;
// Not in the wasm, which runs no tools.
#[cfg(any(unix, windows))]
pub mod run;
#[cfg(any(unix, windows))]
pub mod test_run;
pub mod version;

use std::path::Path;
use temper_frontend::settle::Settled;
use temper_frontend::to_tmpl::to_tmpl;
use temper_tmpl::naming::{Interface, assign};
use temper_tmpl::{Backend, Profile, Program, validate};

/// Every backend's name, alphabetically, whether or not this build has
/// it, so a name left out can say so; the playground's wasm numbers
/// backends by their places here.
pub const KNOWN: [&str; 7] = ["cpp", "csharp", "java", "js", "lua", "py", "rust"];

/// Kotlin Temper's backends that tempest doesn't have yet, alphabetically,
/// so the command can say so rather than that there's no such backend:
/// `cppv` (a second, "versatile" C++ backend, for C++11 and older),
/// `data` (the data files `dataFile` declares, among Kotlin's defaults),
/// `java8` (Java for its version 8), and `mypyc` (Python for mypyc to
/// compile).
pub const NOT_YET: [&str; 4] = ["cppv", "data", "java8", "mypyc"];

/// The backends this run has: those built in, by cargo feature, and
/// later those that join while it runs (docs/internals, "Dynamic
/// backends").
pub struct Registry {
    backends: Vec<&'static Backend>,
}

impl Registry {
    /// The built-in backends, alphabetically.
    pub fn built_in() -> Registry {
        let backends = vec![
            #[cfg(feature = "cpp")]
            &temper_be_cpp::BACKEND,
            #[cfg(feature = "csharp")]
            &temper_be_csharp::BACKEND,
            #[cfg(feature = "java")]
            &temper_be_java::BACKEND,
            #[cfg(feature = "js")]
            &temper_be_js::BACKEND,
            #[cfg(feature = "lua")]
            &temper_be_lua::BACKEND,
            #[cfg(feature = "py")]
            &temper_be_py::BACKEND,
            #[cfg(feature = "rust")]
            &temper_be_rust::BACKEND,
        ];
        Registry { backends }
    }

    /// Every backend it has.
    pub fn all(&self) -> &[&'static Backend] {
        &self.backends
    }

    /// A backend it has, by name.
    pub fn get(&self, name: &str) -> Option<&'static Backend> {
        self.backends.iter().copied().find(|b| b.name == name)
    }

    /// What `build` translates for without `-b` ([`Backend::default`]).
    pub fn defaults(&self) -> Vec<&'static Backend> {
        self.backends
            .iter()
            .copied()
            .filter(|b| b.default)
            .collect()
    }

    /// Whether a name is a known backend's ([`KNOWN`]) that it lacks.
    pub fn lacks(&self, name: &str) -> bool {
        KNOWN.contains(&name) && self.get(name).is_none()
    }
}

/// A settled library as tmpl, named `name`, and checked; or why not: what
/// tmpl doesn't cover yet, or the validator's problems, each `tmpl: …` on
/// a line.
pub fn checked(settled: &Settled, name: &str) -> Result<Program, String> {
    let program = to_tmpl(settled, name).map_err(|e| e.to_string())?;
    let problems = validate(&program, &Profile::full());
    if !problems.is_empty() {
        let problems: Vec<String> = problems.iter().map(|p| format!("tmpl: {p}")).collect();
        return Err(problems.join("\n"));
    }
    Ok(program)
}

/// Whether a program uses std.
pub fn uses_std(p: &Program) -> bool {
    (p.uses.iter()).any(|u| p.strings.get(p.decls.get(u.decl).name) == "std")
}

/// What other libraries use of a library, as a backend names it.
pub fn interface(p: &Program, backend: &Backend) -> Interface {
    Interface::of(p, &assign(p, backend.rules))
}

/// A library built alone, with std's interface for a backend where it
/// uses std, so it spells the names std's own naming gives
/// (notes/tmpl/open-questions.md, 46); `std` gives std as tmpl, asked for
/// only then.
pub fn with_std<'s>(
    mut p: Program,
    backend: &Backend,
    std: impl FnOnce() -> Result<&'s Program, String>,
) -> Result<Program, String> {
    if uses_std(&p) {
        p.interfaces = vec![interface(std()?, backend)];
    }
    Ok(p)
}

/// A program's files from a backend, each a path and its text, or what
/// isn't covered yet. With `maps`, a prefix that leads from the
/// library's output directory to its sources, a backend that writes
/// source maps writes them.
pub fn translate(
    p: &Program,
    backend: &Backend,
    maps: Option<&str>,
) -> Result<Vec<(String, String)>, String> {
    let files = (backend.translate)(p, maps)?;
    Ok(files.into_iter().map(|f| (f.path, f.text)).collect())
}

/// The relative path, with `/` after each part, from a package's
/// directory to the directory of a library's first file, for a source
/// map's `sources`; or, where there's none, as on another drive on
/// Windows, that directory as a `file:` URL, since `sources` are URLs.
pub fn sources_prefix(package: &Path, first: &str) -> String {
    let absolute = |p: &Path| std::path::absolute(p).unwrap_or_else(|_| p.to_path_buf());
    let normal = |p: &Path| {
        let mut parts = Vec::new();
        for c in absolute(p).components() {
            match c {
                std::path::Component::ParentDir => {
                    parts.pop();
                }
                std::path::Component::CurDir => {}
                c => parts.push(c.as_os_str().to_string_lossy().into_owned()),
            }
        }
        parts
    };
    let source = Path::new(first).parent().unwrap_or(Path::new(""));
    let (from, to) = (normal(package), normal(source));
    let common = from.iter().zip(&to).take_while(|(a, b)| a == b).count();
    if common == 0 {
        let dir: std::path::PathBuf = to.iter().collect();
        return dir_url(&temper_frontend::project::slashed(&dir.to_string_lossy()));
    }
    let mut prefix = "../".repeat(from.len() - common);
    for part in &to[common..] {
        prefix.push_str(part.trim_end_matches(['/', '\\']));
        prefix.push('/');
    }
    prefix
}

/// An absolute directory, `/`-separated (`D:/a`, `//server/share/a`, or
/// `/a`), as a `file:` URL with a `/` after it.
fn dir_url(dir: &str) -> String {
    let dir = dir.trim_end_matches('/');
    match dir.strip_prefix("//") {
        Some(unc) => format!("file://{unc}/"),
        None => format!("file:///{}/", dir.trim_start_matches('/')),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dir_urls() {
        assert_eq!(dir_url("D:/a/b"), "file:///D:/a/b/");
        assert_eq!(dir_url("D:/"), "file:///D:/");
        assert_eq!(dir_url("//server/share/a"), "file://server/share/a/");
        assert_eq!(dir_url("/a/b/"), "file:///a/b/");
    }

    #[test]
    fn sources_prefixes_are_relative() {
        let package = Path::new("/w/temper.out/js/lib");
        assert_eq!(sources_prefix(package, "/w/src/x.temper"), "../../../src/");
        assert_eq!(sources_prefix(package, "/w/temper.out/js/lib/x"), "");
    }
}
