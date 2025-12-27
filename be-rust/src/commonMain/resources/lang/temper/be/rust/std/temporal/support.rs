use crate::temporal::Date;

#[cfg(feature = "temporal")]
pub fn today() -> temper_core::Result<Date> {
    // Uses time crate here, don't commit to time vs chrono publicly yet.
    let d = time::OffsetDateTime::now_utc().date();
    Date::new(d.year(), d.month() as i32, d.day() as i32)
}
