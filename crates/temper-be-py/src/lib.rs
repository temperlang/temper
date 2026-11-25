//! Temper's Python backend: tmpl in, Python out.
//!
//! Works from tmpl alone (notes/tmpl/overview.md, "Boundaries"). Its
//! variants share the translation and differ only in data
//! (notes/tmpl/profiles.md, "Variants and the first profiles"); the first,
//! [`PY`], is Kotlin-compatible: the same package and module layout,
//! `temper_core` runtime, and public names as Kotlin Temper's Python
//! backend, with prettier function bodies (no numbered names, f-strings,
//! Python's own operators where they agree with Temper's). A library
//! module's only public names are its exports, which `__all__` lists:
//! everything else it binds at module level starts with `_`, imports
//! (`mapped_for_each as _mapped_for_each`), `TypeVar`s, and the `def`s
//! and temporaries the translator makes included.
//!
//! The pipeline: tmpl's lowerings, the program partitioned by reach
//! ([`temper_tmpl::reach::partition`]: what only tests reach in the
//! tests files, what nothing does left out), the naming pass
//! ([`temper_tmpl::naming::assign`]) with Python's rules
//! ([`names::RULES`]), then [`translate`] into Python's
//! output tree ([`tree`]), with operators spelled by [`ops`], core's
//! connected declarations by [`connected`], and classes and interfaces
//! by [`classes`], then the printer.
//!
//! It covers libraries of several modules, imports between them and
//! from other libraries, and tests; classes and interfaces (fields, constructors, methods,
//! getters and setters, statics, default and abstract members, `is`,
//! `as`, `==` and `toString` protocols), and nullable types (`T | None`,
//! `is None` checks, `??` as a `:=` test), `orelse` as `try` and
//! `except Exception`, `bubble()` as `raise RuntimeError()`, and
//! `panic()` as `raise BaseException('panic')`, which no `orelse`
//! catches; and generics: module-level `TypeVar`s (invariant, as
//! Temper's type parameters are, where Kotlin's makes some covariant),
//! `Generic[…]` bases, and core's lists and maps as Kotlin's runtime
//! models them (`List` and `Listed` a `Sequence`, a literal a `tuple`,
//! `ListBuilder` a `MutableSequence` made as `[]`, `Map` a
//! `MappingProxyType`, `Mapped` a `Mapping`, and `MapBuilder` a
//! `dict`). What can't go inside a Python expression (an `orelse`, a
//! block's statements, an `if` or `&&` whose branch needs statements) is
//! hoisted into statements before it, with temporaries keeping what's
//! evaluated earlier in order ([`translate`]). Anything else is
//! [`NotYet`].

pub mod classes;
pub mod connected;
pub mod names;
pub mod ops;
pub mod runtime;
// Not in wasm, which runs no tools.
#[cfg(any(unix, windows))]
pub mod tools;
pub mod translate;
pub mod tree;

use std::fmt;
use temper_tmpl::coroutines::CoroutineStrategy;
use temper_tmpl::naming::{NamingRules, assign_checked};
use temper_tmpl::version::{self, Spelling};
use temper_tmpl::{Features, Profile, Program};

/// A named set of translation choices for Python.
pub struct Variant {
    /// Its name on the command line: `py`.
    pub name: &'static str,
    /// What it handles natively, which lowering and validation read.
    pub profile: Profile,
    pub rules: NamingRules,
}

/// The first variant: Kotlin-compatible API and names, pretty bodies.
/// Everything is native; statements inside expressions are hoisted by
/// the translator itself.
pub static PY: Variant = Variant {
    name: "py",
    profile: Profile {
        native: Features::STATEMENT_EXPRESSIONS
            .with(Features::RUNS_TYPE_ERRORS)
            .with(Features::NEVER),
        coroutines: CoroutineStrategy::TranslateToGenerator,
    },
    rules: names::RULES,
};

/// Every variant, by name.
pub const VARIANTS: &[&Variant] = &[&PY];

/// Something the translation doesn't cover yet.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NotYet {
    pub what: String,
}

impl fmt::Display for NotYet {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "not yet in the Python backend: {}", self.what)
    }
}

impl std::error::Error for NotYet {}

pub use temper_tmpl::OutFile;

