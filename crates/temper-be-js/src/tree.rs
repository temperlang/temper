//! JS's output tree and its printer.
//!
//! The printer lays code out in Prettier's default style: two-space
//! indents, double quotes, semicolons, and parentheses where precedence
//! needs them or where Prettier adds them for clarity (around a binary
//! operand of a bitwise operator, `(a + b) | 0`; around `&&` inside `||`;
//! and around `%` inside `+` or `-`). It doesn't break long lines, apart
//! from imports and exports.
//!
//! Statements may carry their source spans ([`Stmt::Spanned`]), and a
//! function its name's; the printer marks where each starts, for the
//! module's source map ([`temper_tmpl::source_map`]).

use std::fmt::Write;
use temper_tmpl::Span;
use temper_tmpl::source_map::MappedText;

/// Prettier's default line width.
pub const WIDTH: usize = 80;

/// A binary operator.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Bin {
    /// `??`, which JS won't mix with `&&` or `||` unparenthesized.
    Coalesce,
    Or,
    And,
    BitOr,
    BitXor,
    BitAnd,
    Eq,
    Ne,
    Lt,
    Le,
    Gt,
    Ge,
    Shl,
    Shr,
    Ushr,
    Add,
    Sub,
    Mul,
    Div,
    Mod,
    Pow,
    InstanceOf,
    /// `==`, only against `null`, where it also matches `undefined`.
    LooseEq,
    /// `!=`, only against `null`.
    LooseNe,
}

impl Bin {
    fn text(self) -> &'static str {
        match self {
            Bin::Coalesce => "??",
            Bin::Or => "||",
            Bin::And => "&&",
            Bin::BitOr => "|",
            Bin::BitXor => "^",
            Bin::BitAnd => "&",
            Bin::Eq => "===",
            Bin::Ne => "!==",
            Bin::Lt => "<",
            Bin::Le => "<=",
            Bin::Gt => ">",
            Bin::Ge => ">=",
            Bin::Shl => "<<",
            Bin::Shr => ">>",
            Bin::Ushr => ">>>",
            Bin::Add => "+",
            Bin::Sub => "-",
            Bin::Mul => "*",
            Bin::Div => "/",
            Bin::Mod => "%",
            Bin::Pow => "**",
            Bin::InstanceOf => "instanceof",
            Bin::LooseEq => "==",
            Bin::LooseNe => "!=",
        }
    }

    fn prec(self) -> u8 {
        match self {
            Bin::Coalesce | Bin::Or => OR,
            Bin::And => AND,
            Bin::BitOr => 5,
            Bin::BitXor => 6,
            Bin::BitAnd => 7,
            Bin::Eq | Bin::Ne | Bin::LooseEq | Bin::LooseNe => 8,
            Bin::Lt | Bin::Le | Bin::Gt | Bin::Ge | Bin::InstanceOf => 9,
            Bin::Shl | Bin::Shr | Bin::Ushr => 10,
            Bin::Add | Bin::Sub => 11,
            Bin::Mul | Bin::Div | Bin::Mod => 12,
            Bin::Pow => POW,
        }
    }

    fn bitwise(self) -> bool {
        matches!(
            self,
            Bin::BitOr | Bin::BitXor | Bin::BitAnd | Bin::Shl | Bin::Shr | Bin::Ushr
        )
    }

    /// The comparison that's true exactly when this one is false, for
    /// operands that are never NaN.
    pub fn negated(self) -> Option<Bin> {
        Some(match self {
            Bin::Eq => Bin::Ne,
            Bin::Ne => Bin::Eq,
            Bin::Lt => Bin::Ge,
            Bin::Ge => Bin::Lt,
            Bin::Le => Bin::Gt,
            Bin::Gt => Bin::Le,
            Bin::LooseEq => Bin::LooseNe,
            Bin::LooseNe => Bin::LooseEq,
            _ => return None,
        })
    }
}

/// A prefix operator.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Unary {
    Not,
    Neg,
    BitNot,
}

impl Unary {
    fn text(self) -> &'static str {
        match self {
            Unary::Not => "!",
            Unary::Neg => "-",
            Unary::BitNot => "~",
        }
    }
}

