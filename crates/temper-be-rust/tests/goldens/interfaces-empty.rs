use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let greeter = Greeter::new(HiGreeter::new());
            temper_core::cast::<HiGreeter>(greeter.clone())
                .unwrap()
                .greet();
            Ok(())
        })
        .clone()
}

pub(crate) trait GreeterTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Greeter;
}

#[derive(Clone)]
pub(crate) struct Greeter(Arc<dyn GreeterTrait>);

impl Greeter {
    pub fn new(selfish: impl GreeterTrait + 'static) -> Greeter {
        Greeter(Arc::new(selfish))
    }
}

impl GreeterTrait for Greeter {
    fn clone_boxed(&self) -> Greeter {
        GreeterTrait::clone_boxed(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Greeter);

impl std::ops::Deref for Greeter {
    type Target = dyn GreeterTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct HiGreeterStruct {}

#[derive(Clone)]
pub(crate) struct HiGreeter(Arc<HiGreeterStruct>);

impl HiGreeter {
    pub fn new() -> HiGreeter {
        HiGreeter(Arc::new(HiGreeterStruct {}))
    }

    pub fn greet(&self) {
        println!("Hi!");
    }
}

impl GreeterTrait for HiGreeter {
    fn clone_boxed(&self) -> Greeter {
        Greeter::new(self.clone())
    }
}

temper_core::impl_any_value_trait!(HiGreeter, [Greeter]);
