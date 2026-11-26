//! Temper libraries on disk, found as Kotlin Temper's `temper test` finds
//! them, and loaded into a [`Library`] with the modules they import.
//!
//! - The work root, from a starting directory: the nearest directory, it
//!   or an ancestor, holding a `temper.out` directory, or else the highest
//!   holding a `config.temper.md`, or else the starting directory
//!   ([`work_root`]).
//! - Under it, each directory with a `config.temper.md` is a library's
//!   root, and its `.temper` and `.temper.md` files, in it and below it
//!   but not under another library's root, are the library's sources
//!   ([`find_libraries`]). Each directory is a module. The config file
//!   itself configures the library and isn't one of its modules.
//! - A library's name is what its config exports as `name` (`export let
//!   name = "chromarite";`), or else its config's first line as a slug
//!   (`# Hi There!` gives `hi-there`), or else its root's path under the
//!   work root, with `-` between directories, or else `unnamed`, as
//!   Kotlin's `libraryNameFor` decides ([`Config`]). Configs are settled
//!   as Temper, all of a work root's at once, as libraries are found
//!   ([`settle_configs`]). Two libraries of one name are an error, as in
//!   Kotlin.
//! - An import's path may start with a library's name, as
//!   `import("other-lib/sub")`, naming that library's `sub` directory,
//!   as Kotlin resolves it; the library's modules then build with the
//!   importer's ([`Library::libraries`]).
//! - Directories named `temper.out` or `temper.keep`, or starting with
//!   `.`, are skipped, as is what `.gitignore` and `.temperignore` files
//!   list ([`ignore`]): the work root's, those below it, and those above
//!   it in its git repository, where Kotlin reads only the nearest one
//!   at or above where it starts. A directory's `.temperignore` comes
//!   after its `.gitignore`, so it has the last word there. An ignore
//!   file named on the command line (`--ignorefile`, [`ignore_file`]),
//!   as Kotlin takes one, has the last word everywhere
//!   ([`find_libraries_ignoring`]). Kotlin also builds files outside any
//!   library as one more, which we leave out.
//!
//! Paths are kept as given, joined with `/`, which every platform reads,
//! so diagnostics show them relative to where the search started. Files
//! and directories are read through [`Sources`], which also says where
//! std's modules come from.

use crate::ignore::{self, IgnoreFile};
use crate::library::{Library, normalize};
use crate::sources::{Entry, Sources};
use std::path::Path;
use temper_syntax::lex::SourceKind;

/// The file whose directory is a library's root.
pub const CONFIG_FILE: &str = "config.temper.md";

/// A library's root directory and its source files, sorted, without its
/// config file, and what its config says.
#[derive(Debug, PartialEq)]
pub struct LibrarySources {
    pub root: String,
    pub files: Vec<String>,
    /// Its other files, sorted, which may be host code for its connected
    /// functions and classes (`_connected.rs`, `Counter.java`, …), each
    /// backend taking those with its extensions, as Kotlin's raw backend
    /// files.
    pub host_files: Vec<String>,
    pub config: Config,
}

/// What a library's config file says, settled as Temper with a build's
/// config classes ([`settle_configs`], [`crate::config`]): the string
/// values it exports, in order; its values for each config class, by
/// key; its problems; where each export's name is; and the library's
/// name.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Config {
    /// The library's name (see the module docs).
    pub name: String,
    pub exports: Vec<(String, String)>,
    pub configs: Vec<(String, Vec<(String, temper_tmpl::ConfigValue)>)>,
    pub problems: Vec<crate::config::Problem>,
    pub keys: Vec<(String, temper_syntax::tree::Span)>,
    /// Its text, which spans are in.
    pub text: String,
}

impl Config {
    /// A config file's text, not yet settled, for a library whose root is
    /// `path` under the work root (as `a/b`, or empty for the work root),
    /// named as if it exported no `name`.
    pub fn new(text: &str, path: &str) -> Config {
        let from_path = || {
            let joined = (path.split('/'))
                .filter(|p| !p.is_empty() && *p != ".")
                .collect::<Vec<_>>()
                .join("-");
            dashed(&joined)
        };
        let name = (text.lines().next().and_then(|l| dashed(&slug(l))))
            .or_else(from_path)
            .unwrap_or_else(|| "unnamed".to_string());
        Config {
            name,
            text: text.to_string(),
            ..Config::default()
        }
    }

