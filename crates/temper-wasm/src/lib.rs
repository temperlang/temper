//! A wasm entry point for Temper, for the web playground
//! (`site/playground/`) and for measuring how big Temper is as wasm
//! (`compat/measure_size.py`).
//!
//! `compile` is the playground's: it settles source once and gives, as
//! JSON, its diagnostics with their spans, what it logs and its tests'
//! results from the interpreter, and each backend's files. The
//! playground calls it in two halves, `analyze` for the diagnostics
//! first, then `finish` for the rest, and `analyze` keeps the settled
//! source for the editor's queries, `hover`, `definition`,
//! `completions`, `references`, `rename`, and `signature_help`, which
//! [`temper_analysis`] answers; `backends`
//! lists the backends built in, `version` names the build's version
//! (as `temper --version`, from git when it was built), and
//! `std_library` gives std's own
//! output from one, for running generated tests. The older calls each do
//! one thing: `parse` returns the parse tree and the grammar step's tree
//! as text, `run` settles and runs source, with the std modules it
//! imports, built in, returning its diagnostics and what it logs, and
//! `generate` translates it with one backend, returning the files as
//! JSON. JavaScript calls `alloc` for a buffer, writes UTF-8 source into
//! it, calls one of them, and reads the result from `output_ptr` and
//! `output_len`:
//!
//! ```js
//! const bytes = new TextEncoder().encode(source);
//! const ptr = exports.alloc(bytes.length);
//! new Uint8Array(exports.memory.buffer, ptr, bytes.length).set(bytes);
//! exports.parse(ptr, bytes.length, markdown ? 1 : 0);
//! const out = new Uint8Array(exports.memory.buffer, exports.output_ptr(), exports.output_len());
//! console.log(new TextDecoder().decode(out));
//! ```
//!
//! [`repl`] keeps a REPL session for the playground's REPL tab:
//! `repl_start`, `repl_input`, `repl_complete`, and `repl_reset`.
//!
//! The module imports two functions from the page, in `env`
//! ([`temper_os::host`]): `host_log`, which the REPL streams logged
//! lines through, and `host_now`, the clock, for std's `temporal`.
//! Instantiating it needs both, for every export.
//!
//! Settling aside, it compiles as `temper` does, through
//! `temper-driver` ([`temper_driver::checked`], [`temper_driver::with_std`],
//! [`temper_driver::translate`]). Each backend is a cargo feature, all on
//! by default, as in `temper`.
//!
//! Its only unsafe code is its exports' `no_mangle` attribute, each
//! allowed by name (`#[allow(unsafe_code)]`), as the crate denies it
//! otherwise. An unmangled name could clash with another symbol of the
//! same name; the wasm module has no others, and natively only tests
//! link these. The rest of the workspace forbids unsafe code, but
//! `temper-os`.

use std::cell::{OnceCell, RefCell};
use std::fmt::Write;
use std::sync::OnceLock;
use temper_analysis::{Analysis, DeclKind, Position, byte_offset};
use temper_driver::Registry;
use temper_frontend::library::FileId;
use temper_frontend::library_tests::{Tally, run_settled_tests};
use temper_frontend::project::load_imports;
use temper_frontend::settle::{Settled, settle};
use temper_frontend::sources::Sources;
use temper_frontend::{interp, library::Library, print::write_diagnostics};
use temper_syntax::ast::build;
use temper_syntax::tree::Span;
use temper_syntax::{
    lex::SourceKind, parse::parse as parse_source, print::write_ast, print::write_tree,
};
use temper_tmpl::Program;

pub mod repl;

thread_local! {
    static INPUT: RefCell<Vec<u8>> = const { RefCell::new(Vec::new()) };
    static OUTPUT: RefCell<String> = const { RefCell::new(String::new()) };
}

/// A buffer of `len` bytes for the next input, replacing the last one.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn alloc(len: usize) -> *mut u8 {
    INPUT.with_borrow_mut(|input| {
        *input = vec![0; len];
        input.as_mut_ptr()
    })
}

/// Parses the input buffer as UTF-8 Temper source, or literate markdown if
/// `markdown` is nonzero, and keeps the parse tree and then the grammar
/// step's tree as text for `output_ptr`, with a blank line between.
/// Invalid UTF-8 is replaced rather than rejected.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn parse(ptr: *const u8, len: usize, markdown: u32) {
    with_source(ptr, len, markdown, |source, kind, out| {
        let parsed = parse_source(source, kind);
        write_tree(&parsed, 100, out);
        out.push('\n');
        write_ast(&build(&parsed), 100, out);
    });
}

/// Settles the input buffer as a library, with the std modules it
/// imports, and runs it, keeping its diagnostics, then what it logs, then
/// how it failed, if it did. The
/// interpreter's recursion needs more than wasm's default 1 MiB stack for
/// deeply nested code.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn run(ptr: *const u8, len: usize, markdown: u32) {
    with_source(ptr, len, markdown, |source, kind, out| {
        let mut library = Library::new();
        library.add_file("main.temper", source, kind);
        load_imports(&Sources::memory(&[]), &mut library);
        let settled = settle(library);
        write_diagnostics(&settled, out);
        let run = interp::run(&settled, Some(interp::FUEL));
        for line in run.output {
            out.push_str(&line);
            out.push('\n');
        }
        if let Some(failure) = run.failure {
            out.push_str(&failure);
            out.push('\n');
        }
    });
}

