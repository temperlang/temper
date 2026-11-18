//! Tests of reachability ([`temper_tmpl::reach`]) on hand-written tmpl:
//! each kind of root, what tracing follows, each module's split, and the
//! program partitioned by it: its parts, their text, their imports, and
//! the validator's rule that each part uses only what it may (M3). The
//! frontend's tests port Kotlin Temper's own reachability tests
//! (`export-some`, `init-assignment-reachability`) from Temper, and
//! partition every functional test.

use std::collections::BTreeMap;
use temper_tmpl::analysis::{Import, module_imports, test_imports, test_parts};
use temper_tmpl::reach::{Reach, Split, partition, reach};
use temper_tmpl::{Node, NodeId, Profile, Program, Rule, difference, text, validate};

/// Each unit's reach, by name.
fn reaches(p: &Program) -> BTreeMap<String, Reach> {
    let reached = reach(p);
    (p.decls.ids())
        .filter_map(|d| Some((p.name(d).to_string(), reached.get(d)?)))
        .collect()
}

/// The names with a reach, sorted.
fn with(found: &BTreeMap<String, Reach>, reach: Reach) -> Vec<&str> {
    (found.iter())
        .filter(|(_, r)| **r == reach)
        .map(|(n, _)| n.as_str())
        .collect()
}

fn read(text: &str) -> Program {
    text::read(text).unwrap_or_else(|e| panic!("{e}"))
}

/// Core's and std's declarations the programs below use.
const USES: &str = "uses `temper-core`#90 0.1.0 {
  interface Console#91 {
    connected method log#92(message: String): Void;
  }
  connected fn getConsole#93(name?: String): Console#91;
  class Int32#94 {
    connected method toString#95(radix?: Int32): String;
  }
  class List#99<T#100> {}
}

uses std#96 0.1.0 {
  module testing#97 {
    class Test#98 {}
  }
}
";

/// Roots: exports, `keep`s, and init statements that do something reach
/// the library; tests and `keepTest`s, what they alone reach. A connected
/// function that nothing calls isn't a root, as in Kotlin.
#[test]
fn roots() {
    let p = read(&format!(
        "tmpl 1 model 1 library roots 0.1.0

{USES}
module roots#0 {{
  export fn exported#1(): Int32 {{
    return helper#2();
  }}

  fn helper#2(): Int32 {{
    return 1;
  }}

  keep fn forHost#3(): Int32 {{
    return hostHelper#4();
  }}

  fn hostHelper#4(): Int32 {{
    return 2;
  }}

  keepTest fn forTestHost#5(): Int32 {{
    return testHostHelper#6();
  }}

  fn testHostHelper#6(): Int32 {{
    return 3;
  }}

  fn fromInit#7(): Int32 {{
    return 4;
  }}

  fn fromTest#8(): Int32 {{
    return 5;
  }}

  fn unused#9(): Int32 {{
    return 6;
  }}

  connected fn hosted#10(): Int32;

  keep keepTest fn both#11(): Int32 {{
    return 7;
  }}

  init {{
    fromInit#7();
  }}

  test \"t\" ~test#12(~test#13: Test#98): Void throws Bubble {{
    fromTest#8();
  }}
}}
"
    ));
    let found = reaches(&p);
    assert_eq!(
        with(&found, Reach::Library),
        [
            "both",
            "exported",
            "forHost",
            "fromInit",
            "helper",
            "hostHelper"
        ]
    );
    assert_eq!(
        with(&found, Reach::Test),
        ["forTestHost", "fromTest", "testHostHelper"]
    );
    assert_eq!(with(&found, Reach::Unreached), ["hosted", "unused"]);
}

/// Tracing follows calls through any number of steps, and what the library
/// reaches stays the library's though tests reach it too; references to
/// functions reach them; and another module's private function that an
/// export uses is the library's.
#[test]
fn transitive_references_and_modules() {
    let p = read(&format!(
        "tmpl 1 model 1 library chain 0.1.0

{USES}
module other#20 in \"other\" {{
  fn shared#21(): Int32 {{
    return 1;
  }}

  fn otherUnused#22(): Int32 {{
    return 2;
  }}

  init {{}}
}}

