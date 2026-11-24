//! Static types, the first slice of milestone 4 (notes/history/milestone-4.md):
//! declared and inferred types of declarations as facts in the settle
//! loop, and the type errors Kotlin Temper reports for literals, locals,
//! parameters, calls of known functions, conditions and returns.
//!
//! Typing starts once expansion has settled, as a second round of the
//! loop: each declaration whose type comes from its initializer, as `let
//! x = f(1)`, is a work item that publishes [`Fact::Type`] once it knows
//! that type, waiting on the declarations its initializer reads. Declared
//! types, parameters' and functions' signatures need no waiting: they come
//! from annotations. A cycle, as `let a = b; let b = a`, is forced, leaving
//! the type unknown. Then a pass over each file checks what it can. Macros
//! that need types will interleave with this once there are some.
//!
//! An unknown type never causes an error, so what isn't typed yet, such as
//! function literals' types, just goes unchecked. Builtin operators' and
//! `console.log`'s signatures are in [`signatures`], builtin types'
//! members', read from core, in
//! [`core_members`], what may bubble where it can't, in [`bubbles`],
//! classes' and their members' types, in [`members`], named `@fun`
//! interfaces' rules, nominal, in [`fun_interfaces`], run-time type
//! checks' errors, in [`rtti`], generic functions' calls, solved per
//! call, in [`generic_calls`], and the type a value's place wants, which
//! types list literals, in [`targets`], and decides object literals'
//! classes, in [`object_literals`], and which `@overload` method a call
//! means, in [`overloads`], or which `@extension` function, in
//! [`instance_extensions`]. Before
//! inferring, flow facts ([`crate::flow`]) find the uses of locals that
//! null checks and `is` checks narrow, so typing a use of `a: String?`
//! after `if (a != null)` gives `String`, and of `i: I` after `if (i is
//! D)`, `D`.

use crate::builtins::{Builtin, Constant, Function};
use crate::core::{self, ROLE_MASK, ROLE_TYPE_PARAM};
use crate::library::{Level, Message};
use crate::scope::{ScopeId, SymbolId, SymbolKind};
use crate::settle::{Fact, Settle};
use crate::types::{
    ANY_VALUE, BOOLEAN, BUBBLE, FLOAT64, INT32, INT64, NEVER, NULL, Param, STRING, TypeData,
    TypeId, Types, UNKNOWN, VOID,
};
use std::collections::{HashMap, HashSet};
use temper_syntax::ast::Value;
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind, Span};

/// Builtin types whose members core's table has in full, and whose values
/// are never functions.
const FULL_TYPES: [&str; 10] = [
    "Boolean",
    "Float64",
    "Int",
    "Int32",
    "Int64",
    "List",
    "ListBuilder",
    "Map",
    "MapBuilder",
    "String",
];

mod bubbles;
mod connected;
mod core_members;
mod definite;
mod exports;
mod exprs;
mod fields_first;
mod fun_interfaces;
mod generic_calls;
mod initializers;
mod instance_extensions;
mod members;
mod object_literals;
mod overloads;
mod overrides;
mod resolve;
mod rtti;
mod sealed;
mod signatures;
mod targets;

pub(crate) use bubbles::{fold_float, fold_int};
pub use core_members::{
    BuiltinMember, builtin_members, core_declaration, core_member_declarations,
};
pub use exprs::{FileCoverage, coverage};
pub use resolve::Resolved;

/// What typing found: declarations' and expressions' types.
#[derive(Debug, Default)]
pub struct Typing {
    pub types: Types,
    /// Declarations' types, by their `Let`s.
    pub decls: HashMap<NodeId, TypeId>,
    /// Expressions' types, by node id, where worked out, else [`UNSET`].
    exprs: Vec<TypeId>,
    /// Uses of locals that flow facts show aren't null.
    pub(crate) non_null: HashSet<NodeId>,
    /// Uses of locals that flow facts show are of a type, by the type
    /// expression of the `is` check that says so.
    pub(crate) is_type: HashMap<NodeId, NodeId>,
    /// The type each `is` and `as` checks for, by the call, without `?`:
    /// `AppendEffect<E>` for `x is AppendEffect<E>`.
    pub(crate) rtti_targets: HashMap<NodeId, TypeId>,
    /// Calls being solved for a list literal argument's wanted type,
    /// against cycles, as `g([f([])])` ([`targets`]).
    pub(crate) solving_for_lists: Vec<NodeId>,
    /// Declarations that are assigned after they're declared.
    pub(crate) assigned: HashSet<NodeId>,
    /// Builtin types' generic methods' own type parameters, as `O` in
    /// `Listed.map<O>`, by the declaring type and the member's index.
    core_formals: HashMap<(&'static str, usize), Vec<SymbolId>>,
    /// Each node's parent, under the files' roots, else [`NO_PARENT`].
    parents: Vec<NodeId>,
    /// Calls whose trailing block typing places other than last
    /// ([`Settle::placed_params`]), by the call: the position among the
    /// values the call passes (a constructor's class first, without type
    /// arguments) that the block takes, with null passed for skipped
    /// parameters before it, which take their defaults.
    pub trailing_blocks: HashMap<NodeId, usize>,
    /// Temporaries that hold object literals' values, by their `let`s,
    /// with their uses, whose places want what the values' places do.
    literal_temporaries: HashMap<NodeId, NodeId>,
    /// The type parameter of a list literal's signature, `listT` in `<listT
    /// extends AnyValue>(listT, listT) -> List<listT>`, once one needs it.
    list_formal: Option<SymbolId>,
    /// The type aliases being read, against cycles.
    aliases: Vec<NodeId>,
    /// The locals being typed from their initializers, against cycles,
    /// as `c = c + 1` ([`initializers`]).
    initializing: Vec<NodeId>,
    /// What each call, operator and member read resolves to, where
    /// typing knows ([`resolve`]).
    resolved: HashMap<NodeId, Resolved>,
    /// `==` and `!=` uses with nothing to call but identity, with why.
    identity_eqs: HashMap<NodeId, &'static str>,
    /// What each call of a generic function, method, or class's
    /// constructor binds its type parameters to, by the call: the callee's
    /// own, or for a constructor, its class's, unknown where unsolved.
    type_args: HashMap<NodeId, Vec<TypeId>>,
    /// The empty blocks standing for missing `else`s that are never
    /// reached, as `is` checks before them cover a sealed type
    /// ([`sealed`]): `Never`, and a panic in tmpl, as Kotlin's.
    unreachable: HashSet<NodeId>,
    /// Each sealed interface's subtypes, by its symbol, once one's needed.
    sealed_subtypes: Option<HashMap<SymbolId, Vec<SymbolId>>>,
    /// Whether typing's round is done and checking has begun, so what
    /// isn't known by now never will be.
    checking: bool,
    /// What function literals without a declared result give, from their
    /// bodies, by the literal, once worked out ([`generic_calls`]).
    literal_passes: HashMap<NodeId, TypeId>,
    /// What such literals' bodies may fail with, if anything.
    literal_failures: HashMap<NodeId, Option<TypeId>>,
}

const UNSET: TypeId = TypeId(u32::MAX);
const NO_PARENT: NodeId = NodeId(u32::MAX);

impl Typing {
    /// An expression's type, if typing worked it out.
    pub fn expr(&self, node: NodeId) -> Option<TypeId> {
        self.exprs
            .get(node.0 as usize)
            .copied()
            .filter(|&t| t != UNSET)
    }

    /// What a generic call binds its callee's type parameters to (a
    /// constructor's, its class's), if typing solved it.
    pub fn type_args(&self, call: NodeId) -> Option<&[TypeId]> {
        self.type_args.get(&call).map(Vec::as_slice)
    }

    /// What a call, operator or member read resolves to, if typing knows.
    pub fn resolved(&self, node: NodeId) -> Option<&Resolved> {
        self.resolved.get(&node)
    }

    /// Whether an `if`'s empty `else` is never reached ([`sealed`]).
    pub fn is_unreachable(&self, block: NodeId) -> bool {
        self.unreachable.contains(&block)
    }

    /// Why an `==` or `!=` would compare identities, if it would, having
    /// no equality to call (notes/typing.md, "Resolutions").
    pub fn identity_eq(&self, node: NodeId) -> Option<&'static str> {
        self.identity_eqs.get(&node).copied()
    }

    fn set_expr(&mut self, node: NodeId, t: TypeId) {
        let i = node.0 as usize;
        if i >= self.exprs.len() {
            self.exprs.resize(i + 1, UNSET);
        }
        self.exprs[i] = t;
    }

    fn unset_expr(&mut self, node: NodeId) {
        if let Some(t) = self.exprs.get_mut(node.0 as usize) {
            *t = UNSET;
        }
    }

    /// The node a node is a child of, if it's under a file's root.
    pub(crate) fn parent(&self, node: NodeId) -> Option<NodeId> {
        (self.parents.get(node.0 as usize).copied()).filter(|&p| p != NO_PARENT)
    }

    fn set_parent(&mut self, node: NodeId, parent: NodeId) {
        let i = node.0 as usize;
        if i >= self.parents.len() {
            self.parents.resize(i + 1, NO_PARENT);
        }
        self.parents[i] = parent;
    }

    /// Whether nodes' parents are noted, as once typing has begun.
    fn has_parents(&self) -> bool {
        !self.parents.is_empty()
    }
}

/// Where a value is stored, for how a mismatch is reported.
#[derive(Clone, Copy)]
enum Site {
    /// A declaration's initializer.
    Init,
    /// An assignment, `a = v`.
    Assign(NodeId),
    /// `return v`.
    Return(NodeId),
    /// A function body's result.
    Result,
    /// A call's argument.
    Arg,
    /// A field's initializer, reported as a setter's argument, but solved
    /// from the type wanted, as Kotlin Temper does.
    Field,
}

