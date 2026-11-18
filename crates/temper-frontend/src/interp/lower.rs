//! Lowering for the interpreter: each function body, or other code run on
//! its own, becomes a [`Code`], a flat list of ops for a small stack
//! machine, with jumps for control flow and locals in numbered slots.
//! [`super::frame`] runs it. Lowering is late and per function, on its
//! first call, from the settled tree as it is then, and cached for the run.
//!
//! A local's *home* is the scope whose frame holds it: a function's (a
//! `for … of` body is one too), a class's (for its initializers, which run
//! with `this`), or a module's (for its top-level code; the module's own
//! declarations are globals). [`Layout`] numbers a home's locals. Most get
//! a slot; those that a nested function or class uses get a shared cell
//! instead, made fresh each time their block is entered, so that each pass
//! of a loop has its own. A function or class captures, when it's made,
//! the cells it uses from the homes around it.
//!
//! `break`, `continue` and `return` are jumps, except where they leave a
//! `for … of` body: then they end its call as an [`Abrupt`], and the frame
//! they reach jumps to a landing. A failure jumps to the handler of the
//! innermost `orelse` around the op that failed, found in a table by
//! position, so the ordinary path pays nothing for it.
//!
//! A generator's body, a `Fn` that `extends GeneratorFn`, pauses at each
//! `yield`, and once before its body, so that making a generator checks
//! the arguments and computes defaults, as calling a function does; see
//! [`super::Generator`]. An `async` block is a generator, which pauses
//! too at an `await` of a promise that isn't resolved yet.
//!
//! [`Abrupt`]: super::Abrupt

use super::{Val, builtin_value, is_dot, literal};
use crate::builtins::{Builtin, Function, TYPES};
use crate::core::{
    self, LET_VAR, LOOP_NEXT, MEMBER_CONNECTED, ROLE_CONSTRUCTOR, ROLE_GETTER, ROLE_MASK,
    ROLE_METHOD, ROLE_SETTER,
};
use crate::scope::{ScopeId, ScopeKind, SymbolId, SymbolKind};
use crate::settle::Settled;
use std::collections::hash_map::Entry;
use std::collections::{HashMap, HashSet};
use std::rc::Rc;
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind};

/// How deeply expressions may nest; deeper ones panic when run, so that
/// lowering's recursion stays within the stack.
const MAX_NESTING: u32 = 3000;

/// An op of the stack machine. Each takes its operands from the top of the
/// frame's stack and pushes its result, if any.
#[derive(Debug)]
pub(super) enum Op {
    Const(Val),
    /// Reads a slot, a cell, a captured cell or a global; reading one not
    /// yet set fails.
    Local(u32),
    Cell(u32),
    Capture(u32),
    Global(u32),
    /// Pops into a slot, a cell, a captured cell or a global.
    SetLocal(u32),
    SetCell(u32),
    SetCapture(u32),
    SetGlobal(u32),
    /// Gives a cell a fresh binding, on entering its block.
    FreshCell(u32),
    Pop,
    Dup,
    /// Drops the stack to a depth, for a jump out of an expression.
    Truncate(u32),
    Jump(u32),
    /// Pops a Boolean, jumping if it's false.
    JumpIfFalse(u32),
    /// `obj.name`: pops `obj`.
    GetProp(NameId),
    /// A `@noProperty` input's value, which an initializer reads: pops
    /// `this`, and reads its field in this slot.
    GetInput(u32),
    /// `obj.name = value`: pops both, pushes `value`.
    SetProp(NameId),
    /// Pops the arguments and the callee.
    Call {
        args: u32,
    },
    /// Pops the arguments and the object.
    CallMethod {
        name: NameId,
        args: u32,
    },
    MakeFn {
        node: NodeId,
        sources: Box<[Source]>,
    },
    MakeClass {
        node: NodeId,
        layout: Rc<Layout>,
        sources: Box<[Source]>,
    },
    /// A `Class` statement's static field initializers: pops the class.
    InitStatics,
    /// Pops the value and returns it.
    Return,
    /// Pops the value and pauses the generator, returning it from
    /// `next()`; `next()` goes on with the op after.
    Yield,
    /// Pops a promise and pushes its value, or fails if it's broken. One
    /// not resolved yet pauses the generator, an `async` block's, until it
    /// is; the op then runs again.
    Await,
    /// A constant regex literal's value, if it's been made: pushes it from
    /// the interpreter's cache, by its index, and jumps past the code
    /// making it; else goes on to that code.
    Cached {
        index: u32,
        skip: u32,
    },
    /// Keeps the value on top in the cache, by its index.
    Cache(u32),
    /// Ends the call with a `break`, `continue` or `return` (popping its
    /// value) for a frame further out.
    Exit(Exit),
    Fail(Option<Rc<str>>),
    Panic(Rc<str>),
    /// A parameter without a default, whose argument must be there.
    Required(u32),
    /// A parameter with a default: skips computing it if the argument is
    /// there and not null.
    Default {
        slot: u32,
        skip: u32,
    },
}

/// A jump out of a frame, to the `Block`, `Loop` or `Fn` it names.
#[derive(Clone, Copy, Debug)]
pub(super) enum Exit {
    Break(NodeId),
    Continue(NodeId),
    Return(NodeId),
}

/// Where a closure's or class's captured cell comes from in the frame
/// making it.
#[derive(Clone, Copy, Debug)]
pub(super) enum Source {
    Cell(u32),
    Capture(u32),
}

/// A failure in ops `start..end` goes to `to`, with the stack at `depth`.
#[derive(Debug)]
pub(super) struct Handler {
    pub start: u32,
    pub end: u32,
    pub to: u32,
    pub depth: u32,
}

/// A `break` (or with `continue_`, a `continue`) of `target` coming back
/// from a call in ops `start..end` goes to `to`, with the stack at `depth`.
#[derive(Debug)]
pub(super) struct Landing {
    pub target: NodeId,
    pub continue_: bool,
    pub start: u32,
    pub end: u32,
    pub to: u32,
    pub depth: u32,
}

