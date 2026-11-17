//! Contextual autoescaping worked out at compile time, as Kotlin Temper's
//! `OptimizeContextualAutoescapingBlocks` does (notes/compile-time.md,
//! "Content tags"): a tagged string, as a library's `html"…"`,
//! settles to
//!
//! ```text
//! do { let t = new C(); t.appendSafe("<a href='"); t.append(x); …; t.accumulated }
//! ```
//!
//! and where `C` is a class implementing an interface named
//! `ContextualAutoescapingAccumulator`, this pass runs `C`'s context
//! propagation over the fixed parts with the interpreter, once, at
//! compile time, and rewrites the block to
//!
//! ```text
//! do {
//!   let t = C.newCollector();
//!   t.append("<a href='");                 // each fixed part's effects
//!   t.append(E2.instance.apply(E1.instance.apply(x)));  // the escapers picked
//!   …;
//!   C.fromCollector(t)
//! }
//! ```
//!
//! so generated code calls the escapers directly, with no run-time
//! context propagation. It follows the conventions Kotlin's pass expects
//! of a `ContextualAutoescapingAccumulator`: statics `initialState`,
//! `propagator`, `picker`, `sameStatePredicate`, `mergeStates`,
//! `checkEndState`, `newCollector`, `fromCollector` and `enact`, maybe
//! `foldAnalyses` with `exportAnalyses`, the core module's
//! `propagateOver`, `AppendParseEffect` and `EventParseEffect`, and
//! escapers with a static `instance`, composed ones with fields `first`
//! and `second`. Problems the library reports through the callout it's
//! given become diagnostics, hints in a test's body, as Kotlin's infos.
//!
//! The walk follows blocks, branches, loops, and jumps over the tree
//! ([`Analysis::walk`]): where paths join, and where a loop comes back to
//! its start, their states must be the same by `sameStatePredicate`. So
//! the convention needs that predicate to be exact equality on
//! everything later propagation and the picker read: states it calls the
//! same must give the same effects, escapers, and states after.
//!
//! Where anything doesn't fit, the block is left as it is and propagates
//! its contexts at run time, as before: an accumulator used other than by
//! its appends and `.accumulated`, paths or loop passes that end in other
//! contexts, appends in closures (but `for … of` bodies) or conditions,
//! a fixed part that isn't a constant, a value the interpreter fails on
//! or that code can't make again, or a run past its steps.

use crate::builtins::{Builtin, Function};
use crate::core::{
    self, CLASS_INTERFACE, FN_LOOP_BODY, LET_INIT, LET_VAR, LOOP_EACH, LOOP_NEXT, MEMBER_PUBLIC,
    MEMBER_STATIC, ROLE_MASK, ROLE_METHOD,
};
use crate::interp::Val;
use crate::interp::compile_time::{self as ct, Callouts, Construction, Session};
use crate::library::{Level, Message};
use crate::scope::SymbolId;
use crate::settle::{Settle, Settled, SettledModule};
use crate::types::TypeData;
use std::collections::HashSet;
use temper_syntax::ast::Value;
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind, Span};

/// The interface accumulators implement, by name: a library declares
/// it, as it isn't in std, and Kotlin finds it by name too.
const INTERFACE: &str = "ContextualAutoescapingAccumulator";

/// Steps for each use, as Kotlin's ten million or so; past them, the use
/// propagates at run time.
const USE_FUEL: u64 = 10_000_000;

/// Steps for each module's top-level code, run first.
const MODULE_FUEL: u64 = USE_FUEL;

/// An accumulator's block, as tagged strings settle.
struct Use {
    /// The accumulator's symbol, and the block holding its `let` at
    /// `first`, and the `t.accumulated` read at `end`.
    symbol: SymbolId,
    block: NodeId,
    first: usize,
    end: usize,
    /// The accumulator's class, the symbol declaring it, and the interface.
    class: NodeId,
    class_symbol: SymbolId,
    interface: NodeId,
    /// The methods that append a value to escape: `append` and those
    /// marked `@overload("append")`.
    appends: HashSet<NameId>,
    append_safe: NameId,
    /// In a test's body, where problems are hints.
    in_test: bool,
    /// The top-level statement it's in.
    top: NodeId,
}

/// What a fixed part leaves in the collector.
enum Effect {
    Append(String),
    Event(Construction),
}

/// How an append statement changes: its fixed part's effects, then, for
/// a value, the escapers to apply, outermost first, by their classes.
struct Change {
    statement: NodeId,
    effects: Vec<Effect>,
    escapers: Option<Vec<NodeId>>,
}

/// What a use becomes, and the problems found on the way. With `fold`
/// false, the problems are reported and the use is left to run time.
struct Plan {
    changes: Vec<Change>,
    problems: Vec<(Span, Level, String)>,
    fold: bool,
}

