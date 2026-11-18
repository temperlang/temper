//! The REPL's own functions, `help`, `describe`, `translate`, and
//! `runTests`: declared before the first input, in a scope of their
//! own, as Temper functions calling a bodiless `@connected` one that the
//! session implements ([`crate::interp::repl::Host`]), whose name no
//! input can write. Being ordinary declarations, they complete and type
//! check as any other, and an input's own `help` shadows the REPL's for
//! later inputs, as any redefinition does.
//!
//! What needs more than the frontend, hover's text for `help("name")`,
//! tmpl for `describe(n, "tmpl")`, and the backends for `translate`,
//! comes from [`ReplTools`], which `temper-driver` implements.
//!
//! `describe(n, "module")`, `describe(n, "tmpl")`, and `translate(n)`
//! show one module: input `n` after the earlier inputs it uses, through
//! the names they declare, transitively, each input whole, and with a
//! name that a later one of them declares again renamed, `x` then
//! `x__2`, so the module means what the inputs did.

use super::{ReplEvent, Sink};
use crate::core;
use crate::interp::INTERRUPTED;
use crate::interp::Val;
use crate::interp::repl::{Arg, Host, arg};
use crate::library::{FileId, Library};
use crate::project::load_imports;
use crate::scope::{BUILTINS, Lookup, ScopeId, SymbolId};
use crate::settle::Settled;
use crate::sources::Sources;
use std::cell::{Cell, RefCell};
use std::collections::{BTreeSet, HashMap, HashSet};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::rc::Rc;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use temper_syntax::lex::{SourceKind, lex};
use temper_syntax::tree::{NodeId, NodeKind};

/// The REPL's functions, settled before the first input.
pub(super) const PRELUDE: &str = r#"@connected let replHost(which: Int, a: AnyValue?, b: AnyValue?): Void;
/**
 * What the REPL offers; or with a name, as `help("greet")`, or a
 * function or type, as `help(greet)`, its signature and docs.
 */
let help(about: AnyValue? = null): Void { replHost(0, about, null) }
/**
 * Shows input `n` at one of the compiler's steps: "parsed", "settled" (the
 * default), "types", "module", or "tmpl".
 */
let describe(n: Int, step: String = "settled"): Void { replHost(1, n, step) }
/**
 * Shows input `n`, after the earlier inputs it uses, translated into a
 * backend's language, as "py"; without one, into each.
 */
let translate(n: Int, language: String = ""): Void { replHost(2, n, language) }
/**
 * Runs input 0's tests again, once this input ends, against the newest
 * declarations of the names they use.
 */
let runTests(): Void { replHost(3, null, null) }
"#;

/// The name of the prelude's function that the host implements, which
/// becomes a temporary name, so no input can name it.
pub(super) const HOST_NAME: &str = "replHost";

/// The prelude's file, in the inputs' directory.
pub(super) const PRELUDE_PATH: &str = "-repl-/repl";

/// `describe`'s steps, each with what it shows.
pub const STEPS: [(&str, &str); 5] = [
    ("parsed", "the parser's tree, before names are resolved"),
    (
        "settled",
        "the tree once settled: macros expanded, names resolved",
    ),
    ("types", "each declaration's type, and the result's"),
    (
        "module",
        "the module translate shows: input n after the inputs it uses",
    ),
    ("tmpl", "that module as tmpl, what backends translate"),
];

/// What the REPL's functions need beyond the frontend: hover's text,
/// tmpl, and the backends.
pub trait ReplTools {
    /// A symbol's signature and docs, as hover shows them, for
    /// `help("name")`; `None` to say just its name and type.
    fn symbol_help(&self, s: &Settled, symbol: SymbolId) -> Option<String>;
    /// A library, to settle, as tmpl's text, or why it can't be: its
    /// errors, if it has any. Between its steps, it stops with
    /// [`INTERRUPTED`] once `interrupted` says so.
    fn tmpl(
        &self,
        library: Library,
        sources: &Sources,
        interrupted: &dyn Fn() -> bool,
    ) -> Result<String, String>;
    /// A library, to settle, translated into a language: the files of
    /// its own code, each a path and its text, or why it can't be: its
    /// errors, if it has any. Between its steps, it stops with
    /// [`INTERRUPTED`] once `interrupted` says so.
    fn translate(
        &self,
        library: Library,
        sources: &Sources,
        language: &str,
        interrupted: &dyn Fn() -> bool,
    ) -> Result<Vec<(String, String)>, String>;
    /// The languages it translates into, alphabetically.
    fn languages(&self) -> Vec<String>;
}

