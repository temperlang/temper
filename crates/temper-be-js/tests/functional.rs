//! The JS backend on Kotlin Temper's functional tests that tmpl's
//! translation covers so far, in `temper-funtests`'s
//! `cases/`, with the class and interface tests
//! (`temper_be_testkit::CLASSES`), and on the shared programs
//! (`crates/temper-be-testkit/programs/`), which cover operators,
//! jumps, module state, classes, and nullables beyond those tests, with
//! the nullable and `orelse` tests (`temper_be_testkit::NULLABLES`) and
//! the generics tests (`temper_be_testkit::GENERICS`), the function
//! values, defaults, and core's other types tests
//! (`temper_be_testkit::FUNCTIONS`, `DEFAULTS`, and `CORE_TYPES`), the
//! tests of a library's own connected functions and classes, with host
//! code in `_connected.js` (`temper_be_testkit::CONNECTED`, each as is and
//! under `--no-connected`, which tsc checks with the host code), those
//! of divisions that can't fail and maps (`temper_be_testkit::DIVISIONS`),
//! of labeled loops' jumps and classes declared in functions
//! (`temper_be_testkit::LOOPS_AND_CLASSES`), and of generators, `async`,
//! and `await`, as `function*`s (`temper_be_testkit::ASYNC`),
//! and this crate's own programs (`tests/programs/`): `hoisting`, for what JS
//! writes as statements before the expression they're in.
//!
//! - `goldens`: each program's generated package, laid out as Kotlin's,
//!   with its root module's `.internal.js` matching the golden in
//!   `tests/goldens/`, named for the test's path (`algos-fibonacci.js`),
//!   and its other files, if it has more modules or tests, theirs, named
//!   for the path and the file (`modules.helpers.deep.internal.js`,
//!   `testing.test.testing.js`, `modules.package.json`). To accept new or
//!   changed output, run with `UPDATE_SNAPSHOTS=1` and review the diff.
//! - `source_maps`: each program's `.internal.js.map`, decoded, matching
//!   the golden `tests/goldens/NAME.map.txt`, a line per mapping with the
//!   generated and source text there, updated as `goldens` is; and each
//!   mapping lands where it should (a `while` on a `while`, a function's
//!   or member's name on its source name, and code the compiler made, on
//!   what it's made for).
//! - `runs`: each generated package runs under Node (`node`, or `NODE`),
//!   against `@temperlang/core`
//!   (`crates/temper-be-js/runtime/temper-core`), and logs what the test expects
//!   of Kotlin's `js` backend; and TypeScript's checker (`tsc`, or `TSC`)
//!   finds no problems in it with `--checkJs --strict`, against
//!   declarations it generates from the runtime. Node's own source map
//!   support (`SourceMap` from `node:module`) reads each map to the same
//!   positions, in source files that exist; and with
//!   `--enable-source-maps`, an error thrown in generated code has a stack
//!   trace that points into its Temper source. A package that imports
//!   another (`imports-library`) finds it in `node_modules`, as it
//!   finds std's package, `@temperlang/std`. `node --check` parses tests
//!   files, and a test that runs as tests (`testing/asserts`) runs with
//!   Node's test runner (`node --enable-source-maps --test`, as `temper test -b js` runs it,
//!   with its JUnit reporter, where Kotlin's runs Mocha), its failures
//!   compared with its `expectedFailures`.
//!   Skipped, with a message,
//!   without Node, and its type check without `tsc`; with
//!   `TEMPER_REQUIRE_CHECKERS` set, as in CI, either missing fails it
//!   instead.
//! - `missing_host_code`: a connected class whose `_connected.js` is
//!   missing is `NotYet`, naming the file.

use std::path::{Path, PathBuf};
use std::process::Command;
use std::{env, fs};
use temper_be_js::{JS, OutFile, translate, translate_with_source_maps};
use temper_be_testkit::{self as kit, Case, Goldens};
use temper_tmpl::source_map::{Segment, decode_mappings};

