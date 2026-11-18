//! Editor queries over one settled library: diagnostics, hover, go to
//! definition, and completion, for the playground now and a language
//! server and `watch` later (docs/internals/08-playground.md).
//!
//! An [`Analysis`] owns one [`Settled`] library and answers from what the
//! settle loop already worked out: each name's symbol ([`Scopes`]), each
//! declaration's and expression's type ([`temper_frontend::typing::Typing`]), and
//! each node's span. Nothing is settled again for a query.
//!
//! Positions here are byte offsets in one file. Editors count UTF-16 code
//! units (CodeMirror and LSP alike), so callers convert once, at their
//! boundary, with [`Position`] and [`byte_offset`].
//!
//! This crate depends on `temper-frontend` only, not tmpl or backends.
//! Backend-aware queries (per-declaration output previews, backend
//! diagnostics, and naming hints) may come later, lazily computed, behind
//! an optional cargo feature.

mod builtins;
mod members;
mod pending;
mod references;
mod signatures;
mod symbols;

pub use builtins::CoreLocation;
pub use references::{Edit, KEYWORDS};
pub use signatures::SignatureHelp;
pub use symbols::DocSymbol;

use members::Subst;
use std::cell::OnceCell;
use std::collections::HashMap;
use temper_frontend::builtins::Builtin;
use temper_frontend::core::{
    self, CLASS_INTERFACE, LET_VAR, ROLE_CONSTRUCTOR, ROLE_GETTER, ROLE_MASK, ROLE_METHOD,
    ROLE_PARAM, ROLE_SETTER, ROLE_TYPE_PARAM,
};
use temper_frontend::library::{FileId, Level};
use temper_frontend::scope::{BUILTINS, ScopeId, Scopes, SymbolId, SymbolKind};
use temper_frontend::settle::Settled;
use temper_frontend::types::{self, TypeData, TypeId, show_result};
use temper_frontend::typing::Resolved;
use temper_syntax::adapt::CommentKind;
use temper_syntax::ast::Value;
use temper_syntax::tree::{NodeId, NodeKind, Span};

/// What a declaration is, as hover shows it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum DeclKind {
    Let,
    Var,
    Function,
    Parameter,
    TypeParameter,
    Class,
    Interface,
    Field,
    Method,
    Getter,
    Setter,
    Constructor,
    /// One of Temper's builtins, as `console` or `Int`.
    Builtin,
}

impl DeclKind {
    /// As people say it: `let`, `type parameter`, `method`.
    pub fn label(self) -> &'static str {
        match self {
            DeclKind::Let => "let",
            DeclKind::Var => "var",
            DeclKind::Function => "function",
            DeclKind::Parameter => "parameter",
            DeclKind::TypeParameter => "type parameter",
            DeclKind::Class => "class",
            DeclKind::Interface => "interface",
            DeclKind::Field => "field",
            DeclKind::Method => "method",
            DeclKind::Getter => "getter",
            DeclKind::Setter => "setter",
            DeclKind::Constructor => "constructor",
            DeclKind::Builtin => "builtin",
        }
    }
}

/// A problem, at a span in a file.
#[derive(Clone, Debug, PartialEq)]
pub struct Problem {
    pub level: Level,
    pub message: String,
    pub file: FileId,
    /// Byte offsets in its file.
    pub span: Span,
}

/// What hovering over a position shows.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Hover {
    /// What it's about, in the file's bytes: a name, or the smallest
    /// typed expression around the position.
    pub span: Span,
    /// The name, if it's a name.
    pub name: Option<String>,
    /// What its declaration is, if it's a name.
    pub kind: Option<DeclKind>,
    /// Its type as messages write it, `Int32` or `fn (String): Boolean`;
    /// for a function, its signature with parameter names, as
    /// `greet(name: String): String`.
    pub ty: Option<String>,
    /// The declaration's doc comment, `/** … */`, without its markers.
    pub doc: Option<String>,
}

impl Hover {
    /// Its first line, as an editor's hover shows it: `function
    /// greet(name: String): String`, or `let total: Int32`.
    pub fn headline(&self) -> String {
        let ty = self.ty.as_deref().unwrap_or("?");
        match (&self.name, self.kind) {
            // A signature already starts with its name, as `twice(n: Int32)`,
            // and a type is its name, as `List<T>`; `Int32` isn't `Int`'s.
            (Some(name), Some(kind))
                if (ty.strip_prefix(name.as_str()))
                    .is_some_and(|rest| rest.is_empty() || rest.starts_with(['(', '<'])) =>
            {
                format!("{} {ty}", kind.label())
            }
            (Some(name), Some(kind)) => format!("{} {name}: {ty}", kind.label()),
            _ => ty.to_string(),
        }
    }
}

