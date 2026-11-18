//! Top-level statements run in dependency order, not source order, as
//! Kotlin Temper's `sortTopLevels` (`frontend/.../syntax/SortTopLevels.kt`)
//! arranges them, so a module may use values it declares further down.
//!
//! The rules, from Kotlin's:
//!
//! - A statement *needs* each top-level name it reads anywhere inside it,
//!   function bodies included. Reads are uses resolved to a symbol of the
//!   module's scope, so locals that shadow one don't count.
//! - A statement *declares* the name of a top-level `let` or `class`, and
//!   *assigns* each top-level name it assigns anywhere inside it, function
//!   bodies included.
//! - A statement goes after the declarations of the names it needs, then
//!   their assignments, in the order the names first appear; an assigning
//!   statement also goes after the declarations of what it assigns.
//! - The order is a depth-first walk in source order, putting each
//!   statement after what it needs, so unrelated statements keep their
//!   order. A cycle breaks where the walk enters it: that statement goes
//!   after the others in it.
//!
//! Kotlin also puts imports first. Here an import is `let a = b`, with `b`
//! another module's symbol, so it goes before what reads `a` anyway; each
//! module is sorted on its own, and runs after the modules it imports.

use crate::scope::{ScopeId, Scopes, SymbolId};
use std::collections::HashMap;
use temper_syntax::tree::{NodeId, NodeKind, Tree};

/// The module's top-level statements, from every file root in turn, in
/// the order they should run. Each file's root is also reordered to match,
/// for printing.
pub fn sort_top_levels(
    tree: &mut Tree,
    scopes: &Scopes,
    module: ScopeId,
    roots: &[NodeId],
) -> Vec<NodeId> {
    let order = top_level_order(tree, scopes, module, roots);
    // Each file keeps its own statements, in the new order.
    let mut file_of = HashMap::new();
    for (f, &root) in roots.iter().enumerate() {
        for &c in tree.children(root) {
            file_of.insert(c, f);
        }
    }
    for (f, &root) in roots.iter().enumerate() {
        let mine: Vec<NodeId> = order.iter().copied().filter(|n| file_of[n] == f).collect();
        tree.set_children(root, &mine);
    }
    order
}

/// The module's top-level statements, from every file root in turn, in
/// the order they should run, leaving the tree as it is.
pub(crate) fn top_level_order(
    tree: &Tree,
    scopes: &Scopes,
    module: ScopeId,
    roots: &[NodeId],
) -> Vec<NodeId> {
    let statements: Vec<NodeId> = roots
        .iter()
        .flat_map(|&r| tree.children(r).iter().copied())
        .collect();
    let top = |node: NodeId| {
        scopes
            .resolved(node)
            .filter(|&b| scopes.symbol(b).owner == module)
    };
    let mut decls: HashMap<SymbolId, Vec<usize>> = HashMap::new();
    let mut assigns: HashMap<SymbolId, Vec<usize>> = HashMap::new();
    let mut needs: Vec<Vec<SymbolId>> = Vec::with_capacity(statements.len());
    let mut assigned: Vec<Vec<SymbolId>> = Vec::with_capacity(statements.len());
    let mut stack = Vec::new();
    for (i, &statement) in statements.iter().enumerate() {
        if matches!(tree.kind(statement), NodeKind::Let | NodeKind::Class) {
            if let Some(b) = top(tree.children(statement)[0]) {
                decls.entry(b).or_default().push(i);
            }
        }
        let (mut reads, mut writes) = (Vec::new(), Vec::new());
        stack.push(statement);
        while let Some(node) = stack.pop() {
            let children = tree.children(node);
            match tree.kind(node) {
                NodeKind::RightName => {
                    if let Some(b) = top(node) {
                        if !reads.contains(&b) {
                            reads.push(b);
                        }
                    }
                }
                NodeKind::Assign if tree.kind(children[0]) == NodeKind::RightName => {
                    if let Some(b) = top(children[0]) {
                        if !writes.contains(&b) {
                            writes.push(b);
                            assigns.entry(b).or_default().push(i);
                        }
                    }
                    stack.push(children[1]);
                    continue;
                }
                _ => {}
            }
            // In reverse, so names are found in source order.
            stack.extend(children.iter().rev());
        }
        needs.push(reads);
        assigned.push(writes);
    }

    // What each statement goes after, in order.
    let after: Vec<Vec<usize>> = (0..statements.len())
        .map(|i| {
            let mut deps: Vec<usize> = Vec::new();
            let mut add = |list: Option<&Vec<usize>>| {
                for &d in list.into_iter().flatten() {
                    if !deps.contains(&d) {
                        deps.push(d);
                    }
                }
            };
            for b in &needs[i] {
                add(decls.get(b));
                add(assigns.get(b));
            }
            for b in &assigned[i] {
                add(decls.get(b));
            }
            deps
        })
        .collect();

    // Depth first, without recursion: a statement is marked when entered,
    // which breaks cycles, and placed once everything it goes after is.
    let mut entered = vec![false; statements.len()];
    let mut order = Vec::with_capacity(statements.len());
    let mut walk: Vec<(usize, usize)> = Vec::new();
    for start in 0..statements.len() {
        if entered[start] {
            continue;
        }
        entered[start] = true;
        walk.push((start, 0));
        while let Some(top) = walk.last_mut() {
            let i = top.0;
            if let Some(&d) = after[i].get(top.1) {
                top.1 += 1;
                if !entered[d] {
                    entered[d] = true;
                    walk.push((d, 0));
                }
            } else {
                walk.pop();
                order.push(statements[i]);
            }
        }
    }

    order
}