/// What a library's config may set for this backend, `export let py:
/// PyConfig = { … };`, as Kotlin's `PyConfig`; and std's, as Kotlin's
/// backends inject it.
pub const CONFIG: temper_tmpl::ConfigType = temper_tmpl::ConfigType {
    key: "py",
    class: "PyConfig",
    properties: &[
        temper_tmpl::Property {
            name: "name",
            doc: "The Python project's name, which, as a Python name, also names its package; else the library's name.",
            kind: temper_tmpl::ConfigKind::String {
                form: "a PyPI project's name, as `geometry-py`",
                valid: pypi_name,
            },
        },
        temper_tmpl::Property {
            name: "dependencies",
            doc: "PyPI packages that the library's host code uses, each `\"name==version\"`.",
            kind: temper_tmpl::ConfigKind::Strings {
                form: "name==version",
                package: |d| pypi_dependency(d).map(|(name, _)| names::project_name(name)),
            },
        },
    ],
    std: &[("name", "temper-std")],
    names: |library, metadata| vec![names::project_name(&translate::py_name(library, metadata))],
    runtime: &["temper-core"],
};

/// A package that a config's `dependencies` lists, as `"name==version"`:
/// its name ([`pypi_name`]) and version, PEP 440's characters, as
/// `1.0.post1`, `2!1.0`, `1.*`, or `1.0+local`: no extras, URLs, or
/// requirement file options.
pub fn pypi_dependency(text: &str) -> Option<(&str, &str)> {
    match temper_tmpl::dependency_parts(text, "==", 2)?.as_slice() {
        &[name, version] if pypi_name(name) && temper_tmpl::spelled(version, ".*+!_-") => {
            Some((name, version))
        }
        _ => None,
    }
}

/// Whether a name is a PyPI project's, as PEP 508 has them: ASCII
/// letters, digits, `.`, `_`, and `-`, starting and ending with a letter
/// or digit.
pub fn pypi_name(text: &str) -> bool {
    temper_tmpl::spelled(text, "._-") && text.ends_with(|c: char| c.is_ascii_alphanumeric())
}

/// The packages a library's host code uses, as its config lists them,
/// each as pip takes it, `name==version`.
pub fn pypi_dependencies(metadata: &temper_tmpl::Metadata) -> Vec<String> {
    (CONFIG.strings(metadata, "dependencies").iter())
        .filter_map(|d| pypi_dependency(d))
        .map(|(name, version)| format!("{name}=={version}"))
        .collect()
}

/// This backend, as tools list it ([`temper_tmpl::Backend`]).
pub const BACKEND: temper_tmpl::Backend = temper_tmpl::Backend {
    name: "py",
    comment: "#",
    runtime: if cfg!(target_family = "wasm") {
        &[]
    } else {
        runtime::FILES
    },
    runtime_version: &runtime::VERSION,
    rules: &PY.rules,
    translate: |p, _| translate(p, &PY).map_err(|e| e.to_string()),
    config: &CONFIG,
    default: true,
    #[cfg(any(unix, windows))]
    tools: Some(&tools::TOOLS),
    #[cfg(not(any(unix, windows)))]
    tools: None,
};

/// `pyproject.toml`'s dependencies, as Kotlin writes them: the runtime,
/// each library used, by its name, with `==` its version where it sets
/// one, in PEP 440's spelling, and the packages its config lists, a
/// quoted line each, sorted; with
/// `tests`, instead those only the tests use
/// ([`temper_tmpl::analysis::test_only_libraries`]), for the `test`
/// dependency group, which installing the package skips.
fn dependencies(p: &Program, tests: bool) -> String {
    let tests_only = temper_tmpl::analysis::test_only_libraries(p);
    let mut lines = Vec::new();
    if !tests {
        let runtime = version::current().spell(Spelling::Python);
        lines.push(format!("temper-core=={runtime}"));
        lines.extend(pypi_dependencies(p.metadata()));
    }
    for (name, version) in p.dependencies() {
        if tests_only.iter().any(|t| t == name) != tests {
            continue;
        }
        let name = translate::library_name(p, name);
        let name = name.as_str();
        lines.push(match version {
            "0.0.0" | "" => name.to_string(),
            v => format!("{name}=={}", version::respell(v, Spelling::Python)),
        });
    }
    lines.sort();
    let quoted = (lines.iter()).map(|l| format!("    {}", temper_tmpl::json_string(l)));
    let quoted: Vec<String> = quoted.collect();
    quoted.join(",\n")
}

