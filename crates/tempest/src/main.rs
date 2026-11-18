//! The `temper` command, a thin layer over the other crates, which do
//! the work as libraries: argument parsing and output. Its commands and
//! flags are Kotlin Temper's `temper`'s, where tempest has them
//! (docs/tools/cli.md):
//!
//!     temper build [-w DIR] [-ign FILE] [-o DIR] [-b BACKEND]... [--no-connected]
//!                   [-v] [--logformat text|json] [--std DIR]
//!     temper test [-w DIR | DIR] [-ign FILE] [--library NAME] [-b interp|BACKEND]...
//!                  [--fuel N|none] [--timeout S|none] [--no-connected] [-v]
//!                  [--logformat text|json] [--std DIR]
//!     temper run --library NAME -b interp|BACKEND [-w DIR] [-ign FILE] [-o DIR]
//!                 [--timeout S|none] [--no-connected] [-v] [--logformat text|json]
//!                 [--std DIR]
//!     temper repl [-w DIR] [-ign FILE] [-e CODE]... [--separator newline|semisemi|eof]
//!                  [--prompt normal|hide] [--fuel N|none] [-v] [--std DIR]
//!     temper init [-o DIR]
//!     temper serve|lsp [--std DIR]
//!     temper version | license | licenses | help [COMMAND]
//!
//! - `build` translates every library under the work root for each
//!   backend `-b` names, or Kotlin's defaults that tempest has (`csharp`,
//!   `java`, `js`, `lua`, `py`, and `rust`), writing
//!   `temper.out/BACKEND/LIBRARY/…` (or under `-o DIR`) as Kotlin's
//!   `temper build` does ([`temper_driver::build`]).
//! - `test` runs the libraries' tests on each backend `-b` names, in
//!   turn, or on the interpreter, by default or as `-b interp`
//!   ([`temper_frontend::library_tests`], [`temper_driver::test_run`]),
//!   printing failures (or with `-v`, every result) and Kotlin's summary,
//!   and with several backends, each one's summary and then the total.
//!   DIR is where the work root's search starts, as `-w`'s default
//!   starts from the current directory. On the interpreter, each test
//!   may take `--fuel` steps, [`temper_frontend::interp::TEST_FUEL`] by
//!   default, so a runaway test fails alone ("ran out of steps"). On a
//!   backend, each command its tools start may take `--timeout` seconds
//!   (or `TEMPER_TIMEOUT`'s), 600 by default, and dies with tempest
//!   ([`temper_tmpl::tools::output`]).
//! - `run` builds the work root for one backend, then runs a library's
//!   top level with that language's tools, or on the interpreter
//!   ([`temper_driver::run`]).
//! - `repl` reads Temper a line at a time and runs each input on the
//!   interpreter, against what earlier inputs declared, printing each
//!   result as Kotlin's does, `interactive#N: value` ([`repl`],
//!   [`temper_frontend::repl`]).
//! - `init` writes a new project's skeleton, as Kotlin's
//!   ([`temper_driver::init`]).
//! - `serve`, or `lsp`, serves an editor over stdio as a language server
//!   ([`temper_lsp`]); Kotlin's `--port` says that it's stdio only.
//! - `version`, `license`, and `licenses` are about tempest itself; the
//!   last lists the crates built into it, from a file that a test keeps
//!   current (`tests/licenses.rs`).
//! - `watch` and `doc`, `repl -b`, and Kotlin's backends `cppv`, `data`,
//!   `java8`, and `mypyc`, say they aren't in tempest yet.
//!
//! **Errors** don't stop any command, as in Kotlin Temper: every error is
//! reported, and each fails only where it runs (tmpl's `Error`), so
//! `build` still writes every backend's code, and `test` and `run` still
//! run, on the interpreter and on each backend, where the code with
//! errors fails only what reaches it. Each exits with 1 when there were
//! errors, as Kotlin's `temper build` and `temper test` do.
//!
//! Each backend is a cargo feature, all on by default ([`temper_driver`]);
//! one left out says so and fails.
//!
//! **Exit codes:** 0 on success, 1 on failure, 2 on a usage error, 3
//! for what isn't in tempest yet (a command, a backend, or `--port`), and
//! 101 for a bug in tempest, a panic, as Rust's own panics exit.