/// The session's state that its functions read.
pub(super) struct Specials {
    pub tools: Option<Rc<dyn ReplTools>>,
    pub sources: Sources,
    pub libraries: Vec<(String, String)>,
    /// What the front end adds to `help()`: its keys and options.
    pub help_footer: String,
    /// Each input fed so far, the one running among them: its file and
    /// scope; none for one whose settling panicked.
    pub inputs: Vec<Option<(FileId, ScopeId)>>,
    /// Set to stop what's running, as by Ctrl-C.
    pub interrupt: Option<Arc<AtomicBool>>,
    /// The prelude's scope, and the declaration of the function the host
    /// implements.
    pub prelude: Option<ScopeId>,
    pub host: Option<NodeId>,
    /// Whether input 0 declared tests, which `runTests()` runs again.
    pub has_tests: bool,
    /// Set by `runTests()`, for the session to run input 0's tests again
    /// once the input running ends.
    pub run_tests: Cell<bool>,
}

/// The host that runs the functions, printing what they show to `sink`.
pub(super) fn host(state: Rc<RefCell<Specials>>, sink: Sink) -> Host {
    Rc::new(move |s: &Settled, decl: NodeId, args: &[Val]| {
        let state = state.borrow();
        if state.host != Some(decl) {
            return None;
        }
        let (which, args) = args.split_first()?;
        let shown = catch_unwind(AssertUnwindSafe(|| {
            state.check_interrupt()?;
            match arg(which) {
                Arg::Int(0) => state.help(s, args),
                Arg::Int(1) => state.describe(s, args),
                Arg::Int(2) => state.translate(s, args),
                _ => state.request_tests(),
            }
        }));
        let shown = shown.unwrap_or_else(|_| {
            Err("temper: internal error (a bug in Temper, not in your code)".into())
        });
        Some(shown.map(|text| {
            if !text.is_empty() {
                (*sink.borrow_mut())(&ReplEvent::Output(text));
            }
            Val::Void
        }))
    })
}

impl Specials {
    /// `help(about)`.
    fn help(&self, s: &Settled, args: &[Val]) -> Result<String, String> {
        let symbol = match args.first().map(arg).unwrap_or(Arg::Null) {
            Arg::Null => return Ok(self.overview()),
            Arg::Str(name) => self.lookup(s, self.latest(), name).ok_or_else(|| {
                format!("help: nothing named {name} is declared; help() says what the REPL offers")
            })?,
            Arg::Decl(decl) => symbol_of(s, decl).ok_or("help: that has no declaration")?,
            Arg::Builtin(name) => (self.lookup(s, BUILTINS, name))
                .ok_or_else(|| format!("help: {name} is a builtin"))?,
            Arg::Int(_) => return Err(help_takes("an Int")),
            Arg::Other(ty) => return Err(help_takes(&format!("a {ty}"))),
        };
        let tools = self.tools.as_ref();
        Ok(match tools.and_then(|t| t.symbol_help(s, symbol)) {
            Some(text) => text,
            None => plain_help(s, symbol),
        })
    }

