//! What other libraries may do with builtin types, as core declares them
//! ([`super::core_shape`]; notes/core-capabilities.md): `new` makes only
//! a class with a public constructor, and only unsealed interfaces are
//! extended outside core. Each is checked on the type that `new`'s class
//! or a supertype evaluates to, so an alias, as `let I = Int32;`, is
//! checked as the type it names. A builtin type core doesn't declare
//! enables nothing: it can't be made or extended. Nor can a type
//! parameter, which may stand for any type within its bound.

use super::Settle;
use crate::core;
use crate::library::{Level, Message};
use crate::types::{TypeData, TypeId};
use temper_syntax::tree::NodeId;

impl Settle {
    /// The builtin type `t` is, maybe given type arguments, as `List`
    /// for `List<Int32>`, `Never` for `Never<Int32>`, or `Null` for null's
    /// type, by its name in
    /// [`crate::builtins::TYPES`], if it is one.
    fn builtin_of(&self, t: TypeId) -> Option<&'static str> {
        let types = &self.typing.types;
        match types.get(t) {
            TypeData::NeverOf(_) => Some("Never"),
            TypeData::Null => Some("Null"),
            _ => types.builtin_name(types.base(t)),
        }
    }

    /// Reports `new T(…)` for a type parameter `T`, or for a builtin
    /// type `T` that core declares as an interface, or as a class
    /// without a public constructor, unless it's core's own code, which
    /// makes its classes' values.
    pub(super) fn check_builtin_new(&mut self, call: NodeId) {
        let Some(&target) = self.lib.tree.children(call).get(1) else {
            return;
        };
        let t = self.eval_type(target);
        if let TypeData::Param(_) = self.typing.types.get(t) {
            let name = self.show(t);
            self.error(target, Message::CannotInstantiateTypeParam(name));
            return;
        }
        let Some(name) = self.builtin_of(t) else {
            return;
        };
        if self.lib.in_core(self.lib.tree.span(call).start) {
            return;
        }
        let message = match super::core_shape(name) {
            Some(shape) if shape.interface => {
                Message::CannotInstantiateAbstractType(shape.name.into())
            }
            Some(shape) if shape.public_constructor => return,
            Some(shape) => Message::NotConstructible(shape.name.into()),
            None => Message::NotConstructible(name.into()),
        };
        self.error(target, message);
    }

    /// Reports a class's or interface's supertype that's a type
    /// parameter, or a builtin type core declares as a class, which is
    /// final, or as a sealed interface, which only core extends.
    pub(super) fn check_builtin_supertypes(&mut self, class: NodeId) {
        let in_core = self.lib.in_core(self.lib.tree.span(class).start);
        let supers = core::class_parts(&self.lib.tree, class).1.to_vec();
        for sup in supers {
            let t = self.eval_type(sup);
            if let TypeData::Param(_) = self.typing.types.get(t) {
                let span = self.lib.tree.span(sup);
                let message = Message::CannotExtendTypeParam(self.show(t));
                self.lib.report(Level::Error, message, span);
                continue;
            }
            if in_core {
                continue;
            }
            let Some(name) = self.builtin_of(t) else {
                continue;
            };
            let message = match super::core_shape(name) {
                Some(shape) if !shape.interface => Message::CannotExtendConcrete(shape.name.into()),
                Some(shape) if shape.sealed => {
                    let sub = self.member_text(class).to_string();
                    Message::CannotExtendSealed(shape.name.into(), sub)
                }
                Some(_) => continue,
                None => Message::CannotExtendConcrete(name.into()),
            };
            let span = self.lib.tree.span(sup);
            self.lib.report(Level::Error, message, span);
        }
    }
}
