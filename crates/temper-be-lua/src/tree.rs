//! Lua's output tree and its printer.
//!
//! The printer lays code out as StyLua does by default, but for its
//! indent: two spaces, as Kotlin's output and Lua's manual have them,
//! rather than tabs. Double quotes unless a string has more of them than
//! of single quotes, no semicolons, parentheses only where precedence needs
//! them, and a blank line around each function. It doesn't break long
//! lines.
//!
//! Strings are bytes in Lua, so a literal is the string's UTF-8: printable
//! characters as they are, and control, invisible, and bidirectional
//! characters as decimal byte escapes (`\226\128\174`), which every Lua
//! version reads (`\x` and `\u{…}` need 5.2 and 5.3).

use std::fmt::Write;

/// The line width past which a returned table takes a line per field.
pub const WIDTH: usize = 80;

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
    Add,
    Sub,
    Mul,
    Div,
    Pow,
}

impl Bin {
    fn text(self) -> &'static str {
        match self {
            Bin::Or => "or",
            Bin::And => "and",
            Bin::Eq => "==",
            Bin::Ne => "~=",
            Bin::Lt => "<",
            Bin::Le => "<=",
            Bin::Gt => ">",
            Bin::Ge => ">=",
            Bin::Add => "+",
            Bin::Sub => "-",
            Bin::Mul => "*",
            Bin::Div => "/",
            Bin::Pow => "^",
        }
    }

    fn prec(self) -> u8 {
        match self {
            Bin::Or => OR,
            Bin::And => AND,
            Bin::Eq | Bin::Ne | Bin::Lt | Bin::Le | Bin::Gt | Bin::Ge => COMPARE,
            Bin::Add | Bin::Sub => ADD,
            Bin::Mul | Bin::Div => MUL,
            Bin::Pow => POW,
        }
    }

    /// The comparison that's true exactly when this one is false, for
    /// operands that are never NaN: integers, strings, and the results of
    /// the runtime's three-way comparisons.
    pub fn negated(self) -> Option<Bin> {
        Some(match self {
            Bin::Eq => Bin::Ne,
            Bin::Ne => Bin::Eq,
            Bin::Lt => Bin::Ge,
            Bin::Ge => Bin::Lt,
            Bin::Le => Bin::Gt,
            Bin::Gt => Bin::Le,
            _ => return None,
        })
    }
}

/// A prefix operator.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Unary {
    Not,
    Neg,
}

// Precedences, loosest first, as Lua's manual lists them (Lua's bitwise
// operators aren't printed: the runtime's functions do their work, as
// they parse only in Lua 5.3 and later).
const TOP: u8 = 0;
/// A cast, `x --[[@as T]]`, looser than any operator.
const CAST: u8 = 0;
const OR: u8 = 1;
const AND: u8 = 2;
const COMPARE: u8 = 3;
const CONCAT: u8 = 9;
const ADD: u8 = 10;
const MUL: u8 = 11;
const UNARY: u8 = 12;
const POW: u8 = 14;
const CALL: u8 = 16;
const ATOM: u8 = 17;

#[derive(Clone, Debug, PartialEq)]
pub enum Expr {
    Name(String),
    /// An integer literal: `Int32`'s and `Int64`'s.
    Int(i64),
    /// A float literal, always written as a float (`1.0`), since `1` is
    /// an integer in Lua 5.3 and later.
    Float(f64),
    Str(String),
    Bool(bool),
    Nil,
    Call(Box<Expr>, Vec<Expr>),
    /// `obj.name`, or `obj['name']` for a key that isn't a name.
    Member(Box<Expr>, String),
    /// `obj[key]`.
    Index(Box<Expr>, Box<Expr>),
    Bin(Bin, Box<Expr>, Box<Expr>),
    Unary(Unary, Box<Expr>),
    /// `a .. b .. c`.
    Concat(Vec<Expr>),
    /// A table constructor with named fields: `{ a = a, b = b }`, or
    /// `['$test'] = …` for a key that isn't a name.
    Table(Vec<(String, Expr)>),
    /// A list's table, `{a, b}`.
    Array(Vec<Expr>),
    /// A method call, `obj:name(args)`.
    Invoke(Box<Expr>, String, Vec<Expr>),
    /// A value as LuaLS should type it, `value --[[@as T]]`, for a
    /// downcast that Lua doesn't check.
    Cast(Box<Expr>, String),
    /// `function(a, b) return body end`, or for `Void`, without `return`:
    /// a function literal whose body is one expression, on one line.
    Function {
        params: Vec<String>,
        body: Box<Expr>,
        returns: bool,
    },
}