fn cases() -> Vec<Case> {
    let tests = [
        &kit::CLASSES[..],
        &kit::NULLABLES[..],
        &kit::GENERICS[..],
        &kit::FUNCTIONS[..],
        &kit::DEFAULTS[..],
        &kit::CORE_TYPES[..],
        &kit::CONNECTED[..],
        &kit::DIVISIONS[..],
        &kit::LOOPS_AND_CLASSES[..],
        &kit::TYPING[..],
        &kit::ANY_VALUES[..],
        &kit::ERRORS[..],
        &kit::TYPE_VALUES[..],
        &kit::ASYNC[..],
        &kit::TESTS[..],
        &kit::WITH_STD[..],
        &kit::NET[..],
    ]
    .concat();
    let mut cases = kit::cases_with("js", &kit::PROGRAMS, &tests);
    // This crate's own programs, in `tests/programs/`.
    let dir = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/programs");
    // A list, so more may follow.
    #[allow(clippy::single_element_loop)]
    for name in ["hoisting"] {
        let path = dir.join(format!("{name}.temper.md"));
        let text = fs::read_to_string(&path).unwrap();
        cases.push(Case {
            key: name.into(),
            library: name.into(),
            expected: log_block(&text),
            sources: vec![(path.to_string_lossy().into_owned(), text)],
            dir: dir.clone(),
            libraries: Vec::new(),
            host_files: Vec::new(),
            no_connected: false,
            tests: None,
            absent: Vec::new(),
            fails: false,
            stderr: Vec::new(),
            stderr_absent: Vec::new(),
        });
    }
    cases
}

/// The lines of a program's one log block.
fn log_block(text: &str) -> Vec<String> {
    (text.lines())
        .skip_while(|l| !l.starts_with("```log"))
        .skip(1)
        .take_while(|l| !l.starts_with("```"))
        .map(str::to_string)
        .collect()
}

/// A case's generated files.
fn generate(case: &Case) -> Vec<OutFile> {
    translate(&kit::program_for(case, &JS.rules), &JS)
        .unwrap_or_else(|e| panic!("{}: {e}", case.key))
}

#[test]
fn goldens() {
    kit::with_stack(|| {
        let mut goldens = Goldens::new(env!("CARGO_MANIFEST_DIR"));
        for case in cases() {
            let files = generate(&case);
            let library = &case.library;
            let file = library.replace('-', "_");
            let paths: Vec<&str> = files.iter().map(|f| f.path.as_str()).collect();
            let root = format!("{library}/{file}.internal.js");
            assert_eq!(
                paths[..2],
                [
                    format!("{library}/package.json"),
                    format!("{library}/index.js"),
                ],
                "{}",
                case.key
            );
            assert!(paths.contains(&root.as_str()), "{}", case.key);
            assert!(files[0].text.contains("\"type\": \"module\""));
            let core = temper_tmpl::version::current();
            assert!(
                files[0]
                    .text
                    .contains(&format!("\"@temperlang/core\": \"{core}\""))
            );
            let single = files.len() == 4;
            if single {
                assert_eq!(files[1].text, format!("export * from \"./{file}.js\";\n"));
            } else {
                assert!(
                    files[1]
                        .text
                        .ends_with(&format!("export * from \"./{file}.js\";\n"))
                );
            }
            for f in &files {
                let rest = &f.path[library.len() + 1..];
                // Host code, copied as it is.
                let host = case.host_files.iter().any(|(path, _)| path == rest);
                let public = !host
                    && rest.ends_with(".js")
                    && !rest.ends_with(".internal.js")
                    && !rest.starts_with("test/")
                    && rest != "index.js";
                if public {
                    // A public module only re-exports, as checked here.
                    let leaf = rest
                        .rsplit('/')
                        .next()
                        .unwrap()
                        .strip_suffix(".js")
                        .unwrap();
                    let from = format!(" from \"./{leaf}.internal.js\";\n");
                    assert!(f.text.ends_with(&from), "{}", f.text);
                    continue;
                }
                let index = format!("export * from \"./{file}.js\";\n");
                let name = if f.path == root {
                    format!("{}.js", case.key)
                } else if single || rest == "index.js" && f.text == index {
                    continue;
                } else {
                    format!("{}.{}", case.key, rest.replace('/', "."))
                };
                goldens.check(&name, &f.text);
            }
        }
        goldens.finish();
    });
}

/// The shared `reach` program's test parts only in its tests file, and
/// what nothing reaches nowhere, as tmpl's `reach::partition` splits the
/// program; `runs` runs its test, which passes only if they're set.
#[test]
fn test_parts_apart() {
    kit::with_stack(|| {
        let case = (cases().into_iter()).find(|c| c.key == "reach").unwrap();
        let files = generate(&case);
        let lines = (files.iter().filter(|f| f.path.ends_with(".js"))).flat_map(|f| {
            let tests = f.path.contains("/test/");
            (f.text.lines()).map(move |line| (f.path.as_str(), tests, line))
        });
        kit::check_test_parts_apart(lines, &kit::REACH_TESTS_ONLY, &kit::REACH_UNREACHED);
    });
}

/// Host code that's needed but missing fails, naming the file it'd be in.
#[test]
fn missing_host_code() {
    kit::with_stack(|| {
        let mut case = (cases().into_iter())
            .find(|c| c.key == "types-connected")
            .unwrap();
        case.host_files.retain(|(path, _)| path != "_connected.js");
        let e = translate(&kit::program_for(&case, &JS.rules), &JS).unwrap_err();
        assert!(e.what.contains("with no _connected.js"), "{e}");
    });
}

