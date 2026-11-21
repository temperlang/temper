use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            let h = host_fails(1).unwrap_or_else(|e| panic!("panic\ncaused by: {e}"));
            println!("after {h}");
            Ok(())
        })
        .clone()
}

pub fn host_fails(n: i32) -> temper_core::Result<i32> {
    crate::_connected::host_fails(n)
}