    /// What `help()` shows.
    fn overview(&self) -> String {
        let mut out = String::from(
            "temper repl runs each Temper input against what earlier inputs declared,\n\
             printing its result as interactive#N: value.\n\
             \n\
             The REPL's own functions:\n\
            \x20 help()              this\n\
            \x20 help(\"name\")        a declaration's signature and docs, as help(\"greet\");\n\
            \x20                     a function's or type's also as help(greet)\n\
            \x20 describe(n, step)   input n at one of the compiler's steps (\"settled\" if none):\n",
        );
        for (step, what) in STEPS {
            out.push_str(&format!("      {:<9} {what}\n", format!("\"{step}\"")));
        }
        let languages = match &self.tools {
            Some(tools) => or_list(&tools.languages()),
            None => "none here".into(),
        };
        let translate = format!(
            "that module translated into lang, which is {languages}; translate(n) into each"
        );
        out.push_str(&wrap("  translate(n, lang)  ", &translate, 78));
        out.push_str(
            "  runTests()          input 0's tests again, against the newest declarations\n\
             \nAn input's own help, describe, translate, or runTests shadows the REPL's\n\
             for later inputs.\n",
        );
        if !self.help_footer.is_empty() {
            out.push('\n');
            out.push_str(&self.help_footer);
        }
        out.trim_end().to_string()
    }

    /// `runTests()`: asks for input 0's tests to run once the input
    /// ends, as they can't be settled while it runs; shows nothing.
    fn request_tests(&self) -> Result<String, String> {
        if !self.has_tests {
            return Err("runTests: input 0 declared no tests".into());
        }
        self.run_tests.set(true);
        Ok(String::new())
    }

    /// `describe(n, step)`.
    fn describe(&self, s: &Settled, args: &[Val]) -> Result<String, String> {
        let n = self.input_arg("describe", args)?;
        let step = match args.get(1).map(arg) {
            None | Some(Arg::Null) => "settled",
            Some(Arg::Str(step)) => step,
            Some(_) => "",
        };
        let file = self.input(n)?.0;
        let root = s.library.files[file.0 as usize].root;
        let mut out = String::new();
        match step {
            "parsed" => {
                let source = s.library.files[file.0 as usize].parsed.source();
                let parsed = temper_syntax::parse::parse(source, SourceKind::Standalone);
                let ast = temper_syntax::ast::build(&parsed);
                temper_syntax::print::write_ast(&ast, 80, &mut out);
            }
            "settled" => crate::print::write_node(s, root, 80, &mut out),
            "types" => types(s, root, &mut out),
            "module" => out = self.module(s, n)?,
            "tmpl" => {
                let tools = self.tools.as_ref().ok_or("describe: no tmpl here")?;
                let interrupted = || self.check_interrupt().is_err();
                let library = self.module_library(s, n)?;
                out = (tools.tmpl(library, &self.sources, &interrupted)).map_err(|why| {
                    match why == INTERRUPTED {
                        true => why,
                        false => format!("describe: {why}"),
                    }
                })?;
            }
            _ => {
                let steps: Vec<String> = STEPS.iter().map(|(s, _)| format!("\"{s}\"")).collect();
                return Err(format!("describe: the step is {}", or_list(&steps)));
            }
        }
        Ok(out.trim_end().to_string())
    }

    /// `translate(n, language)`.
    fn translate(&self, s: &Settled, args: &[Val]) -> Result<String, String> {
        let n = self.input_arg("translate", args)?;
        let tools = self.tools.as_ref().ok_or("translate: no backends here")?;
        let known = tools.languages();
        let languages = match args.get(1).map(arg) {
            None | Some(Arg::Null) => known.clone(),
            Some(Arg::Str("")) => known.clone(),
            Some(Arg::Str(language)) if known.iter().any(|k| k == language) => {
                vec![language.to_string()]
            }
            _ => return Err(format!("translate: the language is {}", or_list(&known))),
        };
        let mut out = String::new();
        let interrupted = || self.check_interrupt().is_err();
        for language in &languages {
            self.check_interrupt()?;
            let comment = match language.as_str() {
                "py" => "#",
                "lua" => "--",
                _ => "//",
            };
            let module = self.module_library(s, n)?;
            match tools.translate(module, &self.sources, language, &interrupted) {
                Ok(files) => {
                    for (path, text) in files {
                        out.push_str(&format!("{comment} {path}\n{}\n", text.trim_end()));
                    }
                }
                Err(why) if why == INTERRUPTED => return Err(why),
                // The same for every language, so said once.
                Err(why) if why.starts_with(super::MODULE_ERRORS) => {
                    return Err(format!("translate: {why}"));
                }
                Err(why) if languages.len() > 1 => {
                    out.push_str(&format!("{comment} {language}: {why}\n"))
                }
                Err(why) => return Err(format!("translate: {why}")),
            }
        }
        Ok(out.trim_end().to_string())
    }

