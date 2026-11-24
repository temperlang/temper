//! The Lua backend on Kotlin Temper's functional tests that tmpl's
//! translation covers so far, in `temper-funtests`'s
//! `cases/`, its class tests among them (`kit::CLASSES`),
//! those with nullable types and `orelse` (`kit::NULLABLES`), those
//! with generics (`kit::GENERICS`), those with core's other types
//! and members (`kit::CORE_TYPES`), and those with a library's own
//! connected functions and classes, whose host code `_connected.lua` is
//! inlined in its module and `_support.lua` copied beside it
//! (`kit::CONNECTED`, each as is and under `--no-connected`), those of
//! divisions that can't fail and maps (`kit::DIVISIONS`), those of
//! labeled loops' jumps and classes declared in functions
//! (`kit::LOOPS_AND_CLASSES`), those of generators, `async`, and `await`,
//! as coroutines (`kit::ASYNC`), the tests run as tests (`kit::TESTS`),
//! and those importing std's `regex`, `json`, and `temporal`
//! (`kit::WITH_STD`, each beside std's output, under `timeout`;
//! `regex/zero-advance` gives what Kotlin's issue 166 expects, and
//! `regex`'s don't run on LuaJIT, which lacks `utf8`),
//! std `net`'s (`kit::NET`), through LuaSocket under a Lua that loads it
//! by LuaRocks' paths or its own (the `LUA` that runs the rest, or else
//! `lua`),
//! where they pass, else as Kotlin's suite expects them to fail here (a
//! request gives a broken promise, `kit::without_net`), and with a client
//! that host code plugs in (`tests/host/net.lua`), on
//! the shared programs (`crates/temper-be-testkit/programs/`), which
//! cover operators, jumps, module state, string order, classes, and
//! nullables beyond those tests, and on programs of Lua's own ([`HIDING`],
//! [`HOISTING`], [`MEMBERS`], [`REPEATS`], and [`locals`]).
//!
//! - `goldens`: each program's generated library, laid out as Kotlin's,
//!   with its `init.lua` matching the golden in `tests/goldens/`, named
//!   for the test's path (`algos-fibonacci.lua`), and its rockspec as
//!   Kotlin writes one. To accept new or changed output, run with
//!   `UPDATE_SNAPSHOTS=1` and review the diff.
//! - `programs_log_as_the_interpreter_does`: Lua's own Temper programs
//!   log, on tempest's interpreter, what the test expects of them.
//! - `runs`: with `lua` (or `LUA`), from the directory holding the
//!   generated libraries and the runtime
//!   (`crates/temper-be-lua/runtime/temper-core`), with Kotlin's `LUA_PATH`
//!   (`?.lua;?/init.lua`), one process requires every library in turn,
//!   and each logs what the test expects of Kotlin's `lua` backend (a
//!   library named as one before it, as both `basics`, in a directory
//!   and process of its own); one
//!   library also runs alone, as Kotlin runs a program
//!   (`lua fibonacci/init.lua`); `module-state`'s exported `var`s, read
//!   from its exports table, stay current as its functions write them,
//!   as `members`' does as a method writes it, and its static, property,
//!   and instance's `tostring` read as Lua's users read them. `luac -p`
//!   (or `LUAC`) parses every generated file, if there's a `luac`, and
//!   LuaLS (`lua-language-server --check`, or `LUALS`)
//!   checks them against their LuaCATS annotations and a typed view of
//!   the runtime (`tests/luals/temper-core.lua`), finding no problems at
//!   its `Warning` level, but for a library with host code, which isn't
//!   typed for LuaLS, run in a directory of its own. Skipped, with a
//!   message, without `lua`, and the check without LuaLS; with
//!   `TEMPER_REQUIRE_CHECKERS` set, as in CI, either missing fails it
//!   instead. A missing `luac` only skips.

use std::path::{Path, PathBuf};
use std::process::Command;
use std::{env, fs};
use temper_be_lua::translate::INT64_LOCALS;
use temper_be_lua::{LUA, OutFile, names, translate};
use temper_be_testkit::{self as kit, Case, Goldens};

/// A library of names that Lua reserves or the translator prints (Lua's
/// keywords `end`, `goto`, `repeat`, and `nil`, which Temper allows, and
/// `temper`, `print`, `tostring`, `math`, `exports`, `module`, `require`,
/// and `_ENV`), each taking `_`; `Boolean`'s order, which Lua can't
/// compare, as logic and, where the right side calls something, through
/// the runtime; an `if` value of each kind; strings Lua would read
/// otherwise (quotes, a backslash, a NUL before a digit, and a
/// right-to-left override); and shifts by more than the width.
const HIDING: &str = r#"# Hiding

    let end(goto: Int): Int { goto + 1 }
    let temper = "t";
    let print(repeat: Boolean, nil: Boolean): Boolean { repeat < nil }
    var calls = 0;
    let tostring(b: Boolean): Boolean { calls += 1; b }
    let math(a: Boolean, b: Boolean): String {
      "${(a <= tostring(b)).toString()} ${(a > b).toString()} ${(a >= b).toString()}"
    }
    let exports(b: Boolean): String { "${if (b) { "yes" } else { "no" }}!" }
    let module(b: Boolean, c: Boolean): String { (if (b) { true } else { c }).toString() }
    let require(b: Boolean, c: Boolean): String { (if (b) { false } else { c }).toString() }
    let _ENV(b: Boolean, c: Boolean, d: Boolean): String { (if (b) { c } else { d }).toString() }
    let shifts(n: Int): String { "${1 << n} ${-8 >> n} ${-8 >>> (n - 4)}" }
    console.log("${end(1).toString()} ${temper} ${print(false, true).toString()} ${print(true, false).toString()}");
    console.log("${math(true, false)} ${calls.toString()} ${exports(true)} ${exports(false)}");
    console.log("${module(false, true)} ${require(false, true)} ${_ENV(true, false, true)}");
    console.log("\"it's\" \\ a\u{0}1 \u{202E}x");
    console.log(shifts(33));
