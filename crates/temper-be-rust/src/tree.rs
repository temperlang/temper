//! Rust's output tree and its printer.
//!
//! The printer lays code out as rustfmt does with its default settings,
//! for the constructs the translator makes: lines of at most 100
//! columns, a call's arguments on one line only while they take at most
//! 60 (rustfmt's `fn_call_width`), a chain of method calls on one line
//! only while it takes at most 60 (`chain_width`), an `if` … `else` as a
//! value on one line only after `=` and within 50 (`single_line_if_else_max_width`),
//! a last argument that's a closure, or an only argument that's a call,
//! overflowing onto the lines after its call's, and otherwise one
//! argument per line. The tests check that rustfmt leaves the goldens as
//! they are. It adds parentheses only where Rust's precedence needs
//! them.
//!
//! No printing crate is shared among backends yet: Python's tree and
//! printer (in temper-be-py) share no layout rules with this one beyond
//! indentation, so a shared crate would hold little (notes/tmpl/overview.md,
//! "Boundaries").

const MAX: usize = 100;
const CALL_WIDTH: usize = 60;
const CHAIN_WIDTH: usize = 60;
const IF_WIDTH: usize = 50;
const STRUCT_LIT_WIDTH: usize = 18;
/// The widest a trait's header may be with its supertraits on its line,
/// as rustfmt lays one out (found by trying).
const TRAIT_WIDTH: usize = 92;

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
    Rem,
}

impl Bin {
    fn text(self) -> &'static str {
        match self {
            Bin::Or => "||",
            Bin::And => "&&",
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
            Bin::Rem => "%",
        }
    }

    fn prec(self) -> u8 {
        match self {
            Bin::Or => 3,
            Bin::And => 4,
            Bin::Eq | Bin::Ne | Bin::Lt | Bin::Le | Bin::Gt | Bin::Ge => COMPARE,
            Bin::BitOr => 6,
            Bin::BitXor => 7,
            Bin::BitAnd => 8,
            Bin::Shl | Bin::Shr => 9,
            Bin::Add | Bin::Sub => 10,
            Bin::Mul | Bin::Div | Bin::Rem => 11,
        }
    }
}

/// A prefix operator.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Unary {
    Not,
    Neg,
    Deref,
    Ref,
}

impl Unary {
    fn text(self) -> &'static str {
        match self {
            Unary::Not => "!",
            Unary::Neg => "-",
            Unary::Deref => "*",
            Unary::Ref => "&",
        }
    }
}

// Precedences, loosest first, as Rust's reference has them.
const JUMP: u8 = 0;
const COMPARE: u8 = 5;
const AS: u8 = 12;
const UNARY: u8 = 13;
const POSTFIX: u8 = 14;
const ATOM: u8 = 15;

#[derive(Clone, Debug, PartialEq)]
pub enum Expr {
    /// A name or a path, as is: `fib`, `temper_core::int_div`.
    Path(String),
    /// A literal other than a string's, as is: `0`, `-1`, `1.5`, `true`.
    Lit(String),
    /// A string literal, quoted when printed. A format string is one
    /// too, with its braces already in place.
    Str(String),
    Call(Box<Expr>, Vec<Expr>),
    Method(Box<Expr>, String, Vec<Expr>),
    /// A macro's name, without its `!`, and its arguments.
    Macro(String, Vec<Expr>),
    Bin(Bin, Box<Expr>, Box<Expr>),
    Unary(Unary, Box<Expr>),
    Cast(Box<Expr>, String),
    Try(Box<Expr>),
    If(Box<Expr>, Block, Block),
    /// `let pattern = e`, as an `if`'s condition.
    Let(String, Box<Expr>),
    Block(Block),
    Closure(Vec<String>, Block),
    /// `move` before a closure, which takes what it captures.
    Move(Box<Expr>),
    Return(Option<Box<Expr>>),
    /// A field of a value: `self.0`, `self.0.w`.
    Field(Box<Expr>, String),
    /// A struct literal, each field with its value, or `None` for the
    /// shorthand of a local of the field's name: `RectStruct { w, h: 2 }`.
    Struct(String, Vec<(String, Option<Expr>)>),
}

impl Expr {
    /// `(a, b)`: a call of nothing, which prints as a tuple.
    pub fn tuple(items: Vec<Expr>) -> Expr {
        Expr::Call(Box::new(Expr::Path(String::new())), items)
    }

    pub fn path(name: impl Into<String>) -> Expr {
        Expr::Path(name.into())
    }

    pub fn call(self, args: Vec<Expr>) -> Expr {
        Expr::Call(Box::new(self), args)
    }

    pub fn method(self, name: impl Into<String>, args: Vec<Expr>) -> Expr {
        Expr::Method(Box::new(self), name.into(), args)
    }

    pub fn bin(op: Bin, left: Expr, right: Expr) -> Expr {
        Expr::Bin(op, Box::new(left), Box::new(right))
    }

    pub fn unary(op: Unary, operand: Expr) -> Expr {
        Expr::Unary(op, Box::new(operand))
    }

    pub fn cast(self, ty: impl Into<String>) -> Expr {
        Expr::Cast(Box::new(self), ty.into())
    }

    pub fn try_(self) -> Expr {
        Expr::Try(Box::new(self))
    }

    pub fn field(self, name: impl Into<String>) -> Expr {
        Expr::Field(Box::new(self), name.into())
    }

    /// `Ok(self)`.
    pub fn ok(self) -> Expr {
        Expr::path("Ok").call(vec![self])
    }

    fn prec(&self) -> u8 {
        match self {
            Expr::Lit(text) if text.starts_with('-') => UNARY,
            Expr::Path(_) | Expr::Lit(_) | Expr::Str(_) | Expr::Macro(..) | Expr::Block(_) => ATOM,
            Expr::Call(..) | Expr::Method(..) | Expr::Try(_) | Expr::Field(..) => POSTFIX,
            Expr::Struct(..) => ATOM,
            Expr::Unary(..) => UNARY,
            Expr::Cast(..) => AS,
            Expr::Bin(op, ..) => op.prec(),
            // Never an operand or a receiver without parentheses.
            Expr::If(..) | Expr::Let(..) | Expr::Closure(..) | Expr::Move(_) | Expr::Return(_) => {
                JUMP
            }
        }
    }
}

/// Statements, then maybe a value.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Block {
    pub stmts: Vec<Stmt>,
    pub tail: Option<Box<Expr>>,
}

impl Block {
    /// A block; a `()` after statements is left out, as clippy's
    /// `unused_unit` wants, since the block gives `()` without it.
    pub fn new(stmts: Vec<Stmt>, tail: Option<Expr>) -> Block {
        let unit = matches!(&tail, Some(Expr::Call(f, items))
            if items.is_empty() && matches!(&**f, Expr::Path(p) if p.is_empty()))
            || matches!(&tail, Some(Expr::Path(p)) if p == "()");
        let tail = if unit && !stmts.is_empty() {
            None
        } else {
            tail
        };
        Block {
            stmts,
            tail: tail.map(Box::new),
        }
    }

    /// Just a value.
    pub fn of(value: Expr) -> Block {
        Block::new(Vec::new(), Some(value))
    }

    fn is_empty(&self) -> bool {
        self.stmts.is_empty() && self.tail.is_none()
    }
}

#[derive(Clone, Debug, PartialEq)]
pub enum Stmt {
    /// `let [mut] name[: ty][ = value];`
    Let {
        name: String,
        mutable: bool,
        ty: Option<String>,
        value: Option<Expr>,
    },
    /// `place = value;`
    Assign(Expr, Expr),
    /// `e;`
    Expr(Expr),
    If {
        cond: Expr,
        then: Block,
        els: Option<Block>,
    },
    While {
        label: Option<String>,
        cond: Expr,
        body: Block,
    },
    /// `match scrutinee { pattern => { … } … }`, each arm's pattern
    /// written out, as `1 | 2` or `_`.
    Match {
        scrutinee: Expr,
        arms: Vec<(String, Block)>,
    },
    Loop {
        label: Option<String>,
        body: Block,
    },
    /// A labeled block, `'label: { … }`.
    Labeled {
        label: String,
        body: Block,
    },
    Break(Option<String>),
    Continue(Option<String>),
    Return(Option<Expr>),
    /// A `static` in a function's body.
    Static {
        name: String,
        ty: String,
        value: Expr,
    },
    Block(Block),
    /// A block only tests build, `#[cfg(test)] { … }`, as a module's
    /// test init at the end of its init.
    TestsOnly(Block),
    /// An item in a function's body, as a group of local functions'
    /// struct and its `impl`.
    Item(Box<Item>),
}

