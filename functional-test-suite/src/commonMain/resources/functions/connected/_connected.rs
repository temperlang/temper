use super::Hidden;
use std::any::{Any, TypeId};
use std::sync::{Arc, RwLock};
use temper_core::{impl_any_value_trait, AnyValueTrait};
use more::Support;

mod more;

pub(crate) fn sum(i: i32, j: i32, bonus: i32) -> i32 {
    super::sum_of3(i, j, bonus)
}

pub(crate) fn prod(hidden: Hidden, j: i32) -> i32 {
    let support = Support {};
    support.prod(hidden.i(), j)
}

pub(crate) fn length(s: Option<impl temper_core::ToArcString>) -> i32 {
    match s {
        Some(s) => s.to_arc_string().len() as i32,
        None => -1,
    }
}

pub(crate) fn twice(i: i32) -> i32 {
    i * 2
}

/// Temper's connected Counter class, shaped as Temper would generate it: a
/// cheap-to-clone handle to shared state.
#[derive(Clone)]
pub struct Counter(Arc<RwLock<i32>>);

impl Counter {
    pub fn new() -> Counter {
        Counter(Arc::new(RwLock::new(0)))
    }

    pub fn add(&self, i: i32) {
        *self.0.write().unwrap() += i;
    }

    pub fn total(&self) -> i32 {
        *self.0.read().unwrap()
    }
}

impl_any_value_trait!(Counter, []);
