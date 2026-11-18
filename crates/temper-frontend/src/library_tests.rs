//! Running libraries' tests on the interpreter, as Kotlin Temper's
//! `temper test` does, for the `temper test` command.
//!
//! [`test_dir`] finds the libraries under a directory's work root
//! ([`crate::project`]), in its [`Sources`], settles each, and runs its
//! tests, `test("name")
//! { … }`. Like `temper test`, it prints only what failed, then Kotlin's
//! summary, `Tests passed: 29 of 29`, unless asked for every test's
//! result, and fails if a test failed or didn't run, or a library has
//! errors. [`test_libraries`] does the same under a work root, with
//! `temper test`'s options ([`TestOptions`]): one library, an ignore
//! file, and JSON records, leaving the summary to the caller, which may
//! sum several backends' tallies.

use crate::ignore::IgnoreFile;
use crate::interp::{TEST_FUEL, run_tests};
use crate::library::Diagnostic;
use crate::library::Level;
use crate::log::{LogFormat, Value, record, write_json_diagnostic, write_json_diagnostics};
use crate::project::{
    LibrarySources, duplicate_names, find_libraries_ignoring, library_of, load_library, work_root,
};
use crate::settle::{Settled, settle};
use crate::sources::Sources;
use std::collections::HashSet;
use std::io::Write;

/// Tests the libraries under `dir`'s work root, writing the failures (or
/// with `verbose`, each library's output and every test's result) and the
/// summary to `out`, and diagnostics and problems to `err`. Whether all
/// passed, without errors.
pub fn test_dir(
    sources: &Sources,
    dir: &str,
    verbose: bool,
    out: &mut dyn Write,
    err: &mut dyn Write,
) -> bool {
    let root = work_root(sources, dir);
    let options = TestOptions {
        verbose,
        fuel: Some(TEST_FUEL),
        ..TestOptions::default()
    };
    let Some((ok, tally)) = test_libraries(sources, &root, &options, out, err) else {
        return false;
    };
    let _ = writeln!(out, "{}", tally.summary());
    let _ = out.flush();
    ok
}

/// How to test, as `temper test`'s flags say.
#[derive(Default)]
pub struct TestOptions<'a> {
    /// Every test's result, and the libraries' output.
    pub verbose: bool,
    /// Only this library's tests, by its name (`--library`), as Kotlin's.
    pub library: Option<&'a str>,
    /// An ignore file with the last word (`--ignorefile`).
    pub ignore: Option<&'a IgnoreFile>,
    /// Text, or JSON records, all to `err`, as Kotlin's `--logformat`.
    pub log: LogFormat,
    /// The steps each test may take on the interpreter (`--fuel`), and
    /// its top-level code before them; `None` for no limit. The CLI's
    /// default is [`TEST_FUEL`].
    pub fuel: Option<u64>,
}

/// Tests the libraries under a work root on the interpreter, as
/// [`test_dir`] does, without the summary: whether all passed, without
/// errors, and the tally; or `None`, having said why, if there's nothing
/// to test (no library, two of one name, or none named as asked). In JSON (`options.log`), everything goes to
/// `err` as records ([`crate::log`]): diagnostics, Kotlin's `TestFailed`
/// for each failure, and with `verbose`, `TestPassed` and `Output`.
pub fn test_libraries(
    sources: &Sources,
    root: &str,
    options: &TestOptions,
    out: &mut dyn Write,
    err: &mut dyn Write,
) -> Option<(bool, Tally)> {
    let json = options.log == LogFormat::Json;
    let mut tally = Tally::default();
    let libraries = libraries_to_test(
        sources,
        root,
        options.ignore,
        options.library,
        options.log,
        err,
    )?;
    let mut ok = true;
    let mut reported = Reported::new(options.verbose);
    for library in &libraries {
        if options
            .library
            .is_some_and(|name| name != library.config.name)
        {
            continue;
        }
        let (tests, settled) = match test_library(sources, library, &libraries, options.fuel) {
            Ok(tested) => tested,
            Err(message) => {
                problem(options.log, err, "LibraryUnreadable", &message, &[]);
                ok = false;
                continue;
            }
        };
        let diagnostics = reported.new_diagnostics(&settled, library, &libraries, options.log);
        let _ = err.write_all(diagnostics.as_bytes());
        let failed = tests.failure.is_some() || tests.results.iter().any(|(_, f)| f.is_some());
        if json {
            let name = &library.config.name;
            if options.verbose {
                for line in &tests.output {
                    let values = [name.as_str().into(), line.as_str().into()];
                    let _ = writeln!(err, "{}", record("info", "Output", None, &values, line));
                }
            }
            for (test, failure) in &tests.results {
                match failure {
                    Some(cause) => test_failed(err, "interp", test, cause),
                    None if options.verbose => {
                        let values = ["interp".into(), test.as_str().into()];
                        let message = format!("Test passed (interp): {test}");
                        let _ = writeln!(
                            err,
                            "{}",
                            record("info", "TestPassed", None, &values, &message)
                        );
                    }
                    None => {}
                }
            }
            if let Some(failure) = &tests.failure {
                problem(
                    options.log,
                    err,
                    "LibraryFailed",
                    failure,
                    &[name.as_str().into()],
                );
            }
            tally.add(&tests);
            continue;
        }
        if !options.verbose && !failed {
            tally.add(&tests);
            continue;
        }
        let _ = writeln!(out, "Testing {}", library.root);
        for line in &tests.output {
            let _ = writeln!(out, "{line}");
        }
        for (name, failure) in &tests.results {
            let _ = match failure {
                None if options.verbose => match tests.steps_of(name) {
                    Some(steps) => writeln!(out, "  ok      {name} ({steps} steps)"),
                    None => writeln!(out, "  ok      {name}"),
                },
                None => Ok(()),
                Some(cause) => writeln!(out, "  failed  {name} - {cause}"),
            };
        }
        if let Some(failure) = &tests.failure {
            let _ = writeln!(out, "  {failure}");
        }
        tally.add(&tests);
    }
    // Errors anywhere fail the run, whichever libraries were tested.
    ok &= reported.errors.is_empty();
    Some((ok && tally.all_passed(), tally))
}

