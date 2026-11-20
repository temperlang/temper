//! Temper's JavaScript backend: tmpl in, JavaScript out.
//!
//! Works from tmpl alone (notes/tmpl/overview.md, "Boundaries"). Its
//! variants share the translation and differ only in data
//! (notes/tmpl/profiles.md, "Variants and the first profiles"); the first,
//! [`JS`], is Kotlin-compatible: the same package and module layout
//! (ES modules, with each module's code in a `.internal.js` file that the
//! public one re-exports from), `@temperlang/core` runtime, and public
//! names as Kotlin Temper's JS backend, with prettier function bodies: no
//! numbered names, template literals, `const` where nothing reassigns,
//! and labels only where a jump needs one. JSDoc annotations type
//! parameters, results, and declarations without a value, so
//! `tsc --checkJs --strict` passes on the output.
//!
//! The pipeline: tmpl's lowerings, the program partitioned by reach
//! ([`temper_tmpl::reach::partition`]: what only tests reach in the
//! tests files, what nothing does left out), the naming pass
//! ([`temper_tmpl::naming::assign`]) with JS's rules ([`names::RULES`]),
//! then [`translate`] into JS's output tree
//! ([`tree`]), with operators spelled by [`ops`] and core's connected
//! declarations by [`connected`], then the printer, which can mark where
//! statements and functions' names come from for a source map
//! ([`translate_with_source_maps`]).
//!
//! It covers libraries of several modules, imports between them and
//! from other libraries, and tests, for Node's test runner; classes and
//! interfaces as Kotlin's JS backend
//! writes them (JS classes over `@temperlang/core`'s `type` mixin, fields
//! as `#name` with getters and setters, and `C.new({ … })` factories),
//! with `is`, `as`, casts, and identity (`translate::classes`); and
//! nullable types (`T | null`, `null`, `== null` checks, `??` for its
//! tmpl form, and `===` or a null-safe helper call for `==` with a side
//! that may be null), `orelse` as `try { … } catch { … }`, `bubble()`,
//! and `panic()`, as Kotlin's, but catching only a bubble: `bubble()`
//! and every builtin that bubbles throw the runtime's `Bubble`, and
//! `catch (e$) { if (!isBubble(e$)) throw e$; … }` rethrows anything
//! else (a panic, a plain `Error`; a host error), as host code
//! (`_connected.js`) does unless it throws a `Bubble` or calls
//! `bubble()`; and generics, with JSDoc's `@template`
//! (`{Named} T` for a bound), and core's lists as arrays (a `List` or
//! `Listed` frozen and `readonly T[]` for TypeScript, cast to `T[]` for
//! the runtime's helpers, where Kotlin's types don't check), maps as
//! `Map`s, and pairs as the runtime's `Pair`. Unlike Kotlin's, `getOr` of
//! a map whose values may be null gives a null value, not the fallback.
//! What tmpl has inside expressions that JS
//! writes as statements (an `orelse`, or a block value) the translator
//! hoists into temporaries before the statement (`t$0`), keeping earlier
//! operands in temporaries first so they still run first, and running
//! the hoisted statements only where the expression would (inside `&&`,
//! `||`, `if` values, and each time round a loop's condition). Anything
//! else is [`NotYet`].

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
use temper_tmpl::source_map::source_map;
use temper_tmpl::version::{self, Spelling};
use temper_tmpl::{DeclId, Facts, Features, Node, Profile, Program};

/// A named set of translation choices for JavaScript.
pub struct Variant {
    /// Its name on the command line: `js`.
    pub name: &'static str,
    /// What it handles natively, which lowering and validation read.
    pub profile: Profile,
    pub rules: NamingRules,
}

/// The first variant: Kotlin-compatible API and names, pretty bodies.
/// Everything is native except statements inside expressions, which the
/// translator hoists itself until the hoisting pass is built.
pub static JS: Variant = Variant {
    name: "js",
    profile: Profile {
        native: Features::RUNS_TYPE_ERRORS.with(Features::NEVER),
        coroutines: CoroutineStrategy::TranslateToGenerator,
    },
    rules: names::RULES,
};

/// Every variant, by name.
pub const VARIANTS: &[&Variant] = &[&JS];

/// Something the translation doesn't cover yet.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NotYet {
    pub what: String,
}

