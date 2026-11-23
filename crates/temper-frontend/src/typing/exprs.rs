//! Which nodes are expressions, for slice 7 of milestone 4, "every
//! expression typed" (notes/typing.md, "Every expression typed"): typing
//! works out each one's type once checking is done, so that tmpl's
//! translation can read it ([`super::Typing::expr`]), and [`coverage`]
//! counts, per file, the expressions whose type is still unknown, the
//! calls typing didn't resolve ([`super::resolve`]), and the `==` uses
//! that rely on the interpreter's identity fallback.
//!
//! An expression is a node whose value a program computes: a value other
//! than a symbol, a name read as a value, a call, a function, and a
//! block, `if` or `orelse` whose value is used, as a declaration's
//! initializer, a value assigned or returned, an argument, or a condition.
//! Not a type, a declared name, a named argument's symbol, a callee that
//! names what's called (a builtin, a function, or `o.m` for a method,
//! though `o` is an expression, unless it's `console`), a name that's
//! assigned, the lists a tag such as `char'a'` is given (though values
//! in their holes are expressions), nor a statement (`let`, loops and
//! jumps).

use crate::builtins::{Builtin, Constant, Function};
use crate::core;
use crate::library::Library;
use crate::scope::{Scopes, SymbolKind};
use crate::settle::{Settle, Settled};
use temper_syntax::ast::Value;
use temper_syntax::tree::{NodeId, NodeKind};

/// An expression found by [`Exprs`]: whether it's a call that typing
/// resolves to what it calls ([`super::resolve`]).
#[derive(Clone, Copy)]
pub(crate) struct Expr {
    pub node: NodeId,
    pub resolves: bool,
}

/// Finds the expressions under a node.
pub(crate) struct Exprs<'a> {
    lib: &'a Library,
    scopes: &'a Scopes,
}

impl<'a> Exprs<'a> {
    pub(crate) fn new(lib: &'a Library, scopes: &'a Scopes) -> Self {
        Exprs { lib, scopes }
    }

    /// The expressions under a file's root, in tree order.
    pub(crate) fn under(&self, root: NodeId) -> Vec<Expr> {
        let mut out = Vec::new();
        self.stmt(root, &mut out);
        out
    }

    fn kind(&self, node: NodeId) -> NodeKind {
        self.lib.tree.kind(node)
    }

