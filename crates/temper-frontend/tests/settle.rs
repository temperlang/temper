//! Settle loop tests over the cases in `tests/settle/*.cases`.
//!
//! The cases have the format described in temper-syntax's
//! `tests/common/mod.rs`, with a `--- settled` line before the expected
//! output: the settled tree and diagnostics as `print::write_settled`
//! writes them, then what running it logs, as `log` lines, and how it
//! failed, if it did.
//!
//! - `cases_settle_as_expected` checks each case. To accept new or
//!   changed output, run with `UPDATE_SNAPSHOTS=1` and review the diff.
//! - `cases_with_std_settle_as_expected` checks the cases in
//!   `tests/settle-std/`, with std loaded, by their diagnostics and runs
//!   only.
//! - `mutated_inputs_settle` settles randomly edited copies of these cases
//!   and of temper-syntax's grammar cases, and checks that settling never
//!   panics (nor fails the debug check that no step read a node rewritten
//!   later), finishes within its step budget, gives the same result twice,
//!   and that running the result never panics. Set `SETTLE_FUZZ_SEED` and
//!   `SETTLE_FUZZ_ROUNDS` (edits per case) to vary it, and
//!   `SETTLE_FUZZ_DIR` to add the Temper files under a directory, such as
//!   Kotlin Temper's functional tests.

#[path = "../../temper-syntax/tests/common/mod.rs"]
mod common;

use common::{Rng, case_files, check_cases, env_number, mutate};
use temper_frontend::interp::{run, run_tests};
use temper_frontend::library::{Level, Library, Message};
use temper_frontend::print::write_settled;
use temper_frontend::project::load_imports;
use temper_frontend::settle::settle;
use temper_frontend::sources::{Source, Sources};
use temper_frontend::typing::{Resolved, coverage};
use temper_syntax::lex::SourceKind;
use temper_syntax::tree::{NodeId, NodeKind};

/// The settled tree, diagnostics and run of a source.
fn settled(input: &str, kind: SourceKind, fuel: u64) -> (String, bool) {
    let mut library = Library::new();
    library.add_file("test.temper", input, kind);
    let settled = settle(library);
    let out_of_steps = settled
        .library
        .diagnostics
        .iter()
        .any(|d| matches!(d.message, Message::OutOfSteps(_)));
    let mut text = String::new();
    write_settled(&settled, 80, &mut text);
    let run = run(&settled, Some(fuel));
    for line in run.output {
        text.push_str(&format!("log {line}\n"));
    }
    if let Some(failure) = run.failure {
        text.push_str(&format!("{failure}\n"));
    }
    (text, out_of_steps)
}

/// Runs a test on a thread with room for the interpreter's recursion.
fn with_stack(test: impl FnOnce() + Send + 'static) {
    std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(test)
        .unwrap()
        .join()
        .unwrap();
}

#[test]
fn cases_settle_as_expected() {
    with_stack(|| {
        check_cases("tests/settle", "settled", |case| {
            settled(&case.input, case.kind, 1_000_000).0
        })
    });
}

/// The diagnostics and run of a source with std loaded, as `@json` needs
/// `std/json`: not the settled trees, which would show all of std's,
/// numbered as std's declarations are.
fn settled_with_std(input: &str, kind: SourceKind) -> String {
    let mut library = Library::new();
    library.add_file("test.temper", input, kind);
    load_imports(&Sources::standard(), &mut library);
    let settled = settle(library);
    let mut text = String::new();
    temper_frontend::print::write_diagnostics(&settled, &mut text);
    let run = run(&settled, Some(1_000_000));
    for line in run.output {
        text.push_str(&format!("log {line}\n"));
    }
    if let Some(failure) = run.failure {
        text.push_str(&format!("{failure}\n"));
    }
    text
}

/// The cases in `tests/settle-std/*.cases`, as [`settled_with_std`]
/// gives them.
#[test]
fn cases_with_std_settle_as_expected() {
    with_stack(|| {
        check_cases("tests/settle-std", "settled", |case| {
            settled_with_std(&case.input, case.kind)
        })
    });
}

/// Imports between modules, which the cases' single files can't show.
#[test]
fn imports_across_modules() {
    with_stack(|| {
        let mut library = Library::new();
        let main = "let { twice, C as D } = import(\"./lib/\");\nlet n = 1;\n\
                    console.log(\"${twice(n)} ${new D(3).n}\");\nlet { hidden } = import(\"./lib\");\nlet { z } = import(\"./nope\");";
        library.add_file("app/main.temper", main, SourceKind::Standalone);
        assert_eq!(library.missing_imports(), ["app/lib", "app/nope"]);
        // `n` here is its own; the importer's is another.
        let lib = "export let twice(x: Int): Int { x * n }\nlet n = 2;\nlet hidden = 0;\n\
                   export class C(public n: Int) {}\nconsole.log(\"lib\");";
        library.add_file("app/lib/lib.temper", lib, SourceKind::Standalone);
        assert!(library.missing_imports() == ["app/nope"]);
        let settled = settle(library);
        let messages: Vec<String> = settled
            .library
            .diagnostics
            .iter()
            .map(|d| d.message.text())
            .collect();
        assert_eq!(
            messages,
            [
                "./lib does not export symbol hidden",
                "Import of ./nope failed"
            ]
        );
        let run = run(&settled, Some(1_000_000));
        assert_eq!(
            (run.output, run.failure.as_deref()),
            (
                vec!["lib".to_string(), "2 3".into()],
                Some("Failed: ./lib does not export symbol hidden")
            )
        );
    });
}

