//! A property test: random valid programs, from a small deterministic
//! generator, validate without problems, write as text that reads back to
//! an equal program, and write again the same. Programs use core's
//! members through a `uses` section (classes, an interface, methods, a
//! getter, a static function, and optional parameters), call them with
//! `CallMethod` and `Omitted`, log through a compiler-made `~console`, and
//! carry qname overrides, module paths, and connected and `@keep` marks.
//! Modules may have their own classes and interfaces, with fields (`var`
//! and private ones too), a constructor, methods, getters, setters, static
//! fields and functions, and an `@operator("==")` method, used through
//! `this`, `new`, field reads and writes, upcasts, downcasts, `as`, `is`,
//! and `identical`. Set `TMPL_FUZZ_SEED` and `TMPL_FUZZ_ROUNDS` to vary
//! it.

use std::env;
use temper_tmpl::ops::{OPS, Op};
use temper_tmpl::qname::QNames;
use temper_tmpl::{
    Builder, DeclId, DeclKind, Facts, Kind, NodeId, Profile, Program, Span, TypeId, difference,
    text, validate,
};

const TYPES: [TypeId; 5] = [
    TypeId::BOOLEAN,
    TypeId::INT32,
    TypeId::INT64,
    TypeId::FLOAT64,
    TypeId::STRING,
];
const NAMES: [&str; 9] = ["a", "x", "count", "for", "if", "odd name", "é", "a`b", "_9"];

/// A small xorshift generator, so runs repeat for a given seed.
struct Rng(u64);

impl Rng {
    fn below(&mut self, n: usize) -> usize {
        self.0 ^= self.0 << 13;
        self.0 ^= self.0 >> 7;
        self.0 ^= self.0 << 17;
        (self.0 % n.max(1) as u64) as usize
    }

    fn chance(&mut self, percent: usize) -> bool {
        self.below(100) < percent
    }

    fn pick<T: Copy>(&mut self, items: &[T]) -> T {
        items[self.below(items.len())]
    }
}

/// A value in scope: a local, parameter, or module `let`.
#[derive(Clone, Copy)]
struct Value {
    decl: DeclId,
    ty: TypeId,
    var: bool,
    module_let: bool,
}

/// Something callable: a function, or with a receiver, a method.
#[derive(Clone)]
struct Callee {
    decl: DeclId,
    /// A method's receiver type; `None` for a function.
    receiver: Option<TypeId>,
    result: TypeId,
    bubbles: bool,
    params: Vec<TypeId>,
    optional: Vec<bool>,
}

/// A class or interface of the module being made.
#[derive(Clone)]
struct Class {
    decl: DeclId,
    ty: TypeId,
    /// For an interface, the class that implements it; for a class, the
    /// interface it implements; as an index into `Gen::classes`.
    other: Option<usize>,
    is_interface: bool,
    ctor: DeclId,
    fields: Vec<Field>,
    /// Static fields, of the class's own type.
    statics: Vec<DeclId>,
    /// Static functions, taking nothing and returning the class.
    makers: Vec<DeclId>,
}

#[derive(Clone, Copy)]
struct Field {
    decl: DeclId,
    ty: TypeId,
    var: bool,
    private: bool,
}

/// A member whose body is made once every class's headers are.
enum Body {
    /// A field or static field, which has none.
    Field(DeclId),
    Constructor(DeclId, Vec<DeclId>),
    /// A getter, or a method, with its parameters and result.
    Method(DeclId, Vec<(DeclId, TypeId)>, TypeId),
    Setter(DeclId, DeclId, Field),
    /// An `@operator("==")` method, comparing with `identical`.
    Equals(DeclId, DeclId),
    Maker(DeclId),
}

/// Core's members the programs use.
struct Core {
    console: TypeId,
    log: DeclId,
    get_console: DeclId,
    /// Core's `List<T>`, for list literals.
    list: DeclId,
}

struct Gen {
    rng: Rng,
    b: Builder,
    functions: Vec<Callee>,
    core: Option<Core>,
    /// The module's `~console`.
    console: DeclId,
    /// Counts qname overrides, so each is unique.
    overrides: usize,
    /// Values in scope, innermost last.
    scope: Vec<Value>,
    /// Enclosing loops' labels.
    loops: Vec<DeclId>,
    owner: DeclId,
    may_bubble: bool,
    /// std's `testing` module's `Test`, once a test needs it.
    test_type: Option<TypeId>,
    /// The module's classes and interfaces.
    classes: Vec<Class>,
    /// The class or interface whose method is being made, for `this`.
    this: Option<usize>,
    /// Whether static fields may be read: not while init sets them.
    statics_ready: bool,
    /// The module's generic `id<T>(x: T): T`, if it has one.
    generic_id: Option<DeclId>,
}