    /// The input number that's a function's first argument, checked.
    fn input_arg(&self, function: &str, args: &[Val]) -> Result<usize, String> {
        let Some(last) = self.inputs.len().checked_sub(1) else {
            return Err(format!("{function}: there are no inputs yet"));
        };
        match args.first().map(arg) {
            Some(Arg::Int(n)) if n >= 0 && (n as usize) <= last => Ok(n as usize),
            Some(Arg::Int(n)) => Err(format!(
                "{function}: there's no input {n}; they're 0 to {last}"
            )),
            _ => Err(format!("{function} takes an input's number first")),
        }
    }

    /// The scope a name is looked up from: the input running's.
    fn latest(&self) -> ScopeId {
        (self
            .inputs
            .iter()
            .rev()
            .find_map(|input| input.map(|(_, scope)| scope)))
        .or(self.prelude)
        .unwrap_or(BUILTINS)
    }

    /// The symbol a name means from `scope`.
    fn lookup(&self, s: &Settled, scope: ScopeId, name: &str) -> Option<SymbolId> {
        let name = s.library.names.find(name)?;
        match s.scopes.lookup(scope, name, false, u32::MAX) {
            Lookup::Found(symbol) => Some(symbol),
            _ => None,
        }
    }

    /// The module that input `n` makes, with what it uses, as a library
    /// to settle.
    fn module_library(&self, s: &Settled, n: usize) -> Result<Library, String> {
        self.input(n)?;
        let mut library = Library::new();
        library.libraries = self.libraries.clone();
        // Any later input may use what one declares.
        library.keep_all = true;
        library.add_file(
            "interactive.temper",
            &self.module(s, n)?,
            SourceKind::Standalone,
        );
        load_imports(&self.sources, &mut library);
        Ok(library)
    }

