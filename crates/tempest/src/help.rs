//! What `temper help` prints: the commands, each with a line, as Kotlin
//! Temper's `temper help` lists them, and each command's options, with
//! Kotlin's names, short forms, and defaults.

/// Each command, alphabetically, with its line in `temper help`, and
/// whether tempest has it yet.
pub const COMMANDS: [(&str, &str, bool); 13] = [
    (
        "build",
        "Brings Temper libraries under the work root up to date.",
        true,
    ),
    (
        "doc",
        "Generates documentation for Temper libraries (not available yet).",
        false,
    ),
    ("help", "Prints help text for a command and exits.", true),
    (
        "init",
        "Generates initial contents for a Temper project.",
        true,
    ),
    (
        "license",
        "Displays the copyright and license attributions for the Temper toolchain and documentation.",
        true,
    ),
    (
        "licenses",
        "Displays dependencies and licenses bundled with them.",
        true,
    ),
    (
        "lsp",
        "Starts the language server over standard input and output (as serve).",
        true,
    ),
    ("repl", "Interactive shell for Temper code.", true),
    (
        "run",
        "Loads the named Temper-built library after processing libraries in the work root.",
        true,
    ),
    (
        "serve",
        "Starts the Temper language server, expecting to be called from a language client.",
        true,
    ),
    ("test", "Runs the tests with the configured backends.", true),
    ("version", "Reports Temper's version.", true),
    (
        "watch",
        "Like build, but keeps running, rebuilding as files change (not available yet).",
        false,
    ),
];

/// `temper help`, without a command.
pub fn overview() -> String {
    let mut out = String::from(
        "temper, Temper's command line tool.\n\
         \n\
         usage: temper COMMAND [OPTION]...\n\
         \n\
         Commands:\n",
    );
    for (name, line, _) in COMMANDS {
        out.push_str(&format!("    {name} - {line}\n"));
    }
    out.push_str("\n`temper help COMMAND` or `temper COMMAND --help` shows a command's options.\n");
    out
}

const WORKROOT: &str = "    -w, --workroot DIR
            Root directory to scan for Temper libraries; each
            config.temper.md under it starts one. By default, found by
            searching up from the current directory for the nearest
            directory holding a temper.out directory, or else the
            farthest holding a config.temper.md.
";

const IGNOREFILE: &str = "    -ign, --ignorefile FILE
            A file like .gitignore whose patterns, relative to its own
            directory, say what isn't a source file, with the last word
            over the .gitignore and .temperignore files that temper
            always reads.
";

const BACKEND: &str = "    -b, --backend BACKEND
            A backend; may be given more than once: cpp, csharp, java,
            js, lua, py, or rust.
";

const NO_CONNECTED: &str = "    --no-connected
            Use @connected functions' Temper bodies, where they have
            one, instead of connected code.
";

const VERBOSE: &str = "    -v, --verbose
            Say more, including hints: advice about correct code, such
            as an unnecessary type check, which editors show anyway.
";

const LOGFORMAT: &str = "    --logformat text|json
            text (the default) for people; json for one JSON record
            per line, as Kotlin's.
";

const TIMEOUT: &str = "    --timeout S|none
            On a backend, the seconds each command its tools start (a
            tests file, a build) may take, killed with what it started
            if it runs over; as TEMPER_TIMEOUT. none for no limit.
";

const STD: &str = "    --std DIR
            Read std from DIR instead of the copy built in.
";

use temper_frontend::interp::TEST_FUEL;