    fn children(&self, node: NodeId) -> &'a [NodeId] {
        self.lib.tree.children(node)
    }

    /// A node whose value isn't used.
    fn stmt(&self, node: NodeId, out: &mut Vec<Expr>) {
        self.visit(node, false, out);
    }

    /// A node whose value is used.
    fn value(&self, node: NodeId, out: &mut Vec<Expr>) {
        self.visit(node, true, out);
    }

    fn visit(&self, node: NodeId, used: bool, out: &mut Vec<Expr>) {
        let children = self.children(node);
        match self.kind(node) {
            NodeKind::Value => {
                if !matches!(self.lib.value(node), Some(Value::Symbol(_))) {
                    out.push(Expr {
                        node,
                        resolves: false,
                    });
                }
            }
            NodeKind::RightName => {
                if !self.names_type(node) {
                    out.push(Expr {
                        node,
                        resolves: false,
                    });
                }
            }
            NodeKind::Block => {
                if used {
                    out.push(Expr {
                        node,
                        resolves: false,
                    });
                }
                if let Some((&last, rest)) = children.split_last() {
                    for &child in rest {
                        self.stmt(child, out);
                    }
                    self.visit(last, used, out);
                }
            }
            NodeKind::If | NodeKind::Orelse => {
                if used {
                    out.push(Expr {
                        node,
                        resolves: false,
                    });
                }
                let branches = children.len().saturating_sub(2);
                for (i, &child) in children.iter().enumerate() {
                    if i < branches {
                        // An `if`'s condition.
                        self.value(child, out);
                    } else {
                        self.visit(child, used, out);
                    }
                }
            }
            NodeKind::Loop => {
                for &child in children {
                    self.stmt(child, out);
                }
            }
            NodeKind::Return | NodeKind::Yield | NodeKind::Await => {
                if self.kind(node) == NodeKind::Await && used {
                    out.push(Expr {
                        node,
                        resolves: false,
                    });
                }
                for &child in children {
                    self.value(child, out);
                }
            }
            NodeKind::Assign => {
                if let [target, value] = *children {
                    if let Some(object) = self.dot_object(target) {
                        self.value(object, out);
                    }
                    self.value(value, out);
                }
            }
            NodeKind::Let => {
                let (_, _, init) = core::let_parts(&self.lib.tree, node);
                let is_member = self.is_member(node);
                match init {
                    // A method's, getter's, setter's or constructor's
                    // function isn't a value.
                    Some(f) if is_member && self.kind(f) == NodeKind::Fn => self.fn_parts(f, out),
                    Some(init) => self.value(init, out),
                    None => {}
                }
            }
            NodeKind::Fn => {
                out.push(Expr {
                    node,
                    resolves: false,
                });
                self.fn_parts(node, out);
            }
            NodeKind::Class => {
                for &member in core::class_parts(&self.lib.tree, node).2 {
                    self.stmt(member, out);
                }
            }
            NodeKind::Call => self.call(node, out),
            _ => {}
        }
    }

    /// A function's parameters' defaults and its body.
    fn fn_parts(&self, f: NodeId, out: &mut Vec<Expr>) {
        let (params, _, body) = core::fn_parts(&self.lib.tree, f);
        for param in params {
            if self.kind(param) == NodeKind::Let {
                if let Some(default) = core::let_parts(&self.lib.tree, param).2 {
                    self.value(default, out);
                }
            }
        }
        if let Some(body) = body {
            self.stmt(body, out);
        }
    }

    fn call(&self, node: NodeId, out: &mut Vec<Expr>) {
        let Some((&callee, args)) = self.children(node).split_first() else {
            return;
        };
        let builtin = match self.scopes.resolved_builtin(callee) {
            Some(Builtin::Function(f)) => Some(f),
            Some(_) => None,
            None => None,
        };
        let args = match builtin {
            // Types, not values.
            Some(Function::Angle | Function::Nullable | Function::FnType | Function::Throws) => {
                return;
            }
            Some(Function::Is | Function::As) => {
                out.push(Expr {
                    node,
                    resolves: false,
                });
                if let Some(&value) = args.first() {
                    self.value(value, out);
                }
                return;
            }
            Some(Function::Dot) => {
                out.push(Expr {
                    node,
                    resolves: true,
                });
                if let Some(object) = self.dot_object(node) {
                    self.value(object, out);
                }
                return;
            }
            // A tag's parts, as `char'a'`: lists of the literal text and
            // the values in holes, which are expressions.
            Some(Function::Char | Function::Raw) => {
                out.push(Expr {
                    node,
                    resolves: true,
                });
                for &list in args {
                    for &part in self.children(list).get(1..).unwrap_or(&[]) {
                        self.value(part, out);
                    }
                }
                return;
            }
            // The class made.
            Some(Function::New) => args.get(1..).unwrap_or(&[]),
            _ => args,
        };
        out.push(Expr {
            node,
            resolves: true,
        });
        if builtin.is_none() {
            match self.kind(callee) {
                // A function's or member's name.
                NodeKind::RightName => {}
                // A method's receiver.
                NodeKind::Call if self.is_dot(callee) => {
                    if let Some(object) = self.dot_object(callee) {
                        self.value(object, out);
                    }
                }
                NodeKind::Call if self.is_angle(callee) => {}
                _ => self.value(callee, out),
            }
        }
        let mut rest = args;
        while let Some((&arg, more)) = rest.split_first() {
            rest = more;
            match self.lib.symbol(arg) {
                // `\typeArg T`: a type follows.
                Some(key) if self.lib.names.get(key) == "typeArg" => {
                    rest = rest.get(1..).unwrap_or(&[]);
                }
                Some(_) => {}
                None => self.value(arg, out),
            }
        }
    }

    /// `o` in `o.name`, unless it names a type, as `C` in `C.make`, or
    /// is `console`, as `console.log` is a builtin.
    fn dot_object(&self, node: NodeId) -> Option<NodeId> {
        match (self.kind(node), self.children(node)) {
            (NodeKind::Call, &[_, object, _]) if self.is_dot(node) => {
                let is_console =
                    self.scopes.resolved_builtin(object) == Some(Builtin::Value(Constant::Console));
                (!self.names_type(object) && !is_console).then_some(object)
            }
            _ => None,
        }
    }

    /// Whether a call is `o.name`.
    fn is_dot(&self, node: NodeId) -> bool {
        self.children(node).first().is_some_and(|&c| {
            self.scopes.resolved_builtin(c) == Some(Builtin::Function(Function::Dot))
        })
    }

    /// Whether a call is `f<T>`, a callee given type arguments.
    fn is_angle(&self, node: NodeId) -> bool {
        self.children(node).first().is_some_and(|&c| {
            self.scopes.resolved_builtin(c) == Some(Builtin::Function(Function::Angle))
        })
    }

    /// Whether a name names a type: a builtin one, a type parameter, or a
    /// class or interface, directly or through aliases, as `let { Date } =
    /// import(…)` makes ([`Scopes::type_symbol`]).
    fn names_type(&self, node: NodeId) -> bool {
        if self.kind(node) != NodeKind::RightName {
            return false;
        }
        let Some(b) = self.scopes.resolved(node) else {
            return false;
        };
        let symbol = self
            .scopes
            .symbol(self.scopes.type_symbol(&self.lib.tree, b));
        match (symbol.kind, symbol.decl) {
            (SymbolKind::Builtin(Builtin::Type(_)) | SymbolKind::TypeParam, _) => true,
            (_, Some(d)) => self.kind(d) == NodeKind::Class,
            _ => false,
        }
    }

    /// Whether a `Let` declares a class's member.
    fn is_member(&self, node: NodeId) -> bool {
        let name = self.children(node)[0];
        self.scopes
            .resolved(name)
            .is_some_and(|b| self.scopes.symbol(b).kind == SymbolKind::Member)
    }

    /// What an expression is, for [`coverage`]'s report: its kind and
    /// what it names or calls.
    fn describe(&self, node: NodeId) -> String {
        let name_of = |n: NodeId| {
            self.lib
                .name(n)
                .or_else(|| self.lib.symbol(n))
                .map_or("?", |n| self.lib.names.get(n))
        };
        match self.kind(node) {
            NodeKind::RightName => match self.scopes.resolved(node) {
                Some(b) => format!("name {:?}", self.scopes.symbol(b).kind),
                None => "name unresolved".into(),
            },
            NodeKind::Call => {
                let children = self.children(node);
                let callee = children[0];
                if let Some(Builtin::Function(f)) = self.scopes.resolved_builtin(callee) {
                    return match (f, children) {
                        (Function::Dot, &[_, _, key]) => format!("read .{}", name_of(key)),
                        _ => format!("call {f:?}"),
                    };
                }
                match self.kind(callee) {
                    NodeKind::RightName => match self.scopes.resolved(callee) {
                        Some(b) => format!("call of {:?}", self.scopes.symbol(b).kind),
                        None => "call of unresolved".into(),
                    },
                    NodeKind::Call => match self.children(callee) {
                        &[dot, _, key]
                            if self.scopes.resolved_builtin(dot)
                                == Some(Builtin::Function(Function::Dot)) =>
                        {
                            format!("call .{}", name_of(key))
                        }
                        _ => "call of value".into(),
                    },
                    _ => "call of value".into(),
                }
            }
            kind => format!("{kind:?}"),
        }
    }
}

