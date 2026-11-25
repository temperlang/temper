//! Java's spelling of the 69 operators on builtin types, indexed by
//! [`Op`] (notes/tmpl/nodes.md, "Ops, and core as declarations").
//!
//! As Kotlin's Java backend spells them (its `JavaSupportNetwork.kt`),
//! where that's right: `Int32` and `Int64` are `int` and `long`, whose
//! arithmetic wraps and whose shifts mask their count, as Temper's do, so
//! they're Java's own operators, division and remainder too, whose
//! `ArithmeticException` for a zero divisor is a panic (a host's error,
//! which no handler catches), and whose `MIN_VALUE / -1` wraps as
//! Temper's does;
//! three-way comparisons are `Integer.compare` and its kin, and a
//! string's `compareTo`; string equality is `equals`; `Float64.pow` is
//! `Math.pow`; and `Float64` comparisons go through `Double.compare`,
//! which orders NaN and signed zeros as Temper does. Two differences from
//! Kotlin's: `Float64.eq` is `Double.compare(a, b) == 0`, where Kotlin's
//! compares `Double.doubleToLongBits`, which is the same; and `Boolean`
//! comparisons go through `Boolean.compare`, since Java has no `<` on
//! booleans. And string comparisons call a helper that orders code
//! points, as Temper does ([`COMPARE_CODE_POINTS`]), where Kotlin's call
//! `compareTo`, which orders UTF-16 code units, so a character above
//! U+FFFF sorts before one from U+E000 to U+FFFF, and whose `<=>` gives a
//! difference, not -1, 0, or 1.

use crate::tree::{Bin, Expr, Unary};
use temper_tmpl::ops::{OPS, Op};

/// How an operator is written.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Spelling {
    /// Java's own binary operator.
    Bin(Bin),
    /// Java's own prefix operator.
    Unary(Unary),
    /// A static method of a class, given the operands:
    /// `Integer.compare(a, b)`.
    Static(&'static str, &'static str),
    /// A static method of a class, compared with 0 by the operator given:
    /// `Double.compare(a, b) < 0`.
    CmpWith(&'static str, Bin),
    /// `compareCodePoints(a, b)`, the module's helper
    /// ([`COMPARE_CODE_POINTS`]), which the translator names.
    CodePoints,
    /// `compareCodePoints(a, b)`, compared with 0 by the operator given.
    CodePointsWith(Bin),
    /// `a.equals(b)`.
    Equals,
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

/// The class whose `compare` orders a type's values.
fn boxed(ty: &str) -> Option<&'static str> {
    Some(match ty {
        "Boolean" => "Boolean",
        "Int32" => "Integer",
        "Int64" => "Long",
        "Float64" => "Double",
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
        ("String", "cat") => Cat,
        ("String", "cmp") => CodePoints,
        ("String", "eq") => Equals,
        ("String", _) => CodePointsWith(compare(op)?),
        (_, "cmp") => Static(boxed(ty)?, "compare"),
        ("Boolean" | "Float64", _) if compare(op).is_some() => CmpWith(boxed(ty)?, compare(op)?),
        (_, _) if compare(op).is_some() => Bin(compare(op)?),
        ("Float64", "pow") => Static("Math", "pow"),
        (_, "div" | "divSafe") => Bin(crate::tree::Bin::Div),
        (_, "mod" | "modSafe") => Bin(crate::tree::Bin::Mod),
        (_, "add") => Bin(crate::tree::Bin::Add),
        (_, "sub") => Bin(crate::tree::Bin::Sub),
        (_, "mul") => Bin(crate::tree::Bin::Mul),
        (_, "neg") => Unary(crate::tree::Unary::Neg),
        (_, "bitAnd") => Bin(crate::tree::Bin::BitAnd),
        (_, "bitOr") => Bin(crate::tree::Bin::BitOr),
        (_, "bitXor") => Bin(crate::tree::Bin::BitXor),
        (_, "bitNot") => Unary(crate::tree::Unary::BitNot),
        (_, "shl") => Bin(crate::tree::Bin::Shl),
        (_, "shr") => Bin(crate::tree::Bin::Shr),
        (_, "ushr") => Bin(crate::tree::Bin::Ushr),
        _ => return None,
    })
}

/// Writes an operator applied to its translated operands.
pub fn apply(spelling: Spelling, args: Vec<Expr>) -> Expr {
    let mut args = args.into_iter();
    let mut next = || args.next().unwrap_or(Expr::Name("null".into()));
    match spelling {
        Spelling::Bin(op) => {
            let a = next();
            Expr::bin(op, a, next())
        }
        Spelling::Unary(op) => match (op, next()) {
            // `!(a < b)` reads better as `a >= b`; no comparison here has
            // a NaN operand (floats' compare `Double.compare`'s result).
            (Unary::Not, Expr::Bin(cmp, a, b)) if cmp.negated().is_some() => {
                Expr::Bin(cmp.negated().unwrap_or(cmp), a, b)
            }
            (op, operand) => Expr::unary(op, operand),
        },
        Spelling::Static(class, method) => {
            let a = next();
            Expr::name(class).method(method, vec![a, next()])
        }
        Spelling::CmpWith(class, op) => {
            let a = next();
            let call = Expr::name(class).method("compare", vec![a, next()]);
            Expr::bin(op, call, Expr::Int(0))
        }
        Spelling::CodePoints => {
            let a = next();
            Expr::name(COMPARE_CODE_POINTS).call(vec![a, next()])
        }
        Spelling::CodePointsWith(op) => {
            let a = next();
            let call = Expr::name(COMPARE_CODE_POINTS).call(vec![a, next()]);
            Expr::bin(op, call, Expr::Int(0))
        }
        Spelling::Equals => {
            let a = next();
            a.method("equals", vec![next()])
        }
        Spelling::Cat => {
            let a = next();
            Expr::Concat(vec![a, next()])
        }
    }
}

/// The name of the helper that compares strings by code point, which a
/// module's class gets when it compares strings, as a private static
/// method ([`compare_code_points`]). The translator renames calls of it
/// if one of the module's functions has the name.
pub const COMPARE_CODE_POINTS: &str = "compareCodePoints";

/// The helper, named `name`, as a class member: UTF-16 units compared
/// until the first difference, which is all a code-point order needs,
/// but for a surrogate there, which sorts above the rest, since its code
/// point does. `temper-core` has no such helper, and the helper is small,
/// so each class that needs it gets its own rather than a support file
/// beside the package, which would add a class to the library's package.
pub fn compare_code_points(name: &str) -> String {
    format!(
        "    /** Compares strings by code point, as Temper does: -1, 0, or 1. */
    private static int {name}(String a, String b) {{
        int length = Math.min(a.length(), b.length());
        for (int i = 0; i < length; i++) {{
            char x = a.charAt(i);
            char y = b.charAt(i);
            if (x != y) {{
                boolean surrogate = Character.isSurrogate(x);
                if (surrogate != Character.isSurrogate(y)) {{
                    return surrogate ? 1 : -1;
                }}
                return x < y ? -1 : 1;
            }}
        }}
        return Integer.signum(a.length() - b.length());
    }}
"
    )
}

/// Every operator's name, if it has no spelling.
pub fn unspelled() -> Vec<&'static str> {
    (0..OPS.len() as u32)
        .filter(|&i| spelling(Op(i)).is_none())
        .map(|i| OPS[i as usize].name)
        .collect()
}
