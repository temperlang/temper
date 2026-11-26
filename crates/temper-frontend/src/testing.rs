//! Tests: `test("name") { … }` declares a test case, and `assert(c) {
//! "why" }` in one checks something, as Kotlin Temper's `TestMacro` and
//! `AssertMacro` do, over std's `testing` module.
//!
//! - `test(name, block)` is a top-level `let t#test = new Pair(name, fn
//!   (test#: Test): Void throws Error { block })`, as `processTestCases`
//!   takes them. A block with a parameter, `{ t => … }`, keeps it, typed
//!   `Test` if untyped, and its result type, `Void throws Error` if it
//!   has none: a test may throw any error, which fails it. No source
//!   but std's may write `throws Error`.
//!   The first test in a module also adds `let { Test, runTestCases as
//!   r# } = import("std/testing")` to its file, as Kotlin does, so `Test`
//!   is in scope there.
//! - `assert(c, message)` is `t.assert(c, message)`, where `t` is the
//!   nearest enclosing function's parameter named `test` or typed `Test`.
//!   Without a message, it's `"expected c"`, from the source text, or for
//!   `assert(a == b)`, `do { let a# = a; let b# = b; t.assert(a# == b#, fn
//!   { "expected a == (${b#}) not (${a#})" }) }`, or the plain message
//!   where `a` or `b` has no `toString()`, once typing knows.
//! - Each module with tests gets an expression running them,
//!   `r#([t#test, …])`, a JUnit XML report ([`crate::settle::SettledModule::tests`]),
//!   which [`crate::interp::run_tests`] evaluates after the program runs.

use crate::builtins::{Builtin, FAILURE_ROOT, Function};
use crate::core::{self, FN_OUT_TYPE, LET_INIT, LET_TYPE};
use crate::library::{Message, ModuleId};
use crate::scope::{ScopeId, ScopeKind, SymbolId};
use crate::settle::Settle;
use temper_syntax::ast::Value;
use temper_syntax::tree::{NodeId, NodeKind, Span};

impl Settle {
    /// `test(name) { … }`, at a module's top level.
    pub(crate) fn expand_test(&mut self, call: NodeId, scope: ScopeId, args: &[NodeId]) -> bool {
        let [name, body] = *args else { return false };
        if self.lib.tree.kind(body) != NodeKind::Fun {
            return false;
        }
        let module = self
            .modules
            .iter()
            .position(|&s| s == scope)
            .map(|m| ModuleId(m as u32));
        let root = self
            .lib
            .files
            .iter()
            .map(|f| f.root)
            .find(|&r| self.lib.tree.children(r).contains(&call));
        let (Some(module), Some(root)) = (module, root) else {
            self.error(
                call,
                Message::Unsupported("tests outside the top level".into()),
            );
            return true;
        };
        if self.repl_scopes.contains(&scope) && !self.repl_tests {
            self.error(call, Message::TestsInRepl);
            return true;
        }
        let span = self.lib.tree.span(call);
        let f = match self.block_of(body) {
            Some(block) => {
                let param = self.lib.names.temporary("test");
                let param = self.lib.add_name(NodeKind::LeftName, param, span, call);
                let ty = self.test_type(span, call);
                let param = self.make_let(param, Some(ty), None, 0, span, call);
                let result = self.test_result(span, call);
                let data = 1 | FN_OUT_TYPE;
                self.lib
                    .add(NodeKind::Fn, span, data, &[param, result, block], call)
            }
            None => {
                self.lower_fun(body);
                if self.lib.tree.kind(body) != NodeKind::Fn {
                    return true;
                }
                let params = core::fn_parts(&self.lib.tree, body).0;
                if let Some(&param) = params.first() {
                    let (left, ty, init) = core::let_parts(&self.lib.tree, param);
                    if ty.is_none() {
                        let ty = self.test_type(span, call);
                        let flags = self.lib.tree.data(param) & !(LET_TYPE | LET_INIT);
                        let (children, data) = Self::let_layout(left, Some(ty), init, flags);
                        self.rewrite(param, NodeKind::Let, data, &children);
                    }
                }
                if core::fn_parts(&self.lib.tree, body).1.is_none() {
                    let result = self.test_result(span, call);
                    let mut children = self.lib.tree.children(body).to_vec();
                    let at = core::type_params(&self.lib.tree, body).len() + params.len();
                    children.insert(at, result);
                    let data = self.lib.tree.data(body) | FN_OUT_TYPE;
                    self.rewrite(body, NodeKind::Fn, data, &children);
                }
                body
            }
        };
        let new = self.scopes.hidden(Builtin::Function(Function::New));
        let new = self.builtin_name(new, span, call);
        let pair = Builtin::named("Pair").unwrap();
        let pair = self.builtin_name(self.scopes.hidden(pair), span, call);
        let case = self
            .lib
            .add(NodeKind::Call, span, 0, &[new, pair, name, f], call);
        let left = self.lib.names.temporary("test");
        let left = self.lib.add_name(NodeKind::LeftName, left, span, call);
        self.rewrite(call, NodeKind::Let, LET_INIT, &[left, case]);
        self.tests.push((module, left));
        if !self.test_runners.iter().any(|&(m, _)| m == module) {
            // In a REPL input, whose top-level `let`s are visible from
            // their end, the import is visible all through it.
            let span = match self.repl_scopes.contains(&scope) {
                true => {
                    let at = self.lib.tree.span(root).start;
                    Span { start: at, end: at }
                }
                false => span,
            };
            let runner = self.import_testing(root, span, call);
            self.test_runners.push((module, runner));
        }
        true
    }