/// A source map's fields, read from the one line of JSON it's written as.
struct Map {
    sources: Vec<String>,
    names: Vec<String>,
    lines: Vec<Vec<Segment>>,
}

/// A JSON string's content from `at` on, without escapes but `\"` and
/// `\\`, and the rest after its closing quote.
fn json_string(text: &str) -> (String, &str) {
    let mut out = String::new();
    let mut chars = text.char_indices();
    while let Some((i, c)) = chars.next() {
        match c {
            '"' => return (out, &text[i + 1..]),
            '\\' => out.push(chars.next().unwrap().1),
            c => out.push(c),
        }
    }
    panic!("unterminated string in {text}")
}

fn read_map(json: &str) -> Map {
    let field = |key: &str| {
        let at = json.find(&format!("\"{key}\":")).unwrap() + key.len() + 3;
        &json[at..]
    };
    let list = |key: &str| {
        let mut rest = field(key).strip_prefix('[').unwrap();
        let mut items = Vec::new();
        while let Some(item) = rest.strip_prefix('"') {
            let (s, after) = json_string(item);
            items.push(s);
            rest = after.strip_prefix(',').unwrap_or(after);
        }
        assert!(rest.starts_with(']'), "{json}");
        items
    };
    let (mappings, _) = json_string(field("mappings").strip_prefix('"').unwrap());
    Map {
        sources: list("sources"),
        names: list("names"),
        lines: decode_mappings(&mappings).unwrap(),
    }
}

/// A line's text from a column counted in UTF-16 units.
fn from_column(line: &str, column: u32) -> &str {
    let mut units = 0;
    for (i, c) in line.char_indices() {
        if units >= column {
            return &line[i..];
        }
        units += c.len_utf16() as u32;
    }
    ""
}

/// A case's source that a map names, by its path from the library's
/// root after any `../`s: its main file, or another module's, read.
fn source_text(case: &Case, source: &str) -> String {
    let mut rest = source;
    while let Some(r) = rest.strip_prefix("../") {
        rest = r;
    }
    let main = (case.sources.iter()).find(|(path, _)| Path::new(path) == case.dir.join(rest));
    match main {
        Some((_, text)) => text.clone(),
        None => fs::read_to_string(case.dir.join(rest))
            .unwrap_or_else(|_| panic!("{}: no source {source}", case.key)),
    }
}

/// What Temper source a generated statement starting this way starts
/// with: one of these.
fn expected_source(generated: &str) -> Option<&'static [&'static str]> {
    const STARTS: &[(&str, &[&str])] = &[
        ("/**", &["let ", "var ", "fn", "{", "for"]),
        // A function literal declared before the statement it's in.
        ("function ", &["let ", "fn", "{"]),
        // `for … of`'s variable.
        ("const ", &["let ", "var ", "for"]),
        ("let ", &["let ", "var "]),
        ("while (", &["while", "for"]),
        ("do ", &["do"]),
        ("if (", &["if"]),
        ("console.log(", &["console.log("]),
        ("break", &["break"]),
        ("continue", &["continue"]),
    ];
    STARTS
        .iter()
        .find(|(js, _)| generated.starts_with(js))
        .map(|&(_, temper)| temper)
}

/// Whether source text is what the compiler made code from, which maps
/// there whatever it is: an `@auto("==")` class's `eq` and its body, or
/// what `@json` adds.
fn synthesized(temper: &str) -> bool {
    temper.starts_with('@') || temper.starts_with("auto(")
}

/// Whether a generated statement is a compiler-made local's declaration
/// (`const o = a;`, for `a.n += 1`), or code lowered from `||` or `&&`,
/// which map to the source they're made for.
fn compiler_local(js: &str, temper: &str) -> bool {
    let declared = ["const ", "let "]
        .iter()
        .find_map(|k| js.strip_prefix(k))
        .and_then(|rest| rest.split([' ', ';']).next());
    let word = |name: &str| {
        temper
            .split(|c: char| !c.is_alphanumeric() && c != '_')
            .any(|w| w == name)
    };
    match declared {
        Some(name) => !temper.starts_with("let ") && !temper.starts_with("var ") && !word(name),
        None => js.starts_with("if (") && (temper.contains("||") || temper.contains("&&")),
    }
}