/// What calling a `Fn` that `extends GeneratorFn` makes: a
/// `SafeGenerator` if it declares a return type that doesn't fail, as
/// Kotlin Temper's `AdaptGeneratorFn` chooses, or else a `Generator`, whose
/// `next()` passes on a failure from the body.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(super) enum GeneratorKind {
    Bubbly,
    Safe,
}

/// Lowered code: a function's body, a module's top level, or a class's
/// initializer.
#[derive(Debug)]
pub(super) struct Code {
    /// The function, which `return` and `continue` from a `for … of`
    /// body may reach.
    pub function: Option<NodeId>,
    /// Set for a generator's body.
    pub generator: Option<GeneratorKind>,
    pub ops: Vec<Op>,
    /// Each op's node, for error messages.
    pub nodes: Vec<NodeId>,
    /// Arguments fill the first slots: a function's parameters, or a
    /// class's `this`.
    pub params: u32,
    pub slots: u32,
    pub cells: u32,
    /// The deepest the stack gets.
    pub stack: u32,
    /// The variables it captures, in order.
    pub free: Rc<[SymbolId]>,
    /// The classes it's inside, whose private members it may use.
    pub classes: Rc<[NodeId]>,
    pub handlers: Vec<Handler>,
    pub landings: Vec<Landing>,
}

/// Where a home keeps a local.
#[derive(Clone, Copy, Debug)]
enum Place {
    Slot(u32),
    Cell(u32),
}

/// A home's frame layout, from a pass over its code.
#[derive(Debug)]
pub(super) struct Layout {
    /// Set from arguments, in the first slots, and moved into cells if
    /// captured: a function's parameters, or a class's `this`.
    params: Vec<Option<SymbolId>>,
    places: HashMap<SymbolId, Place>,
    slots: u32,
    cells: u32,
    /// Variables of the homes around it that it uses, nested code's
    /// included.
    pub free: Rc<[SymbolId]>,
    /// Blocks and loops that `break` or `continue` name.
    targets: HashSet<NodeId>,
    /// The classes its code is inside, whose private members it may use.
    classes: Rc<[NodeId]>,
}

/// How a node is reached in a [`Layout`]'s pass: in the home's own code,
/// inside a function or class nested in it, or in one of the class's
/// methods, which get `this` from the call, not a cell.
#[derive(Clone, Copy, PartialEq)]
enum Within {
    Home,
    Nested,
    Method,
}

impl Layout {
    /// The layout of `home`'s code, `roots` and all they contain.
    fn new(s: &Settled, home: ScopeId, roots: &[NodeId]) -> Layout {
        let tree = &s.library.tree;
        let scope = s.scopes.get(home);
        let params: Vec<Option<SymbolId>> = match scope.kind {
            ScopeKind::Fn | ScopeKind::LoopBody => {
                let params = core::fn_parts(tree, scope.node).0;
                params
                    .iter()
                    .map(|&p| s.scopes.resolved(core::let_parts(tree, p).0))
                    .collect()
            }
            ScopeKind::Class => vec![scope.names.first().map(|&(_, this)| this)],
            _ => Vec::new(),
        };
        let mut ancestors = Vec::new();
        let mut at = scope.parent.and_then(|p| home_of(s, p));
        while let Some(h) = at {
            ancestors.push(h);
            at = s.scopes.get(h).parent.and_then(|p| home_of(s, p));
        }
        // Locals in the order first seen.
        let mut seen = Vec::new();
        let mut seen_set = HashSet::new();
        let mut captured = HashSet::new();
        let mut free = Vec::new();
        let mut targets = HashSet::new();
        let mut stack: Vec<(NodeId, Within)> =
            roots.iter().rev().map(|&r| (r, Within::Home)).collect();
        while let Some((node, within)) = stack.pop() {
            let kind = tree.kind(node);
            match kind {
                NodeKind::LeftName | NodeKind::RightName | NodeKind::BuiltinName => {
                    if let Some(symbol) = s.scopes.resolved(node).and_then(|b| variable(s, b)) {
                        let owner = home_of(s, s.scopes.symbol(symbol).owner);
                        if owner == Some(home) {
                            if seen_set.insert(symbol) {
                                seen.push(symbol);
                            }
                            if within == Within::Nested {
                                captured.insert(symbol);
                            }
                        } else if owner.is_some_and(|o| ancestors.contains(&o))
                            && !free.contains(&symbol)
                        {
                            free.push(symbol);
                        }
                    }
                }
                NodeKind::Break | NodeKind::Continue => {
                    targets.insert(NodeId(tree.data(node)));
                }
                _ => {}
            }
            let is_root = node == scope.node;
            let nested = if within == Within::Home {
                Within::Nested
            } else {
                within
            };
            for (i, &child) in tree.children(node).iter().enumerate().rev() {
                let child_within = match kind {
                    // The class's own name belongs to the scope around it.
                    NodeKind::Class if is_root && i == 0 => continue,
                    NodeKind::Class if is_root && is_method(s, child) => Within::Method,
                    _ if is_root => within,
                    NodeKind::Fn => nested,
                    NodeKind::Class if i > 0 => nested,
                    _ => within,
                };
                stack.push((child, child_within));
            }
        }
        let mut places = HashMap::new();
        let mut cells = 0;
        let mut place = |symbol: SymbolId, slot: u32| {
            if captured.contains(&symbol) {
                cells += 1;
                Place::Cell(cells - 1)
            } else {
                Place::Slot(slot)
            }
        };
        for (i, &param) in params.iter().enumerate() {
            if let Some(param) = param {
                places.insert(param, place(param, i as u32));
            }
        }
        let mut slots = params.len() as u32;
        for symbol in seen {
            if let Entry::Vacant(entry) = places.entry(symbol) {
                let at = entry.insert(place(symbol, slots));
                slots += matches!(at, Place::Slot(_)) as u32;
            }
        }
        let mut classes = Vec::new();
        let mut at = Some(home);
        while let Some(id) = at {
            let scope = s.scopes.get(id);
            if scope.kind == ScopeKind::Class {
                classes.push(scope.node);
            }
            at = scope.parent;
        }
        Layout {
            params,
            places,
            slots,
            cells,
            free: free.into(),
            targets,
            classes: classes.into(),
        }
    }

