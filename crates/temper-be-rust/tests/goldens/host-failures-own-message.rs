use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            check(0)?;
            check(3)?;
            println!("after");
            Ok(())
        })
        .clone()
}

#[derive(Clone, Debug)]
#[non_exhaustive]
#[allow(private_interfaces)]
pub enum HostFailuresError {
    NotFound(NotFound),
    Expired(Expired),
}

temper_core::impl_failure_enum!(HostFailuresError, NotFound(NotFound), Expired(Expired));

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

temper_core::impl_any_value_trait!(NotFound, [HostFailuresError]);

pub fn host_lookup(key: impl ToArcString) -> Result<i32, NotFound> {
    crate::_connected::host_lookup(key)
}

struct ExpiredStruct {
    days: i32,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct Expired(Arc<ExpiredStruct>);

impl Expired {
    pub fn new(days: i32) -> Expired {
        Expired(Arc::new(ExpiredStruct {
            days,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("expired {} days ago", self.0.days))
    }

    pub fn days(&self) -> i32 {
        self.0.days
    }
}

temper_core::impl_failure!(Expired, message, cause);

temper_core::impl_any_value_trait!(Expired, [HostFailuresError]);

pub(crate) fn check(days: i32) -> Result<(), Expired> {
    if days > 0 {
        return Err(Expired::new(days));
    }
    Ok(())
}
