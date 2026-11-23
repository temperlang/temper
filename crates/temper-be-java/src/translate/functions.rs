//! Function values in Java, as Kotlin's Java backend writes them: a
//! function type, or a `@fun` interface, is the functional interface of
//! `java.util.function` whose method's signature fits it, by its
//! parameters' and result's kinds (`int`, `double`, `boolean` as a result,
//! or an object), as `IntUnaryOperator` for `fn (Int): Int`, or where none
//! fits, one of the module's own, synthesized in its package's `function`
//! package and named for its types (`Float64Float64Int32`). A literal is a
//! lambda, `x -> f(f(x))`, or with statements, a local of its interface's
//! type declared before what uses it; a reference to a function is a
//! method reference, `WorkGlobal::addOne`; and a call of a value calls its
//! interface's method. A function value where a parameter's declared
//! interface differs is adapted by a method reference to its own method
//! (`f::applyAsInt`), as Kotlin's.
//!
//! A local that a lambda captures and that's assigned is a cell, since
//! Java's lambdas capture only effectively final locals: an instance of
//! a local class with one field, `value`, as Kotlin's local classes hold
//! such captures.

use super::{R, Translator, not_yet};
use crate::tree::{Expr, Stmt};
use temper_tmpl::{DeclId, Kids, Node, NodeId, Scalar, TypeData, TypeId};

/// `java.util.function`'s interfaces (and `Runnable`), as Kotlin's
/// `JavaTypes.kt` lists them: each by its signature's kinds, its
/// parameters' then its result's (`v` void, `o` an object, `b` boolean,
/// `d` double, `i` int, `l` long), and its method.
const STANDARD: &[(&str, &str, &str)] = &[
    ("BiConsumer", "oov", "accept"),
    ("BiFunction", "ooo", "apply"),
    ("BiPredicate", "oob", "test"),
    ("BooleanSupplier", "b", "getAsBoolean"),
    ("Consumer", "ov", "accept"),
    ("DoubleBinaryOperator", "ddd", "applyAsDouble"),
    ("DoubleConsumer", "dv", "accept"),
    ("DoubleFunction", "do", "apply"),
    ("DoublePredicate", "db", "test"),
    ("DoubleSupplier", "d", "getAsDouble"),
    ("DoubleToIntFunction", "di", "applyAsInt"),
    ("DoubleToLongFunction", "dl", "applyAsLong"),
    ("DoubleUnaryOperator", "dd", "applyAsDouble"),
    ("Function", "oo", "apply"),
    ("IntBinaryOperator", "iii", "applyAsInt"),
    ("IntConsumer", "iv", "accept"),
    ("IntFunction", "io", "apply"),
    ("IntPredicate", "ib", "test"),
    ("IntSupplier", "i", "getAsInt"),
    ("IntToDoubleFunction", "id", "applyAsDouble"),
    ("IntToLongFunction", "il", "applyAsLong"),
    ("IntUnaryOperator", "ii", "applyAsInt"),
    ("Predicate", "ob", "test"),
    ("Supplier", "o", "get"),
    ("ToDoubleBiFunction", "ood", "applyAsDouble"),
    ("ToDoubleFunction", "od", "applyAsDouble"),
    ("ToIntBiFunction", "ooi", "applyAsInt"),
    ("ToIntFunction", "oi", "applyAsInt"),
    ("Runnable", "v", "run"),
];

/// A functional interface: its type as written, its simple name, and its
/// method.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Sam {
    pub ty: String,
    pub class: String,
    pub method: &'static str,
}