/// What hover shows as a type parameter's or builtin type's type: the
/// type its name names, as in `name: String`, `T`, or `Map<K, V>`.
fn type_name_shown(s: &temper_frontend::scope::Symbol, name: &str) -> Option<String> {
    match (s.kind, s.decl) {
        (SymbolKind::Builtin(Builtin::Type(_)), None) => Some(builtins::builtin_type_shown(name)),
        (SymbolKind::TypeParam, _) => Some(name.to_string()),
        _ => None,
    }
}

/// A place in a file.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Location {
    pub file: FileId,
    /// Byte offsets in its file.
    pub span: Span,
}

/// A name that may go at a position.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Completion {
    pub name: String,
    pub kind: DeclKind,
    /// Its type, if known.
    pub detail: Option<String>,
}

/// Queries over one settled library, which it owns or borrows.
pub struct Analysis<'a> {
    pub settled: Held<'a>,
    /// Each node's parent, under the files' roots, found when first
    /// needed.
    parents: OnceCell<Vec<Option<NodeId>>>,
    /// Each declaration's symbol, by its `Let` or `Class`, found when
    /// first needed.
    decl_symbols: OnceCell<HashMap<NodeId, SymbolId>>,
}

/// A settled library, owned, as the language server and the playground
/// keep one, or borrowed, as a REPL session lends its own
/// ([`Analysis::of`]).
pub enum Held<'a> {
    Owned(Box<Settled>),
    Borrowed(&'a Settled),
}

impl std::ops::Deref for Held<'_> {
    type Target = Settled;

    fn deref(&self) -> &Settled {
        match self {
            Held::Owned(settled) => settled,
            Held::Borrowed(settled) => settled,
        }
    }
}

impl Analysis<'static> {
    pub fn new(settled: Settled) -> Analysis<'static> {
        Analysis::held(Held::Owned(Box::new(settled)))
    }
}

