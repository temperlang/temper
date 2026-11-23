//! C++'s output tree and its printer.
//!
//! The printer lays code out as Google's C++ style does, which Kotlin
//! Temper's C++ tours follow: two-space indents, braces on the line that
//! opens them (`} else {`), a namespace's members not indented, and blank
//! lines between functions. Parentheses go where precedence needs them,
//! and where they help a reader, as in the other backends: around `&&`
//! inside `||`, around a binary operand of a bitwise operator, and around
//! `%` inside `+` or `-`. It doesn't break long lines.

use std::fmt::Write;
use temper_tmpl::line_directives::{LineAt, Presumed, Printed};

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
            Bin::Lt | Bin::Le | Bin::Gt | Bin::Ge => 9,
            Bin::Shl | Bin::Shr => 10,
            Bin::Add | Bin::Sub => ADD,
            Bin::Mul | Bin::Div | Bin::Mod => 12,
        }
    }

    fn bitwise(self) -> bool {
        matches!(
            self,
            Bin::BitOr | Bin::BitXor | Bin::BitAnd | Bin::Shl | Bin::Shr
        )
    }

    /// Whether it compares.
    pub fn comparison(self) -> bool {
        matches!(
            self,
            Bin::Eq | Bin::Ne | Bin::Lt | Bin::Le | Bin::Gt | Bin::Ge
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
const COND: u8 = 2;
const OR: u8 = 3;
const AND: u8 = 4;
const ADD: u8 = 11;
const UNARY: u8 = 14;
const CALL: u8 = 16;
const ATOM: u8 = 17;

#[derive(Clone, Debug, PartialEq)]
pub enum Expr {
    /// A name, maybe qualified (`core::Int::add`) or with template
    /// arguments.
    Name(String),
    /// An `int32_t` literal, `INT32_MIN` for the one C++ can't write as a
    /// literal.
    Int(i32),
    /// An `int64_t` literal: `int64_t{5}`, or `INT64_MIN`.
    Long(i64),
    Float(f64),
    /// A string literal: a `TEMPER_STR("…")`, the runtime's `core::Str`
    /// made once, on first use, or `core::Str()` for the empty string,
    /// as Kotlin's; in a concatenation, the bare `"…"`.
    Str(String),
    Bool(bool),
    Call(Box<Expr>, Vec<Expr>),
    /// A member of an object: `s.empty`.
    Member(Box<Expr>, String),
    /// A member of an object through a pointer: `rect->w`.
    Arrow(Box<Expr>, String),
    Bin(Bin, Box<Expr>, Box<Expr>),
    Unary(Unary, Box<Expr>),
    /// `cond ? then : els`.
    Cond {
        cond: Box<Expr>,
        then: Box<Expr>,
        els: Box<Expr>,
    },
    /// String concatenation of strings (`core::Str`s and `std::string`s)
    /// and bare literals, `a + b + c`, never two literals in a row: a
    /// `std::string`, which converts to `core::Str` where one is wanted.
    Concat(Vec<Expr>),
    /// A lambda called at once, `[&]() -> T { … }()`: statements where
    /// only an expression may go, printed on one line.
    Run {
        result: String,
        body: Vec<Stmt>,
    },
    /// A lambda, printed on one line: its captures (`=`, or none), its
    /// parameters, its result, and its body, `[=](int32_t x) -> int32_t {
    /// return f(f(x)); }`.
    Lambda {
        captures: String,
        params: Vec<(String, String)>,
        result: String,
        body: Vec<Stmt>,
    },
}

impl Expr {
    pub fn name(name: impl Into<String>) -> Expr {
        Expr::Name(name.into())
    }

    pub fn call(self, args: Vec<Expr>) -> Expr {
        Expr::Call(Box::new(self), args)
    }

    pub fn method(self, name: impl Into<String>, args: Vec<Expr>) -> Expr {
        Expr::Member(Box::new(self), name.into()).call(args)
    }

    /// A member through a pointer, `self->name`.
    pub fn arrow(self, name: impl Into<String>) -> Expr {
        Expr::Arrow(Box::new(self), name.into())
    }

    pub fn bin(op: Bin, left: Expr, right: Expr) -> Expr {
        Expr::Bin(op, Box::new(left), Box::new(right))
    }

    pub fn unary(op: Unary, operand: Expr) -> Expr {
        Expr::Unary(op, Box::new(operand))
    }

    fn prec(&self) -> u8 {
        match self {
            Expr::Int(n) if *n < 0 && *n != i32::MIN => UNARY,
            Expr::Float(f) if f.is_sign_negative() && f.is_finite() => UNARY,
            Expr::Name(_)
            | Expr::Int(_)
            | Expr::Long(_)
            | Expr::Float(_)
            | Expr::Str(_)
            | Expr::Bool(_)
            | Expr::Lambda { .. } => ATOM,
            Expr::Call(..) | Expr::Member(..) | Expr::Arrow(..) | Expr::Run { .. } => CALL,
            Expr::Bin(op, ..) => op.prec(),
            Expr::Unary(..) => UNARY,
            Expr::Cond { .. } => COND,
            Expr::Concat(_) => ADD,
        }
    }

    /// Whether it's a string made as a `std::string`, not a `core::Str`:
    /// a concatenation or `std::to_string`'s.
    pub fn is_std_string(&self) -> bool {
        match self {
            Expr::Concat(_) => true,
            Expr::Call(f, _) => matches!(&**f, Expr::Name(n) if n == "std::to_string"),
            _ => false,
        }
    }

    /// A `core::Str` for a string-typed expression: one made as a
    /// `std::string` ([`is_std_string`](Self::is_std_string)) as
    /// `core::Str(…)`, where C++ would otherwise deduce `std::string` or
    /// find a choice of the two ambiguous.
    pub fn str_value(self) -> Expr {
        if self.is_std_string() {
            Expr::name("core::Str").call(vec![self])
        } else {
            self
        }
    }

    /// Whether C++ allows it as a statement worth writing: a call.
    pub fn is_statement(&self) -> bool {
        matches!(self, Expr::Call(..))
    }
}

/// A method of a group's struct ([`Stmt::GroupStruct`]): its result,
/// name, parameters (an unused one unnamed), and body.
#[derive(Clone, Debug, PartialEq)]
pub struct GroupMethod {
    pub result: String,
    pub name: String,
    pub params: Vec<(String, String)>,
    pub body: Vec<Stmt>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum Stmt {
    Expr(Expr),
    /// `auto name = [=](int32_t x) -> int32_t { … };`: a lambda with
    /// statements, declared before what uses it.
    LambdaLocal {
        name: String,
        captures: String,
        params: Vec<(String, String)>,
        result: String,
        body: Vec<Stmt>,
    },
    /// A call that always throws, `core::bubble();`, after which nothing
    /// runs.
    Bubble(Expr),
    /// `struct Name { T x; R f(A a) const { … } };`: a group of local
    /// functions that use each other or themselves, its captures members.
    GroupStruct {
        name: String,
        fields: Vec<(String, String)>,
        methods: Vec<GroupMethod>,
    },
    /// A block of its own, which scopes its locals.
    Block(Vec<Stmt>),
    /// `ty name = value;`, or `ty name;`.
    Local {
        ty: String,
        name: String,
        value: Option<Expr>,
    },
    Assign {
        target: String,
        value: Expr,
    },
    /// `target = value;` for a target other than a local: a field
    /// (`self->n = 0;`) or a static field.
    Set {
        target: Expr,
        value: Expr,
    },
    If {
        cond: Expr,
        then: Vec<Stmt>,
        els: Vec<Stmt>,
    },
    While {
        cond: Expr,
        body: Vec<Stmt>,
        labels: Labels,
    },
    DoWhile {
        body: Vec<Stmt>,
        cond: Expr,
        labels: Labels,
    },
    /// `switch (scrutinee) { case 0: … break; … default: … }`: each case's
    /// labels, none for the default, and its body, in braces where it
    /// declares locals, and ending with `break;` where it completes.
    Switch {
        scrutinee: Expr,
        cases: Vec<(Vec<Expr>, Vec<Stmt>)>,
    },
    Break,
    Continue,
    /// `goto label;`: a `break` or `continue` of a loop that isn't the
    /// innermost, which C++ has no other way to write.
    Goto(String),
    Return(Option<Expr>),
    /// `std::lock_guard<std::mutex> name(mutex);`: holds the module's lock
    /// to the end of its block.
    Guard {
        name: String,
        mutex: String,
    },
    /// Temper's `orelse`: `try { body } catch (core::TemperBubble const &)
    /// { fallback }`, as Kotlin's.
    Try {
        body: Vec<Stmt>,
        fallback: Vec<Stmt>,
    },
    /// Statements only where `TEMPER_TESTS` is defined, in the same
    /// scope: the init's for what only tests reach.
    TestsOnly(Vec<Stmt>),
    /// Where the statements after it come from, for `temper run` and
    /// `temper test` only: a `#line` directive, where the position
    /// changes ([`temper_tmpl::line_directives`]).
    Line(LineAt),
}

/// A loop's labels, for `goto`s from inner loops: one at the end of its
/// body, which continues it, and one after it, which breaks it.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Labels {
    pub next: Option<String>,
    pub end: Option<String>,
}

/// A function: `static` unless exported, which the header declares; or
/// a member function of a class, defined outside it (`Rect::get_area`),
/// which its struct declares.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Function {
    /// Its `template <class T>` line, without the newline, or empty.
    pub template: String,
    pub exported: bool,
    pub result: String,
    pub name: String,
    /// Each parameter's type and name.
    pub params: Vec<(String, String)>,
    /// Which parameters the body doesn't use, by index: unnamed where
    /// it's defined, which `-Wunused-parameter` would mention.
    pub unused: Vec<usize>,
    /// A member function that leaves its object as it is: `const`.
    pub constant: bool,
    /// An entry point for users, which runs the module's init first
    /// where it has one: an export, or a public constructor or static
    /// function of an exported class or interface, as Kotlin's.
    pub entry: bool,
    pub body: Vec<Stmt>,
}

impl Function {
    /// Its result, name, and parameters; a parameter without a name is
    /// its type alone.
    pub fn signature(&self) -> String {
        self.signature_with(&[])
    }

    /// Its signature where it's defined: unused parameters unnamed.
    pub fn definition(&self) -> String {
        self.signature_with(&self.unused)
    }

    fn signature_with(&self, unnamed: &[usize]) -> String {
        let params: Vec<String> = (self.params.iter().enumerate())
            .map(|(i, (t, n))| match n.as_str() {
                _ if unnamed.contains(&i) => t.clone(),
                "" => t.clone(),
                n => format!("{t} {n}"),
            })
            .collect();
        let constant = if self.constant { " const" } else { "" };
        format!(
            "{} {}({}){constant}",
            self.result,
            self.name,
            params.join(", ")
        )
    }
}

/// A part of a module's `.cpp`, in order.
#[derive(Clone, Debug, PartialEq)]
pub enum Item {
    /// A namespace-scope variable, declared without a value (the init
    /// function gives it one): `static` unless exported, which the header
    /// declares `extern`.
    Variable {
        exported: bool,
        ty: String,
        name: String,
    },
    /// Declares a function that's used before it's defined.
    Prototype(Function),
    Function(Function),
    /// Text as it is, such as a helper's definition.
    Raw(String),
    /// What only tests reach (`temper_tmpl::reach`): a function, its
    /// prototype, a class's member, storage, or the module's lock; or a
    /// private function's overload that only tests call. Only where
    /// `TEMPER_TESTS` is defined, as the tests are, or a build without
    /// them would have unused `static` functions (`-Wunused-function`).
    TestsOnly(Box<Item>),
}

/// A module's `.cpp` file.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Source {
    /// What it includes, its header first: `<module-state/init.hpp>`.
    pub includes: Vec<String>,
    pub namespace: String,
    /// Namespace aliases, as `(alias, namespace)`.
    pub aliases: Vec<(String, String)>,
    pub items: Vec<Item>,
    /// The init function's name, and its body, which runs once.
    pub init: String,
    pub init_body: Vec<Stmt>,
    /// The tests, only where `TEMPER_TESTS` is defined; what only they
    /// include, the header includes there.
    pub tests: Vec<Function>,
    /// With `#line` directives ([`Stmt::Line`]), the file itself, as a
    /// directive names it to restore its own positions where generated
    /// support code follows (C++ has no `#line default`).
    pub generated: Option<String>,
}

