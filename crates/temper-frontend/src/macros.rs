//! The builtin macros: each rewrites its own call node into core constructs
//! ([`crate::core`]), needing only syntax, or into an error node. What they
//! produce is visited afterwards, so nothing under a macro call is resolved
//! before it expands.
//!
//! Call joins arrive rewritten for a library (`temper_syntax::module`):
//! `if (a) { b } else { c }` is `if(a, fn { b }, \else, fn (f#0) { f#0(fn
//! { c }) })`. Rather than calling the delegate, as Kotlin does, `if` reads
//! the arguments it passes on.

use crate::builtins::{Builtin, Function, Macro};
use crate::core::{
    FN_LOOP_BODY, FN_NO_BODY, FN_OUT_TYPE, FN_SUPERS_SHIFT, LET_INIT, LET_TYPE, LET_VAR, LOOP_EACH,
    LOOP_NEXT, MEMBER_CONNECTED, ROLE_TYPE_PARAM, TYPE_PARAM_VARIANT, TYPE_PARAMS_SHIFT,
};
use crate::library::{Level, Message};
use crate::scope::{Lookup, ScopeId, ScopeKind, SymbolKind};
use crate::settle::Settle;
use temper_syntax::ast::{Builtin as SyntaxBuiltin, Value};
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind, Span};

/// How many `let` names for a string's tag to follow.
const TAG_DEPTH: usize = 8;

/// A destructuring target: its source name, the name node it's declared
/// as, and its type, if any.
pub(crate) type Target = (NameId, NodeId, Option<NodeId>);

/// A function's parts after its name, from [`Settle::signature`].
pub(crate) struct Signature {
    /// Each a `Let`.
    pub type_params: Vec<NodeId>,
    pub params: Vec<NodeId>,
    pub out_type: Option<NodeId>,
    /// From `extends`, as `GeneratorFn`.
    pub supers: Vec<NodeId>,
    pub body: Option<NodeId>,
}

impl Settle {
    /// Expands a call to a builtin macro.
    pub(crate) fn expand(&mut self, call: NodeId, scope: ScopeId, m: Macro) {
        let args = self.lib.tree.children(call)[1..].to_vec();
        let ok = match m {
            Macro::Let => self.expand_let(call, &args),
            Macro::Decorate => self.expand_decorate(call, &args),
            Macro::Fn => self.expand_fn(call, &args),
            Macro::If => match self.if_parts(&args) {
                Some(parts) => {
                    self.rewrite(call, NodeKind::If, 0, &parts);
                    true
                }
                None => false,
            },
            Macro::While => self.expand_while(call, &args),
            Macro::For => self.expand_for(call, &args),
            Macro::Do => match *args {
                [body] => self
                    .block_of(body)
                    .map(|b| self.become_node(call, b))
                    .is_some(),
                [body, key, check] if self.symbol_text(key) == Some("while") => {
                    self.expand_do_while(call, body, check)
                }
                _ => false,
            },
            Macro::Return => self.expand_return(call, scope, &args),
            Macro::Yield => self.expand_pause(call, scope, &args, NodeKind::Yield),
            Macro::Await => self.expand_pause(call, scope, &args, NodeKind::Await),
            Macro::Break => self.expand_jump(call, scope, &args, true),
            Macro::Continue => self.expand_jump(call, scope, &args, false),
            Macro::Bubble => {
                self.rewrite(call, NodeKind::Fail, 0, &[]);
                true
            }
            Macro::Assign => match *args {
                [target, value] if self.lib.tree.kind(target) == NodeKind::RightName => {
                    self.rewrite(call, NodeKind::Assign, 0, &[target, value]);
                    true
                }
                [target, value] if self.is_dot_call(target) => {
                    self.rewrite(call, NodeKind::Assign, 0, &[target, value]);
                    true
                }
                [target, value] if self.is_index_call(target) => {
                    // `ls[i] = v` is `ls.set(i, v)`.
                    let [index, ls, i] = *self.lib.tree.children(target) else {
                        unreachable!()
                    };
                    let span = self.lib.tree.span(call);
                    let dot = self.lib.names.intern(".");
                    let dot = self.lib.add_name(NodeKind::BuiltinName, dot, span, index);
                    let key = Value::Symbol(self.lib.names.intern("set"));
                    let key = self.lib.add_value(key, span, index);
                    let member = self
                        .lib
                        .add(NodeKind::Call, span, 0, &[dot, ls, key], target);
                    self.rewrite(call, NodeKind::Call, 0, &[member, i, value]);
                    true
                }
                [_, _] => {
                    // As `var { a } = x`, which isn't destructuring (that
                    // takes `let`), but `var` called with a block.
                    self.error(call, Message::MalformedAssignment);
                    true
                }
                _ => false,
            },
            Macro::And | Macro::Or => match *args {
                [a, b] => {
                    let span = self.lib.tree.span(call);
                    let constant = self.lib.add_value(Value::Bool(m == Macro::Or), span, call);
                    let parts = if m == Macro::And {
                        [a, b, constant]
                    } else {
                        [a, constant, b]
                    };
                    self.rewrite(call, NodeKind::If, 0, &parts);
                    true
                }
                _ => false,
            },
            Macro::Class | Macro::Interface => self.expand_class(call, &args, m),
            Macro::When => self.expand_when(call, &args),
            Macro::Coalesce => match *args {
                [a, b] => {
                    let span = self.lib.tree.span(call);
                    // A member named bare, a getter's call, is held, as
                    // checking it for null doesn't narrow the next read.
                    let (mut statements, name) = if self.is_bare_member(a, scope) {
                        self.held(a, "subject", call)
                    } else {
                        self.subject(a, "subject", call)
                    };
                    let null_check = self.null_check(name, span, call);
                    let reread = self.lib.add_name(NodeKind::RightName, name, span, a);
                    statements.push(self.lib.add(
                        NodeKind::If,
                        span,
                        0,
                        &[null_check, b, reread],
                        call,
                    ));
                    self.rewrite(call, NodeKind::Block, 0, &statements);
                    true
                }
                _ => false,
            },
            // Expanded by `expand_chain_null`, as its callee isn't a name.
            Macro::ChainNull => false,
            // `let { … } = import(…)` is `Settle::import`'s; this is a
            // bare `import("…")` statement.
            Macro::Import => match *args {
                [path] => self.bare_import(call, path),
                _ => false,
            },
            Macro::Test => self.expand_test(call, scope, &args),
            Macro::Assert => self.expand_assert(call, scope, &args),
            Macro::Orelse => match *args {
                [a, b] => {
                    self.rewrite(call, NodeKind::Orelse, 0, &[a, b]);
                    true
                }
                _ => false,
            },
        };
        if !ok && self.lib.tree.kind(call) == NodeKind::Call {
            self.error(
                call,
                Message::MalformedSpecial(Builtin::Macro(m).name().to_string()),
            );
        }
    }

    /// Expands a call to a builtin the grammar step refers to directly.
    pub(crate) fn expand_syntax(&mut self, call: NodeId, scope: ScopeId, builtin: SyntaxBuiltin) {
        let children = self.lib.tree.children(call).to_vec();
        let span = self.lib.tree.span(call);
        match (builtin, &children[1..]) {
            (SyntaxBuiltin::DesugarOperation, [op, operands @ ..]) => {
                self.desugar_operation(call, scope, *op, operands)
            }
            (SyntaxBuiltin::StringExpr, [tag, tagged, parts @ ..]) => {
                let plain = self.lib.value(*tag) == Some(&Value::Null)
                    && self.lib.value(*tagged) == Some(&Value::Bool(false))
                    && parts.iter().all(|&p| self.lib.symbol(p).is_none());
                if plain {
                    let cat = self.scopes.hidden(Builtin::Function(Function::Cat));
                    let callee = self.builtin_name(cat, span, call);
                    let mut new = vec![callee];
                    new.extend_from_slice(parts);
                    self.rewrite(call, NodeKind::Call, 0, &new);
                } else {
                    self.string_expr(call, *tag, *tagged, parts);
                }
            }
            // Type arguments, `T<A>`, and list literals, `[a, b]`.
            (SyntaxBuiltin::Angle, [_, ..]) | (SyntaxBuiltin::List, _) => {
                let function = if builtin == SyntaxBuiltin::Angle {
                    Function::Angle
                } else {
                    Function::List
                };
                let hidden = self.scopes.hidden(Builtin::Function(function));
                let callee = self.builtin_name(hidden, span, call);
                let mut new = vec![callee];
                new.extend_from_slice(&children[1..]);
                self.rewrite(call, NodeKind::Call, 0, &new);
            }
            (SyntaxBuiltin::EmbeddedComment, _) => {
                let void = self.lib.add_value(Value::Void, span, call);
                self.become_node(call, void);
            }
            (SyntaxBuiltin::DocComment, [target, ..]) => self.become_node(call, *target),
            _ => self.error(
                call,
                Message::Unsupported(format!("builtin {}", builtin.name())),
            ),
        }
    }

    /// A string that isn't plain: tagged, `tag"a${b}c"`, or with statements,
    /// as `"""` lines starting with `:` make, `stringExpr(tag, isTagged,
    /// \funString, fn { body })`. As Kotlin Temper's `StringExprMacro`
    /// does, a tag that names a class is an accumulator type, and a string
    /// with it, or with statements and no tag, appends to an accumulator
    /// ([`Settle::accumulate`]); a function tag gets lists of the parts
    /// ([`Settle::tagged_string`]). Kotlin doesn't support statements with
    /// a function tag either (it's a `TODO` there).
    fn string_expr(&mut self, call: NodeId, tag: NodeId, tagged: NodeId, parts: &[NodeId]) {
        let is_tagged = self.lib.value(tagged) == Some(&Value::Bool(true));
        let body = match *parts {
            [key, fun] if self.symbol_text(key) == Some("funString") => {
                match *self.lib.tree.children(fun) {
                    [_, _, block] if self.lib.tree.kind(block) == NodeKind::Block => Some(block),
                    _ => {
                        self.error(call, Message::MalformedSpecial("stringExpr".into()));
                        return;
                    }
                }
            }
            _ => None,
        };
        let accumulator = is_tagged && self.names_class(tag);
        match (body, accumulator) {
            (Some(body), _) if !is_tagged || accumulator => {
                self.accumulate(call, is_tagged.then_some(tag), body)
            }
            (None, true) => {
                let body = self.string_body(call, parts);
                self.accumulate(call, Some(tag), body)
            }
            (None, false) => match self.tagged_string(call, tag, tagged, parts) {
                Some(new) => self.rewrite(call, NodeKind::Call, 0, &new),
                None => self.error(call, Message::MalformedSpecial("stringExpr".into())),
            },
            (Some(_), _) => {
                let what = "statements in strings with a function tag".into();
                self.error(call, Message::Unsupported(what));
            }
        }
    }

