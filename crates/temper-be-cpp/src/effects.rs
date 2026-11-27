//! What evaluating an expression may do that C++'s order of evaluation
//! would show, as `temper_tmpl::analysis::Effects` finds it for functions,
//! extended to classes: methods, getters, setters, and constructors have
//! effects as functions do, a virtual call has those of every member that
//! may answer it (each of its name and kind), writing a field writes, and
//! reading a `var` field reads what calls may write.

use std::collections::{HashMap, HashSet};
use temper_tmpl::analysis::{Effect, PURE_CORE, module_writes, walk};
use temper_tmpl::naming::NameMap;
use temper_tmpl::qname::is_external;
use temper_tmpl::schema::Family;
use temper_tmpl::{DeclId, DeclKind, Facts, Module, Node, NodeId, Program, QNames};

/// Core's connected declarations that, in C++, neither write nor read
/// anything a call could change, beyond [`PURE_CORE`]: strings and string
/// indices are values, and `Float64`'s functions are math, as Kotlin's
/// marks them (`.pure()` and `.reads()` of values). Sorted.
const PURE_HERE: &[&str] = &[
    "core.empty()",
    "core.type Float64.abs()",
    "core.type Float64.acos()",
    "core.type Float64.asin()",
    "core.type Float64.atan()",
    "core.type Float64.atan2()",
    "core.type Float64.ceil()",
    "core.type Float64.cos()",
    "core.type Float64.cosh()",
    "core.type Float64.e",
    "core.type Float64.exp()",
    "core.type Float64.expm1()",
    "core.type Float64.floor()",
    "core.type Float64.log()",
    "core.type Float64.log10()",
    "core.type Float64.log1p()",
    "core.type Float64.max()",
    "core.type Float64.min()",
    "core.type Float64.near()",
    "core.type Float64.pi",
    "core.type Float64.round()",
    "core.type Float64.sign()",
    "core.type Float64.sin()",
    "core.type Float64.sinh()",
    "core.type Float64.sqrt()",
    "core.type Float64.tan()",
    "core.type Float64.tanh()",
    "core.type String.begin",
    "core.type String.countBetween()",
    "core.type String.get end()",
    "core.type String.get()",
    "core.type String.hasAtLeast()",
    "core.type String.hasIndex()",
    "core.type String.next()",
    "core.type String.prev()",
    "core.type String.slice()",
    "core.type String.step()",
    "core.type String.toString()",
    "core.type StringIndex.none",
    "core.type StringIndexOption.compareTo()",
    "core.type StringIndexOption.eq()",
];

/// Core's connected declarations that, in C++, read what calls could
/// change but write nothing: lists', maps', and deques' reads, so two of
/// them need no order kept between them, but one and a write do. Sorted.
const READS_HERE: &[&str] = &[
    "core.type DenseBitVector.get()",
    "core.type Deque.get isEmpty()",
    "core.type List.get length()",
    "core.type List.get()",
    "core.type ListBuilder.get length()",
    "core.type ListBuilder.get()",
    "core.type Listed.get isEmpty()",
    "core.type Listed.get length()",
    "core.type Listed.get()",
    "core.type Listed.getOr()",
    "core.type Mapped.get length()",
    "core.type Mapped.get()",
    "core.type Mapped.getOr()",
    "core.type Mapped.has()",
];

/// Every function-like item of a module, in order: its functions, and
/// its classes' and interfaces' methods (getters, setters, and
/// constructors too) and static functions, with bodies.
pub fn callables(p: &Program, module: &Module) -> Vec<(DeclId, NodeId)> {
    let mut out = Vec::new();
    for item in module.all_items() {
        match p.nodes.get(item) {
            Node::Function { decl, .. } => out.push((decl, item)),
            Node::Class { members, .. } | Node::Interface { members, .. } => {
                for &m in p.nodes.list(members) {
                    match p.nodes.get(m) {
                        Node::Method { decl, .. } | Node::Function { decl, .. } => {
                            out.push((decl, m));
                        }
                        _ => {}
                    }
                }
            }
            _ => {}
        }
    }
    out
}

