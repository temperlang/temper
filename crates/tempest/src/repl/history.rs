//! Where `temper repl` keeps its history, worked out by hand, as each
//! platform keeps an application's data: `$XDG_DATA_HOME/temper`, or
//! `~/.local/share/temper`, on Linux and other Unix systems;
//! `~/Library/Application Support/temper` on macOS; and
//! `%APPDATA%\temper` on Windows. The file is `repl-history.txt`, in
//! rustyline's format (`#V2`, then an entry a line, its line breaks and
//! backslashes escaped).
//!
//! What's typed may be private, a token pasted into a string say, and a
//! history file written by path could be swapped for a link to another
//! file, which would then be rewritten. So the file is opened once,
//! checked, and only ever read and written through that descriptor;
//! rustyline keeps the history in memory and never touches a file. On
//! Unix:
//!
//! - **The directory** must be a real directory, not a link, owned by
//!   the user running tempest; if it's readable or writable by others,
//!   it's made owner-only (0700). One that isn't the user's is refused.
//! - **The file** is opened in that directory, through its descriptor
//!   (`openat`), with `O_NOFOLLOW`, so a link isn't followed; it must be
//!   a regular file, the user's, with no other hard links, and it's made
//!   owner-only (0600) through the descriptor (`fchmod`).
//!
//! On Windows, `%APPDATA%` is the user's own by its access control
//! lists, as Windows sets them up; the directory and the file must not
//! be reparse points (links or junctions), and the file is opened
//! without following one (`FILE_FLAG_OPEN_REPARSE_POINT`), then checked
//! through its handle.
//!
//! Anything refused means no history, said once, and the session goes on.

use std::fs::{self, File};
use std::io::{self, Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};

/// The history file's name in its directory.
const FILE: &str = "repl-history.txt";

/// The first line of a file in rustyline's multi-line format.
const V2: &str = "#V2";

/// The history file's path, from the environment, if the platform's
/// variables say where; `None` if they don't.
pub fn path(env: &dyn Fn(&str) -> Option<String>) -> Option<PathBuf> {
    let set = |name: &str| env(name).filter(|v| !v.is_empty()).map(PathBuf::from);
    let dir = if cfg!(windows) {
        set("APPDATA")?
    } else if cfg!(target_os = "macos") {
        set("HOME")?.join("Library").join("Application Support")
    } else {
        match set("XDG_DATA_HOME").filter(|d| d.is_absolute()) {
            Some(dir) => dir,
            None => set("HOME")?.join(".local").join("share"),
        }
    };
    Some(dir.join("temper").join(FILE))
}

/// The history file, open, and checked ([module docs](self)).
pub struct History {
    file: File,
    /// Whether it has anything yet, even the format's line.
    begun: bool,
}

impl History {
    /// Opens the history file at `path`, its directory made if need be,
    /// both checked and made owner-only, or says why it can't be used.
    pub fn open(path: &Path) -> io::Result<History> {
        let dir = path.parent().unwrap_or(Path::new("."));
        let name = path.file_name().unwrap_or_default();
        let file = platform::open(dir, name)?;
        let begun = file.metadata()?.len() > 0;
        Ok(History { file, begun })
    }

    /// Its entries, oldest first, at most `max`, the newest; if there were
    /// more, the file is rewritten with only those.
    pub fn read(&mut self, max: usize) -> io::Result<Vec<String>> {
        let mut text = String::new();
        self.file.seek(SeekFrom::Start(0))?;
        let mut bytes = Vec::new();
        self.file.read_to_end(&mut bytes)?;
        text.push_str(&String::from_utf8_lossy(&bytes));
        let mut entries = parse(&text);
        if entries.len() > max {
            entries.drain(..entries.len() - max);
            self.file.set_len(0)?;
            self.begun = false;
            for entry in &entries {
                self.append(entry)?;
            }
        }
        Ok(entries)
    }

    /// Adds an entry at the end.
    pub fn append(&mut self, entry: &str) -> io::Result<()> {
        let mut line = String::new();
        if !self.begun {
            line.push_str(V2);
            line.push('\n');
        }
        line.push_str(&escape(entry));
        line.push('\n');
        // At the end, wherever reading left it: Unix's descriptor appends
        // anyway; Windows' is opened to write, not append, since an
        // append-only handle can't be truncated by `read`.
        self.file.seek(SeekFrom::End(0))?;
        self.file.write_all(line.as_bytes())?;
        self.file.flush()?;
        self.begun = true;
        Ok(())
    }
}

