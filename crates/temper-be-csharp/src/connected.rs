//! The connection tables: how core's connected declarations and types are
//! written, by qname, with Kotlin's strings as keys (notes/tmpl/names.md,
//! "Connected declarations"). Each entry gives what Kotlin's C# backend
//! gives for it (its `CSharpSupportNetwork.kt`), or a prettier
//! equivalent, but for culture: C#'s string and number operations follow
//! the current culture unless told otherwise, so every one here is
//! ordinal or invariant. Kotlin's `Int32.toString()` is the runtime's
//! `Core.ConvertToString(n)`, which is `n.ToString()`, whose minus sign is
//! U+2212 in some cultures (`sv-SE`); here it's
//! `n.ToString(CultureInfo.InvariantCulture)`.

use crate::NotYet;
use crate::translate::Translator;
use crate::tree::{Bin, Expr};
use temper_tmpl::{Node, NodeId, TypeData};

/// Custom output: from the call's argument nodes, a method's receiver
/// first, and `Omitted` for an argument left out.
pub type Hook = fn(&mut Translator<'_>, &[NodeId]) -> Result<Expr, NotYet>;

/// How a connected declaration is written.
#[derive(Clone, Copy)]
pub enum Connection {
    /// A static method of a class, given the arguments, a method's
    /// receiver first: `Math.Min(a, b)`.
    Static(&'static str, &'static str),
    /// A method of the receiver, given the rest: `console.Log(s)`.
    Method(&'static str),
    /// Custom output.
    Translate(Hook),
}

/// Sorted by qname.
pub const TABLE: &[(&str, Connection)] = &[
    (
        "core.async()",
        Connection::Static("Async", "LaunchGeneratorAsync"),
    ),
    ("core.empty()", Connection::Static("Core", "Empty")),
    ("core.getConsole()", Connection::Translate(get_console)),
    (
        "core.type Boolean.toString()",
        Connection::Translate(boolean_to_string),
    ),
    (
        "core.type BrokenGenerator.constructor()",
        Connection::Translate(new_broken_generator),
    ),
    (
        "core.type BrokenPromise.constructor()",
        Connection::Translate(new_broken_promise),
    ),
    ("core.type Console.log()", Connection::Method("Log")),
    (
        "core.type DenseBitVector.constructor()",
        Connection::Translate(new_of_type),
    ),
    (
        "core.type DenseBitVector.get()",
        Connection::Static("Core", "BitGet"),
    ),
    (
        "core.type DenseBitVector.set()",
        Connection::Static("Core", "BitSet"),
    ),
    ("core.type Deque.add()", Connection::Method("Enqueue")),
    (
        "core.type Deque.constructor()",
        Connection::Translate(new_of_type),
    ),
    ("core.type Deque.get isEmpty()", Connection::Translate(none)),
    (
        "core.type Deque.removeFirst()",
        Connection::Static("Listed", "RemoveFirst"),
    ),
    // A declared failure's cause is its `InnerException`, where that's a
    // declared failure (`IFailure`), or one it took after it was made
    // (the runtime's `Core.WithCause`); its message, `Message`, which every
    // failure class overrides.
    (
        "core.type Error.get cause()",
        Connection::Static("Core", "FailureCause"),
    ),
    (
        "core.type Error.get message()",
        Connection::Translate(failure_message),
    ),
    ("core.type Float64.abs()", Connection::Static("Math", "Abs")),
    (
        "core.type Float64.acos()",
        Connection::Static("Math", "Acos"),
    ),
    (
        "core.type Float64.asin()",
        Connection::Static("Math", "Asin"),
    ),
    (
        "core.type Float64.atan()",
        Connection::Static("Math", "Atan"),
    ),
    (
        "core.type Float64.atan2()",
        Connection::Static("Math", "Atan2"),
    ),
    (
        "core.type Float64.ceil()",
        Connection::Static("Math", "Ceiling"),
    ),
    ("core.type Float64.cos()", Connection::Static("Math", "Cos")),
    (
        "core.type Float64.cosh()",
        Connection::Static("Math", "Cosh"),
    ),
    ("core.type Float64.e", Connection::Translate(math_e)),
    ("core.type Float64.exp()", Connection::Static("Math", "Exp")),
    (
        "core.type Float64.expm1()",
        Connection::Static("Float64", "ExpM1"),
    ),
    (
        "core.type Float64.floor()",
        Connection::Static("Math", "Floor"),
    ),
    ("core.type Float64.log()", Connection::Static("Math", "Log")),
    (
        "core.type Float64.log10()",
        Connection::Static("Math", "Log10"),
    ),
    (
        "core.type Float64.log1p()",
        Connection::Static("Float64", "LogP1"),
    ),
    ("core.type Float64.max()", Connection::Static("Math", "Max")),
    ("core.type Float64.min()", Connection::Static("Math", "Min")),
    (
        "core.type Float64.near()",
        Connection::Static("Float64", "Near"),
    ),
    ("core.type Float64.pi", Connection::Translate(math_pi)),
    (
        "core.type Float64.round()",
        Connection::Static("Math", "Round"),
    ),
    (
        "core.type Float64.sign()",
        Connection::Static("Float64", "Sign"),
    ),
    ("core.type Float64.sin()", Connection::Static("Math", "Sin")),
    (
        "core.type Float64.sinh()",
        Connection::Static("Math", "Sinh"),
    ),
    (
        "core.type Float64.sqrt()",
        Connection::Static("Math", "Sqrt"),
    ),
    ("core.type Float64.tan()", Connection::Static("Math", "Tan")),
    (
        "core.type Float64.tanh()",
        Connection::Static("Math", "Tanh"),
    ),
    (
        "core.type Float64.toInt32()",
        Connection::Static("Float64", "ToInt"),
    ),
    (
        "core.type Float64.toInt32Unsafe()",
        Connection::Translate(cast_int),
    ),
    (
        "core.type Float64.toInt64()",
        Connection::Static("Float64", "ToInt64"),
    ),
    (
        "core.type Float64.toInt64Unsafe()",
        Connection::Translate(cast_long),
    ),
    (
        "core.type Float64.toString()",
        Connection::Translate(float_to_string),
    ),
    (
        "core.type Generator.next()",
        Connection::Static("Core", "GeneratorNext"),
    ),
    ("core.type Int32.max()", Connection::Static("Math", "Max")),
    ("core.type Int32.min()", Connection::Static("Math", "Min")),
    (
        "core.type Int32.toFloat64()",
        Connection::Translate(cast_double),
    ),
    (
        "core.type Int32.toInt64()",
        Connection::Translate(cast_long),
    ),
    (
        "core.type Int32.toString()",
        Connection::Translate(int_to_string),
    ),
    ("core.type Int64.max()", Connection::Static("Math", "Max")),
    ("core.type Int64.min()", Connection::Static("Math", "Min")),
    (
        "core.type Int64.toFloat64()",
        Connection::Static("Float64", "ToFloat64"),
    ),
    (
        "core.type Int64.toFloat64Unsafe()",
        Connection::Translate(cast_double),
    ),
    (
        "core.type Int64.toInt32()",
        Connection::Static("Core", "ToInt"),
    ),
    (
        "core.type Int64.toInt32Unsafe()",
        Connection::Translate(cast_int),
    ),
    (
        "core.type Int64.toString()",
        Connection::Translate(int_to_string),
    ),
    (
        "core.type InvalidCodePoint.constructor()",
        Connection::Translate(new_invalid_code_point),
    ),
    ("core.type List.get length()", Connection::Translate(count)),
    ("core.type List.get()", Connection::Static("Listed", "Get")),
    ("core.type List.toList()", Connection::Translate(same)),
    (
        "core.type List.toListBuilder()",
        Connection::Translate(new_list),
    ),
    (
        "core.type ListBuilder.add()",
        Connection::Translate(list_add),
    ),
    (
        "core.type ListBuilder.addAll()",
        Connection::Static("Listed", "AddAll"),
    ),
    ("core.type ListBuilder.clear()", Connection::Method("Clear")),
    (
        "core.type ListBuilder.constructor()",
        Connection::Translate(new_list),
    ),
    (
        "core.type ListBuilder.get length()",
        Connection::Translate(count),
    ),
    (
        "core.type ListBuilder.removeLast()",
        Connection::Static("Listed", "RemoveLast"),
    ),
    (
        "core.type ListBuilder.reverse()",
        Connection::Static("Listed", "Reverse"),
    ),
    (
        "core.type ListBuilder.set()",
        Connection::Static("Listed", "Set"),
    ),
    (
        "core.type ListBuilder.sort()",
        Connection::Static("Listed", "Sort"),
    ),
    (
        "core.type ListBuilder.splice()",
        Connection::Static("Listed", "Splice"),
    ),
    (
        "core.type ListBuilder.toList()",
        Connection::Static("Listed", "ToReadOnlyList"),
    ),
    (
        "core.type ListBuilder.toListBuilder()",
        Connection::Translate(new_list),
    ),
    (
        "core.type Listed.filter()",
        Connection::Translate(list_filter),
    ),
    (
        "core.type Listed.get isEmpty()",
        Connection::Translate(none),
    ),
    (
        "core.type Listed.get length()",
        Connection::Translate(count),
    ),
    (
        "core.type Listed.get()",
        Connection::Static("Listed", "Get"),
    ),
    (
        "core.type Listed.getOr()",
        Connection::Translate(listed_get_or),
    ),
    (
        "core.type Listed.join()",
        Connection::Static("Listed", "Join"),
    ),
    ("core.type Listed.map()", Connection::Translate(list_map)),
    (
        "core.type Listed.reduce()",
        Connection::Static("Listed", "Reduce"),
    ),
    (
        "core.type Listed.reduceFrom()",
        Connection::Translate(listed_reduce),
    ),
    (
        "core.type Listed.slice()",
        Connection::Static("Listed", "Slice"),
    ),
    (
        "core.type Listed.sorted()",
        Connection::Static("Listed", "Sorted"),
    ),
    (
        "core.type Listed.toList()",
        Connection::Static("Listed", "ToReadOnlyList"),
    ),
    (
        "core.type Listed.toListBuilder()",
        Connection::Translate(new_list),
    ),
    (
        "core.type Map.constructor()",
        Connection::Static("Mapped", "ConstructMap"),
    ),
    ("core.type MapBuilder.clear()", Connection::Method("Clear")),
    (
        "core.type MapBuilder.constructor()",
        Connection::Translate(new_map_builder),
    ),
    (
        "core.type MapBuilder.remove()",
        Connection::Static("Core", "RemoveGet"),
    ),
    (
        "core.type MapBuilder.set()",
        Connection::Translate(set_index),
    ),
    (
        "core.type Mapped.forEach()",
        Connection::Static("Mapped", "ForEach"),
    ),
    (
        "core.type Mapped.get length()",
        Connection::Translate(count),
    ),
    (
        "core.type Mapped.get()",
        Connection::Static("Mapped", "Get"),
    ),
    (
        "core.type Mapped.getOr()",
        Connection::Translate(mapped_get_or),
    ),
    ("core.type Mapped.has()", Connection::Method("ContainsKey")),
    (
        "core.type Mapped.keys()",
        Connection::Static("Mapped", "Keys"),
    ),
    (
        "core.type Mapped.toList()",
        Connection::Static("Mapped", "ToList"),
    ),
    (
        "core.type Mapped.toListWith()",
        Connection::Static("Mapped", "ToListWith"),
    ),
    (
        "core.type Mapped.toMap()",
        Connection::Static("Mapped", "ToMap"),
    ),
    (
        "core.type Mapped.toMapBuilder()",
        Connection::Static("Mapped", "ToMapBuilder"),
    ),
    (
        "core.type Mapped.values()",
        Connection::Static("Mapped", "Values"),
    ),
    (
        "core.type MissingKey.constructor()",
        Connection::Translate(new_missing_key),
    ),
    (
        "core.type Pair.constructor()",
        Connection::Translate(new_pair),
    ),
    (
        "core.type ParseError.constructor()",
        Connection::Translate(new_parse_error),
    ),
    (
        "core.type PromiseBuilder.breakPromise()",
        Connection::Static("Async", "BreakPromise"),
    ),
    (
        "core.type PromiseBuilder.complete()",
        Connection::Static("Async", "CompletePromise"),
    ),
    (
        "core.type PromiseBuilder.constructor()",
        Connection::Translate(new_of_type),
    ),
    (
        "core.type PromiseBuilder.get promise()",
        Connection::Translate(task),
    ),
    (
        "core.type SafeGenerator.nextSafe()",
        Connection::Static("Core", "GeneratorNext"),
    ),
    ("core.type String.begin", Connection::Translate(zero)),
    (
        "core.type String.countBetween()",
        Connection::Static("StringUtil", "CountBetween"),
    ),
    (
        "core.type String.fromCodePoint()",
        Connection::Static("Core", "StringFromCodePoint"),
    ),
    (
        "core.type String.fromCodePoints()",
        Connection::Static("Core", "StringFromCodePoints"),
    ),
    ("core.type String.get end()", Connection::Translate(length)),
    (
        "core.type String.get isEmpty()",
        Connection::Translate(is_empty),
    ),
    (
        "core.type String.get()",
        Connection::Static("StringUtil", "Get"),
    ),
    (
        "core.type String.hasAtLeast()",
        Connection::Static("StringUtil", "HasAtLeast"),
    ),
    (
        "core.type String.hasIndex()",
        Connection::Static("StringUtil", "HasIndex"),
    ),
    (
        "core.type String.indexOf()",
        Connection::Static("StringUtil", "IndexOf"),
    ),
    (
        "core.type String.next()",
        Connection::Static("StringUtil", "Next"),
    ),
    (
        "core.type String.prev()",
        Connection::Static("StringUtil", "Prev"),
    ),
    (
        "core.type String.slice()",
        Connection::Static("StringUtil", "Slice"),
    ),
    (
        "core.type String.split()",
        Connection::Static("Core", "Split"),
    ),
    (
        "core.type String.step()",
        Connection::Static("StringUtil", "Step"),
    ),
    (
        "core.type String.toFloat64()",
        Connection::Translate(string_to_float),
    ),
    (
        "core.type String.toInt32()",
        Connection::Static("Core", "ToInt"),
    ),
    (
        "core.type String.toInt64()",
        Connection::Static("Core", "ToInt64"),
    ),
    ("core.type String.toString()", Connection::Translate(same)),
    (
        "core.type StringBuilder.append()",
        Connection::Translate(append),
    ),
    (
        "core.type StringBuilder.appendBetween()",
        Connection::Static("StringUtil", "AppendBetween"),
    ),
    (
        "core.type StringBuilder.appendCodePoint()",
        Connection::Static("StringUtil", "AppendCodePoint"),
    ),
    (
        "core.type StringBuilder.clear()",
        Connection::Method("Clear"),
    ),
    (
        "core.type StringBuilder.constructor()",
        Connection::Translate(new_of_type),
    ),
    (
        "core.type StringBuilder.get end()",
        Connection::Translate(length),
    ),
    (
        "core.type StringBuilder.toString()",
        Connection::Method("ToString"),
    ),
    (
        "core.type StringIndex.none",
        Connection::Translate(minus_one),
    ),
    (
        "core.type StringIndexOption.compareTo()",
        Connection::Method("CompareTo"),
    ),
    (
        "core.type StringIndexOption.eq()",
        Connection::Method("Equals"),
    ),
    (
        "std/net.sendRequest()",
        Connection::Static("NetSupport", "StdNetSend"),
    ),
    (
        "std/net.type NetResponse.get bodyContent()",
        Connection::Translate(net_body_content),
    ),
    (
        "std/net.type NetResponse.get contentType()",
        Connection::Translate(net_content_type),
    ),
    (
        "std/net.type NetResponse.get status()",
        Connection::Translate(net_status),
    ),
    (
        "std/regex.type Regex.compiledFind()",
        Connection::Static("RegexSupport", "CompiledFind"),
    ),
    (
        "std/regex.type Regex.compiledFound()",
        Connection::Static("RegexSupport", "CompiledFound"),
    ),
    (
        "std/regex.type Regex.compiledReplace()",
        Connection::Static("RegexSupport", "CompiledReplace"),
    ),
    (
        "std/regex.type Regex.compiledSplit()",
        Connection::Static("RegexSupport", "CompiledSplit"),
    ),
    (
        "std/regex.type RegexFormatter.adjustCodeSet()",
        Connection::Static("RegexSupport", "AdjustCodeSet"),
    ),
    (
        "std/regex.type RegexFormatter.pushCodeTo()",
        Connection::Static("RegexSupport", "PushCodeTo"),
    ),
    (
        "std/regex.type RegexFormatter.regexCompileFormatted()",
        Connection::Static("RegexSupport", "CompileFormatted"),
    ),
    (
        "std/temporal.type Date.constructor()",
        Connection::Static("TemporalSupport", "Date"),
    ),
    (
        "std/temporal.type Date.day",
        Connection::Translate(date_day),
    ),
    (
        "std/temporal.type Date.fromIsoString()",
        Connection::Static("TemporalSupport", "FromIsoString"),
    ),
    (
        "std/temporal.type Date.get dayOfWeek()",
        Connection::Translate(date_day_of_week),
    ),
    (
        "std/temporal.type Date.month",
        Connection::Translate(date_month),
    ),
    (
        "std/temporal.type Date.toString()",
        Connection::Translate(date_to_string),
    ),
    (
        "std/temporal.type Date.today()",
        Connection::Static("TemporalSupport", "Today"),
    ),
    (
        "std/temporal.type Date.year",
        Connection::Translate(date_year),
    ),
    (
        "std/temporal.type Date.yearsBetween()",
        Connection::Static("TemporalSupport", "YearsBetween"),
    ),
    (
        "std/testing.type Test.bail()",
        Connection::Translate(test_bail),
    ),
    (
        "std/testing.type Test.panics()",
        Connection::Translate(test_panics),
    ),
];

/// A helper method: its name, result, parameters (type and name), and
/// body.
pub struct Helper {
    pub name: &'static str,
    pub result: &'static str,
    pub params: &'static [(&'static str, &'static str)],
    pub body: &'static str,
}

/// Helpers that a module's class gets where it uses them, as `internal`
/// static methods: each name, result, parameters, and body. Kotlin's
/// runtime's `Float64.Format` and `Float64.ToFloat64(string)` format and
/// parse by the current culture (`1,5` and a `−` in `sv-SE`); these are
/// theirs by the invariant culture, and the parse takes no thousands
/// separators.
pub const HELPERS: &[Helper] = &[
    Helper {
        name: "FormatFloat64",
        result: "string",
        params: &[("double", "d")],
        body: "if (double.IsInfinity(d))
{
    return d < 0 ? \"-Infinity\" : \"Infinity\";
}
if (double.IsNaN(d))
{
    return \"NaN\";
}
string s = d.ToString(global::System.Globalization.CultureInfo.InvariantCulture);
int e = s.IndexOf('E', global::System.StringComparison.Ordinal);
string main = e == -1 ? s : s[..e];
string frac = main.Contains('.', global::System.StringComparison.Ordinal) ? \"\" : \".0\";
string exp = e == -1 ? \"\" : string.Concat(\"e\", global::System.MemoryExtensions.AsSpan(s, e + 1));
return main + frac + exp;",
    },
    Helper {
        name: "ParseFloat64",
        result: "double",
        params: &[("string", "s")],
        body: "string trimmed = s.Trim();
if (trimmed.StartsWith('.') || trimmed.EndsWith('.'))
{
    throw new global::TemperLang.Core.ParseError(s, \"not a Float64\");
}
return trimmed switch
{
    \"Infinity\" => double.PositiveInfinity,
    \"-Infinity\" => double.NegativeInfinity,
    _ => double.TryParse(
        trimmed,
        global::System.Globalization.NumberStyles.Float,
        global::System.Globalization.CultureInfo.InvariantCulture,
        out double d) ? d : throw new global::TemperLang.Core.ParseError(s, \"not a Float64\"),
};",
    },
];

/// How a connected type is written: its simple name, and the class or
/// namespace that holds it (so the translator can import or qualify it),
/// empty for a keyword such as `int`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct TypeConnection {
    pub name: &'static str,
    pub namespace: &'static str,
}

/// Sorted by qname.
pub const TYPES: &[(&str, TypeConnection)] = &[
    (
        "core.type BrokenGenerator",
        TypeConnection {
            name: "BrokenGenerator",
            namespace: "TemperLang.Core",
        },
    ),
    (
        "core.type BrokenPromise",
        TypeConnection {
            name: "BrokenPromise",
            namespace: "TemperLang.Core",
        },
    ),
    (
        "core.type Console",
        TypeConnection {
            name: "ILoggingConsole",
            namespace: "TemperLang.Core",
        },
    ),
    // Core's own failures, the runtime's classes.
    (
        "core.type CoreError",
        TypeConnection {
            name: "CoreException",
            namespace: "TemperLang.Core",
        },
    ),
    (
        "core.type DenseBitVector",
        TypeConnection {
            name: "BitArray",
            namespace: "System.Collections",
        },
    ),
    (
        "core.type Deque",
        TypeConnection {
            name: "Queue",
            namespace: "System.Collections.Generic",
        },
    ),
    (
        "core.type DoneResult",
        TypeConnection {
            name: "DoneResult",
            namespace: "TemperLang.Core",
        },
    ),
    (
        "core.type Empty",
        TypeConnection {
            name: "Tuple<object?>",
            namespace: "System",
        },
    ),
    // Core's root of declared failures, the runtime's marker, which each
    // library's failure roots implement.
    (
        "core.type Error",
        TypeConnection {
            name: "IFailure",
            namespace: "TemperLang.Core",
        },
    ),
    (
        "core.type Generator",
        TypeConnection {
            name: "IEnumerable",
            namespace: "System.Collections.Generic",
        },
    ),
    (
        "core.type GeneratorResult",
        TypeConnection {
            name: "IGeneratorResult",
            namespace: "TemperLang.Core",
        },
    ),
    (
        "core.type InvalidCodePoint",
        TypeConnection {
            name: "InvalidCodePoint",
            namespace: "TemperLang.Core",
        },
    ),
    (
        "core.type List",
        TypeConnection {
            name: "IReadOnlyList",
            namespace: "System.Collections.Generic",
        },
    ),
    (
        "core.type ListBuilder",
        TypeConnection {
            name: "IList",
            namespace: "System.Collections.Generic",
        },
    ),
    (
        "core.type Listed",
        TypeConnection {
            name: "IReadOnlyList",
            namespace: "System.Collections.Generic",
        },
    ),
    (
        "core.type Map",
        TypeConnection {
            name: "IReadOnlyDictionary",
            namespace: "System.Collections.Generic",
        },
    ),
    (
        "core.type MapBuilder",
        TypeConnection {
            name: "IDictionary",
            namespace: "System.Collections.Generic",
        },
    ),
    (
        "core.type Mapped",
        TypeConnection {
            name: "IReadOnlyDictionary",
            namespace: "System.Collections.Generic",
        },
    ),
    (
        "core.type MissingKey",
        TypeConnection {
            name: "MissingKey",
            namespace: "TemperLang.Core",
        },
    ),
    (
        "core.type NoStringIndex",
        TypeConnection {
            name: "int",
            namespace: "",
        },
    ),
    (
        "core.type Pair",
        TypeConnection {
            name: "KeyValuePair",
            namespace: "System.Collections.Generic",
        },
    ),
    (
        "core.type ParseError",
        TypeConnection {
            name: "ParseError",
            namespace: "TemperLang.Core",
        },
    ),
    (
        "core.type Promise",
        TypeConnection {
            name: "Task",
            namespace: "System.Threading.Tasks",
        },
    ),
    (
        "core.type PromiseBuilder",
        TypeConnection {
            name: "TaskCompletionSource",
            namespace: "System.Threading.Tasks",
        },
    ),
    (
        "core.type SafeGenerator",
        TypeConnection {
            name: "IEnumerable",
            namespace: "System.Collections.Generic",
        },
    ),
    (
        "core.type StringBuilder",
        TypeConnection {
            name: "StringBuilder",
            namespace: "System.Text",
        },
    ),
    (
        "core.type StringIndex",
        TypeConnection {
            name: "int",
            namespace: "",
        },
    ),
    (
        "core.type StringIndexOption",
        TypeConnection {
            name: "int",
            namespace: "",
        },
    ),
    (
        "core.type ValueResult",
        TypeConnection {
            name: "ValueResult",
            namespace: "TemperLang.Core",
        },
    ),
    (
        "std/net.type NetResponse",
        TypeConnection {
            name: "INetResponse",
            namespace: "TemperLang.Std.Net",
        },
    ),
    (
        "std/temporal.type Date",
        TypeConnection {
            name: "DateTime",
            namespace: "System",
        },
    ),
];

/// The connection for a declaration's qname.
pub fn lookup(qname: &str) -> Option<Connection> {
    TABLE
        .binary_search_by_key(&qname, |(k, _)| k)
        .ok()
        .map(|i| TABLE[i].1)
}

/// The connection for a type's qname.
pub fn lookup_type(qname: &str) -> Option<TypeConnection> {
    TYPES
        .binary_search_by_key(&qname, |(k, _)| k)
        .ok()
        .map(|i| TYPES[i].1)
}

/// `Support.Logging.LoggingConsoleFactory.CreateConsole(name)`, the
/// library's console factory from its `Logging.cs`, named for the
/// module's namespace unless a name is given, as Kotlin's.
/// `throw new AssertFailedException(test.MessagesCombined() ?? "")`, as
/// Kotlin's `bail`: MSTest reports the message as it is, where
/// `Assert.Fail` would add to it.
fn test_bail(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let test = one(t, args, "Test.bail")?;
    let messages = test.method("MessagesCombined", Vec::new());
    let message = Expr::bin(Bin::Coalesce, messages, Expr::Str(String::new()));
    let exception = t.construct("AssertFailedException", vec![message]);
    Ok(Expr::Throw(Box::new(exception)))
}

/// `Core.BlockPanics(block)`, which catches a panic, anything but a
/// declared failure or a test's failure, for std's `Test.assertPanics`.
fn test_panics(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [_, block] = args else {
        return Err(NotYet {
            what: "`Test.panics` without its receiver and block".into(),
        });
    };
    let block = t.expr(*block)?;
    Ok(t.class("Core").method("BlockPanics", vec![block]))
}

fn get_console(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let name = match args {
        [] => None,
        [n] if matches!(t.node(*n), Node::Omitted {}) => None,
        [n] => Some(t.expr(*n)?),
        _ => {
            return Err(NotYet {
                what: "`getConsole` with more than a name".into(),
            });
        }
    };
    let name = name.unwrap_or_else(|| Expr::Str(t.namespace().to_string()));
    let factory = t.support("Logging").member("LoggingConsoleFactory");
    Ok(factory.method("CreateConsole", vec![name]))
}

/// `b ? "true" : "false"`, where Kotlin's `b.ToString().ToLower()` takes
/// `True` or `False` and lowercases it by the current culture.
fn boolean_to_string(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [b] = args else {
        return Err(NotYet {
            what: "`Boolean.toString` without its receiver".into(),
        });
    };
    Ok(Expr::Cond {
        cond: Box::new(t.expr(*b)?),
        then: Box::new(Expr::Str("true".into())),
        els: Box::new(Expr::Str("false".into())),
    })
}

/// `n.ToString(CultureInfo.InvariantCulture)` without a radix, else the
/// runtime's `Core.ConvertToString(n, radix)`, which writes its own
/// digits and `-`.
fn int_to_string(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let (n, radix) = match args {
        [n] => (*n, None),
        [n, r] if matches!(t.node(*r), Node::Omitted {}) => (*n, None),
        [n, r] => (*n, Some(*r)),
        _ => {
            return Err(NotYet {
                what: "`Int32.toString` without its receiver".into(),
            });
        }
    };
    let n = t.expr(n)?;
    match radix {
        None => Ok(t.invariant(n)),
        Some(r) => {
            let r = t.expr(r)?;
            Ok(t.class("Core").method("ConvertToString", vec![n, r]))
        }
    }
}

/// `(int)x`, `(long)x`, and `(double)x`: conversions C# writes as casts,
/// as Kotlin's.
fn cast(t: &mut Translator<'_>, args: &[NodeId], ty: &str) -> Result<Expr, NotYet> {
    let [x] = args else {
        return Err(NotYet {
            what: format!("a conversion to {ty} without its receiver"),
        });
    };
    Ok(Expr::Cast(ty.into(), Box::new(t.expr(*x)?)))
}

fn cast_int(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    cast(t, args, "int")
}

fn cast_long(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    cast(t, args, "long")
}

fn cast_double(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    cast(t, args, "double")
}

/// The module's `FormatFloat64(x)` ([`HELPERS`]), where Kotlin's is the
/// runtime's `Float64.Format(x)`, by the current culture.
fn float_to_string(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [x] = args else {
        return Err(NotYet {
            what: "`Float64.toString` without its receiver".into(),
        });
    };
    let x = t.expr(*x)?;
    Ok(t.helper("FormatFloat64").call(vec![x]))
}

/// The module's `ParseFloat64(s)` ([`HELPERS`]), where Kotlin's is the
/// runtime's `Float64.ToFloat64(s)`, by the current culture.
fn string_to_float(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [s] = args else {
        return Err(NotYet {
            what: "`String.toFloat64` without its receiver".into(),
        });
    };
    let s = t.expr(*s)?;
    Ok(t.helper("ParseFloat64").call(vec![s]))
}

/// `s.Length == 0`; Kotlin's `string.IsNullOrEmpty(s)` also checks for a
/// `null` that a Temper `String` never is.
fn is_empty(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [s] = args else {
        return Err(NotYet {
            what: "`String.isEmpty` without its receiver".into(),
        });
    };
    let s = t.expr(*s)?;
    Ok(Expr::bin(Bin::Eq, s.member("Length"), Expr::Int(0)))
}

fn one(t: &mut Translator<'_>, args: &[NodeId], what: &str) -> Result<Expr, NotYet> {
    match args {
        [x] => t.expr(*x),
        _ => Err(NotYet {
            what: format!("`{what}` with other than its receiver"),
        }),
    }
}

/// `d.Year`, a `DateTime`'s, as Kotlin's.
fn date_year(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(one(t, args, "Date.year")?.member("Year"))
}

/// `d.Month`, from 1, as Temper's.
fn date_month(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(one(t, args, "Date.month")?.member("Month"))
}

/// `r.Status`, `INetResponse`'s property.
fn net_status(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(one(t, args, "NetResponse.status")?.member("Status"))
}

/// `r.ContentType`.
fn net_content_type(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(one(t, args, "NetResponse.contentType")?.member("ContentType"))
}

/// `r.BodyContent`.
fn net_body_content(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(one(t, args, "NetResponse.bodyContent")?.member("BodyContent"))
}

/// `d.Day`.
fn date_day(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(one(t, args, "Date.day")?.member("Day"))
}

/// `((int)d.DayOfWeek + 6) % 7 + 1`: ISO's weekday, Sunday 7 where .NET's
/// is 0. Kotlin's `TemporalSupport.IsoWeekdayNum` gives Sunday 0
/// (notes/kotlin-differences.md).
fn date_day_of_week(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let day = one(t, args, "Date.dayOfWeek")?.member("DayOfWeek");
    let day = Expr::Cast("int".into(), Box::new(day));
    let shifted = Expr::bin(Bin::Add, day, Expr::Int(6));
    let iso = Expr::bin(Bin::Mod, shifted, Expr::Int(7));
    Ok(Expr::bin(Bin::Add, iso, Expr::Int(1)))
}

/// `d.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)`: as Kotlin's,
/// but in the invariant culture's calendar, not the current culture's.
fn date_to_string(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let d = one(t, args, "Date.toString")?;
    let invariant = t.class("CultureInfo").member("InvariantCulture");
    Ok(d.method("ToString", vec![Expr::Str("yyyy-MM-dd".into()), invariant]))
}

/// The receiver itself: `List.toList()`.
fn same(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    one(t, args, "toList")
}

/// `x.Count`.
fn count(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(one(t, args, "length")?.member("Count"))
}

/// `e.Message`, a declared failure's.
fn failure_message(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(one(t, args, "message")?.member("Message"))
}

/// `x.Count == 0`.
fn none(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let n = one(t, args, "isEmpty")?.member("Count");
    Ok(Expr::bin(Bin::Eq, n, Expr::Int(0)))
}

/// `x[i] = v`.
fn set_index(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [x, i, v] = args else {
        return Err(NotYet {
            what: "`set` without an index and a value".into(),
        });
    };
    let x = t.expr(*x)?;
    let i = t.expr(*i)?;
    let v = t.expr(*v)?;
    Ok(Expr::Assign(
        Box::new(Expr::Index(Box::new(x), Box::new(i))),
        Box::new(v),
    ))
}

/// `b.Add(x)`, or with a place, `Listed.Add(b, x, at)`.
fn list_add(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let (b, x, at) = match args {
        [b, x] => (*b, *x, None),
        [b, x, at] if matches!(t.node(*at), Node::Omitted {}) => (*b, *x, None),
        [b, x, at] => (*b, *x, Some(*at)),
        _ => {
            return Err(NotYet {
                what: "`add` without its value".into(),
            });
        }
    };
    let b = t.expr(b)?;
    let x = t.expr(x)?;
    Ok(match at {
        None => b.method("Add", vec![x]),
        Some(at) => {
            let at = t.expr(at)?;
            t.class("Listed").method("Add", vec![b, x, at])
        }
    })
}

/// `new List<T>()`, or of a list's items, `new List<T>(x)`, its type
/// the call's.
fn new_list(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let item = t.call_item_type()?;
    let args = match args {
        [] => Vec::new(),
        _ => vec![one(t, args, "toListBuilder")?],
    };
    let list = t.class("List");
    let Expr::Name(list) = list else {
        unreachable!("a class is a name")
    };
    Ok(Expr::name(format!("new {list}<{item}>")).call(args))
}

/// `new TemperLang.Core.OrderedDictionary<K, V>()`, the runtime's, which
/// keeps its keys' order as Temper's maps do; qualified, since .NET 9's
/// `System.Collections.Generic` has one too.
fn new_map_builder(t: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    let ty = t.call_type()?;
    let args = ty.split_once('<').map_or("", |(_, a)| a);
    Ok(Expr::name(format!("new TemperLang.Core.OrderedDictionary<{args}")).call(Vec::new()))
}

/// `new MissingKey(key)`, the runtime's class.
fn new_missing_key(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let key = one(t, args, "MissingKey")?;
    Ok(t.construct("MissingKey", vec![key]))
}

/// `new BrokenGenerator()`, the runtime's class.
fn new_broken_generator(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let args = t.connected_args(args)?;
    Ok(t.construct("BrokenGenerator", args))
}

/// `new BrokenPromise()`, the runtime's class.
fn new_broken_promise(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let args = t.connected_args(args)?;
    Ok(t.construct("BrokenPromise", args))
}

/// `new InvalidCodePoint(codePoint)`, the runtime's class.
fn new_invalid_code_point(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let code_point = one(t, args, "InvalidCodePoint")?;
    Ok(t.construct("InvalidCodePoint", vec![code_point]))
}

/// `new ParseError(input, reason)`, the runtime's class.
fn new_parse_error(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let args = t.connected_args(args)?;
    Ok(t.construct("ParseError", args))
}

/// `Mapped.GetOrDefault(m, k, fallback)`, or where the fallback is null
/// and the values aren't, `Mapped.GetValueOrNull(m, k)` (for a value
/// type's) or `Mapped.GetOrNull(m, k)`, then `?? fallback` unless that's
/// `null`: C# can't pass null for a value type's `TValue`.
fn mapped_get_or(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    get_or(t, args, "Mapped", "GetOrDefault")
}

/// As [`mapped_get_or`], for `Listed.GetOr`.
fn listed_get_or(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    get_or(t, args, "Listed", "GetOr")
}

fn get_or(
    t: &mut Translator<'_>,
    args: &[NodeId],
    class: &str,
    method: &str,
) -> Result<Expr, NotYet> {
    let &[receiver, key, fallback] = args else {
        return Err(NotYet {
            what: format!("`getOr` without a key and a fallback ({class})"),
        });
    };
    let p = t.program();
    // The receiver's values' type, its last type argument.
    let element = p.types.args(p.nodes.ty(receiver)).last().copied();
    let wanted = p.nodes.ty(fallback);
    let widened = match (element, p.types.get(wanted)) {
        (Some(e), TypeData::Nullable(inner))
            if !matches!(p.types.get(e), TypeData::Nullable(_)) =>
        {
            Some(inner)
        }
        _ => None,
    };
    let Some(inner) = widened else {
        let exprs = t.connected_args(args)?;
        return Ok(t.class(class).method(method, exprs));
    };
    if t.is_optional(wanted) {
        return Err(NotYet {
            what: "`getOr` of a type parameter's values with a null fallback".into(),
        });
    }
    let or_null = match t.is_value_type(inner) {
        true => "GetValueOrNull",
        false => "GetOrNull",
    };
    let receiver = t.expr(receiver)?;
    let key = t.expr(key)?;
    let got = t.class(class).method(or_null, vec![receiver, key]);
    Ok(match p.nodes.get(fallback) {
        Node::Null {} => got,
        _ => Expr::bin(Bin::Coalesce, got, t.expr(fallback)?),
    })
}

/// A pair, `new KeyValuePair<K, V>(key, value)`.
fn new_pair(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [k, v] = args else {
        return Err(NotYet {
            what: "a `Pair` of other than a key and a value".into(),
        });
    };
    let ty = t.call_type()?;
    let k = t.expr(*k)?;
    let v = t.expr(*v)?;
    Ok(Expr::name(format!("new {ty}")).call(vec![k, v]))
}

/// `Listed.ToReadOnlyList(Enumerable.Select(ls, f))`, as Kotlin's.
fn list_map(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    linq(t, args, "Select")
}

/// `Listed.ToReadOnlyList(Enumerable.Where(ls, f))`, as Kotlin's.
fn list_filter(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    linq(t, args, "Where")
}

fn linq(t: &mut Translator<'_>, args: &[NodeId], method: &str) -> Result<Expr, NotYet> {
    let exprs = t.connected_args(args)?;
    let found = t.class("Enumerable").method(method, exprs);
    Ok(t.class("Listed").method("ToReadOnlyList", vec![found]))
}

/// `Enumerable.Aggregate(ls, initial, f)`, `reduceFrom`, as Kotlin's.
fn listed_reduce(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let exprs = t.connected_args(args)?;
    Ok(t.class("Enumerable").method("Aggregate", exprs))
}

/// `new T()`, or `new T(n)`, its type the call's: a `StringBuilder`, a
/// `Queue<T>` for a `Deque`, or a `BitArray` for a `DenseBitVector`, as
/// Kotlin's.
fn new_of_type(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let ty = t.call_type()?;
    let args = t.connected_args(args)?;
    Ok(Expr::name(format!("new {ty}")).call(args))
}

/// `b.Task`: a `TaskCompletionSource`'s promise, as Kotlin's.
fn task(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(one(t, args, "promise")?.member("Task"))
}

/// `x.Length`: a string's end, or a builder's, as Kotlin's.
fn length(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(one(t, args, "end")?.member("Length"))
}

/// `0`, `String.begin`: string indices are UTF-16 offsets, as Kotlin's.
fn zero(_: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(Expr::Int(0))
}

/// `-1`, `StringIndex.none`, as Kotlin's.
fn minus_one(_: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(Expr::Int(-1))
}

/// `Math.E`.
fn math_e(t: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(t.class("Math").member("E"))
}

/// `Math.PI`.
fn math_pi(t: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(t.class("Math").member("PI"))
}

/// `b.Append(s)`, or `b.Append('c')` for a constant of one UTF-16 unit,
/// as C#'s analyzers want (CA1834).
fn append(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [b, s] = args else {
        return Err(NotYet {
            what: "`StringBuilder.append` without one string".into(),
        });
    };
    let b = t.expr(*b)?;
    let text = match t.program().nodes.get(*s) {
        Node::StringLit { value } => Some(t.program().strings.get(value).to_string()),
        _ => None,
    };
    let s = match text {
        Some(text) if text.encode_utf16().count() == 1 => {
            let quoted = crate::tree::string_literal(&text);
            let inner = &quoted[1..quoted.len() - 1];
            let inner = if inner == "'" { "\\'" } else { inner };
            Expr::name(format!("'{inner}'"))
        }
        _ => t.expr(*s)?,
    };
    Ok(b.method("Append", vec![s]))
}

/// std's connected keys this backend connects, sorted, as Kotlin's
/// C# support network (`CSharpSupportNetwork.kt`) lists them; its other std
/// `@connected` members use their Temper bodies. Which std keys a backend
/// connects is its own (notes/tmpl/open-questions.md, 48).
pub const STD: &[&str] = &[
    "std/net.sendRequest()",
    "std/net.type NetResponse",
    "std/net.type NetResponse.get bodyContent()",
    "std/net.type NetResponse.get contentType()",
    "std/net.type NetResponse.get status()",
    "std/regex.type Regex.compiledFind()",
    "std/regex.type Regex.compiledFound()",
    "std/regex.type Regex.compiledReplace()",
    "std/regex.type Regex.compiledSplit()",
    "std/regex.type RegexFormatter.adjustCodeSet()",
    "std/regex.type RegexFormatter.pushCodeTo()",
    "std/regex.type RegexFormatter.regexCompileFormatted()",
    "std/temporal.type Date",
    "std/temporal.type Date.constructor()",
    "std/temporal.type Date.day",
    "std/temporal.type Date.fromIsoString()",
    "std/temporal.type Date.get dayOfWeek()",
    "std/temporal.type Date.month",
    "std/temporal.type Date.toString()",
    "std/temporal.type Date.today()",
    "std/temporal.type Date.year",
    "std/temporal.type Date.yearsBetween()",
    "std/testing.type Test.bail()",
    "std/testing.type Test.panics()",
];

/// Whether this backend connects a std declaration, by its key
/// (`temper_tmpl::qname::std_key`).
pub fn connects_std(key: &str) -> bool {
    STD.binary_search(&key).is_ok()
}
