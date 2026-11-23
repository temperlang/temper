//! Which connected declarations each backend takes from host code
//! (`temper_tmpl::host`), as Kotlin's `ConnectedChoice.kt` decides.

use temper_tmpl::host::{HostFile, files, replaced_by_fallbacks, uses_host};
use temper_tmpl::{Builder, DeclId, DeclKind, Facts, Program, TypeId};

/// A module with a function per backend list (`None` for no list), each
/// with a body, plus a bodiless one, a plain one, and a connected class
/// listing only `rust`.
fn program(no_connected: bool) -> (Program, Vec<DeclId>) {
    let mut b = Builder::new("t", "0.1.0");
    let m = b.module("");
    let sig = b.function_type(&[], TypeId::INT32);
    let mut decls = Vec::new();
    for (name, listed) in [
        ("all", Some(&[][..])),
        ("listed", Some(&["py"][..])),
        ("unlisted", Some(&["rust"][..])),
        ("plain", None),
    ] {
        let f = b.declare(DeclKind::Function, name, m, sig);
        match listed {
            Some([]) => b.add_facts(f, Facts::CONNECTED),
            Some(listed) => b.set_connected(f, listed),
            None => {}
        }
        let block = b.block(&[]);
        let function = b.function(f, &[], block);
        b.add_item(m, function);
        decls.push(f);
    }
    let f = b.declare(DeclKind::Function, "bodiless", m, sig);
    b.add_facts(f, Facts::CONNECTED);
    let function = b.bodiless_function(f, &[]);
    b.add_item(m, function);
    decls.push(f);
    let c = b.declare(DeclKind::Class, "Counter", m, TypeId::NONE);
    b.set_connected(c, &["rust"]);
    let class = b.class(c, &[]);
    b.add_item(m, class);
    decls.push(c);
    let init = b.block(&[]);
    b.set_init(m, init);
    let mut p = b.finish();
    p.host.no_connected = no_connected;
    p.host.files = ["Counter.java", "__connected__.py", "_connected.rs"]
        .map(|path| HostFile {
            path: path.into(),
            text: String::new(),
        })
        .to_vec();
    (p, decls)
}

#[test]
fn backends_choose_as_kotlin_does() {
    let (p, d) = program(false);
    let py = |i: usize| uses_host(&p, d[i], &["py"]);
    // No list, listed, unlisted, not connected, bodiless, unlisted class.
    assert_eq!(
        (0..6).map(py).collect::<Vec<_>>(),
        [true, true, false, false, true, false]
    );
    assert!(uses_host(&p, d[5], &["rust"]));
    assert_eq!(
        replaced_by_fallbacks(&p, &["java"], ".java"),
        ["Counter.java"]
    );
    assert!(replaced_by_fallbacks(&p, &["rust"], ".rs").is_empty());
    let py_files: Vec<&str> = (files(&p, &[".py"]).iter())
        .map(|f| f.path.as_str())
        .collect();
    assert_eq!(py_files, ["__connected__.py"]);
}

#[test]
fn no_connected_uses_temper_wherever_there_is_any() {
    let (p, d) = program(true);
    let rust = |i: usize| uses_host(&p, d[i], &["rust"]);
    assert_eq!(
        (0..6).map(rust).collect::<Vec<_>>(),
        [false, false, false, false, true, false]
    );
}