impl Settle {
    /// Queues inferring the type of each declaration that takes its
    /// initializer's, under the files' roots, and notes each node's parent,
    /// for the type its place wants ([`targets`]).
    pub(crate) fn queue_typing(&mut self) {
        self.typing.checking = false;
        self.find_flow_facts();
        self.typing.parents.resize(self.lib.tree.len(), NO_PARENT);
        // A session's later rounds may declare new subtypes.
        self.typing.sealed_subtypes = None;
        let mut stack: Vec<NodeId> = self.round_roots();
        while let Some(node) = stack.pop() {
            for &child in self.lib.tree.children(node) {
                self.typing.set_parent(child, node);
            }
            stack.extend(self.lib.tree.children(node).iter().rev());
            if self.lib.tree.kind(node) != NodeKind::Let
                || !(self.is_value_decl(node) || self.is_field(node))
            {
                continue;
            }
            let (_, ty, init) = core::let_parts(&self.lib.tree, node);
            if ty.is_none() && init.is_some_and(|i| self.lib.tree.kind(i) != NodeKind::Fn) {
                self.queue_type(node);
            }
        }
        self.queue_object_literals();
    }

    /// Whether a `Let` declares a local, parameter or top-level name, not
    /// a member or type parameter.
    fn is_value_decl(&self, node: NodeId) -> bool {
        let name = self.lib.tree.children(node)[0];
        self.scopes.resolved(name).is_some_and(|b| {
            matches!(
                self.scopes.symbol(b).kind,
                SymbolKind::TopLevel
                    | SymbolKind::Local
                    | SymbolKind::Parameter
                    | SymbolKind::Synthetic
            )
        })
    }

    /// Whether a `Let` declares a class's field, static or not, which
    /// may take its initializer's type, as `static srgb = new Space()`.
    fn is_field(&self, node: NodeId) -> bool {
        let name = self.lib.tree.children(node)[0];
        self.lib.tree.data(node) & ROLE_MASK == 0
            && (self.scopes.resolved(name))
                .is_some_and(|b| self.scopes.symbol(b).kind == SymbolKind::Member)
    }

    /// Waits for a member's type, if it's a field's that its initializer
    /// gives ([`Settle::is_field`]), unless `forced`.
    fn wait_for_field(&self, m: NodeId, forced: bool) -> Result<(), Fact> {
        let tree = &self.lib.tree;
        let waits = !forced
            && tree.kind(m) == NodeKind::Let
            && tree.data(m) & ROLE_MASK == 0
            && !self.typing.decls.contains_key(&m)
            && matches!(core::let_parts(tree, m), (_, None, Some(init)) if tree.kind(init) != NodeKind::Fn)
            && self.is_field(m);
        if waits {
            return Err(Fact::Type(m));
        }
        Ok(())
    }

    /// The step that infers a declaration's type from its initializer.
    pub(crate) fn infer_decl(&mut self, decl: NodeId, forced: bool) -> Result<(), Fact> {
        let init = core::let_parts(&self.lib.tree, decl).2;
        let mut t = match init {
            Some(init) => self.expr_type(init, forced)?,
            None => UNKNOWN,
        };
        // Kotlin Temper would type these from later uses.
        if t == NULL || t == NEVER {
            t = UNKNOWN;
        }
        self.typing.decls.insert(decl, t);
        self.publish(Fact::Type(decl));
        Ok(())
    }

    /// A declaration's type: declared, a function's signature, or inferred
    /// from its initializer, waiting for that if it's not known yet, unless
    /// `forced`.
    fn decl_type(&mut self, decl: NodeId, forced: bool) -> Result<TypeId, Fact> {
        if let Some(&t) = self.typing.decls.get(&decl) {
            return Ok(t);
        }
        if self.lib.tree.kind(decl) != NodeKind::Let {
            return Ok(UNKNOWN);
        }
        let (_, ty, init) = core::let_parts(&self.lib.tree, decl);
        let t = match (ty, init) {
            (Some(ty), Some(init)) if self.bare_builtin(ty) => {
                // `let xs: List = …` is the `List` its value is, as
                // `List<Boolean>`, as Kotlin Temper's solver finds it.
                let t = self.eval_type(ty);
                // The bare type meanwhile, against an initializer that
                // reads the declaration.
                self.typing.decls.insert(decl, t);
                let made = self.expr_type(init, forced);
                self.typing.decls.remove(&decl);
                let made = made?;
                match self.typing.types.base(made) == t {
                    true => made,
                    false => t,
                }
            }
            (Some(ty), _) => self.eval_type(ty),
            (None, Some(f)) if self.lib.tree.kind(f) == NodeKind::Fn => {
                self.expr_type(f, forced)?
            }
            (None, Some(_)) if forced => return Ok(UNKNOWN),
            (None, Some(_)) => return Err(Fact::Type(decl)),
            // A function literal's parameter, as `x` in `{ x => x + 1 }`,
            // typed from the literal's place, which a forced step may not
            // know all of; or a local declared without a value, typed
            // from what's assigned to it first ([`initializers`]).
            (None, None) => {
                let t = match self.untyped_param_type(decl, forced)? {
                    Some(t) => t,
                    None => self.initializers_type(decl, forced)?.unwrap_or(UNKNOWN),
                };
                if forced {
                    return Ok(t);
                }
                t
            }
        };
        self.typing.decls.insert(decl, t);
        Ok(t)
    }

    /// An expression's type, waiting for the declarations it reads. Types
    /// worked out in a forced step aren't kept, as they may lack what it
    /// didn't wait for.
    fn expr_type(&mut self, node: NodeId, forced: bool) -> Result<TypeId, Fact> {
        if let Some(t) = self.typing.expr(node) {
            return Ok(t);
        }
        let tree = &self.lib.tree;
        let children = tree.children(node);
        let (first, last) = (children.first().copied(), children.last().copied());
        let before_last = children.len().checked_sub(2).map(|i| children[i]);
        let t = match tree.kind(node) {
            NodeKind::Value => match self.lib.value(node) {
                Some(Value::Int32(_)) => INT32,
                Some(Value::Int64(_)) => INT64,
                Some(Value::Float64(_)) => FLOAT64,
                Some(Value::String(_)) => STRING,
                Some(Value::Bool(_)) => BOOLEAN,
                Some(Value::Null) => NULL,
                Some(Value::Void) => VOID,
                _ => UNKNOWN,
            },
            NodeKind::RightName => self.name_type(node, forced)?,
            // One that jumps away before its end never completes, as `{
            // return -1; }`, whose last statement is a `void`.
            NodeKind::Block if children.iter().any(|&c| self.jumps_from(c, node)) => NEVER,
            NodeKind::Block if last.is_none() && self.unreachable_else(node, forced)? => NEVER,
            NodeKind::Block => match last {
                Some(last) => self.expr_type(last, forced)?,
                None => VOID,
            },
            NodeKind::If | NodeKind::Orelse => match (before_last, last) {
                (Some(a), Some(b)) => {
                    let a = self.expr_type(a, forced)?;
                    let b = self.expr_type(b, forced)?;
                    self.typing.types.join(a, b)
                }
                _ => UNKNOWN,
            },
            NodeKind::Let | NodeKind::Loop => VOID,
            NodeKind::Return | NodeKind::Break | NodeKind::Continue | NodeKind::Fail => NEVER,
            // An assignment's value is what it assigns; one to a member is
            // a setter's call, but passes the value on where it's used, as
            // in `c.p = c.p = v`, not as a block's statement or result.
            NodeKind::Assign => match (first.and_then(|t| self.local_target(t)), last) {
                (Some(_), Some(value)) => self.expr_type(value, forced)?,
                (None, Some(value)) if self.assignment_used(node) => {
                    self.expr_type(value, forced)?
                }
                _ => UNKNOWN,
            },
            NodeKind::Fn => self.literal_type(node, forced)?,
            NodeKind::Call => self.call_type(node, forced)?,
            // `await p`'s value is the `T` of `p`'s `Promise<T>`.
            NodeKind::Await => match first {
                Some(promise) => {
                    let t = self.expr_type(promise, forced)?;
                    match self.typing.types.get(t) {
                        TypeData::Applied { args, .. } if args.len() == 1 => args[0],
                        _ => UNKNOWN,
                    }
                }
                None => UNKNOWN,
            },
            _ => UNKNOWN,
        };
        let t = match self.lib.tree.kind(node) {
            NodeKind::Fn | NodeKind::RightName | NodeKind::Call => {
                self.target_fun(node, t, forced)?
            }
            _ => t,
        };
        if !forced {
            self.typing.set_expr(node, t);
        }
        Ok(t)
    }

    /// [`never_returns`] here, which before typing also knows a call of
    /// a method whose result is `Never<T>` on a parameter or local declared
    /// with its class's type, as `test.fail(why)` on a test's `Test`.
    pub(crate) fn never_returns(&self, node: NodeId) -> bool {
        never_returns(&self.lib.tree, &self.scopes, &self.typing, node)
            || (self.typing.expr(node).is_none() && self.calls_never_method(node))
    }

    /// Whether an untyped call is `object.key(…)`, where `object` names a
    /// parameter or local declared with a class's type, as `test: Test`,
    /// whose own methods named `key` each declare a `Never<T>` result:
    /// what typing the call will find. Methods it inherits aren't looked
    /// for, so a call of one ends its path only once typed.
    fn calls_never_method(&self, node: NodeId) -> bool {
        let tree = &self.lib.tree;
        let Some(&callee) = (tree.kind(node) == NodeKind::Call)
            .then(|| tree.children(node).first())
            .flatten()
        else {
            return false;
        };
        let Some((object, key)) = self.dot_parts(callee) else {
            return false;
        };
        let Some(&Value::Symbol(name)) = self.lib.value(key) else {
            return false;
        };
        let declared = (tree.kind(object) == NodeKind::RightName)
            .then(|| self.scopes.resolved(object))
            .flatten()
            .map(|b| self.scopes.symbol(b))
            .filter(|b| matches!(b.kind, SymbolKind::Parameter | SymbolKind::Local))
            .and_then(|b| b.decl)
            .and_then(|d| core::let_parts(tree, d).1)
            .filter(|&ty| tree.kind(ty) == NodeKind::RightName);
        let Some(class) =
            (declared.and_then(|ty| self.scopes.resolved(ty))).and_then(|b| self.type_decl(b))
        else {
            return false;
        };
        let methods: Vec<NodeId> = (self.members_named(class, name, false).into_iter())
            .filter(|&m| tree.data(m) & ROLE_MASK == core::ROLE_METHOD)
            .collect();
        !methods.is_empty()
            && methods.iter().all(|&m| {
                (core::let_parts(tree, m).2).is_some_and(|f| declares_never(tree, &self.scopes, f))
            })
    }

