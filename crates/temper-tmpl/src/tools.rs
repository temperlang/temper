//! What backends' [`Tools`](crate::Tools) share, and their tests too
//! (`temper-be-testkit`): finding a tool, running it, and reading the
//! results it writes as JUnit XML. Native only, as no tool runs in wasm.
//! What they start is kept from running forever or outliving tempest
//! (`children.rs`).

use crate::TestResults;
use std::path::{Path, PathBuf};
use std::process::{Command, Output};

/// A tool named by an environment variable, or else by its usual name.
pub fn tool(var: &str, name: &str) -> String {
    std::env::var(var).unwrap_or_else(|_| name.to_string())
}

/// Whether compiler warnings on generated code fail a library's tests
/// (`TEMPER_STRICT_WARNINGS` set, as CI's runs of real libraries set
/// it), as the backends' own tests fail on them; else they're hidden, as
/// in Kotlin. The runtimes, Kotlin's, keep their own warnings quiet.
pub fn strict_warnings() -> bool {
    std::env::var_os("TEMPER_STRICT_WARNINGS").is_some()
}

pub use crate::children::{
    DEFAULT_MEMORY_LIMIT_MIB, DEFAULT_TIMEOUT, limit_memory, parse_mib, parse_seconds, set_timeout,
};

/// Runs a command, its output captured, or says why it couldn't start or
/// that it timed out; in a process group of its own, which is killed if
/// tempest is, or it times out (`children.rs`).
pub fn output(command: &mut Command, what: &str) -> Result<Output, String> {
    crate::children::output(command, what)
}

/// Runs a step that must succeed before a program can run, such as its
/// build, quietly, or says why it failed, with the end of what it printed.
pub fn step(command: &mut Command, what: &str) -> Result<(), String> {
    let run = output(command, what)?;
    match run.status.success() {
        true => Ok(()),
        false => Err(format!("{what} failed\n{}", tool_output(&run))),
    }
}

/// Runs a program with this process's standard input, output, and error,
/// so what it prints streams, as [`crate::Tools::run`] does; whether it
/// succeeded. As [`output`], but with no time limit unless one is given.
pub fn program(command: &mut Command, what: &str) -> Result<bool, String> {
    crate::children::program(command, what)
}

/// Installs the packages that libraries' configs list (`dependencies`)
/// into `dir`, as `temper.out/py/.deps`, which no library's name can
/// be, with `install`, once for each list of them: `manifest` says what
/// to install, as the language's tools spell it, and is written as the
/// file `manifest_name` there; `install` runs there unless the last
/// install was of the same manifest, recorded as `.installed` once it
/// succeeds. Nothing is installed, and `dir` is removed, for an empty
/// manifest. Whether there are packages.
pub fn install_once(
    dir: &Path,
    manifest_name: &str,
    manifest: &str,
    install: impl FnOnce(&Path) -> Result<(), String>,
) -> Result<bool, String> {
    let done = dir.join(".installed");
    if manifest.is_empty() {
        let _ = std::fs::remove_dir_all(dir);
        return Ok(false);
    }
    if std::fs::read_to_string(&done).is_ok_and(|d| d == manifest) {
        return Ok(true);
    }
    let _ = std::fs::remove_dir_all(dir);
    let at = |e: std::io::Error| format!("{}: {e}", dir.display());
    std::fs::create_dir_all(dir).map_err(at)?;
    std::fs::write(dir.join(manifest_name), manifest).map_err(at)?;
    install(dir)?;
    std::fs::write(&done, manifest).map_err(at)?;
    Ok(true)
}

/// Removes `path`, a file, a link, or a directory and all it holds, if
/// it's there, but only where it's under `root` by plain names (no `..`,
/// no root of its own, which `Path::join` would take instead of `root`'s),
/// as names from configs make paths; an error otherwise.
pub fn remove_within(root: &Path, path: &Path) -> Result<(), String> {
    use std::path::Component;
    let inside = (path.strip_prefix(root)).is_ok_and(|rest| {
        rest.components().next().is_some()
            && rest.components().all(|c| matches!(c, Component::Normal(_)))
    });
    if !inside {
        return Err(format!("{} isn't in {}", path.display(), root.display()));
    }
    let _ = std::fs::remove_file(path);
    let _ = std::fs::remove_dir_all(path);
    Ok(())
}

