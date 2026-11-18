//! `@staticExtension(T, "name") let f(…)` lets `T.name(…)` call `f`
//! where `f` is visible, under its own name or another, such as an
//! import's: Kotlin Temper's rule, which needs the extension imported
//! (std's `Int.jsonAdapter()` is `int32JsonAdapter()`). A call
//! `T.name(…)`, where `T` names a type, becomes a call of the visible
//! extension, unless `T` is a class with a static member of that name.
//! Instance extensions, `@extension("name")`, are recorded here,
//! declared or imported like static ones; typing makes a call `x.name()`
//! a call of one, as it needs `x`'s type (`typing::instance_extensions`).

use crate::builtins::Builtin;
use crate::core::{self, MEMBER_STATIC};
use crate::scope::{Lookup, ScopeId, SymbolId, SymbolKind};
use crate::settle::{Fact, Settle};
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind};

/// A function declared or imported as a static extension.
#[derive(Clone, Copy, Debug)]
pub(crate) struct StaticExtension {
    /// The function, or a name for it, such as an import's.
    pub symbol: SymbolId,
    /// The extended type's name, as written, and the scope it's read in.
    pub ty: NodeId,
    pub scope: ScopeId,
    pub name: NameId,
}

impl Settle {
    /// The parts of `@staticExtension(T, "name")`, if `decorator` is one.
    pub(crate) fn static_extension_of(&mut self, decorator: NodeId) -> Option<(NodeId, NameId)> {
        let tree = &self.lib.tree;
        let [callee, ty, name] = *tree.children(decorator) else {
            return None;
        };
        if tree.kind(decorator) != NodeKind::Call
            || self
                .lib
                .name(callee)
                .is_none_or(|n| self.lib.names.get(n) != "staticExtension")
        {
            return None;
        }
        let Some(temper_syntax::ast::Value::String(name)) = self.lib.value(name) else {
            return None;
        };
        // Interned, as a REPL's later input may be the first to use it.
        let name = name.clone();
        Some((ty, self.lib.names.intern(&name)))
    }

    /// The name in `@extension("name")`, if `decorator` is one.
    pub(crate) fn extension_of(&mut self, decorator: NodeId) -> Option<NameId> {
        let tree = &self.lib.tree;
        let [callee, name] = *tree.children(decorator) else {
            return None;
        };
        if tree.kind(decorator) != NodeKind::Call
            || self
                .lib
                .name(callee)
                .is_none_or(|n| self.lib.names.get(n) != "extension")
        {
            return None;
        }
        let Some(temper_syntax::ast::Value::String(name)) = self.lib.value(name) else {
            return None;
        };
        // Interned, as a REPL's later input may be the first to use it.
        let name = name.clone();
        Some(self.lib.names.intern(&name))
    }

    /// Records a declared symbol that's an extension, static or not:
    /// marked as one, or a `let` of another one's name, as an import
    /// makes.
    pub(crate) fn note_extension(&mut self, let_node: NodeId, symbol: SymbolId, scope: ScopeId) {
        if let Some(name) = self.extension_marks.remove(&let_node) {
            self.extensions.push((symbol, name));
        }
        if let Some((ty, name)) = self.static_extension_marks.remove(&let_node) {
            self.static_extensions.push(StaticExtension {
                symbol,
                ty,
                scope,
                name,
            });
            return;
        }
        let none = self.static_extensions.is_empty() && self.extensions.is_empty();
        if none || self.lib.tree.kind(let_node) != NodeKind::Let {
            return;
        }
        let init = core::let_parts(&self.lib.tree, let_node).2;
        let Some(of) = init
            .filter(|&i| self.lib.tree.kind(i) == NodeKind::RightName)
            .and_then(|i| self.scopes.resolved(i))
        else {
            return;
        };
        if let Some(&e) = self.static_extensions.iter().find(|e| e.symbol == of) {
            self.static_extensions.push(StaticExtension { symbol, ..e });
        }
        if let Some(&(_, name)) = self.extensions.iter().find(|e| e.0 == of) {
            self.extensions.push((symbol, name));
        }
    }

