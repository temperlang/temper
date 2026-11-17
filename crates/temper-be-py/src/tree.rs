//! Python's output tree and its printer.
//!
//! Each backend has its own tree and printer, as their layouts share
//! little (notes/design.md, section 9). The printer adds parentheses only where Python's precedence needs
//! them, and lays out code as PEP 8 does: four-space indents, and two
//! blank lines around top-level functions.

use std::collections::BTreeSet;
use std::fmt::Write;

/// A binary operator.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Bin {
    Or,
    And,
    Eq,
    Ne,
    Lt,
    Le,
    Gt,
    Ge,
    BitOr,
    BitXor,
    BitAnd,
    Shl,
    Shr,
    Add,
    Sub,
    Mul,
    Div,
    Mod,
    Pow,
    Is,
    IsNot,
}

impl Bin {
    fn text(self) -> &'static str {
        match self {
            Bin::Or => "or",
            Bin::And => "and",
            Bin::Eq => "==",
            Bin::Ne => "!=",
            Bin::Lt => "<",
            Bin::Le => "<=",
            Bin::Gt => ">",
            Bin::Ge => ">=",
            Bin::BitOr => "|",
            Bin::BitXor => "^",
            Bin::BitAnd => "&",
            Bin::Shl => "<<",
            Bin::Shr => ">>",
            Bin::Add => "+",
            Bin::Sub => "-",
            Bin::Mul => "*",
            Bin::Div => "/",
            Bin::Mod => "%",
            Bin::Pow => "**",
            Bin::Is => "is",
            Bin::IsNot => "is not",
        }
    }

    fn prec(self) -> u8 {
        match self {
            Bin::Or => OR,
            Bin::And => AND,
            Bin::Eq | Bin::Ne | Bin::Lt | Bin::Le | Bin::Gt | Bin::Ge | Bin::Is | Bin::IsNot => {
                COMPARE
            }
            Bin::BitOr => 6,
            Bin::BitXor => 7,
            Bin::BitAnd => 8,
            Bin::Shl | Bin::Shr => 9,
            Bin::Add | Bin::Sub => 10,
            Bin::Mul | Bin::Div | Bin::Mod => 11,
            Bin::Pow => POW,
        }
    }
}

/// A unary operator.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Unary {
    Not,
    Neg,
    Invert,
}

// Precedences, loosest first, as Python's grammar has them.
const TOP: u8 = 0;
const IF_ELSE: u8 = 1;
const OR: u8 = 2;
const AND: u8 = 3;
const NOT: u8 = 4;
const COMPARE: u8 = 5;
const UNARY: u8 = 12;
const POW: u8 = 13;
const PRIMARY: u8 = 15;
const ATOM: u8 = 16;

/// A part of an f-string.
#[derive(Clone, Debug, PartialEq)]
pub enum FPart {
    Lit(String),
    /// An expression; `str` says it needs `str(…)` if the f-string falls
    /// back to `+`, since its value isn't a string (as `{n}` for an int).
    Expr {
        expr: Expr,
        str: bool,
    },
}

