use roaring::RoaringBitmap;
use std::any::{Any, TypeId};
use std::sync::{Arc, RwLock};
use temper_core::{impl_any_value_trait, AnyValueTrait};

/// Temper's connected Bitset class, shaped as Temper would generate it:
/// a cheap-to-clone handle to shared state.
#[derive(Clone)]
pub struct Bitset(Arc<RwLock<RoaringBitmap>>);

impl Bitset {
    pub fn new() -> Bitset {
        Bitset(Arc::new(RwLock::new(RoaringBitmap::new())))
    }

    pub fn add(&self, i: i32) {
        // Two's complement is fine here, providing unique values.
        self.0.write().unwrap().insert(i as u32);
    }

    pub fn contains(&self, i: i32) -> bool {
        self.0.read().unwrap().contains(i as u32)
    }
}

impl_any_value_trait!(Bitset, []);