    /// A config file's text settled alone, without config classes, as
    /// std's is.
    pub fn read(text: &str, path: &str) -> Config {
        let mut config = Config::new(text, path);
        settle_configs([&mut config], &crate::config::Classes::default());
        config
    }

    /// What it says, for package files: its string exports, and its
    /// values for each backend's config class.
    pub fn metadata(&self) -> temper_tmpl::Metadata {
        temper_tmpl::Metadata {
            exports: self.exports.clone(),
            configs: self.configs.clone(),
        }
    }

    /// Where a problem with what it sets for a config class goes: at its
    /// export of that class, or else of its name, or else at the start.
    fn span_for(&self, key: &str) -> temper_syntax::tree::Span {
        let at = |key: &str| {
            self.keys
                .iter()
                .find(|(k, _)| k == key)
                .map(|&(_, span)| span)
        };
        (at(key).or_else(|| at("name"))).unwrap_or_default()
    }

    /// A string the config exports, if it does.
    pub fn get(&self, key: &str) -> Option<&str> {
        (self.exports.iter())
            .find(|(k, _)| k == key)
            .map(|(_, v)| v.as_str())
    }

    /// The library's version, or Kotlin's default, `0.0.1`.
    pub fn version_or_default(&self) -> &str {
        self.get("version").unwrap_or("0.0.1")
    }
}

/// Settles the configs of a build's libraries with its config classes,
/// all at once ([`crate::config::settle_configs`]), keeping what each
/// says, and its name, if it exports one; then checks them against each
/// other, as their packages meet in one place (`PYTHONPATH`,
/// `node_modules`, a Cargo workspace). Two libraries taking one name for
/// a backend ([`temper_tmpl::ConfigType::names`], case folded), or a
/// `dependencies` item naming a library's package, std's, or the
/// runtime's (`temper-core`), is a problem in each config concerned.
pub fn settle_configs<'a>(
    configs: impl IntoIterator<Item = &'a mut Config>,
    classes: &crate::config::Classes,
) {
    use temper_tmpl::{ConfigKind, ConfigValue};
    let mut configs: Vec<&mut Config> = configs.into_iter().collect();
    let texts: Vec<&str> = configs.iter().map(|c| c.text.as_str()).collect();
    let said = crate::config::settle_configs(&texts, classes);
    for (config, said) in configs.iter_mut().zip(said) {
        if let Some((_, name)) =
            (said.exports.iter()).find(|(k, v)| k == "name" && !v.trim().is_empty())
        {
            config.name = name.clone();
        }
        config.exports = said.exports;
        config.configs = said.configs;
        config.problems = said.problems;
        config.keys = said.keys;
    }
    let metadata: Vec<temper_tmpl::Metadata> = configs.iter().map(|c| c.metadata()).collect();
    for t in &classes.types {
        let library = |name: &str| format!("library `{name}`");
        // Each name with what takes it: the runtime, std, then each
        // library, by its place.
        let mut taken: Vec<(String, String, Option<usize>)> = (t.runtime.iter())
            .map(|n| (n.to_string(), "the runtime, `temper-core`".into(), None))
            .collect();
        let std = (t.names)("std", &temper_tmpl::Metadata::default());
        taken.extend(std.into_iter().map(|n| (n, "std".into(), None)));
        for (i, config) in configs.iter().enumerate() {
            let names = (t.names)(&config.name, &metadata[i]);
            taken.extend(
                names
                    .into_iter()
                    .map(|n| (n, library(&config.name), Some(i))),
            );
        }
        let mut found: Vec<(usize, String)> = Vec::new();
        for (name, _, at) in &taken {
            let Some(i) = *at else { continue };
            let other = taken
                .iter()
                .find(|(n, _, there)| n == name && *there != Some(i));
            if let Some((_, who, _)) = other {
                let message = format!("{} names this library `{name}`, as it names {who}", t.class);
                found.push((i, message));
            }
        }
        for (i, config) in configs.iter().enumerate() {
            let lists = (config.configs.iter())
                .filter(|(key, _)| key == t.key)
                .flat_map(|(_, values)| values);
            for (property, value) in lists {
                let Some(ConfigKind::Strings { package, .. }) = (t.properties.iter())
                    .find(|p| p.name == property)
                    .map(|p| p.kind)
                else {
                    continue;
                };
                let ConfigValue::Strings(items) = value else {
                    continue;
                };
                for p in items.iter().filter_map(|item| package(item)) {
                    if let Some((_, whose, _)) = taken.iter().find(|(n, _, _)| *n == p) {
                        let message = format!(
                            "{}'s `{property}` lists `{p}`, which names {whose}",
                            t.class
                        );
                        found.push((i, message));
                    }
                }
            }
        }
        for (i, message) in found {
            let span = configs[i].span_for(t.key);
            let problem = crate::config::Problem::new(span, &message);
            configs[i].problems.push(problem);
        }
    }
}