#[derive(Clone, Debug, PartialEq)]
pub enum Expr {
    Name(String),
    /// A builtin, as `len`: bare, unless the function it's in hides it
    /// by a name of its own, which [`qualify_builtins`] turns into
    /// `builtins.len`.
    Builtin(&'static str),
    /// A type's annotation where an expression goes, as is (`Box[int]`,
    /// or quoted, `'list[Any]'`): its builtins' names are reached as
    /// [`Expr::Builtin`]'s are.
    Type(String),
    Int(i64),
    Float(f64),
    Str(String),
    Bool(bool),
    None,
    Call(Box<Expr>, Vec<Expr>),
    Attr(Box<Expr>, String),
    Bin(Bin, Box<Expr>, Box<Expr>),
    Unary(Unary, Box<Expr>),
    /// `then if cond else els`, written in that order.
    IfElse {
        cond: Box<Expr>,
        then: Box<Expr>,
        els: Box<Expr>,
    },
    /// An f-string, or where its parts can't go in one in Python 3.11
    /// (quotes or backslashes inside the braces), a `+` chain.
    FString(Vec<FPart>),
    /// `(a, b)`, or `(a,)`.
    Tuple(Vec<Expr>),
    /// `...`.
    Ellipsis,
    /// `(name := value)`, always parenthesized.
    Walrus(String, Box<Expr>),
    /// `obj[index]`.
    Index(Box<Expr>, Box<Expr>),
    /// `obj[begin : end]`, spaced as Kotlin's.
    Slice(Box<Expr>, Box<Expr>, Box<Expr>),
    /// `[a, b]`.
    List(Vec<Expr>),
    /// `{}`, an empty dict.
    EmptyDict,
    /// `lambda a, b: body`.
    Lambda(Vec<String>, Box<Expr>),
    /// `yield value`, or a bare `yield`: in a generator's `def`.
    Yield(Option<Box<Expr>>),
    /// An expression mypy reports by this error code where the code is
    /// right, as an abstract class passed as a `type[T]`
    /// (`type-abstract`): the line it's on ends with `# type:
    /// ignore[code]`.
    Ignored(Box<Expr>, &'static str),
}

impl Expr {
    pub fn name(name: impl Into<String>) -> Expr {
        Expr::Name(name.into())
    }

    pub fn builtin(name: &'static str) -> Expr {
        Expr::Builtin(name)
    }

    pub fn call(self, args: Vec<Expr>) -> Expr {
        Expr::Call(Box::new(self), args)
    }

    pub fn attr(self, name: impl Into<String>) -> Expr {
        Expr::Attr(Box::new(self), name.into())
    }

    pub fn bin(op: Bin, left: Expr, right: Expr) -> Expr {
        Expr::Bin(op, Box::new(left), Box::new(right))
    }

    pub fn unary(op: Unary, operand: Expr) -> Expr {
        Expr::Unary(op, Box::new(operand))
    }

    fn prec(&self) -> u8 {
        match self {
            Expr::Int(n) if *n < 0 => UNARY,
            Expr::Float(f) if f.is_sign_negative() && f.is_finite() => UNARY,
            Expr::Float(f) if !f.is_finite() => PRIMARY,
            Expr::Name(_)
            | Expr::Builtin(_)
            | Expr::Type(_)
            | Expr::Int(_)
            | Expr::Float(_)
            | Expr::Str(_)
            | Expr::Bool(_)
            | Expr::None
            | Expr::FString(_)
            | Expr::Tuple(_)
            | Expr::Ellipsis
            | Expr::Walrus(..)
            | Expr::List(_)
            | Expr::EmptyDict => ATOM,
            Expr::Call(..) | Expr::Attr(..) | Expr::Index(..) | Expr::Slice(..) => PRIMARY,
            Expr::Bin(op, ..) => op.prec(),
            Expr::Unary(Unary::Not, _) => NOT,
            Expr::Unary(..) => UNARY,
            Expr::IfElse { .. } => IF_ELSE,
            Expr::Yield(_) => ATOM,
            Expr::Lambda(..) => TOP,
            Expr::Ignored(e, _) => e.prec(),
        }
    }
}

/// Marks where an [`Expr::Ignored`] is, until [`module`] moves its code
/// to the end of the line.
const IGNORED: char = '\u{1}';

/// Each line with [`Expr::Ignored`]'s marks, ending with `# type:
/// ignore[…]` for their codes, before any other comment there.
fn ignores_at_ends(text: String) -> String {
    if !text.contains(IGNORED) {
        return text;
    }
    let mut out = String::new();
    for line in text.split_inclusive('\n') {
        let (line, end) = line.strip_suffix('\n').map_or((line, ""), |l| (l, "\n"));
        let mut parts = line.split(IGNORED);
        let mut code = parts.next().unwrap_or("").to_string();
        let mut codes = Vec::new();
        while let (Some(c), Some(rest)) = (parts.next(), parts.next()) {
            if !codes.contains(&c) {
                codes.push(c);
            }
            code.push_str(rest);
        }
        if codes.is_empty() {
            out.push_str(&code);
        } else {
            let ignore = format!("# type: ignore[{}]", codes.join(", "));
            match code.split_once("  # ") {
                Some((code, comment)) => out.push_str(&format!("{code}  {ignore}  # {comment}")),
                None => out.push_str(&format!("{code}  {ignore}")),
            }
        }
        out.push_str(end);
    }
    out
}

#[derive(Clone, Debug, PartialEq)]
pub enum Stmt {
    Expr(Expr),
    /// `target: ann = value`, or without the annotation; the target is a
    /// name or an attribute.
    Assign {
        target: Expr,
        ann: Option<String>,
        value: Expr,
    },
    /// `target: ann`, declared without a value.
    Declare {
        target: String,
        ann: String,
    },
    If {
        cond: Expr,
        then: Vec<Stmt>,
        els: Vec<Stmt>,
    },
    While {
        cond: Expr,
        body: Vec<Stmt>,
    },
    Break,
    Continue,
    Return(Option<Expr>),
    Global(Vec<String>),
    /// `nonlocal a, b`: what a nested function assigns of the functions'
    /// around it.
    Nonlocal(Vec<String>),
    /// A function, after its decorators (`property`, without the `@`);
    /// its first `positional` parameters are positional-only, as
    /// Kotlin's are (all but a constructor's). A parameter with an empty
    /// annotation has none, as a method's `self`.
    Def {
        decorators: Vec<String>,
        name: String,
        params: Vec<(String, String)>,
        positional: usize,
        result: String,
        body: Vec<Stmt>,
    },
    /// A statement with a comment after its first line: `# pyright:
    /// ignore[…]`, where a checker's rule doesn't fit the code.
    Commented(Box<Stmt>, String),
    /// `try: …`, then `except Exception: …` if `except` is given (how a
    /// bubble is caught, as Kotlin's), then `finally: …` if it isn't
    /// empty.
    Try {
        body: Vec<Stmt>,
        except: Option<Vec<Stmt>>,
        finally: Vec<Stmt>,
    },
    /// `raise value`.
    Raise(Expr),
    /// `assert cond, message`, or without the message.
    Assert(Expr, Option<Expr>),
    /// `with context as name: …`, or without `as`: how a jump leaves or
    /// continues a loop around the innermost one, with the runtime's
    /// `Label` or `LabelPair`, as Kotlin's.
    With {
        context: Expr,
        name: Option<String>,
        body: Vec<Stmt>,
    },
    /// `class Name(Base, …): …`, or `class Name: …` without bases; a
    /// base may be a keyword argument, as `metaclass=ABCMeta`. Its body
    /// has a blank line before and after each method.
    Class {
        name: String,
        bases: Vec<String>,
        body: Vec<Stmt>,
    },
}

/// One `.py` file.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Module {
    /// `import MODULE as ALIAS`, in order.
    pub modules: Vec<(String, String)>,
    /// `from MODULE import NAME, …`, in order.
    pub imports: Vec<(String, Vec<String>)>,
    /// Host code, as a module's `__connected__.py`, printed as it is
    /// after the imports.
    pub host: Option<String>,
    /// `__all__`, the names it exports, in order; `None` for a tests
    /// file.
    pub exports: Option<Vec<String>>,
    pub body: Vec<Stmt>,
}

/// Prints a string literal, preferring single quotes, as Python's `repr`.
pub fn string_literal(text: &str) -> String {
    let quote = if text.contains('\'') && !text.contains('"') {
        '"'
    } else {
        '\''
    };
    let mut out = String::new();
    out.push(quote);
    escape_into(&mut out, text, quote, false);
    out.push(quote);
    out
}

fn escape_into(out: &mut String, text: &str, quote: char, braces: bool) {
    for c in text.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '{' if braces => out.push_str("{{"),
            '}' if braces => out.push_str("}}"),
            _ if c == quote => {
                out.push('\\');
                out.push(c);
            }
            _ if (c as u32) < 0x20 || (0x7f..0xa0).contains(&(c as u32)) => {
                let _ = write!(out, "\\x{:02x}", c as u32);
            }
            '\u{ad}'
            | '\u{200b}'..='\u{200f}'
            | '\u{2028}'..='\u{202e}'
            | '\u{2060}'..='\u{206f}'
            | '\u{feff}'
            | '\u{fff9}'..='\u{fffb}' => {
                let _ = write!(out, "\\u{:04x}", c as u32);
            }
            _ => out.push(c),
        }
    }
}

