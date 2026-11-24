//! `String`, `StringIndex` and `StringBuilder` methods, matching Kotlin
//! Temper's (`StringFns.kt`, and core's Temper bodies, `lib/core/`, for
//! those written in Temper).
//!
//! A string index is a UTF-8 byte offset here, where Kotlin's is a UTF-16
//! one; Temper code can't tell, as indices are opaque. Indices out of
//! bounds clamp to the end, as Kotlin's do. One from another string may fall
//! inside a character here; such an index counts as the start of that
//! character (Kotlin's would point at a trailing surrogate instead).

use super::{Abrupt, R, Val};
use std::cell::RefCell;
use std::rc::Rc;
use temper_syntax::lex::is_kotlin_whitespace;

fn bubble() -> Abrupt {
    Abrupt::Fail(None)
}

/// An index as an offset into `text`: clamped to its end, and moved back
/// to the start of a character.
fn at(text: &str, index: u32) -> usize {
    let mut i = (index as usize).min(text.len());
    while !text.is_char_boundary(i) {
        i -= 1;
    }
    i
}

/// Two indices as a range of `text`, clamped as Kotlin's
/// `withStringAndTwoBounds` does, so that `begin <= end`.
fn range(text: &str, begin: u32, end: u32) -> (usize, usize) {
    let begin = at(text, begin);
    (begin, at(text, end).max(begin))
}

fn index(i: usize) -> Val {
    Val::StringIndex(i as u32)
}

/// `s.next(i)`: the index after the character at `i`, or the end.
fn next(text: &str, i: u32) -> usize {
    let i = i as usize;
    if i >= text.len() {
        return text.len();
    }
    let mut next = i + 1;
    while !text.is_char_boundary(next) {
        next += 1;
    }
    next
}

/// `s.prev(i)`: the index of the character before `i`, or the beginning.
fn prev(text: &str, i: u32) -> usize {
    let mut i = (i as usize).min(text.len());
    if i > 0 {
        i -= 1;
        while !text.is_char_boundary(i) {
            i -= 1;
        }
    }
    i
}

/// `s[i]`: the code point at `i`, panicking past the end, as `String.get`'s
/// docs and the backends do. (Kotlin's interpreter fails instead.)
pub(super) fn get(text: &str, i: u32) -> R {
    match text[at(text, i)..].chars().next() {
        Some(c) if (i as usize) < text.len() => Ok(Val::Int(c as i32)),
        _ => Err(Abrupt::Panic(format!(
            "index {i} out of bounds for length {}",
            text.len()
        ))),
    }
}

/// A `String` method that doesn't call back, or `None` if there's none.
pub(super) fn string_method(text: &str, name: &str, args: &[Val]) -> Option<R> {
    use Val::StringIndex as I;
    Some(Ok(match (name, args) {
        ("get", [I(i)]) => return Some(get(text, *i)),
        ("hasIndex", [I(i)]) => Val::Bool((*i as usize) < text.len()),
        ("next", [I(i)]) => index(next(text, *i)),
        ("prev", [I(i)]) => index(prev(text, *i)),
        // As core's, stopping at either end.
        ("step", [I(i), Val::Int(by)]) => {
            let mut i = *i;
            for _ in 0..by.unsigned_abs() {
                let stepped = if *by > 0 {
                    next(text, i)
                } else {
                    prev(text, i)
                } as u32;
                if stepped == i {
                    break;
                }
                i = stepped;
            }
            I(i)
        }
        ("countBetween", [I(begin), I(end)]) => {
            let (begin, end) = range(text, *begin, *end);
            Val::Int(text[begin..end].chars().count() as i32)
        }
        // Counts only when the byte count can't settle it: a code point
        // takes 1 to 4 bytes.
        ("hasAtLeast", [I(begin), I(end), Val::Int(min)]) => {
            let (begin, end) = range(text, *begin, *end);
            let (bytes, min) = ((end - begin) as i64, *min as i64);
            Val::Bool(if bytes < min {
                false
            } else if bytes >= min * 4 {
                true
            } else {
                text[begin..end].chars().take(min as usize).count() as i64 >= min
            })
        }
        ("slice", [I(begin), I(end)]) => {
            let (begin, end) = range(text, *begin, *end);
            Val::Str(text[begin..end].into())
        }
        ("indexOf", [Val::Str(target), rest @ ..]) if rest.len() <= 1 => {
            let start = match rest {
                [] | [Val::Null] => 0,
                [I(i)] => *i,
                _ => return None,
            };
            index_of(text, target, start)
        }
        ("toInt32" | "toInt64", []) => return Some(parse_int(text, 10, name == "toInt64")),
        ("toInt32" | "toInt64", [Val::Null]) => {
            return Some(parse_int(text, 10, name == "toInt64"));
        }
        ("toInt32" | "toInt64", [Val::Int(radix)]) => {
            return Some(parse_int(text, *radix, name == "toInt64"));
        }
        ("toFloat64", []) => {
            return Some(
                super::numbers::parse_float(text)
                    .map(Val::Float)
                    .ok_or_else(bubble),
            );
        }
        // Splitting by "" gives each code point.
        ("split", [Val::Str(separator)]) => {
            let parts: Vec<Val> = if separator.is_empty() {
                text.chars()
                    .map(|c| Val::Str(c.to_string().into()))
                    .collect()
            } else {
                text.split(&**separator)
                    .map(|p| Val::Str(p.into()))
                    .collect()
            };
            Val::List(parts.into())
        }
        _ => return None,
    }))
}

