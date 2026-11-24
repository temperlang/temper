//! Find references and rename: every use of what a name at a position
//! names, by its declaration (or a builtin's symbol or key), across the
//! library's files, and the edits that rename them all. Renaming refuses
//! builtins and std's declarations, and names that aren't identifiers or
//! are keywords. A constructor property's rename also renames object
//! literals' keys for it (`{ name: "a" }`), which settling has replaced
//! by a constructor call.

use super::*;

/// One replacement of text in a file.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Edit {
    pub file: FileId,
    /// Byte offsets in its file.
    pub span: Span,
    pub text: String,
}

/// Words that read as keywords, which a rename mustn't make a name,
/// as the playground's highlighting and the editors' grammars list them.
pub const KEYWORDS: &[&str] = &[
    "as",
    "assert",
    "async",
    "await",
    "break",
    "bubble",
    "case",
    "catch",
    "class",
    "const",
    "continue",
    "do",
    "else",
    "export",
    "extends",
    "false",
    "fn",
    "for",
    "get",
    "if",
    "import",
    "in",
    "interface",
    "is",
    "let",
    "new",
    "null",
    "of",
    "orelse",
    "panic",
    "private",
    "protected",
    "public",
    "return",
    "sealed",
    "set",
    "static",
    "super",
    "test",
    "this",
    "throw",
    "throws",
    "true",
    "try",
    "var",
    "void",
    "when",
    "while",
    "yield",
];

/// What a name names, as references compare it.
#[derive(Clone, Debug, PartialEq, Eq)]
enum Ident {
    Decl(NodeId),
    /// A builtin, or anything else without a declaration.
    Symbol(SymbolId),
    /// A builtin type's member, by its key, as `ListBuilder.add`.
    Builtin(String),
}

impl Analysis<'_> {
    /// Every use of what the name at byte `offset` of `file` names, in
    /// the library's files, in file and position order, with its
    /// declaration's name too if `declaration`.
    pub fn references(&self, file: FileId, offset: u32, declaration: bool) -> Vec<Location> {
        let Some((_, ident, name)) = self.ident_at(file, offset) else {
            return Vec::new();
        };
        self.uses(&ident, &name, declaration)
            .into_iter()
            .map(|(file, span, _)| Location { file, span })
            .collect()
    }

    /// Whether the name at byte `offset` of `file` can be renamed: its
    /// span and text, or why not.
    pub fn prepare_rename(&self, file: FileId, offset: u32) -> Result<(Span, String), String> {
        let (node, ident, name) = self
            .ident_at(file, offset)
            .ok_or("Nothing to rename here")?;
        let decl = match ident {
            Ident::Decl(decl) => decl,
            _ => return Err(format!("{name} is a builtin, which can't be renamed")),
        };
        let (decl_file, _) = self
            .settled
            .library
            .locate(self.settled.library.tree.span(decl).start);
        if self.is_std(decl_file) {
            return Err(format!("{name} is declared in std, which can't be renamed"));
        }
        Ok((self.name_span(node), name))
    }

    /// The edits that rename what the name at byte `offset` of `file`
    /// names to `new_name`, everywhere in the library, or why not.
    pub fn rename(&self, file: FileId, offset: u32, new_name: &str) -> Result<Vec<Edit>, String> {
        self.prepare_rename(file, offset)?;
        let identifier = new_name.starts_with(|c: char| c.is_alphabetic() || c == '_')
            && new_name.chars().all(|c| c.is_alphanumeric() || c == '_');
        if !identifier || KEYWORDS.contains(&new_name) {
            return Err(format!("{new_name} isn't a name that can be declared"));
        }
        let (_, ident, name) = self
            .ident_at(file, offset)
            .ok_or("Nothing to rename here")?;
        let mut edits: Vec<Edit> = Vec::new();
        for (file, span, shorthand) in self.uses(&ident, &name, true) {
            if self.is_std(file) {
                return Err(format!("{name} is used in std, which can't change"));
            }
            let text = match shorthand {
                // `{ name }` keeps its value, the local `name`.
                true => format!("{new_name}: {name}"),
                false => new_name.to_string(),
            };
            edits.push(Edit { file, span, text });
        }
        Ok(edits)
    }

    /// What the name at a position names, with its node and text.
    fn ident_at(&self, file: FileId, offset: u32) -> Option<(NodeId, Ident, String)> {
        let (node, target) = self.target(file, offset)?;
        let ident = self.ident(target?)?;
        let name = self.text_of(node)?.to_string();
        Some((node, ident, name))
    }

    /// A target's identity.
    fn ident(&self, target: Target) -> Option<Ident> {
        Some(match target {
            Target::Symbol(symbol) => match self.settled.scopes.symbol(symbol).decl {
                Some(decl) => Ident::Decl(self.imported(decl)),
                None => Ident::Symbol(symbol),
            },
            Target::Member { call, .. } => match self.member(call)? {
                Resolved::Decl(decl) => Ident::Decl(*decl),
                Resolved::Builtin(key) => Ident::Builtin(key.clone()),
                _ => return None,
            },
        })
    }

    /// A node's text in its file.
    fn text_of(&self, node: NodeId) -> Option<&str> {
        let (file, _) = self
            .settled
            .library
            .locate(self.settled.library.tree.span(node).start);
        let span = self.name_span(node);
        self.source(file)?
            .get(span.start as usize..span.end as usize)
    }

    /// Each use of `ident`, written `name`, with whether it's an object
    /// literal's shorthand key (`{ name }`), in file and position order.
    fn uses(&self, ident: &Ident, name: &str, declaration: bool) -> Vec<(FileId, Span, bool)> {
        let library = &self.settled.library;
        let tree = &library.tree;
        let mut found: Vec<(FileId, Span, bool)> = Vec::new();
        for (i, f) in library.files.iter().enumerate() {
            let file = FileId(i as u32);
            let mut stack = vec![f.root];
            while let Some(node) = stack.pop() {
                stack.extend(tree.children(node));
                let Some(target) = self.name_target(node) else {
                    continue;
                };
                if self.ident(target).as_ref() != Some(ident) || self.text_of(node) != Some(name) {
                    continue;
                }
                let is_decl = tree.kind(node) == NodeKind::LeftName;
                if is_decl && !declaration {
                    continue;
                }
                found.push((file, self.name_span(node), false));
            }
        }
        if let Ident::Decl(decl) = ident {
            found.extend(self.literal_keys(*decl, name));
        }
        found.sort_by_key(|&(file, span, _)| (file.0, span.start, span.end));
        found.dedup_by_key(|&mut (file, span, _)| (file.0, span.start));
        found
    }

    /// Object literals' keys that name a constructor property `decl`,
    /// written `name`: those of literals whose class is the property's.
    fn literal_keys(&self, decl: NodeId, name: &str) -> Vec<(FileId, Span, bool)> {
        let library = &self.settled.library;
        let tree = &library.tree;
        let Some(class) = self.class_of_property(decl) else {
            return Vec::new();
        };
        let mut found = Vec::new();
        for (i, f) in library.files.iter().enumerate() {
            let file = FileId(i as u32);
            let source = f.parsed.source();
            let mut stack = vec![f.root];
            while let Some(node) = stack.pop() {
                stack.extend(tree.children(node));
                let Some(literal) = self.literal_class(node) else {
                    continue;
                };
                if literal != class {
                    continue;
                }
                let span = self.local(node);
                for (key, at, shorthand) in literal_keys(source, span.start as usize) {
                    if key == name {
                        let span = Span {
                            start: at as u32,
                            end: (at + key.len()) as u32,
                        };
                        found.push((file, span, shorthand));
                    }
                }
            }
        }
        found
    }

    /// The class whose constructor takes `decl` as a property: a
    /// header's or body's field, or its constructor's parameter.
    fn class_of_property(&self, decl: NodeId) -> Option<NodeId> {
        let tree = &self.settled.library.tree;
        let mut at = self.parent(decl)?;
        for _ in 0..3 {
            if tree.kind(at) == NodeKind::Class {
                let params = core::constructor_params(tree, at);
                return params.contains(&decl).then_some(at);
            }
            at = self.parent(at)?;
        }
        None
    }

    /// An object literal's class, if `node` is one settled to a
    /// constructor call: `{ … }` lowered to `new C(…)`.
    pub(crate) fn literal_class(&self, node: NodeId) -> Option<NodeId> {
        let library = &self.settled.library;
        let tree = &library.tree;
        if tree.kind(node) != NodeKind::Call {
            return None;
        }
        let children = tree.children(node);
        let callee = *children.first()?;
        let is_new = library
            .name(callee)
            .is_some_and(|n| library.names.get(n) == "new");
        if !is_new || self.text_of(callee) != Some("{") {
            return None;
        }
        let class = self.settled.scopes.resolved(*children.get(1)?)?;
        self.settled.scopes.symbol(class).decl
    }
}

