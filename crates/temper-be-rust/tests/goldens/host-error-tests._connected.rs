pub(crate) fn host_fails(n: i32) -> Result<i32, crate::Oops> {
    match n {
        1 => panic!("host bug {n}"),
        2 => Ok(Vec::<i32>::new()[n as usize]),
        _ => Ok("host bug".parse::<i32>().unwrap()),
    }
}

// A host test's failed assertion, which is a panic on Rust too.
pub(crate) fn host_fails_test() {
    panic!("host test failure")
}
