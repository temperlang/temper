use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            positive(-1)?;
            println!("after");
            Ok(())
        })
        .clone()
}

pub(crate) fn positive(n: i32) -> temper_core::Result<i32> {
    if n <= 0 {
        return Err(temper_core::Error::new());
    }
    Ok(n)
}
