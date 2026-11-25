//! The operator table: the 69 operators on builtin types, each with its
//! signature (notes/tmpl/nodes.md, "Ops, and core as declarations").
//!
//! Per type: `eq`, `lt`, `le`, `gt`, `ge`, and `cmp` (`Boolean`, `Int32`,
//! `Int64`, `Float64`, and `String`); `add`, `sub`, `mul`, `div`, `mod`, and
//! `neg` (the numeric types); `divSafe` and `modSafe` (the integer types:
//! by a divisor known not to fail them, a constant other than 0, and for
//! `modSafe` positive, which skip the zero check, as Kotlin's
//! `DivIntIntSafe` and the like). No operator fails: integer `div` and
//! `mod` panic for a zero divisor; `Float64`'s give IEEE 754's results; `bitAnd`, `bitOr`, `bitXor`, `bitNot`, `shl`, `shr`, and
//! `ushr` (the integer types); `Float64.pow`; `Boolean.not`; and
//! `String.cat`. They belong to the language, as Kotlin's
//! `BuiltinOperatorId`s do, so passes match them by enum. Everything else
//! core offers (`Int32.toString`, `console.log`, …) is a declaration,
//! called like any other. Each operator has one entry per operand type,
//! never a generic one (notes/tmpl/nodes.md, "Equality"). Each backend's
//! table will be checked against this one.

use crate::ids::TypeId;
use std::sync::OnceLock;

/// A builtin operator, by its index in [`OPS`].
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct Op(pub u32);

/// An operator's name and signature.
#[derive(Debug)]
pub struct OpInfo {
    /// As text writes it after `%`: `Int32.add`, `console.log`.
    pub name: &'static str,
    pub params: &'static [TypeId],
    pub result: TypeId,
    /// Whether it has no side effects: every operator but integer `div`
    /// and `mod`, which panic for a zero divisor (a panic shows, so it
    /// keeps its place among what else shows).
    pub pure: bool,
}

impl Op {
    /// The operator with this name.
    pub fn named(name: &str) -> Option<Op> {
        static SORTED: OnceLock<Vec<u32>> = OnceLock::new();
        let sorted = SORTED.get_or_init(|| {
            let mut sorted: Vec<u32> = (0..OPS.len() as u32).collect();
            sorted.sort_by_key(|&i| OPS[i as usize].name);
            sorted
        });
        let i = sorted
            .binary_search_by_key(&name, |&i| OPS[i as usize].name)
            .ok()?;
        Some(Op(sorted[i]))
    }

    /// Whether it has no side effects ([`OpInfo::pure`]).
    pub fn pure(self) -> bool {
        self.info().is_some_and(|info| info.pure)
    }

    /// Its entry, if it's an operator at all.
    pub fn info(self) -> Option<&'static OpInfo> {
        OPS.get(self.0 as usize)
    }
}

const B: TypeId = TypeId::BOOLEAN;
const I: TypeId = TypeId::INT32;
const L: TypeId = TypeId::INT64;
const F: TypeId = TypeId::FLOAT64;
const S: TypeId = TypeId::STRING;

macro_rules! ops {
    ($($name:literal ($($param:ident),*) -> $result:ident $($panics:ident)?;)*) => {
        /// Every operator, in a fixed order: an operator's index is its id.
        pub const OPS: &[OpInfo] = &[$(
            op($name, &[$($param),*], $result, !panics!($($panics)?)),
        )*];
    };
}

macro_rules! panics {
    () => {
        false
    };
    (panics) => {
        true
    };
}

const fn op(name: &'static str, params: &'static [TypeId], result: TypeId, pure: bool) -> OpInfo {
    OpInfo {
        name,
        params,
        result,
        pure,
    }
}

ops! {
    "Boolean.not" (B) -> B;
    "Boolean.eq" (B, B) -> B;
    "Boolean.lt" (B, B) -> B;
    "Boolean.le" (B, B) -> B;
    "Boolean.gt" (B, B) -> B;
    "Boolean.ge" (B, B) -> B;
    "Boolean.cmp" (B, B) -> I;

    "Int32.add" (I, I) -> I;
    "Int32.sub" (I, I) -> I;
    "Int32.mul" (I, I) -> I;
    "Int32.div" (I, I) -> I panics;
    "Int32.mod" (I, I) -> I panics;
    "Int32.divSafe" (I, I) -> I;
    "Int32.modSafe" (I, I) -> I;
    "Int32.neg" (I) -> I;
    "Int32.eq" (I, I) -> B;
    "Int32.lt" (I, I) -> B;
    "Int32.le" (I, I) -> B;
    "Int32.gt" (I, I) -> B;
    "Int32.ge" (I, I) -> B;
    "Int32.cmp" (I, I) -> I;
    "Int32.bitAnd" (I, I) -> I;
    "Int32.bitOr" (I, I) -> I;
    "Int32.bitXor" (I, I) -> I;
    "Int32.bitNot" (I) -> I;
    "Int32.shl" (I, I) -> I;
    "Int32.shr" (I, I) -> I;
    "Int32.ushr" (I, I) -> I;

    "Int64.add" (L, L) -> L;
    "Int64.sub" (L, L) -> L;
    "Int64.mul" (L, L) -> L;
    "Int64.div" (L, L) -> L panics;
    "Int64.mod" (L, L) -> L panics;
    "Int64.divSafe" (L, L) -> L;
    "Int64.modSafe" (L, L) -> L;
    "Int64.neg" (L) -> L;
    "Int64.eq" (L, L) -> B;
    "Int64.lt" (L, L) -> B;
    "Int64.le" (L, L) -> B;
    "Int64.gt" (L, L) -> B;
    "Int64.ge" (L, L) -> B;
    "Int64.cmp" (L, L) -> I;
    "Int64.bitAnd" (L, L) -> L;
    "Int64.bitOr" (L, L) -> L;
    "Int64.bitXor" (L, L) -> L;
    "Int64.bitNot" (L) -> L;
    "Int64.shl" (L, I) -> L;
    "Int64.shr" (L, I) -> L;
    "Int64.ushr" (L, I) -> L;

    "Float64.add" (F, F) -> F;
    "Float64.sub" (F, F) -> F;
    "Float64.mul" (F, F) -> F;
    "Float64.div" (F, F) -> F;
    "Float64.mod" (F, F) -> F;
    "Float64.neg" (F) -> F;
    "Float64.pow" (F, F) -> F;
    "Float64.eq" (F, F) -> B;
    "Float64.lt" (F, F) -> B;
    "Float64.le" (F, F) -> B;
    "Float64.gt" (F, F) -> B;
    "Float64.ge" (F, F) -> B;
    "Float64.cmp" (F, F) -> I;

    "String.eq" (S, S) -> B;
    "String.lt" (S, S) -> B;
    "String.le" (S, S) -> B;
    "String.gt" (S, S) -> B;
    "String.ge" (S, S) -> B;
    "String.cmp" (S, S) -> I;
    "String.cat" (S, S) -> S;
}