    /// `Void throws Error`, a test function's result type, its `Error`
    /// kept in [`Settle::root_throws`], as only std may write one.
    fn test_result(&mut self, span: Span, like: NodeId) -> NodeId {
        let mut name = |b: Builtin| {
            let symbol = self.scopes.hidden(b);
            self.builtin_name(symbol, span, like)
        };
        let throws = name(Builtin::Function(Function::Throws));
        let void = name(Builtin::named("Void").unwrap());
        let error = name(Builtin::named(FAILURE_ROOT).unwrap());
        self.root_throws.insert(error);
        (self.lib).add(NodeKind::Call, span, 0, &[throws, void, error], like)
    }

    fn test_type(&mut self, span: Span, like: NodeId) -> NodeId {
        let name = self.lib.names.intern("Test");
        self.lib.add_name(NodeKind::RightName, name, span, like)
    }

    /// Adds `let { Test, runTestCases as r# } = import("std/testing")` to
    /// the end of a file's block, returning `r#`'s name node.
    fn import_testing(&mut self, root: NodeId, span: Span, like: NodeId) -> NodeId {
        let lib = &mut self.lib;
        let mut name = |text: &str, kind| {
            let name = lib.names.intern(text);
            lib.add_name(kind, name, span, like)
        };
        let curlies = name("{}", NodeKind::BuiltinName);
        let test = name("Test", NodeKind::LeftName);
        let run = name("runTestCases", NodeKind::LeftName);
        let import = name("import", NodeKind::RightName);
        let runner = lib.names.temporary("runTestCases");
        let runner = lib.add_name(NodeKind::LeftName, runner, span, like);
        let key = |lib: &mut crate::library::Library, text: &str| {
            let symbol = Value::Symbol(lib.names.intern(text));
            lib.add_value(symbol, span, like)
        };
        let as_key = key(lib, "as");
        let init_key = key(lib, "init");
        let targets = lib.add(
            NodeKind::Call,
            span,
            0,
            &[curlies, test, run, as_key, runner],
            like,
        );
        let path = lib.add_value(Value::String("std/testing".into()), span, like);
        let init = lib.add(NodeKind::Call, span, 0, &[import, path], like);
        let decl = lib.add(NodeKind::Decl, span, 0, &[targets, init_key, init], like);
        let mut children = self.lib.tree.children(root).to_vec();
        children.push(decl);
        self.set_children(root, &children);
        runner
    }

