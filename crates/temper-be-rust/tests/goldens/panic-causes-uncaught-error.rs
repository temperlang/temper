use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            positive(-1)?;
            println!("after");
            Ok(())
        })
        .clone()
}

#[derive(Clone, Debug)]
pub(crate) enum Problem {
    NotPositive(NotPositive),
}

temper_core::impl_failure_enum!(Problem, NotPositive(NotPositive));

struct NotPositiveStruct {
    n: i32,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct NotPositive(Arc<NotPositiveStruct>);

impl NotPositive {
    pub fn new(n: i32) -> NotPositive {
        NotPositive(Arc::new(NotPositiveStruct {
            n,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("not positive (n: {})", self.0.n))
    }
}

temper_core::impl_failure!(NotPositive, message, cause);

temper_core::impl_any_value_trait!(NotPositive, [Problem]);

pub(crate) fn positive(n: i32) -> Result<i32, NotPositive> {
    if n <= 0 {
        return Err(NotPositive::new(n));
    }
    Ok(n)
}
