//! Declared failures (RFC 164): what every library's failure types share.
//!
//! A library's failure class is a struct, a handle as any class is
//! (`NotFound(Arc<NotFoundStruct>)`), with a [`Cause`] among its fields,
//! and [`impl_failure!`](crate::impl_failure) makes it a Rust error:
//! `Display` its message, `Debug`, `std::error::Error` with its cause as
//! `source()`, and [`Failure`], core's `Error`. A sealed failure
//! interface, a library's roots among them, is an `enum` over the types
//! right under it, `From` each failure type below, so `?` converts;
//! [`impl_failure_enum!`](crate::impl_failure_enum) gives them the same
//! traits, each the variant's. Each failure type converts into core's
//! `Error`, `Arc<dyn Failure>`, by `From` too, which is a [`Failure`]
//! itself, so a handler of several libraries' failures runs its body as a
//! `Result` of it, and `?` converts. A failure is never a panic:
//! generated code returns it in an `Err`.

use crate::{AnyValue, AnyValueTrait, AsAnyValue};
use std::convert::Infallible;
use std::fmt;
use std::sync::{Arc, OnceLock};

/// Core's `Error`: any library's declared failure, as a failure's cause
/// is one (`Arc<dyn Failure>`). Each failure type implements it.
pub trait Failure: std::error::Error + AnyValueTrait + AsAnyValue + Send + Sync + 'static {
    /// Its message, which `Display` shows.
    fn message(&self) -> Arc<String>;

    /// The failure that was being handled where it was first thrown, if
    /// it was thrown in a handler.
    fn cause(&self) -> Option<Arc<dyn Failure>>;

    /// Sets its cause, unless it has one already, or `cause` is itself,
    /// or has it as a cause, so causes never loop ([`Cause::set`]).
    fn set_cause(&self, cause: Arc<dyn Failure>);

    /// Itself as a standard error, as `source()` gives a cause, since a
    /// trait object can't be upcast before Rust 1.86.
    fn as_error(&self) -> &(dyn std::error::Error + Send + Sync + 'static);
}

impl AsAnyValue for Arc<dyn Failure> {
    fn as_any_value(&self) -> AnyValue {
        (**self).as_any_value()
    }
}

/// Core's `Error` is a failure too, the one it holds: what a handler of
/// several libraries' failures catches, which a `throw` there takes as
/// its cause. Each failure type converts into it by `From`, so `?` does
/// ([`impl_failure!`](crate::impl_failure),
/// [`impl_failure_enum!`](crate::impl_failure_enum)).
impl Failure for Arc<dyn Failure> {
    fn message(&self) -> Arc<String> {
        (**self).message()
    }

    fn cause(&self) -> Option<Arc<dyn Failure>> {
        Failure::cause(&**self)
    }

    fn set_cause(&self, cause: Arc<dyn Failure>) {
        (**self).set_cause(cause)
    }

    fn as_error(&self) -> &(dyn std::error::Error + Send + Sync + 'static) {
        (**self).as_error()
    }
}

impl AnyValueTrait for Arc<dyn Failure> {
    fn cast(&self, type_id: std::any::TypeId) -> Option<Box<dyn std::any::Any>> {
        (**self).cast(type_id)
    }

    fn is(&self, type_id: std::any::TypeId) -> bool {
        (**self).is(type_id)
    }

    fn ptr_id(&self) -> usize {
        (**self).ptr_id()
    }
}

/// "Can't fail" (RFC 164): what a failure parameter is given where a
/// callback can't fail, `Never<Error>` in Temper. It's a failure as any
/// other, so generic code takes it, but has no values, so a result that
/// fails with it is its value ([`into_ok`]).
impl Failure for Infallible {
    fn message(&self) -> Arc<String> {
        match *self {}
    }

    fn cause(&self) -> Option<Arc<dyn Failure>> {
        match *self {}
    }

    fn set_cause(&self, _: Arc<dyn Failure>) {
        match *self {}
    }

    fn as_error(&self) -> &(dyn std::error::Error + Send + Sync + 'static) {
        match *self {}
    }
}

impl AnyValueTrait for Infallible {
    fn cast(&self, _: std::any::TypeId) -> Option<Box<dyn std::any::Any>> {
        match *self {}
    }

    fn is(&self, _: std::any::TypeId) -> bool {
        match *self {}
    }

    fn ptr_id(&self) -> usize {
        match *self {}
    }
}

impl AsAnyValue for Infallible {
    fn as_any_value(&self) -> AnyValue {
        match *self {}
    }
}

