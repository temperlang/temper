//! How C# runs built libraries' tests, for `temper test -b csharp`, as
//! Kotlin's `RunCSharp`: `dotnet test --logger:junit` on each library's
//! `tests` project (`DOTNET`), its packages restored from NuGet's cache
//! or feed, reading `TestResults/TestResults.xml`; then `dotnet
//! build-server shutdown`, so nothing stays running. Every tested
//! library's tests build and run together, once per run, through one
//! solution in `temper.out/csharp/.build` ([`build`]): one restore, one
//! MSBuild, and one `dotnet test`, not one of each per library; where a
//! library's results aren't there after it, its tests run alone, as
//! before, saying what's wrong. `temper run -b csharp` builds the
//! library's `program` project quietly, then runs it with `dotnet run
//! --no-build`, as Kotlin's `dotnet run` does; that's one build already.
//!
//! The compiler's warnings are hidden (`WarningLevel=0`), as in Kotlin,
//! unless `TEMPER_STRICT_WARNINGS` is set ([`strict_warnings`]): then
//! the tests are built with every warning (`WarningLevel=9999`, as this
//! crate's own tests compile generated code), and any in std, the
//! libraries, or their tests fails them; the runtime's, Kotlin's, which
//! doesn't enable nullable references yet, are its own.

use std::path::{Path, PathBuf};
use std::process::Command;
use temper_tmpl::files::write_if_changed;
use temper_tmpl::tools::{junit, junit_file, output, program, step, strict_warnings, tool};
use temper_tmpl::{TestResults, Tested, Tools};

pub const TOOLS: Tools = Tools {
    prepare: Some(build),
    build_dirs: &[".build", "*/bin", "*/obj", "*/*/bin", "*/*/obj"],
    note: None,
    test,
    run,
};

/// The shared build's directory in `temper.out/csharp`, a name no
/// library's directory has: `Tests.sln`, naming every tested library's
/// tests project, and `build.log`, what building it printed.
const BUILD: &str = ".build";

/// A `dotnet` command, quiet about telemetry and its logo, in `dir`.
fn dotnet(dir: &Path) -> Command {
    let mut command = Command::new(tool("DOTNET", "dotnet"));
    command
        .env("DOTNET_CLI_TELEMETRY_OPTOUT", "1")
        .env("DOTNET_NOLOGO", "1")
        .current_dir(dir);
    command
}

/// `dotnet build-server shutdown`, so no build server stays running.
fn shut_down_build_servers() {
    let _ = output(
        Command::new(tool("DOTNET", "dotnet")).args(["build-server", "shutdown"]),
        "dotnet build-server shutdown",
    );
}

/// The compiler's warning level: every warning when they're strict.
fn warning_level() -> &'static str {
    match strict_warnings() {
        true => "-p:WarningLevel=9999",
        false => "-p:WarningLevel=0",
    }
}

/// A library's tests project's file name, of `LIBRARY/tests/*.csproj`,
/// if it has one.
fn tests_project(dir: &Path, library: &str) -> Option<String> {
    let entries = std::fs::read_dir(dir.join(library).join("tests")).ok()?;
    let mut found: Vec<String> = (entries.flatten())
        .map(|e| e.file_name().to_string_lossy().into_owned())
        .filter(|f| f.ends_with(".csproj"))
        .collect();
    found.sort();
    found.into_iter().next()
}

/// A library's JUnit results, where `dotnet test` writes them.
fn results_file(dir: &Path, library: &str) -> PathBuf {
    dir.join(library).join("tests/TestResults/TestResults.xml")
}

