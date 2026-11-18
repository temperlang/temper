pub mod client;
pub mod core;
pub mod helpers;
pub mod machine;
mod r#mod;
pub mod parts;
pub mod state;
mod support;

pub use r#mod::*;

/// Initializes the library, once, with `config` or the default
/// configuration, and gives the runner of its async tasks.
pub fn init(config: Option<temper_core::Config>) -> temper_core::Result<temper_core::AsyncRunner> {
    support::CONFIG.get_or_init(|| config.unwrap_or_default());
    support::initializing(|| -> temper_core::Result<()> {
        temper_std::init(Some(support::config().clone()))?;
        helpers::deep::init()?;
        helpers::init()?;
        core::init()?;
        state::init()?;
        parts::regex::init()?;
        machine::init()?;
        r#mod::init()?;
        client::init()?;
        Ok(())
    })?;
    Ok(support::config().runner().clone())
}

/// Initializes the library with the default configuration, where a
/// module-level value is read before any `init`.
pub(crate) fn init_on_first_use() {
    init(None).expect("initializing a Temper library on first use");
}