    /// Resolves a string's tag, and the names that `let`s it names are
    /// given, as far as [`Settle::names_class`] looks, waiting as needed.
    /// Kotlin evaluates the tag; this follows names instead.
    pub(crate) fn resolve_tag(
        &mut self,
        tag: NodeId,
        scope: ScopeId,
        forced: bool,
    ) -> Result<(), crate::settle::Fact> {
        let (mut node, mut scope) = (tag, scope);
        for _ in 0..TAG_DEPTH {
            self.resolve_name(node, scope, forced)?;
            match self.tag_alias(node) {
                Some((init, owner)) => (node, scope) = (init, owner),
                None => break,
            }
        }
        Ok(())
    }

    /// Whether a resolved tag names a class: one declared here, or through
    /// `let` names for it, as imports make.
    fn names_class(&self, tag: NodeId) -> bool {
        let mut node = tag;
        for _ in 0..TAG_DEPTH {
            let decl = self
                .scopes
                .resolved(node)
                .and_then(|b| self.scopes.symbol(b).decl);
            if decl.is_some_and(|d| self.lib.tree.kind(d) == NodeKind::Class) {
                return true;
            }
            match self.tag_alias(node) {
                Some((init, _)) => node = init,
                None => return false,
            }
        }
        false
    }

    /// For a name resolved to a `let` whose value is a name, that name and
    /// the scope it's in.
    fn tag_alias(&self, node: NodeId) -> Option<(NodeId, ScopeId)> {
        let symbol = self.scopes.symbol(self.scopes.resolved(node)?);
        let decl = symbol.decl?;
        if self.lib.tree.kind(decl) != NodeKind::Let || self.lib.tree.data(decl) & LET_VAR != 0 {
            return None;
        }
        let init = crate::core::let_parts(&self.lib.tree, decl).2?;
        (self.lib.tree.kind(init) == NodeKind::RightName).then_some((init, symbol.owner))
    }

    /// A tagged string's parts, `"a", \interpolate, b, "c"`, as the body of
    /// a string with statements: `\safeStringPart "a"; \interpolate b;
    /// \safeStringPart "c"`.
    fn string_body(&mut self, call: NodeId, parts: &[NodeId]) -> NodeId {
        let span = self.lib.tree.span(call);
        let mut statements = Vec::with_capacity(parts.len() * 2);
        let mut rest = parts;
        while let Some((&part, more)) = rest.split_first() {
            rest = more;
            if self.symbol_text(part) == Some("interpolate") {
                if let Some((&value, more)) = rest.split_first() {
                    rest = more;
                    statements.extend([part, value]);
                }
                continue;
            }
            let at = self.lib.tree.span(part);
            let key = Value::Symbol(self.lib.names.intern("safeStringPart"));
            statements.push(self.lib.add_value(key, at, part));
            statements.push(part);
        }
        self.lib.add(NodeKind::Block, span, 0, &statements, call)
    }

    /// A string with statements, or with an accumulator type for a tag,
    /// as Kotlin's `StringExprMacro` lowers it: `do { let t = new
    /// StringBuilder(); body; t.toString() }`, or with a tag `C`, `do { let
    /// t = new C(); body; t.accumulated }`, where in `body`, and blocks
    /// within it, `\safeStringPart "a"` is `t.appendSafe("a")` and
    /// `\interpolate b` is `t.append(b)`; for a `StringBuilder`, both are
    /// `append`, of `cat(b)` for an interpolation, so null is `"null"`.
    fn accumulate(&mut self, call: NodeId, tag: Option<NodeId>, body: NodeId) {
        let span = self.lib.tree.span(call);
        let t = self.lib.names.temporary("accumulator");
        let mut blocks = Vec::new();
        self.string_blocks(body, &mut blocks);
        for block in blocks {
            self.point_appends(block, t, tag.is_some());
        }
        let class = match tag {
            Some(tag) => tag,
            None => {
                let builder = Builtin::named("StringBuilder").unwrap();
                let builder = self.scopes.hidden(builder);
                self.builtin_name(builder, span, call)
            }
        };
        let new = self.scopes.hidden(Builtin::Function(Function::New));
        let new = self.builtin_name(new, span, call);
        let made = self.lib.add(NodeKind::Call, span, 0, &[new, class], call);
        let left = self.lib.add_name(NodeKind::LeftName, t, span, call);
        let decl = self
            .lib
            .add(NodeKind::Let, span, LET_INIT, &[left, made], call);
        let end = Span {
            start: span.end,
            end: span.end,
        };
        let result = if tag.is_some() {
            self.member(t, "accumulated", end, call)
        } else {
            let to_string = self.member(t, "toString", end, call);
            self.lib.add(NodeKind::Call, end, 0, &[to_string], call)
        };
        self.rewrite(call, NodeKind::Block, 0, &[decl, body, result]);
    }

    /// `t.name`.
    fn member(&mut self, t: NameId, name: &str, span: Span, like: NodeId) -> NodeId {
        let dot = self.lib.names.intern(".");
        let dot = self.lib.add_name(NodeKind::BuiltinName, dot, span, like);
        let object = self.lib.add_name(NodeKind::RightName, t, span, like);
        let key = Value::Symbol(self.lib.names.intern(name));
        let key = self.lib.add_value(key, span, like);
        self.lib
            .add(NodeKind::Call, span, 0, &[dot, object, key], like)
    }

    /// The blocks in a string's body, itself included, but not those of
    /// strings with statements nested in it, which have their own
    /// accumulator.
    fn string_blocks(&self, node: NodeId, blocks: &mut Vec<NodeId>) {
        let tree = &self.lib.tree;
        let children = tree.children(node);
        let nested = tree.kind(node) == NodeKind::Fun
            && children
                .first()
                .is_some_and(|&c| self.symbol_text(c) == Some("funString"));
        if nested {
            return;
        }
        if tree.kind(node) == NodeKind::Block {
            blocks.push(node);
        }
        for &child in children {
            self.string_blocks(child, blocks);
        }
    }

    /// Points a block's `\safeStringPart` and `\interpolate` statements at
    /// accumulator `t` ([`Settle::accumulate`]). Adjacent literal parts,
    /// as escapes broken out, are appended as one.
    fn point_appends(&mut self, block: NodeId, t: NameId, tagged: bool) {
        let kids = self.lib.tree.children(block).to_vec();
        let mut new = Vec::with_capacity(kids.len());
        let mut i = 0;
        while i < kids.len() {
            let (kid, next) = (kids[i], kids.get(i + 1).copied());
            let (Some(text), Some(next)) = (self.symbol_text(kid), next) else {
                new.push(kid);
                i += 1;
                continue;
            };
            let at = self.lib.tree.span(next);
            match text {
                "safeStringPart" => {
                    let mut parts = vec![next];
                    i += 2;
                    while i + 1 < kids.len() {
                        if self.is_comment(kids[i]) {
                            i += 1;
                        } else if self.symbol_text(kids[i]) == Some("safeStringPart") {
                            parts.push(kids[i + 1]);
                            i += 2;
                        } else {
                            break;
                        }
                    }
                    let joined: Option<String> = parts
                        .iter()
                        .map(|&p| match self.lib.value(p) {
                            Some(Value::String(s)) => Some(s.as_str()),
                            _ => None,
                        })
                        .collect();
                    let span = at.cover(self.lib.tree.span(*parts.last().unwrap()));
                    let value = match joined {
                        Some(text) if parts.len() > 1 => {
                            self.lib.add_value(Value::String(text), span, next)
                        }
                        _ if parts.len() == 1 => next,
                        _ => {
                            let cat = self.scopes.hidden(Builtin::Function(Function::Cat));
                            let mut args = vec![self.builtin_name(cat, span, next)];
                            args.extend(parts);
                            self.lib.add(NodeKind::Call, span, 0, &args, next)
                        }
                    };
                    let method = if tagged { "appendSafe" } else { "append" };
                    let callee = self.member(t, method, span, next);
                    new.push(
                        self.lib
                            .add(NodeKind::Call, span, 0, &[callee, value], next),
                    );
                }
                "interpolate" => {
                    let value = if tagged {
                        next
                    } else {
                        let cat = self.scopes.hidden(Builtin::Function(Function::Cat));
                        let cat = self.builtin_name(cat, at, next);
                        self.lib.add(NodeKind::Call, at, 0, &[cat, next], next)
                    };
                    let callee = self.member(t, "append", at, next);
                    new.push(self.lib.add(NodeKind::Call, at, 0, &[callee, value], next));
                    i += 2;
                }
                _ => {
                    new.push(kid);
                    i += 1;
                }
            }
        }
        if new != kids {
            self.set_children(block, &new);
        }
    }

    /// Whether a node is a comment, `/* … */` or `// …`, before expansion.
    fn is_comment(&self, node: NodeId) -> bool {
        self.lib.tree.kind(node) == NodeKind::Call
            && self
                .lib
                .tree
                .children(node)
                .first()
                .and_then(|&c| self.lib.value(c))
                == Some(&Value::Builtin(SyntaxBuiltin::EmbeddedComment))
    }

    /// A tagged string, `tag"a${b}c"`, is a call of its tag with a list of
    /// its literal parts, escapes not decoded, and a list of what's
    /// interpolated: `tag(["a", "c"], [b])`, as Kotlin Temper's
    /// `StringExprMacro` does for a tag that's a function. There's one more
    /// literal part than interpolations, empty where two are adjacent.
    /// `None` for anything else.
    fn tagged_string(
        &mut self,
        call: NodeId,
        tag: NodeId,
        tagged: NodeId,
        parts: &[NodeId],
    ) -> Option<Vec<NodeId>> {
        if self.lib.value(tagged) != Some(&Value::Bool(true))
            // An undeclared tag is an error already.
            || !matches!(self.lib.tree.kind(tag), NodeKind::RightName | NodeKind::Error)
        {
            return None;
        }
        let span = self.lib.tree.span(call);
        let (mut literals, mut values) = (Vec::<String>::new(), Vec::new());
        let mut rest = parts;
        while let Some((&part, more)) = rest.split_first() {
            rest = more;
            match (self.symbol_text(part), self.lib.value(part)) {
                (Some("interpolate"), _) => {
                    let (&value, more) = rest.split_first()?;
                    rest = more;
                    if literals.len() <= values.len() {
                        literals.push(String::new());
                    }
                    values.push(value);
                }
                // Adjacent literal parts, as escapes broken out, are joined.
                (None, Some(Value::String(text))) if literals.len() > values.len() => {
                    literals.last_mut()?.push_str(text);
                }
                (None, Some(Value::String(text))) => literals.push(text.to_string()),
                _ => return None,
            }
        }
        if literals.len() <= values.len() {
            literals.push(String::new());
        }
        let list = self.scopes.hidden(Builtin::Function(Function::List));
        let mut literal_list = vec![self.builtin_name(list, span, call)];
        for text in literals {
            literal_list.push(self.lib.add_value(Value::String(text), span, call));
        }
        let mut value_list = vec![self.builtin_name(list, span, call)];
        value_list.extend(values);
        let literal_list = self.lib.add(NodeKind::Call, span, 0, &literal_list, call);
        let value_list = self.lib.add(NodeKind::Call, span, 0, &value_list, call);
        Some(vec![tag, literal_list, value_list])
    }

