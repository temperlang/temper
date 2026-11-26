use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let d = temper_std::temporal::Date::new(2023, 6, 29)?;
            println!(
                "year    = {}\nmonth   = {}\nday     = {}\nweekday = {}",
                d.year(),
                d.month(),
                d.day(),
                d.day_of_week()
            );
            println!("ISO 8601: {}", d.to_string());
            let yonks_ago = temper_std::temporal::Date::new(12, 11, 10)?;
            println!("ISO 8601 yonks: {}", yonks_ago.to_string());
            println!("yonksAgo.year = {}", yonks_ago.year());
            {
                let list: temper_core::List<i32> =
                    Arc::new(vec![1700, 1800, 1900, 2000, 2100, 2400]);
                let length = temper_core::ListedTrait::len(&list);
                let mut i: i32 = 0;
                while i < length {
                    let y = temper_core::ListedTrait::get(&list, i);
                    i = i.wrapping_add(1);
                    println!(
                        "{y}: Jan 1 weekday {}, Mar 1 weekday {}",
                        temper_std::temporal::Date::new(y, 1, 1)?.day_of_week(),
                        temper_std::temporal::Date::new(y, 3, 1)?.day_of_week()
                    );
                }
            }
            println!("{} {}", check_date(2024, 2, 29), check_date(2023, 2, 29));
            let parsed: temper_core::List<Arc<String>> = temper_core::listed::map(
                &Arc::new(vec![
                    Arc::new("2024-02-29".to_string()),
                    Arc::new("2023-02-29".to_string()),
                    Arc::new("2023-2-1".to_string()),
                ]),
                &(|s: Arc<String>| {
                    (|| {
                        Ok::<_, temper_std::temporal::TemporalError>(
                            temper_std::temporal::Date::from_iso_string(s.clone())?.to_string(),
                        )
                    })()
                    .unwrap_or_else(|_| Arc::new("none".to_string()))
                }),
            );
            println!(
                "{}",
                temper_core::listed::join(
                    &parsed,
                    Arc::new(" ".to_string()),
                    &(|it: Arc<String>| it.clone()),
                )
            );
            temper_std::temporal::today().unwrap();
            let start = temper_std::temporal::Date::new(2010, 3, 21)?;
            let first_years = temper_std::temporal::Date::years_between(
                start.clone(),
                temper_std::temporal::Date::new(2012, 3, 20)?,
            );
            println!("First years between: {first_years}");
            let second_years = temper_std::temporal::Date::years_between(
                start.clone(),
                temper_std::temporal::Date::new(2012, 3, 21)?,
            );
            println!("Second years between: {second_years}");
            let rdc = temper_std::temporal::Date::from_iso_string("1596-03-31")
                .unwrap_or_else(|e| panic!("panic\ncaused by: {e}"));
            println!(
                "Happy birthday, René: day={}, month={}, year={}",
                rdc.day(),
                rdc.month(),
                rdc.year()
            );
            Ok(())
        })
        .clone()
}

pub(crate) fn check_date(year: i32, month: i32, day: i32) -> Arc<String> {
    (|| {
        Ok::<_, temper_std::temporal::TemporalError>(
            temper_std::temporal::Date::new(year, month, day)?.to_string(),
        )
    })()
    .unwrap_or_else(|e| e.message())
}