impl fmt::Display for NotYet {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "not yet in the JS backend: {}", self.what)
    }
}

impl std::error::Error for NotYet {}

pub use temper_tmpl::OutFile;

/// This backend, as tools list it ([`temper_tmpl::Backend`]).
pub const BACKEND: temper_tmpl::Backend = temper_tmpl::Backend {
    name: "js",
    comment: "//",
    runtime: if cfg!(target_family = "wasm") {
        &[]
    } else {
        runtime::FILES
    },
    runtime_version: &runtime::VERSION,
    rules: &JS.rules,
    translate: |p, links| generate(p, &JS, links.prefix).map_err(|e| e.to_string()),
    default: true,
    #[cfg(any(unix, windows))]
    tools: Some(&tools::TOOLS),
    #[cfg(not(any(unix, windows)))]
    tools: None,
};

/// Translates a library, laid out as Kotlin's JS backend lays it out:
///
/// ```text
/// hello-world/package.json                ES modules, depending on @temperlang/core
/// hello-world/index.js                    imports each other module, then
///                                         export * from "./hello_world.js";
/// hello-world/hello_world.js              export { … } from "./hello_world.internal.js";
/// hello-world/hello_world.internal.js     the library's root module
/// hello-world/helpers/deep.js             another module, by its path, and
/// hello-world/helpers/deep.internal.js    its code
/// hello-world/test/hello_world.test.js    the root module's tests, for node --test
/// ```
///
/// The package takes the library's name; the root module's files take it
/// with `_` for `-`, and other modules' their paths. A module imports
/// another's exports from its public file. Its tests file, named
/// `.test.js` so that `bun test` and `deno test` find it as Node's
/// runner does, from `package.json`'s glob, has what only its tests
/// reach, before them, and imports from its `.internal.js`, which
/// exports what they use, and a setter, `NAME$set`, for each `var` they
/// assign, so a library that only its tests check doesn't import std's
/// `testing`, as a browser bundle can't. Kotlin also copies
/// `@temperlang/core` beside the package; `temper build` does too
/// ([`runtime`]).
///
/// A library's own `.js` and `.mjs` files, its host code, are copied
/// into the package at their paths under its root, as Kotlin copies them
/// (`work/_connected.js`, `work/_support.js`). A module with a
/// `_connected.js` in its directory exports all its declarations from its
/// `.internal.js`, for host code to import, and imports it as a namespace
/// where it uses it (`import * as _connected from "./_connected.js";`):
/// a connected function that takes host code, as
/// [`temper_tmpl::host::uses_host`] chooses for [`translate::NAMES`], is a
/// wrapper that applies its parameters' defaults and calls host code's
/// function of its source name (`return _connected.sum(i, j, bonus);`),
/// and such a class is a name for host code's (`export const Counter =
/// _connected.Counter;`). Host code that's needed but missing is
/// [`NotYet`], naming the file, where Kotlin's fails as the module loads.
pub fn translate(p: &Program, variant: &Variant) -> Result<Vec<OutFile>, NotYet> {
    generate(p, variant, None)
}

/// [`translate`], with a source map for each `.internal.js` module and
/// tests file, when the program has spans: `hello_world.internal.js.map`
/// after it (`test/hello_world.test.js.map` after its tests), and a
/// `//# sourceMappingURL=` comment ending the file. The map names each
/// source by its path after `sources_prefix`, which leads from the
/// package's directory to the library's root (`../../src/`, say), and
/// leaves out the sources' text. It maps each statement and function, and
/// each function's name, with its source name (notes/tmpl/nodes.md,
/// "Source positions").
pub fn translate_with_source_maps(
    p: &Program,
    variant: &Variant,
    sources_prefix: &str,
) -> Result<Vec<OutFile>, NotYet> {
    generate(p, variant, Some(sources_prefix))
}

