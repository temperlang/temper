//! Writing canonical text.

use super::{FORMAT_VERSION, MODEL_VERSION, writing_order};
use crate::decls::{DeclKind, Facts};
use crate::ids::{DeclId, NodeId, Span, TypeId};
use crate::nodes::{Flags, Kids};
use crate::schema::{Kind, Node};
use crate::{Module, Program, Uses};
use std::fmt::Write as _;

/// Writes a program as canonical text, with its spans section if it has
/// spans. Expects a structurally sound program (the validator's S rules).
pub fn write(p: &Program) -> String {
    let mut w = Writer {
        out: String::new(),
        indent: 0,
        flat: false,
        this: TypeId::NONE,
    };
    let _ = write!(
        w.out,
        "tmpl {FORMAT_VERSION} model {MODEL_VERSION} library {} {}",
        name(&p.library.name),
        p.library.version
    );
    if let Some(profile) = &p.profile {
        let _ = write!(w.out, " profile {}", name(profile));
    }
    w.out.push('\n');
    let members = members(p);
    for uses in &p.uses {
        w.out.push('\n');
        w.uses(p, uses, &members);
    }
    for module in &p.modules {
        w.out.push('\n');
        w.module(p, module);
    }
    if p.has_spans {
        w.out.push('\n');
        write_spans(p, &mut w.out);
    }
    w.out
}

struct Writer {
    out: String,
    indent: usize,
    /// Writing on one line, inside a `BlockValue`.
    flat: bool,
    /// In a method's body, its class's type, which `this` has unless
    /// written otherwise; `NONE` elsewhere.
    this: TypeId,
}

impl Writer {
    /// Starts a new line, or in flat mode, separates with a space.
    fn line(&mut self) {
        if self.flat {
            self.out.push(' ');
        } else {
            self.out.push('\n');
            for _ in 0..self.indent {
                self.out.push_str("  ");
            }
        }
    }

    /// A `uses` section: the library, then what's used of it.
    fn uses(&mut self, p: &Program, uses: &Uses, members: &[Vec<DeclId>]) {
        self.out.push_str("uses ");
        self.decl(p, uses.decl);
        let _ = write!(self.out, " {} {{", uses.version);
        self.indent += 1;
        for &decl in &members[uses.decl.index()] {
            self.external(p, decl, members);
        }
        self.indent -= 1;
        self.out.push_str("\n}\n");
    }

    /// An external declaration, with its members.
    fn external(&mut self, p: &Program, decl: DeclId, members: &[Vec<DeclId>]) {
        let d = p.decls.get(decl);
        let owner_is_type = p.decls.contains(d.owner)
            && matches!(
                p.decls.get(d.owner).kind,
                DeclKind::Class | DeclKind::Interface
            );
        self.marks(p, decl);
        if matches!(
            d.kind,
            DeclKind::ModuleLet | DeclKind::Field | DeclKind::StaticField
        ) {
            if d.kind == DeclKind::StaticField {
                self.out.push_str("static ");
            }
            self.let_word(p, decl);
            self.typed_decl(p, decl);
            self.out.push(';');
            return;
        }
        let word = match d.kind {
            DeclKind::Module => "module ",
            DeclKind::Class => "class ",
            DeclKind::Interface => "interface ",
            DeclKind::Method => "method ",
            DeclKind::Getter => "get ",
            DeclKind::Setter => "set ",
            DeclKind::Constructor => "constructor ",
            DeclKind::Function if owner_is_type => "static ",
            _ => "fn ",
        };
        self.out.push_str(word);
        self.decl(p, decl);
        self.type_params(p, decl);
        if matches!(d.kind, DeclKind::Class | DeclKind::Interface) {
            self.supers(p, decl);
        }
        if matches!(
            d.kind,
            DeclKind::Class | DeclKind::Interface | DeclKind::Module
        ) {
            let inner = &members[decl.index()];
            if inner.is_empty() {
                self.out.push_str(" {}");
                return;
            }
            self.out.push_str(" {");
            self.indent += 1;
            for &member in inner {
                self.external(p, member, members);
            }
            self.indent -= 1;
            self.line();
            self.out.push('}');
            return;
        }
        self.out.push('(');
        if let Some(sig) = p.signature(decl) {
            let names = p.decls.param_names(decl);
            for (i, &ty) in sig.params.iter().enumerate() {
                if i > 0 {
                    self.out.push_str(", ");
                }
                let param = names.get(i).map_or("_", |&n| p.strings.get(n));
                self.out.push_str(&name(param));
                if sig.optional[i] {
                    self.out.push('?');
                }
                self.out.push_str(": ");
                self.ty(p, ty);
            }
        }
        self.out.push(')');
        self.result(p, decl);
        self.out.push(';');
    }

