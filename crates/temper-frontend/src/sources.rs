//! Where a build reads its sources: one [`Sources`] value that reads a
//! file's text and lists a directory, so the compiler never touches the
//! file system itself.
//!
//! A `Sources` is a table of layers, each a [`Source`] mounted at a path
//! prefix:
//!
//! - [`Source::Disk`], files on disk, as given or under a directory;
//! - [`Source::Std`], Temper's std library, built in ([`stdlib`]),
//!   read-only;
//! - [`Source::Memory`], files in a map, for the wasm build and tests.
//!
//! A path goes to the layers with the longest prefix it's under, as a
//! file system's mounts do, so std's layer at [`stdlib::DIR`] owns
//! `-std-/…` whatever the disk has. Among layers at the same prefix,
//! earlier ones win: a read takes the first that has the file, and a
//! listing merges theirs. So the binaries use the disk with std built in
//! ([`Sources::standard`]), `--std DIR` mounts a directory at std's root
//! instead ([`Sources::with_std_dir`]), and the wasm build and tests use
//! memory with std built in ([`Sources::memory`]). The language server
//! (and later watch mode) puts a memory layer of unsaved editor buffers
//! before the disk, at the same prefix, to overlay it.
//!
//! Paths are `/`-joined strings, as the rest of the frontend keeps them.

use crate::library::normalize;
use crate::project::slashed;
use crate::stdlib;
use std::borrow::Cow;
use std::fs;
use std::io::{self, ErrorKind::NotFound};

/// Layers of sources, read as the module docs describe.
#[derive(Debug, Default)]
pub struct Sources {
    layers: Vec<(String, Source)>,
}

/// One layer's files.
#[derive(Debug)]
pub enum Source {
    /// Files on disk under a directory, or as given if it's empty.
    Disk(String),
    /// Std's files, built in.
    Std,
    /// Files by path within the layer, with their texts.
    Memory(Vec<(String, String)>),
}

/// A directory's entry: a file's or subdirectory's name.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Entry {
    pub name: String,
    pub is_dir: bool,
}

impl Sources {
    /// No layers, to [`mount`](Sources::mount) them.
    pub fn new() -> Sources {
        Sources::default()
    }

    /// The disk, with std built in.
    pub fn standard() -> Sources {
        Sources::new()
            .mount(stdlib::DIR, Source::Std)
            .mount("", Source::Disk(String::new()))
    }

    /// The disk, with std's modules from the directory `dir` instead.
    pub fn with_std_dir(dir: &str) -> Sources {
        Sources::new()
            .mount(stdlib::DIR, Source::Disk(normalize(&slashed(dir))))
            .mount("", Source::Disk(String::new()))
    }

    /// Files in memory, by path and text, with std built in.
    pub fn memory(files: &[(&str, &str)]) -> Sources {
        Sources::new()
            .mount(stdlib::DIR, Source::Std)
            .mount("", Source::memory(files))
    }

    /// Adds a layer at `at`, a directory's path, or `""` for every path,
    /// after those already at that prefix.
    pub fn mount(mut self, at: &str, source: Source) -> Sources {
        self.layers
            .push((at.trim_end_matches('/').to_string(), source));
        self
    }