fn float_literal(f: f64) -> String {
    if f.is_nan() {
        "float('nan')".into()
    } else if f.is_infinite() {
        if f > 0.0 {
            "float('inf')".into()
        } else {
            "float('-inf')".into()
        }
    } else {
        format!("{f:?}")
    }
}

/// Prints an expression, parenthesized if it binds looser than `min`.
pub fn expr(e: &Expr, min: u8) -> String {
    let text = match e {
        Expr::Name(n) | Expr::Type(n) => n.clone(),
        Expr::Builtin(n) => n.to_string(),
        Expr::Int(n) => n.to_string(),
        Expr::Float(f) => float_literal(*f),
        Expr::Str(s) => string_literal(s),
        Expr::Bool(true) => "True".into(),
        Expr::Bool(false) => "False".into(),
        Expr::None => "None".into(),
        Expr::Call(f, args) => {
            let args: Vec<String> = args.iter().map(|a| expr(a, TOP)).collect();
            format!("{}({})", expr(f, PRIMARY), args.join(", "))
        }
        Expr::Attr(obj, name) => format!("{}.{name}", expr(obj, PRIMARY)),
        Expr::Bin(op, left, right) => {
            let p = op.prec();
            let (lmin, rmin) = match op {
                Bin::Pow => (p + 1, UNARY),
                _ if p == COMPARE => (p + 1, p + 1),
                _ => (p, p + 1),
            };
            format!("{} {} {}", expr(left, lmin), op.text(), expr(right, rmin))
        }
        Expr::Unary(Unary::Not, operand) => format!("not {}", expr(operand, NOT)),
        Expr::Unary(Unary::Neg, operand) => format!("-{}", expr(operand, UNARY)),
        Expr::Unary(Unary::Invert, operand) => format!("~{}", expr(operand, UNARY)),
        Expr::IfElse { cond, then, els } => format!(
            "{} if {} else {}",
            expr(then, IF_ELSE + 1),
            expr(cond, IF_ELSE + 1),
            expr(els, IF_ELSE)
        ),
        Expr::FString(parts) => fstring(parts, min),
        Expr::Tuple(items) => match items.as_slice() {
            [one] => format!("({},)", expr(one, IF_ELSE)),
            _ => {
                let items: Vec<String> = items.iter().map(|i| expr(i, IF_ELSE)).collect();
                format!("({})", items.join(", "))
            }
        },
        Expr::Ellipsis => "...".into(),
        Expr::Walrus(name, value) => format!("({name} := {})", expr(value, IF_ELSE)),
        Expr::Ignored(e, code) => format!("{}{IGNORED}{code}{IGNORED}", expr(e, TOP)),
        Expr::Index(obj, index) => format!("{}[{}]", expr(obj, PRIMARY), expr(index, IF_ELSE)),
        Expr::Slice(obj, begin, end) => format!(
            "{}[{} : {}]",
            expr(obj, PRIMARY),
            expr(begin, IF_ELSE),
            expr(end, IF_ELSE)
        ),
        Expr::List(items) => {
            let items: Vec<String> = items.iter().map(|i| expr(i, IF_ELSE)).collect();
            format!("[{}]", items.join(", "))
        }
        Expr::EmptyDict => "{}".into(),
        // Parenthesized but where a statement or an assignment's value is
        // one ([`bare_yield`]), as Python wants.
        Expr::Yield(None) => "(yield)".into(),
        Expr::Yield(Some(value)) => format!("(yield {})", expr(value, IF_ELSE)),
        Expr::Lambda(params, body) if params.is_empty() => format!("lambda: {}", expr(body, TOP)),
        Expr::Lambda(params, body) => {
            format!("lambda {}: {}", params.join(", "), expr(body, TOP))
        }
    };
    if e.prec() < min {
        format!("({text})")
    } else {
        text
    }
}

/// An f-string, or a `+` chain where Python 3.11 can't take the parts in
/// braces.
fn fstring(parts: &[FPart], min: u8) -> String {
    let exprs: Vec<String> = parts
        .iter()
        .map(|p| match p {
            FPart::Lit(_) => String::new(),
            FPart::Expr { expr: e, .. } => expr(e, IF_ELSE),
        })
        .collect();
    if let Some(q) = fstring_quote(parts, &exprs) {
        let mut out = format!("f{q}");
        for (part, e) in parts.iter().zip(&exprs) {
            match part {
                FPart::Lit(s) => escape_into(&mut out, s, q, true),
                FPart::Expr { .. } => {
                    out.push('{');
                    out.push_str(e);
                    out.push('}');
                }
            }
        }
        out.push(q);
        return out;
    }
    expr(&plus_chain(parts), min)
}

