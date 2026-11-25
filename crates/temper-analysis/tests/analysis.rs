//! The editor queries in `analysis`: hover, go to definition,
//! completion, and diagnostics' spans, over one settled library.

use temper_analysis::{Analysis, DeclKind, Position, byte_offset};
use temper_frontend::library::{FileId, Level, Library};
use temper_frontend::project::load_imports;
use temper_frontend::settle::settle;
use temper_frontend::sources::Sources;
use temper_syntax::lex::SourceKind;
use temper_syntax::tree::Span;

const MAIN: FileId = FileId(0);

fn analyze(source: &str) -> Analysis {
    let mut library = Library::new();
    library.add_file("main.temper", source, SourceKind::Standalone);
    load_imports(&Sources::memory(&[]), &mut library);
    Analysis::new(settle(library))
}

/// The byte offset of the `nth` (from 0) `needle` in `source`, plus `ahead`.
fn at(source: &str, needle: &str, nth: usize, ahead: u32) -> u32 {
    let (i, _) = source.match_indices(needle).nth(nth).expect(needle);
    i as u32 + ahead
}

const STACK: &str = r#"
/** A last-in, first-out stack. */
export class Stack<T> {
  private items: ListBuilder<T> = new ListBuilder<T>();

  /** Adds an item on top. */
  public push(item: T): Void { items.add(item); }

  public get size(): Int { items.length }
}

/** Doubles `n`. */
export let twice(n: Int, extra: Int = 0): Int { n * 2 + extra }

var count = 0;
let stack = new Stack<String>();
stack.push("a");
count = twice(stack.size);
let label = "n: ${count.toString()}";
"#;

#[test]
fn hover_on_a_local() {
    let a = analyze(STACK);
    let h = a.hover(MAIN, at(STACK, "stack.push", 0, 2)).unwrap();
    assert_eq!(h.name.as_deref(), Some("stack"));
    assert_eq!(h.kind, Some(DeclKind::Let));
    assert_eq!(h.ty.as_deref(), Some("Stack<String>"));
    let start = at(STACK, "stack.push", 0, 0);
    assert_eq!((h.span.start, h.span.end), (start, start + 5));

    let h = a.hover(MAIN, at(STACK, "count =", 1, 1)).unwrap();
    assert_eq!(h.kind, Some(DeclKind::Var));
    assert_eq!(h.ty.as_deref(), Some("Int32"));
}

#[test]
fn hover_on_a_declaration() {
    let a = analyze(STACK);
    let h = a.hover(MAIN, at(STACK, "count", 0, 0)).unwrap();
    assert_eq!(h.name.as_deref(), Some("count"));
    assert_eq!(h.kind, Some(DeclKind::Var));
    let h = a.hover(MAIN, at(STACK, "Stack<T>", 0, 1)).unwrap();
    assert_eq!(h.kind, Some(DeclKind::Class));
    assert_eq!(h.ty.as_deref(), Some("Stack<T>"));
    assert_eq!(h.doc.as_deref(), Some("A last-in, first-out stack."));
}

#[test]
fn hover_on_a_parameter() {
    let a = analyze(STACK);
    let h = a.hover(MAIN, at(STACK, "n * 2", 0, 0)).unwrap();
    assert_eq!(h.name.as_deref(), Some("n"));
    assert_eq!(h.kind, Some(DeclKind::Parameter));
    assert_eq!(h.ty.as_deref(), Some("Int32"));
    let h = a.hover(MAIN, at(STACK, "add(item)", 0, 5)).unwrap();
    assert_eq!(h.kind, Some(DeclKind::Parameter));
    assert_eq!(h.ty.as_deref(), Some("T"));
}

