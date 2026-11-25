use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE.get_or_init(|| Ok(())).clone()
}

#[derive(Clone, Debug)]
#[non_exhaustive]
pub enum CountError {
    BadCount(BadCount),
}

temper_core::impl_failure_enum!(CountError, BadCount(BadCount));

struct BadCountStruct {
    text: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub struct BadCount(Arc<BadCountStruct>);

impl BadCount {
    pub fn new(text: impl ToArcString) -> BadCount {
        let text = text.to_arc_string();
        BadCount(Arc::new(BadCountStruct {
            text,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("bad count (text: {})", self.0.text))
    }

    pub fn text(&self) -> Arc<String> {
        self.0.text.clone()
    }
}

temper_core::impl_failure!(BadCount, message, cause);

temper_core::impl_any_value_trait!(BadCount, [CountError]);

pub fn parse_count(text: impl ToArcString) -> Result<i32, BadCount> {
    let text = text.to_arc_string();
    if text.as_str() == "zero" {
        Ok(0)
    } else if text.as_str() == "two" {
        Ok(2)
    } else {
        Err(BadCount::new(text.clone()))
    }
}
