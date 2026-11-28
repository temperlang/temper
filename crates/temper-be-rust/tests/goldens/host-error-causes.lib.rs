#[allow(warnings)]
mod _connected;
mod r#mod;
mod support;

pub use r#mod::*;

/// The library's error type, its one root.
pub type Error = r#mod::OopsError;

/// Initializes the library, once, with `config` or the default
/// configuration, and gives the runner of its async tasks.
pub fn init(config: Option<temper_core::Config>) -> temper_core::Result<temper_core::AsyncRunner> {
    support::CONFIG.get_or_init(|| config.unwrap_or_default());
    temper_core::initializing(|| -> temper_core::Result<()> {
        r#mod::init()?;
        Ok(())
    })?;
    Ok(support::config().runner().clone())
}
