use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("reading");
            let r = read(Arc::new(vec![1, 2]), 5).unwrap_or(-1);
            println!("read past the end {r}");
            panic!();
        })
        .clone()
}

pub(crate) fn read(ls: impl temper_core::ToList<i32>, i: i32) -> temper_core::Result<i32> {
    let ls = ls.to_list();
    if i < 0 {
        return Err(temper_core::Error::new());
    }
    Ok(temper_core::ListedTrait::get(&ls, i))
}
