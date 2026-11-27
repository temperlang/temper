//! Target typing, milestone 4's sixth slice (notes/typing.md, "List
//! literals and target typing" and "Function literals and target
//! typing"): the type a value's place wants, from a declared type, a
//! function's result, a known callee's parameter, or a list literal's
//! element type, types a literal that has no type of its own to give.
//!
//! So far, list literals: `[]` where a `List<Int32>` is wanted is one,
//! and `[d]` for a `d: Dog` where a `List<Animal>` is wanted is a
//! `List<Animal>`; elsewhere, a list literal is a list of its elements'
//! common type, as Kotlin Temper's solver finds it. And function
//! literals: `{ x => x + 1 }` where a `fn (Int32): Int32` is wanted takes
//! `x` as an `Int32` and gives an `Int32`, with a trailing block placed
//! at its parameter while typing, as Kotlin Temper places it. Object
//! literals' classes, decided from the type wanted, are in
//! [`super::object_literals`].

use crate::builtins::{Builtin, Function};
use crate::core::{self, ROLE_MASK, ROLE_TYPE_PARAM};
use crate::settle::{Fact, Settle};
use crate::types::{ANY_VALUE, NEVER, NULL, Param, TypeData, TypeId, UNKNOWN, VOID};
use temper_syntax::tree::{NodeId, NodeKind};

impl Settle {
    /// Whether a node is a list literal, `[a, b]`.
    pub(super) fn is_list_literal(&self, node: NodeId) -> bool {
        let tree = &self.lib.tree;
        tree.kind(node) == NodeKind::Call
            && (tree.children(node).first()).is_some_and(|&callee| {
                self.scopes.resolved_builtin(callee) == Some(Builtin::Function(Function::List))
            })
    }

    /// A list literal's type: a `List` of the element type its place
    /// wants, if that's a `List` (a `Listed` only for `[]`, which has
    /// nothing else to go by), as Kotlin Temper has it, and every element
    /// is one, else of
    /// its elements' common type ([`Settle::common_type`]), unknown if
    /// they have none.
    pub(super) fn list_type(&mut self, list: NodeId, forced: bool) -> Result<TypeId, Fact> {
        let elements = self.lib.tree.children(list)[1..].to_vec();
        self.list_type_of(list, &elements, forced)
    }

    /// `List.of(a, b)` or `List.of<T>(a, b)`, a list literal by another
    /// name, as Kotlin Temper unifies them: its type arguments and its
    /// elements.
    pub(super) fn list_of_parts(&self, node: NodeId) -> Option<(Vec<NodeId>, Vec<NodeId>)> {
        let tree = &self.lib.tree;
        if tree.kind(node) != NodeKind::Call {
            return None;
        }
        let (&callee, args) = tree.children(node).split_first()?;
        let (object, key) = self.dot_parts(callee)?;
        let named = self.builtin_type_named(object)?;
        let is_of = self
            .lib
            .symbol(key)
            .is_some_and(|k| self.lib.names.get(k) == "of");
        (crate::builtins::TYPES[named as usize] == "List" && is_of)
            .then(|| self.split_type_args(args))?
    }

    /// `List.of`'s type, as a list literal's, or `List<T>` given `T`.
    pub(super) fn list_of_type(&mut self, call: NodeId, forced: bool) -> Result<TypeId, Fact> {
        let Some((type_args, elements)) = self.list_of_parts(call) else {
            return Ok(UNKNOWN);
        };
        match *type_args {
            [] => self.list_type_of(call, &elements, forced),
            [t] => {
                for &element in &elements {
                    self.expr_type(element, forced)?;
                }
                let t = self.eval_type(t);
                Ok(self.typing.types.list_of(t))
            }
            _ => Ok(UNKNOWN),
        }
    }