/// The libraries under a work root, or `None`, having said why: there
/// are none, two share a name, or none has the name asked for.
pub fn libraries_to_test(
    sources: &Sources,
    root: &str,
    ignore: Option<&IgnoreFile>,
    library: Option<&str>,
    log: LogFormat,
    err: &mut dyn Write,
) -> Option<Vec<LibrarySources>> {
    let libraries = find_libraries_ignoring(sources, root, ignore);
    if libraries.is_empty() {
        let message = format!("No Temper library (config.temper.md) under {root}");
        problem(log, err, "NoLibraries", &message, &[root.into()]);
        return None;
    }
    let duplicates = duplicate_names(&libraries);
    for name in &duplicates {
        let message = format!("Two libraries are named {name} under {root}");
        problem(
            log,
            err,
            "DuplicateLibraryName",
            &message,
            &[(*name).into(), root.into()],
        );
    }
    if !duplicates.is_empty() {
        return None;
    }
    if let Some(name) = library {
        if !libraries.iter().any(|l| l.config.name == name) {
            let message = format!("No library named {name} under {root}");
            problem(
                log,
                err,
                "NoSuchLibrary",
                &message,
                &[name.into(), root.into()],
            );
            return None;
        }
    }
    Some(libraries)
}

/// Says what went wrong: its text, a line, or an error record.
pub fn problem(log: LogFormat, err: &mut dyn Write, template: &str, text: &str, values: &[Value]) {
    let _ = match log {
        LogFormat::Text => writeln!(err, "{text}"),
        LogFormat::Json => writeln!(err, "{}", record("error", template, None, values, text)),
    };
}

/// Kotlin's `TestFailed` record: the backend, the test, and why.
pub fn test_failed(err: &mut dyn Write, backend: &str, test: &str, cause: &str) {
    let values = [backend.into(), test.into(), cause.into()];
    let message = format!("Test failed ({backend}): {test} - {cause}");
    let _ = writeln!(
        err,
        "{}",
        record("error", "TestFailed", None, &values, &message)
    );
}

/// A library's tests, run.
#[derive(Debug)]
pub struct LibraryTests {
    /// Each test that ran, by name, with why it failed, if it did.
    pub results: Vec<(String, Option<String>)>,
    /// How many tests the library declares.
    pub defined: usize,
    /// What the library logged, as [`crate::interp::Run::output`] keeps it.
    pub output: Vec<String>,
    /// Each test that ran on the interpreter, by name, with the steps it
    /// took.
    pub steps: Vec<(String, u64)>,
    /// How the run failed, if it stopped before the tests finished.
    pub failure: Option<String>,
    /// The library's diagnostics, a line each, with file, line and column.
    pub diagnostics: String,
    /// The same as JSON records ([`crate::log`]).
    pub json_diagnostics: String,
    /// How many of those are errors.
    pub errors: usize,
}