mod help;
mod repl;

use std::io::{self, Write};
use std::path::Path;
use std::process::ExitCode;
use std::sync::OnceLock;
use temper_driver::{NOT_YET, Registry, build, init, run, test_run};
use temper_frontend::ignore::IgnoreFile;
use temper_frontend::interp::TEST_FUEL;
use temper_frontend::library_tests::{Tally, TestOptions, test_libraries};
use temper_frontend::log::{LogFormat, task};
use temper_frontend::project::{ignore_file, work_root};
use temper_frontend::sources::Sources;
use temper_tmpl::Backend;
use temper_tmpl::tools;

/// Exit codes.
const FAILED: u8 = 1;
const USAGE: u8 = 2;
const NOT_YET_CODE: u8 = 3;
/// A panic, a bug in tempest, as Rust's own panics exit.
const PANICKED: u8 = 101;

fn main() -> ExitCode {
    // Runtimes, std, and core name this build's version, from git.
    temper_driver::version::init();
    // Calls and lowering recurse; give them room (see `interp`).
    let thread = std::thread::Builder::new().stack_size(64 << 20).spawn(|| {
        let args: Vec<String> = std::env::args().skip(1).collect();
        ExitCode::from(run_command(&args))
    });
    // Native builds unwind (the workspace's Cargo.toml), so a panic ends
    // the command's thread, its message printed by the panic hook, and
    // ends here, rather than aborting the process.
    match thread.map(|t| t.join()) {
        Ok(Ok(code)) => code,
        Ok(Err(_)) => {
            eprintln!(
                "temper: internal error (a bug in Temper, not in your code): the panic \
                 above stopped the command; please report it with what you ran."
            );
            ExitCode::from(PANICKED)
        }
        Err(why) => {
            eprintln!("temper: couldn't start: {why}");
            ExitCode::FAILURE
        }
    }
}

/// Runs a command, `args` not including the program's name, and returns
/// the exit code.
fn run_command(args: &[String]) -> u8 {
    let Some(name) = args.first() else {
        eprint!("{}", help::overview());
        return USAGE;
    };
    let rest = &args[1..];
    // `--help` or `-h` anywhere after a command asks for its help.
    if rest.iter().any(|a| a == "--help" || a == "-h") && help::command(name).is_some() {
        print!("{}", help::command(name).unwrap_or_default());
        return 0;
    }
    match name.as_str() {
        "build" => build_command(rest),
        "test" => test_command(rest),
        "run" => run_library(rest),
        "init" => init_command(rest),
        "lsp" | "serve" => serve(name, rest),
        "version" | "--version" => version(rest),
        "license" => about(name, rest, &license_text()),
        "licenses" => about(name, rest, LICENSES),
        "help" | "--help" | "-h" => help_command(rest),
        "repl" => repl::repl_command(rest),
        "watch" | "doc" => {
            eprintln!("{}", not_yet_command(name));
            NOT_YET_CODE
        }
        _ if temper_driver::KNOWN.contains(&name.as_str()) => {
            eprintln!(
                "temper: no command {name}; `temper build -b {name}` writes {name}'s code, \
                 and `-o DIR` puts it in DIR"
            );
            USAGE
        }
        _ => {
            eprintln!("temper: no command {name}");
            eprint!("{}", help::overview());
            USAGE
        }
    }
}

/// What a command that isn't in tempest yet says, with what to do
/// meanwhile.
pub fn not_yet_command(name: &str) -> String {
    let meanwhile = match name {
        "watch" => "meanwhile, run `temper build` or `temper test` after each change",
        _ => "meanwhile, a library's .temper.md files are its documentation",
    };
    format!("temper: {name} isn't available yet; {meanwhile} (docs/tools/cli.md).")
}