/// A result that can't fail as its value, as std's unstable
/// `Result::into_ok`: a call that gives a failure parameter
/// [`Infallible`].
pub fn into_ok<T>(result: Result<T, Infallible>) -> T {
    match result {
        Ok(value) => value,
        Err(never) => match never {},
    }
}

/// A failure's cause, set once, where it's first thrown in a handler:
/// a failure thrown again keeps its first.
#[derive(Default)]
pub struct Cause(OnceLock<Arc<dyn Failure>>);

impl Cause {
    pub fn new() -> Cause {
        Cause(OnceLock::new())
    }

    pub fn get(&self) -> Option<Arc<dyn Failure>> {
        self.0.get().cloned()
    }

    /// Sets it to `cause`, as the cause of `owner`, unless it's set, or
    /// `cause` is `owner` or has it as a cause.
    pub fn set(&self, owner: &dyn AnyValueTrait, cause: Arc<dyn Failure>) {
        let mut at = Some(cause.clone());
        while let Some(c) = at {
            if c.ptr_id() == owner.ptr_id() {
                return;
            }
            at = Failure::cause(&*c);
        }
        let _ = self.0.set(cause);
    }

    /// The cause as `std::error::Error::source` gives it.
    pub fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        (self.0.get()).map(|c| c.as_error() as &(dyn std::error::Error + 'static))
    }
}

/// `failure`, with `cause`, the failure being handled, as its cause
/// unless it has one ([`Failure::set_cause`]): a `throw` in a handler.
pub fn with_cause<F: Failure>(failure: F, cause: impl Failure) -> F {
    failure.set_cause(Arc::new(cause));
    failure
}

/// A failure as core's `Error`.
pub fn failure(failure: impl Failure) -> Arc<dyn Failure> {
    Arc::new(failure)
}

/// A failure class's `Debug`: its name, its message, and its cause, if it
/// has one, `NotFound { message: "not found (key: d)" }`.
pub fn debug(f: &mut fmt::Formatter<'_>, name: &str, failure: &dyn Failure) -> fmt::Result {
    let mut out = f.debug_struct(name);
    out.field("message", &failure.message());
    if let Some(cause) = Failure::cause(failure) {
        out.field("cause", &cause);
    }
    out.finish()
}

/// A failure class's traits ([module docs](self)): `$type`, whose
/// `$message()` gives its message, and whose struct's `$cause` field is
/// its [`Cause`].
#[macro_export]
macro_rules! impl_failure {
    // As `impl_any_value_trait!`, every std name written out in full.
    ($type:ident, $message:ident, $cause:ident) => {
        impl std::fmt::Display for $type {
            fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                f.write_str(&self.$message())
            }
        }

        impl std::fmt::Debug for $type {
            fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                temper_core::failure::debug(f, std::stringify!($type), self)
            }
        }

        impl std::error::Error for $type {
            fn source(&self) -> std::option::Option<&(dyn std::error::Error + 'static)> {
                self.0.$cause.source()
            }
        }

        impl std::convert::From<$type> for std::sync::Arc<dyn temper_core::Failure> {
            fn from(failure: $type) -> std::sync::Arc<dyn temper_core::Failure> {
                std::sync::Arc::new(failure)
            }
        }

        impl temper_core::Failure for $type {
            fn message(&self) -> std::sync::Arc<std::string::String> {
                self.$message()
            }

            fn cause(&self) -> std::option::Option<std::sync::Arc<dyn temper_core::Failure>> {
                self.0.$cause.get()
            }

            fn set_cause(&self, cause: std::sync::Arc<dyn temper_core::Failure>) {
                self.0.$cause.set(self, cause)
            }

            fn as_error(
                &self,
            ) -> &(dyn std::error::Error + std::marker::Send + std::marker::Sync + 'static) {
                self
            }
        }
    };
}

/// `From` each of the failure types `$deep`, below `$via`, a failure
/// enum's variant, into `$type`, that enum, by its variant's `From`:
/// `impl_failure_from!(Error, SignError, BadSign, EmptyText)`.
#[macro_export]
macro_rules! impl_failure_from {
    ($type:ident, $via:ident, $($deep:ty),+ $(,)?) => {
        $(
            impl std::convert::From<$deep> for $type {
                fn from(failure: $deep) -> $type {
                    $type::$via(failure.into())
                }
            }
        )+
    };
}