/// A heading's text as GitHub slugs it: lowercase, without punctuation,
/// spaces as `-` (`# Hi There!` gives `hi-there`).
fn slug(line: &str) -> String {
    let text = line.trim_start_matches('#').trim();
    let mut out = String::new();
    for c in text.chars().flat_map(char::to_lowercase) {
        if c.is_alphanumeric() || c == '-' || c == '_' {
            out.push(c);
        } else if c == ' ' {
            out.push('-');
        }
    }
    out
}

/// Text made a dashed identifier as Kotlin's `DashedIdentifier.from`
/// makes one, as `temper init` names a library: ASCII letters lowercased;
/// what can't start an identifier dropped at the start, but a digit,
/// which gets `x-` before it; and any run of other characters a `-`,
/// as GitHub's heading anchors make them, with none at the end. `None`
/// if nothing is left.
pub fn dashed_from(text: &str) -> Option<String> {
    use temper_syntax::id_parts::{CONTINUE, MEDIAL, START, contains};
    #[derive(PartialEq)]
    enum State {
        Start,
        Continue,
        AfterMedial,
    }
    let mut out = String::new();
    let mut state = State::Start;
    for c in text.chars().map(|c| c.to_ascii_lowercase()) {
        match state {
            State::Start if contains(START, c) => {
                out.push(c);
                state = State::Continue;
            }
            State::Start if contains(CONTINUE, c) => {
                out.push_str("x-");
                out.push(c);
                state = State::Continue;
            }
            State::Start => {}
            State::Continue if contains(CONTINUE, c) => out.push(c),
            State::Continue if contains(MEDIAL, c) => {
                out.push(c);
                state = State::AfterMedial;
            }
            State::Continue => {
                out.push('-');
                state = State::AfterMedial;
            }
            State::AfterMedial if contains(CONTINUE, c) => {
                out.push(c);
                state = State::Continue;
            }
            State::AfterMedial => {}
        }
    }
    if state == State::AfterMedial && out.ends_with('-') {
        out.pop();
    }
    // Valid only ending after an identifier character.
    let valid = out.chars().last().is_some_and(|c| contains(CONTINUE, c))
        && out.chars().next().is_some_and(|c| contains(START, c));
    valid.then_some(out)
}

/// A name, if it's a dashed identifier as Kotlin's `DashedIdentifier`
/// allows: ASCII letters, digits, and `-` or `_` between them, starting
/// with a letter.
pub(crate) fn dashed(name: &str) -> Option<String> {
    let name = name.to_ascii_lowercase();
    let ok = name.starts_with(|c: char| c.is_ascii_lowercase())
        && !name.ends_with(['-', '_'])
        && !name.contains("--")
        && name
            .chars()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-' || c == '_');
    ok.then_some(name)
}

/// The work root for a starting directory (see the module docs), as a
/// path relative to `dir` when `dir` is relative.
pub fn work_root(sources: &Sources, dir: &str) -> String {
    let dir = slashed(dir);
    let Ok(absolute) = std::path::absolute(&dir) else {
        return dir;
    };
    // `absolute` keeps `..`, which `ancestors` would count as a level.
    let absolute = normalize(&slashed(&absolute.to_string_lossy()));
    // Each ancestor, `up` levels up, as a path from `dir`, with its entries.
    let ancestor = |up: usize| normalize(&format!("{dir}{}", "/..".repeat(up)));
    let levels: Vec<Vec<Entry>> = (0..Path::new(&absolute).ancestors().count())
        .map(|up| sources.list(&ancestor(up)).unwrap_or_default())
        .collect();
    let up = (levels.iter())
        .position(|entries| entries.iter().any(|e| e.name == "temper.out" && e.is_dir))
        .or_else(|| {
            (levels.iter()).rposition(|entries| entries.iter().any(|e| e.name == CONFIG_FILE))
        });
    ancestor(up.unwrap_or(0))
}

