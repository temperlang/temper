//! How Java runs built libraries' tests, for `temper test -b java`:
//! `javac` on the runtime, std, and the libraries, then on the tested
//! libraries' tests, against JUnit 5's jars in Maven's local repository,
//! as Kotlin's `pom.xml` names them, and a small runner of JUnit's
//! launcher under `java`, as this crate's own tests do; Kotlin runs
//! Maven instead, which needs its network. Each compiles at most once
//! per run, into `temper.out/java/.build` ([`build`]), not again for
//! each library tested, nor where nothing changed since the last run;
//! then each library's tests run in a JVM of their own.
//! `temper run -b java` compiles the runtime, std, and the libraries the
//! same way, once, quietly, and runs the library's `…Main` class under
//! `java`, as Kotlin runs it by exec.
//!
//! javac's warnings are hidden, as in Kotlin, unless
//! `TEMPER_STRICT_WARNINGS` is set ([`strict_warnings`]): then the
//! tests compile std, the libraries, and their tests with every lint as
//! an error, as this crate's own tests do, and the runtime, Kotlin's, on
//! its own, quietly.

use std::path::{Path, PathBuf};
use std::process::Command;
use temper_tmpl::files::write_if_changed;
use temper_tmpl::tools::{
    files_with, output, program, stamp_matches, strict_warnings, tool, tool_identity, tool_output,
    write_stamp,
};
use temper_tmpl::{TestResults, Tested, Tools};

pub const TOOLS: Tools = Tools {
    prepare: Some(build),
    build_dirs: &[".build", "*/target/temper"],
    note: None,
    test,
    run,
};

/// The shared build's directory in `temper.out/java`, a name no
/// library's directory has: `classes`, the runtime's, std's, and every
/// library's main sources, with the test runner; and `test-classes`,
/// every tested library's tests.
const BUILD: &str = ".build";

/// Compiles, for `temper test`, the runtime, std, every library's main
/// sources, and the test runner into `.build/classes` ([`BUILD`]), as
/// each library's tests compiled them for themselves before, then every
/// tested library's tests at once into `.build/test-classes`. Each is
/// kept from run to run, and compiled again, afresh, only where a source
/// or how it's compiled changed ([`stale`]), as javac doesn't tell
/// itself. Where the tests don't compile together (one doesn't
/// compile, or two libraries' tests clash), `test-classes` is removed,
/// and each library's tests compile alone, saying what's wrong. With
/// nothing to test, as for `temper run`, it does nothing.
fn build(dir: &Path, all: &[Tested]) -> Result<(), String> {
    let root = dir.join(BUILD);
    let tested: Vec<&Tested> = all.iter().filter(|l| l.tests > 0).collect();
    if tested.is_empty() {
        return Ok(());
    }
    let jars = junit()?;
    let runner = root.join("temper/RunTests.java");
    let written = write_if_changed(&runner, RUN_TESTS.as_bytes());
    written.map_err(|e| format!("{}: {e}", runner.display()))?;
    let classpath = std::env::join_paths(&jars).map_err(|e| e.to_string())?;
    let (main, tests) = (root.join("classes"), root.join("test-classes"));
    let strict = strict_warnings();
    let main_key = compile_main(dir, all, &main, &[runner], &classpath, strict)?;
    let mut sources = Vec::new();
    for library in tested {
        files_with(&test_sources(dir, library), "java", &mut sources);
    }
    let with_main = with_main(&main, &jars)?;
    // Compiled again wherever the main classes were.
    let key = format!("{main_key}{}", compile_key(&with_main, &sources, strict));
    if stale(&tests, &key) {
        match javac(&tests, &with_main, &sources, strict) {
            Ok(()) => write_stamp(&stamp(&tests), &key),
            Err(_) => {
                let _ = std::fs::remove_dir_all(&tests);
            }
        }
    }
    Ok(())
}

/// What a javac build depends on: javac, the class path, the lints, and
/// each source's path, size, and time, as `temper build` rewrites only
/// sources that changed.
fn compile_key(classpath: &std::ffi::OsStr, files: &[PathBuf], strict: bool) -> String {
    let mut key = format!(
        "javac {}\nclasspath {}\nstrict {strict}\n",
        tool_identity(&tool("JAVAC", "javac")),
        classpath.to_string_lossy()
    );
    for file in files {
        let meta = std::fs::metadata(file).ok();
        let time = (meta.as_ref())
            .and_then(|m| m.modified().ok())
            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
            .map_or(0, |d| d.as_nanos());
        let size = meta.map_or(0, |m| m.len());
        key.push_str(&format!("{} {size} {time}\n", file.display()));
    }
    key
}

