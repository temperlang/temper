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
use crate::types::{ANY_VALUE, NULL, Param, TypeData, TypeId, UNKNOWN, VOID};
use temper_syntax::tree::{NodeId, NodeKind, Span};

/// A call of a generic function, solved.
pub(super) struct Solved {
    /// The function's signature, with its type parameters as written.
    sig: TypeId,
    /// Its type parameters.
    formals: Vec<SymbolId>,
    /// What each type parameter binds to at this call, unknown where the
    /// call doesn't say.
    actuals: Vec<TypeId>,
    /// Whether each type parameter is unknown as the call says too much:
    /// arguments bind it two ways, or it has the wrong number of type
    /// arguments.
    ambiguous: Vec<bool>,
    /// The call.
    call: NodeId,
    /// The call's arguments, without type arguments.
    args: Vec<NodeId>,
    /// For an instance's method, the receiver's type, as
    /// [`super::members::GenericMethod::this`] says.
    this: Option<(TypeId, TypeId)>,
    /// Whether the call has type arguments, as `id<Int>(x)`.
    has_type_args: bool,
}

impl Settle {
    /// Solves a call of a generic function declared by `let`, as `id(1)`
    /// or `id<Int>(x)`: from its type arguments, if there are as many as
    /// the function has type parameters (else not at all), whatever its
    /// arguments' values; otherwise from its arguments' types
    /// ([`crate::types::Types::unify`]), leaving a type parameter that's
    /// solved two different ways unknown, as Kotlin may find a common
    /// supertype. A function literal argument binds by the types it
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
        let (sig, formals, type_args, args) = match self.generic_fn(callee) {
            Some(f) => {
                let Some((type_args, args)) = self.split_type_args(args) else {
                    return Ok(None);
                };
                (self.fn_signature(f), self.own_formals(f), type_args, args)
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
                (method.sig, method.formals, type_args, args)
            }
            None => match self.generic_new(callee, args) {
                Some((class, type_args)) => {
                    let formals = self.own_formals(class);
                    let params = self.constructor_type(class);
                    let made = formals.iter().map(|&f| self.type_param_type(f)).collect();
                    let result = self.generic_class_type(class, made);
                    let sig = self.typing.types.intern(TypeData::Fn { params, result });
                    (sig, formals, type_args, args[1..].to_vec())
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
                        (method.sig, method.formals, type_args, values)
                    }
                    None => return Ok(None),
                },
            },
        };
        let TypeData::Fn { params, .. } = self.typing.types.get(sig).clone() else {
            return Ok(None);
        };
        let mut actuals = vec![UNKNOWN; formals.len()];
        let mut ambiguous = vec![false; formals.len()];
        // Type arguments count whatever the arguments' values, though
        // Kotlin Temper folds a call of constants first, so it passes
        // `let s: String = id<Int>("a")`.
        if !type_args.is_empty() {
            if type_args.len() == formals.len() {
                for (actual, &t) in actuals.iter_mut().zip(&type_args) {
                    *actual = self.eval_type(t);
                }
            } else {
                ambiguous.fill(true);
            }
        } else {
            let placed = self.placed_params(&params, &args);
            for (&arg, &i) in args.iter().zip(&placed) {
                let Some(param) = params.get(i) else {
                    continue;
                };
                // A function literal binds only by what it declares: its
                // result, and its parameters' types, if any.
                let t = match self.lib.tree.kind(arg) {
                    NodeKind::Fn if !self.declares_result(arg) => continue,
                    // `[]` takes its type from the solution
                    // ([`Settle::wanted_type`]), and another list literal
                    // binds by its elements' common type, as its own type
                    // is what the solution wants.
                    _ if self.is_empty_list(arg) => continue,
                    _ if self.is_list_literal(arg) => {
                        let elements = self.lib.tree.children(arg)[1..].to_vec();
                        let mut types = Vec::with_capacity(elements.len());
                        for &element in &elements {
                            types.push(self.expr_type(element, forced)?);
                        }
                        let element = self.common_element(&elements, &types);
                        self.typing.types.list_of(element)
                    }
                    NodeKind::Fn => self.fn_type(arg),
                    _ => self.expr_type(arg, forced)?,
                };
                let types = &mut self.typing.types;
                types.unify(param.ty, t, &formals, &mut actuals, &mut ambiguous);
            }
            for (actual, &conflict) in actuals.iter_mut().zip(&ambiguous) {
                if conflict {
                    *actual = UNKNOWN;
                }
            }
        }
        Ok(Some(Solved {
            sig,
            formals,
            actuals,
            ambiguous,
            call,
            args,
            this,
            has_type_args: !type_args.is_empty(),
        }))
    }

    /// A solved call's type: its function's result's pass type with the
    /// solution put in, unknown where it isn't solved.
    pub(super) fn solved_result(&mut self, solved: &Solved) -> TypeId {
        let TypeData::Fn { result, .. } = *self.typing.types.get(solved.sig) else {
            return UNKNOWN;
        };
        let types = &mut self.typing.types;
        let result = types.substitute(result, &solved.formals, &solved.actuals);
        types.pass(result)
    }

    /// Whether a function literal declares its result type, and isn't a
    /// generator's.
    fn declares_result(&self, f: NodeId) -> bool {
        let tree = &self.lib.tree;
        core::fn_parts(tree, f).1.is_some() && core::fn_supers(tree, f).is_empty()
    }

    /// A solved call's type, with the type parameters its arguments leave
    /// unknown bound by the type its place wants, where the call has no
    /// type arguments, as Kotlin Temper's solver binds them: `new
    /// ListBuilder()` where a `ListBuilder<Int>` is wanted is one.
    pub(super) fn solved_type(
        &mut self,
        solved: &mut Solved,
        forced: bool,
    ) -> Result<TypeId, Fact> {
        let t = self.solved_result(solved);
        if self.typing.types.is_known(t) || solved.has_type_args {
            return Ok(t);
        }
        let Some(wanted) = self.wanted_type(solved.call, forced)? else {
            self.literal_results_as_bounds(solved);
            return Ok(self.solved_result(solved));
        };
        let TypeData::Fn { result, .. } = *self.typing.types.get(solved.sig) else {
            return Ok(t);
        };
        let ret = self.typing.types.pass(result);
        let wanted = match (self.typing.types.get(wanted), self.typing.types.get(ret)) {
            (&TypeData::Nullable(inner), ret) if !matches!(ret, TypeData::Nullable(_)) => inner,
            _ => wanted,
        };
        let bindings = self.wanted_bindings(ret, wanted, &solved.formals);
        for (actual, b) in solved.actuals.iter_mut().zip(bindings) {
            if *actual == UNKNOWN {
                *actual = b;
            }
        }
        self.literal_results_as_bounds(solved);
        Ok(self.solved_result(solved))
    }

    /// Binds each type parameter still unknown that a function literal
    /// argument without a declared result would give, its parameter's
    /// result, to its bound, as Kotlin Temper does: `ls.map { x => x * x
    /// }`, with nothing wanted, is a `List<AnyValue>`, and the literal a
    /// `fn (Int32): AnyValue` (notes/typing.md, "Function literals and
    /// target typing").
    fn literal_results_as_bounds(&mut self, solved: &mut Solved) {
        if solved.has_type_args {
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
            let TypeData::Fn { result, .. } = *self.typing.types.get(ty) else {
                continue;
            };
            for (k, &formal) in solved.formals.iter().enumerate() {
                open[k] |= self.typing.types.mentions(result, &[formal]);
            }
        }
        let bounds = self.unsolved_as_bounds(solved);
        for (k, bound) in bounds.into_iter().enumerate() {
            if open[k] && solved.actuals[k] == UNKNOWN && !solved.ambiguous[k] {
                solved.actuals[k] = bound;
            }
        }
    }

    /// Checks a call of a generic function, as Kotlin Temper's solver
    /// does, at its positions: a type parameter bound to a type outside
    /// its bound, "Type formal <T extends Animal> cannot bind to Int32
    /// which does not fit upper bounds [Animal]", at the call; the wrong
    /// number of arguments, "No callee matches inputs [Int32] among [<T
    /// extends AnyValue>(T, Int32) -> T]", at the call; and arguments
    /// that don't fit the solved signature, "Actual arguments do not match
    /// signature: <T extends AnyValue>(T, Int32) -> T expected [String,
    /// Int32], but got [Int32, String]", from the first that doesn't to
    /// the last. Returns whether the call was of a generic function.
    pub(super) fn check_generic_call(&mut self, call: NodeId) -> bool {
        let Ok(Some(solved)) = self.solve_call(call, false) else {
            return false;
        };
        let span = self.lib.tree.span(call);
        for (&formal, &actual) in solved.formals.iter().zip(&solved.actuals) {
            let types = &mut self.typing.types;
            let param = types.param(formal);
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

    /// Reports a generic call's arguments that don't fit what the type
    /// its place wants binds, as Kotlin Temper's solver does, where `let
    /// xs: List<String> = wrap(1)` is "Actual arguments do not match
    /// signature: <T extends AnyValue>(T) -> List<T> expected [String],
    /// but got [Int32]", at the argument, rather than an error at the
    /// place. Only where the call's result isn't a bare type parameter
    /// (`T` or `T?`), which Kotlin solves from the arguments, and the call
    /// has no type arguments. Returns whether it reported an error.
    pub(super) fn check_generic_wanted(&mut self, call: NodeId, wanted: TypeId) -> bool {
        let Ok(Some(mut solved)) = self.solve_call(call, false) else {
            return false;
        };
        let TypeData::Fn { result, .. } = *self.typing.types.get(solved.sig) else {
            return false;
        };
        let types = &mut self.typing.types;
        let ret = types.pass(result);
        let bare = match *types.get(ret) {
            TypeData::Nullable(inner) => matches!(types.get(inner), TypeData::Param(_)),
            TypeData::Param(_) => true,
            _ => false,
        };
        if bare || solved.has_type_args {
            return false;
        }
        let mut any = false;
        let bindings = self.wanted_bindings(ret, wanted, &solved.formals);
        for (actual, b) in solved.actuals.iter_mut().zip(bindings) {
            if b != UNKNOWN {
                *actual = b;
                any = true;
            }
        }
        any && self.check_generic_args(&solved)
    }

    /// What the type wanted where a generic call's value goes binds each
    /// of its type parameters to, its result `ret` matched against it part
    /// by part, unknown where it binds none or two ways.
    fn wanted_bindings(
        &mut self,
        ret: TypeId,
        wanted: TypeId,
        formals: &[SymbolId],
    ) -> Vec<TypeId> {
        let types = &mut self.typing.types;
        // `List<T>` where a `Listed<String>` is wanted is a `Listed<T>`.
        let ret = types.as_supertype(ret, wanted);
        let n = formals.len();
        let (mut bound, mut conflicts) = (vec![UNKNOWN; n], vec![false; n]);
        types.unify(ret, wanted, formals, &mut bound, &mut conflicts);
        (bound.into_iter().zip(conflicts))
            .map(|(b, conflict)| if conflict { UNKNOWN } else { b })
            .collect()
    }

    /// The parameters of a generic call that has a function literal
    /// argument, with what's solved put in, as Kotlin Temper types the
    /// literal once it has solved the call: each type parameter from the
    /// other arguments and type arguments ([`Settle::solve_call`]), and
    /// what they leave unknown, where the call has no type arguments, from
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
        let TypeData::Fn { params, result } = self.typing.types.get(solved.sig).clone() else {
            return Ok(None);
        };
        let ret = self.typing.types.pass(result);
        let wanted = match solved.has_type_args {
            true => None,
            false => self.wanted_type(call, forced)?,
        };
        if let Some(wanted) = wanted {
            let types = &self.typing.types;
            let bare = matches!(types.get(self.non_null(ret)), TypeData::Param(_));
            let bindings = self.wanted_bindings(ret, wanted, &solved.formals);
            for ((actual, b), &ambiguous) in
                (solved.actuals.iter_mut().zip(bindings)).zip(&solved.ambiguous)
            {
                if b != UNKNOWN && !ambiguous && (*actual == UNKNOWN || !bare) {
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
        let result = types.list_of(param);
        let params = vec![
            Param {
                ty: param,
                optional: false,
            };
            args.len()
        ];
        let sig = types.intern(TypeData::Fn { params, result });
        self.check_generic_args(&Solved {
            sig,
            formals: vec![formal],
            actuals: vec![element],
            ambiguous: vec![false],
            call: list,
            args,
            this: None,
            has_type_args: false,
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
        // tempest doesn't bind one to null or `Void`, which Kotlin reports
        // otherwise, nor to an argument whose type it doesn't know.
        let bounds = self.unsolved_as_bounds(solved);
        let plain =
            (arg_types.iter()).all(|&t| self.typing.types.is_known(t) && t != NULL && t != VOID);
        let checked: Vec<TypeId> = (0..bounds.len())
            .map(|i| match solved.actuals[i] {
                UNKNOWN if plain && !solved.ambiguous[i] => bounds[i],
                actual => actual,
            })
            .collect();
        let wanted: Vec<TypeId> = (params.iter().take(solved.args.len()))
            .map(|p| {
                let types = &mut self.typing.types;
                types.substitute(p.ty, &solved.formals, &checked)
            })
            .collect();
        let unfit: Vec<usize> = (0..wanted.len())
            .filter(|&i| self.misfits(arg_types[i], wanted[i]))
            .collect();
        let (Some(&first), Some(&last)) = (unfit.first(), unfit.last()) else {
            return false;
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
                NULL => format!("{}?", self.show(w)),
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
        let types = &mut self.typing.types;
        (solved.formals.iter().zip(&solved.actuals))
            .map(|(&formal, &actual)| {
                let param = types.param(formal);
                let bound = types.upper(param);
                let usable = types.is_known(bound) && !types.mentions(bound, &solved.formals);
                match (actual, usable) {
                    (UNKNOWN, true) => bound,
                    _ => actual,
                }
            })
            .collect()
    }

    /// A solved call's type arguments, settled for tmpl: what the call
    /// binds, and for a type parameter that appears in none of the
    /// function's parameters' types nor its result's, so nothing at the
    /// call can bind it (`CONTEXT` in `combine<CONTEXT extends Context,
    /// T>(x: T): T`), its bound, or `AnyValue` if it has none, as Kotlin
    /// Temper's solver binds it, with the other type parameters put in.
    /// Others still unknown stay so.
    pub(super) fn settled_type_args(&mut self, solved: &Solved) -> Vec<TypeId> {
        let TypeData::Fn { params, result } = self.typing.types.get(solved.sig).clone() else {
            return solved.actuals.clone();
        };
        let mut settled = solved.actuals.clone();
        for (k, &formal) in solved.formals.iter().enumerate() {
            let only = &solved.formals[k..=k];
            let types = &self.typing.types;
            let used =
                types.mentions(result, only) || params.iter().any(|p| types.mentions(p.ty, only));
            if settled[k] != UNKNOWN || solved.ambiguous[k] || used {
                continue;
            }
            let bound = self.declared_bound(formal);
            let types = &mut self.typing.types;
            let bound = types.substitute(bound, &solved.formals, &solved.actuals);
            if types.is_known(bound) {
                settled[k] = bound;
            }
        }
        settled
    }

    /// Whether a value of type `s` can't be passed where a `t` is wanted,
    /// as [`Settle::check_value`] judges it.
    pub(super) fn misfits(&mut self, s: TypeId, t: TypeId) -> bool {
        if s == NULL {
            return self.is_mismatch(NULL, t);
        }
        if !self.is_mismatch(s, t) {
            return false;
        }
        match self.typing.types.get(s).clone() {
            TypeData::Nullable(inner) if !self.is_mismatch(inner, t) => t != ANY_VALUE,
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

    /// A type parameter with its bound, as Kotlin Temper writes it: `T
    /// extends AnyValue`.
    fn show_formal(&mut self, formal: SymbolId) -> String {
        let types = &mut self.typing.types;
        let param = types.param(formal);
        let bound = types.upper(param);
        format!("{} extends {}", self.show(param), self.show(bound))
    }

    /// A generic function's signature, as Kotlin Temper writes it: `<T
    /// extends AnyValue>(T, Int32) -> T`, with an instance's method's
    /// receiver first, `<T extends AnyValue>(Order, List<T>) -> T`.
    fn show_generic_sig(&mut self, solved: &Solved) -> String {
        let formals: Vec<String> = (solved.formals.iter())
            .map(|&f| self.show_formal(f))
            .collect();
        let TypeData::Fn { params, result } = self.typing.types.get(solved.sig).clone() else {
            return String::new();
        };
        let params: Vec<String> = (solved.this.map(|(declared, _)| declared).into_iter())
            .chain(params.iter().map(|p| p.ty))
            .map(|t| self.show(t))
            .collect();
        let ret = self.show(result);
        format!("<{}>({}) -> {ret}", formals.join(", "), params.join(", "))
    }
}
