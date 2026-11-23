//! Core's Temper bodies against what runs. Typing reads
//! `lib/core/`'s signatures and editors show its docs, but the
//! interpreter implements core natively and every backend connects it,
//! so nothing ran core's bodies, which could drift from what runs.
//!
//! `bodies_agree_with_natives` settles core as a module beside a library
//! of cases, each a `test` calling a core member that has a Temper body on
//! shared inputs, edge cases among them, and runs it twice: as usual,
//! with the interpreter's natives, and with core's bodies in their place
//! (`interp::run_tests_with_core_bodies`). Each case logs its result,
//! `bubble` if it bubbles, or fails its test if it panics or runs out of
//! steps. Where the runs differ is listed in [`KNOWN`], so the test fails
//! on new drift and on a fix not yet noted; and every body runs in some
//! case, but those in [`NOT_RUN`]. `CORE_BODIES_SHOW=1` prints every
//! case's results and which bodies ran.
//!
//! `CORE_BODIES_LIBRARY=dir` also writes the cases as a library there,
//! each asserting the bodies' result, for `temper test -b …` in `dir`
//! to check a backend's connections against the bodies; `dir/cases.txt`
//! names its tests. A backend that can't yet translate a case stops the
//! whole library, so drop those cases first.
//!
//! The inventory of core's bodies, how each runs in the interpreter and
//! on backends, and what the runs found, is in `notes/core-bodies.md`.

use std::collections::BTreeMap;
use temper_frontend::interp::core_bodies::members;
use temper_frontend::interp::{Run, run_tests, run_tests_with_core_bodies};
use temper_frontend::library::{Level, Library};
use temper_frontend::project::{load_imports, source_kind};
use temper_frontend::settle::settle;
use temper_frontend::sources::Sources;
use temper_syntax::lex::SourceKind;
use temper_syntax::tree::NodeKind;

/// Steps each case may take: enough for every case by far, while a case
/// that loops forever ends quickly.
const FUEL: u64 = 1_000_000;

/// Helpers that show results as strings, built on bodiless members so
/// that both runs show them alike.
const HELPERS: &str = r#"
let showBool(b: Boolean): String { if (b) { "true" } else { "false" } }
let showIndex(s: String, i: StringIndexOption): String {
  if (i.compareTo(StringIndex.none) == 0) { return "none"; }
  let j = (i as StringIndex) orelse panic();
  "@${s.countBetween(String.begin, j).toString()}"
}
let showInts(xs: Listed<Int>): String {
  "[${xs.join(",") { (x: Int): String => x.toString() }}]"
}
let showStrs(xs: Listed<String>): String {
  "[${xs.join(",") { (x: String): String => "'${x}'" }}]"
}
let showPairs(xs: Listed<Pair<String, Int>>): String {
  "[${xs.join(",") { (p: Pair<String, Int>): String => "${p.key}:${p.value.toString()}" }}]"
}
let codePoints(s: String): String {
  let out = new ListBuilder<Int>();
  for (var i = String.begin; s.hasIndex(i); i = s.next(i)) { out.add(s[i]); }
  showInts(out)
}
let sampleMap(): Map<String, Int> { new Map([new Pair("a", 1), new Pair("b", 2)]) }
let sampleBuilder(): MapBuilder<String, Int> {
  let m = new MapBuilder<String, Int>();
  m["b"] = 2;
  m["a"] = 1;
  m
}
let safeCounter(n: Int): SafeGenerator<Int> {
  let make(body: fn (): SafeGenerator<Int>): SafeGenerator<Int> { body() }
  make { (): GeneratorResult<Int> extends GeneratorFn =>
    for (var i = 0; i < n; i += 1) { yield(i); }
  }
}
let showResult(r: GeneratorResult<Int>): String {
  when (r) {
    is ValueResult<Int> -> "value ${r.value.toString()}";
    else -> "done";
  }
}
"#;

