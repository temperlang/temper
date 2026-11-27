//! Calls of generic functions, solved per call (notes/typing.md, "Generic
//! calls"): `id(1)` for `let id<T>(x: T): T` binds `T` to `Int32` from
//! its argument, and `id<Int>(x)` binds it as written, so the call is an
//! `Int32`, and its arguments and `T`'s bound are checked as Kotlin
//! Temper's solver reports them. A generic class's constructor is solved
//! the same way, as a function `<T>(T) -> Box<T>`, so `new Box(1)` is a
//! `Box<Int32>`, and so is a generic method, `o.first(xs)` for
//! `first<T>(items: List<T>): T`, with its class's type parameters as
//! the receiver binds them, or a builtin type's, as `ls.map(f)` for
//! core's `Listed.map<O>`. Only calls naming a function declared by
//! `let`, a generic class, or a method are solved; the rest see a generic
//! function's own type parameters as unknown. A list literal's elements
//! are checked against the element type wanted as Kotlin's solver checks
//! them, as a call of `<listT>(listT, …) -> List<listT>`.

use crate::builtins::{Builtin, Function};
use crate::core::{self, CLASS_INTERFACE};
use crate::library::{Level, Message};
use crate::scope::{SymbolId, SymbolKind};
use crate::settle::{Fact, Settle};
use crate::types::{
    ANY_VALUE, NULL, Param, Solution, TypeData, TypeId, UNKNOWN, VOID, show_result,
};
use temper_syntax::tree::{NodeId, NodeKind, Span};

/// A call of a generic function, solved.
#[derive(Clone)]
pub(super) struct Solved {
    /// The function's signature, with its type parameters as written.
    sig: TypeId,
    /// Its type parameters.
    formals: Vec<SymbolId>,
    /// What each type parameter binds to at this call, unknown where the
    /// call doesn't say, or has too many type arguments.
    pub(super) actuals: Vec<TypeId>,
    /// A type parameter its arguments bind two ways whose join goes above
    /// a type core marks `@mayDowncastTo(false)`, with those two types
    /// ([`crate::types::Types::unify`]).
    clash: Option<(usize, TypeId, TypeId)>,
    /// Which type parameters an argument whose type isn't known, as an
    /// error leaves one, may bind; they stay unknown rather than take
    /// their bound ([`Settle::bind_unsolved`]).
    blocked: Vec<bool>,
    /// Each type parameter that only null is passed for, where a bare
    /// `T` is wanted, and no type argument gives; it binds to what it's
    /// solved as, made nullable, so the call still translates, and null
    /// is reported as not one ([`Settle::check_generic_args`]).
    nulled: Vec<bool>,
    /// The call.
    call: NodeId,
    /// The call's arguments, without type arguments.
    args: Vec<NodeId>,
    /// For an instance's method, the receiver's type, as
    /// [`super::members::GenericMethod::this`] says.
    this: Option<(TypeId, TypeId)>,
    /// How many type arguments the call has, as one in `id<Int>(x)`.
    type_args: usize,
}

impl Solved {
    /// Whether the call's type arguments give each of its type
    /// parameters, as `id<Int>(x)`, or there are too many.
    fn has_type_args(&self) -> bool {
        self.type_args >= self.formals.len() && self.type_args > 0
    }

    /// Whether the call has more type arguments than type parameters.
    fn wrong_type_arity(&self) -> bool {
        self.type_args > self.formals.len()
    }
}

