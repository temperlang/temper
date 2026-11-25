//! Declared failures (RFC 164) reach every built-in backend: each
//! translates every specification tmpl has (the testkit's `TMPL_SPECS`, by
//! their tmpl goldens), but for host code and imported libraries' names
//! they're given elsewhere, as each runs them in its own functional
//! tests; and a program with only
//! `Bubble` isn't one that uses them.

use std::fs;
use std::path::Path;
use temper_driver::Registry;
use temper_tmpl::{Profile, SourceLinks, text, validate};

/// The testkit's goldens of the specifications tmpl has.
fn spec_goldens() -> Vec<(String, String)> {
    let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("../temper-be-testkit/tests/goldens");
    let mut found: Vec<(String, String)> = fs::read_dir(&dir)
        .unwrap()
        .map(|e| e.unwrap().path())
        .filter(|p| {
            let name = p.file_name().unwrap().to_string_lossy();
            name == "failures.tmpl" || name.starts_with("host-failures-")
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
fn every_backend_translates_declared_failures() {
    let goldens = spec_goldens();
    assert_eq!(goldens.len(), 9, "failures, and host-failures' eight runs");
    for (name, golden) in goldens {
        let p = text::read(&golden).unwrap_or_else(|e| panic!("{name}: {e}"));
        assert_eq!(validate(&p, &Profile::full()), [], "{name}");
        for backend in Registry::built_in().all() {
            // Translated, or not for want of host code, or of the names
            // of a library it imports (`count-failures`'), which the
            // testkit's runs give it.
            if let Err(why) = (backend.translate)(&p, SourceLinks::default()) {
                let given = why.contains("host code") || why.contains("another library's");
                assert!(given, "{name}: {}: {why}", backend.name);
            }
        }
    }
}

/// A program with only `Bubble` isn't one that uses declared failures,
/// as core's `Error` alone isn't either.
#[test]
fn bubbles_are_not_declared_failures() {
    let p = text::read(
        "tmpl 1 model 1 library bubbles 0.1.0

uses `temper-core`#2 0.1.0 {
  interface Error#3 {
    connected get message#4(): String;
    connected get cause#5(): Error#3?;
  }
}

module bubbles#0 {
  fn f#1(): Int32 throws Bubble {
    return (orelse((bubble()^): Int32, (bubble()^): Int32)): Int32;
  }

  init {}
}
",
    )
    .unwrap();
    assert_eq!(validate(&p, &Profile::full()), []);
    assert_eq!(temper_tmpl::failures::first_use(&p), None);
}
