//! A library's own connected functions and classes that take host code,
//! as Kotlin's Rust backend writes them. A module's host code is its
//! directory's `_connected.rs`, the crate's module `_connected` (the root
//! module's at the crate root, `src/_connected/mod.rs`, another's inside
//! its own module), with the library's other `.rs` files there beside it
//! (`lib.rs` copies them). A function there is a wrapper, which applies
//! its parameters' defaults and calls the host's function of its name in
//! snake case (`crate::_connected::sum(i, j, bonus)`); a class is a
//! re-export of the host's (`pub use crate::_connected::Counter;`).

use super::{Params, R, Translator, not_yet};
use crate::tree::{Block, Expr, Item, Stmt};
use temper_tmpl::analysis::defaults;
use temper_tmpl::host::uses_host;
use temper_tmpl::naming::snake_case;
use temper_tmpl::{DeclId, Kids, Module, Node, NodeId, TypeId};

/// The names by which a connected declaration's backend list can mean this
/// backend, as Kotlin's `connectedBackendNames`.
pub const NAMES: &[&str] = &["rust"];

/// The name of host code's module, and of its file's directory.
pub const CONNECTED: &str = "_connected";

/// Where a module's host code is, relative to the library's root:
/// `_connected.rs` in its directory.
pub fn host_path(module: &Module) -> String {
    match module.path.as_str() {
        "" => format!("{CONNECTED}.rs"),
        dir => format!("{dir}/{CONNECTED}.rs"),
    }
}

impl Translator<'_> {
    /// Whether this backend takes `decl` from host code, failing where it
    /// would but the module has none, and noting that the module uses it.
    pub(crate) fn takes_host(&mut self, module: &Module, decl: DeclId) -> R<bool> {
        let p = self.p;
        if !uses_host(p, decl, NAMES) {
            return Ok(false);
        }
        let path = host_path(module);
        if !p.host.files.iter().any(|f| f.path == path) {
            return not_yet(format!("{}'s host code, with no {path}", p.name(decl)));
        }
        self.host_dirs.insert(module.path.clone());
        Ok(true)
    }

    /// How code in this module names its host code's module: from the
    /// crate root for the root module's, else its own.
    fn host_module(&self, module: &Module) -> &'static str {
        if module.path.is_empty() {
            "crate::_connected"
        } else {
            CONNECTED
        }
    }

    /// A function whose host code runs instead of its Temper body, as
    /// Kotlin's: its signature, its parameters' defaults, then a call of
    /// the host's function, passing each parameter as given (strings and
    /// lists as their `impl` traits), or its defaulted local.
    pub(crate) fn host_wrapper(
        &mut self,
        module: &Module,
        item: NodeId,
        decl: DeclId,
        params: Kids,
    ) -> R<Item> {
        let p = self.p;
        let Some(sig) = p.signature(decl) else {
            return not_yet("a function without a signature");
        };
        self.begin(item, sig.fail);
        let mut ps = Vec::new();
        let mut conversions = Vec::new();
        self.params_into(item, params, Params::Free, &mut ps, &mut conversions)?;
        // The wrapper assigns no parameter, whatever its Temper body does.
        let ps = (ps.into_iter())
            .map(|s| s.strip_prefix("mut ").map(str::to_string).unwrap_or(s))
            .collect();
        let defaults = match p.nodes.get(item) {
            Node::Function { body, .. } => defaults(p, body),
            _ => Vec::new(),
        };
        // Only defaults read a parameter here, so only theirs convert.
        let defaulted: Vec<String> = defaults.iter().map(|d| self.name(d.param)).collect();
        let mut stmts: Vec<Stmt> = (conversions.into_iter())
            .filter_map(|s| match s {
                Stmt::Let {
                    name, ty, value, ..
                } if defaulted.contains(&name) => Some(Stmt::Let {
                    name,
                    mutable: false,
                    ty,
                    value,
                }),
                _ => None,
            })
            .collect();
        for d in &defaults {
            self.stmt(d.stmt, &mut stmts)?;
        }
        let args = (p.nodes.list(params).iter())
            .filter_map(|&param| match p.nodes.get(param) {
                Node::Param { decl } => Some(decl),
                _ => None,
            })
            .map(|param| {
                let local = defaults.iter().find(|d| d.param == param);
                Expr::path(self.name(local.map_or(param, |d| d.local)))
            })
            .collect();
        let host = format!("{}::{}", self.host_module(module), snake_case(p.name(decl)));
        let call = Expr::path(host).call(args);
        let pass = match sig.pass == TypeId::VOID {
            true => None,
            false => Some(self.ty(sig.pass)?),
        };
        let result = match sig.fail {
            Some(fail) => Some(self.result_of(pass, fail)?),
            None => pass,
        };
        let (generics, _) = self.type_params(decl)?;
        Ok(Item::Fn {
            vis: self.vis(decl),
            name: format!("{}{generics}", self.name(decl)),
            params: ps,
            result,
            body: Block::new(stmts, Some(call)),
        })
    }

    /// A class that host code defines: a re-export of the host's, by its
    /// Temper name, as Kotlin's.
    pub(crate) fn host_class(&self, module: &Module, decl: DeclId) -> Item {
        let host = self.p.name(decl);
        let name = self.name(decl);
        let path = format!("{}::{host}", self.host_module(module));
        Item::Reexport {
            vis: self.vis(decl),
            path: if name == host {
                path
            } else {
                format!("{path} as {name}")
            },
        }
    }
}
