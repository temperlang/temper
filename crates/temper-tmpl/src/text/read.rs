//! Reading canonical text, through the [`Builder`].
//!
//! Reading takes two passes over the tokens, after a scan that defines
//! every class and interface, used or the program's own, so a signature
//! may name any of them: the first reads `uses` sections and defines
//! modules, functions, classes' members, parameters, and module `let`s
//! from their headers, skipping bodies, so a body may use any of them; the
//! second reads bodies, where locals and labels must be defined before
//! they're used. The reader checks what text alone decides: syntax, ids
//! defined once, names matching their declarations, written types
//! matching the local rules, and `^` marking exactly the calls that may
//! bubble. Everything else is the validator's.

use super::{FORMAT_VERSION, MODEL_VERSION, writing_order};
use crate::Program;
use crate::builder::Builder;
use crate::decls::{DeclKind, Facts};
use crate::ids::{DeclId, FileId, NodeId, Span, TypeId};
use crate::nodes::Flags;
use crate::ops::Op;
use crate::schema::Kind;
use crate::types::TypeTable;
use std::fmt;

/// Why text couldn't be read, and where.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ReadError {
    pub line: usize,
    pub column: usize,
    pub message: String,
}

impl fmt::Display for ReadError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{}:{}: {}", self.line, self.column, self.message)
    }
}

impl std::error::Error for ReadError {}

/// Reads canonical text, or hand-written text close to it.
pub fn read(text: &str) -> Result<Program, ReadError> {
    let tokens = lex(text).map_err(|(at, message)| error_at(text, at, message))?;
    let mut r = Reader {
        src: text.to_string(),
        tokens,
        pos: 0,
        b: Builder::new("", ""),
        defined: Vec::new(),
        scanned: Vec::new(),
        owner: DeclId::NONE,
        this: TypeId::NONE,
    };
    let modules = r.headers()?;
    r.bodies(modules)
}

fn error_at(src: &str, at: usize, message: String) -> ReadError {
    let before = &src[..at.min(src.len())];
    let line = before.matches('\n').count() + 1;
    let column = before.len() - before.rfind('\n').map_or(0, |i| i + 1) + 1;
    ReadError {
        line,
        column,
        message,
    }
}

// Tokens.

#[derive(Clone, Debug, PartialEq)]
enum Tok {
    /// A bare or backquoted word without an id: keywords, types, and
    /// names such as the library's; also versions and hashes.
    Word {
        text: String,
        quoted: bool,
    },
    /// `name#id`, or `~hint|alt#id` if compiler-made.
    Decl {
        names: Vec<String>,
        id: u32,
        made: bool,
    },
    /// `%Int32.add`, without the `%`.
    Op(String),
    Int {
        value: i128,
        i64: bool,
    },
    Float(f64),
    Str(String),
    /// A `///` line, without the slashes and one space.
    Doc(String),
    Punct(&'static str),
    End,
}

#[derive(Clone, Debug)]
struct Token {
    tok: Tok,
    start: usize,
    end: usize,
}

const PUNCT: &[&str] = &[
    "&&", "||", "{", "}", "(", ")", ",", ";", ":", "=", "^", "#", "@", "-", "~", ".", "?", "<",
    "[", "]", ">", "&",
];

fn lex(src: &str) -> Result<Vec<Token>, (usize, String)> {
    let bytes = src.as_bytes();
    let mut tokens = Vec::new();
    let mut i = 0;
    loop {
        // Whitespace and comments.
        while i < bytes.len() {
            if bytes[i].is_ascii_whitespace() {
                i += 1;
            } else if src[i..].starts_with("//") && !src[i..].starts_with("///") {
                i = src[i..].find('\n').map_or(src.len(), |n| i + n);
            } else {
                break;
            }
        }
        let start = i;
        if i == bytes.len() {
            tokens.push(Token {
                tok: Tok::End,
                start,
                end: start,
            });
            return Ok(tokens);
        }
        let c = bytes[i];
        let after_word = i > 0 && bytes[i - 1].is_ascii_alphanumeric();
        let tok = if src[i..].starts_with("///") {
            let end = src[i..].find('\n').map_or(src.len(), |n| i + n);
            let line = &src[i + 3..end];
            i = end;
            Tok::Doc(line.strip_prefix(' ').unwrap_or(line).to_string())
        } else if c.is_ascii_digit() || (c == b'-' && !after_word && next_is_number(&src[i + 1..]))
        {
            let (tok, end) = number(src, i)?;
            i = end;
            tok
        } else if c.is_ascii_alphabetic() || c == b'_' || c == b'`' {
            let (name, end) = name(src, i)?;
            i = end;
            match id_after(src, i)? {
                Some((id, end)) => {
                    i = end;
                    Tok::Decl {
                        names: vec![name],
                        id,
                        made: false,
                    }
                }
                None => Tok::Word {
                    text: name,
                    quoted: c == b'`',
                },
            }
        } else if c == b'~' && bytes.get(i + 1).is_some_and(|&c| is_name_start(c)) {
            let mut names = Vec::new();
            i += 1;
            loop {
                let (name, end) = name(src, i)?;
                names.push(name);
                i = end;
                if bytes.get(i) == Some(&b'|') {
                    i += 1;
                } else {
                    break;
                }
            }
            let Some((id, end)) = id_after(src, i)? else {
                return Err((
                    i,
                    "expected `#` and an id after a compiler-made name".into(),
                ));
            };
            i = end;
            Tok::Decl {
                names,
                id,
                made: true,
            }
        } else if c == b'%' {
            let mut end = i + 1;
            loop {
                let word = src[end..]
                    .find(|c: char| !(c.is_ascii_alphanumeric() || c == '_'))
                    .map_or(src.len(), |n| end + n);
                if word == end {
                    return Err((end, "expected an operator after `%`".into()));
                }
                end = word;
                if src[end..].starts_with('.') {
                    end += 1;
                } else {
                    break;
                }
            }
            let op = src[i + 1..end].to_string();
            i = end;
            Tok::Op(op)
        } else if c == b'"' {
            let (text, end) = string(src, i)?;
            i = end;
            Tok::Str(text)
        } else if let Some(p) = PUNCT.iter().find(|p| src[i..].starts_with(**p)) {
            i += p.len();
            Tok::Punct(p)
        } else {
            let c = src[i..].chars().next().unwrap_or('?');
            return Err((i, format!("unexpected character {c:?}")));
        };
        tokens.push(Token { tok, start, end: i });
    }
}

fn is_name_start(c: u8) -> bool {
    c.is_ascii_alphabetic() || c == b'_' || c == b'`'
}

fn next_is_number(rest: &str) -> bool {
    rest.starts_with(|c: char| c.is_ascii_digit()) || rest.starts_with("inf")
}

/// A bare or backquoted name.
fn name(src: &str, start: usize) -> Result<(String, usize), (usize, String)> {
    let rest = &src[start..];
    if let Some(quoted) = rest.strip_prefix('`') {
        let mut text = String::new();
        let mut chars = quoted.char_indices();
        while let Some((n, c)) = chars.next() {
            match c {
                '`' => return Ok((text, start + 1 + n + 1)),
                '\\' => match chars.next() {
                    Some((_, c @ ('`' | '\\'))) => text.push(c),
                    _ => return Err((start + 1 + n, "bad escape in a name".into())),
                },
                '\n' => break,
                c => text.push(c),
            }
        }
        return Err((start, "unclosed name".into()));
    }
    let end = rest
        .find(|c: char| !(c.is_ascii_alphanumeric() || c == '_'))
        .unwrap_or(rest.len());
    if end == 0 {
        return Err((start, "expected a name".into()));
    }
    Ok((rest[..end].to_string(), start + end))
}

/// `#` and digits right after a name, if there.
fn id_after(src: &str, at: usize) -> Result<Option<(u32, usize)>, (usize, String)> {
    let Some(rest) = src[at..].strip_prefix('#') else {
        return Ok(None);
    };
    let digits = rest
        .find(|c: char| !c.is_ascii_digit())
        .unwrap_or(rest.len());
    if digits == 0 {
        return Ok(None);
    }
    let id = rest[..digits]
        .parse::<u32>()
        .ok()
        .filter(|&id| id != u32::MAX)
        .ok_or((at, "id out of range".to_string()))?;
    Ok(Some((id, at + 1 + digits)))
}

/// A number: `Int32`, `Int64` (`i64`), or `Float64`; or, for versions and
/// hashes, a word that starts with a digit.
fn number(src: &str, start: usize) -> Result<(Tok, usize), (usize, String)> {
    let bytes = src.as_bytes();
    let digits_from = |i: usize| {
        (i..bytes.len())
            .find(|&j| !bytes[j].is_ascii_digit())
            .unwrap_or(bytes.len())
    };
    let mut i = start + usize::from(bytes[start] == b'-');
    if src[i..].starts_with("inf") {
        return Ok((Tok::Float(f64::NEG_INFINITY), i + 3));
    }
    i = digits_from(i);
    let mut float = false;
    if bytes.get(i) == Some(&b'.') && bytes.get(i + 1).is_some_and(u8::is_ascii_digit) {
        float = true;
        i = digits_from(i + 1);
        if bytes.get(i) == Some(&b'.') {
            // A version, as `0.1.0-beta.2`.
            let end = (i..bytes.len())
                .find(|&j| !(bytes[j].is_ascii_alphanumeric() || b".-+".contains(&bytes[j])))
                .unwrap_or(bytes.len());
            return Ok((word(src, start, end), end));
        }
    }
    if matches!(bytes.get(i), Some(b'e' | b'E')) {
        let sign = usize::from(bytes.get(i + 1) == Some(&b'-'));
        if bytes.get(i + 1 + sign).is_some_and(u8::is_ascii_digit) {
            float = true;
            i = digits_from(i + 1 + sign);
        }
    }
    let i64 = !float && src[i..].starts_with("i64");
    let end = if i64 { i + 3 } else { i };
    if bytes
        .get(end)
        .is_some_and(|c| c.is_ascii_alphanumeric() || *c == b'_')
    {
        // A hash, say.
        let end = (end..bytes.len())
            .find(|&j| !(bytes[j].is_ascii_alphanumeric() || bytes[j] == b'_'))
            .unwrap_or(bytes.len());
        return Ok((word(src, start, end), end));
    }
    let text = &src[start..i];
    let tok = if float {
        Tok::Float(
            text.parse()
                .map_err(|_| (start, "bad number".to_string()))?,
        )
    } else {
        let value = text
            .parse()
            .map_err(|_| (start, "number out of range".to_string()))?;
        Tok::Int { value, i64 }
    };
    Ok((tok, end))
}

fn word(src: &str, start: usize, end: usize) -> Tok {
    Tok::Word {
        text: src[start..end].to_string(),
        quoted: false,
    }
}

fn string(src: &str, start: usize) -> Result<(String, usize), (usize, String)> {
    let mut text = String::new();
    let mut chars = src[start + 1..].char_indices();
    let at = |n: usize| start + 1 + n;
    while let Some((n, c)) = chars.next() {
        match c {
            '"' => return Ok((text, at(n) + 1)),
            '\\' => match chars.next() {
                Some((_, '"')) => text.push('"'),
                Some((_, '\\')) => text.push('\\'),
                Some((_, 'n')) => text.push('\n'),
                Some((_, 'r')) => text.push('\r'),
                Some((_, 't')) => text.push('\t'),
                Some((m, 'u')) => {
                    let rest = &src[at(m) + 1..];
                    let close = rest.find('}').filter(|_| rest.starts_with('{'));
                    let code = close
                        .and_then(|close| u32::from_str_radix(&rest[1..close], 16).ok())
                        .and_then(char::from_u32)
                        .ok_or((at(n), "bad \\u{…} escape".to_string()))?;
                    text.push(code);
                    for _ in 0..close.unwrap_or(0) + 1 {
                        chars.next();
                    }
                }
                _ => return Err((at(n), "bad escape in a string".into())),
            },
            '\n' => break,
            c => text.push(c),
        }
    }
    Err((start, "unclosed string".into()))
}

// Reading.

/// A module's items and init block, found by the first pass.
struct ModuleHead {
    decl: DeclId,
    items: Vec<Item>,
    init: usize,
    /// Each test's name and head.
    tests: Vec<(String, Head)>,
}

/// A function's or method's declaration, its parameters, and where its
/// body starts, if it has one.
struct Head {
    decl: DeclId,
    params: Vec<DeclId>,
    body: Option<usize>,
}

enum Item {
    Function(Head),
    ModuleLet(DeclId),
    /// A class or interface, and its members.
    Class(DeclId, Vec<Member>),
}

/// What a postfix expression reads as: a value, or an object and the
/// field that a statement sets on it.
enum Postfix {
    Value(NodeId),
    Field(NodeId, DeclId),
}

enum Member {
    Field(DeclId),
    StaticField(DeclId),
    /// A method, getter, setter, or constructor; abstract without a body.
    Method(Head),
    StaticFunction(Head),
}

/// What may precede a declaration: `qname "…"`, `keep`, `keepTest`,
/// `connected`, maybe with backends, `export`, and a member's `private`,
/// `protected`, `auto`, and `operator("…")`.
#[derive(Default)]
struct Marks {
    qname: Option<String>,
    keep: bool,
    keep_test: bool,
    connected: Option<Vec<String>>,
    export: bool,
    /// The member's marks, as facts.
    member: Facts,
}

struct Reader {
    src: String,
    tokens: Vec<Token>,
    pos: usize,
    b: Builder,
    /// Which declaration ids are defined so far.
    defined: Vec<bool>,
    /// Which were defined by the first scan, and are defined again where
    /// they're written.
    scanned: Vec<bool>,
    /// The function or module that owns locals and labels being read.
    owner: DeclId,
    /// In a method's body, its class's type, which `this` has unless
    /// written otherwise; `NONE` elsewhere.
    this: TypeId,
}

type Read<T> = Result<T, ReadError>;

impl Reader {
    // Token helpers.

