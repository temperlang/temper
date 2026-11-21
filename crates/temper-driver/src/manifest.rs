//! What `temper build` writes to `temper.out/BACKEND`, and its manifest
//! there, `.temper-manifest`: each file it generated, by its path, with
//! a hash of its text ([`fnv64`]).
//!
//! Generated code is the same from one build to the next, so a file is
//! written only where what's on disk differs from what it should hold:
//! unchanged files keep their times, and each language's own
//! incremental build (CMake, Cargo, MSBuild) skips what they build. A
//! file edited by hand differs, so it's written again, undoing the
//! edit, and its new time has the tools build it again; where the
//! manifest says Temper wrote something else there, that's said once
//! ([`Writer::edited`]). A file Temper wrote to a library's directory
//! before but no longer generates is removed, unless it was edited
//! since, when it's kept, said once ([`Writer::kept`]); anything else
//! there, which Temper never wrote, is left alone. Nothing is written
//! or removed through a symbolic link ([`Writer::inside`]).
//!
//! A manifest that's missing or doesn't read means no warnings and no
//! removals, never a failure. So files of modules removed while it was
//! missing stay, and Java's and C#'s builds, which compile every source
//! file in a library's directory, still compile them; removing
//! `temper.out/BACKEND` starts afresh.

use std::collections::{BTreeMap, HashSet};
use std::path::{Path, PathBuf};
use temper_tmpl::files::{fnv64, write_if_changed};

/// The manifest's name in `temper.out/BACKEND`.
pub const MANIFEST: &str = ".temper-manifest";

/// Its first line.
const HEADER: &str = "# Written by Temper: each file it generated here, with a hash of its text.";

/// Writes generated files to a backend's directory, keeping its
/// manifest.
pub struct Writer {
    dir: PathBuf,
    /// What the manifest said, where it read.
    old: Option<BTreeMap<String, u64>>,
    /// What's written this time.
    new: BTreeMap<String, u64>,
    /// The directories written this time, whose files the old manifest
    /// lists are now replaced by `new`'s.
    libraries: Vec<String>,
    /// Generated files that had been edited, written again.
    pub edited: Vec<PathBuf>,
    /// Files no longer generated, kept as they'd been edited.
    pub kept: Vec<PathBuf>,
}

impl Writer {
    /// A writer for `dir`, reading its manifest.
    pub fn open(dir: &Path) -> Writer {
        let old = std::fs::read_to_string(dir.join(MANIFEST))
            .ok()
            .and_then(|text| parse(&text));
        Writer {
            dir: dir.to_path_buf(),
            old,
            new: BTreeMap::new(),
            libraries: Vec::new(),
            edited: Vec::new(),
            kept: Vec::new(),
        }
    }

    /// Writes a library's files, by their paths in the backend's
    /// directory, those under `library/` among them, each only where it
    /// changed. First it removes those Temper wrote under `library/`
    /// before but doesn't now, and the directories they leave empty, but
    /// keeps one edited since ([`Writer::kept`]). Removing first, a file
    /// renamed only in case, on a filesystem that ignores case, is
    /// written afresh, not removed as the old name. It writes and removes
    /// nothing through a symbolic link ([`Writer::inside`]).
    pub fn library(&mut self, library: &str, files: &[(String, String)]) -> Result<(), String> {
        // Its files written so far replace the old manifest's, even where
        // one fails, so they're not said to be edited next time.
        let written = self.write_library(library, files);
        self.libraries.push(library.to_string());
        written
    }

