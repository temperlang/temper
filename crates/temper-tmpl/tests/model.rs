//! Tests of the model, its tables, spans, and the validator on programs
//! that text can't express, such as broken structure.

use temper_tmpl::ops::{OPS, Op};
use temper_tmpl::schema::{Arity, Slot};
use temper_tmpl::{
    Builder, DeclId, DeclKind, Features, Flags, Kind, Node, NodeId, Profile, Span, TypeId,
    difference, text, validate,
};

fn op(name: &str) -> Op {
    Op::named(name).unwrap()
}

/// Problems as golden tests list them.
fn problems(p: &temper_tmpl::Program, profile: &Profile) -> Vec<String> {
    let problems = validate(p, profile);
    problems.iter().map(ToString::to_string).collect()
}

/// A module `m` with one function `f(): Int32` around `body`, which the
/// closure builds; the function's declaration is #1.
fn with_function(body: impl FnOnce(&mut Builder) -> Vec<NodeId>) -> temper_tmpl::Program {
    let mut b = Builder::new("t", "0.1.0");
    let m = b.module("m");
    let sig = b.function_type(&[], TypeId::INT32, false);
    let f = b.declare(DeclKind::Function, "f", m, sig);
    let stmts = body(&mut b);
    let block = b.block(&stmts);
    let function = b.function(f, &[], block);
    b.add_item(m, function);
    let init = b.block(&[]);
    b.set_init(m, init);
    b.finish()
}

#[test]
fn schema_fields_split_children_unambiguously() {
    for &kind in Kind::ALL {
        let kids: Vec<Arity> = kind
            .fields()
            .iter()
            .filter_map(|f| match f.slot {
                Slot::Kid(arity, _) => Some(arity),
                _ => None,
            })
            .collect();
        assert!(kids.len() <= 4, "{kind:?}");
        let variable = kids.iter().filter(|a| **a != Arity::One).count();
        assert!(variable <= 1, "{kind:?} has two variable child fields");
        assert_eq!(Kind::ALL[kind as usize], kind);
    }
}

#[test]
fn ops_have_unique_names_and_are_found_by_name() {
    for (i, info) in OPS.iter().enumerate() {
        assert_eq!(Op::named(info.name), Some(Op(i as u32)), "{}", info.name);
    }
    assert_eq!(Op::named("Int32.plus"), None);
}

#[test]
fn builder_nodes_decode_to_their_fields() {
    let mut b = Builder::new("t", "0.1.0");
    let m = b.module("m");
    let local = b.declare(DeclKind::Local, "x", m, TypeId::INT64);
    let big = b.int64(-1 << 40);
    let x = b.float64(-0.5);
    let read = b.local(local);
    let div = b.op(op("Int64.div"), &[read, big]);
    let assign = b.assign(local, div);
    let ret = b.return_stmt(None);
    let block = b.block(&[assign, ret]);
    let p = b.finish();
    assert_eq!(p.nodes.get(big), Node::Int64Lit { value: -1 << 40 });
    assert_eq!(p.nodes.get(x), Node::Float64Lit { value: -0.5 });
    assert_eq!(p.nodes.ty(read), TypeId::INT64);
    let Node::Op {
        op: k,
        may_bubble,
        args,
    } = p.nodes.get(div)
    else {
        panic!()
    };
    assert_eq!((k, may_bubble), (op("Int64.div"), true));
    assert_eq!(p.nodes.list(args), [read, big]);
    assert_eq!(p.nodes.get(ret), Node::Return { value: None });
    let Node::Block { stmts } = p.nodes.get(block) else {
        panic!()
    };
    assert_eq!(p.nodes.list(stmts), [assign, ret]);
    assert_eq!(p.nodes.get(block).kind(), Kind::Block);
}

#[test]
fn floats_write_shortest_and_read_back_exactly() {
    let values = [
        0.1,
        1.0,
        -0.0,
        1e16,
        1.5e-7,
        f64::MAX,
        f64::MIN_POSITIVE,
        5e-324,
        f64::INFINITY,
        f64::NEG_INFINITY,
    ];
    let mut b = Builder::new("t", "0.1.0");
    let m = b.module("m");
    let mut stmts = Vec::new();
    for value in values {
        let lit = b.float64(value);
        let neg = b.op(op("Float64.neg"), &[lit]);
        stmts.push(b.expr_statement(neg));
    }
    let init = b.block(&stmts);
    b.set_init(m, init);
    let p = b.finish();
    let written = text::write(&p);
    assert!(written.contains("%Float64.neg(1.0e16)"));
    assert!(written.contains("%Float64.neg(1.5e-7)"));
    assert!(written.contains("%Float64.neg(5.0e-324)"));
    let back = text::read(&written).unwrap();
    assert_eq!(difference(&p, &back), None);
    // Equality compares bits, so -0.0 isn't 0.0.
    let zero = text::read(&written.replace("(-0.0)", "(0.0)")).unwrap();
    assert!(difference(&p, &zero).is_some());
}