impl Settle {
    /// Works out every expression's type, once checking is done, so that
    /// [`super::Typing::expr`] gives it, and resolves each call
    /// ([`super::resolve`]).
    pub(super) fn type_all_exprs(&mut self) {
        // Missing `else`s never reached, where the `if` is a statement
        // too, which panic, as Kotlin's ([`super::sealed`]).
        for i in self.round_nodes()..self.lib.tree.len() as u32 {
            let node = NodeId(i);
            let tree = &self.lib.tree;
            if tree.kind(node) == NodeKind::Block && tree.children(node).is_empty() {
                let _ = self.unreachable_else(node, false);
            }
        }
        for root in self.round_roots() {
            let exprs = Exprs::new(&self.lib, &self.scopes).under(root);
            for expr in exprs {
                // A generic function's own type parameters are unknown
                // where it's referred to; its own type has them.
                let node = expr.node;
                let is_fn = self.lib.tree.kind(node) == NodeKind::Fn;
                if is_fn && !core::type_params(&self.lib.tree, node).is_empty() {
                    let t = self.fn_signature(node);
                    self.typing.set_expr(node, t);
                }
                self.known_type(node);
                if expr.resolves {
                    self.resolve_call(expr.node);
                }
            }
        }
        // Each class member's type, for tmpl: a field's, a getter's
        // result, or a method's signature without `this`.
        for i in self.round_nodes()..self.lib.tree.len() as u32 {
            let class = NodeId(i);
            if self.lib.tree.kind(class) != NodeKind::Class {
                continue;
            }
            let members = core::class_parts(&self.lib.tree, class).2.to_vec();
            for m in members {
                if self.lib.tree.kind(m) != NodeKind::Let {
                    continue;
                }
                if let std::collections::hash_map::Entry::Vacant(_) = self.typing.decls.entry(m) {
                    let t = self.member_type(m);
                    if t != crate::types::UNKNOWN {
                        self.typing.decls.insert(m, t);
                    }
                }
                let f = core::let_parts(&self.lib.tree, m).2;
                if let Some(f) = f.filter(|&f| self.lib.tree.kind(f) == NodeKind::Fn) {
                    // A generic method's own type has its type parameters,
                    // as a generic function's does.
                    if !core::type_params(&self.lib.tree, f).is_empty() {
                        let t = self.method_signature(f);
                        self.typing.set_expr(f, t);
                        continue;
                    }
                    let known = self.typing.expr(f);
                    let known = known.filter(|&t| t != crate::types::UNKNOWN);
                    if known.is_none() {
                        let t = self.method_type(f);
                        self.typing.set_expr(f, t);
                    }
                }
            }
        }
    }
}

