use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            report_slice_info("κόσμε𐆊");
            report_slice_info("Hi");
            compare_ends("Hi");
            println!("{}", temper_core::string::from_code_point(127757)?);
            println!("{}", from_code_no_inline(127757)?);
            println!(
                "{}",
                from_code_no_inline(1114112).unwrap_or_else(|_| Arc::new("nope".to_string()))
            );
            println!(
                "{}",
                from_code_no_inline(55296)
                    .unwrap_or_else(|_| Arc::new("no to surrogate".to_string()))
            );
            println!(
                "{}",
                from_codes_no_inline(Arc::new(vec![128059, 8205, 10052, 65039]))?
            );
            println!(
                "{}",
                from_codes_no_inline(Arc::new(vec![55296, 56320]))
                    .unwrap_or_else(|_| Arc::new("no to surrogates".to_string()))
            );
            check_parse("-1", None);
            check_parse("2", None);
            check_parse(" 2 ", None);
            check_parse("2.0", None);
            check_parse("2a", None);
            check_parse("0x10", None);
            check_parse("totally", None);
            check_parse("2a", Some(16));
            check_parse("20", Some(7));
            check_parse("20", Some(17));
            check_parse("20", Some(37));
            {
                let s = Arc::new("foo-bar".to_string());
                let after1_2 = temper_core::string::next(&s, 0usize);
                let after2_2 = temper_core::string::next(&s, after1_2);
                let after3 = temper_core::string::next(&s, after2_2);
                let after4 = temper_core::string::next(&s, after3);
                let after4_b = temper_core::string::step(&s, 0usize, 4);
                let before1 = temper_core::string::prev(&s, after4_b);
                let before3 = temper_core::string::step(&s, before1, -2);
                let before3_b = temper_core::string::step(&s, before3, 0);
                let way_before = temper_core::string::step(&s, before3, -100);
                let way_after = temper_core::string::step(&s, before3, 100);
                println!(
                    "s[3:]  -> {}",
                    temper_core::string::slice(&s, after3, s.len())
                );
                println!(
                    "s[:3]  -> {}",
                    temper_core::string::slice(&s, 0usize, after3)
                );
                println!(
                    "s[1:4] -> {}",
                    temper_core::string::slice(&s, after1_2, after4)
                );
                println!(
                    "s[:4]  -> {}",
                    temper_core::string::slice(&s, 0usize, after4)
                );
                println!(
                    "s[1:4] -> {}",
                    temper_core::string::slice(&s, before3_b, after4_b)
                );
                println!(
                    "s[-many:many] -> {}",
                    temper_core::string::slice(&s, way_before, way_after)
                );
            }
            HAY.set(Arc::new(
                "It was the best of times, it was the worst of times, ...".to_string(),
            ))
            .unwrap_or_else(|_| panic!());
            let needle = Arc::new("time".to_string());
            let after1 = report_found("found 1", hay(), needle.clone(), None);
            let after2 = report_found("found 2", hay(), needle.clone(), Some(after1));
            report_found("found 3", hay(), needle.clone(), Some(after2));
            report_found("hay 2", "time comes for us all", needle.clone(), None);
            report_found("hay 3", "in the nick of time", needle.clone(), None);
            report_found("hay 4", "my name is tim", needle.clone(), None);
            report_found("hay 5", "tim", needle.clone(), None);
            let nul_hay = Arc::new("ab\0#\0".to_string());
            println!("nul: {}", found_at(nul_hay.clone(), "\0", None));
            let after_nul = temper_core::string::next(
                &nul_hay,
                temper_core::string::next(&nul_hay, temper_core::string::next(&nul_hay, 0usize)),
            );
            println!(
                "nul again: {}",
                found_at(nul_hay.clone(), "\0", Some(after_nul))
            );
            println!("soft hyphen: {}", found_at("ab\u{ad}#", "\u{ad}", None));
            println!("past soft hyphen: {}", found_at("ab\u{ad}#", "b#", None));
            println!("zwj: {}", found_at("a\u{200d}b", "ab", None));
            println!("composed: {}", found_at("é", "é", None));
            Ok(())
        })
        .clone()
}

