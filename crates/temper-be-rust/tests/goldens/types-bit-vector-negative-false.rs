use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let v = temper_core::BitVector::with_capacity(2);
            v.set(5, false);
            println!("v[5]={}", v.get(5));
            println!("before");
            v.set(-1, false);
            println!("not reached");
            Ok(())
        })
        .clone()
}