impl Settle {
    /// Works out the contexts of each tagged string whose accumulator
    /// fits ([`crate::autoescape`]), rewriting its block to call the
    /// escapers it picks. `modules` are in run order, with their sorted
    /// top levels; those of a module with a block rewritten are sorted
    /// again, for what the new code reads.
    pub(crate) fn optimize_autoescaping(&mut self, modules: &mut Vec<SettledModule>) {
        let uses = self.autoescaped_uses(modules);
        if uses.is_empty() {
            return;
        }
        let module_of = |s: &Settle, node: NodeId| {
            let name = core::class_parts(&s.lib.tree, node).0;
            let owner = s.scopes.symbol(s.scopes.resolved(name)?).owner;
            modules.iter().position(|m| m.scope == owner)
        };
        let needs: Vec<Option<usize>> = (uses.iter())
            .map(|u| Some(module_of(self, u.class)?.max(module_of(self, u.interface)?)))
            .collect();
        let Some(through) = needs.iter().flatten().max().copied() else {
            return;
        };
        let settled = Settled {
            library: std::mem::take(&mut self.lib),
            scopes: std::mem::take(&mut self.scopes),
            modules: std::mem::take(modules),
            steps: 0,
            typing: std::mem::take(&mut self.typing),
        };
        let mut session = Session::new(&settled, through + 1, MODULE_FUEL);
        let mut plans: Vec<Option<Plan>> = Vec::with_capacity(uses.len());
        for (u, need) in uses.iter().zip(&needs) {
            plans.push(match need {
                Some(n) if *n < session.ran() => Analysis::run(&settled, &mut session, u),
                _ => None,
            });
            // A failed call may have left module state half updated.
            if session.failed() {
                session.renew(&settled, through + 1, MODULE_FUEL);
            }
        }
        drop(session);
        self.lib = settled.library;
        self.scopes = settled.scopes;
        *modules = settled.modules;
        self.typing = settled.typing;
        let mut changed = Vec::new();
        for (u, plan) in uses.iter().zip(plans) {
            if plan.is_some_and(|plan| self.apply_plan(u, plan)) {
                changed.extend(modules.iter().position(|m| m.top_levels.contains(&u.top)));
            }
        }
        changed.sort_unstable();
        changed.dedup();
        for m in changed {
            let module = &mut modules[m];
            let files = &self.lib.modules[module.id.0 as usize].files;
            let roots: Vec<NodeId> = (files.iter())
                .map(|f| self.lib.files[f.0 as usize].root)
                .collect();
            module.top_levels = crate::order::sort_top_levels(
                &mut self.lib.tree,
                &self.scopes,
                module.scope,
                &roots,
            );
        }
    }

    /// The blocks `do { let t = new C(); …; t.accumulated }` whose `C`
    /// implements [`INTERFACE`].
    fn autoescaped_uses(&self, modules: &[SettledModule]) -> Vec<Use> {
        let names = &self.lib.names;
        let (Some(accumulated), Some(append), Some(append_safe)) = (
            names.find("accumulated"),
            names.find("append"),
            names.find("appendSafe"),
        ) else {
            return Vec::new();
        };
        let tree = &self.lib.tree;
        let mut uses = Vec::new();
        for i in 0..tree.len() as u32 {
            let read = NodeId(i);
            if tree.kind(read) != NodeKind::Call {
                continue;
            }
            let &[dot, object, key] = tree.children(read) else {
                continue;
            };
            if self.lib.symbol(key) != Some(accumulated)
                || tree.kind(object) != NodeKind::RightName
                || self.scopes.resolved_builtin(dot) != Some(Builtin::Function(Function::Dot))
            {
                continue;
            }
            if let Some(u) = self.autoescaped_use(read, object, append, append_safe, modules) {
                uses.push(u);
            }
        }
        uses
    }

    /// The use whose `t.accumulated` is `read`, if it fits.
    fn autoescaped_use(
        &self,
        read: NodeId,
        object: NodeId,
        append: NameId,
        append_safe: NameId,
        modules: &[SettledModule],
    ) -> Option<Use> {
        let tree = &self.lib.tree;
        let symbol = self.scopes.resolved(object)?;
        let decl = self.scopes.symbol(symbol).decl?;
        let data = tree.data(decl);
        if tree.kind(decl) != NodeKind::Let
            || data & (LET_VAR | ROLE_MASK) != 0
            || data & LET_INIT == 0
            || self.scopes.symbol(symbol).mutable
        {
            return None;
        }
        // `let t = new C()`, in the block that ends with `t.accumulated`.
        let init = core::let_parts(tree, decl).2?;
        let made = tree.children(init);
        if tree.kind(init) != NodeKind::Call
            || made.len() != 2
            || self.scopes.resolved_builtin(made[0]) != Some(Builtin::Function(Function::New))
        {
            return None;
        }
        let block = self.typing.parent(decl)?;
        if tree.kind(block) != NodeKind::Block || self.typing.parent(read) != Some(block) {
            return None;
        }
        let statements = tree.children(block);
        let first = statements.iter().position(|&s| s == decl)?;
        let end = statements.iter().position(|&s| s == read)?;
        if end <= first {
            return None;
        }
        // A concrete class implementing the interface.
        let t = *self.typing.decls.get(&decl)?;
        let types = &self.typing.types;
        let TypeData::Class(class_type) = *types.get(types.base(t)) else {
            return None;
        };
        let class = self.type_decl(class_type)?;
        if tree.kind(class) != NodeKind::Class || tree.data(class) & CLASS_INTERFACE != 0 {
            return None;
        }
        let ancestors = self.ancestors(class);
        let interface = ancestors.iter().copied().find(|&a| {
            tree.data(a) & CLASS_INTERFACE != 0
                && (self.lib.name(core::class_parts(tree, a).0))
                    .is_some_and(|n| self.lib.names.get(n) == INTERFACE)
        })?;
        let class_symbol = self.scopes.resolved(core::class_parts(tree, class).0)?;
        let mut appends = HashSet::from([append]);
        for c in std::iter::once(class).chain(ancestors) {
            for &m in core::class_parts(tree, c).2 {
                let data = tree.data(m);
                let marked = self
                    .overloads
                    .get(&m)
                    .is_some_and(|ns| ns.contains(&append));
                if marked && data & ROLE_MASK == ROLE_METHOD && data & MEMBER_STATIC == 0 {
                    appends.extend(self.lib.name(core::let_parts(tree, m).0));
                }
            }
        }
        // The top-level statement it's in, a test's?
        let mut top = decl;
        while let Some(parent) = self.typing.parent(top) {
            if self.typing.parent(parent).is_none() {
                break;
            }
            top = parent;
        }
        let in_test = modules.iter().any(|m| m.test_cases.contains(&top));
        Some(Use {
            symbol,
            block,
            first,
            end,
            class,
            class_symbol,
            interface,
            appends,
            append_safe,
            in_test,
            top,
        })
    }

