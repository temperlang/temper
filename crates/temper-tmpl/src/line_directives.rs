//! `#line` directives from tmpl's spans, for backends whose languages
//! have them (C++ and C#): `#line N "FILE"` before generated code says
//! that it comes from line N of a Temper file, so compiler errors,
//! debuggers, and stack traces point there.
//!
//! A backend's translator puts a [`LineAt`] before each statement it
//! translates ([`line_at`]), and its printer writes the directive
//! ([`Printed::directive`]) only where the position isn't already what
//! the output so far implies ([`Printed::presumed`]), which it reads from
//! the text itself, so text printed apart and joined later (a lambda in
//! an expression) stays right, reading each line once as the output
//! grows. Where generated support code follows, the
//! printer restores the generated file's own positions, as its language
//! does that (C#'s `#line default`, C++'s `#line N "FILE"` naming the
//! generated file), so errors there point at the generated file.
//!
//! The paths are relative, after a prefix that leads from where the
//! language resolves them to the library's root, so the output names no
//! machine's paths; `temper build` writes no directives at all
//! ([`crate::backend::SourceLinks::dev`]).

use crate::{Program, Span};

/// A position in the Temper source, as a `#line` directive names it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LineAt {
    /// The file's path after a prefix ([`line_at`]), `/`-separated.
    pub file: String,
    /// Counted from 1.
    pub line: u32,
}

/// Where a span starts, as a directive names it: its file's path after
/// `prefix`, and its line; `None` for an unknown span or a file without
/// a line table.
pub fn line_at(p: &Program, prefix: &str, span: Span) -> Option<LineAt> {
    if !span.is_known() {
        return None;
    }
    let file = p.files.get(span.file.index())?;
    let (line, _) = file.position(span.start)?;
    Some(LineAt {
        file: format!("{prefix}{}", file.path),
        line: line + 1,
    })
}

/// What output so far implies about the position of its next line.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub enum Presumed {
    /// The generated file's own: no directive yet, or `#line default`.
    #[default]
    Generated,
    /// As the last directive said, counting the lines after it.
    At(LineAt),
    /// Past a conditional (`#if`, `#ifdef`, `#else`, `#endif`), whose
    /// directives some builds skip.
    Unknown,
}

/// The position output's next line has, from its last directive, the
/// output ending with a newline (a line it doesn't end counts as ended).
pub fn presumed(out: &str) -> Presumed {
    Printed::default().presumed_of(out)
}

impl Presumed {
    /// What the output implies after one more line, `line`.
    fn after(self, line: &str) -> Presumed {
        let Some(rest) = line.strip_prefix('#') else {
            return match self {
                Presumed::At(at) => Presumed::At(LineAt {
                    line: at.line + 1,
                    ..at
                }),
                other => other,
            };
        };
        if let Some(rest) = rest.strip_prefix("line ") {
            if rest == "default" {
                return Presumed::Generated;
            }
            let parsed = rest.split_once(' ').and_then(|(n, file)| {
                let file = file.strip_prefix('"')?.strip_suffix('"')?;
                Some((n.parse::<u32>().ok()?, unescape(file)))
            });
            return match parsed {
                Some((line, file)) => Presumed::At(LineAt { file, line }),
                None => Presumed::Unknown,
            };
        }
        match ["if", "else", "elif", "endif"]
            .iter()
            .any(|d| rest.starts_with(d))
        {
            true => Presumed::Unknown,
            false => self.after(""),
        }
    }
}

/// Output that `#line` directives go in, printed by appending, as to a
/// `String` (it derefs to its text), which keeps what its text so far
/// implies, reading only what was added since it last looked, so a
/// check per statement costs only the new lines.
#[derive(Clone, Debug, Default)]
pub struct Printed {
    pub text: String,
    /// How much of `text` was read: whole lines.
    read: usize,
    /// What the lines read imply.
    state: Presumed,
}

impl Printed {
    /// The position the output's next line has ([`presumed`]).
    pub fn presumed(&mut self) -> Presumed {
        let text = std::mem::take(&mut self.text);
        let presumed = self.presumed_of(&text);
        self.text = text;
        presumed
    }

