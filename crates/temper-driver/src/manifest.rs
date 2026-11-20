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
//! before but no longer generates is removed; anything else there, which
//! Temper never wrote, is left alone. A manifest that's missing or
//! doesn't read means no warnings and no removals, never a failure.

use std::collections::BTreeMap;
use std::path::{Component, Path, PathBuf};
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
        }
    }

    /// Writes a library's files, by their paths in the backend's
    /// directory, those under `library/` among them, each only where it
    /// changed; then removes those Temper wrote under `library/` before
    /// but doesn't now, and the directories they leave empty.
    pub fn library(&mut self, library: &str, files: &[(String, String)]) -> Result<(), String> {
        for (file, text) in files {
            let path = self.dir.join(file);
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
        let prefix = format!("{library}/");
        let gone: Vec<String> = (self.old.iter().flat_map(|old| old.keys()))
            .filter(|f| f.starts_with(&prefix) && !self.new.contains_key(*f))
            .cloned()
            .collect();
        for file in gone {
            let path = self.dir.join(&file);
            if std::fs::symlink_metadata(&path).is_ok_and(|m| m.is_file()) {
                std::fs::remove_file(&path).map_err(|e| format!("{}: {e}", path.display()))?;
            }
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
        self.libraries.push(library.to_string());
        Ok(())
    }

    /// Writes the manifest: what was written this time, and what the old
    /// one said of directories not written this time, such as a library
    /// that failed. One that can't be written means only that the next
    /// build warns of nothing and removes nothing.
    pub fn finish(self) {
        let mut all = self.new;
        for (file, hash) in self.old.into_iter().flatten() {
            let top = file.split('/').next().unwrap_or_default();
            if !self.libraries.iter().any(|l| l == top) {
                all.entry(file).or_insert(hash);
            }
        }
        let mut text = format!("{HEADER}\n");
        for (file, hash) in &all {
            text.push_str(&format!("{hash:016x} {file}\n"));
        }
        let _ = write_if_changed(&self.dir.join(MANIFEST), text.as_bytes());
    }
}

/// A manifest's files and hashes, or `None` where any line isn't one:
/// a hash in hex, a space, and a relative path, `/` between its names,
/// none of them `..`, so a manifest can't name a file outside.
fn parse(text: &str) -> Option<BTreeMap<String, u64>> {
    let mut files = BTreeMap::new();
    for line in text.lines().filter(|l| !l.starts_with('#')) {
        let (hash, file) = line.split_once(' ')?;
        let hash = u64::from_str_radix(hash, 16).ok()?;
        let path = Path::new(file);
        let plain = (path.components()).all(|c| matches!(c, Component::Normal(_)));
        if file.is_empty() || !plain || file.contains('\\') || file.ends_with('/') {
            return None;
        }
        files.insert(file.to_string(), hash);
    }
    Some(files)
}

#[cfg(test)]
mod tests {
    use super::parse;

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
}
