//! The settle loop: name resolution and macro expansion as work items in
//! the [`Engine`], until every node is a core construct ([`crate::core`])
//! or an error.
//!
//! Two kinds of work:
//!
//! - **Reading a block** (phase 0): its statements in order, each expanded
//!   outside-in only as far as needed to see what it declares. A statement
//!   whose callee names a macro is expanded right away, and again if the
//!   result is another macro call; a declaration is declared. Then the
//!   block's scope is complete. A macro name resolves against what's
//!   declared so far, and waits only on enclosing scopes.
//! - **Visiting an expression** (phase 1): resolving its names, expanding
//!   macro calls (callee first: nothing under a macro call is touched until
//!   it has expanded), and queuing its parts. Scopes come from the node
//!   kinds: blocks, functions and loops make them.
//!
//! The facts so far are a scope's completion and a class's, which waits
//! for its supertypes' to inherit their members, and, in a second round
//! once expansion has settled, a declaration's type ([`crate::typing`])
//! and an object literal's class, which waits for the type its place
//! wants: a macro that waits for types.
//! When the loop gets stuck the engine forces the earliest stuck item,
//! whose lookup then treats scopes as complete; a declaration that turns
//! up later where such a lookup passed is an error, so no resolved name
//! ever changes.
//!
//! Debug builds check that no step read a declaration from a node that a
//! later step rewrote.
//!
//! [`settle`] settles a library at once; [`Incremental`] settles one that
//! grows a round at a time, as a REPL session's does.

use crate::builtins::Builtin;
use crate::core;
use crate::engine::{Engine, ItemId, Next};
use crate::extensions::StaticExtension;
use crate::library::{Level, Library, Message, ModuleId};
use crate::scope::{BUILTINS, Lookup, ScopeId, ScopeKind, Scopes, SymbolId, SymbolKind};
use crate::typing::Typing;
use std::collections::{BTreeMap, HashMap, HashSet};
use temper_syntax::ast::{Builtin as SyntaxBuiltin, Value};
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind, Span};

mod incremental;
pub use incremental::{Incremental, Round};

/// What a work item can wait on.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Fact {
    /// All of a scope's declarations are known.
    ScopeComplete(ScopeId),
    /// A class's scope is complete, with the members it inherits, which
    /// its subclasses inherit in turn.
    ClassScope(NodeId),
    /// A declaration's type, inferred from its initializer, is known.
    Type(NodeId),
    /// An object literal's class is decided, once the type its place
    /// wants is known, and it's lowered to a constructor call.
    ObjectLiteral(NodeId),
}

#[derive(Clone, Copy, Debug)]
enum Work {
    Block {
        node: NodeId,
        scope: ScopeId,
        next: u32,
    },
    Expr {
        node: NodeId,
        scope: ScopeId,
    },
    /// Completing a class's scope with what it inherits.
    Inherit {
        class: NodeId,
        scope: ScopeId,
    },
    /// Inferring a declaration's type from its initializer.
    Type {
        decl: NodeId,
    },
    /// Deciding an object literal's class, with the scope it's in.
    ObjectLiteral {
        call: NodeId,
        scope: ScopeId,
    },
}

const READ_BLOCKS: u8 = 0;
const VISIT: u8 = 1;
const TYPES: u8 = 2;

/// A settled library: its tree, now of core constructs, and the scopes and
/// symbols its names resolved to.
#[derive(Debug)]
pub struct Settled {
    pub library: Library,
    pub scopes: Scopes,
    /// Its modules in the order they run, each after those it imports.
    pub modules: Vec<SettledModule>,
    /// Steps the loop took.
    pub steps: u64,
    /// Declarations' and expressions' types, as far as they're known.
    pub typing: Typing,
}

/// A module's scope, and its top-level statements in the order they run
/// ([`crate::order`]).
#[derive(Clone, Debug)]
pub struct SettledModule {
    pub id: ModuleId,
    pub scope: ScopeId,
    pub top_levels: Vec<NodeId>,
    /// If it has tests, an expression that runs them, giving a JUnit XML
    /// report, for [`crate::interp::run_tests`]; not run otherwise.
    pub tests: Option<NodeId>,
    /// Its tests' declarations, in source order: each a top-level `let
    /// t# = new Pair(name, fn (test: Test): Void throws Bubble { … })`
    /// ([`crate::testing`]).
    pub test_cases: Vec<NodeId>,
}

/// Settles a library's files.
pub fn settle(library: Library) -> Settled {
    let mut s = Settle::new(library);
    for m in 0..s.lib.modules.len() {
        s.queue_module(ModuleId(m as u32));
    }
    let typed = s.settle_round();
    let mut modules = s.settled_modules();
    // Tagged strings' contexts worked out at compile time, once the tree
    // is typed and ordered so it can run.
    if typed {
        s.optimize_autoescaping(&mut modules);
    }
    Settled {
        steps: s.engine.steps(),
        typing: s.typing,
        library: s.lib,
        scopes: s.scopes,
        modules,
    }
}