"#;

/// What [`HIDING`] logs.
const HIDING_LOG: [&str; 5] = [
    "2 t true false",
    "false true true 1 yes! no!",
    "true true false",
    "\"it's\" \\ a\u{0}1 \u{202E}x",
    "2 -4 7",
];

/// Lua's standard globals as a module's values and functions, and as
/// parameters, locals, and temporaries, each taking `_`
/// ([`names::GLOBALS`]), so none hides what the output reaches
/// (`math.huge` for `1E999`, `error("panic")`, `tostring`) or host code
/// might, while exports keep their names as their module's keys
/// (`string = string_`, and `exports.pairs` for a `var` that functions
/// write); `no_lua_global_is_declared` checks the output declares none.
const GLOBALS: &str = r#"# Globals

    let table = [1, 2, 3];
    export var string = "s";
    export var pairs = 0;
    export let type(next: Int, math: Int): Int { pairs += 1; next * math }
    let half(error: Int): Int throws Bubble {
      if (error % 2 != 0) { bubble(); }
      error / 2
    }
    let select(error: Int, table: List<Int>): Int {
      let string = half(error) orelse panic();
      var select = 0;
      for (let next of table) { select += next; }
      select + string
    }
    let huge(math: Float64): Float64 { math * 1E999 }
    string = "${type(2, 3).toString()} ${select(4, table).toString()}";
    console.log("${string} ${huge(2.0).toString()} ${type(1, 1) == select(2, [])} ${pairs}");
    for (let string of ["ab", "c"]) { console.log("${string}!"); }
"#;

/// What [`GLOBALS`] logs.
const GLOBALS_LOG: [&str; 3] = ["6 8 Infinity true 2", "ab!", "c!"];

/// The names a Lua chunk declares: its locals, functions, parameters, and
/// loop variables.
fn declared(lua: &str) -> Vec<&str> {
    fn names(list: &str) -> impl Iterator<Item = &str> {
        list.split(',').map(str::trim).filter(|n| !n.is_empty())
    }
    let mut out = Vec::new();
    for line in lua.lines().map(str::trim_start) {
        if line.starts_with("--") {
            continue;
        }
        let rest = line.strip_prefix("local ").unwrap_or(line);
        if let Some(f) = rest.strip_prefix("function ") {
            let (name, params) = f.split_once('(').unwrap_or((f, ""));
            out.push(name.trim());
            out.extend(names(params.split(')').next().unwrap_or("")));
        } else if line.starts_with("local ") {
            out.extend(names(rest.split('=').next().unwrap_or(rest)));
        } else if let Some(f) = line.strip_prefix("for ") {
            out.extend(names(f.split([' ', '=']).next().unwrap_or("")));
            if let Some((vars, _)) = f.split_once(" in ") {
                out.extend(names(vars));
            }
        }
        if let Some((_, f)) = line.split_once("function(") {
            out.extend(names(f.split(')').next().unwrap_or("")));
        }
    }
    out
}

/// [`GLOBALS`]'s output declares none of Lua's globals.
#[test]
fn no_lua_global_is_declared() {
    let case = Case::inline("globals", "globals.temper.md", GLOBALS, vec![]);
    let files = generate(&case);
    let declared = declared(&files[0].text);
    assert!(declared.contains(&"string_"), "{declared:?}");
    let text = &files[0].text;
    for key in ["string = string_", "type = type_", "exports.pairs ="] {
        assert!(text.contains(key), "no `{key}` in\n{text}");
    }
    let hiding: Vec<_> = (declared.iter())
        .filter(|n| names::GLOBALS.contains(n) || names::SPECIAL.contains(n))
        .collect();
    assert!(hiding.is_empty(), "declared {hiding:?}\n{}", files[0].text);
}

/// What Lua can't write inline, hoisted before its statement: an `if`
/// value of `Boolean`s whose condition does something, `orelse` on the
/// right of `&&` and `||`, `orelse` in arguments after others, and `??`
/// and `orelse` in a string template; and jumps out of an `orelse` body.
const HOISTING: &str = r#"# Hoisting

    var calls = 0;
    let noisy(b: Boolean): Boolean { calls += 1; b }
    let half(n: Int): Int throws Bubble {
      if (n % 2 != 0) { bubble(); }
      n / 2
    }
    let pick(c: Boolean, a: Boolean, b: Boolean): Boolean { if (noisy(c)) { a } else { b } }
    let isPos(n: Int): Boolean { n > 0 }
    let both(a: Boolean, n: Int): Boolean { a && isPos(half(n) orelse 0) }
    let either(a: Boolean, n: Int): Boolean { a || isPos(half(n) orelse 0) }
    let sum(a: Int, b: Int): Int { a + b }
    let label(x: String?): String { "got ${x ?? "nothing"} and ${(half(3) orelse -1).toString()}" }
    console.log("${pick(true, false, true)} ${pick(false, false, true)} ${calls}");
    console.log("${both(true, 4)} ${both(true, 3)} ${both(false, 4)}");
    console.log("${either(false, 4)} ${either(false, 3)} ${either(true, 3)}");
    console.log(sum(half(8) orelse 0, half(7) orelse 100).toString());
    console.log(label(null));
    console.log(label("x"));

