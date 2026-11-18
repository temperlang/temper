use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            whatever(1);
            let some_things: temper_core::List<Arc<String>> =
                Arc::new(vec![Arc::new("a".to_string()), Arc::new("b".to_string())]);
            let first = Classy::first(some_things.clone());
            let second = Classy::new(1).indexed(some_things.clone());
            println!("Some things: {first}, {second}");
            println!("Picked: {}", Picker::new().pick(some_things.clone()));
            Ok(())
        })
        .clone()
}

pub(crate) fn whatever(n: i32) -> std::any::TypeId {
    if n == 0 {
        println!("0");
    }
    std::any::TypeId::of::<()>()
}

struct ClassyStruct {
    index: i32,
}

#[derive(Clone)]
pub(crate) struct Classy(Arc<ClassyStruct>);

impl Classy {
    pub fn new(index: i32) -> Classy {
        Classy(Arc::new(ClassyStruct { index }))
    }

    pub fn first<T: Clone + Send + Sync + 'static>(things: impl temper_core::ToList<T>) -> T {
        let things = things.to_list();
        let thing = temper_core::ListedTrait::get(&things, 0);
        thing.clone()
    }

    pub fn indexed<T: Clone + Send + Sync + 'static>(
        &self,
        things: impl temper_core::ToList<T>,
    ) -> T {
        let things = things.to_list();
        let thing = temper_core::ListedTrait::get(&things, self.0.index);
        thing.clone()
    }
}

temper_core::impl_any_value_trait!(Classy, []);

pub(crate) fn last_of<T: Clone + Send + Sync + 'static>(things: impl temper_core::ToList<T>) -> T {
    let things = things.to_list();
    let thing = temper_core::ListedTrait::get(
        &things,
        temper_core::ListedTrait::len(&things).wrapping_sub(1),
    );
    thing.clone()
}

struct PickerStruct {}

#[derive(Clone)]
pub(crate) struct Picker(Arc<PickerStruct>);

impl Picker {
    pub fn new() -> Picker {
        Picker(Arc::new(PickerStruct {}))
    }

    pub fn pick<T: Clone + Send + Sync + 'static>(&self, things: impl temper_core::ToList<T>) -> T {
        let things = things.to_list();
        last_of(things.clone())
    }
}

temper_core::impl_any_value_trait!(Picker, []);