/// Whether a character is escaped in a literal: controls, and characters
/// that don't show, or show as something else: format characters,
/// private use, and noncharacters.
fn hidden(c: char) -> bool {
    let n = c as u32;
    n < 0x20
        || (0x7f..0xa0).contains(&n)
        || matches!(
            c,
            '\u{ad}'
                | '\u{200b}'..='\u{200f}'
                | '\u{2028}'..='\u{202e}'
                | '\u{2060}'..='\u{206f}'
                | '\u{feff}'
                | '\u{fff9}'..='\u{fffb}'
                | '\u{e000}'..='\u{f8ff}'
                | '\u{fdd0}'..='\u{fdef}'
                | '\u{f0000}'..
        )
        || (n & 0xfffe) == 0xfffe
}

/// A string literal as a `core::Str`: `TEMPER_STR("…")`, whose
/// `static` the runtime makes once, on first use, so evaluating it
/// allocates nothing, with the size after it where the text has a NUL,
/// and `core::Str()` for the empty string, as Kotlin's.
pub fn str_literal(text: &str) -> String {
    if text.is_empty() {
        return "core::Str()".into();
    }
    let quoted = quoted(text);
    if text.contains('\0') {
        format!("TEMPER_STR({quoted}, {})", text.len())
    } else {
        format!("TEMPER_STR({quoted})")
    }
}

