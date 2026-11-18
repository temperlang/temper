//! How Lua runs built libraries' tests, for `temper test -b lua`: each
//! library's `tests/*-test.lua`, one module's tests each (the root
//! module's `init-test.lua`), in turn under `lua` (or `LUA`), with LuaUnit
//! beside the libraries, as Kotlin copies it, and `LUA_PATH` of
//! `?.lua;?/init.lua`, writing JUnit XML. `temper run -b lua` runs the
//! library's `init.lua` the same way, as Kotlin's `Lua51Specifics` does.
//! Each file runs with a time and a memory limit (`limit_memory`), so a
//! runaway file fails alone.

use std::path::{Path, PathBuf};
use std::process::Command;
use temper_tmpl::tools::{files_with, junit_file, limit_memory, output, program, tool};
use temper_tmpl::{TestResults, Tested, Tools};

pub const TOOLS: Tools = Tools {
    prepare: Some(write_luaunit),
    test,
    run,
};

fn run(dir: &Path, library: &Tested, _: &[Tested]) -> Result<bool, String> {
    program(
        limit_memory(&mut Command::new(tool("LUA", "lua")))
            .args(["-e", "TEMPER_LUA_DEBUG_FUNCS=false"])
            .arg(PathBuf::from(&library.name).join("init.lua"))
            .env("LUA_PATH", "?.lua;?/init.lua")
            .current_dir(dir),
        "lua",
    )
}

/// LuaUnit beside the libraries, as Kotlin copies it there for tests.
fn write_luaunit(dir: &Path, _: &[Tested]) -> Result<(), String> {
    for (path, text) in crate::runtime::LUAUNIT {
        let path = dir.join("luaunit").join(path);
        path.parent()
            .map_or(Ok(()), std::fs::create_dir_all)
            .and_then(|()| std::fs::write(&path, text))
            .map_err(|e| format!("{}: {e}", path.display()))?;
    }
    Ok(())
}

fn test(dir: &Path, library: &Tested, _: &[Tested]) -> TestResults {
    // Each module's tests file, `tests/init-test.lua` for the root
    // module's and `tests/a-b-test.lua` for `a/b`'s, run in turn, as
    // Kotlin runs each, since a library may have no root module.
    let mut files = Vec::new();
    files_with(&dir.join(&library.name).join("tests"), "lua", &mut files);
    files.retain(|f| f.to_string_lossy().ends_with("-test.lua"));
    if files.is_empty() {
        return Err(format!("no test files in {}/tests", library.name));
    }
    let mut results = Vec::new();
    let mut errors = Vec::new();
    for file in &files {
        let test = file.strip_prefix(dir).unwrap_or(file);
        match test_file(dir, test) {
            Ok(found) => results.extend(found),
            Err(why) => errors.push((test.to_string_lossy().replace('\\', "/"), why)),
        }
    }
    if results.is_empty() && !errors.is_empty() {
        let why = errors
            .into_iter()
            .map(|(file, why)| format!("{file}: {why}"));
        return Err(why.collect::<Vec<_>>().join("\n"));
    }
    // A file that gave no results, as one that fails as it loads, or
    // one that timed out, is a failure by its name, as Python's unittest
    // reports a module that won't import, so the others' results still
    // count.
    for (file, why) in errors {
        let Some(why) = why.strip_prefix("no test results\n") else {
            results.push((file, Some(why)));
            continue;
        };
        let first = why.lines().find(|l| !l.trim().is_empty()).unwrap_or("");
        results.push((file, Some(format!("no test results: {first}\n{why}"))));
    }
    Ok(results)
}

/// One tests file's results.
fn test_file(dir: &Path, test: &Path) -> TestResults {
    let results = dir.join("test-results.xml");
    let _ = std::fs::remove_file(&results);
    let run = output(
        limit_memory(&mut Command::new(tool("LUA", "lua")))
            .args(["-e", "TEMPER_LUA_DEBUG_FUNCS=false"])
            .arg(test)
            .args(["-o", "junit", "-n", "test-results.xml"])
            .env("LUA_PATH", "?.lua;?/init.lua")
            .current_dir(dir),
        "lua",
    )?;
    // LuaUnit's names, `Test_.test_name__12`, as the tests' functions'.
    junit_file(&results, &run, |n| {
        let n = n.strip_prefix("Test_.test_").unwrap_or(n);
        let n = match n.rsplit_once("__") {
            Some((n, id)) if id.chars().all(|c| c.is_ascii_digit()) => n,
            _ => n,
        };
        n.to_string()
    })
}
