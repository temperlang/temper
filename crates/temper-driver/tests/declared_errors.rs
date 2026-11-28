//! Declared errors (RFC 164) reach every built-in backend: each
//! translates the testkit's `declared-errors` and `host-declared-errors` programs (by
//! their tmpl goldens), but for host code and imported libraries' names
//! they're given elsewhere, as each runs them in its own functional
//! tests; and a program with only core's `Error` isn't one that uses
//! them.

use std::fs;
use std::path::Path;
use temper_driver::Registry;
use temper_tmpl::{Profile, SourceLinks, text, validate};

/// The testkit's goldens of `declared-errors` and `host-declared-errors`.
fn spec_goldens() -> Vec<(String, String)> {
    let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("../temper-be-testkit/tests/goldens");
    let mut found: Vec<(String, String)> = fs::read_dir(&dir)
        .unwrap()
        .map(|e| e.unwrap().path())
        .filter(|p| {
            let name = p.file_name().unwrap().to_string_lossy();
            name == "declared-errors.tmpl" || name.starts_with("host-declared-errors-")
        })
        .map(|p| {
            let name = p.file_name().unwrap().to_string_lossy().into_owned();
            (name, fs::read_to_string(&p).unwrap())
        })
        .collect();
    found.sort();
    found
}

#[test]
fn every_backend_translates_declared_errors() {
    let goldens = spec_goldens();
    assert_eq!(
        goldens.len(),
        9,
        "declared-errors, and host-declared-errors' eight runs"
    );
    for (name, golden) in goldens {
        let p = text::read(&golden).unwrap_or_else(|e| panic!("{name}: {e}"));
        assert_eq!(validate(&p, &Profile::full()), [], "{name}");
        for backend in Registry::built_in().all() {
            // Translated, or not for want of host code, or of the names
            // of a library it imports (`count-errors`'), which the
            // testkit's runs give it.
            if let Err(why) = (backend.translate)(&p, SourceLinks::default()) {
                let given = why.contains("host code") || why.contains("another library's");
                assert!(given, "{name}: {}: {why}", backend.name);
            }
        }
    }
}

/// A program with only core's `Error`, which it never throws, isn't one
/// that uses declared errors.
#[test]
fn core_error_alone_is_not_declared_errors() {
    let p = text::read(
        "tmpl 1 model 1 library plain 0.1.0

uses `temper-core`#2 0.1.0 {
  interface Error#3 {
    connected get message#4(): String;
    connected get cause#5(): Error#3?;
  }
}

module plain#0 {
  fn f#1(): Int32 {
    return 1;
  }

  init {}
}
",
    )
    .unwrap();
    assert_eq!(validate(&p, &Profile::full()), []);
    assert_eq!(temper_tmpl::declared_errors::first_use(&p), None);
}