    /// A list literal's type, of these elements.
    fn list_type_of(
        &mut self,
        list: NodeId,
        elements: &[NodeId],
        forced: bool,
    ) -> Result<TypeId, Fact> {
        let mut types = Vec::with_capacity(elements.len());
        for &element in elements {
            types.push(self.expr_type(element, forced)?);
        }
        let wanted = self.wanted_type(list, forced)?;
        // Where a `Listed<P>` is wanted, `[a]` is a `List<A>`, no `Listed<P>`.
        let wanted = wanted.filter(|&t| {
            let types = &self.typing.types;
            elements.is_empty() || types.builtin_name(types.base(self.non_null(t))) == Some("List")
        });
        let element = match wanted.and_then(|t| self.wanted_element(t)) {
            Some(x)
                if (types.iter()).all(|&t| self.typing.types.is_subtype(t, x) == Some(true)) =>
            {
                x
            }
            Some(_) => self.common_type(&types),
            None => self.common_element(elements, &types),
        };
        Ok(self.typing.types.list_of(element))
    }

    /// Elements' common type, where nothing says what's wanted; `[]`s
    /// among lists (`[[], ["a"]]`) are lists of the others' element type,
    /// as Kotlin Temper's solver finds, typed so.
    pub(super) fn common_element(&mut self, elements: &[NodeId], types: &[TypeId]) -> TypeId {
        let empty = |s: &Self, e: NodeId| s.is_list_literal(e) && s.lib.tree.children(e).len() == 1;
        let others: Vec<TypeId> = (elements.iter().zip(types))
            .filter(|&(&e, _)| !empty(self, e))
            .map(|(_, &t)| t)
            .collect();
        if others.len() == types.len() || others.is_empty() {
            return self.common_type(types);
        }
        let common = self.common_type(&others);
        let Some(x) = self.wanted_element(common) else {
            return self.common_type(types);
        };
        let list = self.typing.types.list_of(x);
        if self.typing.types.is_subtype(list, common) != Some(true) {
            return self.common_type(types);
        }
        let empties: Vec<NodeId> = (elements.iter().copied())
            .filter(|&e| empty(self, e))
            .collect();
        for e in empties {
            self.typing.set_expr(e, list);
        }
        common
    }

    /// Whether a node is `[]`, a list literal without elements.
    pub(super) fn is_empty_list(&self, node: NodeId) -> bool {
        self.is_list_literal(node) && self.lib.tree.children(node).len() == 1
    }

    /// Whether a value is `[]`, or a block whose value is.
    fn ends_in_empty_list(&self, node: NodeId) -> bool {
        let tree = &self.lib.tree;
        match tree.kind(node) {
            NodeKind::Block => {
                (tree.children(node).last()).is_some_and(|&l| self.ends_in_empty_list(l))
            }
            _ => self.is_list_literal(node) && tree.children(node).len() == 1,
        }
    }

    /// The element type of a list where a `wanted` is wanted, if it's a
    /// `List` or `Listed` (maybe nullable) whose element type is known.
    pub(super) fn wanted_element(&self, wanted: TypeId) -> Option<TypeId> {
        let wanted = self.non_null(wanted);
        let types = &self.typing.types;
        let base = types.builtin_name(types.base(wanted));
        match types.args(wanted) {
            &[x] if matches!(base, Some("List" | "Listed")) && types.is_known(x) => Some(x),
            _ => None,
        }
    }

    /// List elements' common type, as Kotlin Temper's solver finds one
    /// where tempest can be sure it's the same: their type if they're all
    /// one, made nullable for null or a nullable one, or the one the
    /// others are subtypes of, as `Animal` for a `Dog` and an `Animal`, or
    /// their one least common supertype ([`crate::types::Types::join`]),
    /// as `Animal` for a `Dog` and a `Cat`. A `Never`, as `panic()`,
    /// counts for nothing. Unknown where it's anything else, for elements
    /// that are only null or `Never`, or none (Kotlin: `AnyValue`), and
    /// for a `Void` element, an error itself.
    fn common_type(&mut self, elements: &[TypeId]) -> TypeId {
        let mut common = NEVER;
        for &t in elements {
            if t == VOID || !self.typing.types.is_known(t) {
                return UNKNOWN;
            }
            let joined = self.typing.types.join(common, t);
            common = match joined {
                UNKNOWN => {
                    let (a, b) = (self.non_null(common), self.non_null(t));
                    let types = &mut self.typing.types;
                    let wider = if types.is_subtype(a, b) == Some(true) {
                        b
                    } else if types.is_subtype(b, a) == Some(true) {
                        a
                    } else {
                        return UNKNOWN;
                    };
                    match a != common || b != t {
                        true => types.nullable(wider),
                        false => wider,
                    }
                }
                _ => joined,
            };
        }
        match common {
            NEVER | NULL => UNKNOWN,
            _ => common,
        }
    }

