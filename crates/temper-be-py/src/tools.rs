//! How Python runs built libraries' tests, for `temper test -b py`, as
//! Kotlin's `RunPython`: `python3 -B -m temper_core.testing result.xml`
//! in each library's directory, with `temper-core`, `std`, and the
//! libraries on `PYTHONPATH` (or `PYTHON` for the interpreter), reading
//! the JUnit XML it writes. `temper run -b py` imports the library's
//! package as Kotlin's `top.py` does, with logging to standard output,
//! then waits for its async blocks. Both run Python in UTF-8 mode
//! ([`python`]).

use std::path::Path;
use std::process::Command;
use temper_tmpl::tools::{junit_file, limit_memory, output, program, tool};
use temper_tmpl::{TestResults, Tested, Tools};

pub const TOOLS: Tools = Tools {
    prepare: None,
    test,
    run,
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

/// `PYTHONPATH` for the runtime, std, and every library.
fn python_path(dir: &Path, all: &[Tested]) -> Result<std::ffi::OsString, String> {
    let mut path = vec![dir.join("temper-core"), dir.join("std")];
    path.extend(all.iter().map(|l| dir.join(&l.name)));
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
