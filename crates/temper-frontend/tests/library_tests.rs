//! Tests of running libraries' tests ([`temper_frontend::library_tests`])
//! on small libraries written to a temporary directory, with std built in
//! or from a directory.

use std::fs;
use temper_frontend::library_tests::{TestOptions, parse_junit, test_dir, test_libraries};
use temper_frontend::project::{Config, LibrarySources, find_libraries, load_library, work_root};
use temper_frontend::sources::Sources;
use temper_frontend::stdlib;
use temper_syntax::lex::SourceKind;

/// A temporary directory, removed when dropped.
struct TempTree {
    root: String,
}

impl Drop for TempTree {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.root);
    }
}

/// A fresh directory for a test, with these files, by relative path.
fn write_tree(test: &str, files: &[(&str, &str)]) -> TempTree {
    let dir = std::env::temp_dir().join(format!(
        "tempest-library-tests-{}-{test}",
        std::process::id()
    ));
    let _ = fs::remove_dir_all(&dir);
    for (path, text) in files {
        let path = dir.join(path);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, text).unwrap();
    }
    TempTree {
        root: dir.to_string_lossy().replace('\\', "/"),
    }
}

/// Tests the libraries under `dir`, with std built in, on a thread with
/// room for the interpreter's recursion, giving whether all passed, and
/// what was written to `out` and `err`, verbosely, listing every test.
fn test(dir: &str) -> (bool, String, String) {
    test_with(dir, None)
}

/// [`test`], with std from `std_dir`, if given.
fn test_with(dir: &str, std_dir: Option<String>) -> (bool, String, String) {
    test_in(dir, true, move || match std_dir {
        Some(std_dir) => Sources::with_std_dir(&std_dir),
        None => Sources::standard(),
    })
}

/// [`test`], in the sources that `sources` makes.
fn test_in(
    dir: &str,
    verbose: bool,
    sources: impl FnOnce() -> Sources + Send + 'static,
) -> (bool, String, String) {
    let dir = dir.to_string();
    std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(move || {
            let (mut out, mut err) = (Vec::new(), Vec::new());
            let passed = test_dir(&sources(), &dir, verbose, &mut out, &mut err);
            let text = |b: Vec<u8>| String::from_utf8(b).unwrap();
            (passed, without_steps(&text(out)), text(err))
        })
        .unwrap()
        .join()
        .unwrap()
}

/// Output without each passing test's steps, ` (123 steps)`, which
/// [`runaway_tests_fail_alone`] checks, so the other tests needn't change
/// when the interpreter's steps do.
fn without_steps(out: &str) -> String {
    let mut lines = Vec::new();
    for line in out.split_inclusive('\n') {
        let trimmed = line.trim_end_matches('\n');
        let cut = (trimmed.strip_suffix(" steps)"))
            .and_then(|rest| rest.rsplit_once(" ("))
            .filter(|(_, n)| !n.is_empty() && n.bytes().all(|b| b.is_ascii_digit()));
        match cut {
            Some((kept, _)) if trimmed.starts_with("  ok      ") => {
                lines.push(format!("{kept}{}", &line[trimmed.len()..]))
            }
            _ => lines.push(line.to_string()),
        }
    }
    lines.concat()
}

/// Tests the libraries under `dir` verbosely, with `fuel` steps for each
/// test, giving whether all passed and what was written to `out`.
fn test_with_fuel(dir: &str, fuel: u64) -> (bool, String) {
    let dir = dir.to_string();
    std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(move || {
            let options = TestOptions {
                verbose: true,
                fuel: Some(fuel),
                ..TestOptions::default()
            };
            let (mut out, mut err) = (Vec::new(), Vec::new());
            let sources = Sources::standard();
            let (passed, tally) =
                test_libraries(&sources, &dir, &options, &mut out, &mut err).unwrap();
            let mut out = String::from_utf8(out).unwrap();
            out.push_str(&format!("{}\n", tally.summary()));
            (passed, out)
        })
        .unwrap()
        .join()
        .unwrap()
}

const CONFIG: &str = "# Lib\n\n    export let name = \"lib\";\n";

/// Another library's config, named for its first line.
const OTHER: &str = "# Other\n\nAnother library.\n";

