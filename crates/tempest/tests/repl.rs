//! Tests of `temper repl` as a command: piped transcripts, its flags,
//! the work root's libraries, and Ctrl-C. What a session prints for
//! each input is tested in temper-frontend's `tests/repl.rs`; the
//! tutorial's transcripts, in `tests/repl_docs.rs`.

use std::fs;
use std::io::{Read, Write};
use std::path::Path;
use std::process::{Command, Stdio};

/// Runs `temper repl` with `args`, in `dir` if given, piping `input` in;
/// gives its exit code, stdout and stderr.
fn repl(args: &[&str], dir: Option<&Path>, input: &str) -> (i32, String, String) {
    let mut command = Command::new(env!("CARGO_BIN_EXE_temper"));
    command.arg("repl").args(args);
    if let Some(dir) = dir {
        command.current_dir(dir);
    }
    let mut child = (command.stdin(Stdio::piped()))
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .unwrap();
    child
        .stdin
        .take()
        .unwrap()
        .write_all(input.as_bytes())
        .unwrap();
    let output = child.wait_with_output().unwrap();
    let text = |b: Vec<u8>| String::from_utf8(b).unwrap();
    (
        output.status.code().unwrap(),
        text(output.stdout),
        text(output.stderr),
    )
}

/// A fresh directory, removed after `f`.
fn with_dir(test: &str, f: impl FnOnce(&Path)) {
    let dir = std::env::temp_dir().join(format!("tempest-repl-{}-{test}", std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    fs::create_dir_all(&dir).unwrap();
    f(&dir);
    let _ = fs::remove_dir_all(&dir);
}

/// Piped input gives each result, and nothing else: no prompts, and no
/// indentation echoed (Kotlin's REPL echoed its auto-indent into piped
/// output and into the input, shifting snippets' columns).
#[test]
fn piped_input_prints_only_results() {
    let input = "let f(a: Int): Int {\n  if (a > 1) {\n    a * f(a - 1)\n  } else {\n    1\n  }\n}\nf(5)\n  nope\n";
    let (code, out, err) = repl(&[], None, input);
    assert_eq!((code, err.as_str()), (0, ""));
    assert_eq!(
        out,
        "interactive#0: void\n\
         interactive#1: 120\n\
         interactive#2:1:3: error: nope has not been declared\n    \
         \x20 nope\n    \
         \x20 ^^^^\n\
         interactive#2: fail\n"
    );
}

/// `--prompt normal` prompts with `$ `, and `  ` while an input goes on;
/// the end of input exits with 0, whatever failed.
#[test]
fn prompts() {
    let (code, out, _) = repl(&["--prompt", "normal"], None, "[1,\n2]\npanic()\n");
    assert_eq!(code, 0);
    assert_eq!(
        out,
        "$   interactive#0: [1,2]\n$ Panicked: panic()\ninteractive#1: fail\n$ \n"
    );
    let (code, out, _) = repl(&["--prompt", "hide"], None, "1\n");
    assert_eq!((code, out.as_str()), (0, "interactive#0: 1\n"));
}

/// Input that ends partway still runs, as Ctrl-D runs what's pending.
#[test]
fn the_end_of_input_runs_what_is_pending() {
    let (code, out, _) = repl(&[], None, "[1,\n2]");
    assert_eq!(code, 0);
    assert_eq!(out, "interactive#0: [1,2]\n");
    let (code, out, _) = repl(&[], None, "[1,\n2");
    assert_eq!(code, 0);
    assert!(out.ends_with("interactive#0: 2\n"), "{out}");
}

/// `-e` runs inputs first; `--separator` chooses what ends an input.
#[test]
fn flags() {
    let (_, out, _) = repl(
        &["-e", "let x = 2", "--exec-before", "let y = 3"],
        None,
        "x * y\n",
    );
    assert_eq!(
        out,
        "interactive#0: void\ninteractive#1: void\ninteractive#2: 6\n"
    );
    let input = "let ev(n: Int): Boolean { n == 0 || od(n - 1) }\n\
                 let od(n: Int): Boolean { n != 0 && ev(n - 1) } ;;\n\
                 od(3) ;;\n";
    let (_, out, _) = repl(&["--separator", "semisemi"], None, input);
    assert_eq!(out, "interactive#0: void\ninteractive#1: true\n");
    let (_, out, _) = repl(&["--separator", "eof"], None, "let a = 1;\na + 1\n");
    assert_eq!(out, "interactive#0: 2\n");
    for args in [
        &["--separator", "comma"][..],
        &["--prompt", "loud"],
        &["--fuel", "0"],
        &["extra"],
    ] {
        assert_eq!(repl(args, None, "").0, 2, "{args:?}");
    }
    let (_, out, _) = repl(&["--fuel", "1000"], None, "while (true) {}\n1\n");
    assert_eq!(
        out,
        "Panicked: ran out of steps\ninteractive#0: fail\ninteractive#1: 1\n"
    );
}

/// UTF-8 piped in comes out as the same UTF-8 bytes, on every platform:
/// names in other scripts (Latin with diacritics, CJK), and values with
/// an emoji with a skin tone, combining marks, and CJK, in results and
/// in a diagnostic's quoted line. Piped input and output are bytes, so
/// no console code page (Windows') converts them.
#[test]
fn utf8_input() {
    let input = "let näme = \"😀 e\u{301} 日本\"\nnäme\nnäme.split(\" \").length\n\
                 let 名前 = \"👋🏽 e\u{301} 日本語\"\n\
                 let café = 名前.split(\" \")\n\
                 café.length\n\
                 名前\n\
                 let x = 名前 + nope\n";
    let mut child = Command::new(env!("CARGO_BIN_EXE_temper"))
        .arg("repl")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .unwrap();
    let mut stdin = child.stdin.take().unwrap();
    stdin.write_all(input.as_bytes()).unwrap();
    drop(stdin);
    let output = child.wait_with_output().unwrap();
    let expected = "interactive#0: void\n\
                    interactive#1: \"😀 e\u{301} 日本\"\n\
                    interactive#2: 3\n\
                    interactive#3: void\n\
                    interactive#4: void\n\
                    interactive#5: 3\n\
                    interactive#6: \"👋🏽 e\u{301} 日本語\"\n\
                    interactive#7:1:14: error: nope has not been declared\n    \
                    let x = 名前 + nope\n    \
                    \x20            ^^^^\n\
                    interactive#7: fail\n";
    // Bytes, not text, so a stray conversion can't be decoded away.
    assert_eq!(
        output.stdout,
        expected.as_bytes(),
        "{}",
        String::from_utf8_lossy(&output.stdout)
    );
    assert_eq!(output.stderr, b"");
    assert!(output.status.success());
}

/// Inputs import the work root's libraries by name: the work root around
/// the current directory by default, or `-w`'s.
#[test]
fn imports_from_the_work_root() {
    with_dir("workroot", |dir| {
        let lib = dir.join("lib");
        fs::create_dir_all(&lib).unwrap();
        fs::write(
            lib.join("config.temper.md"),
            "    export let name = \"greeting\";\n",
        )
        .unwrap();
        fs::write(
            lib.join("greet.temper"),
            "export let greet(who: String): String { \"Hello, ${who}!\" }\n",
        )
        .unwrap();
        let input = "let { greet } = import(\"greeting\");\ngreet(\"REPL\")\n";
        let expected = "interactive#0: void\ninteractive#1: \"Hello, REPL!\"\n";
        let (_, out, _) = repl(&[], Some(&lib), input);
        assert_eq!(out, expected);
        let (_, out, _) = repl(&["-w", &dir.to_string_lossy()], None, input);
        assert_eq!(out, expected);
        // Outside a work root, there are none; nor with --no-workroot.
        let (_, out, _) = repl(&[], Some(&std::env::temp_dir()), input);
        assert!(
            out.contains("error: Not supported yet: importing greeting"),
            "{out}"
        );
        let (code, out, _) = repl(&["--no-workroot"], Some(&lib), input);
        assert_eq!(code, 0);
        assert!(
            out.contains("error: Not supported yet: importing greeting"),
            "{out}"
        );
        let (code, _, err) = repl(&["--no-workroot", "-w", "."], None, "");
        assert_eq!(code, 2, "{err}");
    });
}

/// A relative import is of a directory relative to the current one, as
/// if the inputs were there, not to the inputs' own directory, which is
/// never read; it runs that module's top level.
#[test]
fn relative_imports_from_the_current_directory() {
    with_dir("relative", |dir| {
        let write = |path: &str, text: &str| {
            let path = dir.join(path);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(path, text).unwrap();
        };
        write(
            "shapes/shapes.temper",
            "console.log(\"ran shapes\");\nexport let sides = 4;\n",
        );
        write("-repl-/shapes/shapes.temper", "export let sides = 99;\n");
        write("work/inner.temper", "export let inner = 2;\n");
        let input = "let { sides } = import(\"./shapes\")\nsides\n";
        let (code, out, err) = repl(&[], Some(dir), input);
        assert_eq!((code, err.as_str()), (0, ""));
        assert_eq!(out, "ran shapes\ninteractive#0: void\ninteractive#1: 4\n");
        // From a directory below, `..` goes up from the current one.
        let input = "let { sides } = import(\"../shapes\")\nlet { inner } = import(\"./\")\nsides + inner\n";
        let (_, out, _) = repl(&[], Some(&dir.join("work")), input);
        assert!(out.ends_with("interactive#2: 6\n"), "{out}");
    });
}

/// Ctrl-C (SIGINT) stops the input running, not the session.
#[cfg(unix)]
#[test]
fn ctrl_c_stops_the_input_not_the_session() {
    use std::time::{Duration, Instant};
    // `-e` runs once Ctrl-C is handled; until then, SIGINT would end it.
    let mut child = Command::new(env!("CARGO_BIN_EXE_temper"))
        .args(["repl", "-e", "console.log(\"ready\")"])
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .spawn()
        .unwrap();
    let mut stdin = child.stdin.take().unwrap();
    let mut stdout = child.stdout.take().unwrap();
    // What it prints, read on a thread, so the test can wait for it.
    let (send, receive) = std::sync::mpsc::channel();
    let reader = std::thread::spawn(move || {
        let mut buffer = [0; 256];
        while let Ok(n) = stdout.read(&mut buffer) {
            if n == 0 || send.send(buffer[..n].to_vec()).is_err() {
                break;
            }
        }
    });
    let mut out = Vec::new();
    let start = Instant::now();
    while !String::from_utf8_lossy(&out).contains("ready\ninteractive#0: void\n") {
        assert!(start.elapsed() < Duration::from_secs(60), "not ready");
        if let Ok(bytes) = receive.recv_timeout(Duration::from_millis(100)) {
            out.extend(bytes);
        }
    }
    out.clear();
    stdin
        .write_all(b"var i = 0; while (true) { i += 1 }\n")
        .unwrap();
    stdin.flush().unwrap();
    std::thread::sleep(Duration::from_millis(200));
    // A SIGINT, then another every two seconds, in case the first came
    // before the loop began, until the input says it failed.
    while !String::from_utf8_lossy(&out).contains("interactive#1: fail") {
        assert!(
            start.elapsed() < Duration::from_secs(60),
            "no interrupt: {}",
            String::from_utf8_lossy(&out)
        );
        let pid = child.id().to_string();
        let killed = Command::new("kill").args(["-INT", &pid]).status().unwrap();
        assert!(killed.success());
        let wait = Instant::now();
        while wait.elapsed() < Duration::from_secs(2) {
            match receive.recv_timeout(Duration::from_millis(100)) {
                Ok(bytes) => out.extend(bytes),
                Err(_) if String::from_utf8_lossy(&out).contains("interactive#1: fail") => break,
                Err(_) => {}
            }
        }
    }
    stdin.write_all(b"i > 0\n1 + 1\n").unwrap();
    drop(stdin);
    let status = child.wait().unwrap();
    reader.join().unwrap();
    out.extend(receive.try_iter().flatten());
    assert!(status.success());
    assert_eq!(
        String::from_utf8_lossy(&out),
        "Interrupted\ninteractive#1: fail\ninteractive#2: true\ninteractive#3: 2\n"
    );
}

/// On Windows, Ctrl-Break stops the input running, not the session, as
/// Ctrl-C does: Ctrl-C can't be sent to one process alone, but
/// Ctrl-Break can, to a console process group of its own.
#[cfg(windows)]
#[test]
fn ctrl_break_stops_the_input_not_the_session() {
    use std::os::windows::process::CommandExt;
    use std::time::{Duration, Instant};
    const CREATE_NEW_PROCESS_GROUP: u32 = 0x0000_0200;
    let mut child = Command::new(env!("CARGO_BIN_EXE_temper"))
        .args(["repl", "-e", "console.log(\"ready\")"])
        .creation_flags(CREATE_NEW_PROCESS_GROUP)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .spawn()
        .unwrap();
    let mut stdin = child.stdin.take().unwrap();
    let mut stdout = child.stdout.take().unwrap();
    let (send, receive) = std::sync::mpsc::channel();
    let reader = std::thread::spawn(move || {
        let mut buffer = [0; 256];
        while let Ok(n) = stdout.read(&mut buffer) {
            if n == 0 || send.send(buffer[..n].to_vec()).is_err() {
                break;
            }
        }
    });
    let start = Instant::now();
    let mut out = Vec::new();
    let wait_for = |text: &str, out: &mut Vec<u8>| {
        while !String::from_utf8_lossy(out).contains(text) {
            let shown = String::from_utf8_lossy(out).to_string();
            assert!(
                start.elapsed() < Duration::from_secs(60),
                "no {text:?} in {shown:?}"
            );
            if let Ok(bytes) = receive.recv_timeout(Duration::from_millis(50)) {
                out.extend(bytes);
            }
        }
    };
    // `-e` runs once the handler is in.
    wait_for("ready\ninteractive#0: void\n", &mut out);
    out.clear();
    // Sent once the loop has begun, and only once, as a second press
    // before the first is handled ends tempest.
    stdin
        .write_all(b"console.log(\"looping\"); var i = 0; while (true) { i += 1 }\n")
        .unwrap();
    stdin.flush().unwrap();
    wait_for("looping\n", &mut out);
    let sent = temper_os::console::send_ctrl_break(child.id());
    assert!(
        sent,
        "no Ctrl-Break sent: {}",
        std::io::Error::last_os_error()
    );
    wait_for("interactive#1: fail\n", &mut out);
    stdin.write_all(b"i > 0\n1 + 1\n").unwrap();
    drop(stdin);
    let status = child.wait().unwrap();
    reader.join().unwrap();
    out.extend(receive.try_iter().flatten());
    assert!(status.success(), "{status}");
    assert_eq!(
        String::from_utf8_lossy(&out),
        "looping\nInterrupted\ninteractive#1: fail\ninteractive#2: true\ninteractive#3: 2\n"
    );
}

/// `--color`: `auto` colors only a terminal's output, so piped output is
/// plain; `always` colors it whatever `NO_COLOR` says; `never` doesn't.
#[test]
fn color() {
    let colored =
        "\x1b[2minteractive#0:\x1b[0m 1\n\x1b[2minteractive#1:\x1b[0m \x1b[31mfail\x1b[0m\n";
    let plain = "interactive#0: 1\ninteractive#1: fail\n";
    for (args, expected) in [
        (&[][..], plain),
        (&["--color", "auto"], plain),
        (&["--color", "never"], plain),
        (&["--color", "always"], colored),
    ] {
        let (code, out, _) = repl(args, None, "1\n1 / 0\n");
        assert_eq!((code, out.as_str()), (0, expected), "{args:?}");
    }
    let mut child = Command::new(env!("CARGO_BIN_EXE_temper"))
        .args(["repl", "--color", "always"])
        .env("NO_COLOR", "1")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .spawn()
        .unwrap();
    child
        .stdin
        .take()
        .unwrap()
        .write_all(b"1\n1 / 0\n")
        .unwrap();
    let output = child.wait_with_output().unwrap();
    assert_eq!(String::from_utf8(output.stdout).unwrap(), colored);
    assert_eq!(repl(&["--color", "sometimes"], None, "").0, 2);
}

/// The REPL's own functions, through the command: `help()` says what
/// input isn't from a terminal means, and `translate` shows code, of
/// what's reached (an export).
#[test]
fn own_functions() {
    let input = "help()\nexport let x = 1\ntranslate(1, \"py\")\n";
    let (code, out, _) = repl(&[], None, input);
    assert_eq!(code, 0);
    assert!(
        out.contains("Input isn't from a terminal, so lines aren't edited."),
        "{out}"
    );
    assert!(out.contains("History: none"), "{out}");
    if cfg!(feature = "py") {
        assert!(
            out.contains("# interactive/interactive/interactive.py\n"),
            "{out}"
        );
        assert!(out.contains("\nx: int = 1\ninteractive#2: void\n"), "{out}");
    }
}

/// Printed values and diagnostics can't move the terminal's cursor or
/// reorder text: control characters, C1 controls, and bidirectional
/// controls are escaped.
#[test]
fn control_characters_are_escaped() {
    let input = "\"\\u{1b}[2J\\u{9b}\\u{202e}\"\nlet a = \"\x1b[31m\" + nope\n";
    let (_, out, _) = repl(&[], None, input);
    assert!(!out.contains(['\x1b', '\u{9b}', '\u{202e}']), "{out:?}");
    assert!(out.contains("\"\\u{1b}[2J\\u{9b,202e}\""), "{out}");
    assert!(out.contains("let a = \"\\u{1b}[31m\" + nope"), "{out}");
    // What's logged is written as is when piped, as `temper run` does;
    // on a terminal it's escaped (`edits_lines_on_a_terminal`).
    let (_, out, _) = repl(&[], None, "console.log(\"\\u{1b}[1mbold\")\n");
    assert_eq!(out, "\x1b[1mbold\ninteractive#0: void\n");
}

/// Terminal output as text: escape sequences (CSI, as colors, bracketed
/// paste's `?2004h`, and synchronized output's `?2026h`; OSC; and others)
/// and carriage returns dropped, so tests match what's shown, not how
/// it's drawn.
#[cfg(target_os = "linux")]
fn shown(bytes: &[u8]) -> String {
    let text = String::from_utf8_lossy(bytes);
    let mut out = String::new();
    let mut chars = text.chars().peekable();
    while let Some(c) = chars.next() {
        match c {
            '\x1b' => match chars.next() {
                Some('[') => {
                    // Parameters, then a final byte in @ to ~.
                    while chars.next().is_some_and(|c| !('@'..='~').contains(&c)) {}
                }
                Some(']') => {
                    // To BEL, or ESC \.
                    while let Some(c) = chars.next() {
                        if c == '\x07' || c == '\x1b' && chars.next_if_eq(&'\\').is_some() {
                            break;
                        }
                    }
                }
                _ => {}
            },
            '\r' => {}
            c => out.push(c),
        }
    }
    out
}

/// On a terminal, as `script` gives one: an input over lines, indented as
/// it goes on, a signature hint, Ctrl-C at the prompt dropping what's
/// typed, a multi-quoted string typed as it would be piped, logs escaped,
/// and history kept owner-only, a multi-line input as one entry. Each
/// key waits for what it answers to be shown, so it holds on a slow,
/// loaded machine: keys sent before the editor is ready are read by the
/// terminal in its ordinary mode instead.
#[cfg(target_os = "linux")]
#[test]
fn edits_lines_on_a_terminal() {
    use std::os::unix::fs::PermissionsExt;
    use std::time::{Duration, Instant};
    let has_script = Command::new("script").arg("--version").output();
    if !has_script.is_ok_and(|o| o.status.success()) {
        eprintln!("skipped: no `script` here to run tempest on a terminal");
        return;
    }
    with_dir("terminal", |dir| {
        let command = format!("{} repl --color never", env!("CARGO_BIN_EXE_temper"));
        let mut child = Command::new("script")
            .args(["-qfec", &command, "/dev/null"])
            .env("XDG_DATA_HOME", dir)
            .env("TERM", "xterm")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()
            .unwrap();
        let mut stdin = child.stdin.take().unwrap();
        let mut stdout = child.stdout.take().unwrap();
        let (send, receive) = std::sync::mpsc::channel();
        let reader = std::thread::spawn(move || {
            let mut buffer = [0; 1024];
            while let Ok(n) = stdout.read(&mut buffer) {
                if n == 0 || send.send(buffer[..n].to_vec()).is_err() {
                    break;
                }
            }
        });
        let mut out: Vec<u8> = Vec::new();
        // Waits until what's shown since byte `from` of the output
        // contains each of `texts`, in order; gives where the output then
        // ends.
        let mut wait_for = |from: usize, texts: &[&str]| -> usize {
            let start = Instant::now();
            loop {
                let since = shown(&out[from.min(out.len())..]);
                let mut at = 0;
                let found = texts.iter().all(|text| match since[at..].find(text) {
                    Some(i) => {
                        at += i + text.len();
                        true
                    }
                    None => false,
                });
                if found {
                    break out.len();
                }
                assert!(
                    start.elapsed() < Duration::from_secs(120),
                    "no {texts:?} in {since:?}"
                );
                if let Ok(bytes) = receive.recv_timeout(Duration::from_millis(50)) {
                    out.extend(bytes);
                }
            }
        };
        let mut send_keys = |keys: &str| {
            stdin.write_all(keys.as_bytes()).unwrap();
            stdin.flush().unwrap();
        };
        let at = wait_for(0, &["$ "]);
        send_keys("let greet(name: String): String { \"hi ${name}\" }\r");
        // The next keys only once the result and the next prompt are up.
        let at = wait_for(at, &["interactive#0: void", "$ "]);
        // Ctrl-C drops what's typed.
        send_keys("1 +");
        let at = wait_for(at, &["1 +"]);
        send_keys("\x03");
        // The line given up, and a new prompt.
        let at = wait_for(at, &["\n$ "]);
        send_keys("greet(");
        // The hint, after what's typed.
        let at = wait_for(at, &["greet(  greet(name: String): String"]);
        send_keys("\r");
        // A new line, indented, in the same input.
        let at = wait_for(at, &["greet(\n    "]);
        send_keys("\"you\")\r");
        let at = wait_for(at, &["interactive#1: \"hi you\"", "$ "]);
        // A multi-quoted string, ended by Enter on the line Enter
        // indented, is what it would be piped: no space at its end.
        send_keys("let s = \"\"\"\r");
        let at = wait_for(at, &["\"\"\"\n  "]);
        send_keys("\"two\r");
        let at = wait_for(at, &["\"two\n  "]);
        send_keys("\r");
        let at = wait_for(at, &["interactive#2: void", "$ "]);
        send_keys("s\r");
        let at = wait_for(at, &["interactive#3: \"two\"", "$ "]);
        // What's logged is escaped on a terminal.
        let raw = at;
        send_keys("console.log(\"\\u{1b}]52;c;SGVsbG8=\\u{7}after\")\r");
        wait_for(at, &["interactive#4: void", "$ "]);
        let logged = String::from_utf8_lossy(&out[raw..]).to_string();
        assert!(!logged.contains("\x1b]52"), "{logged:?}");
        assert!(
            logged.contains("\\u{1b}]52;c;SGVsbG8=\\u{7}after"),
            "{logged:?}"
        );
        send_keys("\x04");
        drop(stdin);
        let status = child.wait().unwrap();
        reader.join().unwrap();
        assert!(status.success());
        let history = dir.join("temper").join("repl-history.txt");
        let mode = fs::metadata(&history).unwrap().permissions().mode();
        assert_eq!(mode & 0o777, 0o600);
        let kept = fs::read_to_string(&history).unwrap();
        assert!(kept.contains("\ngreet(\\n    \"you\")\n"), "{kept:?}");
        assert!(!kept.contains("1 +"), "{kept:?}");
    });
}