/// A map's mappings, a line each: generated line and column, source,
/// line and column (all from 1), name if any, and the text at both ends;
/// and where a mapping doesn't land where it should.
fn describe<'a>(case: &'a Case, module: &'a str, map: &Map) -> (String, Vec<String>) {
    let mut out =
        String::from("# generated line:column -> source:line:column, and the text at each\n");
    let mut problems = Vec::new();
    let generated: Vec<&str> = module.lines().collect();
    for (i, segments) in map.lines.iter().enumerate() {
        for s in segments {
            let source = &map.sources[s.source as usize];
            let text = source_text(case, source);
            let source_line = text.lines().nth(s.source_line as usize).unwrap_or("");
            let (js, temper) = (
                from_column(generated[i], s.column),
                from_column(source_line, s.source_column).to_string(),
            );
            let snippet = |t: &str| {
                t.chars()
                    .take(28)
                    .collect::<String>()
                    .trim_end()
                    .to_string()
            };
            let name = s.name.map(|n| map.names[n as usize].as_str());
            out.push_str(&format!(
                "{}:{} -> {source}:{}:{}{}  {} <- {}\n",
                i + 1,
                s.column + 1,
                s.source_line + 1,
                s.source_column + 1,
                name.map(|n| format!(" ({n})")).unwrap_or_default(),
                snippet(js),
                snippet(&temper)
            ));
            let lands = match name {
                // Or a declaration a decorator makes, as `@json`'s.
                Some(name) => temper.starts_with(name) || synthesized(&temper),
                None => {
                    // Exported or not, the same.
                    fn unexported(t: &str) -> &str {
                        t.strip_prefix("export ").unwrap_or(t)
                    }
                    let temper = unexported(&temper);
                    // What a JSDoc comment is before: a declaration, or a
                    // class's member, which its name's mapping checks.
                    let documented = (generated[i + 1..].iter())
                        .map(|l| l.trim_start())
                        .find(|l| !l.starts_with('*'))
                        .map(unexported)
                        .unwrap_or("");
                    let js = unexported(js);
                    let member = js.starts_with("/**")
                        && !["let ", "const ", "function ", "class "]
                            .iter()
                            .any(|d| documented.starts_with(d));
                    // A generic class's `@template`s, before it.
                    let class_doc = js.starts_with("/**")
                        && documented.starts_with("class ")
                        && ["class ", "interface ", "sealed interface "]
                            .iter()
                            .any(|k| temper.starts_with(k));
                    // The `is` check of the `eq` a class below an
                    // `@auto("==")` interface gets, at the class.
                    let auto_eq = (temper.split(['(', ' ', '<']).next()).is_some_and(|c| {
                        !c.is_empty() && js.starts_with(&format!("if (other instanceof {c})"))
                    });
                    // A compiler-made local's type.
                    let local_doc = js.starts_with("/**") && compiler_local(documented, temper);
                    // A `when`'s arm, `??`, `?.`, an `if` value, an
                    // `else if` value with a branch that jumps (spanning
                    // from its condition), or an `orelse` whose fallback
                    // jumps, each of which may become an `if` statement,
                    // maps to its value or the statement it's in.
                    let arm = js.starts_with("if (")
                        && (source_line.contains("->")
                            || temper.contains("??")
                            || temper.contains("?.")
                            || temper.contains("if (")
                            || ["return", "break", "continue"]
                                .iter()
                                .any(|j| temper.contains(j))
                            || temper.contains("orelse"));
                    // A parameter's default, at the parameter.
                    let default = (js.split_once(" ??= ").map(|(n, _)| n))
                        .or_else(|| js.strip_prefix("if (")?.strip_suffix(" == null) {"))
                        .is_some_and(|name| temper.contains(&format!("{name}:")));
                    // Core's `forEach`, or a `for … of` loop, inlined as
                    // a loop, its index, and its item.
                    let for_each = ["while (", "const ", "let "]
                        .iter()
                        .any(|k| js.starts_with(k))
                        && (temper.contains(".forEach") || temper.starts_with("for ("));
                    member
                        || class_doc
                        || auto_eq
                        || local_doc
                        || arm
                        || default
                        || for_each
                        || synthesized(temper)
                        || compiler_local(js, temper)
                        || expected_source(js)
                            .is_none_or(|starts| starts.iter().any(|t| temper.starts_with(t)))
                }
            };
            if !lands {
                problems.push(format!(
                    "{}: {}:{} `{}` maps to `{}`",
                    case.key,
                    i + 1,
                    s.column + 1,
                    snippet(js),
                    snippet(&temper)
                ));
            }
        }
    }
    (out, problems)
}