/// The end of what a tool printed, which says most, as a traceback's.
pub fn tool_output(run: &Output) -> String {
    let text = format!(
        "{}{}",
        String::from_utf8_lossy(&run.stdout),
        String::from_utf8_lossy(&run.stderr)
    );
    let lines: Vec<&str> = text.lines().collect();
    lines[lines.len().saturating_sub(40)..].join("\n")
}

/// The tests in a JUnit XML file, each name through `name`, with why
/// each failed ([`Failed::cause`]); or the tool's output where there's
/// none.
pub fn junit_file(path: &Path, run: &Output, name: impl Fn(&str) -> String) -> TestResults {
    match std::fs::read_to_string(path) {
        Ok(xml) if xml.contains("<testsuite") => Ok(junit(&xml)
            .into_iter()
            .map(|c| (name(&c.name), c.cause()))
            .collect()),
        _ => Err(format!("no test results\n{}", tool_output(run))),
    }
}

/// Every file with an extension under a directory, sorted; none if
/// there's no such directory.
pub fn files_with(dir: &Path, extension: &str, out: &mut Vec<PathBuf>) {
    let Ok(entries) = std::fs::read_dir(dir) else {
        return;
    };
    let mut paths: Vec<PathBuf> = entries.flatten().map(|e| e.path()).collect();
    paths.sort();
    for path in paths {
        if path.is_dir() {
            files_with(&path, extension, out);
        } else if path.extension().is_some_and(|e| e == extension) {
            out.push(path);
        }
    }
}

/// A test case in JUnit XML: its name, and its `<failure>` and `<error>`,
/// if it has them.
#[derive(Clone, Debug, PartialEq)]
pub struct Case {
    pub name: String,
    pub failure: Option<Failed>,
    pub error: Option<Failed>,
}

/// A `<failure>` or `<error>`: its `message` and `type`, and its text,
/// unescaped, or a CDATA section's as it is.
#[derive(Clone, Debug, PartialEq)]
pub struct Failed {
    pub message: Option<String>,
    pub kind: Option<String>,
    pub text: String,
}

impl Case {
    /// Why it failed, if it did, as `temper test -b` says it: its
    /// failure's (else its error's) message, or else its type, or else
    /// its text. LuaUnit's type says where it failed first
    /// (`a/tests/init-test.lua:19: expected …`), which is left out.
    pub fn cause(&self) -> Option<String> {
        let failed = self.failure.as_ref().or(self.error.as_ref())?;
        let nonempty = |s: &Option<String>| s.clone().filter(|s| !s.is_empty());
        let kind = nonempty(&failed.kind).map(|kind| {
            (kind.split_once(".lua:"))
                .and_then(|(_, rest)| rest.split_once(": "))
                .map_or(kind.clone(), |(_, said)| said.to_string())
        });
        Some((nonempty(&failed.message).or(kind)).unwrap_or_else(|| failed.text.trim().to_string()))
    }
}

/// Each test case in JUnit XML.
pub fn junit(xml: &str) -> Vec<Case> {
    let mut found = Vec::new();
    let mut rest = xml;
    while let Some(at) = rest.find("<testcase") {
        rest = &rest[at + "<testcase".len()..];
        let (attrs, closed) = tag_attrs(rest);
        let name = attr(attrs, "name").unwrap_or_default();
        let body = if closed {
            ""
        } else {
            &rest[..rest.find("</testcase>").unwrap_or(rest.len())]
        };
        found.push(Case {
            name,
            failure: element(body, "failure"),
            error: element(body, "error"),
        });
    }
    found
}

