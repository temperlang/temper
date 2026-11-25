use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let negative_zero = -0.0;
            let mut zero: f64 = 0.0;
            zero = zero;
            let mut one: f64 = 1.0;
            one = one;
            let mut two: f64 = 2.0;
            two = two;
            let mut three: f64 = 3.0;
            three = three;
            if gt(one, zero) {
                println!("greater than");
            }
            if lt(zero, one) {
                println!("less than");
            }
            if eq(zero, one) {
                println!("equality is wrong");
            }
            if neq(zero, one) {
                println!("inequality");
            }
            println!("{}", temper_core::float64::to_string(zero));
            if neq(negative_zero, zero) {
                println!("zeroes not equal");
            }
            if lt(negative_zero, zero) {
                println!("negZero < zero");
            }
            if temper_core::float64::cmp(zero - zero, zero) == 0 {
                println!("zero subtraction");
            }
            if temper_core::float64::cmp(zero + zero, zero) == 0 {
                println!("zero addition");
            }
            if temper_core::float64::cmp(negative_zero - zero, negative_zero) == 0 {
                println!("negative zero minus zero is negative");
            }
            if temper_core::float64::cmp(negative_zero + zero, zero) == 0 {
                println!("negative zero plus zero is positive");
            }
            if temper_core::float64::cmp(zero - negative_zero, zero) == 0 {
                println!("zero minus negative zero is zero");
            }
            if temper_core::float64::cmp(zero + negative_zero, zero) == 0 {
                println!("zero plus negative zero is zero");
            }
            if temper_core::float64::cmp(negative_zero - negative_zero, zero) == 0 {
                println!("negative zero minus negative zero is zero");
            }
            if temper_core::float64::cmp(negative_zero + negative_zero, negative_zero) == 0 {
                println!("negative zero plus negative zero is negative zero");
            }
            println!("{}", temper_core::float64::to_string(negative_zero));
            let mut third = one / three;
            third = third;
            let mut half = one / two;
            half = half;
            let mut two_thirds = two / three;
            two_thirds = two_thirds;
            if gt(third, half) {
                println!("oops third > half");
            }
            if gt(half, two_thirds) {
                println!("oops half > twoThirds");
            }
            if neq(third * two, two_thirds) {
                println!("misaligned multiplication");
            }
            if neq(add(third, two_thirds), one) {
                println!("doesn't add back up");
            }
            println!("{}", temper_core::float64::to_string(half));
            println!("{}", temper_core::float64::to_string(4.0f64.powf(-half)));
            println!("{}", temper_core::float64::to_string(third));
            println!("{}", temper_core::float64::to_string(two_thirds));
            println!("{}", temper_core::float64::to_string(one));
            let almost_big = 1e25 * one;
            println!("{}", temper_core::float64::to_string(almost_big));
            let mut big = 99999999999999.0 * 999999999999.0;
            big = big;
            println!("{}", temper_core::float64::to_string(big));
            let mut bigger = big * big * big * big * big * big * big * big;
            bigger = bigger;
            println!("{}", temper_core::float64::to_string(bigger));
            let middling = -1500.0 * one;
            println!(
                "{} {} {} {}",
                temper_core::float64::to_string(middling),
                temper_core::float64::to_string(1000000.0 * one),
                temper_core::float64::to_string(9999999.0 * one),
                temper_core::float64::to_string(0.001 * one)
            );
            println!(
                "{} {} {}",
                temper_core::float64::to_string(1e21 * one),
                temper_core::float64::to_string(1.5e-10 * one),
                temper_core::float64::to_string(2e23 * one)
            );
            let mut biggest = bigger * bigger * bigger * bigger * bigger * bigger;
            biggest = biggest;
            println!("biggest {}", temper_core::float64::to_string(biggest));
            let mut tiniest = one / biggest;
            tiniest = tiniest;
            println!("tiniest {}", temper_core::float64::to_string(tiniest));
            let mut negative_infinity = biggest * -1.0;
            negative_infinity = negative_infinity;
            println!(
                "negative infinity {}",
                temper_core::float64::to_string(negative_infinity)
            );
            let mut infinity = f64::INFINITY;
            infinity = infinity;
            println!("{}", temper_core::float64::to_string(infinity));
            println!("processed an infinity");
            let mut nan = f64::NAN;
            nan = nan;
            println!("{}", temper_core::float64::to_string(nan));
            if temper_core::float64::cmp(nan, infinity) > 0 {
                println!("NaN is biggest");
            } else {
                println!("Infinity is biggest");
            }
            println!(
                "NaN == NaN? {}",
                temper_core::float64::cmp(nan, f64::NAN) == 0
            );
            println!(
                "Int to float: {}",
                temper_core::float64::to_string(two as i32 as f64)
            );
            println!("Positive to int: {}", (4.9 * one) as i32);
            println!("Negative to int: {}", (-4.9 * one) as i32);
            println!(
                "Infinity to int: {}",
                (|| {
                    Ok::<_, temper_core::Error>(Arc::new(
                        temper_core::float64::to_int(infinity)?.to_string(),
                    ))
                })()
                .unwrap_or_else(|_| Arc::new("no".to_string()))
            );
            println!(
                "NaN to int: {}",
                (|| {
                    Ok::<_, temper_core::Error>(Arc::new(
                        temper_core::float64::to_int(f64::NAN)?.to_string(),
                    ))
                })()
                .unwrap_or_else(|_| Arc::new("no".to_string()))
            );
            let one_int64 = one as i64;
            let not_so_big = 2147483647i64.wrapping_mul(one_int64);
            let not_so_small = (-2147483648i64).wrapping_mul(one_int64);
            let slightly_bigger = (not_so_big as f64 + 1.5) as i64;
            let slightly_smaller = (not_so_small as f64 - 1.5) as i64;
            let too_big_for_float64 =
                9007199254740992i64.wrapping_mul(temper_core::float64::to_int64(one)?);
            let ok_to_convert64 = too_big_for_float64.wrapping_sub(1) as f64;
            let sneaky_big = ok_to_convert64 + 1.0;
            println!("Bigger to Int64: {slightly_bigger}");
            println!("Smaller to Int64: {slightly_smaller}");
            println!("Barely ok to Int64: {}", ok_to_convert64 as i64);
            println!(
                "Sneaky big to Int64: {}",
                (|| {
                    Ok::<_, temper_core::Error>(Arc::new(
                        temper_core::float64::to_int64(sneaky_big)?.to_string(),
                    ))
                })()
                .unwrap_or_else(|_| Arc::new("no".to_string()))
            );
            println!(
                "Too big Int64 to Float64: {}",
                (|| {
                    Ok::<_, temper_core::Error>(temper_core::float64::to_string(
                        temper_core::int64_to_float64(too_big_for_float64)?,
                    ))
                })()
                .unwrap_or_else(|_| Arc::new("no".to_string()))
            );
            println!(
                "Infinity to Int64: {}",
                (|| {
                    Ok::<_, temper_core::Error>(Arc::new(
                        temper_core::float64::to_int64(infinity)?.to_string(),
                    ))
                })()
                .unwrap_or_else(|_| Arc::new("no".to_string()))
            );
            println!(
                "NaN to Int64: {}",
                (|| {
                    Ok::<_, temper_core::Error>(Arc::new(
                        temper_core::float64::to_int64(f64::NAN)?.to_string(),
                    ))
                })()
                .unwrap_or_else(|_| Arc::new("no".to_string()))
            );
            check_parse("-1.5");
            check_parse("-0");
            check_parse("2.0");
            check_parse("2");
            check_parse(" 2 ");
            check_parse("5e-1");
            check_parse("NaN");
            check_parse("-Infinity");
            check_tiny("2.225e-308");
            check_tiny("5e-324");
            check_parse("1e-400");
            check_parse("-1e-400");
            check_parse("2.");
            check_parse(".2");
            check_parse("2.0.0");
            check_parse("-inf");
            check_parse("totally");
            Ok(())
        })
        .clone()
}

