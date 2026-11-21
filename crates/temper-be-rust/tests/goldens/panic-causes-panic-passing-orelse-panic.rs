use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            let r =
                read(Arc::new(vec![1, 2]), 5).unwrap_or_else(|e| panic!("panic\ncaused by: {e}"));
            println!("after {r}");
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

pub(crate) fn read(ls: impl temper_core::ToList<i32>, i: i32) -> temper_core::Result<i32> {
    let ls = ls.to_list();
    positive(i.wrapping_add(1))?;
    Ok(temper_core::ListedTrait::get(&ls, i))
}