    /// Whether a statement jumps away from the block it's in: `return`,
    /// `continue`, a failure, or `break`, unless to that block's end.
    fn jumps_from(&self, statement: NodeId, block: NodeId) -> bool {
        let tree = &self.lib.tree;
        match tree.kind(statement) {
            NodeKind::Return | NodeKind::Continue | NodeKind::Fail => true,
            NodeKind::Break => tree.data(statement) != block.0,
            _ => false,
        }
    }

    /// The declaration of a local, parameter or top-level name that an
    /// assignment targets, if that's what it targets.
    fn local_target(&self, target: NodeId) -> Option<NodeId> {
        if self.lib.tree.kind(target) != NodeKind::RightName {
            return None;
        }
        let symbol = self.scopes.symbol(self.scopes.resolved(target)?);
        match symbol.kind {
            SymbolKind::TopLevel | SymbolKind::Local | SymbolKind::Parameter => symbol.decl,
            _ => None,
        }
    }

    fn name_type(&mut self, node: NodeId, forced: bool) -> Result<TypeId, Fact> {
        let Some(b) = self.scopes.resolved(node) else {
            return Ok(UNKNOWN);
        };
        let symbol = *self.scopes.symbol(b);
        Ok(match symbol.kind {
            SymbolKind::Builtin(Builtin::Value(c)) => match c {
                Constant::True | Constant::False => BOOLEAN,
                Constant::Null => NULL,
                Constant::Void => VOID,
                Constant::NaN | Constant::Infinity => FLOAT64,
                Constant::TestServerPort(_) => INT32,
                Constant::Console => UNKNOWN,
            },
            SymbolKind::TopLevel
            | SymbolKind::Local
            | SymbolKind::Parameter
            | SymbolKind::Synthetic => match symbol.decl {
                Some(decl) => {
                    let t = self.decl_type(decl, forced)?;
                    self.narrowed(node, t)
                }
                None => self.this_type(b),
            },
            SymbolKind::Member => {
                if let Some(decl) = symbol.decl {
                    self.wait_for_field(decl, forced)?;
                }
                self.bare_member_type(b)
            }
            _ => UNKNOWN,
        })
    }

    /// The type of a use of a declaration of type `t`, as flow facts
    /// narrow it: not null after a null check, and after `a is T`, a `T`,
    /// as in Kotlin Temper, unless `t` is a subtype of `T` already, which
    /// is more precise, or `T` isn't a subtype of `t`, an error.
    fn narrowed(&mut self, node: NodeId, t: TypeId) -> TypeId {
        if !self.typing.non_null.contains(&node) {
            return t;
        }
        let inner = match self.typing.types.get(t) {
            TypeData::Nullable(inner) => *inner,
            _ => t,
        };
        let Some(&ty) = self.typing.is_type.get(&node) else {
            return inner;
        };
        let target = self.eval_type(ty);
        let types = &mut self.typing.types;
        // A type parameter's value may be of any subtype of its bound.
        let upper = types.upper(inner);
        match (
            types.is_subtype(inner, target),
            types.is_subtype(target, upper),
        ) {
            (Some(true), _) => inner,
            // A check between unrelated types is an error of its own
            // (`rtti`), so uses keep their declared type.
            (_, Some(false)) => inner,
            _ => target,
        }
    }

    /// A call's type: a builtin's, or what a known function or method
    /// returns.
    fn call_type(&mut self, node: NodeId, forced: bool) -> Result<TypeId, Fact> {
        let children = self.lib.tree.children(node).to_vec();
        let Some((&callee, args)) = children.split_first() else {
            return Ok(UNKNOWN);
        };
        if self.literal_undecided(node) {
            return if forced {
                Ok(UNKNOWN)
            } else {
                Err(Fact::ObjectLiteral(node))
            };
        }
        if self.is_list_literal(node) {
            return self.list_type(node, forced);
        }
        self.resolve_overload(node, forced)?;
        if self.resolve_extension(node, forced, false)? {
            return self.call_type(node, forced);
        }
        if self.list_of_parts(node).is_some() {
            return self.list_of_type(node, forced);
        }
        if let Some(mut solved) = self.solve_call(node, forced)? {
            let t = self.solved_type(&mut solved, forced)?;
            let actuals = self.settled_type_args(&solved);
            self.typing.type_args.insert(node, actuals);
            return Ok(t);
        }
        match self.scopes.resolved_builtin(callee) {
            Some(Builtin::Function(f)) => return self.builtin_call_type(f, args, forced),
            Some(_) => return Ok(UNKNOWN),
            None => {}
        }
        if self.is_console_log(callee) {
            return Ok(VOID);
        }
        let callee_type = self.callee_type(callee, forced)?;
        Ok(self.typing.types.call_pass(callee_type))
    }

    fn builtin_call_type(
        &mut self,
        f: Function,
        args: &[NodeId],
        forced: bool,
    ) -> Result<TypeId, Fact> {
        use Function::*;
        Ok(match f {
            Lt | Le | Gt | Ge | Eq | Ne | Not | Is => BOOLEAN,
            Cmp => INT32,
            Cat => STRING,
            Panic => NEVER,
            Ignore => VOID,
            // `doPure { … }` gives its block's result.
            DoPure => match args {
                [f] if self.lib.tree.kind(*f) == NodeKind::Fn => {
                    let t = self.expr_type(*f, forced)?;
                    self.typing.types.call_pass(t)
                }
                _ => UNKNOWN,
            },
            Empty => match crate::builtins::TYPES.iter().position(|&t| t == "Empty") {
                Some(i) => self.typing.types.builtin(i as u8),
                None => UNKNOWN,
            },
            Pow => FLOAT64,
            As if args.len() == 2 => self.eval_type(args[1]),
            New => match self.new_class(args) {
                Some(class) => self.class_type(class),
                None => self.builtin_new_type(args),
            },
            // `char'a'`, a code point, and `raw"a\b"`.
            Char => INT32,
            Raw => STRING,
            Dot if args.len() == 2 => self.dot_type(args[0], args[1], forced)?,
            // `a[i]` calls `a.get(i)`.
            Index if !args.is_empty() => {
                let get = self.index_callee_type(args[0], forced)?;
                self.typing.types.call_pass(get)
            }
            _ => match self.builtin_variant(f, args, forced)? {
                Some((sig, _)) => sig.pass,
                None => self.exact_variant(f, args, forced)?,
            },
        })
    }

    /// A function's type, from its signature, with its own type
    /// parameters unknown, as references and unsolved calls see it.
    fn fn_type(&mut self, node: NodeId) -> TypeId {
        let t = self.fn_signature(node);
        let formals = self.own_formals(node);
        self.typing.types.erase(t, &formals)
    }

    /// A function's signature, with its own type parameters as they're
    /// written. A generator's call makes a generator: `SafeGenerator<Y>`
    /// for `GeneratorResult<Y>`, unless it has optional parameters.
    fn fn_signature(&mut self, node: NodeId) -> TypeId {
        let tree = &self.lib.tree;
        let (params, out_type, _) = core::fn_parts(tree, node);
        let is_generator = !core::fn_supers(tree, node).is_empty();
        let mut typed = Vec::with_capacity(params.len());
        for param in params {
            if self.lib.tree.kind(param) != NodeKind::Let {
                typed.push(Param {
                    ty: UNKNOWN,
                    optional: false,
                });
                continue;
            }
            let (_, ty, default) = core::let_parts(&self.lib.tree, param);
            typed.push(Param {
                ty: ty.map_or(UNKNOWN, |t| self.eval_type(t)),
                optional: default.is_some(),
            });
        }
        let result = match out_type {
            Some(t) if !is_generator => self.eval_result(t),
            // Kotlin doesn't check one with optional parameters against
            // the function type its place wants, so neither does tempest.
            Some(t) if !typed.iter().any(|p| p.optional) => self.generator_made(t),
            _ => UNKNOWN,
        };
        self.typing.types.intern(TypeData::Fn {
            params: typed,
            result,
        })
    }

    /// What a generator's factory makes, `SafeGenerator<Y>`, for its
    /// literal's `GeneratorResult<Y>`, or unknown for another type.
    fn generator_made(&mut self, out_type: NodeId) -> TypeId {
        let t = self.eval_type(out_type);
        let types = &mut self.typing.types;
        let index = |name: &str| crate::builtins::TYPES.iter().position(|&t| t == name);
        let (Some(result), Some(made)) = (index("GeneratorResult"), index("SafeGenerator")) else {
            return UNKNOWN;
        };
        let result = types.builtin(result as u8);
        match types.get(t) {
            TypeData::Applied { base, args } if *base == result && args.len() == 1 => {
                let args = args.clone();
                let base = types.builtin(made as u8);
                types.intern(TypeData::Applied { base, args })
            }
            _ => UNKNOWN,
        }
    }

    /// The type parameters a function declares, as `T` in `let f<T>(x:
    /// T)`. References to it, and calls that aren't solved
    /// ([`generic_calls`]), see them as unknown (notes/typing.md,
    /// "Generic calls").
    fn own_formals(&self, f: NodeId) -> Vec<SymbolId> {
        let tree = &self.lib.tree;
        (core::type_params(tree, f).iter())
            .filter_map(|&p| self.scopes.resolved(core::let_parts(tree, p).0))
            .collect()
    }

    /// Whether a type expression is a builtin type's bare name that may
    /// be generic, as `List`: not final, `AnyValue`, or `Never`.
    fn bare_builtin(&mut self, node: NodeId) -> bool {
        let Some(Builtin::Type(i)) = (self.lib.tree.kind(node) == NodeKind::RightName)
            .then(|| self.scopes.resolved_builtin(node))
            .flatten()
        else {
            return false;
        };
        let t = self.typing.types.builtin(i);
        t != NEVER && t != ANY_VALUE && !self.typing.types.is_final(t)
    }

