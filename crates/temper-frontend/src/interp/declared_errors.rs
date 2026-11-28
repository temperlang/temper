//! Declared errors (RFC 164; notes/typing.md, "Declared errors"):
//! the root's members, `message` and `cause`, for an error type that
//! doesn't declare its own, and what a report of an error says.
//!
//! The user-visible choices are here, each in one place, as they may
//! change: an error's default message ([`default_message`]), and how a
//! report says an error and its causes ([`Interp::failure_report`]).
//! Throwing, handling, and causes are in [`super::frame`] and
//! [`super::lower`].

use super::{
    Abrupt, Cause, Class, Interp, Object, Val, describe, thrown_cell, to_string, type_name,
};
use crate::builtins::{Builtin, ERROR_ROOT, TYPES};
use crate::core::{self, MEMBER_NO_PROPERTY, MEMBER_PUBLIC, MEMBER_STATIC, ROLE_MASK, ROLE_PARAM};
use crate::settle::Settled;
use std::rc::Rc;
use temper_syntax::names::NameId;

impl Interp<'_> {
    /// How a run that ended abruptly failed, for people: a declared
    /// error as [`Interp::failure_report`] says it, after "Failed with".
    pub(super) fn describe(&mut self, s: &Settled, abrupt: Abrupt) -> String {
        match abrupt {
            Abrupt::Throw(failure) => format!("Failed with {}", self.failure_report(s, &failure)),
            abrupt => describe(abrupt),
        }
    }

    /// An error, then each of its causes on a line of its own: its type,
    /// its message, and where it was first thrown, as `NotFound: not found
    /// (key: y), at lib/a.temper:3:5`, then `caused by …`.
    pub(super) fn failure_report(&mut self, s: &Settled, failure: &Val) -> String {
        let mut out = String::new();
        let mut next = Some(failure.clone());
        while let Some(failure) = next.take() {
            if !out.is_empty() {
                out.push_str("\ncaused by ");
            }
            let (name, message) = match &failure {
                Val::Object(object) => {
                    (class_name(s, &object.class), self.error_message(s, object))
                }
                Val::CoreDeclaredError(core) => (core.type_name.to_string(), core.message()),
                _ => break,
            };
            out.push_str(&format!("{name}: {message}"));
            if let Some(thrown) = thrown_cell(&failure).and_then(|t| t.get()) {
                let (path, line, column) = s.library.position(thrown.at);
                out.push_str(&format!(", at {path}:{line}:{column}"));
                if let Some(Cause(cause)) = &thrown.cause {
                    next = Some(cause.clone());
                }
            }
        }
        out
    }

    /// An error's message: its type's own `message` getter's, or if it
    /// has none, or that fails, the default ([`default_message`]).
    fn error_message(&mut self, s: &Settled, failure: &Rc<Object>) -> String {
        if let Some(name) = s.library.names.find("message") {
            if let Ok(Val::Str(ref message)) =
                self.get_property(s, Val::Object(failure.clone()), name, None)
            {
                return message.to_string();
            }
        }
        default_message(s, failure)
    }

    /// The root's member `name` of an object of an error type that
    /// doesn't declare it: `message`, its default ([`default_message`]),
    /// or `cause`, the error that caused it, or null.
    pub(super) fn root_member(
        &mut self,
        s: &Settled,
        object: &Val,
        name: NameId,
    ) -> Result<Option<Val>, Abrupt> {
        let Val::Object(failure) = object else {
            return Ok(None);
        };
        let member = s.library.names.get(name);
        if !matches!(member, "message" | "cause") || !self.is_error(s, &failure.class)? {
            return Ok(None);
        }
        Ok(Some(match member {
            "message" => Val::Str(default_message(s, failure).into()),
            _ => match failure.thrown.get().and_then(|t| t.cause.as_ref()) {
                Some(Cause(cause)) => cause.clone(),
                _ => Val::Null,
            },
        }))
    }

    /// Whether a class is an error type: it extends the root, or a
    /// class or interface that does.
    fn is_error(&mut self, s: &Settled, class: &Rc<Class>) -> Result<bool, Abrupt> {
        let tree = &s.library.tree;
        let is_root = |&t: &temper_syntax::tree::NodeId| matches!(s.scopes.resolved_builtin(t), Some(Builtin::Type(i)) if TYPES[i as usize] == ERROR_ROOT);
        if core::class_parts(tree, class.node).1.iter().any(is_root) {
            return Ok(true);
        }
        for sup in self.supers(s, class)?.iter() {
            if self.is_error(s, sup)? {
                return Ok(true);
            }
        }
        Ok(false)
    }
}

