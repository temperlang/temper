//! The connection tables: how core's connected declarations and types are
//! written, by qname, with Kotlin's strings as keys (notes/tmpl/names.md,
//! "Connected declarations"). Each entry matches what Kotlin's Java
//! backend gives for it (its `JavaSupportNetwork.kt`).

use crate::NotYet;
use crate::translate::Translator;
use crate::tree::{Bin, Expr};
use temper_tmpl::{Node, NodeId, Scalar, TypeData, TypeId};

/// Custom output: from the call's argument nodes, a method's receiver
/// first, and `Omitted` for an argument left out.
pub type Hook = fn(&mut Translator<'_>, &[NodeId]) -> Result<Expr, NotYet>;

/// How a connected declaration is written.
#[derive(Clone, Copy)]
pub enum Connection {
    /// A static method of a JDK class, given the arguments, a method's
    /// receiver first: `Math.min(a, b)`.
    Static(&'static str, &'static str),
    /// A method of the receiver, given the rest: `s.isEmpty()`.
    Method(&'static str),
    /// A primitive conversion of the receiver: `(int) x`.
    Cast(&'static str),
    /// Custom output.
    Translate(Hook),
    /// A static method of a std module's support class (Kotlin's
    /// `temper/std/regex/Core.java`, which std's output has beside the
    /// module: [`crate::STD_SUPPORT`]), by its class's full name, given
    /// the arguments, a method's receiver first:
    /// `temper.std.regex.Core.regexCompiledFound(this, compiled, text)`.
    StdStatic(&'static str, &'static str),
}

/// Sorted by qname.
pub const TABLE: &[(&str, Connection)] = &[
    ("core.async()", Connection::Translate(run_async)),
    ("core.awakeUpon()", Connection::Translate(awake_upon)),
    ("core.doneResult()", Connection::Translate(done_result)),
    ("core.empty()", Connection::Translate(empty)),
    ("core.getConsole()", Connection::Translate(get_console)),
    (
        "core.getPromiseResultSync()",
        Connection::Translate(get_promise_result),
    ),
    (
        "core.safeAdaptGeneratorFn()",
        Connection::Translate(safe_adapt_generator_fn),
    ),
    (
        "core.type Boolean.toString()",
        Connection::Static("Boolean", "toString"),
    ),
    ("core.type Console.log()", Connection::Method("log")),
    (
        "core.type DenseBitVector.constructor()",
        Connection::Translate(new_bit_set),
    ),
    ("core.type DenseBitVector.get()", Connection::Method("get")),
    ("core.type DenseBitVector.set()", Connection::Method("set")),
    ("core.type Deque.add()", Connection::Method("addLast")),
    (
        "core.type Deque.constructor()",
        Connection::Translate(new_deque),
    ),
    (
        "core.type Deque.get isEmpty()",
        Connection::Method("isEmpty"),
    ),
    (
        "core.type Deque.removeFirst()",
        Connection::Static("Core", "dequeRemoveFirst"),
    ),
    // A declared failure's cause is Java's own `getCause()`, where that's
    // a declared failure (`temper.core.Failure`); its message,
    // `getMessage()`, which every failure class overrides.
    (
        "core.type Error.get cause()",
        Connection::Static("Core", "failureCause"),
    ),
    (
        "core.type Error.get message()",
        Connection::Method("getMessage"),
    ),
    ("core.type Float64.abs()", Connection::Static("Math", "abs")),
    (
        "core.type Float64.acos()",
        Connection::Static("Math", "acos"),
    ),
    (
        "core.type Float64.asin()",
        Connection::Static("Math", "asin"),
    ),
    (
        "core.type Float64.atan()",
        Connection::Static("Math", "atan"),
    ),
    (
        "core.type Float64.atan2()",
        Connection::Static("Math", "atan2"),
    ),
    (
        "core.type Float64.ceil()",
        Connection::Static("Math", "ceil"),
    ),
    ("core.type Float64.cos()", Connection::Static("Math", "cos")),
    (
        "core.type Float64.cosh()",
        Connection::Static("Math", "cosh"),
    ),
    ("core.type Float64.e", Connection::Translate(math_e)),
    ("core.type Float64.exp()", Connection::Static("Math", "exp")),
    (
        "core.type Float64.expm1()",
        Connection::Static("Math", "expm1"),
    ),
    (
        "core.type Float64.floor()",
        Connection::Static("Math", "floor"),
    ),
    ("core.type Float64.log()", Connection::Static("Math", "log")),
    (
        "core.type Float64.log10()",
        Connection::Static("Math", "log10"),
    ),
    (
        "core.type Float64.log1p()",
        Connection::Static("Math", "log1p"),
    ),
    ("core.type Float64.max()", Connection::Static("Math", "max")),
    ("core.type Float64.min()", Connection::Static("Math", "min")),
    (
        "core.type Float64.near()",
        Connection::Static("Core", "float64Near"),
    ),
    ("core.type Float64.pi", Connection::Translate(math_pi)),
    (
        "core.type Float64.round()",
        Connection::Static("Math", "round"),
    ),
    (
        "core.type Float64.sign()",
        Connection::Static("Math", "signum"),
    ),
    ("core.type Float64.sin()", Connection::Static("Math", "sin")),
    (
        "core.type Float64.sinh()",
        Connection::Static("Math", "sinh"),
    ),
    (
        "core.type Float64.sqrt()",
        Connection::Static("Math", "sqrt"),
    ),
    ("core.type Float64.tan()", Connection::Static("Math", "tan")),
    (
        "core.type Float64.tanh()",
        Connection::Static("Math", "tanh"),
    ),
    (
        "core.type Float64.toInt32()",
        Connection::Static("Core", "float64ToInt"),
    ),
    ("core.type Float64.toInt32Unsafe()", Connection::Cast("int")),
    (
        "core.type Float64.toInt64()",
        Connection::Static("Core", "float64ToInt64"),
    ),
    (
        "core.type Float64.toInt64Unsafe()",
        Connection::Cast("long"),
    ),
    (
        "core.type Float64.toString()",
        Connection::Static("Core", "float64ToString"),
    ),
    ("core.type Int32.max()", Connection::Static("Math", "max")),
    ("core.type Int32.min()", Connection::Static("Math", "min")),
    ("core.type Int32.toFloat64()", Connection::Cast("double")),
    ("core.type Int32.toInt64()", Connection::Cast("long")),
    (
        "core.type Int32.toString()",
        Connection::Static("Integer", "toString"),
    ),
    ("core.type Int64.max()", Connection::Static("Math", "max")),
    ("core.type Int64.min()", Connection::Static("Math", "min")),
    (
        "core.type Int64.toFloat64()",
        Connection::Static("Core", "int64ToFloat64"),
    ),
    (
        "core.type Int64.toFloat64Unsafe()",
        Connection::Cast("double"),
    ),
    (
        "core.type Int64.toInt32()",
        Connection::Static("Core", "int64ToInt"),
    ),
    ("core.type Int64.toInt32Unsafe()", Connection::Cast("int")),
    (
        "core.type Int64.toString()",
        Connection::Static("Long", "toString"),
    ),
    ("core.type List.get length()", Connection::Method("size")),
    (
        "core.type List.get()",
        Connection::Static("Core", "listGet"),
    ),
    ("core.type List.toList()", Connection::Translate(same)),
    (
        "core.type List.toListBuilder()",
        Connection::Translate(new_array_list),
    ),
    (
        "core.type ListBuilder.add()",
        Connection::Translate(list_add),
    ),
    (
        "core.type ListBuilder.addAll()",
        Connection::Static("Core", "listAddAll"),
    ),
    ("core.type ListBuilder.clear()", Connection::Method("clear")),
    (
        "core.type ListBuilder.constructor()",
        Connection::Translate(new_array_list),
    ),
    (
        "core.type ListBuilder.get length()",
        Connection::Method("size"),
    ),
    (
        "core.type ListBuilder.removeLast()",
        Connection::Static("Core", "listRemoveLast"),
    ),
    (
        "core.type ListBuilder.reverse()",
        Connection::Static("Collections", "reverse"),
    ),
    (
        "core.type ListBuilder.set()",
        Connection::Static("Core", "listSet"),
    ),
    (
        "core.type ListBuilder.sort()",
        Connection::Translate(list_sort),
    ),
    (
        "core.type ListBuilder.splice()",
        Connection::Static("Core", "listSplice"),
    ),
    (
        "core.type ListBuilder.toList()",
        Connection::Translate(copy_of),
    ),
    (
        "core.type ListBuilder.toListBuilder()",
        Connection::Translate(new_array_list),
    ),
    (
        "core.type Listed.filter()",
        Connection::Translate(list_filter),
    ),
    (
        "core.type Listed.get isEmpty()",
        Connection::Method("isEmpty"),
    ),
    ("core.type Listed.get length()", Connection::Method("size")),
    (
        "core.type Listed.get()",
        Connection::Static("Core", "listGet"),
    ),
    (
        "core.type Listed.getOr()",
        Connection::Static("Core", "listGetOr"),
    ),
    ("core.type Listed.join()", Connection::Translate(list_join)),
    ("core.type Listed.map()", Connection::Translate(list_map)),
    (
        "core.type Listed.reduce()",
        Connection::Translate(listed_reduce),
    ),
    (
        "core.type Listed.reduceFrom()",
        Connection::Translate(listed_reduce_from),
    ),
    (
        "core.type Listed.slice()",
        Connection::Static("Core", "listSlice"),
    ),
    (
        "core.type Listed.sorted()",
        Connection::Translate(list_sorted),
    ),
    ("core.type Listed.toList()", Connection::Translate(copy_of)),
    (
        "core.type Listed.toListBuilder()",
        Connection::Translate(new_array_list),
    ),
    (
        "core.type Map.constructor()",
        Connection::Static("Core", "mapConstructor"),
    ),
    ("core.type MapBuilder.clear()", Connection::Method("clear")),
    (
        "core.type MapBuilder.constructor()",
        Connection::Translate(new_linked_hash_map),
    ),
    (
        "core.type MapBuilder.remove()",
        Connection::Static("Core", "mapBuilderRemove"),
    ),
    ("core.type MapBuilder.set()", Connection::Method("put")),
    (
        "core.type Mapped.forEach()",
        Connection::Translate(mapped_for_each),
    ),
    ("core.type Mapped.get length()", Connection::Method("size")),
    (
        "core.type Mapped.get()",
        Connection::Static("Core", "mappedGet"),
    ),
    (
        "core.type Mapped.getOr()",
        Connection::Method("getOrDefault"),
    ),
    ("core.type Mapped.has()", Connection::Method("containsKey")),
    ("core.type Mapped.keys()", Connection::Translate(keys)),
    (
        "core.type Mapped.toList()",
        Connection::Static("Core", "mappedToList"),
    ),
    (
        "core.type Mapped.toListWith()",
        Connection::Translate(mapped_to_list_with),
    ),
    (
        "core.type Mapped.toMap()",
        Connection::Static("Core", "mappedToMap"),
    ),
    (
        "core.type Mapped.toMapBuilder()",
        Connection::Translate(new_linked_hash_map),
    ),
    ("core.type Mapped.values()", Connection::Translate(values)),
    (
        "core.type Pair.constructor()",
        Connection::Translate(new_entry),
    ),
    (
        "core.type PromiseBuilder.breakPromise()",
        Connection::Translate(break_promise),
    ),
    (
        "core.type PromiseBuilder.complete()",
        Connection::Method("complete"),
    ),
    (
        "core.type PromiseBuilder.constructor()",
        Connection::Translate(new_completable_future),
    ),
    (
        "core.type PromiseBuilder.get promise()",
        Connection::Translate(identity),
    ),
    (
        "core.type SafeGenerator.nextSafe()",
        Connection::Method("get"),
    ),
    (
        "core.type String.begin",
        Connection::Translate(string_begin),
    ),
    (
        "core.type String.countBetween()",
        Connection::Static("Core", "stringCountBetween"),
    ),
    (
        "core.type String.fromCodePoint()",
        Connection::Static("Core", "stringFromCodePoint"),
    ),
    (
        "core.type String.fromCodePoints()",
        Connection::Static("Core", "stringFromCodePoints"),
    ),
    ("core.type String.get end()", Connection::Method("length")),
    (
        "core.type String.get isEmpty()",
        Connection::Method("isEmpty"),
    ),
    (
        "core.type String.get()",
        Connection::Static("Core", "stringGet"),
    ),
    (
        "core.type String.hasAtLeast()",
        Connection::Static("Core", "stringHasAtLeast"),
    ),
    (
        "core.type String.hasIndex()",
        Connection::Static("Core", "stringHasIndex"),
    ),
    ("core.type String.indexOf()", Connection::Method("indexOf")),
    (
        "core.type String.next()",
        Connection::Static("Core", "stringNext"),
    ),
    (
        "core.type String.prev()",
        Connection::Static("Core", "stringPrev"),
    ),
    (
        "core.type String.slice()",
        Connection::Static("Core", "stringSlice"),
    ),
    (
        "core.type String.split()",
        Connection::Static("Core", "stringSplit"),
    ),
    (
        "core.type String.step()",
        Connection::Static("Core", "stringStep"),
    ),
    (
        "core.type String.toFloat64()",
        Connection::Static("Core", "stringToFloat64"),
    ),
    (
        "core.type String.toInt32()",
        Connection::Static("Core", "stringToInt"),
    ),
    (
        "core.type String.toInt64()",
        Connection::Static("Core", "stringToInt64"),
    ),
    ("core.type String.toString()", Connection::Translate(same)),
    (
        "core.type StringBuilder.append()",
        Connection::Method("append"),
    ),
    (
        "core.type StringBuilder.appendBetween()",
        Connection::Static("Core", "stringBuilderAppendBetween"),
    ),
    (
        "core.type StringBuilder.appendCodePoint()",
        Connection::Static("Core", "stringBuilderAppendCodePoint"),
    ),
    (
        "core.type StringBuilder.clear()",
        Connection::Translate(string_builder_clear),
    ),
    (
        "core.type StringBuilder.constructor()",
        Connection::Translate(new_string_builder),
    ),
    (
        "core.type StringBuilder.get end()",
        Connection::Method("length"),
    ),
    (
        "core.type StringBuilder.toString()",
        Connection::Method("toString"),
    ),
    (
        "core.type StringIndex.none",
        Connection::Translate(string_index_none),
    ),
    (
        "core.type StringIndexOption.compareTo()",
        Connection::Static("Integer", "compare"),
    ),
    (
        "core.type StringIndexOption.eq()",
        Connection::Translate(string_index_eq),
    ),
    (
        "core.type ValueResult.constructor()",
        Connection::Translate(new_value_result),
    ),
    (
        "std/net.sendRequest()",
        Connection::StdStatic("temper.core.net.Core", "stdNetSend"),
    ),
    (
        "std/net.type NetResponse.get bodyContent()",
        Connection::Method("getBodyContent"),
    ),
    (
        "std/net.type NetResponse.get contentType()",
        Connection::Method("getContentType"),
    ),
    (
        "std/net.type NetResponse.get status()",
        Connection::Method("getStatus"),
    ),
    (
        "std/regex.type Regex.compiledFind()",
        Connection::StdStatic("temper.std.regex.Core", "regexCompiledFind"),
    ),
    (
        "std/regex.type Regex.compiledFound()",
        Connection::StdStatic("temper.std.regex.Core", "regexCompiledFound"),
    ),
    (
        "std/regex.type Regex.compiledReplace()",
        Connection::StdStatic("temper.std.regex.Core", "regexCompiledReplace"),
    ),
    (
        "std/regex.type Regex.compiledSplit()",
        Connection::StdStatic("temper.std.regex.Core", "regexCompiledSplit"),
    ),
    (
        "std/regex.type RegexFormatter.pushCodeTo()",
        Connection::StdStatic("temper.std.regex.Core", "regexFormatterPushCodeTo"),
    ),
    (
        "std/regex.type RegexFormatter.regexCompileFormatted()",
        Connection::StdStatic("temper.std.regex.Core", "regexCompiledFormatted"),
    ),
    (
        "std/temporal.type Date.constructor()",
        Connection::StdStatic("temper.std.temporal.Core", "dateOf"),
    ),
    (
        "std/temporal.type Date.day",
        Connection::Method("getDayOfMonth"),
    ),
    (
        "std/temporal.type Date.fromIsoString()",
        Connection::StdStatic("temper.std.temporal.Core", "dateFromIsoString"),
    ),
    (
        "std/temporal.type Date.get dayOfWeek()",
        Connection::Translate(date_day_of_week),
    ),
    (
        "std/temporal.type Date.month",
        Connection::Method("getMonthValue"),
    ),
    (
        "std/temporal.type Date.toString()",
        Connection::Method("toString"),
    ),
    (
        "std/temporal.type Date.today()",
        Connection::Translate(date_today),
    ),
    ("std/temporal.type Date.year", Connection::Method("getYear")),
    (
        "std/temporal.type Date.yearsBetween()",
        Connection::Translate(date_years_between),
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

/// How a connected type is written: its simple name, and what to import
/// for it, if anything (`int` for a string index, as Kotlin's).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct TypeConnection {
    pub name: &'static str,
    pub import: &'static str,
}

/// Sorted by qname.
pub const TYPES: &[(&str, TypeConnection)] = &[
    (
        "core.type Console",
        TypeConnection {
            name: "Console",
            import: "temper.core.Core.Console",
        },
    ),
    (
        "core.type DenseBitVector",
        TypeConnection {
            name: "BitSet",
            import: "java.util.BitSet",
        },
    ),
    (
        "core.type Deque",
        TypeConnection {
            name: "Deque",
            import: "java.util.Deque",
        },
    ),
    (
        "core.type DoneResult",
        TypeConnection {
            name: "Result",
            import: "temper.core.Generator.Result",
        },
    ),
    (
        "core.type Empty",
        TypeConnection {
            name: "Optional<?>",
            import: "java.util.Optional",
        },
    ),
    // Core's root of declared failures, the runtime's marker, which each
    // library's failure roots implement.
    (
        "core.type Error",
        TypeConnection {
            name: "Failure",
            import: "temper.core.Failure",
        },
    ),
    (
        "core.type Generator",
        TypeConnection {
            name: "Generator",
            import: "temper.core.Generator",
        },
    ),
    (
        "core.type GeneratorResult",
        TypeConnection {
            name: "Result",
            import: "temper.core.Generator.Result",
        },
    ),
    (
        "core.type List",
        TypeConnection {
            name: "List",
            import: "java.util.List",
        },
    ),
    (
        "core.type ListBuilder",
        TypeConnection {
            name: "List",
            import: "java.util.List",
        },
    ),
    (
        "core.type Listed",
        TypeConnection {
            name: "List",
            import: "java.util.List",
        },
    ),
    (
        "core.type Map",
        TypeConnection {
            name: "Map",
            import: "java.util.Map",
        },
    ),
    (
        "core.type MapBuilder",
        TypeConnection {
            name: "Map",
            import: "java.util.Map",
        },
    ),
    (
        "core.type Mapped",
        TypeConnection {
            name: "Map",
            import: "java.util.Map",
        },
    ),
    (
        "core.type NoStringIndex",
        TypeConnection {
            name: "int",
            import: "",
        },
    ),
    (
        "core.type Pair",
        TypeConnection {
            name: "Entry",
            import: "java.util.Map.Entry",
        },
    ),
    (
        "core.type Promise",
        TypeConnection {
            name: "CompletableFuture",
            import: "java.util.concurrent.CompletableFuture",
        },
    ),
    (
        "core.type PromiseBuilder",
        TypeConnection {
            name: "CompletableFuture",
            import: "java.util.concurrent.CompletableFuture",
        },
    ),
    (
        "core.type SafeGenerator",
        TypeConnection {
            name: "Generator",
            import: "temper.core.Generator",
        },
    ),
    (
        "core.type StringBuilder",
        TypeConnection {
            name: "StringBuilder",
            import: "",
        },
    ),
    (
        "core.type StringIndex",
        TypeConnection {
            name: "int",
            import: "",
        },
    ),
    (
        "core.type StringIndexOption",
        TypeConnection {
            name: "int",
            import: "",
        },
    ),
    (
        "core.type ValueResult",
        TypeConnection {
            name: "ValueResult",
            import: "temper.core.Generator.ValueResult",
        },
    ),
    (
        "std/net.type NetResponse",
        TypeConnection {
            name: "NetResponse",
            import: "temper.core.net.NetResponse",
        },
    ),
    (
        "std/temporal.type Date",
        TypeConnection {
            name: "LocalDate",
            import: "java.time.LocalDate",
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

/// `Core.throwAssertionError(test.messagesCombined())`, as Kotlin's
/// `bail`, so JUnit reports the test's messages.
fn test_bail(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [test] = args else {
        return Err(NotYet {
            what: "`Test.bail` without its receiver".into(),
        });
    };
    let test = t.expr(*test)?;
    t.import("temper.core.Core");
    let messages = test.method("messagesCombined", Vec::new());
    Ok(Expr::name("Core").method("throwAssertionError", vec![messages]))
}

/// `Core.blockPanics(block)`, which catches a panic, anything but a
/// bubble or a test's failure, for std's `Test.assertPanics`.
fn test_panics(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [_, block] = args else {
        return Err(NotYet {
            what: "`Test.panics` without its receiver and block".into(),
        });
    };
    let block = t.expr(*block)?;
    t.import("temper.core.Core");
    Ok(Expr::name("Core").method("blockPanics", vec![block]))
}

/// `Core.getConsole(Logger.getLogger(name))`, the logger named for the
/// module's package unless a name is given, as Kotlin's.
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
    let name = name.unwrap_or_else(|| Expr::Str(t.package().to_string()));
    t.import("java.util.logging.Logger");
    t.import("temper.core.Core");
    let logger = Expr::name("Logger").method("getLogger", vec![name]);
    Ok(Expr::name("Core").method("getConsole", vec![logger]))
}

fn one(t: &mut Translator<'_>, args: &[NodeId], what: &str) -> Result<Expr, NotYet> {
    match args {
        [x] => t.expr(*x),
        _ => Err(NotYet {
            what: format!("`{what}` with other than its receiver"),
        }),
    }
}

/// The receiver itself: `List.toList()`.
fn same(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    one(t, args, "toList")
}

/// `new ArrayList<>()`, or of a list's items, `new ArrayList<>(x)`.
fn new_array_list(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let args = match args {
        [] => Vec::new(),
        _ => vec![one(t, args, "toListBuilder")?],
    };
    t.import("java.util.ArrayList");
    Ok(Expr::New("ArrayList<>".into(), args))
}

/// `new LinkedHashMap<>()`, which keeps its keys' order as Temper's maps
/// do, or of a map's entries, `new LinkedHashMap<>(m)`.
fn new_linked_hash_map(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let args = match args {
        [] => Vec::new(),
        _ => vec![one(t, args, "toMapBuilder")?],
    };
    t.import("java.util.LinkedHashMap");
    Ok(Expr::New("LinkedHashMap<>".into(), args))
}

/// `List.copyOf(x)`, as Kotlin's, but `Core.listCopyOf(x)` where an item
/// may be null, which `List.copyOf` rejects.
fn copy_of(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let nullable = args.first().is_some_and(|&x| t.items_may_be_null(x));
    let x = one(t, args, "toList")?;
    Ok(if nullable {
        t.import("temper.core.Core");
        Expr::name("Core").method("listCopyOf", vec![x])
    } else {
        t.import("java.util.List");
        Expr::name("List").method("copyOf", vec![x])
    })
}

/// `b.add(x)`, or with a place, `Core.listAdd(b, x, at)`.
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
        None => b.method("add", vec![x]),
        // `Core.listAdd`, which panics out of bounds, as core's docs say.
        Some(at) => {
            let at = t.expr(at)?;
            core_call(t, "listAdd".into(), vec![b, x, at])
        }
    })
}

/// `List.copyOf(m.keySet())`: keys are never null.
fn keys(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let m = one(t, args, "keys")?;
    t.import("java.util.List");
    Ok(Expr::name("List").method("copyOf", vec![m.method("keySet", Vec::new())]))
}

/// `Core.listCopyOf(m.values())`, whose values may be null.
fn values(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let m = one(t, args, "values")?;
    t.import("temper.core.Core");
    Ok(Expr::name("Core").method("listCopyOf", vec![m.method("values", Vec::new())]))
}

/// A pair, `new SimpleImmutableEntry<>(key, value)`, as Kotlin's.
fn new_entry(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [k, v] = args else {
        return Err(NotYet {
            what: "a `Pair` of other than a key and a value".into(),
        });
    };
    let k = t.expr(*k)?;
    let v = t.expr(*v)?;
    t.import("java.util.AbstractMap.SimpleImmutableEntry");
    Ok(Expr::New("SimpleImmutableEntry<>".into(), vec![k, v]))
}

/// How `Core`'s list helpers name a kind of item or result: `Int`,
/// `Double`, `Bool`, or `Obj`.
fn kind_of(t: &Translator<'_>, ty: TypeId) -> &'static str {
    match t.program().types.get(ty) {
        TypeData::Scalar(Scalar::Int32) => "Int",
        TypeData::Scalar(Scalar::Float64) => "Double",
        TypeData::Scalar(Scalar::Boolean) => "Bool",
        _ => "Obj",
    }
}

/// A list's item type.
fn item_of(t: &Translator<'_>, list: NodeId) -> TypeId {
    let p = t.program();
    p.types
        .args(p.nodes.ty(list))
        .first()
        .copied()
        .unwrap_or(TypeId::NONE)
}

/// A function value's result type.
fn result_of(t: &Translator<'_>, f: NodeId) -> TypeId {
    let p = t.program();
    (temper_tmpl::analysis::fun_signature(p, p.nodes.ty(f)))
        .and_then(|s| p.types.signature(s).map(|s| s.pass))
        .unwrap_or(TypeId::NONE)
}

/// A function value for a helper whose parameter's interface is `want`'s
/// (its simple name), or the value's own where `None`: adapted where they
/// differ, as `f::applyAsInt`, unless it's a lambda or method reference,
/// which Java types by where it goes.
fn fn_arg(t: &mut Translator<'_>, f: NodeId, want: Option<&str>) -> Result<Expr, NotYet> {
    let e = t.expr(f)?;
    let Some(want) = want else {
        return Ok(e);
    };
    if matches!(e, Expr::Lambda(..) | Expr::MethodRef(..)) {
        return Ok(e);
    }
    let have = t.sam(t.node_ty(f))?;
    Ok(if have.class == want {
        e
    } else {
        Expr::MethodRef(Box::new(e), have.method.to_string())
    })
}

/// `Core.NAME(args)`, imported.
fn core_call(t: &mut Translator<'_>, name: String, args: Vec<Expr>) -> Expr {
    t.import("temper.core.Core");
    Expr::name("Core").method(name, args)
}

fn list_and_fn(args: &[NodeId], what: &str) -> Result<(NodeId, NodeId), NotYet> {
    match args {
        [ls, f] => Ok((*ls, *f)),
        _ => Err(NotYet {
            what: format!("`{what}` with other than a list and a function"),
        }),
    }
}

/// `Core.listMapIntToObj(ls, f)` and the like, by the item's and the
/// result's kinds, as Kotlin's.
fn list_map(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let (ls, f) = list_and_fn(args, "map")?;
    let item = kind_of(t, item_of(t, ls));
    let item = if item == "Bool" { "Obj" } else { item };
    let result = kind_of(t, result_of(t, f));
    let list = t.expr(ls)?;
    let f = fn_arg(t, f, None)?;
    Ok(core_call(
        t,
        format!("listMap{item}To{result}"),
        vec![list, f],
    ))
}

/// `Core.listFilterInt(ls, f)` and the like, by the item's kind.
fn list_filter(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let (ls, f) = list_and_fn(args, "filter")?;
    let item = kind_of(t, item_of(t, ls));
    let list = t.expr(ls)?;
    let f = fn_arg(t, f, None)?;
    Ok(core_call(t, format!("listFilter{item}"), vec![list, f]))
}

/// `Core.listJoinInt(ls, separator, f)` and the like, by the item's kind.
fn list_join(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [ls, sep, f] = args else {
        return Err(NotYet {
            what: "`join` with other than a list, a separator, and a function".into(),
        });
    };
    let item = kind_of(t, item_of(t, *ls));
    let list = t.expr(*ls)?;
    let sep = t.expr(*sep)?;
    let f = fn_arg(t, *f, None)?;
    Ok(core_call(t, format!("listJoin{item}"), vec![list, sep, f]))
}

/// `Core.listSortedInt(ls, compare)` for `Int`s, else `Core.listSorted`,
/// whose comparator is a `ToIntBiFunction`.
fn list_sorted(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let (ls, f) = list_and_fn(args, "sorted")?;
    let int = kind_of(t, item_of(t, ls)) == "Int";
    let list = t.expr(ls)?;
    let f = fn_arg(t, f, (!int).then_some("ToIntBiFunction"))?;
    let name = if int { "listSortedInt" } else { "listSorted" };
    Ok(core_call(t, name.into(), vec![list, f]))
}

/// `Core.listSortInt(b, compare)` for `Int`s, else `Core.listSort`.
fn list_sort(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let (ls, f) = list_and_fn(args, "sort")?;
    let int = kind_of(t, item_of(t, ls)) == "Int";
    let list = t.expr(ls)?;
    let f = fn_arg(t, f, (!int).then_some("ToIntBiFunction"))?;
    let name = if int { "listSortInt" } else { "listSort" };
    Ok(core_call(t, name.into(), vec![list, f]))
}

/// `Core.listedReduceInt(ls, f)` and the like, by the item's kind; an
/// object's reducer is a `BinaryOperator`, a boolean's Core's
/// `BooleanBiPredicate`.
fn listed_reduce(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let (ls, f) = list_and_fn(args, "reduce")?;
    let item = kind_of(t, item_of(t, ls));
    let want = match item {
        "Obj" => Some("BinaryOperator"),
        "Bool" => Some("BooleanBiPredicate"),
        _ => None,
    };
    let list = t.expr(ls)?;
    let f = fn_arg(t, f, want)?;
    Ok(core_call(t, format!("listedReduce{item}"), vec![list, f]))
}

/// `Core.listedReduceIntToInt(ls, initial, f)` and the like, by the
/// item's and the initial value's kinds, as Kotlin's: where they're
/// different kinds, `f` is one of Core's reducers (`IntToObjReducer`);
/// two different object types have none.
fn listed_reduce_from(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [ls, initial, f] = args else {
        return Err(NotYet {
            what: "`reduceFrom` with other than a list, a value, and a function".into(),
        });
    };
    let item = kind_of(t, item_of(t, *ls));
    let to = kind_of(t, t.node_ty(*initial));
    if item == "Obj" && to == "Obj" && item_of(t, *ls) != t.node_ty(*initial) {
        return Err(NotYet {
            what: "`reduceFrom` to another object type than the items'".into(),
        });
    }
    let reducer = format!("{item}To{to}Reducer");
    let want = match (item, item == to) {
        (_, false) => Some(reducer.as_str()),
        ("Obj", true) => Some("BinaryOperator"),
        ("Bool", true) => Some("BooleanBiPredicate"),
        _ => None,
    };
    let list = t.expr(*ls)?;
    let initial = t.expr(*initial)?;
    let f = fn_arg(t, *f, want)?;
    Ok(core_call(
        t,
        format!("listedReduce{item}To{to}"),
        vec![list, initial, f],
    ))
}

/// `Core.mappedForEach(m, f)`.
fn mapped_for_each(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let (m, f) = list_and_fn(args, "forEach")?;
    let map = t.expr(m)?;
    let f = fn_arg(t, f, Some("BiConsumer"))?;
    Ok(core_call(t, "mappedForEach".into(), vec![map, f]))
}

/// `Core.mappedToListWith(m, f)`, with `f` a `BiFunction`, as Kotlin's.
fn mapped_to_list_with(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let (m, f) = list_and_fn(args, "toListWith")?;
    let map = t.expr(m)?;
    let f = fn_arg(t, f, Some("BiFunction"))?;
    Ok(core_call(t, "mappedToListWith".into(), vec![map, f]))
}

/// `Optional.empty()`, core's `Empty`, as Kotlin's.
/// `Core.runAsync(factory)`: queues an `async` block's first step on the
/// async runner, as Kotlin's.
fn run_async(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let factory = one(t, args, "async")?;
    t.import("temper.core.Core");
    Ok(Expr::name("Core").method("runAsync", vec![factory]))
}

/// `Core.awakeUpon(promise, generator)`: queues the generator's next step
/// on the async runner once the promise is resolved or broken, as Temper
/// PR #532 has Kotlin's do, so `complete` returns before the waiter runs.
fn awake_upon(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [promise, generator] = args else {
        return Err(NotYet {
            what: "`awakeUpon` without two arguments".into(),
        });
    };
    let promise = t.expr(*promise)?;
    let generator = t.expr(*generator)?;
    t.import("temper.core.Core");
    Ok(Expr::name("Core").method("awakeUpon", vec![promise, generator]))
}

/// `DoneResult.get()`, the runtime's one.
fn done_result(t: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    t.import("temper.core.Generator.DoneResult");
    Ok(Expr::name("DoneResult").method("get", Vec::new()))
}

/// `new ValueResult<>(value)`.
fn new_value_result(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let value = one(t, args, "ValueResult")?;
    t.import("temper.core.Generator.ValueResult");
    Ok(Expr::New("ValueResult<>".into(), vec![value]))
}

/// `Core.safeAdaptGeneratorFn(step::apply)`, a step function a generator.
fn safe_adapt_generator_fn(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let step = one(t, args, "safeAdaptGeneratorFn")?;
    t.import("temper.core.Core");
    let step = match step {
        Expr::Lambda(..) | Expr::MethodRef(..) => step,
        _ => Expr::MethodRef(Box::new(step), "apply".into()),
    };
    Ok(Expr::name("Core").method("safeAdaptGeneratorFn", vec![step]))
}

/// `Core.getPromiseResult(promise)`, which throws if it's broken.
fn get_promise_result(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let promise = one(t, args, "getPromiseResultSync")?;
    t.import("temper.core.Core");
    Ok(Expr::name("Core").method("getPromiseResult", vec![promise]))
}

/// `new CompletableFuture<>()`: a promise builder is its promise, as
/// Kotlin's.
fn new_completable_future(t: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    t.import("java.util.concurrent.CompletableFuture");
    Ok(Expr::New("CompletableFuture<>".into(), Vec::new()))
}

/// `b.completeExceptionally(Core.bubble())`, as Kotlin's.
fn break_promise(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let b = one(t, args, "breakPromise")?;
    t.import("temper.core.Core");
    let bubble = Expr::name("Core").method("bubble", Vec::new());
    Ok(b.method("completeExceptionally", vec![bubble]))
}

/// The receiver itself: a promise builder's promise.
fn identity(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    one(t, args, "promise")
}

fn empty(t: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    t.import("java.util.Optional");
    Ok(Expr::name("Optional").method("empty", Vec::new()))
}

/// `new BitSet(capacity)`, as Kotlin's.
fn new_bit_set(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let capacity = one(t, args, "DenseBitVector")?;
    t.import("java.util.BitSet");
    Ok(Expr::New("BitSet".into(), vec![capacity]))
}

/// `new ArrayDeque<>()`, or `new LinkedList<>()` where an item may be
/// null, which `ArrayDeque` rejects, as Kotlin's.
fn new_deque(t: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    let class = if t.result_items_may_be_null() {
        "LinkedList"
    } else {
        "ArrayDeque"
    };
    t.import(&format!("java.util.{class}"));
    Ok(Expr::New(format!("{class}<>"), Vec::new()))
}

/// `d.getDayOfWeek().getValue()`, ISO's numbering, as Kotlin's.
fn date_day_of_week(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [d] = args else {
        return Err(NotYet {
            what: "`Date.dayOfWeek` without its receiver".into(),
        });
    };
    let d = t.expr(*d)?;
    Ok(d.method("getDayOfWeek", Vec::new())
        .method("getValue", Vec::new()))
}

/// `LocalDate.now(ZoneOffset.UTC)`: today in UTC, as Kotlin's.
fn date_today(t: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    t.import("java.time.LocalDate");
    t.import("java.time.ZoneOffset");
    let utc = Expr::name("ZoneOffset").member("UTC");
    Ok(Expr::name("LocalDate").method("now", vec![utc]))
}

/// `(int) ChronoUnit.YEARS.between(start, end)`, as Kotlin's.
fn date_years_between(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [start, end] = args else {
        return Err(NotYet {
            what: "`Date.yearsBetween` without two arguments".into(),
        });
    };
    let start = t.expr(*start)?;
    let end = t.expr(*end)?;
    t.import("java.time.temporal.ChronoUnit");
    let years = Expr::name("ChronoUnit").member("YEARS");
    Ok(Expr::Cast(
        "int".into(),
        Box::new(years.method("between", vec![start, end])),
    ))
}

/// `Math.E`.
fn math_e(_: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(Expr::name("Math").member("E"))
}

/// `Math.PI`.
fn math_pi(_: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(Expr::name("Math").member("PI"))
}

/// `0`: a string index is a UTF-16 offset, as Kotlin's.
fn string_begin(_: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(Expr::Int(0))
}

/// `-1`, no string index, as Kotlin's.
fn string_index_none(_: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(Expr::Int(-1))
}

/// `a == b`: string indices are ints.
fn string_index_eq(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [a, b] = args else {
        return Err(NotYet {
            what: "a string index's `==` of other than two".into(),
        });
    };
    let (a, b) = (t.expr(*a)?, t.expr(*b)?);
    Ok(Expr::bin(Bin::Eq, a, b))
}

/// `new StringBuilder()`.
fn new_string_builder(_: &mut Translator<'_>, _: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(Expr::New("StringBuilder".into(), Vec::new()))
}

/// `b.setLength(0)`, as Kotlin's.
fn string_builder_clear(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let b = one(t, args, "clear")?;
    Ok(b.method("setLength", vec![Expr::Int(0)]))
}

/// std's connected keys this backend connects, sorted, as Kotlin's
/// Java support network (`JavaSupportNetwork.kt`) lists them; its other std
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