/// Each case, a line: the member it calls, then Temper statements whose
/// last expression is the `String` result. `#` starts a comment line.
const CASES: &str = r#"
# String
String.isEmpty showBool("".isEmpty)
String.isEmpty showBool("a".isEmpty)
String.isEmpty showBool("😀".isEmpty)
String.indexOf let s = "abc"; showIndex(s, s.indexOf("b"))
String.indexOf let s = "abc"; showIndex(s, s.indexOf("bc"))
String.indexOf let s = "abc"; showIndex(s, s.indexOf("x"))
String.indexOf let s = "abc"; showIndex(s, s.indexOf(""))
String.indexOf let s = ""; showIndex(s, s.indexOf(""))
String.indexOf let s = ""; showIndex(s, s.indexOf("a"))
String.indexOf let s = "abc"; showIndex(s, s.indexOf("abcd"))
String.indexOf let s = "aaa"; showIndex(s, s.indexOf("aa"))
String.indexOf let s = "abcabc"; showIndex(s, s.indexOf("c", s.next(s.next(s.next(String.begin)))))
String.indexOf let s = "abc"; showIndex(s, s.indexOf("", s.end))
String.indexOf let s = "abc"; showIndex(s, s.indexOf("c", s.end))
String.indexOf let s = "a😀b😀"; showIndex(s, s.indexOf("😀", s.next(s.next(String.begin))))
String.indexOf let s = "été"; showIndex(s, s.indexOf("té"))
String.step let s = "abc"; showIndex(s, s.step(String.begin, 0))
String.step let s = "abc"; showIndex(s, s.step(String.begin, 2))
String.step let s = "abc"; showIndex(s, s.step(String.begin, 5))
String.step let s = "abc"; showIndex(s, s.step(s.end, -1))
String.step let s = "abc"; showIndex(s, s.step(s.end, -5))
String.step let s = "abc"; showIndex(s, s.step(String.begin, -1))
String.step let s = ""; showIndex(s, s.step(String.begin, 1))
String.step let s = "😀éx"; showIndex(s, s.step(String.begin, 2))
String.step let s = "😀éx"; showIndex(s, s.step(s.end, -2))
String.step let s = "ab"; showIndex(s, s.step(String.begin, 2147483647))
String.step let s = "ab"; showIndex(s, s.step(s.end, -2147483647 - 1))
String.forEach let out = new ListBuilder<Int>(); "".forEach { (c: Int): Void => out.add(c) }; showInts(out)
String.forEach let out = new ListBuilder<Int>(); "a😀é".forEach { (c: Int): Void => out.add(c) }; showInts(out)
String.forEach let out = new ListBuilder<Int>(); for (let c: Int of "ab") { out.add(c) }; showInts(out)
String.toString "éx".toString()
String.toString "".toString()
# StringBuilder
StringBuilder let b = new StringBuilder(); b.toString()
StringBuilder.append let b = new StringBuilder(); b.append("ab"); b.append(""); b.append("😀"); codePoints(b.toString())
StringBuilder.appendCodePoint let b = new StringBuilder(); b.appendCodePoint(0x1F600); b.appendCodePoint(0x41); codePoints(b.toString())
StringBuilder.appendCodePoint let b = new StringBuilder(); b.appendCodePoint(0x110000); b.toString()
StringBuilder.appendCodePoint let b = new StringBuilder(); b.appendCodePoint(-1); b.toString()
StringBuilder.appendCodePoint let b = new StringBuilder(); b.appendCodePoint(0xD800); b.toString()
StringBuilder.appendCodePoint let b = new StringBuilder(); b.appendCodePoint(0); codePoints(b.toString())
StringBuilder.appendBetween let s = "a😀cd"; let b = new StringBuilder(); b.appendBetween(s, s.next(String.begin), s.prev(s.end)); codePoints(b.toString())
StringBuilder.appendBetween let s = "abc"; let b = new StringBuilder(); b.appendBetween(s, String.begin, s.end); b.toString()
StringBuilder.appendBetween let s = "abc"; let b = new StringBuilder(); b.appendBetween(s, s.end, String.begin); b.toString()
StringBuilder.appendBetween let s = "abc"; let long = "abcdef"; let b = new StringBuilder(); b.appendBetween(s, s.next(String.begin), long.end); b.toString()
StringBuilder.appendBetween let s = "abc"; let b = new StringBuilder(); b.appendBetween(s, s.end, s.end); b.toString()
StringBuilder.clear let b = new StringBuilder(); b.append("ab"); b.clear(); b.append("c"); b.toString()
StringBuilder.end let b = new StringBuilder(); let e = b.end; b.append("x"); let s = b.toString(); showIndex(s, e)
StringBuilder.end let b = new StringBuilder(); b.append("a😀"); let e = b.end; b.append("z"); let s = b.toString(); "${s.slice(String.begin, e)}|${s.slice(e, s.end)}"
StringBuilder.end let b = new StringBuilder(); b.append("ab"); b.append("c"); let e = b.end; b.append("d"); let s = b.toString(); "${s.slice(String.begin, e)}|${s.slice(e, s.end)}"
# Listed, List, ListBuilder, and their extensions
Listed.isEmpty let xs: List<Int> = []; showBool(xs.isEmpty)
Listed.isEmpty showBool([1].isEmpty)
Listed.isEmpty showBool(new ListBuilder<Int>().isEmpty)
Listed.toListBuilder let xs = [1, 2]; let b = xs.toListBuilder(); b.add(3); "${showInts(xs)} ${showInts(b)}"
Listed.toListBuilder let xs = new ListBuilder<Int>(); xs.add(1); let b = xs.toListBuilder(); b.add(3); "${showInts(xs)} ${showInts(b)}"
Listed.reduce [1, 2, 3].reduce { (a: Int, b: Int): Int => a - b }.toString()
Listed.reduce [5].reduce { (a: Int, b: Int): Int => a - b }.toString()
Listed.reduce let xs: List<Int> = []; xs.reduce { (a: Int, b: Int): Int => a - b }.toString()
listedMap showInts([1, 2, 3].map { (x: Int): Int => x * 10 })
listedMap let xs: List<Int> = []; showInts(xs.map { (x: Int): Int => x * 10 })
listedMap let b = new ListBuilder<Int>(); b.add(1); b.add(2); b.add(3); showInts(b.map { (x: Int): Int => do { b.removeLast(); x } })
listedMap let b = new ListBuilder<Int>(); b.add(1); showInts(b.map { (x: Int): Int => do { if (b.length < 3) { b.add(x + 1) }; x } })
listedReduceFrom [1, 2, 3].reduceFrom("") { (a: String, x: Int): String => "${a}${x.toString()}" }
listedReduceFrom let xs: List<Int> = []; xs.reduceFrom("init") { (a: String, x: Int): String => "${a}${x.toString()}" }
listedReduceFrom let b = new ListBuilder<Int>(); b.add(1); b.add(2); b.reduceFrom("") { (a: String, x: Int): String => do { if (b.length < 4) { b.add(9) }; "${a}${x.toString()}" } }
listedReduceFromIndex [1, 2, 3].reduceFromIndex(0, 1) { (a: Int, x: Int): Int => a + x }.toString()
listedReduceFromIndex [1, 2, 3].reduceFromIndex(0, 3) { (a: Int, x: Int): Int => a + x }.toString()
listedReduceFromIndex [1, 2, 3].reduceFromIndex(0, 5) { (a: Int, x: Int): Int => a + x }.toString()
listedReduceFromIndex [1, 2, 3].reduceFromIndex(0, -1) { (a: Int, x: Int): Int => a + x }.toString()
listedReduceFromIndex [1, 2, 3].reduceFromIndex(10, 0) { (a: Int, x: Int): Int => a + x }.toString()
listedReduceFromIndex let xs: List<Int> = []; xs.reduceFromIndex(7, 0) { (a: Int, x: Int): Int => a + x }.toString()
listedReduceFromIndex let xs: List<Int> = []; xs.reduceFromIndex(7, -1) { (a: Int, x: Int): Int => a + x }.toString()
List.toList let xs = [1, 2]; showInts(xs.toList())
List.forEach let out = new ListBuilder<Int>(); [3, 1, 2].forEach { (x: Int): Void => out.add(x) }; showInts(out)
List.forEach let out = new ListBuilder<Int>(); let xs: List<Int> = []; xs.forEach { (x: Int): Void => out.add(x) }; showInts(out)
List.forEach let out = new ListBuilder<Int>(); for (let x: Int of [4, 5]) { out.add(x) }; showInts(out)
# Mapped, Map, MapBuilder, and their extensions
Mapped.length sampleMap().length.toString()
Mapped.length new Map<String, Int>([]).length.toString()
Mapped.length sampleBuilder().length.toString()
Mapped.get sampleMap()["b"].toString()
Mapped.get sampleMap()["z"].toString()
Mapped.get sampleBuilder().get("a").toString()
Mapped.get let m = sampleBuilder(); m.remove("a"); m["a"].toString()
Mapped.getOr sampleMap().getOr("a", -1).toString()
Mapped.getOr sampleMap().getOr("z", -1).toString()
Mapped.has showBool(sampleMap().has("a"))
Mapped.has showBool(sampleBuilder().has("z"))
Mapped.keys showStrs(sampleMap().keys())
Mapped.keys showStrs(sampleBuilder().keys())
Mapped.values showInts(sampleBuilder().values())
Mapped.toMap let b = sampleBuilder(); let m = b.toMap(); b["c"] = 3; showPairs(m.toList())
Mapped.toMapBuilder let m = sampleMap(); let b = m.toMapBuilder(); b["c"] = 3; "${showPairs(m.toList())} ${showPairs(b.toList())}"
Mapped.toList showPairs(sampleBuilder().toList())
Mapped.toListBuilder let b = sampleMap().toListBuilder(); b.add(new Pair("z", 0)); showPairs(b)
mappedToListWith showStrs(sampleMap().toListWith { (k: String, v: Int): String => "${k}${v.toString()}" })
mappedToListBuilderWith let b = sampleBuilder().toListBuilderWith { (k: String, v: Int): String => k }; b.add("z"); showStrs(b)
# Pair
Pair.constructor let p = new Pair("k", 7); "${p.key}=${p.value.toString()}"
# Boolean
Boolean.toString true.toString()
Boolean.toString false.toString()
# DenseBitVector
DenseBitVector let v = new DenseBitVector(4); "${showBool(v.get(0))} ${showBool(v.get(3))} ${showBool(v.get(4))} ${showBool(v.get(-1))}"
DenseBitVector let v = new DenseBitVector(-1); showBool(v.get(0))
DenseBitVector.set let v = new DenseBitVector(2); v.set(1, true); v.set(9, true); "${showBool(v.get(1))} ${showBool(v.get(8))} ${showBool(v.get(9))} ${showBool(v.get(10))}"
DenseBitVector.set let v = new DenseBitVector(0); v.set(5, false); showBool(v.get(5))
DenseBitVector.set let v = new DenseBitVector(0); v.set(3, true); v.set(3, false); showBool(v.get(3))
DenseBitVector.set let v = new DenseBitVector(2); v.set(-1, true); "set"
DenseBitVector.set let v = new DenseBitVector(2); v.set(-1, false); "set"
DenseBitVector.get let v = new DenseBitVector(2); v.set(1, true); showBool(v[1])
# Int32
Int32.min 3.min(-4).toString()
Int32.min (-2147483647 - 1).min(2147483647).toString()
Int32.max 3.max(-4).toString()
Int32.max 5.max(5).toString()
Int32.succ 41.succ().toString()
Int32.succ 2147483647.succ().toString()
Int32.pred (-2147483647 - 1).pred().toString()
Int32.pred 0.pred().toString()
Int32.pred 2147483647.pred().toString()
Int32.succ (-1).succ().toString()
Int32.succ (-2147483647 - 1).succ().toString()
Int32.signum [-2147483647 - 1, -7, -1, 0, 1, 9, 2147483647].map { (x: Int): String => x.signum.toString() }.join(",") { (x: String): String => x }
# Int64
Int64.min 3i64.min(-4i64).toString()
Int64.max (-9223372036854775807i64 - 1i64).max(9223372036854775807i64).toString()
Int64.succ 9223372036854775807i64.succ().toString()
Int64.pred (-9223372036854775807i64 - 1i64).pred().toString()
Int64.pred 0i64.pred().toString()
Int64.succ 41i64.succ().toString()
Int64.succ (-9223372036854775807i64 - 1i64).succ().toString()
Int64.pred 9223372036854775807i64.pred().toString()
# Float64
Float64.near showBool(1.0.near(1.0 + 1e-10))
Float64.near showBool(1.0.near(1.1))
Float64.near showBool(1.0.near(1.1, 0.2))
Float64.near showBool(0.0.near(1e-300))
Float64.near showBool(0.0.near(1e-300, 1e-9, 1e-12))
Float64.near showBool((0.0 / 1.0 - 0.0).near(-0.0))
Float64.near let inf = 1.0e308 * 10.0; showBool(inf.near(inf))
Float64.near let nan = (1.0e308 * 10.0) - (1.0e308 * 10.0); showBool(nan.near(nan))
Float64.near let nan = (1.0e308 * 10.0) - (1.0e308 * 10.0); showBool(nan.near(nan, 0.0, 0.0))
Float64.near let nan = (1.0e308 * 10.0) - (1.0e308 * 10.0); showBool(nan.near(1.0))
Float64.near let nan = (1.0e308 * 10.0) - (1.0e308 * 10.0); showBool(1.0.near(nan, 1.0, 1.0))
Float64.near let nan = (1.0e308 * 10.0) - (1.0e308 * 10.0); showBool(1.0.near(1.5, nan))
Float64.near let nan = (1.0e308 * 10.0) - (1.0e308 * 10.0); showBool(1.0.near(1.0, nan, nan))
Float64.near let nan = (1.0e308 * 10.0) - (1.0e308 * 10.0); showBool(1.0.near(1.5, nan, 1.0))
Float64.near let nan = (1.0e308 * 10.0) - (1.0e308 * 10.0); showBool(1.0.near(1.5, 1.0, nan))
Float64.near let inf = 1.0e308 * 10.0; showBool(inf.near(1.0, 0.0, inf))
Float64.near let inf = 1.0e308 * 10.0; showBool(inf.near(1.0, 1.0, inf))
Float64.near let inf = 1.0e308 * 10.0; let ninf = 0.0 - inf; showBool(1.0.near(ninf, 1.0, inf))
Float64.near let inf = 1.0e308 * 10.0; let ninf = 0.0 - inf; showBool(ninf.near(ninf))
Float64.near let inf = 1.0e308 * 10.0; let ninf = 0.0 - inf; showBool(inf.near(ninf))
Float64.near let inf = 1.0e308 * 10.0; showBool(inf.near(1.0))
Float64.near let inf = 1.0e308 * 10.0; showBool(inf.near(inf, 0.0, 0.0))
Float64.near let inf = 1.0e308 * 10.0; showBool(inf.near(1.0, 0.0, 0.0))
Float64.near showBool((-0.0).near(0.0, 0.0, 0.0))
Float64.succ 1.5.succ().toString()
Float64.succ 1e16.succ().toString()
Float64.pred (-0.5).pred().toString()
Float64.e Float64.e.toString()
Float64.pi Float64.pi.toString()
# Empty, generators, and other functions
Empty.eq showBool(empty() == empty())
empty let e: Empty? = empty(); showBool(e != null)
ignore ignore(5); "ignored"
doneResult showResult(doneResult<Int>())
ValueResult.constructor showResult(new ValueResult<Int>(4))
SafeGenerator.next let g = safeCounter(2); let a = showResult(g.next()); let b = showResult(g.next()); let c = showResult(g.next()); "${a} ${b} ${c}"
SafeGenerator.next let g = safeCounter(0); showResult(g.next())
"#;