module chain#0 {{
  export fn exported#1(): Int32 {{
    return %Int32.add(a#2(), shared#21());
  }}

  fn a#2(): Int32 {{
    return b#3();
  }}

  fn b#3(): Int32 {{
    return 1;
  }}

  fn c#4(): Int32 {{
    return d#5();
  }}

  fn d#5(): Int32 {{
    let f#6: fn (): Int32 = (ref(e#7)): fn (): Int32;
    return f#6();
  }}

  fn e#7(): Int32 {{
    return 2;
  }}

  init {{}}

  test \"t\" ~test#8(~test#9: Test#98): Void throws Bubble {{
    c#4();
    b#3();
  }}
}}
"
    ));
    let found = reaches(&p);
    assert_eq!(
        with(&found, Reach::Library),
        ["a", "b", "exported", "shared"]
    );
    assert_eq!(with(&found, Reach::Test), ["c", "d", "e"]);
    assert_eq!(with(&found, Reach::Unreached), ["otherUnused"]);
}

/// A class is one unit, reached wherever it's named: by a signature, a
/// local's type, an `is` check, a cast, or a member used. Its
/// members reach what they use, its supertypes are reached with it, and a
/// type parameter's bound with what declares it; an interface doesn't
/// reach the classes that implement it.
#[test]
fn classes_and_types() {
    let p = read(&format!(
        "tmpl 1 model 1 library types 0.1.0

{USES}
module types#0 {{
  interface Shape#1 {{}}

  class Square#2 extends Shape#1 {{
    constructor constructor#3(): Square#2 {{}}
    method area#4(): Int32 {{
      return sideHelper#5();
    }}
  }}

  fn sideHelper#5(): Int32 {{
    return 1;
  }}

  class InSignature#6 {{
    constructor constructor#7(): InSignature#6 {{}}
  }}

  class InLocal#8 {{
    constructor constructor#9(): InLocal#8 {{}}
  }}

  class InIs#10 {{
    constructor constructor#11(): InIs#10 {{}}
  }}

  class Bound#12 {{
    constructor constructor#13(): Bound#12 {{}}
  }}

  interface Implemented#14 {{}}

  class Implementer#15 extends Implemented#14 {{
    constructor constructor#16(): Implementer#15 {{}}
  }}

  export fn exported#17(x#18: InSignature#6?, s#19: Shape#1): Int32 {{
    let y#20: InLocal#8? = (null): InLocal#8?;
    let z#21: Boolean = is(s#19, InIs#10);
    return ((as(s#19)^): Square#2).area#4();
  }}

  export fn generic#23<T#24 extends Bound#12>(t#25: T#24): Int32 {{
    return 1;
  }}

  export fn takesImplemented#26(i#27: Implemented#14): Int32 {{
    return 1;
  }}

  init {{}}
}}
"
    ));
    let found = reaches(&p);
    assert_eq!(
        with(&found, Reach::Library),
        [
            "Bound",
            "Implemented",
            "InIs",
            "InLocal",
            "InSignature",
            "Shape",
            "Square",
            "exported",
            "generic",
            "sideHelper",
            "takesImplemented",
        ]
    );
    assert_eq!(with(&found, Reach::Unreached), ["Implementer"]);
}