    fn peek(&self) -> &Tok {
        &self.tokens[self.pos].tok
    }

    fn peek_at(&self, ahead: usize) -> &Tok {
        let i = (self.pos + ahead).min(self.tokens.len() - 1);
        &self.tokens[i].tok
    }

    fn next(&mut self) -> Tok {
        let tok = self.tokens[self.pos].tok.clone();
        if self.pos + 1 < self.tokens.len() {
            self.pos += 1;
        }
        tok
    }

    fn error<T>(&self, message: impl Into<String>) -> Read<T> {
        Err(error_at(
            &self.src,
            self.tokens[self.pos].start,
            message.into(),
        ))
    }

    fn is_punct(&self, p: &str) -> bool {
        matches!(self.peek(), Tok::Punct(q) if *q == p)
    }

    fn is_word(&self, w: &str) -> bool {
        is_keyword(self.peek(), w)
    }

    fn eat(&mut self, p: &str) -> bool {
        let found = self.is_punct(p);
        if found {
            self.next();
        }
        found
    }

    fn eat_word(&mut self, w: &str) -> bool {
        let found = self.is_word(w);
        if found {
            self.next();
        }
        found
    }

    fn expect(&mut self, p: &str) -> Read<()> {
        if self.eat(p) {
            Ok(())
        } else {
            self.error(format!("expected `{p}`"))
        }
    }

    fn expect_word(&mut self, w: &str) -> Read<()> {
        if self.eat_word(w) {
            Ok(())
        } else {
            self.error(format!("expected `{w}`"))
        }
    }

    /// A word with no id: a library or profile name, or a version.
    fn plain_word(&mut self, what: &str) -> Read<String> {
        match self.next() {
            Tok::Word { text, .. } => Ok(text),
            _ => {
                self.pos -= 1;
                self.error(format!("expected {what}"))
            }
        }
    }

    fn int(&mut self) -> Read<i128> {
        match *self.peek() {
            Tok::Int { value, i64: false } => {
                self.next();
                Ok(value)
            }
            _ => self.error("expected a number"),
        }
    }

    fn u32(&mut self) -> Read<u32> {
        let n = self.int()?;
        u32::try_from(n).or_else(|_| {
            self.pos -= 1;
            self.error("number out of range")
        })
    }

    /// Skips a `{ … }` group.
    fn skip_braces(&mut self) -> Read<()> {
        self.expect("{")?;
        let mut depth = 1;
        while depth > 0 {
            match self.next() {
                Tok::Punct("{") => depth += 1,
                Tok::Punct("}") => depth -= 1,
                Tok::End => return self.error("unclosed `{`"),
                _ => {}
            }
        }
        Ok(())
    }

    // Declarations.

    /// Defines the declaration at the current token.
    fn define(&mut self, kind: DeclKind, owner: DeclId, ty: TypeId) -> Read<DeclId> {
        let Tok::Decl { names, id, made } = self.peek().clone() else {
            return self.error("expected a declaration, as `name#id`");
        };
        let scanned = self.scanned.get_mut(id as usize).map(std::mem::take);
        if self.defined.get(id as usize) == Some(&true) && scanned != Some(true) {
            return self.error(format!("#{id} is defined twice"));
        }
        self.next();
        if self.defined.len() <= id as usize {
            self.defined.resize(id as usize + 1, false);
        }
        self.defined[id as usize] = true;
        let id = DeclId(id);
        let names: Vec<&str> = names.iter().map(String::as_str).collect();
        self.b.define(id, kind, &names, owner, ty);
        if made {
            self.b.add_facts(id, Facts::COMPILER_MADE);
        }
        Ok(id)
    }

    /// A use of a declaration, checked against its definition.
    fn reference(&mut self) -> Read<DeclId> {
        let Tok::Decl { names, id, made } = self.peek().clone() else {
            return self.error("expected a reference, as `name#id`");
        };
        if names.len() > 1 {
            return self.error("a reference names only the first hint");
        }
        if self.defined.get(id as usize) != Some(&true) {
            return self.error(format!("#{id} is used before it's defined"));
        }
        let decl = DeclId(id);
        let p = self.b.program();
        let made_there = p.decls.get(decl).facts.contains(Facts::COMPILER_MADE);
        if p.name(decl) != names[0] || made != made_there {
            let there = if made_there { "~" } else { "" };
            return self.error(format!(
                "#{id} is named {there}{}#{id}",
                super::write::name(p.name(decl))
            ));
        }
        self.next();
        Ok(decl)
    }

