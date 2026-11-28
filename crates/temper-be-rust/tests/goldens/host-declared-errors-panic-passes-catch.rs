use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            let r = risky(2).unwrap_or(-1);
            println!("after {r}");
            Ok(())
        })
        .clone()
}

#[derive(Clone, Debug)]
#[non_exhaustive]
pub enum HostDeclaredErrorsError {
    NotFound(NotFound),
}

temper_core::impl_failure_enum!(HostDeclaredErrorsError, NotFound(NotFound));

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

temper_core::impl_any_value_trait!(NotFound, [HostDeclaredErrorsError]);

pub fn host_lookup(key: impl ToArcString) -> Result<i32, NotFound> {
    crate::_connected::host_lookup(key)
}

pub(crate) fn risky(n: i32) -> Result<i32, NotFound> {
    if n > 1 {
        panic!();
    }
    if n > 0 {
        return Err(NotFound::new("n"));
    }
    Ok(n)
}