    /// Reports a plan's problems; then, if it folds, rewrites the use's
    /// block as it says and types what's new. If the new code doesn't
    /// type completely, as where an escaper has no `apply` for a value,
    /// or an escaper's `apply` takes another type than the append it
    /// stands for, the rewrite is undone, leaving the block to propagate
    /// at run time. True if it stays.
    fn apply_plan(&mut self, u: &Use, plan: Plan) -> bool {
        let mut problems = plan.problems;
        problems.sort_by(|a, b| (a.0.start, a.0.end, &a.2).cmp(&(b.0.start, b.0.end, &b.2)));
        problems.dedup();
        for (span, level, text) in problems {
            self.lib.report(level, Message::ContentTag(text), span);
        }
        if !plan.fold {
            return false;
        }
        let first_new = self.lib.tree.len() as u32;
        let diagnostics = self.lib.diagnostics.len();
        let statements = self.lib.tree.children(u.block).to_vec();
        let (decl, read) = (statements[u.first], statements[u.end]);
        // What changes, to undo it.
        let mut saved = vec![(u.block, statements.clone())];
        let parents =
            (plan.changes.iter()).map(|c| self.typing.parent(c.statement).unwrap_or(u.block));
        for node in std::iter::once(decl).chain(parents.collect::<Vec<_>>()) {
            if !saved.iter().any(|(n, _)| *n == node) {
                saved.push((node, self.lib.tree.children(node).to_vec()));
            }
        }
        let decl_type = self.typing.decls.get(&decl).copied();
        // `let collector = C.newCollector()`, typed as the collector, as
        // Kotlin names it.
        let span = self.lib.tree.span(decl);
        let init = self.static_call(u, "newCollector", Vec::new(), span, decl);
        let name = self.lib.tree.children(decl)[0];
        let old_name = self.scopes.symbol(u.symbol).name;
        let collector_name = self.lib.names.temporary("collector");
        let new_name = self
            .lib
            .add_name(NodeKind::LeftName, collector_name, span, name);
        self.scopes.resolve(new_name, u.symbol);
        self.scopes.rename(u.symbol, collector_name);
        self.lib.tree.set_children(decl, &[new_name, init]);
        let mut complete = self.type_new_nodes(first_new, &[decl], &[decl], &[]);
        let collector = self.typing.decls.get(&decl).copied();
        let append_safe = self.collector_appends_safe(collector);
        // Each append, as its effects and an escaped append; for each, the
        // append method it was and the innermost escaper's `apply` call.
        let mut containers = Vec::new();
        let mut roots = Vec::new();
        let mut applies = Vec::new();
        for change in plan.changes {
            let span = self.lib.tree.span(change.statement);
            let like = change.statement;
            let mut replacement = Vec::new();
            for effect in &change.effects {
                replacement.push(match effect {
                    Effect::Append(text) => {
                        let text = self.lib.add_value(Value::String(text.clone()), span, like);
                        let method = if append_safe { "appendSafe" } else { "append" };
                        self.method_call(u, method, vec![text], span, like)
                    }
                    Effect::Event(event) => {
                        let event = self.construct(event, span, like);
                        let t = self.accumulator(u, span, like);
                        self.static_call(u, "enact", vec![event, t], span, like)
                    }
                });
            }
            if let Some(escapers) = change.escapers {
                let mut value = self.lib.tree.children(change.statement)[1];
                for (i, &escaper) in escapers.iter().rev().enumerate() {
                    let class = self.class_name(escaper, span, like);
                    let instance = self.dot(class, "instance", span, like);
                    let apply = self.dot(instance, "apply", span, like);
                    value = self.lib.add(NodeKind::Call, span, 0, &[apply, value], like);
                    if i == 0 {
                        applies.push((change.statement, value));
                    }
                }
                replacement.push(self.method_call(u, "append", vec![value], span, like));
            }
            let parent = self.typing.parent(change.statement).unwrap_or(u.block);
            let mut kids = self.lib.tree.children(parent).to_vec();
            if self.lib.tree.kind(parent) == NodeKind::Block {
                if let Some(at) = kids.iter().position(|&k| k == change.statement) {
                    kids.splice(at..=at, replacement.iter().copied());
                }
            } else {
                let block = self.lib.add(NodeKind::Block, span, 0, &replacement, like);
                for k in kids.iter_mut().filter(|k| **k == change.statement) {
                    *k = block;
                }
                roots.push(block);
            }
            self.lib.tree.set_children(parent, &kids);
            containers.push(parent);
            roots.extend(replacement);
        }
        // `C.fromCollector(t)` for `t.accumulated`.
        let span = self.lib.tree.span(read);
        let t = self.accumulator(u, span, read);
        let result = self.static_call(u, "fromCollector", vec![t], span, read);
        let mut kids = self.lib.tree.children(u.block).to_vec();
        kids[u.end] = result;
        self.lib.tree.set_children(u.block, &kids);
        containers.push(u.block);
        roots.push(result);
        complete &= self.type_new_nodes(first_new, &containers, &[], &roots);
        // The fold takes `t.appendX(x)` to be `collector.append(e.applyX(x))`,
        // as the library's conventions say; check that the escaper's
        // `apply` takes what the append did.
        complete &= applies
            .iter()
            .all(|&(append, apply)| self.same_first_param(append, apply));
        if !complete {
            // Undo: the old children, parents, name and type come back.
            // Typing only added entries for new nodes, which are detached
            // and emptied, so no later pass sees them.
            for (node, kids) in &saved {
                self.lib.tree.set_children(*node, kids);
            }
            for (node, _) in &saved {
                self.note_parents_under(*node);
            }
            for i in first_new..self.lib.tree.len() as u32 {
                self.lib.tree.replace(NodeId(i), NodeKind::Block, 0, &[]);
            }
            self.scopes.rename(u.symbol, old_name);
            match decl_type {
                Some(t) => self.typing.decls.insert(decl, t),
                None => self.typing.decls.remove(&decl),
            };
            self.lib.diagnostics.truncate(diagnostics);
            return false;
        }
        true
    }

