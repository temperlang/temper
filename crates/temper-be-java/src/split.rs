//! Fitting huge code under the JVM's limits, a pass over a class's
//! output tree before it's printed: a method's code is at most 64 KB, a
//! class's constants at most 65,535, and a string constant at most
//! 65,535 bytes of modified UTF-8. Library data, as conformance tests'
//! thousands of examples, goes past them, and javac says "code too
//! large", "too many constants", or that a string is too long for the
//! constant pool. Code within the limits prints as it was.
//!
//! - **Long strings:** a literal past [`Limits::string`] is
//!   `String.join("", "…", "…")`, of pieces under it; not `"…" + "…"`,
//!   which javac folds back into one constant.
//! - **Big list literals:** in a method (or initializer) estimated past
//!   [`Limits::method`], or a class whose constants are estimated past
//!   [`Limits::constants`], each list literal bigger than
//!   [`Limits::list`] whose items use no locals (data, typically) goes in
//!   parts, each a method of a nested class of its own, so its code and
//!   constants are that class's: `Core.<T>listOfParts(Part1.list(),
//!   Part2.list())`, or `Part1.list()` for one part.
//! - **A big static initializer:** still past them, a module's static
//!   initializer runs its statements in groups, each the `run()` of a
//!   nested class, `Init1.run();`, its top-level locals then static
//!   fields, and its fields no longer `final`, as the nested classes
//!   assign them.
//!
//! Sizes are estimates of bytecode from the tree ([`cost`]), generous so
//! the real code is smaller. A function body that's too big by its own
//! statements stays as it is: its locals can't become fields.

use crate::tree::{self, Class, Expr, Field, Stmt, TestClass, TypeDecl};
use std::collections::HashSet;
use temper_tmpl::chunks;

/// What the pass keeps code under, each an estimate ([`cost`]) but
/// [`Limits::string`], in bytes of modified UTF-8.
#[derive(Clone, Copy, Debug)]
pub struct Limits {
    /// A method's or initializer's code, past which its big lists split.
    pub method: usize,
    /// A class's constants, past which its big lists split.
    pub constants: usize,
    /// A list literal that splits, when its method or class needs it,
    /// is bigger than this.
    pub list: usize,
    /// Each part's (a nested class's method's) code.
    pub part: usize,
    /// A string literal's size.
    pub string: usize,
}

/// The JVM's limits, with room to spare for the estimates.
pub const LIMITS: Limits = Limits {
    method: 40_000,
    constants: 30_000,
    list: 2_000,
    part: 16_000,
    string: 65_535,
};

/// Fits a module's class under `limits`.
pub fn class(c: &mut Class, limits: &Limits) {
    let bodies = c.methods.iter().map(|m| &m.body[..]).chain([&c.init[..]]);
    if fits(bodies, [], limits) {
        return;
    }
    let mut s = Splitter::new(limits, &tree::class(c), &c.imports);
    let fields: Vec<String> = c.fields.iter().map(|f| f.name.clone()).collect();
    let mut bodies: Vec<Body> = Vec::new();
    for m in &mut c.methods {
        bodies.push(Body::of(&mut m.body, &m.params, &m.result));
    }
    let init = bodies.len();
    bodies.push(Body::of(&mut c.init, &[], ""));
    s.fit(&mut bodies);
    let init_cost = bodies[init].cost();
    let over = init_cost > limits.method || s.constants(&bodies) > limits.constants;
    drop(bodies);
    if over {
        let methods: Vec<String> = c.methods.iter().map(|m| m.name.clone()).collect();
        if let Some(new_fields) = s.split_init(&mut c.init, &fields, &methods) {
            for f in &mut c.fields {
                f.is_final = false;
            }
            c.fields.extend(new_fields);
        }
    }
    s.finish(&mut c.helpers);
}