    /// `assert(c)` or `assert(c, message)`, in a test function.
    pub(crate) fn expand_assert(&mut self, call: NodeId, scope: ScopeId, args: &[NodeId]) -> bool {
        let (condition, message) = match *args {
            [c] => (c, None),
            [c, m] => (c, Some(m)),
            _ => return false,
        };
        let Some(test) = self.test_param(scope) else {
            self.error(call, Message::InvalidBlockContent);
            return true;
        };
        let span = self.lib.tree.span(call);
        let dot = self.lib.names.intern(".");
        let dot = self.lib.add_name(NodeKind::BuiltinName, dot, span, call);
        let object = self.builtin_name(test, span, call);
        let key = Value::Symbol(self.lib.names.intern("assert"));
        let key = self.lib.add_value(key, span, call);
        let method = self
            .lib
            .add(NodeKind::Call, span, 0, &[dot, object, key], call);
        if let Some(message) = message {
            self.rewrite(call, NodeKind::Call, 0, &[method, condition, message]);
            return true;
        }
        let text = |s: &Self, node: NodeId| s.lib.source_text(s.lib.tree.span(node)).to_string();
        let cat = self.scopes.hidden(Builtin::Function(Function::Cat));
        let cat = self.builtin_name(cat, span, call);
        let Some([actual, expected]) = self.equality(condition) else {
            let text = Value::String(format!("expected {}", text(self, condition)));
            let text = self.lib.add_value(text, span, call);
            let message = self.constant_fn(text, span, call);
            self.rewrite(call, NodeKind::Call, 0, &[method, condition, message]);
            return true;
        };
        // Kotlin's message names the left side and shows both values;
        // where one can't be shown, it's the plain one instead
        // ([`Settle::settle_assert_messages`]).
        let plain = Value::String(format!("expected {}", text(self, condition)));
        let plain = self.lib.add_value(plain, span, call);
        let before = Value::String(format!("expected {} == (", text(self, actual)));
        let mut statements = Vec::new();
        let mut temporary = |s: &mut Self, value: NodeId, hint: &str| {
            let t = s.lib.names.temporary(hint);
            let left = s.lib.add_name(NodeKind::LeftName, t, span, call);
            statements.push(s.make_let(left, None, Some(value), 0, span, call));
            move |s: &mut Self| s.lib.add_name(NodeKind::RightName, t, span, call)
        };
        let actual = temporary(self, actual, "actual");
        // `null` expected, as `assert(x == null)`, is itself, with no
        // temporary that has no type of its own, and shows as `null`.
        let null = self.lib.value(expected) == Some(&Value::Null)
            || (self.lib.tree.kind(expected) == NodeKind::RightName
                && (self.lib.name(expected)).is_some_and(|n| self.lib.names.get(n) == "null"));
        let expected = (!null).then(|| temporary(self, expected, "expected"));
        let expected_value = |s: &mut Self, check: bool| match &expected {
            Some(read) => read(s),
            None if check => s.lib.add_value(Value::Null, span, call),
            None => s.lib.add_value(Value::String("null".into()), span, call),
        };
        let eq = self.scopes.hidden(Builtin::Function(Function::Eq));
        let eq = self.builtin_name(eq, span, call);
        let (a, e) = (actual(self), expected_value(self, true));
        let check = self.lib.add(NodeKind::Call, span, 0, &[eq, a, e], call);
        let shown = expected_value(self, false);
        let mut parts = vec![cat, self.lib.add_value(before, span, call), shown];
        parts.push(
            self.lib
                .add_value(Value::String(") not (".into()), span, call),
        );
        parts.push(actual(self));
        parts.push(self.lib.add_value(Value::String(")".into()), span, call));
        let text = self.lib.add(NodeKind::Call, span, 0, &parts, call);
        self.assert_messages.push((text, plain));
        let message = self.constant_fn(text, span, call);
        statements.push(
            self.lib
                .add(NodeKind::Call, span, 0, &[method, check, message], call),
        );
        self.rewrite(call, NodeKind::Block, 0, &statements);
        true
    }

