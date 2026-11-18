//! JS's spelling of the 69 operators on builtin types, indexed by [`Op`]
//! (notes/tmpl/nodes.md, "Ops, and core as declarations").
//!
//! As Kotlin's JS backend spells them (its `JsSupportNetwork.kt`), with
//! the same `@temperlang/core` helpers: `Int32` is a `number` kept in
//! range by `| 0` after `+`, `-`, and negation, `Math.imul` for `*`, and
//! `divIntInt` and `modIntInt`, which bubble on a zero divisor (`divSafe`,
//! by a divisor that can't fail it, is `a / b | 0`); `Int64` is
//! a `bigint`, wrapped by `clampInt64` after `+`, `-`, `*`, `/`, and
//! negation, with `/` and `%` as `divInt64` and `modInt64`, which bubble
//! on a zero divisor, where BigInt's throw a `RangeError` (by a divisor
//! that can't fail them, `clampInt64(a / b)` and `a % b`); shifts that JS's own operators get wrong
//! (`Int32.ushr`, and every `Int64` shift) call the runtime; `Float64` and
//! `String` comparisons go through `cmpFloat64` and `cmpString`, which
//! order NaN, signed zeros, and code points as Temper does, and
//! `Float64.eq` through `eqFloat64`. Everything else is JS's own operator.
//! Another difference from Kotlin's: those two helpers give a difference,
//! not -1, 0, or 1 (`cmpString("z", "a")` is 25), so their three-way
//! comparisons take its `Math.sign`, as Temper's `<=>` gives.
//! One difference from Kotlin's: `Int64.mod` isn't clamped, since a
//! remainder is always smaller than its divisor.

use crate::tree::{Bin, Expr, Unary};
use temper_tmpl::ops::{OPS, Op};

