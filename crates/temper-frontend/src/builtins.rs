//! The names every module starts with: macros, functions, values and types.
//! Macro expansion is in `macros`, and what functions do at run time in
//! `interp`.

/// A builtin, by what it is.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Builtin {
    Macro(Macro),
    Function(Function),
    Value(Constant),
    /// A type, by its name in [`TYPES`]. Types aren't checked yet.
    Type(u8),
}

/// Builtins that rewrite their call, needing only its syntax.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Macro {
    Let,
    /// `@decorator target`.
    Decorate,
    Fn,
    If,
    While,
    For,
    Do,
    Return,
    Break,
    Continue,
    Bubble,
    Assign,
    And,
    Or,
    Orelse,
    /// `when (x) { 0 -> a; is T -> b; else -> c }`.
    When,
    /// `a ?? b`: `b` if `a` is null.
    Coalesce,
    /// `a?.b`: null if `a` is, else `a.b`.
    ChainNull,
    /// `class` and `interface` declarations.
    Class,
    Interface,
    /// `import("./path")`, only as `let { a, b } = import(…)`.
    Import,
    /// `test("name") { … }`, a test case, and `assert(c) { "why" }` in
    /// one (`testing`).
    Test,
    Assert,
    /// `yield(x)`, `yield x` or a bare `yield`, in a generator's body.
    Yield,
    /// `await(p)` or `await p`, in an `async` block's body.
    Await,
    /// `throw x`, `try x`, and `x catch (e) { … }`: declared failures'
    /// syntax (RFC 164).
    Throw,
    Try,
    Catch,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Function {
    Add,
    /// Subtraction, or negation with one argument.
    Sub,
    Mul,
    Div,
    Mod,
    /// `a ** b`, for floats.
    Pow,
    Lt,
    Le,
    Gt,
    Ge,
    Eq,
    Ne,
    Not,
    BitAnd,
    BitOr,
    BitXor,
    Shl,
    /// `>>`, sign extending.
    Shr,
    /// `>>>`, zero filling.
    UShr,
    /// `~`, bitwise complement.
    BitNot,
    /// Concatenates its arguments' strings, for `"a${b}"`.
    Cat,
    Panic,
    /// `obj.name`, a property, or as a callee, a method.
    Dot,
    /// A function type, `fn (A): B`, kept for later type checking.
    FnType,
    /// `A throws B`, a type that may fail.
    Throws,
    /// `T<A, B>`, a generic type or function given type arguments. The
    /// tree keeps them for typing and backends; the interpreter ignores
    /// them, so it's `T` there.
    Angle,
    /// `T?`, a type or null. The interpreter, as for [`Function::Angle`],
    /// takes it as `T`.
    Nullable,
    /// `[a, b]`, a `List`.
    List,
    /// `ls[i]`.
    Index,
    /// `new C(args)`.
    New,
    /// `x as T`, failing if `x` isn't a `T`.
    As,
    /// `x is T`.
    Is,
    /// `a <=> b`: -1, 0 or 1.
    Cmp,
    /// `ignore(x)`: evaluates `x` for its effects.
    Ignore,
    /// `char'a'`, a tagged string's tag: the one code point in it.
    Char,
    /// `raw"a\b"`, a tag for strings whose escapes aren't decoded.
    Raw,
    /// `empty()`, the `Empty` value.
    Empty,
    /// `doPure { … }`: calls the block, whose result has no side effects.
    DoPure,
    /// `doneResult()`, a generator's `DoneResult`.
    DoneResult,
    /// `async { … }`: runs a generator block later, from the run queue.
    Async,
    /// `rgx(list(parts), list(values))`, a regex literal, `/a+/` or
    /// `rgx"a${b}"`, which settling rewrites (`regex`).
    Rgx,
    /// `caught()`, the failure a `catch (e)` binds: the one the `Orelse`
    /// whose fallback starts `let e = caught();` handles. Hidden.
    Caught,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Constant {
    True,
    False,
    Null,
    Void,
    Console,
    NaN,
    Infinity,
    /// The port of a local web server for std `net`'s functional tests,
    /// `testServerPort`, declared only when the library says
    /// ([`crate::library::Library::test_server_port`]).
    TestServerPort(i32),
}

/// The root of declared failure types (RFC 164), which every failure
/// class and sealed failure interface extends: its one spelling, so a
/// later rename (`Exception`?) is one line here and core's declaration.
pub const FAILURE_ROOT: &str = "Error";

/// Type names, resolved but not checked yet.
pub const TYPES: &[&str] = &[
    "AnyValue",
    "Boolean",
    "Bubble",
    "CoreError",
    "DenseBitVector",
    "Deque",
    "DoneResult",
    "Empty",
    FAILURE_ROOT,
    "Float64",
    "Generator",
    "GeneratorFn",
    "GeneratorResult",
    "Int",
    "Int32",
    "Int64",
    "InvalidCodePoint",
    "List",
    "ListBuilder",
    "Listed",
    "Map",
    "MapBuilder",
    "MapKey",
    "Mapped",
    "MissingKey",
    "Never",
    "NoResult",
    "NoStringIndex",
    "Pair",
    "ParseError",
    "Promise",
    "PromiseBuilder",
    "SafeGenerator",
    "String",
    "StringBuilder",
    "StringIndex",
    "StringIndexOption",
    "Type",
    "ValueResult",
    "Void",
];

/// Names and their builtins, apart from types.
pub const BUILTINS: &[(&str, Builtin)] = {
    use Builtin::{Function as F, Macro as M, Value as V};
    &[
        ("let", M(Macro::Let)),
        ("@", M(Macro::Decorate)),
        ("fn", M(Macro::Fn)),
        ("if", M(Macro::If)),
        ("while", M(Macro::While)),
        ("for", M(Macro::For)),
        ("do", M(Macro::Do)),
        ("return", M(Macro::Return)),
        ("break", M(Macro::Break)),
        ("continue", M(Macro::Continue)),
        ("bubble", M(Macro::Bubble)),
        ("=", M(Macro::Assign)),
        ("&&", M(Macro::And)),
        ("||", M(Macro::Or)),
        ("orelse", M(Macro::Orelse)),
        ("when", M(Macro::When)),
        ("??", M(Macro::Coalesce)),
        ("?.", M(Macro::ChainNull)),
        ("class", M(Macro::Class)),
        ("interface", M(Macro::Interface)),
        ("import", M(Macro::Import)),
        ("test", M(Macro::Test)),
        ("assert", M(Macro::Assert)),
        ("yield", M(Macro::Yield)),
        ("await", M(Macro::Await)),
        ("throw", M(Macro::Throw)),
        ("try", M(Macro::Try)),
        ("catch", M(Macro::Catch)),
        ("+", F(Function::Add)),
        ("-", F(Function::Sub)),
        ("*", F(Function::Mul)),
        ("/", F(Function::Div)),
        ("%", F(Function::Mod)),
        ("**", F(Function::Pow)),
        ("<", F(Function::Lt)),
        ("<=", F(Function::Le)),
        (">", F(Function::Gt)),
        (">=", F(Function::Ge)),
        ("==", F(Function::Eq)),
        ("!=", F(Function::Ne)),
        ("!", F(Function::Not)),
        ("&", F(Function::BitAnd)),
        ("|", F(Function::BitOr)),
        ("^", F(Function::BitXor)),
        ("<<", F(Function::Shl)),
        (">>", F(Function::Shr)),
        (">>>", F(Function::UShr)),
        ("~", F(Function::BitNot)),
        ("panic", F(Function::Panic)),
        (".", F(Function::Dot)),
        ("[]", F(Function::Index)),
        ("throws", F(Function::Throws)),
        ("?", F(Function::Nullable)),
        ("new", F(Function::New)),
        ("as", F(Function::As)),
        ("is", F(Function::Is)),
        ("<=>", F(Function::Cmp)),
        ("ignore", F(Function::Ignore)),
        ("char", F(Function::Char)),
        ("raw", F(Function::Raw)),
        ("empty", F(Function::Empty)),
        ("doPure", F(Function::DoPure)),
        ("doneResult", F(Function::DoneResult)),
        ("async", F(Function::Async)),
        ("rgx", F(Function::Rgx)),
        ("true", V(Constant::True)),
        ("false", V(Constant::False)),
        ("null", V(Constant::Null)),
        ("void", V(Constant::Void)),
        ("console", V(Constant::Console)),
        ("NaN", V(Constant::NaN)),
        ("Infinity", V(Constant::Infinity)),
    ]
};

impl Builtin {
    /// The builtin with a name, if any.
    pub fn named(name: &str) -> Option<Builtin> {
        if let Some(&(_, b)) = BUILTINS.iter().find(|(n, _)| *n == name) {
            return Some(b);
        }
        TYPES
            .iter()
            .position(|&t| t == name)
            .map(|i| Builtin::Type(i as u8))
    }

    /// The name it's known by.
    pub fn name(self) -> &'static str {
        match self {
            Builtin::Type(i) => TYPES[i as usize],
            Builtin::Function(Function::Cat) => "cat",
            Builtin::Function(Function::FnType) => "FnType",
            Builtin::Function(Function::Angle) => "<>",
            Builtin::Function(Function::List) => "list",
            Builtin::Function(Function::Caught) => "caught",
            Builtin::Value(Constant::TestServerPort(_)) => "testServerPort",
            b => BUILTINS
                .iter()
                .find(|(_, x)| *x == b)
                .map_or("?", |(n, _)| n),
        }
    }
}
