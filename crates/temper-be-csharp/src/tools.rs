//! How C# runs built libraries' tests, for `temper test -b csharp`, as
//! Kotlin's `RunCSharp`: `dotnet test --logger:junit` in each library's
//! `tests` project (`DOTNET`), its packages restored from NuGet's cache
//! or feed, reading `TestResults/TestResults.xml`; then `dotnet
//! build-server shutdown`, so nothing stays running. `temper run -b
//! csharp` builds the library's `program` project quietly, then runs it
//! with `dotnet run --no-build`, as Kotlin's `dotnet run` does.
//!
//! The compiler's warnings are hidden (`WarningLevel=0`), as in Kotlin,
//! unless `TEMPER_STRICT_WARNINGS` is set ([`strict_warnings`]): then
//! the tests are built with every warning (`WarningLevel=9999`, as this
//! crate's own tests compile generated code), and any in std, the
//! libraries, or their tests fails them; the runtime's, Kotlin's, which
//! doesn't enable nullable references yet, are its own.

use std::path::Path;
use std::process::Command;
use temper_tmpl::tools::{junit_file, output, program, step, strict_warnings, tool};
use temper_tmpl::{TestResults, Tested, Tools};

pub const TOOLS: Tools = Tools {
    prepare: None,
    test,
    run,
};

fn run(dir: &Path, library: &Tested, _: &[Tested]) -> Result<bool, String> {
    let here = dir.join(&library.name).join("program");
    let dotnet = || {
        let mut command = Command::new(tool("DOTNET", "dotnet"));
        command
            .env("DOTNET_CLI_TELEMETRY_OPTOUT", "1")
            .env("DOTNET_NOLOGO", "1")
            .current_dir(&here);
        command
    };
    let built = step(
        dotnet().args([
            "build",
            "-p:NuGetAudit=false",
            "-p:WarningLevel=0",
            "-p:CheckEolTargetFramework=false",
        ]),
        "dotnet build",
    );
    let ran = built.and_then(|()| program(dotnet().args(["run", "--no-build"]), "dotnet run"));
    // No build server left running once the program is done.
    let _ = output(
        Command::new(tool("DOTNET", "dotnet")).args(["build-server", "shutdown"]),
        "dotnet build-server shutdown",
    );
    ran
}

fn test(dir: &Path, library: &Tested, _: &[Tested]) -> TestResults {
    let here = dir.join(&library.name).join("tests");
    let results = here.join("TestResults/TestResults.xml");
    let _ = std::fs::remove_file(&results);
    let strict = strict_warnings();
    let level = match strict {
        true => "-p:WarningLevel=9999",
        false => "-p:WarningLevel=0",
    };
    let run = output(
        Command::new(tool("DOTNET", "dotnet"))
            .args(["test", "--logger:junit", "-p:NuGetAudit=false", level])
            .env("DOTNET_CLI_TELEMETRY_OPTOUT", "1")
            .env("DOTNET_NOLOGO", "1")
            .current_dir(&here),
        "dotnet",
    );
    // No build server left running once the tests are done.
    let _ = output(
        Command::new(tool("DOTNET", "dotnet")).args(["build-server", "shutdown"]),
        "dotnet build-server shutdown",
    );
    let run = run?;
    if strict {
        let runtime = dir.join("temper-core");
        let warned = warnings(&String::from_utf8_lossy(&run.stdout), &runtime);
        if !warned.is_empty() {
            return Err(format!(
                "{} compiler warnings (TEMPER_STRICT_WARNINGS)\n{}",
                warned.len(),
                warned.join("\n")
            ));
        }
    }
    let results = junit_file(&results, &run, str::to_string)?;
    Ok((results.into_iter())
        .map(|(name, cause)| (name, cause.map(|c| said(&c))))
        .collect())
}

/// What a failure says, without MSTest's `Test method T.M threw
/// exception:` line before an exception's, nor a panic's type, so a
/// test that panics says what the panic does (`index 5 out of bounds for
/// length 2`), as on the other backends; another exception keeps its
/// type.
fn said(cause: &str) -> String {
    let thrown = (cause.strip_prefix("Test method "))
        .and_then(|rest| rest.split_once(" threw exception:"))
        .map_or(cause, |(_, rest)| rest.trim_start());
    let thrown = thrown
        .strip_prefix("TemperLang.Core.Panic: ")
        .unwrap_or(thrown);
    thrown.to_string()
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
    use super::{said, warnings};
    use std::path::Path;

    #[test]
    fn says_what_a_panic_says() {
        let panic = "Test method A.ATests.B threw exception: \r\n\
                     TemperLang.Core.Panic: index 5 out of bounds for length 2";
        assert_eq!(said(panic), "index 5 out of bounds for length 2");
        let other = "Test method A.ATests.B threw exception: \r\n\
                     System.TypeInitializationException: The type initializer threw";
        assert_eq!(
            said(other),
            "System.TypeInitializationException: The type initializer threw"
        );
        assert_eq!(said("wrong on purpose"), "wrong on purpose");
    }

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
}