    fn ty(&mut self) -> Read<TypeId> {
        let ty = self.non_null_ty()?;
        if self.eat("?") {
            if self.b.program().types.is_nullable(ty) {
                self.pos -= 1;
                return self.error("a nullable type can't be nullable again");
            }
            return Ok(self.b.nullable(ty));
        }
        Ok(ty)
    }

    fn non_null_ty(&mut self) -> Read<TypeId> {
        if self.eat("(") {
            let ty = self.ty()?;
            self.expect(")")?;
            return Ok(ty);
        }
        if is_keyword(self.peek(), "fn") && *self.peek_at(1) == Tok::Punct("(") {
            return self.function_type();
        }
        match self.peek() {
            Tok::Word {
                text,
                quoted: false,
            } => {
                if let Some(ty) = TypeTable::scalar_named(text) {
                    self.next();
                    return Ok(ty);
                }
                if text == "Never" && *self.peek_at(1) == Tok::Punct("<") {
                    self.next();
                    let [of] = self.type_args()?[..] else {
                        return self.error("Never takes one type argument");
                    };
                    let types = &self.b.program().types;
                    if types.is_nullable(of) || types.is_never(of) {
                        return self.error("Never's type argument is neither nullable nor a Never");
                    }
                    return Ok(self.b.never(of));
                }
            }
            Tok::Decl { .. } => {
                let decl = self.reference()?;
                let d = self.b.program().decls.get(decl);
                if d.kind == DeclKind::TypeParam {
                    return Ok(d.ty);
                }
                if !matches!(d.kind, DeclKind::Class | DeclKind::Interface) {
                    self.pos -= 1;
                    return self.error(format!("a {} isn't a type", d.kind.name()));
                }
                if d.ty.is_none()
                    || TypeTable::scalar_named(self.b.program().name(decl)) == Some(d.ty)
                {
                    return Ok(d.ty);
                }
                let args = self.type_args()?;
                return Ok(self.b.applied(decl, &args));
            }
            _ => {}
        }
        self.error("expected a type")
    }

    /// `fn (A, _?: B): R`, maybe `throws Bubble`: a function type.
    fn function_type(&mut self) -> Read<TypeId> {
        self.next();
        self.expect("(")?;
        let (mut params, mut optional) = (Vec::new(), Vec::new());
        while !self.eat(")") {
            if !params.is_empty() {
                self.expect(",")?;
            }
            let opt = self.is_word("_") && *self.peek_at(1) == Tok::Punct("?");
            if opt {
                self.pos += 2;
                self.expect(":")?;
            }
            params.push(self.ty()?);
            optional.push(opt);
        }
        let (result, bubbles) = self.result()?;
        Ok(self
            .b
            .function_type_with(&params, &optional, result, bubbles))
    }

    /// `<A, B>`, if there: a call's or type's type arguments.
    fn type_args(&mut self) -> Read<Vec<TypeId>> {
        let mut args = Vec::new();
        if self.eat("<") {
            loop {
                args.push(self.ty()?);
                if self.eat(">") {
                    break;
                }
                self.expect(",")?;
            }
        }
        Ok(args)
    }

    /// `<T#1 extends B#2, U#3>`, if there: a generic declaration's type
    /// parameters, defined here, owned by `owner`.
    fn type_params(&mut self, owner: DeclId) -> Read<()> {
        if !self.eat("<") {
            return Ok(());
        }
        let mut params = Vec::new();
        let mut bounds = Vec::new();
        loop {
            let param = self.define(DeclKind::TypeParam, owner, TypeId::NONE)?;
            let ty = self.b.program_mut().types.type_param(param);
            self.b.program_mut().decls.get_mut(param).ty = ty;
            params.push(param);
            let mut these = Vec::new();
            if self.eat_word("extends") {
                these.push(self.at_bound()?);
                while self.eat("&") {
                    these.push(self.at_bound()?);
                }
            }
            bounds.push(these);
            if self.eat(">") {
                break;
            }
            self.expect(",")?;
        }
        self.b.set_type_params(owner, &params);
        for (param, these) in params.into_iter().zip(bounds) {
            self.b.set_bounds(param, &these);
        }
        Ok(())
    }

    /// A type parameter's bound, which may mention the type parameters
    /// before it, or itself.
    fn at_bound(&mut self) -> Read<TypeId> {
        self.ty()
    }

    /// Marks before a declaration.
    fn marks(&mut self) -> Read<Marks> {
        let mut marks = Marks::default();
        if self.eat_word("qname") {
            let Tok::Str(qname) = self.next() else {
                self.pos -= 1;
                return self.error("expected the qname, as a string");
            };
            marks.qname = Some(qname);
        }
        marks.keep = self.eat_word("keep");
        marks.keep_test = self.eat_word("keepTest");
        if self.eat_word("connected") {
            let mut backends = Vec::new();
            if self.eat("(") {
                while !self.eat(")") {
                    if !backends.is_empty() {
                        self.expect(",")?;
                    }
                    let Tok::Str(backend) = self.next() else {
                        self.pos -= 1;
                        return self.error("expected a backend's name, as a string");
                    };
                    backends.push(backend);
                }
            }
            marks.connected = Some(backends);
        }
        marks.export = self.eat_word("export");
        for (word, fact) in [
            ("private", Facts::PRIVATE),
            ("protected", Facts::PROTECTED),
            ("auto", Facts::AUTO),
            ("fun", Facts::FUN),
        ] {
            if self.eat_word(word) {
                marks.member = marks.member | fact;
            }
        }
        for (op, fact) in [("==", Facts::OPERATOR_EQ), ("<=>", Facts::OPERATOR_CMP)] {
            let here = is_keyword(self.peek(), "operator")
                && *self.peek_at(1) == Tok::Punct("(")
                && *self.peek_at(2) == Tok::Str(op.into());
            if here {
                self.pos += 3;
                self.expect(")")?;
                marks.member = marks.member | fact;
            }
        }
        if self.is_word("operator") {
            return self.error("expected `operator(\"==\")` or `operator(\"<=>\")`, in that order");
        }
        Ok(marks)
    }

    fn apply(&mut self, decl: DeclId, marks: &Marks) {
        if let Some(qname) = &marks.qname {
            self.b.set_qname(decl, qname);
        }
        if marks.keep {
            self.b.add_facts(decl, Facts::KEEP);
        }
        if marks.keep_test {
            self.b.add_facts(decl, Facts::KEEP_TEST);
        }
        if let Some(backends) = &marks.connected {
            let backends: Vec<&str> = backends.iter().map(String::as_str).collect();
            self.b.set_connected(decl, &backends);
        }
        if marks.export {
            self.b.add_facts(decl, Facts::EXPORTED);
        }
        self.b.add_facts(decl, marks.member);
    }

    /// Doc comment lines, joined.
    fn doc(&mut self) -> Option<String> {
        let mut doc = Vec::new();
        while let Tok::Doc(line) = self.peek() {
            doc.push(line.clone());
            self.next();
        }
        (!doc.is_empty()).then(|| doc.join("\n"))
    }

    // The scan: classes and interfaces.

    /// Defines every used library, and every class and interface, used or
    /// the program's own, so signatures may name them before they're
    /// written.
    fn scan(&mut self) -> Read<()> {
        let mut library = None;
        // Another library's module, and the depth of braces inside it.
        let mut module: Option<(DeclId, usize)> = None;
        // The program's module, by its id, defined later where it's read.
        let mut own = None;
        let mut depth = 0usize;
        let start = self.pos;
        while *self.peek() != Tok::End {
            let tok = self.next();
            match tok {
                Tok::Punct("{") => depth += 1,
                Tok::Punct("}") => {
                    depth = depth.saturating_sub(1);
                    if module.is_some_and(|(_, d)| d == depth) {
                        module = None;
                    }
                }
                _ => {}
            }
            if is_keyword(&tok, "module") && depth == 0 {
                library = None;
                own = match *self.peek() {
                    Tok::Decl { id, .. } => Some(DeclId(id)),
                    _ => None,
                };
                continue;
            }
            if is_keyword(&tok, "spans") && depth == 0 {
                own = None;
            }
            if is_keyword(&tok, "module") && depth == 1 && library.is_some() {
                if let Tok::Decl {
                    id, made: false, ..
                } = *self.peek()
                {
                    if self.defined.get(id as usize) != Some(&true) {
                        let owner = library.unwrap_or(DeclId::NONE);
                        let decl = self.define(DeclKind::Module, owner, TypeId::NONE)?;
                        if self.scanned.len() <= id as usize {
                            self.scanned.resize(id as usize + 1, false);
                        }
                        self.scanned[id as usize] = true;
                        module = Some((decl, depth));
                    }
                }
                continue;
            }
            let kind = if is_keyword(&tok, "uses") {
                DeclKind::Library
            } else if is_keyword(&tok, "class") {
                DeclKind::Class
            } else if is_keyword(&tok, "interface") {
                DeclKind::Interface
            } else {
                continue;
            };
            let Tok::Decl {
                id, made: false, ..
            } = *self.peek()
            else {
                continue;
            };
            let in_own = library.is_none() && own.is_some() && depth == 1;
            if self.defined.get(id as usize) == Some(&true)
                || (kind != DeclKind::Library && library.is_none() && !in_own)
            {
                continue;
            }
            let owner = match module {
                Some((m, _)) if kind != DeclKind::Library => m,
                _ if in_own => own.unwrap_or(DeclId::NONE),
                _ => library.unwrap_or(DeclId::NONE),
            };
            let decl = self.define(kind, owner, TypeId::NONE)?;
            if self.scanned.len() <= id as usize {
                self.scanned.resize(id as usize + 1, false);
            }
            self.scanned[id as usize] = true;
            match kind {
                DeclKind::Library => library = Some(decl),
                _ => self.b.give_type(decl),
            }
        }
        self.pos = start;
        Ok(())
    }

