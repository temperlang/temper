//! Analyses ([`temper_tmpl::analysis`]) and naming hints
//! ([`temper_tmpl::naming::hints`]) on a small program: which operands'
//! order shows, and what a temporary for one is called.

use temper_tmpl::analysis::{Effect, Effects, calls_nothing};
use temper_tmpl::naming::hints;
use temper_tmpl::{Node, NodeId, Program, text};

const PROGRAM: &str = "tmpl 1 model 1 library effects 0.1.0

uses `temper-core`#12 0.1.0 {
  class Int32#7 {
    connected method toString#8(radix?: Int32): String;
  }
  interface Console#9 {
    connected method log#10(message: String): Void;
  }
  connected fn getConsole#11(name?: String): Console#9;
}

module effects#0 {
  let ~console#6: Console#9;

  var total#1: Int32;

  let limit#2: Int32;

  fn bump#3(): Int32 {
    total#1 = %Int32.add(total#1, 1);
    return total#1;
  }

  fn twice#4(n#5: Int32): Int32 {
    return %Int32.mul(n#5, 2);
  }

  fn bumpTwice#13(): Int32 {
    return twice#4(bump#3());
  }

  init {
    init ~console#6 = getConsole#11(omitted);
    init total#1 = 0;
    init limit#2 = 3;
    ~console#6.log#10(%String.cat(bump#3().toString#8(omitted), total#1.toString#8(omitted)));
    ~console#6.log#10(twice#4(limit#2).toString#8(omitted));
    ~console#6.log#10(bumpTwice#13().toString#8(omitted));
  }
}
";

fn program() -> Program {
    text::read(PROGRAM).unwrap()
}

/// Every node of a kind, in the order a walk finds them.
fn find(p: &Program, keep: impl Fn(Node) -> bool) -> Vec<NodeId> {
    let mut found = Vec::new();
    let module = &p.modules[0];
    let mut roots = module.items.clone();
    roots.push(module.init);
    for root in roots {
        temper_tmpl::analysis::walk(p, root, &mut |id| {
            if keep(p.nodes.get(id)) {
                found.push(id);
            }
        });
    }
    found
}

#[test]
fn effects_of_calls_and_reads() {
    let p = program();
    let effects = Effects::of(&p, &p.modules[0]);
    let calls = find(&p, |n| matches!(n, Node::CallFunction { .. }));
    let name = |id: NodeId| match p.nodes.get(id) {
        Node::CallFunction { callee, .. } => p.name(callee).to_string(),
        _ => String::new(),
    };
    // The last call of each: `twice(limit)` in the init block.
    let of = |callee: &str| {
        let id = *calls.iter().rev().find(|&&c| name(c) == callee).unwrap();
        effects.node(&p, id)
    };
    let writes = Effect {
        writes: true,
        reads: true,
        fails: false,
    };
    assert_eq!(of("bump"), writes);
    assert_eq!(of("twice"), Effect::default());
    // Through a call, and an argument.
    assert_eq!(of("bumpTwice"), writes);
    let first_twice = *calls.iter().find(|&&c| name(c) == "twice").unwrap();
    assert_eq!(effects.node(&p, first_twice), writes);
    // `getConsole` is pure; `log` isn't.
    assert_eq!(of("getConsole"), Effect::default());
    let log = find(&p, |n| matches!(n, Node::CallMethod { .. }))
        .into_iter()
        .find(|&m| matches!(p.nodes.get(m), Node::CallMethod { method, .. } if p.name(method) == "log"))
        .unwrap();
    assert!(effects.node(&p, log).writes);
    // `total` is written by a function, `limit` isn't.
    let reads = find(&p, |n| matches!(n, Node::GetModuleLet { .. }));
    assert!(reads.iter().any(|&r| effects.node(&p, r).reads));
    assert!(effects.mutable.iter().all(|&d| p.name(d) == "total"));
    // The order of `bump()` and a read of `total` shows; of two reads, not.
    let read = Effect {
        reads: true,
        ..Effect::default()
    };
    assert!(writes.conflicts(read) && read.conflicts(writes));
    assert!(!read.conflicts(read));
    assert!(writes.conflicts(Effect {
        fails: true,
        ..Effect::default()
    }));
}

#[test]
fn calls_nothing_and_hints() {
    let p = program();
    let sets = find(&p, |n| matches!(n, Node::SetModuleLet { .. }));
    let Node::SetModuleLet { value, .. } = p.nodes.get(sets[0]) else {
        unreachable!();
    };
    assert!(calls_nothing(&p, value));
    let calls = find(&p, |n| matches!(n, Node::CallFunction { .. }));
    assert!(!calls_nothing(&p, calls[0]));
    let bump = *calls
        .iter()
        .find(|&&c| matches!(p.nodes.get(c), Node::CallFunction { callee, .. } if p.name(callee) == "bump"))
        .unwrap();
    assert_eq!(hints(&p, bump), ["bump", "int32", "value"]);
    let to_string = find(&p, |n| matches!(n, Node::CallMethod { .. }))
        .into_iter()
        .find(|&m| matches!(p.nodes.get(m), Node::CallMethod { method, .. } if p.name(method) == "toString"))
        .unwrap();
    assert_eq!(hints(&p, to_string), ["string", "value"]);
}
