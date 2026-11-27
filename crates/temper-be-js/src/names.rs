//! JS's `NamingRules`, for the first variant (notes/tmpl/names.md,
//! "`NamingRules`"): names keep their case, as Kotlin Temper's JS backend
//! keeps them (`halfAndHalf`); reserved words and the names the
//! translator prints take a trailing `_`; and locals are block-scoped
//! without shadowing, as `let` and `const` need.

use temper_tmpl::DeclKind;
use temper_tmpl::naming::{Case, NamingRules};

/// Reserved words, as Kotlin's `jsReservedWords`: keywords, `enum`, and
/// the literals.
pub const KEYWORDS: &[&str] = &[
    "await",
    "break",
    "case",
    "catch",
    "const",
    "continue",
    "debugger",
    "default",
    "delete",
    "do",
    "else",
    "enum",
    "export",
    "extends",
    "false",
    "finally",
    "for",
    "function",
    "if",
    "import",
    "in",
    "instanceof",
    "new",
    "null",
    "return",
    "super",
    "switch",
    "this",
    "throw",
    "true",
    "try",
    "typeof",
    "var",
    "void",
    "while",
    "with",
    "yield",
];

/// Names with special meanings in some places, or in strict mode, or in
/// the type annotations proposal, which Kotlin's `jsReservedWordsAndNames`
/// also avoids.
pub const SPECIAL: &[&str] = &[
    "abstract",
    "arguments",
    "as",
    "caller",
    "class",
    "constructor",
    "declare",
    "eval",
    "from",
    "get",
    "globalThis",
    "implements",
    "interface",
    "let",
    "of",
    "override",
    "package",
    "private",
    "protected",
    "public",
    "readonly",
    "set",
    "static",
    "type",
];

/// The globals the translator prints, and the `@temperlang/core` names it
/// may import.
pub const PRINTED: &[&str] = &[
    "Array",
    "BigInt",
    "Error",
    "Infinity",
    "Map",
    "Math",
    "NaN",
    "Number",
    "Object",
    "PromiseBuilder",
    "Reflect",
    "String",
    "adaptAwaiter",
    "bitwiseShl64",
    "bitwiseShr64",
    "bitwiseShrUnsigned32",
    "bitwiseShrUnsigned64",
    "clampInt64",
    "cmpFloat64",
    "cmpInt32",
    "cmpInt64",
    "cmpString",
    "console",
    "dateConstructor",
    "dateFromIsoString",
    "dateToday",
    "dateYearsBetween",
    "denseBitVectorConstructor",
    "denseBitVectorGet",
    "denseBitVectorSet",
    "dequeAdd",
    "dequeConstructor",
    "dequeGetIsEmpty",
    "dequeRemoveFirst",
    "divInt64",
    "divIntInt",
    "empty",
    "endTest",
    "eqFloat64",
    "failureBase",
    "failureCause",
    "float64Near",
    "float64ToInt32",
    "float64ToInt32Unsafe",
    "float64ToInt64",
    "float64ToInt64Unsafe",
    "float64ToString",
    "int64Max",
    "int64Min",
    "int64ToFloat64",
    "int64ToFloat64Unsafe",
    "int64ToInt32",
    "int64ToInt32Unsafe",
    "listBuilderAdd",
    "listBuilderAddAll",
    "listBuilderClear",
    "listBuilderRemoveLast",
    "listBuilderReverse",
    "listBuilderSet",
    "listBuilderSplice",
    "listBuilderToList",
    "listedFilter",
    "listedGet",
    "listedGetOr",
    "listedJoin",
    "listedMap",
    "listedReduce",
    "listedReduceFrom",
    "listedSlice",
    "listedSorted",
    "listedToList",
    "mapBuilderRemove",
    "mapConstructor",
    "mappedForEach",
    "mappedGet",
    "mappedGetOr",
    "mappedToList",
    "mappedToListWith",
    "mappedToMap",
    "modInt64",
    "modIntInt",
    "netResponseGetBodyContent",
    "netResponseGetContentType",
    "netResponseGetStatus",
    "pairConstructor",
    "panic",
    "regexCompiledFind",
    "regexCompiledFound",
    "regexCompiledReplace",
    "regexCompiledSplit",
    "regexFormatterAdjustCodeSet",
    "regexFormatterPushCodeTo",
    "regexFormatterRegexCompileFormatted",
    "requireInstanceOf",
    "requireNoStringIndex",
    "requireNotNull",
    "requireStringIndex",
    "runAsync",
    "sendRequest",
    "stringBuilderAppendCodePoint",
    "stringCountBetween",
    "stringFromCodePoint",
    "stringFromCodePoints",
    "stringGet",
    "stringHasAtLeast",
    "stringNext",
    "stringPrev",
    "stringSplit",
    "stringStep",
    "stringToFloat64",
    "stringToInt32",
    "stringToInt64",
    "undefined",
    "withCause",
];

fn case(_: DeclKind, _exported: bool) -> Case {
    Case::Keep
}

fn identifier(c: char, first: bool) -> bool {
    c == '_' || c == '$' || c.is_alphabetic() || (!first && c.is_alphanumeric())
}

/// What tests files import from Node's test runner, `node:test`, by
/// name: module-level declarations avoid it, since a tests file imports
/// it beside them, but a parameter or local may take it, since the `it`
/// calls are only at a tests file's top level.
pub const TEST_RUNNER: &[&str] = &["it"];

/// The first variant's rules.
pub const RULES: NamingRules = NamingRules {
    reserved: &[KEYWORDS, SPECIAL, PRINTED, TEST_RUNNER],
    member_reserved: &[KEYWORDS, &["constructor"]],
    local_reserved: Some(&[KEYWORDS, SPECIAL, PRINTED]),
    case,
    private_prefix: "",
    escape_suffix: "_",
    raw_keywords: &[],
    raw_prefix: "",
    identifier,
    block_scoped: true,
    same_type_shares: false,
    hoists_block_values: false,
    init_literals_avoid_module: false,
    members_prefixed: false,
    fields_apart: false,
    types_apart: false,
    modules_share_names: false,
    failure_suffix: None,
    // `Error`'s own properties, which mean something else there: a
    // failure's field `name` is `name_`, as `name` is its type's name.
    failure_member_reserved: &["cause", "name", "stack"],
};

/// A module's file name, without `.js`: the library's name with `_` for
/// `-` (`hello-world` gives `hello_world`), as Kotlin's.
pub fn module_file(name: &str) -> String {
    name.replace('-', "_")
}
