//! Temper's C++ backend: tmpl in, C++ out.
//!
//! Works from tmpl alone (notes/tmpl/overview.md, "Boundaries"). Its
//! variants share the translation and differ only in data
//! (notes/tmpl/profiles.md, "Variants and the first profiles"); the first,
//! [`CPP`], is Kotlin-compatible: the same layout (`LIBRARY/init.hpp` and
//! `LIBRARY/init.cpp` for the library's root module, and
//! `LIBRARY/main.cpp`, which runs it), the same namespace (the library's
//! name, [`names::namespace`]), names, init function
//! (`global_init_init()`, which entry points call first, so a program
//! needn't), storage (`temper_globals` and `temper_statics`), and
//! accessors (`x()`, `Type::x()`),
//! the `temper-core` runtime, included as `<temper-core/core.hpp>` from
//! the directory above the library's, and C++14, as Kotlin Temper's `cpp`
//! backend, with prettier bodies: no numbered names, the source's names
//! for parameters and locals, a `core` alias for `temper::core`, natural
//! control flow without Kotlin's extra blocks and labels, and blank lines
//! between functions. Kotlin writes no build files, building a library
//! with `g++ -std=c++14 -I..` on its `.cpp` files and its dependencies';
//! this also writes a CMake project per library ([`cmake_lists`]).
//!
//! Where Kotlin's isn't safe, it differs ([`translate`]): its module
//! `var`s that functions write are read and written under a lock, and
//! their accessors give copies, it keeps Temper's
//! left-to-right order of evaluation where the order would show, and its
//! shifts are defined for every count. It also declares only exports in
//! the header, and the private functions its templates call, where
//! Kotlin's fills it with every function; it names a module's private
//! declarations apart from other modules', which share the library's
//! namespace (`Engine2`), where Kotlin's redefines one; and it keeps
//! objects alive while they're used where Kotlin's can refer to one a
//! call replaces ([`translate`]'s classes). It compiles
//! without warnings under `-Wall -Wextra -Wpedantic` and more (its tests
//! list them), and runs clean under AddressSanitizer,
//! UndefinedBehaviorSanitizer, and ThreadSanitizer.
//!
//! The pipeline: tmpl's lowerings, the program partitioned by reach
//! ([`temper_tmpl::reach::partition`]: what only tests reach apart, what
//! nothing does left out), the naming pass
//! ([`temper_tmpl::naming::assign`]) with
//! C++'s rules ([`names::RULES`]), then [`translate`] into C++'s output
//! tree ([`tree`]), with operators spelled by [`ops`], core's connected
//! declarations by [`connected`], and the order of evaluation kept by
//! [`effects`], then the printer.
//!
//! It covers libraries of several modules, imports between them and
//! from other libraries, and tests; classes and interfaces, as Kotlin's structs, with their
//! fields, constructors (`make`), methods, getters and setters, statics,
//! conversions, `is`, and identity; nullable types (`NullableParam`, or a
//! null `shared_ptr`), null checks, `??`, and `orelse`, `bubble()`, and
//! `panic()` (`try`/`catch` of the runtime's `TemperBubble`); and
//! generics, as templates, defined in the header where others may
//! instantiate them, called with their type arguments, which C++ can't
//! always deduce (`first<core::Str>(…, TEMPER_STR("z"))`), and core's lists, maps,
//! and pairs as the runtime's (`std::shared_ptr<std::vector<T>>`,
//! `Mapped::Ordered`, `Pair`), its function templates also given their
//! type arguments; anything else is [`NotYet`]. No source maps yet.
//!
//! Strings are the runtime's `temper::core::Str` (`str.hpp`, Temper
//! #654): immutable UTF-8 text behind a `std::shared_ptr<std::string
//! const>`, so a copy bumps a count, passed by `const &` where never
//! assigned, as a `shared_ptr` is. A literal is `TEMPER_STR("…")`, made
//! once, on first use, and the empty string `core::Str()`, as Kotlin's.
//! Where Kotlin's concatenates through the runtime's `cat`, tempest's
//! writes `+` with bare literals, which makes a `std::string` that
//! converts to the `Str` it's kept as, and is wrapped as `core::Str(…)`
//! where C++ would otherwise deduce `std::string` or find a choice of the
//! two ambiguous ([`tree::Expr::str_value`]).

