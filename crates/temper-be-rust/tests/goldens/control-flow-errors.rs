use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            report(Some("hi"));
            report(None::<Arc<String>>);
            println!("{}", calc_rows(3, 6)?);
            println!("{}", count_below("abc").unwrap_or(-1));
            println!(
                "{} {}",
                first_over(1).unwrap_or(-1),
                is_none((|| Ok::<_, Missing>(Some(first_over(1)?)))().unwrap_or(None))
            );
            println!("{}", via_or_else(2));
            println!("{}", via_or_else(12));
            println!(
                "{} {} {}",
                pick(true),
                {
                    let subject = named(true);
                    subject.clone().unwrap_or_else(|| Arc::new("-".to_string()))
                },
                {
                    let subject2 = named(false);
                    subject2
                        .clone()
                        .unwrap_or_else(|| Arc::new("null".to_string()))
                }
            );
            println!(
                "{} {} {}",
                half(4).unwrap_or(-1),
                half(3).unwrap_or(-1),
                half(-2).unwrap_or(-1)
            );
            println!("{} {}", chosen(true), shout(Some("hey")));
            branch(0, 2);
            branch(2, 0);
            Ok(())
        })
        .clone()
}

#[derive(Clone, Debug)]
pub(crate) enum Problem {
    Missing(Missing),
}

temper_core::impl_failure_enum!(Problem, Missing(Missing));

struct MissingStruct {
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct Missing(Arc<MissingStruct>);

impl Missing {
    pub fn new() -> Missing {
        Missing(Arc::new(MissingStruct {
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new("missing".to_string())
    }
}

temper_core::impl_failure!(Missing, message, cause);

temper_core::impl_any_value_trait!(Missing, [Problem]);

pub(crate) fn more(x: Option<i32>) -> Result<i32, Missing> {
    Err(Missing::new())
}

pub(crate) fn something(x: Option<impl ToArcString>) -> Result<Arc<String>, Missing> {
    let x = x.map(|x| x.to_arc_string());
    if let Some(x) = &x {
        Ok(x.clone())
    } else {
        Err(Missing::new())
    }
}

pub(crate) fn report(x: Option<impl ToArcString>) {
    let x = x.map(|x| x.to_arc_string());
    let message = something(x.clone()).unwrap_or_else(|_| Arc::new("null".to_string()));
    let suffix = (|| {
        Ok::<_, Missing>(Arc::new(
            more(Some(if x.is_some() { 1 } else { 0 }))?.to_string(),
        ))
    })()
    .unwrap_or_else(|_| Arc::new(String::new()));
    println!("{message}{suffix}");
}

pub(crate) fn calc_rows(cols: i32, total: i32) -> Result<i32, Missing> {
    println!("calcRows({cols}, {total})");
    if total.wrapping_rem(cols) != 0 {
        return Err(Missing::new());
    }
    Ok(total.wrapping_div(cols))
}

pub(crate) fn parsed(s: impl ToArcString) -> Result<i32, temper_core::ParseError> {
    let s = s.to_arc_string();
    temper_core::string::to_int(&s, None)
}

pub(crate) fn count_below(s: impl ToArcString) -> Result<i32, temper_core::ParseError> {
    let s = s.to_arc_string();
    let mut i: i32 = 0;
    while i < parsed(s.clone())? {
        i = i.wrapping_add(1);
    }
    Ok(i)
}

pub(crate) fn first_over(n: i32) -> Result<i32, Missing> {
    let mut i: i32 = 0;
    while i < 9 {
        i = i.wrapping_add(1);
        if i > n {
            break;
        }
    }
    Ok(i)
}

pub(crate) fn sum_skipping(n: i32) -> Result<i32, Missing> {
    let mut sum: i32 = 0;
    {
        let mut i: i32 = 0;
        while i < 5 {
            if i == n {
                {
                    i = i.wrapping_add(1);
                    continue;
                }
            }
            sum = sum.wrapping_add(i);
            i = i.wrapping_add(1);
        }
    }
    Ok(sum)
}

pub(crate) fn ten_times(n: i32) -> Result<i32, Missing> {
    {
        let mut i: i32 = 0;
        while i < 9 {
            if i == n {
                return Ok(i.wrapping_mul(10));
            }
            i = i.wrapping_add(1);
        }
    }
    Err(Missing::new())
}

pub(crate) fn is_none(r: Option<i32>) -> bool {
    r.is_none()
}

pub(crate) fn via_or_else(n: i32) -> Arc<String> {
    Arc::new(format!(
        "{} {} {}",
        first_over(n).unwrap_or(-1),
        sum_skipping(n).unwrap_or(-1),
        ten_times(n).unwrap_or(-1)
    ))
}

pub(crate) fn unreachable_int() -> i32 {
    panic!();
}

pub(crate) fn fail_as<T: Clone + Send + Sync + 'static>(fallback: T) -> Result<T, Missing> {
    Err(Missing::new())
}

pub(crate) fn bail() -> Result<(), Missing> {
    Err(Missing::new())
}

pub(crate) fn no_name() -> Option<Arc<String>> {
    None
}

pub(crate) fn stop() {
    panic!();
}

pub(crate) fn half(n: i32) -> Result<i32, Missing> {
    if n.wrapping_rem(2) != 0 {
        bail()?;
        panic!();
    }
    if n < 0 {
        fail_as(n)
    } else {
        Ok(n.wrapping_div(2))
    }
}

pub(crate) fn pick(b: bool) -> i32 {
    if b {
        1
    } else {
        unreachable_int()
    }
}

pub(crate) fn named(b: bool) -> Option<Arc<String>> {
    if b {
        Some(Arc::new("named".to_string()))
    } else {
        no_name()
    }
}

pub(crate) fn chosen(b: bool) -> i32 {
    let x: i32;
    if b {
        x = 3;
        println!("chosen");
    } else {
        unreachable_int();
        panic!();
    }
    x
}

pub(crate) fn shout(s: Option<impl ToArcString>) -> Arc<String> {
    let s = s.map(|x| x.to_arc_string());
    if s.is_none() {
        stop();
        panic!();
    }
    let t = s.clone().unwrap();
    Arc::new(format!("{t}!"))
}

pub(crate) fn branch(a: i32, b: i32) {
    let c = if a == 0 {
        if b == 0 {
            Arc::new("00".to_string())
        } else if b == 1 {
            Arc::new("01".to_string())
        } else if b == 2 {
            Arc::new("02".to_string())
        } else {
            Arc::new("0x".to_string())
        }
    } else if a == 1 {
        Arc::new("1x".to_string())
    } else if a == 2 {
        Arc::new("2x".to_string())
    } else if a == 3 {
        Arc::new("3x".to_string())
    } else {
        Arc::new("xx".to_string())
    };
    println!("branch({a}, {b}) -> {c}");
}