impl Settle {
    /// The state for settling a library, with nothing queued yet.
    fn new(library: Library) -> Settle {
        let mut library = library;
        let scopes = Scopes::new(&mut library.names, library.test_server_port);
        let budget = 1000 + 100 * library.tree.len() as u64;
        Settle {
            lib: library,
            scopes,
            engine: Engine::new(budget),
            work: Vec::new(),
            modules: Vec::new(),
            module_blocks: Vec::new(),
            exports: HashSet::new(),
            connected: HashSet::new(),
            connected_lists: HashMap::new(),
            auto_eq: HashMap::new(),
            fun_interfaces: HashSet::new(),
            sealed: HashSet::new(),
            json_decorated: HashSet::new(),
            json: crate::json::JsonState::default(),
            static_extension_marks: HashMap::new(),
            static_extensions: Vec::new(),
            extension_marks: HashMap::new(),
            extensions: Vec::new(),
            imports: Vec::new(),
            overloads: HashMap::new(),
            tests: Vec::new(),
            test_runners: Vec::new(),
            root_throws: HashSet::new(),
            assert_messages: Vec::new(),
            passed: Vec::new(),
            removed: HashSet::new(),
            object_literals: BTreeMap::new(),
            typing: Typing::default(),
            round_files: 0,
            round_nodes: 0,
            repl_scopes: HashSet::new(),
            repl_tests: false,
            step: 0,
            #[cfg(debug_assertions)]
            reads: Vec::new(),
            #[cfg(debug_assertions)]
            rewrites: Vec::new(),
        }
    }

    /// Makes a module's scope, a top level's, and queues reading its
    /// files' blocks.
    fn queue_module(&mut self, m: ModuleId) {
        let files = self.lib.modules[m.0 as usize].files.clone();
        let roots: Vec<NodeId> = files
            .iter()
            .map(|f| self.lib.files[f.0 as usize].root)
            .collect();
        let scope = self
            .scopes
            .add(BUILTINS, ScopeKind::Module, roots[0], false);
        self.modules.push(scope);
        self.module_blocks.push(roots.len() as u32);
        for root in roots {
            self.queue_root(root, scope);
        }
    }

    /// Queues reading a file's block, its module's `scope`.
    fn queue_root(&mut self, root: NodeId, scope: ScopeId) {
        let item = self.add(
            Work::Block {
                node: root,
                scope,
                next: 0,
            },
            READ_BLOCKS,
            root,
        );
        self.engine.will_produce(Fact::ScopeComplete(scope), item);
    }

    /// Runs the loop over what's queued, then, once expansion has
    /// settled, the typing round, over the files from
    /// [`Settle::round_files`] on. True if typing ran, as it does unless
    /// the step budget ran out.
    fn settle_round(&mut self) -> bool {
        let mut typed = false;
        if self.run() {
            self.drop_dead_branches();
            self.queue_typing();
            if self.run() {
                self.check_types();
                self.settle_assert_messages();
                typed = true;
            }
        }
        // Only if the step budget ran out.
        self.decide_object_literals_left();
        #[cfg(debug_assertions)]
        self.check_reads();
        typed
    }

    /// The roots of the files this round settles: all of them, but for
    /// a session that grows ([`Incremental`]), only those added since
    /// the last round.
    pub(crate) fn round_roots(&self) -> Vec<NodeId> {
        (self.lib.files[self.round_files..].iter())
            .map(|f| f.root)
            .collect()
    }

    /// The first node this round made; older ones were settled before.
    pub(crate) fn round_nodes(&self) -> u32 {
        self.round_nodes
    }

    /// The first file this round settles.
    pub(crate) fn round_files(&self) -> usize {
        self.round_files
    }
}

impl Settle {
    /// The modules in the order they run, with their top levels sorted.
    fn settled_modules(&mut self) -> Vec<SettledModule> {
        self.run_order()
            .into_iter()
            .map(|id| self.settled_module(id, self.modules[id.0 as usize]))
            .collect()
    }

    /// A module, its files' top levels sorted to run, with its tests.
    fn settled_module(&mut self, id: ModuleId, scope: ScopeId) -> SettledModule {
        let files = &self.lib.modules[id.0 as usize].files;
        let roots: Vec<NodeId> = files
            .iter()
            .map(|f| self.lib.files[f.0 as usize].root)
            .collect();
        let top_levels =
            crate::order::sort_top_levels(&mut self.lib.tree, &self.scopes, scope, &roots);
        let tests = self.test_run(id, 0);
        let test_cases = self.test_cases(id, 0);
        SettledModule {
            id,
            scope,
            top_levels,
            tests,
            test_cases,
        }
    }
}