    fn write_library(&mut self, library: &str, files: &[(String, String)]) -> Result<(), String> {
        let prefix = format!("{library}/");
        let now: HashSet<&str> = files.iter().map(|(f, _)| f.as_str()).collect();
        let gone: Vec<(String, u64)> = (self.old.iter().flatten())
            .filter(|(f, _)| f.starts_with(&prefix) && !now.contains(f.as_str()))
            .map(|(f, h)| (f.clone(), *h))
            .collect();
        for (file, wrote) in gone {
            let path = self.inside(&file)?;
            if !std::fs::symlink_metadata(&path).is_ok_and(|m| m.is_file()) {
                continue;
            }
            if std::fs::read(&path).is_ok_and(|text| fnv64(&text) != wrote) {
                self.kept.push(path);
                continue;
            }
            std::fs::remove_file(&path).map_err(|e| format!("{}: {e}", path.display()))?;
            // Directories it leaves empty, up to the library's own.
            let top = self.dir.join(library);
            let mut parent = path.parent();
            while let Some(dir) = parent.filter(|d| *d != top && d.starts_with(&top)) {
                if std::fs::remove_dir(dir).is_err() {
                    break;
                }
                parent = dir.parent();
            }
        }
        for (file, text) in files {
            let path = self.inside(file)?;
            let hash = fnv64(text.as_bytes());
            let on_disk = std::fs::read(&path).ok();
            if on_disk.as_deref() != Some(text.as_bytes()) {
                let wrote = self.old.as_ref().and_then(|old| old.get(file));
                if let (Some(on_disk), Some(&wrote)) = (&on_disk, wrote) {
                    if fnv64(on_disk) != wrote {
                        self.edited.push(path.clone());
                    }
                }
                write_if_changed(&path, text.as_bytes())
                    .map_err(|e| format!("{}: {e}", path.display()))?;
            }
            self.new.insert(file.clone(), hash);
        }
        Ok(())
    }

    /// `file`'s path in the backend's directory, or an error where that
    /// directory, or any directory on the way, or the file itself, is a
    /// symbolic link, which a build mustn't follow: a link checked in
    /// as `temper.out/py/a/a.py`, or as a directory there, could
    /// otherwise have a build overwrite or remove a file anywhere.
    /// A `file` that isn't [`plain`] is an error too, so nothing is
    /// written outside, whatever a library is named.
    fn inside(&self, file: &str) -> Result<PathBuf, String> {
        if !plain(file) {
            return Err(format!("{file}: not a plain relative path; not written"));
        }
        let mut path = self.dir.clone();
        let check = |path: &Path| match std::fs::symlink_metadata(path) {
            Ok(meta) if meta.file_type().is_symlink() => Err(format!(
                "{} is a symbolic link, which Temper won't write through",
                path.display()
            )),
            _ => Ok(()),
        };
        check(&path)?;
        for name in file.split('/') {
            path.push(name);
            check(&path)?;
        }
        Ok(path)
    }

    /// Writes the manifest: what was written this time, and what the old
    /// one said of directories not written this time, such as a library
    /// that failed. One that can't be written means only that the next
    /// build warns of nothing and removes nothing.
    pub fn finish(self) {
        let mut all = self.new.clone();
        for (file, hash) in self.old.iter().flatten() {
            let top = file.split('/').next().unwrap_or_default();
            if !self.libraries.iter().any(|l| l == top) {
                all.entry(file.clone()).or_insert(*hash);
            }
        }
        let mut text = format!("{HEADER}\n");
        for (file, hash) in &all {
            text.push_str(&format!("{hash:016x} {file}\n"));
        }
        if let Ok(path) = self.inside(MANIFEST) {
            let _ = write_if_changed(&path, text.as_bytes());
        }
    }
}

/// A manifest's files and hashes, or `None` where any line isn't one:
/// a hash in hex, a space, and a [`plain`] path, so a manifest can't
/// name a file outside.
fn parse(text: &str) -> Option<BTreeMap<String, u64>> {
    let mut files = BTreeMap::new();
    for line in text.lines().filter(|l| !l.starts_with('#')) {
        let (hash, file) = line.split_once(' ')?;
        let hash = u64::from_str_radix(hash, 16).ok()?;
        if !plain(file) {
            return None;
        }
        files.insert(file.to_string(), hash);
    }
    Some(files)
}