/// Builds and runs, for `temper test`, every tested library's tests
/// through one solution naming their projects, `.build/Tests.sln`
/// ([`BUILD`]): `dotnet build` restores and builds them all, each
/// project once, a few at once, then `dotnet test --no-build` runs them,
/// each library's results in its own `tests` project, as alone. Alone,
/// each library paid for MSBuild, a restore, and a test run of its own,
/// with the compiler server started again, most of a run's time. Each
/// project's `bin` and `obj` are kept from run to run, so MSBuild
/// compiles only those whose files `temper build` rewrote; with strict
/// warnings, everything compiles again, so every warning is seen. It
/// fails quietly: where the build fails, nothing runs, and [`test`]
/// runs each library alone, which says what's wrong, reusing what did
/// build. With nothing to test, as for `temper run`, it does
/// nothing.
fn build(dir: &Path, all: &[Tested]) -> Result<(), String> {
    let root = dir.join(BUILD);
    let projects: Vec<(&str, String)> = (all.iter().filter(|l| l.tests > 0))
        .filter_map(|l| Some((l.name.as_str(), tests_project(dir, &l.name)?)))
        .collect();
    if projects.is_empty() {
        return Ok(());
    }
    for (library, _) in &projects {
        let _ = std::fs::remove_file(results_file(dir, library));
    }
    if write_if_changed(&root.join("Tests.sln"), solution(&projects).as_bytes()).is_err() {
        return Ok(());
    }
    // As many projects at once as CPUs, up to four, as each test run
    // starts a process of its own.
    let cpus = std::thread::available_parallelism().map_or(1, |n| n.get());
    let jobs = format!("-maxcpucount:{}", cpus.clamp(1, 4));
    let built = output(
        dotnet(&root)
            .args([
                "build",
                "Tests.sln",
                &jobs,
                "-p:NuGetAudit=false",
                warning_level(),
            ])
            .args(strict_warnings().then_some("--no-incremental")),
        "dotnet build",
    );
    if let Ok(built) = &built {
        let _ = std::fs::write(root.join("build.log"), &built.stdout);
    }
    // Each library's results, only where everything built: after a
    // failed build, `bin` may still hold the last run's assemblies.
    if built.is_ok_and(|b| b.status.success()) {
        let _ = output(
            dotnet(&root).args(["test", "Tests.sln", "--no-build", &jobs, "--logger:junit"]),
            "dotnet test",
        );
    }
    shut_down_build_servers();
    Ok(())
}

/// A Visual Studio solution naming each library's tests project, by its
/// path from `.build` (`../LIBRARY/tests/FILE`), in its `Debug`
/// configuration, as `dotnet new sln` and `dotnet sln add` would write
/// it, without their runs of `dotnet`. Each project is named for its
/// library, which is unique. Solutions have no escapes, so a name must
/// hold no `"`, which a library's name, a dashed identifier, never does.
fn solution(projects: &[(&str, String)]) -> String {
    // C#'s project type, and an ID for each project, by its place.
    const CSHARP: &str = "{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}";
    let id = |i: usize| format!("{{00000000-0000-0000-0000-{:012X}}}", i + 1);
    let mut text = String::from("\nMicrosoft Visual Studio Solution File, Format Version 12.00\n");
    for (i, (library, project)) in projects.iter().enumerate() {
        text.push_str(&format!(
            "Project(\"{CSHARP}\") = \"{library}\", \"../{library}/tests/{project}\", \"{}\"\nEndProject\n",
            id(i)
        ));
    }
    text.push_str(
        "Global\n\tGlobalSection(SolutionConfigurationPlatforms) = preSolution\n\t\tDebug|Any CPU = Debug|Any CPU\n\tEndGlobalSection\n\tGlobalSection(ProjectConfigurationPlatforms) = postSolution\n",
    );
    for i in 0..projects.len() {
        let id = id(i);
        text.push_str(&format!(
            "\t\t{id}.Debug|Any CPU.ActiveCfg = Debug|Any CPU\n\t\t{id}.Debug|Any CPU.Build.0 = Debug|Any CPU\n"
        ));
    }
    text.push_str("\tEndGlobalSection\nEndGlobal\n");
    text
}

fn run(dir: &Path, library: &Tested, _: &[Tested]) -> Result<bool, String> {
    let here = dir.join(&library.name).join("program");
    let built = step(
        dotnet(&here).args([
            "build",
            "-p:NuGetAudit=false",
            "-p:WarningLevel=0",
            "-p:CheckEolTargetFramework=false",
        ]),
        "dotnet build",
    );
    let ran = built.and_then(|()| program(dotnet(&here).args(["run", "--no-build"]), "dotnet run"));
    // No build server left running once the program is done.
    shut_down_build_servers();
    ran
}

/// A library's test results, from [`build`]'s run, or else from `dotnet
/// test` in its `tests` project, alone, as before [`build`] shared one;
/// with `TEMPER_STRICT_WARNINGS`, their compiler warnings fail them
/// ([`warned`]).
fn test(dir: &Path, library: &Tested, all: &[Tested]) -> TestResults {
    let results = results_file(dir, &library.name);
    let strict = strict_warnings();
    let shared = match strict {
        true => warned(dir, library, all),
        false => Vec::new(),
    };
    let xml = std::fs::read_to_string(&results).unwrap_or_default();
    if xml.contains("<testsuite") {
        fail_on(shared)?;
        return Ok((junit(&xml).into_iter())
            .map(|c| (c.name.clone(), c.cause()))
            .collect());
    }
    let run = output(
        dotnet(&dir.join(&library.name).join("tests")).args([
            "test",
            "--logger:junit",
            "-p:NuGetAudit=false",
            warning_level(),
        ]),
        "dotnet",
    );
    // No build server left running once the tests are done.
    shut_down_build_servers();
    let run = run?;
    if strict {
        let runtime = dir.join("temper-core");
        let mut all_warned = shared;
        for line in warnings(&String::from_utf8_lossy(&run.stdout), &runtime) {
            if !all_warned.contains(&line) {
                all_warned.push(line);
            }
        }
        fail_on(all_warned)?;
    }
    junit_file(&results, &run, str::to_string)
}

