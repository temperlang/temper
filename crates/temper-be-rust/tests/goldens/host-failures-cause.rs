use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            setting("port")?;
            println!("after");
            Ok(())
        })
        .clone()
}

#[derive(Clone, Debug)]
#[non_exhaustive]
#[allow(private_interfaces)]
pub enum Error {
    NotFound(NotFound),
    LookupFailed(LookupFailed),
}

temper_core::impl_failure_enum!(Error, NotFound(NotFound), LookupFailed(LookupFailed));

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

struct LookupFailedStruct {
    name: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct LookupFailed(Arc<LookupFailedStruct>);

impl LookupFailed {
    pub fn new(name: impl ToArcString) -> LookupFailed {
        let name = name.to_arc_string();
        LookupFailed(Arc::new(LookupFailedStruct {
            name,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("lookup failed (name: {})", self.0.name))
    }

    pub fn name(&self) -> Arc<String> {
        self.0.name.clone()
    }
}

temper_core::impl_failure!(LookupFailed, message, cause);

temper_core::impl_any_value_trait!(LookupFailed, [Error]);

pub(crate) fn setting(name: impl ToArcString) -> Result<i32, LookupFailed> {
    let name = name.to_arc_string();
    host_lookup(name.clone()).map_err(|failure| {
        temper_core::with_cause(LookupFailed::new(name.clone()), failure.clone())
    })
}
