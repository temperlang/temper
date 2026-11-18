//! Temper's functional tests, in this crate's `cases/`, for crates that
//! build or translate them in their tests.
//!
//! Each test is a directory whose main file is named after it
//! (`algos/fibonacci/fibonacci.temper.md`). [`tests`] lists them;
//! [`Test::sources`] gives a test's own files, the main one first;
//! [`Test::std_modules`] the std modules it imports, with their files; and
//! [`Test::expected_log`] what a run on a given backend logs, as Kotlin's
//! `MarkdownFileBasedFunctionalTestBase` reads the main file's ```` ```log ````
//! blocks, honoring their backend selectors (```` ```log ~interp ~lua ````).
//!
//! No dependencies, so the frontend and every backend can use it.

use std::fs;
use std::path::{Path, PathBuf};

/// The repository's root directory: two up from this crate's. Not
/// canonicalized, which on Windows gives a `\\?\` path that the
/// frontend's `/`-joined paths can't hold.
fn repo() -> PathBuf {
    let crate_dir = Path::new(env!("CARGO_MANIFEST_DIR"));
    crate_dir
        .ancestors()
        .nth(2)
        .unwrap_or(crate_dir)
        .to_path_buf()
}

/// The functional tests' root directory, this crate's `cases/`.
pub fn suite_dir() -> PathBuf {
    repo().join("crates/temper-funtests/cases")
}

/// std's root directory, the repository's `lib/std/`.
pub fn std_dir() -> PathBuf {
    repo().join("lib/std")
}

/// One functional test.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Test {
    /// Its path under the suite, as `algos/fibonacci`.
    pub path: String,
    /// Its directory.
    pub dir: PathBuf,
}

/// A source file: its path, as given to the frontend, and its text.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Source {
    pub path: String,
    pub text: String,
}

/// A std module a test imports, as `std/regex`, and its files.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct StdModule {
    pub name: String,
    pub sources: Vec<Source>,
}

/// Every functional test, sorted by path.
pub fn tests() -> Vec<Test> {
    let root = suite_dir();
    let mut found = Vec::new();
    let mut stack = vec![root.clone()];
    while let Some(dir) = stack.pop() {
        let Ok(entries) = fs::read_dir(&dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if !path.is_dir() {
                continue;
            }
            let name = path.file_name().unwrap_or_default().to_string_lossy();
            if path.join(format!("{name}.temper.md")).exists() {
                let rel = path.strip_prefix(&root).unwrap_or(&path);
                found.push(Test {
                    path: rel.to_string_lossy().replace('\\', "/"),
                    dir: path.clone(),
                });
            }
            stack.push(path);
        }
    }
    found.sort_by(|a, b| a.path.cmp(&b.path));
    found
}

/// The test at `path`, as `algos/fibonacci`.
pub fn test(path: &str) -> Option<Test> {
    let dir = suite_dir().join(path);
    let name = dir.file_name()?.to_string_lossy().to_string();
    dir.join(format!("{name}.temper.md"))
        .exists()
        .then(|| Test {
            path: path.to_string(),
            dir,
        })
}

fn is_temper(path: &Path) -> bool {
    let name = path.to_string_lossy();
    name.ends_with(".temper") || name.ends_with(".temper.md")
}

fn read(path: &Path) -> Source {
    Source {
        path: path.to_string_lossy().replace('\\', "/"),
        text: fs::read_to_string(path).unwrap_or_default(),
    }
}

impl Test {
    /// Its directory's name, which names its library: `fibonacci`.
    pub fn name(&self) -> &str {
        self.path.rsplit('/').next().unwrap_or(&self.path)
    }

    /// Its main file, named after it.
    pub fn main_file(&self) -> PathBuf {
        self.dir.join(format!("{}.temper.md", self.name()))
    }

    /// Its directory's own Temper files, the main one first, then the rest
    /// sorted, as compat/check_functional.py builds them.
    pub fn sources(&self) -> Vec<Source> {
        let main = self.main_file();
        let mut others: Vec<PathBuf> = fs::read_dir(&self.dir)
            .map(|entries| {
                entries
                    .flatten()
                    .map(|e| e.path())
                    .filter(|p| *p != main && p.is_file() && is_temper(p))
                    .collect()
            })
            .unwrap_or_default();
        others.sort();
        std::iter::once(main)
            .chain(others)
            .map(|p| read(&p))
            .collect()
    }

