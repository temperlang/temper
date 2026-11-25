use std::sync::{Arc, OnceLock};
use temper_core::AsAnyValue;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            {
                let mut x = Some(Apple::new(Fuji::new()));
                println!("{}", x.clone().unwrap().name());
                println!(
                    "{}",
                    x.clone()
                        .and_then(temper_core::cast::<Fuji>)
                        .unwrap()
                        .name()
                );
                x = Some(Apple::new(Gala::new()));
                println!(
                    "{}",
                    if x.is_some() && temper_core::is::<Fuji>(x.clone().unwrap()) {
                        x.clone()
                            .and_then(temper_core::cast::<Fuji>)
                            .unwrap()
                            .name()
                    } else {
                        Arc::new("Cast failed!".to_string())
                    }
                );
                let y = x.clone().map(|it| it.as_any_value());
                println!(
                    "{}",
                    y.clone()
                        .and_then(temper_core::cast::<Apple>)
                        .unwrap()
                        .name()
                );
                println!(
                    "{}",
                    y.clone()
                        .and_then(temper_core::cast::<Gala>)
                        .unwrap()
                        .name()
                );
                x = None;
                println!(
                    "{}",
                    if x.is_some() {
                        "Tasty nothing."
                    } else {
                        "Was null!"
                    }
                );
            }
            THING0
                .set(false.as_any_value())
                .unwrap_or_else(|_| panic!());
            THING1.set(1.as_any_value()).unwrap_or_else(|_| panic!());
            THING2
                .set(Arc::new("two".to_string()).as_any_value())
                .unwrap_or_else(|_| panic!());
            THING3.set(3.14.as_any_value()).unwrap_or_else(|_| panic!());
            Ok(())
        })
        .clone()
}

pub(crate) trait AppleTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Apple;

    fn name(&self) -> Arc<String>;
}

#[derive(Clone)]
pub(crate) struct Apple(Arc<dyn AppleTrait>);

impl Apple {
    pub fn new(selfish: impl AppleTrait + 'static) -> Apple {
        Apple(Arc::new(selfish))
    }
}

impl AppleTrait for Apple {
    fn clone_boxed(&self) -> Apple {
        AppleTrait::clone_boxed(&*self.0)
    }

    fn name(&self) -> Arc<String> {
        AppleTrait::name(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Apple);

impl std::ops::Deref for Apple {
    type Target = dyn AppleTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct FujiStruct {}

#[derive(Clone)]
pub(crate) struct Fuji(Arc<FujiStruct>);

impl Fuji {
    pub fn new() -> Fuji {
        Fuji(Arc::new(FujiStruct {}))
    }

    pub fn name(&self) -> Arc<String> {
        Arc::new("Fuji".to_string())
    }
}

impl AppleTrait for Fuji {
    fn clone_boxed(&self) -> Apple {
        Apple::new(self.clone())
    }

    fn name(&self) -> Arc<String> {
        self.name()
    }
}

temper_core::impl_any_value_trait!(Fuji, [Apple]);

struct GalaStruct {}

#[derive(Clone)]
pub(crate) struct Gala(Arc<GalaStruct>);

impl Gala {
    pub fn new() -> Gala {
        Gala(Arc::new(GalaStruct {}))
    }

    pub fn name(&self) -> Arc<String> {
        Arc::new("Gala".to_string())
    }
}

impl AppleTrait for Gala {
    fn clone_boxed(&self) -> Apple {
        Apple::new(self.clone())
    }

    fn name(&self) -> Arc<String> {
        self.name()
    }
}

temper_core::impl_any_value_trait!(Gala, [Apple]);

static THING0: OnceLock<temper_core::AnyValue> = OnceLock::new();

pub fn thing0() -> temper_core::AnyValue {
    crate::support::initialized(|| THING0.get()).clone()
}

static THING1: OnceLock<temper_core::AnyValue> = OnceLock::new();

pub fn thing1() -> temper_core::AnyValue {
    crate::support::initialized(|| THING1.get()).clone()
}

static THING2: OnceLock<temper_core::AnyValue> = OnceLock::new();

pub fn thing2() -> temper_core::AnyValue {
    crate::support::initialized(|| THING2.get()).clone()
}

static THING3: OnceLock<temper_core::AnyValue> = OnceLock::new();

pub fn thing3() -> temper_core::AnyValue {
    crate::support::initialized(|| THING3.get()).clone()
}
