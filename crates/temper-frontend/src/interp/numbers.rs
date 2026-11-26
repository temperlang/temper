//! Number methods, conversions and formatting, matching Kotlin Temper's
//! (`FloatFns.kt`, `StandardLibraryHelpers.kt`, `FormatDouble.kt`).

use super::{CoreFailure, R, Val, to_string};
use std::cmp::Ordering;

/// Largest magnitude of an `Int64` that converts to and from `Float64`
/// exactly, as `Number.MAX_SAFE_INTEGER` in JavaScript.
const SAFE_INTEGER: i64 = (1 << 53) - 1;

/// Floats in Temper's total order, as Java's `Double.compare`: `-0.0 <
/// 0.0`, and `NaN` equals itself and is above everything else.
pub(super) fn float_cmp(a: f64, b: f64) -> Ordering {
    match (a.is_nan(), b.is_nan()) {
        (true, true) => Ordering::Equal,
        (true, false) => Ordering::Greater,
        (false, true) => Ordering::Less,
        _ => a.total_cmp(&b),
    }
}

/// A float as `toString` gives it: Java's shortest round-trip form, with
/// `e+N` exponents, and at most 16 digits after the point (cut, not
/// rounded), as `FormatDouble.kt` does.
pub(super) fn format_float(f: f64) -> String {
    if f.is_nan() {
        return "NaN".into();
    } else if f.is_infinite() {
        return if f > 0.0 { "Infinity" } else { "-Infinity" }.into();
    } else if f == 0.0 {
        return if f.is_sign_negative() { "-0.0" } else { "0.0" }.into();
    }
    // Java shows at least two significant digits, choosing the closest
    // such, so `5e-324` shows as `4.9e-324`.
    let mut sci = format!("{f:e}");
    if !sci.contains('.') {
        sci = format!("{f:.1e}");
    }
    let (mantissa, exponent) = sci.split_once('e').unwrap();
    let exponent: i32 = exponent.parse().unwrap();
    let (sign, mantissa) = mantissa
        .strip_prefix('-')
        .map_or(("", mantissa), |m| ("-", m));
    let mut text = String::from(sign);
    if (-3..7).contains(&exponent) {
        // Plain, as Java for 1e-3 <= |f| < 1e7.
        let digits: String = mantissa.chars().filter(|&c| c != '.').collect();
        let digits = digits.trim_end_matches('0');
        if exponent < 0 {
            text.push_str("0.");
            text.extend(std::iter::repeat_n('0', (-exponent - 1) as usize));
            text.push_str(digits);
        } else {
            let whole = exponent as usize + 1;
            let fraction = digits.get(whole..).unwrap_or("");
            text.push_str(&digits[..whole.min(digits.len())]);
            text.extend(std::iter::repeat_n('0', whole.saturating_sub(digits.len())));
            text.push('.');
            text.push_str(if fraction.is_empty() { "0" } else { fraction });
        }
    } else {
        text.push_str(mantissa);
        text.push_str(&format!(
            "e{}{}",
            if exponent < 0 { '-' } else { '+' },
            exponent.abs()
        ));
    }
    if let Some(dot) = text.find('.') {
        let end = text[dot + 1..]
            .find(|c: char| !c.is_ascii_digit())
            .map_or(text.len(), |i| dot + 1 + i);
        if end - dot - 1 > 16 {
            text.replace_range(dot + 17..end, "");
        }
    }
    text
}

/// `String.toFloat64()`: JSON's number syntax, plus `NaN` and `Infinity`,
/// around whitespace. Kotlin Temper accepts what the JVM does, as `1f`,
/// `-.5` and `0x1p3`, which its docs don't promise.
pub(super) fn parse_float(text: &str) -> Option<f64> {
    let text = text.trim();
    match text {
        "NaN" => return Some(f64::NAN),
        "Infinity" => return Some(f64::INFINITY),
        "-Infinity" => return Some(f64::NEG_INFINITY),
        _ => {}
    }
    let bytes = text.as_bytes();
    let mut i = usize::from(bytes.first() == Some(&b'-'));
    let digits = |i: &mut usize| {
        let start = *i;
        while bytes.get(*i).is_some_and(u8::is_ascii_digit) {
            *i += 1;
        }
        *i - start
    };
    let whole = digits(&mut i);
    if whole == 0 || (whole > 1 && bytes[i - whole] == b'0') {
        return None;
    }
    if bytes.get(i) == Some(&b'.') {
        i += 1;
        if digits(&mut i) == 0 {
            return None;
        }
    }
    if matches!(bytes.get(i), Some(b'e' | b'E')) {
        i += 1;
        if matches!(bytes.get(i), Some(b'+' | b'-')) {
            i += 1;
        }
        if digits(&mut i) == 0 {
            return None;
        }
    }
    if i != bytes.len() {
        return None;
    }
    text.parse().ok()
}

/// A `ParseError` for a number another type can't hold, its `input` the
/// number as `toString` gives it.
fn out_of_range(n: Val, reason: &str) -> super::Abrupt {
    CoreFailure::parse_error(to_string(&n), reason)
}