impl Expr {
    pub fn name(name: impl Into<String>) -> Expr {
        Expr::Name(name.into())
    }

    pub fn call(self, args: Vec<Expr>) -> Expr {
        Expr::Call(Box::new(self), args)
    }

    pub fn member(self, name: impl Into<String>) -> Expr {
        Expr::Member(Box::new(self), name.into())
    }

    pub fn method(self, name: impl Into<String>, args: Vec<Expr>) -> Expr {
        self.member(name).call(args)
    }

    /// `self:name(args)`.
    pub fn invoke(self, name: impl Into<String>, args: Vec<Expr>) -> Expr {
        Expr::Invoke(Box::new(self), name.into(), args)
    }

    pub fn bin(op: Bin, left: Expr, right: Expr) -> Expr {
        Expr::Bin(op, Box::new(left), Box::new(right))
    }

    pub fn unary(op: Unary, operand: Expr) -> Expr {
        Expr::Unary(op, Box::new(operand))
    }

    /// `not e`, with a comparison negated in place (`a >= b` for
    /// `not (a < b)`) and `not not e` as `e`.
    pub fn negate(self) -> Expr {
        match self {
            Expr::Bin(op, a, b) if op.negated().is_some() => {
                Expr::Bin(op.negated().unwrap_or(op), a, b)
            }
            Expr::Unary(Unary::Not, e) => *e,
            Expr::Bool(b) => Expr::Bool(!b),
            e => Expr::unary(Unary::Not, e),
        }
    }

    /// Whether Lua takes it as a statement: only a call.
    pub fn is_statement(&self) -> bool {
        matches!(self, Expr::Call(..) | Expr::Invoke(..))
    }

    fn prec(&self) -> u8 {
        match self {
            Expr::Int(n) if *n < 0 => UNARY,
            Expr::Float(f) if f.is_sign_negative() && !f.is_nan() => UNARY,
            Expr::Float(f) if f.is_nan() => MUL,
            Expr::Name(_)
            | Expr::Int(_)
            | Expr::Float(_)
            | Expr::Str(_)
            | Expr::Bool(_)
            | Expr::Nil
            | Expr::Table(_)
            | Expr::Array(_)
            | Expr::Function { .. } => ATOM,
            // Parenthesized as an operand, as LuaLS takes `--[[@as T]]` as
            // typing all of what's before it there: `a and b --[[@as T]]`
            // is a `T`.
            Expr::Cast(..) => CAST,
            Expr::Call(..) | Expr::Member(..) | Expr::Index(..) | Expr::Invoke(..) => CALL,
            Expr::Bin(op, ..) => op.prec(),
            Expr::Unary(..) => UNARY,
            Expr::Concat(_) => CONCAT,
        }
    }
}