pub(crate) struct Settle {
    pub lib: Library,
    pub scopes: Scopes,
    engine: Engine<Fact>,
    work: Vec<Work>,
    /// Each module's scope, by [`ModuleId`].
    pub(crate) modules: Vec<ScopeId>,
    /// Each module's blocks, one per file, still being read.
    module_blocks: Vec<u32>,
    /// Declarations marked `export`, until they're declared.
    pub(crate) exports: HashSet<NodeId>,
    /// Declarations marked `@connected`, until they're expanded.
    pub(crate) connected: HashSet<NodeId>,
    /// Those of them that list backends, as `@connected(["cpp", "rust"])`,
    /// with the backends listed.
    pub(crate) connected_lists: HashMap<NodeId, Vec<String>>,
    /// Declarations marked `@auto("==")`, until they're classes, each with
    /// its decorator, where the `eq` it makes is spanned.
    pub(crate) auto_eq: HashMap<NodeId, NodeId>,
    /// Declarations marked `@fun`, until they're interfaces.
    pub(crate) fun_interfaces: HashSet<NodeId>,
    /// Declarations marked `sealed`, until they're interfaces.
    pub(crate) sealed: HashSet<NodeId>,
    /// Declarations marked `@json`, until they're classes, which get
    /// members to encode and decode JSON ([`crate::json`]).
    pub(crate) json_decorated: HashSet<NodeId>,
    /// What `@json` generates, and keeps between steps.
    pub(crate) json: crate::json::JsonState,
    /// Declarations marked `@staticExtension(T, "name")`, until they're
    /// declared: `T` and the name.
    pub(crate) static_extension_marks: HashMap<NodeId, (NodeId, NameId)>,
    pub(crate) static_extensions: Vec<StaticExtension>,
    /// Declarations marked `@extension("name")`, until they're declared,
    /// with the name; then each such function or a name for it, such as
    /// an import's, with the name, for typing to call where visible
    /// (`typing::instance_extensions`).
    pub(crate) extension_marks: HashMap<NodeId, NameId>,
    pub(crate) extensions: Vec<(SymbolId, NameId)>,
    /// Methods marked `@overload("name")`, with each such name: a call
    /// `c.name(…)`, where `c`'s class has no member `name`, calls the one
    /// that fits its arguments best ([`Settle::resolve_overload`]).
    pub(crate) overloads: HashMap<NodeId, Vec<NameId>>,
    /// Which module imports which.
    pub(crate) imports: Vec<(ModuleId, ModuleId)>,
    /// Each module's tests, by their `let`s' names, and the name its
    /// `runTestCases` is imported as (`testing`).
    pub(crate) tests: Vec<(ModuleId, NodeId)>,
    pub(crate) test_runners: Vec<(ModuleId, NodeId)>,
    /// Where `throws Error` may name the root, beyond std: each test's
    /// result, `Void throws Error`, which the compiler writes
    /// ([`Settle::expand_test`]), and each generator literal's, whose
    /// `next()` throws `Error` anyway.
    pub(crate) root_throws: HashSet<NodeId>,
    /// Each `assert(a == b)`'s message showing both values, with its
    /// plain one, `"expected a == b"`, to use where a value can't be
    /// shown ([`Settle::settle_assert_messages`]).
    pub(crate) assert_messages: Vec<(NodeId, NodeId)>,
    /// Lookups that resolved past a scope that wasn't complete yet, as a
    /// statement's macro name or when forced: the scope, name and use.
    passed: Vec<(ScopeId, NameId, Span)>,
    /// Statements to drop from their blocks, like comments.
    removed: HashSet<NodeId>,
    /// Object literals without `class: C` whose class isn't decided yet,
    /// with the scopes they're in ([`Settle::decide_object_literal`]).
    pub(crate) object_literals: BTreeMap<NodeId, ScopeId>,
    pub(crate) typing: Typing,
    /// The first file and node of the round being settled: 0 for a
    /// library settled at once; for a session that grows
    /// ([`Incremental`]), the first of what it added since the last
    /// round. The typing round and checks walk only what's new.
    round_files: usize,
    round_nodes: u32,
    /// A REPL session's top levels, a scope per input, each chained to
    /// the one before ([`Incremental`]).
    pub(crate) repl_scopes: HashSet<ScopeId>,
    /// Whether the REPL input this round settles may declare tests
    /// ([`Incremental::allow_tests`]); else a test there is an error.
    pub(crate) repl_tests: bool,
    /// The current step, for the debug check.
    step: u64,
    #[cfg(debug_assertions)]
    reads: Vec<(NodeId, u64)>,
    #[cfg(debug_assertions)]
    rewrites: Vec<(NodeId, u64)>,
}

/// What a step did.
enum Outcome {
    Done,
    Wait(Fact),
}

impl Settle {
    fn add(&mut self, work: Work, phase: u8, node: NodeId) -> ItemId {
        let id = self.engine.add(phase, self.lib.tree.span(node).start);
        debug_assert_eq!(id.0 as usize, self.work.len());
        self.work.push(work);
        id
    }

    /// Queues a visit of an expression.
    pub(crate) fn visit(&mut self, node: NodeId, scope: ScopeId) {
        self.add(Work::Expr { node, scope }, VISIT, node);
    }

    /// Queues completing a class's scope with what it inherits
    /// ([`Settle::inherit`]).
    pub(crate) fn queue_inherit(&mut self, class: NodeId, scope: ScopeId) {
        let item = self.add(Work::Inherit { class, scope }, VISIT, class);
        self.engine.will_produce(Fact::ScopeComplete(scope), item);
        self.engine.will_produce(Fact::ClassScope(class), item);
    }

    pub(crate) fn publish(&mut self, fact: Fact) {
        self.engine.publish(fact);
    }

    pub(crate) fn is_published(&self, fact: Fact) -> bool {
        self.engine.is_published(fact)
    }

    /// Queues deciding an object literal's class
    /// ([`Settle::decide_object_literal`]).
    pub(crate) fn queue_object_literal(&mut self, call: NodeId, scope: ScopeId) {
        let item = self.add(Work::ObjectLiteral { call, scope }, TYPES, call);
        self.engine.will_produce(Fact::ObjectLiteral(call), item);
    }