/// A module's item.
#[derive(Clone, Debug, PartialEq)]
pub enum Item {
    Use(String),
    Fn {
        /// `pub`, `pub(crate)`, or nothing.
        vis: &'static str,
        name: String,
        /// Each written out: `mut i: i32`.
        params: Vec<String>,
        result: Option<String>,
        body: Block,
    },
    Static {
        vis: &'static str,
        name: String,
        ty: String,
        value: Expr,
    },
    /// `pub mod name;`, a module in a file of its own.
    Mod(String),
    /// `mod _connected;`, host code's module, which isn't the
    /// library's API, with its lints allowed ([`crate::translate::host`]).
    HostMod,
    /// `mod support; use support::*;`, a std module's support code
    /// ([`crate::STD_SUPPORT`]), with its lints allowed, as Kotlin's:
    /// `pub use` where it's the module's API (`temporal::today()`), else
    /// `use` (Kotlin's `pub use` re-exports nothing of regex's, its
    /// functions `pub(crate)`).
    SupportMod(bool),
    /// `pub use path;`, a re-export, with its visibility.
    Reexport {
        vis: &'static str,
        path: String,
    },
    /// `#[cfg(test)] mod tests { use …; … }`: its `use`s, and each test
    /// function, returning `temper_core::Result<()>`.
    Tests {
        uses: Vec<String>,
        tests: Vec<(String, Block)>,
    },
    /// A struct with named fields, after its attributes (`#[derive(Clone)]`):
    /// each field's visibility, name, and type.
    Struct {
        attrs: Vec<String>,
        vis: &'static str,
        name: String,
        fields: Vec<(&'static str, String, String)>,
    },
    /// A tuple struct of one or more types: `pub struct Rect(Arc<RectStruct>);`.
    TupleStruct {
        attrs: Vec<String>,
        vis: &'static str,
        name: String,
        types: Vec<String>,
    },
    /// `impl Head { … }`, its head as written: `Rect`, `ShapeTrait for Rect`.
    Impl {
        head: String,
        items: Vec<Member>,
    },
    /// A trait with its supertraits and members.
    Trait {
        vis: &'static str,
        name: String,
        bounds: Vec<String>,
        items: Vec<Member>,
    },
    /// A macro called as an item: `temper_core::impl_any_value_trait!(Rect, [Shape]);`.
    Macro(Expr),
    /// A module written inline, `pub mod builders { … }`.
    InlineMod {
        vis: &'static str,
        name: String,
        items: Vec<Item>,
    },
    /// An item after attributes, each on a line of its own, as
    /// `#[cfg(test)]` for what only tests reach.
    Attributed {
        attrs: Vec<&'static str>,
        item: Box<Item>,
    },
}

/// A member of an `impl` or a trait.
#[derive(Clone, Debug, PartialEq)]
pub enum Member {
    /// A function, with its parameters written out, `&self` among them;
    /// without a body, a trait's required method.
    Fn {
        vis: &'static str,
        name: String,
        params: Vec<String>,
        result: Option<String>,
        body: Option<Block>,
    },
    /// `type Name = Ty;`
    Type { name: String, ty: String },
}

/// What `mod _connected;` allows: host code is the library author's,
/// written for Kotlin's backend, whose crates allow every warning, so it
/// needn't be warning-free.
pub const HOST_ALLOW: &str = "#[allow(warnings)]";

/// A source file's items, printed with a blank line between items, but
/// none between `use`s, nor between `mod`s.
pub fn file(items: &[Item]) -> String {
    MEMO.with(|m| *m.borrow_mut() = Some(Memo::new()));
    let text = items_at(items, 0);
    MEMO.with(|m| *m.borrow_mut() = None);
    text
}

/// Layouts already made in the file being printed, by expression (its
/// address, while [`file`] borrows the items) and where it starts, and
/// one-line forms ([`flat`]) at column `usize::MAX`: a call that doesn't
/// fit tries its last argument overflowing before one argument a line,
/// so without them, deeply nested calls, as a regex's tree, take time
/// exponential in their depth. None outside [`file`], where expressions
/// come and go.
type Memo = std::collections::HashMap<(usize, usize, usize), Option<String>>;

thread_local! {
    static MEMO: std::cell::RefCell<Option<Memo>> = const { std::cell::RefCell::new(None) };
}

/// `make`'s result for `e` at `a` and `b`, made once per file.
fn memo(e: &Expr, a: usize, b: usize, make: impl FnOnce() -> Option<String>) -> Option<String> {
    let key = (e as *const Expr as usize, a, b);
    let found = MEMO.with(|m| m.borrow().as_ref().map(|m| m.get(&key).cloned()));
    match found {
        // Outside `file`.
        None => make(),
        Some(Some(made)) => made,
        Some(None) => {
            let made = make();
            MEMO.with(|m| {
                if let Some(m) = m.borrow_mut().as_mut() {
                    m.insert(key, made.clone());
                }
            });
            made
        }
    }
}

/// Items at an indent, with a blank line between items, but none
/// between `use`s, nor between `mod`s.
fn items_at(items: &[Item], indent: usize) -> String {
    let mut out = String::new();
    let mut last = 0;
    for (i, item) in items.iter().enumerate() {
        // `use`s together, and `mod`s.
        let group = match item {
            Item::Use(_) => 1,
            Item::Mod(_) | Item::HostMod | Item::SupportMod(_) => 2,
            Item::Reexport { .. } => 3,
            _ => 0,
        };
        if i > 0 && !(group != 0 && group == last) {
            out.push('\n');
        }
        last = group;
        out.push_str(&item_at(item, indent));
    }
    out
}

fn attrs_text(attrs: &[impl AsRef<str>], pad: &str) -> String {
    attrs
        .iter()
        .map(|a| format!("{pad}{}\n", a.as_ref()))
        .collect::<Vec<_>>()
        .concat()
}

/// An `impl`'s or a trait's members, a blank line between functions.
fn members(items: &[Member], indent: usize) -> String {
    let mut out = String::new();
    let mut last_fn = false;
    for (i, m) in items.iter().enumerate() {
        let is_fn = matches!(m, Member::Fn { .. });
        if i > 0 && (is_fn || last_fn) {
            out.push('\n');
        }
        last_fn = is_fn;
        match m {
            Member::Fn {
                vis,
                name,
                params,
                result,
                body: Some(body),
            } => out.push_str(&function(
                vis,
                name,
                params,
                result.as_deref(),
                body,
                indent,
            )),
            Member::Fn {
                vis,
                name,
                params,
                result,
                body: None,
            } => {
                let sig = signature(vis, name, params, result.as_deref(), indent, 1);
                out.push_str(&format!("{sig};\n"));
            }
            Member::Type { name, ty } => {
                out.push_str(&format!("{}type {name} = {ty};\n", pad(indent)));
            }
        }
    }
    out
}

/// An `impl`'s head after `impl`: `<T: …> Box<T>` with no space first.
fn impl_head(head: &str) -> String {
    if head.starts_with('<') {
        head.to_string()
    } else {
        format!(" {head}")
    }
}

/// Where an impl's head has its trait's ` for ` (outside its generics),
/// if it does.
fn impl_for(head: &str) -> Option<usize> {
    let mut depth = 0i32;
    for (i, c) in head.char_indices() {
        match c {
            '<' => depth += 1,
            // Not a function type's `->`.
            '>' if !head[..i].ends_with('-') => depth -= 1,
            ' ' if depth == 0 && head[i..].starts_with(" for ") => return Some(i),
            _ => {}
        }
    }
    None
}

fn item_at(item: &Item, indent: usize) -> String {
    let pad = pad(indent);
    match item {
        Item::Use(path) => format!("{pad}use {path};\n"),
        Item::Mod(name) => format!("{pad}pub mod {name};\n"),
        Item::HostMod => format!("{pad}{HOST_ALLOW}\n{pad}mod _connected;\n"),
        Item::SupportMod(false) => {
            format!("{pad}{HOST_ALLOW}\n{pad}mod support;\n{pad}use support::*;\n")
        }
        // Without its feature, it has nothing to re-export.
        Item::SupportMod(true) => format!(
            "{pad}{HOST_ALLOW}\n{pad}mod support;\n{pad}#[allow(unused_imports)]\n{pad}pub use support::*;\n"
        ),
        Item::Reexport { vis, path } => format!("{pad}{vis} use {path};\n"),
        Item::Fn {
            vis,
            name,
            params,
            result,
            body,
        } => function(vis, name, params, result.as_deref(), body, indent),
        Item::Struct {
            attrs,
            vis,
            name,
            fields,
        } => {
            let mut out = attrs_text(attrs, &pad);
            let head = format!("{pad}{}struct {name}", vis_text(vis));
            if fields.is_empty() {
                out.push_str(&format!("{head} {{}}\n"));
                return out;
            }
            out.push_str(&format!("{head} {{\n"));
            for (fvis, fname, ty) in fields {
                out.push_str(&format!("{pad}    {}{fname}: {ty},\n", vis_text(fvis)));
            }
            out.push_str(&format!("{pad}}}\n"));
            out
        }
        Item::TupleStruct {
            attrs,
            vis,
            name,
            types,
        } => {
            let line = format!("{pad}{}struct {name}({});", vis_text(vis), types.join(", "));
            if width(&line) <= MAX {
                return format!("{}{line}\n", attrs_text(attrs, &pad));
            }
            // Too wide: a type per line, as rustfmt lays it out.
            let mut out = attrs_text(attrs, &pad);
            out.push_str(&format!("{pad}{}struct {name}(\n", vis_text(vis)));
            for ty in types {
                out.push_str(&format!("{pad}    {ty},\n"));
            }
            out.push_str(&format!("{pad});\n"));
            out
        }
        Item::Impl { head, items } => {
            let head = impl_head(head);
            let body = if items.is_empty() {
                "{}".to_string()
            } else {
                format!("{{\n{}{pad}}}", members(items, indent + 4))
            };
            let line = format!("{pad}impl{head} {{");
            match impl_for(&head) {
                // Too wide: `for Type` on the next line and the brace on
                // its own, as rustfmt lays it out.
                Some(at) if width(&line) > MAX => format!(
                    "{pad}impl{}\n{pad}    {}\n{pad}{body}\n",
                    &head[..at],
                    &head[at + 1..]
                ),
                _ => format!("{pad}impl{head} {body}\n"),
            }
        }
        Item::Trait {
            vis,
            name,
            bounds,
            items,
        } => {
            let head = format!("{pad}{}trait {name}", vis_text(vis));
            let joined = bounds.join(" + ");
            let open = if bounds.is_empty() {
                format!("{head} {{")
            } else if width(&head) + 2 + width(&joined) + 2 <= TRAIT_WIDTH {
                format!("{head}: {joined} {{")
            } else if indent + 4 + width(&joined) <= MAX {
                format!("{head}:\n{pad}    {joined}\n{pad}{{")
            } else {
                let mut open = format!("{head}:\n{pad}    {}", bounds[0]);
                for b in &bounds[1..] {
                    open.push_str(&format!("\n{pad}    + {b}"));
                }
                open.push_str(&format!("\n{pad}{{"));
                open
            };
            if items.is_empty() {
                format!("{open}}}\n")
            } else {
                format!("{open}\n{}{pad}}}\n", members(items, indent + 4))
            }
        }
        Item::Macro(e) => format!("{pad}{};\n", layout_t(e, indent, indent, 1)),
        Item::InlineMod { vis, name, items } => format!(
            "{pad}{}mod {name} {{\n{}{pad}}}\n",
            vis_text(vis),
            items_at(items, indent + 4)
        ),
        Item::Attributed { attrs, item } => attrs_text(attrs, &pad) + &item_at(item, indent),
        Item::Tests { uses, tests } => {
            let mut out = String::from("#[cfg(test)]\nmod tests {\n");
            for u in uses {
                out.push_str(&format!("    use {u};\n"));
            }
            for (i, (name, body)) in tests.iter().enumerate() {
                if i > 0 || !uses.is_empty() {
                    out.push('\n');
                }
                out.push_str("    #[test]\n");
                let result = Some("temper_core::Result<()>");
                out.push_str(&function("", name, &[], result, body, 4));
            }
            out.push_str("}\n");
            out
        }
        Item::Static {
            vis,
            name,
            ty,
            value,
        } => {
            let head = format!("{}static {name}: {ty} = ", vis_text(vis));
            static_at(&head, value, indent)
        }
    }
}

/// A `static`, given its head up to `= `: its value after it, or where
/// that's too wide and the value fits on the next line, there, as
/// rustfmt lays it out.
fn static_at(head: &str, value: &Expr, indent: usize) -> String {
    let pad = pad(indent);
    let col = indent + width(head);
    if let Some(text) = flat(value) {
        if col + width(&text) + 1 > MAX && indent + 4 + width(&text) < MAX {
            return format!("{pad}{}\n{pad}    {text};\n", head.trim_end());
        }
    }
    format!("{pad}{head}{};\n", layout_t(value, indent, col, 1))
}

/// A function at an indent.
fn function(
    vis: &str,
    name: &str,
    params: &[String],
    result: Option<&str>,
    body: &Block,
    indent: usize,
) -> String {
    let sig = signature(vis, name, params, result, indent, 2);
    let pad = pad(indent);
    if body.is_empty() {
        format!("{sig} {{}}\n")
    } else {
        format!("{sig} {{\n{}{pad}}}\n", block(body, indent + 4))
    }
}

/// A function's signature, with `trail` columns after it: its
/// parameters on one line if they fit, else one per line.
fn signature(
    vis: &str,
    name: &str,
    params: &[String],
    result: Option<&str>,
    indent: usize,
    trail: usize,
) -> String {
    let pad = pad(indent);
    // Attributes before the visibility, each on a line of its own.
    let (attrs, vis) = vis.rsplit_once('\n').unwrap_or(("", vis));
    let attrs = attrs
        .lines()
        .map(|a| format!("{pad}{a}\n"))
        .collect::<Vec<_>>()
        .concat();
    let vis = vis_text(vis);
    let result = result.map(|r| format!(" -> {r}")).unwrap_or_default();
    // Type parameters too wide for the line, one per line, and then the
    // parameters too, as rustfmt lays them out.
    if let Some((base, generics)) = name.strip_suffix('>').and_then(|n| n.split_once('<')) {
        if width(&format!("{pad}{vis}fn {name}(")) > MAX {
            let mut sig = format!("{pad}{vis}fn {base}<\n");
            for g in split_top(generics) {
                sig.push_str(&format!("{pad}    {g},\n"));
            }
            sig.push_str(&format!("{pad}>(\n"));
            for p in params {
                sig.push_str(&format!("{pad}    {p},\n"));
            }
            sig.push_str(&format!("{pad}){result}"));
            return format!("{attrs}{sig}");
        }
    }
    let mut sig = format!("{pad}{vis}fn {name}({}){result}", params.join(", "));
    if width(&sig) + trail > MAX {
        sig = format!("{pad}{vis}fn {name}(\n");
        for p in params {
            sig.push_str(&format!("{pad}    {p},\n"));
        }
        sig.push_str(&format!("{pad}){result}"));
    }
    format!("{attrs}{sig}")
}

/// A list's parts, split at its commas outside `<…>` and `(…)`.
fn split_top(list: &str) -> Vec<&str> {
    let mut out = Vec::new();
    let (mut depth, mut start) = (0i32, 0);
    for (i, c) in list.char_indices() {
        match c {
            '<' | '(' => depth += 1,
            '>' | ')' => depth -= 1,
            ',' if depth == 0 => {
                out.push(list[start..i].trim());
                start = i + 1;
            }
            _ => {}
        }
    }
    out.push(list[start..].trim());
    out
}

fn vis_text(vis: &str) -> String {
    if vis.is_empty() {
        String::new()
    } else {
        format!("{vis} ")
    }
}

fn pad(indent: usize) -> String {
    " ".repeat(indent)
}

fn width(s: &str) -> usize {
    s.chars().count()
}

/// The column after `text`, written from `col`.
fn end_col(col: usize, text: &str) -> usize {
    match text.rfind('\n') {
        Some(i) => width(&text[i + 1..]),
        None => col + width(text),
    }
}

fn first_line(text: &str) -> &str {
    text.split('\n').next().unwrap_or("")
}

/// A Rust string literal.
pub fn quote(s: &str) -> String {
    let mut out = String::from("\"");
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '\0' => out.push_str("\\0"),
            // Controls, characters that show as nothing, which clippy's
            // `invisible_characters` rejects, and those that change the
            // direction text shows in, which rustc's
            // `text_direction_codepoint_in_literal` does.
            c if c.is_control()
                || matches!(
                    c,
                    '\u{ad}'
                        | '\u{200b}'..='\u{200d}'
                        | '\u{2060}'
                        | '\u{feff}'
                        | '\u{202a}'..='\u{202e}'
                        | '\u{2066}'..='\u{2069}'
                ) =>
            {
                out.push_str(&format!("\\u{{{:x}}}", c as u32))
            }
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

fn paren(text: String, needed: bool) -> String {
    if needed { format!("({text})") } else { text }
}

/// An operand, in parentheses if its precedence is looser than `min`.
fn operand(e: &Expr, min: u8) -> Option<String> {
    Some(paren(flat(e)?, e.prec() < min))
}

fn bin_parts(op: Bin, a: &Expr, b: &Expr) -> (bool, bool) {
    let p = op.prec();
    let compare = p == COMPARE;
    // `a as i32 < b` would start generic arguments.
    let cast_lt = matches!(a, Expr::Cast(..)) && matches!(op, Bin::Lt | Bin::Shl);
    let left = a.prec() < p || (compare && a.prec() == p) || cast_lt;
    let right = b.prec() <= p;
    (left, right)
}

/// Arguments on one line, if each is flat and together they're narrow
/// enough. An only argument that's a method call may be wider, as
/// rustfmt overflows it: `is_none(f().unwrap_or(None))` within the line.
fn flat_args(args: &[Expr]) -> Option<String> {
    let parts: Option<Vec<String>> = args.iter().map(flat_arg).collect();
    let text = parts?.join(", ");
    let overflows = matches!(args, [Expr::Method(..)]);
    (overflows || width(&text) <= CALL_WIDTH).then_some(text)
}

/// An argument on one line: as [`flat`], or a short `if` … `else`, which
/// rustfmt keeps on one line as an argument as after `=`.
fn flat_arg(e: &Expr) -> Option<String> {
    flat_if(e).or_else(|| flat(e))
}

/// Whether rustfmt calls an argument simple, for a format macro's
/// arguments after its string: a literal, a name, or a field of one.
fn simple_arg(e: &Expr) -> bool {
    match e {
        Expr::Lit(_) | Expr::Str(_) => true,
        Expr::Path(p) => !p.contains("::"),
        Expr::Field(x, _) | Expr::Unary(_, x) | Expr::Cast(x, _) | Expr::Try(x) => simple_arg(x),
        _ => false,
    }
}

/// A link of a chain: a method call, a field, or `?`.
#[derive(Clone, Copy)]
enum Link<'a> {
    Call(&'a str, &'a [Expr]),
    Field(&'a str),
    Try,
}

impl Link<'_> {
    /// Whether rustfmt keeps it on the line of the link before, `after`:
    /// `?`, and a tuple's field (`.0`) unless after a `?`.
    fn joins(self, after: Option<Link<'_>>) -> bool {
        match self {
            Link::Try => true,
            Link::Field(name) => {
                name.chars().all(|c| c.is_ascii_digit()) && !matches!(after, Some(Link::Try))
            }
            Link::Call(..) => false,
        }
    }

    fn flat(self) -> Option<String> {
        Some(match self {
            Link::Call(name, args) => format!(".{name}({})", flat_args(args)?),
            Link::Field(name) => format!(".{name}"),
            Link::Try => "?".into(),
        })
    }
}

/// A chain's root and its links, innermost first.
fn links(e: &Expr) -> (&Expr, Vec<Link<'_>>) {
    let mut out = Vec::new();
    let mut base = e;
    loop {
        match base {
            Expr::Method(r, name, args) => {
                out.push(Link::Call(name, args));
                base = r;
            }
            Expr::Field(r, name) => {
                out.push(Link::Field(name));
                base = r;
            }
            Expr::Try(r) => {
                out.push(Link::Try);
                base = r;
            }
            _ => break,
        }
    }
    out.reverse();
    (base, out)
}

/// A chain's links as rustfmt groups them: those on the root's line (the
/// links that join it, and the first other one, if the root is no wider
/// than an indent), then each other link with those that join it.
fn chain_groups<'a>(root_width: usize, links: &[Link<'a>]) -> (Vec<Link<'a>>, Vec<Vec<Link<'a>>>) {
    let mut head = Vec::new();
    let mut rest = links.iter().copied().peekable();
    // A `?` joins the root; a tuple's field only a short root's.
    while let Some(l) = rest.next_if(|l| matches!(l, Link::Try)) {
        head.push(l);
    }
    if root_width <= 4 && head.is_empty() {
        if let Some(l) = rest.next() {
            head.push(l);
            while let Some(l) = rest.next_if(|l| l.joins(head.last().copied())) {
                head.push(l);
            }
        }
    }
    let mut groups: Vec<Vec<Link>> = Vec::new();
    for l in rest {
        match groups.last_mut() {
            Some(g) if l.joins(g.last().copied()) => g.push(l),
            _ => groups.push(vec![l]),
        }
    }
    (head, groups)
}

