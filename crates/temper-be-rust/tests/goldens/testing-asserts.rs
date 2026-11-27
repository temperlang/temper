use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            UNIT_VECS
                .set(Arc::new(vec![Vec2::new(1.0, 0.0), Vec2::new(0.0, 1.0)]))
                .unwrap_or_else(|_| panic!());
            Ok(())
        })
        .clone()
}

struct Vec2Struct {
    x: f64,
    y: f64,
}

#[derive(Clone)]
pub struct Vec2(Arc<Vec2Struct>);

impl Vec2 {
    pub fn new(x: f64, y: f64) -> Vec2 {
        Vec2(Arc::new(Vec2Struct { x, y }))
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!(
            "({}, {}) size squared {}",
            temper_core::float64::to_string(self.0.x),
            temper_core::float64::to_string(self.0.y),
            temper_core::float64::to_string(norm_squared(self.clone()))
        ))
    }

    pub fn x(&self) -> f64 {
        self.0.x
    }

    pub fn y(&self) -> f64 {
        self.0.y
    }
}

impl std::fmt::Display for Vec2 {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

temper_core::impl_any_value_trait!(Vec2, []);

pub(crate) fn norm_squared(vec: Vec2) -> f64 {
    vec.0.x * vec.0.x + vec.0.y * vec.0.y
}

static UNIT_VECS: OnceLock<temper_core::List<Vec2>> = OnceLock::new();

#[cfg(test)]
pub(crate) fn unit_vecs() -> temper_core::List<Vec2> {
    crate::support::initialized(|| UNIT_VECS.get()).clone()
}

#[allow(dead_code)]
struct KeptForHostStruct {
    n: i32,
}

#[allow(dead_code)]
#[derive(Clone)]
pub(crate) struct KeptForHost(Arc<KeptForHostStruct>);

#[allow(dead_code)]
impl KeptForHost {
    #[allow(dead_code)]
    pub fn new(n: i32) -> KeptForHost {
        KeptForHost(Arc::new(KeptForHostStruct { n }))
    }
}

temper_core::impl_any_value_trait!(KeptForHost, []);

#[cfg(test)]
struct TestHelperStruct {
    i: i32,
    j: i32,
}

#[cfg(test)]
#[derive(Clone)]
pub(crate) struct TestHelper(Arc<TestHelperStruct>);

#[cfg(test)]
impl TestHelper {
    pub fn new(i: i32, j: i32) -> TestHelper {
        TestHelper(Arc::new(TestHelperStruct { i, j }))
    }
}

#[cfg(test)]
temper_core::impl_any_value_trait!(TestHelper, []);

#[cfg(test)]
pub(crate) fn assert_more_things(
    t: temper_std::testing::Test,
    helper: TestHelper,
    strings: impl temper_core::ToList<temper_core::List<Arc<String>>>,
) {
    let strings = strings.to_list();
    let mut i: i32 = 0;
    while i < temper_core::ListedTrait::len(&strings) {
        {
            let mut j: i32 = 0;
            while j < temper_core::ListedTrait::len(&temper_core::ListedTrait::get(&strings, i)) {
                {
                    let actual = helper.0.i;
                    let expected = helper.0.j;
                    t.assert(
                        actual == expected,
                        Arc::new(move || {
                            Arc::new(format!("expected helper.i == ({expected}) not ({actual})"))
                        }),
                    );
                }
                let s =
                    temper_core::ListedTrait::get(&temper_core::ListedTrait::get(&strings, i), j);
                {
                    let actual2 = s.clone();
                    let expected2 = Arc::new("hi".to_string());
                    t.assert(actual2 == expected2, {
                        let actual2 = actual2.clone();
                        let expected2 = expected2.clone();
                        Arc::new(move || {
                            Arc::new(format!("expected s == ({expected2}) not ({actual2})"))
                        })
                    });
                }
                j = j.wrapping_add(1);
            }
        }
        i = i.wrapping_add(1);
    }
}

#[cfg(test)]
pub(crate) fn sum_of(helper: TestHelper) -> i32 {
    helper.0.i.wrapping_add(helper.0.j)
}

#[cfg(test)]
#[allow(dead_code)]
pub(crate) fn kept_for_tests(helper: TestHelper) -> i32 {
    sum_of(helper.clone())
}

pub mod builders {
    use super::*;

    #[derive(Clone)]
    pub struct Vec2Builder {
        pub x: f64,
        pub y: f64,
    }

    impl Vec2Builder {
        pub fn build(self) -> Vec2 {
            Vec2::new(self.x, self.y)
        }
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    #[test]
    fn a_test_case() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert(
            temper_core::float64::cmp(super::norm_squared(super::Vec2::new(0.0, 0.0)), 0.0) == 0,
            Arc::new(move || Arc::new("Oh noes".to_string())),
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn a_failing_test() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert(
            false,
            Arc::new(move || Arc::new("because it's wrong".to_string())),
        );
        let helper = super::TestHelper::new(3, 2);
        super::assert_more_things(
            test.clone(),
            helper.clone(),
            Arc::new(vec![Arc::new(vec![Arc::new("bye".to_string())])]),
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn exported() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let vec = super::Vec2::new(1.5, 2.5);
        {
            let actual = vec.to_string();
            let expected = Arc::new("(1.5, 2.5) size squared 8.5".to_string());
            test.assert(actual == expected, {
                let actual = actual.clone();
                let expected = expected.clone();
                Arc::new(move || {
                    Arc::new(format!(
                        "expected vec.toString() == ({expected}) not ({actual})"
                    ))
                })
            });
        }
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn module_values() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        {
            let length = temper_core::ListedTrait::len(&super::unit_vecs());
            let mut i: i32 = 0;
            while i < length {
                let vec = temper_core::ListedTrait::get(&super::unit_vecs(), i);
                i = i.wrapping_add(1);
                {
                    let actual = super::norm_squared(vec.clone());
                    let expected: f64 = 1.0;
                    test.assert(
                        temper_core::float64::cmp(actual, expected) == 0,
                        Arc::new(move || {
                            Arc::new(format!(
                                "expected normSquared(vec) == ({}) not ({})",
                                temper_core::float64::to_string(expected),
                                temper_core::float64::to_string(actual)
                            ))
                        }),
                    );
                }
            }
        }
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }
}
