use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let n: i32 = 200000;
            let source: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
            {
                let mut i: i32 = 0;
                while i < n {
                    temper_core::listed::add(&source, i, None);
                    i = i.wrapping_add(1);
                }
            }
            let values: temper_core::List<i32> = temper_core::ListedTrait::to_list(&source);
            let b: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
            temper_core::listed::add_all(&b, temper_core::Listed::new(values.clone()), None);
            temper_core::listed::add_all(&b, temper_core::Listed::new(values.clone()), Some(0));
            temper_core::listed::add(&b, -1, Some(n));
            temper_core::listed::add_all(&b, temper_core::Listed::new(values.clone()), Some(n));
            println!(
                "{} {} {} {} {} {} {}",
                temper_core::ListedTrait::len(&b),
                temper_core::ListedTrait::get(&b, 0),
                temper_core::ListedTrait::get(&b, n.wrapping_sub(1)),
                temper_core::ListedTrait::get(&b, n),
                temper_core::ListedTrait::get(&b, 2i32.wrapping_mul(n)),
                temper_core::ListedTrait::get(&b, 2i32.wrapping_mul(n).wrapping_add(1)),
                temper_core::ListedTrait::get(&b, 3i32.wrapping_mul(n))
            );
            let twice: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
            temper_core::listed::add_all(&twice, temper_core::Listed::new(values.clone()), None);
            temper_core::listed::add_all(&twice, temper_core::Listed::new(twice.clone()), Some(1));
            println!(
                "{} {} {} {} {}",
                temper_core::ListedTrait::len(&twice),
                temper_core::ListedTrait::get(&twice, 0),
                temper_core::ListedTrait::get(&twice, 1),
                temper_core::ListedTrait::get(&twice, n),
                temper_core::ListedTrait::get(&twice, n.wrapping_add(1))
            );
            let s: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
            temper_core::listed::add(&s, 7, None);
            temper_core::listed::add(&s, 8, None);
            let removed: temper_core::List<i32> = temper_core::listed::splice(
                &s,
                Some(1),
                Some(0),
                Some(temper_core::Listed::new(values.clone())),
            );
            println!(
                "{} {} {} {} {} {}",
                temper_core::ListedTrait::len(&s),
                temper_core::ListedTrait::len(&removed),
                temper_core::ListedTrait::get(&s, 0),
                temper_core::ListedTrait::get(&s, 1),
                temper_core::ListedTrait::get(&s, n),
                temper_core::ListedTrait::get(&s, n.wrapping_add(1))
            );
            let c: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
            temper_core::listed::add(&c, 7, None);
            temper_core::listed::add(&c, 8, None);
            temper_core::listed::splice(
                &c,
                Some(-1),
                Some(0),
                Some(temper_core::Listed::new(values.clone())),
            );
            temper_core::listed::splice(
                &c,
                Some(10i32.wrapping_mul(n)),
                Some(0),
                Some(temper_core::Listed::new(values.clone())),
            );
            println!(
                "{} {} {} {} {} {} {}",
                temper_core::ListedTrait::len(&c),
                temper_core::ListedTrait::get(&c, 0),
                temper_core::ListedTrait::get(&c, n.wrapping_sub(1)),
                temper_core::ListedTrait::get(&c, n),
                temper_core::ListedTrait::get(&c, n.wrapping_add(1)),
                temper_core::ListedTrait::get(&c, n.wrapping_add(2)),
                temper_core::ListedTrait::get(&c, 2i32.wrapping_mul(n).wrapping_add(1))
            );
            let own: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
            temper_core::listed::add_all(&own, temper_core::Listed::new(values.clone()), None);
            let gone: temper_core::List<i32> = temper_core::listed::splice(
                &own,
                Some(0),
                Some(1),
                Some(temper_core::Listed::new(own.clone())),
            );
            println!(
                "{} {} {} {} {} {}",
                temper_core::ListedTrait::len(&own),
                temper_core::ListedTrait::len(&gone),
                temper_core::ListedTrait::get(&own, 0),
                temper_core::ListedTrait::get(&own, n.wrapping_sub(1)),
                temper_core::ListedTrait::get(&own, n),
                temper_core::ListedTrait::get(&own, 2i32.wrapping_mul(n).wrapping_sub(2))
            );
            let kept: temper_core::List<i32> =
                temper_core::listed::filter(&values, &(|x: i32| x != 5));
            println!(
                "{} {} {} {}",
                temper_core::ListedTrait::len(&kept),
                temper_core::ListedTrait::get(&kept, 4),
                temper_core::ListedTrait::get(&kept, 5),
                temper_core::ListedTrait::get(&kept, n.wrapping_sub(2))
            );
            let codes: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
            {
                let mut i2: i32 = 0;
                while i2 < n {
                    temper_core::listed::add(&codes, 97i32.wrapping_add(i2.wrapping_rem(3)), None);
                    i2 = i2.wrapping_add(1);
                }
            }
            let text = temper_core::string::from_code_points(&*temper_core::Listed::new(
                temper_core::ListedTrait::to_list(&codes),
            ))
            .unwrap_or_else(|_| Arc::new(String::new()));
            let four = temper_core::string::next(
                &text,
                temper_core::string::next(
                    &text,
                    temper_core::string::next(&text, temper_core::string::next(&text, 0usize)),
                ),
            );
            println!(
                "{} {}",
                temper_core::string::count_between(&text, 0usize, text.len()),
                temper_core::string::slice(&text, 0usize, four)
            );
            Ok(())
        })
        .clone()
}