#[test]
fn passing_and_failing_tests() {
    let tree = write_tree(
        "results",
        &[
            ("src/config.temper.md", CONFIG),
            (
                "src/a.temper",
                "export let double(x: Int): Int { x * 2 }\n\
                 test(\"doubles\") { assert(double(2) == 4) }\n\
                 test(\"<odd> & 'quoted'\") { assert(double(2) == 5) }\n",
            ),
        ],
    );
    let root = &tree.root;
    let (passed, out, err) = test(root);
    assert_eq!(err, "");
    assert_eq!(
        out,
        format!(
            "Testing {root}/src\n  \
             ok      doubles\n  \
             failed  <odd> & 'quoted' - expected double(2) == (5) not (4)\n\
             Tests passed: 1 of 2\n"
        )
    );
    assert!(!passed);
}

#[test]
fn quiet_by_default() {
    let tree = write_tree(
        "quiet",
        &[
            ("good/config.temper.md", CONFIG),
            ("good/a.temper", "test(\"fine\") { assert(true) }\n"),
            ("bad/config.temper.md", OTHER),
            (
                "bad/b.temper",
                "test(\"fine\") { assert(true) }\n\
                 test(\"broken\") { assert(1 == 2) }\n",
            ),
        ],
    );
    let root = &tree.root;
    let (passed, out, err) = test_in(root, false, Sources::standard);
    assert_eq!(err, "");
    // Like `temper test`: only failures, under their library, and the summary.
    assert_eq!(
        out,
        format!(
            "Testing {root}/bad\n  \
             failed  broken - expected 1 == (2) not (1)\n\
             Tests passed: 2 of 3\n"
        )
    );
    assert!(!passed);
}

#[test]
fn work_root_and_skipped_directories() {
    let tree = write_tree(
        "root",
        &[
            ("temper.out/x", ""),
            ("lib/config.temper.md", CONFIG),
            (
                "lib/a.temper.md",
                "# A\n\n    test(\"a\") { assert(true) }\n",
            ),
            ("lib/sub/b.temper", "let b = 1;\n"),
            ("lib/.hidden/c.temper", "this is not Temper (\n"),
            ("lib/temper.keep/d.temper", "nor this (\n"),
            ("other/config.temper.md", OTHER),
        ],
    );
    let root = &tree.root;
    // From a library's subdirectory, `temper.out` marks the work root.
    let disk = Sources::standard();
    assert_eq!(work_root(&disk, &format!("{root}/lib/sub")), *root);
    let sources = |dir: &str, files: &[&str], config: &str| LibrarySources {
        root: format!("{root}/{dir}"),
        files: files.iter().map(|f| format!("{root}/{f}")).collect(),
        host_files: Vec::new(),
        config: Config::read(config, dir),
    };
    assert_eq!(
        find_libraries(&disk, root),
        [
            sources("lib", &["lib/a.temper.md", "lib/sub/b.temper"], CONFIG),
            sources("other", &[], OTHER),
        ]
    );
    let (passed, out, _) = test(&format!("{root}/lib/sub"));
    assert!(out.contains("/lib\n  ok      a\nTesting "), "{out}");
    assert!(out.ends_with("/other\nTests passed: 1 of 1\n"), "{out}");
    assert!(passed);
}