/// Prints a string literal as a bare `"…"` (a `const char*`), as a
/// concatenation's parts are: a string with a NUL in it is a
/// `std::string` of its length, since a literal would end there.
pub fn string_literal(text: &str) -> String {
    let out = quoted(text);
    if text.contains('\0') {
        return format!("std::string({out}, {})", text.len());
    }
    out
}

/// A string's text as a quoted C++ literal, in UTF-8, as C++ compilers
/// read sources by default and as `core::Str` holds Temper's strings. A
/// `?` after a `?` is escaped, since C++14 still reads trigraphs (`??=`).
fn quoted(text: &str) -> String {
    let mut out = String::from("\"");
    let mut prev = '\0';
    for c in text.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '"' => out.push_str("\\\""),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            // Three digits, so a digit after it isn't read as octal.
            '\0' => out.push_str("\\000"),
            '?' if prev == '?' => out.push_str("\\?"),
            _ if hidden(c) && (c as u32) < 0x10000 => {
                let _ = write!(out, "\\u{:04X}", c as u32);
            }
            _ if hidden(c) => {
                let _ = write!(out, "\\U{:08X}", c as u32);
            }
            _ => out.push(c),
        }
        prev = c;
    }
    out.push('"');
    out
}

fn float_literal(f: f64) -> String {
    if f.is_nan() {
        "std::numeric_limits<double>::quiet_NaN()".into()
    } else if f.is_infinite() {
        if f > 0.0 {
            "std::numeric_limits<double>::infinity()".into()
        } else {
            "-std::numeric_limits<double>::infinity()".into()
        }
    } else {
        // Rust's `{:?}` gives `1.0`, `0.1`, and `1e100`, all C++'s too.
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
        Expr::Int(i32::MIN) => "INT32_MIN".into(),
        Expr::Int(n) => n.to_string(),
        Expr::Long(i64::MIN) => "INT64_MIN".into(),
        Expr::Long(n) => format!("int64_t{{{n}}}"),
        Expr::Float(f) => float_literal(*f),
        Expr::Str(s) => str_literal(s),
        Expr::Bool(b) => b.to_string(),
        Expr::Call(f, args) => {
            let args: Vec<String> = args.iter().map(|a| expr(a, COND)).collect();
            format!("{}({})", expr(f, CALL), args.join(", "))
        }
        Expr::Member(obj, name) => format!("{}.{name}", expr(obj, CALL)),
        Expr::Arrow(obj, name) => format!("{}->{name}", expr(obj, CALL)),
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
        // A `std::string` choice as a `core::Str`, which C++ would find
        // ambiguous beside a `core::Str`.
        Expr::Cond { cond, then, els } => format!(
            "{} ? {} : {}",
            expr(cond, COND + 1),
            expr(&then.clone().str_value(), COND + 1),
            expr(&els.clone().str_value(), COND)
        ),
        Expr::Concat(parts) => {
            let parts: Vec<String> = (parts.iter())
                .map(|p| match p {
                    Expr::Str(s) => string_literal(s),
                    p => expr(p, ADD + 1),
                })
                .collect();
            parts.join(" + ")
        }
        Expr::Run { result, body } => {
            let mut text = Printed::default();
            for s in body {
                stmt(&mut text, s, 0);
            }
            format!("[&]() -> {result} {{ {} }}()", one_line(&text))
        }
        Expr::Lambda {
            captures,
            params,
            result,
            body,
        } => {
            let mut text = Printed::default();
            for s in body {
                stmt(&mut text, s, 0);
            }
            format!(
                "{} {{ {} }}",
                lambda_head(captures, params, result),
                one_line(&text)
            )
        }
    };
    if e.prec() < min {
        format!("({text})")
    } else {
        text
    }
}