impl Settle {
    /// Solves a call of a generic function declared by `let`, as `id(1)`
    /// or `id<Int>(x)`: from its type arguments, if there are no more than
    /// the function has type parameters (else not at all), unless Kotlin
    /// Temper would fold the call; those they leave, the trailing ones, as
    /// `B` in `pair<Int>(1, "s")`, from its arguments' types
    /// ([`crate::types::Types::unify`]), one solved two different ways
    /// bound to their common supertype, as Kotlin's solver binds it, so
    /// `new Two(1, "s")` for `class Two<T>(a: T, b: T)` is a
    /// `Two<AnyValue>`. A function literal argument binds by the types it
    /// declares, if it declares its result's, as `ls.map { (x): Int => -x
    /// }` binds `O` to `Int32` (one that doesn't isn't used yet); the
    /// arguments are matched against the parameters they're placed at, as a
    /// trailing block moves them ([`Settle::placed_params`]). `None` if
    /// the call isn't of such a function, nor of a generic class's
    /// constructor ([`Settle::generic_new`]).
    pub(super) fn solve_call(
        &mut self,
        call: NodeId,
        forced: bool,
    ) -> Result<Option<Solved>, Fact> {
        let children = self.lib.tree.children(call).to_vec();
        let Some((&callee, args)) = children.split_first() else {
            return Ok(None);
        };
        let mut this = None;
        let (sig, formals, type_args, args, foldable) = match self.generic_fn(callee) {
            Some(f) => {
                let Some((type_args, args)) = self.split_type_args(args) else {
                    return Ok(None);
                };
                (
                    self.fn_signature(f),
                    self.own_formals(f),
                    type_args,
                    args,
                    true,
                )
            }
            None if self.scopes.resolved_builtin(callee).is_none() => {
                let method = match self.generic_method(callee, forced)? {
                    Some(method) => Some(method),
                    None => self.core_generic_method(callee, forced)?,
                };
                let (Some(method), Some((type_args, args))) = (method, self.split_type_args(args))
                else {
                    return Ok(None);
                };
                this = method.this;
                (method.sig, method.formals, type_args, args, false)
            }
            None => match self.generic_new(callee, args) {
                Some((class, type_args)) => {
                    let formals = self.own_formals(class);
                    let params = self.constructor_type(class);
                    let made = formals.iter().map(|&f| self.type_param_type(f)).collect();
                    let ret = self.generic_class_type(class, made);
                    let sig = self.typing.types.intern(TypeData::Fn {
                        params,
                        ret,
                        throws: false,
                    });
                    (sig, formals, type_args, args[1..].to_vec(), false)
                }
                None if self.scopes.resolved_builtin(callee)
                    != Some(Builtin::Function(Function::New)) =>
                {
                    return Ok(None);
                }
                None => match self.core_generic_new(args) {
                    Some((method, type_args)) => {
                        let values = self.split_type_args(&args[1..]).map(|(_, v)| v);
                        let Some(values) = values else {
                            return Ok(None);
                        };
                        (method.sig, method.formals, type_args, values, false)
                    }
                    None => return Ok(None),
                },
            },
        };
        let TypeData::Fn { params, .. } = self.typing.types.get(sig).clone() else {
            return Ok(None);
        };
        let mut solution = Solution::new(formals.len());
        let mut blocked = vec![false; formals.len()];
        let mut nulled = vec![false; formals.len()];
        // Kotlin Temper folds a call of constants to its value before
        // checking it, so its type arguments go unchecked: `let s: String
        // = id<Int>("a")` passes, as the value is a `String`; they give
        // only what the arguments leave unknown, as `B` in `make<Int,
        // String>(1)` for `make<A, B>(a: A): P<A, B?>`. It doesn't fold
        // a constructor's call.
        let folds = foldable && self.may_fold(call, 0);
        let given = if folds { &[][..] } else { &type_args[..] };
        if given.len() < formals.len() {
            let placed = self.placed_params(&params, &args);
            for (&arg, &i) in args.iter().zip(&placed) {
                let Some(param) = params.get(i) else {
                    continue;
                };
                // A function literal binds only by what it declares: its
                // result, and its parameters' types, if any.
                let mut apart = None;
                let list = self.is_list_literal(arg);
                let t = match self.lib.tree.kind(arg) {
                    NodeKind::Fn if !self.declares_result(arg) => continue,
                    // `[]` takes its type from the solution
                    // ([`Settle::wanted_type`]), and another list literal
                    // binds by its elements' common type, as its own type
                    // is what the solution wants.
                    _ if self.is_empty_list(arg) => continue,
                    _ if list => {
                        let elements = self.lib.tree.children(arg)[1..].to_vec();
                        let mut types = Vec::with_capacity(elements.len());
                        for &element in &elements {
                            types.push(self.expr_type(element, forced)?);
                        }
                        apart = self.common_type_apart(&types).1;
                        let element = self.common_element(&elements, &types);
                        self.typing.types.list_of(element)
                    }
                    NodeKind::Fn => self.fn_type(arg),
                    _ => self.expr_type(arg, forced)?,
                };
                let types = &mut self.typing.types;
                if !types.is_known(t) {
                    for (b, &formal) in blocked.iter_mut().zip(&formals) {
                        *b |= types.mentions(param.ty, &[formal]);
                    }
                }
                if t == NULL {
                    if let TypeData::Param(symbol) = *types.get(param.ty) {
                        let k = formals.iter().position(|&f| f == symbol);
                        k.into_iter().for_each(|k| nulled[k] = true);
                    }
                }
                if !list {
                    types.unify(param.ty, t, &formals, &mut solution);
                    continue;
                }
                types.unify_list_literal(param.ty, t, &formals, &mut solution);
                // Elements that can't be told apart once joined, as in
                // `["a", 3]`, are a clash of what each would bind, as
                // `List<String>` and `List<Int32>` for a whole `T`.
                if let Some((a, b)) = apart {
                    let (a, b) = (types.list_of(a), types.list_of(b));
                    let (mut x, mut y) =
                        (Solution::new(formals.len()), Solution::new(formals.len()));
                    types.unify_list_literal(param.ty, a, &formals, &mut x);
                    types.unify_list_literal(param.ty, b, &formals, &mut y);
                    let k = (0..formals.len()).find(|&k| x.actuals[k] != y.actuals[k]);
                    if let Some(k) = k {
                        solution
                            .clash
                            .get_or_insert((k, x.actuals[k], y.actuals[k]));
                    }
                }
            }
            // Null alone doesn't bind one, which Kotlin reports where it's
            // passed for a bare `T` ([`Settle::check_generic_args`]).
            for (k, actual) in solution.actuals.iter_mut().enumerate() {
                nulled[k] &= *actual == NULL;
                if *actual == NULL {
                    *actual = UNKNOWN;
                }
            }
        }
        let Solution {
            mut actuals,
            mut clash,
            ..
        } = solution;
        if given.len() <= formals.len() {
            for (actual, &t) in actuals.iter_mut().zip(given) {
                *actual = self.eval_type(t);
            }
        }
        // Type arguments are never limited, even where Kotlin would fold
        // the call and ignore them.
        clash = clash.filter(|&(k, ..)| k >= type_args.len());
        for k in 0..type_args.len().min(nulled.len()) {
            nulled[k] = false;
        }
        if folds && type_args.len() <= formals.len() {
            for (actual, &t) in actuals.iter_mut().zip(&type_args) {
                if *actual == UNKNOWN {
                    *actual = self.eval_type(t);
                }
            }
        }
        Ok(Some(Solved {
            sig,
            formals,
            actuals,
            clash,
            blocked,
            nulled,
            call,
            args,
            this,
            type_args: type_args.len(),
        }))
    }

