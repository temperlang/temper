use roaring::RoaringBitmap;
use std::any::{Any, TypeId};
use std::sync::{Arc, Mutex};
use temper_core::{cast, impl_any_value_trait, AnyValue, AnyValueTrait};

#[derive(Clone)]
struct RoaringWrapper(Arc<Mutex<RoaringBitmap>>);
impl_any_value_trait!(RoaringWrapper, []);

pub(crate) fn new_bitset_connected() -> temper_core::AnyValue {
    AnyValue::new(RoaringWrapper(Arc::new(Mutex::new(RoaringBitmap::new()))))
}

pub(crate) fn bitset_add(bitset: temper_core::AnyValue, i: i32) {
    let bitset = cast::<RoaringWrapper>(bitset).unwrap();
    let mut bitset = bitset.0.lock().unwrap();
    bitset.insert(i as u32);
}

pub(crate) fn bitset_contains(bitset: temper_core::AnyValue, i: i32) -> bool {
    let bitset = cast::<RoaringWrapper>(bitset).unwrap();
    let bitset = bitset.0.lock().unwrap();
    bitset.contains(i as u32)
}