pub mod connected;
pub mod effects;
pub mod names;
pub mod ops;
pub mod runtime;
// Not in wasm, which runs no tools.
#[cfg(any(unix, windows))]
pub mod tools;
pub mod translate;
pub mod tree;

use std::collections::{HashMap, HashSet};
use std::fmt;
use temper_tmpl::analysis;
use temper_tmpl::coroutines::CoroutineStrategy;
use temper_tmpl::naming::{NameMap, NamingRules, assign, assign_checked};
use temper_tmpl::version::Spelling;
use temper_tmpl::{DeclId, Facts, Features, Node, Profile, Program};

/// A named set of translation choices for C++.
pub struct Variant {
    /// Its name on the command line: `cpp`.
    pub name: &'static str,
    /// What it handles natively, which lowering and validation read.
    pub profile: Profile,
    pub rules: NamingRules,
    /// The C++ standard its output keeps to, as `g++ -std=` names it.
    pub standard: &'static str,
}

/// The first variant: Kotlin-compatible API and names, pretty bodies,
/// C++14, as Kotlin's (`-std=c++14`). Everything is native except
/// statements inside expressions, which the hoisting pass will lower (not
/// built yet: a program that needs it is [`NotYet`]), and left-to-right
/// order of evaluation, which the translator keeps with temporaries.
pub static CPP: Variant = Variant {
    name: "cpp",
    profile: Profile {
        native: Features::SWITCH,
        coroutines: CoroutineStrategy::TranslateToRegularFunction,
    },
    rules: names::RULES,
    standard: "c++14",
};

/// Every variant, by name.
pub const VARIANTS: &[&Variant] = &[&CPP];

/// The names by which a connected declaration's backend list can mean
/// this backend, as Kotlin's `connectedBackendNames`.
pub const NAMES: &[&str] = &["cpp"];

/// The extensions of the host files the backend takes, as Kotlin's
/// (its module and header extensions).
pub const HOST_EXTENSIONS: &[&str] = &[".cpp", ".hpp"];

/// The host code's `.cpp`s, relative to the library's directory, which
/// the library builds with its modules.
fn host_sources(p: &Program) -> Vec<String> {
    (temper_tmpl::host::files(p, &[".cpp"]).into_iter())
        .map(|f| f.path.clone())
        .collect()
}

/// Something the translation doesn't cover yet.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NotYet {
    pub what: String,
}

impl fmt::Display for NotYet {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "not yet in the C++ backend: {}", self.what)
    }
}

impl std::error::Error for NotYet {}

pub use temper_tmpl::OutFile;

/// What a library's config may set for this backend, `export let cpp:
/// CppConfig = { … };`, as Kotlin's `CppConfig`. It has no properties
/// yet, as Kotlin's had none.
pub const CONFIG: temper_tmpl::ConfigType = temper_tmpl::ConfigType {
    key: "cpp",
    class: "CppConfig",
    properties: &[],
    std: &[],
};

/// This backend, as tools list it ([`temper_tmpl::Backend`]).
pub const BACKEND: temper_tmpl::Backend = temper_tmpl::Backend {
    name: "cpp",
    comment: "//",
    runtime: if cfg!(target_family = "wasm") {
        &[]
    } else {
        runtime::FILES
    },
    runtime_version: &runtime::VERSION,
    rules: &CPP.rules,
    translate: |p, _| translate(p, &CPP).map_err(|e| e.to_string()),
    config: &CONFIG,
    default: false,
    #[cfg(any(unix, windows))]
    tools: Some(&tools::TOOLS),
    #[cfg(not(any(unix, windows)))]
    tools: None,
};

