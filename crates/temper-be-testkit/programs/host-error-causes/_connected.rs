pub(crate) fn host_fails(n: i32) -> Result<i32, crate::Oops> {
    panic!("host bug {n}")
}
