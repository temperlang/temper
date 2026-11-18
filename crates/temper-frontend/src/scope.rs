//! Scopes and symbols, and looking names up in them.
//!
//! Names are kept simple (notes/history/milestone-3.md, "Names"): a name's text is
//! an interned `NameId`; resolution is a side table from each use node to a
//! [`SymbolId`]; and symbols are one flat table of plain structs. Macros
//! that mean a particular builtin insert a name already resolved to its
//! symbol, so user declarations can't capture it. A temporary name from the
//! syntax step (never interned) gets a synthetic symbol when it's declared.
//!
//! A scope is complete once every declaration in it is known: for a
//! block, once each of its statements has had its macro expanded, as far as
//! needed to see whether it declares something. A lookup that reaches an
//! incomplete scope without finding its name waits for that scope to
//! complete, so a name never resolves outward past a declaration that's
//! still to come. The exception is a statement's own macro name, looked up
//! while its block is still being read: it sees the declarations before it
//! (see [`Lookup`]).

use crate::builtins::{Builtin, Constant, Function};
use std::collections::HashMap;
use temper_syntax::names::{NameId, Names};
use temper_syntax::tree::NodeId;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct ScopeId(pub u32);

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct SymbolId(pub u32);

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ScopeKind {
    Builtins,
    Module,
    /// A function's parameters.
    Fn,
    Block,
    /// A loop, for `break` and `continue` without a label.
    Loop,
    /// A labeled block, for `break` and `continue` with its label.
    Label(NameId),
    /// A `for … of` loop's body function's parameter; jumps reach through
    /// it (see [`crate::core::FN_LOOP_BODY`]).
    LoopBody,
    /// A class's or interface's members, after a synthetic `this`.
    Class,
    /// A generic class's or function's type parameters, around its own
    /// scope, so that they cover supertypes and parameters' types too.
    TypeParams,
}

#[derive(Clone, Debug)]
pub struct Scope {
    pub parent: Option<ScopeId>,
    pub kind: ScopeKind,
    /// The node that made it: a block, function, loop or class.
    pub node: NodeId,
    /// Its symbols in order of declaration.
    pub names: Vec<(NameId, SymbolId)>,
    pub complete: bool,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum SymbolKind {
    Builtin(Builtin),
    TopLevel,
    Parameter,
    Local,
    /// A class's member: a field, method, getter, setter or constructor.
    /// A getter and setter share a name, so lookups find the last one.
    Member,
    /// Made by the compiler, like a call join's delegate parameter.
    Synthetic,
    /// A type parameter. The interpreter ignores types, so no frame holds
    /// it.
    TypeParam,
}

/// A declared name.
#[derive(Clone, Copy, Debug)]
pub struct Symbol {
    pub name: NameId,
    pub kind: SymbolKind,
    /// The scope it's declared in.
    pub owner: ScopeId,
    /// Its index among its scope's symbols, its place in a run-time frame.
    pub slot: u32,
    /// The declaration, a `Let`; for builtins, none.
    pub decl: Option<NodeId>,
    /// Declared `var`, so it may be assigned.
    pub mutable: bool,
    pub exported: bool,
    /// Where it's visible from: a block's locals other than functions and
    /// classes, only after their declaration; everything else, everywhere
    /// in its scope (0).
    pub visible_from: u32,
    /// A class header's `@noProperty` input, which its class's
    /// initializers read by name, ahead of a member of the same name, and
    /// its methods don't see ([`Scopes::lookup`]).
    pub input: bool,
}

/// The result of a lookup.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Lookup {
    Found(SymbolId),
    /// Not found so far; this scope is incomplete.
    Wait(ScopeId),
    Missing,
}

#[derive(Debug, Default)]
pub struct Scopes {
    pub scopes: Vec<Scope>,
    pub symbols: Vec<Symbol>,
    /// The symbol each name node resolved to.
    resolved: HashMap<NodeId, SymbolId>,
    /// The scope each block, function and loop made.
    node_scopes: HashMap<NodeId, ScopeId>,
    builtins: HashMap<NameId, SymbolId>,
    hidden: HashMap<Builtin, SymbolId>,
}

pub const BUILTINS: ScopeId = ScopeId(0);