/// Whether a chain on one line is too wide: one of two or more links
/// after its root's line, wider than rustfmt's `chain_width`.
fn chain_too_wide(e: &Expr, text: &str) -> bool {
    // rustfmt counts a last link's `?`s twice, with the link and after it.
    let tries = match e {
        Expr::Try(_) => text.len() - text.trim_end_matches('?').len(),
        _ => 0,
    };
    if width(text) + tries <= CHAIN_WIDTH {
        return false;
    }
    let (root, links) = links(e);
    let root_width = flat(root).map_or(MAX, |r| width(&r));
    let (head, groups) = chain_groups(root_width, &links);
    // A call on the root's line counts as one of rustfmt's children.
    let head_call = head.iter().any(|l| matches!(l, Link::Call(..)));
    groups.len() + usize::from(head_call) >= 2
}

/// An expression on one line, if it may be: no part must take several
/// lines, and every call and chain is within its width.
fn flat(e: &Expr) -> Option<String> {
    memo(e, usize::MAX, 0, || flat_made(e))
}

/// [`flat`], made.
fn flat_made(e: &Expr) -> Option<String> {
    Some(match e {
        Expr::Path(s) | Expr::Lit(s) => s.clone(),
        Expr::Str(s) => quote(s),
        Expr::Let(pattern, x) => format!("let {pattern} = {}", flat(x)?),
        // A field's value called needs parentheses, or it's a method call.
        Expr::Call(f, args) if matches!(**f, Expr::Field(..)) => {
            format!("({})({})", flat(f)?, flat_args(args)?)
        }
        Expr::Call(f, args) => format!("{}({})", operand(f, POSTFIX)?, flat_args(args)?),
        Expr::Method(r, name, args) => {
            let text = format!("{}.{name}({})", operand(r, POSTFIX)?, flat_args(args)?);
            if chain_too_wide(e, &text) {
                return None;
            }
            text
        }
        Expr::Macro(name, args) if name == "vec" => format!("vec![{}]", flat_args(args)?),
        Expr::Macro(name, args) => format!("{name}!({})", flat_args(args)?),
        Expr::Bin(op, a, b) => {
            let (pa, pb) = bin_parts(*op, a, b);
            format!(
                "{} {} {}",
                paren(flat(a)?, pa),
                op.text(),
                paren(flat(b)?, pb)
            )
        }
        Expr::Unary(op, x) => format!("{}{}", op.text(), operand(x, UNARY)?),
        Expr::Cast(x, ty) => format!("{} as {ty}", operand(x, AS)?),
        Expr::Try(x) => {
            let text = format!("{}?", operand(x, POSTFIX)?);
            if chain_too_wide(e, &text) {
                return None;
            }
            text
        }
        Expr::Closure(params, body) if body.stmts.is_empty() => {
            let tail = body.tail.as_deref()?;
            if matches!(tail, Expr::Block(_) | Expr::If(..)) {
                return None;
            }
            format!("|{}| {}", params.join(", "), flat(tail)?)
        }
        Expr::Return(None) => "return".into(),
        Expr::Return(Some(v)) => format!("return {}", flat(v)?),
        Expr::Field(r, name) => {
            let text = format!("{}.{name}", operand(r, POSTFIX)?);
            if chain_too_wide(e, &text) {
                return None;
            }
            text
        }
        Expr::Struct(name, fields) if fields.is_empty() => format!("{name} {{}}"),
        Expr::Struct(name, fields) => {
            let parts: Option<Vec<String>> = fields
                .iter()
                .map(|(f, v)| match v {
                    None => Some(f.clone()),
                    Some(v) => Some(format!("{f}: {}", flat(v)?)),
                })
                .collect();
            let body = parts?.join(", ");
            if width(&body) > STRUCT_LIT_WIDTH {
                return None;
            }
            format!("{name} {{ {body} }}")
        }
        Expr::Move(c) => format!("move {}", flat(c)?),
        Expr::If(..) | Expr::Block(_) | Expr::Closure(..) => return None,
    })
}

