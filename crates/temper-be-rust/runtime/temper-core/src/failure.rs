//! Declared failures (RFC 164): what every library's failure types share.
//!
//! A library's failure class is a struct, a handle as any class is
//! (`NotFound(Arc<NotFoundStruct>)`), with a [`Cause`] among its fields,
//! and [`impl_failure!`](crate::impl_failure) makes it a Rust error:
//! `Display` its message, `Debug`, `std::error::Error` with its cause as
//! `source()`, and [`Failure`], core's `Error`. A sealed failure
//! interface is an `enum` over the types right under it, and the
//! library's base its crate's `Error` enum, `From` each failure type
//! below, so `?` converts; [`impl_failure_enum!`](crate::impl_failure_enum)
//! gives them the same traits, each the variant's. A failure is never a
//! panic: generated code returns it in an `Err`.

use crate::{AnyValue, AnyValueTrait, AsAnyValue};
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