    /// A call's type arguments, `<A, B>`, if it has any.
    fn type_args(&mut self, p: &Program, call: NodeId) {
        let args = p
            .types
            .args_of(crate::types::TypeArgs(p.nodes.slots(call).1));
        if args.is_empty() {
            return;
        }
        self.out.push('<');
        for (i, &a) in args.iter().enumerate() {
            if i > 0 {
                self.out.push_str(", ");
            }
            self.ty(p, a);
        }
        self.out.push('>');
    }

    /// A generic declaration's type parameters where it's defined, `<T#1
    /// extends B#2, U#3>`, if it has any.
    fn type_params(&mut self, p: &Program, decl: DeclId) {
        let params = p.decls.type_params(decl);
        if params.is_empty() {
            return;
        }
        self.out.push('<');
        for (i, &t) in params.iter().enumerate() {
            if i > 0 {
                self.out.push_str(", ");
            }
            self.decl(p, t);
            for (j, &b) in p.decls.supers(t).iter().enumerate() {
                self.out.push_str(if j == 0 { " extends " } else { " & " });
                self.ty(p, b);
            }
        }
        self.out.push('>');
    }

    /// ` extends A#1, B#2`, if it has supertypes.
    fn supers(&mut self, p: &Program, decl: DeclId) {
        for (i, &s) in p.decls.supers(decl).iter().enumerate() {
            self.out.push_str(if i == 0 { " extends " } else { ", " });
            self.ty(p, s);
        }
    }

    /// `: Result`, as `: Int32 throws Bubble` if it may bubble.
    fn result(&mut self, p: &Program, decl: DeclId) {
        let result = p.signature(decl).map_or(TypeId::NONE, |sig| sig.returns);
        self.out.push_str(": ");
        self.ty(p, result);
    }

    /// A declaration's doc comment and marks, each line started: `///`
    /// lines, then `qname "…"`, `keep`, `keepTest`, `connected`, and
    /// `export`.
    fn marks(&mut self, p: &Program, decl: DeclId) {
        let d = p.decls.get(decl);
        if !d.doc.is_none() {
            for line in p.strings.get(d.doc).split('\n') {
                self.line();
                self.out.push_str("///");
                if !line.is_empty() {
                    self.out.push(' ');
                    self.out.push_str(line);
                }
            }
        }
        self.line();
        self.inline_marks(p, decl);
    }

    /// `qname "…"`, `keep`, `keepTest`, `connected(…)`, and `export`, each
    /// followed by a space.
    fn inline_marks(&mut self, p: &Program, decl: DeclId) {
        let d = p.decls.get(decl);
        if !d.qname.is_none() {
            self.out.push_str("qname ");
            quote(p.strings.get(d.qname), &mut self.out);
            self.out.push(' ');
        }
        if d.facts.contains(Facts::KEEP) {
            self.out.push_str("keep ");
        }
        if d.facts.contains(Facts::KEEP_TEST) {
            self.out.push_str("keepTest ");
        }
        if d.facts.contains(Facts::CONNECTED) {
            self.out.push_str("connected");
            let backends = p.decls.backends(decl);
            if !backends.is_empty() {
                self.out.push('(');
                for (i, &b) in backends.iter().enumerate() {
                    if i > 0 {
                        self.out.push_str(", ");
                    }
                    quote(p.strings.get(b), &mut self.out);
                }
                self.out.push(')');
            }
            self.out.push(' ');
        }
        if d.facts.contains(Facts::EXPORTED) {
            self.out.push_str("export ");
        }
        if d.facts.contains(Facts::PRIVATE) {
            self.out.push_str("private ");
        }
        if d.facts.contains(Facts::PROTECTED) {
            self.out.push_str("protected ");
        }
        if d.facts.contains(Facts::AUTO) {
            self.out.push_str("auto ");
        }
        if d.facts.contains(Facts::FUN) {
            self.out.push_str("fun ");
        }
        if d.facts.contains(Facts::OPERATOR_EQ) {
            self.out.push_str("operator(\"==\") ");
        }
        if d.facts.contains(Facts::OPERATOR_CMP) {
            self.out.push_str("operator(\"<=>\") ");
        }
    }