/// How an operator is written.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Spelling {
    /// JS's own binary operator.
    Bin(Bin),
    /// JS's own prefix operator.
    Unary(Unary),
    /// JS's operator, then `| 0`, which wraps to `Int32`.
    Wrap32(Bin),
    /// `-a | 0`.
    Neg32,
    /// `Math.imul(a, b)`.
    Imul,
    /// `clampInt64(a op b)`, which wraps to `Int64`.
    Clamp64(Bin),
    /// `clampInt64(-a)`.
    Neg64,
    /// A call of a `@temperlang/core` function with the operands.
    Helper(&'static str),
    /// `Math.sign` of such a call, for a helper that gives a difference.
    SignOf(&'static str),
    /// A `@temperlang/core` three-way comparison, compared with 0 by the
    /// operator given: `cmpString(a, b) < 0`.
    CmpWith(&'static str, Bin),
    /// `Number(a) - Number(b)`, for `Boolean.cmp`; Kotlin's `cmpBoolean`
    /// is declared to take numbers, which a type checker rejects.
    BoolCmp,
    /// String concatenation: a template literal, flattened by the
    /// translator.
    Cat,
}

fn compare(op: &str) -> Option<Bin> {
    Some(match op {
        "eq" => Bin::Eq,
        "lt" => Bin::Lt,
        "le" => Bin::Le,
        "gt" => Bin::Gt,
        "ge" => Bin::Ge,
        _ => return None,
    })
}

/// The spelling of each operator, by its name in [`OPS`]; every one has
/// one (a test checks).
pub fn spelling(op: Op) -> Option<Spelling> {
    use Spelling::*;
    let name = op.info().map_or("", |i| i.name);
    let (ty, op) = name.split_once('.').unwrap_or(("", ""));
    Some(match (ty, op) {
        ("Boolean", "not") => Unary(crate::tree::Unary::Not),
        ("Boolean", "cmp") => BoolCmp,
        ("String", "cat") => Cat,
        ("String", "cmp") => SignOf("cmpString"),
        ("String", "eq") => Bin(crate::tree::Bin::Eq),
        ("String", _) => CmpWith("cmpString", compare(op)?),
        ("Float64", "cmp") => SignOf("cmpFloat64"),
        ("Float64", "eq") => Helper("eqFloat64"),
        ("Float64", _) if compare(op).is_some() => CmpWith("cmpFloat64", compare(op)?),
        ("Int32", "cmp") => Helper("cmpInt32"),
        ("Int64", "cmp") => Helper("cmpInt64"),
        (_, _) if compare(op).is_some() => Bin(compare(op)?),
        ("Float64", "add") => Bin(crate::tree::Bin::Add),
        ("Float64", "sub") => Bin(crate::tree::Bin::Sub),
        ("Float64", "mul") => Bin(crate::tree::Bin::Mul),
        ("Float64", "div") => Bin(crate::tree::Bin::Div),
        ("Float64", "mod") => Bin(crate::tree::Bin::Mod),
        ("Float64", "neg") => Unary(crate::tree::Unary::Neg),
        ("Float64", "pow") => Bin(crate::tree::Bin::Pow),
        ("Int64", "add") => Clamp64(crate::tree::Bin::Add),
        ("Int64", "sub") => Clamp64(crate::tree::Bin::Sub),
        ("Int64", "mul") => Clamp64(crate::tree::Bin::Mul),
        // A zero divisor bubbles, where BigInt's `/` and `%` throw.
        ("Int64", "div") => Helper("divInt64"),
        ("Int64", "divSafe") => Clamp64(crate::tree::Bin::Div),
        ("Int64", "mod") => Helper("modInt64"),
        ("Int64", "modSafe") => Bin(crate::tree::Bin::Mod),
        ("Int64", "neg") => Neg64,
        ("Int32", "add") => Wrap32(crate::tree::Bin::Add),
        ("Int32", "sub") => Wrap32(crate::tree::Bin::Sub),
        ("Int32", "mul") => Imul,
        ("Int32", "div") => Helper("divIntInt"),
        // By a divisor that can't fail it, as Kotlin's: `a / b | 0`.
        ("Int32", "divSafe") => Wrap32(crate::tree::Bin::Div),
        ("Int32", "mod" | "modSafe") => Helper("modIntInt"),
        ("Int32", "neg") => Neg32,
        (_, "bitAnd") => Bin(crate::tree::Bin::BitAnd),
        (_, "bitOr") => Bin(crate::tree::Bin::BitOr),
        (_, "bitXor") => Bin(crate::tree::Bin::BitXor),
        (_, "bitNot") => Unary(crate::tree::Unary::BitNot),
        ("Int64", "shl") => Helper("bitwiseShl64"),
        ("Int64", "shr") => Helper("bitwiseShr64"),
        ("Int64", "ushr") => Helper("bitwiseShrUnsigned64"),
        ("Int32", "shl") => Bin(crate::tree::Bin::Shl),
        ("Int32", "shr") => Bin(crate::tree::Bin::Shr),
        ("Int32", "ushr") => Helper("bitwiseShrUnsigned32"),
        _ => return None,
    })
}

/// The runtime helper a spelling calls, which the module imports.
pub fn helper(spelling: Spelling) -> Option<&'static str> {
    match spelling {
        Spelling::Helper(name) | Spelling::SignOf(name) | Spelling::CmpWith(name, _) => Some(name),
        Spelling::Clamp64(_) | Spelling::Neg64 => Some("clampInt64"),
        _ => None,
    }
}

/// Writes an operator applied to its translated operands.
pub fn apply(spelling: Spelling, mut args: Vec<Expr>) -> Expr {
    match spelling {
        Spelling::Helper(name) => return Expr::name(name).call(args),
        Spelling::SignOf(name) => {
            return Expr::name("Math").method("sign", vec![Expr::name(name).call(args)]);
        }
        _ => {}
    }
    let mut next = || {
        if args.is_empty() {
            Expr::Undefined
        } else {
            args.remove(0)
        }
    };
    match spelling {
        Spelling::Bin(op) => {
            let a = next();
            Expr::bin(op, a, next())
        }
        Spelling::Unary(op) => match (op, next()) {
            // `!(a < b)` reads better as `a >= b`; no comparison here has
            // a NaN operand (floats' compare `cmpFloat64`'s result).
            (Unary::Not, Expr::Bin(cmp, a, b)) if cmp.negated().is_some() => {
                Expr::Bin(cmp.negated().unwrap_or(cmp), a, b)
            }
            (op, operand) => Expr::unary(op, operand),
        },
        Spelling::Wrap32(op) => {
            let a = next();
            Expr::bin(Bin::BitOr, Expr::bin(op, a, next()), Expr::Int(0))
        }
        Spelling::Neg32 => Expr::bin(Bin::BitOr, Expr::unary(Unary::Neg, next()), Expr::Int(0)),
        Spelling::Imul => {
            let a = next();
            Expr::name("Math").method("imul", vec![a, next()])
        }
        Spelling::Clamp64(op) => {
            let a = next();
            Expr::name("clampInt64").call(vec![Expr::bin(op, a, next())])
        }
        Spelling::Neg64 => Expr::name("clampInt64").call(vec![Expr::unary(Unary::Neg, next())]),
        Spelling::Helper(name) | Spelling::SignOf(name) => Expr::name(name).call(Vec::new()),
        Spelling::CmpWith(name, op) => {
            let a = next();
            let call = Expr::name(name).call(vec![a, next()]);
            Expr::bin(op, call, Expr::Int(0))
        }
        Spelling::BoolCmp => {
            let a = Expr::name("Number").call(vec![next()]);
            let b = Expr::name("Number").call(vec![next()]);
            Expr::bin(Bin::Sub, a, b)
        }
        Spelling::Cat => {
            let a = next();
            Expr::bin(Bin::Add, a, next())
        }
    }
}

/// Every operator's name, if it has no spelling.
pub fn unspelled() -> Vec<&'static str> {
    (0..OPS.len() as u32)
        .filter(|&i| spelling(Op(i)).is_none())
        .map(|i| OPS[i as usize].name)
        .collect()
}