/// Translates the input buffer, as a library named `main` from a file
/// `main.temper` (or `main.temper.md`), with the std modules it imports,
/// by the backend numbered by its place in [`temper_driver::KNOWN`] (`0` for
/// C++, …, `6` for Rust), through the driver's compile path, as `tempest
/// build` does, and keeps the
/// generated files as JSON, `{"files": [{"path": ..., "text":
/// ...}, ...]}`, laid out as Kotlin's `temper.out/LANG` under it, or
/// `{"error": ...}` with what went wrong. JavaScript's module comes with
/// its source map, which names the source `../main.temper` (or `.md`).
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn generate(ptr: *const u8, len: usize, markdown: u32, backend: u32) {
    with_source(ptr, len, markdown, |source, kind, out| {
        let json = match translate(source, kind, backend) {
            Ok(files) => files_json(&files),
            Err(error) => format!("{{\"error\":{}}}", json_string(&error)),
        };
        out.push_str(&json);
    });
}

/// `compile`'s `options` bit for literate markdown source.
pub const MARKDOWN: u32 = 1;
/// `compile`'s `options` bit to run the library in the interpreter.
pub const RUN: u32 = 2;
/// `compile`'s `options` bit to also run its tests, if it runs.
pub const TESTS: u32 = 4;

/// The backends this build has, alphabetically, as JSON,
/// `["py", ...]`, for `compile`'s `backends` mask, whose bit `1 << i` is
/// the `i`th of them.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn backends() {
    let names: Vec<String> = built_in().map(json_string).collect();
    OUTPUT.with_borrow_mut(|out| *out = format!("[{}]", names.join(",")));
}

/// This build's version, with its commit, as a JSON string, as
/// `temper --version` says it: `"0.7.0-alpha.0.123 (3f2b063)"`. Output
/// still names the version with no commits counted
/// ([`temper_tmpl::version::current`]), as tests' does.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn version() {
    let text = json_string(&temper_driver::version::describe());
    OUTPUT.with_borrow_mut(|out| *out = text);
}

/// The backends this build has, alphabetically.
pub fn built_in() -> impl Iterator<Item = &'static str> {
    registry().all().iter().map(|b| b.name)
}

/// The backends this build has ([`Registry`]).
fn registry() -> &'static Registry {
    static REGISTRY: OnceLock<Registry> = OnceLock::new();
    REGISTRY.get_or_init(Registry::built_in)
}

/// Settles the input buffer once, as `generate` does, and keeps all a
/// playground shows as one JSON object: its diagnostics, what it logs
/// if `options` has [`RUN`] (and its tests' report, with [`TESTS`]),
/// and what each backend in the `backends` mask (bits by [`backends`])
/// generates:
///
/// ```json
/// {"diagnostics": [{"level": "error", "message": "...", "file": "main.temper",
///                   "from": 4, "to": 9, "line": 1, "column": 5}],
///  "run": {"output": ["..."], "failure": null,
///          "tests": [{"name": "...", "failure": null}], "defined": 1,
///          "summary": "Tests passed: 1 of 1"},
///  "backends": {"py": {"files": [{"path": "...", "text": "..."}]},
///               "rust": {"error": "..."}}}
/// ```
///
/// `from` and `to` count UTF-16 code units in the main file, as
/// JavaScript strings and editors do, and `line` and `column` count
/// from 1; diagnostics in other files (std's) have no position.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn compile(ptr: *const u8, len: usize, options: u32, backends: u32) {
    with_source(ptr, len, options & MARKDOWN, |source, kind, out| {
        compile_into(source, kind, options, backends, out);
    });
}

/// What `compile` does, into `out`: [`analyze_into`], then
/// [`finish_into`] over what it kept, as one object.
pub fn compile_into(source: &str, kind: SourceKind, options: u32, mask: u32, out: &mut String) {
    analyze_into(source, kind, out);
    out.pop();
    let mut rest = String::new();
    finish_into(options, mask, &mut rest);
    out.push(',');
    out.push_str(&rest[1..]);
}

/// Settles the input buffer, as `compile` does, and keeps it for
/// [`finish`] and the editor queries ([`hover`], [`definition`], and
/// [`completions`]), giving just its diagnostics, as
/// `{"diagnostics": [...]}` in `compile`'s shape. The playground shows
/// these first, before the run and the backends' output.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn analyze(ptr: *const u8, len: usize, options: u32) {
    with_source(ptr, len, options & MARKDOWN, analyze_into);
}

/// What `analyze` does, into `out`.
pub fn analyze_into(source: &str, kind: SourceKind, out: &mut String) {
    let file = match kind {
        SourceKind::Markdown => "main.temper.md",
        _ => "main.temper",
    };
    let mut library = Library::new();
    // The backends' tabs show all that's written, every top-level
    // declaration kept, not only what an export would reach.
    library.keep_all = true;
    library.add_file(file, source, kind);
    load_imports(&Sources::memory(&[]), &mut library);
    let analysis = Analysis::new(settle(library));
    out.push_str("{\"diagnostics\":[");
    diagnostics_json(&analysis, source, out);
    out.push_str("]}");
    KEPT.with_borrow_mut(|kept| {
        *kept = Some(Kept {
            analysis,
            source: source.to_string(),
        })
    });
}

/// The rest of `compile` over what [`analyze`] kept: what it logs if
/// `options` has [`RUN`] (and its tests' report, with [`TESTS`]), and
/// what each backend in the `backends` mask generates, as `{"run": ...,
/// "backends": ...}`.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn finish(options: u32, mask: u32) {
    OUTPUT.with_borrow_mut(|out| {
        out.clear();
        finish_into(options, mask, out);
    });
}