    /// A function's result, from its declared return type: `T throws E`
    /// for a type parameter `E` is the result `T throws E`
    /// ([`Types::result`]), with a failure parameter (RFC 164), and `T
    /// throws X` for any other `X` is `T throws Bubble`, as `Bubble` is
    /// the only failure type for now; any other type as
    /// [`Settle::eval_type`] gives it.
    fn eval_result(&mut self, node: NodeId) -> TypeId {
        let pass = self.eval_type(node);
        if !self.is_throws(node) {
            return pass;
        }
        let fail = match self.lib.tree.children(node).get(2) {
            Some(&fail) => self.eval_type(fail),
            None => UNKNOWN,
        };
        let fail = match self.typing.types.get(fail) {
            TypeData::Param(_) => fail,
            _ => BUBBLE,
        };
        self.typing.types.result(pass, fail)
    }

    /// The type a type expression names, as `Int`, `String?` or `fn
    /// (Int): Boolean`. `T throws Bubble` is `T` but as a function's
    /// result ([`Settle::eval_result`]).
    fn eval_type(&mut self, node: NodeId) -> TypeId {
        let tree = &self.lib.tree;
        match tree.kind(node) {
            NodeKind::RightName => {
                let Some(b) = self.scopes.resolved(node) else {
                    return UNKNOWN;
                };
                let symbol = self.scopes.symbol(b);
                match symbol.kind {
                    // A bare `Never`, an error ([`Settle::check_bare_never`]),
                    // is unknown, as an undeclared type is.
                    SymbolKind::Builtin(Builtin::Type(i)) => match self.typing.types.builtin(i) {
                        NEVER => UNKNOWN,
                        t => t,
                    },
                    SymbolKind::TypeParam => self.type_param_type(b),
                    _ => match self.type_decl(b) {
                        Some(class) => self.class_type(class),
                        None => self.alias_type(b),
                    },
                }
            }
            NodeKind::Call => {
                let children = tree.children(node).to_vec();
                let Some((&callee, args)) = children.split_first() else {
                    return UNKNOWN;
                };
                match (self.scopes.resolved_builtin(callee), args) {
                    (Some(Builtin::Function(Function::Nullable)), [inner]) => {
                        let inner = self.eval_type(*inner);
                        self.typing.types.nullable(inner)
                    }
                    (Some(Builtin::Function(Function::Angle)), [base, args @ ..]) => {
                        self.applied_type(*base, args)
                    }
                    (Some(Builtin::Function(Function::Throws)), [pass, ..]) => {
                        self.eval_type(*pass)
                    }
                    (Some(Builtin::Function(Function::FnType)), [params @ .., ret]) => {
                        let params = params
                            .iter()
                            .map(|&p| Param {
                                ty: self.eval_type(p),
                                optional: false,
                            })
                            .collect();
                        let result = self.eval_result(*ret);
                        self.typing.types.intern(TypeData::Fn { params, result })
                    }
                    _ => UNKNOWN,
                }
            }
            _ => UNKNOWN,
        }
    }

    /// The type a type alias names, as `TestCase` in `export let TestCase
    /// = Pair<TestName, TestFun>`: a top-level `let` whose initializer is
    /// a type's name, or a type given arguments, nullable, or a function
    /// type. Not one that names itself, even through others.
    fn alias_type(&mut self, symbol: SymbolId) -> TypeId {
        let Some(init) = self.alias_init(symbol) else {
            return UNKNOWN;
        };
        let decl = self.scopes.symbol(symbol).decl.unwrap();
        if self.typing.aliases.contains(&decl) {
            return UNKNOWN;
        }
        self.typing.aliases.push(decl);
        let t = self.eval_type(init);
        self.typing.aliases.pop();
        t
    }

    /// The type expression a type alias names, as `C<Int>` for `let
    /// Alias = C<Int>;`, if `symbol` is one.
    pub(crate) fn alias_init(&self, symbol: SymbolId) -> Option<NodeId> {
        let tree = &self.lib.tree;
        let s = self.scopes.symbol(symbol);
        let decl = s.decl.filter(|&d| {
            s.kind == SymbolKind::TopLevel && !s.mutable && tree.kind(d) == NodeKind::Let
        })?;
        let (_, None, Some(init)) = core::let_parts(tree, decl) else {
            return None;
        };
        let is_type = tree.kind(init) == NodeKind::RightName
            || tree.kind(init) == NodeKind::Call
                && matches!(
                    self.scopes.resolved_builtin(tree.children(init)[0]),
                    Some(Builtin::Function(
                        Function::Angle | Function::Nullable | Function::FnType
                    ))
                );
        is_type.then_some(init)
    }

    /// A type parameter's type, as `T` names it, with its bound: `AnyValue`
    /// or the type it `extends`. Not a class's synthetic parameter.
    fn type_param_type(&mut self, symbol: SymbolId) -> TypeId {
        let tree = &self.lib.tree;
        let Some(decl) = (self.scopes.symbol(symbol).decl).filter(|&d| {
            tree.kind(d) == NodeKind::Let && tree.data(d) & ROLE_MASK == ROLE_TYPE_PARAM
        }) else {
            return UNKNOWN;
        };
        if self.typing.types.knows_param(symbol) {
            return self.typing.types.param(symbol);
        }
        // Known before its bound, which may name it, as in `<T extends
        // MapKey<T>>`.
        let t = self.typing.types.param(symbol);
        let bound = match core::let_parts(tree, decl).1 {
            Some(bound) => self.eval_type(bound),
            None => ANY_VALUE,
        };
        self.typing.types.set_bound(symbol, bound);
        t
    }

    /// A type parameter's bound as declared: the type it `extends`, or
    /// `AnyValue`; for one not declared by a `let` (core's), as
    /// [`Types::bound_of`] has it, `AnyValue` if unset. Evaluated here, as
    /// [`Types::param`] may have made its type before its bound was set.
    pub(crate) fn declared_bound(&mut self, symbol: SymbolId) -> TypeId {
        let tree = &self.lib.tree;
        let decl = (self.scopes.symbol(symbol).decl).filter(|&d| {
            tree.kind(d) == NodeKind::Let && tree.data(d) & ROLE_MASK == ROLE_TYPE_PARAM
        });
        match decl.map(|d| core::let_parts(tree, d).1) {
            Some(Some(bound)) => self.eval_type(bound),
            Some(None) => ANY_VALUE,
            None => self.typing.types.bound_of(symbol).unwrap_or(ANY_VALUE),
        }
    }

    /// A generic type given arguments, as `List<Int>`, `Never<Int>`
    /// ([`Types::apply_builtin`]), or `Box<String>`; not a class given the
    /// wrong number of them.
    fn applied_type(&mut self, base: NodeId, args: &[NodeId]) -> TypeId {
        let Some(b) = self.scopes.resolved(base) else {
            return UNKNOWN;
        };
        let base = match self.scopes.symbol(b).kind {
            SymbolKind::Builtin(Builtin::Type(i)) => {
                let base = self.typing.types.builtin(i);
                let args = args.iter().map(|&a| self.eval_type(a)).collect();
                return self.typing.types.apply_builtin(base, args);
            }
            _ => {
                let Some(class) = self.type_decl(b) else {
                    return UNKNOWN;
                };
                if core::type_params(&self.lib.tree, class).len() != args.len() {
                    return UNKNOWN;
                }
                match self.class_symbol(class) {
                    Some(symbol) => self.typing.types.intern(TypeData::Class(symbol)),
                    None => return UNKNOWN,
                }
            }
        };
        let args = args.iter().map(|&a| self.eval_type(a)).collect();
        self.typing.types.intern(TypeData::Applied { base, args })
    }

    // Checking.

    /// Reports type errors in each file, once declarations' types are
    /// known.
    pub(crate) fn check_types(&mut self) {
        self.typing.checking = true;
        for root in self.round_roots() {
            self.check(root);
        }
        self.check_fields_first();
        self.check_definite_assignment();
        self.check_exports();
        // After the other checks, so an error there comes first.
        for m in 0..self.lib.modules.len() {
            let scope = self.modules[m];
            let files = self.lib.modules[m].files.clone();
            let first = self.round_files();
            for f in files.into_iter().filter(|f| f.0 as usize >= first) {
                // A REPL input's own scope, which its root made.
                let root = self.lib.files[f.0 as usize].root;
                let scope = self.scopes.of_node(root).unwrap_or(scope);
                self.check_members_exist(root, scope, &mut Vec::new());
            }
        }
        self.type_all_exprs();
    }

    /// Types and checks nodes a pass made once typing was done, those
    /// numbered from `first_new` on, under `roots`, whose parents are
    /// noted from `containers` down; older nodes among them, moved there,
    /// keep what typing found for them. A declaration's type comes from its
    /// initializer again where `redeclared` lists it, before the rest,
    /// which may use it. True if every new expression is typed, each call
    /// among them resolved, and checking found no error.
    pub(crate) fn type_new_nodes(
        &mut self,
        first_new: u32,
        containers: &[NodeId],
        redeclared: &[NodeId],
        roots: &[NodeId],
    ) -> bool {
        for &c in containers {
            self.note_parents_under(c);
        }
        let errors = |s: &Settle| {
            (s.lib.diagnostics.iter())
                .filter(|d| d.level == Level::Error)
                .count()
        };
        let errors_before = errors(self);
        let mut complete = true;
        for &decl in redeclared {
            if let Some(init) = core::let_parts(&self.lib.tree, decl).2 {
                self.check_added(init, first_new);
                complete &= self.type_new_exprs(init, first_new);
                let t = self.known_type(init);
                self.typing.decls.insert(decl, t);
            }
        }
        for &root in roots.iter().filter(|&&r| r.0 >= first_new) {
            self.check_added(root, first_new);
            complete &= self.type_new_exprs(root, first_new);
        }
        complete && errors(self) == errors_before
    }

    /// [`Settle::check`] for the new nodes under `node`.
    fn check_added(&mut self, node: NodeId, first_new: u32) {
        if node.0 < first_new {
            return;
        }
        for i in 0..self.lib.tree.children(node).len() {
            let child = self.lib.tree.children(node)[i];
            self.check_added(child, first_new);
        }
        self.check_node(node);
    }