    /// Queues inferring a declaration's type ([`Settle::infer_decl`]).
    pub(crate) fn queue_type(&mut self, decl: NodeId) {
        let item = self.add(Work::Type { decl }, TYPES, decl);
        self.engine.will_produce(Fact::Type(decl), item);
    }

    /// Runs items until nothing's left to do, or the step budget runs out,
    /// returning false then.
    fn run(&mut self) -> bool {
        loop {
            let (id, forced) = match self.engine.next_step() {
                Next::Run(id) => (id, false),
                Next::Force(id, _) => (id, true),
                Next::Settled => return true,
                Next::OutOfBudget(id) => {
                    let node = match self.work[id.0 as usize] {
                        Work::Block { node, .. }
                        | Work::Expr { node, .. }
                        | Work::Inherit { class: node, .. }
                        | Work::Type { decl: node }
                        | Work::ObjectLiteral { call: node, .. } => node,
                    };
                    let steps = self.engine.steps();
                    self.lib.report(
                        Level::Error,
                        Message::OutOfSteps(steps),
                        self.lib.tree.span(node),
                    );
                    return false;
                }
            };
            self.step = self.engine.steps();
            let outcome = match self.work[id.0 as usize] {
                Work::Block { node, scope, next } => self.read_block(id, node, scope, next, forced),
                Work::Expr { node, scope } => self.expr(node, scope, forced),
                Work::Inherit { class, scope } => match self.inherit(class, scope, forced) {
                    Ok(()) => Outcome::Done,
                    Err(fact) => Outcome::Wait(fact),
                },
                Work::Type { decl } => match self.infer_decl(decl, forced) {
                    Ok(()) => Outcome::Done,
                    Err(fact) => Outcome::Wait(fact),
                },
                Work::ObjectLiteral { call, scope } => {
                    match self.decide_object_literal(call, scope, forced) {
                        Ok(()) => Outcome::Done,
                        Err(fact) => Outcome::Wait(fact),
                    }
                }
            };
            match outcome {
                Outcome::Done => self.engine.done(id),
                Outcome::Wait(fact) => self.engine.wait(id, fact),
            }
        }
    }

    // Reading blocks.

    fn read_block(
        &mut self,
        id: ItemId,
        node: NodeId,
        scope: ScopeId,
        mut next: u32,
        forced: bool,
    ) -> Outcome {
        let mut forced = forced;
        while let Some(&statement) = self.lib.tree.children(node).get(next as usize) {
            let flattened = self
                .flatten_declarations(statement)
                .or_else(|| self.destructure_decorated(statement));
            if let Some(statements) = flattened {
                let mut children = self.lib.tree.children(node).to_vec();
                children.splice(next as usize..=next as usize, statements);
                self.set_children(node, &children);
                continue;
            }
            // `let { a, b } = x` stands for several statements.
            if self.lib.tree.kind(statement) == NodeKind::Decl {
                let imported = match self.import(statement, scope, forced) {
                    Ok(imported) => imported,
                    Err(fact) => {
                        self.work[id.0 as usize] = Work::Block { node, scope, next };
                        return Outcome::Wait(fact);
                    }
                };
                if let Some(statements) = imported {
                    forced = false;
                    let mut children = self.lib.tree.children(node).to_vec();
                    children.splice(next as usize..=next as usize, statements);
                    self.set_children(node, &children);
                    continue;
                }
                if let Some(statements) = self.destructure(statement) {
                    let mut children = self.lib.tree.children(node).to_vec();
                    children.splice(next as usize..=next as usize, statements);
                    self.set_children(node, &children);
                    continue;
                }
            }
            if let Err(fact) = self.head(statement, scope, forced) {
                self.work[id.0 as usize] = Work::Block { node, scope, next };
                return Outcome::Wait(fact);
            }
            forced = false;
            next += 1;
            // A `@json` type's adapter class, declared after it.
            if let Some(siblings) = self.json.siblings.remove(&statement) {
                let mut children = self.lib.tree.children(node).to_vec();
                let at = next as usize;
                children.splice(at..at, siblings);
                self.set_children(node, &children);
            }
        }
        let children = self.lib.tree.children(node);
        if children.iter().any(|c| self.removed.contains(c)) {
            let kept: Vec<NodeId> = children
                .iter()
                .copied()
                .filter(|c| !self.removed.contains(c))
                .collect();
            self.set_children(node, &kept);
        }
        if let Some(m) = self.modules.iter().position(|&s| s == scope) {
            self.module_blocks[m] -= 1;
            if self.module_blocks[m] > 0 {
                return Outcome::Done;
            }
        }
        self.scopes.complete(scope);
        self.engine.publish(Fact::ScopeComplete(scope));
        Outcome::Done
    }

