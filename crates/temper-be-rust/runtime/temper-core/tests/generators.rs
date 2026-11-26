//! Generators whose bodies may throw (`Generator`): a step gives its
//! value, `None` once done, or the failure, which leaves it done; and a
//! `SafeGenerator` as one, which never fails.

use std::sync::{Arc, RwLock};
use temper_core::failure::failure;
use temper_core::{BrokenPromise, Failure, Generator, SafeGenerator};

#[test]
fn a_failing_step_leaves_it_done() {
    let n = Arc::new(RwLock::new(0));
    let g = Generator::from_fn(Arc::new(move |_: Generator<i32>| {
        let mut n = n.write().unwrap();
        *n += 1;
        match *n {
            1 => Ok(Some(1)),
            _ => Err(failure(BrokenPromise::new())),
        }
    }));
    assert_eq!(g.next().ok(), Some(Some(1)));
    let e = g.next().err().unwrap();
    assert_eq!(*e.message(), "broken promise");
    // Done: the step doesn't run again.
    assert_eq!(g.next().ok(), Some(None));
    assert_eq!(g.next().ok(), Some(None));
}

#[test]
fn a_safe_generator_is_one_that_never_fails() {
    let left = Arc::new(RwLock::new(2));
    let safe = SafeGenerator::from_fn(Arc::new(move |_: SafeGenerator<i32>| {
        let mut left = left.write().unwrap();
        *left -= 1;
        (*left >= 0).then_some(*left)
    }));
    let g = Generator::from(safe);
    assert_eq!(g.next().ok(), Some(Some(1)));
    assert_eq!(g.next().ok(), Some(Some(0)));
    assert_eq!(g.next().ok(), Some(None));
}