/// A directory of classes' stamp, the [`compile_key`] it was built by.
fn stamp(classes: &Path) -> PathBuf {
    classes.with_extension("stamp")
}

/// Whether a directory of classes wasn't built by `key`, in which case
/// it's removed, so no class of a source that's gone stays.
fn stale(classes: &Path, key: &str) -> bool {
    if classes.is_dir() && stamp_matches(&stamp(classes), key) {
        return false;
    }
    let _ = std::fs::remove_dir_all(classes);
    let _ = std::fs::remove_file(stamp(classes));
    true
}

/// A library's test sources' directory.
fn test_sources(dir: &Path, library: &Tested) -> PathBuf {
    dir.join(&library.name).join("src/test/java")
}

/// JUnit's jars, or why they can't be had.
fn junit() -> Result<Vec<PathBuf>, String> {
    junit_jars().map_err(|missing| {
        format!(
            "no {} (JUnit, as the generated pom.xml names it)",
            missing.display()
        )
    })
}

/// A class path of `main` and JUnit's jars.
fn with_main(main: &Path, jars: &[PathBuf]) -> Result<std::ffi::OsString, String> {
    std::env::join_paths(std::iter::once(main).chain(jars.iter().map(PathBuf::as_path)))
        .map_err(|e| e.to_string())
}

/// Compiles the runtime, std, and the libraries' main sources with
/// `javac` (`JAVAC`) into `classes`, with `extra` sources and `classpath`;
/// with `strict`, the runtime first, quietly, then the rest with every
/// lint an error; only where they're [`stale`]. The [`compile_key`]
/// they were built by.
fn compile_main(
    dir: &Path,
    all: &[Tested],
    classes: &Path,
    extra: &[PathBuf],
    classpath: &std::ffi::OsStr,
    strict: bool,
) -> Result<String, String> {
    let mut runtime = Vec::new();
    files_with(&dir.join("temper-core/src/main/java"), "java", &mut runtime);
    let mut sources = Vec::new();
    let roots = std::iter::once("std".to_string());
    for root in roots.chain(all.iter().map(|l| l.name.clone())) {
        files_with(&dir.join(root).join("src/main/java"), "java", &mut sources);
    }
    sources.extend(extra.iter().cloned());
    let all: Vec<PathBuf> = runtime.iter().chain(&sources).cloned().collect();
    let key = compile_key(classpath, &all, strict);
    if !stale(classes, &key) {
        return Ok(key);
    }
    if !strict {
        javac(classes, classpath, &all, false)?;
    } else {
        javac(classes, classpath, &runtime, false)?;
        let mut with_runtime = vec![classes.to_path_buf()];
        let given = std::env::split_paths(classpath).filter(|p| !p.as_os_str().is_empty());
        with_runtime.extend(given);
        let with_runtime = std::env::join_paths(with_runtime).map_err(|e| e.to_string())?;
        javac(classes, &with_runtime, &sources, true)?;
    }
    write_stamp(&stamp(classes), &key);
    Ok(key)
}

/// javac's options with `strict`: every lint and Javadoc check but for
/// missing comments, as errors, as this crate's tests compile generated
/// code; else no warnings.
fn lints(strict: bool) -> &'static [&'static str] {
    match strict {
        true => &["-Xlint:all", "-Xdoclint:all,-missing", "-Werror"],
        false => &["-nowarn"],
    }
}

/// `javac` on `files` into a directory, against a class path; the files
/// are named in an argument file beside the directory ([`argfile`]).
fn javac(
    into: &Path,
    classpath: &std::ffi::OsStr,
    files: &[PathBuf],
    strict: bool,
) -> Result<(), String> {
    let list = into.with_extension("args");
    let sources = (list.parent())
        .map_or(Ok(()), std::fs::create_dir_all)
        .and_then(|()| argfile(&list, files))
        .map_err(|e| format!("{}: {e}", list.display()))?;
    let built = output(
        Command::new(tool("JAVAC", "javac"))
            .arg(ARGFILE_ENCODING)
            .args(["-encoding", "UTF-8"])
            .args(lints(strict))
            .arg("-d")
            .arg(into)
            .arg("-cp")
            .arg(classpath)
            .arg(sources),
        "javac",
    )?;
    match built.status.success() {
        true => Ok(()),
        false => Err(format!("javac failed\n{}", tool_output(&built))),
    }
}