/// A `catch (e)` binding, as declared and as used, has the type of
/// what its operand may fail with.
#[test]
fn hover_on_a_catch_binding() {
    const SOURCE: &str = r#"
sealed interface LookupError extends Error {}
class NotFound(public key: String) extends LookupError {}
let lookup(key: String): Int throws NotFound { throw new NotFound(key) }
let n = lookup("a") catch (e) { console.log(e.key); 0 };
"#;
    let a = analyze(SOURCE);
    for offset in [at(SOURCE, "(e)", 0, 1), at(SOURCE, "e.key", 0, 0)] {
        let h = a.hover(MAIN, offset).unwrap();
        assert_eq!(h.name.as_deref(), Some("e"));
        assert_eq!(h.kind, Some(DeclKind::Let));
        assert_eq!(h.ty.as_deref(), Some("NotFound"));
        let start = at(SOURCE, "(e)", 0, 1);
        let d = a.definition(MAIN, offset).unwrap();
        assert_eq!((d.span.start, d.span.end), (start, start + 1));
    }
}

#[test]
fn hover_on_members() {
    let a = analyze(STACK);
    let h = a.hover(MAIN, at(STACK, "push(\"a\")", 0, 1)).unwrap();
    assert_eq!(h.name.as_deref(), Some("push"));
    assert_eq!(h.kind, Some(DeclKind::Method));
    // On a `Stack<String>`, `T` is `String`.
    assert_eq!(h.ty.as_deref(), Some("push(item: String): Void"));
    assert_eq!(h.doc.as_deref(), Some("Adds an item on top."));
    let h = a.hover(MAIN, at(STACK, "stack.size", 0, 7)).unwrap();
    assert_eq!(h.name.as_deref(), Some("size"));
    assert_eq!(h.kind, Some(DeclKind::Getter));
    let h = a.hover(MAIN, at(STACK, "items.add", 0, 1)).unwrap();
    assert_eq!(h.kind, Some(DeclKind::Field));
    assert_eq!(h.ty.as_deref(), Some("ListBuilder<T>"));
    // A builtin type's member, as core declares it.
    let h = a.hover(MAIN, at(STACK, "toString", 0, 2)).unwrap();
    assert_eq!(h.name.as_deref(), Some("toString"));
    assert_eq!(h.kind, Some(DeclKind::Method));
    assert!(
        h.ty.as_deref().is_some_and(|t| t.starts_with("toString(")),
        "{h:?}"
    );
    // In the class, `items` is still a `ListBuilder<T>`; its `add` takes a `T`.
    let h = a.hover(MAIN, at(STACK, "items.add", 0, 7)).unwrap();
    assert_eq!(h.ty.as_deref(), Some("add(newValue: T, at?: Int32): Void"));
}

#[test]
fn hover_on_a_call() {
    let a = analyze(STACK);
    let h = a.hover(MAIN, at(STACK, "twice(stack", 0, 1)).unwrap();
    assert_eq!(h.kind, Some(DeclKind::Function));
    assert_eq!(
        h.ty.as_deref(),
        Some("twice(n: Int32, extra?: Int32): Int32")
    );
    assert_eq!(h.doc.as_deref(), Some("Doubles `n`."));
    // On the call's parenthesis: the call's type.
    let h = a.hover(MAIN, at(STACK, "twice(stack", 0, 5)).unwrap();
    assert_eq!(h.name, None);
    assert_eq!(h.ty.as_deref(), Some("Int32"));
    // A builtin.
    let h = a.hover(MAIN, at(STACK, "ListBuilder<T>()", 0, 1)).unwrap();
    assert_eq!(h.kind, Some(DeclKind::Builtin));
}

#[test]
fn definitions() {
    let a = analyze(STACK);
    let d = a.definition(MAIN, at(STACK, "stack.push", 0, 1)).unwrap();
    let decl = at(STACK, "stack =", 0, 0);
    assert_eq!((d.file, d.span.start, d.span.end), (MAIN, decl, decl + 5));
    let d = a.definition(MAIN, at(STACK, "push(\"a\")", 0, 0)).unwrap();
    assert_eq!(d.span.start, at(STACK, "push(item", 0, 0));
    let d = a.definition(MAIN, at(STACK, "n * 2", 0, 0)).unwrap();
    assert_eq!(d.span.start, at(STACK, "n: Int", 0, 0));
    assert_eq!(
        a.definition(MAIN, at(STACK, "ListBuilder<T>()", 0, 1)),
        None
    );
}

