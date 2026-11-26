//! Help while typing a call or an object literal, where the code is
//! often too unfinished to have settled into a call: signature help for
//! the call whose arguments a position is in, and completion of the
//! property names an object literal's class takes (`{ na| }` for a class
//! with `name`). Both read the brackets open at the position from the
//! source text ([`open_brackets`]) and resolve the callee or the class
//! from names in scope and types settling worked out.

use super::members::Receiver;
use super::*;
use temper_frontend::typing::core_member_declarations;

/// A call's signature, while typing its arguments.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct SignatureHelp {
    /// As hover shows it: `twice(n: Int32, extra?: Int32): Int32`.
    pub label: String,
    /// Each parameter as the label writes it, `extra?: Int32`.
    pub params: Vec<String>,
    /// The parameter the position is at, from 0.
    pub active: usize,
    pub doc: Option<String>,
}

/// A bracket open at a position.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Open {
    pub bracket: u8,
    /// Where it is.
    pub at: usize,
    /// Commas since, at its depth.
    pub commas: usize,
    /// Just past it or its last comma.
    pub item: usize,
}

/// What signature help knows of a callee.
pub(crate) struct Callee {
    pub(crate) label: String,
    pub(crate) params: Vec<(String, Option<TypeId>)>,
    pub(crate) doc: Option<String>,
    subst: Subst,
}

