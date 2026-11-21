use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE.get_or_init(|| Ok(())).clone()
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;
    use std::sync::RwLock;

    #[test]
    fn fails_in_an_async_block() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin();
        crate::support::run_async({
            let test = test.clone();
            Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                temper_core::SafeGenerator::from_fn({
                    let test = test.clone();
                    let case_index = case_index.clone();
                    Arc::new(move |_generator: temper_core::SafeGenerator<()>| {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        if case_index_local == 0 {
                            test.assert(
                                1i32.wrapping_add(1) == 3,
                                Arc::new(move || Arc::new("failed in async".to_string())),
                            );
                            None
                        } else {
                            None
                        }
                    })
                })
            })
        });
        work.finish(crate::support::config().runner());
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn passes_in_an_async_block() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin();
        crate::support::run_async({
            let test = test.clone();
            Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                temper_core::SafeGenerator::from_fn({
                    let test = test.clone();
                    let case_index = case_index.clone();
                    Arc::new(move |_generator: temper_core::SafeGenerator<()>| {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        if case_index_local == 0 {
                            test.assert(
                                1i32.wrapping_add(1) == 2,
                                Arc::new(move || Arc::new("not shown".to_string())),
                            );
                            None
                        } else {
                            None
                        }
                    })
                })
            })
        });
        work.finish(crate::support::config().runner());
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn fails_in_a_later_turn() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin();
        let pb: temper_core::PromiseBuilder<i32> = temper_core::PromiseBuilder::new();
        crate::support::run_async({
            let test = test.clone();
            let pb = pb.clone();
            Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let n: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let awaited: Arc<RwLock<Option<temper_core::Promise<i32>>>> =
                    Arc::new(RwLock::new(None));
                temper_core::SafeGenerator::from_fn({
                    let test = test.clone();
                    let pb = pb.clone();
                    let n = n.clone();
                    let case_index = case_index.clone();
                    let awaited = awaited.clone();
                    Arc::new(move |generator: temper_core::SafeGenerator<()>| loop {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        match case_index_local {
                            0 => {
                                *awaited.write().unwrap() = Some(pb.promise());
                                *case_index.write().unwrap() = 3;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            1 => {
                                *n.write().unwrap() = -1;
                                *case_index.write().unwrap() = 2;
                            }
                            2 => {
                                test.assert(temper_core::read_locked(&n) == 2, {
                                    let n = n.clone();
                                    Arc::new(move || {
                                        Arc::new(format!(
                                            "later turn got {}",
                                            temper_core::read_locked(&n)
                                        ))
                                    })
                                });
                                return None;
                            }
                            3 => {
                                if (|| {
                                    *n.write().unwrap() = temper_core::read_locked(&awaited)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *case_index.write().unwrap() = 2;
                                    Ok::<_, temper_core::Error>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index.write().unwrap() = 1;
                                    };
                                }
                            }
                            _ => {
                                return None;
                            }
                        }
                    })
                })
            })
        });
        crate::support::run_async({
            let pb = pb.clone();
            Arc::new(move || {
                let case_index2: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                temper_core::SafeGenerator::from_fn({
                    let pb = pb.clone();
                    let case_index2 = case_index2.clone();
                    Arc::new(move |_generator: temper_core::SafeGenerator<()>| {
                        let case_index_local = temper_core::read_locked(&case_index2);
                        *case_index2.write().unwrap() = -1;
                        if case_index_local == 0 {
                            pb.complete(1);
                            None
                        } else {
                            None
                        }
                    })
                })
            })
        });
        work.finish(crate::support::config().runner());
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn passes_after_a_later_turn() -> temper_core::Result<()> {
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
    fn fails_hard_in_an_async_block() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin();
        crate::support::run_async({
            let test = test.clone();
            Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                temper_core::SafeGenerator::from_fn({
                    let test = test.clone();
                    let case_index = case_index.clone();
                    Arc::new(move |_generator: temper_core::SafeGenerator<()>| {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        if case_index_local == 0 {
                            test.fail("failed hard in async");
                            panic!();
                        } else {
                            None
                        }
                    })
                })
            })
        });
        work.finish(crate::support::config().runner());
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn panics_in_an_async_block() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin();
        crate::support::run_async(Arc::new(move || {
            let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
            temper_core::SafeGenerator::from_fn({
                let case_index = case_index.clone();
                Arc::new(move |_generator: temper_core::SafeGenerator<()>| {
                    let case_index_local = temper_core::read_locked(&case_index);
                    *case_index.write().unwrap() = -1;
                    if case_index_local == 0 {
                        panic!();
                    } else {
                        None
                    }
                })
            })
        }));
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
}
