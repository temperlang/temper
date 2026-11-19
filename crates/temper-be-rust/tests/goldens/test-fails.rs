use std::sync::OnceLock;

#[cfg(test)]
use std::sync::Arc;

#[cfg(test)]
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE.get_or_init(|| Ok(())).clone()
}

#[cfg(test)]
pub(crate) fn tries(f: Arc<dyn Fn() -> temper_core::Result<()> + Send + Sync>) -> bool {
    let mut finished = true;
    if f().is_err() {
        {
            finished = false;
        };
    }
    finished
}

#[cfg(test)]
pub(crate) fn find(key: impl ToArcString) -> Option<i32> {
    let key = key.to_arc_string();
    if key.as_str() == "one" {
        Some(1)
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    #[test]
    fn passes_before() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        test.assert(
            super::tries({
                let test = test.clone();
                Arc::new(move || {
                    let actual = super::find("one");
                    let expected: i32 = 1;
                    test.assert(
                        actual == Some(expected),
                        Arc::new(move || {
                            Arc::new(format!(
                                "expected find(\"one\") == ({expected}) not ({})",
                                if let Some(actual) = actual {
                                    Arc::new(actual.to_string())
                                } else {
                                    Arc::new("null".to_string())
                                }
                            ))
                        }),
                    );
                    Ok(())
                })
            }),
            Arc::new(move || Arc::new("expected tries { assert(find(\"one\") == 1); }".to_string())),
        );
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn fails_past_an_orelse() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        super::tries({
            let test = test.clone();
            Arc::new(move || {
                test.fail("failed on purpose");
                panic!();
            })
        });
        test.fail("swallowed");
        panic!();
    }

    #[test]
    fn narrows_after_fail() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let n = super::find("one");
        if n.is_none() {
            test.fail("one not found");
            panic!();
        }
        {
            let actual = n.unwrap().wrapping_add(1);
            let expected: i32 = 2;
            test.assert(
                actual == expected,
                Arc::new(move || Arc::new(format!("expected n + 1 == ({expected}) not ({actual})"))),
            );
        }
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn fails_before_narrowing() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let n = super::find("two");
        if n.is_none() {
            test.fail("two not found");
            panic!();
        }
        test.assert(
            n.unwrap().wrapping_add(1) == 3,
            Arc::new(move || Arc::new("went on".to_string())),
        );
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn collects_soft_failures() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        test.assert(
            1i32.wrapping_add(1) == 3,
            Arc::new(move || Arc::new("first".to_string())),
        );
        test.assert(true, Arc::new(move || Arc::new("not shown".to_string())));
        test.assert(
            2i32.wrapping_add(2) == 5,
            Arc::new(move || Arc::new("second".to_string())),
        );
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn fails_hard_after_soft_failures() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        test.assert(false, Arc::new(move || Arc::new("soft before".to_string())));
        test.fail("then hard");
        panic!();
    }

    #[test]
    fn fails_a_hard_assertion_past_an_orelse() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        super::tries({
            let test = test.clone();
            Arc::new(move || {
                test.assert_hard(
                    1i32.wrapping_add(1) == 3,
                    Arc::new(move || Arc::new("hard assertion".to_string())),
                );
                Ok(())
            })
        });
        test.fail("swallowed");
        panic!();
    }

    #[test]
    fn hardens_soft_failures_past_an_orelse() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        test.assert(false, Arc::new(move || Arc::new("soft only".to_string())));
        super::tries({
            let test = test.clone();
            Arc::new(move || {
                test.soft_fail_to_hard();
                Ok(())
            })
        });
        test.fail("swallowed");
        panic!();
    }

    #[test]
    fn passes_after() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        {
            let actual = super::find("one");
            let expected: i32 = 1;
            test.assert(
                actual == Some(expected),
                Arc::new(move || {
                    Arc::new(format!(
                        "expected find(\"one\") == ({expected}) not ({})",
                        if let Some(actual) = actual {
                            Arc::new(actual.to_string())
                        } else {
                            Arc::new("null".to_string())
                        }
                    ))
                }),
            );
        }
        test.soft_fail_to_hard();
        Ok(())
    }
}
