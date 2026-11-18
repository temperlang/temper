use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("reading");
            let r = read(Arc::new(vec![1, 2]), 5).unwrap_or(-1);
            println!("read past the end {r}");
            Ok(())
        })
        .clone()
}

pub(crate) fn at(i: i32) -> temper_core::Result<i32> {
    if i == 99 {
        return Err(temper_core::Error::new());
    }
    Ok(i)
}

pub(crate) fn read(ls: impl temper_core::ToList<i32>, i: i32) -> temper_core::Result<i32> {
    let ls = ls.to_list();
    Ok(temper_core::ListedTrait::get(&ls, at(i)?))
}