    fn module(&mut self, p: &Program, module: &Module) {
        self.out.push_str("module ");
        self.decl(p, module.decl);
        if !module.path.is_empty() {
            self.out.push_str(" in ");
            quote(&module.path, &mut self.out);
        }
        self.out.push_str(" {");
        self.indent += 1;
        for &item in &module.items {
            self.item(p, item);
            self.out.push('\n');
        }
        self.line();
        self.out.push_str("init ");
        self.block(p, module.init);
        // What only tests reach, once partitioned: its items, then its
        // init block.
        if !module.test_items.is_empty() || module.test_init.is_some() {
            self.out.push('\n');
            self.line();
            self.out.push_str("for tests {");
            self.indent += 1;
            for (i, &item) in module.test_items.iter().enumerate() {
                if i > 0 {
                    self.out.push('\n');
                }
                self.item(p, item);
            }
            if let Some(init) = module.test_init {
                if !module.test_items.is_empty() {
                    self.out.push('\n');
                }
                self.line();
                self.out.push_str("init ");
                self.block(p, init);
            }
            self.indent -= 1;
            self.line();
            self.out.push('}');
        }
        for &test in &module.tests {
            self.out.push('\n');
            self.item(p, test);
        }
        self.indent -= 1;
        self.out.push_str("\n}\n");
    }

    fn item(&mut self, p: &Program, item: NodeId) {
        let decl = match p.nodes.get(item) {
            Node::Function { decl, .. }
            | Node::BodilessFunction { decl, .. }
            | Node::Test { decl, .. }
            | Node::ModuleLet { decl }
            | Node::Class { decl, .. }
            | Node::Interface { decl, .. }
            | Node::Field { decl }
            | Node::StaticField { decl }
            | Node::Method { decl, .. }
            | Node::AbstractMethod { decl, .. } => decl,
            _ => DeclId::NONE,
        };
        self.marks(p, decl);
        match p.nodes.get(item) {
            Node::Class { members, .. } | Node::Interface { members, .. } => {
                let word = if p.nodes.kind(item) == Kind::Class {
                    "class "
                } else {
                    "interface "
                };
                self.out.push_str(word);
                self.decl(p, decl);
                self.type_params(p, decl);
                self.supers(p, decl);
                let members = p.nodes.list(members);
                if members.is_empty() {
                    self.out.push_str(" {}");
                    return;
                }
                self.out.push_str(" {");
                self.indent += 1;
                let class = p.decls.get(decl).ty;
                for &member in members {
                    let method = p.nodes.kind(member) == Kind::Method;
                    self.this = if method { class } else { TypeId::NONE };
                    self.item(p, member);
                    self.this = TypeId::NONE;
                }
                self.indent -= 1;
                self.line();
                self.out.push('}');
            }
            Node::Field { .. } | Node::StaticField { .. } => {
                if p.nodes.kind(item) == Kind::StaticField {
                    self.out.push_str("static ");
                }
                self.let_word(p, decl);
                self.typed_decl(p, decl);
                self.out.push(';');
            }
            Node::Method { params, .. } | Node::AbstractMethod { params, .. } => {
                let word = match p.decls.get(decl).kind {
                    DeclKind::Getter => "get ",
                    DeclKind::Setter => "set ",
                    DeclKind::Constructor => "constructor ",
                    _ => "method ",
                };
                if p.nodes.kind(item) == Kind::AbstractMethod {
                    self.out.push_str("abstract ");
                }
                self.out.push_str(word);
                self.decl(p, decl);
                self.type_params(p, decl);
                self.params(p, params);
                self.result(p, decl);
                match p.nodes.get(item) {
                    Node::Method { body, .. } => {
                        self.out.push(' ');
                        self.block(p, body);
                    }
                    _ => self.out.push(';'),
                }
            }
            Node::Function { params, .. }
            | Node::BodilessFunction { params, .. }
            | Node::Test { params, .. } => {
                match p.nodes.get(item) {
                    Node::Test { name, .. } => {
                        self.out.push_str("test ");
                        quote(p.strings.get(name), &mut self.out);
                        self.out.push(' ');
                    }
                    _ => {
                        let owner = p.decls.get(decl).owner;
                        let in_type = p.decls.contains(owner)
                            && matches!(
                                p.decls.get(owner).kind,
                                DeclKind::Class | DeclKind::Interface
                            );
                        self.out
                            .push_str(if in_type { "static fn " } else { "fn " });
                    }
                }
                self.decl(p, decl);
                self.type_params(p, decl);
                self.params(p, params);
                self.result(p, decl);
                match p.nodes.get(item) {
                    Node::Function { body, .. } | Node::Test { body, .. } => {
                        self.out.push(' ');
                        self.block(p, body);
                    }
                    _ => self.out.push(';'),
                }
            }
            _ => {
                self.let_word(p, decl);
                self.typed_decl(p, decl);
                self.out.push(';');
            }
        }
    }