    /// Whether two calls resolve to methods whose first parameters have
    /// one type.
    fn same_first_param(&mut self, a: NodeId, b: NodeId) -> bool {
        let mut param = |call: NodeId| {
            let Some(crate::typing::Resolved::Decl(m)) = self.typing.resolved(call).cloned() else {
                return None;
            };
            let t = *self.typing.decls.get(&m)?;
            let types = &mut self.typing.types;
            let f = types.as_fn(t)?;
            match types.get(f) {
                TypeData::Fn { params, .. } => params.first().map(|p| p.ty),
                _ => None,
            }
        };
        matches!((param(a), param(b)), (Some(x), Some(y)) if x == y)
    }

    /// Whether the collector takes fixed parts by a public `appendSafe`
    /// method, as Kotlin checks, else by `append`.
    fn collector_appends_safe(&self, collector: Option<crate::types::TypeId>) -> bool {
        let types = &self.typing.types;
        let Some(&TypeData::Class(symbol)) = collector.map(|t| types.get(types.base(t))) else {
            return false;
        };
        let Some(class) = self.type_decl(symbol) else {
            return false;
        };
        let tree = &self.lib.tree;
        let classes = std::iter::once(class).chain(self.ancestors(class));
        classes
            .flat_map(|c| core::class_parts(tree, c).2.to_vec())
            .any(|m| {
                let data = tree.data(m);
                data & ROLE_MASK == ROLE_METHOD
                    && data & (MEMBER_STATIC | MEMBER_PUBLIC) == MEMBER_PUBLIC
                    && (self.lib.name(core::let_parts(tree, m).0))
                        .is_some_and(|n| self.lib.names.get(n) == "appendSafe")
            })
    }

    /// `object.name`.
    fn dot(&mut self, object: NodeId, name: &str, span: Span, like: NodeId) -> NodeId {
        let dot = self.scopes.hidden(Builtin::Function(Function::Dot));
        let dot = self.builtin_name(dot, span, like);
        let key = Value::Symbol(self.lib.names.intern(name));
        let key = self.lib.add_value(key, span, like);
        self.lib
            .add(NodeKind::Call, span, 0, &[dot, object, key], like)
    }

    /// A read of the accumulator, now the collector.
    fn accumulator(&mut self, u: &Use, span: Span, like: NodeId) -> NodeId {
        self.builtin_name(u.symbol, span, like)
    }

    /// A class's name, by the symbol declaring it.
    fn class_name(&mut self, class: NodeId, span: Span, like: NodeId) -> NodeId {
        let name = core::class_parts(&self.lib.tree, class).0;
        let symbol = self.scopes.resolved(name).expect("a settled class's name");
        self.builtin_name(symbol, span, like)
    }

    /// `C.name(args)`, for the accumulator's class `C`.
    fn static_call(
        &mut self,
        u: &Use,
        name: &str,
        args: Vec<NodeId>,
        span: Span,
        like: NodeId,
    ) -> NodeId {
        let class = self.builtin_name(u.class_symbol, span, like);
        let callee = self.dot(class, name, span, like);
        let mut children = vec![callee];
        children.extend(args);
        self.lib.add(NodeKind::Call, span, 0, &children, like)
    }

    /// `t.name(args)`, for the accumulator `t`.
    fn method_call(
        &mut self,
        u: &Use,
        name: &str,
        args: Vec<NodeId>,
        span: Span,
        like: NodeId,
    ) -> NodeId {
        let t = self.accumulator(u, span, like);
        let callee = self.dot(t, name, span, like);
        let mut children = vec![callee];
        children.extend(args);
        self.lib.add(NodeKind::Call, span, 0, &children, like)
    }

    /// Code that makes a value again.
    fn construct(&mut self, c: &Construction, span: Span, like: NodeId) -> NodeId {
        let value = |s: &mut Settle, v| s.lib.add_value(v, span, like);
        match c {
            Construction::Null => value(self, Value::Null),
            Construction::Bool(b) => value(self, Value::Bool(*b)),
            Construction::Int(n) => value(self, Value::Int32(*n)),
            Construction::Int64(n) => value(self, Value::Int64(*n)),
            Construction::Float(f) => value(self, Value::Float64(*f)),
            Construction::Str(text) => value(self, Value::String(text.clone())),
            Construction::StringBegin => {
                let string = Builtin::named("String").expect("String is a builtin type");
                let string = self.scopes.hidden(string);
                let string = self.builtin_name(string, span, like);
                self.dot(string, "begin", span, like)
            }
            Construction::List(items) => {
                let list = self.scopes.hidden(Builtin::Function(Function::List));
                let mut children = vec![self.builtin_name(list, span, like)];
                for item in items {
                    children.push(self.construct(item, span, like));
                }
                self.lib.add(NodeKind::Call, span, 0, &children, like)
            }
            Construction::New(class, args) => {
                let new = self.scopes.hidden(Builtin::Function(Function::New));
                let mut children = vec![
                    self.builtin_name(new, span, like),
                    self.class_name(*class, span, like),
                ];
                for arg in args {
                    children.push(self.construct(arg, span, like));
                }
                self.lib.add(NodeKind::Call, span, 0, &children, like)
            }
        }
    }
}

