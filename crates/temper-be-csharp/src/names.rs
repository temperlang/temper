//! C#'s `NamingRules`, for the first variant (notes/tmpl/names.md,
//! "`NamingRules`"): functions and exported values in Pascal case, as
//! Kotlin Temper's C# backend names them (`Fib`, `Greeting`), other names
//! as the source has them; C#'s reserved words escaped as verbatim
//! identifiers (`@class`), which keeps a parameter's name exactly the
//! source's, since callers may pass arguments by name; and locals
//! block-scoped without shadowing, as C# requires of a method's locals.
//! Also the root namespace Kotlin derives from a library's name.
//!
//! The types the translator names (`Core`, `Math`, `CultureInfo`, …)
//! aren't reserved, since that would rename exports: where a name in the
//! program would hide one, the translator qualifies it from `global::`.

use temper_tmpl::DeclKind;
use temper_tmpl::naming::{Case, NamingRules};

/// C#'s reserved keywords (C# 10, ECMA-334 6.4.4), which a name takes
/// only as a verbatim identifier, `@` first. Contextual keywords (`var`,
/// `value`, `async`, …) are names like any other where Temper's names go.
pub const KEYWORDS: &[&str] = &[
    "abstract",
    "as",
    "base",
    "bool",
    "break",
    "byte",
    "case",
    "catch",
    "char",
    "checked",
    "class",
    "const",
    "continue",
    "decimal",
    "default",
    "delegate",
    "do",
    "double",
    "else",
    "enum",
    "event",
    "explicit",
    "extern",
    "false",
    "finally",
    "fixed",
    "float",
    "for",
    "foreach",
    "goto",
    "if",
    "implicit",
    "in",
    "int",
    "interface",
    "internal",
    "is",
    "lock",
    "long",
    "namespace",
    "new",
    "null",
    "object",
    "operator",
    "out",
    "override",
    "params",
    "private",
    "protected",
    "public",
    "readonly",
    "ref",
    "return",
    "sbyte",
    "sealed",
    "short",
    "sizeof",
    "stackalloc",
    "static",
    "string",
    "struct",
    "switch",
    "this",
    "throw",
    "true",
    "try",
    "typeof",
    "uint",
    "ulong",
    "unchecked",
    "unsafe",
    "ushort",
    "using",
    "virtual",
    "void",
    "volatile",
    "while",
];

/// Pascal case for functions, exported module values, classes,
/// interfaces, and members but instance fields, as Kotlin's
/// (`PrettyPascal`), with its word breaks ([`temper_tmpl::naming::pascal_case`]);
/// the rest keep the source's case (`PrettyCamel`), instance fields too,
/// which are private: a property in Pascal case gives one to others
/// (`x` and `X`).
fn case(kind: DeclKind, exported: bool) -> Case {
    match kind {
        DeclKind::Function
        | DeclKind::Class
        | DeclKind::Interface
        | DeclKind::Method
        | DeclKind::Getter
        | DeclKind::Setter
        | DeclKind::StaticField => Case::Pascal,
        DeclKind::ModuleLet if exported => Case::Pascal,
        _ => Case::Keep,
    }
}

fn identifier(c: char, first: bool) -> bool {
    c == '_' || c.is_alphabetic() || (!first && c.is_alphanumeric())
}

/// The first variant's rules.
pub const RULES: NamingRules = NamingRules {
    reserved: &[],
    member_reserved: &[],
    local_reserved: None,
    case,
    private_prefix: "",
    escape_suffix: "_",
    raw_keywords: KEYWORDS,
    raw_prefix: "@",
    identifier,
    block_scoped: true,
    same_type_shares: false,
    hoists_block_values: false,
    init_literals_avoid_module: false,
    members_prefixed: false,
    fields_apart: false,
    types_apart: true,
    modules_share_names: false,
    // Failure types say `Exception` for `Error` (RFC 164).
    failure_suffix: Some(("Error", "Exception")),
    failure_base: None,
    failure_member_reserved: &[],
};

/// A library's root namespace, as Kotlin's `dashToPascal` makes it from
/// a library's name in lowercase words: the words between `-`s and `_`s,
/// each lowercased but for its first letter, joined (`hello-world` gives
/// `HelloWorld`, for `HelloWorld.HelloWorldGlobal`). A digit first takes
/// `_` before it, and other characters that can't be in a name become `_`
/// and their hex code.
pub fn root_namespace(library: &str) -> String {
    let mut out = String::new();
    for word in library.split(['-', '_']) {
        let lower = word.to_lowercase();
        let mut chars = lower.chars();
        if let Some(first) = chars.next() {
            out.extend(first.to_uppercase());
            out.push_str(chars.as_str());
        }
    }
    let mut safe = String::new();
    for (i, c) in out.chars().enumerate() {
        if identifier(c, i == 0) {
            safe.push(c);
        } else if i == 0 && c.is_numeric() {
            safe.push('_');
            safe.push(c);
        } else {
            safe.push_str(&format!("_{:x}", c as u32));
        }
    }
    if safe.is_empty() { "_".into() } else { safe }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn root_namespaces() {
        assert_eq!(root_namespace("hello-world"), "HelloWorld");
        assert_eq!(root_namespace("loop-reenterable"), "LoopReenterable");
        assert_eq!(root_namespace("nonascii"), "Nonascii");
        assert_eq!(root_namespace("is-empty"), "IsEmpty");
        assert_eq!(root_namespace("HTML-parser"), "HtmlParser");
        assert_eq!(root_namespace("module-state"), "ModuleState");
        assert_eq!(root_namespace("2d"), "_2d");
        assert_eq!(root_namespace("a.b"), "A_2eb");
    }

    #[test]
    fn keywords_sorted() {
        let mut sorted = KEYWORDS.to_vec();
        sorted.sort();
        assert_eq!(KEYWORDS, sorted);
    }
}