    /// The type a value's place wants, if it says: a declaration's,
    /// field's, or parameter's declared type, for its initializer or
    /// default; a local's, for a value assigned to it, or a member's
    /// setter's; a function's declared result, for what it returns or
    /// its body gives as its result, through blocks and the branches of
    /// `if` and `orelse`, or for a function literal without one, the
    /// result its place wants; a known callee's parameter, for an
    /// argument, with a trailing block placed ([`Settle::placed_params`]),
    /// but not a generic function's or class's, except for a function
    /// literal ([`Settle::wanted_literal_arg`]) and a builtin type's
    /// constructor given its type arguments ([`Settle::builtin_new_params`]);
    /// an object literal's
    /// parameter, once its class is decided, for its value, or its
    /// temporary's use, for a value held in one; and the element type of
    /// a list wanted, for a list literal's element.
    pub(super) fn wanted_type(
        &mut self,
        node: NodeId,
        forced: bool,
    ) -> Result<Option<TypeId>, Fact> {
        let Some(parent) = self.typing.parent(node) else {
            return Ok(None);
        };
        let tree = &self.lib.tree;
        let children = tree.children(parent);
        // None where the parent has since become something else, as an
        // object literal that's an error.
        let Some(index) = children.iter().position(|&c| c == node) else {
            return Ok(None);
        };
        let is_last = index + 1 == children.len();
        let other_branch = (tree.kind(parent) == NodeKind::If && matches!(index, 1 | 2))
            .then(|| children.get(3 - index).copied())
            .flatten();
        Ok(match tree.kind(parent) {
            NodeKind::Block if is_last => self.wanted_type(parent, forced)?,
            // An `if`'s branches, after its condition, and an `orelse`'s.
            // With nothing wanted, a branch that's `[]` wants the other's
            // type, as Kotlin Temper's solver finds: `if (c) { xs } else
            // { [] }`.
            NodeKind::If if matches!(index, 1 | 2) => match self.wanted_type(parent, forced)? {
                None if self.ends_in_empty_list(node) => match other_branch {
                    Some(other) if !self.ends_in_empty_list(other) => {
                        Some(self.expr_type(other, forced)?)
                            .filter(|&t| self.wanted_element(t).is_some())
                    }
                    _ => None,
                },
                wanted => wanted,
            },
            NodeKind::Orelse => self.wanted_type(parent, forced)?,
            NodeKind::Fn if core::fn_parts(tree, parent).2 == Some(node) => {
                self.fn_result(parent, forced)?
            }
            NodeKind::Return => self.fn_result(NodeId(tree.data(parent)), forced)?,
            NodeKind::Let => {
                let (_, ty, init) = core::let_parts(tree, parent);
                // An object literal's value, held in a temporary.
                if let (None, Some(&used)) = (ty, self.typing.literal_temporaries.get(&parent)) {
                    return self.wanted_type(used, forced);
                }
                let is_type_param = tree.data(parent) & ROLE_MASK == ROLE_TYPE_PARAM;
                (ty.filter(|_| init == Some(node) && !is_type_param)).map(|ty| self.eval_type(ty))
            }
            NodeKind::Assign if is_last && children.len() == 2 => {
                let target = children[0];
                match self.local_target(target) {
                    Some(decl) => Some(self.decl_type(decl, forced)?),
                    None => Some(self.member_target_type(target)),
                }
            }
            NodeKind::Call if index > 0 => self.wanted_arg(parent, index - 1, forced)?,
            _ => None,
        })
    }

