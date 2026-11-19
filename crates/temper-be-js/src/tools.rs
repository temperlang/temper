//! How JavaScript runs built libraries' tests, for `temper test -b js`:
//! Node's own test runner (`node --test`, with `node` or `NODE`) in each
//! library's package, on every `.test.js` file under `test/`, all in one
//! process, as Mocha ran them for Kotlin, with no time limit but
//! `--timeout`'s, its JUnit reporter writing the results, Node finding
//! `@temperlang/core`, `@temperlang/std`, and the libraries in
//! `temper.out/js/node_modules`, linked to their directories, as npm's
//! workspace that Kotlin makes links them. `temper run -b js` imports the
//! library's package under Node (`node`, or `NODE`), as Kotlin's
//! `index.js` does.
//!
//! Tests need Node 22.8 or later: one process is
//! `--experimental-test-isolation=none` on 22.8 through 23.5 and
//! `--test-isolation=none` from 23.6, the name it has had since (Node 24
//! takes both). Node 22 also has the runner's globs and JUnit reporter.

use std::path::Path;
use std::process::Command;
use std::sync::OnceLock;
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
    let isolation = node_isolation()?;
    // One process, test isolation `none`, so a tests file that fails
    // as it loads is a failure saying why, not only that its process
    // failed, and module state is shared as it was under Mocha.
    let run = output(
        Command::new(tool("NODE", "node"))
            .args(["--test", isolation, "--test-reporter=junit"])
            .arg(format!("--test-reporter-destination={}", results.display()))
            .arg("test/**/*.test.js")
            .current_dir(&here),
        "node",
    )?;
    junit_file(&results, &run, str::to_string)
}

/// The flag running every tests file in one process under the Node that
/// `node --version` says it is, asked once per run.
fn node_isolation() -> Result<&'static str, String> {
    static FLAG: OnceLock<Result<&'static str, String>> = OnceLock::new();
    let flag = FLAG.get_or_init(|| {
        let run = output(Command::new(tool("NODE", "node")).arg("--version"), "node")?;
        isolation_flag(&String::from_utf8_lossy(&run.stdout))
    });
    flag.clone()
}

/// The flag running every tests file in one process under the Node whose
/// `node --version` is `version` (such as `v24.1.0`), or why that Node
/// can't run Temper's tests: Node 22.8 added the flag as
/// `--experimental-test-isolation`, and 23.6 renamed it
/// `--test-isolation`. Before 22.8 there's no such flag; running each
/// file in its own process instead would change what tests see (module
/// state no longer shared, a file failing as it loads reported only as
/// its process failing), so those versions fail clearly instead.
pub fn isolation_flag(version: &str) -> Result<&'static str, String> {
    let version = version.trim();
    let mut parts = version.strip_prefix('v').unwrap_or("").split('.');
    let mut number = || parts.next().and_then(|p| p.parse::<u32>().ok());
    let (Some(major), Some(minor)) = (number(), number()) else {
        return Err(format!(
            "temper test -b js couldn't read Node's version from `node --version`: {version:?}"
        ));
    };
    match (major, minor) {
        (..22, _) => Err(format!(
            "temper test -b js needs Node 22 or later; found {version}"
        )),
        (22, ..8) => Err(format!(
            "temper test -b js needs Node 22.8 or later on the 22 line, \
             for --experimental-test-isolation; found {version}"
        )),
        (22, _) | (23, ..6) => Ok("--experimental-test-isolation=none"),
        _ => Ok("--test-isolation=none"),
    }
}

#[cfg(test)]
mod tests {
    use super::isolation_flag;

    #[test]
    fn stable_flag_from_23_6() {
        for v in ["v23.6.0", "v23.11.1", "v24.0.0", "v24.22.2\n", "v25.1.0"] {
            assert_eq!(isolation_flag(v), Ok("--test-isolation=none"), "{v}");
        }
    }

    #[test]
    fn experimental_flag_from_22_8() {
        for v in ["v22.8.0", "v22.21.1", "v23.0.0", "v23.5.0"] {
            assert_eq!(
                isolation_flag(v),
                Ok("--experimental-test-isolation=none"),
                "{v}"
            );
        }
    }

    #[test]
    fn early_22_fails_clearly() {
        for v in ["v22.0.0", "v22.7.0"] {
            assert_eq!(
                isolation_flag(v),
                Err(format!(
                    "temper test -b js needs Node 22.8 or later on the 22 line, \
                     for --experimental-test-isolation; found {v}"
                ))
            );
        }
    }

    #[test]
    fn before_22_fails_clearly() {
        for v in ["v20.19.5", "v21.7.3", "v18.0.0"] {
            assert_eq!(
                isolation_flag(v),
                Err(format!(
                    "temper test -b js needs Node 22 or later; found {v}"
                ))
            );
        }
    }

    #[test]
    fn malformed_version_fails_clearly() {
        for v in ["", "22.8.0", "v", "vx.y.z", "v22", "node v24.0.0"] {
            assert_eq!(
                isolation_flag(v),
                Err(format!(
                    "temper test -b js couldn't read Node's version from \
                     `node --version`: {:?}",
                    v.trim()
                )),
                "{v:?}"
            );
        }
    }
}