/// Writes `args` to `path` as a javac argument file, one to a line,
/// quoted, with `\` and `"` escaped as javac reads them, and gives
/// javac's argument naming it, `@path`. A library's sources, std's, and
/// the runtime's, named on the command line, can be more than Windows
/// takes (32K characters).
pub fn argfile(path: &Path, args: &[PathBuf]) -> std::io::Result<std::ffi::OsString> {
    let mut text = String::new();
    for arg in args {
        text.push('"');
        for c in arg.to_string_lossy().chars() {
            match c {
                '\\' => text.push_str("\\\\"),
                '"' => text.push_str("\\\""),
                '\n' => text.push_str("\\n"),
                '\r' => text.push_str("\\r"),
                c => text.push(c),
            }
        }
        text.push_str("\"\n");
    }
    std::fs::write(path, text)?;
    let mut at = std::ffi::OsString::from("@");
    at.push(path);
    Ok(at)
}

/// javac's option that has it read an argument file ([`argfile`]) as
/// UTF-8, as written; JDK 18 and later read it so by default, and
/// earlier ones by the system's code page.
pub const ARGFILE_ENCODING: &str = "-J-Dfile.encoding=UTF-8";

/// Compiles as [`build`] does, without JUnit, into
/// `temper.out/java/LIBRARY/target/temper/classes`, and runs the
/// library's main class, the one its `…Main.java` declares.
fn run(dir: &Path, library: &Tested, all: &[Tested]) -> Result<bool, String> {
    let here = dir.join(&library.name);
    let mut mains = Vec::new();
    files_with(&here.join("src/main/java"), "java", &mut mains);
    let main = (mains.iter())
        .filter(|f| {
            f.file_name()
                .is_some_and(|n| n.to_string_lossy().ends_with("Main.java"))
        })
        .find_map(|f| f.strip_prefix(here.join("src/main/java")).ok())
        .map(|f| {
            f.with_extension("")
                .to_string_lossy()
                .replace(['/', '\\'], ".")
        })
        .ok_or_else(|| format!("no main class in {}", here.display()))?;
    let classes = here.join("target/temper/classes");
    compile_main(dir, all, &classes, &[], std::ffi::OsStr::new(""), false)?;
    program(
        Command::new(tool("JAVA", "java"))
            .args(["-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-cp"])
            .arg(&classes)
            .arg(main),
        "java",
    )
}

/// [`JUNIT`]'s jars in Maven's local repository (`~/.m2/repository`),
/// or the first that's missing.
pub fn junit_jars() -> Result<Vec<PathBuf>, PathBuf> {
    let home = std::env::var_os("HOME")
        .map(PathBuf::from)
        .unwrap_or_default();
    let jars: Vec<PathBuf> = (JUNIT.iter())
        .map(|j| home.join(".m2/repository").join(j))
        .collect();
    match jars.iter().find(|j| !j.exists()) {
        Some(missing) => Err(missing.clone()),
        None => Ok(jars),
    }
}

/// JUnit 5's jars, as Kotlin's `pom.xml` names them (`junit-jupiter`
/// 5.9.2), with what they need to compile tests and run them through
/// JUnit's launcher, by their paths in Maven's local repository.
pub const JUNIT: [&str; 7] = [
    "org/junit/jupiter/junit-jupiter-api/5.9.2/junit-jupiter-api-5.9.2.jar",
    "org/junit/jupiter/junit-jupiter-engine/5.9.2/junit-jupiter-engine-5.9.2.jar",
    "org/junit/platform/junit-platform-commons/1.9.2/junit-platform-commons-1.9.2.jar",
    "org/junit/platform/junit-platform-engine/1.9.2/junit-platform-engine-1.9.2.jar",
    "org/junit/platform/junit-platform-launcher/1.9.2/junit-platform-launcher-1.9.2.jar",
    "org/opentest4j/opentest4j/1.2.0/opentest4j-1.2.0.jar",
    "org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar",
];

