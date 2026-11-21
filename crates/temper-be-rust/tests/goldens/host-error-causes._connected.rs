pub(crate) fn host_fails(n: i32) -> temper_core::Result<i32> {
    panic!("host bug {n}")
}