#[test]
fn source_maps() {
    kit::with_stack(|| {
        let mut goldens = Goldens::new(env!("CARGO_MANIFEST_DIR"));
        let mut problems = Vec::new();
        for case in cases() {
            let program = kit::program_for(&case, &JS.rules);
            let plain = translate(&program, &JS).unwrap();
            let files = translate_with_source_maps(&program, &JS, "../").unwrap();
            let name = format!("{}.map.txt", case.key);
            if !program.has_spans {
                // Read from tmpl's text without spans: no map.
                assert_eq!(files, plain, "{}", case.key);
                let golden = goldens.path(&name);
                assert!(!golden.exists(), "{}", golden.display());
                continue;
            }
            // The same files, each module with a comment naming its map,
            // and the map after it.
            let unmapped: Vec<&OutFile> =
                files.iter().filter(|f| !f.path.ends_with(".map")).collect();
            assert_eq!(unmapped.len(), plain.len(), "{}", case.key);
            let mut blocks = Vec::new();
            for (f, before) in unmapped.iter().zip(&plain) {
                assert_eq!(f.path, before.path, "{}", case.key);
                let map_path = format!("{}.map", f.path);
                let Some(map_file) = files.iter().find(|m| m.path == map_path) else {
                    assert_eq!(f.text, before.text, "{}", case.key);
                    continue;
                };
                let internal = f.path.rsplit('/').next().unwrap();
                assert_eq!(
                    f.text,
                    format!("{}//# sourceMappingURL={internal}.map\n", before.text)
                );
                let json = &map_file.text;
                assert!(
                    json.starts_with(&format!("{{\"version\":3,\"file\":\"{internal}\",")),
                    "{json}"
                );
                let map = read_map(json);
                assert!(map.sources.iter().all(|s| s.starts_with("../")), "{json}");
                let (text, wrong) = describe(&case, &f.text, &map);
                problems.extend(wrong);
                blocks.push((f.path.clone(), text));
            }
            // Several maps, each after its module's path.
            let description = match &blocks[..] {
                [(_, one)] => one.clone(),
                _ => (blocks.iter())
                    .map(|(path, text)| ["# ", path, "\n", text].concat())
                    .collect(),
            };
            goldens.check(&name, &description);
        }
        kit::assert_none(&problems);
        goldens.finish();
    });
}

fn runtime() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime/temper-core")
}

/// The relative path from one directory to another, both absolute, with
/// `/` after each part, as a source map's `sources` prefix.
fn relative(from: &Path, to: &Path) -> String {
    let normal = |p: &Path| {
        let mut parts: Vec<std::ffi::OsString> = Vec::new();
        for c in p.components() {
            match c {
                std::path::Component::ParentDir => {
                    parts.pop();
                }
                std::path::Component::CurDir => {}
                c => parts.push(c.as_os_str().to_owned()),
            }
        }
        parts
    };
    let (from, to) = (normal(from), normal(to));
    let common = from.iter().zip(&to).take_while(|(a, b)| a == b).count();
    let mut out = "../".repeat(from.len() - common);
    for part in &to[common..] {
        out.push_str(&part.to_string_lossy());
        out.push('/');
    }
    out
}

/// Has Node load a generated module or tests file with
/// `--enable-source-maps`, then look up each of its map's mappings, and
/// a column after each, with its own source map support; and says where
/// Node reads them differently, or finds a source that isn't there.
fn node_reads_map(node: &str, case: &Case, module: &Path, map: &Map) -> Vec<String> {
    const LOOKUP: &str = r#"
import { findSourceMap } from "node:module";
import { pathToFileURL } from "node:url";
const [file, ...queries] = process.argv.slice(1);
const url = pathToFileURL(file).href;
// A module whose run fails (`semantics/broken`) still has its map.
try { await import(url); } catch {}
const map = findSourceMap(url);
for (const query of queries) {
  const [line, column] = query.split(":").map(Number);
  const e = map.findEntry(line, column);
  console.log(["@@", query, e.originalSource, e.originalLine, e.originalColumn, e.name ?? "-"].join("\t"));
}
"#;
    let mut queries = Vec::new();
    for (i, segments) in map.lines.iter().enumerate() {
        for s in segments {
            for column in [s.column, s.column + 1] {
                let next = segments.iter().any(|t| t.column == column && t != s);
                if !next {
                    queries.push((format!("{i}:{column}"), s));
                }
            }
        }
    }
    let run = Command::new(node)
        .args(["--enable-source-maps", "--input-type=module", "-e", LOOKUP])
        .arg(module)
        .args(queries.iter().map(|(q, _)| q))
        .current_dir(module.parent().unwrap())
        .output()
        .unwrap();
    let stdout = String::from_utf8_lossy(&run.stdout);
    let answers: Vec<Vec<&str>> = (stdout.lines())
        .filter_map(|l| l.strip_prefix("@@\t"))
        .map(|l| l.split('\t').collect())
        .collect();
    // A tests file's tests run as it loads, and some are meant to fail
    // (`run_tests` checks them), failing the process, as a program whose
    // run fails does after the lookups, where its async work fails.
    let tests = module.to_string_lossy().ends_with(".test.js");
    if (!run.status.success() && !tests && !case.fails) || answers.len() != queries.len() {
        return vec![format!(
            "{}: Node's source map lookup failed:\n{stdout}{}",
            case.key,
            String::from_utf8_lossy(&run.stderr)
        )];
    }
    let mut failures = Vec::new();
    let dir = module.parent().unwrap();
    for ((query, s), answer) in queries.iter().zip(&answers) {
        let source = &map.sources[s.source as usize];
        let name = s.name.map_or("-", |n| map.names[n as usize].as_str());
        let expected = [
            s.source_line.to_string(),
            s.source_column.to_string(),
            name.into(),
        ];
        // Node's source, a URL resolved against the map's, is the file
        // the map names relative to the module.
        let same = match (fs::canonicalize(file_url_path(answer[1]))).ok() {
            Some(read) => fs::canonicalize(dir.join(source)).is_ok_and(|s| s == read),
            None => false,
        };
        if !same || answer[2..] != expected {
            failures.push(format!(
                "{}: at {query}, Node reads {answer:?}, not {source} {expected:?} (from {})",
                case.key,
                dir.display()
            ));
        }
    }
    failures
}