    /// A file's text, from the first layer that has it.
    pub fn read(&self, path: &str) -> io::Result<Cow<'_, str>> {
        let mut error: Option<io::Error> = None;
        for (source, start) in self.layers_for(path) {
            match source.read(&path[start..]) {
                Ok(text) => return Ok(text),
                // A layer's own failure says more than another's absence.
                Err(e) if error.as_ref().is_none_or(|old| old.kind() == NotFound) => {
                    error = Some(e)
                }
                Err(_) => {}
            }
        }
        Err(error.unwrap_or_else(|| NotFound.into()))
    }

    /// A directory's entries, sorted by name, merged across layers, or an
    /// error if no layer has the directory.
    pub fn list(&self, dir: &str) -> io::Result<Vec<Entry>> {
        let mut entries: Option<Vec<Entry>> = None;
        let mut error = None;
        for (source, start) in self.layers_for(dir) {
            match source.list(&dir[start..]) {
                Ok(listed) => {
                    let entries = entries.get_or_insert_with(Vec::new);
                    for entry in listed {
                        if !entries.iter().any(|e| e.name == entry.name) {
                            entries.push(entry);
                        }
                    }
                }
                Err(e) => {
                    error.get_or_insert(e);
                }
            }
        }
        let mut entries = entries.ok_or_else(|| error.unwrap_or_else(|| NotFound.into()))?;
        entries.sort_by(|a, b| a.name.cmp(&b.name));
        Ok(entries)
    }

    /// The layers a path goes to, those with the longest prefix it's
    /// under, each with where the path within it starts.
    fn layers_for(&self, path: &str) -> Vec<(&Source, usize)> {
        let longest = (self.layers.iter())
            .filter(|(at, _)| within(at, path).is_some())
            .map(|(at, _)| at.len())
            .max();
        (self.layers.iter())
            .filter(|(at, _)| Some(at.len()) == longest)
            .filter_map(|(at, source)| Some((source, within(at, path)?)))
            .collect()
    }
}

/// Where `path` within a layer at `at` starts, if it's under `at`.
fn within(at: &str, path: &str) -> Option<usize> {
    if at.is_empty() {
        return Some(0);
    }
    match path.strip_prefix(at)? {
        "" => Some(path.len()),
        rest => rest.starts_with('/').then_some(at.len() + 1),
    }
}

impl Source {
    /// Files in memory, by path and text.
    pub fn memory(files: &[(&str, &str)]) -> Source {
        let files = files.iter().map(|&(p, t)| (p.to_string(), t.to_string()));
        Source::Memory(files.collect())
    }

    fn read(&self, path: &str) -> io::Result<Cow<'_, str>> {
        match self {
            Source::Disk(dir) => fs::read_to_string(on_disk(dir, path)).map(Cow::Owned),
            Source::Std => (stdlib::FILES.iter())
                .find(|&&(p, _)| p == path)
                .map(|&(_, text)| Cow::Borrowed(text))
                .ok_or_else(|| NotFound.into()),
            Source::Memory(files) => (files.iter())
                .find(|(p, _)| p == path)
                .map(|(_, text)| Cow::Borrowed(text.as_str()))
                .ok_or_else(|| NotFound.into()),
        }
    }

    fn list(&self, dir: &str) -> io::Result<Vec<Entry>> {
        match self {
            Source::Disk(base) => Ok(fs::read_dir(on_disk(base, dir))?
                .filter_map(|e| {
                    let e = e.ok()?;
                    Some(Entry {
                        name: e.file_name().into_string().ok()?,
                        is_dir: e.file_type().ok()?.is_dir(),
                    })
                })
                .collect()),
            Source::Std => list_paths(stdlib::FILES.iter().map(|&(p, _)| p), dir),
            Source::Memory(files) => list_paths(files.iter().map(|(p, _)| p.as_str()), dir),
        }
    }
}

/// A layer's path on disk, under `dir` if it isn't empty.
fn on_disk(dir: &str, path: &str) -> String {
    match (dir, path) {
        ("", _) => path.to_string(),
        (_, "") => dir.to_string(),
        _ => format!("{dir}/{path}"),
    }
}