/// A function's parameters and result, as LuaCATS annotations
/// (`---@param i integer`), for LuaLS's checker.
#[derive(Clone, Debug, PartialEq)]
pub struct Signature {
    pub params: Vec<(String, String)>,
    pub result: Option<String>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum Stmt {
    /// A call.
    Expr(Expr),
    /// `local name = value`, or `local name` with a `---@type` annotation
    /// before it.
    Local {
        name: String,
        ty: Option<String>,
        value: Option<Expr>,
    },
    /// `local a, b`: names declared before what refers to them.
    Locals(Vec<String>),
    /// `---@cast name type`: what LuaLS takes a local to be from here on,
    /// as after a parameter's default.
    Cast {
        name: String,
        ty: String,
    },
    Assign {
        target: Expr,
        value: Expr,
    },
    If {
        cond: Expr,
        then: Vec<Stmt>,
        els: Vec<Stmt>,
    },
    /// `while cond do … end`, with `::label::` ending the body when a
    /// `continue` needs one.
    While {
        cond: Expr,
        body: Vec<Stmt>,
        next: Option<String>,
    },
    /// `repeat … until cond`, with `::label::` before `until` when a
    /// `continue` needs one. Lua's `until` sees the body's locals, so a
    /// jump to the label would enter their scope: a body that declares any
    /// is put in a `do … end` of its own first.
    Repeat {
        body: Vec<Stmt>,
        cond: Expr,
        next: Option<String>,
    },
    /// `do … end`.
    Do(Vec<Stmt>),
    /// Temper's `orelse`, as Kotlin's: `body` in a function that
    /// `temper.pcall` runs, then `fallback` if it bubbled (anything
    /// else it raises, `temper.pcall` raises again):
    /// `if not temper.pcall(function() … end) then … end`.
    Try {
        body: Vec<Stmt>,
        fallback: Vec<Stmt>,
    },
    /// Temper's `orelse` whose body jumps out of it, as Kotlin's: `local
    /// ok, value, kind = temper.pcall(function() … end)`, the body
    /// returning a signal for each jump (`return v, "return"`, `return
    /// "break", "flow"`), and an `if` after it redoing the jump.
    PCall {
        names: Vec<String>,
        body: Vec<Stmt>,
    },
    Break,
    Goto(String),
    Label(String),
    Return(Option<Expr>),
    /// `return a, b`: a jump's signal out of a function `temper.pcall`
    /// runs ([`Stmt::PCall`]).
    Returns(Vec<Expr>),
    /// `local function name(…)`, or `function name(…)` for a name declared
    /// before (`local`, or a table's field, `module.name`).
    /// A test, as LuaUnit runs it: `function METHOD()`, running
    /// `temper.test(name, function(test) … end)`, which fails it if it
    /// errs or its `test` has failures.
    Test {
        method: String,
        name: String,
        test: String,
        body: Vec<Stmt>,
    },
    Function {
        local: bool,
        name: String,
        signature: Signature,
        body: Vec<Stmt>,
    },
    /// LuaCATS annotations, a `---` line each, for LuaLS: a class's
    /// `---@class` and `---@field`s.
    Doc(Vec<String>),
    /// Code as it is, a line each, for the runtime support a module
    /// defines for itself.
    Raw(Vec<String>),
    /// A blank line, between groups of statements.
    Blank,
}

impl Stmt {
    /// Whether nothing after it in its block can run.
    pub fn ends(&self) -> bool {
        matches!(
            self,
            Stmt::Return(_) | Stmt::Returns(_) | Stmt::Break | Stmt::Goto(_)
        )
    }
}

fn escape_into(out: &mut String, text: &str, quote: char) {
    for c in text.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            _ if c == quote => {
                out.push('\\');
                out.push(c);
            }
            _ if (c as u32) < 0x20
                || (0x7f..0xa0).contains(&(c as u32))
                || matches!(c,
                    '\u{ad}'
                    | '\u{200b}'..='\u{200f}'
                    | '\u{2028}'..='\u{202e}'
                    | '\u{2060}'..='\u{206f}'
                    | '\u{feff}'
                    | '\u{fff9}'..='\u{fffb}') =>
            {
                // Three digits, so a digit after it isn't read as more.
                let mut bytes = [0; 4];
                for b in c.encode_utf8(&mut bytes).bytes() {
                    let _ = write!(out, "\\{b:03}");
                }
            }
            _ => out.push(c),
        }
    }
}

/// Prints a string literal, in double quotes unless the text has more of
/// them than of single quotes, as StyLua chooses.
pub fn string_literal(text: &str) -> String {
    let doubles = text.matches('"').count();
    let singles = text.matches('\'').count();
    let quote = if doubles > singles { '\'' } else { '"' };
    let mut out = String::new();
    out.push(quote);
    escape_into(&mut out, text, quote);
    out.push(quote);
    out
}

fn float_literal(f: f64) -> String {
    if f.is_nan() {
        "0.0 / 0.0".into()
    } else if f.is_infinite() {
        if f > 0.0 {
            "math.huge".into()
        } else {
            "-math.huge".into()
        }
    } else {
        // Rust's `{:?}` gives `1.0`, `0.1`, and `1e100`, all floats in Lua.
        format!("{f:?}")
    }
}