/// Statements' text on one line, without `#line` directives, which
/// need lines of their own.
fn one_line(text: &str) -> String {
    let lines: Vec<&str> = (text.lines().map(str::trim))
        .filter(|l| !l.starts_with("#line "))
        .collect();
    lines.join(" ")
}

/// Text without its `#line` directives.
fn without_lines(text: &str) -> String {
    (text.split_inclusive('\n'))
        .filter(|l| !l.starts_with("#line "))
        .collect()
}

fn indent(out: &mut String, depth: usize) {
    for _ in 0..depth {
        out.push_str("  ");
    }
}

/// Statements inside braces, the `{` already written, and the closing
/// `}` without a newline.
fn body(out: &mut Printed, stmts: &[Stmt], depth: usize) {
    out.push_str("{\n");
    for s in stmts {
        stmt(out, s, depth + 1);
    }
    indent(out, depth);
    out.push('}');
}

/// A label on an empty statement, where a `goto` lands.
fn label(out: &mut String, label: &Option<String>, depth: usize) {
    if let Some(l) = label {
        indent(out, depth);
        let _ = writeln!(out, "{l}:;");
    }
}

/// A loop's body, with its label for `continue` at the end, after the
/// body's own block, so a `goto` there never skips a local's
/// initialization in the scope it lands in.
fn loop_body(out: &mut Printed, stmts: &[Stmt], labels: &Labels, depth: usize) {
    match &labels.next {
        None => body(out, stmts, depth),
        Some(_) => {
            out.push_str("{\n");
            indent(out, depth + 1);
            body(out, stmts, depth + 1);
            out.push('\n');
            label(out, &labels.next, depth + 1);
            indent(out, depth);
            out.push('}');
        }
    }
}