/// `if c { a } else { b }` on one line, for after `=`: each branch just a
/// flat value, within rustfmt's width for it.
fn flat_if(e: &Expr) -> Option<String> {
    let Expr::If(c, t, f) = e else {
        return None;
    };
    let branch = |b: &Block| match (&b.stmts[..], b.tail.as_deref()) {
        ([], Some(v)) if !matches!(v, Expr::If(..)) => flat(v),
        _ => None,
    };
    let text = format!(
        "if {} {{ {} }} else {{ {} }}",
        flat(c)?,
        branch(t)?,
        branch(f)?
    );
    (width(&text) <= IF_WIDTH).then_some(text)
}

/// An expression starting at column `col`, with `indent` the indentation
/// of the line it starts on: on one line if it fits, else broken as
/// rustfmt breaks it.
fn layout(e: &Expr, indent: usize, col: usize) -> String {
    layout_t(e, indent, col, 0)
}

/// As [`layout`], with `trail` columns after it on its line (a `;`, a
/// `,`, or ` {`).
fn layout_t(e: &Expr, indent: usize, col: usize, trail: usize) -> String {
    if let Some(text) = flat(e) {
        if col + width(&text) + trail + try_penalty(e) <= MAX {
            return text;
        }
    }
    match e {
        Expr::Method(..) | Expr::Field(..) | Expr::Try(_) => chain(e, indent, col, trail),
        Expr::Unary(op, x) if x.prec() >= UNARY => {
            format!("{}{}", op.text(), layout_t(x, indent, col + 1, trail))
        }
        _ => broken(e, indent, col),
    }
}

