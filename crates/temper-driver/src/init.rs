//! `temper init [-o DIR]`: a new project's skeleton, as Kotlin Temper's
//! `temper init` writes it, file for file:
//!
//! - `DIR/src/config.temper.md`, which names the library, and
//! - `DIR/src/NAME.temper.md`, a first source file to start from.
//!
//! The library's name is DIR's own name, dashed as Kotlin dashes it
//! ([`temper_tmpl::naming::dash_case`], then Kotlin's `DashedIdentifier`
//! rules, [`temper_frontend::project::dashed_from`]): `MyProj` and
//! `my_proj` are `my-proj`, `config` is `config-lib` (so it isn't
//! `config.temper.md`), and a name with nothing left is `untitled`. The
//! files' titles come from it ([`temper_tmpl::naming::dashed_title`]).
//!
//! DIR is made if it isn't there. As in Kotlin, it fails if DIR already
//! has a `src` directory or a `config.temper.md`, and writes nothing then.

use std::path::Path;
use temper_frontend::project::{CONFIG_FILE, dashed_from};
use temper_tmpl::naming::{dash_case, dashed_title};

/// What `init` made: the library's name, and its directory, as the file
/// system names it.
#[derive(Debug, PartialEq)]
pub struct Initialized {
    pub name: String,
    pub dir: std::path::PathBuf,
}

/// Writes a project skeleton in `dir`, or says why it didn't, in
/// Kotlin's words.
pub fn init(dir: &Path) -> Result<Initialized, String> {
    let failed = |_| "Failed to create project directory".to_string();
    std::fs::create_dir_all(dir).map_err(failed)?;
    // The real path, for the directory's name as the file system has it.
    let real = std::fs::canonicalize(dir).map_err(failed)?;
    let src = dir.join("src");
    if src.exists() {
        return Err("Directory src already exists".into());
    }
    if dir.join(CONFIG_FILE).exists() {
        return Err("Temper config already exists".into());
    }
    let dir_name = real
        .file_name()
        .map_or(String::new(), |n| n.to_string_lossy().into_owned());
    let name = library_name(&dir_name);
    let title = dashed_title(&name);
    std::fs::create_dir_all(&src).map_err(failed)?;
    let write = |path: &Path, text: String| {
        std::fs::write(path, text).map_err(|e| format!("{}: {e}", path.display()))
    };
    write(&src.join(CONFIG_FILE), config(&title, &name))?;
    write(&src.join(format!("{name}.temper.md")), module(&title))?;
    Ok(Initialized { name, dir: real })
}

/// A directory's name as a library's, as Kotlin's `chimericToDash`.
pub fn library_name(dir_name: &str) -> String {
    let dashed = dash_case(dir_name).replace('_', "-");
    let name = dashed_from(&dashed).unwrap_or_else(|| "untitled".to_string());
    // So it doesn't clash with config.temper.md.
    match name.as_str() {
        "config" => "config-lib".to_string(),
        _ => name,
    }
}

/// The config file, as Kotlin's `initConfigContent`.
fn config(title: &str, name: &str) -> String {
    format!(
        "# {title}\n\
         \n\
         This file is used for library configuration.\n\
         \n\
         The `name` export defines the library name:\n\
         \n\
         \x20   export let name = \"{name}\";\n\
         \n\
         By default, the current directory is imported as a module. It can import\n\
         other module directories. See Temper documentation for additional config\n\
         options.\n"
    )
}

/// The first source file, as Kotlin's `generateModule`.
fn module(title: &str) -> String {
    format!(
        "# Implementation for {title}\n\
         \n\
         Library discussion goes here.\n\
         \n\
         \x20   // Library implementation code goes here.\n\
         \n\
         Additional documentation and code blocks may follow.\n"
    )
}

#[cfg(test)]
mod tests {
    use super::library_name;

    /// As Kotlin's `temper init` names libraries (checked 2026-11-09).
    #[test]
    fn names_as_kotlin() {
        for (dir, name) in [
            ("my-Proj", "my-proj"),
            ("MyProj", "my-proj"),
            ("my_proj", "my-proj"),
            ("fooBar2", "foo-bar2"),
            ("123abc", "x-123-abc"),
            ("a b", "a-b"),
            ("XMLParser", "xml-parser"),
            ("__x", "x"),
            ("a--b", "a-b"),
            ("config", "config-lib"),
            ("---", "untitled"),
            // Kotlin's JVM fails on this path in a C locale.
            ("Café", "café"),
        ] {
            assert_eq!(library_name(dir), name, "{dir}");
        }
    }
}