/// Each case's member and statements.
fn cases() -> Vec<(&'static str, &'static str)> {
    (CASES.lines())
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(|line| line.split_once(' ').expect("a member, then statements"))
        .collect()
}

/// Where the runs disagree, as found: each case, as its member and its
/// place among that member's cases (`listedMap#2`), with the natives'
/// result and the bodies'. A native's `bubble` here is the interpreter's
/// "No method" failure, which `orelse` catches.
const KNOWN: &[(&str, &str, &str)] = &[
    // The bodies loop to a builder's live length, which the callbacks
    // change; natives snapshot it. Listed's docs leave this open.
    ("listedMap#2", "[1,2,3]", "[1,2]"),
    ("listedMap#3", "[1]", "[1,2,3]"),
    ("listedReduceFrom#2", "12", "1299"),
];

/// Core's bodies that no case runs, each with why.
const NOT_RUN: &[(&str, &str)] = &[
    ("Null.constructor", "private: null is a builtin value"),
    (
        "Empty.constructor",
        "runs in core's top level, for `emptySingleton`, before bodies are watched",
    ),
    (
        "ValueResult.constructor",
        "`new` makes the builtin value; the body only sets the field cases read",
    ),
    (
        "GeneratorFnWrapper.constructor",
        "a private class nothing uses: generators are native",
    ),
    ("GeneratorFnWrapper.next", "as above"),
    ("GeneratorFnWrapper.done", "as above"),
    ("GeneratorFnWrapper.close", "as above"),
    ("SafeGeneratorFnWrapper.constructor", "as above"),
    ("SafeGeneratorFnWrapper.nextSafe", "as above"),
    ("SafeGeneratorFnWrapper.done", "as above"),
    ("SafeGeneratorFnWrapper.close", "as above"),
    (
        "NoStringIndex.constructor",
        "private: string indices are builtin values",
    ),
    ("StringIndex.constructor", "as above"),
    (
        "GlobalConsole.log",
        "calls bodiless `globalLog`; the console is native",
    ),
    (
        "getConsole",
        "tmpl calls it for each module's `console`; code can't name it",
    ),
    (
        "Pair.constructor",
        "`new` makes the builtin value; the body only sets the fields cases read",
    ),
];