/// A history file's entries: in rustyline's multi-line format if it
/// starts with `#V2`, else a line each, as its older format was.
fn parse(text: &str) -> Vec<String> {
    let mut lines = text.lines();
    let v2 = text.starts_with(&format!("{V2}\n")) || text == V2;
    if v2 {
        lines.next();
    }
    let lines = lines.filter(|line| !line.is_empty());
    match v2 {
        true => lines.map(unescape).collect(),
        false => lines.map(String::from).collect(),
    }
}

/// An entry as a line: its line breaks as `\n`, its backslashes as `\\`.
fn escape(entry: &str) -> String {
    entry.replace('\\', "\\\\").replace('\n', "\\n")
}

/// A line as its entry, undoing [`escape`]; another escape stays as is.
fn unescape(line: &str) -> String {
    let mut out = String::with_capacity(line.len());
    let mut chars = line.chars();
    while let Some(c) = chars.next() {
        match (c, chars.clone().next()) {
            ('\\', Some('n')) => {
                out.push('\n');
                chars.next();
            }
            ('\\', Some('\\')) => {
                out.push('\\');
                chars.next();
            }
            (c, _) => out.push(c),
        }
    }
    out
}

/// Makes a directory and those it's in that are missing, each made
/// owner-only on Unix.
fn make_dir(dir: &Path) -> io::Result<()> {
    let mut builder = fs::DirBuilder::new();
    builder.recursive(true);
    #[cfg(unix)]
    std::os::unix::fs::DirBuilderExt::mode(&mut builder, 0o700);
    builder.create(dir)
}

#[cfg(unix)]
mod platform {
    use super::make_dir;
    use std::ffi::{CString, OsStr};
    use std::fs::{File, OpenOptions, Permissions};
    use std::io;
    use std::os::unix::ffi::OsStrExt;
    use std::os::unix::fs::{MetadataExt, OpenOptionsExt, PermissionsExt};
    use std::path::Path;

    /// Opens `name` in `dir`, both checked ([module docs](super)).
    pub fn open(dir: &Path, name: &OsStr) -> io::Result<File> {
        make_dir(dir)?;
        let dir = OpenOptions::new()
            .read(true)
            .custom_flags(libc::O_DIRECTORY | libc::O_NOFOLLOW | libc::O_CLOEXEC)
            .open(dir)
            .map_err(|e| refused(e, "the directory is a link, or can't be opened"))?;
        let me = temper_os::fs::effective_uid();
        let meta = dir.metadata()?;
        check_dir(meta.uid(), me)?;
        if meta.mode() & 0o077 != 0 {
            dir.set_permissions(Permissions::from_mode(0o700))?;
        }
        let name = CString::new(name.as_bytes()).map_err(io::Error::other)?;
        let flags =
            libc::O_RDWR | libc::O_APPEND | libc::O_CREAT | libc::O_NOFOLLOW | libc::O_CLOEXEC;
        let file = temper_os::fs::open_at(&dir, &name, flags, 0o600)
            .map_err(|e| refused(e, "the file is a link, or can't be opened"))?;
        let meta = file.metadata()?;
        check_file(meta.file_type().is_file(), meta.uid(), meta.nlink(), me)?;
        if meta.mode() & 0o077 != 0 {
            file.set_permissions(Permissions::from_mode(0o600))?;
        }
        Ok(file)
    }

    /// An error with what it means here, when a link is why.
    fn refused(e: io::Error, link: &str) -> io::Error {
        match e.raw_os_error() == Some(libc::ELOOP) || e.raw_os_error() == Some(libc::ENOTDIR) {
            true => io::Error::other(link.replace(", or can't be opened", "")),
            false => e,
        }
    }

    /// Whether a directory, by its owner, may hold the history of the
    /// user `me`: it must be theirs. (Its mode is then tightened, so
    /// others can't swap the file.)
    pub fn check_dir(owner: u32, me: u32) -> io::Result<()> {
        match owner == me {
            true => Ok(()),
            false => Err(io::Error::other(
                "the directory belongs to another user, so others could change the file",
            )),
        }
    }

    /// Whether the file opened is one to keep history in: a regular file,
    /// the user's, with no other names (hard links) elsewhere.
    pub fn check_file(regular: bool, owner: u32, links: u64, me: u32) -> io::Result<()> {
        if !regular {
            return Err(io::Error::other("the file isn't a regular file"));
        }
        if owner != me {
            return Err(io::Error::other("the file belongs to another user"));
        }
        if links != 1 {
            return Err(io::Error::other("the file has other names (hard links)"));
        }
        Ok(())
    }
}