/// Translates a library, laid out as Kotlin's C++ backend lays it out:
///
/// ```text
/// mods/init.hpp            the root module's header: exports and the init function
/// mods/init.cpp            its code, in namespace mods
/// mods/helpers.hpp         another module's, by its path, in the same namespace
/// mods/helpers.cpp
/// mods/helpers/deep.hpp
/// mods/helpers/deep.cpp
/// mods/main.cpp            a program that runs every module's init
/// mods/test.cpp            if there are tests, a program that runs them
/// mods/CMakeLists.txt      a CMake project (Kotlin writes none)
/// mods/_connected.hpp      host code, copied as it is, as Kotlin does
/// mods/_connected.cpp
/// ```
///
/// **Host code:** a library's own `.cpp` and `.hpp` files, beside its
/// modules, are copied to the same paths, as Kotlin copies them, its
/// `.cpp`s built with the modules (in [`cmake_lists`]). A connected
/// function that takes host code ([`temper_tmpl::host::uses_host`] for
/// [`NAMES`]) is a wrapper, as Kotlin's: its parameters' defaults, then
/// `return _connected::sum(i, j, bonus);`; a connected class is named in
/// the header (`namespace _connected { struct Counter; }`, `using Counter
/// = _connected::Counter;`), which host code defines. A module that
/// takes either includes its `_connected.hpp` in its `.cpp`; the header
/// also declares `@keep` functions, which host code calls (`work::sumOf3`),
/// and, where `TEMPER_TESTS` is defined, `@keepTest` ones, which tests'
/// host code calls; host code reads unexported classes there too. Needed host code
/// that's missing is [`NotYet`], where Kotlin's fails in `g++`.
///
/// Kotlin also copies the runtime beside the library, as `temper-core/`,
/// which the files include from the directory above; `temper build`
/// does too ([`runtime`]). Kotlin's `main.cpp` runs the tests where there
/// are some, and its modules' `.cpp`s define them always, needing std;
/// here they're defined only for `test.cpp`'s build ([`test_main`]).
pub fn translate(p: &Program, variant: &Variant) -> Result<Vec<OutFile>, NotYet> {
    // Type-only errors run as written, or fail where they are
    // (`temper_tmpl::errors`), by the profile.
    let settled = temper_tmpl::errors::settle(p, &variant.profile);
    let p = settled.as_ref().unwrap_or(p);
    // `Never<T>` as `T`, but where the profile keeps it
    // (`temper_tmpl::nevers`).
    let nevered = temper_tmpl::nevers::lower(p, &variant.profile);
    let p = nevered.as_ref().unwrap_or(p);
    // Generators as state machines (`temper_tmpl::coroutines`).
    let lowered = temper_tmpl::coroutines::lower(p, variant.profile.coroutines)
        .map_err(|what| NotYet { what })?;
    let p = lowered.as_ref().unwrap_or(p);
    // A captured `var` parameter as a local, which may be a cell.
    let copied = temper_tmpl::cells::copy_captured_params(p);
    let p = copied.as_ref().unwrap_or(p);
    // Operands that run before an `orelse` or a block's value, held
    // first, and choices' parts and loops' conditions that need
    // statements, theirs or temporaries for C++'s order of evaluation, as
    // statements (`temper_tmpl::spill`).
    let spilled = {
        let names = assign(p, &variant.rules);
        let effects = effects::Effects::of(p, &names);
        let marked = effects::needing_temporaries(p, &effects, &names);
        temper_tmpl::spill::spill_with(p, &|n| marked.contains(&n))
    };
    let p = spilled.as_ref().unwrap_or(p);
    // What only tests reach apart, under `TEMPER_TESTS`, and what
    // nothing reaches left out (`temper_tmpl::reach`), as the lowerings
    // left it: errors' may drop a call.
    let parted = temper_tmpl::reach::partition(p, connected::connects_std);
    let p = &parted;
    let names = assign_checked(p, &variant.rules).map_err(|what| NotYet { what })?;
    let library = &p.library.name;
    let namespace = names::namespace(library);
    check_modules(p, &names)?;
    let test_names = test_names(p, &names);
    let mutating = translate::mutating_members(p, &names);
    let mut files = Vec::new();
    let mut sources = Vec::new();
    for module in &p.modules {
        let stem = translate::module_stem(p, module.decl);
        let init = translate::init_name(p, module.decl);
        let mut translator = translate::Translator::new(p, &names, module);
        translator.test_names = test_names.clone();
        translator.mutating = mutating.clone();
        let mut translated = translator.module(module)?;
        translated.source.includes = vec![format!("<{library}/{stem}.hpp>")];
        if translated.uses_mutex {
            translated.source.includes.push("<mutex>".into());
        }
        let runtime_includes = std::mem::take(&mut translated.runtime_includes);
        translated.source.includes.extend(runtime_includes);
        // Host code's definitions, which `_connected.hpp` beside the
        // module's directory gives, as Kotlin includes it.
        if translated.host {
            let dir = module.path.rsplit('/').next().unwrap_or_default();
            translated.source.includes.push(match dir {
                "" => "\"_connected.hpp\"".to_string(),
                dir => format!("\"{dir}/_connected.hpp\""),
            });
        }
        translated.source.namespace = namespace.clone();
        translated.source.init = init.clone();
        files.push(OutFile {
            path: format!("{library}/{stem}.hpp"),
            text: header(&namespace, &translated, &init),
        });
        files.push(OutFile {
            path: format!("{library}/{stem}.cpp"),
            text: tree::source(&translated.source),
        });
        sources.push(format!("{stem}.cpp"));
    }
    // Host code, copied beside the modules as Kotlin copies it, its
    // `.cpp`s built with them.
    for f in temper_tmpl::host::files(p, HOST_EXTENSIONS) {
        files.push(OutFile {
            path: format!("{library}/{}", f.path),
            text: f.text.clone(),
        });
    }
    sources.extend(host_sources(p));
    files.push(OutFile {
        path: format!("{library}/main.cpp"),
        text: main(p, &namespace),
    });
    let tests = test_main(p, &namespace, &test_names);
    if let Some(text) = &tests {
        files.push(OutFile {
            path: format!("{library}/test.cpp"),
            text: text.clone(),
        });
    }
    files.push(OutFile {
        path: format!("{library}/CMakeLists.txt"),
        text: cmake_lists_of(p, &sources, tests.is_some()),
    });
    Ok(files)
}