/// The libraries under a work root, in path order, with their configs
/// settled with a build's config classes ([`settle_configs`]).
pub fn find_libraries(
    sources: &Sources,
    root: &str,
    classes: &crate::config::Classes,
) -> Vec<LibrarySources> {
    find_libraries_ignoring(sources, root, None, classes)
}

/// The libraries under a work root, as [`find_libraries`] finds them,
/// skipping also what `extra` lists, which has the last word on any
/// path, as Kotlin's `--ignorefile` ([`ignore_file`]).
pub fn find_libraries_ignoring(
    sources: &Sources,
    root: &str,
    extra: Option<&IgnoreFile>,
    classes: &crate::config::Classes,
) -> Vec<LibrarySources> {
    let root = normalize(&slashed(root));
    let mut libraries = Vec::new();
    let mut ignores = ignores_above(sources, &root);
    let mut scanning = Scan {
        sources,
        libraries: &mut libraries,
        ignores: &mut ignores,
        extra,
    };
    scanning.scan(&root, None);
    for library in &mut libraries {
        let config = format!("{}/{CONFIG_FILE}", library.root);
        let text = sources.read(&config).map(|t| t.into_owned());
        let path = (library.root.strip_prefix(&root))
            .unwrap_or(&library.root)
            .trim_start_matches('/');
        library.config = Config::new(&text.unwrap_or_default(), path);
    }
    settle_configs(libraries.iter_mut().map(|l| &mut l.config), classes);
    libraries
}

/// The library a directory belongs to, of those in `libraries`: the one
/// with the deepest root that holds it.
pub fn library_of<'a>(libraries: &'a [LibrarySources], dir: &str) -> Option<&'a LibrarySources> {
    let holds = |root: &str| {
        root == "." && !dir.starts_with("..") && !dir.starts_with('/')
            || dir == root
            || dir.strip_prefix(root).is_some_and(|r| r.starts_with('/'))
    };
    (libraries.iter())
        .filter(|l| holds(&l.root))
        .max_by_key(|l| l.root.len())
}

/// The names that more than one library has, each once, in path order.
pub fn duplicate_names(libraries: &[LibrarySources]) -> Vec<&str> {
    let mut duplicates = Vec::new();
    for (i, library) in libraries.iter().enumerate() {
        let name = library.config.name.as_str();
        if libraries[..i].iter().any(|l| l.config.name == name) && !duplicates.contains(&name) {
            duplicates.push(name);
        }
    }
    duplicates
}

/// The ignore files' names, in the order a directory's are read, so a
/// `.temperignore` has the last word over a `.gitignore` beside it.
const IGNORE_FILES: [&str; 2] = [".gitignore", ".temperignore"];

/// An ignore file named on the command line, as Kotlin's `--ignorefile`
/// takes one: its patterns apply relative to its own directory, as in
/// Kotlin, to a work root at or below it, and match nothing under a work
/// root elsewhere. The error says why it can't be read.
pub fn ignore_file(sources: &Sources, root: &str, path: &str) -> Result<IgnoreFile, String> {
    let path = slashed(path);
    let text = sources.read(&path).map_err(|e| format!("{path}: {e}"))?;
    let absolute = |p: &str| {
        std::path::absolute(p)
            .map(|a| normalize(&slashed(&a.to_string_lossy())))
            .unwrap_or_else(|_| normalize(p))
    };
    let root = normalize(&slashed(root));
    let (root_abs, file_abs) = (absolute(&root), absolute(&path));
    let dir = file_abs.rsplit_once('/').map_or("", |(d, _)| d);
    let dir = if dir.is_empty() { "/" } else { dir };
    let under = |inner: &str, outer: &str| -> Option<String> {
        if inner == outer {
            return Some(String::new());
        }
        let outer = outer.trim_end_matches('/');
        (inner.strip_prefix(outer)?.strip_prefix('/')).map(str::to_string)
    };
    if let Some(prefix) = under(&root_abs, dir) {
        // At or above the work root.
        return Ok(IgnoreFile::parse(&text, scanned(&root), &prefix));
    }
    if let Some(rest) = under(dir, &root_abs) {
        // Below it, where the scan's paths reach it.
        let inside = match scanned(&root) {
            "" => rest,
            root => format!("{root}/{rest}"),
        };
        return Ok(IgnoreFile::parse(&text, &inside, ""));
    }
    Ok(IgnoreFile::parse("", scanned(&root), ""))
}

