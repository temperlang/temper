//! Object literals' classes from the type wanted (notes/typing.md,
//! "Object literals and target typing"; notes/language.md, "Object
//! literals, not named arguments"). An object literal without `class: C`, `{ a: 1, b }`,
//! keeps its `\a` markers through expansion, while its values settle, and
//! is a work item of typing's round that waits for the type its place
//! wants ([`Settle::wanted_type`]), or for knowing there's none, and then
//! decides its class, by that type and by the names given together, and
//! becomes a constructor call with its arguments placed
//! ([`Settle::place_literal`]), publishing [`Fact::ObjectLiteral`]. Its
//! type, and the types its values' places want, wait for that fact. A
//! spread, `{ ...old, b }`, waits instead for `old`'s type, which gives
//! its class, and becomes the literal that gives every property
//! ([`Settle::place_spread`]; notes/typing.md, "Object spread").

use crate::builtins::{Builtin, Function};
use crate::classes::Placed;
use crate::core::{
    CLASS_INTERFACE, MEMBER_PRIVATE, MEMBER_STATIC, ROLE_GETTER, ROLE_MASK, ROLE_PARAM,
    class_parts, constructor_params,
};
use crate::library::Message;
use crate::scope::{ScopeId, ScopeKind, SymbolId};
use crate::settle::{Fact, Settle};
use crate::types::{TypeData, TypeId, UNKNOWN};
use temper_syntax::ast::Value;
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
        let key = self.lib.tree.children(call).get(2);
        if key
            .and_then(|&k| self.lib.symbol(k))
            .is_some_and(|n| self.is_spread(n))
        {
            return self.decide_spread(call, scope, forced);
        }
        let wanted = match self.wanted_type(call, forced) {
            Ok(wanted) => wanted,
            Err(fact) if !forced => return Err(fact),
            Err(_) => None,
        };
        self.object_literals.remove(&call);
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
                    let block_scope = self.scopes.add(scope, ScopeKind::Block, call, true);
                    self.declare_temporaries(block_scope, &temporaries);
                }
            }
            Err(message) => self.error(call, message),
        }
        self.note_parents(call);
        self.publish(Fact::ObjectLiteral(call));
        Ok(())
    }

    /// Lowers a spread, `{ ...old, b: e }`, once `old`'s type is known
    /// ([`Settle::place_spread`]), or reports why it can't.
    fn decide_spread(&mut self, call: NodeId, scope: ScopeId, forced: bool) -> Result<(), Fact> {
        let old = self.lib.tree.children(call)[3];
        let t = match self.expr_type(old, forced) {
            Ok(t) => t,
            Err(fact) if !forced => return Err(fact),
            Err(_) => UNKNOWN,
        };
        self.object_literals.remove(&call);
        if let Err(message) = self.place_spread(call, scope, t) {
            self.error(call, message);
        }
        self.note_parents(call);
        self.publish(Fact::ObjectLiteral(call));
        Ok(())
    }

    /// Makes a spread, `{ ...old, b: e }`, whose value `old` has type `t`,
    /// the object literal that gives every property, those not given read
    /// from `old`, held in a temporary that runs first: `do { let spread#1
    /// = old; new C(spread#1.a, e) }`. So the class's constructor runs, as
    /// for any object literal ([`Settle::place_literal`]), and a parameter
    /// with a default takes `old`'s value. The class is `old`'s, which must
    /// be a class; `class: C`, if given, must name it; and each property
    /// copied must be readable here. Copied properties go among the given
    /// ones in the parameters' order, so placing them needs no more
    /// temporaries than the given ones do.
    fn place_spread(&mut self, call: NodeId, scope: ScopeId, t: TypeId) -> Result<(), Message> {
        let children = self.lib.tree.children(call).to_vec();
        let &[callee, target, _, old, ref given @ ..] = &children[..] else {
            return Err(Message::MalformedSpecial("new".into()));
        };
        let symbol = match *self.typing.types.get(self.typing.types.base(t)) {
            TypeData::Class(symbol) => Some(symbol),
            _ => None,
        };
        let Some((symbol, class)) = symbol.and_then(|b| Some((b, self.class_of(b)?))) else {
            return Err(Message::SpreadNeedsClass(self.show(t)));
        };
        let span = self.lib.tree.span(call);
        let class_name = if self.lib.value(target) == Some(&Value::Void) {
            self.builtin_name(symbol, span, target)
        } else if self.scopes.resolved(target).and_then(|b| self.class_of(b)) == Some(class) {
            target
        } else {
            let named = self.lib.source_text(self.lib.tree.span(target)).to_string();
            return Err(Message::SpreadOtherClass(
                self.member_text(class).into(),
                named,
            ));
        };
        let params = constructor_params(&self.lib.tree, class);
        let param_names: Vec<Option<NameId>> = (params.iter())
            .map(|&p| self.lib.name(self.lib.tree.children(p)[0]))
            .collect();
        let given_names: Vec<Option<NameId>> = given
            .chunks(2)
            .map(|pair| self.lib.symbol(pair[0]))
            .collect();
        let mut copied = Vec::new();
        for (i, &name) in param_names.iter().enumerate() {
            let Some(name) = name.filter(|n| !given_names.contains(&Some(*n))) else {
                continue;
            };
            if !self.readable_here(class, name, scope) {
                let text = self.lib.names.get(name).to_string();
                return Err(Message::SpreadCannotCopy(
                    text,
                    self.member_text(class).into(),
                ));
            }
            copied.push((i, name));
        }
        let held = self.lib.names.temporary("spread");
        let old_span = self.lib.tree.span(old);
        let left = self.lib.add_name(NodeKind::LeftName, held, old_span, old);
        let decl = self.make_let(left, None, Some(old), 0, old_span, old);
        let dot = self.scopes.hidden(Builtin::Function(Function::Dot));
        let at = |name: Option<NameId>| param_names.iter().position(|&n| n == name);
        let mut given = given.chunks(2).peekable();
        let mut args = vec![callee, class_name];
        let mut uses = Vec::new();
        for (i, name) in copied {
            while let Some(pair) =
                given.next_if(|pair| at(self.lib.symbol(pair[0])).is_some_and(|g| g < i))
            {
                args.extend_from_slice(pair);
            }
            let used = self.lib.add_name(NodeKind::RightName, held, old_span, old);
            uses.push(used);
            let key = self.lib.add_value(Value::Symbol(name), span, call);
            let dot = self.builtin_name(dot, span, call);
            let read = (self.lib).add(NodeKind::Call, span, 0, &[dot, used, key], call);
            args.extend([key, read]);
        }
        args.extend(given.flatten());
        self.set_children(call, &args);
        let temporaries = match self.place_literal(call, class_name, class) {
            // Reported already.
            Placed::Error => return Ok(()),
            Placed::InPlace => {
                let made = self.lib.tree.children(call).to_vec();
                let made = self.lib.add(NodeKind::Call, span, 0, &made, call);
                self.rewrite(call, NodeKind::Block, 0, &[made]);
                Vec::new()
            }
            Placed::Block(temporaries) => temporaries,
        };
        let mut statements = vec![decl];
        statements.extend_from_slice(self.lib.tree.children(call));
        self.set_children(call, &statements);
        let block_scope = self.scopes.add(scope, ScopeKind::Block, call, true);
        self.declare_temporary(block_scope, decl, &uses);
        self.declare_temporaries(block_scope, &temporaries);
        Ok(())
    }

    /// Whether code in `scope` may read a class's instance property
    /// `name`, a field or getter: if it's private, only in the class that
    /// declares it.
    fn readable_here(&self, class: NodeId, name: NameId, scope: ScopeId) -> bool {
        let tree = &self.lib.tree;
        let inside = |owner: NodeId| {
            let mut at = Some(scope);
            while let Some(id) = at {
                let s = self.scopes.get(id);
                if s.kind == ScopeKind::Class && s.node == owner {
                    return true;
                }
                at = s.parent;
            }
            false
        };
        (self.members_named(class, name, false).into_iter()).any(|m| {
            let data = tree.data(m);
            matches!(data & ROLE_MASK, 0 | ROLE_PARAM | ROLE_GETTER)
                && data & MEMBER_STATIC == 0
                && (data & MEMBER_PRIVATE == 0 || self.member_owner(m).is_some_and(inside))
        })
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
    ///   constructor".
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
        if !is_interface && self.takes_names(decl, names) {
            // The nearest symbol for it, else its own, if it's not visible
            // here by name.
            let symbol = (found.iter().find(|&&(_, c)| c == decl).map(|&(b, _)| b))
                .or_else(|| self.scopes.resolved(class_parts(&self.lib.tree, decl).0));
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
            ([], _) => Err(self.matching_error(found)),
            (_, _) => Err(self.matching_error(&fits)),
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
    /// now a block, in the block's own scope, as reading the block would
    /// have. A value's place wants what its temporary's use does.
    fn declare_temporaries(&mut self, block_scope: ScopeId, temporaries: &[(NodeId, NodeId)]) {
        for &(decl, used) in temporaries {
            self.declare_temporary(block_scope, decl, &[used]);
            self.typing.literal_temporaries.insert(decl, used);
        }
    }

    /// Declares a temporary in a block's scope, with its uses, and queues
    /// inferring its type.
    fn declare_temporary(&mut self, block_scope: ScopeId, decl: NodeId, uses: &[NodeId]) {
        self.declare(decl, block_scope);
        if let Some(b) = self.scopes.resolved(self.lib.tree.children(decl)[0]) {
            for &used in uses {
                self.scopes.resolve(used, b);
            }
        }
        self.queue_type(decl);
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