#[test]
fn gitignore_files_skip_what_they_list() {
    // As Hrefine's checkout: a build copied a library under
    // `bench/build/`, which the root's `.gitignore` lists, so its config
    // doesn't make a second library of the same name.
    const HREFINE: &str = "# Hrefine\n\n    export let name = \"hrefine\";\n";
    let test_file = "# A\n\n    test(\"a\") { assert(true) }\n";
    let tree = write_tree(
        "gitignore",
        &[
            (".git/HEAD", ""),
            (".gitignore", "# Outputs\nbuild/\n/scratch.temper\n"),
            ("repo/.gitignore", "/src/old/\n*.tmp.temper\n"),
            ("repo/config.temper.md", HREFINE),
            ("repo/src/a.temper.md", test_file),
            ("repo/src/x.tmp.temper", "not Temper (\n"),
            ("repo/src/old/b.temper", "nor this (\n"),
            ("repo/scratch.temper", "let s = 1;\n"),
            ("repo/bench/build/lib/src/config.temper.md", HREFINE),
            ("repo/bench/build/lib/src/a.temper.md", test_file),
            // A deeper file re-includes what a shallower one excludes.
            ("repo/gen/.gitignore", "!keep.tmp.temper\n"),
            ("repo/gen/keep.tmp.temper", "let k = 1;\n"),
        ],
    );
    let root = format!("{}/repo", tree.root);
    let disk = Sources::standard();
    let found = find_libraries(&disk, &root);
    assert_eq!(found.len(), 1, "{found:?}");
    let files: Vec<&str> = (found[0].files.iter())
        .map(|f| f.strip_prefix(&root).unwrap_or(f))
        .collect();
    // The repository's root's `/scratch.temper` is its own, not the work
    // root's.
    assert_eq!(
        files,
        [
            "/gen/keep.tmp.temper",
            "/scratch.temper",
            "/src/a.temper.md"
        ]
    );
    let (passed, out, _) = test(&root);
    assert!(out.ends_with("Tests passed: 1 of 1\n"), "{out}");
    assert!(passed);
}

#[test]
fn tests_not_run_and_errors() {
    let tree = write_tree(
        "not-run",
        &[
            ("config.temper.md", CONFIG),
            (
                "a.temper",
                "console.log(\"starting\");\nlet x = y;\ntest(\"a\") { assert(true) }\n",
            ),
        ],
    );
    let root = &tree.root;
    let (passed, out, err) = test(root);
    assert_eq!(
        err,
        format!("{root}/a.temper:2:9: error: y has not been declared\n")
    );
    assert_eq!(
        out,
        format!(
            "Testing {root}\nstarting\n  Failed: y has not been declared\nTests passed: 0 of 1 (1 not run)\n"
        )
    );
    assert!(!passed);
}

#[test]
fn no_libraries() {
    let tree = write_tree("empty", &[("a.temper", "let a = 1;\n")]);
    let root = &tree.root;
    let (passed, _, err) = test(root);
    assert_eq!(
        err,
        format!("No Temper library (config.temper.md) under {root}\n")
    );
    assert!(!passed);
}

#[test]
fn std_built_in_or_from_a_directory() {
    let testing = stdlib::FILES
        .iter()
        .find(|&&(path, _)| path == "testing/testing.temper.md")
        .unwrap()
        .1;
    let tree = write_tree(
        "std",
        &[
            ("lib/config.temper.md", CONFIG),
            (
                "lib/a.temper",
                "let { Date } = import(\"std/temporal\");\n\
                 test(\"date\") { assert((new Date(2024, 3, 5) orelse panic()).year == 2024) }\n",
            ),
            ("other/config.temper.md", CONFIG),
            (
                "other/b.temper",
                "let { m } = import(\"std/m\");\ntest(\"m\") { assert(m == 1) }\n",
            ),
            ("std/testing/testing.temper.md", testing),
            ("std/m/m.temper", "export let m = 1;\n"),
        ],
    );
    let root = &tree.root;
    // Built in: temporal and testing, but no `m`.
    let (passed, out, err) = test(&format!("{root}/lib"));
    assert_eq!(err, "");
    assert!(
        out.ends_with("  ok      date\nTests passed: 1 of 1\n"),
        "{out}"
    );
    assert!(passed);
    let (passed, _, err) = test(&format!("{root}/other"));
    assert!(err.contains("Import of std/m failed"), "{err}");
    assert!(!passed);
    // From a directory, which has `m` and testing.
    let std_dir = Some(format!("{root}/std"));
    let (passed, out, err) = test_with(&format!("{root}/other"), std_dir);
    assert_eq!(err, "");
    assert!(
        out.ends_with("  ok      m\nTests passed: 1 of 1\n"),
        "{out}"
    );
    assert!(passed);
}

