//! Object literals' classes from the type wanted (notes/typing.md,
//! "Object literals and target typing"; notes/language.md, "Object
//! literals, not named arguments"). An object literal without `class: C`, `{ a: 1, b }`,
//! keeps its `\a` markers through expansion, while its values settle, and
//! is a work item of typing's round that waits for the type its place
//! wants ([`Settle::wanted_type`]), or for knowing there's none, and then
//! decides its class, by that type and by the names given together, and
//! becomes a constructor call with its arguments placed
//! ([`Settle::place_literal`]), publishing [`Fact::ObjectLiteral`]. Its
//! type, and the types its values' places want, wait for that fact.

use crate::classes::Placed;
use crate::core::{CLASS_INTERFACE, class_parts, let_parts};
use crate::library::Message;
use crate::scope::{ScopeId, ScopeKind, SymbolId};
use crate::settle::{Fact, Settle};
use crate::types::{TypeData, TypeId};
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind};

impl Settle {
    /// Queues deciding each object literal's class, as typing starts.
    pub(super) fn queue_object_literals(&mut self) {
        let literals: Vec<(NodeId, ScopeId)> =
            self.object_literals.iter().map(|(&c, &s)| (c, s)).collect();
        for (call, scope) in literals {
            self.queue_object_literal(call, scope);
        }
    }

    /// Whether an object literal's class is still to be decided, so its
    /// type, and what its values' places want, wait for that.
    pub(super) fn literal_undecided(&self, call: NodeId) -> bool {
        self.object_literals.contains_key(&call)
    }

    /// Decides an object literal's class, once the type its place wants
    /// is known ([`Settle::literal_class`]), and lowers it to a
    /// constructor call, or an error.
    pub(crate) fn decide_object_literal(
        &mut self,
        call: NodeId,
        scope: ScopeId,
        forced: bool,
    ) -> Result<(), Fact> {
        if !self.literal_undecided(call) {
            return Ok(());
        }
        let wanted = match self.wanted_type(call, forced) {
            Ok(wanted) => wanted,
            Err(fact) if !forced => return Err(fact),
            Err(_) => None,
        };
        self.object_literals.remove(&call);
        // A declared type that's an error, already reported, gives no
        // class, rather than one guessed from the names: the literal
        // fails as the type did.
        if let Some(failed) = self.failed_declared_type(call) {
            let index = self.lib.tree.data(failed);
            self.rewrite(call, NodeKind::Error, index, &[]);
            self.publish(Fact::ObjectLiteral(call));
            return Ok(());
        }
        let names: Vec<NameId> = (self.literal_props(call).unwrap_or_default().iter())
            .map(|&(name, _)| name)
            .collect();
        // Scopes are as complete as they'll be, now that expansion has
        // settled.
        let found = self.matching_classes(&names, scope, true)?;
        match self.literal_class(wanted, &names, &found) {
            Ok((symbol, class)) => {
                let target = self.lib.tree.children(call)[1];
                let span = self.lib.tree.span(target);
                let class_name = self.builtin_name(symbol, span, target);
                if let Placed::Block(temporaries) = self.place_literal(call, class_name, class) {
                    self.declare_temporaries(call, scope, &temporaries);
                }
            }
            Err(message) => self.error(call, message),
        }
        self.note_parents(call);
        self.publish(Fact::ObjectLiteral(call));
        Ok(())
    }

    /// Decides the object literals left undecided, as when the step budget
    /// runs out before typing, so none keeps its `\a` markers.
    pub(crate) fn decide_object_literals_left(&mut self) {
        while let Some((&call, &scope)) = self.object_literals.iter().next() {
            let _ = self.decide_object_literal(call, scope, true);
        }
    }