    // `uses` sections.

    fn uses(&mut self) -> Read<()> {
        let library = self.define(DeclKind::Library, DeclId::NONE, TypeId::NONE)?;
        let version = self.plain_word("the library's version")?;
        self.b.add_uses(library, &version);
        self.expect("{")?;
        while !self.eat("}") {
            self.external(library)?;
        }
        Ok(())
    }

    /// An external declaration, owned by a library or a type.
    fn external(&mut self, owner: DeclId) -> Read<()> {
        let doc = self.doc();
        let marks = self.marks()?;
        if marks.export || marks.qname.is_some() {
            return self
                .error("an external declaration's qname and export follow from its library");
        }
        let owner_kind = self.b.program().decls.get(owner).kind;
        let in_type = !matches!(owner_kind, DeclKind::Library | DeclKind::Module);
        let in_library = owner_kind == DeclKind::Library;
        let in_module = owner_kind == DeclKind::Module;
        // A static function is `static name#id(…)`, a static field
        // `static let name#id: T`.
        let is_static = in_type && self.eat_word("static");
        let is_let = self.is_word("let") || self.is_word("var");
        let kind = match self.peek() {
            _ if is_static && is_let => DeclKind::StaticField,
            _ if is_static => DeclKind::Function,
            t if is_keyword(t, "module") && in_library => DeclKind::Module,
            _ if is_let && in_module => DeclKind::ModuleLet,
            _ if is_let && in_type => DeclKind::Field,
            t if is_keyword(t, "class") && !in_type => DeclKind::Class,
            t if is_keyword(t, "interface") && !in_type => DeclKind::Interface,
            t if is_keyword(t, "fn") && !in_type => DeclKind::Function,
            t if is_keyword(t, "method") && in_type => DeclKind::Method,
            t if is_keyword(t, "get") && in_type => DeclKind::Getter,
            t if is_keyword(t, "set") && in_type => DeclKind::Setter,
            t if is_keyword(t, "constructor") && in_type => DeclKind::Constructor,
            _ if in_type => {
                return self.error(
                    "expected `method`, `get`, `set`, `constructor`, `static`, `let`, `var`, or `}`",
                );
            }
            _ if in_module => {
                return self.error("expected `class`, `interface`, `fn`, `let`, `var`, or `}`");
            }
            _ => return self.error("expected `module`, `class`, `interface`, `fn`, or `}`"),
        };
        let var = self.is_word("var");
        if !(is_static && kind == DeclKind::Function) {
            self.next();
        }
        let decl = self.define(kind, owner, TypeId::NONE)?;
        if kind == DeclKind::Module {
            self.expect("{")?;
            while !self.eat("}") {
                self.external(decl)?;
            }
            return Ok(());
        }
        self.apply(decl, &marks);
        if let Some(doc) = doc {
            self.b.set_doc(decl, &doc);
        }
        if is_let {
            if var {
                self.b.add_facts(decl, Facts::VAR);
            }
            self.expect(":")?;
            self.b.program_mut().decls.get_mut(decl).ty = self.ty()?;
            self.expect(";")?;
            return Ok(());
        }
        if matches!(kind, DeclKind::Class | DeclKind::Interface) {
            self.b.give_type(decl);
            self.type_params(decl)?;
            self.supers(decl)?;
            self.expect("{")?;
            while !self.eat("}") {
                self.external(decl)?;
            }
            return Ok(());
        }
        self.type_params(decl)?;
        self.expect("(")?;
        let (mut names, mut types, mut optional) = (Vec::new(), Vec::new(), Vec::new());
        while !self.eat(")") {
            if !names.is_empty() {
                self.expect(",")?;
            }
            names.push(self.plain_word("a parameter's name")?);
            optional.push(self.eat("?"));
            self.expect(":")?;
            types.push(self.ty()?);
        }
        let (result, bubbles) = self.result()?;
        self.expect(";")?;
        let sig = self
            .b
            .function_type_with(&types, &optional, result, bubbles);
        self.b.program_mut().decls.get_mut(decl).ty = sig;
        let names: Vec<&str> = names.iter().map(String::as_str).collect();
        self.b.set_param_names(decl, &names);
        Ok(())
    }

    /// `: Result`, and `throws Bubble` if it may bubble.
    fn result(&mut self) -> Read<(TypeId, bool)> {
        self.expect(":")?;
        let result = self.ty()?;
        let bubbles = self.eat_word("throws");
        if bubbles {
            self.expect_word("Bubble")?;
        }
        Ok((result, bubbles))
    }

    /// ` extends A#1, B#2`, if there, as a class's or interface's
    /// supertypes.
    fn supers(&mut self, decl: DeclId) -> Read<()> {
        let mut supers = Vec::new();
        if self.eat_word("extends") {
            supers.push(self.ty()?);
            while self.eat(",") {
                supers.push(self.ty()?);
            }
        }
        self.b.set_supers(decl, &supers);
        Ok(())
    }

    // The first pass: headers.

    fn headers(&mut self) -> Read<Vec<ModuleHead>> {
        self.expect_word("tmpl")?;
        let format = self.int()?;
        self.expect_word("model")?;
        let model = self.int()?;
        if format != FORMAT_VERSION as i128 || model != MODEL_VERSION as i128 {
            return self.error(format!(
                "this reads tmpl {FORMAT_VERSION} model {MODEL_VERSION} only"
            ));
        }
        self.expect_word("library")?;
        let name = self.plain_word("the library's name")?;
        let version = self.plain_word("the library's version")?;
        self.b = Builder::new(&name, &version);
        if self.eat_word("profile") {
            let profile = self.plain_word("a profile's name")?;
            self.b.set_profile(&profile);
        }
        self.scan()?;
        while self.eat_word("uses") {
            self.uses()?;
        }
        let mut modules = Vec::new();
        while self.eat_word("module") {
            modules.push(self.module_head()?);
        }
        if self.is_word("spans") {
            self.next();
            self.skip_braces()?;
        }
        if *self.peek() != Tok::End {
            let what = if modules.is_empty() {
                "`uses`, `module`, `spans`, or the end"
            } else {
                "`module`, `spans`, or the end"
            };
            return self.error(format!("expected {what}"));
        }
        Ok(modules)
    }

    fn module_head(&mut self) -> Read<ModuleHead> {
        let decl = self.define(DeclKind::Module, DeclId::NONE, TypeId::NONE)?;
        self.b.add_module(decl);
        if self.eat_word("in") {
            let Tok::Str(path) = self.next() else {
                self.pos -= 1;
                return self.error("expected the module's path, as a string");
            };
            self.b.set_module_path(decl, &path);
        }
        self.expect("{")?;
        let mut items = Vec::new();
        loop {
            let doc = self.doc();
            if doc.is_none() && self.eat_word("init") {
                break;
            }
            let marks = self.marks()?;
            let item = if self.eat_word("fn") {
                Item::Function(self.function_head(DeclKind::Function, decl)?)
            } else if self.is_word("let") || self.is_word("var") {
                Item::ModuleLet(self.let_head(DeclKind::ModuleLet, decl)?)
            } else if self.is_word("class") || self.is_word("interface") {
                self.class_head(decl)?
            } else {
                return self.error("expected `fn`, `let`, `var`, `class`, `interface`, or `init`");
            };
            let d = match item {
                Item::Function(Head { decl: d, .. }) | Item::ModuleLet(d) | Item::Class(d, _) => d,
            };
            self.apply(d, &marks);
            if let Some(doc) = doc {
                self.b.set_doc(d, &doc);
            }
            items.push(item);
        }
        let init = self.pos;
        self.skip_braces()?;
        let mut tests = Vec::new();
        while self.eat_word("test") {
            let Tok::Str(name) = self.next() else {
                self.pos -= 1;
                return self.error("expected the test's name, as a string");
            };
            let head = self.function_head(DeclKind::Function, decl)?;
            if head.body.is_none() {
                return self.error("expected the test's body");
            }
            tests.push((name, head));
        }
        self.expect("}")?;
        Ok(ModuleHead {
            decl,
            items,
            init,
            tests,
        })
    }

    /// `let name#id: T;`, or `var …`.
    fn let_head(&mut self, kind: DeclKind, owner: DeclId) -> Read<DeclId> {
        let var = self.is_word("var");
        self.next();
        let d = self.define(kind, owner, TypeId::NONE)?;
        self.expect(":")?;
        self.b.program_mut().decls.get_mut(d).ty = self.ty()?;
        self.expect(";")?;
        if var {
            self.b.add_facts(d, Facts::VAR);
        }
        Ok(d)
    }

