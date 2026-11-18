//! `temper test -b BACKEND` ([`test_backend`]): running built libraries' tests with each
//! language's own tools, as Kotlin Temper's `temper test -b` does, on
//! what `build` wrote to `temper.out/BACKEND`, and reading their results
//! into the interpreter's summary ([`Tally`](temper_frontend::library_tests::Tally)),
//! so `-b interp` and `-b py` print alike. How each language runs its
//! tests is its backend's ([`Backend::tools`], each crate's `tools`
//! module); this runs them for any backend that has them.
//!
//! Each library's results are its tests' names and failures, as the
//! tools report them; a run with no results fails the library, with the
//! tool's output.

use crate::build;
use std::io::Write;
use std::path::Path;
use temper_frontend::library_tests::{LibraryTests, Tally, TestOptions, problem, test_failed};
use temper_frontend::log::{LogFormat, record};
use temper_frontend::sources::Sources;
use temper_tmpl::Backend;
pub use temper_tmpl::Tested;

/// `temper test -b BACKEND`: builds the libraries under a work root for
/// the backend, as `build` does, saying what each needs that isn't
/// covered yet, then runs the tests of those that built (or only
/// `options.library`'s) with the language's own tools ([`run`]),
/// writing failures (or with `verbose`, every result) to `out` as the
/// interpreter's run does (in JSON, records to `err`). Whether all
/// passed, without errors, and the tally, whose summary the caller
/// writes; or `None`, having said why, if nothing could be built or
/// there's no library of the name asked for. Tests of a library that
/// didn't build count as not run.
pub fn test_backend(
    sources: &Sources,
    root: &str,
    backend: &'static Backend,
    options: &TestOptions,
    build_options: build::Options,
    out: &mut dyn Write,
    err: &mut dyn Write,
) -> Option<(bool, Tally)> {
    let mut tally = Tally::default();
    let built = build::build(sources, root, &[backend], build_options, out, err)?;
    let json = options.log == LogFormat::Json;
    let wanted = |name: &str| options.library.is_none_or(|l| l == name);
    if let Some(name) = options.library {
        if !built.tests.iter().any(|(l, _)| l == name) {
            let text = format!("No library named {name} under {root}");
            problem(
                options.log,
                err,
                "NoSuchLibrary",
                &text,
                &[name.into(), root.into()],
            );
            return None;
        }
    }
    // Libraries with errors still built, and their tests run, as on the
    // interpreter and in Kotlin's `temper test`, but the run fails.
    let failed: Vec<&str> = (built.results.iter())
        .filter(|(_, _, r)| r.is_err())
        .map(|(l, _, _)| l.as_str())
        .collect();
    let all: Vec<Tested> = (built.tests.iter())
        .filter(|(name, _)| !failed.contains(&name.as_str()))
        .map(|(name, tests)| built.tested(name, if wanted(name) { *tests } else { 0 }))
        .collect();
    let dir = build_options.out(root).join(backend.name);
    let mut ok = failed.iter().all(|l| !wanted(l)) && built.errors.is_empty();
    // Tests of libraries that didn't build didn't run.
    for (name, tests) in &built.tests {
        if failed.contains(&name.as_str()) && wanted(name) {
            tally.defined += tests;
            tally.not_run += tests;
        }
    }
    for (name, result) in run(backend, &dir, &all) {
        match result {
            Ok(tests) => {
                let failing = tests.results.iter().any(|(_, f)| f.is_some());
                if json {
                    for (test, failure) in &tests.results {
                        match failure {
                            Some(cause) => {
                                let cause = cause.lines().next().unwrap_or("");
                                test_failed(err, backend.name, test, cause);
                            }
                            None if options.verbose => {
                                let values = [backend.name.into(), test.as_str().into()];
                                let text = format!("Test passed ({}): {test}", backend.name);
                                let line = record("info", "TestPassed", None, &values, &text);
                                let _ = writeln!(err, "{line}");
                            }
                            None => {}
                        }
                    }
                } else if options.verbose || failing {
                    let _ = writeln!(out, "Testing {name} on {}", backend.name);
                    for (test, failure) in &tests.results {
                        let _ = match failure {
                            None if options.verbose => writeln!(out, "  ok      {test}"),
                            None => Ok(()),
                            // Its first line, as a stack trace may follow.
                            Some(cause) => {
                                let cause = cause.lines().next().unwrap_or("");
                                writeln!(out, "  failed  {test} - {cause}")
                            }
                        };
                    }
                }
                tally.add(&tests);
            }
            Err(why) => {
                ok = false;
                let text = format!("{name}: {}: {why}", backend.name);
                let values = [
                    name.as_str().into(),
                    backend.name.into(),
                    why.as_str().into(),
                ];
                match json {
                    true => problem(options.log, err, "TestsNotRun", &text, &values),
                    false => {
                        let _ = writeln!(out, "{text}");
                    }
                }
                let defined = (all.iter()).find(|t| t.name == name).map_or(0, |t| t.tests);
                tally.defined += defined;
                tally.not_run += defined;
            }
        }
    }
    Some((ok && tally.all_passed(), tally))
}

/// Runs the tests of the libraries in `dir` (`temper.out/BACKEND`) with
/// the backend's tools, each as its results, or why there are none.
/// `all` is every library built there, which tests may import.
pub fn run(
    backend: &Backend,
    dir: &Path,
    all: &[Tested],
) -> Vec<(String, Result<LibraryTests, String>)> {
    // Tools run in libraries' directories, so paths are absolute.
    let dir = &std::path::absolute(dir).unwrap_or_else(|_| dir.to_path_buf());
    let tools = (backend.tools).ok_or_else(|| format!("no test runner for {}", backend.name));
    let setup = tools
        .clone()
        .and_then(|t| t.prepare.map_or(Ok(()), |prepare| prepare(dir, all)));
    let mut out = Vec::new();
    for library in all.iter().filter(|l| l.tests > 0) {
        let result = setup.clone().and_then(|()| {
            let results = (tools.clone()?.test)(dir, library, all)?;
            Ok(LibraryTests {
                results,
                defined: library.tests,
                output: Vec::new(),
                steps: Vec::new(),
                failure: None,
                diagnostics: String::new(),
                json_diagnostics: String::new(),
                errors: 0,
            })
        });
        out.push((library.name.clone(), result));
    }
    out
}