#[test]
fn completions() {
    let a = analyze(STACK);
    let names: Vec<String> = (a.completions(MAIN, at(STACK, "n * 2", 0, 0)).into_iter())
        .map(|c| c.name)
        .collect();
    for name in ["n", "extra", "twice", "Stack", "console"] {
        assert!(names.contains(&name.to_string()), "{name} in {names:?}");
    }
    assert!(names.iter().position(|n| n == "n") < names.iter().position(|n| n == "console"));

    let source = format!("{STACK}stack.");
    let a = analyze(&source);
    let members = a.completions(MAIN, source.len() as u32);
    let names: Vec<&str> = members.iter().map(|c| c.name.as_str()).collect();
    assert_eq!(names, ["push", "size"]);
    assert_eq!(
        members[0].detail.as_deref(),
        Some("push(item: String): Void")
    );
}

/// Diagnostics' spans as the text they cover.
fn problems(source: &str) -> Vec<(Level, String, &str)> {
    let a = analyze(source);
    (a.diagnostics().into_iter())
        .filter(|p| p.file == MAIN)
        .map(|p| {
            let text = &source[p.span.start as usize..p.span.end as usize];
            (p.level, p.message, text)
        })
        .collect()
}

#[test]
fn diagnostics_are_tight_and_all_there() {
    let source = r#"let f(a: Int): Int { a + "x" }
let g(): Void {
  let y = 1 +;
  zzz(2);
}
let h: String = f(1);
let k = f("s");
let later = f(2) + 1;
"#;
    let found = problems(source);
    let texts: Vec<&str> = found.iter().map(|p| p.2).collect();
    assert!(found.iter().all(|p| p.0 == Level::Error));
    // Parse, settle (an undeclared name), and typing errors, each where
    // it is, and later declarations still typed (keep on trucking).
    assert_eq!(
        texts,
        ["let y = 1 +", "zzz", "\"x\"", "f(1)", "\"s\""],
        "{found:?}"
    );
    let a = analyze(source);
    let h = a.hover(MAIN, at(source, "later", 0, 1)).unwrap();
    assert_eq!(h.ty.as_deref(), Some("Int32"));
}

#[test]
fn diagnostics_past_a_broken_bracket() {
    let source = "export let f(): Int { (1 + 2; }\nlet x: Int = \"s\";\nlet y = f() + 1;\n";
    let found = problems(source);
    assert!(found.len() >= 2, "{found:?}");
    assert!(found.iter().any(|p| p.2 == "\"s\""), "{found:?}");
    let a = analyze(source);
    let h = a.hover(MAIN, at(source, "y =", 0, 0)).unwrap();
    assert_eq!(h.ty.as_deref(), Some("Int32"));
}

#[test]
fn utf16_at_the_boundary() {
    let source = "let s = \"é😀\"; let t = s;";
    let t = at(source, "t =", 0, 0) as usize;
    let p = Position::of(source, t);
    assert_eq!(p.utf16, t - 3);
    assert_eq!(byte_offset(source, p.utf16), t);
    // Inside the surrogate pair: the emoji's start.
    let emoji = source.find('😀').unwrap();
    let pair = Position::of(source, emoji).utf16;
    assert_eq!(byte_offset(source, pair + 1), emoji);
    assert_eq!(byte_offset(source, 10_000), source.len());
}

#[test]
fn getters_show_their_type() {
    let a = analyze(STACK);
    let h = a.hover(MAIN, at(STACK, "stack.size", 0, 7)).unwrap();
    assert_eq!(h.ty.as_deref(), Some("Int32"));
}

/// Completion's names at the end of `STACK` plus `tail`.
fn completed(tail: &str) -> Vec<(String, Option<String>)> {
    let source = format!("{STACK}{tail}");
    let a = analyze(&source);
    (a.completions(MAIN, source.len() as u32).into_iter())
        .map(|c| (c.name, c.detail))
        .collect()
}

fn names_of(found: &[(String, Option<String>)]) -> Vec<&str> {
    found.iter().map(|(n, _)| n.as_str()).collect()
}