/// What C++ can't tell apart, which the naming pass doesn't see: two
/// modules' init functions of one name (Kotlin names them by their
/// paths' last parts), or two modules exporting one name into the
/// library's namespace. A private name avoids every other module's
/// names ([`names::RULES`]' `modules_share_names`).
fn check_modules(p: &Program, names: &NameMap) -> Result<(), NotYet> {
    use std::collections::HashMap;
    let mut inits: HashMap<String, &str> = HashMap::new();
    let mut exports: HashMap<String, DeclId> = HashMap::new();
    for module in &p.modules {
        let init = translate::init_name(p, module.decl);
        if (init == "global_init_init" && !module.path.is_empty())
            || inits.insert(init.clone(), &module.path).is_some()
        {
            return Err(NotYet {
                what: format!("two modules with one init function, {init}"),
            });
        }
        for d in module_level(p, module) {
            if p.decls.get(d).facts.contains(Facts::EXPORTED) {
                if let Some(other) = exports.insert(names.get(d).to_string(), d) {
                    if other != d {
                        return Err(NotYet {
                            what: format!("two modules exporting {}", names.get(d)),
                        });
                    }
                }
            }
        }
    }
    Ok(())
}

/// A module's functions, module `let`s, classes, and interfaces.
fn module_level(p: &Program, module: &temper_tmpl::Module) -> Vec<DeclId> {
    (module.items.iter())
        .filter_map(|&item| match p.nodes.get(item) {
            Node::Function { decl, .. }
            | Node::BodilessFunction { decl, .. }
            | Node::ModuleLet { decl }
            | Node::Class { decl, .. }
            | Node::Interface { decl, .. } => Some(decl),
            _ => None,
        })
        .collect()
}