/// A failure enum's traits ([module docs](self)), each its variant's:
/// `$type, Variant(Type), …`, the types right under it, each `From` its
/// type; with `$type::new` taking any of them (an upcast) and
/// `message()`. Those further below convert by
/// [`impl_failure_from!`](crate::impl_failure_from).
#[macro_export]
macro_rules! impl_failure_enum {
    ($type:ident, $($variant:ident($inner:ty)),+ $(,)?) => {
        #[allow(dead_code)]
        impl $type {
            pub fn new(failure: impl std::convert::Into<$type>) -> $type {
                failure.into()
            }

            pub fn message(&self) -> std::sync::Arc<std::string::String> {
                match self {
                    $($type::$variant(f) => temper_core::Failure::message(f),)*
                }
            }
        }

        $(
            impl std::convert::From<$inner> for $type {
                fn from(failure: $inner) -> $type {
                    $type::$variant(failure)
                }
            }
        )*

        impl std::fmt::Display for $type {
            fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                match self {
                    $($type::$variant(e) => std::fmt::Display::fmt(e, f),)*
                }
            }
        }

        impl std::error::Error for $type {
            fn source(&self) -> std::option::Option<&(dyn std::error::Error + 'static)> {
                match self {
                    $($type::$variant(e) => std::error::Error::source(e),)*
                }
            }
        }

        impl std::convert::From<$type> for std::sync::Arc<dyn temper_core::Failure> {
            fn from(failure: $type) -> std::sync::Arc<dyn temper_core::Failure> {
                std::sync::Arc::new(failure)
            }
        }

        impl temper_core::Failure for $type {
            fn message(&self) -> std::sync::Arc<std::string::String> {
                self.message()
            }

            fn cause(&self) -> std::option::Option<std::sync::Arc<dyn temper_core::Failure>> {
                match self {
                    $($type::$variant(e) => temper_core::Failure::cause(e),)*
                }
            }

            fn set_cause(&self, cause: std::sync::Arc<dyn temper_core::Failure>) {
                match self {
                    $($type::$variant(e) => temper_core::Failure::set_cause(e, cause),)*
                }
            }

            fn as_error(
                &self,
            ) -> &(dyn std::error::Error + std::marker::Send + std::marker::Sync + 'static) {
                match self {
                    $($type::$variant(e) => temper_core::Failure::as_error(e),)*
                }
            }
        }

        impl temper_core::AnyValueTrait for $type {
            fn cast(
                &self,
                type_id: std::any::TypeId,
            ) -> std::option::Option<std::boxed::Box<dyn std::any::Any>> {
                match self {
                    $($type::$variant(e) => temper_core::AnyValueTrait::cast(e, type_id),)*
                }
            }

            fn is(&self, type_id: std::any::TypeId) -> bool {
                match self {
                    $($type::$variant(e) => temper_core::AnyValueTrait::is(e, type_id),)*
                }
            }

            fn ptr_id(&self) -> usize {
                match self {
                    $($type::$variant(e) => temper_core::AnyValueTrait::ptr_id(e),)*
                }
            }
        }

        impl temper_core::AsAnyValue for $type {
            fn as_any_value(&self) -> temper_core::AnyValue {
                match self {
                    $($type::$variant(e) => temper_core::AsAnyValue::as_any_value(e),)*
                }
            }
        }
    };
}

/// Core's own failures' root, `CoreError`: an enum over the types right
/// under it, as a library's roots are.
#[derive(Clone, Debug)]
#[non_exhaustive]
pub enum CoreError {
    MissingKey(MissingKey),
    ParseError(ParseError),
    InvalidCodePoint(InvalidCodePoint),
    BrokenPromise(BrokenPromise),
    BrokenGenerator(BrokenGenerator),
}

crate::impl_failure_enum!(
    CoreError,
    MissingKey(MissingKey),
    ParseError(ParseError),
    InvalidCodePoint(InvalidCodePoint),
    BrokenPromise(BrokenPromise),
    BrokenGenerator(BrokenGenerator)
);

pub struct MissingKeyStruct {
    key: AnyValue,
    message: Arc<String>,
    cause: Cause,
}

/// What a map's `get` gives for a key it doesn't have, with that key.
#[derive(Clone)]
pub struct MissingKey(Arc<MissingKeyStruct>);

impl MissingKey {
    /// For `key`, a map key's value, as Temper's `new MissingKey(key)`.
    pub fn new(key: AnyValue) -> MissingKey {
        let shown = shown_key(&key);
        MissingKey(Arc::new(MissingKeyStruct {
            key,
            message: Arc::new(format!("missing key (key: {shown})")),
            cause: Cause::new(),
        }))
    }