fn int_literal(n: i64) -> String {
    if n == i64::MIN {
        // `9223372036854775808` doesn't fit, so Lua would read a float.
        "(-9223372036854775807 - 1)".into()
    } else {
        n.to_string()
    }
}

/// Prints an expression, parenthesized if it binds looser than `min`.
pub fn expr(e: &Expr, min: u8) -> String {
    let text = match e {
        Expr::Name(n) => n.clone(),
        Expr::Int(n) => int_literal(*n),
        Expr::Float(f) => float_literal(*f),
        Expr::Str(s) => string_literal(s),
        Expr::Bool(b) => b.to_string(),
        Expr::Nil => "nil".into(),
        Expr::Call(f, args) => {
            let args: Vec<String> = args.iter().map(|a| expr(a, TOP)).collect();
            format!("{}({})", expr(f, CALL), args.join(", "))
        }
        Expr::Member(obj, name) => {
            let o = prefix(obj);
            if is_name(name) {
                format!("{o}.{name}")
            } else {
                format!("{o}[{}]", string_literal(name))
            }
        }
        Expr::Index(obj, key) => format!("{}[{}]", prefix(obj), expr(key, TOP)),
        Expr::Invoke(obj, name, args) => {
            let args: Vec<String> = args.iter().map(|a| expr(a, TOP)).collect();
            format!("{}:{name}({})", prefix(obj), args.join(", "))
        }
        // A cast of a cast is one, the outer's: LuaLS reads only the
        // first `@as` (`x --[[@as A]] --[[@as B]]` is an `A`).
        Expr::Cast(value, ty) => {
            let mut value = value;
            while let Expr::Cast(inner, _) = &**value {
                value = inner;
            }
            // A type ending in `]`, as `number[]`, would end `--[[`'s
            // comment early, so `--[=[` it is.
            if ty.ends_with(']') || ty.contains("]]") {
                format!("{} --[=[@as {ty}]=]", expr(value, CALL))
            } else {
                format!("{} --[[@as {ty}]]", expr(value, CALL))
            }
        }
        Expr::Function {
            params,
            body,
            returns,
        } => {
            let ret = if *returns { "return " } else { "" };
            format!(
                "function({}) {ret}{} end",
                params.join(", "),
                expr(body, TOP)
            )
        }
        Expr::Bin(op, left, right) => {
            let p = op.prec();
            let (lmin, rmin) = match op {
                // Right-associative, and binding tighter than a prefix
                // operator on its left, but not on its right.
                Bin::Pow => (POW + 1, UNARY),
                // Comparisons chain without meaning what they'd seem to.
                _ if p == COMPARE => (p + 1, p + 1),
                _ => (p, p + 1),
            };
            format!("{} {} {}", expr(left, lmin), op.text(), expr(right, rmin))
        }
        Expr::Unary(op, operand) => {
            let inner = expr(operand, UNARY);
            match op {
                Unary::Not => format!("not {inner}"),
                // `--` starts a comment.
                Unary::Neg if inner.starts_with('-') => format!("-({inner})"),
                Unary::Neg => format!("-{inner}"),
            }
        }
        Expr::Concat(parts) => {
            // Arithmetic binds tighter than `..`, but reads more clearly
            // in parentheses, as `"n=" .. (a + b)`.
            let parts: Vec<String> = (parts.iter())
                .map(|p| match p {
                    Expr::Bin(..) => format!("({})", expr(p, TOP)),
                    _ => expr(p, CONCAT + 1),
                })
                .collect();
            parts.join(" .. ")
        }
        Expr::Table(entries) if entries.is_empty() => "{}".into(),
        Expr::Array(items) if items.is_empty() => "{}".into(),
        Expr::Array(items) => {
            let items: Vec<String> = items.iter().map(|i| expr(i, TOP)).collect();
            format!("{{ {} }}", items.join(", "))
        }
        Expr::Table(entries) => {
            let entries: Vec<String> = (entries.iter())
                .map(|(k, v)| match is_name(k) {
                    true => format!("{k} = {}", expr(v, TOP)),
                    false => format!("[{}] = {}", string_literal(k), expr(v, TOP)),
                })
                .collect();
            format!("{{ {} }}", entries.join(", "))
        }
    };
    if e.prec() < min {
        format!("({text})")
    } else {
        text
    }
}