/// An error listing compiler warnings, if there are any.
fn fail_on(warned: Vec<String>) -> Result<(), String> {
    match warned.is_empty() {
        true => Ok(()),
        false => Err(format!(
            "{} compiler warnings (TEMPER_STRICT_WARNINGS)\n{}",
            warned.len(),
            warned.join("\n")
        )),
    }
}

/// A library's compiler warnings in [`build`]'s `build.log`
/// ([`warnings_of`]).
fn warned(dir: &Path, library: &Tested, all: &[Tested]) -> Vec<String> {
    let log = std::fs::read_to_string(dir.join(BUILD).join("build.log")).unwrap_or_default();
    let tested: Vec<&str> = (all.iter().filter(|l| l.tests > 0))
        .map(|l| l.name.as_str())
        .collect();
    warnings_of(&log, dir, &library.name, &tested)
}

/// A library's compiler warnings in a shared build's output, given the
/// libraries tested, in order: those in its own files, and, for the
/// first library tested, those in files that are no tested library's
/// (std's, or an untested library's), so each warning fails one
/// library's tests, as when each built alone, where a library already
/// built printed nothing.
fn warnings_of(log: &str, dir: &Path, library: &str, tested: &[&str]) -> Vec<String> {
    let first = tested.first() == Some(&library);
    (warnings(log, &dir.join("temper-core")).into_iter())
        .filter(|line| {
            let owner = (line.split_once(".cs("))
                .and_then(|(file, _)| Path::new(file).strip_prefix(dir).ok())
                .and_then(|file| file.components().next())
                .map(|c| c.as_os_str().to_string_lossy().into_owned());
            match owner.filter(|o| tested.contains(&o.as_str())) {
                Some(owner) => owner == library,
                None => first,
            }
        })
        .collect()
}

/// The compiler's warnings in a build's output, `FILE(LINE,COL): warning
/// CODE: …`, each once (MSBuild may repeat them), but those in files
/// under `runtime`.
pub fn warnings(output: &str, runtime: &Path) -> Vec<String> {
    let mut found: Vec<String> = Vec::new();
    for line in output.lines().map(str::trim) {
        let Some((file, _)) = line.split_once(".cs(") else {
            continue;
        };
        if !line.contains("): warning ") || Path::new(file).starts_with(runtime) {
            continue;
        }
        if !found.iter().any(|f| f == line) {
            found.push(line.to_string());
        }
    }
    found
}

#[cfg(test)]
mod tests {
    use super::{warnings, warnings_of};
    use std::path::Path;

    #[test]
    fn warnings_but_the_runtimes() {
        let output = "\
  Determining projects to restore...
/o/csharp/temper-core/Core.cs(3,9): warning CS8632: The annotation… [/o/csharp/temper-core/TemperLang.Core.csproj]
/o/csharp/a/src/A.cs(7,5): warning CS0168: The variable 'x' is declared but never used [/o/csharp/a/src/A.csproj]
/o/csharp/a/src/A.cs(7,5): warning CS0168: The variable 'x' is declared but never used [/o/csharp/a/src/A.csproj]
/o/csharp/a/src/A.cs(9,1): error CS1002: ; expected [/o/csharp/a/src/A.csproj]
";
        let found = warnings(output, Path::new("/o/csharp/temper-core"));
        assert_eq!(found.len(), 1, "{found:?}");
        assert!(found[0].starts_with("/o/csharp/a/src/A.cs(7,5): warning CS0168"));
    }

    #[test]
    fn each_warning_fails_one_library() {
        let log = "\
/o/csharp/std/src/Std.cs(1,1): warning CS0168: s [/o/csharp/std/src/TemperLang.Std.csproj]
/o/csharp/a/src/A.cs(2,1): warning CS0168: a [/o/csharp/a/src/A.csproj]
/o/csharp/b/tests/BTests.cs(3,1): warning CS0168: b [/o/csharp/b/tests/BTest.csproj]
/o/csharp/c/src/C.cs(4,1): warning CS0168: c [/o/csharp/c/src/C.csproj]
";
        let dir = Path::new("/o/csharp");
        let of = |library| warnings_of(log, dir, library, &["a", "b"]);
        let (a, b) = (of("a"), of("b"));
        // std's and the untested `c`'s go to `a`, tested first.
        assert_eq!(a.len(), 3, "{a:?}");
        assert!(a.iter().all(|w| !w.contains("BTests.cs")), "{a:?}");
        assert_eq!(b.len(), 1, "{b:?}");
        assert!(b[0].contains("BTests.cs"), "{b:?}");
    }
}