/// An f-string's parts as a `+` chain, each part that isn't a string in
/// `str(…)`.
fn plus_chain(parts: &[FPart]) -> Expr {
    let terms: Vec<Expr> = parts
        .iter()
        .map(|p| match p {
            FPart::Lit(s) => Expr::Str(s.clone()),
            FPart::Expr { expr: e, str: true } => Expr::builtin("str").call(vec![e.clone()]),
            FPart::Expr {
                expr: e,
                str: false,
            } => e.clone(),
        })
        .collect();
    terms
        .into_iter()
        .reduce(|a, b| Expr::bin(Bin::Add, a, b))
        .unwrap_or(Expr::Str(String::new()))
}

/// The quote an f-string of these parts, its expressions printed as
/// `exprs`, takes: `None` where Python 3.11 can't take them in braces.
fn fstring_quote(parts: &[FPart], exprs: &[String]) -> Option<char> {
    let lits: String = parts
        .iter()
        .filter_map(|p| match p {
            FPart::Lit(s) => Some(s.as_str()),
            FPart::Expr { .. } => None,
        })
        .collect();
    let inner = |q: char| {
        exprs
            .iter()
            .all(|e| !e.contains([q, '\\', '{', '}', '\n']) && !e.starts_with('='))
    };
    let preferred = if lits.contains('\'') && !lits.contains('"') {
        '"'
    } else {
        '\''
    };
    let other = if preferred == '\'' { '"' } else { '\'' };
    [preferred, other].into_iter().find(|&q| inner(q))
}

/// Marks each module-level function or class whose name starts with `_`
/// and that nothing else in the module names, which pyright's strict
/// mode reports as unused: a private one the module doesn't use itself.
pub fn mark_unused(body: &mut [Stmt]) {
    let texts: Vec<String> = body
        .iter()
        .map(|s| {
            let mut out = String::new();
            stmt(&mut out, s, 0);
            out
        })
        .collect();
    for i in 0..body.len() {
        let (name, rule) = match &body[i] {
            Stmt::Def { name, .. } => (name, "reportUnusedFunction"),
            Stmt::Class { name, .. } => (name, "reportUnusedClass"),
            _ => continue,
        };
        if !name.starts_with('_') {
            continue;
        }
        let own = texts[i].split_once('\n').map_or("", |(_, rest)| rest);
        let used = (texts.iter().enumerate())
            .filter(|&(j, _)| j != i)
            .map(|(_, t)| t.as_str())
            .chain([own])
            .any(|t| names_in(t).any(|n| n == name));
        if !used {
            let comment = format!("pyright: ignore[{rule}]");
            let s = std::mem::replace(&mut body[i], Stmt::Break);
            body[i] = Stmt::Commented(Box::new(s), comment);
        }
    }
}

/// The identifiers in some code (and words in its strings).
fn names_in(text: &str) -> impl Iterator<Item = &str> {
    text.split(|c: char| !(c == '_' || c.is_alphanumeric()))
        .filter(|w| !w.is_empty())
}

/// Prints a module.
pub fn module(m: &Module) -> String {
    let mut out = String::new();
    for (module, alias) in &m.modules {
        match module == alias {
            true => writeln!(out, "import {module}"),
            false => writeln!(out, "import {module} as {alias}"),
        }
        .unwrap_or_default();
    }
    for (from, names) in &m.imports {
        let line = format!("from {from} import {}", names.join(", "));
        if line.len() <= 79 {
            let _ = writeln!(out, "{line}");
        } else {
            let _ = writeln!(out, "from {from} import (");
            for name in names {
                let _ = writeln!(out, "    {name},");
            }
            let _ = writeln!(out, ")");
        }
    }
    if let Some(exports) = &m.exports {
        if !out.is_empty() {
            out.push('\n');
        }
        let quoted: Vec<String> = exports.iter().map(|e| string_literal(e)).collect();
        let line = match quoted.as_slice() {
            [one] => format!("__all__ = ({one},)"),
            _ => format!("__all__ = ({})", quoted.join(", ")),
        };
        if line.len() <= 79 {
            let _ = writeln!(out, "{line}");
        } else {
            let _ = writeln!(out, "__all__ = (");
            for name in &quoted {
                let _ = writeln!(out, "    {name},");
            }
            let _ = writeln!(out, ")");
        }
    }
    if let Some(host) = &m.host {
        if !out.is_empty() {
            out.push_str("\n\n");
        }
        out.push_str(host.trim_end());
        out.push('\n');
        if !m.body.is_empty() {
            out.push_str("\n\n");
        }
    }
    let mut previous_def = None;
    for (i, s) in m.body.iter().enumerate() {
        let is_def = match s {
            Stmt::Commented(s, _) => matches!(**s, Stmt::Def { .. } | Stmt::Class { .. }),
            _ => matches!(s, Stmt::Def { .. } | Stmt::Class { .. }),
        };
        if i == 0 {
            if (!m.imports.is_empty() || !m.modules.is_empty() || m.exports.is_some())
                && m.host.is_none()
            {
                out.push_str(if is_def { "\n\n" } else { "\n" });
            }
        } else if is_def || previous_def == Some(true) {
            out.push_str("\n\n");
        }
        stmt(&mut out, s, 0);
        previous_def = Some(is_def);
    }
    ignores_at_ends(out)
}