    /// A class or interface, with its members' headers.
    fn class_head(&mut self, module: DeclId) -> Read<Item> {
        let kind = if self.eat_word("class") {
            DeclKind::Class
        } else {
            self.expect_word("interface")?;
            DeclKind::Interface
        };
        let decl = self.define(kind, module, TypeId::NONE)?;
        self.b.give_type(decl);
        self.type_params(decl)?;
        self.supers(decl)?;
        self.expect("{")?;
        let mut members = Vec::new();
        while !self.eat("}") {
            let doc = self.doc();
            let marks = self.marks()?;
            let member = self.member_head(decl)?;
            let d = match &member {
                Member::Field(d) | Member::StaticField(d) => *d,
                Member::Method(head) | Member::StaticFunction(head) => head.decl,
            };
            self.apply(d, &marks);
            if let Some(doc) = doc {
                self.b.set_doc(d, &doc);
            }
            members.push(member);
        }
        Ok(Item::Class(decl, members))
    }

    /// A member's header, after its marks.
    fn member_head(&mut self, class: DeclId) -> Read<Member> {
        let is_let = |r: &Self| r.is_word("let") || r.is_word("var");
        if self.eat_word("static") {
            if is_let(self) {
                return Ok(Member::StaticField(
                    self.let_head(DeclKind::StaticField, class)?,
                ));
            }
            self.expect_word("fn")?;
            return Ok(Member::StaticFunction(
                self.function_head(DeclKind::Function, class)?,
            ));
        }
        let is_abstract = self.eat_word("abstract");
        if !is_abstract && is_let(self) {
            return Ok(Member::Field(self.let_head(DeclKind::Field, class)?));
        }
        let kind = match self.peek() {
            t if is_keyword(t, "method") => DeclKind::Method,
            t if is_keyword(t, "get") => DeclKind::Getter,
            t if is_keyword(t, "set") => DeclKind::Setter,
            t if is_keyword(t, "constructor") => DeclKind::Constructor,
            _ if is_abstract => {
                return self.error("expected `method`, `get`, `set`, or `constructor`");
            }
            _ => {
                return self.error(
                    "expected `let`, `var`, `static`, `abstract`, `method`, `get`, `set`, `constructor`, or `}`",
                );
            }
        };
        self.next();
        let head = self.function_head(kind, class)?;
        match (is_abstract, head.body) {
            (true, Some(body)) => {
                self.pos = body;
                self.error("an abstract member has no body")
            }
            (false, None) => {
                self.pos -= 1;
                self.error("expected the member's body")
            }
            _ => Ok(Member::Method(head)),
        }
    }

    /// A function's or member's declaration, parameters, result, and
    /// where its body starts, or `;`.
    fn function_head(&mut self, kind: DeclKind, owner: DeclId) -> Read<Head> {
        let decl = self.define(kind, owner, TypeId::NONE)?;
        self.type_params(decl)?;
        let params = self.signature_of(decl)?;
        let body = if self.eat(";") {
            None
        } else {
            let body = self.pos;
            self.skip_braces()?;
            Some(body)
        };
        Ok(Head { decl, params, body })
    }

    /// `(fn ~fn#1(x#2: T): R [c#3] { … }): F`, after `(fn`: a function
    /// literal, its captures, its body, and its type.
    fn function_literal(&mut self, generator: bool) -> Read<NodeId> {
        let decl = self.define(DeclKind::Lambda, self.owner, TypeId::NONE)?;
        if generator {
            self.b.add_facts(decl, Facts::GENERATOR);
        }
        let params = self.signature_of(decl)?;
        let mut captures = Vec::new();
        if self.eat("[") {
            while !self.eat("]") {
                if !captures.is_empty() {
                    self.expect(",")?;
                }
                captures.push(self.reference()?);
            }
        }
        self.b.set_captures(decl, &captures);
        let owner = std::mem::replace(&mut self.owner, decl);
        let body = self.block();
        self.owner = owner;
        let body = body?;
        let params = self.params(&params);
        let ty = self.written_type()?;
        Ok(self.b.function_literal(ty, decl, &params, body))
    }

    /// A function's parameters, defined here and owned by it, and its
    /// result, which give its signature.
    fn signature_of(&mut self, decl: DeclId) -> Read<Vec<DeclId>> {
        self.expect("(")?;
        let mut params = Vec::new();
        let mut types = Vec::new();
        let mut optional = Vec::new();
        while !self.eat(")") {
            if !params.is_empty() {
                self.expect(",")?;
            }
            let marks = self.marks()?;
            if marks.export
                || marks.keep
                || marks.keep_test
                || marks.connected.is_some()
                || marks.member != Facts::NONE
            {
                return self.error("a parameter's only mark is `qname`");
            }
            let var = self.eat_word("var");
            let param = self.define(DeclKind::Param, decl, TypeId::NONE)?;
            self.apply(param, &marks);
            if var {
                self.b.add_facts(param, Facts::VAR);
            }
            let opt = self.eat("?");
            if opt {
                self.b.add_facts(param, Facts::OPTIONAL);
            }
            self.expect(":")?;
            let ty = self.ty()?;
            self.b.program_mut().decls.get_mut(param).ty = ty;
            params.push(param);
            types.push(ty);
            optional.push(opt);
        }
        let (result, bubbles) = self.result()?;
        let sig = self
            .b
            .function_type_with(&types, &optional, result, bubbles);
        self.b.program_mut().decls.get_mut(decl).ty = sig;
        Ok(params)
    }

    // The second pass: bodies.

    fn bodies(mut self, modules: Vec<ModuleHead>) -> Read<Program> {
        for module in modules {
            for item in module.items {
                let node = match item {
                    Item::Function(head) => self.function(head)?,
                    Item::ModuleLet(decl) => self.b.module_let(decl),
                    Item::Class(decl, members) => {
                        let ty = self.b.program().decls.get(decl).ty;
                        let mut nodes = Vec::with_capacity(members.len());
                        for member in members {
                            nodes.push(match member {
                                Member::Field(d) => self.b.field(d),
                                Member::StaticField(d) => self.b.static_field(d),
                                Member::StaticFunction(head) => self.function(head)?,
                                Member::Method(head) => {
                                    let params = self.params(&head.params);
                                    match head.body {
                                        Some(body) => {
                                            self.this = ty;
                                            let body = self.body(head.decl, body);
                                            self.this = TypeId::NONE;
                                            self.b.method(head.decl, &params, body?)
                                        }
                                        None => self.b.abstract_method(head.decl, &params),
                                    }
                                }
                            });
                        }
                        self.b.class(decl, &nodes)
                    }
                };
                self.b.add_item(module.decl, node);
            }
            self.pos = module.init;
            self.owner = module.decl;
            let init = self.block()?;
            self.b.set_init(module.decl, init);
            for (name, head) in module.tests {
                let params = self.params(&head.params);
                let body = self.body(head.decl, head.body.unwrap_or(0))?;
                let test = self.b.test(head.decl, &name, &params, body);
                self.b.add_test(module.decl, test);
            }
        }
        while !matches!(self.peek(), Tok::End) && !self.is_word("spans") {
            self.next();
        }
        if self.eat_word("spans") {
            self.spans()?;
        }
        Ok(self.b.finish())
    }

    /// A function, with its body if it has one.
    fn function(&mut self, head: Head) -> Read<NodeId> {
        let params = self.params(&head.params);
        Ok(match head.body {
            Some(body) => {
                let body = self.body(head.decl, body)?;
                self.b.function(head.decl, &params, body)
            }
            None => self.b.bodiless_function(head.decl, &params),
        })
    }

    fn params(&mut self, params: &[DeclId]) -> Vec<NodeId> {
        params.iter().map(|&d| self.b.param(d)).collect()
    }

    /// The body of `decl`, which starts at token `at`.
    fn body(&mut self, decl: DeclId, at: usize) -> Read<NodeId> {
        self.pos = at;
        self.owner = decl;
        self.block()
    }

    fn block(&mut self) -> Read<NodeId> {
        self.expect("{")?;
        self.scan_function_lets()?;
        let mut stmts = Vec::new();
        while !self.eat("}") {
            stmts.push(self.stmt()?);
        }
        Ok(self.b.block(&stmts))
    }

    /// Defines, ahead of their `let`s, the block's locals whose values are
    /// function literals, which a run of them may use before their `let`s
    /// (`analysis::function_groups`).
    fn scan_function_lets(&mut self) -> Read<()> {
        let start = self.pos;
        let mut depth = 0usize;
        loop {
            match self.peek().clone() {
                Tok::End => break,
                Tok::Punct("{") => depth += 1,
                Tok::Punct("}") if depth == 0 => break,
                Tok::Punct("}") => depth -= 1,
                _ if depth == 0 && self.is_word("let") => {
                    let at = self.pos;
                    self.next();
                    let decl = self.pos;
                    if let Tok::Decl { id, .. } = *self.peek() {
                        let fresh = self.defined.get(id as usize) != Some(&true);
                        if fresh && *self.peek_at(1) == Tok::Punct(":") {
                            self.pos += 2;
                            let ty = self.ty()?;
                            let literal = self.is_punct("=")
                                && *self.peek_at(1) == Tok::Punct("(")
                                && is_keyword(self.peek_at(2), "fn");
                            if literal {
                                self.pos = decl;
                                let local = self.define(DeclKind::Local, self.owner, ty)?;
                                self.b.program_mut().decls.get_mut(local).ty = ty;
                                if self.scanned.len() <= id as usize {
                                    self.scanned.resize(id as usize + 1, false);
                                }
                                self.scanned[id as usize] = true;
                            }
                        }
                    }
                    self.pos = at;
                }
                _ => {}
            }
            self.next();
        }
        self.pos = start;
        Ok(())
    }

