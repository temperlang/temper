use super::{Error, Result};
use std::sync::Arc;

pub fn cmp(x: f64, y: f64) -> i32 {
    match (x.is_nan(), y.is_nan()) {
        (false, false) => match (x == 0.0, y == 0.0) {
            (true, true) => (x.signum() - y.signum()) as i32,
            _ => x.partial_cmp(&y).unwrap() as i32,
        },
        (x, y) => (x as i32) - (y as i32),
    }
    .signum()
}

pub fn cmp_option(x: Option<f64>, y: Option<f64>) -> i32 {
    match (x, y) {
        (Some(x), Some(y)) => cmp(x, y),
        _ => x.partial_cmp(&y).unwrap() as i32,
    }
}

pub fn div(x: f64, y: f64) -> Result<f64> {
    if y == 0.0 {
        return Err(Error::new());
    }
    Ok(x / y)
}

pub fn max(x: f64, y: f64) -> f64 {
    if x.is_nan() || y.is_nan() {
        return f64::NAN;
    }
    x.max(y)
}

pub fn min(x: f64, y: f64) -> f64 {
    if x.is_nan() || y.is_nan() {
        return f64::NAN;
    }
    x.min(y)
}

pub fn near(x: f64, y: f64, rel_tol: Option<f64>, abs_tol: Option<f64>) -> bool {
    let rel_tol = rel_tol.unwrap_or(1e-9);
    let abs_tol = abs_tol.unwrap_or(0.0);
    let margin = (x.abs().max(y.abs()) * rel_tol).max(abs_tol);
    (x - y).abs() < margin
}

pub fn rem(x: f64, y: f64) -> Result<f64> {
    if y == 0.0 {
        return Err(Error::new());
    }
    Ok(x % y)
}

pub fn sign(x: f64) -> f64 {
    match () {
        _ if x == 0.0 => x,
        _ => x.signum(),
    }
}

pub fn to_int(x: f64) -> Result<i32> {
    match () {
        _ if x > (i32::MIN as f64) - 1.0 && x < (i32::MAX as f64) + 1.0 => Ok(x as i32),
        _ => Err(Error::new()),
    }
}

const MANTISSA_MAX: f64 = (1i64 << 53) as f64;

pub fn to_int64(x: f64) -> Result<i64> {
    match () {
        _ if x > -MANTISSA_MAX && x < MANTISSA_MAX => Ok(x as i64),
        _ => Err(Error::new()),
    }
}

pub fn to_string(x: f64) -> Arc<String> {
    match () {
        // No floats in patterns, but still can compare.
        _ if x == f64::INFINITY => "Infinity".to_string(),
        _ if x == f64::NEG_INFINITY => "-Infinity".to_string(),
        // "NaN" is default for NaN, but avoid later logic.
        _ if x.is_nan() => "NaN".to_string(),
        _ => match () {
            _ if x == 0.0 || (0.001..10_000_000.0).contains(&x.abs()) => {
                let mut text = x.to_string();
                if !text.contains('.') {
                    text.push_str(".0");
                }
                text
            }
            _ => {
                let mut text = format!("{x:e}");
                if x.abs() > 1.0 {
                    let index = text.find('e').unwrap();
                    let replacement = match () {
                        _ if text.contains('.') => "e+",
                        _ => ".0e+",
                    };
                    text.replace_range(index..index + 1, replacement);
                }
                text
            }
        },
    }
    .into()
}
