//! A library settled a round at a time, as a REPL session grows: each
//! round settles only what was added since the last, against what's
//! settled already, and earlier rounds are never settled or typed again.
//!
//! That works because the settle loop's facts are monotonic: a resolved
//! name never changes, and neither does a type once known. A REPL's
//! inputs are files of one module, each with a scope of its own, a
//! *segment*, whose parent is the previous input's, so a lookup walks
//! back through earlier inputs, and a later declaration of a name
//! shadows an earlier one for later inputs only. Modules that inputs
//! import, from std or the work root, are ordinary modules, settled in
//! the round that loads them.

use super::{Fact, Settle, Settled, SettledModule};
use crate::library::{FileId, Library, ModuleId};
use crate::scope::{BUILTINS, ScopeId, ScopeKind, Scopes};
use crate::typing::Typing;
use temper_syntax::tree::{NodeId, NodeKind};

/// A library that grows by rounds ([module docs](self)).
pub struct Incremental {
    s: Settle,
    /// Every module settled so far, each input's segment among them, in
    /// the order they run.
    modules: Vec<SettledModule>,
    /// The REPL's inputs' module, once there's one, and the latest
    /// input's scope.
    last_segment: Option<ScopeId>,
    /// Files and nodes settled by earlier rounds.
    files_settled: usize,
    nodes_settled: u32,
    /// While a round settles: the modules before it, and the latest
    /// input's scope before it, to go back to if it's abandoned.
    round_start: Option<(usize, Option<ScopeId>)>,
    /// Makes the next round panic once settled, to test that a panic
    /// while settling is recovered from.
    #[cfg(test)]
    pub(crate) panic_next: bool,
}

/// What a round settled.
pub struct Round {
    /// The modules it loaded, each settled as a library's are, in the
    /// order they run.
    pub modules: Vec<SettledModule>,
    /// The input, its top levels sorted to run.
    pub input: SettledModule,
    /// The input's last statement in source order, which gives its
    /// result. If it's an expression, not a declaration, it runs last,
    /// as nothing can depend on it.
    pub last: Option<NodeId>,
}

impl Incremental {
    /// An empty session over `library`, which has no files yet, but may
    /// name the libraries its imports can reach.
    pub fn new(library: Library) -> Incremental {
        let s = Settle::new(library);
        Incremental {
            s,
            modules: Vec::new(),
            last_segment: None,
            files_settled: 0,
            nodes_settled: 0,
            round_start: None,
            #[cfg(test)]
            panic_next: false,
        }
    }

    /// The library, to add an input's file to, and the modules it
    /// imports, before [`Incremental::settle_input`].
    pub fn library_mut(&mut self) -> &mut Library {
        &mut self.s.lib
    }

    pub fn library(&self) -> &Library {
        &self.s.lib
    }

    pub fn scopes(&self) -> &Scopes {
        &self.s.scopes
    }

    pub fn typing(&self) -> &Typing {
        &self.s.typing
    }

    /// The latest input's scope, from which names are looked up as the
    /// next input would see them.
    pub fn last_segment(&self) -> Option<ScopeId> {
        self.last_segment
    }

    /// Whether later rounds' inputs may declare tests, which the input
    /// then gives as [`SettledModule::tests`]; else a test is an error.
    pub fn allow_tests(&mut self, allowed: bool) {
        self.s.repl_tests = allowed;
    }