#[test]
fn spans_round_trip_and_are_checked() {
    let mut b = Builder::new("t", "0.1.0");
    let file = b.add_file("src/t.temper", [0xab; 16]);
    let span = |start, end| Span { file, start, end };
    let m = b.module("t");
    b.set_decl_spans(m, span(0, 40), span(0, 0));
    let sig = b.function_type(&[], TypeId::VOID, false);
    let f = b.declare(DeclKind::Function, "f", m, sig);
    b.set_decl_spans(f, span(0, 20), span(4, 5));
    let hello = b.bool_lit(true);
    b.set_span(hello, span(10, 17), false);
    let log = b.op(op("Boolean.not"), &[hello]);
    b.set_span(log, span(8, 18), false);
    let stmt = b.expr_statement(log);
    b.set_span(stmt, span(8, 19), false);
    let body = b.block(&[stmt]);
    b.set_span(body, span(7, 20), false);
    let function = b.function(f, &[], body);
    b.set_span(function, span(0, 20), false);
    b.add_item(m, function);
    let init = b.block(&[]);
    b.set_span(init, span(21, 21), true);
    b.set_init(m, init);
    let p = b.finish();
    assert_eq!(problems(&p, &Profile::full()), Vec::<String>::new());
    let written = text::write(&p);
    let spans = &written[written.find("spans {").unwrap()..];
    assert_eq!(
        spans,
        "spans {\n  file 0 \"src/t.temper\" abababababababababababababababab;\n  \
         #0 0:0-40 name 0:0-0;\n  #1 0:0-20 name 0:4-5;\n  @0 0:0-20;\n  @1 0:7-20;\n  \
         @2 0:8-19;\n  @3 0:8-18;\n  @4 0:10-17;\n  @5 0:21-21 ~;\n}\n"
    );
    let back = text::read(&written).unwrap();
    assert_eq!(difference(&p, &back), None);
    assert_eq!(text::write(&back), written);
    // Without the section, the program has no spans, and equals the
    // original apart from them.
    let stripped = text::read(&written[..written.find("\nspans {").unwrap() + 1]).unwrap();
    assert!(!stripped.has_spans);
    assert_eq!(stripped.nodes.span(NodeId(0)), Span::UNKNOWN);
    // A program with spans needs them everywhere (P1).
    let partial = text::read(&written.replace("  @3 0:8-18;\n", "")).unwrap();
    assert_eq!(
        problems(&partial, &Profile::full()),
        ["P1 @3: Op has no span in a known file"]
    );
}

#[test]
fn structure_problems() {
    // A Return with two values.
    let p = with_function(|b| {
        let one = b.int32(1);
        let two = b.int32(2);
        vec![b.raw(Kind::Return, TypeId::NONE, (0, 0), Flags::NONE, &[one, two])]
    });
    let two_values = "S1 @2: Return can't have 2 children";
    assert_eq!(problems(&p, &Profile::full()), [two_values]);
    // A node used twice.
    let p = with_function(|b| {
        let one = b.int32(1);
        vec![b.expr_statement(one), b.return_stmt(Some(one))]
    });
    assert_eq!(
        problems(&p, &Profile::full()),
        ["S2 @4: its child Int32Lit has another parent too"]
    );
    // An expression where a statement goes, a statement where an
    // expression goes, a child after its parent, and flags on a kind
    // without them.
    let p = with_function(|b| {
        let one = b.int32(1);
        let empty = b.block(&[]);
        let bad = b.raw(Kind::Return, TypeId::NONE, (0, 0), Flags::NONE, &[empty]);
        let later = NodeId(100);
        let after = b.raw(
            Kind::ExprStatement,
            TypeId::NONE,
            (0, 0),
            Flags::NONE,
            &[later],
        );
        let flagged = b.raw(Kind::Block, TypeId::NONE, (0, 0), Flags::MAY_BUBBLE, &[]);
        let copy = b.raw(Kind::Block, TypeId::NONE, (0, 0), Flags::SPREAD_COPY, &[]);
        vec![one, bad, after, flagged, copy]
    });
    assert_eq!(
        problems(&p, &Profile::full()),
        [
            "S3 @2: Int32Lit can't go where Stmt goes",
            "S3 @4: Block can't go where Expr goes",
            "S1 @5: node 100 doesn't exist",
            "S1 @6: Block can't be marked may-bubble",
            "S1 @7: Block can't be marked a spread's copy",
        ]
    );
}

#[test]
fn profiles_limit_kinds() {
    let p = with_function(|b| {
        let one = b.int32(1);
        let value = b.block_value(TypeId::INT32, &[], one);
        vec![b.return_stmt(Some(value))]
    });
    assert!(problems(&p, &Profile::full()).is_empty());
    let no_statements = Profile {
        native: Features::NONE,
        coroutines: temper_tmpl::coroutines::CoroutineStrategy::TranslateToGenerator,
    };
    assert_eq!(
        problems(&p, &no_statements),
        ["S4 @3: the profile doesn't allow BlockValue"]
    );
}

