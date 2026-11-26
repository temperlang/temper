use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("{}", caught(1));
            println!("{}", or_else(1));
            println!("caught");
            println!("or else");
            Ok(())
        })
        .clone()
}

pub(crate) fn caught(n: i32) -> i32 {
    n.wrapping_add(1)
}

pub(crate) fn or_else(n: i32) -> i32 {
    n.wrapping_add(2)
}
