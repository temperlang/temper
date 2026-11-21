//! `temper run --library NAME -b BACKEND`: a library's top level, run,
//! as Kotlin Temper's `temper run` does: every library under the work
//! root is built for the backend ([`build`]), then the backend's tools
//! run the one named ([`temper_tmpl::Tools::run`]), with what it prints
//! going straight to the command's standard output and error. `-b
//! interp` runs it on the interpreter instead, writing nothing.
//!
//! Errors don't stop it, as in `build` and `test`: the library runs, an
//! error failing where it's reached, and the run then fails.

use crate::build;
use std::io::Write;
use temper_frontend::interp;
use temper_frontend::library_tests::{Reported, libraries_to_test, problem};
use temper_frontend::log::LogFormat;
use temper_frontend::project::load_library;
use temper_frontend::settle::settle;
use temper_frontend::sources::Sources;
use temper_tmpl::{Backend, Tested};

/// Runs the library named `library` under the work root `root` on
/// `backend`, or on the interpreter for `None`, writing what goes wrong
/// to `err`, and on the interpreter, what it logs to `out`. Whether it
/// ran to its end without errors.
pub fn run(
    sources: &Sources,
    root: &str,
    library: &str,
    backend: Option<&'static Backend>,
    options: build::Options,
    out: &mut dyn Write,
    err: &mut dyn Write,
) -> bool {
    let Some(backend) = backend else {
        return run_interp(sources, root, library, options, out, err);
    };
    let Some(built) = build::build(sources, root, &[backend], options, out, err) else {
        return false;
    };
    if !built.tests.iter().any(|(name, _)| name == library) {
        let text = format!("No library named {library} under {root}");
        problem(
            options.log,
            err,
            "NoSuchLibrary",
            &text,
            &[library.into(), root.into()],
        );
        return false;
    }
    // What the build said goes first.
    let _ = out.flush();
    let _ = err.flush();
    let failed = |name: &str| (built.results.iter()).any(|(l, _, r)| l == name && r.is_err());
    if failed(library) {
        // Already said why.
        return false;
    }
    let Some(tools) = backend.tools else {
        let text = format!("{library}: {}: no tools to run it", backend.name);
        problem(
            options.log,
            err,
            "NotRun",
            &text,
            &[library.into(), backend.name.into()],
        );
        return false;
    };
    let dir = options.out(root).join(backend.name);
    // Tools run in libraries' directories, so paths are absolute.
    let dir = std::path::absolute(&dir).unwrap_or(dir);
    let all: Vec<Tested> = (built.tests.iter())
        .filter(|(name, _)| !failed(name))
        .map(|(name, _)| built.tested(name, 0))
        .collect();
    let this = built.tested(library, 0);
    let ran = (tools.prepare)
        .map_or(Ok(()), |prepare| prepare(&dir, &all))
        .and_then(|()| (tools.run)(&dir, &this, &all));
    match ran {
        Ok(true) => built.ok(),
        Ok(false) => false,
        Err(why) => {
            let text = format!("{library}: {}: {why}", backend.name);
            let values = [library.into(), backend.name.into(), why.as_str().into()];
            problem(options.log, err, "NotRun", &text, &values);
            false
        }
    }
}

/// Runs a library's top level on the interpreter, after its diagnostics,
/// what it logs going to `out` as it's logged.
fn run_interp(
    sources: &Sources,
    root: &str,
    library: &str,
    options: build::Options,
    out: &mut dyn Write,
    err: &mut dyn Write,
) -> bool {
    let log = options.log;
    let Some(libraries) = libraries_to_test(sources, root, options.ignore, Some(library), log, err)
    else {
        return false;
    };
    let Some(found) = libraries.iter().find(|l| l.config.name == library) else {
        return false;
    };
    let settled = match load_library(sources, found, &libraries) {
        Ok(library) => settle(library),
        Err(why) => {
            problem(log, err, "LibraryUnreadable", &why, &[]);
            return false;
        }
    };
    // Each counted under the library it's in, as `build` counts them.
    let mut reported = Reported::new(options.hints);
    let diagnostics = reported.new_diagnostics(&settled, found, &libraries, log);
    let _ = err.write_all(diagnostics.as_bytes());
    // Each line as it's logged, so a long run's output isn't kept.
    let ran = interp::run_streaming(&settled, None, &mut |line| {
        let _ = writeln!(out, "{line}");
    });
    let _ = out.flush();
    if let Some(failure) = &ran.failure {
        let text = format!("{library}: {failure}");
        problem(
            log,
            err,
            "RunFailed",
            &text,
            &[library.into(), failure.as_str().into()],
        );
    }
    if log == LogFormat::Text {
        for (library, count) in &reported.errors {
            let _ = writeln!(err, "{library}: {}", build::error_count(*count));
        }
    }
    ran.failure.is_none() && reported.errors.is_empty()
}