/// A start tag's attributes, from just past its name, and whether it
/// closes itself.
fn tag_attrs(after_name: &str) -> (&str, bool) {
    let mut quote = None;
    for (i, c) in after_name.char_indices() {
        match (quote, c) {
            (None, '\'' | '"') => quote = Some(c),
            (Some(q), _) if q == c => quote = None,
            (None, '>') => {
                let attrs = &after_name[..i];
                return match attrs.strip_suffix('/') {
                    Some(attrs) => (attrs, true),
                    None => (attrs, false),
                };
            }
            _ => {}
        }
    }
    (after_name, true)
}

/// An attribute's value, unescaped.
fn attr(attrs: &str, name: &str) -> Option<String> {
    let mut rest = attrs;
    loop {
        let at = rest.find(name)?;
        let before = rest[..at].chars().next_back();
        rest = &rest[at + name.len()..];
        let after = rest.trim_start();
        if before.is_some_and(char::is_whitespace) && after.starts_with('=') {
            let value = after[1..].trim_start();
            let q = value.chars().next()?;
            let value = &value[1..];
            let end = value.find(q)?;
            return Some(unescape_xml(&value[..end]));
        }
    }
}

/// An element in `body`, if it's there.
fn element(body: &str, tag: &str) -> Option<Failed> {
    let open = format!("<{tag}");
    let mut rest = body;
    loop {
        let at = rest.find(&open)?;
        rest = &rest[at + open.len()..];
        if rest.starts_with(|c: char| c.is_whitespace() || c == '>' || c == '/') {
            break;
        }
    }
    let (attrs, closed) = tag_attrs(rest);
    let text = if closed {
        String::new()
    } else {
        let text = &rest[attrs.len() + 1..];
        let text = &text[..text.find(&format!("</{tag}>")).unwrap_or(text.len())];
        match text.trim().strip_prefix("<![CDATA[") {
            Some(cdata) => cdata.strip_suffix("]]>").unwrap_or(cdata).to_string(),
            None => unescape_xml(text),
        }
    };
    Some(Failed {
        message: attr(attrs, "message"),
        kind: attr(attrs, "type"),
        text,
    })
}

/// Text with XML's entities, named and numeric, as their characters.
pub fn unescape_xml(s: &str) -> String {
    let mut out = String::new();
    let mut rest = s;
    while let Some(at) = rest.find('&') {
        out.push_str(&rest[..at]);
        rest = &rest[at..];
        let Some(end) = rest.find(';') else { break };
        let entity = &rest[1..end];
        let c = match entity {
            "amp" => Some('&'),
            "lt" => Some('<'),
            "gt" => Some('>'),
            "quot" => Some('"'),
            "apos" => Some('\''),
            _ => match entity
                .strip_prefix("#x")
                .or_else(|| entity.strip_prefix("#X"))
            {
                Some(hex) => u32::from_str_radix(hex, 16).ok().and_then(char::from_u32),
                None => (entity.strip_prefix('#'))
                    .and_then(|d| d.parse().ok())
                    .and_then(char::from_u32),
            },
        };
        match c {
            Some(c) => {
                out.push(c);
                rest = &rest[end + 1..];
            }
            None => {
                out.push('&');
                rest = &rest[1..];
            }
        }
    }
    out.push_str(rest);
    out
}

#[cfg(test)]
mod tests {
    use super::{install_once, junit, remove_within};
    use std::cell::Cell;
    use std::path::Path;

