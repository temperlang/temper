//! Python's spelling of the 69 operators on builtin types, indexed by
//! [`Op`] (notes/tmpl/nodes.md, "Ops, and core as declarations").
//!
//! `Int32` and `Int64` arithmetic that can overflow calls `temper_core`'s
//! wrapping helpers (`int_add`, `int64_mul`, …), as Kotlin's Python
//! backend does, and division and remainder call helpers that bubble for
//! a zero divisor (`int_div`, `float64_div`, …), as the runtime's
//! `Bubble`, where Python raises `ZeroDivisionError` (but `Float64`
//! division by a nonzero literal is Python's `/`); everything Python's
//! own operators get right is written with them. Three-way comparisons of ints and strings are
//! `(a > b) - (a < b)`, which gives -1, 0, or 1, where the operands are
//! names or literals; otherwise ints' is `a - b`, as Kotlin's, which has
//! the right sign, and strings' waits for operand temporaries (the
//! hoisting pass), since each operand must be evaluated once.

use crate::tree::{Bin, Expr, Unary};
use temper_tmpl::ops::{OPS, Op};

/// How an operator is written.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Spelling {
    /// Python's own binary operator.
    Bin(Bin),
    /// Python's own unary operator.
    Unary(Unary),
    /// A call of a runtime helper, from the module named.
    Helper(&'static str, &'static str),
    /// `(a > b) - (a < b)`, or for ints with operands that aren't
    /// simple, `a - b`.
    Cmp { ints: bool },
    /// `a >> (b & mask)`.
    Shr(i64),
    /// String concatenation: an f-string, flattened by the translator.
    Cat,
    /// `float_cmp(a, b) < 0` and the like: `Float64`'s order, where `-0.0`
    /// is below `0.0` and NaN above everything, as Kotlin's (Python's own
    /// `<` gets both wrong).
    FloatCmp(Bin),
}

const CORE: &str = "temper_core";

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
        (_, "eq") if ty == "Float64" => Helper(CORE, "float_eq"),
        (_, "eq") => Bin(crate::tree::Bin::Eq),
        ("Float64", "lt") => FloatCmp(crate::tree::Bin::Lt),
        ("Float64", "le") => FloatCmp(crate::tree::Bin::Le),
        ("Float64", "gt") => FloatCmp(crate::tree::Bin::Gt),
        ("Float64", "ge") => FloatCmp(crate::tree::Bin::Ge),
        (_, "lt") => Bin(crate::tree::Bin::Lt),
        (_, "le") => Bin(crate::tree::Bin::Le),
        (_, "gt") => Bin(crate::tree::Bin::Gt),
        (_, "ge") => Bin(crate::tree::Bin::Ge),
        ("Boolean", "cmp") => Helper(CORE, "bool_cmp"),
        ("Float64", "cmp") => Helper(CORE, "float_cmp"),
        (_, "cmp") => Cmp {
            ints: ty != "String",
        },
        ("Float64", "add") => Bin(crate::tree::Bin::Add),
        ("Float64", "sub") => Bin(crate::tree::Bin::Sub),
        ("Float64", "mul") => Bin(crate::tree::Bin::Mul),
        // Bubbling for a zero divisor, where Python raises.
        ("Float64", "div") => Helper(CORE, "float64_div"),
        ("Float64", "mod") => Helper(CORE, "float64_mod"),
        ("Float64", "neg") => Unary(crate::tree::Unary::Neg),
        ("Float64", "pow") => Bin(crate::tree::Bin::Pow),
        (_, "add") => Helper(CORE, if int64 { "int64_add" } else { "int_add" }),
        (_, "sub") => Helper(CORE, if int64 { "int64_sub" } else { "int_sub" }),
        (_, "mul") => Helper(CORE, if int64 { "int64_mul" } else { "int_mul" }),
        // By a divisor that can't fail them too, as Kotlin's.
        (_, "div" | "divSafe") => Helper(CORE, if int64 { "int64_div" } else { "int_div" }),
        (_, "mod" | "modSafe") => Helper(CORE, "arith_int_mod"),
        (_, "neg") => Helper(CORE, if int64 { "int64_negate" } else { "int_negate" }),
        (_, "bitAnd") => Bin(crate::tree::Bin::BitAnd),
        (_, "bitOr") => Bin(crate::tree::Bin::BitOr),
        (_, "bitXor") => Bin(crate::tree::Bin::BitXor),
        (_, "bitNot") => Unary(crate::tree::Unary::Invert),
        (_, "shl") => Helper(
            CORE,
            if int64 {
                "arith_bit_shl64"
            } else {
                "arith_bit_shl32"
            },
        ),
        (_, "shr") => Shr(if int64 { 0x3f } else { 0x1f }),
        (_, "ushr") => Helper(
            CORE,
            if int64 {
                "arith_bit_ushr64"
            } else {
                "arith_bit_ushr32"
            },
        ),
        _ => return None,
    })
}

/// Whether an operand can be written twice: a name or a literal.
fn simple(e: &Expr) -> bool {
    matches!(
        e,
        Expr::Name(_) | Expr::Int(_) | Expr::Float(_) | Expr::Str(_) | Expr::Bool(_) | Expr::None
    )
}

/// Writes an operator applied to its translated operands, with `helper`
/// the module's name for a helper or `float_cmp`, imported; `None` where
/// that needs a temporary, which waits for the hoisting pass.
pub fn apply(spelling: Spelling, mut args: Vec<Expr>, helper: &str) -> Option<Expr> {
    let second = |args: &mut Vec<Expr>| args.pop().unwrap_or(Expr::None);
    Some(match spelling {
        Spelling::Bin(op) => {
            let b = second(&mut args);
            let a = second(&mut args);
            Expr::bin(op, a, b)
        }
        Spelling::Unary(op) => match (op, second(&mut args)) {
            // `!(a == b)` reads better as `a != b`, which Python's `==`
            // makes exact (floats' `eq` is a helper, not `==`).
            (Unary::Not, Expr::Bin(Bin::Eq, a, b)) => Expr::Bin(Bin::Ne, a, b),
            (Unary::Not, Expr::Bin(Bin::Is, a, b)) => Expr::Bin(Bin::IsNot, a, b),
            (op, operand) => Expr::unary(op, operand),
        },
        Spelling::Helper(..) => Expr::name(helper).call(args),
        Spelling::FloatCmp(op) => Expr::bin(op, Expr::name(helper).call(args), Expr::Int(0)),
        Spelling::Cmp { ints } => {
            let b = second(&mut args);
            let a = second(&mut args);
            if !(simple(&a) && simple(&b)) {
                return ints.then(|| Expr::bin(Bin::Sub, a, b));
            }
            Expr::bin(
                Bin::Sub,
                Expr::bin(Bin::Gt, a.clone(), b.clone()),
                Expr::bin(Bin::Lt, a, b),
            )
        }
        Spelling::Shr(mask) => {
            let b = second(&mut args);
            let a = second(&mut args);
            Expr::bin(Bin::Shr, a, Expr::bin(Bin::BitAnd, b, Expr::Int(mask)))
        }
        Spelling::Cat => {
            let b = second(&mut args);
            let a = second(&mut args);
            Expr::bin(Bin::Add, a, b)
        }
    })
}

/// Every operator's name, if it has no spelling.
pub fn unspelled() -> Vec<&'static str> {
    (0..OPS.len() as u32)
        .filter(|&i| spelling(Op(i)).is_none())
        .map(|i| OPS[i as usize].name)
        .collect()
}
