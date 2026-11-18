//! Lua's spelling of the 69 operators on builtin types, indexed by [`Op`]
//! (notes/tmpl/nodes.md, "Ops, and core as declarations").
//!
//! As Kotlin's Lua backend spells them (its `LuaSupportNetwork.kt`), with
//! the same `temper-core` helpers, which work on every Lua the runtime
//! supports: on Lua 5.3 and later, through its `intnew.lua`, with Lua's
//! 64-bit integers, and on 5.1 and LuaJIT, through `intold.lua`, with
//! doubles and a bit library. So no Lua 5.3 operator (`&`, `//`, `<<`, …)
//! is printed.
//!
//! - `Int32` is a Lua integer kept in range by the runtime:
//!   `temper.int32_add`, `int32_sub`, `int32_mul`, and `int32_unm` wrap
//!   their 64-bit result to 32 bits, and `int32_div` and `int32_mod`
//!   truncate toward zero, as Temper's do, rather than flooring, as Lua's
//!   `//` and `%` do, and fail on a zero divisor (Lua's integer `//`
//!   raises an error, which bubbles). Comparisons are Lua's own;
//!   `int_cmp` gives -1, 0, or 1.
//! - `Int64`: whatever the runtime's `int64_constructor` makes, as
//!   Kotlin's: a Lua integer on Lua 5.3 and later, and on 5.1 and LuaJIT
//!   a table of two 32-bit halves, whose metatable gives it `+`, `-`,
//!   `*`, `==`, `<`, and `<=`. So literals are its calls (the translator
//!   writes them), conversions from `Int32` too (`int32_toint64`), and
//!   operators are Lua's own `+`, `-`, `*`, and comparisons, which wrap
//!   at 64 bits in Lua 5.3 and later, and `int64_div`, `int64_mod`,
//!   `int64_unm`, and `int64_cmp` from the runtime. Both operands are
//!   always `Int64`s, so the metatable's always there to compare them.
//! - Bitwise operators and shifts: the runtime's `band`, `bor`, `bxor`,
//!   `bnot` (for both widths), and `shl32`, `shl64`, and the rest, which
//!   mask the count as Temper does.
//! - `Float64`: Lua's own `+`, `-`, `*`, `/`, `^`, and negation, as
//!   Kotlin's (so division by zero gives an infinity or NaN, as Kotlin's
//!   JS and Lua do, where tmpl's operators bubble); `fmod`, `float_eq`,
//!   and `float_cmp` from the runtime, which order NaN and signed zeros
//!   as Temper does, so `<` and the rest compare `float_cmp`'s result with
//!   0, as Kotlin's.
//! - `String`: Lua's own `==`, `<`, and the rest, which compare bytes with
//!   `strcoll` in the C locale (the one Lua starts in), so UTF-8 orders by
//!   code point; `str_cmp` for `<=>`; and `..` for concatenation, which
//!   the translator flattens. A host that sets another collation with
//!   `os.setlocale` would change the order, Kotlin's too.
//! - `Boolean`: `not`, `==`, and `bool_cmp`; `<` and the rest as logic
//!   (`not a and b`), since Lua can't compare booleans: Kotlin's `a < b`
//!   raises an error.

use crate::tree::{Bin, Expr, Unary};
use temper_tmpl::ops::{OPS, Op};