/// An `@extension("isPal")` imported from another module, under its own
/// name or another, answers to `s.isPal()` there, which becomes a call of
/// the import; a module that doesn't import it gets "No member" (the
/// audit's probe: once, any file's text mentioning `@extension` turned
/// the check off for the whole library).
#[test]
fn imported_extensions_called_where_imported() {
    with_stack(|| {
        let mut library = Library::new();
        let lib =
            "@extension(\"isPal\")\nexport let stringIsPal(s: String): Boolean { s == \"aba\" }";
        library.add_file("app/lib/lib.temper", lib, SourceKind::Standalone);
        let main = "let { stringIsPal } = import(\"./lib/\");\n\
                    let f(s: String): Boolean { s.isPal() }\n\
                    console.log(f(\"aba\").toString());";
        library.add_file("app/main.temper", main, SourceKind::Standalone);
        let renamed = "let { stringIsPal as pal } = import(\"../lib/\");\n\
                       export let h(s: String): Boolean { s.isPal() }";
        library.add_file(
            "app/renamed/renamed.temper",
            renamed,
            SourceKind::Standalone,
        );
        let other = "let g(s: String): Boolean { s.isPal() }";
        library.add_file("app/other/other.temper", other, SourceKind::Standalone);
        let settled = settle(library);
        let errors: Vec<(String, String)> = (settled.library.diagnostics.iter())
            .filter(|d| d.level == Level::Error)
            .map(|d| {
                let file = settled.library.locate(d.span.start).0;
                let path = &settled.library.files[file.0 as usize].path;
                (path.clone(), d.message.text())
            })
            .collect();
        assert_eq!(
            errors,
            [(
                "app/other/other.temper".to_string(),
                "No member isPal in String".to_string()
            )]
        );
        let mut text = String::new();
        write_settled(&settled, 80, &mut text);
        assert!(text.contains("(Call stringIsPal:"), "{text}");
        assert!(text.contains("(Call pal:"), "{text}");
        let run = run(&settled, Some(1_000_000));
        assert!(run.output.contains(&"true".to_string()), "{:?}", run.output);
    });
}

/// `std/…` imports, under `-std-`, and `...`, which imports every export
/// not named otherwise.
#[test]
fn std_imports_and_spread() {
    with_stack(|| {
        let mut library = Library::new();
        let main = "let { ... } = import(\"std/m\");\nlet { a as b, ... } = import(\"./n\");\n\
                    console.log(\"${a} ${c2} ${b} ${d}\");";
        library.add_file("app/main.temper", main, SourceKind::Standalone);
        assert_eq!(library.missing_imports(), ["-std-/m", "app/n"]);
        // Std modules import each other relatively.
        let std = [
            (
                "m/m.temper",
                "export let a = 1;\nlet { c } = import(\"../o\");\nexport let c2 = c;",
            ),
            ("o/o.temper", "export let c = 3;"),
        ];
        let app = [(
            "app/n/n.temper",
            "export let a = 2;\nexport let d = 4;\nlet hidden = 5;",
        )];
        let sources = Sources::new()
            .mount("-std-", Source::memory(&std))
            .mount("", Source::memory(&app));
        load_imports(&sources, &mut library);
        assert!(library.missing_imports().is_empty());
        let settled = settle(library);
        assert!(settled.library.diagnostics.is_empty());
        // `a` is std/m's, as n's is named and renamed.
        let run = run(&settled, Some(1_000_000));
        assert_eq!(
            (run.output, run.failure),
            (vec!["1 3 2 4".to_string()], None)
        );
        // Std built in has no `m` module.
        let mut library = Library::new();
        library.add_file(
            "main.temper",
            "let { a } = import(\"std/m\");",
            SourceKind::Standalone,
        );
        assert_eq!(library.missing_imports(), ["-std-/m"]);
        load_imports(&Sources::memory(&[]), &mut library);
        let messages: Vec<String> = settle(library)
            .library
            .diagnostics
            .iter()
            .map(|d| d.message.text())
            .collect();
        assert_eq!(messages, ["Import of std/m failed"]);
    });
}

/// `test` and `assert`, over a small stand-in for std's `testing`, and
/// running the tests after the program.
#[test]
fn tests_and_asserts() {
    with_stack(|| {
        let mut library = Library::new();
        let main = "test(\"one ${n}\") { assert(n == 2); assert(n > 2) }\nlet n = 1;\n\
                    test(\"two\") { t => helper(t); assert(false) { \"no\" } }\n\
                    let helper(test: Test): Void { assert(n != 1) }\nlet f(x: Int): Void { assert(true) }\nconsole.log(\"ran\");";
        library.add_file("main.temper", main, SourceKind::Standalone);
        assert_eq!(library.missing_imports(), ["-std-/testing"]);
        let testing = "export class Test {\n\
                         public assert(ok: Boolean, message: fn (): String): Void { if (!ok) { messages.add(message()) } }\n\
                         public messages: ListBuilder<String> = new ListBuilder<String>();\n}\n\
                       export let runTestCases(cases: List<Pair<String, fn (Test): Void>>): String {\n\
                         cases.map { (c): String => let t = new Test(); let f = c.value; f(t); \"${c.key}: ${t.messages.join(\", \") { it => it }}\" }\n\
                           .join(\"\\n\") { it => it }\n}";
        library.add_file(
            "-std-/testing/testing.temper",
            testing,
            SourceKind::Standalone,
        );
        let settled = settle(library);
        let messages: Vec<String> = settled
            .library
            .diagnostics
            .iter()
            .map(|d| d.message.text())
            .collect();
        assert_eq!(messages, ["Invalid block content"]);
        let run = run(&settled, Some(1_000_000));
        assert_eq!((run.output, run.failure), (vec!["ran".to_string()], None));
        let run = run_tests(&settled, Some(1_000_000));
        let expected = [
            "ran",
            "one 1: expected n == (2) not (1), expected n > 2",
            "two: expected n != 1, no",
        ];
        assert_eq!(
            (run.output, run.failure),
            (expected.map(String::from).to_vec(), None)
        );
    });
}

