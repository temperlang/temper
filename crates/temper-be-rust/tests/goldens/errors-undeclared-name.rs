use std::sync::{Arc, OnceLock};
use temper_core::AsAnyValue;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let r#box = Box::new(3);
            println!(
                "{} {} {} {}",
                good(1),
                unknown_type(1.as_any_value()),
                after_unclosed(2),
                r#box.get()
            );
            undeclared();
            println!("after");
            Ok(())
        })
        .clone()
}

pub fn good(x: i32) -> i32 {
    x.wrapping_add(1)
}

pub(crate) fn undeclared() -> i32 {
    panic!()
}

pub(crate) fn unknown_type(x: temper_core::AnyValue) -> i32 {
    1
}

pub(crate) fn after_unclosed(x: i32) -> i32 {
    x.wrapping_mul(3)
}

struct BoxStruct {
    value: i32,
}

#[derive(Clone)]
pub(crate) struct Box(Arc<BoxStruct>);

impl Box {
    pub fn new(value: i32) -> Box {
        Box(Arc::new(BoxStruct { value }))
    }

    pub fn get(&self) -> i32 {
        self.0.value
    }

    pub fn bad(&self) -> Arc<String> {
        panic!()
    }
}

temper_core::impl_any_value_trait!(Box, []);