/// How an operator is written.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Spelling {
    /// Lua's own binary operator.
    Bin(Bin),
    /// Lua's own prefix operator.
    Unary(Unary),
    /// A call of a `temper-core` function with the operands:
    /// `temper.int32_add(a, b)`.
    Helper(&'static str),
    /// A `temper-core` three-way comparison, compared with 0 by the
    /// operator given: `temper.float_cmp(a, b) < 0`.
    CmpWith(&'static str, Bin),
    /// `Boolean`'s order (`false < true`) as logic, by the comparison.
    BoolOrder(Bin),
    /// String concatenation, `a .. b`, flattened by the translator.
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
        ("Boolean", "eq") => Bin(crate::tree::Bin::Eq),
        ("Boolean", "cmp") => Helper("bool_cmp"),
        ("Boolean", _) => BoolOrder(compare(op)?),
        ("String", "cat") => Cat,
        ("String", "cmp") => Helper("str_cmp"),
        ("Float64", "eq") => Helper("float_eq"),
        ("Float64", "cmp") => Helper("float_cmp"),
        ("Float64", _) if compare(op).is_some() => CmpWith("float_cmp", compare(op)?),
        ("Int32", "cmp") => Helper("int_cmp"),
        ("Int64", "cmp") => Helper("int64_cmp"),
        (_, _) if compare(op).is_some() => Bin(compare(op)?),
        ("Float64", "add") | ("Int64", "add") => Bin(crate::tree::Bin::Add),
        ("Float64", "sub") | ("Int64", "sub") => Bin(crate::tree::Bin::Sub),
        ("Float64", "mul") | ("Int64", "mul") => Bin(crate::tree::Bin::Mul),
        ("Float64", "div") => Bin(crate::tree::Bin::Div),
        ("Float64", "mod") => Helper("fmod"),
        ("Float64", "neg") => Unary(crate::tree::Unary::Neg),
        ("Float64", "pow") => Bin(crate::tree::Bin::Pow),
        ("Int64", "div" | "divSafe") => Helper("int64_div"),
        ("Int64", "mod" | "modSafe") => Helper("int64_mod"),
        ("Int64", "neg") => Helper("int64_unm"),
        ("Int32", "add") => Helper("int32_add"),
        ("Int32", "sub") => Helper("int32_sub"),
        ("Int32", "mul") => Helper("int32_mul"),
        // By a divisor that can't fail them too, as Kotlin's.
        ("Int32", "div" | "divSafe") => Helper("int32_div"),
        ("Int32", "mod" | "modSafe") => Helper("int32_mod"),
        ("Int32", "neg") => Helper("int32_unm"),
        (_, "bitAnd") => Helper("band"),
        (_, "bitOr") => Helper("bor"),
        (_, "bitXor") => Helper("bxor"),
        (_, "bitNot") => Helper("bnot"),
        ("Int32", "shl") => Helper("shl32"),
        ("Int32", "shr") => Helper("shr32"),
        ("Int32", "ushr") => Helper("ushr32"),
        ("Int64", "shl") => Helper("shl64"),
        ("Int64", "shr") => Helper("shr64"),
        ("Int64", "ushr") => Helper("ushr64"),
        _ => return None,
    })
}

/// The runtime helper a spelling calls, as `temper.NAME`.
pub fn helper(spelling: Spelling) -> Option<&'static str> {
    match spelling {
        Spelling::Helper(name) | Spelling::CmpWith(name, _) => Some(name),
        _ => None,
    }
}

/// The runtime's table, as generated code names it.
pub fn temper() -> Expr {
    Expr::name("temper")
}

/// Writes an operator applied to its translated operands.
pub fn apply(spelling: Spelling, mut args: Vec<Expr>) -> Expr {
    if let Spelling::Helper(name) = spelling {
        return temper().method(name, args);
    }
    let mut next = || {
        if args.is_empty() {
            Expr::Nil
        } else {
            args.remove(0)
        }
    };
    match spelling {
        Spelling::Bin(op) => {
            let a = next();
            Expr::bin(op, a, next())
        }
        Spelling::Unary(Unary::Not) => next().negate(),
        Spelling::Unary(op) => Expr::unary(op, next()),
        Spelling::CmpWith(name, op) => {
            let a = next();
            let call = temper().method(name, vec![a, next()]);
            Expr::bin(op, call, Expr::Int(0))
        }
        Spelling::BoolOrder(op) => {
            let (a, b) = (next(), next());
            match op {
                // `false < true` is the only `<`.
                Bin::Lt => Expr::bin(Bin::And, a.negate(), b),
                Bin::Le => Expr::bin(Bin::Or, a.negate(), b),
                Bin::Gt => Expr::bin(Bin::And, a, b.negate()),
                Bin::Ge => Expr::bin(Bin::Or, a, b.negate()),
                _ => Expr::bin(op, a, b),
            }
        }
        Spelling::Cat => {
            let a = next();
            Expr::Concat(vec![a, next()])
        }
        Spelling::Helper(_) => Expr::Nil,
    }
}

/// Every operator's name, if it has no spelling.
pub fn unspelled() -> Vec<&'static str> {
    (0..OPS.len() as u32)
        .filter(|&i| spelling(Op(i)).is_none())
        .map(|i| OPS[i as usize].name)
        .collect()
}
