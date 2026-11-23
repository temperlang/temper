use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE.get_or_init(|| Ok(())).clone()
}

#[cfg(test)]
pub(crate) fn at(ls: impl temper_core::ToList<i32>, i: i32) -> temper_core::Result<i32> {
    let ls = ls.to_list();
    if i == 99 {
        return Err(temper_core::Error::new());
    }
    Ok(temper_core::ListedTrait::get(&ls, i))
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    #[test]
    fn passes_before() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin();
        {
            let actual = 1i32.wrapping_add(1);
            let expected: i32 = 2;
            test.assert(
                actual == expected,
                Arc::new(move || Arc::new(format!("expected 1 + 1 == ({expected}) not ({actual})"))),
            );
        }
        work.finish(crate::support::config().runner());
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn panics_reading_past_the_end() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin();
        let n = super::at(Arc::new(vec![1, 2]), 5).unwrap_or(-1);
        {
            let actual = n;
            let expected: i32 = -1;
            test.assert(
                actual == expected,
                Arc::new(move || Arc::new(format!("expected n == ({expected}) not ({actual})"))),
            );
        }
        work.finish(crate::support::config().runner());
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn dies_outright() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let _test = temper_std::testing::Test::new();
        let _work = temper_core::TestWork::begin();
        panic!();
    }

    #[test]
    fn fails_an_assertion() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin();
        test.assert(
            1i32.wrapping_add(1) == 3,
            Arc::new(move || Arc::new("wrong on purpose".to_string())),
        );
        work.finish(crate::support::config().runner());
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn passes_after() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin();
        {
            let actual = super::at(Arc::new(vec![1, 2]), 1)?;
            let expected: i32 = 2;
            test.assert(
                actual == expected,
                Arc::new(move || {
                    Arc::new(format!(
                        "expected at([1, 2], 1) == ({expected}) not ({actual})"
                    ))
                }),
            );
        }
        work.finish(crate::support::config().runner());
        test.soft_fail_to_hard();
        Ok(())
    }
}