    /// The std modules its sources import, and those they import in turn,
    /// sorted by name, each with its files.
    pub fn std_modules(&self) -> Vec<StdModule> {
        let mut names: Vec<String> = Vec::new();
        let mut pending: Vec<String> = self
            .sources()
            .iter()
            .flat_map(|s| std_imports(&s.text))
            .collect();
        let mut modules = Vec::new();
        while let Some(name) = pending.pop() {
            if names.contains(&name) {
                continue;
            }
            names.push(name.clone());
            let dir = std_dir().join(name.trim_start_matches("std/"));
            let mut files: Vec<PathBuf> = fs::read_dir(&dir)
                .map(|entries| {
                    entries
                        .flatten()
                        .map(|e| e.path())
                        .filter(|p| p.is_file() && is_temper(p))
                        .collect()
                })
                .unwrap_or_default();
            files.sort();
            let sources: Vec<Source> = files.iter().map(|p| read(p)).collect();
            pending.extend(sources.iter().flat_map(|s| std_imports(&s.text)));
            modules.push(StdModule { name, sources });
        }
        modules.sort_by(|a, b| a.name.cmp(&b.name));
        modules
    }

    /// The main file's text.
    pub fn main_text(&self) -> String {
        fs::read_to_string(self.main_file()).unwrap_or_default()
    }

    /// What a run on `backend` logs (`interp`, `py`, `lua`, …): the lines
    /// of the main file's log blocks that apply to it, joined.
    pub fn expected_log(&self, backend: &str) -> Vec<String> {
        expected_log(&self.main_text(), backend)
    }

    /// What a run on `backend` under `--no-connected` logs, where Temper
    /// implementations stand in for host code: the blocks that apply to
    /// `backend` or to `no-connected`, as Kotlin names such a run.
    pub fn expected_log_no_connected(&self, backend: &str) -> Vec<String> {
        expected_log_run(&self.main_text(), &[backend, NO_CONNECTED])
    }

    /// Its files that aren't Temper, as host code beside its modules
    /// (`__connected__.py`, `Counter.java`, …), each with its path
    /// relative to its directory, sorted.
    pub fn host_files(&self) -> Vec<Source> {
        host_files(&self.dir)
    }

    /// Whether its metadata says a run fails (`expectRunFailure = true`).
    pub fn expects_run_failure(&self) -> bool {
        self.main_text().contains("expectRunFailure = true")
    }

    /// Whether it runs as tests rather than a program (`runAsTest = true`).
    pub fn runs_as_test(&self) -> bool {
        self.main_text().contains("runAsTest = true")
    }

    /// The tests a run as tests expects to fail, each its name and text
    /// its failure's cause contains, from its metadata's
    /// `expectedFailures = mapOf("name" to """cause""", …)`.
    pub fn expected_failures(&self) -> Vec<(String, String)> {
        expected_failures(&self.main_text())
    }
}

/// The `expectedFailures` of a main file's metadata, as
/// [`Test::expected_failures`] gives them: each entry on a
/// `@meta:arg` line of its own, as the functional tests write them.
pub fn expected_failures(main: &str) -> Vec<(String, String)> {
    let mut found = Vec::new();
    let mut inside = false;
    for line in main.lines() {
        let Some(arg) = line.trim().strip_prefix("@meta:arg") else {
            continue;
        };
        let arg = arg.trim();
        if arg.starts_with("expectedFailures") {
            inside = true;
        } else if inside && arg.starts_with(')') {
            inside = false;
        } else if inside {
            let Some((name, cause)) = arg.split_once(" to ") else {
                continue;
            };
            let unquote = |s: &str| {
                let s = s.trim().trim_end_matches(',');
                let q = if s.starts_with("\"\"\"") {
                    "\"\"\""
                } else {
                    "\""
                };
                let s = s.strip_prefix(q).and_then(|s| s.strip_suffix(q));
                s.unwrap_or_default().to_string()
            };
            found.push((unquote(name), unquote(cause)));
        }
    }
    found
}

/// The `std/…` modules a source imports, by the text of its
/// `import("std/…")` calls.
pub fn std_imports(text: &str) -> Vec<String> {
    let mut found = Vec::new();
    let mut rest = text;
    while let Some(at) = rest.find("import(\"std/") {
        rest = &rest[at + "import(\"".len()..];
        if let Some(end) = rest.find('"') {
            let name = rest[..end].trim_end_matches('/').to_string();
            if !found.contains(&name) {
                found.push(name);
            }
            rest = &rest[end..];
        }
    }
    found
}

/// The name of runs under `--no-connected` in log blocks' selectors, as
/// Kotlin's `NO_CONNECTED_LOG_NAME`.
pub const NO_CONNECTED: &str = "no-connected";

/// A directory's files that aren't Temper, under it at any depth, each
/// with its path relative to it (with `/`), sorted by path.
pub fn host_files(dir: &Path) -> Vec<Source> {
    let mut found = Vec::new();
    let mut stack = vec![dir.to_path_buf()];
    while let Some(d) = stack.pop() {
        let Ok(entries) = fs::read_dir(&d) else {
            continue;
        };
        for path in entries.flatten().map(|e| e.path()) {
            if path.is_dir() {
                stack.push(path);
            } else if !is_temper(&path) {
                let rel = path.strip_prefix(dir).unwrap_or(&path);
                found.push(Source {
                    path: rel.to_string_lossy().replace('\\', "/"),
                    text: fs::read_to_string(&path).unwrap_or_default(),
                });
            }
        }
    }
    found.sort_by(|a, b| a.path.cmp(&b.path));
    found
}

