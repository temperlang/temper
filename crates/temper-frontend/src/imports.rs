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
//! `let rx = import("std/regex")` binds a module namespace instead
//! ([`crate::scope::Namespace`]), once the module's scope is complete,
//! as for destructuring: no statement, just a name, only for `rx.member`,
//! which becomes a name resolved to that export, as destructuring's
//! `let member = member'` would be followed to it. So the namespace never
//! reaches the interpreter or tmpl, and code that names exports through
//! one translates exactly as code that imports them by destructuring.
//! It's an error to use `rx` any other way, or to export or type it.
//!
//! `import("core")` names core, whose exports are the builtins (but for
//! macros and operators), which no module holds: `let core =
//! import("core")` qualifies them where a module declares the same name,
//! as `core.String`, and `let { String as CoreString } = import("core")`
//! renames one. Core's names are in scope already, so `...` is an error.
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
use crate::scope::{BUILTINS, Lookup, Namespace, ScopeId, SymbolKind};
use crate::settle::{Fact, Settle};
use temper_syntax::ast::Value;
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind};

/// Where an import leads: the importing module and the imported one,
/// for the order modules run in, and the imported module's scope; for
/// core, no module, and the builtins' scope.
struct Imported {
    edge: Option<(ModuleId, ModuleId)>,
    scope: ScopeId,
}