    /// Whether a statement starts here, rather than an expression.
    fn at_stmt(&self) -> bool {
        let keyword = [
            "qname", "let", "var", "init", "if", "switch", "break", "continue", "return", "yield",
        ]
        .iter()
        .any(|w| self.is_word(w));
        keyword
            || self.is_punct("{")
            || (matches!(self.peek(), Tok::Decl { .. })
                && matches!(self.peek_at(1), Tok::Punct(":" | "=")))
    }

    fn stmt(&mut self) -> Read<NodeId> {
        let qname = if self.eat_word("qname") {
            let Tok::Str(qname) = self.next() else {
                self.pos -= 1;
                return self.error("expected the qname, as a string");
            };
            if !(self.is_word("let") || self.is_word("var")) {
                return self.error("expected `let` or `var` after a qname");
            }
            Some(qname)
        } else {
            None
        };
        if self.is_word("let") || self.is_word("var") {
            let var = self.is_word("var");
            self.next();
            let local = self.define(DeclKind::Local, self.owner, TypeId::NONE)?;
            if let Some(qname) = qname {
                self.b.set_qname(local, &qname);
            }
            if var {
                self.b.add_facts(local, Facts::VAR);
            }
            self.expect(":")?;
            self.b.program_mut().decls.get_mut(local).ty = self.ty()?;
            let init = if self.eat("=") {
                Some(self.expr()?)
            } else {
                None
            };
            self.expect(";")?;
            return Ok(self.b.let_stmt(local, init));
        }
        if self.eat_word("init") {
            let target = self.reference()?;
            self.expect("=")?;
            let value = self.expr()?;
            self.expect(";")?;
            let is_static = self.b.program().decls.get(target).kind == DeclKind::StaticField;
            return Ok(if is_static {
                self.b.init_static(target, value)
            } else {
                self.b.init_let(target, value)
            });
        }
        if self.is_word("if") {
            return self.if_stmt();
        }
        if self.eat_word("switch") {
            return self.switch_stmt();
        }
        if self.is_word("break") || self.is_word("continue") {
            let is_break = self.is_word("break");
            self.next();
            let label = self.reference()?;
            self.expect(";")?;
            return Ok(if is_break {
                self.b.break_loop(label)
            } else {
                self.b.continue_loop(label)
            });
        }
        if self.eat_word("yield") {
            let value = if self.is_punct(";") {
                None
            } else {
                Some(self.expr()?)
            };
            self.expect(";")?;
            return Ok(self.b.yield_stmt(value));
        }
        if self.eat_word("return") {
            let value = if self.is_punct(";") {
                None
            } else {
                Some(self.expr()?)
            };
            self.expect(";")?;
            return Ok(self.b.return_stmt(value));
        }
        if self.is_punct("{") {
            return self.block();
        }
        if matches!(self.peek(), Tok::Decl { .. }) && *self.peek_at(1) == Tok::Punct(":") {
            return self.labeled_loop();
        }
        if matches!(self.peek(), Tok::Decl { .. }) && *self.peek_at(1) == Tok::Punct("=") {
            let target = self.reference()?;
            self.next();
            let value = self.expr()?;
            self.expect(";")?;
            let module_let = self.b.program().decls.get(target).kind == DeclKind::ModuleLet;
            return Ok(if module_let {
                self.b.set_module_let(target, value)
            } else {
                self.b.assign(target, value)
            });
        }
        if matches!(self.peek(), Tok::Doc(_)) {
            return self.error("a doc comment goes only before a module's item");
        }
        match self.postfix_or_field()? {
            Postfix::Field(object, field) => self.set_field(object, field),
            Postfix::Value(first) => {
                let expr = self.expr_after(first)?;
                self.expect(";")?;
                Ok(self.b.expr_statement(expr))
            }
        }
    }

    /// `= value;`, after `object.field`.
    fn set_field(&mut self, object: NodeId, field: DeclId) -> Read<NodeId> {
        self.expect("=")?;
        let value = self.expr()?;
        self.expect(";")?;
        Ok(self.b.set_field(field, object, value))
    }

    fn if_stmt(&mut self) -> Read<NodeId> {
        self.expect_word("if")?;
        self.expect("(")?;
        let cond = self.expr()?;
        self.expect(")")?;
        let then = self.block()?;
        let els = if self.eat_word("else") {
            if self.is_word("if") {
                let inner = self.if_stmt()?;
                Some(self.b.block(&[inner]))
            } else {
                Some(self.block()?)
            }
        } else {
            None
        };
        Ok(self.b.if_stmt(cond, then, els))
    }

    /// `switch (x) { case 0, 1 { … } default { … } }`, after `switch`.
    fn switch_stmt(&mut self) -> Read<NodeId> {
        self.expect("(")?;
        let scrutinee = self.expr()?;
        self.expect(")")?;
        self.expect("{")?;
        let mut cases = Vec::new();
        while !self.eat("}") {
            let mut labels = Vec::new();
            if !self.eat_word("default") {
                if !self.eat_word("case") {
                    return self.error("expected `case`, `default`, or `}`");
                }
                labels.push(self.expr()?);
                while self.eat(",") {
                    labels.push(self.expr()?);
                }
            }
            let body = self.block()?;
            cases.push(self.b.case(&labels, body));
        }
        Ok(self.b.switch_stmt(scrutinee, &cases))
    }

    fn labeled_loop(&mut self) -> Read<NodeId> {
        let label = self.define(DeclKind::Label, self.owner, TypeId::NONE)?;
        self.expect(":")?;
        if self.eat_word("while") {
            self.expect("(")?;
            let cond = self.expr()?;
            self.expect(")")?;
            let body = self.block()?;
            Ok(self.b.while_loop(label, cond, body))
        } else if self.eat_word("do") {
            let body = self.block()?;
            self.expect_word("while")?;
            self.expect("(")?;
            let cond = self.expr()?;
            self.expect(")")?;
            self.expect(";")?;
            Ok(self.b.do_while(label, body, cond))
        } else {
            self.error("expected `while` or `do` after a label")
        }
    }

    fn expr(&mut self) -> Read<NodeId> {
        let first = self.postfix()?;
        self.expr_after(first)
    }

    /// The rest of an expression whose first operand is read.
    fn expr_after(&mut self, first: NodeId) -> Read<NodeId> {
        let mut left = self.and_after(first)?;
        while self.eat("||") {
            let right = self.postfix()?;
            let right = self.and_after(right)?;
            left = self.b.or(left, right);
        }
        Ok(left)
    }

    fn and_after(&mut self, first: NodeId) -> Read<NodeId> {
        let mut left = first;
        while self.eat("&&") {
            let right = self.postfix()?;
            left = self.b.and(left, right);
        }
        Ok(left)
    }

    fn postfix(&mut self) -> Read<NodeId> {
        match self.postfix_or_field()? {
            Postfix::Value(value) => Ok(value),
            Postfix::Field(..) => self.error("a field is set only by a statement"),
        }
    }

    /// A primary, then any method calls and field reads on it; or, if a
    /// field is followed by `=`, the object and field it sets.
    fn postfix_or_field(&mut self) -> Read<Postfix> {
        let mut receiver = self.primary()?;
        loop {
            if self.is_punct("(") {
                let args = self.args(DeclId::NONE, None, &[])?;
                receiver = self.b.call_value(receiver, &args);
                self.caret(receiver)?;
                continue;
            }
            if !self.eat(".") {
                break;
            }
            let member = self.reference()?;
            let kind = self.b.program().decls.get(member).kind;
            match kind {
                DeclKind::Field if self.is_punct("=") => {
                    return Ok(Postfix::Field(receiver, member));
                }
                DeclKind::Field => receiver = self.b.get_field(member, receiver),
                DeclKind::Method | DeclKind::Getter | DeclKind::Setter => {
                    let type_args = self.type_args()?;
                    let args = self.args(member, Some(receiver), &type_args)?;
                    receiver = self
                        .b
                        .call_method_generic(member, &type_args, receiver, &args);
                    self.caret(receiver)?;
                }
                DeclKind::StaticField => {
                    self.pos -= 1;
                    return self.error("a static field is read without a receiver");
                }
                _ => {
                    self.pos -= 1;
                    return self.error(format!("a {} isn't called on a receiver", kind.name()));
                }
            }
        }
        Ok(Postfix::Value(receiver))
    }