    /// A class's `this`.
    pub(super) fn this(&self) -> Option<SymbolId> {
        self.params.first().copied().flatten()
    }

    /// The cell a captured parameter moves to.
    fn param_cell(&self, i: usize) -> Option<u32> {
        match self
            .params
            .get(i)
            .copied()
            .flatten()
            .and_then(|p| self.places.get(&p))
        {
            Some(&Place::Cell(cell)) => Some(cell),
            _ => None,
        }
    }
}

/// Whether a `Fn` makes generators, and which kind.
fn generator_kind(s: &Settled, f: NodeId) -> Option<GeneratorKind> {
    let tree = &s.library.tree;
    let is_generator_fn = |t: NodeId| matches!(s.scopes.resolved_builtin(t), Some(Builtin::Type(i)) if TYPES[i as usize] == "GeneratorFn");
    if !core::fn_supers(tree, f).iter().any(|&t| is_generator_fn(t)) {
        return None;
    }
    // `T throws Bubble`.
    let fails = |t: NodeId| {
        let callee = tree
            .children(t)
            .first()
            .filter(|_| tree.kind(t) == NodeKind::Call);
        callee.is_some_and(|&c| {
            s.scopes.resolved_builtin(c) == Some(Builtin::Function(Function::Throws))
        })
    };
    match core::fn_parts(tree, f).1 {
        Some(out_type) if !fails(out_type) => Some(GeneratorKind::Safe),
        _ => Some(GeneratorKind::Bubbly),
    }
}

/// The variable a name means, for frames: `this` for a class member, none
/// for builtins, type parameters and module-level declarations (globals).
fn variable(s: &Settled, symbol: SymbolId) -> Option<SymbolId> {
    let sym = s.scopes.symbol(symbol);
    match sym.kind {
        SymbolKind::Builtin(_) | SymbolKind::TypeParam => None,
        SymbolKind::Member => s.scopes.get(sym.owner).names.first().map(|&(_, this)| this),
        _ if s.scopes.get(sym.owner).kind == ScopeKind::Module => None,
        _ => Some(symbol),
    }
}

/// The home whose frame holds a scope's locals.
fn home_of(s: &Settled, mut scope: ScopeId) -> Option<ScopeId> {
    loop {
        let sc = s.scopes.get(scope);
        match sc.kind {
            ScopeKind::Fn | ScopeKind::LoopBody | ScopeKind::Class | ScopeKind::Module => {
                return Some(scope);
            }
            ScopeKind::Builtins => return None,
            _ => scope = sc.parent?,
        }
    }
}

/// Whether a class member runs as a method, getter, setter or
/// constructor, called with `this`.
fn is_method(s: &Settled, member: NodeId) -> bool {
    let tree = &s.library.tree;
    tree.kind(member) == NodeKind::Let
        && matches!(
            tree.data(member) & ROLE_MASK,
            ROLE_METHOD | ROLE_GETTER | ROLE_SETTER | ROLE_CONSTRUCTOR
        )
}

fn cached(codes: &[Option<Rc<Code>>], node: NodeId) -> Option<Rc<Code>> {
    codes.get(node.0 as usize).and_then(Option::clone)
}

fn cache(codes: &mut Vec<Option<Rc<Code>>>, node: NodeId, code: Rc<Code>) -> Rc<Code> {
    let i = node.0 as usize;
    if codes.len() <= i {
        codes.resize(i + 1, None);
    }
    codes[i] = Some(code.clone());
    code
}

/// Where a variable is, from the code being lowered.
#[derive(Clone, Copy)]
enum Loc {
    Slot(u32),
    Cell(u32),
    Capture(u32),
    Global(u32),
}

/// A `Block` or `Loop` that jumps in the code being lowered may reach,
/// with the jumps to patch once its end is known.
struct Target {
    node: NodeId,
    is_loop: bool,
    depth: u32,
    breaks: Vec<usize>,
    continues: Vec<usize>,
}

/// Lowering, with its caches for a run.
#[derive(Default)]
pub(super) struct Lowering {
    /// Functions' code, by node.
    functions: Vec<Option<Rc<Code>>>,
    /// Class initializers' code, by node.
    initializers: Vec<Option<Rc<Code>>>,
    /// Functions' and classes' layouts.
    layouts: HashMap<ScopeId, Rc<Layout>>,
    /// Each module's first global; its declarations follow in order.
    globals: HashMap<ScopeId, u32>,
    /// The code being lowered.
    b: Builder,
    /// Each error's message by its span, made the first time an
    /// expression is lowered ([`Lowering::error_at`]).
    errors: Option<HashMap<(u32, u32), Rc<str>>>,
    /// Constant regex literals' cache indices, by node.
    pub(super) cached: HashMap<NodeId, u32>,
}

/// Code being lowered.
struct Builder {
    layout: Option<Rc<Layout>>,
    function: Option<NodeId>,
    generator: Option<GeneratorKind>,
    ops: Vec<Op>,
    nodes: Vec<NodeId>,
    handlers: Vec<Handler>,
    landings: Vec<Landing>,
    /// The stack's depth, and the deepest so far.
    depth: u32,
    max: u32,
    targets: Vec<Target>,
    nesting: u32,
    /// The node ops come from.
    at: NodeId,
}