/// The cases as Temper tests, each logging its result, or with
/// `expected`, asserting it instead, for a backend's run.
fn cases_source(expected: Option<&BTreeMap<usize, String>>) -> String {
    let mut source = String::from(HELPERS);
    for (i, (_, body)) in cases().into_iter().enumerate() {
        let check = match expected.map(|e| e.get(&i)) {
            None => format!("console.log(\"c{i}=${{r}}\");"),
            // What fails isn't checked; nor is what a string can't hold.
            Some(None) => continue,
            Some(Some(e)) if e.starts_with("FAILED") || e.contains(['"', '\\', '$']) => continue,
            Some(Some(e)) => format!("assert(r == \"{e}\");"),
        };
        source.push_str(&format!(
            "test(\"c{i}\") {{\n  let r: String = do {{ {body} }} orelse \"bubble\";\n  {check}\n}}\n"
        ));
    }
    source
}

/// The library of cases, core, and std, settled.
fn settled() -> temper_frontend::settle::Settled {
    let source = cases_source(None);
    let mut library = Library::new();
    for (path, text) in temper_frontend::corelib::SOURCES {
        library.add_file(&format!("core/{path}"), text, source_kind(path));
    }
    library.add_file("cases/cases.temper", &source, SourceKind::Standalone);
    load_imports(&Sources::standard(), &mut library);
    let settled = settle(library);
    let errors: Vec<String> = (settled.library.diagnostics.iter())
        .filter(|d| d.level == Level::Error)
        .map(|d| {
            let (file, at) = settled.library.locate(d.span.start);
            let file = &settled.library.files[file.0 as usize];
            let source = file.parsed.source();
            let line = source[..at as usize].lines().count();
            let text = source.lines().nth(line.saturating_sub(1)).unwrap_or("");
            format!("{}:{line}: {}: {text}", file.path, d.message.text())
        })
        .collect();
    assert_eq!(errors, Vec::<String>::new());
    settled
}