/// Settles a library, with the modules it imports from the libraries in
/// `all`, and runs its tests, each with `fuel` steps if given, giving
/// them and the settled library. The error names a file that can't be
/// read.
pub fn test_library(
    sources: &Sources,
    library: &LibrarySources,
    all: &[LibrarySources],
    fuel: Option<u64>,
) -> Result<(LibraryTests, Settled), String> {
    let mut settled = settle(load_library(sources, library, all)?);
    // Only the library's own tests, not those of libraries it imports.
    for module in &mut settled.modules {
        let dir = &settled.library.modules[module.id.0 as usize].dir;
        if library_of(all, dir).is_some_and(|l| l.root != library.root) {
            module.tests = None;
        }
    }
    Ok((run_settled_tests(&settled, fuel), settled))
}

/// Runs a settled library's tests, those of its modules that still have
/// them, as [`test_library`] and the web playground do. With `fuel`, the
/// top-level code and each test may take that many evaluation steps; a
/// test that runs out fails alone.
pub fn run_settled_tests(settled: &Settled, fuel: Option<u64>) -> LibraryTests {
    let mut diagnostics = String::new();
    write_located_diagnostics(settled, &mut diagnostics);
    let mut json_diagnostics = String::new();
    write_json_diagnostics(settled, &mut json_diagnostics);
    let errors = (settled.library.diagnostics.iter())
        .filter(|d| d.level == Level::Error)
        .count();
    let defined = settled
        .modules
        .iter()
        .filter_map(|m| m.tests)
        .map(|run| {
            // `r#([t#test, …])`: the list's items after its callee.
            let list = settled.library.tree.children(run)[1];
            settled.library.tree.children(list).len() - 1
        })
        .sum();
    let run = run_tests(settled, fuel);
    let (mut output, mut report) = (Vec::new(), Vec::new());
    let mut in_report = false;
    for line in run.output {
        in_report |= line == "<testsuites>";
        if in_report {
            in_report = line != "</testsuites>";
            report.push(line);
        } else {
            output.push(line);
        }
    }
    LibraryTests {
        results: parse_junit(&report),
        defined,
        output,
        steps: run.test_steps,
        failure: run.failure,
        diagnostics,
        json_diagnostics,
        errors,
    }
}

impl LibraryTests {
    /// The steps a test took, by its name, if it ran on the interpreter.
    pub fn steps_of(&self, test: &str) -> Option<u64> {
        (self.steps.iter())
            .find(|(name, _)| name == test)
            .map(|s| s.1)
    }
}

/// Each test case in JUnit XML as std's `reportTestResults` writes it, a
/// tag a line, with its failure message, if any.
pub fn parse_junit(lines: &[String]) -> Vec<(String, Option<String>)> {
    let mut results: Vec<(String, Option<String>)> = Vec::new();
    for line in lines {
        let line = line.trim_start();
        if line.starts_with("<testcase ") {
            results.push((attribute(line, "name").unwrap_or_default(), None));
        } else if line.starts_with("<failure ") {
            if let Some(last) = results.last_mut() {
                last.1 = Some(attribute(line, "message").unwrap_or_default());
            }
        }
    }
    results
}

/// An attribute's value in a tag, quoted with `'`, unescaped.
fn attribute(tag: &str, name: &str) -> Option<String> {
    let start = tag.find(&format!(" {name}='"))? + name.len() + 3;
    let end = start + tag[start..].find('\'')?;
    Some(
        tag[start..end]
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&#34;", "\"")
            .replace("&amp;", "&"),
    )
}

/// Diagnostics as `path:line:column: level: message`, a line each, with
/// the column in characters, both from 1.
pub fn write_located_diagnostics(s: &Settled, out: &mut String) {
    for d in &s.library.diagnostics {
        write_located_diagnostic(s, d, out);
    }
}

/// One diagnostic as [`write_located_diagnostics`] writes each.
pub fn write_located_diagnostic(s: &Settled, d: &Diagnostic, out: &mut String) {
    use std::fmt::Write;
    let level = format!("{:?}", d.level).to_lowercase();
    let (file, offset) = s.library.locate(d.span.start);
    let file = &s.library.files[file.0 as usize];
    let source = file.parsed.source();
    let before = source.get(..offset as usize).unwrap_or(source);
    let line = before.matches('\n').count() + 1;
    let column = before.rsplit('\n').next().unwrap_or("").chars().count() + 1;
    let path = &file.path;
    let _ = writeln!(out, "{path}:{line}:{column}: {level}: {}", d.message.text());
}

/// The diagnostics a command has reported, across the libraries it
/// settles. A library is settled with the modules it imports from other
/// libraries, and their diagnostics, so each is reported once, by the
/// first settle that finds it, and counted under the library whose file
/// it's in, not under each library importing it. (Settling each library
/// once, giving importers its interface, would avoid finding them again;
/// notes/library-cache.md.)
#[derive(Default)]
pub struct Reported {
    /// Reports hints too, as `-v` asks; else they're skipped.
    hints: bool,
    /// Each reported: its file, span in the file, and message.
    seen: HashSet<(String, u32, u32, String)>,
    /// Each library with errors, by name, and how many, in the order
    /// first reported.
    pub errors: Vec<(String, usize)>,
}