#[test]
fn builtin_members_complete() {
    // Builtin types' members, with their type arguments.
    let found = completed("let b = new ListBuilder<String>();\nb.");
    let add = found.iter().find(|(n, _)| n == "add").expect("add");
    assert_eq!(
        add.1.as_deref(),
        Some("add(newValue: String, at?: Int32): Void")
    );
    assert!(names_of(&found).contains(&"length"), "{found:?}");
    let found = completed("let m = new Map<String, Int>([]);\nm.");
    assert!(names_of(&found).contains(&"get"), "{found:?}");
    let found = completed("let t = \"abc\";\nt.");
    assert!(names_of(&found).contains(&"split"), "{found:?}");
    // A builtin type's statics after its name.
    let found = completed("String.");
    assert!(names_of(&found).contains(&"fromCodePoint"), "{found:?}");
    // `console`'s, which core declares a `Console`, alone or before more.
    let log = ("log".to_string(), Some("log(message: String): Void".into()));
    assert_eq!(completed("console."), [log.clone()]);
    assert_eq!(completed("console.l"), [log.clone()]);
    let source = "console.
let after = 1;
";
    let a = analyze(source);
    let found: Vec<String> = (a.completions(MAIN, 8).into_iter())
        .map(|c| c.name)
        .collect();
    assert_eq!(found, ["log"]);
}

#[test]
fn completion_after_any_expression() {
    // A call's result, a member's, `this`, and a prefix typed.
    let found = completed("let make(): Stack<Int> { new Stack<Int>() }\nmake().");
    assert_eq!(names_of(&found), ["push", "size"]);
    assert_eq!(found[0].1.as_deref(), Some("push(item: Int32): Void"));
    let found = completed("let label2 = label.toString().spl");
    assert!(names_of(&found).contains(&"split"), "{found:?}");
    let source = "class Box(public item: String) {\n  public get size(): Int { this.item.";
    let a = analyze(source);
    let names: Vec<String> = (a.completions(MAIN, source.len() as u32).into_iter())
        .map(|c| c.name)
        .collect();
    assert!(names.iter().any(|n| n == "split"), "{names:?}");
    let source = "class Box(public item: String) {\n  public get size(): Int { this.";
    let a = analyze(source);
    let names: Vec<String> = (a.completions(MAIN, source.len() as u32).into_iter())
        .map(|c| c.name)
        .collect();
    assert!(names.iter().any(|n| n == "item"), "{names:?}");
}

#[test]
fn no_member_spans_just_the_name() {
    // Kotlin spans `s.lenght`; editors underline `lenght`.
    let found = problems("let s = \"abc\";\nlet n = s.lenght;\n");
    assert_eq!(found.len(), 1, "{found:?}");
    assert_eq!(found[0].2, "lenght");
}

const SHAPES: &str = r#"let { Regex } = import("std/regex");
class Point(public x: Int, public y: Int) {}
let origin: Point = { x: 0, y: 0 };
let make(x: Int): Point { { x, y: 1 } }
let far = new Point(10, origin.x);
let pattern = new Regex("a");
"#;

#[test]
fn definition_into_std() {
    // std's declarations are in the library's files.
    let a = analyze(SHAPES);
    let d = a.definition(MAIN, at(SHAPES, "Regex(", 0, 1)).unwrap();
    assert!(a.is_std(d.file), "{d:?}");
    let path = &a.settled.library.files[d.file.0 as usize].path;
    assert!(path.starts_with("-std-/regex/"), "{path}");
}