    /// The type a call's `index`th argument's place wants, as
    /// [`Settle::wanted_type`] says.
    fn wanted_arg(
        &mut self,
        call: NodeId,
        index: usize,
        forced: bool,
    ) -> Result<Option<TypeId>, Fact> {
        if self.literal_undecided(call) {
            return if forced {
                Ok(None)
            } else {
                Err(Fact::ObjectLiteral(call))
            };
        }
        let children = self.lib.tree.children(call).to_vec();
        let args = &children[1..];
        if self.scopes.resolved_builtin(children[0]) == Some(Builtin::Function(Function::List)) {
            let wanted = self.wanted_type(call, forced)?;
            return Ok(wanted.and_then(|t| self.wanted_element(t)));
        }
        if let Some((type_args, _)) = self.list_of_parts(call) {
            if let [t] = *type_args {
                return Ok(Some(self.eval_type(t)));
            }
            let wanted = self.wanted_type(call, forced)?;
            return Ok(wanted.and_then(|t| self.wanted_element(t)));
        }
        // Among the values passed, past any type arguments.
        let arg = args[index];
        let Some((_, values)) = self.split_type_args(args) else {
            return Ok(None);
        };
        let Some(index) = values.iter().position(|&v| v == arg) else {
            return Ok(None);
        };
        let is_literal = self.lib.tree.kind(arg) == NodeKind::Fn;
        // A list literal, as `[]` or `[new Sub()]` where a `List<Super<T>>`
        // is wanted, takes a generic function's type parameters as solved
        // from all the arguments, as a function literal does.
        let list = self.is_list_literal(arg);
        if list && !is_literal && self.typing.solving_for_lists.contains(&call) {
            return Ok(None);
        }
        let solve = is_literal || list;
        if list {
            self.typing.solving_for_lists.push(call);
        }
        let params = self.arg_params(call, solve, forced);
        if list {
            self.typing.solving_for_lists.pop();
        }
        let Some((params, skip)) = params? else {
            return Ok(None);
        };
        let Some(index) = index.checked_sub(skip) else {
            return Ok(None);
        };
        let placed = self.placed_params(&params, &values[skip..]);
        let t = params.get(placed[index]).map(|p| p.ty);
        Ok(t.filter(|&t| is_literal || self.typing.types.is_known(t)))
    }

    /// The parameters a call's arguments go to, as [`Settle::wanted_arg`]
    /// finds them, with how many of the values it passes, without its
    /// type arguments ([`Settle::split_type_args`]), come before the first
    /// of them: one for a constructor's class, else none. With `literal`,
    /// for a function literal argument, a generic function's are solved
    /// from the call's type arguments, other arguments, and place
    /// ([`Settle::wanted_literal_arg`]). `None` for a builtin's call, other
    /// than a constructor's, and for a call with named arguments.
    fn arg_params(
        &mut self,
        call: NodeId,
        literal: bool,
        forced: bool,
    ) -> Result<Option<(Vec<Param>, usize)>, Fact> {
        let children = self.lib.tree.children(call).to_vec();
        let (callee, args) = (children[0], &children[1..]);
        if self.split_type_args(args).is_none() {
            return Ok(None);
        }
        Ok(match self.scopes.resolved_builtin(callee) {
            // Not a generic class's, solved per call, unless it's a builtin
            // type given its type arguments.
            Some(Builtin::Function(Function::New)) => match self.new_class(args) {
                Some(class) => Some((self.constructor_type(class), 1)),
                None => match self.builtin_new_params(args) {
                    Some(params) => Some((params, 1)),
                    // A generic class's, solved, for a literal.
                    None if literal => (self.wanted_literal_arg(call, forced)?).map(|p| (p, 1)),
                    None => None,
                },
            },
            Some(_) => None,
            None if literal => match self.wanted_literal_arg(call, forced)? {
                Some(params) => Some((params, 0)),
                None => Some((self.callee_params(callee, forced)?, 0)),
            },
            // A generic function's own type parameters are unknown here,
            // as it's solved per call.
            None => Some((self.callee_params(callee, forced)?, 0)),
        })
    }

