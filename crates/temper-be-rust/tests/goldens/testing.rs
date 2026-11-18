use std::sync::OnceLock;

#[cfg(test)]
use std::sync::RwLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("init {}", twice(1));
            #[cfg(test)]
            {
                *RUNS.write().unwrap() = Some(0);
                BASE.set(10).unwrap_or_else(|_| panic!());
            }
            Ok(())
        })
        .clone()
}

pub fn twice(n: i32) -> i32 {
    n.wrapping_mul(2)
}

#[cfg(test)]
static RUNS: RwLock<Option<i32>> = RwLock::new(None);

#[cfg(test)]
pub(crate) fn runs() -> i32 {
    crate::support::initialized(|| *RUNS.read().unwrap())
}

#[cfg(test)]
static BASE: OnceLock<i32> = OnceLock::new();

#[cfg(test)]
pub(crate) fn base() -> i32 {
    *crate::support::initialized(|| BASE.get())
}

#[cfg(test)]
mod tests {
    #[test]
    fn twice_works() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let x = super::twice(2);
        *super::RUNS.write().unwrap() =
            Some(super::runs().wrapping_add(x).wrapping_add(super::base()));
        test.soft_fail_to_hard()
    }

    #[test]
    fn names_punctuation_quotes_and_n_code() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let t = temper_std::testing::Test::new();
        super::twice(super::runs());
        t.soft_fail_to_hard()
    }

    #[test]
    fn locals_a_test_assigns() -> temper_core::Result<()> {
        crate::init(None)?;
        temper_std::init(None)?;
        let test = temper_std::testing::Test::new();
        let mut n: i32 = 0;
        {
            let mut i: i32 = 0;
            while i < 3 {
                n = n.wrapping_add(super::twice(i));
                i = i.wrapping_add(1);
            }
        }
        *super::RUNS.write().unwrap() = Some(n);
        test.soft_fail_to_hard()
    }
}