/// What `finish` does, into `out`.
pub fn finish_into(options: u32, mask: u32, out: &mut String) {
    KEPT.with_borrow(|kept| match kept {
        Some(kept) => finish_settled(&kept.analysis.settled, options, mask, out),
        None => out.push_str("{\"backends\":{}}"),
    });
}

fn finish_settled(settled: &Settled, options: u32, mask: u32, out: &mut String) {
    out.push('{');
    if options & RUN != 0 {
        let (output, failure, tests) = if options & TESTS != 0 {
            let tests = run_settled_tests(settled, Some(interp::FUEL));
            let mut tally = Tally::default();
            tally.add(&tests);
            (
                tests.output.clone(),
                tests.failure.clone(),
                Some((tests, tally)),
            )
        } else {
            let run = interp::run(settled, Some(interp::FUEL));
            (run.output, run.failure, None)
        };
        let lines: Vec<String> = output.iter().map(|l| json_string(l)).collect();
        let failure = failure.as_deref().map_or("null".into(), json_string);
        let _ = write!(
            out,
            "\"run\":{{\"output\":[{}],\"failure\":{failure}",
            lines.join(",")
        );
        if let Some((tests, tally)) = tests {
            let results: Vec<String> = (tests.results.iter())
                .map(|(name, failure)| {
                    let failure = failure.as_deref().map_or("null".into(), json_string);
                    format!("{{\"name\":{},\"failure\":{failure}}}", json_string(name))
                })
                .collect();
            let _ = write!(
                out,
                ",\"tests\":[{}],\"defined\":{},\"summary\":{}",
                results.join(","),
                tests.defined,
                json_string(&tally.summary())
            );
        }
        out.push_str("},");
    }
    out.push_str("\"backends\":{");
    let chosen: Vec<&str> = (built_in().enumerate())
        .filter(|&(i, _)| i < 32 && mask & (1 << i) != 0)
        .map(|(_, name)| name)
        .collect();
    if !chosen.is_empty() {
        let program = program(settled);
        for (i, name) in chosen.into_iter().enumerate() {
            if i > 0 {
                out.push(',');
            }
            let generated = program.clone().and_then(|p| generate_with(p, name));
            let json = match generated {
                Ok(files) => files_json(&files),
                Err(error) => format!("{{\"error\":{}}}", json_string(&error)),
            };
            let _ = write!(out, "{}:{json}", json_string(name));
        }
    }
    out.push_str("}}");
}

/// What [`analyze`] kept: the main file's analysis and its source, for
/// converting UTF-16 offsets.
struct Kept {
    analysis: Analysis<'static>,
    source: String,
}

thread_local! {
    static KEPT: RefCell<Option<Kept>> = const { RefCell::new(None) };
}

/// What hovering at UTF-16 offset `at` of the kept source shows, as
/// `{"from": 4, "to": 9, "name": "x", "kind": "let", "type": "Int32",
/// "doc": null}`, with `name` and `kind` null if it's not a name, or
/// `null` if there's nothing there.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn hover(at: u32) {
    query(|kept, out| {
        let offset = byte_offset(&kept.source, at as usize) as u32;
        let Some(h) = kept.analysis.hover(FileId(0), offset) else {
            return out.push_str("null");
        };
        let opt = |s: Option<&str>| s.map_or("null".into(), json_string);
        let (from, to) = utf16_span(&kept.source, h.span);
        let _ = write!(
            out,
            "{{\"from\":{from},\"to\":{to},\"name\":{},\"kind\":{},\"type\":{},\"doc\":{}}}",
            opt(h.name.as_deref()),
            opt(h.kind.map(DeclKind::label)),
            opt(h.ty.as_deref()),
            opt(h.doc.as_deref()),
        );
    });
}

/// Where the name at UTF-16 offset `at` of the kept source is declared,
/// as `{"from": 4, "to": 9}` in the same source, or in std or core, also
/// with the file's path and its text, `{"file":
/// "std/regex/regex.temper.md", "text": "…", "from": …, "to": …}` or
/// `{"file": "core/core.temper", …}` for a builtin, or `null` for a
/// builtin core doesn't declare.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn definition(at: u32) {
    query(|kept, out| {
        let offset = byte_offset(&kept.source, at as usize) as u32;
        let found = kept.analysis.definition(FileId(0), offset);
        let library = &kept.analysis.settled.library;
        match found.map(|d| (d, library.files.get(d.file.0 as usize))) {
            Some((d, _)) if d.file == FileId(0) => {
                let (from, to) = utf16_span(&kept.source, d.span);
                let _ = write!(out, "{{\"from\":{from},\"to\":{to}}}");
            }
            Some((d, Some(file))) if kept.analysis.is_std(d.file) => {
                let text = file.parsed.source();
                let (from, to) = utf16_span(text, d.span);
                let path = file.path.replacen(temper_frontend::stdlib::DIR, "std", 1);
                let _ = write!(
                    out,
                    "{{\"file\":{},\"text\":{},\"from\":{from},\"to\":{to}}}",
                    json_string(&path),
                    json_string(text)
                );
            }
            Some(_) => out.push_str("null"),
            None => match kept.analysis.core_definition(FileId(0), offset) {
                Some(core) => {
                    let (from, to) = utf16_span(core.source, core.span);
                    let _ = write!(
                        out,
                        "{{\"file\":{},\"text\":{},\"from\":{from},\"to\":{to}}}",
                        json_string(&format!("core/{}", core.path)),
                        json_string(core.source)
                    );
                }
                None => out.push_str("null"),
            },
        }
    });
}

