use std::sync::OnceLock;

#[cfg(test)]
use std::convert::Infallible;

#[cfg(test)]
use std::sync::Arc;

#[cfg(test)]
use temper_core::ToArcString;

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
pub(crate) fn at(i: i32) -> Result<i32, Oops> {
    if i == 99 {
        return Err(Oops::new());
    }
    Ok(i)
}

#[cfg(test)]
pub(crate) fn built() -> temper_core::ListBuilder<i32> {
    let lb: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
    temper_core::listed::add(&lb, 1, None);
    lb.clone()
}

#[cfg(test)]
pub(crate) fn read(ls: impl temper_core::ToListed<i32>, i: i32) -> Result<i32, Oops> {
    let ls = ls.to_listed();
    Ok(temper_core::ListedTrait::get(&*ls, at(i)?))
}

#[cfg(test)]
pub(crate) fn write(lb: temper_core::ListBuilder<i32>, i: i32) -> Result<i32, Oops> {
    temper_core::listed::set(&lb, at(i)?, 3);
    Ok(temper_core::ListedTrait::len(&lb))
}

#[cfg(test)]
pub(crate) fn insert(lb: temper_core::ListBuilder<i32>, i: i32) -> Result<i32, Oops> {
    temper_core::listed::add(&lb, 2, Some(at(i)?));
    Ok(temper_core::ListedTrait::len(&lb))
}

#[cfg(test)]
pub(crate) fn remove_last(lb: temper_core::ListBuilder<i32>, i: i32) -> Result<i32, Oops> {
    at(i)?;
    Ok(temper_core::listed::remove_last(&lb))
}

#[cfg(test)]
pub(crate) fn remove_first(d: temper_core::Deque<i32>, i: i32) -> Result<i32, Oops> {
    at(i)?;
    Ok(temper_core::deque::remove_first(&d))
}

#[cfg(test)]
pub(crate) fn sum(ls: impl temper_core::ToList<i32>, i: i32) -> Result<i32, Oops> {
    let ls = ls.to_list();
    at(i)?;
    Ok(temper_core::listed::reduce(
        &ls,
        &(|a: i32, b: i32| a.wrapping_add(b)),
    ))
}

#[cfg(test)]
pub(crate) fn code_at(s: impl ToArcString, i: i32) -> Result<i32, Oops> {
    let s = s.to_arc_string();
    at(i)?;
    Ok(temper_core::string::get(&s, s.len()))
}

#[cfg(test)]
pub(crate) fn replaced(i: i32) -> Result<Arc<String>, Oops> {
    at(i)?;
    Ok(temper_core::into_ok(
        temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::CodePoints::new("b"))
            .replace::<Infallible>(
                "abc",
                Arc::new(move |_match: temper_std::regex::Match| {
                    Ok(temper_core::ListedTrait::get(
                        &Arc::new(vec![Arc::new("x".to_string())]),
                        i,
                    ))
                }),
            ),
    ))
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    #[test]
    fn list_read() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let r = super::read(Arc::new(vec![1, 2]), 5).unwrap_or(-1);
        test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn list_read_at_the_end() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let ls: temper_core::List<i32> = Arc::new(vec![1, 2]);
        let r = super::read(ls.clone(), temper_core::ListedTrait::len(&ls)).unwrap_or(-1);
        test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn negative_index() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let r = super::read(super::built(), -1).unwrap_or(-1);
        test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn set() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let r = super::write(super::built(), 5).unwrap_or(-1);
        test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn set_at_the_end() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let lb: temper_core::ListBuilder<i32> = super::built();
        let r = super::write(lb.clone(), temper_core::ListedTrait::len(&lb)).unwrap_or(-1);
        test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn insert() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let r = super::insert(super::built(), 5).unwrap_or(-1);
        test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn insert_past_the_end() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let lb: temper_core::ListBuilder<i32> = super::built();
        let r = super::insert(
            lb.clone(),
            temper_core::ListedTrait::len(&lb).wrapping_add(1),
        )
        .unwrap_or(-1);
        test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn insert_at_the_end_appends() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let lb: temper_core::ListBuilder<i32> = super::built();
        let n = super::insert(lb.clone(), temper_core::ListedTrait::len(&lb)).unwrap_or(-1);
        test.assert(
            n == 2 && temper_core::ListedTrait::get(&lb, 1) == 2,
            Arc::new(move || Arc::new("didn't append".to_string())),
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn remove_last() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let r = super::remove_last(temper_core::listed::new_builder(), 0).unwrap_or(-1);
        test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn remove_first() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let r = super::remove_first(temper_core::deque::new(), 0).unwrap_or(-1);
        test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn reduce() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let r = super::sum(Arc::new(vec![]), 0).unwrap_or(-1);
        test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn string_read() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let r = super::code_at("ab", 0).unwrap_or(-1);
        test.assert(r != -1, Arc::new(move || Arc::new("caught".to_string())));
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }

    #[test]
    fn replace_callback() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let work = temper_core::TestWork::begin(crate::support::config().runner());
        let r = super::replaced(3).unwrap_or_else(|_| Arc::new(String::new()));
        test.assert(
            r.as_str() != "",
            Arc::new(move || Arc::new("caught".to_string())),
        );
        work.finish();
        test.soft_fail_to_hard();
        Ok(())
    }
}
