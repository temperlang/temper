//! The coroutines' state machines ([`temper_tmpl::coroutines`]): a
//! `Switch` on the state, branches on literal conditions folded, so
//! `while (true)` and `while (false)` leave no `if (true)` and no states
//! that nothing reaches, jumps through states that only jump going
//! straight on, and states numbered in order from 0.

use temper_tmpl::coroutines::{CoroutineStrategy, lower};
use temper_tmpl::{Profile, text, validate};

const PROGRAM: &str = "tmpl 1 model 1 library folded 0.0.0

uses `temper-core`#2 0.1.0 {
  interface SafeGenerator#3<YIELD#4> extends Generator#5<YIELD#4> {
    connected method nextSafe#13(): GeneratorResult#14<YIELD#4>;
  }
  interface Generator#5<YIELD#6> {}
  class Empty#7 {}
  interface Console#10 {
    connected method log#11(message: String): Void;
  }
  interface GeneratorResult#14<YIELD#15> {}
  class ValueResult#16<YIELD#17> extends GeneratorResult#14<YIELD#17> {
    connected constructor constructor#18(value: YIELD#17): ValueResult#16<YIELD#17>;
  }
  connected fn doneResult#19<YIELD#20>(): DoneResult#21<YIELD#20>;
  class DoneResult#21<YIELD#22> extends GeneratorResult#14<YIELD#22> {}
  connected fn empty#23(): Empty#7;
  connected fn safeAdaptGeneratorFn#24<YIELD#25>(step: fn (SafeGenerator#3<YIELD#25>): GeneratorResult#14<YIELD#25>): SafeGenerator#3<YIELD#25>;
  connected fn awakeUpon#26<T#27>(promise: Promise#28<T#27>, generator: SafeGenerator#3<Empty#7>): Void;
  class Promise#28<R#29> {}
  interface Error#8 {}
  class BrokenPromise#9 extends Error#8 {}
  connected fn getPromiseResultSync#30<T#31>(promise: Promise#28<T#31>): T#31 throws BrokenPromise#9;
  class Int32#35 {
    connected method toString#36(radix?: Int32): String;
  }
  connected fn getConsole#37(name?: String): Console#10;
}

module folded#0 {
  let ~console#12: Console#10;

  init {
    init ~console#12 = getConsole#37(omitted);
    let g#1: fn (): SafeGenerator#3<Empty#7> = (fn generator ~fn#32(): SafeGenerator#3<Empty#7> {
      var i#33: Int32 = 0;
      ~loop#34: while (true) {
        yield;
        if (%Int32.gt(i#33, 2)) {
          break ~loop#34;
        }
        i#33 = %Int32.add(i#33, 1);
      }
      ~loop#38: while (false) {
        yield;
      }
      ~console#12.log#11(\"done\");
    }): fn (): SafeGenerator#3<Empty#7>;
  }
}
";

#[test]
fn literal_conditions_fold_and_dead_states_go() {
    let program = text::read(PROGRAM).unwrap();
    assert_eq!(validate(&program, &Profile::full()), []);
    let lowered = lower(&program, CoroutineStrategy::TranslateToRegularFunction)
        .unwrap()
        .expect("a generator to lower");
    assert_eq!(validate(&lowered, &Profile::full()), []);
    let written = text::write(&lowered);
    assert!(
        !written.contains("if (true)") && !written.contains("if (false)"),
        "{written}"
    );
    // The head of `while (true)`, which only jumped on, is gone; so is
    // `while (false)`'s body, which nothing reaches; the rest count from
    // 0, and the break goes to the code after the loops.
    let step = &written[written.find("switch (").unwrap()..];
    let step = &step[..step.find("default").unwrap()];
    assert_eq!(
        step,
        "switch (~caseIndexLocal#43) {
            case 0 {
              i#33 = 0;
              ~caseIndex#41 = 1;
            }
            case 1 {
              ~caseIndex#41 = 2;
              return (upcast(new constructor#18<Empty#7>(empty#23()))): GeneratorResult#14<Empty#7>;
            }
            case 2 {
              if (%Int32.gt(i#33, 2)) {
                {
                  ~caseIndex#41 = 3;
                  continue ~loop#42;
                }
              }
              i#33 = %Int32.add(i#33, 1);
              ~caseIndex#41 = 1;
            }
            case 3 {
              ~console#12.log#11(\"done\");
              return (upcast(doneResult#19<Empty#7>())): GeneratorResult#14<Empty#7>;
            }
            ",
        "{written}"
    );
}