// Precedences, loosest first.
const TOP: u8 = 0;
const ARROW: u8 = 1;
const COND: u8 = 2;
const OR: u8 = 3;
const AND: u8 = 4;
const POW: u8 = 13;
const UNARY: u8 = 14;
const CALL: u8 = 16;
const ATOM: u8 = 17;

/// A part of a template literal.
#[derive(Clone, Debug, PartialEq)]
pub enum TPart {
    Lit(String),
    Expr(Expr),
}

#[derive(Clone, Debug, PartialEq)]
pub enum Expr {
    Name(String),
    /// A number literal for an integer: `Int32`'s.
    Int(i64),
    Float(f64),
    /// A `bigint` literal: `Int64`'s, `5n`.
    BigInt(i64),
    Str(String),
    Bool(bool),
    Undefined,
    Null,
    Call(Box<Expr>, Vec<Expr>),
    Member(Box<Expr>, String),
    Bin(Bin, Box<Expr>, Box<Expr>),
    Unary(Unary, Box<Expr>),
    /// `cond ? then : els`.
    Cond {
        cond: Box<Expr>,
        then: Box<Expr>,
        els: Box<Expr>,
    },
    Template(Vec<TPart>),
    This,
    /// `new C(args)`.
    New(Box<Expr>, Vec<Expr>),
    /// A JSDoc cast, `/** @type {T} */ (e)`, for TypeScript's checker.
    Cast(String, Box<Expr>),
    /// `[a, b]`.
    Array(Vec<Expr>),
    /// `(a, b) => body`, an arrow function whose body is an expression.
    Arrow(Vec<String>, Box<Expr>),
    /// `obj[index]`.
    Index(Box<Expr>, Box<Expr>),
    /// `target = value` or a compound assignment, `target += value`: the
    /// operator, then its sides.
    Assign(&'static str, Box<Expr>, Box<Expr>),
    /// `yield value`, or a bare `yield`: in a `function*`.
    Yield(Option<Box<Expr>>),
    /// `throw value`, a declared error's: a `throw` statement as an
    /// expression statement's, an assignment's value, or a `return`'s
    /// (code after it, which JS never reaches, left out), and elsewhere,
    /// where JS has no `throw` expression, an arrow function called at
    /// once, `(() => { throw value; })()`.
    Throw(Box<Expr>),
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

    pub fn bin(op: Bin, left: Expr, right: Expr) -> Expr {
        Expr::Bin(op, Box::new(left), Box::new(right))
    }

    pub fn unary(op: Unary, operand: Expr) -> Expr {
        Expr::Unary(op, Box::new(operand))
    }

    fn prec(&self) -> u8 {
        match self {
            Expr::Int(n) | Expr::BigInt(n) if *n < 0 => UNARY,
            Expr::Float(f) if f.is_sign_negative() && !f.is_nan() => UNARY,
            Expr::Name(_)
            | Expr::Int(_)
            | Expr::Float(_)
            | Expr::BigInt(_)
            | Expr::Str(_)
            | Expr::Bool(_)
            | Expr::Undefined
            | Expr::Null
            | Expr::Template(_)
            | Expr::This
            | Expr::Cast(..)
            | Expr::Array(_) => ATOM,
            Expr::New(..) | Expr::Throw(..) => CALL,
            Expr::Call(..) | Expr::Member(..) | Expr::Index(..) => CALL,
            Expr::Assign(..) | Expr::Yield(..) => ARROW,
            Expr::Bin(op, ..) => op.prec(),
            Expr::Unary(..) => UNARY,
            Expr::Cond { .. } => COND,
            Expr::Arrow(..) => ARROW,
        }
    }
}

/// `const` or `let`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Keyword {
    Const,
    Let,
}