    /// Each new expression's type and resolution, as
    /// [`Settle::type_all_exprs`] finds them; true if all are typed and
    /// those that resolve are resolved.
    fn type_new_exprs(&mut self, root: NodeId, first_new: u32) -> bool {
        let exprs = exprs::Exprs::new(&self.lib, &self.scopes).under(root);
        let mut complete = true;
        for expr in exprs.into_iter().filter(|e| e.node.0 >= first_new) {
            complete &= self.known_type(expr.node) != UNKNOWN;
            if expr.resolves {
                self.resolve_call(expr.node);
                complete &= self.typing.resolved.contains_key(&expr.node);
            }
        }
        complete
    }

    /// Notes the parents of a node's descendants.
    pub(crate) fn note_parents_under(&mut self, node: NodeId) {
        let mut stack = vec![node];
        while let Some(node) = stack.pop() {
            for &child in self.lib.tree.children(node) {
                self.typing.set_parent(child, node);
                stack.push(child);
            }
        }
    }

    /// [`Settle::check_member_exists`] and [`Settle::check_member_access`]
    /// for every member read or call, in `scope` or the scope a node under
    /// it makes, inside `classes`.
    fn check_members_exist(&mut self, node: NodeId, scope: ScopeId, classes: &mut Vec<NodeId>) {
        let scope = self.scopes.of_node(node).unwrap_or(scope);
        let class = self.lib.tree.kind(node) == NodeKind::Class;
        if class {
            classes.push(node);
        }
        for i in 0..self.lib.tree.children(node).len() {
            let child = self.lib.tree.children(node)[i];
            self.check_members_exist(child, scope, classes);
        }
        if class {
            classes.pop();
        }
        match self.lib.tree.kind(node) {
            NodeKind::Call => {
                self.check_nullable_member(node);
                self.check_member_access(node, classes);
                self.check_member_exists(node, scope);
            }
            NodeKind::RightName => self.check_bare_static(node),
            _ => {}
        }
    }

    /// Reports a private member used outside the class that declares it,
    /// as `c.secret` or `C.secret`, which Kotlin Temper's typer rejects
    /// ("Member secret defined in C not publicly accessible"); code in the
    /// class may use it on any instance (kotlin-differences.md). Also a
    /// static member that isn't there, as `C.nope` ("No member nope in
    /// C"), which [`Settle::check_member_exists`] leaves alone, and a
    /// property of a type that's never a function called as a method, as
    /// `c.count()` ("Member count defined in C incompatible with usage").
    fn check_member_access(&mut self, node: NodeId, classes: &[NodeId]) {
        let Some((object, key)) = self.dot_parts(node) else {
            return;
        };
        let Some(name) = self.lib.symbol(key) else {
            return;
        };
        let span = self.lib.tree.span(node);
        let overlaps = |d: &crate::library::Diagnostic| {
            d.level == Level::Error && d.span.start < span.end && span.start < d.span.end
        };
        if self.lib.diagnostics.iter().any(overlaps) {
            return;
        }
        let tree = &self.lib.tree;
        let callee = (self.typing.parent(node))
            .is_some_and(|p| tree.kind(p) == NodeKind::Call && tree.children(p)[0] == node);
        let named_class = (tree.kind(object) == NodeKind::RightName)
            .then(|| self.scopes.resolved(object))
            .flatten()
            .and_then(|b| self.type_decl(b))
            .filter(|_| self.builtin_type_named(object).is_none());
        let (class, member) = match named_class {
            Some(class) => {
                let member = self.member_named(class, name, true, callee);
                let none = member.is_none()
                    && self.members_named(class, name, true).is_empty()
                    && self.members_named(class, name, false).is_empty();
                if none {
                    let message = Message::NoMember(
                        self.lib.names.get(name).to_string(),
                        self.member_text(class).to_string(),
                    );
                    self.lib.report(Level::Error, message, span);
                    return;
                }
                (class, member)
            }
            None => match self.dot_member_node(object, key, callee, false) {
                Ok(Some((m, receiver, _))) => (receiver.class, Some(m)),
                _ => return,
            },
        };
        let Some(member) = member else {
            return;
        };
        let owner = (self.ancestors(class).into_iter())
            .find(|&c| core::class_parts(&self.lib.tree, c).2.contains(&member))
            .unwrap_or(class);
        let (name, owner_text) = (
            self.lib.names.get(name).to_string(),
            self.member_text(owner).to_string(),
        );
        // A property called as a method, which Kotlin reports first.
        if callee && self.lib.tree.data(member) & ROLE_MASK != core::ROLE_METHOD {
            let t = self.member_type(member);
            let base = match self.typing.types.get(t) {
                TypeData::Applied { base, .. } => *base,
                _ => t,
            };
            if let TypeData::Builtin(i) = *self.typing.types.get(base) {
                if FULL_TYPES.contains(&crate::builtins::TYPES[i as usize]) {
                    let message = Message::IncompatibleUsage(name, owner_text);
                    self.lib.report(Level::Error, message, span);
                    return;
                }
            }
        }
        if self.lib.tree.data(member) & core::MEMBER_PRIVATE == 0 || classes.contains(&owner) {
            return;
        }
        let message = Message::NotPubliclyAccessible(name, owner_text);
        self.lib.report(Level::Error, message, span);
    }

    /// Reports a static member named bare, as `count` for `C.count` in
    /// `C`'s methods, as Kotlin Temper does ("Type name required for
    /// accessing static member"). It still runs as `C.count`.
    fn check_bare_static(&mut self, node: NodeId) {
        let Some(symbol) = self.scopes.resolved(node) else {
            return;
        };
        let symbol = self.scopes.symbol(symbol);
        let static_member = symbol.kind == SymbolKind::Member
            && symbol
                .decl
                .is_some_and(|d| self.lib.tree.data(d) & core::MEMBER_STATIC != 0);
        if !static_member {
            return;
        }
        let span = self.lib.tree.span(node);
        // Once, where `count += 1` reads and assigns it.
        let reported = (self.lib.diagnostics.iter())
            .any(|d| d.span == span && d.message == Message::StaticNeedsTypeName);
        if !reported {
            self.lib
                .report(Level::Error, Message::StaticNeedsTypeName, span);
        }
    }

    /// Reports an assignment to a static member, `C.count = 1` or `C.count
    /// += 1`, even a `static var`, as Kotlin Temper does: statics are only
    /// initialized at their declaration. A bare `count = 1` in `C` is
    /// reported too, besides needing its type's name. Returns whether it
    /// reported one; the assignment then fails where it runs.
    fn check_static_assignment(&mut self, node: NodeId, target: NodeId) -> bool {
        let (class, name) = match self.dot_parts(target) {
            Some((object, key)) => {
                let class = (self.lib.tree.kind(object) == NodeKind::RightName)
                    .then(|| self.scopes.resolved(object))
                    .flatten()
                    .and_then(|b| self.type_decl(b))
                    .filter(|_| self.builtin_type_named(object).is_none());
                match (class, self.lib.symbol(key)) {
                    (Some(class), Some(name)) => (class, name),
                    _ => return false,
                }
            }
            None => {
                let Some(b) = self.scopes.resolved(target) else {
                    return false;
                };
                let symbol = *self.scopes.symbol(b);
                let is_static = symbol.kind == SymbolKind::Member
                    && symbol
                        .decl
                        .is_some_and(|d| self.lib.tree.data(d) & core::MEMBER_STATIC != 0);
                if !is_static {
                    return false;
                }
                (self.scopes.get(symbol.owner).node, symbol.name)
            }
        };
        // `C.x` for an instance member `x` names nothing assignable either,
        // and Kotlin reports it the same way.
        if self.members_named(class, name, true).is_empty()
            && self.members_named(class, name, false).is_empty()
        {
            return false;
        }
        let message = Message::CannotAssignStatic(
            self.lib.names.get(name).to_string(),
            self.member_text(class).to_string(),
        );
        let span = self.lib.tree.span(node);
        self.lib.report(Level::Error, message, span);
        true
    }

    /// Checks a node after its children, as Kotlin Temper's `TypeChecker`
    /// does, so deeper errors come first.
    fn check(&mut self, node: NodeId) {
        let count = self.lib.tree.children(node).len();
        for i in 0..count {
            let child = self.lib.tree.children(node)[i];
            self.check(child);
        }
        self.check_node(node);
    }

    /// Checks a node, its children already checked.
    fn check_node(&mut self, node: NodeId) {
        let count = self.lib.tree.children(node).len();
        let first = self.lib.tree.children(node).first().copied();
        let last = self.lib.tree.children(node).last().copied();
        match (self.lib.tree.kind(node), first, last) {
            (NodeKind::RightName, ..) => self.check_bare_never(node),
            (NodeKind::Let, ..) => self.check_let(node),
            (NodeKind::Assign, Some(target), Some(value)) if count == 2 => {
                if let Some(decl) = self.local_target(target) {
                    let t = self.known_decl_type(decl);
                    self.check_value(t, value, Site::Assign(node));
                    self.check_not_void(value, node);
                } else if !self.check_static_assignment(node, target) {
                    // A setter's call, as Kotlin Temper reports it.
                    let t = self.member_target_type(target);
                    self.check_value(t, value, Site::Arg);
                    self.check_not_void(value, value);
                }
            }
            (NodeKind::Return, Some(value), _) => {
                let f = NodeId(self.lib.tree.data(node));
                match self.declared_result(f) {
                    Some(t) => self.check_value(t, value, Site::Return(node)),
                    None => self.check_literal_return(f, value),
                }
            }
            (NodeKind::Fn, ..) => {
                let body = core::fn_parts(&self.lib.tree, node).2;
                if let (Some(t), Some(body)) = (self.declared_result(node), body) {
                    if t != VOID && self.falls_off(body) {
                        self.report_falls_off(t, body);
                    }
                    let mut results = Vec::new();
                    self.results(body, &mut results);
                    // A `Never<T>` function whose body completes with
                    // `Void`, as Kotlin Temper reports it; but a loop, which
                    // may never end.
                    let never = matches!(self.typing.types.get(t), TypeData::NeverOf(_));
                    for result in results {
                        // Kotlin Temper reports a Void result where one is
                        // wanted at odd positions; not yet here.
                        let void = self.known_type(result) == VOID;
                        if !void || (never && self.lib.tree.kind(result) != NodeKind::Loop) {
                            self.check_value(t, result, Site::Result);
                        }
                    }
                }
                self.check_literal_result(node);
                if self.may_not_bubble(node) {
                    match self.unfailing_place(node) {
                        Some(message) if !self.failures_in(node).is_empty() => {
                            let span = self.lib.tree.span(node);
                            self.lib.report(Level::Error, message, span);
                        }
                        Some(_) => {}
                        None => self.check_bubbles(node),
                    }
                }
            }
            (NodeKind::Call, ..) => {
                self.check_call(node);
                self.record_trailing_block(node);
                let args = self
                    .lib
                    .tree
                    .children(node)
                    .get(1..)
                    .unwrap_or(&[])
                    .to_vec();
                for arg in args {
                    self.check_not_void(arg, arg);
                }
            }
            (NodeKind::Class, ..) => {
                self.check_overrides(node);
                self.check_fun_interface(node);
                self.check_connected_class(node);
            }
            (NodeKind::If, Some(condition), _) => {
                let mut t = self.known_type(condition);
                let mut mismatch = self.is_mismatch(t, BOOLEAN);
                if let Some(leaves) = self.unjoined_leaves(condition, t) {
                    mismatch = !self.leaves_fit(&leaves, BOOLEAN);
                    t = self.unjoined_type(&leaves);
                }
                if mismatch && t != NULL {
                    let (expected, got) = (self.show(BOOLEAN), self.show(t));
                    let span = self.lib.tree.span(condition);
                    let message = Message::ExpectedValueOfType(expected, got);
                    self.lib.report(Level::Error, message, span);
                }
            }
            _ => {}
        }
    }

