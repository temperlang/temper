//! The connection tables: how core's connected declarations and types are
//! written, by qname, with Kotlin's strings as keys (notes/tmpl/names.md,
//! "Connected declarations"). Each entry gives what Kotlin's C++ backend
//! gives for it (its `CppSupportNetwork.kt`), or a plainer equivalent:
//! `isEmpty` is `s.empty()` rather than `core::String::isEmpty(s)`, and
//! `Int32`'s `toString()`, `min`, and `max` are `std::to_string`,
//! `std::min`, and `std::max` rather than the runtime's wrappers of them.

use crate::NotYet;
use crate::translate::Translator;
use crate::tree::{Bin, Expr};
use temper_tmpl::{Node, NodeId};

/// Custom output: from the call's argument nodes, a method's receiver
/// first, and `Omitted` for an argument left out.
pub type Hook = fn(&mut Translator<'_>, &[NodeId]) -> Result<Expr, NotYet>;

/// How a connected declaration is written.
#[derive(Clone, Copy)]
pub enum Connection {
    /// A function, given the arguments, a method's receiver first: of
    /// the runtime's, relative to `temper::core` (`Console::log`), or of
    /// the standard library's, qualified (`std::min`).
    Call(&'static str),
    /// A function template of the runtime's, given its receiver's type
    /// arguments, which C++ can't always deduce (a list of
    /// `core::Str`s and a concatenation's `std::string`), then the
    /// arguments, the receiver first: `core::List::getOr<core::Str>(built,
    /// 5, TEMPER_STR("?"))`.
    Templated(&'static str),
    /// A function template of the runtime's that makes the call's value,
    /// given that type's arguments: `core::Map::make<core::Str,
    /// int32_t>(entries)`.
    Making(&'static str),
    /// A function template of the runtime's given the class the call
    /// makes, then the arguments: `core::Date::makeDate<Date>(y, m, d)`,
    /// as Kotlin's for std's `Date`.
    Made(&'static str),
    /// A static function of the runtime's class template that the call
    /// makes, given the arguments: `core::ValueResult<T>::make(v)`.
    Static(&'static str, &'static str),
    /// Custom output.
    Translate(Hook),
}

/// Sorted by qname.
pub const TABLE: &[(&str, Connection)] = &[
    ("core.async()", Connection::Call("async_run")),
    ("core.awakeUpon()", Connection::Call("awake_upon")),
    ("core.doneResult()", Connection::Call("doneResult")),
    ("core.empty()", Connection::Call("empty")),
    ("core.getConsole()", Connection::Translate(get_console)),
    (
        "core.getPromiseResultSync()",
        Connection::Call("get_promise_result_sync"),
    ),
    (
        "core.safeAdaptGeneratorFn()",
        Connection::Making("safe_adapt_generator_fn"),
    ),
    (
        "core.type Boolean.toString()",
        Connection::Call("Boolean::toString"),
    ),
    ("core.type Console.log()", Connection::Call("Console::log")),
    (
        "core.type DenseBitVector.constructor()",
        Connection::Call("DenseBitVector::make"),
    ),
    (
        "core.type DenseBitVector.get()",
        Connection::Call("DenseBitVector::get"),
    ),
    (
        "core.type DenseBitVector.set()",
        Connection::Call("DenseBitVector::set"),
    ),
    ("core.type Deque.add()", Connection::Templated("Deque::add")),
    (
        "core.type Deque.constructor()",
        Connection::Making("Deque::make"),
    ),
    (
        "core.type Deque.get isEmpty()",
        Connection::Templated("Deque::isEmpty"),
    ),
    (
        "core.type Deque.removeFirst()",
        Connection::Templated("Deque::removeFirst"),
    ),
    // A declared failure's cause and message, `core::Failure`'s.
    (
        "core.type Error.get cause()",
        Connection::Translate(failure_cause),
    ),
    (
        "core.type Error.get message()",
        Connection::Translate(failure_message),
    ),
    ("core.type Float64.abs()", Connection::Call("Float64::abs")),
    (
        "core.type Float64.acos()",
        Connection::Call("Float64::acos"),
    ),
    (
        "core.type Float64.asin()",
        Connection::Call("Float64::asin"),
    ),
    (
        "core.type Float64.atan()",
        Connection::Call("Float64::atan"),
    ),
    (
        "core.type Float64.atan2()",
        Connection::Call("Float64::atan2"),
    ),
    (
        "core.type Float64.ceil()",
        Connection::Call("Float64::ceil"),
    ),
    ("core.type Float64.cos()", Connection::Call("Float64::cos")),
    (
        "core.type Float64.cosh()",
        Connection::Call("Float64::cosh"),
    ),
    ("core.type Float64.e", Connection::Call("Float64::e")),
    ("core.type Float64.exp()", Connection::Call("Float64::exp")),
    (
        "core.type Float64.expm1()",
        Connection::Call("Float64::expm1"),
    ),
    (
        "core.type Float64.floor()",
        Connection::Call("Float64::floor"),
    ),
    ("core.type Float64.log()", Connection::Call("Float64::log")),
    (
        "core.type Float64.log10()",
        Connection::Call("Float64::log10"),
    ),
    (
        "core.type Float64.log1p()",
        Connection::Call("Float64::log1p"),
    ),
    ("core.type Float64.max()", Connection::Call("Float64::max")),
    ("core.type Float64.min()", Connection::Call("Float64::min")),
    (
        "core.type Float64.near()",
        Connection::Translate(float64_near),
    ),
    ("core.type Float64.pi", Connection::Call("Float64::pi")),
    (
        "core.type Float64.round()",
        Connection::Call("Float64::round"),
    ),
    (
        "core.type Float64.sign()",
        Connection::Call("Float64::sign"),
    ),
    ("core.type Float64.sin()", Connection::Call("Float64::sin")),
    (
        "core.type Float64.sinh()",
        Connection::Call("Float64::sinh"),
    ),
    (
        "core.type Float64.sqrt()",
        Connection::Call("Float64::sqrt"),
    ),
    ("core.type Float64.tan()", Connection::Call("Float64::tan")),
    (
        "core.type Float64.tanh()",
        Connection::Call("Float64::tanh"),
    ),
    (
        "core.type Float64.toInt32()",
        Connection::Call("Float64::toInt32"),
    ),
    (
        "core.type Float64.toInt32Unsafe()",
        Connection::Call("Float64::toInt32Unsafe"),
    ),
    (
        "core.type Float64.toInt64()",
        Connection::Call("Float64::toInt64"),
    ),
    (
        "core.type Float64.toInt64Unsafe()",
        Connection::Call("Float64::toInt64Unsafe"),
    ),
    (
        "core.type Float64.toString()",
        Connection::Call("Float64::toString"),
    ),
    ("core.type Int32.max()", Connection::Call("std::max")),
    ("core.type Int32.min()", Connection::Call("std::min")),
    (
        "core.type Int32.toFloat64()",
        Connection::Call("Int::toFloat64"),
    ),
    (
        "core.type Int32.toInt64()",
        Connection::Call("Int::toInt64"),
    ),
    (
        "core.type Int32.toString()",
        Connection::Translate(int_to_string),
    ),
    ("core.type Int64.max()", Connection::Call("std::max")),
    ("core.type Int64.min()", Connection::Call("std::min")),
    (
        "core.type Int64.toFloat64()",
        Connection::Call("Int64::toFloat64"),
    ),
    (
        "core.type Int64.toFloat64Unsafe()",
        Connection::Call("Int64::toFloat64Unsafe"),
    ),
    (
        "core.type Int64.toInt32()",
        Connection::Call("Int64::toInt32"),
    ),
    (
        "core.type Int64.toInt32Unsafe()",
        Connection::Call("Int64::toInt32Unsafe"),
    ),
    (
        "core.type Int64.toString()",
        Connection::Translate(int64_to_string),
    ),
    (
        "core.type InvalidCodePoint.constructor()",
        Connection::Static("InvalidCodePoint", "make"),
    ),
    (
        "core.type List.get length()",
        Connection::Templated("List::length"),
    ),
    ("core.type List.get()", Connection::Templated("List::get")),
    ("core.type List.toList()", Connection::Translate(same)),
    (
        "core.type List.toListBuilder()",
        Connection::Templated("List::toListBuilder"),
    ),
    (
        "core.type ListBuilder.add()",
        Connection::Templated("ListBuilder::add"),
    ),
    (
        "core.type ListBuilder.addAll()",
        Connection::Templated("ListBuilder::addAll"),
    ),
    (
        "core.type ListBuilder.clear()",
        Connection::Templated("ListBuilder::clear"),
    ),
    (
        "core.type ListBuilder.constructor()",
        Connection::Making("ListBuilder::make"),
    ),
    (
        "core.type ListBuilder.get length()",
        Connection::Templated("List::length"),
    ),
    (
        "core.type ListBuilder.removeLast()",
        Connection::Templated("ListBuilder::removeLast"),
    ),
    (
        "core.type ListBuilder.reverse()",
        Connection::Templated("ListBuilder::reverse"),
    ),
    (
        "core.type ListBuilder.set()",
        Connection::Templated("ListBuilder::set"),
    ),
    (
        "core.type ListBuilder.sort()",
        Connection::Call("ListBuilder::sort"),
    ),
    (
        "core.type ListBuilder.splice()",
        Connection::Templated("ListBuilder::splice"),
    ),
    (
        "core.type ListBuilder.toList()",
        Connection::Templated("List::toList"),
    ),
    (
        "core.type ListBuilder.toListBuilder()",
        Connection::Templated("List::toListBuilder"),
    ),
    (
        "core.type Listed.filter()",
        Connection::Call("List::filter"),
    ),
    (
        "core.type Listed.get isEmpty()",
        Connection::Templated("List::isEmpty"),
    ),
    (
        "core.type Listed.get length()",
        Connection::Templated("List::length"),
    ),
    ("core.type Listed.get()", Connection::Templated("List::get")),
    (
        "core.type Listed.getOr()",
        Connection::Translate(listed_get_or),
    ),
    ("core.type Listed.join()", Connection::Call("List::join")),
    ("core.type Listed.map()", Connection::Call("List::map")),
    (
        "core.type Listed.reduce()",
        Connection::Call("List::reduce"),
    ),
    (
        "core.type Listed.reduceFrom()",
        Connection::Call("List::reduceFrom"),
    ),
    (
        "core.type Listed.slice()",
        Connection::Templated("List::slice"),
    ),
    (
        "core.type Listed.sorted()",
        Connection::Call("List::sorted"),
    ),
    (
        "core.type Listed.toList()",
        Connection::Templated("List::toList"),
    ),
    (
        "core.type Listed.toListBuilder()",
        Connection::Templated("List::toListBuilder"),
    ),
    (
        "core.type Map.constructor()",
        Connection::Making("Map::make"),
    ),
    (
        "core.type MapBuilder.clear()",
        Connection::Templated("MapBuilder::clear"),
    ),
    (
        "core.type MapBuilder.constructor()",
        Connection::Making("Map::make"),
    ),
    (
        "core.type MapBuilder.remove()",
        Connection::Templated("MapBuilder::remove"),
    ),
    (
        "core.type MapBuilder.set()",
        Connection::Templated("MapBuilder::set"),
    ),
    (
        "core.type Mapped.forEach()",
        Connection::Call("Mapped::forEach"),
    ),
    (
        "core.type Mapped.get length()",
        Connection::Templated("Mapped::length"),
    ),
    (
        "core.type Mapped.get()",
        Connection::Templated("Mapped::get"),
    ),
    (
        "core.type Mapped.getOr()",
        Connection::Translate(mapped_get_or),
    ),
    (
        "core.type Mapped.has()",
        Connection::Templated("Mapped::has"),
    ),
    (
        "core.type Mapped.keys()",
        Connection::Templated("Mapped::keys"),
    ),
    (
        "core.type Mapped.toList()",
        Connection::Templated("Mapped::toList"),
    ),
    (
        "core.type Mapped.toListWith()",
        Connection::Call("Mapped::toListWith"),
    ),
    (
        "core.type Mapped.toMap()",
        Connection::Templated("Mapped::toMap"),
    ),
    (
        "core.type Mapped.toMapBuilder()",
        Connection::Templated("Mapped::toMapBuilder"),
    ),
    (
        "core.type Mapped.values()",
        Connection::Templated("Mapped::values"),
    ),
    (
        "core.type MissingKey.constructor()",
        Connection::Static("MissingKey", "make"),
    ),
    (
        "core.type Pair.constructor()",
        Connection::Making("PairFactory::make"),
    ),
    (
        "core.type ParseError.constructor()",
        Connection::Static("ParseError", "make"),
    ),
    (
        "core.type PromiseBuilder.breakPromise()",
        Connection::Call("breakpromise"),
    ),
    (
        "core.type PromiseBuilder.complete()",
        Connection::Call("complete"),
    ),
    (
        "core.type PromiseBuilder.constructor()",
        Connection::Making("PromiseBuilderNs::make"),
    ),
    (
        "core.type PromiseBuilder.get promise()",
        Connection::Call("getpromise"),
    ),
    (
        "core.type SafeGenerator.nextSafe()",
        Connection::Call("next"),
    ),
    ("core.type String.begin", Connection::Call("String::begin")),
    (
        "core.type String.countBetween()",
        Connection::Call("String::countBetween"),
    ),
    (
        "core.type String.fromCodePoint()",
        Connection::Call("String::fromCodepoint"),
    ),
    (
        "core.type String.fromCodePoints()",
        Connection::Call("String::fromCodepoints"),
    ),
    (
        "core.type String.get end()",
        Connection::Call("String::end"),
    ),
    (
        "core.type String.get isEmpty()",
        Connection::Translate(is_empty),
    ),
    ("core.type String.get()", Connection::Call("String::get")),
    (
        "core.type String.hasAtLeast()",
        Connection::Call("String::hasAtLeast"),
    ),
    (
        "core.type String.hasIndex()",
        Connection::Call("String::hasIndex"),
    ),
    (
        "core.type String.indexOf()",
        Connection::Call("String::indexOf"),
    ),
    ("core.type String.next()", Connection::Call("String::next")),
    ("core.type String.prev()", Connection::Call("String::prev")),
    (
        "core.type String.slice()",
        Connection::Call("String::slice"),
    ),
    (
        "core.type String.split()",
        Connection::Call("String::split"),
    ),
    ("core.type String.step()", Connection::Call("String::step")),
    (
        "core.type String.toFloat64()",
        Connection::Call("String::toFloat64"),
    ),
    (
        "core.type String.toInt32()",
        Connection::Call("String::toInt32"),
    ),
    (
        "core.type String.toInt64()",
        Connection::Call("String::toInt64"),
    ),
    ("core.type String.toString()", Connection::Translate(same)),
    (
        "core.type StringBuilder.append()",
        Connection::Call("StringBuilder::append"),
    ),
    (
        "core.type StringBuilder.appendBetween()",
        Connection::Call("StringBuilder::appendBetween"),
    ),
    (
        "core.type StringBuilder.appendCodePoint()",
        Connection::Call("StringBuilder::appendCodepoint"),
    ),
    (
        "core.type StringBuilder.clear()",
        Connection::Call("StringBuilder::clear"),
    ),
    (
        "core.type StringBuilder.constructor()",
        Connection::Call("StringBuilder::make"),
    ),
    (
        "core.type StringBuilder.get end()",
        Connection::Call("StringBuilder::end"),
    ),
    (
        "core.type StringBuilder.toString()",
        Connection::Call("StringBuilder::toString"),
    ),
    (
        "core.type StringIndex.none",
        Connection::Call("String::none"),
    ),
    (
        "core.type StringIndexOption.compareTo()",
        Connection::Call("Compare::cmp"),
    ),
    (
        "core.type StringIndexOption.eq()",
        Connection::Translate(index_eq),
    ),
    (
        "core.type ValueResult.constructor()",
        Connection::Static("ValueResult", "make"),
    ),
    (
        "std/regex.type Regex.compiledFind()",
        Connection::Call("Regex::compiledFind"),
    ),
    (
        "std/regex.type Regex.compiledFound()",
        Connection::Call("Regex::compiledFound"),
    ),
    (
        "std/regex.type Regex.compiledReplace()",
        Connection::Call("Regex::compiledReplace"),
    ),
    (
        "std/regex.type Regex.compiledSplit()",
        Connection::Call("Regex::compiledSplit"),
    ),
    (
        "std/regex.type RegexFormatter.pushCaptureName()",
        Connection::Call("Regex::pushCaptureName"),
    ),
    (
        "std/regex.type RegexFormatter.pushCodeTo()",
        Connection::Call("Regex::pushCodeTo"),
    ),
    (
        "std/regex.type RegexFormatter.regexCompileFormatted()",
        Connection::Call("Regex::compileFormatted"),
    ),
    (
        "std/temporal.type Date.get dayOfWeek()",
        Connection::Translate(date_day_of_week),
    ),
    (
        "std/temporal.type Date.toString()",
        Connection::Call("Date::toString"),
    ),
    (
        "std/temporal.type Date.today()",
        Connection::Made("Date::today"),
    ),
    (
        "std/temporal.type Date.yearsBetween()",
        Connection::Call("Date::yearsBetween"),
    ),
    // The runtime's `testBail(test)`, as Kotlin's, which throws a
    // `TemperTestFailure`, where Kotlin's throws a bubble.
    ("std/testing.type Test.bail()", Connection::Call("testBail")),
    // The runtime's `testPanics(test, block)`, which catches a panic,
    // anything but a bubble or a test's failure.
    (
        "std/testing.type Test.panics()",
        Connection::Call("testPanics"),
    ),
];

/// How a connected type is written, relative to `temper::core` where it
/// starts with `core::`, which the header spells out.
/// Sorted by qname. String indices are `int32_t`, and the others the
/// runtime's, as Kotlin's.
pub const TYPES: &[(&str, &str)] = &[
    ("core.type Console", "std::shared_ptr<core::Console::Type>"),
    ("core.type CoreError", "std::shared_ptr<core::CoreError>"),
    (
        "core.type DenseBitVector",
        "std::shared_ptr<std::vector<bool>>",
    ),
    ("core.type Empty", "std::shared_ptr<core::Empty>"),
    ("core.type Error", "std::shared_ptr<core::Failure>"),
    (
        "core.type InvalidCodePoint",
        "std::shared_ptr<core::InvalidCodePoint>",
    ),
    ("core.type MissingKey", "std::shared_ptr<core::MissingKey>"),
    ("core.type NoStringIndex", "int32_t"),
    ("core.type ParseError", "std::shared_ptr<core::ParseError>"),
    (
        "core.type StringBuilder",
        "std::shared_ptr<std::ostringstream>",
    ),
    ("core.type StringIndex", "int32_t"),
    ("core.type StringIndexOption", "int32_t"),
];

/// The connection for a declaration's qname.
pub fn lookup(qname: &str) -> Option<Connection> {
    TABLE
        .binary_search_by_key(&qname, |(k, _)| k)
        .ok()
        .map(|i| TABLE[i].1)
}

/// The connection for a type's qname.
pub fn lookup_type(qname: &str) -> Option<&'static str> {
    TYPES
        .binary_search_by_key(&qname, |(k, _)| k)
        .ok()
        .map(|i| TYPES[i].1)
}

/// `core::Console::get_console()`, which logs to `std::cout`, as
/// Kotlin's; it takes no name.
fn get_console(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    match args {
        [] => {}
        [n] if matches!(t.node(*n), Node::Omitted {}) => {}
        _ => {
            return Err(NotYet {
                what: "`getConsole` with a name".into(),
            });
        }
    }
    Ok(t.core("Console::get_console").call(Vec::new()))
}

/// A failure's receiver, as its own type, not upcast to core's `Error`:
/// `e->get_message()` calls its own getter, which overrides
/// `core::Failure`'s.
fn failure_receiver(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let &[mut receiver] = args else {
        return Err(NotYet {
            what: "a failure's getter without its receiver".into(),
        });
    };
    while let Node::Upcast { value } = t.node(receiver) {
        receiver = value;
    }
    t.expr(receiver)
}

/// `e->get_message()`.
fn failure_message(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(failure_receiver(t, args)?
        .arrow("get_message")
        .call(Vec::new()))
}

/// `e->get_cause()`, a `std::shared_ptr<core::Failure>`, null for none.
fn failure_cause(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    Ok(failure_receiver(t, args)?
        .arrow("get_cause")
        .call(Vec::new()))
}

/// `s.empty()`, where Kotlin's is `core::String::isEmpty(s)`.
fn is_empty(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [s] = args else {
        return Err(NotYet {
            what: "`String.isEmpty` without its receiver".into(),
        });
    };
    let s = t.expr(*s)?;
    Ok(s.method("empty", Vec::new()))
}

/// `std::to_string(n)` without a radix, else the runtime's
/// `core::Int64::toString(n, radix)`.
fn int64_to_string(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    match args {
        [n] => Ok(Expr::name("std::to_string").call(vec![t.expr(*n)?])),
        [n, r] if matches!(t.node(*r), Node::Omitted {}) => {
            Ok(Expr::name("std::to_string").call(vec![t.expr(*n)?]))
        }
        [n, r] => {
            let args = t.operands(&[*n, *r])?;
            Ok(t.core("Int64::toString").call(args))
        }
        _ => Err(NotYet {
            what: "`Int64.toString` without its receiver".into(),
        }),
    }
}

/// `std::to_string(n)` without a radix, else the runtime's
/// `core::Int::toString(n, radix)`, which writes its own digits.
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
    match radix {
        None => {
            let n = t.expr(n)?;
            Ok(Expr::name("std::to_string").call(vec![n]))
        }
        Some(r) => {
            let args = t.operands(&[n, r])?;
            Ok(t.core("Int::toString").call(args))
        }
    }
}

/// The receiver itself: `List.toList()`, of a list that never changes,
/// and `String.toString()`.
fn same(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    match args {
        [x] => t.expr(*x),
        _ => Err(NotYet {
            what: "`toList` or `toString` with arguments".into(),
        }),
    }
}

/// `core::Compare::eq(a, b)`: string indices are `int32_t`s, which
/// Kotlin compares with `==`, but `i == i` is a self-comparison
/// (`-Wtautological-compare`).
fn index_eq(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    if args.len() != 2 {
        return Err(NotYet {
            what: "`StringIndexOption.eq` without two operands".into(),
        });
    }
    let both = t.operands(args)?;
    Ok(t.core("Compare::eq").call(both))
}

/// `core::Float64::near(x, y, relTol, absTol)`: tolerances left out at
/// the end dropped, as its defaults leave them, and one left out before
/// one given `nullptr`, an empty `NullableParam`.
fn float64_near(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let given = (args.iter())
        .rposition(|&a| !matches!(t.node(a), Node::Omitted {}))
        .map_or(0, |i| i + 1);
    let args = &args[..given];
    let present: Vec<NodeId> = (args.iter().copied())
        .filter(|&a| !matches!(t.node(a), Node::Omitted {}))
        .collect();
    let mut values = t.operands(&present)?.into_iter();
    let out = (args.iter())
        .map(|&a| match t.node(a) {
            Node::Omitted {} => Expr::name("nullptr"),
            _ => values.next().unwrap_or(Expr::name("nullptr")),
        })
        .collect();
    Ok(t.core("Float64::near").call(out))
}

/// `core::Mapped::getOr<K, V>(m, k, fallback)`, or where the fallback
/// is null and the values aren't nullable, `getOrNull`, whose result
/// is a `NullableParam` of them.
fn mapped_get_or(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    get_or(t, args, "Mapped")
}

/// As [`mapped_get_or`], for `core::List`.
fn listed_get_or(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    get_or(t, args, "List")
}

fn get_or(t: &mut Translator<'_>, args: &[NodeId], by: &str) -> Result<Expr, NotYet> {
    let &[receiver, _, fallback] = args else {
        return Err(NotYet {
            what: "`getOr` without a key and a fallback".into(),
        });
    };
    let p = t.program();
    let receiver_args = p.types.args(p.nodes.ty(receiver));
    let element = receiver_args.last().copied();
    let wanted = p.nodes.ty(fallback);
    // A class's `shared_ptr` is nullable itself.
    let widened = match element {
        Some(e) => t.type_text(e)? != t.type_text(wanted)?,
        None => false,
    };
    let method = if widened { "getOrNull" } else { "getOr" };
    let targs = t.receiver_template_args(receiver)?;
    let args = t.runtime_call_args(args)?;
    Ok(t.core(&format!("{by}::{method}{targs}")).call(args))
}

/// `(core::Date::getDayOfWeek(d) + 6) % 7 + 1`: ISO's weekday, Sunday 7,
/// where the runtime's, as Kotlin's, gives Sunday 0
/// (notes/kotlin-differences.md); 7 stays 7 should the runtime's change.
fn date_day_of_week(t: &mut Translator<'_>, args: &[NodeId]) -> Result<Expr, NotYet> {
    let [d] = args else {
        return Err(NotYet {
            what: "`Date.dayOfWeek` without its receiver".into(),
        });
    };
    let d = t.expr(*d)?;
    let day = t.core("Date::getDayOfWeek").call(vec![d]);
    let shifted = Expr::bin(Bin::Add, day, Expr::Int(6));
    let iso = Expr::bin(Bin::Mod, shifted, Expr::Int(7));
    Ok(Expr::bin(Bin::Add, iso, Expr::Int(1)))
}

/// std's connected keys this backend connects, sorted, as Kotlin's
/// C++ support network (`CppSupportNetwork.kt`) lists them; its other std
/// `@connected` members use their Temper bodies. Which std keys a backend
/// connects is its own (notes/tmpl/open-questions.md, 48).
pub const STD: &[&str] = &[
    "std/regex.type Regex.compiledFind()",
    "std/regex.type Regex.compiledFound()",
    "std/regex.type Regex.compiledReplace()",
    "std/regex.type Regex.compiledSplit()",
    "std/regex.type RegexFormatter.pushCaptureName()",
    "std/regex.type RegexFormatter.pushCodeTo()",
    "std/regex.type RegexFormatter.regexCompileFormatted()",
    "std/temporal.type Date.day",
    "std/temporal.type Date.get dayOfWeek()",
    "std/temporal.type Date.month",
    "std/temporal.type Date.toString()",
    "std/temporal.type Date.today()",
    "std/temporal.type Date.year",
    "std/temporal.type Date.yearsBetween()",
    "std/testing.type Test.bail()",
    "std/testing.type Test.panics()",
];

/// std's bodiless connected functions that this backend gives a function
/// template of the runtime's, by key: the template, given the class the
/// function's promise is of, and the runtime header that has it. std
/// `net`'s `sendRequest` sends through the runtime's pluggable client
/// (`net.hpp`). Bodiless functions not here give broken promises.
pub const BODILESS: &[(&str, &str, &str)] = &[(
    "std/net.sendRequest()",
    "net::sendRequest",
    "<temper-core/net.hpp>",
)];

/// [`BODILESS`]'s template and header for a key.
pub fn bodiless(key: &str) -> Option<(&'static str, &'static str)> {
    (BODILESS.iter())
        .find(|(k, _, _)| *k == key)
        .map(|&(_, f, header)| (f, header))
}

/// Whether this backend connects a std declaration, by its key
/// (`temper_tmpl::qname::std_key`).
pub fn connects_std(key: &str) -> bool {
    STD.binary_search(&key).is_ok()
}