    /// `a + b` is `desugarOperation(+, a, b)`: a call of the operator's
    /// name, or for `+=`, `++` and the like, an assignment.
    fn desugar_operation(&mut self, call: NodeId, scope: ScopeId, op: NodeId, operands: &[NodeId]) {
        let Some(name) = self.lib.name(op) else {
            return self.error(call, Message::MalformedSpecial("desugarOperation".into()));
        };
        let text = self.lib.names.get(name).to_string();
        let span = self.lib.tree.span(call);
        let simple = match (text.as_str(), operands) {
            ("++" | "_++", [_]) => Some("+"),
            ("--" | "_--", [_]) => Some("-"),
            (t, [_, _])
                if t.len() >= 2 && t.ends_with('=') && !matches!(t, "==" | "!=" | "<=" | ">=") =>
            {
                Some(&t[..t.len() - 1])
            }
            _ => None,
        };
        let Some(simple) = simple else {
            let mut children = vec![op];
            children.extend_from_slice(operands);
            return self.rewrite(call, NodeKind::Call, 0, &children);
        };
        let target = operands[0];
        if self.is_dot_call(target) {
            return self.desugar_member_operation(
                call,
                scope,
                &text,
                simple,
                target,
                operands.get(1).copied(),
            );
        }
        if self.is_index_call(target) {
            return self.desugar_index_operation(
                call,
                scope,
                &text,
                simple,
                target,
                operands.get(1).copied(),
            );
        }
        let Some(target_name) = self
            .lib
            .name(target)
            .filter(|_| self.lib.tree.kind(target) == NodeKind::RightName)
        else {
            return self.error(
                call,
                Message::Unsupported(format!("{text} on anything but a name")),
            );
        };
        let Some(function) = self
            .lib
            .names
            .find(simple)
            .and_then(|n| self.scopes.builtin(n))
        else {
            return self.error(call, Message::Unsupported(format!("operator {text}")));
        };
        let operator = self.builtin_name(function, span, op);
        let reread = self.lib.add_name(
            NodeKind::RightName,
            target_name,
            self.lib.tree.span(target),
            target,
        );
        let amount = match operands {
            [_, value] => *value,
            _ => self.lib.add_value(Value::Int32(1), span, call),
        };
        if text.starts_with('_') {
            // `x++`: `do { let t = x; x = t + 1; t }`.
            let t = self.lib.names.temporary("t");
            let t_left = self.lib.add_name(NodeKind::LeftName, t, span, call);
            let t_let = self
                .lib
                .add(NodeKind::Let, span, LET_INIT, &[t_left, reread], call);
            let t_use = self.lib.add_name(NodeKind::RightName, t, span, call);
            let sum = self
                .lib
                .add(NodeKind::Call, span, 0, &[operator, t_use, amount], call);
            let assign = self
                .lib
                .add(NodeKind::Assign, span, 0, &[target, sum], call);
            // The result is the old `x`, where type errors about it go.
            let x_span = self.lib.tree.span(target);
            let t_result = self.lib.add_name(NodeKind::RightName, t, x_span, call);
            self.rewrite(call, NodeKind::Block, 0, &[t_let, assign, t_result]);
        } else {
            let sum = self
                .lib
                .add(NodeKind::Call, span, 0, &[operator, reread, amount], call);
            self.rewrite(call, NodeKind::Assign, 0, &[target, sum]);
        }
    }

    /// `obj.name += v`, `obj.name++` and the like, reading `obj` once:
    /// `do { let o = obj; o.name = o.name + v }`, or for `obj.name++`,
    /// `do { let o = obj; let t = o.name; o.name = t + 1; t }`. `obj`
    /// isn't held if it's a stable name ([`Settle::stable_name`]).
    fn desugar_member_operation(
        &mut self,
        call: NodeId,
        scope: ScopeId,
        text: &str,
        simple: &str,
        target: NodeId,
        value: Option<NodeId>,
    ) {
        let span = self.lib.tree.span(call);
        let Some(function) = self
            .lib
            .names
            .find(simple)
            .and_then(|n| self.scopes.builtin(n))
        else {
            return self.error(call, Message::Unsupported(format!("operator {text}")));
        };
        let [dot, object, key] = *self.lib.tree.children(target) else {
            unreachable!()
        };
        let (dot_name, key_value) = (self.lib.name(dot).unwrap(), self.lib.value(key).cloned());
        let Some(key_value) = key_value else {
            return self.error(call, Message::MalformedSpecial(".".into()));
        };
        let (mut statements, o) = self.held_receiver(object, scope, "o", call);
        // A name read again where it's written, for the editor.
        let o_span = match statements.is_empty() {
            true => self.lib.tree.span(object),
            false => span,
        };
        let member = |s: &mut Self| {
            let callee = s.lib.add_name(NodeKind::BuiltinName, dot_name, span, dot);
            let o_use = s.lib.add_name(NodeKind::RightName, o, o_span, call);
            let key = s.lib.add_value(key_value.clone(), span, key);
            s.lib
                .add(NodeKind::Call, span, 0, &[callee, o_use, key], target)
        };
        let amount = match value {
            Some(v) => v,
            None => self.lib.add_value(Value::Int32(1), span, call),
        };
        let operator = self.builtin_name(function, span, call);
        let mut old = member(self);
        let postfix = text.starts_with('_');
        if postfix {
            let t = self.lib.names.temporary("t");
            let t_left = self.lib.add_name(NodeKind::LeftName, t, span, call);
            statements.push(
                self.lib
                    .add(NodeKind::Let, span, LET_INIT, &[t_left, old], call),
            );
            old = self.lib.add_name(NodeKind::RightName, t, span, call);
        }
        let sum = self
            .lib
            .add(NodeKind::Call, span, 0, &[operator, old, amount], call);
        let assigned = member(self);
        statements.push(
            self.lib
                .add(NodeKind::Assign, span, 0, &[assigned, sum], call),
        );
        if postfix {
            let t = self.lib.name(old).unwrap();
            statements.push(self.lib.add_name(NodeKind::RightName, t, span, call));
        }
        self.rewrite(call, NodeKind::Block, 0, &statements);
    }

    /// `ls[i] += v`, `ls[i]++`, `++ls[i]` and the like, evaluating `ls`
    /// and `i` once, as Kotlin Temper does: `do { let o = ls; let k = i;
    /// o.set(k, o[k] + v) }`, whose value is `set`'s, as for `ls[i] = v`.
    /// `ls[i]++` is `do { ...; let t = o[k]; o.set(k, t + 1); t }`, and
    /// `++ls[i]` is `do { ...; let t = o[k] + 1; o.set(k, t); t }`. A
    /// literal index isn't held, nor a list or an index that's a stable
    /// name ([`Settle::stable_name`]).
    fn desugar_index_operation(
        &mut self,
        call: NodeId,
        scope: ScopeId,
        text: &str,
        simple: &str,
        target: NodeId,
        value: Option<NodeId>,
    ) {
        let span = self.lib.tree.span(call);
        let Some(function) = self
            .lib
            .names
            .find(simple)
            .and_then(|n| self.scopes.builtin(n))
        else {
            return self.error(call, Message::Unsupported(format!("operator {text}")));
        };
        let [index, object, key] = *self.lib.tree.children(target) else {
            unreachable!()
        };
        let index_name = self.lib.name(index).unwrap();
        let (mut statements, o) = self.held_receiver(object, scope, "o", call);
        // A name read again where it's written, for the editor.
        let name_span = |s: &Self, node: NodeId, held: bool| match held {
            true => span,
            false => s.lib.tree.span(node),
        };
        let o_span = name_span(self, object, !statements.is_empty());
        let key_value = match self.lib.tree.kind(key) {
            NodeKind::Value => self.lib.value(key).cloned(),
            _ => None,
        };
        let k = match key_value {
            Some(_) => None,
            None => {
                let (lets, k) = self.held_receiver(key, scope, "k", call);
                let k_span = name_span(self, key, !lets.is_empty());
                statements.extend(lets);
                Some((k, k_span))
            }
        };
        let key_use = |s: &mut Self| match (k, &key_value) {
            (Some((k, k_span)), _) => s.lib.add_name(NodeKind::RightName, k, k_span, call),
            (None, Some(v)) => s.lib.add_value(v.clone(), span, key),
            (None, None) => unreachable!(),
        };
        let o_use = |s: &mut Self| s.lib.add_name(NodeKind::RightName, o, o_span, call);
        let read = {
            let callee = self
                .lib
                .add_name(NodeKind::BuiltinName, index_name, span, index);
            let (o, k) = (o_use(self), key_use(self));
            self.lib
                .add(NodeKind::Call, span, 0, &[callee, o, k], target)
        };
        let amount = match value {
            Some(v) => v,
            None => self.lib.add_value(Value::Int32(1), span, call),
        };
        let operator = self.builtin_name(function, span, call);
        let compound = value.is_some();
        let postfix = text.starts_with('_');
        let mut held_result = None;
        let mut temp = |s: &mut Self, init: NodeId, statements: &mut Vec<NodeId>| {
            let t = s.lib.names.temporary("t");
            let t_left = s.lib.add_name(NodeKind::LeftName, t, span, call);
            statements.push(
                s.lib
                    .add(NodeKind::Let, span, LET_INIT, &[t_left, init], call),
            );
            held_result = Some(t);
            s.lib.add_name(NodeKind::RightName, t, span, call)
        };
        let old = match postfix {
            true => temp(self, read, &mut statements),
            false => read,
        };
        let mut new = self
            .lib
            .add(NodeKind::Call, span, 0, &[operator, old, amount], call);
        if !compound && !postfix {
            new = temp(self, new, &mut statements);
        }
        let set = {
            let dot = self.lib.names.intern(".");
            let dot = self.lib.add_name(NodeKind::BuiltinName, dot, span, index);
            let key = Value::Symbol(self.lib.names.intern("set"));
            let key = self.lib.add_value(key, span, index);
            let o = o_use(self);
            let member = self
                .lib
                .add(NodeKind::Call, span, 0, &[dot, o, key], target);
            let k = key_use(self);
            self.lib
                .add(NodeKind::Call, span, 0, &[member, k, new], call)
        };
        statements.push(set);
        if let Some(t) = held_result {
            statements.push(self.lib.add_name(NodeKind::RightName, t, span, call));
        }
        self.rewrite(call, NodeKind::Block, 0, &statements);
    }

