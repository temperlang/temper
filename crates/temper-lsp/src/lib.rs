//! Temper's language server, which `temper lsp` runs over stdio,
//! answering an editor's questions from [`temper_analysis`], as the
//! playground does.
//!
//! - **Messages** are JSON-RPC 2.0, each after a `Content-Length` header
//!   ([`read_message`], [`write_message`]), as `serde_json` values; params
//!   and results are `lsp-types`' structs. The loop stays synchronous.
//! - **Documents** the editor opens are kept in memory, updated by
//!   incremental sync ([`apply_change`]), and read before the disk: a
//!   memory layer of [`Sources`] in front of it, so analysis sees unsaved
//!   text.
//! - **Units:** a document belongs to the library around it, found as
//!   `temper build` finds libraries (the nearest `config.temper.md`
//!   above it, under its work root), or else stands alone. Each unit is
//!   settled as one [`Analysis`], which answers for all its files.
//! - **Staying current:** a change marks the document stale. Stale units
//!   are analyzed again once typing pauses (a debounce), and their
//!   diagnostics published; a question about a stale document analyzes
//!   it first, so answers always match the text the editor has.
//! - **Positions** are LSP's lines and UTF-16 characters at the boundary,
//!   and byte offsets in a file everywhere else.
//! - **std:** going to a definition in std writes std's sources, once,
//!   to a directory in the system's temporary directory, for the editor
//!   to open, as built-in std has no files of its own.
//! - **Panics,** bugs in tempest, don't end the server: each request and
//!   each analysis runs under `catch_unwind`. A request that panics gets
//!   an error response, an analysis that panics an "internal error"
//!   diagnostic on its document, and the panic's message goes to stderr,
//!   which editors keep in the server's log. This needs unwinding, so
//!   native builds don't abort on panic (the workspace's Cargo.toml).

use lsp_types as lsp;
use serde::Serialize;
use serde::de::DeserializeOwned;
use serde_json::{Value, json};
use std::collections::{HashMap, HashSet};
use std::io::{self, BufRead, BufReader, Read, Write};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::PathBuf;
use std::sync::mpsc;
use std::time::{Duration, Instant};
use temper_analysis::{Analysis, DeclKind, Position, byte_offset};
use temper_frontend::library::{FileId, Level, Library};
use temper_frontend::project::{find_libraries, library_of, load_imports, load_library, work_root};
use temper_frontend::settle::settle;
use temper_frontend::sources::{Source, Sources};
use temper_frontend::{corelib, stdlib};

/// How long typing must pause before stale documents are analyzed again.
const DEBOUNCE: Duration = Duration::from_millis(250);

/// Reads one message's body, after its headers, or `None` at the end of
/// the input.
pub fn read_message(input: &mut impl BufRead) -> io::Result<Option<String>> {
    let mut length = None;
    let mut line = String::new();
    loop {
        line.clear();
        if input.read_line(&mut line)? == 0 {
            return Ok(None);
        }
        let header = line.trim_end_matches(['\r', '\n']);
        if header.is_empty() {
            if length.is_some() {
                break;
            }
            continue;
        }
        if let Some((name, value)) = header.split_once(':') {
            if name.trim().eq_ignore_ascii_case("content-length") {
                length = value.trim().parse::<usize>().ok();
            }
        }
    }
    let mut body = vec![0; length.unwrap_or(0)];
    input.read_exact(&mut body)?;
    String::from_utf8(body)
        .map(Some)
        .map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))
}

/// Writes one message, with its header.
pub fn write_message(out: &mut impl Write, body: &Value) -> io::Result<()> {
    let text = body.to_string();
    write!(out, "Content-Length: {}\r\n\r\n{text}", text.len())?;
    out.flush()
}

/// Serves an editor over `input` and `output` until it says `exit`,
/// returning the exit code: 0 after a `shutdown`, else 1. `std_dir`
/// reads std from a directory rather than the built-in copy.
pub fn serve(input: impl Read + Send + 'static, output: impl Write, std_dir: Option<String>) -> u8 {
    serve_with_hook(input, output, std_dir, None)
}