    /// Once typing is done, each `assert(a == b)` message made this round
    /// that shows a value without a `toString()`, as a class's without
    /// one, or a list, becomes the plain one, `"expected a == b"`, so it
    /// translates and runs, where Kotlin Temper reports "No member
    /// toString" at the `assert`.
    pub(crate) fn settle_assert_messages(&mut self) {
        for (text, plain) in std::mem::take(&mut self.assert_messages) {
            let parts = self.lib.tree.children(text)[1..].to_vec();
            let hidden = parts.into_iter().any(|part| {
                self.lib.tree.kind(part) != NodeKind::Value
                    && (self.typing.expr(part)).is_some_and(|t| !self.has_to_string(t))
            });
            if hidden {
                let data = self.lib.tree.data(plain);
                self.rewrite(text, NodeKind::Value, data, &[]);
            }
        }
    }

    /// `fn { value }`.
    fn constant_fn(&mut self, value: NodeId, span: Span, like: NodeId) -> NodeId {
        let block = self.lib.add(NodeKind::Block, span, 0, &[value], like);
        self.lib.add(NodeKind::Fn, span, 0, &[block], like)
    }

    /// `a == b`'s sides, before it's expanded.
    fn equality(&self, node: NodeId) -> Option<[NodeId; 2]> {
        let tree = &self.lib.tree;
        let (op, a, b) = match *tree.children(node) {
            [op, a, b] => (op, a, b),
            [f, op, a, b]
                if matches!(
                    self.lib.value(f),
                    Some(Value::Builtin(
                        temper_syntax::ast::Builtin::DesugarOperation
                    ))
                ) =>
            {
                (op, a, b)
            }
            _ => return None,
        };
        let is_eq = tree.kind(node) == NodeKind::Call && tree.kind(op) == NodeKind::RightName;
        (is_eq
            && self
                .lib
                .name(op)
                .is_some_and(|n| self.lib.names.get(n) == "=="))
        .then_some([a, b])
    }

    /// The nearest enclosing function's parameter named `test` (a
    /// test's own, if unnamed, has that hint) or typed `Test`.
    fn test_param(&self, scope: ScopeId) -> Option<SymbolId> {
        let tree = &self.lib.tree;
        let mut at = Some(scope);
        while let Some(s) = at {
            let scope = self.scopes.get(s);
            at = scope.parent;
            if !matches!(scope.kind, ScopeKind::Fn | ScopeKind::LoopBody) {
                continue;
            }
            for param in core::fn_parts(tree, scope.node).0 {
                let (name, ty, _) = core::let_parts(tree, param);
                let named = |n: NodeId| self.lib.name(n).map(|n| self.lib.names.get(n));
                let typed = ty.is_some_and(|t| {
                    tree.kind(t) == NodeKind::RightName && named(t) == Some("Test")
                });
                if named(name) == Some("test") || typed {
                    return self.scopes.resolved(name);
                }
            }
        }
        None
    }

    /// A module's tests' `let`s, in source order, of those declared
    /// from the `from`th test on (as a REPL round's own).
    pub(crate) fn test_cases(&self, module: ModuleId, from: usize) -> Vec<NodeId> {
        (self.tests[from..].iter())
            .filter(|&&(m, _)| m == module)
            .filter_map(|&(_, t)| self.scopes.symbol(self.scopes.resolved(t)?).decl)
            .collect()
    }

    /// For a module with tests, `r#([t#test, …])`, which runs them: of
    /// those declared from the `from`th test on.
    pub(crate) fn test_run(&mut self, module: ModuleId, from: usize) -> Option<NodeId> {
        let &(_, runner) = self.test_runners.iter().find(|&&(m, _)| m == module)?;
        let runner = self.scopes.resolved(runner)?;
        let tests: Vec<NodeId> = self.tests[from..]
            .iter()
            .filter(|&&(m, _)| m == module)
            .map(|&(_, t)| t)
            .collect();
        let span = self.lib.tree.span(*tests.first()?);
        let list = self.scopes.hidden(Builtin::Function(Function::List));
        let mut items = vec![self.builtin_name(list, span, tests[0])];
        for test in tests {
            let symbol = self.scopes.resolved(test)?;
            items.push(self.builtin_name(symbol, span, test));
        }
        let list = self.lib.add(NodeKind::Call, span, 0, &items, items[0]);
        let runner = self.builtin_name(runner, span, list);
        Some(self.lib.add(NodeKind::Call, span, 0, &[runner, list], list))
    }
}