/// Runs the tests compiled into the directory it's given through JUnit's
/// launcher, or only those of the classes named after it, printing a
/// line per test: `ok`, or `failed` and its message, then its name, tab
/// separated, with newlines as `\n`.
pub const RUN_TESTS: &str = r#"package temper;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherFactory;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

public final class RunTests {
    private RunTests() {}

    public static void main(String[] args) {
        Launcher launcher = LauncherFactory.create();
        TestExecutionListener listener = new TestExecutionListener() {
            @Override
            public void executionFinished(TestIdentifier id, TestExecutionResult result) {
                if (!id.isTest()) {
                    return;
                }
                String name = id.getDisplayName().replace("\t", " ");
                if (result.getStatus() == TestExecutionResult.Status.SUCCESSFUL) {
                    System.out.println("ok\t" + name);
                } else {
                    String message = result.getThrowable().map(t -> String.valueOf(t.getMessage())).orElse("failed");
                    System.out.println("failed\t" + name + "\t" + message.replace("\n", "\\n"));
                }
            }
        };
        List<DiscoverySelector> selectors = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            selectors.add(selectClass(args[i]));
        }
        if (selectors.isEmpty()) {
            selectors.addAll(selectClasspathRoots(Set.of(Paths.get(args[0]))));
        }
        launcher.execute(request().selectors(selectors).build(), listener);
    }
}
"#;

/// Runs a library's tests with `java` (`JAVA`) through JUnit's launcher
/// ([`RUN_TESTS`]): those [`build`] compiled with the others', selected
/// by their classes, or, where they didn't compile together, its own,
/// compiled alone into `temper.out/java/LIBRARY/target/temper`.
fn test(dir: &Path, library: &Tested, _: &[Tested]) -> TestResults {
    let jars = junit()?;
    let root = dir.join(BUILD);
    let main = root.join("classes");
    let shared = root.join("test-classes");
    let mut sources = Vec::new();
    let from = test_sources(dir, library);
    files_with(&from, "java", &mut sources);
    // Each source's class, by its path, as Java names them, but for
    // `package-info.java`'s, which isn't one.
    let classes: Vec<String> = (sources.iter())
        .filter_map(|f| f.strip_prefix(&from).ok())
        .filter(|f| !f.to_string_lossy().contains('-'))
        .map(|f| {
            f.with_extension("")
                .to_string_lossy()
                .replace(['/', '\\'], ".")
        })
        .collect();
    // With no classes named, the runner runs every test it finds.
    let (tests, classes) = match shared.is_dir() && !classes.is_empty() {
        true => (shared, classes),
        false => {
            let own = dir.join(&library.name).join("target/temper/test-classes");
            let _ = std::fs::remove_dir_all(&own);
            javac(&own, &with_main(&main, &jars)?, &sources, strict_warnings())?;
            (own, Vec::new())
        }
    };
    let everything = std::env::join_paths([tests.clone(), main].into_iter().chain(jars))
        .map_err(|e| e.to_string())?;
    let run = output(
        Command::new(tool("JAVA", "java"))
            .args(["-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-cp"])
            .arg(everything)
            .arg("temper.RunTests")
            .arg(&tests)
            .args(classes),
        "java",
    )?;
    let results = results(&String::from_utf8_lossy(&run.stdout));
    if results.is_empty() {
        return Err(format!("no test results\n{}", tool_output(&run)));
    }
    Ok(results)
}

/// Each test's name and failure, as [`RUN_TESTS`] prints them.
pub fn results(stdout: &str) -> Vec<(String, Option<String>)> {
    (stdout.lines())
        .filter_map(|line| {
            let mut parts = line.splitn(3, '\t');
            // JUnit's display name of a method, `b()`, as the test `b`.
            let (status, name, cause) = (parts.next(), parts.next(), parts.next());
            let name = name.map(|n| n.strip_suffix("()").unwrap_or(n));
            match (status, name, cause) {
                (Some("ok"), Some(name), _) => Some((name.to_string(), None)),
                (Some("failed"), Some(name), cause) => {
                    let cause = cause.unwrap_or("failed").replace("\\n", "\n");
                    Some((name.to_string(), Some(cause)))
                }
                _ => None,
            }
        })
        .collect()
}
