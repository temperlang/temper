use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("{}", crate::mine::summary());
            let s = Arc::new("core's string".to_string());
            println!("{s}");
            let xs: temper_core::List<i32> = Arc::new(vec![1, 2]);
            println!("{}", temper_core::ListedTrait::len(&xs));
            println!("{}", bump(5));
            Ok(())
        })
        .clone()
}

pub(crate) fn bump(console: i32) -> i32 {
    console.wrapping_add(1)
}