/// Fits a module's test class under `limits`.
pub fn test_class(c: &mut TestClass, limits: &Limits) {
    let bodies = (c.statics.methods.iter().map(|m| &m.body[..]))
        .chain([&c.statics.init[..]])
        .chain(c.tests.iter().map(|(_, b)| &b[..]));
    if fits(bodies, [], limits) {
        return;
    }
    let text = tree::test_class(c);
    let statics = &mut c.statics;
    let mut s = Splitter::new(limits, &text, &statics.imports);
    let mut bodies: Vec<Body> = Vec::new();
    for m in &mut statics.methods {
        bodies.push(Body::of(&mut m.body, &m.params, &m.result));
    }
    bodies.push(Body::of(&mut statics.init, &[], ""));
    for (_, body) in &mut c.tests {
        bodies.push(Body::of(body, &[], ""));
    }
    s.fit(&mut bodies);
    drop(bodies);
    s.finish(&mut statics.helpers);
}

/// Fits a class's file under `limits`; an interface's, or a generic
/// class's, whose nested classes couldn't use its type parameters, only
/// by its strings.
pub fn type_decl(t: &mut TypeDecl, limits: &Limits) {
    let bodies = t.members.iter().map(|m| &m.body[..]);
    if fits(
        bodies,
        t.fields.iter().filter_map(|f| f.value.as_ref()),
        limits,
    ) {
        return;
    }
    let mut s = Splitter::new(limits, &tree::type_decl(t), &t.imports);
    for f in &mut t.fields {
        if let Some(v) = &mut f.value {
            s.strings(v);
        }
    }
    let mut bodies: Vec<Body> = Vec::new();
    for m in &mut t.members {
        bodies.push(Body::of(&mut m.body, &m.params, &m.result));
    }
    if t.interface || !t.generics.is_empty() {
        // Strings alone.
        for b in &mut bodies {
            for s2 in b.stmts.iter_mut() {
                stmt_exprs(s2, &mut |e| s.strings(e));
            }
        }
        return;
    }
    s.fit(&mut bodies);
    drop(bodies);
    s.finish(&mut t.helpers);
}

/// Whether code, `bodies` and `values` (fields' values), is under
/// `limits` as it is, as nearly all is.
fn fits<'a>(
    bodies: impl IntoIterator<Item = &'a [Stmt]>,
    values: impl IntoIterator<Item = &'a Expr>,
    limits: &Limits,
) -> bool {
    let mut long = false;
    let mut pool = Pool::default();
    let mut check = |e: &'a Expr| {
        walk(e, &mut |e| {
            // Each UTF-8 byte is at most two of modified UTF-8 (NUL's).
            long |= matches!(e, Expr::Str(s) if s.len() * 2 > limits.string && utf8_size(s) > limits.string);
        });
        pool.add(e);
    };
    for body in bodies {
        let cost: usize = body.iter().map(stmt_cost).sum();
        if cost > limits.method {
            return false;
        }
        for s in body {
            stmt_exprs_deep(s, &mut check);
        }
    }
    values.into_iter().for_each(&mut check);
    !long && pool.size() <= limits.constants
}

/// A class's constant pool, estimated, generously: each distinct string
/// and wide number in two entries, each distinct member or class named
/// in five (a reference, its name and type, its class, and their
/// names), as javac shares them.
#[derive(Default)]
struct Pool<'a> {
    strings: HashSet<&'a str>,
    numbers: HashSet<u64>,
    names: HashSet<&'a str>,
}

impl<'a> Pool<'a> {
    fn add(&mut self, e: &'a Expr) {
        walk_ref(e, &mut |e| match e {
            Expr::Str(s) => {
                self.strings.insert(s);
            }
            Expr::Long(n) => {
                self.numbers.insert(*n as u64);
            }
            Expr::Int(n) if !(-32768..=32767).contains(n) => {
                self.numbers.insert(*n as u64);
            }
            Expr::Float(f) => {
                self.numbers.insert(f.to_bits());
            }
            Expr::Name(n) | Expr::Member(_, n) | Expr::New(n, _) | Expr::MethodRef(_, n) => {
                self.names.insert(n);
            }
            _ => {}
        });
    }

    fn size(&self) -> usize {
        2 * (self.strings.len() + self.numbers.len()) + 5 * self.names.len()
    }
}

/// A method's (or initializer's) body, with the names it declares, which
/// a part can't use, and its type parameters, which a nested class can't.
struct Body<'a> {
    stmts: &'a mut Vec<Stmt>,
    locals: HashSet<String>,
    type_vars: Vec<String>,
}