    /// The receiver of `obj.name += v` or `obj[i] += v`, or the index,
    /// held in a temporary, unless it's a name that reads the same each
    /// time ([`Settle::stable_name`]).
    fn held_receiver(
        &mut self,
        object: NodeId,
        scope: ScopeId,
        hint: &str,
        like: NodeId,
    ) -> (Vec<NodeId>, NameId) {
        match self.stable_name(object, scope) {
            Some(name) => (Vec::new(), name),
            None => self.held(object, hint, like),
        }
    }

    /// A name that reads the same however often it's read: a type's, as
    /// `C` in `C.count += 1`, which Kotlin Temper doesn't hold either
    /// (`isKnownStable`), and which a static member's read and assignment
    /// need as written; or a `let`'s or a parameter's, which nothing can
    /// assign meanwhile. Not a `var`, which the value may assign, nor a
    /// member named bare, a getter's call.
    fn stable_name(&self, node: NodeId, scope: ScopeId) -> Option<NameId> {
        let name = self.lib.name(node)?;
        if self.lib.tree.kind(node) != NodeKind::RightName {
            return None;
        }
        let at = self.lib.tree.span(node).start;
        let Lookup::Found(s) = self.scopes.lookup(scope, name, false, at) else {
            return None;
        };
        let symbol = self.scopes.symbol(s);
        let stable = self.type_decl(s).is_some()
            || (!symbol.mutable
                && matches!(
                    symbol.kind,
                    SymbolKind::Local | SymbolKind::Parameter | SymbolKind::TopLevel
                ));
        stable.then_some(name)
    }

    /// Whether `node` names a member bare, as `problem` for
    /// `this.problem` in its class.
    fn is_bare_member(&self, node: NodeId, scope: ScopeId) -> bool {
        let Some(name) = self.lib.name(node) else {
            return false;
        };
        if self.lib.tree.kind(node) != NodeKind::RightName {
            return false;
        }
        let at = self.lib.tree.span(node).start;
        match self.scopes.lookup(scope, name, false, at) {
            Lookup::Found(s) => self.scopes.symbol(s).kind == SymbolKind::Member,
            _ => false,
        }
    }

    /// A temporary holding `value`, unless it's a name already: the
    /// statements declaring it, if any, and the name to use.
    fn subject(&mut self, value: NodeId, hint: &str, like: NodeId) -> (Vec<NodeId>, NameId) {
        if self.lib.tree.kind(value) == NodeKind::RightName {
            return (Vec::new(), self.lib.name(value).unwrap());
        }
        self.held(value, hint, like)
    }

    /// A temporary holding `value`, and its `let`.
    fn held(&mut self, value: NodeId, hint: &str, like: NodeId) -> (Vec<NodeId>, NameId) {
        let span = self.lib.tree.span(value);
        let t = self.lib.names.temporary(hint);
        let left = self.lib.add_name(NodeKind::LeftName, t, span, like);
        (
            vec![
                self.lib
                    .add(NodeKind::Let, span, LET_INIT, &[left, value], like),
            ],
            t,
        )
    }

    /// `name == null`.
    fn null_check(&mut self, name: NameId, span: Span, like: NodeId) -> NodeId {
        let eq = self.scopes.hidden(Builtin::Function(Function::Eq));
        let eq = self.builtin_name(eq, span, like);
        let used = self.lib.add_name(NodeKind::RightName, name, span, like);
        let null = self.lib.add_value(Value::Null, span, like);
        self.lib
            .add(NodeKind::Call, span, 0, &[eq, used, null], like)
    }

    /// `a?.name`, or as `dot` in a call, `a?.name(args)`: `do { let t = a;
    /// if (t == null) { null } else { t.name(args) } }`, as Kotlin Temper
    /// lowers it. A later `.` isn't skipped: `a?.b.c` fails if `a` is null.
    /// `a` is held in a temporary unless it's a name that reads the same
    /// each time ([`Settle::stable_name`]), which the null check narrows:
    /// a `var`'s or a bare member's name (`this`'s getter) is held, as
    /// checking it for null doesn't narrow the next read.
    pub(crate) fn expand_chain_null(&mut self, call: NodeId, dot: NodeId, scope: ScopeId) {
        let span = self.lib.tree.span(call);
        let [op, object, key] = *self.lib.tree.children(dot) else {
            unreachable!()
        };
        let (mut statements, name) = self.held_receiver(object, scope, "subject", call);
        let null_check = self.null_check(name, span, call);
        let null = self.lib.add_value(Value::Null, span, call);
        let dot_name = self.lib.names.intern(".");
        let dot_name =
            self.lib
                .add_name(NodeKind::BuiltinName, dot_name, self.lib.tree.span(op), op);
        let used = self.lib.add_name(
            NodeKind::RightName,
            name,
            self.lib.tree.span(object),
            object,
        );
        let mut member = self.lib.add(
            NodeKind::Call,
            self.lib.tree.span(dot),
            0,
            &[dot_name, used, key],
            dot,
        );
        if dot != call {
            let mut parts = self.lib.tree.children(call).to_vec();
            parts[0] = member;
            member = self.lib.add(NodeKind::Call, span, 0, &parts, call);
        }
        statements.push(
            self.lib
                .add(NodeKind::If, span, 0, &[null_check, null, member], call),
        );
        self.rewrite(call, NodeKind::Block, 0, &statements);
    }

    /// `when (x) { 0, 1 -> a; is T -> b; else -> c }` is, as in Kotlin
    /// Temper, `do { let t = x; if (t == 0 || t == 1) { a } else if (t is
    /// T) { b } else { c } }`, with no temporary if `x` is a name. Without
    /// `else`, it's void when nothing matches. A `case` pattern, `case
    /// f(let y) ->`, is an error: decomposing it needs the subject's type,
    /// and Kotlin hasn't written that either, so its test fails there too.
    fn expand_when(&mut self, call: NodeId, args: &[NodeId]) -> bool {
        let [subject, body] = *args else { return false };
        let Some(block) = self.block_of(body) else {
            return false;
        };
        let span = self.lib.tree.span(call);
        let (mut statements, name) = self.subject(subject, "subject", call);
        let kids = self.lib.tree.children(block).to_vec();
        // Each branch's tests (none for `else`) and value, gathered as
        // Kotlin's `buildBranches` does.
        let mut branches: Vec<(Vec<NodeId>, NodeId)> = Vec::new();
        let mut has_else = false;
        let mut i = 0;
        while i < kids.len() {
            let start = i;
            let is_excess = has_else;
            let mut tests = Vec::new();
            let (mut is_else, mut invalid) = (false, false);
            while let Some(kind) = kids.get(i).and_then(|&k| self.symbol_text(k)) {
                // `None` for `else`, and for `case` patterns, Kotlin's
                // `postponedCase` calls, `Some(None)`.
                let kind = match kind {
                    "default" => None,
                    "case" => Some(Some(Function::Eq)),
                    "case_is" => Some(Some(Function::Is)),
                    "case_case" => Some(None),
                    _ => break,
                };
                let kid = kids[i];
                i += 1;
                let Some(kind) = kind else {
                    is_else = true;
                    has_else = true;
                    continue;
                };
                let Some(&test) = kids.get(i) else {
                    self.error(kid, Message::InvalidCaseCondition);
                    break;
                };
                i += 1;
                let Some(kind) = kind else {
                    let is_pattern = self.lib.tree.kind(test) == NodeKind::Call
                        && self.lib.tree.children(test).first().is_some_and(|&c| {
                            self.lib.value(c) == Some(&Value::Builtin(SyntaxBuiltin::PostponedCase))
                        });
                    if is_pattern {
                        // Kotlin Temper keeps the pattern's tokens for a
                        // macro of the subject's type to decompose, which
                        // isn't written yet; so its test fails there.
                        let what = "case patterns, which need types".into();
                        self.error(test, Message::Unsupported(what));
                        tests.push(test);
                    } else {
                        self.error(test, Message::InvalidCaseCondition);
                        invalid = true;
                    }
                    continue;
                };
                let function = self.scopes.hidden(Builtin::Function(kind));
                let test_span = self.lib.tree.span(test);
                let callee = self.builtin_name(function, test_span, test);
                let used = self
                    .lib
                    .add_name(NodeKind::RightName, name, test_span, subject);
                tests.push(
                    self.lib
                        .add(NodeKind::Call, test_span, 0, &[callee, used, test], test),
                );
            }
            if tests.is_empty() && !is_else && !invalid {
                // Not a case: a trailing `;`'s void and comments are fine.
                let kid = kids[start];
                if self.lib.value(kid) != Some(&Value::Void) && !self.is_comment(kid) {
                    self.error(kid, Message::InvalidBlockContent);
                }
                if i == start {
                    i += 1;
                }
                continue;
            }
            let Some(&value) = kids.get(i) else {
                // Kotlin stops here, keeping the branches before.
                let at = self.lib.tree.span(kids[i.saturating_sub(2)]).end;
                let span = Span { start: at, end: at };
                self.lib
                    .report(Level::Error, Message::MissingCaseValue, span);
                break;
            };
            i += 1;
            if is_excess {
                self.error(kids[start], Message::ElseMustBeLast);
            } else if !invalid {
                branches.push((if is_else { Vec::new() } else { tests }, value));
            }
        }
        // Later branches nest in earlier ones' `else`, so fold from the right.
        let mut chain = None;
        for (tests, value) in branches.into_iter().rev() {
            let Some(test) = tests.into_iter().rev().reduce(|later, test| {
                let yes = self.lib.add_value(Value::Bool(true), span, call);
                self.lib.add(
                    NodeKind::If,
                    self.lib.tree.span(test),
                    0,
                    &[test, yes, later],
                    call,
                )
            }) else {
                chain = Some(value);
                continue;
            };
            let otherwise = match chain {
                Some(c) => c,
                None => self.lib.add(
                    NodeKind::Block,
                    Span {
                        start: span.end,
                        end: span.end,
                    },
                    0,
                    &[],
                    call,
                ),
            };
            chain = Some(self.lib.add(
                NodeKind::If,
                self.lib.tree.span(value),
                0,
                &[test, value, otherwise],
                call,
            ));
        }
        if let Some(chain) = chain {
            statements.push(chain);
        }
        self.rewrite(call, NodeKind::Block, 0, &statements);
        true
    }