    /// A solved call's type: its function's result with the solution
    /// put in, unknown where it isn't solved.
    pub(super) fn solved_result(&mut self, solved: &Solved) -> TypeId {
        let TypeData::Fn { ret, .. } = *self.typing.types.get(solved.sig) else {
            return UNKNOWN;
        };
        let types = &mut self.typing.types;
        types.substitute(ret, &solved.formals, &solved.actuals)
    }

    /// Whether a function literal declares its result type, and isn't a
    /// generator's.
    fn declares_result(&self, f: NodeId) -> bool {
        let tree = &self.lib.tree;
        core::fn_parts(tree, f).1.is_some() && core::fn_supers(tree, f).is_empty()
    }

    /// Binds the type parameters a solved call's arguments leave
    /// unknown, or bind in a clash, by the type its place wants, where
    /// the call's type arguments don't give them, as Kotlin Temper's
    /// solver binds them: `new ListBuilder()` where a `ListBuilder<Int>`
    /// is wanted is one, and so is `new Two(1, "s")` where a
    /// `Two<AnyValue>` is.
    pub(super) fn bind_wanted(&mut self, solved: &mut Solved, forced: bool) -> Result<(), Fact> {
        let t = self.solved_result(solved);
        if (self.typing.types.is_known(t) && solved.clash.is_none()) || solved.has_type_args() {
            return Ok(());
        }
        let Some(wanted) = self.wanted_type(solved.call, forced)? else {
            self.literal_results_as_bounds(solved);
            return Ok(());
        };
        let TypeData::Fn { ret, .. } = *self.typing.types.get(solved.sig) else {
            return Ok(());
        };
        let wanted = match (self.typing.types.get(wanted), self.typing.types.get(ret)) {
            (&TypeData::Nullable(inner), ret) if !matches!(ret, TypeData::Nullable(_)) => inner,
            _ => wanted,
        };
        let bindings = self.wanted_bindings(ret, wanted, &solved.formals);
        if let Some((k, ..)) = solved.clash.filter(|&(k, ..)| bindings[k] != UNKNOWN) {
            solved.actuals[k] = UNKNOWN;
            solved.clash = None;
        }
        for (actual, b) in solved.actuals.iter_mut().zip(bindings) {
            if *actual == UNKNOWN {
                *actual = b;
            }
        }
        self.literal_results_as_bounds(solved);
        Ok(())
    }

    /// Binds each type parameter still unknown that a function literal
    /// argument without a declared result would give, its parameter's
    /// result, to its bound, as Kotlin Temper does: `ls.map { x => x * x
    /// }`, with nothing wanted, is a `List<AnyValue>`, and the literal a
    /// `fn (Int32): AnyValue` (notes/typing.md, "Function literals and
    /// target typing").
    fn literal_results_as_bounds(&mut self, solved: &mut Solved) {
        if solved.has_type_args() {
            return;
        }
        let TypeData::Fn { params, .. } = self.typing.types.get(solved.sig).clone() else {
            return;
        };
        let placed = self.placed_params(&params, &solved.args);
        let mut open = vec![false; solved.formals.len()];
        for (&arg, &i) in solved.args.iter().zip(&placed) {
            let literal = self.lib.tree.kind(arg) == NodeKind::Fn && !self.declares_result(arg);
            let Some(param) = params.get(i).filter(|_| literal) else {
                continue;
            };
            let ty = self.non_null(param.ty);
            let TypeData::Fn { ret, .. } = *self.typing.types.get(ty) else {
                continue;
            };
            for (k, &formal) in solved.formals.iter().enumerate() {
                open[k] |= self.typing.types.mentions(ret, &[formal]);
            }
        }
        let bounds = self.unsolved_as_bounds(solved);
        for (k, bound) in bounds.into_iter().enumerate() {
            if open[k] && solved.actuals[k] == UNKNOWN {
                solved.actuals[k] = bound;
            }
        }
    }