    /// For a map's key, of a map key type: `i32` or `Arc<String>`.
    pub fn of<K: Clone + Send + Sync + 'static>(key: &K) -> MissingKey {
        let any: &dyn std::any::Any = key;
        let key = if let Some(&i) = any.downcast_ref::<i32>() {
            AnyValue::new(i)
        } else if let Some(s) = any.downcast_ref::<Arc<String>>() {
            AnyValue::new(s.clone())
        } else {
            AnyValue::new(())
        };
        MissingKey::new(key)
    }

    /// The key that was missing.
    pub fn key(&self) -> AnyValue {
        self.0.key.clone()
    }

    pub fn message(&self) -> Arc<String> {
        self.0.message.clone()
    }
}

/// A map key's text, as a default message shows a field.
fn shown_key(key: &AnyValue) -> String {
    let as_type = |t: std::any::TypeId| AnyValueTrait::cast(key, t);
    if let Some(i) = as_type(std::any::TypeId::of::<i32>()).and_then(|b| b.downcast::<i32>().ok()) {
        return i.to_string();
    }
    if let Some(s) = (as_type(std::any::TypeId::of::<Arc<String>>()))
        .and_then(|b| b.downcast::<Arc<String>>().ok())
    {
        return s.as_str().to_string();
    }
    "?".to_string()
}

crate::impl_failure!(MissingKey, message, cause);

crate::impl_any_value_trait!(MissingKey, [CoreError]);

pub struct ParseErrorStruct {
    input: Arc<String>,
    reason: Arc<String>,
    cause: Cause,
}

/// What a conversion gives for a value it can't convert: its input, as
/// text, and why, as `not an Int32`.
#[derive(Clone)]
pub struct ParseError(Arc<ParseErrorStruct>);

impl ParseError {
    pub fn new(input: impl crate::ToArcString, reason: impl crate::ToArcString) -> ParseError {
        ParseError(Arc::new(ParseErrorStruct {
            input: input.to_arc_string(),
            reason: reason.to_arc_string(),
            cause: Cause::new(),
        }))
    }

    /// The text, or the number as text.
    pub fn input(&self) -> Arc<String> {
        self.0.input.clone()
    }

    /// Why it couldn't be converted.
    pub fn reason(&self) -> Arc<String> {
        self.0.reason.clone()
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!(
            "parse error (input: {}, reason: {})",
            self.0.input, self.0.reason
        ))
    }
}

crate::impl_failure!(ParseError, message, cause);

crate::impl_any_value_trait!(ParseError, [CoreError]);

pub struct InvalidCodePointStruct {
    code_point: i32,
    cause: Cause,
}

/// What making a string from a code point gives for one that isn't a
/// Unicode scalar value, with that number.
#[derive(Clone)]
pub struct InvalidCodePoint(Arc<InvalidCodePointStruct>);

impl InvalidCodePoint {
    pub fn new(code_point: i32) -> InvalidCodePoint {
        InvalidCodePoint(Arc::new(InvalidCodePointStruct {
            code_point,
            cause: Cause::new(),
        }))
    }

    /// The number that isn't a Unicode scalar value.
    pub fn code_point(&self) -> i32 {
        self.0.code_point
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!(
            "invalid code point (codePoint: {})",
            self.0.code_point
        ))
    }
}

crate::impl_failure!(InvalidCodePoint, message, cause);

crate::impl_any_value_trait!(InvalidCodePoint, [CoreError]);

pub struct BrokenPromiseStruct {
    cause: Cause,
}

/// What awaiting a broken promise gives. It has no fields: a broken
/// promise doesn't say why.
#[derive(Clone)]
pub struct BrokenPromise(Arc<BrokenPromiseStruct>);

impl BrokenPromise {
    pub fn new() -> BrokenPromise {
        BrokenPromise(Arc::new(BrokenPromiseStruct {
            cause: Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new("broken promise".to_string())
    }
}

impl Default for BrokenPromise {
    fn default() -> BrokenPromise {
        BrokenPromise::new()
    }
}

crate::impl_failure!(BrokenPromise, message, cause);

crate::impl_any_value_trait!(BrokenPromise, [CoreError]);

pub struct BrokenGeneratorStruct {
    cause: Cause,
}

/// What a generator's `next()` gives when its body fails, with that
/// failure as its cause; the generator is done after. It has no other
/// fields.
#[derive(Clone)]
pub struct BrokenGenerator(Arc<BrokenGeneratorStruct>);

impl BrokenGenerator {
    pub fn new() -> BrokenGenerator {
        BrokenGenerator(Arc::new(BrokenGeneratorStruct {
            cause: Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new("broken generator".to_string())
    }
}

impl Default for BrokenGenerator {
    fn default() -> BrokenGenerator {
        BrokenGenerator::new()
    }
}

crate::impl_failure!(BrokenGenerator, message, cause);

crate::impl_any_value_trait!(BrokenGenerator, [CoreError]);