    /// Whether a node is a list literal of string literals, as
    /// `["cpp", "rust"]`.
    fn is_string_list(&self, node: NodeId) -> bool {
        let tree = &self.lib.tree;
        let Some((&callee, items)) = tree.children(node).split_first() else {
            return false;
        };
        tree.kind(node) == NodeKind::Call
            && self.lib.value(callee) == Some(&Value::Builtin(SyntaxBuiltin::List))
            && (items.iter()).all(|&i| matches!(self.lib.value(i), Some(Value::String(_))))
    }

    /// The string values in a tree, in order, as the backends a
    /// `@connected(["cpp", "rust"])` lists.
    fn strings_in(&self, node: NodeId) -> Vec<String> {
        let mut found = Vec::new();
        let mut stack = vec![node];
        while let Some(n) = stack.pop() {
            if let Some(Value::String(s)) = self.lib.value(n) {
                found.push(s.to_string());
            }
            stack.extend(self.lib.tree.children(n).iter().rev());
        }
        found
    }

    /// `let f(x: Int): Int { body }`: `let \word ^f params... \outType Int
    /// fn { body }`, a constant function.
    fn expand_let(&mut self, call: NodeId, args: &[NodeId]) -> bool {
        let [word, name, rest @ ..] = args else {
            return false;
        };
        if self.symbol_text(*word) != Some("word")
            || self.lib.tree.kind(*name) != NodeKind::LeftName
        {
            return false;
        }
        let Some(sig) = self.signature(call, rest) else {
            return self.lib.tree.kind(call) == NodeKind::Error;
        };
        let connected = self.connected.remove(&call);
        let listed = self.connected_lists.remove(&call);
        if connected {
            let c = crate::library::Connected {
                backends: listed,
                params: Vec::new(),
            };
            self.lib.connected.insert(call, c);
        }
        let Some(body) = sig.body.and_then(|b| self.block_of(b)) else {
            if connected && sig.body.is_none() {
                // Backends not listed would have no body to use.
                if self
                    .lib
                    .connected
                    .get(&call)
                    .is_some_and(|c| c.backends.is_some())
                {
                    let span = self.lib.tree.span(*name);
                    let message = Message::ConnectedBackendsNeedBody;
                    self.lib.report(Level::Error, message, span);
                }
                // Implemented outside Temper: its type, `fn (A): B`.
                let lowered = self.lower_params(&sig.params);
                // Its parameters' names, for backends' wrappers of host code.
                let names: Vec<String> = (lowered.iter().flatten())
                    .map(|&p| crate::core::let_parts(&self.lib.tree, p).0)
                    .map(|n| self.lib.name(n).map_or("", |n| self.lib.names.get(n)))
                    .map(str::to_string)
                    .collect();
                if let Some(c) = self.lib.connected.get_mut(&call) {
                    c.params = names;
                }
                let types = lowered.and_then(|params| {
                    let type_of = |&p: &NodeId| crate::core::let_parts(&self.lib.tree, p).1;
                    params.iter().map(type_of).collect::<Option<Vec<_>>>()
                });
                let Some(types) = types else {
                    self.error(call, Message::MalformedFunction);
                    return true;
                };
                let span = self.lib.tree.span(call);
                let parts = self.fn_type_parts(&types, sig.out_type, span, call);
                let ty = self.lib.add(NodeKind::Call, span, 0, &parts, call);
                self.rewrite(
                    call,
                    NodeKind::Let,
                    LET_TYPE | MEMBER_CONNECTED,
                    &[*name, ty],
                );
                return true;
            }
            self.error(
                call,
                Message::Unsupported("a function without a body".into()),
            );
            return true;
        };
        let span = self.lib.tree.span(call);
        let Some(f) = self.make_fn(&sig, body, span, call) else {
            self.error(call, Message::MalformedFunction);
            return true;
        };
        self.rewrite(call, NodeKind::Let, LET_INIT, &[*name, f]);
        true
    }

    /// `fn (x: Int): Int { body }` is a function, and without a body, a
    /// function type.
    fn expand_fn(&mut self, call: NodeId, args: &[NodeId]) -> bool {
        let Some(sig) = self.signature(call, args) else {
            return self.lib.tree.kind(call) == NodeKind::Error;
        };
        let span = self.lib.tree.span(call);
        match sig.body {
            Some(body) => {
                let Some(block) = self.block_of(body) else {
                    return false;
                };
                let Some((children, data)) = self.fn_layout(&sig, block) else {
                    self.error(call, Message::MalformedFunction);
                    return true;
                };
                self.rewrite(call, NodeKind::Fn, data, &children);
            }
            None => {
                let params: Vec<NodeId> = sig.params.iter().map(|&p| self.param_type(p)).collect();
                let children = self.fn_type_parts(&params, sig.out_type, span, call);
                self.rewrite(call, NodeKind::Call, 0, &children);
            }
        }
        true
    }

    /// A function type's parameter: its type, so `fn (row: Int): Void`
    /// is `fn (Int): Void`, as in Kotlin, where the names only document.
    fn param_type(&self, param: NodeId) -> NodeId {
        let tree = &self.lib.tree;
        let children = tree.children(param);
        let named = tree.kind(param) == NodeKind::Block
            && children.len() == 4
            && self.symbol_text(children[0]) == Some("_complexArg_")
            && tree.kind(children[1]) == NodeKind::RightName;
        match self.pairs(&children[2.min(children.len())..], "default") {
            Some((Some(ty), None)) if named => ty,
            _ => param,
        }
    }

    /// A function type, `fn (A): B`, as a call's children. Type
    /// parameters are dropped for now.
    fn fn_type_parts(
        &mut self,
        params: &[NodeId],
        out_type: Option<NodeId>,
        span: Span,
        origin: NodeId,
    ) -> Vec<NodeId> {
        let fn_type = self.scopes.hidden(Builtin::Function(Function::FnType));
        let callee = self.builtin_name(fn_type, span, origin);
        let mut children = vec![callee];
        children.extend(params);
        children.extend(out_type);
        children
    }

    /// Splits a function's parts after its name: type parameters (each
    /// `\typeArg T`, lowered to a `Let`), parameters, `\outType T`,
    /// supertypes (each `\super T`), and a final `Fun` for the body.
    /// Malformed parts make `node` an error.
    pub(crate) fn signature(&mut self, node: NodeId, parts: &[NodeId]) -> Option<Signature> {
        let mut sig = Signature {
            type_params: Vec::new(),
            params: Vec::new(),
            out_type: None,
            supers: Vec::new(),
            body: None,
        };
        let mut i = 0;
        while i < parts.len() {
            let part = parts[i];
            match self.symbol_text(part) {
                Some("outType") if i + 1 < parts.len() => {
                    sig.out_type = Some(parts[i + 1]);
                    i += 2;
                    continue;
                }
                // Up to 15, as `Fn`'s data counts them in 4 bits.
                Some("super") if i + 1 < parts.len() && sig.supers.len() < 15 => {
                    sig.supers.push(parts[i + 1]);
                    i += 2;
                    continue;
                }
                Some("typeArg") if i + 1 < parts.len() => {
                    let Some(param) = self.lower_type_param(parts[i + 1]) else {
                        self.error(node, Message::MalformedFunction);
                        return None;
                    };
                    sig.type_params.push(param);
                    i += 2;
                    continue;
                }
                Some(_) => return None,
                None => {}
            }
            if i == parts.len() - 1 && self.lib.tree.kind(part) == NodeKind::Fun {
                sig.body = Some(part);
            } else {
                sig.params.push(part);
            }
            i += 1;
        }
        Some(sig)
    }

    /// A type parameter, as `T`, `T extends Bound` or with decorators,
    /// becomes a `Let` whose type is its bound. Type parameters are
    /// invariant, but `@out` and `@in` are kept as
    /// [`TYPE_PARAM_VARIANT`], so checks can leave alone what only
    /// variance accepts; other decorators are ignored.
    pub(crate) fn lower_type_param(&mut self, node: NodeId) -> Option<NodeId> {
        self.lower_type_param_marked(node, 0)
    }

    fn lower_type_param_marked(&mut self, node: NodeId, flags: u32) -> Option<NodeId> {
        let children = self.lib.tree.children(node).to_vec();
        let span = self.lib.tree.span(node);
        let (name, bound) = match (self.lib.tree.kind(node), &children[..]) {
            (NodeKind::RightName, _) => (node, None),
            (NodeKind::Block, [key, name, rest @ ..])
                if self.symbol_text(*key) == Some("typeArg") =>
            {
                match rest {
                    [] => (*name, None),
                    [sup, bound] if self.symbol_text(*sup) == Some("super") => {
                        (*name, Some(*bound))
                    }
                    _ => return None,
                }
            }
            (NodeKind::Call, [callee, decorator, target])
                if self
                    .lib
                    .name(*callee)
                    .is_some_and(|n| self.lib.names.get(n) == "@") =>
            {
                let variant = (self.lib.name(*decorator))
                    .is_some_and(|n| matches!(self.lib.names.get(n), "out" | "in"));
                let flags = flags | if variant { TYPE_PARAM_VARIANT } else { 0 };
                return self.lower_type_param_marked(*target, flags);
            }
            _ => return None,
        };
        if self.lib.tree.kind(name) != NodeKind::RightName {
            return None;
        }
        let text = self.lib.name(name).unwrap();
        let left = self
            .lib
            .add_name(NodeKind::LeftName, text, self.lib.tree.span(name), name);
        Some(self.make_let(left, bound, None, ROLE_TYPE_PARAM | flags, span, node))
    }

    /// A grammar `Fun`, as a block argument `{ (x: Int): Int => body }`,
    /// becomes a `Fn`.
    pub(crate) fn lower_fun(&mut self, fun: NodeId) {
        let children = self.lib.tree.children(fun).to_vec();
        let Some((body, rest)) = children.split_last() else {
            return self.error(fun, Message::MalformedFunction);
        };
        let lowered = self.signature(fun, rest).filter(|sig| sig.body.is_none());
        let Some(sig) = lowered else {
            if self.lib.tree.kind(fun) == NodeKind::Fun {
                self.error(fun, Message::MalformedFunction);
            }
            return;
        };
        let Some((new, data)) = self.fn_layout(&sig, *body) else {
            return self.error(fun, Message::MalformedFunction);
        };
        self.rewrite(fun, NodeKind::Fn, data, &new);
    }

    pub(crate) fn make_fn(
        &mut self,
        sig: &Signature,
        body: NodeId,
        span: Span,
        like: NodeId,
    ) -> Option<NodeId> {
        let (children, data) = self.fn_layout(sig, body)?;
        Some(self.lib.add(NodeKind::Fn, span, data, &children, like))
    }

