//! `let { a, b as c } = import("./dir")`: names another module of the
//! library exports.
//!
//! A module is a directory's files ([`crate::library::Module`]), and a
//! relative path names one from the importing module's directory. Reading
//! the declaration waits until the imported module's scope is complete;
//! then each target becomes `let c = b`, with `b` already resolved to the
//! exported symbol, as macros refer to builtins. The interpreter runs each
//! module after those it imports, and modules' declarations are its
//! globals, so reading `b` finds its value. A cycle of imports gets stuck, and the forced
//! import fails.
//!
//! `let { ... } = import(…)` imports every export, as do `...` beside
//! named ones, for those not named. A path starting `std/` names a
//! module of the standard library, under
//! [`Library::std_root`](crate::library::Library::std_root).
//!
//! A bare `import("./tests");` statement, as Kotlin allows, only makes the
//! module part of the library, which
//! [`Library::missing_imports`](crate::library::Library::missing_imports)
//! already did, so it becomes an empty block. With no names to read, it
//! doesn't order the modules: it often names a tests module that imports
//! the one naming it, which would be a cycle.
//!
//! Not yet: checking that `export` is only on top-level declarations.

use crate::builtins::{Builtin, Macro};
use crate::library::{Message, ModuleId};
use crate::scope::{Lookup, ScopeId, SymbolKind};
use crate::settle::{Fact, Settle};
use temper_syntax::ast::Value;
use temper_syntax::tree::{NodeId, NodeKind};

impl Settle {
    /// The statements an import declaration stands for, `None` if `decl`
    /// isn't one, or the fact to wait for. A malformed or failed import
    /// becomes an error, which stands for itself.
    pub(crate) fn import(
        &mut self,
        decl: NodeId,
        scope: ScopeId,
        forced: bool,
    ) -> Result<Option<Vec<NodeId>>, Fact> {
        let Some(call) = self.destructured_init(decl) else {
            return Ok(None);
        };
        let tree = &self.lib.tree;
        let children = tree.children(call);
        let Some(&callee) = children
            .first()
            .filter(|_| tree.kind(call) == NodeKind::Call)
        else {
            return Ok(None);
        };
        if tree.kind(callee) != NodeKind::RightName {
            return Ok(None);
        }
        let path = match children[1..] {
            [arg] => match self.lib.value(arg) {
                Some(Value::String(path)) => Some(path.clone()),
                _ => None,
            },
            _ => None,
        };
        let name = self.lib.name(callee).unwrap();
        match self.lookup(scope, name, callee, true, forced) {
            Lookup::Wait(s) => return Err(Fact::ScopeComplete(s)),
            Lookup::Found(b)
                if self.scopes.symbol(b).kind
                    == SymbolKind::Builtin(Builtin::Macro(Macro::Import)) =>
            {
                self.scopes.resolve(callee, b);
            }
            _ => return Ok(None),
        }
        let from = self
            .modules
            .iter()
            .position(|&s| s == scope)
            .map(|m| ModuleId(m as u32));
        let (Some(from), Some(path)) = (from, path) else {
            self.error(decl, Message::MalformedSpecial("import".into()));
            return Ok(Some(vec![decl]));
        };
        let Some(dir) = self.lib.import_dir(from, &path) else {
            self.error(decl, Message::Unsupported(format!("importing {path}")));
            return Ok(Some(vec![decl]));
        };
        let target = self.lib.module_at(&dir);
        let Some(target) = target.filter(|&t| t != from) else {
            self.error(decl, Message::ImportFailed(path));
            return Ok(Some(vec![decl]));
        };
        let target_scope = self.modules[target.0 as usize];
        if !self.scopes.get(target_scope).complete {
            if !forced {
                return Err(Fact::ScopeComplete(target_scope));
            }
            // Stuck, so in a cycle of imports.
            self.error(decl, Message::ImportFailed(path));
            return Ok(Some(vec![decl]));
        }
        let Some((mut each, spread, _)) = self.destructured(decl) else {
            return Ok(Some(vec![decl]));
        };
        if spread.is_some() {
            // `...` imports every other export, under its own name.
            let at = self.lib.tree.span(decl);
            let names = self.scopes.get(target_scope).names.clone();
            for (name, b) in names {
                if self.scopes.symbol(b).exported && !each.iter().any(|&(n, ..)| n == name) {
                    let target = self.lib.add_name(NodeKind::LeftName, name, at, decl);
                    each.push((name, target, None));
                }
            }
        }
        self.imports.push((from, target));
        let span = self.lib.tree.span(decl);
        let mut statements = Vec::with_capacity(each.len());
        for (name, target_name, ty) in each {
            let at = self.lib.tree.span(target_name);
            let exported = self
                .scopes
                .get(target_scope)
                .names
                .iter()
                .rev()
                .find(|&&(n, b)| n == name && self.scopes.symbol(b).exported);
            let value = match exported {
                Some(&(_, b)) => self.builtin_name(b, at, decl),
                None => {
                    let node = self.lib.add_name(NodeKind::RightName, name, at, decl);
                    let text = self.lib.names.get(name).to_string();
                    self.error(node, Message::NotExported(path.clone(), text));
                    node
                }
            };
            statements.push(self.make_let(target_name, ty, Some(value), 0, span, decl));
        }
        Ok(Some(statements))
    }

    /// Expands a bare `import(path)` call, giving whether `path` was a
    /// string, and reporting a module that didn't load.
    pub(crate) fn bare_import(&mut self, call: NodeId, path: NodeId) -> bool {
        let Some(Value::String(path)) = self.lib.value(path) else {
            return false;
        };
        let path = path.clone();
        let span = self.lib.tree.span(call);
        let from = self.lib.files[self.lib.locate(span.start).0.0 as usize].module;
        let Some(dir) = self.lib.import_dir(from, &path) else {
            self.error(call, Message::Unsupported(format!("importing {path}")));
            return true;
        };
        if self.lib.module_at(&dir).is_none_or(|m| m == from) {
            self.error(call, Message::ImportFailed(path));
            return true;
        }
        self.rewrite(call, NodeKind::Block, 0, &[]);
        true
    }
}
