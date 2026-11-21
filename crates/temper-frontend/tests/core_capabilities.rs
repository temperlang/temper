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
    /// Not meant to: a `@connected` type reached from no sealed
    /// supertype, which Kotlin Temper refuses ("Connected types cannot be
    /// targeted"), but Temper allows for now (notes/kotlin-differences.md,
    /// "A user's `@connected` type").
    ConnectedGap,
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
    ("DenseBitVector", New::Args("8"), Ext::Concrete, Cast::Yes, Key::No),
    ("Deque<Int>", New::Args(""), Ext::Concrete, Cast::TypeArgs, Key::No),
    ("DoneResult<Int>", New::Args(""), Ext::Concrete, Cast::ConnectedGap, Key::No),
    ("Empty", New::Private, Ext::Concrete, Cast::ConnectedGap, Key::No),
    ("Float64", New::Private, Ext::Concrete, Cast::No, Key::No),
    ("Generator<Int>", New::Abstract, Ext::Yes, Cast::ConnectedGap, Key::No),
    ("GeneratorFn", New::Abstract, Ext::Yes, Cast::Yes, Key::No),
    ("GeneratorResult<Int>", New::Abstract, Ext::Sealed, Cast::ConnectedGap, Key::No),
    ("Int", New::Private, Ext::Concrete, Cast::No, Key::Yes),
    ("Int32", New::Private, Ext::Concrete, Cast::No, Key::Yes),
    ("Int64", New::Private, Ext::Concrete, Cast::No, Key::No),
    ("List<Int>", New::Private, Ext::Concrete, Cast::No, Key::No),
    ("ListBuilder<Int>", New::Args(""), Ext::Concrete, Cast::No, Key::No),
    ("Listed<Int>", New::Abstract, Ext::Sealed, Cast::TypeArgs, Key::No),
    ("Map<String, Int>", New::Args("[]"), Ext::Concrete, Cast::No, Key::No),
    ("MapBuilder<String, Int>", New::Args(""), Ext::Concrete, Cast::No, Key::No),
    ("MapKey<String>", New::Abstract, Ext::Yes, Cast::TypeArgs, Key::No),
    ("Mapped<String, Int>", New::Abstract, Ext::Sealed, Cast::TypeArgs, Key::No),
    ("Never<Int>", New::Abstract, Ext::Sealed, Cast::Impossible, Key::No),
    ("NoStringIndex", New::Private, Ext::Concrete, Cast::ConnectedGap, Key::No),
    ("Pair<Int, Int>", New::Args("1, 2"), Ext::Concrete, Cast::Yes, Key::No),
    ("Promise<Int>", New::Private, Ext::Concrete, Cast::ConnectedGap, Key::No),
    ("PromiseBuilder<Int>", New::Args(""), Ext::Concrete, Cast::TypeArgs, Key::No),
    ("SafeGenerator<Int>", New::Abstract, Ext::Yes, Cast::Yes, Key::No),
    ("String", New::Private, Ext::Concrete, Cast::No, Key::Yes),
    ("StringBuilder", New::Args(""), Ext::Concrete, Cast::ConnectedGap, Key::No),
    ("StringIndex", New::Private, Ext::Concrete, Cast::ConnectedGap, Key::No),
    ("StringIndexOption", New::Abstract, Ext::Sealed, Cast::ConnectedGap, Key::No),
    ("Type", New::Private, Ext::Concrete, Cast::Yes, Key::No),
    ("ValueResult<Int>", New::Args("1"), Ext::Concrete, Cast::ConnectedGap, Key::No),
    ("Void", New::Private, Ext::Concrete, Cast::Unrelated, Key::No),
];

/// Core's exported types that user code can't name at all, as it can't
/// in Kotlin Temper either for some (notes/core-capabilities.md).
const UNNAMED: &[&str] = &[
    "Console",
    "Equatable",
    "Function",
    "Invalid",
    "Null",
    "Problem",
];

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
    (
        "let f(r: GeneratorResult<Int>): Boolean { r is DoneResult<Int> }",
        &[],
    ),
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