/// Each use of what the name at UTF-16 offset `at` names in the kept
/// source, with its declaration, as `[{"from": 4, "to": 9}, …]`.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn references(at: u32) {
    query(|kept, out| {
        let offset = byte_offset(&kept.source, at as usize) as u32;
        let spans: Vec<String> = (kept.analysis.references(FileId(0), offset, true).iter())
            .filter(|l| l.file == FileId(0))
            .map(|l| {
                let (from, to) = utf16_span(&kept.source, l.span);
                format!("{{\"from\":{from},\"to\":{to}}}")
            })
            .collect();
        let _ = write!(out, "[{}]", spans.join(","));
    });
}

/// Renames what the name at UTF-16 offset `at` of the kept source names
/// to the input buffer's text (from `alloc`): the edits, as `{"edits":
/// [{"from": 4, "to": 9, "text": "y"}, …]}`, or `{"error": "why"}`. With
/// an empty name, only checks: `{"from": 4, "to": 9, "name": "x"}`.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn rename(at: u32, ptr: *const u8, len: usize) {
    let new_name = input_text(ptr, len);
    query(|kept, out| {
        let offset = byte_offset(&kept.source, at as usize) as u32;
        let analysis = &kept.analysis;
        if new_name.is_empty() {
            let _ = match analysis.prepare_rename(FileId(0), offset) {
                Ok((span, name)) => {
                    let (from, to) = utf16_span(&kept.source, span);
                    let name = json_string(&name);
                    write!(out, "{{\"from\":{from},\"to\":{to},\"name\":{name}}}")
                }
                Err(why) => write!(out, "{{\"error\":{}}}", json_string(&why)),
            };
            return;
        }
        let _ = match analysis.rename(FileId(0), offset, &new_name) {
            // The playground has one file; anything else is std's, which
            // renaming refuses.
            Ok(edits) => {
                let edits: Vec<String> = (edits.iter())
                    .filter(|e| e.file == FileId(0))
                    .map(|e| {
                        let (from, to) = utf16_span(&kept.source, e.span);
                        let text = json_string(&e.text);
                        format!("{{\"from\":{from},\"to\":{to},\"text\":{text}}}")
                    })
                    .collect();
                write!(out, "{{\"edits\":[{}]}}", edits.join(","))
            }
            Err(why) => write!(out, "{{\"error\":{}}}", json_string(&why)),
        };
    });
}

/// The signature of the call whose arguments UTF-16 offset `at` of the
/// kept source is among, as `{"label": "f(a: Int32): Void", "params":
/// ["a: Int32"], "active": 0, "doc": null}`, or `null`.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn signature_help(at: u32) {
    query(|kept, out| {
        let offset = byte_offset(&kept.source, at as usize) as u32;
        let Some(help) = kept.analysis.signature_help(FileId(0), offset) else {
            return out.push_str("null");
        };
        let params: Vec<String> = help.params.iter().map(|p| json_string(p)).collect();
        let _ = write!(
            out,
            "{{\"label\":{},\"params\":[{}],\"active\":{},\"doc\":{}}}",
            json_string(&help.label),
            params.join(","),
            help.active,
            help.doc.as_deref().map_or("null".into(), json_string)
        );
    });
}

/// The names that may go at UTF-16 offset `at` of the kept source, as
/// `[{"name": "x", "kind": "let", "type": "Int32"}, ...]`, innermost
/// first; after `x.`, `x`'s members.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn completions(at: u32) {
    query(|kept, out| {
        let offset = byte_offset(&kept.source, at as usize) as u32;
        let items: Vec<String> = (kept.analysis.completions(FileId(0), offset).iter())
            .map(|c| {
                format!(
                    "{{\"name\":{},\"kind\":{},\"type\":{}}}",
                    json_string(&c.name),
                    json_string(c.kind.label()),
                    c.detail.as_deref().map_or("null".into(), json_string)
                )
            })
            .collect();
        let _ = write!(out, "[{}]", items.join(","));
    });
}

/// Runs an editor query over what [`analyze`] kept, into the output, or
/// gives `null` if nothing's kept.
fn query(f: impl FnOnce(&Kept, &mut String)) {
    OUTPUT.with_borrow_mut(|out| {
        out.clear();
        KEPT.with_borrow(|kept| match kept {
            Some(kept) => f(kept, out),
            None => out.push_str("null"),
        });
    });
}

/// A span of bytes as UTF-16 offsets.
fn utf16_span(source: &str, span: Span) -> (usize, usize) {
    let from = Position::of(source, span.start as usize).utf16;
    let to = Position::of(source, span.end as usize).utf16;
    (from, to)
}

/// What the backend that's `backend`th of [`backends`] generates for
/// std, as `generate`'s JSON, translated once and kept, for running a
/// library's tests with std's `testing` in its own language.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn std_library(backend: u32) {
    let json = match built_in().nth(backend as usize) {
        Some(name) => match std_files(name) {
            Ok(files) => files_json(&files),
            Err(error) => format!("{{\"error\":{}}}", json_string(&error)),
        },
        _ => format!(
            "{{\"error\":{}}}",
            json_string(&format!("no backend {backend}"))
        ),
    };
    OUTPUT.with_borrow_mut(|out| *out = json);
}