/// A command's options, as parsed: Kotlin's names, short forms, and the
/// values they take.
#[derive(Default)]
struct Flags {
    std_dir: Option<String>,
    workroot: Option<String>,
    ignorefile: Option<String>,
    output: Option<String>,
    backends: Vec<String>,
    library: Option<String>,
    no_connected: bool,
    verbose: bool,
    log: LogFormat,
    port: Option<String>,
    tokens: Option<String>,
    fuel: Option<String>,
    timeout: Option<String>,
    exec: Vec<String>,
    separator: Option<String>,
    prompt: Option<String>,
    color: Option<String>,
    no_history: bool,
    no_workroot: bool,
    positional: Vec<String>,
}

/// Each option: its long name, its short one if any, and whether it
/// takes a value.
const OPTIONS: [(&str, Option<&str>, bool); 19] = [
    ("std", None, true),
    ("workroot", Some("w"), true),
    ("ignorefile", Some("ign"), true),
    ("output-directory", Some("o"), true),
    ("backend", Some("b"), true),
    ("library", None, true),
    ("no-connected", None, false),
    ("verbose", Some("v"), false),
    ("logformat", None, true),
    ("port", None, true),
    ("tokens", None, true),
    ("fuel", None, true),
    ("timeout", None, true),
    ("exec-before", Some("e"), true),
    ("separator", None, true),
    ("prompt", None, true),
    ("color", None, true),
    ("no-history", None, false),
    ("no-workroot", None, false),
];

/// Parses a command's arguments, allowing the options in `allowed` (by
/// long name) and up to `positional` other arguments; or says what's
/// wrong and gives the usage error's exit code. Values follow their
/// options, or join them after `=` (`--workroot=DIR`).
fn parse(command: &str, args: &[String], allowed: &[&str], positional: usize) -> Result<Flags, u8> {
    let mut flags = Flags::default();
    let mut args = args.iter();
    let wrong = |what: String| {
        eprintln!("temper {command}: {what}");
        eprintln!("`temper help {command}` shows its options.");
        USAGE
    };
    while let Some(arg) = args.next() {
        let Some(flag) = arg.strip_prefix('-').filter(|f| !f.is_empty()) else {
            if flags.positional.len() == positional {
                return Err(wrong(format!("unexpected argument {arg}")));
            }
            flags.positional.push(arg.clone());
            continue;
        };
        let flag = flag.strip_prefix('-').unwrap_or(flag);
        let (flag, joined) = match flag.split_once('=') {
            Some((f, v)) => (f, Some(v.to_string())),
            None => (flag, None),
        };
        let option = OPTIONS
            .iter()
            .find(|(long, short, _)| *long == flag || *short == Some(flag))
            .filter(|(long, _, _)| allowed.contains(long));
        let Some(&(long, _, takes)) = option else {
            return Err(wrong(format!("unknown option {arg}")));
        };
        let value = match (takes, joined) {
            (true, Some(v)) => Some(v),
            (true, None) => match args.next() {
                Some(v) => Some(v.clone()),
                None => return Err(wrong(format!("--{long} needs a value"))),
            },
            (false, Some(_)) => return Err(wrong(format!("--{long} takes no value"))),
            (false, None) => None,
        };
        let value = value.unwrap_or_default();
        let once = |slot: &mut Option<String>| match slot.replace(value.clone()) {
            Some(_) => Err(wrong(format!("--{long} given twice"))),
            None => Ok(()),
        };
        match long {
            "std" => once(&mut flags.std_dir)?,
            "workroot" => once(&mut flags.workroot)?,
            "ignorefile" => once(&mut flags.ignorefile)?,
            "output-directory" => once(&mut flags.output)?,
            "library" => once(&mut flags.library)?,
            "port" => once(&mut flags.port)?,
            "tokens" => once(&mut flags.tokens)?,
            "fuel" => once(&mut flags.fuel)?,
            "timeout" => once(&mut flags.timeout)?,
            "separator" => once(&mut flags.separator)?,
            "prompt" => once(&mut flags.prompt)?,
            "color" => once(&mut flags.color)?,
            "exec-before" => flags.exec.push(value),
            "backend" => {
                if !flags.backends.contains(&value) {
                    flags.backends.push(value);
                }
            }
            "no-connected" => flags.no_connected = true,
            "no-history" => flags.no_history = true,
            "no-workroot" => flags.no_workroot = true,
            "verbose" => flags.verbose = true,
            "logformat" => match LogFormat::named(&value) {
                Some(format) => flags.log = format,
                None => return Err(wrong(format!("--logformat is text or json, not {value}"))),
            },
            _ => unreachable!("every option is matched"),
        }
    }
    Ok(flags)
}