pub(crate) fn gt(a: f64, b: f64) -> bool {
    temper_core::float64::cmp(a, b) > 0
}

pub(crate) fn lt(a: f64, b: f64) -> bool {
    temper_core::float64::cmp(a, b) < 0
}

pub(crate) fn eq(a: f64, b: f64) -> bool {
    temper_core::float64::cmp(a, b) == 0
}

pub(crate) fn neq(a: f64, b: f64) -> bool {
    temper_core::float64::cmp(a, b) != 0
}

pub(crate) fn add(a: f64, b: f64) -> f64 {
    a + b
}

pub(crate) fn check_parse(string: impl ToArcString) {
    let string = string.to_arc_string();
    let converted = (|| {
        Ok::<_, temper_core::Error>(temper_core::float64::to_string(
            temper_core::string::to_float64(&string)?,
        ))
    })()
    .unwrap_or_else(|_| Arc::new("failed".to_string()));
    println!("Parse {string} -> {converted}");
}

pub(crate) fn check_tiny(string: impl ToArcString) {
    let string = string.to_arc_string();
    let positive = (|| {
        Ok::<_, temper_core::Error>(Arc::new(
            (temper_core::float64::cmp(temper_core::string::to_float64(&string)?, 0.0) > 0)
                .to_string(),
        ))
    })()
    .unwrap_or_else(|_| Arc::new("failed".to_string()));
    println!("Parse {string} > 0 -> {positive}");
}