/// An init statement that sets a module `let` is a root only if its
/// value may do something: a literal, a list of them, an operator that
/// can't bubble, a function, or core's pure `getConsole()` isn't, but any
/// other call or a constructor is. A statement that sets nothing is one
/// (a second assignment to a `var`, as Kotlin's
/// `init-assignment-reachability` tests). What only tests read is set
/// only for them; and what nothing reads, not at all. Static fields set
/// in init go with their class, and the init block's own locals are units
/// too.
#[test]
fn init_statements() {
    let p = read(&format!(
        "tmpl 1 model 1 library init 0.1.0

{USES}
module init#0 {{
  let ~console#1: Console#91;

  let literal#2: Int32;

  let computed#3: Int32;

  let constructed#4: Holder#5;

  class Holder#5 {{
    constructor constructor#6(): Holder#5 {{}}
    static let shared#7: Int32;
  }}

  fn make#8(): Int32 {{
    return 1;
  }}

  let forTests#9: List#99<Int32>;

  var hi#10: Int32;

  let ha#11: Int32;

  let usedByStatic#12: Int32;

  class Unused#13 {{
    constructor constructor#14(): Unused#13 {{}}
    static let unusedStatic#15: Int32;
  }}

  init {{
    init ~console#1 = getConsole#93(omitted);
    init literal#2 = %Int32.add(1, 2);
    init computed#3 = make#8();
    init constructed#4 = new constructor#6();
    init usedByStatic#12 = 3;
    init shared#7 = usedByStatic#12;
    init unusedStatic#15 = 4;
    init forTests#9 = (list(1, 2)): List#99<Int32>;
    init hi#10 = 0;
    hi#10 = 1;
    init ha#11 = 2;
    let local#16: Int32 = literal#2;
    let unusedLocal#17: Int32 = 5;
    let effect#18: Int32 = make#8();
    ~console#1.log#92(local#16.toString#95(omitted));
  }}

  test \"t\" ~test#19(~test#20: Test#98): Void throws Bubble {{
    let n#21: List#99<Int32> = forTests#9;
  }}
}}
"
    ));
    let found = reaches(&p);
    assert_eq!(
        with(&found, Reach::Library),
        [
            "Holder",
            "computed",
            "console",
            "constructed",
            "effect",
            "hi",
            "literal",
            "local",
            "make",
            "usedByStatic",
        ]
    );
    assert_eq!(with(&found, Reach::Test), ["forTests"]);
    assert_eq!(
        with(&found, Reach::Unreached),
        ["Unused", "ha", "unusedLocal"]
    );
}

/// `keep` and `keepTest` on a class reach what its members use; on a
/// module `let`, what its initializer does, though it's simple.
#[test]
fn kept_classes_and_lets() {
    let p = read(&format!(
        "tmpl 1 model 1 library kept 0.1.0

{USES}
module kept#0 {{
  keep class ForHost#1 {{
    constructor constructor#2(): ForHost#1 {{}}
    method run#3(): Int32 {{
      return hostOnly#4();
    }}
  }}

  fn hostOnly#4(): Int32 {{
    return 1;
  }}

  keepTest class ForTests#5 {{
    constructor constructor#6(): ForTests#5 {{}}
    method run#7(): Int32 {{
      return testOnly#8();
    }}
  }}

  fn testOnly#8(): Int32 {{
    return 2;
  }}

  keep let hostValue#9: fn (): Int32;

  keepTest let testValue#10: Int32;

  fn fromHostValue#11(): Int32 {{
    return 3;
  }}

  let fromTestValue#12: Int32;

  init {{
    init fromTestValue#12 = 4;
    init hostValue#9 = (ref(fromHostValue#11)): fn (): Int32;
    init testValue#10 = fromTestValue#12;
  }}
}}
"
    ));
    let found = reaches(&p);
    assert_eq!(
        with(&found, Reach::Library),
        ["ForHost", "fromHostValue", "hostOnly", "hostValue"]
    );
    assert_eq!(
        with(&found, Reach::Test),
        ["ForTests", "fromTestValue", "testOnly", "testValue"]
    );
}