Jumps out of an `orelse` body, which `temper.pcall` runs as a function of
its own: a `return`, a `break`, and a `continue`, of an outer loop too,
and from an `orelse` in another.

    let firstHalf(ns: List<Int>): Int {
      for (var i = 0; i < ns.length; ++i) {
        do {
          if (ns[i] < 0) { continue; }
          if (ns[i] == 0) { break; }
          return half(ns[i]);
        } orelse console.log("odd ${ns[i].toString()}");
      }
      -1
    }
    let halfOr(n: Int): Int {
      var h = 0;
      do {
        if (n < 0) { return -100; }
        h = half(n);
      } orelse do { h = 7; }
      h + 1
    }
    let pairsUpTo(n: Int): Int {
      var count = 0;
      outer: for (var i = 0; i < n; ++i) {
        for (var j = 0; j < n; ++j) {
          do {
            if (j > i + half(2) - 1) { continue outer; }
            if (i == 3) { break outer; }
            do {
              if (j == 1) { continue; }
              count += 1 + half(0);
            } orelse do { count += 100; }
          } orelse do { count += 1000; }
        }
      }
      count
    }
    console.log(firstHalf([3, -2, 8]).toString());
    console.log(firstHalf([5, 0, 8]).toString());
    console.log("${halfOr(-1).toString()} ${halfOr(4).toString()} ${halfOr(3).toString()}");
    console.log(pairsUpTo(5).toString());
"#;

/// What [`HOISTING`] logs.
const HOISTING_LOG: [&str; 12] = [
    "false true 2",
    "true false false",
    "true false true",
    "104",
    "got nothing and -1",
    "got x and -1",
    "odd 3",
    "4",
    "odd 5",
    "-1",
    "-100 3 8",
    "4",
];

/// Jumps the frontend doesn't translate into tmpl yet, as tmpl: a
/// `continue` in a `do … while` whose body declares a local, which Lua's
/// `goto` can't jump past to `until`, so the body takes a `do … end` of
/// its own; a `continue` of an outer loop from an inner one; and a
/// bubble in a function with a result, which `return`s it.
const REPEATS: &str = "tmpl 1 model 1 library repeats 0.0.0

uses `temper-core`#1 0.1.0 {
  interface Console#2 {
    connected method log#3(message: String): Void;
  }
  class Int32#4 {
    connected method toString#5(radix?: Int32): String;
  }
  connected fn getConsole#6(name?: String): Console#2;
}

