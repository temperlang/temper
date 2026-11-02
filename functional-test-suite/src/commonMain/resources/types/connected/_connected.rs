use std::any::{Any, TypeId};
use std::sync::{Arc, RwLock};
use temper_core::{impl_any_value_trait, AnyValueTrait};

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