/// The path a `file:` URL names: `file:///D:/a%20b` is `D:/a b` on
/// Windows, and `file:///a%20b` is `/a b` elsewhere. Anything else is
/// taken as a path.
fn file_url_path(url: &str) -> PathBuf {
    let Some(path) = url.strip_prefix("file://") else {
        return PathBuf::from(url);
    };
    // A drive's path, `/D:/…`, without its first `/`.
    let path = match path.as_bytes() {
        [b'/', drive, b':', ..] if cfg!(windows) && drive.is_ascii_alphabetic() => &path[1..],
        _ => path,
    };
    let mut bytes = Vec::new();
    let mut rest = path.as_bytes();
    while let Some((&b, after)) = rest.split_first() {
        let hex = |i: usize| (after.get(i).and_then(|h| (*h as char).to_digit(16)));
        match (b, hex(0), hex(1)) {
            (b'%', Some(high), Some(low)) => {
                bytes.push((high * 16 + low) as u8);
                rest = &after[2..];
            }
            _ => {
                bytes.push(b);
                rest = after;
            }
        }
    }
    PathBuf::from(String::from_utf8_lossy(&bytes).into_owned())
}

/// An error thrown in generated code, from a call with a value Temper
/// doesn't allow, has a stack trace that points into its Temper source,
/// with Node's `--enable-source-maps`.
fn stack_trace(node: &str, out: &Path) -> Vec<String> {
    let dir = out.join("traces-src");
    fs::create_dir_all(&dir).unwrap();
    let text =
        "# Traces\n\n    export let twice(n: Int): Int {\n      let m = n * 2;\n      m\n    }\n";
    let path = dir.join("traces.temper.md");
    fs::write(&path, text).unwrap();
    let case = Case {
        dir: dir.clone(),
        ..Case::inline("traces", &path.to_string_lossy(), text, Vec::new())
    };
    let root = out.join("traces");
    let prefix = relative(&root.join("traces"), &dir);
    let files =
        translate_with_source_maps(&kit::program_for(&case, &JS.rules), &JS, &prefix).unwrap();
    kit::write_files(
        &root,
        files.iter().map(|f| (f.path.as_str(), f.text.as_str())),
    );
    let harness = root.join("harness.mjs");
    fs::write(
        &harness,
        "import { twice } from \"./traces/index.js\";\n\
         try { twice(Symbol()); } catch (e) { console.log(e.stack); }\n",
    )
    .unwrap();
    let run = Command::new(node)
        .arg("--enable-source-maps")
        .arg(&harness)
        .current_dir(out)
        .output()
        .unwrap();
    let stdout = String::from_utf8_lossy(&run.stdout);
    // `let m = n * 2;`, which throws converting the symbol to a number.
    let frame = stdout
        .lines()
        .find(|l| l.trim_start().starts_with("at twice "));
    // Node names the source by its path, with `\` on Windows.
    match frame.map(|f| f.replace('\\', "/")) {
        Some(frame) if frame.contains("/traces-src/traces.temper.md:4:7)") => Vec::new(),
        _ => vec![format!(
            "the stack trace doesn't point at traces.temper.md:4:7:\n{stdout}{}",
            String::from_utf8_lossy(&run.stderr)
        )],
    }
}