/// A command's help, or `None` for a command there isn't.
pub fn command(name: &str) -> Option<String> {
    let text = match name {
        "build" => format!(
            "usage: temper build [OPTION]...\n\
             \n\
             Brings Temper libraries under the work root up to date, writing\n\
             temper.out/BACKEND/LIBRARY for each backend.\n\
             \n\
             Options:\n\
             {WORKROOT}{IGNOREFILE}\
             \x20   -o, --output-directory DIR\n\
             \x20           Write each backend's directory under DIR instead of the\n\
             \x20           work root's temper.out.\n\
             {BACKEND}\
             \x20           Defaults to csharp, java, js, lua, py, and rust.\n\
             {NO_CONNECTED}{VERBOSE}{LOGFORMAT}{STD}"
        ),
        "test" => format!(
            "usage: temper test [OPTION]... [DIR]\n\
             \n\
             Runs the libraries' tests on each backend given, in turn, or on the\n\
             interpreter. DIR, if given, is where the work root's search starts.\n\
             \n\
             Options:\n\
             {WORKROOT}{IGNOREFILE}\
             \x20   --library NAME\n\
             \x20           Only this library's tests, by its dashed name, as foo-bar.\n\
             \x20   -b, --backend BACKEND\n\
             \x20           A backend, or interp for the interpreter; may be given\n\
             \x20           more than once. Defaults to interp.\n\
             \x20   --fuel N|none\n\
             \x20           On the interpreter, the steps each test may take, and the\n\
             \x20           top-level code before them (default {TEST_FUEL}); a test\n\
             \x20           that runs out fails alone. none for no limit.\n\
             {TIMEOUT}\
             \x20           Default 600.\n\
             {NO_CONNECTED}{VERBOSE}{LOGFORMAT}{STD}"
        ),
        "run" => format!(
            "usage: temper run --library NAME -b BACKEND [OPTION]...\n\
             \n\
             Builds the work root's libraries for the backend, then runs the named\n\
             library's top level with the backend's language, or on the interpreter\n\
             with -b interp.\n\
             \n\
             Options:\n\
             \x20   --library NAME\n\
             \x20           The library to run, by its dashed name (required).\n\
             \x20   -b, --backend BACKEND\n\
             \x20           cpp, csharp, java, js, lua, py, rust, or interp (required).\n\
             {WORKROOT}{IGNOREFILE}\
             \x20   -o, --output-directory DIR\n\
             \x20           As for build.\n\
             {TIMEOUT}\
             \x20           The program itself has none by default; each step that\n\
             \x20           builds it, 600.\n\
             {NO_CONNECTED}{VERBOSE}{LOGFORMAT}{STD}"
        ),
        "init" => "usage: temper init [-o DIR]\n\
             \n\
             Generates a new project: DIR/src/config.temper.md and a first source\n\
             file, DIR/src/NAME.temper.md, where NAME is DIR's name, dashed.\n\
             \n\
             Options:\n\
             \x20   -o, --output-directory DIR\n\
             \x20           New or empty directory in which to create the project;\n\
             \x20           defaults to the current directory.\n"
            .to_string(),
        "serve" | "lsp" => format!(
            "usage: temper {name} [--std DIR]\n\
             \n\
             Starts the Temper language server, talking to its client over standard\n\
             input and output. `serve` and `lsp` are the same.\n\
             \n\
             Options:\n\
             \x20   --port N\n\
             \x20           A TCP port to serve on; not available yet: the server talks\n\
             \x20           over standard input and output only, so it says so and stops.\n\
             \x20   --tokens add|full|none\n\
             \x20           Semantic tokens; none are sent yet, so all\n\
             \x20           three are as none.\n\
             {STD}"
        ),
        "help" => "usage: temper help [COMMAND]\n\
             \n\
             Prints the commands, or a command's options.\n"
            .to_string(),
        "version" => "usage: temper version\n\nReports Temper's version, the commit it was built from, and whether\nthat commit's files had changes, as in 1.2.0-alpha.0.34 (3f2b063, modified).\n".to_string(),
        "license" => "usage: temper license\n\n\
             Displays the copyright and license attributions for the Temper\n\
             toolchain and documentation.\n"
            .to_string(),
        "licenses" => "usage: temper licenses\n\n\
             Displays the third-party crates built into temper and their licenses.\n"
            .to_string(),
        "repl" => format!(
            "usage: temper repl [OPTION]...\n\
             \n\
             Reads Temper a line at a time, running each complete input on the\n\
             interpreter against what earlier inputs declared, and printing its\n\
             result as interactive#N: value. A line ends an input unless a\n\
             bracket, a multi-quoted string, or a block comment is still open.\n\
             Ctrl-C stops the input running; Ctrl-D ends the session.\n\
             \n\
             On a terminal, lines are edited, with history, Tab completion,\n\
             a signature hint, and highlighting. The REPL's own functions:\n\
             help() says what it offers, and help(\"name\") a declaration's\n\
             docs; describe(n, step) shows input n at one of the compiler's steps;\n\
             translate(n, lang) prints it translated; runTests() runs input\n\
             0's tests again, against the newest declarations\n\
             (docs/tools/repl.md).\n\
             \n\
             Inputs import std, the work root's libraries by name, and\n\
             directories relative to the current one, as import(\"./shapes\");\n\
             importing runs that code's top level. On a terminal, what's\n\
             logged is shown with control characters escaped.\n\
             \n\
             Options:\n\
             \x20   -w, --workroot DIR\n\
             \x20           The work root whose libraries inputs can import, by name.\n\
             \x20           By default, the work root around the current directory, as\n\
             \x20           for build, if there is one.\n\
             \x20   --no-workroot\n\
             \x20           No work root: inputs import only std, and paths relative to\n\
             \x20           the current directory.\n\
             {IGNOREFILE}\
             \x20   -e, --exec-before CODE\n\
             \x20           An input to run before reading any; may be given more than\n\
             \x20           once.\n\
             \x20   --separator newline|semisemi|eof\n\
             \x20           What ends an input: a line break outside brackets and strings\n\
             \x20           (the default), ;; at the end of a line, or the end of all\n\
             \x20           input.\n\
             \x20   --prompt normal|hide\n\
             \x20           Whether to prompt with $; by default, only when standard\n\
             \x20           input is a terminal.\n\
             \x20   --color auto|always|never\n\
             \x20           Whether to color output: by default, only on a terminal,\n\
             \x20           unless NO_COLOR is set, TERM is dumb, or CI is set.\n\
             \x20   --no-history\n\
             \x20           Neither read nor keep the history of what's typed, which\n\
             \x20           is otherwise kept, owner-only, in the platform's data\n\
             \x20           directory (temper/repl-history.txt).\n\
             \x20   --fuel N|none\n\
             \x20           The steps each input may take (default none).\n\
             \x20   -b, --backend BACKEND\n\
             \x20           A backend language's own REPL, with the work root's\n\
             \x20           libraries loaded; not available yet.\n\
             {VERBOSE}{STD}"
        ),
        "watch" | "doc" => format!("{}\n", crate::not_yet_command(name)),
        _ => return None,
    };
    Some(text)
}