    /// Whether an instance extension named `name` is visible at `node`,
    /// in `scope`, under the name it has: declared there or outside, or
    /// imported.
    pub(crate) fn extension_visible(&self, scope: ScopeId, name: NameId, node: NodeId) -> bool {
        let pos = self.lib.tree.span(node).start;
        self.extensions.iter().any(|&(symbol, n)| {
            n == name
                && match self
                    .scopes
                    .lookup(scope, self.scopes.symbol(symbol).name, false, pos)
                {
                    Lookup::Found(b) => b == symbol,
                    // Can't tell, after settling stopped early.
                    Lookup::Wait(_) => true,
                    Lookup::Missing => false,
                }
        })
    }

    /// For a call `T.name(…)` of a visible static extension of type `T`,
    /// makes the callee that extension's name, returning true.
    pub(crate) fn static_extension_call(
        &mut self,
        call: NodeId,
        scope: ScopeId,
        forced: bool,
    ) -> Result<bool, Fact> {
        let tree = &self.lib.tree;
        let Some(&callee) = tree
            .children(call)
            .first()
            .filter(|&&c| tree.kind(c) == NodeKind::Call)
        else {
            return Ok(false);
        };
        let [dot, object, key] = *tree.children(callee) else {
            return Ok(false);
        };
        let is_dot = tree.kind(dot) == NodeKind::BuiltinName
            && self
                .lib
                .name(dot)
                .is_some_and(|n| self.lib.names.get(n) == ".");
        let Some(name) = self
            .lib
            .symbol(key)
            .filter(|_| is_dot && tree.kind(object) == NodeKind::RightName)
        else {
            return Ok(false);
        };
        let candidates: Vec<StaticExtension> = self
            .static_extensions
            .iter()
            .filter(|e| e.name == name)
            .copied()
            .collect();
        if candidates.is_empty() {
            return Ok(false);
        }
        self.resolve_name(object, scope, forced)?;
        let Some(ty) = self
            .scopes
            .resolved(object)
            .and_then(|b| self.type_symbol(b))
        else {
            return Ok(false);
        };
        if self.has_static_member(ty, name) {
            return Ok(false);
        }
        for e in candidates {
            // The extension must be visible here under the name it has.
            let symbol = *self.scopes.symbol(e.symbol);
            match self.lookup(scope, symbol.name, callee, false, forced) {
                Lookup::Wait(s) => return Err(Fact::ScopeComplete(s)),
                Lookup::Found(b) if b == e.symbol => {}
                _ => continue,
            }
            let ty_name = self
                .lib
                .name(e.ty)
                .filter(|_| self.lib.tree.kind(e.ty) == NodeKind::RightName);
            let extended = match ty_name.map(|n| self.lookup(e.scope, n, e.ty, false, forced)) {
                Some(Lookup::Wait(s)) => return Err(Fact::ScopeComplete(s)),
                Some(Lookup::Found(b)) => self.type_symbol(b),
                _ => None,
            };
            if extended == Some(ty) {
                self.rewrite(callee, NodeKind::RightName, symbol.name.0, &[]);
                self.scopes.resolve(callee, e.symbol);
                return Ok(true);
            }
        }
        Ok(false)
    }

    /// The type a symbol names, a builtin type or a class or interface,
    /// through `let` aliases such as imports make.
    fn type_symbol(&self, symbol: SymbolId) -> Option<SymbolId> {
        let tree = &self.lib.tree;
        let mut symbol = symbol;
        for _ in 0..8 {
            if let SymbolKind::Builtin(Builtin::Type(_)) = self.scopes.symbol(symbol).kind {
                return Some(symbol);
            }
            let decl = self.scopes.symbol(symbol).decl?;
            match tree.kind(decl) {
                NodeKind::Class => return Some(symbol),
                NodeKind::Let if tree.data(decl) & core::ROLE_MASK == 0 => {
                    let init = core::let_parts(tree, decl).2?;
                    symbol = self
                        .scopes
                        .resolved(init)
                        .filter(|_| tree.kind(init) == NodeKind::RightName)?;
                }
                _ => return None,
            }
        }
        None
    }

    fn has_static_member(&self, ty: SymbolId, name: NameId) -> bool {
        let tree = &self.lib.tree;
        let Some(class) = self
            .scopes
            .symbol(ty)
            .decl
            .filter(|&d| tree.kind(d) == NodeKind::Class)
        else {
            return false;
        };
        core::class_parts(tree, class).2.iter().any(|&m| {
            tree.data(m) & MEMBER_STATIC != 0
                && self.lib.name(core::let_parts(tree, m).0) == Some(name)
        })
    }
}
