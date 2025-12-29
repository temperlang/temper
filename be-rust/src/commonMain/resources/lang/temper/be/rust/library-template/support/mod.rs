use std::sync::{Arc, OnceLock};

pub(crate) static CONFIG: OnceLock<temper_core::Config> = OnceLock::new();

pub(crate) fn config() -> &'static temper_core::Config {
    CONFIG.get().as_ref().unwrap()
}

pub(crate) fn run_async<T>(gen: Arc<dyn Fn() -> temper_core::SafeGenerator<T> + Send + Sync>)
where
    T: Clone + Send + Sync + 'static,
{
    config().runner().run_async(gen);
}