#[derive(Clone, Debug, PartialEq)]
pub enum Stmt {
    /// A statement and the span it comes from.
    Spanned(Span, Box<Stmt>),
    Expr(Expr),
    /// A block of its own, which scopes its `let`s and `const`s.
    Block(Vec<Stmt>),
    /// `const name = value;` or `let name;`, with a JSDoc `@type` before
    /// it if `ty` is given, and `export` first if `export`.
    Decl {
        export: bool,
        keyword: Keyword,
        name: String,
        ty: Option<String>,
        value: Option<Expr>,
    },
    /// `target = value;`, to a name or a member.
    Assign {
        target: Expr,
        value: Expr,
    },
    /// `target ??= value;`: a parameter's default.
    AssignCoalesce {
        target: Expr,
        value: Expr,
    },
    If {
        cond: Expr,
        then: Vec<Stmt>,
        els: Vec<Stmt>,
    },
    While {
        label: Option<String>,
        cond: Expr,
        body: Vec<Stmt>,
    },
    DoWhile {
        label: Option<String>,
        body: Vec<Stmt>,
        cond: Expr,
    },
    Break(Option<String>),
    Continue(Option<String>),
    /// `try { … } catch (var) { if (!caught) throw var; … }`, where
    /// `caught` says whether `var` is what a handler catches, as
    /// `e instanceof NotFound` for a declared error: Temper's `orelse` and `catch`, which take only
    /// what their bodies may fail with and rethrow anything else (a panic,
    /// a host error), which keeps what it said.
    Try {
        body: Vec<Stmt>,
        var: String,
        caught: Expr,
        catch: Vec<Stmt>,
    },
    Return(Option<Expr>),
    /// A function declaration, with JSDoc for its parameters' types and
    /// its result's, if given, and its name's span and source name; a
    /// `function*` where it's a generator.
    Function {
        export: bool,
        generator: bool,
        name: String,
        name_span: Span,
        source_name: String,
        params: Vec<(String, String)>,
        result: Option<String>,
        body: Vec<Stmt>,
    },
    /// A class, which a Temper class or interface becomes, with its
    /// supertypes' mixin after `extends`, and its name's span and source
    /// name.
    Class {
        export: bool,
        name: String,
        name_span: Span,
        source_name: String,
        /// Its type parameters, as JSDoc's `@template` takes them: `T`,
        /// or `{Named} T` with a bound.
        templates: Vec<String>,
        extends: Option<Expr>,
        members: Vec<Member>,
    },
    /// A test, as Node's test runner runs it, with no time limit:
    /// `it(name, async function () { … })`, making
    /// `test`, a new `class`, and running `body`, keeping what it threw
    /// in `failure`, then, even if it threw, `await endTest(test,
    /// failure)`, which finishes the async work it started and
    /// `test.softFailToHard()`, the body's error winning.
    Test {
        name: String,
        test: String,
        class: String,
        failure: String,
        body: Vec<Stmt>,
    },
}

impl Stmt {
    /// Exports the statement, if it declares `name`.
    pub fn export_named(&mut self, name: &str) {
        match self {
            Stmt::Spanned(_, s) => s.export_named(name),
            Stmt::Decl {
                export, name: n, ..
            }
            | Stmt::Function {
                export, name: n, ..
            } if n == name => *export = true,
            _ => {}
        }
    }

    /// The statement with its span, if it isn't unknown.
    pub fn spanned(self, span: Span) -> Stmt {
        match self {
            Stmt::Spanned(..) => self,
            _ if !span.is_known() => self,
            _ => Stmt::Spanned(span, Box::new(self)),
        }
    }

    /// The statement without its span.
    pub fn bare(&self) -> &Stmt {
        match self {
            Stmt::Spanned(_, s) => s.bare(),
            _ => self,
        }
    }
}

/// What a method of a class is.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum MethodKind {
    Method,
    Getter,
    Setter,
    Constructor,
}

/// A member of a class, with its source span.
#[derive(Clone, Debug, PartialEq)]
pub enum Member {
    /// `/** @type {T} */ name;`, `static` first if `is_static`, with
    /// `= value` if given.
    Field {
        span: Span,
        is_static: bool,
        name: String,
        ty: String,
        value: Option<Expr>,
    },
    /// A method, getter, setter, or constructor, with JSDoc for its
    /// parameters' and result's types, as a function has.
    Method {
        span: Span,
        is_static: bool,
        kind: MethodKind,
        name: String,
        name_span: Span,
        source_name: String,
        params: Vec<(String, String)>,
        result: Option<String>,
        body: Vec<Stmt>,
    },
}

/// One `.js` file: imports from `@temperlang/core`, then its body.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Module {
    /// A namespace import first, its name and specifier: host code's,
    /// `import * as _connected from "./_connected.js";`.
    pub namespace: Option<(String, String)>,
    /// Names imported from the runtime.
    pub imports: Vec<String>,
    /// Names imported from other modules, by specifier, in order.
    pub from: Vec<(String, Vec<String>)>,
    pub body: Vec<Stmt>,
}

