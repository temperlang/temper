//! How Python runs built libraries' tests, for `temper test -b py`, as
//! Kotlin's `RunPython`: `python3 -B -m temper_core.testing result.xml`
//! in each library's directory, with `temper-core`, `std`, and the
//! libraries on `PYTHONPATH` (or `PYTHON` for the interpreter), reading
//! the JUnit XML it writes. `temper run -b py` imports the library's
//! package as Kotlin's `top.py` does, with logging to standard output,
//! then waits for its async blocks. Both run Python in UTF-8 mode
//! ([`python`]). Packages that libraries' configs list are installed
//! first, with pip, into `temper.out/py/.deps` ([`install`]), as
//! Kotlin's `pip install --target`.

use std::path::Path;
use std::process::Command;
use temper_tmpl::tools::{
    identity, install_once, junit_file, limit_memory, output, program, step, tool,
};
use temper_tmpl::{TestResults, Tested, Tools};

pub const TOOLS: Tools = Tools {
    prepare: Some(install),
    test,
    run,
    dirs: &[".deps"],
};

/// Python (`PYTHON`, or `python3`) in UTF-8 mode (`PYTHONUTF8=1`), as
/// Python 3.15 and later run by default: else, on Windows, its standard
/// streams are in the system's code page, which can't hold most of
/// Unicode, so what a program logs can be lost.
fn python() -> Command {
    let mut command = Command::new(tool("PYTHON", "python3"));
    command.env("PYTHONUTF8", "1");
    command
}

/// Where the packages that libraries' configs list go.
const DEPS: &str = ".deps";

/// Installs the packages that libraries' configs list, with `pip install
/// --target`, once for each list of them and Python ([`install_once`]),
/// as pip installs compiled packages for the Python it runs under.
fn install(dir: &Path, all: &[Tested]) -> Result<(), String> {
    let mut packages: Vec<String> = (all.iter())
        .flat_map(|l| crate::pypi_dependencies(&l.metadata))
        .collect();
    packages.sort();
    packages.dedup();
    let manifest: String = packages.iter().map(|p| p.clone() + "\n").collect();
    let python_id = || {
        let python = tool("PYTHON", "python3");
        identity(
            &python,
            &["-c", "import sys; print(sys.executable, sys.version)"],
        )
    };
    install_once(
        &dir.join(DEPS),
        "requirements.txt",
        &manifest,
        python_id,
        |deps| {
            step(
                python()
                    .args(["-m", "pip", "install", "--disable-pip-version-check"])
                    .args(["--no-input", "--quiet", "--target", ".", "-r"])
                    .arg("requirements.txt")
                    .current_dir(deps),
                "pip install",
            )
        },
    )?;
    Ok(())
}

/// `PYTHONPATH` for the runtime, std, every library, and the packages
/// their configs list.
fn python_path(dir: &Path, all: &[Tested]) -> Result<std::ffi::OsString, String> {
    let mut path = vec![dir.join("temper-core"), dir.join("std")];
    path.extend(all.iter().map(|l| dir.join(&l.name)));
    path.push(dir.join(DEPS));
    std::env::join_paths(path).map_err(|e| e.to_string())
}

fn run(dir: &Path, library: &Tested, all: &[Tested]) -> Result<bool, String> {
    let project = crate::translate::py_name(&library.name, &library.metadata);
    let package = crate::names::library_package(&project);
    let top = format!(
        "from temper_core import init_simple_logging, await_safe_to_exit\n\
         init_simple_logging()\n\
         import {package}\n\
         await_safe_to_exit()\n"
    );
    program(
        limit_memory(&mut python())
            .args(["-B", "-c", &top])
            .env("PYTHONPATH", python_path(dir, all)?)
            .current_dir(dir),
        "python3",
    )
}

fn test(dir: &Path, library: &Tested, all: &[Tested]) -> TestResults {
    let path = python_path(dir, all)?;
    let here = dir.join(&library.name);
    let results = here.join("result.xml");
    let _ = std::fs::remove_file(&results);
    let run = output(
        limit_memory(&mut python())
            .args(["-B", "-m", "temper_core.testing", "result.xml"])
            .env("PYTHONPATH", path)
            .current_dir(&here),
        "python3",
    )?;
    junit_file(&results, &run, str::to_string)
}