impl Settle {
    /// The path an `import(…)` call imports, `Some(None)` if it's
    /// malformed, or `None` if `call` isn't an `import` call; or the fact
    /// to wait for to tell.
    fn import_path(
        &mut self,
        call: NodeId,
        scope: ScopeId,
        forced: bool,
    ) -> Result<Option<Option<String>>, Fact> {
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
            Lookup::Wait(s) => Err(Fact::ScopeComplete(s)),
            Lookup::Found(b)
                if self.scopes.symbol(b).kind
                    == SymbolKind::Builtin(Builtin::Macro(Macro::Import)) =>
            {
                self.scopes.resolve(callee, b);
                Ok(Some(path))
            }
            _ => Ok(None),
        }
    }

    /// The module that `decl`, an import at the top level of `scope`,
    /// imports from `path`, once its scope is complete, or the fact to
    /// wait for. `None` if it failed, reported as an error on `decl`.
    fn imported(
        &mut self,
        decl: NodeId,
        path: Option<String>,
        scope: ScopeId,
        forced: bool,
    ) -> Result<Option<Imported>, Fact> {
        let from = self
            .modules
            .iter()
            .position(|&s| s == scope)
            .map(|m| ModuleId(m as u32));
        let (Some(from), Some(path)) = (from, path) else {
            self.error(decl, Message::MalformedSpecial("import".into()));
            return Ok(None);
        };
        // Core, whose names are the builtins, which no module holds.
        if path == "core" {
            return Ok(Some(Imported {
                edge: None,
                scope: BUILTINS,
            }));
        }
        let Some(dir) = self.lib.import_dir(from, &path) else {
            self.error(decl, Message::Unsupported(format!("importing {path}")));
            return Ok(None);
        };
        let target = self.lib.module_at(&dir);
        let Some(target) = target.filter(|&t| t != from) else {
            self.error(decl, Message::ImportFailed(path));
            return Ok(None);
        };
        let target_scope = self.modules[target.0 as usize];
        if !self.scopes.get(target_scope).complete {
            if !forced {
                return Err(Fact::ScopeComplete(target_scope));
            }
            // Stuck, so in a cycle of imports.
            self.error(decl, Message::ImportFailed(path));
            return Ok(None);
        }
        Ok(Some(Imported {
            edge: Some((from, target)),
            scope: target_scope,
        }))
    }

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
        let Some(path) = self.import_path(call, scope, forced)? else {
            return Ok(None);
        };
        let shown = path.clone().unwrap_or_default();
        let Some(imported) = self.imported(decl, path, scope, forced)? else {
            return Ok(Some(vec![decl]));
        };
        let Some((mut each, spread, _)) = self.destructured(decl) else {
            return Ok(Some(vec![decl]));
        };
        if spread.is_some() && imported.scope == BUILTINS {
            let message = "importing every name of core, which are in scope already";
            self.error(decl, Message::Unsupported(message.into()));
            return Ok(Some(vec![decl]));
        }
        if spread.is_some() {
            // `...` imports every other export, under its own name.
            let at = self.lib.tree.span(decl);
            let exports: Vec<NameId> = (self.scopes.exports(imported.scope, &self.lib.names))
                .map(|(n, _)| n)
                .collect();
            for name in exports.into_iter().rev() {
                if !each.iter().any(|&(n, ..)| n == name) {
                    let target = self.lib.add_name(NodeKind::LeftName, name, at, decl);
                    each.push((name, target, None));
                }
            }
        }
        self.imports.extend(imported.edge);
        let span = self.lib.tree.span(decl);
        let mut statements = Vec::with_capacity(each.len());
        for (name, target_name, ty) in each {
            let at = self.lib.tree.span(target_name);
            let value = match self.scopes.exported(imported.scope, name, &self.lib.names) {
                Some(b) => self.builtin_name(b, at, decl),
                None => {
                    let node = self.lib.add_name(NodeKind::RightName, name, at, decl);
                    let message = self.not_exported(&shown, imported.scope, name);
                    self.error(node, message);
                    node
                }
            };
            statements.push(self.make_let(target_name, ty, Some(value), 0, span, decl));
        }
        Ok(Some(statements))
    }

    /// What a module namespace's declaration, `let rx =
    /// import("std/regex")`, stands for: nothing, once it's declared,
    /// or itself as an error. `None` if `statement` isn't one, or the
    /// fact to wait for.
    pub(crate) fn namespace_import(
        &mut self,
        statement: NodeId,
        scope: ScopeId,
        forced: bool,
    ) -> Result<Option<Vec<NodeId>>, Fact> {
        let (decorators, decl) = self.decorators(statement);
        let tree = &self.lib.tree;
        if tree.kind(decl) != NodeKind::Decl {
            return Ok(None);
        }
        let Some((&name, pairs)) = tree.children(decl).split_first() else {
            return Ok(None);
        };
        if tree.kind(name) != NodeKind::LeftName {
            return Ok(None);
        }
        let Some((ty, Some(init))) = self.pairs(pairs, "init") else {
            return Ok(None);
        };
        let Some(path) = self.import_path(init, scope, forced)? else {
            return Ok(None);
        };
        // A namespace is a plain `let`: not yet exported, never `var`
        // or typed.
        let exported = decorators.iter().any(|&d| {
            let callee = self.lib.tree.children(d).get(1).copied();
            callee
                .and_then(|c| self.lib.name(c))
                .map(|n| self.lib.names.get(n))
                == Some("export")
        });
        if exported {
            let message = Message::Unsupported("exporting a module namespace".into());
            self.error(statement, message);
            return Ok(Some(vec![statement]));
        }
        if !decorators.is_empty() || ty.is_some() {
            self.error(statement, Message::MalformedSpecial("import".into()));
            return Ok(Some(vec![statement]));
        }
        let shown = path.clone().unwrap_or_default();
        let Some(imported) = self.imported(decl, path, scope, forced)? else {
            return Ok(Some(vec![decl]));
        };
        self.imports.extend(imported.edge);
        let ns = Namespace {
            path: shown,
            scope: imported.scope,
            name,
        };
        let text = self.lib.name(name).unwrap();
        self.scopes.declare_namespace(scope, text, ns);
        Ok(Some(Vec::new()))
    }

    /// `rx.member`, for a module namespace `rx`, as the export it names:
    /// `node` becomes that name, resolved, spanning `member`, or an error
    /// if there's no such export. False for any other node, which may be
    /// `.` on anything else.
    pub(crate) fn namespace_member(
        &mut self,
        node: NodeId,
        scope: ScopeId,
        forced: bool,
    ) -> Result<bool, Fact> {
        let tree = &self.lib.tree;
        if tree.kind(node) != NodeKind::Call {
            return Ok(false);
        }
        let [dot, object, key] = *tree.children(node) else {
            return Ok(false);
        };
        let is_dot = tree.kind(dot) == NodeKind::BuiltinName
            && self
                .lib
                .name(dot)
                .is_some_and(|n| self.lib.names.get(n) == ".");
        if !is_dot || tree.kind(object) != NodeKind::RightName || self.is_temporary(object) {
            return Ok(false);
        }
        let Some(&Value::Symbol(member)) = self.lib.value(key) else {
            return Ok(false);
        };
        self.resolve_name(object, scope, forced)?;
        let Some(ns) = (self.scopes.resolved(object)).and_then(|b| self.scopes.namespace(b)) else {
            return Ok(false);
        };
        let (path, target) = (ns.path.clone(), ns.scope);
        let span = self.lib.tree.span(key);
        self.lib.tree.set_span(node, span);
        match self.scopes.exported(target, member, &self.lib.names) {
            Some(export) => {
                self.rewrite(node, NodeKind::RightName, member.0, &[]);
                self.scopes.resolve(node, export);
                self.scopes.qualify(node, object);
            }
            None => {
                let message = self.not_exported(&path, target, member);
                self.error(node, message);
            }
        }
        Ok(true)
    }

    /// That the module at `path`, whose scope is `scope`, doesn't export
    /// `name`, with the export of the most similar name, if one's close.
    fn not_exported(&self, path: &str, scope: ScopeId, name: NameId) -> Message {
        let names = &self.lib.names;
        let text = names.get(name);
        let mut exports: Vec<&str> = (self.scopes.exports(scope, names))
            .map(|(n, _)| names.get(n))
            .collect();
        exports.sort_unstable();
        let similar = similar(text, &exports).map(str::to_string);
        Message::NotExported(path.to_string(), text.to_string(), similar)
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

/// The candidate nearest `name` by edit distance, if it's within a third
/// of `name`'s length (at least one edit), the first of those tied.
fn similar<'a>(name: &str, candidates: &[&'a str]) -> Option<&'a str> {
    let limit = (name.chars().count() / 3).max(1);
    let mut best: Option<(usize, &str)> = None;
    for &c in candidates {
        let d = distance(name, c);
        if d <= limit && best.is_none_or(|(b, _)| d < b) {
            best = Some((d, c));
        }
    }
    best.map(|(_, c)| c)
}

/// Levenshtein distance between two strings, by `char`.
fn distance(a: &str, b: &str) -> usize {
    let b: Vec<char> = b.chars().collect();
    let mut row: Vec<usize> = (0..=b.len()).collect();
    for (i, ca) in a.chars().enumerate() {
        let mut diagonal = row[0];
        row[0] = i + 1;
        for (j, &cb) in b.iter().enumerate() {
            let above = row[j + 1];
            row[j + 1] = (above + 1)
                .min(row[j] + 1)
                .min(diagonal + usize::from(ca != cb));
            diagonal = above;
        }
    }
    row[b.len()]
}

#[cfg(test)]
mod tests {
    use super::{distance, similar};

    #[test]
    fn similar_names() {
        assert_eq!(distance("Regx", "Regex"), 1);
        assert_eq!(distance("", "ab"), 2);
        assert_eq!(similar("Regx", &["Match", "Regex"]), Some("Regex"));
        assert_eq!(similar("regexRefs", &["Regex", "RegexNode"]), None);
    }
}