/// rustfmt keeps a chain that ends in `?` on one line only with two
/// columns to spare.
fn try_penalty(e: &Expr) -> usize {
    match e {
        Expr::Try(x) if matches!(**x, Expr::Method(..)) => 2,
        _ => 0,
    }
}

/// An expression laid out on several lines where it can be.
fn broken(e: &Expr, indent: usize, col: usize) -> String {
    memo(e, indent, col, || Some(broken_made(e, indent, col))).unwrap_or_default()
}

/// [`broken`], made.
fn broken_made(e: &Expr, indent: usize, col: usize) -> String {
    match e {
        Expr::Call(f, args) => {
            let head = layout(f, indent, col);
            let head = paren(head, f.prec() < POSTFIX || matches!(**f, Expr::Field(..)));
            call(&head, args, indent, col, true)
        }
        Expr::Macro(name, args) if name == "vec" => {
            bracketed("vec!", args, indent, col, true, ('[', ']'))
        }
        Expr::Macro(name, args) => call(&format!("{name}!"), args, indent, col, false),
        Expr::Method(..) | Expr::Field(..) | Expr::Try(_) => chain(e, indent, col, 0),
        Expr::If(c, t, f) => if_else(c, t, f, indent, col),
        Expr::Let(pattern, x) => {
            let head = format!("let {pattern} = ");
            format!("{head}{}", layout(x, indent, col + width(&head)))
        }
        Expr::Block(b) => braced(b, indent),
        Expr::Closure(params, body) => {
            let head = format!("|{}| ", params.join(", "));
            // A body that's one unlabeled loop is the loop, as rustfmt
            // writes it.
            if let ([Stmt::Loop { label: None, body }], None) =
                (body.stmts.as_slice(), body.tail.as_deref())
            {
                return format!("{head}loop {}", braced(body, indent));
            }
            format!("{head}{}", braced(body, indent))
        }
        Expr::Move(c) => format!("move {}", layout(c, indent, col + 5)),
        Expr::Return(Some(v)) => format!("return {}", layout(v, indent, col + 7)),
        Expr::Unary(op, x) => {
            let inner = paren(layout(x, indent, col + 1), x.prec() < UNARY);
            format!("{}{inner}", op.text())
        }
        Expr::Cast(x, ty) => format!("{} as {ty}", paren(layout(x, indent, col), x.prec() < AS)),
        // Both operands flat, each on a line of its own, the operator
        // leading the second, as rustfmt's `rewrite_pair` prefers to
        // breaking the right one's call: `a(0)\n    == b(1)`.
        Expr::Bin(op, a, b)
            if matches!((flat(a), flat(b)), (Some(l), Some(r))
                if col + width(&l) <= MAX
                    && indent + 4 + width(op.text()) + 1 + width(&r) + 4 <= MAX)
                && !matches!(&**a, Expr::Bin(..))
                && !matches!(&**b, Expr::Bin(..)) =>
        {
            operands(e, indent, col)
        }
        Expr::Bin(op, a, b) => {
            let (pa, pb) = bin_parts(*op, a, b);
            let left = paren(layout(a, indent, col), pa);
            let mid = format!("{left} {} ", op.text());
            let right = paren(layout(b, indent, end_col(col, &mid)), pb);
            format!("{mid}{right}")
        }
        Expr::Struct(name, fields) => {
            let mut out = format!("{name} {{\n");
            let at = indent + 4;
            for (f, v) in fields {
                match v {
                    None => out.push_str(&format!("{}{f},\n", pad(at))),
                    Some(v) => {
                        let head = format!("{f}: ");
                        let text = layout_t(v, at, at + width(&head), 1);
                        out.push_str(&format!("{}{head}{text},\n", pad(at)));
                    }
                }
            }
            out.push_str(&pad(indent));
            out.push('}');
            out
        }
        Expr::Path(_) | Expr::Lit(_) | Expr::Str(_) | Expr::Return(None) => {
            flat(e).unwrap_or_default()
        }
    }
}

/// `{`, a block's lines, and `}` at `indent`; `{}` if it's empty.
fn braced(b: &Block, indent: usize) -> String {
    if b.is_empty() {
        "{}".into()
    } else {
        format!("{{\n{}{}}}", block(b, indent + 4), pad(indent))
    }
}

/// A call whose arguments don't fit on its line: its last argument
/// overflowing, if it's a closure or the only argument and a call, else
/// one argument per line, with a trailing comma unless it's a macro's.
fn call(head: &str, args: &[Expr], indent: usize, col: usize, comma: bool) -> String {
    bracketed(head, args, indent, col, comma, ('(', ')'))
}

/// A call's layout, or with `[` and `]`, `vec!`'s.
fn bracketed(
    head: &str,
    args: &[Expr],
    indent: usize,
    col: usize,
    comma: bool,
    (open_ch, close_ch): (char, char),
) -> String {
    let open = format!("{head}{open_ch}");
    let after = end_col(col, &open);
    // A lone argument that's a call with `?`, as `f(g(…)?)`, too long for
    // a line of its own: rustfmt overflows the call, and keeps a comma
    // after the `?`, `f(g(\n…\n)?,)`.
    if let [Expr::Try(x)] = args {
        let own_line = flat(&args[0]).is_some_and(|t| indent + 4 + width(&t) < MAX);
        if matches!(**x, Expr::Call(..)) && !own_line {
            let inner = broken(x, indent, after);
            if inner.contains('\n') && after + width(first_line(&inner)) <= MAX {
                return format!("{open}{inner}?,{close_ch}");
            }
        }
    }
    if let Some((last, init)) = args.split_last() {
        let overflows = matches!(
            last,
            Expr::Closure(..) | Expr::Move(_) | Expr::Struct(..) | Expr::Block(_)
        ) || (init.is_empty() && matches!(last, Expr::Call(..) | Expr::Macro(..)))
            // `Some(if b {`, as rustfmt overflows a lone `if`, but not an
            // `if let`, whose condition may break.
            || (init.is_empty()
                && matches!(last, Expr::If(c, ..) if !matches!(**c, Expr::Let(..))))
            || (init.is_empty() && unary_call(last))
            || unary_block(last);
        if overflows {
            let prefix = if init.is_empty() {
                Some(String::new())
            } else {
                flat_args(init).map(|p| format!("{p}, "))
            };
            if let Some(prefix) = prefix {
                let at = after + width(&prefix);
                let closure = matches!(last, Expr::Closure(..) | Expr::Move(_));
                // A closure whose body is one expression stays on the
                // call's line where it fits, as rustfmt overflows it: a
                // lone argument even past `fn_call_width`, `.f(|_|
                // a.b().c(|| d))`, else all the arguments within it.
                let flat_closure = flat(last).filter(|t| {
                    closure
                        && at + width(t) < MAX
                        && (init.is_empty() || width(&prefix) + width(t) <= CALL_WIDTH)
                });
                let last = flat_closure.unwrap_or_else(|| broken(last, indent, at));
                // A closure on one line, as rustfmt lets the last argument
                // overflow where the line fits.
                let one_line = closure && !last.contains('\n') && at + width(&last) < MAX;
                if at + width(first_line(&last)) <= MAX && (last.contains('\n') || one_line) {
                    return format!("{open}{prefix}{last}{close_ch}");
                }
            }
        }
    }
    if args.is_empty() {
        return format!("{open}{close_ch}");
    }
    let mut out = format!("{open}\n");
    // A format macro's arguments after its string, if each is simple, as
    // many to a line as fit, as rustfmt's mixed layout of them.
    if let (false, [Expr::Str(_), rest @ ..]) = (comma, args) {
        let flats: Option<Vec<String>> = rest
            .iter()
            .map(|a| flat(a).filter(|_| simple_arg(a)))
            .collect();
        if let (false, Some(flats)) = (rest.is_empty(), flats) {
            let at = indent + 4;
            let first = layout_t(&args[0], at, at, 1);
            out.push_str(&format!("{}{first},\n", pad(at)));
            let mut line = String::new();
            for (i, f) in flats.iter().enumerate() {
                let sep = if i + 1 < flats.len() { "," } else { "" };
                let piece = format!("{f}{sep}");
                if !line.is_empty() && at + width(&line) + 1 + width(&piece) > MAX {
                    out.push_str(&format!("{}{line}\n", pad(at)));
                    line.clear();
                }
                if !line.is_empty() {
                    line.push(' ');
                }
                line.push_str(&piece);
            }
            out.push_str(&format!("{}{line}\n", pad(at)));
            out.push_str(&pad(indent));
            out.push(close_ch);
            return out;
        }
    }
    for (i, a) in args.iter().enumerate() {
        let sep = if comma || i + 1 < args.len() { "," } else { "" };
        let at = indent + 4;
        let text = match flat_if(a) {
            Some(text) if at + width(&text) + sep.len() <= MAX => text,
            _ => layout_t(a, at, at, sep.len()),
        };
        out.push_str(&format!("{}{text}{sep}\n", pad(at)));
    }
    out.push_str(&pad(indent));
    out.push(close_ch);
    out
}