    /// A function's or method's parameters, in parentheses.
    fn params(&mut self, p: &Program, params: Kids) {
        self.out.push('(');
        for (i, &param) in p.nodes.list(params).iter().enumerate() {
            if i > 0 {
                self.out.push_str(", ");
            }
            if let Node::Param { decl } = p.nodes.get(param) {
                self.inline_marks(p, decl);
                if p.decls.get(decl).facts.contains(Facts::VAR) {
                    self.out.push_str("var ");
                }
                self.decl(p, decl);
                if p.decls.get(decl).facts.contains(Facts::OPTIONAL) {
                    self.out.push('?');
                }
                self.out.push_str(": ");
                self.ty(p, p.decls.get(decl).ty);
            }
        }
        self.out.push(')');
    }

    /// `let` or `var`, with a space.
    fn let_word(&mut self, p: &Program, decl: DeclId) {
        let var = p.decls.get(decl).facts.contains(Facts::VAR);
        self.out.push_str(if var { "var " } else { "let " });
    }

    /// A declaration where it's defined: `name#id`, or `~hint|alt#id` if
    /// compiler-made.
    fn decl(&mut self, p: &Program, decl: DeclId) {
        let d = p.decls.get(decl);
        if d.facts.contains(Facts::COMPILER_MADE) {
            self.out.push('~');
            self.out.push_str(&name(p.name(decl)));
            for &alt in p.decls.alts(decl) {
                self.out.push('|');
                self.out.push_str(&name(p.strings.get(alt)));
            }
            let _ = write!(self.out, "#{}", decl.0);
        } else {
            self.reference(p, decl);
        }
    }

    fn typed_decl(&mut self, p: &Program, decl: DeclId) {
        self.decl(p, decl);
        self.out.push_str(": ");
        self.ty(p, p.decls.get(decl).ty);
    }

    /// A use of a declaration: `name#id`, or `~hint#id`.
    fn reference(&mut self, p: &Program, decl: DeclId) {
        if p.decls.get(decl).facts.contains(Facts::COMPILER_MADE) {
            self.out.push('~');
        }
        let _ = write!(self.out, "{}#{}", name(p.name(decl)), decl.0);
    }

    fn ty(&mut self, p: &Program, ty: TypeId) {
        self.out.push_str(&p.show_type(ty));
    }

    fn block(&mut self, p: &Program, block: NodeId) {
        let Node::Block { stmts } = p.nodes.get(block) else {
            return;
        };
        self.stmts_in_braces(p, stmts);
    }

    fn stmts_in_braces(&mut self, p: &Program, stmts: Kids) {
        if stmts.is_empty() {
            self.out.push_str("{}");
            return;
        }
        self.out.push('{');
        self.indent += 1;
        for &stmt in p.nodes.list(stmts) {
            self.line();
            self.stmt(p, stmt);
        }
        self.indent -= 1;
        self.line();
        self.out.push('}');
    }

