//! Fields first, Swift-style two-phase initialization (decided by the
//! Temper team, 2026-11-02; notes/typing.md, "Fields first"): until every
//! field of a new instance is set on every path, its constructor, and the
//! field initializers that run before it, may set fields, read those
//! already set, and use anything not involving `this`, but nothing else
//! of `this`: no method calls on it, no getter or setter with a body, no
//! passing, returning or storing it, and no function capturing it. And
//! every path by which the constructor returns normally sets every field,
//! since the new instance escapes there. Kotlin Temper doesn't check this
//! yet (notes/kotlin-differences.md); it reports only a field without an
//! initializer in a class without a constructor, with the message used
//! here.
//!
//! A field is a backed property: a non-static member declared as `x: T`,
//! `x: T = v`, `var x: T`, or in the class's header. One with an
//! initializer, or a header's, is set by the time the constructor runs,
//! in declaration order, as the interpreter and Kotlin set them; so an
//! initializer may read only the fields declared before it. Interfaces
//! have no backed properties, so a supertype's property is a getter or
//! setter here, which counts as a use of `this`.
//!
//! One pass over the constructor in evaluation order, as the flow facts
//! ([`crate::flow`]) are found, carries the set of fields known to be set:
//! branches merge by intersection; a loop starts each pass with what held
//! on entry, since nothing unsets a field, and what follows it is what
//! its `break`s carry; an `orelse`'s fallback starts with what held before
//! its body; and a path that bubbles or panics needs nothing set. Each
//! offending use of `this` gets one error, naming the fields not yet set.

use crate::core::{
    self, CLASS_INTERFACE, FN_LOOP_BODY, MEMBER_STATIC, ROLE_CONSTRUCTOR, ROLE_GETTER, ROLE_MASK,
    ROLE_METHOD, ROLE_PARAM, ROLE_SETTER,
};
use crate::library::{Level, Message};
use crate::scope::{ScopeId, SymbolId};
use crate::settle::Settle;
use std::collections::{HashMap, HashSet};
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind, Span};

/// The fields known to be set at a point, by index, or `None` where
/// control can't reach.
type Set = Option<Vec<bool>>;

/// What a member of `this` is, by its name.
#[derive(Clone, Copy, PartialEq)]
enum Member {
    /// A field, by its index.
    Field(usize),
    Getter,
    Setter,
    /// A getter and a setter.
    Accessors,
    Method,
    /// Anything else: an inherited abstract property, say.
    Other,
}

/// One constructor's check.
struct Cx {
    class_scope: ScopeId,
    /// `this`, and temporaries holding it, as `this.x += 1` makes.
    this: HashSet<SymbolId>,
    fields: Vec<NameId>,
    members: HashMap<NameId, Member>,
    /// The constructor's `Fn`, which its `return`s name.
    ctor: Option<NodeId>,
    /// What reaches each `break`'s target, and the constructor's end by
    /// `return`.
    breaks: HashMap<NodeId, Set>,
    returns: Set,
    errors: Vec<(Span, Message)>,
}

fn merge(a: Set, b: Set) -> Set {
    match (a, b) {
        (None, s) | (s, None) => s,
        (Some(mut a), Some(b)) => {
            for (x, y) in a.iter_mut().zip(b) {
                *x &= y;
            }
            Some(a)
        }
    }
}

fn all_set(set: &Set) -> bool {
    set.as_ref().is_none_or(|s| s.iter().all(|&b| b))
}

impl Settle {
    /// Checks each class's constructor and field initializers.
    pub(super) fn check_fields_first(&mut self) {
        let mut classes = Vec::new();
        let mut stack = self.round_roots();
        while let Some(node) = stack.pop() {
            if self.lib.tree.kind(node) == NodeKind::Class
                && self.lib.tree.data(node) & CLASS_INTERFACE == 0
            {
                classes.push(node);
            }
            stack.extend(self.lib.tree.children(node).iter().copied());
        }
        let mut errors = Vec::new();
        for class in classes {
            errors.extend(self.fields_first(class));
        }
        errors.sort_by_key(|(span, _)| (span.start, span.end));
        for (span, message) in errors {
            self.lib.report(Level::Error, message, span);
        }
    }

