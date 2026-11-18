//! C++'s `NamingRules`, for the first variant (notes/tmpl/names.md,
//! "`NamingRules`"): names as the source has them, as Kotlin Temper's C++
//! backend keeps them (`halfAndHalf`, `sayHello`); C++'s keywords, and
//! the names the translator prints or the runtime's headers define as
//! macros, escaped with `_` after them, as Kotlin's `fixName` escapes
//! keywords (`explicit_`); and locals block-scoped without shadowing, so
//! `-Wshadow` has nothing to say. Unlike Kotlin's, names that aren't API
//! (parameters and locals) are never numbered for being declared twice
//! in a module, and keep their non-ASCII letters, which C++ allows in
//! names (`όνομα`), where Kotlin spells them in hex. Also the library's
//! namespace, as Kotlin derives it from the library's name.

use temper_tmpl::DeclKind;
use temper_tmpl::naming::{Case, NamingRules};

/// C++'s keywords, as Kotlin's `cppKeywords` lists them (cppreference's
/// list, with the alternative tokens and the TS keywords), sorted.
pub const KEYWORDS: &[&str] = &[
    "alignas",
    "alignof",
    "and",
    "and_eq",
    "asm",
    "atomic_cancel",
    "atomic_commit",
    "atomic_noexcept",
    "auto",
    "bitand",
    "bitor",
    "bool",
    "break",
    "case",
    "catch",
    "char",
    "char16_t",
    "char32_t",
    "char8_t",
    "class",
    "co_await",
    "co_return",
    "co_yield",
    "compl",
    "concept",
    "const",
    "const_cast",
    "consteval",
    "constexpr",
    "constinit",
    "continue",
    "contract_assert",
    "decltype",
    "default",
    "delete",
    "do",
    "double",
    "dynamic_cast",
    "else",
    "enum",
    "explicit",
    "export",
    "extern",
    "false",
    "float",
    "for",
    "friend",
    "goto",
    "if",
    "inline",
    "int",
    "long",
    "mutable",
    "namespace",
    "new",
    "noexcept",
    "not",
    "not_eq",
    "nullptr",
    "operator",
    "or",
    "or_eq",
    "private",
    "protected",
    "public",
    "reflexpr",
    "register",
    "reinterpret_cast",
    "requires",
    "return",
    "short",
    "signed",
    "sizeof",
    "static",
    "static_assert",
    "static_cast",
    "struct",
    "switch",
    "synchronized",
    "template",
    "this",
    "thread_local",
    "throw",
    "true",
    "try",
    "typedef",
    "typeid",
    "typename",
    "union",
    "unsigned",
    "using",
    "virtual",
    "void",
    "volatile",
    "wchar_t",
    "while",
    "xor",
    "xor_eq",
];

/// Names the translator prints unqualified, which a declaration of the
/// library's namespace would hide: the namespaces it starts names from,
/// the integer types, the runtime's alias, the module's init function,
/// the storage of module values and statics (`temper_globals` and a
/// class's `temper_statics`, as Kotlin's), and what every class's struct
/// has, its factory `make` and `shared_from_this`. Sorted.
pub const PRINTED: &[&str] = &[
    "core",
    "global_init_init",
    "int32_t",
    "int64_t",
    "make",
    "shared_from_this",
    "std",
    "temper",
    "temper_globals",
    "temper_statics",
];

/// Macros that the runtime's headers bring in (from `<cmath>`, with
/// `_USE_MATH_DEFINES`, `<cstdint>`, `<cstdio>`, `<cerrno>`, and
/// `<cassert>`), which would replace a name. Sorted.
pub const MACROS: &[&str] = &[
    "EOF",
    "HUGE_VAL",
    "INFINITY",
    "INT32_MAX",
    "INT32_MIN",
    "INT64_MAX",
    "INT64_MIN",
    "M_1_PI",
    "M_2_PI",
    "M_2_SQRTPI",
    "M_E",
    "M_LN10",
    "M_LN2",
    "M_LOG10E",
    "M_LOG2E",
    "M_PI",
    "M_PI_2",
    "M_PI_4",
    "M_SQRT1_2",
    "M_SQRT2",
    "NAN",
    "NULL",
    "assert",
    "errno",
    "stderr",
    "stdin",
    "stdout",
];

fn case(_: DeclKind, _exported: bool) -> Case {
    Case::Keep
}

fn identifier(c: char, first: bool) -> bool {
    c == '_' || c.is_alphabetic() || (!first && c.is_alphanumeric())
}

/// The first variant's rules.
pub const RULES: NamingRules = NamingRules {
    reserved: &[KEYWORDS, PRINTED, MACROS],
    // A member hides a name in its member functions (`int32_t`), and
    // every struct has `make` and `shared_from_this`.
    member_reserved: &[KEYWORDS, PRINTED, MACROS],
    local_reserved: None,
    case,
    private_prefix: "",
    escape_suffix: "_",
    raw_keywords: &[],
    raw_prefix: "",
    identifier,
    block_scoped: true,
    same_type_shares: false,
    hoists_block_values: false,
    init_literals_avoid_module: true,
    members_prefixed: false,
    fields_apart: false,
    types_apart: false,
    // Every module's declarations are the library namespace's.
    modules_share_names: true,
};

/// Whether C++ reserves a name for its implementation: one with `__`, or
/// one that starts with `_` and a capital letter.
pub fn reserved_for_implementation(name: &str) -> bool {
    let mut chars = name.chars();
    name.contains("__")
        || (chars.next() == Some('_') && chars.next().is_some_and(char::is_uppercase))
}

/// A library's namespace, as Kotlin's `CppBuilder` makes it from the
/// library's name: each run of characters other than ASCII letters and
/// `_` becomes one `_` (`hello-world` gives `hello_world`), and a name
/// the standard library's namespaces take gets `temper_` before it
/// (`temper_std`), as Kotlin's `safeCppNamespace` does. A keyword, which
/// Kotlin would print as it is, takes `_` after it.
pub fn namespace(library: &str) -> String {
    let mut out = String::new();
    let mut run = false;
    for c in library.chars() {
        if c.is_ascii_alphabetic() || c == '_' {
            out.push(c);
            run = false;
        } else if !run {
            out.push('_');
            run = true;
        }
    }
    if matches!(out.as_str(), "std" | "chrono" | "filesystem") {
        out.insert_str(0, "temper_");
    }
    if KEYWORDS.contains(&out.as_str()) {
        out.push('_');
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn namespaces() {
        assert_eq!(namespace("hello-world"), "hello_world");
        assert_eq!(namespace("loop-reenterable"), "loop_reenterable");
        assert_eq!(namespace("module-state"), "module_state");
        assert_eq!(namespace("std"), "temper_std");
        assert_eq!(namespace("a--b2c"), "a_b_c");
        assert_eq!(namespace("new"), "new_");
    }

    #[test]
    fn reserved() {
        assert!(reserved_for_implementation("a__b"));
        assert!(reserved_for_implementation("_Foo"));
        assert!(!reserved_for_implementation("_foo"));
        assert!(!reserved_for_implementation("foo_"));
    }

    #[test]
    fn lists_sorted() {
        for list in [KEYWORDS, PRINTED, MACROS] {
            let mut sorted = list.to_vec();
            sorted.sort();
            assert_eq!(list, sorted);
        }
    }
}