/// Whether an expression is a call under `&` and maybe more unary
/// operators, as `&*f(…)`, which rustfmt lets overflow as a lone
/// argument.
fn unary_call(e: &Expr) -> bool {
    match e {
        Expr::Unary(Unary::Ref, x) => {
            let mut x = &**x;
            while let Expr::Unary(_, inner) = x {
                x = inner;
            }
            matches!(x, Expr::Call(..) | Expr::Macro(..))
        }
        _ => false,
    }
}

/// Whether an expression is a block under unary operators, as `&*{ … }`,
/// which rustfmt lets overflow as it does a block.
fn unary_block(e: &Expr) -> bool {
    match e {
        Expr::Unary(_, x) => matches!(**x, Expr::Block(_)) || unary_block(x),
        _ => false,
    }
}

/// A chain of method calls that doesn't fit on one line: with one call,
/// as a call of `receiver.method`; with more, each call on a line of its
/// own after the receiver.
fn chain(e: &Expr, indent: usize, col: usize, trail: usize) -> String {
    let (base, links) = links(e);
    // The `?`s that join the root count on its line, and what follows the
    // chain too if they're all it has.
    let tries = links.iter().take_while(|l| matches!(l, Link::Try)).count();
    let root_trail = tries + if tries == links.len() { trail } else { 0 };
    // An `if`'s value as the root, on one line where rustfmt keeps it:
    // `(if c { 1i32 } else { 2 })`.
    let root = match flat_if(base) {
        Some(text) if col + width(&text) + 2 <= MAX => paren(text, true),
        _ => paren(
            layout_t(base, indent, col, root_trail),
            base.prec() < POSTFIX,
        ),
    };
    // rustfmt's tab width less how far the chain starts past its indent,
    // as after `x = `, where only a root at the indent takes a link.
    let root_width = if root.contains('\n') {
        MAX
    } else {
        width(&root) + col.saturating_sub(indent)
    };
    let (head, groups) = chain_groups(root_width, &links);
    let mut out = root;
    // The root's line, its last call's arguments broken if they must be.
    let last_call = head.iter().rposition(|l| matches!(l, Link::Call(..)));
    for (i, l) in head.iter().enumerate() {
        match (l, groups.is_empty() && Some(i) == last_call) {
            (Link::Call(name, args), true) => {
                out = call(&format!("{out}.{name}"), args, indent, col, true);
            }
            (l, _) => match l.flat() {
                Some(text) => out.push_str(&text),
                // Arguments too wide for one line: broken.
                None => {
                    if let Link::Call(name, args) = l {
                        out = call(&format!("{out}.{name}"), args, indent, col, true);
                    }
                }
            },
        }
    }
    if groups.len() == 1 && !out.contains('\n') {
        // One more link: a call, its arguments broken if they must be.
        if let [Link::Call(name, args), rest @ ..] = &groups[0][..] {
            let one = flat_args(args).map(|a| format!("{out}.{name}({a})"));
            let overflows = matches!(
                args.last(),
                Some(Expr::Closure(..) | Expr::Move(_) | Expr::Struct(..) | Expr::Block(_))
            );
            // Else, as rustfmt, the call on a line of its own, if it fits.
            let next = flat_args(args).map(|a| format!(".{name}({a})"));
            let broken_call = call(&format!("{out}.{name}"), args, indent, col, true);
            let overflows = overflows && first_line(&broken_call).ends_with('{');
            // As rustfmt, a closure overflows its call only if that takes
            // 5 lines or more; else the call goes on the next line if it
            // fits there on one.
            let overflows = overflows && broken_call.lines().count() >= 5;
            // A call on the root's line makes two of rustfmt's children,
            // which share its `chain_width`.
            let head_call = head.iter().any(|l| matches!(l, Link::Call(..)));
            let mut text = match one {
                Some(text)
                    if end_col(col, &text) + 3 * rest.len() + trail <= MAX
                        && !(head_call && width(&text) > CHAIN_WIDTH) =>
                {
                    text
                }
                _ if !overflows
                    && next
                        .as_ref()
                        .is_some_and(|n| indent + 4 + width(n) + rest.len() + trail <= MAX) =>
                {
                    format!("{out}\n{}{}", pad(indent + 4), next.unwrap_or_default())
                }
                _ => broken_call,
            };
            for l in rest {
                text.push_str(&l.flat().unwrap_or_default());
            }
            return text;
        }
    }
    // After a root of several lines, as `(|| { … })()`, the links line
    // up with it, as rustfmt lays them.
    let at = if out.contains('\n') {
        indent
    } else {
        indent + 4
    };
    for group in groups {
        let mut text = String::new();
        for l in group {
            match l {
                Link::Call(name, args) => {
                    let one = flat_args(args).map(|a| format!(".{name}({a})"));
                    match one {
                        Some(one) if at + width(&text) + width(&one) <= MAX => text.push_str(&one),
                        _ => text.push_str(&call(&format!(".{name}"), args, at, at, true)),
                    }
                }
                l => text.push_str(&l.flat().unwrap_or_default()),
            }
        }
        out.push_str(&format!("\n{}{text}", pad(at)));
    }
    out
}

fn if_else(c: &Expr, t: &Block, f: &Block, indent: usize, col: usize) -> String {
    let cond = condition(c, indent, col + 3);
    let then = open_brace(&cond, indent, braced(t, indent));
    let mut out = format!("if {}{then}", cond.0);
    match (&f.stmts[..], f.tail.as_deref()) {
        ([], Some(Expr::If(c2, t2, f2))) => {
            let rest = if_else(c2, t2, f2, indent, indent + 7);
            out.push_str(&format!(" else {rest}"));
        }
        // `else if`, as clippy's `collapsible_else_if` wants, for an
        // `else` that's just an `if` statement.
        ([Stmt::If { cond, then, els }], None) => {
            let rest = match els
                .as_ref()
                .filter(|e| !e.stmts.is_empty() || e.tail.is_some())
            {
                Some(els) => if_else(cond, then, els, indent, indent + 7),
                None => {
                    let cond = condition(cond, indent, indent + 10);
                    let then = open_brace(&cond, indent, braced(then, indent));
                    format!("if {}{then}", cond.0)
                }
            };
            out.push_str(&format!(" else {rest}"));
        }
        _ => out.push_str(&format!(" else {}", braced(f, indent))),
    }
    out
}

/// An `if`'s or `while`'s condition, from `col`: on one line if it fits
/// with ` {` after it, else, for a binary operator, as rustfmt breaks it,
/// each operand of a run of one operator on a line of its own from the
/// second, after the operator, 4 columns in from the line that starts it.
/// With whether it was broken at its operators, which puts the `{` after
/// it on a line of its own.
fn condition(e: &Expr, indent: usize, col: usize) -> (String, bool) {
    if let Some(text) = flat(e) {
        if col + width(&text) + 2 <= MAX {
            return (text, false);
        }
    }
    match e {
        // `let Some(x) = x`, written out, its value on the next line.
        Expr::Path(text) if text.starts_with("let ") && text.contains(" = ") => {
            let (head, value) = text.split_once(" = ").unwrap_or_default();
            (format!("{head} =\n{}{value}", pad(indent + 4)), true)
        }
        // `let Ok(v) = e` (`if_ok`), its value on the next line, as rustfmt
        // breaks a `let` that doesn't fit, and the `{` on a line of its own;
        // where it doesn't fit there either, after the `=`, broken.
        Expr::Let(pattern, x) => match flat(x) {
            Some(value) if indent + 4 + width(&value) <= MAX => {
                (format!("let {pattern} =\n{}{value}", pad(indent + 4)), true)
            }
            _ => {
                let head = format!("let {pattern} = ");
                let value = layout(x, indent, col + width(&head));
                (format!("{head}{value}"), true)
            }
        },
        // One operator, its left operand broken over lines and its right
        // fitting after it on the last, as rustfmt's `rewrite_pair` puts
        // it: `f(\n    x,\n) == 200`.
        Expr::Bin(op, a, b) if !matches!(&**a, Expr::Bin(o, ..) if o == op) => {
            let (pa, pb) = bin_parts(*op, a, b);
            let left = paren(layout(a, indent, col + usize::from(pa)), pa);
            let last = left.rsplit('\n').next().unwrap_or("");
            let right = flat(b).map(|r| paren(r, pb));
            match right {
                Some(r)
                    if left.contains('\n')
                        && width(last) + width(op.text()) + width(&r) + 4 <= MAX =>
                {
                    let text = format!("{left} {} {r}", op.text());
                    let open = !text
                        .rsplit('\n')
                        .next()
                        .unwrap_or("")
                        .trim()
                        .chars()
                        .all(|c| ")]}".contains(c));
                    (text, open)
                }
                _ => (operands(e, indent, col), true),
            }
        }
        Expr::Bin(..) => (operands(e, indent, col), true),
        _ => {
            // Broken, and ending on more than closing brackets, as a
            // method chain's `.is_err()`, it takes the `{` on a line of
            // its own, as rustfmt puts it.
            let text = layout_t(e, indent, col, 2);
            let last = text.rsplit('\n').next().unwrap_or("").trim();
            let open = text.contains('\n') && !last.chars().all(|c| ")]}".contains(c));
            (text, open)
        }
    }
}

