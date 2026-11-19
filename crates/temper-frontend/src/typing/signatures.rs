//! Builtins' signatures, the third slice of milestone 4: the variants of
//! each operator as Kotlin Temper declares them (`BuiltinFuns.kt` and
//! `BuiltinOperatorSpecs.kt`), which one a call picks, as Kotlin's solver
//! does, and `console.log`'s. `<`, `<=`, `>` and `>=` take `<=>`'s
//! variants, as Kotlin desugars them to it.
//!
//! An operator with several variants reports arguments that don't fit the
//! one picked as "Actual arguments do not match signature"; one with a
//! single variant (`!`, `**` and `console.log`), each argument as "Expected
//! subtype", as for any function. A call is only checked when typing knows
//! every argument's type as Kotlin would, so an unknown type or a function
//! never causes an error.
//!
//! `==`, `!=`, `<=>` and the comparisons whose first operand is of a class
//! call its method marked `@operator` instead, if it has one; if it has
//! none, the builtin variants apply, which no class's value fits, and the
//! error names the operator the class lacks.

use super::Site;
use crate::builtins::{Builtin, Constant, Function};
use crate::core::{MEMBER_OPERATOR_CMP, MEMBER_OPERATOR_EQ};
use crate::library::{Level, Message};
use crate::scope::SymbolKind;
use crate::settle::{Fact, Settle};
use crate::types::{
    BOOLEAN, FLOAT64, INT32, INT64, NULL, STRING, TypeData, TypeId, UNKNOWN, VOID, show_result,
};
use temper_syntax::ast::Value;
use temper_syntax::tree::{NodeId, NodeKind, Span};

/// One variant of a builtin function.
#[derive(Debug)]
pub(super) struct Sig {
    pub params: &'static [TypeId],
    pub ret: TypeId,
    /// Whether it may bubble: its return type is `ret throws Bubble`.
    pub bubbles: bool,
}

const fn sig(params: &'static [TypeId], ret: TypeId) -> Sig {
    Sig {
        params,
        ret,
        bubbles: false,
    }
}

const fn bubbly(params: &'static [TypeId], ret: TypeId) -> Sig {
    Sig {
        params,
        ret,
        bubbles: true,
    }
}

const ARITH: &[Sig] = &[
    sig(&[INT32, INT32], INT32),
    sig(&[INT64, INT64], INT64),
    sig(&[FLOAT64, FLOAT64], FLOAT64),
];
const NEGATE: &[Sig] = &[
    sig(&[INT32], INT32),
    sig(&[INT64], INT64),
    sig(&[FLOAT64], FLOAT64),
];
const DIVIDE: &[Sig] = &[
    bubbly(&[INT32, INT32], INT32),
    bubbly(&[INT64, INT64], INT64),
    bubbly(&[FLOAT64, FLOAT64], FLOAT64),
];
// `&` has a third variant, for intersection types, which no value fits.
const BITS: &[Sig] = &[sig(&[INT32, INT32], INT32), sig(&[INT64, INT64], INT64)];
const BIT_NOT: &[Sig] = &[sig(&[INT32], INT32), sig(&[INT64], INT64)];
const SHIFT: &[Sig] = &[sig(&[INT32, INT32], INT32), sig(&[INT64, INT32], INT64)];
const COMPARE: &[Sig] = &[
    sig(&[INT32, INT32], INT32),
    sig(&[INT64, INT64], INT32),
    sig(&[FLOAT64, FLOAT64], INT32),
    sig(&[STRING, STRING], INT32),
    sig(&[BOOLEAN, BOOLEAN], INT32),
];
const EQUAL: &[Sig] = &[
    sig(&[INT32, INT32], BOOLEAN),
    sig(&[INT64, INT64], BOOLEAN),
    sig(&[FLOAT64, FLOAT64], BOOLEAN),
    sig(&[STRING, STRING], BOOLEAN),
    sig(&[BOOLEAN, BOOLEAN], BOOLEAN),
];
const NOT: &[Sig] = &[sig(&[BOOLEAN], BOOLEAN)];
const POW: &[Sig] = &[sig(&[FLOAT64, FLOAT64], FLOAT64)];
/// `Console.log`'s, without `this`.
const LOG: Sig = sig(&[STRING], VOID);

/// What an operator calls whose first operand is of a class.
pub(super) enum OperatorMethod {
    /// Its method marked `@operator`, and the class or interface that
    /// declares it.
    Method { owner: NodeId, method: NodeId },
    /// The builtin variants, as it has no such method.
    Builtin,
    /// Typing can't tell: a class or interface may inherit one from a
    /// supertype typing doesn't know.
    Unsure,
}

