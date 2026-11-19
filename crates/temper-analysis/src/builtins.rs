//! Builtins' declarations in core (`temper_frontend::corelib`): where
//! core declares a builtin type, function, or value, or a builtin type's
//! member, for go to definition, and its doc comment, for hover. Core
//! isn't one of the library's files, so its places are in core's own
//! text, by path from core's root.

use super::*;
use members::Named;
use temper_frontend::builtins::{Builtin, Constant};
use temper_frontend::corelib;
use temper_frontend::typing::{core_declaration, core_member_declarations};

/// A place in one of core's files.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct CoreLocation {
    /// Its file's path from core's root, as `core.temper`, one of
    /// [`corelib::FILES`].
    pub path: &'static str,
    /// Its file's text.
    pub source: &'static str,
    /// Byte offsets in its file.
    pub span: Span,
}

impl Analysis<'_> {
    /// Where core declares the builtin named at byte `offset` of `file`:
    /// a builtin type, function, or value, or a builtin type's member,
    /// through generic receivers and supertypes. None for anything else,
    /// and for builtins core doesn't declare.
    pub fn core_definition(&self, file: FileId, offset: u32) -> Option<CoreLocation> {
        let (_, target) = self.target(file, offset)?;
        let (ats, _) = self.core_target(target?)?;
        let at = *ats.first()?;
        // The name there: the member's, or an instance extension's
        // function's, as `listedMap` for `map`.
        let rest = &corelib::TEXT[at..];
        let len = rest
            .find(|c: char| !c.is_alphanumeric() && c != '_')
            .unwrap_or(rest.len());
        Some(CoreLocation {
            path: corelib::MAIN,
            source: corelib::TEXT,
            span: Span {
                start: at as u32,
                end: (at + len) as u32,
            },
        })
    }

    /// Where core declares what a target names, if it's a builtin, with
    /// its name: a member's declarations nearest first, from its type's
    /// own through its supertypes'; never empty.
    pub(crate) fn core_target(&self, target: Target) -> Option<(Vec<usize>, String)> {
        match target {
            Target::Symbol(symbol) => {
                let s = self.settled.scopes.symbol(symbol);
                if s.decl.is_some() || !matches!(s.kind, SymbolKind::Builtin(_)) {
                    return None;
                }
                let name = self.settled.library.names.get(s.name);
                let at = core_declaration(name)?;
                // An alias, as `Int = Int32`, then what it aliases, for
                // its doc comment.
                let mut ats = vec![at];
                ats.extend(core_alias(at).and_then(core_declaration));
                Some((ats, name.to_string()))
            }
            Target::Member { call, key } => {
                if let Some(Resolved::Decl(_)) = self.member(call) {
                    return None;
                }
                let name = self.settled.library.names.get(key);
                let owner = self.builtin_owner(call)?;
                let ats = core_member_declarations(owner, name);
                (!ats.is_empty()).then(|| (ats, name.to_string()))
            }
        }
    }

    /// The builtin type whose member a `.` call reads: its object's type,
    /// or the type its object names, for a static, or `Console` for the
    /// builtin `console`.
    fn builtin_owner(&self, call: NodeId) -> Option<&'static str> {
        let object = *self.settled.library.tree.children(call).get(1)?;
        if let Some(Named::Builtin(name)) = self.type_named(object) {
            return Some(name);
        }
        if self.settled.scopes.resolved_builtin(object) == Some(Builtin::Value(Constant::Console)) {
            return Some("Console");
        }
        match self.receiver_of(call)? {
            members::Receiver::Builtin { name, .. } => Some(name),
            members::Receiver::Class { .. } => None,
        }
    }
}

/// A builtin type as hover shows it: with its type parameters, as core
/// declares them, `Map<K, V>`; or what it aliases, `Int32` for `Int`.
pub(crate) fn builtin_type_shown(name: &str) -> String {
    let Some(at) = core_declaration(name) else {
        return name.to_string();
    };
    if let Some(aliased) = core_alias(at) {
        return aliased.to_string();
    }
    let rest = &corelib::TEXT[at + name.len()..];
    let Some(rest) = rest.strip_prefix('<') else {
        return name.to_string();
    };
    // Each parameter's name, past `@out` and before `extends …`.
    let (mut depth, mut params, mut param) = (1, Vec::new(), String::new());
    for c in rest.chars() {
        match c {
            '<' => depth += 1,
            '>' if depth == 1 => break,
            '>' => depth -= 1,
            ',' if depth == 1 => params.push(std::mem::take(&mut param)),
            '\n' => break,
            _ => param.push(c),
        }
    }
    params.push(param);
    let params: Vec<&str> = (params.iter())
        .filter_map(|p| {
            (p.split_whitespace())
                .find(|w| !w.starts_with('@'))
                .map(|w| w.trim_end_matches(|c: char| !c.is_alphanumeric() && c != '_'))
        })
        .collect();
    format!("{name}<{}>", params.join(", "))
}

/// What core's declaration at byte `at` of its text aliases, if it's an
/// alias, as `export let Int = Int32;`.
fn core_alias(at: usize) -> Option<&'static str> {
    let text = corelib::TEXT;
    let line_start = text.get(..at)?.rfind('\n').map_or(0, |i| i + 1);
    let line = text[line_start..].lines().next()?;
    let rest = line.strip_prefix("export let ")?;
    let (_, aliased) = rest.split_once(" = ")?;
    let aliased = aliased.strip_suffix(';')?;
    aliased
        .chars()
        .all(|c| c.is_alphanumeric() || c == '_')
        .then_some(aliased)
}

/// The doc comment, `/** … */`, right before a declaration whose name is
/// at byte `at` of core's text, past decorations' lines, as
/// [`doc_text`] gives it.
pub(crate) fn core_doc(at: usize) -> Option<String> {
    let text = corelib::TEXT;
    let line_start = text.get(..at)?.rfind('\n').map_or(0, |i| i + 1);
    if text[line_start..at].contains(|c| ";{}()=,".contains(c)) {
        return None;
    }
    let mut end = line_start;
    loop {
        let before = text[..end].trim_end();
        if before.ends_with("*/") {
            let start = before.rfind("/*")?;
            return doc_text(before.get(start..)?);
        }
        let line = before.rfind('\n').map_or(0, |i| i + 1);
        if !before[line..].trim_start().starts_with('@') || line == 0 {
            return None;
        }
        end = line;
    }
}