module repeats#0 {
  let ~console#7: Console#2;

  fn odds#8(n#9: Int32): Int32 {
    var i#10: Int32 = 0;
    var sum#11: Int32 = 0;
    ~loop#12: do {
      i#10 = %Int32.add(i#10, 1);
      let bit#13: Int32 = %Int32.bitAnd(i#10, 1);
      if (%Int32.eq(bit#13, 0)) {
        continue ~loop#12;
      }
      sum#11 = %Int32.add(sum#11, i#10);
    } while (%Int32.lt(i#10, n#9));
    return sum#11;
  }

  fn pairs#14(n#15: Int32): Int32 {
    var count#16: Int32 = 0;
    var i#17: Int32 = 0;
    rows#18: while (%Int32.lt(i#17, n#15)) {
      i#17 = %Int32.add(i#17, 1);
      var j#19: Int32 = 0;
      ~loop#20: while (true) {
        j#19 = %Int32.add(j#19, 1);
        if (%Int32.gt(j#19, i#17)) {
          continue rows#18;
        }
        count#16 = %Int32.add(count#16, 1);
      }
    }
    return count#16;
  }

  fn positive#21(n#22: Int32): Int32 throws Bubble {
    if (%Int32.lt(n#22, 0)) {
      return (error(\"negative\")): Int32;
    }
    return n#22;
  }

  init {
    init ~console#7 = getConsole#6(omitted);
    ~console#7.log#3(odds#8(9).toString#5(omitted));
    ~console#7.log#3(pairs#14(4).toString#5(omitted));
    ~console#7.log#3(positive#21(3)^.toString#5(omitted));
  }
}
";

/// Classes where Lua's translation takes care: an exported `var` that only
/// a method writes, which stays current in the exports table; a static
/// named as a key of the runtime's type table (`methods`), which takes a
/// `_`; a method call on the right of `Boolean`'s `<`, which Lua's `and`
/// would skip; a field that implements an interface's default getter,
/// stored under a key of its own, with a getter; and a plain `toString()`,
/// which `tostring` shows ([`MEMBERS_READ`]).
const MEMBERS: &str = r#"# Members

    let make(n: Int): Box { new Box(n) }
    export var made = 0;
    export class Box(public var size: Int) extends Sized {
      public static methods = 7;
      public bump(): Int { made += 1; size += 1; size }
      public positive(): Boolean { made += 1; size > 0 }
      public toString(): String { "Box(${size})" }
    }
    export interface Sized {
      public get size(): Int;
      public twice(): Int { size * 2 }
    }
    export interface Named {
      public get name(): String { "anon" }
    }
    export class Pet(public name: String) extends Named {}
    let order(b: Box): Boolean { true < b.positive() }
    let b = make(1);
    console.log("${b.bump().toString()} ${b.twice().toString()} ${made.toString()}");
    console.log("${order(b).toString()} ${made.toString()} ${Box.methods.toString()}");
    let n: Named = new Pet("rex");
    console.log(n.name);
"#;

/// What [`MEMBERS`] logs.
const MEMBERS_LOG: [&str; 3] = ["2 4 1", "false 2 7", "rex"];

/// How many module-level `let`s and functions [`locals`] declares, past
/// what Lua allows a chunk as locals.
const MANY: usize = 220;

/// A library with more module-level declarations than Lua allows a chunk
/// locals, so those past the first 100 are fields of a table, a
/// function that uses the first and the last, and one with more `Int64`
/// constants than a chunk declares as locals.
fn locals() -> (String, Vec<String>) {
    let mut text = String::from("# Locals\n\n");
    for i in 0..MANY / 2 {
        text.push_str(&format!("    let v{i} = {i};\n"));
        text.push_str(&format!("    let f{i}(n: Int): Int {{ n + v{i} }}\n"));
    }
    let last = MANY / 2 - 1;
    text.push_str(&format!(
        "    export let sum(): Int {{ f0(1) + f{last}(v{last}) }}\n"
    ));
    text.push_str("    console.log(sum().toString());\n");
    // More `Int64` constants than a chunk declares as locals, so they're
    // a table's, one of them twice, one negated, and one of two halves.
    let terms: Vec<String> = (0..=INT64_LOCALS).map(|i| format!("{i}i64")).collect();
    text.push_str(&format!(
        "    let sum64(): Int64 {{ {} - 1i64 + -2i64 + 0x1_0000_0000_i64 }}\n",
        terms.join(" + ")
    ));
    text.push_str("    console.log(sum64().toString());\n");
    let sum64 = INT64_LOCALS * (INT64_LOCALS + 1) / 2 - 3 + (1 << 32);
    (text, vec![format!("{}", 1 + 2 * last), sum64.to_string()])
}

fn cases() -> Vec<Case> {
    let tests = [
        &kit::CLASSES[..],
        &kit::NULLABLES[..],
        &kit::GENERICS[..],
        &kit::NAMES[..],
        &kit::FUNCTIONS[..],
        &kit::DEFAULTS[..],
        &kit::CORE_TYPES[..],
        &kit::CONNECTED[..],
        &kit::DIVISIONS[..],
        &kit::LOOPS_AND_CLASSES[..],
        &kit::TYPING[..],
        &kit::ANY_VALUES[..],
        &kit::CORE_CHECKS[..],
        &kit::ERRORS[..],
        &kit::TYPE_VALUES[..],
        &kit::ASYNC[..],
        &kit::TESTS[..],
        &kit::WITH_STD[..],
        &kit::NET[..],
    ]
    .concat();
    let mut cases = kit::cases_with("lua", &kit::PROGRAMS, &tests);
    // Kotlin's suite expects `regex/zero-advance` to fail on Lua (its
    // issue 166): the runtime's engine replaces nothing for `(^|,)\s*`
    // (`^` alone matches, and `,\s*` leaves the space), so the slice
    // drops the "a" of "apples". The log expected here is what it gives,
    // so a fix shows as a change.
    for case in cases.iter_mut().filter(|c| c.key == "regex-zero-advance") {
        case.expected = vec!["pples, bananas, cherries".into()];
    }
    let hiding_log = HIDING_LOG.iter().map(|s| s.to_string()).collect();
    cases.push(Case::inline(
        "hiding",
        "hiding.temper.md",
        HIDING,
        hiding_log,
    ));
    let globals_log = GLOBALS_LOG.iter().map(|s| s.to_string()).collect();
    cases.push(Case::inline(
        "globals",
        "globals.temper.md",
        GLOBALS,
        globals_log,
    ));
    cases.push(Case::inline(
        "repeats",
        "repeats.tmpl",
        REPEATS,
        vec!["25".into(), "10".into(), "3".into()],
    ));
    let members_log = MEMBERS_LOG.iter().map(|s| s.to_string()).collect();
    cases.push(Case::inline(
        "members",
        "members.temper.md",
        MEMBERS,
        members_log,
    ));
    let hoisting_log = HOISTING_LOG.iter().map(|s| s.to_string()).collect();
    cases.push(Case::inline(
        "hoisting",
        "hoisting.temper.md",
        HOISTING,
        hoisting_log,
    ));
    let (text, expected) = locals();
    cases.push(Case::inline("locals", "locals.temper.md", text, expected));
    cases
}

/// A case's generated files.
fn generate(case: &Case) -> Vec<OutFile> {
    translate(&kit::program_for(case, &LUA.rules), &LUA)
        .unwrap_or_else(|e| panic!("{}: {e}", case.key))
}

/// The rockspec Kotlin writes for a library without a version.
fn rockspec(library: &str) -> String {
    format!(
        "package = '{library}'
version = 'dev-1'
rockspec_format = '3.0'
description = {{
}}
build = {{
    type = 'builtin',
    copy_directories = {{'.'}},
    modules = {{}},
}}
dependencies = {{
    'lua >= 5.1',
    'temper-core == {core}',
}}
source = {{
    url = 'file://{library}.zip',
}}
",
        core = temper_tmpl::version::current().spell(temper_tmpl::version::Spelling::LuaRocks),
    )
}

#[test]
fn goldens() {
    kit::with_stack(|| {
        let mut goldens = Goldens::new(env!("CARGO_MANIFEST_DIR"));
        for case in cases() {
            let files = generate(&case);
            let library = &case.library;
            let paths: Vec<&str> = files.iter().map(|f| f.path.as_str()).collect();
            let spec = format!("{library}/{library}-dev-1.rockspec");
            assert_eq!(paths[0], format!("{library}/init.lua"), "{}", case.key);
            assert_eq!(paths.last(), Some(&spec.as_str()), "{}", case.key);
            if case.key == "locals" {
                // Too long to keep; `runs` runs it.
                continue;
            }
            goldens.check(&format!("{}.lua", case.key), &files[0].text);
            // Other modules, tests, and a rockspec with dependencies.
            for f in &files[1..] {
                if f.path == spec && f.text == rockspec(library) {
                    continue;
                }
                let rest = &f.path[library.len() + 1..];
                goldens.check(&format!("{}.{}", case.key, rest.replace('/', ".")), &f.text);
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
        let lines = (files.iter().filter(|f| f.path.ends_with(".lua"))).flat_map(|f| {
            let tests = f.path.contains("/tests/");
            (f.text.lines()).map(move |line| (f.path.as_str(), tests, line))
        });
        kit::check_test_parts_apart(lines, &kit::REACH_TESTS_ONLY, &kit::REACH_UNREACHED);
    });
}

#[test]
fn programs_log_as_the_interpreter_does() {
    kit::with_stack(|| {
        for case in cases() {
            if !matches!(
                case.key.as_str(),
                "hiding" | "members" | "locals" | "hoisting"
            ) {
                continue;
            }
            let (failure, output) = kit::interpret(&case);
            assert_eq!(failure, None, "{}", case.key);
            assert_eq!(output, case.expected, "{}", case.key);
        }
    });
}

/// A program given by an environment variable, or found on `PATH`, if it
/// runs with `arg`.
fn tool(var: &str, name: &str, arg: &str) -> Option<String> {
    kit::tool_with(var, name, arg).map(|(t, _)| t)
}

fn runtime() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime/temper-core")
}

/// Kotlin's `LUA_PATH`, for running from the directory that holds the
/// libraries and `temper-core`.
const LUA_PATH: &str = "?.lua;?/init.lua";

/// Requires each library named on the command line in turn, after a line
/// naming it, so one process runs them all; an error is a line too.
const RUNNER: &str = r#"for _, name in ipairs(arg) do
  io.write("==> ", name, "\n")
  local ok, err = pcall(require, name)
  if not ok then
    io.write("!!> ", (tostring(err):gsub("\n", " ")), "\n")
  end
end
"#;

/// Runs a tests file with a stand-in for LuaUnit, which runs each
/// function of `Test_`, in order of name, printing `ok NAME` or `fail NAME
/// why`.
const TESTS: &str = r#"package.preload["luaunit"] = function()
  return { LuaUnit = { run = function()
    local names = {}
    for name in pairs(Test_) do names[#names + 1] = name end
    table.sort(names)
    for _, name in ipairs(names) do
      local ok, err = pcall(Test_[name])
      io.write(ok and "ok " or "fail ", name, ok and "" or (" " .. tostring(err)), "\n")
    end
  end } }
end
arg = {}
dofile(...)
"#;

/// Reads `module-state`'s exports after its functions write them.
const LIVE: &str = r#"local state = require("module-state")
state.tick()
state.tick()
print(state.ticks, state.total, state.greeting, state.count)
"#;

/// Uses [`MEMBERS`]' exports: its `var`, current after a method writes
/// it, its static, a field read through its getter, and `tostring` of an
/// instance.
const MEMBERS_READ: &str = r#"local m = require("members")
print(m.made, m.Box.methods_, m.Pet("x").name, tostring(m.Box(5)))
m.Box(1):bump()
print(m.made)
"#;

/// LuaLS's settings for checking generated code: Lua 5.4, `require` as
/// Kotlin's `LUA_PATH` finds modules, the runtime's typed view as a
/// library in place of the runtime itself (and std's output, where it's
/// imported), and the type checks on every file. Doc comments aren't
/// required, as with Java's `-missing`, until tmpl carries Temper's docs.
fn luarc(libraries: &[&Path]) -> String {
    let libraries: Vec<String> = (libraries.iter())
        .map(|l| format!("\"{}\"", l.to_string_lossy().replace('\\', "/")))
        .collect();
    let libraries = libraries.join(", ");
    format!(
        r#"{{
  "runtime.version": "Lua 5.4",
  "runtime.path": ["?.lua", "?/init.lua"],
  "workspace.library": [{libraries}],
  "workspace.ignoreDir": ["temper-core", "luaunit"],
  "workspace.checkThirdParty": false,
  "diagnostics.disable": ["missing-local-export-doc"],
  "diagnostics.groupFileStatus": {{
    "type-check": "Any",
    "luadoc": "Any",
    "unbalanced": "Any",
    "global": "Any",
    "await": "Any"
  }}
}}
"#
    )
}

/// Tests that the runtime gets wrong on LuaJIT (and Lua 5.1), Kotlin's
/// bugs, which these run on Lua 5.4 to show: LuaJIT's `tonumber(s, 10)`
/// takes `"2.0"` and `"0x10"` (`String.toInt32()`, `types/string/read`);
/// and `float64_max` and `float64_min` check only their second argument
/// for NaN, where LuaJIT's `math.max(nan, 2)` gives 2
/// (`types/float/ops`).
/// The runtime's regex engine needs Lua 5.3's `utf8` too (`regex/match`
/// and `regex/zero-advance`).
const NEEDS_LUA53: [&str; 4] = [
    "types-float-ops",
    "types-string-read",
    "regex-match",
    "regex-zero-advance",
];

/// Whether a library imports std's modules, which it then runs beside
/// ([`kit::Case::needs_std`]; a run as tests needs only the runtime's
/// `test_*` functions on Lua).
fn imports_std(case: &Case) -> bool {
    case.needs_std() && case.tests.is_none()
}

/// A Lua run, under `timeout` where there is one: the regex runtime
/// hangs on some patterns (a negated range up to U+10FFFF, say).
fn capped(lua: &str) -> Command {
    match tool("TIMEOUT", "timeout", "--version") {
        Some(timeout) => {
            let mut command = Command::new(timeout);
            command.args(["300", lua]);
            command
        }
        None => Command::new(lua),
    }
}

#[test]
fn runs() {
    let Some((lua, version)) = kit::tool_with("LUA", "lua", "-v") else {
        kit::missing("no Lua (on PATH, or named by LUA)");
        return;
    };
    let jit = version.contains("LuaJIT");
    let luals = tool("LUALS", "lua-language-server", "--version");
    if luals.is_none() {
        kit::missing("type check: no LuaLS (lua-language-server on PATH, or named by LUALS)");
    }
    let luac = tool("LUAC", "luac", "-v");
    if luac.is_none() {
        eprintln!("syntax check skipped: no luac (on PATH, or named by LUAC)");
    }
    kit::with_stack(move || {
        let out = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-lua");
        let _ = fs::remove_dir_all(&out);
        let dir = out.join("lua");
        kit::copy_dir(&runtime(), &dir.join("temper-core"), &[]);
        let mut cases = cases();
        // std `net`'s tests, with LuaSocket's client where a Lua loads it,
        // else as Kotlin's suite expects them to fail on Lua.
        let net = net_lua(&lua);
        if net.is_none() {
            eprintln!(
                "std net's LuaSocket client skipped: no Lua that loads socket.http through luarocks' paths or its own"
            );
            kit::without_net(&mut cases);
        }
        if jit {
            cases.retain(|c| !NEEDS_LUA53.contains(&c.key.as_str()));
        }
        let mut generated = Vec::new();
        // A library named as one before it (`types/int/basics` and
        // `types/float/basics` are both `basics`), or whose run fails, goes
        // in a directory of its own, and runs and is checked there.
        let mut named = std::collections::HashSet::new();
        let mut apart: Vec<(&Case, PathBuf)> = Vec::new();
        for case in &cases {
            // A library with host code too, or ill-typed code, which LuaLS
            // doesn't check, one that runs as tests, with LuaUnit beside
            // it, and one that imports std's modules, with std on its
            // `LUA_PATH`.
            let here = if case.host_files.is_empty()
                && !kit::ill_typed(case)
                && case.tests.is_none()
                && !case.fails
                && !imports_std(case)
                && named.insert(case.library.clone())
            {
                dir.clone()
            } else {
                let own = out.join(format!("lua-{}", case.key));
                kit::copy_dir(&runtime(), &own.join("temper-core"), &[]);
                // The shared libraries it imports, beside it there too
                // (checked where they're generated on their own).
                for library in kit::libraries(case, "lua") {
                    kit::write_files(
                        &own,
                        (generate(&library).iter()).map(|f| (f.path.as_str(), f.text.as_str())),
                    );
                }
                apart.push((case, own.clone()));
                own
            };
            for file in generate(case) {
                let path = here.join(&file.path);
                fs::create_dir_all(path.parent().unwrap()).unwrap();
                fs::write(&path, &file.text).unwrap();
                if file.path.ends_with(".lua") {
                    generated.push(path);
                }
            }
        }
        // std's output, for the libraries that import its modules, in a
        // directory of its own on their `LUA_PATH`.
        let std_dir = out.join("lua-std");
        if cases.iter().any(imports_std) {
            let std = translate(&kit::std_program(), &LUA).unwrap_or_else(|e| panic!("std: {e}"));
            for file in std {
                let path = std_dir.join(&file.path);
                fs::create_dir_all(path.parent().unwrap()).unwrap();
                fs::write(&path, &file.text).unwrap();
                generated.push(path);
            }
        }
        let std_path = format!(
            "{LUA_PATH};{0}/?.lua;{0}/?/init.lua",
            std_dir.to_string_lossy()
        );
        let lua_path = |case: &Case| match imports_std(case) {
            true => std_path.clone(),
            false => LUA_PATH.to_string(),
        };
        let mut failures = Vec::new();
        // Every library, in one process, but those that import another,
        // whose output they show only when they're the first to require
        // it, each in one of its own.
        let runner = out.join("runner.lua");
        fs::write(&runner, RUNNER).unwrap();
        let (cases, importers): (Vec<&Case>, Vec<&Case>) = cases
            .iter()
            .filter(|c| !apart.iter().any(|(a, _)| a.key == c.key))
            .partition(|c| c.libraries.is_empty() || kit::libraries(c, "lua").is_empty());
        let alone: Vec<(&Case, PathBuf)> = (importers.iter())
            .map(|&c| (c, dir.clone()))
            .chain(apart.iter().filter(|(c, _)| c.tests.is_none()).cloned())
            .collect();
        // Runs as tests, as Kotlin's `Lua51Specifics`: each tests file with
        // LuaUnit, `-o junit`, its failures compared with what's expected.
        for (case, here) in apart.iter().filter(|(c, _)| c.tests.is_some()) {
            kit::copy_dir(
                &PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime/luaunit"),
                &here.join("luaunit"),
                &[],
            );
            let mut files = Vec::new();
            kit::files_with(&here.join(&case.library).join("tests"), "lua", &mut files);
            for file in files {
                let results = here.join("test-results.xml");
                let _ = fs::remove_file(&results);
                let run = Command::new(&lua)
                    .args(["-e", "TEMPER_LUA_DEBUG_FUNCS=false"])
                    .arg(&file)
                    .args(["-o", "junit", "-n", "test-results.xml"])
                    .current_dir(here)
                    .env("LUA_PATH", LUA_PATH)
                    .output()
                    .unwrap();
                let actual = kit::junit_file_failures(&results);
                // Kotlin's pattern, `Test_\.test_(.*?)(?:__\d+)?`.
                let name = |n: &str| {
                    let n = n.strip_prefix("Test_.test_").unwrap_or(n);
                    kit::test_name_words(n)
                };
                kit::check_tests(&mut failures, case, actual, &run, name);
            }
        }
        for (case, here) in &alone {
            let mut command = match (&net, is_net(case)) {
                (Some(net), true) => {
                    let mut command = capped(&net.lua);
                    command
                        .env("LUA_PATH", format!("{};{}", lua_path(case), net.path))
                        .env("LUA_CPATH", &net.cpath);
                    command
                }
                _ => {
                    let mut command = capped(&lua);
                    command.env("LUA_PATH", lua_path(case));
                    command
                }
            };
            let run = command
                .arg(&runner)
                .arg(&case.library)
                .current_dir(here)
                .output()
                .unwrap();
            let stdout = kit::printed(&run.stdout);
            let mut log: Vec<&str> = stdout.lines().skip(1).collect();
            // A run expected to fail (`semantics/broken`) ends with the
            // runner's line for the error.
            let failed = log.last().is_some_and(|l| l.starts_with("!!> "));
            // One that must panic ends with its panic's message.
            let panicked = match &case.panic {
                Some(message) => log.last().is_some_and(|l| kit::ends_line(l, "", message)),
                None => true,
            };
            if case.fails && failed {
                log.pop();
            }
            if !run.status.success() || log != case.expected || case.fails != failed || !panicked {
                failures.push(format!(
                    "{}: expected {:?}, got {log:?}\n{}",
                    case.key,
                    case.expected,
                    String::from_utf8_lossy(&run.stderr)
                ));
            }
        }
        // The tests of `testing`, which need nothing of std's on Lua.
        let tests = out.join("tests.lua");
        fs::write(&tests, TESTS).unwrap();
        let run = Command::new(&lua)
            .arg(&tests)
            .arg("testing/tests/init-test.lua")
            .current_dir(&dir)
            .env("LUA_PATH", LUA_PATH)
            .output()
            .unwrap();
        let stdout = kit::printed(&run.stdout);
        // In LuaUnit's order, by name. (The shared `reach` program, which
        // runs as tests, checks the state tests share with their module.)
        let expected = "init 2\n\
            ok test_locals_a_test_assigns\n\
            ok test_names_punctuation_quotes_and_n_code\n\
            ok test_twice_works\n";
        if !run.status.success() || stdout != expected {
            failures.push(format!(
                "testing's tests: expected {expected:?}, got {stdout:?}\n{}",
                String::from_utf8_lossy(&run.stderr)
            ));
        }
        let run = Command::new(&lua)
            .arg(&runner)
            .args(cases.iter().map(|c| &c.library))
            .current_dir(&dir)
            .env("LUA_PATH", LUA_PATH)
            .output()
            .unwrap();
        let stdout = kit::printed(&run.stdout);
        let mut logs: Vec<(String, Vec<String>)> = Vec::new();
        for line in stdout.split('\n') {
            match line.strip_prefix("==> ") {
                Some(name) => logs.push((name.to_string(), Vec::new())),
                None => {
                    if let Some((_, log)) = logs.last_mut() {
                        log.push(line.to_string());
                    }
                }
            }
        }
        if let Some((_, log)) = logs.last_mut() {
            // The newline ending the output.
            if log.last().is_some_and(|l| l.is_empty()) {
                log.pop();
            }
        }
        if !run.status.success() || logs.len() != cases.len() {
            failures.push(format!(
                "the runner failed:\n{stdout}{}",
                String::from_utf8_lossy(&run.stderr)
            ));
        }
        for (case, (name, log)) in cases.iter().zip(&logs) {
            if *name != case.library || *log != case.expected {
                failures.push(format!(
                    "{}: expected {:?}, got {log:?}",
                    case.key, case.expected
                ));
            }
        }
        // One library as Kotlin runs a program.
        let alone = Command::new(&lua)
            .arg("fibonacci/init.lua")
            .current_dir(&dir)
            .env("LUA_PATH", LUA_PATH)
            .output()
            .unwrap();
        let expected = &cases[0].expected;
        let stdout = kit::printed(&alone.stdout);
        if !alone.status.success() || stdout.lines().collect::<Vec<_>>() != *expected {
            failures.push(format!(
                "lua fibonacci/init.lua: expected {expected:?}, got:\n{stdout}{}",
                String::from_utf8_lossy(&alone.stderr)
            ));
        }
        // Exported `var`s, as users see them.
        let live = out.join("live.lua");
        fs::write(&live, LIVE).unwrap();
        let read = Command::new(&lua)
            .arg(&live)
            .current_dir(&dir)
            .env("LUA_PATH", LUA_PATH)
            .output()
            .unwrap();
        let stdout = kit::printed(&read.stdout);
        let expected = "hi 1 of 3 11\nhi 2 of 3 13\nlocal 4\n2\t13\thi\tnil\n";
        if !read.status.success() || stdout != expected {
            failures.push(format!(
                "module-state's exports: expected {expected:?}, got {stdout:?}\n{}",
                String::from_utf8_lossy(&read.stderr)
            ));
        }
        // `members`' exports.
        let members = out.join("members.lua");
        fs::write(&members, MEMBERS_READ).unwrap();
        let read = Command::new(&lua)
            .arg(&members)
            .current_dir(&dir)
            .env("LUA_PATH", LUA_PATH)
            .output()
            .unwrap();
        let stdout = kit::printed(&read.stdout);
        let expected = "2 4 1\nfalse 2 7\nrex\n2\t7\tx\tBox(5)\n3\n";
        if !read.status.success() || stdout != expected {
            failures.push(format!(
                "members' exports: expected {expected:?}, got {stdout:?}\n{}",
                String::from_utf8_lossy(&read.stderr)
            ));
        }
        // Host code that uses `async-turns`, beside it, so LuaLS checks it.
        if let Some(case) = cases.iter().find(|c| c.key == "async-turns") {
            host_interop(&lua, &dir, &case.expected, &mut failures);
        }
        // Host code that plugs its own client into std `net`.
        let nets: Vec<&(&Case, PathBuf)> = apart.iter().filter(|(c, _)| is_net(c)).collect();
        if nets.len() == kit::NET.len() {
            host_net(&lua, &out, &nets, &std_dir, &mut failures);
        }
        if let Some(luac) = &luac {
            let parse = Command::new(luac)
                .arg("-p")
                .args(&generated)
                .output()
                .unwrap();
            if !parse.status.success() {
                failures.push(format!(
                    "luac -p:\n{}",
                    String::from_utf8_lossy(&parse.stderr)
                ));
            }
        }
        if let Some(luals) = &luals {
            failures.extend(check(luals, &out, &dir, None));
            // Host code is the library's own, inlined in its module, and
            // the functional tests' isn't typed for LuaLS (`length` takes
            // `string|nil`, where Kotlin, as this backend, passes
            // `temper.null`), so a library with host code isn't checked.
            // Nor is ill-typed code, which runs as written.
            let checked = |c: &Case| c.host_files.is_empty() && !kit::ill_typed(c);
            for (case, own) in apart.iter().filter(|(c, _)| checked(c)) {
                let std = imports_std(case).then_some(std_dir.as_path());
                failures.extend(check(luals, &out, own, std));
            }
        }
        kit::assert_none(&failures);
    });
}

/// Whether a case is one of std `net`'s tests ([`kit::NET`]).
fn is_net(case: &Case) -> bool {
    kit::NET.iter().any(|t| case.key == t.replace('/', "-"))
}

/// A Lua that loads LuaSocket's `socket.http`, std `net`'s default
/// client, through LuaRocks' paths (`luarocks path`), or else through
/// Lua's own (`;;`), where a system package puts it (as CI's Ubuntu
/// `lua-socket` does), with those paths.
struct NetLua {
    lua: String,
    path: String,
    cpath: String,
}

/// The Lua the tests run, if it loads LuaSocket, or else `lua` on
/// `PATH`: LuaRocks' rocks are for one Lua version, which LuaJIT, as CI
/// runs, may not be.
fn net_lua(lua: &str) -> Option<NetLua> {
    let rocks = |arg: &str| {
        let out = Command::new("luarocks").args(["path", arg]).output().ok()?;
        out.status
            .success()
            .then(|| String::from_utf8_lossy(&out.stdout).trim().to_string())
    };
    let rocks_paths = rocks("--lr-path").zip(rocks("--lr-cpath"));
    let own = (";;".to_string(), ";;".to_string());
    let paths: Vec<_> = rocks_paths.into_iter().chain([own]).collect();
    paths.iter().find_map(|(path, cpath)| {
        [lua, "lua"].into_iter().find_map(|lua| {
            let loads = Command::new(lua)
                .args(["-e", "require('socket.http')"])
                .env("LUA_PATH", path)
                .env("LUA_CPATH", cpath)
                .output()
                .is_ok_and(|o| o.status.success());
            loads.then(|| NetLua {
                lua: lua.to_string(),
                path: path.clone(),
                cpath: cpath.clone(),
            })
        })
    })
}

/// `tests/host/net.lua`, host code that plugs a client of its own into
/// std `net`, answering as the test server does from an I/O source the
/// runner waits on, then requires the `netresponse` tests' libraries, in
/// `nets`' directories, which log what the tests expect.
fn host_net(
    lua: &str,
    out: &Path,
    nets: &[&(&Case, PathBuf)],
    std_dir: &Path,
    failures: &mut Vec<String>,
) {
    let dir = out.join("lua-host-net");
    fs::create_dir_all(&dir).unwrap();
    let script = dir.join("net.lua");
    let source = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/host/net.lua");
    fs::copy(source, &script).unwrap();
    let mut path = Vec::new();
    for (_, here) in nets {
        let here = here.to_string_lossy();
        path.push(format!("{here}/?.lua;{here}/?/init.lua"));
    }
    let std = std_dir.to_string_lossy();
    path.push(format!("{std}/?.lua;{std}/?/init.lua"));
    let run = Command::new(lua)
        .arg(&script)
        .current_dir(&nets[0].1)
        .env("LUA_PATH", path.join(";"))
        .output()
        .unwrap();
    // What they log where requests reach a server, as `nets` may expect
    // Kotlin's failures.
    let expected: Vec<String> = (kit::functional_cases("lua", &kit::NET).into_iter())
        .filter(is_net)
        .flat_map(|c| c.expected)
        .chain(["requests 3".to_string()])
        .collect();
    kit::check_log(failures, "host net client", &run, &expected);
}

/// `tests/host/interop.lua`, host code that uses `async-turns` the
/// standard way, copied into `dir`, beside it: a block of its own awaits a
/// generated promise, an `await` outside any block fails clearly, and
/// `for … in` loops over a generated generator, which a failed step
/// leaves done.
fn host_interop(lua: &str, dir: &Path, logged: &[String], failures: &mut Vec<String>) {
    let script = dir.join("interop.lua");
    let source = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/host/interop.lua");
    fs::copy(source, &script).unwrap();
    let run = Command::new(lua)
        .arg(&script)
        .current_dir(dir)
        .env("LUA_PATH", LUA_PATH)
        .output()
        .unwrap();
    let expected: Vec<String> = (logged.iter().cloned())
        .chain(
            [
                "await 2",
                "settled outside true",
                "outside a block: true",
                "for in 0,1,2",
                "failed step true",
                "then done true",
                "fibonacci 0,1,1,2,3,5,8,13",
                "collatz 7,22,11,34,17,52,26,13,40,20,10,5,16,8,4,2,1",
            ]
            .map(String::from),
        )
        .collect();
    kit::check_log(failures, "host interop", &run, &expected);
}

/// Checks the generated libraries in `dir` with LuaLS, against the
/// runtime's typed view, and std's output in `std` as a library, which
/// LuaLS reads for its types but doesn't check: as Kotlin's, it calls
/// runtime functions the runtime lacks (`RegexFormatter`'s
/// `pushCodeTo`, `pushCaptureName`, and `adjustCodeSet`, only where Lua's
/// `regexFormat` never goes), or with more arguments than they take
/// (`compiledFind`'s `regexRefs`, `Test.bail()`'s test). Gives its
/// problems, if any.
fn check(luals: &str, out: &Path, dir: &Path, std: Option<&Path>) -> Vec<String> {
    let meta = out.join("luals-meta");
    fs::create_dir_all(&meta).unwrap();
    let view = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/luals/temper-core.lua");
    fs::copy(view, meta.join("temper-core.lua")).unwrap();
    let libraries: Vec<&Path> = [meta.as_path()].into_iter().chain(std).collect();
    fs::write(dir.join(".luarc.json"), luarc(&libraries)).unwrap();
    let logs = out.join("luals-logs");
    let check = Command::new(luals)
        .arg(format!("--check={}", dir.display()))
        .arg(format!(
            "--configpath={}",
            dir.join(".luarc.json").display()
        ))
        .arg("--checklevel=Warning")
        .arg("--locale=en-us")
        .arg(format!("--logpath={}", logs.display()))
        .current_dir(dir)
        .output()
        .unwrap();
    let stdout = String::from_utf8_lossy(&check.stdout);
    let report = fs::read_to_string(logs.join("check.json")).unwrap_or_default();
    // LuaLS says so when it finds nothing; otherwise it writes what it
    // found to `check.json` in its log directory.
    let empty = report
        .trim_matches(|c: char| c.is_whitespace() || "{}[]".contains(c))
        .is_empty();
    let said = stdout.to_lowercase();
    let clean = said.contains("no problems found")
        || (check.status.success() && empty && !said.contains("problem"));
    if clean {
        Vec::new()
    } else {
        vec![format!(
            "lua-language-server --check:\n{stdout}{}\n{report}",
            String::from_utf8_lossy(&check.stderr)
        )]
    }
}