/// [`serve`], for tests, with a hook that makes a panic: an analysis of
/// a unit whose open documents contain `panic_on` panics, as does the
/// request `tempest/testPanic`.
#[doc(hidden)]
pub fn serve_with_hook(
    input: impl Read + Send + 'static,
    output: impl Write,
    std_dir: Option<String>,
    panic_on: Option<String>,
) -> u8 {
    let (send, receive) = mpsc::channel();
    // Messages are read on a thread of their own, so the server can wait
    // for one or for the debounce, whichever comes first.
    std::thread::spawn(move || {
        let mut input = BufReader::new(input);
        loop {
            let message = match read_message(&mut input) {
                Ok(Some(text)) => Some(serde_json::from_str::<Value>(&text)),
                Ok(None) | Err(_) => None,
            };
            let end = message.is_none();
            if send.send(message).is_err() || end {
                return;
            }
        }
    });
    let mut server = Server {
        out: output,
        std_dir,
        docs: Vec::new(),
        units: Vec::new(),
        stale: HashSet::new(),
        deadline: None,
        initialized: false,
        shut_down: false,
        std_copy: None,
        panic_on,
    };
    loop {
        let message = match server.deadline {
            Some(deadline) => {
                let wait = deadline.saturating_duration_since(Instant::now());
                match receive.recv_timeout(wait) {
                    Ok(m) => m,
                    Err(mpsc::RecvTimeoutError::Timeout) => {
                        server.deadline = None;
                        server.refresh();
                        continue;
                    }
                    Err(mpsc::RecvTimeoutError::Disconnected) => None,
                }
            }
            None => receive.recv().unwrap_or(None),
        };
        match message {
            None => return 1,
            Some(Err(why)) => server.send(&error_response(Value::Null, -32700, &why.to_string())),
            Some(Ok(message)) => {
                if message.get("method").and_then(Value::as_str) == Some("exit") {
                    return if server.shut_down { 0 } else { 1 };
                }
                server.handle(message);
            }
        }
    }
}

/// An open document.
struct Doc {
    path: String,
    uri: String,
    text: String,
}

/// What's analyzed together: a library, or a file on its own.
struct Unit {
    /// The library's root, or the lone file's path.
    key: String,
    /// Its own source files, to publish diagnostics for; not std's or
    /// other libraries' it imports.
    files: Vec<String>,
    analysis: Analysis<'static>,
    /// The files diagnostics were last published for.
    published: Vec<String>,
}

struct Server<W: Write> {
    out: W,
    std_dir: Option<String>,
    docs: Vec<Doc>,
    units: Vec<Unit>,
    /// Paths of documents changed since their unit was analyzed.
    stale: HashSet<String>,
    /// When to analyze stale documents again.
    deadline: Option<Instant>,
    initialized: bool,
    shut_down: bool,
    /// Where std's sources were written for the editor, once they were.
    std_copy: Option<PathBuf>,
    /// The test hook's text ([`serve_with_hook`]).
    panic_on: Option<String>,
}

/// A panic's message, from its payload.
fn panic_message(payload: &(dyn std::any::Any + Send)) -> String {
    match (
        payload.downcast_ref::<&str>(),
        payload.downcast_ref::<String>(),
    ) {
        (Some(text), _) => text.to_string(),
        (_, Some(text)) => text.clone(),
        _ => "a panic without a message".to_string(),
    }
}

/// What an editor is told about a panic, a bug in tempest.
fn internal_error(what: &str, message: &str) -> String {
    format!("internal error (a bug in Temper) {what}: {message}")
}

/// A request's failure: a JSON-RPC error code and message.
type Failure = (i32, String);

/// A request's params as LSP's type for them.
fn params<T: DeserializeOwned>(params: Value) -> Result<T, Failure> {
    serde_json::from_value(params).map_err(|why| (-32602, why.to_string()))
}

/// A request's result, as JSON.
fn answer(result: impl Serialize) -> Result<Value, Failure> {
    serde_json::to_value(result).map_err(|why| (-32603, why.to_string()))
}

impl<W: Write> Server<W> {
    fn send(&mut self, message: &Value) {
        let _ = write_message(&mut self.out, message);
    }

    fn handle(&mut self, mut message: Value) {
        let method = match message.get("method").and_then(Value::as_str) {
            Some(m) => m.to_string(),
            None => String::new(),
        };
        let params = message.get_mut("params").map_or(Value::Null, Value::take);
        let Some(id) = message.get_mut("id").map(Value::take) else {
            // A notification, or a response to us, which we never ask for.
            if !method.is_empty() {
                self.notified(&method, params);
            }
            return;
        };
        let result = match (self.initialized, method.as_str()) {
            (_, "initialize") => {
                self.initialized = true;
                answer(initialize_result())
            }
            (false, _) => Err((-32002, "not initialized yet".to_string())),
            (true, "shutdown") => {
                self.shut_down = true;
                Ok(Value::Null)
            }
            (true, method) => {
                let request = catch_unwind(AssertUnwindSafe(|| self.request(method, params)));
                request.unwrap_or_else(|payload| {
                    let why = internal_error(&format!("in {method}"), &panic_message(&*payload));
                    eprintln!("temper lsp: {why}");
                    Err((-32603, why))
                })
            }
        };
        let response = match result {
            Ok(result) => json!({"jsonrpc": "2.0", "id": id, "result": result}),
            Err((code, why)) => error_response(id, code, &why),
        };
        self.send(&response);
    }