impl Flags {
    fn sources(&self) -> Sources {
        match &self.std_dir {
            Some(std_dir) => Sources::with_std_dir(std_dir),
            None => Sources::standard(),
        }
    }

    /// The work root: `-w`'s, or else found from `from`, as Kotlin's
    /// default is from the current directory.
    fn root(&self, sources: &Sources, from: &str) -> String {
        match &self.workroot {
            Some(root) => root.clone(),
            None => work_root(sources, from),
        }
    }

    /// `--ignorefile`'s file, read, if given; or why it can't be.
    fn ignore(&self, sources: &Sources, root: &str) -> Result<Option<IgnoreFile>, u8> {
        let Some(path) = &self.ignorefile else {
            return Ok(None);
        };
        ignore_file(sources, root, path).map(Some).map_err(|why| {
            eprintln!("temper: --ignorefile {why}");
            FAILED
        })
    }
}

/// The backends this run has, built in.
fn registry() -> &'static Registry {
    static REGISTRY: OnceLock<Registry> = OnceLock::new();
    REGISTRY.get_or_init(Registry::built_in)
}

/// A backend by name: `None` for the interpreter, where `interp` is
/// allowed; or else the exit code, saying why: 1 for one this build was
/// built without, 3 for one of Kotlin's that tempest doesn't have yet,
/// and 2 for a name that isn't one.
fn backend(name: &str, interp: bool) -> Result<Option<&'static Backend>, u8> {
    match registry().get(name) {
        Some(backend) => Ok(Some(backend)),
        None if interp && name == "interp" => Ok(None),
        None if registry().lacks(name) => {
            eprintln!("temper: built without the {name} backend");
            Err(FAILED)
        }
        None if NOT_YET.contains(&name) => {
            eprintln!("temper: the {name} backend isn't available yet (docs/tools/cli.md).");
            Err(NOT_YET_CODE)
        }
        None => {
            let names: Vec<&str> = temper_driver::KNOWN.to_vec();
            let interp = if interp { ", or interp" } else { "" };
            eprintln!(
                "temper: no backend {name}; backends are {}{interp}",
                names.join(", ")
            );
            Err(USAGE)
        }
    }
}

/// Every backend named, in order, or the exit code.
fn backends(names: &[String], interp: bool) -> Result<Vec<Option<&'static Backend>>, u8> {
    names.iter().map(|n| backend(n, interp)).collect()
}

/// Ends a command, as Kotlin does in JSON with its task's record.
fn finish(log: LogFormat, label: &str, ok: bool) -> u8 {
    if log == LogFormat::Json {
        eprintln!("{}", task(label, ok));
    }
    if ok { 0 } else { FAILED }
}