/// Whether a path is relative and plain, so it stays inside wherever
/// it's joined: names with `/` between, none of them empty, `.`, or
/// `..`, nor holding `\` or `:`, which Windows reads as separators or a
/// drive.
fn plain(file: &str) -> bool {
    (file.split('/')).all(|name| !matches!(name, "" | "." | "..") && !name.contains(['\\', ':']))
}

#[cfg(test)]
mod tests {
    use super::{Writer, parse};
    use std::fs;
    use std::path::{Path, PathBuf};
    use temper_tmpl::files::fnv64;

    /// A fresh directory for a test, with `temper.out/py` in it.
    fn scratch(name: &str) -> (PathBuf, PathBuf) {
        let root =
            std::env::temp_dir().join(format!("temper-manifest-{}-{name}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        let py = root.join("temper.out/py");
        fs::create_dir_all(&py).unwrap();
        (root, py)
    }

    /// A manifest in `dir` listing `files`, each with its text's hash.
    fn manifest(dir: &Path, files: &[(&str, &str)]) {
        let mut text = String::from("# manifest\n");
        for (file, body) in files {
            text.push_str(&format!("{:016x} {file}\n", fnv64(body.as_bytes())));
        }
        fs::write(dir.join(super::MANIFEST), text).unwrap();
    }

    fn write(path: &Path, text: &str) {
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, text).unwrap();
    }

    #[test]
    fn manifests_name_only_files_inside() {
        let ok = parse("# x\n00000000000000ff a/b.py\n").unwrap();
        assert_eq!(ok.get("a/b.py"), Some(&255));
        for bad in [
            "zz a/b.py\n",
            "ff\n",
            "ff /etc/passwd\n",
            "ff a/../../x\n",
            "ff ./a\n",
            "ff a\\b\n",
            "ff a/\n",
            "ff \n",
        ] {
            assert_eq!(parse(bad), None, "{bad}");
        }
    }

    /// Nothing is written outside the backend's directory: a library
    /// or file named with `..`, an absolute path, or an empty name is an
    /// error, and a manifest naming one is ignored, removing nothing.
    #[test]
    fn writes_stay_inside() {
        let (root, py) = scratch("inside");
        for library in [
            "../../escaped",
            "..",
            "/abs",
            "a//b",
            "",
            ".",
            "a\\b",
            "c:x",
        ] {
            let file = format!("{library}/x.py");
            let mut writer = Writer::open(&py);
            assert!(
                writer.library(library, &[(file, "x".into())]).is_err(),
                "{library}"
            );
        }
        let mut writer = Writer::open(&py);
        let files = [("a/../../escaped.py".to_string(), "x".to_string())];
        assert!(writer.library("a", &files).is_err());
        assert!(!root.join("escaped").exists() && !root.join("escaped.py").exists());
        let entries = |dir: &Path| fs::read_dir(dir).unwrap().count();
        assert_eq!(entries(&root), 1);
        assert_eq!(entries(&root.join("temper.out")), 1);
        // A manifest naming a file outside is ignored: nothing's removed.
        write(&root.join("victim.py"), "old");
        manifest(&py, &[("a/../../victim.py", "old")]);
        let mut writer = Writer::open(&py);
        writer.library("a", &[]).unwrap();
        assert!(root.join("victim.py").is_file());
        let _ = fs::remove_dir_all(root);
    }

    /// A library that fails partway still records the files it wrote,
    /// so the next build doesn't say they were edited.
    #[test]
    fn partial_writes_are_recorded() {
        let (root, py) = scratch("partial");
        manifest(&py, &[("a/x.py", "old")]);
        write(&py.join("a/x.py"), "old");
        let mut writer = Writer::open(&py);
        let files = [("a/x.py", "new"), ("a/../bad.py", "x")].map(|(f, t)| (f.into(), t.into()));
        assert!(writer.library("a", &files).is_err());
        writer.finish();
        let mut writer = Writer::open(&py);
        writer
            .library("a", &[("a/x.py".into(), "newer".into())])
            .unwrap();
        assert!(writer.edited.is_empty(), "{:?}", writer.edited);
        let _ = fs::remove_dir_all(root);
    }

    /// A file no longer generated goes, but not one edited since, which
    /// stays, said of once; nor does the manifest list it after, so
    /// it's the user's own from then on.
    #[test]
    fn edited_files_no_longer_generated_stay() {
        let (root, py) = scratch("kept");
        manifest(&py, &[("a/gone.py", "old"), ("a/edited.py", "old")]);
        write(&py.join("a/gone.py"), "old");
        write(&py.join("a/edited.py"), "mine");
        let mut writer = Writer::open(&py);
        writer.library("a", &[]).unwrap();
        assert!(!py.join("a/gone.py").exists());
        assert_eq!(fs::read_to_string(py.join("a/edited.py")).unwrap(), "mine");
        assert_eq!(writer.kept, [py.join("a/edited.py")]);
        writer.finish();
        let mut writer = Writer::open(&py);
        writer.library("a", &[]).unwrap();
        assert!(writer.kept.is_empty());
        assert!(py.join("a/edited.py").is_file());
        let _ = fs::remove_dir_all(root);
    }

    /// A module renamed only in case: on a filesystem that ignores case,
    /// as macOS's and Windows' do, the old name is the new file, which
    /// must still be there.
    #[test]
    fn a_rename_in_case_keeps_the_new_file() {
        let (root, py) = scratch("case");
        manifest(&py, &[("a/Foo.py", "x")]);
        write(&py.join("a/Foo.py"), "x");
        let mut writer = Writer::open(&py);
        writer
            .library("a", &[("a/foo.py".into(), "x".into())])
            .unwrap();
        writer.finish();
        assert_eq!(fs::read_to_string(py.join("a/foo.py")).unwrap(), "x");
        let _ = fs::remove_dir_all(root);
    }

    /// Nothing is written or removed through a symbolic link in
    /// `temper.out`, as a file, a directory on the way, or the backend's
    /// directory: the build fails, saying so, and what's outside stays.
    #[test]
    #[cfg(unix)]
    fn links_are_not_followed() {
        use std::os::unix::fs::symlink;
        let (root, py) = scratch("links");
        let outside = root.join("outside");
        write(&outside.join("precious.txt"), "precious");
        write(&outside.join("gone.py"), "old");
        let linked = |writer: Result<(), String>| {
            let why = writer.unwrap_err();
            assert!(why.contains("is a symbolic link"), "{why}");
        };
        // A generated file's path.
        symlink(outside.join("precious.txt"), py.join("a.py")).unwrap();
        linked(Writer::open(&py).library("a", &[("a.py".into(), "new".into())]));
        // A directory on the way to one.
        fs::create_dir_all(py.join("b")).unwrap();
        symlink(&outside, py.join("b/b")).unwrap();
        let files = [("b/b/precious.txt".into(), "new".into())];
        linked(Writer::open(&py).library("b", &files));
        // A directory on the way to one no longer generated.
        manifest(&py, &[("c/sub/gone.py", "old")]);
        fs::create_dir_all(py.join("c")).unwrap();
        symlink(&outside, py.join("c/sub")).unwrap();
        linked(Writer::open(&py).library("c", &[]));
        // The backend's directory.
        let java = root.join("temper.out/java");
        symlink(&outside, &java).unwrap();
        linked(Writer::open(&java).library("d", &[("d.java".into(), "new".into())]));
        Writer::open(&java).finish();
        assert!(!outside.join(super::MANIFEST).exists());
        assert_eq!(
            fs::read_to_string(outside.join("precious.txt")).unwrap(),
            "precious"
        );
        assert_eq!(fs::read_to_string(outside.join("gone.py")).unwrap(), "old");
        assert!(!outside.join("d.java").exists());
        let _ = fs::remove_dir_all(root);
    }
}