    /// A `Fn`'s children and data, lowering its parameters.
    fn fn_layout(&mut self, sig: &Signature, body: NodeId) -> Option<(Vec<NodeId>, u32)> {
        let (mut children, data) = self.signature_layout(sig)?;
        children.push(body);
        Some((children, data))
    }

    /// A bodiless `Fn` ([`FN_NO_BODY`]): an abstract or `@connected`
    /// member's signature.
    pub(crate) fn make_bodiless_fn(
        &mut self,
        sig: &Signature,
        span: Span,
        like: NodeId,
    ) -> Option<NodeId> {
        let (children, data) = self.signature_layout(sig)?;
        Some(
            self.lib
                .add(NodeKind::Fn, span, data | FN_NO_BODY, &children, like),
        )
    }

    /// A `Fn`'s children but its body, and its data, lowering its
    /// parameters.
    fn signature_layout(&mut self, sig: &Signature) -> Option<(Vec<NodeId>, u32)> {
        let mut children = sig.type_params.clone();
        children.extend(self.lower_params(&sig.params)?);
        children.extend(sig.out_type);
        children.extend(&sig.supers);
        let data = sig.params.len() as u32
            | ((sig.supers.len() as u32) << FN_SUPERS_SHIFT)
            | ((sig.type_params.len() as u32) << TYPE_PARAMS_SHIFT)
            | if sig.out_type.is_some() {
                FN_OUT_TYPE
            } else {
                0
            };
        Some((children, data))
    }

    fn lower_params(&mut self, params: &[NodeId]) -> Option<Vec<NodeId>> {
        params.iter().map(|&p| self.lower_param(p)).collect()
    }

    /// A parameter, as `(Block \_complexArg_ x \type Int \default 1)`,
    /// a declaration, or either with decorators or doc comments, becomes a
    /// `Let`. Decorators here are read by their text: only `var` matters.
    pub(crate) fn lower_param(&mut self, param: NodeId) -> Option<NodeId> {
        let tree = &self.lib.tree;
        let children = tree.children(param).to_vec();
        match tree.kind(param) {
            NodeKind::Block
                if children
                    .first()
                    .is_some_and(|&c| self.symbol_text(c) == Some("_complexArg_")) =>
            {
                let first = *children.get(1)?;
                match self.lib.tree.kind(first) {
                    NodeKind::RightName => {
                        let name = self.lib.name(first).unwrap();
                        let left = self.lib.add_name(
                            NodeKind::LeftName,
                            name,
                            self.lib.tree.span(first),
                            first,
                        );
                        let (ty, init) = self.pairs(&children[2..], "default")?;
                        let span = self.lib.tree.span(param);
                        Some(self.make_let(left, ty, init, 0, span, param))
                    }
                    NodeKind::Decl if children.len() == 2 => self.lower_param(first),
                    _ => None,
                }
            }
            NodeKind::Decl => {
                self.lower_decl(param);
                (self.lib.tree.kind(param) == NodeKind::Let).then_some(param)
            }
            NodeKind::RightName => {
                let name = self.lib.name(param).unwrap();
                let left =
                    self.lib
                        .add_name(NodeKind::LeftName, name, self.lib.tree.span(param), param);
                Some(self.make_let(left, None, None, 0, self.lib.tree.span(param), param))
            }
            NodeKind::Call => {
                let callee = *children.first()?;
                if let Some(Value::Builtin(SyntaxBuiltin::DocComment)) = self.lib.value(callee) {
                    return self.lower_param(*children.get(1)?);
                }
                let decorator = self.lib.name(callee).map(|n| self.lib.names.get(n));
                match (decorator, &children[1..]) {
                    (Some("@"), [what, target]) => {
                        let var = self
                            .lib
                            .name(*what)
                            .is_some_and(|n| self.lib.names.get(n) == "var");
                        let lowered = self.lower_param(*target)?;
                        if var {
                            let data = self.lib.tree.data(lowered) | LET_VAR;
                            let parts = self.lib.tree.children(lowered).to_vec();
                            self.rewrite(lowered, NodeKind::Let, data, &parts);
                        }
                        Some(lowered)
                    }
                    _ => None,
                }
            }
            _ => None,
        }
    }

    /// Reads `\type T` and `\init V` (or `default_key`) pairs.
    fn pairs(&self, pairs: &[NodeId], init_key: &str) -> Option<(Option<NodeId>, Option<NodeId>)> {
        let (mut ty, mut init) = (None, None);
        for pair in pairs.chunks(2) {
            let [key, value] = *pair else { return None };
            match self.symbol_text(key)? {
                "type" => ty = Some(value),
                // A parameter declared as `var x = 1` has a `\default`.
                k if k == init_key || k == "default" => init = Some(value),
                _ => return None,
            }
        }
        Some((ty, init))
    }

    pub(crate) fn make_let(
        &mut self,
        name: NodeId,
        ty: Option<NodeId>,
        init: Option<NodeId>,
        flags: u32,
        span: Span,
        like: NodeId,
    ) -> NodeId {
        let (children, data) = Self::let_layout(name, ty, init, flags);
        self.lib.add(NodeKind::Let, span, data, &children, like)
    }

    pub(crate) fn let_layout(
        name: NodeId,
        ty: Option<NodeId>,
        init: Option<NodeId>,
        flags: u32,
    ) -> (Vec<NodeId>, u32) {
        let mut children = vec![name];
        let mut data = flags;
        if let Some(t) = ty {
            children.push(t);
            data |= LET_TYPE;
        }
        if let Some(i) = init {
            children.push(i);
            data |= LET_INIT;
        }
        (children, data)
    }

    /// A grammar declaration, `^x \type T \init v`, becomes a `Let`, or an
    /// error.
    pub(crate) fn lower_decl(&mut self, decl: NodeId) {
        let children = self.lib.tree.children(decl).to_vec();
        let lowered = match children.split_first() {
            Some((&name, pairs)) if self.lib.tree.kind(name) == NodeKind::LeftName => self
                .pairs(pairs, "init")
                .map(|(ty, init)| Self::let_layout(name, ty, init, 0)),
            _ => None,
        };
        match lowered {
            Some((children, data)) => self.rewrite(decl, NodeKind::Let, data, &children),
            None => self.error(decl, Message::MalformedDeclaration),
        }
    }

    /// A destructuring declaration, `let { a, b as c, d is T } = x`, as the
    /// statements it stands for, as Kotlin's `flattenMultiInit` does: `let
    /// t = x; let a = t.a; let c = t.b; let d: T = t.d`, with no temporary
    /// when `x` is a name. `None` for any other declaration; a malformed one
    /// becomes an error.
    pub(crate) fn destructure(&mut self, decl: NodeId) -> Option<Vec<NodeId>> {
        self.destructured_init(decl)?;
        let (each, spread, init) = self.destructured(decl)?;
        if let Some(spread) = spread {
            // `...` is only for imports; the rest still declares.
            let span = self.lib.tree.span(spread);
            self.lib
                .report(Level::Error, Message::WildcardWithoutImport, span);
        }
        let span = self.lib.tree.span(decl);
        let (mut statements, t) = self.subject(init, "destructured", decl);
        for (name, target, ty) in each {
            let at = self.lib.tree.span(target);
            let dot = self.lib.names.intern(".");
            let dot = self.lib.add_name(NodeKind::BuiltinName, dot, at, decl);
            let object = self.lib.add_name(NodeKind::RightName, t, at, decl);
            let key = self.lib.add_value(Value::Symbol(name), at, decl);
            let value = self
                .lib
                .add(NodeKind::Call, at, 0, &[dot, object, key], decl);
            statements.push(self.make_let(target, ty, Some(value), 0, span, decl));
        }
        Some(statements)
    }

    /// Several declarations in one statement, `@var let a = 1, b;` (as
    /// `@(var, ,(Decl a, Decl b))`), as one statement each, `@var let a =
    /// 1; @var let b`, as Kotlin's `flattenMultiDeclarations` does: each
    /// gets a copy of the decorators. `None` for any other statement.
    /// Kotlin also moves complex decorator arguments into temporaries, to
    /// evaluate them once; they're copied here, as decorators other than
    /// `var` and `export` are ignored so far.
    pub(crate) fn flatten_declarations(&mut self, statement: NodeId) -> Option<Vec<NodeId>> {
        let (path, decorated) = self.decorators(statement);
        let tree = &self.lib.tree;
        let (&comma, decls) = tree
            .children(decorated)
            .split_first()
            .filter(|_| tree.kind(decorated) == NodeKind::Call)?;
        let is_comma = matches!(
            self.lib.value(comma),
            Some(Value::Builtin(SyntaxBuiltin::Comma))
        );
        if !is_comma || decls.is_empty() || decls.iter().any(|&d| tree.kind(d) != NodeKind::Decl) {
            return None;
        }
        let decls = decls.to_vec();
        Some(
            decls
                .into_iter()
                .map(|decl| self.redecorate(&path, decl))
                .collect(),
        )
    }

    /// A decorated destructuring declaration, `@var let { a, b } = x`, as
    /// the statements it stands for, each with a copy of the decorators,
    /// as Kotlin's `flattenMultiInit` and then `flattenMultiDeclarations`
    /// make it: `@var let t = x; @var let a = t.a; @var let b = t.b`.
    /// `None` for any other statement, and for imports.
    pub(crate) fn destructure_decorated(&mut self, statement: NodeId) -> Option<Vec<NodeId>> {
        let (path, decl) = self.decorators(statement);
        if path.is_empty() || self.lib.tree.kind(decl) != NodeKind::Decl {
            return None;
        }
        let init = self.destructured_init(decl)?;
        let is_import = self.lib.tree.kind(init) == NodeKind::Call
            && self.lib.tree.children(init).first().is_some_and(|&c| {
                self.lib
                    .name(c)
                    .is_some_and(|n| self.lib.names.get(n) == "import")
            });
        if is_import {
            return None;
        }
        let statements = self.destructure(decl)?;
        Some(
            statements
                .into_iter()
                .map(|s| self.redecorate(&path, s))
                .collect(),
        )
    }

    /// The decorator calls around a statement, `@a @b decl`, outermost
    /// first, and what they decorate.
    fn decorators(&self, statement: NodeId) -> (Vec<NodeId>, NodeId) {
        let tree = &self.lib.tree;
        let mut path = Vec::new();
        let mut decorated = statement;
        while tree.kind(decorated) == NodeKind::Call && tree.children(decorated).len() >= 3 && {
            let callee = tree.children(decorated)[0];
            tree.kind(callee) == NodeKind::RightName
                && self
                    .lib
                    .name(callee)
                    .is_some_and(|n| self.lib.names.get(n) == "@")
        } {
            path.push(decorated);
            decorated = *tree.children(decorated).last().unwrap();
        }
        (path, decorated)
    }