    /// An object literal's class, with a symbol naming it, from the type
    /// its place wants, `wanted`, and the classes visible there whose
    /// constructors take every name, `found`:
    ///
    /// - Where a class `C` is wanted, maybe nullable or given type
    ///   arguments, `C`, if its constructor takes every name; if not, and
    ///   one other class's does, that's an error rather than a silent
    ///   pick: "Point is wanted here, but these properties fit Line's
    ///   constructor"; else `C` anyway, so placing the names says which
    ///   it lacks: "Point has no property `z`; it has `x` and `y`".
    /// - Where an interface `I` is wanted, the one class found that
    ///   implements it, or as for a class wanted, the error if exactly one
    ///   other was found.
    /// - Otherwise, as where a builtin type, a type parameter, or nothing
    ///   is wanted, the one class found, as Kotlin Temper finds it.
    ///
    /// Else Kotlin's errors: "No signature matches" where none fits, and
    /// "Multiple types have matching constructors: A, B" where several do.
    fn literal_class(
        &mut self,
        wanted: Option<TypeId>,
        names: &[NameId],
        found: &[(SymbolId, NodeId)],
    ) -> Result<(SymbolId, NodeId), Message> {
        let Some((wanted, decl)) = wanted.and_then(|t| self.wanted_decl(t)) else {
            return match *found {
                [one] => Ok(one),
                _ => Err(self.matching_error(found)),
            };
        };
        let is_interface = self.lib.tree.data(decl) & CLASS_INTERFACE != 0;
        // The nearest symbol for it, else its own, if it's not visible
        // here by name.
        let symbol = (found.iter().find(|&&(_, c)| c == decl).map(|&(b, _)| b))
            .or_else(|| self.scopes.resolved(class_parts(&self.lib.tree, decl).0));
        if !is_interface && self.takes_names(decl, names) {
            if let Some(symbol) = symbol {
                return Ok((symbol, decl));
            }
        }
        let fits: Vec<(SymbolId, NodeId)> = match is_interface {
            true => (found.iter().copied())
                .filter(|&(_, c)| self.ancestors(c).contains(&decl))
                .collect(),
            false => Vec::new(),
        };
        match (&fits[..], found) {
            ([one], _) => Ok(*one),
            ([], &[(other, _)]) => {
                let other = self
                    .lib
                    .names
                    .get(self.scopes.symbol(other).name)
                    .to_string();
                Err(Message::ObjectLiteralWantsOther(self.show(wanted), other))
            }
            // The class wanted, though it doesn't take the names, so
            // placing them says which it lacks ([`Settle::place_literal`]).
            ([], _) if !is_interface => symbol
                .map(|s| (s, decl))
                .ok_or_else(|| self.matching_error(found)),
            ([], _) => Err(self.matching_error(found)),
            (_, _) => Err(self.matching_error(&fits)),
        }
    }

    /// The declared type of the `let` an object literal initializes, if
    /// it's an error.
    fn failed_declared_type(&self, call: NodeId) -> Option<NodeId> {
        let tree = &self.lib.tree;
        let parent = self
            .typing
            .parent(call)
            .filter(|&p| tree.kind(p) == NodeKind::Let)?;
        match let_parts(tree, parent) {
            (_, Some(ty), Some(init)) if init == call && tree.kind(ty) == NodeKind::Error => {
                Some(ty)
            }
            _ => None,
        }
    }

    /// The class or interface a wanted type names, a user's, maybe
    /// nullable or given type arguments, with that type, not nullable.
    fn wanted_decl(&self, wanted: TypeId) -> Option<(TypeId, NodeId)> {
        let wanted = self.non_null(wanted);
        let types = &self.typing.types;
        match *types.get(types.base(wanted)) {
            TypeData::Class(symbol) => Some((wanted, self.type_decl(symbol)?)),
            _ => None,
        }
    }

    /// Declares the temporaries that hold an object literal's values,
    /// now a block, in a scope of the block's own, as reading the block
    /// would have, and queues inferring their types. A value's place wants
    /// what its temporary's use does.
    fn declare_temporaries(
        &mut self,
        block: NodeId,
        scope: ScopeId,
        temporaries: &[(NodeId, NodeId)],
    ) {
        let block_scope = self.scopes.add(scope, ScopeKind::Block, block, true);
        for &(decl, used) in temporaries {
            self.declare(decl, block_scope);
            if let Some(b) = self.scopes.resolved(self.lib.tree.children(decl)[0]) {
                self.scopes.resolve(used, b);
            }
            self.typing.literal_temporaries.insert(decl, used);
            self.queue_type(decl);
        }
    }

    /// Notes the parents of a node's descendants, once typing has begun,
    /// for the types their places want.
    fn note_parents(&mut self, node: NodeId) {
        if !self.typing.has_parents() {
            return;
        }
        let mut stack = vec![node];
        while let Some(node) = stack.pop() {
            for &child in self.lib.tree.children(node) {
                self.typing.set_parent(child, node);
                stack.push(child);
            }
        }
    }
}