/// The runtime's module, as generated code imports it.
pub const RUNTIME: &str = "@temperlang/core";

/// Node's test runner, whose `it` tests files import, which Bun and Deno
/// run too, with `describe`, `it`, and `test`, if not all of its API.
pub const TEST_MODULE: &str = "node:test";

fn escape_into(out: &mut String, text: &str, quote: char) {
    let mut chars = text.chars().peekable();
    while let Some(c) = chars.next() {
        match c {
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '$' if quote == '`' && chars.peek() == Some(&'{') => out.push_str("\\$"),
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

/// Prints a string literal, in double quotes unless the text has more of
/// them than of single quotes, as Prettier chooses.
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
        "NaN".into()
    } else if f.is_infinite() {
        if f > 0.0 {
            "Infinity".into()
        } else {
            "-Infinity".into()
        }
    } else {
        // Rust's `{:?}` gives `1.0`, `0.1`, and `1e100`, all JS too.
        format!("{f:?}")
    }
}

/// Whether a binary operand needs parentheses beyond what precedence
/// says, as Prettier adds them.
fn clarify(parent: Bin, child: &Expr, right: bool) -> bool {
    let Expr::Bin(op, ..) = child else {
        return false;
    };
    let (pp, cp) = (parent.prec(), op.prec());
    if parent.bitwise() {
        return true;
    }
    // `??` mixed with `&&` or `||` must be parenthesized.
    if (parent == Bin::Coalesce) != (*op == Bin::Coalesce)
        && matches!(parent, Bin::Coalesce | Bin::Or | Bin::And)
        && matches!(op, Bin::Coalesce | Bin::Or | Bin::And)
    {
        return true;
    }
    if pp == cp {
        // Same level: left-associative chains only of operators that
        // flatten (`a + b - c`, `a * b / c`), never `**` or `%` mixed in.
        return right
            || parent == Bin::Pow
            || matches!(parent, Bin::Eq | Bin::Ne | Bin::LooseEq | Bin::LooseNe)
            || (parent != *op && (parent == Bin::Mod || *op == Bin::Mod))
            || (parent == Bin::Mul && *op == Bin::Div)
            || (parent == Bin::Div && *op == Bin::Mul);
    }
    (parent == Bin::Or && *op == Bin::And)
        || (*op == Bin::Mod && matches!(parent, Bin::Add | Bin::Sub))
}

/// Prints an expression, parenthesized if it binds looser than `min`.
pub fn expr(e: &Expr, min: u8) -> String {
    let text = match e {
        Expr::Name(n) => n.clone(),
        Expr::Int(n) => n.to_string(),
        Expr::Float(f) => float_literal(*f),
        Expr::BigInt(n) => format!("{n}n"),
        Expr::Str(s) => string_literal(s),
        Expr::Bool(b) => b.to_string(),
        Expr::Undefined => "undefined".into(),
        Expr::Null => "null".into(),
        Expr::Call(f, args) => {
            let args: Vec<String> = args.iter().map(|a| expr(a, ARROW)).collect();
            format!("{}({})", expr(f, CALL), args.join(", "))
        }
        Expr::Arrow(params, body) => format!("({}) => {}", params.join(", "), expr(body, ARROW)),
        Expr::Index(obj, index) => format!("{}[{}]", expr(obj, CALL), expr(index, TOP)),
        Expr::Assign(op, target, value) => {
            format!("{} {op} {}", expr(target, CALL), expr(value, ARROW))
        }
        Expr::Throw(e) => format!("(() => {{ throw {}; }})()", expr(e, TOP)),
        Expr::Yield(None) => "yield".into(),
        Expr::Yield(Some(value)) => format!("yield {}", expr(value, ARROW)),
        Expr::Member(obj, name) => {
            // A number literal's `.` would read as its decimal point.
            let o = match **obj {
                Expr::Int(_) | Expr::Float(_) | Expr::BigInt(_) => format!("({})", expr(obj, TOP)),
                _ => expr(obj, CALL),
            };
            format!("{o}.{name}")
        }
        Expr::Bin(op, left, right) => {
            let p = op.prec();
            let (lmin, rmin) = match op {
                // `-a ** b` is a syntax error: no unary on the left.
                Bin::Pow => (CALL, p),
                _ => (p, p + 1),
            };
            let side = |e: &Expr, min: u8, right: bool| {
                if clarify(*op, e, right) {
                    format!("({})", expr(e, TOP))
                } else {
                    expr(e, min)
                }
            };
            format!(
                "{} {} {}",
                side(left, lmin, false),
                op.text(),
                side(right, rmin, true)
            )
        }
        Expr::Unary(op, operand) => {
            let inner = expr(operand, UNARY);
            // `- -a` and `--a` read badly or mean something else.
            if inner.starts_with(['-', '+']) && *op == Unary::Neg {
                format!("-({inner})")
            } else {
                format!("{}{inner}", op.text())
            }
        }
        Expr::Cond { cond, then, els } => format!(
            "{} ? {} : {}",
            expr(cond, COND + 1),
            expr(then, COND),
            expr(els, COND)
        ),
        Expr::Template(parts) => {
            let mut out = String::from("`");
            for part in parts {
                match part {
                    TPart::Lit(s) => escape_into(&mut out, s, '`'),
                    TPart::Expr(e) => {
                        out.push_str("${");
                        out.push_str(&expr(e, TOP));
                        out.push('}');
                    }
                }
            }
            out.push('`');
            out
        }
        Expr::This => "this".into(),
        Expr::New(class, args) => {
            let args: Vec<String> = args.iter().map(|a| expr(a, COND)).collect();
            format!("new {}({})", expr(class, ATOM), args.join(", "))
        }
        Expr::Cast(ty, e) => format!("/** @type {{{ty}}} */ ({})", expr(e, TOP)),
        Expr::Array(items) => {
            let items: Vec<String> = items.iter().map(|i| expr(i, COND)).collect();
            format!("[{}]", items.join(", "))
        }
    };
    if e.prec() < min {
        format!("({text})")
    } else {
        text
    }
}

/// Prints a module.
pub fn module(m: &Module) -> String {
    module_mapped(m).text
}

/// Prints a module, marking where each statement with a span starts.
pub fn module_mapped(m: &Module) -> MappedText {
    let mut out = MappedText::new();
    if let Some((name, from)) = &m.namespace {
        let _ = writeln!(out, "import * as {name} from \"{from}\";");
    }
    let runtime = (!m.imports.is_empty()).then(|| (RUNTIME.to_string(), m.imports.clone()));
    for (from, names) in runtime.iter().chain(&m.from) {
        let line = format!("import {{ {} }} from \"{from}\";", names.join(", "));
        if line.len() <= WIDTH {
            let _ = writeln!(out, "{line}");
        } else {
            out.push_str("import {\n");
            for name in names {
                let _ = writeln!(out, "  {name},");
            }
            let _ = writeln!(out, "}} from \"{from}\";");
        }
    }
    let imported = m.namespace.is_some() || !m.imports.is_empty() || !m.from.is_empty();
    let mut previous_function = None;
    for (i, s) in m.body.iter().enumerate() {
        let function = matches!(
            s.bare(),
            Stmt::Function { .. } | Stmt::Test { .. } | Stmt::Class { .. }
        );
        let blank = if i == 0 {
            imported
        } else {
            function || previous_function == Some(true)
        };
        if blank {
            out.push('\n');
        }
        stmt(&mut out, s, 0);
        previous_function = Some(function);
    }
    out
}

fn indent(out: &mut MappedText, depth: usize) {
    for _ in 0..depth {
        out.push_str("  ");
    }
}

fn block(out: &mut MappedText, body: &[Stmt], depth: usize) {
    out.push_str("{\n");
    for s in body {
        stmt(out, s, depth + 1);
    }
    indent(out, depth);
    out.push('}');
}

fn label(out: &mut MappedText, label: &Option<String>) {
    if let Some(l) = label {
        let _ = write!(out, "{l}: ");
    }
}

fn stmt(out: &mut MappedText, s: &Stmt, depth: usize) {
    indent(out, depth);
    stmt_at(out, s, depth, None);
}

/// Prints a statement after its indent; `span` is its own, if any, to
/// mark again where a line of JSDoc comes first.
fn stmt_at(out: &mut MappedText, s: &Stmt, depth: usize, span: Option<Span>) {
    let remark = |out: &mut MappedText| {
        if let Some(span) = span {
            out.mark(span);
        }
    };
    match s {
        Stmt::Spanned(span, s) => {
            out.mark(*span);
            stmt_at(out, s, depth, Some(*span));
        }
        // A declared error's `throw`, where code after it is never run.
        Stmt::Expr(Expr::Throw(e))
        | Stmt::Assign {
            value: Expr::Throw(e),
            ..
        }
        | Stmt::Return(Some(Expr::Throw(e))) => {
            let _ = writeln!(out, "throw {};", expr(e, TOP));
        }
        Stmt::Expr(e) => {
            // An expression statement can't start with `{` or `function`;
            // none of these do.
            let _ = writeln!(out, "{};", expr(e, TOP));
        }
        Stmt::Block(body) => {
            block(out, body, depth);
            out.push('\n');
        }
        Stmt::Decl {
            export,
            keyword,
            name,
            ty,
            value,
        } => {
            if let Some(ty) = ty {
                let _ = writeln!(out, "/** @type {{{ty}}} */");
                indent(out, depth);
                remark(out);
            }
            if *export {
                out.push_str("export ");
            }
            out.push_str(match keyword {
                Keyword::Const => "const ",
                Keyword::Let => "let ",
            });
            out.push_str(name);
            if let Some(v) = value {
                let _ = write!(out, " = {}", expr(v, ARROW));
            }
            out.push_str(";\n");
        }
        Stmt::Assign { target, value } => {
            let _ = writeln!(out, "{} = {};", expr(target, CALL), expr(value, ARROW));
        }
        Stmt::AssignCoalesce { target, value } => {
            let _ = writeln!(out, "{} ??= {};", expr(target, CALL), expr(value, ARROW));
        }
        Stmt::If { cond, then, els } => {
            let _ = write!(out, "if ({}) ", expr(cond, TOP));
            block(out, then, depth);
            let mut els = els;
            loop {
                match els.as_slice() {
                    [] => break,
                    [only] if matches!(only.bare(), Stmt::If { .. }) => {
                        let Stmt::If {
                            cond,
                            then,
                            els: next,
                        } = only.bare()
                        else {
                            unreachable!()
                        };
                        out.push_str(" else ");
                        if let Stmt::Spanned(span, _) = only {
                            out.mark(*span);
                        }
                        let _ = write!(out, "if ({}) ", expr(cond, TOP));
                        block(out, then, depth);
                        els = next;
                    }
                    _ => {
                        out.push_str(" else ");
                        block(out, els, depth);
                        break;
                    }
                }
            }
            out.push('\n');
        }
        Stmt::While {
            label: l,
            cond,
            body,
        } => {
            label(out, l);
            let _ = write!(out, "while ({}) ", expr(cond, TOP));
            block(out, body, depth);
            out.push('\n');
        }
        Stmt::DoWhile {
            label: l,
            body,
            cond,
        } => {
            label(out, l);
            out.push_str("do ");
            block(out, body, depth);
            let _ = writeln!(out, " while ({});", expr(cond, TOP));
        }
        Stmt::Try {
            body,
            var,
            caught,
            catch,
        } => {
            out.push_str("try ");
            block(out, body, depth);
            let _ = writeln!(out, " catch ({var}) {{");
            indent(out, depth + 1);
            let caught = expr(&Expr::unary(Unary::Not, caught.clone()), TOP);
            let _ = writeln!(out, "if ({caught}) throw {var};");
            for s in catch {
                stmt(out, s, depth + 1);
            }
            indent(out, depth);
            out.push_str("}\n");
        }
        Stmt::Break(l) | Stmt::Continue(l) => {
            out.push_str(if matches!(s, Stmt::Break(_)) {
                "break"
            } else {
                "continue"
            });
            if let Some(l) = l {
                let _ = write!(out, " {l}");
            }
            out.push_str(";\n");
        }
        Stmt::Return(None) => out.push_str("return;\n"),
        Stmt::Return(Some(e)) => {
            let _ = writeln!(out, "return {};", expr(e, TOP));
        }
        Stmt::Function {
            export,
            generator,
            name,
            name_span,
            source_name,
            params,
            result,
            body,
        } => {
            jsdoc(out, depth, params, result, span);
            if *export {
                out.push_str("export ");
            }
            out.push_str(if *generator {
                "function* "
            } else {
                "function "
            });
            out.mark_name(*name_span, source_name);
            out.push_str(name);
            remark(out);
            let names: Vec<&str> = (params.iter())
                .map(|(n, _)| param_name(n))
                .filter(|n| !matches!(*n, "@template" | "@throws"))
                .collect();
            let _ = write!(out, "({}) ", names.join(", "));
            block(out, body, depth);
            out.push('\n');
        }
        Stmt::Class {
            export,
            name,
            name_span,
            source_name,
            templates,
            extends,
            members,
        } => {
            match templates.as_slice() {
                [] => {}
                [one] => {
                    let _ = writeln!(out, "/** @template {one} */");
                    indent(out, depth);
                }
                _ => {
                    out.push_str("/**\n");
                    for t in templates {
                        indent(out, depth);
                        let _ = writeln!(out, " * @template {t}");
                    }
                    indent(out, depth);
                    out.push_str(" */\n");
                    indent(out, depth);
                }
            }
            if *export {
                out.push_str("export ");
            }
            out.push_str("class ");
            out.mark_name(*name_span, source_name);
            out.push_str(name);
            remark(out);
            if let Some(e) = extends {
                let _ = write!(out, " extends {}", expr(e, CALL));
            }
            if members.is_empty() {
                out.push_str(" {}\n");
                return;
            }
            out.push_str(" {\n");
            let mut previous_method = None;
            for m in members {
                let method = matches!(m, Member::Method { .. });
                if previous_method.is_some() && (method || previous_method == Some(true)) {
                    out.push('\n');
                }
                member(out, m, depth + 1);
                previous_method = Some(method);
            }
            indent(out, depth);
            out.push_str("}\n");
        }
        Stmt::Test {
            name,
            test,
            class,
            failure,
            body,
        } => {
            let _ = writeln!(out, "it({}, async function () {{", string_literal(name));
            indent(out, depth + 1);
            let _ = writeln!(out, "const {test} = new {class}();");
            indent(out, depth + 1);
            let _ = writeln!(out, "let {failure};");
            indent(out, depth + 1);
            out.push_str("try ");
            block(out, body, depth + 1);
            out.push_str(" catch (e) {\n");
            indent(out, depth + 2);
            let _ = writeln!(out, "{failure} = e;");
            indent(out, depth + 2);
            out.push_str("throw e;\n");
            indent(out, depth + 1);
            out.push_str("} finally {\n");
            indent(out, depth + 2);
            let _ = writeln!(out, "await endTest({test}, {failure});");
            indent(out, depth + 1);
            out.push_str("}\n");
            indent(out, depth);
            out.push_str("});\n");
        }
    }
}

/// A parameter's name, without the brackets JSDoc puts around an optional
/// one's (`[x]`).
fn param_name(n: &str) -> &str {
    n.strip_prefix('[')
        .and_then(|n| n.strip_suffix(']'))
        .unwrap_or(n)
}

/// JSDoc for a function's or method's parameters and result, if any,
/// then the indent, with the statement's span marked again after it.
fn jsdoc(
    out: &mut MappedText,
    depth: usize,
    params: &[(String, String)],
    result: &Option<String>,
    span: Option<Span>,
) {
    // A type parameter is a parameter named `@template`, first, and the
    // declared error it throws one named `@throws`, last.
    let mut tags: Vec<String> = params
        .iter()
        .filter(|(n, _)| n != "@throws")
        .map(|(n, t)| match n.as_str() {
            "@template" => format!("@template {t}"),
            _ => format!("@param {{{t}}} {n}"),
        })
        .collect();
    if let Some(r) = result {
        tags.push(format!("@returns {{{r}}}"));
    }
    for (_, t) in params.iter().filter(|(n, _)| n == "@throws") {
        tags.push(format!("@throws {{{t}}}"));
    }
    match tags.as_slice() {
        [] => return,
        [one] => {
            let _ = writeln!(out, "/** {one} */");
        }
        _ => {
            out.push_str("/**\n");
            for t in &tags {
                indent(out, depth);
                let _ = writeln!(out, " * {t}");
            }
            indent(out, depth);
            out.push_str(" */\n");
        }
    }
    indent(out, depth);
    if let Some(span) = span {
        out.mark(span);
    }
}

/// Prints a class's member, marking where it starts and its name.
fn member(out: &mut MappedText, m: &Member, depth: usize) {
    indent(out, depth);
    match m {
        Member::Field {
            span,
            is_static,
            name,
            ty,
            value,
        } => {
            out.mark(*span);
            let _ = writeln!(out, "/** @type {{{ty}}} */");
            indent(out, depth);
            out.mark(*span);
            if *is_static {
                out.push_str("static ");
            }
            out.push_str(name);
            if let Some(v) = value {
                let _ = write!(out, " = {}", expr(v, ARROW));
            }
            out.push_str(";\n");
        }
        Member::Method {
            span,
            is_static,
            kind,
            name,
            name_span,
            source_name,
            params,
            result,
            body,
        } => {
            out.mark(*span);
            jsdoc(out, depth, params, result, Some(*span));
            if *is_static {
                out.push_str("static ");
            }
            match kind {
                MethodKind::Getter => out.push_str("get "),
                MethodKind::Setter => out.push_str("set "),
                MethodKind::Method | MethodKind::Constructor => {}
            }
            out.mark_name(*name_span, source_name);
            out.push_str(name);
            let names: Vec<&str> = (params.iter())
                .map(|(n, _)| param_name(n))
                .filter(|n| !matches!(*n, "@template" | "@throws"))
                .collect();
            let _ = write!(out, "({}) ", names.join(", "));
            block(out, body, depth);
            out.push('\n');
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
            expr(&Expr::bin(Bin::BitOr, sum.clone(), Expr::Int(0)), 0),
            "(a + b) | 0"
        );
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
        let and = Expr::bin(Bin::And, n("a"), n("b"));
        assert_eq!(expr(&Expr::bin(Bin::Or, and, n("c")), 0), "(a && b) || c");
        assert_eq!(
            expr(&Expr::bin(Bin::Pow, Expr::Int(-2), n("x")), 0),
            "(-2) ** x"
        );
        assert_eq!(
            expr(&Expr::bin(Bin::Pow, n("x"), Expr::Int(-2)), 0),
            "x ** -2"
        );
        assert_eq!(expr(&Expr::unary(Unary::Neg, Expr::Int(-2)), 0), "-(-2)");
        assert_eq!(
            expr(&Expr::Int(5).method("toString", vec![]), 0),
            "(5).toString()"
        );
        let lt = Expr::bin(Bin::Lt, n("a"), n("b"));
        assert_eq!(expr(&Expr::unary(Unary::Not, lt), 0), "!(a < b)");
        let cond = Expr::Cond {
            cond: Box::new(n("c")),
            then: Box::new(n("a")),
            els: Box::new(n("b")),
        };
        assert_eq!(expr(&n("f").call(vec![cond.clone()]), 0), "f(c ? a : b)");
        assert_eq!(
            expr(&Expr::bin(Bin::Add, cond, n("d")), 0),
            "(c ? a : b) + d"
        );
    }

    #[test]
    fn classes() {
        let is = Expr::bin(Bin::InstanceOf, n("s"), n("Rect"));
        assert_eq!(
            expr(&Expr::unary(Unary::Not, is), 0),
            "!(s instanceof Rect)"
        );
        let new = Expr::New(Box::new(n("Rect")), vec![Expr::Int(1)]);
        assert_eq!(expr(&new.clone().member("area"), 0), "new Rect(1).area");
        let cast = Expr::Cast("Shape".into(), Box::new(new));
        assert_eq!(
            expr(&cast.member("area"), 0),
            "/** @type {Shape} */ (new Rect(1)).area"
        );
    }

    #[test]
    fn strings() {
        assert_eq!(string_literal("it's"), "\"it's\"");
        assert_eq!(string_literal("say \"hi\""), "'say \"hi\"'");
        assert_eq!(string_literal("a\n\\\u{2028}"), "\"a\\n\\\\\\u2028\"");
        let t = Expr::Template(vec![TPart::Lit("`${x}` = ".into()), TPart::Expr(n("x"))]);
        assert_eq!(expr(&t, 0), "`\\`\\${x}\\` = ${x}`");
    }
}