#[test]
fn references_across_uses() {
    let a = analyze(STACK);
    let refs = a.references(MAIN, at(STACK, "stack.push", 0, 1), true);
    let starts: Vec<u32> = refs.iter().map(|r| r.span.start).collect();
    assert_eq!(
        starts,
        [
            at(STACK, "stack =", 0, 0),
            at(STACK, "stack.push", 0, 0),
            at(STACK, "stack.size", 0, 0)
        ]
    );
    // Without the declaration; and a method, by its uses after `.`.
    assert_eq!(
        a.references(MAIN, at(STACK, "stack.push", 0, 1), false)
            .len(),
        2
    );
    let refs = a.references(MAIN, at(STACK, "push(item", 0, 1), true);
    let texts: Vec<&str> = (refs.iter())
        .map(|r| &STACK[r.span.start as usize..r.span.end as usize])
        .collect();
    assert_eq!(texts, ["push", "push"]);
    // A field, used bare in its class.
    assert_eq!(a.references(MAIN, at(STACK, "items:", 0, 1), true).len(), 3);
}

#[test]
fn references_across_files() {
    let mut library = Library::new();
    library.add_file(
        "a.temper",
        "export let shared = 1;\n",
        SourceKind::Standalone,
    );
    library.add_file(
        "b.temper",
        "let b = shared + shared;\n",
        SourceKind::Standalone,
    );
    load_imports(&Sources::memory(&[]), &mut library);
    let a = Analysis::new(settle(library));
    let refs = a.references(FileId(1), 9, true);
    let files: Vec<u32> = refs.iter().map(|r| r.file.0).collect();
    assert_eq!(files, [0, 1, 1]);
    let edits = a.rename(FileId(1), 9, "common").unwrap();
    assert_eq!(edits.len(), 3);
    assert!(edits.iter().all(|e| e.text == "common"));
}

/// The source with the edits made.
fn edited(source: &str, mut edits: Vec<temper_analysis::Edit>) -> String {
    edits.sort_by_key(|e| std::cmp::Reverse(e.span.start));
    let mut out = source.to_string();
    for e in edits {
        out.replace_range(e.span.start as usize..e.span.end as usize, &e.text);
    }
    out
}

#[test]
fn rename() {
    let a = analyze(STACK);
    let (span, name) = a
        .prepare_rename(MAIN, at(STACK, "items.add", 0, 1))
        .unwrap();
    assert_eq!((name.as_str(), span.end - span.start), ("items", 5));
    let edits = a
        .rename(MAIN, at(STACK, "items.add", 0, 1), "elements")
        .unwrap();
    let renamed = edited(STACK, edits);
    assert!(
        renamed.contains("private elements: ListBuilder<T>"),
        "{renamed}"
    );
    assert!(renamed.contains("elements.add(item)") && renamed.contains("{ elements.length }"));
    // Builtins, std, keywords, and what isn't a name are refused.
    assert!(
        a.prepare_rename(MAIN, at(STACK, "ListBuilder<T>()", 0, 1))
            .is_err()
    );
    assert!(a.rename(MAIN, at(STACK, "count", 0, 0), "if").is_err());
    assert!(a.rename(MAIN, at(STACK, "count", 0, 0), "a b").is_err());
    let a = analyze(SHAPES);
    let why = a
        .prepare_rename(MAIN, at(SHAPES, "Regex(", 0, 1))
        .unwrap_err();
    assert!(why.contains("std"), "{why}");
}

#[test]
fn rename_reaches_object_literals() {
    let a = analyze(SHAPES);
    let edits = a
        .rename(MAIN, at(SHAPES, "origin.x", 0, 7), "left")
        .unwrap();
    let renamed = edited(SHAPES, edits);
    assert!(renamed.contains("public left: Int"), "{renamed}");
    assert!(renamed.contains("{ left: 0, y: 0 }"), "{renamed}");
    // Shorthand keeps its value, the parameter `x`.
    assert!(renamed.contains("{ left: x, y: 1 }"), "{renamed}");
    assert!(renamed.contains("origin.left"), "{renamed}");
    assert!(renamed.contains("make(x: Int)"), "{renamed}");
}

