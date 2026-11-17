//! `Never<T>` as `T` ([`temper_tmpl::nevers`]): for a profile without
//! a bottom type, everywhere, with a `panic()` after a call that ends its
//! block; for one with it ([`Features::NEVER`]), only where
//! [`temper_tmpl::nevers::may_keep`] allows: not in an interface's method
//! or the method implementing it, whose signatures must match, nor in a
//! function read as a value. Each result validates for its profile, and
//! the program before it only for a profile with `NEVER`.

use temper_tmpl::nevers::lower;
use temper_tmpl::{Features, Profile, text, validate};

/// From the frontend's translation of a Temper program, as `run-temper
/// --tmpl` writes it.
const PROGRAM: &str = r#"tmpl 1 model 1 library nevers 0.0.0

module nevers#0 {
  fn stop#7(): Never<Void> {
    (panic()): Void;
  }

  fn stopInt#8(): Never<Int32> {
    (panic()): Void;
  }

  interface Limited#1 {
    abstract method tooFar#3(): Never<Int32>;
  }

  class Stepper#2 extends Limited#1 {
    constructor constructor#6(): Stepper#2 {}
    method tooFar#4(): Never<Int32> {
      (panic()): Void;
    }
    method halt#5(): Never<Int32> {
      (panic()): Void;
    }
  }

  fn pick#9(b#13: Boolean): Int32 {
    let x#14: Int32;
    if (b#13) {
      x#14 = 1;
    } else {
      stop#7();
    }
    return x#14;
  }

  fn tail#10(b#15: Boolean, s#16: Stepper#2): Int32 {
    if (b#15) {
      return s#16.halt#5();
    } else {
      return stopInt#8();
    }
  }

  fn viaInterface#11(l#17: Limited#1): Int32 {
    return l#17.tooFar#3();
  }

  fn asValue#12(): fn (): Never<Int32> {
    return (ref(stopInt#8)): fn (): Never<Int32>;
  }

  init {}
}
"#;

const LOWERED: &str = r#"tmpl 1 model 1 library nevers 0.0.0

module nevers#0 {
  fn stop#7(): Void {
    (panic()): Void;
  }

  fn stopInt#8(): Int32 {
    (panic()): Void;
  }

  interface Limited#1 {
    abstract method tooFar#3(): Int32;
  }

  class Stepper#2 extends Limited#1 {
    constructor constructor#6(): Stepper#2 {}
    method tooFar#4(): Int32 {
      (panic()): Void;
    }
    method halt#5(): Int32 {
      (panic()): Void;
    }
  }

  fn pick#9(b#13: Boolean): Int32 {
    let x#14: Int32;
    if (b#13) {
      x#14 = 1;
    } else {
      stop#7();
      (panic()): Void;
    }
    return x#14;
  }

  fn tail#10(b#15: Boolean, s#16: Stepper#2): Int32 {
    if (b#15) {
      return s#16.halt#5();
    } else {
      return stopInt#8();
    }
  }

  fn viaInterface#11(l#17: Limited#1): Int32 {
    return l#17.tooFar#3();
  }

  fn asValue#12(): fn (): Int32 {
    return (ref(stopInt#8)): fn (): Int32;
  }

  init {}
}
"#;

const KEPT: &str = r#"tmpl 1 model 1 library nevers 0.0.0

module nevers#0 {
  fn stop#7(): Never<Void> {
    (panic()): Void;
  }

  fn stopInt#8(): Int32 {
    (panic()): Void;
  }

  interface Limited#1 {
    abstract method tooFar#3(): Int32;
  }

  class Stepper#2 extends Limited#1 {
    constructor constructor#6(): Stepper#2 {}
    method tooFar#4(): Int32 {
      (panic()): Void;
    }
    method halt#5(): Never<Int32> {
      (panic()): Void;
    }
  }

  fn pick#9(b#13: Boolean): Int32 {
    let x#14: Int32;
    if (b#13) {
      x#14 = 1;
    } else {
      stop#7();
    }
    return x#14;
  }

  fn tail#10(b#15: Boolean, s#16: Stepper#2): Int32 {
    if (b#15) {
      return s#16.halt#5();
    } else {
      return stopInt#8();
    }
  }

  fn viaInterface#11(l#17: Limited#1): Int32 {
    return l#17.tooFar#3();
  }

  fn asValue#12(): fn (): Int32 {
    return (ref(stopInt#8)): fn (): Int32;
  }

  init {}
}
"#;

fn profile(native: Features) -> Profile {
    Profile {
        native,
        ..Profile::full()
    }
}

#[test]
fn nevers_lower_to_their_types() {
    let program = text::read(PROGRAM).unwrap();
    assert_eq!(text::write(&program), PROGRAM);
    assert_eq!(validate(&program, &Profile::full()), []);
    let without = profile(Features::STATEMENT_EXPRESSIONS);
    // T5: a profile without `NEVER` has no `Never<T>`.
    assert!(
        validate(&program, &without)
            .iter()
            .any(|p| p.to_string().contains("no bottom type")),
    );
    let lowered = lower(&program, &without).expect("nevers to lower");
    assert_eq!(validate(&lowered, &without), []);
    assert_eq!(text::write(&lowered), LOWERED);
    // Nothing left to lower.
    assert!(lower(&lowered, &without).is_none());
}

#[test]
fn nevers_stay_where_signatures_needn_t_match() {
    let program = text::read(PROGRAM).unwrap();
    let with = profile(Features::STATEMENT_EXPRESSIONS.with(Features::NEVER));
    let kept = lower(&program, &with).expect("some nevers to lower");
    assert_eq!(validate(&kept, &with), []);
    assert_eq!(text::write(&kept), KEPT);
}

#[test]
fn never_takes_one_plain_type() {
    for (ty, why) in [
        ("Never<Int32?>", "neither nullable nor a Never"),
        ("Never<Never<Int32>>", "neither nullable nor a Never"),
        ("Never<Int32, Int32>", "one type argument"),
    ] {
        let source = format!(
            "tmpl 1 model 1 library n 0.0.0\n\nmodule n#0 {{\n  fn f#1(): {ty} {{\n    (panic()): Void;\n  }}\n\n  init {{}}\n}}\n"
        );
        let error = text::read(&source).unwrap_err().to_string();
        assert!(error.contains(why), "{ty}: {error}");
    }
}
