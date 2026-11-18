use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("reading");
            let r = remove_first(temper_core::deque::new(), 0).unwrap_or(-1);
            println!("removed from empty {r}");
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

pub(crate) fn remove_first(d: temper_core::Deque<i32>, i: i32) -> temper_core::Result<i32> {
    at(i)?;
    Ok(temper_core::deque::remove_first(&d))
}