/// A module's split: its items and its init block's statements, each
/// where its unit's reach puts it, in source order.
#[test]
fn split() {
    let p = read(&format!(
        "tmpl 1 model 1 library split 0.1.0

{USES}
module split#0 {{
  export fn exported#1(): Int32 {{
    return libValue#4;
  }}

  fn forTests#2(): Int32 {{
    return testValue#5;
  }}

  fn unused#3(): Int32 {{
    return 1;
  }}

  let libValue#4: Int32;

  let testValue#5: Int32;

  let unusedValue#6: Int32;

  init {{
    init libValue#4 = 1;
    init testValue#5 = 2;
    init unusedValue#6 = 3;
    exported#1();
  }}

  test \"t\" ~test#7(~test#8: Test#98): Void throws Bubble {{
    forTests#2();
  }}
}}
"
    ));
    let module = &p.modules[0];
    let items = |range: std::ops::Range<usize>| module.items[range].to_vec();
    let Some(stmts) = (match p.nodes.get(module.init) {
        temper_tmpl::Node::Block { stmts } => Some(p.nodes.list(stmts).to_vec()),
        _ => None,
    }) else {
        panic!("no init block");
    };
    let at = |i: usize| -> NodeId { stmts[i] };
    let unreached = [vec![module.items[2], module.items[5]], vec![at(2)]].concat();
    assert_eq!(
        reach(&p).split(&p, module),
        Split {
            items: [items(0..1), items(3..4)].concat(),
            init: vec![at(0), at(3)],
            test_items: [items(1..2), items(4..5)].concat(),
            test_init: vec![at(1)],
            unreached,
        }
    );
}

/// Connects nothing: these programs have no std members to connect.
fn none(_: &str) -> bool {
    false
}

/// The names of what items declare.
fn item_names(p: &Program, items: &[NodeId]) -> Vec<String> {
    (items.iter())
        .map(|&item| match p.nodes.get(item) {
            Node::Function { decl, .. }
            | Node::BodilessFunction { decl, .. }
            | Node::ModuleLet { decl }
            | Node::Class { decl, .. }
            | Node::Interface { decl, .. } => p.name(decl).to_string(),
            _ => panic!("not an item"),
        })
        .collect()
}

/// A block's statement count.
fn stmt_count(p: &Program, block: NodeId) -> usize {
    match p.nodes.get(block) {
        Node::Block { stmts } => p.nodes.list(stmts).len(),
        _ => panic!("not a block"),
    }
}

/// A library with a part of each kind: what the library reaches stays,
/// what only the test does (a function, a class and its static, and a
/// module `let`, with their init statements) moves to the test parts,
/// and what nothing does (a function with its parameter and local, an
/// interface, and a module `let`, the last declaration) is dropped.
fn parts_program() -> Program {
    read(&format!(
        "tmpl 1 model 1 library parts 0.1.0

{USES}
module parts#0 {{
  export fn exported#1(): Int32 {{
    return libValue#2;
  }}

  let libValue#2: Int32;

  fn forTests#3(): Int32 {{
    let box#4: Box#5 = new constructor#6();
    return %Int32.add(testValue#8, box#4.size#7());
  }}

  class Box#5 {{
    constructor constructor#6(): Box#5 {{}}
    method size#7(): Int32 {{
      return shared#9;
    }}
    static let shared#9: Int32;
  }}

  let testValue#8: Int32;

  fn unused#10(p#11: Int32): Int32 {{
    let q#12: Int32 = p#11;
    return q#12;
  }}

  interface Unused#13 {{}}

  let unusedValue#16: Int32;

  init {{
    init libValue#2 = 1;
    init testValue#8 = 2;
    init shared#9 = 3;
    init unusedValue#16 = 4;
    exported#1();
  }}

  test \"t\" ~test#14(~test#15: Test#98): Void throws Bubble {{
    forTests#3();
  }}
}}
"
    ))
}

