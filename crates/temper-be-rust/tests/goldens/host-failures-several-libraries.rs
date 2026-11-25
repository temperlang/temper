use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            println!(
                "{} {} {}",
                total("a", "two"),
                total("z", "two"),
                total("a", "x")
            );
            let r = reading("a", "x").unwrap_or_else(|e| {
                println!("{}, caused by {}", e.message(), {
                    let subject = {
                        let subject2 = temper_core::Failure::cause(&e);
                        subject2.as_ref().map(|subject2| subject2.message())
                    };
                    subject
                        .clone()
                        .unwrap_or_else(|| Arc::new("nothing".to_string()))
                });
                0
            });
            println!(
                "{r} {} {} {} {}",
                ratio("a", "two"),
                ratio("a", "zero"),
                ratio("a", "x"),
                ratio("z", "two")
            );
            count_failures::parse_count("x")?;
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
    Unreadable(Unreadable),
}

temper_core::impl_failure_enum!(
    HostFailuresError,
    NotFound(NotFound),
    Unreadable(Unreadable)
);

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

pub(crate) fn total(key: impl ToArcString, count: impl ToArcString) -> i32 {
    let key = key.to_arc_string();
    let count = count.to_arc_string();
    (|| {
        Ok::<_, Arc<dyn temper_core::Failure>>(
            host_lookup(key.clone())?.wrapping_add(count_failures::parse_count(count.clone())?),
        )
    })()
    .unwrap_or_else(|e| {
        println!("caught {}", e.message());
        if temper_core::is::<NotFound>(e.clone()) {
            -1
        } else {
            -2
        }
    })
}

struct UnreadableStruct {
    key: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct Unreadable(Arc<UnreadableStruct>);

impl Unreadable {
    pub fn new(key: impl ToArcString) -> Unreadable {
        let key = key.to_arc_string();
        Unreadable(Arc::new(UnreadableStruct {
            key,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("unreadable (key: {})", self.0.key))
    }

    pub fn key(&self) -> Arc<String> {
        self.0.key.clone()
    }
}

temper_core::impl_failure!(Unreadable, message, cause);

temper_core::impl_any_value_trait!(Unreadable, [HostFailuresError]);

pub(crate) fn reading(key: impl ToArcString, count: impl ToArcString) -> Result<i32, Unreadable> {
    let key = key.to_arc_string();
    let count = count.to_arc_string();
    (|| {
        Ok::<_, Arc<dyn temper_core::Failure>>(
            host_lookup(key.clone())?.wrapping_add(count_failures::parse_count(count.clone())?),
        )
    })()
    .map_err(|failure| temper_core::with_cause(Unreadable::new(key.clone()), failure.clone()))
}

pub(crate) fn nonzero(n: i32) -> temper_core::Result<i32> {
    if n == 0 {
        Err(temper_core::Error::new())
    } else {
        Ok(n)
    }
}

pub(crate) fn ratio(key: impl ToArcString, count: impl ToArcString) -> i32 {
    let key = key.to_arc_string();
    let count = count.to_arc_string();
    (|| {
        Ok::<_, temper_core::Error>(
            host_lookup(key.clone())?
                .wrapping_div(nonzero(count_failures::parse_count(count.clone())?)?),
        )
    })()
    .unwrap_or(-9)
}
