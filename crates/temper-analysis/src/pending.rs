//! Help for text not settled yet, typed after the end of a scope: a REPL
//! input as it's typed, against what earlier inputs declared. With no
//! tree for the text, these read it as signature help does (the brackets
//! open, the name before a `(` or `.`), and resolve names from the
//! scope, through members' declared types. Nothing is settled or run,
//! so they're quick enough for every keystroke.

use super::members::{Named, Receiver};
use super::*;
use temper_frontend::builtins::TYPES;
use temper_frontend::repl::STEPS;
use temper_frontend::typing::builtin_members;
use temper_syntax::lex::{SourceKind, TokenKind, lex};

/// What a name before `.` is: a value, with members of its type, or a
/// type, with its statics.
enum Before {
    Value(Receiver),
    Type(Named),
}

impl Analysis<'_> {
    /// What may go at byte `offset` of `text`, typed in `scope`: where
    /// the word being typed starts, and the candidates that start with
    /// it. After `x.`, the members of `x`'s type, or a type's statics;
    /// otherwise the text's own declarations before the word, the names
    /// in scope, innermost first, and keywords. Nothing in a string or a
    /// comment.
    pub fn pending_completions(
        &self,
        scope: ScopeId,
        text: &str,
        offset: usize,
    ) -> (usize, Vec<Completion>) {
        let offset = floor_boundary(text, offset);
        let start = word_start(text, offset);
        let prefix = &text[start..offset];
        if in_string_or_comment(text, start) {
            return (start, Vec::new());
        }
        let mut out: Vec<Completion> = Vec::new();
        if let Some(object) = text[..start].trim_end().strip_suffix('.') {
            if let Some(before) = self.before_dot(scope, object) {
                out = self.members_of_before(&before);
            }
        } else {
            for (name, kind) in own_declarations(&text[..start]) {
                out.push(Completion {
                    name,
                    kind,
                    detail: None,
                });
            }
            out.extend(self.names_in(scope));
            for &k in KEYWORDS {
                out.push(Completion {
                    name: k.to_string(),
                    kind: DeclKind::Builtin,
                    detail: None,
                });
            }
        }
        let mut seen = std::collections::HashSet::new();
        out.retain(|c| c.name.starts_with(prefix) && seen.insert(c.name.clone()));
        (start, out)
    }

    /// The signature of the call whose arguments byte `offset` of `text`,
    /// typed in `scope`, is among, with the argument it's at: a function
    /// in scope, a method after `x.`, or a class's constructor.
    pub fn pending_signature(
        &self,
        scope: ScopeId,
        text: &str,
        offset: usize,
    ) -> Option<SignatureHelp> {
        let offset = floor_boundary(text, offset);
        let open = signatures::open_brackets(text, offset)?;
        let call = open.iter().rev().find(|o| o.bracket == b'(')?;
        let before = signatures::skip_type_args(text[..call.at].trim_end());
        let start = word_start(before, before.len());
        let name = &before[start..];
        if name.is_empty() {
            return None;
        }
        let callee = match before[..start].trim_end().strip_suffix('.') {
            Some(object) => match self.before_dot(scope, object)? {
                Before::Value(receiver) => self.member_callee(&receiver, name)?,
                Before::Type(Named::Class(class)) => {
                    let (decl, subst) = self.static_member(class, name)?;
                    self.fn_callee(decl, name, subst)?
                }
                Before::Type(Named::Builtin(_)) => return None,
            },
            None => {
                let symbol = self.lookup_in(scope, name)?;
                let decl = self.settled.scopes.symbol(symbol).decl?;
                self.decl_callee(decl, name)?
            }
        };
        Some(SignatureHelp {
            label: callee.label,
            params: callee.params.into_iter().map(|(p, _)| p).collect(),
            active: call.commas,
            doc: callee.doc,
        })
    }

    /// In a string that's an argument of one of the REPL's functions,
    /// at byte `offset` of `text`, typed in `scope`: where the string's
    /// text starts, and what may go there that starts with what's typed:
    /// `describe(n, "` its steps, `translate(n, "` the `languages`, and
    /// `help("` the names in scope. `None` anywhere else.
    pub fn pending_argument_completions(
        &self,
        scope: ScopeId,
        text: &str,
        offset: usize,
        languages: &[String],
    ) -> Option<(usize, Vec<String>)> {
        let before = text.get(..floor_boundary(text, offset))?;
        let quote = before.rfind('"')?;
        let typed = &before[quote + 1..];
        if typed.contains(['"', '\\', '$']) {
            return None;
        }
        let call = before[..quote].trim_end();
        let words: Vec<String> = match call.strip_suffix('(').map(str::trim_end) {
            Some(call) if ends_with_name(call, "help") => {
                let (_, names) = self.pending_completions(scope, typed, typed.len());
                (names.into_iter())
                    .map(|c| c.name)
                    .filter(|n| !KEYWORDS.contains(&n.as_str()))
                    .collect()
            }
            Some(_) => return None,
            None => {
                // `describe(0, "` or `translate(0, "`.
                let call = call.strip_suffix(',')?.trim_end();
                let call = call.trim_end_matches(|c: char| c.is_ascii_digit());
                let call = call.trim_end().strip_suffix('(')?.trim_end();
                if ends_with_name(call, "describe") {
                    STEPS.iter().map(|(s, _)| s.to_string()).collect()
                } else if ends_with_name(call, "translate") {
                    languages.to_vec()
                } else {
                    return None;
                }
            }
        };
        let words = words.into_iter().filter(|w| w.starts_with(typed)).collect();
        Some((quote + 1, words))
    }

    /// What hovering at byte `offset` of `text`, typed in `scope`, shows:
    /// the name there, as a name in scope, or a member after `x.`, with
    /// its span in `text`. `None` for a keyword, a name the text declares
    /// itself, or one that isn't found.
    pub fn pending_hover(&self, scope: ScopeId, text: &str, offset: usize) -> Option<Hover> {
        let offset = floor_boundary(text, offset);
        let start = word_start(text, offset);
        let end = offset
            + (text[offset..].char_indices())
                .take_while(|&(_, c)| c.is_alphanumeric() || c == '_')
                .last()
                .map_or(0, |(i, c)| i + c.len_utf8());
        let name = &text[start..end];
        if !name.starts_with(|c: char| c.is_alphabetic() || c == '_')
            || KEYWORDS.contains(&name)
            || in_string_or_comment(text, start)
            || own_declarations(&text[..end])
                .iter()
                .any(|(n, _)| n == name)
        {
            return None;
        }
        let mut hover = match text[..start].trim_end().strip_suffix('.') {
            Some(object) => self.member_hover(&self.before_dot(scope, object)?, name)?,
            None => self.symbol_hover(self.lookup_in(scope, name)?),
        };
        hover.span = Span {
            start: start as u32,
            end: end as u32,
        };
        Some(hover)
    }

    /// What hover shows of the member `name` of what's before a `.`.
    fn member_hover(&self, before: &Before, name: &str) -> Option<Hover> {
        let declared = |(decl, subst): (NodeId, Subst)| Hover {
            span: Span { start: 0, end: 0 },
            name: Some(name.to_string()),
            kind: Some(self.decl_kind(decl, None)),
            ty: self.decl_type_in(decl, name, decl, &subst),
            doc: self.doc(decl),
        };
        let builtin = |m: temper_frontend::typing::BuiltinMember| Hover {
            span: Span { start: 0, end: 0 },
            name: Some(name.to_string()),
            kind: Some(match m.is_method {
                true => DeclKind::Method,
                false => DeclKind::Field,
            }),
            ty: Some(m.detail),
            doc: None,
        };
        match before {
            Before::Value(receiver) => match self.find_member(receiver, name) {
                Some(found) => Some(declared(found)),
                None => self.builtin_member(receiver, name).map(builtin),
            },
            Before::Type(Named::Class(class)) => self.static_member(*class, name).map(declared),
            Before::Type(Named::Builtin(ty)) => (builtin_members(ty, &[]).into_iter())
                .find(|m| m.is_static && m.name == name)
                .map(builtin),
        }
    }

    /// What hover shows of a symbol, as at its declaration: its kind,
    /// type or signature, and doc comment; for a builtin, core's.
    pub fn symbol_hover(&self, symbol: SymbolId) -> Hover {
        if let Some(h) = self.namespace_hover(symbol) {
            return h;
        }
        let s = self.settled.scopes.symbol(symbol);
        let name = self.settled.library.names.get(s.name).to_string();
        let decl = s.decl.map(|d| self.imported(d));
        let kind = match decl {
            Some(d) if decl != s.decl => self.decl_kind(d, None),
            _ => self.kind(symbol),
        };
        let ty = type_name_shown(s, &name).or_else(|| match decl {
            Some(d) => self.decl_type(d, &name, d),
            // A builtin's, as core declares it: `console: Console`.
            None => (self.core_target(Target::Symbol(symbol)))
                .and_then(|(ats, _)| builtins::core_signature(*ats.first()?, &name)),
        });
        let doc = match decl {
            Some(d) => self.doc(d),
            None => (self.core_target(Target::Symbol(symbol)))
                .and_then(|(ats, _)| ats.into_iter().find_map(builtins::core_doc)),
        };
        let span = match decl {
            Some(d) => self.local(d),
            None => Span { start: 0, end: 0 },
        };
        Hover {
            span,
            name: Some(name),
            kind: Some(kind),
            ty,
            doc,
        }
    }

    /// The symbol a name means from the end of `scope`.
    pub fn lookup_in(&self, scope: ScopeId, name: &str) -> Option<SymbolId> {
        let name = self.settled.library.names.find(name)?;
        let scopes = &self.settled.scopes;
        let mut at = Some(scope);
        while let Some(id) = at {
            let s = scopes.get(id);
            if let Some(&(_, symbol)) = s.names.iter().rev().find(|(n, _)| *n == name) {
                return Some(symbol);
            }
            at = s.parent;
        }
        None
    }

    /// The names visible at the end of `scope`, innermost first, each
    /// once, builtins last.
    fn names_in(&self, scope: ScopeId) -> Vec<Completion> {
        let scopes = &self.settled.scopes;
        let names = &self.settled.library.names;
        let mut out = Vec::new();
        let mut seen = std::collections::HashSet::new();
        let mut at = Some(scope);
        while let Some(id) = at {
            let s = scopes.get(id);
            for &(name, symbol) in s.names.iter().rev() {
                let text = names.get(name);
                if names.is_temporary(name)
                    || !text.starts_with(|c: char| c.is_alphabetic() || c == '_')
                    || !seen.insert(name)
                {
                    continue;
                }
                let sym = scopes.symbol(symbol);
                out.push(Completion {
                    name: text.to_string(),
                    kind: self.kind(symbol),
                    detail: sym.decl.and_then(|d| self.decl_type(d, text, d)),
                });
            }
            at = s.parent;
        }
        out
    }

    /// What the expression ending `object`, before a `.`, is: a chain of
    /// names, `a.b.c`, from a name in scope through fields' and getters'
    /// declared types, or a string literal.
    fn before_dot(&self, scope: ScopeId, object: &str) -> Option<Before> {
        let object = object.trim_end();
        if object.ends_with('"') {
            return Some(Before::Value(Receiver::Builtin {
                name: "String",
                args: Vec::new(),
            }));
        }
        let start = chain_start(object);
        let mut parts = object[start..].split('.').map(str::trim);
        let first = parts.next().filter(|p| !p.is_empty())?;
        let symbol = self.lookup_in(scope, first)?;
        let s = self.settled.scopes.symbol(symbol);
        let tree = &self.settled.library.tree;
        let mut before = match s.decl {
            Some(decl) if tree.kind(decl) == NodeKind::Class => Before::Type(Named::Class(decl)),
            Some(decl) => {
                let t = *self.settled.typing.decls.get(&self.imported(decl))?;
                Before::Value(self.receiver(t)?)
            }
            None => {
                let name = self.settled.library.names.get(s.name);
                match TYPES.iter().find(|&&t| t == name) {
                    Some(&name) => Before::Type(Named::Builtin(name)),
                    None if name == "console" => Before::Value(Receiver::Builtin {
                        name: "Console",
                        args: Vec::new(),
                    }),
                    None => return None,
                }
            }
        };
        for part in parts {
            let (decl, subst) = match &before {
                Before::Value(receiver) => self.find_member(receiver, part)?,
                Before::Type(Named::Class(class)) => self.static_member(*class, part)?,
                Before::Type(Named::Builtin(_)) => return None,
            };
            let _ = subst;
            let t = *self.settled.typing.decls.get(&decl)?;
            before = Before::Value(self.receiver(t)?);
        }
        Some(before)
    }

    /// The members after `.` on what [`Analysis::before_dot`] found.
    fn members_of_before(&self, before: &Before) -> Vec<Completion> {
        match before {
            Before::Value(receiver) => self.members_of(receiver),
            Before::Type(Named::Class(class)) => {
                let mut out = Vec::new();
                self.class_members(*class, &Subst::new(), true, &mut out, &mut Vec::new());
                out
            }
            Before::Type(Named::Builtin(name)) => (builtin_members(name, &[]).into_iter())
                .filter(|m| m.is_static)
                .map(|m| Completion {
                    name: m.name,
                    kind: match m.is_method {
                        true => DeclKind::Method,
                        false => DeclKind::Field,
                    },
                    detail: Some(m.detail),
                })
                .collect(),
        }
    }

    /// A class's static member, by name.
    fn static_member(&self, class: NodeId, name: &str) -> Option<(NodeId, Subst)> {
        let tree = &self.settled.library.tree;
        let (_, _, members) = core::class_parts(tree, class);
        members
            .iter()
            .copied()
            .find(|&m| {
                tree.data(m) & core::MEMBER_STATIC != 0
                    && (self.settled.library.name(tree.children(m)[0]))
                        .is_some_and(|n| self.settled.library.names.get(n) == name)
            })
            .map(|m| (m, Subst::new()))
    }
}

