//! Node storage: one column per field, indexed by [`NodeId`].
//!
//! Each node has a kind, a type, a span, a range of children, two payload
//! slots (`a` and `b`, whose use the [schema](crate::schema) gives per
//! kind), and flags. [`Nodes::get`] decodes a node into a [`Node`].

use crate::ids::{NodeId, Span, TypeId};
use crate::schema::{Arity, Kind, Slot};

/// Per-node flags.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash)]
pub struct Flags(pub u8);

impl Flags {
    pub const NONE: Flags = Flags(0);
    /// A call that may fail, written `^` in text.
    pub const MAY_BUBBLE: Flags = Flags(1);
    /// The span points at what caused the node, not at its own source.
    pub const DERIVED: Flags = Flags(2);

    pub fn contains(self, other: Flags) -> bool {
        self.0 & other.0 == other.0
    }
}

impl std::ops::BitOr for Flags {
    type Output = Flags;
    fn bitor(self, other: Flags) -> Flags {
        Flags(self.0 | other.0)
    }
}

/// A run of children, as a node's field holds them; [`Nodes::list`]
/// gives their ids.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash)]
pub struct Kids {
    start: u32,
    len: u32,
}

impl Kids {
    pub fn len(self) -> usize {
        self.len as usize
    }

    pub fn is_empty(self) -> bool {
        self.len == 0
    }
}

/// Every node of a program, in columns.
#[derive(Clone, Debug, Default)]
pub struct Nodes {
    kind: Vec<Kind>,
    ty: Vec<TypeId>,
    span: Vec<Span>,
    kids: Vec<Kids>,
    a: Vec<u32>,
    b: Vec<u32>,
    flags: Vec<Flags>,
    kid_list: Vec<NodeId>,
}

impl Nodes {
    pub fn len(&self) -> usize {
        self.kind.len()
    }

    pub fn is_empty(&self) -> bool {
        self.kind.is_empty()
    }

    pub fn contains(&self, id: NodeId) -> bool {
        id.index() < self.len()
    }

    pub fn kind(&self, id: NodeId) -> Kind {
        self.kind[id.index()]
    }

    /// The node's type, or [`TypeId::NONE`] for a statement or
    /// declaration.
    pub fn ty(&self, id: NodeId) -> TypeId {
        self.ty[id.index()]
    }

    pub(crate) fn set_ty(&mut self, id: NodeId, ty: TypeId) {
        self.ty[id.index()] = ty;
    }

    pub fn span(&self, id: NodeId) -> Span {
        self.span[id.index()]
    }

    pub fn flags(&self, id: NodeId) -> Flags {
        self.flags[id.index()]
    }

    /// All of the node's children, in evaluation order.
    pub fn kids(&self, id: NodeId) -> &[NodeId] {
        self.list(self.kids[id.index()])
    }

    pub fn list(&self, kids: Kids) -> &[NodeId] {
        &self.kid_list[kids.start as usize..(kids.start + kids.len) as usize]
    }

    /// The raw payload slots, for code that walks the schema generically.
    pub fn slots(&self, id: NodeId) -> (u32, u32) {
        (self.a[id.index()], self.b[id.index()])
    }

    pub(crate) fn push(
        &mut self,
        kind: Kind,
        ty: TypeId,
        (a, b): (u32, u32),
        flags: Flags,
        kids: &[NodeId],
    ) -> NodeId {
        let id = NodeId(self.kind.len() as u32);
        self.kind.push(kind);
        self.ty.push(ty);
        self.span.push(Span::UNKNOWN);
        self.kids.push(Kids {
            start: self.kid_list.len() as u32,
            len: kids.len() as u32,
        });
        self.a.push(a);
        self.b.push(b);
        self.flags.push(flags);
        self.kid_list.extend_from_slice(kids);
        id
    }

    /// Makes `id` another node, keeping its span: for passes that rewrite
    /// a node where its parent points at it.
    pub(crate) fn replace(
        &mut self,
        id: NodeId,
        kind: Kind,
        ty: TypeId,
        (a, b): (u32, u32),
        flags: Flags,
        kids: &[NodeId],
    ) {
        let i = id.index();
        self.kind[i] = kind;
        self.ty[i] = ty;
        self.kids[i] = Kids {
            start: self.kid_list.len() as u32,
            len: kids.len() as u32,
        };
        self.a[i] = a;
        self.b[i] = b;
        self.flags[i] = flags;
        self.kid_list.extend_from_slice(kids);
    }

    /// The nodes under `roots`, copied children first, as rule S1 wants
    /// after [`Nodes::replace`]; `roots` become their copies' ids.
    pub(crate) fn renumbered(&self, roots: &mut [&mut NodeId]) -> Nodes {
        let mut out = Nodes::default();
        let mut copied = vec![NodeId::NONE; self.len()];
        for root in roots.iter_mut() {
            **root = self.copy_into(**root, &mut out, &mut copied);
        }
        out
    }

    fn copy_into(&self, id: NodeId, out: &mut Nodes, copied: &mut [NodeId]) -> NodeId {
        if id.is_none() {
            return id;
        }
        if !copied[id.index()].is_none() {
            return copied[id.index()];
        }
        let kids: Vec<NodeId> = (self.kids(id).iter())
            .map(|&k| self.copy_into(k, out, copied))
            .collect();
        let i = id.index();
        let new = out.push(
            self.kind[i],
            self.ty[i],
            (self.a[i], self.b[i]),
            self.flags[i],
            &kids,
        );
        out.set_span(new, self.span[i]);
        copied[i] = new;
        new
    }

    pub(crate) fn set_span(&mut self, id: NodeId, span: Span) {
        self.span[id.index()] = span;
    }

    pub(crate) fn add_flags(&mut self, id: NodeId, flags: Flags) {
        self.flags[id.index()] = self.flags[id.index()] | flags;
    }

    /// Splits a node's children among its kind's child fields: one each
    /// for `one` fields, and the rest for its `opt` or `many` field. `None`
    /// if the count doesn't fit the kind (the validator's S1).
    pub(crate) fn parts(&self, id: NodeId) -> Option<Parts> {
        let all = self.kids[id.index()];
        split(self.kind(id), all)
    }
}

/// A node's children split by field, in field order.
pub(crate) struct Parts {
    parts: [Kids; 4],
    count: usize,
    next: usize,
}

impl Iterator for Parts {
    type Item = Kids;
    fn next(&mut self) -> Option<Kids> {
        (self.next < self.count).then(|| {
            self.next += 1;
            self.parts[self.next - 1]
        })
    }
}

fn split(kind: Kind, all: Kids) -> Option<Parts> {
    let arities = kind.fields().iter().filter_map(|f| match f.slot {
        Slot::Kid(arity, _) => Some(arity),
        _ => None,
    });
    let ones = arities.clone().filter(|a| *a == Arity::One).count() as u32;
    let rest = all.len.checked_sub(ones)?;
    let mut parts = Parts {
        parts: [Kids::default(); 4],
        count: 0,
        next: 0,
    };
    let mut start = all.start;
    let mut rest_used = false;
    for arity in arities {
        let len = match arity {
            Arity::One => 1,
            Arity::Opt if rest > 1 => return None,
            Arity::Opt | Arity::Many => {
                rest_used = true;
                rest
            }
        };
        parts.parts[parts.count] = Kids { start, len };
        parts.count += 1;
        start += len;
    }
    (rest_used || rest == 0).then_some(parts)
}
