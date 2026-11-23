use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE.get_or_init(|| Ok(())).clone()
}

pub fn host_fails(n: i32) -> temper_core::Result<i32> {
    crate::_connected::host_fails(n)
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    #[test]
    fn host_error_is_no_panic() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin();
        test.assert_panics(
            {
                let test = test.clone();
                Arc::new(move || {
                    let r = super::host_fails(1).unwrap_or(-1);
                    test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
                    Ok(())
                })
            },
            None::<Arc<String>>,
        );
        work.finish(crate::support::config().runner());
        test.soft_fail_to_hard();
        Ok(())
    }
}
