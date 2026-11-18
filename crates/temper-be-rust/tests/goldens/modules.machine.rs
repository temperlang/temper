use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE.get_or_init(|| Ok(())).clone()
}

pub fn describe() -> Arc<String> {
    let state = crate::state::State::new(crate::state::first_id());
    let engine = crate::state::Engine::new(crate::parts::regex::wheels());
    let least = temper_std::regex::one_or_more(
        temper_std::regex::RegexNode::new(temper_std::regex::dot()),
        None,
    )
    .min();
    let parts = crate::state::Parts::new(engine.n().wrapping_add(least));
    Arc::new(format!(
        "state {} engine {} parts {}",
        state.id(),
        engine.n(),
        parts.count()
    ))
}
