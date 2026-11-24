//! Java's `NamingRules`, for the first variant (notes/tmpl/names.md,
//! "`NamingRules`"): names keep their case, as Kotlin Temper's Java
//! backend keeps them (`halfAndHalf`); reserved words and the names the
//! translator prints take a trailing `_`; and locals are block-scoped
//! without shadowing, as Java requires of a method's locals. Also the
//! package and class names Kotlin derives from a library's name.

use temper_tmpl::DeclKind;
use temper_tmpl::naming::{Case, NamingRules};

/// Reserved words, as Kotlin's `identifierAvoids`: JLS 3.9's reserved
/// and contextual keywords, and the literals.
pub const KEYWORDS: &[&str] = &[
    "_",
    "abstract",
    "assert",
    "boolean",
    "break",
    "byte",
    "case",
    "catch",
    "char",
    "class",
    "const",
    "continue",
    "default",
    "do",
    "double",
    "else",
    "enum",
    "exports",
    "extends",
    "false",
    "final",
    "finally",
    "float",
    "for",
    "goto",
    "if",
    "implements",
    "import",
    "instanceof",
    "int",
    "interface",
    "long",
    "module",
    "native",
    "new",
    "null",
    "open",
    "opens",
    "package",
    "permits",
    "private",
    "protected",
    "provides",
    "public",
    "record",
    "requires",
    "return",
    "sealed",
    "short",
    "static",
    "strictfp",
    "super",
    "switch",
    "synchronized",
    "this",
    "throw",
    "throws",
    "to",
    "transient",
    "transitive",
    "true",
    "try",
    "uses",
    "var",
    "void",
    "volatile",
    "while",
    "with",
    "yield",
];

/// The types the translator names in code: a local of the same name
/// would obscure them (`Math.min` reading a local `Math`).
pub const PRINTED: &[&str] = &[
    "Boolean",
    "Character",
    "Class",
    "Console",
    "Core",
    "Double",
    "Integer",
    "Logger",
    "Long",
    "Math",
    "Object",
    "String",
    "System",
];

fn case(_: DeclKind, _exported: bool) -> Case {
    Case::Keep
}

fn identifier(c: char, first: bool) -> bool {
    c == '_' || c == '$' || c.is_alphabetic() || (!first && c.is_alphanumeric())
}

/// The first variant's rules.
pub const RULES: NamingRules = NamingRules {
    reserved: &[KEYWORDS, PRINTED],
    // A field obscures a type of its name in its class (`Math.min`).
    member_reserved: &[KEYWORDS, PRINTED],
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
    types_apart: true,
    modules_share_names: false,
    // Failure types say `Exception` for `Error` (RFC 164).
    failure_suffix: Some(("Error", "Exception")),
    failure_base: None,
    failure_member_reserved: &[],
};

/// A library's package, as Kotlin's `safeIdentifier` makes it from the
/// library's name: `-` and `#` become `_`, other characters that can't be
/// in a name `$` and their hex code, and a keyword takes `_`
/// (`hello-world` gives `hello_world`).
pub fn package(library: &str) -> String {
    if KEYWORDS.contains(&library) {
        return format!("{library}_");
    }
    let mut out = String::new();
    for (i, c) in library.chars().enumerate() {
        match c {
            '-' | '#' => out.push('_'),
            c if identifier(c, i == 0) => out.push(c),
            c => out.push_str(&format!("${:x}", c as u32)),
        }
    }
    if out.is_empty() { "$".into() } else { out }
}

/// The start of a module's class names, as Kotlin's `temperToJavaClass`
/// makes it from the package's last part: each word between `_`s with
/// its first letter capitalized, and `_` kept only between digits
/// (`hello_world` gives `HelloWorld`, for `HelloWorldGlobal` and
/// `HelloWorldMain`).
pub fn class_base(package: &str) -> String {
    let mut out = String::new();
    for word in package.split('_').filter(|w| !w.is_empty()) {
        let digit = |c: Option<char>| c.is_some_and(|c| c.is_ascii_digit());
        if digit(out.chars().last()) && digit(word.chars().next()) {
            out.push('_');
        }
        let mut chars = word.chars();
        if let Some(first) = chars.next() {
            out.extend(first.to_uppercase());
            out.push_str(chars.as_str());
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn packages_and_classes() {
        assert_eq!(package("hello-world"), "hello_world");
        assert_eq!(package("int"), "int_");
        assert_eq!(package("a.b"), "a$2eb");
        assert_eq!(class_base("hello_world"), "HelloWorld");
        assert_eq!(class_base("loop_reenterable"), "LoopReenterable");
        assert_eq!(class_base("nonascii"), "Nonascii");
        assert_eq!(class_base("v1_2"), "V1_2");
    }

    #[test]
    fn keywords_sorted() {
        let mut sorted = KEYWORDS.to_vec();
        sorted.sort();
        assert_eq!(KEYWORDS, sorted);
    }
}
