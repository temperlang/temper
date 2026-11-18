//! How Rust runs built libraries' tests, for `temper test -b rust`:
//! `cargo test` in each library's crate (`CARGO`), offline, with one
//! target directory, `temper.out/rust/target`, for them all, reading
//! what it prints. `temper run -b rust` builds the library's crate the
//! same way, quietly, then runs its program (`src/main.rs`), as
//! Kotlin's `cargo run` does.

use std::path::Path;
use std::process::Command;
use temper_tmpl::tools::{output, program, step, tool, tool_output};
use temper_tmpl::{TestResults, Tested, Tools};

pub const TOOLS: Tools = Tools {
    prepare: None,
    test,
    run,
};

fn run(dir: &Path, library: &Tested, _: &[Tested]) -> Result<bool, String> {
    let target = std::path::absolute(dir.join("target")).map_err(|e| e.to_string())?;
    // The package's binary, as its config may name it ([`crate::bin_name`]).
    let bin = crate::bin_name(&crate::translate::rust_name(
        &library.name,
        &library.metadata,
    ));
    step(
        Command::new(tool("CARGO", "cargo"))
            .args(["build", "--offline", "--bin", &bin])
            .env("CARGO_TARGET_DIR", &target)
            .current_dir(dir.join(&library.name)),
        "cargo build",
    )?;
    let exe = target
        .join("debug")
        .join(format!("{bin}{}", std::env::consts::EXE_SUFFIX));
    program(&mut Command::new(exe), &library.name)
}

fn test(dir: &Path, library: &Tested, _: &[Tested]) -> TestResults {
    let target = std::path::absolute(dir.join("target")).map_err(|e| e.to_string())?;
    let run = output(
        Command::new(tool("CARGO", "cargo"))
            .args(["test", "--offline"])
            .env("CARGO_TARGET_DIR", target)
            .current_dir(dir.join(&library.name)),
        "cargo",
    )?;
    let stdout = String::from_utf8_lossy(&run.stdout);
    let mut results = Vec::new();
    for line in stdout.lines() {
        // `test tests::name ... ok`, or `... FAILED`.
        let Some(rest) = line.strip_prefix("test ") else {
            continue;
        };
        let Some((name, result)) = rest.rsplit_once(" ... ") else {
            continue;
        };
        let short = name.rsplit("::").next().unwrap_or(name).to_string();
        match result {
            "ok" => results.push((short, None)),
            "FAILED" => results.push((short, Some(failure(&stdout, name)))),
            _ => {}
        }
    }
    if results.is_empty() {
        return Err(format!("no test results\n{}", tool_output(&run)));
    }
    Ok(results)
}

/// Why a Rust test failed, from its captured output: the message of the
/// error it returned (`Error: Error(Some(MessageError("expected …")))`),
/// or its panic's, else just that it failed.
fn failure(stdout: &str, name: &str) -> String {
    let header = format!("---- {name} stdout ----\n");
    let Some(at) = stdout.find(&header) else {
        return "failed".to_string();
    };
    let section = &stdout[at + header.len()..];
    let section = &section[..section.find("\n\n").unwrap_or(section.len())];
    if let Some((_, rest)) = section.split_once("MessageError(\"") {
        if let Some((message, _)) = rest.split_once("\")") {
            return message.replace("\\\"", "\"");
        }
    }
    let mut lines = section.lines();
    while let Some(line) = lines.next() {
        if line.contains(" panicked at ") {
            if let Some(message) = lines.next() {
                return message.to_string();
            }
        }
    }
    section.lines().next().unwrap_or("failed").to_string()
}

#[cfg(test)]
mod tests {
    use super::failure;

    /// A Rust test's failure, from what `cargo test` prints.
    #[test]
    fn reads_rust_failures() {
        let out = "test r#mod::tests::b ... FAILED\n\nfailures:\n\n\
                   ---- r#mod::tests::b stdout ----\n\
                   Error: Error(Some(MessageError(\"expected 1 + 1 == (3) not (2)\")))\n\n";
        assert_eq!(
            failure(out, "r#mod::tests::b"),
            "expected 1 + 1 == (3) not (2)"
        );
        assert_eq!(failure(out, "r#mod::tests::c"), "failed");
    }
}