    /// Input `n` as one Temper module ([module docs](self)), or
    /// [`INTERRUPTED`] if it's interrupted.
    pub(super) fn module(&self, s: &Settled, n: usize) -> Result<String, String> {
        let tree = &s.library.tree;
        let by_scope: HashMap<ScopeId, usize> = (self.inputs.iter().enumerate())
            .filter_map(|(i, input)| input.map(|(_, scope)| (scope, i)))
            .collect();
        let input = |i: usize| self.inputs[i].expect("an input that's included was settled");
        let root = |i: usize| s.library.files[input(i).0.0 as usize].root;
        // The inputs it uses, through names, transitively, and the
        // earlier inputs that change what those declare, until there are
        // no more.
        let mut included = BTreeSet::from([n]);
        let mut todo = vec![n];
        loop {
            while let Some(i) = todo.pop() {
                self.check_interrupt()?;
                for node in descendants(s, root(i)) {
                    // A name, or an expression of a class's type, as an
                    // object literal is.
                    let class = s.typing.expr(node).and_then(|t| class_of(s, t));
                    for symbol in [s.scopes.resolved(node), class].into_iter().flatten() {
                        let owner = s.scopes.symbol(symbol).owner;
                        if let Some(&j) = by_scope.get(&owner) {
                            if included.insert(j) {
                                todo.push(j);
                            }
                        }
                    }
                }
            }
            let mine = |symbol: SymbolId| {
                (by_scope.get(&s.scopes.symbol(symbol).owner)).is_some_and(|j| included.contains(j))
            };
            for j in 0..n {
                self.check_interrupt()?;
                if !included.contains(&j) && self.inputs[j].is_some() && changes(s, root(j), &mine)
                {
                    todo.push(j);
                }
            }
            if todo.is_empty() {
                break;
            }
            included.extend(todo.iter().copied());
        }
        // Names declared again among them, renamed after the first; an
        // import of what an earlier one imported under the same name is
        // dropped, its uses meaning the earlier one.
        let mut taken: HashSet<String> = HashSet::new();
        for &i in &included {
            for &(name, _) in &s.scopes.get(input(i).1).names {
                taken.insert(s.library.names.get(name).to_string());
            }
        }
        let mut counts: HashMap<&str, usize> = HashMap::new();
        let mut renames: HashMap<SymbolId, String> = HashMap::new();
        let mut latest: HashMap<&str, SymbolId> = HashMap::new();
        let mut again: HashSet<SymbolId> = HashSet::new();
        for &i in &included {
            for &(name, symbol) in &s.scopes.get(input(i).1).names {
                if s.library.names.is_temporary(name) {
                    continue;
                }
                let text = s.library.names.get(name);
                let imported = import_target(s, &by_scope, symbol);
                let earlier = latest.get(text).copied();
                if imported.is_some()
                    && earlier.and_then(|e| import_target(s, &by_scope, e)) == imported
                {
                    if let Some(renamed) = earlier.and_then(|e| renames.get(&e)).cloned() {
                        renames.insert(symbol, renamed);
                    }
                    again.insert(symbol);
                    continue;
                }
                latest.insert(text, symbol);
                let count = counts.entry(text).or_default();
                *count += 1;
                if *count > 1 {
                    let mut k = *count;
                    while taken.contains(&format!("{text}__{k}")) {
                        k += 1;
                    }
                    let renamed = format!("{text}__{k}");
                    taken.insert(renamed.clone());
                    renames.insert(symbol, renamed);
                }
            }
        }
        let mut out = String::new();
        for &i in &included {
            self.check_interrupt()?;
            let (file, scope) = input(i);
            let source = s.library.files[file.0 as usize].parsed.source();
            // The imports dropped, as byte ranges.
            let repeated: Vec<usize> = (s.scopes.get(scope).names.iter())
                .filter(|(_, symbol)| again.contains(symbol))
                .filter_map(|&(_, symbol)| s.scopes.symbol(symbol).decl)
                .map(|decl| {
                    let name = tree.children(decl)[0];
                    s.library.locate(tree.span(name).start).1 as usize
                })
                .collect();
            let drops = drop_imports(source, &repeated);
            let dropped = |at: usize| drops.iter().any(|&(start, end)| (start..end).contains(&at));
            // Each use and declaration of a renamed name, by its offset.
            let mut edits: Vec<(usize, usize, &str)> = Vec::new();
            let mut names: Vec<(NodeId, SymbolId)> = Vec::new();
            for node in descendants(s, root(i)) {
                if let Some(symbol) = s.scopes.resolved(node) {
                    names.push((node, symbol));
                }
            }
            for &symbol in renames.keys() {
                let decl = s.scopes.symbol(symbol).decl;
                if let Some(decl) =
                    decl.filter(|&d| matches!(tree.kind(d), NodeKind::Let | NodeKind::Class))
                {
                    names.push((tree.children(decl)[0], symbol));
                }
            }
            for (node, symbol) in names {
                let Some(renamed) = renames.get(&symbol) else {
                    continue;
                };
                let (at, start) = s.library.locate(tree.span(node).start);
                let old = s.library.names.get(s.scopes.symbol(symbol).name);
                let start = start as usize;
                if at == file
                    && !dropped(start)
                    && source.get(start..).is_some_and(|t| t.starts_with(old))
                {
                    edits.push((start, start + old.len(), renamed));
                }
            }
            edits.extend(drops.iter().map(|&(start, end)| (start, end, "")));
            edits.sort();
            edits.dedup_by_key(|e| e.0);
            let mut text = source.to_string();
            for &(start, end, renamed) in edits.iter().rev() {
                text.replace_range(start..end, renamed);
            }
            // A `;` after its last token, so the next input starts anew.
            let tokens = lex(&text, SourceKind::Standalone);
            if let Some(last) = tokens.iter().rev().find(|t| !t.kind.is_ignorable()) {
                if &text[last.range()] != ";" {
                    text.insert(last.end as usize, ';');
                }
            }
            out.push_str(&format!("// interactive#{i}\n"));
            if !text.trim().is_empty() {
                out.push_str(&format!("{}\n", text.trim()));
            }
        }
        Ok(out)
    }

