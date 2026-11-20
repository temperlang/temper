//! Actions that run on every exit from a region: the one place that
//! decides how what must run however a stretch of statements ends is
//! written. Today it is a `for` loop's step, which runs after the body
//! whether it ends or a `continue` leaves it; RFC 661's `finally` is meant
//! to use it too, and may give it a strategy that writes the actions once.
//!
//! The strategy for now copies: each early exit that must run the
//! actions is a block of a fresh copy of them, then the jump, and the
//! body's end runs them once more:
//!
//! ```text
//! loop: while (c) { …; { step; continue loop; } …; step; }
//! ```
//!
//! The caller translates the actions again for each copy, so each copy
//! declares its own locals.
//!
//! **Output size.** With `j` jump sites that must run actions of size
//! `a`, regions nested `d` deep, and `k` jumps inside the actions
//! themselves:
//!
//! - Copying, as here: `O((j + 1)·a)` per region. Regions nested in the
//!   body, or jumps from blocks nested in it (a `do { … continue … }`
//!   that is a value), add their own sites, each one copy, so nesting is
//!   additive: `O(Σ (j_i + 1)·a_i)` over the regions, linear in the
//!   source for fixed `a`. It turns exponential only if the actions
//!   themselves hold regions with exits that copy their own actions
//!   (`a` then includes copies, about `(j + 1)^d` at depth `d`). That
//!   can't happen here: a step holds no loops (to_tmpl refuses one that
//!   does), and a jump inside a loop's own step (`i = next(i) ??
//!   continue`, `k > 0`), which the interpreter runs as running the step
//!   again, is refused too, so copies hold no copies.
//! - javac does the same for `finally` and people live with it: nested
//!   `try`s, or a `try`/`finally` in loops with jumps at each level, grow
//!   linearly (about 115 bytes of bytecode per level, measured with javac
//!   21), but a `finally` whose body holds its own `try`/`finally` with a
//!   jump grows about 4x per level (371, 562, 3,383, 52,236 bytes at
//!   depths 1, 2, 4, 6; "code too large" at 8).
//! - Writing the actions once per region instead (a labeled one-shot
//!   region left by `break`, `goto`, or an exit code dispatched after the
//!   actions) is `O(j + a)` per region whatever the nesting; that waits
//!   for RFC 661, which needs it for a `finally` holding jumps.

use crate::Builder;
use crate::ids::NodeId;

/// How a region's exits run its actions ([module docs](self)).
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Region;

impl Region {
    /// An early exit, `jump`, that runs the actions first: `copy`, a
    /// fresh translation of them.
    pub fn leave(&self, b: &mut Builder, copy: &[NodeId], jump: NodeId) -> NodeId {
        let mut stmts = copy.to_vec();
        stmts.push(jump);
        b.block(&stmts)
    }

    /// The body's statements, then the actions, as they run when the body
    /// ends.
    pub fn close(&self, body: &[NodeId], actions: &[NodeId]) -> Vec<NodeId> {
        let mut stmts = body.to_vec();
        stmts.extend_from_slice(actions);
        stmts
    }
}
