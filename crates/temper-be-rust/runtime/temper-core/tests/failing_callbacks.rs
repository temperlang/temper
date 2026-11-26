//! Core's members that take callbacks, with callbacks that may fail
//! (RFC 164's failure parameters): each `try_` form stops at the first
//! failure and returns it, and gives what its plain form does where
//! nothing fails; and "can't fail", `Infallible`, as a failure whose
//! results are their values (`into_ok`).

use std::convert::Infallible;
use std::sync::Arc;
use temper_core::{into_ok, listed, Cause, Failure, List, ListedTrait, Map};

struct MissingStruct {
    key: i32,
    cause: Cause,
}

#[derive(Clone)]
pub struct Missing(Arc<MissingStruct>);

impl Missing {
    pub fn new(key: i32) -> Missing {
        Missing(Arc::new(MissingStruct {
            key,
            cause: Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("missing {}", self.0.key))
    }
}

temper_core::impl_failure!(Missing, message, cause);
temper_core::impl_any_value_trait!(Missing, []);

/// `n`, unless it's 0, counting each call.
fn check(n: i32, calls: &std::cell::Cell<i32>) -> Result<i32, Missing> {
    calls.set(calls.get() + 1);
    match n {
        0 => Err(Missing::new(n)),
        n => Ok(n),
    }
}

fn list(items: &[i32]) -> List<i32> {
    Arc::new(items.to_vec())
}

#[test]
fn try_forms_give_what_plain_forms_do() {
    let xs = list(&[3, 1, 2]);
    let ok = |n: i32| Ok::<_, Missing>(n);
    let mapped = listed::try_map(&xs, &|n| Ok::<_, Missing>(n * 2));
    assert_eq!(*mapped.ok().unwrap(), vec![6, 2, 4]);
    let kept = listed::try_filter(&xs, &|n| Ok::<_, Missing>(n > 1));
    assert_eq!(*kept.ok().unwrap(), vec![3, 2]);
    let joined = listed::try_join(&xs, "+", &|n| Ok::<_, Missing>(Arc::new(n.to_string())));
    assert_eq!(*joined.ok().unwrap(), "3+1+2");
    assert_eq!(listed::try_reduce(&xs, &|a, b| ok(a + b)).ok(), Some(6));
    assert_eq!(
        listed::try_reduce_from(&xs, 10, &|a, b| ok(a + b)).ok(),
        Some(16)
    );
    let sorted = listed::try_sorted(&xs, &|a, b| ok(a - b));
    assert_eq!(*sorted.ok().unwrap(), vec![1, 2, 3]);
    let builder = xs.to_list_builder();
    assert!(listed::try_sort(&builder, &|a, b| ok(b - a)).is_ok());
    assert_eq!(*builder.read().unwrap(), vec![3, 2, 1]);
    let map = Map::new(vec![(1, 10), (2, 20)]);
    let pairs = temper_core::mapped_try_to_list_with(&map, &|k, v| ok(k + v));
    assert_eq!(*pairs.ok().unwrap(), vec![11, 22]);
    let total = std::cell::Cell::new(0);
    let each = temper_core::mapped_try_for_each(&map, &|k, v| {
        total.set(total.get() + k * v);
        ok(0).map(|_| ())
    });
    assert!(each.is_ok());
    assert_eq!(total.get(), 50);
}

/// A result's failure's message, if it failed.
fn failed<T>(result: Result<T, Missing>) -> Option<String> {
    result.err().map(|e| e.message().to_string())
}

#[test]
fn try_forms_stop_at_the_first_failure() {
    let xs = list(&[3, 0, 2, 0]);
    let calls = std::cell::Cell::new(0);
    let missing = Some("missing 0".to_string());
    assert_eq!(failed(listed::try_map(&xs, &|n| check(n, &calls))), missing);
    assert_eq!(calls.get(), 2);
    assert_eq!(
        failed(listed::try_filter(&xs, &|n| check(n, &calls).map(|_| true))),
        missing
    );
    let joined = listed::try_join(&xs, ",", &|n| {
        check(n, &calls).map(|n| Arc::new(n.to_string()))
    });
    assert_eq!(failed(joined), missing);
    let sum = |a: i32, b: i32| check(b, &calls).map(|b| a + b);
    assert_eq!(failed(listed::try_reduce(&xs, &sum)), missing);
    assert_eq!(failed(listed::try_reduce_from(&xs, 0, &sum)), missing);
    calls.set(0);
    let compare = |a: i32, b: i32| check(a * b, &calls);
    assert_eq!(failed(listed::try_sorted(&xs, &compare)), missing);
    // A failing comparison stops comparing, and leaves a builder as it
    // was.
    let builder = xs.to_list_builder();
    assert_eq!(failed(listed::try_sort(&builder, &compare)), missing);
    assert_eq!(*builder.read().unwrap(), vec![3, 0, 2, 0]);
    let map = Map::new(vec![(1, 1), (0, 2), (3, 0)]);
    let pair = |k: i32, v: i32| check(k, &calls).map(|k| k + v);
    let pairs = temper_core::mapped_try_to_list_with(&map, &pair);
    assert_eq!(failed(pairs), missing);
    calls.set(0);
    let each = temper_core::mapped_try_for_each(&map, &|k, _| check(k, &calls).map(|_| ()));
    assert_eq!(failed(each), missing);
    assert_eq!(calls.get(), 2);
}

/// A failure parameter's "can't fail" is `Infallible`, a failure like
/// any other, whose results unwrap without a check.
#[test]
fn infallible_is_a_failure_that_cannot_happen() {
    fn generic<E: Failure + Clone>(f: &dyn Fn(i32) -> Result<i32, E>) -> Result<i32, E> {
        f(1)
    }
    let n = into_ok(generic(&|n| Ok::<_, Infallible>(n + 1)));
    assert_eq!(n, 2);
    let xs = list(&[1, 2]);
    let mapped = into_ok(listed::try_map(&xs, &|n| Ok::<_, Infallible>(n * 3)));
    assert_eq!(mapped.len(), 2);
}
