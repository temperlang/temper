//! What a REPL session's own functions need beyond the frontend
//! ([`temper_frontend::repl::ReplTools`]): hover's text for `help`, tmpl
//! for `describe(n, "tmpl")`, and this build's backends for `translate`.

use crate::{Registry, checked, translate, with_std};
use std::cell::OnceCell;
use temper_analysis::Analysis;
use temper_frontend::library::Library;
use temper_frontend::library::{Level, Message};
use temper_frontend::print::write_diagnostics;
use temper_frontend::repl::{INTERRUPTED, MODULE_ERRORS};
use temper_frontend::scope::SymbolId;
use temper_frontend::settle::{Settled, settle};
use temper_frontend::sources::Sources;
use temper_tmpl::Program;

/// The library name a REPL module translates as.
const NAME: &str = "interactive";

/// The tools, with std as tmpl, settled the first time a translation
/// uses it.
pub struct ReplTools {
    registry: Registry,
    std: OnceCell<Result<Program, String>>,
}

impl Default for ReplTools {
    fn default() -> ReplTools {
        ReplTools {
            registry: Registry::built_in(),
            std: OnceCell::new(),
        }
    }
}

impl ReplTools {
    /// A library settled, as tmpl, or why not: its errors, first, as
    /// tmpl may tolerate some, which would translate as code that fails;
    /// or its diagnostics, then what tmpl doesn't cover.
    fn program(library: Library, interrupted: &dyn Fn() -> bool) -> Result<Program, String> {
        let settled = settle(library);
        if interrupted() {
            return Err(INTERRUPTED.into());
        }
        errors(&settled)?;
        checked(&settled, NAME).map_err(|e| {
            let mut diagnostics = String::new();
            write_diagnostics(&settled, &mut diagnostics);
            format!("{diagnostics}{e}")
        })
    }
}

/// What a module with errors fails with: [`MODULE_ERRORS`], then each
/// error, located in the module as `describe(n, "module")` shows it.
fn errors(settled: &Settled) -> Result<(), String> {
    let library = &settled.library;
    let mut out = String::new();
    let mut awaits = false;
    for d in (library.diagnostics.iter()).filter(|d| d.level == Level::Error) {
        let (path, line, column) = library.position(d.span.start);
        let message = d.message.text();
        awaits |= matches!(d.message, Message::YieldingOutsideGeneratorFn("await"));
        out.push_str(&format!("\n{path}:{line}:{column}: error: {message}"));
    }
    if out.is_empty() {
        return Ok(());
    }
    if awaits {
        out.push_str(
            "\nA top-level await runs only in the REPL; to translate it, put it in an \
             async block, as async { (): GeneratorResult<Empty> extends GeneratorFn => … }",
        );
    }
    Err(format!("{MODULE_ERRORS}:{out}"))
}

impl temper_frontend::repl::ReplTools for ReplTools {
    fn symbol_help(&self, s: &Settled, symbol: SymbolId) -> Option<String> {
        let hover = Analysis::of(s).symbol_hover(symbol);
        let mut text = hover.headline();
        if let Some(doc) = &hover.doc {
            text.push_str("\n\n");
            text.push_str(doc.trim_end());
        }
        Some(text)
    }

    /// Without its spans, which say where in the module's source each
    /// declaration and expression came from.
    fn tmpl(
        &self,
        library: Library,
        _sources: &Sources,
        interrupted: &dyn Fn() -> bool,
    ) -> Result<String, String> {
        let text = temper_tmpl::text::write(&ReplTools::program(library, interrupted)?);
        Ok(match text.find("\nspans {") {
            Some(at) => text[..at].trim_end().to_string(),
            None => text,
        })
    }

    fn translate(
        &self,
        library: Library,
        sources: &Sources,
        language: &str,
        interrupted: &dyn Fn() -> bool,
    ) -> Result<Vec<(String, String)>, String> {
        let backend = (self.registry.get(language))
            .ok_or_else(|| format!("built without the {language} backend"))?;
        let program = ReplTools::program(library, interrupted)?;
        let std = || {
            let std = self.std.get_or_init(|| {
                temper_frontend::stdlib::program(sources).map_err(|e| format!("std: {e}"))
            });
            std.as_ref().map_err(String::clone)
        };
        let program = with_std(program, backend, std)?;
        if interrupted() {
            return Err(INTERRUPTED.into());
        }
        let files = translate(&program, backend, temper_tmpl::SourceLinks::default())?;
        Ok(files
            .into_iter()
            .filter(|(path, _)| own_code(path))
            .collect())
    }

    fn languages(&self) -> Vec<String> {
        (self.registry.all().iter())
            .map(|b| b.name.to_string())
            .collect()
    }
}

/// Whether a generated file holds the module's own code, rather than
/// what every library gets: a package's metadata and build files, its
/// entry points and re-exports, and support code.
fn own_code(path: &str) -> bool {
    let name = path.rsplit('/').next().unwrap_or(path);
    let source = [".py", ".js", ".lua", ".java", ".cs", ".cpp", ".hpp", ".rs"];
    let boilerplate = [
        "__init__.py",
        "index.js",
        "interactive.js",
        "main.cpp",
        "Logging.cs",
        "Program.cs",
        "InteractiveMain.java",
        "lib.rs",
        "main.rs",
    ];
    source.iter().any(|e| name.ends_with(e))
        && !boilerplate.contains(&name)
        && !path.contains("/support/")
        && !path.contains("/program/")
}
