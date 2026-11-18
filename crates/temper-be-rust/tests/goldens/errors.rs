use std::sync::{Arc, OnceLock};
use temper_core::AsAnyValue;
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let a = mismatched(1).unwrap_or(-1);
            let b = undeclared().unwrap_or(-1);
            let c = misspelled("a").unwrap_or(-1);
            let d = misspelled_method("a").unwrap_or(-1);
            let e = wrong_arity().unwrap_or(-1);
            let r#box = Box::new(3);
            let f = r#box
                .bad()
                .unwrap_or_else(|_| Arc::new("caught".to_string()));
            println!("{} {a} {b} {c} {d} {e}", good(1));
            println!(
                "{} {} {} {f}",
                unknown_type(1.as_any_value()),
                after_unclosed(2),
                r#box.get()
            );
            let g = stored(1).unwrap_or_else(|_| Arc::new("caught".to_string()));
            let h = returned().unwrap_or(-1);
            let i: i32 = -1;
            let j = dropped(1).unwrap_or_else(|_| Arc::new("dropped".to_string()));
            println!("{g} {h} {i} {j}");
            Ok(())
        })
        .clone()
}

pub fn good(x: i32) -> i32 {
    x.wrapping_add(1)
}

pub(crate) fn mismatched(x: i32) -> temper_core::Result<i32> {
    Err(temper_core::Error::new())
}

pub(crate) fn undeclared() -> temper_core::Result<i32> {
    Err(temper_core::Error::new())
}

pub(crate) fn misspelled(s: impl ToArcString) -> temper_core::Result<i32> {
    let s = s.to_arc_string();
    Err(temper_core::Error::new())
}

pub(crate) fn misspelled_method(s: impl ToArcString) -> temper_core::Result<i32> {
    let s = s.to_arc_string();
    Err(temper_core::Error::new())
}

pub(crate) fn wrong_arity() -> temper_core::Result<i32> {
    Err(temper_core::Error::new())
}

pub(crate) fn unknown_type(x: temper_core::AnyValue) -> i32 {
    1
}

pub(crate) fn after_unclosed(x: i32) -> i32 {
    x.wrapping_mul(3)
}

pub(crate) fn dropped(cents: i32) -> temper_core::Result<Arc<String>> {
    Err(temper_core::Error::new())
}

pub(crate) fn stored(x: i32) -> temper_core::Result<Arc<String>> {
    Err(temper_core::Error::new())
}

pub(crate) fn returned() -> temper_core::Result<i32> {
    Err(temper_core::Error::new())
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

    pub fn bad(&self) -> temper_core::Result<Arc<String>> {
        Err(temper_core::Error::new())
    }
}

temper_core::impl_any_value_trait!(Box, []);
