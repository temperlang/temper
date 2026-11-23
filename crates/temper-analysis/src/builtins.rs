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
use temper_syntax::lex::TokenKind;

/// A place in one of core's files.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct CoreLocation {
    /// Its file's path from core's root, as `strings.temper.md`, one of
    /// [`corelib::SOURCES`].
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
        let at = match self.syntax_at(file, offset) {
            Some(syntax) => syntax.at,
            None => {
                let (_, target) = self.target(file, offset)?;
                *self.core_target(target?)?.0.first()?
            }
        };
        // The name there: the member's, or an instance extension's
        // function's, as `listedMap` for `map`, or a quoted one,
        // ``nym`true` ``.
        let len = core_name_len(at);
        let (path, source, at) = corelib::source_at(at)?;
        Some(CoreLocation {
            path,
            source,
            span: Span {
                start: at as u32,
                end: (at + len) as u32,
            },
        })
    }

    /// The builtin word at byte `offset` of `file`, if core documents
    /// it: a keyword that names a builtin, as `if`, `orelse` or `await`,
    /// where the tree has no name of its own for it. A macro's word is
    /// its own even where its expansion's names cover it, as `for`'s
    /// `forEach`, unless it names a declaration, as a user's `let test`;
    /// any other builtin's only where no name covers it. Core declares no
    /// operators, so they're left to the tree's names.
    pub(crate) fn syntax_at(&self, file: FileId, offset: u32) -> Option<Syntax> {
        let parsed = &self.settled.library.files.get(file.0 as usize)?.parsed;
        // Tokens are in source order, and the source's don't overlap, so
        // the only one that may hold `offset` is the last to start by it.
        let lexed = parsed.lexed();
        let started = lexed.partition_point(|t| t.start <= offset);
        let token = (lexed[..started].iter().rev())
            .find(|t| t.synthetic.is_none())
            .filter(|t| t.kind == TokenKind::Word && offset < t.end)?;
        let text = &parsed.source()[token.range()];
        let builtin = Builtin::named(text)?;
        if matches!(builtin, Builtin::Type(_)) {
            return None;
        }
        let at = core_declaration(text)?;
        let names = match self.target(file, offset) {
            Some((node, Some(target))) => {
                let span = self.name_span(node);
                let inside = span.start <= offset && offset < span.end;
                match (builtin, target) {
                    (Builtin::Macro(_), Target::Symbol(symbol)) => {
                        let s = self.settled.scopes.symbol(symbol);
                        self.settled.library.names.get(s.name) == text
                    }
                    (Builtin::Macro(_), Target::Member { .. }) => false,
                    _ => inside,
                }
            }
            _ => false,
        };
        (!names).then(|| Syntax {
            name: text.to_string(),
            span: Span {
                start: token.start,
                end: token.end,
            },
            at,
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

/// A builtin word core documents, as [`Analysis::syntax_at`] finds it.
pub(crate) struct Syntax {
    /// Its text, as `if` or `orelse`.
    pub name: String,
    /// Its token's byte offsets in its file.
    pub span: Span,
    /// Its declaration's offset in core's text, as [`core_declaration`]
    /// gives it.
    pub at: usize,
}

/// A builtin function's or value's type as core's declaration at byte
/// `at` of its text writes it: a function's signature with its name, as
/// `ignore(value: AnyValue): Void`, or a value's type, as `Console`; None
/// for a declaration with neither, as `export let if;`.
pub(crate) fn core_signature(at: usize, name: &str) -> Option<String> {
    let rest = &corelib::code()[at..];
    let rest = match rest.strip_prefix("nym`") {
        Some(quoted) => &quoted[quoted.find('`')? + 1..],
        None => rest.strip_prefix(name)?,
    };
    // Up to its body, its initial value, or its `;`, outside brackets.
    let mut depth = 0;
    let end = rest
        .char_indices()
        .find(|&(_, c)| {
            match c {
                '(' | '[' => depth += 1,
                ')' | ']' => depth -= 1,
                _ => {}
            }
            depth == 0 && matches!(c, '{' | '=' | ';')
        })
        .map_or(rest.len(), |(i, _)| i);
    let shown = rest[..end].split_whitespace().collect::<Vec<_>>().join(" ");
    if shown.starts_with(['(', '<']) {
        return Some(format!("{name}{shown}"));
    }
    let ty = shown.strip_prefix(':')?.trim();
    (!ty.is_empty()).then(|| ty.to_string())
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
    let rest = &corelib::code()[at + name.len()..];
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
    let text = corelib::code();
    let line_start = text.get(..at)?.rfind('\n').map_or(0, |i| i + 1);
    let line = text[line_start..].lines().next()?;
    let rest = line.trim_start().strip_prefix("export let ")?;
    let (_, aliased) = rest.split_once(" = ")?;
    let aliased = aliased.strip_suffix(';')?;
    aliased
        .chars()
        .all(|c| c.is_alphanumeric() || c == '_')
        .then_some(aliased)
}

/// The length of the name at byte `at` of core's text: a word, or a
/// quoted one, ``nym`true` ``.
fn core_name_len(at: usize) -> usize {
    let rest = &corelib::code()[at..];
    match rest.strip_prefix("nym`") {
        Some(quoted) => quoted.find('`').map_or(rest.len(), |end| end + 5),
        None => (rest.find(|c: char| !c.is_alphanumeric() && c != '_')).unwrap_or(rest.len()),
    }
}

/// What documents a declaration whose name is at byte `at` of core's
/// text, as a library's declarations' docs ([`Analysis::doc`]): its doc
/// comment, `/** … */`, as [`doc_text`] gives it, or else the prose
/// before it in its `.temper.md` file, from the paragraph that starts
/// with its name, as [`prose_text`] gives it.
pub(crate) fn core_doc(at: usize) -> Option<String> {
    core_comment_doc(at).or_else(|| {
        let name = &corelib::code()[at..at + core_name_len(at)];
        let name = name
            .strip_prefix("nym`")
            .map_or(name, |n| n.trim_end_matches('`'));
        let doc = corelib::doc_string(at, name)?;
        doc.prose.then(|| prose_text(&doc.long)).flatten()
    })
}

/// The doc comment, `/** … */`, right before a declaration whose name is
/// at byte `at` of core's text, past decorations' lines, as
/// [`doc_text`] gives it.
fn core_comment_doc(at: usize) -> Option<String> {
    let text = corelib::code();
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
