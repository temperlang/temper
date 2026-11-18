use std::sync::OnceLock;
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("reading");
            let r = code_at("ab", 0).unwrap_or(-1);
            println!("read the end {r}");
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

pub(crate) fn code_at(s: impl ToArcString, i: i32) -> temper_core::Result<i32> {
    let s = s.to_arc_string();
    at(i)?;
    Ok(temper_core::string::get(&s, s.len()))
}