    fn notified(&mut self, method: &str, params: Value) {
        match method {
            "textDocument/didOpen" => {
                let Ok(p) = serde_json::from_value::<lsp::DidOpenTextDocumentParams>(params) else {
                    return;
                };
                let uri = p.text_document.uri.as_str().to_string();
                let path = uri_to_path(&uri);
                self.docs.retain(|d| d.path != path);
                self.docs.push(Doc {
                    path: path.clone(),
                    uri,
                    text: p.text_document.text,
                });
                self.touch(path);
                // Opening shows a document's problems at once.
                self.deadline = Some(Instant::now());
            }
            "textDocument/didChange" => {
                let Ok(p) = serde_json::from_value::<lsp::DidChangeTextDocumentParams>(params)
                else {
                    return;
                };
                let path = uri_to_path(p.text_document.uri.as_str());
                if let Some(doc) = self.docs.iter_mut().find(|d| d.path == path) {
                    // Incremental sync: each change replaces a range, or,
                    // without one, the whole text.
                    for change in p.content_changes {
                        apply_change(&mut doc.text, change);
                    }
                    self.touch(path);
                }
            }
            "textDocument/didClose" => {
                let Ok(p) = serde_json::from_value::<lsp::DidCloseTextDocumentParams>(params)
                else {
                    return;
                };
                let path = uri_to_path(p.text_document.uri.as_str());
                self.docs.retain(|d| d.path != path);
                // A lone file closed has nothing to show; a library's
                // file goes back to what's on disk.
                if let Some(i) = self.units.iter().position(|u| u.key == path) {
                    let unit = self.units.remove(i);
                    for file in unit.published {
                        self.publish(&file, Vec::new());
                    }
                    self.stale.remove(&path);
                } else {
                    self.touch(path);
                }
            }
            _ => {}
        }
    }

    /// Marks a document's unit stale, to analyze again after a pause.
    fn touch(&mut self, path: String) {
        // The unit holding it, if analyzed, is what's stale.
        if let Some(unit) = self.units.iter().find(|u| u.files.contains(&path)) {
            self.stale.extend(unit.files.iter().cloned());
        }
        self.stale.insert(path);
        self.deadline = Some(Instant::now() + DEBOUNCE);
    }

    /// Analyzes every stale document's unit again, publishing its
    /// diagnostics.
    fn refresh(&mut self) {
        let open: Vec<String> = self.docs.iter().map(|d| d.path.clone()).collect();
        for path in open {
            if self.stale.contains(&path) {
                self.analyze(&path);
            }
        }
        // Stale files no longer open, in a library with others open, are
        // analyzed with them; those left have nothing open to show.
        self.stale.clear();
    }

    /// The analysis of the unit holding `path`, analyzing it first if it's
    /// stale or wasn't yet.
    fn current(&mut self, path: &str) -> Option<usize> {
        let found = self
            .units
            .iter()
            .position(|u| u.files.iter().any(|f| f == path));
        match found {
            Some(i) if !self.stale.contains(path) => Some(i),
            _ => self.analyze(path),
        }
    }

    /// Analyzes the unit holding `path` and publishes its diagnostics,
    /// giving its index.
    fn analyze(&mut self, path: &str) -> Option<usize> {
        let sources = self.sources();
        let dir = parent(path);
        let root = work_root(&sources, &dir);
        let libraries = find_libraries(&sources, &root);
        let (key, files, lib) = match library_of(&libraries, &dir) {
            Some(lib) if lib.files.iter().any(|f| f == path) => {
                (lib.root.clone(), lib.files.clone(), Some(lib))
            }
            _ => (path.to_string(), vec![path.to_string()], None),
        };
        // Reading parses, so it's under `catch_unwind` with the rest.
        let load = || -> Option<Library> {
            if let Some(lib) = lib {
                return load_library(&sources, lib, &libraries).ok();
            }
            let text = sources.read(path).ok()?.into_owned();
            let mut library = Library::new();
            library.add_file(path, &text, temper_frontend::project::source_kind(path));
            load_imports(&sources, &mut library);
            Some(library)
        };
        for file in &files {
            self.stale.remove(file);
        }
        let hooked = (self.panic_on.as_deref()).filter(|marker| {
            (self.docs.iter()).any(|d| files.contains(&d.path) && d.text.contains(marker))
        });
        let analyzed = catch_unwind(AssertUnwindSafe(|| {
            if let Some(marker) = hooked {
                panic!("test hook: a document contains {marker}");
            }
            let analysis = Analysis::new(settle(load()?));
            let by_file = diagnostics_by_file(&analysis, &files);
            Some((analysis, by_file))
        }));
        let (analysis, by_file) = match analyzed {
            Ok(analyzed) => analyzed?,
            Err(payload) => {
                // The unit's analysis, if any, no longer fits the text;
                // the next question about it analyzes it again.
                let why = internal_error(&format!("analyzing {key}"), &panic_message(&*payload));
                eprintln!("temper lsp: {why}");
                self.units
                    .retain(|u| !u.files.iter().any(|f| files.contains(f)));
                let diagnostic = lsp::Diagnostic {
                    range: lsp::Range::default(),
                    severity: Some(lsp::DiagnosticSeverity::ERROR),
                    source: Some("temper".to_string()),
                    message: why,
                    ..Default::default()
                };
                self.publish(path, vec![diagnostic]);
                return None;
            }
        };
        let old = match self.units.iter().position(|u| u.key == key) {
            Some(i) => self.units.remove(i).published,
            None => Vec::new(),
        };
        // A unit that took over a file from another (a lone file now in a
        // library, say) replaces it.
        self.units
            .retain(|u| !u.files.iter().any(|f| files.contains(f)));
        let published: Vec<String> = by_file.iter().map(|(f, _)| f.clone()).collect();
        for file in old.iter().filter(|f| !published.contains(f)) {
            self.publish(file, Vec::new());
        }
        for (file, diagnostics) in by_file {
            self.publish(&file, diagnostics);
        }
        self.units.push(Unit {
            key,
            files,
            analysis,
            published,
        });
        Some(self.units.len() - 1)
    }