    /// What `text`, the text read so far grown, implies.
    fn presumed_of(&mut self, text: &str) -> Presumed {
        // Read again from the start where the text isn't what was read
        // plus more, as where it was cut.
        let read = self.read;
        if text.len() < read || (read > 0 && text.as_bytes()[read - 1] != b'\n') {
            *self = Printed::default();
        }
        let whole = text.rfind('\n').map_or(0, |i| i + 1).max(self.read);
        for line in text[self.read..whole].lines() {
            self.state = std::mem::take(&mut self.state).after(line);
        }
        self.read = whole;
        match &text[whole..] {
            "" => self.state.clone(),
            last => self.state.clone().after(last),
        }
    }

    /// Writes a directive for `at`, on a line of its own, unless the
    /// output is there already.
    pub fn directive(&mut self, at: &LineAt) {
        if self.presumed() != Presumed::At(at.clone()) {
            if !self.text.is_empty() && !self.text.ends_with('\n') {
                self.text.push('\n');
            }
            line(&mut self.text, at);
        }
    }
}

impl std::ops::Deref for Printed {
    type Target = String;
    fn deref(&self) -> &String {
        &self.text
    }
}

impl std::ops::DerefMut for Printed {
    fn deref_mut(&mut self) -> &mut String {
        &mut self.text
    }
}

/// Writes a directive for `at`, on a line of its own, `out` ending with a
/// newline.
pub fn line(out: &mut String, at: &LineAt) {
    out.push_str(&format!("#line {} \"{}\"\n", at.line, escape(&at.file)));
}

fn escape(path: &str) -> String {
    path.replace('\\', "\\\\").replace('"', "\\\"")
}

fn unescape(text: &str) -> String {
    let mut out = String::new();
    let mut chars = text.chars();
    while let Some(c) = chars.next() {
        match c {
            '\\' => out.extend(chars.next()),
            c => out.push(c),
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn at(file: &str, line: u32) -> LineAt {
        LineAt {
            file: file.into(),
            line,
        }
    }

    #[test]
    fn presumes_from_the_last_directive() {
        assert_eq!(presumed(""), Presumed::Generated);
        assert_eq!(presumed("int a;\n"), Presumed::Generated);
        let text = "int a;\n#line 4 \"../a.temper\"\nint b;\nint c;\n";
        assert_eq!(presumed(text), Presumed::At(at("../a.temper", 6)));
        assert_eq!(
            presumed("#line 4 \"../a.temper\"\n"),
            Presumed::At(at("../a.temper", 4))
        );
        let text = "#line 4 \"a\"\nint b;\n#line default\nint c;\n";
        assert_eq!(presumed(text), Presumed::Generated);
        let text = "#line 4 \"a\"\n#ifdef TEMPER_TESTS\nint b;\n";
        assert_eq!(presumed(text), Presumed::Unknown);
        let text = "#line 2 \"say \\\"hi\\\"\\\\\"\nint b;\n";
        assert_eq!(presumed(text), Presumed::At(at("say \"hi\"\\", 3)));
    }

    fn printed(text: &str) -> Printed {
        Printed {
            text: text.into(),
            ..Printed::default()
        }
    }

    #[test]
    fn writes_only_what_changes() {
        let mut out = printed("int a;\n");
        out.directive(&at("a.temper", 3));
        out.push_str("f();\n");
        out.directive(&at("a.temper", 4));
        out.push_str("g();\n");
        out.directive(&at("a.temper", 4));
        out.push_str("h();\n");
        // The next line is line 4 already; line 4 again needs saying.
        assert_eq!(
            out.text,
            "int a;\n#line 3 \"a.temper\"\nf();\ng();\n#line 4 \"a.temper\"\nh();\n"
        );
        // Another file, though at the line it would be.
        let mut out = printed("#line 3 \"a.temper\"\nf();\n");
        out.directive(&at("b.temper", 4));
        assert_eq!(
            out.text,
            "#line 3 \"a.temper\"\nf();\n#line 4 \"b.temper\"\n"
        );
    }

    /// Read as it grows, a line at a time, or a part of one, or cut, it
    /// presumes as reading it whole does.
    #[test]
    fn reads_as_it_grows() {
        let text = "int a;\n#line 4 \"a\"\nint b;\nint c;\n#if X\n#endif\n#line 9 \"b\"\nx\n";
        let mut out = Printed::default();
        for c in text.chars() {
            out.push(c);
            let whole = presumed(&out.text);
            assert_eq!(out.presumed(), whole, "{:?}", out.text);
        }
        // Cut back, then grown again otherwise.
        out.text.truncate(text.find("#if").unwrap());
        out.push_str("y;\n");
        assert_eq!(out.presumed(), Presumed::At(at("a", 7)));
    }
}
