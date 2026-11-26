use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE.get_or_init(|| Ok(())).clone()
}

pub fn greet(who: impl ToArcString) -> Arc<String> {
    let who = who.to_arc_string();
    Arc::new(format!("hello from {who}"))
}

pub fn share(n: i32, d: i32) -> i32 {
    temper_core::int_div(n, d).unwrap_or(0)
}