/// A lambda's head: `[=](int32_t x) -> int32_t`.
fn lambda_head(captures: &str, params: &[(String, String)], result: &str) -> String {
    // An unused one is unnamed, which `-Wunused-parameter` wants.
    let params: Vec<String> = params
        .iter()
        .map(|(t, n)| match n.is_empty() {
            true => t.clone(),
            false => format!("{t} {n}"),
        })
        .collect();
    format!("[{captures}]({}) -> {result}", params.join(", "))
}

fn stmt(out: &mut Printed, s: &Stmt, depth: usize) {
    if let Stmt::Line(at) = s {
        out.directive(at);
        return;
    }
    if let Stmt::TestsOnly(stmts) = s {
        out.push_str("#ifdef TEMPER_TESTS\n");
        for s in stmts {
            stmt(out, s, depth);
        }
        out.push_str("#endif  // TEMPER_TESTS\n");
        return;
    }
    indent(out, depth);
    match s {
        Stmt::LambdaLocal {
            name,
            captures,
            params,
            result,
            body: stmts,
        } => {
            let _ = write!(
                out,
                "auto {name} = {} ",
                lambda_head(captures, params, result)
            );
            body(out, stmts, depth);
            out.push_str(";\n");
        }
        Stmt::GroupStruct {
            name,
            fields,
            methods,
        } => {
            let _ = writeln!(out, "struct {name} {{");
            for (ty, field) in fields {
                indent(out, depth + 1);
                let _ = writeln!(out, "{ty} {field};");
            }
            for m in methods {
                indent(out, depth + 1);
                let params: Vec<String> = (m.params.iter())
                    .map(|(t, n)| match n.is_empty() {
                        true => t.clone(),
                        false => format!("{t} {n}"),
                    })
                    .collect();
                let _ = write!(out, "{} {}({}) const ", m.result, m.name, params.join(", "));
                body(out, &m.body, depth + 1);
                out.push('\n');
            }
            indent(out, depth);
            out.push_str("};\n");
        }
        Stmt::Expr(e) | Stmt::Bubble(e) => {
            let _ = writeln!(out, "{};", expr(e, TOP));
        }
        Stmt::Block(stmts) => {
            body(out, stmts, depth);
            out.push('\n');
        }
        Stmt::Local { ty, name, value } => {
            let _ = write!(out, "{ty} {name}");
            if let Some(v) = value {
                let _ = write!(out, " = {}", expr(v, COND));
            }
            out.push_str(";\n");
        }
        Stmt::Assign { target, value } => {
            let _ = writeln!(out, "{target} = {};", expr(value, COND));
        }
        Stmt::Set { target, value } => {
            let _ = writeln!(out, "{} = {};", expr(target, CALL), expr(value, COND));
        }
        Stmt::If { cond, then, els } => {
            let _ = write!(out, "if ({}) ", expr(cond, TOP));
            body(out, then, depth);
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
                        body(out, then, depth);
                        els = next;
                    }
                    _ => {
                        out.push_str(" else ");
                        body(out, els, depth);
                        break;
                    }
                }
            }
            out.push('\n');
        }
        Stmt::While {
            cond,
            body: stmts,
            labels,
        } => {
            let _ = write!(out, "while ({}) ", expr(cond, TOP));
            loop_body(out, stmts, labels, depth);
            out.push('\n');
            label(out, &labels.end, depth);
        }
        Stmt::DoWhile {
            body: stmts,
            cond,
            labels,
        } => {
            out.push_str("do ");
            loop_body(out, stmts, labels, depth);
            let _ = writeln!(out, " while ({});", expr(cond, TOP));
            label(out, &labels.end, depth);
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
                    stmts.push(Stmt::Break);
                }
                let declares = stmts.iter().any(|s| {
                    matches!(
                        s,
                        Stmt::Local { .. }
                            | Stmt::LambdaLocal { .. }
                            | Stmt::GroupStruct { .. }
                            | Stmt::Guard { .. }
                    )
                });
                if declares {
                    out.push(' ');
                    body(out, &stmts, depth + 1);
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
        Stmt::Break => out.push_str("break;\n"),
        Stmt::Continue => out.push_str("continue;\n"),
        Stmt::Goto(l) => {
            let _ = writeln!(out, "goto {l};");
        }
        Stmt::Return(None) => out.push_str("return;\n"),
        Stmt::Return(Some(e)) => {
            let _ = writeln!(out, "return {};", expr(e, TOP));
        }
        Stmt::Guard { name, mutex } => {
            let _ = writeln!(out, "std::lock_guard<std::mutex> {name}({mutex});");
        }
        Stmt::Try {
            body: stmts,
            fallback,
        } => {
            out.push_str("try ");
            body(out, stmts, depth);
            out.push_str(" catch (core::TemperBubble const &) ");
            body(out, fallback, depth);
            out.push('\n');
        }
        // Written above, without the indent.
        Stmt::TestsOnly(_) | Stmt::Line(_) => {}
    }
}