/// The keys of the object literal whose `{` is at byte `open` of
/// `source`: each name, where it is, and whether it's shorthand.
pub(crate) fn literal_keys(source: &str, open: usize) -> Vec<(String, usize, bool)> {
    let bytes = source.as_bytes();
    let mut keys = Vec::new();
    let mut depth = 0usize;
    let mut at_key = true;
    let mut i = open + 1;
    while i < bytes.len() {
        let c = bytes[i];
        match c {
            b'"' => {
                i = skip_string(bytes, i);
                at_key = false;
                continue;
            }
            b'(' | b'[' | b'{' => depth += 1,
            b')' | b']' | b'}' if depth == 0 => break,
            b')' | b']' | b'}' => depth -= 1,
            b',' if depth == 0 => {
                at_key = true;
                i += 1;
                continue;
            }
            _ if depth == 0 && at_key && (c.is_ascii_alphabetic() || c == b'_') => {
                let start = i;
                while i < bytes.len() && (bytes[i].is_ascii_alphanumeric() || bytes[i] == b'_') {
                    i += 1;
                }
                let rest = source[i..].trim_start();
                let shorthand = rest.starts_with([',', '}']);
                if rest.starts_with(':') || shorthand {
                    keys.push((source[start..i].to_string(), start, shorthand));
                }
                at_key = false;
                continue;
            }
            _ if c.is_ascii_whitespace() => {}
            _ => at_key = false,
        }
        i += 1;
    }
    keys
}

/// Where a string that starts at byte `i` ends, past its `"`, skipping
/// escapes and `${…}`.
fn skip_string(bytes: &[u8], mut i: usize) -> usize {
    i += 1;
    while i < bytes.len() {
        match bytes[i] {
            b'\\' => i += 1,
            b'"' => return i + 1,
            b'$' if bytes.get(i + 1) == Some(&b'{') => {
                let mut depth = 0;
                i += 1;
                while i < bytes.len() {
                    match bytes[i] {
                        b'{' => depth += 1,
                        b'}' => {
                            depth -= 1;
                            if depth == 0 {
                                break;
                            }
                        }
                        b'"' => {
                            i = skip_string(bytes, i);
                            continue;
                        }
                        _ => {}
                    }
                    i += 1;
                }
            }
            _ => {}
        }
        i += 1;
    }
    bytes.len()
}