    /// Expands a statement as far as needed to see whether it declares
    /// something, then queues its visit.
    fn head(&mut self, statement: NodeId, scope: ScopeId, forced: bool) -> Result<(), Fact> {
        loop {
            match self.lib.tree.kind(statement) {
                NodeKind::Decl => {
                    self.lower_decl(statement);
                }
                NodeKind::Let | NodeKind::Class => {
                    self.declare(statement, scope);
                    self.visit(statement, scope);
                    return Ok(());
                }
                NodeKind::Call => {
                    let Some(&callee) = self.lib.tree.children(statement).first() else {
                        self.visit(statement, scope);
                        return Ok(());
                    };
                    match self.lib.tree.kind(callee) {
                        NodeKind::RightName if !self.is_temporary(callee) => {
                            let name = self.lib.name(callee).unwrap();
                            match self.lookup(scope, name, callee, true, forced) {
                                Lookup::Wait(s) => return Err(Fact::ScopeComplete(s)),
                                Lookup::Found(b) => {
                                    // Only a macro is settled here; anything else
                                    // resolves when visited, once its scope is complete.
                                    if let SymbolKind::Builtin(Builtin::Macro(m)) =
                                        self.scopes.symbol(b).kind
                                    {
                                        self.scopes.resolve(callee, b);
                                        self.expand(statement, scope, m);
                                        continue;
                                    }
                                }
                                Lookup::Missing => {}
                            }
                        }
                        NodeKind::Value => match self.lib.value(callee) {
                            Some(Value::Builtin(SyntaxBuiltin::EmbeddedComment)) => {
                                self.removed.insert(statement);
                                return Ok(());
                            }
                            Some(Value::Builtin(SyntaxBuiltin::DocComment)) => {
                                self.expand_syntax(statement, scope, SyntaxBuiltin::DocComment);
                                continue;
                            }
                            _ => {}
                        },
                        _ => {}
                    }
                    self.visit(statement, scope);
                    return Ok(());
                }
                _ => {
                    self.visit(statement, scope);
                    return Ok(());
                }
            }
        }
    }

    /// Declares a `Let`'s or `Class`'s name in a scope, unless it's
    /// declared already.
    pub(crate) fn declare(&mut self, let_node: NodeId, scope: ScopeId) {
        let name_node = self.lib.tree.children(let_node)[0];
        if self.scopes.resolved(name_node).is_some() {
            return;
        }
        let name = self.lib.name(name_node).unwrap();
        let is_let = self.lib.tree.kind(let_node) == NodeKind::Let;
        let mutable = is_let && self.lib.tree.data(let_node) & core::LET_VAR != 0;
        let kind = if self.lib.names.is_temporary(name) {
            SymbolKind::Synthetic
        } else {
            match self.scopes.get(scope).kind {
                ScopeKind::Module => SymbolKind::TopLevel,
                ScopeKind::Fn | ScopeKind::LoopBody => SymbolKind::Parameter,
                ScopeKind::Class => SymbolKind::Member,
                ScopeKind::TypeParams => SymbolKind::TypeParam,
                _ => SymbolKind::Local,
            }
        };
        let b = self
            .scopes
            .declare(scope, name, kind, Some(let_node), mutable);
        if kind == SymbolKind::Member
            && is_let
            && self.lib.tree.data(let_node) & core::MEMBER_NO_PROPERTY != 0
        {
            self.scopes.mark_input(b);
        }
        self.note_extension(let_node, b, scope);
        // As in Kotlin Temper, a block's local is visible only after its
        // declaration, so `var x = x` reads an outer `x`; functions and
        // classes are visible all through the block, for mutual recursion.
        // So too a REPL input's top-level `let`, so `let s = "${s}!"`
        // reads the `s` of an earlier input.
        let block_like = kind == SymbolKind::Local
            && self.scopes.get(scope).kind == ScopeKind::Block
            || kind == SymbolKind::TopLevel && self.repl_scopes.contains(&scope);
        if block_like && is_let {
            let init = core::let_parts(&self.lib.tree, let_node).2;
            if !init.is_some_and(|i| matches!(self.lib.tree.kind(i), NodeKind::Fn | NodeKind::Fun))
            {
                self.scopes
                    .set_visible_from(b, self.lib.tree.span(let_node).end);
            }
        }
        self.scopes.resolve(name_node, b);
        if self.exports.remove(&let_node) && kind == SymbolKind::TopLevel {
            self.scopes.export(b);
        }
        let uses: Vec<Span> = self
            .passed
            .iter()
            .filter(|(s, n, _)| *s == scope && *n == name)
            .map(|&(_, _, span)| span)
            .collect();
        let visible_from = self.scopes.symbol(b).visible_from;
        let uses = uses.into_iter().filter(|span| span.start >= visible_from);
        for span in uses {
            let text = self.lib.names.get(name).to_string();
            self.lib
                .report(Level::Error, Message::DeclaredAfterUse(text), span);
        }
    }

    /// Its modules in the order they run: each after the ones it imports,
    /// and otherwise in the order they were loaded. A cycle breaks where it's
    /// entered, as for top-level statements.
    fn run_order(&self) -> Vec<ModuleId> {
        let count = self.modules.len();
        let mut entered = vec![false; count];
        let mut order = Vec::with_capacity(count);
        let mut walk: Vec<(ModuleId, usize)> = Vec::new();
        for start in 0..count as u32 {
            if entered[start as usize] {
                continue;
            }
            entered[start as usize] = true;
            walk.push((ModuleId(start), 0));
            while let Some(top) = walk.last_mut() {
                let (m, next) = *top;
                match self.imports.iter().filter(|(from, _)| *from == m).nth(next) {
                    Some(&(_, to)) => {
                        top.1 += 1;
                        if !entered[to.0 as usize] {
                            entered[to.0 as usize] = true;
                            walk.push((to, 0));
                        }
                    }
                    None => {
                        walk.pop();
                        order.push(m);
                    }
                }
            }
        }
        order
    }

