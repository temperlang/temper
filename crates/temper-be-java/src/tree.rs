//! Java's output tree and its printer.
//!
//! The printer lays code out as Kotlin Temper's Java output is laid out,
//! with four-space indents and braces on the same line, and with blank
//! lines between a class's members, as most Java style guides have it.
//! Parentheses go where precedence needs them, and where they help a
//! reader: around `&&` inside `||`, around a binary operand of a bitwise
//! operator or a shift, around `%` inside `+` or `-`, and around a sum or
//! difference inside a string concatenation (`"n=" + (a + b)`). It
//! doesn't break long lines.

use std::fmt::Write;

/// A binary operator.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Bin {
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
}

impl Bin {
    fn text(self) -> &'static str {
        match self {
            Bin::Or => "||",
            Bin::And => "&&",
            Bin::BitOr => "|",
            Bin::BitXor => "^",
            Bin::BitAnd => "&",
            Bin::Eq => "==",
            Bin::Ne => "!=",
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
        }
    }

    fn prec(self) -> u8 {
        match self {
            Bin::Or => OR,
            Bin::And => AND,
            Bin::BitOr => 5,
            Bin::BitXor => 6,
            Bin::BitAnd => 7,
            Bin::Eq | Bin::Ne => 8,
            Bin::Lt | Bin::Le | Bin::Gt | Bin::Ge => REL,
            Bin::Shl | Bin::Shr | Bin::Ushr => 10,
            Bin::Add | Bin::Sub => ADD,
            Bin::Mul | Bin::Div | Bin::Mod => 12,
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
const LAMBDA: u8 = 1;
const COND: u8 = 2;
const OR: u8 = 3;
const AND: u8 = 4;
const REL: u8 = 9;
const ADD: u8 = 11;
const UNARY: u8 = 14;
const CALL: u8 = 16;
const ATOM: u8 = 17;

#[derive(Clone, Debug, PartialEq)]
pub enum Expr {
    Name(String),
    /// An `int` literal: `Int32`'s.
    Int(i64),
    /// A `long` literal: `Int64`'s, `5L`.
    Long(i64),
    Float(f64),
    Str(String),
    Bool(bool),
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
    /// String concatenation, `a + b + c`, evaluated from the left; the
    /// translator sees that it concatenates from the first `+`.
    Concat(Vec<Expr>),
    /// `this`.
    This,
    /// `new Class(args)`.
    New(String, Vec<Expr>),
    /// `value instanceof Type`.
    InstanceOf(Box<Expr>, String),
    /// `(Type) value`.
    Cast(String, Box<Expr>),
    /// `(a, b) -> body`, a lambda whose body is an expression.
    Lambda(Vec<String>, Box<Expr>),
    /// `value::method`, a method reference: a function value as another
    /// functional interface.
    MethodRef(Box<Expr>, String),
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
            Expr::Int(n) | Expr::Long(n) if *n < 0 => UNARY,
            Expr::Float(f) if f.is_sign_negative() && !f.is_nan() => UNARY,
            Expr::Name(_)
            | Expr::Int(_)
            | Expr::Long(_)
            | Expr::Float(_)
            | Expr::Str(_)
            | Expr::Bool(_)
            | Expr::This => ATOM,
            Expr::Call(..) | Expr::Member(..) | Expr::New(..) => CALL,
            Expr::InstanceOf(..) => REL,
            Expr::Cast(..) => UNARY,
            Expr::Bin(op, ..) => op.prec(),
            Expr::Unary(..) => UNARY,
            Expr::Cond { .. } => COND,
            Expr::Concat(_) => ADD,
            Expr::Lambda(..) => LAMBDA,
            Expr::MethodRef(..) => CALL,
        }
    }

    /// Whether Java allows it as a statement: a call, or `new C(…)`
    /// whose constructor does something.
    pub fn is_statement(&self) -> bool {
        matches!(self, Expr::Call(..) | Expr::New(..))
    }
}