/// An expression that's indexed or called through: a literal or a cast
/// needs parentheses.
fn prefix(obj: &Expr) -> String {
    match obj {
        Expr::Name(_) | Expr::Call(..) | Expr::Member(..) | Expr::Index(..) | Expr::Invoke(..) => {
            expr(obj, CALL)
        }
        _ => format!("({})", expr(obj, TOP)),
    }
}

/// One Lua file: its statements, at the top level of its chunk.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Chunk {
    pub body: Vec<Stmt>,
}

/// Prints a chunk: a blank line where the tree has one, and around each
/// function.
pub fn chunk(c: &Chunk) -> String {
    let mut out = String::new();
    let (mut started, mut blank, mut after_function) = (false, false, false);
    for s in &c.body {
        if let Stmt::Blank = s {
            blank = started;
            continue;
        }
        let function = matches!(s, Stmt::Function { .. } | Stmt::Test { .. } | Stmt::Raw(_));
        if started && (blank || function || after_function) {
            out.push('\n');
        }
        stmt(&mut out, s, 0);
        (started, blank, after_function) = (true, false, function);
    }
    out
}

fn indent(out: &mut String, depth: usize) {
    for _ in 0..depth {
        out.push_str("  ");
    }
}

fn block(out: &mut String, body: &[Stmt], depth: usize) {
    for (i, s) in body.iter().enumerate() {
        // `return` ends a block in every Lua; elsewhere, `do return end`.
        if matches!(s, Stmt::Return(_) | Stmt::Returns(_)) && i + 1 < body.len() {
            stmt(out, &Stmt::Do(vec![s.clone()]), depth);
        } else {
            stmt(out, s, depth);
        }
    }
}

/// A loop's body with the label a `continue` jumps to, if any, ending
/// it; in a `repeat` whose body declares locals, after a `do … end` of
/// the body, so the jump doesn't enter their scope, which `until` shares.
fn labeled(mut body: Vec<Stmt>, next: &Option<String>, repeat: bool) -> Vec<Stmt> {
    let Some(next) = next else {
        return body;
    };
    if repeat && body.iter().any(|s| matches!(s, Stmt::Local { .. })) {
        body = vec![Stmt::Do(body)];
    }
    body.push(Stmt::Label(next.clone()));
    body
}

fn stmt(out: &mut String, s: &Stmt, depth: usize) {
    if let Stmt::Blank = s {
        out.push('\n');
        return;
    }
    indent(out, depth);
    let start = out.len();
    stmt_text(out, s, depth);
    // A statement starting with `(` would continue the last one as a
    // call (`f(x)\n(t).a = 1` is `f(x)(t).a = 1`), so `;` ends that.
    if out[start..].starts_with('(') {
        out.insert(start, ';');
    }
}