/// Regex literals, which build std's regex objects, over a small stand-in
/// for std's `regex` whose objects describe themselves.
#[test]
fn regex_literals() {
    with_stack(|| {
        let mut library = Library::new();
        let main = "let x = \"-\";\nconsole.log(rgx\"a${x}b+^(?n=c)\");\nlet bad = /a{2,1}/;";
        library.add_file("main.temper", main, SourceKind::Standalone);
        assert_eq!(library.missing_imports(), ["-std-/regex"]);
        let regex = "export interface RegexNode { public compiled(): String { this.toString() } }\n\
                     export class CodePoints(public value: String) extends RegexNode { public toString(): String { \"'${value}'\" } }\n\
                     export class Sequence(public items: List<RegexNode>) extends RegexNode {\n\
                       public toString(): String { \"seq(${items.join(\", \") { it => it.toString() }})\" }\n}\n\
                     export class Repeat(public item: RegexNode, public min: Int, public max: Int?, public reluctant: Boolean) extends RegexNode {\n\
                       public toString(): String { \"rep(${item}, ${min}, ${max ?? -1}, ${reluctant})\" }\n}\n\
                     export class Capture(public name: String, public item: RegexNode) extends RegexNode {\n\
                       public toString(): String { \"cap(${name}, ${item})\" }\n}\n\
                     class B extends RegexNode { public toString(): String { \"^\" } }\nexport let Begin: RegexNode = new B();";
        library.add_file("-std-/regex/regex.temper", regex, SourceKind::Standalone);
        let settled = settle(library);
        let messages: Vec<String> = settled
            .library
            .diagnostics
            .iter()
            .map(|d| d.message.text())
            .collect();
        assert_eq!(
            messages,
            ["Invalid regex: at position 6: quantifier has min greater than max"]
        );
        let run = run(&settled, Some(1_000_000));
        let expected = "seq('a-', rep('b', 1, -1, false), ^, cap(n, 'c'))";
        let failure = Some(
            "Failed: Invalid regex: at position 6: quantifier has min greater than max".to_string(),
        );
        assert_eq!(
            (run.output, run.failure),
            (vec![expected.to_string()], failure)
        );
    });
}

/// A function literal, and a reference to a declared function or method,
/// takes the `@fun` interface its place wants as its type, where it fits,
/// as tmpl will need; a value of a bare function type, or with no
/// interface wanted, keeps its function type (notes/typing.md, "Fun
/// interfaces").
#[test]
fn fun_values_take_their_interface() {
    with_stack(|| {
        let mut library = Library::new();
        let main = "@fun interface Pred(x: Int): Boolean;\n\
                    let isPos(x: Int): Boolean { x > 0 }\n\
                    let keep(p: Pred): Boolean { p(1) }\n\
                    class K {\n  public big(x: Int): Boolean { x > 9 }\n  public pick(): Pred { big }\n}\n\
                    let p: Pred = isPos;\n\
                    let q: Pred? = fn (x) { x > 1 };\n\
                    let r = keep(isPos) && keep { x => x > 2 };\n\
                    let s: Pred = new K().big;\n\
                    let f: fn (Int): Boolean = isPos;\n\
                    let g: Pred = f;\n\
                    let h: fn (String): Boolean = fn (x) { x == \"\" };";
        library.add_file("main.temper", main, SourceKind::Standalone);
        let settled = settle(library);
        let messages: Vec<String> = (settled.library.diagnostics.iter())
            .map(|d| d.message.text())
            .collect();
        assert_eq!(messages, Vec::<String>::new());
        let (lib, typing) = (&settled.library, &settled.typing);
        let class_name = |b| lib.names.get(settled.scopes.symbol(b).name).to_string();
        let mut found = Vec::new();
        for i in 0..lib.tree.len() as u32 {
            let node = NodeId(i);
            let kind = lib.tree.kind(node);
            let is_fn_value = kind == NodeKind::Fn
                || (kind == NodeKind::RightName
                    && matches!(lib.source_text(lib.tree.span(node)), "isPos" | "big" | "f"));
            if let Some(t) = typing.expr(node).filter(|_| is_fn_value) {
                let text = lib.source_text(lib.tree.span(node));
                let text = text.split_whitespace().collect::<Vec<_>>().join(" ");
                found.push(format!("{text}: {}", typing.types.display(t, &class_name)));
            }
        }
        found.sort();
        assert_eq!(
            found,
            [
                // Members' functions are typed too, for tmpl.
                "@fun interface Pred(x: Int): Boolean: fn (Int32): Boolean",
                "big(x: Int): Boolean { x > 9 }: fn (Int32): Boolean",
                "big: Pred",
                "f: fn (Int32): Boolean",
                "fn (x) { x == \"\" }: fn (String): Boolean",
                "fn (x) { x > 1 }: Pred",
                "isPos: Pred",
                "isPos: Pred",
                "isPos: fn (Int32): Boolean",
                "let isPos(x: Int): Boolean { x > 0 }: fn (Int32): Boolean",
                "let keep(p: Pred): Boolean { p(1) }: fn (Pred): Boolean",
                "pick(): Pred { big }: fn: Pred",
                "{ x => x > 2 }: Pred",
            ]
        );
    });
}