/// Prints a module's `.cpp`.
/// A function's definition as a header has it, with its template line,
/// and `temper::core` spelled out; without `#line` directives, which
/// would last to the header's end.
pub fn header_definition(f: &Function) -> String {
    let mut out = Printed::default();
    if !f.template.is_empty() {
        let _ = writeln!(out, "{}", f.template);
    }
    let _ = write!(out, "{} ", f.definition());
    body(&mut out, &f.body, 0);
    out.push('\n');
    spell_core(&without_lines(&out))
}

/// Restores the generated file's own positions, `generated` as a
/// directive names it, where `#line` directives have pointed elsewhere,
/// before generated support code, so errors there point at it; `counted`
/// is how many of `out`'s bytes and lines were counted already.
fn restore(out: &mut Printed, generated: &Option<String>, counted: &mut (usize, usize)) {
    let Some(generated) = generated else {
        return;
    };
    match out.presumed() {
        Presumed::Generated => return,
        Presumed::At(at) if at.file == *generated => return,
        _ => {}
    }
    counted.1 += out.as_bytes()[counted.0..]
        .iter()
        .filter(|&&b| b == b'\n')
        .count();
    counted.0 = out.len();
    // The directive's line is the next, then the line after it.
    let at = LineAt {
        file: generated.clone(),
        line: counted.1 as u32 + 2,
    };
    temper_tmpl::line_directives::line(out, &at);
}