#[test]
fn type_reference_and_declaration_problems() {
    let p = with_function(|b| {
        // A literal of the wrong type, an expression without one, a
        // missing declaration, a flag that disagrees with the callee, and
        // a compiler-made local without a hint.
        let wrong = b.raw(Kind::Int32Lit, TypeId::BOOLEAN, (1, 0), Flags::NONE, &[]);
        let log = b.op(op("Boolean.not"), &[wrong]);
        let untyped = b.raw(Kind::BoolLit, TypeId::NONE, (1, 0), Flags::NONE, &[]);
        let ghost = b.raw(Kind::Local, TypeId::INT32, (99, 0), Flags::NONE, &[]);
        let one = b.int32(1);
        let marked = b.raw(
            Kind::Op,
            TypeId::INT32,
            (op("Int32.neg").0, 0),
            Flags::MAY_BUBBLE,
            &[one],
        );
        let owner = temper_tmpl::DeclId(1);
        let nameless = b.declare_made(DeclKind::Local, &[""], owner, TypeId::INT32);
        let two = b.int32(2);
        let three = b.int32(3);
        vec![
            b.expr_statement(log),
            b.expr_statement(untyped),
            b.expr_statement(ghost),
            b.expr_statement(marked),
            b.let_stmt(nameless, Some(two)),
            b.return_stmt(Some(three)),
        ]
    });
    assert_eq!(
        problems(&p, &Profile::full()),
        [
            "T2 @4: Int32Lit is Int32, not Boolean",
            "T1 @6: BoolLit needs a type",
            "R1 @8: declaration #99 doesn't exist",
            "F3 @10: the call of %Int32.neg can't bubble, but is marked",
            "D1 #2: compiler-made without a hint",
        ]
    );
}

#[test]
fn external_declaration_and_method_problems() {
    let mut b = Builder::new("t", "0.1.0");
    let m = b.module("m");
    let core = b.uses("temper-core", "0.1.0");
    // A library declaration no `uses` section lists, and a class owned by
    // a module (D2); a getter with a parameter (T1).
    let stray = b.declare(DeclKind::Library, "stray", DeclId::NONE, TypeId::NONE);
    let misplaced = b.declare_type(DeclKind::Class, "Misplaced", m);
    let int32 = b.declare_type(DeclKind::Class, "Int32", core);
    assert_eq!(b.program().decls.get(int32).ty, TypeId::INT32);
    let sig = b.function_type(&[TypeId::INT32], TypeId::BOOLEAN, false);
    let getter = b.declare(DeclKind::Getter, "odd", int32, sig);
    let sig = b.function_type(&[], TypeId::VOID, false);
    let f = b.declare(DeclKind::Function, "f", m, sig);
    // A local typed by a function's "nominal type" (T5).
    let bogus = b.nominal(f);
    let local = b.declare(DeclKind::Local, "x", m, bogus);
    // A method call naming a function (R2), and `Omitted` where no call's
    // argument goes (S3).
    let receiver = b.int32(1);
    let call = b.call_method(f, receiver, &[]);
    let omitted = b.omitted(TypeId::INT32);
    let stmts = [b.expr_statement(call), b.expr_statement(omitted)];
    let block = b.block(&stmts);
    b.set_init(m, block);
    let p = b.finish();
    let _ = (stray, misplaced, getter, local);
    assert_eq!(
        problems(&p, &Profile::full()),
        ["S3 @5: Omitted goes only where a call's argument does"]
    );
    // Without the structural problem, the rest show.
    let mut b = Builder::new("t", "0.1.0");
    let m = b.module("m");
    let core = b.uses("temper-core", "0.1.0");
    b.declare(DeclKind::Library, "stray", DeclId::NONE, TypeId::NONE);
    b.declare_type(DeclKind::Class, "Misplaced", m);
    let int32 = b.declare_type(DeclKind::Class, "Int32", core);
    let sig = b.function_type(&[TypeId::INT32], TypeId::BOOLEAN, false);
    b.declare(DeclKind::Getter, "odd", int32, sig);
    let sig = b.function_type(&[], TypeId::VOID, false);
    let f = b.declare(DeclKind::Function, "f", m, sig);
    let bogus = b.nominal(f);
    b.declare(DeclKind::ModuleLet, "x", m, bogus);
    let receiver = b.int32(1);
    let call = b.call_method(f, receiver, &[]);
    let stmt = b.expr_statement(call);
    let block = b.block(&[stmt]);
    b.set_init(m, block);
    let p = b.finish();
    assert_eq!(
        problems(&p, &Profile::full()),
        [
            "T1 #5: a getter takes no arguments",
            "R2 @2: CallMethod needs a method, not f#6, a function",
            "D2 #2: a library needs a uses section and no owner",
            "T5 #7: #6 isn't a class or interface",
        ]
    );
}