#[test]
fn junit_reports() {
    let lines: Vec<String> = [
        "<testsuites>",
        "  <testsuite name='suite' tests='2' failures='1' time='0.0'>",
        "    <testcase name='a &amp; b' classname='a &amp; b' time='0.0' />",
        "    <testcase name='c' classname='c' time='0.0'>",
        "      <failure message='&lt;x&gt; &#39;y&#39; &#34;z&#34;' />",
        "    </testcase>",
        "  </testsuite>",
        "</testsuites>",
    ]
    .map(String::from)
    .into();
    assert_eq!(
        parse_junit(&lines),
        [
            ("a & b".to_string(), None),
            ("c".to_string(), Some("<x> 'y' \"z\"".to_string())),
        ]
    );
}

/// A library in memory, as the wasm build or an editor's unsaved
/// buffers would give it, with std built in.
#[test]
fn library_in_memory() {
    let files = [
        ("app/config.temper.md", CONFIG),
        (
            "app/a.temper",
            "let { Date } = import(\"std/temporal\");\nlet { b } = import(\"./b\");\n\
             test(\"date\") { assert((new Date(2024, 3, 5) orelse panic()).year == b) }\n",
        ),
        ("app/b/b.temper", "export let b = 2024;\n"),
    ];
    let sources = Sources::memory(&files);
    assert_eq!(work_root(&sources, "app/b"), "app");
    let (passed, out, err) = test_in("app/b", true, move || Sources::memory(&files));
    assert_eq!(err, "");
    assert_eq!(out, "Testing app\n  ok      date\nTests passed: 1 of 1\n");
    assert!(passed);
}

/// Libraries' names, as Kotlin's `libraryNameFor` gives them: what the
/// config exports, else its first line as a slug, else the root's path.
#[test]
fn library_names() {
    let name = |text: &str, path: &str| Config::read(text, path).name;
    assert_eq!(name(CONFIG, "src"), "lib");
    assert_eq!(name("# Hi There!\n", "src"), "hi-there");
    assert_eq!(
        name("\n    export let version = \"1.2.0\";\n", "source/path"),
        "source-path"
    );
    assert_eq!(name("", ""), "unnamed");
    let config = Config::read(
        "# A\n\n    export let name = \"a\";\n    export let version = \"1.2.0\";\n",
        "",
    );
    assert_eq!(config.get("version"), Some("1.2.0"));
    assert_eq!(config.version_or_default(), "1.2.0");
    assert_eq!(Config::read(CONFIG, "").version_or_default(), "0.0.1");
}

/// A library imports another by its name, as `import("other-lib/sub")`,
/// and each runs only its own tests.
#[test]
fn libraries_import_each_other() {
    let tree = write_tree(
        "imports",
        &[
            ("config.temper.md", "# Root\n"),
            (
                "app/config.temper.md",
                "# App\n\n    export let name = \"app\";\n",
            ),
            (
                "app/a.temper",
                "let { twice } = import(\"util/math\");\n\
                 let { one } = import(\"util\");\n\
                 test(\"uses util\") { assert(twice(one) == 2) }\n",
            ),
            (
                "util/config.temper.md",
                "# Util\n\n    export let name = \"util\";\n",
            ),
            (
                "util/u.temper",
                "export let one = 1;\ntest(\"util's own\") { assert(one == 1) }\n",
            ),
            (
                "util/math/m.temper",
                "export let twice(x: Int): Int { x * 2 }\n",
            ),
        ],
    );
    let root = &tree.root;
    let (passed, out, err) = test(root);
    assert_eq!(err, "");
    assert!(out.contains("/app\n  ok      uses util\n"), "{out}");
    assert!(out.contains("/util\n  ok      util's own\n"), "{out}");
    assert!(out.ends_with("Tests passed: 2 of 2\n"), "{out}");
    assert!(passed);
}