/// TypeScript's options for checking generated code, as a tsconfig's
/// `compilerOptions`: strict, as ES modules under Node's rules, with the
/// runtime's declarations taken as given (`skipLibCheck`), since they
/// don't all check themselves.
const TSC_CHECK: &str = r#"{
    "checkJs": true,
    "allowJs": true,
    "noEmit": true,
    "strict": true,
    "skipLibCheck": true,
    "module": "nodenext",
    "target": "es2022"
  }"#;

/// A tsconfig, `tsconfig.check.json` in `dir`, that checks `files` with
/// [`TSC_CHECK`]; tsc reads it with `-p`, since a command line naming
/// the files can be more than Windows takes (8K for `tsc.cmd`).
fn tsc_check_config(dir: &Path, files: &[PathBuf]) -> PathBuf {
    let quoted: Vec<String> = (files.iter())
        .map(|f| {
            let mut out = String::from("\"");
            for c in f.to_string_lossy().chars() {
                match c {
                    '"' | '\\' => {
                        out.push('\\');
                        out.push(c);
                    }
                    c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
                    c => out.push(c),
                }
            }
            out.push('"');
            out
        })
        .collect();
    let path = dir.join("tsconfig.check.json");
    let text = format!(
        "{{\n  \"compilerOptions\": {TSC_CHECK},\n  \"files\": [\n    {}\n  ]\n}}\n",
        quoted.join(",\n    ")
    );
    fs::write(&path, text).unwrap();
    path
}

/// `tests/host/interop.mjs`, host code that uses `async-turns` the
/// standard way, beside its package in `root`: it `await`s a generated
/// promise and loops over a generated generator with `for…of`, which a
/// failed step leaves done. Gives its path, for tsc to check.
fn host_interop(node: &str, root: &Path, logged: &[String], failures: &mut Vec<String>) -> PathBuf {
    let script = root.join("interop.mjs");
    let source = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/host/interop.mjs");
    fs::copy(source, &script).unwrap();
    let run = Command::new(node).arg(&script).output().unwrap();
    let expected: Vec<String> = (logged.iter().cloned())
        .chain(
            [
                "await 2",
                "for…of 0,1,2",
                "failed step",
                "then done true",
                "fibonacci 0,1,1,2,3,5,8,13",
                "collatz 7,22,11,34,17,52,26,13,40,20,10,5,16,8,4,2,1",
                "halve 4 caught",
            ]
            .map(String::from),
        )
        .collect();
    kit::check_log(failures, "host interop", &run, &expected);
    script
}

/// Runs a package's tests, as Kotlin's `RunJs` does with Mocha, with
/// Node's test runner, as `temper test -b js` runs it (`tools.rs`), its
/// failures, from its JUnit reporter, compared with its
/// `expectedFailures`; std's package, `@temperlang/std`, is beside it in
/// `node_modules`. `isolation` is the flag for one process
/// ([`temper_be_js::tools::isolation_flag`]).
fn run_tests(failures: &mut Vec<String>, case: &Case, package: &Path, node: &str, isolation: &str) {
    let results = package.join("test-results.xml");
    let run = Command::new(node)
        .args(["--enable-source-maps", "--test", isolation])
        .arg("--test-reporter=junit")
        .arg(format!("--test-reporter-destination={}", results.display()))
        .arg("test/**/*.test.js")
        .current_dir(package)
        .output()
        .unwrap();
    let actual = kit::junit_file_results(&results);
    kit::check_tests(failures, case, actual, &run, |n| n.to_string());
}