/// Typing records what each call, operator, and member read resolves to,
/// for tmpl's translation (notes/typing.md, "Resolutions"): a
/// declaration, the `eq` that `@auto("==")` makes, a builtin by its key, a
/// null check, or a call of a function value; and notes an `==` with
/// nothing to call but identity.
#[test]
fn calls_resolve_to_what_they_call() {
    with_stack(|| {
        let mut library = Library::new();
        let main = "class Loose(public n: Int) {\n\
                    \x20 @operator(\"==\") public eq(other: Loose): Boolean { n == other.n }\n\
                    \x20 @operator(\"<=>\") public compareTo(other: Loose): Int { n <=> other.n }\n\
                    \x20 public twice(): Int { n * 2 }\n\
                    }\n\
                    interface Named {\n\
                    \x20 public name: String;\n\
                    \x20 @operator(\"==\") public same(other: Named): Boolean { name == other.name }\n\
                    }\n\
                    class Dog(public name: String) extends Named {}\n\
                    @auto(\"==\") class P(public x: Int) {}\n\
                    let same<T>(a: T, b: T): Boolean { a == b }\n\
                    let apply(f: fn (Int): Int): Int { f(1) }\n\
                    let a = new Loose(1);\n\
                    let s: String? = \"s\";\n\
                    let ls = [1, 2];\n\
                    console.log(\"${a == a} ${a < a} ${new Dog(\"r\") == new Dog(\"r\")} ${new P(1) != new P(2)}\");\n\
                    console.log(\"${s == null} ${s == \"t\"} ${1.5 < 2.0} ${-a.twice()} ${a.n}\");\n\
                    console.log(\"${ls[0]} ${ls.length} ${\"x\".toInt32() orelse 0} ${apply { x => x }}\");";
        library.add_file("main.temper", main, SourceKind::Standalone);
        let settled = settle(library);
        let messages: Vec<String> = (settled.library.diagnostics.iter())
            .map(|d| d.message.text())
            .collect();
        assert_eq!(messages, Vec::<String>::new());
        let (lib, typing) = (&settled.library, &settled.typing);
        let decl_name = |d: NodeId| {
            let name = lib.tree.children(d)[0];
            lib.source_text(lib.tree.span(name)).to_string()
        };
        let mut found = Vec::new();
        for i in 0..lib.tree.len() as u32 {
            let node = NodeId(i);
            let text = lib.source_text(lib.tree.span(node));
            let what = match (typing.resolved(node), typing.identity_eq(node)) {
                (Some(Resolved::Decl(d)), _) => format!("decl {}", decl_name(*d)),
                (Some(Resolved::Builtin(key)), _) => key.clone(),
                (Some(Resolved::NullCheck), _) => "null check".into(),
                (Some(Resolved::Value), _) => "value".into(),
                (None, Some(why)) => format!("identity: {why}"),
                (None, None) => continue,
            };
            let text = match text.find('(') {
                _ if what == "String.cat" => "a template",
                Some(i) if text.starts_with("console.log") => &text[..i],
                _ => text,
            };
            found.push(format!("{text}: {what}"));
        }
        found.sort();
        found.dedup();
        assert_eq!(
            found,
            [
                "\"x\".toInt32(): String.toInt32",
                "-a.twice(): Int32.neg",
                "1.5 < 2.0: Float64.lt",
                "[1, 2]: list",
                "a < a: decl compareTo",
                "a == a: decl eq",
                "a == b: identity: type parameter",
                "a template: String.cat",
                "a.n: decl n",
                "a.twice(): decl twice",
                "apply { x => x }: decl apply",
                "auto(\"==\"): Int32.eq",
                "auto(\"==\"): decl x",
                "console.log: console.log",
                "f(1): value",
                "ls.length: List.length",
                "ls[0]: List.get",
                "n * 2: Int32.mul",
                "n <=> other.n: Int32.cmp",
                "n == other.n: Int32.eq",
                "name == other.name: String.eq",
                "new Dog(\"r\") == new Dog(\"r\"): decl same",
                "new Dog(\"r\"): decl Dog",
                "new Loose(1): decl Loose",
                "new P(1) != new P(2): decl auto(\"==\")",
                "new P(1): decl P",
                "new P(2): decl P",
                "other.n: decl n",
                "other.name: decl name",
                "s == \"t\": String.eq",
                "s == null: null check",
            ]
        );
    });
}

/// Every expression gets a type, and every call a resolution, in code
/// like the functional tests' (notes/typing.md, "Every expression
/// typed"): `console.log`, tags, builtin types' constructors, solved from
/// arguments, type arguments, or the type wanted, through an alias too,
/// fields typed by their initializers, `for … of` variables, a type
/// parameter's members, `i++`, `doPure`, a block that returns, `empty()`,
/// string indices, a generic function, and tests, one asserting a
/// nullable list is null with its own message (the message `assert`
/// makes would show the list, which has no `toString`).
#[test]
fn every_expression_is_typed() {
    with_stack(|| {
        let mut library = Library::new();
        let main = "interface I { public x: Int; }\n\
                    class P(public x: Int) extends I {\n  public static origin = new P(0);\n}\n\
                    let leastX<T extends I>(a: T, b: T): T { if (a.x < b.x) { a } else { b } }\n\
                    let Names = ListBuilder<String>;\n\
                    let digit(c: Int): Int {\n\
                    \x20 let d = if (c >= char'0') { c - char'0' } else { return -1; };\n\
                    \x20 d\n}\n\
                    let b = new ListBuilder<Int>();\n\
                    let c: ListBuilder<String> = new ListBuilder();\n\
                    let n = new Names();\n\
                    let p = new Pair(1, \"a\");\n\
                    var i = 0;\n\
                    for (let x of [1, 2]) { b.add(x); i++; }\n\
                    let pure = doPure { b.toList() };\n\
                    let e = empty();\n\
                    let s = \"ab\";\n\
                    let j = s.next(String.begin);\n\
                    console.log(\"${leastX(P.origin, new P(1)).x} ${digit(char'7')} ${raw\"\\d\"} ${p.key}\");\n\
                    console.log(\"${c.length} ${n.length} ${pure.length} ${e == e} ${j < s.end}\");\n\
                    let none: List<Int>? = null;\n\
                    test(\"adds\") { assert(i == 2) }\n\
                    test(\"none\") { assert(none == null) { \"none isn't null\" } }";
        library.add_file("main.temper", main, SourceKind::Standalone);
        load_imports(&Sources::standard(), &mut library);
        let settled = settle(library);
        let messages: Vec<String> = (settled.library.diagnostics.iter())
            .map(|d| d.message.text())
            .collect();
        assert_eq!(messages, Vec::<String>::new());
        let lib = &settled.library;
        let text = |node: NodeId| lib.source_text(lib.tree.span(node)).to_string();
        let file = coverage(&settled)
            .into_iter()
            .find(|f| f.path == "main.temper")
            .unwrap();
        let untyped: Vec<String> = file.untyped.iter().map(|&(n, _)| text(n)).collect();
        let unresolved: Vec<String> = file.unresolved.iter().map(|&(n, _)| text(n)).collect();
        assert_eq!((untyped, unresolved), (Vec::new(), Vec::new()));
        assert!(file.exprs > 100, "{} expressions", file.exprs);
        let run = run_tests(&settled, Some(10_000_000));
        assert_eq!(run.failure, None);
        assert_eq!(run.output[0], "0 7 \\d 1");
        assert_eq!(run.output[1], "0 0 2 true true");
    });
}