/// Reaches the builtins that a function's own names hide through the
/// `builtins` module, imported as `alias`: in each function and lambda, an
/// [`Expr::Builtin`], or a name in an annotation or an [`Expr::Type`],
/// that the function or one around it binds (a parameter, a local, a
/// nested `def`), as Python decides locality per function, becomes
/// `alias.name` (`_builtins.len(items)` beside a parameter `len`), as
/// does the printer's `float('inf')`;
/// elsewhere builtins stay bare. `builtins` lists the builtins module's
/// names. A class body adds nothing it binds, as its members avoid the
/// names it prints (`names::CLASS_BODY`), and decorators stay as they are:
/// `property` and `staticmethod` in class bodies, and a property's own
/// name, `map.setter`. Whether any was reached so.
pub fn qualify_builtins(body: &mut [Stmt], builtins: &[&str], alias: &str) -> bool {
    let mut q = Qualify {
        builtins,
        alias,
        used: false,
    };
    let hidden = q.hidden(&BTreeSet::new(), &[], body, None);
    q.stmts(body, &hidden);
    q.used
}

struct Qualify<'a> {
    builtins: &'a [&'a str],
    alias: &'a str,
    used: bool,
}

impl Qualify<'_> {
    /// `outer`'s names, with the builtins' names a function binds: its
    /// parameters, and what its body or a lambda's value binds outside
    /// nested functions.
    fn hidden(
        &self,
        outer: &BTreeSet<String>,
        params: &[&str],
        body: &[Stmt],
        value: Option<&Expr>,
    ) -> BTreeSet<String> {
        let mut bound: Vec<String> = params.iter().map(|p| p.to_string()).collect();
        for s in body {
            bound_by_stmt(s, &mut bound);
        }
        if let Some(e) = value {
            bound_by_expr(e, &mut bound);
        }
        let mut hidden = outer.clone();
        hidden.extend((bound.into_iter()).filter(|n| self.builtins.contains(&n.as_str())));
        hidden
    }

    fn stmts(&mut self, body: &mut [Stmt], hidden: &BTreeSet<String>) {
        for s in body {
            self.stmt(s, hidden);
        }
    }

    fn stmt(&mut self, s: &mut Stmt, hidden: &BTreeSet<String>) {
        match s {
            Stmt::Expr(e) | Stmt::Raise(e) | Stmt::Return(Some(e)) => self.expr(e, hidden),
            Stmt::Assign { target, ann, value } => {
                self.expr(target, hidden);
                if let Some(ann) = ann {
                    self.ann(ann, hidden);
                }
                self.expr(value, hidden);
            }
            Stmt::Declare { ann, .. } => self.ann(ann, hidden),
            Stmt::If { cond, then, els } => {
                self.expr(cond, hidden);
                self.stmts(then, hidden);
                self.stmts(els, hidden);
            }
            Stmt::While { cond, body } => {
                self.expr(cond, hidden);
                self.stmts(body, hidden);
            }
            Stmt::Def {
                params,
                result,
                body,
                ..
            } => {
                // Annotations, as decorators, are evaluated where the
                // `def` is, its body in its own scope.
                for (_, ann) in params.iter_mut() {
                    self.ann(ann, hidden);
                }
                self.ann(result, hidden);
                let names: Vec<&str> = params.iter().map(|(n, _)| n.as_str()).collect();
                let inner = self.hidden(hidden, &names, body, None);
                self.stmts(body, &inner);
            }
            Stmt::Commented(s, _) => self.stmt(s, hidden),
            Stmt::Try {
                body,
                except,
                finally,
            } => {
                self.stmts(body, hidden);
                if let Some(except) = except {
                    self.stmts(except, hidden);
                }
                self.stmts(finally, hidden);
            }
            Stmt::Assert(cond, message) => {
                self.expr(cond, hidden);
                if let Some(m) = message {
                    self.expr(m, hidden);
                }
            }
            Stmt::With { context, body, .. } => {
                self.expr(context, hidden);
                self.stmts(body, hidden);
            }
            Stmt::Class { bases, body, .. } => {
                for base in bases.iter_mut() {
                    self.ann(base, hidden);
                }
                self.stmts(body, hidden);
            }
            Stmt::Return(None)
            | Stmt::Break
            | Stmt::Continue
            | Stmt::Global(_)
            | Stmt::Nonlocal(_) => {}
        }
    }

    fn expr(&mut self, e: &mut Expr, hidden: &BTreeSet<String>) {
        match e {
            Expr::Builtin(name) if hidden.contains(*name) => {
                self.used = true;
                *e = Expr::name(self.alias).attr(*name);
            }
            Expr::Type(text) => self.ann(text, hidden),
            // `float('inf')`, which the printer writes.
            Expr::Float(f) if !f.is_finite() && hidden.contains("float") => {
                self.used = true;
                let text = float_literal(*f);
                let arg = text.trim_start_matches("float('").trim_end_matches("')");
                *e = (Expr::name(self.alias).attr("float")).call(vec![Expr::Str(arg.into())]);
            }
            Expr::Lambda(params, body) => {
                let names: Vec<&str> = params.iter().map(String::as_str).collect();
                let inner = self.hidden(hidden, &names, &[], Some(body));
                self.expr(body, &inner);
            }
            // Where `str` is hidden and the parts can't go in an f-string,
            // the `+` chain the printer would make, with `str` reached.
            Expr::FString(parts) => {
                for part in parts.iter_mut() {
                    if let FPart::Expr { expr: e, .. } = part {
                        self.expr(e, hidden);
                    }
                }
                let needs_str = parts
                    .iter()
                    .any(|p| matches!(p, FPart::Expr { str: true, .. }));
                if needs_str && hidden.contains("str") {
                    let exprs: Vec<String> = (parts.iter())
                        .map(|p| match p {
                            FPart::Lit(_) => String::new(),
                            FPart::Expr { expr: e, .. } => expr(e, IF_ELSE),
                        })
                        .collect();
                    if fstring_quote(parts, &exprs).is_none() {
                        let mut chain = plus_chain(parts);
                        self.expr(&mut chain, hidden);
                        *e = chain;
                    }
                }
            }
            Expr::Call(f, args) => {
                self.expr(f, hidden);
                for a in args {
                    self.expr(a, hidden);
                }
            }
            Expr::Attr(obj, _) | Expr::Unary(_, obj) | Expr::Walrus(_, obj) => {
                self.expr(obj, hidden)
            }
            Expr::Ignored(inner, _) => self.expr(inner, hidden),
            Expr::Yield(Some(value)) => self.expr(value, hidden),
            Expr::Bin(_, left, right) | Expr::Index(left, right) => {
                self.expr(left, hidden);
                self.expr(right, hidden);
            }
            Expr::IfElse { cond, then, els } => {
                self.expr(cond, hidden);
                self.expr(then, hidden);
                self.expr(els, hidden);
            }
            Expr::Slice(obj, begin, end) => {
                self.expr(obj, hidden);
                self.expr(begin, hidden);
                self.expr(end, hidden);
            }
            Expr::Tuple(items) | Expr::List(items) => {
                for i in items {
                    self.expr(i, hidden);
                }
            }
            Expr::Name(_)
            | Expr::Builtin(_)
            | Expr::Int(_)
            | Expr::Float(_)
            | Expr::Str(_)
            | Expr::Bool(_)
            | Expr::None
            | Expr::Ellipsis
            | Expr::EmptyDict
            | Expr::Yield(None) => {}
        }
    }

    /// An annotation's names that `hidden` has, but after a `.`, reached
    /// through the alias: they're types, never a function's own names.
    fn ann(&mut self, text: &mut String, hidden: &BTreeSet<String>) {
        if hidden.is_empty() {
            return;
        }
        let mut out = String::new();
        let mut word = String::new();
        let mut after_dot = false;
        let mut flush = |word: &mut String, out: &mut String, after_dot: bool| {
            if !after_dot && hidden.contains(word.as_str()) {
                self.used = true;
                out.push_str(self.alias);
                out.push('.');
            }
            out.push_str(word);
            word.clear();
        };
        for c in text.chars() {
            if c == '_' || c.is_alphanumeric() {
                word.push(c);
                continue;
            }
            if !word.is_empty() {
                flush(&mut word, &mut out, after_dot);
            }
            after_dot = c == '.';
            out.push(c);
        }
        if !word.is_empty() {
            flush(&mut word, &mut out, after_dot);
        }
        *text = out;
    }
}