    /// Records where a call's trailing block goes, if typing places it
    /// other than last ([`Settle::placed_params`]), for the interpreter
    /// ([`crate::typing::Typing::trailing_blocks`]), once typing is done.
    pub(super) fn record_trailing_block(&mut self, call: NodeId) {
        let tree = &self.lib.tree;
        let children = tree.children(call);
        if children.len() < 2 || tree.kind(children[children.len() - 1]) != NodeKind::Fn {
            return;
        }
        let Ok(Some((params, skip))) = self.arg_params(call, true, false) else {
            return;
        };
        let Some((_, values)) = self.split_type_args(&self.lib.tree.children(call)[1..]) else {
            return;
        };
        let args = &values[skip..];
        let at = self.placed_params(&params, args).last().copied();
        if let Some(at) = at.filter(|&at| at + 1 != args.len()) {
            self.typing.trailing_blocks.insert(call, skip + at);
        }
    }

    /// The parameters of what a callee calls, if it's a function.
    fn callee_params(&mut self, callee: NodeId, forced: bool) -> Result<Vec<Param>, Fact> {
        let t = self.callee_type(callee, forced)?;
        Ok(match self.typing.types.get(t) {
            TypeData::Fn { params, .. } => params.clone(),
            _ => Vec::new(),
        })
    }

    /// Where each of a call's arguments goes among its callee's
    /// parameters `params`, by index: in order, but a last argument that's
    /// a function literal, as a trailing block, goes to the last parameter
    /// of a function type or a `@fun` interface, maybe nullable, and the
    /// arguments after that parameter's place move up one, as Kotlin
    /// Temper's `applicationOrderForActuals` places them, so `f(1) { … }`
    /// fits `let f(g: fn (): Void, n: Int)`, and `f(1) { … }` fits `let
    /// f(n: Int, opts: Opts = …, g: fn (): Void)` too, with nothing for
    /// `opts`.
    /// Only a required parameter takes it, unless the other arguments
    /// cover them all, and none where there are more arguments than
    /// parameters. The interpreter passes them as placed
    /// ([`Settle::record_trailing_block`]).
    pub(super) fn placed_params(&self, params: &[Param], args: &[NodeId]) -> Vec<usize> {
        let mut placed: Vec<usize> = (0..args.len()).collect();
        let Some(&last) = args.last() else {
            return placed;
        };
        if self.lib.tree.kind(last) != NodeKind::Fn || args.len() > params.len() {
            return placed;
        }
        let required = params.iter().filter(|p| !p.optional).count();
        let any_optional = args.len() > required;
        let types = &self.typing.types;
        let at = params.iter().rposition(|p| {
            types.is_functional(self.non_null(p.ty)) && (any_optional || !p.optional)
        });
        if let Some(at) = at.filter(|&at| at + 1 != args.len()) {
            for (i, place) in placed.iter_mut().enumerate().skip(at) {
                *place = i + 1;
            }
            placed[args.len() - 1] = at;
        }
        placed
    }

    /// The function type a function literal's place wants, if it wants
    /// one, or a `@fun` interface, which stands for one, and the literal
    /// takes its types from it: not a generator's factory. A `for … of`
    /// body takes its loop variable's type from what `forEach` wants.
    pub(super) fn wanted_fn(&mut self, f: NodeId, forced: bool) -> Result<Option<TypeId>, Fact> {
        let tree = &self.lib.tree;
        if !core::fn_supers(tree, f).is_empty() {
            return Ok(None);
        }
        let Some(wanted) = self.wanted_type(f, forced)? else {
            return Ok(None);
        };
        let wanted = self.non_null(wanted);
        Ok(self.typing.types.as_fn(wanted))
    }