/// Each case's result in a run: what it logged, or why its test failed.
fn results(run: &Run) -> BTreeMap<usize, String> {
    assert_eq!(run.failure, None, "the run failed");
    let mut found = BTreeMap::new();
    let mut current = None;
    for line in &run.output {
        if let Some((case, result)) = (line.strip_prefix('c')).and_then(|l| l.split_once('=')) {
            if let Ok(case) = case.parse() {
                found.insert(case, result.to_string());
                continue;
            }
        }
        let line = line.trim();
        if let Some(name) = line.strip_prefix("<testcase name='c") {
            current = name.split('\'').next().and_then(|n| n.parse().ok());
        } else if let (Some(rest), Some(case)) = (line.strip_prefix("<failure message='"), current)
        {
            let why = rest.split('\'').next().unwrap_or(rest);
            found.entry(case).or_insert_with(|| format!("FAILED {why}"));
        }
    }
    found
}

/// Each case's name, as its member and its place among that member's
/// cases, as `listedMap#2`.
fn case_names() -> Vec<String> {
    let mut counts = BTreeMap::new();
    (cases().into_iter())
        .map(|(member, _)| {
            let count = counts.entry(member).or_insert(0);
            *count += 1;
            format!("{member}#{}", *count - 1)
        })
        .collect()
}