impl<'a> Body<'a> {
    fn of(stmts: &'a mut Vec<Stmt>, params: &[(String, String)], result: &str) -> Body<'a> {
        let mut locals: HashSet<String> = params.iter().map(|(_, n)| n.clone()).collect();
        declared(stmts, &mut locals);
        // A generic method's result starts with its type parameters.
        let type_vars = match result.strip_prefix('<') {
            Some(rest) => words(&rest[..rest.find("> ").unwrap_or(rest.len())]),
            None => Vec::new(),
        };
        Body {
            stmts,
            locals,
            type_vars,
        }
    }

    fn cost(&self) -> usize {
        self.stmts.iter().map(stmt_cost).sum()
    }
}

struct Splitter<'a> {
    limits: &'a Limits,
    /// Every word of the class's text, which a nested class's name
    /// mustn't be.
    taken: HashSet<String>,
    /// Nested classes made, as their text.
    helpers: Vec<String>,
    /// How to name core's class and `List`, imported or not.
    core: &'static str,
    list: &'static str,
}

impl<'a> Splitter<'a> {
    fn new(limits: &'a Limits, text: &str, imports: &[String]) -> Splitter<'a> {
        let imported = |name: &str| imports.iter().any(|i| i == name);
        let simple = |name: &str| {
            let tail = &name[name.rfind('.').map_or(0, |i| i + 1)..];
            imported(name) || !imports.iter().any(|i| i.ends_with(&format!(".{tail}")))
        };
        Splitter {
            limits,
            taken: words(text).into_iter().collect(),
            helpers: Vec::new(),
            core: if imported("temper.core.Core") {
                "Core"
            } else {
                "temper.core.Core"
            },
            list: if imported("java.util.List") && simple("java.util.List") {
                "List"
            } else {
                "java.util.List"
            },
        }
    }

    /// A nested class's name from `stem`, numbered, that nothing in the
    /// class is.
    fn fresh(&mut self, stem: &str) -> String {
        let name = (1..)
            .map(|n| format!("{stem}{n}"))
            .find(|n| !self.taken.contains(n))
            .unwrap_or_default();
        self.taken.insert(name.clone());
        name
    }

    /// Splits long strings everywhere, and big lists where a body or the
    /// class is past its limits.
    fn fit(&mut self, bodies: &mut [Body]) {
        for b in bodies.iter_mut() {
            for s in b.stmts.iter_mut() {
                stmt_exprs(s, &mut |e| self.strings(e));
            }
        }
        let class_over = self.constants(bodies) > self.limits.constants;
        for b in bodies.iter_mut() {
            if class_over || b.cost() > self.limits.method {
                let (locals, type_vars) = (&b.locals, &b.type_vars);
                for s in b.stmts.iter_mut() {
                    stmt_exprs(s, &mut |e| self.lists(e, locals, type_vars));
                }
            }
        }
    }

    /// The class's constants, estimated ([`Pool`]).
    fn constants(&self, bodies: &[Body]) -> usize {
        let mut pool = Pool::default();
        for b in bodies {
            for s in b.stmts.iter() {
                stmt_exprs_deep(s, &mut |e| pool.add(e));
            }
        }
        pool.size()
    }

    /// `e` with each string literal past the limit as pieces.
    fn strings(&mut self, e: &mut Expr) {
        walk_mut(e, &mut |e| {
            if let Expr::Str(text) = e {
                if utf8_size(text) > self.limits.string {
                    let mut args = vec![Expr::Str(String::new())];
                    for piece in chunks::split_text(text, self.limits.string, modified_utf8) {
                        args.push(Expr::Str(piece.to_string()));
                    }
                    *e = Expr::name("String").method("join", args);
                }
            }
        });
    }

    /// `e` with each list literal past the limit whose items use no
    /// locals in parts, innermost first.
    fn lists(&mut self, e: &mut Expr, locals: &HashSet<String>, type_vars: &[String]) {
        match e {
            Expr::Lambda(..) => return,
            _ => kids_mut(e, &mut |k| self.lists(k, locals, type_vars)),
        }
        let Some((ty, items)) = list_literal(e) else {
            return;
        };
        if items.len() < 2
            || expr_cost(e) <= self.limits.list
            || words(&ty).iter().any(|w| type_vars.contains(w))
            || !items.iter().all(|i| closed(i, locals))
        {
            return;
        }
        let Expr::Call(f, items) = e else {
            return;
        };
        let costs: Vec<usize> = items.iter().map(expr_cost).collect();
        let mut rest = std::mem::take(items).into_iter();
        let mut parts = Vec::new();
        for n in chunks::groups(&costs, self.limits.part) {
            let part: Vec<Expr> = rest.by_ref().take(n).collect();
            let name = self.fresh("Part");
            let value = Expr::Call(f.clone(), part);
            let body = vec![Stmt::Return(Some(value))];
            let result = format!("{}<{ty}>", self.list);
            self.helpers.push(nested(&name, &result, "list", &body));
            parts.push(Expr::name(name).method("list", Vec::new()));
        }
        *e = if parts.len() == 1 {
            parts.pop().unwrap_or(Expr::Bool(false))
        } else {
            Expr::name(self.core).method(format!("<{ty}>listOfParts"), parts)
        };
    }

    /// The static initializer `init` in groups, each a nested class's
    /// `run()`, with its top-level locals as private static fields, which
    /// it gives; `None` where it can't: where it declares a local class
    /// or lambda, or a local of a field's or method's name.
    fn split_init(
        &mut self,
        init: &mut Vec<Stmt>,
        fields: &[String],
        methods: &[String],
    ) -> Option<Vec<Field>> {
        let mut new_fields = Vec::new();
        for s in init.iter() {
            match s {
                Stmt::LambdaLocal { .. } | Stmt::LocalClass { .. } | Stmt::GroupClass { .. } => {
                    return None;
                }
                Stmt::Local { ty, name, .. } => {
                    if fields.contains(name) || methods.contains(name) {
                        return None;
                    }
                    new_fields.push(Field {
                        public: false,
                        is_final: false,
                        volatile: false,
                        ty: ty.clone(),
                        name: name.clone(),
                    });
                }
                _ => {}
            }
        }
        let stmts: Vec<Stmt> = std::mem::take(init)
            .into_iter()
            .filter_map(|s| match s {
                Stmt::Local { name, value, .. } => value.map(|value| Stmt::Assign {
                    target: Expr::Name(name),
                    value,
                }),
                s => Some(s),
            })
            .collect();
        let costs: Vec<usize> = stmts.iter().map(stmt_cost).collect();
        let mut rest = stmts.into_iter();
        for n in chunks::groups(&costs, self.limits.part) {
            let body: Vec<Stmt> = rest.by_ref().take(n).collect();
            let name = self.fresh("Init");
            self.helpers.push(nested(&name, "void", "run", &body));
            init.push(Stmt::Expr(Expr::name(name).method("run", Vec::new())));
        }
        Some(new_fields)
    }

    fn finish(self, helpers: &mut Vec<String>) {
        helpers.extend(self.helpers);
    }
}

/// A nested class of one static method, `name`'s `method`, as a member's
/// text.
fn nested(name: &str, result: &str, method: &str, body: &[Stmt]) -> String {
    let mut out =
        format!("    private static final class {name} {{\n        static {result} {method}() ");
    tree::block(&mut out, body, 2);
    out.push_str("\n    }\n");
    out
}

/// A list literal's item type and items: `List.<T>of(…)` or
/// `Core.<T>listOf(…)`, with its type written.
fn list_literal(e: &Expr) -> Option<(String, &[Expr])> {
    let Expr::Call(f, items) = e else {
        return None;
    };
    let Expr::Member(class, method) = &**f else {
        return None;
    };
    let Expr::Name(class) = &**class else {
        return None;
    };
    let ty = match (class.as_str(), method.strip_prefix('<')) {
        ("List" | "java.util.List", Some(rest)) => rest.strip_suffix(">of")?,
        ("Core" | "temper.core.Core", Some(rest)) => rest.strip_suffix(">listOf")?,
        _ => return None,
    };
    Some((ty.to_string(), items))
}

/// Whether `e` uses none of `locals`, nor `this`, nor a lambda, and so
/// can be a nested class's.
fn closed(e: &Expr, locals: &HashSet<String>) -> bool {
    let mut ok = true;
    walk(e, &mut |e| match e {
        Expr::Name(n) if locals.contains(n) => ok = false,
        Expr::New(class, _) if words(class).iter().any(|w| locals.contains(w)) => ok = false,
        Expr::This | Expr::Lambda(..) | Expr::MethodRef(..) => ok = false,
        _ => {}
    });
    ok
}

/// The names `stmts` declare, at any depth: locals, lambdas, local
/// classes, and a `catch`'s variable.
fn declared(stmts: &[Stmt], out: &mut HashSet<String>) {
    for s in stmts {
        match s {
            Stmt::Local { name, .. } | Stmt::LocalClass { name, .. } => {
                out.insert(name.clone());
            }
            Stmt::LambdaLocal {
                name, params, body, ..
            } => {
                out.insert(name.clone());
                out.extend(params.iter().cloned());
                declared(body, out);
            }
            Stmt::GroupClass { name, methods } => {
                out.insert(name.clone());
                for m in methods {
                    out.extend(m.params.iter().map(|(_, n)| n.clone()));
                    declared(&m.body, out);
                }
            }
            Stmt::TryCatch { body, var, handler } => {
                out.insert(var.clone());
                declared(body, out);
                declared(handler, out);
            }
            _ => {
                for_stmts(s, &mut |body| declared(body, out));
            }
        }
    }
}

/// Each identifier in `text`.
fn words(text: &str) -> Vec<String> {
    text.split(|c: char| !(c.is_alphanumeric() || c == '_' || c == '$'))
        .filter(|w| !w.is_empty())
        .map(str::to_string)
        .collect()
}

fn utf8_size(text: &str) -> usize {
    text.chars().map(modified_utf8).sum()
}

/// A character's size in the class file's modified UTF-8: NUL in two
/// bytes, and a supplementary character as two surrogates of three.
fn modified_utf8(c: char) -> usize {
    match c as u32 {
        0 => 2,
        0x01..=0x7f => 1,
        0x80..=0x7ff => 2,
        0x800..=0xffff => 3,
        _ => 6,
    }
}

/// An estimate of the bytecode `e` compiles to, generous: each load,
/// call, and `new` at its most, and 5 bytes an argument, as a varargs
/// array's store takes.
pub fn expr_cost(e: &Expr) -> usize {
    let own = match e {
        Expr::Name(_) | Expr::Str(_) | Expr::Long(_) | Expr::Float(_) => 3,
        Expr::Int(_) => 5,
        Expr::Bool(_) | Expr::This => 1,
        Expr::Call(_, args) => 5 + 5 * args.len(),
        Expr::New(_, args) => 7 + 5 * args.len(),
        Expr::Concat(parts) => 5 + 5 * parts.len(),
        Expr::Member(..) | Expr::Bin(..) | Expr::Unary(..) => 3,
        Expr::Cond { .. } => 6,
        Expr::InstanceOf(..) | Expr::Cast(..) => 3,
        Expr::Lambda(..) | Expr::MethodRef(..) => 5,
    };
    let mut kids = 0;
    match e {
        // A lambda's body is a method of its own.
        Expr::Lambda(..) => {}
        _ => kids_ref(e, &mut |k| kids += expr_cost(k)),
    }
    own + kids
}

/// An estimate of a statement's bytecode, as [`expr_cost`]'s; a
/// `finally` counts twice, as javac copies it.
pub fn stmt_cost(s: &Stmt) -> usize {
    let mut cost = match s {
        Stmt::Try { finally, .. } => finally.iter().map(stmt_cost).sum::<usize>(),
        Stmt::Switch { cases, .. } => 8 * cases.len(),
        _ => 0,
    } + 6;
    stmt_exprs_ref(s, &mut |e| cost += expr_cost(e));
    for_stmts(s, &mut |body| {
        cost += body.iter().map(stmt_cost).sum::<usize>()
    });
    cost
}

/// Calls `f` with each statement list directly in `s`.
fn for_stmts<'a>(s: &'a Stmt, f: &mut impl FnMut(&'a [Stmt])) {
    match s {
        Stmt::Block(body)
        | Stmt::While { body, .. }
        | Stmt::DoWhile { body, .. }
        | Stmt::Synchronized { body, .. } => f(body),
        Stmt::LambdaLocal { body, .. } => f(body),
        Stmt::If { then, els, .. } => {
            f(then);
            f(els);
        }
        Stmt::Switch { cases, .. } => {
            for (_, body) in cases {
                f(body);
            }
        }
        Stmt::Try { body, finally } => {
            f(body);
            f(finally);
        }
        Stmt::TryCatch { body, handler, .. } => {
            f(body);
            f(handler);
        }
        Stmt::GroupClass { methods, .. } => {
            for m in methods {
                f(&m.body);
            }
        }
        _ => {}
    }
}

/// Calls `f` with each expression in `s`, nested statements' too.
fn stmt_exprs(s: &mut Stmt, f: &mut impl FnMut(&mut Expr)) {
    match s {
        Stmt::Expr(e) | Stmt::Throw(e) | Stmt::Return(Some(e)) => f(e),
        Stmt::Local { value, .. } => {
            if let Some(v) = value {
                f(v);
            }
        }
        Stmt::Assign { target, value } => {
            f(target);
            f(value);
        }
        Stmt::If { cond, then, els } => {
            f(cond);
            then.iter_mut().for_each(|s| stmt_exprs(s, f));
            els.iter_mut().for_each(|s| stmt_exprs(s, f));
        }
        Stmt::While { cond, body, .. } | Stmt::DoWhile { cond, body, .. } => {
            f(cond);
            body.iter_mut().for_each(|s| stmt_exprs(s, f));
        }
        Stmt::Switch { scrutinee, cases } => {
            f(scrutinee);
            for (_, body) in cases {
                body.iter_mut().for_each(|s| stmt_exprs(s, f));
            }
        }
        Stmt::Synchronized { lock, body } => {
            f(lock);
            body.iter_mut().for_each(|s| stmt_exprs(s, f));
        }
        Stmt::Block(body) | Stmt::LambdaLocal { body, .. } => {
            body.iter_mut().for_each(|s| stmt_exprs(s, f));
        }
        Stmt::Try { body, finally } => {
            body.iter_mut().for_each(|s| stmt_exprs(s, f));
            finally.iter_mut().for_each(|s| stmt_exprs(s, f));
        }
        Stmt::TryCatch { body, handler, .. } => {
            body.iter_mut().for_each(|s| stmt_exprs(s, f));
            handler.iter_mut().for_each(|s| stmt_exprs(s, f));
        }
        Stmt::GroupClass { methods, .. } => {
            for m in methods {
                m.body.iter_mut().for_each(|s| stmt_exprs(s, f));
            }
        }
        Stmt::LocalClass { .. } | Stmt::Break(_) | Stmt::Continue(_) | Stmt::Return(None) => {}
    }
}

/// Calls `f` with each expression in `s`, nested statements' too.
fn stmt_exprs_deep<'a>(s: &'a Stmt, f: &mut impl FnMut(&'a Expr)) {
    stmt_exprs_ref(s, f);
    for_stmts(s, &mut |body| {
        body.iter().for_each(|s| stmt_exprs_deep(s, f))
    });
}

/// The expressions directly in `s`, not its nested statements'.
fn stmt_exprs_ref<'a>(s: &'a Stmt, f: &mut impl FnMut(&'a Expr)) {
    match s {
        Stmt::Expr(e) | Stmt::Throw(e) | Stmt::Return(Some(e)) => f(e),
        Stmt::Local { value: Some(v), .. } => f(v),
        Stmt::Assign { target, value } => {
            f(target);
            f(value);
        }
        Stmt::If { cond, .. } | Stmt::While { cond, .. } | Stmt::DoWhile { cond, .. } => f(cond),
        Stmt::Switch { scrutinee, .. } => f(scrutinee),
        Stmt::Synchronized { lock, .. } => f(lock),
        _ => {}
    }
}

fn kids_ref(e: &Expr, f: &mut impl FnMut(&Expr)) {
    match e {
        Expr::Call(callee, args) => {
            f(callee);
            args.iter().for_each(f);
        }
        Expr::Member(obj, _) | Expr::MethodRef(obj, _) => f(obj),
        Expr::Bin(_, l, r) => {
            f(l);
            f(r);
        }
        Expr::Unary(_, x) | Expr::InstanceOf(x, _) | Expr::Cast(_, x) | Expr::Lambda(_, x) => f(x),
        Expr::Cond { cond, then, els } => {
            f(cond);
            f(then);
            f(els);
        }
        Expr::Concat(parts) | Expr::New(_, parts) => parts.iter().for_each(f),
        Expr::Name(_)
        | Expr::Int(_)
        | Expr::Long(_)
        | Expr::Float(_)
        | Expr::Str(_)
        | Expr::Bool(_)
        | Expr::This => {}
    }
}

fn kids_mut(e: &mut Expr, f: &mut impl FnMut(&mut Expr)) {
    match e {
        Expr::Call(callee, args) => {
            f(callee);
            args.iter_mut().for_each(f);
        }
        Expr::Member(obj, _) | Expr::MethodRef(obj, _) => f(obj),
        Expr::Bin(_, l, r) => {
            f(l);
            f(r);
        }
        Expr::Unary(_, x) | Expr::InstanceOf(x, _) | Expr::Cast(_, x) | Expr::Lambda(_, x) => f(x),
        Expr::Cond { cond, then, els } => {
            f(cond);
            f(then);
            f(els);
        }
        Expr::Concat(parts) | Expr::New(_, parts) => parts.iter_mut().for_each(f),
        Expr::Name(_)
        | Expr::Int(_)
        | Expr::Long(_)
        | Expr::Float(_)
        | Expr::Str(_)
        | Expr::Bool(_)
        | Expr::This => {}
    }
}

fn walk(e: &Expr, f: &mut impl FnMut(&Expr)) {
    f(e);
    kids_ref(e, &mut |k| walk(k, f));
}

/// [`walk`], its expressions borrowed as long as `e` is.
fn walk_ref<'a>(e: &'a Expr, f: &mut impl FnMut(&'a Expr)) {
    f(e);
    match e {
        Expr::Call(callee, args) => {
            walk_ref(callee, f);
            args.iter().for_each(|a| walk_ref(a, f));
        }
        Expr::Member(obj, _) | Expr::MethodRef(obj, _) => walk_ref(obj, f),
        Expr::Bin(_, l, r) => {
            walk_ref(l, f);
            walk_ref(r, f);
        }
        Expr::Unary(_, x) | Expr::InstanceOf(x, _) | Expr::Cast(_, x) | Expr::Lambda(_, x) => {
            walk_ref(x, f)
        }
        Expr::Cond { cond, then, els } => {
            walk_ref(cond, f);
            walk_ref(then, f);
            walk_ref(els, f);
        }
        Expr::Concat(parts) | Expr::New(_, parts) => parts.iter().for_each(|p| walk_ref(p, f)),
        Expr::Name(_)
        | Expr::Int(_)
        | Expr::Long(_)
        | Expr::Float(_)
        | Expr::Str(_)
        | Expr::Bool(_)
        | Expr::This => {}
    }
}

fn walk_mut(e: &mut Expr, f: &mut impl FnMut(&mut Expr)) {
    f(e);
    kids_mut(e, &mut |k| walk_mut(k, f));
}

#[cfg(test)]
mod tests {
    use super::{Expr, LIMITS, Limits, fits};

    /// A string of NULs, each two bytes in modified UTF-8 but one in
    /// UTF-8, fits only at its modified size.
    #[test]
    fn nul_strings_split_at_their_modified_size() {
        let limits = Limits {
            string: 16,
            ..LIMITS
        };
        let nuls = Expr::Str("\0".repeat(10));
        assert!(!fits([], [&nuls], &limits));
        assert!(fits([], [&Expr::Str("\0".repeat(8))], &limits));
    }
}
