//! Rust's spelling of the 69 operators on builtin types, indexed by
//! [`Op`] (notes/tmpl/nodes.md, "Ops, and core as declarations").
//!
//! As Kotlin's Rust backend spells them (its `RustSupportNetwork.kt`):
//! `Int32` and `Int64` arithmetic wraps (`wrapping_add`, `wrapping_neg`,
//! …), division and remainder too (`wrapping_div` and `wrapping_rem`,
//! which panic on a zero divisor, as Temper does); `Float64`'s are Rust's
//! own, IEEE 754's; shifts mask their count, as
//! `wrapping_shl` does; `Float64` comparisons go through
//! `temper_core::float64::cmp`, which orders NaN and signed zeros as
//! Temper does; three-way comparisons are `a.cmp(&b) as i32`; and
//! everything else is Rust's own operator. String comparisons are
//! spelled by the translator, which sees whether an operand is a literal.

use crate::tree::{Bin, Expr, Unary};
use temper_tmpl::ops::{OPS, Op};

/// How an operator is written.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Spelling {
    /// Rust's own binary operator.
    Bin(Bin),
    /// Rust's own prefix operator.
    Unary(Unary),
    /// A method of the first operand, given the rest: `a.wrapping_add(b)`.
    Method(&'static str),
    /// A shift: `a.wrapping_shl(b as u32)`, which masks the count by the
    /// width, as Temper does.
    Shift(&'static str),
    /// An unsigned shift right, through the unsigned type of the width:
    /// `(a as u32).wrapping_shr(b as u32) as i32`.
    Ushr(&'static str, &'static str),
    /// `a.cmp(&b) as i32`.
    Cmp,
    /// A call of a runtime function: `temper_core::int_div(a, b)`.
    Helper(&'static str),
    /// `temper_core::float64::cmp(a, b)`, compared with 0 by the operator
    /// given, if any.
    FloatCmp(Option<Bin>),
    /// A comparison of strings.
    StrBin(Bin),
    /// `a.as_str().cmp(b.as_str()) as i32`.
    StrCmp,
    /// String concatenation: `format!`, flattened by the translator.
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
    let int64 = ty == "Int64";
    Some(match (ty, op) {
        ("Boolean", "not") => Unary(crate::tree::Unary::Not),
        ("String", "cat") => Cat,
        ("String", "cmp") => StrCmp,
        ("String", _) => StrBin(compare(op)?),
        ("Float64", "cmp") => FloatCmp(None),
        ("Float64", _) if compare(op).is_some() => FloatCmp(compare(op)),
        (_, "cmp") => Cmp,
        (_, _) if compare(op).is_some() => Bin(compare(op)?),
        ("Float64", "add") => Bin(crate::tree::Bin::Add),
        ("Float64", "sub") => Bin(crate::tree::Bin::Sub),
        ("Float64", "mul") => Bin(crate::tree::Bin::Mul),
        ("Float64", "div") => Bin(crate::tree::Bin::Div),
        ("Float64", "mod") => Bin(crate::tree::Bin::Rem),
        ("Float64", "neg") => Unary(crate::tree::Unary::Neg),
        ("Float64", "pow") => Method("powf"),
        (_, "add") => Method("wrapping_add"),
        (_, "sub") => Method("wrapping_sub"),
        (_, "mul") => Method("wrapping_mul"),
        // Rust's panic for a zero divisor is Temper's; `MIN / -1` wraps.
        (_, "div" | "divSafe") => Method("wrapping_div"),
        (_, "mod" | "modSafe") => Method("wrapping_rem"),
        (_, "neg") => Method("wrapping_neg"),
        (_, "bitAnd") => Bin(crate::tree::Bin::BitAnd),
        (_, "bitOr") => Bin(crate::tree::Bin::BitOr),
        (_, "bitXor") => Bin(crate::tree::Bin::BitXor),
        (_, "bitNot") => Unary(crate::tree::Unary::Not),
        (_, "shl") => Shift("wrapping_shl"),
        (_, "shr") => Shift("wrapping_shr"),
        (_, "ushr") if int64 => Ushr("u64", "i64"),
        (_, "ushr") => Ushr("u32", "i32"),
        _ => return None,
    })
}

/// The comparison that's true exactly when `op` is false.
fn negated(op: Bin) -> Option<Bin> {
    Some(match op {
        Bin::Eq => Bin::Ne,
        Bin::Ne => Bin::Eq,
        Bin::Lt => Bin::Ge,
        Bin::Ge => Bin::Lt,
        Bin::Le => Bin::Gt,
        Bin::Gt => Bin::Le,
        _ => return None,
    })
}

/// A shift's count as `u32`: a literal that's not negative as it is,
/// which Rust then types as `u32`, else cast, which keeps the low bits.
fn count(e: Expr) -> Expr {
    match e {
        Expr::Lit(ref text) if !text.starts_with('-') => e,
        e => e.cast("u32"),
    }
}

/// Writes an operator applied to its translated operands, for every
/// spelling but strings' ([`Spelling::StrBin`], [`Spelling::StrCmp`],
/// and [`Spelling::Cat`]), which the translator writes.
pub fn apply(spelling: Spelling, args: Vec<Expr>) -> Option<Expr> {
    let mut args = args.into_iter();
    let a = args.next()?;
    let b = args.next();
    Some(match spelling {
        Spelling::Bin(op) => Expr::bin(op, a, b?),
        Spelling::Unary(op) => match (op, a) {
            // `!(a < b)` reads better as `a >= b`, which is exact for
            // every comparison spelled with Rust's operators, since none
            // compares floats directly.
            (Unary::Not, Expr::Bin(op, x, y)) if negated(op).is_some() => {
                Expr::Bin(negated(op)?, x, y)
            }
            // `!x.is_none()` as `x.is_some()`, and back, as clippy wants.
            (Unary::Not, Expr::Method(x, name, margs))
                if margs.is_empty() && (name == "is_none" || name == "is_some") =>
            {
                let other = if name == "is_none" {
                    "is_some"
                } else {
                    "is_none"
                };
                Expr::Method(x, other.into(), margs)
            }
            (op, a) => Expr::unary(op, a),
        },
        Spelling::Method(name) => a.method(name, b.into_iter().collect()),
        Spelling::Shift(name) => a.method(name, vec![count(b?)]),
        Spelling::Ushr(unsigned, signed) => a
            .cast(unsigned)
            .method("wrapping_shr", vec![count(b?)])
            .cast(signed),
        Spelling::Cmp => a
            .method("cmp", vec![Expr::unary(Unary::Ref, b?)])
            .cast("i32"),
        Spelling::Helper(path) => Expr::path(path).call(std::iter::once(a).chain(b).collect()),
        Spelling::FloatCmp(op) => {
            let cmp = Expr::path("temper_core::float64::cmp").call(vec![a, b?]);
            match op {
                Some(op) => Expr::bin(op, cmp, Expr::Lit("0".into())),
                None => cmp,
            }
        }
        Spelling::StrBin(_) | Spelling::StrCmp | Spelling::Cat => return None,
    })
}

/// Every operator's name, if it has no spelling.
pub fn unspelled() -> Vec<&'static str> {
    (0..OPS.len() as u32)
        .filter(|&i| spelling(Op(i)).is_none())
        .map(|i| OPS[i as usize].name)
        .collect()
}