/// Partitioned, each module keeps the library's items and init
/// statements; the test items and init take what only tests reach, in
/// source order; and what nothing reaches is gone, its declarations and
/// theirs too. The result validates, and partitioning it again changes
/// nothing.
#[test]
fn partition_parts() {
    let p = parts_program();
    let parted = partition(&p, none);
    let module = &parted.modules[0];
    assert_eq!(item_names(&parted, &module.items), ["exported", "libValue"]);
    assert_eq!(
        item_names(&parted, &module.test_items),
        ["forTests", "Box", "testValue"]
    );
    assert_eq!(stmt_count(&parted, module.init), 2);
    let test_init = module.test_init.expect("a test init");
    assert_eq!(stmt_count(&parted, test_init), 2);
    assert_eq!(module.tests.len(), 1);
    let live = |name: &str| {
        (parted.decls.ids())
            .filter(|&d| parted.decls.contains(d))
            .any(|d| parted.name(d) == name)
    };
    for gone in ["unused", "p", "q", "Unused", "unusedValue"] {
        assert!(!live(gone), "{gone} is still declared");
    }
    for kept in ["exported", "forTests", "box", "size", "shared", "testValue"] {
        assert!(live(kept), "{kept} is gone");
    }
    assert_eq!(validate(&parted, &Profile::full()), []);
    assert_eq!(difference(&partition(&parted, none), &parted), None);
}

/// Text writes the test parts in a section of their own after the init
/// block, `for tests`, its items and then its init block, which reads
/// back to an equal program and writes the same again.
#[test]
fn partition_text() {
    let parted = partition(&parts_program(), none);
    let written = text::write(&parted);
    let module = &written[written.find("module parts#0").expect("the module")..];
    assert_eq!(
        module,
        "module parts#0 {
  export fn exported#1(): Int32 {
    return libValue#2;
  }

  let libValue#2: Int32;

  init {
    init libValue#2 = 1;
    exported#1();
  }

  for tests {
    fn forTests#3(): Int32 {
      let box#4: Box#5 = new constructor#6();
      return %Int32.add(testValue#8, box#4.size#7());
    }

    class Box#5 {
      constructor constructor#6(): Box#5 {}
      method size#7(): Int32 {
        return shared#9;
      }
      static let shared#9: Int32;
    }

    let testValue#8: Int32;

    init {
      init testValue#8 = 2;
      init shared#9 = 3;
    }
  }

  test \"t\" ~test#14(~test#15: Test#98): Void throws Bubble {
    forTests#3();
  }
}
"
    );
    let back = read(&written);
    assert_eq!(difference(&parted, &back), None);
    assert_eq!(text::write(&back), written);
    // Test items alone, or a test init alone, too.
    for program in [
        "module m#0 {\n  init {}\n\n  for tests {\n    fn f#1(): Int32 {\n      return 1;\n    }\n  }\n}\n",
        "module m#0 {\n  let v#1: Int32;\n\n  init {}\n\n  for tests {\n    init {\n      init v#1 = 1;\n    }\n  }\n}\n",
    ] {
        let text = format!("tmpl 1 model 1 library m 0.1.0\n\n{program}");
        assert_eq!(text::write(&read(&text)), text);
    }
}

/// What a module imports for its library comes from its library's
/// parts alone; what its tests import, from its test parts and tests.
#[test]
fn partition_imports() {
    let p = read(&format!(
        "tmpl 1 model 1 library imports 0.1.0

{USES}
module helpers#20 in \"helpers\" {{
  export fn forLibrary#21(): Int32 {{
    return 1;
  }}

  export fn forTests#22(): Int32 {{
    return 2;
  }}

  init {{}}
}}

module imports#0 {{
  export fn exported#1(): Int32 {{
    return forLibrary#21();
  }}

  fn testHelper#2(t#3: Test#98): Int32 {{
    return forTests#22();
  }}

  init {{}}

  test \"t\" ~test#4(~test#5: Test#98): Void throws Bubble {{
    testHelper#2(~test#5);
  }}
}}
"
    ));
    let shown = |p: &Program, imports: Vec<Import>| -> Vec<(String, Vec<String>)> {
        (imports.into_iter())
            .map(|i| {
                let decls = i.decls.iter().map(|&d| p.name(d).to_string()).collect();
                (p.name(i.from).to_string(), decls)
            })
            .collect()
    };
    let parted = partition(&p, none);
    let module = &parted.modules[1];
    assert_eq!(
        shown(&parted, module_imports(&parted, module)),
        [("helpers".to_string(), vec!["forLibrary".to_string()])]
    );
    assert_eq!(
        shown(&parted, test_imports(&parted, module)),
        [
            ("helpers".to_string(), vec!["forTests".to_string()]),
            ("testing".to_string(), vec!["Test".to_string()]),
        ]
    );
}

