use std::sync::OnceLock;

#[cfg(test)]
use std::sync::Arc;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE.get_or_init(|| Ok(())).clone()
}

#[cfg(test)]
#[derive(Clone, Debug)]
pub(crate) enum AtError {
    Unlucky(Unlucky),
}

#[cfg(test)]
temper_core::impl_failure_enum!(AtError, Unlucky(Unlucky));

#[cfg(test)]
struct UnluckyStruct {
    cause: temper_core::Cause,
}

#[cfg(test)]
#[derive(Clone)]
pub(crate) struct Unlucky(Arc<UnluckyStruct>);

#[cfg(test)]
impl Unlucky {
    pub fn new() -> Unlucky {
        Unlucky(Arc::new(UnluckyStruct {
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new("unlucky".to_string())
    }
}

#[cfg(test)]
temper_core::impl_failure!(Unlucky, message, cause);

#[cfg(test)]
temper_core::impl_any_value_trait!(Unlucky, [AtError]);

#[cfg(test)]
pub(crate) fn at(ls: impl temper_core::ToList<i32>, i: i32) -> Result<i32, Unlucky> {
    let ls = ls.to_list();
    if i == 99 {
        return Err(Unlucky::new());
    }
    Ok(temper_core::ListedTrait::get(&ls, i))
}

#[cfg(test)]
pub(crate) trait ShapeTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Shape;
}

#[cfg(test)]
#[derive(Clone)]
pub(crate) struct Shape(Arc<dyn ShapeTrait>);

#[cfg(test)]
impl Shape {
    pub fn new(selfish: impl ShapeTrait + 'static) -> Shape {
        Shape(Arc::new(selfish))
    }
}

#[cfg(test)]
impl ShapeTrait for Shape {
    fn clone_boxed(&self) -> Shape {
        ShapeTrait::clone_boxed(&*self.0)
    }
}

#[cfg(test)]
temper_core::impl_any_value_trait_for_interface!(Shape);

#[cfg(test)]
impl std::ops::Deref for Shape {
    type Target = dyn ShapeTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

#[cfg(test)]
struct SquareStruct {}

#[cfg(test)]
#[derive(Clone)]
pub(crate) struct Square(Arc<SquareStruct>);

#[cfg(test)]
impl Square {
    pub fn new() -> Square {
        Square(Arc::new(SquareStruct {}))
    }
}

#[cfg(test)]
impl ShapeTrait for Square {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }
}

#[cfg(test)]
temper_core::impl_any_value_trait!(Square, [Shape]);

#[cfg(test)]
struct CircleStruct {}

#[cfg(test)]
#[derive(Clone)]
pub(crate) struct Circle(Arc<CircleStruct>);

#[cfg(test)]
impl Circle {
    pub fn new() -> Circle {
        Circle(Arc::new(CircleStruct {}))
    }
}

#[cfg(test)]
impl ShapeTrait for Circle {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }
}

#[cfg(test)]
temper_core::impl_any_value_trait!(Circle, [Shape]);

#[cfg(test)]
pub(crate) fn divide(a: i32, b: i32) -> i32 {
    a.wrapping_div(b)
}

#[cfg(test)]
pub(crate) fn remainder(a: i64, b: i64) -> i64 {
    a.wrapping_rem(b)
}

#[cfg(test)]
pub(crate) fn squared(s: Shape) -> Square {
    temper_core::cast::<Square>(s.clone()).unwrap()
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
    fn passes_when_division_by_zero_or_a_cast_panics() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert_panics(
            Arc::new(move || {
                super::divide(1, 0);
                Ok(())
            }),
            None::<Arc<String>>,
        );
        test.assert_panics(
            Arc::new(move || {
                super::remainder(1, 0);
                Ok(())
            }),
            None::<Arc<String>>,
        );
        test.assert_panics(
            Arc::new(move || {
                super::squared(super::Shape::new(super::Circle::new()));
                Ok(())
            }),
            None::<Arc<String>>,
        );
        super::squared(super::Shape::new(super::Square::new()));
        {
            let actual = super::divide(-7, 2);
            let expected: i32 = -3;
            test.assert(
                actual == expected,
                Arc::new(move || {
                    Arc::new(format!(
                        "expected divide(-7, 2) == ({expected}) not ({actual})"
                    ))
                }),
            );
        }
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
    fn fails_when_the_block_throws() -> temper_core::Result<()> {
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
    fn fails_with_its_message_when_the_block_throws() -> temper_core::Result<()> {
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
    fn fails_when_its_body_throws() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        super::at(Arc::new(vec![1, 2]), 99)?;
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
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