impl Scopes {
    /// Scopes with just the builtins, as named in `names`, and
    /// `testServerPort` if given.
    pub fn new(names: &mut Names, test_server_port: Option<i32>) -> Scopes {
        let mut scopes = Scopes::default();
        scopes.scopes.push(Scope {
            parent: None,
            kind: ScopeKind::Builtins,
            node: NodeId(u32::MAX),
            names: Vec::new(),
            complete: true,
        });
        let port = test_server_port.map(|p| Builtin::Value(Constant::TestServerPort(p)));
        let all = crate::builtins::BUILTINS.iter().map(|&(n, b)| (n, b));
        let all = all.chain(port.map(|b| (b.name(), b)));
        let types = crate::builtins::TYPES
            .iter()
            .enumerate()
            .map(|(i, &n)| (n, Builtin::Type(i as u8)));
        for (text, builtin) in all.chain(types) {
            let name = names.intern(text);
            let b = scopes.declare(BUILTINS, name, SymbolKind::Builtin(builtin), None, false);
            scopes.builtins.insert(name, b);
            scopes.hidden.insert(builtin, b);
        }
        // Builtins that macros refer to but source can't name.
        for function in [
            Function::Cat,
            Function::FnType,
            Function::Angle,
            Function::List,
        ] {
            let builtin = Builtin::Function(function);
            let name = names.intern(builtin.name());
            let id = SymbolId(scopes.symbols.len() as u32);
            let kind = SymbolKind::Builtin(builtin);
            let (owner, slot, decl, mutable, exported) = (BUILTINS, u32::MAX, None, false, false);
            scopes.symbols.push(Symbol {
                name,
                kind,
                owner,
                slot,
                decl,
                mutable,
                exported,
                visible_from: 0,
                input: false,
            });
            scopes.hidden.insert(builtin, id);
        }
        scopes
    }

    /// The symbol for a builtin that macros refer to by itself, like `cat`
    /// or `==`, whatever names are in scope.
    pub fn hidden(&self, builtin: Builtin) -> SymbolId {
        self.hidden[&builtin]
    }

    pub fn add(
        &mut self,
        parent: ScopeId,
        kind: ScopeKind,
        node: NodeId,
        complete: bool,
    ) -> ScopeId {
        let id = ScopeId(self.scopes.len() as u32);
        self.scopes.push(Scope {
            parent: Some(parent),
            kind,
            node,
            names: Vec::new(),
            complete,
        });
        if !matches!(kind, ScopeKind::Label(_) | ScopeKind::TypeParams) {
            self.node_scopes.insert(node, id);
        }
        id
    }

    pub fn get(&self, id: ScopeId) -> &Scope {
        &self.scopes[id.0 as usize]
    }

    pub fn complete(&mut self, id: ScopeId) {
        self.scopes[id.0 as usize].complete = true;
    }

    /// The scope a block, function or loop made, if it was visited.
    pub fn of_node(&self, node: NodeId) -> Option<ScopeId> {
        self.node_scopes.get(&node).copied()
    }

    pub fn declare(
        &mut self,
        scope: ScopeId,
        name: NameId,
        kind: SymbolKind,
        decl: Option<NodeId>,
        mutable: bool,
    ) -> SymbolId {
        let id = SymbolId(self.symbols.len() as u32);
        let s = &mut self.scopes[scope.0 as usize];
        let slot = s.names.len() as u32;
        s.names.push((name, id));
        let exported = false;
        self.symbols.push(Symbol {
            name,
            kind,
            owner: scope,
            slot,
            decl,
            mutable,
            exported,
            visible_from: 0,
            input: false,
        });
        id
    }

    /// A type parameter that no scope names, as `O` in core's `Listed.map<O>`,
    /// which typing reads from core's signatures, not from a declaration.
    pub fn unnamed_type_param(&mut self, name: NameId) -> SymbolId {
        let id = SymbolId(self.symbols.len() as u32);
        self.symbols.push(Symbol {
            name,
            kind: SymbolKind::TypeParam,
            owner: BUILTINS,
            slot: u32::MAX,
            decl: None,
            mutable: false,
            exported: false,
            visible_from: 0,
            input: false,
        });
        id
    }

