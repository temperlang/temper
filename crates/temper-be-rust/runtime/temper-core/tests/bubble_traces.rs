//! Bubbles keep where they're made only once a program or a test asks
//! (`keep_bubble_traces`), even with `RUST_LIB_BACKTRACE` set, so a
//! library in a host's app pays no stack walk per bubble. Its own test
//! binary, since `std::backtrace` reads the environment once.

use temper_core::{keep_bubble_traces, Error};

#[test]
fn traces_only_once_kept() {
    std::env::set_var("RUST_LIB_BACKTRACE", "1");
    assert!(Error::new().backtrace().is_none());
    keep_bubble_traces();
    let error = Error::new();
    assert!(error.backtrace().is_some());
    assert!(format!("{error:?}").starts_with("Error(None)\nbubbled at:\n"));
}