    fn primary(&mut self) -> Read<NodeId> {
        match self.peek().clone() {
            Tok::Int { value, i64: false } => match i32::try_from(value) {
                Ok(value) => {
                    self.next();
                    Ok(self.b.int32(value))
                }
                Err(_) => self.error("Int32 out of range"),
            },
            Tok::Int { value, i64: true } => match i64::try_from(value) {
                Ok(value) => {
                    self.next();
                    Ok(self.b.int64(value))
                }
                Err(_) => self.error("Int64 out of range"),
            },
            Tok::Float(value) => {
                self.next();
                Ok(self.b.float64(value))
            }
            Tok::Str(text) => {
                self.next();
                Ok(self.b.string(&text))
            }
            Tok::Word {
                text,
                quoted: false,
            } => {
                let node = match text.as_str() {
                    "true" => self.b.bool_lit(true),
                    "false" => self.b.bool_lit(false),
                    "void" => self.b.void(),
                    "nan" => self.b.float64(f64::NAN),
                    "inf" => self.b.float64(f64::INFINITY),
                    "this" if self.this.is_none() => {
                        return self
                            .error("`this` outside a method needs its type, as `(this): T`");
                    }
                    "this" => self.b.this(self.this),
                    "new" => return self.new_object(),
                    "typeValue" => {
                        self.next();
                        self.expect("(")?;
                        let of = self.ty()?;
                        self.expect(")")?;
                        return Ok(self.b.type_value(of));
                    }
                    "is" => {
                        self.next();
                        self.expect("(")?;
                        let value = self.expr()?;
                        self.expect(",")?;
                        let ty = self.ty()?;
                        self.expect(")")?;
                        return Ok(self.b.is(ty, value));
                    }
                    "identical" => {
                        self.next();
                        self.expect("(")?;
                        let left = self.expr()?;
                        self.expect(",")?;
                        let right = self.expr()?;
                        self.expect(")")?;
                        return Ok(self.b.identical(left, right));
                    }
                    "isNull" => {
                        self.next();
                        self.expect("(")?;
                        let value = self.expr()?;
                        self.expect(")")?;
                        return Ok(self.b.is_null(value));
                    }
                    "eqNullable" => {
                        self.next();
                        self.expect("(")?;
                        let Tok::Op(name) = self.peek().clone() else {
                            return self.error("expected an operator, as `%String.eq`");
                        };
                        let Some(op) = Op::named(&name) else {
                            return self.error(format!("no operator %{name}"));
                        };
                        self.next();
                        self.expect(",")?;
                        let left = self.expr()?;
                        self.expect(",")?;
                        let right = self.expr()?;
                        self.expect(")")?;
                        return Ok(self.b.eq_nullable(op, left, right));
                    }
                    "omitted" => return self.error("`omitted` goes only where an argument does"),
                    _ => return self.error("expected an expression"),
                };
                self.next();
                Ok(node)
            }
            Tok::Op(name) => {
                let Some(op) = Op::named(&name) else {
                    return self.error(format!("no operator %{name}"));
                };
                self.next();
                let args = self.args(DeclId::NONE, None, &[])?;
                let call = self.b.op(op, &args);
                self.caret(call)?;
                Ok(call)
            }
            Tok::Decl { .. } => {
                let decl = self.reference()?;
                let kind = self.b.program().decls.get(decl).kind;
                let value_kind = matches!(
                    kind,
                    DeclKind::Local | DeclKind::Param | DeclKind::ModuleLet | DeclKind::StaticField
                );
                if (self.is_punct("(") && !value_kind)
                    || (self.is_punct("<") && kind == DeclKind::Function)
                {
                    let type_args = self.type_args()?;
                    if kind != DeclKind::Function {
                        self.pos -= 1;
                        return self.error(if kind == DeclKind::Constructor {
                            "a constructor is called with `new`"
                        } else {
                            "only a function can be called"
                        });
                    }
                    let args = self.args(decl, None, &type_args)?;
                    let call = self.b.call_generic(decl, &type_args, &args);
                    self.caret(call)?;
                    return Ok(call);
                }
                match kind {
                    DeclKind::Local | DeclKind::Param => Ok(self.b.local(decl)),
                    DeclKind::ModuleLet => Ok(self.b.get_module_let(decl)),
                    DeclKind::StaticField => Ok(self.b.get_static(decl)),
                    _ => {
                        self.pos -= 1;
                        self.error(format!("a {} isn't a value", kind.name()))
                    }
                }
            }
            Tok::Punct("(") => self.parenthesized(),
            _ => self.error("expected an expression"),
        }
    }

    /// `new ctor#id(args)`.
    fn new_object(&mut self) -> Read<NodeId> {
        self.expect_word("new")?;
        let ctor = self.reference()?;
        let kind = self.b.program().decls.get(ctor).kind;
        if kind != DeclKind::Constructor {
            self.pos -= 1;
            return self.error(format!("`new` calls a constructor, not a {}", kind.name()));
        }
        let type_args = self.type_args()?;
        let args = self.args(ctor, None, &type_args)?;
        let new = self.b.new_generic(ctor, &type_args, &args);
        self.caret(new)?;
        Ok(new)
    }

    /// A call's arguments; `omitted` takes the type of the callee's
    /// parameter in its place, with the type parameters the call binds
    /// put in: its type arguments, and the receiver's.
    fn args(
        &mut self,
        callee: DeclId,
        receiver: Option<NodeId>,
        type_args: &[TypeId],
    ) -> Read<Vec<NodeId>> {
        self.expect("(")?;
        let mut args = Vec::new();
        while !self.eat(")") {
            if !args.is_empty() {
                self.expect(",")?;
            }
            if self.eat_word("omitted") {
                let p = self.b.program();
                let params = if callee.is_none() {
                    None
                } else {
                    p.signature(callee)
                };
                let ty = params
                    .and_then(|sig| sig.params.get(args.len()).copied())
                    .unwrap_or(TypeId::NONE);
                let ty = if ty.is_none() {
                    ty
                } else {
                    let receiver = receiver.map(|r| p.nodes.ty(r));
                    let (from, to) = crate::analysis::bindings(p, callee, receiver, type_args);
                    self.b.program_mut().types.substitute(ty, &from, &to)
                };
                args.push(self.b.omitted(ty));
                continue;
            }
            args.push(self.expr()?);
        }
        Ok(args)
    }

    /// Checks that `^` follows exactly the calls that may bubble.
    fn caret(&mut self, call: NodeId) -> Read<()> {
        let marked = self.eat("^");
        let bubbles = self
            .b
            .program()
            .nodes
            .flags(call)
            .contains(Flags::MAY_BUBBLE);
        match (marked, bubbles) {
            (true, false) => {
                self.pos -= 1;
                self.error("`^` marks a call that can't bubble")
            }
            (false, true) => self.error("a call that may bubble needs `^`"),
            _ => Ok(()),
        }
    }