impl Default for Builder {
    fn default() -> Builder {
        Builder {
            layout: None,
            function: None,
            generator: None,
            ops: Vec::new(),
            nodes: Vec::new(),
            handlers: Vec::new(),
            landings: Vec::new(),
            depth: 0,
            max: 0,
            targets: Vec::new(),
            nesting: 0,
            at: NodeId(0),
        }
    }
}

impl Lowering {
    /// Lowering for modules whose globals start at these indices.
    pub(super) fn new(globals: HashMap<ScopeId, u32>) -> Lowering {
        Lowering {
            globals,
            ..Lowering::default()
        }
    }

    /// A `Fn`'s code: its parameters' defaults, then its body.
    pub(super) fn function(&mut self, s: &Settled, f: NodeId) -> Result<Rc<Code>, &'static str> {
        if let Some(code) = cached(&self.functions, f) {
            return Ok(code);
        }
        let home = s.scopes.of_node(f).ok_or("Function not settled")?;
        let layout = self.layout(s, home);
        let tree = &s.library.tree;
        let (params, _, body) = core::fn_parts(tree, f);
        let body = body.ok_or("Abstract method")?;
        self.begin(layout.clone(), Some(f), f);
        self.b.generator = generator_kind(s, f);
        for (i, &param) in params.iter().enumerate() {
            let slot = i as u32;
            self.b.at = param;
            match core::let_parts(tree, param).2 {
                // Computed here in the callee, after earlier parameters.
                Some(default) => {
                    let check = self.emit(Op::Default { slot, skip: 0 });
                    self.expr(s, default, true);
                    self.emit(Op::SetLocal(slot));
                    self.patch(check);
                }
                None => _ = self.emit(Op::Required(slot)),
            }
            if let Some(cell) = layout.param_cell(i) {
                self.emit(Op::Local(slot));
                self.emit(Op::SetCell(cell));
            }
        }
        if self.b.generator.is_some() {
            self.emit(Op::Const(Val::Void));
            self.emit(Op::Yield);
        }
        self.expr(s, body, true);
        self.emit(Op::Return);
        let code = Rc::new(self.build(params.len() as u32));
        Ok(cache(&mut self.functions, f, code))
    }

    /// A class's initializer, a supertype or a field's value, run with
    /// `this` (an object, or the class itself for static members).
    pub(super) fn class_code(
        &mut self,
        s: &Settled,
        class: NodeId,
        expr: NodeId,
    ) -> Result<Rc<Code>, &'static str> {
        if let Some(code) = cached(&self.initializers, expr) {
            return Ok(code);
        }
        let home = s.scopes.of_node(class).ok_or("Class not settled")?;
        let layout = self.layout(s, home);
        self.begin(layout.clone(), None, expr);
        if let Some(cell) = layout.param_cell(0) {
            self.emit(Op::Local(0));
            self.emit(Op::SetCell(cell));
        }
        self.expr(s, expr, true);
        self.emit(Op::Return);
        let code = Rc::new(self.build(1));
        Ok(cache(&mut self.initializers, expr, code))
    }

    /// A module's top-level statements, in the order they run.
    pub(super) fn module(
        &mut self,
        s: &Settled,
        scope: ScopeId,
        statements: &[NodeId],
    ) -> Rc<Code> {
        self.begin(
            Rc::new(Layout::new(s, scope, statements)),
            None,
            statements.first().copied().unwrap_or(NodeId(0)),
        );
        self.items(s, statements, None, false);
        self.emit(Op::Const(Val::Void));
        self.emit(Op::Return);
        Rc::new(self.build(0))
    }

    /// A REPL input's top-level statements, in the order they run,
    /// giving the last one's value with `keep`. It may `await` at its top
    /// level, as an `async` block's body may, its file's `root` standing
    /// for the function ([`crate::repl`]).
    pub(super) fn repl_input(
        &mut self,
        s: &Settled,
        scope: ScopeId,
        statements: &[NodeId],
        keep: bool,
        root: NodeId,
    ) -> Rc<Code> {
        self.begin(
            Rc::new(Layout::new(s, scope, statements)),
            Some(root),
            statements.first().copied().unwrap_or(root),
        );
        self.b.generator = Some(GeneratorKind::Safe);
        self.items(s, statements, None, keep);
        if !keep {
            self.emit(Op::Const(Val::Void));
        }
        self.emit(Op::Return);
        Rc::new(self.build(0))
    }

    /// Adds a module whose globals start at `first`, as a REPL session
    /// grows.
    pub(super) fn add_globals(&mut self, scope: ScopeId, first: u32) {
        self.globals.insert(scope, first);
    }

    /// Forgets the error messages found so far, as a session's library
    /// has reported more since.
    pub(super) fn forget_errors(&mut self) {
        self.errors = None;
    }

    /// An expression at a module's top level, such as running its tests.
    pub(super) fn expression(&mut self, s: &Settled, scope: ScopeId, expr: NodeId) -> Rc<Code> {
        self.begin(Rc::new(Layout::new(s, scope, &[expr])), None, expr);
        self.expr(s, expr, true);
        self.emit(Op::Return);
        Rc::new(self.build(0))
    }

    /// A function's or class's layout, which its code and the code making
    /// it share.
    pub(super) fn layout(&mut self, s: &Settled, home: ScopeId) -> Rc<Layout> {
        let node = s.scopes.get(home).node;
        self.layouts
            .entry(home)
            .or_insert_with(|| Rc::new(Layout::new(s, home, &[node])))
            .clone()
    }

    fn begin(&mut self, layout: Rc<Layout>, function: Option<NodeId>, at: NodeId) {
        self.b = Builder {
            layout: Some(layout),
            function,
            at,
            ..Builder::default()
        };
    }

    fn build(&mut self, params: u32) -> Code {
        let b = std::mem::take(&mut self.b);
        let layout = b.layout.unwrap();
        Code {
            function: b.function,
            generator: b.generator,
            ops: b.ops,
            nodes: b.nodes,
            params,
            slots: layout.slots,
            cells: layout.cells,
            stack: b.max,
            free: layout.free.clone(),
            classes: layout.classes.clone(),
            handlers: b.handlers,
            landings: b.landings,
        }
    }

    fn layout_here(&self) -> &Layout {
        self.b.layout.as_ref().unwrap()
    }

    /// Appends an op, tracking the stack's depth, and returns its index.
    fn emit(&mut self, op: Op) -> usize {
        let b = &mut self.b;
        match &op {
            Op::Const(_)
            | Op::Local(_)
            | Op::Cell(_)
            | Op::Capture(_)
            | Op::Global(_)
            | Op::Dup => b.depth += 1,
            Op::MakeFn { .. } | Op::MakeClass { .. } => b.depth += 1,
            Op::SetLocal(_) | Op::SetCell(_) | Op::SetCapture(_) | Op::SetGlobal(_) | Op::Pop => {
                b.depth -= 1
            }
            Op::JumpIfFalse(_) | Op::InitStatics | Op::SetProp(_) | Op::Return | Op::Yield => {
                b.depth -= 1
            }
            Op::Exit(Exit::Return(_)) => b.depth -= 1,
            Op::Call { args, .. } | Op::CallMethod { args, .. } => b.depth -= args,
            Op::Truncate(depth) => b.depth = *depth,
            _ => {}
        }
        b.max = b.max.max(b.depth);
        b.ops.push(op);
        b.nodes.push(b.at);
        b.ops.len() - 1
    }

    fn pc(&self) -> u32 {
        self.b.ops.len() as u32
    }

    /// Points a jump at the next op.
    fn patch(&mut self, jump: usize) {
        let to = self.pc();
        match &mut self.b.ops[jump] {
            Op::Jump(at)
            | Op::JumpIfFalse(at)
            | Op::Default { skip: at, .. }
            | Op::Cached { skip: at, .. } => *at = to,
            op => unreachable!("not a jump: {op:?}"),
        }
    }

    /// After code that doesn't go on (a jump or a failure), the depth that
    /// code after it would have had.
    fn dead(&mut self, base: u32, keep: bool) {
        self.b.depth = base + keep as u32;
        self.b.max = self.b.max.max(self.b.depth);
    }

    /// The message of an error reported at a value, a name, or a call
    /// (a member that isn't there, the wrong number of arguments), if
    /// there is one that isn't type-only.
    fn error_at(&mut self, s: &Settled, node: NodeId) -> Option<Rc<str>> {
        let lib = &s.library;
        if !matches!(
            lib.tree.kind(node),
            NodeKind::Call | NodeKind::RightName | NodeKind::Value
        ) {
            return None;
        }
        let errors = self.errors.get_or_insert_with(|| {
            // Not type-only ones: ill-typed code runs on as written, as
            // in Kotlin's interpreter and dynamically typed backends.
            (lib.diagnostics.iter())
                .filter(|d| d.level == crate::library::Level::Error && !d.message.type_only())
                .map(|d| ((d.span.start, d.span.end), d.message.text().into()))
                .collect()
        });
        if errors.is_empty() {
            return None;
        }
        let span = lib.tree.span(node);
        errors.get(&(span.start, span.end)).cloned()
    }

    fn fail(&mut self, message: impl Into<Rc<str>>) {
        self.emit(Op::Fail(Some(message.into())));
    }

    /// Lowers an expression, leaving its value on the stack if `keep`.
    fn expr(&mut self, s: &Settled, node: NodeId, keep: bool) {
        let base = self.b.depth;
        if self.b.nesting >= MAX_NESTING {
            self.emit(Op::Panic("nested too deeply".into()));
            return self.dead(base, keep);
        }
        self.b.nesting += 1;
        let outer = std::mem::replace(&mut self.b.at, node);
        match self.error_at(s, node) {
            // Code with an error fails there, as `bubble()` does, as each
            // backend's does (tmpl's `Error`), rather than running on
            // with a value of the wrong type or a member that isn't there.
            Some(message) => {
                self.emit(Op::Fail(Some(message)));
                self.dead(base, keep);
            }
            None if keep && s.library.constant_regexes.contains(&node) => {
                let next = self.cached.len() as u32;
                let index = *self.cached.entry(node).or_insert(next);
                let skip = self.emit(Op::Cached { index, skip: 0 });
                self.node(s, node, keep);
                self.emit(Op::Cache(index));
                self.patch(skip);
            }
            None => self.node(s, node, keep),
        }
        debug_assert_eq!(
            self.b.depth,
            base + keep as u32,
            "{:?}",
            s.library.tree.kind(node)
        );
        self.b.at = outer;
        self.b.nesting -= 1;
    }

    fn node(&mut self, s: &Settled, node: NodeId, keep: bool) {
        let tree = &s.library.tree;
        let children = tree.children(node);
        let base = self.b.depth;
        match tree.kind(node) {
            NodeKind::Value => {
                match s.library.value(node).and_then(literal) {
                    Some(value) if keep => _ = self.emit(Op::Const(value)),
                    Some(_) => {}
                    None => {
                        self.fail("Unsupported value");
                        self.dead(base, keep);
                    }
                }
                return;
            }
            NodeKind::RightName | NodeKind::BuiltinName | NodeKind::LeftName => self.read(s, node),
            NodeKind::Block => return self.block(s, node, keep),
            NodeKind::Let => {
                let (name, _, init) = core::let_parts(tree, node);
                let connected = tree.data(node) & MEMBER_CONNECTED != 0;
                match init {
                    Some(init) => self.store(s, name, false, |me| me.expr(s, init, true)),
                    None if connected => self.store(s, name, false, |me| {
                        _ = me.emit(Op::Const(Val::Connected(node)))
                    }),
                    None => self.store(s, name, false, |me| _ = me.emit(Op::Const(Val::Uninit))),
                }
                if keep {
                    self.emit(Op::Const(Val::Void));
                }
                return;
            }
            NodeKind::Assign => {
                let (target, value) = (children[0], children[1]);
                if tree.kind(target) != NodeKind::Call {
                    return self.store(s, target, keep, |me| me.expr(s, value, true));
                }
                match *tree.children(target) {
                    [_, object, name] => {
                        self.expr(s, object, true);
                        match s.library.symbol(name) {
                            Some(name) => {
                                self.expr(s, value, true);
                                self.emit(Op::SetProp(name));
                            }
                            None => self.fail("Malformed member access"),
                        }
                    }
                    _ => self.fail("Malformed member access"),
                }
                self.dead(base, true);
            }
            NodeKind::Class => {
                self.read(s, children[0]);
                self.emit(Op::InitStatics);
                if keep {
                    self.emit(Op::Const(Val::Void));
                }
                return;
            }
            NodeKind::If => {
                self.expr(s, children[0], true);
                let to_else = self.emit(Op::JumpIfFalse(0));
                self.expr(s, children[1], keep);
                let to_end = self.emit(Op::Jump(0));
                self.patch(to_else);
                self.b.depth = base;
                self.expr(s, children[2], keep);
                self.patch(to_end);
                return;
            }
            NodeKind::Loop => return self.looping(s, node, keep),
            NodeKind::Break | NodeKind::Continue => {
                self.jump(
                    NodeId(tree.data(node)),
                    tree.kind(node) == NodeKind::Continue,
                );
                return self.dead(base, keep);
            }
            NodeKind::Return => {
                self.expr(s, children[0], true);
                let target = NodeId(tree.data(node));
                if self.b.function == Some(target) {
                    self.emit(Op::Return);
                } else {
                    self.emit(Op::Exit(Exit::Return(target)));
                }
                return self.dead(base, keep);
            }
            NodeKind::Yield | NodeKind::Await => {
                // Only a generator's own body may pause.
                let is_yield = tree.kind(node) == NodeKind::Yield;
                if self.b.generator.is_none() || self.b.function != Some(NodeId(tree.data(node))) {
                    let what = if is_yield { "yield" } else { "await" };
                    self.fail(format!("{what} outside generator function body"));
                    return self.dead(base, keep);
                }
                self.expr(s, children[0], true);
                if is_yield {
                    // `next()` takes no argument, so `yield` itself is void.
                    self.emit(Op::Yield);
                    if keep {
                        self.emit(Op::Const(Val::Void));
                    }
                    return;
                }
                // `await` leaves the promise's value.
                self.emit(Op::Await);
            }
            NodeKind::Orelse => {
                let start = self.pc();
                self.expr(s, children[0], keep);
                let end = self.pc();
                let to_end = self.emit(Op::Jump(0));
                let to = self.pc();
                self.b.depth = base;
                self.expr(s, children[1], keep);
                self.patch(to_end);
                self.b.handlers.push(Handler {
                    start,
                    end,
                    to,
                    depth: base,
                });
                return;
            }
            NodeKind::Fail => {
                self.emit(Op::Fail(None));
                return self.dead(base, keep);
            }
            NodeKind::Fn => self.make_fn(s, node),
            NodeKind::Call => return self.call(s, node, keep),
            NodeKind::Error => {
                self.fail(s.library.error_text(node));
                return self.dead(base, keep);
            }
            kind => {
                self.fail(format!("Not settled: {kind:?}"));
                return self.dead(base, keep);
            }
        }
        // An expression that leaves a value.
        if !keep {
            self.emit(Op::Pop);
        }
    }

    /// A block: fresh cells for its captured locals, then its statements.
    fn block(&mut self, s: &Settled, node: NodeId, keep: bool) {
        let base = self.b.depth;
        let is_target = self.layout_here().targets.contains(&node);
        if is_target {
            self.b.targets.push(Target {
                node,
                is_loop: false,
                depth: base,
                breaks: Vec::new(),
                continues: Vec::new(),
            });
        }
        let start = self.pc();
        self.items(
            s,
            s.library.tree.children(node),
            s.scopes.of_node(node),
            keep,
        );
        if !is_target {
            return;
        }
        let target = self.b.targets.pop().unwrap();
        let end = self.pc();
        // A `break` leaves void.
        let to_end = keep.then(|| self.emit(Op::Jump(0)));
        let to = self.pc();
        self.b.depth = base;
        if keep {
            self.emit(Op::Const(Val::Void));
        }
        for jump in target.breaks {
            self.b.ops[jump] = Op::Jump(to);
        }
        if let Some(to_end) = to_end {
            self.patch(to_end);
        }
        self.b.landings.push(Landing {
            target: node,
            continue_: false,
            start,
            end,
            to,
            depth: base,
        });
    }

    /// A block's statements, after setting its functions and classes, so
    /// that they may use later ones.
    fn items(&mut self, s: &Settled, statements: &[NodeId], scope: Option<ScopeId>, keep: bool) {
        let tree = &s.library.tree;
        if let Some(scope) = scope {
            for &(_, symbol) in &s.scopes.get(scope).names {
                if let Some(&Place::Cell(cell)) = self.layout_here().places.get(&symbol) {
                    self.emit(Op::FreshCell(cell));
                }
            }
        }
        for &statement in statements {
            self.b.at = statement;
            match tree.kind(statement) {
                NodeKind::Class => self.store(s, tree.children(statement)[0], false, |me| {
                    me.make_class(s, statement)
                }),
                NodeKind::Let if tree.data(statement) & LET_VAR == 0 => {
                    let (name, _, init) = core::let_parts(tree, statement);
                    if let Some(init) = init.filter(|&i| tree.kind(i) == NodeKind::Fn) {
                        self.store(s, name, false, |me| me.make_fn(s, init));
                    }
                }
                _ => {}
            }
        }
        match statements.split_last() {
            Some((&last, rest)) => {
                for &statement in rest {
                    self.expr(s, statement, false);
                }
                self.expr(s, last, keep);
            }
            None if keep => _ = self.emit(Op::Const(Val::Void)),
            None => {}
        }
    }

    /// `Loop(body, next?)`: the body, then `next`, again until a `break`.
    fn looping(&mut self, s: &Settled, node: NodeId, keep: bool) {
        let tree = &s.library.tree;
        let children = tree.children(node);
        let base = self.b.depth;
        self.b.targets.push(Target {
            node,
            is_loop: true,
            depth: base,
            breaks: Vec::new(),
            continues: Vec::new(),
        });
        let start = self.pc();
        self.expr(s, children[0], false);
        let next = self.pc();
        if tree.data(node) & LOOP_NEXT != 0 {
            self.expr(s, children[1], false);
        }
        self.emit(Op::Jump(start));
        let end = self.pc();
        let target = self.b.targets.pop().unwrap();
        for jump in target.breaks {
            self.b.ops[jump] = Op::Jump(end);
        }
        for jump in target.continues {
            self.b.ops[jump] = Op::Jump(next);
        }
        if self.layout_here().targets.contains(&node) {
            let landing = |continue_, to| Landing {
                target: node,
                continue_,
                start,
                end,
                to,
                depth: base,
            };
            self.b
                .landings
                .extend([landing(false, end), landing(true, next)]);
        }
        if keep {
            self.emit(Op::Const(Val::Void));
        }
    }

    /// `break` or `continue`: a jump within this code, or the end of a
    /// `for … of` body's call.
    fn jump(&mut self, target: NodeId, is_continue: bool) {
        let found = self
            .b
            .targets
            .iter()
            .rposition(|t| t.node == target && (t.is_loop || !is_continue));
        if let Some(i) = found {
            let depth = self.b.targets[i].depth;
            if self.b.depth > depth {
                self.emit(Op::Truncate(depth));
            }
            let jump = self.emit(Op::Jump(0));
            let t = &mut self.b.targets[i];
            if is_continue {
                t.continues.push(jump)
            } else {
                t.breaks.push(jump)
            };
        } else if is_continue && self.b.function == Some(target) {
            // `continue` in a `for … of` body ends its call.
            self.emit(Op::Const(Val::Void));
            self.emit(Op::Return);
        } else if is_continue {
            self.emit(Op::Exit(Exit::Continue(target)));
        } else {
            self.emit(Op::Exit(Exit::Break(target)));
        }
    }

    /// A call: `obj.name`, `obj.name(args)` or `f(args)`.
    fn call(&mut self, s: &Settled, node: NodeId, keep: bool) {
        let tree = &s.library.tree;
        let children = tree.children(node);
        let base = self.b.depth;
        let Some(&callee) = children.first() else {
            self.fail("Malformed call");
            return self.dead(base, keep);
        };
        if is_dot(s, callee) {
            // `obj.name` read as a property.
            if let Some(name) = self.member(s, node) {
                self.emit(Op::GetProp(name));
                if !keep {
                    self.emit(Op::Pop);
                }
                return;
            }
            return self.dead(base, keep);
        }
        let method = tree.kind(callee) == NodeKind::Call
            && tree.children(callee).first().is_some_and(|&c| is_dot(s, c));
        // A method with an error reported at it, as `c.secret()` outside
        // `c`'s class, fails before its object or arguments run, as tmpl's
        // `Error` for the whole call does.
        if let Some(message) = method.then(|| self.error_at(s, callee)).flatten() {
            self.fail(message);
            return self.dead(base, keep);
        }
        let method = if method {
            match self.member(s, callee) {
                Some(name) => Some(name),
                None => return self.dead(base, keep),
            }
        } else {
            self.expr(s, callee, true);
            None
        };
        // Each value passed, or `None` for null.
        let mut values = Vec::with_capacity(children.len() - 1);
        let mut rest = &children[1..];
        while let Some((&arg, more)) = rest.split_first() {
            rest = more;
            match s.library.symbol(arg) {
                // The interpreter ignores type arguments.
                Some(key) if s.library.names.get(key) == "typeArg" => {
                    rest = rest.get(1..).unwrap_or_default()
                }
                // Object literals' names are placed when settling.
                Some(_) => {
                    self.fail("Malformed call");
                    return self.dead(base, keep);
                }
                None => values.push(Some(arg)),
            }
        }
        // A trailing block goes where typing placed it, with null for
        // skipped parameters before it, which take their defaults.
        if let Some(&at) = s.typing.trailing_blocks.get(&node) {
            let block = values.pop().flatten();
            if values.len() < at {
                values.resize(at, None);
            }
            values.insert(at, block);
        }
        for &value in &values {
            match value {
                Some(arg) => self.expr(s, arg, true),
                None => _ = self.emit(Op::Const(Val::Null)),
            }
        }
        let args = values.len() as u32;
        self.b.at = node;
        match method {
            Some(name) => self.emit(Op::CallMethod { name, args }),
            None => self.emit(Op::Call { args }),
        };
        if !keep {
            self.emit(Op::Pop);
        }
    }

    /// Pushes the object of `obj.name` and gives the name, or fails.
    fn member(&mut self, s: &Settled, dot: NodeId) -> Option<NameId> {
        match *s.library.tree.children(dot) {
            [_, object, name] => {
                self.expr(s, object, true);
                let name = s.library.symbol(name);
                if name.is_none() {
                    self.fail("Malformed member access");
                }
                name
            }
            _ => {
                self.fail("Malformed member access");
                None
            }
        }
    }

    /// Where a variable is, from here: a global, a local of this code, or
    /// one it captured.
    fn locate(&self, s: &Settled, symbol: SymbolId) -> Option<Loc> {
        let sym = s.scopes.symbol(symbol);
        if s.scopes.get(sym.owner).kind == ScopeKind::Module {
            return self
                .globals
                .get(&sym.owner)
                .map(|first| Loc::Global(first + sym.slot));
        }
        let layout = self.layout_here();
        match layout.places.get(&symbol) {
            Some(&Place::Slot(slot)) => Some(Loc::Slot(slot)),
            Some(&Place::Cell(cell)) => Some(Loc::Cell(cell)),
            None => layout
                .free
                .iter()
                .position(|&f| f == symbol)
                .map(|i| Loc::Capture(i as u32)),
        }
    }

    fn not_in_scope(&mut self, s: &Settled, symbol: SymbolId) {
        let name = s.library.names.get(s.scopes.symbol(symbol).name);
        self.fail(format!("{name} is not in scope here"));
    }

    /// Pushes a name's value.
    fn read(&mut self, s: &Settled, name: NodeId) {
        let base = self.b.depth;
        let Some(symbol) = s.scopes.resolved(name) else {
            self.fail("Unresolved name");
            return self.dead(base, true);
        };
        let sym = *s.scopes.symbol(symbol);
        match sym.kind {
            SymbolKind::Builtin(b) => match builtin_value(b) {
                Some(value) => _ = self.emit(Op::Const(value)),
                None => {
                    self.fail("Macro used as a value");
                    self.dead(base, true);
                }
            },
            SymbolKind::TypeParam => _ = self.emit(Op::Const(Val::Type)),
            SymbolKind::Member if sym.input => {
                self.this(s, sym.owner);
                self.emit(Op::GetInput(sym.slot));
            }
            SymbolKind::Member => {
                self.this(s, sym.owner);
                self.emit(Op::GetProp(sym.name));
            }
            _ => match self.locate(s, symbol) {
                Some(at) => self.load(at),
                None => {
                    self.not_in_scope(s, symbol);
                    self.dead(base, true);
                }
            },
        }
    }

    fn load(&mut self, at: Loc) {
        self.emit(match at {
            Loc::Slot(i) => Op::Local(i),
            Loc::Cell(i) => Op::Cell(i),
            Loc::Capture(i) => Op::Capture(i),
            Loc::Global(i) => Op::Global(i),
        });
    }

    /// Pushes `this` for a class's scope.
    fn this(&mut self, s: &Settled, class_scope: ScopeId) {
        let base = self.b.depth;
        let this = s
            .scopes
            .get(class_scope)
            .names
            .first()
            .map(|&(_, this)| this);
        match this.and_then(|this| self.locate(s, this)) {
            Some(at) => self.load(at),
            None => {
                self.fail("No this here");
                self.dead(base, true);
            }
        }
    }

    /// Stores the value that `value` pushes in a name, leaving it on the
    /// stack if `keep`. A member's name stores into `this`.
    fn store(&mut self, s: &Settled, name: NodeId, keep: bool, value: impl FnOnce(&mut Self)) {
        let base = self.b.depth;
        let Some(symbol) = s.scopes.resolved(name) else {
            value(self);
            self.fail("Unresolved name");
            return self.dead(base, keep);
        };
        let sym = *s.scopes.symbol(symbol);
        if sym.kind == SymbolKind::Member {
            self.this(s, sym.owner);
            value(self);
            self.emit(Op::SetProp(sym.name));
            if !keep {
                self.emit(Op::Pop);
            }
            return;
        }
        let at = self.locate(s, symbol);
        value(self);
        if keep {
            self.emit(Op::Dup);
        }
        match at {
            Some(at) => {
                _ = self.emit(match at {
                    Loc::Slot(i) => Op::SetLocal(i),
                    Loc::Cell(i) => Op::SetCell(i),
                    Loc::Capture(i) => Op::SetCapture(i),
                    Loc::Global(i) => Op::SetGlobal(i),
                })
            }
            None => {
                self.not_in_scope(s, symbol);
                self.dead(base, keep);
            }
        }
    }

    /// Where the frame making a function or class finds what it captures,
    /// or none if something is out of reach.
    fn sources(&self, free: &[SymbolId]) -> Option<Box<[Source]>> {
        let layout = self.layout_here();
        let source = |symbol: &SymbolId| match layout.places.get(symbol) {
            Some(&Place::Cell(cell)) => Some(Source::Cell(cell)),
            Some(&Place::Slot(_)) => None,
            None => layout
                .free
                .iter()
                .position(|f| f == symbol)
                .map(|i| Source::Capture(i as u32)),
        };
        free.iter().map(source).collect()
    }

    /// Pushes a closure. A function never settled fails when called.
    fn make_fn(&mut self, s: &Settled, node: NodeId) {
        let free = match s.scopes.of_node(node) {
            Some(home) => self.layout(s, home).free.clone(),
            None => Rc::from([]),
        };
        match self.sources(&free) {
            Some(sources) => _ = self.emit(Op::MakeFn { node, sources }),
            None => self.unreachable_capture(s, &free),
        }
    }

    /// Pushes a class.
    fn make_class(&mut self, s: &Settled, node: NodeId) {
        let base = self.b.depth;
        let Some(home) = s.scopes.of_node(node) else {
            self.fail("Class not settled");
            return self.dead(base, true);
        };
        let layout = self.layout(s, home);
        match self.sources(&layout.free) {
            Some(sources) => {
                _ = self.emit(Op::MakeClass {
                    node,
                    layout,
                    sources,
                })
            }
            None => self.unreachable_capture(s, &layout.free),
        }
    }

    /// Fails for a captured variable that isn't reachable from here, which
    /// settling shouldn't allow.
    fn unreachable_capture(&mut self, s: &Settled, free: &[SymbolId]) {
        let base = self.b.depth;
        match free.iter().find(|&&f| self.sources(&[f]).is_none()) {
            Some(&symbol) => self.not_in_scope(s, symbol),
            None => self.fail("Captured variable out of reach"),
        }
        self.dead(base, true);
    }
}