#[test]
fn document_symbols() {
    let a = analyze(STACK);
    let symbols = a.document_symbols(MAIN);
    let names: Vec<&str> = symbols.iter().map(|s| s.name.as_str()).collect();
    assert_eq!(names, ["Stack", "twice", "count", "stack", "label"]);
    let stack = &symbols[0];
    assert_eq!(stack.kind, DeclKind::Class);
    let members: Vec<(&str, DeclKind)> = (stack.children.iter())
        .map(|s| (s.name.as_str(), s.kind))
        .collect();
    assert_eq!(
        members,
        [
            ("items", DeclKind::Field),
            ("push", DeclKind::Method),
            ("size", DeclKind::Getter)
        ]
    );
    let twice = &symbols[1];
    assert_eq!(
        twice.detail.as_deref(),
        Some("twice(n: Int32, extra?: Int32): Int32")
    );
    let name = at(STACK, "twice(n", 0, 0);
    assert_eq!(
        twice.selection,
        Span {
            start: name,
            end: name + 5
        }
    );
    assert!(
        twice.span.start < name && twice.span.end > name + 20,
        "{twice:?}"
    );
}

#[test]
fn signature_help() {
    let source = format!("{STACK}let z = twice(1, ");
    let a = analyze(&source);
    let help = a.signature_help(MAIN, source.len() as u32).unwrap();
    assert_eq!(help.label, "twice(n: Int32, extra?: Int32): Int32");
    assert_eq!(help.params, ["n: Int32", "extra?: Int32"]);
    assert_eq!(help.active, 1);
    assert_eq!(help.doc.as_deref(), Some("Doubles `n`."));
    // A method on a generic receiver, inside nested brackets.
    let source = format!("{STACK}stack.push([1, 2].length.toString(");
    let a = analyze(&source);
    let help = a.signature_help(
        MAIN,
        (source.len() - "[1, 2].length.toString(".len()) as u32,
    );
    assert_eq!(help.unwrap().label, "push(item: String): Void");
    // A builtin's, and a constructor's.
    let source = format!("{STACK}let b = new ListBuilder<Int>();\nb.add(1, ");
    let a = analyze(&source);
    let help = a.signature_help(MAIN, source.len() as u32).unwrap();
    assert_eq!(help.params, ["newValue: Int32", "at?: Int32"]);
    assert_eq!(help.active, 1);
    let a = analyze(SHAPES);
    let help = a
        .signature_help(MAIN, at(SHAPES, "10, origin", 0, 4))
        .unwrap();
    assert_eq!(help.label, "Point(x: Int32, y: Int32)");
    assert_eq!(help.active, 1);
    // Not in a call, or in a string.
    assert_eq!(a.signature_help(MAIN, at(SHAPES, "far", 0, 0)), None);
    assert_eq!(a.signature_help(MAIN, at(SHAPES, "\"a\"", 0, 1)), None);
}

#[test]
fn object_literal_keys_complete() {
    let names = |source: &str| -> Vec<String> {
        let a = analyze(source);
        let offset = source.find('|').unwrap();
        let source = source.replace('|', "");
        let a2 = analyze(&source);
        drop(a);
        (a2.completions(MAIN, offset as u32).into_iter())
            .map(|c| c.name)
            .collect()
    };
    let class = "class Person(public name: String, public age: Int) {}\n";
    assert_eq!(
        names(&format!("{class}let p: Person = {{ na| }};\n")),
        ["name", "age"]
    );
    assert_eq!(
        names(&format!("{class}let p: Person = {{ name: \"a\", | }};\n")),
        ["age"]
    );
    let call = format!("{class}let greet(p: Person): String {{ p.name }}\nlet s = greet({{ |");
    assert_eq!(names(&call), ["name", "age"]);
    // A value's place, not a key's: names in scope.
    let value = names(&format!("{class}let p: Person = {{ name: | }};\n"));
    assert!(value.contains(&"console".to_string()), "{value:?}");
}

const BUILTINS: &str = r#"
let names: ListBuilder<String> = new ListBuilder<String>();
names.add("a");
let frozen: List<String> = names.toList();
let empty = frozen.isEmpty;
let count = frozen.length;
let blank = "".isEmpty;
let letter = String.fromCodePoint(97);
ignore(count);
console.log("hi");
if (blank) { panic(); }
"#;