fn generate(p: &Program, variant: &Variant, maps: Option<&str>) -> Result<Vec<OutFile>, NotYet> {
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
    let package = &p.library.name;
    let mut translator = translate::Translator::new(p, &names);
    let mut files = Vec::new();
    let mut entries = Vec::new();
    let mut index = String::new();
    let mut tested = false;
    for module in &p.modules {
        let file = translate::module_file(p, module.decl);
        let depth = file.matches('/').count();
        let access = translate::TestAccess::of(p, module);
        let tree = translator.module(module, &access)?;
        // In source order, as Kotlin lists them.
        let mut exported: Vec<DeclId> = module
            .items
            .iter()
            .filter_map(|&item| match p.nodes.get(item) {
                Node::Function { decl, .. }
                | Node::ModuleLet { decl }
                | Node::Class { decl, .. }
                | Node::Interface { decl, .. } => Some(decl),
                _ => None,
            })
            .filter(|&d| p.decls.get(d).facts.contains(Facts::EXPORTED))
            // A std class connected to a JS type (`Date`) isn't declared,
            // nor is a `@fun` interface, which is its function type.
            .filter(|&d| !temper_tmpl::qname::connected_type(p, d, connected::connects_std))
            .filter(|&d| !p.decls.get(d).facts.contains(Facts::FUN))
            .collect();
        exported.sort();
        let exports: Vec<String> = exported.iter().map(|&d| names.get(d).to_string()).collect();
        let leaf = file.rsplit('/').next().unwrap_or(&file);
        let public = public_module(&exports, leaf);
        let internal = format!("{file}.internal.js");
        let internal_leaf = format!("{leaf}.internal.js");
        let mapped = tree::module_mapped(&tree);
        // Sources are in the module's directory under the library's root.
        let prefix = maps.map(|prefix| {
            let mut full = up(depth, prefix);
            if !module.path.is_empty() {
                full.push_str(&module.path);
                full.push('/');
            }
            full
        });
        let map =
            (prefix.as_ref()).and_then(|prefix| source_map(p, &internal_leaf, prefix, &mapped));
        let mut text = mapped.text;
        if map.is_some() {
            text.push_str(&format!("//# sourceMappingURL={internal_leaf}.map\n"));
        }
        files.push(OutFile {
            path: format!("{package}/{file}.js"),
            text: public,
        });
        files.push(OutFile {
            path: format!("{package}/{internal}"),
            text,
        });
        if let Some(map) = map {
            files.push(OutFile {
                path: format!("{package}/{internal}.map"),
                text: map.to_json(),
            });
        }
        if let Some(tests) = translator.tests(module)? {
            tested = true;
            let path = format!("{package}/test/{file}.test.js");
            let mapped = tree::module_mapped(&tests);
            // A directory further from the sources, under `test/`.
            let leaf = format!("{leaf}.test.js");
            let map =
                (prefix.as_ref()).and_then(|prefix| source_map(p, &leaf, &up(1, prefix), &mapped));
            let mut text = mapped.text;
            if map.is_some() {
                text.push_str(&format!("//# sourceMappingURL={leaf}.map\n"));
            }
            files.push(OutFile {
                path: path.clone(),
                text,
            });
            if let Some(map) = map {
                files.push(OutFile {
                    path: format!("{path}.map"),
                    text: map.to_json(),
                });
            }
        }
        entries.push(format!("        \"./{file}\": \"./{file}.js\","));
        if module.path.is_empty() {
            index.push_str(&format!("export * from \"./{file}.js\";\n"));
        } else {
            index.push_str(&format!("import \"./{file}.js\";\n"));
        }
    }
    // Host code, beside the modules, as Kotlin copies it: every `.js` or
    // `.mjs` file under the library's root, at its path there.
    for f in temper_tmpl::host::files(p, &[".js", ".mjs"]) {
        files.push(OutFile {
            path: format!("{package}/{}", f.path),
            text: f.text.clone(),
        });
    }
    // The root module's `export *` goes last.
    let (imports, exports): (Vec<&str>, Vec<&str>) =
        index.lines().partition(|l| l.starts_with("import"));
    let mut index = String::new();
    for line in imports.iter().chain(&exports) {
        index.push_str(line);
        index.push('\n');
    }
    // Node's own test runner, `node:test`, which needs no package and
    // sets no time limit; Kotlin's runs Mocha. A glob, which Node 21 and
    // later expand, since a directory isn't one: tests files only, the
    // `.test.js` files Bun and Deno find too, not host code under `test/`.
    // With source maps on, so failures point at Temper lines; Bun and
    // Deno running the script as `node` take the flag too.
    let tests = if tested {
        r#"
    "scripts": {
        "test": "node --enable-source-maps --test \"test/**/*.test.js\""
    },"#
    } else {
        ""
    };
    // Sorted, as Kotlin's: `@temperlang/core` first. A library only the
    // tests use is a dev dependency.
    let tests_only = temper_tmpl::analysis::test_only_libraries(p);
    let (mut dependencies, mut dev) = (
        vec![format!(
            "        \"@temperlang/core\": \"{}\"",
            version::current().spell(Spelling::SemVer)
        )],
        Vec::new(),
    );
    for (name, version) in p.dependencies() {
        let line = format!(
            "        \"{}\": \"{}\"",
            translate::package_of(p, name),
            match version {
                "0.0.0" => "*".to_string(),
                v => version::respell(v, Spelling::SemVer),
            }
        );
        match tests_only.iter().any(|l| l == name) {
            true => dev.push(line),
            false => dependencies.push(line),
        }
    }
    let dev = match dev.is_empty() {
        true => String::new(),
        false => format!(
            ",\n    \"devDependencies\": {{\n{}\n    }}",
            dev.join(",\n")
        ),
    };
    let package_json = format!(
        r#"{{
    "name": "{name}",
{metadata}    "type": "module",
    "exports": {{
{entries}
        ".": "./index.js"
    }},{tests}
    "dependencies": {{
{dependencies}
    }}{dev}
}}
"#,
        // std's is `@temperlang/std`, as its importers name it.
        name = translate::package_of(p, package),
        metadata = package_metadata(p),
        entries = entries.join("\n"),
        dependencies = dependencies.join(",\n"),
    );
    files.insert(
        0,
        OutFile {
            path: format!("{package}/index.js"),
            text: index,
        },
    );
    files.insert(
        0,
        OutFile {
            path: format!("{package}/package.json"),
            text: package_json,
        },
    );
    Ok(files)
}

