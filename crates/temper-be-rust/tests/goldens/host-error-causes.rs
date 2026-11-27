use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            let h = host_fails(1).unwrap_or_else(|e| panic!("panic\ncaused by: {e}"));
            println!("after {h}");
            Ok(())
        })
        .clone()
}

#[derive(Clone, Debug)]
#[non_exhaustive]
pub enum OopsError {
    Oops(Oops),
}

temper_core::impl_failure_enum!(OopsError, Oops(Oops));

struct OopsStruct {
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub struct Oops(Arc<OopsStruct>);

impl Oops {
    #[allow(clippy::new_without_default)]
    pub fn new() -> Oops {
        Oops(Arc::new(OopsStruct {
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new("oops".to_string())
    }
}

temper_core::impl_failure!(Oops, message, cause);

temper_core::impl_any_value_trait!(Oops, [OopsError]);

pub fn host_fails(n: i32) -> Result<i32, Oops> {
    crate::_connected::host_fails(n)
}