/// std `net`'s `sendRequest`, over a small stand-in for std's `net`,
/// against a local server that answers one request, echoing it. Without
/// the `net` feature, the promise breaks, saying why.
#[test]
fn net_requests() {
    with_stack(|| {
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port();
        let mut library = Library::new();
        library.test_server_port = Some(port.into());
        let main = "let { send, NetResponse } = import(\"std/net\");\n\
                    let p = send(\"http://127.0.0.1:${testServerPort}/echo\");\n\
                    console.log(\"sent\");\n\
                    async { (): GeneratorResult<Empty> extends GeneratorFn =>\n\
                      let r = await p;\n\
                      let body = (await r.bodyContent) ?? \"none\";\n\
                      console.log(\"${r.status} ${r.contentType ?? \"none\"} ${r is NetResponse}: ${body}\");\n\
                    }";
        library.add_file("main.temper", main, SourceKind::Standalone);
        let net = "@connected\n\
                   let sendRequest(url: String, method: String, bodyContent: String?, bodyMimeType: String?): Promise<NetResponse>;\n\
                   export let send(url: String): Promise<NetResponse> { sendRequest(url, \"POST\", \"[]\", \"application/json\") }\n\
                   @connected export interface NetResponse {\n\
                     @connected get status(): Int;\n\
                     @connected get contentType(): String?;\n\
                     @connected get bodyContent(): Promise<String?>;\n\
                   }";
        library.add_file("-std-/net/net.temper", net, SourceKind::Standalone);
        let settled = settle(library);
        // Typing knows what `await p` gives, so `r is NetResponse` is
        // only informed of, as Kotlin Temper does.
        let messages: Vec<&Message> = (settled.library.diagnostics.iter())
            .map(|d| &d.message)
            .collect();
        assert_eq!(
            messages,
            [&Message::UnnecessaryRttiCheck(
                "NetResponse".into(),
                "NetResponse".into()
            )]
        );
        let server = cfg!(feature = "net").then(|| std::thread::spawn(move || echo_once(listener)));
        let run = run(&settled, Some(1_000_000));
        let (output, failure) = if cfg!(feature = "net") {
            let echo = "201 text/plain true: POST /echo HTTP/1.1, application/json, []";
            (vec!["sent", echo], None)
        } else {
            (vec!["sent"], Some("Failed: web client not supported here"))
        };
        let output: Vec<String> = output.into_iter().map(String::from).collect();
        assert_eq!((run.output, run.failure.as_deref()), (output, failure));
        if let Some(server) = server {
            server.join().unwrap();
        }
    });
}

/// Answers one HTTP request with 201 and, as text, its request line,
/// content type and body.
fn echo_once(listener: std::net::TcpListener) {
    use std::io::{BufRead, BufReader, Read, Write};
    let (stream, _) = listener.accept().unwrap();
    let mut reader = BufReader::new(stream);
    let mut lines = Vec::new();
    loop {
        let mut line = String::new();
        reader.read_line(&mut line).unwrap();
        match line.trim_end() {
            "" => break,
            line => lines.push(line.to_string()),
        }
    }
    let header = |name: &str| {
        lines.iter().find_map(|l| {
            let (key, value) = l.split_once(':')?;
            key.eq_ignore_ascii_case(name)
                .then(|| value.trim().to_string())
        })
    };
    let length = header("content-length").map_or(0, |n| n.parse().unwrap());
    let mut body = vec![0; length];
    reader.read_exact(&mut body).unwrap();
    let kind = header("content-type").unwrap_or_default();
    let echo = format!("{}, {kind}, {}", lines[0], String::from_utf8_lossy(&body));
    let response = format!(
        "HTTP/1.1 201 Created\r\nContent-Type: text/plain\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{echo}",
        echo.len()
    );
    reader.get_mut().write_all(response.as_bytes()).unwrap();
}

/// Pieces that matter to the settle loop, on top of the lexer's.
#[rustfmt::skip]
const SETTLE_PIECES: &[&str] = &[
    "let ", "var ", "let f(x: Int): Int { x }\n", "if (x) {", "} else {", "} else if (y) {", "while (",
    "for (var i = 0; i < 3; ++i) {", "break;", "continue;", "break outer;", "outer: ", "return ",
    "x", "f(", "=", "+=", "++", "--", "fn (", "=>", "do {", "} orelse ", "bubble()", "${",
    "console.log(", "@var ", "&&", "||", ";", "{", "}", "(", ")", "\n", "yield ", " extends GeneratorFn",
    ".next()", "await ", "async { (): GeneratorResult<Empty> extends GeneratorFn => ", "new PromiseBuilder<Int>()", ".promise",
    ".complete(1)", ".breakPromise()", "@connected ",
];