/// `temper build`.
fn build_command(args: &[String]) -> u8 {
    let allowed = [
        "std",
        "workroot",
        "ignorefile",
        "output-directory",
        "backend",
        "no-connected",
        "verbose",
        "logformat",
    ];
    let flags = match parse("build", args, &allowed, 0) {
        Ok(flags) => flags,
        Err(code) => return code,
    };
    let chosen: Vec<&Backend> = match backends(&flags.backends, false) {
        Ok(chosen) => chosen.into_iter().flatten().collect(),
        Err(code) => return code,
    };
    let chosen = match chosen.is_empty() {
        true => registry().defaults(),
        false => chosen,
    };
    let sources = flags.sources();
    let root = flags.root(&sources, ".");
    let ignore = match flags.ignore(&sources, &root) {
        Ok(ignore) => ignore,
        Err(code) => return code,
    };
    let options = build::Options {
        verbose: flags.verbose,
        no_connected: flags.no_connected,
        out_dir: flags.output.as_deref().map(Path::new),
        ignore: ignore.as_ref(),
        log: flags.log,
        hints: flags.verbose,
    };
    let mut out = io::BufWriter::new(io::stdout().lock());
    let built = build::build(
        &sources,
        &root,
        &chosen,
        options,
        &mut out,
        &mut io::stderr(),
    );
    let _ = out.flush();
    finish(flags.log, "Build", built.is_some_and(|b| b.ok()))
}

/// `temper test`: each backend in turn, or the interpreter.
fn test_command(args: &[String]) -> u8 {
    let allowed = [
        "std",
        "workroot",
        "ignorefile",
        "library",
        "backend",
        "no-connected",
        "verbose",
        "logformat",
        "fuel",
        "timeout",
    ];
    let flags = match parse("test", args, &allowed, 1) {
        Ok(flags) => flags,
        Err(code) => return code,
    };
    if let Err(code) = set_limits("test", &flags) {
        return code;
    }
    let fuel = match flags.fuel.as_deref().map(parse_fuel) {
        None => Some(TEST_FUEL),
        Some(Ok(fuel)) => fuel,
        Some(Err(())) => {
            let value = flags.fuel.as_deref().unwrap_or_default();
            eprintln!("temper test: --fuel is a number of steps above 0, or none, not {value}");
            return USAGE;
        }
    };
    if flags.workroot.is_some() && !flags.positional.is_empty() {
        eprintln!("temper test: give -w or DIR, not both");
        return USAGE;
    }
    let chosen = match backends(&flags.backends, true) {
        Ok(chosen) if chosen.is_empty() => vec![None],
        Ok(chosen) => chosen,
        Err(code) => return code,
    };
    let sources = flags.sources();
    let from = flags.positional.first().map_or(".", String::as_str);
    let root = flags.root(&sources, from);
    let ignore = match flags.ignore(&sources, &root) {
        Ok(ignore) => ignore,
        Err(code) => return code,
    };
    let options = TestOptions {
        verbose: flags.verbose,
        library: flags.library.as_deref(),
        ignore: ignore.as_ref(),
        log: flags.log,
        fuel,
    };
    let build_options = build::Options {
        verbose: false,
        no_connected: flags.no_connected,
        out_dir: None,
        ignore: ignore.as_ref(),
        log: flags.log,
        hints: flags.verbose,
    };
    let json = flags.log == LogFormat::Json;
    let several = chosen.len() > 1;
    let (mut all_ok, mut total, mut stopped) = (true, Tally::default(), false);
    let mut out = io::BufWriter::new(io::stdout().lock());
    let mut err = io::stderr();
    for backend in chosen {
        let tested = match backend {
            None => test_libraries(&sources, &root, &options, &mut out, &mut err),
            Some(backend) => test_run::test_backend(
                &sources,
                &root,
                backend,
                &options,
                build_options,
                &mut out,
                &mut err,
            ),
        };
        // Nothing to test, the same for every backend, as said.
        let Some((ok, tally)) = tested else {
            (all_ok, stopped) = (false, true);
            break;
        };
        if !json {
            let name = backend.map_or("interp", |b| b.name);
            let _ = match several {
                true => writeln!(out, "{name}: {}", tally.summary()),
                false => writeln!(out, "{}", tally.summary()),
            };
        }
        let _ = out.flush();
        all_ok &= ok;
        total.sum(&tally);
    }
    match (json, several) {
        _ if stopped => {}
        (true, _) => eprintln!("{}", total.summary_record()),
        (false, true) => {
            let _ = writeln!(out, "{}", total.summary());
        }
        (false, false) => {}
    }
    let _ = out.flush();
    finish(flags.log, "Test", all_ok)
}