#[derive(Clone, Debug, PartialEq)]
pub enum Stmt {
    Expr(Expr),
    /// `Type name = (a, b) -> { … };`: a lambda with statements, declared
    /// before what uses it.
    LambdaLocal {
        ty: String,
        name: String,
        params: Vec<String>,
        body: Vec<Stmt>,
    },
    /// `class Name { Type field; }`: a local class, a cell for a local
    /// that lambdas capture and that's assigned.
    LocalClass {
        name: String,
        fields: Vec<(String, String)>,
    },
    /// `class Name { R f(A a) { … } … }`: a local class whose methods are
    /// a group of local functions that use each other or themselves.
    GroupClass {
        name: String,
        methods: Vec<Method>,
    },
    /// A block of its own, which scopes its locals.
    Block(Vec<Stmt>),
    /// `ty name = value;`, or `ty name;`.
    Local {
        ty: String,
        name: String,
        value: Option<Expr>,
    },
    /// `target = value;`, the target a name or a member.
    Assign {
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
    /// `switch (scrutinee) { case 0: … break; … default: … }`: each case's
    /// labels, none for the default, and its body, in braces where it
    /// declares locals, and ending with `break;` where it completes.
    Switch {
        scrutinee: Expr,
        cases: Vec<(Vec<Expr>, Vec<Stmt>)>,
    },
    Break(Option<String>),
    Continue(Option<String>),
    Return(Option<Expr>),
    Throw(Expr),
    /// `synchronized (lock) { … }`.
    Synchronized {
        lock: Expr,
        body: Vec<Stmt>,
    },
    /// `try { … } finally { … }`.
    Try {
        body: Vec<Stmt>,
        finally: Vec<Stmt>,
    },
    /// `try { … } catch (RuntimeException var) { … }`: Temper's `orelse`,
    /// a bubble being a `RuntimeException`.
    TryCatch {
        body: Vec<Stmt>,
        var: String,
        handler: Vec<Stmt>,
    },
}

/// A static field, declared without a value, as Kotlin's are: the static
/// initializer gives it one.
#[derive(Clone, Debug, PartialEq)]
pub struct Field {
    pub public: bool,
    pub is_final: bool,
    pub volatile: bool,
    pub ty: String,
    pub name: String,
}

/// A static method.
#[derive(Clone, Debug, PartialEq)]
pub struct Method {
    pub public: bool,
    pub result: String,
    pub name: String,
    /// Each parameter's type and name.
    pub params: Vec<(String, String)>,
    /// Checked exceptions it may throw, after `throws`.
    pub throws: Vec<String>,
    pub body: Vec<Stmt>,
}

/// One `.java` file: a package, its imports, and one final class of
/// static members, which no one can instantiate.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Class {
    pub package: String,
    pub imports: Vec<String>,
    pub name: String,
    pub fields: Vec<Field>,
    pub methods: Vec<Method>,
    /// Private helpers, as their text, each indented as a member.
    pub helpers: Vec<String>,
    /// The static initializer's statements: none, no initializer.
    pub init: Vec<Stmt>,
}

/// A member's access, as Java writes it: `public`, none (the package's),
/// or `private`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Vis {
    Public,
    Package,
    Private,
}

impl Vis {
    fn text(self) -> &'static str {
        match self {
            Vis::Public => "public ",
            Vis::Package => "",
            Vis::Private => "private ",
        }
    }
}

/// A field of a class or interface: an instance field, or a static one,
/// with its value where the class's own initializer gives it one. An
/// interface's are constants, `public static final` without saying so.
#[derive(Clone, Debug, PartialEq)]
pub struct MemberField {
    pub vis: Vis,
    pub is_static: bool,
    pub is_final: bool,
    pub ty: String,
    pub name: String,
    pub value: Option<Expr>,
}

/// What kind of method a member is.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum MemberKind {
    /// A constructor, named for its class, without a result.
    Constructor,
    /// An instance method of a class.
    Instance,
    Static,
    /// An interface's method with a body.
    Default,
    /// An interface's method without one.
    Abstract,
}

/// A method of a class or interface.
#[derive(Clone, Debug, PartialEq)]
pub struct Member {
    pub vis: Vis,
    pub kind: MemberKind,
    pub result: String,
    pub name: String,
    pub params: Vec<(String, String)>,
    pub body: Vec<Stmt>,
}