    /// A copy of the decorator calls in `path`, each around the next,
    /// around `decl`.
    fn redecorate(&mut self, path: &[NodeId], decl: NodeId) -> NodeId {
        let Some((&outer, rest)) = path.split_first() else {
            return decl;
        };
        let inner = self.redecorate(rest, decl);
        let children = self.lib.tree.children(outer).to_vec();
        let (&_, parts) = children.split_last().unwrap();
        let mut copied: Vec<NodeId> = parts.iter().map(|&part| self.lib.copy(part)).collect();
        copied.push(inner);
        // Ending at `decl`'s end, not the statement's, so each declaration
        // is visible to the next: `var i = 0, next = i`, as in std's json.
        let span = Span {
            start: self.lib.tree.span(outer).start,
            end: self.lib.tree.span(decl).end,
        };
        self.lib.add(
            NodeKind::Call,
            span,
            self.lib.tree.data(outer),
            &copied,
            outer,
        )
    }

    /// A destructuring declaration's value, if it is one, which may still
    /// be malformed.
    pub(crate) fn destructured_init(&self, decl: NodeId) -> Option<NodeId> {
        let tree = &self.lib.tree;
        let (&targets, pairs) = tree.children(decl).split_first()?;
        let is_curlies = tree.kind(targets) == NodeKind::Call
            && tree.children(targets).first().is_some_and(|&c| {
                tree.kind(c) == NodeKind::BuiltinName
                    && self
                        .lib
                        .name(c)
                        .is_some_and(|n| self.lib.names.get(n) == "{}")
            });
        match *pairs {
            [key, value] if is_curlies && self.symbol_text(key) == Some("init") => Some(value),
            _ => None,
        }
    }

    /// A destructuring declaration's targets, each its source name, the
    /// name it's declared as, and its type; its `...`, if any; and its
    /// value. `None` if it's malformed, and then it's an error. A second
    /// `as` is reported, and the first one kept, as in Kotlin Temper's
    /// `walkDestructuring`.
    pub(crate) fn destructured(
        &mut self,
        decl: NodeId,
    ) -> Option<(Vec<Target>, Option<NodeId>, NodeId)> {
        let tree = &self.lib.tree;
        let (&targets, pairs) = tree.children(decl).split_first()?;
        let parts = tree.children(targets)[1..].to_vec();
        let init = match *pairs {
            [key, value] if self.symbol_text(key) == Some("init") => Some(value),
            _ => None,
        };
        // Each target: its source name, the name it's declared as, and its type.
        let mut each: Vec<Target> = Vec::new();
        let mut rest = &parts[..];
        let mut ok = init.is_some();
        let mut spread = None;
        while let (true, Some((&source, more))) = (ok, rest.split_first()) {
            rest = more;
            if self.symbol_text(source) == Some("...") && spread.is_none() {
                spread = Some(source);
                continue;
            }
            let Some(name) = self
                .lib
                .name(source)
                .filter(|_| self.lib.tree.kind(source) == NodeKind::LeftName)
            else {
                ok = false;
                break;
            };
            let (mut target, mut ty) = (source, None);
            while let [key, value, more @ ..] = rest {
                match self.symbol_text(*key) {
                    Some("as") if self.lib.tree.kind(*value) == NodeKind::LeftName => {
                        if target == source {
                            target = *value;
                        } else {
                            let span = self.lib.tree.span(*key);
                            self.lib
                                .report(Level::Error, Message::MultipleRenames, span);
                        }
                    }
                    Some("type") if ty.is_none() => ty = Some(*value),
                    Some("...") => break,
                    // Other metadata, a second rename, nested patterns.
                    Some(_) => ok = false,
                    None => break,
                }
                rest = more;
            }
            each.push((name, target, ty));
        }
        let (true, Some(init)) = (ok, init) else {
            self.error(decl, Message::MalformedDeclaration);
            return None;
        };
        Some((each, spread, init))
    }

    /// `@var decl` makes a `var`, `@export decl` marks it for declaring
    /// as exported, and `@connected` marks a function that may have no
    /// body; other decorators are ignored for now.
    /// `auto("==")`.
    fn is_auto_eq(&self, decorator: NodeId) -> bool {
        match *self.lib.tree.children(decorator) {
            [callee, arg] if self.lib.tree.kind(decorator) == NodeKind::Call => {
                self.lib
                    .name(callee)
                    .is_some_and(|n| self.lib.names.get(n) == "auto")
                    && matches!(self.lib.value(arg), Some(Value::String(s)) if &**s == "==")
            }
            _ => false,
        }
    }

    fn expand_decorate(&mut self, call: NodeId, args: &[NodeId]) -> bool {
        let [decorator, target] = *args else {
            return false;
        };
        if self
            .lib
            .name(decorator)
            .is_some_and(|n| self.lib.names.get(n) == "export")
        {
            // The target becomes this node, so the mark stays with it.
            self.exports.insert(call);
        }
        if self.is_auto_eq(decorator) {
            self.auto_eq.insert(call, decorator);
        }
        if (self.lib.name(decorator)).is_some_and(|n| self.lib.names.get(n) == "fun") {
            self.fun_interfaces.insert(call);
        }
        if (self.lib.name(decorator)).is_some_and(|n| self.lib.names.get(n) == "sealed") {
            self.sealed.insert(call);
        }
        // `@keep`, which keeps a declaration that only host code uses, and
        // `@keepTest`, only tests' host code; the `Let` or `Class` it
        // becomes keeps this node's id.
        match (self.lib.name(decorator)).map(|n| self.lib.names.get(n)) {
            Some("keep") => _ = self.lib.kept.insert(call),
            Some("keepTest") => _ = self.lib.kept_for_tests.insert(call),
            _ => {}
        }
        if (self.lib.name(decorator)).is_some_and(|n| self.lib.names.get(n) == "json") {
            self.json_decorated.insert(call);
            // Where generated members are spanned, as `@auto`'s.
            let at = self.lib.tree.span(call).start;
            self.json.at.entry(call).or_insert(at);
        }
        self.json_extra(call, decorator);
        // `@connected` or `@connected(["cpp", "rust"])`.
        let callee = match self.lib.tree.kind(decorator) {
            NodeKind::Call => self.lib.tree.children(decorator).first().copied(),
            _ => Some(decorator),
        };
        if callee
            .and_then(|c| self.lib.name(c))
            .is_some_and(|n| self.lib.names.get(n) == "connected")
        {
            self.connected.insert(call);
            // A list of backends, as `["cpp", "rust"]`; anything else,
            // as a key, `@connected("X")`, is an error.
            let args = match self.lib.tree.kind(decorator) {
                NodeKind::Call => &self.lib.tree.children(decorator)[1..],
                _ => &[],
            };
            if let [arg] = *args {
                if self.is_string_list(arg) {
                    let listed = self.strings_in(arg);
                    self.connected_lists.insert(call, listed);
                } else {
                    // Reported, and connected as if without a list, as
                    // Kotlin's `ConnectedDecorator` does.
                    let span = self.lib.tree.span(arg);
                    let text = self.lib.source_text(span).to_string();
                    let message = Message::ConnectedBackendsNotNames(text);
                    self.lib.report(Level::Error, message, span);
                }
            }
        }
        if let Some(extension) = self.static_extension_of(decorator) {
            self.static_extension_marks.insert(call, extension);
        }
        if let Some(name) = self.extension_of(decorator) {
            self.extension_marks.insert(call, name);
        }
        let var = self
            .lib
            .name(decorator)
            .is_some_and(|n| self.lib.names.get(n) == "var");
        if var {
            if self.lib.tree.kind(target) == NodeKind::Decl {
                self.lower_decl(target);
            }
            if self.lib.tree.kind(target) != NodeKind::Let {
                return false;
            }
            let data = self.lib.tree.data(target) | LET_VAR;
            let parts = self.lib.tree.children(target).to_vec();
            self.rewrite(call, NodeKind::Let, data, &parts);
        } else {
            self.become_node(call, target);
        }
        true
    }

    /// The parts of `if`: condition, then and else, with `else if` as a
    /// nested `If`.
    fn if_parts(&mut self, args: &[NodeId]) -> Option<[NodeId; 3]> {
        let [condition, then, rest @ ..] = args else {
            return None;
        };
        let then = self.block_of(*then)?;
        let otherwise = match rest {
            [] => {
                let at = self.lib.tree.span(then).end;
                self.lib
                    .add(NodeKind::Block, Span { start: at, end: at }, 0, &[], then)
            }
            [key, delegate] => {
                let passed = self.delegated(*delegate)?;
                match self.symbol_text(*key)? {
                    "else" => match *passed {
                        [body] => self.block_of(body)?,
                        _ => return None,
                    },
                    "else_if" => {
                        let parts = self.if_parts(&passed)?;
                        let span = self.lib.tree.span(*delegate);
                        self.lib.add(NodeKind::If, span, 0, &parts, *delegate)
                    }
                    _ => return None,
                }
            }
            _ => return None,
        };
        Some([*condition, then, otherwise])
    }

    /// What a call join's delegate, `fn (f#0) { f#0(args...) }`, passes on.
    fn delegated(&self, fun: NodeId) -> Option<Vec<NodeId>> {
        let tree = &self.lib.tree;
        let [decl, call] = *tree.children(fun) else {
            return None;
        };
        if tree.kind(fun) != NodeKind::Fun
            || tree.kind(decl) != NodeKind::Decl
            || tree.kind(call) != NodeKind::Call
        {
            return None;
        }
        let param = self.lib.name(*tree.children(decl).first()?)?;
        let (&callee, args) = tree.children(call).split_first()?;
        (self.lib.name(callee) == Some(param) && self.lib.names.is_temporary(param))
            .then(|| args.to_vec())
    }

    /// The block of a block argument without parameters, `{ ... }`.
    pub(crate) fn block_of(&self, fun: NodeId) -> Option<NodeId> {
        let tree = &self.lib.tree;
        match *tree.children(fun) {
            [block] if tree.kind(fun) == NodeKind::Fun && tree.kind(block) == NodeKind::Block => {
                Some(block)
            }
            _ => None,
        }
    }

    /// `while (c) { body }` is `Loop(If(c, body, Break))`.
    fn expand_while(&mut self, call: NodeId, args: &[NodeId]) -> bool {
        let [condition, body] = *args else {
            return false;
        };
        let Some(body) = self.block_of(body) else {
            return false;
        };
        let span = self.lib.tree.span(call);
        let exit = self.lib.add(NodeKind::Break, span, call.0, &[], call);
        let check = self
            .lib
            .add(NodeKind::If, span, 0, &[condition, body, exit], call);
        self.rewrite(call, NodeKind::Loop, 0, &[check]);
        true
    }