    fn stmt(&mut self, p: &Program, id: NodeId) {
        match p.nodes.get(id) {
            Node::Block { stmts } => self.stmts_in_braces(p, stmts),
            Node::Let { local, init } => {
                self.inline_marks(p, local);
                self.let_word(p, local);
                self.typed_decl(p, local);
                if let Some(init) = init {
                    self.out.push_str(" = ");
                    self.expr(p, init);
                }
                self.out.push(';');
            }
            Node::Assign {
                local: target,
                value,
            }
            | Node::SetModuleLet { target, value } => {
                self.reference(p, target);
                self.out.push_str(" = ");
                self.expr(p, value);
                self.out.push(';');
            }
            Node::InitLet { target, value } => {
                self.out.push_str("init ");
                self.reference(p, target);
                self.out.push_str(" = ");
                self.expr(p, value);
                self.out.push(';');
            }
            Node::If { cond, then, els } => {
                self.out.push_str("if (");
                self.expr(p, cond);
                self.out.push_str(") ");
                self.block(p, then);
                if let Some(els) = els {
                    self.out.push_str(" else ");
                    match else_if(p, els) {
                        Some(inner) => self.stmt(p, inner),
                        None => self.block(p, els),
                    }
                }
            }
            Node::Switch { scrutinee, cases } => {
                self.out.push_str("switch (");
                self.expr(p, scrutinee);
                self.out.push_str(") ");
                if cases.is_empty() {
                    self.out.push_str("{}");
                    return;
                }
                self.out.push('{');
                self.indent += 1;
                for &case in p.nodes.list(cases) {
                    self.line();
                    let Node::Case { labels, body } = p.nodes.get(case) else {
                        // A non-case in a case's place (S3).
                        self.stmt(p, case);
                        continue;
                    };
                    if labels.is_empty() {
                        self.out.push_str("default");
                    } else {
                        self.out.push_str("case ");
                        for (i, &label) in p.nodes.list(labels).iter().enumerate() {
                            if i > 0 {
                                self.out.push_str(", ");
                            }
                            self.expr(p, label);
                        }
                    }
                    self.out.push(' ');
                    self.block(p, body);
                }
                self.indent -= 1;
                self.line();
                self.out.push('}');
            }
            Node::While { label, cond, body } => {
                self.decl(p, label);
                self.out.push_str(": while (");
                self.expr(p, cond);
                self.out.push_str(") ");
                self.block(p, body);
            }
            Node::DoWhile { label, body, cond } => {
                self.decl(p, label);
                self.out.push_str(": do ");
                self.block(p, body);
                self.out.push_str(" while (");
                self.expr(p, cond);
                self.out.push_str(");");
            }
            Node::Break { label } => {
                self.out.push_str("break ");
                self.reference(p, label);
                self.out.push(';');
            }
            Node::Continue { label } => {
                self.out.push_str("continue ");
                self.reference(p, label);
                self.out.push(';');
            }
            Node::Return { value } => {
                self.out.push_str("return");
                if let Some(value) = value {
                    self.out.push(' ');
                    self.expr(p, value);
                }
                self.out.push(';');
            }
            Node::ExprStatement { expr } => {
                self.expr(p, expr);
                self.out.push(';');
            }
            Node::Yield { value } => {
                self.out.push_str("yield");
                if let Some(value) = value {
                    self.out.push(' ');
                    self.expr(p, value);
                }
                self.out.push(';');
            }
            Node::SetField {
                field,
                object,
                value,
            } => {
                self.receiver(p, object);
                self.out.push('.');
                self.reference(p, field);
                self.out.push_str(" = ");
                self.expr(p, value);
                self.out.push(';');
            }
            Node::InitStatic { target, value } => {
                self.out.push_str("init ");
                self.reference(p, target);
                self.out.push_str(" = ");
                self.expr(p, value);
                self.out.push(';');
            }
            _ => {
                // An expression in a statement's place (S3): written so
                // the problem shows.
                self.expr(p, id);
                self.out.push(';');
            }
        }
    }

