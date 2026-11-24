//! Declared failures as generated code writes them: failure classes with
//! `impl_failure!`, a sealed interface's enum and a crate's `Error` with
//! `impl_failure_enum!`, causes set once and never looping, `?` into the
//! enums and into a bubble, `is` and `cast` through an enum, and what an
//! uncaught one says.

use std::error::Error as _;
use std::sync::Arc;
use temper_core::{with_cause, Cause, Failure};

struct NotFoundStruct {
    key: Arc<String>,
    cause: Cause,
}

#[derive(Clone)]
pub struct NotFound(Arc<NotFoundStruct>);

impl NotFound {
    pub fn new(key: &str) -> NotFound {
        NotFound(Arc::new(NotFoundStruct {
            key: Arc::new(key.to_string()),
            cause: Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("not found (key: {})", self.0.key))
    }
}

temper_core::impl_failure!(NotFound, message, cause);
temper_core::impl_any_value_trait!(NotFound, [Error]);

struct BadSignStruct {
    cause: Cause,
}

#[derive(Clone)]
pub struct BadSign(Arc<BadSignStruct>);

impl BadSign {
    pub fn new() -> BadSign {
        BadSign(Arc::new(BadSignStruct {
            cause: Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new("bad sign".to_string())
    }
}

temper_core::impl_failure!(BadSign, message, cause);
temper_core::impl_any_value_trait!(BadSign, [SignError, Error]);

#[derive(Clone, Debug)]
pub enum SignError {
    BadSign(BadSign),
}

temper_core::impl_failure_enum!(SignError, BadSign(BadSign));

#[derive(Clone, Debug)]
#[non_exhaustive]
pub enum Error {
    NotFound(NotFound),
    SignError(SignError),
}

temper_core::impl_failure_enum!(Error, NotFound(NotFound), SignError(SignError));
temper_core::impl_failure_from!(Error, SignError, BadSign);

fn lookup(key: &str) -> Result<i32, NotFound> {
    Err(NotFound::new(key))
}

fn sign() -> Result<i32, SignError> {
    Err(BadSign::new().into())
}

fn both(first: bool) -> Result<i32, Error> {
    Ok(if first { lookup("k")? } else { sign()? })
}

#[test]
fn failures_display_their_messages_and_debug() {
    let e = lookup("k").unwrap_err();
    assert_eq!(e.to_string(), "not found (key: k)");
    assert_eq!(
        format!("{e:?}"),
        r#"NotFound { message: "not found (key: k)" }"#
    );
    let s = sign().unwrap_err();
    assert_eq!(s.to_string(), "bad sign");
    assert_eq!(s.message().as_str(), "bad sign");
    assert_eq!(
        format!("{s:?}"),
        r#"BadSign(BadSign { message: "bad sign" })"#
    );
}

#[test]
fn question_marks_convert_into_enums_and_bubbles() {
    assert!(matches!(both(true), Err(Error::NotFound(_))));
    assert!(matches!(
        both(false),
        Err(Error::SignError(SignError::BadSign(_)))
    ));
    let deep: Error = BadSign::new().into();
    assert!(matches!(deep, Error::SignError(_)));
    // Into a bubble, as a module's init takes it, saying its message.
    let bubble = (|| -> temper_core::Result<i32> { Ok(lookup("b")?) })().unwrap_err();
    assert_eq!(bubble.to_string(), "not found (key: b)");
    assert!(bubble
        .source()
        .unwrap()
        .downcast_ref::<NotFound>()
        .is_some());
}

#[test]
fn causes_are_set_once_and_never_loop() {
    let first = NotFound::new("first");
    let second = NotFound::new("second");
    let outer = with_cause(NotFound::new("outer"), first.clone());
    // Thrown again in another handler, it keeps its first cause.
    let outer = with_cause(outer, second.clone());
    let cause = outer.source().unwrap();
    assert_eq!(cause.to_string(), "not found (key: first)");
    assert!(cause.downcast_ref::<NotFound>().is_some());
    assert_eq!(
        Failure::cause(&outer).unwrap().message().as_str(),
        "not found (key: first)"
    );
    // Not its own cause, nor its cause's.
    let again = with_cause(second.clone(), second.clone());
    assert!(again.source().is_none());
    let looped = with_cause(first.clone(), outer.clone());
    assert!(looped.source().is_none());
    // Through an enum, the variant's.
    let e = Error::NotFound(NotFound::new("e"));
    let e = with_cause(e, first);
    assert_eq!(e.source().unwrap().to_string(), "not found (key: first)");
    assert!(format!("{e:?}").contains("cause: NotFound"));
}

#[test]
fn is_and_cast_see_through_enums_and_causes() {
    let e = Error::SignError(SignError::BadSign(BadSign::new()));
    assert!(temper_core::is::<BadSign>(e.clone()));
    assert!(!temper_core::is::<NotFound>(e.clone()));
    assert!(temper_core::is::<SignError>(e.clone()));
    assert!(temper_core::cast::<BadSign>(e.clone()).is_some());
    let as_core = temper_core::failure::failure(e);
    assert!(temper_core::is::<BadSign>(as_core.clone()));
    assert_eq!(as_core.message().as_str(), "bad sign");
}

#[test]
fn an_uncaught_failure_says_its_causes() {
    let outer = with_cause(NotFound::new("outer"), NotFound::new("inner"));
    let error = temper_core::Error::from(outer);
    let said = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        temper_core::uncaught::<()>(error)
    }))
    .unwrap_err();
    let said = said.downcast_ref::<String>().unwrap();
    assert_eq!(
        said,
        "uncaught failure: not found (key: outer)\ncaused by: not found (key: inner)"
    );
    let said = std::panic::catch_unwind(|| temper_core::uncaught::<()>(temper_core::Error::new()))
        .unwrap_err();
    assert_eq!(
        said.downcast_ref::<String>().unwrap(),
        "uncaught bubble: bubble"
    );
}
