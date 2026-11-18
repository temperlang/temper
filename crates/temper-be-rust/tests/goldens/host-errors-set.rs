use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("reading");
            let r = write(built(), 5).unwrap_or(-1);
            println!("set past the end {r}");
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

pub(crate) fn built() -> temper_core::ListBuilder<i32> {
    let lb: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
    temper_core::listed::add(&lb, 1, None);
    lb.clone()
}

pub(crate) fn write(lb: temper_core::ListBuilder<i32>, i: i32) -> temper_core::Result<i32> {
    temper_core::listed::set(&lb, at(i)?, 3);
    Ok(temper_core::ListedTrait::len(&lb))
}