/// `import("..")` and `import(".")` are relative, as `../` and `./`
/// are, and a bare `import("…")` statement makes a module part of the
/// library without ordering it, so it may name a module that imports the
/// one naming it, as Kotlin allows.
#[test]
fn parent_and_bare_imports() {
    let tree = write_tree(
        "parent-imports",
        &[
            ("config.temper.md", CONFIG),
            ("top.temper", "export let top = 1;\nimport(\"./checks\");\n"),
            (
                "checks/checks.temper",
                "let { top } = import(\"..\");\nlet { inner } = import(\"./inner\");\n\
                 test(\"parent and child\") { assert(top + inner == 3) }\n",
            ),
            ("checks/inner/inner.temper", "export let inner = 2;\n"),
        ],
    );
    let root = &tree.root;
    let (passed, out, err) = test(root);
    assert_eq!(err, "", "{out}");
    assert!(
        out.ends_with("  ok      parent and child\nTests passed: 1 of 1\n"),
        "{out}"
    );
    assert!(passed);
    // A module importing itself, or one that isn't there, fails.
    let tree = write_tree(
        "bare-import-errors",
        &[
            ("config.temper.md", CONFIG),
            ("a.temper", "import(\".\");\nimport(\"./missing\");\n"),
        ],
    );
    let root = &tree.root;
    let (passed, _, err) = test(root);
    assert_eq!(
        err,
        format!(
            "{root}/a.temper:1:1: error: Import of . failed\n\
             {root}/a.temper:2:1: error: Import of ./missing failed\n"
        )
    );
    assert!(!passed);
}

/// Two libraries of one name are an error, as in Kotlin.
#[test]
fn duplicate_names() {
    let tree = write_tree(
        "duplicates",
        &[
            ("a/config.temper.md", CONFIG),
            ("b/config.temper.md", CONFIG),
            ("config.temper.md", "# Root\n"),
        ],
    );
    let root = &tree.root;
    let (passed, _, err) = test(root);
    assert_eq!(err, format!("Two libraries are named lib under {root}\n"));
    assert!(!passed);
}

/// Core's config, as `lib/core/config.temper.md` has it.
const CORE_CONFIG: &str = "# Core\n\n    export let name = \"temper-core\";\n";

/// `temper-core` is core's name: building another library of that name
/// is an error, and its files have none of core's powers, as
/// naming `_listify`. Only a Temper checkout's own `lib/core` is core,
/// whatever its text, as an editor may have changed it.
#[test]
fn temper_core_is_reserved() {
    let text = "export let xs: List<Int> = _listify(1);\n";
    let files = [
        ("user/config.temper.md", CORE_CONFIG),
        ("user/core.temper", text),
    ];
    let (passed, _, err) = test_in("user", true, move || Sources::memory(&files));
    assert!(
        err.contains("temper-core is reserved for Temper's core library"),
        "{err}"
    );
    assert!(!passed);
    // Loaded anyway, it's no more core than any library.
    let sources = Sources::memory(&files);
    let libraries = find_libraries(&sources, "user");
    let mut library = temper_frontend::library::Library::new();
    library.libraries = vec![("temper-core".into(), "user".into())];
    library.add_file("user/core.temper", text, SourceKind::Standalone);
    assert!(!library.in_core(library.files[0].base));
    assert!(load_library(&sources, &libraries[0], &libraries).is_err());
    // A copy of the built-in core, as editors write, is core, by its
    // files' texts, but not with another file or one missing.
    let copy = |extra: &[(&'static str, &'static str)], skip: usize| {
        let core = temper_frontend::corelib::SOURCES;
        let files: Vec<(String, &str)> = std::iter::once(("config.temper.md", CORE_CONFIG))
            .chain(core.iter().skip(skip).copied())
            .chain(extra.iter().copied())
            .map(|(path, text)| (format!("copy/{path}"), text))
            .collect();
        let files: Vec<(&str, &str)> = files.iter().map(|(p, t)| (p.as_str(), *t)).collect();
        let sources = Sources::memory(&files);
        let libraries = find_libraries(&sources, "copy");
        load_library(&sources, &libraries[0], &libraries).is_ok()
    };
    assert!(copy(&[], 0));
    assert!(!copy(&[("more.temper", "export let more = 1;\n")], 0));
    assert!(!copy(&[], 1));
    // A checkout's core, edited, each file of it: in `dir` under a
    // repository with the compiler's marker if `marker`, named `name`.
    let (first, text) = temper_frontend::corelib::SOURCES[0];
    let edited = format!("{text}\nexport let edited = 1;\n");
    let checkout = |dir: &str, marker: bool, name: &str| {
        let config = format!("# Core\n\n    export let name = \"{name}\";\n");
        let mut files: Vec<(String, String)> = vec![
            ("repo/Cargo.toml".into(), "[workspace]\n".into()),
            (format!("repo/{dir}/config.temper.md"), config),
        ];
        if marker {
            let marker = "repo/crates/temper-frontend/src/corelib.rs";
            files.push((marker.into(), "//! Core.\n".into()));
        }
        for &(path, text) in temper_frontend::corelib::SOURCES {
            let text = if path == first { edited.as_str() } else { text };
            files.push((format!("repo/{dir}/{path}"), text.to_string()));
        }
        let files: Vec<(&str, &str)> = files
            .iter()
            .map(|(p, t)| (p.as_str(), t.as_str()))
            .collect();
        let sources = Sources::memory(&files);
        let libraries = find_libraries(&sources, &format!("repo/{dir}"));
        load_library(&sources, &libraries[0], &libraries)
    };
    let library = checkout("lib/core", true, "temper-core").unwrap();
    assert_eq!(library.files.len(), temper_frontend::corelib::SOURCES.len());
    for file in &library.files {
        assert!(library.in_core(file.base), "{}", file.path);
    }
    // Not without the compiler's marker, nor anywhere but `lib/core`.
    let reserved = "temper-core is reserved for Temper's core library";
    for (dir, marker) in [
        ("lib/core", false),
        ("lib/core2", true),
        ("libs/core", true),
    ] {
        let err = checkout(dir, marker, "temper-core").err();
        assert!(err.is_some_and(|e| e.contains(reserved)), "{dir}");
    }
    // Another library there isn't core: its edited file has no powers.
    let library = checkout("lib/core", true, "other").unwrap();
    let edited_file = (library.files.iter())
        .find(|f| f.path.ends_with(first))
        .unwrap();
    assert!(!library.in_core(edited_file.base));
    // A file whose text is exactly one of core's is core's in any
    // library, as tests and editors settle core's files from other
    // paths: it can do only what core's own text does. The library's
    // other files have no powers.
    let mut library = temper_frontend::library::Library::new();
    library.add_file("other/copy.temper.md", text, SourceKind::Markdown);
    library.add_file(
        "other/mine.temper",
        "export let xs = 1;\n",
        SourceKind::Standalone,
    );
    assert!(library.in_core(library.files[0].base));
    assert!(!library.in_core(library.files[1].base));
}