/// The ignore files above the work root, outermost first, up to its git
/// repository's root, the nearest directory holding `.git`: none outside
/// one, or for a work root that is one.
fn ignores_above(sources: &Sources, root: &str) -> Vec<IgnoreFile> {
    let has = |dir: &str, name: &str| {
        (sources.list(dir).unwrap_or_default().iter()).any(|e| e.name == name)
    };
    let Ok(absolute) = std::path::absolute(root) else {
        return Vec::new();
    };
    let absolute = normalize(&slashed(&absolute.to_string_lossy()));
    let names: Vec<&str> = absolute.split('/').filter(|s| !s.is_empty()).collect();
    let mut found = Vec::new();
    let mut dir = root.to_string();
    for up in 1..=names.len() {
        if has(&dir, ".git") {
            found.reverse();
            return found;
        }
        dir = normalize(&format!("{dir}/.."));
        // Found innermost first, and reversed at the end, so each
        // directory's last file goes first here.
        for name in IGNORE_FILES.iter().rev() {
            if has(&dir, name) {
                let prefix = names[names.len() - up..].join("/");
                if let Ok(text) = sources.read(&format!("{dir}/{name}")) {
                    found.push(IgnoreFile::parse(&text, scanned(root), &prefix));
                }
            }
        }
    }
    if has(&dir, ".git") {
        found.reverse();
        return found;
    }
    Vec::new()
}

/// A directory as the scan's paths start with it: nothing for `.`.
fn scanned(dir: &str) -> &str {
    if dir == "." { "" } else { dir }
}

/// A scan for libraries under a work root: what it has found, and the
/// ignore files that apply where it is, outermost first, then `extra`,
/// which has the last word.
struct Scan<'a> {
    sources: &'a Sources,
    libraries: &'a mut Vec<LibrarySources>,
    ignores: &'a mut Vec<IgnoreFile>,
    extra: Option<&'a IgnoreFile>,
}

impl Scan<'_> {
    /// Adds the sources under `dir` to the library whose root is the
    /// index `library` in `libraries`, or to a new one if `dir` has a
    /// config file, skipping what the ignore files, and `dir`'s own,
    /// list.
    fn scan(&mut self, dir: &str, library: Option<usize>) {
        let sources = self.sources;
        let mut entries = sources.list(dir).unwrap_or_default();
        let mut own = 0;
        for name in IGNORE_FILES {
            if entries.iter().any(|e| e.name == name && !e.is_dir) {
                if let Ok(text) = sources.read(&format!("{dir}/{name}")) {
                    self.ignores
                        .push(IgnoreFile::parse(&text, scanned(dir), ""));
                    own += 1;
                }
            }
        }
        let path_of = |name: &str| match dir {
            "." => name.to_string(),
            _ => format!("{dir}/{name}"),
        };
        let (ignores, extra) = (&*self.ignores, self.extra);
        entries.retain(|e| {
            let path = path_of(&e.name);
            let skip = (extra.and_then(|f| f.decides(&path, e.is_dir)))
                .unwrap_or_else(|| ignore::ignored(ignores, &path, e.is_dir));
            !skip
        });
        let library = if (entries.iter()).any(|e| e.name == CONFIG_FILE && !e.is_dir) {
            self.libraries.push(LibrarySources {
                root: dir.to_string(),
                files: Vec::new(),
                host_files: Vec::new(),
                config: Config::default(),
            });
            Some(self.libraries.len() - 1)
        } else {
            library
        };
        for Entry { name, is_dir } in entries {
            let path = path_of(&name);
            if is_dir {
                if !(name.starts_with('.') || name == "temper.out" || name == "temper.keep") {
                    self.scan(&path, library);
                }
            } else if let Some(i) = library {
                let library = &mut self.libraries[i];
                let config = name == CONFIG_FILE && library.root == dir;
                if is_source(&name) && !config {
                    library.files.push(path);
                } else if !config {
                    library.host_files.push(path);
                }
            }
        }
        let kept = self.ignores.len() - own;
        self.ignores.truncate(kept);
    }
}