impl Translator<'_> {
    /// A parameter's or result's kind, as `JavaTypes.kt`'s
    /// `simpleType`: a boolean parameter is an object.
    fn kind(&self, ty: TypeId, result: bool) -> char {
        match self.p.types.get(ty) {
            TypeData::Scalar(Scalar::Void) if result => 'v',
            TypeData::Scalar(Scalar::Boolean) if result => 'b',
            TypeData::Scalar(Scalar::Int32) => 'i',
            TypeData::Scalar(Scalar::Float64) => 'd',
            _ => 'o',
        }
    }

    /// The functional interface for a function value's type: a function
    /// type, or a `@fun` interface.
    pub(crate) fn sam(&mut self, ty: TypeId) -> R<Sam> {
        let Some(sig) = temper_tmpl::analysis::fun_signature(self.p, ty) else {
            return not_yet("a functional interface for another type");
        };
        let Some(s) = self.p.types.signature(sig) else {
            return not_yet("a function type without a signature");
        };
        if s.optional.contains(&true) {
            return not_yet("a function type with optional parameters");
        }
        let (params, result) = (s.params.to_vec(), s.pass);
        let mut code: String = params.iter().map(|&t| self.kind(t, false)).collect();
        code.push(self.kind(result, true));
        if let Some(&(name, _, method)) = STANDARD.iter().find(|(_, c, _)| *c == code) {
            let mut args = Vec::new();
            for (&t, c) in params.iter().chain([&result]).zip(code.chars()) {
                if c == 'o' {
                    args.push(self.type_arg(t)?);
                }
            }
            let class = if name == "Runnable" {
                name.to_string()
            } else {
                self.class_ref(&format!("java.util.function.{name}"))
            };
            let ty = if args.is_empty() {
                class.clone()
            } else {
                format!("{class}<{}>", args.join(", "))
            };
            return Ok(Sam {
                ty,
                class: name.to_string(),
                method,
            });
        }
        self.synthetic_sam(sig, &params, result, code.chars().last().unwrap_or('o'))
    }

    /// One of the module's own functional interfaces, for a signature that
    /// none of `java.util.function`'s fits, as Kotlin synthesizes them:
    /// named for its parameters' and result's types, in the `function`
    /// package beside the module's, its method named for its result's
    /// kind.
    fn synthetic_sam(
        &mut self,
        sig: TypeId,
        params: &[TypeId],
        result: TypeId,
        kind: char,
    ) -> R<Sam> {
        let method = match kind {
            'v' => "accept",
            'b' => "test",
            'd' => "applyAsDouble",
            'i' => "applyAsInt",
            _ => "apply",
        };
        let name = match self.sams.iter().find(|(s, _, _)| *s == sig) {
            Some((_, name, _)) => name.clone(),
            None => {
                let mut base: String = params.iter().map(|&t| self.simple_name(t)).collect();
                if base.is_empty() {
                    base.push_str("Nullary");
                }
                base.push_str(&match self.p.types.get(result) {
                    TypeData::Scalar(Scalar::Void) => "Procedure".to_string(),
                    TypeData::Scalar(Scalar::Boolean) => "Predicate".to_string(),
                    _ => self.simple_name(result),
                });
                let taken = |n: &str, sams: &[(TypeId, String, String)]| {
                    sams.iter().any(|(_, m, _)| m == n)
                };
                let name = (0..)
                    .map(|i| {
                        if i == 0 {
                            base.clone()
                        } else {
                            format!("{base}{i}")
                        }
                    })
                    .find(|n| !taken(n, &self.sams))
                    .unwrap_or(base);
                if params
                    .iter()
                    .chain([&result])
                    .any(|&t| self.mentions_type_param(t))
                {
                    return not_yet("a synthesized functional interface of type parameters");
                }
                // Its types as its own file names them, with its imports.
                let package = format!("{}.function", self.package);
                let saved = (
                    std::mem::take(&mut self.imports),
                    std::mem::replace(&mut self.package, package.clone()),
                    std::mem::replace(&mut self.class, name.clone()),
                );
                let types = (|| -> R<(Vec<String>, String)> {
                    let mut ps = Vec::new();
                    for (i, &t) in params.iter().enumerate() {
                        ps.push(format!("{} arg{}", self.ty(t)?, i + 1));
                    }
                    Ok((ps, self.ty(result)?))
                })();
                let imports = std::mem::replace(&mut self.imports, saved.0);
                self.package = saved.1;
                self.class = saved.2;
                let (ps, result_ty) = types?;
                // `package …;`, its imports between blank lines, then it.
                let mut text = format!("package {package};\n");
                if !imports.is_empty() {
                    text.push('\n');
                    for i in &imports {
                        text.push_str(&format!("import {i};\n"));
                    }
                    text.push('\n');
                }
                text.push_str(&format!(
                    "public interface {name} {{\n    {result_ty} {method}({});\n}}\n",
                    ps.join(", ")
                ));
                self.sams.push((sig, name.clone(), text));
                name
            }
        };
        let class = self.class_ref(&format!("{}.function.{name}", self.package));
        Ok(Sam {
            ty: class.clone(),
            class: name,
            method,
        })
    }

    /// Whether a type is or has a type parameter.
    fn mentions_type_param(&self, ty: TypeId) -> bool {
        let p = self.p;
        match p.types.get(ty) {
            TypeData::Never(t) => self.mentions_type_param(t),
            TypeData::TypeParam { .. } => true,
            TypeData::Nullable(t) => self.mentions_type_param(t),
            TypeData::Nominal { args, .. } => {
                (p.types.list(args).iter()).any(|&t| self.mentions_type_param(t))
            }
            TypeData::Function { params, result, .. } => (p.types.list(params).iter())
                .chain([&result])
                .any(|&t| self.mentions_type_param(t)),
            TypeData::Result { pass, fail } => {
                self.mentions_type_param(pass) || self.mentions_type_param(fail)
            }
            TypeData::Scalar(_) => false,
        }
    }

    /// A type's name in a synthesized interface's name, as Kotlin's
    /// `suggestSimpleTypeName`.
    fn simple_name(&self, ty: TypeId) -> String {
        let p = self.p;
        match p.types.get(p.types.non_null(ty)) {
            TypeData::Never(t) => self.simple_name(t),
            TypeData::Scalar(Scalar::AnyValue) => "Any".into(),
            TypeData::Scalar(s) => s.name().into(),
            TypeData::Function { .. } => "Function".into(),
            TypeData::Nominal { decl, .. }
                if temper_tmpl::analysis::fun_apply(p, decl).is_some() =>
            {
                "Function".into()
            }
            TypeData::Nominal { decl, .. } | TypeData::TypeParam { decl } => p.name(decl).into(),
            TypeData::Nullable(_) => "Any".into(),
            TypeData::Result { pass, .. } => self.simple_name(pass),
        }
    }

    /// The functional interfaces the module synthesized, as files: each
    /// its path from the module's package's directory, and its text; those
    /// of its library's parts, or with `tests`, those its test parts
    /// added, which are test sources.
    pub fn synthesized(&self, tests: bool) -> Vec<(String, String)> {
        let (library, tests_only) = self.sams.split_at(self.library_sams.min(self.sams.len()));
        (if tests { tests_only } else { library }.iter())
            .map(|(_, name, text)| (format!("function/{name}.java"), text.clone()))
            .collect()
    }

    /// A function literal: a lambda where its body is one `return` of an
    /// expression that needs no statements; else a local of its
    /// interface's type, declared before the statement it's in.
    pub(crate) fn literal(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: Kids,
        body: NodeId,
        name: Option<String>,
    ) -> R<Expr> {
        let p = self.p;
        let names: Vec<String> = (p.nodes.list(params).iter())
            .filter_map(|&param| match p.nodes.get(param) {
                Node::Param { decl } => Some(self.names.get(decl).to_string()),
                _ => None,
            })
            .collect();
        let stmts = match p.nodes.get(body) {
            Node::Block { stmts } => p.nodes.list(stmts).to_vec(),
            _ => Vec::new(),
        };
        // The function around it, whose loops and labels its own replace.
        let saved = (
            std::mem::take(&mut self.loops),
            std::mem::take(&mut self.labels),
        );
        let void = p.signature(decl).is_some_and(|s| s.pass == TypeId::VOID);
        let result = (|| -> R<Expr> {
            if let ([ret], None) = (stmts.as_slice(), &name) {
                let value = match p.nodes.get(*ret) {
                    Node::Return { value: Some(value) } => Some(value),
                    Node::ExprStatement { expr } if void => Some(expr),
                    _ => None,
                };
                // A value with statements of its own, as an `orelse`, needs
                // a body: a lambda local's, below.
                if let Some(value) = value.filter(|&v| !has_statements(p, v)) {
                    let outer = std::mem::take(&mut self.pre);
                    let e = self.expr(value);
                    let pre = std::mem::replace(&mut self.pre, outer);
                    let e = e?;
                    // A void lambda's body must be a statement expression.
                    let fits = !void || e.is_statement();
                    if pre.is_empty() && fits {
                        return Ok(Expr::Lambda(names.clone(), Box::new(e)));
                    }
                }
            }
            let sam = self.sam(p.nodes.ty(id))?;
            let mut out = self.stmts(body)?;
            if void && out.last() == Some(&Stmt::Return(None)) {
                out.pop();
            }
            let local = name.unwrap_or_else(|| self.names.get(decl).to_string());
            self.pre.push(Stmt::LambdaLocal {
                ty: sam.ty,
                name: local.clone(),
                params: names.clone(),
                body: out,
            });
            Ok(Expr::name(local))
        })();
        let (loops, labels) = saved;
        self.loops = loops;
        self.labels = labels;
        result
    }

    /// A declared function or static method as a method reference:
    /// `WorkGlobal::addOne`, `Scaler::triple`, where Kotlin's names the
    /// static method alone, which isn't a value.
    pub(crate) fn function_ref(&mut self, target: DeclId) -> R<Expr> {
        let p = self.p;
        let owner = p.decls.get(target).owner;
        let class = if super::is_type(p, owner) {
            self.class_name(owner)
        } else {
            self.owner_class(target)
                .unwrap_or_else(|| self.class.clone())
        };
        Ok(Expr::MethodRef(
            Box::new(Expr::name(class)),
            self.names.get(target).to_string(),
        ))
    }

    /// A call of a function value: its interface's method.
    pub(crate) fn call_value(&mut self, callee: NodeId, args: Kids) -> R<Expr> {
        let p = self.p;
        if let Node::Local { local } = p.nodes.get(callee) {
            if self.group_of(local).is_some() {
                // A group's function, called as its method.
                let mut out = Vec::new();
                for &a in p.nodes.list(args) {
                    out.push(self.expr(a)?);
                }
                return Ok(Expr::name(self.name(local)).call(out));
            }
        }
        let sam = self.sam(p.nodes.ty(callee))?;
        let f = self.expr(callee)?;
        let mut out = Vec::new();
        for &a in p.nodes.list(args) {
            out.push(self.expr(a)?);
        }
        Ok(f.method(sam.method, out))
    }

    /// A function value given where `want`, a parameter's declared type,
    /// is wanted: as it is if it's a lambda or method reference, which
    /// Java types by where it goes, or if their interfaces are the same;
    /// else adapted, `f::applyAsInt`.
    pub(crate) fn adapt(&mut self, e: Expr, have: TypeId, want: TypeId) -> R<Expr> {
        if matches!(e, Expr::Lambda(..) | Expr::MethodRef(..))
            || temper_tmpl::analysis::fun_signature(self.p, want).is_none()
            || temper_tmpl::analysis::fun_signature(self.p, have).is_none()
        {
            return Ok(e);
        }
        let (have, want) = (self.sam(have)?, self.sam(want)?);
        if have.class == want.class {
            return Ok(e);
        }
        Ok(Expr::MethodRef(Box::new(e), have.method.to_string()))
    }

    /// Arguments for a call of `callee`, each function value adapted to
    /// its parameter's declared interface.
    pub(crate) fn adapted_args(
        &mut self,
        callee: DeclId,
        args: &[NodeId],
        exprs: Vec<Expr>,
    ) -> R<Vec<Expr>> {
        let params = self
            .p
            .signature(callee)
            .map(|s| s.params.to_vec())
            .unwrap_or_default();
        let mut out = Vec::new();
        for (i, e) in exprs.into_iter().enumerate() {
            let have = args.get(i).map(|&a| self.p.nodes.ty(a));
            out.push(match (have, params.get(i)) {
                (Some(have), Some(&want)) => self.adapt(e, have, want)?,
                _ => e,
            });
        }
        Ok(out)
    }

    /// Whether a local is a cell: captured by a lambda and assigned.
    pub(crate) fn is_cell(&self, decl: DeclId) -> bool {
        self.cells.contains(&decl)
    }

    /// A cell's local class's name: `CountCell` for `count`.
    pub(crate) fn cell_class(&self, decl: DeclId) -> String {
        let name = self.names.get(decl);
        let mut chars = name.chars();
        let first = chars.next().map(|c| c.to_ascii_uppercase());
        format!(
            "{}{}Cell",
            first.map(String::from).unwrap_or_default(),
            chars.as_str()
        )
    }

    /// A cell's declaration: `class CountCell { int value; }` and `final
    /// CountCell count = new CountCell();`, then its value if given.
    pub(crate) fn declare_cell(&mut self, decl: DeclId, ty: String, out: &mut Vec<Stmt>) {
        let class = self.cell_class(decl);
        out.push(Stmt::LocalClass {
            name: class.clone(),
            fields: vec![(ty, "value".into())],
        });
        out.push(Stmt::Local {
            ty: format!("final {class}"),
            name: self.names.get(decl).to_string(),
            value: Some(Expr::New(class, Vec::new())),
        });
    }
}

/// Whether a value has an `orelse` or a block's value in it, outside
/// function literals, which Java writes as statements.
fn has_statements(p: &temper_tmpl::Program, id: NodeId) -> bool {
    match p.nodes.get(id) {
        Node::OrElse { .. } | Node::BlockValue { .. } => true,
        Node::FunctionLiteral { .. } => false,
        _ => p.nodes.kids(id).iter().any(|&k| has_statements(p, k)),
    }
}