/// A source map's `sources` prefix, `prefix`, from a file `depth`
/// directories further down: `../` that many times before a relative
/// one, and a `file:` URL, for sources on another drive, as it is.
fn up(depth: usize, prefix: &str) -> String {
    match prefix.starts_with("file:") {
        true => prefix.to_string(),
        false => "../".repeat(depth) + prefix,
    }
}

/// A module's public file, re-exporting its exports from its
/// `.internal.js`, beside it.
fn public_module(exports: &[String], leaf: &str) -> String {
    if exports.is_empty() {
        return format!("export {{}} from \"./{leaf}.internal.js\";\n");
    }
    let one_line = format!(
        "export {{ {} }} from \"./{leaf}.internal.js\";",
        exports.join(", ")
    );
    if one_line.len() <= tree::WIDTH {
        format!("{one_line}\n")
    } else {
        let list: String = exports.iter().map(|e| ["  ", e, ",\n"].concat()).collect();
        format!("export {{\n{list}}} from \"./{leaf}.internal.js\";\n")
    }
}

/// The library's version, where it sets one, and its config's metadata,
/// as package.json's fields in Kotlin's order, a line each: `version`,
/// `author`, `description`, `homepage`, `license`, and `repository`,
/// as git's, which Kotlin leaves out.
fn package_metadata(p: &Program) -> String {
    let meta = p.metadata();
    let quoted = temper_tmpl::json_string;
    let mut out = String::new();
    if !matches!(p.library.version.as_str(), "0.0.0" | "") {
        out.push_str(&format!(
            "    \"version\": {},\n",
            quoted(&version::respell(&p.library.version, Spelling::SemVer))
        ));
    }
    for (field, key) in [
        ("author", "authors"),
        ("description", "description"),
        ("homepage", "homepage"),
        ("license", "license"),
    ] {
        if let Some(value) = meta.get(key) {
            out.push_str(&format!("    \"{field}\": {},\n", quoted(value)));
        }
    }
    if let Some(url) = meta.get("repository") {
        out.push_str(&format!(
            "    \"repository\": {{ \"type\": \"git\", \"url\": {} }},\n",
            quoted(url)
        ));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::up;

    #[test]
    fn prefixes_from_further_down() {
        assert_eq!(up(0, "../../src/"), "../../src/");
        assert_eq!(up(2, "../../src/"), "../../../../src/");
        // Sources on another drive, as a URL, are where they are.
        assert_eq!(up(2, "file:///D:/w/src/"), "file:///D:/w/src/");
    }
}
