//! Color for `temper repl`, as plain ANSI escape codes (SGR), no crate:
//! whether to color ([`decide`]), and how each part looks: the prompt,
//! results, diagnostics ([`paint`]), and input as it's typed
//! ([`super::line`]).
//!
//! With `--color auto`, the default, output is colored only when standard
//! output is a terminal, and neither `NO_COLOR` (set and not empty),
//! `TERM=dumb`, nor `CI` says otherwise; `always` and `never` decide
//! outright.

use std::borrow::Cow;
use temper_frontend::library::Level;
use temper_frontend::repl::ReplEvent;

/// `--color`'s value.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Choice {
    Auto,
    Always,
    Never,
}

impl Choice {
    pub fn named(name: &str) -> Option<Choice> {
        match name {
            "auto" => Some(Choice::Auto),
            "always" => Some(Choice::Always),
            "never" => Some(Choice::Never),
            _ => None,
        }
    }
}

/// Whether to color, given `--color`, whether standard output is a
/// terminal, and the environment.
pub fn decide(choice: Choice, terminal: bool, env: &dyn Fn(&str) -> Option<String>) -> bool {
    match choice {
        Choice::Always => true,
        Choice::Never => false,
        Choice::Auto => {
            terminal
                && env("NO_COLOR").is_none_or(|v| v.is_empty())
                && env("TERM").is_none_or(|v| v != "dumb")
                && env("CI").is_none()
        }
    }
}

/// SGR parameters for each kind of thing shown.
pub mod style {
    pub const PROMPT: &str = "1;32";
    pub const LABEL: &str = "2";
    pub const FAIL: &str = "31";
    pub const ERROR: &str = "1;31";
    pub const WARNING: &str = "1;33";
    pub const HINT: &str = "36";
    pub const LOCATION: &str = "1";
    pub const NOTE: &str = "36";
    pub const MUTED: &str = "90";

    // Input as it's typed, by the token classes the playground and the
    // editors' grammars use.
    pub const KEYWORD: &str = "34";
    pub const ATOM: &str = "35";
    pub const NUMBER: &str = "35";
    pub const STRING: &str = "32";
    pub const ESCAPE: &str = "33";
    pub const COMMENT: &str = "90";
    pub const TYPE: &str = "36";
    pub const DECORATOR: &str = "33";
    pub const INVALID: &str = "4;31";
    pub const BRACKET: &str = "1;7";
}

/// Text in a style, or as is without color.
pub fn sgr(on: bool, style: &str, text: &str) -> String {
    match on && !text.is_empty() {
        true => format!("\x1b[{style}m{text}\x1b[0m"),
        false => text.to_string(),
    }
}

/// An event as `temper repl` prints it, colored if `on`.
pub fn paint(event: &ReplEvent, on: bool) -> Cow<'static, str> {
    let text = event.text();
    if !on {
        return Cow::Owned(text);
    }
    Cow::Owned(match event {
        ReplEvent::Diagnostic(d) => {
            let level = match d.level {
                Level::Error => style::ERROR,
                Level::Warn => style::WARNING,
                _ => style::HINT,
            };
            // `location: level: message`, then the snippet's two lines.
            let mut lines = text.splitn(3, '\n');
            let head = lines.next().unwrap_or_default();
            let line = lines.next().unwrap_or_default();
            let under = lines.next().unwrap_or_default();
            let name = format!("{:?}", d.level).to_lowercase();
            let head = match head.split_once(&format!(": {name}: ")) {
                Some((location, message)) => format!(
                    "{}: {}: {message}",
                    sgr(true, style::LOCATION, location),
                    sgr(true, level, &name)
                ),
                None => head.to_string(),
            };
            let marks = under.trim_start();
            let under = format!(
                "{}{}",
                &under[..under.len() - marks.len()],
                sgr(true, level, marks)
            );
            format!("{head}\n{line}\n{under}")
        }
        ReplEvent::Failure(_) | ReplEvent::InternalError(_) => sgr(true, style::FAIL, &text),
        ReplEvent::Interrupted | ReplEvent::Unresolved => sgr(true, style::WARNING, &text),
        ReplEvent::Note(_) => sgr(true, style::NOTE, &text),
        ReplEvent::Result { input, .. } => {
            let label = sgr(true, style::LABEL, &format!("interactive#{input}:"));
            let value = text.split_once(": ").map_or("", |(_, v)| v);
            match value {
                "fail" => format!("{label} {}", sgr(true, style::FAIL, value)),
                _ => format!("{label} {value}"),
            }
        }
        ReplEvent::Log(_) | ReplEvent::Output(_) => text,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn env(vars: &[(&str, &str)]) -> impl Fn(&str) -> Option<String> {
        let vars: Vec<(String, String)> = (vars.iter())
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect();
        move |name| {
            (vars.iter())
                .find(|(k, _)| k == name)
                .map(|(_, v)| v.clone())
        }
    }

    #[test]
    fn decides_by_flag_terminal_and_environment() {
        let none = env(&[]);
        assert!(decide(Choice::Auto, true, &none));
        assert!(!decide(Choice::Auto, false, &none));
        assert!(decide(Choice::Always, false, &none));
        assert!(!decide(Choice::Never, true, &none));
        assert!(!decide(Choice::Auto, true, &env(&[("NO_COLOR", "1")])));
        // An empty NO_COLOR doesn't count, as no-color.org says.
        assert!(decide(Choice::Auto, true, &env(&[("NO_COLOR", "")])));
        assert!(!decide(Choice::Auto, true, &env(&[("TERM", "dumb")])));
        assert!(decide(Choice::Auto, true, &env(&[("TERM", "xterm")])));
        assert!(!decide(Choice::Auto, true, &env(&[("CI", "true")])));
        assert!(decide(Choice::Always, true, &env(&[("NO_COLOR", "1")])));
        assert_eq!(Choice::named("auto"), Some(Choice::Auto));
        assert_eq!(Choice::named("sometimes"), None);
    }

    #[test]
    fn paints_events() {
        let result = ReplEvent::Result {
            input: 3,
            value: "fail".into(),
        };
        assert_eq!(paint(&result, false), "interactive#3: fail");
        assert_eq!(
            paint(&result, true),
            "\x1b[2minteractive#3:\x1b[0m \x1b[31mfail\x1b[0m"
        );
        let diagnostic = ReplEvent::Diagnostic(temper_frontend::repl::ReplDiagnostic {
            level: Level::Error,
            message: "nope has not been declared".into(),
            location: "interactive#1".into(),
            line: 1,
            column: 3,
            snippet: ["1 + nope".into(), "    ^^^^".into()],
        });
        assert_eq!(
            paint(&diagnostic, true),
            "\x1b[1minteractive#1:1:3\x1b[0m: \x1b[1;31merror\x1b[0m: nope has not been \
             declared\n    1 + nope\n        \x1b[1;31m^^^^\x1b[0m"
        );
        let log = ReplEvent::Log("\x1b[1mmine".into());
        assert_eq!(paint(&log, true), "\x1b[1mmine");
    }
}