/// Names a use's analysis looks members up by.
struct Names {
    state_after: NameId,
    effects: NameId,
    text: NameId,
    event: NameId,
    escaper_for: NameId,
    merge_states: NameId,
    check_end_state: NameId,
    first: Option<NameId>,
    second: Option<NameId>,
    instance: NameId,
}

/// Working out one use's contexts, with the interpreter.
struct Analysis<'a> {
    s: &'a Settled,
    ev: &'a mut Session,
    u: &'a Use,
    names: Names,
    class: Val,
    propagate_over: Val,
    propagator: Val,
    same_state: Val,
    picker: Val,
    callout: Val,
    callouts: Callouts,
    /// The interface's module's effect classes.
    append_effect: NodeId,
    event_effect: NodeId,
    /// `foldAnalyses`, if the class has it and `exportAnalyses`, what it
    /// gave last, and whether it ever failed.
    fold: Option<Val>,
    analyses: Val,
    fold_failed: bool,
    /// The effects since the last branch or join, for `foldAnalyses`.
    segment: Vec<Val>,
    segment_span: Option<Span>,
    changes: Vec<Change>,
    problems: Vec<(Span, Level, String)>,
    /// The accumulator's reads found in appends.
    reads: usize,
    /// Whether the library reported an error, so the use is left to run
    /// time.
    error: bool,
    /// Whether the walk stopped where compile-time contexts can't be
    /// trusted, reporting what it found so far but folding nothing.
    unfoldable: bool,
    /// The states `break` and `continue` carry to targets inside the
    /// region, by target and whether it's a `continue`, until the walk
    /// reaches it. A `Loop` is both jumps' target.
    exits: Vec<(Jump, Val)>,
}

/// A jump's target, and whether it's a `continue`.
type Jump = (NodeId, bool);

/// Where a statement leaves the walk: the state it completes with, or
/// `None` where it never completes normally, as after `return`.
type After = Option<Val>;

impl<'a> Analysis<'a> {
    /// A use's plan, if everything fits.
    fn run(s: &'a Settled, ev: &'a mut Session, u: &'a Use) -> Option<Plan> {
        ev.set_fuel(USE_FUEL);
        let find = |text: &str| s.library.names.find(text);
        let names = Names {
            state_after: find("stateAfter")?,
            effects: find("effects")?,
            text: find("text")?,
            event: find("event")?,
            escaper_for: find("escaperFor")?,
            merge_states: find("mergeStates")?,
            check_end_state: find("checkEndState")?,
            first: find("first"),
            second: find("second"),
            instance: find("instance")?,
        };
        let tree = &s.library.tree;
        // What the interface's module declares.
        let owner = s
            .scopes
            .symbol(s.scopes.resolved(core::class_parts(tree, u.interface).0)?);
        let declared = |text: &str| {
            let name = find(text)?;
            let names = &s.scopes.get(owner.owner).names;
            names.iter().find(|&&(n, _)| n == name).map(|&(_, b)| b)
        };
        let propagate_over = ev.global(s, declared("propagateOver")?)?;
        let class_decl = |text: &str| s.scopes.symbol(declared(text)?).decl;
        let append_effect = class_decl("AppendParseEffect")?;
        let event_effect = class_decl("EventParseEffect")?;
        let class = ev.global(s, u.class_symbol)?;
        let mut statics =
            |text: &str| -> Option<Val> { ev.call_member(s, &class, find(text)?, Vec::new()).ok() };
        let initial = statics("initialState")?;
        let propagator = statics("propagator")?;
        let same_state = statics("sameStatePredicate")?;
        let picker = statics("picker")?;
        let static_value = |ev: &mut Session, text: &str| ev.get_if_any(s, &class, find(text)?);
        let fold = match (
            static_value(ev, "foldAnalyses"),
            static_value(ev, "exportAnalyses"),
        ) {
            (Some(fold), Some(_)) => Some(fold),
            _ => None,
        };
        let (callout, callouts) = ct::callout();
        let mut a = Analysis {
            s,
            ev,
            u,
            names,
            class,
            propagate_over,
            propagator,
            same_state,
            picker,
            callout,
            callouts,
            append_effect,
            event_effect,
            fold,
            analyses: Val::Null,
            fold_failed: false,
            segment: Vec::new(),
            segment_span: None,
            changes: Vec::new(),
            problems: Vec::new(),
            reads: 0,
            error: false,
            unfoldable: false,
            exits: Vec::new(),
        };
        let statements = tree.children(u.block);
        // Every read of the accumulator is an append's, or the end's.
        if a.append_reads(u.block) + 1 != a.count_reads(u.block) {
            return None;
        }
        let mut state = Some(initial);
        for &statement in &statements[u.first + 1..u.end] {
            let walked = match state {
                Some(before) => a.walk(statement, before),
                // Unreachable: nothing there may append.
                None if a.reads(statement) => None,
                None => Some(None),
            };
            match walked {
                Some(after) => state = after,
                None if a.unfoldable => {
                    return Some(Plan {
                        changes: Vec::new(),
                        problems: a.problems,
                        fold: false,
                    });
                }
                None => return None,
            }
        }
        // Every jump inside the region was followed to its target, and
        // the region's end is reached.
        let state = state.filter(|_| a.exits.is_empty())?;
        // Every read of the accumulator is an append's, or the end's.
        if a.reads + 1 != a.count_reads(u.block) {
            return None;
        }
        let read = statements[u.end];
        let span = tree.span(read);
        a.flush();
        let check = a.names.check_end_state;
        let callout = a.callout.clone();
        a.ev.call_member(s, &a.class, check, vec![state, callout])
            .ok()?;
        a.drain(span);
        if a.fold_failed {
            let class = s.library.name(core::class_parts(tree, u.class).0)?;
            let text = format!("{}.foldAnalyses failed", s.library.names.get(class));
            let at = Span {
                start: span.start,
                end: span.start,
            };
            a.problems.push((at, Level::Error, text));
        }
        Some(Plan {
            changes: a.changes,
            problems: a.problems,
            fold: !a.error,
        })
    }

