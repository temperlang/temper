//! tmpl, Temper's backend IR: a settled, typed, fully explicit program
//! that a backend can translate without asking anything of the frontend
//! (notes/tmpl/README.md).
//!
//! This crate depends only on `temper-os`, for the processes its tools
//! start, and never lists backends:
//!
//! - [`Backend`], what tools need of a backend, and [`OutFile`]; with
//!   [`Tools`], how a backend's language runs tests, natively, which
//!   [`tools`]'s helpers (tool lookup, commands, and JUnit XML) serve;
//! - [`Program`], with its tables: [`Nodes`], [`TypeTable`],
//!   [`DeclTable`], and [`Strings`];
//! - [`schema`], one table declaring every node kind, from which
//!   [`Kind`] and [`Node`] are generated;
//! - [`Builder`], for constructing programs;
//! - [`text`], the canonical text form, which reads back exactly;
//! - [`validate()`](mod@validate), the validator's rules, each problem
//!   with its code;
//! - [`ops`], the operator table;
//! - [`qname`], qualified names in Kotlin's format, and how connected
//!   declarations resolve;
//! - [`naming`], the naming pass, which gives each declaration a pretty
//!   output name by a target's [`naming::NamingRules`];
//! - [`analysis`], analyses that passes and backends share;
//! - [`reach`], which declarations the library needs, which only its
//!   tests do, and which nothing does, and each module split by it;
//! - [`host`], a library's host code, and which connected declarations
//!   each backend takes from it;
//! - [`source_map`], Source Map v3 files from spans, for backends;
//! - shared lowerings that backends pick by their profiles:
//!   [`coroutines`], [`errors`], [`nevers`], [`spill`], and [`switches`];
//! - [`version`], Temper's version and its spelling in each ecosystem.

pub mod analysis;
mod backend;
pub mod builder;
pub mod cells;
#[cfg(any(unix, windows))]
mod children;
pub mod coroutines;
mod decls;
pub mod errors;
pub mod failures;
pub mod host;
mod ids;
pub mod line_directives;
pub mod naming;
pub mod nevers;
mod nodes;
pub mod ops;
mod profile;
pub mod qname;
pub mod reach;
mod same;
pub mod schema;
pub mod source_map;
pub mod spill;
pub mod switches;
pub mod text;
// Not in wasm, which runs no tools.
#[cfg(any(unix, windows))]
pub mod tools;
mod types;
pub mod validate;
pub mod version;

pub use backend::{Backend, OutFile, Run, SourceLinks, TestResults, Tested, Tools};
pub use builder::Builder;
pub use decls::{Decl, DeclKind, DeclTable, Facts, Strings};
pub use ids::{DeclId, FileId, NodeId, SourceFile, Span, StrId, TypeId};
pub use nodes::{Flags, Kids, Nodes};
pub use profile::{Features, Profile};
pub use qname::{QName, QNames};
pub use same::difference;
pub use schema::{Kind, Node};
pub use types::{Scalar, Signature, TypeArgs, TypeData, TypeList, TypeTable};
pub use validate::{Problem, Rule, validate};

/// The library a program is.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Library {
    pub name: String,
    pub version: String,
}

/// A version, or Kotlin's default, `0.0.1`, for `0.0.0` or none, which
/// mean a library sets none.
pub fn version_or_default(version: &str) -> &str {
    match version {
        "0.0.0" | "" => "0.0.1",
        v => v,
    }
}

/// What a library's config file says about it besides its name and
/// version, for package files: the string values it exports, by key, as
/// `license`, `description`, `authors`, `homepage`, and `repository`, and
/// each backend's names for it (`pyName`, `javaPackage`, …). Given by the
/// build; not in tmpl's text, and not compared.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Metadata {
    pub exports: Vec<(String, String)>,
}