    /// Fails with [`INTERRUPTED`] once the interrupt flag is set, as by
    /// Ctrl-C, so the REPL's functions can be stopped as inputs are.
    pub(super) fn check_interrupt(&self) -> Result<(), String> {
        match self
            .interrupt
            .as_ref()
            .is_some_and(|flag| flag.load(Ordering::Relaxed))
        {
            true => Err(INTERRUPTED.to_string()),
            false => Ok(()),
        }
    }

    /// Input `n`'s file and scope, or why there are none: settling it
    /// failed.
    fn input(&self, n: usize) -> Result<(FileId, ScopeId), String> {
        self.inputs[n].ok_or_else(|| {
            format!(
                "input {n} failed as it was settled, a bug in Temper, so it has nothing to show"
            )
        })
    }
}

/// Whether an input's top level has a statement, not a declaration, that
/// assigns to one of `mine`'s symbols, or calls a method on one, as
/// `v = 2;` or `list.add(x);`, changing what an earlier input declared.
fn changes(s: &Settled, root: NodeId, mine: &dyn Fn(SymbolId) -> bool) -> bool {
    let tree = &s.library.tree;
    let resolved_mine = |node: NodeId| s.scopes.resolved(node).is_some_and(mine);
    let top = tree.children(root).iter().copied();
    top.filter(|&t| !matches!(tree.kind(t), NodeKind::Let | NodeKind::Class))
        .flat_map(|t| descendants(s, t))
        .any(|node| match tree.kind(node) {
            NodeKind::Assign => (tree.children(node).first())
                .is_some_and(|&target| descendants(s, target).into_iter().any(resolved_mine)),
            // `receiver.method(…)`: a call of a member, `(. receiver \method)`.
            NodeKind::Call => (tree.children(node).first().copied())
                .filter(|&callee| tree.kind(callee) == NodeKind::Call)
                .and_then(|callee| tree.children(callee).get(1).copied())
                .is_some_and(resolved_mine),
            _ => false,
        })
}

/// What a symbol was imported as, if an input's import declared it: the
/// module's symbol that its `Let` takes the value of.
fn import_target(
    s: &Settled,
    inputs: &HashMap<ScopeId, usize>,
    symbol: SymbolId,
) -> Option<SymbolId> {
    let tree = &s.library.tree;
    let decl = s.scopes.symbol(symbol).decl?;
    if tree.kind(decl) != NodeKind::Let {
        return None;
    }
    let target = s.scopes.resolved(core::let_parts(tree, decl).2?)?;
    let owner = s.scopes.symbol(target).owner;
    (!inputs.contains_key(&owner) && owner != BUILTINS).then_some(target)
}