#[cfg(windows)]
mod platform {
    use super::make_dir;
    use std::ffi::OsStr;
    use std::fs::{self, File, OpenOptions};
    use std::io;
    use std::os::windows::fs::{MetadataExt, OpenOptionsExt};
    use std::path::Path;

    const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x400;
    const FILE_FLAG_OPEN_REPARSE_POINT: u32 = 0x0020_0000;

    /// Opens `name` in `dir`, neither a reparse point ([module
    /// docs](super)).
    pub fn open(dir: &Path, name: &OsStr) -> io::Result<File> {
        make_dir(dir)?;
        let meta = fs::symlink_metadata(dir)?;
        if !meta.is_dir() || meta.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0 {
            return Err(io::Error::other("the directory is a link or a junction"));
        }
        let file = OpenOptions::new()
            .read(true)
            // Not `append`: Windows refuses `set_len` on an append-only
            // handle (access denied), and `read` truncates to trim.
            .write(true)
            .create(true)
            .custom_flags(FILE_FLAG_OPEN_REPARSE_POINT)
            .open(dir.join(name))?;
        let meta = file.metadata()?;
        if meta.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0 {
            return Err(io::Error::other("the file is a link"));
        }
        if !meta.is_file() {
            return Err(io::Error::other("the file isn't a regular file"));
        }
        Ok(file)
    }
}

#[cfg(not(any(unix, windows)))]
mod platform {
    use std::ffi::OsStr;
    use std::fs::File;
    use std::io;
    use std::path::Path;