    /// `(e)`, `(e): T`, or a form whose type is always written.
    fn parenthesized(&mut self) -> Read<NodeId> {
        self.expect("(")?;
        if self.eat_word("if") {
            self.expect("(")?;
            let cond = self.expr()?;
            self.expect(")")?;
            let then = self.expr()?;
            self.expect_word("else")?;
            let els = self.expr()?;
            let ty = self.written_type()?;
            return Ok(self.b.if_value(ty, cond, then, els));
        }
        if self.eat_word("value") {
            self.expect("{")?;
            let mut stmts = Vec::new();
            let value = loop {
                if self.at_stmt() {
                    stmts.push(self.stmt()?);
                    continue;
                }
                let expr = match self.postfix_or_field()? {
                    Postfix::Field(object, field) => {
                        stmts.push(self.set_field(object, field)?);
                        continue;
                    }
                    Postfix::Value(first) => self.expr_after(first)?,
                };
                if self.eat(";") {
                    stmts.push(self.b.expr_statement(expr));
                } else {
                    self.expect("}")?;
                    break expr;
                }
            };
            let ty = self.written_type()?;
            return Ok(self.b.block_value(ty, &stmts, value));
        }
        if self.eat_word("error") {
            self.expect("(")?;
            let Tok::Str(message) = self.next() else {
                self.pos -= 1;
                return self.error("expected the error's message");
            };
            let value = if self.eat(",") {
                Some(self.expr()?)
            } else {
                None
            };
            self.expect(")")?;
            let ty = self.written_type()?;
            return Ok(match value {
                Some(value) => self.b.type_error(ty, &message, value),
                None => self.b.error(ty, &message),
            });
        }
        if self.is_word("this") && *self.peek_at(1) == Tok::Punct(")") {
            // `this` with its type written, as it is outside a method or
            // where it isn't the method's class.
            self.next();
            let ty = self.written_type()?;
            return Ok(self.b.this(ty));
        }
        if self.is_word("fn") && matches!(self.peek_at(1), Tok::Decl { .. }) {
            self.next();
            return self.function_literal(false);
        }
        if self.is_word("fn") && is_keyword(self.peek_at(1), "generator") {
            self.pos += 2;
            return self.function_literal(true);
        }
        if self.is_word("await") && *self.peek_at(1) == Tok::Punct("(") {
            self.pos += 2;
            let promise = self.expr()?;
            self.expect(")")?;
            let flags = if self.eat("^") {
                Flags::MAY_BUBBLE
            } else {
                Flags::NONE
            };
            let ty = self.written_type()?;
            return Ok(self.b.raw(Kind::Await, ty, (0, 0), flags, &[promise]));
        }
        if self.is_word("ref") && *self.peek_at(1) == Tok::Punct("(") {
            self.pos += 2;
            let target = self.reference()?;
            let kind = self.b.program().decls.get(target).kind;
            if kind != DeclKind::Function {
                self.pos -= 1;
                return self.error(format!("`ref` names a function, not a {}", kind.name()));
            }
            self.expect(")")?;
            let ty = self.written_type()?;
            return Ok(self.b.function_ref(ty, target));
        }
        for word in ["toFun", "fromFun"] {
            if !(self.is_word(word) && *self.peek_at(1) == Tok::Punct("(")) {
                continue;
            }
            self.pos += 2;
            let value = self.expr()?;
            self.expect(")")?;
            let ty = self.written_type()?;
            return Ok(if word == "toFun" {
                self.b.to_fun(ty, value)
            } else {
                self.b.from_fun(ty, value)
            });
        }
        if self.is_word("list") && *self.peek_at(1) == Tok::Punct("(") {
            self.pos += 2;
            let mut items = Vec::new();
            while !self.eat(")") {
                if !items.is_empty() {
                    self.expect(",")?;
                }
                items.push(self.expr()?);
            }
            let ty = self.written_type()?;
            return Ok(self.b.list_lit(ty, &items));
        }
        if self.is_word("null") && *self.peek_at(1) == Tok::Punct(")") {
            self.next();
            let ty = self.written_type()?;
            return Ok(self.b.null(ty));
        }
        for word in ["bubble", "panic"] {
            if !(self.is_word(word)
                && *self.peek_at(1) == Tok::Punct("(")
                && *self.peek_at(2) == Tok::Punct(")"))
            {
                continue;
            }
            self.pos += 3;
            let flags = if self.eat("^") {
                Flags::MAY_BUBBLE
            } else {
                Flags::NONE
            };
            let ty = self.written_type()?;
            let kind = if word == "bubble" {
                Kind::Bubble
            } else {
                Kind::Panic
            };
            return Ok(self.b.raw(kind, ty, (0, 0), flags, &[]));
        }
        if self.is_word("coalesce") && *self.peek_at(1) == Tok::Punct("(") {
            self.pos += 2;
            let left = self.expr()?;
            self.expect(",")?;
            let right = self.expr()?;
            self.expect(")")?;
            let ty = self.written_type()?;
            return Ok(self
                .b
                .raw(Kind::Coalesce, ty, (0, 0), Flags::NONE, &[left, right]));
        }
        if self.is_word("orelse") && *self.peek_at(1) == Tok::Punct("(") {
            self.pos += 2;
            let body = self.expr()?;
            self.expect(",")?;
            let fallback = self.expr()?;
            self.expect(")")?;
            let ty = self.written_type()?;
            return Ok(self.b.or_else(ty, body, fallback));
        }
        for word in ["nonNull", "asNullable"] {
            if !(self.is_word(word) && *self.peek_at(1) == Tok::Punct("(")) {
                continue;
            }
            self.pos += 2;
            let value = self.expr()?;
            self.expect(")")?;
            let ty = self.written_type()?;
            let kind = if word == "nonNull" {
                Kind::NonNull
            } else {
                Kind::AsNullable
            };
            return Ok(self.b.raw(kind, ty, (0, 0), Flags::NONE, &[value]));
        }
        for word in ["upcast", "downcast", "as"] {
            if !(self.is_word(word) && *self.peek_at(1) == Tok::Punct("(")) {
                continue;
            }
            self.next();
            self.next();
            let value = self.expr()?;
            self.expect(")")?;
            let flags = if word == "as" && self.eat("^") {
                Flags::MAY_BUBBLE
            } else {
                Flags::NONE
            };
            let ty = self.written_type()?;
            return Ok(match word {
                "upcast" => self.b.upcast(ty, value),
                "downcast" => self.b.downcast(ty, value),
                _ => self.b.raw(Kind::As, ty, (0, 0), flags, &[value]),
            });
        }
        let expr = self.expr()?;
        self.expect(")")?;
        if self.eat(":") {
            let at = self.pos;
            let ty = self.ty()?;
            let p = self.b.program();
            let rule = p.nodes.ty(expr);
            if ty != rule {
                self.pos = at;
                let rule = p.types.show(rule);
                return self.error(format!("the type here is {rule}, by the local rules"));
            }
        }
        Ok(expr)
    }

    /// `): T`, closing a form whose type is written.
    fn written_type(&mut self) -> Read<TypeId> {
        self.expect(")")?;
        self.expect(":")?;
        self.ty()
    }

    fn spans(&mut self) -> Read<()> {
        self.b.program_mut().has_spans = true;
        self.expect("{")?;
        while self.eat_word("file") {
            let id = self.u32()?;
            if id as usize != self.b.program().files.len() {
                return self.error("files are numbered in order from 0");
            }
            let Tok::Str(path) = self.next() else {
                self.pos -= 1;
                return self.error("expected the file's path");
            };
            let hash = self.hash()?;
            self.b.add_file(&path, hash);
            self.expect(";")?;
        }
        let order = writing_order(self.b.program());
        loop {
            if self.eat("#") {
                let id = self.u32()?;
                if self.defined.get(id as usize) != Some(&true) {
                    return self.error(format!("no declaration #{id}"));
                }
                let span = self.maybe_span()?;
                let name_span = if self.eat_word("name") {
                    self.span()?
                } else {
                    Span::UNKNOWN
                };
                self.b.set_decl_spans(DeclId(id), span, name_span);
            } else if self.eat("@") {
                let n = self.u32()?;
                let Some(&node) = order.get(n as usize) else {
                    return self.error(format!("no node @{n}"));
                };
                let span = self.maybe_span()?;
                let derived = self.eat("~");
                self.b.set_span(node, span, derived);
            } else {
                break;
            }
            self.expect(";")?;
        }
        self.expect("}")
    }

    fn hash(&mut self) -> Read<[u8; 16]> {
        let token = &self.tokens[self.pos];
        let text = &self.src[token.start..token.end];
        let mut hash = [0; 16];
        let valid = text.len() == 32
            && (0..16).all(|i| match u8::from_str_radix(&text[2 * i..2 * i + 2], 16) {
                Ok(byte) => {
                    hash[i] = byte;
                    true
                }
                Err(_) => false,
            });
        if !valid {
            return self.error("expected a hash of 32 hex digits");
        }
        self.next();
        Ok(hash)
    }

    fn maybe_span(&mut self) -> Read<Span> {
        if matches!(self.peek(), Tok::Int { .. }) {
            self.span()
        } else {
            Ok(Span::UNKNOWN)
        }
    }

    /// `file:start-end`.
    fn span(&mut self) -> Read<Span> {
        let file = self.u32()?;
        if file as usize >= self.b.program().files.len() {
            self.pos -= 1;
            return self.error(format!("no file {file}"));
        }
        self.expect(":")?;
        let start = self.u32()?;
        self.expect("-")?;
        let end = self.u32()?;
        Ok(Span {
            file: FileId(file),
            start,
            end,
        })
    }
}

fn is_keyword(tok: &Tok, w: &str) -> bool {
    matches!(tok, Tok::Word { text, quoted: false } if text == w)
}

#[cfg(test)]
mod tests {
    use super::{Tok, lex};

    fn tokens(src: &str) -> Vec<Tok> {
        let mut tokens: Vec<Tok> = lex(src).unwrap().into_iter().map(|t| t.tok).collect();
        tokens.pop(); // End
        tokens
    }

    fn word(text: &str) -> Tok {
        Tok::Word {
            text: text.into(),
            quoted: false,
        }
    }

    #[test]
    fn numbers_versions_and_hashes() {
        assert_eq!(
            tokens("1 -2 3i64 1.5 -1.0e-3 -inf 0.1.0-beta.2 00ff 0:10-20"),
            [
                Tok::Int {
                    value: 1,
                    i64: false
                },
                Tok::Int {
                    value: -2,
                    i64: false
                },
                Tok::Int {
                    value: 3,
                    i64: true
                },
                Tok::Float(1.5),
                Tok::Float(-1.0e-3),
                Tok::Float(f64::NEG_INFINITY),
                word("0.1.0-beta.2"),
                word("00ff"),
                Tok::Int {
                    value: 0,
                    i64: false
                },
                Tok::Punct(":"),
                Tok::Int {
                    value: 10,
                    i64: false
                },
                Tok::Punct("-"),
                Tok::Int {
                    value: 20,
                    i64: false
                },
            ]
        );
    }

    #[test]
    fn names_and_ops() {
        let decl = |names: &[&str], id, made| Tok::Decl {
            names: names.iter().map(|n| n.to_string()).collect(),
            id,
            made,
        };
        assert_eq!(
            tokens("x#1 `a b`#2 ~t|u#3 %Int32.add fn `fn` /// doc\n// skipped\n~"),
            [
                decl(&["x"], 1, false),
                decl(&["a b"], 2, false),
                decl(&["t", "u"], 3, true),
                Tok::Op("Int32.add".into()),
                word("fn"),
                Tok::Word {
                    text: "fn".into(),
                    quoted: true
                },
                Tok::Doc("doc".into()),
                Tok::Punct("~"),
            ]
        );
    }
}