    /// Looks up a name used at `node`, recording lookups that resolve past an
    /// incomplete scope.
    pub(crate) fn lookup(
        &mut self,
        scope: ScopeId,
        name: NameId,
        node: NodeId,
        so_far: bool,
        forced: bool,
    ) -> Lookup {
        let mut from = scope;
        let mut skipped = Vec::new();
        let pos = self.lib.tree.span(node).start;
        let result = loop {
            match self.scopes.lookup(from, name, so_far && from == scope, pos) {
                Lookup::Wait(s) if forced => {
                    skipped.push(s);
                    match self.scopes.get(s).parent {
                        Some(parent) => from = parent,
                        None => break Lookup::Missing,
                    }
                }
                other => break other,
            }
        };
        // What a statement's macro name resolves to is final, as is what a
        // forced lookup finds; other lookups in the starting scope are
        // redone once it's complete.
        let found_in = match result {
            Lookup::Found(b) => Some(self.scopes.symbol(b).owner),
            _ => None,
        };
        let is_macro = |b| {
            matches!(
                self.scopes.symbol(b).kind,
                SymbolKind::Builtin(Builtin::Macro(_))
            )
        };
        let settles = match result {
            Lookup::Found(b) => forced || is_macro(b),
            _ => false,
        };
        if !settles {
            skipped.clear();
        } else if so_far && !self.scopes.get(scope).complete {
            skipped.push(scope);
        }
        let span = self.lib.tree.span(node);
        for s in skipped {
            if found_in != Some(s) {
                self.passed.push((s, name, span));
            }
        }
        if let Lookup::Found(b) = result {
            if let Some(decl) = self.scopes.symbol(b).decl {
                self.read(decl);
            }
        }
        result
    }

    // Visiting expressions.

    fn expr(&mut self, node: NodeId, scope: ScopeId, forced: bool) -> Outcome {
        loop {
            let tree = &self.lib.tree;
            match tree.kind(node) {
                NodeKind::Value | NodeKind::Error | NodeKind::Break | NodeKind::Continue => {}
                NodeKind::LeftName => {}
                NodeKind::RightName => {
                    if let Err(fact) = self.resolve_name(node, scope, forced) {
                        return Outcome::Wait(fact);
                    }
                    if let Some(Builtin::Macro(m)) = self.scopes.resolved_builtin(node) {
                        let name = Builtin::Macro(m).name().to_string();
                        self.error(node, Message::MalformedSpecial(name));
                    }
                }
                NodeKind::BuiltinName => {
                    let name = self.lib.name(node).unwrap();
                    match self.scopes.builtin(name) {
                        Some(b) => self.scopes.resolve(node, b),
                        None => {
                            let text = format!("builtin {}", self.lib.names.get(name));
                            self.error(node, Message::Unsupported(text));
                        }
                    }
                }
                NodeKind::Decl => {
                    self.error(node, Message::MalformedDeclaration);
                }
                NodeKind::Let => {
                    self.declare(node, scope);
                    let parts = self.lib.tree.children(node)[1..].to_vec();
                    for part in parts {
                        self.visit(part, scope);
                    }
                }
                NodeKind::Block => self.enter_block(node, scope),
                NodeKind::Fun => {
                    self.lower_fun(node);
                    continue;
                }
                NodeKind::Fn => self.enter_fn(node, scope),
                NodeKind::Class => self.enter_class(node, scope),
                NodeKind::Loop => {
                    let loop_scope = self.scopes.add(scope, ScopeKind::Loop, node, true);
                    let parts = self.lib.tree.children(node).to_vec();
                    for part in parts {
                        self.visit(part, loop_scope);
                    }
                }
                NodeKind::Assign => {
                    let target = self.lib.tree.children(node)[0];
                    if self.lib.tree.kind(target) != NodeKind::RightName {
                        // `obj.name = value`, a field or setter; for `this`,
                        // checked as a bare member name is.
                        let member = match self.this_member(target, scope, forced) {
                            Ok(member) => member,
                            Err(fact) => return Outcome::Wait(fact),
                        };
                        if let Some(b) = member.filter(|&b| !self.member_assignable(b, scope)) {
                            self.report_member_assignment(b, target);
                            let value = self.lib.tree.children(node)[1];
                            self.become_node(node, value);
                            continue;
                        }
                        let parts = self.lib.tree.children(node).to_vec();
                        for part in parts {
                            self.visit(part, scope);
                        }
                        return Outcome::Done;
                    }
                    if let Err(fact) = self.resolve_name(target, scope, forced) {
                        return Outcome::Wait(fact);
                    }
                    if let Some(b) = self.scopes.resolved(target) {
                        let symbol = *self.scopes.symbol(b);
                        // A `let` without a value may be assigned once, and a
                        // field, only in its class's constructor.
                        let assignable = if symbol.kind == SymbolKind::Member {
                            self.member_assignable(b, scope)
                        } else {
                            symbol.mutable
                                || symbol
                                    .decl
                                    .is_some_and(|d| self.lib.tree.data(d) & core::LET_INIT == 0)
                        };
                        if !assignable {
                            if symbol.kind == SymbolKind::Member {
                                self.report_member_assignment(b, target);
                            } else {
                                let text = self.lib.names.get(symbol.name).to_string();
                                let span = self.lib.tree.span(target);
                                self.lib.report(
                                    Level::Error,
                                    Message::IllegalAssignment(text),
                                    span,
                                );
                            }
                            // It's not performed: the value stands in.
                            let value = self.lib.tree.children(node)[1];
                            self.become_node(node, value);
                            continue;
                        }
                    }
                    let value = self.lib.tree.children(node)[1];
                    self.visit(value, scope);
                }
                NodeKind::If
                | NodeKind::Orelse
                | NodeKind::Fail
                | NodeKind::Return
                | NodeKind::Yield
                | NodeKind::Await => {
                    let parts = self.lib.tree.children(node).to_vec();
                    for part in parts {
                        self.visit(part, scope);
                    }
                }
                NodeKind::Call => match self.call(node, scope, forced) {
                    Ok(true) => continue,
                    Ok(false) => {}
                    Err(fact) => return Outcome::Wait(fact),
                },
                NodeKind::Esc => self.error(node, Message::Unsupported("quoted trees".into())),
                NodeKind::Op(_) | NodeKind::Token => unreachable!("parse node in a library tree"),
            }
            return Outcome::Done;
        }
    }