    fn expr(&mut self, p: &Program, id: NodeId) {
        match p.nodes.get(id) {
            Node::BoolLit { value } => self.out.push_str(if value { "true" } else { "false" }),
            Node::Int32Lit { value } => {
                let _ = write!(self.out, "{value}");
            }
            Node::Int64Lit { value } => {
                let _ = write!(self.out, "{value}i64");
            }
            Node::Float64Lit { value } => self.out.push_str(&float(value)),
            Node::StringLit { value } => quote(p.strings.get(value), &mut self.out),
            Node::VoidLit {} => self.out.push_str("void"),
            Node::TypeValue { of } => {
                self.out.push_str("typeValue(");
                self.ty(p, of);
                self.out.push(')');
            }
            Node::Local { local: decl } | Node::GetModuleLet { target: decl } => {
                self.reference(p, decl)
            }
            Node::CallFunction { callee, args, .. } => {
                self.reference(p, callee);
                self.type_args(p, id);
                self.args(p, args, id);
            }
            Node::CallMethod {
                method,
                receiver,
                args,
                ..
            } => {
                self.receiver(p, receiver);
                self.out.push('.');
                self.reference(p, method);
                self.type_args(p, id);
                self.args(p, args, id);
            }
            Node::This {} if self.is_this(p, id) => self.out.push_str("this"),
            Node::This {} => {
                self.out.push_str("(this");
                self.written_type(p, id);
            }
            Node::GetField { field, object } => {
                self.receiver(p, object);
                self.out.push('.');
                self.reference(p, field);
            }
            Node::GetStatic { target } => self.reference(p, target),
            Node::New { ctor, args, .. } => {
                self.out.push_str("new ");
                self.reference(p, ctor);
                self.type_args(p, id);
                self.args(p, args, id);
            }
            Node::Upcast { value } | Node::Downcast { value } => {
                let word = if p.nodes.kind(id) == Kind::Upcast {
                    "(upcast("
                } else {
                    "(downcast("
                };
                self.out.push_str(word);
                self.expr(p, value);
                self.out.push(')');
                self.written_type(p, id);
            }
            Node::As { value, .. } => {
                self.out.push_str("(as(");
                self.expr(p, value);
                self.out.push(')');
                if p.nodes.flags(id).contains(Flags::MAY_BUBBLE) {
                    self.out.push('^');
                }
                self.written_type(p, id);
            }
            Node::Is { ty, value } => {
                self.out.push_str("is(");
                self.expr(p, value);
                self.out.push_str(", ");
                self.ty(p, ty);
                self.out.push(')');
            }
            Node::Identical { left, right } => {
                self.out.push_str("identical(");
                self.expr(p, left);
                self.out.push_str(", ");
                self.expr(p, right);
                self.out.push(')');
            }
            Node::Omitted {} => self.out.push_str("omitted"),
            Node::FunctionLiteral { decl, params, body } => {
                self.out.push_str("(fn ");
                if p.decls.get(decl).facts.contains(Facts::GENERATOR) {
                    self.out.push_str("generator ");
                }
                self.decl(p, decl);
                self.params(p, params);
                self.result(p, decl);
                let captures = p.decls.captures(decl);
                if !captures.is_empty() {
                    self.out.push_str(" [");
                    for (i, &c) in captures.iter().enumerate() {
                        if i > 0 {
                            self.out.push_str(", ");
                        }
                        self.reference(p, c);
                    }
                    self.out.push(']');
                }
                self.out.push(' ');
                self.block(p, body);
                self.written_type(p, id);
            }
            Node::FunctionRef { target } => {
                self.out.push_str("(ref(");
                self.reference(p, target);
                self.out.push(')');
                self.written_type(p, id);
            }
            Node::CallValue { callee, args, .. } => {
                self.receiver(p, callee);
                self.args(p, args, id);
            }
            Node::ToFun { value } | Node::FromFun { value } => {
                let word = if p.nodes.kind(id) == Kind::ToFun {
                    "(toFun("
                } else {
                    "(fromFun("
                };
                self.out.push_str(word);
                self.expr(p, value);
                self.out.push(')');
                self.written_type(p, id);
            }
            Node::ListLit { items } => {
                self.out.push_str("(list(");
                for (i, &item) in p.nodes.list(items).iter().enumerate() {
                    if i > 0 {
                        self.out.push_str(", ");
                    }
                    self.expr(p, item);
                }
                self.out.push(')');
                self.written_type(p, id);
            }
            Node::Null {} => {
                self.out.push_str("(null");
                self.written_type(p, id);
            }
            Node::IsNull { value } => {
                self.out.push_str("isNull(");
                self.expr(p, value);
                self.out.push(')');
            }
            Node::NonNull { value } | Node::AsNullable { value } => {
                let word = if p.nodes.kind(id) == Kind::NonNull {
                    "(nonNull("
                } else {
                    "(asNullable("
                };
                self.out.push_str(word);
                self.expr(p, value);
                self.out.push(')');
                self.written_type(p, id);
            }
            Node::EqNullable { op, left, right } => {
                self.out.push_str("eqNullable(%");
                self.out.push_str(op.info().map_or("?", |info| info.name));
                self.out.push_str(", ");
                self.expr(p, left);
                self.out.push_str(", ");
                self.expr(p, right);
                self.out.push(')');
            }
            Node::Coalesce { left, right } => {
                self.out.push_str("(coalesce(");
                self.expr(p, left);
                self.out.push_str(", ");
                self.expr(p, right);
                self.out.push(')');
                self.written_type(p, id);
            }
            Node::OrElse { body, fallback } => {
                self.out.push_str("(orelse(");
                self.expr(p, body);
                self.out.push_str(", ");
                self.expr(p, fallback);
                self.out.push(')');
                self.written_type(p, id);
            }
            Node::Catch {
                local,
                body,
                fallback,
            } => {
                self.out.push_str("(catch(");
                self.expr(p, body);
                self.out.push_str(", ");
                self.typed_decl(p, local);
                self.out.push_str(", ");
                self.expr(p, fallback);
                self.out.push(')');
                self.written_type(p, id);
            }
            Node::Throw { value, cause, .. } => {
                self.out.push_str("(throw(");
                self.expr(p, value);
                if let Some(cause) = cause {
                    self.out.push_str(", ");
                    self.expr(p, cause);
                }
                self.out.push(')');
                if p.nodes.flags(id).contains(Flags::MAY_BUBBLE) {
                    self.out.push('^');
                }
                self.written_type(p, id);
            }
            Node::Bubble { .. } => {
                self.out.push_str("(bubble()");
                if p.nodes.flags(id).contains(Flags::MAY_BUBBLE) {
                    self.out.push('^');
                }
                self.written_type(p, id);
            }
            Node::Panic {} => {
                self.out.push_str("(panic()");
                self.written_type(p, id);
            }
            Node::Await {
                may_bubble,
                promise,
            } => {
                self.out.push_str("(await(");
                self.expr(p, promise);
                self.out.push(')');
                if may_bubble {
                    self.out.push('^');
                }
                self.written_type(p, id);
            }
            Node::Op { op, args, .. } => {
                self.out.push('%');
                self.out.push_str(op.info().map_or("?", |info| info.name));
                self.args(p, args, id);
            }
            Node::And { left, right } => {
                self.operand(p, left, Kind::And, false);
                self.out.push_str(" && ");
                self.operand(p, right, Kind::And, true);
            }
            Node::Or { left, right } => {
                self.operand(p, left, Kind::Or, false);
                self.out.push_str(" || ");
                self.operand(p, right, Kind::Or, true);
            }
            Node::IfValue { cond, then, els } => {
                self.out.push_str("(if (");
                self.expr(p, cond);
                self.out.push_str(") ");
                self.expr(p, then);
                self.out.push_str(" else ");
                self.expr(p, els);
                self.written_type(p, id);
            }
            Node::BlockValue { stmts, value } => {
                self.out.push_str("(value {");
                let flat = std::mem::replace(&mut self.flat, true);
                for &stmt in p.nodes.list(stmts) {
                    self.line();
                    self.stmt(p, stmt);
                }
                self.line();
                self.expr(p, value);
                self.flat = flat;
                self.out.push_str(" }");
                self.written_type(p, id);
            }
            Node::Error { message, value } => {
                self.out.push_str("(error(");
                quote(p.strings.get(message), &mut self.out);
                // A type-only error's value as written.
                if let Some(value) = value {
                    self.out.push_str(", ");
                    self.expr(p, value);
                }
                self.out.push(')');
                self.written_type(p, id);
            }
            _ => {
                // A statement in an expression's place (S3).
                self.out.push_str("(value {");
                let flat = std::mem::replace(&mut self.flat, true);
                self.line();
                self.stmt(p, id);
                self.flat = flat;
                self.out.push_str(" })");
            }
        }
    }

