use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            compare_positions("cookie", 99, 105);
            compare_positions("cookie", 107, 111);
            compare_positions("cookie", 107, 107);
            compare_positions("cookie", 120, 99);
            compare_positions("cookie", 101, 120);
            compare_positions("cookie", 120, 121);
            let s = Arc::new("cookie".to_string());
            {
                let list: temper_core::List<i32> = Arc::new(vec![111, 105, 122]);
                let length = temper_core::ListedTrait::len(&list);
                let mut i: i32 = 0;
                while i < length {
                    let c = temper_core::ListedTrait::get(&list, i);
                    i = i.wrapping_add(1);
                    println!(
                        "'{}' in \"{s}\": {}, {}, {}, {}, {}",
                        temper_core::string::from_code_point(c)
                            .unwrap_or_else(|_| Arc::new("?".to_string())),
                        if has0(s.clone(), c) { "y" } else { "n" },
                        if has1(s.clone(), c) { "y" } else { "n" },
                        if has2(s.clone(), c) { "y" } else { "n" },
                        if has3(s.clone(), c) { "y" } else { "n" },
                        if has4(s.clone(), c) { "y" } else { "n" }
                    );
                }
            }
            {
                let string_builder = Arc::new(std::sync::RwLock::new(String::new()));
                temper_core::string::builder::append(
                    &string_builder,
                    Arc::new("Hello".to_string()),
                );
                temper_core::string::builder::append(&string_builder, Arc::new(",".to_string()));
                let after_comma = temper_core::string::builder::end(&string_builder);
                temper_core::string::builder::append(
                    &string_builder,
                    Arc::new(" World!".to_string()),
                );
                let str = temper_core::string::builder::to_string(&string_builder);
                println!("{}", temper_core::string::slice(&str, 0usize, after_comma));
                println!(
                    "{}",
                    temper_core::string::slice(&str, after_comma, str.len())
                );
            }
            Ok(())
        })
        .clone()
}

pub(crate) fn index_of_char(s: impl ToArcString, c: i32) -> Option<usize> {
    let s = s.to_arc_string();
    let mut i: usize = 0usize;
    while temper_core::string::has_index(&s, i) {
        if temper_core::string::get(&s, i) == c {
            return Some(i);
        }
        i = temper_core::string::next(&s, i);
    }
    None
}

pub(crate) fn compare_positions(s: impl ToArcString, a: i32, b: i32) {
    let s = s.to_arc_string();
    let delta = index_of_char(s.clone(), a).cmp(&index_of_char(s.clone(), b)) as i32;
    let rel_pos = if delta < 0 {
        Arc::new("before".to_string())
    } else if delta == 0 {
        Arc::new("at".to_string())
    } else {
        Arc::new("after".to_string())
    };
    println!(
        "'{}' is {rel_pos} '{}' in \"{s}\".",
        temper_core::string::from_code_point(a).unwrap_or_else(|_| Arc::new("?".to_string())),
        temper_core::string::from_code_point(b).unwrap_or_else(|_| Arc::new("?".to_string()))
    );
}

pub(crate) fn has0(s: impl ToArcString, c: i32) -> bool {
    let s = s.to_arc_string();
    index_of_char(s.clone(), c).is_some()
}

pub(crate) fn has1(s: impl ToArcString, c: i32) -> bool {
    let s = s.to_arc_string();
    let i = index_of_char(s.clone(), c);
    i.is_some()
}

pub(crate) fn has2(s: impl ToArcString, c: i32) -> bool {
    let s = s.to_arc_string();
    let i = index_of_char(s.clone(), c);
    i.is_some()
}

pub(crate) fn has3(s: impl ToArcString, c: i32) -> bool {
    let s = s.to_arc_string();
    let i = index_of_char(s.clone(), c);
    i.is_some()
}

pub(crate) fn has4(s: impl ToArcString, c: i32) -> bool {
    let s = s.to_arc_string();
    let i = index_of_char(s.clone(), c);
    i.is_some()
}
