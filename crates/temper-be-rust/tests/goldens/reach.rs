use std::sync::OnceLock;

#[cfg(test)]
use std::sync::{Arc, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            BASE.set(1).unwrap_or_else(|_| panic!());
            CHECKED.set(shown(2)).unwrap_or_else(|_| panic!());
            println!("shown {}", shown(1));
            #[cfg(test)]
            {
                TEST_FACTOR
                    .set(base().wrapping_mul(2))
                    .unwrap_or_else(|_| panic!());
                *TEST_COUNT.write().unwrap() = Some(0);
            }
            Ok(())
        })
        .clone()
}

static BASE: OnceLock<i32> = OnceLock::new();

pub(crate) fn base() -> i32 {
    *crate::support::initialized(|| BASE.get())
}

pub fn shown(n: i32) -> i32 {
    n.wrapping_add(base())
}

static CHECKED: OnceLock<i32> = OnceLock::new();

#[cfg(test)]
pub(crate) fn checked() -> i32 {
    *crate::support::initialized(|| CHECKED.get())
}

#[cfg(test)]
static TEST_FACTOR: OnceLock<i32> = OnceLock::new();

#[cfg(test)]
pub(crate) fn test_factor() -> i32 {
    *crate::support::initialized(|| TEST_FACTOR.get())
}

#[cfg(test)]
pub(crate) fn for_tests(n: i32) -> i32 {
    n.wrapping_mul(test_factor())
}

#[cfg(test)]
static TEST_COUNT: RwLock<Option<i32>> = RwLock::new(None);

#[cfg(test)]
pub(crate) fn test_count() -> i32 {
    crate::support::initialized(|| *TEST_COUNT.read().unwrap())
}

#[cfg(test)]
pub(crate) trait TestShapeTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> TestShape;

    fn area(&self) -> i32;
}

#[cfg(test)]
#[derive(Clone)]
pub(crate) struct TestShape(Arc<dyn TestShapeTrait>);

#[cfg(test)]
impl TestShape {
    pub fn new(selfish: impl TestShapeTrait + 'static) -> TestShape {
        TestShape(Arc::new(selfish))
    }
}

#[cfg(test)]
impl TestShapeTrait for TestShape {
    fn clone_boxed(&self) -> TestShape {
        TestShapeTrait::clone_boxed(&*self.0)
    }

    fn area(&self) -> i32 {
        TestShapeTrait::area(&*self.0)
    }
}

#[cfg(test)]
temper_core::impl_any_value_trait_for_interface!(TestShape);

#[cfg(test)]
impl std::ops::Deref for TestShape {
    type Target = dyn TestShapeTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

#[cfg(test)]
struct TestSquareStruct {
    side: i32,
}

#[cfg(test)]
#[derive(Clone)]
pub(crate) struct TestSquare(Arc<TestSquareStruct>);

#[cfg(test)]
impl TestSquare {
    pub fn new(side: i32) -> TestSquare {
        TestSquare(Arc::new(TestSquareStruct { side }))
    }

    pub fn area(&self) -> i32 {
        self.0.side.wrapping_mul(self.0.side)
    }
}

#[cfg(test)]
impl TestShapeTrait for TestSquare {
    fn clone_boxed(&self) -> TestShape {
        TestShape::new(self.clone())
    }

    fn area(&self) -> i32 {
        self.area()
    }
}

#[cfg(test)]
temper_core::impl_any_value_trait!(TestSquare, [TestShape]);

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    #[test]
    fn test_parts() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let shape = super::TestShape::new(super::TestSquare::new(super::for_tests(2)));
        *super::TEST_COUNT.write().unwrap() =
            Some(super::test_count().wrapping_add(super::TestShapeTrait::area(&shape)));
        {
            let actual = super::test_count();
            let expected: i32 = 16;
            test.assert(
                actual == expected,
                Arc::new(move || {
                    Arc::new(format!("expected testCount == ({expected}) not ({actual})"))
                }),
            );
        }
        {
            let actual2 = super::checked();
            let expected2: i32 = 3;
            test.assert(
                actual2 == expected2,
                Arc::new(move || {
                    Arc::new(format!("expected checked == ({expected2}) not ({actual2})"))
                }),
            );
        }
        test.soft_fail_to_hard()
    }
}