    fn publish(&mut self, path: &str, diagnostics: Vec<lsp::Diagnostic>) {
        let Some(uri) = lsp_uri(&self.uri_of(path)) else {
            return;
        };
        let params = lsp::PublishDiagnosticsParams {
            uri,
            diagnostics,
            version: None,
        };
        let message = json!({
            "jsonrpc": "2.0",
            "method": "textDocument/publishDiagnostics",
            "params": params,
        });
        self.send(&message);
    }

    /// Where to read sources: open documents, then the disk, with std.
    fn sources(&self) -> Sources {
        let std = match &self.std_dir {
            Some(dir) => Source::Disk(dir.clone()),
            None => Source::Std,
        };
        let open = self.docs.iter().map(|d| (d.path.clone(), d.text.clone()));
        Sources::new()
            .mount(stdlib::DIR, std)
            .mount("", Source::Memory(open.collect()))
            .mount("", Source::Disk(String::new()))
    }

    /// A path's URI: an open document's as the editor gave it.
    fn uri_of(&self, path: &str) -> String {
        match self.docs.iter().find(|d| d.path == path) {
            Some(doc) => doc.uri.clone(),
            None => path_to_uri(path),
        }
    }

    fn request(&mut self, method: &str, p: Value) -> Result<Value, Failure> {
        match method {
            "textDocument/hover" => {
                let p: lsp::HoverParams = params(p)?;
                let (path, at) = position_of(&p.text_document_position_params);
                let found = self.query(&path, at, |_, a, file, offset| hover(a, file, offset));
                answer(found.flatten())
            }
            "textDocument/definition" => {
                let p: lsp::GotoDefinitionParams = params(p)?;
                let (path, at) = position_of(&p.text_document_position_params);
                let found = self.query(&path, at, |_, a, file, offset| {
                    let Some(target) = a.definition(file, offset) else {
                        // A builtin, in core's files.
                        let core = a.core_definition(file, offset)?;
                        let path = format!("{}/{}", corelib::DIR, core.path);
                        return Some((path, range(core.source, core.span.start, core.span.end)));
                    };
                    place(a, target.file, target.span)
                });
                let location = found.flatten().and_then(|(path, range)| {
                    let uri = lsp_uri(&self.location_uri(&path))?;
                    Some(lsp::GotoDefinitionResponse::Scalar(lsp::Location {
                        uri,
                        range,
                    }))
                });
                answer(location)
            }
            "textDocument/completion" => {
                let p: lsp::CompletionParams = params(p)?;
                let (path, at) = position_of(&p.text_document_position);
                let items = self.query(&path, at, |_, a, file, offset| {
                    (a.completions(file, offset).into_iter())
                        .map(|c| lsp::CompletionItem {
                            label: c.name,
                            kind: Some(completion_kind(c.kind)),
                            detail: c.detail,
                            ..Default::default()
                        })
                        .collect::<Vec<_>>()
                });
                answer(items.unwrap_or_default())
            }
            "textDocument/references" => {
                let p: lsp::ReferenceParams = params(p)?;
                let (path, at) = position_of(&p.text_document_position);
                let declaration = p.context.include_declaration;
                let found = self.query(&path, at, |_, a, file, offset| {
                    (a.references(file, offset, declaration).into_iter())
                        .filter_map(|l| place(a, l.file, l.span))
                        .collect::<Vec<_>>()
                });
                let locations: Vec<lsp::Location> = (found.unwrap_or_default().into_iter())
                    .filter_map(|(path, range)| {
                        let uri = lsp_uri(&self.location_uri(&path))?;
                        Some(lsp::Location { uri, range })
                    })
                    .collect();
                answer(locations)
            }
            "textDocument/prepareRename" => {
                let p: lsp::TextDocumentPositionParams = params(p)?;
                let (path, at) = position_of(&p);
                let found = self.query(&path, at, |source, a, file, offset| {
                    a.prepare_rename(file, offset).map(|(span, name)| {
                        lsp::PrepareRenameResponse::RangeWithPlaceholder {
                            range: range(source, span.start, span.end),
                            placeholder: name,
                        }
                    })
                });
                match found {
                    Some(Ok(found)) => answer(found),
                    Some(Err(why)) => Err((-32803, why)),
                    None => Ok(Value::Null),
                }
            }
            "textDocument/rename" => {
                let p: lsp::RenameParams = params(p)?;
                let (path, at) = position_of(&p.text_document_position);
                let new_name = p.new_name;
                let found = self.query(&path, at, |_, a, file, offset| {
                    a.rename(file, offset, &new_name).map(|edits| {
                        (edits.into_iter())
                            .filter_map(|e| Some((place(a, e.file, e.span)?, e.text)))
                            .collect::<Vec<_>>()
                    })
                });
                let edits = match found {
                    Some(Ok(edits)) => edits,
                    Some(Err(why)) => return Err((-32803, why)),
                    None => return Ok(Value::Null),
                };
                // Edits grouped by file, as `changes` keys them by URI.
                // `WorkspaceEdit` keys by `Uri`, whose cache clippy counts as
                // mutable; its hash and equality don't change.
                #[allow(clippy::mutable_key_type)]
                let mut changes: HashMap<lsp::Uri, Vec<lsp::TextEdit>> = HashMap::new();
                for ((path, range), new_text) in edits {
                    if let Some(uri) = lsp_uri(&self.uri_of(&path)) {
                        let edit = lsp::TextEdit { range, new_text };
                        changes.entry(uri).or_default().push(edit);
                    }
                }
                answer(lsp::WorkspaceEdit {
                    changes: Some(changes),
                    ..Default::default()
                })
            }
            "textDocument/documentSymbol" => {
                let p: lsp::DocumentSymbolParams = params(p)?;
                let path = uri_to_path(p.text_document.uri.as_str());
                let at = lsp::Position::new(0, 0);
                let found = self.query(&path, at, |source, a, file, _| {
                    let symbols = a.document_symbols(file);
                    symbols.iter().map(|s| doc_symbol(source, s)).collect()
                });
                answer(lsp::DocumentSymbolResponse::Nested(
                    found.unwrap_or_default(),
                ))
            }
            "textDocument/signatureHelp" => {
                let p: lsp::SignatureHelpParams = params(p)?;
                let (path, at) = position_of(&p.text_document_position_params);
                let found = self.query(&path, at, |_, a, file, offset| {
                    let help = a.signature_help(file, offset)?;
                    let parameters = (help.params.into_iter())
                        .map(|label| lsp::ParameterInformation {
                            label: lsp::ParameterLabel::Simple(label),
                            documentation: None,
                        })
                        .collect();
                    Some(lsp::SignatureHelp {
                        signatures: vec![lsp::SignatureInformation {
                            label: help.label,
                            documentation: help.doc.map(lsp::Documentation::String),
                            parameters: Some(parameters),
                            active_parameter: None,
                        }],
                        active_signature: Some(0),
                        active_parameter: Some(help.active as u32),
                    })
                });
                answer(found.flatten())
            }
            "tempest/testPanic" if self.panic_on.is_some() => panic!("test hook: {method}"),
            _ => Err((-32601, format!("no method {method}"))),
        }
    }