/// The largest char boundary at or before `offset`.
fn floor_boundary(text: &str, offset: usize) -> usize {
    let mut offset = offset.min(text.len());
    while !text.is_char_boundary(offset) {
        offset -= 1;
    }
    offset
}

/// Where the word ending at `offset` starts.
fn word_start(text: &str, offset: usize) -> usize {
    text[..offset]
        .char_indices()
        .rev()
        .take_while(|&(_, c)| c.is_alphanumeric() || c == '_')
        .last()
        .map_or(offset, |(i, _)| i)
}

/// Whether `text` ends with a name, not as the end of a longer name or
/// a member.
fn ends_with_name(text: &str, name: &str) -> bool {
    text.strip_suffix(name).is_some_and(|ahead| {
        !(ahead.chars().next_back()).is_some_and(|c| c.is_alphanumeric() || c == '_' || c == '.')
    })
}

/// Where a chain of names, `a.b.c`, ending `text`, starts.
fn chain_start(text: &str) -> usize {
    text.char_indices()
        .rev()
        .take_while(|&(_, c)| c.is_alphanumeric() || c == '_' || c == '.' || c == ' ')
        .last()
        .map_or(text.len(), |(i, _)| i)
        .max(text.len() - text.trim_start().len())
}

/// Whether byte `at` of `text` is in a string's text or a comment.
fn in_string_or_comment(text: &str, at: usize) -> bool {
    let tokens = lex(text, SourceKind::Standalone);
    tokens.iter().any(|t| {
        let (start, end) = (t.start as usize, t.end as usize);
        match t.kind {
            TokenKind::QuotedString => start <= at && at <= end,
            TokenKind::Comment => {
                let token = &text[t.range()];
                let closed = token.starts_with("/*") && token.ends_with("*/") && token.len() > 3;
                start < at && (at < end || at == end && !closed)
            }
            _ => false,
        }
    })
}

