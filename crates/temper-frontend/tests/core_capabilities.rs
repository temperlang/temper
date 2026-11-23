//! What other libraries may do with each type core exports, one case per
//! type and capability: make one with `new`, extend it, downcast to it
//! with `is` and `as`, use it as a type argument and a map key, and call
//! its static members, naming it or an alias of it. Allowed uses settle
//! with no diagnostics; others get a clear error.
//! notes/core-capabilities.md gives the reasons; core's declarations
//! (`lib/core/`) carry them: an interface or a class without a
//! public constructor can't be made, only unsealed interfaces can be
//! extended, and `@mayDowncastTo(false)` refuses `is`. The table covers
//! every builtin type, and names core's other types, as core declares
//! them, so a type core adds needs a row.

use std::collections::BTreeSet;
use temper_frontend::library::{Library, Message};
use temper_frontend::settle::settle;
use temper_syntax::ast::{Ast, Value, build};
use temper_syntax::lex::SourceKind;
use temper_syntax::parse::parse;
use temper_syntax::tree::{NodeId, NodeKind};

/// Whether `new` may make one.
#[derive(Clone, Copy)]
enum New {
    /// Yes, given these arguments.
    Args(&'static str),
    /// No: an interface.
    Abstract,
    /// No: a class whose constructor is private, or core's own.
    Private,
}

/// Whether a user's interface or class may extend it.
#[derive(Clone, Copy)]
enum Ext {
    Yes,
    /// No: a class.
    Concrete,
    /// No: a sealed interface, extended only in core.
    Sealed,
}

/// Whether `x is T` and `x as T`, for `x: AnyValue`, may check for it.
#[derive(Clone, Copy)]
enum Cast {
    Yes,
    /// No: core marks it `@mayDowncastTo(false)`.
    No,
    /// No: its type arguments can't be checked at run time.
    TypeArgs,
    /// No: it isn't an `AnyValue`.
    Unrelated,
    /// No: it has no values.
    Impossible,
    /// No: a `@connected` type, which only a sealed supertype may check
    /// for, as in Kotlin Temper, since several types may connect to one
    /// host type.
    Connected,
}

/// Whether it may be a map key, as `new MapBuilder<T, Int>()`.
#[derive(Clone, Copy)]
enum Key {
    /// Yes: a `MapKey`.
    Yes,
    /// No: "cannot bind to T which does not fit upper bounds".
    No,
}

/// Each type user code can name, as written with type arguments, and
/// what it may do with it (notes/core-capabilities.md).
#[rustfmt::skip]
const ROWS: &[(&str, New, Ext, Cast, Key)] = &[
    ("AnyValue", New::Abstract, Ext::Yes, Cast::Yes, Key::No),
    ("Boolean", New::Private, Ext::Concrete, Cast::No, Key::No),
    ("Bubble", New::Private, Ext::Concrete, Cast::Unrelated, Key::No),
    ("Console", New::Abstract, Ext::Yes, Cast::Connected, Key::No),
    ("DenseBitVector", New::Args("8"), Ext::Concrete, Cast::Connected, Key::No),
    ("Deque<Int>", New::Args(""), Ext::Concrete, Cast::TypeArgs, Key::No),
    ("DoneResult<Int>", New::Private, Ext::Concrete, Cast::Connected, Key::No),
    ("Empty", New::Private, Ext::Concrete, Cast::Connected, Key::No),
    ("Equatable<Int>", New::Abstract, Ext::Sealed, Cast::TypeArgs, Key::No),
    ("Float64", New::Private, Ext::Concrete, Cast::No, Key::No),
    ("Generator<Int>", New::Abstract, Ext::Yes, Cast::Connected, Key::No),
    ("GeneratorFn", New::Abstract, Ext::Yes, Cast::Yes, Key::No),
    ("GeneratorResult<Int>", New::Abstract, Ext::Sealed, Cast::Connected, Key::No),
    ("Int", New::Private, Ext::Concrete, Cast::No, Key::Yes),
    ("Int32", New::Private, Ext::Concrete, Cast::No, Key::Yes),
    ("Int64", New::Private, Ext::Concrete, Cast::No, Key::No),
    ("List<Int>", New::Private, Ext::Concrete, Cast::No, Key::No),
    ("ListBuilder<Int>", New::Args(""), Ext::Concrete, Cast::No, Key::No),
    ("Listed<Int>", New::Abstract, Ext::Sealed, Cast::TypeArgs, Key::No),
    ("Map<String, Int>", New::Args("[]"), Ext::Concrete, Cast::No, Key::No),
    ("MapBuilder<String, Int>", New::Args(""), Ext::Concrete, Cast::No, Key::No),
    ("MapKey<String>", New::Abstract, Ext::Sealed, Cast::TypeArgs, Key::No),
    ("Mapped<String, Int>", New::Abstract, Ext::Sealed, Cast::TypeArgs, Key::No),
    ("Never<Int>", New::Abstract, Ext::Sealed, Cast::Impossible, Key::No),
    ("NoStringIndex", New::Private, Ext::Concrete, Cast::Connected, Key::No),
    ("Null", New::Private, Ext::Concrete, Cast::Yes, Key::No),
    ("Pair<Int, Int>", New::Args("1, 2"), Ext::Concrete, Cast::Connected, Key::No),
    ("Promise<Int>", New::Private, Ext::Concrete, Cast::Connected, Key::No),
    ("PromiseBuilder<Int>", New::Args(""), Ext::Concrete, Cast::TypeArgs, Key::No),
    ("SafeGenerator<Int>", New::Abstract, Ext::Yes, Cast::Connected, Key::No),
    ("String", New::Private, Ext::Concrete, Cast::No, Key::Yes),
    ("StringBuilder", New::Args(""), Ext::Concrete, Cast::Connected, Key::No),
    ("StringIndex", New::Private, Ext::Concrete, Cast::Connected, Key::No),
    ("StringIndexOption", New::Abstract, Ext::Sealed, Cast::Connected, Key::No),
    ("Type", New::Private, Ext::Concrete, Cast::Yes, Key::No),
    ("ValueResult<Int>", New::Args("1"), Ext::Concrete, Cast::Connected, Key::No),
    ("Void", New::Private, Ext::Concrete, Cast::Unrelated, Key::No),
];

/// Core's exported types that user code can't name at all, as it can't
/// in Kotlin Temper either for some (notes/core-capabilities.md).
const UNNAMED: &[&str] = &["Function", "Invalid", "Problem"];

/// Static members user code may read or call, each with its use.
const STATICS: &[&str] = &[
    "let a: String = String.fromCodePoint(65) orelse \"\";",
    "let b: String = String.fromCodePoints([65, 66]) orelse \"\";",
    "let c: StringIndex = String.begin;",
    "let d: NoStringIndex = StringIndex.none;",
    "let e: Float64 = Float64.e;",
    "let f: Float64 = Float64.pi;",
    "let g: List<Int> = List.of(1, 2);",
];

/// Uses that aren't one type's capability, each with the errors it
/// should get, each the start of a diagnostic.
const OTHERS: &[(&str, &[&str])] = &[
    // Static members that aren't there, as for a user's class.
    ("let a = Int32.zzz;", &["No member zzz in Int32"]),
    ("let a = Int.zzz;", &["No member zzz in Int32"]),
    ("let a = List.zzz;", &["No member zzz in List"]),
    (
        "let a = Int32.constructor;",
        &["No member constructor in Int32"],
    ),
    (
        "let a = Empty.constructor;",
        &["No member constructor in Empty"],
    ),
    (
        "class C {}\nlet a = C.constructor;",
        &["No member constructor in C"],
    ),
    // Literals, and the values core documents but doesn't declare.
    (
        "let a: Int = 1;\nlet b: String = \"a\";\nlet c: Boolean = true;\n\
         let d: Boolean = false;\nlet e: List<Int> = [1];\nlet f: Float64 = 1.0;\n\
         let g: String? = null;\nlet h: Float64 = NaN;",
        &[],
    ),
    // `is` from a supertype: `List` may not be told from other `Listed`s.
    (
        "let f(x: Listed<Int>): Boolean { x is List<Int> }",
        &["Types marked @mayDowncastTo(false) cannot be targeted"],
    ),
    // A connected type from a sealed supertype, which tells its subtypes
    // apart, as in Kotlin Temper.
    (
        "let f(r: GeneratorResult<Int>): Boolean { r is DoneResult<Int> }",
        &[],
    ),
    (
        "let f(r: GeneratorResult<Int>): Boolean { r is ValueResult<Int> }",
        &[],
    ),
    (
        "let f(i: StringIndexOption): Boolean { i is StringIndex }",
        &[],
    ),
    (
        "let f(i: StringIndexOption): Void throws Bubble { i as NoStringIndex; }",
        &[],
    ),
    // `doneResult()` makes the `DoneResult` that `new` can't.
    ("let d: DoneResult<Int> = doneResult<Int>();", &[]),
    // A sealed `MapKey` is still a bound and a type.
    ("let f(k: MapKey<String>): MapKey<String> { k }", &[]),
    // A `Bubble` isn't an `AnyValue` (core's `AnyValue` docs).
    (
        "let f(x: Bubble): AnyValue { x }",
        &["Cannot assign to AnyValue from Bubble"],
    ),
    // Aliases are their types: of a generic type, given arguments.
    (
        "let LL = Listed;\ninterface UserType extends LL<Int> {}",
        &["Cannot extend sealed type Listed from UserType."],
    ),
    (
        "let LL = List;\nlet v = new LL<Int>();",
        &["List is not a concrete type with a public constructor"],
    ),
    // A local alias of a type parameter is the type parameter (t924).
    (
        "let f<T>(): Void { let U = T; let u = new U(); }",
        &["Cannot instantiate type parameter T"],
    ),
    (
        "let f<T>(x: AnyValue): Boolean { let U = T; x is U }",
        &["Type parameters cannot be targeted"],
    ),
    ("let f<T>(x: T): Void { let U = T; let y: U = x; }", &[]),
    (
        "let f<T>(): Void { let U = T; class C extends U {} }",
        &["Cannot extend type parameter T"],
    ),
    // A user's class's private constructor is its own, through an alias
    // too.
    (
        "class C {\n  private constructor() {}\n}\nlet c = new C();",
        &["C is not a concrete type with a public constructor"],
    ),
    (
        "class C {\n  private constructor() {}\n}\nlet f(): Void { let D = C; let c = new D(); }",
        &["C is not a concrete type with a public constructor"],
    ),
    // `Console`, `Equatable` and `Null`, which core exports, as Kotlin
    // Temper's did: a console may be passed around, implemented, and
    // got by name; the equatable builtin types are `Equatable`s, as a
    // bound; `Null` is null's type.
    (
        "let c: Console = console;\nc.log(\"a\");\nlet d: Console = getConsole(\"mine\");\n\
         let e: Console = getConsole();",
        &[],
    ),
    (
        "class Quiet extends Console { public log(message: String): Void {} }\n\
         let q: Console = new Quiet();",
        &[],
    ),
    (
        "let same<T extends Equatable<T>>(a: T, b: T): Boolean { a == b }\n\
         let a = same(1, 2);\nlet b = same(\"a\", \"b\");\nlet c: Equatable<Boolean> = true;\n\
         let d: Equatable<String> = \"s\";\nlet e: Equatable<Float64> = 1.0;",
        &[],
    ),
    // Only what core declares `Equatable` fits the bound: not a user's
    // class, a list, or a function.
    (
        "let same<T extends Equatable<T>>(a: T, b: T): Boolean { a == b }\n\
         class C {}\nlet a = same(new C(), new C());",
        &["Type formal <T extends Equatable<T>> cannot bind to C"],
    ),
    (
        "let same<T extends Equatable<T>>(a: T, b: T): Boolean { a == b }\n\
         let a = same([1], [1]);",
        &["Type formal <T extends Equatable<T>> cannot bind to List<Int32>"],
    ),
    (
        "let same<T extends Equatable<T>>(a: T, b: T): Boolean { a == b }\n\
         let f(): Int { 1 }\nlet a = same(f, f);",
        &["Type formal <T extends Equatable<T>> cannot bind to"],
    ),
    (
        "let k: Equatable<String> = 1;",
        &["Cannot assign to Equatable<String> from Int32"],
    ),
    ("let n: Null = null;\nlet m: Null? = null;", &[]),
    ("let n: Null = 1;", &["Cannot assign to Null from Int32"]),
    // A user's class named as a builtin type is the user's.
    (
        "class Int32 { public get big(): Boolean { true } }\nlet b = new Int32().big;",
        &[],
    ),
    (
        "class Int32 {}\nlet f(x: AnyValue): Boolean { x is Int32 }",
        &[],
    ),
    ("class Empty {}\nlet e = new Empty();", &[]),
    ("interface Empty {}\nclass C extends Empty {}", &[]),
];

/// Errors and warnings from settling `source` alone, as text.
fn diagnostics(source: &str) -> Vec<String> {
    let mut library = Library::new();
    library.add_file("test.temper", source, SourceKind::Standalone);
    let settled = settle(library);
    (settled.library.diagnostics.iter())
        .filter(|d| !matches!(d.message, Message::OutOfSteps(_)))
        .filter(|d| d.level != temper_frontend::library::Level::Hint)
        .map(|d| d.message.text())
        .collect()
}

/// The name of a type as written, without its type arguments.
fn base(ty: &str) -> &str {
    ty.split('<').next().unwrap_or(ty)
}

/// Core's name for a type, as `Int32` for `Int`.
fn declared(ty: &str) -> &str {
    match base(ty) {
        "Int" => "Int32",
        b => b,
    }
}

/// A case: its source, the errors it should get, each the start of a
/// diagnostic, and if only some diagnostics are compared, the start
/// they share.
type Case = (String, Vec<String>, Option<&'static str>);

/// A use of a type, `%` standing for it in `template`, written with the
/// type's name; imported by name from core; through a namespace of
/// core; and with an alias of it, top-level and local, as an alias or a
/// namespace's member is the type it names wherever types are checked
/// (notes/typing.md, "Type aliases").
fn uses(ty: &str, template: &str) -> [String; 5] {
    let named = template.replace('%', ty);
    let imported = format!("let {{ {} }} = import(\"core\");\n{named}", base(ty));
    let spaced = format!(
        "let core = import(\"core\");\n{}",
        template.replace('%', &format!("core.{ty}"))
    );
    let aliased = template.replace('%', "A");
    let local = format!("let g(): Void {{\n  let A = {ty};\n  {aliased}\n}}");
    [
        named,
        imported,
        spaced,
        format!("let A = {ty};\n{aliased}"),
        local,
    ]
}

/// Every case.
fn cases() -> Vec<Case> {
    let mut out: Vec<Case> = Vec::new();
    let mut push = |ty: &str, template: &str, wanted: &[String], only: Option<&'static str>| {
        for source in uses(ty, template) {
            out.push((source, wanted.to_vec(), only));
        }
    };
    for &(ty, new, extend, cast, key) in ROWS {
        let name = declared(ty);
        let (args, wanted) = match new {
            New::Args(args) => (args, vec![]),
            New::Abstract => ("", vec![format!("Cannot instantiate abstract type {name}")]),
            New::Private => (
                "",
                vec![format!(
                    "{name} is not a concrete type with a public constructor"
                )],
            ),
        };
        push(ty, &format!("let v = new %({args});"), &wanted, None);
        let wanted = match extend {
            Ext::Yes => vec![],
            Ext::Concrete => vec![format!("Cannot extend concrete type(s) {name}")],
            Ext::Sealed => vec![format!(
                "Cannot extend sealed type {name} from UserType. UserType is not declared in the \
                 same module."
            )],
        };
        push(ty, "interface UserType extends % {}", &wanted, None);
        // A class must implement what an interface leaves abstract, which
        // isn't checked here.
        push(
            ty,
            "class UserType extends % {}",
            &wanted,
            Some("Cannot extend"),
        );
        let wanted = match cast {
            Cast::Yes => vec![],
            Cast::Connected => vec!["Connected types cannot be targeted".into()],
            Cast::Unrelated => vec!["Unrelated types cannot be targeted".into()],
            Cast::No => vec!["Types marked @mayDowncastTo(false) cannot be targeted".into()],
            Cast::TypeArgs => vec!["Type arguments cannot be introduced".into()],
            Cast::Impossible => vec![format!(
                "Runtime type check from AnyValue to {} can never succeed",
                ty.replace("<Int>", "<Int32>")
            )],
        };
        push(ty, "let f(x: AnyValue): Boolean { x is % }", &wanted, None);
        let cast = "let f(x: AnyValue): Void throws Bubble { x as %; }";
        push(ty, cast, &wanted, None);
        // Any type but `Void` may be a type argument, and only a `MapKey`
        // a map key.
        let void = vec![String::from("Void is only what a function returns")];
        let wanted = if ty == "Void" { void.clone() } else { vec![] };
        push(ty, "let v: List<%>? = null;", &wanted, None);
        let mut wanted = match key {
            Key::Yes => vec![],
            Key::No => vec!["Type formal <K extends MapKey<K>> cannot bind to".into()],
        };
        if ty == "Void" {
            wanted.splice(0..0, void);
        }
        push(ty, "let v = new MapBuilder<%, Int>();", &wanted, None);
    }
    // A generic type's bounds hold however it's named, aliases too:
    // `let M = Map; M<Boolean, Int>` is no more a map than
    // `Map<Boolean, Int>` (t-review #2).
    let bound = "Type formal <K extends MapKey<K>> cannot bind to Boolean";
    for (ty, template, wanted) in [
        ("MapBuilder", "let v = new %<Boolean, Int>();", Some(bound)),
        ("MapBuilder", "let v = new %<String, Int>();", None),
        ("MapBuilder", "let v: %<Boolean, Int>? = null;", Some(bound)),
        ("Map", "let c(m: %<Boolean, Int>): Void {}", Some(bound)),
        ("Map", "let c(m: %<String, Int>): Void {}", None),
        (
            "Mapped",
            "let v: List<%<Boolean, Int>>? = null;",
            Some(bound),
        ),
    ] {
        let wanted: Vec<String> = wanted.into_iter().map(String::from).collect();
        push(ty, template, &wanted, None);
    }
    for name in UNNAMED {
        let wanted = vec![format!("{name} has not been declared")];
        out.push((format!("let v: {name}? = null;"), wanted, None));
    }
    for &source in STATICS {
        out.push((source.to_string(), vec![], None));
    }
    for &(source, wanted) in OTHERS {
        let wanted = wanted.iter().map(|w| w.to_string()).collect();
        out.push((source.to_string(), wanted, None));
    }
    out
}

/// Runs `f` with a stack big enough for settling.
fn with_stack(f: impl FnOnce() + Send + 'static) {
    std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(f)
        .unwrap()
        .join()
        .unwrap();
}

/// Each case gets the errors it should, and no others.
#[test]
fn core_types_enable_only_what_they_mean_to() {
    with_stack(|| {
        let mut wrong = Vec::new();
        for (source, wanted, only) in cases() {
            let found: Vec<String> = (diagnostics(&source).into_iter())
                .filter(|f| only.is_none_or(|o| f.starts_with(o)))
                .collect();
            let fits = found.len() == wanted.len()
                && (found.iter().zip(&wanted)).all(|(f, w)| f.starts_with(w.as_str()));
            if !fits {
                wrong.push(format!("{source}\n  wanted {wanted:?}\n  found  {found:?}"));
            }
        }
        assert!(
            wrong.is_empty(),
            "{} cases:\n{}",
            wrong.len(),
            wrong.join("\n")
        );
    });
}

/// A class or interface core exports, as parsed from its text.
struct CoreExport {
    name: String,
    is_class: bool,
    /// Whether it declares a constructor, public or private.
    has_constructor: bool,
}

/// Each class and interface core exports, parsed from its text.
fn core_exports() -> Vec<CoreExport> {
    let ast = build(&parse(
        temper_frontend::corelib::code(),
        SourceKind::Standalone,
    ));
    let mut out = Vec::new();
    for &statement in ast.tree().children(ast.root()) {
        let (decorators, declaration) = undecorated(&ast, statement);
        if !decorators.contains(&"export") {
            continue;
        }
        let Some((callee, args)) = call(&ast, declaration) else {
            continue;
        };
        if !matches!(callee, Some("class" | "interface")) {
            continue;
        }
        // `\word Name`.
        let name = (args.windows(2))
            .find(|pair| symbol(&ast, pair[0]) == Some("word"))
            .and_then(|pair| text(&ast, pair[1]));
        // Its body, the block of the function that ends it.
        let body = (args.last())
            .filter(|&&f| ast.tree().kind(f) == NodeKind::Fun)
            .and_then(|&f| ast.tree().children(f).last().copied());
        let has_constructor = body.is_some_and(|body| {
            (ast.tree().children(body).iter()).any(|&m| {
                let member = undecorated(&ast, m).1;
                call(&ast, member).is_some_and(|(callee, _)| callee == Some("constructor"))
            })
        });
        out.push(CoreExport {
            name: name.unwrap_or_default().to_string(),
            is_class: callee == Some("class"),
            has_constructor,
        });
    }
    out
}

/// A declaration without its decorations, as `@export`, and their names.
fn undecorated(ast: &Ast, node: NodeId) -> (Vec<&str>, NodeId) {
    let mut decorators = Vec::new();
    let mut at = node;
    while let Some((Some("@"), &[decorator, target])) = call(ast, at) {
        let name = match call(ast, decorator) {
            Some((callee, _)) => callee,
            None => text(ast, decorator),
        };
        decorators.extend(name);
        at = target;
    }
    (decorators, at)
}

/// A call's callee's name, if it's a name, and its arguments.
fn call(ast: &Ast, node: NodeId) -> Option<(Option<&str>, &[NodeId])> {
    let tree = ast.tree();
    let (&callee, args) = (tree.kind(node) == NodeKind::Call)
        .then(|| tree.children(node).split_first())
        .flatten()?;
    Some((text(ast, callee), args))
}

fn text(ast: &Ast, node: NodeId) -> Option<&str> {
    Some(ast.names().get(ast.name(node)?))
}

fn symbol(ast: &Ast, node: NodeId) -> Option<&str> {
    match ast.value(node)? {
        Value::Symbol(n) => Some(ast.names().get(*n)),
        _ => None,
    }
}

/// The table has a row for each builtin type, and names the rest of
/// what core exports, so a type core adds can't go unchecked.
#[test]
fn rows_cover_what_core_declares() {
    let rows: BTreeSet<&str> = ROWS.iter().map(|&(ty, ..)| base(ty)).collect();
    let builtins: BTreeSet<&str> = temper_frontend::builtins::TYPES.iter().copied().collect();
    assert_eq!(rows, builtins);
    let exports = core_exports();
    assert!(exports.len() > 30, "{} exports", exports.len());
    let others: BTreeSet<&str> = (exports.iter())
        .map(|e| e.name.as_str())
        .filter(|n| !builtins.contains(n))
        .collect();
    assert_eq!(others, UNNAMED.iter().copied().collect());
}

/// Each class core exports declares its constructor, public or private,
/// so that whether `new` may make one is written down, not implied.
#[test]
fn core_classes_declare_their_constructors() {
    let missing: Vec<String> = (core_exports().into_iter())
        .filter(|e| e.is_class && !e.has_constructor)
        .map(|e| e.name)
        .collect();
    assert_eq!(missing, Vec::<String>::new());
}

/// A library's types, for [`module_types_keep_their_rules`]: a sealed
/// interface, extended from its module's other file too, an open one, a
/// class with a private constructor, and a generic class with a bound.
const LIB_A: &str = "export sealed interface S {}\nexport class A extends S {}\n\
export interface Open {}\n\
export class Token {\n  public text: String;\n  private constructor(text: String) { this.text = text; }\n  \
public static make(text: String): Token { new Token(text) }\n  \
public static empty: Token = new Token(\"\");\n}\n\
export class Box<K extends MapKey<K>> {}\n";
const LIB_B: &str = "export class B extends S {}\n";

/// The library's names, `@`-prefixed in a template.
const LIB_NAMES: &[&str] = &["S", "A", "Open", "Token", "Box"];

/// Uses of the library's types from another module, `@T` standing for
/// type `T`, each with the errors it should get.
const MODULE_USES: &[(&str, &[&str])] = &[
    (
        "class D extends @S {}\ninterface J extends @S {}",
        &[
            "Cannot extend sealed type S from D. D is not declared in the same module.",
            "Cannot extend sealed type S from J. J is not declared in the same module.",
        ],
    ),
    (
        "class D extends @Open {}\ninterface J extends @Open {}",
        &[],
    ),
    (
        "let t = new @Token(\"x\");",
        &["Token is not a concrete type with a public constructor"],
    ),
    (
        "let t: @Token = @Token.make(\"x\");\nlet e: @Token = @Token.empty;",
        &[],
    ),
    (
        "let t: @Token = { text: \"lit\" };",
        &["Token is not a concrete type"],
    ),
    (
        "let v: @Box<Boolean>? = null;",
        &["Type formal <K extends MapKey<K>> cannot bind to Boolean"],
    ),
    (
        "let v = new @Box<Boolean>();",
        &["Type formal <K extends MapKey<K>> cannot bind to Boolean"],
    ),
    (
        "let v: @Box<String>? = null;\nlet w = new @Box<String>();",
        &[],
    ),
    ("let f(x: AnyValue): Boolean { x is @A }", &[]),
];

/// Errors from settling `main` beside the library [`LIB_A`] and
/// [`LIB_B`] make.
fn module_errors(main: &str) -> Vec<String> {
    let mut library = Library::new();
    library.add_file("app/lib/a.temper", LIB_A, SourceKind::Standalone);
    library.add_file("app/lib/b.temper", LIB_B, SourceKind::Standalone);
    library.add_file("app/main.temper", main, SourceKind::Standalone);
    let settled = settle(library);
    (settled.library.diagnostics.iter())
        .filter(|d| d.level == temper_frontend::library::Level::Error)
        .map(|d| d.message.text())
        .collect()
}

/// A library's rules for its types hold however another module names
/// them: imported by name, through a namespace, or by an alias of
/// either, top-level or local. A namespace's member and an alias are the
/// type they name (notes/typing.md, "Type aliases"). Each rule has its
/// legal neighbor beside it. A sealed interface is extended only in its
/// own module, from any of its files, as Kotlin Temper's
/// `checkSealedExtension` allows (docs/language/builtins.md, "`sealed`
/// type modifier"). Core extends its own sealed interfaces, which
/// `settle.rs`'s `core_keeps_the_rules_it_sets` shows.
#[test]
fn module_types_keep_their_rules() {
    with_stack(|| {
        let names = LIB_NAMES.join(", ");
        let aliases = |from: &str| -> String {
            (LIB_NAMES.iter())
                .map(|n| format!("let {n}2 = {from}{n};"))
                .collect::<Vec<_>>()
                .join("\n")
                + "\n"
        };
        let mut wrong = Vec::new();
        for &(template, wanted) in MODULE_USES {
            let by = |prefix: &str, suffix: &str| {
                (LIB_NAMES.iter().rev()).fold(template.to_string(), |t, n| {
                    t.replace(&format!("@{n}"), &format!("{prefix}{n}{suffix}"))
                })
            };
            let imported = format!("let {{ {names} }} = import(\"./lib\");\n");
            let spaced = "let lib = import(\"./lib\");\n";
            let local = by("", "2").replace('\n', "\n  ");
            let mains = [
                format!("{imported}{}", by("", "")),
                format!("{spaced}{}", by("lib.", "")),
                format!("{spaced}{}{}", aliases("lib."), by("", "2")),
                format!(
                    "{imported}let g(): Void {{\n  {}  {local}\n}}",
                    aliases("").replace('\n', "\n  ")
                ),
            ];
            for main in mains {
                let found = module_errors(&main);
                let fits = found.len() == wanted.len()
                    && (found.iter().zip(wanted)).all(|(f, w)| f.starts_with(w));
                if !fits {
                    wrong.push(format!("{main}\n  wanted {wanted:?}\n  found  {found:?}"));
                }
            }
        }
        assert!(
            wrong.is_empty(),
            "{} cases:\n{}",
            wrong.len(),
            wrong.join("\n")
        );
    });
}

/// Each builtin type's supertypes among the builtin types, as core's
/// `extends` clauses give them: `("List", "Listed")` for `export class
/// List<@out T> extends Listed<T>`.
fn core_extends() -> BTreeSet<(String, String)> {
    let builtins: BTreeSet<&str> = temper_frontend::builtins::TYPES.iter().copied().collect();
    let mut found = BTreeSet::new();
    for line in temper_frontend::corelib::code().lines() {
        let line = line.trim();
        let Some(rest) = line.strip_prefix("export ") else {
            continue;
        };
        let rest = rest.strip_prefix("sealed ").unwrap_or(rest);
        let Some(rest) = (rest.strip_prefix("class ")).or_else(|| rest.strip_prefix("interface "))
        else {
            continue;
        };
        let name_end = rest
            .find(|c: char| !c.is_ascii_alphanumeric())
            .unwrap_or(rest.len());
        let (name, mut rest) = rest.split_at(name_end);
        // Past its type parameters, whose bounds say `extends` too.
        if rest.starts_with('<') {
            let mut depth = 0;
            let end = rest.char_indices().find_map(|(i, c)| {
                depth += match c {
                    '<' => 1,
                    '>' => -1,
                    _ => 0,
                };
                (depth == 0).then_some(i + 1)
            });
            rest = &rest[end.unwrap_or(rest.len())..];
        }
        let Some(supers) = rest.trim_start().strip_prefix("extends ") else {
            continue;
        };
        let supers = supers.split('{').next().unwrap_or("");
        let mut depth = 0;
        for piece in supers.split(|c: char| {
            depth += match c {
                '<' => 1,
                '>' => -1,
                _ => 0,
            };
            c == ',' && depth == 0
        }) {
            let sup = piece.trim().split('<').next().unwrap_or("").trim();
            if builtins.contains(name) && builtins.contains(sup) {
                found.insert((name.to_string(), sup.to_string()));
            }
        }
    }
    found
}

/// The frontend's table of builtin types' supertypes
/// (`types::BUILTIN_SUPERTYPES`), which typing reads for subtyping, is
/// what core's declarations say, so that one can't change without the
/// other.
#[test]
fn builtin_supertypes_are_cores() {
    let table: BTreeSet<(String, String)> = (temper_frontend::types::BUILTIN_SUPERTYPES.iter())
        .map(|&(sub, sup)| (sub.to_string(), sup.to_string()))
        .collect();
    let core = core_extends();
    assert!(core.len() > 10, "{core:?}");
    assert_eq!(table, core);
}