impl Metadata {
    /// A value the config sets, if it sets one that isn't blank.
    pub fn get(&self, key: &str) -> Option<&str> {
        (self.exports.iter())
            .find(|(k, v)| k == key && !v.trim().is_empty())
            .map(|(_, v)| v.as_str())
    }
}

/// A string quoted for JSON, which is also a TOML basic string: `"`,
/// `\\`, and control characters escaped.
pub fn json_string(s: &str) -> String {
    let mut out = String::from('"');
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\t' => out.push_str("\\t"),
            '\r' => out.push_str("\\r"),
            c if (c as u32) < 0x20 || c == '\u{7f}' => {
                out.push_str(&format!("\\u{:04x}", c as u32))
            }
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

/// Text escaped for XML's text and attribute values.
pub fn xml_text(s: &str) -> String {
    let mut out = String::new();
    for c in s.chars() {
        match c {
            '&' => out.push_str("&amp;"),
            '<' => out.push_str("&lt;"),
            '>' => out.push_str("&gt;"),
            '"' => out.push_str("&quot;"),
            '\'' => out.push_str("&apos;"),
            c => out.push(c),
        }
    }
    out
}

/// No metadata, for a library whose config the build didn't give.
static NO_METADATA: Metadata = Metadata {
    exports: Vec::new(),
};

/// A library this one uses: its declaration, of kind
/// [`DeclKind::Library`], which owns the external declarations used from
/// it, and its version.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Uses {
    pub decl: DeclId,
    pub version: String,
}

/// One module: its declarations and its init block.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Module {
    pub decl: DeclId,
    /// Its directory relative to the library's root, as `foo/bar`; empty
    /// at the root. Qnames start with it.
    pub path: String,
    /// `Function`, `BodilessFunction`, `ModuleLet`, `Class`, and
    /// `Interface` nodes, in source order: the library's, once
    /// [`reach::partition`] has moved out what only tests reach.
    pub items: Vec<NodeId>,
    /// A `Block`: `InitLet`s and top-level statements, in source order.
    pub init: NodeId,
    /// Items that only tests reach, in source order: empty until
    /// [`reach::partition`] moves them here.
    pub test_items: Vec<NodeId>,
    /// A `Block` of the init statements that set what only tests reach,
    /// in source order, which run after `init` and before the tests;
    /// `None` where there are none.
    pub test_init: Option<NodeId>,
    /// `Test` nodes, in source order, run after init. What a module
    /// imports isn't stored: it's the other modules' declarations its
    /// nodes and types name ([`analysis::imports`]).
    pub tests: Vec<NodeId>,
}

impl Module {
    /// Its items, the library's then the tests'.
    pub fn all_items(&self) -> impl Iterator<Item = NodeId> + '_ {
        self.items.iter().chain(&self.test_items).copied()
    }

    /// Every node that isn't another's child, in writing order: its
    /// items, its init block, its test items and init, and its tests.
    pub fn roots(&self) -> impl Iterator<Item = NodeId> + '_ {
        (self.items.iter().chain([&self.init]))
            .chain(&self.test_items)
            .chain(&self.test_init)
            .chain(&self.tests)
            .copied()
    }

    /// What tests may use beyond the library: its test items and init
    /// block, and its tests.
    pub fn test_roots(&self) -> impl Iterator<Item = NodeId> + '_ {
        (self.test_items.iter())
            .chain(&self.test_init)
            .chain(&self.tests)
            .copied()
    }
}

