//! `temper repl`: reads Temper a line at a time and runs each complete
//! input in one [`ReplSession`], printing what it reports.
//!
//! On a terminal, lines are edited with rustyline ([`line`]): an input
//! over several lines is one buffer, with completion, a signature hint,
//! highlighting, and history ([`history`]). Otherwise, as for piped
//! input, lines are read plainly, without a prompt, so the output is
//! clean. Output is colored by `--color` ([`color`]).
//!
//! Input and output are UTF-8 whatever the console's code page: piped,
//! they're bytes, read and written as they are; on a Windows console,
//! Rust's std and rustyline use its UTF-16 calls.
//!
//! Ctrl-C stops the input running, through the interpreter's interrupt
//! check, and the session goes on; at the prompt, it drops the input
//! begun so far. If tempest itself is stuck, pressing Ctrl-C again ends
//! it (a third time on Unix). Ctrl-D, the end of input, runs what's
//! pending and exits with 0, whatever failed.

mod color;
mod history;
mod line;

use crate::{Flags, NOT_YET_CODE, USAGE, parse, parse_fuel};
use history::History;
use rustyline::error::ReadlineError;
use rustyline::history::MemHistory;
use rustyline::{
    ColorMode, CompletionType, Config, Editor, EventHandler, KeyCode, KeyEvent, Modifiers,
};
use std::cell::RefCell;
use std::io::{self, BufRead, IsTerminal, Write};
use std::path::PathBuf;
use std::rc::Rc;
use std::sync::atomic::{AtomicBool, Ordering};
use temper_frontend::project::{CONFIG_FILE, find_libraries_ignoring, work_root};
use temper_frontend::repl::{
    Chunker, ReplEvent, ReplOptions, ReplSession, ReplTools, Separator, escape_controls,
};
use temper_frontend::sources::Sources;

/// The most history entries kept.
const HISTORY_SIZE: usize = 1000;

/// `temper repl`.
pub fn repl_command(args: &[String]) -> u8 {
    let allowed = [
        "std",
        "workroot",
        "ignorefile",
        "backend",
        "exec-before",
        "separator",
        "prompt",
        "fuel",
        "verbose",
        "color",
        "no-history",
        "no-workroot",
    ];
    let flags = match parse("repl", args, &allowed, 0) {
        Ok(flags) => flags,
        Err(code) => return code,
    };
    if let Some(backend) = flags.backends.first() {
        eprintln!(
            "temper repl: -b {backend} isn't available yet (it would open the backend \
             language's own REPL with the work root's libraries loaded); the REPL runs \
             Temper on its interpreter."
        );
        return NOT_YET_CODE;
    }
    let separator = match flags.separator.as_deref().map(Separator::named) {
        None => Separator::Newline,
        Some(Some(separator)) => separator,
        Some(None) => {
            let value = flags.separator.as_deref().unwrap_or_default();
            eprintln!("temper repl: --separator is newline, semisemi, or eof, not {value}");
            return USAGE;
        }
    };
    let terminal = io::stdin().is_terminal();
    let prompt = match flags.prompt.as_deref() {
        None => terminal,
        Some("normal") => true,
        Some("hide") => false,
        Some(other) => {
            eprintln!("temper repl: --prompt is normal or hide, not {other}");
            return USAGE;
        }
    };
    let choice = match flags.color.as_deref().map(color::Choice::named) {
        None => color::Choice::Auto,
        Some(Some(choice)) => choice,
        Some(None) => {
            let value = flags.color.as_deref().unwrap_or_default();
            eprintln!("temper repl: --color is auto, always, or never, not {value}");
            return USAGE;
        }
    };
    let fuel = match flags.fuel.as_deref().map(parse_fuel) {
        None => None,
        Some(Ok(fuel)) => fuel,
        Some(Err(())) => {
            let value = flags.fuel.as_deref().unwrap_or_default();
            eprintln!("temper repl: --fuel is a number of steps above 0, or none, not {value}");
            return USAGE;
        }
    };
    let out_terminal = io::stdout().is_terminal();
    let env = |name: &str| std::env::var(name).ok();
    let colors = color::decide(
        choice,
        out_terminal && temper_os::console::enable_ansi(),
        &env,
    );
    // Line editing on a terminal, but not for `--separator eof`, where
    // all that's typed is one input.
    let editing = terminal && out_terminal && separator != Separator::Eof;
    let history = match editing && !flags.no_history {
        true => history::path(&env),
        false => None,
    };
    let sources = flags.sources();
    let libraries = match libraries(&flags, &sources) {
        Ok(libraries) => libraries,
        Err(code) => return code,
    };
    let interrupt = temper_os::interrupt::install();
    let tools = temper_driver::repl::ReplTools::default();
    let languages = tools.languages();
    let options = ReplOptions {
        sources,
        libraries,
        fuel,
        interrupt: Some(interrupt.clone()),
        hints: flags.verbose,
        tools: Some(Rc::new(tools)),
        help_footer: help_footer(editing, history.as_deref()),
    };
    // What an input logs is printed as is when piped or redirected, as
    // `temper run` prints it; on a terminal, escaped as the REPL's own
    // output is, so a log can't rewrite the screen, set the clipboard,
    // or hide text.
    let print = move |event: &ReplEvent| {
        let text = match event {
            ReplEvent::Log(line) if out_terminal => escape_controls(line),
            _ => color::paint(event, colors),
        };
        let mut out = io::stdout().lock();
        let _ = writeln!(out, "{text}");
        let _ = out.flush();
    };
    let mut session = ReplSession::new(options, Box::new(print));
    for code in &flags.exec {
        interrupt.store(false, Ordering::Relaxed);
        session.feed(code);
    }
    if editing {
        let session = Rc::new(RefCell::new(session));
        let editor = Editing {
            session,
            separator,
            prompt: if prompt { "$ " } else { "" },
            colors,
            languages,
            history,
        };
        if let Err(why) = editor.run(&interrupt) {
            eprintln!("temper repl: {why}");
            return crate::FAILED;
        }
        return 0;
    }
    read_inputs(&mut session, separator, prompt, &interrupt);
    0
}

