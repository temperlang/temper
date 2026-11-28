use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            let p = (|| {
                let n = count_errors::parse_count("two")?;
                let m = host_lookup("a")?;
                if n.wrapping_add(m) > 2 {
                    panic!();
                }
                Ok::<_, Arc<dyn temper_core::Failure>>(10i32.wrapping_div(n.wrapping_sub(m)))
            })()
            .unwrap_or(-1);
            println!("after {p}");
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