    /// Runs a query at a position in a document, against its unit's
    /// current analysis, with the position as a byte offset in its file.
    fn query<T>(
        &mut self,
        path: &str,
        at: lsp::Position,
        f: impl FnOnce(&str, &Analysis, FileId, u32) -> T,
    ) -> Option<T> {
        let i = self.current(path)?;
        let unit = &self.units[i];
        let library = &unit.analysis.settled.library;
        let file = library.files.iter().position(|f| f.path == path)?;
        let source = library.files[file].parsed.source();
        let offset = offset_at(source, at.line, at.character);
        Some(f(source, &unit.analysis, FileId(file as u32), offset))
    }

    /// A URI for a definition's file: std's or core's, written out for
    /// the editor.
    fn location_uri(&mut self, path: &str) -> String {
        if let Some(rest) = path.strip_prefix(&format!("{}/", corelib::DIR)) {
            let dir = self.std_copy.get_or_insert_with(write_std);
            let file = dir.join(CORE_SUBDIR).join(rest);
            return path_to_uri(&file.to_string_lossy().replace('\\', "/"));
        }
        let Some(rest) = path.strip_prefix(&format!("{}/", stdlib::DIR)) else {
            return self.uri_of(path);
        };
        if let Some(dir) = &self.std_dir {
            return path_to_uri(&format!("{}/{rest}", dir.trim_end_matches('/')));
        }
        let dir = self.std_copy.get_or_insert_with(write_std);
        path_to_uri(&dir.join(rest).to_string_lossy().replace('\\', "/"))
    }
}