    /// Whether a `This` is written bare: in a method, typed as its class.
    fn is_this(&self, p: &Program, id: NodeId) -> bool {
        !self.this.is_none() && p.nodes.ty(id) == self.this
    }

    /// What comes before a `.`: grouped if it's `&&` or `||`, which `.`
    /// binds tighter than, a float, whose `.` would read as part of the
    /// number, or a form whose type is written, which would seem to end
    /// with the type's name.
    fn receiver(&mut self, p: &Program, receiver: NodeId) {
        let kind = p.nodes.kind(receiver);
        let group = matches!(kind, Kind::And | Kind::Or | Kind::Float64Lit)
            || (kind.type_rule() == crate::schema::TypeRule::Written
                && !(kind == Kind::This && self.is_this(p, receiver)));
        if group {
            self.out.push('(');
        }
        self.expr(p, receiver);
        if group {
            self.out.push(')');
        }
    }

    /// An operand of `&&` or `||`. `&&` binds tighter, and both group
    /// from the left, so an `||` inside `&&`, or a right operand of the
    /// same kind, needs parentheses.
    fn operand(&mut self, p: &Program, id: NodeId, parent: Kind, right: bool) {
        let needs = match p.nodes.kind(id) {
            Kind::Or => parent == Kind::And || right,
            Kind::And => parent == Kind::And && right,
            _ => false,
        };
        if needs {
            self.out.push('(');
            self.expr(p, id);
            self.out.push(')');
        } else {
            self.expr(p, id);
        }
    }

    fn args(&mut self, p: &Program, args: Kids, call: NodeId) {
        self.out.push('(');
        for (i, &arg) in p.nodes.list(args).iter().enumerate() {
            if i > 0 {
                self.out.push_str(", ");
            }
            self.expr(p, arg);
        }
        self.out.push(')');
        if p.nodes.flags(call).contains(Flags::MAY_BUBBLE) {
            self.out.push('^');
        }
    }