/// One missing or extra bracket is one local error: every diagnostic is
/// on the broken lines, and the later `h`, whose body has the wrong type,
/// is still typed. Each case is the source, the broken lines (from 1), and
/// the bracket error's text.
#[test]
fn bracket_recovery_stays_local() {
    const LATER: &str = "let h(): Int { \"s\" }\n";
    let cases: &[(&str, (usize, usize), &str)] = &[
        // The playground's case: one `)` deleted from a method.
        (
            "export class Stack<T> {\n  private items: ListBuilder<T> = new ListBuilder<T>();\n  \
             public push(item: T: Void { items.add(item); }\n  \
             public get size(): Int { items.length }\n}\n",
            (3, 3),
            "Expected `)` to close the `(` on line 3",
        ),
        // Parameter lists.
        (
            "class C {\n  public f(a: Int,\n    b: Int: Int {\n    a + b\n  }\n  \
             public g(): Int { 1 }\n}\n",
            (2, 3),
            "Expected `)` to close the `(` on line 2",
        ),
        (
            "class C {\n  public pop(: Int {\n    1\n  }\n}\n",
            (2, 2),
            "Expected `)` to close the `(` on line 2",
        ),
        (
            "let f(a: Int)): Int { a }\n",
            (1, 1),
            "Close bracket matches no open bracket",
        ),
        // Calls.
        (
            "let f(): Int {\n  let x = g(1, 2;\n  x\n}\nlet g(a: Int, b: Int): Int { a }\n",
            (2, 2),
            "Expected `)` to close the `(` on line 2",
        ),
        (
            "let f(): Int {\n  let x = g(1, 2));\n  x\n}\nlet g(a: Int, b: Int): Int { a }\n",
            (2, 2),
            "Close bracket matches no open bracket",
        ),
        (
            "let f(): Int {\n  var t = 0;\n  for (var i = 0; i < 3; ++i {\n    t += i;\n  }\n  t\n}\n",
            (3, 3),
            "Expected `)` to close the `(` on line 3",
        ),
        // Blocks.
        (
            "class C {\n  public f(): Int {\n    1\n  public g(): Int { 2 }\n}\n",
            (2, 3),
            "Expected `}` to close the `{` on line 2",
        ),
        (
            "let f(): Int {\n  if (true) {\n    1\n  } else {\n    2\n}\n",
            (4, 5),
            "Expected `}` to close the `{` on line 4",
        ),
        (
            "class C {\n  public f(): Int { 1 }\n",
            (1, 2),
            "Expected `}` to close the `{` on line 1",
        ),
        (
            "let f(): Int {\n  1\n}}\n",
            (3, 3),
            "Close bracket matches no open bracket",
        ),
        // List literals.
        (
            "let f(): List<Int> {\n  let x = [1, 2;\n  x\n}\n",
            (2, 2),
            "Expected `]` to close the `[` on line 2",
        ),
        (
            "let f(): List<Int> { [1, 2]] }\n",
            (1, 1),
            "Close bracket matches no open bracket",
        ),
        // Strings with interpolation.
        (
            "let f(x: Int): String { \"a${x.toString(}b\" }\n",
            (1, 1),
            "Expected `)` to close the `(` on line 1",
        ),
        (
            "let f(x: Int): String { \"a${x.toString())}b\" }\n",
            (1, 1),
            "Close bracket matches no open bracket",
        ),
        // Type arguments, and `<` as less-than without spaces: still an
        // error, as Kotlin rejects it, with a hint.
        (
            "let f(xs: List<Int): Int { 1 }\n",
            (1, 1),
            "Expected `>` to close `<`",
        ),
        (
            "let f(a: Int, b: Int): Boolean { a<b }\n",
            (1, 1),
            "Expected `>` to close `<`, or did you mean `a < b`?",
        ),
        (
            "let f(a: Int, b: Int): Int { if (a<b) { 1 } else { 2 } }\n",
            (1, 1),
            "Expected `>` to close `<`, or did you mean `a < b`?",
        ),
        (
            "let a = 1; let b = 2;\nlet x = a<b;\nlet y = a > b;\n",
            (2, 2),
            "Expected `>` to close `<`, or did you mean `a < b`?",
        ),
    ];
    for &(broken, (first, last), expected) in cases {
        let source = format!("{broken}{LATER}");
        let later = source.lines().count();
        let mut library = Library::new();
        library.add_file("test.temper", &source, SourceKind::Standalone);
        let settled = settle(library);
        let found: Vec<(usize, String)> = (settled.library.diagnostics.iter())
            .map(|d| {
                let line = 1 + source[..d.span.start as usize].matches('\n').count();
                (line, d.message.text())
            })
            .collect();
        let context = format!("{source}\n{found:#?}");
        assert!(
            found.iter().any(|(_, m)| m == expected),
            "missing {expected:?}: {context}"
        );
        for (line, message) in &found {
            let local = (first..=last).contains(line);
            let typed = *line == later && message == "Cannot assign to Int32 from String";
            assert!(local || typed, "not local: {line}: {message}: {context}");
        }
        assert!(
            found.iter().any(|(line, _)| *line == later),
            "later declaration not typed: {context}"
        );
        assert!(
            (settled.library.diagnostics.iter()).all(|d| d.level == Level::Error),
            "{context}"
        );
    }
}

