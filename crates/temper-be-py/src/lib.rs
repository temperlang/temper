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
//! The pipeline: the naming pass ([`temper_tmpl::naming::assign`]) with
//! Python's rules ([`names::RULES`]), then [`translate`] into Python's
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
    default: true,
    #[cfg(any(unix, windows))]
    tools: Some(&tools::TOOLS),
    #[cfg(not(any(unix, windows)))]
    tools: None,
};

/// `pyproject.toml`'s dependencies, as Kotlin writes them: the runtime
/// and each library used, by its name, with `==` its version where it
/// sets one, in PEP 440's spelling, a quoted line each, sorted.
fn dependencies(p: &Program) -> String {
    let runtime = version::current().spell(Spelling::Python);
    let mut lines = vec![format!("temper-core=={runtime}")];
    for (name, version) in p.dependencies() {
        let name = translate::library_name(p, name);
        let name = name.as_str();
        lines.push(match version {
            "0.0.0" | "" => name.to_string(),
            v => format!("{name}=={}", version::respell(v, Spelling::Python)),
        });
    }
    lines.sort();
    let quoted: Vec<String> = lines.iter().map(|l| format!("    \"{l}\"")).collect();
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
/// mods/mods/mods.py                the library's root module
/// mods/mods/helpers/__init__.py    a module with modules under it
/// mods/mods/helpers/deep.py        one without
/// mods/tests/__init__.py           empty
/// mods/tests/test_mods.py          the root module's tests
/// mods/tests/test_helpers_deep.py  another's
/// ```
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
    let names = assign_checked(p, &variant.rules).map_err(|what| NotYet { what })?;
    // std's is `temper-std`, its package `temper_std`, as Kotlin's; a
    // config's `pyName` names the project and its package, but not its
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
version = "{version}"
description = {description}
readme = {{text = "n/a", content-type = "text/plain"}}
requires-python = ">=3.11"
license = {{ text = {license} }}
authors = [ {authors} ]

dependencies = [
{dependencies}
]
{urls}
[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"
"#,
        project = names::project_name(&project),
        version = version::respell(p.version_or_default(), Spelling::Python),
        description = quoted(meta.get("description").unwrap_or("")),
        license = quoted(license),
        dependencies = dependencies(p),
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
    ]
    .into_iter()
    .chain(sources)
    .chain(host)
    .chain(tests)
    .collect())
}
