use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let builder: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
            temper_core::listed::add(&builder, 1, None);
            temper_core::listed::add(&builder, 2, None);
            put(builder.clone(), 1, 5);
            println!(
                "{}",
                temper_core::listed::join(
                    &builder,
                    Arc::new(", ".to_string()),
                    &(|n: i32| Arc::new(n.to_string())),
                )
            );
            println!("before");
            put(builder.clone(), -1, 0);
            println!("not reached");
            Ok(())
        })
        .clone()
}

pub(crate) fn put(builder: temper_core::ListBuilder<i32>, i: i32, value: i32) {
    temper_core::listed::set(&builder, i, value);
}
