//! How JavaScript runs built libraries' tests, for `temper test -b js`:
//! Mocha (`mocha`, or `MOCHA`) in each library's package, `mocha test
//! --recursive` with its xunit reporter, Node finding `@temperlang/core`,
//! `@temperlang/std`, and the libraries in `temper.out/js/node_modules`,
//! linked to their directories, as npm's workspace that Kotlin makes
//! links them. `temper run -b js` imports the library's package under
//! Node (`node`, or `NODE`), as Kotlin's `index.js` does.

use std::path::Path;
use std::process::Command;
use temper_tmpl::tools::{junit_file, output, program, tool};
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

/// Links the runtime, std, and every library into `node_modules`, by the
/// names imports use.
fn link(dir: &Path, all: &[Tested]) -> Result<(), String> {
    let modules = dir.join("node_modules");
    let mut links = vec![
        ("@temperlang/core".to_string(), dir.join("temper-core")),
        ("@temperlang/std".to_string(), dir.join("std")),
    ];
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