    fn check_let(&mut self, node: NodeId) {
        let (name, ty, init) = core::let_parts(&self.lib.tree, node);
        let Some(b) = self.scopes.resolved(name) else {
            return;
        };
        let kind = self.scopes.symbol(b).kind;
        let role = self.lib.tree.data(node) & core::ROLE_MASK;
        let wants_type = match kind {
            SymbolKind::TopLevel | SymbolKind::Local => ty.is_none(),
            SymbolKind::Member => role == core::ROLE_METHOD || role == core::ROLE_GETTER,
            _ => false,
        };
        if let Some(init) = init {
            let is_fn = self.lib.tree.kind(init) == NodeKind::Fn;
            let body = is_fn
                .then(|| core::fn_parts(&self.lib.tree, init).2)
                .flatten();
            let body = body.filter(|_| wants_type && self.lacks_return_type(init));
            if let Some(body) = body {
                let start = self.lib.tree.span(body).start;
                let span = Span { start, end: start };
                self.lib
                    .report(Level::Error, Message::MissingReturnType, span);
            }
            if ty.is_some() && matches!(kind, SymbolKind::TopLevel | SymbolKind::Local) {
                let t = self.known_decl_type(node);
                self.check_value(t, init, Site::Init);
            }
            // A field's initializer, which Kotlin Temper assigns as a
            // setter would.
            let is_field = role == 0 && self.lib.tree.data(node) & core::MEMBER_STATIC == 0;
            if let (Some(ty), SymbolKind::Member, true) = (ty, kind, is_field) {
                let t = self.eval_type(ty);
                self.check_value(t, init, Site::Field);
            }
            if matches!(kind, SymbolKind::TopLevel | SymbolKind::Local) {
                self.check_not_void(init, init);
                if ty.is_none() {
                    self.check_untyped_fun_literal(node, init);
                }
            }
            // These return `Void` without saying so.
            let is_void = role == core::ROLE_CONSTRUCTOR || role == core::ROLE_SETTER;
            if is_void && is_fn && core::fn_parts(&self.lib.tree, init).1.is_none() {
                self.check_bubbles(init);
            }
        }
    }

    /// A function with no return type that something else could give it:
    /// not a generator, nor a `for … of` body.
    fn lacks_return_type(&self, f: NodeId) -> bool {
        let data = self.lib.tree.data(f);
        data & (core::FN_OUT_TYPE | core::FN_LOOP_BODY) == 0
            && core::fn_supers(&self.lib.tree, f).is_empty()
    }

    /// A function's declared return type, unless it's a generator's.
    fn declared_result(&mut self, f: NodeId) -> Option<TypeId> {
        if self.lib.tree.kind(f) != NodeKind::Fn || !core::fn_supers(&self.lib.tree, f).is_empty() {
            return None;
        }
        let out_type = core::fn_parts(&self.lib.tree, f).1?;
        Some(self.eval_type(out_type))
    }

    /// What a function literal without a return type gives, as its type
    /// says ([`Settle::literal_type`]), where its place wants a function
    /// type: what that gives, or where a generic call leaves it open, the
    /// call's solution, from the literal's body.
    fn literal_result(&mut self, f: NodeId) -> Option<TypeId> {
        self.fn_result(f, false).ok()??;
        let t = self.literal_type(f, false).ok()?;
        match *self.typing.types.get(t) {
            TypeData::Fn { result, .. } => Some(self.typing.types.pass(result)),
            _ => None,
        }
    }

    /// Reports a function literal without a return type whose result
    /// isn't what the function type its place wants gives, as Kotlin
    /// Temper does: "Expected subtype of Int32, but got String", for `{ x
    /// => "a" }` where a `fn (Int32): Int32` is wanted, at the result,
    /// where Kotlin's position is empty, at the start of the body. The
    /// result is the body's last expression, as one value, so an `if`'s
    /// branches are joined. Not where `Void` is wanted, which
    /// Kotlin reports but for some builtin methods, as `forEach`, nor for
    /// a result that's null, `Void` or never returns, which it reports
    /// with odd types.
    fn check_literal_result(&mut self, f: NodeId) {
        let (_, out_type, body) = core::fn_parts(&self.lib.tree, f);
        let (None, Some(body)) = (out_type, body) else {
            return;
        };
        let Some(t) = self.literal_result(f) else {
            return;
        };
        let mut result = body;
        while self.lib.tree.kind(result) == NodeKind::Block {
            match self.lib.tree.children(result).last() {
                Some(&last) => result = last,
                None => break,
            }
        }
        let s = self.known_type(result);
        if t == VOID || matches!(s, NULL | VOID | NEVER) || !self.misfits(s, t) {
            return;
        }
        let message = Message::ExpectedSubType(self.show(t), self.show(s));
        let span = self.lib.tree.span(result);
        self.lib.report(Level::Error, message, span);
    }

    /// Reports a value returned from a function literal without a return
    /// type that isn't the result its place wants, as Kotlin Temper does:
    /// "Expected subtype of Int32, but got String", for `fn (x) { return
    /// "a"; }` where a `fn (Int32): Int32` is wanted, at the value, where
    /// Kotlin's position is empty, just after the `return`; a bare
    /// `return` gives `Void`, at the `return`, and null is shown as the
    /// nullable result wanted. A value that never returns, as `panic()`,
    /// fits.
    fn check_literal_return(&mut self, f: NodeId, value: NodeId) {
        if core::fn_parts(&self.lib.tree, f).1.is_some() {
            return;
        }
        let Some(t) = self.literal_result(f) else {
            return;
        };
        let s = self.known_type(value);
        if s == NEVER || !self.misfits(s, t) {
            return;
        }
        let got = match s {
            NULL => self.typing.types.nullable(t),
            _ => s,
        };
        let message = Message::ExpectedSubType(self.show(t), self.show(got));
        let span = self.lib.tree.span(value);
        self.lib.report(Level::Error, message, span);
    }

    /// Whether a body can reach its end without a value, as Kotlin Temper
    /// sees it: where its result would be an empty block, as an `if`'s
    /// missing `else`, unless that `else` is never reached, as a `when`'s
    /// whose `is` checks cover a sealed type ([`sealed`]), however deep in
    /// the result it is.
    fn falls_off(&mut self, node: NodeId) -> bool {
        let children = self.lib.tree.children(node).to_vec();
        match self.lib.tree.kind(node) {
            NodeKind::Block => match children.last() {
                Some(&last) => self.falls_off(last),
                None => !self.unreachable_else(node, false).unwrap_or(false),
            },
            NodeKind::If if children.len() == 3 => {
                children[1..].iter().any(|&branch| self.falls_off(branch))
            }
            _ => false,
        }
    }

    /// Reports a body that can reach its end where a `t` is wanted, as
    /// Kotlin Temper does, as `Void` assigned to the result, at its
    /// closing brace: "Cannot assign to String from Void". `to_tmpl`
    /// fails there.
    fn report_falls_off(&mut self, t: TypeId, body: NodeId) {
        let span = self.lib.tree.span(body);
        let span = Span {
            start: span.end.saturating_sub(1),
            end: span.end,
        };
        let message = Message::CannotAssign(self.show(t), self.show(VOID));
        self.lib.report(Level::Error, message, span);
    }

    /// The expressions whose values a body gives as its result.
    fn results(&self, node: NodeId, out: &mut Vec<NodeId>) {
        let children = self.lib.tree.children(node);
        match self.lib.tree.kind(node) {
            NodeKind::Block => {
                if let Some(&last) = children.last() {
                    self.results(last, out);
                }
            }
            NodeKind::If | NodeKind::Orelse if children.len() >= 2 => {
                for &branch in &children[children.len() - 2..] {
                    self.results(branch, out);
                }
            }
            _ => out.push(node),
        }
    }