    /// Resolves a used name, unless it's resolved already.
    pub(crate) fn resolve_name(
        &mut self,
        node: NodeId,
        scope: ScopeId,
        forced: bool,
    ) -> Result<(), Fact> {
        if self.lib.tree.kind(node) != NodeKind::RightName || self.scopes.resolved(node).is_some() {
            return Ok(());
        }
        if let Some(&path) = self.json.std_refs.get(&node) {
            return self.resolve_std_ref(node, path, forced);
        }
        let name = self.lib.name(node).unwrap();
        match self.lookup(scope, name, node, false, forced) {
            Lookup::Found(b) => self.scopes.resolve(node, b),
            Lookup::Wait(s) => return Err(Fact::ScopeComplete(s)),
            Lookup::Missing => {
                let text = self.lib.names.get(name).to_string();
                self.error(node, Message::UndeclaredName(text));
            }
        }
        Ok(())
    }

    /// Visits a call: expands it if its callee is a macro, returning true,
    /// or queues its parts.
    fn call(&mut self, node: NodeId, scope: ScopeId, forced: bool) -> Result<bool, Fact> {
        let children = self.lib.tree.children(node).to_vec();
        let Some(&callee) = children.first() else {
            self.error(node, Message::Unsupported("a call without a callee".into()));
            return Ok(false);
        };
        // A sealed `@json` interface's decoder, once its module is read.
        if self.json_sealed_body(node, forced)? {
            return Ok(true);
        }
        // `T.name()` may become `f()`, rewriting the callee in place.
        self.static_extension_call(node, scope, forced)?;
        if self.is_chain_null(callee) {
            // `a?.m(args)`: the whole call is skipped if `a` is null.
            self.expand_chain_null(node, callee, scope);
            return Ok(true);
        }
        match self.lib.tree.kind(callee) {
            NodeKind::BuiltinName if self.is_chain_null(node) => {
                self.expand_chain_null(node, node, scope);
                return Ok(true);
            }
            NodeKind::BuiltinName
                if self
                    .lib
                    .name(callee)
                    .is_some_and(|n| self.lib.names.get(n) == "rgx") =>
            {
                return self.regex_literal(node, forced);
            }
            NodeKind::RightName => {
                self.resolve_name(callee, scope, forced)?;
                match self.scopes.resolved_builtin(callee) {
                    Some(Builtin::Macro(m)) => {
                        self.expand(node, scope, m);
                        return Ok(true);
                    }
                    Some(Builtin::Function(crate::builtins::Function::Rgx)) => {
                        return self.regex_literal(node, forced);
                    }
                    Some(Builtin::Function(crate::builtins::Function::New)) => {
                        if self.object_literal(node, scope, forced)? {
                            return Ok(true);
                        }
                        self.check_new(node, scope, forced)?;
                    }
                    _ => {}
                }
            }
            NodeKind::Value => {
                if let Some(Value::Builtin(b)) = self.lib.value(callee) {
                    let b = *b;
                    if let (SyntaxBuiltin::StringExpr, [_, tag, tagged, ..]) = (b, &children[..]) {
                        // Whether a tag names a class decides how it's
                        // lowered ([`Settle::string_expr`]).
                        if self.lib.value(*tagged) == Some(&Value::Bool(true)) {
                            self.resolve_tag(*tag, scope, forced)?;
                        }
                    }
                    if b == SyntaxBuiltin::This {
                        self.expand_this(node, scope);
                    } else {
                        self.expand_syntax(node, scope, b);
                    }
                    return Ok(true);
                }
                self.visit(callee, scope);
            }
            _ => self.visit(callee, scope),
        }
        let children = self.lib.tree.children(node).to_vec();
        for &arg in &children[1..] {
            self.visit(arg, scope);
        }
        Ok(false)
    }