/// `core::`, the alias of `temper::core` in a module's `.cpp`, spelled
/// out for a header, where it isn't: only where `core` is a whole name,
/// not the end of another's, as `kelpto_core::` or `temper::core::`.
pub fn spell_core(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    let mut rest = text;
    while let Some(at) = rest.find("core::") {
        let before = rest[..at].chars().next_back();
        let whole = !before.is_some_and(|c| c.is_alphanumeric() || c == '_' || c == ':');
        out.push_str(&rest[..at]);
        out.push_str(if whole { "temper::core::" } else { "core::" });
        rest = &rest[at + "core::".len()..];
    }
    out.push_str(rest);
    out
}

/// One item of a `.cpp`, as [`source`] writes it.
fn item_text(out: &mut Printed, item: &Item) {
    match item {
        Item::Variable { exported, ty, name } => {
            let storage = if *exported { "" } else { "static " };
            let _ = writeln!(out, "{storage}{ty} {name};");
        }
        Item::Prototype(f) => {
            if !f.template.is_empty() {
                let _ = writeln!(out, "{}", f.template);
            }
            let _ = writeln!(out, "static {};", f.signature());
        }
        Item::Function(f) => {
            if !f.template.is_empty() {
                let _ = writeln!(out, "{}", f.template);
            }
            if !f.exported {
                out.push_str("static ");
            }
            let _ = write!(out, "{} ", f.definition());
            body(out, &f.body, 0);
            out.push('\n');
        }
        Item::Raw(text) => out.push_str(text),
        // Written in its run by [`source`].
        Item::TestsOnly(item) => item_text(out, item),
    }
}

