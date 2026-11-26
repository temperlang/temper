//! The connection tables: how core's connected declarations and types are
//! written, by qname, with Kotlin's strings as keys (notes/tmpl/names.md,
//! "Connected declarations"). Each entry matches what Kotlin's Rust
//! backend gives for it (its `RustSupportNetwork.kt`), or a prettier
//! equivalent: `println!("fib(1)={}", …)` with literals in the format
//! string, never `println!("{}", "…")`, and `n.to_string()` for
//! `int_to_string(n, None)`.

use crate::NotYet;
use crate::translate::{Translator, Want};
use crate::tree::{Bin, Expr, Unary};
use temper_tmpl::{Node, NodeId, TypeData};

/// Custom output: from the call's argument nodes (a method's receiver
/// first, and `Omitted` for an argument left out) and what its place
/// wants.
pub type Hook = fn(&mut Translator<'_>, &[NodeId], Want) -> Result<Expr, NotYet>;

/// How a connected declaration is written.
#[derive(Clone, Copy)]
pub enum Connection {
    /// A method of the receiver, borrowed, with the other arguments
    /// given: `a.min(b)`, `s.is_empty()`.
    Method(&'static str),
    /// Custom output.
    Translate(Hook),
    /// A runtime function of the receiver, owned, or for a string
    /// borrowed, then the other arguments, an optional one as an
    /// `Option`: `temper_core::string::to_int(&s, None)`.
    Function(&'static str),
    /// `x as T`, a conversion that can't fail or that wraps, as Kotlin's.
    Cast(&'static str),
    /// A function whose result Rust doesn't keep, because the type it
    /// gives is erased ([`TYPES`]): its calls go where they'd be stored.
    Erased,
    /// A function by its path, called with a borrow of the receiver (of
    /// a `Listed` or `Mapped` handle, its trait object), then the other
    /// arguments, an optional one as an `Option`, as Kotlin's:
    /// `temper_core::ListedTrait::get(&ns, 0)`.
    Ufcs(&'static str),
    /// A function of a std module's support code (Kotlin's `support.rs`
    /// for it, which the module re-exports: [`crate::STD_SUPPORT`]),
    /// called with a borrow of the receiver, or of a static's first
    /// argument, an interface's as its trait object, then the other
    /// arguments, a function as `&dyn Fn`, as Kotlin's `FunctionCall`s
    /// for them: `compiled_found(&self, …)`,
    /// `compile_formatted(&*data, formatted)`.
    Support(&'static str),
}

/// Sorted by qname.
pub const TABLE: &[(&str, Connection)] = &[
    ("core.async()", Connection::Translate(run_async)),
    ("core.awakeUpon()", Connection::Translate(awake_upon)),
    ("core.doneResult()", Connection::Translate(none)),
    ("core.empty()", Connection::Translate(empty)),
    ("core.getConsole()", Connection::Erased),
    ("core.getPromiseResultSync()", Connection::Method("get")),
    (
        "core.safeAdaptGeneratorFn()",
        Connection::Function("temper_core::SafeGenerator::from_fn"),
    ),
    (
        "core.type Boolean.toString()",
        Connection::Translate(to_string),
    ),
    (
        "core.type BrokenPromise.constructor()",
        Connection::Function("temper_core::BrokenPromise::new"),
    ),
    (
        "core.type Console.log()",
        Connection::Translate(console_log),
    ),
    (
        "core.type DenseBitVector.constructor()",
        Connection::Function("temper_core::DenseBitVector::with_capacity"),
    ),
    ("core.type DenseBitVector.get()", Connection::Method("get")),
    ("core.type DenseBitVector.set()", Connection::Method("set")),
    (
        "core.type Deque.add()",
        Connection::Ufcs("temper_core::deque::add"),
    ),
    (
        "core.type Deque.constructor()",
        Connection::Translate(new_deque),
    ),
    (
        "core.type Deque.get isEmpty()",
        Connection::Ufcs("temper_core::deque::is_empty"),
    ),
    (
        "core.type Deque.removeFirst()",
        Connection::Ufcs("temper_core::deque::remove_first"),
    ),
    // A declared failure's cause and message, `temper_core::Failure`'s.
    (
        "core.type Error.get cause()",
        Connection::Translate(failure_cause),
    ),
    (
        "core.type Error.get message()",
        Connection::Translate(failure_message),
    ),
    ("core.type Float64.abs()", Connection::Method("abs")),
    ("core.type Float64.acos()", Connection::Method("acos")),
    ("core.type Float64.asin()", Connection::Method("asin")),
    ("core.type Float64.atan()", Connection::Method("atan")),
    ("core.type Float64.atan2()", Connection::Method("atan2")),
    ("core.type Float64.ceil()", Connection::Method("ceil")),
    ("core.type Float64.cos()", Connection::Method("cos")),
    ("core.type Float64.cosh()", Connection::Method("cosh")),
    ("core.type Float64.e", Connection::Translate(float64_e)),
    ("core.type Float64.exp()", Connection::Method("exp")),
    ("core.type Float64.expm1()", Connection::Method("exp_m1")),
    ("core.type Float64.floor()", Connection::Method("floor")),
    ("core.type Float64.log()", Connection::Method("ln")),
    ("core.type Float64.log10()", Connection::Method("log10")),
    ("core.type Float64.log1p()", Connection::Method("ln_1p")),
    (
        "core.type Float64.max()",
        Connection::Function("temper_core::float64::max"),
    ),
    (
        "core.type Float64.min()",
        Connection::Function("temper_core::float64::min"),
    ),
    (
        "core.type Float64.near()",
        Connection::Function("temper_core::float64::near"),
    ),
    ("core.type Float64.pi", Connection::Translate(float64_pi)),
    ("core.type Float64.round()", Connection::Method("round")),
    (
        "core.type Float64.sign()",
        Connection::Function("temper_core::float64::sign"),
    ),
    ("core.type Float64.sin()", Connection::Method("sin")),
    ("core.type Float64.sinh()", Connection::Method("sinh")),
    ("core.type Float64.sqrt()", Connection::Method("sqrt")),
    ("core.type Float64.tan()", Connection::Method("tan")),
    ("core.type Float64.tanh()", Connection::Method("tanh")),
    (
        "core.type Float64.toInt32()",
        Connection::Function("temper_core::float64::to_int"),
    ),
    ("core.type Float64.toInt32Unsafe()", Connection::Cast("i32")),
    (
        "core.type Float64.toInt64()",
        Connection::Function("temper_core::float64::to_int64"),
    ),
    ("core.type Float64.toInt64Unsafe()", Connection::Cast("i64")),
    (
        "core.type Float64.toString()",
        Connection::Function("temper_core::float64::to_string"),
    ),
    ("core.type Int32.max()", Connection::Method("max")),
    ("core.type Int32.min()", Connection::Method("min")),
    ("core.type Int32.toFloat64()", Connection::Cast("f64")),
    ("core.type Int32.toInt64()", Connection::Cast("i64")),
    (
        "core.type Int32.toString()",
        Connection::Translate(to_string),
    ),
    ("core.type Int64.max()", Connection::Method("max")),
    ("core.type Int64.min()", Connection::Method("min")),
    (
        "core.type Int64.toFloat64()",
        Connection::Function("temper_core::int64_to_float64"),
    ),
    ("core.type Int64.toFloat64Unsafe()", Connection::Cast("f64")),
    (
        "core.type Int64.toInt32()",
        Connection::Function("temper_core::int64_to_int32"),
    ),
    ("core.type Int64.toInt32Unsafe()", Connection::Cast("i32")),
    (
        "core.type Int64.toString()",
        Connection::Translate(to_string),
    ),
    (
        "core.type InvalidCodePoint.constructor()",
        Connection::Function("temper_core::InvalidCodePoint::new"),
    ),
    (
        "core.type List.get length()",
        Connection::Ufcs("temper_core::ListedTrait::len"),
    ),
    (
        "core.type List.get()",
        Connection::Ufcs("temper_core::ListedTrait::get"),
    ),
    ("core.type List.toList()", Connection::Translate(itself)),
    (
        "core.type List.toListBuilder()",
        Connection::Ufcs("temper_core::ListedTrait::to_list_builder"),
    ),
    (
        "core.type ListBuilder.add()",
        Connection::Ufcs("temper_core::listed::add"),
    ),
    (
        "core.type ListBuilder.addAll()",
        Connection::Ufcs("temper_core::listed::add_all"),
    ),
    (
        "core.type ListBuilder.clear()",
        Connection::Ufcs("temper_core::listed::clear"),
    ),
    (
        "core.type ListBuilder.constructor()",
        Connection::Translate(new_list_builder),
    ),
    (
        "core.type ListBuilder.get length()",
        Connection::Ufcs("temper_core::ListedTrait::len"),
    ),
    (
        "core.type ListBuilder.removeLast()",
        Connection::Ufcs("temper_core::listed::remove_last"),
    ),
    (
        "core.type ListBuilder.reverse()",
        Connection::Ufcs("temper_core::listed::reverse"),
    ),
    (
        "core.type ListBuilder.set()",
        Connection::Ufcs("temper_core::listed::set"),
    ),
    (
        "core.type ListBuilder.sort()",
        Connection::Ufcs("temper_core::listed::sort"),
    ),
    (
        "core.type ListBuilder.splice()",
        Connection::Ufcs("temper_core::listed::splice"),
    ),
    (
        "core.type ListBuilder.toList()",
        Connection::Ufcs("temper_core::ListedTrait::to_list"),
    ),
    (
        "core.type ListBuilder.toListBuilder()",
        Connection::Ufcs("temper_core::ListedTrait::to_list_builder"),
    ),
    (
        "core.type Listed.filter()",
        Connection::Ufcs("temper_core::listed::filter"),
    ),
    (
        "core.type Listed.get isEmpty()",
        Connection::Ufcs("temper_core::ListedTrait::is_empty"),
    ),
    (
        "core.type Listed.get length()",
        Connection::Ufcs("temper_core::ListedTrait::len"),
    ),
    (
        "core.type Listed.get()",
        Connection::Ufcs("temper_core::ListedTrait::get"),
    ),
    (
        "core.type Listed.getOr()",
        Connection::Translate(listed_get_or),
    ),
    (
        "core.type Listed.join()",
        Connection::Ufcs("temper_core::listed::join"),
    ),
    (
        "core.type Listed.map()",
        Connection::Ufcs("temper_core::listed::map"),
    ),
    (
        "core.type Listed.reduce()",
        Connection::Ufcs("temper_core::listed::reduce"),
    ),
    (
        "core.type Listed.reduceFrom()",
        Connection::Ufcs("temper_core::listed::reduce_from"),
    ),
    (
        "core.type Listed.slice()",
        Connection::Ufcs("temper_core::listed::slice"),
    ),
    (
        "core.type Listed.sorted()",
        Connection::Ufcs("temper_core::listed::sorted"),
    ),
    (
        "core.type Listed.toList()",
        Connection::Ufcs("temper_core::ListedTrait::to_list"),
    ),
    (
        "core.type Listed.toListBuilder()",
        Connection::Ufcs("temper_core::ListedTrait::to_list_builder"),
    ),
    (
        "core.type Map.constructor()",
        Connection::Translate(new_map),
    ),
    (
        "core.type MapBuilder.clear()",
        Connection::Ufcs("temper_core::MapBuilder::clear"),
    ),
    (
        "core.type MapBuilder.constructor()",
        Connection::Translate(new_map_builder),
    ),
    (
        "core.type MapBuilder.remove()",
        Connection::Ufcs("temper_core::MapBuilder::remove"),
    ),
    (
        "core.type MapBuilder.set()",
        Connection::Ufcs("temper_core::MapBuilder::set"),
    ),
    (
        "core.type Mapped.forEach()",
        Connection::Ufcs("temper_core::MappedTrait::for_each"),
    ),
    (
        "core.type Mapped.get length()",
        Connection::Ufcs("temper_core::MappedTrait::len"),
    ),
    (
        "core.type Mapped.get()",
        Connection::Ufcs("temper_core::MappedTrait::get"),
    ),
    (
        "core.type Mapped.getOr()",
        Connection::Translate(mapped_get_or),
    ),
    (
        "core.type Mapped.has()",
        Connection::Ufcs("temper_core::MappedTrait::has"),
    ),
    (
        "core.type Mapped.keys()",
        Connection::Ufcs("temper_core::MappedTrait::keys"),
    ),
    (
        "core.type Mapped.toList()",
        Connection::Ufcs("temper_core::MappedTrait::to_list"),
    ),
    (
        "core.type Mapped.toListWith()",
        Connection::Ufcs("temper_core::mapped_to_list_with"),
    ),
    (
        "core.type Mapped.toMap()",
        Connection::Ufcs("temper_core::MappedTrait::to_map"),
    ),
    (
        "core.type Mapped.toMapBuilder()",
        Connection::Ufcs("temper_core::MappedTrait::to_map_builder"),
    ),
    (
        "core.type Mapped.values()",
        Connection::Ufcs("temper_core::MappedTrait::values"),
    ),
    (
        "core.type MissingKey.constructor()",
        Connection::Function("temper_core::MissingKey::new"),
    ),
    (
        "core.type Pair.constructor()",
        Connection::Translate(new_pair),
    ),
    (
        "core.type ParseError.constructor()",
        Connection::Function("temper_core::ParseError::new"),
    ),
    (
        "core.type PromiseBuilder.breakPromise()",
        Connection::Method("break_promise"),
    ),
    (
        "core.type PromiseBuilder.complete()",
        Connection::Method("complete"),
    ),
    (
        "core.type PromiseBuilder.constructor()",
        Connection::Translate(new_promise_builder),
    ),
    (
        "core.type PromiseBuilder.get promise()",
        Connection::Method("promise"),
    ),
    (
        "core.type SafeGenerator.nextSafe()",
        Connection::Method("next_safe"),
    ),
    (
        "core.type String.begin",
        Connection::Translate(string_begin),
    ),
    (
        "core.type String.countBetween()",
        Connection::Function("temper_core::string::count_between"),
    ),
    (
        "core.type String.fromCodePoint()",
        Connection::Function("temper_core::string::from_code_point"),
    ),
    (
        "core.type String.fromCodePoints()",
        Connection::Ufcs("temper_core::string::from_code_points"),
    ),
    ("core.type String.get end()", Connection::Method("len")),
    (
        "core.type String.get isEmpty()",
        Connection::Method("is_empty"),
    ),
    (
        "core.type String.get()",
        Connection::Function("temper_core::string::get"),
    ),
    (
        "core.type String.hasAtLeast()",
        Connection::Function("temper_core::string::has_at_least"),
    ),
    (
        "core.type String.hasIndex()",
        Connection::Function("temper_core::string::has_index"),
    ),
    (
        "core.type String.indexOf()",
        Connection::Function("temper_core::string::index_of"),
    ),
    (
        "core.type String.next()",
        Connection::Function("temper_core::string::next"),
    ),
    (
        "core.type String.prev()",
        Connection::Function("temper_core::string::prev"),
    ),
    (
        "core.type String.slice()",
        Connection::Function("temper_core::string::slice"),
    ),
    (
        "core.type String.split()",
        Connection::Function("temper_core::string::split"),
    ),
    (
        "core.type String.step()",
        Connection::Function("temper_core::string::step"),
    ),
    (
        "core.type String.toFloat64()",
        Connection::Function("temper_core::string::to_float64"),
    ),
    (
        "core.type String.toInt32()",
        Connection::Function("temper_core::string::to_int"),
    ),
    (
        "core.type String.toInt64()",
        Connection::Function("temper_core::string::to_int64"),
    ),
    ("core.type String.toString()", Connection::Translate(itself)),
    (
        "core.type StringBuilder.append()",
        Connection::Ufcs("temper_core::string::builder::append"),
    ),
    (
        "core.type StringBuilder.appendBetween()",
        Connection::Ufcs("temper_core::string::builder::append_between"),
    ),
    (
        "core.type StringBuilder.appendCodePoint()",
        Connection::Ufcs("temper_core::string::builder::append_code_point"),
    ),
    (
        "core.type StringBuilder.clear()",
        Connection::Ufcs("temper_core::string::builder::clear"),
    ),
    (
        "core.type StringBuilder.constructor()",
        Connection::Translate(new_string_builder),
    ),
    (
        "core.type StringBuilder.get end()",
        Connection::Ufcs("temper_core::string::builder::end"),
    ),
    (
        "core.type StringBuilder.toString()",
        Connection::Ufcs("temper_core::string::builder::to_string"),
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
    (
        "core.type ValueResult.constructor()",
        Connection::Translate(some),
    ),
    ("std/net.sendRequest()", Connection::Support("send_request")),
    (
        "std/regex.type Regex.compiledFind()",
        Connection::Support("compiled_find"),
    ),
    (
        "std/regex.type Regex.compiledFound()",
        Connection::Support("compiled_found"),
    ),
    (
        "std/regex.type Regex.compiledReplace()",
        Connection::Support("compiled_replace"),
    ),
    (
        "std/regex.type Regex.compiledSplit()",
        Connection::Support("compiled_split"),
    ),
    (
        "std/regex.type RegexFormatter.pushCodeTo()",
        Connection::Support("push_code_to"),
    ),
    (
        "std/regex.type RegexFormatter.regexCompileFormatted()",
        Connection::Support("compile_formatted"),
    ),
    (
        "std/temporal.type Date.today()",
        Connection::Translate(date_today),
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

/// How a connected type is written.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TypeConnection {
    /// Not kept: a module `let` of this type isn't declared, and its
    /// methods ignore it as a receiver. `Console` is, as Kotlin's, since
    /// `Console.log` prints with `println!`.
    Erased,
}

/// Sorted by qname.
pub const TYPES: &[(&str, TypeConnection)] = &[("core.type Console", TypeConnection::Erased)];

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

/// `temper_core::MappedTrait::get_or(&m, k, fallback)`, or where the
/// fallback is null and the values aren't, `get_or_null(&m, k)`, then
/// `.or(fallback)` unless that's `None`: the call's result is an
/// `Option` of the values then.
fn mapped_get_or(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    get_or(t, args, "temper_core::MappedTrait")
}

/// As [`mapped_get_or`], for `temper_core::ListedTrait`.
fn listed_get_or(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    get_or(t, args, "temper_core::ListedTrait")
}

fn get_or(t: &mut Translator<'_>, args: &[NodeId], by: &str) -> Result<Expr, NotYet> {
    let &[receiver, key, fallback] = args else {
        return Err(NotYet::new("`getOr` without a key and a fallback"));
    };
    let p = t.program();
    let element = p.types.args(p.nodes.ty(receiver)).last().copied();
    let widened = element.is_some_and(|e| {
        matches!(p.types.get(p.nodes.ty(fallback)), TypeData::Nullable(_))
            && !matches!(p.types.get(e), TypeData::Nullable(_))
    });
    let method = if widened { "get_or_null" } else { "get_or" };
    let mut fargs = vec![t.handle_ref(receiver)?];
    fargs.push(t.expr(key, Want::Owned)?);
    if !widened {
        fargs.push(t.expr(fallback, Want::Owned)?);
        return Ok(Expr::path(format!("{by}::{method}")).call(fargs));
    }
    let got = Expr::path(format!("{by}::{method}")).call(fargs);
    Ok(match p.nodes.get(fallback) {
        Node::Null {} => got,
        _ => got.method("or", vec![t.expr(fallback, Want::Owned)?]),
    })
}

/// `println!`, with the message's literal parts in its format string, as
/// Kotlin's `ConsoleLog`.
fn console_log(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    let [console, message] = args else {
        return Err(NotYet::new("`Console.log` with other than a message"));
    };
    if !t.is_erased(*console) {
        return Err(NotYet::new("a console other than the module's"));
    }
    let (format, rest) = t.format(*message)?;
    let mut margs = vec![Expr::Str(format)];
    margs.extend(rest);
    Ok(Expr::Macro("println".into(), margs))
}

/// `Boolean.toString()` and `Int32.toString()`: the value itself in a
/// format string, whose `{}` writes it as Temper does, else
/// `Arc::new(n.to_string())` without a radix, or
/// `temper_core::int_to_string(n, Some(radix))`.
fn to_string(t: &mut Translator<'_>, args: &[NodeId], want: Want) -> Result<Expr, NotYet> {
    let (n, radix) = match args {
        [n] => (*n, None),
        [n, r] if matches!(t.node(*r), Node::Omitted {}) => (*n, None),
        [n, r] => (*n, Some(*r)),
        _ => {
            return Err(NotYet::new("`toString` without its receiver"));
        }
    };
    let helper = if t.p_ty(n) == temper_tmpl::TypeId::INT64 {
        "temper_core::int64_to_string"
    } else {
        "temper_core::int_to_string"
    };
    let n = t.receiver(n, true)?;
    Ok(match radix {
        None if want == Want::Display => n,
        None => t.arc(n.method("to_string", Vec::new())),
        Some(radix) => {
            let radix = t.expr(radix, Want::Owned)?;
            Expr::path(helper).call(vec![n, Expr::path("Some").call(vec![radix])])
        }
    })
}

/// A declared failure's receiver, as its own type, not upcast to core's
/// `Error`, which it implements as `temper_core::Failure`, and whether
/// it's core's `Error` itself, an `Arc<dyn Failure>`.
fn failure_receiver(t: &mut Translator<'_>, args: &[NodeId]) -> Result<(Expr, bool), NotYet> {
    let &[mut receiver] = args else {
        return Err(NotYet::new("a failure's getter without its receiver"));
    };
    while let Node::Upcast { value } = t.node(receiver) {
        receiver = value;
    }
    let ty = t.p_ty(receiver);
    let core = t.is_failure_root(ty);
    Ok((t.expr(receiver, Want::Borrowed)?, core))
}

/// `e.message()`: a failure class's own getter, a failure enum's, or the
/// trait's on core's `Error`.
fn failure_message(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    let (receiver, _) = failure_receiver(t, args)?;
    Ok(receiver.method("message", Vec::new()))
}

/// `temper_core::Failure::cause(&e)`, by the trait, as `std::error::Error`
/// has a `cause` of its own, deprecated: `&*c` for core's `Error`.
fn failure_cause(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    let (receiver, core) = failure_receiver(t, args)?;
    let receiver = match core {
        true => Expr::unary(Unary::Ref, Expr::unary(Unary::Deref, receiver)),
        false => Expr::unary(Unary::Ref, receiver),
    };
    Ok(Expr::path("temper_core::Failure::cause").call(vec![receiver]))
}

/// The receiver itself, owned: `List.toList()`.
fn itself(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    match args {
        [x] => t.expr(*x, Want::Owned),
        _ => Err(NotYet::new("`toList` with arguments")),
    }
}

/// `temper_core::listed::new_builder()`, as Kotlin's.
fn new_list_builder(_: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    Ok(Expr::path("temper_core::listed::new_builder").call(Vec::new()))
}

/// `temper_core::MapBuilder::new()`, as Kotlin's.
fn new_map_builder(_: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    Ok(Expr::path("temper_core::MapBuilder::new").call(Vec::new()))
}

/// `temper_core::Map::new(entries)`, from a list of pairs.
fn new_map(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    match args {
        [entries] => {
            let e = t.expr(*entries, Want::Owned)?;
            Ok(Expr::path("temper_core::Map::new").call(vec![e]))
        }
        _ => Err(NotYet::new("a `Map` from other than its entries")),
    }
}

/// A pair, `(key, value)`, a tuple as Kotlin's.
fn new_pair(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    match args {
        [k, v] => {
            let k = t.expr(*k, Want::Owned)?;
            let v = t.expr(*v, Want::Owned)?;
            Ok(Expr::tuple(vec![k, v]))
        }
        _ => Err(NotYet::new("a `Pair` of other than a key and a value")),
    }
}

/// `panic!("{}", test.messages_combined().unwrap_or_default())`, which no
/// `orelse` catches, so the test harness reports the test's messages as
/// its failure; Kotlin's `TestBail` returns them as an `Err`, which one
/// in the code under test could catch.
fn test_bail(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    let [test] = args else {
        return Err(NotYet::new("`Test.bail` without its receiver"));
    };
    let test = t.receiver(*test, true)?;
    let messages =
        (test.method("messages_combined", Vec::new())).method("unwrap_or_default", Vec::new());
    Ok(Expr::Macro(
        "panic".into(),
        vec![Expr::Str("{}".into()), messages],
    ))
}

/// `temper_core::block_panics(block)`, which catches the block's panic,
/// for std's `Test.assertPanics`.
fn test_panics(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    let [_, block] = args else {
        return Err(NotYet::new("`Test.panics` without its receiver and block"));
    };
    let block = t.expr(*block, Want::Owned)?;
    Ok(Expr::path("temper_core::block_panics").call(vec![block]))
}

/// `crate::support::run_async(f)`: runs an `async` block's generator on
/// the library's runner, as Kotlin's `run_async`.
fn run_async(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    let [f] = args else {
        return Err(NotYet::new("async's arguments"));
    };
    match t.in_tests || t.for_tests {
        true => t.runs_async_in_tests = true,
        false => t.runs_async = true,
    }
    let f = t.expr(*f, Want::Owned)?;
    Ok(Expr::path("crate::support::run_async").call(vec![f]))
}

/// `crate::support::awake_upon(promise, generator)`: queues the
/// generator's next step on the library's runner once the promise is
/// resolved, so the call that resolves it returns first, as in the
/// interpreter and JS, where Kotlin's steps it inside that call.
fn awake_upon(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    let [promise, generator] = args else {
        return Err(NotYet::new("awakeUpon's arguments"));
    };
    match t.in_tests || t.for_tests {
        true => t.awaits_in_tests = true,
        false => t.awaits = true,
    }
    let promise = t.expr(*promise, Want::Owned)?;
    let generator = t.expr(*generator, Want::Owned)?;
    Ok(Expr::path("crate::support::awake_upon").call(vec![promise, generator]))
}

/// `temper_core::PromiseBuilder::new()`.
fn new_promise_builder(_: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    Ok(Expr::path("temper_core::PromiseBuilder::new").call(Vec::new()))
}

/// `Some(value)`: a generator's `ValueResult`, as Kotlin's.
fn some(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    let [value] = args else {
        return Err(NotYet::new("ValueResult's arguments"));
    };
    let value = t.expr(*value, Want::Owned)?;
    Ok(Expr::path("Some").call(vec![value]))
}

/// `None`: a generator's `DoneResult`, as Kotlin's.
fn none(_: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    Ok(Expr::path("None"))
}

/// `()`, core's `Empty`, as Kotlin's.
fn empty(_: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    Ok(Expr::tuple(Vec::new()))
}

/// `temper_std::temporal::today().unwrap()`: std's support function, as
/// Kotlin's, whose `Result` never fails for a date from the clock.
fn date_today(_: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    let today = Expr::path("temper_std::temporal::today").call(Vec::new());
    Ok(today.method("unwrap", Vec::new()))
}

/// `std::f64::consts::E`, as Kotlin's.
fn float64_e(_: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    Ok(Expr::path("std::f64::consts::E"))
}

/// `std::f64::consts::PI`, as Kotlin's.
fn float64_pi(_: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    Ok(Expr::path("std::f64::consts::PI"))
}

/// `0usize`: string indices are byte offsets, as Kotlin's.
fn string_begin(_: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    Ok(Expr::Lit("0usize".into()))
}

/// `()`, `NoStringIndex`'s one value, as Kotlin's.
fn string_index_none(_: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    Ok(Expr::tuple(Vec::new()))
}

/// `temper_core::deque::new()`, as Kotlin's.
fn new_deque(_: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    Ok(Expr::path("temper_core::deque::new").call(Vec::new()))
}

/// `Arc::new(RwLock::new(String::new()))`, as Kotlin's.
fn new_string_builder(t: &mut Translator<'_>, _: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    let lock =
        Expr::path("std::sync::RwLock::new").call(vec![Expr::path("String::new").call(Vec::new())]);
    Ok(t.arc(lock))
}

/// `a.cmp(&b) as i32`: string index options are `Option<usize>`, where
/// `None` sorts first, as core says, and as Kotlin's.
fn string_index_compare_to(
    t: &mut Translator<'_>,
    args: &[NodeId],
    _: Want,
) -> Result<Expr, NotYet> {
    let [a, b] = args else {
        return Err(NotYet::new("`compareTo` of other than two"));
    };
    let a = t.expr(*a, Want::Owned)?;
    let b = t.expr(*b, Want::Owned)?;
    Ok(a.method("cmp", vec![Expr::unary(Unary::Ref, b)])
        .cast("i32"))
}

/// `a == b`.
fn string_index_eq(t: &mut Translator<'_>, args: &[NodeId], _: Want) -> Result<Expr, NotYet> {
    let [a, b] = args else {
        return Err(NotYet::new("`eq` of other than two"));
    };
    let a = t.expr(*a, Want::Owned)?;
    let b = t.expr(*b, Want::Owned)?;
    Ok(Expr::bin(Bin::Eq, a, b))
}

/// std's connected keys this backend connects, sorted, as Kotlin's
/// Rust support network (`RustSupportNetwork.kt`) lists them; its other std
/// `@connected` members use their Temper bodies. Which std keys a backend
/// connects is its own (notes/tmpl/open-questions.md, 48).
pub const STD: &[&str] = &[
    "std/net.sendRequest()",
    "std/regex.type Regex.compiledFind()",
    "std/regex.type Regex.compiledFound()",
    "std/regex.type Regex.compiledReplace()",
    "std/regex.type Regex.compiledSplit()",
    "std/regex.type RegexFormatter.pushCodeTo()",
    "std/regex.type RegexFormatter.regexCompileFormatted()",
    "std/temporal.type Date.today()",
    "std/testing.type Test.bail()",
    "std/testing.type Test.panics()",
];

/// Whether this backend connects a std declaration, by its key
/// (`temper_tmpl::qname::std_key`).
pub fn connects_std(key: &str) -> bool {
    STD.binary_search(&key).is_ok()
}