/// What `help()` adds about the terminal: keys, history, and options.
fn help_footer(editing: bool, history: Option<&std::path::Path>) -> String {
    let mut out = String::from(
        "Keys: Enter runs the input once it's whole, or else starts a new line in it;\n\
         Tab completes names, members after a dot, and these functions' arguments;\n\
         Up and Down move between lines, then through history; Ctrl-R searches\n\
         history; Ctrl-C drops the input being typed, or stops the one running;\n\
         Ctrl-D on an empty line ends the session.\n",
    );
    if !editing {
        out = "Input isn't from a terminal, so lines aren't edited.\n".into();
    }
    out.push_str(&match history {
        Some(path) => format!("History: {}\n", path.display()),
        None => "History: none\n".into(),
    });
    out.push_str(
        "Options, as --color never, --no-history, and -w DIR: temper help repl\n\
         More: docs/tools/repl.md",
    );
    out
}

/// Line editing on a terminal ([`line`]).
struct Editing {
    session: Rc<RefCell<ReplSession>>,
    separator: Separator,
    prompt: &'static str,
    colors: bool,
    languages: Vec<String>,
    history: Option<PathBuf>,
}

impl Editing {
    /// Reads inputs until Ctrl-D, feeding each.
    fn run(self, interrupt: &AtomicBool) -> rustyline::Result<()> {
        let config = Config::builder()
            .max_history_size(HISTORY_SIZE)?
            .history_ignore_space(true)
            .auto_add_history(false)
            .completion_type(CompletionType::List)
            .color_mode(match self.colors {
                true => ColorMode::Forced,
                false => ColorMode::Disabled,
            })
            .build();
        let memory = MemHistory::with_config(&config);
        let mut editor: Editor<line::Line, MemHistory> = Editor::with_history(config, memory)?;
        // Ctrl-C while an input runs is the interpreter's again, as
        // rustyline has taken SIGINT for itself.
        temper_os::interrupt::install();
        let helper = line::Line::new(
            self.session.clone(),
            self.separator,
            self.colors,
            self.languages,
        );
        editor.set_helper(Some(helper));
        let indent = line::Indent {
            separator: self.separator,
        };
        let enter = KeyEvent(KeyCode::Enter, Modifiers::NONE);
        editor.bind_sequence(enter, EventHandler::Conditional(Box::new(indent)));
        // Read and written by tempest, through one checked descriptor;
        // rustyline's is in memory only.
        let opened = (self.history.as_deref()).map(|path| match History::open(path) {
            Ok(history) => Ok(history),
            Err(why) => Err(format!("no history in {}: {why}", path.display())),
        });
        let mut history = match opened {
            Some(Ok(mut history)) => match history.read(HISTORY_SIZE) {
                Ok(entries) => {
                    for entry in entries {
                        let _ = editor.add_history_entry(entry);
                    }
                    Some(history)
                }
                Err(why) => {
                    eprintln!("temper repl: history not read: {why}");
                    None
                }
            },
            Some(Err(why)) => {
                eprintln!("temper repl: {why}");
                None
            }
            None => None,
        };
        loop {
            match editor.readline(self.prompt) {
                Ok(buffer) => {
                    if buffer.trim().is_empty() {
                        continue;
                    }
                    let added = editor.add_history_entry(buffer.as_str());
                    if let (Some(file), Ok(true)) = (&mut history, added) {
                        if let Err(why) = file.append(&buffer) {
                            eprintln!("temper repl: history no longer kept: {why}");
                            history = None;
                        }
                    }
                    // Lines pasted together split into inputs as piped
                    // ones do.
                    let mut chunker = Chunker::new(self.separator);
                    let mut session = self.session.borrow_mut();
                    for line in buffer.split('\n') {
                        if let Some(input) = chunker.push_line(line) {
                            run(&mut session, &input, interrupt);
                        }
                    }
                    if let Some(input) = chunker.finish() {
                        run(&mut session, &input, interrupt);
                    }
                }
                // Drops what's typed.
                Err(ReadlineError::Interrupted) => continue,
                Err(ReadlineError::Eof) => {
                    println!();
                    return Ok(());
                }
                Err(why) => return Err(why),
            }
        }
    }
}