/// The effects of a program's functions and members.
#[derive(Clone, Debug, Default)]
pub struct Effects {
    /// Module `var`s that a function or member writes.
    pub mutable: HashSet<DeclId>,
    functions: HashMap<DeclId, Effect>,
    pure: HashSet<DeclId>,
    /// Connected declarations that only read ([`READS_HERE`]).
    readers: HashSet<DeclId>,
    /// Members with bodies, by name and kind, for virtual calls.
    by_name: HashMap<(String, DeclKind), Vec<DeclId>>,
    /// Locals that function literals capture and that are assigned,
    /// which a call of a function value may write.
    cells: HashSet<DeclId>,
}

impl Effects {
    /// The effects of every module's functions and members.
    pub fn of(p: &Program, names: &NameMap) -> Effects {
        let qnames = QNames::derive(p);
        let listed = |list: &[&str]| -> HashSet<DeclId> {
            (p.decls.ids())
                .filter(|&d| p.decls.contains(d) && is_external(p, d))
                .filter(|&d| {
                    qnames.get(d).is_some_and(|q| {
                        let q = q.to_string();
                        list.binary_search(&q.as_str()).is_ok()
                    })
                })
                .collect()
        };
        let mut pure = listed(PURE_CORE);
        pure.extend(listed(PURE_HERE));
        let mut effects = Effects {
            pure,
            readers: listed(READS_HERE),
            cells: temper_tmpl::analysis::assigned_captures(p),
            ..Effects::default()
        };
        let mut functions = Vec::new();
        for module in &p.modules {
            // A function literal in the init block may run later.
            for block in [module.init].into_iter().chain(module.test_init) {
                walk(p, block, &mut |n| {
                    if let Node::FunctionLiteral { .. } = p.nodes.get(n) {
                        effects.mutable.extend(module_writes(p, n));
                    }
                });
            }
            for (decl, item) in callables(p, module) {
                effects.mutable.extend(module_writes(p, item));
                let key = (names.get(decl).to_string(), p.decls.get(decl).kind);
                effects.by_name.entry(key).or_default().push(decl);
                functions.push((decl, item));
            }
        }
        // Each one's own effect, then through calls, until nothing
        // changes.
        loop {
            let mut changed = false;
            for &(decl, item) in &functions {
                let ctor = p.decls.get(decl).kind == DeclKind::Constructor;
                let effect = effects.node_in(p, item, names, ctor);
                if effects.functions.get(&decl) != Some(&effect) {
                    effects.functions.insert(decl, effect);
                    changed = true;
                }
            }
            if !changed {
                return effects;
            }
        }
    }

    /// What calling a declaration of the program's may do: a virtual
    /// call, what any member of its name and kind may.
    fn called(&self, p: &Program, d: DeclId, names: &NameMap) -> Effect {
        if is_external(p, d) {
            let reads = self.readers.contains(&d);
            return Effect {
                writes: !reads && !self.pure.contains(&d),
                reads,
                ..Effect::default()
            };
        }
        let mut effect = self.functions.get(&d).copied().unwrap_or_default();
        if p.is_virtual(d) {
            let key = (names.get(d).to_string(), p.decls.get(d).kind);
            for other in self.by_name.get(&key).into_iter().flatten() {
                effect = effect.and(self.functions.get(other).copied().unwrap_or_default());
            }
        }
        effect
    }

    /// What evaluating `id` may do.
    pub fn node_with(&self, p: &Program, id: NodeId, names: &NameMap) -> Effect {
        self.node_in(p, id, names, false)
    }