    pub fn open(_: &Path, _: &OsStr) -> io::Result<File> {
        Err(io::Error::other("history isn't kept on this platform"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn finds_the_platforms_data_directory() {
        let env = |vars: &'static [(&'static str, &'static str)]| {
            move |name: &str| {
                (vars.iter())
                    .find(|(k, _)| *k == name)
                    .map(|(_, v)| v.to_string())
            }
        };
        let none = env(&[]);
        assert_eq!(path(&none), None);
        if cfg!(windows) {
            let found = path(&env(&[("APPDATA", r"C:\Users\a\AppData\Roaming")]));
            assert!(found.unwrap().ends_with(r"temper\repl-history.txt"));
        } else if cfg!(target_os = "macos") {
            let found = path(&env(&[("HOME", "/Users/a")]));
            let expected = "/Users/a/Library/Application Support/temper/repl-history.txt";
            assert_eq!(found, Some(PathBuf::from(expected)));
        } else {
            let home = env(&[("HOME", "/home/a")]);
            let expected = "/home/a/.local/share/temper/repl-history.txt";
            assert_eq!(path(&home), Some(PathBuf::from(expected)));
            let xdg = env(&[("HOME", "/home/a"), ("XDG_DATA_HOME", "/data")]);
            let expected = "/data/temper/repl-history.txt";
            assert_eq!(path(&xdg), Some(PathBuf::from(expected)));
            // A relative XDG_DATA_HOME is ignored, as the spec says.
            let relative = env(&[("HOME", "/home/a"), ("XDG_DATA_HOME", "data")]);
            let expected = "/home/a/.local/share/temper/repl-history.txt";
            assert_eq!(path(&relative), Some(PathBuf::from(expected)));
        }
    }

    /// A fresh directory for a test, removed after `f`.
    fn with_dir(test: &str, f: impl FnOnce(&Path)) {
        let dir =
            std::env::temp_dir().join(format!("tempest-history-{}-{test}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        f(&dir);
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn keeps_entries_in_rustylines_format() {
        with_dir("format", |dir| {
            let path = dir.join("temper").join(FILE);
            let mut history = History::open(&path).unwrap();
            assert_eq!(history.read(10).unwrap(), Vec::<String>::new());
            history.append("f(1,\n  2)").unwrap();
            history.append("\"a\\\\b\"").unwrap();
            let text = fs::read_to_string(&path).unwrap();
            assert_eq!(text, "#V2\nf(1,\\n  2)\n\"a\\\\\\\\b\"\n");
            let mut again = History::open(&path).unwrap();
            assert_eq!(again.read(10).unwrap(), ["f(1,\n  2)", "\"a\\\\b\""]);
            // Past the most kept, the oldest go, from the file too.
            again.append("3").unwrap();
            assert_eq!(again.read(2).unwrap(), ["\"a\\\\b\"", "3"]);
            assert_eq!(History::open(&path).unwrap().read(10).unwrap().len(), 2);
            // rustyline's older format, a line each.
            fs::write(&path, "1 + 2\nlet x = 3\n").unwrap();
            let mut old = History::open(&path).unwrap();
            assert_eq!(old.read(10).unwrap(), ["1 + 2", "let x = 3"]);
        });
    }

    #[cfg(unix)]
    #[test]
    fn makes_the_file_owner_only() {
        use std::os::unix::fs::PermissionsExt;
        with_dir("modes", |dir| {
            let path = dir.join("temper").join(FILE);
            History::open(&path).unwrap();
            let mode = |p: &Path| fs::metadata(p).unwrap().permissions().mode() & 0o777;
            assert_eq!(mode(&path), 0o600);
            assert_eq!(mode(path.parent().unwrap()), 0o700);
            // One readable by others is made owner-only again.
            fs::set_permissions(&path, fs::Permissions::from_mode(0o644)).unwrap();
            History::open(&path).unwrap();
            assert_eq!(mode(&path), 0o600);
            // A directory found writable by all is made owner-only.
            fs::set_permissions(path.parent().unwrap(), fs::Permissions::from_mode(0o777)).unwrap();
            History::open(&path).unwrap();
            assert_eq!(mode(path.parent().unwrap()), 0o700);
            // So is one that's new under one writable by all.
            let loose = dir.join("loose");
            fs::create_dir(&loose).unwrap();
            fs::set_permissions(&loose, fs::Permissions::from_mode(0o777)).unwrap();
            History::open(&loose.join(FILE)).unwrap();
            assert_eq!(mode(&loose), 0o700);
        });
    }

    /// Links aren't followed, as the file or its directory, nor is a
    /// file with another name; and once open, the file swapped for a
    /// link changes nothing but the history's own file.
    #[cfg(unix)]
    #[test]
    fn refuses_links() {
        use std::os::unix::fs::symlink;
        with_dir("links", |dir| {
            let victim = dir.join("victim.txt");
            fs::write(&victim, "victim's\n").unwrap();
            let real = dir.join("real");
            fs::create_dir(&real).unwrap();
            // The file a link.
            symlink(&victim, real.join(FILE)).unwrap();
            assert!(History::open(&real.join(FILE)).is_err());
            fs::remove_file(real.join(FILE)).unwrap();
            // The file a hard link.
            fs::hard_link(&victim, real.join(FILE)).unwrap();
            let why = History::open(&real.join(FILE)).err().unwrap();
            assert!(why.to_string().contains("hard links"), "{why}");
            fs::remove_file(real.join(FILE)).unwrap();
            // The directory a link.
            symlink(&real, dir.join("linked")).unwrap();
            assert!(History::open(&dir.join("linked").join(FILE)).is_err());
            // Swapped for a link once open.
            let path = real.join(FILE);
            let mut history = History::open(&path).unwrap();
            fs::remove_file(&path).unwrap();
            symlink(&victim, &path).unwrap();
            history.append("let secret = \"tok\"").unwrap();
            assert_eq!(fs::read_to_string(&victim).unwrap(), "victim's\n");
        });
    }

    #[cfg(unix)]
    #[test]
    fn refuses_another_users_directory() {
        assert!(platform::check_dir(1000, 1000).is_ok());
        assert!(platform::check_dir(0, 1000).is_err());
        assert!(platform::check_dir(1001, 1000).is_err());
        assert!(platform::check_file(true, 1000, 1, 1000).is_ok());
        assert!(platform::check_file(false, 1000, 1, 1000).is_err());
        assert!(platform::check_file(true, 0, 1, 1000).is_err());
        assert!(platform::check_file(true, 1000, 2, 1000).is_err());
    }

    #[cfg(windows)]
    #[test]
    fn refuses_links() {
        with_dir("links", |dir| {
            let real = dir.join("real");
            fs::create_dir(&real).unwrap();
            let victim = dir.join("victim.txt");
            fs::write(&victim, "victim's\n").unwrap();
            // Making a link needs a privilege or developer mode; without
            // one, there's nothing to test.
            if std::os::windows::fs::symlink_file(&victim, real.join(FILE)).is_err() {
                eprintln!("skipped: can't make a link here");
                return;
            }
            assert!(History::open(&real.join(FILE)).is_err());
            assert_eq!(fs::read_to_string(&victim).unwrap(), "victim's\n");
        });
    }
}