/// One library's program: modules plus the tables they share.
///
/// Programs compare equal when they mean the same: ids of nodes, types,
/// and strings may differ, but declarations keep theirs.
#[derive(Clone, Debug)]
pub struct Program {
    pub library: Library,
    /// The profile it's lowered for; `None` for starting tmpl.
    pub profile: Option<String>,
    /// The libraries it uses, core among them, with what it uses of each.
    pub uses: Vec<Uses>,
    /// In dependency order, then source order.
    pub modules: Vec<Module>,
    pub nodes: Nodes,
    pub types: TypeTable,
    pub decls: DeclTable,
    pub strings: Strings,
    /// The files that spans point into.
    pub files: Vec<SourceFile>,
    /// Whether nodes and declarations carry spans; `false` for a program
    /// read from text without its spans section.
    pub has_spans: bool,
    /// The library's host code and how to use it, given by the build; not
    /// in tmpl's text, and not compared ([`host`]).
    pub host: host::Host,
    /// Its dependencies' naming, for the backend it's translated for,
    /// given by the build: what the naming pass names their declarations
    /// ([`naming::Interface`]). Not in tmpl's text, and not compared.
    pub interfaces: Vec<naming::Interface>,
    /// What the configs of the library and of each library it uses say,
    /// by library name ([`Metadata`]); given by the build, not in tmpl's
    /// text, and not compared.
    pub metadata: Vec<(String, Metadata)>,
}

impl Program {
    /// The libraries it uses other than the runtime, `temper-core`: each
    /// one's name and version, sorted by name, for package files'
    /// dependencies. A version of `0.0.0` means the library sets none.
    pub fn dependencies(&self) -> Vec<(&str, &str)> {
        let mut found: Vec<(&str, &str)> = (self.uses.iter())
            .map(|u| (self.name(u.decl), u.version.as_str()))
            .filter(|(name, _)| *name != "temper-core")
            .collect();
        found.sort();
        found.dedup();
        found
    }

    /// Where a span starts, as `path:line:column`, both from 1, the column
    /// in UTF-16 units, as source maps count it; just the path without
    /// its file's text; `None` for an unknown span.
    pub fn location(&self, span: Span) -> Option<String> {
        let file = self.files.get(span.file.index())?;
        Some(match file.position(span.start) {
            Some((line, column)) => format!("{}:{}:{}", file.path, line + 1, column + 1),
            None => file.path.clone(),
        })
    }

    /// The library's version, or Kotlin's default, `0.0.1`, where it sets
    /// none (`0.0.0`, as the frontend gives it), for the package files
    /// that always name one.
    pub fn version_or_default(&self) -> &str {
        version_or_default(&self.library.version)
    }

    /// What the library's config says ([`Metadata`]).
    pub fn metadata(&self) -> &Metadata {
        self.metadata_of(&self.library.name)
    }

    /// What a library's config says, this one's or one it uses.
    pub fn metadata_of(&self, library: &str) -> &Metadata {
        (self.metadata.iter())
            .find(|(name, _)| name == library)
            .map_or(&NO_METADATA, |(_, m)| m)
    }

    /// A declaration's name.
    pub fn name(&self, decl: DeclId) -> &str {
        let name = self.decls.get(decl).name;
        if name.is_none() {
            ""
        } else {
            self.strings.get(name)
        }
    }

    /// A function's, method's, or getter's signature.
    pub fn signature(&self, decl: DeclId) -> Option<Signature<'_>> {
        self.types.signature(self.decls.get(decl).ty)
    }

    /// A type as text writes it: a nominal type as its declaration,
    /// `Console#9`.
    pub fn show_type(&self, ty: TypeId) -> String {
        self.types.show_with(ty, &|d| {
            if !self.decls.contains(d) {
                return format!("#{}", d.0);
            }
            let made = self.decls.get(d).facts.contains(Facts::COMPILER_MADE);
            let tilde = if made { "~" } else { "" };
            format!("{tilde}{}#{}", text::name(self.name(d)), d.0)
        })
    }

    /// Whether a call of this method is virtual: exactly when its owner is
    /// an interface.
    pub fn is_virtual(&self, method: DeclId) -> bool {
        let owner = self.decls.get(method).owner;
        self.decls.contains(owner) && self.decls.get(owner).kind == DeclKind::Interface
    }
}

impl PartialEq for Program {
    fn eq(&self, other: &Program) -> bool {
        same::difference(self, other).is_none()
    }
}