/// What to cut from an input's source to drop the bindings that start at
/// `names`, each in an import, `let { a, b } = import("…");`: a binding
/// with the comma after or before it, or the whole statement if all its
/// bindings go. A binding that isn't in such an import stays.
fn drop_imports(source: &str, names: &[usize]) -> Vec<(usize, usize)> {
    if names.is_empty() {
        return Vec::new();
    }
    let tokens: Vec<_> = (lex(source, SourceKind::Standalone).into_iter())
        .filter(|t| !t.kind.is_ignorable())
        .collect();
    let text = |k: usize| tokens.get(k).map_or("", |t| &source[t.range()]);
    // The token after the bracket that `open` opens closes.
    let close = |open: usize| {
        let mut depth = 0i32;
        for k in open..tokens.len() {
            match text(k) {
                "(" | "[" | "{" | "${" => depth += 1,
                ")" | "]" | "}" => depth -= 1,
                _ => {}
            }
            if depth == 0 {
                return Some(k);
            }
        }
        None
    };
    // Each import's first and last tokens, and its bindings' tokens.
    type Import = (usize, usize, Vec<(usize, usize)>);
    let mut statements: Vec<Import> = Vec::new();
    let mut doomed: HashMap<usize, Vec<usize>> = HashMap::new();
    for &at in names {
        let Some(k) = tokens.iter().position(|t| t.start as usize == at) else {
            continue;
        };
        // Back to the pattern's `{`, after `let`.
        let mut depth = 0i32;
        let open = (0..k).rev().find(|&j| {
            match text(j) {
                ")" | "]" | "}" => depth += 1,
                "(" | "[" | "{" | "${" => depth -= 1,
                _ => {}
            }
            depth < 0
        });
        let Some(open) = open.filter(|&o| o > 0 && text(o) == "{" && text(o - 1) == "let") else {
            continue;
        };
        let Some(shut) = close(open) else { continue };
        if text(shut + 1) != "=" || text(shut + 2) != "import" || text(shut + 3) != "(" {
            continue;
        }
        let Some(mut end) = close(shut + 3) else {
            continue;
        };
        if text(end + 1) == ";" {
            end += 1;
        }
        // The pattern's bindings, by their tokens, split at its commas.
        let mut bindings = Vec::new();
        let mut first = open + 1;
        let mut j = open + 1;
        while j < shut {
            match text(j) {
                "," => {
                    bindings.push((first, j));
                    first = j + 1;
                }
                "(" | "[" | "{" | "${" => j = close(j).unwrap_or(shut),
                _ => {}
            }
            j += 1;
        }
        if first < shut {
            bindings.push((first, shut));
        }
        let Some(b) = bindings.iter().position(|&(a, z)| (a..z).contains(&k)) else {
            continue;
        };
        if !statements.iter().any(|st| st.0 == open - 1) {
            statements.push((open - 1, end, bindings));
        }
        doomed.entry(open - 1).or_default().push(b);
    }
    let mut cuts = Vec::new();
    for (start, end, bindings) in statements {
        let gone = &doomed[&start];
        if bindings.iter().enumerate().all(|(b, _)| gone.contains(&b)) {
            cuts.push((tokens[start].start as usize, tokens[end].end as usize));
            continue;
        }
        for &b in gone {
            let (a, z) = bindings[b];
            // The comma after it, or for the last, the one before.
            let cut = match bindings.get(b + 1) {
                Some(&(next, _)) => (tokens[a].start as usize, tokens[next].start as usize),
                None => (
                    tokens[bindings[b - 1].1].start as usize,
                    tokens[z - 1].end as usize,
                ),
            };
            cuts.push(cut);
        }
    }
    cuts
}

/// Text after a lead, wrapped at `width` columns, later lines indented
/// under its first word.
fn wrap(lead: &str, text: &str, width: usize) -> String {
    let indent = " ".repeat(lead.chars().count());
    let mut out = String::from(lead);
    let mut column = lead.chars().count();
    for (i, word) in text.split(' ').enumerate() {
        if i > 0 && column + 1 + word.chars().count() > width {
            out.push('\n');
            out.push_str(&indent);
            column = indent.len();
        } else if i > 0 {
            out.push(' ');
            column += 1;
        }
        out.push_str(word);
        column += word.chars().count();
    }
    out.push('\n');
    out
}

/// Words as a list, `a, b, or c`.
fn or_list(words: &[String]) -> String {
    match words {
        [] => "none".into(),
        [one] => one.clone(),
        [one, two] => format!("{one} or {two}"),
        [rest @ .., last] => format!("{}, or {last}", rest.join(", ")),
    }
}