    fn check_call(&mut self, node: NodeId) {
        let _ = self.resolve_overload(node, false);
        let _ = self.resolve_extension(node, false, true);
        let children = self.lib.tree.children(node).to_vec();
        let Some((&callee, args)) = children.split_first() else {
            return;
        };
        let params = match self.scopes.resolved_builtin(callee) {
            Some(Builtin::Function(Function::New)) => match self.new_class(args) {
                Some(class) => self.constructor_type(class),
                None => {
                    self.check_generic_call(node);
                    return;
                }
            },
            Some(Builtin::Function(f @ (Function::Is | Function::As))) => {
                return self.check_rtti(node, f, args);
            }
            // `a[i]` calls `a.get(i)`.
            Some(Builtin::Function(Function::Index)) if !args.is_empty() => {
                let t = self.known_index_callee_type(args[0]);
                match self.typing.types.get(t) {
                    TypeData::Fn { params, .. } => params.clone(),
                    _ => return,
                }
            }
            Some(Builtin::Function(f)) => return self.check_builtin_call(f, args),
            Some(_) => return,
            None if self.check_tie(node, callee, args) => return,
            None if self.check_generic_call(node) => return,
            None => {
                self.check_console_call(node, callee, args);
                let mut callee_type = self.known_callee_type(callee);
                // A function that may be null, as `act(i)` for `act: (fn
                // (Int): Void)?`, as Kotlin Temper reports it; the
                // arguments are checked against what it is when not null.
                if let TypeData::Nullable(inner) = *self.typing.types.get(callee_type) {
                    if let Some(f) = self.typing.types.as_fn(inner) {
                        let (to, from) = (self.show(inner), self.show(callee_type));
                        let span = self.lib.tree.span(callee);
                        self.lib
                            .report(Level::Error, Message::ExpectedSubType(to, from), span);
                        callee_type = f;
                    }
                }
                match self.typing.types.get(callee_type) {
                    TypeData::Fn { params, .. } => params.clone(),
                    _ => return,
                }
            }
        };
        // A constructor's class, or what's indexed, comes first.
        let args = match self.scopes.resolved_builtin(callee) {
            Some(_) => &args[1..],
            None => args,
        };
        // Not yet named arguments, nor type arguments.
        if (args.iter()).any(|&a| matches!(self.lib.value(a), Some(Value::Symbol(_)))) {
            return;
        }
        let required = params.iter().filter(|p| !p.optional).count();
        if args.len() < required || args.len() > params.len() {
            let span = self.lib.tree.span(node);
            let message = Message::ArityMismatch(params.len());
            self.lib.report(Level::Error, message, span);
        }
        let placed = self.placed_params(&params, args);
        for (&arg, &i) in args.iter().zip(&placed) {
            let Some(param) = params.get(i) else {
                continue;
            };
            // Null selects a parameter's default, so a parameter with one
            // takes null, or any nullable value whose non-null type fits.
            let t = self.known_type(arg);
            let defaults = match *self.typing.types.get(t) {
                _ if t == NULL => true,
                TypeData::Nullable(inner) => !self.is_mismatch(inner, param.ty),
                _ => false,
            };
            if !(param.optional && defaults) {
                self.check_value(param.ty, arg, Site::Arg);
            }
        }
    }

    /// Reports `object.key` where `object` may be null, as `c.n` for `c:
    /// Counter?` with no null check that narrows it (`flow`), a property's
    /// read or assignment or a method's call, as Kotlin Temper rejects it,
    /// though in odder words ("No member n in AnyValue"): "No member n in
    /// Counter?". Beside [`Settle::check_call`]'s nullable function value.
    /// `c?.n` is fine. A nullable value is nullable whatever it holds, so
    /// `s.isEmpty` for `let s: String? = "q"` is reported too, where
    /// Kotlin works `s` out while compiling. Code is still generated; the
    /// access fails where it runs, as for a member that isn't there.
    fn check_nullable_member(&mut self, node: NodeId) {
        let Some((object, key)) = self.dot_parts(node) else {
            return;
        };
        let Some(name) = self.lib.symbol(key) else {
            return;
        };
        let t = self.known_type(object);
        if !matches!(self.typing.types.get(t), TypeData::Nullable(_)) {
            return;
        }
        let span = self.lib.tree.span(node);
        let overlaps = |d: &crate::library::Diagnostic| {
            d.level == Level::Error && d.span.start < span.end && span.start < d.span.end
        };
        if self.lib.diagnostics.iter().any(overlaps) {
            return;
        }
        let message = Message::NoMember(self.lib.names.get(name).to_string(), self.show(t));
        self.lib.report(Level::Error, message, span);
    }

    /// Reports `object.key` where `object`'s type has no member `key`,
    /// of its own, inherited, or core's, as Kotlin Temper does ("No
    /// member key in T"), so a misspelled member is an error that fails
    /// where it runs (tmpl's `Error`), not code that can't be translated.
    /// Only where typing knows every member: of a class's value, or of a
    /// builtin type whose members core's table has in full ([`FULL_TYPES`]);
    /// not of an interface's value, as Kotlin lets `this.toString()` be
    /// in one, nor of a nullable value or a type parameter's (Kotlin's
    /// messages there are odd; kotlin-differences.md), nor a static
    /// member, as `C.key`, nor a destructuring's, nor a call where an
    /// `@extension("key")` is visible, declared or imported, that typing
    /// left a method call after reporting why ([`instance_extensions`]),
    /// nor where an error is reported already, as for a method inherited
    /// twice.
    fn check_member_exists(&mut self, node: NodeId, scope: ScopeId) {
        let Some((object, key)) = self.dot_parts(node) else {
            return;
        };
        let Some(name) = self.lib.symbol(key) else {
            return;
        };
        let names_type = self.lib.tree.kind(object) == NodeKind::RightName
            && (self.builtin_type_named(object).is_some()
                || (self.scopes.resolved(object)).is_some_and(|b| self.type_decl(b).is_some()));
        let destructured = self.is_temporary(object)
            && self.lib.name(object).map(|n| self.lib.names.get(n)) == Some("destructured");
        if names_type || destructured {
            return;
        }
        let t = self.known_type(object);
        let base = match self.typing.types.get(t) {
            TypeData::Applied { base, .. } => *base,
            _ => t,
        };
        let known = match *self.typing.types.get(base) {
            TypeData::Class(symbol) => (self.scopes.symbol(symbol).decl).is_some_and(|c| {
                self.lib.tree.kind(c) == NodeKind::Class
                    && self.lib.tree.data(c) & core::CLASS_INTERFACE == 0
            }),
            TypeData::Builtin(i) => FULL_TYPES.contains(&crate::builtins::TYPES[i as usize]),
            _ => false,
        };
        let span = self.lib.tree.span(node);
        let overlaps = |d: &crate::library::Diagnostic| {
            d.level == Level::Error && d.span.start < span.end && span.start < d.span.end
        };
        // A method call an extension might be, which typing left as it
        // is after reporting why.
        let tree = &self.lib.tree;
        let callee = (self.typing.parent(node))
            .is_some_and(|p| tree.kind(p) == NodeKind::Call && tree.children(p)[0] == node);
        if !known
            || self.lib.diagnostics.iter().any(overlaps)
            || (callee && self.extension_visible(scope, name, node))
        {
            return;
        }
        // A setter-only property assigned, as `c.p = 5` for `set p(v: Int)`.
        let tree = &self.lib.tree;
        let assigned = (self.typing.parent(node)).is_some_and(|p| {
            tree.kind(p) == NodeKind::Assign && tree.children(p).first() == Some(&node)
        });
        if assigned && self.has_setter(t, name) {
            return;
        }
        let name = self.lib.names.get(name).to_string();
        for callee in [false, true] {
            match self.dot_member_node(object, key, callee, false) {
                Ok(None) => {}
                _ => return,
            }
            match self.core_member_named(object, &name, callee, false) {
                Ok(None) => {}
                _ => return,
            }
        }
        let message = Message::NoMember(name, self.show(t));
        self.lib.report(Level::Error, message, span);
    }

    /// Whether an assignment's value is used, as an initializer's, an
    /// argument's or another assignment's, not as a block's statement or
    /// result.
    fn assignment_used(&self, node: NodeId) -> bool {
        let tree = &self.lib.tree;
        (self.typing.parent(node)).is_some_and(|p| match tree.kind(p) {
            NodeKind::Let | NodeKind::Call => true,
            NodeKind::Assign => tree.children(p).get(1) == Some(&node),
            _ => false,
        })
    }

    /// Whether a value of type `t` has an instance setter named `name`.
    fn has_setter(&mut self, t: TypeId, name: NameId) -> bool {
        let Some(receiver) = self.receiver(t) else {
            return false;
        };
        let tree = &self.lib.tree;
        (self.members_named(receiver.class, name, false).iter())
            .any(|&m| tree.data(m) & ROLE_MASK == core::ROLE_SETTER)
    }

    /// Reports `Never` named without the type it stands for, as `let f():
    /// Never`: only `Never<T>` is a type ([`Types::never_of`]), which
    /// says what its uses translate to.
    fn check_bare_never(&mut self, node: NodeId) {
        let Some(Builtin::Type(i)) = self.scopes.resolved_builtin(node) else {
            return;
        };
        let tree = &self.lib.tree;
        let applied = (self.typing.parent(node)).is_some_and(|p| {
            tree.kind(p) == NodeKind::Call
                && tree.children(p).get(1) == Some(&node)
                && (self.scopes.resolved_builtin(tree.children(p)[0]))
                    == Some(Builtin::Function(Function::Angle))
        });
        if self.typing.types.builtin(i) == NEVER && !applied {
            let span = self.lib.tree.span(node);
            self.lib.report(Level::Error, Message::BareNever, span);
        }
    }

    /// Reports a `Void` value stored in a declaration or passed as an
    /// argument, as `let x = b()` for `b(): Void` or `c(void)`, or a
    /// `Never<Void>` one ([`Types::is_void_like`]), at `site`, unless an
    /// error is reported there already, as Kotlin Temper reports one error
    /// per position.
    fn check_not_void(&mut self, value: NodeId, site: NodeId) {
        let t = self.known_type(value);
        if !self.typing.types.is_void_like(t) {
            return;
        }
        let span = self.lib.tree.span(site);
        let reported =
            (self.lib.diagnostics.iter()).any(|d| d.level == Level::Error && d.span == span);
        if !reported {
            self.lib.report(Level::Error, Message::VoidAsValue, span);
        }
    }

