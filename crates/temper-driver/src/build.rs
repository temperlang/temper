//! `temper build`: every library under a work root, translated by each
//! backend asked for, written to `temper.out` as Kotlin Temper's `temper
//! build` lays it out.
//!
//! - **Libraries** are found as `temper build` finds them
//!   ([`temper_frontend::project`]): each directory with a
//!   `config.temper.md`, named by its config, and two of one name are an
//!   error. A library may import another by its name
//!   (`import("other-lib/sub")`); its modules then settle with the
//!   importer's, and what it uses of them becomes a `uses` section of its
//!   tmpl, with the other library's version, which its package files
//!   name as a dependency. So does what it uses of std, as `temper-std`,
//!   whose names come from std's own naming, as std's modules that build
//!   as a library ([`temper_frontend::stdlib::program`]) give them; std's
//!   output is written beside the libraries that use it, as Kotlin's.
//! - **Output:** `temper.out/BACKEND/LIBRARY/…` (or `DIR/BACKEND/…`
//!   with `-o DIR`, [`Options::out_dir`]), each backend's layout as
//!   Kotlin's, with the package files it writes (`pyproject.toml`,
//!   `Cargo.toml`, `package.json`, `pom.xml`, the `.csproj`s, and the
//!   rockspec), and the backend's runtime beside the libraries, as
//!   `temper.out/BACKEND/temper-core`, as Kotlin copies it. A library's
//!   directory is emptied first, so no stale file survives a rename.
//!   C++ also gets a `CMakeLists.txt` per library, which Kotlin doesn't
//!   write.
//! - **Backends:** those `-b` names, or else Kotlin's defaults that
//!   tempest has ([`crate::Registry::defaults`]; Kotlin's `data` isn't
//!   one of tempest's, and `cpp`, as in Kotlin, is only built when asked
//!   for).
//! - **Host code:** a library's files that aren't Temper go with its
//!   program ([`temper_tmpl::host`]), for each backend to take those with
//!   its extensions; `--no-connected` ([`Options::no_connected`]) has
//!   every backend use Temper implementations instead, wherever there are
//!   any.
//! - **Errors** don't stop a library: as Kotlin's `temper build` does, it
//!   reports each, then translates and writes the library anyway, each
//!   error failing where it runs, as `bubble()` does (tmpl's `Error`), so
//!   its good parts work in every language. [`Built::errors`] counts
//!   them, and `temper build` exits with 1 when there are any.
//! - **What isn't covered yet** fails that library, with the first reason
//!   on a line of its own (`LIBRARY: BACKEND: why`), and the build goes on
//!   with the others. With `--verbose`, every gap in tmpl's translation
//!   comes first, one per line (`LIBRARY: not yet in tmpl: …`), from
//!   [`temper_frontend::to_tmpl::gaps`].
//! - **Reports** are text, or with `--logformat json`
//!   ([`Options::log`]), a JSON record per line ([`temper_frontend::log`]).

use crate::{checked, interface, sources_prefix, translate, uses_std};
use std::io::Write;
use std::path::Path;
use temper_frontend::ignore::IgnoreFile;
use temper_frontend::library_tests::{Reported, libraries_to_test, problem};
use temper_frontend::log::LogFormat;
use temper_frontend::project::{LibrarySources, library_of, load_library};
use temper_frontend::settle::settle;
use temper_frontend::sources::Sources;
use temper_frontend::to_tmpl::gaps;
use temper_tmpl::Tested;
use temper_tmpl::host::HostFile;
use temper_tmpl::naming::Interface;
use temper_tmpl::{Backend, Program};