/// The entries of `dir` among files' paths, where `""` and `.` are the
/// top, or an error if no path is under it.
fn list_paths<'a>(paths: impl Iterator<Item = &'a str>, dir: &str) -> io::Result<Vec<Entry>> {
    let dir = if dir == "." { "" } else { dir };
    let mut entries: Vec<Entry> = Vec::new();
    for path in paths {
        let rest = match dir {
            "" => path,
            _ => match path.strip_prefix(dir).and_then(|r| r.strip_prefix('/')) {
                Some(rest) => rest,
                None => continue,
            },
        };
        let entry = match rest.split_once('/') {
            Some((name, _)) => Entry {
                name: name.to_string(),
                is_dir: true,
            },
            None => Entry {
                name: rest.to_string(),
                is_dir: false,
            },
        };
        if !entries.contains(&entry) {
            entries.push(entry);
        }
    }
    if entries.is_empty() && !dir.is_empty() {
        return Err(NotFound.into());
    }
    Ok(entries)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn names(sources: &Sources, dir: &str) -> Vec<String> {
        let entries = sources.list(dir).unwrap();
        let name = |e: Entry| e.name + if e.is_dir { "/" } else { "" };
        entries.into_iter().map(name).collect()
    }

    #[test]
    fn memory_reads_and_lists() {
        let sources = Sources::memory(&[("a/b.temper", "b"), ("a/c/d.temper", "d"), ("e", "e")]);
        assert_eq!(sources.read("a/b.temper").unwrap(), "b");
        assert_eq!(names(&sources, "."), ["a/", "e"]);
        assert_eq!(names(&sources, "a"), ["b.temper", "c/"]);
        assert_eq!(names(&sources, "a/c"), ["d.temper"]);
        let missing = sources.read("a/x.temper").unwrap_err();
        assert_eq!(missing.kind(), NotFound);
        assert!(sources.read("a").is_err());
        assert!(sources.list("x").is_err());
        assert!(sources.list("a/b").is_err());
    }

    #[test]
    fn embedded_std_lists_its_modules() {
        let sources = Sources::memory(&[("-std-/fake/x.temper", "")]);
        assert_eq!(
            names(&sources, "-std-"),
            [
                "collections/",
                "config.temper.md",
                "json/",
                "net/",
                "regex/",
                "temporal/",
                "testing/"
            ]
        );
        assert_eq!(names(&sources, "-std-/json"), ["json.temper.md"]);
        let json = sources.read("-std-/json/json.temper.md").unwrap();
        assert!(matches!(json, Cow::Borrowed(_)));
        // Std's mount owns its prefix, so the memory layer's file is
        // hidden, but not a sibling's.
        assert!(sources.list("-std-/fake").is_err());
        assert!(sources.read("-std-/fake/x.temper").is_err());
        assert!(sources.list("-std-/nope").is_err());
        assert!(sources.list("-std-x").is_err());
    }

    #[test]
    fn earlier_layers_win_at_a_prefix() {
        let sources = Sources::new()
            .mount("", Source::memory(&[("a/x", "edited")]))
            .mount("", Source::memory(&[("a/x", "saved"), ("a/y", "y")]))
            .mount("lib/", Source::memory(&[("z", "z")]));
        assert_eq!(sources.read("a/x").unwrap(), "edited");
        assert_eq!(sources.read("a/y").unwrap(), "y");
        assert_eq!(names(&sources, "a"), ["x", "y"]);
        assert_eq!(sources.read("lib/z").unwrap(), "z");
        assert_eq!(names(&sources, "lib"), ["z"]);
    }

    #[test]
    fn disk_under_a_directory() {
        let dir = std::env::temp_dir().join(format!("tempest-sources-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(dir.join("json")).unwrap();
        fs::write(dir.join("json/json.temper"), "on disk").unwrap();
        let sources = Sources::with_std_dir(&dir.to_string_lossy());
        assert_eq!(sources.read("-std-/json/json.temper").unwrap(), "on disk");
        assert_eq!(names(&sources, "-std-"), ["json/"]);
        // Nothing of the built-in copy shows through.
        assert!(sources.read("-std-/json/json.temper.md").is_err());
        let path = format!("{}/json/json.temper", dir.to_string_lossy());
        assert_eq!(sources.read(&path).unwrap(), "on disk");
        let _ = fs::remove_dir_all(&dir);
    }
}