/// An analysis's diagnostics in each of `files`, as LSP's, for each
/// file, even with none.
fn diagnostics_by_file(
    analysis: &Analysis,
    files: &[String],
) -> Vec<(String, Vec<lsp::Diagnostic>)> {
    let mut by_file: Vec<(String, Vec<lsp::Diagnostic>)> =
        files.iter().map(|f| (f.clone(), Vec::new())).collect();
    for problem in analysis.diagnostics() {
        let Some(file) = analysis.settled.library.files.get(problem.file.0 as usize) else {
            continue;
        };
        let Some(entry) = by_file.iter_mut().find(|(f, _)| *f == file.path) else {
            continue;
        };
        let source = file.parsed.source();
        entry.1.push(lsp::Diagnostic {
            range: range(source, problem.span.start, problem.span.end),
            severity: Some(match problem.level {
                Level::Error => lsp::DiagnosticSeverity::ERROR,
                Level::Warn => lsp::DiagnosticSeverity::WARNING,
                Level::Hint => lsp::DiagnosticSeverity::HINT,
            }),
            source: Some("temper".to_string()),
            message: problem.message,
            ..Default::default()
        });
    }
    by_file
}

/// A document's path and a position in it.
fn position_of(p: &lsp::TextDocumentPositionParams) -> (String, lsp::Position) {
    (uri_to_path(p.text_document.uri.as_str()), p.position)
}

/// A URI as LSP's type, if it's a valid one.
fn lsp_uri(uri: &str) -> Option<lsp::Uri> {
    uri.parse().ok()
}

/// Applies one of `didChange`'s changes to a document's text.
pub fn apply_change(text: &mut String, change: lsp::TextDocumentContentChangeEvent) {
    match change.range {
        Some(r) => {
            let start = offset_at(text, r.start.line, r.start.character) as usize;
            let end = (offset_at(text, r.end.line, r.end.character) as usize).max(start);
            text.replace_range(start..end, &change.text);
        }
        None => *text = change.text,
    }
}

/// Where core's files go in the directory [`write_std`] writes, beside
/// std's modules, none of which is named so.
const CORE_SUBDIR: &str = "core";

/// Writes built-in std's sources, and core's in [`CORE_SUBDIR`], to a
/// directory named by their hash in the system's temporary directory,
/// once, read-only, giving it.
fn write_std() -> PathBuf {
    let core = corelib::FILES
        .iter()
        .map(|(p, t)| (format!("{CORE_SUBDIR}/{p}"), *t));
    let files: Vec<(String, &str)> = (stdlib::FILES.iter())
        .map(|(p, t)| (p.to_string(), *t))
        .chain(core)
        .collect();
    // FNV-1a over every path and text.
    let mut hash: u64 = 0xcbf29ce484222325;
    for (path, text) in &files {
        for b in path.bytes().chain([0]).chain(text.bytes()) {
            hash = (hash ^ b as u64).wrapping_mul(0x100000001b3);
        }
    }
    let dir = std::env::temp_dir().join(format!("temper-std-{hash:016x}"));
    for (path, text) in &files {
        let file = dir.join(path);
        if file.exists() {
            continue;
        }
        if let Some(parent) = file.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        if std::fs::write(&file, text).is_ok() {
            if let Ok(meta) = std::fs::metadata(&file) {
                let mut permissions = meta.permissions();
                permissions.set_readonly(true);
                let _ = std::fs::set_permissions(&file, permissions);
            }
        }
    }
    dir
}

/// A span in a file of an analysis, as the file's path and an LSP range.
fn place(
    a: &Analysis,
    file: FileId,
    span: temper_syntax::tree::Span,
) -> Option<(String, lsp::Range)> {
    let f = a.settled.library.files.get(file.0 as usize)?;
    Some((
        f.path.clone(),
        range(f.parsed.source(), span.start, span.end),
    ))
}

/// An outline's entry as an LSP `DocumentSymbol`.
// `deprecated` is a field LSP keeps for older clients; it stays unset.
#[allow(deprecated)]
fn doc_symbol(source: &str, s: &temper_analysis::DocSymbol) -> lsp::DocumentSymbol {
    use lsp::SymbolKind as K;
    let kind = match s.kind {
        DeclKind::Class => K::CLASS,
        DeclKind::Method => K::METHOD,
        DeclKind::Getter | DeclKind::Setter => K::PROPERTY,
        DeclKind::Field => K::FIELD,
        DeclKind::Constructor => K::CONSTRUCTOR,
        DeclKind::Interface => K::INTERFACE,
        DeclKind::Function => K::FUNCTION,
        DeclKind::Let | DeclKind::Var | DeclKind::Parameter | DeclKind::Builtin => K::VARIABLE,
        DeclKind::TypeParameter => K::TYPE_PARAMETER,
    };
    lsp::DocumentSymbol {
        name: s.name.clone(),
        detail: s.detail.clone(),
        kind,
        tags: None,
        deprecated: None,
        range: range(source, s.span.start, s.span.end),
        selection_range: range(source, s.selection.start, s.selection.end),
        children: Some(s.children.iter().map(|c| doc_symbol(source, c)).collect()),
    }
}