    /// Closes a form whose type is written: `): T`.
    fn written_type(&mut self, p: &Program, id: NodeId) {
        self.out.push_str("): ");
        self.ty(p, p.nodes.ty(id));
    }
}

/// The `If` that an else block holds alone, written `else if`.
fn else_if(p: &Program, els: NodeId) -> Option<NodeId> {
    match p.nodes.kids(els) {
        &[only] if matches!(p.nodes.get(only), Node::If { .. }) => Some(only),
        _ => None,
    }
}

/// A name, bare if it can be, else in backquotes.
pub(crate) fn name(name: &str) -> String {
    if super::is_plain_name(name) {
        return name.into();
    }
    let mut out = String::from("`");
    for c in name.chars() {
        if matches!(c, '`' | '\\') {
            out.push('\\');
        }
        out.push(c);
    }
    out.push('`');
    out
}

/// A `Float64` in the shortest form that reads back to the same bits,
/// always with `.` or an exponent.
fn float(value: f64) -> String {
    if value.is_nan() {
        return "nan".into();
    }
    if value.is_infinite() {
        return if value > 0.0 { "inf" } else { "-inf" }.into();
    }
    // Debug is the shortest form that reads back, as `1.5`, `1e16`, or
    // `1e-7`; the grammar wants a `.` before any exponent.
    let text = format!("{value:?}");
    match text.find('e') {
        Some(e) if !text[..e].contains('.') => format!("{}.0{}", &text[..e], &text[e..]),
        _ => text,
    }
}

/// A string literal, escaping `"`, `\`, and control characters only.
fn quote(text: &str, out: &mut String) {
    out.push('"');
    for c in text.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if c.is_control() => {
                let _ = write!(out, "\\u{{{:x}}}", c as u32);
            }
            c => out.push(c),
        }
    }
    out.push('"');
}

/// The trailing section: files, then declarations' spans by id, then
/// nodes' by writing order.
fn write_spans(p: &Program, out: &mut String) {
    out.push_str("spans {\n");
    for (i, file) in p.files.iter().enumerate() {
        let _ = write!(out, "  file {i} ");
        quote(&file.path, out);
        out.push(' ');
        for byte in file.hash {
            let _ = write!(out, "{byte:02x}");
        }
        out.push_str(";\n");
    }
    for decl in p.decls.ids() {
        let d = p.decls.get(decl);
        if d.kind == DeclKind::Removed || !(d.span.is_known() || d.name_span.is_known()) {
            continue;
        }
        let _ = write!(out, "  #{}", decl.0);
        span(d.span, out);
        if d.name_span.is_known() {
            out.push_str(" name");
            span(d.name_span, out);
        }
        out.push_str(";\n");
    }
    for (n, id) in writing_order(p).into_iter().enumerate() {
        let derived = p.nodes.flags(id).contains(Flags::DERIVED);
        if !p.nodes.span(id).is_known() && !derived {
            continue;
        }
        let _ = write!(out, "  @{n}");
        span(p.nodes.span(id), out);
        if derived {
            out.push_str(" ~");
        }
        out.push_str(";\n");
    }
    out.push_str("}\n");
}

fn span(span: Span, out: &mut String) {
    if span.is_known() {
        let _ = write!(out, " {}:{}-{}", span.file.0, span.start, span.end);
    }
}

/// Each library's and type's external declarations, by owner, in
/// `DeclId` order.
fn members(p: &Program) -> Vec<Vec<DeclId>> {
    let mut members = vec![Vec::new(); p.decls.len()];
    for decl in p.decls.ids() {
        let d = p.decls.get(decl);
        let external = matches!(
            d.kind,
            DeclKind::Class
                | DeclKind::Interface
                | DeclKind::Function
                | DeclKind::Method
                | DeclKind::Getter
                | DeclKind::Setter
                | DeclKind::Constructor
                | DeclKind::Field
                | DeclKind::StaticField
                | DeclKind::Module
                | DeclKind::ModuleLet
        );
        if external && p.decls.contains(d.owner) {
            let owner = p.decls.get(d.owner).kind;
            let in_external_module = owner == DeclKind::Module
                && p.decls.contains(p.decls.get(d.owner).owner)
                && !matches!(d.kind, DeclKind::Module);
            if matches!(
                owner,
                DeclKind::Library | DeclKind::Class | DeclKind::Interface
            ) || in_external_module
            {
                members[d.owner.index()].push(decl);
            }
        }
    }
    members
}
