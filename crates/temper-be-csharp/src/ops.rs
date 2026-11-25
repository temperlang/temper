//! C#'s spelling of the 69 operators on builtin types, indexed by [`Op`]
//! (notes/tmpl/nodes.md, "Ops, and core as declarations").
//!
//! As Kotlin's C# backend spells them (its `CSharpSupportNetwork.kt`),
//! where that's right: `Int32` and `Int64` are `int` and `long`, whose
//! arithmetic wraps (C# is `unchecked` unless a project says otherwise,
//! and Kotlin's `.csproj` doesn't) and whose shifts mask their count, as
//! Temper's do, so they're C#'s own operators, but for division and
//! remainder, which call the runtime's `Core.Div` and `Core.Mod` to give
//! `int.MinValue / -1` without C#'s `OverflowException` (a zero divisor
//! throws C#'s `DivideByZeroException`, a panic; `Float64`'s give IEEE
//! 754's results), and an unsigned shift, `Core.UShr`, since C# 10
//! has no `>>>`. Three-way comparisons of integers and booleans are
//! `CompareTo`, which gives -1, 0, or 1; `Float64`'s go through the
//! runtime's `Float64.Compare`, which orders NaN and signed zeros as
//! Temper does; and strings' through `StringUtil.CompareStringsByCodePoint`,
//! which orders code points, as Temper does (not UTF-16 units, as
//! `string.CompareOrdinal` would), and gives -1, 0, or 1. String equality
//! is `==`, which is ordinal. Two differences from Kotlin's: `Boolean`
//! comparisons go through `CompareTo`, since C# has no `<` on booleans
//! (Kotlin's turns `CompareTo(…) < 0` into `<`, which doesn't compile),
//! and `Float64.pow` is `Math.Pow`, as Kotlin's.

use crate::tree::{Bin, Expr, Unary};
use temper_tmpl::ops::{OPS, Op};

/// How an operator is written.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Spelling {
    /// C#'s own binary operator.
    Bin(Bin),
    /// C#'s own prefix operator.
    Unary(Unary),
    /// A static method of a runtime class (`Core`, `Float64`,
    /// `StringUtil`) or of `Math`, given the operands: `Core.Div(a, b)`.
    Static(&'static str, &'static str),
    /// Such a call, compared with 0 by the operator given:
    /// `Float64.Compare(a, b) < 0`.
    StaticWith(&'static str, &'static str, Bin),
    /// `a.CompareTo(b)`.
    CompareTo,
    /// `a.CompareTo(b)`, compared with 0 by the operator given.
    CompareToWith(Bin),
    /// String concatenation, flattened by the translator.
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

/// The runtime's code-point comparison of strings.
const STRINGS: (&str, &str) = ("StringUtil", "CompareStringsByCodePoint");
/// The runtime's comparison of `Float64`s.
const FLOATS: (&str, &str) = ("Float64", "Compare");

/// The spelling of each operator, by its name in [`OPS`]; every one has
/// one (a test checks).
pub fn spelling(op: Op) -> Option<Spelling> {
    use Spelling::*;
    let name = op.info().map_or("", |i| i.name);
    let (ty, op) = name.split_once('.').unwrap_or(("", ""));
    Some(match (ty, op) {
        ("Boolean", "not") => Unary(crate::tree::Unary::Not),
        ("Boolean", "eq") => Bin(crate::tree::Bin::Eq),
        ("Boolean", "cmp") => CompareTo,
        ("Boolean", _) => CompareToWith(compare(op)?),
        ("String", "cat") => Cat,
        ("String", "eq") => Bin(crate::tree::Bin::Eq),
        ("String", "cmp") => Static(STRINGS.0, STRINGS.1),
        ("String", _) => StaticWith(STRINGS.0, STRINGS.1, compare(op)?),
        ("Float64", "cmp") => Static(FLOATS.0, FLOATS.1),
        ("Float64", _) if compare(op).is_some() => StaticWith(FLOATS.0, FLOATS.1, compare(op)?),
        (_, "cmp") => CompareTo,
        (_, _) if compare(op).is_some() => Bin(compare(op)?),
        ("Float64", "pow") => Static("Math", "Pow"),
        // `MinValue / -1` wraps, where C# would throw; a zero divisor
        // throws C#'s `DivideByZeroException`, a panic.
        ("Int32" | "Int64", "div" | "divSafe") => Static("Core", "Div"),
        ("Int32" | "Int64", "mod" | "modSafe") => Static("Core", "Mod"),
        ("Int32" | "Int64", "ushr") => Static("Core", "UShr"),
        (_, "add") => Bin(crate::tree::Bin::Add),
        (_, "sub") => Bin(crate::tree::Bin::Sub),
        (_, "mul") => Bin(crate::tree::Bin::Mul),
        (_, "div") => Bin(crate::tree::Bin::Div),
        (_, "mod") => Bin(crate::tree::Bin::Mod),
        (_, "neg") => Unary(crate::tree::Unary::Neg),
        (_, "bitAnd") => Bin(crate::tree::Bin::BitAnd),
        (_, "bitOr") => Bin(crate::tree::Bin::BitOr),
        (_, "bitXor") => Bin(crate::tree::Bin::BitXor),
        (_, "bitNot") => Unary(crate::tree::Unary::BitNot),
        (_, "shl") => Bin(crate::tree::Bin::Shl),
        (_, "shr") => Bin(crate::tree::Bin::Shr),
        _ => return None,
    })
}

/// The class a spelling calls a static method of, if any.
pub fn class(spelling: Spelling) -> Option<&'static str> {
    match spelling {
        Spelling::Static(class, _) | Spelling::StaticWith(class, _, _) => Some(class),
        _ => None,
    }
}

/// Writes an operator applied to its translated operands, with `class`
/// writing a class's name as the module has to (qualified where a name of
/// the module's would hide it).
pub fn apply(spelling: Spelling, args: Vec<Expr>, class: impl Fn(&str) -> Expr) -> Expr {
    let mut args = args.into_iter();
    let mut next = || args.next().unwrap_or(Expr::Name("null".into()));
    match spelling {
        Spelling::Bin(op) => {
            let a = next();
            Expr::bin(op, a, next())
        }
        Spelling::Unary(op) => match (op, next()) {
            // `!(a < b)` reads better as `a >= b`; no comparison here has
            // a NaN operand (floats' compare `Float64.Compare`'s result).
            (Unary::Not, Expr::Bin(cmp, a, b)) if cmp.negated().is_some() => {
                Expr::Bin(cmp.negated().unwrap_or(cmp), a, b)
            }
            (op, operand) => Expr::unary(op, operand),
        },
        Spelling::Static(name, method) => {
            let a = next();
            class(name).method(method, vec![a, next()])
        }
        Spelling::StaticWith(name, method, op) => {
            let a = next();
            let call = class(name).method(method, vec![a, next()]);
            Expr::bin(op, call, Expr::Int(0))
        }
        Spelling::CompareTo => {
            let a = next();
            a.method("CompareTo", vec![next()])
        }
        Spelling::CompareToWith(op) => {
            let a = next();
            Expr::bin(op, a.method("CompareTo", vec![next()]), Expr::Int(0))
        }
        Spelling::Cat => {
            let a = next();
            Expr::Concat(vec![a, next()])
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