/// What the server can do, for `initialize`.
fn initialize_result() -> lsp::InitializeResult {
    lsp::InitializeResult {
        capabilities: lsp::ServerCapabilities {
            text_document_sync: Some(lsp::TextDocumentSyncCapability::Options(
                lsp::TextDocumentSyncOptions {
                    open_close: Some(true),
                    change: Some(lsp::TextDocumentSyncKind::INCREMENTAL),
                    ..Default::default()
                },
            )),
            hover_provider: Some(lsp::HoverProviderCapability::Simple(true)),
            definition_provider: Some(lsp::OneOf::Left(true)),
            completion_provider: Some(lsp::CompletionOptions {
                trigger_characters: Some(vec![".".to_string()]),
                ..Default::default()
            }),
            references_provider: Some(lsp::OneOf::Left(true)),
            rename_provider: Some(lsp::OneOf::Right(lsp::RenameOptions {
                prepare_provider: Some(true),
                work_done_progress_options: Default::default(),
            })),
            document_symbol_provider: Some(lsp::OneOf::Left(true)),
            signature_help_provider: Some(lsp::SignatureHelpOptions {
                trigger_characters: Some(vec!["(".to_string(), ",".to_string()]),
                ..Default::default()
            }),
            ..Default::default()
        },
        server_info: Some(lsp::ServerInfo {
            name: "temper".to_string(),
            version: None,
        }),
    }
}

fn error_response(id: Value, code: i32, message: &str) -> Value {
    json!({"jsonrpc": "2.0", "id": id, "error": {"code": code, "message": message}})
}

/// Hover's answer: a Markdown code block with what the name is and its
/// type, then its doc comment.
fn hover(a: &Analysis, file: FileId, offset: u32) -> Option<lsp::Hover> {
    let h = a.hover(file, offset)?;
    let source = a
        .settled
        .library
        .files
        .get(file.0 as usize)?
        .parsed
        .source();
    let mut value = format!("```temper\n{}\n```", h.headline());
    if let Some(doc) = &h.doc {
        value.push_str("\n\n");
        value.push_str(doc);
    }
    Some(lsp::Hover {
        contents: lsp::HoverContents::Markup(lsp::MarkupContent {
            kind: lsp::MarkupKind::Markdown,
            value,
        }),
        range: Some(range(source, h.span.start, h.span.end)),
    })
}

/// LSP's `CompletionItemKind` for a declaration's kind.
fn completion_kind(kind: DeclKind) -> lsp::CompletionItemKind {
    use lsp::CompletionItemKind as K;
    match kind {
        DeclKind::Method => K::METHOD,
        DeclKind::Function => K::FUNCTION,
        DeclKind::Constructor => K::CONSTRUCTOR,
        DeclKind::Field => K::FIELD,
        DeclKind::Let | DeclKind::Var | DeclKind::Parameter => K::VARIABLE,
        DeclKind::Class => K::CLASS,
        DeclKind::Interface => K::INTERFACE,
        DeclKind::Getter | DeclKind::Setter => K::PROPERTY,
        DeclKind::Builtin => K::VALUE,
        DeclKind::TypeParameter => K::TYPE_PARAMETER,
    }
}

/// An LSP position: a line from 0 and a UTF-16 character in it.
pub fn position(source: &str, offset: u32) -> lsp::Position {
    let p = Position::of(source, offset as usize);
    lsp::Position::new((p.line - 1) as u32, (p.column - 1) as u32)
}

/// An LSP range for a span of bytes.
pub fn range(source: &str, start: u32, end: u32) -> lsp::Range {
    lsp::Range::new(position(source, start), position(source, end))
}

/// The byte offset of an LSP position, clamped to its line and the text.
pub fn offset_at(source: &str, line: u32, character: u32) -> u32 {
    let mut start = 0;
    for _ in 0..line {
        match source[start..].find('\n') {
            Some(i) => start += i + 1,
            None => return source.len() as u32,
        }
    }
    let end = source[start..]
        .find('\n')
        .map_or(source.len(), |i| start + i);
    let line_text = source[start..end].trim_end_matches('\r');
    (start + byte_offset(line_text, character as usize)) as u32
}

/// A `file:` URI's path, `/`-joined, with `%` escapes decoded; other
/// URIs as they are.
pub fn uri_to_path(uri: &str) -> String {
    let Some(rest) = uri.strip_prefix("file://") else {
        return uri.to_string();
    };
    // Skip an authority, as `file://host/path`; `localhost` is local.
    let rest = match rest.find('/') {
        Some(i) => &rest[i..],
        None => rest,
    };
    let mut bytes = Vec::with_capacity(rest.len());
    let raw = rest.as_bytes();
    let mut i = 0;
    while i < raw.len() {
        let hex = (raw[i] == b'%')
            .then(|| rest.get(i + 1..i + 3))
            .flatten()
            .and_then(|h| u8::from_str_radix(h, 16).ok());
        match hex {
            Some(b) => {
                bytes.push(b);
                i += 3;
            }
            None => {
                bytes.push(raw[i]);
                i += 1;
            }
        }
    }
    let path = String::from_utf8_lossy(&bytes).into_owned();
    // `/c:/x` on Windows is `C:/x`: the drive in upper case, as the
    // frontend keeps paths (VS Code sends it lower).
    let windows = path.len() > 2 && path.as_bytes()[2] == b':';
    let path = if windows {
        temper_frontend::project::upper_drive(&path[1..])
    } else {
        path
    };
    normalized(&path)
}

