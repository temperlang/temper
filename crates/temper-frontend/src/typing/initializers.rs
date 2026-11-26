//! A local declared without a type or a value, as `var nc;`, takes its
//! type from its initializers, as Kotlin Temper's `TyperPlan` does: the
//! assignments to it that no other assignment to it comes before along
//! any path from the declaration (ignoring loops' back edges). Their
//! types' join is its type, so `let d; if (c) { d = f() } else { d = null
//! }` is `f()`'s type, nullable. Assignments in other functions, which
//! may run at any time, don't count.

use super::*;

impl Settle {
    /// A declaration's type from its initializers, waiting for their
    /// values' types unless `forced`; `None` if it has none or isn't a
    /// local or top-level name.
    pub(super) fn initializers_type(
        &mut self,
        decl: NodeId,
        forced: bool,
    ) -> Result<Option<TypeId>, Fact> {
        let t = self.initializers_join(decl, forced)?;
        Ok(t.filter(|&t| t != NULL && t != NEVER && t != UNKNOWN))
    }

    /// The join of the types of a declaration's initializers, as
    /// [`Settle::initializers_type`] finds them, whatever it is.
    pub(super) fn initializers_join(
        &mut self,
        decl: NodeId,
        forced: bool,
    ) -> Result<Option<TypeId>, Fact> {
        if !self.is_value_decl(decl) {
            return Ok(None);
        }
        let Some(block) = self.typing.parent(decl) else {
            return Ok(None);
        };
        let children = self.lib.tree.children(block);
        let Some(at) = children.iter().position(|&c| c == decl) else {
            return Ok(None);
        };
        let mut found = Vec::new();
        let mut assigned = false;
        for &c in &children[at + 1..] {
            assigned = self.find_initializers(c, decl, assigned, &mut found);
        }
        // An initializer that reads the local, as `c = c + 1`, reads it
        // unknown, as Kotlin's reads it `Invalid`.
        if self.typing.initializing.contains(&decl) {
            return Ok(None);
        }
        self.typing.initializing.push(decl);
        let t = self.join_values(&found, forced);
        self.typing.initializing.pop();
        t
    }

    /// The join of the values' types, if any.
    fn join_values(&mut self, values: &[NodeId], forced: bool) -> Result<Option<TypeId>, Fact> {
        let mut t = None;
        for &value in values {
            let v = self.expr_type(value, forced)?;
            t = Some(match t {
                Some(t) => self.typing.types.join(t, v),
                None => v,
            });
        }
        Ok(t)
    }

    /// Adds to `found` the values of the assignments to `decl` in `node`
    /// that come first along some path, given whether it's `assigned`
    /// along some path already; gives whether it may be after `node`.
    fn find_initializers(
        &self,
        node: NodeId,
        decl: NodeId,
        assigned: bool,
        found: &mut Vec<NodeId>,
    ) -> bool {
        let tree = &self.lib.tree;
        let children = tree.children(node);
        match tree.kind(node) {
            // Another function's body runs whenever it's called, but a
            // `for … of` loop's body runs in place.
            NodeKind::Fn if tree.data(node) & core::FN_LOOP_BODY == 0 => assigned,
            NodeKind::If if children.len() == 3 => {
                let after_cond = self.find_initializers(children[0], decl, assigned, found);
                let then = self.find_initializers(children[1], decl, after_cond, found);
                let els = self.find_initializers(children[2], decl, after_cond, found);
                then || els
            }
            NodeKind::Assign if children.len() == 2 => {
                let assigned = self.find_initializers(children[1], decl, assigned, found);
                if self.local_target(children[0]) != Some(decl) {
                    return self.find_initializers(children[0], decl, assigned, found);
                }
                if !assigned {
                    found.push(children[1]);
                }
                true
            }
            _ => {
                (children.iter()).fold(assigned, |a, &c| self.find_initializers(c, decl, a, found))
            }
        }
    }
}
