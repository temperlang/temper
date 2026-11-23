use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let one: i32 = 1;
            let big = 2147483647i32.wrapping_mul(one);
            let small = (-2147483648i32).wrapping_neg().wrapping_mul(one);
            println!(
                "{} {} {} {} {} {}",
                big.wrapping_add(1),
                small.wrapping_sub(1),
                one.wrapping_add(1),
                one.wrapping_sub(1),
                small.wrapping_add(1),
                big.wrapping_sub(1)
            );
            let one64 = one as i64;
            let big64 = 9223372036854775807i64.wrapping_mul(one64);
            let small64 = big64.wrapping_neg().wrapping_sub(one64);
            println!(
                "{} {} {}",
                big64.wrapping_add(1),
                small64.wrapping_sub(1),
                one64.wrapping_sub(1)
            );
            let zero = one.wrapping_sub(one);
            println!(
                "{} {} {} {} {} {}",
                big.signum(),
                small.signum(),
                zero.signum(),
                one.signum(),
                zero.wrapping_sub(one).signum(),
                small.wrapping_add(1).signum()
            );
            DIGITS
                .set(Arc::new(vec![1, 2, 3]))
                .unwrap_or_else(|_| panic!());
            println!("{} {} {} {}", from(0), from(1), from(3), from(7));
            let inf = 1e308 * 10.0;
            let nan = inf - inf;
            let ninf = 0.0 - inf;
            println!(
                "{} {} {} {} {}",
                temper_core::float64::near(nan, nan, None, None),
                temper_core::float64::near(inf, inf, None, None),
                temper_core::float64::near(ninf, ninf, None, None),
                temper_core::float64::near(nan, 1.0, None, None),
                temper_core::float64::near(1.0, nan, None, None)
            );
            println!(
                "{} {} {} {} {} {}",
                temper_core::float64::near(inf, 1.0, None, None),
                temper_core::float64::near(inf, ninf, None, None),
                temper_core::float64::near(inf, 1.0, Some(0.0), Some(0.0)),
                temper_core::float64::near(inf, inf, Some(0.0), Some(0.0)),
                temper_core::float64::near(inf, 1.0, Some(1.0), Some(inf)),
                temper_core::float64::near(1.0, ninf, Some(1.0), Some(inf))
            );
            println!(
                "{} {} {} {}",
                temper_core::float64::near(1.0, 1.5, Some(nan), Some(1.0)),
                temper_core::float64::near(1.0, 1.5, Some(1.0), Some(nan)),
                temper_core::float64::near(1.0, 1.0, Some(nan), Some(nan)),
                temper_core::float64::near(-0.0, 0.0, Some(0.0), Some(0.0))
            );
            println!(
                "{} {} {} {}",
                temper_core::float64::near(1.0, 1.0 + 1e-10, None, None),
                temper_core::float64::near(1.0, 1.1, None, None),
                temper_core::float64::near(1.0, 1.1, Some(0.2), None),
                temper_core::float64::near(0.0, 1e-300, Some(1e-9), Some(1e-12))
            );
            Ok(())
        })
        .clone()
}

static DIGITS: OnceLock<temper_core::List<i32>> = OnceLock::new();

pub(crate) fn digits() -> temper_core::List<i32> {
    crate::support::initialized(|| DIGITS.get()).clone()
}

pub(crate) fn from(index: i32) -> i32 {
    temper_core::listed::reduce_from_index(
        &digits(),
        100,
        index,
        &(|s: i32, n: i32| s.wrapping_mul(10).wrapping_add(n)),
    )
}
