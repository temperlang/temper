use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let mut one: f64 = 1.0;
            one = one;
            TWO.set(one + one).unwrap_or_else(|_| panic!());
            let half = one / two();
            near("half.abs()", half.abs(), half);
            near("(-half).abs()", (-half).abs(), half);
            let three = one + two();
            near("half.acos()", half.acos(), std::f64::consts::PI / three);
            let pi = std::f64::consts::PI;
            let six = two() * three;
            near("half.asin()", half.asin(), pi / six);
            let four = two() * two();
            near("(-one).atan()", (-one).atan(), -pi / four);
            near("one.atan2(-one)", one.atan2(-one), three * pi / four);
            near("(four / three).ceil()", (four / three).ceil(), two());
            near("(-four / three).ceil()", (-four / three).ceil(), -one);
            near("(pi / three).cos()", (pi / three).cos(), half);
            let e = std::f64::consts::E;
            near("one.cosh()", one.cosh(), (e + one / e) / two());
            near("half.exp() == e.sqrt()", half.exp(), e.sqrt());
            near("half.expm1()", half.exp_m1(), e.sqrt() - one);
            near("(four / three).floor()", (four / three).floor(), one);
            near("(-four / three).floor()", (-four / three).floor(), -two());
            near("e.log()", std::f64::consts::E.ln(), one);
            let five = one + four;
            let ten = two() * five;
            near("ten.log10()", ten.log10(), one);
            near("(e - one).log1p()", (e - one).ln_1p(), one);
            let max_min_combos = temper_core::listed::join(
                &Arc::new(vec![f64::NAN, one]),
                Arc::new(", ".to_string()),
                &(|x: f64| {
                    temper_core::listed::join(
                        &Arc::new(vec![f64::NAN, two()]),
                        Arc::new(", ".to_string()),
                        &(|y: f64| {
                            let pair = temper_core::listed::join(
                                &Arc::new(vec![
                                    temper_core::float64::max(x, y),
                                    temper_core::float64::min(x, y),
                                ]),
                                Arc::new(", ".to_string()),
                                &(|z: f64| temper_core::float64::to_string(z)),
                            );
                            Arc::new(format!("({pair})"))
                        }),
                    )
                }),
            );
            println!("maxMinCombos: {max_min_combos}");
            let quarter = half / two();
            near("2.25 % 2.0", (two() + quarter) % two(), 0.25);
            near("2.25 % -2.0", (two() + quarter) % -two(), 0.25);
            near("-2.25 % 2.0", -(two() + quarter) % two(), -0.25);
            near("-2.25 % -2.0", -(two() + quarter) % -two(), -0.25);
            near("pi.round()", pi.round(), three);
            near("(-pi).round()", (-pi).round(), -three);
            near("pi.sign()", temper_core::float64::sign(pi), one);
            near("(-pi).sign()", temper_core::float64::sign(-pi), -one);
            let zero = one - one;
            println!(
                "zero.sign(): {}",
                temper_core::float64::to_string(temper_core::float64::sign(zero))
            );
            println!(
                "(-zero).sign(): {}",
                temper_core::float64::to_string(temper_core::float64::sign(-zero))
            );
            near("(pi / six).sin()", (pi / six).sin(), half);
            near("one.sinh()", one.sinh(), (e - one / e) / two());
            near("(-pi / four).tan()", (-pi / four).tan(), -one);
            near("(three * pi / four).tan()", (three * pi / four).tan(), -one);
            near(
                "one.tanh()",
                one.tanh(),
                (e.powf(2.0) - 1.0) / (e.powf(2.0) + 1.0),
            );
            println!(
                "one.near(one + 0.1): {}",
                temper_core::float64::near(one, one + 0.1, None, None)
            );
            println!(
                "one.near(one + 0.25, absTol = 0.25): {}",
                temper_core::float64::near(one, one + 0.25, None, Some(0.25))
            );
            println!(
                "one.near(one + 0.1 + 1e-9, absTol = 0.1): {}",
                temper_core::float64::near(one, one + 0.1 + 1e-9, None, Some(0.1))
            );
            println!(
                "one.near(one - 0.1, absTol = 0.11): {}",
                temper_core::float64::near(one, one - 0.1, None, Some(0.11))
            );
            println!(
                "ten.near(ten + 0.1, absTol = 0.011): {}",
                temper_core::float64::near(ten, ten + 0.1, None, Some(0.011))
            );
            println!(
                "ten.near(ten + 0.1, relTol = 0.011): {}",
                temper_core::float64::near(ten, ten + 0.1, Some(0.011), None)
            );
            Ok(())
        })
        .clone()
}

pub(crate) fn near(label: impl ToArcString, actual: f64, expected: f64) {
    let label = label.to_arc_string();
    let result = {
        let subject = temper_core::float64::near(actual, expected, None, None);
        if subject {
            Arc::new("✅".to_string())
        } else {
            Arc::new(format!(
                "❌ wanted {}, was {}",
                temper_core::float64::to_string(expected),
                temper_core::float64::to_string(actual)
            ))
        }
    };
    println!("{label}: {result}");
}

static TWO: OnceLock<f64> = OnceLock::new();

pub(crate) fn two() -> f64 {
    *crate::support::initialized(|| TWO.get())
}
