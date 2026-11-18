//! How C++ runs built libraries' tests, for `temper test -b cpp`: CMake
//! (`CMAKE`) on each library's `CMakeLists.txt`, which tempest writes,
//! in `temper.out/cpp/LIBRARY/build`, building its `LIBRARY-test`
//! target, its tests with std and the libraries it imports, by `g++` or
//! whatever CMake finds (`CXX`), two jobs at once, then that program run
//! in the library's directory, which writes `test-results.xml`, as
//! Kotlin's `RunCpp` runs its `g++` build. `temper run -b cpp` builds
//! the `LIBRARY-main` target the same way, quietly, and runs it.

use std::path::Path;
use std::process::Command;
use temper_tmpl::tools::{junit_file, limit_memory, output, program, step, tool, tool_output};
use temper_tmpl::{TestResults, Tested, Tools};

pub const TOOLS: Tools = Tools {
    prepare: None,
    test,
    run,
};

fn run(dir: &Path, library: &Tested, _: &[Tested]) -> Result<bool, String> {
    let here = dir.join(&library.name);
    let cmake = tool("CMAKE", "cmake");
    step(
        Command::new(&cmake)
            .args(["-S", ".", "-B", "build", "-DCMAKE_BUILD_TYPE=Debug"])
            .current_dir(&here),
        "cmake",
    )?;
    let target = format!("{}-main", library.name);
    step(
        Command::new(&cmake)
            .args(["--build", "build", "--target", &target, "-j", "2"])
            .current_dir(&here),
        "cmake --build",
    )?;
    program(
        limit_memory(&mut Command::new(here.join("build").join(&target))).current_dir(&here),
        &target,
    )
}

fn test(dir: &Path, library: &Tested, _: &[Tested]) -> TestResults {
    let here = dir.join(&library.name);
    let build = here.join("build");
    let cmake = tool("CMAKE", "cmake");
    let configure = output(
        Command::new(&cmake)
            .args(["-S", ".", "-B", "build", "-DCMAKE_BUILD_TYPE=Debug"])
            .current_dir(&here),
        "cmake",
    )?;
    if !configure.status.success() {
        return Err(format!("cmake failed\n{}", tool_output(&configure)));
    }
    let target = format!("{}-test", library.name);
    let made = output(
        Command::new(&cmake)
            .args(["--build", "build", "--target", &target, "-j", "2"])
            .current_dir(&here),
        "cmake --build",
    )?;
    if !made.status.success() {
        return Err(format!("the build failed\n{}", tool_output(&made)));
    }
    let results = here.join("test-results.xml");
    let _ = std::fs::remove_file(&results);
    let run = output(
        limit_memory(&mut Command::new(build.join(&target))).current_dir(&here),
        "the tests",
    )?;
    junit_file(&results, &run, str::to_string)
}