#[test]
fn runs() {
    let Some(node) = kit::tool("NODE", "node") else {
        kit::missing("no Node (on PATH, or named by NODE)");
        return;
    };
    // As `temper test -b js` picks it, from `node --version`, once.
    let version = Command::new(&node).arg("--version").output().unwrap();
    let isolation = temper_be_js::tools::isolation_flag(&String::from_utf8_lossy(&version.stdout))
        .unwrap_or_else(|e| panic!("{e}"));
    let tsc = kit::tool("TSC", "tsc");
    if tsc.is_none() {
        kit::missing("type check: no tsc (on PATH, or named by TSC)");
    }
    kit::with_stack(move || {
        let out = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-js");
        let _ = fs::remove_dir_all(&out);
        // The runtime, where Node and tsc look for `@temperlang/core`.
        let core = out.join("node_modules/@temperlang/core");
        kit::copy_dir(&runtime(), &core, &[]);
        let mut failures = Vec::new();
        let mut checked = Vec::new();
        // std's package, `@temperlang/std`, where Node finds packages, and
        // checked there, so tsc reads its JSDoc.
        let std = translate(&kit::std_program(), &JS).unwrap_or_else(|e| panic!("std: {e}"));
        let modules = out.join("node_modules/@temperlang");
        kit::write_files(
            &modules,
            std.iter().map(|f| (f.path.as_str(), f.text.as_str())),
        );
        checked.extend(
            (std.iter())
                .filter(|f| f.path.ends_with(".js"))
                .map(|f| modules.join(&f.path)),
        );
        for case in cases() {
            let root = out.join(&case.key);
            let package = root.join(&case.library);
            let prefix = relative(&package, &case.dir);
            let files =
                translate_with_source_maps(&kit::program_for(&case, &JS.rules), &JS, &prefix)
                    .unwrap_or_else(|e| panic!("{}: {e}", case.key));
            kit::write_files(
                &root,
                files.iter().map(|f| (f.path.as_str(), f.text.as_str())),
            );
            // The libraries it imports, where Node finds packages, and
            // checked there too, so tsc reads their JSDoc.
            for library in kit::libraries(&case, "js") {
                let files = generate(&library);
                let modules = out.join("node_modules");
                kit::write_files(
                    &modules,
                    files.iter().map(|f| (f.path.as_str(), f.text.as_str())),
                );
                checked.extend(
                    (files.iter())
                        .filter(|f| f.path.ends_with(".js") && !f.path.contains("/test/"))
                        .map(|f| modules.join(&f.path)),
                );
            }
            for map in files.iter().filter(|f| f.path.ends_with(".map")) {
                let module = root.join(map.path.strip_suffix(".map").unwrap());
                failures.extend(node_reads_map(&node, &case, &module, &read_map(&map.text)));
            }
            for test in
                (files.iter()).filter(|f| f.path.contains("/test/") && f.path.ends_with(".js"))
            {
                let check = Command::new(&node)
                    .arg("--check")
                    .arg(root.join(&test.path))
                    .output()
                    .unwrap();
                if !check.status.success() {
                    failures.push(format!(
                        "{}: node --check {}:\n{}",
                        case.key,
                        test.path,
                        String::from_utf8_lossy(&check.stderr)
                    ));
                }
            }
            if case.tests.is_some() {
                run_tests(&mut failures, &case, &package, &node, isolation);
                checked.extend(
                    (files.iter())
                        .filter(|f| f.path.ends_with(".js") && !f.path.contains("/test/"))
                        .map(|f| root.join(&f.path)),
                );
                continue;
            }
            let index = root.join(&case.library).join("index.js");
            let run = Command::new(&node)
                .arg(&index)
                .current_dir(&out)
                .output()
                .unwrap();
            kit::check_case(&mut failures, &case.key, &run, &case);
            // Ill-typed code runs as written, which tsc would reject.
            if kit::ill_typed(&case) {
                continue;
            }
            checked.extend(
                files
                    .iter()
                    .filter(|f| f.path.ends_with(".js") && !f.path.contains("/test/"))
                    .map(|f| root.join(&f.path)),
            );
            if case.key == "async-turns" {
                checked.push(host_interop(&node, &root, &case.expected, &mut failures));
            }
        }
        failures.extend(stack_trace(&node, &out));
        if let Some(tsc) = &tsc {
            // Declarations for the runtime, beside its modules, generated
            // from a copy outside `node_modules`, which tsc won't emit for.
            let source = out.join("temper-core");
            kit::copy_dir(&runtime(), &source, &[]);
            let emit = Command::new(tsc)
                .args(["--allowJs", "--declaration", "--emitDeclarationOnly"])
                .args(["--module", "nodenext", "--target", "es2022", "--outDir"])
                .arg(&core)
                .arg(source.join("index.js"))
                .current_dir(&out)
                .output()
                .unwrap();
            if !core.join("index.d.ts").exists() {
                failures.push(format!(
                    "tsc made no declarations for the runtime:\n{}",
                    String::from_utf8_lossy(&emit.stdout)
                ));
            } else {
                let check = Command::new(tsc)
                    .arg("-p")
                    .arg(tsc_check_config(&out, &checked))
                    .current_dir(&out)
                    .output()
                    .unwrap();
                if !check.status.success() {
                    failures.push(format!(
                        "tsc --checkJs --strict:\n{}{}",
                        String::from_utf8_lossy(&check.stdout),
                        String::from_utf8_lossy(&check.stderr)
                    ));
                }
            }
        }
        kit::assert_none(&failures);
    });
}

/// std's package is named `@temperlang/std`, as its importers name it
/// and as Kotlin's is, so Node finds it in a workspace's `node_modules`.
#[test]
fn std_package_name() {
    kit::with_stack(|| {
        let files = translate(&kit::std_program(), &JS).unwrap();
        let package = files.iter().find(|f| f.path == "std/package.json");
        let text = &package.expect("std/package.json").text;
        assert!(text.contains("\"name\": \"@temperlang/std\""), "{text}");
    });
}