    fn fields_first(&self, class: NodeId) -> Vec<(Span, Message)> {
        let tree = &self.lib.tree;
        let Some(class_scope) = self.scopes.of_node(class) else {
            return Vec::new();
        };
        let scope = self.scopes.get(class_scope);
        if scope.kind != crate::scope::ScopeKind::Class || scope.names.is_empty() {
            return Vec::new();
        }
        let (_, _, members) = core::class_parts(tree, class);
        let role = |m: NodeId| tree.data(m) & ROLE_MASK;
        let is_static = |m: NodeId| tree.data(m) & MEMBER_STATIC != 0;
        let fields: Vec<NodeId> = (members.iter().copied())
            .filter(|&m| {
                tree.kind(m) == NodeKind::Let
                    && matches!(role(m), 0 | ROLE_PARAM)
                    && !is_static(m)
                    && self.lib.name(tree.children(m)[0]).is_some()
            })
            .collect();
        let field_names: Vec<NameId> = (fields.iter())
            .map(|&m| self.lib.name(tree.children(m)[0]).unwrap())
            .collect();
        let mut kinds: HashMap<NameId, Member> = HashMap::new();
        for &(name, symbol) in &scope.names[1..] {
            let Some(decl) = self.scopes.symbol(symbol).decl else {
                continue;
            };
            if tree.kind(decl) != NodeKind::Let || is_static(decl) {
                continue;
            }
            let own_field = fields.iter().position(|&f| f == decl);
            let kind = match (own_field, role(decl)) {
                (Some(i), _) => Member::Field(i),
                (None, ROLE_GETTER) => Member::Getter,
                (None, ROLE_SETTER) => Member::Setter,
                (None, ROLE_METHOD) => Member::Method,
                (None, ROLE_CONSTRUCTOR) => continue,
                _ => Member::Other,
            };
            let kind = match (kinds.get(&name).copied(), kind) {
                (None, k) | (Some(Member::Other), k) => k,
                (Some(f @ Member::Field(_)), _) | (_, f @ Member::Field(_)) => f,
                (Some(Member::Getter), Member::Setter) | (Some(Member::Setter), Member::Getter) => {
                    Member::Accessors
                }
                (Some(k), _) => k,
            };
            kinds.insert(name, kind);
        }
        let ctors: Vec<NodeId> = (members.iter().copied())
            .filter(|&m| role(m) == ROLE_CONSTRUCTOR && !is_static(m))
            .collect();
        let mut cx = Cx {
            class_scope,
            this: HashSet::from([scope.names[0].1]),
            fields: field_names,
            members: kinds,
            ctor: None,
            breaks: HashMap::new(),
            returns: None,
            errors: Vec::new(),
        };
        // Initializers, in order, then each constructor.
        let mut set: Set = Some(vec![false; fields.len()]);
        for (i, &field) in fields.iter().enumerate() {
            let init = core::let_parts(tree, field).2;
            if let Some(init) = init {
                set = self.fields_flow(&mut cx, init, set);
            }
            let header = role(field) == ROLE_PARAM;
            if init.is_some() || header || ctors.is_empty() {
                if init.is_none() && !header && !self.core_content(field) {
                    let name = self.lib.names.get(cx.fields[i]).to_string();
                    let span = tree.span(tree.children(field)[0]);
                    cx.errors
                        .push((span, Message::PropertyNotInitializedInConstructor(name)));
                }
                if let Some(s) = &mut set {
                    s[i] = true;
                }
            }
        }
        for ctor in ctors {
            let Some(f) = core::member_fn(tree, ctor) else {
                continue;
            };
            cx.ctor = Some(f);
            cx.returns = None;
            cx.breaks.clear();
            cx.this = HashSet::from([scope.names[0].1]);
            let (params, _, body) = core::fn_parts(tree, f);
            let mut at = set.clone();
            for param in params {
                if let Some(default) = core::let_parts(tree, param).2 {
                    at = self.fields_flow(&mut cx, default, at);
                }
            }
            if let Some(body) = body {
                at = self.fields_flow(&mut cx, body, at);
            }
            let end = merge(at, cx.returns.take());
            if let Some(end) = end {
                let span = tree.span(tree.children(ctor)[0]);
                for (i, done) in end.iter().enumerate() {
                    if !done {
                        let name = self.lib.names.get(cx.fields[i]).to_string();
                        cx.errors
                            .push((span, Message::PropertyNotInitializedInConstructor(name)));
                    }
                }
            }
        }
        cx.errors
    }

    /// Core's `content: builtins.T`, which backends' own code sets, as
    /// Kotlin allows.
    fn core_content(&self, field: NodeId) -> bool {
        let tree = &self.lib.tree;
        let name = tree.children(field)[0];
        let named = (self.lib.name(name)).is_some_and(|n| self.lib.names.get(n) == "content");
        let (start, end) = (tree.span(name).end, tree.span(field).end);
        let ty = self.lib.source_text(Span { start, end });
        named
            && (ty.trim_start().strip_prefix(':'))
                .is_some_and(|t| t.trim().starts_with("builtins."))
    }