#[test]
fn bodies_agree_with_natives() {
    std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(|| {
            let settled = settled();
            let natives = results(&run_tests(&settled, Some(FUEL)));
            let (run, ran) = run_tests_with_core_bodies(&settled, Some(FUEL));
            let bodies = results(&run);
            // A library of the cases, asserting the bodies' results, for
            // `temper test -b …` to check backends' connections against.
            if let Some(dir) = std::env::var_os("CORE_BODIES_LIBRARY") {
                let src = std::path::Path::new(&dir).join("src");
                std::fs::create_dir_all(&src).unwrap();
                let config = "# Core bodies\n\n    export let name = \"core-bodies\";\n";
                std::fs::write(src.join("config.temper.md"), config).unwrap();
                std::fs::write(src.join("cases.temper"), cases_source(Some(&bodies))).unwrap();
                let names: Vec<String> = (case_names().iter().enumerate())
                    .map(|(i, name)| format!("c{i} {name}"))
                    .collect();
                let names = names.join("\n") + "\n";
                std::fs::write(std::path::Path::new(&dir).join("cases.txt"), names).unwrap();
            }
            let show = std::env::var_os("CORE_BODIES_SHOW").is_some();
            let mut found = Vec::new();
            for (i, name) in case_names().into_iter().enumerate() {
                let missing = || "(no result)".to_string();
                let native = natives.get(&i).cloned().unwrap_or_else(missing);
                let body = bodies.get(&i).cloned().unwrap_or_else(missing);
                if show {
                    println!("{name}\n  native: {native}\n  body:   {body}");
                }
                if native != body {
                    found.push((name, native, body));
                }
            }
            let known: Vec<(String, String, String)> = (KNOWN.iter())
                .map(|&(c, n, b)| (c.to_string(), n.to_string(), b.to_string()))
                .collect();
            assert_eq!(found, known, "where natives and bodies disagree");
            // Every body runs in some case, but those listed.
            if show {
                for (label, f) in members(&settled) {
                    let ran = ran.binary_search(&f).is_ok();
                    println!("member {label}{}", if ran { "" } else { " (not run)" });
                }
            }
            // By label, whichever of core's files declares each.
            let mut not_run: Vec<String> = (members(&settled).into_iter())
                .filter(|(_, f)| ran.binary_search(f).is_err())
                .map(|(label, _)| label)
                .collect();
            not_run.sort();
            let mut listed: Vec<&str> = NOT_RUN.iter().map(|&(label, _)| label).collect();
            listed.sort();
            assert_eq!(not_run, listed, "core's bodies that no case runs");
        })
        .unwrap()
        .join()
        .unwrap();
}

