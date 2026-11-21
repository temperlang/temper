use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            println!("after {}", checked(-1));
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

pub(crate) fn checked(n: i32) -> i32 {
    if n == 0 {
        return 0;
    }
    positive(n).unwrap_or_else(|e| panic!("panic\ncaused by: {e}"));
    n
}