pub(crate) fn report_slice_info(s: impl ToArcString) {
    let s = s.to_arc_string();
    let start: usize = 0usize;
    let end = s.len();
    let count = temper_core::string::count_between(&s, start, end);
    println!("Count of code-points for `{s}`: {count}");
    println!(
        "Has at least 0: {}",
        temper_core::string::has_at_least(&s, start, end, 0)
    );
    println!(
        "Has at least 1: {}",
        temper_core::string::has_at_least(&s, start, end, 1)
    );
    println!(
        "Has at least 2: {}",
        temper_core::string::has_at_least(&s, start, end, 2)
    );
    println!(
        "Has at least 3: {}",
        temper_core::string::has_at_least(&s, start, end, 3)
    );
    println!(
        "Has at least 4: {}",
        temper_core::string::has_at_least(&s, start, end, 4)
    );
    println!(
        "Has at least 5: {}",
        temper_core::string::has_at_least(&s, start, end, 5)
    );
    println!(
        "Has at least 6: {}",
        temper_core::string::has_at_least(&s, start, end, 6)
    );
    println!(
        "Has at least 7: {}",
        temper_core::string::has_at_least(&s, start, end, 7)
    );
    println!(
        "Has at least 8: {}",
        temper_core::string::has_at_least(&s, start, end, 8)
    );
    println!("code-points");
    let mut i: usize = 0usize;
    while temper_core::string::has_index(&s, i) {
        let cp = temper_core::string::get(&s, i);
        i = temper_core::string::next(&s, i);
        println!("  {}", temper_core::int_to_string(cp, Some(16)));
    }
}

pub(crate) fn compare_ends(message: impl ToArcString) {
    let message = message.to_arc_string();
    let start: usize = 0usize;
    let end = message.len();
    println!("start < end? {}", (Some(start).cmp(&Some(end)) as i32) < 0);
    println!("start > end? {}", Some(start).cmp(&Some(end)) as i32 > 0);
    println!("start == start? {}", Some(start) == Some(start));
    println!("end < end? {}", (Some(end).cmp(&Some(end)) as i32) < 0);
    println!("end <= end? {}", Some(end).cmp(&Some(end)) as i32 <= 0);
}

pub(crate) fn from_code_no_inline(code: i32) -> temper_core::Result<Arc<String>> {
    println!("From 0x{}", temper_core::int_to_string(code, Some(16)));
    temper_core::string::from_code_point(code)
}

pub(crate) fn from_codes_no_inline(
    codes: impl temper_core::ToListed<i32>,
) -> temper_core::Result<Arc<String>> {
    let codes = codes.to_listed();
    println!("From codes");
    temper_core::string::from_code_points(&*codes)
}

pub(crate) fn check_parse(string: impl ToArcString, radix: Option<i32>) {
    let string = string.to_arc_string();
    let converted = (|| {
        Ok::<_, temper_core::Error>(Arc::new(
            (if let Some(radix) = radix {
                temper_core::string::to_int(&string, Some(radix))?
            } else {
                temper_core::string::to_int(&string, None)?
            })
            .to_string(),
        ))
    })()
    .unwrap_or_else(|_| Arc::new("failed".to_string()));
    println!("Parse {string} -> {converted}");
}

static HAY: OnceLock<Arc<String>> = OnceLock::new();

pub(crate) fn hay() -> Arc<String> {
    crate::support::initialized(|| HAY.get()).clone()
}

pub(crate) fn report_found(
    label: impl ToArcString,
    haystack: impl ToArcString,
    needle: impl ToArcString,
    start: Option<usize>,
) -> usize {
    let label = label.to_arc_string();
    let haystack = haystack.to_arc_string();
    let needle = needle.to_arc_string();
    let start = start.unwrap_or(0usize);
    let index = temper_core::string::index_of(&haystack, needle.clone(), Some(start));
    if let Some(index) = index {
        let before =
            temper_core::string::slice(&haystack, temper_core::string::prev(hay(), start), index);
        println!("{label}: {before}...");
        temper_core::string::next(&haystack, index)
    } else {
        println!("{label}: (nope)");
        0usize
    }
}

pub(crate) fn found_at(
    haystack: impl ToArcString,
    needle: impl ToArcString,
    start: Option<usize>,
) -> i32 {
    let haystack = haystack.to_arc_string();
    let needle = needle.to_arc_string();
    let start = start.unwrap_or(0usize);
    let index = temper_core::string::index_of(&haystack, needle.clone(), Some(start));
    if let Some(index) = index {
        temper_core::string::count_between(&haystack, 0usize, index)
    } else {
        -1
    }
}
