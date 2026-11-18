//! The canonical text form: complete, deterministic, and Temper-like, but
//! never mistaken for Temper (notes/tmpl/formats.md, Level 3).
//!
//! [`write()`] leaves out the types that the local rules give back, which
//! [`read`] restores and checks. Reading then writing gives the same
//! text, byte for byte; writing then reading gives an equal program.
//! That covers subset A and subset B's classes and interfaces, where
//! `this` goes bare in a member's body, its class giving its type.
//!
//! ```text
//! tmpl 1 model 1 library example 0.1.0
//!
//! module example#0 {
//!   export fn twice#1(n#2: Int32): Int32 {
//!     return %Int32.mul(n#2, 2);
//!   }
//!
//!   init {}
//! }
//! ```

mod read;
mod write;

pub use read::{ReadError, read};
pub(crate) use write::name;
pub use write::write;

use crate::Program;
use crate::ids::NodeId;

/// The text format's version, written first in every file.
pub const FORMAT_VERSION: u32 = 1;
/// The model's version: what nodes and fields mean.
pub const MODEL_VERSION: u32 = 1;

/// Every node in the order text writes them (preorder, declarations
/// included), which numbers nodes in the spans section and in the
/// validator's problems.
pub fn writing_order(p: &Program) -> Vec<NodeId> {
    let mut order = Vec::with_capacity(p.nodes.len());
    let mut stack = Vec::new();
    for module in &p.modules {
        for root in module.roots() {
            stack.push(root);
            while let Some(id) = stack.pop() {
                if !p.nodes.contains(id) {
                    continue;
                }
                order.push(id);
                // Children come before parents; anything else is the
                // validator's S1, and mustn't loop here.
                let kids = p.nodes.kids(id).iter().rev();
                stack.extend(kids.filter(|&&kid| kid < id));
            }
        }
    }
    order
}

/// Whether `name` can be written bare, rather than in backquotes.
fn is_plain_name(name: &str) -> bool {
    let mut chars = name.chars();
    chars
        .next()
        .is_some_and(|c| c.is_ascii_alphabetic() || c == '_')
        && chars.all(|c| c.is_ascii_alphanumeric() || c == '_')
}