    /// Settles what was added since the last round: `input`, a REPL
    /// input's file, in a scope chained to the previous input's, and the
    /// modules loaded for it, each in a scope of its own.
    pub fn settle_input(&mut self, input: FileId) -> Round {
        let s = &mut self.s;
        s.round_files = self.files_settled;
        s.round_nodes = self.nodes_settled;
        let new_nodes = s.lib.tree.len() as u64 - self.nodes_settled as u64;
        let added = s
            .lib
            .namespace_names(self.nodes_settled..s.lib.tree.len() as u32);
        s.namespace_names.extend(added);
        s.engine.grow_budget(1000 + 100 * new_nodes);
        let repl = s.lib.files[input.0 as usize].module;
        let root = s.lib.files[input.0 as usize].root;
        let first_new = s.modules.len();
        let tests_before = s.tests.len();
        self.round_start = Some((first_new, self.last_segment));
        let parent = self.last_segment.unwrap_or(BUILTINS);
        let segment = s.scopes.add(parent, ScopeKind::Module, root, false);
        s.repl_scopes.insert(segment);
        for m in first_new..s.lib.modules.len() {
            if m == repl.0 as usize {
                s.modules.push(segment);
                s.module_blocks.push(0);
            } else {
                s.queue_module(ModuleId(m as u32));
            }
        }
        // The module's scope is its latest input's, so that what asks
        // which module a scope is, as `import`, finds it.
        s.modules[repl.0 as usize] = segment;
        s.module_blocks[repl.0 as usize] = 1;
        s.queue_root(root, segment);
        self.last_segment = Some(segment);
        s.settle_round();
        #[cfg(test)]
        if std::mem::take(&mut self.panic_next) {
            panic!("as a test asked, while settling");
        }

        let order = s.run_order();
        let loaded: Vec<ModuleId> = (order.into_iter())
            .filter(|&m| m.0 as usize >= first_new && m != repl)
            .collect();
        let mut modules = Vec::new();
        for id in loaded {
            let scope = s.modules[id.0 as usize];
            modules.push(s.settled_module(id, scope));
        }
        let last = s.lib.tree.children(root).last().copied();
        let expression =
            last.filter(|&n| !matches!(s.lib.tree.kind(n), NodeKind::Let | NodeKind::Class));
        let mut top_levels =
            crate::order::sort_top_levels(&mut s.lib.tree, &s.scopes, segment, &[root]);
        if let Some(last) = expression {
            top_levels.retain(|&n| n != last);
            top_levels.push(last);
            s.lib.tree.set_children(root, &top_levels);
        }
        // Its tests, if it declared any; the module's earlier inputs'
        // tests, if any, are already reported.
        let declared = s.tests[tests_before..].iter().any(|&(m, _)| m == repl);
        let (tests, test_cases) = match declared {
            true => (
                s.test_run(repl, tests_before),
                s.test_cases(repl, tests_before),
            ),
            false => (None, Vec::new()),
        };
        let input = SettledModule {
            id: repl,
            scope: segment,
            top_levels,
            tests,
            test_cases,
        };
        self.files_settled = s.lib.files.len();
        self.nodes_settled = s.lib.tree.len() as u32;
        self.modules.extend(modules.iter().cloned());
        self.modules.push(input.clone());
        self.round_start = None;
        Round {
            modules,
            input,
            last,
        }
    }

    /// Ends a round that stopped partway, as when settling panicked: what
    /// it left waiting is dropped, and its scopes count as complete, so
    /// the next round goes on without them. Its input's scope is dropped
    /// from the chain, so the next input's follows the one before; the
    /// modules it loaded stay loaded, never run, and their scopes are
    /// returned, to give them globals that say so when read.
    pub fn abandon_round(&mut self, scopes_before: usize) -> Vec<ScopeId> {
        let s = &mut self.s;
        let mut loaded = Vec::new();
        if let Some((first_module, segment_before)) = self.round_start.take() {
            self.last_segment = segment_before;
            let repl = (s.modules.iter()).position(|&m| s.repl_scopes.contains(&m));
            if let (Some(repl), Some(before)) = (repl, segment_before) {
                s.modules[repl] = before;
            }
            loaded = (first_module..s.modules.len())
                .filter(|&m| Some(m) != repl)
                .map(|m| s.modules[m])
                .collect();
        }
        s.engine.abandon();
        s.object_literals.clear();
        for i in scopes_before..s.scopes.scopes.len() {
            let id = ScopeId(i as u32);
            s.scopes.complete(id);
            s.engine.publish(Fact::ScopeComplete(id));
        }
        self.files_settled = s.lib.files.len();
        self.nodes_settled = s.lib.tree.len() as u32;
        loaded
    }

    /// Lends what's settled so far as a [`Settled`], for the
    /// interpreter, giving it back after, even if `f` panics.
    pub fn view<R>(&mut self, f: impl FnOnce(&Settled) -> R) -> R {
        struct Lent<'a> {
            home: &'a mut Incremental,
            settled: Settled,
        }
        impl Drop for Lent<'_> {
            fn drop(&mut self) {
                let (home, settled) = (&mut *self.home, &mut self.settled);
                std::mem::swap(&mut home.s.lib, &mut settled.library);
                std::mem::swap(&mut home.s.scopes, &mut settled.scopes);
                std::mem::swap(&mut home.s.typing, &mut settled.typing);
                std::mem::swap(&mut home.modules, &mut settled.modules);
            }
        }
        let settled = Settled {
            library: std::mem::take(&mut self.s.lib),
            scopes: std::mem::take(&mut self.s.scopes),
            modules: std::mem::take(&mut self.modules),
            steps: self.s.engine.steps(),
            typing: std::mem::take(&mut self.s.typing),
        };
        let lent = Lent {
            home: self,
            settled,
        };
        f(&lent.settled)
    }
}
