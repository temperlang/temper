use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE.get_or_init(|| Ok(())).clone()
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

pub fn host_fails_test() {
    crate::_connected::host_fails_test()
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    #[test]
    fn host_errors_are_panics() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            {
                let test = test.clone();
                Arc::new(move || {
                    let r = super::host_fails(1).unwrap_or(-1);
                    test.assert(
                        r != -1,
                        Arc::new(move || Arc::new("expected r != -1".to_string())),
                    );
                    Ok(())
                })
            },
            Some("host error 1"),
        );
        test.assert_panics(
            {
                let test = test.clone();
                Arc::new(move || {
                    let r = super::host_fails(2).unwrap_or(-1);
                    test.assert(
                        r != -1,
                        Arc::new(move || Arc::new("expected r != -1".to_string())),
                    );
                    Ok(())
                })
            },
            Some("host error 2"),
        );
        test.assert_panics(
            {
                let test = test.clone();
                Arc::new(move || {
                    let r = super::host_fails(3).unwrap_or(-1);
                    test.assert(
                        r != -1,
                        Arc::new(move || Arc::new("expected r != -1".to_string())),
                    );
                    Ok(())
                })
            },
            Some("host error 3"),
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn host_test_failure_is_no_panic() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            Arc::new(move || {
                super::host_fails_test();
                Ok(())
            }),
            None::<Arc<String>>,
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }
}