/// Each test's function's name, by its declaration: its name's words, as
/// Kotlin's (`twice10` for "twice 10"), ASCII letters and digits only,
/// numbered past any name the library takes.
fn test_names(p: &Program, names: &NameMap) -> HashMap<DeclId, String> {
    let mut taken: HashSet<String> = (p.decls.ids())
        .filter(|&d| p.decls.contains(d))
        .map(|d| names.get(d).to_string())
        .collect();
    for list in names::RULES.reserved {
        taken.extend(list.iter().map(|s| s.to_string()));
    }
    let mut out = HashMap::new();
    for module in &p.modules {
        for &test in &module.tests {
            let Node::Test { decl, name, .. } = p.nodes.get(test) else {
                continue;
            };
            let mut base = String::new();
            let mut upper = false;
            for c in p.strings.get(name).chars() {
                if c.is_ascii_alphanumeric() {
                    if base.is_empty() && c.is_ascii_digit() {
                        base.push_str("test");
                    }
                    base.push(if upper { c.to_ascii_uppercase() } else { c });
                    upper = false;
                } else {
                    upper = !base.is_empty();
                }
            }
            if base.is_empty() {
                base.push_str("test");
            }
            let mut unique = base.clone();
            let mut n = 2;
            while taken.contains(&unique) {
                unique = format!("{base}{n}");
                n += 1;
            }
            taken.insert(unique.clone());
            out.insert(decl, unique);
        }
    }
    out
}

/// The program that runs the tests, `test.cpp`, if there are any: every
/// module's init, then each test with a fresh std `Test`, as Kotlin's
/// `main.cpp` does when there are tests, through the runtime's
/// `run_tests`, which writes JUnit's XML. Built with `TEMPER_TESTS`
/// defined, which the modules' tests need.
pub fn test_main(p: &Program, namespace: &str, names: &HashMap<DeclId, String>) -> Option<String> {
    let mut entries = String::new();
    let mut std_inits: Vec<String> = Vec::new();
    for module in &p.modules {
        for &test in &module.tests {
            let Node::Test { decl, name, .. } = p.nodes.get(test) else {
                continue;
            };
            let function = &names[&decl];
            entries.push_str(&format!(
                "      {{{}, [] {{
        auto test = temper_std::Test::make();
        {namespace}::{function}(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()}};
      }}}},
",
                tree::string_literal(p.strings.get(name))
            ));
        }
        for import in analysis::test_imports(p, module) {
            if let Some(library) = translate::external_library(p, import.from) {
                let init = format!(
                    "{}::{}",
                    names::namespace(&library),
                    translate::init_name(p, import.from)
                );
                if !std_inits.contains(&init) {
                    std_inits.push(init);
                }
            }
        }
    }
    if entries.is_empty() {
        return None;
    }
    let library = &p.library.name;
    let mut out = String::new();
    for module in &p.modules {
        let stem = translate::module_stem(p, module.decl);
        out.push_str(&format!("#include \"{library}/{stem}.hpp\"\n"));
    }
    out.push_str("#include \"temper-core/test_main.hpp\"\n\nint main() {\n");
    for module in &p.modules {
        out.push_str(&format!(
            "  {namespace}::{}();\n",
            translate::init_name(p, module.decl)
        ));
    }
    for init in std_inits {
        out.push_str(&format!("  {init}();\n"));
    }
    out.push_str(&format!(
        "  return temper::core::run_tests({{\n{entries}  }});\n}}\n"
    ));
    Some(out)
}

