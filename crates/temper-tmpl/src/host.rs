//! A library's host code: the files of backend code beside its modules
//! (`__connected__.py`, `_connected.rs`, `Counter.java`, …) that
//! implement its own `@connected` functions and classes, and which of
//! those each backend takes from host code rather than from their Temper
//! implementations, as Kotlin Temper's `ConnectedChoice.kt` decides
//! (notes/tmpl/names.md, "Connected declarations").
//!
//! - A connected declaration without a Temper implementation (a bodiless
//!   function, or a class of bodiless methods) always uses host code.
//! - With one, `--no-connected` ([`Host::no_connected`]) uses it on every
//!   backend; otherwise a declaration that lists no backends uses host
//!   code everywhere, and one that lists some uses it on those, and its
//!   Temper implementation elsewhere.
//!
//! As in Kotlin, the choice doesn't look at which files are there: a
//! listed backend whose host code is missing fails in its own compiler or
//! at its first call. Core's and std's connected declarations resolve by
//! qname ([`crate::qname::resolves_by_qname`]) and aren't affected.
//!
//! Host files are given by the build, beside the program; tmpl's text form
//! doesn't carry them.

use crate::{DeclId, DeclKind, Facts, Kind, Node, Program, qname};

/// A file of host code: its path relative to the library's root, as
/// `_connected.rs` or `sub/__connected__.py`, and its text.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct HostFile {
    pub path: String,
    pub text: String,
}

/// A library's host files, and whether the build asked for Temper
/// implementations wherever there are any (`--no-connected`).
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Host {
    /// Every file under the library's root that isn't Temper, sorted by
    /// path; each backend picks those with its extensions ([`files`]).
    pub files: Vec<HostFile>,
    pub no_connected: bool,
}

/// Whether `decl` is a user's connected function or class: connected,
/// and not core's or std's.
pub fn is_user_connected(p: &Program, decl: DeclId) -> bool {
    p.decls.contains(decl)
        && p.decls.get(decl).facts.contains(Facts::CONNECTED)
        && !qname::resolves_by_qname(p, decl)
}

/// Whether a user's connected declaration has a Temper implementation: a
/// class unless its methods are `AbstractMethod`s (all are, or the
/// frontend reported an error), and a function unless it's a
/// `BodilessFunction`.
pub fn has_temper_implementation(p: &Program, decl: DeclId) -> bool {
    let items = || (p.modules.iter()).flat_map(|m| m.all_items());
    match p.decls.get(decl).kind {
        DeclKind::Class => !items().any(|item| match p.nodes.get(item) {
            Node::Class { decl: d, members } if d == decl => {
                (p.nodes.list(members).iter()).any(|&m| p.nodes.kind(m) == Kind::AbstractMethod)
            }
            _ => false,
        }),
        _ => !items().any(|item| {
            p.nodes.kind(item) == Kind::BodilessFunction && p.nodes.slots(item).0 == decl.0
        }),
    }
}

/// Whether a backend, known by `names` (as `["py"]`), uses host code for
/// `decl`, rather than its Temper implementation: only for a user's
/// connected declaration, per this module's rules.
pub fn uses_host(p: &Program, decl: DeclId, names: &[&str]) -> bool {
    if !is_user_connected(p, decl) {
        return false;
    }
    if !has_temper_implementation(p, decl) {
        return true;
    }
    if p.host.no_connected {
        return false;
    }
    let listed = p.decls.backends(decl);
    listed.is_empty() || listed.iter().any(|&b| names.contains(&p.strings.get(b)))
}

/// The host files with one of these extensions (as `.py`), sorted by path.
pub fn files<'a>(p: &'a Program, extensions: &[&str]) -> Vec<&'a HostFile> {
    (p.host.files.iter())
        .filter(|f| extensions.iter().any(|e| f.path.ends_with(e)))
        .collect()
}

/// The host files named for a user's connected class that a backend
/// generates from its Temper implementation instead, as `Counter.java`
/// beside its module: those a backend whose classes live in files of their
/// own (Java, C#) leaves out, so as not to define the class twice, as
/// Kotlin's `connectedFilesReplacedByFallbacks` does.
pub fn replaced_by_fallbacks(p: &Program, names: &[&str], extension: &str) -> Vec<String> {
    let mut found = Vec::new();
    for module in &p.modules {
        for id in p.decls.ids() {
            let d = p.decls.get(id);
            if d.kind != DeclKind::Class || d.owner != module.decl {
                continue;
            }
            if is_user_connected(p, id) && !uses_host(p, id, names) {
                let name = p.strings.get(d.name);
                found.push(match module.path.as_str() {
                    "" => format!("{name}{extension}"),
                    dir => format!("{dir}/{name}{extension}"),
                });
            }
        }
    }
    found
}