/// Why `help` can't say what a value is.
fn help_takes(what: &str) -> String {
    format!(
        "help takes a name, as help(\"greet\"), or a function or type, as help(greet), \
         not {what}"
    )
}

/// The class a type is of, if it's one's, through type arguments' base.
fn class_of(s: &Settled, t: crate::types::TypeId) -> Option<SymbolId> {
    use crate::types::TypeData;
    let types = &s.typing.types;
    let base = match types.get(t) {
        TypeData::Applied { base, .. } => *base,
        _ => t,
    };
    match types.get(base) {
        TypeData::Class(symbol) => Some(*symbol),
        _ => None,
    }
}

/// A node and everything under it.
fn descendants(s: &Settled, root: NodeId) -> Vec<NodeId> {
    let tree = &s.library.tree;
    let mut out = Vec::new();
    let mut stack = vec![root];
    let mut seen = HashSet::new();
    while let Some(node) = stack.pop() {
        if seen.insert(node) {
            out.push(node);
            stack.extend(tree.children(node).iter().rev());
        }
    }
    out
}

/// The symbol of a declaration: a `Let`, a `Class`, or a function's
/// `Fn`, by the `Let` that gives it.
fn symbol_of(s: &Settled, decl: NodeId) -> Option<SymbolId> {
    let tree = &s.library.tree;
    let is = |d: NodeId| {
        d == decl || tree.kind(d) == NodeKind::Let && core::let_parts(tree, d).2 == Some(decl)
    };
    (s.scopes.symbols.iter())
        .position(|b| b.decl.is_some_and(is))
        .map(|i| SymbolId(i as u32))
}

/// A symbol's name and type, for `help` without tools.
fn plain_help(s: &Settled, symbol: SymbolId) -> String {
    let b = s.scopes.symbol(symbol);
    let name = s.library.names.get(b.name);
    match b.decl.and_then(|d| s.typing.decls.get(&d)) {
        Some(&t) => format!("{name}: {}", show(s, t)),
        None => name.to_string(),
    }
}

/// A type as messages write it.
fn show(s: &Settled, t: crate::types::TypeId) -> String {
    let class_name = |b: SymbolId| s.library.names.get(s.scopes.symbol(b).name).to_string();
    s.typing.types.display(t, &class_name)
}

/// `describe(n, "types")`: each declaration in an input, a class's
/// members under it, then the result's type.
fn types(s: &Settled, root: NodeId, out: &mut String) {
    let tree = &s.library.tree;
    let mut tops: Vec<NodeId> = tree.children(root).to_vec();
    tops.sort_by_key(|&n| tree.span(n).start);
    let declared = |node: NodeId, indent: &str, out: &mut String| {
        let name = tree.children(node)[0];
        let Some(name) = s.library.name(name) else {
            return;
        };
        if s.library.names.is_temporary(name) {
            return;
        }
        let text = s.library.names.get(name);
        match s.typing.decls.get(&node) {
            Some(&t) => out.push_str(&format!("{indent}{text}: {}\n", show(s, t))),
            None => out.push_str(&format!("{indent}{text}\n")),
        }
    };
    for &node in &tops {
        match tree.kind(node) {
            NodeKind::Let => declared(node, "", out),
            NodeKind::Class => {
                let name = s.library.name(tree.children(node)[0]);
                let name = name.map_or("?", |n| s.library.names.get(n));
                out.push_str(&format!("class {name}\n"));
                for &m in core::class_parts(tree, node).2 {
                    if tree.kind(m) == NodeKind::Let {
                        declared(m, "  ", out);
                    }
                }
            }
            _ => {}
        }
    }
    if let Some(&last) = tops.last() {
        if !matches!(tree.kind(last), NodeKind::Let | NodeKind::Class) {
            let shown = s.typing.expr(last).map(|t| show(s, t));
            if let Some(shown) = shown.filter(|t| t != "Void") {
                out.push_str(&format!("result: {shown}\n"));
            }
        }
    }
}