/// A CMake project for the library, which Kotlin doesn't write: the
/// library from `init.cpp`, a target of its own name, with the directory
/// above as an include directory, where the runtime (`temper-core`, header
/// only) and the libraries it uses are; each library it uses, by the same
/// file in its directory, unless a project above has added it already;
/// and `main.cpp` as a program, `NAME-main`; for std `net`, libcurl,
/// its default client, where CMake finds it. It builds
/// alone (`cmake -S NAME -B build`) or as part of a project that adds the
/// libraries' directories.
pub fn cmake_lists(p: &Program) -> String {
    let mut sources: Vec<String> = (p.modules.iter())
        .map(|m| format!("{}.cpp", translate::module_stem(p, m.decl)))
        .collect();
    sources.extend(host_sources(p));
    let tests = p.modules.iter().any(|m| !m.tests.is_empty());
    cmake_lists_of(p, &sources, tests)
}

/// The libraries the modules use (`production`), or their tests, other
/// than core.
fn used_libraries(p: &Program, tests: bool) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    for module in &p.modules {
        let imports = if tests {
            analysis::test_imports(p, module)
        } else {
            analysis::module_imports(p, module)
        };
        for import in imports {
            if let Some(library) = translate::external_library(p, import.from) {
                if !out.contains(&library) {
                    out.push(library);
                }
            }
        }
    }
    out.sort();
    out
}

/// Whether the library sends through the runtime's network client
/// (`net.hpp`, as std `net` does, [`connected::BODILESS`]).
fn uses_net_client(p: &Program) -> bool {
    (p.modules.iter().flat_map(|m| m.all_items())).any(|item| match p.nodes.get(item) {
        Node::BodilessFunction { decl, .. } => temper_tmpl::qname::std_key(p, decl)
            .and_then(|key| connected::bodiless(&key))
            .is_some_and(|(_, header)| header == "<temper-core/net.hpp>"),
        _ => false,
    })
}

/// The CMake for a library with [`uses_net_client`]: libcurl, the
/// runtime's default client, where CMake finds it. Without it, the rest
/// builds, and requests fail at run time, saying how to plug in a client.
const CURL_CMAKE: &str = "
# std/net's default client is libcurl's, where CMake finds it. Without it,
# requests fail at run time until host code plugs in a client
# (temper::core::net::set_client).
find_package(CURL QUIET)
if(CURL_FOUND)
  target_compile_definitions({library} PUBLIC TEMPER_NET_CURL)
  target_include_directories({library} PUBLIC ${CURL_INCLUDE_DIRS})
  target_link_libraries({library} PUBLIC ${CURL_LIBRARIES})
endif()
";