    /// What evaluating `id` may do; in a constructor (`ctor`), where
    /// setting and reading the new object's fields shows nowhere else.
    fn node_in(&self, p: &Program, id: NodeId, names: &NameMap, ctor: bool) -> Effect {
        let mut effect = Effect::default();
        let this = |object: NodeId| ctor && matches!(p.nodes.get(object), Node::This {});
        let bubbles = |b: bool| Effect {
            bubbles: b,
            ..Effect::default()
        };
        // A function literal's body runs where it's called, not here.
        let mut stack = vec![id];
        while let Some(n) = stack.pop() {
            if !matches!(p.nodes.get(n), Node::FunctionLiteral { .. }) {
                stack.extend(p.nodes.kids(n).iter().rev());
            }
            let here = match p.nodes.get(n) {
                // A function value may do anything, its captures' writes
                // among them.
                Node::CallValue { may_bubble, .. } => Effect {
                    reads: true,
                    writes: true,
                    ..Effect::default()
                }
                .and(bubbles(may_bubble)),
                Node::Local { local } if self.cells.contains(&local) => Effect {
                    reads: true,
                    ..Effect::default()
                },
                Node::Assign { local, .. } if self.cells.contains(&local) => Effect {
                    writes: true,
                    ..Effect::default()
                },
                Node::SetField { object, .. } if this(object) => Effect::default(),
                Node::SetModuleLet { .. } | Node::SetField { .. } | Node::InitStatic { .. } => {
                    Effect {
                        writes: true,
                        ..Effect::default()
                    }
                }
                Node::GetModuleLet { target } => Effect {
                    reads: self.mutable.contains(&target),
                    ..Effect::default()
                },
                Node::GetField { field, object } => Effect {
                    reads: p.decls.get(field).facts.contains(Facts::VAR) && !this(object),
                    ..Effect::default()
                },
                Node::CallFunction {
                    callee: d,
                    may_bubble,
                    ..
                }
                | Node::CallMethod {
                    method: d,
                    may_bubble,
                    ..
                }
                | Node::New {
                    ctor: d,
                    may_bubble,
                    ..
                } => self.called(p, d, names).and(bubbles(may_bubble)),
                // An integer division panics for a zero divisor, and a
                // cast where it fails, as `panic()` does.
                Node::Op { op, .. } => Effect {
                    writes: !op.pure(),
                    ..Effect::default()
                },
                Node::As { .. } => Effect {
                    writes: true,
                    ..Effect::default()
                },
                Node::Throw { .. } => bubbles(true),
                // Stopping the program shows before or after anything
                // else that shows, as code with an error does.
                Node::Panic {} | Node::Error { .. } => Effect {
                    writes: true,
                    ..Effect::default()
                },
                _ => Effect::default(),
            };
            effect = effect.and(here);
        }
        effect
    }
}

/// The expressions that hold some of their own operands in temporaries,
/// for C++'s order of evaluation, or may: two operands whose order shows,
/// a nullable equality's operand that isn't a local, or a field passed
/// to a function of the program's, which could change while it runs. A
/// superset, for `temper_tmpl::spill::spill_with`, which makes a choice's
/// part or a loop's condition holding one statements, where temporaries
/// can go. Choices run their parts in order, so aren't among them.
pub fn needing_temporaries(p: &Program, effects: &Effects, names: &NameMap) -> HashSet<NodeId> {
    let mut found = HashSet::new();
    for module in &p.modules {
        for root in module.roots() {
            walk(p, root, &mut |n| {
                if needs_temporaries(p, effects, names, n) {
                    found.insert(n);
                }
            });
        }
    }
    found
}

/// Whether `n` may hold some of its own operands in temporaries
/// ([`needing_temporaries`]).
fn needs_temporaries(p: &Program, effects: &Effects, names: &NameMap, n: NodeId) -> bool {
    let operands: Vec<NodeId> = (p.nodes.kids(n).iter().copied())
        .filter(|&k| p.nodes.kind(k).family() == Family::Expr)
        .collect();
    let field = |k: &NodeId| matches!(p.nodes.get(*k), Node::GetField { .. });
    let named = match p.nodes.get(n) {
        Node::And { .. }
        | Node::Or { .. }
        | Node::IfValue { .. }
        | Node::Coalesce { .. }
        | Node::OrElse { .. }
        | Node::Catch { .. }
        | Node::BlockValue { .. }
        | Node::FunctionLiteral { .. } => return false,
        Node::EqNullable { left, right, .. } => {
            return [left, right]
                .iter()
                .any(|&k| !matches!(p.nodes.get(k), Node::Local { .. }));
        }
        Node::CallFunction { callee: d, .. } | Node::New { ctor: d, .. } if !is_external(p, d) => {
            operands.iter().any(field)
        }
        // Past the receiver.
        Node::CallMethod { method, .. } if !is_external(p, method) => {
            operands.iter().skip(1).any(field)
        }
        _ => false,
    };
    if named {
        return true;
    }
    let each: Vec<Effect> = (operands.iter())
        .map(|&k| effects.node_with(p, k, names))
        .collect();
    (0..each.len()).any(|i| each[i + 1..].iter().any(|&j| each[i].conflicts(j)))
}
