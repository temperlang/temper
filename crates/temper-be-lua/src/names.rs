//! Lua's `NamingRules`, for the first variant (notes/tmpl/names.md,
//! "`NamingRules`"): names keep their case, as Kotlin Temper's Lua backend
//! keeps them (`halfAndHalf`); Lua's keywords and the names the translator
//! prints take a trailing `_`, as Kotlin's keywords do; characters Lua
//! doesn't allow in a name (anything but ASCII letters, digits, and `_`)
//! become `_` and their hex code; and locals are block-scoped without
//! shadowing, as `local` is block-scoped. Lua's standard globals take a
//! `_` too ([`GLOBALS`]), where Kotlin's keep their names: a module's
//! `local string` would hide the `string` library from the rest of the
//! module, host code included, and a function's `local type` from the
//! rest of the function.

use temper_tmpl::DeclKind;
use temper_tmpl::naming::{Case, NamingRules};

/// Lua 5.4's reserved words. Kotlin's list leaves out `goto` (reserved
/// since Lua 5.2), so a Temper name `goto` breaks its output; this
/// reserves it.
pub const KEYWORDS: &[&str] = &[
    "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if", "in",
    "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while",
];

/// Names with a special meaning to Lua: a local `_ENV` would change what
/// every global name refers to, and `_G` is the globals' table.
pub const SPECIAL: &[&str] = &["_ENV", "_G"];

/// Lua 5.4's standard globals, those of 5.1 and 5.2 that 5.4 dropped
/// (`unpack`, `setfenv`, `bit32`, …), LuaJIT's (`bit`, `jit`, and `ffi`,
/// which its users require under that name): what host code and the
/// translator's output may reach (`tostring`, `require`, `math.huge`).
/// Not the standalone interpreter's `arg`, which a library has no use
/// for. Members keep these names, as table fields hide nothing, and so
/// do exports as their module's table keys them
/// ([`NameMap::public`](temper_tmpl::naming::NameMap::public)).
pub const GLOBALS: &[&str] = &[
    "_VERSION",
    "assert",
    "bit",
    "bit32",
    "collectgarbage",
    "coroutine",
    "debug",
    "dofile",
    "error",
    "ffi",
    "gcinfo",
    "getfenv",
    "getmetatable",
    "io",
    "ipairs",
    "jit",
    "load",
    "loadfile",
    "loadstring",
    "math",
    "module",
    "newproxy",
    "next",
    "os",
    "package",
    "pairs",
    "pcall",
    "print",
    "rawequal",
    "rawget",
    "rawlen",
    "rawset",
    "require",
    "select",
    "setfenv",
    "setmetatable",
    "string",
    "table",
    "tonumber",
    "tostring",
    "type",
    "unpack",
    "utf8",
    "warn",
    "xpcall",
];

/// The other names the translator prints, and the locals it declares at a
/// module's top level: the runtime (`temper`), the module's live exports
/// (`exports`), the helper that gives a class's instances a `__tostring`
/// (`with_tostring`), and host code's table (`_connected`); in a tests
/// file, the tested module's table for its tests (`tested`) and LuaUnit
/// (`luaunit`); and a member's object (`this`). The table that holds
/// module-level declarations past Lua's limit on locals, `module`, is
/// among [`GLOBALS`].
pub const PRINTED: &[&str] = &[
    "_connected",
    "exports",
    "luaunit",
    "temper",
    "tested",
    "this",
    "with_tostring",
];

fn case(_: DeclKind, _exported: bool) -> Case {
    Case::Keep
}

/// Lua's names: ASCII letters, digits, and `_`, not starting with a digit.
/// Lua 5.4 reads no other characters in a name (`LUA_UCID` is off by
/// default), nor does LuaJIT.
fn identifier(c: char, first: bool) -> bool {
    c == '_' || c.is_ascii_alphabetic() || (!first && c.is_ascii_digit())
}

/// The first variant's rules.
pub const RULES: NamingRules = NamingRules {
    reserved: &[KEYWORDS, SPECIAL, GLOBALS, PRINTED],
    member_reserved: &[KEYWORDS],
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
    init_literals_avoid_module: false,
    members_prefixed: false,
    fields_apart: false,
    types_apart: false,
    modules_share_names: false,
    failure_suffix: None,
    // A failure's cause is its key `cause`, so a failure type's own
    // member named so is `cause_`.
    failure_member_reserved: &["cause"],
};