    /// Checks a call of a generic function, as Kotlin Temper's solver
    /// does, at its positions: a type parameter its arguments bind two
    /// ways whose join would go above a type core marks
    /// `@mayDowncastTo(false)`, "T is bound to both Int32 and String, which
    /// can't be told apart once joined; give T explicitly", at the call,
    /// which Kotlin accepts; a type parameter bound to a type outside
    /// its bound, "Type formal <T extends Animal> cannot bind to Int32
    /// which does not fit upper bounds [Animal]", at the call; the wrong
    /// number of arguments, "No callee matches inputs [Int32] among [<T
    /// extends AnyValue>(T, Int32) -> T]", at the call; and arguments
    /// that don't fit the solved signature, "Actual arguments do not match
    /// signature: <T extends AnyValue>(T, Int32) -> T expected [String,
    /// Int32], but got [Int32, String]", from the first that doesn't to
    /// the last. And more type arguments than type parameters, "Wrong
    /// number of type arguments.  Expected 3", at the call, which Kotlin
    /// misses; its type parameters stay unknown. Returns
    /// whether the call was of a generic function.
    pub(super) fn check_generic_call(&mut self, call: NodeId) -> bool {
        let Ok(Some(mut solved)) = self.solve_call(call, false) else {
            return false;
        };
        // Checked against the solution that types the call, with what its
        // arguments leave unknown bound by the type wanted, as `T` in `let
        // b: Box<List<String>> = new Box(["a", 3])`.
        if self.bind_wanted(&mut solved, false).is_err() {
            return true;
        }
        let span = self.lib.tree.span(call);
        if solved.wrong_type_arity() {
            let message = Message::TypeArityMismatch(solved.formals.len());
            self.lib.report(Level::Error, message, span);
            return true;
        }
        if let Some((k, a, b)) = solved.clash {
            let formal = self.formal_type(solved.formals[k]);
            let message = Message::JoinedApart(self.show(formal), self.show(a), self.show(b));
            self.lib.report(Level::Error, message, span);
            return true;
        }
        if self.check_unbindable(&solved) {
            return true;
        }
        for (&formal, &actual) in solved.formals.iter().zip(&solved.actuals) {
            let param = self.formal_type(formal);
            let types = &mut self.typing.types;
            let bound = types.upper(param);
            // A bound may name the call's own type parameters, as `K
            // extends MapKey<K>` does, so it's checked with what they're
            // bound to put in; the message shows it as declared.
            let wanted = types.substitute(bound, &solved.formals, &solved.actuals);
            // Kotlin's check takes a nullable type as fitting `AnyValue`,
            // so `new PromiseBuilder<String?>()` and `f<Int?>(null)` for
            // `<T extends AnyValue>` (or `<T>`) pass.
            let seen = match *types.get(actual) {
                TypeData::Nullable(inner) if wanted == ANY_VALUE => inner,
                _ => actual,
            };
            let fits = types.is_subtype(seen, wanted) != Some(false);
            if fits || !types.is_known(wanted) {
                continue;
            }
            let message = Message::TypeFormalBound(
                self.show_formal(formal),
                self.show(actual),
                self.show(bound),
            );
            self.lib.report(Level::Error, message, span);
            return true;
        }
        self.check_generic_args(&solved);
        true
    }

    /// Reports a solved call with a type parameter that nothing binds and
    /// that can't take its bound, which names it, as `T` in `none()` for
    /// `none<T extends Ord<T>>(): List<T>`, as Kotlin Temper does: "Could
    /// not infer type actuals for <T extends Ord<T>>() -> List<T> given []
    /// in context unknown", at the call, with the type wanted as the
    /// context. Not one an argument whose type isn't known may bind.
    /// Returns whether it reported it.
    fn check_unbindable(&mut self, solved: &Solved) -> bool {
        let mut bound = solved.clone();
        self.bind_unsolved(&mut bound);
        let unbound = (bound.actuals.iter().zip(&bound.blocked)).any(|(&a, &b)| a == UNKNOWN && !b);
        if !unbound {
            return false;
        }
        let this = solved.this.map(|(_, actual)| actual);
        let args: Vec<TypeId> = solved.args.iter().map(|&a| self.known_type(a)).collect();
        let given: Vec<String> = this.iter().chain(&args).map(|&t| self.show(t)).collect();
        let context = match self.wanted_type(solved.call, false) {
            Ok(Some(t)) => self.show(t),
            _ => "unknown".to_string(),
        };
        let sig = self.show_generic_sig(solved);
        let message = Message::CouldNotInfer(sig, format!("[{}]", given.join(", ")), context);
        let span = self.lib.tree.span(solved.call);
        self.lib.report(Level::Error, message, span);
        true
    }