    /// How many appends to the accumulator are under a node.
    fn append_reads(&self, node: NodeId) -> usize {
        let (s, tree) = (self.s, &self.s.library.tree);
        let mut count = 0;
        let mut stack = vec![node];
        while let Some(n) = stack.pop() {
            if let (NodeKind::Call, &[callee, _]) = (tree.kind(n), tree.children(n)) {
                if let (NodeKind::Call, &[_, object, key]) =
                    (tree.kind(callee), tree.children(callee))
                {
                    let method = s.library.symbol(key);
                    let append = method
                        .is_some_and(|m| m == self.u.append_safe || self.u.appends.contains(&m));
                    if append
                        && tree.kind(object) == NodeKind::RightName
                        && s.scopes.resolved(object) == Some(self.u.symbol)
                    {
                        count += 1;
                    }
                }
            }
            stack.extend(tree.children(n));
        }
        count
    }

    /// Whether a node reads the accumulator.
    fn reads(&self, node: NodeId) -> bool {
        self.count_reads(node) > 0
    }

    fn count_reads(&self, node: NodeId) -> usize {
        let tree = &self.s.library.tree;
        let mut count = 0;
        let mut stack = vec![node];
        while let Some(n) = stack.pop() {
            if tree.kind(n) == NodeKind::RightName
                && self.s.scopes.resolved(n) == Some(self.u.symbol)
            {
                count += 1;
            }
            stack.extend(tree.children(n));
        }
        count
    }

    /// Whether `node` is `ancestor` or under it.
    fn within(&self, node: NodeId, ancestor: NodeId) -> bool {
        let mut at = Some(node);
        while let Some(n) = at {
            if n == ancestor {
                return true;
            }
            at = self.s.typing.parent(n);
        }
        false
    }

    /// Notes the state a jump carries to `target`, if that's inside the
    /// region, to be joined where the walk reaches it. A jump to a
    /// target outside the region (as `return`, or `break` out of a loop
    /// around the tagged string) leaves the region on a path that never
    /// reads the accumulator again, since every read is an append's or
    /// the end's and nothing else holds the accumulator; so its state
    /// reaches no escaper, and isn't kept.
    fn exit(&mut self, jump: Jump, state: &Val) {
        if jump.0 != self.u.block && self.within(jump.0, self.u.block) {
            self.exits.push((jump, ct::deep_copy(state)));
        }
    }

    /// The states jumps carried to a target.
    fn take_exits(&mut self, jump: Jump) -> Vec<Val> {
        let (taken, kept) = std::mem::take(&mut self.exits)
            .into_iter()
            .partition(|(j, _)| *j == jump);
        self.exits = kept;
        taken.into_iter().map(|(_, state)| state).collect()
    }

    /// A statement that doesn't read the accumulator, so leaves the
    /// state as it is: each `break` and `continue` in it to a target
    /// outside it carries that state there ([`Analysis::exit`]), as do
    /// those in `for … of` bodies, whose jumps leave the function; other
    /// functions' can't reach out. It completes with the state, unless
    /// it's a jump, `return`, `bubble()`, `panic()`, or a call whose result
    /// is `Never<T>` itself, which never complete
    /// ([`crate::typing::never_returns`]). Anything else is taken to
    /// complete, which can only add a state to the joins after it.
    fn pass(&mut self, node: NodeId, state: Val) -> After {
        let (s, tree) = (self.s, &self.s.library.tree);
        let mut stack = vec![node];
        while let Some(n) = stack.pop() {
            match tree.kind(n) {
                NodeKind::Fn if tree.data(n) & FN_LOOP_BODY == 0 => continue,
                NodeKind::Break | NodeKind::Continue => {
                    let target = NodeId(tree.data(n));
                    if !self.within(target, node) {
                        let jump = (target, tree.kind(n) == NodeKind::Continue);
                        self.exit(jump, &state);
                    }
                }
                _ => {}
            }
            stack.extend(tree.children(n));
        }
        let panics = crate::typing::never_returns(tree, &s.scopes, &s.typing, node);
        let never = matches!(
            tree.kind(node),
            NodeKind::Return | NodeKind::Break | NodeKind::Continue | NodeKind::Fail
        );
        (!never && !panics).then_some(state)
    }

    /// Where the region's paths join, the state after: with none, the
    /// join is never reached; with one, it's that state. With more, they
    /// fold only if `sameStatePredicate` holds for each and the first, as
    /// a library's merge may keep one path's state for all, as one may
    /// where only nested languages' states differ; `mergeStates` still
    /// runs, for what it reports at `at`. Otherwise the use isn't folded.
    fn join(&mut self, states: Vec<Val>, at: Span) -> Option<After> {
        let mut states = states.into_iter();
        let Some(first) = states.next() else {
            return Some(None);
        };
        let mut merged = first.clone();
        let mut same = true;
        for state in states {
            same &= self.same(&first, &state)?;
            let merge = self.names.merge_states;
            let args = vec![merged, state, self.callout.clone()];
            merged = (self.ev.call_member(self.s, &self.class, merge, args)).ok()?;
        }
        self.drain(at);
        if !same {
            self.unfoldable = true;
            return None;
        }
        Some(Some(merged))
    }

