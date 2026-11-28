//! Rust's `NamingRules`, for the first variant (notes/tmpl/names.md,
//! "`NamingRules`"): snake_case values and functions, as Kotlin Temper's
//! Rust backend gives them; no prefix on private names, since visibility
//! is `pub(crate)`, not a convention; Rust's keywords as raw identifiers
//! (`r#type`), except the four that can't be raw (`self`, `Self`,
//! `super`, and `crate`), which take a trailing `_`, as do the names the
//! translator prints.

use temper_tmpl::DeclKind;
use temper_tmpl::naming::{Case, NamingRules};

/// Keywords that may be raw identifiers: strict, reserved, and edition
/// 2024's `gen`, so output builds in any edition.
pub const RAW_KEYWORDS: &[&str] = &[
    "abstract", "as", "async", "await", "become", "box", "break", "const", "continue", "do", "dyn",
    "else", "enum", "extern", "false", "final", "fn", "for", "gen", "if", "impl", "in", "let",
    "loop", "macro", "match", "mod", "move", "mut", "override", "priv", "pub", "ref", "return",
    "static", "struct", "trait", "true", "try", "type", "typeof", "unsafe", "unsized", "use",
    "virtual", "where", "while", "yield",
];

/// Keywords that can't be raw identifiers, and `_`.
pub const KEYWORDS: &[&str] = &["Self", "_", "crate", "self", "super"];

/// The names the translator prints where a declaration's name could be
/// in scope: crates, the `use`d names, the prelude's that it writes, and
/// each module's `init` and its `INIT_ONCE` (as `init_once`, since
/// statics are a module `let`'s name in upper case); and for classes,
/// the methods it calls or defines on them (`clone`, `clone_boxed`,
/// `new`), a constructor's `selfish`, and the `builders` module.
pub const PRINTED: &[&str] = &[
    "Arc",
    "Err",
    "None",
    "Ok",
    "OnceLock",
    "Option",
    "PartialEq",
    "Result",
    "RwLock",
    "Send",
    "Some",
    "String",
    "Sync",
    "ToArcString",
    "builders",
    "clone",
    "clone_boxed",
    "core",
    "init",
    "init_once",
    "new",
    "selfish",
    "std",
    "temper_core",
];

/// The methods the translator defines or calls on a class's or an
/// interface's type, which a member of the same name would clash with:
/// the rest of [`PRINTED`] names paths and locals, which a method or
/// field never hides (`core`, `init`, `std`). Sorted.
pub const MEMBER_PRINTED: &[&str] = &["clone", "clone_boxed", "new"];

fn case(kind: DeclKind, _exported: bool) -> Case {
    match kind {
        DeclKind::Class | DeclKind::Interface => Case::Keep,
        _ => Case::Snake,
    }
}

fn identifier(c: char, first: bool) -> bool {
    c == '_' || c.is_alphabetic() || (!first && c.is_alphanumeric())
}

/// The first variant's rules.
pub const RULES: NamingRules = NamingRules {
    reserved: &[KEYWORDS, PRINTED],
    member_reserved: &[KEYWORDS, MEMBER_PRINTED],
    local_reserved: None,
    case,
    private_prefix: "",
    escape_suffix: "_",
    raw_keywords: RAW_KEYWORDS,
    raw_prefix: "r#",
    identifier,
    block_scoped: false,
    same_type_shares: false,
    hoists_block_values: false,
    init_literals_avoid_module: false,
    members_prefixed: false,
    fields_apart: true,
    types_apart: false,
    modules_share_names: false,
    error_suffix: None,
    error_member_reserved: &[],
};

/// The crate name cargo gives a package: dashes become underscores, as
/// Kotlin's `PackageNaming.crateName` (`hello-world` gives
/// `hello_world`).
pub fn crate_name(package: &str) -> String {
    package.replace('-', "_")
}

/// A module `let`'s static, for its output name: in upper case, without
/// a raw identifier's `r#` (`count` gives `COUNT`, `r#type` `TYPE`).
pub fn static_name(name: &str) -> String {
    name.trim_start_matches("r#").to_uppercase()
}