/// Operands side by side with no operator between them, as in `cents 100`
/// for `cents % 100`, are one error, at the stray operand, and the
/// statement is kept: a declaration is still declared, with the first
/// operand's type, so later uses of it are no errors. Each case is the
/// source, the stray's text, and the error's text.
#[test]
fn missing_operator_keeps_the_statement() {
    let cases: &[(&str, &str, &str)] = &[
        // The playground's case.
        (
            "let cents = 1234;\nlet part = cents  100;\nlet more = part + 1;\n\
             console.log(\"${part} ${more}\");\n",
            "100",
            "Expected an operator or `;` before `100`",
        ),
        (
            "let a = 1; let b = 2;\nlet c = a b;\nlet d = c * 2;\n",
            "b",
            "Expected an operator or `;` before `b`",
        ),
        (
            "let f(): Int { 1 }\nlet g(): Int { 2 }\nlet c = f() g();\n\
             let d = c.toString();\n",
            "g()",
            "Expected an operator or `;` before `g`",
        ),
        // An assignment.
        (
            "var x = 0;\nlet a = 1;\nx = a 1;\nlet y = x + 1;\n",
            "1",
            "Expected an operator or `;` before `1`",
        ),
        // An argument.
        (
            "let a = 1;\nlet f(n: Int): Int { n }\nlet c = f(a 1);\nlet d = c + 1;\n",
            "1",
            "Expected an operator or `,` before `1`",
        ),
        // A return.
        (
            "let a = 1;\nlet f(): Int { return a 1; }\nlet c = f() + 1;\n",
            "1",
            "Expected an operator or `;` before `1`",
        ),
        // A condition.
        (
            "let a = true; let b = false;\nvar z = 0;\nif (a b) { z = 1; }\nlet w = z + 1;\n",
            "b",
            "Expected an operator or `,` before `b`",
        ),
        // The first operand's type is the declaration's.
        (
            "let cents = 1234;\nlet part = cents 100;\nlet s: Int = part;\nlet more = part + 1;\n",
            "100",
            "Expected an operator or `;` before `100`",
        ),
    ];
    for &(source, stray, expected) in cases {
        let mut library = Library::new();
        library.add_file("test.temper", source, SourceKind::Standalone);
        let settled = settle(library);
        let found: Vec<(&str, String)> = (settled.library.diagnostics.iter())
            .map(|d| {
                let span = d.span.start as usize..d.span.end as usize;
                (&source[span], d.message.text())
            })
            .collect();
        assert_eq!(found, [(stray, expected.to_string())], "{source}");
    }
    // Typed as the first operand: an `Int` that doesn't fit a `String`.
    let source = "let cents = 1234;\nlet part = cents 100;\nlet s: String = part;\n";
    let mut library = Library::new();
    library.add_file("test.temper", source, SourceKind::Standalone);
    let settled = settle(library);
    let found: Vec<String> = (settled.library.diagnostics.iter())
        .map(|d| d.message.text())
        .collect();
    assert_eq!(
        found,
        [
            "Expected an operator or `;` before `100`",
            "Cannot assign to String from Int32"
        ],
        "{source}"
    );
}

#[test]
fn mutated_inputs_settle() {
    with_stack(|| {
        let seed = env_number("SETTLE_FUZZ_SEED", 1);
        let rounds = env_number("SETTLE_FUZZ_ROUNDS", 20);
        let mut rng = Rng(seed.max(1));
        let pieces: Vec<&str> = common::PIECES
            .iter()
            .chain(SETTLE_PIECES)
            .copied()
            .collect();
        let mut inputs = Vec::new();
        for (dir, separator) in [
            ("tests/settle", "settled"),
            ("../temper-syntax/tests/ast", "ast"),
        ] {
            for (path, cases) in case_files(dir, separator) {
                for case in cases {
                    inputs.push((
                        format!("{} case {:?}", path.display(), case.name),
                        case.input,
                        case.kind,
                    ));
                }
            }
        }
        if let Ok(dir) = std::env::var("SETTLE_FUZZ_DIR") {
            add_files(std::path::Path::new(&dir), &mut inputs);
        }
        for (context, input, kind) in inputs {
            check(&input, kind, &context);
            for round in 0..rounds {
                let text = mutate(&input, &pieces, &mut rng);
                check(
                    &text,
                    kind,
                    &format!("{context}, seed {seed} round {round}"),
                );
            }
        }
    });
}

fn add_files(dir: &std::path::Path, inputs: &mut Vec<(String, String, SourceKind)>) {
    let mut entries: Vec<_> = std::fs::read_dir(dir)
        .unwrap()
        .map(|e| e.unwrap().path())
        .collect();
    entries.sort();
    for path in entries {
        let name = path.to_string_lossy().to_string();
        if path.is_dir() {
            add_files(&path, inputs);
        } else if name.ends_with(".temper") || name.ends_with(".temper.md") {
            let kind = if name.ends_with(".md") {
                SourceKind::Markdown
            } else {
                SourceKind::Standalone
            };
            inputs.push((name, std::fs::read_to_string(&path).unwrap(), kind));
        }
    }
}

fn check(text: &str, kind: SourceKind, context: &str) {
    let result = std::panic::catch_unwind(|| settled(text, kind, 20_000));
    let Ok((once, out_of_steps)) = result else {
        panic!("{context}: panicked\ninput: {text:?}");
    };
    assert!(
        !out_of_steps,
        "{context}: ran out of steps\ninput: {text:?}"
    );
    let (again, _) = settled(text, kind, 20_000);
    assert_eq!(
        once, again,
        "{context}: settled differently twice\ninput: {text:?}"
    );
}

/// The toy content tag of `settle/autoescape.cases`, whose propagation
/// first calls `hook(known)`, which each test declares.
fn toy_tag() -> String {
    let cases = include_str!("settle/autoescape.cases");
    let start = cases.find("=== content tags\n").unwrap() + "=== content tags\n".len();
    let end = cases.find("let q = Quoting;").unwrap();
    let null_check = "  if (known == null) { return new After([], before); }";
    let lib = cases[start..end].replace(null_check, &format!("  hook(known);\n{null_check}"));
    assert!(lib.contains("hook(known)"));
    format!("{lib}let q = Quoting;\nlet greet(x: String): String {{ q\"a '${{x}}' b\" }}\n")
}

/// Settles a library of the toy tag and `hook`, with the fake std
/// modules given, returning whether the tag's use folded, and the
/// settled library.
fn toy_settled(hook: &str, files: &[(&str, &str)]) -> (bool, temper_frontend::settle::Settled) {
    let mut library = Library::new();
    let main = format!("{hook}\n{}", toy_tag());
    library.add_file("main.temper", &main, SourceKind::Standalone);
    for &(path, text) in files {
        library.add_file(path, text, SourceKind::Standalone);
    }
    let settled = settle(library);
    let mut tree = String::new();
    write_settled(&settled, 100, &mut tree);
    (tree.contains("collector#"), settled)
}

