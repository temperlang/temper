use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let text = Arc::new("abc".to_string());
            println!("{}", code_at(text.clone(), 0usize));
            println!("before");
            println!("{}", code_at(text.clone(), text.len()));
            println!("not reached");
            Ok(())
        })
        .clone()
}

pub(crate) fn code_at(s: impl ToArcString, i: usize) -> i32 {
    let s = s.to_arc_string();
    temper_core::string::get(&s, i)
}
