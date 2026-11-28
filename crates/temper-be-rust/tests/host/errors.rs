//! Host code using the generated `errors-interop` crate's declared
//! errors the standard way, for `functional.rs`'s `runs_and_lints`: it
//! matches them by their own types, a sealed interface's enum, and the
//! crate's `Error`, reads their fields, messages (`Display`), and causes
//! (`source()`), passes them on with `?` into the crate's `Error` and a
//! boxed error, and sees a panic stay a panic, never an `Err`. It prints
//! a line per check, which the test compares with what it expects.
//! Clippy lints it with the generated crates.

use errors_interop::{LookupFailed, NotFound, SignError};
use std::error::Error as _;

/// Any of the library's errors, into its crate's `Error` by `?`.
fn both(key: &str, text: &str) -> Result<i32, errors_interop::Error> {
    Ok(errors_interop::lookup(key)? + errors_interop::sign(text)?)
}

/// An error into a boxed error by `?`, as `anyhow` and the like take one.
fn boxed(key: &str) -> Result<i32, Box<dyn std::error::Error + Send + Sync>> {
    Ok(errors_interop::lookup(key)?)
}

/// An error's message and its causes', as `source()` gives them.
fn chain(error: &dyn std::error::Error) -> String {
    let mut out = error.to_string();
    let mut cause = error.source();
    while let Some(c) = cause {
        out.push_str(", caused by ");
        out.push_str(&c.to_string());
        cause = c.source();
    }
    out
}

fn main() {
    // Its top-level code logs.
    errors_interop::init(None).unwrap();

    match errors_interop::lookup("z") {
        Err(e) => println!("NotFound {}: {e}", e.key()),
        Ok(n) => println!("found {n}"),
    }

    // Each error type converts into the crate's `Error`, a sealed
    // interface's under its own variant.
    for (key, text) in [("y", "+"), ("a", "?")] {
        match both(key, text) {
            Err(errors_interop::Error::NotFound(e)) => println!("base NotFound {}", e.key()),
            Err(errors_interop::Error::SignError(SignError::BadSign(e))) => {
                println!("base BadSign {}", e.text());
            }
            Err(e) => println!("base other {e}"),
            Ok(n) => println!("both {n}"),
        }
    }
    println!("both {}", both("a", "-").unwrap());

    match boxed("w") {
        Err(e) => println!(
            "boxed {e}, a NotFound {}",
            e.downcast_ref::<NotFound>().is_some()
        ),
        Ok(n) => println!("boxed {n}"),
    }

    for text in ["x", ""] {
        match errors_interop::sign(text) {
            Err(SignError::BadSign(e)) => println!("sign BadSign {}: {e}", e.text()),
            Err(SignError::EmptyText(e)) => println!("sign EmptyText: {e}"),
            Ok(n) => println!("sign {n}"),
        }
    }

    let e: LookupFailed = errors_interop::setting("port").unwrap_err();
    let cause = e.source().unwrap();
    println!(
        "LookupFailed {}, a NotFound cause {}",
        chain(&e),
        cause.downcast_ref::<NotFound>().is_some()
    );
    let e = errors_interop::made_before("early").unwrap_err();
    println!("made before {}", chain(&e));
    let e = errors_interop::thrown_again("first").unwrap_err();
    println!("thrown again {}", chain(&e));

    // `Debug`, as `unwrap()` and `{:?}` show it.
    println!("debug {:?}", errors_interop::lookup("d").unwrap_err());

    // A panic isn't an error: it stays a panic.
    std::panic::set_hook(Box::new(|_| {}));
    let panicked = std::panic::catch_unwind(|| errors_interop::risky(2));
    println!("panic passes {}", panicked.is_err());
}