/// `std` is std's name, as `temper-core` is core's: another library of
/// that name is an error. A Temper checkout's `lib/std`, edited or not,
/// and the copy of the built-in std that editors write, are std: they
/// settle and translate as std does, `@json` and `@connected` included,
/// not as a library with a second std beside it.
#[test]
fn std_is_reserved() {
    let config = "# Std\n\n    export let name = \"std\";\n";
    let files = [
        ("user/config.temper.md", config),
        ("user/a.temper", "export let a = 1;\n"),
    ];
    let (passed, _, err) = test_in("user", true, move || Sources::memory(&files));
    assert!(
        err.contains("user: std is reserved for Temper's std library"),
        "{err}"
    );
    assert!(!passed);
    // Std's files under `root`, the first edited if `edit`, with more.
    let std_at = |root: &str, edit: bool, more: &[(&str, &'static str)]| {
        let mut files: Vec<(String, String)> = (more.iter())
            .map(|(p, t)| (p.to_string(), t.to_string()))
            .collect();
        for (i, (path, text)) in stdlib::FILES.iter().enumerate() {
            let text = match edit && i == 1 {
                true => format!("{text}\n    export let edited = 1;\n"),
                false => text.to_string(),
            };
            files.push((format!("{root}/{path}"), text));
        }
        files
    };
    // Loads std from `files` at `root`, checks it's std, and settles and
    // translates it without errors; or the error loading it.
    let load = |files: Vec<(String, String)>, root: &'static str| {
        std::thread::Builder::new()
            .stack_size(64 << 20)
            .spawn(move || {
                let files: Vec<(&str, &str)> = files
                    .iter()
                    .map(|(p, t)| (p.as_str(), t.as_str()))
                    .collect();
                let sources = Sources::memory(&files);
                let libraries = find_libraries(&sources, root);
                let library = load_library(&sources, &libraries[0], &libraries)?;
                for file in library.files.iter().filter(|f| f.path.starts_with(root)) {
                    assert!(library.in_std(file.base), "{}", file.path);
                }
                let settled = temper_frontend::settle::settle(library);
                let errors: Vec<String> = (settled.library.diagnostics.iter())
                    .filter(|d| d.level == temper_frontend::library::Level::Error)
                    .map(|d| d.message.text())
                    .collect();
                assert!(errors.is_empty(), "{errors:?}");
                temper_frontend::to_tmpl::to_tmpl(&settled, "std").unwrap();
                Ok::<(), String>(())
            })
            .unwrap()
            .join()
            .unwrap()
    };
    assert_eq!(load(std_at("copy", false, &[]), "copy"), Ok(()));
    let more = [("copy/more/more.temper", "export let more = 1;\n")];
    assert!(load(std_at("copy", false, &more), "copy").is_err());
    assert!(load(std_at("copy", true, &[]), "copy").is_err());
    let repo = [
        ("repo/Cargo.toml", "[workspace]\n"),
        ("repo/crates/temper-frontend/src/stdlib.rs", "//! Std.\n"),
    ];
    let checkout = std_at("repo/lib/std", true, &repo);
    assert_eq!(load(checkout, "repo/lib/std"), Ok(()));
}