/// The names that `let`, `var`, `class`, and `interface` declare in
/// `text`, in order, then its parameters', as `n` in `(n: Int)`.
fn own_declarations(text: &str) -> Vec<(String, DeclKind)> {
    let tokens: Vec<_> = (lex(text, SourceKind::Standalone).into_iter())
        .filter(|t| !t.kind.is_ignorable())
        .collect();
    let words: Vec<&str> = (tokens.iter())
        .filter(|t| t.kind == TokenKind::Word)
        .map(|t| &text[t.range()])
        .collect();
    let mut out: Vec<(String, DeclKind)> = Vec::new();
    for pair in words.windows(2) {
        let kind = match pair[0] {
            "let" => DeclKind::Let,
            "var" => DeclKind::Var,
            "class" => DeclKind::Class,
            "interface" => DeclKind::Interface,
            _ => continue,
        };
        if !KEYWORDS.contains(&pair[1]) {
            out.push((pair[1].to_string(), kind));
        }
    }
    // A parameter, as `n` in `(n: Int)`: any other name before `:`.
    for pair in tokens.windows(2) {
        let (name, colon) = (&text[pair[0].range()], &text[pair[1].range()]);
        if pair[0].kind == TokenKind::Word
            && colon == ":"
            && !KEYWORDS.contains(&name)
            && !out.iter().any(|(n, _)| n == name)
        {
            out.push((name.to_string(), DeclKind::Parameter));
        }
    }
    out
}