/// Core's classes keep no field that only describes a host's
/// representation, as each builtin's `content` did: each instance field
/// is state that core's own Temper sets, by an initializer or an
/// assignment in its class, for its bodies to read.
#[test]
fn fields_are_cores_own_state() {
    use temper_frontend::core::{
        CLASS_INTERFACE, MEMBER_STATIC, ROLE_MASK, class_parts, let_parts,
    };
    std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(|| {
            let mut library = Library::new();
            for (path, text) in temper_frontend::corelib::SOURCES {
                library.add_file(&format!("core/{path}"), text, source_kind(path));
            }
            let settled = settle(library);
            let (lib, tree) = (&settled.library, &settled.library.tree);
            let text_of = |node| lib.name(node).map_or("?", |n| lib.names.get(n));
            let mut unset = Vec::new();
            for module in &settled.modules {
                for &class in &module.top_levels {
                    if tree.kind(class) != NodeKind::Class
                        || tree.data(class) & CLASS_INTERFACE != 0
                    {
                        continue;
                    }
                    let (name, _, members) = class_parts(tree, class);
                    let text = lib.source_text(tree.span(class));
                    for &m in members {
                        let data = tree.data(m);
                        if tree.kind(m) != NodeKind::Let || data & (ROLE_MASK | MEMBER_STATIC) != 0
                        {
                            continue;
                        }
                        let (field, _, init) = let_parts(tree, m);
                        if init.is_none() && !assigns(text, text_of(field)) {
                            unset.push(format!("{}.{}", text_of(name), text_of(field)));
                        }
                    }
                }
            }
            assert_eq!(unset, Vec::<String>::new(), "fields nothing in core sets");
        })
        .unwrap()
        .join()
        .unwrap();
}

