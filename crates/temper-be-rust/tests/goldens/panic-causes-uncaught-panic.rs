use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            boom(1);
            println!("after");
            Ok(())
        })
        .clone()
}

pub(crate) fn boom(n: i32) -> i32 {
    if n > 0 {
        panic!();
    }
    n
}