/// Reads a library's sources and the modules they import, from std, and
/// from the libraries in `all` (those of its work root), which imports
/// name by their names. The error names a file that can't be read.
pub fn load_library(
    sources: &Sources,
    library: &LibrarySources,
    all: &[LibrarySources],
) -> Result<Library, String> {
    let mut loaded = Library::new();
    loaded.libraries = (all.iter())
        .map(|l| (l.config.name.clone(), l.root.clone()))
        .collect();
    for path in &library.files {
        let text = sources.read(path).map_err(|e| format!("{path}: {e}"))?;
        loaded.add_file(path, &text, source_kind(path));
    }
    load_imports(sources, &mut loaded);
    Ok(loaded)
}

/// Adds the files of each module that an `import` names, and of those
/// they import in turn. A directory that can't be read is left out, so
/// the import fails when settled.
pub fn load_imports(sources: &Sources, library: &mut Library) {
    let mut tried = Vec::new();
    loop {
        let missing: Vec<String> = library
            .missing_imports()
            .into_iter()
            .filter(|d| !tried.contains(d))
            .collect();
        if missing.is_empty() {
            return;
        }
        for dir in missing {
            // The module's files, sorted, leaving out those that can't
            // be read.
            let entries = sources.list(&dir).unwrap_or_default();
            for entry in entries.iter().filter(|e| !e.is_dir && is_source(&e.name)) {
                let path = format!("{dir}/{}", entry.name);
                if let Ok(text) = sources.read(&path) {
                    library.add_file(&path, &text, source_kind(&path));
                }
            }
            tried.push(dir);
        }
    }
}

/// Whether a file name is a Temper source's.
pub fn is_source(name: &str) -> bool {
    name.ends_with(".temper") || name.ends_with(".temper.md")
}

/// How to read a source: literate markdown, or plain Temper.
pub fn source_kind(path: &str) -> SourceKind {
    if path.ends_with(".md") {
        SourceKind::Markdown
    } else {
        SourceKind::Standalone
    }
}

/// A Windows path's drive letter in upper case: `c:/x` is `C:/x`.
pub fn upper_drive(path: &str) -> String {
    match path.as_bytes() {
        [d, b':', ..] if d.is_ascii_lowercase() => {
            format!("{}{}", d.to_ascii_uppercase() as char, &path[1..])
        }
        _ => path.to_string(),
    }
}

/// A path with `/` between its parts, on Windows too, where a verbatim
/// prefix (`\\?\C:\…`, as `fs::canonicalize` gives) is dropped
/// (`\\?\UNC\server\…` is `//server/…`), and a drive letter is upper
/// case (`c:\x` is `C:/x`), as editors' URIs may give it lower.
pub fn slashed(path: &str) -> String {
    if cfg!(windows) {
        let path = match (path.strip_prefix(r"\\?\UNC\"), path.strip_prefix(r"\\?\")) {
            (Some(unc), _) => format!(r"\\{unc}"),
            (None, Some(local)) => local.to_string(),
            (None, None) => path.to_string(),
        };
        upper_drive(&path.replace('\\', "/"))
    } else {
        path.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn drives_upper_case() {
        assert_eq!(upper_drive("c:/x"), "C:/x");
        assert_eq!(upper_drive("C:/x"), "C:/x");
        assert_eq!(upper_drive("/c:/x"), "/c:/x");
        assert_eq!(upper_drive("ab"), "ab");
    }

    /// Windows' path forms, as `/`-joined paths the frontend keeps.
    #[test]
    #[cfg(windows)]
    fn windows_paths_slashed() {
        assert_eq!(
            slashed(r"C:\Users\Zoë\My Projects"),
            "C:/Users/Zoë/My Projects"
        );
        assert_eq!(slashed(r"c:\x\y"), "C:/x/y");
        assert_eq!(slashed(r"\\?\C:\x\y"), "C:/x/y");
        assert_eq!(slashed(r"\\?\UNC\server\share\x"), "//server/share/x");
        assert_eq!(slashed(r"\\server\share\x"), "//server/share/x");
    }
}
