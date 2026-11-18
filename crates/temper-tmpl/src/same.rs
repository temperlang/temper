//! Whether two programs mean the same: what `==` on [`Program`] checks.
//!
//! Declarations keep their ids everywhere, so they compare by id; nodes,
//! types, and strings compare by content, since building the same program
//! in another order gives them other ids. Spans count only when either
//! program has them.

use crate::Program;
use crate::ids::{DeclId, NodeId, StrId, TypeId};
use crate::nodes::Flags;
use crate::schema::{Payload, Slot};
use crate::types::TypeArgs;

/// The first difference between two programs, if any, for tests.
pub fn difference(a: &Program, b: &Program) -> Option<String> {
    programs(a, b).err()
}

type Same = Result<(), String>;

fn check(same: bool, what: impl FnOnce() -> String) -> Same {
    if same { Ok(()) } else { Err(what()) }
}

fn programs(a: &Program, b: &Program) -> Same {
    check(a.library == b.library, || "libraries differ".into())?;
    check(a.profile == b.profile, || "profiles differ".into())?;
    check(a.has_spans == b.has_spans, || "only one has spans".into())?;
    // Line tables are derived from the sources, which text leaves out.
    let file = |f: &crate::SourceFile| (f.path.clone(), f.hash);
    check(
        !spans(a, b) || a.files.iter().map(file).eq(b.files.iter().map(file)),
        || "files differ".into(),
    )?;
    // Removed declarations at the end aren't in text, so they don't
    // count.
    let count = |p: &Program| {
        (0..p.decls.len())
            .rev()
            .find(|&i| p.decls.contains(DeclId(i as u32)))
            .map_or(0, |i| i + 1)
    };
    check(count(a) == count(b), || {
        format!("declaration counts differ: {} and {}", count(a), count(b))
    })?;
    for id in a.decls.ids().take(count(a)) {
        decl(a, b, id)?;
    }
    check(a.uses == b.uses, || "used libraries differ".into())?;
    check(a.modules.len() == b.modules.len(), || {
        "module counts differ".into()
    })?;
    for (m, n) in a.modules.iter().zip(&b.modules) {
        check(m.decl == n.decl && m.path == n.path, || {
            "modules differ".into()
        })?;
        check(m.items.len() == n.items.len(), || {
            format!("module #{}'s item counts differ", m.decl.0)
        })?;
        for (&x, &y) in m.items.iter().zip(&n.items) {
            node(a, b, x, y)?;
        }
        node(a, b, m.init, n.init)?;
        check(m.test_items.len() == n.test_items.len(), || {
            format!("module #{}'s test item counts differ", m.decl.0)
        })?;
        for (&x, &y) in m.test_items.iter().zip(&n.test_items) {
            node(a, b, x, y)?;
        }
        match (m.test_init, n.test_init) {
            (Some(x), Some(y)) => node(a, b, x, y)?,
            (None, None) => {}
            _ => return Err(format!("only one module #{} has a test init", m.decl.0)),
        }
        check(m.tests.len() == n.tests.len(), || {
            format!("module #{}'s test counts differ", m.decl.0)
        })?;
        for (&x, &y) in m.tests.iter().zip(&n.tests) {
            node(a, b, x, y)?;
        }
    }
    Ok(())
}

fn spans(a: &Program, b: &Program) -> bool {
    a.has_spans || b.has_spans
}

fn string(a: &Program, b: &Program, x: StrId, y: StrId) -> bool {
    match (x.is_none(), y.is_none()) {
        (true, true) => true,
        (false, false) => a.strings.get(x) == b.strings.get(y),
        _ => false,
    }
}

fn decl(a: &Program, b: &Program, id: DeclId) -> Same {
    let (x, y) = (a.decls.get(id), b.decls.get(id));
    let strs = |p: &Program, list: &[StrId]| -> Vec<String> {
        list.iter().map(|&s| p.strings.get(s).to_string()).collect()
    };
    let alts = |p: &Program| strs(p, p.decls.alts(id));
    let backends = |p: &Program| strs(p, p.decls.backends(id));
    let (xs, ys) = (a.decls.supers(id), b.decls.supers(id));
    let supers = xs.len() == ys.len()
        && xs
            .iter()
            .zip(ys)
            .all(|(&s, &t)| a.types.same(s, &b.types, t));
    let same = x.kind == y.kind
        && supers
        && a.decls.captures(id) == b.decls.captures(id)
        && string(a, b, x.name, y.name)
        && alts(a) == alts(b)
        && backends(a) == backends(b)
        && string(a, b, x.qname, y.qname)
        && x.owner == y.owner
        && a.types.same(x.ty, &b.types, y.ty)
        && x.facts == y.facts
        && string(a, b, x.doc, y.doc)
        && (!spans(a, b) || (x.span == y.span && x.name_span == y.name_span));
    check(same, || format!("declarations #{} differ", id.0))
}

/// Whether two calls' type arguments are the same types, whatever order
/// each program interned its lists in.
fn type_args(p: &Program, q: &Program, x: u32, y: u32) -> bool {
    let (xs, ys) = (p.types.args_of(TypeArgs(x)), q.types.args_of(TypeArgs(y)));
    xs.len() == ys.len()
        && xs
            .iter()
            .zip(ys)
            .all(|(&a, &b)| p.types.same(a, &q.types, b))
}

fn node(p: &Program, q: &Program, x: NodeId, y: NodeId) -> Same {
    let (a, b) = (&p.nodes, &q.nodes);
    if !a.contains(x) || !b.contains(y) {
        return check(a.contains(x) == b.contains(y), || {
            "a node is missing".into()
        });
    }
    let kind = a.kind(x);
    let spans = spans(p, q);
    let flags = |f: Flags| {
        if spans {
            f
        } else {
            Flags(f.0 & !Flags::DERIVED.0)
        }
    };
    let (sa, sb) = (a.slots(x), b.slots(y));
    let payload_same = kind.fields().iter().all(|field| match field.slot {
        Slot::A(Payload::Str) => string(p, q, StrId(sa.0), StrId(sb.0)),
        Slot::A(Payload::Type) => p.types.same(TypeId(sa.0), &q.types, TypeId(sb.0)),
        Slot::A(Payload::Args) => type_args(p, q, sa.0, sb.0),
        Slot::B(Payload::Args) => type_args(p, q, sa.1, sb.1),
        Slot::A(_) => sa.0 == sb.0,
        Slot::B(Payload::Str) => string(p, q, StrId(sa.1), StrId(sb.1)),
        Slot::B(_) => sa.1 == sb.1,
        Slot::AB(_) => sa == sb,
        Slot::Flag | Slot::Kid(..) => true,
    });
    let same = kind == b.kind(y)
        && p.types.same(a.ty(x), &q.types, b.ty(y))
        && flags(a.flags(x)) == flags(b.flags(y))
        && (!spans || a.span(x) == b.span(y))
        && payload_same
        && a.kids(x).len() == b.kids(y).len();
    check(same, || {
        format!("{} nodes differ ({} and {})", kind.name(), x.0, y.0)
    })?;
    for (&k, &l) in a.kids(x).iter().zip(b.kids(y)) {
        node(p, q, k, l)?;
    }
    Ok(())
}
