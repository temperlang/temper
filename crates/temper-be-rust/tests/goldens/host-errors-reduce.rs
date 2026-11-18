use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("reading");
            let r = sum(Arc::new(vec![]), 0).unwrap_or(-1);
            println!("reduced empty {r}");
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

pub(crate) fn sum(ls: impl temper_core::ToList<i32>, i: i32) -> temper_core::Result<i32> {
    let ls = ls.to_list();
    at(i)?;
    Ok(temper_core::listed::reduce(
        &ls,
        &(|a: i32, b: i32| a.wrapping_add(b)),
    ))
}