    /// A fresh scratch directory for a test.
    fn scratch(test: &str) -> std::path::PathBuf {
        let dir = std::env::temp_dir().join(format!("tmpl-tools-{}-{test}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    /// Installs once per manifest: not for an empty one, which removes
    /// the directory; again for a changed one; and again after a failed
    /// install, which leaves no record that it succeeded.
    #[test]
    fn installs_once_per_manifest() {
        let root = scratch("install-once");
        let deps = root.join(".deps");
        let runs = Cell::new(0);
        let install = |ok: bool| {
            let runs = &runs;
            move |dir: &Path| {
                runs.set(runs.get() + 1);
                assert!(dir.join("m.txt").is_file());
                if ok {
                    Ok(())
                } else {
                    Err("failed".to_string())
                }
            }
        };
        assert_eq!(install_once(&deps, "m.txt", "", install(true)), Ok(false));
        assert_eq!((runs.get(), deps.exists()), (0, false));
        assert_eq!(install_once(&deps, "m.txt", "a\n", install(true)), Ok(true));
        assert_eq!(runs.get(), 1);
        assert_eq!(std::fs::read_to_string(deps.join("m.txt")).unwrap(), "a\n");
        assert_eq!(install_once(&deps, "m.txt", "a\n", install(true)), Ok(true));
        assert_eq!(runs.get(), 1);
        assert_eq!(install_once(&deps, "m.txt", "b\n", install(true)), Ok(true));
        assert_eq!(runs.get(), 2);
        let failed = install_once(&deps, "m.txt", "c\n", install(false));
        assert_eq!((failed, runs.get()), (Err("failed".to_string()), 3));
        assert!(!deps.join(".installed").exists());
        assert_eq!(install_once(&deps, "m.txt", "c\n", install(true)), Ok(true));
        assert_eq!(runs.get(), 4);
        assert_eq!(install_once(&deps, "m.txt", "", install(true)), Ok(false));
        assert_eq!((runs.get(), deps.exists()), (4, false));
        let _ = std::fs::remove_dir_all(&root);
    }

    /// Removes only what's under its root by plain names.
    #[test]
    fn removes_only_within() {
        let root = scratch("remove-within");
        let modules = root.join("node_modules");
        let kept = root.join("kept");
        std::fs::create_dir_all(modules.join("@s/x/y")).unwrap();
        std::fs::create_dir_all(&kept).unwrap();
        assert_eq!(remove_within(&modules, &modules.join("@s/x")), Ok(()));
        assert!(!modules.join("@s/x").exists() && modules.join("@s").exists());
        assert_eq!(remove_within(&modules, &modules.join("absent")), Ok(()));
        for outside in [
            modules.join("../kept"),
            modules.join("@s/../../kept"),
            modules.join(&kept),
            modules.clone(),
            root.clone(),
            kept.clone(),
        ] {
            assert!(
                remove_within(&modules, &outside).is_err(),
                "{}",
                outside.display()
            );
        }
        assert!(kept.exists() && modules.exists());
        let _ = std::fs::remove_dir_all(&root);
    }

    fn causes(xml: &str) -> Vec<(String, Option<String>)> {
        junit(xml)
            .into_iter()
            .map(|c| (c.name.clone(), c.cause()))
            .collect()
    }

    #[test]
    fn reads_junit() {
        let xml = r#"<testsuite tests="3">
  <testcase name="a &amp; b" time="0"/>
  <testcase name="c"><failure message="expected 1 == (2) not (1)">trace</failure></testcase>
  <testcase name="d"><error>Traceback &lt;here&gt; &#x27;x&#39;</error></testcase>
</testsuite>"#;
        assert_eq!(
            causes(xml),
            [
                ("a & b".to_string(), None),
                (
                    "c".to_string(),
                    Some("expected 1 == (2) not (1)".to_string())
                ),
                ("d".to_string(), Some("Traceback <here> 'x'".to_string())),
            ]
        );
    }

    /// LuaUnit's failure says why in its `type`, after where; another's
    /// text may be CDATA, taken as it is.
    #[test]
    fn reads_luaunit_and_cdata() {
        let xml = r#"<testsuite tests="2">
  <testcase name="Test_.test_b">
    <failure type="a/tests/init-test.lua:19: expected 1 + 1 == (3) not (2)">
      <![CDATA[stack traceback:
	a/tests/init-test.lua:19]]></failure>
  </testcase>
  <testcase name="e"><failure><![CDATA[x < y]]></failure></testcase>
</testsuite>"#;
        assert_eq!(
            causes(xml),
            [
                (
                    "Test_.test_b".to_string(),
                    Some("expected 1 + 1 == (3) not (2)".to_string())
                ),
                ("e".to_string(), Some("x < y".to_string())),
            ]
        );
    }
}