    /// Marks a member as a `@noProperty` input ([`Symbol::input`]).
    pub fn mark_input(&mut self, id: SymbolId) {
        self.symbols[id.0 as usize].input = true;
    }

    pub fn set_visible_from(&mut self, id: SymbolId, at: u32) {
        self.symbols[id.0 as usize].visible_from = at;
    }

    /// Gives a symbol another name, as a pass that changes what a
    /// temporary holds does.
    pub fn rename(&mut self, id: SymbolId, name: NameId) {
        let symbol = &mut self.symbols[id.0 as usize];
        symbol.name = name;
        let owner = symbol.owner;
        for entry in &mut self.scopes[owner.0 as usize].names {
            if entry.1 == id {
                entry.0 = name;
            }
        }
    }

    pub fn export(&mut self, id: SymbolId) {
        self.symbols[id.0 as usize].exported = true;
    }

    pub fn symbol(&self, id: SymbolId) -> &Symbol {
        &self.symbols[id.0 as usize]
    }

    /// Whether `from`, inside `class`'s scope, is in one of its methods,
    /// getters, setters, or constructor: under a function's scope right
    /// in the class's.
    fn in_method(&self, from: ScopeId, class: ScopeId) -> bool {
        let mut below = None;
        let mut at = from;
        while at != class {
            below = Some(at);
            match self.get(at).parent {
                Some(parent) => at = parent,
                None => return false,
            }
        }
        below.is_some_and(|b| self.get(b).kind == ScopeKind::Fn)
    }

    /// Looks a name used at source position `pos` up from a scope outward.
    /// With `so_far`, the starting scope counts as complete, for a
    /// statement's macro name.
    pub fn lookup(&self, from: ScopeId, name: NameId, so_far: bool, pos: u32) -> Lookup {
        let mut at = Some(from);
        while let Some(id) = at {
            let scope = self.get(id);
            // Later declarations shadow earlier ones in the same scope.
            let visible =
                |&&(n, b): &&(NameId, SymbolId)| n == name && self.symbol(b).visible_from <= pos;
            let mut named = scope.names.iter().rev().filter(visible).map(|&(_, b)| b);
            let mut found = named.next();
            // A `@noProperty` input is what initializers read, ahead of a
            // getter of the same name, and methods don't see it.
            let input = |b: SymbolId| self.symbol(b).input;
            if scope.kind == ScopeKind::Class && found.is_some_and(|b| !input(b)) {
                let shadowed = named.find(|&b| input(b));
                if shadowed.is_some() && !self.in_method(from, id) {
                    found = shadowed;
                }
            } else if scope.kind == ScopeKind::Class && found.is_some() && self.in_method(from, id)
            {
                found = named.find(|&b| !input(b));
            }
            if let Some(b) = found {
                return Lookup::Found(b);
            }
            if !(scope.complete || so_far && id == from) {
                return Lookup::Wait(id);
            }
            at = scope.parent;
        }
        Lookup::Missing
    }

    /// The builtin with a name, as for builtin names like `.`.
    pub fn builtin(&self, name: NameId) -> Option<SymbolId> {
        self.builtins.get(&name).copied()
    }

    pub fn resolve(&mut self, node: NodeId, symbol: SymbolId) {
        self.resolved.insert(node, symbol);
    }

    /// The symbol a name node resolved to.
    pub fn resolved(&self, node: NodeId) -> Option<SymbolId> {
        self.resolved.get(&node).copied()
    }

    /// The builtin a name node resolved to, if it did.
    pub fn resolved_builtin(&self, node: NodeId) -> Option<Builtin> {
        match self.symbol(self.resolved(node)?).kind {
            SymbolKind::Builtin(b) => Some(b),
            _ => None,
        }
    }

    /// The enclosing scopes from `from` outward, up to its function or class.
    pub fn within_function(&self, from: ScopeId) -> impl Iterator<Item = &Scope> {
        let mut at = Some(from);
        let mut stop = false;
        std::iter::from_fn(move || {
            if stop {
                return None;
            }
            let scope = self.get(at?);
            at = scope.parent;
            stop = matches!(
                scope.kind,
                ScopeKind::Fn | ScopeKind::Class | ScopeKind::Module
            );
            Some(scope)
        })
    }
}
