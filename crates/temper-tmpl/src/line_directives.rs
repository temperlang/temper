//! `#line` directives from tmpl's spans, for backends whose languages
//! have them (C++ and C#): `#line N "FILE"` before generated code says
//! that it comes from line N of a Temper file, so compiler errors,
//! debuggers, and stack traces point there.
//!
//! A backend's translator puts a [`LineAt`] before each statement it
//! translates ([`line_at`]), and its printer writes the directive
//! ([`directive`]) only where the position isn't already what the
//! output so far implies ([`presumed`]), which it reads back from the
//! text itself, so text printed apart and joined later (a lambda in an
//! expression) stays right. Where generated support code follows, the
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
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Presumed {
    /// The generated file's own: no directive yet, or `#line default`.
    Generated,
    /// As the last directive said, counting the lines after it.
    At(LineAt),
    /// Past a conditional (`#if`, `#ifdef`, `#else`, `#endif`), whose
    /// directives some builds skip.
    Unknown,
}

/// The position output's next line has, from its last directive, the
/// output ending with a newline.
pub fn presumed(out: &str) -> Presumed {
    let text = out.strip_suffix('\n').unwrap_or(out);
    if text.is_empty() {
        return Presumed::Generated;
    }
    for (after, line) in text.rsplit('\n').enumerate() {
        let Some(rest) = line.strip_prefix('#') else {
            continue;
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
                Some((n, file)) => Presumed::At(LineAt {
                    file,
                    line: n + after as u32,
                }),
                None => Presumed::Unknown,
            };
        }
        if ["if", "else", "elif", "endif"]
            .iter()
            .any(|d| rest.starts_with(d))
        {
            return Presumed::Unknown;
        }
    }
    Presumed::Generated
}

/// Writes a directive for `at`, on a line of its own, unless the output
/// is there already.
pub fn directive(out: &mut String, at: &LineAt) {
    if presumed(out) != Presumed::At(at.clone()) {
        if !out.is_empty() && !out.ends_with('\n') {
            out.push('\n');
        }
        line(out, at);
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

    #[test]
    fn writes_only_what_changes() {
        let mut out = String::from("int a;\n");
        directive(&mut out, &at("a.temper", 3));
        out.push_str("f();\n");
        directive(&mut out, &at("a.temper", 4));
        out.push_str("g();\n");
        directive(&mut out, &at("a.temper", 4));
        out.push_str("h();\n");
        // The next line is line 4 already; line 4 again needs saying.
        assert_eq!(
            out,
            "int a;\n#line 3 \"a.temper\"\nf();\ng();\n#line 4 \"a.temper\"\nh();\n"
        );
        // Another file, though at the line it would be.
        let mut out = String::from("#line 3 \"a.temper\"\nf();\n");
        directive(&mut out, &at("b.temper", 4));
        assert_eq!(out, "#line 3 \"a.temper\"\nf();\n#line 4 \"b.temper\"\n");
    }
}