/// A path's `file:` URI, escaping what URIs must.
pub fn path_to_uri(path: &str) -> String {
    let path = path.replace('\\', "/");
    let mut uri = String::from("file://");
    if !path.starts_with('/') {
        uri.push('/');
    }
    for (i, b) in path.bytes().enumerate() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'.' | b'_' | b'~' | b'/' => {
                uri.push(b as char)
            }
            // A Windows drive's, as `c:`.
            b':' if i == 1 => uri.push(':'),
            _ => uri.push_str(&format!("%{b:02X}")),
        }
    }
    uri
}

/// A path without `.` and `..` parts or doubled `/`s.
fn normalized(path: &str) -> String {
    let mut parts: Vec<&str> = Vec::new();
    for part in path.split('/') {
        match part {
            "" | "." => {}
            ".." => {
                parts.pop();
            }
            part => parts.push(part),
        }
    }
    let joined = parts.join("/");
    match path.starts_with('/') {
        true => format!("/{joined}"),
        false => joined,
    }
}

/// A path's directory.
fn parent(path: &str) -> String {
    match path.rfind('/') {
        Some(0) => "/".to_string(),
        Some(i) => path[..i].to_string(),
        None => ".".to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn positions_in_utf16() {
        let source = "let a = \"😀\";\r\nlet b = a;\n";
        let b = source.find("b =").unwrap() as u32;
        assert_eq!(position(source, b), lsp::Position::new(1, 4));
        assert_eq!(offset_at(source, 1, 4), b);
        // After the emoji, two UTF-16 units.
        let after = source.find("\";").unwrap() as u32;
        assert_eq!(offset_at(source, 0, 11), after);
        // Clamped to the line, before its `\r`.
        assert_eq!(offset_at(source, 0, 99), source.find('\r').unwrap() as u32);
        assert_eq!(offset_at(source, 9, 0), source.len() as u32);
    }

    #[test]
    fn uris() {
        assert_eq!(
            uri_to_path("file:///home/a%20b/x.temper"),
            "/home/a b/x.temper"
        );
        assert_eq!(uri_to_path("file:///c%3A/x/../y.temper"), "C:/y.temper");
        assert_eq!(uri_to_path("file:///C:/y.temper"), "C:/y.temper");
        assert_eq!(
            path_to_uri("/home/a b/x.temper"),
            "file:///home/a%20b/x.temper"
        );
        assert_eq!(path_to_uri("c:/y.temper"), "file:///c:/y.temper");
        let odd = "/tmp/é#?.temper";
        assert_eq!(uri_to_path(&path_to_uri(odd)), odd);
    }

    #[test]
    fn incremental_changes() {
        let mut text = "let a = \"😀\";\nlet b = a;\n".to_string();
        let change = |range: Option<((u32, u32), (u32, u32))>, text: &str| {
            lsp::TextDocumentContentChangeEvent {
                range: range.map(|((l0, c0), (l1, c1))| {
                    lsp::Range::new(lsp::Position::new(l0, c0), lsp::Position::new(l1, c1))
                }),
                range_length: None,
                text: text.to_string(),
            }
        };
        // `b` to `bee`, then after the emoji's two UTF-16 units, and across
        // the line break.
        apply_change(&mut text, change(Some(((1, 4), (1, 5))), "bee"));
        apply_change(&mut text, change(Some(((0, 11), (0, 11))), "!"));
        assert_eq!(text, "let a = \"😀!\";\nlet bee = a;\n");
        apply_change(&mut text, change(Some(((0, 14), (1, 0))), " "));
        assert_eq!(text, "let a = \"😀!\"; let bee = a;\n");
        // Without a range, the whole text.
        apply_change(&mut text, change(None, "x"));
        assert_eq!(text, "x");
    }

    #[test]
    fn frames_messages() {
        let mut out = Vec::new();
        write_message(&mut out, &json!({"a": "é"})).unwrap();
        let text = String::from_utf8(out.clone()).unwrap();
        assert!(text.starts_with("Content-Length: 10\r\n\r\n"), "{text}");
        let mut input = io::Cursor::new([out.clone(), out].concat());
        assert_eq!(
            read_message(&mut input).unwrap().as_deref(),
            Some(r#"{"a":"é"}"#)
        );
        assert_eq!(
            read_message(&mut input).unwrap().as_deref(),
            Some(r#"{"a":"é"}"#)
        );
        assert_eq!(read_message(&mut input).unwrap(), None);
    }
}