/// The analysis's diagnostics as JSON objects, comma-separated.
fn diagnostics_json(analysis: &Analysis, source: &str, out: &mut String) {
    let library = &analysis.settled.library;
    for (i, d) in analysis.diagnostics().into_iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        let level = format!("{:?}", d.level).to_lowercase();
        let path = library.files.get(d.file.0 as usize).map_or("", |f| &f.path);
        let _ = write!(
            out,
            "{{\"level\":{},\"message\":{},\"file\":{}",
            json_string(&level),
            json_string(&d.message),
            json_string(path)
        );
        if d.file == FileId(0) {
            let at = Position::of(source, d.span.start as usize);
            let to = Position::of(source, d.span.end as usize);
            let _ = write!(
                out,
                ",\"from\":{},\"to\":{},\"line\":{},\"column\":{}",
                at.utf16, to.utf16, at.line, at.column
            );
        }
        out.push('}');
    }
}

/// Generated files, each a path and text.
pub type Files = Vec<(String, String)>;

/// What `generate` does, before the JSON.
pub fn translate(source: &str, kind: SourceKind, backend: u32) -> Result<Files, String> {
    let Some(&name) = temper_driver::KNOWN.get(backend as usize) else {
        return Err(format!("no backend {backend}"));
    };
    let file = match kind {
        SourceKind::Markdown => "main.temper.md",
        _ => "main.temper",
    };
    let mut library = Library::new();
    library.add_file(file, source, kind);
    load_imports(&Sources::memory(&[]), &mut library);
    generate_with(program(&settle(library))?, name)
}

thread_local! {
    /// std as tmpl, settled once and kept, since it never changes and
    /// settling it takes most of a translation's time.
    static STD: OnceCell<Result<&'static Program, String>> = const { OnceCell::new() };
}

/// std as tmpl, settled the first time it's asked for.
fn std_program() -> Result<&'static Program, String> {
    STD.with(|std| {
        let std = std.get_or_init(|| {
            (temper_frontend::stdlib::program(&Sources::memory(&[])))
                .map(|p| &*Box::leak(Box::new(p)))
                .map_err(|e| format!("std: {e}"))
        });
        std.clone()
    })
}

/// std's own files from a backend that's built in.
fn std_files(name: &str) -> Result<Files, String> {
    generate_with(std_program()?.clone(), name)
}

/// A settled library as tmpl, checked, or what went wrong, after its
/// diagnostics.
fn program(settled: &Settled) -> Result<Program, String> {
    temper_driver::checked(settled, "main").map_err(|e| {
        let mut diagnostics = String::new();
        write_diagnostics(settled, &mut diagnostics);
        format!("{diagnostics}{e}")
    })
}

/// Generates code from tmpl with a backend, by name, with std's names
/// where it uses std; JavaScript's source map names the source from the
/// package's directory.
fn generate_with(program: Program, name: &str) -> Result<Files, String> {
    let backend =
        (registry().get(name)).ok_or_else(|| format!("built without the {name} backend"))?;
    let program = temper_driver::with_std(program, backend, std_program)?;
    temper_driver::translate(&program, backend, Some("../"))
}

/// Files as `generate`'s JSON.
pub fn files_json(files: &[(String, String)]) -> String {
    let files: Vec<String> = (files.iter())
        .map(|(path, text)| {
            format!(
                "{{\"path\":{},\"text\":{}}}",
                json_string(path),
                json_string(text)
            )
        })
        .collect();
    format!("{{\"files\":[{}]}}", files.join(","))
}