/// A test that loops forever runs out of its steps and fails alone; the
/// tests before and after it run and pass, each with a budget of its own.
#[test]
fn runaway_tests_fail_alone() {
    let tree = write_tree(
        "runaway",
        &[
            ("src/config.temper.md", CONFIG),
            (
                "src/a.temper",
                "test(\"before\") { assert(1 + 1 == 2) }\n\
                 test(\"loops\") { var i = 0; while (i >= 0) { i = (i + 1) % 10 } }\n\
                 test(\"after\") {\n  var i = 0;\n  while (i < 1000) { i += 1; }\n  assert(i == 1000);\n}\n",
            ),
        ],
    );
    let root = &tree.root;
    let (passed, out) = test_with_fuel(root, 20_000);
    let lines: Vec<&str> = out.lines().collect();
    assert_eq!(lines[0], format!("Testing {root}/src"), "{out}");
    let steps = |line: &str, name: &str| -> u64 {
        let rest = line.strip_prefix(&format!("  ok      {name} ("));
        let n = rest.and_then(|r| r.strip_suffix(" steps)"));
        n.unwrap_or_else(|| panic!("{line}")).parse().unwrap()
    };
    assert!(steps(lines[1], "before") < 5_000, "{out}");
    assert_eq!(
        lines[2],
        "  failed  loops - Panicked: ran out of steps (each test may take 20000)"
    );
    // The loop alone takes several thousand steps, which a budget shared
    // with the test before wouldn't leave room for.
    assert!((5_000..20_000).contains(&steps(lines[3], "after")), "{out}");
    assert_eq!(lines[4..], ["Tests passed: 2 of 3"]);
    assert!(!passed);
}

/// The audit's probe, a `for` loop missing its `i++` (control-flow/loops),
/// at a library's top level: it logs until it runs out of steps, keeping
/// only the first 64 KiB of what it logged, then a note, and fails.
#[test]
fn runaway_top_level_is_cut_short() {
    let tree = write_tree(
        "runaway-top",
        &[
            ("src/config.temper.md", CONFIG),
            (
                "src/a.temper",
                "for (var i = 0; i < 5;) { console.log(i.toString()); }\n\
                 test(\"never runs\") { assert(true) }\n",
            ),
        ],
    );
    let root = &tree.root;
    let (passed, out) = test_with_fuel(root, 1_000_000);
    assert!(out.len() < (64 << 10) + 1_000, "{} bytes", out.len());
    let tail: Vec<&str> = out.lines().rev().take(3).collect();
    assert!(
        tail[2].starts_with("[output truncated: ")
            && tail[2].ends_with(" more lines past 65536 bytes]"),
        "{tail:?}"
    );
    assert_eq!(
        tail[1],
        "  Panicked: ran out of steps (the top-level code may take 1000000)"
    );
    assert_eq!(tail[0], "Tests passed: 0 of 1 (1 not run)");
    assert!(!passed);
}
