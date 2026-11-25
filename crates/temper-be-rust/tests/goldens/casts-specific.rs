use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            check_b(AlphaInterface::new(BravoClass::new()));
            check_b(AlphaInterface::new(CharlieClass::new()));
            let items: temper_core::List<AlphaInterface> = Arc::new(vec![
                AlphaInterface::new(BravoClass::new()),
                AlphaInterface::new(CharlieClass::new()),
            ]);
            check_b(temper_core::ListedTrait::get(&items, 0));
            check_b(temper_core::ListedTrait::get(&items, 1));
            check_if_b(temper_core::ListedTrait::get(&items, 0));
            check_if_b(temper_core::ListedTrait::get(&items, 1));
            Ok(())
        })
        .clone()
}

pub(crate) trait AlphaInterfaceTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> AlphaInterface;
}

#[derive(Clone)]
pub(crate) struct AlphaInterface(Arc<dyn AlphaInterfaceTrait>);

impl AlphaInterface {
    pub fn new(selfish: impl AlphaInterfaceTrait + 'static) -> AlphaInterface {
        AlphaInterface(Arc::new(selfish))
    }
}

impl AlphaInterfaceTrait for AlphaInterface {
    fn clone_boxed(&self) -> AlphaInterface {
        AlphaInterfaceTrait::clone_boxed(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(AlphaInterface);

impl std::ops::Deref for AlphaInterface {
    type Target = dyn AlphaInterfaceTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct BravoClassStruct {}

#[derive(Clone)]
pub(crate) struct BravoClass(Arc<BravoClassStruct>);

impl BravoClass {
    pub fn new() -> BravoClass {
        BravoClass(Arc::new(BravoClassStruct {}))
    }
}

impl AlphaInterfaceTrait for BravoClass {
    fn clone_boxed(&self) -> AlphaInterface {
        AlphaInterface::new(self.clone())
    }
}

temper_core::impl_any_value_trait!(BravoClass, [AlphaInterface]);

struct CharlieClassStruct {}

#[derive(Clone)]
pub(crate) struct CharlieClass(Arc<CharlieClassStruct>);

impl CharlieClass {
    pub fn new() -> CharlieClass {
        CharlieClass(Arc::new(CharlieClassStruct {}))
    }
}

impl AlphaInterfaceTrait for CharlieClass {
    fn clone_boxed(&self) -> AlphaInterface {
        AlphaInterface::new(self.clone())
    }
}

temper_core::impl_any_value_trait!(CharlieClass, [AlphaInterface]);

pub(crate) fn check_b(instance: AlphaInterface) {
    println!(
        "{}",
        if temper_core::is::<BravoClass>(instance.clone()) {
            "yep"
        } else {
            "nope"
        }
    );
}

pub(crate) fn check_if_b(instance: AlphaInterface) {
    println!(
        "{}",
        if temper_core::is::<BravoClass>(instance.clone()) {
            "yep"
        } else {
            "nope"
        }
    );
}