    /// Walks a node, given which fields are set before it, and gives
    /// which are set after it.
    fn fields_flow(&self, cx: &mut Cx, node: NodeId, set: Set) -> Set {
        if all_set(&set) {
            return set;
        }
        let tree = &self.lib.tree;
        let children = tree.children(node);
        match tree.kind(node) {
            NodeKind::Block => {
                let entry = set.clone();
                let mut set = set;
                for (i, &child) in children.iter().enumerate() {
                    // A local function may be used before it's declared,
                    // and then captures what held on entry.
                    let used_before = self.hoists(child)
                        && self
                            .scopes
                            .resolved(tree.children(child)[0])
                            .is_some_and(|f| children[..i].iter().any(|&c| self.mentions(c, f)));
                    if used_before {
                        self.fields_flow(cx, child, entry.clone());
                    } else {
                        set = self.fields_flow(cx, child, set);
                    }
                }
                let broken = cx.breaks.remove(&node).flatten();
                merge(set, broken)
            }
            NodeKind::If if children.len() == 3 => {
                let set = self.fields_flow(cx, children[0], set);
                // `while (true)` checks a constant.
                let constant = self.boolean_constant(children[0]);
                let then = self.fields_flow(
                    cx,
                    children[1],
                    set.clone().filter(|_| constant != Some(false)),
                );
                let other =
                    self.fields_flow(cx, children[2], set.filter(|_| constant != Some(true)));
                merge(then, other)
            }
            // Each pass starts with what held on entry, as nothing unsets
            // a field; only `break` leaves.
            NodeKind::Loop => {
                for &child in children {
                    self.fields_flow(cx, child, set.clone());
                }
                cx.breaks.remove(&node).flatten()
            }
            NodeKind::Break => {
                let target = NodeId(tree.data(node));
                let before = cx.breaks.remove(&target).flatten();
                cx.breaks.insert(target, merge(before, set));
                None
            }
            NodeKind::Continue | NodeKind::Fail => None,
            NodeKind::Return => {
                let mut set = set;
                for &child in children {
                    set = self.fields_flow(cx, child, set);
                }
                if Some(NodeId(tree.data(node))) == cx.ctor {
                    cx.returns = merge(cx.returns.take(), set);
                }
                None
            }
            // The fallback may follow a failure anywhere in the body.
            NodeKind::Orelse if children.len() == 2 => {
                let body = self.fields_flow(cx, children[0], set.clone());
                let fallback = self.fields_flow(cx, children[1], set);
                merge(body, fallback)
            }
            NodeKind::Fn if tree.data(node) & FN_LOOP_BODY != 0 => {
                if let Some(body) = core::fn_parts(tree, node).2 {
                    self.fields_flow(cx, body, set.clone());
                }
                set
            }
            NodeKind::Fn => {
                if let Some(use_) = self.captured_this(cx, node) {
                    let error = Message::ThisBeforeFieldsSet(
                        "Capturing this in a function".into(),
                        self.unset(cx, &set),
                    );
                    cx.errors.push((tree.span(use_), error));
                }
                set
            }
            NodeKind::Class => set,
            NodeKind::Let => {
                let init = core::let_parts(tree, node).2;
                // `this.x += 1` reads `this` once, through a temporary.
                if let Some(init) = init.filter(|&i| self.is_this(cx, i)) {
                    let name = tree.children(node)[0];
                    if self.is_temporary(name) {
                        if let Some(symbol) = self.scopes.resolved(name) {
                            cx.this.insert(symbol);
                            return set;
                        }
                    }
                    self.this_use(cx, init, "Using this", &set);
                    return set;
                }
                match init {
                    Some(init) => self.fields_flow(cx, init, set),
                    None => set,
                }
            }
            NodeKind::Assign if children.len() == 2 => {
                let (target, value) = (children[0], children[1]);
                let mut set = self.fields_flow(cx, value, set);
                match self.member_of_this(cx, target) {
                    Some((Member::Field(i), _)) => {
                        if let Some(s) = &mut set {
                            s[i] = true;
                        }
                    }
                    Some((Member::Setter | Member::Accessors, name)) => {
                        let what = format!("Calling setter this.{name}");
                        self.this_use(cx, target, &what, &set);
                    }
                    Some((_, name)) => {
                        let what = format!("Setting this.{name}");
                        self.this_use(cx, target, &what, &set);
                    }
                    None => {
                        if let Some((object, _)) = self.dot_parts(target) {
                            set = self.fields_flow(cx, object, set);
                        }
                    }
                }
                set
            }
            NodeKind::Call | NodeKind::RightName => {
                if let Some((member, name)) = self.member_of_this(cx, node) {
                    self.member_read(cx, node, member, &name, &set);
                    return set;
                }
                if tree.kind(node) == NodeKind::RightName {
                    if self.is_this(cx, node) {
                        self.this_use(cx, node, "Using this", &set);
                    }
                    return set;
                }
                let Some((&callee, args)) = children.split_first() else {
                    return set;
                };
                let mut set = set;
                match self.member_of_this(cx, callee) {
                    Some((Member::Field(i), name)) => {
                        self.member_read(cx, callee, Member::Field(i), &name, &set)
                    }
                    Some((Member::Method, name)) => {
                        let what = format!("Calling this.{name}()");
                        self.this_use(cx, callee, &what, &set);
                    }
                    Some((member, name)) => self.member_read(cx, callee, member, &name, &set),
                    None => set = self.fields_flow(cx, callee, set),
                }
                for &arg in args {
                    set = self.fields_flow(cx, arg, set);
                }
                if self.never_returns(node) {
                    return None;
                }
                set
            }
            _ => {
                let mut set = set;
                for &child in children {
                    set = self.fields_flow(cx, child, set);
                }
                set
            }
        }
    }