impl Gen {
    fn name(&mut self) -> &'static str {
        self.rng.pick(&NAMES)
    }

    fn declare(&mut self, kind: DeclKind, owner: DeclId, ty: TypeId) -> DeclId {
        if self.rng.chance(15) {
            let hints: Vec<&str> = (0..1 + self.rng.below(3)).map(|_| self.name()).collect();
            self.b.declare_made(kind, &hints, owner, ty)
        } else {
            let name = self.name();
            self.b.declare(kind, name, owner, ty)
        }
    }

    /// Sometimes overrides a declaration's qname, uniquely.
    fn qname(&mut self, decl: DeclId, suffix: &str) {
        if self.rng.chance(10) {
            self.overrides += 1;
            let tail = format!("over{}{suffix}", self.overrides);
            self.b.set_qname(decl, &tail);
        }
    }

    /// A `uses` section for core: the scalars' classes with some members,
    /// and `Console`.
    fn core(&mut self) {
        let b = &mut self.b;
        let core = b.uses("temper-core", "0.1.0");
        let member = |b: &mut Builder,
                      kind: DeclKind,
                      name: &str,
                      owner: DeclId,
                      params: &[(&str, TypeId, bool)],
                      result: TypeId,
                      bubbles: bool| {
            let types: Vec<TypeId> = params.iter().map(|p| p.1).collect();
            let optional: Vec<bool> = params.iter().map(|p| p.2).collect();
            let returns = match bubbles {
                true => b.result(result, TypeId::BUBBLE),
                false => result,
            };
            let sig = b.function_type_with(&types, &optional, returns);
            let decl = b.declare(kind, name, owner, sig);
            b.set_connected(decl, &[]);
            let names: Vec<&str> = params.iter().map(|p| p.0).collect();
            b.set_param_names(decl, &names);
            let receiver = (kind != DeclKind::Function).then(|| b.program().decls.get(owner).ty);
            Callee {
                decl,
                receiver,
                result,
                bubbles,
                params: types,
                optional,
            }
        };
        let int32 = b.declare_type(DeclKind::Class, "Int32", core);
        let string = b.declare_type(DeclKind::Class, "String", core);
        let float64 = b.declare_type(DeclKind::Class, "Float64", core);
        let (i, s, f) = (TypeId::INT32, TypeId::STRING, TypeId::FLOAT64);
        let mut callees = vec![
            member(
                b,
                DeclKind::Method,
                "toString",
                int32,
                &[("radix", i, true)],
                s,
                false,
            ),
            member(
                b,
                DeclKind::Method,
                "max",
                int32,
                &[("other", i, false)],
                i,
                false,
            ),
            member(
                b,
                DeclKind::Function,
                "fromCodePoint",
                string,
                &[("codePoint", i, false)],
                s,
                true,
            ),
            member(
                b,
                DeclKind::Getter,
                "isEmpty",
                string,
                &[],
                TypeId::BOOLEAN,
                false,
            ),
            member(
                b,
                DeclKind::Method,
                "toInt32",
                string,
                &[("radix", i, true)],
                i,
                true,
            ),
            member(b, DeclKind::Method, "sqrt", float64, &[], f, false),
        ];
        let console = b.declare_type(DeclKind::Interface, "Console", core);
        let console_ty = b.program().decls.get(console).ty;
        let log = member(
            b,
            DeclKind::Method,
            "log",
            console,
            &[("message", s, false)],
            TypeId::VOID,
            false,
        );
        let get_console = member(
            b,
            DeclKind::Function,
            "getConsole",
            core,
            &[("name", s, true)],
            console_ty,
            false,
        );
        if self.rng.chance(30) {
            self.b
                .set_doc(get_console.decl, "Access or make a console.");
        }
        callees.push(log.clone());
        self.functions.extend(callees);
        let b = &mut self.b;
        let list = b.declare_type(DeclKind::Class, "List", core);
        let t = b.declare_type_param("T", list, &[]);
        b.set_type_params(list, &[t]);
        self.core = Some(Core {
            console: console_ty,
            log: log.decl,
            get_console: get_console.decl,
            list,
        });
    }

    fn program(&mut self) -> Program {
        self.core();
        for _ in 0..1 + self.rng.below(2) {
            let module = self.b.module("m");
            if self.rng.chance(50) {
                let path = format!("p/{}", self.rng.below(100));
                self.b.set_module_path(module, &path);
            }
            // An earlier module's functions are imported only if exported.
            let p = self.b.program();
            let keep: Vec<bool> = (self.functions.iter())
                .map(|f| {
                    let d = p.decls.get(f.decl);
                    temper_tmpl::qname::is_external(p, f.decl) || d.facts.contains(Facts::EXPORTED)
                })
                .collect();
            let mut keep = keep.into_iter();
            self.functions.retain(|_| keep.next().unwrap());
            self.module(module);
        }
        let mut b = std::mem::replace(&mut self.b, Builder::new("", ""));
        if self.rng.chance(50) {
            add_spans(&mut b);
        }
        b.finish()
    }

    fn module(&mut self, module: DeclId) {
        // A compiler-made `~console`, as the frontend makes, first.
        let core = self.core.as_ref().unwrap();
        let (console_ty, get_console) = (core.console, core.get_console);
        self.console = self
            .b
            .declare_made(DeclKind::ModuleLet, &["console"], module, console_ty);
        let node = self.b.module_let(self.console);
        self.b.add_item(module, node);
        self.generic_id = None;
        if self.rng.chance(50) {
            // A generic function, `id<T>(x: T): T`.
            let id = self
                .b
                .declare(DeclKind::Function, "id", module, TypeId::NONE);
            let t = self.b.declare_type_param("T", id, &[]);
            self.b.set_type_params(id, &[t]);
            let t = self.b.program().decls.get(t).ty;
            let sig = self.b.function_type(&[t], t);
            self.b.set_decl_type(id, sig);
            let x = self.b.declare(DeclKind::Param, "x", id, t);
            let param = self.b.param(x);
            let read = self.b.local(x);
            let ret = self.b.return_stmt(Some(read));
            let body = self.b.block(&[ret]);
            let f = self.b.function(id, &[param], body);
            self.b.add_item(module, f);
            self.generic_id = Some(id);
        }
        let mut lets = Vec::new();
        for _ in 0..self.rng.below(3) {
            let ty = self.rng.pick(&TYPES);
            let decl = self.declare(DeclKind::ModuleLet, module, ty);
            let var = self.rng.chance(50);
            if var {
                self.b.add_facts(decl, Facts::VAR);
            }
            self.facts(decl);
            self.qname(decl, "");
            let node = self.b.module_let(decl);
            self.b.add_item(module, node);
            lets.push(Value {
                decl,
                ty,
                var,
                module_let: true,
            });
        }
        self.classes.clear();
        if self.rng.chance(50) {
            self.classes(module, &lets);
        }
        for _ in 0..self.rng.below(4) {
            self.function(module, &lets);
        }
        // Init: each module `let` initialized in order, then statements.
        self.owner = module;
        self.may_bubble = true;
        self.scope.clear();
        let name = if self.rng.chance(50) {
            self.b.omitted(TypeId::STRING)
        } else {
            self.b.string("m")
        };
        let console = self.b.call(get_console, &[name]);
        let mut stmts = vec![self.b.init_let(self.console, console)];
        // Static fields first, which can't be read until they're set.
        self.statics_ready = false;
        for c in 0..self.classes.len() {
            for target in self.classes[c].statics.clone() {
                let value = self.object(c, 1);
                stmts.push(self.b.init_static(target, value));
            }
        }
        self.statics_ready = true;
        for value in lets {
            let init = self.expr(value.ty, 2);
            stmts.push(self.b.init_let(value.decl, init));
            self.scope.push(value);
        }
        for _ in 0..self.rng.below(3) {
            stmts.push(self.stmt(2));
        }
        let init = self.b.block(&stmts);
        self.b.set_init(module, init);
        for _ in 0..self.rng.below(3) {
            self.test(module, &lets_in_scope(&self.scope));
        }
    }

    /// A test: a compiler-made function of std's `Test`, which may bubble.
    fn test(&mut self, module: DeclId, lets: &[Value]) {
        let test_type = match self.test_type {
            Some(t) => t,
            None => {
                let std = self.b.uses("std", "0.6.0");
                let testing = self.b.external_module(std, "testing");
                let class = self.b.declare_type(DeclKind::Class, "Test", testing);
                let t = self.b.nominal(class);
                self.test_type = Some(t);
                t
            }
        };
        let result = self.b.result(TypeId::VOID, TypeId::BUBBLE);
        let sig = self.b.function_type(&[test_type], result);
        let decl = self
            .b
            .declare_made(DeclKind::Function, &["test"], module, sig);
        let param = self
            .b
            .declare_made(DeclKind::Param, &["test"], decl, test_type);
        let param = self.b.param(param);
        self.owner = decl;
        self.may_bubble = true;
        self.scope = lets.to_vec();
        let stmts: Vec<NodeId> = (0..self.rng.below(3)).map(|_| self.stmt(2)).collect();
        let body = self.b.block(&stmts);
        let name = self.rng.pick(&["works", "names: 'quoted'", "ünïcode", ""]);
        let node = self.b.test(decl, name, &[param], body);
        self.b.add_test(module, node);
    }

    fn facts(&mut self, decl: DeclId) {
        if self.rng.chance(30) {
            self.b.add_facts(decl, Facts::EXPORTED);
        }
        if self.rng.chance(10) {
            self.b.add_facts(decl, Facts::KEEP);
        }
        if self.rng.chance(10) {
            self.b.add_facts(decl, Facts::KEEP_TEST);
        }
        if self.rng.chance(20) {
            let doc = self
                .rng
                .pick(&["Docs.", "Two\n\nparagraphs.", " indented", ""]);
            self.b.set_doc(decl, doc);
        }
    }

    fn function(&mut self, module: DeclId, lets: &[Value]) {
        let params: Vec<TypeId> = (0..self.rng.below(3))
            .map(|_| self.rng.pick(&TYPES))
            .collect();
        let optional: Vec<bool> = params.iter().map(|_| self.rng.chance(30)).collect();
        // Connected: with no body (host code everywhere), or with a body,
        // its fallback, maybe for backends without host code.
        let connected = self.rng.chance(20);
        let bodiless = connected && self.rng.chance(50);
        // An optional parameter of a function with a body is nullable,
        // and the body starts with its default (rule N2).
        let inner = params.clone();
        let params: Vec<TypeId> = (params.iter().zip(&optional))
            .map(|(&t, &o)| {
                if o && !bodiless {
                    self.b.nullable(t)
                } else {
                    t
                }
            })
            .collect();
        let result = if self.rng.chance(30) {
            TypeId::VOID
        } else {
            self.rng.pick(&TYPES)
        };
        let bubbles = self.rng.chance(30);
        let returns = match bubbles {
            true => self.b.result(result, TypeId::BUBBLE),
            false => result,
        };
        let sig = self.b.function_type_with(&params, &optional, returns);
        let decl = self.declare(DeclKind::Function, module, sig);
        self.facts(decl);
        self.qname(decl, "()");
        if connected {
            let backends: &[&str] = if !bodiless && self.rng.chance(50) {
                &["cpp", "rust"]
            } else {
                &[]
            };
            self.b.set_connected(decl, backends);
        }
        self.functions.push(Callee {
            decl,
            receiver: None,
            result,
            bubbles,
            params: params.clone(),
            optional: optional.clone(),
        });
        self.owner = decl;
        self.may_bubble = bubbles;
        self.scope = lets.to_vec();
        let mut param_nodes = Vec::new();
        let mut defaulted = Vec::new();
        for (i, (ty, optional)) in params.into_iter().zip(optional).enumerate() {
            let param = self.declare(DeclKind::Param, decl, ty);
            if optional {
                self.b.add_facts(param, Facts::OPTIONAL);
            }
            self.qname(param, "");
            param_nodes.push(self.b.param(param));
            if optional && !bodiless {
                // Read through its default's local, below.
                defaulted.push((param, inner[i]));
                continue;
            }
            self.scope.push(Value {
                decl: param,
                ty,
                var: false,
                module_let: false,
            });
        }
        if bodiless {
            let node = self.b.bodiless_function(decl, &param_nodes);
            self.b.add_item(module, node);
            return;
        }
        // Each default may read the parameters before it, with their
        // defaults, and the other parameters.
        let mut stmts: Vec<NodeId> = Vec::new();
        for (param, ty) in defaulted {
            let value = self.expr(ty, 1);
            let read = self.b.local(param);
            let init = self.b.coalesce(read, value);
            let local = self.declare(DeclKind::Local, decl, ty);
            self.qname(local, "=");
            stmts.push(self.b.let_stmt(local, Some(init)));
            self.scope.push(Value {
                decl: local,
                ty,
                var: false,
                module_let: false,
            });
        }
        stmts.extend(
            (0..self.rng.below(4))
                .map(|_| self.stmt(3))
                .collect::<Vec<_>>(),
        );
        if result != TypeId::VOID || self.rng.chance(30) {
            let value = (result != TypeId::VOID).then(|| self.expr(result, 2));
            stmts.push(self.b.return_stmt(value));
        }
        let body = self.b.block(&stmts);
        let node = self.b.function(decl, &param_nodes, body);
        self.b.add_item(module, node);
    }

    fn block(&mut self, depth: usize) -> NodeId {
        let mark = self.scope.len();
        let stmts: Vec<NodeId> = (0..self.rng.below(3)).map(|_| self.stmt(depth)).collect();
        self.scope.truncate(mark);
        self.b.block(&stmts)
    }

    fn stmt(&mut self, depth: usize) -> NodeId {
        let choice = if depth == 0 { 0 } else { self.rng.below(11) };
        match choice {
            9 => {
                let fields = self.fields(|f| f.var);
                if fields.is_empty() {
                    return self.stmt(0);
                }
                let (c, field) = self.rng.pick(&fields);
                let object = self.object(c, depth - 1);
                let value = self.expr(field.ty, depth - 1);
                self.b.set_field(field.decl, object, value)
            }
            10 => {
                let p = self.b.program();
                let setters: Vec<(usize, DeclId)> = (self.classes.iter().enumerate())
                    .flat_map(|(c, class)| {
                        let decls = p.decls.ids();
                        decls
                            .filter(move |&d| {
                                let d = p.decls.get(d);
                                d.kind == DeclKind::Setter && d.owner == class.decl
                            })
                            .map(move |d| (c, d))
                    })
                    .collect();
                if setters.is_empty() {
                    return self.stmt(0);
                }
                let (c, setter) = self.rng.pick(&setters);
                let ty = p.signature(setter).unwrap().params[0];
                let object = self.object(c, depth - 1);
                let value = self.expr(ty, depth - 1);
                let call = self.b.call_method(setter, object, &[value]);
                self.b.expr_statement(call)
            }
            1 => {
                let ty = self.rng.pick(&TYPES);
                let init = self.expr(ty, depth - 1);
                let local = self.declare(DeclKind::Local, self.owner, ty);
                self.qname(local, "=");
                let var = self.rng.chance(50);
                if var {
                    self.b.add_facts(local, Facts::VAR);
                }
                self.scope.push(Value {
                    decl: local,
                    ty,
                    var,
                    module_let: false,
                });
                self.b.let_stmt(local, Some(init))
            }
            2 => {
                let vars: Vec<Value> = self.scope.iter().copied().filter(|v| v.var).collect();
                if vars.is_empty() {
                    return self.stmt(0);
                }
                let target = self.rng.pick(&vars);
                let value = self.expr(target.ty, depth - 1);
                if target.module_let {
                    self.b.set_module_let(target.decl, value)
                } else {
                    self.b.assign(target.decl, value)
                }
            }
            3 if self.rng.chance(30) => {
                let scrutinee = self.expr(TypeId::INT32, depth - 1);
                let mut labels: Vec<i32> = Vec::new();
                let mut cases = Vec::new();
                for _ in 0..self.rng.below(4) {
                    let mut lits = Vec::new();
                    for _ in 0..1 + self.rng.below(2) {
                        let n = self.rng.below(7) as i32 - 1;
                        if !labels.contains(&n) {
                            labels.push(n);
                            lits.push(self.b.int32(n));
                        }
                    }
                    if !lits.is_empty() {
                        let body = self.block(depth - 1);
                        cases.push(self.b.case(&lits, body));
                    }
                }
                if self.rng.chance(50) {
                    let body = self.block(depth - 1);
                    cases.push(self.b.case(&[], body));
                }
                self.b.switch_stmt(scrutinee, &cases)
            }
            3 => {
                let cond = self.expr(TypeId::BOOLEAN, depth - 1);
                let then = self.block(depth - 1);
                let els = if self.rng.chance(50) {
                    Some(self.block(depth - 1))
                } else {
                    None
                };
                self.b.if_stmt(cond, then, els)
            }
            4 | 5 => {
                let label = self.declare(DeclKind::Label, self.owner, TypeId::NONE);
                self.loops.push(label);
                let cond = self.expr(TypeId::BOOLEAN, depth - 1);
                let body = self.block(depth - 1);
                self.loops.pop();
                if choice == 4 {
                    self.b.while_loop(label, cond, body)
                } else {
                    self.b.do_while(label, body, cond)
                }
            }
            6 if !self.loops.is_empty() => {
                let label = self.rng.pick(&self.loops.clone());
                if self.rng.chance(50) {
                    self.b.break_loop(label)
                } else {
                    self.b.continue_loop(label)
                }
            }
            7 => self.block(depth - 1),
            _ => {
                let text = self.expr(TypeId::STRING, depth.saturating_sub(1));
                let console = self.b.get_module_let(self.console);
                let log = self.core.as_ref().unwrap().log;
                let log = self.b.call_method(log, console, &[text]);
                self.b.expr_statement(log)
            }
        }
    }

    /// A function literal of one `Int32` parameter, called at once, with
    /// what it captures listed.
    fn called_literal(&mut self, ty: TypeId, depth: usize) -> NodeId {
        let refs: Vec<Callee> = (self.functions.iter())
            .filter(|f| f.receiver.is_none() && f.result == ty && !f.optional.contains(&true))
            .filter(|f| !f.bubbles || self.may_bubble)
            .cloned()
            .collect();
        if !refs.is_empty() && self.rng.chance(40) {
            let f = refs[self.rng.below(refs.len())].clone();
            let sig = self.b.program().decls.get(f.decl).ty;
            let callee = self.b.function_ref(sig, f.decl);
            let args: Vec<NodeId> = f.params.iter().map(|&t| self.expr(t, depth - 1)).collect();
            return self.b.call_value(callee, &args);
        }
        let sig = self.b.function_type(&[TypeId::INT32], ty);
        let decl = self
            .b
            .declare_made(DeclKind::Lambda, &["fn"], self.owner, sig);
        let param = self.declare(DeclKind::Param, decl, TypeId::INT32);
        let owner = std::mem::replace(&mut self.owner, decl);
        let may_bubble = std::mem::replace(&mut self.may_bubble, false);
        let loops = std::mem::take(&mut self.loops);
        let mark = self.scope.len();
        self.scope.push(Value {
            decl: param,
            ty: TypeId::INT32,
            var: false,
            module_let: false,
        });
        let mut stmts: Vec<NodeId> = (0..self.rng.below(3))
            .map(|_| self.stmt(depth - 1))
            .collect();
        if ty != TypeId::VOID {
            let value = self.expr(ty, depth - 1);
            stmts.push(self.b.return_stmt(Some(value)));
        }
        let body = self.b.block(&stmts);
        self.scope.truncate(mark);
        self.owner = owner;
        self.may_bubble = may_bubble;
        self.loops = loops;
        let param = self.b.param(param);
        let literal = self.b.function_literal(sig, decl, &[param], body);
        let captures = temper_tmpl::analysis::captures(self.b.program(), literal);
        self.b.set_captures(decl, &captures);
        let arg = self.expr(TypeId::INT32, depth - 1);
        self.b.call_value(literal, &[arg])
    }

    fn expr(&mut self, ty: TypeId, depth: usize) -> NodeId {
        if let Some(c) = self.classes.iter().position(|c| c.ty == ty) {
            return self.object(c, depth);
        }
        if depth > 0 && self.rng.chance(5) {
            return self.called_literal(ty, depth);
        }
        let choice = if depth == 0 {
            self.rng.below(2)
        } else {
            self.rng.below(14)
        };
        match choice {
            11 if ty == TypeId::BOOLEAN && self.rng.chance(50) => {
                let inner = self.rng.pick(&[TypeId::INT32, TypeId::STRING]);
                let value = self.nullable(inner, depth - 1);
                self.b.is_null(value)
            }
            11 if ty == TypeId::BOOLEAN => {
                let (op, inner) = if self.rng.chance(50) {
                    ("Int32.eq", TypeId::INT32)
                } else {
                    ("String.eq", TypeId::STRING)
                };
                let left = self.nullable(inner, depth - 1);
                let right = self.nullable(inner, depth - 1);
                self.b.eq_nullable(Op::named(op).unwrap(), left, right)
            }
            11 if [TypeId::INT32, TypeId::STRING, TypeId::FLOAT64].contains(&ty) => {
                // `x ?? d`, as a check and a narrowing.
                let value = self.nullable(ty, depth - 1);
                let nullable = self.b.nullable(ty);
                let local = self.declare(DeclKind::Local, self.owner, nullable);
                let init = self.b.let_stmt(local, Some(value));
                let read = self.b.local(local);
                let check = self.b.is_null(read);
                let fallback = self.expr(ty, depth - 1);
                let read = self.b.local(local);
                let non_null = self.b.non_null(read);
                let pick = self.b.if_value(ty, check, fallback, non_null);
                self.b.block_value(ty, &[init], pick)
            }
            12 => {
                let may_bubble = std::mem::replace(&mut self.may_bubble, true);
                let body = if self.rng.chance(20) {
                    self.b.bubble(ty)
                } else {
                    self.expr(ty, depth - 1)
                };
                self.may_bubble = may_bubble;
                let fallback = if self.rng.chance(20) {
                    self.b.panic(ty)
                } else {
                    self.expr(ty, depth - 1)
                };
                self.b.or_else(ty, body, fallback)
            }
            13 if self.may_bubble && self.rng.chance(30) => self.b.bubble(ty),
            13 if self.rng.chance(30) => {
                // A list's literal, its value dropped.
                let item = self.rng.pick(&[TypeId::INT32, TypeId::STRING]);
                let list = self.core.as_ref().unwrap().list;
                let list_ty = self.b.applied(list, &[item]);
                let items: Vec<NodeId> = (0..self.rng.below(3))
                    .map(|_| self.expr(item, depth - 1))
                    .collect();
                let lit = self.b.list_lit(list_ty, &items);
                let stmt = self.b.expr_statement(lit);
                let value = self.expr(ty, depth - 1);
                self.b.block_value(ty, &[stmt], value)
            }
            13 if self.generic_id.is_some() && ty != TypeId::VOID => {
                let id = self.generic_id.unwrap();
                let arg = self.expr(ty, depth - 1);
                self.b.call_generic(id, &[ty], &[arg])
            }
            9 if ty == TypeId::BOOLEAN && !self.classes.is_empty() => {
                let c = self.rng.below(self.classes.len());
                match self.classes[c].other {
                    Some(other) if self.classes[c].is_interface => {
                        let value = self.object(c, depth - 1);
                        let class_ty = self.classes[other].ty;
                        self.b.is(class_ty, value)
                    }
                    _ => {
                        let left = self.object(c, depth - 1);
                        let right = self.object(c, depth - 1);
                        self.b.identical(left, right)
                    }
                }
            }
            10 => {
                let fields = self.fields(|f| f.ty == ty);
                if fields.is_empty() {
                    return self.literal(ty);
                }
                let (c, field) = self.rng.pick(&fields);
                let object = self.object(c, depth - 1);
                self.b.get_field(field.decl, object)
            }
            1 => {
                let values: Vec<Value> =
                    self.scope.iter().copied().filter(|v| v.ty == ty).collect();
                if values.is_empty() {
                    return self.literal(ty);
                }
                let value = self.rng.pick(&values);
                if value.module_let {
                    self.b.get_module_let(value.decl)
                } else {
                    self.b.local(value.decl)
                }
            }
            2 | 3 => {
                let may_bubble = self.may_bubble;
                let ops: Vec<usize> = (0..OPS.len())
                    .filter(|&i| OPS[i].result == ty && (may_bubble || !OPS[i].bubbles))
                    .collect();
                if ops.is_empty() {
                    return self.literal(ty);
                }
                let op = Op(self.rng.pick(&ops) as u32);
                let params = op.info().unwrap().params;
                let args: Vec<NodeId> = params.iter().map(|&p| self.expr(p, depth - 1)).collect();
                self.b.op(op, &args)
            }
            4 => {
                let may_bubble = self.may_bubble;
                let callees: Vec<Callee> = self
                    .functions
                    .iter()
                    .filter(|f| f.result == ty && (may_bubble || !f.bubbles))
                    .cloned()
                    .collect();
                if callees.is_empty() {
                    return self.literal(ty);
                }
                let callee = callees[self.rng.below(callees.len())].clone();
                let receiver = callee.receiver.map(|r| self.expr(r, depth - 1));
                let mut args = Vec::new();
                for (&p, &optional) in callee.params.iter().zip(&callee.optional) {
                    args.push(if optional && self.rng.chance(40) {
                        self.b.omitted(p)
                    } else if self.b.program().types.is_nullable(p) {
                        let inner = self.b.program().types.non_null(p);
                        self.nullable(inner, depth - 1)
                    } else {
                        self.expr(p, depth - 1)
                    });
                }
                match receiver {
                    Some(receiver) => self.b.call_method(callee.decl, receiver, &args),
                    None => self.b.call(callee.decl, &args),
                }
            }
            5 if ty == TypeId::BOOLEAN => {
                let left = self.expr(ty, depth - 1);
                let right = self.expr(ty, depth - 1);
                if self.rng.chance(50) {
                    self.b.and(left, right)
                } else {
                    self.b.or(left, right)
                }
            }
            6 => {
                let cond = self.expr(TypeId::BOOLEAN, depth - 1);
                let then = self.expr(ty, depth - 1);
                let els = self.expr(ty, depth - 1);
                self.b.if_value(ty, cond, then, els)
            }
            7 => {
                let mark = self.scope.len();
                let stmts: Vec<NodeId> = (0..self.rng.below(3))
                    .map(|_| self.stmt(depth - 1))
                    .collect();
                let value = self.expr(ty, depth - 1);
                self.scope.truncate(mark);
                self.b.block_value(ty, &stmts, value)
            }
            8 if self.rng.chance(20) => self.b.error(ty, "unresolved \"thing\""),
            _ => self.literal(ty),
        }
    }

    /// Fields that may be used here, by class: private ones only in
    /// their class's methods.
    fn fields(&self, wanted: impl Fn(&Field) -> bool) -> Vec<(usize, Field)> {
        let mut found = Vec::new();
        for (c, class) in self.classes.iter().enumerate() {
            for field in &class.fields {
                if wanted(field) && (!field.private || self.this == Some(c)) {
                    found.push((c, *field));
                }
            }
        }
        found
    }

    /// An object of a class or interface: `this`, a new one, a static
    /// field, a static function's, or a conversion of one.
    fn object(&mut self, c: usize, depth: usize) -> NodeId {
        let class = self.classes[c].clone();
        if class.is_interface {
            if self.this == Some(c) && self.rng.chance(50) {
                return self.b.this(class.ty);
            }
            let other = class.other.expect("an interface's class");
            let value = self.object(other, depth.saturating_sub(1));
            return self.b.upcast(class.ty, value);
        }
        let mut choices = vec![0];
        if self.this == Some(c) {
            choices.push(1);
        }
        if self.statics_ready && !class.statics.is_empty() {
            choices.push(2);
        }
        if !class.makers.is_empty() {
            choices.push(3);
        }
        if class.other.is_some() && depth > 0 {
            choices.push(4);
            if self.may_bubble {
                choices.push(5);
            }
        }
        match self.rng.pick(&choices) {
            1 => self.b.this(class.ty),
            2 => {
                let target = self.rng.pick(&class.statics);
                self.b.get_static(target)
            }
            3 => {
                let maker = self.rng.pick(&class.makers);
                self.b.call(maker, &[])
            }
            choice @ (4 | 5) => {
                let up = class.other.unwrap();
                let value = self.object(up, depth - 1);
                if choice == 4 {
                    self.b.downcast(class.ty, value)
                } else {
                    self.b.as_cast(class.ty, value)
                }
            }
            _ => {
                let params: Vec<TypeId> = class.fields.iter().map(|f| f.ty).collect();
                let args: Vec<NodeId> = params
                    .into_iter()
                    .map(|ty| self.expr(ty, depth.saturating_sub(1)))
                    .collect();
                self.b.new_object(class.ctor, &args)
            }
        }
    }

    /// Maybe an interface, then a class implementing it, and maybe another
    /// class: their headers first, so bodies may use any of them.
    fn classes(&mut self, module: DeclId, lets: &[Value]) {
        let interface = self.rng.chance(60);
        let count = 1 + usize::from(interface) + self.rng.below(2);
        for c in 0..count {
            let is_interface = interface && c == 0;
            let kind = if is_interface {
                DeclKind::Interface
            } else {
                DeclKind::Class
            };
            let name = self.name();
            let decl = self.b.declare_type(kind, name, module);
            self.facts(decl);
            self.qname(decl, "");
            let other = match (interface, c) {
                (true, 0) => Some(1),
                (true, 1) => Some(0),
                _ => None,
            };
            let ty = self.b.program().decls.get(decl).ty;
            self.classes.push(Class {
                decl,
                ty,
                other,
                is_interface,
                ctor: DeclId::NONE,
                fields: Vec::new(),
                statics: Vec::new(),
                makers: Vec::new(),
            });
        }
        let mut members = Vec::new();
        for c in 0..count {
            members.push(self.class_heads(c));
        }
        for (c, bodies) in members.into_iter().enumerate() {
            let mut nodes = Vec::new();
            for body in bodies {
                nodes.push(self.member(c, body, lets));
            }
            let node = self.b.class(self.classes[c].decl, &nodes);
            self.b.add_item(module, node);
        }
    }

    /// Declares a class's or interface's members, for bodies to come.
    fn class_heads(&mut self, c: usize) -> Vec<Body> {
        let class = self.classes[c].clone();
        let mut bodies = Vec::new();
        let doc = |g: &mut Gen, decl: DeclId| {
            if g.rng.chance(15) {
                g.b.set_doc(decl, "A member.");
            }
        };
        if class.is_interface {
            // Supertypes: the interface's class implements it.
            let implementor = self.classes[class.other.unwrap()].decl;
            self.b.set_supers(implementor, &[class.ty]);
            // An abstract getter, and a method with a body.
            let ty = self.rng.pick(&TYPES);
            let sig = self.b.function_type(&[], ty);
            let getter = self.declare(DeclKind::Getter, class.decl, sig);
            doc(self, getter);
            bodies.push(Body::Method(getter, Vec::new(), ty));
        } else {
            for _ in 0..1 + self.rng.below(3) {
                let ty = self.rng.pick(&TYPES);
                let decl = self.declare(DeclKind::Field, class.decl, ty);
                doc(self, decl);
                let var = self.rng.chance(50);
                if var {
                    self.b.add_facts(decl, Facts::VAR);
                }
                let private = self.rng.chance(25);
                if private {
                    self.b.add_facts(decl, Facts::PRIVATE);
                }
                let field = Field {
                    decl,
                    ty,
                    var,
                    private,
                };
                self.classes[c].fields.push(field);
                bodies.push(Body::Field(decl));
            }
            let fields = self.classes[c].fields.clone();
            let params: Vec<TypeId> = fields.iter().map(|f| f.ty).collect();
            let sig = self.b.function_type(&params, class.ty);
            let ctor = self
                .b
                .declare(DeclKind::Constructor, "constructor", class.decl, sig);
            self.classes[c].ctor = ctor;
            let params = (fields.iter())
                .map(|f| self.declare(DeclKind::Param, ctor, f.ty))
                .collect();
            bodies.push(Body::Constructor(ctor, params));
            if self.rng.chance(50) {
                let decl = self.declare(DeclKind::StaticField, class.decl, class.ty);
                self.classes[c].statics.push(decl);
                bodies.push(Body::Field(decl));
            }
            // A getter and a setter for a public var field, if any.
            if let Some(&field) = fields.iter().find(|f| f.var && !f.private) {
                let sig = self.b.function_type(&[], field.ty);
                let getter = self.declare(DeclKind::Getter, class.decl, sig);
                self.functions.push(Callee {
                    decl: getter,
                    receiver: Some(class.ty),
                    result: field.ty,
                    bubbles: false,
                    params: Vec::new(),
                    optional: Vec::new(),
                });
                bodies.push(Body::Method(getter, Vec::new(), field.ty));
                let sig = self.b.function_type(&[field.ty], TypeId::VOID);
                let setter = self.declare(DeclKind::Setter, class.decl, sig);
                let value = self.declare(DeclKind::Param, setter, field.ty);
                bodies.push(Body::Setter(setter, value, field));
            }
            if let Some(other) = class.other {
                // Implements the interface's getter.
                let wanted = self.b.program().decls.ids().find(|&d| {
                    let d = self.b.program().decls.get(d);
                    d.kind == DeclKind::Getter && d.owner == self.classes[other].decl
                });
                if let Some(abstract_getter) = wanted {
                    let sig = self.b.program().decls.get(abstract_getter).ty;
                    let ty = self.b.program().signature(abstract_getter).unwrap().pass;
                    let getter = self.declare(DeclKind::Getter, class.decl, sig);
                    bodies.push(Body::Method(getter, Vec::new(), ty));
                }
            }
            if self.rng.chance(40) {
                let sig = self.b.function_type(&[class.ty], TypeId::BOOLEAN);
                let eq = self.b.declare(DeclKind::Method, "equals", class.decl, sig);
                self.b.add_facts(eq, Facts::OPERATOR_EQ);
                if self.rng.chance(50) {
                    self.b.add_facts(eq, Facts::AUTO);
                }
                let other = self.declare(DeclKind::Param, eq, class.ty);
                self.functions.push(Callee {
                    decl: eq,
                    receiver: Some(class.ty),
                    result: TypeId::BOOLEAN,
                    bubbles: false,
                    params: vec![class.ty],
                    optional: vec![false],
                });
                bodies.push(Body::Equals(eq, other));
            }
            if self.rng.chance(40) {
                let sig = self.b.function_type(&[], class.ty);
                let maker = self.declare(DeclKind::Function, class.decl, sig);
                self.qname(maker, "()");
                self.classes[c].makers.push(maker);
                bodies.push(Body::Maker(maker));
            }
        }
        // A method, maybe with parameters.
        let params: Vec<TypeId> = (0..self.rng.below(3))
            .map(|_| self.rng.pick(&TYPES))
            .collect();
        let result = self.rng.pick(&TYPES);
        let sig = self.b.function_type(&params, result);
        let method = self.declare(DeclKind::Method, class.decl, sig);
        doc(self, method);
        let params = (params.into_iter())
            .map(|ty| (self.declare(DeclKind::Param, method, ty), ty))
            .collect::<Vec<_>>();
        self.functions.push(Callee {
            decl: method,
            receiver: Some(class.ty),
            result,
            bubbles: false,
            params: params.iter().map(|p| p.1).collect(),
            optional: vec![false; params.len()],
        });
        bodies.push(Body::Method(method, params, result));
        bodies
    }

    /// A member's node, its body made now that every header is.
    fn member(&mut self, c: usize, body: Body, lets: &[Value]) -> NodeId {
        let class = self.classes[c].clone();
        self.scope = lets.to_vec();
        self.may_bubble = false;
        self.this = Some(c);
        let node = match body {
            Body::Constructor(ctor, params) => {
                let mut stmts = Vec::new();
                for (field, &param) in class.fields.iter().zip(&params) {
                    let this = self.b.this(class.ty);
                    let value = self.b.local(param);
                    stmts.push(self.b.set_field(field.decl, this, value));
                }
                let params: Vec<NodeId> = params.iter().map(|&d| self.b.param(d)).collect();
                let body = self.b.block(&stmts);
                self.b.method(ctor, &params, body)
            }
            Body::Method(decl, params, result) => {
                self.owner = decl;
                let nodes: Vec<NodeId> = params.iter().map(|&(d, _)| self.b.param(d)).collect();
                if class.is_interface
                    && params.is_empty()
                    && self.b.program().decls.get(decl).kind == DeclKind::Getter
                {
                    self.this = None;
                    return self.b.abstract_method(decl, &nodes);
                }
                for &(decl, ty) in &params {
                    self.scope.push(Value {
                        decl,
                        ty,
                        var: false,
                        module_let: false,
                    });
                }
                let mut stmts: Vec<NodeId> = (0..self.rng.below(3)).map(|_| self.stmt(2)).collect();
                let value = self.expr(result, 2);
                stmts.push(self.b.return_stmt(Some(value)));
                let body = self.b.block(&stmts);
                self.b.method(decl, &nodes, body)
            }
            Body::Setter(setter, value, field) => {
                let param = self.b.param(value);
                let this = self.b.this(class.ty);
                let read = self.b.local(value);
                let set = self.b.set_field(field.decl, this, read);
                let body = self.b.block(&[set]);
                self.b.method(setter, &[param], body)
            }
            Body::Equals(eq, other) => {
                let param = self.b.param(other);
                let this = self.b.this(class.ty);
                let read = self.b.local(other);
                let same = self.b.identical(this, read);
                let ret = self.b.return_stmt(Some(same));
                let body = self.b.block(&[ret]);
                self.b.method(eq, &[param], body)
            }
            Body::Field(decl) if self.b.program().decls.get(decl).kind == DeclKind::StaticField => {
                self.b.static_field(decl)
            }
            Body::Field(decl) => self.b.field(decl),
            Body::Maker(maker) => {
                self.this = None;
                self.owner = maker;
                let value = self.object(c, 1);
                let ret = self.b.return_stmt(Some(value));
                let body = self.b.block(&[ret]);
                self.b.function(maker, &[], body)
            }
        };
        self.this = None;
        node
    }

    /// A value of `inner?`: `null`, or a value of `inner`.
    fn nullable(&mut self, inner: TypeId, depth: usize) -> NodeId {
        let ty = self.b.nullable(inner);
        if self.rng.chance(30) {
            return self.b.null(ty);
        }
        let value = self.expr(inner, depth);
        self.b.as_nullable(value)
    }

    fn literal(&mut self, ty: TypeId) -> NodeId {
        match ty {
            TypeId::BOOLEAN => self.b.bool_lit(self.rng.chance(50)),
            TypeId::INT32 => {
                let value = self.rng.pick(&[0, 1, -1, i32::MIN, i32::MAX, 42]);
                self.b.int32(value)
            }
            TypeId::INT64 => {
                let value = self.rng.pick(&[0, -7, i64::MIN, i64::MAX, 1 << 40]);
                self.b.int64(value)
            }
            TypeId::FLOAT64 => {
                let value = self.rng.pick(&[
                    0.0,
                    -0.0,
                    0.1,
                    1e300,
                    -2.5e-8,
                    f64::NAN,
                    f64::INFINITY,
                    f64::NEG_INFINITY,
                    5e-324,
                ]);
                self.b.float64(value)
            }
            TypeId::STRING => {
                let value = self
                    .rng
                    .pick(&["", "hi", "a \"quote\"", "tab\tline\n", "\u{1}é😀\\"]);
                self.b.string(value)
            }
            _ => self.b.void(),
        }
    }
}