    /// Reports a generic call's arguments that don't fit what the type
    /// its place wants binds, as Kotlin Temper's solver does, where `let
    /// xs: List<String> = wrap(1)` is "Actual arguments do not match
    /// signature: <T extends AnyValue>(T) -> List<T> expected [String],
    /// but got [Int32]", at the argument, rather than an error at the
    /// place. Only where the call's result isn't a bare type parameter
    /// (`T` or `T?`), which Kotlin solves from the arguments, and for the
    /// type parameters the call's type arguments don't give. Returns
    /// whether it reported an error.
    pub(super) fn check_generic_wanted(&mut self, call: NodeId, wanted: TypeId) -> bool {
        let Ok(Some(mut solved)) = self.solve_call(call, false) else {
            return false;
        };
        let TypeData::Fn { ret, .. } = *self.typing.types.get(solved.sig) else {
            return false;
        };
        let types = &mut self.typing.types;
        let bare = match *types.get(ret) {
            TypeData::Nullable(inner) => matches!(types.get(inner), TypeData::Param(_)),
            TypeData::Param(_) => true,
            _ => false,
        };
        if bare || solved.has_type_args() {
            return false;
        }
        let mut any = false;
        let bindings = self.wanted_bindings(ret, wanted, &solved.formals);
        let given = solved.type_args;
        for (actual, b) in solved.actuals.iter_mut().zip(bindings).skip(given) {
            if b != UNKNOWN {
                *actual = b;
                any = true;
            }
        }
        any && self.check_generic_args(&solved)
    }

    /// What the type wanted where a generic call's value goes binds each
    /// of its type parameters to, its result `ret` matched against it part
    /// by part, unknown where it binds none.
    fn wanted_bindings(
        &mut self,
        ret: TypeId,
        wanted: TypeId,
        formals: &[SymbolId],
    ) -> Vec<TypeId> {
        let types = &mut self.typing.types;
        // `List<T>` where a `Listed<String>` is wanted is a `Listed<T>`.
        let ret = types.as_supertype(ret, wanted);
        let mut solution = Solution::new(formals.len());
        types.unify(ret, wanted, formals, &mut solution);
        solution.actuals
    }

    /// The parameters of a generic call that has a function literal
    /// argument, with what's solved put in, as Kotlin Temper types the
    /// literal once it has solved the call: each type parameter from the
    /// other arguments and type arguments ([`Settle::solve_call`]), and
    /// what they leave unknown, but for those type arguments give, from
    /// the type its place wants, which binds first where the call's
    /// result isn't a bare type parameter, as for
    /// [`Settle::check_generic_wanted`]; unknown where none of them says.
    /// `None` if the call isn't solved.
    pub(super) fn wanted_literal_arg(
        &mut self,
        call: NodeId,
        forced: bool,
    ) -> Result<Option<Vec<Param>>, Fact> {
        let Some(mut solved) = self.solve_call(call, forced)? else {
            return Ok(None);
        };
        let TypeData::Fn { params, ret, .. } = self.typing.types.get(solved.sig).clone() else {
            return Ok(None);
        };
        let wanted = match solved.has_type_args() {
            true => None,
            false => self.wanted_type(call, forced)?,
        };
        if let Some(wanted) = wanted {
            let types = &self.typing.types;
            let bare = matches!(types.get(self.non_null(ret)), TypeData::Param(_));
            let bindings = self.wanted_bindings(ret, wanted, &solved.formals);
            let given = solved.type_args;
            for (actual, b) in solved.actuals.iter_mut().zip(bindings).skip(given) {
                if b != UNKNOWN && (*actual == UNKNOWN || !bare) {
                    *actual = b;
                }
            }
        }
        self.literal_results_as_bounds(&mut solved);
        let types = &mut self.typing.types;
        Ok(Some(
            (params.iter())
                .map(|p| Param {
                    ty: types.substitute(p.ty, &solved.formals, &solved.actuals),
                    optional: p.optional,
                })
                .collect(),
        ))
    }

    /// Reports a list literal's elements that don't fit the element type
    /// of the list its place wants, as Kotlin Temper's solver does for
    /// what it sees as a call of `<listT extends AnyValue>(listT, listT)
    /// -> List<listT>`, so `let xs: List<String> = [1, "a"]` is "Actual
    /// arguments do not match signature: <listT extends AnyValue>(listT,
    /// listT) -> List<listT> expected [String, String], but got [Int32,
    /// String]", at the `1`. Returns whether it reported an error.
    pub(super) fn check_list_wanted(&mut self, list: NodeId, wanted: TypeId) -> bool {
        let Some(element) = self.wanted_element(wanted) else {
            return false;
        };
        let formal = match self.typing.list_formal {
            Some(formal) => formal,
            None => {
                let name = self.lib.names.intern("listT");
                let formal = self.scopes.unnamed_type_param(name);
                self.typing.types.set_bound(formal, ANY_VALUE);
                self.typing.list_formal = Some(formal);
                formal
            }
        };
        let args = self.lib.tree.children(list)[1..].to_vec();
        let types = &mut self.typing.types;
        let param = types.param(formal);
        let ret = types.list_of(param);
        let params = vec![
            Param {
                ty: param,
                optional: false,
            };
            args.len()
        ];
        let sig = types.intern(TypeData::Fn {
            params,
            ret,
            throws: false,
        });
        self.check_generic_args(&Solved {
            sig,
            formals: vec![formal],
            actuals: vec![element],
            clash: None,
            blocked: vec![false],
            nulled: vec![false],
            call: list,
            args,
            this: None,
            // The element type wanted gives `listT`, as a type argument
            // would, so a null that doesn't fit it misfits as any element
            // does, as Kotlin reports it.
            type_args: 1,
        })
    }

