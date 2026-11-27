//! Qnames: Kotlin's format, parsed and printed (the examples in Kotlin
//! Temper's `QName.kt` and its test), and derived for a program's
//! declarations (notes/tmpl/names.md, "Qnames").

use temper_tmpl::qname::{PartKind, QName, QNames, library_of, resolves_by_qname};
use temper_tmpl::{DeclId, Program, text};

#[test]
fn kotlins_examples_print_and_parse_back() {
    let examples = [
        "my-library/foo/bar.x",
        "my-library/foo/bar.f()",
        "my-library/foo/bar.f().(x)",
        "my-library/foo/bar.f().y=",
        "my-library/foo/bar.type C",
        "my-library/foo/bar.type C.<T>",
        "my-library/foo/bar.type C.x",
        "my-library/foo/bar.type C.f()",
        "my-library/foo/bar.type C.f().<T>",
        "my-library/foo/bar.type C.f().(x)",
        "my-library/foo/bar.type C.get y()",
        "my-library/foo/bar.type C.set y()",
        "my-library/foo/bar.type C.set y().(newY)",
        "my-library/foo/bar.type C.fromY()",
        "my-library/foo/bar.type C.fromY().(y)",
        "my-library/foo/bar.type C.fromY().c=",
        "my-library/foo/bar.x=#0",
        "my-library/foo/bar.x=#1",
        "my-library/foo/bar.f().type Helper",
        "my-library/foo/bar.f().helper()",
        "test-library/method.type C.constructor()",
        "core.type Int32.toString()",
        "core.type String.get isEmpty()",
        "core.getConsole()",
        "std/temporal.type Date",
        r"my-library/emacs\.d.\.a\/\\b().c=",
    ];
    for text in examples {
        let q = QName::parse(text).unwrap_or_else(|e| panic!("{text}: {e}"));
        assert_eq!(q.to_string(), text);
    }
    // The escaped example, part by part.
    let q = QName::parse(r"my-library/emacs\.d.\.a\/\\b().c=").unwrap();
    assert_eq!(q.library, "my-library");
    assert_eq!(q.path, ["emacs.d"]);
    assert_eq!(q.parts[0].name, ".a/\\b");
    assert_eq!(q.parts[0].kind, PartKind::FunctionOrMethod);
    assert_eq!(q.parts[1].kind, PartKind::Local);
    let q = QName::parse("lib.type C.set y().(newY)#3").unwrap();
    let kinds: Vec<PartKind> = q.parts.iter().map(|p| p.kind).collect();
    assert_eq!(kinds, [PartKind::Type, PartKind::Setter, PartKind::Input]);
    assert_eq!(q.index, Some(3));
    // A constructor part, as Kotlin prints one.
    let q = QName::parse("lib.type C.constructor new()").unwrap();
    assert_eq!(q.parts[1].kind, PartKind::Constructor);
    assert_eq!(q.to_string(), "lib.type C.constructor new()");
    // Escapes for control characters and quotes print back the same.
    let odd = r#"lib.a\ b\"c\t\0\=\<\>\#()"#;
    assert_eq!(QName::parse(odd).unwrap().to_string(), odd);
}

#[test]
fn malformed_qnames_are_rejected() {
    for bad in [
        "", "lib.", "lib.f(", "lib.x#", "lib.x#-1", "lib.x#1a", "lib/..", r"lib.\q", "lib.x\\",
        "lib.x)y",
    ] {
        assert!(QName::parse(bad).is_err(), "{bad:?} parsed");
    }
}

fn qnames(p: &Program) -> Vec<(String, String)> {
    let names = QNames::derive(p);
    p.decls
        .ids()
        .filter_map(|d| {
            let q = names.get(d)?;
            Some((format!("{}#{}", p.name(d), d.0), q.to_string()))
        })
        .collect()
}

#[test]
fn qnames_derive_as_kotlin_gives_them() {
    let p = text::read(
        r#"tmpl 1 model 1 library `my-library` 0.1.0

uses `temper-core`#20 0.1.0 {
  class Int32#21 {
    connected method toString#22(radix?: Int32): String;
  }
  class String#23 {
    connected static fromCodePoint#24(codePoint: Int32): String;
    connected get isEmpty#25(): Boolean;
  }
  interface Console#26 {
    connected method log#27(message: String): Void;
  }
  connected fn getConsole#28(name?: String): Console#26;
}

uses std#30 0.1.0 {
  connected fn sleep#31(ms: Int32): Void;
}

module bar#0 in "foo/bar" {
  let ~console#1: Console#26;

  let x#2: Int32;

  fn f#3(x#4: Int32): Int32 {
    let y#5: Int32 = %Int32.add(x#4, 1);
    {
      let z#6: Int32 = y#5;
    }
    outer#7: while (false) {}
    return y#5;
  }

  qname "g()" let g#8: Int32;

  connected fn host#9(): Void;

  init {
    init ~console#1 = getConsole#28(omitted);
    init x#2 = 123;
    {
      let x#10: Int32 = 1;
    }
    {
      let x#11: Int32 = 2;
    }
    init g#8 = 0;
  }
}
"#,
    )
    .unwrap();
    let expected = [
        ("x#2", "my-library/foo/bar.x"),
        ("f#3", "my-library/foo/bar.f()"),
        ("x#4", "my-library/foo/bar.f().(x)"),
        ("y#5", "my-library/foo/bar.f().y="),
        ("z#6", "my-library/foo/bar.f().z="),
        ("g#8", "my-library/foo/bar.g()"),
        ("host#9", "my-library/foo/bar.host()"),
        ("x#10", "my-library/foo/bar.x=#0"),
        ("x#11", "my-library/foo/bar.x=#1"),
        ("Int32#21", "core.type Int32"),
        ("toString#22", "core.type Int32.toString()"),
        ("String#23", "core.type String"),
        ("fromCodePoint#24", "core.type String.fromCodePoint()"),
        ("isEmpty#25", "core.type String.get isEmpty()"),
        ("Console#26", "core.type Console"),
        ("log#27", "core.type Console.log()"),
        ("getConsole#28", "core.getConsole()"),
        ("sleep#31", "std.sleep()"),
    ];
    let expected: Vec<(String, String)> = expected
        .iter()
        .map(|(d, q)| (d.to_string(), q.to_string()))
        .collect();
    // No qname: the compiler-made `~console#1`, the label `outer#7`, the
    // module, and the used libraries.
    assert_eq!(qnames(&p), expected);
    assert_eq!(temper_tmpl::validate(&p, &temper_tmpl::Profile::full()), []);
    // How connected declarations resolve: core's and std's by qname, the
    // program's own by name.
    assert!(resolves_by_qname(&p, DeclId(22)));
    assert!(resolves_by_qname(&p, DeclId(31)));
    assert!(!resolves_by_qname(&p, DeclId(9)));
    assert!(
        !resolves_by_qname(&p, DeclId(26)),
        "Console isn't connected"
    );
    assert_eq!(library_of(&p, DeclId(27)), "core");
    assert_eq!(library_of(&p, DeclId(5)), "my-library");
    assert!(p.is_virtual(DeclId(27)));
    assert!(!p.is_virtual(DeclId(22)));
}