/// A config's `authors` as pyproject.toml's author: its name, without
/// the commas a name there can't have, as Kotlin's, and its email apart
/// where it ends with one, as `Name <name@example.com>`.
fn author(authors: &str) -> String {
    let quoted = temper_tmpl::json_string;
    let (name, email) = match authors
        .trim()
        .strip_suffix('>')
        .and_then(|a| a.rsplit_once('<'))
    {
        Some((name, email)) if email.contains('@') => (name.trim(), Some(email.trim())),
        _ => (authors.trim(), None),
    };
    let name = name.replace(',', "");
    match (name.as_str(), email) {
        ("", Some(email)) => format!("{{ email = {} }}", quoted(email)),
        (name, Some(email)) => format!("{{ name = {}, email = {} }}", quoted(name), quoted(email)),
        (name, None) => format!("{{ name = {} }}", quoted(name)),
    }
}

/// Translates a library, laid out as Kotlin's Python backend lays it out:
///
/// ```text
/// mods/pyproject.toml
/// mods/mods/__init__.py            imports each module, then
///                                  from .mods import *
/// mods/mods/py.typed               empty: the package is typed (PEP 561)
/// mods/mods/mods.py                the library's root module
/// mods/mods/helpers/__init__.py    a module with modules under it
/// mods/mods/helpers/deep.py        one without
/// mods/tests/__init__.py           empty
/// mods/tests/test_mods.py          the root module's tests
/// mods/tests/test_helpers_deep.py  another's
/// ```
///
/// A tests file has what only its module's tests reach, before them, and
/// imports what they use of the module, its private names too.
///
/// The project directory takes the library's name; the package and the
/// root module's file take it as a Python name (`hello-world` gives
/// `hello_world`).
pub fn translate(p: &Program, variant: &Variant) -> Result<Vec<OutFile>, NotYet> {
    // Type-only errors run as written, or fail where they are
    // (`temper_tmpl::errors`), by the profile.
    let settled = temper_tmpl::errors::settle(p, &variant.profile);
    let p = settled.as_ref().unwrap_or(p);
    // `Never<T>` as `T`, but where the profile keeps it
    // (`temper_tmpl::nevers`).
    let nevered = temper_tmpl::nevers::lower(p, &variant.profile);
    let p = nevered.as_ref().unwrap_or(p);
    // `Switch` as `if`s (`temper_tmpl::switches`); none comes here yet.
    let chained = (!variant.profile.native.contains(Features::SWITCH))
        .then(|| temper_tmpl::switches::to_if_chains(p))
        .flatten();
    let p = chained.as_ref().unwrap_or(p);
    // What only tests reach apart, in the tests files, and what nothing
    // reaches left out (`temper_tmpl::reach`), as the lowerings left it:
    // errors' may drop a call.
    let parted = temper_tmpl::reach::partition(p, connected::connects_std);
    let p = &parted;
    let names = assign_checked(p, &variant.rules).map_err(|what| NotYet { what })?;
    // std's is `temper-std`, its package `temper_std`, as Kotlin's; a
    // config's `PyConfig` name names the project and its package, but not its
    // directory, which has the library's name.
    let project = translate::library_name(p, &p.library.name);
    let package = names::library_package(&project);
    let library = match p.library.name.as_str() {
        "std" => &project,
        name => name,
    };
    let mut translator = translate::Translator::new(p, &names);
    let mut sources = Vec::new();
    let mut tests = Vec::new();
    let mut inits = Vec::new();
    for module in &p.modules {
        let path = &module.path;
        let parent =
            !path.is_empty() && (p.modules.iter()).any(|m| m.path.starts_with(&format!("{path}/")));
        // Each part a Python name, as `extra_class` for `extra-class`.
        let py_path: Vec<String> = path.split('/').map(names::package_name).collect();
        let py_path = py_path.join("/");
        let file = if path.is_empty() {
            format!("{package}/{package}.py")
        } else if parent {
            format!("{package}/{py_path}/__init__.py")
        } else {
            format!("{package}/{py_path}.py")
        };
        if file == format!("{package}/{package}.py") && !path.is_empty() {
            return Err(NotYet {
                what: format!("a module named as its library's root, {path}"),
            });
        }
        let tree = translator.module(module)?;
        sources.push(OutFile {
            path: format!("{library}/{file}"),
            text: tree::module(&tree),
        });
        let stem = match path.as_str() {
            "" => package.clone(),
            path => names::package_name(&path.replace('/', "_")),
        };
        let class = format!("Test{}", temper_tmpl::naming::pascal_case(&stem));
        if let Some(tree) = translator.tests(module, &class)? {
            tests.push(OutFile {
                path: format!("{library}/tests/test_{stem}.py"),
                text: tree::module(&tree),
            });
        }
        if !path.is_empty() {
            let (parent, leaf) = match path.rsplit_once('/') {
                Some((parent, leaf)) => {
                    let parts: Vec<String> = parent.split('/').map(names::package_name).collect();
                    (parts.join("."), leaf)
                }
                None => (String::new(), path.as_str()),
            };
            // `as` itself: an explicit re-export, which checkers don't call
            // an unused import.
            let leaf = names::package_name(leaf);
            inits.push(format!("from .{parent} import {leaf} as {leaf}\n"));
        }
    }
    if !tests.is_empty() {
        tests.insert(
            0,
            OutFile {
                path: format!("{library}/tests/__init__.py"),
                text: String::new(),
            },
        );
    }
    // A library with no root module, as std, has nothing to star-import.
    let root = if p.modules.iter().any(|m| m.path.is_empty()) {
        format!("from .{package} import *\n")
    } else {
        String::new()
    };
    let init = format!("{}{root}", inits.concat());
    let meta = p.metadata();
    let quoted = temper_tmpl::json_string;
    // A license as Kotlin's, which names none as proprietary.
    let license = meta.get("license").unwrap_or("LicenseRef-Proprietary");
    let authors = meta.get("authors").map_or(String::new(), author);
    // The links, as `[project.urls]`, where pyproject.toml has them.
    let urls: String = [("Homepage", "homepage"), ("Repository", "repository")]
        .iter()
        .filter_map(|(label, key)| {
            meta.get(key)
                .map(|url| format!("{label} = {}\n", quoted(url)))
        })
        .collect();
    let urls = match urls.as_str() {
        "" => String::new(),
        urls => format!("\n[project.urls]\n{urls}"),
    };
    let pyproject = format!(
        r#"[project]
name = "{project}"
version = {version}
description = {description}
readme = {{text = "n/a", content-type = "text/plain"}}
requires-python = ">=3.11"
license = {{ text = {license} }}
authors = [ {authors} ]

dependencies = [
{dependencies}
]
{test_group}{urls}
[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"
"#,
        project = names::project_name(&project),
        version = quoted(&version::respell(p.version_or_default(), Spelling::Python)),
        description = quoted(meta.get("description").unwrap_or("")),
        license = quoted(license),
        dependencies = dependencies(p, false),
        test_group = match dependencies(p, true) {
            lines if lines.is_empty() => String::new(),
            lines => format!("\n[dependency-groups]\ntest = [\n{lines}\n]\n"),
        },
    );
    // Host code's other files, beside the modules, as Kotlin copies them;
    // each `__connected__.py` starts its module's file instead.
    let host = (temper_tmpl::host::files(p, &[".py"]).into_iter())
        .filter(|f| f.path.rsplit('/').next() != Some("__connected__.py"))
        .map(|f| OutFile {
            path: format!("{library}/{package}/{}", f.path),
            text: f.text.clone(),
        });
    Ok([
        OutFile {
            path: format!("{library}/pyproject.toml"),
            text: pyproject,
        },
        OutFile {
            path: format!("{library}/{package}/__init__.py"),
            text: init,
        },
        // PEP 561's marker, so checkers read an installed package's
        // annotations.
        OutFile {
            path: format!("{library}/{package}/py.typed"),
            text: String::new(),
        },
    ]
    .into_iter()
    .chain(sources)
    .chain(host)
    .chain(tests)
    .collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pypi_spellings() {
        let good = [
            ("pyroaring==1.1.0", ("pyroaring", "1.1.0")),
            ("Zope.Interface==5.0", ("Zope.Interface", "5.0")),
            ("a_b-c==1.0.post1", ("a_b-c", "1.0.post1")),
            ("x==2!1.0rc1+local.7", ("x", "2!1.0rc1+local.7")),
            ("x==1.*", ("x", "1.*")),
        ];
        for (text, parts) in good {
            assert_eq!(pypi_dependency(text), Some(parts), "{text}");
        }
        let hostile = [
            "pyroaring",
            "--index-url=https://evil/${GITHUB_TOKEN}/simple==1",
            "-egit+https://evil/x#egg=x==1",
            "x==${TOKEN}",
            "x==1;python_version>'3'",
            "x[extra]==1",
            "x==1 --hash=sha256:0",
            "x==1\n-i https://evil",
            "x===1",
            "x==1==2",
            "x>=1",
            "x-==1",
            "../x==1",
            "x==/tmp/x",
            "x==\"1\"",
            "x @ file:///tmp/x==1",
        ];
        for text in hostile {
            assert_eq!(pypi_dependency(text), None, "{text}");
        }
        assert!(pypi_name("geometry-py"));
        for bad in ["", "-x", "x-", "../x", "/abs", "a/b", "a b", "<x>", "a\"b"] {
            assert!(!pypi_name(bad), "{bad}");
        }
    }
}