impl Analysis<'_> {
    /// The signature of the call whose arguments byte `offset` of `file`
    /// is among, with the argument it's at.
    pub fn signature_help(&self, file: FileId, offset: u32) -> Option<SignatureHelp> {
        let source = self.source(file)?;
        let open = open_brackets(source, offset as usize)?;
        let call = open.iter().rev().find(|o| o.bracket == b'(')?;
        let callee = self.callee(file, call.at)?;
        Some(SignatureHelp {
            label: callee.label,
            params: callee.params.into_iter().map(|(p, _)| p).collect(),
            active: call.commas,
            doc: callee.doc,
        })
    }

    /// What's called by the `(` at byte `paren` of `file`: a function, a
    /// method after `.`, or a class's constructor after `new`.
    fn callee(&self, file: FileId, paren: usize) -> Option<Callee> {
        let source = self.source(file)?;
        let before = skip_type_args(source[..paren].trim_end());
        let name_start = before
            .rfind(|c: char| !(c.is_alphanumeric() || c == '_'))
            .map_or(0, |i| {
                i + before[i..].chars().next().map_or(1, char::len_utf8)
            });
        let name = &before[name_start..];
        if name.is_empty() {
            return None;
        }
        let ahead = before[..name_start].trim_end();
        if let Some(object) = ahead.strip_suffix('.') {
            let node = self.receiver_node(file, object.trim_end().len() as u32)?;
            let receiver = self.receiver(self.settled.typing.expr(node)?)?;
            return self.member_callee(&receiver, name);
        }
        let symbol = self.lookup(file, name_start as u32, name)?;
        let decl = self.settled.scopes.symbol(symbol).decl?;
        self.decl_callee(decl, name)
    }

    /// A member's signature, by name: a builtin type's, or a class's.
    pub(crate) fn member_callee(&self, receiver: &Receiver, name: &str) -> Option<Callee> {
        if let Some(m) = self.builtin_member(receiver, name) {
            // Its docs from core, as hover's: an override's, or else
            // what it overrides'.
            let doc = match receiver {
                Receiver::Builtin { name: owner, .. } => (core_member_declarations(owner, name))
                    .into_iter()
                    .find_map(builtins::core_doc),
                Receiver::Class { .. } => None,
            };
            return Some(Callee {
                label: m.detail,
                params: m.params.into_iter().map(|p| (p, None)).collect(),
                doc,
                subst: Subst::new(),
            });
        }
        let (decl, subst) = self.find_member(receiver, name)?;
        self.fn_callee(decl, name, subst)
    }

    /// A declaration's signature: a function's, or a class's
    /// constructor's.
    pub(crate) fn decl_callee(&self, decl: NodeId, name: &str) -> Option<Callee> {
        let tree = &self.settled.library.tree;
        if tree.kind(decl) == NodeKind::Class {
            // `new C(…)`: its constructor's parameters.
            let params: Vec<(String, Option<TypeId>)> = (core::constructor_params(tree, decl)
                .iter())
            .map(|&p| {
                let n = (self.settled.library.name(tree.children(p)[0]))
                    .map_or("_", |n| self.settled.library.names.get(n));
                let optional = core::let_parts(tree, p).2.is_some();
                let t = self.settled.typing.decls.get(&p).copied();
                let ty = t.map_or("?".into(), |t| self.show(t));
                (format!("{n}{}: {ty}", if optional { "?" } else { "" }), t)
            })
            .collect();
            let shown: Vec<&str> = params.iter().map(|(p, _)| p.as_str()).collect();
            return Some(Callee {
                label: format!("{name}({})", shown.join(", ")),
                params,
                doc: self.doc(decl),
                subst: Subst::new(),
            });
        }
        self.fn_callee(decl, name, Subst::new())
    }

    /// A function's or method's signature, by its `Let`.
    pub(crate) fn fn_callee(&self, decl: NodeId, name: &str, subst: Subst) -> Option<Callee> {
        let tree = &self.settled.library.tree;
        if tree.kind(decl) != NodeKind::Let {
            return None;
        }
        let f = core::let_parts(tree, decl)
            .2
            .filter(|&i| tree.kind(i) == NodeKind::Fn)?;
        let t = self.settled.typing.decls.get(&decl).copied();
        let (params, ret) = self.signature_parts(f, t, &subst);
        let shown: Vec<&str> = params.iter().map(|(p, _)| p.as_str()).collect();
        Some(Callee {
            label: format!("{name}({}): {ret}", shown.join(", ")),
            params,
            doc: self.doc(decl),
            subst,
        })
    }

    /// The property names an object literal's class takes, at a key's
    /// place in a literal at byte `offset` of `file`, leaving out those
    /// it has; `None` if it's not at a key in an object literal.
    pub(crate) fn literal_completions(&self, file: FileId, offset: u32) -> Option<Vec<Completion>> {
        let source = self.source(file)?;
        let word = source.get(..offset as usize)?;
        let word_start = word
            .trim_end_matches(|c: char| c.is_alphanumeric() || c == '_')
            .len();
        let open = open_brackets(source, word_start)?;
        let brace = *open.last()?;
        if brace.bracket != b'{' || !source[brace.item..word_start].trim().is_empty() {
            return None;
        }
        if !holds_properties(&source[..brace.at]) {
            return None;
        }
        let (class, subst) = self.literal_wants(file, &open)?;
        let tree = &self.settled.library.tree;
        let given: Vec<String> = (references::literal_keys(source, brace.at).into_iter())
            .filter(|&(_, at, _)| at != word_start)
            .map(|(k, _, _)| k)
            .collect();
        let mut out = Vec::new();
        for p in core::constructor_params(tree, class) {
            let Some(name) = self.settled.library.name(tree.children(p)[0]) else {
                continue;
            };
            let text = self.settled.library.names.get(name).to_string();
            if given.contains(&text) {
                continue;
            }
            out.push(Completion {
                detail: self.decl_type_in(p, &text, p, &subst),
                name: text,
                kind: DeclKind::Field,
            });
        }
        Some(out)
    }

    /// The class an object literal, the innermost of `open`, is for:
    /// what settling decided, or the declared type it's assigned to, or
    /// the parameter it's passed as.
    fn literal_wants(&self, file: FileId, open: &[Open]) -> Option<(NodeId, Subst)> {
        let brace = open.last()?;
        let source = self.source(file)?;
        let class_of = |t: TypeId| match self.receiver(t)? {
            Receiver::Class { decl, subst } => Some((decl, subst)),
            Receiver::Builtin { .. } => None,
        };
        // Settled already, as a constructor call.
        let pos = self.global(file, brace.at as u32);
        let tree = &self.settled.library.tree;
        let mut stack = vec![self.settled.library.files.get(file.0 as usize)?.root];
        while let Some(node) = stack.pop() {
            let span = tree.span(node);
            if span.start > pos || span.end <= pos {
                continue;
            }
            if span.start == pos && self.literal_class(node).is_some() {
                if let Some(found) = self.settled.typing.expr(node).and_then(class_of) {
                    return Some(found);
                }
            }
            stack.extend(tree.children(node));
        }
        let before = source[..brace.at].trim_end();
        // `let p: Person = {`.
        if let Some(lhs) = before
            .strip_suffix('=')
            .filter(|b| !b.ends_with(['=', '!', '<', '>']))
        {
            let ty = skip_type_args(lhs.trim_end().trim_end_matches('?').trim_end());
            let start = ty
                .rfind(|c: char| !(c.is_alphanumeric() || c == '_'))
                .map_or(0, |i| i + 1);
            let name = &ty[start..];
            if !name.is_empty() && ty[..start].trim_end().ends_with(':') {
                let symbol = self.lookup(file, start as u32, name)?;
                let decl = self.settled.scopes.symbol(symbol).decl?;
                return (tree.kind(decl) == NodeKind::Class).then(|| (decl, Subst::new()));
            }
            return None;
        }
        // `f({` or `f(a, {`: the parameter's type.
        if before.ends_with(['(', ',']) {
            let call = open[..open.len() - 1]
                .iter()
                .rev()
                .find(|o| o.bracket == b'(')?;
            let callee = self.callee(file, call.at)?;
            let (_, t) = callee.params.get(call.commas)?;
            let (decl, mut subst) = class_of((*t)?)?;
            subst.extend(callee.subst);
            return Some((decl, subst));
        }
        None
    }
}