/// `--fuel`'s value: a number of steps above 0, with `_` or `,` between
/// digits allowed, or `none` for no limit.
fn parse_fuel(value: &str) -> Result<Option<u64>, ()> {
    if value == "none" {
        return Ok(None);
    }
    let digits: String = value.chars().filter(|c| !matches!(c, '_' | ',')).collect();
    match digits.parse::<u64>() {
        Ok(steps) if steps > 0 && value.starts_with(|c: char| c.is_ascii_digit()) => {
            Ok(Some(steps))
        }
        _ => Err(()),
    }
}

/// Sets the time limit of what backends' tools start, `--timeout`'s,
/// else `TEMPER_TIMEOUT`'s, and checks `TEMPER_MEMORY_LIMIT`; or says
/// what's wrong and gives the usage error's exit code.
fn set_limits(command: &str, flags: &Flags) -> Result<(), u8> {
    let wrong = |what: &str, why: String| {
        eprintln!("temper {command}: {what} is {why}");
        USAGE
    };
    match flags.timeout.as_deref() {
        Some(value) => {
            let limit = tools::parse_seconds(value).map_err(|why| wrong("--timeout", why))?;
            tools::set_timeout(limit);
        }
        None => {
            if let Ok(value) = std::env::var("TEMPER_TIMEOUT") {
                tools::parse_seconds(&value).map_err(|why| wrong("TEMPER_TIMEOUT", why))?;
            }
        }
    }
    if let Ok(value) = std::env::var("TEMPER_MEMORY_LIMIT") {
        tools::parse_mib(&value).map_err(|why| wrong("TEMPER_MEMORY_LIMIT", why))?;
    }
    Ok(())
}

/// `temper run --library NAME -b BACKEND`.
fn run_library(args: &[String]) -> u8 {
    let allowed = [
        "std",
        "library",
        "backend",
        "workroot",
        "ignorefile",
        "output-directory",
        "no-connected",
        "verbose",
        "logformat",
        "timeout",
    ];
    let flags = match parse("run", args, &allowed, 0) {
        Ok(flags) => flags,
        Err(code) => return code,
    };
    if let Err(code) = set_limits("run", &flags) {
        return code;
    }
    let Some(library) = flags.library.as_deref() else {
        eprintln!("temper run: --library NAME is required");
        return USAGE;
    };
    let backend = match flags.backends.as_slice() {
        [name] => match backend(name, true) {
            Ok(backend) => backend,
            Err(code) => return code,
        },
        [] => {
            eprintln!("temper run: -b BACKEND is required (interp for the interpreter)");
            return USAGE;
        }
        _ => {
            eprintln!("temper run: one -b BACKEND only");
            return USAGE;
        }
    };
    let sources = flags.sources();
    let root = flags.root(&sources, ".");
    let ignore = match flags.ignore(&sources, &root) {
        Ok(ignore) => ignore,
        Err(code) => return code,
    };
    let options = build::Options {
        verbose: flags.verbose,
        no_connected: flags.no_connected,
        out_dir: flags.output.as_deref().map(Path::new),
        ignore: ignore.as_ref(),
        log: flags.log,
        hints: flags.verbose,
    };
    let mut out = io::stdout();
    let ok = run::run(
        &sources,
        &root,
        library,
        backend,
        options,
        &mut out,
        &mut io::stderr(),
    );
    finish(flags.log, "Run", ok)
}