/// What a statement binds in its function, but in nested functions and
/// classes: assigned and declared names, `with … as` names, walruses,
/// and a nested `def`'s or class's own name.
fn bound_by_stmt(s: &Stmt, bound: &mut Vec<String>) {
    match s {
        Stmt::Expr(e) | Stmt::Raise(e) | Stmt::Return(Some(e)) => bound_by_expr(e, bound),
        Stmt::Assign { target, value, .. } => {
            if let Expr::Name(n) = target {
                bound.push(n.clone());
            }
            bound_by_expr(target, bound);
            bound_by_expr(value, bound);
        }
        Stmt::Declare { target, .. } => bound.push(target.clone()),
        Stmt::If { cond, then, els } => {
            bound_by_expr(cond, bound);
            for s in then.iter().chain(els) {
                bound_by_stmt(s, bound);
            }
        }
        Stmt::While { cond, body } => {
            bound_by_expr(cond, bound);
            for s in body {
                bound_by_stmt(s, bound);
            }
        }
        Stmt::Def { name, .. } | Stmt::Class { name, .. } => bound.push(name.clone()),
        Stmt::Commented(s, _) => bound_by_stmt(s, bound),
        Stmt::Try {
            body,
            except,
            finally,
        } => {
            let except = except.iter().flatten();
            for s in body.iter().chain(except).chain(finally) {
                bound_by_stmt(s, bound);
            }
        }
        Stmt::Assert(cond, message) => {
            bound_by_expr(cond, bound);
            if let Some(m) = message {
                bound_by_expr(m, bound);
            }
        }
        Stmt::With {
            context,
            name,
            body,
        } => {
            bound_by_expr(context, bound);
            bound.extend(name.iter().cloned());
            for s in body {
                bound_by_stmt(s, bound);
            }
        }
        Stmt::Return(None) | Stmt::Break | Stmt::Continue | Stmt::Global(_) | Stmt::Nonlocal(_) => {
        }
    }
}

/// The walruses' names in an expression, but in lambdas.
fn bound_by_expr(e: &Expr, bound: &mut Vec<String>) {
    match e {
        Expr::Walrus(name, value) => {
            bound.push(name.clone());
            bound_by_expr(value, bound);
        }
        Expr::Lambda(..) => {}
        Expr::Call(f, args) => {
            bound_by_expr(f, bound);
            for a in args {
                bound_by_expr(a, bound);
            }
        }
        Expr::Attr(obj, _) | Expr::Unary(_, obj) | Expr::Ignored(obj, _) => {
            bound_by_expr(obj, bound)
        }
        Expr::Yield(Some(value)) => bound_by_expr(value, bound),
        Expr::Bin(_, left, right) | Expr::Index(left, right) => {
            bound_by_expr(left, bound);
            bound_by_expr(right, bound);
        }
        Expr::IfElse { cond, then, els } => {
            for e in [cond, then, els] {
                bound_by_expr(e, bound);
            }
        }
        Expr::Slice(obj, begin, end) => {
            for e in [obj, begin, end] {
                bound_by_expr(e, bound);
            }
        }
        Expr::Tuple(items) | Expr::List(items) => {
            for i in items {
                bound_by_expr(i, bound);
            }
        }
        Expr::FString(parts) => {
            for part in parts {
                if let FPart::Expr { expr: e, .. } = part {
                    bound_by_expr(e, bound);
                }
            }
        }
        Expr::Name(_)
        | Expr::Builtin(_)
        | Expr::Type(_)
        | Expr::Int(_)
        | Expr::Float(_)
        | Expr::Str(_)
        | Expr::Bool(_)
        | Expr::None
        | Expr::Ellipsis
        | Expr::EmptyDict
        | Expr::Yield(None) => {}
    }
}