/// What typing left unknown in one source file ([`coverage`]).
#[derive(Debug)]
pub struct FileCoverage {
    pub path: String,
    /// How many expressions it has.
    pub exprs: usize,
    /// Those of unknown type, or whose type has unknown parts, as
    /// `List<?>`, each with what it is.
    pub untyped: Vec<(NodeId, String)>,
    /// How many calls, operators and member reads it has.
    pub calls: usize,
    /// Those not resolved to what they call, each with what it is.
    pub unresolved: Vec<(NodeId, String)>,
    /// `==` and `!=` uses with no equality to call but identity, each with
    /// why: an operand of a type parameter, `AnyValue`, a function type,
    /// a class or interface with no `==` typing can find, or a type
    /// typing doesn't know.
    pub identity_eqs: Vec<(NodeId, String)>,
}

/// What typing left unknown, per source file, in file order.
pub fn coverage(s: &Settled) -> Vec<FileCoverage> {
    let exprs = Exprs::new(&s.library, &s.scopes);
    let typing = &s.typing;
    let mut out = Vec::new();
    for file in &s.library.files {
        let mut cover = FileCoverage {
            path: file.path.clone(),
            exprs: 0,
            untyped: Vec::new(),
            calls: 0,
            unresolved: Vec::new(),
            identity_eqs: Vec::new(),
        };
        for expr in exprs.under(file.root) {
            let node = expr.node;
            cover.exprs += 1;
            let known = typing.expr(node).is_some_and(|t| typing.types.is_known(t));
            if !known {
                cover.untyped.push((node, exprs.describe(node)));
            }
            if expr.resolves {
                cover.calls += 1;
                if let Some(why) = typing.identity_eq(node) {
                    cover.identity_eqs.push((node, why.to_string()));
                } else if typing.resolved(node).is_none() {
                    cover.unresolved.push((node, exprs.describe(node)));
                }
            }
        }
        out.push(cover);
    }
    out
}