/// Gives every node and declaration a span, as P1 wants.
fn add_spans(b: &mut Builder) {
    let file = b.add_file("src/fuzz.temper", [7; 16]);
    let (nodes, decls) = {
        let p = b.program();
        (p.nodes.len() as u32, p.decls.len() as u32)
    };
    for i in 0..nodes {
        let span = Span {
            file,
            start: i,
            end: i + 3,
        };
        b.set_span(NodeId(i), span, i % 5 == 0);
    }
    for i in 0..decls {
        let span = Span {
            file,
            start: i,
            end: i + 9,
        };
        let name = Span { end: i + 1, ..span };
        b.set_decl_spans(DeclId(i), span, name);
    }
}

#[test]
fn random_programs_validate_and_round_trip() {
    let seed: u64 = env::var("TMPL_FUZZ_SEED")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(0x5eed);
    let rounds: u64 = env::var("TMPL_FUZZ_ROUNDS")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(300);
    let (mut kinds, mut qnames) = (std::collections::HashSet::new(), 0);
    let mut nodes = std::collections::HashSet::new();
    for round in 0..rounds {
        let mut g = Gen {
            rng: Rng(seed.wrapping_mul(0x9e37_79b9_7f4a_7c15).wrapping_add(round) | 1),
            b: Builder::new("fuzz", "0.1.0"),
            functions: Vec::new(),
            core: None,
            console: DeclId::NONE,
            overrides: 0,
            scope: Vec::new(),
            loops: Vec::new(),
            owner: DeclId::NONE,
            test_type: None,
            may_bubble: false,
            classes: Vec::new(),
            this: None,
            statics_ready: true,
            generic_id: None,
        };
        let p = g.program();
        let written = text::write(&p);
        let problems = validate(&p, &Profile::full());
        assert!(
            problems.is_empty(),
            "round {round}: {}\n{written}",
            problems[0]
        );
        let back = text::read(&written).unwrap_or_else(|e| panic!("round {round}: {e}\n{written}"));
        if let Some(d) = difference(&p, &back) {
            panic!("round {round}: {d}\n{written}");
        }
        assert_eq!(text::write(&back), written, "round {round}");
        kinds.extend(p.decls.ids().map(|d| p.decls.get(d).kind));
        nodes.extend((0..p.nodes.len() as u32).map(|n| p.nodes.kind(NodeId(n))));
        let names = QNames::derive(&p);
        qnames += p.decls.ids().filter(|&d| names.get(d).is_some()).count();
    }
    // The generator reaches every declaration kind but a removed one.
    if rounds >= 100 {
        assert_eq!(kinds.len(), 17, "{kinds:?}");
        let classes = [
            Kind::Class,
            Kind::Interface,
            Kind::Field,
            Kind::StaticField,
            Kind::Method,
            Kind::AbstractMethod,
            Kind::SetField,
            Kind::InitStatic,
            Kind::This,
            Kind::GetField,
            Kind::GetStatic,
            Kind::New,
            Kind::Upcast,
            Kind::Downcast,
            Kind::As,
            Kind::Is,
            Kind::Identical,
            Kind::Null,
            Kind::IsNull,
            Kind::NonNull,
            Kind::AsNullable,
            Kind::EqNullable,
            Kind::Coalesce,
            Kind::OrElse,
            Kind::Bubble,
            Kind::Panic,
            Kind::ListLit,
            Kind::FunctionLiteral,
            Kind::FunctionRef,
            Kind::CallValue,
            Kind::Switch,
            Kind::Case,
        ];
        let missing: Vec<_> = classes.iter().filter(|k| !nodes.contains(k)).collect();
        assert!(missing.is_empty(), "{missing:?}");
        assert!(qnames > 0);
    }
}

/// The module `let`s among values in scope.
fn lets_in_scope(scope: &[Value]) -> Vec<Value> {
    scope.iter().filter(|v| v.module_let).cloned().collect()
}