/// The libraries imports can name: those under `-w`'s work root, or
/// else the work root around the current directory, if it's in one;
/// none with `--no-workroot`.
fn libraries(flags: &Flags, sources: &Sources) -> Result<Vec<(String, String)>, u8> {
    if flags.no_workroot {
        if flags.workroot.is_some() || flags.ignorefile.is_some() {
            eprintln!("temper repl: --no-workroot leaves no work root for -w or -ign");
            return Err(USAGE);
        }
        return Ok(Vec::new());
    }
    let root = match &flags.workroot {
        Some(root) => root.clone(),
        None => {
            let root = work_root(sources, ".");
            let entries = sources.list(&root).unwrap_or_default();
            let marked = (entries.iter())
                .any(|e| e.name == CONFIG_FILE && !e.is_dir || e.name == "temper.out" && e.is_dir);
            if !marked {
                return Ok(Vec::new());
            }
            root
        }
    };
    let ignore = flags.ignore(sources, &root)?;
    let found = find_libraries_ignoring(sources, &root, ignore.as_ref());
    Ok((found.into_iter())
        .map(|l| (l.config.name, l.root))
        .collect())
}

/// Reads lines until the end of input, feeding each complete input.
fn read_inputs(
    session: &mut ReplSession,
    separator: Separator,
    prompt: bool,
    interrupt: &AtomicBool,
) {
    let mut chunker = Chunker::new(separator);
    let stdin = io::stdin();
    let mut reader = stdin.lock();
    let mut line = Vec::new();
    loop {
        if prompt {
            let mut out = io::stdout().lock();
            let _ = write!(out, "{}", if chunker.is_pending() { "  " } else { "$ " });
            let _ = out.flush();
        }
        line.clear();
        let read = reader.read_until(b'\n', &mut line);
        if matches!(read, Ok(0) | Err(_)) {
            break;
        }
        // Ctrl-C at the prompt drops what's begun.
        if interrupt.swap(false, Ordering::Relaxed) {
            temper_os::interrupt::reset();
            chunker.clear();
            continue;
        }
        let text = String::from_utf8_lossy(&line);
        if let Some(input) = chunker.push_line(&text) {
            run(session, &input, interrupt);
        }
    }
    if let Some(input) = chunker.finish() {
        run(session, &input, interrupt);
    }
    if prompt {
        println!();
    }
}

/// Feeds an input; a Ctrl-C while it runs is for it alone.
fn run(session: &mut ReplSession, input: &str, interrupt: &AtomicBool) {
    interrupt.store(false, Ordering::Relaxed);
    session.feed(input);
    interrupt.store(false, Ordering::Relaxed);
    temper_os::interrupt::reset();
}
