//! How JavaScript runs built libraries' tests, for `temper test -b js`:
//! Mocha (`mocha`, or `MOCHA`) in each library's package, `mocha test
//! --recursive` with its xunit reporter, Node finding `@temperlang/core`,
//! `@temperlang/std`, and the libraries in `temper.out/js/node_modules`,
//! linked to their directories, as npm's workspace that Kotlin makes
//! links them. `temper run -b js` imports the library's package under
//! Node (`node`, or `NODE`), as Kotlin's `index.js` does. Packages
//! that libraries' configs list are installed first, with npm (`npm`,
//! or `NPM`), into `temper.out/js/.deps`, and linked into
//! `node_modules` by their names too ([`install`]).

use std::path::Path;
use std::process::Command;
use temper_tmpl::tools::{install_once, junit_file, output, program, step, tool};
use temper_tmpl::{TestResults, Tested, Tools};

pub const TOOLS: Tools = Tools {
    prepare: Some(link),
    test,
    run,
};

fn run(dir: &Path, library: &Tested, _: &[Tested]) -> Result<bool, String> {
    // Package names have no quotes or backslashes, which configs can't
    // give, so they need no escaping.
    let name = crate::translate::js_name(&library.name, &library.metadata);
    program(
        Command::new(tool("NODE", "node"))
            .args(["--input-type=module", "-e"])
            .arg(format!("await import(\"{name}\");"))
            .current_dir(dir),
        "node",
    )
}

/// Installs the packages that libraries' configs list with `npm
/// install`, once for each list of them ([`install_once`]), giving
/// each one's name and where it is.
fn install(dir: &Path, all: &[Tested]) -> Result<Vec<(String, std::path::PathBuf)>, String> {
    let mut packages: Vec<(&str, &str)> = (all.iter())
        .flat_map(|l| crate::CONFIG.strings(&l.metadata, "dependencies"))
        .filter_map(|d| crate::npm_dependency(d))
        .collect();
    packages.sort();
    packages.dedup();
    let deps = dir.join(".deps");
    let manifest = match packages.is_empty() {
        true => String::new(),
        false => {
            let quoted = temper_tmpl::json_string;
            let lines: Vec<String> = (packages.iter())
                .map(|(name, version)| format!("    {}: {}", quoted(name), quoted(version)))
                .collect();
            format!(
                "{{\n  \"private\": true,\n  \"dependencies\": {{\n{}\n  }}\n}}\n",
                lines.join(",\n")
            )
        }
    };
    install_once(&deps, "package.json", &manifest, |deps| {
        step(
            Command::new(tool("NPM", "npm"))
                .args(["install", "--no-audit", "--no-fund", "--loglevel=error"])
                .current_dir(deps),
            "npm install",
        )
    })?;
    let modules = deps.join("node_modules");
    Ok((packages.iter())
        .map(|(name, _)| (name.to_string(), modules.join(name)))
        .collect())
}

/// Links the runtime, std, every library, and the packages that their
/// configs list into `node_modules`, by the names imports use.
fn link(dir: &Path, all: &[Tested]) -> Result<(), String> {
    let modules = dir.join("node_modules");
    let mut links = install(dir, all)?;
    links.extend([
        ("@temperlang/core".to_string(), dir.join("temper-core")),
        (
            crate::translate::js_name("std", &Default::default()),
            dir.join("std"),
        ),
    ]);
    links.extend((all.iter()).map(|l| {
        (
            crate::translate::js_name(&l.name, &l.metadata),
            dir.join(&l.name),
        )
    }));
    for (name, target) in links {
        let link = modules.join(&name);
        if let Some(parent) = link.parent() {
            std::fs::create_dir_all(parent).map_err(|e| format!("{}: {e}", parent.display()))?;
        }
        let _ = std::fs::remove_file(&link);
        let _ = std::fs::remove_dir_all(&link);
        let target = std::path::absolute(&target).unwrap_or(target);
        link_dir(&target, &link).map_err(|e| format!("{}: {e}", link.display()))?;
    }
    Ok(())
}

#[cfg(unix)]
fn link_dir(target: &Path, link: &Path) -> std::io::Result<()> {
    std::os::unix::fs::symlink(target, link)
}

#[cfg(windows)]
fn link_dir(target: &Path, link: &Path) -> std::io::Result<()> {
    std::os::windows::fs::symlink_dir(target, link)
}

fn test(dir: &Path, library: &Tested, _: &[Tested]) -> TestResults {
    let here = dir.join(&library.name);
    let results = std::path::absolute(here.join("test-results.xml")).map_err(|e| e.to_string())?;
    let _ = std::fs::remove_file(&results);
    let run = output(
        Command::new(tool("MOCHA", "mocha"))
            .args([
                "test",
                "--recursive",
                "--reporter",
                "xunit",
                "--reporter-option",
            ])
            .arg(format!("output={}", results.display()))
            .current_dir(&here),
        "mocha",
    )?;
    junit_file(&results, &run, str::to_string)
}