    /// Reports storing `value` where a `t` is wanted, if it can't be one.
    fn check_value(&mut self, t: TypeId, value: NodeId, site: Site) {
        let s = self.known_type(value);
        // An `if` or `orelse` whose branches have no common type, as `p()
        // orelse false` for `p(): Int64`: as Kotlin Temper does, each
        // branch is assigned on its own, but an argument that doesn't fit
        // is an `AnyValue`.
        if let Some(leaves) = self.unjoined_leaves(value, s) {
            if !matches!(site, Site::Arg | Site::Field) {
                for leaf in leaves {
                    self.check_value(t, leaf, Site::Init);
                }
            } else if !self.leaves_fit(&leaves, t) {
                let joined = self.unjoined_type(&leaves);
                let (to, from) = (self.show(t), self.show(joined));
                let span = self.lib.tree.span(value);
                self.lib
                    .report(Level::Error, Message::ExpectedSubType(to, from), span);
            }
            return;
        }
        let value_span = self.lib.tree.span(value);
        // Kotlin Temper solves a generic call or a list literal from the
        // type wanted, and reports its arguments instead; not for a call's
        // argument, where it misses the error.
        let solves_wanted = !matches!(site, Site::Arg);
        if solves_wanted && self.is_list_literal(value) && self.check_list_wanted(value, t) {
            return;
        }
        if s == NULL {
            if self.is_mismatch(NULL, t) {
                let t_or_null = self.typing.types.nullable(t);
                let message = Message::ExpectedSubType(self.show(t), self.show(t_or_null));
                self.lib.report(Level::Error, message, value_span);
            }
            return;
        }
        if !self.is_mismatch(s, t) {
            return;
        }
        if solves_wanted && self.check_generic_wanted(value, t) {
            return;
        }
        // Kotlin Temper lets a nullable value be an `AnyValue`. Otherwise
        // a nullable value is nullable whatever it holds, until narrowed.
        if let TypeData::Nullable(inner) = *self.typing.types.get(s) {
            if t == ANY_VALUE && !self.is_mismatch(inner, t) {
                return;
            }
        }
        let span = match site {
            Site::Assign(n) | Site::Return(n) => self.lib.tree.span(n),
            _ => value_span,
        };
        if self.check_fun_adapter(s, t, value, span) {
            return;
        }
        // A `Never<Void>` is shown as the `Void` it stands for, as Kotlin
        // Temper shows it.
        let s = if self.typing.types.is_void_like(s) {
            VOID
        } else {
            s
        };
        let (to, from) = (self.show(t), self.show(s));
        let (message, span) = match site {
            Site::Init | Site::Result => (Message::CannotAssign(to, from), value_span),
            Site::Assign(n) | Site::Return(n) => {
                (Message::CannotAssign(to, from), self.lib.tree.span(n))
            }
            Site::Arg | Site::Field => (Message::ExpectedSubType(to, from), value_span),
        };
        self.lib.report(Level::Error, message, span);
    }

    /// The branches of an `if` or `orelse` value of type `t`, through
    /// nested ones, when they have no common type (`t` is unknown) though
    /// each one's type is known, as `p()` and `false` in `p() orelse
    /// false` for `p(): Int64`.
    fn unjoined_leaves(&mut self, value: NodeId, t: TypeId) -> Option<Vec<NodeId>> {
        if t != UNKNOWN || !matches!(self.lib.tree.kind(value), NodeKind::If | NodeKind::Orelse) {
            return None;
        }
        let mut leaves = Vec::new();
        self.branch_leaves(value, &mut leaves);
        for &leaf in &leaves {
            let t = self.known_type(leaf);
            if !self.typing.types.is_known(t) {
                return None;
            }
        }
        Some(leaves)
    }

    /// Adds the branches of `node`'s value to `out`, through nested `if`s
    /// and `orelse`s, or else `node` itself.
    fn branch_leaves(&self, node: NodeId, out: &mut Vec<NodeId>) {
        let children = self.lib.tree.children(node);
        match self.lib.tree.kind(node) {
            NodeKind::If if children.len() == 3 => {
                self.branch_leaves(children[1], out);
                self.branch_leaves(children[2], out);
            }
            NodeKind::Orelse if children.len() == 2 => {
                self.branch_leaves(children[0], out);
                self.branch_leaves(children[1], out);
            }
            _ => out.push(node),
        }
    }

    /// Whether every one of these branches' values can be a `t`.
    fn leaves_fit(&mut self, leaves: &[NodeId], t: TypeId) -> bool {
        leaves.iter().all(|&leaf| {
            let s = self.known_type(leaf);
            !self.is_mismatch(s, t)
        })
    }

    /// The type Kotlin Temper gives a value whose branches have no common
    /// type: `AnyValue`, or `AnyValue?` if a branch may be null.
    fn unjoined_type(&mut self, leaves: &[NodeId]) -> TypeId {
        let mut nullable = false;
        for &leaf in leaves {
            let t = self.known_type(leaf);
            nullable |= t == NULL || matches!(self.typing.types.get(t), TypeData::Nullable(_));
        }
        if nullable {
            self.typing.types.nullable(ANY_VALUE)
        } else {
            ANY_VALUE
        }
    }

    /// Whether Kotlin Temper might work out an expression's value while
    /// compiling, as it does for literals, `let`s of them, and calls of
    /// functions with such arguments, when it can: anything that doesn't
    /// read a parameter, an instance's member, or a `var` that's assigned,
    /// may be.
    fn may_fold(&self, node: NodeId, depth: u32) -> bool {
        let tree = &self.lib.tree;
        if depth > 8 {
            return true;
        }
        match tree.kind(node) {
            NodeKind::Value | NodeKind::Fn => true,
            NodeKind::RightName => {
                let Some(b) = self.scopes.resolved(node) else {
                    return true;
                };
                let symbol = self.scopes.symbol(b);
                match (symbol.kind, symbol.decl) {
                    (SymbolKind::Parameter, _) => false,
                    // An instance's member reads `this`.
                    (SymbolKind::Member, Some(decl)) => tree.data(decl) & core::MEMBER_STATIC != 0,
                    (_, Some(decl)) if tree.kind(decl) == NodeKind::Let => {
                        match core::let_parts(tree, decl).2 {
                            _ if symbol.mutable && self.typing.assigned.contains(&decl) => false,
                            Some(init) => self.may_fold(init, depth + 1),
                            None => true,
                        }
                    }
                    _ => true,
                }
            }
            // A builtin type's static member, as `String.fromCodePoint`.
            NodeKind::Call if self.names_builtin_static(node) => true,
            NodeKind::Call | NodeKind::Block | NodeKind::If | NodeKind::Orelse => tree
                .children(node)
                .iter()
                .all(|&child| self.may_fold(child, depth + 1)),
            NodeKind::Let => core::let_parts(tree, node)
                .2
                .is_none_or(|init| self.may_fold(init, depth + 1)),
            _ => false,
        }
    }

    /// Whether a value of type `s` can't be a `t`, both fully known.
    fn is_mismatch(&mut self, s: TypeId, t: TypeId) -> bool {
        let types = &mut self.typing.types;
        types.is_known(s) && types.is_known(t) && types.is_subtype(s, t) == Some(false)
    }

    /// A type once declarations' types are known: any still waited on
    /// was never queued, so is unknown.
    fn known_type(&mut self, node: NodeId) -> TypeId {
        self.expr_type(node, false).unwrap_or(UNKNOWN)
    }

    /// What `object[…]` calls, as [`Settle::index_callee_type`] finds it.
    fn known_index_callee_type(&mut self, object: NodeId) -> TypeId {
        self.index_callee_type(object, false).unwrap_or(UNKNOWN)
    }

    /// What a callee calls, as [`Settle::callee_type`] finds it.
    fn known_callee_type(&mut self, callee: NodeId) -> TypeId {
        self.callee_type(callee, false).unwrap_or(UNKNOWN)
    }

    fn known_decl_type(&mut self, decl: NodeId) -> TypeId {
        self.decl_type(decl, false).unwrap_or(UNKNOWN)
    }

    /// A type as messages show it.
    fn show(&self, t: TypeId) -> String {
        let class_name = |b| {
            let name = self.scopes.symbol(b).name;
            self.lib.names.get(name).to_string()
        };
        self.typing.types.display(t, &class_name)
    }
}

/// Whether a node is a call that never returns normally, after which
/// nothing runs, as after `return`: `panic()`, or a call of a function or
/// method whose result is `Never<T>`, by the call's type once typed, or
/// before, as flow facts are found, by the declaration its callee's name
/// resolves to, which a method called on an object isn't yet.
pub(crate) fn never_returns(
    tree: &temper_syntax::tree::Tree,
    scopes: &crate::scope::Scopes,
    typing: &Typing,
    node: NodeId,
) -> bool {
    let Some(&callee) = (tree.kind(node) == NodeKind::Call)
        .then(|| tree.children(node).first())
        .flatten()
    else {
        return false;
    };
    let is = |n: NodeId, what: Function| {
        tree.kind(n) == NodeKind::Call
            && (tree.children(n).first())
                .is_some_and(|&c| scopes.resolved_builtin(c) == Some(Builtin::Function(what)))
    };
    if is(node, Function::Panic) {
        return true;
    }
    if let Some(t) = typing.expr(node) {
        return matches!(typing.types.get(t), TypeData::NeverOf(_));
    }
    let decl = (scopes.resolved(callee)).and_then(|b| scopes.symbol(b).decl);
    (decl.filter(|&d| tree.kind(d) == NodeKind::Let))
        .and_then(|d| core::let_parts(tree, d).2)
        .is_some_and(|f| declares_never(tree, scopes, f))
}

/// Whether a function's declared result is `Never<T>`, as `fn (): Never<Void>`,
/// or `Never<T> throws E`.
fn declares_never(
    tree: &temper_syntax::tree::Tree,
    scopes: &crate::scope::Scopes,
    f: NodeId,
) -> bool {
    let is = |n: NodeId, what: Function| {
        tree.kind(n) == NodeKind::Call
            && (tree.children(n).first())
                .is_some_and(|&c| scopes.resolved_builtin(c) == Some(Builtin::Function(what)))
    };
    let out = (tree.kind(f) == NodeKind::Fn)
        .then(|| core::fn_parts(tree, f).1)
        .flatten();
    let Some(mut out) = out else {
        return false;
    };
    if is(out, Function::Throws) {
        out = tree.children(out)[1];
    }
    is(out, Function::Angle)
        && matches!(
            scopes.resolved_builtin(tree.children(out)[1]),
            Some(Builtin::Type(i)) if crate::builtins::TYPES[i as usize] == "Never"
        )
}