/// A binary operator's operands, one to a line from the second.
fn operands(e: &Expr, indent: usize, col: usize) -> String {
    let Expr::Bin(op, ..) = e else {
        return layout(e, indent, col);
    };
    // `a && b && c`, its operands each with whether it needs parentheses.
    let mut parts: Vec<(&Expr, bool)> = Vec::new();
    let mut at = e;
    while let Expr::Bin(o, a, b) = at {
        if o != op {
            break;
        }
        let (pa, pb) = bin_parts(*o, a, b);
        parts.push((b, pb));
        match &**a {
            Expr::Bin(o2, ..) if o2 == op && !pa => at = a,
            _ => {
                parts.push((a, pa));
                break;
            }
        }
    }
    parts.reverse();
    let next = indent + 4;
    let mut out = String::new();
    for (i, &(x, parens)) in parts.iter().enumerate() {
        let (start, lead) = if i == 0 {
            (col, String::new())
        } else {
            let lead = format!("\n{}{} ", pad(next), op.text());
            (next + width(op.text()) + 1, lead)
        };
        let line_indent = if i == 0 { indent } else { next };
        let text = match flat(x) {
            Some(t) if start + width(&t) + usize::from(parens) * 2 <= MAX => t,
            _ if !parens && matches!(x, Expr::Bin(..)) => operands(x, line_indent, start),
            _ => layout(x, line_indent, start + usize::from(parens)),
        };
        out.push_str(&lead);
        out.push_str(&paren(text, parens));
    }
    out
}

/// ` {` and a block after a condition; after one broken at its operators
/// onto several lines, the `{` on a line of its own.
fn open_brace(cond: &(String, bool), indent: usize, block: String) -> String {
    if cond.1 && cond.0.contains('\n') {
        format!("\n{}{block}", pad(indent))
    } else {
        format!(" {block}")
    }
}

/// A block's lines at `indent`, each ending in a newline.
fn block(b: &Block, indent: usize) -> String {
    let mut out = String::new();
    for s in &b.stmts {
        out.push_str(&stmt(s, indent));
    }
    if let Some(tail) = &b.tail {
        out.push_str(&format!(
            "{}{}\n",
            pad(indent),
            layout(tail, indent, indent)
        ));
    }
    out
}

/// What follows `=`: an `if` on one line only if short.
///
/// As rustfmt, a value too long for the line goes on the next, indented,
/// where it fits on one line there.
fn rhs(e: &Expr, indent: usize, col: usize) -> String {
    if let Some(text) = flat_if(e) {
        if col + width(&text) < MAX {
            return text;
        }
    }
    if let Some(text) = flat(e) {
        let next = indent + 4;
        let breakable = !matches!(
            e,
            Expr::Closure(..) | Expr::Move(_) | Expr::Block(_) | Expr::If(..) | Expr::Struct(..)
        );
        // rustfmt counts a value's last `?`s twice, as `chain_too_wide`.
        let tries = 2 * (text.len() - text.trim_end_matches('?').len());
        if breakable && col + width(&text) + 1 > MAX && next + width(&text) + tries < MAX {
            return format!("\n{}{text}", pad(next));
        }
    }
    let same = layout_t(e, indent, col, 1);
    // An `if` too, where its condition breaks on the same line.
    let breakable = !matches!(
        e,
        Expr::Closure(..) | Expr::Move(_) | Expr::Block(_) | Expr::Struct(..)
    );
    if breakable && same.contains('\n') {
        // As rustfmt's `prefer_next_line`: on the next line instead, if
        // there it doesn't open with a call left hanging, or it takes
        // fewer lines.
        let next = layout_t(e, indent + 4, indent + 4, 1);
        let ends = |t: &str, c: char| first_line(t).ends_with(c);
        let lines = |t: &str| t.matches('\n').count();
        let fits = indent + 4 + width(first_line(&next)) <= MAX;
        // As rustfmt's `choose_rhs`, too: on the next line where the
        // same line can't even hold the value's first line.
        let better = !next.contains('\n')
            || col + width(first_line(&same)) > MAX
            || lines(&same) > lines(&next) + 1
            || (ends(&same, '(') && !ends(&next, '('))
            || (ends(&same, '{') && !ends(&next, '{'))
            || (ends(&same, '[') && !ends(&next, '['));
        if fits && better {
            return format!("\n{}{next}", pad(indent + 4));
        }
    }
    same
}

/// A `let`'s head whose type doesn't fit its line (with ` =`), its
/// outermost type arguments each on a line of their own, ending at the
/// `>`; `None` if it fits, or has none.
fn broken_type(head: &str, indent: usize) -> Option<String> {
    if indent + width(head) + 2 <= MAX || !head.ends_with('>') {
        return None;
    }
    let colon = head.find(": ")?;
    let open = colon + head[colon..].find('<')?;
    let inner = &head[open + 1..head.len() - 1];
    let mut args = Vec::new();
    let (mut depth, mut start) = (0, 0);
    for (i, c) in inner.char_indices() {
        match c {
            '<' | '(' | '[' => depth += 1,
            '>' | ')' | ']' => depth -= 1,
            ',' if depth == 0 => {
                args.push(inner[start..i].trim());
                start = i + 1;
            }
            _ => {}
        }
    }
    args.push(inner[start..].trim());
    let p = pad(indent + 4);
    let mut out = format!("{}<\n", &head[..open]);
    for a in args {
        out.push_str(&format!("{p}{a},\n"));
    }
    out.push_str(&pad(indent));
    out.push('>');
    Some(out)
}

fn stmt(s: &Stmt, indent: usize) -> String {
    let p = pad(indent);
    let label = |l: &Option<String>| l.as_ref().map(|l| format!("{l}: ")).unwrap_or_default();
    let jump = |kw: &str, l: &Option<String>| match l {
        Some(l) => format!("{p}{kw} {l};\n"),
        None => format!("{p}{kw};\n"),
    };
    match s {
        Stmt::Let {
            name,
            mutable,
            ty,
            value,
        } => {
            let m = if *mutable { "mut " } else { "" };
            let ty = ty.as_ref().map(|t| format!(": {t}")).unwrap_or_default();
            let head = format!("let {m}{name}{ty}");
            // A type too long for its line breaks between its outermost
            // type arguments, as rustfmt does: `let x: A<\n    B<C>,\n> = …`.
            if let (Some(v), Some(broken)) = (value, broken_type(&head, indent)) {
                let col = indent + 4;
                return format!("{p}{broken} = {};\n", rhs(v, indent, col))
                    .replacen(" = \n", " =\n", 1);
            }
            match value {
                Some(v) => {
                    let col = indent + width(&head) + 3;
                    format!("{p}{head} = {};\n", rhs(v, indent, col)).replacen(" = \n", " =\n", 1)
                }
                None => format!("{p}{head};\n"),
            }
        }
        Stmt::Assign(place, value) => {
            let place = layout(place, indent, indent);
            let col = end_col(indent, &place) + 3;
            format!("{p}{place} = {};\n", rhs(value, indent, col)).replacen(" = \n", " =\n", 1)
        }
        Stmt::Expr(e) => format!("{p}{};\n", layout_t(e, indent, indent, 1)),
        Stmt::If { cond, then, els } => {
            // Borrowed, not cloned: [`memo`] knows expressions by address.
            let empty = Block::default();
            let els = els.as_ref().unwrap_or(&empty);
            if els.is_empty() {
                let cond = condition(cond, indent, indent + 3);
                let then = open_brace(&cond, indent, braced(then, indent));
                format!("{p}if {}{then}\n", cond.0)
            } else {
                format!("{p}{}\n", if_else(cond, then, els, indent, indent))
            }
        }
        Stmt::While {
            label: l,
            cond,
            body,
        } => {
            let head = format!("{}while ", label(l));
            let cond = condition(cond, indent, indent + width(&head));
            let body = open_brace(&cond, indent, braced(body, indent));
            format!("{p}{head}{}{body}\n", cond.0)
        }
        Stmt::Match { scrutinee, arms } => {
            let head = condition(scrutinee, indent, indent + 6);
            let mut body = String::from("{\n");
            for (pattern, arm) in arms {
                let arm = braced(arm, indent + 4);
                body.push_str(&format!("{}{pattern} => {arm}\n", pad(indent + 4)));
            }
            body.push_str(&format!("{p}}}"));
            format!("{p}match {}{}\n", head.0, open_brace(&head, indent, body))
        }
        Stmt::Loop { label: l, body } => {
            format!("{p}{}loop {}\n", label(l), braced(body, indent))
        }
        Stmt::Labeled { label: l, body } => format!("{p}{l}: {}\n", braced(body, indent)),
        Stmt::Break(l) => jump("break", l),
        Stmt::Continue(l) => jump("continue", l),
        Stmt::Return(None) => format!("{p}return;\n"),
        Stmt::Return(Some(v)) => format!("{p}return {};\n", layout_t(v, indent, indent + 7, 1)),
        Stmt::Static { name, ty, value } => {
            let head = format!("static {name}: {ty} = ");
            static_at(&head, value, indent)
        }
        Stmt::Block(b) => format!("{p}{}\n", braced(b, indent)),
        Stmt::TestsOnly(b) => format!("{p}#[cfg(test)]\n{p}{}\n", braced(b, indent)),
        Stmt::Item(item) => item_at(item, indent),
    }
}

