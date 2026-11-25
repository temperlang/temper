//! C++'s spelling of the 69 operators on builtin types, indexed by [`Op`]
//! (notes/tmpl/nodes.md, "Ops, and core as declarations").
//!
//! As Kotlin's C++ backend spells them (its `CppSupportNetwork.kt`),
//! where that's right: `Int32` and `Int64` arithmetic goes through the
//! runtime's `Int` and `Int64` helpers (`core::Int::add`), which wrap as
//! Temper's does rather than overflow, which C++ leaves undefined, and
//! division and remainder through `div_wrap` and `mod_wrap`, which panic
//! on a zero divisor and give `INT32_MIN / -1` without C++'s undefined
//! behavior, or by a divisor that can't be zero, `div_safe` and
//! `mod_safe`; comparisons, bitwise operators, and `Float64`'s arithmetic
//! are C++'s own; `Float64`'s comparisons go through the runtime's
//! `Float64::lt` and the like, which order NaN and signed zeros as Temper
//! does; three-way comparisons through `Compare::cmp` and
//! `Float64::cmp`, which give -1, 0, or 1. Strings compare with C++'s own
//! operators, which compare `core::Str`'s bytes as `unsigned char`, so
//! UTF-8 orders by code point, as Temper does.
//!
//! Differences from Kotlin's: shifts go through the runtime's `Int::shl`
//! and `Int::shr`, which mask the count, as Temper does, where Kotlin's
//! `a << b` is undefined for a negative `a` or a count past 31 (`>>` with
//! a count from 0 to 31 is C++'s own); and the translator writes C++'s
//! own `/` and `%` where the divisor is a literal other than 0 and -1, so
//! neither the zero check nor the overflow can apply.

use crate::tree::{Bin, Unary};
use temper_tmpl::ops::{OPS, Op};

/// How an operator is written.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Spelling {
    /// C++'s own binary operator.
    Bin(Bin),
    /// C++'s own prefix operator.
    Unary(Unary),
    /// A function, given the operands: of the runtime's, relative to
    /// `temper::core` (`Int::add`), or of the standard library's,
    /// qualified (`std::fmod`).
    Call(&'static str),
    /// A division or remainder: C++'s own operator where the divisor is a
    /// literal other than 0 and -1, else the runtime's function.
    Divide(Bin, &'static str),
    /// A shift right: C++'s own `>>` where the count is a literal from 0
    /// to the width less one, else the runtime's function.
    ShiftRight(&'static str),
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

/// The spelling of each operator, by its name in [`OPS`]; every one has
/// one (a test checks).
pub fn spelling(op: Op) -> Option<Spelling> {
    use Spelling::*;
    let name = op.info().map_or("", |i| i.name);
    let (ty, op) = name.split_once('.').unwrap_or(("", ""));
    // The runtime's namespace for each integer type.
    let int = match ty {
        "Int32" => "Int",
        _ => "Int64",
    };
    let int_fn = |f: &str| -> &'static str {
        match (int, f) {
            ("Int", "add") => "Int::add",
            ("Int", "sub") => "Int::sub",
            ("Int", "mul") => "Int::mul",
            ("Int", "neg") => "Int::neg",
            ("Int", "div") => "Int::div_wrap",
            ("Int", "mod") => "Int::mod_wrap",
            ("Int", "divSafe") => "Int::div_safe",
            ("Int", "modSafe") => "Int::mod_safe",
            ("Int", "shl") => "Int::shl",
            ("Int", "shr") => "Int::shr",
            ("Int", "ushr") => "Int::ushr",
            (_, "add") => "Int64::add",
            (_, "sub") => "Int64::sub",
            (_, "mul") => "Int64::mul",
            (_, "neg") => "Int64::neg",
            (_, "div") => "Int64::div_wrap",
            (_, "mod") => "Int64::mod_wrap",
            (_, "divSafe") => "Int64::div_safe",
            (_, "modSafe") => "Int64::mod_safe",
            (_, "shl") => "Int64::shl",
            (_, "shr") => "Int64::shr",
            _ => "Int64::ushr",
        }
    };
    Some(match (ty, op) {
        ("Boolean", "not") => Unary(crate::tree::Unary::Not),
        (_, "cmp") if ty == "Float64" => Call("Float64::cmp"),
        (_, "cmp") => Call("Compare::cmp"),
        ("Float64", "eq") => Call("Float64::eq"),
        ("Float64", "lt") => Call("Float64::lt"),
        ("Float64", "le") => Call("Float64::le"),
        ("Float64", "gt") => Call("Float64::gt"),
        ("Float64", "ge") => Call("Float64::ge"),
        (_, _) if compare(op).is_some() => Bin(compare(op)?),
        ("String", "cat") => Cat,
        ("Float64", "add") => Bin(crate::tree::Bin::Add),
        ("Float64", "sub") => Bin(crate::tree::Bin::Sub),
        ("Float64", "mul") => Bin(crate::tree::Bin::Mul),
        ("Float64", "div") => Bin(crate::tree::Bin::Div),
        ("Float64", "mod") => Call("std::fmod"),
        ("Float64", "neg") => Unary(crate::tree::Unary::Neg),
        ("Float64", "pow") => Call("Float64::pow"),
        ("Int32" | "Int64", "div") => Divide(crate::tree::Bin::Div, int_fn("div")),
        ("Int32" | "Int64", "mod") => Divide(crate::tree::Bin::Mod, int_fn("mod")),
        // By a divisor that can't fail them, as Kotlin's `div_safe` and
        // `mod_safe`, which skip the zero check.
        ("Int32" | "Int64", "divSafe") => Divide(crate::tree::Bin::Div, int_fn("divSafe")),
        ("Int32" | "Int64", "modSafe") => Divide(crate::tree::Bin::Mod, int_fn("modSafe")),
        ("Int32" | "Int64", "shr") => ShiftRight(int_fn("shr")),
        ("Int32" | "Int64", "add" | "sub" | "mul" | "neg" | "shl" | "ushr") => Call(int_fn(op)),
        ("Int32" | "Int64", "bitAnd") => Bin(crate::tree::Bin::BitAnd),
        ("Int32" | "Int64", "bitOr") => Bin(crate::tree::Bin::BitOr),
        ("Int32" | "Int64", "bitXor") => Bin(crate::tree::Bin::BitXor),
        ("Int32" | "Int64", "bitNot") => Unary(crate::tree::Unary::BitNot),
        _ => return None,
    })
}

/// The runtime's functions that spellings call, relative to
/// `temper::core`, for the test that the runtime has each.
pub fn runtime_functions() -> Vec<&'static str> {
    let mut out = Vec::new();
    for i in 0..OPS.len() as u32 {
        let f = match spelling(Op(i)) {
            Some(Spelling::Call(f))
            | Some(Spelling::Divide(_, f))
            | Some(Spelling::ShiftRight(f)) => f,
            _ => continue,
        };
        if !f.starts_with("std::") && !out.contains(&f) {
            out.push(f);
        }
    }
    out
}

/// Every operator's name, if it has no spelling.
pub fn unspelled() -> Vec<&'static str> {
    (0..OPS.len() as u32)
        .filter(|&i| spelling(Op(i)).is_none())
        .map(|i| OPS[i as usize].name)
        .collect()
}