    /// Reports a solved call's arguments that don't fit its signature
    /// with the solution put in, as [`Settle::check_generic_call`] says.
    /// Returns whether it reported an error.
    fn check_generic_args(&mut self, solved: &Solved) -> bool {
        let span = self.lib.tree.span(solved.call);
        let TypeData::Fn { params, .. } = self.typing.types.get(solved.sig).clone() else {
            return false;
        };
        // A function literal only if it declares its result, as Kotlin
        // then checks it, and not yet where a trailing block moves.
        let literals = (solved.args.iter()).filter(|&&a| self.lib.tree.kind(a) == NodeKind::Fn);
        if literals.clone().next().is_some() {
            let placed = self.placed_params(&params, &solved.args);
            let in_place = placed.iter().enumerate().all(|(i, &p)| i == p);
            if !in_place || !literals.copied().all(|f| self.declares_result(f)) {
                return false;
            }
        }
        let arg_types: Vec<TypeId> = solved.args.iter().map(|&a| self.known_type(a)).collect();
        let required = params.iter().filter(|p| !p.optional).count();
        if !(required..=params.len()).contains(&solved.args.len()) {
            if arg_types.iter().all(|&t| self.typing.types.is_known(t)) {
                let this = solved.this.map(|(_, actual)| actual);
                let inputs: Vec<String> = (this.iter().chain(&arg_types))
                    .map(|&t| self.show(t))
                    .collect();
                let among = self.show_generic_sig(solved);
                let message = Message::NoCalleeMatches(inputs.join(", "), among);
                self.lib.report(Level::Error, message, span);
                return true;
            }
            return false;
        }
        // Kotlin's solver binds a type parameter that no argument binds to
        // its bound, so `ls.map(1)` wants a `fn (Int32): AnyValue`; but
        // tempest doesn't bind one for `Void`, which Kotlin reports
        // otherwise, nor for an argument whose type it doesn't know.
        let bounds = self.unsolved_as_bounds(solved);
        let plain = (arg_types.iter()).all(|&t| self.typing.types.is_known(t) && t != VOID);
        let checked: Vec<TypeId> = (0..bounds.len())
            .map(|i| match solved.actuals[i] {
                UNKNOWN if plain => bounds[i],
                actual => actual,
            })
            .collect();
        // Null passed for a bare `T`, which it doesn't bind, where `T` is
        // solved as a type that isn't nullable, its bound if nothing else
        // binds it: "Expected subtype of AnyValue, but got AnyValue?" for
        // `id(null)`, at the null, as Kotlin reports it. Not for `T` as
        // type arguments give it, which misfits as any other argument.
        let placed = self.placed_params(&params, &solved.args);
        let mut reported = false;
        let mut nulls = vec![false; solved.args.len()];
        for (i, &p) in placed.iter().enumerate() {
            let param = params.get(p).map(|p| self.typing.types.get(p.ty));
            let (NULL, Some(&TypeData::Param(symbol))) = (arg_types[i], param) else {
                continue;
            };
            let Some(k) = solved.formals.iter().position(|&f| f == symbol) else {
                continue;
            };
            let types = &mut self.typing.types;
            let t = checked[k];
            let nullable = matches!(types.get(t), TypeData::Nullable(_));
            if k < solved.type_args || !types.is_known(t) || nullable {
                continue;
            }
            let t_or_null = types.nullable(t);
            let message = Message::ExpectedSubType(self.show(t), self.show(t_or_null));
            let span = self.lib.tree.span(solved.args[i]);
            self.lib.report(Level::Error, message, span);
            (nulls[i], reported) = (true, true);
        }
        let wanted: Vec<TypeId> = (params.iter().take(solved.args.len()))
            .map(|p| {
                let types = &mut self.typing.types;
                types.substitute(p.ty, &solved.formals, &checked)
            })
            .collect();
        // A list literal whose elements have no common type, or none
        // that keeps them apart ([`Settle::list_apart`]), as `["a", 3]`,
        // is checked element by element ([`Settle::check_list_wanted`]).
        let mut done = nulls;
        for (i, &arg) in solved.args.iter().enumerate().take(wanted.len()) {
            let unknown = !self.typing.types.is_known(arg_types[i]);
            if !self.is_list_literal(arg) || !(unknown || self.list_apart(arg)) {
                continue;
            }
            if self.check_list_wanted(arg, wanted[i]) {
                (done[i], reported) = (true, true);
            }
        }
        let unfit: Vec<usize> = (0..wanted.len())
            .filter(|&i| !done[i] && self.misfits(arg_types[i], wanted[i], solved.args[i]))
            .collect();
        let (Some(&first), Some(&last)) = (unfit.first(), unfit.last()) else {
            return reported;
        };
        // A `@fun` interface's value where another's is wanted, alone.
        if let [i] = unfit[..] {
            let (arg, arg_span) = (solved.args[i], self.lib.tree.span(solved.args[i]));
            if self.check_fun_adapter(arg_types[i], wanted[i], arg, arg_span) {
                return true;
            }
        }
        let span = Span {
            start: self.lib.tree.span(solved.args[first]).start,
            end: self.lib.tree.span(solved.args[last]).end,
        };
        // Kotlin's solver gives `null` the type wanted, made nullable.
        let got: Vec<String> = (solved.this.map(|(_, actual)| self.show(actual)).into_iter())
            .chain((arg_types.iter().zip(&wanted)).map(|(&t, &w)| match t {
                NULL => {
                    let w = self.typing.types.nullable(w);
                    self.show(w)
                }
                _ => self.show(t),
            }))
            .collect();
        // It shows a type parameter it couldn't solve as its bound.
        let shown: Vec<TypeId> = (params.iter().take(solved.args.len()))
            .map(|p| (self.typing.types).substitute(p.ty, &solved.formals, &bounds))
            .collect();
        let expected: Vec<String> = (solved.this.map(|(declared, _)| self.show(declared)))
            .into_iter()
            .chain(shown.iter().map(|&t| self.show(t)))
            .collect();
        let message = Message::SignatureMismatch(
            self.show_generic_sig(solved),
            format!("[{}]", expected.join(", ")),
            format!("[{}]", got.join(", ")),
        );
        self.lib.report(Level::Error, message, span);
        true
    }