/// How to build.
#[derive(Clone, Copy, Debug, Default)]
pub struct Options<'a> {
    /// Says what was written.
    pub verbose: bool,
    /// Uses connected functions' and classes' Temper implementations
    /// wherever there are any, rather than host code, as Kotlin's
    /// `--no-connected`.
    pub no_connected: bool,
    /// Where to write each backend's directory, in place of the work
    /// root's `temper.out` (`-o`).
    pub out_dir: Option<&'a Path>,
    /// An ignore file with the last word (`--ignorefile`).
    pub ignore: Option<&'a IgnoreFile>,
    /// Text, or JSON records (`--logformat`).
    pub log: LogFormat,
    /// Reports hints, not only warnings and errors (`-v`).
    pub hints: bool,
}

impl Options<'_> {
    /// Where a build under `root` writes each backend's directory.
    pub fn out(&self, root: &str) -> std::path::PathBuf {
        match self.out_dir {
            Some(dir) => dir.to_path_buf(),
            None => Path::new(root).join("temper.out"),
        }
    }
}

/// What a build did: each library's result per backend.
pub struct Built {
    /// Each library, backend, and why it failed, if it did.
    pub results: Vec<(String, &'static str, Result<(), String>)>,
    /// Each library, with how many tests it has: those that didn't reach
    /// tmpl too, which count as not run, as long as it settled.
    pub tests: Vec<(String, usize)>,
    /// Each library with errors in its own files, and how many, whether
    /// or not it built.
    pub errors: Vec<(String, usize)>,
    /// What each library's config says, by its name.
    pub metadata: Vec<(String, temper_tmpl::Metadata)>,
}

impl Built {
    /// A library that built, for a backend's tools, with what its config
    /// says.
    pub fn tested(&self, name: &str, tests: usize) -> Tested {
        let metadata = (self.metadata.iter())
            .find(|(n, _)| n == name)
            .map(|(_, m)| m.clone())
            .unwrap_or_default();
        Tested {
            name: name.to_string(),
            tests,
            metadata,
        }
    }
}

impl Built {
    /// Whether every library built for every backend, without errors.
    pub fn ok(&self) -> bool {
        self.errors.is_empty() && self.results.iter().all(|(_, _, r)| r.is_ok())
    }
}

/// Builds the libraries under a work root for the backends given,
/// writing to `ROOT/temper.out` (or `options.out_dir`), and diagnostics
/// and failures to `err`, and with `verbose`, what was written to `out`.
/// `None` if there's no library, or two share a name.
pub fn build(
    sources: &Sources,
    root: &str,
    backends: &[&Backend],
    options: Options,
    out: &mut dyn Write,
    err: &mut dyn Write,
) -> Option<Built> {
    let libraries = libraries_to_test(sources, root, options.ignore, None, options.log, err)?;
    let temper_out = options.out(root);
    let mut results = Vec::new();
    let mut programs = Vec::new();
    let mut tests = Vec::new();
    let mut reported = Reported::new(options.hints);
    for library in &libraries {
        let name = &library.config.name;
        match program(sources, library, &libraries, options, &mut reported, err) {
            Ok(program) => {
                let count = program.modules.iter().map(|m| m.tests.len()).sum();
                tests.push((name.clone(), count));
                programs.push((library, program));
            }
            Err((why, count)) => {
                tests.push((name.clone(), count));
                for &backend in backends {
                    results.push((name.clone(), backend.name, Err(why.clone())));
                }
            }
        }
    }
    // Each library after those it uses, so each backend names it with
    // their interfaces (notes/tmpl/open-questions.md, 46 and 47).
    let order = dependency_order(&programs);
    // std, as a library of its own, named by each backend before the
    // libraries that use it.
    let std = match programs.iter().any(|(_, p)| uses_std(p)) {
        true => match temper_frontend::stdlib::program(sources) {
            Ok(std) => Some(std),
            Err(why) => {
                let text = format!("std: {why}");
                problem(
                    options.log,
                    err,
                    "StdFailed",
                    &text,
                    &[why.to_string().into()],
                );
                None
            }
        },
        false => None,
    };
    for &backend in backends {
        let mut interfaces: Vec<Interface> = Vec::new();
        if let Some(std) = &std {
            interfaces.push(interface(std, backend));
        }
        // std's output beside the libraries, which their tests and
        // imports of std's modules need, as `temper.out/BACKEND/std`, as
        // Kotlin's.
        if let Some(std) = &std {
            let dir = temper_out.join(backend.name);
            let result = translate(std, backend, None).and_then(|files| {
                let files: Vec<(String, String)> = (files.into_iter())
                    .map(|(path, text)| {
                        let rest = path.split_once('/').map_or(path.as_str(), |(_, r)| r);
                        (format!("std/{rest}"), text)
                    })
                    .collect();
                write_library(&dir, "std", &files)
            });
            if let Err(why) = result {
                results.push(("std".into(), backend.name, Err(why)));
            }
        }
        for &i in &order {
            let (library, program) = &mut programs[i];
            let name = &library.config.name;
            program.interfaces = interfaces.clone();
            interfaces.push(interface(program, backend));
            let dir = temper_out.join(backend.name);
            // Source maps lead back to the sources' directory.
            let maps = sources_prefix(
                &dir.join(&program.library.name),
                &format!("{}/x", library.root),
            );
            let result = translate(program, backend, Some(&maps))
                .and_then(|files| write_library(&dir, name, &files));
            if result.is_ok() && options.verbose {
                let _ = writeln!(out, "Wrote {}", dir.join(name).display());
            }
            results.push((name.clone(), backend.name, result));
        }
    }
    for &backend in backends {
        let dir = temper_out.join(backend.name);
        if let Err(why) = write_runtime(&dir, backend) {
            results.push(("temper-core".into(), backend.name, Err(why)));
        }
    }
    for (library, backend, result) in &results {
        if let Err(why) = result {
            let text = format!("{library}: {backend}: {why}");
            let values = [
                library.as_str().into(),
                (*backend).into(),
                why.as_str().into(),
            ];
            problem(options.log, err, "NotBuilt", &text, &values);
        }
    }
    let errors = reported.errors;
    // Each error has a record of its own in JSON.
    if options.log == LogFormat::Text {
        for (library, count) in &errors {
            let _ = writeln!(err, "{library}: {}", error_count(*count));
        }
    }
    let metadata = (libraries.iter())
        .map(|l| {
            let exports = l.config.exports.clone();
            (l.config.name.clone(), temper_tmpl::Metadata { exports })
        })
        .collect();
    Some(Built {
        results,
        tests,
        errors,
        metadata,
    })
}

/// "1 error" or "N errors".
pub fn error_count(count: usize) -> String {
    match count {
        1 => "1 error".into(),
        n => format!("{n} errors"),
    }
}

/// The programs' indexes, each after those of the libraries it uses,
/// else in the order found.
fn dependency_order(programs: &[(&LibrarySources, Program)]) -> Vec<usize> {
    let mut order: Vec<usize> = Vec::new();
    while order.len() < programs.len() {
        let before = order.len();
        for (i, (_, program)) in programs.iter().enumerate() {
            if order.contains(&i) {
                continue;
            }
            let ready = program.dependencies().iter().all(|(name, _)| {
                (programs.iter().enumerate())
                    .all(|(j, (l, _))| l.config.name != *name || order.contains(&j))
            });
            if ready {
                order.push(i);
            }
        }
        if order.len() == before {
            // A cycle, which imports rule out: the rest as found.
            let rest: Vec<usize> = (0..programs.len()).filter(|i| !order.contains(i)).collect();
            order.extend(rest);
        }
    }
    order
}

/// A library's program in tmpl, with its config's version, or why there
/// isn't one: what tmpl doesn't cover yet. Its diagnostics go to `err`,
/// those not yet reported, as those of a library it imports may have
/// been, counted in `reported` under the library they're in; its errors
/// become tmpl's `Error`s, which fail where they run.
fn program(
    sources: &Sources,
    library: &LibrarySources,
    all: &[LibrarySources],
    options: Options,
    reported: &mut Reported,
    err: &mut dyn Write,
) -> Result<Program, (String, usize)> {
    let settled = settle(load_library(sources, library, all).map_err(|e| (e, 0))?);
    // Its tests, which count where it fails, as not run.
    let count = settled.modules.iter().map(|m| m.test_cases.len()).sum();
    let diagnostics = reported.new_diagnostics(&settled, library, all, options.log);
    let _ = err.write_all(diagnostics.as_bytes());
    // The libraries it imports, named when it fails, which may be why.
    let used: Vec<&str> = (settled.library.modules.iter())
        .filter_map(|m| library_of(all, &m.dir))
        .filter(|l| l.root != library.root)
        .map(|l| l.config.name.as_str())
        .collect();
    let translated = checked(&settled, &library.config.name);
    // With `--verbose`, every gap, not only the first.
    if translated.is_err() && options.verbose {
        for gap in gaps(&settled, &library.config.name) {
            let text = format!("{}: {gap}", library.config.name);
            let values = [library.config.name.as_str().into(), gap.to_string().into()];
            problem(options.log, err, "NotYetInTmpl", &text, &values);
        }
    }
    let mut program = translated.map_err(|e| {
        let why = if used.is_empty() {
            e
        } else {
            format!("{e} (it imports {})", used.join(", "))
        };
        (why, count)
    })?;
    if let Some(version) = library.config.get("version") {
        program.library.version = version.to_string();
    }
    // The libraries it uses take their configs' versions, for package
    // files' dependencies.
    // Their configs' metadata, and its own, for package files and each
    // backend's names for them.
    let metadata = |config: &temper_frontend::project::Config| temper_tmpl::Metadata {
        exports: config.exports.clone(),
    };
    program.metadata = vec![(library.config.name.clone(), metadata(&library.config))];
    for uses in &mut program.uses {
        let name = program.decls.get(uses.decl).name;
        let name = program.strings.get(name);
        let config = all.iter().find(|l| l.config.name == name);
        if let Some(version) = config.and_then(|l| l.config.get("version")) {
            uses.version = version.to_string();
        }
        if let Some(config) = config {
            program
                .metadata
                .push((name.to_string(), metadata(&config.config)));
        }
    }
    // Its host code, by paths relative to its root.
    program.host.no_connected = options.no_connected;
    for path in &library.host_files {
        let text = (sources.read(path)).map_err(|e| (format!("{path}: {e}"), count))?;
        let rel = (path.strip_prefix(&library.root))
            .unwrap_or(path)
            .trim_start_matches('/');
        program.host.files.push(HostFile {
            path: rel.to_string(),
            text: text.into_owned(),
        });
    }
    Ok(program)
}

/// Writes a library's files under a backend's directory, after emptying
/// the library's own directory there.
fn write_library(dir: &Path, library: &str, files: &[(String, String)]) -> Result<(), String> {
    let own = dir.join(library);
    if own.exists() {
        std::fs::remove_dir_all(&own).map_err(|e| format!("{}: {e}", own.display()))?;
    }
    for (file, text) in files {
        let path = dir.join(file);
        path.parent()
            .map_or(Ok(()), std::fs::create_dir_all)
            .and_then(|()| std::fs::write(&path, text))
            .map_err(|e| format!("{}: {e}", path.display()))?;
    }
    Ok(())
}

/// Writes a backend's runtime as `temper-core` in its directory, replacing
/// what was there, as Kotlin does, naming the build's version.
fn write_runtime(dir: &Path, backend: &temper_tmpl::Backend) -> Result<(), String> {
    let core = dir.join("temper-core");
    if core.exists() {
        std::fs::remove_dir_all(&core).map_err(|e| format!("{}: {e}", core.display()))?;
    }
    let version = temper_tmpl::version::current();
    let files =
        temper_tmpl::version::fill_runtime(backend.runtime, backend.runtime_version, version);
    let pairs: Vec<(String, String)> = (files.into_iter())
        .map(|(p, t)| (format!("temper-core/{p}"), t))
        .collect();
    write_library(dir, "temper-core", &pairs)
}