    /// Makes a block's scope, or a label's and then the block's, and queues
    /// reading it.
    fn enter_block(&mut self, node: NodeId, scope: ScopeId) {
        let children = self.lib.tree.children(node);
        let mut parent = scope;
        if let [label, name, ..] = *children {
            let is_label = self
                .lib
                .symbol(label)
                .is_some_and(|s| self.lib.names.get(s) == "label");
            if is_label && self.lib.tree.kind(name) == NodeKind::LeftName {
                let name = self.lib.name(name).unwrap();
                parent = self.scopes.add(scope, ScopeKind::Label(name), node, true);
                let rest = children[2..].to_vec();
                self.set_children(node, &rest);
            }
        }
        let block_scope = self.scopes.add(parent, ScopeKind::Block, node, false);
        let item = self.add(
            Work::Block {
                node,
                scope: block_scope,
                next: 0,
            },
            READ_BLOCKS,
            node,
        );
        self.engine
            .will_produce(Fact::ScopeComplete(block_scope), item);
    }

    /// Makes a function's scope with its parameters, and queues its parts.
    fn enter_fn(&mut self, node: NodeId, scope: ScopeId) {
        let scope = self.enter_type_params(node, scope);
        let kind = if self.lib.tree.data(node) & core::FN_LOOP_BODY != 0 {
            ScopeKind::LoopBody
        } else {
            ScopeKind::Fn
        };
        let fn_scope = self.scopes.add(scope, kind, node, true);
        let (params, out_type, body) = core::fn_parts(&self.lib.tree, node);
        for &param in &params {
            self.declare(param, fn_scope);
            let parts = self.lib.tree.children(param)[1..].to_vec();
            for part in parts {
                self.visit(part, fn_scope);
            }
        }
        if let Some(t) = out_type {
            self.visit(t, fn_scope);
        }
        let supers = core::fn_supers(&self.lib.tree, node).to_vec();
        for sup in supers {
            self.visit(sup, fn_scope);
        }
        if let Some(body) = body {
            self.visit(body, fn_scope);
        }
    }

    /// Makes a scope for a generic `Fn`'s or `Class`'s type parameters, if
    /// any, declaring them and queueing their bounds, which may use them.
    /// Returns the scope for the rest.
    pub(crate) fn enter_type_params(&mut self, node: NodeId, scope: ScopeId) -> ScopeId {
        let params = core::type_params(&self.lib.tree, node).to_vec();
        if params.is_empty() {
            return scope;
        }
        let inner = self.scopes.add(scope, ScopeKind::TypeParams, node, true);
        for &param in &params {
            self.declare(param, inner);
        }
        for param in params {
            let parts = self.lib.tree.children(param)[1..].to_vec();
            for part in parts {
                self.visit(part, inner);
            }
        }
        inner
    }

    // Rewriting, with the records for the debug check.

    pub(crate) fn rewrite(&mut self, node: NodeId, kind: NodeKind, data: u32, children: &[NodeId]) {
        #[cfg(debug_assertions)]
        self.rewrites.push((node, self.step));
        self.lib.tree.replace(node, kind, data, children);
    }

    pub(crate) fn set_children(&mut self, node: NodeId, children: &[NodeId]) {
        #[cfg(debug_assertions)]
        self.rewrites.push((node, self.step));
        self.lib.tree.set_children(node, children);
    }

    /// Rewrites a node into an error node.
    pub(crate) fn error(&mut self, node: NodeId, message: Message) {
        #[cfg(debug_assertions)]
        self.rewrites.push((node, self.step));
        self.lib.error(node, message);
    }

    /// Makes `node` a copy of `other`, which becomes garbage.
    pub(crate) fn become_node(&mut self, node: NodeId, other: NodeId) {
        let tree = &self.lib.tree;
        let (kind, data, children) = (
            tree.kind(other),
            tree.data(other),
            tree.children(other).to_vec(),
        );
        self.rewrite(node, kind, data, &children);
        if let Some(b) = self.scopes.resolved(other) {
            self.scopes.resolve(node, b);
        }
    }

    pub(crate) fn read(&mut self, _node: NodeId) {
        #[cfg(debug_assertions)]
        self.reads.push((_node, self.step));
    }

    /// Checks the reads and rewrites recorded this round, then forgets
    /// them, so a session's rounds check only their own.
    #[cfg(debug_assertions)]
    fn check_reads(&mut self) {
        let mut last_rewrite = std::collections::HashMap::new();
        for &(node, step) in &self.rewrites {
            last_rewrite.insert(node, step);
        }
        for &(node, step) in &self.reads {
            if let Some(&rewritten) = last_rewrite.get(&node) {
                assert!(
                    rewritten <= step,
                    "step {step} read node {node:?}, rewritten later at step {rewritten}"
                );
            }
        }
        self.reads.clear();
        self.rewrites.clear();
    }

    pub(crate) fn is_temporary(&self, node: NodeId) -> bool {
        self.lib
            .name(node)
            .is_some_and(|n| self.lib.names.is_temporary(n))
    }

    /// The scopes around `scope` up to its function, for jump targets.
    pub(crate) fn enclosing(&self, scope: ScopeId) -> Vec<(ScopeKind, NodeId)> {
        self.scopes
            .within_function(scope)
            .map(|s| (s.kind, s.node))
            .collect()
    }

    /// A pre-resolved name node for a builtin that macros use.
    pub(crate) fn builtin_name(&mut self, symbol: SymbolId, span: Span, like: NodeId) -> NodeId {
        let name = self.scopes.symbol(symbol).name;
        let node = self.lib.add_name(NodeKind::RightName, name, span, like);
        self.scopes.resolve(node, symbol);
        node
    }
}