/// A float method, or `None` if there's no such method.
pub(super) fn float_method(f: f64, name: &str, args: &[Val]) -> Option<R> {
    let float = |x: f64| Some(Ok(Val::Float(x)));
    // A missing or null argument takes its default.
    let arg = |i: usize, default: f64| match args.get(i) {
        None | Some(Val::Null) => Some(default),
        Some(Val::Float(x)) => Some(*x),
        _ => None,
    };
    match (name, args) {
        ("toInt32", []) => {
            let n = f as i32;
            Some(match (f - n as f64).abs() < 1.0 {
                true => Ok(Val::Int(n)),
                false => Err(out_of_range(Val::Float(f), "out of Int32 range")),
            })
        }
        ("toInt32Unsafe", []) => Some(Ok(Val::Int(f as i32))),
        ("toInt64", []) => Some(match f.abs() <= SAFE_INTEGER as f64 {
            true => Ok(Val::Int64(f as i64)),
            false => Err(out_of_range(Val::Float(f), "out of Int64 range")),
        }),
        ("toInt64Unsafe", []) => Some(Ok(Val::Int64(f as i64))),
        ("abs", []) => float(f.abs()),
        ("acos", []) => float(f.acos()),
        ("asin", []) => float(f.asin()),
        ("atan", []) => float(f.atan()),
        ("atan2", [Val::Float(x)]) => float(f.atan2(*x)),
        ("ceil", []) => float(f.ceil()),
        ("cos", []) => float(f.cos()),
        ("cosh", []) => float(f.cosh()),
        ("exp", []) => float(f.exp()),
        ("expm1", []) => float(f.exp_m1()),
        ("floor", []) => float(f.floor()),
        ("log", []) => float(f.ln()),
        ("log10", []) => float(f.log10()),
        ("log1p", []) => float(f.ln_1p()),
        // NaN if either is, and `-0.0 < 0.0`, as Kotlin's `max` and `min`.
        ("max" | "min", [Val::Float(x)]) => float(if f.is_nan() || x.is_nan() {
            f64::NAN
        } else if (float_cmp(f, *x) == Ordering::Greater) == (name == "max") {
            f
        } else {
            *x
        }),
        ("near", [_, ..]) if args.len() <= 3 => {
            let (Some(other), Some(rel), Some(abs)) = (arg(0, f64::NAN), arg(1, 1e-9), arg(2, 0.0))
            else {
                return None;
            };
            let margin = (f.abs().max(other.abs()) * rel).max(abs);
            Some(Ok(Val::Bool((f - other).abs() <= margin)))
        }
        ("pred", []) => float(f - 1.0),
        ("succ", []) => float(f + 1.0),
        ("round", []) => float(f.round_ties_even()),
        // Zeros and NaN keep their own value.
        ("sign", []) => float(if f == 0.0 || f.is_nan() {
            f
        } else {
            f.signum()
        }),
        ("sin", []) => float(f.sin()),
        ("sinh", []) => float(f.sinh()),
        ("sqrt", []) => float(f.sqrt()),
        ("tan", []) => float(f.tan()),
        ("tanh", []) => float(f.tanh()),
        _ => None,
    }
}

/// An `Int` (32-bit) or `Int64` method, or `None` if there's no such
/// method.
pub(super) fn int_method(n: i64, is_64: bool, name: &str, args: &[Val]) -> Option<R> {
    let result = match (name, args, is_64) {
        ("toFloat64", [], false) | ("toFloat64Unsafe", [], true) => Val::Float(n as f64),
        ("toFloat64", [], true) if !(-SAFE_INTEGER..=SAFE_INTEGER).contains(&n) => {
            return Some(Err(out_of_range(Val::Int64(n), "not exact as Float64")));
        }
        ("toFloat64", [], true) => Val::Float(n as f64),
        ("toInt64", [], false) => Val::Int64(n),
        ("toInt32", [], true) if i32::try_from(n).is_err() => {
            return Some(Err(out_of_range(Val::Int64(n), "out of Int32 range")));
        }
        ("toInt32" | "toInt32Unsafe", [], true) => Val::Int(n as i32),
        _ => return None,
    };
    Some(Ok(result))
}

/// Today's date in UTC, as year, month and day, from the system clock,
/// or on wasm the page's ([`temper_os::host::now_ms`]), by Howard
/// Hinnant's `civil_from_days`.
pub fn today_utc() -> (i32, i32, i32) {
    let ms = temper_os::host::now_ms();
    let secs = if ms.is_finite() {
        (ms / 1000.0).floor() as i64
    } else {
        0
    };
    let z = secs.div_euclid(86_400) + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let day = doy - (153 * mp + 2) / 5 + 1;
    let month = if mp < 10 { mp + 3 } else { mp - 9 };
    let year = yoe + era * 400 + i64::from(month <= 2);
    (year as i32, month as i32, day as i32)
}