    /// A function's type, with what its place wants for what it doesn't
    /// declare, as Kotlin Temper types a function literal: a parameter
    /// without a type takes the wanted function type's parameter in its
    /// place, or `AnyValue` past its last, and a function without a
    /// return type takes the wanted one's result, and whether it may
    /// bubble.
    pub(super) fn literal_type(&mut self, f: NodeId, forced: bool) -> Result<TypeId, Fact> {
        let t = self.fn_type(f);
        // `doPure { … }`'s block, without a result type, gives its result.
        let declares = core::fn_parts(&self.lib.tree, f).1.is_some();
        if let Some(call) = self.typing.parent(f).filter(|_| !declares) {
            let tree = &self.lib.tree;
            let callee = tree.children(call)[0];
            let is_pure = tree.kind(call) == NodeKind::Call
                && tree.children(call).len() == 2
                && self.scopes.resolved_builtin(callee)
                    == Some(Builtin::Function(Function::DoPure));
            if let (true, Some(body)) = (is_pure, core::fn_parts(tree, f).2) {
                let ret = self.expr_type(body, forced)?;
                return Ok(self.typing.types.intern(TypeData::Fn {
                    params: Vec::new(),
                    ret,
                    throws: false,
                }));
            }
        }
        let Some(wanted) = self.wanted_fn(f, forced)? else {
            return Ok(t);
        };
        let types = &self.typing.types;
        let (
            TypeData::Fn {
                params,
                ret,
                throws,
            },
            TypeData::Fn {
                params: wanted_params,
                ret: wanted_ret,
                throws: wanted_throws,
            },
        ) = (types.get(t).clone(), types.get(wanted).clone())
        else {
            return Ok(t);
        };
        let tree = &self.lib.tree;
        let (decls, out_type, _) = core::fn_parts(tree, f);
        let params = (params.into_iter().zip(decls).enumerate())
            .map(|(i, (param, decl))| match self.is_untyped_param(decl) {
                true => Param {
                    ty: wanted_params.get(i).map_or(ANY_VALUE, |w| w.ty),
                    optional: param.optional,
                },
                false => param,
            })
            .collect();
        let (ret, throws) = match out_type {
            Some(_) => (ret, throws),
            None => (wanted_ret, wanted_throws),
        };
        Ok(self.typing.types.intern(TypeData::Fn {
            params,
            ret,
            throws,
        }))
    }

    /// Whether a function's parameter declares no type.
    fn is_untyped_param(&self, decl: NodeId) -> bool {
        let tree = &self.lib.tree;
        tree.kind(decl) == NodeKind::Let && core::let_parts(tree, decl).1.is_none()
    }

    /// The type of a parameter without a declared type, `decl`, as its
    /// function's place has it ([`Settle::literal_type`]), if it's a
    /// parameter of a function whose place wants a function type.
    pub(super) fn untyped_param_type(
        &mut self,
        decl: NodeId,
        forced: bool,
    ) -> Result<Option<TypeId>, Fact> {
        let Some(f) = (self.typing.parent(decl)).filter(|&f| self.lib.tree.kind(f) == NodeKind::Fn)
        else {
            return Ok(None);
        };
        let Some(i) = core::fn_parts(&self.lib.tree, f)
            .0
            .iter()
            .position(|&p| p == decl)
        else {
            return Ok(None);
        };
        let Some(wanted) = self.wanted_fn(f, forced)? else {
            return Ok(None);
        };
        Ok(match self.typing.types.get(wanted) {
            TypeData::Fn { params, .. } => Some(params.get(i).map_or(ANY_VALUE, |p| p.ty)),
            _ => None,
        })
    }

    /// A function's result type: its declared one, unless it's a
    /// generator's, or for one without, as a function literal, the
    /// result of the function type its place wants.
    pub(super) fn fn_result(&mut self, f: NodeId, forced: bool) -> Result<Option<TypeId>, Fact> {
        if core::fn_parts(&self.lib.tree, f).1.is_some() {
            return Ok(self.declared_result(f));
        }
        let wanted = self.wanted_fn(f, forced)?;
        Ok(wanted.and_then(|t| match self.typing.types.get(t) {
            TypeData::Fn { ret, .. } => Some(*ret),
            _ => None,
        }))
    }
}