/// Whether a `{` after `before` starts an object literal rather than a
/// block, as the parser decides from the token before it.
fn holds_properties(before: &str) -> bool {
    let before = before.trim_end();
    let Some(last) = before.chars().last() else {
        return true;
    };
    if last.is_alphanumeric() || last == '_' {
        let word_start = before
            .rfind(|c: char| !(c.is_alphanumeric() || c == '_'))
            .map_or(0, |i| i + 1);
        let word = &before[word_start..];
        return matches!(
            word,
            "orelse" | "let" | "new" | "await" | "return" | "throw" | "yield"
        );
    }
    !(before.ends_with("=>") || ")]}!?>".contains(last))
}

/// Text before a call's `(` without its type arguments, as `f<T>`'s `f`.
pub(crate) fn skip_type_args(text: &str) -> &str {
    if !text.ends_with('>') {
        return text;
    }
    let mut depth = 0;
    for (i, c) in text.char_indices().rev() {
        match c {
            '>' => depth += 1,
            '<' => {
                depth -= 1;
                if depth == 0 {
                    return text[..i].trim_end();
                }
            }
            _ => {}
        }
    }
    text
}

/// The brackets open at byte `offset` of `source`, outermost first,
/// reading strings (with `${…}`) and comments as the lexer does; `None`
/// inside a string or comment.
pub(crate) fn open_brackets(source: &str, offset: usize) -> Option<Vec<Open>> {
    #[derive(PartialEq)]
    enum Frame {
        Bracket(Open),
        /// In a string.
        Text,
        /// In a string's `${…}`.
        Hole,
    }
    let bytes = source.as_bytes();
    let end = offset.min(bytes.len());
    let mut frames: Vec<Frame> = Vec::new();
    let mut i = 0;
    while i < end {
        let c = bytes[i];
        if frames.last() == Some(&Frame::Text) {
            match c {
                b'\\' => i += 1,
                b'"' => {
                    frames.pop();
                }
                b'$' if bytes.get(i + 1) == Some(&b'{') => {
                    frames.push(Frame::Hole);
                    i += 1;
                }
                _ => {}
            }
            i += 1;
            continue;
        }
        match c {
            b'/' if bytes.get(i + 1) == Some(&b'/') => match source[i..].find('\n') {
                Some(n) if i + n < end => i += n,
                _ => return None,
            },
            b'/' if bytes.get(i + 1) == Some(&b'*') => match source[i + 2..].find("*/") {
                Some(n) if i + 2 + n + 2 <= end => i += 2 + n + 1,
                _ => return None,
            },
            b'"' => frames.push(Frame::Text),
            b'(' | b'[' | b'{' => frames.push(Frame::Bracket(Open {
                bracket: c,
                at: i,
                commas: 0,
                item: i + 1,
            })),
            b'}' if frames.last() == Some(&Frame::Hole) => {
                frames.pop();
            }
            b')' | b']' | b'}' => {
                if let Some(Frame::Bracket(_)) = frames.last() {
                    frames.pop();
                }
            }
            b',' => {
                if let Some(Frame::Bracket(open)) = frames.last_mut() {
                    open.commas += 1;
                    open.item = i + 1;
                }
            }
            _ => {}
        }
        i += 1;
    }
    if frames.iter().any(|f| *f == Frame::Text) && frames.last() == Some(&Frame::Text) {
        return None;
    }
    Some(
        frames
            .into_iter()
            .filter_map(|f| match f {
                Frame::Bracket(open) => Some(open),
                _ => None,
            })
            .collect(),
    )
}
