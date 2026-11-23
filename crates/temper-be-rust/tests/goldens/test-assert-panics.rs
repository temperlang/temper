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
    use std::sync::RwLock;

    #[test]
    fn passes_before() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
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
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn passes_when_the_block_panics() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            Arc::new(move || {
                super::at(Arc::new(vec![1, 2]), 5)?;
                Ok(())
            }),
            None::<Arc<String>>,
        );
        test.assert_panics(
            Arc::new(move || {
                panic!();
            }),
            None::<Arc<String>>,
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn passes_past_an_orelse() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            {
                let test = test.clone();
                Arc::new(move || {
                    let n = super::at(Arc::new(vec![1, 2]), 5).unwrap_or(-1);
                    test.assert(
                        n == -1,
                        Arc::new(move || Arc::new("caught by an orelse".to_string())),
                    );
                    Ok(())
                })
            },
            None::<Arc<String>>,
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn fails_when_the_block_returns() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            Arc::new(move || {
                super::at(Arc::new(vec![1, 2]), 0)?;
                Ok(())
            }),
            None::<Arc<String>>,
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn fails_with_its_message() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            Arc::new(move || {
                super::at(Arc::new(vec![1, 2]), 1)?;
                Ok(())
            }),
            Some("reading index 1 panics"),
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn fails_when_the_block_bubbles() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            Arc::new(move || {
                super::at(Arc::new(vec![1, 2]), 99)?;
                Ok(())
            }),
            None::<Arc<String>>,
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn fails_with_its_message_when_the_block_bubbles() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            Arc::new(move || {
                super::at(Arc::new(vec![1, 2]), 99)?;
                Ok(())
            }),
            Some("reading index 99 panics"),
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn goes_on_after_failing() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            Arc::new(move || {
                super::at(Arc::new(vec![1, 2]), 0)?;
                Ok(())
            }),
            None::<Arc<String>>,
        );
        test.assert(false, Arc::new(move || Arc::new("went on".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn nests() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            {
                let test = test.clone();
                Arc::new(move || {
                    test.assert_panics(
                        Arc::new(move || {
                            panic!();
                        }),
                        None::<Arc<String>>,
                    );
                    test.assert_panics(
                        Arc::new(move || {
                            super::at(Arc::new(vec![1, 2]), 0)?;
                            Ok(())
                        }),
                        Some("inner returns"),
                    );
                    panic!();
                })
            },
            None::<Arc<String>>,
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn keeps_what_the_block_changed() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let n: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
        test.assert_panics(
            {
                let n = n.clone();
                Arc::new(move || {
                    *n.write().unwrap() = temper_core::read_locked(&n).wrapping_add(1);
                    panic!();
                })
            },
            None::<Arc<String>>,
        );
        test.assert(
            temper_core::read_locked(&n) == 1,
            Arc::new(move || Arc::new("lost the change".to_string())),
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn fails_on_a_panic_after_the_block() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            Arc::new(move || {
                panic!();
            }),
            None::<Arc<String>>,
        );
        super::at(Arc::new(vec![1, 2]), 7)?;
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn ends_on_a_fail_in_the_block() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let _work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            {
                let test = test.clone();
                Arc::new(move || {
                    test.fail("failed inside");
                    panic!();
                })
            },
            None::<Arc<String>>,
        );
        test.fail("swallowed");
        panic!();
    }

    #[test]
    fn passes_after() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        {
            let actual = super::at(Arc::new(vec![1, 2]), 0)?;
            let expected: i32 = 1;
            test.assert(
                actual == expected,
                Arc::new(move || {
                    Arc::new(format!(
                        "expected at([1, 2], 0) == ({expected}) not ({actual})"
                    ))
                }),
            );
        }
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }
}
