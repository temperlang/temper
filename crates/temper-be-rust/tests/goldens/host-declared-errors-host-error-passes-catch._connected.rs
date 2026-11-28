// A specification: `NotFound` is the library's own error struct, at its
// crate's root, as the Rust backend names it, which host code returns in an
// `Err`; a host error is a panic.
pub(crate) fn host_lookup(
    key: impl temper_core::ToArcString,
) -> Result<i32, crate::NotFound> {
    let key = key.to_arc_string();
    match key.as_str() {
        "bug" => panic!("host bug"),
        "a" => Ok(1),
        _ => Err(crate::NotFound::new(key)),
    }
}