/// M3: the library's items and init block use nothing of the test
/// parts', which a build without tests leaves out: here a call of a test
/// item, and an init statement setting one.
#[test]
fn library_parts_use_no_test_parts() {
    let p = read(
        "tmpl 1 model 1 library bad 0.1.0

module bad#0 {
  export fn exported#1(): Int32 {
    return forTests#2();
  }

  init {
    init value#3 = 1;
  }

  for tests {
    fn forTests#2(): Int32 {
      return 1;
    }

    let value#3: Int32;
  }
}
",
    );
    let rules: Vec<Rule> = (validate(&p, &Profile::full()).into_iter())
        .map(|problem| problem.rule)
        .collect();
    assert_eq!(rules, [Rule::M3, Rule::M3]);
}

/// The body of a std class's member that a backend connects reaches
/// nothing, as the backend doesn't write it: what only it uses is
/// dropped, where a backend that doesn't connect it keeps it; and the
/// member loses its body, so the program stays valid, as a connected
/// member without one is.
#[test]
fn connected_members_reach_nothing() {
    let p = read(
        "tmpl 1 model 1 library std 0.1.0

module temporal#0 in \"temporal\" {
  export class Date#1 {
    constructor constructor#2(): Date#1 {}
    connected method label#3(): Int32 {
      return helper#4();
    }
  }

  fn helper#4(): Int32 {
    return 1;
  }

  init {}
}
",
    );
    let connects = |key: &str| key == "std/temporal.type Date.label()";
    let helper = |p: &Program| {
        let module = &p.modules[0];
        item_names(p, &module.items).contains(&"helper".to_string())
    };
    assert!(helper(&partition(&p, none)));
    let parted = partition(&p, connects);
    assert!(!helper(&parted));
    assert_eq!(validate(&parted, &Profile::full()), []);
    let written = text::write(&parted);
    assert!(
        written.contains("connected abstract method label#3(): Int32;"),
        "{written}"
    );
}

/// What a partitioned module's test parts define, and use of its
/// library's parts, for a backend that writes them in a file of their
/// own: a function and the module `let` it reads, and of the library's,
/// a `var` that the test-only function assigns and a local of the init
/// block that the test init reads. The test's call of its own function
/// uses nothing of the library's.
#[test]
fn test_parts_define_and_use() {
    let p = read(&format!(
        "tmpl 1 model 1 library uses 0.1.0

{USES}
module uses#0 {{
  export fn exported#1(): Int32 {{
    return count#2;
  }}

  var count#2: Int32;

  fn bump#3(): Void {{
    count#2 = %Int32.add(count#2, testValue#5);
  }}

  let testValue#5: Int32;

  init {{
    init count#2 = 0;
    let base#4: Int32 = 2;
    count#2 = base#4;
    init testValue#5 = base#4;
  }}

  test \"t\" ~test#6(~test#7: Test#98): Void throws Bubble {{
    bump#3();
  }}
}}
"
    ));
    let parted = partition(&p, none);
    let parts = test_parts(&parted, &parted.modules[0]);
    let names = |decls: &mut dyn Iterator<Item = &temper_tmpl::DeclId>| {
        let mut names: Vec<&str> = decls.map(|&d| parted.name(d)).collect();
        names.sort();
        names
    };
    assert_eq!(names(&mut parts.own.iter()), ["bump", "testValue"]);
    assert_eq!(names(&mut parts.used.iter()), ["base", "count"]);
    assert_eq!(names(&mut parts.assigned.iter()), ["count"]);
    assert!(!parts.assign_init_locals(&parted));
}