pub fn source(s: &Source) -> String {
    let mut out = Printed::default();
    let (header, rest) = s
        .includes
        .split_first()
        .map_or((None, &[][..]), |(h, r)| (Some(h), r));
    if let Some(h) = header {
        let _ = writeln!(out, "#include {h}\n");
    }
    for include in rest {
        let _ = writeln!(out, "#include {include}");
    }
    if !rest.is_empty() {
        out.push('\n');
    }
    let _ = writeln!(out, "namespace {} {{\n", s.namespace);
    for (alias, namespace) in &s.aliases {
        let _ = writeln!(out, "namespace {alias} = {namespace};");
    }
    if !s.aliases.is_empty() {
        out.push('\n');
    }
    // Variables and prototypes in runs, blank lines around the rest; a
    // run of what only tests use in one `#ifdef`.
    fn unwrap(i: &Item) -> (bool, &Item) {
        match i {
            Item::TestsOnly(inner) => (true, inner),
            i => (false, i),
        }
    }
    let mut prev: Option<(bool, &Item)> = None;
    let mut counted = (0, 0);
    for item in &s.items {
        let (tests, item) = unwrap(item);
        if let Some((prev_tests, prev)) = prev {
            if prev_tests && !tests {
                out.push_str("#endif  // TEMPER_TESTS\n");
            }
            let run = prev_tests == tests
                && matches!(
                    (prev, item),
                    (Item::Variable { .. }, Item::Variable { .. })
                        | (Item::Prototype(_), Item::Prototype(_))
                );
            if !run {
                out.push('\n');
            }
        }
        if tests && !prev.is_some_and(|(t, _)| t) {
            out.push_str("#ifdef TEMPER_TESTS\n");
        }
        restore(&mut out, &s.generated, &mut counted);
        item_text(&mut out, item);
        prev = Some((tests, item));
    }
    if let Some((prev_tests, _)) = prev {
        if prev_tests {
            out.push_str("#endif  // TEMPER_TESTS\n");
        }
        out.push('\n');
    }
    // The init function: its body runs once, through the runtime's
    // `init_once`, however many threads call it at once; a call from
    // another thread while it runs waits for it to finish, and one from
    // the same thread, as the init calls the module's entry points,
    // returns at once.
    restore(&mut out, &s.generated, &mut counted);
    if s.init_body.is_empty() {
        let _ = writeln!(out, "void {}() {{}}", s.init);
    } else {
        let _ = writeln!(out, "void {}() {{", s.init);
        out.push_str("  core::init_once([] ");
        body(&mut out, &s.init_body, 1);
        out.push_str(");\n}\n");
    }
    let _ = writeln!(out, "\n}}  // namespace {}", s.namespace);
    if !s.tests.is_empty() {
        out.push_str("\n#ifdef TEMPER_TESTS\n\n");
        let _ = writeln!(out, "namespace {} {{\n", s.namespace);
        for (i, f) in s.tests.iter().enumerate() {
            if i > 0 {
                out.push('\n');
            }
            restore(&mut out, &s.generated, &mut counted);
            let _ = write!(out, "{} ", f.definition());
            body(&mut out, &f.body, 0);
            out.push('\n');
        }
        let _ = writeln!(out, "\n}}  // namespace {}", s.namespace);
        out.push_str("\n#endif  // TEMPER_TESTS\n");
    }
    out.text
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
            expr(&Expr::bin(Bin::BitAnd, sum.clone(), Expr::Int(1)), 0),
            "(a + b) & 1"
        );
        assert_eq!(
            expr(&Expr::bin(Bin::Sub, n("c"), sum.clone()), 0),
            "c - (a + b)"
        );
        let and = Expr::bin(Bin::And, n("a"), n("b"));
        assert_eq!(expr(&Expr::bin(Bin::Or, and, n("c")), 0), "(a && b) || c");
        assert_eq!(expr(&Expr::unary(Unary::Neg, Expr::Int(-2)), 0), "-(-2)");
        assert_eq!(expr(&Expr::Int(i32::MIN), 0), "INT32_MIN");
        assert_eq!(expr(&Expr::Long(-5), 0), "int64_t{-5}");
        assert_eq!(expr(&Expr::Long(i64::MIN), 0), "INT64_MIN");
        assert_eq!(
            expr(&Expr::Float(f64::NAN), 0),
            "std::numeric_limits<double>::quiet_NaN()"
        );
        assert_eq!(
            expr(&Expr::Str("a".into()).method("empty", vec![]), 0),
            "TEMPER_STR(\"a\").empty()"
        );
        let concat = Expr::Concat(vec![Expr::Str("n=".into()), Expr::name("n")]);
        assert_eq!(expr(&concat, 0), "\"n=\" + n");
        let choice = Expr::Cond {
            cond: Box::new(Expr::name("c")),
            then: Box::new(concat),
            els: Box::new(Expr::Str(String::new())),
        };
        assert_eq!(expr(&choice, 0), "c ? core::Str(\"n=\" + n) : core::Str()");
    }

    /// `core::` spelled out only where it's the alias, not another
    /// library's namespace ending in it (`kelpto_core::`).
    #[test]
    fn spells_core() {
        assert_eq!(
            spell_core("core::List f(kelpto_core::RedNode, temper::core::Int, (core::Int))"),
            "temper::core::List f(kelpto_core::RedNode, temper::core::Int, (temper::core::Int))"
        );
    }

    #[test]
    fn strings() {
        assert_eq!(string_literal("it's"), "\"it's\"");
        assert_eq!(string_literal("say \"hi\""), "\"say \\\"hi\\\"\"");
        assert_eq!(string_literal("??=?"), "\"?\\?=?\"");
        assert_eq!(
            string_literal("a\n\\\u{2028}\u{1}"),
            "\"a\\n\\\\\\u2028\\u0001\""
        );
        assert_eq!(
            string_literal("\u{e000}\u{ffff}\u{10ffff}\u{1f600}"),
            "\"\\uE000\\uFFFF\\U0010FFFF\u{1f600}\""
        );
        assert_eq!(string_literal("a\u{0}1"), "std::string(\"a\\0001\", 3)");
        assert_eq!(str_literal("a\u{0}1"), "TEMPER_STR(\"a\\0001\", 3)");
        assert_eq!(str_literal("hi"), "TEMPER_STR(\"hi\")");
        assert_eq!(str_literal(""), "core::Str()");
    }
}