/// What a use's propagation reaches, its `hook`, and the fake std files.
type ReachCase = (&'static str, String, Vec<(&'static str, &'static str)>);

/// Nothing reaches outside the interpreter at compile time: a use whose
/// propagation would send a request, read the clock, call a library's
/// own `@connected` function, use a std module not on the allowlist, or
/// start an `async` block is left to run time, and no request is made,
/// even from the module's top level.
#[test]
fn content_tags_reach_nothing_at_compile_time() {
    with_stack(|| {
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        listener.set_nonblocking(true).unwrap();
        let port = listener.local_addr().unwrap().port();
        let url = format!("http://127.0.0.1:{port}/compile-time");
        let net = "@connected\n\
                   let sendRequest(url: String, method: String, bodyContent: String?, bodyMimeType: String?): Promise<String>;\n\
                   export let send(url: String): Promise<String> { sendRequest(url, \"GET\", null, null) }";
        let temporal = "export class Date(public year: Int) {\n\
                          @connected public static today(): Date;\n\
                        }";
        let other = "export let pure(s: String): String { s }";
        let net_file = ("-std-/net/net.temper", net);
        // Control: a pure hook folds.
        let (folded, _) = toy_settled("let hook(k: String?): Void {}", &[]);
        assert!(folded, "the control should fold");
        let cases: Vec<ReachCase> = vec![
            (
                "a request in the propagation",
                format!("let {{ send }} = import(\"std/net\");\nlet hook(k: String?): Void {{ send(\"{url}\"); }}"),
                vec![net_file],
            ),
            (
                "a request at the top level",
                format!("let {{ send }} = import(\"std/net\");\nlet hook(k: String?): Void {{}}\nsend(\"{url}\");"),
                vec![net_file],
            ),
            (
                "the clock",
                "let { Date } = import(\"std/temporal\");\nlet hook(k: String?): Void { Date.today(); }".into(),
                vec![("-std-/temporal/temporal.temper", temporal)],
            ),
            (
                "a std module not on the allowlist",
                "let { pure } = import(\"std/other\");\nlet hook(k: String?): Void { pure(\"x\"); }".into(),
                vec![("-std-/other/other.temper", other)],
            ),
            (
                "a library's own @connected function",
                "@connected\nlet effect(): Void;\nlet hook(k: String?): Void { effect(); }".into(),
                vec![],
            ),
            (
                "an async block",
                "let hook(k: String?): Void {\n  async { (): GeneratorResult<Empty> extends GeneratorFn => void; }\n}".into(),
                vec![],
            ),
        ];
        for (what, hook, files) in cases {
            let (folded, settled) = toy_settled(&hook, &files);
            assert!(!folded, "{what}: folded");
            // Left to run time for what it reaches, not for an error.
            let errors: Vec<String> = (settled.library.diagnostics.iter())
                .filter(|d| d.level == Level::Error)
                .map(|d| d.message.text())
                .collect();
            assert!(errors.is_empty(), "{what}: {errors:?}");
        }
        assert!(
            matches!(listener.accept(), Err(e) if e.kind() == std::io::ErrorKind::WouldBlock),
            "a request was made at compile time"
        );
    });
}

/// A use whose propagation runs out of steps at compile time is left to
/// run time, and the session that ran out isn't used again: the next use
/// folds, and both give what the tag's own methods do.
#[test]
fn content_tags_out_of_steps() {
    with_stack(|| {
        let hook = "let hook(k: String?): Void {\n\
                      if (k == \"slow\") { var i = 0; while (i < 2000000) { i += 1; } }\n\
                    }\n\
                    let slow(x: String): String { q\"slow${x}\" }";
        let (_, settled) = toy_settled(hook, &[]);
        let mut tree = String::new();
        write_settled(&settled, 100, &mut tree);
        let slow = &tree[tree.find("^slow:").unwrap()..];
        let slow = &slow[..slow.find("\n  (Let").unwrap_or(slow.len())];
        assert!(slow.contains("accumulator#"), "slow folded:\n{slow}");
        let greet = &tree[tree.find("^greet:").unwrap()..];
        let greet = &greet[..greet.find("\n  (Let").unwrap_or(greet.len())];
        assert!(greet.contains("collector#"), "greet didn't fold:\n{greet}");
    });
}

/// A use whose propagation fails partway, after changing module state,
/// is left to run time, and the session starts again before the next use,
/// whose propagation would fail on that state: so it folds.
#[test]
fn content_tags_renew_after_failure() {
    with_stack(|| {
        let hook = "var poisoned = false;\n\
                    let hook(k: String?): Void {\n\
                      if (k == \"poison\") { poisoned = true; panic(); }\n\
                      if (poisoned) { panic(); }\n\
                    }\n\
                    let bad(x: String): String { q\"poison${x}\" }";
        let (_, settled) = toy_settled(hook, &[]);
        let mut tree = String::new();
        write_settled(&settled, 100, &mut tree);
        let bad = &tree[tree.find("^bad:").unwrap()..];
        let bad = &bad[..bad.find("\n  (Let").unwrap_or(bad.len())];
        assert!(bad.contains("accumulator#"), "bad folded:\n{bad}");
        let greet = &tree[tree.find("^greet:").unwrap()..];
        let greet = &greet[..greet.find("\n  (Let").unwrap_or(greet.len())];
        assert!(greet.contains("collector#"), "greet didn't fold:\n{greet}");
    });
}

/// What compile-time runs may use is listed in one place, so additions
/// are deliberate: printed here, and checked against what's expected.
#[test]
fn compile_time_allowlist() {
    use temper_frontend::builtins::{Builtin, Function};
    use temper_frontend::interp::compile_time::{
        ALLOWED_STD_MODULES, DENIED_MEMBERS, builtin_allowed,
    };
    println!("std modules allowed: {ALLOWED_STD_MODULES:?}");
    println!("members denied: {DENIED_MEMBERS:?}");
    assert_eq!(ALLOWED_STD_MODULES, ["regex", "json", "temporal"]);
    assert_eq!(DENIED_MEMBERS, [("temporal", "Date", "today")]);
    let builtins: Vec<&str> = temper_frontend::builtins::BUILTINS
        .iter()
        .filter_map(|&(name, b)| match b {
            Builtin::Function(f) if builtin_allowed(f) => Some(name),
            _ => None,
        })
        .collect();
    println!("builtin functions allowed: {builtins:?}");
    assert!(!builtin_allowed(Function::Async), "async runs code later");
    assert!(builtin_allowed(Function::Cat));
}
