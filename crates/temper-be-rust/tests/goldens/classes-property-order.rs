use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("about to create instance");
            let c = C::new(None);
            println!("about to get backed");
            println!("c.backed={}", c.0.backed);
            println!("about to get computed");
            println!("c.computed={}", c.computed());
            println!("done");
            Ok(())
        })
        .clone()
}

struct CStruct {
    backed: i32,
}

#[derive(Clone)]
pub(crate) struct C(Arc<CStruct>);

impl C {
    pub fn new(backed: Option<i32>) -> C {
        let backed = if let Some(backed) = backed {
            backed
        } else {
            println!("initializing backed");
            1
        };
        C(Arc::new(CStruct { backed }))
    }

    pub fn computed(&self) -> i32 {
        println!("computing computed");
        1
    }
}

temper_core::impl_any_value_trait!(C, []);