/// One `.java` file of a class or interface: a final class (classes are
/// final in Temper) or an interface, `public` if exported, else its
/// package's.
#[derive(Clone, Debug, PartialEq)]
pub struct TypeDecl {
    pub package: String,
    pub imports: Vec<String>,
    pub public: bool,
    pub interface: bool,
    pub name: String,
    /// Its type parameters, as `<T extends Named>`, or empty.
    pub generics: String,
    /// What it implements, or for an interface, extends.
    pub supers: Vec<String>,
    pub fields: Vec<MemberField>,
    pub members: Vec<Member>,
    /// Nested classes and helpers, as their text, each indented as a
    /// member.
    pub helpers: Vec<String>,
}

/// Prints a class's or interface's file.
pub fn type_decl(t: &TypeDecl) -> String {
    let mut out = String::new();
    let _ = writeln!(out, "package {};", t.package);
    if !t.imports.is_empty() {
        out.push('\n');
        for import in &t.imports {
            let _ = writeln!(out, "import {import};");
        }
    }
    out.push('\n');
    if t.public {
        out.push_str("public ");
    }
    let (keyword, supers) = if t.interface {
        ("interface", "extends")
    } else {
        ("final class", "implements")
    };
    let _ = write!(out, "{keyword} {}{}", t.name, t.generics);
    if !t.supers.is_empty() {
        let _ = write!(out, " {supers} {}", t.supers.join(", "));
    }
    out.push_str(" {\n");
    let mut first = true;
    let mut gap = |out: &mut String| {
        if !first {
            out.push('\n');
        }
        first = false;
    };
    if !t.fields.is_empty() {
        gap(&mut out);
    }
    for f in &t.fields {
        out.push_str("    ");
        if !t.interface {
            out.push_str(f.vis.text());
            if f.is_static {
                out.push_str("static ");
            }
            if f.is_final {
                out.push_str("final ");
            }
        }
        let _ = write!(out, "{} {}", f.ty, f.name);
        if let Some(v) = &f.value {
            let _ = write!(out, " = {}", expr(v, COND));
        }
        out.push_str(";\n");
    }
    for m in &t.members {
        gap(&mut out);
        out.push_str("    ");
        // An interface's members are public without saying so.
        if !t.interface || m.vis == Vis::Private {
            out.push_str(m.vis.text());
        }
        let params: Vec<String> = m.params.iter().map(|(t, n)| format!("{t} {n}")).collect();
        let params = params.join(", ");
        match m.kind {
            MemberKind::Constructor => {
                let _ = write!(out, "{}({params}) ", t.name);
            }
            MemberKind::Abstract => {
                let _ = writeln!(out, "{} {}({params});", m.result, m.name);
                continue;
            }
            kind => {
                let modifier = match kind {
                    MemberKind::Static => "static ",
                    MemberKind::Default => "default ",
                    _ => "",
                };
                let _ = write!(out, "{modifier}{} {}({params}) ", m.result, m.name);
            }
        }
        if m.body.is_empty() {
            out.push_str("{}\n");
            continue;
        }
        block(&mut out, &m.body, 1);
        out.push('\n');
    }
    for h in &t.helpers {
        gap(&mut out);
        out.push_str(h);
    }
    out.push_str("}\n");
    out
}