/// Where core declares a builtin at the `nth` `needle` in [`BUILTINS`],
/// `ahead` bytes in, as the line in core and the name there.
fn core_at(a: &Analysis, needle: &str, nth: usize, ahead: u32) -> Option<(String, String)> {
    let d = a.core_definition(MAIN, at(BUILTINS, needle, nth, ahead))?;
    assert_eq!(d.path, "core.temper");
    let name = &d.source[d.span.start as usize..d.span.end as usize];
    let line_start = d.source[..d.span.start as usize]
        .rfind('\n')
        .map_or(0, |i| i + 1);
    let line_end = d.source[line_start..]
        .find('\n')
        .map_or(d.source.len(), |i| line_start + i);
    Some((
        d.source[line_start..line_end].trim().to_string(),
        name.to_string(),
    ))
}

#[test]
fn definition_into_core() {
    let a = analyze(BUILTINS);
    let found = |needle, nth, ahead| core_at(&a, needle, nth, ahead).unwrap();
    // A builtin type.
    let (line, name) = found("ListBuilder<String>", 0, 1);
    assert_eq!(name, "ListBuilder");
    assert!(line.starts_with("export class ListBuilder<T>"), "{line}");
    // A method, through a generic receiver.
    let (line, name) = found("add(\"a\")", 0, 0);
    assert_eq!(name, "add");
    assert!(line.starts_with("public add(newValue: T"), "{line}");
    // A getter on List<String>, its own, and one its supertype declares.
    let (line, _) = found("length;", 0, 0);
    assert_eq!(line, "public get length(): builtins.Int;");
    let isempty_list = core_at(&a, "isEmpty;", 0, 0).unwrap().0;
    assert_eq!(
        isempty_list,
        "public get isEmpty(): builtins.Boolean { length == 0 }"
    );
    // String's own, and a static after the type's name.
    let (line, _) = found("isEmpty;", 1, 0);
    assert!(
        line.starts_with("public get isEmpty(): builtins.Boolean { content"),
        "{line}"
    );
    let (line, _) = found("fromCodePoint", 0, 0);
    assert!(line.starts_with("public static fromCodePoint("), "{line}");
    // Builtin functions and values, and a member of `console`.
    let (line, _) = found("ignore(", 0, 0);
    assert!(
        line.starts_with("export let ignore(value: AnyValue)"),
        "{line}"
    );
    let (line, _) = found("log(", 0, 0);
    assert_eq!(line, "public log(message: String): Void;");
    let (line, _) = found("console.", 0, 0);
    assert!(line.starts_with("export let console: Console"), "{line}");
    // Nothing for what core doesn't declare, or what's not a builtin.
    assert_eq!(core_at(&a, "panic", 0, 0), None);
    assert_eq!(core_at(&a, "count);", 0, 0), None);
    assert_eq!(a.definition(MAIN, at(BUILTINS, "add(\"a\")", 0, 0)), None);
}

#[test]
fn hover_core_docs() {
    let a = analyze(BUILTINS);
    let doc = |needle, nth, ahead| a.hover(MAIN, at(BUILTINS, needle, nth, ahead)).unwrap().doc;
    let length = doc("length;", 0, 0).unwrap();
    assert!(length.starts_with("The *length* of a listed"), "{length}");
    assert_eq!(
        doc("add(\"a\")", 0, 0).as_deref(),
        Some("Panics for `at` less than 0 or greater than `length`.")
    );
    let list = doc("List<String>", 0, 0).unwrap();
    assert!(list.contains("immutable"), "{list}");
    assert_eq!(
        doc("log(", 0, 0).as_deref(),
        Some("Log a message at default informational level to this console.")
    );
    assert!(doc("ignore(", 0, 0).is_some());
    assert!(doc("fromCodePoint", 0, 0).is_some());
    // Doc lines lose their leading `*`, as user declarations' do.
    let console = doc("console.", 0, 0).unwrap();
    assert!(
        console.starts_with("The global default `Console` instance."),
        "{console}"
    );
    assert!(!console.contains("\n*"), "{console}");
    // Core's docs-site directives go: included snippets, and links to
    // them keep their text.
    let typed = analyze(TYPED);
    let string = typed
        .hover(MAIN, at(TYPED, "String, box", 0, 0))
        .unwrap()
        .doc;
    let string = string.unwrap();
    assert!(
        string.starts_with("A *String* is a chunk of textual content.\n\nTo"),
        "{string}"
    );
    assert!(
        !string.contains('⎀') && !string.contains("snippet/"),
        "{string}"
    );
    assert!(
        string.contains("documentation for string indices to see"),
        "{string}"
    );
    assert!(!string.contains("\n\n\n"), "{string}");
    let int = typed
        .hover(MAIN, at(TYPED, "Int { 1", 0, 0))
        .unwrap()
        .doc
        .unwrap();
    assert!(!int.contains('⎀') && !int.ends_with('\n'), "{int}");
}

