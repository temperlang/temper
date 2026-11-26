use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("{}", sum(Arc::new(vec![1, 2, 3])));
            println!("before");
            println!("{}", sum(Arc::new(vec![])));
            println!("not reached");
            Ok(())
        })
        .clone()
}

pub(crate) fn sum(values: impl temper_core::ToListed<i32>) -> i32 {
    let values = values.to_listed();
    temper_core::listed::reduce(&*values, &(|a: i32, b: i32| a.wrapping_add(b)))
}