    /// Reports reading a member of `this`, unless it's a field already set.
    fn member_read(&self, cx: &mut Cx, node: NodeId, member: Member, name: &str, set: &Set) {
        let what = match member {
            Member::Field(i) => {
                if set.as_ref().is_none_or(|s| s[i]) {
                    return;
                }
                let error = Message::ThisBeforeFieldsSet(
                    format!("Reading this.{name}"),
                    vec![name.to_string()],
                );
                cx.errors.push((self.lib.tree.span(node), error));
                return;
            }
            Member::Getter | Member::Accessors => format!("Calling getter this.{name}"),
            _ => format!("Using this.{name}"),
        };
        self.this_use(cx, node, &what, set);
    }

    /// Reports a use of `this` where some field may not be set.
    fn this_use(&self, cx: &mut Cx, node: NodeId, what: &str, set: &Set) {
        if all_set(set) {
            return;
        }
        let error = Message::ThisBeforeFieldsSet(what.to_string(), self.unset(cx, set));
        cx.errors.push((self.lib.tree.span(node), error));
    }

    /// The fields not yet set, by name.
    fn unset(&self, cx: &Cx, set: &Set) -> Vec<String> {
        let Some(set) = set else {
            return Vec::new();
        };
        (cx.fields.iter().zip(set))
            .filter(|(_, done)| !**done)
            .map(|(&name, _)| self.lib.names.get(name).to_string())
            .collect()
    }

    /// Whether a node is `this`, or a temporary holding it.
    fn is_this(&self, cx: &Cx, node: NodeId) -> bool {
        self.lib.tree.kind(node) == NodeKind::RightName
            && self
                .scopes
                .resolved(node)
                .is_some_and(|s| cx.this.contains(&s))
    }

    /// For `this.name`, or a bare `name` meaning it, the member it is.
    fn member_of_this(&self, cx: &Cx, node: NodeId) -> Option<(Member, String)> {
        let name = match self.lib.tree.kind(node) {
            NodeKind::RightName => {
                let symbol = self.scopes.symbol(self.scopes.resolved(node)?);
                if symbol.owner != cx.class_scope || cx.this.contains(&self.scopes.resolved(node)?)
                {
                    return None;
                }
                symbol.name
            }
            NodeKind::Call => {
                let (object, key) = self.dot_parts(node)?;
                if !self.is_this(cx, object) {
                    return None;
                }
                self.lib.symbol(key)?
            }
            _ => return None,
        };
        let member = cx.members.get(&name).copied()?;
        Some((member, self.lib.names.get(name).to_string()))
    }

    /// Whether a node names a symbol anywhere in it.
    fn mentions(&self, node: NodeId, symbol: SymbolId) -> bool {
        self.scopes.resolved(node) == Some(symbol)
            || (self.lib.tree.children(node).iter()).any(|&c| self.mentions(c, symbol))
    }

    /// The first use of `this` in a function, if it uses it.
    fn captured_this(&self, cx: &Cx, node: NodeId) -> Option<NodeId> {
        let tree = &self.lib.tree;
        match tree.kind(node) {
            NodeKind::Class => None,
            NodeKind::RightName if self.is_this(cx, node) => Some(node),
            NodeKind::RightName | NodeKind::Call if self.member_of_this(cx, node).is_some() => {
                Some(node)
            }
            _ => (tree.children(node).iter()).find_map(|&c| self.captured_this(cx, c)),
        }
    }
}