fn stmt_text(out: &mut String, s: &Stmt, depth: usize) {
    match s {
        Stmt::Blank => {}
        Stmt::Expr(e) => {
            let _ = writeln!(out, "{}", expr(e, TOP));
        }
        Stmt::Local { name, ty, value } => {
            if let Some(ty) = ty {
                let _ = writeln!(out, "---@type {ty}");
                indent(out, depth);
            }
            match value {
                Some(v) => {
                    let _ = writeln!(out, "local {name} = {}", expr(v, TOP));
                }
                None => {
                    let _ = writeln!(out, "local {name}");
                }
            }
        }
        Stmt::Doc(lines) | Stmt::Raw(lines) => {
            let dashes = if matches!(s, Stmt::Doc(_)) { "---" } else { "" };
            for (i, line) in lines.iter().enumerate() {
                if i > 0 && !line.is_empty() {
                    indent(out, depth);
                }
                let _ = writeln!(out, "{dashes}{line}");
            }
        }
        Stmt::Locals(names) => {
            let _ = writeln!(out, "local {}", names.join(", "));
        }
        Stmt::Assign { target, value } => {
            let _ = writeln!(out, "{} = {}", expr(target, TOP), expr(value, TOP));
        }
        Stmt::If { cond, then, els } => {
            let _ = writeln!(out, "if {} then", expr(cond, TOP));
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
                        let _ = writeln!(out, "elseif {} then", expr(cond, TOP));
                        block(out, then, depth + 1);
                        els = next;
                    }
                    _ => {
                        indent(out, depth);
                        out.push_str("else\n");
                        block(out, els, depth + 1);
                        break;
                    }
                }
            }
            indent(out, depth);
            out.push_str("end\n");
        }
        Stmt::While { cond, body, next } => {
            let _ = writeln!(out, "while {} do", expr(cond, TOP));
            block(out, &labeled(body.clone(), next, false), depth + 1);
            indent(out, depth);
            out.push_str("end\n");
        }
        Stmt::Repeat { body, cond, next } => {
            out.push_str("repeat\n");
            block(out, &labeled(body.clone(), next, true), depth + 1);
            indent(out, depth);
            let _ = writeln!(out, "until {}", expr(cond, TOP));
        }
        Stmt::Do(body) => {
            match body.as_slice() {
                // `do return end`, Lua's idiom for a `return` mid-block.
                [Stmt::Return(value)] => {
                    let value = value
                        .as_ref()
                        .map(|v| format!(" {}", expr(v, TOP)))
                        .unwrap_or_default();
                    let _ = writeln!(out, "do return{value} end");
                    return;
                }
                _ => out.push_str("do\n"),
            }
            block(out, body, depth + 1);
            indent(out, depth);
            out.push_str("end\n");
        }
        Stmt::Try { body, fallback } => {
            out.push_str("if not temper.pcall(function()\n");
            block(out, body, depth + 1);
            indent(out, depth);
            out.push_str("end) then\n");
            block(out, fallback, depth + 1);
            indent(out, depth);
            out.push_str("end\n");
        }
        Stmt::PCall { names, body } => {
            let _ = writeln!(out, "local {} = temper.pcall(function()", names.join(", "));
            block(out, body, depth + 1);
            indent(out, depth);
            out.push_str("end)\n");
        }
        Stmt::Returns(values) => {
            let values: Vec<String> = values.iter().map(|v| expr(v, TOP)).collect();
            let _ = writeln!(out, "return {}", values.join(", "));
        }
        Stmt::Break => out.push_str("break\n"),
        Stmt::Goto(label) => {
            let _ = writeln!(out, "goto {label}");
        }
        Stmt::Label(label) => {
            let _ = writeln!(out, "::{label}::");
        }
        Stmt::Return(None) => out.push_str("return\n"),
        Stmt::Return(Some(Expr::Table(entries))) => {
            let line = format!("return {}", expr(&Expr::Table(entries.clone()), TOP));
            if line.len() + 2 * depth <= WIDTH {
                let _ = writeln!(out, "{line}");
            } else {
                // A field a line, as StyLua breaks a long table.
                out.push_str("return {\n");
                for (k, v) in entries {
                    indent(out, depth + 1);
                    match is_name(k) {
                        true => writeln!(out, "{k} = {},", expr(v, TOP)),
                        false => writeln!(out, "[{}] = {},", string_literal(k), expr(v, TOP)),
                    }
                    .unwrap();
                }
                indent(out, depth);
                out.push_str("}\n");
            }
        }
        Stmt::Return(Some(e)) => {
            let _ = writeln!(out, "return {}", expr(e, TOP));
        }
        Stmt::Cast { name, ty } => {
            let _ = writeln!(out, "---@cast {name} {ty}");
        }
        Stmt::Function {
            local,
            name,
            signature,
            body,
        } => {
            // A type parameter is a parameter named `@generic`.
            for (n, t) in &signature.params {
                match n.as_str() {
                    "@generic" => writeln!(out, "---@generic {t}"),
                    _ => writeln!(out, "---@param {n} {t}"),
                }
                .unwrap();
                indent(out, depth);
            }
            if let Some(r) = &signature.result {
                let _ = writeln!(out, "---@return {r}");
                indent(out, depth);
            }
            if *local {
                out.push_str("local ");
            }
            // An optional parameter's `?` is only for LuaLS.
            let names: Vec<&str> = (signature.params.iter())
                .map(|(n, _)| n.strip_suffix('?').unwrap_or(n))
                .filter(|n| *n != "@generic")
                .collect();
            let _ = writeln!(out, "function {name}({})", names.join(", "));
            block(out, body, depth + 1);
            indent(out, depth);
            out.push_str("end\n");
        }
        Stmt::Test {
            method,
            name,
            test,
            body,
        } => {
            let _ = writeln!(out, "function {method}()");
            indent(out, depth + 1);
            let _ = writeln!(
                out,
                "temper.test({}, function({test})",
                string_literal(name)
            );
            block(out, body, depth + 2);
            indent(out, depth + 1);
            out.push_str("end)\n");
            indent(out, depth);
            out.push_str("end\n");
        }
    }
}

