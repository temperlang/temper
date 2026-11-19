//! The connection table: how core's connected declarations are written,
//! by qname, with Kotlin's strings as keys (notes/tmpl/names.md,
//! "Connected declarations"). Each entry matches what Kotlin's Python
//! backend gives for it (its `PySupportNetwork.kt`), or a prettier
//! equivalent: `str(n)` for `int_to_string(n)` with the radix left out.

use crate::tree::{Bin, Expr, Unary};

/// A custom translation's output: the expression.
pub type Translated = Expr;

/// Imports a name a custom translation needs, `temper_core`'s (`int_add`)
/// or another module's (`typing.cast`), giving the module's name for it
/// (`_int_add`).
pub type Import<'a> = &'a mut dyn FnMut(&str) -> String;

/// How a connected declaration is written. Arguments come translated, a
/// method's receiver first; `None` is an argument left out (`Omitted`).
#[derive(Clone, Copy)]
pub enum Connection {
    /// A call of a Python builtin, with the arguments given.
    Builtin(&'static str),
    /// A call of a method of the receiver.
    Method(&'static str),
    /// A call of a `temper_core` function, imported, with the arguments
    /// given.
    Runtime(&'static str),
    /// A call of a function another Python module has, imported from it:
    /// the module, then the name.
    Import(&'static str, &'static str),
    /// The receiver itself.
    Identity,
    /// An attribute of the receiver, as Kotlin's `inlineAttribute`.
    Attribute(&'static str),
    /// Custom output: the expression, with the `temper_core` names it
    /// needs, or another module's, as `typing.cast`, imported.
    Translate(fn(Vec<Option<Expr>>, Import) -> Translated),
}

/// Sorted by qname.
pub const TABLE: &[(&str, Connection)] = &[
    ("core.async()", Connection::Runtime("async_launch")),
    ("core.empty()", Connection::Translate(empty)),
    ("core.getConsole()", Connection::Translate(get_console)),
    (
        "core.type Boolean.toString()",
        Connection::Translate(boolean_to_string),
    ),
    ("core.type Console.log()", Connection::Method("log")),
    (
        "core.type DenseBitVector.constructor()",
        Connection::Runtime("DenseBitVector"),
    ),
    ("core.type DenseBitVector.get()", Connection::Method("get")),
    (
        "core.type DenseBitVector.set()",
        Connection::Runtime("dense_bit_vector_set"),
    ),
    ("core.type Deque.add()", Connection::Method("append")),
    (
        "core.type Deque.constructor()",
        Connection::Import("collections", "deque"),
    ),
    (
        "core.type Deque.get isEmpty()",
        Connection::Translate(is_empty),
    ),
    (
        "core.type Deque.removeFirst()",
        Connection::Runtime("deque_remove_first"),
    ),
    ("core.type Float64.abs()", Connection::Builtin("abs")),
    (
        "core.type Float64.acos()",
        Connection::Import("math", "acos"),
    ),
    (
        "core.type Float64.asin()",
        Connection::Import("math", "asin"),
    ),
    (
        "core.type Float64.atan()",
        Connection::Import("math", "atan"),
    ),
    (
        "core.type Float64.atan2()",
        Connection::Import("math", "atan2"),
    ),
    (
        "core.type Float64.ceil()",
        Connection::Import("math", "ceil"),
    ),
    ("core.type Float64.cos()", Connection::Import("math", "cos")),
    (
        "core.type Float64.cosh()",
        Connection::Import("math", "cosh"),
    ),
    ("core.type Float64.e", Connection::Translate(float64_e)),
    ("core.type Float64.exp()", Connection::Import("math", "exp")),
    (
        "core.type Float64.expm1()",
        Connection::Import("math", "expm1"),
    ),
    (
        "core.type Float64.floor()",
        Connection::Import("math", "floor"),
    ),
    ("core.type Float64.log()", Connection::Import("math", "log")),
    (
        "core.type Float64.log10()",
        Connection::Import("math", "log10"),
    ),
    (
        "core.type Float64.log1p()",
        Connection::Import("math", "log1p"),
    ),
    (
        "core.type Float64.max()",
        Connection::Runtime("float64_max"),
    ),
    (
        "core.type Float64.min()",
        Connection::Runtime("float64_min"),
    ),
    (
        "core.type Float64.near()",
        Connection::Translate(float64_near),
    ),
    ("core.type Float64.pi", Connection::Translate(float64_pi)),
    ("core.type Float64.round()", Connection::Builtin("round")),
    (
        "core.type Float64.sign()",
        Connection::Runtime("float64_sign"),
    ),
    ("core.type Float64.sin()", Connection::Import("math", "sin")),
    (
        "core.type Float64.sinh()",
        Connection::Import("math", "sinh"),
    ),
    (
        "core.type Float64.sqrt()",
        Connection::Import("math", "sqrt"),
    ),
    ("core.type Float64.tan()", Connection::Import("math", "tan")),
    (
        "core.type Float64.tanh()",
        Connection::Import("math", "tanh"),
    ),
    (
        "core.type Float64.toInt32()",
        Connection::Runtime("float64_to_int"),
    ),
    (
        "core.type Float64.toInt32Unsafe()",
        Connection::Runtime("float64_to_int_unsafe"),
    ),
    (
        "core.type Float64.toInt64()",
        Connection::Runtime("float64_to_int64"),
    ),
    (
        "core.type Float64.toInt64Unsafe()",
        Connection::Runtime("float64_to_int64_unsafe"),
    ),
    (
        "core.type Float64.toString()",
        Connection::Runtime("float64_to_string"),
    ),
    ("core.type Int32.max()", Connection::Builtin("max")),
    ("core.type Int32.min()", Connection::Builtin("min")),
    ("core.type Int32.toFloat64()", Connection::Builtin("float")),
    ("core.type Int32.toInt64()", Connection::Identity),
    (
        "core.type Int32.toString()",
        Connection::Translate(int_to_string),
    ),
    ("core.type Int64.max()", Connection::Builtin("max")),
    ("core.type Int64.min()", Connection::Builtin("min")),
    (
        "core.type Int64.toFloat64()",
        Connection::Runtime("int64_to_float64"),
    ),
    (
        "core.type Int64.toFloat64Unsafe()",
        Connection::Builtin("float"),
    ),
    (
        "core.type Int64.toInt32()",
        Connection::Runtime("int64_to_int32"),
    ),
    (
        "core.type Int64.toInt32Unsafe()",
        Connection::Runtime("int64_to_int32_unsafe"),
    ),
    (
        "core.type Int64.toString()",
        Connection::Translate(int_to_string),
    ),
    ("core.type List.get length()", Connection::Builtin("len")),
    ("core.type List.get()", Connection::Runtime("list_get")),
    ("core.type List.toList()", Connection::Identity),
    (
        "core.type List.toListBuilder()",
        Connection::Builtin("list"),
    ),
    (
        "core.type ListBuilder.add()",
        Connection::Translate(list_builder_add),
    ),
    (
        "core.type ListBuilder.addAll()",
        Connection::Runtime("list_builder_add_all"),
    ),
    ("core.type ListBuilder.clear()", Connection::Method("clear")),
    (
        "core.type ListBuilder.constructor()",
        Connection::Translate(empty_list),
    ),
    (
        "core.type ListBuilder.get length()",
        Connection::Builtin("len"),
    ),
    (
        "core.type ListBuilder.removeLast()",
        Connection::Runtime("list_builder_remove_last"),
    ),
    (
        "core.type ListBuilder.reverse()",
        Connection::Method("reverse"),
    ),
    (
        "core.type ListBuilder.set()",
        Connection::Runtime("list_builder_set"),
    ),
    (
        "core.type ListBuilder.sort()",
        Connection::Translate(list_builder_sort),
    ),
    (
        "core.type ListBuilder.splice()",
        Connection::Runtime("list_builder_splice"),
    ),
    (
        "core.type ListBuilder.toList()",
        Connection::Builtin("tuple"),
    ),
    (
        "core.type ListBuilder.toListBuilder()",
        Connection::Builtin("list"),
    ),
    (
        "core.type Listed.filter()",
        Connection::Runtime("list_filter"),
    ),
    (
        "core.type Listed.get isEmpty()",
        Connection::Translate(is_empty),
    ),
    ("core.type Listed.get length()", Connection::Builtin("len")),
    ("core.type Listed.get()", Connection::Runtime("list_get")),
    (
        "core.type Listed.getOr()",
        Connection::Runtime("list_get_or"),
    ),
    ("core.type Listed.join()", Connection::Runtime("list_join")),
    ("core.type Listed.map()", Connection::Runtime("list_map")),
    (
        "core.type Listed.reduce()",
        Connection::Runtime("listed_reduce"),
    ),
    (
        "core.type Listed.reduceFrom()",
        Connection::Runtime("listed_reduce_from"),
    ),
    (
        "core.type Listed.slice()",
        Connection::Runtime("list_slice"),
    ),
    (
        "core.type Listed.sorted()",
        Connection::Runtime("listed_sorted"),
    ),
    (
        "core.type Listed.toList()",
        Connection::Runtime("listed_to_list"),
    ),
    (
        "core.type Listed.toListBuilder()",
        Connection::Builtin("list"),
    ),
    (
        "core.type Map.constructor()",
        Connection::Runtime("map_constructor"),
    ),
    ("core.type MapBuilder.clear()", Connection::Method("clear")),
    (
        "core.type MapBuilder.constructor()",
        Connection::Translate(empty_dict),
    ),
    (
        "core.type MapBuilder.remove()",
        Connection::Runtime("map_builder_remove"),
    ),
    (
        "core.type MapBuilder.set()",
        Connection::Runtime("map_builder_set"),
    ),
    (
        "core.type Mapped.forEach()",
        Connection::Runtime("mapped_for_each"),
    ),
    ("core.type Mapped.get length()", Connection::Builtin("len")),
    ("core.type Mapped.get()", Connection::Runtime("mapped_get")),
    ("core.type Mapped.getOr()", Connection::Method("get")),
    ("core.type Mapped.has()", Connection::Runtime("mapped_has")),
    (
        "core.type Mapped.keys()",
        Connection::Translate(mapped_keys),
    ),
    (
        "core.type Mapped.toList()",
        Connection::Runtime("mapped_to_list"),
    ),
    (
        "core.type Mapped.toListBuilder()",
        Connection::Runtime("mapped_to_list_builder"),
    ),
    (
        "core.type Mapped.toListWith()",
        Connection::Runtime("mapped_to_list_with"),
    ),
    (
        "core.type Mapped.toMap()",
        Connection::Runtime("mapped_to_map"),
    ),
    (
        "core.type Mapped.toMapBuilder()",
        Connection::Builtin("dict"),
    ),
    (
        "core.type Mapped.values()",
        Connection::Translate(mapped_values),
    ),
    ("core.type Pair.constructor()", Connection::Runtime("Pair")),
    (
        "core.type PromiseBuilder.breakPromise()",
        Connection::Runtime("break_promise"),
    ),
    (
        "core.type PromiseBuilder.complete()",
        Connection::Runtime("complete_promise"),
    ),
    (
        "core.type PromiseBuilder.constructor()",
        Connection::Runtime("new_unbound_promise"),
    ),
    (
        "core.type PromiseBuilder.get promise()",
        Connection::Identity,
    ),
    (
        "core.type SafeGenerator.nextSafe()",
        Connection::Translate(next_safe),
    ),
    (
        "core.type String.begin",
        Connection::Translate(string_begin),
    ),
    (
        "core.type String.countBetween()",
        Connection::Runtime("string_count_between"),
    ),
    (
        "core.type String.fromCodePoint()",
        Connection::Runtime("string_from_code_point"),
    ),
    (
        "core.type String.fromCodePoints()",
        Connection::Runtime("string_from_code_points"),
    ),
    ("core.type String.get end()", Connection::Builtin("len")),
    (
        "core.type String.get isEmpty()",
        Connection::Translate(is_empty),
    ),
    ("core.type String.get()", Connection::Runtime("string_get")),
    (
        "core.type String.hasAtLeast()",
        Connection::Runtime("string_has_at_least"),
    ),
    (
        "core.type String.hasIndex()",
        Connection::Translate(string_has_index),
    ),
    ("core.type String.indexOf()", Connection::Method("find")),
    (
        "core.type String.next()",
        Connection::Runtime("string_next"),
    ),
    (
        "core.type String.prev()",
        Connection::Runtime("string_prev"),
    ),
    (
        "core.type String.slice()",
        Connection::Translate(string_slice),
    ),
    (
        "core.type String.split()",
        Connection::Runtime("string_split"),
    ),
    (
        "core.type String.step()",
        Connection::Runtime("string_step"),
    ),
    (
        "core.type String.toFloat64()",
        Connection::Runtime("string_to_float64"),
    ),
    (
        "core.type String.toInt32()",
        Connection::Runtime("string_to_int32"),
    ),
    (
        "core.type String.toInt64()",
        Connection::Runtime("string_to_int64"),
    ),
    ("core.type String.toString()", Connection::Identity),
    (
        "core.type StringBuilder.append()",
        Connection::Method("append"),
    ),
    (
        "core.type StringBuilder.appendBetween()",
        Connection::Translate(string_builder_append_between),
    ),
    (
        "core.type StringBuilder.appendCodePoint()",
        Connection::Translate(string_builder_append_code_point),
    ),
    (
        "core.type StringBuilder.clear()",
        Connection::Method("clear"),
    ),
    (
        "core.type StringBuilder.constructor()",
        Connection::Translate(string_builder),
    ),
    (
        "core.type StringBuilder.get end()",
        Connection::Runtime("string_builder_end"),
    ),
    (
        "core.type StringBuilder.toString()",
        Connection::Translate(string_builder_to_string),
    ),
    (
        "core.type StringIndex.none",
        Connection::Translate(string_index_none),
    ),
    (
        "core.type StringIndexOption.compareTo()",
        Connection::Translate(string_index_compare_to),
    ),
    (
        "core.type StringIndexOption.eq()",
        Connection::Translate(string_index_eq),
    ),
    ("std/net.sendRequest()", Connection::Runtime("std_net_send")),
    (
        "std/net.type NetResponse.get bodyContent()",
        Connection::Attribute("text"),
    ),
    (
        "std/net.type NetResponse.get contentType()",
        Connection::Attribute("content_type"),
    ),
    (
        "std/net.type NetResponse.get status()",
        Connection::Attribute("status"),
    ),
    (
        "std/regex.type Regex.compiledFind()",
        Connection::Import("temper_core.regex", "regex_compiled_find"),
    ),
    (
        "std/regex.type Regex.compiledFound()",
        Connection::Import("temper_core.regex", "regex_compiled_found"),
    ),
    (
        "std/regex.type Regex.compiledReplace()",
        Connection::Import("temper_core.regex", "regex_compiled_replace"),
    ),
    (
        "std/regex.type Regex.compiledSplit()",
        Connection::Import("temper_core.regex", "regex_compiled_split"),
    ),
    (
        "std/regex.type RegexFormatter.pushCaptureName()",
        Connection::Import("temper_core.regex", "regex_formatter_push_capture_name"),
    ),
    (
        "std/regex.type RegexFormatter.pushCodeTo()",
        Connection::Import("temper_core.regex", "regex_formatter_push_code_to"),
    ),
    (
        "std/regex.type RegexFormatter.regexCompileFormatted()",
        Connection::Import("temper_core.regex", "regex_compile_formatted"),
    ),
    (
        "std/temporal.type Date.constructor()",
        Connection::Runtime("date_of"),
    ),
    (
        "std/temporal.type Date.fromIsoString()",
        Connection::Runtime("date_from_iso_string"),
    ),
    (
        "std/temporal.type Date.get dayOfWeek()",
        Connection::Method("isoweekday"),
    ),
    (
        "std/temporal.type Date.toString()",
        Connection::Runtime("date_to_string"),
    ),
    (
        "std/temporal.type Date.today()",
        Connection::Runtime("date_today"),
    ),
    (
        "std/temporal.type Date.yearsBetween()",
        Connection::Runtime("years_between"),
    ),
    (
        "std/testing.type Test.panics()",
        Connection::Translate(test_panics),
    ),
];

/// Where Python finds the type a std class this backend connects is, and
/// its name there, as Kotlin's (`PySupportNetwork.kt`).
pub fn std_type(key: &str) -> Option<(&'static str, &'static str)> {
    match key {
        "std/net.type NetResponse" => Some(("temper_core", "NetResponse")),
        "std/temporal.type Date" => Some(("datetime", "date")),
        _ => None,
    }
}

/// The connection for a qname.
pub fn lookup(qname: &str) -> Option<Connection> {
    TABLE
        .binary_search_by_key(&qname, |(k, _)| k)
        .ok()
        .map(|i| TABLE[i].1)
}

fn arg(args: &mut [Option<Expr>], i: usize) -> Option<Expr> {
    args.get_mut(i).and_then(Option::take)
}

/// `LoggingConsole(__name__)`, a logger named for the module, as Kotlin's
/// `GetConsole`.
fn get_console(mut args: Vec<Option<Expr>>, im: Import) -> Translated {
    let name = arg(&mut args, 0).unwrap_or(Expr::name("__name__"));
    Expr::name(im("LoggingConsole")).call(vec![name])
}

/// `boolean_to_string(b)`, as Kotlin's: `true` or `false`, where `str`
/// gives `True` or `False`.
fn boolean_to_string(mut args: Vec<Option<Expr>>, im: Import) -> Translated {
    let b = arg(&mut args, 0).unwrap_or(Expr::None);
    Expr::name(im("boolean_to_string")).call(vec![b])
}

/// `block_panics(block)`, the runtime's, which catches a panic, a plain
/// `BaseException`, alone, for std's `Test.assertPanics` (not named
/// `test_…`, which pytest would take for a test).
fn test_panics(mut args: Vec<Option<Expr>>, im: Import) -> Translated {
    let block = arg(&mut args, 1).unwrap_or(Expr::None);
    Expr::name(im("block_panics")).call(vec![block])
}

/// `str(n)` without a radix, else `int_to_string(n, radix)`.
fn int_to_string(mut args: Vec<Option<Expr>>, im: Import) -> Translated {
    let n = arg(&mut args, 0).unwrap_or(Expr::None);
    match arg(&mut args, 1) {
        None => Expr::builtin("str").call(vec![n]),
        Some(radix) => Expr::name(im("int_to_string")).call(vec![n, radix]),
    }
}

/// `next(map(lambda v: (v,), g), None)`: a step's value as a 1-tuple, or
/// `None` once the generator is done, as Kotlin annotates
/// `GeneratorResult`, with the generator itself yielding bare values for
/// Python's own loops. A bare `next`, as Kotlin's, raises `StopIteration`
/// once the generator is done, where `nextSafe` says it's done.
fn next_safe(mut args: Vec<Option<Expr>>, _: Import) -> Translated {
    let g = arg(&mut args, 0).unwrap_or(Expr::None);
    let one = Expr::Lambda(
        vec!["v".into()],
        Box::new(Expr::Tuple(vec![Expr::name("v")])),
    );
    let wrapped = Expr::builtin("map").call(vec![one, g]);
    Expr::builtin("next").call(vec![wrapped, Expr::None])
}

/// `not s`, as Kotlin's `GenericIsEmpty`.
fn is_empty(mut args: Vec<Option<Expr>>, _: Import) -> Translated {
    let s = arg(&mut args, 0).unwrap_or(Expr::None);
    Expr::unary(Unary::Not, s)
}

/// `[]`, an empty `ListBuilder`.
fn empty_list(_: Vec<Option<Expr>>, _: Import) -> Translated {
    Expr::List(Vec::new())
}

/// `{}`, an empty `MapBuilder`.
fn empty_dict(_: Vec<Option<Expr>>, _: Import) -> Translated {
    Expr::EmptyDict
}

/// `list_builder_sort(cast('list[Any]', b), compare)`: the runtime's
/// annotation takes a `list`, which a `ListBuilder`, annotated
/// `MutableSequence`, isn't to mypy (a Kotlin bug: its output fails `mypy
/// --strict` there).
fn list_builder_sort(args: Vec<Option<Expr>>, im: Import) -> Translated {
    let mut args = args.into_iter().flatten();
    let (b, compare) = (
        args.next().unwrap_or(Expr::None),
        args.next().unwrap_or(Expr::None),
    );
    let any = im("typing.Any");
    let ty = Expr::Type(crate::tree::string_literal(&format!("list[{any}]")));
    let b = Expr::name(im("typing.cast")).call(vec![ty, b]);
    Expr::name(im("list_builder_sort")).call(vec![b, compare])
}

/// `b.append(x)`, or `list_builder_add(b, x, at)` with a place, as
/// Kotlin's.
fn list_builder_add(mut args: Vec<Option<Expr>>, im: Import) -> Translated {
    let b = arg(&mut args, 0).unwrap_or(Expr::None);
    let x = arg(&mut args, 1).unwrap_or(Expr::None);
    match arg(&mut args, 2) {
        None => b.attr("append").call(vec![x]),
        Some(at) => Expr::name(im("list_builder_add")).call(vec![b, x, at]),
    }
}

/// `list(m.keys())`, as Kotlin's.
fn mapped_keys(mut args: Vec<Option<Expr>>, _: Import) -> Translated {
    let m = arg(&mut args, 0).unwrap_or(Expr::None);
    Expr::builtin("list").call(vec![m.attr("keys").call(Vec::new())])
}

/// `list(m.values())`, as Kotlin's.
fn mapped_values(mut args: Vec<Option<Expr>>, _: Import) -> Translated {
    let m = arg(&mut args, 0).unwrap_or(Expr::None);
    Expr::builtin("list").call(vec![m.attr("values").call(Vec::new())])
}

/// `()`, core's `Empty`, as Kotlin's.
fn empty(_: Vec<Option<Expr>>, _: Import) -> Translated {
    Expr::Tuple(Vec::new())
}

/// `math.e`, imported.
fn float64_e(_: Vec<Option<Expr>>, im: Import) -> Translated {
    Expr::name(im("math.e"))
}

/// `math.pi`, imported.
fn float64_pi(_: Vec<Option<Expr>>, im: Import) -> Translated {
    Expr::name(im("math.pi"))
}

/// `float64_near(x, y, rel_tol, abs_tol)`: `None` for a tolerance left
/// out before one given, as Kotlin's, and none after.
fn float64_near(mut args: Vec<Option<Expr>>, im: Import) -> Translated {
    while args.last().is_some_and(Option::is_none) {
        args.pop();
    }
    let args = args.into_iter().map(|a| a.unwrap_or(Expr::None)).collect();
    Expr::name(im("float64_near")).call(args)
}

/// `0`: string indices are code point offsets, as Kotlin's.
fn string_begin(_: Vec<Option<Expr>>, _: Import) -> Translated {
    Expr::Int(0)
}

/// `-1`, no string index, as Kotlin's.
fn string_index_none(_: Vec<Option<Expr>>, _: Import) -> Translated {
    Expr::Int(-1)
}

/// `len(s) > i`, as Kotlin's.
fn string_has_index(mut args: Vec<Option<Expr>>, _: Import) -> Translated {
    let s = arg(&mut args, 0).unwrap_or(Expr::None);
    let i = arg(&mut args, 1).unwrap_or(Expr::None);
    Expr::bin(Bin::Gt, Expr::builtin("len").call(vec![s]), i)
}

/// `s[begin : end]`.
fn string_slice(mut args: Vec<Option<Expr>>, _: Import) -> Translated {
    let s = arg(&mut args, 0).unwrap_or(Expr::None);
    let begin = arg(&mut args, 1).unwrap_or(Expr::None);
    let end = arg(&mut args, 2).unwrap_or(Expr::None);
    Expr::Slice(Box::new(s), Box::new(begin), Box::new(end))
}

/// `['']`, a `list[str]`, as Kotlin's.
fn string_builder(_: Vec<Option<Expr>>, _: Import) -> Translated {
    Expr::List(vec![Expr::Str(String::new())])
}

/// `''.join(b)`.
fn string_builder_to_string(mut args: Vec<Option<Expr>>, _: Import) -> Translated {
    let b = arg(&mut args, 0).unwrap_or(Expr::None);
    Expr::Str(String::new()).attr("join").call(vec![b])
}

/// `b.append(s[begin : end])`, as Kotlin's.
fn string_builder_append_between(mut args: Vec<Option<Expr>>, im: Import) -> Translated {
    let b = arg(&mut args, 0).unwrap_or(Expr::None);
    let slice = string_slice(args.split_off(1), im);
    b.attr("append").call(vec![slice])
}

/// `b.append(string_from_code_point(c))`, as Kotlin's.
fn string_builder_append_code_point(mut args: Vec<Option<Expr>>, im: Import) -> Translated {
    let b = arg(&mut args, 0).unwrap_or(Expr::None);
    let c = arg(&mut args, 1).unwrap_or(Expr::None);
    let c = Expr::name(im("string_from_code_point")).call(vec![c]);
    b.attr("append").call(vec![c])
}

/// `a - b`: string indices, and no index, are ints, as Kotlin's.
fn string_index_compare_to(mut args: Vec<Option<Expr>>, _: Import) -> Translated {
    let a = arg(&mut args, 0).unwrap_or(Expr::None);
    let b = arg(&mut args, 1).unwrap_or(Expr::None);
    Expr::bin(Bin::Sub, a, b)
}

/// `a == b`.
fn string_index_eq(mut args: Vec<Option<Expr>>, _: Import) -> Translated {
    let a = arg(&mut args, 0).unwrap_or(Expr::None);
    let b = arg(&mut args, 1).unwrap_or(Expr::None);
    Expr::bin(Bin::Eq, a, b)
}

/// std's connected keys this backend connects, sorted, as Kotlin's
/// Python support network (`PySupportNetwork.kt`) lists them; its other std
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
    "std/regex.type RegexFormatter.pushCaptureName()",
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