fn indent(out: &mut String, depth: usize) {
    for _ in 0..depth {
        out.push_str("    ");
    }
}

fn block(out: &mut String, body: &[Stmt], depth: usize) {
    if body.is_empty() {
        indent(out, depth);
        out.push_str("pass\n");
    }
    for s in body {
        stmt(out, s, depth);
    }
}

/// An expression where a `yield` needs no parentheses: a statement, or
/// an assignment's value.
fn bare_yield(e: &Expr) -> String {
    match e {
        Expr::Yield(None) => "yield".into(),
        Expr::Yield(Some(value)) => format!("yield {}", expr(value, IF_ELSE)),
        _ => expr(e, TOP),
    }
}

fn stmt(out: &mut String, s: &Stmt, depth: usize) {
    indent(out, depth);
    match s {
        Stmt::Expr(e) => {
            let _ = writeln!(out, "{}", bare_yield(e));
        }
        Stmt::Assign { target, ann, value } => {
            let ann = ann.as_ref().map(|a| format!(": {a}")).unwrap_or_default();
            let _ = writeln!(
                out,
                "{}{ann} = {}",
                expr(target, PRIMARY),
                bare_yield(value)
            );
        }
        Stmt::Declare { target, ann } => {
            let _ = writeln!(out, "{target}: {ann}");
        }
        Stmt::If { cond, then, els } => {
            let _ = writeln!(out, "if {}:", expr(cond, TOP));
            block(out, then, depth + 1);
            let mut els = els;
            loop {
                match els.as_slice() {
                    [] => break,
                    [
                        Stmt::If {
                            cond,
                            then,
                            els: next,
                        },
                    ] => {
                        indent(out, depth);
                        let _ = writeln!(out, "elif {}:", expr(cond, TOP));
                        block(out, then, depth + 1);
                        els = next;
                    }
                    _ => {
                        indent(out, depth);
                        out.push_str("else:\n");
                        block(out, els, depth + 1);
                        break;
                    }
                }
            }
        }
        Stmt::While { cond, body } => {
            let _ = writeln!(out, "while {}:", expr(cond, TOP));
            block(out, body, depth + 1);
        }
        Stmt::Break => out.push_str("break\n"),
        Stmt::Continue => out.push_str("continue\n"),
        Stmt::Return(None) => out.push_str("return\n"),
        Stmt::Return(Some(e)) => {
            let _ = writeln!(out, "return {}", expr(e, TOP));
        }
        Stmt::Global(names) => {
            let _ = writeln!(out, "global {}", names.join(", "));
        }
        Stmt::Nonlocal(names) => {
            let _ = writeln!(out, "nonlocal {}", names.join(", "));
        }
        Stmt::Def {
            decorators,
            name,
            params,
            positional,
            result,
            body,
        } => {
            let mut list: Vec<String> = (params.iter())
                .map(|(n, t)| match t.as_str() {
                    "" => n.clone(),
                    t => format!("{n}: {t}"),
                })
                .collect();
            if *positional > 0 {
                list.insert(*positional, "/".into());
            }
            for (i, d) in decorators.iter().enumerate() {
                if i == 0 {
                    let _ = writeln!(out, "@{d}");
                } else {
                    indent(out, depth);
                    let _ = writeln!(out, "@{d}");
                }
            }
            if !decorators.is_empty() {
                indent(out, depth);
            }
            let _ = writeln!(out, "def {name}({}) -> {result}:", list.join(", "));
            block(out, body, depth + 1);
        }
        Stmt::Commented(s, comment) => {
            let mut text = String::new();
            stmt(&mut text, s, depth);
            let text = text.trim_start();
            // On a decorated function's `def` line, where checkers report.
            let decorators = match &**s {
                Stmt::Def { decorators, .. } => decorators.len(),
                _ => 0,
            };
            let mut lines = text.split_inclusive('\n');
            for line in lines.by_ref().take(decorators) {
                out.push_str(line);
            }
            let line = lines.next().unwrap_or("");
            let first = line.strip_suffix('\n').unwrap_or(line);
            let _ = writeln!(out, "{first}  # {comment}");
            out.extend(lines);
        }
        Stmt::Try {
            body,
            except,
            finally,
        } => {
            out.push_str("try:\n");
            block(out, body, depth + 1);
            if let Some(except) = except {
                indent(out, depth);
                out.push_str("except Exception:\n");
                block(out, except, depth + 1);
            }
            if !finally.is_empty() || except.is_none() {
                indent(out, depth);
                out.push_str("finally:\n");
                block(out, finally, depth + 1);
            }
        }
        Stmt::Raise(e) => {
            let _ = writeln!(out, "raise {}", expr(e, TOP));
        }
        Stmt::Assert(cond, message) => {
            let _ = match message {
                Some(m) => writeln!(out, "assert {}, {}", expr(cond, TOP), expr(m, TOP)),
                None => writeln!(out, "assert {}", expr(cond, TOP)),
            };
        }
        Stmt::With {
            context,
            name,
            body,
        } => {
            match name {
                Some(name) => {
                    let _ = writeln!(out, "with {} as {name}:", expr(context, TOP));
                }
                None => {
                    let _ = writeln!(out, "with {}:", expr(context, TOP));
                }
            }
            block(out, body, depth + 1);
        }
        Stmt::Class { name, bases, body } => {
            match bases.as_slice() {
                [] => {
                    let _ = writeln!(out, "class {name}:");
                }
                _ => {
                    let _ = writeln!(out, "class {name}({}):", bases.join(", "));
                }
            }
            let mut previous_def = None;
            for s in body {
                let is_def = matches!(s, Stmt::Def { .. });
                if previous_def.is_some_and(|p| p || is_def) {
                    out.push('\n');
                }
                stmt(out, s, depth + 1);
                previous_def = Some(is_def);
            }
            if body.is_empty() {
                block(out, body, depth + 1);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn n(s: &str) -> Expr {
        Expr::name(s)
    }

    #[test]
    fn precedence() {
        let sum = Expr::bin(Bin::Add, n("a"), n("b"));
        assert_eq!(
            expr(&Expr::bin(Bin::Mul, sum.clone(), n("c")), 0),
            "(a + b) * c"
        );
        assert_eq!(
            expr(&Expr::bin(Bin::Sub, n("c"), sum.clone()), 0),
            "c - (a + b)"
        );
        assert_eq!(
            expr(&Expr::bin(Bin::Sub, sum.clone(), n("c")), 0),
            "a + b - c"
        );
        let lt = Expr::bin(Bin::Lt, n("a"), n("b"));
        assert_eq!(
            expr(&Expr::bin(Bin::Eq, lt.clone(), n("c")), 0),
            "(a < b) == c"
        );
        assert_eq!(expr(&Expr::unary(Unary::Not, lt), 0), "not a < b");
        assert_eq!(
            expr(&Expr::bin(Bin::Pow, Expr::Int(-2), n("x")), 0),
            "(-2) ** x"
        );
        assert_eq!(
            expr(&Expr::bin(Bin::Pow, n("x"), Expr::Int(-2)), 0),
            "x ** -2"
        );
        let cond = Expr::IfElse {
            cond: Box::new(n("c")),
            then: Box::new(n("a")),
            els: Box::new(n("b")),
        };
        assert_eq!(
            expr(&n("f").call(vec![cond.clone()]), 0),
            "f(a if c else b)"
        );
        assert_eq!(
            expr(&Expr::bin(Bin::Add, cond, n("d")), 0),
            "(a if c else b) + d"
        );
    }

    #[test]
    fn strings() {
        assert_eq!(string_literal("it's"), "\"it's\"");
        assert_eq!(string_literal("a\n'\"\\"), "'a\\n\\'\"\\\\'");
        let parts = vec![
            FPart::Lit("x={".into()),
            FPart::Expr {
                expr: n("x"),
                str: true,
            },
        ];
        assert_eq!(expr(&Expr::FString(parts), 0), "f'x={{{x}'");
        let quoted = vec![
            FPart::Lit("a".into()),
            FPart::Expr {
                expr: n("f").call(vec![Expr::Str("'\"".into())]),
                str: true,
            },
        ];
        assert_eq!(expr(&Expr::FString(quoted), 0), "'a' + str(f('\\'\"'))");
    }

    /// A builtin a function's own names hide is reached through
    /// `builtins` there, and in what's nested in it, and only there: its
    /// annotations, but after a `.`, and an f-string's `str` where it
    /// falls back to `+`; a lambda's own parameter hides one in it alone.
    #[test]
    fn hidden_builtins_qualified() {
        let def = |name: &str, params: &[(&str, &str)], body: Vec<Stmt>| Stmt::Def {
            decorators: Vec::new(),
            name: name.into(),
            params: (params.iter())
                .map(|(n, t)| (n.to_string(), t.to_string()))
                .collect(),
            positional: params.len(),
            result: "int".into(),
            body,
        };
        let len_of = |x: &str| Expr::builtin("len").call(vec![n(x)]);
        let quoted = Expr::FString(vec![FPart::Expr {
            expr: n("f").call(vec![Expr::Str("'\"".into())]),
            str: true,
        }]);
        let mut body = vec![
            def(
                "f",
                &[("str", "str"), ("xs", "list[int]")],
                vec![
                    Stmt::Declare {
                        target: "n".into(),
                        ann: "x.str | str".into(),
                    },
                    Stmt::Expr(quoted),
                    Stmt::Return(Some(Expr::Lambda(
                        vec!["xs".into()],
                        Box::new(len_of("xs")),
                    ))),
                ],
            ),
            def(
                "g",
                &[("xs", "str")],
                vec![Stmt::Return(Some(Expr::Lambda(
                    vec!["len".into()],
                    Box::new(len_of("len")),
                )))],
            ),
            Stmt::Expr(len_of("xs")),
        ];
        assert!(qualify_builtins(&mut body, &["len", "str"], "_b"));
        let text = module(&Module {
            body,
            ..Module::default()
        });
        assert!(
            text.contains("def f(str: str, xs: list[int], /) -> int:"),
            "{text}"
        );
        assert!(text.contains("    n: x.str | _b.str\n"), "{text}");
        assert!(text.contains("    _b.str(f('\\'\"'))\n"), "{text}");
        assert!(text.contains("    return lambda xs: len(xs)\n"), "{text}");
        assert!(
            text.contains("    return lambda len: _b.len(len)\n"),
            "{text}"
        );
        assert!(text.ends_with("\nlen(xs)\n"), "{text}");
        let mut inf = vec![def(
            "h",
            &[("float", "float")],
            vec![Stmt::Return(Some(Expr::Float(f64::NEG_INFINITY)))],
        )];
        assert!(qualify_builtins(&mut inf, &["float"], "_b"));
        let text = module(&Module {
            body: inf,
            ..Module::default()
        });
        assert!(text.contains("    return _b.float('-inf')\n"), "{text}");
        let mut bare = vec![Stmt::Expr(len_of("xs"))];
        assert!(!qualify_builtins(&mut bare, &["len"], "_b"));
    }
}