    /// A solved call's bindings, with each type parameter it leaves
    /// unknown bound to its bound, where that's known and doesn't name the
    /// function's type parameters.
    fn unsolved_as_bounds(&mut self, solved: &Solved) -> Vec<TypeId> {
        (solved.formals.iter().zip(&solved.actuals))
            .map(|(&formal, &actual)| {
                let param = self.formal_type(formal);
                let types = &mut self.typing.types;
                let bound = types.upper(param);
                let usable = types.is_known(bound) && !types.mentions(bound, &solved.formals);
                match (actual, usable) {
                    (UNKNOWN, true) => bound,
                    _ => actual,
                }
            })
            .collect()
    }

    /// Binds each type parameter a solved call still leaves unknown to
    /// its bound, as Kotlin Temper's solver does: the bound as declared,
    /// or `AnyValue`, with what the others bind put in, so `make(1)` and
    /// `make<Int>(1)` for `make<A, B>(a: A): P<A, B?>` are a `P<Int32,
    /// AnyValue?>`, `U` in `<T, U extends T>` binds to what `T` does, and
    /// a type parameter nothing at the call can bind (`CONTEXT` in
    /// `combine<CONTEXT extends Context, T>(x: T): T`) to its bound too.
    /// Not one that an argument whose type isn't known may bind, nor any
    /// where the call has too many type arguments, as those are errors
    /// of their own, nor one whose bound names it (`K extends
    /// MapKey<K>`).
    pub(super) fn bind_unsolved(&mut self, solved: &mut Solved) {
        if solved.wrong_type_arity() {
            return;
        }
        for k in 0..solved.formals.len() {
            if solved.actuals[k] != UNKNOWN || solved.blocked[k] {
                continue;
            }
            let bound = self.declared_bound(solved.formals[k]);
            let types = &mut self.typing.types;
            let bound = types.substitute(bound, &solved.formals, &solved.actuals);
            if types.is_known(bound) {
                solved.actuals[k] = bound;
            }
        }
        // One only null is passed for, as `T` in `id(null)`, an error, as
        // what it's solved as made nullable, which null is.
        for (actual, &nulled) in solved.actuals.iter_mut().zip(&solved.nulled) {
            if nulled && self.typing.types.is_known(*actual) {
                *actual = self.typing.types.nullable(*actual);
            }
        }
    }

    /// Whether a value of type `s` can't be passed where a `t` is wanted,
    /// as [`Settle::check_value`] judges it.
    pub(super) fn misfits(&mut self, s: TypeId, t: TypeId, value: NodeId) -> bool {
        if s == NULL {
            return self.is_mismatch(NULL, t);
        }
        if !self.is_mismatch(s, t) {
            return false;
        }
        match self.typing.types.get(s).clone() {
            TypeData::Nullable(inner) if !self.is_mismatch(inner, t) => {
                t != ANY_VALUE && !self.may_fold(value, 0)
            }
            _ => true,
        }
    }

