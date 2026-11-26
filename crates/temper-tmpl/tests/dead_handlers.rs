//! Handlers whose bodies can't fail as their bodies alone
//! ([`temper_tmpl::failures::drop_dead_handlers`]), as `to_tmpl` writes
//! them, for every backend: a `catch` and an `orelse`, nested, and in a
//! `Void` one's place, which drops its body's value; one whose body may
//! fail stays. The result validates.

use temper_tmpl::failures::drop_dead_handlers;
use temper_tmpl::{Profile, text, validate};

const PROGRAM: &str = "tmpl 1 model 1 library dead 0.1.0

uses `temper-core`#1 0.1.0 {
  interface Error#2 {}
}

module dead#0 {
  interface DeadError#3 extends Error#2 {}

  fn one#4(): Int32 {
    return 1;
  }

  fn caught#5(n#6: Int32): Int32 {
    return (catch(%Int32.add(n#6, 1), e#7: DeadError#3, 0)): Int32;
  }

  fn nested#8(): Int32 {
    return (orelse((catch(one#4(), e#9: DeadError#3, 2)): Int32, 3)): Int32;
  }

  fn dropped#10(): Void {
    (catch(one#4(), e#11: DeadError#3, void)): Void;
  }

  fn live#12(): Int32 {
    return (orelse((bubble()^): Int32, 4)): Int32;
  }

  init {}
}
";

const LOWERED: &str = "tmpl 1 model 1 library dead 0.1.0

uses `temper-core`#1 0.1.0 {
  interface Error#2 {}
}

module dead#0 {
  interface DeadError#3 extends Error#2 {}

  fn one#4(): Int32 {
    return 1;
  }

  fn caught#5(n#6: Int32): Int32 {
    return %Int32.add(n#6, 1);
  }

  fn nested#8(): Int32 {
    return one#4();
  }

  fn dropped#10(): Void {
    one#4();
  }

  fn live#12(): Int32 {
    return (orelse((bubble()^): Int32, 4)): Int32;
  }

  init {}
}
";

#[test]
fn dead_handlers_are_their_bodies() {
    let program = text::read(PROGRAM).unwrap();
    assert_eq!(validate(&program, &Profile::full()), []);
    let lowered = drop_dead_handlers(&program).expect("dead handlers to drop");
    assert_eq!(validate(&lowered, &Profile::full()), []);
    assert_eq!(text::write(&lowered), LOWERED);
    // None left, nothing new.
    assert!(drop_dead_handlers(&lowered).is_none());
}