/// Whether a log block whose opening line names `selectors` (as `interp
/// lua` or `~interp ~lua`) applies to `backend`, as Kotlin's
/// `logBlockApplies` decides: no inclusions or one naming it, and no
/// exclusion naming it.
pub fn block_applies(selectors: &str, backend: &str) -> bool {
    block_applies_run(selectors, &[backend])
}

/// Whether a log block applies to a run with these names (as `["py",
/// "no-connected"]`): no inclusions or one naming the run, and no
/// exclusion naming it.
pub fn block_applies_run(selectors: &str, run: &[&str]) -> bool {
    let words: Vec<&str> = selectors.split_whitespace().collect();
    let includes: Vec<&str> = words
        .iter()
        .filter(|w| !w.starts_with('~'))
        .copied()
        .collect();
    let excluded = (words.iter()).any(|w| w.strip_prefix('~').is_some_and(|w| run.contains(&w)));
    (includes.is_empty() || includes.iter().any(|w| run.contains(w))) && !excluded
}

/// The lines of a main file's log blocks that apply to `backend`.
pub fn expected_log(main: &str, backend: &str) -> Vec<String> {
    expected_log_run(main, &[backend])
}

/// The lines of a main file's log blocks that apply to a run with these
/// names.
pub fn expected_log_run(main: &str, run: &[&str]) -> Vec<String> {
    let mut lines = Vec::new();
    let mut inside = None;
    for line in main.lines() {
        let trimmed = line.trim_start_matches([' ', '\t']);
        match inside {
            Some(keep) => {
                if trimmed.trim_end_matches([' ', '\t']) == "```" {
                    inside = None;
                } else if keep {
                    lines.push(line.to_string());
                }
            }
            None => {
                if let Some(rest) = trimmed.strip_prefix("```log") {
                    let selectors = rest.trim_end_matches([' ', '\t']);
                    let valid = selectors.is_empty() || selectors.starts_with([' ', '\t']);
                    if valid {
                        inside = Some(block_applies_run(selectors, run));
                    }
                }
            }
        }
    }
    lines
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lists_the_suite() {
        let all = tests();
        assert!(all.len() >= 69, "{} tests", all.len());
        assert!(all.iter().any(|t| t.path == "algos/fibonacci"));
        let fib = test("algos/fibonacci").unwrap();
        assert_eq!(fib.name(), "fibonacci");
        assert_eq!(fib.sources().len(), 1);
        assert_eq!(
            fib.expected_log("py"),
            ["fib(1)=1", "fib(10)=55", "fib(20)=6765"]
        );
    }

    #[test]
    fn expected_failures_of_asserts() {
        let asserts = test("testing/asserts").unwrap();
        assert!(asserts.runs_as_test());
        assert_eq!(
            asserts.expected_failures(),
            [(
                "a failing test".to_string(),
                "because it's wrong, expected helper.i == (2) not (3), expected s == (hi) not (bye)"
                    .to_string()
            )]
        );
    }

    #[test]
    fn selectors() {
        let main = "```log\na\n```\n```log ~interp ~lua\nb\n```\n```log interp lua\nc\n```\n";
        assert_eq!(expected_log(main, "interp"), ["a", "c"]);
        assert_eq!(expected_log(main, "py"), ["a", "b"]);
        assert_eq!(expected_log(main, "lua"), ["a", "c"]);
    }

    #[test]
    fn no_connected_runs() {
        let main =
            "```log ~interp ~no-connected ~lua\na\n```\n```log interp no-connected lua\nb\n```\n";
        assert_eq!(expected_log_run(main, &["py"]), ["a"]);
        assert_eq!(expected_log_run(main, &["py", NO_CONNECTED]), ["b"]);
        assert_eq!(expected_log_run(main, &["lua"]), ["b"]);
        let types = test("types/connected").unwrap();
        assert_eq!(
            types.expected_log_no_connected("py"),
            ["counter.total(): 1005", "new Doubler().twice(5): 1010"]
        );
        let hosts: Vec<String> = types.host_files().into_iter().map(|f| f.path).collect();
        assert!(hosts.contains(&"Counter.java".to_string()), "{hosts:?}");
        assert!(!hosts.iter().any(|h| h.ends_with(".md")), "{hosts:?}");
    }

    #[test]
    fn std_imports_are_found() {
        let regex = test("regex/match").unwrap();
        let modules = regex.std_modules();
        assert!(modules.iter().any(|m| m.name == "std/regex"), "{modules:?}");
        assert!(modules.iter().all(|m| !m.sources.is_empty()));
    }
}