    /// The generic function a callee names: one declared by `let` (not
    /// `var`), with type parameters and no declared type.
    fn generic_fn(&self, callee: NodeId) -> Option<NodeId> {
        let tree = &self.lib.tree;
        let mut callee = callee;
        // Through another name for it, as an import makes
        // (`listJsonAdapter` for std's).
        for _ in 0..8 {
            if tree.kind(callee) != NodeKind::RightName {
                return None;
            }
            let symbol = self.scopes.symbol(self.scopes.resolved(callee)?);
            if !matches!(symbol.kind, SymbolKind::TopLevel | SymbolKind::Local) || symbol.mutable {
                return None;
            }
            let decl = symbol.decl.filter(|&d| tree.kind(d) == NodeKind::Let)?;
            let (_, ty, init) = core::let_parts(tree, decl);
            let init = init.filter(|_| ty.is_none())?;
            if tree.kind(init) == NodeKind::RightName {
                callee = init;
                continue;
            }
            let f = Some(init).filter(|&f| tree.kind(f) == NodeKind::Fn)?;
            return (!core::type_params(tree, f).is_empty()).then_some(f);
        }
        None
    }

    /// The generic class a call `new Box(…)` or `new Box<Int>(…)` makes,
    /// with its type arguments, if typing knows it: not an interface, nor
    /// one of core's builtin types.
    fn generic_new(&self, callee: NodeId, args: &[NodeId]) -> Option<(NodeId, Vec<NodeId>)> {
        if self.scopes.resolved_builtin(callee) != Some(Builtin::Function(Function::New)) {
            return None;
        }
        let tree = &self.lib.tree;
        let mut made = *args.first()?;
        // `new Alias(…)`, for `let Alias = C<Int>;`, is `new C<Int>(…)`.
        let mut aliases = 0;
        let (class, type_args) = loop {
            let (name, type_args) = match tree.kind(made) {
                NodeKind::RightName => (made, Vec::new()),
                NodeKind::Call => match tree.children(made) {
                    [angle, name, type_args @ ..]
                        if self.scopes.resolved_builtin(*angle)
                            == Some(Builtin::Function(Function::Angle)) =>
                    {
                        (*name, type_args.to_vec())
                    }
                    _ => return None,
                },
                _ => return None,
            };
            let b = self.scopes.resolved(name)?;
            match self.type_decl(b) {
                Some(class) => break (class, type_args),
                None if type_args.is_empty() && aliases < 8 => {
                    aliases += 1;
                    made = self.alias_init(b)?;
                }
                None => return None,
            }
        };
        let generic = !core::type_params(tree, class).is_empty();
        let builtin = crate::builtins::TYPES.contains(&self.member_text(class));
        (generic && !builtin && tree.data(class) & CLASS_INTERFACE == 0)
            .then_some((class, type_args))
    }

    /// A call's type arguments, as `Int` in `f<Int>(x)`, and its other
    /// arguments; `None` if it has other symbols among them.
    pub(super) fn split_type_args(&self, args: &[NodeId]) -> Option<(Vec<NodeId>, Vec<NodeId>)> {
        let (mut type_args, mut values) = (Vec::new(), Vec::new());
        let mut rest = args;
        while let Some((&arg, more)) = rest.split_first() {
            rest = more;
            match self.lib.symbol(arg) {
                Some(key) if self.lib.names.get(key) == "typeArg" => {
                    let (&t, more) = rest.split_first()?;
                    type_args.push(t);
                    rest = more;
                }
                Some(_) => return None,
                None => values.push(arg),
            }
        }
        Some((type_args, values))
    }

    /// A generic function's type parameter's type, with its bound set
    /// ([`Settle::type_param_type`]), whatever was typed first.
    fn formal_type(&mut self, formal: SymbolId) -> TypeId {
        self.type_param_type(formal);
        self.typing.types.param(formal)
    }

    /// A type parameter with its bound, as Kotlin Temper writes it: `T
    /// extends AnyValue`.
    fn show_formal(&mut self, formal: SymbolId) -> String {
        let param = self.formal_type(formal);
        let bound = self.typing.types.upper(param);
        format!("{} extends {}", self.show(param), self.show(bound))
    }

    /// A generic function's signature, as Kotlin Temper writes it: `<T
    /// extends AnyValue>(T, Int32) -> T`, with an instance's method's
    /// receiver first, `<T extends AnyValue>(Order, List<T>) -> T`.
    fn show_generic_sig(&mut self, solved: &Solved) -> String {
        let formals: Vec<String> = (solved.formals.iter())
            .map(|&f| self.show_formal(f))
            .collect();
        let TypeData::Fn {
            params,
            ret,
            throws,
        } = self.typing.types.get(solved.sig).clone()
        else {
            return String::new();
        };
        let params: Vec<String> = (solved.this.map(|(declared, _)| declared).into_iter())
            .chain(params.iter().map(|p| p.ty))
            .map(|t| self.show(t))
            .collect();
        let ret = show_result(self.show(ret), throws);
        format!("<{}>({}) -> {ret}", formals.join(", "), params.join(", "))
    }
}