    /// `do { body } while (c)` is `Loop(body, next: If(c, {}, Break))`: the
    /// body runs once before the check, and `continue` goes to the check,
    /// as in Kotlin Temper's `DoTransform`. The call join passes the
    /// condition on through its delegate, `fn (f#0) { f#0(c) }`.
    fn expand_do_while(&mut self, call: NodeId, body: NodeId, check: NodeId) -> bool {
        let Some(body) = self.block_of(body) else {
            return false;
        };
        let Some([condition]) = self
            .delegated(check)
            .and_then(|c| <[NodeId; 1]>::try_from(c).ok())
        else {
            return false;
        };
        let span = self.lib.tree.span(condition);
        let exit = self.lib.add(NodeKind::Break, span, call.0, &[], call);
        let stay = self.lib.add(NodeKind::Block, span, 0, &[], call);
        let next = self
            .lib
            .add(NodeKind::If, span, 0, &[condition, stay, exit], call);
        self.rewrite(call, NodeKind::Loop, LOOP_NEXT, &[body, next]);
        true
    }

    /// `for (init; c; next) { body }` is `{ init; Loop(If(c, body, Break),
    /// next) }`.
    fn expand_for(&mut self, call: NodeId, args: &[NodeId]) -> bool {
        let Some((&body, pairs)) = args.split_last() else {
            return false;
        };
        let Some(body) = self.block_of(body) else {
            return false;
        };
        if let [of] = *pairs {
            if let Some([decl, group]) = self.of_parts(of) {
                return self.expand_for_of(call, decl, group, body);
            }
        }
        let (mut init, mut condition, mut next) = (None, None, None);
        for pair in pairs.chunks(2) {
            let [key, value] = *pair else { return false };
            match self.symbol_text(key) {
                Some("__flowInit") => init = Some(value),
                Some("cond") => condition = Some(value),
                Some("incr") => next = Some(value),
                _ => {
                    self.error(call, Message::Unsupported("this kind of for loop".into()));
                    return true;
                }
            }
        }
        let span = self.lib.tree.span(call);
        let looping = self.lib.add(NodeKind::Loop, span, 0, &[], call);
        let pass = match condition {
            Some(c) => {
                let exit = self.lib.add(NodeKind::Break, span, looping.0, &[], call);
                self.lib.add(NodeKind::If, span, 0, &[c, body, exit], call)
            }
            None => body,
        };
        let mut parts = vec![pass];
        parts.extend(next);
        self.rewrite(
            looping,
            NodeKind::Loop,
            if next.is_some() { LOOP_NEXT } else { 0 },
            &parts,
        );
        let mut statements: Vec<NodeId> = init.into_iter().collect();
        statements.push(looping);
        self.rewrite(call, NodeKind::Block, 0, &statements);
        true
    }

    /// `x of xs` as its two sides, before or after `desugarOperation`.
    fn of_parts(&self, node: NodeId) -> Option<[NodeId; 2]> {
        let tree = &self.lib.tree;
        if tree.kind(node) != NodeKind::Call {
            return None;
        }
        let children = tree.children(node);
        let parts = match *children {
            [op, a, b] if self.lib.name(op).is_some() => (op, a, b),
            [f, op, a, b]
                if self.lib.value(f) == Some(&Value::Builtin(SyntaxBuiltin::DesugarOperation)) =>
            {
                (op, a, b)
            }
            _ => return None,
        };
        let is_of = self
            .lib
            .name(parts.0)
            .is_some_and(|n| self.lib.names.get(n) == "of");
        is_of.then_some([parts.1, parts.2])
    }

    /// `for (let x of xs) { body }` is `Loop({ xs.forEach(fn (x) { body });
    /// Break })`, as Kotlin Temper lowers it to `xs.forEach`; see
    /// [`LOOP_EACH`] and [`FN_LOOP_BODY`].
    fn expand_for_of(&mut self, call: NodeId, decl: NodeId, group: NodeId, body: NodeId) -> bool {
        let span = self.lib.tree.span(call);
        if self.lib.tree.kind(decl) != NodeKind::Decl {
            self.error(call, Message::ExpectedDeclarationForOf);
            return true;
        }
        let Some(param) = self.lower_param(decl) else {
            return false;
        };
        if self.lib.tree.data(param) & LET_INIT != 0 {
            self.error(call, Message::OfDeclarationInitializer);
            return true;
        }
        let each = self
            .lib
            .add(NodeKind::Fn, span, 1 | FN_LOOP_BODY, &[param, body], call);
        let dot = self.lib.names.intern(".");
        let dot = self.lib.add_name(NodeKind::BuiltinName, dot, span, call);
        let key = Value::Symbol(self.lib.names.intern("forEach"));
        let key = self.lib.add_value(key, span, call);
        let member = self
            .lib
            .add(NodeKind::Call, span, 0, &[dot, group, key], call);
        let looped = self.lib.add(NodeKind::Call, span, 0, &[member, each], call);
        let exit = self.lib.add(NodeKind::Break, span, call.0, &[], call);
        let block = self
            .lib
            .add(NodeKind::Block, span, 0, &[looped, exit], call);
        self.rewrite(call, NodeKind::Loop, LOOP_EACH, &[block]);
        true
    }

    fn expand_return(&mut self, call: NodeId, scope: ScopeId, args: &[NodeId]) -> bool {
        let value = match *args {
            [] => self
                .lib
                .add_value(Value::Void, self.lib.tree.span(call), call),
            [value] => value,
            _ => return false,
        };
        match self.enclosing(scope).last() {
            Some(&(ScopeKind::Fn, f)) => self.rewrite(call, NodeKind::Return, f.0, &[value]),
            _ => self.error(call, Message::ReturnOutsideFunction),
        }
        true
    }

    /// `yield(x)`, or a bare `yield`, which yields null as in Kotlin
    /// Temper; or `await(p)`. Whether the function is a generator is
    /// checked when it's lowered to run. A `for … of` body is a function of
    /// its own, so neither can reach the generator from there yet (nor can
    /// they in Kotlin Temper).
    fn expand_pause(
        &mut self,
        call: NodeId,
        scope: ScopeId,
        args: &[NodeId],
        kind: NodeKind,
    ) -> bool {
        let what = if kind == NodeKind::Await {
            "await"
        } else {
            "yield"
        };
        let value = match *args {
            [] if kind == NodeKind::Yield => {
                self.lib
                    .add_value(Value::Null, self.lib.tree.span(call), call)
            }
            [value] => value,
            _ => return false,
        };
        let enclosing = self.enclosing(scope);
        let in_loop_body = enclosing
            .iter()
            .any(|&(kind, _)| kind == ScopeKind::LoopBody);
        match enclosing.last() {
            Some(_) if in_loop_body => self.error(
                call,
                Message::Unsupported(format!("{what} in a for … of body")),
            ),
            Some(&(ScopeKind::Fn, f)) => self.rewrite(call, kind, f.0, &[value]),
            // A REPL input may await at its top level, which runs as an
            // `async` block's body does, its root standing for the
            // function ([`crate::repl`]).
            Some(&(ScopeKind::Module, root))
                if kind == NodeKind::Await
                    && (self.scopes.of_node(root))
                        .is_some_and(|s| self.repl_scopes.contains(&s)) =>
            {
                self.rewrite(call, kind, root.0, &[value])
            }
            _ => self.error(call, Message::YieldingOutsideGeneratorFn(what)),
        }
        true
    }

    /// `break` and `continue`, with a label or to the nearest loop.
    fn expand_jump(
        &mut self,
        call: NodeId,
        scope: ScopeId,
        args: &[NodeId],
        is_break: bool,
    ) -> bool {
        let label = match *args {
            [] => None,
            [key, name] if self.symbol_text(key) == Some("label") => Some(self.lib.name(name)),
            _ => return false,
        };
        let target =
            self.enclosing(scope)
                .into_iter()
                .find_map(|(kind, node)| match (kind, label) {
                    (ScopeKind::Loop, None) => Some(node),
                    (ScopeKind::LoopBody, None) if !is_break => Some(node),
                    (ScopeKind::Label(l), Some(Some(name))) if l == name => {
                        if is_break {
                            Some(node)
                        } else {
                            self.labeled_loop(node)
                        }
                    }
                    _ => None,
                });
        let (kind, what) = if is_break {
            (NodeKind::Break, "break")
        } else {
            (NodeKind::Continue, "continue")
        };
        match target {
            Some(target) => self.rewrite(call, kind, target.0, &[]),
            None => self.error(call, Message::NoJumpTarget(what)),
        }
        true
    }

    /// The loop a label names, for `continue`: its statement, or what that
    /// expanded to, as a `for`'s block ending in its loop, or for `for …
    /// of`, its body `Fn`.
    fn labeled_loop(&self, block: NodeId) -> Option<NodeId> {
        let tree = &self.lib.tree;
        let mut at = *tree.children(block).first()?;
        loop {
            match tree.kind(at) {
                NodeKind::Loop if tree.data(at) & LOOP_EACH != 0 => {
                    let looped = *tree.children(*tree.children(at).first()?).first()?;
                    return tree.children(looped).last().copied();
                }
                NodeKind::Loop => return Some(at),
                NodeKind::Block => at = *tree.children(at).last()?,
                _ => return None,
            }
        }
    }

    /// Whether a node is `obj.name`, before or after resolution.
    fn is_dot_call(&self, node: NodeId) -> bool {
        self.is_builtin_call(node, ".")
    }

    /// Whether a node is `ls[i]`.
    fn is_index_call(&self, node: NodeId) -> bool {
        self.is_builtin_call(node, "[]")
    }

    /// Whether a node is `a?.name`.
    pub(crate) fn is_chain_null(&self, node: NodeId) -> bool {
        self.is_builtin_call(node, "?.")
    }

    /// Whether a node is a call of a builtin name, with two arguments.
    fn is_builtin_call(&self, node: NodeId, text: &str) -> bool {
        let tree = &self.lib.tree;
        tree.kind(node) == NodeKind::Call
            && tree.children(node).len() == 3
            && tree.children(node).first().is_some_and(|&c| {
                tree.kind(c) == NodeKind::BuiltinName
                    && self
                        .lib
                        .name(c)
                        .is_some_and(|n| self.lib.names.get(n) == text)
            })
    }

    pub(crate) fn symbol_text(&self, node: NodeId) -> Option<&str> {
        self.lib.symbol(node).map(|s| self.lib.names.get(s))
    }
}
