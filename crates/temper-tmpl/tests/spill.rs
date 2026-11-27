//! Spilling operands before what's hoisted ([`temper_tmpl::spill`]): an
//! operand that runs before an `orelse` becomes a block's value of its
//! own, a simple one stays, a `while` loop whose condition hoists checks
//! it in its body, and the result validates.

use temper_tmpl::spill::spill;
use temper_tmpl::{Profile, text, validate};

const PROGRAM: &str = "tmpl 1 model 1 library spilled 0.1.0

uses `temper-core`#10 0.1.0 {
  interface Error#11 {}
}

module spilled#0 {
  interface SpillError#12 extends Error#11 {}

  fn next#1(): Int32 {
    return 1;
  }

  fn risky#2(n#3: Int32): Int32 throws SpillError#12 {
    return n#3;
  }

  fn sum#4(n#5: Int32): Int32 {
    return %Int32.add(%Int32.add(n#5, next#1()), (orelse(risky#2(n#5)^, 0)): Int32);
  }

  fn count#6(n#7: Int32): Void {
    var i#8: Int32 = 0;
    loop#9: while (%Int32.gt((orelse(risky#2(i#8)^, n#7)): Int32, 0)) {
      i#8 = %Int32.add(i#8, 1);
    }
  }

  init {}
}
";

#[test]
fn spills_what_runs_first() {
    let program = text::read(PROGRAM).unwrap();
    assert!(validate(&program, &Profile::full()).is_empty());
    let spilled = spill(&program).expect("something to spill");
    let problems = validate(&spilled, &Profile::full());
    assert!(problems.is_empty(), "{problems:#?}");
    let written = text::write(&spilled);
    // `n + next()` runs first, held in a local; `n` alone needs nothing.
    assert!(
        written.contains("value { let ~int32|value#")
            && written.contains("= %Int32.add(n#5, next#1());"),
        "{written}"
    );
    assert_eq!(written.matches("value { let ~").count(), 1, "{written}");
    // The loop checks its condition in its body.
    assert!(written.contains("while (true)"), "{written}");
    assert!(written.contains("break loop#9;"), "{written}");
    // Nothing to spill, nothing new.
    let plain = PROGRAM
        .replace(
            "%Int32.add(%Int32.add(n#5, next#1()), (orelse(risky#2(n#5)^, 0)): Int32)",
            "next#1()",
        )
        .replace(
            "while (%Int32.gt((orelse(risky#2(i#8)^, n#7)): Int32, 0))",
            "while (%Int32.gt(i#8, n#7))",
        );
    assert!(spill(&text::read(&plain).unwrap()).is_none());
}

/// A backend's own marks ([`temper_tmpl::spill::spill_with`]): C++ marks
/// what holds operands in temporaries for its order of evaluation, so a
/// choice's part or a loop's condition holding one becomes statements,
/// with nothing held first before it.
#[test]
fn spills_what_the_backend_marks() {
    let text = "tmpl 1 model 1 library marked 0.1.0

module marked#0 {
  fn tick#1(): Int32 {
    return 1;
  }

  fn both#2(b#3: Boolean): Boolean {
    return b#3 && %Int32.gt(%Int32.add(tick#1(), tick#1()), 0);
  }

  fn count#4(n#5: Int32): Void {
    var i#6: Int32 = 0;
    loop#7: while (%Int32.lt(i#6, %Int32.add(tick#1(), tick#1()))) {
      i#6 = %Int32.add(i#6, 1);
    }
  }

  init {}
}
";
    let program = text::read(text).unwrap();
    assert!(validate(&program, &Profile::full()).is_empty());
    // Unmarked, nothing changes.
    assert!(spill(&program).is_none());
    // Each sum of two calls marked.
    let mut marked = Vec::new();
    for &item in &program.modules[0].items {
        temper_tmpl::analysis::walk(&program, item, &mut |n| {
            let kids = program.nodes.kids(n);
            let call = |k: &temper_tmpl::NodeId| {
                matches!(
                    program.nodes.get(*k),
                    temper_tmpl::Node::CallFunction { .. }
                )
            };
            if kids.len() == 2 && kids.iter().all(call) {
                marked.push(n);
            }
        });
    }
    assert_eq!(marked.len(), 2);
    let spilled = temper_tmpl::spill::spill_with(&program, &|n| marked.contains(&n))
        .expect("something to spill");
    let problems = validate(&spilled, &Profile::full());
    assert!(problems.is_empty(), "{problems:#?}");
    let written = text::write(&spilled);
    // `&&` as statements, its right side only where its left is true.
    assert!(written.contains("if (~boolean"), "{written}");
    // The loop checks its condition in its body.
    assert!(written.contains("while (true)"), "{written}");
    assert!(written.contains("break loop#7;"), "{written}");
}