/// Whether statements never finish normally: they end in a `return`,
/// a jump, or a `panic!()`, on every path.
pub fn diverges(stmts: &[Stmt]) -> bool {
    let block = |b: &Block| b.tail.is_none() && diverges(&b.stmts);
    match stmts.last() {
        Some(Stmt::Return(_) | Stmt::Break(_) | Stmt::Continue(_)) => true,
        Some(Stmt::Expr(Expr::Return(_))) => true,
        Some(Stmt::Expr(Expr::Macro(name, _))) => name == "panic",
        Some(Stmt::If {
            then,
            els: Some(els),
            ..
        }) => block(then) && block(els),
        Some(Stmt::Block(b)) => block(b),
        Some(Stmt::Match { arms, .. }) => arms.iter().all(|(_, b)| block(b)),
        _ => false,
    }
}

/// `if let Ok(v) = e { v } else { … }`: a `Result`'s value, or `els`,
/// which jumps or bubbles; `None` if `e` doesn't fit on a line, which the
/// `let` needs.
pub fn if_ok(e: &Expr, els: Block) -> Option<Expr> {
    flat(e)?;
    Some(Expr::If(
        Box::new(Expr::Let("Ok(v)".into(), Box::new(e.clone()))),
        Block::of(Expr::path("v")),
        els,
    ))
}

/// Each `?` in `stmts`, outside their closures, as a break out of the
/// labeled block `label` where it fails: `if let Ok(v) = e { v } else {
/// break 'orelse; }`, for an `orelse`'s body run in place. False if an
/// `e` doesn't fit on a line, which the `let` needs.
pub fn retarget_tries(stmts: &mut [Stmt], label: &str) -> bool {
    stmts.iter_mut().all(|s| retarget_stmt(s, label))
}

fn retarget_stmt(s: &mut Stmt, label: &str) -> bool {
    match s {
        Stmt::Let { value, .. } => value.as_mut().is_none_or(|v| retarget_expr(v, label)),
        Stmt::Assign(a, b) => retarget_expr(a, label) && retarget_expr(b, label),
        Stmt::Expr(e) => retarget_expr(e, label),
        Stmt::Return(e) => e.as_mut().is_none_or(|e| retarget_expr(e, label)),
        Stmt::If { cond, then, els } => {
            retarget_expr(cond, label)
                && retarget_block(then, label)
                && els.as_mut().is_none_or(|b| retarget_block(b, label))
        }
        Stmt::While { cond, body, .. } => retarget_expr(cond, label) && retarget_block(body, label),
        Stmt::Match { scrutinee, arms } => {
            retarget_expr(scrutinee, label)
                && arms.iter_mut().all(|(_, b)| retarget_block(b, label))
        }
        Stmt::Loop { body, .. }
        | Stmt::Labeled { body, .. }
        | Stmt::Block(body)
        | Stmt::TestsOnly(body) => retarget_block(body, label),
        Stmt::Static { value, .. } => retarget_expr(value, label),
        Stmt::Break(_) | Stmt::Continue(_) | Stmt::Item(_) => true,
    }
}

fn retarget_block(b: &mut Block, label: &str) -> bool {
    retarget_tries(&mut b.stmts, label) && b.tail.as_mut().is_none_or(|t| retarget_expr(t, label))
}

fn retarget_expr(e: &mut Expr, label: &str) -> bool {
    match e {
        Expr::Try(x) => {
            if !retarget_expr(x, label) {
                return false;
            }
            let els = Block::new(vec![Stmt::Break(Some(label.to_string()))], None);
            let Some(made) = if_ok(x, els) else {
                return false;
            };
            *e = made;
            true
        }
        Expr::Call(f, args) => {
            retarget_expr(f, label) && args.iter_mut().all(|a| retarget_expr(a, label))
        }
        Expr::Method(r, _, args) => {
            retarget_expr(r, label) && args.iter_mut().all(|a| retarget_expr(a, label))
        }
        Expr::Macro(_, args) => args.iter_mut().all(|a| retarget_expr(a, label)),
        Expr::Bin(_, a, b) => retarget_expr(a, label) && retarget_expr(b, label),
        Expr::Unary(_, x) | Expr::Cast(x, _) | Expr::Field(x, _) => retarget_expr(x, label),
        Expr::If(c, t, f) => {
            retarget_expr(c, label) && retarget_block(t, label) && retarget_block(f, label)
        }
        Expr::Block(b) => retarget_block(b, label),
        Expr::Let(_, x) => retarget_expr(x, label),
        Expr::Return(x) => x.as_mut().is_none_or(|x| retarget_expr(x, label)),
        Expr::Struct(_, fields) => {
            (fields.iter_mut()).all(|(_, v)| v.as_mut().is_none_or(|v| retarget_expr(v, label)))
        }
        // A closure's `?`s are its own.
        Expr::Closure(..) | Expr::Move(_) => true,
        Expr::Path(_) | Expr::Lit(_) | Expr::Str(_) => true,
    }
}

/// Whether statements leave early, outside their closures: by a `?` or a
/// `return`. A closure called in place for them (`(|| { …; Ok(()) })()`)
/// can't fail without either.
pub fn leave_early(stmts: &[Stmt]) -> bool {
    stmts.iter().any(stmt_leaves)
}

fn stmt_leaves(s: &Stmt) -> bool {
    match s {
        Stmt::Let { value, .. } => value.as_ref().is_some_and(expr_leaves),
        Stmt::Assign(a, b) => expr_leaves(a) || expr_leaves(b),
        Stmt::Expr(e) | Stmt::Static { value: e, .. } => expr_leaves(e),
        Stmt::Return(_) => true,
        Stmt::If { cond, then, els } => {
            expr_leaves(cond) || block_leaves(then) || els.as_ref().is_some_and(block_leaves)
        }
        Stmt::While { cond, body, .. } => expr_leaves(cond) || block_leaves(body),
        Stmt::Match { scrutinee, arms } => {
            expr_leaves(scrutinee) || arms.iter().any(|(_, b)| block_leaves(b))
        }
        Stmt::Loop { body, .. }
        | Stmt::Labeled { body, .. }
        | Stmt::Block(body)
        | Stmt::TestsOnly(body) => block_leaves(body),
        Stmt::Break(_) | Stmt::Continue(_) | Stmt::Item(_) => false,
    }
}

fn block_leaves(b: &Block) -> bool {
    leave_early(&b.stmts) || b.tail.as_deref().is_some_and(expr_leaves)
}

fn expr_leaves(e: &Expr) -> bool {
    match e {
        Expr::Try(_) | Expr::Return(_) => true,
        Expr::Call(f, args) => expr_leaves(f) || args.iter().any(expr_leaves),
        Expr::Method(r, _, args) => expr_leaves(r) || args.iter().any(expr_leaves),
        Expr::Macro(_, args) => args.iter().any(expr_leaves),
        Expr::Bin(_, a, b) => expr_leaves(a) || expr_leaves(b),
        Expr::Unary(_, x) | Expr::Cast(x, _) | Expr::Field(x, _) => expr_leaves(x),
        Expr::If(c, t, f) => expr_leaves(c) || block_leaves(t) || block_leaves(f),
        Expr::Block(b) => block_leaves(b),
        Expr::Let(_, x) => expr_leaves(x),
        Expr::Struct(_, fields) => {
            (fields.iter()).any(|(_, v)| v.as_ref().is_some_and(expr_leaves))
        }
        // A closure's `?`s and `return`s are its own.
        Expr::Closure(..) | Expr::Move(_) => false,
        _ => false,
    }
}

#[cfg(test)]
mod tests {
    use super::quote;

    /// What rustc or clippy reject in a literal, escaped.
    #[test]
    fn quotes_what_rust_rejects() {
        assert_eq!(quote("a\"b\\"), "\"a\\\"b\\\\\"");
        assert_eq!(quote("look\u{202e}out"), "\"look\\u{202e}out\"");
        assert_eq!(quote("\u{2066}x\u{2069}"), "\"\\u{2066}x\\u{2069}\"");
        assert_eq!(quote("a\u{200b}"), "\"a\\u{200b}\"");
        assert_eq!(quote("é"), "\"é\"");
    }
}