/// Whether a table's key can be written bare, as a name: not a keyword.
fn is_name(key: &str) -> bool {
    let mut chars = key.chars();
    chars
        .next()
        .is_some_and(|c| c == '_' || c.is_ascii_alphabetic())
        && chars.all(|c| c == '_' || c.is_ascii_alphanumeric())
        && !crate::names::KEYWORDS.contains(&key)
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
        assert_eq!(
            expr(&Expr::bin(Bin::Pow, Expr::Int(-2), n("x")), 0),
            "(-2) ^ x"
        );
        assert_eq!(
            expr(&Expr::bin(Bin::Pow, n("x"), Expr::Int(-2)), 0),
            "x ^ -2"
        );
        assert_eq!(expr(&Expr::unary(Unary::Neg, Expr::Int(-2)), 0), "-(-2)");
        let eq = Expr::bin(Bin::Eq, n("a"), n("b"));
        assert_eq!(
            expr(&Expr::unary(Unary::Not, eq.clone()), 0),
            "not (a == b)"
        );
        assert_eq!(expr(&eq.clone().negate(), 0), "a ~= b");
        assert_eq!(
            expr(&Expr::bin(Bin::Eq, eq.clone(), n("c")), 0),
            "(a == b) == c"
        );
        let and = Expr::bin(Bin::And, n("c"), n("a"));
        assert_eq!(expr(&Expr::bin(Bin::Or, and, n("b")), 0), "c and a or b");
        let cat = Expr::Concat(vec![Expr::Str("n=".into()), sum.clone(), Expr::Int(-1)]);
        assert_eq!(expr(&cat, 0), "\"n=\" .. (a + b) .. -1");
        assert_eq!(
            expr(&Expr::Str("x".into()).method("len", vec![]), 0),
            "(\"x\").len()"
        );
        assert_eq!(expr(&Expr::Int(i64::MIN), 0), "(-9223372036854775807 - 1)");
        assert_eq!(
            expr(&Expr::Cast(Box::new(n("b")), "number[]".into()), 0),
            "b --[=[@as number[]]=]"
        );
        assert_eq!(expr(&Expr::Float(1.0), 0), "1.0");
        assert_eq!(
            expr(&Expr::bin(Bin::Div, n("x"), Expr::Float(f64::NAN)), 0),
            "x / (0.0 / 0.0)"
        );
    }

    #[test]
    fn strings() {
        assert_eq!(string_literal("it's"), "\"it's\"");
        assert_eq!(string_literal("say \"hi\""), "'say \"hi\"'");
        assert_eq!(string_literal("a\n\\"), "\"a\\n\\\\\"");
        assert_eq!(string_literal("a\u{0}1"), "\"a\\0001\"");
        assert_eq!(string_literal("\u{202e}é"), "\"\\226\\128\\174é\"");
    }

    /// A statement that starts with `(` would continue the call before
    /// it, so `;` comes first.
    #[test]
    fn parenthesized_statements_start_with_semicolons() {
        let cast = Expr::Cast(Box::new(n("log")), "Log".into());
        let body = vec![
            Stmt::Expr(n("f").call(vec![])),
            Stmt::Assign {
                target: Expr::Member(Box::new(cast), "hostEnd".into()),
                value: Expr::Int(1),
            },
        ];
        let mut out = String::new();
        block(&mut out, &body, 1);
        assert_eq!(out, "  f()\n  ;(log --[[@as Log]]).hostEnd = 1\n");
    }

    #[test]
    fn returns_end_blocks() {
        let body = vec![
            Stmt::Return(Some(Expr::Int(1))),
            Stmt::Label("continue".into()),
        ];
        let mut out = String::new();
        block(&mut out, &body, 0);
        assert_eq!(out, "do return 1 end\n::continue::\n");
    }
}