fn cmake_lists_of(p: &Program, sources: &[String], tests: bool) -> String {
    let library = &p.library.name;
    let curl = match uses_net_client(p) {
        true => CURL_CMAKE.replace("{library}", library),
        false => String::new(),
    };
    let mut uses = String::new();
    let mut links = String::from(" Threads::Threads");
    let used = used_libraries(p, false);
    for name in &used {
        uses.push_str(&format!(
            "if(NOT TARGET {name})\n  add_subdirectory(${{CMAKE_CURRENT_SOURCE_DIR}}/../{name} ${{CMAKE_CURRENT_BINARY_DIR}}/{name})\nendif()\n"
        ));
        links.push_str(&format!(" {name}"));
    }
    let sources = sources.join(" ");
    let mut test = String::new();
    if tests {
        // The tests need std, which tmpl doesn't translate yet, so they
        // build only where it's there, with a CMake project of its own.
        let extra: Vec<String> = (used_libraries(p, true).into_iter())
            .filter(|l| !used.contains(l))
            .collect();
        let found: Vec<String> = (extra.iter())
            .map(|l| format!("EXISTS ${{CMAKE_CURRENT_SOURCE_DIR}}/../{l}/CMakeLists.txt"))
            .collect();
        let condition = match found.is_empty() {
            true => "TRUE".to_string(),
            false => found.join(" AND "),
        };
        let mut adds = String::new();
        let mut test_links = links.clone();
        for name in &extra {
            adds.push_str(&format!(
                "  if(NOT TARGET {name})\n    add_subdirectory(${{CMAKE_CURRENT_SOURCE_DIR}}/../{name} ${{CMAKE_CURRENT_BINARY_DIR}}/{name})\n  endif()\n"
            ));
            test_links.push_str(&format!(" {name}"));
        }
        test = format!(
            "
if({condition})
{adds}  add_executable({library}-test test.cpp {sources})
  target_compile_definitions({library}-test PRIVATE TEMPER_TESTS)
  target_include_directories({library}-test PRIVATE ${{CMAKE_CURRENT_SOURCE_DIR}}/..)
  target_link_libraries({library}-test PRIVATE{test_links})
endif()
"
        );
    }
    // The config's description, which CMake 3.10's `project` takes;
    // Kotlin's names none.
    let description = match p.metadata().get("description") {
        Some(text) => format!(" DESCRIPTION {}", cmake_string(text)),
        None => String::new(),
    };
    // The library's version, where it sets one: its numbers in
    // `project`, which takes only numbers, and the whole of it beside.
    let (version, version_full) = match cmake_version(&p.library.version) {
        Some((numbers, full)) => (
            format!(" VERSION {numbers}"),
            format!("set({library}_VERSION_FULL {})\n", cmake_string(&full)),
        ),
        None => (String::new(), String::new()),
    };
    format!(
        "# Generated by Temper.
cmake_minimum_required(VERSION 3.10)
project({library}{version}{description} LANGUAGES CXX)
{version_full}
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)
{uses}
add_library({library} {sources})
target_include_directories({library} PUBLIC ${{CMAKE_CURRENT_SOURCE_DIR}}/..)
target_link_libraries({library} PUBLIC{links})
{curl}
add_executable({library}-main main.cpp)
target_link_libraries({library}-main PRIVATE {library})
{test}"
    )
}

/// A library's version for CMake, where it sets one: its numbers, as
/// `project(VERSION)` takes them, and the whole version (a dev build's
/// `0.7.0-alpha.0.123` is `0.7.0` and that); `None` for none (`0.0.0`)
/// or one whose numbers CMake can't take.
fn cmake_version(version: &str) -> Option<(String, String)> {
    if matches!(version, "0.0.0" | "") {
        return None;
    }
    let full = temper_tmpl::version::respell(version, Spelling::SemVer);
    let numbers = match temper_tmpl::version::Version::parse(version) {
        Some(v) => v.spell(Spelling::CMake),
        None => version.split(['-', '+']).next().unwrap_or("").to_string(),
    };
    let parts: Vec<&str> = numbers.split('.').collect();
    let numeric = (1..=4).contains(&parts.len())
        && (parts.iter()).all(|n| !n.is_empty() && n.bytes().all(|b| b.is_ascii_digit()));
    numeric.then_some((numbers, full))
}