impl<'a> Analysis<'a> {
    /// Queries over a library borrowed for a while, as a REPL session's.
    pub fn of(settled: &'a Settled) -> Analysis<'a> {
        Analysis::held(Held::Borrowed(settled))
    }

    fn held(settled: Held<'a>) -> Analysis<'a> {
        Analysis {
            settled,
            parents: OnceCell::new(),
            decl_symbols: OnceCell::new(),
        }
    }
}

impl Analysis<'_> {
    fn parents(&self) -> &[Option<NodeId>] {
        self.parents.get_or_init(|| {
            let tree = &self.settled.library.tree;
            let mut parents = vec![None; tree.len()];
            let mut stack: Vec<NodeId> = (self.settled.library.files.iter())
                .map(|f| f.root)
                .collect();
            let mut seen = vec![false; tree.len()];
            while let Some(node) = stack.pop() {
                if std::mem::replace(&mut seen[node.0 as usize], true) {
                    continue;
                }
                for &child in tree.children(node) {
                    if parents[child.0 as usize].is_none() {
                        parents[child.0 as usize] = Some(node);
                    }
                    stack.push(child);
                }
            }
            parents
        })
    }

    fn decl_symbols(&self) -> &HashMap<NodeId, SymbolId> {
        self.decl_symbols.get_or_init(|| {
            (self.settled.scopes.symbols.iter().enumerate())
                .filter_map(|(i, s)| Some((s.decl?, SymbolId(i as u32))))
                .collect()
        })
    }

    /// Every diagnostic, in the order found, with its span in its file.
    pub fn diagnostics(&self) -> Vec<Problem> {
        let library = &self.settled.library;
        (library.diagnostics.iter())
            .map(|d| {
                // "No member" and "not publicly accessible" span
                // `obj.name`, as Kotlin's do; editors underline just the
                // name.
                let span = match d.message {
                    temper_frontend::library::Message::NoMember(..)
                    | temper_frontend::library::Message::NotPubliclyAccessible(..) => {
                        self.member_name_span(d.span)
                    }
                    _ => d.span,
                };
                let (file, start) = library.locate(span.start);
                Problem {
                    level: d.level,
                    message: d.message.text(),
                    file,
                    span: Span {
                        start,
                        end: start + span.end.saturating_sub(span.start),
                    },
                }
            })
            .collect()
    }

    /// The member's name in a `.` call with a library-wide span, or the
    /// span itself if it isn't one.
    fn member_name_span(&self, span: Span) -> Span {
        let tree = &self.settled.library.tree;
        let library = &self.settled.library;
        let (file, _) = library.locate(span.start);
        let Some(root) = library.files.get(file.0 as usize).map(|f| f.root) else {
            return span;
        };
        let mut stack = vec![root];
        while let Some(node) = stack.pop() {
            let s = tree.span(node);
            if s.start > span.start || s.end < span.end {
                continue;
            }
            let children = tree.children(node);
            if s == span && tree.kind(node) == NodeKind::Call && children.len() == 3 {
                let dot = library
                    .name(children[0])
                    .is_some_and(|n| library.names.get(n) == ".");
                if dot && tree.kind(children[2]) == NodeKind::Value {
                    return tree.span(children[2]);
                }
            }
            stack.extend(children);
        }
        span
    }

    /// What's at byte `offset` of `file`: the name there, with its
    /// declaration's kind, type, and doc comment, or else the type of the
    /// smallest expression around it.
    pub fn hover(&self, file: FileId, offset: u32) -> Option<Hover> {
        let (node, target) = self.target(file, offset)?;
        let span = match target {
            Some(_) => self.name_span(node),
            None => self.local(node),
        };
        let Some(target) = target else {
            let t = self.settled.typing.expr(node)?;
            return Some(Hover {
                span,
                name: None,
                kind: None,
                ty: Some(self.show(t)),
                doc: None,
            });
        };
        let (name, kind, ty, decl) = match target {
            Target::Symbol(symbol) => {
                let s = self.settled.scopes.symbol(symbol);
                let name = self.settled.library.names.get(s.name).to_string();
                // An imported name is what it imports.
                let decl = s.decl.map(|d| self.imported(d));
                let kind = match decl == s.decl {
                    true => self.kind(symbol),
                    false => self.decl_kind(decl.unwrap_or(node), None),
                };
                let ty = type_name_shown(s, &name).or_else(|| match decl {
                    Some(decl) => self.decl_type(decl, &name, node),
                    None => self.settled.typing.expr(node).map(|t| self.show(t)),
                });
                (name, kind, ty, decl)
            }
            Target::Member { call, key } => {
                let name = self.settled.library.names.get(key).to_string();
                let receiver = self.receiver_of(call);
                match self.member(call) {
                    Some(Resolved::Decl(decl)) => {
                        let decl = *decl;
                        let kind = self.decl_kind(decl, None);
                        let subst = match receiver.as_ref().and_then(|r| self.find_member(r, &name))
                        {
                            Some((m, subst)) if m == decl => subst,
                            _ => Subst::new(),
                        };
                        (
                            name.clone(),
                            kind,
                            self.decl_type_in(decl, &name, call, &subst),
                            Some(decl),
                        )
                    }
                    _ => match receiver
                        .as_ref()
                        .and_then(|r| self.builtin_member(r, &name))
                    {
                        // A builtin type's member, as core declares it.
                        Some(m) => {
                            let kind = match m.is_method {
                                true => DeclKind::Method,
                                false => DeclKind::Field,
                            };
                            (name, kind, Some(m.detail), None)
                        }
                        None => {
                            let ty = self.settled.typing.expr(call).map(|t| self.show(t));
                            (name, DeclKind::Builtin, ty, None)
                        }
                    },
                }
            }
        };
        let doc = match decl {
            Some(d) => self.doc(d),
            // A builtin's, from core: an override's, or else what it
            // overrides'.
            None => (self.core_target(target))
                .and_then(|(ats, _)| ats.into_iter().find_map(builtins::core_doc)),
        };
        Some(Hover {
            span,
            name: Some(name),
            kind: Some(kind),
            ty,
            doc,
        })
    }

    /// Where the name at byte `offset` of `file` is declared: its
    /// declaration's name, in whichever file that is. None for builtins,
    /// which [`Analysis::core_definition`] finds in core.
    pub fn definition(&self, file: FileId, offset: u32) -> Option<Location> {
        let decl = match self.target(file, offset)?.1? {
            Target::Symbol(symbol) => self.imported(self.settled.scopes.symbol(symbol).decl?),
            Target::Member { call, .. } => match self.member(call)? {
                Resolved::Decl(decl) => *decl,
                _ => return None,
            },
        };
        let tree = &self.settled.library.tree;
        let name = match tree.kind(decl) {
            NodeKind::Let | NodeKind::Class => tree.children(decl)[0],
            _ => decl,
        };
        let (file, _) = self.settled.library.locate(tree.span(name).start);
        Some(Location {
            file,
            span: self.name_span(name),
        })
    }

    /// What an imported name's declaration, as `let { Regex } =
    /// import("std/regex")`, imports: the exporting module's declaration,
    /// through imports of imports; any other declaration itself.
    pub(crate) fn imported(&self, decl: NodeId) -> NodeId {
        let tree = &self.settled.library.tree;
        let mut decl = decl;
        for _ in 0..8 {
            if tree.kind(decl) != NodeKind::Let {
                break;
            }
            let (name, _, init) = core::let_parts(tree, decl);
            // Destructuring gives each name a `let` of the module's name,
            // at the same place.
            let Some(init) = init.filter(|&i| {
                tree.kind(i) == NodeKind::RightName && tree.span(i) == tree.span(name)
            }) else {
                break;
            };
            let next = (self.settled.scopes.resolved(init))
                .and_then(|s| self.settled.scopes.symbol(s).decl);
            match next {
                Some(next) if next != decl => decl = next,
                _ => break,
            }
        }
        decl
    }

    /// A name's span in its file: just the name, where a declaration's
    /// name node also spans its type, as `items: ListBuilder<T>`.
    pub(crate) fn name_span(&self, node: NodeId) -> Span {
        let span = self.local(node);
        let library = &self.settled.library;
        let (file, _) = library.locate(library.tree.span(node).start);
        let text = (library.name(node).map(|n| library.names.get(n))).or_else(|| {
            match library.value(node) {
                Some(Value::Symbol(n)) => Some(library.names.get(*n)),
                _ => None,
            }
        });
        let source = self.source(file).unwrap_or("");
        match (text, source.get(span.start as usize..span.end as usize)) {
            (Some(name), Some(at)) if !name.is_empty() && at.starts_with(name) && at != name => {
                Span {
                    start: span.start,
                    end: span.start + name.len() as u32,
                }
            }
            _ => span,
        }
    }

    /// The names in scope at byte `offset` of `file`, innermost first,
    /// each once, with builtins last. After `.`, the members of what's
    /// before it, if its type is a class's.
    pub fn completions(&self, file: FileId, offset: u32) -> Vec<Completion> {
        let pos = self.global(file, offset);
        if let Some(keys) = self.literal_completions(file, offset) {
            return keys;
        }
        if let Some(members) = self.member_completions(file, offset) {
            return members;
        }
        let mut scope = self.scope_at(file, pos);
        let mut out = Vec::new();
        let mut seen = std::collections::HashSet::new();
        let scopes = &self.settled.scopes;
        while let Some(id) = scope {
            let s = scopes.get(id);
            for &(name, symbol) in s.names.iter().rev() {
                let sym = scopes.symbol(symbol);
                let text = self.settled.library.names.get(name);
                let visible = sym.visible_from <= pos || id == BUILTINS;
                if !visible
                    || self.settled.library.names.is_temporary(name)
                    || text.is_empty()
                    || !text.starts_with(|c: char| c.is_alphabetic() || c == '_')
                    || !seen.insert(name)
                {
                    continue;
                }
                let kind = self.kind(symbol);
                let detail = sym.decl.and_then(|d| self.decl_type(d, text, d));
                out.push(Completion {
                    name: text.to_string(),
                    kind,
                    detail,
                });
            }
            scope = s.parent;
        }
        out
    }

    /// Members after `x.` (or `x.prefix`) at `offset`: of the type of
    /// whatever expression `x` is, or a type's statics after its name;
    /// empty if that's unknown. `None` if there's no `.` before.
    fn member_completions(&self, file: FileId, offset: u32) -> Option<Vec<Completion>> {
        let source = self.source(file)?;
        let before = source.get(..offset as usize)?;
        let before = before.trim_end_matches(|c: char| c.is_alphanumeric() || c == '_');
        let before = before.trim_end().strip_suffix('.')?;
        let object_end = before.trim_end().len() as u32;
        if let Some(node) = self.receiver_node(file, object_end) {
            if let Some(members) = self.members_after(node) {
                return Some(members);
            }
        }
        // Code too broken to type: a local's declared type.
        let object = before
            .rsplit(|c: char| !(c.is_alphanumeric() || c == '_'))
            .next()
            .filter(|o| !o.is_empty())?;
        let Some(symbol) = self.lookup(file, object_end, object) else {
            return Some(Vec::new());
        };
        if object == "console" && self.settled.scopes.symbol(symbol).decl.is_none() {
            return Some(self.members_of(&members::CONSOLE));
        }
        let t = (self.settled.scopes.symbol(symbol).decl)
            .and_then(|decl| self.settled.typing.decls.get(&decl).copied());
        Some(match t.and_then(|t| self.receiver(t)) {
            Some(r) => self.members_of(&r),
            None => Vec::new(),
        })
    }

    /// The symbol a name means at byte `offset` of `file`, if it's in
    /// scope there.
    pub(crate) fn lookup(&self, file: FileId, offset: u32, name: &str) -> Option<SymbolId> {
        let pos = self.global(file, offset);
        let name = self.settled.library.names.find(name)?;
        let scopes = &self.settled.scopes;
        let mut at = self.scope_at(file, pos);
        loop {
            let s = scopes.get(at?);
            if let Some(&(_, symbol)) = s.names.iter().rev().find(|(n, _)| *n == name) {
                return Some(symbol);
            }
            at = s.parent;
        }
    }

    /// A file's source text.
    pub(crate) fn source(&self, file: FileId) -> Option<&str> {
        Some(
            self.settled
                .library
                .files
                .get(file.0 as usize)?
                .parsed
                .source(),
        )
    }

    /// Whether a file is one of std's.
    pub fn is_std(&self, file: FileId) -> bool {
        (self.settled.library.files.get(file.0 as usize)).is_some_and(|f| {
            f.path
                .starts_with(&format!("{}/", temper_frontend::stdlib::DIR))
        })
    }

    /// What a `.` call's object has members of.
    fn receiver_of(&self, call: NodeId) -> Option<members::Receiver> {
        let object = *self.settled.library.tree.children(call).get(1)?;
        self.receiver(self.settled.typing.expr(object)?)
    }

    /// The innermost scope around a library-wide position in `file`.
    fn scope_at(&self, file: FileId, pos: u32) -> Option<ScopeId> {
        let tree = &self.settled.library.tree;
        let root = self.settled.library.files.get(file.0 as usize)?.root;
        // The smallest node with a scope whose span holds the position.
        let mut best: Option<(u32, ScopeId)> = None;
        let mut stack = vec![root];
        while let Some(node) = stack.pop() {
            let span = tree.span(node);
            let holds = span.start <= pos && pos <= span.end;
            if let Some(scope) = self.settled.scopes.of_node(node) {
                let width = span.end - span.start;
                if (holds || node == root) && best.is_none_or(|(w, _)| width <= w) {
                    best = Some((if node == root { u32::MAX } else { width }, scope));
                }
            }
            stack.extend(tree.children(node));
        }
        let module = self.settled.library.files[file.0 as usize].module;
        best.map(|(_, s)| s).or_else(|| {
            (self.settled.modules.iter())
                .find(|m| m.id == module)
                .map(|m| m.scope)
        })
    }

    /// The node at a position, and the name it's a use or declaration
    /// of, if it is one. Names win over other nodes, then smaller spans.
    fn target(&self, file: FileId, offset: u32) -> Option<(NodeId, Option<Target>)> {
        let library = &self.settled.library;
        let tree = &library.tree;
        let root = library.files.get(file.0 as usize)?.root;
        let pos = self.global(file, offset);
        let mut best: Option<(Rank, NodeId, Option<Target>)> = None;
        let mut stack = vec![root];
        let mut seen = std::collections::HashSet::new();
        while let Some(node) = stack.pop() {
            if !seen.insert(node) {
                continue;
            }
            stack.extend(tree.children(node));
            let span = tree.span(node);
            if !(span.start <= pos && pos <= span.end) || span.start == span.end {
                continue;
            }
            let target = self.name_target(node);
            let typed = target.is_some() || self.settled.typing.expr(node).is_some();
            if !typed || node == root {
                continue;
            }
            // Prefer a span the position is inside over one it ends,
            // then names, then smaller spans.
            let key = (
                pos < span.end,
                target.is_some(),
                u32::MAX - (span.end - span.start),
            );
            if best.as_ref().is_none_or(|b| b.0 < key) {
                best = Some((key, node, target));
            }
        }
        best.map(|(_, node, target)| (node, target))
    }

    /// The name a node is, if it's one: a use, a declaration's name, or
    /// a member's name after `.`.
    fn name_target(&self, node: NodeId) -> Option<Target> {
        let library = &self.settled.library;
        let tree = &library.tree;
        match tree.kind(node) {
            NodeKind::RightName => self.settled.scopes.resolved(node).map(Target::Symbol),
            NodeKind::LeftName => {
                let parent = self.parent(node)?;
                if library.names.is_temporary(library.name(node)?) {
                    return None;
                }
                (matches!(tree.kind(parent), NodeKind::Let | NodeKind::Class)
                    && tree.children(parent).first() == Some(&node))
                .then(|| {
                    self.decl_symbols()
                        .get(&parent)
                        .copied()
                        .map(Target::Symbol)
                })
                .flatten()
            }
            NodeKind::Value => {
                let Some(Value::Symbol(key)) = library.value(node) else {
                    return None;
                };
                let call = self.parent(node)?;
                let children = tree.children(call);
                let is_dot = tree.kind(call) == NodeKind::Call
                    && children.len() == 3
                    && children[2] == node
                    && library
                        .name(children[0])
                        .is_some_and(|n| library.names.get(n) == ".");
                is_dot.then_some(Target::Member { call, key: *key })
            }
            _ => None,
        }
    }

    /// What a `.` call reads: its own resolution, or for a method call,
    /// the call around it's.
    fn member(&self, dot: NodeId) -> Option<&Resolved> {
        let typing = &self.settled.typing;
        let outer = self.parent(dot).filter(|&p| {
            let tree = &self.settled.library.tree;
            tree.kind(p) == NodeKind::Call && tree.children(p).first() == Some(&dot)
        });
        match typing.resolved(dot) {
            Some(r @ Resolved::Decl(_)) => Some(r),
            r => outer.and_then(|o| typing.resolved(o)).or(r),
        }
    }

    fn parent(&self, node: NodeId) -> Option<NodeId> {
        self.parents().get(node.0 as usize).copied().flatten()
    }

    /// What a symbol's declaration is.
    fn kind(&self, symbol: SymbolId) -> DeclKind {
        let s = self.settled.scopes.symbol(symbol);
        match (s.kind, s.decl) {
            (SymbolKind::Builtin(_), _) | (_, None) => DeclKind::Builtin,
            (SymbolKind::TypeParam, _) => DeclKind::TypeParameter,
            (kind, Some(decl)) => self.decl_kind(decl, Some(kind)),
        }
    }

    /// What a `Let` or `Class` declares.
    fn decl_kind(&self, decl: NodeId, kind: Option<SymbolKind>) -> DeclKind {
        let tree = &self.settled.library.tree;
        let data = tree.data(decl);
        if tree.kind(decl) == NodeKind::Class {
            return match data & CLASS_INTERFACE {
                0 => DeclKind::Class,
                _ => DeclKind::Interface,
            };
        }
        if tree.kind(decl) != NodeKind::Let {
            return DeclKind::Let;
        }
        match data & ROLE_MASK {
            ROLE_PARAM => return DeclKind::Field,
            ROLE_METHOD => return DeclKind::Method,
            ROLE_GETTER => return DeclKind::Getter,
            ROLE_SETTER => return DeclKind::Setter,
            ROLE_CONSTRUCTOR => return DeclKind::Constructor,
            ROLE_TYPE_PARAM => return DeclKind::TypeParameter,
            _ => {}
        }
        if kind == Some(SymbolKind::Member) {
            return DeclKind::Field;
        }
        if kind == Some(SymbolKind::Parameter) {
            return DeclKind::Parameter;
        }
        let init = core::let_parts(tree, decl).2;
        if data & LET_VAR == 0 && init.is_some_and(|i| tree.kind(i) == NodeKind::Fn) {
            return DeclKind::Function;
        }
        match data & LET_VAR {
            0 => DeclKind::Let,
            _ => DeclKind::Var,
        }
    }

    /// A declaration's type as hover shows it: a function's or method's
    /// signature with its parameters' names, else its type, or else the
    /// type at `use_site`, which may be narrower (or the only one known).
    fn decl_type(&self, decl: NodeId, name: &str, use_site: NodeId) -> Option<String> {
        self.decl_type_in(decl, name, use_site, &Subst::new())
    }

    /// [`Analysis::decl_type`], with type parameters in `subst` shown as
    /// their arguments, as a generic class's members on its instances.
    fn decl_type_in(
        &self,
        decl: NodeId,
        name: &str,
        use_site: NodeId,
        subst: &Subst,
    ) -> Option<String> {
        let tree = &self.settled.library.tree;
        let typing = &self.settled.typing;
        if tree.kind(decl) == NodeKind::Class {
            let params: Vec<String> = (core::type_params(tree, decl).iter())
                .filter_map(|&p| self.settled.library.name(tree.children(p)[0]))
                .map(|n| self.settled.library.names.get(n).to_string())
                .collect();
            return Some(match params.is_empty() {
                true => name.to_string(),
                false => format!("{name}<{}>", params.join(", ")),
            });
        }
        if tree.kind(decl) == NodeKind::Let {
            let init = core::let_parts(tree, decl).2;
            let role = tree.data(decl) & ROLE_MASK;
            if let Some(f) = init.filter(|&i| tree.kind(i) == NodeKind::Fn) {
                if role == ROLE_GETTER {
                    // A getter reads as a field of its result type.
                    let t = (typing.decls.get(&decl).copied()).or_else(|| typing.expr(f));
                    return match t.map(|t| (t, typing.types.get(t))) {
                        Some((_, TypeData::Fn { ret, .. })) => Some(self.show_in(*ret, subst)),
                        Some((t, _)) => Some(self.show_in(t, subst)),
                        None => typing.expr(use_site).map(|t| self.show(t)),
                    };
                }
                if tree.data(decl) & LET_VAR == 0 {
                    let t = typing.decls.get(&decl).copied();
                    return Some(self.signature(f, name, t, subst));
                }
            }
        }
        let t = typing
            .decls
            .get(&decl)
            .copied()
            .or_else(|| typing.expr(use_site))?;
        Some(self.show_in(t, subst))
    }

    /// A function's signature, `name(a: Int32, b?: String): Boolean`.
    fn signature(&self, f: NodeId, name: &str, t: Option<TypeId>, subst: &Subst) -> String {
        let (params, ret) = self.signature_parts(f, t, subst);
        let params: Vec<String> = params.into_iter().map(|(p, _)| p).collect();
        format!("{name}({}): {ret}", params.join(", "))
    }

    /// A function's parameters, as `b?: String`, each with its type, and
    /// its result type.
    fn signature_parts(
        &self,
        f: NodeId,
        t: Option<TypeId>,
        subst: &Subst,
    ) -> (Vec<(String, Option<TypeId>)>, String) {
        let tree = &self.settled.library.tree;
        let typing = &self.settled.typing;
        let (params, _, _) = core::fn_parts(tree, f);
        let fn_type = t.or_else(|| typing.expr(f)).map(|t| typing.types.get(t));
        let params = (params.iter().enumerate())
            .map(|(i, &p)| {
                let n = (self.settled.library.name(tree.children(p)[0]))
                    .map_or("_", |n| self.settled.library.names.get(n));
                let optional = core::let_parts(tree, p).2.is_some();
                // The parameter's own type, or else the function type's.
                let t = typing.decls.get(&p).copied().or_else(|| match fn_type {
                    Some(TypeData::Fn { params, .. }) => params.get(i).map(|p| p.ty),
                    _ => None,
                });
                let ty = t.map_or("?".into(), |t| self.show_in(t, subst));
                (format!("{n}{}: {ty}", if optional { "?" } else { "" }), t)
            })
            .collect();
        let ret = match t.or_else(|| typing.expr(f)).map(|t| typing.types.get(t)) {
            Some(TypeData::Fn { ret, throws, .. }) => {
                show_result(self.show_in(*ret, subst), *throws)
            }
            _ => "?".into(),
        };
        (params, ret)
    }

    /// The doc comment just before a declaration, if any: `/** … */`
    /// with nothing but modifiers and decorators between.
    fn doc(&self, decl: NodeId) -> Option<String> {
        let library = &self.settled.library;
        let tree = &library.tree;
        let name = match tree.kind(decl) {
            NodeKind::Let | NodeKind::Class => tree.children(decl)[0],
            _ => return None,
        };
        let (file, start) = library.locate(tree.span(name).start);
        let parsed = &library.files.get(file.0 as usize)?.parsed;
        let source = parsed.source();
        let comment = (parsed.comments().iter())
            .rev()
            .find(|c| c.end <= start && c.kind == CommentKind::Block)?;
        let text = source.get(comment.start as usize..comment.end as usize)?;
        let between = source.get(comment.end as usize..start as usize)?;
        if !text.starts_with("/**") || between.contains(|c| ";{}()=,/".contains(c)) {
            return None;
        }
        doc_text(text)
    }

    fn show(&self, t: TypeId) -> String {
        self.show_in(t, &Subst::new())
    }

    /// A node's span in its file.
    fn local(&self, node: NodeId) -> Span {
        let span = self.settled.library.tree.span(node);
        let (_, start) = self.settled.library.locate(span.start);
        Span {
            start,
            end: start + span.end.saturating_sub(span.start),
        }
    }

    /// A byte offset in a file as a library-wide position.
    fn global(&self, file: FileId, offset: u32) -> u32 {
        let base = self
            .settled
            .library
            .files
            .get(file.0 as usize)
            .map_or(0, |f| f.base);
        base + offset
    }

    /// The scopes, for callers that need more than the queries give.
    pub fn scopes(&self) -> &Scopes {
        &self.settled.scopes
    }
}

/// How well a node fits a position: whether the position is inside it
/// (not at its end), whether it's a name, and how small it is.
type Rank = (bool, bool, u32);

/// A doc comment's text, `/** … */`, without its markers or each line's
/// leading `*`; None for another comment or an empty one. Core's
/// docs-site directives go: a snippet included, `⎀ syntax/StringExpr`,
/// and a link to one, `[string indices][snippet/type/StringIndex]`,
/// which keeps its text.
pub(crate) fn doc_text(comment: &str) -> Option<String> {
    let body = comment.strip_prefix("/**")?.trim_end_matches("*/");
    let mut lines: Vec<String> = Vec::new();
    for line in body.lines() {
        let line = line.trim().trim_start_matches('*').trim();
        if line.starts_with('⎀') {
            continue;
        }
        // One blank line between paragraphs, where a snippet went.
        if line.is_empty() && lines.last().is_some_and(|l| l.is_empty()) {
            continue;
        }
        lines.push(without_snippet_links(line));
    }
    let doc = lines.join("\n").trim().to_string();
    (!doc.is_empty()).then_some(doc)
}

/// `[text][snippet/…]` as its `text`.
fn without_snippet_links(line: &str) -> String {
    let mut out = String::new();
    let mut rest = line;
    while let Some(at) = rest.find("][snippet/") {
        let after = &rest[at + 2..];
        let (Some(open), Some(close)) = (rest[..at].rfind('['), after.find(']')) else {
            break;
        };
        out.push_str(&rest[..open]);
        out.push_str(&rest[open + 1..at]);
        rest = &after[close + 1..];
    }
    out.push_str(rest);
    out
}

/// What a name at a position names.
#[derive(Clone, Copy, Debug)]
enum Target {
    Symbol(SymbolId),
    /// A member read, `obj.key`, by its `.` call.
    Member {
        call: NodeId,
        key: temper_syntax::names::NameId,
    },
}

/// Where a byte offset is, as JavaScript and editors count.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Position {
    /// UTF-16 code units before it.
    pub utf16: usize,
    /// Its line, from 1.
    pub line: usize,
    /// Its column in UTF-16 code units, from 1.
    pub column: usize,
}

impl Position {
    /// The position of a byte offset in `source`, clamped to its end and
    /// moved back to a character boundary.
    pub fn of(source: &str, offset: usize) -> Position {
        let mut offset = offset.min(source.len());
        while !source.is_char_boundary(offset) {
            offset -= 1;
        }
        let before = &source[..offset];
        let line_start = before.rfind('\n').map_or(0, |i| i + 1);
        Position {
            utf16: before.encode_utf16().count(),
            line: before.matches('\n').count() + 1,
            column: before[line_start..].encode_utf16().count() + 1,
        }
    }
}

/// The byte offset of a UTF-16 offset in `source`, clamped to its end; an
/// offset inside a surrogate pair counts as its character's start.
pub fn byte_offset(source: &str, utf16: usize) -> usize {
    let mut units = 0;
    for (i, c) in source.char_indices() {
        units += c.len_utf16();
        if units > utf16 {
            return i;
        }
    }
    source.len()
}