const TYPED: &str = r#"
class Box<T> {
  public value: T;
  public put(item: T): Void { value = item; }
  public get size(): Int { 1 }
}
let greet(name: String, box: Box<String>): String { name }
let each(f: fn (Int): Void): Void { f(1) }
each { (n: Int): Void => console.log(n.toString()) }
let keyed(m: Map<String, Int>): Boolean { m.isEmpty }
"#;

/// Hover's headline at the `nth` `needle` in [`TYPED`], `ahead` bytes in.
fn typed_headline(a: &Analysis, needle: &str, nth: usize, ahead: u32) -> String {
    let h = a.hover(MAIN, at(TYPED, needle, nth, ahead));
    h.map(|h| h.headline()).unwrap_or_default()
}

#[test]
fn hover_on_types_in_signatures() {
    let a = analyze(TYPED);
    let shown = |needle, nth, ahead| typed_headline(&a, needle, nth, ahead);
    // Parameters' and results' types, builtin or declared.
    assert_eq!(shown("String, box", 0, 0), "builtin String");
    assert_eq!(shown("String { name", 0, 2), "builtin String");
    assert_eq!(shown("Box<String>)", 0, 1), "class Box<T>");
    assert_eq!(shown("T): Void", 0, 0), "type parameter T");
    assert_eq!(shown("Void { value", 0, 0), "builtin Void");
    // An alias shows what it aliases, and its doc.
    assert_eq!(shown("Int { 1", 0, 0), "builtin Int: Int32");
    let int = a.hover(MAIN, at(TYPED, "Int { 1", 0, 0)).unwrap();
    assert!(int.doc.is_some_and(|d| d.contains("*Int32*")));
    // A function type's, and a block's parameters and result.
    assert_eq!(shown("Int): Void):", 0, 0), "builtin Int: Int32");
    assert_eq!(shown("Void): Void", 0, 7), "builtin Void");
    assert_eq!(shown("Int): Void =>", 0, 0), "builtin Int: Int32");
    assert_eq!(shown("Void =>", 0, 0), "builtin Void");
    // A generic builtin, with core's type parameters.
    assert_eq!(shown("Map<", 0, 0), "builtin Map<K, V>");
    assert_eq!(shown("Boolean", 0, 0), "builtin Boolean");
}

#[test]
fn definitions_and_references_from_signatures() {
    let a = analyze(TYPED);
    let d = a.definition(MAIN, at(TYPED, "Box<String>)", 0, 0)).unwrap();
    assert_eq!(d.span.start, at(TYPED, "Box<T>", 0, 0));
    let d = a.definition(MAIN, at(TYPED, "T): Void", 0, 0)).unwrap();
    assert_eq!(d.span.start, at(TYPED, "T>", 0, 0));
    let core = a
        .core_definition(MAIN, at(TYPED, "String { name", 0, 2))
        .unwrap();
    assert_eq!(&core.source[core.span.start as usize..][..6], "String");
    // `T`'s uses: the field's type and the parameter's.
    let uses = a.references(MAIN, at(TYPED, "T>", 0, 0), false);
    let starts: Vec<u32> = uses.iter().map(|l| l.span.start).collect();
    assert_eq!(starts, [at(TYPED, "T;", 0, 0), at(TYPED, "T): Void", 0, 0)]);
}
