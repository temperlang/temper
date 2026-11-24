use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            let h = host_lookup("bug").unwrap_or(-1);
            println!("after {h}");
            Ok(())
        })
        .clone()
}

#[derive(Clone, Debug)]
#[non_exhaustive]
pub enum Error {
    NotFound(NotFound),
}

temper_core::impl_failure_enum!(Error, NotFound(NotFound));

struct NotFoundStruct {
    key: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub struct NotFound(Arc<NotFoundStruct>);

impl NotFound {
    pub fn new(key: impl ToArcString) -> NotFound {
        let key = key.to_arc_string();
        NotFound(Arc::new(NotFoundStruct {
            key,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("not found (key: {})", self.0.key))
    }

    pub fn key(&self) -> Arc<String> {
        self.0.key.clone()
    }
}

temper_core::impl_failure!(NotFound, message, cause);

temper_core::impl_any_value_trait!(NotFound, [Error]);

pub fn host_lookup(key: impl ToArcString) -> Result<i32, NotFound> {
    crate::_connected::host_lookup(key)
}
