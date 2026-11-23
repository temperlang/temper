// A specification: `NotFound` is the library's own failure struct, at its
// crate's root, as the Rust backend will name it; a host error is a panic.
pub(crate) fn host_lookup(key: std::sync::Arc<String>) -> Result<i32, crate::NotFound> {
    match key.as_str() {
        "bug" => panic!("host bug"),
        "a" => Ok(1),
        _ => Err(crate::NotFound::new(key)),
    }
}