/// `temper init [-o DIR]`.
fn init_command(args: &[String]) -> u8 {
    let flags = match parse("init", args, &["output-directory"], 0) {
        Ok(flags) => flags,
        Err(code) => return code,
    };
    let dir = flags.output.as_deref().unwrap_or(".");
    match init::init(Path::new(dir)) {
        Ok(made) => {
            println!(
                "Initialized new Temper project \"{}\" in {}",
                made.name,
                made.dir.display()
            );
            0
        }
        Err(why) => {
            eprintln!("{why}");
            eprintln!("Init failed");
            FAILED
        }
    }
}

/// `temper serve` or `temper lsp`: a language server on stdin and
/// stdout.
fn serve(name: &str, args: &[String]) -> u8 {
    let flags = match parse(name, args, &["std", "port", "tokens"], 0) {
        Ok(flags) => flags,
        Err(code) => return code,
    };
    if let Some(tokens) = &flags.tokens {
        if !["add", "full", "none"].contains(&tokens.as_str()) {
            eprintln!("temper {name}: --tokens is add, full, or none, not {tokens}");
            return USAGE;
        }
    }
    if let Some(port) = &flags.port {
        eprintln!(
            "temper {name}: temper serves the language server over standard input and \
             output, not a TCP port; run `temper {name}` without --port {port}, with the \
             client talking over stdio."
        );
        return NOT_YET_CODE;
    }
    temper_lsp::serve(io::stdin(), io::stdout().lock(), flags.std_dir)
}

/// `temper version`: its version and the commit it was built from, with
/// a mark where the tree had changes: `temper 0.7.0-alpha.0.123
/// (3f2b063, modified)`.
fn version(args: &[String]) -> u8 {
    if let Err(code) = parse("version", args, &[], 0) {
        return code;
    }
    println!("temper {}", temper_driver::version::describe());
    0
}

/// `temper license` and `licenses`, which print a text.
fn about(name: &str, args: &[String], text: &str) -> u8 {
    if let Err(code) = parse(name, args, &[], 0) {
        return code;
    }
    print!("{text}");
    0
}

/// `temper help [COMMAND]`.
fn help_command(args: &[String]) -> u8 {
    match args {
        [] => {
            print!("{}", help::overview());
            0
        }
        [name] => match help::command(name) {
            Some(text) => {
                print!("{text}");
                0
            }
            None => {
                eprintln!("temper help: no command {name}");
                eprint!("{}", help::overview());
                USAGE
            }
        },
        _ => {
            eprintln!("temper help: one command at a time");
            USAGE
        }
    }
}

/// The third-party crates built into tempest and their licenses, which
/// `tests/licenses.rs` keeps current from Cargo's metadata.
const LICENSES: &str = include_str!("licenses.txt");

/// Tempest's copyright and licenses, as Kotlin's `temper license` prints
/// Temper's: each of the repository's legal files under a heading, with
/// NOTICE, which lists the third-party material in tempest's sources.
fn license_text() -> String {
    let files = [
        ("COPYRIGHT", include_str!("../../../COPYRIGHT")),
        ("NOTICE", include_str!("../../../NOTICE")),
        ("LICENSE-APACHE", include_str!("../../../LICENSE-APACHE")),
        ("LICENSE-MIT", include_str!("../../../LICENSE-MIT")),
        (
            "LICENSE-CC-BY-SA-4.0",
            include_str!("../../../LICENSE-CC-BY-SA-4.0"),
        ),
    ];
    let mut out = String::new();
    for (name, text) in files {
        if !out.is_empty() {
            out.push_str("\n\n");
        }
        out.push_str(&heading(name));
        out.push_str(text);
    }
    out
}

/// A name in a box of dashes, as Kotlin's license files head each part.
pub fn heading(name: &str) -> String {
    let text = format!("--- {name} ---");
    let line = "-".repeat(text.chars().count());
    format!("{line}\n{text}\n{line}\n")
}
