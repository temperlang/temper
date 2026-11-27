use std::sync::OnceLock;

#[cfg(test)]
use std::sync::Arc;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE.get_or_init(|| Ok(())).clone()
}

#[cfg(test)]
#[derive(Clone, Debug)]
pub(crate) enum Problem {
    Oops(Oops),
}

#[cfg(test)]
temper_core::impl_failure_enum!(Problem, Oops(Oops));

#[cfg(test)]
struct OopsStruct {
    cause: temper_core::Cause,
}

#[cfg(test)]
#[derive(Clone)]
pub(crate) struct Oops(Arc<OopsStruct>);

#[cfg(test)]
impl Oops {
    pub fn new() -> Oops {
        Oops(Arc::new(OopsStruct {
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new("oops".to_string())
    }
}

#[cfg(test)]
temper_core::impl_failure!(Oops, message, cause);

#[cfg(test)]
temper_core::impl_any_value_trait!(Oops, [Problem]);

#[cfg(test)]
pub(crate) fn at(ls: impl temper_core::ToList<i32>, i: i32) -> Result<i32, Oops> {
    let ls = ls.to_list();
    if i == 99 {
        return Err(Oops::new());
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
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        {
            let actual = 1i32.wrapping_add(1);
            let expected: i32 = 2;
            test.assert(
                actual == expected,
                Arc::new(move || Arc::new(format!("expected 1 + 1 == ({expected}) not ({actual})"))),
            );
        }
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn panics_reading_past_the_end() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let n = super::at(Arc::new(vec![1, 2]), 5).unwrap_or(-1);
        {
            let actual = n;
            let expected: i32 = -1;
            test.assert(
                actual == expected,
                Arc::new(move || Arc::new(format!("expected n == ({expected}) not ({actual})"))),
            );
        }
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn dies_outright() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let _test = temper_std::testing::Test::new();
        let _work = temper_core::TestWork::begin(crate::support::config().runner());
        panic!();
    }

    #[test]
    fn fails_an_assertion() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert(
            1i32.wrapping_add(1) == 3,
            Arc::new(move || Arc::new("wrong on purpose".to_string())),
        );
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
    fn dies_after_failing() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let _work = temper_core::TestWork::begin(crate::support::config().runner());
        test.assert(
            1i32.wrapping_add(1) == 3,
            Arc::new(move || Arc::new("failed before".to_string())),
        );
        panic!();
    }
}