    /// Whether `sameStatePredicate` holds for two states.
    fn same(&mut self, a: &Val, b: &Val) -> Option<bool> {
        let args = vec![a.clone(), b.clone()];
        let same = self.ev.call(self.s, self.same_state.clone(), args).ok()?;
        Some(matches!(same, Val::Bool(true)))
    }

    /// A node's end, where diagnostics about its joins go.
    fn end_of(&self, node: NodeId) -> Span {
        let end = self.s.library.tree.span(node).end;
        Span { start: end, end }
    }

    /// The state after a statement, from the state before it; `None` to
    /// leave the use to run time.
    fn walk(&mut self, node: NodeId, state: Val) -> Option<After> {
        if !self.reads(node) {
            return Some(self.pass(node, state));
        }
        let tree = &self.s.library.tree;
        match (tree.kind(node), tree.children(node)) {
            (NodeKind::Block, children) => {
                let mut after = Some(state);
                for &child in children {
                    after = match after {
                        Some(state) => self.walk(child, state)?,
                        // Unreachable: nothing there may append.
                        None if self.reads(child) => return None,
                        None => None,
                    };
                }
                // A labeled block, where `break` joins what falls through.
                let mut states: Vec<Val> = after.into_iter().collect();
                let breaks = self.take_exits((node, false));
                if breaks.is_empty() {
                    return Some(states.pop());
                }
                self.flush();
                states.extend(breaks);
                self.join(states, self.end_of(node))
            }
            (NodeKind::If, &[condition, then, otherwise]) => {
                if self.reads(condition) {
                    return None;
                }
                let state = self.pass(condition, state)?;
                self.flush();
                let a = self.walk(then, ct::deep_copy(&state))?;
                self.flush();
                let b = self.walk(otherwise, ct::deep_copy(&state))?;
                self.flush();
                // A branch that never ends, as by `return` or `break`,
                // brings no state to the join.
                self.join(a.into_iter().chain(b).collect(), self.end_of(node))
            }
            (NodeKind::Loop, _) => self.looping(node, state),
            (NodeKind::Call, &[callee, arg]) => self.append(node, callee, arg, state).map(Some),
            _ => None,
        }
    }

    /// A loop, walked once from the state before it, on a copy. If every
    /// state that comes back to its start (from the body's end, each
    /// `continue`, and then the part run after each pass) is the same as
    /// that state by `sameStatePredicate`, each pass sees the contexts
    /// the first did, so the body folds once; and the states its `break`s
    /// carry out join as an `if`'s branches do. The state before the loop
    /// is among them where the loop may end before a pass, as a `while`'s
    /// check. Otherwise it's left to run time: no unrolling.
    ///
    /// `for … of` is a loop too, `Loop({ xs.forEach(fn (x) { body });
    /// break })`, when `forEach` is core's, on a `List` or `String`,
    /// which calls the body for each item in order, before it returns:
    /// `continue` ends the body's function, and the state after the
    /// last pass, or before any, leaves the loop.
    fn looping(&mut self, node: NodeId, state: Val) -> Option<After> {
        let (s, tree) = (self.s, &self.s.library.tree);
        let data = tree.data(node);
        let (body, next, head) = if data & LOOP_EACH != 0 {
            let &[block] = tree.children(node) else {
                return None;
            };
            let &[call, done] = tree.children(block) else {
                return None;
            };
            let &[callee, f] = tree.children(call) else {
                return None;
            };
            let core_each = matches!(
                s.typing.resolved(call),
                Some(crate::typing::Resolved::Builtin(key))
                    if key == "List.forEach" || key == "String.forEach"
            );
            let body = *tree.children(f).last()?;
            let fn_parts = &tree.children(f)[..tree.children(f).len() - 1];
            if !core_each
                || tree.kind(done) != NodeKind::Break
                || tree.data(done) != node.0
                || tree.kind(f) != NodeKind::Fn
                || tree.data(f) & FN_LOOP_BODY == 0
                || self.reads(callee)
                || fn_parts.iter().any(|&p| self.reads(p))
                || self.pass(callee, state.clone()).is_none()
            {
                return None;
            }
            (body, None, f)
        } else {
            match *tree.children(node) {
                [body] if data & LOOP_NEXT == 0 => (body, None, node),
                [body, next] if data & LOOP_NEXT != 0 => (body, Some(next), node),
                _ => return None,
            }
        };
        self.flush();
        let end = self.walk(body, ct::deep_copy(&state))?;
        // What comes back to the start: the body's end and `continue`s.
        let mut back: Vec<Val> = end.into_iter().collect();
        back.extend(self.take_exits((head, true)));
        for b in &back {
            if !self.same(&state, b)? {
                return None;
            }
        }
        if let Some(next) = next {
            if back.is_empty() {
                // Never reached.
                if self.reads(next) {
                    return None;
                }
            } else if let Some(after) = self.walk(next, ct::deep_copy(&state))? {
                if !self.same(&state, &after)? {
                    return None;
                }
            }
        }
        self.flush();
        let mut leaving = self.take_exits((node, false));
        if data & LOOP_EACH != 0 {
            leaving.push(ct::deep_copy(&state));
        }
        self.join(leaving, self.end_of(node))
    }