fn json_string(s: &str) -> String {
    let mut out = String::from("\"");
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

/// The input buffer's first `len` bytes as text, if `ptr` is the buffer
/// `alloc` gave, or else nothing. Invalid UTF-8 is replaced.
fn input_text(ptr: *const u8, len: usize) -> String {
    INPUT.with_borrow(|input| match ptr == input.as_ptr() && len <= input.len() {
        true => String::from_utf8_lossy(&input[..len]).into_owned(),
        false => String::new(),
    })
}

/// Reads the input buffer as source and fills the output.
fn with_source(
    ptr: *const u8,
    len: usize,
    markdown: u32,
    f: impl FnOnce(&str, SourceKind, &mut String),
) {
    let kind = if markdown != 0 {
        SourceKind::Markdown
    } else {
        SourceKind::Standalone
    };
    INPUT.with_borrow(|input| {
        // Only our own buffer from `alloc` is accepted.
        let bytes = if ptr == input.as_ptr() && len <= input.len() {
            &input[..len]
        } else {
            &[][..]
        };
        let source = String::from_utf8_lossy(bytes);
        OUTPUT.with_borrow_mut(|out| {
            out.clear();
            f(&source, kind, out);
        });
    });
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn output_ptr() -> *const u8 {
    OUTPUT.with_borrow(|out| out.as_ptr())
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn output_len() -> usize {
    OUTPUT.with_borrow(|out| out.len())
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Runs source as JavaScript would, natively, on a thread with room
    /// for the interpreter's recursion, giving the output.
    fn run_source(source: &'static str) -> String {
        std::thread::Builder::new()
            .stack_size(64 << 20)
            .spawn(move || {
                let ptr = alloc(source.len());
                INPUT.with_borrow_mut(|input| input.copy_from_slice(source.as_bytes()));
                run(ptr, source.len(), 0);
                assert_eq!(output_len(), OUTPUT.with_borrow(|out| out.len()));
                OUTPUT.with_borrow(|out| out.clone())
            })
            .unwrap()
            .join()
            .unwrap()
    }

    /// The functional tests tmpl's translation covers, as the backends'
    /// own tests list them, whose goldens are the native output.
    use temper_be_testkit::COVERED;

    /// Generates code as JavaScript would, natively, on a thread with
    /// room for the settle loop's recursion.
    fn generate_source(source: String, backend: u32) -> String {
        std::thread::Builder::new()
            .stack_size(64 << 20)
            .spawn(move || {
                let ptr = alloc(source.len());
                INPUT.with_borrow_mut(|input| input.copy_from_slice(source.as_bytes()));
                generate(ptr, source.len(), 1, backend);
                assert_eq!(output_len(), OUTPUT.with_borrow(|out| out.len()));
                OUTPUT.with_borrow(|out| out.clone())
            })
            .unwrap()
            .join()
            .unwrap()
    }

    /// Each built-in backend's output for subset A matches its native
    /// golden (in `crates/temper-be-*/tests/goldens/`), and `generate`
    /// gives it as JSON.
    #[test]
    fn generates_as_natively() {
        let crates = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
        for path in COVERED {
            let test = temper_funtests::test(path).unwrap();
            let [source] = &test.sources()[..] else {
                panic!("{path}: not one file");
            };
            for (backend, &name) in temper_driver::KNOWN.iter().enumerate() {
                let json = generate_source(source.text.clone(), backend as u32);
                if registry().get(name).is_none() {
                    let error = format!("built without the {name} backend");
                    assert_eq!(json, format!("{{\"error\":{}}}", json_string(&error)));
                    continue;
                }
                let text = source.text.clone();
                let files = std::thread::Builder::new()
                    .stack_size(64 << 20)
                    .spawn(move || translate(&text, SourceKind::Markdown, backend as u32))
                    .unwrap()
                    .join()
                    .unwrap()
                    .unwrap_or_else(|e| panic!("{path} {name}: {e}"));
                assert_eq!(json, files_json(&files), "{path} {name}");
                let (module, extension) = match name {
                    "py" => ("main/main/main.py", "py"),
                    "rust" => ("main/src/mod.rs", "rs"),
                    "java" => ("main/src/main/java/main/MainGlobal.java", "java"),
                    "csharp" => ("main/src/MainGlobal.cs", "cs"),
                    "cpp" => ("main/init.cpp", "cpp"),
                    "lua" => ("main/init.lua", "lua"),
                    _ => ("main/main.internal.js", "js"),
                };
                let text = &files.iter().find(|(p, _)| p == module).unwrap().1;
                let text = text
                    .strip_suffix("//# sourceMappingURL=main.internal.js.map\n")
                    .unwrap_or(text);
                let golden = (crates.join(format!("temper-be-{name}/tests/goldens")))
                    .join(format!("{}.{extension}", path.replace('/', "-")));
                let mut expected = std::fs::read_to_string(&golden).unwrap();
                if name == "java" {
                    // Java's class names its package, class, and logger
                    // for the library, here `main`.
                    let package = test.name().replace('-', "_");
                    let class: String = (package.split('_'))
                        .map(|w| w[..1].to_uppercase() + &w[1..])
                        .collect();
                    expected = expected
                        .replace(&format!("package {package};"), "package main;")
                        .replace(&format!("{class}Global"), "MainGlobal")
                        .replace(&format!("getLogger(\"{package}\")"), "getLogger(\"main\")");
                }
                if name == "csharp" {
                    // C#'s class names its namespace, class, and console
                    // for the library, here `Main`.
                    let root: String = (test.name().split('-'))
                        .map(|w| w[..1].to_uppercase() + &w[1..])
                        .collect();
                    expected = expected
                        .replace(&format!("namespace {root};"), "namespace Main;")
                        .replace(&format!("{root}Global"), "MainGlobal")
                        .replace(
                            &format!("CreateConsole(\"{root}\")"),
                            "CreateConsole(\"Main\")",
                        );
                }
                if name == "cpp" {
                    // C++'s golden holds the header and the module, in
                    // the library's namespace and directory, here `main`.
                    let header = &files.iter().find(|(p, _)| p == "main/init.hpp").unwrap().1;
                    let library = test.name();
                    let namespace = library.replace('-', "_");
                    expected = expected
                        .replace(&format!("namespace {namespace} "), "namespace main ")
                        .replace(&format!("namespace {namespace}\n"), "namespace main\n")
                        .replace(&format!(" {namespace}::"), " main::")
                        .replace(&format!("({namespace}::"), "(main::")
                        .replace(&format!("<{library}/init.hpp>"), "<main/init.hpp>");
                    let both = format!("// init.hpp\n{header}\n// init.cpp\n{text}");
                    assert_eq!(both, expected, "{path} {name}");
                    continue;
                }
                assert_eq!(text, expected, "{path} {name}");
                if name == "js" {
                    let (_, map) = (files.iter())
                        .find(|(p, _)| p == "main/main.internal.js.map")
                        .unwrap();
                    assert!(map.contains("\"sources\":[\"../main.temper.md\"]"), "{map}");
                }
            }
        }
        assert!(generate_source(String::new(), 7).contains("no backend 7"));
    }

    /// `compile`'s JSON for source, natively, with room to recurse.
    fn compile_source(source: &'static str, options: u32, mask: u32) -> String {
        std::thread::Builder::new()
            .stack_size(64 << 20)
            .spawn(move || {
                let mut out = String::new();
                compile_into(source, SourceKind::Standalone, options, mask, &mut out);
                out
            })
            .unwrap()
            .join()
            .unwrap()
    }

    /// Diagnostics count UTF-16 code units, as JavaScript does.
    #[test]
    fn compiles_with_positions() {
        let json = compile_source("let \u{e9} = 1;\nconsole.log(y);", 0, 0);
        assert_eq!(
            json,
            "{\"diagnostics\":[{\"level\":\"error\",\"message\":\"y has not been declared\",\
             \"file\":\"main.temper\",\"from\":23,\"to\":24,\"line\":2,\"column\":13}],\
             \"backends\":{}}"
        );
    }

    /// The playground's case: one `)` deleted from a method is one error
    /// at the gap, and the rest of the file is still typed.
    #[test]
    fn compiles_with_narrow_syntax_errors() {
        let source = "export class Stack<T> {\n\
                      \x20 private items: ListBuilder<T> = new ListBuilder<T>();\n\
                      \x20 public push(item: T: Void { items.add(item); }\n\
                      \x20 public get size(): Int { items.length }\n\
                      }\n\
                      let later(): Int { \"s\" }\n";
        let json = compile_source(source, 0, 0);
        assert_eq!(
            json,
            "{\"diagnostics\":[{\"level\":\"error\",\
             \"message\":\"Expected `)` to close the `(` on line 3\",\
             \"file\":\"main.temper\",\"from\":101,\"to\":101,\"line\":3,\"column\":22},\
             {\"level\":\"error\",\"message\":\"Cannot assign to Int32 from String\",\
             \"file\":\"main.temper\",\"from\":192,\"to\":195,\"line\":6,\"column\":20}],\
             \"backends\":{}}"
        );
    }

    /// The playground's case: an operator missing in `cents % 100` is one
    /// error, at `100`; `part` is still declared, so its later uses are
    /// fine; the run fails where the error is; and the code is written.
    #[test]
    fn compiles_with_a_missing_operator() {
        let source = "let cents = 1234;\n\
                      let part = cents  100;\n\
                      console.log(\"${part}\");\n\
                      let more = part + 1;\n";
        let py = built_in().position(|b| b == "py").map_or(0, |i| 1 << i);
        let json = compile_source(source, RUN, py);
        let expected = "{\"diagnostics\":[{\"level\":\"error\",\
                        \"message\":\"Expected an operator or `;` before `100`\",\
                        \"file\":\"main.temper\",\"from\":36,\"to\":39,\"line\":2,\"column\":19}],\
                        \"run\":{\"output\":[],\
                        \"failure\":\"Failed: Expected an operator or `;` before `100`\"},";
        assert!(json.starts_with(expected), "{json}");
        if py != 0 {
            assert!(
                json.contains("_cents: int = 1234\\nraise RuntimeError()\\n"),
                "{json}"
            );
        }
    }

    /// The editor's queries over what `analyze` kept, at UTF-16
    /// offsets, past an emoji and with an error before them.
    #[test]
    fn answers_queries_in_utf16() {
        const SOURCE: &str = "let s = \"\u{1F600}\"; let n: Int = \"no\";\n\
                              /** Twice. */\nlet twice(x: Int): Int { x * 2 }\n\
                              let y = twice(3);\nclass C { public m(): Int { 1 } }\n\
                              let c = new C();\nc.";
        let utf16 = |needle: &str| {
            let byte = SOURCE.find(needle).unwrap();
            Position::of(SOURCE, byte).utf16 as u32
        };
        let (call, decl, y) = (utf16("twice(3)"), utf16("twice(x"), utf16("y ="));
        let end = SOURCE.encode_utf16().count() as u32;
        let answers = std::thread::Builder::new()
            .stack_size(64 << 20)
            .spawn(move || {
                let mut out = String::new();
                analyze_into(SOURCE, SourceKind::Standalone, &mut out);
                let mut answers = vec![out];
                for (f, at) in [
                    (hover as extern "C" fn(u32), call + 1),
                    (definition, call + 1),
                    (completions, y),
                    (completions, end),
                ] {
                    f(at);
                    answers.push(OUTPUT.with_borrow(|out| out.clone()));
                }
                answers
            })
            .unwrap()
            .join()
            .unwrap();
        let no = utf16("\"no\"");
        assert!(
            answers[0].contains(&format!("\"from\":{no},\"to\":{}", no + 4)),
            "{}",
            answers[0]
        );
        assert_eq!(
            answers[1],
            format!(
                "{{\"from\":{call},\"to\":{},\"name\":\"twice\",\"kind\":\"function\",\
                 \"type\":\"twice(x: Int32): Int32\",\"doc\":\"Twice.\"}}",
                call + 5
            )
        );
        assert_eq!(
            answers[2],
            format!("{{\"from\":{decl},\"to\":{}}}", decl + 5)
        );
        assert!(
            answers[3].contains("{\"name\":\"twice\",\"kind\":\"function\""),
            "{}",
            answers[3]
        );
        assert_eq!(
            answers[4],
            "[{\"name\":\"m\",\"kind\":\"method\",\"type\":\"m(): Int32\"}]"
        );
    }

    /// References, rename, signature help, and a definition in std.
    #[test]
    fn answers_editing_queries() {
        const SOURCE: &str = "let { Regex } = import(\"std/regex\");\n\
                              let \u{e9}t\u{e9} = 1;\nlet b = \u{e9}t\u{e9} + \u{e9}t\u{e9};\n\
                              let r: Regex? = null;\nlet f(a: Int, b: Int): Int { a }\nf(1, ";
        let utf16 = |needle: &str| {
            let byte = SOURCE.find(needle).unwrap();
            Position::of(SOURCE, byte).utf16 as u32
        };
        let (ete, regex) = (utf16("\u{e9}t\u{e9} +"), utf16("Regex? "));
        let int = utf16("Int, b");
        let end = SOURCE.encode_utf16().count() as u32;
        let answers = std::thread::Builder::new()
            .stack_size(64 << 20)
            .spawn(move || {
                let mut out = String::new();
                analyze_into(SOURCE, SourceKind::Standalone, &mut out);
                let mut answers = Vec::new();
                references(ete);
                answers.push(OUTPUT.with_borrow(|out| out.clone()));
                let name = "x";
                let ptr = alloc(name.len());
                INPUT.with_borrow_mut(|input| input[..1].copy_from_slice(b"x"));
                rename(ete, ptr, name.len());
                answers.push(OUTPUT.with_borrow(|out| out.clone()));
                rename(ete, std::ptr::null(), 0);
                answers.push(OUTPUT.with_borrow(|out| out.clone()));
                signature_help(end);
                answers.push(OUTPUT.with_borrow(|out| out.clone()));
                definition(regex + 1);
                answers.push(OUTPUT.with_borrow(|out| out.clone()));
                definition(int + 1);
                answers.push(OUTPUT.with_borrow(|out| out.clone()));
                answers
            })
            .unwrap()
            .join()
            .unwrap();
        let decl = utf16("\u{e9}t\u{e9} =");
        let second = utf16("\u{e9}t\u{e9};");
        assert_eq!(
            answers[0],
            format!(
                "[{{\"from\":{decl},\"to\":{}}},{{\"from\":{ete},\"to\":{}}},{{\"from\":{second},\"to\":{}}}]",
                decl + 3,
                ete + 3,
                second + 3
            )
        );
        assert!(answers[1].starts_with("{\"edits\":[{"), "{}", answers[1]);
        assert_eq!(
            answers[1].matches("\"text\":\"x\"").count(),
            3,
            "{}",
            answers[1]
        );
        assert_eq!(
            answers[2],
            format!(
                "{{\"from\":{ete},\"to\":{},\"name\":\"\u{e9}t\u{e9}\"}}",
                ete + 3
            )
        );
        assert_eq!(
            answers[3],
            "{\"label\":\"f(a: Int32, b: Int32): Int32\",\"params\":[\"a: Int32\",\"b: Int32\"],\"active\":1,\"doc\":null}"
        );
        assert!(
            answers[4].starts_with("{\"file\":\"std/regex/regex.temper.md\",\"text\":"),
            "{}",
            &answers[4][..80]
        );
        // A builtin, `Int`, in core.
        let core = temper_frontend::corelib::TEXT;
        let int = temper_frontend::typing::core_declaration("Int").unwrap();
        let from = Position::of(core, int).utf16;
        assert!(
            answers[5].starts_with("{\"file\":\"core/core.temper\",\"text\":"),
            "{}",
            &answers[5][..80]
        );
        let span = format!(",\"from\":{from},\"to\":{}}}", from + 3);
        assert!(answers[5].ends_with(&span), "{span}");
    }

    /// One compile runs the tests and generates each backend asked for.
    #[cfg(feature = "py")]
    #[test]
    fn compiles_runs_and_tests() {
        let source = "export let twice(n: Int): Int { n * 2 }\n\
                      test(\"doubles\") { assert(twice(2) == 4); }\n\
                      test(\"fails\") { assert(twice(2) == 5) { \"no\" } }\n\
                      console.log(\"hi\");";
        let py = 1 << built_in().position(|b| b == "py").unwrap();
        let json = compile_source(source, RUN | TESTS, py);
        assert!(json.starts_with("{\"diagnostics\":[],\"run\":{\"output\":[\"hi\"],\"failure\":null,\
             \"tests\":[{\"name\":\"doubles\",\"failure\":null},{\"name\":\"fails\",\"failure\":\"no\"}],\
             \"defined\":2,\"summary\":\"Tests passed: 1 of 2\"},\"backends\":{\"py\":{\"files\":["), "{json}");
        assert!(json.contains("def twice(n: int, /) -> int:"), "{json}");
        assert!(!json.contains("\"rust\":"), "{json}");
        backends();
        let names = OUTPUT.with_borrow(|out| out.clone());
        // Alphabetically, as `temper` lists them.
        let listed: Vec<String> = built_in().map(json_string).collect();
        assert_eq!(names, format!("[{}]", listed.join(",")));
        assert!(names.contains("\"py\""), "{names}");
    }

    /// std's own JavaScript, for running tests with std's `testing`.
    #[cfg(feature = "js")]
    #[test]
    fn generates_std() {
        let js = built_in().position(|b| b == "js").unwrap() as u32;
        let json = std::thread::Builder::new()
            .stack_size(64 << 20)
            .spawn(move || {
                std_library(js);
                OUTPUT.with_borrow(|out| out.clone())
            })
            .unwrap()
            .join()
            .unwrap();
        assert!(
            json.contains("{\"path\":\"std/testing.js\""),
            "{}",
            &json[..200]
        );
    }

    #[test]
    fn runs_with_built_in_std() {
        let source = "let { Date } = import(\"std/temporal\");\n\
                      console.log((new Date(2024, 3, 5) orelse panic()).month.toString());";
        assert_eq!(run_source(source), "3\n");
    }
}
