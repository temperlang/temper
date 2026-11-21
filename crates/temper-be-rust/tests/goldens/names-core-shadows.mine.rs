use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            BOOLEAN.set(7).unwrap_or_else(|_| panic!());
            Ok(())
        })
        .clone()
}

struct StringStruct {
    text: i32,
}

#[derive(Clone)]
pub struct String_(Arc<StringStruct>);

impl String_ {
    pub fn new(text: i32) -> String_ {
        String_(Arc::new(StringStruct { text }))
    }

    pub fn doubled(&self) -> i32 {
        self.0.text.wrapping_mul(2)
    }

    pub fn text(&self) -> i32 {
        self.0.text
    }
}

temper_core::impl_any_value_trait!(String_, []);

struct ListStruct {
    n: i32,
}

#[derive(Clone)]
pub struct List(Arc<ListStruct>);

impl List {
    pub fn new(n: i32) -> List {
        List(Arc::new(ListStruct { n }))
    }

    pub fn n(&self) -> i32 {
        self.0.n
    }
}

temper_core::impl_any_value_trait!(List, []);

pub fn ignore(x: i32) -> i32 {
    x.wrapping_add(1)
}

static BOOLEAN: OnceLock<i32> = OnceLock::new();

pub(crate) fn boolean() -> i32 {
    *crate::support::initialized(|| BOOLEAN.get())
}

pub fn summary() -> i32 {
    let s = String_::new(3);
    let l = List::new(4);
    s.0.text
        .wrapping_add(s.doubled())
        .wrapping_add(l.0.n)
        .wrapping_add(ignore(10))
        .wrapping_add(boolean())
}