/// Whether `text` assigns `name`, as `name = …` or `this.name = …`.
fn assigns(text: &str, name: &str) -> bool {
    text.match_indices(name).any(|(at, _)| {
        let before = text[..at].chars().next_back();
        let after = text[at + name.len()..].trim_start();
        !before.is_some_and(|c| c.is_alphanumeric() || c == '_')
            && after.starts_with('=')
            && !after.starts_with("==")
    })
}

/// Core's bodies that aren't `@connected`, which no backend's host code
/// stands in for, each with what the interpreter runs for it and why
/// (notes/core-bodies.md, "What the interpreter runs").
const UNCONNECTED: &[(&str, &str)] = &[
    ("Null.constructor", "private: null is a builtin value"),
    ("Empty.constructor", "private: empty() is a builtin value"),
    (
        "StringIndex.constructor",
        "private: string indices are builtin values",
    ),
    (
        "GeneratorFnWrapper.constructor",
        "a private class nothing uses: generators are native",
    ),
    ("GeneratorFnWrapper.next", "as above"),
    ("GeneratorFnWrapper.done", "as above"),
    ("GeneratorFnWrapper.close", "as above"),
    ("SafeGeneratorFnWrapper.constructor", "as above"),
    ("SafeGeneratorFnWrapper.nextSafe", "as above"),
    ("SafeGeneratorFnWrapper.done", "as above"),
    ("SafeGeneratorFnWrapper.close", "as above"),
    (
        "GlobalConsole.log",
        "the console is the host's, as getConsole() is connected",
    ),
    (
        "listedReduceFromIndex",
        "a native, measurably faster: its body takes core settled beside each program, \
         and runs 4 to 5 times slower; bodies_agree_with_natives keeps the two in step",
    ),
];

/// Each of core's bodies is `@connected`, so the interpreter's native
/// stands in for it as host code does on backends, or is listed in
/// [`UNCONNECTED`] with what runs for it.
#[test]
fn unconnected_bodies_are_listed() {
    use temper_frontend::core::{MEMBER_CONNECTED, ROLE_CONSTRUCTOR, ROLE_MASK, class_parts};
    use temper_frontend::core::{let_parts, member_fn};
    std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(|| {
            let mut library = Library::new();
            for (path, text) in temper_frontend::corelib::SOURCES {
                library.add_file(&format!("core/{path}"), text, source_kind(path));
            }
            let settled = settle(library);
            let (lib, tree) = (&settled.library, &settled.library.tree);
            let text_of = |node| lib.name(node).map_or("?", |n| lib.names.get(n));
            let connected =
                |decl| tree.data(decl) & MEMBER_CONNECTED != 0 || lib.connected.contains_key(&decl);
            let mut found = Vec::new();
            for module in &settled.modules {
                for &top in &module.top_levels {
                    match tree.kind(top) {
                        NodeKind::Class => {
                            let (class, _, members) = class_parts(tree, top);
                            for &m in members {
                                if member_fn(tree, m).is_none() || connected(m) {
                                    continue;
                                }
                                let member = match tree.data(m) & ROLE_MASK {
                                    ROLE_CONSTRUCTOR => "constructor",
                                    _ => text_of(let_parts(tree, m).0),
                                };
                                found.push(format!("{}.{member}", text_of(class)));
                            }
                        }
                        NodeKind::Let if member_fn(tree, top).is_some() && !connected(top) => {
                            found.push(text_of(let_parts(tree, top).0).to_string());
                        }
                        _ => {}
                    }
                }
            }
            found.sort();
            let mut listed: Vec<String> = UNCONNECTED
                .iter()
                .map(|&(label, _)| label.to_string())
                .collect();
            listed.sort();
            assert_eq!(found, listed, "core's bodies that aren't @connected");
        })
        .unwrap()
        .join()
        .unwrap();
}