/// An error's message where its type declares none: the type's name in
/// lowercase words, then, if it has any, its public fields in order, each
/// as its name and its value, as `not found (key: y)` or `range (low: 1,
/// high: 2.5)`. A string, number, Boolean, or null shows as its
/// `toString()`, anything else as its type's name.
fn default_message(s: &Settled, failure: &Object) -> String {
    let tree = &s.library.tree;
    let mut out = words(&class_name(s, &failure.class));
    let Some(scope) = s.scopes.of_node(failure.class.node) else {
        return out;
    };
    let own = 1 + core::class_parts(tree, failure.class.node).2.len();
    let fields = failure.fields.borrow();
    let mut shown = Vec::new();
    for &(name, symbol) in s.scopes.get(scope).names.iter().take(own) {
        let symbol = s.scopes.symbol(symbol);
        let Some(decl) = symbol.decl else {
            continue;
        };
        let data = tree.data(decl);
        let role = data & ROLE_MASK;
        let public = data & MEMBER_PUBLIC != 0
            && data & (MEMBER_STATIC | MEMBER_NO_PROPERTY) == 0
            && (role == 0 || role == ROLE_PARAM);
        if !public || symbol.input || tree.kind(decl) != temper_syntax::tree::NodeKind::Let {
            continue;
        }
        let value = match fields.get(symbol.slot as usize) {
            Some(value @ (Val::Str(_) | Val::Int(_) | Val::Int64(_) | Val::Float(_))) => {
                to_string(value)
            }
            Some(value @ (Val::Bool(_) | Val::Null)) => to_string(value),
            Some(Val::Object(object)) => class_name(s, &object.class),
            Some(value) => type_name(value).to_string(),
            None => continue,
        };
        shown.push(format!("{}: {value}", s.library.names.get(name)));
    }
    if !shown.is_empty() {
        out.push_str(&format!(" ({})", shown.join(", ")));
    }
    out
}

/// A type's name in lowercase words: `NotFound` is `not found`,
/// `HTTPFailure` is `http failure`, `Utf8Error` is `utf8 error`.
pub(crate) fn words(name: &str) -> String {
    let chars: Vec<char> = name.chars().collect();
    let mut out = String::new();
    for (i, &c) in chars.iter().enumerate() {
        if c == '_' {
            if !out.is_empty() && !out.ends_with(' ') {
                out.push(' ');
            }
            continue;
        }
        if c.is_uppercase() && i > 0 {
            let before = chars[i - 1];
            let lower_next = chars.get(i + 1).is_some_and(|n| n.is_lowercase());
            let starts = before.is_lowercase()
                || before.is_ascii_digit()
                || (before.is_uppercase() && lower_next);
            if starts && !out.ends_with(' ') {
                out.push(' ');
            }
        }
        out.extend(c.to_lowercase());
    }
    out.trim_end().to_string()
}

/// A class's name, as declared.
fn class_name(s: &Settled, class: &Class) -> String {
    super::class_name(s, class.node).to_string()
}

#[cfg(test)]
mod tests {
    #[test]
    fn words_split_names() {
        for (name, said) in [
            ("NotFound", "not found"),
            ("HTTPFailure", "http failure"),
            ("Utf8Error", "utf8 error"),
            ("SqlProblem", "sql problem"),
            ("Bad_Input", "bad input"),
            ("E", "e"),
        ] {
            assert_eq!(super::words(name), said);
        }
    }
}