    /// `t.appendSafe("…")` or `t.append(x)`: the state after it.
    fn append(&mut self, node: NodeId, callee: NodeId, arg: NodeId, state: Val) -> Option<Val> {
        let s = self.s;
        let tree = &s.library.tree;
        let &[dot, object, key] = tree.children(callee) else {
            return None;
        };
        let method = s.library.symbol(key)?;
        if tree.kind(callee) != NodeKind::Call
            || s.scopes.resolved_builtin(dot) != Some(Builtin::Function(Function::Dot))
            || tree.kind(object) != NodeKind::RightName
            || s.scopes.resolved(object) != Some(self.u.symbol)
            || self.reads(arg)
        {
            return None;
        }
        // The value is worked out before the append runs, so a jump in
        // it, as `${if (c) { break } else { x }}`, carries the state from
        // before the append to its target. A value that never completes
        // leaves the use to run time.
        self.pass(arg, state.clone())?;
        let safe = method == self.u.append_safe;
        if !safe && !self.u.appends.contains(&method) {
            return None;
        }
        let (known, at) = if safe {
            let Some(Value::String(text)) = s.library.value(arg) else {
                return None;
            };
            (Val::Str(text.as_str().into()), tree.span(arg))
        } else {
            let at = tree.span(arg).start;
            (Val::Null, Span { start: at, end: at })
        };
        self.drain(Span {
            start: tree.span(node).start,
            end: tree.span(node).start,
        });
        let args = vec![
            self.propagator.clone(),
            state,
            known,
            self.same_state.clone(),
            self.callout.clone(),
        ];
        let after = self.ev.call(s, self.propagate_over.clone(), args).ok()?;
        self.drain(at);
        let Val::List(ref effect_values) = self.ev.get(s, &after, self.names.effects).ok()? else {
            return None;
        };
        let mut effects = Vec::new();
        for value in effect_values.iter() {
            let class = ct::class_node(value)?;
            if class == self.append_effect {
                match ct::field(s, value, self.names.text)? {
                    Val::Str(ref text) => effects.push(Effect::Append(text.to_string())),
                    _ => return None,
                }
            } else if class == self.event_effect {
                let event = ct::field(s, value, self.names.event)?;
                effects.push(Effect::Event(ct::construction(s, &event)?));
            } else {
                return None;
            }
        }
        self.segment.extend(effect_values.iter().cloned());
        self.segment_span = Some(tree.span(node));
        let state = self.ev.get(s, &after, self.names.state_after).ok()?;
        let escapers = if safe {
            None
        } else {
            let args = vec![state.clone(), self.callout.clone()];
            let pick = self.names.escaper_for;
            let escaper = self.ev.call_member(s, &self.picker, pick, args).ok()?;
            self.drain(tree.span(node));
            Some(self.unravel(&escaper, 0)?)
        };
        self.changes.push(Change {
            statement: node,
            effects,
            escapers,
        });
        self.reads += 1;
        Some(state)
    }

    /// An escaper's classes, outermost first: a composed one's, with
    /// `first` and `second`, are `second`'s, then `first`'s, taking it to
    /// apply as `second.apply(first.apply(x))`, as the library's
    /// conventions say. Each other must be alike its class's static
    /// `instance` ([`ct::alike`]: that very object, or one of no other
    /// settings), since that's what the folded code calls: an escaper made
    /// with its own settings isn't folded.
    fn unravel(&mut self, escaper: &Val, depth: u32) -> Option<Vec<NodeId>> {
        let s = self.s;
        let parts = match (self.names.first, self.names.second) {
            (Some(first), Some(second)) => {
                ct::field(s, escaper, first).zip(ct::field(s, escaper, second))
            }
            _ => None,
        };
        match parts {
            Some((first, second)) if depth < 64 => {
                let mut escapers = self.unravel(&second, depth + 1)?;
                escapers.extend(self.unravel(&first, depth + 1)?);
                Some(escapers)
            }
            Some(_) => None,
            None => {
                let class = ct::class_node(escaper)?;
                let tree = &s.library.tree;
                let has_instance = core::class_parts(tree, class).2.iter().any(|&m| {
                    tree.data(m) & MEMBER_STATIC != 0
                        && s.library.name(core::let_parts(tree, m).0) == Some(self.names.instance)
                });
                if !has_instance {
                    return None;
                }
                let statics = ct::class_value(escaper)?;
                let instance = self.ev.get(s, &statics, self.names.instance).ok()?;
                ct::alike(&instance, escaper).then(|| vec![class])
            }
        }
    }

    /// Folds the effects since the last branch or join into the
    /// analyses, as Kotlin folds each basic block's.
    fn flush(&mut self) {
        let Some(fold) = self.fold.clone() else {
            self.segment.clear();
            return;
        };
        if self.segment.is_empty() {
            return;
        }
        let effects = Val::List(std::mem::take(&mut self.segment).into());
        let args = vec![self.analyses.clone(), effects, self.callout.clone()];
        match self.ev.call(self.s, fold, args) {
            Ok(analyses) => self.analyses = analyses,
            Err(_) => self.fail_fold(),
        }
        if let Some(span) = self.segment_span {
            self.drain(span);
        }
    }

    fn fail_fold(&mut self) {
        self.fold_failed = true;
        self.fold = None;
    }

    /// The callouts since the last, as problems at `span`.
    fn drain(&mut self, span: Span) {
        for (text, is_error) in self.callouts.borrow_mut().drain(..) {
            self.error |= is_error;
            let level = match (self.u.in_test, is_error) {
                (true, _) => Level::Hint,
                (false, true) => Level::Error,
                (false, false) => Level::Warn,
            };
            self.problems.push((span, level, text));
        }
    }
}