/// The signature of a class's method that an operator calls.
pub(super) struct OperatorSig {
    /// The first operand's type.
    this: TypeId,
    /// The class or interface declaring the method.
    owner: TypeId,
    param: TypeId,
    ret: TypeId,
}

/// A builtin function's variants for a number of arguments, if typed.
pub(super) fn variants(f: Function, arity: usize) -> &'static [Sig] {
    use Function::*;
    match (f, arity) {
        (Add | Sub, 1) => NEGATE,
        (Add | Sub | Mul, 2) => ARITH,
        (Div | Mod, 2) => DIVIDE,
        (BitAnd | BitOr | BitXor, 2) => BITS,
        (BitNot, 1) => BIT_NOT,
        (Shl | Shr | UShr, 2) => SHIFT,
        (Lt | Le | Gt | Ge | Cmp, 2) => COMPARE,
        (Eq | Ne, 2) => EQUAL,
        (Not, 1) => NOT,
        (Pow, 2) => POW,
        _ => &[],
    }
}

impl Settle {
    /// The variant of a builtin operator that a call picks, as Kotlin
    /// Temper's solver does, with its arguments' types, if typing knows
    /// them as Kotlin would: each a final builtin type, maybe nullable, or
    /// null. Going through the arguments in order, the solver drops, last
    /// first, each variant that the argument can't fit, until one is left.
    pub(super) fn builtin_variant(
        &mut self,
        f: Function,
        args: &[NodeId],
        forced: bool,
    ) -> Result<Option<(&'static Sig, Vec<TypeId>)>, Fact> {
        let variants = variants(f, args.len());
        if variants.len() < 2 {
            return Ok(None);
        }
        let mut types = Vec::with_capacity(args.len());
        for &arg in args {
            types.push(self.expr_type(arg, forced)?);
        }
        let is_eq = matches!(f, Function::Eq | Function::Ne);
        for (i, (&arg, &t)) in args.iter().zip(&types).enumerate() {
            let base = match *self.typing.types.get(t) {
                TypeData::Nullable(inner) => inner,
                _ => t,
            };
            // A class's value fits no variant, unless it's the first
            // operand, not null, and its class's method may be called
            // instead.
            let is_class = matches!(self.typing.types.get(base), TypeData::Class(_));
            let class_fits_none = match self.class_operator(f, t) {
                Some(OperatorMethod::Builtin) => true,
                Some(_) => i > 0,
                None => is_class,
            };
            let known = t == NULL || self.typing.types.is_final(base) || class_fits_none;
            // Kotlin expands `==` with a nullable or `Void` side otherwise;
            // a compiler temporary, as `x++` makes, gets other errors there.
            if !known
                || (is_eq && (t != base || t == NULL || base == VOID))
                || self.is_synthetic(arg)
            {
                return Ok(None);
            }
        }
        let mut left: Vec<&Sig> = variants.iter().collect();
        'args: for (i, &t) in types.iter().enumerate() {
            for j in (0..left.len()).rev() {
                if left[j].params[i] != t {
                    left.remove(j);
                    if left.len() == 1 {
                        break 'args;
                    }
                }
            }
        }
        Ok((left.len() == 1).then(|| (left[0], types)))
    }

    /// The result of the variant of a builtin operator whose parameters
    /// are its arguments' types exactly, where Kotlin Temper's solver
    /// wouldn't pick one as [`Settle::builtin_variant`] does, as for a
    /// compiler temporary in `i++`; unknown if none is.
    pub(super) fn exact_variant(
        &mut self,
        f: Function,
        args: &[NodeId],
        forced: bool,
    ) -> Result<TypeId, Fact> {
        let variants = variants(f, args.len());
        if variants.is_empty() {
            return Ok(UNKNOWN);
        }
        let mut types = Vec::with_capacity(args.len());
        for &arg in args {
            types.push(self.expr_type(arg, forced)?);
        }
        let found = variants.iter().find(|sig| sig.params == types.as_slice());
        Ok(found.map_or(UNKNOWN, |sig| sig.ret))
    }

    /// What an operator calls with a first operand of type `t`, if `t` is
    /// a class's or interface's, and the operator one that a method marked
    /// `@operator` may implement.
    pub(super) fn class_operator(&mut self, f: Function, t: TypeId) -> Option<OperatorMethod> {
        use Function::*;
        let flag = match f {
            Eq | Ne => MEMBER_OPERATOR_EQ,
            Cmp | Lt | Le | Gt | Ge => MEMBER_OPERATOR_CMP,
            _ => return None,
        };
        // A generic class's too, as `Box<Int>`'s.
        let types = &self.typing.types;
        let TypeData::Class(symbol) = *types.get(types.base(t)) else {
            return None;
        };
        let class = self.scopes.symbol(symbol).decl?;
        Some(self.operator_method(class, flag))
    }

    /// The signature of the method an operator calls whose first operand
    /// is of a class with one marked `@operator`, as Kotlin Temper writes
    /// it, `(H, H) -> Boolean`, and whether the second operand doesn't fit
    /// it, as typing knows it. None if the call isn't such a call.
    pub(super) fn class_operator_call(
        &mut self,
        f: Function,
        args: &[NodeId],
    ) -> Option<(OperatorSig, bool)> {
        let &[a, b] = args else {
            return None;
        };
        let this = self.known_type(a);
        let (owner, param, ret) = match self.class_operator(f, this)? {
            OperatorMethod::Method { owner, method } => {
                // A generic class's, with its type arguments put in, as
                // `(Box<Int32>, Box<Int32>) -> Boolean`.
                let (sig, owner) = match self.receiver(this) {
                    Some(r) if !self.typing.types.args(this).is_empty() => {
                        let owner = if r.class == owner { this } else { UNKNOWN };
                        (self.member_type_in(method, &r), owner)
                    }
                    _ => (self.member_type(method), self.class_type(owner)),
                };
                match self.typing.types.get(sig) {
                    TypeData::Fn { params, ret, .. } if params.len() == 1 => {
                        (owner, params[0].ty, *ret)
                    }
                    _ => (owner, UNKNOWN, UNKNOWN),
                }
            }
            _ => return None,
        };
        let sig = OperatorSig {
            this,
            owner,
            param,
            ret,
        };
        let that = self.known_type(b);
        // Kotlin expands `==` with a nullable side to a null check first.
        let nullable = matches!(self.typing.types.get(that), TypeData::Nullable(_));
        let is_eq = matches!(f, Function::Eq | Function::Ne);
        let unfit = !((is_eq && (nullable || that == NULL))
            || self.is_synthetic(a)
            || !self.is_mismatch(that, param));
        Some((sig, unfit))
    }

    /// Checks a call of an operator whose first operand is of a class with
    /// a method marked `@operator`, as Kotlin Temper does, as a call of
    /// that method: "Actual arguments do not match signature: (H, H) ->
    /// Boolean expected [H, H], but got [H, G]", at the second operand.
    /// Returns whether it was such a call.
    fn check_operator_method(&mut self, f: Function, args: &[NodeId]) -> bool {
        let Some((sig, unfit)) = self.class_operator_call(f, args) else {
            return false;
        };
        if !unfit {
            return true;
        }
        let expected = format!("{}, {}", self.show(sig.owner), self.show(sig.param));
        // Kotlin's solver gives `null` the type wanted, made nullable.
        let that = match self.known_type(args[1]) {
            NULL => self.typing.types.nullable(sig.param),
            that => that,
        };
        let message = Message::SignatureMismatch(
            format!("({expected}) -> {}", self.show(sig.ret)),
            format!("[{expected}]"),
            format!("[{}, {}]", self.show(sig.this), self.show(that)),
        );
        self.lib
            .report(Level::Error, message, self.lib.tree.span(args[1]));
        true
    }

    /// Whether a node reads a compiler temporary.
    fn is_synthetic(&self, node: NodeId) -> bool {
        self.lib.tree.kind(node) == NodeKind::RightName
            && self
                .scopes
                .resolved(node)
                .is_some_and(|b| self.scopes.symbol(b).kind == SymbolKind::Synthetic)
    }

    /// Checks a call of a builtin function against its signatures.
    pub(super) fn check_builtin_call(&mut self, f: Function, args: &[NodeId]) {
        if self.check_operator_method(f, args) {
            return;
        }
        let variants = variants(f, args.len());
        if let [only] = variants {
            for (&arg, &param) in args.iter().zip(only.params) {
                self.check_value(param, arg, Site::Arg);
            }
            return;
        }
        let Ok(Some((sig, types))) = self.builtin_variant(f, args, false) else {
            return;
        };
        let bad: Vec<usize> = (0..args.len())
            .filter(|&i| types[i] != sig.params[i])
            .collect();
        let (Some(&first), Some(&last)) = (bad.first(), bad.last()) else {
            return;
        };
        let span = Span {
            start: self.lib.tree.span(args[first]).start,
            end: self.lib.tree.span(args[last]).end,
        };
        // Kotlin's solver gives `null` the type wanted, made nullable.
        let got = (types.iter().zip(sig.params))
            .map(|(&t, &param)| match t {
                NULL => format!("{}?", self.show(param)),
                _ => self.show(t),
            })
            .collect::<Vec<_>>();
        let message = match self.missing_operator(f, args) {
            Some(message) => message,
            None => {
                let expected = self.show_list(sig.params);
                Message::SignatureMismatch(
                    self.show_sig(sig),
                    format!("[{expected}]"),
                    format!("[{}]", got.join(", ")),
                )
            }
        };
        self.lib.report(Level::Error, message, span);
    }

    /// For an operator a class's or interface's value doesn't fit as the
    /// first operand, "C does not support infix `==`; give it a method
    /// marked `@operator("==")`", rather than the mismatch against
    /// `Int32`'s variant that Kotlin Temper reports (temperlang/temper#508).
    fn missing_operator(&mut self, f: Function, args: &[NodeId]) -> Option<Message> {
        let (op, method, flag) = match f {
            Function::Eq => ("==", "==", MEMBER_OPERATOR_EQ),
            Function::Ne => ("!=", "==", MEMBER_OPERATOR_EQ),
            Function::Cmp => ("<=>", "<=>", MEMBER_OPERATOR_CMP),
            Function::Lt => ("<", "<=>", MEMBER_OPERATOR_CMP),
            Function::Le => ("<=", "<=>", MEMBER_OPERATOR_CMP),
            Function::Gt => (">", "<=>", MEMBER_OPERATOR_CMP),
            Function::Ge => (">=", "<=>", MEMBER_OPERATOR_CMP),
            _ => return None,
        };
        // Not a nullable value's, which no operator method takes.
        let t = self.known_type(*args.first()?);
        let types = &self.typing.types;
        let &TypeData::Class(symbol) = types.get(types.base(t)) else {
            return None;
        };
        let class = self.scopes.symbol(symbol).decl?;
        match self.operator_method(class, flag) {
            OperatorMethod::Builtin => {
                Some(Message::NoSuchOperator(self.show(t), op.into(), method))
            }
            _ => None,
        }
    }

    /// Whether a callee is `console.log`.
    pub(super) fn is_console_log(&self, callee: NodeId) -> bool {
        let tree = &self.lib.tree;
        if tree.kind(callee) != NodeKind::Call {
            return false;
        }
        let [dot, object, key] = *tree.children(callee) else {
            return false;
        };
        self.scopes.resolved_builtin(dot) == Some(Builtin::Function(Function::Dot))
            && self.scopes.resolved_builtin(object) == Some(Builtin::Value(Constant::Console))
            && self
                .lib
                .symbol(key)
                .is_some_and(|s| self.lib.names.get(s) == "log")
    }

    /// Checks `console.log(message)`, if the callee is that.
    pub(super) fn check_console_call(&mut self, call: NodeId, callee: NodeId, args: &[NodeId]) {
        let tree = &self.lib.tree;
        if !self.is_console_log(callee)
            || args.iter().any(|&a| {
                tree.kind(a) == NodeKind::Fn || matches!(self.lib.value(a), Some(Value::Symbol(_)))
            })
        {
            return;
        }
        // Kotlin counts `this`, saying "Expected 2".
        if args.len() != LOG.params.len() {
            let message = Message::ArityMismatch(LOG.params.len());
            self.lib
                .report(Level::Error, message, self.lib.tree.span(call));
        }
        for (&arg, &param) in args.iter().zip(LOG.params) {
            self.check_value(param, arg, Site::Arg);
        }
    }

    fn show_list(&self, types: &[TypeId]) -> String {
        let shown: Vec<String> = types.iter().map(|&t| self.show(t)).collect();
        shown.join(", ")
    }

    /// A signature as Kotlin Temper writes it, `(Int32, Int32) -> Int32`,
    /// but with a result that may bubble as `Int32 throws Bubble`.
    fn show_sig(&self, sig: &Sig) -> String {
        let ret = show_result(self.show(sig.ret), sig.bubbles);
        format!("({}) -> {ret}", self.show_list(sig.params))
    }
}
