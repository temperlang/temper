use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("Int32");
            let mut one: i32 = 1;
            one = one;
            let big = 2147483647i32.wrapping_mul(one);
            let small = (-2147483648i32).wrapping_neg().wrapping_mul(one);
            println!("{big} + 1 == {}", big.wrapping_add(1));
            println!("{small} - 1 == {}", small.wrapping_sub(1));
            println!("{big} * 4 == {}", big.wrapping_mul(4));
            println!("{small} * 4 == {}", small.wrapping_mul(4));
            println!(
                "{} * 4 == {}",
                small.wrapping_add(1),
                small.wrapping_add(1).wrapping_mul(4)
            );
            println!(
                "{} * {big} == {}",
                small.wrapping_add(1),
                small.wrapping_add(1).wrapping_mul(big)
            );
            println!("{small} / -1 == {}", small.wrapping_div(-1));
            println!("{small} % -1 == {}", small.wrapping_rem(-1));
            println!("-({small}) == {}", small.wrapping_neg());
            let three = 3i32.wrapping_mul(one);
            let five = 5i32.wrapping_mul(one);
            println!("{five} / {three} == {}", five.wrapping_div(three));
            println!(
                "{five} / -{three} == {}",
                five.wrapping_div(three.wrapping_neg())
            );
            println!(
                "-{five} / {three} == {}",
                five.wrapping_neg().wrapping_div(three)
            );
            println!(
                "-{five} / -{three} == {}",
                five.wrapping_neg().wrapping_div(three.wrapping_neg())
            );
            println!("{five} % {three} == {}", five.wrapping_rem(three));
            println!(
                "{five} % -{three} == {}",
                five.wrapping_rem(three.wrapping_neg())
            );
            println!(
                "-{five} % {three} == {}",
                five.wrapping_neg().wrapping_rem(three)
            );
            println!(
                "-{five} % -{three} == {}",
                five.wrapping_neg().wrapping_rem(three.wrapping_neg())
            );
            println!("Int64");
            let not_so_big = big as i64;
            let not_so_small = small as i64;
            println!("{not_so_big} + 1 == {}", not_so_big.wrapping_add(1));
            println!("{not_so_small} - 1 == {}", not_so_small.wrapping_sub(1));
            let big64 = 9223372036854775807i64.wrapping_mul(one as i64);
            let small64 = (-9223372036854775808i64)
                .wrapping_neg()
                .wrapping_mul(one as i64);
            println!("{big64} + 1 == {}", big64.wrapping_add(1));
            println!("{small64} - 1 == {}", small64.wrapping_sub(1));
            println!("{small64} / -1 == {}", small64.wrapping_div(-1));
            println!("{small64} % -1 == {}", small64.wrapping_rem(-1));
            println!("-({small64}) == {}", small64.wrapping_neg());
            let three64 = three as i64;
            let five64 = five as i64;
            println!("{five64} / {three64} == {}", five64.wrapping_div(three64));
            println!(
                "{five64} / -{three64} == {}",
                five64.wrapping_div(three64.wrapping_neg())
            );
            println!(
                "-{five64} / {three64} == {}",
                five64.wrapping_neg().wrapping_div(three64)
            );
            println!(
                "-{five64} / -{three64} == {}",
                five64.wrapping_neg().wrapping_div(three64.wrapping_neg())
            );
            println!("{five64} % {three64} == {}", five64.wrapping_rem(three64));
            println!(
                "{five64} % -{three64} == {}",
                five64.wrapping_rem(three64.wrapping_neg())
            );
            println!(
                "-{five64} % {three64} == {}",
                five64.wrapping_neg().wrapping_rem(three64)
            );
            println!(
                "-{five64} % -{three64} == {}",
                five64.wrapping_neg().wrapping_rem(three64.wrapping_neg())
            );
            check_parse32("7FFFFFFF");
            check_parse32("-80000000");
            check_parse32("80000000");
            check_parse32("-80000001");
            println!("Not so big: {}", not_so_big as i32);
            println!(
                "Actually big: {}",
                (|| {
                    Ok::<_, temper_core::ParseError>(Arc::new(
                        temper_core::int64_to_int32(big64)?.to_string(),
                    ))
                })()
                .unwrap_or_else(|_| Arc::new("no".to_string()))
            );
            check_parse64("7FFFFFFFFFFFFFFF");
            check_parse64("-8000000000000000");
            check_parse64("8000000000000000");
            check_parse64("-8000000000000001");
            println!("{} < {}", small64.min(big64), small64.max(big64));
            Ok(())
        })
        .clone()
}

pub(crate) fn check_parse32(string: impl ToArcString) {
    let string = string.to_arc_string();
    let converted = (|| {
        Ok::<_, temper_core::ParseError>(Arc::new(
            temper_core::string::to_int(&string, Some(16))?.to_string(),
        ))
    })()
    .unwrap_or_else(|_| Arc::new("failed".to_string()));
    println!("Parse {string} -> {converted}");
}

pub(crate) fn check_parse64(string: impl ToArcString) {
    let string = string.to_arc_string();
    let converted = (|| {
        Ok::<_, temper_core::ParseError>(Arc::new(
            temper_core::string::to_int64(&string, Some(16))?.to_string(),
        ))
    })()
    .unwrap_or_else(|_| Arc::new("failed".to_string()));
    println!("Parse {string} -> {converted}");
}