fn escape_into(out: &mut String, text: &str, quote: char) {
    for c in text.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '\u{8}' => out.push_str("\\b"),
            '\u{c}' => out.push_str("\\f"),
            _ if c == quote => {
                out.push('\\');
                out.push(c);
            }
            // Never `\u000a` or `\u000d`, which Java reads as line breaks
            // before it reads the literal; those are `\n` and `\r` above.
            _ if (c as u32) < 0x20 || (0x7f..0xa0).contains(&(c as u32)) => {
                let _ = write!(out, "\\u{:04x}", c as u32);
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

/// Prints a string literal.
pub fn string_literal(text: &str) -> String {
    let mut out = String::from("\"");
    escape_into(&mut out, text, '"');
    out.push('"');
    out
}

fn float_literal(f: f64) -> String {
    if f.is_nan() {
        "Double.NaN".into()
    } else if f.is_infinite() {
        if f > 0.0 {
            "Double.POSITIVE_INFINITY".into()
        } else {
            "Double.NEGATIVE_INFINITY".into()
        }
    } else {
        // Rust's `{:?}` gives `1.0`, `0.1`, and `1e100`, all Java's too.
        format!("{f:?}")
    }
}

/// Whether a binary operand needs parentheses beyond what precedence
/// says, for clarity.
fn clarify(parent: Bin, child: &Expr, right: bool) -> bool {
    let Expr::Bin(op, ..) = child else {
        return false;
    };
    let (pp, cp) = (parent.prec(), op.prec());
    if parent.bitwise() {
        return true;
    }
    if pp == cp {
        // Same level: left-associative chains only of operators that
        // flatten (`a + b - c`, `a * b / c`), never `%` mixed in.
        return right
            || matches!(parent, Bin::Eq | Bin::Ne)
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
        Expr::Long(n) => format!("{n}L"),
        Expr::Float(f) => float_literal(*f),
        Expr::Str(s) => string_literal(s),
        Expr::Bool(b) => b.to_string(),
        Expr::Call(f, args) => {
            let args: Vec<String> = args.iter().map(|a| expr(a, LAMBDA)).collect();
            format!("{}({})", expr(f, CALL), args.join(", "))
        }
        Expr::Lambda(params, body) => {
            let head = match params.as_slice() {
                [one] => one.clone(),
                _ => format!("({})", params.join(", ")),
            };
            format!("{head} -> {}", expr(body, LAMBDA))
        }
        Expr::MethodRef(value, method) => format!("{}::{method}", expr(value, CALL)),
        Expr::Member(obj, name) => format!("{}.{name}", expr(obj, CALL)),
        Expr::Bin(op, left, right) => {
            let p = op.prec();
            let side = |e: &Expr, min: u8, right: bool| {
                if clarify(*op, e, right) {
                    format!("({})", expr(e, TOP))
                } else {
                    expr(e, min)
                }
            };
            format!(
                "{} {} {}",
                side(left, p, false),
                op.text(),
                side(right, p + 1, true)
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
            expr(then, COND + 1),
            expr(els, COND)
        ),
        Expr::Concat(parts) => {
            let parts: Vec<String> = parts.iter().map(|p| expr(p, ADD + 1)).collect();
            parts.join(" + ")
        }
        Expr::This => "this".into(),
        Expr::New(class, args) => {
            let args: Vec<String> = args.iter().map(|a| expr(a, LAMBDA)).collect();
            format!("new {class}({})", args.join(", "))
        }
        Expr::InstanceOf(value, ty) => format!("{} instanceof {ty}", expr(value, REL)),
        // A cast binds tighter than any binary operator, so its operand
        // is a unary expression or tighter.
        Expr::Cast(ty, value) => format!("({ty}) {}", expr(value, UNARY)),
    };
    if e.prec() < min {
        format!("({text})")
    } else {
        text
    }
}

fn indent(out: &mut String, depth: usize) {
    for _ in 0..depth {
        out.push_str("    ");
    }
}

pub(crate) fn block(out: &mut String, body: &[Stmt], depth: usize) {
    out.push_str("{\n");
    for s in body {
        stmt(out, s, depth + 1);
    }
    indent(out, depth);
    out.push('}');
}

fn label(out: &mut String, label: &Option<String>) {
    if let Some(l) = label {
        let _ = write!(out, "{l}: ");
    }
}

fn stmt(out: &mut String, s: &Stmt, depth: usize) {
    indent(out, depth);
    match s {
        Stmt::Expr(e) => {
            let _ = writeln!(out, "{};", expr(e, TOP));
        }
        Stmt::Block(body) => {
            block(out, body, depth);
            out.push('\n');
        }
        Stmt::Local { ty, name, value } => {
            let _ = write!(out, "{ty} {name}");
            if let Some(v) = value {
                let _ = write!(out, " = {}", expr(v, LAMBDA));
            }
            out.push_str(";\n");
        }
        Stmt::Assign { target, value } => {
            let _ = writeln!(out, "{} = {};", expr(target, CALL), expr(value, LAMBDA));
        }
        Stmt::LambdaLocal {
            ty,
            name,
            params,
            body,
        } => {
            let head = match params.as_slice() {
                [one] => one.clone(),
                _ => format!("({})", params.join(", ")),
            };
            let _ = write!(out, "{ty} {name} = {head} -> ");
            block(out, body, depth);
            out.push_str(";\n");
        }
        Stmt::GroupClass { name, methods } => {
            let _ = writeln!(out, "class {name} {{");
            for (i, m) in methods.iter().enumerate() {
                if i > 0 {
                    out.push('\n');
                }
                indent(out, depth + 1);
                let params: Vec<String> =
                    m.params.iter().map(|(t, n)| format!("{t} {n}")).collect();
                let _ = write!(out, "{} {}({}) ", m.result, m.name, params.join(", "));
                block(out, &m.body, depth + 1);
                out.push('\n');
            }
            indent(out, depth);
            out.push_str("}\n");
        }
        Stmt::LocalClass { name, fields } => {
            let _ = writeln!(out, "class {name} {{");
            for (ty, field) in fields {
                indent(out, depth + 1);
                let _ = writeln!(out, "{ty} {field};");
            }
            indent(out, depth);
            out.push_str("}\n");
        }
        Stmt::If { cond, then, els } => {
            let _ = write!(out, "if ({}) ", expr(cond, TOP));
            block(out, then, depth);
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
                        let _ = write!(out, " else if ({}) ", expr(cond, TOP));
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
        Stmt::Switch { scrutinee, cases } => {
            let _ = writeln!(out, "switch ({}) {{", expr(scrutinee, TOP));
            for (labels, stmts) in cases {
                for (i, l) in labels.iter().enumerate() {
                    if i > 0 {
                        out.push('\n');
                    }
                    indent(out, depth + 1);
                    let _ = write!(out, "case {}:", expr(l, TOP));
                }
                if labels.is_empty() {
                    indent(out, depth + 1);
                    out.push_str("default:");
                }
                let mut stmts = stmts.clone();
                if stmts.iter().all(crate::translate::completes) {
                    stmts.push(Stmt::Break(None));
                }
                let declares = stmts.iter().any(|s| {
                    matches!(
                        s,
                        Stmt::Local { .. }
                            | Stmt::LambdaLocal { .. }
                            | Stmt::LocalClass { .. }
                            | Stmt::GroupClass { .. }
                    )
                });
                if declares {
                    out.push(' ');
                    block(out, &stmts, depth + 1);
                    out.push('\n');
                } else {
                    out.push('\n');
                    for s in &stmts {
                        stmt(out, s, depth + 2);
                    }
                }
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
        Stmt::Throw(e) => {
            let _ = writeln!(out, "throw {};", expr(e, TOP));
        }
        Stmt::Synchronized { lock, body } => {
            let _ = write!(out, "synchronized ({}) ", expr(lock, TOP));
            block(out, body, depth);
            out.push('\n');
        }
        Stmt::Try { body, finally } => {
            out.push_str("try ");
            block(out, body, depth);
            out.push_str(" finally ");
            block(out, finally, depth);
            out.push('\n');
        }
        Stmt::TryCatch { body, var, handler } => {
            out.push_str("try ");
            block(out, body, depth);
            let _ = write!(out, " catch (RuntimeException {var}) ");
            block(out, handler, depth);
            out.push('\n');
        }
    }
}

/// A module's test class: what only its tests reach, as statics, and
/// its tests.
#[derive(Clone, Debug, PartialEq)]
pub struct TestClass {
    /// Its package, imports, name, and statics.
    pub statics: Class,
    /// Each test's method name and body.
    pub tests: Vec<(String, Vec<Stmt>)>,
}

/// Prints a test class's file: as a class of statics, but that JUnit
/// makes one for each test, which runs as a method.
pub fn test_class(c: &TestClass) -> String {
    let mut out = header(&c.statics);
    let _ = writeln!(out, "public final class {} {{", c.statics.name);
    let mut first = statics(&mut out, &c.statics, true);
    for (name, body) in &c.tests {
        if !first {
            out.push('\n');
        }
        first = false;
        let _ = write!(
            out,
            "    @org.junit.jupiter.api.Test\n    public void {name}() "
        );
        block(&mut out, body, 1);
        out.push('\n');
    }
    out.push_str("}\n");
    out
}

/// A file's package and imports, then a blank line.
fn header(c: &Class) -> String {
    let mut out = String::new();
    let _ = writeln!(out, "package {};", c.package);
    if !c.imports.is_empty() {
        out.push('\n');
        for import in &c.imports {
            let _ = writeln!(out, "import {import};");
        }
    }
    out.push('\n');
    out
}

/// Prints a class's file.
pub fn class(c: &Class) -> String {
    let mut out = header(c);
    let name = &c.name;
    let _ = writeln!(out, "public final class {name} {{");
    let _ = writeln!(out, "    private {name}() {{}}");
    statics(&mut out, c, false);
    out.push_str("}\n");
    out
}

/// A class's statics, as members, each after a blank line but where
/// nothing comes before it (`first`); whether still nothing has.
fn statics(out: &mut String, c: &Class, mut first: bool) -> bool {
    if !c.fields.is_empty() && !first {
        out.push('\n');
    }
    for f in &c.fields {
        out.push_str("    ");
        if f.public {
            out.push_str("public ");
        }
        out.push_str("static ");
        if f.is_final {
            out.push_str("final ");
        }
        if f.volatile {
            out.push_str("volatile ");
        }
        let _ = writeln!(out, "{} {};", f.ty, f.name);
        first = false;
    }
    for m in &c.methods {
        if !first {
            out.push('\n');
        }
        out.push_str("    ");
        if m.public {
            out.push_str("public ");
        }
        let params: Vec<String> = m.params.iter().map(|(t, n)| format!("{t} {n}")).collect();
        let _ = write!(
            out,
            "static {} {}({}) ",
            m.result,
            m.name,
            params.join(", ")
        );
        if !m.throws.is_empty() {
            let _ = write!(out, "throws {} ", m.throws.join(", "));
        }
        block(out, &m.body, 1);
        out.push('\n');
        first = false;
    }
    for h in &c.helpers {
        if !first {
            out.push('\n');
        }
        out.push_str(h);
        first = false;
    }
    if !c.init.is_empty() {
        if !first {
            out.push('\n');
        }
        out.push_str("    static ");
        block(out, &c.init, 1);
        out.push('\n');
        first = false;
    }
    first
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
            expr(&Expr::bin(Bin::Shr, sum.clone(), Expr::Int(1)), 0),
            "(a + b) >> 1"
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
        assert_eq!(expr(&Expr::unary(Unary::Neg, Expr::Int(-2)), 0), "-(-2)");
        let lt = Expr::bin(Bin::Lt, n("a"), n("b"));
        assert_eq!(expr(&Expr::unary(Unary::Not, lt), 0), "!(a < b)");
        let cat = Expr::Concat(vec![
            Expr::Str("n=".into()),
            sum.clone(),
            Expr::bin(Bin::Mul, n("a"), n("b")),
        ]);
        assert_eq!(expr(&cat, 0), "\"n=\" + (a + b) + a * b");
        let cond = Expr::Cond {
            cond: Box::new(n("c")),
            then: Box::new(n("a")),
            els: Box::new(n("b")),
        };
        assert_eq!(expr(&n("f").call(vec![cond.clone()]), 0), "f(c ? a : b)");
        assert_eq!(
            expr(&Expr::Concat(vec![Expr::Str("x".into()), cond]), 0),
            "\"x\" + (c ? a : b)"
        );
        assert_eq!(expr(&Expr::Long(-5), 0), "-5L");
    }

    #[test]
    fn strings() {
        assert_eq!(string_literal("it's"), "\"it's\"");
        assert_eq!(string_literal("say \"hi\""), "\"say \\\"hi\\\"\"");
        assert_eq!(
            string_literal("a\n\\\u{2028}\u{1}"),
            "\"a\\n\\\\\\u2028\\u0001\""
        );
        assert_eq!(string_literal("\\u0041"), "\"\\\\u0041\"");
    }
}