/// Every case.
fn cases() -> Vec<Case> {
    let mut out: Vec<Case> = Vec::new();
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
        out.push((format!("let v = new {ty}({args});"), wanted.clone(), None));
        // An alias is the type it names.
        let alias = format!("let A = {ty};\nlet v = new A({args});");
        out.push((alias, wanted, None));
        let wanted = match extend {
            Ext::Yes => vec![],
            Ext::Concrete => vec![format!("Cannot extend concrete type(s) {name}")],
            Ext::Sealed => vec![format!(
                "Cannot extend sealed type {name} from UserType. UserType is not declared in the \
                 same module."
            )],
        };
        let interface = format!("interface UserType extends {ty} {{}}");
        out.push((interface, wanted.clone(), None));
        let alias = format!("let A = {ty};\ninterface UserType extends A {{}}");
        out.push((alias, wanted.clone(), None));
        // A class must implement what an interface leaves abstract, which
        // isn't checked here.
        let class = format!("class UserType extends {ty} {{}}");
        out.push((class, wanted.clone(), Some("Cannot extend")));
        let alias = format!("let A = {ty};\nclass UserType extends A {{}}");
        out.push((alias, wanted, Some("Cannot extend")));
        let wanted = match cast {
            Cast::Yes | Cast::ConnectedGap => vec![],
            Cast::Unrelated => vec!["Unrelated types cannot be targeted".into()],
            Cast::No => vec!["Types marked @mayDowncastTo(false) cannot be targeted".into()],
            Cast::TypeArgs => vec!["Type arguments cannot be introduced".into()],
            Cast::Impossible => vec![format!(
                "Runtime type check from AnyValue to {} can never succeed",
                ty.replace("<Int>", "<Int32>")
            )],
        };
        let check = format!("let f(x: AnyValue): Boolean {{ x is {ty} }}");
        out.push((check, wanted.clone(), None));
        let cast = format!("let f(x: AnyValue): Void throws Bubble {{ x as {ty}; }}");
        out.push((cast, wanted, None));
        // Any type but `Void` may be a type argument, and only a `MapKey`
        // a map key.
        let void = vec![String::from("Void is only what a function returns")];
        let wanted = if ty == "Void" { void.clone() } else { vec![] };
        out.push((format!("let v: List<{ty}>? = null;"), wanted, None));
        let mut wanted = match key {
            Key::Yes => vec![],
            Key::No => vec!["Type formal <K extends MapKey<K>> cannot bind to".into()],
        };
        if ty == "Void" {
            wanted.splice(0..0, void);
        }
        out.push((
            format!("let v = new MapBuilder<{ty}, Int>();"),
            wanted,
            None,
        ));
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

/// A sealed interface is extended only in its own module, from any of
/// its files, as Kotlin Temper's `checkSealedExtension` allows
/// (docs/language/builtins.md, "`sealed` type modifier"), not from
/// another module. Core extends its own sealed interfaces, which
/// `settle.rs`'s `core_keeps_the_rules_it_sets` shows.
#[test]
fn sealed_interfaces_extended_only_in_their_module() {
    with_stack(|| {
        let mut library = Library::new();
        let a = "export sealed interface S {}\nexport class A extends S {}\n";
        library.add_file("app/lib/a.temper", a, SourceKind::Standalone);
        let b = "export class B extends S {}\n";
        library.add_file("app/lib/b.temper", b, SourceKind::Standalone);
        let main = "let { S } = import(\"./lib\");\nclass C extends S {}\n";
        library.add_file("app/main.temper", main, SourceKind::Standalone);
        let settled = settle(library);
        let found: Vec<String> = (settled.library.diagnostics.iter())
            .map(|d| d.message.text())
            .collect();
        let wanted = |sub: &str| {
            format!(
                "Cannot extend sealed type S from {sub}. {sub} is not declared in the same \
                 module."
            )
        };
        assert_eq!(found, [wanted("C")]);
    });
}