/// `s.indexOf(target, start)`, as core's: the first index from
/// `start` where `target` is, or `StringIndex.none`. That finds an empty
/// target at `start`, even past the end.
fn index_of(text: &str, target: &str, start: u32) -> Val {
    if start as usize > text.len() {
        return if target.is_empty() {
            Val::StringIndex(start)
        } else {
            Val::NoStringIndex
        };
    }
    let mut start = start as usize;
    while !text.is_char_boundary(start) {
        start += 1;
    }
    match text[start..].find(target) {
        Some(at) => index(start + at),
        None => Val::NoStringIndex,
    }
}

/// `String.toInt32(radix)` and `toInt64`: an optional sign and digits,
/// around whitespace, as Kotlin's `trim().toIntOrNull(radix)`, for radixes
/// 2 through 36. Kotlin also takes non-ASCII digits, which we don't.
fn parse_int(text: &str, radix: i32, is_64: bool) -> R {
    if !(2..=36).contains(&radix) {
        return Err(bubble());
    }
    let text = text.trim_matches(is_kotlin_whitespace);
    let (negative, digits) = match text.as_bytes().first() {
        Some(b'-') => (true, &text[1..]),
        Some(b'+') => (false, &text[1..]),
        _ => (false, text),
    };
    if digits.is_empty() {
        return Err(bubble());
    }
    let mut n: i128 = 0;
    for c in digits.chars() {
        let digit = c.to_digit(radix as u32).ok_or_else(bubble)?;
        n = n * radix as i128 + digit as i128;
        if n > 1 << 64 {
            return Err(bubble());
        }
    }
    let n = if negative { -n } else { n };
    match is_64 {
        true => i64::try_from(n).map(Val::Int64).map_err(|_| bubble()),
        false => i32::try_from(n).map(Val::Int).map_err(|_| bubble()),
    }
}

/// `n.toString(radix)`: lowercase digits, with `-` for negatives, for
/// radixes 2 through 36; others fail, as in Kotlin Temper.
pub(super) fn int_to_string(n: i64, radix: i32) -> R {
    if !(2..=36).contains(&radix) {
        return Err(bubble());
    }
    let mut magnitude = n.unsigned_abs();
    let mut digits = Vec::new();
    loop {
        digits.push(
            std::char::from_digit((magnitude % radix as u64) as u32, radix as u32).unwrap_or('?'),
        );
        magnitude /= radix as u64;
        if magnitude == 0 {
            break;
        }
    }
    if n < 0 {
        digits.push('-');
    }
    Ok(Val::Str(digits.iter().rev().collect::<String>().into()))
}

/// A code point as a `char`, failing for surrogates and past `0x10FFFF`.
fn scalar(code: i32) -> Result<char, Abrupt> {
    u32::try_from(code)
        .ok()
        .and_then(char::from_u32)
        .ok_or_else(bubble)
}

/// `String`'s static methods.
pub(super) fn static_method(name: &str, args: &[Val]) -> Option<R> {
    Some(match (name, args) {
        ("fromCodePoint", [Val::Int(code)]) => {
            scalar(*code).map(|c| Val::Str(c.to_string().into()))
        }
        ("fromCodePoints", [list]) => super::with_items(list, |items| {
            items
                .iter()
                .map(|item| match item {
                    Val::Int(code) => scalar(*code),
                    _ => Err(bubble()),
                })
                .collect::<Result<String, _>>()
                .map(|text| Val::Str(text.into()))
        }),
        _ => return None,
    })
}

/// `char'a'`, called as a tag with its literal parts and interpolations:
/// the one code point in it, escapes decoded, or failure.
pub(super) fn char_tag(args: &[Val]) -> R {
    let [Val::List(parts), Val::List(_)] = args else {
        return Err(bubble());
    };
    let [Val::Str(raw)] = &parts[..] else {
        return Err(bubble());
    };
    let text = temper_syntax::unpack::unpack_string(raw, false).ok_or_else(bubble)?;
    let mut chars = text.chars();
    match (chars.next(), chars.next()) {
        (Some(c), None) => Ok(Val::Int(c as i32)),
        _ => Err(bubble()),
    }
}

/// A `StringBuilder` method, or `None` if there's none.
pub(super) fn builder_method(builder: &Rc<RefCell<String>>, name: &str, args: &[Val]) -> Option<R> {
    Some(Ok(match (name, args) {
        ("append", [Val::Str(text)]) => {
            builder.borrow_mut().push_str(text);
            Val::Void
        }
        ("appendCodePoint", [Val::Int(code)]) => match scalar(*code) {
            Ok(c) => {
                builder.borrow_mut().push(c);
                Val::Void
            }
            Err(abrupt) => return Some(Err(abrupt)),
        },
        // As core's, which appends the code points from `begin`
        // up to `end` or the source's end.
        (
            "appendBetween",
            [
                Val::Str(source),
                Val::StringIndex(begin),
                Val::StringIndex(end),
            ],
        ) => {
            let (begin, end) = range(source, *begin, *end);
            builder.borrow_mut().push_str(&source[begin..end]);
            Val::Void
        }
        ("clear", []) => {
            builder.borrow_mut().clear();
            Val::Void
        }
        ("toString", []) => Val::Str(builder.borrow().as_str().into()),
        _ => return None,
    }))
}