impl Reported {
    /// None reported yet; hints among them only with `hints` (`-v`).
    pub fn new(hints: bool) -> Self {
        Reported {
            hints,
            ..Reported::default()
        }
    }

    /// `settled`'s diagnostics that aren't reported yet, as text or JSON
    /// records, a line each, counting their errors under the libraries
    /// among `all` that hold their files, or else under `library`, the
    /// one settled.
    pub fn new_diagnostics(
        &mut self,
        settled: &Settled,
        library: &LibrarySources,
        all: &[LibrarySources],
        log: LogFormat,
    ) -> String {
        let mut text = String::new();
        let lib = &settled.library;
        for d in &lib.diagnostics {
            if d.level == Level::Hint && !self.hints {
                continue;
            }
            let (file, start) = lib.locate(d.span.start);
            let (_, end) = lib.locate(d.span.end.max(d.span.start));
            let file = &lib.files[file.0 as usize];
            let key = (file.path.clone(), start, end, d.message.text());
            if !self.seen.insert(key) {
                continue;
            }
            match log {
                LogFormat::Text => write_located_diagnostic(settled, d, &mut text),
                LogFormat::Json => write_json_diagnostic(settled, d, &mut text),
            }
            if d.level != Level::Error {
                continue;
            }
            let dir = &lib.modules[file.module.0 as usize].dir;
            let owner = &library_of(all, dir).unwrap_or(library).config.name;
            match self.errors.iter_mut().find(|(name, _)| name == owner) {
                Some((_, count)) => *count += 1,
                None => self.errors.push((owner.clone(), 1)),
            }
        }
        text
    }

    /// The errors reported in a library's own files.
    pub fn errors_in(&self, library: &str) -> usize {
        (self.errors.iter())
            .find(|(name, _)| name == library)
            .map_or(0, |(_, count)| *count)
    }
}

/// Tests across libraries, counted as Kotlin's `TestTally` counts them.
#[derive(Debug, Default, PartialEq)]
pub struct Tally {
    pub run: usize,
    pub failed: usize,
    pub defined: usize,
    pub not_run: usize,
    pub unexpected: usize,
}

impl Tally {
    pub fn add(&mut self, tests: &LibraryTests) {
        let run = tests.results.len();
        self.run += run;
        self.failed += tests.results.iter().filter(|r| r.1.is_some()).count();
        self.defined += tests.defined;
        self.not_run += tests.defined.saturating_sub(run);
        self.unexpected += run.saturating_sub(tests.defined);
    }

    /// Whether every test declared ran and passed.
    pub fn all_passed(&self) -> bool {
        self.failed == 0 && self.not_run == 0 && self.unexpected == 0
    }

    /// Kotlin's summary as a JSON record, its template's name and its
    /// values, as `--logformat json` writes it.
    pub fn summary_record(&self) -> String {
        let passed = self.run - self.failed;
        let total = self.defined;
        let (template, values): (&str, Vec<Value>) = match (self.not_run, self.unexpected) {
            (0, 0) => ("TestSummaryAllRun", vec![passed.into(), total.into()]),
            (n, 0) => (
                "TestSummaryNotAllRun",
                vec![passed.into(), total.into(), n.into()],
            ),
            (0, u) => (
                "TestSummaryUnexpected",
                vec![passed.into(), total.into(), u.into()],
            ),
            (n, u) => (
                "TestSummaryNotRunAndUnexpected",
                vec![passed.into(), total.into(), n.into(), u.into()],
            ),
        };
        record("summary", template, None, &values, &self.summary())
    }

    /// Adds another tally, as Kotlin sums its backends'.
    pub fn sum(&mut self, other: &Tally) {
        self.run += other.run;
        self.failed += other.failed;
        self.defined += other.defined;
        self.not_run += other.not_run;
        self.unexpected += other.unexpected;
    }

    /// Kotlin's summary line: `Tests passed: 28 of 29 (1 not run)`.
    pub fn summary(&self) -> String {
        let passed = self.run - self.failed;
        let total = self.defined;
        match (self.not_run, self.unexpected) {
            (0, 0) => format!("Tests passed: {passed} of {total}"),
            (n, 0) => format!("Tests passed: {passed} of {total} ({n} not run)"),
            (0, u) => format!("Tests passed: {passed} of {total} ({u} unexpected)"),
            (n, u) => format!("Tests passed: {passed} of {total} ({n} not run, {u} unexpected)"),
        }
    }
}