/// The header: the runtime, then the module's exports and its init
/// function, in its namespace.
fn header(namespace: &str, t: &translate::Translated, init: &str) -> String {
    let mut out = String::from("#pragma once\n\n#include <temper-core/core.hpp>\n");
    for include in &t.header_includes {
        out.push_str(&format!("#include {include}\n"));
    }
    if !t.test_includes.is_empty() {
        out.push_str("#ifdef TEMPER_TESTS\n");
        for include in &t.test_includes {
            out.push_str(&format!("#include {include}\n"));
        }
        out.push_str("#endif\n");
    }
    out.push_str(&format!("\nnamespace {namespace} {{\n\n"));
    // First, since the entry points below, some defined here, call it.
    out.push_str(&format!(
        "// Runs the module's top level, once. Exported functions, constructors,\n\
         // static functions, and accessors run it first, so calling it is\n\
         // optional.\n\
         void {init}();\n\n"
    ));
    if !t.classes.is_empty() {
        out.push_str(&t.classes);
        out.push('\n');
    }
    if !t.exported_variables.is_empty() || !t.template_storage.is_empty() {
        out.push_str(
            "// Storage, which the library reads and its init sets; read values\n\
             // through the accessors below.\n\
             namespace temper_globals {\n",
        );
        for v in &t.exported_variables {
            out.push_str(&format!("extern {} {};\n", v.slot, v.name));
        }
        for v in &t.template_storage {
            out.push_str(&format!("extern {v};\n"));
        }
        out.push_str("}  // namespace temper_globals\n\n");
    }
    for v in &t.exported_variables {
        if v.shared {
            out.push_str(
                "// The module's functions write it, so this reads it under their\n\
                 // lock and gives a copy.\n",
            );
        }
        out.push_str(&format!("{};\n", v.accessor));
    }
    if !t.exported_variables.is_empty() {
        out.push('\n');
    }
    for f in &t.exported_functions {
        out.push_str(&format!("{f};\n"));
    }
    if !t.exported_functions.is_empty() {
        out.push('\n');
    }
    out.push_str(&t.templates);
    // What only tests reach, and the tests.
    let tests = &t.tests;
    if !tests.is_empty() || !t.kept_for_tests.is_empty() || !t.test_prototypes.is_empty() {
        out.push_str("\n#ifdef TEMPER_TESTS\n");
        if !tests.classes.is_empty() {
            out.push_str(&tests.classes);
            out.push('\n');
        }
        if !tests.template_storage.is_empty() {
            out.push_str("namespace temper_globals {\n");
            for v in &tests.template_storage {
                out.push_str(&format!("extern {v};\n"));
            }
            out.push_str("}  // namespace temper_globals\n\n");
        }
        for f in &tests.functions {
            out.push_str(&format!("{f};\n"));
        }
        if !tests.functions.is_empty() {
            out.push('\n');
        }
        out.push_str(&tests.templates);
        for (comment, prototypes) in [
            ("For tests' host code (`@keepTest`).", &t.kept_for_tests),
            ("Tests, which `test.cpp` runs.", &t.test_prototypes),
        ] {
            if !prototypes.is_empty() {
                out.push_str(&format!("// {comment}\n"));
            }
            for f in prototypes {
                out.push_str(&format!("{f};\n"));
            }
        }
        out.push_str("#endif\n");
    }
    while out.ends_with("\n\n") {
        out.pop();
    }
    out.push_str(&format!("\n}}  // namespace {namespace}\n"));
    out
}

/// The program: runs every module's init, in order, as Kotlin's
/// `main.cpp` does.
fn main(p: &Program, namespace: &str) -> String {
    let library = &p.library.name;
    let mut out = String::new();
    for module in &p.modules {
        let stem = translate::module_stem(p, module.decl);
        out.push_str(&format!("#include \"{library}/{stem}.hpp\"\n"));
    }
    out.push_str("\nint main() {\n");
    for module in &p.modules {
        out.push_str(&format!(
            "  {namespace}::{}();\n",
            translate::init_name(p, module.decl)
        ));
    }
    out.push_str("}\n");
    out
}

/// Text as a quoted CMake argument: `\\`, `"`, and `$` escaped, so it's
/// taken as it is.
fn cmake_string(text: &str) -> String {
    let mut out = String::from('"');
    for c in text.chars() {
        if matches!(c, '\\' | '"' | '$') {
            out.push('\\');
        }
        out.push(c);
    }
    out.push('"');
    out
}
