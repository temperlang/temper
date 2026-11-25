use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let one: i32 = 1;
            let big = 2147483647i32.wrapping_mul(one);
            let small = (-2147483648i32).wrapping_neg().wrapping_mul(one);
            println!(
                "{} {} {} {}",
                big.wrapping_add(1),
                small.wrapping_sub(1),
                big.wrapping_mul(4),
                small.wrapping_neg()
            );
            println!(
                "{} {} {} {} {} {} {}",
                (-7i32).wrapping_mul(one).wrapping_shr(1),
                ((-7i32).wrapping_mul(one) as u32).wrapping_shr(28) as i32,
                one.wrapping_shl(33),
                !one,
                5 & 3,
                5 | 3,
                5 ^ 3
            );
            println!("{} {}", 3.cmp(&5) as i32, "b".cmp("a") as i32);
            println!(
                "{} {} {}",
                same(true, true),
                same(true, false),
                same(false, true)
            );
            println!("{} {} {}", quot(-7, 2)?, rem(-7, 2)?, quot(small, -1)?);
            println!("{}", words("apple", "banana"));
            println!("{}", words("pear", "pear"));
            println!("{}", words("zoo", "yak"));
            println!("{}", words("a", "b").as_str() == "a < b");
            let mut n: i32 = 0;
            let mut odd: i32 = 0;
            loop {
                n = n.wrapping_add(1);
                if n.wrapping_rem(2) != 0 {
                    odd = odd.wrapping_add(n);
                }
                if n >= 9 {
                    break;
                }
            }
            println!("odd {odd}");
            let mut found: i32 = -1;
            let mut i: i32 = 0;
            while i < 5 && found < 0 {
                let mut j: i32 = 0;
                while j < 5 {
                    if i.wrapping_mul(j) == 6 {
                        found = i.wrapping_mul(10).wrapping_add(j);
                        break;
                    }
                    j = j.wrapping_add(1);
                }
                i = i.wrapping_add(1);
            }
            println!("found {found}");
            let empty = Arc::new(String::new());
            let x = Arc::new("x".to_string());
            println!("{}", empty.is_empty() && !x.is_empty());
            Ok(())
        })
        .clone()
}

pub(crate) fn same(a: bool, b: bool) -> Arc<String> {
    Arc::new(format!("{} {} {}", a == b, a != b, a.cmp(&b) as i32))
}

pub(crate) fn quot(a: i32, b: i32) -> temper_core::Result<i32> {
    Ok(a.wrapping_div(b))
}

pub(crate) fn rem(a: i32, b: i32) -> temper_core::Result<i32> {
    Ok(a.wrapping_rem(b))
}

pub(crate) fn words(a: impl ToArcString, b: impl ToArcString) -> Arc<String> {
    let a = a.to_arc_string();
    let b = b.to_arc_string();
    if a < b {
        return Arc::new(format!("{a} < {b}"));
    }
    if a <= b {
        Arc::new(format!("{a} == {b}"))
    } else {
        Arc::new(format!("{a} > {b}"))
    }
}
